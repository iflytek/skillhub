## Context

本设计是统一身份认证与企业身份平台的第一批可部署切片。动机与用户可见范围见 [proposal.md](./proposal.md)，行为契约见 `specs/`，分批合并、部署和启用策略见 [rollout-plan.md](./rollout-plan.md)。

SkillHub 已有四类认证入口：local password、公共 OAuth/OIDC、CLI Device Flow 和 API Token。历史 OAuth 流程同时承担上游 claims 解析、账号查找/创建、资料写入和 session 建立；这种结构无法安全扩展到企业自带身份源。另一方面，企业登录在首次认证前就需要一个稳定租户边界，用来约束连接、预供给成员、已验证域名和撤权，但这个 Organization 不能与 Namespace 或目录同步混为一体。

本批次跨 `domain`、`auth`、`infra`、`app` 和 `web`，涉及新的持久化模型、匿名回调、Secret、出站网络和兼容迁移，因此必须支持默认关闭、组织灰度、可观测回滚和真实协议验证。

## Goals / Non-Goals

**Goals:**

- 建立稳定的“协议边缘 → 已验证事实 → 统一身份决策 → 平台主体 → 会话”主链路。
- 将 Provider 协议与登录上下文解耦：同一 Provider 可以用于平台公共登录，也可以在另一个批次中用于企业 Organization 登录，但账号决策只走统一核心。
- 用最小 Organization/Membership 模型承载租户隔离、预供给、状态检查和 authority version，不依赖 Namespace。
- 首批把动态 OIDC 做到可配置、可测试、可激活、可灰度、可撤权和可回滚。
- 将已有公共 OAuth 登录接入同一账号状态与资料权威规则，同时保留 legacy 回读能力。
- 为 SAML、CAS 和 credential-based Adapter 冻结足够小的扩展边界，而不提前实现这些协议。
- 提供可自动化的迁移、安全、协议、浏览器和回滚验收入口。

**Non-Goals:**

- 不在本批次实现 SAML、CAS、LDAP end-user login 或厂商专用 Adapter。
- 不实现 Directory Connection、SCIM、用户/群组同步、调和任务或组织通讯录 UI。
- 不把 Organization 自动映射为 Namespace，不创建 group-to-Namespace mapping 或 source-aware grant。
- 不实现 Identity Link 或 Account Merge V2；首次登录不能把已绑定账号仅凭 email 静默合并。
- 不改变 Skill、Namespace、CLI Sync 或发布生命周期。
- 不让协议 Adapter 直接访问账号、Membership、Namespace 或 session 仓储。

## Decisions

### 登录体验：独立页面与配置驱动

登录页复用现有 `/api/v1/auth/methods`，增加同一 DTO 形状下的 `ENTERPRISE_DISCOVERY` capability；不新增独立“登录体验配置”API。Catalog 根据现有 OIDC 双开关、Organization allowlist 和 identity core ACTIVE 声明通用发现入口，不读取或公开租户清单。具体 Organization 和 ACTIVE connection 仍由既有 discovery 与 redirect gateway 判断。

外部入口链路是服务端 OAuth2 registration 配置 → AuthMethodCatalog → 页面按钮，不由前端写死 GitHub、钉钉、飞书列表，也不依赖企业发现开关。当前过滤规则是 client ID 非空且不含 placeholder；没有新增每个来源的 enabled 字段。来源名称使用配置的 client-name，跳转使用服务端 actionUrl。新增厂商仍需后端协议适配、稳定 subject 解析和真实授权验收，配置一个显示名称不能替代这些能力。

登录、注册及 OAuth 成功后优先返回合法的应用内 returnTo；缺省返回首页 `/` 而非控制台。导航栏登录链接携带当前路径、查询参数和锚点；显式 CLI 授权路径保留。不采用未经校验的外部地址或浏览器 referrer 作为返回目标。

桌面使用固定视口的左右分屏：左侧品牌与静态背景位置不随表单内容变化，右侧登录流程顶端对齐；小高度或放大场景只允许右侧 main 独立滚动，页头与条款保持可见。移动端只保留品牌入口和表单。企业发现与账号密码使用分段切换，同时只显示一种表单，切换保留输入；只有一种方式时不显示空分段。公共 OAuth 为紧凑按钮，并仅展示实际配置的 Provider。请求失败提供重试，空 catalog 明确提示联系管理员；未知 method type 不推断交互。

企业发现成功后用返回的组织名称和 loginOptions 替换查询表单，提供“更换企业”回到输入，并明确跳转企业身份源授权页面；不收集企业密码，不将此流程与组织同步耦合。`/register` 与 `/login` 共用 AuthShell，通过站内路由切换，保留现有用户名、邮箱、密码校验与注册 API，不再复用旧 Card/Tabs 页面。

本轮不增加企业专属域名、动态租户品牌、单 IdP 自动跳转、本地账号禁用或独立 break-glass 管理入口。这些需要后续独立产品与安全决策。本地账号继续按现有服务端 catalog 始终可用；direct password 与 session bootstrap 保留已有前后端运行配置约束。图中虚构的黑色 S Logo 替换为项目正式 BrandMark。

背景为本地打包的约 7 KiB WebP，不新增 Prompt、动画或绘图库，不新增持久运行服务。

2026-09-17 人工反馈后收紧尺寸：表单最大宽度 440px，密码登录输入和主按钮高 40px，右侧主标题 24px，左侧标题 36–48px。混合方式下隐藏与分段标签重复的可见标题，但保留语义标题。普通桌面验收必须检查右侧 main 无溢出，不能仅检查 document 无溢出；小高度、放大、校验错误增加内容时保留可访问的局部滚动，不用隐藏滚动条掩盖裁切。

分段代表身份类型，固定使用“企业账号 / 个人账号”，不带“登录”。`/register` 主标题使用“创建个人账号”，说明与主标题随登录/注册动作切换，去掉原有重复创建副标题；分段和左侧位置保持一致。个人注册不会创建企业身份或授予组织权限。

### Provider 协议与登录上下文分离

统一身份认证的第一层抽象是 Provider 协议，不是“企业/非企业”。GitHub、GitLab、飞书、钉钉、标准 OIDC、CAS、SAML、LDAP gateway 都是 Provider 或 Adapter；它们输出同一种已验证身份事实。登录上下文决定这些事实如何被消费：

```text
Provider Adapter
  -> ProviderAuthenticationResult / IdentityAssertion
  -> External Identity Login Core
  -> loginContext: PUBLIC_PLATFORM | ENTERPRISE_ORGANIZATION | CLI_BROWSER_AUTH
  -> PlatformPrincipal / Enterprise session / Device authorization
```

`PUBLIC_PLATFORM` 只证明“这个外部账号可以登录 SkillHub 平台账号”，不得创建 Organization Membership、企业会话或 Namespace 权限。`ENTERPRISE_ORGANIZATION` 在已确定 Organization/LoginConnection 后运行，额外执行企业成员、连接、authority version 和 session-origin guard。二者共享协议客户端、subject 解析和资料可信度判断，但不能共享租户凭证、连接生命周期或权限副作用。

因此飞书、钉钉既可以是公共快捷登录，也可以是某个企业配置的 SSO。产品入口应分开：

```text
个人账号登录
  - local password
  - GitHub / GitLab / Feishu public / DingTalk public

企业账号登录
  - 输入企业邮箱或企业标识
  - discovery 返回该企业已激活的 OIDC / Feishu enterprise / DingTalk enterprise connection
```

首批统一身份核心应先证明所有外部公共登录都会经过同一个账号状态、绑定、资料权威和 session 决策门禁；公共 OAuth 的持久化仍可保留现有 legacy binding，避免第一批迁移存量 GitHub/GitLab 身份。企业 Organization 登录复用同一核心并增加企业上下文。不能再新增 `FeishuLoginService`、`DingTalkLoginService` 这类各自建号、绑定和建 session 的旁路。

### 飞书、钉钉 Provider 与统一身份架构的接入边界

2026-09-22 状态：R1-A 统一身份核心已合并；飞书公共 Provider Adapter 已合并但仍需真实厂商登录验收；钉钉公共 Provider Adapter 已合并并完成真实钉钉往返验证修正。二者仍是公共平台登录能力，不等同于每个 Organization 单独配置凭证、策略与成员上下文的企业登录。

公共 Provider 当前主要提供部署级 OAuth 登录：配置一个 Provider registration，匿名 catalog 暴露按钮，通过公共 OAuth callback 进入统一身份核心门禁，同时 legacy `identity_binding` persistence 仍是公共 OAuth 的写入权威。企业 redirect 后续经 Adapter、IdentityAssertion、EnterpriseIdentityAssociationService 和企业 session。不能描述为所有写入路径已经统一迁移到 Binding V2。

接入分三批，而不是为每家厂商复制身份核心：

1. **公共登录兼容批次**：复用 PR 的 authorization/token/userinfo/claims 处理、图标与测试，保留既有 Provider code、回调和部署配置语义；把加载分派接到当前 OAuthLoginFlowService 或其统一核心替代入口。避免在 SecurityConfig 不断叠加厂商专属 if；此批仅提供平台级登录，不建立企业或 Namespace 权限。
2. **公共登录统一核心批次**：将 GitHub/GitLab/Feishu/DingTalk 等公共 Provider 的已验证事实统一送入 platform-scoped 统一身份决策门禁；ACTIVE 模式下 Denied/Conflict 必须在写入 active 或 pending legacy binding 前 fail closed。legacy binding 继续作为公共 OAuth 的写入与回滚权威；V60 可以保留只读影子回填和一致性校验，但不代表运行时写入权威已经切到 V2。LEGACY/SHADOW/ACTIVE 模式、冲突 fail closed 和资料权威规则必须覆盖所有公共 Provider，而不仅是 GitHub/GitLab。
3. **企业连接批次**：在独立需求下将厂商协议实现封装为 RedirectAuthenticationAdapter，并提供对应配置校验/连接测试与 registry 装配。由现有 LoginConnection 版本、secret reference、组织 allowlist 和 discovery 选择企业连接；Adapter 仅输出验证后的 IdentityAssertion，关联、JIT、账号状态与 session 仍由既有核心决定。无需等待 SCIM/通讯录同步，但不自动授予 Namespace 角色。

公共按钮与企业连接可以并存：公共按钮使用平台配置，企业入口先确定 Organization/connection 再跳转其身份源。共享协议客户端与验证逻辑，不共享租户凭证或放宽租户边界。前端公共图标 resolver 当前只识别 GitHub/GitLab/OIDC，接入厂商 PR 时需同步支持其已提供的真实图标；不能只合后端然后声称完整入口已接通。

身份坐标必须包含可信的组织/连接上下文、issuer 与 typed subject；裸 open_id/unionId/userId、昵称或邮箱不能代表全球唯一企业身份。钉钉公共 Provider 的稳定主 subject 是 `unionId`，不以 `openId` 或 `userId` 做运行时 fallback：`openId` 按应用隔离，`userId` 按组织隔离，任一字段可用性变化都可能把同一人拆成不同平台账号。当前 R1-A2 不做别名迁移；如果将来需要接受历史 `openId`/`userId`、切换 subject 或支持企业钉钉连接，必须以独立迁移完成：先生成候选 alias 冲突报告，再由管理员/运维确认，最后写入显式 alias/binding 记录。运行时登录不得因为“找不到 unionId”而静默改用其他字段，也不得仅凭邮箱或昵称合号。飞书的 open_id 需保留应用作用域；union_id 不自动证明跨应用或跨组织可合号。邮箱必须有可靠的验证依据才进入 VerifiedEmail，禁止仅因返回邮箱或名称相同而绑定。

组织、部门与人员目录同步属于后续 provisioning connection，不能复用登录 token 暗中拉取通讯录。实现前分别验证公共登录回归、企业连接隔离、旧 binding 兼容、验证 email、账号禁用与回调防重放；当前没有执行真实飞书/钉钉登录验收。

### 1. Organization 是认证租户边界，不是 Namespace 的别名

`Organization` 表示企业身份与治理边界；`Namespace` 表示 Skill 协作和授权边界。一个组织未来可以管理多个 Namespace，一个账号也可以属于多个组织。第一批只实现 Organization、Membership、Domain 和组织角色，二者之间没有自动授权关系。

将企业直接等同于 Namespace 会使多部门、多 Namespace、共享 Namespace 和后续目录来源无法表达。复用 Namespace membership 虽然改动小，但会把登录准入和 Skill 权限耦合，停用时容易误删手工授权，因此不采用。第一批公开角色只包含 `ORG_OWNER`、`IDENTITY_ADMIN`、`LOGIN_SECRET_ADMIN`、`MEMBER_ADMIN` 和 `ORG_AUDITOR`；Directory/Entitlement 角色留到对应能力真正交付时再通过向前迁移增加。

### 2. 协议 Adapter 只产生已验证事实

认证链路分为五层：

```text
OIDC Adapter
  -> IdentityAssertion（已验证事实）
  -> ExternalIdentityLoginModule（统一关联与 guard）
  -> PlatformPrincipal（稳定平台账号）
  -> EnterpriseBrowserSessionService（来源与 authority version）
  -> Spring Session
```

Adapter 负责协议特有的签名、issuer、audience、时效、state/nonce/PKCE 等校验，并输出标准化 `IdentityAssertion`。控制面的同协议 Adapter 还负责把管理员录入的协议 subject 转换为与 Assertion 完全相同的 typed identity coordinate；Organization 应用服务不得理解 OIDC、SAML 或 CAS 的 issuer/subject 结构。统一身份核心独占以下决策：已有 binding、预供给 subject、verified email、JIT、账号状态、成员状态、资料权威和会话创建。

每个 provider 实现完整 login service 会复制安全规则并产生协议间不一致，因此不采用。顶层扩展契约按 interaction model 区分 redirect、credential 和 passive assertion，避免为了兼容所有协议而设计万能接口。

### 3. 登录连接使用控制面与数据面分离

`LoginConnection` 保存稳定 handle、组织、Adapter key 和生命周期；`LoginConnectionRevision` 保存不可变 typed configuration、能力、schema/contract version、Secret binding version 和关联策略。管理员创建 revision 后必须先测试，再原子激活。正在执行的登录事务绑定启动时的 runtime snapshot，不读取半更新配置。

连接状态采用 `DRAFT → ACTIVE ↔ SUSPENDED → DISABLED` 的受控转换，`DISABLED` 为终态。匿名入口只接受不可枚举的 public handle，不接受数据库 ID、issuer URL 或任意 callback origin。

配置编辑直接覆盖当前记录无法解释进行中的登录，也无法安全回切，因此不采用。

### 4. External Identity V2 使用复合身份坐标

企业外部身份唯一键至少包含：

```text
(organization_id, connection_id, issuer, subject_type, subject_value)
```

平台级公共 OAuth 使用 platform scope 的等价坐标参与统一身份决策门禁。`subject` 必须是协议保证稳定的标识；显示名和 email 不是主键。Release 1 可以把 GitHub/GitLab 既有 `identity_binding` 影子回填到 External Identity V2 以做一致性校验，但 legacy binding 仍保留为公共 OAuth 的写入、兜底和回滚权威。后续如要把运行时写入权威切到 V2，必须作为独立批次完成新写入同步、历史回填验证、回滚和真实登录验收；冲突时 fail closed。

仅用 `provider + subject` 无法区分企业自建 issuer；仅用 email 会导致地址复用或验证强度不足时账号接管，均不采用。

### 5. 首次登录关联采用确定性优先级

统一核心按以下顺序执行，并在一个事务中收敛：

1. 已存在且有效的 External Identity binding。
2. 同 Organization、Connection 下预供给的 immutable subject。
3. revision 显式开启后的 verified-email correlation。
4. revision 同时允许、domain 已验证且没有历史冲突时的 JIT。
5. 拒绝。

数据库唯一约束承担最终并发仲裁；重复预供给在写入前检查，但数据库仍是并发竞争的最终裁决者，失败统一返回稳定冲突而不是 500，并回滚同事务新建的 Membership。首次登录竞争失败只允许有界重读，不能留下孤立账号。`SUSPENDED`、`DEPROVISIONED`、已撤销预供给 subject、歧义 email、已绑定账号和非 ACTIVE 组织均阻止自动关联。未来 Identity Link 必须走独立重新认证流程。

### 6. email 是受限信号，不是全局身份键

只有 Adapter 明确证明 email 已验证、Organization 持有 ACTIVE verified domain、连接 revision 显式开启 correlation 时，email 才能参与关联。默认关闭 verified-email correlation 和 JIT。上游 email 变化不会迁移已有 binding。

组织域名验证使用 challenge 生命周期，匿名 discovery 只能根据已验证 domain 返回相同的公开连接信息，不能查询账号或 Membership，从而避免 local-part 枚举。

### 7. 动态 OIDC 采用固定回调和一次性事务

OIDC start 创建带过期时间的一次性事务，保存 connection revision snapshot、state、nonce、PKCE verifier、returnTo 和浏览器绑定；Redis 只保存服务端状态。浏览器 Cookie 只含随机绑定值，使用 `Secure`、`HttpOnly`、`SameSite=Lax` 和受限 path。

回调必须同时匹配 public handle、state、浏览器绑定和未消费事务；成功或失败后清理绑定 Cookie。callback base 由 operator 明确配置，不信任请求 Host 或 Forwarded header。`returnTo` 只接受本地相对路径。

OIDC ID Token 校验至少覆盖 issuer、签名、算法、kid、audience/azp、expiry、issued-at 和 nonce。metadata/JWKS/token endpoint 均执行 HTTPS、主机/IP、重定向、超时和响应大小限制；不将 code、state、token、email 或 Secret 写入日志。

### 8. Secret 使用 envelope encryption 和版本绑定

OIDC client secret 不写入 typed configuration、API 响应、审计详情或日志。Secret 单独保存为加密 envelope，记录用途、key id、版本和状态；revision 只保存 Secret version reference。运行时只有 materializer 在构造不可变 snapshot 时短暂解密。

创建带 Secret 的连接或新 revision 需要 `ROTATE_LOGIN_SECRETS` 组织权限；激活、暂停和禁用要求显式确认。第一批不提供批量轮换或跨连接复用。

### 9. 企业会话保存来源并按版本 fail closed

企业浏览器会话持久化 `organization`、`membership`、`connection`、`external identity`、assurance、认证时间及 Organization/Membership authority version，并只保存 session key 的不可逆摘要。每次进入企业边界时重新检查账号、组织、成员、连接和 identity 状态以及版本。

暂停或停用会递增 authority version，旧会话随即失效。Device Flow 在批准与兑换之间也重新校验；API Token 继续代表同一 Platform Account，但企业资源访问必须使用当前 Membership 判定，不能继承浏览器 Cookie 的来源。

### 10. 既有认证采用兼容迁移

local login、公共 OAuth、Device Flow 和 API Token 的路由与响应保持不变。公共 OAuth 将已验证 facts 送入统一核心；身份核心支持 `LEGACY` 和 `ACTIVE` 模式。V54–V60 只做向前 schema/constraint/backfill，不删除旧表或旧 binding，因此回滚应用时无需执行 destructive down migration。

动态企业 OIDC 把控制面装配开关、匿名登录数据面开关和 Organization allowlist 分开。控制面可以先启用来配置、探测和激活连接，同时保持匿名 discovery/start/callback 关闭；只有身份核心对目标 Organization 为 `ACTIVE`、登录数据面开启且 Organization 位于 allowlist 时，动态登录才可执行。身份核心切回 `LEGACY` 时公共 OAuth 完全走旧 binding。

认证专属的 Login Connection、revision、Secret、External Identity、认证操作和 Session origin
属于 `skillhub-auth` 安全边界。它们的聚合、Repository port 和 JPA adapter 在 auth 内闭合，
`skillhub-domain` 只承载跨产品能力共享的 Platform Account 与 Organization 业务域；app 只能
通过 auth 公开 port 编排，不得依赖 Spring Data Repository 或协议实现。这是对仓库既有
ApiToken、IdentityBinding、LocalCredential 和 Account Merge 归属的延续，不允许扩展成
普通业务聚合跳过 infra 的通用规则。

### 11. API 和权限面分离

平台面只允许 `SUPER_ADMIN` 创建 Organization 并指定已有 Platform Account 为初始 owner。租户面即使调用者是 `SUPER_ADMIN`，也必须具有目标 Organization 的 ACTIVE Membership 和相应组织角色。

组织角色最小集合保留未来扩展槽位，但本批次只实现组织、成员、域名和登录连接所需动作。Controller 只绑定 transport 与 `PlatformPrincipal`；应用服务编排事务；领域服务执行生命周期/RBAC；query repository 负责分页与组合视图。

匿名面仅包括 login discovery、enterprise start 和 callback，并分别配置限流。错误响应遵循现有 `ApiResponse`，对匿名方使用通用错误，不泄露组织、成员、连接或账号是否存在。

### 12. 扩展新协议必须是独立批次

SAML 和 CAS 复用 Organization、Connection、External Identity、关联、guard 和 session，只新增协议 Adapter、配置 materializer、控制面 probe、真实参考服务和协议安全矩阵。每个协议单独 OpenSpec/commit/验收，不与 Directory/SCIM 同批。

LDAP 只有在明确决定 credential-based end-user flow、TLS 策略、稳定 object id 和密码不落盘后才能标记为登录能力。Directory/SCIM 是 provisioning plane，独立于 authentication plane；二者即使共用上游产品，也不得共用连接类型或生命周期状态。

本批次运行时代码只声明 `LOGIN` connection kind、登录 capability，以及
`MANUAL`/`INVITATION`/`JIT` Membership source；不提前公开 Directory connection、capability、
profile source 或 Membership source 占位值。后续 provisioning 批次通过自己的 OpenSpec 和
向前迁移增加这些词汇，不能让当前 API 或数据库值暗示已经支持 Directory/SCIM。

### 13. 验证分四层，证据不可互相替代

- L0：OpenSpec strict validation、架构依赖检查、format/diff guard。
- L1：domain/auth/app 单元与持久化测试，覆盖关联顺序、并发、状态 guard、OIDC 校验、Secret、session 和迁移。
- L2：从当前 exact SHA 构建镜像，运行 PostgreSQL/Redis/SkillHub，验证健康、迁移、配置开关、回滚和既有认证兼容。
- L3：使用隔离 Docker OIDC 参考实现和真实 Windows 浏览器，验证 discovery → redirect → IdP → callback → session、错误回调、重放、跨组织碰撞和成员暂停后的访问失效。

MockMvc 或伪造 token 不能替代 L3。实验室必须限制 CPU/内存、随机化隔离网络/端口、只使用测试凭证、验证清理无残留，并记录运行时镜像 digest 与源码 SHA。Windows 浏览器域名映射必须作为一个完整的 quoted browser argument 传入；之前将映射拆成多个参数会表现为全部域名不可达。

## Risks / Trade-offs

- [第一批只支持动态 OIDC，协议覆盖有限] → 先获得可回滚的真实生产切片；SAML、CAS 分批复用同一 conformance suite。
- [Organization 模型先于 Directory 上线，管理员仍需手工预供给] → 明确这是认证批次；后续 provisioning 不改变已有 identity coordinate。
- [verified email/JIT 可能造成账号接管] → 默认关闭、要求已验证域名与高 assurance、歧义和历史成员 fail closed。
- [metadata/JWKS/token 出站请求可能形成 SSRF 或 DNS rebinding] → 每一跳仅允许 HTTPS，解析并校验全部地址，并将已验证地址固定到该次实际连接；保留原主机名用于 TLS SNI/hostname verification，禁止私网/回环/链路本地地址并限制 redirect/timeout/body。
- [公共 OAuth 统一核心门禁与 legacy binding 结果不一致] → ACTIVE 下在 active/pending legacy 写入前 fail closed，保留 LEGACY/SHADOW 回滚；V2 写入权威切换另立批次时再补新写入同步、backfill digest 与 dual-read 证据。
- [authority version 校验增加请求开销] → 查询使用索引并仅在企业边界执行；后续性能证据不足时不得扩大灰度。
- [七个 Flyway migration 增加发布复杂度] → 编号固定 V54–V60，做空库和升级库测试；当前批次不再新增 SQL，后续协议优先复用已有表。
- [Secret key 配置错误导致连接不可用] → 默认禁用企业 OIDC；启动校验 keyring，暴露不含 Secret 的健康信息，保留旧 revision 供回切。

## Migration Plan

以下步骤描述完整 Release 1 的运行时启用顺序。将当前完整参考实现重组为独立合并批次时，以 [rollout-plan.md](./rollout-plan.md) 为准；每个重组后的批次必须基于自己的 exact SHA 重新验证，不能直接继承完整参考分支的验证结论。

1. 在合并前冻结 source SHA，运行迁移护栏、全量后端、Web typecheck/lint/test、OpenAPI freshness 和架构检查。
2. 从 exact SHA 构建本地镜像，在空数据库验证 V1–V60，在 main 现有 V53 数据库验证 V54–V60 升级；确认公共 OAuth legacy binding 仍为持久化权威。
3. 保持 `identity core=LEGACY`、OIDC 控制面和登录数据面均关闭，验证 local/OAuth/Device/API Token 回归。
4. 开启 OIDC 控制面并切换统一身份核心到 ACTIVE，但保持登录数据面关闭；配置、测试并激活测试 Organization 的连接，同时观察公共登录成功率、fallback、冲突和异常日志。
5. 将测试 Organization 加入 allowlist，再仅打开 OIDC 登录数据面。
6. 完成真实浏览器登录、首次关联、重复登录、成员暂停、连接暂停、错误回调、重放和日志脱敏验收。
7. 小范围扩大 Organization allowlist；任何关键指标异常先移出 allowlist或关闭动态 OIDC，再按需切回 LEGACY。数据库保留，不执行降级删除。

回滚顺序：Organization allowlist → OIDC 登录数据面开关 → 身份核心 LEGACY；控制面可保留用于诊断，也可最后关闭。回滚只改变运行时开关，不删除 External Identity V2、session origin 或审计数据。

## Open Questions

无阻塞本批次实现的问题。SAML、CAS、LDAP 登录和 Directory/SCIM 的产品优先级在各自后续 OpenSpec 中决定，不改变本批次数据模型和 Adapter 边界。
