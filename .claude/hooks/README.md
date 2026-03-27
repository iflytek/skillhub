# Claude Code Hooks

这个目录包含 SkillHub 项目的 Claude Code hooks 脚本，用于自动化代码质量检查和保护关键文件。

## 脚本说明

### PreToolUse Hooks
- **pre-tool-use.js**: 在文件编辑前执行
  - 阻止手动编辑 `web/src/api/generated/schema.d.ts`（自动生成文件）
  - 阻止修改已有的 Flyway migration 文件

### PostToolUse Hooks
- **post-tool-use.js**: 在文件编辑后执行
  - 检测 Controller 文件变更，提醒运行 `make generate-api`

### Stop Hooks
- **stop-frontend-check.js**: 任务完成前执行
  - 前端代码变更时自动运行 TypeScript 和 ESLint 检查
  - 后端代码变更时提醒运行测试

- **stop-auto-retry.js**: 任务异常时自动重试
  - 检测任务异常停止，最多自动重试 3 次

## 技术栈

所有脚本使用 **Node.js** 编写，无需额外依赖：
- ✅ 跨平台兼容（Windows/macOS/Linux）
- ✅ 项目已有 Node.js 环境
- ✅ 无需安装 jq 等外部工具
- ✅ 团队成员开箱即用

## 使用方式

这些脚本由 `.claude/settings.json` 自动调用，无需手动执行。

## 维护指南

修改 hooks 逻辑时：
1. 直接编辑对应的 `.js` 文件
2. 测试验证后提交到 Git
3. 团队成员拉取后自动生效
