# skillhub 前端架构设计

## 1 技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| 框架 | React 19 + TypeScript | |
| 构建 | Vite | |
| 路由 | TanStack Router | |
| 数据获取 | TanStack Query | 管理所有服务端数据（API 响应缓存、加载/错误状态） |
| UI 组件 | shadcn/ui + Radix UI | |
| 样式 | Tailwind CSS | |
| 本地状态 | Zustand | 仅管理纯客户端状态 |
| API 客户端 | openapi-fetch + openapi-typescript | |
| 图标 | Lucide React | |

### 1.1 Zustand 与 TanStack Query 职责边界

- **TanStack Query**：管理所有服务端数据（API 响应缓存、加载/错误状态）
- **Zustand**：仅管理纯客户端状态（UI 偏好、侧边栏展开、主题、当前选中的命名空间过滤等）
- 禁止在 Zustand 中缓存服务端数据

## 2 页面结构

### 2.1 门户区（公开，匿名可访问）

| 页面 | 路径 | 说明 |
|------|------|------|
| 首页 | `/` | 精选/热门/最新、搜索入口 |
| 搜索页 | `/search` | 关键词搜索 + 过滤 + 排序 |
| 命名空间主页 | `/@{namespace}` | 空间介绍 + 技能列表 |
| 技能详情页 | `/@{namespace}/{slug}` | README 渲染、版本、评分、收藏、下载 |
| 版本历史 | `/@{namespace}/{slug}/versions` | 版本列表 + changelog |

门户区所有 PUBLIC 技能匿名可浏览和下载，无需登录。

### 2.2 个人中心（需登录）

| 页面 | 路径 | 说明 |
|------|------|------|
| 我的技能 | `/dashboard/skills` | 我发布的技能 + 统一生命周期状态 |
| 发布技能 | `/dashboard/publish` | zip 上传 + 预览 + 提交审核 |
| 我的收藏 | `/dashboard/stars` | 收藏列表 |
| Token 管理 | `/dashboard/tokens` | 创建/查看/吊销 |
| 我的命名空间 | `/dashboard/namespaces` | 参与的命名空间 |

### 2.3 命名空间管理（需空间 ADMIN）

| 页面 | 路径 | 说明 |
|------|------|------|
| 成员管理 | `/dashboard/namespaces/{slug}/members` | 成员管理 |
| 空间审核 | `/dashboard/namespaces/{slug}/reviews` | 待审核列表 |

### 2.4 平台管理（需对应平台角色）

| 页面 | 路径 | 所需角色 | 说明 |
|------|------|---------|------|
| 审核中心 | `/admin/reviews` | SKILL_ADMIN | 全局待审核列表 |
| 提升审核 | `/admin/promotions` | SKILL_ADMIN | 提升到全局的申请列表 |
| 技能管理 | `/admin/skills` | SKILL_ADMIN | 隐藏/恢复技能、撤回已发布版本 |
| 用户管理 | `/admin/users` | USER_ADMIN | 用户列表、角色分配、准入审批、封禁/解封 |
| 审计日志 | `/admin/audit-logs` | AUDITOR | 操作日志查询 |
| 命名空间管理 | `/admin/namespaces` | SUPER_ADMIN | 创建/归档/冻结 |

SUPER_ADMIN 可访问所有管理页面。路由守卫检查用户是否持有对应角色。

## 3 布局结构

- 门户区：顶部导航 + 内容区，无侧边栏，突出浏览体验
- Dashboard / Admin：顶部导航 + 左侧边栏，管理效率优先
- 响应式：移动端侧边栏收起为抽屉

## 3.1 生命周期展示模型

前端不再从 `status + hidden + latestVersionStatus + viewingVersionStatus` 拼装 skill 生命周期，而统一消费后端返回的 projection：

- `headlineVersion`：当前页面主展示版本
- `publishedVersion`：当前最新已发布版本
- `ownerPreviewVersion`：owner / namespace 管理者可见的待审核版本
- `resolutionMode`：`PUBLISHED` / `OWNER_PREVIEW` / `NONE`

约束：

- 详情页和“我的技能”列表统一以 `headlineVersion` 作为主展示版本
- 安装、下载、promotion 等公开分发相关操作只允许绑定 `publishedVersion`
- `hidden` 是独立治理覆盖层，不属于版本生命周期状态机

## 4 登录与鉴权

### 4.1 OAuth2 登录流程（前端视角）

```
用户点击"登录"按钮
    │
    ▼
前端调用 GET /api/v1/auth/providers
    │
    ▼
渲染可用的 OAuth Provider 按钮（一期只有 GitHub）
    │
    ▼
用户点击 "Sign in with GitHub"
    │
    ▼
window.location.href = "/oauth2/authorization/github"
    │
    ▼
（浏览器跳转到 GitHub → 授权 → 回调后端 → 后端创建 Session）
    │
    ▼
后端重定向回前端页面（如 / 或用户之前访问的页面）
    │
    ▼
前端检测到 Session Cookie，调用 GET /api/v1/auth/me
    │
    ▼
获取用户信息，渲染登录态 UI
```

前端不需要任何 OAuth 库，登录完全由后端 Spring Security 处理。前端只负责：
1. 调用 `/api/v1/auth/providers` 获取可用 Provider 列表
2. 跳转到对应的 `authorizationUrl`
3. 回调后通过 `/api/v1/auth/me` 检测登录态

### 4.2 预留的被动会话引导

为未来私有部署下的企业 SSO 兼容，前端可在登录页或应用初始化阶段显式调用：

- `POST /api/v1/auth/session/bootstrap`

该接口在开源版默认关闭；私有版启用后，前端可在检测到用户未登录时主动调用一次，以尝试将外部 SSO Cookie 换成 skillhub Session。该流程必须保持显式触发，不默认依赖全局透明拦截器。

前端兼容接入层约束如下：

- 默认不启用，运行时配置不打开时，登录页和全局行为与开源版完全一致
- 账号密码登录兼容层与被动会话兼容层相互独立，可单独启用
- 启用后，登录页会出现一个“企业 SSO”兼容入口
- 启用密码兼容层后，登录页账号密码表单会改为调用通用直连认证接口
- 前端应优先消费 `/api/v1/auth/methods` 作为统一登录方式目录；`/api/v1/auth/providers` 仅保留兼容
- 可选自动尝试，但仍限定在登录页内执行，不在全站每次匿名访问时自动探测
- bootstrap 失败时应静默回退到现有本地登录和 OAuth 登录，不打断正常流程

前端运行时配置项：

- `SKILLHUB_WEB_AUTH_DIRECT_ENABLED`
- `SKILLHUB_WEB_AUTH_DIRECT_PROVIDER`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO`

推荐策略：

- 私有版密码直连：`auth_direct_enabled=true`，`auth_direct_provider=private-sso`
- 私有版初期：`enabled=true`，`provider=private-sso`，`auto=false`
- 验证稳定后：再评估是否切到 `auto=true`

### 4.3 登录态检测

```
页面加载 → GET /api/v1/auth/me
              │
    ┌─────────┴──────────┐
    │ 200: 已登录          │ 401: 未登录
    │ 存入全局状态          │ 门户页正常展示（匿名浏览）
    │ 渲染登录态 UI         │ Dashboard/Admin 重定向到登录
    └────────────────────┘
```

- TanStack Router `beforeLoad` 做路由守卫
- Admin 路由额外检查角色
- 前端权限控制粒度详见 [03-authentication-design.md](./03-authentication-design.md) 前端权限控制粒度章节

## 5 API 集成工作流

```
后端 Springdoc → openapi.json
    → openapi-typescript 生成类型
    → openapi-fetch 创建客户端
    → TanStack Query 封装为 hooks
```

## 6 文件上传

一期 Web 端：zip 上传 → 后端解压校验 → 返回预览 → 用户确认 → 提交审核。
支持 drag-and-drop + 进度条。

## 7 关键交互

**技能详情页**：SKILL.md Markdown 渲染、右侧信息栏（版本/下载量/评分/收藏/标签/空间）、版本切换、安装命令一键复制（同时展示 skillhub CLI 格式 `install @namespace/slug` 和 ClawHub CLI 格式 `install canonical-slug`）。匿名用户可浏览和下载，收藏/评分按钮提示登录。

**搜索页**：实时搜索（debounce 300ms）、技能卡片、排序（相关度/下载量/评分/最新）、命名空间过滤。匿名用户可搜索 PUBLIC 技能。注意：一期搜索仅基于 latest 版本内容，不支持按 tag/version 搜索（详见 `04-search-architecture.md` 5.1 节）。

**审核页面**：左侧列表 + 右侧内容预览（Markdown + 文件树）、通过/拒绝 + 意见输入。
