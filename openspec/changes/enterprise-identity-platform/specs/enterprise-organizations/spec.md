## Purpose

定义企业登录所依赖的最小租户、成员、域名与管理权限边界，使企业身份能够在不依赖 Namespace、目录同步或自动授权的情况下安全建立和撤销。

## ADDED Requirements

### Requirement: Organization is an independent security boundary

系统 SHALL 将 Organization 建模为独立于 Platform Account、Namespace 和身份提供方的安全边界，并为每个 Organization 分配不可变标识和唯一 slug；创建 Organization 不得隐式创建、接管或授权任何 Namespace。

#### Scenario: Platform administrator creates an organization
- **WHEN** `SUPER_ADMIN` 提交唯一 slug、显示名和一个已有可登录 Platform Account 作为初始 owner
- **THEN** 系统在一个事务中创建 ACTIVE Organization、owner 的 ACTIVE Membership 和 `ORG_OWNER` role binding，且不创建 Namespace

#### Scenario: Duplicate slug is rejected
- **WHEN** 管理员提交已占用的 Organization slug
- **THEN** 系统返回冲突错误且不留下 Organization、Membership 或 role binding 部分数据

### Requirement: Platform and tenant administration are separate

系统 SHALL 将平台创建/处置 Organization 的权限面与组织内部管理权限面分离；平台角色不得替代目标 Organization 的 Membership 和组织角色。

#### Scenario: Platform role cannot bypass tenant authorization
- **WHEN** 仅具有 `SUPER_ADMIN` 平台角色但不是目标 Organization 成员的账号访问组织管理 API
- **THEN** 系统拒绝访问且不返回目标组织的成员、域名、连接或角色信息

#### Scenario: Member lists own organizations
- **WHEN** 已认证账号分页查询自己可管理或可查看的 Organization
- **THEN** 系统只返回该账号具有当前 ACTIVE Membership 的组织，并使用稳定排序和不超过 100 的页大小

#### Scenario: Member-only organization is shown without management entry
- **WHEN** 已认证账号拥有目标 Organization 的 ACTIVE Membership 但没有任何组织管理或审计角色
- **THEN** Web UI 可以展示该 Organization 的成员关系，但不得提供进入组织管理详情页的可点击入口，服务端管理 API 仍以 403 作为最终保护

### Requirement: Organization data is tenant isolated

所有 Organization Membership、Domain、Role Binding、Login Connection、External Identity、Session Origin 和组织审计 SHALL 绑定唯一 Organization；读取和写入 SHALL 同时使用 Organization id 限定。

#### Scenario: Cross-organization object id is supplied
- **WHEN** Organization A 的管理员把 Organization B 的 membership、domain 或 connection id 放入 A 的管理路径
- **THEN** 系统按不存在或无权限处理，不变更任何对象且不泄露 B 的敏感信息

### Requirement: Organization lifecycle invalidates stale authority

Organization SHALL 支持 `ACTIVE → SUSPENDED → ACTIVE` 和 `SUSPENDED → DECOMMISSIONED`；`DECOMMISSIONED` 为终态。每次有效状态变更 SHALL 递增 authority version，相同目标状态的重复命令 SHALL 幂等。

#### Scenario: Organization is suspended
- **WHEN** 获授权管理员暂停 ACTIVE Organization
- **THEN** 系统将其置为 SUSPENDED、递增 authority version，并使基于旧版本的企业会话不能继续访问企业边界

#### Scenario: Direct decommission is rejected
- **WHEN** 管理员尝试把 ACTIVE Organization 直接置为 DECOMMISSIONED
- **THEN** 系统拒绝该转换且状态和 authority version 保持不变

#### Scenario: Repeated suspension is idempotent
- **WHEN** 管理员重复暂停已经 SUSPENDED 的 Organization
- **THEN** 系统返回当前状态且不再次递增 authority version

### Requirement: Platform account and organization membership are separate

系统 SHALL 区分可登录的 Platform Account 与 Organization Membership；一个账号可以属于零个、一个或多个 Organization，预供给 Membership 可以暂不绑定 Platform Account。

#### Scenario: Member is pre-provisioned before login
- **WHEN** 组织管理员创建带企业资料但没有 Platform Account 的成员
- **THEN** 系统创建 PROVISIONED Membership，且不生成账号、密码、Token 或登录会话

#### Scenario: The same provider subject is pre-provisioned concurrently
- **WHEN** 两个请求同时尝试在同一 Organization 和 Login Connection 下预留同一 typed subject
- **THEN** 数据库只保留一个预留关系，另一个请求返回稳定冲突且不得留下孤立 Membership

#### Scenario: Account joins two organizations
- **WHEN** 同一 Platform Account 在两个 Organization 中各自具有有效 Membership
- **THEN** 两个 Membership、角色和 authority version 独立管理，不互相复制或覆盖

### Requirement: Membership lifecycle is guarded and reversible only where safe

Membership SHALL 显式区分 PROVISIONED、ACTIVE、SUSPENDED 和 DEPROVISIONED；有效状态变化 SHALL 递增 authority version，DEPROVISIONED 不得由登录流程自动恢复。

#### Scenario: Provisioned member is activated by trusted identity
- **WHEN** 统一身份核心通过同一 Organization 和 Login Connection 的 immutable subject 确认 PROVISIONED Membership
- **THEN** 系统原子绑定 Platform Account、将 Membership 置为 ACTIVE 并递增 authority version

#### Scenario: Suspended member attempts enterprise login
- **WHEN** SUSPENDED Membership 对应的上游身份完成有效协议认证
- **THEN** 系统拒绝建立企业会话，不自动恢复成员状态

#### Scenario: Deprovisioned member attempts re-entry
- **WHEN** DEPROVISIONED Membership 的 email 或 subject 再次出现在登录 assertion 中
- **THEN** 系统拒绝关联和 JIT 替代创建，直到管理员通过独立流程处理

### Requirement: Organization domains have verified ownership lifecycle

Organization Domain SHALL 经 challenge 验证后才能用于登录发现、verified-email correlation 或 JIT；domain 在所有 Organization 间唯一，并支持禁用。

#### Scenario: Unverified domain is submitted to discovery
- **WHEN** 匿名用户提交属于 PENDING 或 DISABLED domain 的 email
- **THEN** 系统不返回该 Organization 的企业登录选项

#### Scenario: Domain is already owned
- **WHEN** 另一个 Organization 尝试添加已被有效占用的规范化 domain
- **THEN** 系统拒绝请求且不披露现有 owner 的内部信息

#### Scenario: Verified domain is disabled
- **WHEN** 获授权管理员禁用 VERIFIED domain
- **THEN** 新 discovery、email correlation 和 JIT 不再使用该 domain，既有 External Identity binding 不被自动迁移或删除

### Requirement: Organization roles use least privilege

系统 SHALL 使用 `ORG_OWNER`、`IDENTITY_ADMIN`、`LOGIN_SECRET_ADMIN`、`MEMBER_ADMIN` 和
`ORG_AUDITOR` 分别授权组织所有权、身份连接管理、登录 Secret 管理、成员管理和只读审计。
本批次不得公开 Directory 或 Entitlement 专用角色；管理 Secret 的动作 SHALL 需要独立的
`LOGIN_SECRET_ADMIN`，不得由 `IDENTITY_ADMIN` 隐式获得。

#### Scenario: Organization owner lacks identity administration role
- **WHEN** 仅有 `ORG_OWNER`、没有 `IDENTITY_ADMIN` 的成员尝试创建或激活 Login Connection
- **THEN** 系统拒绝请求，不把 owner 身份视为所有管理权限的隐式超集

#### Scenario: Identity administrator without secret authority rotates a secret
- **WHEN** 只有 `IDENTITY_ADMIN`、没有 `LOGIN_SECRET_ADMIN` 的成员提交新的 client secret
- **THEN** 系统拒绝 Secret 写入和 revision 创建

#### Scenario: Member administrator changes membership state
- **WHEN** 具有 `MEMBER_ADMIN` 的成员新增、暂停、恢复或撤销 Organization Membership
- **THEN** 系统允许成员管理，但不授予登录连接、Secret、角色委派或审计读取权限

### Requirement: Organization administration is audited and paginated

所有 Organization、Membership、Domain、Role Binding 和 Login Connection 管理写操作 SHALL 产生结构化、脱敏审计；列表 SHALL 使用稳定服务端分页且每页不超过 100。

#### Scenario: Member status changes
- **WHEN** 管理员暂停、恢复或撤销 Membership
- **THEN** 审计记录包含 actor、organization、action、target、时间、requestId 和允许的状态变化，不包含上游 token、Secret 或原始会话标识

#### Scenario: Page size is excessive
- **WHEN** 客户端请求超过最大页大小
- **THEN** 系统拒绝或限制到 100，并保持稳定排序以避免重复或遗漏
