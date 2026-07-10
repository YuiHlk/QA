package com.test.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.qa.domain.TrainTask;
import com.test.qa.dto.PythonTrainRespDTO;
import com.test.qa.mapper.TrainTaskMapper;
import com.test.qa.service.TrainTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Python微调任务服务实现
 *
 * 架构关键：Java不写训练代码，仅做HTTP调度。
 * 通过WebClient调用Python FastAPI服务，将训练结果持久化到MySQL。
 */
@Slf4j
@Service
public class TrainTaskServiceImpl extends ServiceImpl<TrainTaskMapper, TrainTask> implements TrainTaskService {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient pythonTrainWebClient;
    private final ObjectMapper objectMapper;

    public TrainTaskServiceImpl(@Qualifier("pythonTrainWebClient") WebClient pythonTrainWebClient,
                                 ObjectMapper objectMapper) {
        this.pythonTrainWebClient = pythonTrainWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<TrainTask> pageQuery(int page, int size, String status) {
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainTask::getStatus, status);
        }
        wrapper.orderByDesc(TrainTask::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public TrainTask createAndStart(TrainTask task) {
        // 默认值
        if (task.getLoraRank() == null) task.setLoraRank(64);
        if (task.getLoraAlpha() == null) task.setLoraAlpha(16);
        if (task.getLearningRate() == null) task.setLearningRate(2e-4);
        if (task.getNumEpochs() == null) task.setNumEpochs(3);
        if (task.getBatchSize() == null) task.setBatchSize(4);
        if (task.getStatus() == null) task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());

        // 1. 保存到数据库（独立事务，单条INSERT具备原子性）
        save(task);
        log.info("训练任务已创建 [id={}, name={}]", task.getId(), task.getTaskName());

        // 2. HTTP调用Python服务启动训练（脱离数据库事务，避免连接池耗尽）
        try {
            Map<String, Object> requestBody = buildTrainRequestBody(task);

            PythonTrainRespDTO response = pythonTrainWebClient.post()
                    .uri("/train")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(PythonTrainRespDTO.class)
                    .block(BLOCK_TIMEOUT);

            if (response != null && response.getPythonTaskId() != null) {
                task.setPythonTaskId(response.getPythonTaskId());
            }
            task.setStatus("TRAINING");
            task.setErrorMsg(null);
            log.info("Python训练任务已启动 [id={}, pythonTaskId={}]", task.getId(), task.getPythonTaskId());

        } catch (WebClientResponseException e) {
            log.error("调用Python训练接口HTTP异常 taskId={}, code={}", task.getId(), e.getStatusCode(), e);
            task.setStatus("PENDING");
            task.setErrorMsg("无法连接Python训练服务: " + e.getStatusText());
        } catch (Exception e) {
            log.error("调用Python训练接口未知异常 taskId={}", task.getId(), e);
            task.setStatus("PENDING");
            task.setErrorMsg("无法连接Python训练服务: " + e.getMessage());
        }

        // 3. 更新任务状态（带乐观锁，防止并发覆盖）
        updateById(task);
        return task;
    }

    @Override
    public TrainTask pollStatus(Long taskId) {
        // 1. 查询任务（无事务）
        TrainTask task = getById(taskId);
        if (task == null || task.getPythonTaskId() == null) {
            return task;
        }
        String pythonTaskId = task.getPythonTaskId();

        // 2. HTTP请求完全脱离数据库事务
        PythonTrainRespDTO resp;
        try {
            resp = pythonTrainWebClient.get()
                    .uri("/train/{task_id}/status", pythonTaskId)
                    .retrieve()
                    .bodyToMono(PythonTrainRespDTO.class)
                    .block(BLOCK_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("轮询训练接口HTTP异常 taskId={}, pythonTaskId={}, code={}",
                    taskId, pythonTaskId, e.getStatusCode(), e);
            markTaskFailed(taskId, task.getVersion(), "调用Python训练接口异常：" + e.getStatusText());
            return getById(taskId);
        } catch (Exception e) {
            log.error("轮询训练状态未知异常 taskId={}, pythonTaskId={}", taskId, pythonTaskId, e);
            markTaskFailed(taskId, task.getVersion(), "拉取训练状态失败：" + e.getMessage());
            return getById(taskId);
        }

        if (resp == null) {
            log.warn("Python训练接口返回空响应 taskId={}", taskId);
            return task;
        }

        // 3. 组装更新条件，自带乐观锁version校验
        LambdaUpdateWrapper<TrainTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TrainTask::getId, taskId)
                .eq(TrainTask::getVersion, task.getVersion())
                .set(TrainTask::getStatus, mapStatus(resp.getStatus()))
                .set(TrainTask::getProgress, resp.getProgress() != null ? resp.getProgress() : task.getProgress())
                .set(TrainTask::getUpdateTime, LocalDateTime.now());

        // 指标JSON
        if (resp.getMetrics() != null) {
            try {
                String metricsJson = objectMapper.writeValueAsString(resp.getMetrics());
                updateWrapper.set(TrainTask::getMetrics, metricsJson);
            } catch (JsonProcessingException e) {
                log.error("序列化训练指标JSON失败 taskId={}", taskId, e);
            }
        }
        // Lora权重路径
        if (resp.getLoraWeightPath() != null) {
            updateWrapper.set(TrainTask::getLoraWeightPath, resp.getLoraWeightPath());
        }
        // 错误信息
        if (resp.getError() != null) {
            updateWrapper.set(TrainTask::getErrorMsg, resp.getError());
        }

        // 4. 执行乐观锁更新
        int affectRows = baseMapper.update(null, updateWrapper);
        if (affectRows == 0) {
            log.warn("任务更新冲突，已被其他线程修改 taskId={}, version={}", taskId, task.getVersion());
        }

        // 5. 返回数据库最新数据，保证返回对象真实
        return getById(taskId);
    }

    @Override
    public TrainTask getTaskDetail(Long id) {
        TrainTask task = getById(id);
        if (task != null && "TRAINING".equals(task.getStatus())) {
            return pollStatus(id);
        }
        return task;
    }

    @Override
    public void deleteTask(Long id) {
        removeById(id);
    }

    /**
     * 异常时标记任务为失败，使用乐观锁防止并发覆盖
     */
    private void markTaskFailed(Long taskId, Integer oldVersion, String errMsg) {
        LambdaUpdateWrapper<TrainTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TrainTask::getId, taskId)
                .eq(TrainTask::getVersion, oldVersion)
                .set(TrainTask::getStatus, "FAILED")
                .set(TrainTask::getErrorMsg, errMsg)
                .set(TrainTask::getUpdateTime, LocalDateTime.now());
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            log.warn("标记任务失败时版本冲突，已被其他线程更新 taskId={}, version={}", taskId, oldVersion);
        }
    }

    /**
     * 构建发送给Python服务的请求体，过滤null值
     */
    private Map<String, Object> buildTrainRequestBody(TrainTask task) {
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", String.valueOf(task.getId()));
        putIfNotNull(body, "model_base", task.getModelBase());
        putIfNotNull(body, "dataset_name", task.getDatasetName());
        putIfNotNull(body, "dataset_path", task.getDatasetPath());
        putIfNotNull(body, "lora_rank", task.getLoraRank());
        putIfNotNull(body, "lora_alpha", task.getLoraAlpha());
        putIfNotNull(body, "learning_rate", task.getLearningRate());
        putIfNotNull(body, "num_epochs", task.getNumEpochs());
        putIfNotNull(body, "batch_size", task.getBatchSize());
        return body;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * 将Python服务的状态映射为本地状态
     */
    private String mapStatus(String pythonStatus) {
        if (pythonStatus == null) return "TRAINING";
        return switch (pythonStatus.toLowerCase()) {
            case "training", "running" -> "TRAINING";
            case "completed", "done", "finished" -> "COMPLETED";
            case "failed", "error" -> "FAILED";
            default -> "TRAINING";
        };
    }
}
