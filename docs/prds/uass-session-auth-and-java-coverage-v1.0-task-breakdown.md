# UASS Session 认证接入与 Java 覆盖率整改 - 实施任务清单

## 1. Purpose

本文档是以下总 PRD 的实施拆解版本：

- [uass-session-auth-and-java-coverage-v1.0-prd.md](./uass-session-auth-and-java-coverage-v1.0-prd.md)

目标：

- 将 UASS Session 认证接入拆成可执行任务
- 将 Java 100% 行覆盖率目标落到具体代码范围和验证动作
- 明确任务依赖关系，便于在同一个 feature 分支中分阶段推进

## 2. Execution Rules

- 所有新增认证能力优先复用现有 Session 架构，不改浏览器主登录模型。
- 所有 UASS 登录中间态存储都必须通过统一抽象访问，不允许业务代码直接操作 Redis 或本地缓存实现。
- 单机测试模式和高可用模式必须通过装配 / 配置切换，不允许在主业务逻辑中散落运行模式判断。
- 每个任务完成时，必须同步补齐单测；不允许“功能先完成、测试后补”。
- 本 feature 范围内新增和重度修改的 Java 生产代码，最终要求 `line missed = 0`。

## 3. Delivery Phases

### Phase A: 基础接入与抽象
- T-001 到 T-004

### Phase B: 登录闭环与前后端打通
- T-005 到 T-008

### Phase C: 状态、登出、双模式
- T-009 到 T-011

### Phase D: 覆盖率补齐与门禁
- T-012 到 T-015

## 4. Task List

### T-001: 增加 UASS 依赖与配置抽象
**Goal**
- 将企业内部 jar 依赖收敛到认证模块，并建立统一配置入口。

**Primary Files**
- [server/skillhub-auth/pom.xml](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/pom.xml)
- [server/skillhub-app/src/main/resources/application.yml](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/resources/application.yml)

**New Files**
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassProperties.java`

**Implementation Notes**
- 在 `skillhub-auth` 模块引入内部 jar 依赖。
- 增加 `skillhub.auth.uass.*` 配置项。
- 明确 `enabled`、`base-url`、`client-id`、`client-secret`、`callback-path`、`state-ttl`、`cache-mode`。

**Acceptance**
- [ ] 应用启动时能成功绑定 `UassProperties`
- [ ] 未配置 UASS 时不影响现有登录功能
- [ ] 对新增配置类增加单测

### T-002: 建立 UASS Client Facade 与领域对象
**Goal**
- 封装内部 jar，不让业务层依赖 jar 的原始类型和协议细节。

**New Files**
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassClientFacade.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassLoginContext.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassUserProfile.java`

**Implementation Notes**
- `Facade` 统一封装：
  - 获取登录 URL
  - 登录校验
  - 登录状态检查
  - 用户信息查询
  - 用户登出
- 所有加解密操作继续由内部 jar 完成。

**Acceptance**
- [ ] 业务层只依赖 `UassClientFacade`
- [ ] 所有 UASS 响应都转换成平台内部 DTO
- [ ] 对 `Facade` 增加成功/失败分支单测

### T-003: 建立 UASS 状态存储抽象
**Goal**
- 支持 Redis 模式和本地内存模式双实现。

**New Files**
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/UassLoginStateStore.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/RedisUassLoginStateStore.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/LocalUassLoginStateStore.java`

**Existing Files to Reuse**
- [RedisTemplateConfig.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/RedisTemplateConfig.java)

**Implementation Notes**
- `login state store` 保存 `uass:state:{state}`
- 本地实现必须支持 TTL 和显式删除
- Redis 不可用或显式关闭时，按配置回落本地实现

**Acceptance**
- [ ] Redis 模式下可读写 state
- [ ] 本地模式下可读写 state
- [ ] 两种模式行为一致，接口签名一致
- [ ] 对 Redis / Local 双实现分别加单测

### T-004: 建立 UASS 用户映射层
**Goal**
- 复用现有本地用户与身份绑定模型。

**Existing Files to Reuse**
- [IdentityBindingService.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/IdentityBindingService.java)

**New Files**
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassPrincipalFactory.java`

**Implementation Notes**
- 将 UASS 用户映射为统一身份对象
- 建议使用：
  - `provider = "uass"`
  - `subject = user_code`
  - `providerLogin = 登录名/工号`
  - `email = 公司邮箱`
- 登录回调后先按 `user_code` 查本地用户 / 绑定
- 查不到则根据 UASS 返回信息创建用户
- 创建成功后继续本地登录流程，不中断登录闭环

**Acceptance**
- [ ] 首次登录可创建/绑定本地用户
- [ ] 重复登录可命中已有 identity binding
- [ ] 用户被禁用/待审批时行为与现有主链路一致
- [ ] 对映射和绑定逻辑加单测

### T-005: 实现 UASS 登录编排服务
**Goal**
- 打通“生成登录入口 -> callback 校验 -> 查询用户 -> 建 Session -> 写缓存”。

**Existing Files to Reuse**
- [PlatformSessionService.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/session/PlatformSessionService.java)

**New Files**
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/UassAuthService.java`

**Implementation Notes**
- 生成 `state`
- 保存 `state -> returnTo`
- callback 时校验 `state`
- 调用 `UassClientFacade` 完成登录校验和用户信息查询
- 使用 `user_code` 查询本地用户；不存在则创建用户
- 建立 SkillHub Session
- 消费或清理 `UASS login state store`

**Acceptance**
- [ ] 登录成功后浏览器进入 SkillHub 已登录状态
- [ ] 非法或过期 state 被拒绝
- [ ] callback 失败不会留下脏 Session
- [ ] 首次登录用户可在同一次 callback 流程中自动建档并完成登录
- [ ] 对编排服务的正常/异常分支加单测

### T-006: 新增 UASS Auth Controller
**Goal**
- 对前端暴露 UASS 专用认证入口。

**New Files**
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/UassAuthController.java`

**Required Endpoints**
- `GET /api/v1/auth/uass/login-url`
- `GET /api/v1/auth/uass/redirect`
- `GET /api/v1/auth/uass/callback`
- `GET /api/v1/auth/uass/status`
- `POST /api/v1/auth/uass/logout`

**Acceptance**
- [ ] 所有接口返回或跳转行为符合 PRD
- [ ] callback 能处理 `returnTo`
- [ ] controller 层新增测试覆盖输入校验和状态码

### T-007: 安全路由与放行策略调整
**Goal**
- 让新增 UASS 路由进入现有 Spring Security 目录。

**Existing Files**
- [RouteSecurityPolicyRegistry.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java)

**Implementation Notes**
- `permitAll`：
  - `/api/v1/auth/uass/login-url`
  - `/api/v1/auth/uass/redirect`
  - `/api/v1/auth/uass/callback`
  - `/api/v1/auth/uass/status`
- `authenticated`：
  - `/api/v1/auth/uass/logout`

**Acceptance**
- [ ] 未登录用户能进入登录入口与 callback
- [ ] 未登录用户不能直接调用 logout
- [ ] 安全策略测试补齐

### T-008: 前端登录页新增企业登录入口
**Goal**
- 让 Web 登录页可进入 UASS 登录流程。

**Primary Files**
- [web/src/pages/login.tsx](/Users/robin-mac/ai-code/jk-code/skillhub/web/src/pages/login.tsx)
- `web/src/api/client.ts`
- `web/src/api/types.ts`

**Implementation Notes**
- 新增“企业登录”按钮
- 点击后走 `/api/v1/auth/uass/redirect` 或先拉 `login-url`
- 登录成功后回到 `returnTo`

**Acceptance**
- [ ] 登录页展示企业登录入口
- [ ] 正常跳转到 UASS
- [ ] callback 成功后回到预期页面
- [ ] 前端单测与 typecheck 通过

### T-009: 实现状态检查与失效处理
**Goal**
- 基于本地 Session 返回明确登录状态，并在需要时支持远端增强校验。

**Primary Files**
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/UassAuthService.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/UassAuthController.java`

**Implementation Notes**
- `status` 默认返回两种状态：
  - 未登录
  - 已登录
- 如果 UASS jar 确认支持登录后状态校验，可扩展远端增强检查，但不作为主登录态依据

**Acceptance**
- [ ] 状态检查以本地 Session 为准
- [ ] 状态检查单测补齐

### T-010: 实现统一登出闭环
**Goal**
- 同时清理 UASS 侧状态、SkillHub Session 和本地缓存。

**Existing Files to Reference**
- [AuthContextFilter.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/filter/AuthContextFilter.java)

**Implementation Notes**
- 先尝试调用 UASS 登出
- 再删除 UASS 登录中间态
- 再删除本地 Session / SecurityContext
- 远端失败时本地仍要强制清理

**Acceptance**
- [ ] 登出成功后重新访问受保护页面会回到登录页
- [ ] UASS 远端失败时本地状态也会被清理
- [ ] 登出相关单测补齐

### T-011: 支持单机模式与 HA 模式装配切换
**Goal**
- 将 Redis / Local 模式切换收敛到装配层。

**Primary Files**
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/config/*`
- [application.yml](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/resources/application.yml)

**Implementation Notes**
- 增加 `cache-mode=redis|local|auto`
- 单机测试模式支持无 Redis 启动
- 日志中明确标识当前运行模式不是 HA

**Acceptance**
- [ ] Redis 模式可正常装配
- [ ] Local 模式可正常装配
- [ ] `auto` 模式行为可预测并有测试覆盖

### T-012: 建立覆盖率基线
**Goal**
- 明确本 feature 影响范围内哪些类必须清零 missed lines。

**Inputs**
- [java-unit-line-coverage-inventory.md](/Users/robin-mac/ai-code/jk-code/skillhub/docs/prds/java-unit-line-coverage-inventory.md)

**Implementation Notes**
- 列出本次新增和重改类
- 列出需要顺带补齐的现有认证/缓存/Session 辅助类

**Acceptance**
- [ ] 形成 feature 范围 coverage 清单
- [ ] 清单中的类都有对应测试 owner

### T-013: 补齐新增代码单测到 100% line coverage
**Goal**
- 对本次新增 UASS 相关代码做到 `line missed = 0`。

**Scope**
- UASS facade
- state store
- service
- controller
- config / properties
- mapper / principal factory

**Acceptance**
- [ ] 新增类全部 `line missed = 0`
- [ ] 异常分支有断言，不是只跑过代码

### T-014: 补齐既有认证与会话辅助代码测试
**Goal**
- 对因本次 feature 被重改或新增分支的既有类补测。

**Likely Existing Classes**
- `PlatformSessionService`
- `AuthContextFilter`
- `RouteSecurityPolicyRegistry`
- 相关 auth config / exception handling

**Acceptance**
- [ ] 所有被重改既有类覆盖率达标
- [ ] 不存在“新增逻辑在旧类中但没有补测”的遗漏

### T-015: 增加 Maven 覆盖率门禁与验证命令
**Goal**
- 防止 feature 合并前后覆盖率回退。

**Primary Files**
- `server/pom.xml`
- 各模块 `pom.xml`（如需要）
- `Makefile`（如需要）
- 开发文档（如需要）

**Implementation Notes**
- 增加 JaCoCo 校验规则或等价门禁
- 明确本地执行方式

**Acceptance**
- [ ] 覆盖率不达标时构建失败
- [ ] 本地验证命令写入文档
- [ ] CI 或本地脚本可重复执行

## 5. Dependency Graph

- `T-001` -> `T-002`
- `T-002` -> `T-005`
-( `T-003` and `T-004` ) -> `T-005`
- `T-005` -> `T-006`
- `T-006` -> `T-007`
- `T-006` -> `T-008`
- `T-005` -> `T-009`
- `T-005` -> `T-010`
- `T-003` -> `T-011`
- `T-001` to `T-011` -> `T-012`
- `T-012` -> `T-013`, `T-014`
- `T-013`, `T-014` -> `T-015`

## 6. Suggested Commit Strategy

1. `feat(auth): add uass facade and config skeleton`
2. `feat(auth): add uass session store abstraction with redis/local impl`
3. `feat(auth): implement uass login callback and session establishment`
4. `feat(auth): add uass status and logout flows`
5. `feat(web): add uass login entry`
6. `test(auth): complete uass flow coverage`
7. `build(test): enforce jacoco line coverage gate`

## 7. Verification Checklist

- [ ] Redis 模式下登录成功、刷新页面后仍保持已登录
- [ ] Local 模式下登录成功、刷新页面后仍保持已登录
- [ ] Redis 模式下多副本跨节点访问不丢登录态
- [ ] 本地模式下明确标识非 HA
- [ ] `status` 能区分三类状态
- [ ] `logout` 能清理本地和缓存状态
- [ ] feature 范围内 Java 生产代码 `line missed = 0`
- [ ] 覆盖率门禁已接入构建
