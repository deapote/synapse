# Synapse

<p align="center">
  <strong>多知识库 RAG 系统</strong> | Multi-Knowledgebase Retrieval-Augmented Generation System
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> •
  <a href="docs/API.md">API 文档</a> •
  <a href="docs/ARCHITECTURE.md">架构设计</a>
</p>

---

Synapse 是一个支持多知识库隔离、用户鉴权和流式问答的 RAG 系统。用户登录后可创建知识库、上传文档，系统会异步解析、语义分块、向量化并写入 Milvus 与 Mongo 关键词索引；问答时只在目标知识库内执行混合检索和重排，并通过 SSE 返回流式回答和引用。

## 功能特性

- **Sa-Token 鉴权与 RBAC** — 默认 `ADMIN` / `USER` 角色，权限粒度为 `KB_READ`、`KB_WRITE`、`KB_DELETE`、`AUTH_ADMIN`
- **知识库归属隔离** — `USER` 只能访问自己创建的知识库，`ADMIN` 可跨用户管理
- **异步文档摄入** — 上传成功立即返回 `PENDING` 文档，后台执行 `PROCESSING -> COMPLETED/FAILED`
- **上传安全控制** — 文件大小、扩展名、MIME、解析字符数和 SHA-256 去重均配置化
- **混合检索 + LLM 生成** — 基于 Milvus 向量召回、Mongo BM25 关键词召回和融合重排，结合 Ollama 流式生成答案
- **Query 改写质量门禁** — 改写 query 后强制计算 embedding 余弦相似度，低于阈值自动回退原 query
- **引用溯源** — SSE 最终返回来源文档、片段文本、分数和位置
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
| 向量存储 | Milvus | Standalone |
| 元数据/关键词索引 | MongoDB | Spring Data MongoDB 同步仓储 |
| 文档解析 | Apache Tika | - |
| 构建工具 | Maven | Java 21 |
| 前端 | Vue 3 + Vite + Pinia | - |

## 架构概览

项目采用 DDD + 六边形架构，`auth` 与 `kb` 是并列 bounded context：

```text
synapse/
├── synapse-shared/
├── synapse-auth-domain/
├── synapse-auth-application/
├── synapse-auth-adapter/
├── synapse-auth-config/
├── synapse-kb-domain/
├── synapse-kb-application/
├── synapse-kb-adapter/
├── synapse-kb-config/
├── synapse-kb-bootstrap/
└── synapse-frontend/
```

依赖方向：`shared <- domain <- application <- adapter <- config <- bootstrap`。

详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.9+
- Node.js 20+
- MongoDB（默认端口 27017）
- Milvus Standalone（默认端口 19530）
- Ollama（默认端口 11434），并已下载模型：

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
  collection-name: synapse_document_chunks
  embedding-dimension: 1536
```

本地开发可创建 `application-local.yml` 覆盖（已加入 `.gitignore`）。

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
| POST | `/api/knowledge-bases/{kbId}/documents` | 上传文档，返回 `PENDING`（`KB_WRITE`） |
| GET | `/api/knowledge-bases/{kbId}/documents` | 列出文档（`KB_READ`） |
| DELETE | `/api/documents/{id}` | 删除文档（`KB_DELETE`） |
| GET | `/api/knowledge-bases/{kbId}/chat/sessions/current` | 获取当前聊天会话（`KB_READ`） |
| POST | `/api/knowledge-bases/{kbId}/chat/sessions` | 新建聊天会话（`KB_READ`） |
| GET | `/api/chat/sessions/{sessionId}/messages` | 分页读取聊天记录（仅当前用户） |
| POST | `/api/knowledge-bases/{kbId}/query/stream` | SSE 流式问答（`KB_READ`） |

完整接口定义见 [docs/API.md](docs/API.md)。

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

# 3. 上传文档，接口会立即返回 PENDING
curl -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/documents" \
  -H "synapse-token: <tokenValue>" \
  -F "file=@example.pdf"

# 4. 流式问答
curl -N -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/query/stream" \
  -H "Content-Type: application/json" \
  -H "synapse-token: <tokenValue>" \
  -d '{"query":"这篇文档的主要内容是什么？","sessionId":"可选会话ID"}'
```

问答页面会自动为“当前用户 + 当前知识库”恢复最新聊天会话。后端只把会话摘要和最近若干条消息拼入 prompt，长历史会触发 Ollama 摘要压缩，避免无限增长的上下文拖慢问答。

## 开发文档

- [API 接口文档](docs/API.md)
- [架构设计文档](docs/ARCHITECTURE.md)
- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md)

## 许可证

[MIT](LICENSE)
