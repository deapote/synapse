# Synapse Frontend — 架构设计文档

## 1. 系统概述

Synapse 前端是 Synapse 知识库 RAG 系统的 Web 界面，基于 Vue 3 + Vite + TypeScript 构建。
提供知识库管理、文档上传与管理、知识库问答三大核心功能模块。

## 2. 模块结构

```
synapse-frontend/
├── public/
├── src/
│   ├── api/              # API 服务层 — 统一封装 HTTP 请求
│   ├── components/       # 公共组件 — 可复用的 UI 组件
│   ├── composables/      # 组合式函数 — 与 UI 无关的逻辑复用
│   ├── layouts/          # 布局组件 — 页面骨架
│   ├── router/           # 路由配置 — 声明式导航
│   ├── stores/           # Pinia Store — 客户端状态管理
│   ├── types/            # TypeScript 类型 — 前后端契约
│   ├── views/            # 页面组件 — 路由级视图
│   ├── styles/           # 全局样式 — CSS Variables + 工具类
│   ├── App.vue
│   └── main.ts
├── docs/
│   └── ARCHITECTURE.md
├── index.html
├── package.json
└── vite.config.ts
```

## 3. 技术决策

### 3.1 为什么选 Vue 3 + 组合式 API

- **渐进式框架**：适合中小型管理后台，学习成本低于 React 生态。
- **组合式 API**：逻辑复用清晰，与 TypeScript 配合好。
- **单文件组件**：模板 + 脚本 + 样式在一个文件内，开发效率高。

### 3.2 为什么选 Pinia 而不是 Vuex

- Vue 3 官方推荐，TypeScript 支持更好。
- 语法更简洁，无 mutations 冗余概念。
- Devtools 集成完善。

### 3.3 为什么选原生 CSS 而不是 Tailwind / Sass

- **Tailwind**：utility-first 在管理后台中会导致类名冗长，且需要额外学习成本。
- **Sass**：本项目样式复杂度不高，原生 CSS Variables 已足够。
- **决策**：原生 CSS + CSS Variables，保持零额外依赖，与设计风格（极简）一致。

### 3.4 为什么选 Axios 而不是原生 fetch

- 拦截器机制成熟（统一错误处理、Token 注入）。
- 自动 JSON 序列化/反序列化。
- 请求/取消更便捷（后续如需取消轮询）。

## 4. 组件层次

### 4.1 布局层次

```
App.vue
└── MainLayout.vue (default layout)
    ├── AppSidebar.vue         # 左侧导航
    ├── AppHeader.vue          # 顶部栏（可选）
    └── <router-view>          # 页面内容
        ├── KnowledgeBaseList.vue
        ├── KnowledgeBaseDetail.vue
        └── ChatView.vue
```

### 4.2 页面组件拆分

**KnowledgeBaseList（知识库列表）**
```
KnowledgeBaseList.vue
├── AppHeader (title + "新建" button)
├── DataTable (知识库列表)
│   └── StatusBadge (状态标签)
├── Modal (新建知识库表单)
└── ConfirmDialog (删除确认)
```

**KnowledgeBaseDetail（知识库详情）**
```
KnowledgeBaseDetail.vue
├── 知识库信息卡片
├── DocumentList (文档表格)
│   ├── 上传区域 / 按钮
│   ├── DataTable (文档列表)
│   │   └── StatusBadge (文档状态)
│   └── ConfirmDialog (删除确认)
└── 快捷入口：进入问答
```

**ChatView（问答页面）**
```
ChatView.vue
├── ChatHeader (知识库选择器 + 模型信息)
├── ChatMessages (消息列表)
│   ├── ChatMessageUser
│   └── ChatMessageAI
│       └── ReferenceCard (引用来源)
└── ChatInput (输入框 + 发送按钮)
```

## 5. 状态管理

### 5.1 Store 设计

**useKnowledgeBaseStore**
```ts
interface KnowledgeBaseState {
  list: KnowledgeBase[]        // 所有知识库
  currentId: string | null     // 当前选中的知识库 ID
  loading: boolean
  error: string | null
}

// Actions
- fetchList()                 // 获取列表
- create(data)                // 创建知识库
- remove(id)                  // 删除知识库
- setCurrent(id)              // 设置当前知识库（持久化到 localStorage）
```

**useDocumentStore**
```ts
interface DocumentState {
  list: Document[]             // 当前知识库下的文档
  loading: boolean
  uploading: boolean
  error: string | null
}

// Actions
- fetchList(kbId)             // 获取文档列表
- upload(kbId, file)          // 上传文档（multipart/form-data）
- remove(id)                  // 删除文档
- refreshStatus()             // 轮询更新状态
```

**useChatStore**
```ts
interface ChatState {
  messages: ChatMessage[]      // 消息历史
  knowledgeBaseId: string | null
  loading: boolean
  error: string | null
}

// Actions
- sendQuestion(kbId, query)   // 发送问题
- clearHistory()              // 清空对话
```

### 5.2 状态流

```
用户操作 → Vue 组件 → Pinia Action → API 调用 → 后端
                                              ↓
                                    Action 更新 State
                                              ↓
                                    Vue 响应式更新 UI
```

## 6. API 集成模式

### 6.1 Axios 实例配置

```ts
// api/client.ts
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：从 localStorage 读取 Sa-Token 并写入动态 token 请求头
// 响应拦截器：统一错误处理；401 清理 token 并跳转登录页
```

### 6.2 API 模块

| 模块 | 文件 | 接口 |
|------|------|------|
| 认证/权限 | `api/auth.ts` | login, logout, me, users, roles |
| 知识库 | `api/knowledgeBase.ts` | create, delete, list |
| 文档 | `api/document.ts` | upload, listByKb, delete |
| 问答 | `api/query.ts` | streamQueryKnowledgeBase |

### 6.3 类型定义

所有类型与后端 API 文档保持一致：

```ts
// types/index.ts
export interface KnowledgeBase {
  id: string
  name: string
  description: string
  ownerUserId: string
  createdAt: string
}

export type RoleName = 'ADMIN' | 'USER'
export type AuthPermission = 'KB_READ' | 'KB_WRITE' | 'KB_DELETE' | 'AUTH_ADMIN'

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  roles: RoleName[]
  permissions: AuthPermission[]
  enabled: boolean
  createdAt: string | null
}

export interface Document {
  id: string
  knowledgeBaseId: string
  fileName: string
  fileType: string
  fileSize: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  chunkCount: number
  uploadedAt: string | null
}

export interface ChunkReference {
  documentId: string
  documentName: string
  chunkText: string
  score: number
  startPosition: number
  endPosition: number
}

// 问答通过 SSE 返回 token / references / complete / error 事件。
```

## 7. 路由设计

```ts
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', redirect: '/knowledge-bases' },
      { path: 'knowledge-bases', name: 'KnowledgeBaseList' },
      { path: 'knowledge-bases/:id', name: 'KnowledgeBaseDetail', props: true },
      { path: 'knowledge-bases/:id/chat', name: 'KnowledgeBaseChat', props: true },
      { path: 'chat', name: 'Chat' },
      { path: 'admin', name: 'Admin', meta: { requiresAdmin: true } }
    ]
  }
]
```

路由守卫会在进入非公开页面前调用 `/api/auth/me`。管理页需要 `ADMIN` 角色。

## 8. 样式策略

### 8.1 CSS Variables

所有设计 Token 集中定义，方便后续主题切换：

```css
:root {
  /* 背景 */
  --bg-base: #ffffff;
  --bg-subtle: #f9f9f9;
  --bg-hover: #f3f3f3;
  --bg-active: #ebebeb;

  /* 边框 */
  --border: #e5e5e5;
  --border-strong: #d4d4d4;

  /* 文字 */
  --text-primary: #111111;
  --text-secondary: #6b6b6b;
  --text-muted: #9ca3af;

  /* 强调色 */
  --accent: #2563eb;
  --accent-hover: #1d4ed8;
  --accent-subtle: rgba(37, 99, 235, 0.08);

  /* 语义色 */
  --success: #16a34a;
  --success-subtle: rgba(22, 163, 74, 0.08);
  --warning: #d97706;
  --warning-subtle: rgba(217, 119, 6, 0.08);
  --danger: #dc2626;
  --danger-subtle: rgba(220, 38, 38, 0.08);

  /* 布局 */
  --sidebar-w: 260px;
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
}
```

### 8.2 组件样式原则

- **无硬编码**：所有颜色和尺寸使用 CSS Variables。
- **Scoped 优先**：组件样式加 `scoped`，避免污染。
- **工具类**：少量通用工具类在 `global.css` 中定义（如 `.flex`, `.mb-4`）。

## 9. 关键交互设计

### 9.1 文档上传

1. 用户点击"上传文档" → 弹出文件选择
2. 前端直接 multipart/form-data POST 到后端
3. 上传成功后，文档状态为 PENDING，自动启动轮询
4. 轮询间隔 2 秒，最多 30 次（约 1 分钟）
5. 状态变为 COMPLETED 或 FAILED 后停止轮询

### 9.2 知识库问答

1. 用户选择知识库（下拉选择器或从知识库详情页进入）
2. 输入问题，点击发送
3. 前端调用 POST /api/knowledge-bases/{kbId}/query/stream
4. 使用 fetch + ReadableStream 手动解析 SSE
5. `token` 事件流式追加到 assistant 消息
6. `references` 事件渲染引用片段，`complete` 结束，`error` 展示失败原因

### 9.3 删除操作

1. 点击删除按钮 → 弹出 ConfirmDialog
2. 确认后调用删除 API
3. 成功后从列表移除（乐观更新或重新拉取列表）

## 10. 性能考量

- **路由懒加载**：所有页面组件使用 `() => import()` 懒加载。
- **轮询控制**：文档状态轮询使用 `setInterval`，组件卸载时 `clearInterval`。
- **防抖输入**：问答输入框发送按钮不需要防抖（点击触发），但搜索过滤需要。
- **列表虚拟化**：当前文档/知识库数量不多，暂不需要虚拟滚动，后续可扩展。

## 11. 构建与部署

### 开发

```bash
npm run dev        # 启动开发服务器，端口 5173，代理到 localhost:8082
```

### 生产构建

```bash
npm run build      # 输出到 dist/，静态文件可直接部署
npm run preview    # 本地预览生产构建
```

### 与后端集成

生产环境将前端 `dist/` 部署到 Nginx，配置反向代理：

```nginx
location / {
    root /var/www/synapse-frontend/dist;
    try_files $uri $uri/ /index.html;
}
location /api/ {
    proxy_pass http://localhost:8082/api/;
}
```

## 12. 扩展预留

- **深色模式**：通过 `data-theme="dark"` + CSS Variables 切换，预留变量覆写。
- **SSE 流式输出**：问答接口可升级为 EventSource，前端逐步渲染回答。
- **对话历史**：ChatStore 可对接 localStorage 或后端持久化。
- **文件拖拽上传**：文档上传区域可扩展拖拽交互。
