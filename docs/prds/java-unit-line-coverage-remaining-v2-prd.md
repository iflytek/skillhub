# PRD: Remaining Java Line Coverage to 100% (V2)

## 1. Objective

基于当前 JaCoCo 模块级报告，把仍然 `line_missed > 0` 的 Java 生产类全部补齐到 `100%` line coverage，并把覆盖率事实来源、清单和门禁统一起来。

本轮执行策略分两段：

- 核心功能类：要求用完整功能测试把覆盖率直接做到 `100%`
- 非核心支撑类：`0% -> 90%` 仍按正常单测思路补；`90% -> 100%` 才允许 coverage-only 测试

也就是说：

- 核心功能类不存在“先 90% 再说”的降级口径
- 只有非核心类的最后少量边界、fallback、private helper、匿名内部类、cleanup/finally、不可达尾差行，才允许弱断言甚至无断言

当前事实来源：

- `server/skillhub-app/target/site/jacoco/jacoco.csv`
- `server/skillhub-auth/target/site/jacoco/jacoco.csv`
- `server/skillhub-domain/target/site/jacoco/jacoco.csv`
- `server/skillhub-infra/target/site/jacoco/jacoco.csv`
- `server/skillhub-notification/target/site/jacoco/jacoco.csv`
- `server/skillhub-search/target/site/jacoco/jacoco.csv`
- `server/skillhub-storage/target/site/jacoco/jacoco.csv`

精确到类与行号的剩余缺口明细见：

- [java-unit-line-coverage-remaining-v2-line-detail.md](./java-unit-line-coverage-remaining-v2-line-detail.md)

## 2. Current Baseline

生成时间：`2026-05-05`

| Module | Missed Classes | Missed Lines | Coverage |
|------|------:|------:|------:|
| `skillhub-app` | 43 | 183 | 96.42% |
| `skillhub-auth` | 16 | 116 | 94.43% |
| `skillhub-domain` | 25 | 159 | 95.20% |
| `skillhub-infra` | 7 | 12 | 96.10% |
| `skillhub-notification` | 0 | 0 | 100.00% |
| `skillhub-search` | 0 | 0 | 100.00% |
| `skillhub-storage` | 5 | 78 | 63.38% |
| **Total** | **96** | **548** | **95.41%** |

说明：

- `skillhub-notification` 与 `skillhub-search` 当前已达 `100%`，不再作为补测目标。
- 本 PRD 只跟踪当前真实剩余缺口，不重复记录已完成 story。

## 3. Success Criteria

- 所有模块 `jacoco.csv` 中 `LINE_MISSED > 0` 的生产类数量为 `0`
- Java 生产代码 line coverage 达到 `100.00%`
- `server/coverage-baseline.md` 与 `docs/prds/java-unit-line-coverage-inventory.md` 同步为最新事实
- Maven 本地校验命令可稳定失败/通过，且失败输出能定位到具体类

阶段性标准：

- 核心功能类：最终 `100%` 必须来自完整功能测试
- 非核心类：第一轮尽量先清完“简单 / 中等”类；困难类可先做到约 `90%`
- 第二轮：只处理非核心难类的尾差行，最终清零；这一轮才允许 coverage-only 测试

## 3A. Two-Stage Test Principles

### Stage A: 先到 90%

这一阶段仍采用正常测试原则：

- 需要覆盖主要功能路径
- 需要有基本断言
- 需要验证核心返回值、状态变化或交互
- 不追求把每个极端边角一次性做满

核心功能类补充要求：

- 不能把 `90%` 当作阶段终点
- 从一开始就要按最终 `100%` 口径设计测试
- 对主链路、主状态迁移、主异常语义要有完整断言

### Stage B: 90% 之后冲到 100%

只有这一阶段允许 coverage-only 策略：

- 直接实例化对象，调用 public 方法，只求命中剩余行
- 用 `ReflectionTestUtils` 或反射直接调用 private/helper 方法
- 对 DTO / properties / exception / inner class，只做构造与 getter/setter 调用
- 对异常/cleanup/finally 分支，可通过 stub/mock 直接把流程推进到剩余行
- 对 Spring Data repository interface、配置类、`main` 方法、匿名内部类，用最小 smoke test 命中源码行

允许但应限制在 Stage B 的做法：

- 弱断言测试
- 无断言 smoke test
- 只为清尾差存在的 helper test

## 3B. Core vs Non-Core Scope

### 核心功能类

这些类要求以完整功能测试做到 `100%`，不允许用“只跑到行”替代：

- `com.iflytek.skillhub.auth.local.LocalAuthService`
- `com.iflytek.skillhub.auth.local.PasswordResetService`
- `com.iflytek.skillhub.auth.device.DeviceAuthService`
- `com.iflytek.skillhub.auth.config.SecurityConfig`
- `com.iflytek.skillhub.controller.AuthController`
- `com.iflytek.skillhub.controller.UserProfileController`
- `com.iflytek.skillhub.service.SkillDeleteAppService`
- `com.iflytek.skillhub.storage.S3StorageService`
- `com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy`
- `com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator`
- `com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator`
- `com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser`
- `com.iflytek.skillhub.domain.namespace.NamespaceService`
- `com.iflytek.skillhub.domain.user.ProfileReviewService`
- `com.iflytek.skillhub.domain.label.LabelPermissionChecker`
- `com.iflytek.skillhub.domain.social.SkillRating`

### 非核心支撑类

这类可以按“两阶段”推进：

- DTO / properties / exception / record / main
- repository helper / query adapter
- config / post-processor / bootstrap helper
- filter / rate-limit / small checker / small app service 尾差
- mock UASS / mock auth / supporting controller / scanner seam

## 4. Execution Stories

以下顺序按“修改难易优先，组内再按缺口大小优先”排列。

补测口径：

- 核心功能类：从一开始就按完整功能测试做到 `100%`
- 非核心类：Wave 1 ~ Wave 4 默认按正常测试补到目标值
- 只有 Wave 5 的非核心清尾轮，才允许 coverage-only 写法

### Wave 1: 超轻量清扫，优先用正常测试拿掉简单类

### US-R201: 先清 DTO、properties、exception、main、简单 inner class

目标类：

- `com.iflytek.skillhub.dto.MockUassLoginRequest`
- `com.iflytek.skillhub.dto.MockUassLoginResponse`
- `com.iflytek.skillhub.auth.device.DeviceCodeData`
- `com.iflytek.skillhub.auth.uass.UassProperties`
- `com.iflytek.skillhub.auth.merge.AccountMergeRequest`
- `com.iflytek.skillhub.storage.S3StorageProperties`
- `com.iflytek.skillhub.storage.StorageProperties`
- `com.iflytek.skillhub.storage.StorageAccessException`
- `com.iflytek.skillhub.domain.user.ModerationResult`
- `com.iflytek.skillhub.domain.user.UpdateProfileResult`
- `com.iflytek.skillhub.domain.user.UpdateProfileResult.Mixed`
- `com.iflytek.skillhub.domain.namespace.SlugValidator`
- `com.iflytek.skillhub.domain.skill.validation.NoOpPrePublishValidator`
- `com.iflytek.skillhub.SkillhubApplication`

Checklist:

- [ ] 优先补已有简单测试，保留基本断言
- [ ] 对 DTO/properties/exception 仍可用轻量断言快速清零
- [ ] 目标一次性 `100%`

### US-R202: 清接口声明、轻量 repository / config / smoke 入口

目标类：

- `com.iflytek.skillhub.infra.jpa.NotificationJpaRepository`
- `com.iflytek.skillhub.infra.jpa.SkillJpaRepository`
- `com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository`
- `com.iflytek.skillhub.infra.jpa.SecurityAuditJpaRepository`
- `com.iflytek.skillhub.infra.jpa.SkillReportJpaRepository`
- `com.iflytek.skillhub.config.RuntimeStateEnvironmentPostProcessor`
- `com.iflytek.skillhub.config.RedissonConfig`
- `com.iflytek.skillhub.controller.BaseApiController`

Checklist:

- [ ] 用最小但仍有意义的 smoke test 覆盖类型加载、Bean 创建、方法声明行
- [ ] 保留最基本的可执行性断言
- [ ] 目标一次性 `100%`

### Wave 2: 简单控制器和小型 service，优先低成本清零

### US-R203: 清简单 controller / portal / admin 端点

目标类：

- `com.iflytek.skillhub.controller.portal.SkillRatingController`
- `com.iflytek.skillhub.controller.portal.SkillStarController`
- `com.iflytek.skillhub.controller.portal.SkillPublishController`
- `com.iflytek.skillhub.controller.portal.SkillSearchController`
- `com.iflytek.skillhub.controller.admin.AdminProfileReviewController`
- `com.iflytek.skillhub.controller.admin.UserManagementController`
- `com.iflytek.skillhub.controller.AccountMergeController`
- `com.iflytek.skillhub.controller.TokenController`
- `com.iflytek.skillhub.controller.LocalAuthController`

Checklist:

- [ ] 直接调用 controller 方法或使用最小 `MockMvc`
- [ ] 断言核心响应/状态即可，不要求把全部边界做满
- [ ] 目标一次性 `100%`

### US-R204: 清简单 filter / rate-limit / app service 尾差

目标类：

- `com.iflytek.skillhub.filter.AuthContextFilter`
- `com.iflytek.skillhub.filter.RequestLoggingFilter`
- `com.iflytek.skillhub.ratelimit.InMemorySlidingWindowRateLimiter`
- `com.iflytek.skillhub.ratelimit.RateLimitInterceptor`
- `com.iflytek.skillhub.service.AdminProfileReviewAppService`
- `com.iflytek.skillhub.service.AdminSkillReportAppService`
- `com.iflytek.skillhub.service.AdminUserAppService`
- `com.iflytek.skillhub.service.GovernanceWorkbenchAppService`
- `com.iflytek.skillhub.service.SkillSearchAppService`
- `com.iflytek.skillhub.domain.governance.GovernanceNotificationService`
- `com.iflytek.skillhub.domain.audit.AuditLogQueryService`
- `com.iflytek.skillhub.domain.label.LabelSlugValidator`
- `com.iflytek.skillhub.domain.review.ReviewPermissionChecker`

Checklist:

- [ ] 通过 mock 依赖覆盖正常路径和主要分支
- [ ] 对 helper / checker 类，优先直接实例化调用并保留基本断言
- [ ] 目标一次性 `100%`

### Wave 3: 中等复杂度，仍争取一次性清零

### US-R205: 清 device auth / mock UASS / 用户资料相关 controller

目标类：

- `com.iflytek.skillhub.controller.DeviceAuthController`
- `com.iflytek.skillhub.controller.DeviceAuthController.TokenRequest`
- `com.iflytek.skillhub.controller.DeviceAuthWebController`
- `com.iflytek.skillhub.controller.DeviceAuthWebController.AuthorizeRequest`
- `com.iflytek.skillhub.controller.MockUassController`
- `com.iflytek.skillhub.controller.UserProfileController`
- `com.iflytek.skillhub.auth.device.DeviceAuthService`

Checklist:

- [ ] 优先直调 controller/service，而不是完整 Spring 流程
- [ ] 用 mock/stub 覆盖主要功能路径和主要状态分支
- [ ] 目标一次性 `100%`

### US-R206: 清 app repository / boot / service 中等复杂度类

目标类：

- `com.iflytek.skillhub.repository.JpaAdminSkillReportQueryRepository`
- `com.iflytek.skillhub.repository.JpaMySkillQueryRepository`
- `com.iflytek.skillhub.repository.JpaProfileReviewQueryRepository`
- `com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer`
- `com.iflytek.skillhub.service.NamespaceMemberCandidateService`
- `com.iflytek.skillhub.service.ReviewSkillDetailAppService`
- `com.iflytek.skillhub.service.LabelAdminAppService`
- `com.iflytek.skillhub.service.LabelAdminAppService$1`
- `com.iflytek.skillhub.security.AuthFailureThrottleService`
- `com.iflytek.skillhub.storage.LocalFileStorageService`
- `com.iflytek.skillhub.infra.scanner.SkillScannerAdapter`
- `com.iflytek.skillhub.infra.scanner.SkillScannerService`

Checklist:

- [ ] 先用正常单测把主要行为做实
- [ ] 匿名事务回调/cleanup/finally 仍可用手动触发方式覆盖
- [ ] 目标一次性 `100%`

### US-R207: 清 domain 中等复杂度 service / entity

目标类：

- `com.iflytek.skillhub.domain.namespace.NamespaceMemberService`
- `com.iflytek.skillhub.domain.user.UserProfileService`
- `com.iflytek.skillhub.domain.report.SkillReport`
- `com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService`
- `com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService`
- `com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService`
- `com.iflytek.skillhub.domain.security.SecurityScanService`
- `com.iflytek.skillhub.domain.skill.service.SkillTagService`

Checklist:

- [ ] mock repository / notifier / storage，覆盖正常业务流程
- [ ] 优先从现有测试补少量 case 清尾
- [ ] 目标一次性 `100%`

### Wave 4: 困难类分流处理

### US-R208: auth 困难类

目标类：

- `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor`
- `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor$1`
- `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.GitHubEmail`
- `com.iflytek.skillhub.auth.mock.MockAuthFilter`
- `com.iflytek.skillhub.auth.local.PasswordResetService`
- `com.iflytek.skillhub.auth.local.LocalAuthService`
- `com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor`
- `com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver`
- `com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler`
- `com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler`
- `com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport`
- `com.iflytek.skillhub.auth.config.SecurityConfig`

Checklist:

- [ ] `LocalAuthService`、`PasswordResetService`、`DeviceAuthService`、`SecurityConfig` 直接按完整功能测试做到 `100%`
- [ ] 其他非核心 auth 类允许先做主要成功路径、主要失败路径和关键状态转换
- [ ] 对外部调用、filter 链、OAuth request/resolver 用 mock/stub，但仍保留基本断言
- [ ] 阶段目标：核心类 `100%`；非核心难类可先到 `>=90%`

### US-R209: domain 困难类

目标类：

- `com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy`
- `com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser`
- `com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator`
- `com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator`
- `com.iflytek.skillhub.domain.namespace.NamespaceService`
- `com.iflytek.skillhub.domain.user.ProfileReviewService`
- `com.iflytek.skillhub.domain.label.LabelPermissionChecker`
- `com.iflytek.skillhub.domain.social.SkillRating`

Checklist:

- [ ] `SkillPackagePolicy`、`SkillPackageValidator`、`BasicPrePublishValidator`、`SkillMetadataParser`、`NamespaceService`、`ProfileReviewService`、`LabelPermissionChecker`、`SkillRating` 直接按完整功能测试做到 `100%`
- [ ] 其余残余 domain 类按主要规则路径、主要校验结果和主要解析流程推进
- [ ] 对校验类和 parser 类可用最小输入，但仍需保留基本断言
- [ ] 阶段目标：核心类 `100%`；非核心类如有残差可后置

### US-R210: app / storage 困难类

目标类：

- `com.iflytek.skillhub.repository.AdminUserSearchRepository`
- `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository`
- `com.iflytek.skillhub.controller.AuthController`
- `com.iflytek.skillhub.service.SkillDeleteAppService`
- `com.iflytek.skillhub.storage.S3StorageService`

Checklist:

- [ ] `AuthController`、`UserProfileController`、`SkillDeleteAppService`、`S3StorageService` 直接按完整功能测试做到 `100%`
- [ ] `AdminUserSearchRepository`、`JpaGovernanceQueryRepository` 等支撑类可先覆盖主要 public 路径、主要包装分支和主要交互流程
- [ ] 对 criteria / S3 client / auth flow 使用 mock 深度 stub，但保留基本行为断言
- [ ] 阶段目标：核心类 `100%`；非核心困难类可先到 `>=90%`

### Wave 5: 第二轮仅清非核心尾差到 100%

### US-R211: 困难类尾差清零

目标：

- 专门处理非核心类留下的少量尾差行
- 仅在这一轮允许新增“只为覆盖率存在的行触达测试”
- 将所有剩余非核心困难类从 `90%+` 拉到 `100%`

Checklist:

- [ ] 逐类查看剩余 line numbers
- [ ] 仅对非核心特殊尾差行使用反射、mock、helper smoke test 定点命中
- [ ] 这一轮允许弱断言甚至无断言，但范围只限非核心尾差清零

### Wave 6: 统一 inventory 与门禁

### US-R212: 统一 inventory、门禁与最终验证

Checklist:

- [ ] 用模块级 `jacoco.csv` 重生成 `docs/prds/java-unit-line-coverage-inventory.md`
- [ ] 重生成 `server/coverage-baseline.md`
- [ ] 明确门禁以“模块级 CSV 汇总结果”为唯一事实来源，而不是 feature-scope aggregate
- [ ] 新增 Maven/脚本门禁：任一生产类 `LINE_MISSED > 0` 则失败，并打印 `FQCN + line_missed`
- [ ] 在文档中写明本地验证命令和刷新 inventory 的方式

### Wave 7: 全量后端单测与 100% 覆盖率复核

### US-R213: 执行全量后端单测并验证 Java 行覆盖率 100%

目标：

- 在所有补测 story 完成后，统一做一次全量后端单测和 JaCoCo 复核
- 确认不是局部模块达标，而是整个后端 Java 生产代码都已 `100%`

Checklist:

- [ ] 执行全量后端单测命令：`cd server && mvn test jacoco:report`
- [ ] 重生成所有模块级 `jacoco.csv`
- [ ] 确认所有生产类 `LINE_MISSED=0`
- [ ] 确认 `docs/prds/java-unit-line-coverage-inventory.md` 已清空剩余类
- [ ] 更新 `server/coverage-baseline.md` 记录最终 `100%` 结果

### Wave 8: 端到端回归兜底

### US-R214: 执行端到端回归验证以防补测过程引入行为回归

目标：

- 在 `US-R213` 通过后，再做一次真实回归
- 防止为了补单测而误改生产代码

Checklist:

- [ ] 验证登录流
- [ ] 验证一个发布相关流
- [ ] 验证一个搜索流
- [ ] 验证一个治理或审核流
- [ ] 验证 `/actuator/health` 返回 `UP`
- [ ] 在项目文档或 notes 中记录最终回归结果

## 5. Recommended Order

1. `US-R201`
2. `US-R202`
3. `US-R203`
4. `US-R204`
5. `US-R205`
6. `US-R206`
7. `US-R207`
8. `US-R208`
9. `US-R209`
10. `US-R210`
11. `US-R211`
12. `US-R212`
13. `US-R213`
14. `US-R214`

## 6. Difficulty Ranking Summary

### 简单，优先清零

- DTO / properties / exception / record / `main` / interface 声明行
- 单方法 controller 尾差
- 小型 checker / helper / config / smoke 入口

### 中等，尽量一次性清零

- 常规 controller
- filter / rate-limit / app service 尾差
- 简单 repository / scanner / local storage / 事务回调

### 困难核心类，直接做完整的 100%

- `S3StorageService`
- `LocalAuthService`
- `PasswordResetService`
- `DeviceAuthService`
- `SecurityConfig`
- `AuthController`
- `UserProfileController`
- `SkillDeleteAppService`
- `SkillPackagePolicy`
- `SkillMetadataParser`
- `SkillPackageValidator`
- `BasicPrePublishValidator`
- `NamespaceService`
- `ProfileReviewService`
- `LabelPermissionChecker`
- `SkillRating`

### 困难非核心类，先做正确的 90%，再回头清 100%

- `AdminUserSearchRepository`
- `JpaGovernanceQueryRepository`
- `GitHubClaimsExtractor`
- `MockAuthFilter`
- `GitLabClaimsExtractor`
- `SkillHubOAuth2AuthorizationRequestResolver`

## 7. Local Verification

按 story 执行模块级验证后，全量复核执行：

```bash
cd server
mvn test jacoco:report
```

如需补充聚合报告或做收尾核对，再执行：

```bash
cd server
mvn -pl skillhub-app -am test jacoco:report-aggregate
```
