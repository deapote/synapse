# Synapse 知识库 RAG 系统 — API 接口文档

## 1. 基础信息

| 项 | 值 |
|---|---|
| 基础地址 | `http://localhost:8082` |
| 协议 | HTTP |
| 数据格式 | JSON（文件上传除外） |
| 字符编码 | UTF-8 |

## 2. 通用约定

### 2.1 请求格式

- `Content-Type: application/json`（文件上传使用 `multipart/form-data`）
- 路径参数使用 `{param}` 占位
- 请求体字段如无特殊说明均为必填

### 2.2 响应格式

**成功响应**：直接返回对应的数据对象（非包装结构）。HTTP 状态码为 `200 OK`。

**错误响应**：统一返回以下 JSON 结构，HTTP 状态码为 `400 Bad Request`（业务错误）或 `500 Internal Server Error`（系统错误）。

```json
{
  "error": "BUSINESS_ERROR",
  "message": "错误描述信息",
  "timestamp": "2026-05-12T08:30:00Z"
}
```

### 2.3 时间格式

所有时间字段使用 ISO-8601 UTC 格式，如 `2026-05-12T08:30:00Z`。

## 3. 接口清单

| 序号 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 1 | POST | `/api/knowledge-bases` | 创建知识库 |
| 2 | DELETE | `/api/knowledge-bases/{id}` | 删除知识库 |
| 3 | POST | `/api/knowledge-bases/{kbId}/documents` | 上传文档 |
| 4 | GET | `/api/knowledge-bases/{kbId}/documents` | 列出文档 |
| 5 | DELETE | `/api/documents/{id}` | 删除文档 |
| 6 | POST | `/api/knowledge-bases/{kbId}/query` | 知识库问答 |

---

## 4. 接口详情

### 4.1 创建知识库

创建一个新的知识库，用于组织和管理相关文档。

**请求**

```http
POST /api/knowledge-bases
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| `name` | string | 是 | 知识库名称，长度 1–200 |
| `description` | string | 否 | 知识库描述 |

```json
{
  "name": "产品手册知识库",
  "description": "存放所有产品相关的技术文档"
}
```

**响应**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `id` | string | 知识库唯一标识（UUID） |
| `name` | string | 知识库名称 |
| `description` | string | 知识库描述 |
| `createdAt` | string | 创建时间（ISO-8601） |

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "产品手册知识库",
  "description": "存放所有产品相关的技术文档",
  "createdAt": "2026-05-12T08:30:00Z"
}
```

**错误情况**

- 名称重复或格式不合法 → `400 Bad Request`

---

### 4.2 删除知识库

删除指定知识库，级联删除其下的所有文档及向量数据。

**请求**

```http
DELETE /api/knowledge-bases/{id}
```

**路径参数**

| 参数 | 类型 | 说明 |
|:---|:---|:---|
| `id` | string | 知识库唯一标识 |

**响应**

- 成功：HTTP `200 OK`，无响应体

**错误情况**

- 知识库不存在 → `400 Bad Request`

---

### 4.3 上传文档

向指定知识库上传文档。系统会自动解析文档内容、分块、生成向量并存储到 Milvus。

**请求**

```http
POST /api/knowledge-bases/{kbId}/documents
Content-Type: multipart/form-data
```

**路径参数**

| 参数 | 类型 | 说明 |
|:---|:---|:---|
| `kbId` | string | 知识库唯一标识 |

**表单参数**

| 参数 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| `file` | file | 是 | 待上传的文件，支持 PDF、Word、TXT 等格式 |

**响应**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `id` | string | 文档唯一标识 |
| `knowledgeBaseId` | string | 所属知识库 ID |
| `fileName` | string | 原始文件名 |
| `fileType` | string | 文件 MIME 类型 |
| `fileSize` | number | 文件大小（字节） |
| `status` | string | 处理状态：`PENDING` |
| `chunkCount` | number | 分块数量（处理完成前为 0） |
| `uploadedAt` | string \| null | 上传时间 |

```json
{
  "id": "doc-uuid-001",
  "knowledgeBaseId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "产品手册_v2.pdf",
  "fileType": "application/pdf",
  "fileSize": 2048576,
  "status": "PENDING",
  "chunkCount": 0,
  "uploadedAt": null
}
```

**说明**

- 上传成功后文档状态为 `PENDING`，后台异步处理
- 处理完成后状态变为 `COMPLETED`（成功）或 `FAILED`（失败）
- 可通过「列出文档」接口查询最新状态

**错误情况**

- 知识库不存在 → `400 Bad Request`
- 重复上传相同内容文档 → `400 Bad Request`

---

### 4.4 列出文档

查询指定知识库下的所有文档列表。

**请求**

```http
GET /api/knowledge-bases/{kbId}/documents
```

**路径参数**

| 参数 | 类型 | 说明 |
|:---|:---|:---|
| `kbId` | string | 知识库唯一标识 |

**响应**

返回 `DocumentResponse` 数组，按上传时间倒序排列。

```json
[
  {
    "id": "doc-uuid-001",
    "knowledgeBaseId": "550e8400-e29b-41d4-a716-446655440000",
    "fileName": "产品手册_v2.pdf",
    "fileType": "application/pdf",
    "fileSize": 2048576,
    "status": "COMPLETED",
    "chunkCount": 42,
    "uploadedAt": "2026-05-12T08:35:00Z"
  }
]
```

**状态说明**

| 状态值 | 含义 |
|:---|:---|
| `PENDING` | 等待处理 |
| `PROCESSING` | 正在解析、分块、向量化 |
| `COMPLETED` | 处理完成，可参与问答检索 |
| `FAILED` | 处理失败，可重新上传 |

---

### 4.5 删除文档

删除指定文档，同时清理向量库中的相关向量数据。

**请求**

```http
DELETE /api/documents/{id}
```

**路径参数**

| 参数 | 类型 | 说明 |
|:---|:---|:---|
| `id` | string | 文档唯一标识 |

**响应**

- 成功：HTTP `200 OK`，无响应体

**错误情况**

- 文档不存在 → `400 Bad Request`

---

### 4.6 知识库问答

向指定知识库提问，系统执行 RAG 流程：检索相关文档片段 → 组装上下文 → 调用 LLM 生成回答。

**请求**

```http
POST /api/knowledge-bases/{kbId}/query
Content-Type: application/json
```

**路径参数**

| 参数 | 类型 | 说明 |
|:---|:---|:---|
| `kbId` | string | 知识库唯一标识 |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| `query` | string | 是 | 用户提问内容 |

```json
{
  "query": "产品的核心功能有哪些？"
}
```

**响应**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `answer` | string | LLM 生成的回答文本 |
| `references` | array | 引用来源列表，按相似度降序排列 |

**引用来源结构**

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `documentId` | string | 来源文档 ID |
| `documentName` | string | 来源文档文件名 |
| `chunkText` | string | 被引用的片段文本 |
| `score` | number | 相似度分数，范围 [0, 1]，越高越相关 |
| `startPosition` | number | 片段在原文中的起始位置 |
| `endPosition` | number | 片段在原文中的结束位置 |

```json
{
  "answer": "根据产品手册，核心功能包括：1. 实时数据同步...",
  "references": [
    {
      "documentId": "doc-uuid-001",
      "documentName": "产品手册_v2.pdf",
      "chunkText": "实时数据同步是本产品的核心功能之一...",
      "score": 0.92,
      "startPosition": 1520,
      "endPosition": 1650
    }
  ]
}
```

**错误情况**

- 知识库不存在 → `400 Bad Request`
- 知识库无可用文档（所有文档状态非 COMPLETED） → `400 Bad Request`

---

## 5. 数据模型汇总

### 5.1 CreateKnowledgeBaseRequest

```json
{
  "name": "string",      // 必填，1-200 字符
  "description": "string" // 可选
}
```

### 5.2 KnowledgeBaseResponse

```json
{
  "id": "string",          // UUID
  "name": "string",
  "description": "string",
  "createdAt": "string"    // ISO-8601 UTC
}
```

### 5.3 DocumentResponse

```json
{
  "id": "string",
  "knowledgeBaseId": "string",
  "fileName": "string",
  "fileType": "string",    // MIME type
  "fileSize": 0,           // 字节
  "status": "PENDING",     // PENDING | PROCESSING | COMPLETED | FAILED
  "chunkCount": 0,
  "uploadedAt": "string"   // ISO-8601 UTC，可能为 null
}
```

### 5.4 QueryRequest

```json
{
  "query": "string"  // 必填
}
```

### 5.5 AnswerResponse

```json
{
  "answer": "string",
  "references": [
    {
      "documentId": "string",
      "documentName": "string",
      "chunkText": "string",
      "score": 0.0,           // [0, 1]
      "startPosition": 0,
      "endPosition": 0
    }
  ]
}
```

### 5.6 ErrorResponse

```json
{
  "error": "BUSINESS_ERROR",
  "message": "string",
  "timestamp": "string"  // ISO-8601 UTC
}
```

## 6. 前端对接要点

### 6.1 跨域（CORS）

如果前端独立部署（如 `localhost:3000`），需确认后端已开启 CORS。当前后端未显式配置 CORS，独立部署时可能需要：

- 前端开发时代理到 `localhost:8082`
- 或后端添加 `@CrossOrigin` / `CorsWebFilter` 配置

### 6.2 文件上传

上传文档使用 `multipart/form-data`，前端示例（Fetch API）：

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);

fetch(`/api/knowledge-bases/${kbId}/documents`, {
  method: 'POST',
  body: formData
})
```

注意：**不要**手动设置 `Content-Type` header，浏览器会自动生成包含 `boundary` 的正确 header。

### 6.3 文档状态轮询

上传后文档状态为 `PENDING`，需要轮询「列出文档」接口更新状态：

```javascript
// 建议轮询间隔：2 秒
// 建议最大轮询次数：30 次（约 1 分钟）
```

### 6.4 问答引用展示

`AnswerResponse.references` 提供了回答的引用来源，建议前端：

1. 展示回答文本
2. 在回答下方或侧边栏展示引用列表
3. 可点击引用查看对应文档片段的详细信息

### 6.5 状态码速查

| 状态码 | 场景 |
|:---|:---|
| `200 OK` | 请求成功 |
| `400 Bad Request` | 参数错误、业务规则违反、资源不存在 |
| `500 Internal Server Error` | 系统内部错误 |
