# Java Unit Test Line Coverage Inventory

当前清单基于 `server/*/target/site/jacoco/jacoco.csv` 生成。

说明：
- 仅列出 `line_missed > 0` 的类
- 指标为 Java 生产代码的 JaCoCo line coverage
- 按模块分组，便于后续拆批补测
- 报告命令：`mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`
- 聚合报告路径：`server/skillhub-app/target/site/jacoco-aggregate/index.html`
- 当前后端多模块 aggregate line coverage 为 `95.41%`（`11397/11945`）

### Explicit Exclusions from Production Gate

以下类虽被跟踪，但因属于 mock / dev-only / platform-specific，明确排除在覆盖率门禁之外：

- `com.iflytek.skillhub.auth.device.DeviceAuthService`
- `com.iflytek.skillhub.auth.device.DeviceCodeData`
- `com.iflytek.skillhub.auth.device.DeviceCodeResponse`
- `com.iflytek.skillhub.auth.device.DeviceCodeStatus`
- `com.iflytek.skillhub.auth.device.DeviceTokenResponse`
- `com.iflytek.skillhub.auth.mock.MockAuthFilter`
- `com.iflytek.skillhub.bootstrap.LocalDevDataInitializer`
- `com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer`
- `com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer.IndexInspection`
- `com.iflytek.skillhub.controller.DeviceAuthController`
- `com.iflytek.skillhub.controller.DeviceAuthController.TokenRequest`
- `com.iflytek.skillhub.controller.DeviceAuthWebController`
- `com.iflytek.skillhub.controller.DeviceAuthWebController.AuthorizeRequest`
- `com.iflytek.skillhub.controller.MockUassController`
- `com.iflytek.skillhub.dto.MockUassLoginRequest`
- `com.iflytek.skillhub.dto.MockUassLoginResponse`


## skillhub-app

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.repository.AdminUserSearchRepository` | 29 | 3 | 9.38% |
| `com.iflytek.skillhub.controller.MockUassController` | 17 | 3 | 15.00% |
| `com.iflytek.skillhub.controller.DeviceAuthWebController` | 12 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.UserProfileController` | 12 | 71 | 85.54% |
| `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository` | 12 | 150 | 92.59% |
| `com.iflytek.skillhub.controller.AuthController` | 7 | 51 | 87.93% |
| `com.iflytek.skillhub.security.AuthFailureThrottleService` | 7 | 83 | 92.22% |
| `com.iflytek.skillhub.service.SkillDeleteAppService` | 6 | 53 | 89.83% |
| `com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer` | 5 | 24 | 82.76% |
| `com.iflytek.skillhub.controller.DeviceAuthController` | 5 | 0 | 0.00% |
| `com.iflytek.skillhub.service.LabelAdminAppService` | 5 | 72 | 93.51% |
| `com.iflytek.skillhub.controller.LocalAuthController` | 4 | 38 | 90.48% |
| `com.iflytek.skillhub.controller.TokenController` | 4 | 34 | 89.47% |
| `com.iflytek.skillhub.service.NamespaceMemberCandidateService` | 4 | 29 | 87.88% |
| `com.iflytek.skillhub.controller.AccountMergeController` | 3 | 19 | 86.36% |
| `com.iflytek.skillhub.filter.AuthContextFilter` | 3 | 50 | 94.34% |
| `com.iflytek.skillhub.filter.RequestLoggingFilter` | 3 | 45 | 93.75% |
| `com.iflytek.skillhub.ratelimit.InMemorySlidingWindowRateLimiter` | 3 | 15 | 83.33% |
| `com.iflytek.skillhub.service.AdminProfileReviewAppService` | 3 | 12 | 80.00% |
| `com.iflytek.skillhub.service.AdminSkillReportAppService` | 3 | 10 | 76.92% |
| `com.iflytek.skillhub.service.LabelAdminAppService.new TransactionSynchronization() {...}` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.service.ReviewSkillDetailAppService` | 3 | 69 | 95.83% |
| `com.iflytek.skillhub.SkillhubApplication` | 2 | 1 | 33.33% |
| `com.iflytek.skillhub.config.RuntimeStateEnvironmentPostProcessor` | 2 | 9 | 81.82% |
| `com.iflytek.skillhub.controller.portal.SkillPublishController` | 2 | 20 | 90.91% |
| `com.iflytek.skillhub.controller.portal.SkillSearchController` | 2 | 22 | 91.67% |
| `com.iflytek.skillhub.ratelimit.RateLimitInterceptor` | 2 | 60 | 96.77% |
| `com.iflytek.skillhub.repository.JpaAdminSkillReportQueryRepository` | 2 | 31 | 93.94% |
| `com.iflytek.skillhub.repository.JpaMySkillQueryRepository` | 2 | 52 | 96.30% |
| `com.iflytek.skillhub.repository.JpaProfileReviewQueryRepository` | 2 | 38 | 95.00% |
| `com.iflytek.skillhub.service.AdminUserAppService` | 2 | 67 | 97.10% |
| `com.iflytek.skillhub.config.RedissonConfig` | 1 | 45 | 97.83% |
| `com.iflytek.skillhub.controller.BaseApiController` | 1 | 4 | 80.00% |
| `com.iflytek.skillhub.controller.DeviceAuthController.TokenRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.DeviceAuthWebController.AuthorizeRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.admin.AdminProfileReviewController` | 1 | 27 | 96.43% |
| `com.iflytek.skillhub.controller.admin.UserManagementController` | 1 | 14 | 93.33% |
| `com.iflytek.skillhub.controller.portal.SkillRatingController` | 1 | 10 | 90.91% |
| `com.iflytek.skillhub.controller.portal.SkillStarController` | 1 | 10 | 90.91% |
| `com.iflytek.skillhub.dto.MockUassLoginRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.MockUassLoginResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.service.GovernanceWorkbenchAppService` | 1 | 70 | 98.59% |
| `com.iflytek.skillhub.service.SkillSearchAppService` | 1 | 86 | 98.85% |

## skillhub-auth

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor` | 27 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.mock.MockAuthFilter` | 22 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.local.PasswordResetService` | 17 | 97 | 85.09% |
| `com.iflytek.skillhub.auth.local.LocalAuthService` | 16 | 86 | 84.31% |
| `com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor` | 8 | 43 | 84.31% |
| `com.iflytek.skillhub.auth.device.DeviceAuthService` | 5 | 52 | 91.23% |
| `com.iflytek.skillhub.auth.merge.AccountMergeRequest` | 5 | 18 | 78.26% |
| `com.iflytek.skillhub.auth.config.SecurityConfig` | 4 | 116 | 96.67% |
| `com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver` | 3 | 7 | 70.00% |
| `com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler` | 2 | 8 | 80.00% |
| `com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler` | 2 | 14 | 87.50% |
| `com.iflytek.skillhub.auth.device.DeviceCodeData` | 1 | 12 | 92.31% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.GitHubEmail` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.new ParameterizedTypeReference() {...}` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport` | 1 | 7 | 87.50% |
| `com.iflytek.skillhub.auth.uass.UassProperties` | 1 | 43 | 97.73% |

## skillhub-domain

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy` | 29 | 58 | 66.67% |
| `com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser` | 14 | 49 | 77.78% |
| `com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator` | 14 | 53 | 79.10% |
| `com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator` | 12 | 32 | 72.73% |
| `com.iflytek.skillhub.domain.namespace.NamespaceService` | 11 | 44 | 80.00% |
| `com.iflytek.skillhub.domain.social.SkillRating` | 11 | 11 | 50.00% |
| `com.iflytek.skillhub.domain.user.ProfileReviewService` | 11 | 37 | 77.08% |
| `com.iflytek.skillhub.domain.label.LabelPermissionChecker` | 10 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.report.SkillReport` | 7 | 24 | 77.42% |
| `com.iflytek.skillhub.domain.skill.service.SkillTagService` | 5 | 46 | 90.20% |
| `com.iflytek.skillhub.domain.audit.AuditLogQueryService` | 4 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.namespace.NamespaceMemberService` | 4 | 48 | 92.31% |
| `com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService` | 4 | 21 | 84.00% |
| `com.iflytek.skillhub.domain.label.LabelSlugValidator` | 3 | 12 | 80.00% |
| `com.iflytek.skillhub.domain.review.ReviewPermissionChecker` | 3 | 37 | 92.50% |
| `com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService` | 3 | 72 | 96.00% |
| `com.iflytek.skillhub.domain.user.UserProfileService` | 3 | 60 | 95.24% |
| `com.iflytek.skillhub.domain.security.SecurityScanService` | 2 | 88 | 97.78% |
| `com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService` | 2 | 46 | 95.83% |
| `com.iflytek.skillhub.domain.skill.validation.NoOpPrePublishValidator` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.governance.GovernanceNotificationService` | 1 | 15 | 93.75% |
| `com.iflytek.skillhub.domain.namespace.SlugValidator` | 1 | 25 | 96.15% |
| `com.iflytek.skillhub.domain.user.ModerationResult` | 1 | 3 | 75.00% |
| `com.iflytek.skillhub.domain.user.UpdateProfileResult` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.domain.user.UpdateProfileResult.Mixed` | 1 | 0 | 0.00% |

## skillhub-infra

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.infra.jpa.NotificationJpaRepository` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillJpaRepository` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.scanner.SkillScannerAdapter` | 2 | 57 | 96.61% |
| `com.iflytek.skillhub.infra.scanner.SkillScannerService` | 2 | 62 | 96.88% |
| `com.iflytek.skillhub.infra.jpa.SecurityAuditJpaRepository` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillReportJpaRepository` | 1 | 0 | 0.00% |

## skillhub-notification

（本模块所有生产类 line_missed = 0）


## skillhub-search

（本模块所有生产类 line_missed = 0）


## skillhub-storage

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.storage.S3StorageService` | 62 | 68 | 52.31% |
| `com.iflytek.skillhub.storage.S3StorageProperties` | 9 | 27 | 75.00% |
| `com.iflytek.skillhub.storage.StorageProperties` | 3 | 4 | 57.14% |
| `com.iflytek.skillhub.storage.LocalFileStorageService` | 2 | 27 | 93.10% |
| `com.iflytek.skillhub.storage.StorageAccessException` | 2 | 4 | 66.67% |
