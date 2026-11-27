## Why

R1-C 已经验证了企业 OIDC 登录，但企业用户仍需手工维护 Organization membership。下一批需要先补 provisioning plane 的最小闭环，让企业目录可以通过标准 SCIM 2.0 管理用户生命周期，同时不把目录同步、登录认证和 Namespace 授权耦合在一起。

Casdoor 的开源 SCIM 实现证明了 Organization-scoped Users、分页、过滤、PATCH 和 externalId 是可复用的标准边界；SkillHub 只借鉴这些协议边界，不引入 Casdoor 运行时，也不复制其管理员 session 鉴权或内部 User 模型。

## What Changes

- 新增与 `LoginConnection` 独立的 `DirectoryConnection`，绑定一个 Organization、SCIM 2.0 协议、状态、版本化 bearer secret 和审计上下文。
- 提供组织限定的 SCIM 2.0 `/Users` 资源：ServiceProviderConfig、ResourceTypes、Schemas、list/filter、get、create、replace、patch、deactivate。
- 目录对象以 `(directoryConnectionId, externalResourceId)` 稳定定位；email 只作 profile，不作为唯一主键。
- 将用户生命周期映射为 `OrganizationMembership(source=DIRECTORY)`；允许 membership 在首次企业登录前处于 `PROVISIONED` 且没有 `userId`。
- 目录停用/去供给立即提升 authority version 并阻断企业 session，但保留 Platform Account、手工 membership、Namespace membership 和审计历史。
- 默认关闭并按 Organization/Connection allowlist 逐个启用；secret 不回显、不写日志、不进入 SCIM response。

## Impact

涉及 Organization provisioning 控制面、SCIM inbound API、OrganizationMembership source type、权限/审计、OpenAPI 类型、管理页面和实验室验证。只追加必要数据库迁移，不修改公共 OAuth、R1-C OIDC 登录协议或 CLI。

## Non-goals

- SCIM Groups、部门树、Group-to-Namespace entitlement、全量调和和增量事件。
- SAML、CAS、LDAP、飞书/钉钉/企业微信通讯录连接器。
- 邮箱域名组织发现、自动认领、账号合并、密码同步和自动角色授予。
- 生产部署、真实企业目录导入、npm 发布。
