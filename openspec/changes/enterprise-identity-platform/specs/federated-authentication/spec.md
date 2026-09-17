## Purpose

定义协议无关的企业认证、账号关联和会话契约，并规定首批动态 OIDC 的安全行为，使后续协议能够复用同一身份核心而不复制账号与权限决策。

## ADDED Requirements

### Requirement: Provider protocol and login context are separate

系统 SHALL 将 Provider 协议适配与登录上下文分离。同一 Provider 可以用于平台公共登录，也可以用于企业 Organization 登录；Provider Adapter 只输出已验证事实，账号绑定、资料权威、成员关系和 session 副作用由统一身份核心按上下文决定。

#### Scenario: Public provider login creates only a platform session
- **WHEN** 用户从个人账号登录区域选择 GitHub、GitLab、飞书、钉钉或其他公共 Provider
- **THEN** 系统只按 platform-scoped external identity 完成平台账号登录，不创建 Organization Membership、企业 session origin 或 Namespace 权限

#### Scenario: Enterprise provider login requires organization context
- **WHEN** 用户通过企业账号发现流程选择某个 Organization 的飞书、钉钉或 OIDC 登录连接
- **THEN** 系统在已确定的 Organization/LoginConnection 上下文中完成协议校验，并继续执行企业成员、连接、authority version 和企业 session guard

#### Scenario: Provider implementation is reused without duplicating identity decisions
- **WHEN** 新增飞书或钉钉 Provider
- **THEN** 协议代码可以复用 authorization、token、userinfo 和 subject 解析，但不得引入绕过统一身份核心的建号、绑定、成员授权或 session 创建路径

### Requirement: Adapters output normalized verified assertions

认证 Adapter SHALL 只在完成协议级校验后输出标准化 Identity Assertion；Adapter 不得创建账号、绑定 Membership、修改企业资料、授予 Namespace 权限或建立 session。

#### Scenario: OIDC callback is valid
- **WHEN** OIDC Adapter 完成 issuer、signature、algorithm、key、audience/azp、expiry、issued-at、state、nonce、PKCE 和授权码交换校验
- **THEN** Adapter 输出 connection、issuer、typed subject、verified claims 和 assurance，由统一身份核心继续决策

#### Scenario: Protocol verification fails
- **WHEN** 任一必需协议校验失败或连接 revision 已失效
- **THEN** 系统拒绝登录，不创建账号、Membership、External Identity 或 session

### Requirement: Adapter registration is versioned and extensible

每个 Adapter SHALL 注册唯一 key、contract version、config schema version、interaction model 和 capabilities；运行时 SHALL 拒绝不支持的版本，不得猜测降级。

#### Scenario: Compatible adapter revision is activated
- **WHEN** Login Connection revision 引用受支持的 Adapter contract、schema 和 capabilities
- **THEN** 运行时可通过统一 registry 构造 snapshot，身份核心无需增加 provider-name 分支

#### Scenario: Unsupported adapter version is encountered
- **WHEN** active revision 引用当前运行时不支持的 contract 或 schema version
- **THEN** 系统拒绝激活或认证，并返回不泄露配置内容的可诊断错误

### Requirement: Login Connection runtime is immutable

Login Connection SHALL 分离可编辑草稿、不可变 revision 和 active runtime snapshot；管理员必须先成功测试 revision 再激活，激活切换 SHALL 原子发生。

#### Scenario: New revision becomes active
- **WHEN** 管理员成功测试并激活一个兼容 revision
- **THEN** 新登录使用该 revision，已开始的登录继续使用启动时绑定的 snapshot

#### Scenario: Untested revision is activated
- **WHEN** 管理员尝试激活未测试成功或测试结果已不匹配的 revision
- **THEN** 系统拒绝激活，当前 active revision 不变

#### Scenario: Connection is suspended
- **WHEN** 管理员显式确认并暂停 ACTIVE connection
- **THEN** discovery 不再展示该连接，新的 start/callback 被拒绝，旧企业会话在企业边界失效

### Requirement: Login discovery resists enumeration

匿名 discovery SHALL 仅依据规范化 Organization slug 或 ACTIVE VERIFIED domain 返回公开品牌和登录选项；不得查询或泄露账号、Membership、内部 connection id、issuer 或 Secret。

#### Scenario: Two local parts use the same verified domain
- **WHEN** 匿名用户分别提交 `member@example.com` 和 `unknown@example.com`，且 `example.com` 属于同一 Organization
- **THEN** 两个响应的公开登录数据相同，系统不根据 local part 判断成员是否存在

#### Scenario: Unknown identifier is submitted
- **WHEN** 匿名用户提交未知组织或 domain
- **THEN** 系统返回通用结果和仍可用的公共登录方式，不表明用户或组织是否存在

### Requirement: Anonymous enterprise endpoints use opaque handles and fixed origins

企业 start/callback SHALL 只接受平台生成的 opaque public handle；callback origin SHALL 来自 operator 配置，不得由 Host、Forwarded header、issuer 参数或客户端 URL 决定。

#### Scenario: Internal identifier or unsafe return target is supplied
- **WHEN** 请求使用 connection database id、issuer URL、非法 handle、绝对 URL 或协议相对 URL
- **THEN** 系统在调用 Adapter 前拒绝请求且不泄露连接是否存在

#### Scenario: Callback host headers are forged
- **WHEN** callback 请求携带攻击者控制的 Host 或 Forwarded header
- **THEN** 系统仍使用已配置 callback base 和已绑定事务，不把请求头作为可信 origin

### Requirement: OIDC authorization transaction is one-time and browser-bound

动态 OIDC SHALL 使用有过期时间的一次性事务绑定 public handle、connection revision、state、nonce、PKCE verifier、return target 和随机浏览器值；callback 必须完整匹配后才能消费。

#### Scenario: Valid callback completes once
- **WHEN** callback 的 state、浏览器 Cookie、public handle 和协议响应都匹配未过期事务
- **THEN** 系统原子消费事务、完成身份决策，并在成功或失败响应中删除绑定 Cookie

#### Scenario: Callback is replayed
- **WHEN** 已消费 callback 被再次提交，即使参数和 Cookie 相同
- **THEN** 系统拒绝请求且不创建第二个 session 或重复关联

#### Scenario: Browser binding is missing or duplicated
- **WHEN** callback 没有绑定 Cookie或存在多个同名 Cookie
- **THEN** 系统拒绝 callback，不仅依赖 state 判断请求有效

### Requirement: OIDC provider data is fetched with outbound protections

OIDC discovery、JWKS 和 token endpoint 访问 SHALL 限制协议、目标地址、重定向、连接/读取超时和响应大小；每一跳 SHALL 验证全部解析地址，并将已验证地址固定到该次实际连接，同时保留原主机名执行 TLS SNI 和 hostname verification。

#### Scenario: Metadata resolves to a private address
- **WHEN** issuer 或 metadata/JWKS/token endpoint 解析到回环、私网、链路本地、保留或不允许的地址
- **THEN** 系统在发送凭证或读取响应前拒绝访问

#### Scenario: Endpoint redirects to a different target
- **WHEN** 上游返回重定向
- **THEN** 系统不自动跟随未经重新校验的目标，不把 authorization code 或 client secret 转发到新地址

#### Scenario: Response exceeds the configured bound
- **WHEN** metadata、JWKS 或 token response 超过允许大小或超时
- **THEN** 系统中止读取并返回标准化暂不可用错误，不记录响应正文

### Requirement: External identity uses an issuer-scoped typed subject

External Identity SHALL 至少由 Organization、Login Connection、issuer、subject type 和 subject value 唯一确定；显示名或 email 不得替代该坐标。

#### Scenario: Same subject is returned by two issuers
- **WHEN** 两个不同 issuer 返回相同 subject 文本
- **THEN** 系统将它们视为不同 External Identity，不自动合并 Platform Account

#### Scenario: Same subject is used in two organizations
- **WHEN** 两个 Organization 的连接返回相同 issuer/subject 文本组合
- **THEN** 两个租户的 identity coordinate 独立，任何关联不得跨 Organization 泄漏

### Requirement: First-login correlation is deterministic and conservative

统一身份核心 SHALL 按“已有有效 binding → 同连接预供给 immutable subject → 策略允许的 verified email → 策略允许的 JIT → 拒绝”顺序处理首次登录，并在歧义、历史成员或已绑定账号场景 fail closed。

#### Scenario: Existing binding wins
- **WHEN** assertion 命中已有有效 External Identity binding
- **THEN** 系统使用绑定的 Platform Account，不继续执行 email 或 JIT 匹配

#### Scenario: Pre-provisioned subject is matched
- **WHEN** assertion 精确命中同一 Organization 和 Connection 下的有效预供给 immutable subject
- **THEN** 系统在一个事务中关联或创建 Platform Account、激活 Membership 并建立 External Identity

#### Scenario: Concurrent first logins race
- **WHEN** 同一 subject 的两个首次登录并发观察到尚无 binding
- **THEN** 数据库唯一约束和有界重读使二者收敛到同一 Platform Account、Membership 和 External Identity，不留下孤立账号

#### Scenario: Existing account is matched only by email
- **WHEN** verified email 对应的 ACTIVE Membership 已绑定 Platform Account，但新 External Identity 尚未绑定
- **THEN** 系统拒绝自动关联并要求未来独立的重新认证 Identity Link 流程

#### Scenario: Historical member exists
- **WHEN** email 或预供给 subject 命中 SUSPENDED、DEPROVISIONED 或 revoked 历史记录
- **THEN** 系统拒绝关联且不得通过 JIT 创建替代成员

### Requirement: Verified email correlation and JIT are explicit opt-ins

系统 SHALL 仅在 Adapter 提供可信 verified assurance、Organization 拥有 ACTIVE VERIFIED domain、revision 显式开启 correlation 时使用 email 参与匹配；JIT 还必须单独显式开启并且不存在当前或历史候选。

#### Scenario: Correlation is disabled
- **WHEN** assertion 含 verified email 但 revision 未开启 verified-email correlation
- **THEN** 系统不按 email 查询或关联 Membership，也不创建 JIT Membership

#### Scenario: Email is unverified
- **WHEN** assertion 含 email 但没有 `email_verified=true` 或协议等价保证
- **THEN** 系统不使用该 email 关联账号或写入可信企业资料

#### Scenario: JIT is explicitly allowed
- **WHEN** verified email 属于同一 Organization 的 ACTIVE VERIFIED domain，correlation 与 JIT 都开启，且没有任何当前或历史 Membership 候选
- **THEN** 系统可以原子创建 JIT Membership、Platform Account 和 External Identity

### Requirement: Account and authority guards run before session creation

系统 SHALL 在创建 session 前检查 Platform Account、Organization、Membership、Login Connection 和 External Identity 的当前状态；merged、system、suspended、deprovisioned 或 disabled 对象 SHALL 拒绝登录。

#### Scenario: Account is merged or reserved
- **WHEN** 关联结果指向 merged account 或 system account
- **THEN** 系统拒绝登录并记录脱敏安全审计

#### Scenario: Membership changes during login
- **WHEN** Membership 在协议验证完成后、session 提交前被暂停或 authority version 变化
- **THEN** 系统拒绝建立 session，不使用启动时旧状态放行

### Requirement: Enterprise sessions retain origin and authority version

企业 session SHALL 记录 Organization、Membership、Connection、External Identity、assurance、认证时间以及 Organization/Membership authority version，并只持久化不可逆 session key 摘要。

#### Scenario: Membership is suspended after login
- **WHEN** 已登录成员被暂停并递增 authority version
- **THEN** 下一次企业资源请求拒绝旧 session，不等待 session 自然过期

#### Scenario: Device approval becomes stale
- **WHEN** 企业成员批准 CLI Device Flow 后、兑换前被暂停或 authority version 变化
- **THEN** 系统拒绝兑换且不创建 API Token

#### Scenario: API token request carries a browser cookie
- **WHEN** API Token 请求同时携带无关企业浏览器 Cookie
- **THEN** 系统仍以 Token 对应 Platform Account 和当前 Membership 判定企业访问，不继承 Cookie authority

### Requirement: Existing authentication remains compatible

local login、公共 OAuth、CLI Device Flow 和 API Token SHALL 保留既有路径和主体语义；统一身份迁移不得自动赋予企业 Membership 或 Namespace 权限。

#### Scenario: Public OAuth keeps legacy persistence during the first rollout
- **WHEN** GitHub/GitLab 公共 OAuth 在 R1-A 中登录
- **THEN** 统一身份核心先做 platform-scoped 决策门禁，legacy `identity_binding` 仍作为持久化权威

#### Scenario: Active unified core denies a public OAuth correlation
- **WHEN** identity core 为 ACTIVE 且公共 OAuth 的统一身份决策返回 Denied 或 Conflict
- **THEN** 系统 fail closed，且不创建 active 或 pending legacy binding

#### Scenario: Public OAuth V2 write authority is evaluated later
- **WHEN** 后续决定把公共 OAuth 运行时写入权威切到 External Identity V2
- **THEN** 该切换必须独立完成新写入同步、历史回填验证、回滚和真实 Provider 登录验收；不得作为 R1-A 上线前置条件

### Requirement: Dynamic enterprise OIDC is disabled by default

动态企业 OIDC SHALL 仅在身份核心对目标 Organization 为 `ACTIVE`、OIDC 登录数据面开关启用且目标 Organization 位于 allowlist 时公开和执行；控制面开关只决定连接管理组件是否装配，不得隐式开放匿名登录。默认配置 SHALL 不改变现有登录。

#### Scenario: Global switch is disabled
- **WHEN** operator 未开启动态企业 OIDC
- **THEN** discovery 不展示企业连接，start/callback 不进入 Adapter，公共登录仍可用

#### Scenario: Organization is not allowlisted
- **WHEN** 全局开关已启用但目标 Organization 不在 allowlist
- **THEN** 该组织的连接保持不可发现且认证数据面拒绝使用

### Requirement: Login page is driven by advertised authentication capabilities

登录页 SHALL 使用独立的左右分屏页面框架，不嵌入 Marketplace 导航和大型页脚。前端 SHALL 依据现有匿名 `/api/v1/auth/methods` catalog 展示入口，不硬编码公共 Provider 或无条件展示企业发现。移动端 SHALL 隐藏装饰区并保持表单无横向溢出。

服务端 SHALL 仅在 OIDC 控制面和登录数据面均启用、Organization allowlist 非空且 identity core 为 `ACTIVE` 时声明 `ENTERPRISE_DISCOVERY`。该声明 SHALL 不暴露 Organization 清单、内部 connection id、issuer 或 Secret；具体可用连接仍由现有 discovery 和数据面校验。

#### Scenario: Mixed enterprise and account login
- **WHEN** catalog 同时声明企业发现和当前表单支持的账号密码方式
- **THEN** 企业发现默认选中；分段按钮切换时仅显示一个表单，已输入内容保留，左侧品牌位置不变

#### Scenario: Registration uses the same authentication shell
- **WHEN** 用户从登录进入注册或直接访问 `/register`
- **THEN** 使用相同分屏外壳，混合方式下保留相同位置的分段入口且个人账号选中；注册内容置于其下，保留现有字段、校验和接口；不回到旧卡片页，小高度窗口仅表单区滚动。没有配置 OAuth 时不得在说明中宣传 OAuth 登录
- **AND** 分段标签为“企业账号 / 个人账号”，表示身份类型而非登录动作；注册页唯一主标题为“创建个人账号”，不同时显示“登录 SkillHub”或重复的创建标题

#### Scenario: Enterprise discovery succeeds
- **WHEN** discovery 返回可用 Organization
- **THEN** 查询表单替换为返回的组织与登录选项，用户可以更换企业；继续通过既有 actionUrl 跳转企业身份源，不收集企业密码

#### Scenario: Enterprise login is disabled or rolled back
- **WHEN** 任一 OIDC 开关关闭、allowlist 为空或 identity core 为 LEGACY/SHADOW
- **THEN** catalog 不声明企业入口，页面不显示企业发现空壳，现有公共和本地登录行为保留

#### Scenario: Provider is not configured
- **WHEN** catalog 不包含某个 OAUTH_REDIRECT Provider
- **THEN** 登录页不显示该 Provider 的按钮

#### Scenario: Configured external providers do not depend on enterprise discovery
- **WHEN** 企业发现入口关闭，服务端 catalog 声明已配置的公共 OAuth 来源
- **THEN** 页面按 catalog 的 displayName 和 actionUrl 展示并跳转，不硬编码 GitHub、钉钉、飞书等来源列表；入口展示不代表对应协议适配已经完成

#### Scenario: Successful authentication returns to the source page or homepage
- **WHEN** 用户完成密码登录、注册或 OAuth 登录
- **THEN** 有合法的应用内 returnTo 时返回该页面，保留查询参数和锚点；没有或不安全时返回首页 `/`，不默认进入控制台
- **AND** 导航栏登录入口记录当前页面；显式 CLI 授权目标继续保留；外部 URL、协议相对 URL、反斜杠和控制字符不得成为返回目标

#### Scenario: Catalog cannot be loaded
- **WHEN** 登录方式请求失败
- **THEN** 页面显示可重试错误，不猜测或展示未经 catalog 声明的登录方式
