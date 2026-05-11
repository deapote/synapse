# Synapse - 知识库 RAG 系统

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
| 文档解析 | Apache Tika | |
| 构建工具 | Maven | Java 21 |

## 模块结构与依赖方向

```
synapse/
├── synapse-shared/              # 共享内核（DomainException）
├── synapse-kb-domain/           # 领域层 —— 纯 Java，零框架依赖
├── synapse-kb-application/      # 应用层 —— 用例编排，定义端口
├── synapse-kb-adapter/          # 适配器层 —— 所有技术实现
├── synapse-kb-config/           # Spring Bean 组装
└── synapse-kb-bootstrap/        # Spring Boot 启动入口
```

**依赖方向（不可违反）：**
```
shared ← domain ← application ← adapter ← config ← bootstrap
```

## 分层规范

### domain 层（synapse-kb-domain）

- **纯 Java**。禁止引入 `org.springframework.*`、`dev.langchain4j.*`、`reactor-core`。
- **充血模型**。Entity 包含业务方法和校验逻辑。
- **值对象**。用 Java `record` 或 final class，不可变。
- **仓储接口**。只定义接口，返回 `Optional<Aggregate>`。

### application 层（synapse-kb-application）

- **用例编排**。协调多个端口完成一个业务场景。
- **入站端口（Driving Ports）**。命名 `*UseCase`，定义在 `port/in/` 下。
- **出站端口（Driven Ports / SPI）**。命名 `*Port`，定义在 `port/out/` 下。
- **同步 API**。返回领域对象，不返回 `Mono`/`Flux`。

### adapter 层（synapse-kb-adapter）

- **入站适配器**。WebFlux Controller，返回 `Mono<>`，`Mono.fromCallable + Schedulers.boundedElastic()` 包装同步调用。
- **出站适配器**。实现 application 层 SPI。
- **LangChain4j 只在这里**。所有外部技术依赖（Ollama、Milvus、Tika）仅限本层。
- **全局异常处理**。`GlobalExceptionHandler` 统一捕获异常，返回结构化 JSON。

### config 层（synapse-kb-config）

- **Bean 组装**。`@Configuration` 创建无 Spring 注解的 domain/application Bean。
- **不定义业务逻辑**。只负责依赖注入。

## 领域模型核心约定

### 聚合根

- `KnowledgeBase`：聚合根，仅元数据（id, name, description, createdAt）。
- `Document`：**独立聚合根**（非 KnowledgeBase 内部实体），通过 `knowledgeBaseId` 关联。

### 实体状态机

- `Document.status`：PENDING → PROCESSING → COMPLETED/FAILED，支持 FAILED → PENDING 重试。
- `transitionTo(DocumentStatus)` 校验流转合法性并自动更新时间戳。
- `reconstruct()` 仅供仓储层重建聚合根使用。

### 值对象

| 类 | 用途 |
|----|------|
| `DocumentChunk` | 文档分块（index, text, startPosition, endPosition） |
| `Query` | 用户查询（knowledgeBaseId, text） |
| `RagContext` | 检索上下文（prompt + references） |
| `ChunkReference` | 引用来源（documentId, documentName, chunkText, score, position） |

## 编码规范

### 命名约定

| 层级 | 类命名 | 包命名 |
|------|--------|--------|
| 领域实体 | `KnowledgeBase`, `Document` | `kb.model` |
| 领域服务 | `RecursiveChunkingStrategy` | `kb.service` |
| 仓储接口 | `DocumentRepository` | `kb.repository` |
| 入站端口 | `IngestDocumentUseCase` | `kb.port.in` |
| 出站端口 | `VectorStorePort` | `kb.port.out` |
| 应用服务 | `KnowledgeBaseApplicationService` | `kb.port.service` |
| Controller | `DocumentController` | `kb.adapter.in.web` |
| 出站适配器 | `MilvusVectorStoreAdapter` | `kb.adapter.out.*` |
| MongoDB 实体 | `DocumentDocument` | `kb.adapter.out.persistence.entity` |

### 响应式边界

- **domain / application**：完全同步 API。
- **adapter/in/web**：Controller 返回 `Mono<>` / `Flux<>`。
- **adapter/out/persistence**：使用 Reactive MongoDB 驱动，内部 `.block()` 包装为同步 API。
- **adapter/out/llm**：Ollama 调用是阻塞的，已用 `Schedulers.boundedElastic()` 隔离。

## 禁止事项

1. **domain 层禁止引入任何框架依赖**（Spring、LangChain4j、Reactor）。
2. **application 层禁止直接实例化 adapter 实现类**。只能通过端口接口调用。
3. **adapter 层禁止直接调用另一个 adapter**。必须通过 application 层端口。
4. **禁止贫血领域模型**。Entity 必须有业务方法和校验逻辑。
5. **禁止在 domain 层使用 `@Autowired`, `@Entity`, `@Document` 等注解**。
6. **禁止跨知识库查询**。`VectorStorePort.search()` 必须传入 `knowledgeBaseId`。
7. **禁止 adapter 模块拥有 `@SpringBootApplication`**。

## 配置

配置集中在 `synapse-kb-bootstrap/src/main/resources/application.yaml`：

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

所有 adapter 通过 `@Value` 读取配置，无硬编码。

## 扩展预留

- **对话历史**：后续在 domain 层新增 `Conversation` 聚合。
- **用户权限**：后续新增 `synapse-auth` bounded context，通过 ACL 与知识库交互。
- **多 Embedding 模型**：新增 `EmbeddingPort` 的实现即可，无需改动应用层。
- **混合搜索**：向量搜索 + 关键词搜索（BM25），在 `VectorStorePort` 中扩展接口。

## 详细设计文档

见 `docs/ARCHITECTURE.md`（模块结构、数据流、端口清单、API 端点）。
