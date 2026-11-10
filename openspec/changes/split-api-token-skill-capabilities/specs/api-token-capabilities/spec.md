## ADDED Requirements

### Requirement: REQ-ATC-01 Token 技能能力 SHALL 分为 5 类并按交集生效

系统 SHALL 提供 `skill:read`、`skill:submit`、`skill:publish-private`、`skill:manage`、`skill:delete` 五类技能能力。
Token 调用的有效权限 SHALL 为账号／空间权限、Token 有效能力、资源状态与治理规则三者的交集。授予能力 SHALL 不使普通成员
获得管理员权限，也 SHALL 不扩大可访问的资源范围。

#### Scenario: 候选提交 Token
- **WHEN** 空间 MEMBER 的 Token 只有 `skill:read + skill:submit`
- **THEN** 上传 PRIVATE 技能包成功并保持为未发布候选
- **AND** 对该候选调用 `submit-review`（`NAMESPACE_ONLY` 或 `PUBLIC`）成功，进入现有扫描与审核流程
- **AND** 调用 `confirm-publish` 返回 403 `error.apiToken.scope.missing`（参数 `skill:publish-private`），候选状态不变
- **AND** 调用 `visibility`、`archive`、`yank`、标签写入或删除接口均被拒绝，资源状态不变

#### Scenario: 私有自动发布 Token
- **WHEN** Token 有 `skill:read + skill:submit + skill:publish-private`
- **THEN** 上传 PRIVATE 后调用 `confirm-publish` 成功，版本成为 `PUBLISHED`，不生成审核通过记录

#### Scenario: 空间角色不扩大 Token 能力
- **WHEN** 空间 OWNER 使用只有 `skill:read + skill:submit` 的 Token
- **THEN** `confirm-publish` 与管理类接口仍被拒绝

### Requirement: REQ-ATC-02 每个 Token 可写入口 SHALL 有明确能力归属

`RouteSecurityPolicyRegistry` 中每条要求技能写入能力的 Token 策略 SHALL 映射到 design D2 中的唯一能力。
`/api/v1`、`/api/web`、`/api/cli/v1` 与 ClawHub 兼容入口中执行同一操作的路由 SHALL 要求相同能力。
新增的技能写入路由未归类时，目录完整性测试 SHALL 失败。

#### Scenario: 等价入口一致
- **WHEN** 同一 Token 分别调用 `POST /api/v1/skills/{ns}/{slug}/confirm-publish` 与 `POST /api/web/skills/{ns}/{slug}/confirm-publish`
- **THEN** 两者返回相同的授权结果与错误码

#### Scenario: 订阅路由不被删除通配命中
- **WHEN** `skill:read` Token 调用 `DELETE /api/v1/skills/{id}/subscription`
- **THEN** 请求按订阅路由授权，不要求 `skill:delete`

### Requirement: REQ-ATC-03 组合操作 SHALL 按实际执行的能力检查

组合操作 SHALL 对其实际执行的每个状态转换检查对应能力，任何权限不足 SHALL 在产生副作用之前拒绝。

#### Scenario: 上传改变已有技能可见性
- **WHEN** 同 slug 已有技能为 `NAMESPACE_ONLY`，只有 `skill:read + skill:submit` 的 Token 以 `PRIVATE` 上传新版本
- **THEN** 请求返回 403 `error.apiToken.scope.missing`（参数 `skill:manage`）
- **AND** 不创建 SkillVersion，技能可见性与 `latestVersionId` 不变，空间成员访问不受影响

#### Scenario: 同时具备提交与管理
- **WHEN** Token 有 `skill:submit + skill:manage`，对已有技能以不同可见性上传
- **THEN** 按现有规则处理：缩小立即生效，扩大进入审核

#### Scenario: 重新发布不能绕过私有发布
- **WHEN** 只有 `skill:submit` 的 Token 对 PRIVATE 技能执行 `rerelease`
- **THEN** 新版本落在 `UPLOADED`，不成为 `PUBLISHED`

#### Scenario: 平台管理员 Token 不走直发捷径
- **WHEN** SUPER_ADMIN 账号的 `skill:read + skill:submit` Token 以 `PRIVATE` 上传
- **THEN** 版本落在 `UPLOADED`，没有 `skill:publish-private` 时不能发布
- **AND** 同一账号的会话上传行为不变

#### Scenario: Bundle 内部私有发布
- **WHEN** Bundle 操作的发起 Token 有效能力不含 `skill:publish-private`，成员为 PRIVATE
- **THEN** 成员标记为 `BLOCKED_RETRYABLE` 并说明缺少的能力，不调用 `confirmPublish`，不创建部分 Suite 草稿

### Requirement: REQ-ATC-04 Token 签发、轮换与续期 SHALL 不超过调用 Token 的能力上限

经 Token 调用创建、同名轮换或续期 Token 时，新 Token 的有效能力 SHALL 是调用 Token 有效能力的子集，
到期时间 SHALL 不晚于调用 Token。比较 SHALL 在别名展开后进行，省略 scopes SHALL 按默认值展开后比较。
未知 scope SHALL 被拒绝且不写入。

#### Scenario: 受限 Token 签出更高权限
- **WHEN** 只有 `token:manage` 的 Token 请求创建带 `skill:delete` 的 Token
- **THEN** 返回 403 `error.apiToken.scope.escalation`，不创建 Token，同名旧 Token 不被吊销

#### Scenario: 省略 scopes 不能绕过
- **WHEN** 只有 `token:manage + skill:read` 的 Token 省略 scopes 创建 Token
- **THEN** 按默认值展开后比较，超出时返回 403

#### Scenario: 未知 scope
- **WHEN** 请求包含 `no-such:scope`
- **THEN** 返回 400 `error.apiToken.scope.unknown`

#### Scenario: 会话创建
- **WHEN** 用户通过会话登录创建任意合法组合
- **THEN** 只受账号权限约束

### Requirement: REQ-ATC-05 读取能力 SHALL 支持查询本人空间、候选与 Token 能力

`skill:read` Token SHALL 能查询本人所属命名空间及本人角色（包括空空间）、本人有权查看的未发布候选及状态，
以及当前 Token 的有效能力、兼容状态和到期时间。查询当前 Token 能力 SHALL 不需要 `token:manage`，
SHALL 不返回其他凭据或其哈希、前缀以外的标识。

#### Scenario: 空空间角色
- **WHEN** 账号是某空间 ADMIN 且该空间没有技能
- **THEN** `GET /api/v1/me/namespaces` 返回该空间及 `currentUserRole = ADMIN`

#### Scenario: 当前 Token 能力
- **WHEN** 旧版 `skill:read + skill:publish` Token 调用 `GET /api/cli/v1/auth/whoami`
- **THEN** 响应 `capabilities` 含 `skill:read`、`skill:submit`、`skill:publish-private`、`skill:manage`，`legacyCompat = true`

### Requirement: REQ-ATC-06 存量 Token 与旧 CLI SHALL 保持兼容

存量 Token 的存储 SHALL 不被改写，升级后行为 SHALL 与升级前一致并标注旧版兼容。新建入口的宽默认值 SHALL 标注弃用并有退出计划。
CLI 登录 SHALL 签发 `skill:read + skill:submit`；旧 CLI 的 publish、预检与送审 SHALL 继续可用；删除 SHALL 需要显式授权，
系统 SHALL 不通过自动换发更宽 Token 恢复删除能力。

#### Scenario: 升级后的存量 Token
- **WHEN** 升级前签发的 `skill:read + skill:publish` Token 调用 `confirm-publish`
- **THEN** 结果与升级前一致

#### Scenario: 旧 CLI 使用新登录 Token
- **WHEN** 旧版 CLI 重新登录后执行 `publish` 与送审
- **THEN** 均成功
- **AND** 执行 `remove` 时返回 `error.apiToken.scope.missing`（参数 `skill:delete`），远端技能不变

### Requirement: REQ-ATC-07 拒绝原因 SHALL 使用稳定错误码

缺少能力 SHALL 返回 `error.apiToken.scope.missing` 并以新能力名为参数；路由不对 Token 开放 SHALL 返回
`error.apiToken.endpoint.unsupported`；签发越权与未知 scope SHALL 分别使用 `error.apiToken.scope.escalation`、
`error.apiToken.scope.unknown`。调用方 SHALL 能据此判断授权，而不需要通过尝试写操作探测。

#### Scenario: 服务层拒绝沿用同一错误码
- **WHEN** 上传因缺少 `skill:manage` 在服务层被拒绝
- **THEN** 响应错误码与路由层缺少能力时相同
