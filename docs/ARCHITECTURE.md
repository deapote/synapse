# Synapse 架构设计文档

## 1. 系统概述

基于 Spring Boot + LangChain4j 的多知识库 RAG（检索增强生成）系统。
支持多格式文档上传、自动解析分块、向量化存储、语义检索问答。

## 2. 模块结构

```
synapse/
├── synapse-shared/              # 共享内核
│   └── exception/DomainException.java
├── synapse-kb-domain/           # 领域层 —— 纯 Java，零框架依赖
│   ├── model/                   # 聚合根、值对象、枚举
│   ├── repository/              # 仓储接口
│   └── service/                 # 领域服务（纯算法）
├── synapse-kb-application/      # 应用层 —— 用例编排，定义端口
│   ├── port/in/                 # 入站端口（UseCase）
│   ├── port/out/                # 出站端口（SPI）
│   └── service/                 # 应用服务（编排器）
├── synapse-kb-adapter/          # 适配器层 —— 所有技术实现
│   ├── in/web/                  # WebFlux Controller + DTO
│   └── out/                     # 出站适配器
│       ├── embedding/           # Ollama Embedding
│       ├── llm/                 # Ollama LLM
│       ├── parser/              # Apache Tika
│       ├── persistence/         # MongoDB Reactive
│       └── vector/              # Milvus
├── synapse-kb-config/           # Spring Bean 组装配置
│   └── config/KnowledgeBaseBeanConfig.java
└── synapse-kb-bootstrap/        # Spring Boot 启动入口
    └── resources/application.yaml
```

## 3. 端口清单

### 入站端口（UseCase）

| 端口 | 职责 |
|------|------|
| `CreateKnowledgeBaseUseCase` | 创建知识库 |
| `ListKnowledgeBaseUseCase` | 列出所有知识库 |
| `DeleteKnowledgeBaseUseCase` | 删除知识库（级联清理） |
| `IngestDocumentUseCase` | 上传并处理文档 |
| `ListDocumentUseCase` | 列出知识库下的文档 |
| `DeleteDocumentUseCase` | 删除文档（连带清理向量） |
| `QueryKnowledgeBaseUseCase` | 检索 + 组装 prompt，返回 `RagContext` |

### 出站端口（Port）

| 端口 | 职责 | 当前实现 |
|------|------|----------|
| `VectorStorePort` | 向量存储与检索 | `MilvusVectorStoreAdapter` |
| `EmbeddingPort` | 文本向量化 | `OllamaEmbeddingAdapter` |
| `StreamingLlmPort` | LLM 流式文本生成 | `OllamaStreamingLlmAdapter` |
| `DocumentParserPort` | 文档解析为纯文本 | `ApacheTikaDocumentParserAdapter` |
| `KnowledgeBaseRepository` | 知识库元数据持久化 | `MongoKnowledgeBaseRepository` |
| `DocumentRepository` | 文档元数据持久化 | `MongoDocumentRepository` |

## 4. 数据流

### 4.1 文档摄入流程

```
用户上传文件
    |
    v
DocumentController.upload() — WebFlux 接收 multipart FilePart
    |
    v
IngestDocumentUseCase.ingest()
    |
    v
KnowledgeBaseApplicationService
    |
    +-- DocumentRepository.save() → MongoDB（状态 PENDING）
    |
    +-- processDocument():
        |
        +-- DocumentParserPort.parse() → ApacheTikaDocumentParserAdapter
        +-- RecursiveChunkingStrategy.split() → domain service（纯算法）
        +-- EmbeddingPort.embed() → OllamaEmbeddingAdapter
        +-- VectorStorePort.store() → MilvusVectorStoreAdapter
        +-- DocumentRepository.save() → MongoDB（状态 COMPLETED/FAILED）
```

### 4.2 问答流程（SSE 流式）

```
用户提问
    |
    v
StreamingQueryController.queryStream() — SSE 端点
    |
    v
QueryKnowledgeBaseUseCase.prepare()
    |
    +-- EmbeddingPort.embed() → OllamaEmbeddingAdapter
    +-- VectorStorePort.search() → MilvusVectorStoreAdapter（topK=5）
    |
    v
RagContext（prompt + references）
    |
    v
StreamingLlmPort.generateStream() → OllamaStreamingLlmAdapter
    |
    v
SSE 事件流：token → token → ... → references → complete
```

## 5. API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge-bases` | 创建知识库 |
| GET | `/api/knowledge-bases` | 列出知识库 |
| DELETE | `/api/knowledge-bases/{id}` | 删除知识库（级联） |
| POST | `/api/knowledge-bases/{kbId}/documents` | 上传文档 |
| GET | `/api/knowledge-bases/{kbId}/documents` | 列出文档 |
| DELETE | `/api/documents/{id}` | 删除文档 |
| POST | `/api/knowledge-bases/{kbId}/query/stream` | 知识库流式问答（SSE） |

### 错误响应格式

```json
{
  "error": "BUSINESS_ERROR",
  "message": "此知识库已存在相同内容的文档",
  "timestamp": "2026-05-12T00:00:00Z"
}
```

## 6. 领域模型详情

### Document 状态机

```
PENDING --(ingest)--> PROCESSING --(success)--> COMPLETED
                          |
                          +--(failure)--> FAILED
                          |
FAILED --(retry)--> PENDING
```

- `transitionTo()` 校验流转合法性
- PROCESSING → COMPLETED/FAILED 时自动记录时间戳

### 关键设计决策

1. **Document 是独立聚合根**：知识库可能包含成百上千个文档，作为内部实体会导致大聚合性能问题。
2. **懒加载 Milvus 连接**：`MilvusVectorStoreAdapter` 不在构造函数中连接服务器，第一次使用时才初始化，避免启动依赖问题。
3. **配置外部化**：所有 adapter 通过 `@Value` 读取 `application.yaml`，无硬编码。
4. **filter 注入防护**：Milvus filter 字符串拼接前对值进行单引号转义。

## 7. 外部依赖

| 服务 | 地址 | 用途 |
|------|------|------|
| MongoDB | mongodb://localhost:27017/synapse_kb | 文档/知识库元数据 |
| Ollama | http://localhost:11434 | LLM + Embedding 推理 |
| Milvus | http://127.0.0.1:19530 | 向量存储与相似度检索 |

## 8. 扩展预留

- **对话历史**：后续在 `synapse-kb-domain` 中新增 `Conversation` 聚合。
- **用户权限**：后续新增 `synapse-auth` bounded context，通过 Anti-Corruption Layer 与知识库交互。
- **多 Embedding 模型**：新增 `EmbeddingPort` 的实现即可，无需改动应用层。
- **混合搜索**：向量搜索 + 关键词搜索（BM25），在 `VectorStorePort` 中扩展接口。
- **流式输出**：已通过 SSE 实现，支持逐字推送和取消生成。
