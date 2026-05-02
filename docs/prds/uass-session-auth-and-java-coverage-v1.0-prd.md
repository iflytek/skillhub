# UASS Session 认证接入与 Java 单元测试 100% 行覆盖率 - 产品需求文档 (PRD)

## 1. Introduction

当前团队计划在同一个 feature 中同时完成两项高关联工作：

1. 企业内部 UASS 认证接入
2. Java 单元测试行覆盖率提升到 100%

将两项工作合并到一个 PRD 的原因是：

- UASS 认证接入属于安全与登录主链路改造，风险高，必须同步补齐测试和覆盖率门禁。
- 认证适配层、会话缓存、回调异常、登出兜底等逻辑需要高质量测试支撑，单独拆开容易导致“功能先落地、测试后补齐”的风险。
- 团队计划在同一个 feature 分支中统一推进，因此需要一个共同的范围定义、验收标准和交付顺序。

本 PRD 定义统一交付目标：

- SkillHub 通过企业内部 UASS 完成登录
- 登录主模型继续采用 Session
- 生产高可用走 Redis 共享 Session / 共享缓存
- 单机测试模式允许无 Redis，回落到本地 Session / 本地缓存
- 与本次改动相关的 Java 生产代码行覆盖率最终达到 100%，并建立门禁

## 2. Goals

- 在不改写 SkillHub 主认证模型的前提下接入企业内部 UASS 登录。
- 登录成功后建立 SkillHub 本地 Session，并在多副本部署下保持可用。
- 将登录过程中的 `state` 与必要的回跳上下文写入统一存储抽象，支持 Redis 与本地缓存双实现。
- 将企业内部 jar 的依赖和实现细节隔离在适配层，避免扩散到业务层。
- 将本次 feature 涉及的 Java 生产代码 JaCoCo line coverage 提升到 100%。
- 建立覆盖率校验门禁，防止后续修改导致认证主链路和相关模块的测试覆盖回退。

## 3. Unified Scope

### 3.1 Functional Scope

- UASS 登录入口
- UASS 跳转与回调处理
- UASS 登录状态检查
- UASS 登出
- UASS 用户信息映射到 SkillHub 本地用户
- SkillHub 本地 Session 建立
- UASS 回跳状态管理
- 生产 Redis 模式与单机本地状态存储模式切换

### 3.2 Quality Scope

- 补齐 UASS 接入相关 Java 代码的单元测试
- 补齐现有认证、Session、缓存和异常处理分支的测试
- 将本次 feature 相关 Java 生产代码的 line coverage 拉到 100%
- 在构建链路中增加覆盖率门禁

## 4. Default Assumptions

1. UASS 不是标准开源 OAuth2/OIDC 方案，而是企业内部私有认证能力。
2. UASS jar 已经能够提供登录 URL 获取、登录校验、状态检查、用户信息查询和登出能力。
3. SkillHub 仍以浏览器 Web 控制台登录为主，不在本轮改造成前端 Bearer Token 主登录模式。
4. SkillHub 后端部署为多副本时，Session 与 UASS 登录过程中的 `state` 都必须放入共享存储。
5. 单机测试场景允许不接 Redis，但该模式不提供跨节点高可用保证。
6. Java 单测 100% line coverage 的目标仅针对生产代码，不包含前端 TypeScript 覆盖率。
7. UASS 回调会返回可用于企业用户唯一识别的稳定字符串标识；当前实现落地为 `ussId`。

## 5. User Stories

### US-001: 新增 UASS 适配层
**Description:** 作为开发者，我需要一个独立的 UASS 适配层，以便 SkillHub 只依赖统一接口而不直接耦合企业内部 jar 的实现细节。

**Acceptance Criteria:**
- [ ] 在认证模块中新增 `UassClientFacade` 或等价适配接口。
- [ ] 适配层统一封装登录 URL 获取、登录校验、状态检查、用户信息查询和登出能力。
- [ ] SkillHub 业务服务层不直接依赖企业 jar 的原始类型。
- [ ] jar 依赖仅落在认证模块或专用集成模块中。
- [ ] 适配层新增代码具备单元测试。

### US-002: 建立 UASS 登录回调并创建 SkillHub Session
**Description:** 作为企业用户，我希望完成 UASS 登录后自动进入 SkillHub 已登录状态，以便继续访问平台页面和业务能力。

**Acceptance Criteria:**
- [ ] 新增 UASS 登录入口和回调入口。
- [ ] UASS 回调成功后，SkillHub 服务端先使用返回的 `ussId` 查询本地用户。
- [ ] 当本地不存在该 `ussId` 对应用户时，系统根据 UASS 返回的用户信息创建新用户。
- [ ] 新建用户完成后继续执行同一次登录流程，而不是要求用户再次登录。
- [ ] 登录成功后通过现有 Session 机制建立 SkillHub 本地登录态。
- [ ] 登录成功后重定向回预期页面，而不是停留在中间跳转页。
- [ ] 登录闭环相关 Java 生产代码 line coverage 达到 100%。

### US-003: 管理 UASS 回跳状态
**Description:** 作为平台维护者，我希望 UASS 登录过程中的 `state` 和必要回跳上下文写入统一存储抽象，以便多副本场景和单机测试场景都能稳定完成登录闭环。

**Acceptance Criteria:**
- [ ] 发起登录前将 `state` 和 `returnTo` 等必要回跳上下文写入统一存储抽象。
- [ ] 生产模式下状态存储实现使用 Redis。
- [ ] 单机测试模式下状态存储实现使用本地内存缓存。
- [ ] `state` 存储具备 TTL，并在 callback 成功或失败后被消费或清理。
- [ ] 状态存储相关 Java 生产代码 line coverage 达到 100%。

### US-004: 提供登录状态检查能力
**Description:** 作为前端和平台调用方，我希望能检查当前登录状态，以便确认用户是否已经在 SkillHub 建立有效 Session。

**Acceptance Criteria:**
- [ ] 新增 UASS 登录状态检查接口。
- [ ] 状态检查至少能够区分：未登录、已登录。
- [ ] 状态检查默认以 SkillHub 本地 Session 为准。
- [ ] 如果 UASS jar 支持远端状态检查，该能力应作为可选增强，而不是主登录态事实来源。
- [ ] 状态检查相关 Java 生产代码 line coverage 达到 100%。

### US-005: 提供统一登出能力
**Description:** 作为企业用户，我希望从 SkillHub 登出时同时退出 UASS 登录态或至少清理本地关联状态，以便后续访问不会误判为已登录。

**Acceptance Criteria:**
- [ ] 新增 UASS 登出接口。
- [ ] 登出时先调用 UASS 登出能力，再清理 SkillHub Session 和登录中间态存储。
- [ ] 登出失败时也必须保证 SkillHub 本地 Session 被清理。
- [ ] 登出完成后访问受保护页面会重新回到登录入口。
- [ ] 登出相关 Java 生产代码 line coverage 达到 100%。

### US-006: 兼容无 Redis 的单机测试模式
**Description:** 作为开发者和测试人员，我希望在没有 Redis 的单机环境中也能完成 UASS 登录验证，以便降低本地调试和单机联调成本。

**Acceptance Criteria:**
- [ ] 当 Redis 不可用或显式关闭时，系统自动切换到本地缓存实现。
- [ ] 当 Redis 不可用或显式关闭时，系统仍可建立本地 Session 并完成后续状态检查。
- [ ] 单机测试模式下不要求跨节点共享登录态。
- [ ] 本地缓存实现具备 TTL 控制和显式清理能力。
- [ ] 本地模式相关 Java 生产代码 line coverage 达到 100%。

### US-007: 保持高可用部署兼容
**Description:** 作为运维人员，我希望 UASS 接入后的认证链路仍然适用于多副本高可用部署，以便后端实例切换时不丢登录态。

**Acceptance Criteria:**
- [ ] SkillHub Session 继续使用 Redis 共享会话。
- [ ] UASS 登录过程中的 `state` 在生产模式下使用 Redis 共享存储，不依赖单机 JVM 内存。
- [ ] 不要求负载均衡启用粘性会话。
- [ ] 多副本部署下，用户跨节点访问时仍能识别为已登录。
- [ ] HA 相关装配代码 line coverage 达到 100%。

### US-008: 复用本地用户与权限模型
**Description:** 作为平台管理员，我希望 UASS 登录用户仍映射到 SkillHub 本地用户体系，以便继续使用现有权限、空间成员关系、审计和治理能力。

**Acceptance Criteria:**
- [ ] UASS 用户能够映射到本地 `user_account` 与 `identity_binding` 体系。
- [ ] 本地用户主匹配键使用统一登录平台返回的 `ussId`。
- [ ] 首次登录用户可按既有准入策略创建或绑定本地用户。
- [ ] 本地权限、命名空间角色、审计日志能力不需要单独为 UASS 重做一套模型。
- [ ] 用户唯一标识使用 `ussId` 或以 `ussId` 为核心的企业稳定唯一 ID，而不是临时登录名。
- [ ] 用户映射相关 Java 生产代码 line coverage 达到 100%。

### US-009: 建立覆盖率基线与门禁
**Description:** 作为维护者，我希望将本次 feature 涉及的 Java 生产代码覆盖率提升到 100%，并建立门禁，以便认证改造不会以牺牲可验证性为代价落地。

**Acceptance Criteria:**
- [ ] 为本次 feature 涉及模块生成 JaCoCo line coverage 报告。
- [ ] 与 UASS 接入直接相关的 Java 生产代码 `line missed = 0`。
- [ ] Maven 构建中增加 line coverage 100% 校验规则或等价门禁。
- [ ] 未满足门禁时构建明确失败。

## 6. Functional Requirements

- FR-1: 系统必须新增 UASS 适配层，用于统一封装企业内部 jar 的能力调用。
- FR-2: 系统必须提供 UASS 登录入口、回调入口、登录状态检查入口和登出入口。
- FR-3: 系统必须在 UASS 登录成功后建立 SkillHub 本地 Session。
- FR-4: 系统必须将 UASS 登录过程中的 `state` 与必要回跳上下文写入统一存储抽象，而不是直接写死在 Redis 实现中。
- FR-5: 系统必须以 SkillHub 本地 Session 作为浏览器后续请求的唯一登录态事实来源。
- FR-6: 系统必须通过企业稳定唯一用户标识完成本地用户绑定或创建。
- FR-6.1: 系统必须优先使用统一登录平台返回的 `ussId` 查询本地用户。
- FR-6.2: 当本地不存在该 `ussId` 对应用户时，系统必须使用回传的用户信息创建新用户。
- FR-6.3: 新用户创建成功后，系统必须在同一次登录流程中继续完成本地 Session 建立。
- FR-7: 系统必须支持在多副本部署下跨节点恢复用户登录态。
- FR-8: 系统必须在用户登出时清理本地 Session 与登录中间态存储。
- FR-9: 系统必须提供登录状态检查能力，并默认以本地 Session 为准。
- FR-10: 系统必须将新增 UASS 接口纳入现有 Spring Security 路由放行和鉴权目录。
- FR-11: 系统必须支持“Redis 共享缓存实现”和“本地内存缓存实现”两种缓存模式。
- FR-12: 在未启用 Redis 的单机测试模式下，系统必须仍能跑通 UASS 登录、状态检查和登出闭环。
- FR-13: 本地缓存模式必须明确标记为非高可用模式，不承诺跨节点共享。
- FR-14: 本次 feature 涉及的 Java 生产代码 line coverage 必须达到 100%。
- FR-15: 构建链路必须对本次 feature 涉及的 Java 覆盖率建立自动门禁。

## 7. Recommended API Surface

建议新增以下平台接口：

- `GET /api/v1/auth/uass`
- `GET /api/v1/auth/uass/login-url`
- `GET /api/v1/auth/uass/redirect`
- `GET /api/v1/auth/uass/callback`
- `GET /api/v1/auth/uass/status`
- `POST /api/v1/auth/uass/logout`
- `POST /api/v1/auth/uass/mock/login`（仅本地模拟第三方页面时启用）

建议职责：

1. `uass`
   - 当前主入口，负责生成 `state`、保存回跳上下文并直接 302 到 UASS 登录页。
2. `login-url`
   - 兼容保留接口，返回前端可跳转的 UASS 登录地址。
3. `redirect`
   - 服务端直接 302 跳转到 UASS 登录页。
4. `callback`
   - 登录校验、查询用户信息、建立本地 Session、写入缓存、跳回目标页面。
5. `status`
   - 检查当前 SkillHub Session 和 UASS 缓存是否仍有效。
6. `logout`
   - 调 UASS 登出并清理 SkillHub 本地状态。
7. `mock/login`
   - 本地模拟第三方登录页时接收 `state/callbackUrl/ussId` 等输入并生成 callback 跳转地址。

### 7.1 当前实现增补（2026-05-02）

- 本地开发模式下支持 `skillhub.auth.uass.base-url=mock://self`，并可通过 `skillhub.auth.uass.mock-login-base-url=http://localhost:3001` 将第三方登录页独立跑在单独前端实例。
- 当前本地模拟第三方页面路由为 `/mock-uass`，由独立前端实例承载，提交后跳回 `/api/v1/auth/uass/callback`。
- UASS 用户落库时会写入 `user_account.uss_id`，并以 `ussId` 作为本地去重与复用主键；同一 `ussId` 重复登录不会新建用户。
- `/api/v1/auth/me` 与 `/api/v1/user/profile` 当前都会返回 `ussId`，以便前端和管理端可见该企业标识。
- 新增 `skillhub.auth.uass.admin-users[]` 配置，允许按 `ussId` 为“首次登录的新用户”预置管理员角色；后续角色调整以数据库中的 `user_role_binding` 为准。
- Spring Security 路由放行策略当前通过 YAML overlay 配置 UASS 路由，而不是继续把所有部署差异硬编码在 `RouteSecurityPolicyRegistry` 中。

## 8. State Store Design

### 8.1 缓存抽象要求

建议新增统一抽象，例如：

- `UassLoginStateStore`

推荐提供两个实现：

- `RedisUassLoginStateStore`
- `LocalUassLoginStateStore`

装配原则：

- 生产高可用：优先 Redis 实现
- 单机测试：允许显式切换或在无 Redis 时降级到本地实现

### 8.2 推荐 Redis Key

- `uass:state:{state}`

### 8.3 推荐存储内容

`uass:state:{state}` 至少包含：

- `returnTo`
- `createdAt`
- `provider`
- `requestFingerprint`（可选）

### 8.4 设计原则

- `state` 存储只服务于登录跳转和 callback 闭环，不承担长期登录态职责。
- `state` TTL 应尽量短，一般建议 5 到 10 分钟。
- 本地缓存实现也必须支持 TTL 和按 Session 标识删除。
- 本地缓存模式只用于单机测试或本地联调，不作为高可用生产形态。

## 9. Coverage Baseline And Verification

### 9.1 覆盖率范围

本 PRD 将覆盖率目标限定在：

- 本次 UASS 接入 feature 中新增或重度修改的 Java 生产代码
- 与认证主链路、Session 建立、缓存装配和登出兜底直接相关的既有 Java 生产代码

### 9.2 验证口径

- 使用 JaCoCo line coverage
- 对 feature 范围内的 Java 生产类，要求 `line missed = 0`
- 相关辅助清单可继续引用：
  - [java-unit-line-coverage-inventory.md](./java-unit-line-coverage-inventory.md)

### 9.3 门禁目标

- 本次 feature 合并前，本次范围内 Java 生产代码 line coverage = 100%
- 构建脚本或 Maven 校验规则在覆盖率不达标时必须失败

## 10. Design Considerations

### 10.1 认证模型边界

- 浏览器主登录态继续使用 Session。
- UASS 回跳成功后，SkillHub 本地 Session 是后续请求的唯一登录态依据。

### 10.2 运行模式边界

- 生产高可用模式：Redis Session + Redis UASS 缓存
- 单机测试模式：本地 Session + 本地 UASS 缓存
- 两种模式必须通过装配或配置切换，而不是在业务逻辑里散落 if/else 判断

### 10.3 模块边界

- 企业 jar 依赖建议落在 `skillhub-auth` 模块或独立 integration 模块。
- `skillhub-app` 只依赖封装后的 `UassAuthService`。

### 10.4 用户匹配边界

- 用户查找的第一关键字必须是 `ussId`。
- 不允许只按显示名、邮箱昵称或临时登录名匹配用户。
- 如需与现有 `IdentityBindingService` 复用，应确保 `subject` 等价映射到 `ussId`。

### 10.5 前端改动范围

- 登录页新增“企业登录”入口即可。
- 不要求前端接入新的 OAuth SDK。
- 不要求前端本地存储任何 UASS token。

### 10.5 质量边界

- 本次 feature 不仅要求功能可用，还要求认证主链路和相关辅助类具备完整可验证性。
- 不允许以“先实现、后补测试”的方式交付。

## 11. Technical Considerations

- 现有 SkillHub Session 已支持 Redis 共享存储，可继续复用。
- 单机测试模式下允许不依赖 Redis，会话回落为本地 Session。
- UASS 用户应继续映射为平台本地 `PlatformPrincipal` 和本地用户实体。
- UASS 用户查找应先命中 `ussId` 绑定，再决定是否创建用户。
- 回调接口必须处理重放、过期 state、回调失败和用户信息查询失败。
- 登出流程应具备“远端失败但本地仍强制清理”的兜底能力。
- 需要在路由策略中为新增 UASS 登录入口配置 `permitAll` 或 `authenticated` 规则。
- 本地缓存实现建议优先使用进程内 TTL 缓存，不要求引入额外中间件。
- 当系统检测到本地缓存模式时，应在日志或监控中明确标识当前不是 HA 运行模式。
- 认证、缓存、装配和兜底分支都必须有覆盖率证明，而不是仅依赖手工联调。

## 12. Non-Goals

- 本期不将 SkillHub Web 主登录模型改造成前端 Bearer Token 模式。
- 本期不要求改造现有 CLI/API Token 体系。
- 本期不要求设计或实现 UASS token 生命周期管理。
- 本期不要求支持多套企业 SSO 并存。
- 本期不要求替换现有本地登录、OAuth 登录或 direct login 能力。
- 本期不要求本地缓存模式具备跨节点共享能力。
- 本期不要求把全仓所有 Java 生产代码一次性提升到 100%，但本 feature 相关范围必须达到。

## 13. Success Metrics

- UASS 用户可通过企业登录成功进入 SkillHub，并在页面刷新后保持登录态。
- 多副本部署下，用户跨节点访问不会丢失登录态。
- 登录成功前后，`state` 的创建、消费和清理符合预期。
- 登出后，本地 Session 与登录中间态都被清理。
- 前端无需持有任何 UASS token，仍可正常使用需要登录的 SkillHub 页面能力。
- 在无 Redis 的单机测试模式下，仍可完成登录、状态检查和登出闭环。
- 本次 feature 范围内 Java 生产代码 JaCoCo line coverage = 100%。

## 14. Open Questions

1. UASS 是否提供稳定唯一用户标识字段，可直接作为本地 `subject`？
   当前默认答案：使用 `ussId`。
2. UASS jar 所谓“登录状态检查”是只用于 callback 校验，还是支持登录后任意时刻远端校验？
3. UASS 登录状态检查接口的耗时和限流约束是什么？
4. UASS 登出是否必须前端浏览器参与跳转，还是服务端可直接完成？
5. 企业内部 jar 是否线程安全，是否允许在多实例服务中单例复用？
6. 是否需要将 UASS 用户扩展字段（部门、组织、工号）同步到 SkillHub 用户画像？
7. 覆盖率门禁是只针对变更范围生效，还是同步上升到相关模块级别生效？

## 15. Suggested Delivery Phases

### Phase 1: 最小登录闭环
- UASS 适配层
- 登录入口 / callback
- 本地 Session 建立
- 缓存抽象层
- 本地缓存实现
- Redis 缓存实现

### Phase 2: 状态检查与登出
- `status` 接口
- `logout` 接口
- 失效处理

### Phase 3: 测试补齐与覆盖率门禁
- 认证主链路测试
- 本地缓存与 Redis 双模式测试
- 异常分支测试
- Maven / CI 覆盖率门禁
