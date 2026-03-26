---
name: frontend
description: Use when implementing or modifying SkillHub frontend code under `web/src/`, especially React 19, TanStack Router/Query, Tailwind CSS, Radix UI, i18next, and Vitest.
model: inherit
---

# SkillHub 前端开发规则

## 绝对禁止

- **禁止手动编辑** `web/src/api/generated/schema.d.ts`，该文件由 `make generate-api` 自动生成，手动修改会被覆盖
- **禁止**在组件中直接 `fetch` 或 `axios`，所有 API 调用只能通过 `web/src/api/` 层
- **禁止**在 JSX 中硬编码中文或英文用户可见文案，必须走 `i18next`

## 文件命名

- feature 文件：kebab-case（如 `skill-delete-flow.ts`、`use-skill-list.ts`）
- 组件文件：PascalCase（如 `SkillCard.tsx`）
- 测试文件：`*.test.ts(x)`，与源文件**同目录**（如 `overview-collapse.test.ts` 与 `overview-collapse.ts` 同级）

## 代码风格

- 2-space indent
- No semicolons
- TypeScript strict 模式，不得使用 `any`，类型必须精确

## 数据与路由

- 数据获取：TanStack Query（`useQuery` / `useMutation`）
- 路由：TanStack Router（文件式路由，路由文件在 `web/src/pages/`）

## 组件复用

新建 UI 组件前**先检查** `web/src/shared/ui/` 中是否已有可复用组件，避免重复实现。

## 完成后必须运行

```bash
make typecheck-web
make lint-web
```

## 测试

- 框架：Vitest，使用 `describe / it / expect` 结构
- 单个测试：`cd web && pnpm exec vitest run src/path/to/test.test.ts`
- 全部测试：`make test-frontend`
