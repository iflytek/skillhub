## Why

Issue #1049：自动化调用方需要一个“只能上传候选并提交审核”的 Token。当前 `skill:publish` 同时放行上传、送审、
PRIVATE `confirm-publish`、可见范围调整、归档、撤回和标签维护，无法只授予前两项。Token 一旦泄漏，持有人可以把
PRIVATE 候选直接发布为 `PUBLISHED`，不产生审核任务。

核对现有实现（main `929f3f1`，与 v0.2.26 镜像同一 revision）和本地干净实例时，还发现几处会突破 Token 能力上限的路径：

- 持有 `token:manage` 的 Token 可以签出带 `skill:delete` 的新 Token，自身却没有删除能力。
- SUPER_ADMIN 账号的 Token 上传 PRIVATE 时直接成为 `PUBLISHED`，不经 `confirm-publish`。
- 在已批准的 `NAMESPACE_ONLY` 技能上以 PRIVATE 上传新版本，会当场把技能改为 PRIVATE，其他空间成员立即失去访问。
- 新建 Token 接受任意 scope 字符串；`DELETE /api/v1/skills/{id}/subscription` 被删除规则通配命中，取消订阅需要 `skill:delete`。

## What Changes

- 技能写入能力拆成 `skill:submit`、`skill:publish-private`、`skill:manage`；`skill:read`、`skill:delete` 保留。
  有效权限 = 账号／空间权限 ∩ Token 能力 ∩ 资源状态和治理规则。
- 每个现有要求 `skill:publish` 的技能写接口都有明确的新归属，并由目录完整性测试保证不遗漏。
- 组合操作按实际执行的能力检查：已有技能上改变可见性的上传同时要求提交与管理；重新发布不能绕过私有发布；
  经 Token 调用时不使用 SUPER_ADMIN 直发捷径；Suite／Bundle 内部的私有发布受调用 Token 约束。
- Token 创建、轮换、续期按展开后的有效能力检查，新 Token 不得超过调用 Token；省略 scopes 和旧版别名同样适用。
  未知 scope 拒绝。
- “读取技能”新增本人所属空间及角色、当前 Token 有效能力的查询。
- 存量 `skill:publish` Token 保持升级前行为并标记为旧版兼容；新建入口的宽默认值有明确的退出计划。
- CLI 登录（设备码、浏览器回跳）改签 `skill:read + skill:submit`；旧 CLI 的 publish、送审保持可用，
  `remove` 需要显式授予删除权限。
- 保留稳定错误码 `error.apiToken.scope.missing` 与 `error.apiToken.endpoint.unsupported`。

## Capabilities

### New Capabilities

- `api-token-capabilities`：API Token 的技能能力划分、接口映射、组合操作上限、Token 签发上限、查询接口和兼容迁移。

### Modified Capabilities

无已归档 capability。`docs/03-authentication-design.md` 第 5 节的作用域说明随实现同步更新。

## Impact

- **Auth**：`RouteSecurityPolicyRegistry` 路由映射、`ApiTokenScopeService` 能力展开、`ApiTokenService`／`TokenController` 签发校验、`DeviceAuthService` 默认能力。
- **Domain／app service**：`SkillPublishService` 上传可见性与 SUPER_ADMIN 捷径、`SkillReviewSubmitService#confirmPublish`、Suite Bundle 执行与恢复。
- **API**：`/api/cli/v1/auth/whoami` 响应增加有效能力；`/api/{v1,web}/me/namespaces` 对 Token 开放；Token 创建对未知 scope 返回 400。需要重新生成 OpenAPI 类型。
- **Web**：Token 创建对话框增加预设与能力说明；Token 列表展示能力与兼容状态；`cli-auth` 改签新能力。
- **CLI**：`whoami` 展示能力；`remove` 缺少删除权限时给出明确提示。
- **兼容**：存量 Token、旧 CLI 发布与送审、会话登录行为不变。
- **Out of scope**：Suite 权限细分、Token 人工审批、多人维护（#732）、可信成员免审（#1068）、namespace 级 Token 绑定。
