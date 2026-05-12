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

Synapse 是一个支持多知识库的 RAG（检索增强生成）系统。你可以创建多个独立的知识库，上传文档（PDF、Word、TXT 等），系统会自动解析、分块、生成向量嵌入并存储到向量数据库中。之后通过自然语言提问，系统会检索相关知识片段并调用 LLM 生成带引用的回答。

## 功能特性

- **多知识库隔离** — 每个知识库独立管理文档和向量数据，查询不会跨库
- **自动文档处理** — 支持 PDF、DOCX、TXT 等格式，自动提取文本并递归分块
- **向量检索 + LLM 生成** — 基于语义相似度检索相关片段，结合上下文生成回答
- **文档状态追踪** — PENDING → PROCESSING → COMPLETED/FAILED 状态机，支持失败重试
- **引用溯源** — 回答附带来源文档和具体片段位置，便于核实
- **响应式 API** — 基于 Spring WebFlux，适配高并发场景

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.13 |
| 响应式编程 | Spring WebFlux | — |
| AI 编排 | LangChain4j | 1.13.0 |
| LLM | Ollama | qwen2.5:7b |
| Embedding | Ollama | gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0 (1536维) |
| 向量存储 | Milvus | Standalone |
| 文档元数据 | MongoDB | Reactive |
| 文档解析 | Apache Tika | — |
| 构建工具 | Maven | Java 21 |

## 架构概览

项目采用 **DDD + 六边形架构**，严格分层：

```
synapse/
├── synapse-shared/              # 共享内核（DomainException 等）
├── synapse-kb-domain/           # 领域层 —— 纯 Java，零框架依赖
├── synapse-kb-application/      # 应用层 —— 用例编排，定义端口
├── synapse-kb-adapter/          # 适配器层 —— 技术实现（WebFlux、MongoDB、Milvus、Ollama）
├── synapse-kb-config/           # Spring Bean 组装
└── synapse-kb-bootstrap/        # Spring Boot 启动入口
```

依赖方向：**shared ← domain ← application ← adapter ← config ← bootstrap**

详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.9+
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

### 启动

```bash
cd synapse-kb-bootstrap
mvn spring-boot:run
```

服务启动后访问：`http://localhost:8082`

### 配置

主要配置集中在 `synapse-kb-bootstrap/src/main/resources/application.yaml`：

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
```

本地开发可创建 `application-local.yml` 覆盖（已加入 `.gitignore`）。

## API 概览

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/knowledge-bases` | 创建知识库 |
| GET | `/api/knowledge-bases` | 列出知识库 |
| DELETE | `/api/knowledge-bases/{id}` | 删除知识库 |
| POST | `/api/knowledge-bases/{kbId}/documents` | 上传文档 |
| GET | `/api/knowledge-bases/{kbId}/documents` | 列出文档 |
| DELETE | `/api/documents/{id}` | 删除文档 |
| POST | `/api/knowledge-bases/{kbId}/query` | 知识库问答 |

完整接口定义、请求/响应格式和错误码见 [docs/API.md](docs/API.md)。

### 快速体验

```bash
# 1. 创建知识库
curl -X POST http://localhost:8082/api/knowledge-bases \
  -H "Content-Type: application/json" \
  -d '{"name": "示例知识库", "description": "测试用途"}'

# 2. 上传文档（将 {kbId} 替换为上一步返回的 id）
curl -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/documents" \
  -F "file=@example.pdf"

# 3. 问答查询
curl -X POST "http://localhost:8082/api/knowledge-bases/{kbId}/query" \
  -H "Content-Type: application/json" \
  -d '{"question": "这篇文档的主要内容是什么？"}'
```

## 开发文档

- [API 接口文档](docs/API.md) — 完整的接口定义和示例
- [架构设计文档](docs/ARCHITECTURE.md) — 模块结构、数据流、端口清单
- [CLAUDE.md](CLAUDE.md) — 项目编码规范和分层约束

## 许可证

[MIT](LICENSE)
