## Why

SkillHub 现有 OAuth、local login、CLI Device Flow 和 API Token 能完成平台登录，但不同外部身份源的账号关联、资料权威、账号状态与会话建立仍缺少统一决策边界。企业身份还额外需要成员状态、租户边界和会话失效控制。直接同时上线 OIDC、SAML、CAS、LDAP、SCIM 和组织同步，会把协议正确性、身份安全与目录一致性风险叠加在一次发布中，无法充分验证和独立回滚。

本变更先交付可独立上线的统一身份认证基础版本：所有外部登录先收口到协议无关的统一身份核心；企业 Organization 登录只是统一核心的一个登录上下文，而不是另一套账号体系。首批同时提供最小 Organization 安全边界，并仅启用经过完整验证的动态 OIDC。后续公共 Provider、企业 Provider 和组织同步沿相同扩展契约分批交付。

具体的合并单元、部署顺序、启用门槛和后续认证/组织同步边界见 [rollout-plan.md](./rollout-plan.md)。

## What Changes

- 新增独立于 Namespace 的 Organization、Membership、已验证域名和组织管理角色，作为企业登录的租户与撤权边界。
- 新增版本化 Login Connection 控制面，区分草稿、测试、激活、暂停和禁用状态；认证数据面只读取不可变的已激活 revision。
- 新增协议无关的认证 Adapter 契约和统一身份决策核心。Adapter 只验证协议并输出标准化事实，不得自行建号、绑定成员或创建会话。
- 明确区分 Provider 协议和登录上下文：GitHub、GitLab、飞书、钉钉、OIDC 等 Provider 可服务于平台公共登录，也可在独立批次中服务于企业 Organization 登录；账号创建、绑定、资料写入和 session 建立必须继续由统一身份核心完成。
- 首批新增动态 OIDC：安全获取 discovery/JWKS、使用一次性 state/nonce/PKCE 事务、验证 ID Token，并通过统一身份核心完成关联和会话建立。
- 新增 External Identity V2，以 Organization、Connection、Issuer、typed Subject 形成唯一身份坐标；email 只在已验证且策略显式允许时参与关联。
- 新增预供给 immutable subject、可选 verified-email correlation 和可选 JIT，默认均采用保守策略；冲突和历史成员状态 fail closed。
- 企业会话记录不可逆 session 摘要、身份来源和组织/成员 authority version，使暂停、停用和版本变化能拒绝旧会话。
- 保持现有 GitHub/GitLab OAuth、local login、CLI Device Flow、API Token、平台角色和 Namespace 行为兼容；公共 OAuth 先进入统一身份核心决策门禁，但 legacy `identity_binding` 继续作为持久化权威。公共飞书/钉钉登录属于后续公共 Provider Adapter 批次，不自动代表企业成员身份。
- 新增企业登录发现、组织管理、连接管理和最小 Web 管理界面。
- 动态企业 OIDC 默认关闭，必须同时通过全局开关和 Organization allowlist 才可进入数据面。
- **不在本批次交付**：SAML、CAS、LDAP 登录、飞书/钉钉真实登录 Adapter、Identity Link、Account Merge V2、Directory/SCIM、群组同步、Namespace entitlement 自动投影。

## Capabilities

### New Capabilities

- `enterprise-organizations`: 企业登录所需的最小组织、成员、域名、角色、生命周期与租户隔离边界。
- `federated-authentication`: 协议 Adapter、Login Connection、动态 OIDC、统一账号关联、登录发现和企业会话。
- `enterprise-identity-governance`: 企业连接 Secret、审计、限流、日志脱敏、灰度开关、观测与回滚约束。

### Modified Capabilities

当前 `openspec/specs/` 没有已归档的身份能力规范，本变更不声明 modified capability。既有认证入口只做兼容迁移，不改变其外部契约。

## Impact

- 后端：`skillhub-domain` 增加 Organization 聚合；`skillhub-auth` 增加连接、OIDC、统一身份、External Identity 和 session origin；`skillhub-app` 增加管理与匿名认证 API、查询编排和审计。
- 前端：增加组织概览、成员、域名和登录连接管理入口；复用现有登录页并通过 discovery 展示企业登录方式。
- 数据：在现有 Skill Suites 的 V49–V53 之后新增 V54–V60；迁移只向前增加表、约束和兼容回填，不删除 legacy binding。
- 配置：新增身份核心模式、动态 OIDC 开关、Organization allowlist、OIDC callback base、Secret envelope keyring 和相关限流配置。
- 网络：仅 OIDC metadata、JWKS 和 token endpoint 需要受控出站访问；地址解析、协议、重定向和响应大小受策略限制。
- 发布：必须先完成 OpenSpec strict validation、全量源代码测试、前端检查、迁移测试、exact-SHA 本地镜像、真实 OIDC 参考实现和浏览器登录/撤权验收，才可进入 PR/合并阶段。
- 分批：完整参考实现不得直接等同于一次性生产启用；统一身份核心、控制面和 OIDC 数据面按 `rollout-plan.md` 分批重组，每批重新生成 exact-SHA 验证记录。
