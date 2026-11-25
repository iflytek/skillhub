## ADDED Requirements

### Requirement: Members can administer an OIDC connection within their organization
系统 SHALL 在企业 OIDC 连接管理开启时，向具备目标组织权限的管理员提供配置、探测与生命周期操作。连接管理须遵循组织授权及 [企业身份治理规范](../../../enterprise-identity-platform/specs/enterprise-identity-governance/spec.md)。

#### Scenario: Connection management is disabled
- **WHEN** 企业 OIDC 连接管理关闭
- **THEN** 系统拒绝连接写入和探测，已授权成员仍可读取连接摘要并使用组织基础功能

#### Scenario: A connection operation targets another organization
- **WHEN** 管理员尝试操作不属于其有权管理组织的连接
- **THEN** 系统拒绝操作，且不泄露目标连接或密钥信息

#### Scenario: Connection and secret administration are assigned separately
- **WHEN** 身份管理员保存 OIDC 草稿，密钥管理员为同一组织的当前草稿录入密钥
- **THEN** 两人分别完成各自操作，身份管理员可探测和激活新 revision，返回内容仅包含密钥状态

#### Scenario: The secret write refers to an outdated draft
- **WHEN** 密钥管理员核对的草稿已被新的 revision 取代
- **THEN** 系统拒绝写入，当前草稿和 active revision 均不改变

### Requirement: Activation requires a successful probe of the same revision
系统 SHALL 仅激活成功完成探测且配置和密钥版本与探测时一致的最新 revision。激活、暂停、恢复和禁用 SHALL 要求显式确认，并记录不含凭证的审计事件。

#### Scenario: A revision was edited after a successful probe
- **WHEN** 管理员成功探测一个 revision 后提交了会改变配置或密钥版本的新 revision
- **THEN** 新 revision 需要重新探测，在成功激活前现有 active revision 保持有效

#### Scenario: An activated connection is not yet available for sign-in
- **WHEN** 管理员成功激活 revision
- **THEN** 连接可供管理员查看，员工匿名企业登录入口仍不开放

### Requirement: Operators can distinguish and restore the previously effective configuration

组织连接详情 SHALL 在生效版本与草稿不同时分别展示两者；已暂停时标出上次生效版本。密钥只以配置状态展示。停用为终止状态。探测失败 SHOULD 展示基于已知失败类型的排查提示。

#### Scenario: A published configuration coexists with an untested draft
- **WHEN** 已启用的连接保存了更新的草稿
- **THEN** 连接保留原生效版本，界面明确标出该版本和待启用草稿，未通过测试的草稿不可启用

#### Scenario: A suspended connection has a newer draft that failed testing
- **WHEN** 有权的身份管理员明确确认恢复与上次生效版本一致的 revision
- **THEN** 系统只恢复该版本，保留更新草稿及其测试结果

#### Scenario: The restore target is stale or the connection is disabled
- **WHEN** 请求的恢复目标与上次生效版本不同，或者连接已禁用
- **THEN** 系统拒绝恢复并保留现有连接状态
