# SkillHub Web E2E 测试说明（真实请求版）

本文档描述当前 `web/e2e` 的真实请求（non-mock）测试体系、执行方式与维护规范。

## 1. 当前状态

`web/e2e` 已完成 API mock 迁移，现状如下：

- 不再使用 `helpers/route-mocks.ts`、`helpers/api-fixtures.ts`、`helpers/assertions.ts`
- 不在 spec 内使用 `page.route('**/api/...')` 拦截 API
- 通过 Playwright `request`（`page.context().request`）与后端进行真实认证与数据交互
- 关键会话 helper：`web/e2e/helpers/session.ts`

当前 Playwright 配置（`web/playwright.config.ts`）：

- `baseURL`: `http://localhost:3000`
- 浏览器：`chromium`
- `workers`: `1`（真实请求模式下优先稳定性）
- `fullyParallel`: `false`
- `reporter`: `html`
- `trace: 'on-first-retry'`
- `screenshot: 'on'`
- `webServer.command`: `pnpm exec vite --host 127.0.0.1 --port 3000 --strictPort`

## 2. 目录结构

```text
web/
├── e2e/
│   ├── auth-entry.spec.ts
│   ├── dashboard-shell.spec.ts
│   ├── landing-navigation.spec.ts
│   ├── public-pages.spec.ts
│   ├── route-guard.spec.ts
│   ├── settings-pages.spec.ts
│   ├── tokens.spec.ts
│   └── helpers/
│       ├── auth-fixtures.ts
│       ├── session.ts
│       └── test-data-builder.ts
├── playwright.config.ts
└── playwright.smoke.config.ts
```

职责约定：

- `web/e2e/*.spec.ts`：按用户业务流组织测试
- `web/e2e/helpers/auth-fixtures.ts`：locale 等非网络辅助
- `web/e2e/helpers/session.ts`：真实认证会话建立（登录/注册 + worker 级隔离）
- `web/e2e/helpers/test-data-builder.ts`：通用测试数据构建与清理（namespace/skill/review）

## 3. 当前覆盖范围

当前真实请求 E2E 覆盖 23 个 spec：

- `auth-entry.spec.ts`：登录入口、注册入口、`returnTo` 保留
- `landing-navigation.spec.ts`：首页导航与匿名受限跳转
- `public-pages.spec.ts`：公开法律页面可达
- `search-flow.spec.ts`：搜索查询状态与匿名收藏筛选跳转登录
- `route-guard.spec.ts`：匿名拦截与登录后访问受保护路由
- `skill-detail-browse.spec.ts`：登录后命名空间/技能详情不存在场景
- `dashboard-shell.spec.ts`：Dashboard 基础壳层与快捷入口
- `dashboard-routes.spec.ts`：Dashboard 主要子路由可达与命名空间治理页面可达
- `workspace-pages.spec.ts`：我的技能/我的命名空间工作台页面可达
- `my-namespaces-data.spec.ts`：通过 request 创建 namespace 并在工作台验证可见
- `my-skills-data.spec.ts`：通过 request 发布 skill 并在工作台验证可见
- `my-skills-navigation.spec.ts`：从我的技能列表进入技能详情并返回
- `namespace-members-data.spec.ts`：通过 request 准备 namespace 后验证成员管理页可达
- `namespace-page-data.spec.ts`：通过 request 准备 namespace/skill 后验证命名空间公开页可达
- `namespace-reviews-data.spec.ts`：通过 request 造 review 数据并验证命名空间审核页可达
- `publish-flow-ui.spec.ts`：在发布页上传真实 zip 并验证发布后回到我的技能
- `dashboard-personal-modules.spec.ts`：`/dashboard/stars` 与 `/dashboard/notifications` 个人模块可达
- `settings-pages.spec.ts`：Profile/Security/Notifications 页面基础行为
- `settings-routing.spec.ts`：`/settings/accounts` 重定向到 `/settings/security`
- `tokens.spec.ts`：Token 管理入口可达
- `protected-routes.spec.ts`：匿名访问 dashboard/admin 受保护路由跳转登录
- `cli-auth.spec.ts`：CLI Auth 缺失参数错误路径
- `role-access-control.spec.ts`：登录普通用户访问治理/管理台受限路由会被回退

## 4. 执行命令

推荐优先使用根目录命令：

```bash
make test-e2e-frontend
make test-e2e-smoke-frontend
```

在 `web` 目录也可直接执行：

```bash
cd web && pnpm test:e2e
cd web && pnpm test:e2e:smoke
cd web && pnpm exec playwright test e2e/<feature>.spec.ts
cd web && pnpm test:e2e:ui
```

说明：

- 在你已手动启动服务时，可直接执行 `cd web && pnpm test:e2e`
- 在 CI 或独立环境中，可让 Playwright 根据配置自动拉起前端服务

## 5. 编写规范（真实请求）

### 5.1 严禁 API mock

新增或修改 E2E 时，禁止：

- 引入 `page.route('**/api/...')`
- 引入页面级 API mock helper
- 在用例中伪造关键业务响应

### 5.2 认证统一走 `session.ts`

- 需要登录态的用例统一复用 `registerSession(page, testInfo)`
- 通过 worker 级账号隔离避免并发冲突
- 不在 spec 内重复手写登录/注册流程

### 5.3 选择器优先级

- `getByRole`
- `getByLabel`
- `getByTestId`

避免结构耦合高的 CSS 深层选择器。

### 5.4 禁止盲等

不要新增 `waitForTimeout`。优先：

- `await expect(locator).toBeVisible()`
- `await expect(page).toHaveURL(...)`
- `await expect(locator).toContainText(...)`

## 6. Smoke 规则

Smoke 只保留关键路径，目标是快且稳，不追求覆盖面最大。

当前 smoke 套件建议包含：

- `auth-entry.spec.ts`
- `landing-navigation.spec.ts`
- `route-guard.spec.ts`
- `dashboard-shell.spec.ts`

## 7. 常见问题排查

- 认证失败：先确认后端可达（`http://localhost:8080`）且注册/登录接口正常
- 用例偶发失败：优先检查选择器歧义和断言时机，不要用固定等待掩盖
- 并发冲突：确认用例是否复用统一 helper，并避免共享可变测试数据

## 8. 验收口径

满足以下条件视为迁移完成：

- `web/e2e/**/*.spec.ts` 不含 API mock
- 真实请求路径可达并稳定
- `cd web && pnpm test:e2e` 全量通过
- `cd web && pnpm test:e2e:smoke` 通过
