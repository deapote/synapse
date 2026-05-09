# Synapse - 多知识库 RAG 系统设计文档

## 1. 项目概述

基于 Spring Boot + LangChain4j 的多知识库 RAG（检索增强生成）系统。

**核心功能**：
- 多知识库管理（创建、删除）
- 多格式文档上传（PDF/DOCX/TXT）
- 自动解析、分块、向量化存储
- 语义检索问答（SSE 流式输出，带引用来源）
- 文档管理（列表、删除、状态追踪）

**技术栈**：Spring Boot 3.5.13 / WebFlux / LangChain4j 1.13.0 / Ollama / Milvus / MongoDB / Apache Tika

---

## 2. 架构决策

### 2.1 六边形架构（Ports & Adapters）+ DDD

选择六边形架构的原因：
- RAG 系统的底层技术变化快（LLM、向量库、解析器频繁替换）
- 六边形架构将技术变化关在 adapter 层，领域逻辑不受影响
- Maven 模块强制分层：`domain` → `application` → `adapter` → `config` → `bootstrap`

### 2.2 WebFlux（响应式）

- SSE 流式输出是核心用户体验
- 但 domain / application 层保持同步 API，响应式只留在 adapter 层

### 2.3 多知识库设计

- Milvus 共用 1 个 collection，通过 `knowledgeBaseId` metadata 字段过滤
- 避免每个知识库一个 collection（Milvus collection 数量有限制）

### 2.4 流式输出架构（关键决策）

真正的 RAG 流式 = 同步检索（快）+ 异步 LLM 生成（慢）。

`QueryKnowledgeBaseUseCase` 只提供 `prepare()` 方法，返回 `RagContext`（prompt + references）。Controller 先用 `prepare()` 得到 prompt，再用 adapter 层流式服务生成 token。

原因：如果 UseCase 返回完整 `Answer`，流式端点只能伪流式或重新生成。

---

## 3. 模块结构

```
synapse/
├── synapse-shared/              # 共享内核（DomainException 等）
├── synapse-kb-domain/           # 领域层 —— 纯 Java，零框架依赖
├── synapse-kb-application/      # 应用层 —— 用例编排，定义端口
├── synapse-kb-adapter/          # 适配器层 —— 所有技术实现
├── synapse-kb-config/           # Spring 配置组装
└── synapse-bootstrap/           # Spring Boot 启动入口
```

**Maven 依赖方向**：`shared` → `domain` → `application` → `adapter` → `config` → `bootstrap`

---

## 4. 领域模型

### 4.1 聚合根

**KnowledgeBase**：仅包含知识基本身元数据（id, name, description, createdAt）。`documentCount` 作为派生字段已去掉。

**Document**：独立聚合根（非 KnowledgeBase 内部实体），通过 `knowledgeBaseId` 关联。原因：避免大聚合性能问题。

### 4.2 实体

**Document**：
- `status`：PENDING → PROCESSING → COMPLETED/FAILED，支持重试（FAILED → PENDING）
- `transitionTo(DocumentStatus)` 校验状态流转合法性
- `contentHash`：文件内容哈希，用于知识库内去重
- `processingStartedAt` / `processingCompletedAt`：处理时间戳

### 4.3 值对象

| 值对象 | 字段 |
|--------|------|
| `DocumentChunk` | index, text, startPosition, endPosition |
| `Query` | knowledgeBaseId, text |
| `RagContext` | prompt, references |
| `Answer` | text, List<ChunkReference> |
| `ChunkReference` | documentId, documentName, chunkText, score, startPosition, endPosition |

---

## 5. 应用层端口

### 5.1 入站端口（UseCase）

```java
CreateKnowledgeBaseUseCase.create(CreateKnowledgeBaseCommand) → KnowledgeBaseId
DeleteKnowledgeBaseUseCase.delete(KnowledgeBaseId)

IngestDocumentUseCase.ingest(IngestDocumentCommand) → DocumentId
ListDocumentsUseCase.listByKnowledgeBase(KnowledgeBaseId) → List<Document>
DeleteDocumentUseCase.delete(DocumentId)

QueryKnowledgeBaseUseCase.prepare(Query) → RagContext
```

### 5.2 出站端口（SPI）

```java
VectorStorePort.store(String kbId, List<DocumentChunk>, List<float[]>) → void
VectorStorePort.search(String kbId, float[] embedding, int topK) → List<ChunkSearchResult>
VectorStorePort.deleteByDocumentId(String kbId, String docId) → void

EmbeddingPort.embed(String text) → float[]
EmbeddingPort.embed(List<String> texts) → List<float[]>   // 默认逐条 fallback

LlmPort.generate(String prompt) → String

DocumentParserPort.parse(InputStream, String fileName) → String

KnowledgeBaseRepository: save/findById/findAll/deleteById
DocumentRepository: save/findById/findByKnowledgeBaseId/deleteById/existsByKnowledgeBaseIdAndContentHash
```

---

## 6. API 设计

### 6.1 知识库管理

| 方法 | 路径 | 响应 |
|------|------|------|
| POST | `/knowledge-bases` | `{id, name, description, createdAt}` |
| GET | `/knowledge-bases` | `[{id, name, description, createdAt}]` |
| DELETE | `/knowledge-bases/{kbId}` | 204 |

### 6.2 文档管理

| 方法 | 路径 | 响应 |
|------|------|------|
| POST | `/knowledge-bases/{kbId}/documents` | `{id, fileName, status, uploadedAt}` |
| GET | `/knowledge-bases/{kbId}/documents` | `[{id, fileName, status, chunkCount, uploadedAt}]` |
| GET | `/documents/{docId}` | 文档详情（含 status、failureReason） |
| DELETE | `/documents/{docId}` | 204 |

### 6.3 问答

| 方法 | 路径 | 响应 |
|------|------|------|
| POST | `/query` | `{text, references:[{...}]}` |
| GET | `/query/stream` | SSE（token 流 + references 事件） |

---

## 7. 数据流

### 7.1 文档摄入

```
POST /knowledge-bases/{kbId}/documents (multipart file)
  → DocumentController
  → IngestDocumentUseCase.ingest(command)
  → ApplicationService
    ├─→ DocumentRepository.existsByKnowledgeBaseIdAndContentHash(kbId, hash)  // 去重
    ├─→ Document.create(kbId, ...)  // status = PENDING
    ├─→ DocumentRepository.save(doc)
    └─→ @Async 开始处理
          → doc.transitionTo(PROCESSING)
          → DocumentParserPort.parse(file) → Tika
          → RecursiveChunkingStrategy.split(text)
          → EmbeddingPort.embed(chunks)
          → VectorStorePort.store(kbId, chunks, embeddings) → Milvus
          → doc.setChunkCount(...)
          → doc.transitionTo(COMPLETED)
          → DocumentRepository.save(doc)
```

### 7.2 流式问答

```
GET /query/stream?kbId=xxx&text=yyy
  → QueryController
  → QueryKnowledgeBaseUseCase.prepare(query)      // 同步，< 500ms
    ├─→ EmbeddingPort.embed(text)
    ├─→ VectorStorePort.search(kbId, embedding, topK=5)
    └→ RagContext(prompt, references)
  → StreamingLlmService.stream(prompt)              // 异步流式
  → SSE: token → token → ... → references event
```

### 7.3 删除文档

```
DELETE /documents/{docId}
  → DocumentController
  → DeleteDocumentUseCase.delete(docId)
  → ApplicationService
    ├─→ VectorStorePort.deleteByDocumentId(kbId, docId)  // 先删向量
    └─→ DocumentRepository.deleteById(docId)             // 再删元数据
```

---

## 8. 适配器实现策略

| 适配器 | 技术实现 |
|--------|----------|
| `MongoKnowledgeBaseRepository` | Spring Data Reactive MongoDB |
| `MongoDocumentRepository` | Spring Data Reactive MongoDB |
| `MilvusVectorStoreAdapter` | LangChain4j `MilvusEmbeddingStore`，共用 collection + metadata 过滤 |
| `OllamaEmbeddingAdapter` | LangChain4j `OllamaEmbeddingModel`，覆盖批量接口 |
| `OllamaLlmAdapter` | LangChain4j `OllamaChatModel`，同步生成 |
| `StreamingLlmService` | LangChain4j `StreamingChatLanguageModel`，adapter 层内部组件（非端口） |
| `ApacheTikaDocumentParserAdapter` | LangChain4j `ApacheTikaDocumentParser` |

---

## 9. 配置

```yaml
server:
  port: 8082

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/synapse_kb

ollama:
  base-url: http://localhost:11434
  chat-model: qwen2.5:7b
  embedding-model: hf.co/sinequa/gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0

milvus:
  host: 127.0.0.1
  port: 19530
  collection-name: synapse_document_chunks
  embedding-dimension: 1536
  index-type: IVF_FLAT
  metric-type: COSINE
```

---

## 10. 扩展预留

- **对话历史**：后续新增 `Conversation` 聚合
- **用户权限**：后续新增 `synapse-auth` bounded context
- **多 Embedding 模型**：新增 `EmbeddingPort` 实现
- **混合搜索**：`VectorStorePort` 扩展关键词搜索接口
- **处理进度**：文档摄入增加进度百分比（当前只有 status）
- **定时清理**：处理卡住的 PROCESSING 文档、孤儿向量
