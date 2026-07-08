package com.test.qa.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UpdatePromptTemplateRequest implements Serializable {

    //系统提示词
    private String systemPrompt;

    //用户提示词模板（支持 {{variable}} 占位符）
    private String userTemplate;

    //Few-Shot示例（JSON数组）
    private String fewShotExamples;

    //模型温度参数 0.0-2.0
    private Double temperature;

    //模型 top_p 参数
    private Double topP;

    //最大生成 Token 数
    private Integer maxTokens;

    //状态: ACTIVE / ARCHIVED / DRAFT
    private String status;

    //版本备注
    private String remark;
}
