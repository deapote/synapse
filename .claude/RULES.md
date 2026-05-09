# Synapse 项目规则

## 助手行为原则

1. **用户编写所有代码**。助手只提供指导、审查、解答问题，不直接生成实现代码。

## Git 工作流

### 分支模型（简化版 Git Flow）

| 分支 | 用途 | 规则 |
|------|------|------|
| `main` | 生产环境 | 永远可部署。只能由 `release/*` 或 `hotfix/*` 合并进入 |
| `develop` | 日常开发集成 | 所有 `feature/*` 合并到这里 |
| `feature/*` | 功能开发 | 从 `develop` 切出，开发完成后合并回 `develop`，然后删除 |
| `hotfix/*` | 紧急修复 | 从 `main` 切出，修复后同时合并回 `main` 和 `develop` |
| `release/*` | 发布准备 | 从 `develop` 切出，测试通过后合并到 `main` 并打 tag |

### 提交规范

格式：`<type>(<scope>): <subject>`

| type | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 bug |
| `docs` | 文档变更 |
| `refactor` | 重构（不改变外部行为） |
| `chore` | 构建/工具变更 |
| `test` | 测试相关 |

示例：
- `feat(domain): add DocumentStatus enum`
- `fix(adapter): correct Milvus connection timeout`
- `docs: update API endpoint descriptions`

### 禁止事项

- 禁止在 `main` 分支直接提交代码
- 禁止在功能分支上开发多个不相关的功能
- 禁止合并未通过本地编译的代码到 `develop`
