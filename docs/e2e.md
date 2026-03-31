# SkillHub Web E2E 测试说明

本文档用于说明 SkillHub 前端 E2E 的当前状态、目录结构、执行方式、覆盖范围和新增用例的接入规范。

它不是规划文档，也不是待办清单。它的目标是让维护者快速回答下面几个问题：

- 这个项目的 E2E 放在哪里
- 当前已经覆盖了什么
- smoke 和全量回归怎么跑
- 新增页面或交互时，E2E 应该怎么补
- 公共 mock 和断言应该放到哪里

## 1. 当前状态

SkillHub Web 侧已经基于 Playwright 建立了完整的 E2E 基线。

当前基线包括：

- Playwright 主配置：[web/playwright.config.ts](/Users/admin/Documents/skillhub/web/playwright.config.ts)
- Smoke 配置：[web/playwright.smoke.config.ts](/Users/admin/Documents/skillhub/web/playwright.smoke.config.ts)
- 用例目录：`web/e2e`
- 公共 helper：`web/e2e/helpers`
- 测试产物：`web/test-results/`、`web/playwright-report/`
- 当前 spec 数量：`30`

Playwright 运行特征：

- 浏览器：`chromium`
- `baseURL`: `http://localhost:3000`
- `reporter`: `html`
- `screenshot: 'on'`
- `trace: 'on-first-retry'`
- 预览服务由 Playwright 自动拉起：`pnpm preview --port 3000`

## 2. 目录结构

```text
web/
├── e2e/
│   ├── *.spec.ts
│   └── helpers/
│       ├── api-fixtures.ts
│       ├── auth-fixtures.ts
│       ├── assertions.ts
│       └── route-mocks.ts
├── playwright.config.ts
└── playwright.smoke.config.ts
```

职责约定：

- `web/e2e/*.spec.ts`：按业务流组织的 E2E 用例
- `web/e2e/helpers/api-fixtures.ts`：测试数据工厂、标准响应封装
- `web/e2e/helpers/auth-fixtures.ts`：登录态、用户角色、语言环境相关 mock
- `web/e2e/helpers/route-mocks.ts`：页面级 route mock 组装
- `web/e2e/helpers/assertions.ts`：通用断言

原则上不要再新增类似旧兼容层的 helper。新能力优先补到现有四个 helper 中。

## 3. 当前覆盖范围

当前 E2E 已经覆盖项目的大部分关键业务路径，重点不是“组件遍历”，而是“用户可感知业务流”。

### 3.1 公共入口与鉴权

相关 spec：

- `web/e2e/landing-navigation.spec.ts`
- `web/e2e/auth-entry.spec.ts`
- `web/e2e/public-pages.spec.ts`
- `web/e2e/route-guard.spec.ts`

已覆盖：

- 落地页搜索跳转
- 登录、注册、`returnTo` 回跳
- 隐私条款和服务条款可达
- 受保护路由的匿名访问拦截

### 3.2 搜索与详情浏览

相关 spec：

- `web/e2e/search-flow.spec.ts`
- `web/e2e/network-error.spec.ts`
- `web/e2e/namespace-page.spec.ts`
- `web/e2e/skill-detail-browse.spec.ts`
- `web/e2e/share-button.spec.ts`

已覆盖：

- 搜索关键字、排序、标签、分页、URL 状态同步
- 搜索接口失败后的页面退化和恢复
- 命名空间页成功、空态、404
- 技能详情页概览、版本、文件、README 预览
- 分享按钮、复制文本、状态回退

### 3.3 用户工作台

相关 spec：

- `web/e2e/dashboard-shell.spec.ts`
- `web/e2e/publish-flow.spec.ts`
- `web/e2e/my-skills.spec.ts`
- `web/e2e/my-namespaces.spec.ts`
- `web/e2e/stars.spec.ts`
- `web/e2e/tokens.spec.ts`
- `web/e2e/notifications.spec.ts`
- `web/e2e/settings-profile.spec.ts`
- `web/e2e/settings-security.spec.ts`
- `web/e2e/settings-notification-settings.spec.ts`

已覆盖：

- Dashboard 基础壳层
- 发布流程成功和重复版本失败
- 我的技能筛选、撤回、归档、恢复、推广冲突
- 我的命名空间创建、冻结、归档、恢复
- 收藏列表空态和分页
- Token 创建、更新过期时间、删除
- 通知筛选与跳转
- 个人资料编辑、安全设置、通知偏好

### 3.4 治理与审核

相关 spec：

- `web/e2e/namespace-members.spec.ts`
- `web/e2e/namespace-reviews.spec.ts`
- `web/e2e/reviews.spec.ts`
- `web/e2e/review-detail.spec.ts`
- `web/e2e/promotions.spec.ts`
- `web/e2e/reports.spec.ts`

已覆盖：

- 命名空间成员管理
- 命名空间审核列表
- 技能审核队列
- 审核详情展开、文件预览、审批动作
- 推广审批
- 举报处理

### 3.5 管理后台与权限

相关 spec：

- `web/e2e/admin-users.spec.ts`
- `web/e2e/admin-audit-log.spec.ts`
- `web/e2e/admin-labels.spec.ts`
- `web/e2e/cli-auth.spec.ts`
- `web/e2e/role-access-control.spec.ts`

已覆盖：

- 用户管理
- 审计日志筛选与分页
- 标签管理
- CLI Auth 回跳和 token 下发
- 多角色路由访问边界

## 4. Smoke Suite

Smoke suite 是快速关键路径回归，不追求覆盖面最大，只追求：

- 关键链路代表性足够强
- 执行时间短
- 稳定，不允许 flaky

当前 smoke 集合：

- `web/e2e/auth-entry.spec.ts`
- `web/e2e/search-flow.spec.ts`
- `web/e2e/skill-detail-browse.spec.ts`
- `web/e2e/publish-flow.spec.ts`
- `web/e2e/review-detail.spec.ts`
- `web/e2e/role-access-control.spec.ts`

这些用例覆盖：

- 登录与回跳
- 搜索与 URL 状态
- 技能详情浏览
- 发布流程
- 审核流程
- 后台与治理权限边界

## 5. 执行命令

推荐优先使用根目录 `Makefile`。

### 5.1 根目录命令

```bash
make test-e2e-smoke-frontend
make test-e2e-frontend
```

### 5.2 Web 目录命令

```bash
cd web && pnpm test:e2e:smoke
cd web && pnpm test:e2e
cd web && pnpm exec playwright test e2e/<feature-name>.spec.ts
cd web && pnpm test:e2e:ui
```

### 5.3 常见使用场景

- 提交前快速回归：`make test-e2e-smoke-frontend`
- 功能开发完成后全量回归：`make test-e2e-frontend`
- 只调一条业务流：`cd web && pnpm exec playwright test e2e/<feature-name>.spec.ts`
- 本地交互调试：`cd web && pnpm test:e2e:ui`

## 6. 新增 E2E 的接入方式

### 6.1 什么时候必须补 E2E

以下场景默认都应该补 E2E：

- 新增页面或新路由
- 修改已有关键业务流
- 新增权限控制或路由守卫
- 新增表单提交流程
- 新增审批、删除、归档、恢复、发布等状态变更动作
- 修改 dashboard、详情页、后台页等高风险入口

如果是前端行为变化，但没有对应 E2E，回归风险会很高。

### 6.2 文件放在哪里

- 新 spec：`web/e2e/<feature-name>.spec.ts`
- 复用数据：`web/e2e/helpers/api-fixtures.ts`
- 复用登录态：`web/e2e/helpers/auth-fixtures.ts`
- 复用页面 mock：`web/e2e/helpers/route-mocks.ts`
- 复用断言：`web/e2e/helpers/assertions.ts`

### 6.3 命名建议

命名以业务流为单位，不要按组件命名。

推荐：

- `publish-flow.spec.ts`
- `review-detail.spec.ts`
- `role-access-control.spec.ts`

不推荐：

- `publish-button.spec.ts`
- `skill-card.spec.ts`
- `dashboard-header.spec.ts`

### 6.4 用例最低要求

每个新业务流至少应覆盖：

- 1 条成功路径
- 1 条失败路径、边界路径或权限路径

示例：

- 发布成功 + 重复版本失败
- 审核通过 + 无权限访问被拦截
- 搜索成功 + 搜索接口失败退化

## 7. 编写规范

### 7.1 按用户路径写，不按组件写

E2E 关注的是完整行为链路，例如：

- 匿名用户搜索并打开技能
- 登录用户发布技能
- 审核员审批待审技能
- 管理员管理标签和用户

不要把 E2E 退化成组件冒烟。

### 7.2 优先复用页面级 mock

如果某个页面已经有对应的 `mockXxxPage()`，优先复用，不要在 spec 内手写大量 `page.route()`。

这样做的原因很简单：

- 重复逻辑少
- 修改接口时只需要改一处
- 用例更短，更容易读

### 7.3 选择器优先级

优先级固定如下：

- `getByRole`
- `getByLabel`
- `getByTestId`

避免：

- 深层 CSS 选择器
- 结构耦合太强的 DOM 定位
- 依赖页面偶然文本布局的脆弱断言

只有在语义选择器不稳定时，才补 `data-testid`。

### 7.4 禁止盲等

不要新增 `waitForTimeout`。

优先使用：

- `await expect(locator).toBeVisible()`
- `await expect(page).toHaveURL(...)`
- `await expect(locator).toContainText(...)`
- `await expect.poll(...)`

### 7.5 失败场景要真实

失败场景不要只做“页面能打开”。优先覆盖这些真实风险：

- 401 或匿名跳转登录
- 403 或权限回退
- 404 或空态
- 接口失败后的 UI 退化
- 重复提交、冲突、校验失败

## 8. Smoke 维护规则

只有满足下面条件的 spec 才应该进入 smoke：

- 能代表一个平台级关键路径
- 失败时对平台可用性有明确信号
- 不依赖脆弱时序
- 能稳定跑在默认并发下

不应该把 smoke 当成“小型全量回归”。

更具体地说：

- smoke 放代表性路径
- 边界组合、复杂筛选、分页、状态变体放全量套件
- 如果某条用例长期 flaky，应先修稳定性，再决定是否保留在 smoke

## 9. 调试与产物

Playwright 产物默认保存在：

- `web/test-results/`
- `web/playwright-report/`

排查失败时优先看：

- 失败截图
- `error-context.md`
- HTML report
- 对应用例里的 route mock 是否与页面当前请求一致

如果是重绘或时序问题，先收紧断言和选择器，不要用时间等待掩盖问题。

## 10. 维护建议

### 10.1 保持 helper 收敛

公共逻辑应该继续向下面四个文件集中：

- `api-fixtures.ts`
- `auth-fixtures.ts`
- `route-mocks.ts`
- `assertions.ts`

不要重新引入新的兼容层或历史 helper。

### 10.2 页面扩展时，优先补页面级 mock

如果新增一个 dashboard 子页，建议顺序是：

1. 先补 `route-mocks.ts` 中的页面 mock
2. 再补对应 spec
3. 最后判断是否需要进入 smoke

### 10.3 用例拆分要克制

大页面建议按“浏览”和“状态变更”拆分，而不是把所有交互塞进一个超大 spec。

这样失败时更容易定位。

## 11. 当前建议的使用方式

如果你只是想确认项目健康状态：

```bash
make test-e2e-smoke-frontend
```

如果你修改了前端关键链路并准备合并：

```bash
make test-e2e-frontend
```

如果你要给新功能补 E2E：

1. 先看是否已有可复用的 `mockXxxPage()`
2. 没有就先在 `web/e2e/helpers/route-mocks.ts` 补页面级 mock
3. 用业务流命名 spec
4. 至少覆盖成功路径和失败/边界路径
5. 再决定这条 spec 是否值得进入 smoke
