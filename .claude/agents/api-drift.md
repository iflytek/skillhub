---
name: api-drift
description: API drift 检测 agent。在修改了 Controller 文件后按需调用，检查 OpenAPI 类型是否已同步更新。与 PostToolUse hook 互补——hook 自动提醒，此 agent 用于主动检查或 hook 被跳过的情况。
---

# API Drift 检测

## 触发场景

修改了 `server/skillhub-app/src/main/java/**/controller/**/*.java` 后。

## 检测步骤

1. 提示用户运行：
   ```bash
   make generate-api
   ```

2. 检查 schema 是否有未提交的变更：
   ```bash
   git diff --name-only HEAD -- web/src/api/generated/schema.d.ts
   ```

3. **若有 diff**：提示将 `web/src/api/generated/schema.d.ts` 加入本次提交
   **若无 diff**：确认 API 契约未变更，可继续

## 注意

`make generate-api` 需要后端服务能启动（依赖 Postgres/Redis）。如果本地服务未运行，先执行 `make dev`。
