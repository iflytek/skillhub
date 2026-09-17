# 统一身份与企业身份平台分批合并与上线计划

## 文档状态

- 状态：人工评审稿
- 适用范围：`enterprise-identity-platform` Release 1 及其后续认证、目录同步批次
- 当前参考实现：`feature/enterprise-login-release`
- 当前参考验证：见 [verification.md](./verification.md)
- 本文不授权创建 PR、合并、部署、修改生产配置或导入真实企业数据

本文回答的不是“完整企业身份平台最终长什么样”，而是“如何把已经完成的第一版实现拆成可以逐次合并、逐次部署、逐次启用和逐次验证的发布单元”。稳定需求仍以 `specs/` 为准，架构决策仍以 [design.md](./design.md) 为准。

## 1. 为什么需要分批

当前参考分支同时包含统一身份核心、Organization 安全边界、Login Connection 控制面、动态 OIDC 数据面、管理页面、数据库迁移和身份测试实验室。它已经作为一个整体通过本地验证，但整体通过不等于适合一次性在生产启用。

企业身份有三类不同风险：

1. **账号风险**：同一个外部身份是否稳定关联到原来的 Platform Account，是否可能误建号、误合并或覆盖可信资料。
2. **租户风险**：Organization、Membership、角色、连接和会话是否严格隔离，停用后是否立即撤权。
3. **协议风险**：OIDC、SAML、CAS 等协议的回调、签名、重放、出站请求和供应商差异是否处理正确。

如果三类风险同时开放，出现问题时很难判断是账号关联、组织权限还是协议 Adapter 导致，也难以只关闭故障面。因此必须把合并、部署和启用拆开。

## 2. 三种动作必须区分

| 动作 | 含义 | 是否对用户立即可见 |
|---|---|---|
| 合并 | 代码进入 `main` | 不一定 |
| 部署 | 构建产物运行在环境中，Flyway 可能执行向前迁移 | 不一定 |
| 启用 | 通过模式、数据面开关和 Organization allowlist 让请求进入新路径 | 是 |

一批代码可以先合并并部署，但保持默认关闭。只有该批验证通过后才启用；启用失败时优先关闭运行时入口，不删除数据库记录，也不执行破坏性降级迁移。

## 3. 总体发布序列

```text
R1-A 统一身份核心与兼容迁移
  -> R1-A2 公共 Provider Adapter 扩展（飞书/钉钉等，按 Provider 分批）
  -> R1-B Organization 与 Login Connection 控制面
  -> R1-C 动态 OIDC 登录数据面
  -> OIDC 小范围灰度并扩大
  -> R2-SAML（独立）
  -> R3-CAS（独立）
  -> R4-LDAP 登录（只有产品决策明确后）

认证面稳定后，另起 Provisioning 发布序列：
P1-SCIM 用户生命周期
  -> P2-Group/组织关系同步
  -> P3-Namespace entitlement 投影
  -> P4-厂商目录连接器（按需分别交付）
```

认证面回答“登录者是谁”；Provisioning 面回答“企业中有哪些人和群组”；Entitlement 面回答“这些人获得哪些 Namespace/Skill 权限”。三者可以复用 Organization 和 Platform Account，但不得共享连接类型、生命周期状态或失败处理逻辑。认证面内部还要区分 Provider 协议与登录上下文：公共飞书/钉钉登录只建立平台 session，企业飞书/钉钉登录必须在 Organization/LoginConnection 上下文中执行。

## 4. Release 1 的三个合并批次

### 4.1 R1-A：统一身份核心与兼容迁移

#### 目标

先让现有公共 OAuth 使用统一的账号状态、身份关联和资料权威规则，不开放企业登录入口。该批次证明“统一身份核心不会破坏现有登录”。

#### 包含

- 协议无关的 Adapter descriptor、`IdentityAssertion`、typed subject 和标准失败分类。
- 统一身份决策：已有 binding、预供给 subject、verified email、JIT、账号与 Membership guard。
- External Identity V2 用于企业登录基础；公共 OAuth 先接入统一身份核心决策门禁，legacy binding 继续作为持久化权威。
- 现有公共 OAuth 到统一核心的 bridge。
- merged、system、disabled 等账号状态保护及 Profile Authority。
- 公共 OAuth 在 ACTIVE 模式下遇到统一核心 Denied/Conflict 时先于 legacy binding 写入 fail closed，并保留低基数观测指标。
- 为后续企业认证准备的最小 Organization/Connection schema 与内部模型，但不开放管理入口。
- 本批次所需测试、迁移护栏和运维说明。

#### 数据库策略

V54–V60 是一条已经联合验证的 expand-only 迁移链：

| Migration | 主要内容 |
|---|---|
| V54 | Organization、Domain、Membership 和角色基础 |
| V55 | 企业审计上下文 |
| V56 | Login Connection、revision、External Identity V2 和 session origin 基础 |
| V57 | 加密 Secret version |
| V58 | 预供给 immutable subject |
| V59 | 登录关联策略 |
| V60 | 兼容性校验与后续 public OAuth V2 迁移预留，不作为 R1-A 启用门槛 |

虽然 R1-A 不开放 Organization 管理功能，但 V56 及后续企业认证表依赖这条 schema 链。建议在 R1-A 一次部署 V54–V60，保持全部新业务入口关闭。不要为了让 PR 看起来更小而重新编号、拆改或跨批次修改已经部署的 migration。公共 GitHub/GitLab 可以保留 V60 影子回填和一致性校验；R1-A 不切换公共 OAuth 的运行时写入权威，生产登录仍写入 legacy `identity_binding`。

#### 部署与启用

1. 合并并部署，保持 `SKILLHUB_IDENTITY_CORE_MODE=LEGACY`。
2. 验证 local login、公共 OAuth、CLI Device Flow、API Token 和既有 Session。
3. 切换为 `SHADOW`，新核心只做比较，旧 binding 继续决定结果；观察 mismatch、fallback 和异常。
4. 在独立验收通过后切换为 `ACTIVE`。
5. OIDC 控制面、OIDC 登录数据面和 Organization allowlist 继续保持关闭/为空。

`SKILLHUB_IDENTITY_CORE_MODE` 对 platform-scoped 公共 OAuth 是全局模式，不能按 Organization 灰度。因此从 `SHADOW` 切换到 `ACTIVE` 是本批次明确的独立发布决策，不能被后续 OIDC 灰度顺带触发。

#### 进入下一批的门槛

- 空库 V1–V60 和现有 V53→V60 升级均成功。
- `LEGACY`、`SHADOW`、`ACTIVE` 下公共 OAuth 返回同一 Platform Account。
- local login、CLI Device Flow 和 API Token 外部契约不变。
- ACTIVE 下统一身份核心 Denied/Conflict 能够在 active/pending legacy binding 写入前 fail closed；日志不包含 subject、email 或 token。
- 公共 Provider 登录不会创建 Organization Membership、企业 session origin 或 Namespace 权限。
- 经过约定观察窗口后没有未解释的 mismatch、登录失败率或重复账号。

#### 回滚

将 `SKILLHUB_IDENTITY_CORE_MODE` 切回 `LEGACY` 并重新验证现有登录。V54–V60 数据保留；不删除新表或 V2 企业身份数据，不执行 down migration。

### 4.1.1 R1-A2：公共 Provider Adapter 扩展

#### 目标

在统一身份核心稳定后，按 Provider 单独接入飞书、钉钉等公共登录入口。该批次只证明“这个外部账号可以登录 SkillHub 平台账号”，不建立企业成员身份。

#### 包含

- Provider 专属 authorization、token、userinfo 或 equivalent API 客户端。
- 稳定 subject 选择、email verified 语义、avatar/displayName 资料权威映射。
- `/api/v1/auth/methods` catalog 展示、图标、登录按钮和 returnTo 保留。
- 公共登录回调进入统一身份核心或当前 R1-A 兼容入口。
- Provider 专属错误映射、日志脱敏、远程 I/O 超时与响应大小限制。

#### 不包含

- Organization Login Connection。
- 企业成员 JIT 或预供给。
- 通讯录、部门、群组或 SCIM 同步。
- Namespace 权限自动授予。

#### 进入下一批的门槛

- 真实 Provider 或可重复参考服务完成登录、重复登录、禁用账号和错误回调验证。
- 该 Provider 在 LEGACY/SHADOW/ACTIVE 或当前等价模式下返回同一 Platform Account。
- 未配置 client id/secret 时 catalog 不展示按钮；placeholder 不展示。
- 账号禁用、merged/system 账号、未验证 email 和资料写入边界与 GitHub/GitLab 一致。
- 登录成功只建立平台 session，不产生 Organization Membership 或企业 session origin。

### 4.2 R1-B：Organization 与 Login Connection 控制面

#### 目标

让管理员能够建立企业身份边界、成员关系和 OIDC 连接配置，但普通用户仍不能从匿名入口发起企业登录。该批次证明“企业配置和权限模型正确”，不同时承担真实登录风险。

#### 包含

- Organization、Membership、Domain、角色和 authority version 生命周期。
- 平台创建 Organization 与租户内部管理权限分离。
- Organization、成员、域名、角色和审计 API。
- Login Connection、不可变 revision、测试后激活和 suspend/disable 生命周期。
- client secret envelope encryption、Secret version 和脱敏摘要。
- Organization 与 Login Connection 管理页面。
- 稳定分页、租户隔离、并发冲突和审计测试。
- 控制面所需的 OIDC 配置校验与连接探测，但不开放匿名 start/callback。

#### 部署与启用

1. 保持 R1-A 已验收的 identity core 模式。
2. 注入 Secret keyring 和固定的 OIDC public base URI 等运行配置。
3. 设置 `SKILLHUB_ENTERPRISE_OIDC_ENABLED=true`，只装配控制面。
4. 明确保持 `SKILLHUB_ENTERPRISE_OIDC_LOGIN_ENABLED=false`。
5. 保持 `SKILLHUB_ENTERPRISE_ORGANIZATION_ALLOWLIST` 为空。
6. 只创建测试 Organization，配置、测试并激活测试连接。

#### 进入下一批的门槛

- `SUPER_ADMIN` 可以创建 Organization，但不能绕过目标 Organization Membership 管理租户数据。
- Organization 角色满足最小权限；`IDENTITY_ADMIN` 不自动获得 Secret 管理权限。
- Secret 不出现在 API、UI、审计、异常、应用日志和容器启动日志。
- revision 未测试不能激活；暂停、禁用必须显式确认。
- Organization、Membership 和 Connection 状态变化正确递增 authority version，重复命令幂等。
- 匿名 discovery/start/callback 仍不可用，现有登录入口不受影响。
- 管理 API 的 OpenAPI 产物、Web typecheck/lint/test 和权限测试通过。

#### 回滚

设置 `SKILLHUB_ENTERPRISE_OIDC_ENABLED=false`，关闭控制面组件。已创建的 Organization、Connection、revision、Secret 和审计记录保留，不影响现有公共登录。

### 4.3 R1-C：动态 OIDC 登录数据面

#### 目标

在一个测试 Organization 内开放完整 OIDC authorization-code 登录，验证真实身份源、首次关联、重复登录、Session 和即时撤权，然后再逐个扩大 Organization allowlist。

#### 包含

- 匿名 login discovery、enterprise start 和 callback。
- 固定 callback origin、opaque public handle 和安全 `returnTo`。
- 一次性 state、nonce、PKCE、浏览器绑定和 callback replay 防护。
- discovery/JWKS/token/UserInfo 出站访问安全。
- issuer、签名、算法、kid、audience/azp、时效和 nonce 校验。
- 首次登录关联、External Identity、企业 Session origin 和 authority version 重检。
- 匿名限流、代理信任、错误收敛、日志脱敏和安全审计。
- `oidc-fast`、Keycloak `oidc-real`、真实浏览器和回滚演练。

#### 部署与启用

1. 部署后继续保持 `SKILLHUB_ENTERPRISE_OIDC_LOGIN_ENABLED=false`。
2. 使用测试 Organization 完成控制面连接测试和激活。
3. 确认 identity core 已经在 R1-A 独立验收为 `ACTIVE`。
4. 只把测试 Organization 放入 `SKILLHUB_ENTERPRISE_ORGANIZATION_ALLOWLIST`。
5. 设置 `SKILLHUB_ENTERPRISE_OIDC_LOGIN_ENABLED=true`。
6. 完成首次登录、重复登录、退出、成员暂停、连接暂停和错误路径验收。
7. 一个 Organization 一个 Organization 地扩大 allowlist；禁止直接使用全量或隐式通配。

#### 进入扩大灰度的门槛

- `oidc-fast` L2 和 Keycloak `oidc-real` L3 均基于该批 exact SHA 通过。
- 真实浏览器完成 discovery → redirect → IdP → callback → session → logout。
- 首次登录和重复登录只保留一个 Platform Account、Membership 和 External Identity。
- 错误 state/nonce/signature/audience/issuer、callback replay 和跨 Organization 冲突全部 fail closed。
- Organization、Membership 或 Connection 暂停后，旧企业 Session 不等待 TTL 即被拒绝。
- 日志、报告和持久容器日志不含 token、code、state、nonce、Cookie、client secret 或测试密码。
- 本批的资源上限、清理脚本和回滚演练通过。

#### 回滚

按以下顺序缩小故障面：

1. 从 Organization allowlist 移除受影响组织。
2. 设置 `SKILLHUB_ENTERPRISE_OIDC_LOGIN_ENABLED=false`。
3. 如问题位于统一身份核心，再将 `SKILLHUB_IDENTITY_CORE_MODE` 切回 `LEGACY`。
4. 必要时最后关闭 `SKILLHUB_ENTERPRISE_OIDC_ENABLED` 控制面。

回滚不删除 Organization、Membership、Connection、External Identity、Session origin 或审计数据。

## 5. 测试必须跟随功能批次

不能先合功能、最后再补测试。每个批次至少同时携带以下验证资产：

| 层级 | R1-A | R1-A2 公共 Provider | R1-B | R1-C |
|---|---|---|---|---|
| L0 契约/架构 | Adapter 与身份决策边界 | Provider 与登录上下文分离 | 租户/RBAC/Secret 边界 | 协议和匿名入口边界 |
| L1 单元/持久化 | 关联、兼容、迁移、账号 guard | subject、verified email、错误映射、catalog | 生命周期、并发、分页、审计 | OIDC 校验、事务、限流、Session |
| L2 exact-SHA | 现有认证与数据库升级 | 配置开关、按钮展示、returnTo、无企业副作用 | 控制面 default-off | `oidc-fast`、开关和回滚 |
| L3 真实实现/浏览器 | 不要求 | 真实厂商测试账号或明确参考服务 | 只要求连接探测 | Keycloak 与真实 Windows 浏览器 |

Keycloak、CoreDNS、Caddy、mock provider 都是隔离的测试设施，不是生产依赖。测试 Profile 必须串行运行并在结束后清理容器、网络、volume、生成凭证和浏览器 Profile。公共 Provider 批次不能只靠 mocked catalog 按钮验收；按钮测试只证明展示和跳转，真实认证必须使用厂商测试账号、官方沙箱或写明边界的参考服务单独证明。

## 6. 当前参考分支如何重组

当前 `feature/enterprise-login-release` 保留为完整、已验证的参考实现，不对它做交互式 rebase 或历史重写。后续得到授权再按以下方式重组：

1. 从当时最新 `main` 创建 R1-A 工作分支。
2. 按能力和文件依赖提取实现，把后期安全修复折叠回所属代码，而不是机械 cherry-pick 一段早期提交。
3. 保证 R1-A 独立编译、迁移、测试和运行；完成审查、合并、部署与观察后结束该批。
4. 从已经包含 R1-A 的最新 `main` 创建 R1-B，重复完整验证。
5. R1-B 合并并完成控制面验收后，再从最新 `main` 创建 R1-C。
6. 每批生成自己的 exact-SHA 验证记录；当前 [verification.md](./verification.md) 只能作为整体参考证据，不能替代重组后 SHA 的验证。

重组过程中可能需要少量代码调整来消除批次间的编译依赖。是否属于 R1-A、R1-B 或 R1-C，以运行时职责和最小可独立验证单元判断，不以原提交时间判断。

### 6.1 OpenSpec 归属

当前 `enterprise-identity-platform` change 保留为总体架构、完整行为边界和参考验证记录。真正开始重组时，R1-A、R1-B、R1-C 分别建立自己的 OpenSpec change，并分别维护：

- 本批 proposal、delta spec、design 和 tasks。
- 本批相对最新 `main` 的明确范围与非目标。
- 本批 migration、配置、兼容和回滚边界。
- 本批 exact-SHA 验证记录。

不能让三个合并批次共同复用当前已经全部勾选完成的 `tasks.md`，否则后续无法判断某个 PR 到底完成了哪一批要求。子 change 只引用本总体设计，不复制整份架构说明；稳定行为在对应批次完成并验收后，再按 OpenSpec 流程归档到正式 specs。

### 6.2 当前参考工作区的本地审查包

当前 `feature/enterprise-login-release` 不是最终 PR 形态。人工审查时先按以下四组看，不把它们理解成必须一次合并的单个补丁：

#### A. OpenSpec 与上线边界

目的：确认统一身份、企业登录、公共 Provider 和组织同步的职责边界，避免把 SCIM、Namespace entitlement、飞书/钉钉和 V2 写入权威切换混入 R1-A。

- `openspec/changes/enterprise-identity-platform/proposal.md`
- `openspec/changes/enterprise-identity-platform/design.md`
- `openspec/changes/enterprise-identity-platform/rollout-plan.md`
- `openspec/changes/enterprise-identity-platform/tasks.md`
- `openspec/changes/enterprise-identity-platform/verification.md`
- `openspec/changes/enterprise-identity-platform/specs/**/spec.md`
- `design-qa.md`

审查重点：R1-A 只要求 public OAuth 进入统一身份核心决策门禁；legacy `identity_binding` 仍是运行时写入和回滚权威。V60 影子回填是校验/回滚辅助，不等于切换写入权威。

#### B. 后端认证兼容与统一核心门禁

目的：在不改变现有 public OAuth 写入路径的前提下，让 GitHub/GitLab 等公共 OAuth 先经过统一身份核心决策；ACTIVE 模式下 Denied/Conflict 必须在 active/pending legacy binding 写入前 fail closed。

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCore.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityDecision.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCoreBridge.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginRedirectSupport.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/oauth/*Test.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java`
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/service/AuthMethodCatalogTest.java`

审查重点：LEGACY/SHADOW 不改变旧登录结果；ACTIVE 只在统一核心明确拒绝或冲突时阻断。错误日志不得包含 subject、email、token 或 client secret。

#### C. 配置驱动登录页与回跳安全

目的：登录页由服务端 auth catalog 决定显示哪些入口；个人账号、企业账号和 public OAuth 可以同时存在，但没有配置的入口不展示。登录成功默认回首页，显式安全 `returnTo` 保留来源。

- `web/src/pages/login.tsx`
- `web/src/pages/register.tsx`
- `web/src/features/auth/auth-shell.tsx`
- `web/src/features/auth/auth-entry-switch.tsx`
- `web/src/features/auth/enterprise-login-discovery.tsx`
- `web/src/features/auth/login-button.tsx`
- `web/src/shared/lib/auth-route.ts`
- `web/src/api/types.ts`
- `web/src/app/layout.tsx`
- `web/src/assets/login-network.webp`
- `web/src/assets/login-network.webp.json`
- 对应 `web/src/**/*.test.tsx?`
- `web/e2e/enterprise-identity-flow.spec.ts`
- `web/src/i18n/locales/{zh,en,ru}.json`

审查重点：前端不能硬编码 GitHub、飞书、钉钉等入口为“默认可用”；按钮只来自服务端 catalog。mocked catalog 只能证明展示和跳转，不能证明真实 Provider 登录。

#### D. Organization 管理入口的 UX 边界

目的：成员可以看到自己所属 Organization，但没有组织管理角色时不能进入管理详情；后端 403 仍是最终权限边界。

- `web/src/pages/dashboard/organizations.tsx`
- `web/src/features/organization/organization-admin-shell.tsx`
- `web/src/pages/dashboard/organization-admin-pages.test.tsx`

审查重点：一个账号可以属于多个 Organization；列表页不是“当前企业唯一入口”。是否要改成单组织优先体验是产品交互问题，不应削弱多组织模型。

#### 不进入第一批的内容

- 飞书、钉钉真实 Adapter 合并。
- SAML、CAS、LDAP 登录。
- SCIM、Directory、Group 或 Namespace entitlement 同步。
- public OAuth 运行时写入权威切到 External Identity V2。
- 生产环境导入真实企业组织或真实目录数据。

## 7. 合并与上线纪律

- 同一时间只推进一个功能批次，不预先堆积多个相互依赖的开放 PR。
- 每批必须基于最新 `main`，记录 base SHA、head SHA、镜像 digest、migration 版本和配置状态。
- PR 合并不等于允许部署；部署成功不等于允许启用。
- Flyway migration 一经共享环境执行即视为不可变，只能追加修复 migration。
- 未完成该批退出门槛时，不开始下一批。
- 生产启用先使用一次性测试 Organization 和测试身份，不直接导入真实企业目录。
- 任一关键指标异常时先关闭最窄入口，再判断是否需要应用版本回滚。

## 8. 后续认证协议的交付模板

OIDC 稳定后，SAML、CAS 和可能的 LDAP 登录分别建立独立 OpenSpec。每个协议批次只新增：

- 协议 Adapter 和版本化配置 schema。
- 控制面校验、连接探测和 runtime snapshot materializer。
- 协议特有的安全校验与错误映射。
- 一个成熟的开源参考服务或可重复的测试实现。
- 真实协议流程、浏览器验收、故障路径和回滚说明。

这些批次复用统一身份核心、External Identity、Organization、Membership、Profile Authority 和 Session guard。Adapter 仍然不得决定建号、账号合并、成员授权或 Namespace 权限。

LDAP 可能同时被用作“用户输入企业密码直接登录”和“后台读取企业目录”。这两种能力必须使用不同连接类型和安全边界：前者属于 authentication，后者属于 provisioning。没有明确 TLS、稳定 object id、密码不落盘和连接隔离方案前，不把 LDAP 标记为已支持的登录协议。

## 9. 组织同步的独立发布序列

组织同步不进入 Release 1。后续建议至少拆为三个批次：

### P1：SCIM 用户生命周期

- 建立 Directory Connection 与 source-owned 属性边界。
- 支持 User 创建、更新、停用和幂等重放。
- 维护外部目录对象 ID，不用 email 作为稳定主键。
- 不自动授予 Namespace 权限。

### P2：Group 与组织关系同步

- 同步 Group、成员关系和删除/停用语义。
- 定义全量调和、增量事件、游标、水位和冲突处理。
- 手工 Membership 与目录 Membership 不互相覆盖。

### P3：Namespace entitlement 投影

- 单独配置 Group/Organization 到 Namespace role 的映射。
- 使用 source-aware grant，删除目录来源时不误删手工授权。
- 提供 dry-run、差异预览、审计和可恢复撤权。

厂商连接器按需建立在 P1/P2 之后。钉钉、飞书、企业微信或 LDAP Directory 的数据模型差异由各自 Adapter 吸收，不能把厂商字段直接扩散到 Platform Account、Organization 或 Namespace 核心模型。

## 10. 人工评审需要确认的决定

在开始重组代码前，需要人工明确接受或修改以下决定：

1. 是否接受 R1-A 先部署完整 V54–V60 expand-only schema，但暂不开放 Organization/OIDC 功能。
2. 是否接受公共 OAuth 按 `LEGACY → SHADOW → ACTIVE` 独立完成统一身份核心切换。
3. 是否接受 R1-B 允许管理员配置并测试 OIDC Connection，但匿名企业登录保持关闭。
4. R1-C 首个灰度 Organization、负责人、观察窗口和停止条件是什么。
5. OIDC 之后优先做 SAML、CAS 还是 LDAP 登录；每种协议仍保持独立批次。
6. 是否确认 SCIM/Directory、Group 同步和 Namespace entitlement 分别立项，不与认证协议混合。

以上六项确认前，只保留当前参考分支和文档，不创建分批 PR。

## 11. R1-A 本地提取作业单

本节是从当前参考工作区重组第一批 PR 时的操作边界。它不是新的产品范围；只把本文前面已经定义的 R1-A 落成可检查的文件与验证清单。

### 11.1 第一批只解决什么

R1-A 的可交付结果是：

```text
现有 public OAuth/GitHub/GitLab
  -> 提取已验证 facts
  -> 经过统一身份核心决策门禁
  -> LEGACY/SHADOW 不改变旧结果
  -> ACTIVE 下 Denied/Conflict 在 legacy binding 写入前 fail closed
  -> legacy identity_binding 仍是 public OAuth 运行时写入和回滚权威
```

登录页改版、Organization 管理 UI、动态 OIDC 数据面和飞书/钉钉 Adapter 都不是 R1-A 的必要条件。若为了编译保留少量 schema/config/model，也必须保持入口默认关闭，且 PR 描述中明确“为后续批次铺 schema，不开放用户入口”。

### 11.2 建议进入 R1-A 的文件组

后端认证兼容核心：

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCore.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityDecision.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCoreBridge.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCoreBridgeTest.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowServiceTest.java`

如果 R1-A 同时承载“登录成功默认回首页、returnTo 安全收敛”这个兼容修复，则可以加入：

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginRedirectSupport.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuth2LoginSuccessHandler.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/oauth/OAuthLoginRedirectSupportTest.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/oauth/OAuth2LoginHandlersTest.java`
- `web/src/shared/lib/auth-route.ts`
- `web/src/shared/lib/auth-route.test.ts`
- 导航栏登录 returnTo 保留所需的最小 `web/src/app/layout.tsx` 改动

若加入 returnTo 修复，PR 标题或正文必须单独列为“登录回跳安全兼容修复”，避免 reviewer 误以为它是企业登录 UI 的一部分。

### 11.3 不应进入 R1-A 的文件组

以下内容应留到 R1-B/R1-C 或 UI 批次，除非人工明确改变拆批策略：

- `web/src/pages/login.tsx` 和 `web/src/pages/register.tsx` 的分屏 UI 大改。
- `web/src/features/auth/auth-shell.tsx`、`auth-entry-switch.tsx`、`login-network.webp`。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java` 中声明 `ENTERPRISE_DISCOVERY` 的用户入口逻辑。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/EnterpriseLoginAppService.java` 的 discovery fallback UI 逻辑。
- Organization 列表/管理入口 UX 调整。
- 飞书、钉钉、SAML、CAS、LDAP、SCIM、Directory、Group、Namespace entitlement。
- public OAuth runtime write authority 切到 External Identity V2。

这些文件可以在参考分支保留，供人工体验完整效果；但第一批 PR 不应夹带，否则 R1-A 会变成“统一身份核心 + 新登录页 + 企业发现入口”混合风险。

### 11.4 R1-A 验证命令

提取到新的 R1-A 分支后至少运行：

```bash
openspec validate <r1-a-change-id> --strict
git diff --check

cd server
MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am \
  -Dtest=OAuthLoginFlowServiceTest,LegacyPlatformIdentityCoreBridgeTest,OAuth2LoginHandlersTest,OAuthLoginRedirectSupportTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -DskipTests package

cd ../web
pnpm exec vitest run src/shared/lib/auth-route.test.ts --maxWorkers=1
pnpm run typecheck
pnpm run lint
```

如果 R1-A 没有带任何 Web 改动，则 Web 命令可以退化为 OpenAPI freshness 和现有认证入口冒烟；但 PR 描述必须说明为什么没有登录页/UI 变更。

### 11.5 R1-A 人工验收

R1-A 人工验收只看现有登录是否被统一核心门禁破坏：

1. `LEGACY`：local login、GitHub/GitLab public OAuth、CLI Device Flow、API Token 行为不变。
2. `SHADOW`：public OAuth 登录结果不变；统一核心异常不影响旧登录结果；日志不含 subject、email、token 或 secret。
3. `ACTIVE`：正常 public OAuth 仍登录到同一 Platform Account；统一核心 Denied/Conflict 时 fail closed，且不创建 active 或 pending legacy binding。
4. public OAuth 登录不会创建 Organization Membership、企业 session origin 或 Namespace 权限。
5. 如果包含 returnTo 修复：无 returnTo 登录回 `/`；安全应用内 returnTo 保留 query/hash；外部 URL、协议相对 URL、反斜杠和控制字符回 `/`。

只有以上通过后，才进入 R1-B 控制面。不要因为参考分支里的完整登录页已经能跑，就跳过 R1-A 的独立验收。
