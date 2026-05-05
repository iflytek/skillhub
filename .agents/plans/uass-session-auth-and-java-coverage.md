# 功能: UASS Session Auth And Java Coverage

下面这份计划应尽可能完整，但在真正开始实现前，你仍然必须再次验证文档、代码库模式以及任务本身是否合理。

特别注意现有 utils、types、models 的命名，并确保从正确的文件中导入。

## 功能描述

为 SkillHub 增加企业内部 UASS 登录接入能力，保持浏览器主登录模型为 Session，并兼容两种运行模式：

- 生产高可用：Redis 共享 Session + Redis 共享 UASS 缓存
- 单机测试：本地 Session + 本地内存 UASS 缓存

同时，将本次 feature 涉及的 Java 生产代码 JaCoCo line coverage 提升到 100%，并建立自动门禁，避免认证链路在交付时缺乏测试保护。

## 用户故事

作为一名企业内部平台用户
我想要通过现有 UASS 登录 SkillHub 并在后续请求中保持已登录状态
以便在不感知底层认证切换的前提下继续使用平台的发布、治理和下载能力。

## 问题陈述

当前 SkillHub 的主登录体系围绕本地账号、OAuth、直连登录和被动 Session bootstrap 展开，但没有企业内部 UASS 的私有 jar 适配层。UASS 登录如果直接散落在业务代码中，会引入以下问题：

- jar 依赖和加解密细节扩散到业务层
- UASS 回跳登录缺少统一的 Session 编排与 state 生命周期管理
- 高可用与单机测试模式切换难以收敛
- Session、缓存、回调、登出等高风险路径缺少针对性测试
- 认证主链路改造后容易出现 coverage 回退

## 方案陈述

在不改变浏览器主登录模型的前提下，为 SkillHub 增加 UASS 适配层、登录编排服务和缓存抽象：

- 在 `skillhub-auth` 模块内封装 UASS jar 到 `UassClientFacade`
- 在 `skillhub-auth` 模块内新增 `UassLoginStateStore` 抽象，以及 Redis / Local 双实现
- 在 `skillhub-app` 模块内新增 `UassAuthService` 与 `UassAuthController`
- callback 成功后必须先按统一登录平台返回的 `user_code` 查找本地用户；若不存在，则根据回传信息自动创建用户，再复用现有 `IdentityBindingService` 和 `PlatformSessionService` 建立本地用户与 Session
- 将 UASS 登录过程中的 `state` 写入统一存储抽象，生产走 Redis，单机测试走本地 TTL 缓存
- 新增或重改的 Java 生产代码全部补齐测试，并通过 Maven / Makefile 验证到 100% line coverage

## 功能元数据

**功能类型**: 增强
**预估复杂度**: 高
**主要受影响系统**: `skillhub-auth`, `skillhub-app`, `web`, Redis Session, Spring Security 路由策略, 测试与构建链路
**依赖项**: 企业内部 UASS jar, Spring Security, Spring Session Redis, RedisTemplate, React 登录页, JaCoCo/Maven 测试链路

---

## 上下文参考

### 相关代码文件 重要：实现前你必须先阅读这些文件！

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/session/PlatformSessionService.java:20-81` - 原因：本地登录态如何持久化到 `HttpSession` 与 Spring Security Context。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/filter/AuthContextFilter.java:111-127` - 原因：后续请求如何从 `SecurityContext` 或 Session 中恢复 `PlatformPrincipal`。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/IdentityBindingService.java:41-90` - 原因：外部身份如何绑定或创建本地用户，必须复用这条链路而不是新造用户模型。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/AuthController.java:121-159` - 原因：现有认证入口风格、返回结构和限流模式示例；新增 UASS 接口应对齐这一层 transport 风格。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SessionBootstrapService.java:17-50` - 原因：外部 Session -> 本地 Session 的现有编排模式可作为 UASS Session 方案参考。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/DirectAuthService.java:18-55` - 原因：provider 映射与开关控制模式可借鉴，但本次不直接走密码直连主路径。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java:18-35` - 原因：新增认证端点必须在这里明确放行或要求鉴权。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/SecurityConfig.java:90-149` - 原因：了解整体过滤器链、logout URL、API 入口点和 SessionCreationPolicy。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/RedisTemplateConfig.java:14-32` - 原因：现有认证跨切面共享 RedisTemplate 配置，UASS Redis store 应优先复用。
- `server/skillhub-app/src/main/resources/application.yml:40-48` - 原因：现有 Redis 和 Spring Session 配置入口，UASS cache mode 要与现有运行时配置协同。
- `server/skillhub-app/src/main/resources/application-local-mysql.yml:1-24` - 原因：单机轻量模式下本地缓存与 UASS 配置的兼容性需要明确。
- `web/src/pages/login.tsx:20-80` - 原因：当前登录页结构、returnTo 处理和 tab 布局；UASS 入口要融入这里。
- `web/src/features/auth/use-password-login.ts:11-34` - 原因：当前密码登录 mutation 的模式；新增 UASS 登录不能破坏现有本地/direct 登录逻辑。
- `web/src/features/auth/login-button.tsx:1-44` - 原因：OAuth 登录按钮渲染方式；如果需要统一展示风格可参考这里。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AuthControllerTest.java` - 原因：认证控制器测试风格参考。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/LocalAuthControllerTest.java` - 原因：Session 建立、错误处理和响应结构的现有测试模式。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SessionBootstrapControllerTest.java` - 原因：外部身份 -> 本地 Session 编排的测试样例。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/DirectAuthControllerTest.java` - 原因：provider 切换、禁用开关和限流失败路径的测试样例。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/service/AuthMethodCatalogTest.java` - 原因：登录方式目录的测试模式，可用于新增 UASS 登录入口元数据（若需要）。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/filter/AuthContextFilterTest.java` - 原因：Session 恢复与登出清理路径测试参考。
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistryTest.java` - 原因：新增路由放行规则必须补同类测试。
- `docs/prds/uass-session-auth-and-java-coverage-v1.0-prd.md` - 原因：总需求、非目标、成功指标。
- `docs/prds/uass-session-auth-and-java-coverage-v1.0-task-breakdown.md` - 原因：已拆好的任务边界和依赖顺序，实施时必须严格对齐。

### 需要创建的新文件

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassProperties.java` - UASS 配置绑定。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassClientFacade.java` - 内部 jar 统一适配入口。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassLoginContext.java` - 登录上下文 DTO。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassUserProfile.java` - 用户信息 DTO。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassPrincipalFactory.java` - UASS 用户 -> 本地身份映射。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/UassLoginStateStore.java` - callback state 缓存抽象。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/RedisUassLoginStateStore.java` - Redis state 实现。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/LocalUassLoginStateStore.java` - 本地 TTL state 实现。
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/config/*` - UASS 装配配置。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/UassAuthService.java` - 登录编排服务。
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/UassAuthController.java` - UASS HTTP 入口。
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/uass/...` - UASS 适配和缓存层单测。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/UassAuthControllerTest.java` - Controller 单测。
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/service/UassAuthServiceTest.java` - 编排服务单测。
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/uass/store/...` - Redis/Local 双实现测试。

### 相关文档 实现前你应该先阅读这些文档！

- `docs/03-authentication-design.md`
  - 具体章节：认证流程、Provider 扩展、准入策略
  - 原因：确保 UASS 接入不破坏现有认证边界和用户状态模型。
- `docs/06-api-design.md`
  - 具体章节：`/api/v1/auth/*` 接口、登录目录、whoami/logout 语义
  - 原因：新增 UASS 控制器要与现有 API 风格一致。
- `docs/09-deployment.md`
  - 具体章节：单机 / 高可用运行模型、Redis/Session、生产入口
  - 原因：UASS 缓存模式必须同时兼容 HA 和单机测试。
- `docs/08-frontend-architecture.md`
  - 具体章节：登录页、认证入口、前端 runtime config
  - 原因：前端登录页接入点和路由约束。
- `https://docs.spring.io/spring-session/reference/configuration/redis.html`
  - 具体章节：Spring Session backed by Redis
  - 原因：确认现有 Redis Session 模型的行为边界，避免重复发明 Session 共享机制。
- `https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html`
  - 具体章节：Session Management
  - 原因：确认 session fixation、防止误用 custom session persistence。

### 需要遵循的模式

**命名约定：**
- Java 业务类使用职责命名：`*Controller`、`*Service`、`*Properties`、`*Repository`。
- 认证扩展点接口已存在类似模式：`DirectAuthProvider`、`PassiveSessionAuthenticator`。UASS 相关类型应保持同样的职责清晰度。

**错误处理：**
- Controller 层只做 transport 和参数整形，具体异常由 service 抛出，统一交给全局异常处理。
- 现有认证链路使用 `ForbiddenException`、`BadRequestException`、`UnauthorizedException`、`AuthFlowException` 等进行语义区分；UASS 实现必须复用这一模式，而不是直接抛出 jar 的底层异常。
- 用户自动创建失败必须被视为登录失败的一部分，不能留下半成功 Session。

**日志模式：**
- 认证和安全相关日志需要聚焦状态、provider、userId、requestId、错误类别，不要记录敏感回调参数或内部凭据。
- 敏感字段屏蔽应遵守现有 `SensitiveLogSanitizer` 约束。

**缓存模式：**
- 不允许业务层直接 `redisTemplate.opsForValue()`；必须经由统一 store abstraction。
- 运行模式切换靠配置和装配，不靠业务 if/else。

**测试模式：**
- Controller 测试优先复用现有 `@WebMvcTest` / MockMvc 风格。
- Service / adapter 测试优先做分支驱动断言，不能只跑 happy path。
- 本 feature 覆盖率目标是 `line missed = 0`，因此配置类、store、异常分支都要单测。

**反模式：**
- 不要把 UASS jar 类型扩散到 `skillhub-app` 业务层。
- 不要让前端保存任何 UASS 凭据作为主调用凭证。
- 不要让单机模式和 HA 模式共享一套不透明 fallback 逻辑；必须显式可观测。

---

## 实现计划

### 阶段 1：基础准备

建立 UASS 配置、适配层和缓存抽象，先把内部 jar 与运行模式边界收敛。

**任务：**
- 增加 UASS 配置类与配置项
- 封装内部 jar 为 `UassClientFacade`
- 创建用户信息 / 登录上下文 DTO
- 创建 `UassLoginStateStore` 抽象
- 实现 Redis 与 Local 双 store
- 设计本地缓存 TTL 策略和 cache-mode 配置

### 阶段 2：核心实现

打通 UASS callback -> 用户查询 -> 本地用户绑定 -> Session 建立 -> 缓存写入。

**任务：**
- 实现 `UassPrincipalFactory`
- 复用 `IdentityBindingService` 完成本地用户绑定
- 实现 `UassAuthService`
- 支持 state 生成、state 校验、returnTo 恢复
- callback 成功后按 `user_code` 查询或创建本地用户，再写 Session，并消费/清理 login state

### 阶段 3：集成

接入 HTTP 入口、Spring Security 放行规则和登录页 UI 入口。

**任务：**
- 新增 `UassAuthController`
- 在 `RouteSecurityPolicyRegistry` 中为新增端点配置 `permitAll`/`authenticated`
- 在登录页中新增企业登录入口
- 如需要，在前端 API client 中新增 UASS auth API 封装
- 更新运行时配置和部署文档（如实现时需要）

### 阶段 4：测试与验证

围绕新增链路和重改类补齐所有单测，并建立覆盖率门禁。

**任务：**
- 为 facade、stores、service、controller、properties、principal factory 实现单测
- 为 Redis / Local 双模式编排失败路径补测
- 为回调异常、状态检查、登出失败兜底补测
- 对重改既有类补测：`PlatformSessionService`、`AuthContextFilter`、`RouteSecurityPolicyRegistry`
- 增加 JaCoCo line coverage gate

---

## 分步任务

重要：严格按顺序执行所有任务，从上到下。每个任务都必须是原子性的，并且可独立测试。

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassProperties.java

- **IMPLEMENT**: 定义 `enabled`, `baseUrl`, `clientId`, `clientSecret`, `callbackPath`, `stateTtl`, `cacheMode` 等配置；必要时加入校验默认值。
- **PATTERN**: `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/DirectAuthProperties.java`, `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AuthSessionBootstrapProperties.java`
- **IMPORTS**: `@ConfigurationProperties`, `@Component` 或集中配置类
- **GOTCHA**: 不要把 UASS 私密配置放到 `skillhub-app` 专属类中，配置归属应靠近认证集成。
- **VALIDATE**: `make test-backend-app`

### UPDATE server/skillhub-auth/pom.xml

- **IMPLEMENT**: 增加内部 UASS jar 依赖；如果需要本地安装 jar，记录构建前置要求。
- **PATTERN**: 现有 `skillhub-auth` 依赖声明风格
- **IMPORTS**: 内部仓库坐标或本地 system/私服依赖方式
- **GOTCHA**: 不要把内部 jar 依赖加到 `skillhub-app`，避免扩散到上层模块。
- **VALIDATE**: `cd server && ./mvnw -pl skillhub-auth -am test -DskipTests`

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassClientFacade.java

- **IMPLEMENT**: 统一暴露 `buildLoginUrl`, `validateLogin`, `checkStatus`, `loadUserProfile`, `logout` 等方法。
- **PATTERN**: `OAuthClaimsExtractor` 的 provider 封装思想；`SessionBootstrapService` 的单一职责边界
- **IMPORTS**: 内部 jar API、UASS DTO
- **GOTCHA**: 所有底层 jar 异常必须转换为平台异常或明确的受检/领域异常；严禁泄漏 token 到日志。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassLoginContext.java
### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassUserProfile.java
### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassTokenBundle.java

- **IMPLEMENT**: 把 UASS 登录校验结果和用户信息标准化为项目内部 DTO。
- **PATTERN**: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AuthMeResponse.java`
- **IMPORTS**: `record` 或标准 POJO
- **GOTCHA**: DTO 只保留当前方案真正需要的字段，避免预留无实际生命周期管理的 token 结构。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/UassLoginStateStore.java

- **IMPLEMENT**: 定义 `saveState`, `consumeState`, `deleteState` 等最小接口。
- **PATTERN**: `RedisTemplateConfig` 的共享依赖方式；避免暴露实现细节
- **IMPORTS**: `Duration`, DTO
- **GOTCHA**: 接口必须兼容 Redis 和 Local 双实现；不要在接口层暴露 Redis key。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/RedisUassLoginStateStore.java

- **IMPLEMENT**: 使用 `RedisTemplate<String, Object>` 写 `uass:state:{state}`。
- **PATTERN**: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/RedisTemplateConfig.java:14-32`
- **IMPORTS**: `RedisTemplate`, `Duration`
- **GOTCHA**: TTL 建议 5-10 分钟；value 序列化应复用现有 Jackson serializer。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/store/LocalUassLoginStateStore.java

- **IMPLEMENT**: 基于进程内 TTL 缓存实现单机模式 fallback。
- **PATTERN**: 项目中暂无现成 TTL cache，实现时保持单一职责并提供时钟注入以便测试。
- **IMPORTS**: `ConcurrentHashMap`, `Clock`, `Instant`
- **GOTCHA**: 必须实现显式过期清理和读取时过期判断；明确记录“非 HA 模式”。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/uass/UassPrincipalFactory.java

- **IMPLEMENT**: 将 `UassUserProfile` 映射为可供 `IdentityBindingService` 消费的统一身份对象；必要时构造等价 `OAuthClaims`。
- **PATTERN**: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/IdentityBindingService.java:41-90`
- **IMPORTS**: `OAuthClaims`, `PlatformPrincipal`, `UserStatus`
- **GOTCHA**: `subject` 必须使用统一登录平台返回的 `user_code`；不要使用易变用户名、显示名或邮箱昵称。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-app/src/main/java/com/iflytek/skillhub/service/UassAuthService.java

- **IMPLEMENT**: 编排 login-url、redirect、callback、status、logout；负责 state 生命周期、用户信息查询、Session 建立和 UASS cache 写入。
- **PATTERN**: `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SessionBootstrapService.java:17-50`, `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/DirectAuthService.java:18-55`
- **IMPORTS**: `PlatformSessionService`, `IdentityBindingService`, `UassClientFacade`, `UassLoginStateStore`
- **GOTCHA**: callback 失败不能留下脏 state/Session；必须先按 `user_code` 查找或创建用户再建 Session；status 以本地 Session 为准；logout 必须本地清理优先保证。
- **VALIDATE**: `make test-backend-app`

### CREATE server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/UassAuthController.java

- **IMPLEMENT**: 暴露 `/api/v1/auth/uass/login-url`, `/redirect`, `/callback`, `/status`, `/logout`。
- **PATTERN**: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/AuthController.java`, `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/LocalAuthController.java`
- **IMPORTS**: `ApiResponse`, `HttpServletRequest`, `ResponseEntity`, `@AuthenticationPrincipal`
- **GOTCHA**: callback 建议支持 `returnTo` 恢复；redirect 与 callback 要清楚区分 JSON 响应和 302 行为。
- **VALIDATE**: `make test-backend-app`

### UPDATE server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java

- **IMPLEMENT**: 为新增 UASS 端点配置 `permitAll` / `authenticated` 策略。
- **PATTERN**: 现有 `/api/v1/auth/session/bootstrap` 与 `/api/v1/auth/direct/login` 放行方式 (`RouteSecurityPolicyRegistry.java:23-29`)
- **IMPORTS**: 无新增特殊依赖
- **GOTCHA**: 不要误把 `/logout` 设为 `permitAll`；如果 `status` 需要匿名可达，要明确只返回脱敏状态。
- **VALIDATE**: `make test-backend-app`

### UPDATE server/skillhub-app/src/main/resources/application.yml

- **IMPLEMENT**: 增加 `skillhub.auth.uass.*` 配置；必要时为本地模式补默认值。
- **PATTERN**: `spring.session.store-type=redis`, `skillhub.security.scanner.*`, `skillhub.auth.direct.*`
- **IMPORTS**: N/A
- **GOTCHA**: 本地默认不要误开 UASS；生产默认值不能带演示凭据。
- **VALIDATE**: `make test-backend-app`

### UPDATE web/src/pages/login.tsx

- **IMPLEMENT**: 新增企业登录入口，不破坏现有 password / oauth tab；保留 `returnTo`。
- **PATTERN**: `login.tsx:20-80` 现有登录页组织方式，`login-button.tsx` 的按钮风格
- **IMPORTS**: 前端 UASS auth API client, Button, navigate helpers
- **GOTCHA**: 不要让 UASS 入口和 `usePasswordLogin` 产生行为耦合；避免把任何回调凭据透传到前端状态。
- **VALIDATE**: `make typecheck-web && make test-frontend`

### UPDATE web/src/api/client.ts
### UPDATE web/src/api/types.ts

- **IMPLEMENT**: 增加 `uassAuthApi`、对应响应类型和状态类型。
- **PATTERN**: 现有 `authApi`, `tokenApi` 的封装方式
- **IMPORTS**: OpenAPI types / 自定义 runtime types
- **GOTCHA**: 如果后端尚未进入 openapi schema，先保持轻量自定义类型，后续再 `make generate-api`。
- **VALIDATE**: `make typecheck-web && make test-frontend`

### CREATE server/skillhub-app/src/test/java/com/iflytek/skillhub/service/UassAuthServiceTest.java
### CREATE server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/UassAuthControllerTest.java
### CREATE server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/uass/...`

- **IMPLEMENT**: 为 facade、stores、service、controller、properties、principal factory 及双模式装配补齐测试。
- **PATTERN**: `AuthControllerTest`, `LocalAuthControllerTest`, `SessionBootstrapControllerTest`, `RouteSecurityPolicyRegistryTest`, `AuthContextFilterTest`
- **IMPORTS**: JUnit 5, AssertJ, Mockito, MockMvc
- **GOTCHA**: 不要只测 happy path；必须覆盖 state 过期、用户查询失败、远端 logout 失败、本地缓存 TTL 过期、Redis 不可用 fallback。
- **VALIDATE**: `make test-backend-app`

### UPDATE server/pom.xml / module pom(s)

- **IMPLEMENT**: 增加 JaCoCo line coverage gate，至少约束本 feature 范围相关模块和类；若现阶段无法精确到类，先对相关模块建立门禁并在说明中注明。
- **PATTERN**: 项目当前无 Jacoco 门禁，需要按 Maven 常规方式新增插件配置
- **IMPORTS**: `jacoco-maven-plugin`
- **GOTCHA**: 不要一次性强推全仓 100% 导致 feature 无法落地；门禁范围必须和 PRD 中“本 feature 范围”一致。
- **VALIDATE**: `cd server && ./mvnw -pl skillhub-app -am test`

---

## 测试策略

### 单元测试

- 认证适配层：mock 内部 UASS jar / facade 依赖，验证返回值转换和异常映射。
- 缓存层：Redis/Local 双实现都要覆盖 save/find/delete/TTL/expired branches。
- 服务层：覆盖 login-url、redirect、callback、status、logout 的成功与失败路径。
- 用户映射：覆盖首次创建、已绑定用户、禁用用户、待审批用户。
- 路由策略：确认 UASS 新端点的 `permitAll` / `authenticated` 行为。

设计单元测试时，fixture 和断言应遵循现有测试方式。

### 集成测试

- MockMvc 验证 `/api/v1/auth/uass/*` 的 transport 行为。
- 验证 callback 后 `Session` 建立、重定向恢复、logout 清理。
- 对单机模式至少增加一条完整闭环测试：不依赖 Redis 启动并完成登录 -> status -> logout。

### 边界情况

- state 缺失、重复消费、过期
- UASS 登录校验失败
- UASS 用户信息查询失败
- UASS 返回用户缺少 `user_code`
- 本地用户自动创建失败
- UASS 返回用户缺少稳定唯一 ID
- callback 时 state 缺失/过期
- UASS 远端 logout 失败
- Redis 不可用但 cache-mode=local
- cache-mode=auto 的降级路径
- 用户被禁用 / 待审批
- `returnTo` 非法路径被 sanitize

---

## 验证命令

执行所有命令，确保零回归与功能 100% 正确。

### 级别 1：语法与风格

```bash
make typecheck-web
make lint-web
```

### 级别 2：单元测试

```bash
make test-backend-app
make test-frontend
```

### 级别 3：集成测试

```bash
make test-backend-app
```

说明：当前后端 controller / service 集成测试主要包含在 `skillhub-app` 模块测试集中。

### 级别 4：手动验证

1. 在无 Redis 单机模式下启动应用
2. 打开 `/login`
3. 点击企业登录入口，验证跳转到 UASS
4. 模拟 callback 成功后回到 `returnTo`
5. 刷新页面，确认仍为已登录
6. 调用 `/api/v1/auth/uass/status`，确认返回“完全已登录”
7. 调用 `/api/v1/auth/uass/logout`，确认再次访问受保护页面会跳回登录页
8. 在 Redis 模式下重复上述流程，并验证跨节点访问仍保持已登录

### 级别 5：附加验证（可选）

- 若 UASS 提供测试环境，可增加真实端到端联调脚本
- 若存在浏览器自动化能力，可用浏览器验证登录页企业入口与回跳行为

---

## 验收标准

- [ ] 功能实现了总 PRD 和任务拆解文档中的所有指定需求
- [ ] UASS 登录、callback、status、logout 在 Redis 和 Local 双模式下均可用
- [ ] 所有验证命令都零错误通过
- [ ] 本 feature 范围内 Java 生产代码 line coverage 达到 100%
- [ ] 集成测试验证了登录闭环与登出闭环
- [ ] 代码遵循项目约定与模式
- [ ] 现有登录方式无回归
- [ ] 文档已更新（必要时更新运行方式和配置说明）
- [ ] 安全问题已处理（不泄漏敏感回调信息、不破坏 session fixation 防护）

---

## 完成检查清单

- [ ] 所有任务均已按顺序完成
- [ ] 每个任务的验证都已立即通过
- [ ] 所有验证命令都已成功执行
- [ ] 完整测试套件通过（单元 + 集成）
- [ ] 无 lint 或类型检查错误
- [ ] 手动测试确认功能可用
- [ ] 验收标准全部满足
- [ ] 已完成代码质量与可维护性审查

---

## 备注

- 当前仓库 `docs/09-deployment.md` 与 README 中提到的部分 `deploy/k8s`、`monitoring/`、`validate-release-config.sh` 在当前 checkout 中并不存在，实现时不要把这些不存在的文件当成可直接修改的资产。
- UASS 属于企业内部私有能力，本计划假定没有公开的官方集成文档；实现时必须以内部 jar 的真实接口契约为准。
- 覆盖率目标应优先聚焦本 feature 范围，避免在同一 feature 中无边界扩展为全仓 100% 改造。
- 如果最终发现 UASS jar 线程模型、回调语义或状态检查语义与当前假设不一致，应先更新此计划再实施。

**信心分数**：8/10，首次执行成功的主要风险集中在内部 UASS jar 的真实接口契约，以及“登录状态检查”到底是 callback 校验还是登录后远端状态校验。
