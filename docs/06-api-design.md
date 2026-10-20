# skillhub API 设计

## 0. 标识类型约束

- 所有 API 中出现的用户标识一律为 `string`。
- 该约束覆盖路径参数、query 参数、请求体字段、响应 DTO 字段，以及统一响应结构中的业务数据内容。
- 任何旧草案中的整型用户标识写法都已失效，前后端正式契约只允许字符串用户标识。

## 1. 响应结构规范

除文件下载、文件内容读取这类二进制流接口外，所有 JSON API 必须统一使用以下成功响应结构：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {},
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

约束如下：

- `code`：成功时固定为 `0`；失败时固定为 HTTP 状态码，例如 `400`、`401`、`403`、`500`。
- `msg`：返回给调用方的用户可读提示文案，必须通过 Spring Boot `MessageSource` + i18n 机制生成，禁止在 controller 中硬编码。
- `msg` 的 locale 必须在响应封装层或全局异常处理层通过 `LocaleContextHolder` 从请求上下文自动获取，禁止在 controller/service 中显式传递 `Locale`。
- `data` 承载实际业务数据；列表、分页对象、详情对象、操作结果对象都必须放在 `data` 下。
- 分页响应统一使用 `{ items, total, page, size }`，禁止直接暴露 Spring `Page` 的 `content/pageable/sort/first/last` 等内部结构。
- `timestamp`：响应创建时间戳，由后端统一自动生成。
- `requestId`：请求链路 ID，由后端统一注入，便于日志追踪。
- Controller 层禁止直接返回 `Map`、裸 DTO、裸 `Page`、裸 `List` 作为 JSON 成功响应。
- 普通 JSON 接口应直接返回统一响应 DTO；仅文件下载、文件预览等需要自定义状态码或 header 的二进制接口保留 `ResponseEntity`。
- 删除、撤销、移动标签等操作也必须返回统一 JSON 结构；如无实体数据，返回 `data.message` 或 `data=null`，但外层结构不得变化。
- 错误响应与成功响应使用同一外层结构，不再使用单独的异常 JSON 结构。
- 异常链路中的 `msg` 也必须通过 Spring Boot 标准 i18n 机制生成；参数校验异常、领域异常、认证鉴权异常都必须进入统一的 `@RestControllerAdvice` 出口。
- 二进制流接口保持原始 HTTP 语义，不套 `code/data` 包装：
  - `/download`
  - `/file`
  - 其他返回 `application/octet-stream`、`application/zip` 等内容类型的接口

成功响应示例：

```json
{
  "code": 0,
  "msg": "发布成功",
  "data": {
    "skillId": 123,
    "version": "1.0.0"
  },
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

错误响应示例：

```json
{
  "code": 403,
  "msg": "需要命名空间管理员或所有者权限",
  "data": null,
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

## 7.1 Public API（匿名可访问）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/skills` | 搜索/列表（匿名仅返回 PUBLIC 技能） |
| GET | `/api/v1/skills/{namespace}/{slug}` | 技能详情（PUBLIC 匿名可访问） |
| GET | `/api/v1/skills/{namespace}/{slug}/versions` | 版本列表 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}` | 版本详情 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}/files` | 文件清单 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}/file?path=...` | 读取单个文件（query param 避免路径中 / 的解析问题） |
| GET | `/api/v1/skills/{namespace}/{slug}/download` | 按原始 latest 指针选择版本，再检查下载权限；公开分发须已发布，管理预览例外见[生命周期读模型](./14-skill-lifecycle.md#32-lifecycle-projection) |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}/download` | 下载指定版本包 |
| GET | `/api/v1/skills/{namespace}/{slug}/resolve` | 解析技能版本（支持 query param: `version`、`tag`、`hash`） |
| GET | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}/download` | 按标签下载（解析标签指向的版本后下载） |
| GET | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}/files` | 按标签查看文件清单 |
| GET | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}/file?path=...` | 按标签读取单个文件 |
| GET | `/api/v1/skills/{skillId}/reviews` | 公开评价分页列表；仅返回可见评价，管理员可见隐藏项 |
| GET | `/api/v1/namespaces` | 公开命名空间列表 |
| GET | `/api/v1/namespaces/{slug}` | 命名空间详情 |

版本列表支持 `page`（从 0 开始）、`size` 和可选 `downloadableOnly`（缺省 `false`）。`true` 仅返回已发布、下载就绪且未撤回的版本，先过滤和排序后分页，`total` 为过滤后的数量。公开列表按发布时间倒序、空时间靠后，再按创建时间和 ID 倒序稳定排序。管理者缺省读取保留生命周期分组和未发布历史。过滤不探测对象存储，实际下载仍可能因文件缺失或网络错误失败。

Public API 的可见性规则：
- `PUBLIC` 技能：若存在已发布版本，则已登录用户可访问；匿名访问仍受下载/resolve 端点的 namespace 类型限制
- `NAMESPACE_ONLY` 技能：仅该命名空间成员可访问（需登录）
- `PRIVATE` 技能：owner 本人 + 该 namespace 的 ADMIN 以上可访问（需登录）
- 若 `latest_version_id = null`，即使 `visibility=PUBLIC`，skill 也不会对外公开，只有 owner 可访问
- `hidden=true` 时，普通访客不可访问；仅 owner 或该 namespace 的 `ADMIN` / `OWNER` 可访问

`GET /api/v1/skills/{namespace}/{slug}/versions/{version}` 的 `data` 字段除版本基础信息外，还必须包含：

- `parsedMetadataJson`：`SKILL.md` frontmatter 的完整 JSON 序列化结果
- `manifestJson`：版本文件清单摘要 JSON

## 7.2 Auth API（登录与会话相关）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/oauth2/authorization/github` | 发起 GitHub OAuth 登录（Spring Security 内置） |
| GET | `/login/oauth2/code/github` | GitHub OAuth 回调（Spring Security 内置） |
| GET | `/api/v1/auth/me` | 当前用户信息（未登录返回 401） |
| POST | `/api/v1/auth/logout` | 登出（清除 Session） |
| GET | `/api/v1/auth/providers` | 可用的 OAuth Provider 列表（前端渲染登录按钮用） |
| GET | `/api/v1/auth/methods` | 统一登录方式目录（密码/OAuth/direct/bootstrap 元数据） |
| POST | `/api/v1/auth/direct/login` | 显式走直连认证 provider 的兼容登录入口（默认关闭） |
| POST | `/api/v1/auth/session/bootstrap` | 显式尝试用外部被动会话换取 skillhub Session（默认关闭） |

`/api/v1/auth/providers` 响应示例：

```json
{
  "code": 0,
  "msg": "获取成功",
  "data": [
    { "id": "github", "name": "GitHub", "authorizationUrl": "/oauth2/authorization/github" }
  ],
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

前端根据此接口动态渲染登录按钮，新增 Provider 无需改前端代码。

`/api/v1/auth/methods` 返回统一登录方式目录。典型项包括：

- `PASSWORD`：现有本地账号密码登录
- `OAUTH_REDIRECT`：OAuth 跳转登录
- `DIRECT_PASSWORD`：默认关闭的直连认证兼容入口
- `SESSION_BOOTSTRAP`：默认关闭的被动会话引导入口

示例：

```json
{
  "code": 0,
  "msg": "获取成功",
  "data": [
    {
      "id": "local-password",
      "methodType": "PASSWORD",
      "provider": "local",
      "displayName": "Local Account",
      "actionUrl": "/api/v1/auth/local/login"
    },
    {
      "id": "oauth-github",
      "methodType": "OAUTH_REDIRECT",
      "provider": "github",
      "displayName": "GitHub",
      "actionUrl": "/oauth2/authorization/github"
    }
  ],
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

`/api/v1/auth/session/bootstrap` 请求示例：

```json
{
  "provider": "private-sso"
}
```

`/api/v1/auth/session/bootstrap` 协议约束：

- 开源版默认关闭，需显式开启 `skillhub.auth.session-bootstrap.enabled=true`
- 关闭时返回 `403`
- provider 不存在时返回 `400`
- 外部会话不存在或校验失败时返回 `401`
- 成功时返回与 `/api/v1/auth/me` 相同的用户结构，并建立标准 Session

`/api/v1/auth/direct/login` 请求示例：

```json
{
  "provider": "private-sso",
  "username": "alice",
  "password": "secret"
}
```

`/api/v1/auth/direct/login` 协议约束：

- 开源版默认关闭，需显式开启 `skillhub.auth.direct.enabled=true`
- 关闭时返回 `403`
- provider 不存在时返回 `400`
- 成功时返回与 `/api/v1/auth/me` 相同的用户结构，并建立标准 Session
- `/api/v1/auth/local/login` 继续保留，作为现有本地账号入口

## 7.3 Authenticated API（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/v1/skills/{skillId}/star` | 收藏 |
| GET | `/api/v1/skills/{skillId}/star` | 当前用户收藏状态 |
| DELETE | `/api/v1/skills/{skillId}/star` | 取消本人收藏（幂等） |
| PUT | `/api/v1/skills/{skillId}/rating` | 评分 |
| GET | `/api/v1/skills/{skillId}/rating` | 当前用户评分状态 |
| GET | `/api/v1/skills/{skillId}/reviews/me` | 当前用户的评分与文字评价 |
| PUT | `/api/v1/skills/{skillId}/reviews/me` | 新增或更新当前用户评价（`score` 1-5，`reviewText` 最长 2000） |
| DELETE | `/api/v1/skills/{skillId}/reviews/me` | 删除文字评价并保留星级评分 |
| GET | `/api/v1/me/stars` | 我的收藏列表 |
| GET | `/api/v1/me/skills` | 我发布的技能列表 |

收藏与评分同样支持 `/api/web` 路由。上述 `GET` / `PUT star`、`GET` / `PUT rating`
在读取状态或写入前，按技能详情的对象可见性策略检查当前用户、空间角色和平台角色。
存在但不可访问的技能返回 `403`，不存在返回 `404`；拒绝不会新增收藏、评分或统计事件。
合法的所有者、空间管理员及超级管理员仍按现有访问策略处理，不统一禁止私有技能互动。

`GET /me/stars` 在数据库计数、排序与分页之前过滤当前无权访问的技能。
技能转私有、被隐藏或用户失去空间角色后，旧收藏不能继续返回技能名称、摘要等信息。
收藏关系仍保留，恢复访问后可再次显示；不因当前不可访问而自动删除历史收藏或评分。
`DELETE star` 只操作当前用户自己的收藏，可在失去访问权限后继续幂等清理，不返回目标技能信息。
本次不改变归档技能和未发布技能的既有可见性规则，也不增加数据库业务约束。

### 已上传版本与审核提交

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/skills/{namespace}/{slug}/versions/{version}/withdraw-review` | 撤回提审（PENDING_REVIEW → UPLOADED，同时删除关联的 PENDING review_task） |
| POST | `/api/v1/reviews` | 提交指定 `skillVersionId` 进入审核队列 |
| POST | `/api/v1/skills/{namespace}/{slug}/submit-review` | 提交 `UPLOADED` 或兼容 `DRAFT` 版本；body 为 `version`、`targetVisibility`（`PUBLIC` / `NAMESPACE_ONLY`） |
| POST | `/api/v1/skills/{namespace}/{slug}/confirm-publish` | 确认发布 PRIVATE 技能的 `UPLOADED` 或兼容 `DRAFT` 版本；body 为 `version` |

### 标签管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/skills/{namespace}/{slug}/tags` | 列出标签 |
| PUT | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}` | 创建/移动自定义标签（`latest` 为系统保留标签，不可通过此接口操作） |
| DELETE | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}` | 删除自定义标签（`latest` 不可删） |

### 技能可见范围

已发布技能可独立调整范围，不上传新 ZIP、不创建内容版本。`GET/POST /api/v1/skills/{namespace}/{slug}/visibility`（Web 同时支持 `/api/web`）提供当前范围及申请提交；Token 写入需 `skill:publish`，仍需维护权限。扩大范围走统一技能审核，缩小立即生效。请求、响应、扫描和冲突说明见[可见范围使用指南](./skill-visibility.md)。

### 技能生命周期管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/skills/{namespace}/{slug}/archive` | 归档技能（namespace ADMIN 或 owner） |
| POST | `/api/v1/skills/{namespace}/{slug}/unarchive` | 恢复归档（namespace ADMIN 或 owner） |
| DELETE | `/api/v1/skills/{namespace}/{slug}/versions/{version}` | 删除 DRAFT/UPLOADED/REJECTED/SCAN_FAILED 版本；不能删除最后一个版本 |
| POST | `/api/v1/skills/{namespace}/{slug}/versions/{version}/yank` | 撤回已发布版本（namespace ADMIN 或 owner；可选 body `{"reason"}`；API Token 需 `skill:publish`） |
| POST | `/api/v1/skills/{namespace}/{slug}/versions/{version}/rerelease` | 从已发布版本重新发出一个新版本（namespace ADMIN 或 owner） |

当前代码中的 skill 生命周期读模型不再依赖 `latestVersionStatus` / `viewingVersionStatus` 一类拼装字段，而统一使用以下 projection：

- `headlineVersion`：projection 的默认主展示候选；前端可按选中版本或管理预览选择展示内容，不保证每个页面都直接显示该字段
- `publishedVersion`：当前最新已发布版本；能否安装仍需通过可见性与安全门禁
- `ownerPreviewVersion`：owner / namespace 管理者可见、比当前已发布版本更新的未发布版本（排除 `YANKED`）；无已发布版本时取最新候选
- `resolutionMode`：`PUBLISHED` / `OWNER_PREVIEW` / `NONE`

其中：

- 默认公开版本展示、安装与搜索以最新已发布版本为候选；显式版本和自定义标签可选择其他 `PUBLISHED` 版本，仍须通过对应入口的授权与就绪检查
- owner 详情页在没有可展示发布版本时，才允许 `headlineVersion` 落到 `ownerPreviewVersion`
- 提升到全局可选择属于来源 skill 的任一 `PUBLISHED` 版本，包括历史版本；不要求等于 `publishedVersion.id`
- `hidden` 是独立治理覆盖层，不属于生命周期状态机
- 常规版本详情与文件预览在技能可见性校验后，允许 owner / namespace `ADMIN` / `OWNER` 查看非发布版本；这不授予安装或下载权限。显式下载另对 `UPLOADED` / `PENDING_REVIEW` 放行有权管理的用户，其他未发布状态不放行。完整边界见 [生命周期文档](./14-skill-lifecycle.md)。

发布成功响应中的 `data` 至少包含以下字段：

- `skillId`
- `namespace`
- `slug`
- `version`
- `status`
- `fileCount`
- `totalSize`

发布状态约束：

- 普通用户上传 PRIVATE 技能：启用扫描时先进入 `SCANNING`，扫描完成后进入 `UPLOADED`；关闭扫描时直接进入 `UPLOADED`。
- 普通用户上传 PUBLIC / NAMESPACE_ONLY 技能必须启用扫描，先进入 `SCANNING`，扫描完成后进入 `PENDING_REVIEW`。扫描执行失败与扫描发现风险是不同结果，不能把进入待审等同于通过安全验收。
- `SUPER_ADMIN` 可免人工审核、免 namespace 成员校验而直接创建 `PUBLISHED` 版本，但不绕过 PUBLIC / NAMESPACE_ONLY 的扫描启用要求，也不表示扫描和安装门禁已通过。
- 撤回审核保留版本记录，将 `PENDING_REVIEW → UPLOADED`；`DRAFT` 是兼容状态，不是当前撤回目标。
- 确认私有发布、提交审核、扫描重试及安装门禁见 [生命周期文档](./14-skill-lifecycle.md)，不要只根据发布响应的 `status` 判断可安装性。

## 7.4 Token API（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/tokens` | 创建 API Token |
| GET | `/api/v1/tokens` | 列出我的 Token |
| DELETE | `/api/v1/tokens/{id}` | 吊销 Token |

## 7.5 CLI API（Bearer Token 认证，CLI 主流程由 Device Flow 获取凭证）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/whoami` | 当前 Bearer Token 对应的用户信息 |
| POST | `/api/v1/publish` | 上传技能包（结果受可见性、扫描和发布权限影响，见发布状态约束） |
| GET | `/api/v1/resolve/{namespace}/{slug}` | 解析版本 |
| GET | `/api/v1/check/{namespace}/{slug}/{version}` | 本地哈希与远端比对 |

### ClawHub CLI 协议兼容层

一期不仅提供 skillhub 自有 CLI API，还必须暴露一组兼容 ClawHub CLI 的 registry API。

- 目标：让现有 ClawHub CLI 可通过配置 registry base URL 直接对接 skillhub
- 范围：一期聚焦覆盖 ClawHub CLI 所依赖的核心接口：查询、版本解析、下载、发布、whoami
- 要求：兼容层优先保持 ClawHub CLI 既有请求/响应语义；若内部领域模型不同，通过 adapter 层完成协议转换，而不是要求客户端适配 skillhub 私有协议
- 要求：兼容层纳入 OpenAPI 或独立兼容协议文档，并作为正式对外契约维护
- 要求：兼容层与 skillhub 自有 `/api/v1/**` 并存，二者共享同一套权限、审计、限流与领域服务
- 非目标：前端页面不直接依赖兼容层；兼容层用于服务已有 ClawHub CLI 和相关自动化脚本

兼容层最少需要覆盖的能力类别：

- Registry metadata：技能查询、技能详情、版本列表、标签/默认版本解析
- Artifact resolution：按技能坐标或版本解析下载地址/下载流
- Publish workflow：包上传与发布结果返回
- Integrity check：版本存在性校验、摘要/哈希比对、whoami/token 上下文确认

如 ClawHub CLI 的现有协议与 skillhub 自有接口存在差异，文档以“兼容 ClawHub CLI 协议”为准，skillhub 内部 API 可继续保持当前风格。

## 7.6 Admin API（需对应平台角色）

Admin API 按最小权限拆分，不再统一要求 SUPER_ADMIN：

### 平台治理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/promotions` | 待审核提升申请列表（需 `SKILL_ADMIN` / `SUPER_ADMIN`；路由不在 `/admin/*` 下） |
| GET | `/api/v1/promotions/{id}` | 提升申请详情（提交人本人或 `SKILL_ADMIN` / `SUPER_ADMIN` 可读） |
| GET | `/api/v1/promotions/source/{sourceSkillId}/state` | 来源团队技能的提升状态和关联全局版本（服务端校验来源管理权限） |
| POST | `/api/v1/promotions/{id}/approve` | 通过提升申请（需 `SKILL_ADMIN` / `SUPER_ADMIN`） |
| POST | `/api/v1/promotions/{id}/reject` | 拒绝提升申请（需 `SKILL_ADMIN` / `SUPER_ADMIN`） |
| GET | `/api/v1/promotion-revocations/pending` | 待审撤销申请（需平台技能管理员） |
| GET | `/api/v1/promotion-revocations/history?page=0&size=20` | 已审核撤销申请历史，服务端分页（需平台技能管理员） |
| GET | `/api/v1/promotion-revocations/{id}` | 撤销申请详情（提交人或平台技能管理员） |
| GET | `/api/v1/promotion-revocations/source/{sourceSkillId}/history` | 来源技能的撤销申请历史（按来源权限读取） |
| POST | `/api/v1/promotion-revocations/{id}/approve` | 审核通过撤销并删除全局派生技能 |
| POST | `/api/v1/promotion-revocations/{id}/reject` | 拒绝撤销申请 |
| POST | `/api/v1/promotion-revocations/source/{sourceSkillId}/direct` | 平台技能管理员直接撤销，仍写申请及审计 |
| POST | `/api/v1/admin/skills/{id}/hide` | 隐藏技能（仅 `SUPER_ADMIN`） |
| POST | `/api/v1/admin/skills/{id}/unhide` | 恢复技能（仅 `SUPER_ADMIN`） |
| POST | `/api/v1/admin/skills/versions/{versionId}/yank` | 撤回已发布版本（`SKILL_ADMIN` / `SUPER_ADMIN`） |
| POST | `/api/v1/admin/skill-reviews/{reviewId}/hide` | 隐藏用户评价（`SKILL_ADMIN` / `SUPER_ADMIN`） |
| POST | `/api/v1/admin/skill-reviews/{reviewId}/restore` | 恢复用户评价（`SKILL_ADMIN` / `SUPER_ADMIN`） |

### 用户治理（需 USER_ADMIN / SUPER_ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/users` | 用户列表 |
| GET | `/api/v1/admin/users/{id}` | 用户详情 |
| PUT | `/api/v1/admin/users/{id}/role` | 修改用户角色（USER_ADMIN 不可分配 SUPER_ADMIN，也不可修改已有 SUPER_ADMIN 的角色状态） |
| POST | `/api/v1/admin/users/{id}/approve` | 审批待准入用户 |
| POST | `/api/v1/admin/users/{id}/disable` | 封禁用户 |
| POST | `/api/v1/admin/users/{id}/enable` | 解封用户 |

### 审计（需 AUDITOR / SUPER_ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/audit-logs` | 审计日志查询 |

## 7.7 Namespace 管理 API（需命名空间 OWNER 或 ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/namespaces` | 创建命名空间 |
| PUT | `/api/v1/namespaces/{slug}` | 更新命名空间信息 |
| GET | `/api/v1/namespaces/{slug}/members` | 成员列表 |
| POST | `/api/v1/namespaces/{slug}/members` | 添加成员 |
| PUT | `/api/v1/namespaces/{slug}/members/{userId}/role` | 修改成员角色 |
| DELETE | `/api/v1/namespaces/{slug}/members/{userId}` | 移除成员 |
| GET | `/api/v1/reviews?namespaceId={id}` | 该空间待审核列表 |
| POST | `/api/v1/reviews/{id}/approve` | 空间管理员审核通过 |
| POST | `/api/v1/reviews/{id}/reject` | 空间管理员审核拒绝 |
| POST | `/api/v1/promotions` | 首次提升或再次提交已发布团队版本；后者自动关联原全局技能，均需平台审核 |
| POST | `/api/v1/promotion-revocations` | 来源 owner 或 namespace 管理员申请撤销提升 |

## 7.8 `latest` 语义说明

`latest` 自动跟随最新已发布版本，不可手动移动。

- 对外 `latest` 以 `publishedVersion` 表示的最新已发布版本为准；`skill.latest_version_id` 是内部指针，PRIVATE 上传流程可能指向未发布版本，不能单凭该指针判断已发布或可安装。
- `yank` 当前最新已发布版本时，需要同步重算 `latest_version_id` 指向下一个最新的 `PUBLISHED` 版本；若不存在则允许为 `null`
- `latest` 标签是系统保留、API 不可手动移动的标签。当前 `SkillTagService.listTags` 通过读取授权后，直接按原始 `latest_version_id` 合成该标签，不检查目标版本状态；PRIVATE 未发布指针也可能出现在授权用户的标签列表中。这是列表现状，不代表该版本可以公开下载，解析与下载仍须执行各自门禁。
- 自定义标签（如 `beta`、`stable-2026q1`）：允许人工创建和移动，用于固定安装通道

| 场景 | 使用字段 | 说明 |
|------|---------|------|
| 搜索索引内容 | `publishedVersion` | 外部协议仍叫 latest，但内部语义必须等价于最新已发布版本 |
| `/download`（不带版本号） | `latest_version_id`，再做下载权限和版本检查 | 公开分发只允许可安装的发布版本；管理者受限下载与公开分发分开判断 |
| CLI `install @team/skill` | `publishedVersion` | 等同于 `@latest` |
| CLI `install @team/skill@beta` | `skill_tag` 查询 | 自定义标签指向的版本 |

## 7.9 Resolve 接口说明

`GET /api/v1/skills/{namespace}/{slug}/resolve` 用于解析技能版本，支持以下 query param：

| 参数 | 类型 | 说明 |
|------|------|------|
| `version` | string | 精确版本号（如 `1.2.0`） |
| `tag` | string | 标签名（如 `beta`、`latest`） |
| `hash` | string | fingerprint 哈希，用于判断本地版本是否与 registry 同步 |

解析优先级：
1. `version` 和 `tag` 不可同时传，同时传返回 `400 Bad Request`
2. 仅传 `version`：精确匹配版本号
3. 仅传 `tag`：查询 `skill_tag` 表获取 `target_version_id`
4. 仅传 `hash`：遍历已发布版本，比对 fingerprint
5. 均不传：返回最新已发布版本；实现上可由 `latest_version_id` 或等价 published projection 解析

响应：

```json
{
  "code": 0,
  "msg": "获取成功",
  "data": {
    "skillId": 456,
    "namespace": "team-name",
    "slug": "my-skill",
    "version": "1.2.0",
    "versionId": 123,
    "fingerprint": "sha256:abc123...",
    "downloadUrl": "/api/v1/skills/team-name/my-skill/versions/1.2.0/download"
  },
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

`hash` 匹配时额外返回 `"matched": true`，不匹配时返回最新版本信息 + `"matched": false`。

## 7.10 ClawHub CLI 兼容层 API

兼容层 API 基地址为 `/api/v1`，通过 `/.well-known/clawhub.json` 发现。兼容层使用 canonical slug（双连字符映射规则，详见 `00-product-direction.md` 1.1 节）。

认证方式：`Authorization: Bearer <token>`。Bearer Token 可来自 CLI Device Flow 或平台 API Token。

### Well-known 发现

```
GET /.well-known/clawhub.json

响应：
{
  "apiBase": "/api/v1"
}
```

### 兼容层端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/whoami` | 当前用户信息 |
| GET | `/api/v1/search` | 搜索技能 |
| GET | `/api/v1/resolve` | 通过 slug + version 解析版本 |
| GET | `/api/v1/download/{slug}/{version}` | 下载技能 zip 包 |
| POST | `/api/v1/publish` | 上传技能（multipart/form-data；状态与安装门禁见发布状态约束） |

### 兼容层请求/响应格式

**GET `/api/v1/whoami`**

```json
{
  "handle": "username",
  "displayName": "User Name",
  "role": "user"
}
```

**GET `/api/v1/search?q={keyword}&page={page}&limit={limit}`**

```json
{
  "results": [
    {
      "slug": "my-skill",
      "name": "My Skill",
      "description": "...",
      "author": { "handle": "username", "displayName": "User Name" },
      "version": "1.2.0",
      "downloadCount": 100,
      "starCount": 50,
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-03-01T00:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "limit": 20
}
```

注意：兼容层返回的 `slug` 为 canonical slug 格式（全局空间直接返回 skill slug，团队空间返回 `namespace--skill`）。

**GET `/api/v1/resolve?slug={slug}&version={version}`**

```json
{
  "slug": "my-skill",
  "version": "1.2.0",
  "downloadUrl": "/api/v1/download/my-skill/1.2.0"
}
```

**GET `/api/v1/download/{slug}/{version}`**

返回指定版本的 zip 文件流。默认版本解析由 `resolve` 接口负责。

**POST `/api/v1/publish`**

```
Content-Type: multipart/form-data
Parts:
  - file: zip 包
```

一期同步响应，返回发布结果：

```json
{
  "slug": "my-skill",
  "version": "1.0.0",
  "status": "published"
}
```

### 兼容层适配说明

- 兼容层是独立的 Controller 层，内部调用与 native API 相同的领域服务
- 请求进入时将 canonical slug 转换为 `(namespace_id, skill_slug)` 坐标
- 响应返回时将内部坐标转换为 canonical slug
- 兼容层不暴露 namespace 概念，对 ClawHub CLI 透明
- 发布时如果 canonical slug 包含 `--`，解析为团队空间发布；否则发布到全局空间
- 兼容层的认证复用 skillhub Bearer Token 体系，ClawHub CLI 通过登录或配置 token 后即可使用

## 7.11 Rate Limiting

分两阶段实施：

### Phase 1：Ingress 层基础限流

通过 Nginx Ingress `limit-req` 按 IP 全局限流，覆盖认证、搜索、下载等匿名可访问接口，防止基本的滥用和爬虫。

### Phase 2：应用层精细限流

基于 Redis 滑动窗口，按用户/端点分类的精细限流。

| 端点类别 | 限流策略 |
|---------|---------|
| 搜索 API | 已登录 60 次/分钟，匿名 20 次/分钟（按 IP） |
| 下载 API | 已登录 120 次/分钟，匿名 30 次/分钟（按 IP） |
| 发布 API | 10 次/小时（按用户） |
| 认证 API | 30 次/分钟（按 IP） |

触发限流时返回 `429 Too Many Requests` + `Retry-After` Header。

## 7.12 API 设计原则

### Native API（`/api/v1/*`）

- 统一响应包裹：`{ code, message, data, timestamp }`
- 分页格式：`{ items, total, page, size }`
- 错误码体系：业务错误码 + HTTP 状态码配合
- 版本策略：URL path 版本 `/api/v1/`
- 幂等性：写操作通过 `X-Request-Id` + Redis 去重（TTL 24h）

### Compatibility API（`/api/v1/*`）

- 响应格式完全遵循 ClawHub 协议，不套统一响应包裹
- 错误响应遵循 ClawHub 格式：`{ error: string, message: string }`
- 分页格式遵循 ClawHub 格式：`{ results, total, page, limit }`

### OpenAPI 文档分离

生成两份独立的 OpenAPI spec：
- `openapi-native.json`：skillhub Native API，用于前端 SDK 生成
- `openapi-compat-clawhub.json`：ClawHub 兼容层 API，用于兼容性测试和文档
