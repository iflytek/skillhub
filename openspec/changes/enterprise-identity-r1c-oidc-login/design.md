## 员工和管理员怎么操作

1. 运维配置 SkillHub 对外回调地址和密钥管理，并在企业身份源为 SkillHub 注册应用。
2. 组织身份管理员在 R1-B 的连接管理中保存、探测并启用 OIDC 配置，密钥管理员通过既有入口录入客户端密钥。
3. 试点启用前，核对员工在该 OIDC 客户端下的不可变 subject 与目标组织成员关系。用已有有效绑定或同组织、同连接的预授权记录确认首批人员。只在本地实验室用隔离测试数据准备记录。试点运维路径依相同权限和审计约束交付。
4. 员工选择「通过组织登录」并输入组织标识，再通过企业身份源验证。SkillHub 根据外部身份和当前成员状态决定是否建立企业会话，成功后返回站内目标。
5. 管理员暂停组织、成员或连接时，后续企业访问立即重新校验授权。恢复连接后重新登录，已失效会话不会恢复。

## 职责和控制边界

- 登录发现只读取当前可用的组织及连接公开信息，响应不包含内部 ID、issuer、客户端密钥或成员信息。使用不透明连接句柄定位服务器端配置。组织无可用连接时给出通用结果，保留公共登录方式。
- 重定向入口建立短期事务，绑定请求时的连接版本、固定回调地址、state、nonce、PKCE、浏览器随机值和安全站内回跳。回调验证绑定和过期后原子消费，只有一次进入授权码交换；失败和成功都清除事务 Cookie。
- OIDC adapter 通过受限出站访问取得并验证协议结果，经既有 adapter 契约产出标准化身份事实。客户端凭据仅在授权码交换时读取和使用。配置探测与真实登录共享网络安全约束，登录端点不继承管理界面的信任。
- 复用 EnterpriseIdentityAssociationService 及已有 subject 坐标持久化，按有效绑定、明确预授权的顺序关联。关联和 Session 提交前重新读取账户、组织、成员及连接授权。使用既有企业会话来源及授权版本契约，避免把企业上下文复制给公共登录、API Token 或普通 CLI 会话。
- 连接激活新版本后，新发起的登录使用新版本。旧回调只能使用启动事务绑定且仍可用的快照。连接已暂停、禁用或旧快照无法按原值解析时拒绝完成，不换用新的客户端凭据。
- 采用当前 main 的连接、版本、密钥、subject 及会话来源数据模型。先核对现有 schema 与 Spring 装配，发现缺口时再明确是否需要独立迁移。

## 当前主线可复用的部分

| 主线已有 | R1-C 接续 |
| --- | --- |
| `EnterpriseConnectionRegistry` 和 `LoginConnectionRuntimeSnapshot` 契约 | 将当前启用的连接版本接入运行时 registry，以真实 snapshot 驱动协议。 |
| `OidcConnectionProbe` 和受限的 OIDC HTTP 读取 | 复用访问约束，补授权码交换、令牌校验及协议事务。 |
| `EnterpriseIdentityAssociationService` 的绑定和预授权 subject 路径 | 使用关闭邮箱和 JIT 策略的关联入口，为试点接线。 |
| V62 的 `identity_session_origin` 和 V128 的登录密钥版本表 | 接入企业会话来源写入、授权重检和运行时密钥读取，按新增行为验证数据库。 |

## 开启、故障与回滚

只有身份核心允许、OIDC 员工登录开关开启、组织在试点名单且连接已生效时才开放发现与登录。真实验证之前仅使用一次性测试组织及账号。异常回调不得产生 Session，也不得把授权码、Cookie、token 或响应正文写入日志。故障时先移出受影响组织，再关闭员工登录开关，组织管理按自身权限继续可用。

## Casdoor 对本方案的参考边界

Casdoor 对 SkillHub 的主要参考价值在于身份平台的组织方式。它把身份提供方、应用、组织、用户、组和权限策略做成可配置对象，并通过统一的登录入口承载多种 Provider。SkillHub 可以借鉴这些对象的职责划分和 Provider 注册方式。

SkillHub 保留自己的业务身份核心。Casdoor 的用户和组织对象不能直接替代 SkillHub 的 Platform Account、Organization、Membership、External Identity、Session Origin 和 Namespace 授权。企业登录成功后，SkillHub 仍要按自己的组织成员状态和 authority version 决定是否建立企业会话。

| Casdoor 可参考的能力 | SkillHub 的落点 | 使用边界 |
| --- | --- | --- |
| Provider 注册与协议适配 | Adapter Registry、OIDC/SAML/CAS/LDAP Adapter | Adapter 只返回已验证身份事实，不创建 SkillHub 账号或成员 |
| Application 与回调配置 | Login Connection、不可变 revision、回调策略 | 每个 Organization 的连接由 SkillHub 管理，密钥和版本沿用 SkillHub 控制面 |
| Organization、User、Group 组织模型 | Organization、Membership、Group 同步模型 | Casdoor 的组织关系不能直接变成 Namespace 权限 |
| Permission / Role policy | SkillHub Organization Role 和 Namespace Entitlement | SkillHub 的资源授权继续由自己的策略和审计模型决定 |
| 登录页和多 Provider 入口 | 现有 SkillHub 登录页的配置驱动入口 | 显示入口不代表拥有企业成员资格 |

如果未来把 Casdoor 作为外部身份中心部署，SkillHub 只把它当作一个 OIDC 身份源接入。SkillHub 不把 Casdoor 数据库当作本地权限数据库，也不让 Casdoor 绕过成员、连接和企业会话校验。这样可以保留未来接入飞书、钉钉、企业微信、LDAP、CAS 和其他 OIDC/SAML 服务的统一边界。

## 新的整体分阶段方案

### 阶段 0：统一身份核心稳定

现有公共 OAuth、Local Account、CLI Device Flow 和 API Token 统一经过身份状态、账号绑定和资料权威检查。R1-A 的 LEGACY、SHADOW、ACTIVE 模式独立观察和回滚。

验收重点是重复登录得到同一 Platform Account，禁用、合并和未验证邮箱边界正确，公共登录不会创建企业 Membership。

### 阶段 1：公共 Provider 扩展

按 Provider 接入 GitHub、GitLab、飞书、钉钉等公共登录。Provider Adapter 负责授权、Token、UserInfo 和 subject 解析，统一身份核心负责账号决策。

验收重点是每个 Provider 的重复登录、错误回调、禁用账号和日志脱敏。公共飞书或钉钉登录只建立平台会话。

### 阶段 2：Organization 与 Login Connection 控制面

R1-B 已完成 Organization、成员、角色和 OIDC 连接管理。管理员可以保存版本化连接、录入密钥、探测 discovery/JWKS、启用、暂停、恢复和停用连接。

验收重点是角色分离、版本一致性、密钥不回显、跨组织隔离和默认关闭。

### 阶段 3：R1-C 企业 OIDC 登录试点

先按明确 Organization 标识查找可用连接，再通过 opaque handle 进入 start。浏览器事务绑定 state、nonce、PKCE、连接 revision 和固定回调地址。callback 完成协议验证后，统一身份核心按已有绑定或预授权 subject 建立企业会话。

第一批开发顺序是登录发现和 start 事务，其次是 callback 与 ID Token 校验，再接身份关联和企业 Session Origin，最后做成员暂停、连接暂停、退出和回滚验收。

### 阶段 4：域名验证与组织发现

在明确组织登录稳定后，再增加 verified domain 管理和邮箱域名发现。邮箱只用于发现候选组织，不能单独决定 Membership 或账号绑定。多个组织命中时让用户选择组织。

### 阶段 5：协议扩展

按真实客户需求分别接入 SAML、CAS 2.0/3.0、LDAP 和更多 OIDC Provider。每个协议单独实现 Adapter、配置模型、错误分类和真实测试，不把协议差异塞进统一身份核心。

### 阶段 6：SCIM 与目录同步

SCIM 2.0 负责用户和群组生命周期同步。同步结果先落到 Organization、Membership 和 Group，再由授权投影决定 Namespace 权限。同步暂停、离职和删除都要有回滚和审计策略。

### 阶段 7：Namespace Entitlement

把组织角色、群组和 Skill/Namespace 访问策略映射为明确的授权投影。登录、组织同步和资源授权各自维护状态机，出现同步延迟时保留可追踪的授权版本。

### 阶段 8：外部身份中心可选接入

Casdoor 可以作为一个可选的统一 OIDC 身份源，也可以继续使用企业自己的 IdP。SkillHub 对所有外部身份源使用同一套 Login Connection、Adapter、External Identity 和企业会话边界。
