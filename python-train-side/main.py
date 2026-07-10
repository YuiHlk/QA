"""
Python QLoRA 微调服务 —— FastAPI HTTP 接口

职责（最小化、仅训练）：
- 接收Java后端发来的微调任务请求
- 后台执行QLoRA 4bit微调
- 提供状态查询和结果获取接口

不写业务逻辑、不做持久化（由Java负责）
"""

import os
import uuid
import signal
import threading
import logging
import traceback
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, field_validator

# ── 日志配置 ───────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("qlora-train")

app = FastAPI(title="QLoRA微调服务", version="1.1.0")

# ── 任务存储（线程安全） ───────────────────────────────────────
_tasks: dict = {}
_tasks_lock = threading.Lock()
_active_count = 0

# 任务最大保留时间（秒），超时后自动清理
TASK_TTL_SECONDS = int(os.getenv("TASK_TTL_SECONDS", "86400"))  # 默认24小时
MAX_TASKS = int(os.getenv("MAX_TASKS", "1000"))

# 是否使用真实训练（GPU环境设为 true）
USE_REAL_TRAINING = os.getenv("USE_REAL_TRAINING", "false").lower() == "true"


# ── 请求 / 响应模型 ───────────────────────────────────────────

class TrainRequest(BaseModel):
    task_id: str
    model_base: str = "Qwen2-7B-Instruct"
    dataset_name: str = "default"
    dataset_path: str = ""
    lora_rank: int = 64
    lora_alpha: int = 16
    learning_rate: float = 2e-4
    num_epochs: int = 3
    batch_size: int = 4

    @field_validator("lora_rank")
    @classmethod
    def check_lora_rank(cls, v: int) -> int:
        if v < 1 or v > 256:
            raise ValueError("lora_rank 必须在 1~256 之间")
        return v

    @field_validator("lora_alpha")
    @classmethod
    def check_lora_alpha(cls, v: int) -> int:
        if v < 1 or v > 256:
            raise ValueError("lora_alpha 必须在 1~256 之间")
        return v

    @field_validator("learning_rate")
    @classmethod
    def check_learning_rate(cls, v: float) -> float:
        if v <= 0 or v > 1e-2:
            raise ValueError("learning_rate 必须在 (0, 1e-2] 之间")
        return v

    @field_validator("num_epochs")
    @classmethod
    def check_num_epochs(cls, v: int) -> int:
        if v < 1 or v > 100:
            raise ValueError("num_epochs 必须在 1~100 之间")
        return v

    @field_validator("batch_size")
    @classmethod
    def check_batch_size(cls, v: int) -> int:
        if v < 1 or v > 64:
            raise ValueError("batch_size 必须在 1~64 之间")
        return v


class TrainStatus(BaseModel):
    python_task_id: str
    status: str  # training | completed | failed
    progress: int  # 0-100
    metrics: Optional[dict] = None
    lora_weight_path: Optional[str] = None
    error: Optional[str] = None


# ── 任务清理 ───────────────────────────────────────────────────

def _cleanup_expired_tasks() -> None:
    """清理超过 TTL 的已完成/失败任务，并限制任务总数"""
    import time as _time
    with _tasks_lock:
        now = _time.time()
        expired = [
            tid for tid, t in _tasks.items()
            if t["status"] in ("completed", "failed")
            and (now - t.get("_updated_at", now)) > TASK_TTL_SECONDS
        ]
        for tid in expired:
            del _tasks[tid]

        # 如果任务数仍超限，按更新时间清理最旧的任务
        if len(_tasks) > MAX_TASKS:
            finished = sorted(
                [(tid, t) for tid, t in _tasks.items()
                 if t["status"] in ("completed", "failed")],
                key=lambda x: x[1].get("_updated_at", 0),
            )
            overflow = len(_tasks) - MAX_TASKS
            for tid, _ in finished[:overflow]:
                del _tasks[tid]

    if expired:
        logger.info("清理了 %d 个过期任务", len(expired))


def _schedule_cleanup() -> None:
    """定期清理过期任务的后台线程"""
    while True:
        threading.Event().wait(600)  # 每10分钟
        try:
            _cleanup_expired_tasks()
        except Exception:
            logger.exception("任务清理异常")


_cleanup_thread = threading.Thread(target=_schedule_cleanup, daemon=True)
_cleanup_thread.start()


# ── API 端点 ───────────────────────────────────────────────────

@app.get("/health")
async def health_check():
    """Java端健康检查"""
    with _tasks_lock:
        active = _active_count
    return {"status": "ok", "active_tasks": active}


@app.post("/train")
async def start_training(request: TrainRequest):
    """
    启动QLoRA微调任务

    Java后端调用此接口发起训练，立即返回python_task_id，
    训练在后台线程执行，Java通过 GET /train/{task_id}/status 查询进度。
    """
    python_task_id = str(uuid.uuid4())[:8]

    with _tasks_lock:
        if len(_tasks) >= MAX_TASKS:
            _cleanup_expired_tasks()
            if len(_tasks) >= MAX_TASKS:
                raise HTTPException(
                    status_code=503,
                    detail=f"任务队列已满（上限 {MAX_TASKS}），请稍后重试",
                )

        _tasks[python_task_id] = {
            "python_task_id": python_task_id,
            "status": "training",
            "progress": 0,
            "metrics": None,
            "lora_weight_path": None,
            "error": None,
            "request": request.model_dump(),
            "_updated_at": __import__("time").time(),
        }

    thread = threading.Thread(
        target=_run_training,
        args=(python_task_id, request),
        daemon=True,
    )
    thread.start()

    logger.info("训练任务已启动: id=%s model=%s dataset=%s",
                python_task_id, request.model_base, request.dataset_name)

    return {
        "python_task_id": python_task_id,
        "status": "training",
        "message": f"训练任务已启动: {python_task_id}",
    }


@app.get("/train/{task_id}/status")
async def get_status(task_id: str):
    """
    查询训练任务状态

    返回当前进度(0-100)、训练指标(损失等)、权重路径
    Java端定时轮询此接口更新本地数据库
    """
    with _tasks_lock:
        task = _tasks.get(task_id)

    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")

    return {
        "python_task_id": task["python_task_id"],
        "status": task["status"],
        "progress": task["progress"],
        "metrics": task["metrics"],
        "lora_weight_path": task["lora_weight_path"],
        "error": task["error"],
    }


# ── 训练执行 ───────────────────────────────────────────────────

def _run_training(task_id: str, request: TrainRequest):
    """后台执行QLoRA微调"""
    import time as _time

    def update_progress(progress: int, message: str):
        _update_progress(task_id, progress, message)

    task = _get_task(task_id)
    if task is None:
        return

    try:
        if USE_REAL_TRAINING:
            _run_real_training(task_id, request, update_progress)
        else:
            _run_simulation_training(task_id, request, update_progress)
    except Exception as e:
        _set_task(task_id, "status", "failed")
        _set_task(task_id, "error", str(e))
        _set_task(task_id, "_updated_at", _time.time())
        logger.exception("训练任务失败: id=%s", task_id)


def _run_real_training(task_id: str, request: TrainRequest,
                       progress_callback) -> None:
    """使用 QLoRATrainer 执行真实训练"""
    from trainer import QLoRATrainer

    trainer = QLoRATrainer(
        model_base=request.model_base,
        dataset_name=request.dataset_name,
        dataset_path=request.dataset_path,
        lora_rank=request.lora_rank,
        lora_alpha=request.lora_alpha,
        learning_rate=request.learning_rate,
        num_epochs=request.num_epochs,
        batch_size=request.batch_size,
    )

    progress_callback(5, "加载数据集中...")
    trainer.load_dataset()

    progress_callback(15, "加载基座模型(4bit)...")
    trainer.load_model()

    progress_callback(25, "配置LoRA适配器...")
    trainer.apply_lora()

    progress_callback(30, "开始训练...")
    output_dir = f"./output/{task_id}"
    lora_path, metrics = trainer.train(
        output_dir=output_dir,
        progress_callback=progress_callback,
    )

    _set_task(task_id, "status", "completed")
    _set_task(task_id, "lora_weight_path", lora_path)
    _set_task(task_id, "metrics", metrics)
    _set_task(task_id, "progress", 100)
    logger.info("训练完成: id=%s lora_path=%s", task_id, lora_path)


def _run_simulation_training(task_id: str, request: TrainRequest,
                              progress_callback) -> None:
    """模拟训练流程（无需GPU，用于接口测试）"""
    import time as _time

    steps = [
        (5, "加载数据集中..."),
        (15, "加载基座模型(4bit)..."),
        (25, "配置LoRA适配器..."),
        (30, "配置训练参数..."),
    ]
    for progress, msg in steps:
        progress_callback(progress, msg)
        _time.sleep(0.05)

    total_steps = request.num_epochs * 100
    for step in range(total_steps):
        _time.sleep(0.01)
        progress = 35 + int(55 * (step + 1) / total_steps)
        if step % max(1, total_steps // 10) == 0:
            progress_callback(progress, "训练中...")

    progress_callback(90, "保存LoRA权重...")
    lora_path = f"./output/{task_id}/lora-weights"

    progress_callback(100, "训练完成")
    _set_task(task_id, "status", "completed")
    _set_task(task_id, "lora_weight_path", lora_path)
    _set_task(task_id, "metrics", {
        "loss": 1.23,
        "eval_loss": 1.15,
        "train_runtime": 3600.0,
    })


# ── 线程安全的任务读写 ──────────────────────────────────────────

def _get_task(task_id: str) -> Optional[dict]:
    with _tasks_lock:
        return _tasks.get(task_id)


def _set_task(task_id: str, key: str, value) -> None:
    import time as _time
    with _tasks_lock:
        task = _tasks.get(task_id)
        if task is not None:
            task[key] = value
            task["_updated_at"] = _time.time()
            # 跟踪活跃任务数
            global _active_count
            if key == "status":
                _active_count = sum(
                    1 for t in _tasks.values() if t["status"] == "training"
                )


def _update_progress(task_id: str, progress: int, message: str):
    import time as _time
    with _tasks_lock:
        task = _tasks.get(task_id)
        if task is not None:
            task["progress"] = progress
            task["_updated_at"] = _time.time()
    logger.info("[%s] %d%% - %s", task_id, progress, message)


# ── 优雅关闭 ───────────────────────────────────────────────────

def _shutdown(signum=None, frame=None):
    """收到 SIGTERM/SIGINT 时优雅关闭"""
    logger.info("收到关闭信号，等待训练任务结束...")
    with _tasks_lock:
        active = [tid for tid, t in _tasks.items() if t["status"] == "training"]
    if active:
        logger.warning("仍有 %d 个训练任务进行中: %s", len(active), active)
    else:
        logger.info("无进行中的训练任务，安全退出")


signal.signal(signal.SIGTERM, _shutdown)
signal.signal(signal.SIGINT, _shutdown)


# ── 启动入口 ───────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    logger.info("QLoRA微调服务启动: port=8002 real_training=%s", USE_REAL_TRAINING)
    uvicorn.run(app, host="0.0.0.0", port=8002)
