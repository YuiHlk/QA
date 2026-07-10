package com.test.qa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Python训练服务响应DTO
 * 对应 Python FastAPI 的 TrainStatus 模型返回
 */
@Data
public class PythonTrainRespDTO {

    @JsonProperty("python_task_id")
    private String pythonTaskId;

    private String status;

    private Integer progress;

    private Map<String, Object> metrics;

    @JsonProperty("lora_weight_path")
    private String loraWeightPath;

    private String error;
}
