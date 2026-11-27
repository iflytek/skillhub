# P1 SCIM 用户生命周期设计

## 边界

```text
DirectoryConnection (provisioning)
  -> SCIM bearer credential
  -> SCIM User resource mapper
  -> DirectoryMembershipService
  -> OrganizationMembership(source=DIRECTORY)
  -> optional later identity correlation

LoginConnection (authentication)
  -> protocol Adapter
  -> IdentityAssertion
  -> unified identity core
  -> session
```

SCIM 不调用登录 Adapter、不创建公共 OAuth binding、不读取 LoginConnection secret，也不授予 Namespace 权限。

## 标识与状态

- Directory resource key：`(directoryConnectionId, externalResourceId)`。
- `externalResourceId` 优先使用 SCIM `id`，没有时使用经校验的 `externalId`；两者都缺失拒绝创建。
- `userName`、primary email、displayName、department、employeeNumber 和 active 是可同步 profile/status 字段。
- `MembershipSourceType.DIRECTORY` 表示目录来源。初次创建为 `PROVISIONED`；`active=false` 映射为 `SUSPENDED`，DELETE 映射为 `DEPROVISIONED`。
- `OrganizationMembership.userId` 可以为空，企业登录时再由统一身份核心按 typed subject/预授权策略激活。

## HTTP 契约

Base path：`/api/v1/organizations/{organizationId}/directory-connections/{connectionId}/scim/v2`。

P1 支持：

- `/ServiceProviderConfig`
- `/ResourceTypes`
- `/Schemas`
- `/Users`
- `/Users/{id}`

Users 列表必须分页、稳定排序并限制最大 `count`；支持安全的 `externalId`/`userName` filter。POST/PUT/PATCH/DELETE 必须幂等或返回标准冲突；跨 Organization 资源不泄漏存在性。

## 安全与回滚

- secret 使用 envelope 加密和只写轮换；response、日志、审计和错误不包含 secret/token/password/raw body。
- 连接默认关闭，先测试再激活；请求受到 body、filter、分页、速率和并发限制。
- 出错先移除 allowlist、暂停 Directory Connection；保留目录事实和审计，不执行 down migration，不影响 R1-C OIDC。
