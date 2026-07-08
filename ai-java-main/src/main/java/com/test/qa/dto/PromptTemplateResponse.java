package com.test.qa.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PromptTemplateResponse implements Serializable {

    //主键ID
    private Long id;

    //场景名称
    private String scene;

    //系统提示词
    private String systemPrompt;

    //用户提示词模板
    private String userTemplate;

    //Few-Shot示例（JSON数组）
    private String fewShotExamples;

    //模型温度参数
    private Double temperature;

    //模型 top_p 参数
    private Double topP;

    //最大生成 Token 数
    private Integer maxTokens;

    //版本号
    private Integer version;

    //状态
    private String status;

    //版本备注
    private String remark;

    //创建时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
