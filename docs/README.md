# Synapse 文档中心

## 定位

本文档站是 Synapse 知识库 RAG 系统的工程文档，覆盖架构、领域模型、文档摄入、问答检索、聊天记忆、认证授权、部署、安全、API 与配置参考。文档面向后端开发、前端开发、架构评审、运维部署和后续维护者。

文档基于 Mintlify 构建，代码与文档同源管理。

## 快速入口

| 角色/场景 | 推荐入口 |
|:---|:---|
| 后端开发 | `overview/architecture-overview`、`design/ports`、`ingestion/application`、`query/application` |
| 前端开发 | `reference/api/*`、`chat/adapter-in`、`query/adapter-in` |
| 运维部署 | `deployment/local`、`deployment/production-notes`、`reference/configuration` |
| 安全审查 | `security/auth`、`security/rag`、`security/cors` |
| RAG 检索调优 | `design/hybrid-retrieval`、`query/application`、`query/adapter-out` |
| 资料时效性治理 | `ingestion/domain`、`ingestion/application`、`query/application`、`design/hybrid-retrieval`、`reference/api/document`、`reference/api/streaming-query` |

## 文档目录

- **overview**：系统定位、技术栈、架构鸟瞰、运行前置条件
- **getting-started**：本地启动与首次使用
- **concepts**：RAG、向量检索、DDD、分层架构、状态机等基础概念
- **auth**：认证授权 bounded context 的领域、应用、适配器和配置
- **ingestion**：文档上传、解析、分块、索引写入、摄入任务、资料时效元数据
- **query**：Query 改写、`asOfDate`、混合检索、时效过滤、Prompt 组装、SSE 输出
- **chat**：聊天会话、消息持久化、引用保存、摘要记忆
- **design**：端口设计、混合检索、分块算法、Reactive 桥接、前端架构等横向专题
- **deployment**：本地和生产部署注意事项
- **reference**：API、配置、目录、错误码、术语
- **security**：认证、RAG Prompt 安全、CORS

## 当前关键设计边界

- Domain/Application 保持同步 API，WebFlux 只在 adapter-in 边界。
- `auth` 与 `kb` 是并列 bounded context，`kb` 通过 `AccessControlPort` 依赖权限能力。
- 文档摄入状态 `Document.status`（`PENDING → PROCESSING → COMPLETED/FAILED`）、业务时效状态 `DocumentLifecycleStatus`（`ACTIVE`、`SUPERSEDED`、`RETIRED`）、索引同步状态 `DocumentIndexStatus`（`SYNCED`、`STALE`、`REFRESHING`、`FAILED`）三者严格分离。
- 资料时效性治理采用 Milvus/Mongo 检索硬过滤 + Application 层 `filterByDocumentEffectiveDate` 兜底过滤的双层策略。
- 在线 metadata 编辑使用 `PatchValue<T>` 三态语义（`unset`/`set`/`clear`），`sourceType` 和 `effectiveFrom` 不允许清空。
- 所有治理操作（patch metadata、supersede、retire、reactivate、reindex）都会标记索引为 `STALE`，由后台 `DocumentIndexRefreshJobWorker` 异步刷新。
- Mongo `Document` 是权威状态，Milvus/Mongo chunk index 是派生索引；索引刷新采用异步任务保证最终一致性。
- `effectiveTo` 是排他结束日：`effectiveTo=2025-01-01` 表示 2025-01-01 当天起旧资料无效。
- 旧 collection / 存量数据需要重新摄入或迁移到 v3，否则不能保证完整时效过滤。

## 文档维护规范

- 文档必须以当前代码为准，不能保留旧方法签名或旧路径。
- API 文档变更必须同步 request/response 示例。
- 端口签名变更必须同步 `design/ports`、`ingestion/query` 对应 `application`/`adapter-out` 文档。
- 配置变更必须同步 `reference/configuration` 和 `README.md`。
- 涉及安全、权限、资料时效性的行为变更，必须同步 `AGENTS.md` 和 `CLAUDE.md`。
- 文档中的代码片段应简化但必须可对应当前真实类名、方法名和字段名。

## 本地预览

```bash
npm i -g mint
cd docs
mint dev
```

访问 `http://localhost:3000` 查看文档。

## 发布

本文档由 Mintlify 构建，发布流程依赖仓库配置。推送代码后 Mintlify 自动构建并部署。
