# 前端规范入口

正文读取与换会话接续遵循[有界读取规则](../workflow/context-reading.md)，按本页触发条件选资料，不批量加载整个目录。

适用于 SkillHub 的页面、组件、样式、状态、Hook 和 API 消费代码。先读本页，再按任务
读取下表中的专题；不要求纯类型或数据逻辑修改加载全部视觉规范，也不要求纯前端任务
读取全部后端规范。仓库 AGENTS.md 仍适用；CLAUDE.md 仅在当前检出存在时读取。

## 通用原则

- 先检查已有组件、调用方和测试，再决定复用或扩展；业务逻辑不因“复用”下沉到通用组件。
- 服务端状态与临时交互状态分开，避免重复缓存和相互同步；状态作用域尽量贴近使用处。
- 保留键盘、焦点、表单、受控状态和错误反馈契约；加载、空态、错误与无权限分别表达。
- 类型来自真实接口契约；设计基线、当前实现和验证证据分别记录，不把文档当作能力证明。

## SkillHub 约定

- 使用现有 React/TypeScript、Vite、Tailwind 工程；版本以当前依赖声明为准。
- 按 FSD 放置代码：`web/src/pages/` 为页面，`features/` 为业务功能，`entities/` 为领域展示，
  `shared/` 为可复用基础能力；不为对齐目录示例搬动已有效工作的业务代码。
- TanStack Query 管理服务端请求、缓存和失效，不用 `useEffect` 取数；Zustand 管理需要共享
  的本地/UI 状态，组件内部短暂交互可用组件状态，不把服务端缓存复制进 UI store。
- API 复用既有客户端（含 `openapi-fetch` 与类型化 wrapper）及 `web/src/api/generated/schema.d.ts`；不使用 `any`。
  后端契约变化后通过 `make generate-api` 更新生成类型，不手改生成结果。
- 复用现有 Radix 与 shadcn 源码体系、`cn()`、组件变体和 Tailwind；“不使用 shadcn
  运行时库”不等于禁止参考其源码，不另建整套 UI 体系。
- 用户可见文案使用 i18next/react-i18next，翻译资源在 `web/src/i18n/`。
- 视觉规则见专题；字号、密度、组件 API 必须核对当前实现，个人预览例外不自动升级为全站默认。

## 按需深读

| 修改触发条件 | 读取内容 |
|---|---|
| 新增文件或调整层次 | [目录结构](./directory-structure.md) |
| 修改视觉、布局或可见 UI | 读取[主题与组件依据](./design-system.md)，再读[视觉基础](./visual-foundations.md)、[组件规则](./component-guidelines.md) |
| 使用选定设计系统调整主题、组件或页面 | [设计系统采用](./design-system.md)；仅在资源已提供时按任务读取其主题、组件和规范 |
| 动效设计、实现或评审 | [交互动效规范入口](./motion-guidelines.md)及其链接的正文；按入口说明为实现／检查 Agent 准备上下文 |
| 选型、新增或盘点组件 | [组件目录](./component-catalog.md)；候选不代表已安装 |
| 新增页面或改变页面组合 | [页面模式](./page-patterns.md)及其引用的视觉与组件专题 |
| 修改请求 Hook 或缓存失效 | [Hook 规则](./hook-guidelines.md) |
| 修改状态作用域或 store | [状态管理](./state-management.md) |
| 修改 API 消费、类型或生成 schema | [类型安全](./type-safety.md) |
| 修改架构、路由或共享模式 | [前端架构](../../../docs/08-frontend-architecture.md) |
| 涉及版本状态、权限或公开分发 | [生命周期契约](../../../docs/14-skill-lifecycle.md)，并核对相关源码与测试 |
| 新增日志、错误诊断或排查凭证泄漏 | [前端验收：日志与诊断](./quality-guidelines.md#日志与诊断) |
| 组件交互、视觉与页面验收 | [前端验收](./quality-guidelines.md) |

## 验证与补充参考

按改动范围读取[测试与验证入口](../testing/index.md)，选择最小相关检查；涉及共享组件、
路由或用户流程时按影响扩大。可见变化在环境允许时用浏览器验证；未运行或环境失败明确记录。

本目录持有前端工程规范正文，不依赖旧工程 Skill 自动发现。架构背景与业务契约仍按上表引用。
遇到文档与源码或其他规则冲突，记录差异，按适用指令优先级处理，不借整理规范默改业务规则。
