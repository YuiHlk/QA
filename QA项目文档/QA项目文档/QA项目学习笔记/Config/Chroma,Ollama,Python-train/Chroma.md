## 一、Chroma 是什么

1. **定位**：轻量开源向量数据库，专门存**浮点向量**、做**余弦相似度检索**
2. **部署方式**：你用 `chromadb/chroma:latest` Docker 镜像独立运行（截图容器`qa-chromadb`，端口`8000:8000`）
3. **本质**：容器内是一套 Python FastAPI 后端，内置一套固定 RESTful 接口（`/add`/`/query`/`/delete`等）
4. **数据存储**：容器内`/data`目录持久化向量索引、文本、自定义 ID，重启不丢失
5. **关键提醒**：只有 API 接口，**无可视化网页**，浏览器直接访问`localhost:8000`只会 404，只能代码 / Postman 调用

## 二、分层角色拆分（分清 Java 和 Chroma 各自干什么）

### 1）Java（EmbeddingService）= 客户端

只做三件事：

- 调用 Ollama 把文本转为向量
- 按 Chroma 规定格式组装 HTTP 请求，调用 Chroma 内置 API
- 解析 Chroma 返回的 JSON 结果给上层业务

### 2）Chroma 服务 = 服务端（向量数据库本体）

内置全部向量处理逻辑：

- 维护集合 Collection（相当于数据表）
- 持久化存储 `自定义UUID(ids) + 向量embedding + 原文document`
- 收到`/query`请求自动计算向量相似度，返回匹配的 ids
- 收到`/add`请求写入向量索引持久化

## 三、Chroma 核心概念（对应代码里的变量）

1. **Collection 集合**
    
    向量的容器，一张 “向量表”，代码里`qa_knowledge`，对应配置`collectionName`；
    
    所有 add/query 操作必须传集合唯一`collId`（Chroma 内部自动生成 UUID，缓存到`collectionId`变量）
2. **ids**
    
    客户端（你的 Java）生成的全局唯一 UUID，作为每条向量的主键，存入时传给 Chroma，检索时 Chroma 原样返回
3. **embeddings 向量**
    
    文本经过 Nomic-embed-text 模型算出的浮点数组，语义数字化载体，相似度比对依靠它
4. **documents**
    
    原始文本片段，随向量一起存入 Chroma 副本，项目仅作备用，业务权威文本存在 MySQL

## 四、三大核心 API 逐段对照代码讲解

### API1：POST /{coll_id}/add 向量存入接口（写操作）

#### 对应代码 storeEmbeddings 方法

```java
// 1. Java层准备数据
// 传入文本块、对应向量
public List<String> storeEmbeddings(List<String> chunks, List<List<Double>> embeddings) {
    String collId = getCollectionId(); // 获取Chroma集合UUID
    // 自己生成每条向量唯一主键ids
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
        ids.add(UUID.randomUUID().toString());
    }
    // 2. 严格按照Chroma规定组装请求体
    Map<String, Object> body = Map.of(
            "ids", ids,
            "embeddings", embeddings,
            "documents", chunks
    );
    // 3. 调用Chroma内置add接口
    chromaWebClient.post()
            .uri(CHROMA_V2 + "/{coll_id}/add", collId)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity() // add无返回业务JSON，只看状态200
            .block();
    return ids;
}
```

#### Chroma 服务内部执行逻辑：

1. 接收 POST 请求，解析 JSON 拿到`ids、embeddings、documents`
2. 根据路径`collId`找到目标 Collection
3. 三者一一绑定，写入 HNSW 向量索引（用于快速检索）
4. 持久化保存到容器`/data`文件夹
5. 返回空 200 响应，无数据

#### 数据流：

Java 生成 ids → 打包向量文本发给 Chroma → Chroma 永久绑定存储

---

### API2：POST /{coll_id}/query 语义检索接口（只读查询）

#### 对应代码 query 方法

```java
public List<String> query(String queryText, int topK) {
    String collId = getCollectionId();
    // 1. 用户提问文本 → 调用Ollama转为查询向量
    List<List<Double>> queryEmbeddings = List.of(embed(queryText));
    // 2. 按Chroma规范组装查询请求体（这里没有ids！）
    Map<String, Object> body = Map.of(
            "query_embeddings", queryEmbeddings,
            "n_results", topK
    );
    // 3. 调用Chroma内置query检索接口
    Map<String, Object> response = chromaWebClient.post()
            .uri(CHROMA_V2 + "/{coll_id}/query", collId)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class) // 需要读取返回的ids、距离等JSON
            .block();
    // 4. 解析Chroma返回结果
    List<List<String>> idsList = (List<List<String>>) response.get("ids");
    return idsList.get(0);
}
```

#### Chroma 服务内部执行逻辑（核心语义检索原理）

1. 接收查询向量二维数组`query_embeddings`、返回条数`n_results`
2. 在当前 Collection 加载全部已存入向量索引
3. 计算**查询向量和库内所有向量的余弦相似度**
4. 按相似度从高到低排序，截取前 topK 条
5. 取出当初存入时绑定的 ids、相似度距离、原文，封装 JSON 返回给 Java

```json
// Chroma返回response结构
{
  "ids": [["uuid1","uuid2"]], // 双层数组：外层=查询数量，内层=匹配id
  "distances": [[0.12,0.35]]
}
```

#### 为什么返回双层 ids 数组？

Chroma 接口支持批量传入多个问题向量同时检索；

你一次只传 1 个用户问题，所以取`idsList.get(0)`拿到一维 id 列表。

#### 数据流：

Java 只发问题向量 → Chroma 内部比对全部存量向量 → 返回匹配向量的 ids

---

### API3：POST /{coll_id}/delete 删除向量接口

#### 对应代码 deleteByIds 方法

```java
public void deleteByIds(List<String> ids) {
    String collId = getCollectionId();
    chromaWebClient.post()
            .uri(CHROMA_V2 + "/{coll_id}/delete", collId)
            .bodyValue(Map.of("ids", ids))
            .retrieve()
            .toBodilessEntity()
            .block();
}
```

#### Chroma 逻辑：

根据传入的 ids，在集合中删除对应向量、元数据，清理向量索引。

业务场景：后台删除 PDF 文档时，同步清理 Chroma 里无用向量。

## 五、公共工具方法 getCollectionId () 与集合生命周期

```java
private String getCollectionId() {
    if (collectionId != null) return collectionId;
    synchronized (this) {
        // 1. 尝试创建同名集合，Chroma不存在则新建，返回集合UUID
        Map<String, Object> resp = chromaWebClient.post()
                .uri(CHROMA_V2)
                .bodyValue(Map.of("name", collectionName))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        collectionId = (String) resp.get("id");
        return collectionId;
    }
}
```

1. 每个集合拥有唯一全局 UUID（`collId`），所有 add/query/delete 操作必须携带
2. Java 缓存到成员变量`collectionId`，不用每次请求都创建 / 查询集合
3. Chroma 逻辑：集合不存在自动创建，存在直接返回已有 ID

## 六、WebClient 网络层统一规则（区分 add/query 写法差异）

### 1）写入接口（add/delete）：`.toBodilessEntity()`

```java
.retrieve().toBodilessEntity().block();
```

原因：写入操作成功无返回 JSON，仅需判断 HTTP 200 状态，不需要解析数据。

### 2）查询接口（query）：`.bodyToMono(Map.class)`

```java
.retrieve().bodyToMono(Map.class).block();
```

原因：检索需要读取返回的`ids`、`distances`等业务数据，Jackson 自动把响应 JSON 转为 Map 对象。

### 统一`.block()`作用

Chroma 的 WebClient 是 WebFlux 响应式惰性加载，不加 block 不会发起真实网络请求；

block 阻塞线程同步等待 Chroma 返回数据，适配你的 MVC 同步业务代码。

## 七、完整业务全链路（Java ↔ Chroma ↔ MySQL）

### 阶段 1：上传文档存入向量库

1. 文档文本 → TextChunkService 切割成 chunks
2. embeddingService.embedBatch () 调用 Ollama 批量生成向量
3. embeddingService.storeEmbeddings(chunks, vectors)
    
    - Java 生成一批 UUID ids
    - HTTP 调用 Chroma `/add`，存入向量 + 文本 + ids
    - Chroma 持久化数据，返回成功
    
4. 上层 DocumentService 拿到 ids，存入 MySQL `rag_chunk`表，建立`文本<->向量id`关联

### 阶段 2：用户提问语义检索

1. 前端传入问题，调用`embeddingService.query(question,5)`
2. 内部把问题转向量，调用 Chroma `/query`
3. Chroma 相似度计算，返回相似向量 ids 列表
4. 上层拿 ids 去 MySQL 批量查询完整知识库文本
5. 拼接召回文本 + 用户问题，组装 Prompt 调用 LLM 生成回答