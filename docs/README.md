# Synapse 文档

这是 Synapse 知识库 RAG 系统的教育型文档站点，基于 [Mintlify](https://mintlify.com) 构建。

文档面向"会用 Spring Boot 写 CRUD 的 Java 学习者"，通过纵向切片的学习路径，从 HTTP 请求到数据库逐层拆解每个功能。

## 本地预览

```bash
# 安装 Mintlify CLI
npm i -g mint

# 进入文档目录
cd docs

# 启动开发服务器
mint dev
```

访问 `http://localhost:3000` 查看文档。

## 部署

文档通过 Mintlify 自动部署：

1. 推送代码到 GitHub 主分支
2. Mintlify 自动构建并部署
3. 访问 `https://synapse.mintlify.app`

## 文档结构

- `00-overview/` — 项目概览、技术栈、架构鸟瞰
- `01-getting-started/` — 快速上手、首次使用指南
- `02-core-concepts/` — 核心概念铺垫（RAG、向量检索、DDD、分层架构、状态机）
- `03-vertical-slices/` — 纵向切片（认证 → 文档摄入 → 问答检索 → 聊天记忆）
- `04-horizontal-deepdive/` — 横向深入（设计模式、端口设计、Reactive 桥接、混合检索、分块算法、前端架构）
- `05-practice/` — 动手实践（调试、添加字段、更换模型、问题排查）
- `06-deployment/` — 部署文档
- `07-reference/` — API 参考、配置参考、目录速查、术语表
- `08-security/` — 安全设计
