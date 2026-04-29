# UASS Session 认证接入 - 产品需求文档 (PRD)

> 本文档已被统一合并到：
> [uass-session-auth-and-java-coverage-v1.0-prd.md](./uass-session-auth-and-java-coverage-v1.0-prd.md)
>
> 后续请以合并后的总 PRD 为准。

## 1. Introduction

当前 SkillHub Web 登录主模型是基于浏览器 Session 的认证方案，登录成功后由服务端建立本地登录态。

在标准生产部署中，SkillHub 使用 Redis 共享会话支撑多副本部署；在单机测试场景下，系统也存在不依赖 Redis 的本地运行模式。因此，UASS 接入方案必须同时兼容：

- **生产高可用模式**：Redis 共享 Session + Redis 共享 UASS 缓存
- **单机测试模式**：本地 Session + 本地内存 UASS 缓存

企业内部现有统一认证服务 UASS 以私有 jar 的形式提供接入能力，jar 内已经实现：

- 登录 URL 获取
- 登录状态检查
- 登录校验
- 跳转 UASS URL
- 用户登出
- 用户信息查询
- token / 相关字段的加解密

本 PRD 定义一轮最小但可生产化的 UASS 接入方案，使 SkillHub 能够通过 UASS 完成平台登录，并保持后续高可用部署能力。

本方案明确采用：

- **SkillHub 主登录态：Session**
- **SkillHub 会话共享：生产走 Redis，单机测试走本地 Session**
- **UASS token / 用户信息缓存：生产走 Redis，单机测试走本地缓存**

本方案不将浏览器主登录模型切换为前端 Bearer Token 模式。

## 2. Goals

- 在不改写 SkillHub 主认证模型的前提下接入企业内部 UASS 登录。
- 登录成功后建立 SkillHub 本地 Session，并在多副本部署下保持可用。
- 将 UASS token 和必要用户信息写入共享缓存，保证后续平台执行链路可识别用户已登录。
- 将 UASS 私有 jar 的依赖和实现细节隔离在适配层，避免扩散到业务层。
- 为后续高可用部署提供明确的会话、缓存、登出和失效处理边界。
- 在没有 Redis 的单机测试场景下，仍能以本地 Session 与本地缓存跑通完整登录闭环。

## 3. Default Assumptions

本 PRD 按以下默认假设编写：

1. UASS 不是标准开源 OAuth2/OIDC 方案，而是企业内部私有认证能力。
2. UASS jar 已经能够提供完整登录校验与用户信息查询能力。
3. SkillHub 仍以浏览器 Web 控制台登录为主，不需要在本轮将前端主登录改造成 Bearer Token 模式。
4. SkillHub 后端部署为多副本时，Session 与 UASS token 都必须放入共享存储。
5. 企业用户在 SkillHub 中仍需要映射为平台本地用户，以便沿用现有权限、命名空间和审计模型。
6. 单机测试场景允许不接 Redis，但该模式不提供跨节点高可用保证。

## 4. User Stories

### US-001: 新增 UASS 适配层
**Description:** 作为开发者，我需要一个独立的 UASS 适配层，以便 SkillHub 只依赖统一接口而不直接耦合企业内部 jar 的实现细节。

**Acceptance Criteria:**
- [ ] 在认证模块中新增 `UassClientFacade` 或等价适配接口。
- [ ] 适配层统一封装登录 URL 获取、登录校验、状态检查、用户信息查询和登出能力。
- [ ] SkillHub 业务服务层不直接依赖企业 jar 的原始类型。
- [ ] jar 依赖仅落在认证模块或专用集成模块中。

### US-002: 建立 UASS 登录回调并创建 SkillHub Session
**Description:** 作为企业用户，我希望完成 UASS 登录后自动进入 SkillHub 已登录状态，以便继续访问平台页面和业务能力。

**Acceptance Criteria:**
- [ ] 新增 UASS 登录入口和回调入口。
- [ ] UASS 回调成功后，SkillHub 服务端能够查询用户信息并映射到平台本地用户。
- [ ] 登录成功后通过现有 Session 机制建立 SkillHub 本地登录态。
- [ ] 登录成功后重定向回预期页面，而不是停留在中间跳转页。

### US-003: 将 UASS token 和用户信息写入共享缓存
**Description:** 作为平台维护者，我希望 UASS token 和必要用户信息写入共享缓存，以便多副本场景下后续请求都能识别当前用户已完成企业登录。

**Acceptance Criteria:**
- [ ] 登录成功后将 UASS access token、refresh token、过期时间和必要用户信息写入统一缓存抽象。
- [ ] 生产模式下缓存实现使用 Redis。
- [ ] 单机测试模式下缓存实现使用本地内存缓存。
- [ ] Redis key 设计与 SkillHub Session 绑定，能够通过 Session 标识定位 UASS 缓存。
- [ ] 缓存 TTL 与 UASS token 生命周期保持一致或更严格。
- [ ] 不将 UASS token 明文返回给浏览器前端页面。

### US-004: 提供登录状态检查能力
**Description:** 作为前端和平台调用方，我希望能检查当前 UASS 登录状态，以便在 Session 仍存在但 UASS 凭证失效时及时发现。

**Acceptance Criteria:**
- [ ] 新增 UASS 登录状态检查接口。
- [ ] 状态检查至少能够区分：未登录、SkillHub 已登录但 UASS 缓存失效、完全已登录。
- [ ] 当本地 Session 存在但 UASS token 已失效时，系统返回明确的未就绪状态。
- [ ] 状态检查接口不泄露敏感 token 原文。

### US-005: 提供统一登出能力
**Description:** 作为企业用户，我希望从 SkillHub 登出时同时退出 UASS 登录态或至少清理本地关联状态，以便后续访问不会误判为已登录。

**Acceptance Criteria:**
- [ ] 新增 UASS 登出接口。
- [ ] 登出时先调用 UASS 登出能力，再清理 SkillHub Session 和 Redis 中的 UASS 缓存。
- [ ] 登出失败时也必须保证 SkillHub 本地 Session 被清理。
- [ ] 登出完成后访问受保护页面会重新回到登录入口。

### US-006: 兼容无 Redis 的单机测试模式
**Description:** 作为开发者和测试人员，我希望在没有 Redis 的单机环境中也能完成 UASS 登录验证，以便降低本地调试和单机联调成本。

**Acceptance Criteria:**
- [ ] 当 Redis 不可用或显式关闭时，系统自动切换到本地缓存实现。
- [ ] 当 Redis 不可用或显式关闭时，系统仍可建立本地 Session 并完成后续状态检查。
- [ ] 单机测试模式下不要求跨节点共享登录态。
- [ ] 本地缓存实现具备 TTL 控制和显式清理能力。

### US-007: 保持高可用部署兼容
**Description:** 作为运维人员，我希望 UASS 接入后的认证链路仍然适用于多副本高可用部署，以便后端实例切换时不丢登录态。

**Acceptance Criteria:**
- [ ] SkillHub Session 继续使用 Redis 共享会话。
- [ ] UASS token 缓存使用 Redis 共享缓存，不依赖单机 JVM 内存。
- [ ] 不要求负载均衡启用粘性会话。
- [ ] 多副本部署下，用户跨节点访问时仍能识别为已登录。

### US-008: 复用本地用户与权限模型
**Description:** 作为平台管理员，我希望 UASS 登录用户仍映射到 SkillHub 本地用户体系，以便继续使用现有权限、空间成员关系、审计和治理能力。

**Acceptance Criteria:**
- [ ] UASS 用户能够映射到本地 `user_account` 与 `identity_binding` 体系。
- [ ] 首次登录用户可按既有准入策略创建或绑定本地用户。
- [ ] 本地权限、命名空间角色、审计日志能力不需要单独为 UASS 重做一套模型。
- [ ] 用户唯一标识使用企业稳定唯一 ID，而不是临时登录名。

## 5. Functional Requirements

- FR-1: 系统必须新增 UASS 适配层，用于统一封装企业内部 jar 的能力调用。
- FR-2: 系统必须提供 UASS 登录入口、回调入口、登录状态检查入口和登出入口。
- FR-3: 系统必须在 UASS 登录成功后建立 SkillHub 本地 Session。
- FR-4: 系统必须将 UASS token 和必要用户信息写入统一缓存抽象，而不是直接写死在 Redis 实现中。
- FR-5: 系统必须保证 UASS token 不作为前端浏览器主调用凭证直接暴露给页面。
- FR-6: 系统必须通过企业稳定唯一用户标识完成本地用户绑定或创建。
- FR-7: 系统必须支持在多副本部署下跨节点恢复用户登录态。
- FR-8: 系统必须在用户登出时清理本地 Session 与 Redis 中的 UASS 缓存。
- FR-9: 系统必须在本地 Session 存在但 UASS 缓存失效时返回明确状态，以便前端触发重新登录。
- FR-10: 系统必须将新增 UASS 接口纳入现有 Spring Security 路由放行和鉴权目录。
- FR-11: 系统必须支持“Redis 共享缓存实现”和“本地内存缓存实现”两种缓存模式。
- FR-12: 在未启用 Redis 的单机测试模式下，系统必须仍能跑通 UASS 登录、状态检查和登出闭环。
- FR-13: 本地缓存模式必须明确标记为非高可用模式，不承诺跨节点共享。

## 6. Recommended API Surface

建议新增以下平台接口：

- `GET /api/v1/auth/uass/login-url`
- `GET /api/v1/auth/uass/redirect`
- `GET /api/v1/auth/uass/callback`
- `GET /api/v1/auth/uass/status`
- `POST /api/v1/auth/uass/logout`

建议职责：

1. `login-url`
   - 返回前端可跳转的 UASS 登录地址。
2. `redirect`
   - 服务端直接 302 跳转到 UASS 登录页。
3. `callback`
   - 登录校验、查询用户信息、建立本地 Session、写入 Redis 缓存、跳回目标页面。
4. `status`
   - 检查当前 SkillHub Session 和 UASS 缓存是否仍有效。
5. `logout`
   - 调 UASS 登出并清理 SkillHub 本地状态。

## 7. Cache Design

### 7.0 缓存抽象要求

建议新增统一抽象，例如：

- `UassSessionStore`
- `UassLoginStateStore`

推荐提供两个实现：

- `RedisUassSessionStore`
- `LocalUassSessionStore`

装配原则：

- 生产高可用：优先 Redis 实现
- 单机测试：允许显式切换或在无 Redis 时降级到本地实现

### 7.1 推荐 Redis Key

- `uass:state:{state}`
- `uass:session:{httpSessionId}`

### 7.2 推荐缓存内容

`uass:session:{httpSessionId}` 至少包含：

- `accessToken`
- `refreshToken`
- `expiresAt`
- `userId`
- `username`
- `displayName`
- `email`
- `extra`

### 7.3 设计原则

- UASS token 缓存必须与 SkillHub Session 生命周期关联。
- UASS token 缓存 TTL 不得长于 token 实际有效期。
- 允许后续扩展 refresh / revalidate 逻辑，但本期不要求完整自动续期。
- 本地缓存实现也必须支持 TTL 和按 Session 标识删除。
- 本地缓存模式只用于单机测试或本地联调，不作为高可用生产形态。

## 8. Design Considerations

### 8.1 认证模型边界

- 浏览器主登录态继续使用 Session。
- UASS token 只是服务端后续调用 UASS 能力时的凭据，不是前端主凭据。

### 8.2 运行模式边界

- 生产高可用模式：Redis Session + Redis UASS 缓存
- 单机测试模式：本地 Session + 本地 UASS 缓存
- 两种模式必须通过装配或配置切换，而不是在业务逻辑里散落 if/else 判断
### 8.3 模块边界

- 企业 jar 依赖建议落在 `skillhub-auth` 模块或独立 integration 模块。
- `skillhub-app` 只依赖封装后的 `UassAuthService`。
### 8.4 前端改动范围

- 登录页新增“企业登录”入口即可。
- 不要求前端接入新的 OAuth SDK。
- 不要求前端本地存储 UASS token。

## 9. Technical Considerations

- 现有 SkillHub Session 已支持 Redis 共享存储，可继续复用。
- 单机测试模式下允许不依赖 Redis，会话回落为本地 Session。
- UASS 用户应继续映射为平台本地 `PlatformPrincipal` 和本地用户实体。
- 不建议将 UASS token 塞入 `PlatformPrincipal`，避免 Principal 过重且敏感信息扩散。
- 回调接口必须处理重放、过期 state、回调失败和用户信息查询失败。
- 登出流程应具备“远端失败但本地仍强制清理”的兜底能力。
- 需要在路由策略中为新增 UASS 登录入口配置 `permitAll` 或 `authenticated` 规则。
- 本地缓存实现建议优先使用进程内 TTL 缓存，不要求引入额外中间件。
- 当系统检测到本地缓存模式时，应在日志或监控中明确标识当前不是 HA 运行模式。

## 10. Non-Goals

- 本期不将 SkillHub Web 主登录模型改造成前端 Bearer Token 模式。
- 本期不要求改造现有 CLI/API Token 体系。
- 本期不要求实现 UASS token 自动刷新闭环。
- 本期不要求支持多套企业 SSO 并存。
- 本期不要求替换现有本地登录、OAuth 登录或 direct login 能力。
- 本期不要求本地缓存模式具备跨节点共享能力。

## 11. Success Metrics

- UASS 用户可通过企业登录成功进入 SkillHub，并在页面刷新后保持登录态。
- 多副本部署下，用户跨节点访问不会丢失登录态。
- 登录成功后，Redis 中能正确写入与 Session 关联的 UASS token 缓存。
- 登出后，本地 Session 与 UASS 缓存都被清理。
- 前端无需持有 UASS token，仍可正常使用需要登录的 SkillHub 页面能力。
- 在无 Redis 的单机测试模式下，仍可完成登录、状态检查和登出闭环。

## 12. Open Questions

1. UASS 是否提供稳定唯一用户标识字段，可直接作为本地 `subject`？
2. UASS token 是否存在 refresh token，以及推荐刷新时机是什么？
3. UASS 登录状态检查接口的耗时和限流约束是什么？
4. UASS 登出是否必须前端浏览器参与跳转，还是服务端可直接完成？
5. 企业内部 jar 是否线程安全，是否允许在多实例服务中单例复用？
6. 是否需要将 UASS 用户扩展字段（部门、组织、工号）同步到 SkillHub 用户画像？

## 13. Suggested Delivery Phases

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

### Phase 3: 稳定性与运维增强
- 指标与日志
- 缓存 TTL 校准
- 回调异常审计
- 可选的续期策略
