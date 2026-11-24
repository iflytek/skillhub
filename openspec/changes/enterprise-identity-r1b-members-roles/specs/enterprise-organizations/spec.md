## ADDED Requirements

### Requirement: Initial owner can delegate organization administration

首位 `ORG_OWNER` SHALL 可在本 Organization 内添加已有可用 Platform Account 并管理角色绑定。`MEMBER_ADMIN` SHALL 可管理成员但不得授予或撤销组织角色。角色委派 SHALL 要求目标 ACTIVE Membership；自我授权与授权他人适用相同授权、审计和撤权规则。

#### Scenario: Owner adds and delegates

- **WHEN** 仅持有 `ORG_OWNER` 的用户添加已有可用账号为本组织成员，并显式向该成员授予 `MEMBER_ADMIN`
- **THEN** 成员具有 ACTIVE Membership 与显式角色，操作均可审计；owner 本身没有因该操作获得连接或 Secret 权限

#### Scenario: Owner grants self a sensitive role

- **WHEN** owner 显式向自己授予 `IDENTITY_ADMIN` 或 `LOGIN_SECRET_ADMIN`
- **THEN** 只有授予成功后才获得对应操作权限，授予前后角色变化和操作者均可审计

#### Scenario: Platform admin without membership attempts a tenant write

- **WHEN** 仅有 `SUPER_ADMIN` 平台角色而不是目标 Organization 成员的账号添加成员或授予角色
- **THEN** 系统拒绝写入，不泄露成员、角色或目标账号信息

#### Scenario: Last owner is revoked

- **WHEN** 管理员尝试撤销本组织最后一个 ACTIVE `ORG_OWNER` 绑定
- **THEN** 系统返回冲突，原绑定和 Organization authority version 保持不变

#### Scenario: Retried and concurrent writes preserve committed state

- **WHEN** 同一组织收到并发的重复添加成员、重复授权或撤权请求
- **THEN** 重复添加返回冲突，重复授权复用已有绑定，撤权保留最后一个 ACTIVE owner，每个已提交的实际变更只记录一次审计
- **AND** 审计写入失败时，成员或角色变更与 authority version SHALL 一并回滚

### Requirement: Member and role management is visible only within authorized tenant scope

系统 SHALL 提供有界、稳定分页的本组织成员与角色读取；写操作 SHALL 使用本 Organization 标识限制目标对象。Web SHALL 仅向有相应权限的成员展示操作入口，但不得代替服务端鉴权。

#### Scenario: Member-only account views its organization

- **WHEN** 用户是本组织 ACTIVE 成员但没有管理角色
- **THEN** 可看到所属组织摘要，但无法进入成员管理或执行写操作

#### Scenario: Member administrator reads the member list

- **WHEN** 用户有成员查看权限，但没有组织角色查看权限
- **THEN** 成员列表可正常读取，角色绑定信息保持隐藏；成员接口不返回平台账号邮箱

#### Scenario: Organization or signed-in account changes

- **WHEN** 用户切换组织或登录账号
- **THEN** 页面清除旧范围内未提交的角色操作，成员数据和管理入口根据当前账号与组织重新加载

#### Scenario: Cross-organization target is supplied

- **WHEN** Organization A 的管理员在 A 的路径下提交 B 的成员或角色绑定标识
- **THEN** 系统拒绝且 B 的数据、角色和 authority version 不改变

#### Scenario: Existing login navigation remains intact

- **WHEN** 未登录用户从受保护页面跳转登录，或已登录用户打开登录页
- **THEN** 页面继续遵守现有安全 `returnTo` 和已登录回跳规则；本批组织管理变更不改变其行为
