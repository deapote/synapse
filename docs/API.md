# Synapse API 文档

## 1. 基础信息

| 项 | 值 |
|---|---|
| 基础地址 | `http://localhost:8082` |
| 协议 | HTTP / SSE |
| 数据格式 | JSON；文件上传使用 `multipart/form-data` |
| 字符编码 | UTF-8 |

除 `POST /api/auth/login` 外，所有 `/api/**` 接口都需要登录。登录成功后响应会返回 `tokenName` 与 `tokenValue`，后续请求用响应中的 `tokenName` 作为请求头名。

```http
synapse-token: <tokenValue>
```

## 2. 通用约定

### 成功响应

成功响应直接返回对象或数组，删除类接口返回空响应体。

### 错误响应

```json
{
  "error": "BUSINESS_ERROR",
  "message": "错误描述信息",
  "timestamp": "2026-05-17T00:00:00Z"
}
```

常见错误码：

| HTTP | error | 说明 |
|---|---|---|
| 400 | `BUSINESS_ERROR` | 参数错误、业务规则失败、重复上传等 |
| 401 | `UNAUTHORIZED` | 未登录或 token 失效 |
| 403 | `FORBIDDEN` | 权限不足 |
| 500 | `INTERNAL_ERROR` | 未预期系统异常 |

## 3. 认证接口

### 3.1 登录

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "ChangeMe123!"
}
```

响应：

```json
{
  "id": "user-id",
  "username": "admin",
  "displayName": "Administrator",
  "roles": ["ADMIN"],
  "permissions": ["KB_READ", "KB_WRITE", "KB_DELETE", "AUTH_ADMIN"],
  "tokenName": "synapse-token",
  "tokenValue": "token-value"
}
```

### 3.2 登出

```http
POST /api/auth/logout
synapse-token: <tokenValue>
```

### 3.3 当前用户

```http
GET /api/auth/me
synapse-token: <tokenValue>
```

响应：

```json
{
  "id": "user-id",
  "username": "admin",
  "displayName": "Administrator",
  "roles": ["ADMIN"],
  "permissions": ["KB_READ", "KB_WRITE", "KB_DELETE", "AUTH_ADMIN"],
  "enabled": true,
  "createdAt": "2026-05-17T00:00:00Z"
}
```

## 4. 管理接口

管理接口都需要 `AUTH_ADMIN` 权限。

### 4.1 用户列表

```http
GET /api/admin/users?page=0&size=20
synapse-token: <tokenValue>
```

分页约束：`page >= 0`，`1 <= size <= synapse.web.max-page-size`。

### 4.2 创建用户

```http
POST /api/admin/users
Content-Type: application/json
synapse-token: <tokenValue>
```

```json
{
  "username": "alice",
  "displayName": "Alice",
  "password": "Password123",
  "roles": ["USER"]
}
```

密码至少 8 位，后端使用 BCrypt 保存哈希。

### 4.3 修改用户角色

```http
PUT /api/admin/users/{id}/roles
Content-Type: application/json
synapse-token: <tokenValue>
```

```json
{
  "roles": ["USER"]
}
```

### 4.4 启停用户

```http
PUT /api/admin/users/{id}/enabled
Content-Type: application/json
synapse-token: <tokenValue>
```

```json
{
  "enabled": false
}
```

### 4.5 角色列表

```http
GET /api/admin/roles
synapse-token: <tokenValue>
```

### 4.6 修改角色权限

```http
PUT /api/admin/roles/{roleName}/permissions
Content-Type: application/json
synapse-token: <tokenValue>
```

```json
{
  "permissions": ["KB_READ", "KB_WRITE"]
}
```

## 5. 知识库接口

权限规则：

- `KB_READ`：列表、文档列表、问答。
- `KB_WRITE`：创建知识库、上传文档。
- `KB_DELETE`：删除知识库、删除文档。
- `ADMIN` 可跨用户访问；`USER` 只能访问自己创建的知识库。

### 5.1 创建知识库

```http
POST /api/knowledge-bases
Content-Type: application/json
synapse-token: <tokenValue>
```

```json
{
  "name": "产品手册知识库",
  "description": "存放产品相关文档"
}
```

响应：

```json
{
  "id": "kb-id",
  "name": "产品手册知识库",
  "description": "存放产品相关文档",
  "ownerUserId": "user-id",
  "createdAt": "2026-05-17T00:00:00Z"
}
```

### 5.2 知识库列表

```http
GET /api/knowledge-bases?page=0&size=20
synapse-token: <tokenValue>
```

响应为当前用户可访问的 `KnowledgeBaseResponse[]`。

### 5.3 删除知识库

```http
DELETE /api/knowledge-bases/{id}
synapse-token: <tokenValue>
```

删除会清理该知识库下文档元数据、Milvus 向量和 Mongo 关键词索引；失败时以错误响应返回，残留数据后续可重试清理。

## 6. 文档接口

### 6.1 上传文档

```http
POST /api/knowledge-bases/{kbId}/documents
Content-Type: multipart/form-data
synapse-token: <tokenValue>
```

表单字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | 支持 `pdf`、`doc`、`docx`、`txt`、`md` |

后端校验文件大小、扩展名、MIME、SHA-256 内容哈希和解析后文本长度。上传接口只创建 `PENDING` 文档并提交后台摄入任务。

响应：

```json
{
  "id": "doc-id",
  "knowledgeBaseId": "kb-id",
  "fileName": "产品手册.pdf",
  "fileType": "application/pdf",
  "fileSize": 2048576,
  "status": "PENDING",
  "chunkCount": 0,
  "uploadedAt": null
}
```

### 6.2 文档列表

```http
GET /api/knowledge-bases/{kbId}/documents?page=0&size=20
synapse-token: <tokenValue>
```

响应：

```json
[
  {
    "id": "doc-id",
    "knowledgeBaseId": "kb-id",
    "fileName": "产品手册.pdf",
    "fileType": "application/pdf",
    "fileSize": 2048576,
    "status": "COMPLETED",
    "chunkCount": 18,
    "uploadedAt": "2026-05-17T00:00:00Z"
  }
]
```

文档状态：

| 状态 | 说明 |
|---|---|
| `PENDING` | 已创建，等待后台摄入 |
| `PROCESSING` | 正在解析、语义分块、向量化并写入 Milvus 与 Mongo 关键词索引 |
| `COMPLETED` | 摄入完成，可用于问答 |
| `FAILED` | 摄入失败，可重新上传同内容触发清理后重试 |

### 6.3 删除文档

```http
DELETE /api/documents/{id}
synapse-token: <tokenValue>
```

删除会同时清理 Milvus 向量和 Mongo 关键词索引。Milvus 删除条件同时包含 `knowledgeBaseId` 和 `documentId`，避免跨知识库误删。

### 6.4 重试失败文档

```http
POST /api/documents/{id}/retry
synapse-token: <tokenValue>
```

仅 `FAILED` 文档可重试。后端会清理该文档已有 Milvus 向量和 Mongo 关键词索引，将状态重置为 `PENDING`，并重新提交后台摄入任务。

## 7. 聊天会话接口

聊天会话按“当前用户 + 知识库”隔离。即使拥有同一个知识库访问权，也不能读取其他用户的会话。

### 7.1 获取当前会话

```http
GET /api/knowledge-bases/{kbId}/chat/sessions/current
synapse-token: <tokenValue>
```

若当前用户在该知识库下没有会话，后端会自动创建。

```json
{
  "id": "session-id",
  "knowledgeBaseId": "kb-id",
  "title": "新对话",
  "summary": "",
  "messageCount": 0,
  "createdAt": "2026-05-17T05:00:00Z",
  "updatedAt": "2026-05-17T05:00:00Z"
}
```

### 7.2 新建会话

```http
POST /api/knowledge-bases/{kbId}/chat/sessions
synapse-token: <tokenValue>
```

返回体同当前会话接口。前端“新对话”按钮使用该接口。

### 7.3 分页读取消息

```http
GET /api/chat/sessions/{sessionId}/messages?page=0&size=50
synapse-token: <tokenValue>
```

```json
[
  {
    "id": "message-id",
    "sessionId": "session-id",
    "role": "assistant",
    "content": "回答内容",
    "references": [],
    "sequence": 2,
    "createdAt": "2026-05-17T05:01:00Z"
  }
]
```

## 8. 流式问答接口

```http
POST /api/knowledge-bases/{kbId}/query/stream
Content-Type: application/json
Accept: text/event-stream
synapse-token: <tokenValue>
```

```json
{
  "query": "这篇文档的主要内容是什么？",
  "sessionId": "可选。为空时使用或创建当前用户在该知识库下的最新会话"
}
```

SSE 事件：

```text
event: session
data: {"sessionId":"session-id"}

event: token
data: {"token":"文本片段"}

event: references
data: {"references":[{"sourceId":1,"documentId":"doc-id","documentName":"产品手册.pdf","chunkText":"...","score":0.91,"startPosition":0,"endPosition":200,"used":true}]}

event: validation
data: {"trusted":true,"usedSourceIds":[1],"warnings":[]}

event: complete
data: {"sessionId":"session-id"}
```

失败时返回：

```text
event: error
data: {"message":"错误信息","traceId":"trace-id"}
```

后端在 prompt 中用 `<source>` 和 `<user_question>` 边界标记隔离检索片段和用户问题，并明确检索内容不具备指令优先级，以降低文档 prompt 注入风险。每个检索片段会分配 `sourceId`，模型回答应使用 `[1]` 形式引用来源。`validation` 事件用于返回引用编号是否合法、哪些来源被答案实际使用，以及引用校验警告。

查询链路会先尝试 Query 改写，并用原 query 与改写 query 的 embedding 余弦相似度做质量门禁；默认阈值为 `0.8`，未通过或改写失败时自动回退原 query。检索结果来自 Milvus 向量召回与 Mongo BM25 关键词召回的融合重排，`score` 为融合后的最终分数。

聊天记忆会在生成前保存用户消息，并把会话摘要与最近消息拼入 prompt；生成成功后保存 assistant 消息和引用。若生成失败，不保存半截 assistant 回复。

## 9. CORS

CORS 由 `synapse.cors.allowed-origins` 配置控制。默认只允许本地前端：

```yaml
synapse:
  cors:
    allowed-origins: http://localhost:5173,http://127.0.0.1:5173
```

生产环境必须显式配置真实前端域名。
