# Synapse - 知识库 RAG 系统

## 项目概述

基于 Spring Boot + LangChain4j 的多知识库 RAG（检索增强生成）系统。
支持多格式文档上传、自动解析分块、向量化存储、语义检索问答（SSE 流式输出，带引用来源）。

## 技术栈

| 层级 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.13 |
| 响应式 | Spring WebFlux | |
| AI 编排 | LangChain4j | 1.13.0 |
| LLM | Ollama | qwen2.5:7b |
| Embedding | Ollama | gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0 (1536维) |
| 向量存储 | Milvus | Standalone |
| 文档元数据 | MongoDB | Reactive |
| 文档解析 | Apache Tika + PDFBox | |
| 构建工具 | Maven | |

## 模块结构

```
synapse/
├── synapse-shared/              # 共享内核
├── synapse-kb-domain/           # 领域层 —— 纯 Java，零框架依赖
├── synapse-kb-application/      # 应用层 —— 用例编排，定义端口
├── synapse-kb-adapter/          # 适配器层 —— 所有技术实现
├── synapse-kb-config/           # Spring 配置组装
└── synapse-bootstrap/           # Spring Boot 启动入口
```

### Maven 依赖方向（不可违反）

```
synapse-shared
    ↑
synapse-kb-domain
    ↑
synapse-kb-application
    ↑
synapse-kb-adapter
    ↑
synapse-kb-config
    ↑
synapse-bootstrap
```

**synapse-kb-domain 的 pom.xml 禁止包含以下依赖：**
- `org.springframework.*`
- `dev.langchain4j.*`
- `reactor-core`
- `io.projectreactor.*`

## 分层规范

### domain 层（synapse-kb-domain）

- **纯 Java**。不 import 任何框架包。
- **充血模型**。Entity 包含业务方法和校验逻辑，不是只有 getter/setter 的数据容器。
- **值对象（Value Object）**。用 Java `record` 或 final class，不可变。
- **仓储接口**。只定义接口，不实现。返回 `Optional<Aggregate>`，不暴露基础设施细节。

```java
// 正确：领域层纯 Java
public class KnowledgeBase {
    private KnowledgeBaseId id;
    private String name;
    private String description;
    private Instant createdAt;

    public static KnowledgeBase create(String name, String description) {
        validateName(name);
        return new KnowledgeBase(KnowledgeBaseId.generate(), name, description, Instant.now());
    }
}

public class Document {
    private DocumentId id;
    private KnowledgeBaseId knowledgeBaseId;
    private String fileName;
    private DocumentStatus status;
    private String contentHash;
    private Instant processingStartedAt;
    private Instant processingCompletedAt;

    public void transitionTo(DocumentStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new DomainException("Invalid transition: " + status + " -> " + newStatus);
        }
        this.status = newStatus;
    }
}

// 错误：领域层引入 Spring
@Entity  // ❌ 禁止
public class KnowledgeBase { }
```

### application 层（synapse-kb-application）

- **用例编排**。协调多个端口完成一个业务场景。
- **入站端口（Driving Ports）**。命名 `*UseCase`，定义在 `port/in/` 下。
- **出站端口（Driven Ports / SPI）**。命名 `*Port`，定义在 `port/out/` 下。
- **同步 API**。返回领域对象，不返回 `Mono`/`Flux`。

```java
// 入站端口
public interface IngestDocumentUseCase {
    void ingest(IngestDocumentCommand command);
}

// 出站端口
public interface VectorStorePort {
    void store(String knowledgeBaseId, List<DocumentChunk> chunks, List<Embedding> embeddings);
    List<ChunkSearchResult> search(String knowledgeBaseId, Embedding queryEmbedding, int topK);
}
```

### adapter 层（synapse-kb-adapter）

- **入站适配器**。WebFlux Controller，接收 HTTP 请求，调用 application 层 UseCase。
- **出站适配器**。实现 application 层定义的 SPI 端口。
- **响应式在这里**。Controller 返回 `Mono<>`，内部用 `Mono.fromCallable(() -> useCase.execute(cmd))`。
- **LangChain4j 只在这里**。`LangChain4jRAGPipelineAdapter`、`OllamaEmbeddingAdapter` 等。

```java
// WebFlux Controller（入站适配器）
@RestController
public class DocumentController {
    private final IngestDocumentUseCase ingestUseCase;

    @PostMapping("/knowledge-bases/{kbId}/documents")
    public Mono<Void> upload(@PathVariable String kbId, @RequestPart("file") FilePart file) {
        return Mono.fromCallable(() -> {
            ingestUseCase.ingest(new IngestDocumentCommand(kbId, file));
            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

// Milvus 适配器（出站适配器）
@Component
public class MilvusVectorStoreAdapter implements VectorStorePort {
    // 实现 SPI 接口
}
```

### config 层（synapse-kb-config）

- **Bean 组装**。用 `@Configuration` 创建 Bean，将 adapter 实现注入到 application 服务中。
- **不定义业务逻辑**。只负责依赖注入和配置属性绑定。

## 领域模型约定

### 聚合根

- `KnowledgeBase`：聚合根，仅包含知识基本身元数据（id, name, description, createdAt）。
- `Document`：**独立聚合根**（非 KnowledgeBase 的内部实体），通过 `knowledgeBaseId` 关联到知识库。
  - 原因：知识库可能包含成百上千个文档，作为内部实体会导致大聚合性能问题。

### 实体（Entity）

- `Document`：独立聚合根，有完整生命周期。
  - `status` 字段：PENDING → PROCESSING → COMPLETED/FAILED，支持重试（FAILED → PENDING）。
  - `transitionTo(DocumentStatus)` 方法校验状态流转合法性。
  - `contentHash`：文件内容哈希，用于去重。
  - `processingStartedAt` / `processingCompletedAt`：处理时间戳，用于运维监控。

### 值对象（Value Object）

- `DocumentChunk`：文档分块，不包含 vector（vector 是基础设施概念）。
  - `index`, `text`, `startPosition`, `endPosition`
- `Query`：用户查询。
  - `knowledgeBaseId`, `text`
- `RagContext`：检索上下文（**新增**，解决流式输出架构问题）。
  - `prompt`：组装好的 prompt（含检索上下文）
  - `references`：引用来源列表
- `Answer`：LLM 完整回答（非流式场景用）。
  - `text`, `List<ChunkReference>`
- `ChunkReference`：引用来源。
  - `documentId`：来源文档唯一标识
  - `documentName`：来源文档名
  - `chunkText`：被引用的片段文本
  - `score`：相似度分数
  - `startPosition` / `endPosition`：在原文中的位置（前端高亮用）

### 枚举

- `DocumentStatus`：PENDING, PROCESSING, COMPLETED, FAILED

## 端口清单

### 入站端口（UseCase）

| 端口 | 职责 |
|------|------|
| `CreateKnowledgeBaseUseCase` | 创建知识库 |
| `IngestDocumentUseCase` | 上传并处理文档 |
| `QueryKnowledgeBaseUseCase` | **检索 + 组装 prompt，返回 `RagContext`** |
| `ListDocumentsUseCase` | 列出知识库下的文档 |
| `DeleteDocumentUseCase` | 删除文档（连带清理向量） |
| `DeleteKnowledgeBaseUseCase` | 删除知识库（级联清理） |

### 出站端口（Port）

| 端口 | 职责 | 当前实现 |
|------|------|----------|
| `VectorStorePort` | 向量存储与检索 | `MilvusVectorStoreAdapter` |
| `EmbeddingPort` | 文本向量化 | `OllamaEmbeddingAdapter` |
| `LlmPort` | LLM 文本生成 | `OllamaLlmAdapter` |
| `DocumentParserPort` | 文档解析为纯文本 | `ApacheTikaDocumentParserAdapter` |
| `RAGPipelinePort` | 高层 RAG 编排（可选） | `LangChain4jRAGPipelineAdapter` |
| `KnowledgeBaseRepository` | 知识库元数据持久化 | `MongoKnowledgeBaseRepository` |
| `DocumentRepository` | 文档元数据持久化 | `MongoDocumentRepository` |

## 数据流

### 文档摄入流程

```
用户上传文件
    ↓
Web Controller（adapter/in/web）
    ↓
IngestDocumentUseCase（application/port/in）
    ↓
KnowledgeBaseApplicationService（application/service）
    ↓
  ├─→ DocumentParserPort.parse() → ApacheTikaDocumentParserAdapter
  ├─→ RecursiveChunkingStrategy.split() → domain/service（纯算法）
  ├─→ EmbeddingPort.embed() → OllamaEmbeddingAdapter
  ├─→ VectorStorePort.store() → MilvusVectorStoreAdapter
  └─→ DocumentRepository.save() → MongoDocumentRepository
```

### 问答流程（流式 SSE）

```
用户提问
    ↓
Web Controller（SSE 流式端点）
    ↓
QueryKnowledgeBaseUseCase.prepare(query)  // 同步：检索 + 组装 prompt
    ↓
  ├─→ EmbeddingPort.embed(query.text()) → OllamaEmbeddingAdapter
  ├─→ VectorStorePort.search(kbId, embedding, topK=5) → MilvusVectorStoreAdapter
  └→ 返回 RagContext（prompt + references）
    ↓
StreamingLlmService.stream(RagContext.prompt())  // 异步：流式生成 token
    ↓
SSE 事件序列：token → token → ... → token → references 事件
```

### 问答流程（非流式）

```
用户提问
    ↓
Web Controller（同步端点）
    ↓
QueryKnowledgeBaseUseCase.prepare(query)
    ↓
LlmPort.generate(RagContext.prompt()) → OllamaLlmAdapter
    ↓
组装 Answer（text + references）
    ↓
返回 Answer
```

## 编码规范

### 命名约定

| 层级 | 类命名 | 包命名 |
|------|--------|--------|
| 领域实体 | `KnowledgeBase`, `Document` | `kb.model` |
| 领域服务 | `RecursiveChunkingStrategy` | `kb.service` |
| 仓储接口 | `DocumentRepository` | `kb.repository` |
| 入站端口 | `IngestDocumentUseCase` | `kb.port.in` |
| 出站端口 | `VectorStorePort` | `kb.port.out` |
| 应用服务 | `KnowledgeBaseApplicationService` | `kb.service` |
| Controller | `DocumentController` | `kb.adapter.in.web` |
| 出站适配器 | `MilvusVectorStoreAdapter` | `kb.adapter.out.vector` |
| MongoDB 实体 | `DocumentDocument` | `kb.adapter.out.persistence.entity` |

### 响应式边界

- **domain / application 层**：完全同步 API。
- **adapter/in/web**：Controller 返回 `Mono<>` / `Flux<>`。
- **adapter/out/llm**：Ollama 调用是阻塞的，用 `Schedulers.boundedElastic()` 包装。
- **adapter/out/persistence**：使用 Reactive MongoDB 驱动。

### 异常处理

- **领域异常**：`DomainException`（运行时异常），在 domain 层定义。
- **应用异常**：在 application 层定义，继承 `DomainException`。
- **适配器异常**：在 adapter 层捕获并转换为领域/应用异常。
- **全局异常处理**：在 `kb.adapter.in.web` 中定义 `@ControllerAdvice`。

## 禁止事项

1. **domain 层禁止引入任何框架依赖**（Spring、LangChain4j、Reactor）。
2. **application 层禁止直接实例化 adapter 实现类**。只能通过端口接口调用。
3. **adapter 层禁止直接调用另一个 adapter**。必须通过 application 层端口。
4. **禁止贫血领域模型**。Entity 必须有业务方法和校验逻辑。
5. **禁止在 domain 层使用 `@Autowired`, `@Entity`, `@Document` 等注解**。
6. **禁止跨知识库查询**。`VectorStorePort.search()` 必须传入 `knowledgeBaseId`。

## 配置约定

### application.yml

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

## 扩展预留

- **对话历史**：后续在 `synapse-kb-domain` 中新增 `Conversation` 聚合。
- **用户权限**：后续新增 `synapse-auth` bounded context，通过 Anti-Corruption Layer 与知识库交互。
- **多 Embedding 模型**：新增 `EmbeddingPort` 的实现即可，无需改动应用层。
- **混合搜索**：向量搜索 + 关键词搜索（BM25），在 `VectorStorePort` 中扩展接口。
