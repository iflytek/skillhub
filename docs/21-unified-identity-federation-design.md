# SkillHub 统一身份联邦设计

> 状态：Proposed
> 日期：2026-07-30
> 适用范围：外部身份登录、身份绑定、首次建号、资料同步、账号关联、企业身份接入
> 不包含：本地密码实现细节、API Token、平台 RBAC 重构、MFA/WebAuthn 的具体实现
> 权威关系：本设计批准后，外部身份接入以本文为准；`03-authentication-design.md`、
> `11-auth-extensibility-and-private-sso.md` 和 `12-private-sso-integration-playbook.md`
> 中允许 Provider 直接返回 `PlatformPrincipal` 的扩展方式视为待迁移的兼容设计。

## 1. 摘要

SkillHub 当前已经支持本地密码、GitHub/GitLab/OIDC、统一 Session、外部身份绑定，以及
Direct/Passive 两类私有 SSO 扩展点。但现有身份核心仍以 `OAuthClaims` 为中心，
Direct/Passive Provider 还能直接返回 `PlatformPrincipal`。这会让新增 LDAP、DingTalk、
CAS、SAML 或私有 SSO 时，协议实现有机会绕过准入、身份绑定、首次建号、账号状态、
资料同步和角色加载。

目标架构采用混合方案：

1. SkillHub 内部建设一个小接口、深实现的统一身份核心。
2. OAuth/OIDC、LDAP、DingTalk、CAS、SAML、可信代理等只负责验证各自协议并产出
   `ProviderAuthenticationResult`；由核心根据当前 Provider Instance 归一化为
   `IdentityAssertion`。
3. Provider 不得创建平台账号、决定绑定目标、授予平台角色或建立 Session。
4. 不用一个万能 `IdentityProvider.login()` 强行统一重定向、密码、Ticket、SAML POST、
   Header 和 SCIM。
5. OIDC、LDAP/AD、DingTalk、CAS 作为优先原生能力；SAML 先支持通过身份 Broker 接入，
   原生实现取决于明确需求和长期维护能力。
6. Kerberos/SPNEGO、WS-Federation、RADIUS、PAM 等长尾协议优先交给 Keycloak、
   authentik、Dex 或企业身份网关转换为 OIDC。
7. SCIM 2.0 是账号生命周期 Provisioning 协议，不是登录协议，使用独立链路。
8. 统一身份核心、安全不变量、数据库迁移和发布验收由维护者承担最终责任；协议 Adapter
   可以由外部贡献者实现。

目标数据流：

```text
OAuth/OIDC、DingTalk、LDAP、CAS、SAML、可信代理等协议 Adapter
        │
        │ 验证上游协议，产出外部身份事实
        ▼
ProviderAuthenticationResult
        │
        │ 应用层传入核心签发的 ResolvedProviderHandle
        ▼
ExternalIdentityLoginService
  ├─ Provider Descriptor / Authority Lock
  ├─ 内部 Factory 构造 IdentityAssertion
  ├─ Subject 与 Alias 解析
  ├─ Login Policy
  ├─ Provisioning Policy
  ├─ Identity Binding
  ├─ Profile Sync Policy
  ├─ Account State Guard
  ├─ PlatformPrincipal 构建
  └─ Audit / Metrics
        │
        │ 数据库事务成功提交
        ▼
PlatformSessionService
```

本地密码不是外部身份，继续由 `LocalAuthService` 处理，但复用统一的账号状态检查和
`PlatformPrincipal` 构建逻辑。

## 2. 背景与问题

### 2.1 当前实现

当前主要链路如下：

```text
OAuth2/OIDC
  → OAuthClaimsExtractor
  → OAuthClaims
  → AccessPolicy
  → IdentityBindingService.bindOrCreate()
  → PlatformPrincipal

Direct login
  → DirectAuthProvider.authenticate()
  → PlatformPrincipal

Passive session bootstrap
  → PassiveSessionAuthenticator.authenticate()
  → PlatformPrincipal
```

当前 `identity_binding` 使用 `(provider_code, subject)` 唯一约束，这是正确的基础，
但还不能表达一个外部身份的多种 typed subject，也不能防止同一个 `provider_code`
被重新配置到另一个身份域。

### 2.2 已确认的设计和安全缺口

以下问题来自当前代码检查，不是对未来实现的假设：

1. `OAuthClaims` 使准入和绑定接口只适用于 OAuth/OIDC。
2. `DirectAuthProvider` 和 `PassiveSessionAuthenticator` 直接返回
   `PlatformPrincipal`，可绕过统一账号决策。
3. `IdentityBindingService` 在每次外部登录时无条件覆盖 `displayName`，并在 email
   非空时覆盖平台 email，没有字段来源和同步策略。
4. `EmailDomainAccessPolicy` 当前只检查 email 字符串，没有检查 `emailVerified`。
5. 外部身份登录只显式处理 `PENDING`、`DISABLED`，没有统一拒绝 `MERGED` 和
   system account。
6. 当前绑定只支持一个无类型 `subject`，无法安全表达 DingTalk 的
   `unionId/openId/userId`、LDAP 的 `entryUUID/objectGUID` 等标识。
7. `provider_code` 没有固定到 issuer、directory authority、SAML entityID 等身份域；
   运维误把相同 code 指向另一个 IdP 时，旧绑定可能被错误复用。
8. 当前账号合并初始化接口把 verification token 直接返回给主账号会话，不能证明调用者
   拥有次账号。
9. PENDING 用户的管理员审批路径允许 `PENDING → ACTIVE`，但只修改账号状态，没有在
   同一事务补齐 `@global` membership，批准后的用户仍可能缺少基础成员关系。
10. 外部 Provider 的网络请求和平台数据库事务没有统一边界约束，LDAP 等实现容易把远程
    I/O 放入事务。

### 2.3 关联社区 PR

| PR | 能力 | 当前状态 | 本设计中的处理 |
|---|---|---|---|
| [#437](https://github.com/iflytek/skillhub/pull/437) | LDAP 登录 | Open，Changes Requested | 保持开放；统一核心稳定后邀请作者迁移到 LDAP Adapter |
| [#467](https://github.com/iflytek/skillhub/pull/467) | DingTalk OAuth2 | Open，作者已继续修复 | 保持开放；协议客户端和测试可复用，身份映射迁入统一核心 |
| [#464](https://github.com/iflytek/skillhub/pull/464) | CAS 2.0/3.0 | Closed | 保留为协议实现和测试参考，不以其通用 `IdentityClaims` 作为目标核心 |

这些 PR 提供了真实需求和实现经验，但不能让具体协议 PR 先定义平台级身份模型。

## 3. 目标、非目标和约束

### 3.1 目标

1. 所有外部身份入口执行相同的安全不变量。
2. 新增协议时不复制建号、绑定、角色、Session 和资料同步逻辑。
3. 支持一个平台账号绑定多个不同 Provider。
4. 支持一个外部身份包含多个有类型的稳定标识。
5. 禁止通过 email、username 或 display name 静默接管已有账号。
6. 明确区分登录准入、首次建号、资料同步、身份关联和账号合并。
7. 支持旧数据库、旧 OAuth 绑定和旧 Redis Session 平滑升级。
8. 为社区贡献者提供稳定、可测试的 Adapter 契约。
9. 对常见企业协议提供可执行的原生或 Broker 接入路径。
10. 让身份协议依赖可替换，避免 SkillHub 自行维护签名、XML、LDAP 生命周期等底层安全实现。

### 3.2 非目标

1. 第一阶段不支持上传第三方 JAR、运行时热加载或插件市场。
2. 第一阶段不在后台动态编辑和热更新 Provider secret。
3. 不把 SkillHub 建成 Keycloak 或 authentik 的完整替代品。
4. 不在统一身份核心内实现 OAuth、SAML、CAS、LDAP 的底层协议。
5. 不允许外部身份属性直接赋予 `SUPER_ADMIN` 等平台角色。
6. 不把 SCIM、MFA、WebAuthn、API Token 混入外部登录接口。
7. 不以“支持所有协议”为理由一次性引入没有部署需求和测试环境的实现。

### 3.3 当前技术约束

- Spring Boot 3.2.3、Java 21。
- Spring Security OAuth2 Client 已在使用。
- Spring Session + Redis 是 Web Session 的统一持久化方式。
- PostgreSQL + Flyway 是身份数据的权威存储和升级机制。
- `PlatformPrincipal` 已序列化到 Redis；第一阶段不改变其 record 字段结构。
- Provider 配置和 secret 继续使用 YAML、环境变量、Docker/Kubernetes Secret。
- 新功能默认关闭；没有完整配置的 Provider 不得出现在 `/api/v1/auth/methods`。

## 4. 外部开源项目与标准调研

本节只提取可用于 SkillHub 的架构原则，不代表复制对应项目的全部能力。

### 4.1 Keycloak

Keycloak 将外部 IdP 的协议验证、First Broker Login、账号创建和账号关联分开：

- Identity Broker 验证外部响应，然后决定创建本地用户或链接既有用户。
- First Login Flow 可以选择自动建号、禁止建号、确认关联、邮件验证或重新认证。
- 官方明确指出：仅因相同 email 自动关联既有账号是潜在安全漏洞。
- LDAP/AD 属于 User Federation，并提供属性 Mapper、导入/非导入和同步模式。
- Kerberos/SPNEGO 与 LDAP federation 是独立但可组合的能力。
- User Storage SPI 采用 capability interfaces，不要求所有 Provider 实现同一组方法。

SkillHub 借鉴：

1. 首次外部登录必须是显式、可配置的流程。
2. 账号关联必须重新证明已有账号的所有权。
3. 登录、目录联邦、属性映射不是同一个职责。
4. Provider 能力按实际交互类型拆分。

SkillHub 不照搬：

- 不实现可视化认证 Flow 编排器。
- 不实现完整的外部用户存储虚拟化。
- 不在第一阶段支持运行时安装任意 SPI。

参考：

- [Keycloak Identity Brokering](https://www.keycloak.org/docs/latest/server_admin/#_identity_broker)
- [Keycloak First Broker Login](https://www.keycloak.org/docs/latest/server_admin/#_identity_broker_first_login)
- [Keycloak User Storage Federation](https://www.keycloak.org/docs/latest/server_admin/#_user-storage-federation)
- [Keycloak Provider capability interfaces](https://www.keycloak.org/docs/latest/server_development/#_provider_capability_interfaces)

### 4.2 authentik

authentik 明确区分：

- Source：LDAP、OAuth、SAML、SCIM、Kerberos、社交登录等外部身份来源。
- Provider：向下游应用提供 OAuth2/OIDC、SAML、Proxy、LDAP、RADIUS、SCIM 等协议。
- Flow/Policy：控制登录、准入和用户交互。
- Property Mapping：控制属性如何进入统一用户模型。
- SCIM Provider：作为 backchannel Provisioning，与主登录 Provider 并存。

SkillHub 借鉴：

1. 身份来源、向应用提供登录协议和生命周期同步是不同模型。
2. 属性映射和访问策略应该独立于协议客户端。
3. SCIM 不建立浏览器 Session。

SkillHub 不照搬：

- 不建立通用 Flow/Stage 表达式引擎。
- 不同时充当企业 IdP 和所有下游应用的身份中枢。

参考：

- [authentik Sources](https://docs.goauthentik.io/users-sources/sources/)
- [authentik SCIM Provider](https://docs.goauthentik.io/add-secure-apps/providers/scim/)

### 4.3 Dex

Dex 作为 OIDC shim，将 LDAP、GitHub、SAML、OIDC 等上游身份转换为下游统一 OIDC。
Dex 没有一个万能 Connector 登录方法，而是区分：

- `PasswordConnector`
- `CallbackConnector`
- `SAMLConnector`
- `RefreshConnector`
- `TokenIdentityConnector`
- `LogoutCallbackConnector`

各 Connector 最终产生统一 `Identity`。

SkillHub 借鉴：

1. 按交互能力拆 Adapter，不强迫 LDAP 理解浏览器 Callback，也不强迫 SAML 接受密码。
2. 长尾协议可以通过独立 Broker 转换为 OIDC，SkillHub 不需要原生维护全部协议。

SkillHub 不照搬：

- 不把 SkillHub 本身变成通用 OIDC Provider。
- 默认不保存上游 access token、refresh token 或 Connector 私有凭证。

参考：

- [Dex Connectors](https://dexidp.io/docs/connectors/)
- [Dex connector interfaces](https://github.com/dexidp/dex/blob/master/connector/connector.go)

### 4.4 Authelia

Authelia 将 LDAP authentication backend、OIDC Provider、反向代理授权和 Trusted Header
集成拆开。LDAP 配置显式包含目录实现、filter、属性映射、TLS、超时和 group 查询策略；
反向代理模式强调 forwarded header 和可信代理配置。

SkillHub 借鉴：

1. LDAP 不只是一个 URL；需要实现类型、filter、属性和 TLS 的完整配置模型。
2. Trusted Header 必须建立可信代理边界，不能仅因请求带有 `X-User` 就信任。
3. 认证和反向代理授权不应混成一个 Provider 方法。

参考：

- [Authelia LDAP backend](https://www.authelia.com/configuration/first-factor/ldap/)
- [Authelia proxy integration](https://www.authelia.com/integration/proxies/introduction/)
- [Authelia OIDC Provider](https://www.authelia.com/integration/openid-connect/introduction/)

### 4.5 Backstage、Grafana 与 Kubernetes

这些项目提供了两个重要的反例和部署边界：

- Backstage 的 sign-in resolver 负责把外部身份映射为平台 identity；官方文档提醒错误的
  resolver 会导致未经授权的访问。
- Grafana Auth Proxy 可以按 Header 自动建号，但必须把 Grafana 放在认证代理之后，并
  提供代理 whitelist 等边界。
- Kubernetes authenticating proxy 只信任由专用 CA 验证的代理客户端证书，再接受
  `X-Remote-User` 等 Header。

SkillHub 的结论：

1. 身份映射是安全核心，不是普通字段转换。
2. Trusted Header 必须配合网络隔离、Header 清洗，以及 mTLS 或签名 assertion。
3. 不能通过 `X-Forwarded-For` 判断请求是否来自可信代理。

参考：

- [Backstage Sign-in Identities and Resolvers](https://backstage.io/docs/auth/identity-resolver/)
- [Grafana Auth Proxy](https://grafana.com/docs/grafana/latest/setup-grafana/configure-access/configure-authentication/auth-proxy/)
- [Kubernetes Authenticating Proxy](https://kubernetes.io/docs/reference/access-authn-authz/authentication/#authenticating-proxy)

### 4.6 Spring Security

Spring Security 为 OAuth2/OIDC Client、SAML 2.0 Service Provider、LDAP 和 CAS 提供独立
集成。它验证了“不同协议使用不同 transport/authentication integration，业务身份映射
汇聚到统一核心”的方向。

实现阶段必须先做当前 Spring Boot/Security 版本兼容性 spike，再选择模块和库；本设计
不提前写死尚未验证的依赖版本。

参考：

- [Spring Security OAuth2/OIDC](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [Spring Security SAML2](https://docs.spring.io/spring-security/reference/servlet/saml2/index.html)
- [Spring Security LDAP](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/ldap.html)
- [Spring Security CAS](https://docs.spring.io/spring-security/reference/servlet/authentication/cas.html)

### 4.7 标准协议

- OIDC 的 `sub` 是 issuer 内局部唯一、不会重新分配且区分大小写的标识，因此真正身份键
  至少是 `(issuer, sub)`，不能只存一个全局 `sub`。
- SCIM 2.0 定义 User/Group schema 以及基于 HTTP 的创建、查询、更新、禁用/删除和发现，
  不定义浏览器登录。
- CAS 是基于 Service Ticket 的 SSO 协议，Ticket 验证必须绑定精确 service。

参考：

- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [RFC 7643: SCIM Core Schema](https://www.rfc-editor.org/rfc/rfc7643)
- [RFC 7644: SCIM Protocol](https://www.rfc-editor.org/rfc/rfc7644)
- [Apereo CAS Protocol](https://apereo.github.io/cas/7.3.x/protocol/CAS-Protocol.html)

## 5. 方案比较与决定

### 5.1 方案 A：最小统一身份核心

所有协议自行完成交互，产出不含平台身份的 `ProviderAuthenticationResult`；核心将其
绑定到当前 Provider Instance 并构造 `IdentityAssertion`，然后调用统一登录入口。

优点：

- 公共接口最小。
- 可以较快迁移现有 OAuth。
- 核心能够集中执行安全规则。

缺点：

- Provider 发现、配置状态和 `/auth/methods` 容易继续散落。
- 社区 Adapter 的契约不够完整。

### 5.2 方案 B：完整能力型插件体系

增加 Provider Driver、Provider Instance、Browser/Credential/Passive/Pull 等 capability
interface，支持多个 Provider 实例和统一能力协商。

优点：

- 扩展模型完整。
- 适合大量 Provider 和企业私有扩展。
- 可以统一 Provider 状态、UI 目录和契约测试。

缺点：

- 第一阶段引入新 Maven 模块、SPI 版本、配置 schema 和能力协商，范围过大。
- 只有少数 Adapter 时容易形成尚未证明价值的浅层接口。
- 运行时插件还会引入类加载和供应链问题。

### 5.3 方案 C：身份 Broker 优先

SkillHub 只原生支持 OIDC，把 LDAP、CAS、SAML、Kerberos、DingTalk 等全部交给 Keycloak、
authentik 或 Dex。

优点：

- SkillHub 协议安全面最小。
- 长尾协议交给成熟项目维护。

缺点：

- 简单自托管用户也必须额外部署身份组件。
- LDAP、CAS、DingTalk 是当前明确需求，完全 Broker-only 会降低产品可用性。
- 私有环境不一定允许增加 Broker。

### 5.4 决定：渐进式混合方案

采用 A 的深核心作为第一阶段，在实际出现第二类 Adapter 后逐步吸收 B 的 Provider
Registry 和 capability；同时使用 C 处理长尾和高运维成本协议。

决定如下：

1. 第一个核心架构阶段（在 P0 安全热修之后）只引入统一身份核心并迁移现有
   OAuth/OIDC，不增加新协议。总范围统一由父 issue #628 跟踪，#630 只作为设计 PR；
   后续实现按阶段逐个创建 PR、逐个验证并合入 `main`，不再拆出子 issue，也不提前批量
   打开多个并行实现 PR。
2. Provider Adapter 按 Browser、Credential、Passive 等交互类型分别集成，不建立万能
   `authenticate(Object credentials)`。
3. 第一阶段是构建时可信模块，不提供运行时第三方 JAR。
4. Provider Registry 在核心接口稳定后引入，负责实例、authority、状态、UI 投影和
   conformance。
5. 原生支持与 Broker 支持并存；Broker 最终仍通过 OIDC/可信 assertion 进入同一核心。

这个决定把复杂性集中在统一身份核心，调用方只需要知道一个外部登录用例入口；同时避免
第一阶段先建设一个没有足够 Adapter 证明价值的完整插件平台。

## 6. 统一领域语言

### 6.1 Platform Account

SkillHub 内的 `UserAccount`。它拥有平台 userId、状态、平台角色、Namespace membership、
API Token 和业务数据。

避免称为：外部用户、OAuth 用户、LDAP 用户。

System Account 是 `UserAccount.system_account = true` 的内部服务账号，对应现有
`UserAccount.isSystemAccount()`；它不是靠 userId 前缀或角色推断。System Account 不得
用于本地或外部交互式登录、Identity Link 或 Account Merge。

### 6.2 Provider Instance

一个具体配置的外部身份源实例，而不是协议类型。示例：

- `github`
- `corp-oidc`
- `corp-ldap`
- `dingtalk-prod`
- `cas-main`

`oidc`、`ldap`、`saml` 只表示协议，不足以作为多个身份源并存时的实例标识。

### 6.3 Authority

Provider Instance 所代表的稳定身份域：

- OIDC：规范化后的 issuer。
- SAML：IdP entityID。
- LDAP/AD：管理员配置的稳定 directory authority id。
- DingTalk：应用/企业身份域。
- CAS：管理员固定的 CAS realm/service identity。
- Trusted Header：可信代理或签名 assertion issuer。

endpoint、证书和 secret 可以轮换；Authority 不得静默改变。

#### Authority 规范化与指纹

Authority 不是登录 endpoint 的通用 URL 归一化结果，而是协议验证后或管理员配置中的
稳定身份域。指纹算法固定为：

```text
fingerprint =
  lowercaseHex(
    SHA-256(
      UTF-8(protocol + "\n" + canonicalAuthority)
    )
  )
```

其中 `protocol` 使用 SkillHub 冻结的稳定小写协议 code：

```text
oauth2-github
oauth2-gitlab
oidc
ldap
dingtalk-oauth2
cas
saml2
trusted-gateway
```

已经产生 Authority Lock 后不能重命名 protocol code；新增版本只能通过显式数据迁移。
`canonicalAuthority` 按以下规则取得：

- OIDC issuer、SAML entityID：使用协议校验后的精确值，不擅自 lowercase、去尾斜杠或
  做 URL 等价改写。
- 公共 GitHub：固定为 ASCII 字符串 `https://github.com`。
- 公共 GitLab：固定为 ASCII 字符串 `https://gitlab.com`。
- GitHub Enterprise/GitLab Self-Managed：使用管理员配置并经协议校验的实例
  Authority。
- LDAP/AD：使用管理员配置字段 `authority` 中的稳定目录 ID，不使用可能切换的主机名、
  端口或负载均衡 endpoint。
- CAS：使用管理员配置字段 `authority` 中的稳定 CAS ID；login/validation endpoint
  可以轮换。
- DingTalk：使用受信配置中的企业/应用身份域；`unionId/openId/userId` 对应的精确范围
  必须在 DingTalk Adapter 实现前通过官方协议和真实租户 fixture 固化。
- Trusted Gateway：使用验证过的签名 issuer 或 mTLS 客户端身份，不使用普通 Header
  值。

管理员自定义的稳定 ID 必须是 1–128 位小写 ASCII，匹配
`[a-z0-9][a-z0-9._:-]{0,127}`；不能包含空白。读取配置时不静默 trim 或 lowercase，
非法值直接判定为 `MISCONFIGURED`。OIDC issuer、SAML entityID 和 HTTPS Authority 则按
对应协议验证后的 UTF-8 精确字节参与 hash。

阶段 1 必须固化以下测试向量，避免不同实现生成不同数据库值：

| protocol | canonicalAuthority | SHA-256 fingerprint |
|---|---|---|
| `oidc` | `https://id.example.com` | `4d12ea0e7a413a716adf2acf2434f2a0642e74abcd3e3273bad170127c53fd00` |
| `oauth2-github` | `https://github.com` | `b2a93d58465e3de9e8b6cd127ba18425ae0f80c49c85f18f76086832923ca619` |

首次持久化 pin 使用数据库 compare-and-set，而不是应用内“先查再写”：

```text
1. 新 Provider：尝试插入 (providerCode, protocol, authority, fingerprint, READY)。
2. 已存在 LEGACY_UNPINNED：仅在 state=LEGACY_UNPINNED 且 fingerprint IS NULL 时更新。
3. 插入或更新未生效时，重新读取数据库记录。
4. fingerprint 相同：多个 Pod 收敛到同一结果。
5. fingerprint 不同：不得 last-write-wins，保留原 Authority/fingerprint，以条件更新把
   Provider 置为 AUTHORITY_MISMATCH 并 fail closed。
```

compare-and-set 和结果复读必须位于同一短数据库事务中。Authority 的显式迁移是独立运维
操作，不复用首次 pin 路径。`AUTHORITY_MISMATCH` 是粘性状态，不能因仍存活的旧配置 Pod
再次请求而自动恢复，避免不同配置 Pod 之间反复切换状态。运维把所有 Pod 配置恢复为已
pin 的 fingerprint 后，执行“同 Authority 恢复”操作；该操作只在当前 descriptor
fingerprint 等于数据库已 pin 值且 state 仍为 `AUTHORITY_MISMATCH` 时 compare-and-set
回 `READY`，不修改 Authority/fingerprint，并写恢复审计。若配置仍是新 fingerprint，
只能使用待设计的显式 Authority 迁移操作。

### 6.4 External Subject

外部身份域内稳定、不可变的主体标识，由 `subjectType + subjectValue` 表达。email、
username、display name 默认不是稳定 External Subject。

### 6.5 Subject Alias

同一个已验证外部身份的其他稳定标识。例如同一次 DingTalk 响应中的 `unionId`、
`openId` 和 `userId`。Alias 用于标识演进和兼容，不代表另一个平台账号。

### 6.6 Identity Assertion

协议 Adapter 完成签名、Ticket、密码、Header 或上游响应验证后，先返回
`ProviderAuthenticationResult`。核心拥有的 `IdentityAssertionFactory` 再把结果绑定到
受信 Provider Instance，并构造不可变 `IdentityAssertion`。Assertion 不是平台账号，
也不包含平台角色。

### 6.7 Identity Binding

一个 Provider Instance 中的外部身份与一个 Platform Account 的显式关联。

### 6.8 Login Policy

每次外部登录都执行的准入规则，例如 Provider 是否启用、账号是否可登录、verified
email domain 是否满足要求。

### 6.9 Provisioning Policy

只在外部身份尚未绑定时执行的首次建号规则：

- `AUTO`
- `APPROVAL`
- `EXISTING_BINDING_ONLY`

### 6.10 Profile Sync Policy

控制外部 profile 字段何时能初始化或覆盖平台资料的规则。

### 6.11 Identity Link

已登录 Platform Account 在重新认证后增加一种外部登录身份。

### 6.12 Account Merge

把两个已经存在且都拥有业务数据的 Platform Account 合并为一个账号。它不同于首次登录
时的 Identity Link。

### 6.13 Provisioning

通过 SCIM 或目录同步创建、更新、禁用账号和群组的生命周期管理。Provisioning 不建立
浏览器 Session。

## 7. 安全不变量

以下规则必须通过核心代码、数据库约束和测试保证，不能只写在文档中。

### 7.1 Provider 和 Authority

1. `providerCode` 必须唯一标识 Provider Instance。
2. Provider 产生 binding 后，必须固定到 Authority。
3. endpoint、secret 或证书轮换不得改变 Authority。
4. Authority 变化必须 fail closed，并通过显式迁移操作处理。
5. Provider disabled 或 misconfigured 时不得出现在登录方法目录，也不得建立远程连接。

### 7.2 Subject

1. 唯一身份键是：

   ```text
   providerCode + subjectType + canonicalSubjectValue
   ```

2. Subject type 的规范化算法由对应协议 Adapter 的受信 descriptor 定义，但由核心
   `IdentityAssertionFactory` 选择并执行；核心不能对所有类型统一 lowercase。
3. email、username、display name 和 LDAP DN 默认不能作为稳定 Subject。
4. 多个 Subject 命中不同账号时立即返回冲突，绝不自动合并。
5. 首次登录只允许保存同一次已验证 Assertion 中的 Alias。
6. 后续补充 Alias 时，至少一个已有 Subject 必须先命中相同 Binding。

### 7.3 Email

1. email 是资料和准入属性，不是默认身份主键。
2. email 相同不能触发静默绑定或账号合并。
3. `EMAIL_DOMAIN` 只接受 `VERIFIED` 或 `AUTHORITATIVE` email。
4. 未验证 email 不能覆盖已有可信 email。
5. 合成 email 只能作为展示/兼容占位，必须标记为非真实、非 verified，不能参加准入。

### 7.4 账号和角色

1. Provider 不能指定 SkillHub userId。
2. Provider 不能返回 `PlatformPrincipal`。
3. Provider 不能直接设置平台角色或 Namespace role。
4. Provider groups/entitlements 如需映射，必须经过管理员配置的独立映射层。
5. 默认永远不能把外部 group 映射为 `SUPER_ADMIN`。
6. `PENDING`、`DISABLED`、`MERGED`、system account 必须统一拒绝建立普通用户 Session。

### 7.5 Session 和事务

1. 只有 `Authenticated` outcome 可以建立 Session。
2. Session 必须在身份事务成功提交后建立。
3. 所有 Web 登录方式最终调用 `PlatformSessionService`。
4. 外部网络请求必须在数据库事务外执行。
5. Provider 不操作 `HttpSession`、`SecurityContext` 或 Redis。

### 7.6 凭证和敏感数据

以下内容不得进入 Assertion、数据库普通字段、审计 detail 或日志：

- OAuth access token / refresh token
- authorization code
- LDAP password
- bind password
- CAS Ticket
- SAML 原始 assertion
- 企业 SSO Cookie
- 完整上游响应
- Client secret

### 7.7 关联、解绑和合并

1. Identity Link 必须从已认证账号发起并重新认证目标外部身份。
2. Account Merge 必须分别证明主账号和次账号的控制权。
3. verification token 不能直接返回给尚未证明次账号所有权的主账号。
4. 解绑最后一种可用登录方式必须拒绝。
5. 合并完成后必须撤销次账号 Session，并按明确策略处理 API Token。

## 8. 目标架构

### 8.1 模块与 Seam

```text
┌──────────────────────────────────────────────────────────────┐
│ Protocol / Transport Adapters                                │
│ OAuth/OIDC | DingTalk | LDAP | CAS | SAML | Trusted Gateway │
└──────────────────────────────┬───────────────────────────────┘
                               │ ProviderAuthenticationResult
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Unified Identity Core                                       │
│ ExternalIdentityLoginService Facade                         │
│  ├─ Descriptor / Authority Lock                             │
│  ├─ Internal Assertion Factory                              │
│  ├─ Assertion validation                                    │
│  ├─ Subject resolution                                      │
│  ├─ Login policy                                            │
│  ├─ Provisioning policy                                     │
│  ├─ Binding persistence                                     │
│  ├─ Profile synchronization                                 │
│  ├─ Account guard                                           │
│  └─ Principal / audit                                       │
└──────────────────────────────┬───────────────────────────────┘
                               │ IdentityLoginOutcome
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Application / Session                                       │
│ Login controller / success handler → PlatformSessionService │
└──────────────────────────────────────────────────────────────┘
```

统一身份核心是深 Module：外部只看到一个登录用例 Interface，复杂的解析、绑定、建号、
同步、状态和角色逻辑隐藏在实现内部。

### 8.2 核心公开 Interface

```java
public interface ExternalIdentityLoginService {

    IdentityLoginOutcome authenticate(
        ResolvedProviderHandle provider,
        ProviderAuthenticationResult result,
        IdentityLoginContext context
    );
}
```

这是应用层唯一可调用的外部身份登录 Facade。OAuth success handler 或未来统一 Provider
handler 必须先通过核心拥有的 `TrustedProviderRouteResolver` 解析当前服务端路由，取得
`ResolvedProviderHandle`；协议 Adapter 只返回 `ProviderAuthenticationResult`，不持有
此 Service，也不直接调用核心。Handle 的实现类型和构造器保持 package-private，公开
Interface 只允许消费核心签发的实例，避免把未经证明来源的普通字符串误当成受信
`providerCode`。

```java
public sealed interface ResolvedProviderHandle
    permits DefaultResolvedProviderHandle {

    String providerCode();
}

interface TrustedProviderRouteResolver {

    ResolvedProviderHandle resolve(TrustedRouteIdentity route);
}
```

`TrustedRouteIdentity` 由服务端注册的 callback path、`ClientRegistration` 或管理员启用的
Provider route 构造，不能来自 query、Header、Token claim 或 Adapter 返回值。阶段 1 增加
ArchUnit/模块依赖测试，保证 Adapter package 不能依赖 Handle 实现、Assertion Factory、
Principal Factory 或 Session package。

`DefaultExternalIdentityLoginService` 的内部顺序固定为：

```text
ResolvedProviderHandle + ProviderAuthenticationResult
  → TrustedProviderDescriptorSource.require(handle)
  → ProviderAuthorityLockService.requirePinnedAuthority()
  → package-private IdentityAssertionFactory
  → package-private IdentityAssertion
  → package-private IdentityResolutionTransaction
  → IdentityLoginOutcome
```

`IdentityAssertionFactory`、`IdentityAssertion`、`ProviderReference`、
`ExternalSubject` 和事务服务放在核心内部 package，不作为 Provider SPI 导出；示例中的
Java 定义是领域结构说明，不表示它们必须是 `public`。阶段 4 拆出 Provider SPI 时，SPI
模块只导出 `ProviderAuthenticationResult` 及其输入值对象，不能导出 Assertion 构造
路径。

这样阶段 1 即使尚未拆 Maven 模块，也能通过 Java package 可见性防止正常代码直接
`new IdentityAssertion(...)`。运行在同一 JVM 内的恶意可信模块仍可使用反射，因此真正
的不可信集成必须继续放在外部 Broker；本方案不宣称 package 可见性是安全沙箱。

```java
public record IdentityLoginContext(
    String requestId,
    String clientIp,
    String userAgent
) {}
```

`IdentityLoginContext` 只包含审计所需的请求元数据，不持有 `HttpServletRequest` 或
`HttpSession`。HTTP 入口负责提取这些字段；非 HTTP 调用可以使用明确的 system context。

第一阶段不要向 Provider 公开：

- `bindOrCreate`
- `createPendingUserIfAbsent`
- `IdentityAssertionFactory`
- `IdentityAssertion`
- `PlatformPrincipalFactory`
- `ProfileSynchronizer`
- `IdentitySubjectResolver`
- `ProvisioningService`

这些是核心内部协作对象，不是 Provider SPI。

### 8.3 ProviderAuthenticationResult 与 Assertion Factory

协议 Adapter 不能自行声明 `providerCode`、Authority、SkillHub userId 或平台角色，只返回
经过协议验证的外部事实：

```java
public record ProviderAuthenticationResult(
    SubjectCandidate primarySubject,
    List<SubjectCandidate> alternateSubjects,
    Map<String, List<ProviderAttributeValue>> attributes,
    ProtocolAuthenticationEvidence evidence
) {}
```

```java
public record SubjectCandidate(
    String type,
    String value
) {}

public record ProviderAttributeValue(
    String value,
    ProviderAttributeTrust trust
) {}

public enum ProviderAttributeTrust {
    UNVERIFIED,
    ASSERTED,
    VERIFIED
}

public record ProtocolAuthenticationEvidence(
    String protocol,
    Instant authenticatedAt,
    Set<String> authenticationMethods
) {}
```

语义：

- `SubjectCandidate` 是 Adapter 从已验证协议响应中提取的候选值，尚未成为平台 Subject。
- `alternateSubjects` 使用 `List`，使 Factory 能检测重复值和不稳定顺序；Factory
  规范化成功后才生成 `Set<ExternalSubject>`。
- `ProviderAttributeValue.trust` 只陈述协议事实，不是最终平台 assurance。
- `ProtocolAuthenticationEvidence.protocol` 必须与受信 descriptor 完全一致；Factory
  使用 descriptor 的 protocol 构造最终 `AuthenticationEvidence`，不能接受 Adapter
  覆盖。
- `ProtocolAuthenticationEvidence` 不包含原始 Token、Ticket、Cookie 或密码。

核心拥有 `IdentityAssertionFactory`，从当前受信路由和
`TrustedProviderDescriptorSource` 取得 Provider Instance；阶段 4 之后该 Source 由完整
Provider Registry 提供。Factory 执行：

1. 固定 `providerCode` 和 Authority，忽略上游响应中的同名字段。
2. 校验 Subject type 是否在该 Provider 的 allowlist。
3. 规范化 Subject，并限制字段数量、长度和总大小。
4. 把协议声明的 email 可信度限制在 Adapter 和管理员配置允许的上限内。
5. 丢弃未声明属性和敏感字段。
6. 构造不可变 `IdentityAssertion`。

原始属性到平台字段的转换来自受信 Provider descriptor：

```text
Provider attribute key
  → descriptor AttributeMapping
  → displayName / email / avatarUrl / allowed mapped attribute
  → AttributeRule + Provider Instance assurance policy
  → ExternalProfile + mappedAttributes
```

未知属性直接丢弃。上游响应里的 `providerCode`、role、userId 或 assurance 字段不能覆盖
descriptor。

这一步防止错误或恶意 Adapter 通过伪造另一个 Provider code，命中不属于自己的历史
Binding。

Factory 是 Facade 内部协作者，其输入必须显式区分可信路由事实和不可信上游结果。

`ResolvedProviderHandle` 必须来自已经成功解析的服务端 Provider 路由或配置。Facade
再通过核心内部 seam 取得 descriptor：

```java
interface TrustedProviderDescriptorSource {

    ProviderDescriptor require(ResolvedProviderHandle provider);
}
```

`ProviderDescriptor` 至少固定 protocol、canonical Authority、允许的 Subject type、
每种 Subject 的 canonicalizer，以及 11.3 节定义的 `AttributeRule` 和 Provider Instance
assurance policy。

阶段 1 尚未建设完整 Provider Registry，因此先提供一个仅覆盖现有 GitHub/GitLab/OIDC
配置的静态实现，并由 `ProviderAuthorityLockService` 负责持久化 Authority：

- Provider code 必须能解析到现有 Spring Security `ClientRegistration`。
- protocol、Authority 和 claims extractor 来自服务端受信配置及内置规则。
- 未注册、重复或无法唯一确定 Authority 的配置在启动时或首次使用时 fail closed。
- 阶段 1 使用最小 `identity_provider_state` 表完成 Authority pin 和跨重启变化检测。
- 兼容期 `AuthMethodCatalog` 必须使用同一 descriptor source 和 Authority 状态过滤登录
  方法；未注册、歧义、未 pin 或 mismatch 的 Provider 不得展示，也不能等到 callback
  才失败。
- 阶段 1 对非空 `alternateSubjects` fail closed；阶段 2 完成 typed Subject 表和冲突约束后
  才允许保存 Alias。
- 阶段 1 启动 reconciliation 的顺序固定为：读取唯一 descriptor source → 对每个启用的
  Provider 执行 Authority compare-and-set pin → 重新读取持久化状态 → 投影 Provider
  state 和 `AuthMethodCatalog`。Catalog 不能直接投影尚未复读的内存配置，也不能在 pin
  失败时保留旧的 READY 项。
- 阶段 1 通过 compatibility provisioning adapter 保持现有 GitHub/GitLab/OIDC 的建号、
  PENDING 和 membership 行为；它只是迁移入口，不引入第二套 Policy。阶段 3 上线正式
  `ProvisioningPolicy` 后删除 compatibility adapter，并用行为回归测试证明切换等价。

阶段 4 用完整 Provider Registry 替换该静态实现，但保留
`TrustedProviderDescriptorSource` 和 `ProviderAuthorityLockService` 语义，避免
Facade 反向依赖具体注册中心。

### 8.4 IdentityAssertion

建议模型：

```java
record IdentityAssertion(
    ProviderReference provider,
    ExternalSubject primarySubject,
    Set<ExternalSubject> alternateSubjects,
    ExternalProfile profile,
    Map<String, List<String>> mappedAttributes,
    AuthenticationEvidence evidence
) {}
```

```java
record ProviderReference(
    String providerCode,
    String protocol,
    String authority
) {}
```

```java
record ExternalSubject(
    String type,
    String value
) {}
```

```java
record ExternalProfile(
    String displayName,
    Optional<EmailClaim> email,
    URI avatarUrl
) {}
```

```java
record EmailClaim(
    String value,
    EmailAssurance assurance
) {}

enum EmailAssurance {
    UNVERIFIED,
    PROVIDER_ASSERTED,
    VERIFIED,
    AUTHORITATIVE
}
```

```java
record AuthenticationEvidence(
    String protocol,
    Instant authenticatedAt,
    Set<String> authenticationMethods
) {}
```

所有 record 的 canonical constructor 必须：

- 对必填值执行 `Objects.requireNonNull`。
- 对字符串执行类型特定的空值、长度和格式校验。
- 使用 `List.copyOf`、`Set.copyOf`、`Map.copyOf`。
- 对 `Map<String, List<...>>` 的每个嵌套 List 也执行 `List.copyOf`，不能只复制外层 Map。
- 拒绝 primary 与 alternate 重复、规范化后重复，以及不允许的 Subject type。
- 通过核心拥有的 `ProviderAssertionLimits` 限制 Subject 数量、属性数量、单值长度和
  总载荷；Adapter 不能自行放宽。
- email 缺失统一表示为 `Optional.empty()`；核心、Policy、Profile Sync 和 Adapter
  契约不得再混用 `null`、空字符串或带 `UNVERIFIED` 的空 `EmailClaim`。

因此“不可变”包含集合的防御性复制，不只是使用 Java record 语法。

`AuthenticationEvidence` 只保留非敏感审计事实；不得保存原始 Token、Ticket 或密码。

### 8.5 Subject 示例

| Provider | Primary Subject | 可选 Alias | 禁止默认使用 |
|---|---|---|---|
| GitHub | `github_user_id` | 无 | login、email |
| GitLab | `gitlab_user_id` | 无 | username、email |
| OIDC | `oidc_sub` | 协议明确的稳定标识 | email、preferred_username |
| OpenLDAP | `ldap_entry_uuid` | 配置确认的不可变属性 | uid、DN、mail |
| Active Directory | `ad_object_guid` | `ad_object_sid` | sAMAccountName、DN、mail |
| DingTalk | `dingtalk_union_id` | `dingtalk_open_id`、`dingtalk_user_id` | email |
| CAS | `cas_principal` 或配置的 immutable attribute | 无 | 未确认稳定的 email |
| SAML | `saml_persistent_name_id` | immutable attribute | transient NameID、email |
| Kerberos | `kerberos_principal`，包含 realm | AD objectGUID | 无 realm 短用户名 |
| Trusted Gateway | `gateway_subject` | 无 | 裸 email Header |

### 8.6 登录结果

```java
public sealed interface IdentityLoginOutcome {

    record Authenticated(
        PlatformPrincipal principal,
        boolean accountCreated,
        boolean bindingCreated
    ) implements IdentityLoginOutcome {}

    record PendingApproval(
        String reasonCode
    ) implements IdentityLoginOutcome {}

    record LinkRequired(
        String reasonCode
    ) implements IdentityLoginOutcome {}
}
```

预期业务分支使用 outcome；上游失败、安全冲突和系统错误使用稳定错误码。

首次创建 PENDING Account，以及后续已绑定 PENDING Account 再次登录，都返回
`PendingApproval`。`ACCOUNT_PENDING` 是该 outcome 的 `reasonCode` 和审计 code，不作为
异常路径；Web 层统一映射到 403/等待审批提示页，且不得建立 Session。

`LinkRequired` 只返回稳定 reason code，不返回目标账号或可直接完成绑定的 token。阶段 5
中的显式 Link 流程必须在用户认证现有账号后重新认证目标 Provider，不依赖首次碰撞请求
携带外部凭证。

### 8.7 错误分类

| 错误码 | 语义 | 对外行为 |
|---|---|---|
| `PROVIDER_DISABLED` | Provider 未启用 | 403/隐藏登录入口 |
| `PROVIDER_AUTHORITY_MISMATCH` | Provider code 指向了不同身份域 | 503，运维处理 |
| `INVALID_IDENTITY_ASSERTION` | Assertion 缺字段或超限 | 401/503，按来源分类 |
| `IDENTITY_SUBJECT_MISSING` | 没有稳定 Subject | 401 |
| `IDENTITY_IDENTIFIER_CONFLICT` | 多个 Subject 命中不同账号 | 409，不泄露账号 |
| `ACCESS_DENIED` | Login Policy 拒绝 | 403 |
| `ACCOUNT_PENDING` | `PendingApproval` 的原因/审计 code | 403/等待审批提示页 |
| `ACCOUNT_DISABLED` | 账号禁用 | 403 |
| `ACCOUNT_MERGED` | 账号已合并 | 403 |
| `SYSTEM_ACCOUNT_FORBIDDEN` | system account 不可登录/绑定 | 403 |
| `UPSTREAM_INVALID_CREDENTIALS` | 密码或 Ticket 无效 | 401 |
| `UPSTREAM_UNAVAILABLE` | 上游不可用/超时 | 503 |
| `UPSTREAM_MISCONFIGURED` | 配置错误 | 503 |
| `TLS_VALIDATION_FAILED` | TLS/证书不可信 | 503 |
| `REPLAY_DETECTED` | Ticket/Assertion/State 重放 | 401 + 审计 |
| `RATE_LIMITED` | 登录限流 | 429 |

对用户不得区分“用户名不存在”和“密码错误”；运维日志可以记录脱敏后的内部分类。

### 8.8 核心内部流程

```text
1. 使用核心签发的 Handle 解析 descriptor，验证 Provider 已启用且处于 READY。
2. 执行 Authority Lock，再由 Factory 校验和规范化 Subject、Alias、email 与属性。
3. 一次查询 Assertion 中的全部 Subject；命中不同 Binding 时 fail closed，并通过独立
   审计事务或可靠 outbox 保留冲突事件。
4. 若命中 Binding，先加载 Platform Account，再执行 Account Guard；PENDING、DISABLED、
   MERGED、system 的结果必须在任何资料写入前确定。
5. 使用已经确定的 `NEW_IDENTITY/RETURNING_IDENTITY`、Account 状态和 Assertion 构造
   Login Policy Context，然后执行每次登录的 Login Policy。
6. 若已有 Binding 且 Guard/Policy 允许，按 Profile Sync Policy 更新允许字段。
7. 若没有 Binding：
   7.1 执行 Provisioning Policy。
   7.2 检查 email collision，但不自动绑定。
   7.3 AUTO：创建 ACTIVE Account + Binding + Subjects + @global member。
   7.4 APPROVAL：创建 PENDING Account + Binding，不建立 Session。
   7.5 EXISTING_BINDING_ONLY：直接返回 `ACCESS_DENIED`；管理员预绑定是独立、待设计的
       管理用例，不能由普通首次登录隐式触发。
8. 重新从数据库加载角色和 Namespace 相关登录信息。
9. 构建 PlatformPrincipal。
10. 写成功审计或事务 outbox。
11. 提交数据库事务。
12. 外层收到 Authenticated 后建立 Session。
```

外部建号沿用随机平台标识：

- `userId` 使用 `usr_<UUID>`，不能从 email、Subject、username、Provider login 或
  display name 派生。
- 极低概率主键冲突时，整个身份事务回滚；外层在新事务中生成新 userId 后重试，不能在
  已标记 rollback 的事务中继续。
- 外部登录不创建 `LocalCredential`，因此不存在为了适配外部 username 而占用或覆盖
  本地登录名的行为。
- Provider login/username 只能作为受 Profile Sync Policy 管理的 displayName 候选。

### 8.9 外部 I/O 与事务

```text
Protocol Adapter
  → 远程协议验证（事务外）
  → ProviderAuthenticationResult
  → ExternalIdentityLoginService Facade
     → Descriptor / Authority Lock
     → Core-owned IdentityAssertionFactory
     → IdentityAssertion
     → IdentityResolutionTransaction（短数据库事务）
  → IdentityLoginOutcome
  → PlatformSessionService（事务提交后）
```

LDAP search/bind、OAuth token/userinfo、CAS validation、SAML metadata/JWKS 获取都不能放在
身份数据库事务内。

### 8.10 并发首次登录

不能只依赖“先查再插”：

```text
请求 A、B 同时未查到 Subject
  → 都尝试建号
  → A 提交成功
  → B 触发 Subject 唯一约束
  → B 整个事务回滚
  → B 在新事务中重新解析
  → 命中 A 创建的 Binding
```

数据库唯一约束是最终裁判。重试必须在新事务中执行，不在已标记 rollback 的事务内部继续。
不需要为此增加 Redis 分布式锁。

### 8.11 本地密码、Device Flow 和 API Token

本地密码不是外部身份，不创建 `identity_binding`，也不伪造
`ProviderAuthenticationResult`：

```text
LocalAuthService
  → LocalCredential 验证
  → AccountLoginGuard
  → PlatformPrincipalFactory
  → PlatformSessionService
```

本地登录与外部登录共同复用：

- `AccountLoginGuard`：交互登录统一处理 PENDING、DISABLED、MERGED、system account。
- `PlatformPrincipalFactory`：统一从数据库加载角色并构造 Principal。
- `PlatformSessionService`：统一建立 Web Session。

CLI Device Flow 仍由已登录 Web 用户批准设备，不成为新的外部 Provider。API Token 仍由
现有 Token 校验链路构造 Principal，但只复用适用于凭证的账号状态约束，例如拒绝
PENDING、DISABLED、MERGED；它不复用“system account 禁止交互式 Session”这一规则，
因为 system account 可能正是 API Token 的合法主体。Token 的签发、撤销和 system account
资格继续由独立 Token Policy 决定；两者不进入 Identity Binding 流程。

## 9. Provider Registry 和 Adapter 模型

### 9.1 为什么不使用万能 Provider Interface

不采用：

```java
interface IdentityProvider {
    PlatformPrincipal login(Object credentials);
    void syncUsers();
    void logout();
    void link();
}
```

原因：

- LDAP 被迫理解 OAuth Callback。
- SAML 被迫理解用户名密码。
- SCIM 被误当成登录。
- Provider 可以伪造 userId 和角色。
- 每新增能力都会扩张 Interface。

### 9.2 交互类型

Provider Adapter 按下列交互类型接入：

```text
Browser Redirect / Callback
  OAuth2、OIDC、DingTalk、CAS、SAML

Credential Login
  LDAP、企业 RPC

Passive Request Assertion
  可信 Header、签名 JWT、SPNEGO、已有企业会话

Provisioning
  SCIM Push、LDAP Pull Sync
```

它们共享 `ProviderAuthenticationResult` 输出契约，但不共享万能输入契约。
`IdentityAssertion` 只能由核心拥有的 Factory 构造。

### 9.3 Provider Registry 职责

Registry 负责：

- Provider Instance 唯一性。
- protocol 和 Authority 描述。
- 启用、禁用、错误、降级状态。
- 配置完整性校验。
- Adapter 能力发现。
- Subject type allowlist。
- email assurance 上限。
- 登录方法目录投影。
- Provider health 和指标标签。

Provider 状态：

```text
READY
DISABLED
MISCONFIGURED
DEGRADED
AUTHORITY_MISMATCH
LEGACY_UNPINNED
```

`LEGACY_UNPINNED` 只在升级过程中短暂存在：应用根据受信配置完成 Authority pin 后才
进入 `READY`。如果无法唯一确定 Authority，保持 fail closed 并要求运维确认。正常运行
时只有 `READY` Provider 进入 `/api/v1/auth/methods`。

阶段所有权：

- 阶段 1 至阶段 3：`TrustedProviderDescriptorSource` 解析现有静态配置，
  `ProviderAuthorityLockService` 独占 `identity_provider_state` 的 pin、读取、状态转换
  和兼容目录过滤职责。
- 阶段 4：Provider Registry 组合 descriptor source、Authority Lock 和 capability 状态，
  成为统一查询入口；它复用已有 Service，不重新实现或绕过 Authority compare-and-set。

### 9.4 第一阶段不做运行时插件

任意第三方 JAR 与 SkillHub 运行在同一 JVM 时可以访问文件、网络、反射和进程权限，不能
靠 Java Interface 把恶意代码变成“不可信沙箱插件”。

因此：

- 第一阶段只支持经过 Review、构建进发布物的可信 Provider 模块。
- 不支持上传 JAR、热加载和插件市场。
- 社区贡献代码可以进入官方模块，但必须经过安全 Review 和 conformance。
- 不可信或长尾集成放在外部 Broker，通过标准 OIDC 或可信 assertion 接入。

### 9.5 依赖方向

在确有两个以上独立 Adapter 后，可以拆出仅含值对象和契约的
`skillhub-auth-provider-spi`。拆分前先在现有 `skillhub-auth` 内验证 Interface。

目标依赖：

```text
provider-spi
  → JDK + 最小值对象

provider-ldap / provider-cas / provider-dingtalk
  → provider-spi + 对应协议库

skillhub-auth core
  → provider-spi + domain + repositories + Spring Security

skillhub-app
  → skillhub-auth core + 被选中的官方 Provider modules
```

Provider module 禁止依赖：

- JPA Repository
- `PlatformPrincipal`
- `PlatformSessionService`
- 平台角色和 Namespace Module

依赖方向最终使用 Maven Enforcer 或 ArchUnit 固化。

## 10. 数据模型

### 10.1 identity_provider_state

```text
provider_code           varchar(64) PK
protocol                varchar(32) NOT NULL
authority               varchar(512)
authority_fingerprint   varchar(64)
state                   varchar(32) NOT NULL
created_at              timestamptz NOT NULL
last_seen_at            timestamptz
```

用途：

- 固定 provider code 与 Authority。
- 检测运维误配置或身份源替换。
- 不保存 client secret、LDAP bind password 或 token。

LDAP endpoint 可能切换但仍属于同一目录，所以 LDAP Authority 使用通用配置字段
`authority` 中的稳定目录 ID，不能直接使用单个 hostname。

约束：

```text
authority 和 authority_fingerprint
  → 必须同时为 NULL 或同时非 NULL

state = LEGACY_UNPINNED
  → 两者必须为 NULL

state IN (READY, DEGRADED, AUTHORITY_MISMATCH)
  → 两者必须非 NULL

state IN (DISABLED, MISCONFIGURED)
  → 可以未 pin；重新启用或进入 READY 前必须完成 pin
```

Authority pin 成功后，`authority_fingerprint` 必须满足 64 位小写十六进制格式；状态
改变不能绕过 fingerprint 比较。

### 10.2 identity_binding

保留现有表并增加：

```text
status                  ACTIVE / REVOKED
last_authenticated_at   timestamptz
last_synchronized_at    timestamptz
revoked_at              timestamptz
revoked_by              varchar(128)
revocation_reason       varchar(256)
```

约束建议：

- 一个 Platform Account 对同一个 Provider Instance 最多有一个 ACTIVE Binding。
- 更换同 Provider 的外部账号必须走显式替换流程。
- 增加上述唯一约束前必须先运行升级 preflight；若历史数据中同一用户同一 Provider
  存在多个 Binding，只报告并阻止约束创建，不自动删除或合并。

Binding 的 `ACTIVE` 表示“外部身份与平台账号的关联仍有效”，不代表账号当前允许登录。
账号是否可以建立 Session 仍由 Account 状态和 Login Policy 决定。因此审批拒绝后可以
保留 ACTIVE Binding 并把 Account 置为 `DISABLED`，防止相同身份重复建号。

### 10.3 identity_binding_subject

```text
id                      bigserial PK
binding_id              bigint NOT NULL
provider_code           varchar(64) NOT NULL
subject_type            varchar(64) NOT NULL
subject_value           varchar(512) NOT NULL
is_primary              boolean NOT NULL
status                  ACTIVE / REVOKED
created_at              timestamptz NOT NULL
last_seen_at            timestamptz
revoked_at              timestamptz
```

关键约束：

```text
ACTIVE(provider_code, subject_type, subject_value) 全局唯一
每个 ACTIVE Binding 恰好一个 ACTIVE primary subject
binding_id + provider_code 必须对应同一 identity_binding
```

PostgreSQL 使用两层保证：

1. partial unique index 保证每个 Binding “最多一个” ACTIVE primary。
2. `DEFERRABLE INITIALLY DEFERRED` constraint trigger 在事务提交时检查每个受影响的
   ACTIVE Binding “至少一个” ACTIVE primary。

这样 primary 替换可以在同一事务内先降级旧 primary、再提升新 primary，而事务提交时
不能留下零 primary 或两个 primary。Binding 撤销、primary 撤销和迁移都必须通过同一个
Domain Service；阶段 2 的 PostgreSQL 测试必须覆盖零 primary、双 primary 和原子替换。

“至少一个 primary”的下界约束必须分两阶段启用，不能和 additive DDL 一起在混合版本
窗口立即生效：

1. **Expand 阶段**：创建新表、外键、Subject 全局唯一约束和“最多一个 primary”的
   partial unique index；新版本开始双读、双写、回填。此时不创建或不启用下界 constraint
   trigger，因为旧 Pod 仍可能只写 `identity_binding`。
2. **Contract gate 阶段**：确认所有旧 Pod 已退出，运行全量校验并修复零 primary/冲突
   数据，然后由独立 Flyway migration 启用 deferred constraint trigger。该 migration
   是发布门禁，不与第一阶段镜像混跑。

阶段 2 必须用真实的阶段 1 版本应用或兼容 fixture 在同一 PostgreSQL schema 上执行一次旧版
首次绑定写入，证明 Expand 阶段不会拒绝旧写；随后升级到阶段 2 完成回填，再验证 Contract
gate 能启用并拒绝零 primary。只用新版本 repository 模拟旧写不算滚动升级证据。

如果未来需要对 Subject 加密或 HMAC lookup，另开安全设计；第一阶段沿用当前 raw subject
存储风险，不在本次架构 PR 中引入新的密钥管理系统。

### 10.4 user_profile_field_source

```text
user_id                 varchar(128)
field_name              displayName / email / avatarUrl
source_type             PROVIDER / USER / ADMIN / LEGACY_LOCAL
provider_code           varchar(64)
assurance               varchar(32)
last_synchronized_at    timestamptz
updated_at              timestamptz
PRIMARY KEY(user_id, field_name)
```

用途：避免后续外部登录覆盖用户或管理员维护过的资料。

### 10.5 identity_link_request

```text
id                      uuid PK
primary_user_id         varchar(128) NOT NULL
provider_code           varchar(64) NOT NULL
state_hash              varchar(128) NOT NULL
status                  PENDING / COMPLETED / EXPIRED / CANCELLED
expires_at              timestamptz NOT NULL
created_at              timestamptz NOT NULL
completed_at            timestamptz
```

只保存 hash 和流程元数据，不保存 OAuth code、CAS Ticket 或密码。

### 10.6 SCIM 资源绑定

SCIM Provisioning 使用独立表，不直接复用登录 Subject：

```text
source_code             varchar(64)
scim_resource_id        varchar(256)
external_id             varchar(256)
user_id                 varchar(128)
version                 varchar(128)
status                  ACTIVE / DISABLED
last_synchronized_at    timestamptz
PRIMARY KEY(source_code, scim_resource_id)
```

SCIM `externalId` 与登录 `sub` 只有在管理员明确声明并验证映射规则时才能关联。不得按 email
自动把 SCIM 用户与登录身份绑定。

## 11. Policy 设计

### 11.1 Login Policy

每次登录执行：

- Provider 是否启用且 Authority 匹配。
- Subject denylist。
- email domain 或组织限制。
- Account 状态。
- 必须重新验证的企业属性。
- 登录风险和限流。

Login Policy Context 至少包含：

```text
NEW_IDENTITY / RETURNING_IDENTITY
ProviderReference
IdentityAssertion
ExistingAccountStatus
Request metadata
```

### 11.2 Provisioning Policy

只对未绑定身份执行：

| 模式 | 行为 |
|---|---|
| `AUTO` | 创建 ACTIVE Platform Account 和 Binding |
| `APPROVAL` | 创建 PENDING Account 和 Binding，不建立 Session |
| `EXISTING_BINDING_ONLY` | 不自动建号，只允许预绑定用户 |

Login Policy 与 Provisioning Policy 分开，避免“已有用户每次登录”和“陌生身份首次建号”
混用一个 `AccessDecision`。

`APPROVAL` 的状态语义已经确定：

1. 首次登录在一个事务中创建 `PENDING Account + ACTIVE Binding + Subjects`，但不创建
   `@global` membership，也不建立 Session。
2. 相同外部身份再次登录仍命中同一 Binding，返回 `PendingApproval(ACCOUNT_PENDING)`，
   不能重复建号。
3. 审批通过时，账号转为 `ACTIVE`，并在同一事务中补齐 `@global MEMBER`。
4. 审批拒绝时，账号转为 `DISABLED`，保留 ACTIVE Binding 防止相同外部身份反复创建
   PENDING 账号；后续恢复必须走管理员操作并写审计。

### 11.3 Email assurance

`ProviderAttributeValue.trust` 到平台基础 assurance 的固定映射：

```text
UNVERIFIED → UNVERIFIED
ASSERTED   → PROVIDER_ASSERTED
VERIFIED   → VERIFIED
```

受信 `ProviderDescriptor` 必须把以下三部分分开保存，不能压成一个含义不清的
`assurance ceiling`：

```text
AttributeRule
  ├─ evidenceMapping            # 上述固定映射或协议专用降级
  ├─ adapterMaximumAssurance    # 官方 Adapter 代码允许的硬上限
  └─ authoritativeSourceAllowed # 该协议/属性是否允许管理员声明为权威目录

Provider Instance config
  ├─ emailAssuranceLimit        # 管理员设置的最终上限
  └─ emailAuthoritativeSource   # 默认 false
```

计算顺序：

1. 根据已验证协议事实得到基础 assurance。
2. 按 Adapter hard maximum 降级。
3. 只有 `authoritativeSourceAllowed=true` 且管理员显式设置
   `emailAuthoritativeSource=true` 时，才能把企业目录中的 ASSERTED email 提升为
   `AUTHORITATIVE`。
4. 最后使用 `emailAssuranceLimit` 再做上限裁剪。

Adapter 不能直接输出 `AUTHORITATIVE`，也不能通过上游属性打开
`emailAuthoritativeSource`。

#### 多 email 选择

Provider descriptor 必须声明可作为 email 候选的属性 key 和确定的优先级。Factory 对
所有候选先执行语法校验、规范化、去重和 assurance 裁剪，再按以下规则只生成一个
`Optional<EmailClaim>`：

1. 没有有效候选时返回 `Optional.empty()`。
2. 相同规范化 email 的重复候选合并，取不超过配置上限的最高 assurance。
3. 若出现两个不同的 `VERIFIED`/`AUTHORITATIVE` email，判定
   `INVALID_IDENTITY_ASSERTION` 并 fail closed，不能按返回顺序任选其一。
4. 没有可信冲突时，先选 assurance 更高者，再按 descriptor 中固定的属性优先级选择；
   Adapter 返回顺序不能影响结果。
5. 被选中的 `UNVERIFIED`/`PROVIDER_ASSERTED` email 仍受 Login Policy 和 Profile Sync
   限制，不能参加 `EMAIL_DOMAIN`，也不能覆盖可信平台 email。

默认：

| 来源 | 默认 assurance |
|---|---|
| OIDC `email_verified=true` | `VERIFIED` |
| OIDC 未验证 email | `UNVERIFIED` |
| GitHub verified primary email | `VERIFIED` |
| LDAP `mail` | `PROVIDER_ASSERTED` |
| 管理员声明企业 LDAP 为权威 email 目录 | `AUTHORITATIVE` |
| DingTalk 普通 email 属性 | `PROVIDER_ASSERTED` |
| CAS/SAML 普通 email attribute | `PROVIDER_ASSERTED` |
| 裸 Header email | `UNVERIFIED` |
| 经 mTLS/签名网关且管理员声明权威 | 最高 `AUTHORITATIVE` |

### 11.4 Profile Sync Policy

每个字段可选：

```text
NEVER
INITIAL_ONLY
FILL_IF_EMPTY
PRESERVE_LOCAL
PROVIDER_AUTHORITATIVE
```

默认：

| 字段 | 默认策略 |
|---|---|
| displayName | 首次初始化；用户/管理员修改后保留 |
| email | 仅 verified/authoritative 且平台为空时填充 |
| avatarUrl | 首次初始化；用户修改后保留 |
| platform roles | NEVER |
| namespace roles | NEVER |

`PROVIDER_AUTHORITATIVE` 必须由管理员对具体 Provider 和字段显式开启。

## 12. 身份关联、解绑和账号合并

### 12.1 首次登录碰撞

外部身份尚未绑定，但 email 或 username 与已有账号相同时：

```text
不自动复用账号
  → 返回 LINK_REQUIRED
  → 用户先认证现有 Platform Account
  → 再完成目标外部 Provider 认证
  → 两个证明都有效且未过期
  → 创建 Identity Binding
```

响应不得泄露碰撞账号的 userId、角色或完整 email。

### 12.2 显式 Identity Link

建议流程：

```text
1. 已登录用户从账号设置发起“添加登录方式”。
2. 服务端创建短期、一次性的 link request。
3. 对当前账号执行 fresh reauthentication。
4. 跳转/调用目标 Provider 完成外部认证。
5. Adapter 产出 `ProviderAuthenticationResult`。
6. Link Facade 使用受信 Provider、Authority Lock 和内部 Factory 构造
   `IdentityAssertion`。
7. Link Transaction 检查 Subject 未绑定到其他账号。
8. 创建 Binding 和 Subjects。
9. 写审计并消费 link request。
```

未来 Interface：

```java
public interface ExternalIdentityLinkService {

    IdentityLinkOutcome link(
        AuthenticatedActor actor,
        IdentityLinkIntent intent,
        ResolvedProviderHandle provider,
        ProviderAuthenticationResult result
    );
}
```

Link Facade 内部复用同一个 descriptor source、Authority Lock 和 package-private
Assertion Factory；Provider 仍不能构造 `IdentityAssertion`。

### 12.3 解绑

解绑前必须：

- fresh reauthentication。
- 确认该 Binding 属于当前账号。
- 确认账号仍有其他可用登录方式。
- system account 禁止操作。
- 写审计。

### 12.4 Account Merge

账号合并与 Identity Link 分开：

```text
1. 主账号会话发起 merge intent。
2. 通过独立浏览器流程或重新登录证明次账号所有权。
3. 服务端把 proof 绑定到 merge request、主账号会话和短 TTL。
4. 展示将迁移的身份、Token、角色、Namespace membership 和冲突。
5. 用户确认。
6. 单事务迁移数据。
7. 次账号状态改为 MERGED。
8. 撤销次账号所有 Web Session。
9. 按策略撤销或迁移 API Token。
```

当前直接返回 verification token 的流程应列为独立安全修复，不等待 LDAP/DingTalk。

## 13. 协议支持策略

### 13.1 分层定义

| 层级 | 含义 |
|---|---|
| Native Tier 1 | 官方内置，完整 CI、文档、升级测试和安全维护 |
| Native Tier 2 | 社区或实验性内置，通过 conformance，但支持承诺较低 |
| Broker | 推荐由 Keycloak/authentik/Dex/企业网关转换为 OIDC 或可信 assertion |
| SPI Reserved | 架构预留，不承诺近期实现 |

### 13.2 支持矩阵

| 协议/方式 | 类型 | 策略 | 优先级 |
|---|---|---|---|
| GitHub/GitLab OAuth | Browser | 迁入统一核心，Native Tier 1 | P0/P1 |
| 标准 OIDC | Browser | Native Tier 1，首选企业标准入口 | P1 |
| LDAP/LDAPS/StartTLS | Credential | Native Tier 1 | P2 |
| Active Directory LDAP | Credential | LDAP Adapter 的独立 mapper/fixture | P2 |
| DingTalk OAuth2 | Browser | 目标为 Native Tier 1 | P2 |
| CAS 2.0/3.0 | Browser Ticket | Native Tier 1，复用成熟 Client | P2 |
| SAML 2.0 SP | Browser POST/Redirect | Broker 优先；原生候选 | P3 |
| Trusted Header / signed JWT | Passive | Native Tier 2，默认关闭 | P3 |
| SCIM 2.0 | Provisioning | 独立模块，不进入登录 SPI | P3/P4 |
| Kerberos/SPNEGO | Passive Challenge | Broker 优先，SPI Reserved | P4 |
| WeCom/Feishu | Browser OAuth/OIDC | 社区 Adapter 或 Broker | P3/P4 |
| X.509/mTLS user auth | Passive | Broker 优先，SPI Reserved | P4 |
| WS-Federation | Browser | Broker only | 不原生 |
| RADIUS/PAM | Credential | Broker only | 不原生 |
| WebAuthn/Passkeys/TOTP | Local strong auth | 独立本地认证设计 | 不属于联邦 |

“架构可适配”不等于“SkillHub 必须原生实现”。每个 Native Tier 1 协议都会形成长期安全和
兼容承诺，必须有真实需求、测试环境和维护责任人。

Tier 表示项目合入后的支持等级，不表示代码作者。DingTalk Adapter 可以由外部贡献者
实现；如果合入官方发布物并标记为 Native Tier 1，项目仍需承担完整 CI、文档、升级测试
和安全维护责任。

## 14. 协议 Adapter 设计

### 14.1 OAuth2/OIDC

继续使用 Spring Security OAuth2 Client：

```text
Spring Security 验证 state、code、token、OIDC ID Token
  → Claims Mapper
  → ProviderAuthenticationResult
  → ExternalIdentityLoginService Facade
     → Descriptor / Authority Lock
     → Internal IdentityAssertionFactory
     → IdentityResolutionTransaction
  → IdentityLoginOutcome
  → OAuth success handler
  → PlatformSessionService
```

要求：

- OIDC identity key 使用 issuer + `sub`。
- `sub` 区分大小写，不统一 lowercase。
- `email_verified` 正确映射到 assurance。
- 不把 access/refresh token 放入 Assertion 或 Binding。
- GitHub/GitLab 使用稳定数字 user id，不使用 login/username。
- 现有 OAuth Provider 必须先完成回归，作为其他 Adapter 的参考实现。

### 14.2 LDAP/AD

```text
Credential Controller
  → LDAP credential verifier（事务外）
     1. service bind/search
     2. 唯一匹配
     3. user DN bind 验证密码
     4. 读取稳定 subject 和配置属性
  → LDAP identity mapper
  → ProviderAuthenticationResult
  → ExternalIdentityLoginService Facade
     → Descriptor / Authority Lock
     → Internal IdentityAssertionFactory
     → IdentityResolutionTransaction
```

要求：

- 支持 OpenLDAP `entryUUID` 和 AD `objectGUID`。
- subject 属性必须可配置且缺失时 fail closed。
- username、DN、mail 不能作为默认 subject。
- filter 必须正确 escape。
- search 结果必须唯一。
- 支持 LDAPS 和 StartTLS；生产不提供 trust-all。
- 连接、读取、池等待均有明确超时。
- 区分 invalid credentials、not found、unavailable、TLS、misconfigured。
- disabled 时不初始化连接。
- Provider 内不访问 `UserAccountRepository`。
- 属性映射至少覆盖 username、displayName、email、avatar 的可配置来源。

Subject 依据：

- OpenLDAP `entryUUID` 的语义以 IETF
  [RFC 4530](https://www.rfc-editor.org/rfc/rfc4530.html) 为依据，并由真实 OpenLDAP
  fixture 验证服务端确实返回该 operational attribute。
- Active Directory `objectGUID` 的属性语义以 Microsoft
  [Object-Guid attribute](https://learn.microsoft.com/en-us/windows/win32/adschema/a-objectguid)
  为依据；字节数组到 canonical string 的转换必须由固定 fixture 验证，不能直接依赖
  平台默认字节序。
- 其他目录产品若没有稳定、官方定义且实例可读取的 immutable id，只能通过管理员显式
  mapper 加 fixture 接入，不能退化为 DN、uid 或 mail。

验证：

- OpenLDAP 真实容器。
- LDAPS 正常和不可信证书。
- 首次、重复登录和用户名变化。
- email collision。
- entryUUID 缺失、多结果、filter injection。
- AD objectGUID 字节序 fixture。
- 目录不可用和超时。

### 14.3 DingTalk

PR #467 的非标准 OAuth2 transport 可以保留：

- JSON token exchange。
- `x-acs-dingtalk-access-token` user-info header。
- Provider-specific authorization 参数。

身份规则迁入核心：

```text
primary: dingtalk_union_id（存在且适用时）
aliases:
  dingtalk_open_id
  dingtalk_user_id
```

若只能取得 fallback subject，必须保留 subject type，不能把不同类型值混在同一无类型
`subject` 空间。后续取得更高优先级 Subject 时，必须先由已有 Alias 命中同一 Binding，
再补充新 Alias。

钉钉官方“根据 sns 临时授权码获取用户信息”文档说明了 `unionId`/`openId` 返回字段：
[DingTalk user information](https://open.dingtalk.com/document/orgapp/obtain-the-user-information-based-on-the-sns-temporary-authorization)。
但 `unionId/openId/userId` 在不同应用类型、企业授权关系和新版 OAuth2 接口下的精确唯一
范围，目前仍视为**待官方契约和真实租户 fixture 验证的假设**。阶段 8 开始前必须形成
Authority × subject type 决策表；未完成时不得把 fallback 顺序作为稳定 Binding 契约。

要求：

- 无真实 email 时使用 `Optional.empty()`，不制造 verified 合成 email。
- token expiry 正确写入 Spring Security token response。
- Provider 默认关闭，配置完整才进入方法目录。
- Compose/Kubernetes 配置链路完整。
- token/userinfo HTTP 错误映射为统一错误。
- mock callback 覆盖 GitHub/GitLab/OIDC 无回归。

### 14.4 CAS 2.0/3.0

```text
GET login start
  → 保存 state + 精确 service URL
  → 重定向 CAS /login
  → callback ticket
  → CAS Client validation（事务外）
  → verified principal + attributes
  → ProviderAuthenticationResult
  → ExternalIdentityLoginService Facade
     → Descriptor / Authority Lock
     → Internal IdentityAssertionFactory
     → IdentityResolutionTransaction
```

要求：

- 使用成熟 CAS Client 或 Spring Security CAS 集成，不自行维护不安全 XML parser。
- Ticket validation 必须绑定发起时精确 service URL。
- state 和 Ticket 一次性消费。
- CAS 2 XML 禁止 XXE。
- CAS 3 JSON/XML 错误正确分类。
- principal 或 immutable attribute 必须明确配置为稳定 Subject。
- CAS email attribute 默认不是 verified。
- 强制 HTTPS 的规则要支持明确的本地测试例外，生产 fail closed。

### 14.5 SAML 2.0

首选路径：

```text
SAML IdP
  → Keycloak/authentik
  → OIDC
  → SkillHub
```

若实现原生 SAML SP：

- 使用 Spring Security SAML2/OpenSAML 成熟实现。
- 验证 Response/Assertion 签名。
- 验证 issuer、audience、recipient、destination、`InResponseTo`。
- 防重放并限制时钟偏差。
- 支持 metadata 和证书轮换。
- 仅 persistent NameID 或明确 immutable attribute 可作 Subject。
- transient NameID 禁止持久绑定。
- Single Logout 可以拆为后续 PR。

原生实现门槛：

- 至少两个真实部署需求，或一名长期维护者。
- 有稳定测试 IdP 和证书轮换测试。
- 通过 SAML 安全检查矩阵。

### 14.6 Trusted Header / Gateway Assertion

不允许简单读取来自公网请求的 `X-User`。

可接受模式：

1. 反向代理删除所有客户端传入的身份 Header。
2. 代理到 SkillHub 的链路使用专用 mTLS CA，并校验代理证书身份；或使用短期签名 JWT。
3. Assertion 校验 issuer、audience、expiry、nonce/jti 和重放。
4. 身份中必须有稳定 UID。
5. 普通 `X-Forwarded-For` 不构成代理身份证明。
6. 入口默认关闭。

`PassiveSessionAuthenticator` 的替代 Adapter 目标输出改为
`ProviderAuthenticationResult`，再由核心构造 `IdentityAssertion`；不得返回 Principal。

### 14.7 Kerberos/SPNEGO

优先：

```text
Browser SPNEGO
  → Keycloak/authentik/Apache enterprise gateway
  → OIDC 或签名 Gateway Assertion
  → SkillHub
```

只有具备真实 KDC、keytab 管理、multi-realm 测试和长期维护者时才做原生：

- 正确处理 `WWW-Authenticate: Negotiate` challenge。
- keytab 最小权限和轮换。
- principal canonicalization。
- realm 隔离。
- AD 场景尽量解析到 objectGUID。

### 14.8 SCIM 2.0

SkillHub 在企业 Provisioning 场景中扮演 SCIM Service Provider，提供例如：

```text
/scim/v2/Users
/scim/v2/Groups
/scim/v2/Schemas
/scim/v2/ResourceTypes
/scim/v2/ServiceProviderConfig
```

SCIM 模块独立于外部登录核心：

- Bearer token 只存 hash，或使用 mTLS/OAuth client credentials。
- 支持幂等、分页、filter、PATCH、ETag/If-Match。
- delete 默认转为 DISABLED，不直接物理删除业务账号。
- deprovision 必须撤销 Session，并按策略撤销 API Token。
- group 先进入 entitlement/mapping 层，不能直接授予 `SUPER_ADMIN`。
- SCIM binding 与 login binding 不按 email 自动合并。

第一阶段只做独立详细设计，不与统一登录核心同阶段实现。

## 15. Auth Method Catalog

目标 `/api/v1/auth/methods` 由 Provider Registry 投影：

```text
Provider Registry
  → READY Provider Instances
  → negotiated login capabilities
  → LoginMethodProjection
  → /api/v1/auth/methods
```

方法目录只返回展示和路由所需信息：

```text
providerCode
displayName
methodType
startUrl（由核心生成）
iconKey
```

不得返回：

- secret 或 endpoint 内部凭证。
- 任意由 Provider 提供的 action URL。
- Subject mapping 细节。
- 上游错误。

## 16. 配置策略

第一阶段：

- 非敏感配置：YAML/环境变量。
- secret：环境变量、Docker Secret、Kubernetes Secret 或现有 secret 管理方式。
- 数据库：只保存 Authority lock、Binding 和运行状态，不保存 client secret。
- 配置变更：应用重启生效。
- Provider 配置不完整：`MISCONFIGURED`，不进入登录目录。

每个 Provider Instance 配置至少包含：

```text
code
enabled
displayName
protocol
authority
provisioningMode
profileSyncPolicy
emailAssuranceLimit
protocol-specific settings
secret references
```

后台动态配置、secret 加密存储和热重载属于后续独立设计，不能夹入首个核心 PR。

## 17. 审计、日志和指标

### 17.1 审计事件

建议事件：

```text
IDENTITY_LOGIN_SUCCEEDED
IDENTITY_LOGIN_DENIED
IDENTITY_LOGIN_PENDING
IDENTITY_PROVISIONED
IDENTITY_BINDING_CREATED
IDENTITY_BINDING_REVOKED
IDENTITY_LINK_REQUIRED
IDENTITY_LINK_COMPLETED
IDENTITY_CONFLICT_DETECTED
PROVIDER_AUTHORITY_MISMATCH
ACCOUNT_MERGE_INITIATED
ACCOUNT_MERGE_COMPLETED
SCIM_USER_DISABLED
```

审计字段：

- providerCode
- protocol
- bindingId
- userId（已解析时）
- decision/reason code
- requestId
- client IP / user agent（按现有隐私策略）
- accountCreated / bindingCreated

Subject 只记录脱敏或不可逆摘要；不得记录凭证和完整上游响应。

审计持久化必须区分成功与失败事务：

- 与成功状态变更一致的事件使用同事务 outbox，提交后异步投递。
- `IDENTITY_CONFLICT_DETECTED`、`PROVIDER_AUTHORITY_MISMATCH`、重放和其他会主动回滚身份
  事务的安全拒绝，使用受限的独立事务（例如专用 audit writer）或由事务外 Facade 写入
  可靠 outbox；不能随被拒绝的 Binding/Provisioning 事务一起回滚。
- 独立审计失败不得把敏感原始 Assertion 打进 fallback 日志；只记录 requestId、稳定原因
  code 和脱敏 Provider 标识，并触发运维指标。

### 17.2 日志

- invalid credentials 使用通用信息。
- upstream unavailable、TLS 和 misconfiguration 保留安全的运维分类。
- URL 日志必须去除 userinfo 和 query secret。
- 不记录 LDAP bind DN 密码、OAuth token、CAS Ticket、SAML assertion、Cookie。

### 17.3 指标

建议低基数标签：

```text
skillhub_identity_login_total{provider,protocol,result}
skillhub_identity_login_duration_seconds{provider,stage}
skillhub_identity_provision_total{provider,result}
skillhub_identity_conflict_total{provider,type}
skillhub_identity_provider_state{provider,state}
```

禁止使用 userId、email、Subject 作为指标 label。

## 18. 兼容升级和迁移

### 18.1 原则

使用 additive migration、双读、双写，至少保留一个发布周期：

1. 不删除现有 `identity_binding.subject`。
2. 新增 Provider state、typed Subject 和 profile source 表。
3. 旧代码可继续读取旧列。
4. 双读兼容窗口内，新代码同时解析 typed Subject 和 legacy binding 并比较结果。
5. 新登录同时维护旧列和新表。
6. 不改变 `PlatformPrincipal` record 结构。
7. 不改变现有公开登录 URL，除非另有兼容层。

### 18.2 数据回填

每条现有 Binding 回填：

```text
subject_type = legacy_subject
subject_value = identity_binding.subject
is_primary = true
status = ACTIVE
```

能够由当前受信配置唯一解析 protocol 的历史 Provider，state 初始为：

```text
LEGACY_UNPINNED
```

如果历史 `provider_code` 缺失配置、匹配多个 descriptor 或无法确定 protocol，不写入伪造
的 `legacy-unknown` 记录；该 Provider 保持隐藏和 fail closed，并进入 preflight 报告。
部署新版本后，根据当前受信配置完成一次 Authority pin。完成后 Authority 变化必须拒绝。

迁移前置检查必须报告：

- 重复 `(provider_code, subject)` 之外的异常历史数据。
- 同一 user/provider 的多个 Binding。
- Binding 指向不存在或已 MERGED 的账号。
- Provider code 在当前配置中缺失或对应多个候选 Authority。

迁移不得根据 email、login name 或“看起来相同”的 Subject 自动修复冲突。冲突数据进入
管理员处理清单，在完成显式决策前保持 fail closed。

### 18.3 OAuth 渐进升级

GitHub/GitLab/OIDC：

1. 在双读兼容窗口内，对同一次登录同时查询：
   - 新 `(provider_code, subject_type, canonical_subject_value)`；
   - 旧 `(provider_code, identity_binding.subject)`。
2. 比较两个查询结果：
   - 都未命中：进入正常首次 Provisioning。
   - 只命中 typed：按新 Binding 登录，并检查旧双写列是否缺失或不一致。
   - 只命中 legacy：进入升级事务。
   - 两者命中同一 Binding：继续，并在需要时完成幂等修复。
   - 两者命中不同 Binding/账号：立即冲突并 fail closed。
3. 命中旧 Binding 后锁定该 Binding，在一个事务中完成 primary 升级：

   ```text
   legacy_subject.is_primary = false
   新 typed subject.is_primary = true
   legacy_subject 继续保持 ACTIVE alias
   ```

4. 同一 Assertion 中允许的其他 typed Subject 以非 primary Alias 写入。
5. 同时保留旧 `identity_binding.subject` 双写，供旧版本读取。
6. 若 typed Subject 已属于另一个 Binding、新旧查询命中不同 Binding/账号，或事务内
   唯一约束失败，
   整个事务回滚并 fail closed；不得保留半升级状态。

兼容窗口内每个 Provider descriptor 必须冻结且只能声明一个
`legacyPrimarySubjectType`：

- 只有该类型的候选值可以写入旧 `identity_binding.subject`；typed Alias 不双写旧列。
- 新 Assertion 必须包含可唯一得到该 legacy 值的候选，否则该 Provider 在旧 Pod 退出前
  不能切换 primary 类型。
- typed 表可以把更稳定的新类型设为 primary，并把 legacy 类型保留为 ACTIVE Alias；旧列
  仍保存 legacy 类型的值，不能把不同 subject type 混入同一个无类型唯一空间。
- Provider 想更换 `legacyPrimarySubjectType`，必须等待旧 Pod 全部退出并完成独立迁移，
  不能仅修改配置。

### 18.4 滚动升级

- Flyway 只做 additive DDL。
- 部署阶段 1 前冻结所有现有 Provider 的 protocol/Authority 配置；混合版本窗口内不得变更
  这些字段，并在发布检查中确认所有 Pod 使用同一配置。旧 Pod 不具备 Authority Lock，
  因此显式 Authority 迁移只能在旧 Pod 全部退出后进行。
- 新协议默认关闭，所有 Pod 升级完成后再启用。
- 新代码写入旧代码可忽略的新表/列。
- 不依赖旧 Pod 无法识别的新 Session record 字段。
- Provider Authority pin 使用 6.3 节的数据库 compare-and-set；相同 fingerprint 的 Pod
  收敛，不同 fingerprint 的 Pod 触发 `AUTHORITY_MISMATCH`。
- 阶段 2 的 Subject schema 按 10.3 节拆成 Expand 和 Contract gate 两次 migration；
  Expand 窗口不启用“至少一个 primary”trigger，旧 Pod 全部退出、回填校验完成后才启用。

### 18.5 回滚

- 回滚旧版本时，旧 OAuth 仍可使用现有 `identity_binding.subject`。
- 新协议在回滚前先关闭。
- 新增表和列保留，不执行 down migration。
- 若新 Adapter 已创建旧代码无法识别的 Binding，回滚前必须评估对应用户登录路径。
- 在尚未产生 Binding/Subject revocation 且 Contract gate 尚未启用时，可以按双写数据回滚
  到阶段 1 兼容版本。
- 一旦提交第一条 `REVOKED` Binding/Subject，最低可回滚版本提升为“首个理解并强制过滤
  revocation 状态的阶段 2 发布版本”；禁止回滚到只读取旧 `identity_binding.subject` 且
  不认识 revocation 的版本，否则被撤销身份会重新获得登录能力。此后只能前向修复或执行
  经审计的数据恢复方案。

## 19. 测试与验收

### 19.1 分阶段核心测试矩阵

目标能力的测试必须在其实现阶段中落地，不能把后续数据库和 Profile Sync 能力提前写成
阶段 1 的验收条件：

| 阶段 | 必须验证 |
|---|---|
| P0 / 阶段 0A、0B、0C | 未验证 email 不能通过 `EMAIL_DOMAIN`；未验证 email 不写入可信资料；`PENDING/DISABLED/MERGED/system` 状态；PENDING 审批通过补 membership；旧 Merge 不能绕过次账号控制权证明 |
| 阶段 1 | 受信 Provider descriptor 来源；未注册/歧义 Authority fail closed；Authority fingerprint、测试向量、compare-and-set、跨重启 mismatch、粘性状态和同 Authority 恢复；primary Subject 缺失、类型非法、过长；非空 Alias fail closed；GitHub/GitLab/OIDC 回归；本地登录复用 Guard/Principal Factory；只有 `Authenticated` outcome 建立 Session |
| 阶段 2 | typed Subject 与 Alias；同一 Binding 多 Alias 命中；Alias 命中不同账号；legacy primary 单事务升级；零/双 primary 拒绝；并发首次登录；旧数据库、滚动升级和回滚 |
| 阶段 3 | Profile Sync 来源和覆盖策略；`AUTO/APPROVAL/EXISTING_BINDING_ONLY`；email collision → 仅含 reason code 的 `LINK_REQUIRED` 安全提示；PENDING 重复登录、批准和拒绝语义；事务失败不留下孤立账号或 Binding |
| 阶段 4 | Registry 状态；disabled/misconfigured Provider 不展示且不联网；旧登录目录兼容；Provider Conformance Kit |
| 阶段 5 | Identity Link：当前账号 fresh reauthentication、目标 Provider 再认证、重放/过期、解绑最后登录方式 |
| 阶段 6 | Account Merge：主次账号独立控制权证明、冲突预览、Session/API Token 处理和原子迁移 |

并发和唯一约束必须在 PostgreSQL 上验证；H2 不能证明 JSONB、部分唯一索引和真实竞争行为。

### 19.2 Provider Conformance Kit

每个 Adapter 都必须通过：

- descriptor 与 Provider code 一致。
- Provider code/Authority 不能由上游响应覆盖。
- 产生稳定、非空且允许类型的 Subject。
- email assurance 不超过协议和配置上限。
- Assertion 不包含 token、密码、Cookie、Ticket。
- Subject 规范化确定且有 fixture。
- 401/403、5xx、timeout、TLS、misconfigured 分类正确。
- disabled 时不发起网络连接。
- 超时生效。
- 日志不包含凭证。
- Adapter 不创建 UserAccount、角色或 Session。

### 19.3 协议集成测试

| 协议 | 自动化验证 |
|---|---|
| OIDC | mock issuer、state、nonce、issuer、audience、JWKS 轮换 |
| OAuth | GitHub/GitLab claims、verified email、回调回归 |
| LDAP | OpenLDAP、LDAPS、StartTLS、entryUUID、filter、超时 |
| AD | objectGUID/objectSid fixture、属性映射 |
| DingTalk | mock token/userinfo、expiry、typed aliases |
| CAS | CAS 2 XML、CAS 3 JSON、service mismatch、重放、XXE |
| SAML | 签名、audience、recipient、InResponseTo、重放、证书轮换 |
| Trusted Header | mTLS/签名、Header spoof、过期、重放 |
| SCIM | create/update/disable、PATCH、pagination、ETag、幂等 |

### 19.4 升级测试

- 从包含现有 OAuth Binding 的发布数据库升级。
- legacy binding 首次登录补写 typed Subject。
- 新旧双写一致。
- provider authority 首次 pin。
- 旧 Redis Session 在新版本继续可用。
- mixed-version rolling upgrade。
- 回滚旧版本后现有 OAuth 登录仍可用。

### 19.5 `big-main` 和测试机验收

每个协议阶段：

1. 先完成范围内单元/集成测试。
2. 进入 `big-main` 或等价的集成验证分支，不直接进入 `main`。
3. 构建新的 Server/Web 镜像。
4. 在专用测试机部署新镜像，不重启测试机器。
5. 验证旧 Provider 回归和新 Provider 最小完整链路。
6. 检查日志、审计、指标和数据库迁移。
7. 人工验证对应登录 UI、错误页和账号设置。
8. 记录镜像、commit、测试命令和结果。
9. 全部通过后再由维护者决定是否把当前阶段 PR 更新到可进入 `main` 的状态。

## 20. 分阶段实施计划

本工作统一在父 issue #628 中跟踪，#630 只负责设计、阶段计划和验收标准。为降低认证
边界风险，后续实现按阶段逐个创建 PR、逐个处理、逐个合入 `main`；不要一次性打开多个
阶段 PR。每个阶段必须聚焦，不把协议、核心、数据库大迁移和 UI 重构混在一起。原先拆出
的子 issue/PR 关闭后，其范围、验收和证据回收到本节。

阶段 PR 协作规则：

- 只保留一个父 issue #628，不再为阶段拆子 issue。
- #630 合并后作为后续实现的设计依据，不承载所有实现提交。
- 每次只创建并推进当前阶段 PR；当前阶段合并或明确暂停后，再创建下一阶段 PR。
- 每个阶段 PR 在正文中引用 #628，并标明对应阶段、范围、非范围、验收命令和回滚风险。
- 阶段完成状态回写到 #628，避免用多个 open PR 承载任务状态。

依赖关系：

```text
阶段 0A / 阶段 0B / 阶段 0C：安全热修
        │
        ▼
阶段 1：统一身份核心 + Authority Lock + 现有 OAuth/OIDC 迁移
        │
        ▼
阶段 2：Binding V2 + typed Subject/Alias
        │
        ▼
阶段 3：Profile Sync + Provisioning Policy
        │
        ▼
阶段 4：Provider Registry + Adapter 契约冻结
        ├→ 阶段 5：Identity Link
        ├→ 阶段 6：Account Merge
        ├→ 阶段 7：LDAP/AD
        ├→ 阶段 8：DingTalk
        └→ 阶段 9：CAS
```

“身份核心稳定”在本计划中的可观察定义是阶段 1 至阶段 4 已完成验收，而不是仅合入接口
文件。阶段 5、阶段 6 与协议 Adapter 可以在阶段 4 后并行；但某 Provider 如果启用
`AUTO` 或 `APPROVAL` 且可能发生 email collision，在阶段 5 完成前只能安全返回 `LINK_REQUIRED`，
不能用临时 email 自动绑定绕过显式 Link 流程。

### P0：安全热修

#### 阶段 0A：email 和账号状态不变量

范围：

- `EmailDomainAccessPolicy` 强制检查 verified/authoritative email。
- 未验证 email 不写入平台可信 email。
- 外部登录拒绝 `MERGED` 和 system account。
- 覆盖回归测试。

进入下一阶段门槛：

- GitHub/GitLab/OIDC 现有测试通过。
- 未验证 email 准入测试通过。

#### 阶段 0B：PENDING 审批补齐基础 membership

范围：

- 保持已有 `PENDING → ACTIVE` 管理员审批语义，并把状态变更与补齐 `@global`
  membership 放入同一事务。
- 覆盖重复审批、事务失败、批准后登录和拒绝后登录测试。

#### 阶段 0C：隔离不安全的旧 Account Merge

范围：

- 先禁止或隔离当前不能证明次账号所有权的 merge 流程。
- 返回稳定错误和运维说明，不把 verification token 暴露给主账号会话。
- 为阶段 6 的安全 Account Merge 编写独立验收设计；P0 不夹带迁移实现。

### P1：统一身份核心

#### 阶段 1：第一个核心架构阶段，只做统一身份核心

范围：

- `IdentityAssertion`
- `ProviderAuthenticationResult`
- Core-owned `IdentityAssertionFactory`
- `ProviderReference`
- typed `ExternalSubject` 值对象
- `EmailAssurance`
- `AuthenticationEvidence`
- `ExternalIdentityLoginService`
- `IdentityLoginOutcome`
- `TrustedProviderDescriptorSource` 内部 seam
- `ProviderAuthorityLockService`
- 最小 `identity_provider_state` additive Flyway migration
- 基于现有 `ClientRegistration` 和受信 claims extractor 的静态 descriptor source
- 兼容期 `AuthMethodCatalog` 使用 descriptor/Authority 状态过滤
- 中立的 Login/Provisioning Policy Context
- `AccountLoginGuard`
- `PlatformPrincipalFactory`
- GitHub/GitLab/OIDC 迁入统一入口
- 本地密码登录复用 `AccountLoginGuard` 和 `PlatformPrincipalFactory`
- 继续使用已有 `PlatformSessionService`
- 现有 `identity_binding` 继续使用

明确不做：

- LDAP
- DingTalk
- CAS
- SAML
- SCIM
- Provider 动态配置
- typed Subject/Alias 持久化
- 新 Maven SPI 模块
- 除最小 Authority Lock 表之外的数据库迁移
- `PlatformPrincipal` 结构变化

验收门槛：

- 现有三类 OAuth/OIDC 登录行为无回归。
- Provider code、Authority、Subject 和属性映射只能来自受信 descriptor。
- 相同 Provider code 跨重启切换 Authority 时 fail closed。
- 非空 Alias 在阶段 2 前 fail closed。
- 已迁移的 GitHub/GitLab/OIDC Adapter 无法直接构造 Principal；历史
  `DirectAuthProvider`/`PassiveSessionAuthenticator` 的全局收口属于阶段 4。
- 只有 Authenticated outcome 建立 Session。
- 19.1 节阶段 1 测试子集通过。

#### 阶段 2：Binding V2 和 typed Subject/Alias

范围：

- `identity_binding_subject`
- Binding status/timestamps
- additive Flyway migration
- legacy 回填、双读、双写
- Alias conflict
- 并发首次登录
- 旧数据库升级测试

验收门槛：

- 现有 OAuth Binding 平滑升级。
- rolling upgrade 和回滚路径有测试证据。
- 每个 ACTIVE Binding 恰有一个 ACTIVE primary。

#### 阶段 3：Profile Sync 和 Provisioning Policy

范围：

- `user_profile_field_source`
- 字段同步策略。
- `AUTO/APPROVAL/EXISTING_BINDING_ONLY`
- email collision → 仅返回 reason code 的 `LINK_REQUIRED`，不自动绑定
- 审计和指标。

验收门槛：

- 用户/管理员资料不被外部登录覆盖。
- PENDING 审批链路完整。
- email collision 不自动绑定。
- 阶段 5 完成前，`LINK_REQUIRED` 只提供安全提示和已有账号登录入口，不生成可直接完成绑定
  的 challenge/token。

#### 阶段 4：Provider Registry 和旧 SPI 收口

范围：

- Provider Instance/Authority/State。
- `/auth/methods` 从 Registry 投影。
- 新 Browser/Credential/Passive Adapter 契约。
- `DirectAuthProvider`、`PassiveSessionAuthenticator` 不再返回 Principal。
- Provider Conformance Kit。
- 依赖方向检查。

验收门槛：

- 至少现有 OAuth Adapter 和一个测试 Adapter 使用 Registry。
- disabled/misconfigured Provider 不出现在目录且不联网。
- 旧前端登录目录 API 保持兼容。

#### 阶段 5：显式 Identity Link

范围：

- link intent。
- 当前账号 fresh reauthentication。
- 目标 Provider 独立再认证。
- 解绑最后登录方式保护。
- 一次性 request、短 TTL、重放保护和审计。

验收门槛：

- 无法只凭 email 或 username 创建 Binding。
- Link 重放、过期、并发消费和最后登录方式测试通过。

#### 阶段 6：安全 Account Merge

Account Merge 使用独立设计和阶段验收，不把高风险的数据迁移藏在 Identity Link 中。

范围：

- merge intent 和主、次账号独立控制权证明。
- 角色、Namespace membership、Binding、Token 冲突预览。
- 原子迁移和次账号 `MERGED` 状态。
- 次账号 Session 撤销及 API Token 撤销/迁移策略。
- 一次性 proof、短 TTL、重放保护和审计。

验收门槛：

- 主账号会话不能取得或代替次账号所有权 proof。
- 无法只凭 email、username 或主账号返回的 token 合并。
- Merge 重放、过期、冲突、事务回滚和 Session/API Token 测试通过。

### P2：当前明确协议需求

阶段 1 至阶段 4 完成且 Adapter Interface 冻结后，以下 Adapter 可以由维护者或外部贡献者
开发。可以并行编码，但必须逐个进入 `big-main` 或等价集成验证分支，并完成测试机验证。

#### 阶段 7：LDAP/AD Adapter

- 可邀请 #437 原作者迁移。
- 不复用按 email 建号/复用账号逻辑。
- OpenLDAP、AD fixture、LDAPS/StartTLS。
- 真实升级和重复登录测试。

#### 阶段 8：DingTalk Adapter

- 可邀请 #467 原作者迁移。
- 复用经过 Review 的 token/userinfo transport。
- unionId/openId/userId typed Alias。
- 无 verified email 时不参与 EMAIL_DOMAIN。

DingTalk 的目标支持等级是 Native Tier 1。实现可以来自社区，但合入后由项目承担 Tier 1
的兼容、安全和维护责任。

#### 阶段 9：CAS Adapter

- 从 #464 复用协议和测试思路。
- 使用成熟 CAS Client。
- 不复用仅把 `OAuthClaims` 泛化为浅 `IdentityClaims` 的核心方式。

LDAP 和 DingTalk 不要求必须由维护者亲自编码。更准确的规则是：

> 维护者负责冻结统一 Interface、安全不变量、数据迁移和最终验收；协议 Adapter 可以由
> 外部贡献者实现，但不得越过统一核心。

### P3：Broker 与企业扩展

#### 阶段 10：Broker 集成指南

- Keycloak LDAP/Kerberos/SAML → OIDC → SkillHub。
- authentik Source/Proxy → OIDC → SkillHub。
- Dex LDAP/SAML Connector → OIDC → SkillHub。
- Provider Authority、claim 和 email assurance 配置示例。

#### 阶段 11：Trusted Gateway Assertion

- mTLS 或签名 JWT。
- Header stripping。
- issuer/audience/expiry/replay。
- 默认关闭。

#### SAML 原生决策点

满足真实需求、测试 IdP 和维护责任门槛后，再进入原生 SAML 阶段；否则保持 Broker 支持。

### P4：生命周期和权限映射

#### 阶段 12：SCIM 2.0 独立设计与最小实现

- User lifecycle。
- Group lifecycle。
- 禁用和 Session 撤销。
- SCIM binding。
- 不建立登录 Session。

#### 阶段 13：External Entitlement Mapping

- 外部 group/claim → 中间 entitlement。
- 管理员显式配置 → platform/namespace role。
- 禁止默认映射 `SUPER_ADMIN`。

## 21. 贡献与维护责任

### 21.1 责任划分

| 工作 | 谁可以编码 | 谁承担最终责任 |
|---|---|---|
| 正式设计、威胁模型 | 维护者或贡献者 | 维护者批准 |
| 统一身份核心 | 维护者或高信任贡献者 | 维护者主导安全 Review |
| 数据库迁移和升级 | 维护者或贡献者 | 维护者验证升级/回滚 |
| Provider Registry/SPI | 维护者或贡献者 | 维护者保证兼容 |
| LDAP/DingTalk/CAS/SAML Adapter | 外部贡献者可以实现 | 维护者 sponsor + 最终验收 |
| 协议测试 | 外部贡献者可以实现 | 合并前必须通过 |
| `big-main` 和测试机部署 | 维护者 | 维护者 |
| Tier 1 后续安全维护 | 原作者、社区 owner、项目维护者 | 项目必须有人接管 |

代码可以来自社区，但安全责任、Interface 稳定性和发布责任不能外包。本文中的 Tier
只描述项目支持承诺，不描述提交者身份。

### 21.2 Adapter 合入要求

每个 Adapter 阶段必须：

- 一个协议一个阶段。
- 基于已合并的核心 Interface。
- 不自行创建平台账号或角色。
- 不注册绕过核心的 Controller/Filter。
- 通过 Provider Conformance Kit。
- 提供协议集成测试。
- 提供配置、部署和排错文档。
- 新功能默认关闭。
- 在 `big-main` 和测试机验证。
- 明确长期 owner 或降级为 Tier 2。

### 21.3 当前 PR 的协作方式

#630 是设计 PR，不承载后续所有实现。实现阶段按第 20 节逐个创建 PR，并在合入 `main`
后再推进下一个阶段，除非维护者明确批准并行。

#437、#467 暂时保持开放：

1. 告知作者统一身份核心正在冻结。
2. 不要求作者继续在旧身份模型上堆补丁。
3. 核心合并后邀请作者选择：
   - 自己迁移到新 Adapter；
   - 同意维护者提取协议客户端和测试；
   - 由其他社区贡献者接手。
4. 对应阶段 PR 中的替代实现真正进入 `main` 后再决定是否以 superseded 关闭。
5. 关闭或替代时保留需求来源、设计探索和原作者贡献说明。

## 22. 待决问题

以下问题不阻塞设计批准，但必须在对应实现阶段前决定：

1. Provider Authority 的管理员迁移命令和审批流程。
2. Subject 是否需要 HMAC lookup/加密存储，以及密钥轮换方案。
3. Provider-authoritative profile 与现有人工资料审核流程如何协同。
4. Account Merge 后 API Token 默认迁移还是撤销。
5. SCIM delete 的禁用、保留和重新激活语义。
6. External group 映射的配置位置和审计粒度。
7. Native SAML 的真实需求数量和长期维护者。
8. DingTalk `unionId/openId/userId` 在不同应用/企业配置下的精确 Authority 范围。
9. CAS principal 不稳定时，管理员如何选择 immutable attribute。
10. Provider health 是启动校验、周期探测，还是仅按请求结果更新。

## 23. 决策检查清单

设计批准前确认：

- [ ] 同意 Provider 只产出 `ProviderAuthenticationResult`，不返回 Principal。
- [ ] 同意 email 默认不参与自动绑定。
- [ ] 同意 Provider code 固定 Authority。
- [ ] 同意 typed Subject + Alias 数据模型。
- [ ] 同意 Login Policy 与 Provisioning Policy 分开。
- [ ] 同意 SCIM 使用独立 Provisioning 链路。
- [ ] 同意第一阶段不做运行时 JAR 插件。
- [ ] 同意原生 + Broker 混合协议策略。
- [ ] 同意第一个核心架构阶段（在 P0 安全热修之后）不夹带 LDAP、DingTalk、CAS。
- [ ] 同意协议 Adapter 可以由外部贡献者实现。
- [ ] 同意所有协议先进入 `big-main` 和测试机验收。

## 24. 结论

SkillHub 不需要成为另一个全功能身份平台，但必须拥有自己的平台账号安全核心。最稳妥的
架构不是为 LDAP、DingTalk、CAS、SAML 各复制一套登录和建号逻辑，也不是把所有协议塞进
一个万能 Provider，而是：

```text
协议 Adapter 证明外部身份并产出 ProviderAuthenticationResult
  → 核心归一化为统一 IdentityAssertion
  → SkillHub 统一身份核心决定准入、绑定、建号、同步和账号状态
  → 事务提交后统一建立 Session
```

常用、明确有产品价值且能够长期测试的协议可以原生实现；复杂长尾协议通过成熟 Broker
转换为 OIDC。统一核心和安全边界由维护者负责，具体协议 Adapter 可以充分利用社区贡献。
