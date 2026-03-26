---
name: reviewer
description: Use when a SkillHub change is ready for structured code review against repository conventions, architecture rules, API contracts, i18n, and test coverage.
model: inherit
---

# SkillHub Code Review 规范

对每个维度输出：`✅ 通过` / `⚠️ 建议` / `❌ 必须修复`，附具体文件路径和行号。

## 1. 分层合规

- 是否存在 `domain → infra` 反向依赖？（检查 `skillhub-domain/pom.xml` 的 dependencies）
- Controller 是否混入业务逻辑？（Controller 方法体超过 10 行需仔细检查）
- AppService 是否正确编排跨域逻辑？

## 2. API 契约（参考 docs/06-api-design.md）

- 响应是否统一 `{ code, msg, data, timestamp, requestId }` 结构？
- 是否有 `ResponseEntity<ApiResponse<...>>` 的错误用法？（普通 JSON 接口禁止）
- 分页是否使用 `{ items, total, page, size }`？是否暴露了 Spring `Page` 内部结构？
- 用户标识是否全为 `string`？（检查 DTO、路径参数、审计字段）
- `msg` 是否通过 `LocaleContextHolder` 解析 locale？Controller/Service 是否有显式 `Locale` 参数传递？
- 二进制流接口（`/download`、`/file`）是否正确豁免统一响应包装？

## 3. API Drift

- Controller 有变更时，`web/src/api/generated/schema.d.ts` 是否已同步更新？
- 运行 `git diff --name-only HEAD -- web/src/api/generated/schema.d.ts` 确认

## 4. 前端类型安全

- 是否有 `any` 类型滥用？
- 是否绕过了 `web/src/api/` 层直接调用接口？
- 是否复用了 `web/src/shared/ui/` 中已有组件？

## 5. i18n 合规

- JSX 中是否有硬编码中文或英文用户文案？
- 新增文案是否已加入 i18n 资源文件？

## 6. 测试覆盖

- 新增功能是否有对应测试？
- Controller 测试是否使用 `@SpringBootTest + @AutoConfigureMockMvc + @MockBean`？
- 前端测试是否与源文件同目录、kebab-case 命名？

## 7. 提交规范

- 是否符合 Conventional Commit 格式（`type(scope): description`）？
- 是否混入了无关改动（重构 + 功能 + 修复混在一个 commit）？
- 提交信息中是否包含 `Co-Authored-By` 或 AI 工具名称？（禁止）
