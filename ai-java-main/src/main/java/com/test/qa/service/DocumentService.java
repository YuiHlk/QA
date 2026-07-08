package com.test.qa.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.test.qa.domain.RagDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 Service
 */
public interface DocumentService extends IService<RagDocument> {

    /**
     * 上传文档并异步处理（解析→分块→向量化→入库）
     *
     * @param file         上传的文件
     * @param chunkSize    分块大小
     * @param chunkOverlap 分块重叠
     * @return 创建的文档记录
     */
    RagDocument upload(MultipartFile file, int chunkSize, int chunkOverlap);

    /**
     * 分页查询文档列表
     */
    Page<RagDocument> pageQuery(int page, int size, String status);

    /**
     * 删除文档（同时删除关联的分块记录）
     */
    void deleteDocument(Long id);

    /**
     * 异步处理文档（解析→分块→向量化→入库）
     */
    void processDocument(Long documentId);

    /**
     * 将分块和向量ID写入 MySQL 并更新文档状态（事务保护）
     */
    void saveChunksAndUpdateStatus(Long documentId, List<String> chunks, List<String> embeddingIds);
}
