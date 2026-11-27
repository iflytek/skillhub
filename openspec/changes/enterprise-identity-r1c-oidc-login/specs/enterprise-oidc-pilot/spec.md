## ADDED Requirements

### Requirement: Organization employees can start an explicit OIDC sign-in

系统 SHALL 允许员工使用明确的组织标识查找已启用的 OIDC 登录方式，并由服务器生成不透明连接句柄。只在组织为有效试点、数据面开关开启、身份核心与有效连接均满足条件时展示并执行登录。

#### Scenario: Organization and connection are ready

- **WHEN** 员工从组织登录入口选择已启用的 OIDC 连接
- **THEN** 系统绑定该组织和生效版本，安全跳转企业身份源，成功后返回原站内目标或首页

#### Scenario: Organization sign-in is unavailable

- **WHEN** 开关关闭、组织不在试点名单、标识未知或连接不可用
- **THEN** 系统不给出该组织的登录连接或内部信息，公共登录保持可用

### Requirement: The authorization transaction is used once by the initiating browser

系统 SHALL 使用固定回调 origin、state、nonce、PKCE 和短期浏览器绑定校验回调，在通过事务绑定检查后原子消费事务。OIDC adapter SHALL 完成规定的授权码交换、出站安全和身份令牌校验。

#### Scenario: The callback arrives with a valid transaction

- **WHEN** 同一浏览器携带匹配的一次性事务，且交换的令牌来自绑定的 OIDC 连接并通过验证
- **THEN** 系统输出同一组织与连接的可信身份事实，由统一身份核心完成账号关联和会话决策

#### Scenario: The callback cannot be accepted

- **WHEN** 浏览器绑定或协议验证失败、事务过期或重放，或者连接已失效
- **THEN** 系统拒绝登录，不建立会话或建立身份关联，且不在日志中记录凭证和敏感参数

### Requirement: The pilot uses known subjects and current organization authority

系统 SHALL 基于同一组织、连接、issuer 和不可变 subject 的有效绑定或预授权记录决定企业身份；在 Session 提交前及后续企业资源访问重新检查相关授权状态。

#### Scenario: Pre-authorized employee signs in repeatedly

- **WHEN** 员工使用符合预授权坐标的企业账号首登，随后再次登录
- **THEN** 系统收敛到同一个 Platform Account、Membership 和 External Identity，不重复授权

#### Scenario: The verified identity is unknown or its authorization changes

- **WHEN** 没有有效绑定或明确预授权，或管理员暂停组织、成员或连接
- **THEN** 系统拒绝新增企业会话或后续企业资源访问，不按邮箱关联、不创建替代成员
