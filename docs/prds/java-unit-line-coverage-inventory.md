# Java Unit Test Line Coverage Inventory

当前清单基于 `server/*/target/site/jacoco/jacoco.csv` 生成。

说明：
- 仅列出 `line_missed > 0` 的类
- 指标为 Java 生产代码的 JaCoCo line coverage
- 按模块分组，便于后续拆批补测
- 报告命令：`mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`
- 聚合报告路径：`server/skillhub-app/target/site/jacoco-aggregate/index.html`
- 当前后端多模块 aggregate line coverage 为 `91.91%`（`10971/11937`）

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
| `com.iflytek.skillhub.controller.portal.SkillController` | 135 | 6 | 4.26% |
| `com.iflytek.skillhub.service.SkillLifecycleAppService` | 75 | 24 | 24.24% |
| `com.iflytek.skillhub.controller.portal.ReviewController` | 50 | 3 | 5.66% |
| `com.iflytek.skillhub.controller.portal.SecurityAuditController` | 47 | 7 | 12.96% |
| `com.iflytek.skillhub.service.ReviewPortalAppService` | 39 | 64 | 62.14% |
| `com.iflytek.skillhub.service.PromotionPortalAppService` | 33 | 21 | 38.89% |
| `com.iflytek.skillhub.controller.portal.NamespaceController` | 31 | 6 | 16.22% |
| `com.iflytek.skillhub.controller.UassAuthController` | 29 | 0 | 0.00% |
| `com.iflytek.skillhub.repository.AdminUserSearchRepository` | 29 | 3 | 9.38% |
| `com.iflytek.skillhub.service.GovernanceWorkflowAppService` | 29 | 10 | 25.64% |
| `com.iflytek.skillhub.controller.portal.SkillLifecycleController` | 24 | 3 | 11.11% |
| `com.iflytek.skillhub.controller.LocalAuthController` | 23 | 19 | 45.24% |
| `com.iflytek.skillhub.compat.ClawHubCompatController` | 21 | 3 | 12.50% |
| `com.iflytek.skillhub.service.AuthMethodCatalog` | 20 | 36 | 64.29% |
| `com.iflytek.skillhub.controller.TokenController` | 19 | 19 | 50.00% |
| `com.iflytek.skillhub.controller.portal.SkillSearchController` | 19 | 5 | 20.83% |
| `com.iflytek.skillhub.controller.admin.AdminSkillReportController` | 18 | 4 | 18.18% |
| `com.iflytek.skillhub.controller.admin.AdminSkillController` | 17 | 3 | 15.00% |
| `com.iflytek.skillhub.controller.portal.SkillPublishController` | 17 | 5 | 22.73% |
| `com.iflytek.skillhub.controller.admin.AdminProfileReviewController` | 16 | 12 | 42.86% |
| `com.iflytek.skillhub.controller.portal.SkillDeleteController` | 16 | 3 | 15.79% |
| `com.iflytek.skillhub.controller.portal.PromotionController` | 15 | 3 | 16.67% |
| `com.iflytek.skillhub.controller.portal.SkillLifecycleDeleteController` | 14 | 3 | 17.65% |
| `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository` | 14 | 148 | 91.36% |
| `com.iflytek.skillhub.controller.AccountMergeController` | 13 | 9 | 40.91% |
| `com.iflytek.skillhub.controller.portal.SkillReportController` | 13 | 5 | 27.78% |
| `com.iflytek.skillhub.controller.portal.SkillTagController` | 11 | 3 | 21.43% |
| `com.iflytek.skillhub.ratelimit.RateLimitInterceptor` | 11 | 51 | 82.26% |
| `com.iflytek.skillhub.controller.portal.SkillLabelController` | 10 | 3 | 23.08% |
| `com.iflytek.skillhub.security.ApiAccessDeniedHandler` | 10 | 6 | 37.50% |
| `com.iflytek.skillhub.controller.admin.AdminLabelController` | 9 | 3 | 25.00% |
| `com.iflytek.skillhub.controller.admin.UserManagementController` | 9 | 6 | 40.00% |
| `com.iflytek.skillhub.controller.portal.SkillRatingController` | 8 | 3 | 27.27% |
| `com.iflytek.skillhub.controller.admin.AdminSearchController` | 7 | 4 | 36.36% |
| `com.iflytek.skillhub.dto.TagResponse` | 6 | 0 | 0.00% |
| `com.iflytek.skillhub.config.ProfileFieldPolicyProperties` | 4 | 5 | 55.56% |
| `com.iflytek.skillhub.controller.AuthController` | 3 | 55 | 94.83% |
| `com.iflytek.skillhub.listener.NotificationEventListener` | 3 | 130 | 97.74% |
| `com.iflytek.skillhub.service.AuditRequestContext` | 3 | 1 | 25.00% |
| `com.iflytek.skillhub.config.ProfileFieldPolicyProperties.FieldEntry` | 2 | 3 | 60.00% |
| `com.iflytek.skillhub.compat.ClawHubCompatAppService` | 1 | 172 | 99.42% |
| `com.iflytek.skillhub.compat.dto.ClawHubSkillResponse.OwnerInfo` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.admin.AuditLogController` | 1 | 3 | 75.00% |
| `com.iflytek.skillhub.controller.portal.LabelController` | 1 | 3 | 75.00% |
| `com.iflytek.skillhub.controller.portal.SecurityAuditController.new TypeReference() {...}` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.AdminSkillMutationResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.AdminSkillReportActionRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.AdminUserRoleUpdateRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.AdminUserStatusUpdateRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.AuthProviderResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.BatchMemberRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ChangePasswordRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.LocalRegisterRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.MergeInitiateResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.PasswordResetConfirmRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.PasswordResetRequestDto` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ProfileReviewMutationResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ProfileReviewRejectRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.PromotionActionRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.PromotionRequestDto` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.PublishResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ResolveVersionResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ReviewActionRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ReviewTaskRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SecurityAuditResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillDeleteResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillRatingRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillRatingStatusResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillReportMutationResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillReportSubmitRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillVersionDetailResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SkillVersionRereleaseRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.TokenExpirationUpdateRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.TokenSummaryResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.UassLoginStatusResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.UassLoginUrlResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.exception.ForbiddenException` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.exception.UnauthorizedException` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.service.DirectAuthService` | 1 | 14 | 93.33% |
| `com.iflytek.skillhub.service.SessionBootstrapService` | 1 | 17 | 94.44% |

## skillhub-auth

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor` | 27 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.mock.MockAuthFilter` | 17 | 5 | 22.73% |
| `com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor` | 8 | 43 | 84.31% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.GitHubEmail` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.new ParameterizedTypeReference() {...}` | 1 | 0 | 0.00% |

## skillhub-domain

（本模块所有生产类 line_missed = 0）


## skillhub-infra

（本模块所有生产类 line_missed = 0）


## skillhub-notification

（本模块所有生产类 line_missed = 0）


## skillhub-search

（本模块所有生产类 line_missed = 0）


## skillhub-storage

（本模块所有生产类 line_missed = 0）

