## Context

现有 API Token 授权分两层：

1. `RouteSecurityPolicyRegistry.API_TOKEN_POLICIES` 按“方法 + Ant 路径”首条匹配，给出放行、所需 scope 或不支持；
   `ApiTokenScopeFilter` 在过滤器阶段执行，缺 scope 返回 `error.apiToken.scope.missing`，未列出返回
   `error.apiToken.endpoint.unsupported`。
2. 业务服务只看账号身份（owner、namespace 角色、平台角色），不知道请求来自 Token，也不知道 Token 的 scope。

所以 Token 的能力上限只在路由层生效。内部调用（Bundle 成员执行、恢复任务）以及身份短路（SUPER_ADMIN 直发）
都不经过路由层。本变更在保持路由层为主入口的同时，在少数跨入口的状态转换上补服务层校验。

### Ubiquitous language

| Term | Definition |
| --- | --- |
| 能力（capability） | Token 授予的一类技能操作：读取、提交、私有发布、管理、删除。 |
| 有效能力 | 存储的 scope 列表按别名规则展开后的能力集合。 |
| 旧版兼容 | 存储 scope 中含 `skill:publish`；按旧行为展开，在界面和 whoami 上标注。 |
| Token 调用 | 以 Bearer API Token 认证的请求；会话登录不属于 Token 调用。 |
| 能力上限 | Token 调用可以执行的操作集合，不因账号是平台管理员、空间 OWNER 或内部编排而扩大。 |

## Goals / Non-Goals

**Goals:**

- 让“读取 + 提交”的 Token 能完成上传、预检、送审、回读候选状态，且不能私有发布、管理或删除。
- 每个现有 Token 可写入口都有明确能力归属，新增入口未归类时测试失败。
- 受限 Token 不能经签发、轮换、续期、内部编排或身份短路取得更高能力。
- 存量 Token 保持原有 scope 的路由授权与存储格式；旧 CLI 的发布、预检与送审继续可用。
  SUPER_ADMIN Token 直发与 CLI 重新登录后的删除行为是明确例外（见 D3／D6／D7）。

**Non-Goals:**

- Suite／Bundle 的细分权限；本期 Suite 写入继续只认旧 `skill:publish`。
- namespace 级 Token 绑定、Token 人工审批、多人维护（#732）、可信成员免审（#1068）。
- 修改会话登录的权限模型或空间角色语义。

## Decisions

### D1. scope 命名与展开

| 能力 | scope | 新建入口 |
| --- | --- | --- |
| 读取技能 | `skill:read` | 可选 |
| 提交技能 | `skill:submit` | 可选 |
| 发布私有技能 | `skill:publish-private` | 可选 |
| 管理技能 | `skill:manage` | 可选 |
| 删除技能 | `skill:delete` | 可选，不默认勾选 |
| Token 管理 | `token:manage` | 可选，不默认勾选 |
| 旧版发布 | `skill:publish` | 仅兼容，见 D6 |

`ApiTokenScopeService` 负责展开：`skill:publish` → `skill:submit + skill:publish-private + skill:manage + suite:legacy-write`。
`suite:legacy-write` 是内部标记，不对外签发，Suite 写入路由要求它。路由策略只引用展开后的能力名。

备选：在新 scope 下继续让 `skill:publish` 表示“提交”。放弃原因：存量 Token 的语义会在升级时被静默缩小，
自动化会在无提示的情况下失败，与“存量 Token 保持升级前行为”冲突。

### D2. 接口映射

v1／web 成对路由一并处理。

| 能力 | 路由 |
| --- | --- |
| `skill:submit` | `POST /api/cli/v1/skills/*/publish`、`/publish/validate`；`POST /api/{v1,web}/skills/*/publish`、`/package-preview`、`/*/*/submit-review`、`/*/*/versions/*/rerelease`；ClawHub 兼容 `POST /api/v1/skills`、`POST /api/v1/publish` |
| `skill:publish-private` | `POST /api/{v1,web}/skills/*/*/confirm-publish` |
| `skill:manage` | `POST /api/{v1,web}/skills/*/*/visibility`、`/archive`、`/unarchive`、`/versions/*/yank`、`/versions/*/withdraw-review`；`PUT／DELETE /api/{v1,web}/skills/*/*/labels/*`；`POST /api/v1/skills/*/versions/*/security-audit/retry` |
| `skill:delete` | 现有 4 组删除路由，不变 |
| `skill:read` | 现有 GET 放行规则不变；新增 `GET /api/{v1,web}/me/namespaces`、`/me/namespaces/page` |
| `suite:legacy-write` | 现有全部 Suite 与 suite-bundles 写路由 |

订阅路由显式列出，排在删除通配规则之前：`PUT／DELETE /api/{v1,web}/skills/*/subscription` 要求 `skill:read`。
理由：订阅只影响调用者自己的通知，与删除技能无关；现在 DELETE 被 `/api/v1/skills/*/*` 删除规则误命中。

### D3. 组合操作上限

服务层在以下转换前校验 Token 有效能力；会话调用不受影响。实现方式：认证过滤器把 Token 有效能力写入请求上下文
（`PlatformPrincipal` 增加只读的 `tokenCapabilities`，会话为空表示不受限），AppService 层显式传入领域服务。

1. **改变已有技能可见性的上传**：同 slug 已有技能且请求可见性与当前不同，要求 `skill:submit + skill:manage`。
   缺少时在创建版本、修改 `skill.visibility` 或 `latestVersionId` 之前拒绝，返回 `error.apiToken.scope.missing`（参数 `skill:manage`），
   原状态不变。新技能首次上传不受此限。
2. **重新发布**：只要求 `skill:submit`；PRIVATE 技能 rerelease 落在 `UPLOADED`，发布仍需 `skill:publish-private`。
3. **SUPER_ADMIN 直发捷径**：所有 Token 调用（含含有旧 `skill:publish` 的存量 Token）均不再因 `isSuperAdmin` 自动发布；
   上传 PRIVATE 后落在 `UPLOADED`，需显式调用 `confirm-publish`，并持有 `skill:publish-private`。因此存量 SUPER_ADMIN Token
   若原本依赖上传即发布，升级后需要多调用一次；其余存量 Token 的路由能力按 D6 保留。只有 `skill:read + skill:submit`
   的新 Token 不能借 SUPER_ADMIN 身份直发，也不能调用 `confirm-publish`。会话调用仍按原有 SUPER_ADMIN 直发流程，
   成员校验短路保持不变。
4. **Suite／Bundle 内部私有发布**：Bundle 确认时把发起方式（Token／会话）与 Token 有效能力快照不可变地保存在操作记录；
   新建的 Token 操作必须保存非空快照，缺失则在落库前拒绝；会话操作显式记录为会话，能力快照可空。
   对 PRIVATE 成员调用 `confirmPublish` 前按操作记录校验 `skill:publish-private`；缺少时将成员／操作标为
   `BLOCKED_RETRYABLE` 并注明能力缺失，不调用发布，也不创建部分 Suite 草稿。对快照缺失且来源无法证实的
   历史操作也保持阻塞并注明“授权来源不明”。恢复执行沿用持久化的发起方式与
   快照，不能按恢复 worker 的会话身份或当前重试者的 Token 重新授权；失败重试只重试原操作，不得补入更宽能力。
   迁移前创建、且能以原操作 ID 关联的持久记录／审计记录证明发起方式与当时的授权结果的历史操作，才可
   沿用原授权语义；确认原先是带 `skill:publish` 的 Token 时按 D1 展开，确认原先是会话时沿用会话权限。
   实施前先盘点现有记录是否真的有这样的关联证据；不能用当前 actor 角色、当前 Token、创建时间或快照为空
   反推历史权限。无法证明来源和旧权限的空快照操作保持 `BLOCKED_RETRYABLE`，须重新以明确授权的请求创建
   新操作，不能在原操作上通过重试升级权限。由于 Bundle 路由要求 `suite:legacy-write`，新预设 Token 进不到这条路径；
   此校验仍防止未来开放 Suite 路由时产生内部绕过。
5. **平台角色与空间角色**：不扩大 Token 能力。空间 OWNER 的“读取 + 提交”Token 仍不能 `confirm-publish`。

### D4. Token 签发上限

`POST /api/v1/tokens`、轮换（同名创建）、`PUT /api/v1/tokens/{id}/expiration`：

- 未知 scope 返回 400 `error.apiToken.scope.unknown`，不写入。
- Token 调用时，新 Token 的有效能力必须是调用 Token 有效能力的子集（含 `token:manage` 本身），否则 403
  `error.apiToken.scope.escalation`；比较在展开后进行，省略 scopes 时按 D6 的默认值展开后比较。
- 续期不得晚于调用 Token 自身的到期时间。
- 会话调用只受账号权限约束，可创建任意合法组合。
- 设备码兑换（`/api/v1/auth/device/token`）只签发 D7 的固定能力，不接受调用方传入 scope。

### D5. 查询接口

- `GET /api/cli/v1/auth/whoami` 增加 `capabilities`（展开后的有效能力，排序后的字符串数组）、`legacyCompat`（布尔）、
  `expiresAt`。会话调用返回 `capabilities: null`。不放在 `/api/v1/tokens/**` 下，因此不需要 `token:manage`，
  也不返回其他凭据。
- `GET /api/{v1,web}/me/namespaces` 对 `skill:read` Token 开放，返回现有 `MyNamespaceResponse`（含 `currentUserRole`），
  包括没有技能的空间。
- 自有候选状态沿用 `GET .../versions`：owner 与空间 ADMIN/OWNER 已能看到 `UPLOADED／SCANNING／PENDING_REVIEW／REJECTED`。

### D6. 存量 Token 与默认值

- 存储的 `scope_json` 不迁移、不改写。含 `skill:publish` 的 Token 按 D1 展开，`legacyCompat = true`。
- 新建时显式传 `skill:publish` 被接受并标为旧版兼容，供旧脚本过渡；Web 创建对话框不再提供该选项。
- 省略 scopes 时，本期仍签发 `skill:read + skill:publish + skill:delete`，响应带 `deprecatedDefault: true`，文档写明弃用。
  退出计划：下一个次版本把省略 scopes 改为只签 `skill:read`，发布说明提前一个版本预告。
- Web 创建对话框提供预设：只读下载（read）、候选提交（read + submit）、私有发布（read + submit + publish-private）、自定义。
  删除与 Token 管理只在自定义中显式勾选。

### D7. CLI 兼容

- 设备码与浏览器回跳两条登录流程改签 `skill:read + skill:submit`。
- 旧 CLI 只调用读取、`publish`、`publish/validate`、`submit-review` 与 `remove`（删除）；前四项在新能力下照常可用。
- `remove` 缺少删除能力时返回 `error.apiToken.scope.missing`（参数 `skill:delete`）。新版 CLI 识别后提示用户在 Web 创建
  带删除能力的 Token 并 `skillhub login --token` 使用；不自动换发更宽的 Token。
- 已登录的旧 CLI 持有的存量 Token 按 D6 展开，`remove` 继续可用，直到用户重新登录。

## Risks / Trade-offs

- **旧脚本依赖省略 scopes 的宽默认值**：本期不改默认，只标弃用，下一个次版本再收紧。
- **重新登录后 `remove` 不可用**：这是有意的显式授权；CLI 给出明确提示与下一步。
- **服务层校验扩散**：只在 D3 列出的四处转换补校验，不把 Token 能力传遍所有服务，避免与路由层重复。
- **存量 Token 兼容边界**：`scope_json` 不改写，原 scope 的路由授权保留；但 SUPER_ADMIN 的 Token 上传 PRIVATE
  不再自动成为 `PUBLISHED`，旧 `skill:publish` Token 可显式 `confirm-publish`，新 `skill:read + skill:submit`
  Token 不可。会话上传仍按原有 SUPER_ADMIN 直发行为。见 D3 与 REQ-ATC-06。
- **Bundle 能力快照**：确认时持久化发起方式与 Token 能力；新 Token 操作缺快照不得落库。历史空快照仅在
  可按操作 ID 关联到当时发起方式和授权结果的证据时按旧语义处理；无证据则保持阻塞并重新创建明确授权的操作。
  恢复和重试都不从当前执行者继承更宽权限。实施前盘点现有记录能否提供此类历史证据。

## Migration Plan

1. 发布兼容服务端：识别新 scope、展开旧 scope、目录完整性测试、D3／D4 校验；Web 和 CLI 仍签发旧 scope。
   部署前盘点待恢复的 Bundle 操作与可关联的历史授权证据；无法证明来源的空快照操作阻塞并要求重新确认，
   不以迁移时间或当前 actor 权限给它们补全能力。提前告知依赖 SUPER_ADMIN Token 上传即直发的调用方需改为显式确认。
2. 同一版本切换 Web 创建对话框与 `cli-auth`、设备码到新能力，发布 CLI 新版本。
3. 下一个次版本：省略 scopes 改为只读。

回滚：服务端回退后，新 scope Token 的写入请求会因路由不认识新 scope 而返回 `scope.missing`（安全失败）；
存量 Token 不受影响。Flyway 只新增 Bundle 操作的能力快照列，不需要回退。

## Design disposition

沿用现有可见性规则：具备 `skill:submit + skill:manage` 只满足执行门槛；缩小可见范围立即生效，扩大仍进入扫描／审核，其他资源状态与治理规则不变。
