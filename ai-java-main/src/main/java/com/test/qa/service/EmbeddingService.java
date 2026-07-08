package com.test.qa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embedding 向量化 + ChromaDB 存储服务
 *
 * 负责：
 * 1. 调用 Ollama Embedding API 将文本转为向量
 * 2. 通过 ChromaDB REST API 存储和查询向量
 */
@Slf4j
@Service
public class EmbeddingService {

    private final WebClient ollamaWebClient;
    private final WebClient chromaWebClient;

    public EmbeddingService(@Qualifier("ollamaWebClient") WebClient ollamaWebClient,
                            @Qualifier("chromaWebClient") WebClient chromaWebClient) {
        this.ollamaWebClient = ollamaWebClient;
        this.chromaWebClient = chromaWebClient;
    }

    @Value("${ollama.embedding.model:nomic-embed-text}")
    private String ollamaEmbeddingModel;

    @Value("${chromadb.collection-name:qa_knowledge_base}")
    private String collectionName;

    /** ChromaDB v2 API 基础路径 */
    private static final String CHROMA_V2 = "/api/v2/tenants/default_tenant/databases/default_database/collections";

    /** 缓存的 collection UUID（ChromaDB 0.6+ 要求用 UUID 而非名称） */
    private volatile String collectionId;

    /**
     * 将单条文本转为向量，直接复用批量接口。
     */
    public List<Double> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量向量化：一次 HTTP 调用传入全部文本，返回对应的向量列表
     */
    @SuppressWarnings("unchecked")
    public List<List<Double>> embedBatch(List<String> texts) {
        Map<String, Object> requestBody = Map.of(
                "model", ollamaEmbeddingModel,
                "input", texts
        );
        Map<String, Object> response = ollamaWebClient.post()
                .uri("/v1/embeddings")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("data")) {
            throw new RuntimeException("Ollama Embedding 批量调用返回为空");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Ollama Embedding 批量调用返回空列表");
        }
        // 按 index 排序后提取 embedding
        List<List<Double>> embeddings = new ArrayList<>();
        for (Map<String, Object> item : data) {
            embeddings.add(toDoubleList(item.get("embedding")));
        }
        return embeddings;
    }

    /**
     * 将 Ollama 返回的 embedding 数组安全转换为 List&lt;Double&gt;。
     * Jackson 反序列化时小整数可能为 Integer，直接用 (List&lt;Double&gt;) 强转会抛 ClassCastException。
     */
    private static List<Double> toDoubleList(Object embedding) {
        if (embedding instanceof List<?> list) {
            List<Double> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Number num) {
                    result.add(num.doubleValue());
                } else {
                    throw new RuntimeException("Ollama Embedding 返回非数字元素: " + item.getClass());
                }
            }
            return result;
        }
        throw new RuntimeException("Ollama Embedding 返回格式异常");
    }

    /**
     * 将向量及对应文本存入 ChromaDB，返回每个 chunk 的向量 ID 列表。
     *
     * @param chunks     原始文本块
     * @param embeddings 对应的向量（顺序须一致）
     */
    public List<String> storeEmbeddings(List<String> chunks, List<List<Double>> embeddings) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        String collId = getCollectionId();

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ids.add(UUID.randomUUID().toString());
        }

        try {
            Map<String, Object> body = Map.of(
                    "ids", ids,
                    "embeddings", embeddings,
                    "documents", chunks
            );
            log.info("ChromaDB add: collection={}, ids.size={}, dim={}",
                    collectionName, ids.size(),
                    embeddings.isEmpty() ? 0 : embeddings.get(0).size());

            chromaWebClient.post()
                    .uri(CHROMA_V2 + "/{coll_id}/add", collId)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("存入ChromaDB: collection={}, count={}", collectionName, ids.size());
        } catch (WebClientResponseException e) {
            log.error("ChromaDB存储失败 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("向量存储失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("ChromaDB存储失败: {}", e.getMessage());
            throw new RuntimeException("向量存储失败: " + e.getMessage(), e);
        }
        return ids;
    }

    /**
     * 查询向量：将问题向量化 → ChromaDB 语义检索
     *
     * @param queryText 查询文本
     * @param topK      返回数量
     * @return 相似chunk的ID列表
     */
    @SuppressWarnings("unchecked")
    public List<String> query(String queryText, int topK) {
        String collId = getCollectionId();
        List<List<Double>> queryEmbeddings = List.of(embed(queryText));

        try {
            Map<String, Object> body = Map.of(
                    "query_embeddings", queryEmbeddings,
                    "n_results", topK
            );
            Map<String, Object> response = chromaWebClient.post()
                    .uri(CHROMA_V2 + "/{coll_id}/query", collId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("ids")) {
                return List.of();
            }
            List<List<String>> idsList = (List<List<String>>) response.get("ids");
            if (idsList == null || idsList.isEmpty()) {
                return List.of();
            }
            log.info("ChromaDB检索: query='{}', topK={}, results={}", queryText, topK, idsList.get(0).size());
            return idsList.get(0);
        } catch (Exception e) {
            log.error("ChromaDB查询失败", e);
            throw new RuntimeException("向量检索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 ChromaDB 删除指定 ID 的向量
     */
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String collId = getCollectionId();
        try {
            chromaWebClient.post()
                    .uri(CHROMA_V2 + "/{coll_id}/delete", collId)
                    .bodyValue(Map.of("ids", ids))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("ChromaDB向量已删除: collection={}, count={}", collectionName, ids.size());
        } catch (Exception e) {
            log.error("ChromaDB删除向量失败: collection={}, count={}", collectionName, ids.size(), e);
        }
    }

    // ================================================================
    // Collection 管理
    // ================================================================

    /**
     * 获取 collection UUID，缓存下来复用。
     * 先尝试创建（拿到 UUID），若已存在则从列表中按名称查找。
     */
    @SuppressWarnings("unchecked")
    private String getCollectionId() {
        if (collectionId != null) {
            return collectionId;
        }
        synchronized (this) {
            if (collectionId != null) {
                return collectionId;
            }
            // 1. 尝试创建，拿到 UUID
            try {
                Map<String, Object> resp = chromaWebClient.post()
                        .uri(CHROMA_V2)
                        .bodyValue(Map.of("name", collectionName))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                if (resp != null && resp.containsKey("id")) {
                    collectionId = (String) resp.get("id");
                    log.info("ChromaDB collection 已创建: name={}, id={}", collectionName, collectionId);
                    return collectionId;
                }
            } catch (Exception e) {
                log.info("ChromaDB collection 创建失败(可能已存在): {}", e.getMessage());
            }

            // 2. 已存在：从列表中按名称查找 UUID
            try {
                List<Map<String, Object>> collections = chromaWebClient.get()
                        .uri(CHROMA_V2)
                        .retrieve()
                        .bodyToMono(List.class)
                        .block();
                if (collections != null) {
                    for (Map<String, Object> col : collections) {
                        if (collectionName.equals(col.get("name"))) {
                            collectionId = (String) col.get("id");
                            log.info("ChromaDB collection 已绑定: name={}, id={}", collectionName, collectionId);
                            return collectionId;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("ChromaDB collection 列表查询失败: {}", e.getMessage());
            }

            throw new RuntimeException("无法获取 ChromaDB collection: " + collectionName);
        }
    }
}
