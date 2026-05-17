# Synapse 架构设计文档

## 1. 系统概述

Synapse 是基于 Spring Boot、Sa-Token、LangChain4j、MongoDB、Milvus 和 Ollama 的多知识库 RAG 系统。系统支持 RBAC 鉴权、知识库归属隔离、异步文档摄入、混合检索、Query 改写质量门禁和 SSE 流式问答。

## 2. 模块结构

```text
synapse/
├── synapse-shared/              # 共享内核
├── synapse-auth-domain/         # 认证领域模型：UserAccount、RoleDefinition
├── synapse-auth-application/    # 认证用例：登录、当前用户、用户/角色管理
├── synapse-auth-adapter/        # 认证适配器：Web、Mongo、Sa-Token、BCrypt
├── synapse-auth-config/         # 认证 Bean 组装、安全过滤器、种子数据
├── synapse-kb-domain/           # 知识库领域模型、值对象、分块策略
├── synapse-kb-application/      # 知识库用例编排、出站端口
├── synapse-kb-adapter/          # 知识库 Web、Mongo、Milvus、Ollama、Tika 适配器
├── synapse-kb-config/           # 知识库 Bean 组装
├── synapse-kb-bootstrap/        # Spring Boot 启动入口
└── synapse-frontend/            # Vue 前端
```

依赖方向：

```text
shared <- domain <- application <- adapter <- config <- bootstrap
```

`auth` 与 `kb` 是并列 bounded context。知识库上下文通过 `AccessControlPort` 调用权限能力，具体 Sa-Token 实现由 `auth-adapter` 提供，避免 KB 应用层直接依赖鉴权框架。

## 3. 端口清单

### 认证入站端口

| 端口 | 职责 |
|---|---|
| `AuthenticationUseCase` | 登录、登出、当前用户 |
| `UserAdminUseCase` | 用户管理、角色绑定、角色权限管理 |

### 认证出站端口

| 端口 | 当前实现 |
|---|---|
| `PasswordHasherPort` | `BCryptPasswordHasherAdapter` |
| `LoginSessionPort` | `SaTokenLoginSessionAdapter` |
| `UserAccountRepository` | `MongoUserAccountRepository` |
| `RoleDefinitionRepository` | `MongoRoleDefinitionRepository` |

### 知识库入站端口

| 端口 | 职责 |
|---|---|
| `CreateKnowledgeBaseUseCase` | 创建知识库 |
| `ListKnowledgeBaseUseCase` | 列出可访问知识库 |
| `DeleteKnowledgeBaseUseCase` | 删除知识库及其文档/向量 |
| `IngestDocumentUseCase` | 创建 `PENDING` 文档并提交异步摄入 |
| `ListDocumentUseCase` | 列出知识库文档 |
| `DeleteDocumentUseCase` | 删除文档及向量 |
| `QueryKnowledgeBaseUseCase` | 检索并组装 RAG prompt |

### 知识库出站端口

| 端口 | 当前实现 |
|---|---|
| `AccessControlPort` | `SaTokenKbAccessControlAdapter` |
| `VectorStorePort` | `MilvusVectorStoreAdapter` |
| `ChunkSearchIndexPort` | `MongoChunkSearchIndexAdapter` |
| `EmbeddingPort` | `OllamaEmbeddingAdapter` |
| `QueryRewritePort` | `OllamaQueryRewriteAdapter` |
| `StreamingLlmPort` | `OllamaStreamingLlmAdapter` |
| `DocumentParserPort` | `ApacheTikaDocumentParserAdapter` |
| `KnowledgeBaseRepository` | `MongoKnowledgeBaseRepository` |
| `DocumentRepository` | `MongoDocumentRepository` |

## 4. 核心数据流

### 4.1 登录与权限

```text
AuthController
  -> AuthenticationUseCase.login()
  -> UserAccountRepository.findByUsername()
  -> BCryptPasswordHasherAdapter.matches()
  -> SaTokenLoginSessionAdapter.login()
  -> 返回 tokenName/tokenValue
```

后续 `/api/**` 请求由 Sa-Token WebFlux 过滤器校验登录态。角色权限通过 `SaTokenPermissionAdapter` 从 MongoDB 读取。

### 4.2 创建知识库

```text
KnowledgeBaseController
  -> AccessControlPort.checkPermission(KB_WRITE)
  -> KnowledgeBase.create(ownerUserId=currentUserId)
  -> KnowledgeBaseRepository.save()
```

`USER` 后续只能访问 `ownerUserId` 等于自己的知识库；`ADMIN` 不受该限制。

### 4.3 文档摄入

```text
DocumentController.upload()
  -> 读取 multipart 内容并校验大小/MIME/扩展名
  -> 计算 SHA-256
  -> IngestDocumentUseCase.ingest()
  -> 校验知识库存在、权限和归属
  -> 创建 Document(PENDING) 并保存 MongoDB
  -> 提交后台摄入任务
  -> 立即返回文档 ID 和 PENDING

后台任务:
  PENDING -> PROCESSING
  -> Apache Tika 解析
  -> RecursiveChunkingStrategy 语义分块
  -> Ollama Embedding
  -> Milvus store
  -> Mongo chunk BM25 index store
  -> COMPLETED
  -> 失败时清理该文档向量和关键词索引并转 FAILED
```

Mongo 索引包含：

- `knowledgeBase.ownerUserId`
- `document.knowledgeBaseId`
- `document.uploadedAt`
- `document.knowledgeBaseId + contentHash` 唯一索引
- `document_chunk_index.knowledgeBaseId + tokens`
- `chat_sessions.ownerUserId + knowledgeBaseId + updatedAt`
- `chat_messages.sessionId + sequence` 唯一索引
- `user.username` 唯一索引

### 4.4 流式问答

```text
StreamingQueryController
  -> QueryKnowledgeBaseUseCase.prepare()
  -> 校验 KB_READ 和知识库归属
  -> 解析或创建当前用户的聊天会话
  -> 保存用户消息，必要时压缩旧历史为摘要
  -> QueryRewritePort.rewrite()
  -> 原 query / 改写 query embedding 余弦相似度校验
  -> 并行 Milvus 向量召回 + Mongo BM25 关键词召回
  -> 分数融合重排(vectorWeight + keywordWeight)
  -> 组装聊天摘要、最近消息、引用片段和用户问题
  -> StreamingLlmPort.generateStream()
  -> SSE: session -> token* -> references -> complete
  -> 生成完成后保存 assistant 消息和引用
```

LLM 流式异常通过 SSE `error` 事件返回，不发送伪 `complete`。前端关闭 SSE 时会关闭 Java `Stream` 并触发底层生成取消。

## 5. 领域模型

### 认证

- `UserAccount`：用户聚合根，包含用户名、显示名、BCrypt 密码哈希、角色集合、启停状态。
- `RoleDefinition`：角色定义，包含角色名与权限集合。
- `AuthPermission`：`KB_READ`、`KB_WRITE`、`KB_DELETE`、`AUTH_ADMIN`。
- `RoleName`：`ADMIN`、`USER`。

### 知识库

- `KnowledgeBase`：知识库聚合根，包含归属用户 `ownerUserId`。
- `Document`：独立聚合根，包含内容哈希、状态、失败原因、处理时间、分块数量。
- `Query`：查询值对象，强制携带 `knowledgeBaseId`，禁止跨库查询。
- `RagContext`：已组装 prompt 与引用片段。
- `ChunkReference.score`：融合重排后的最终分数，范围 `[0, 1]`。
- `ChatSession`：当前用户在某个知识库下的聊天会话，保存摘要、消息数量和摘要进度。
- `ChatMessage`：单条用户或助手消息，按 `sessionId + sequence` 有序存储。

### 文档状态机

```text
PENDING -> PROCESSING -> COMPLETED
                     \-> FAILED
FAILED -> PENDING
```

## 6. 安全设计

- Sa-Token Reactor 负责 WebFlux 登录态校验。
- Controller 调用同步应用服务时通过 Sa-Token Reactor 上下文桥接，避免线程切换丢失上下文。
- 管理接口统一校验 `AUTH_ADMIN`。
- KB/文档/问答接口同时校验权限和知识库归属。
- 聊天记忆按 `ownerUserId + knowledgeBaseId + sessionId` 隔离，用户不能读取其他用户的会话。
- 上传限制集中配置，默认不允许 `application/octet-stream`。
- RAG prompt 明确检索内容不具备指令优先级，并用 XML 风格边界标记隔离用户问题与引用片段。
- Query 改写必须通过 embedding 余弦相似度门禁，默认阈值 `0.8`，失败或低相似度时回退原 query。
- CORS 默认只允许本地前端来源，生产环境必须显式配置。
- 默认管理员密码仅用于开发，启动时会打印告警。

## 7. 性能与稳定性

- WebFlux 仅作为入站响应式边界；应用层保持同步 API。
- MongoDB 使用同步 Spring Data MongoDB 仓储，阻塞调用在 Web 层隔离到 boundedElastic。
- 文档摄入使用独立 Executor，默认虚拟线程。
- RAG 检索使用独立 Executor 并行执行向量召回和关键词召回。
- 聊天记忆只携带摘要和最近消息；旧历史达到阈值后通过 Ollama 非流式模型压缩。
- 文本切分按 Markdown、段落、中英文句子、软标点和硬切逐级选择边界，默认保留约 15% 重叠窗口。
- 上传接口会一次性读取文件内容，文件大小必须由 `synapse.upload.max-file-bytes` 控制在可接受范围内。
- Milvus 客户端懒初始化，避免启动强依赖外部服务。
- SSE 使用有界队列桥接 LLM 回调，队列满时阻塞生产端，避免静默丢 token。

## 8. API 端点

完整字段见 [API.md](API.md)。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 当前用户 |
| GET/POST | `/api/admin/users` | 用户管理 |
| GET/PUT | `/api/admin/roles...` | 角色权限管理 |
| POST/GET/DELETE | `/api/knowledge-bases...` | 知识库管理 |
| POST/GET/DELETE | `/api/.../documents...` | 文档管理 |
| GET/POST | `/api/knowledge-bases/{kbId}/chat/sessions...` | 聊天会话 |
| GET | `/api/chat/sessions/{sessionId}/messages` | 聊天消息 |
| POST | `/api/knowledge-bases/{kbId}/query/stream` | SSE 流式问答 |

## 9. 模块划分裁决

当前模块划分合理：`auth` 与 `kb` 是两个并列 bounded context，职责边界清晰。已补齐 `synapse-auth-config`，使 auth 与 kb 在 `domain/application/adapter/config/bootstrap` 分层上保持一致。无需合并模块。
