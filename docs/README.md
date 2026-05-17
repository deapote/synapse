# Synapse 文档

这是 Synapse 知识库 RAG 系统的官方文档站点，基于 [Mintlify](https://mintlify.com) 构建。

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

- `introduction/` — 项目介绍、快速开始、技术栈
- `guides/` — 使用指南（面向最终用户）
- `core-concepts/` — 核心概念（面向开发者）
- `developer-guide/` — 开发者指南
- `api-reference/` — API 接口参考
- `deployment/` — 部署文档
- `security/` — 安全设计
