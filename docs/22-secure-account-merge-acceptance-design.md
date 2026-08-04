# SkillHub 安全账号合并验收设计

> 状态：Proposed
>
> 日期：2026-07-30
>
> 上位设计：[#628 统一身份联邦架构](https://github.com/iflytek/skillhub/issues/628)
>
> P0 隔离：[#634](https://github.com/iflytek/skillhub/issues/634)
>
> 目标实现：统一身份计划 PR 6，必须在统一身份核心与 Provider Adapter 契约稳定后实施

## 1. 目的

本文定义未来安全 Account Merge 的实现边界和可执行验收条件。它不是 P0 隔离 PR 的实现
清单，也不授权重新开启旧流程。

账号合并的含义是：两个已存在、都有独立登录方式和业务数据的 Platform Account，在分别
证明控制权后，把允许迁移的数据收敛到主账号，并把次账号永久标记为 `MERGED`。

它不同于：

- **首次登录建号**：外部身份还没有 Platform Account。
- **Identity Link**：给同一个 Platform Account 增加一种登录方式。
- **Profile Sync**：同步 display name、email、avatar 等资料字段。
- **管理员改状态**：启用或禁用账号，不证明另一个账号的控制权。
- **按 email 去重**：email 不是账号控制权证明，不能触发 Link 或 Merge。

## 2. P0 隔离契约

安全 Account Merge 上线前必须维持以下行为：

1. 旧 `/api/v1/account/merge/initiate`、`verify`、`confirm` 路径保留，避免部署客户端因
   404 误判，但对有效的已认证请求统一返回稳定的 `503 Service Unavailable`。
2. 旧路径不解析次账号标识、不创建或更新 merge request、不返回 verification token，
   不迁移任何账号数据。
3. 账号设置页面只显示不可用说明，不展示 username、`provider:subject`、request id、
   verification token 或确认控件。
4. 现有 `account_merge_request` 表和记录保留；P0 不删除、不完成、不转换旧请求。
5. 运维人员不得通过 SQL 把旧请求改为 `VERIFIED`/`COMPLETED`，也不得手工修改
   `identity_binding`、`api_token`、平台角色、namespace membership 或本地凭据来模拟合并。
6. 回滚到旧镜像会恢复不安全实现。若发生必须回滚的故障，应先在 Ingress、网关或反向代理
   阻断 `/api/v1/account/merge/*`。

## 3. 威胁模型

### 3.1 需要防御的攻击者

- 只控制主账号 Web Session，但知道次账号 username。
- 只控制主账号 Web Session，但从页面、日志或其他系统知道
  `provider_code:subject`。
- 窃取了一个旧 merge request id、state、浏览器历史或回调 URL。
- 可以重放已完成或已过期的 proof。
- 同时发起多个请求，尝试并发确认同一个合并。
- 控制一个 OAuth/OIDC Provider 账号，但不控制碰撞 email 对应的平台账号。
- 可以诱导已登录用户打开跨站页面，但不能读取同源响应。
- 拥有普通管理员权限，试图把合并当作后台数据修复操作。

### 3.2 安全不变量

实现和测试必须证明：

1. 主账号现有 Session 只表示“当前操作人已登录”，不能代替 fresh reauthentication。
2. 主账号证明和次账号证明来自两个独立认证动作。
3. 次账号只能由成功认证结果解析，主账号请求不能通过 email、username、
   `provider:subject` 或 userId 指定合并目标。
4. proof 只保存在服务端，浏览器、API 响应、URL、日志和审计 detail 不出现可直接消费的
   raw proof。
5. proof 同时绑定 merge request、对应账号、主账号 Session nonce、认证方法、签发时间和
   短 TTL。
6. request、state、proof 和 confirmation 都只能消费一次。
7. 两个账号必须不同、均为 `ACTIVE`、均非 system account、均未 `MERGED`。
8. 任一冲突、状态变化、过期、重放、Session 不匹配或并发竞争都必须 fail closed。
9. 合并不会通过角色并集静默提升平台权限。
10. 次账号 API Token 默认撤销，不迁移成主账号 Token。
11. 数据库提交后，次账号旧 Web Session 即使尚未从 Redis 删除，也不能继续访问业务接口。
12. 事务失败时不能留下部分 Binding、角色、membership、凭据、Token 或业务归属迁移。

## 4. 核心对象

### 4.1 Merge Intent

服务端创建的一次性合并意图。建议使用不可预测 UUID，至少包含：

```text
id
primary_user_id
secondary_user_id          # 次账号认证成功前为 NULL
status
primary_session_nonce_hash
primary_proof_at
secondary_proof_at
expires_at
preview_version
preview_digest
confirmed_at
completed_at
row_version
created_at
updated_at
```

约束：

- 不保存 raw Session ID、密码、OAuth token、authorization code、CAS Ticket、SAML
  assertion 或 raw proof。
- `primary_session_nonce_hash` 对 Session 中的高熵随机 nonce 做 SHA-256；不直接散列低熵
  用户输入。
- `secondary_user_id` 只能由认证成功后的统一身份核心写入。
- `row_version` 或等价 CAS 条件用于防止并发消费。
- `preview_digest` 只固定迁移计划，不包含可用于认证的秘密。

### 4.2 Fresh Reauthentication

fresh reauthentication 不是“Session 仍有效”。它必须重新执行当前账号的一种可用登录方式：

- 本地账号：重新校验密码、锁定和限流策略。
- OAuth/OIDC：强制新的上游认证交互；不能直接复用当前 Platform Session。
- LDAP、CAS、DingTalk 等：由对应 Adapter 重新验证协议结果，再进入统一身份核心。

主账号 proof 与次账号 proof 默认都在 10 分钟内有效；最终实现可以选择更短 TTL，但不得
超过普通 Web Session 生命周期，也不能靠续期 Session 自动延长。

### 4.3 Session Binding

创建 intent 时，在当前主账号 Session 生成一个合并专用高熵 nonce：

```text
HttpSession: raw merge_session_nonce
Database:    SHA-256(raw merge_session_nonce)
```

所有查询、次账号认证启动、预览、确认和取消都必须同时校验：

- 当前 `principal.userId == primary_user_id`
- 当前 Session nonce hash 匹配
- intent 未过期且状态允许该操作

换浏览器、换 Session、Session rotation 后未安全迁移 nonce，均不能继续旧 intent。

## 5. 必须采用的流程

```text
主账号已登录
  → 主账号 fresh reauthentication
  → 创建 Merge Intent，并绑定主账号 Session nonce
  → 启动独立的次账号认证
  → 统一身份核心由认证事实解析 secondary_user_id
  → 服务端把次账号 proof 写入 Intent，不向浏览器返回 raw proof
  → 生成冲突预览和 preview digest
  → 主账号确认同一 preview version
  → 单事务重新校验并迁移 PostgreSQL 数据
  → secondary account = MERGED，撤销 secondary API Token
  → 事务提交
  → 每请求账号状态守卫立即拒绝次账号
  → 可靠任务删除次账号 Redis Session
  → 审计完成
```

### 5.1 发起

- 只接受主账号 Session 和完成的主账号 fresh reauthentication challenge。
- 请求体不接受 `secondaryUserId`、email、username 或 `provider:subject`。
- 返回 intent id、可用的次账号认证方式、到期时间；不返回 proof 或推测出的次账号信息。
- 同一主账号可以只有一个有效 intent，或使用明确的数量限制；重复发起不能绕过限流。

### 5.2 次账号认证

- 使用独立认证事务，不把次账号 Principal 写入主账号 HttpSession。
- Browser Provider 的 state 必须绑定 intent 和主账号 Session nonce。
- 本地密码输入只进入专用 reauthentication 端点，并沿用密码锁定、限流和统一错误。
- OAuth/OIDC 等回调完成后，只在服务端记录“该 intent 已证明控制 secondary_user_id”。
- 次账号认证结果与主账号相同、账号不合格或已绑定到另一 intent 时返回通用错误，避免账号
  枚举。

### 5.3 预览

预览必须从数据库实时读取，至少列出：

- 将迁移的 Identity Binding 数量和 Provider 名称。
- 本地凭据保留/迁移/丢弃策略。
- 平台角色变化和所有阻塞的高权限角色。
- Namespace membership 变化及角色。
- 将撤销的次账号 API Token 名称、prefix 和数量，不返回 hash 或 raw token。
- Skill ownership 和其他业务资源归属变化。
- 阻塞冲突以及用户或管理员应先执行的解决动作。

预览生成 `preview_version` 和服务端 `preview_digest`。确认时必须重新计算；任何相关数据或
账号状态变化都使旧预览失效并返回 `409 Conflict`。

### 5.4 确认

- 只接受创建 intent 的主账号 Session。
- 主、次账号 proof 均未过期。
- intent 状态为可确认，preview version 匹配，且没有阻塞冲突。
- 使用行锁或等价并发控制按稳定顺序锁定两个账号和 intent。
- 同一 intent 并发确认时只能有一个提交成功；其他请求返回已消费的稳定错误。

## 6. 状态机

建议状态：

```text
PENDING_SECONDARY_PROOF
  → READY_FOR_PREVIEW
  → READY_TO_CONFIRM
  → COMPLETED

任一未完成状态
  → CANCELLED
  → EXPIRED
  → FAILED_CONFLICT
```

规则：

- 不允许从 `COMPLETED`、`CANCELLED`、`EXPIRED` 返回可执行状态。
- `FAILED_CONFLICT` 不能直接确认；冲突解决后创建新 preview，必要时创建新 intent。
- 过期由请求时检查和后台清理共同执行，不能只依赖定时任务。
- 所有状态迁移使用条件更新或乐观锁，不能“先查后改”而没有数据库竞争保护。

## 7. 数据迁移与冲突规则

### 7.1 Identity Binding

- 只迁移 `ACTIVE` Binding。
- 主账号已经存在同一 Provider Instance 的 ACTIVE Binding 时阻塞，不自动覆盖或选择。
- typed Subject/Alias 的全局唯一约束必须继续成立。
- 已撤销 Binding 保留历史归属，不自动复活。

### 7.2 本地凭据

- 主账号无本地凭据、次账号有本地凭据：可以迁移到主账号。
- 两个账号都有本地凭据：保留主账号凭据并使次账号凭据失效；该结果必须在预览中明确展示。
- username 唯一约束、锁定状态和密码策略不能因迁移被绕过。
- 不复制 password hash，不让同一凭据同时属于两个账号。

### 7.3 平台角色

- 默认角色无需复制。
- 次账号拥有任何非默认平台角色时，第一版安全实现应阻塞合并，由平台管理员先显式调整。
- 禁止使用“角色取并集”作为默认行为，避免普通主账号通过合并获得管理权限。
- 管理员调整和最终合并分别写审计。

### 7.4 Namespace Membership

- 只有次账号存在 membership：迁移同一角色。
- 两个账号都有 membership：保留权限更高者，并在预览中展示。
- 如果迁移会让主账号新获得 `OWNER`，第一版应阻塞；先通过 Namespace 治理流程显式转移
  ownership，再重新发起合并。
- 必须继续满足 `(namespace_id, user_id)` 唯一约束和 Namespace 最后所有者规则。

### 7.5 API Token

- 次账号所有未撤销 API Token 在数据库事务内统一设置 `revoked_at`。
- 不把次账号 Token 的 `user_id` 或 `subject_id` 改成主账号。
- 主账号 Token 不变。
- 预览只显示非秘密元数据；完成响应不返回任何新 Token。

### 7.6 业务数据

PR 6 开始前必须建立所有 userId 引用的迁移清单，并分类：

1. **当前归属**：如 Skill owner，需要迁移。
2. **当前授权**：如 membership、role、Token，按本设计处理。
3. **历史事实**：如 audit actor、过去的 reviewer/creator，应保留原 userId，不重写历史。
4. **通知和临时数据**：明确迁移、失效或删除策略。

任何未分类的 userId 外键、字符串引用或 JSON 引用都阻塞发布。不能只迁移旧
`AccountMergeService` 已知的几张表就宣称完成。

## 8. 事务、Session 与跨存储一致性

### 8.1 PostgreSQL 事务

以下操作必须在同一事务：

- 锁定和重新校验 intent、主账号、次账号。
- 重算 preview digest。
- 迁移允许迁移的 Binding、credential、membership 和业务归属。
- 撤销次账号 API Token。
- 把次账号设为 `MERGED` 并写 `merged_to_user_id`。
- 把 intent 标记为 `COMPLETED`。
- 写数据库审计或可靠 outbox 事件。

任一 repository 失败必须整体回滚。

### 8.2 Redis Session

PostgreSQL 与 Redis 不能依赖普通本地事务实现原子提交，因此需要两层保证：

1. 所有基于 Web Session 的请求在授权前检查账号仍可登录；数据库已是 `MERGED` 时立即
   拒绝，即使 Redis 中还存在旧 Principal 快照。
2. PostgreSQL 提交后，通过可重试的 session revocation 任务按次账号删除所有 indexed
   Spring Session。任务失败必须有指标、告警和重试，不能吞掉异常。

只有“删 Redis Session”而没有每请求状态守卫，会产生提交到删除之间的继续访问窗口；
只有状态守卫而不删除 Session，会留下长期无效 Session。两者都必须实现。

### 8.3 API Token

Token 在同一个 PostgreSQL 事务撤销。Token Authentication 还必须检查账号状态，因此即使
个别旧 Token 未被清理，`MERGED` 账号也不能认证成功。

## 9. API 与错误语义

未来 API 应使用新资源式路径，不复活旧 token 驱动接口。建议：

```text
POST   /api/v1/account/merge/intents
POST   /api/v1/account/merge/intents/{id}/secondary-auth/start
GET    /api/v1/account/merge/intents/{id}
POST   /api/v1/account/merge/intents/{id}/preview
POST   /api/v1/account/merge/intents/{id}/confirm
DELETE /api/v1/account/merge/intents/{id}
```

协议回调由各 Adapter 的既有 callback transport 处理，最终只调用统一的 server-side proof
完成接口，不把 proof 暴露为公共请求参数。

至少定义以下稳定 reason code：

| reason code | HTTP | 含义 |
|---|---:|---|
| `ACCOUNT_MERGE_UNAVAILABLE` | 503 | 功能尚未安全启用 |
| `MERGE_INTENT_NOT_FOUND` | 404 | 不存在或当前 Session 不可见 |
| `MERGE_REAUTH_REQUIRED` | 401 | 需要重新认证 |
| `MERGE_SESSION_MISMATCH` | 403 | Intent 不属于当前 Session |
| `MERGE_PROOF_EXPIRED` | 410 | 任一 proof 或 intent 已过期 |
| `MERGE_CONFLICT` | 409 | 数据或权限存在阻塞冲突 |
| `MERGE_PREVIEW_STALE` | 409 | 确认的预览已失效 |
| `MERGE_ALREADY_CONSUMED` | 409 | 已完成、取消或并发消费 |
| `MERGE_ACCOUNT_NOT_ELIGIBLE` | 409 | 账号状态不允许合并 |

面向未证明身份的响应不得泄露目标 userId、完整 email、角色、membership、Provider subject
或账号是否存在。

## 10. 审计、日志与指标

审计事件至少包括：

```text
ACCOUNT_MERGE_INTENT_CREATED
ACCOUNT_MERGE_PRIMARY_REAUTHENTICATED
ACCOUNT_MERGE_SECONDARY_REAUTHENTICATED
ACCOUNT_MERGE_PREVIEWED
ACCOUNT_MERGE_CONFIRMED
ACCOUNT_MERGE_COMPLETED
ACCOUNT_MERGE_CANCELLED
ACCOUNT_MERGE_EXPIRED
ACCOUNT_MERGE_REJECTED
ACCOUNT_MERGE_SESSION_REVOCATION_RETRIED
```

审计 detail 可以记录 request id、主/次账号内部 ID、Provider code、迁移数量、冲突 reason
code 和结果，但不能记录密码、raw proof、Session ID/nonce、OAuth token、authorization
code、Ticket、SAML assertion、API Token hash 或完整上游响应。

指标至少覆盖 intent 创建、proof 成功/失败、冲突、过期、完成、事务回滚和 Session 撤销
重试；Provider code 可以作为受控低基数标签，userId、request id 和 intent id 不能作为
指标标签。

## 11. 升级、启用与回滚

### 11.1 旧请求

- 旧 `PENDING`/`VERIFIED` 请求不携带可信的次账号 proof，不能转换为可确认的新 intent。
- 新版本可以把它们标记为 `LEGACY_BLOCKED`/过期，或保留只读；无论采用哪种方式，都必须
  保留审计证据，不能自动完成。
- 用户必须从头执行主、次账号 fresh reauthentication。

### 11.2 滚动升级

1. 数据库只做 additive migration，新旧 Pod 共存时旧路径仍由网关阻断。
2. 部署所有包含新实现的 Pod，但新 Account Merge 功能保持关闭。
3. 验证 schema、Session index、状态守卫、Provider reauthentication 和回滚脚本。
4. 确认没有旧 Pod 后再打开新资源式 API 和 UI。
5. 旧 initiate/verify/confirm 路径继续返回 503，不重定向到新确认接口。

### 11.3 回滚

- 关闭新 UI 和新 intent 创建。
- 已 `COMPLETED` 的合并不能靠镜像回滚自动拆分；拆分账号需要独立、人工审核的数据恢复
  流程。
- 未完成的新 intent 可统一失效。
- 数据库 additive 字段和表保留，不在应用回滚时删除。
- 不得回滚到会重新开放旧 token 流程的版本；如果镜像回滚不可避免，网关阻断规则必须先
  生效。

## 12. 自动化验收矩阵

### 12.1 控制权证明

- 只有主账号 Session，无法产生次账号 proof。
- email、username、display name、userId、`provider:subject` 均不能替代次账号认证。
- 次账号密码错误、锁定、禁用、待审批、已合并或 system account 时失败。
- OAuth/OIDC state 不能用于另一个 intent 或另一个主账号 Session。
- proof、Session nonce、callback 和 confirmation 不出现在日志或审计 detail。

### 12.2 过期与重放

- 主 proof 过期、次 proof 过期、intent 过期分别 fail closed。
- 已完成、取消、过期的 intent 无法恢复。
- 同一 callback 重放、同一 confirmation 重放都失败。
- 两个线程同时确认，只有一个成功，另一个得到 `MERGE_ALREADY_CONSUMED`。

### 12.3 冲突与权限

- 两账号相同、状态变化、system account、`MERGED` 均失败。
- 同 Provider Binding 冲突不覆盖。
- 非默认平台角色阻塞，不执行角色并集。
- Namespace OWNER 新增冲突阻塞。
- preview 后新增 Token、Binding、membership 或业务归属会让 preview stale。

### 12.4 原子性

在 Binding、credential、membership、业务归属、Token 撤销、账号状态、intent 状态和审计
每一步注入失败，验证所有 PostgreSQL 数据回到事务前状态。

### 12.5 Session 与 Token

- 合并提交后，次账号现有 Web Session 下一次请求立即被拒绝。
- Redis 删除失败时，请求仍被状态守卫拒绝，并产生重试任务和指标。
- 重试成功后次账号所有 indexed Session 消失。
- 次账号所有 API Token 被撤销，旧 Token 返回 401；主账号 Token 不受影响。

### 12.6 升级与回滚

- 从包含旧 `account_merge_request` 数据的版本升级，旧请求不能确认。
- 新旧 Pod 混跑期间旧路径始终被阻断。
- 关闭功能后不能新建 intent，已有未完成 intent 按策略失效。
- 应用版本回滚不删除新表，也不让旧 token 流程恢复可用。

## 13. 测试环境人工验收

在 `big-main` 测试镜像完成自动化检查后，人工至少验证：

1. 旧三个接口：未登录为 401；缺少有效 CSRF 时由现有安全链以 4xx 拒绝（当前实现为
   401）；已登录、CSRF 有效且请求合法时统一返回 503。
2. 账号设置页只显示中英文不可用说明，没有 identifier、request id、token 和确认按钮。
3. 普通本地登录、OAuth 登录、`/api/v1/auth/me`、Namespace 列表和 Skill 浏览不受影响。
4. 数据库已有 `account_merge_request` 数量和内容未被 P0 隔离部署修改。
5. 日志只包含稳定错误和 request id，不包含请求中的 secondary identifier 或 token。
6. 部署和回滚文档明确要求旧路径网关阻断；不得通过数据库手工演示“成功合并”。

PR 6 上线时，再执行本文件第 12 节的完整双账号、Redis Session、API Token、冲突、并发和
事务回滚验收。

## 14. 合并门禁

PR 6 只有同时满足以下条件才可以从 `big-main` 进入 `main`：

- 统一身份核心、Binding V2、Provider Registry/Adapter 契约已经稳定。
- 独立 Identity Link 已经证明 fresh reauthentication、一次性 request 和重放保护可用。
- 本文件所有自动化验收项有对应测试和可追溯结果。
- PostgreSQL + Redis 真实集成测试通过。
- 测试环境完成双账号人工验收，记录镜像 tag、commit、请求结果和数据前后快照。
- 安全 Review 没有 Blocker/Critical 发现。
- 运维、升级和回滚文档同步完成。

不满足任一门禁时，继续保持 Account Merge 不可用；不得以管理员手工操作作为替代方案。
