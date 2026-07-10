"""
QLoRA 4bit 微调训练器

完整训练流程封装，用于生产环境的实际训练。
依赖（GPU环境）：transformers, peft, bitsandbytes, accelerate, datasets, torch

典型用法:
    from trainer import QLoRATrainer
    trainer = QLoRATrainer(
        model_base="Qwen/Qwen2-7B-Instruct",
        dataset_name="your_dataset",
        lora_rank=64, lora_alpha=16,
        learning_rate=2e-4, num_epochs=3, batch_size=4,
    )
    trainer.load_dataset()
    trainer.load_model()
    trainer.apply_lora()
    lora_path, metrics = trainer.train(output_dir="./output")
"""

import os
import json
import logging
from typing import Optional, Callable

logger = logging.getLogger("qlora-train.trainer")

# 各模型系列的目标模块映射
_MODEL_TARGET_MODULES = {
    "qwen": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    "llama": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    "mistral": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    "default": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
}


class QLoRATrainer:
    """
    QLoRA 4bit量化微调训练器

    支持的基座模型：Qwen2系列、Llama系列、Mistral系列等
    微调策略：4bit NormalFloat量化 + LoRA低秩适配
    """

    def __init__(
        self,
        model_base: str = "Qwen/Qwen2-7B-Instruct",
        dataset_name: str = "default",
        dataset_path: str = "",
        lora_rank: int = 64,
        lora_alpha: int = 16,
        learning_rate: float = 2e-4,
        num_epochs: int = 3,
        batch_size: int = 4,
    ):
        # ── 参数校验 ──
        if not model_base or not model_base.strip():
            raise ValueError("model_base 不能为空")
        if lora_rank < 1 or lora_rank > 256:
            raise ValueError(f"lora_rank 必须在 1~256 之间，当前值: {lora_rank}")
        if lora_alpha < 1 or lora_alpha > 256:
            raise ValueError(f"lora_alpha 必须在 1~256 之间，当前值: {lora_alpha}")
        if learning_rate <= 0 or learning_rate > 1e-2:
            raise ValueError(f"learning_rate 必须在 (0, 1e-2] 之间，当前值: {learning_rate}")
        if num_epochs < 1 or num_epochs > 100:
            raise ValueError(f"num_epochs 必须在 1~100 之间，当前值: {num_epochs}")
        if batch_size < 1 or batch_size > 64:
            raise ValueError(f"batch_size 必须在 1~64 之间，当前值: {batch_size}")

        self.model_base = model_base.strip()
        self.dataset_name = dataset_name
        self.dataset_path = dataset_path
        self.lora_rank = lora_rank
        self.lora_alpha = lora_alpha
        self.learning_rate = learning_rate
        self.num_epochs = num_epochs
        self.batch_size = batch_size

        self.model = None
        self.tokenizer = None
        self.dataset = None
        self._tokenized = False

    def _detect_target_modules(self) -> list:
        """根据模型名称推断目标模块列表"""
        model_lower = self.model_base.lower()
        for prefix, modules in _MODEL_TARGET_MODULES.items():
            if prefix in model_lower:
                return modules
        return _MODEL_TARGET_MODULES["default"]

    def load_model(self):
        """加载基座模型（4bit量化）"""
        try:
            import torch
            from transformers import (
                AutoTokenizer,
                AutoModelForCausalLM,
                BitsAndBytesConfig,
            )
        except ImportError as e:
            raise ImportError(
                "缺少GPU训练依赖，请安装: pip install torch transformers bitsandbytes accelerate"
            ) from e

        logger.info("加载基座模型: %s", self.model_base)

        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
        )

        self.model = AutoModelForCausalLM.from_pretrained(
            self.model_base,
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=True,
            torch_dtype=torch.bfloat16,
        )

        self.tokenizer = AutoTokenizer.from_pretrained(
            self.model_base,
            trust_remote_code=True,
            padding_side="right",
        )

        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        logger.info("模型加载完成，显存占用: %.2f GB",
                     torch.cuda.memory_allocated() / 1024**3)

    def load_dataset(self, formatting_func: Optional[Callable] = None):
        """加载并格式化训练数据集"""
        try:
            from datasets import load_dataset
        except ImportError as e:
            raise ImportError(
                "缺少datasets依赖，请安装: pip install datasets"
            ) from e

        logger.info("加载数据集: name=%s path=%s", self.dataset_name, self.dataset_path)

        if self.dataset_path:
            self.dataset = load_dataset("json", data_files=self.dataset_path)
        else:
            self.dataset = load_dataset(self.dataset_name)

        if formatting_func:
            self.dataset = self.dataset.map(formatting_func)

        # 确保有 train 分片
        if self.dataset is not None and "train" not in self.dataset:
            if len(self.dataset.keys()) == 1:
                key = list(self.dataset.keys())[0]
                self.dataset = self.dataset.rename_column(key, "train")  # fallback
                logger.warning("数据集无 train 分片，使用 '%s' 作为训练集", key)
            else:
                raise ValueError(
                    f"数据集必须包含 train 分片，当前分片: {list(self.dataset.keys())}"
                )

        logger.info("数据集加载完成，训练样本数: %d", len(self.dataset["train"]))

    def _tokenize(self):
        """对数据集进行 tokenization"""
        if self.tokenizer is None:
            raise RuntimeError("请先调用 load_model() 加载分词器")
        if self.dataset is None:
            raise RuntimeError("请先调用 load_dataset() 加载数据集")
        if self._tokenized:
            return

        logger.info("开始 tokenization...")

        def _tokenize_fn(examples):
            result = self.tokenizer(
                examples["text"],
                truncation=True,
                max_length=2048,
                padding=False,
            )
            result["labels"] = result["input_ids"].copy()
            return result

        # 移除可能存在的 text 之外的其他列，避免 map 时的长度不匹配
        keep_cols = ["text"]
        remove_cols = [c for c in self.dataset["train"].column_names if c not in keep_cols]
        self.dataset = self.dataset.map(
            _tokenize_fn,
            batched=True,
            remove_columns=remove_cols,
            desc="Tokenizing",
        )
        self._tokenized = True
        logger.info("Tokenization 完成")

    def apply_lora(self):
        """配置并应用LoRA适配器"""
        try:
            from peft import (
                LoraConfig,
                get_peft_model,
                prepare_model_for_kbit_training,
                TaskType,
            )
        except ImportError as e:
            raise ImportError(
                "缺少peft依赖，请安装: pip install peft"
            ) from e

        if self.model is None:
            raise RuntimeError("请先调用 load_model() 加载模型")

        target_modules = self._detect_target_modules()
        logger.info("配置LoRA: rank=%d alpha=%d target_modules=%s",
                     self.lora_rank, self.lora_alpha, target_modules)

        self.model = prepare_model_for_kbit_training(self.model)

        lora_config = LoraConfig(
            r=self.lora_rank,
            lora_alpha=self.lora_alpha,
            target_modules=target_modules,
            lora_dropout=0.05,
            bias="none",
            task_type=TaskType.CAUSAL_LM,
        )

        self.model = get_peft_model(self.model, lora_config)
        self.model.print_trainable_parameters()

    def train(
        self,
        output_dir: str,
        progress_callback: Optional[Callable[[int, str], None]] = None,
    ):
        """
        执行训练

        Args:
            output_dir: LoRA权重输出目录
            progress_callback: 进度回调函数, 参数 (progress: int, message: str)

        Returns:
            (lora_path, metrics): LoRA权重路径和训练指标
        """
        try:
            from transformers import (
                TrainingArguments,
                Trainer,
                DataCollatorForLanguageModeling,
                TrainerCallback,
            )
        except ImportError as e:
            raise ImportError(
                "缺少transformers依赖，请安装: pip install transformers"
            ) from e

        if self.model is None:
            raise RuntimeError("请先调用 load_model()")
        if self.tokenizer is None:
            raise RuntimeError("请先调用 load_model()")
        if self.dataset is None:
            raise RuntimeError("请先调用 load_dataset()")

        self._tokenize()

        os.makedirs(output_dir, exist_ok=True)
        logger.info("开始训练: output_dir=%s epochs=%d batch_size=%d lr=%.2e",
                     output_dir, self.num_epochs, self.batch_size, self.learning_rate)

        if progress_callback:
            progress_callback(30, "训练中...")

        training_args = TrainingArguments(
            output_dir=output_dir,
            num_train_epochs=self.num_epochs,
            per_device_train_batch_size=self.batch_size,
            gradient_accumulation_steps=4,
            learning_rate=self.learning_rate,
            warmup_ratio=0.03,
            lr_scheduler_type="cosine",
            logging_steps=10,
            save_strategy="epoch",
            evaluation_strategy="no",
            fp16=True,
            gradient_checkpointing=True,
            optim="paged_adamw_8bit",
            report_to="none",
            ddp_find_unused_parameters=False,
        )

        data_collator = DataCollatorForLanguageModeling(
            tokenizer=self.tokenizer, mlm=False,
        )

        # 注册进度回调
        callbacks = []
        if progress_callback:

            class _ProgressCallback(TrainerCallback):
                def on_log(self, args, state, control, logs=None, **kwargs):
                    if logs and "loss" in logs:
                        step = state.global_step
                        max_steps = state.max_steps
                        if max_steps and max_steps > 0:
                            pct = min(30 + int(60 * step / max_steps), 90)
                            progress_callback(pct, f"训练中... step={step} loss={logs['loss']:.4f}")

            callbacks.append(_ProgressCallback())

        trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=self.dataset["train"],
            data_collator=data_collator,
            tokenizer=self.tokenizer,
            callbacks=callbacks,
        )

        try:
            trainer.train()
        except Exception:
            logger.exception("训练过程异常")
            raise

        # 保存LoRA权重
        if progress_callback:
            progress_callback(90, "保存LoRA权重...")
        lora_path = os.path.join(output_dir, "lora-weights")
        self.model.save_pretrained(lora_path)
        self.tokenizer.save_pretrained(lora_path)
        logger.info("LoRA权重已保存至: %s", lora_path)

        # 收集训练指标
        log_history = trainer.state.log_history
        metrics = {
            "loss": None,
            "eval_loss": None,
            "train_runtime": None,
        }
        for entry in reversed(log_history):
            if "loss" in entry and metrics["loss"] is None:
                metrics["loss"] = entry["loss"]
            if "eval_loss" in entry and metrics["eval_loss"] is None:
                metrics["eval_loss"] = entry["eval_loss"]
            if "train_runtime" in entry and metrics["train_runtime"] is None:
                metrics["train_runtime"] = entry.get("train_runtime")

        metrics_path = os.path.join(output_dir, "metrics.json")
        with open(metrics_path, "w", encoding="utf-8") as f:
            json.dump(metrics, f, ensure_ascii=False, indent=2)

        if progress_callback:
            progress_callback(100, "训练完成")

        logger.info("训练完成: lora_path=%s metrics=%s", lora_path, metrics)
        return lora_path, metrics

    def cleanup(self):
        """释放GPU显存"""
        self.model = None
        self.tokenizer = None
        self.dataset = None
        self._tokenized = False
        try:
            import torch
            torch.cuda.empty_cache()
            logger.info("GPU显存已释放")
        except ImportError:
            pass
