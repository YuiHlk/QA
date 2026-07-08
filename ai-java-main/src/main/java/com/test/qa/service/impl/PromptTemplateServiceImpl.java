package com.test.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.qa.domain.PromptTemplate;
import com.test.qa.mapper.PromptTemplateMapper;
import com.test.qa.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 提示词模板 Service 实现
 */
@Slf4j
@Service
public class PromptTemplateServiceImpl
        extends ServiceImpl<PromptTemplateMapper, PromptTemplate>
        implements PromptTemplateService {

    @Override
    public Page<PromptTemplate> pageQuery(int page, int size, String scene, String status) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(scene)) {
            wrapper.like(PromptTemplate::getScene, scene);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PromptTemplate::getStatus, status);
        }
        wrapper.orderByDesc(PromptTemplate::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public PromptTemplate create(PromptTemplate template) {
        // 默认状态为草稿
        if (!StringUtils.hasText(template.getStatus())) {
            template.setStatus(PromptTemplate.STATUS_DRAFT);
        }
        // 设置默认超参
        if (template.getTemperature() == null) {
            template.setTemperature(0.7);
        }
        if (template.getTopP() == null) {
            template.setTopP(1.0);
        }
        if (template.getMaxTokens() == null) {
            template.setMaxTokens(2048);
        }

        // 乐观分配版本号：利用唯一索引 idx_scene_version(scene, version) 防并发冲突，
        // 遇 DuplicateKeyException 自动重试，最多 3 次
        int maxRetries = 3;
        for (int attempt = 0; ; attempt++) {
            int nextVersion = queryNextVersion(template.getScene());
            template.setVersion(nextVersion);
            try {
                save(template);
                log.info("新增提示词模板: scene={}, version={}, id={}",
                        template.getScene(), template.getVersion(), template.getId());
                return template;
            } catch (DuplicateKeyException e) {
                if (attempt >= maxRetries - 1) {
                    throw new IllegalStateException(
                            "创建提示词模板失败：版本号冲突，已重试" + maxRetries + "次, scene="
                                    + template.getScene(), e);
                }
                log.warn("版本号冲突重试: scene={}, version={}, attempt={}",
                        template.getScene(), nextVersion, attempt + 1);
            }
        }
    }

    /**
     * 查询同场景下一个可用的版本号
     */
    private int queryNextVersion(String scene) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getScene, scene)
                .orderByDesc(PromptTemplate::getVersion)
                .last("LIMIT 1");
        PromptTemplate latest = getOne(wrapper, false);
        return (latest != null) ? latest.getVersion() + 1 : 1;
    }

    @Override
    @Transactional
    public PromptTemplate update(Long id, PromptTemplate template) {
        PromptTemplate existing = getById(id);
        if (existing == null) {
            return null;
        }
        template.setId(id);
        updateById(template);
        log.info("更新提示词模板: id={}, scene={}", id, existing.getScene());
        return getById(id);
    }

    @Override
    public List<PromptTemplate> getSceneVersions(String scene) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getScene, scene)
                .orderByDesc(PromptTemplate::getVersion);
        return list(wrapper);
    }

    @Override
    @Transactional
    public void archive(Long id) {
        PromptTemplate template = getById(id);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: id=" + id);
        }
        if (PromptTemplate.STATUS_ARCHIVED.equals(template.getStatus())) {
            throw new IllegalStateException("该模板已归档: id=" + id);
        }
        template.setStatus(PromptTemplate.STATUS_ARCHIVED);
        updateById(template);
        log.info("归档提示词模板: id={}, scene={}, version={}",
                id, template.getScene(), template.getVersion());
    }

    @Override
    @Transactional
    public void activate(Long id) {
        PromptTemplate template = getById(id);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: id=" + id);
        }
        if (PromptTemplate.STATUS_ACTIVE.equals(template.getStatus())) {
            return;
        }
        // 将同场景其他ACTIVE版本归档（确保一个场景只有一个ACTIVE版本）
        LambdaUpdateWrapper<PromptTemplate> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PromptTemplate::getScene, template.getScene())
                .eq(PromptTemplate::getStatus, PromptTemplate.STATUS_ACTIVE)
                .set(PromptTemplate::getStatus, PromptTemplate.STATUS_ARCHIVED);
        update(updateWrapper);
        // 激活目标版本
        template.setStatus(PromptTemplate.STATUS_ACTIVE);
        updateById(template);
        log.info("激活提示词模板: id={}, scene={}, version={}",
                id, template.getScene(), template.getVersion());
    }
}
