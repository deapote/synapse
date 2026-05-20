# Synapse

<p align="center">
  <strong>多知识库 RAG 系统</strong> | Multi-Knowledgebase Retrieval-Augmented Generation System
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> •
  <a href="docs/overview/architecture-overview.mdx">架构概览</a> •
  <a href="docs/reference/api/document.mdx">文档 API</a> •
  <a href="docs/reference/api/streaming-query.mdx">流式问答 API</a> •
  <a href="docs/reference/configuration.mdx">配置参考</a> •
  <a href="docs/design/hybrid-retrieval.mdx">混合检索</a>
</p>

---

Synapse 是一个支持多知识库隔离、用户鉴权、流式问答和资料时效性治理的 RAG 系统。用户登录后可创建知识库、上传文档（携带时效元数据），系统会异步解析、语义分块、向量化并写入 Milvus v3 与 Mongo 关键词索引；问答时支持 `asOfDate` 参数，在目标知识库内执行向量/Mongo 双侧时效硬过滤、融合重排和文档真实状态兜底过滤，并通过 SSE 返回流式回答和带版本/生效期的引用。

## 功能特性

- **Sa-Token 鉴权与 RBAC** — 默认 `ADMIN` / `USER` 角色，权限粒度为 `KB_READ`、`KB_WRITE`、`KB_DELETE`、`AUTH_ADMIN`
- **知识库归属隔离** — `USER` 只能访问自己创建的知识库，`ADMIN` 可跨用户管理
- **异步文档摄入** — 上传成功立即返回 `PENDING` 文档，后台执行 `PROCESSING -> COMPLETED/FAILED`
- **资料时效性治理 v1** — 文档携带 `effectiveFrom`、`effectiveTo`、`lifecycleStatus` 和 `canonicalKey`，检索时按 `asOfDate` 硬过滤
- **资料时效性治理 v2** — 在线 metadata patch、手动 supersede/retire/reactivate、强制 reindex、索引状态追踪、审计事件、版本链查询
- **法规/政策多版本管理** — 同一 `canonicalKey` 可存在多个版本，新版本通过 `supersedesDocumentId` 自动替代旧版本，也支持手动建立替代关系
- **MinerU 文档解析集成** — 可通过本地独立 MinerU API 解析复杂 PDF、表格和公式，失败时可回退 Apache Tika
- **asOfDate 查询** — 支持显式指定查询日期，未传时默认当前日期并附带轻量时间意图解析
- **Milvus v3 + Mongo BM25 双侧时效过滤** — 向量检索和关键词检索均按 `knowledgeBaseId + effectiveFromEpochDay + effectiveToEpochDay + lifecycleStatus` 做硬过滤
- **引用带版本和生效期** — SSE 返回的 `ChunkReferenceResponse` 包含 `versionLabel`、`effectiveFrom`、`effectiveTo`、`lifecycleStatus`、`authorityLevel`
- **上传安全控制** — 文件大小、扩展名、MIME、解析字符数和 SHA-256 去重均配置化
- **混合检索 + LLM 生成** — 基于 Milvus 向量召回、Mongo BM25 关键词召回和融合重排，结合 Ollama 流式生成答案
- **Query 改写质量门禁** — 改写 query 后强制计算 embedding 余弦相似度，低于阈值自动回退原 query
- **引用溯源** — SSE 最终返回来源文档、片段文本、分数、位置和时效元数据
- **响应式 Web 边界** — WebFlux Controller 对同步应用服务做线程隔离，避免阻塞事件循环

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.13 |
| 响应式编程 | Spring WebFlux | - |
| 鉴权 | Sa-Token Reactor | 1.45.0 |
| AI 编排 | LangChain4j | 1.13.0 |
| LLM | Ollama | qwen2.5:7b |
| Embedding | Ollama | gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0 (1536维) |
| 向量存储 | Milvus | Standalone (v2 SDK, v3 collection) |
| 元数据/关键词索引 | MongoDB | Spring Data MongoDB 同步仓储 |
| 文档解析 | MinerU / Apache Tika | MinerU 本地服务，Tika fallback |
| 构建工具 | Maven | Java 21 |
| 前端 | Vue 3 + Vite + Pinia | - |

## 架构概览

项目采用 DDD + 六边形架构，`auth` 与 `kb` 是并列 bounded context：

```text
synapse/
├── synapse-shared/              # 共享内核（DomainException）
├── synapse-auth-domain/         # 认证领域层 —— 用户、角色、权限
├── synapse-auth-application/    # 认证应用层 —— 登录、当前用户、用户/角色管理
├── synapse-auth-adapter/        # 认证适配器层 —— Web、Mongo、Sa-Token、BCrypt
├── synapse-auth-config/         # 认证 Bean 组装与安全过滤器
├── synapse-kb-domain/           # 知识库领域层 —— 纯 Java，零框架依赖
├── synapse-kb-application/      # 知识库应用层 —— 用例编排，定义端口
├── synapse-kb-adapter/          # 知识库适配器层 —— Web、Mongo、Milvus、Ollama、MinerU/Tika
├── synapse-kb-config/           # 知识库 Bean 组装
├── synapse-kb-bootstrap/        # Spring Boot 启动入口
└── synapse-frontend/            # Vue 前端
```

依赖方向：`shared <- domain <- application <- adapter <- config <- bootstrap`。

详见 [docs/overview/architecture-overview.mdx](docs/overview/architecture-overview.mdx)。

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.9+
- Node.js 20+
- MongoDB（默认端口 27017）
- Milvus Standalone（默认端口 19530）
- Ollama（默认端口 11434）
- MinerU（默认端口 8000），用于文档解析；完整部署见 [docs/deployment/mineru-local.mdx](docs/deployment/mineru-local.mdx)

Ollama 需要先下载模型：

```bash
ollama pull qwen2.5:7b
ollama pull hf.co/sinequa/gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0
```

### 编译

```bash
mvn clean install
```

### 启动后端

```bash
cd synapse-kb-bootstrap
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8082`。

### 启动前端

```bash
cd synapse-frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`。

### 默认管理员

首次启动会创建默认管理员：

- 用户名：`admin`
- 密码：`ChangeMe123!`

生产环境必须通过 `synapse.auth.bootstrap-admin.password` 覆盖默认密码。

## 配置

主要配置集中在 `synapse-kb-bootstrap/src/main/resources/application.yaml`：

```yaml
server:
  port: 8082

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/synapse_kb?serverSelectionTimeoutMS=2000&connectTimeoutMS=1000

synapse:
  cors:
    allowed-origins: http://localhost:5173,http://127.0.0.1:5173
  auth:
    bootstrap-admin:
      username: admin
      password: ChangeMe123!
  web:
    max-page-size: 100
  upload:
    max-file-bytes: 20971520
    max-parsed-chars: 10000000
    allowed-extensions: pdf,doc,docx,txt,md
    allowed-content-types: application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown
  parser:
    provider: mineru
    mineru:
      base-url: http://127.0.0.1:8000
      endpoint: /file_parse
      backend: pipeline
      parse-method: auto
      language: ch
      formula-enable: true
      table-enable: true
      timeout-seconds: 600
      fallback-to-tika: true
  chunking:
    max-size: 1000
    overlap-ratio: 0.15
    min-overlap: 80
    max-overlap: 200
  rag:
    top-k: 5
    vector-candidate-k: 20
    keyword-candidate-k: 20
    vector-weight: 0.65
    keyword-weight: 0.35
    query-rewrite:
      enabled: true
      similarity-threshold: 0.8

sa-token:
  token-name: synapse-token

ollama:
  base-url: http://localhost:11434
  chat-model: qwen2.5:7b
  embedding-model: hf.co/sinequa/gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0

milvus:
  host: 127.0.0.1
  port: 19530
  v3:
    collection-name: synapse_document_chunks_v3
  embedding-dimension: 1536
```

本地开发可创建 `application-local.yml` 覆盖（已加入 `.gitignore`）。

> **注意**：存量 collection `synapse_document_chunks` 不包含时效字段，需要重新摄入或迁移到 `synapse_document_chunks_v3`，否则不能保证完整时效过滤。

## API 概览

除 `/api/auth/login` 外，所有 `/api/**` 接口都需要请求头携带 Sa-Token 返回的 token。

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/admin/users` | 用户列表（`AUTH_ADMIN`） |
| POST | `/api/admin/users` | 创建用户（`AUTH_ADMIN`） |
| GET | `/api/admin/roles` | 角色列表（`AUTH_ADMIN`） |
| POST | `/api/knowledge-bases` | 创建知识库（`KB_WRITE`） |
| GET | `/api/knowledge-bases` | 列出可访问知识库（`KB_READ`） |
| DELETE | `/api/knowledge-bases/{id}` | 删除知识库（`KB_DELETE`） |
| POST | `/api/knowledge-bases/{kbId}/documents` | 上传文档，支持 metadata 参数（`KB_WRITE`） |
| GET | `/api/knowledge-bases/{kbId}/documents` | 列出文档，支持筛选（`KB_READ`） |
| DELETE | `/api/documents/{id}` | 删除文档（`KB_DELETE`） |
| PUT | `/api/documents/{id}/metadata` | 在线修改 metadata（`KB_WRITE`） |
| POST | `/api/documents/{id}/supersede` | 手动替代旧文档（`KB_WRITE`） |
| POST | `/api/documents/{id}/retire` | 废止文档（`KB_WRITE`） |
| POST | `/api/documents/{id}/reactivate` | 重新启用文档（`KB_WRITE`） |
| POST | `/api/documents/{id}/reindex` | 强制重建索引（`KB_WRITE`） |
| GET | `/api/documents/{id}/version-chain` | 查询版本链（`KB_READ`） |
| GET | `/api/documents/{id}/audit-events` | 查询审计事件（`KB_READ`） |
| GET | `/api/knowledge-bases/{kbId}/chat/sessions/current` | 获取当前聊天会话（`KB_READ`） |
| POST | `/api/knowledge-bases/{kbId}/chat/sessions` | 新建聊天会话（`KB_READ`） |
| GET | `/api/chat/sessions/{sessionId}/messages` | 分页读取聊天记录（仅当前用户） |
| POST | `/api/knowledge-bases/{kbId}/query/stream` | SSE 流式问答，支持 `asOfDate`（`KB_READ`） |

完整接口定义见 [docs/reference/api/document.mdx](docs/reference/api/document.mdx) 和 [docs/reference/api/streaming-query.mdx](docs/reference/api/streaming-query.mdx)。

### 文档上传与 metadata

上传接口支持以下 `multipart/form-data` 参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 文档文件 |
| sourceType | string | 否 | `LEGAL`、`POLICY`、`GENERAL`，默认 `GENERAL` |
| canonicalKey | string | 否 | 规范标识，用于同一资料的多版本关联 |
| versionLabel | string | 否 | 版本标签，如 "2024 版" |
| effectiveFrom | string | 否 | 生效日期，格式 `YYYY-MM-DD`，默认上传日期 |
| effectiveTo | string | 否 | 排他结束日期，格式 `YYYY-MM-DD` |
| supersedesDocumentId | string | 否 | 被替代的旧文档 ID |
| authorityLevel | int | 否 | 权威等级，数值越高优先级越高，默认 0 |
| jurisdiction | string | 否 | 适用区域/管辖范围 |

`DocumentResponse` 返回字段包含上述所有时效字段，以及 `status`（`PENDING`/`PROCESSING`/`COMPLETED`/`FAILED`）、`lifecycleStatus`（`ACTIVE`/`SUPERSEDED`/`RETIRED`）、`indexStatus`（`SYNCED`/`STALE`/`REFRESHING`/`FAILED`）、`metadataVersion`、`indexedMetadataVersion`、`lastIndexRefreshAt`、`lastIndexFailureReason`。

### 流式问答与 asOfDate

```bash
curl -N -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/query/stream" \
  -H "Content-Type: application/json" \
  -H "synapse-token: <tokenValue>" \
  -d '{
    "query": "2024年规定是什么？",
    "sessionId": "可选会话ID",
    "asOfDate": "2024-06-01"
  }'
```

`ChunkReferenceResponse` 在 `references` 事件中返回：

```json
{
  "sourceId": 1,
  "documentId": "doc-uuid",
  "documentName": "法规A-2024版.pdf",
  "chunkText": "...",
  "score": 0.9234,
  "startPosition": 0,
  "endPosition": 200,
  "used": true,
  "canonicalKey": "regulation-a",
  "versionLabel": "2024 版",
  "effectiveFrom": "2024-01-01",
  "effectiveTo": null,
  "lifecycleStatus": "ACTIVE",
  "authorityLevel": 10,
  "jurisdiction": "全国"
}
```

## 快速体验

```bash
# 1. 登录并记录响应中的 tokenName/tokenValue
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMe123!"}'

# 2. 创建知识库，把 synapse-token 替换为登录返回的 tokenValue
curl -X POST http://localhost:8082/api/knowledge-bases \
  -H "Content-Type: application/json" \
  -H "synapse-token: <tokenValue>" \
  -d '{"name":"示例知识库","description":"测试用途"}'

# 3. 上传 2024 版本法规
curl -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/documents" \
  -H "synapse-token: <tokenValue>" \
  -F "file=@law-2024.pdf" \
  -F "canonicalKey=law-x" \
  -F "versionLabel=2024 版" \
  -F "effectiveFrom=2024-01-01" \
  -F "sourceType=LEGAL"

# 4. 上传 2025 版本法规（替代 2024 版）
curl -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/documents" \
  -H "synapse-token: <tokenValue>" \
  -F "file=@law-2025.pdf" \
  -F "canonicalKey=law-x" \
  -F "versionLabel=2025 版" \
  -F "effectiveFrom=2025-01-01" \
  -F "sourceType=LEGAL" \
  -F "supersedesDocumentId=<v1DocumentId>"

# 5. 查询 2024 年 6 月的适用法规
curl -N -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/query/stream" \
  -H "Content-Type: application/json" \
  -H "synapse-token: <tokenValue>" \
  -d '{
    "query": "这项法规的主要内容是什么？",
    "asOfDate": "2024-06-01"
  }'

# 6. 查询 2025 年 6 月的适用法规（应返回 2025 版）
curl -N -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/query/stream" \
  -H "Content-Type: application/json" \
  -H "synapse-token: <tokenValue>" \
  -d '{
    "query": "这项法规的主要内容是什么？",
    "asOfDate": "2025-06-01"
  }'
```

问答页面会自动为"当前用户 + 当前知识库"恢复最新聊天会话。后端只把会话摘要和最近若干条消息拼入 prompt，长历史会触发 Ollama 摘要压缩，避免无限增长的上下文拖慢问答。

## 开发文档

- [架构概览](docs/overview/architecture-overview.mdx)
- [文档 API](docs/reference/api/document.mdx)
- [流式问答 API](docs/reference/api/streaming-query.mdx)
- [配置参考](docs/reference/api/streaming-query.mdx)
- [混合检索设计](docs/design/hybrid-retrieval.mdx)
- [资料时效性治理](docs/design/temporal-validity.mdx)
- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md)

## 许可证

[MIT](LICENSE)
