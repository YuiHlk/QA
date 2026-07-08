package com.test.qa.dto;

import com.test.qa.domain.PromptTemplate;

/**
 * PromptTemplate 实体与 DTO 之间的转换工具
 */
public final class PromptTemplateConverter {

    private PromptTemplateConverter() {
    }

    /**
     * CreateRequest → Entity
     */
    public static PromptTemplate toEntity(CreatePromptTemplateRequest request) {
        PromptTemplate entity = new PromptTemplate();
        entity.setScene(request.getScene());
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setUserTemplate(request.getUserTemplate());
        entity.setFewShotExamples(request.getFewShotExamples());
        entity.setTemperature(request.getTemperature());
        entity.setTopP(request.getTopP());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setStatus(request.getStatus());
        entity.setRemark(request.getRemark());
        return entity;
    }

    /**
     * UpdateRequest → Entity（仅设置非 null 字段，支持部分更新）
     */
    public static PromptTemplate toEntity(UpdatePromptTemplateRequest request) {
        PromptTemplate entity = new PromptTemplate();
        if (request.getSystemPrompt() != null) {
            entity.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getUserTemplate() != null) {
            entity.setUserTemplate(request.getUserTemplate());
        }
        if (request.getFewShotExamples() != null) {
            entity.setFewShotExamples(request.getFewShotExamples());
        }
        if (request.getTemperature() != null) {
            entity.setTemperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            entity.setTopP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            entity.setMaxTokens(request.getMaxTokens());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark());
        }
        return entity;
    }

    /**
     * Entity → Response
     */
    public static PromptTemplateResponse toResponse(PromptTemplate entity) {
        PromptTemplateResponse response = new PromptTemplateResponse();
        response.setId(entity.getId());
        response.setScene(entity.getScene());
        response.setSystemPrompt(entity.getSystemPrompt());
        response.setUserTemplate(entity.getUserTemplate());
        response.setFewShotExamples(entity.getFewShotExamples());
        response.setTemperature(entity.getTemperature());
        response.setTopP(entity.getTopP());
        response.setMaxTokens(entity.getMaxTokens());
        response.setVersion(entity.getVersion());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }
}
