# Java Unit Test Line Coverage Inventory

当前清单基于 `server/*/target/site/jacoco/jacoco.csv` 生成。

说明：
- 仅列出 `line_missed > 0` 的类
- 指标为 Java 生产代码的 JaCoCo line coverage
- 按模块分组，便于后续拆批补测

## skillhub-app

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.bootstrap.BootstrapAdminInitializer` | 3 | 47 | 94.00% |
| `com.iflytek.skillhub.bootstrap.LocalDevDataInitializer` | 30 | 56 | 65.12% |
| `com.iflytek.skillhub.compat.ClawHubCompatAppService` | 86 | 87 | 50.29% |
| `com.iflytek.skillhub.compat.ClawHubCompatController` | 9 | 15 | 62.50% |
| `com.iflytek.skillhub.compat.ClawHubRegistryFacade` | 53 | 31 | 36.90% |
| `com.iflytek.skillhub.compat.CompatSkillLookupService` | 13 | 19 | 59.38% |
| `com.iflytek.skillhub.compat.dto.ClawHubDeleteResponse` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubRegistryModeration` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubRegistryOwner` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubRegistrySkillResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubRegistrySkillVersion` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubRegistrySkill` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse.SkillListItem.LatestVersion` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse.SkillListItem` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubSkillResponse.OwnerInfo` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubSkillResponse.VersionInfo` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubStarResponse` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.compat.dto.ClawHubUnstarResponse` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.config.ProfileModerationProperties` | 1 | 1 | 50.00% |
| `com.iflytek.skillhub.config.RedisStreamConfig` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.config.RedissonConfig` | 3 | 43 | 93.48% |
| `com.iflytek.skillhub.config.SkillScannerConfig` | 43 | 3 | 6.52% |
| `com.iflytek.skillhub.config.SkillScannerProperties.Analyzers` | 6 | 31 | 83.78% |
| `com.iflytek.skillhub.config.SkillScannerProperties.Policy` | 3 | 10 | 76.92% |
| `com.iflytek.skillhub.config.SkillScannerProperties` | 5 | 36 | 87.80% |
| `com.iflytek.skillhub.controller.AccountMergeController` | 3 | 19 | 86.36% |
| `com.iflytek.skillhub.controller.AuthController` | 7 | 51 | 87.93% |
| `com.iflytek.skillhub.controller.DeviceAuthController.TokenRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.DeviceAuthController` | 5 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.DeviceAuthWebController.AuthorizeRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.DeviceAuthWebController` | 12 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.LocalAuthController` | 4 | 38 | 90.48% |
| `com.iflytek.skillhub.controller.TokenController` | 4 | 34 | 89.47% |
| `com.iflytek.skillhub.controller.UserProfileController` | 12 | 70 | 85.37% |
| `com.iflytek.skillhub.controller.admin.AdminLabelController` | 2 | 10 | 83.33% |
| `com.iflytek.skillhub.controller.admin.AdminProfileReviewController` | 1 | 27 | 96.43% |
| `com.iflytek.skillhub.controller.admin.AdminSkillController` | 5 | 15 | 75.00% |
| `com.iflytek.skillhub.controller.admin.AdminSkillReportController` | 6 | 16 | 72.73% |
| `com.iflytek.skillhub.controller.admin.UserManagementController` | 1 | 14 | 93.33% |
| `com.iflytek.skillhub.controller.portal.MeController` | 2 | 8 | 80.00% |
| `com.iflytek.skillhub.controller.portal.NamespaceController` | 9 | 28 | 75.68% |
| `com.iflytek.skillhub.controller.portal.NotificationController` | 13 | 46 | 77.97% |
| `com.iflytek.skillhub.controller.portal.NotificationPreferenceController` | 4 | 26 | 86.67% |
| `com.iflytek.skillhub.controller.portal.PromotionController` | 5 | 13 | 72.22% |
| `com.iflytek.skillhub.controller.portal.ReviewController` | 26 | 27 | 50.94% |
| `com.iflytek.skillhub.controller.portal.SecurityAuditController` | 11 | 43 | 79.63% |
| `com.iflytek.skillhub.controller.portal.SkillController` | 32 | 109 | 77.30% |
| `com.iflytek.skillhub.controller.portal.SkillLabelController` | 8 | 5 | 38.46% |
| `com.iflytek.skillhub.controller.portal.SkillLifecycleController` | 9 | 18 | 66.67% |
| `com.iflytek.skillhub.controller.portal.SkillPublishController` | 2 | 20 | 90.91% |
| `com.iflytek.skillhub.controller.portal.SkillRatingController` | 1 | 10 | 90.91% |
| `com.iflytek.skillhub.controller.portal.SkillSearchController` | 2 | 20 | 90.91% |
| `com.iflytek.skillhub.controller.portal.SkillStarController` | 1 | 10 | 90.91% |
| `com.iflytek.skillhub.controller.portal.SkillTagController` | 5 | 9 | 64.29% |
| `com.iflytek.skillhub.controller.support.MultipartPackageExtractor.PublishPayload.ForkOf` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.controller.support.MultipartPackageExtractor` | 13 | 32 | 71.11% |
| `com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor` | 20 | 56 | 73.68% |
| `com.iflytek.skillhub.controller.support.ZipPackageExtractor` | 72 | 3 | 4.00% |
| `com.iflytek.skillhub.dto.ApiResponseFactory` | 1 | 8 | 88.89% |
| `com.iflytek.skillhub.dto.CliWhoamiResponse` | 8 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.ConfirmPublishRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.MemberResponse` | 7 | 10 | 58.82% |
| `com.iflytek.skillhub.dto.SkillCheckResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.SubmitReviewRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.dto.TagRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.exception.GlobalExceptionHandler` | 24 | 44 | 64.71% |
| `com.iflytek.skillhub.filter.AuthContextFilter` | 3 | 50 | 94.34% |
| `com.iflytek.skillhub.filter.IdempotencyInterceptor` | 12 | 50 | 80.65% |
| `com.iflytek.skillhub.filter.RequestLoggingFilter` | 3 | 45 | 93.75% |
| `com.iflytek.skillhub.listener.NotificationEventListener` | 39 | 94 | 70.68% |
| `com.iflytek.skillhub.metrics.SkillHubMetrics` | 5 | 19 | 79.17% |
| `com.iflytek.skillhub.ratelimit.AnonymousDownloadIdentityService` | 23 | 38 | 62.30% |
| `com.iflytek.skillhub.ratelimit.ClientIpResolver` | 7 | 18 | 72.00% |
| `com.iflytek.skillhub.ratelimit.InMemorySlidingWindowRateLimiter` | 3 | 15 | 83.33% |
| `com.iflytek.skillhub.ratelimit.RateLimitInterceptor` | 2 | 60 | 96.77% |
| `com.iflytek.skillhub.ratelimit.RedisSlidingWindowRateLimiter` | 15 | 0 | 0.00% |
| `com.iflytek.skillhub.repository.AdminUserSearchRepository` | 28 | 3 | 9.68% |
| `com.iflytek.skillhub.repository.JpaAdminSkillReportQueryRepository` | 2 | 31 | 93.94% |
| `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository` | 12 | 150 | 92.59% |
| `com.iflytek.skillhub.repository.JpaMySkillQueryRepository` | 3 | 51 | 94.44% |
| `com.iflytek.skillhub.repository.JpaProfileReviewQueryRepository` | 2 | 38 | 95.00% |
| `com.iflytek.skillhub.security.AuthFailureThrottleService` | 84 | 6 | 6.67% |
| `com.iflytek.skillhub.security.SensitiveLogSanitizer` | 2 | 16 | 88.89% |
| `com.iflytek.skillhub.service.AdminAuditLogAppService` | 21 | 37 | 63.79% |
| `com.iflytek.skillhub.service.AdminProfileReviewAppService` | 3 | 12 | 80.00% |
| `com.iflytek.skillhub.service.AdminSkillReportAppService` | 3 | 10 | 76.92% |
| `com.iflytek.skillhub.service.AdminUserAppService` | 2 | 66 | 97.06% |
| `com.iflytek.skillhub.service.GovernanceWorkbenchAppService` | 1 | 70 | 98.59% |
| `com.iflytek.skillhub.service.GovernanceWorkflowAppService` | 15 | 24 | 61.54% |
| `com.iflytek.skillhub.service.LabelAdminAppService.new TransactionSynchronization() {...}` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.service.LabelAdminAppService` | 5 | 72 | 93.51% |
| `com.iflytek.skillhub.service.LabelLocalizationService` | 17 | 1 | 5.56% |
| `com.iflytek.skillhub.service.LabelSearchSyncListener` | 8 | 4 | 33.33% |
| `com.iflytek.skillhub.service.LabelSearchSyncRequestedEvent` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.service.LabelSearchSyncService` | 6 | 15 | 71.43% |
| `com.iflytek.skillhub.service.MySkillAppService` | 9 | 53 | 85.48% |
| `com.iflytek.skillhub.service.NamespaceMemberCandidateService` | 4 | 29 | 87.88% |
| `com.iflytek.skillhub.service.NamespacePortalCommandAppService` | 9 | 70 | 88.61% |
| `com.iflytek.skillhub.service.NamespacePortalQueryAppService` | 7 | 43 | 86.00% |
| `com.iflytek.skillhub.service.NoOpProfileModerationService` | 1 | 1 | 50.00% |
| `com.iflytek.skillhub.service.PromotionPortalAppService` | 20 | 34 | 62.96% |
| `com.iflytek.skillhub.service.PublicLabelAppService` | 12 | 4 | 25.00% |
| `com.iflytek.skillhub.service.ReviewPortalAppService` | 32 | 71 | 68.93% |
| `com.iflytek.skillhub.service.ReviewSkillDetailAppService` | 3 | 69 | 95.83% |
| `com.iflytek.skillhub.service.SkillDeleteAppService` | 6 | 53 | 89.83% |
| `com.iflytek.skillhub.service.SkillLabelAppService.new TransactionSynchronization() {...}` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.service.SkillLabelAppService` | 47 | 27 | 36.49% |
| `com.iflytek.skillhub.service.SkillLifecycleAppService` | 29 | 70 | 70.71% |
| `com.iflytek.skillhub.service.SkillSearchAppService` | 5 | 82 | 94.25% |
| `com.iflytek.skillhub.stream.AbstractStreamConsumer` | 60 | 63 | 51.22% |
| `com.iflytek.skillhub.stream.ScanTaskConsumer` | 16 | 103 | 86.55% |

## skillhub-auth

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.auth.config.RedisTemplateConfig` | 11 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.device.DeviceAuthService` | 57 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.device.DeviceCodeData` | 13 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.device.DeviceCodeResponse` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.device.DeviceCodeStatus` | 4 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.device.DeviceTokenResponse` | 3 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.entity.IdentityBinding` | 17 | 8 | 32.00% |
| `com.iflytek.skillhub.auth.local.LocalAuthService` | 16 | 86 | 84.31% |
| `com.iflytek.skillhub.auth.local.PasswordResetService` | 17 | 97 | 85.09% |
| `com.iflytek.skillhub.auth.merge.AccountMergeRequest` | 6 | 17 | 73.91% |
| `com.iflytek.skillhub.auth.mock.MockAuthFilter` | 22 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.GitHubEmail` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.new ParameterizedTypeReference() {...}` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor` | 27 | 0 | 0.00% |
| `com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor` | 8 | 43 | 84.31% |
| `com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler` | 2 | 8 | 80.00% |
| `com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler` | 2 | 14 | 87.50% |
| `com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport` | 2 | 6 | 75.00% |
| `com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver` | 3 | 7 | 70.00% |
| `com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry.RouteAuthorizationPolicy` | 3 | 4 | 57.14% |
| `com.iflytek.skillhub.auth.rbac.RbacService` | 22 | 0 | 0.00% |

## skillhub-domain

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.domain.audit.AuditLogQueryService` | 4 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.audit.AuditLog` | 8 | 14 | 63.64% |
| `com.iflytek.skillhub.domain.auth.PasswordResetRequest` | 24 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.governance.GovernanceNotificationService` | 1 | 15 | 93.75% |
| `com.iflytek.skillhub.domain.governance.UserNotification` | 7 | 18 | 72.00% |
| `com.iflytek.skillhub.domain.idempotency.IdempotencyRecord` | 22 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.idempotency.IdempotencyStatus` | 4 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.label.LabelDefinition` | 14 | 16 | 53.33% |
| `com.iflytek.skillhub.domain.label.LabelPermissionChecker` | 10 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.label.LabelSlugValidator` | 3 | 12 | 80.00% |
| `com.iflytek.skillhub.domain.label.LabelTranslation` | 12 | 7 | 36.84% |
| `com.iflytek.skillhub.domain.label.SkillLabel` | 14 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.namespace.NamespaceMemberService` | 4 | 48 | 92.31% |
| `com.iflytek.skillhub.domain.namespace.NamespaceMember` | 10 | 10 | 50.00% |
| `com.iflytek.skillhub.domain.namespace.NamespaceService` | 11 | 44 | 80.00% |
| `com.iflytek.skillhub.domain.namespace.Namespace` | 10 | 18 | 64.29% |
| `com.iflytek.skillhub.domain.namespace.SlugValidator` | 3 | 23 | 88.46% |
| `com.iflytek.skillhub.domain.report.SkillReport` | 8 | 23 | 74.19% |
| `com.iflytek.skillhub.domain.review.PromotionRequest` | 2 | 30 | 93.75% |
| `com.iflytek.skillhub.domain.review.ReviewPermissionChecker` | 3 | 37 | 92.50% |
| `com.iflytek.skillhub.domain.review.ReviewTask` | 2 | 25 | 92.59% |
| `com.iflytek.skillhub.domain.security.ScannerType` | 4 | 7 | 63.64% |
| `com.iflytek.skillhub.domain.security.SecurityAudit` | 12 | 36 | 75.00% |
| `com.iflytek.skillhub.domain.security.SecurityScanRequest` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.domain.skill.SkillFile` | 7 | 13 | 65.00% |
| `com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation` | 10 | 23 | 69.70% |
| `com.iflytek.skillhub.domain.skill.SkillTag` | 14 | 8 | 36.36% |
| `com.iflytek.skillhub.domain.skill.SkillVersionStats` | 16 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.skill.SkillVersion` | 7 | 51 | 87.93% |
| `com.iflytek.skillhub.domain.skill.Skill` | 8 | 57 | 87.69% |
| `com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser` | 14 | 49 | 77.78% |
| `com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService` | 3 | 72 | 96.00% |
| `com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService` | 2 | 46 | 95.83% |
| `com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService` | 4 | 21 | 84.00% |
| `com.iflytek.skillhub.domain.skill.service.SkillTagService` | 5 | 46 | 90.20% |
| `com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator` | 12 | 32 | 72.73% |
| `com.iflytek.skillhub.domain.skill.validation.NoOpPrePublishValidator` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy` | 29 | 58 | 66.67% |
| `com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator` | 14 | 53 | 79.10% |
| `com.iflytek.skillhub.domain.skill.validation.ValidationResult` | 3 | 6 | 66.67% |
| `com.iflytek.skillhub.domain.social.SkillRating` | 11 | 11 | 50.00% |
| `com.iflytek.skillhub.domain.social.SkillStar` | 7 | 4 | 36.36% |
| `com.iflytek.skillhub.domain.user.ModerationResult` | 1 | 3 | 75.00% |
| `com.iflytek.skillhub.domain.user.ProfileChangeRequest` | 7 | 20 | 74.07% |
| `com.iflytek.skillhub.domain.user.ProfileReviewService` | 11 | 37 | 77.08% |
| `com.iflytek.skillhub.domain.user.UpdateProfileResult.Mixed` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.domain.user.UpdateProfileResult` | 1 | 2 | 66.67% |
| `com.iflytek.skillhub.domain.user.UserAccount` | 18 | 10 | 35.71% |
| `com.iflytek.skillhub.domain.user.UserProfileService` | 3 | 60 | 95.24% |

## skillhub-infra

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.infra.jpa.NotificationJpaRepository` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SecurityAuditJpaRepository` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillJpaRepository` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillReportJpaRepository` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository` | 2 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.jpa.SkillVersionStatsJpaRepository` | 1 | 0 | 0.00% |
| `com.iflytek.skillhub.infra.scanner.SkillScannerAdapter` | 7 | 52 | 88.14% |
| `com.iflytek.skillhub.infra.scanner.SkillScannerService` | 6 | 58 | 90.62% |

## skillhub-notification

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|

## skillhub-search

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.search.h2.H2LikeSearchQueryService` | 23 | 60 | 72.29% |
| `com.iflytek.skillhub.search.postgres.PostgresFullTextIndexService` | 19 | 42 | 68.85% |
| `com.iflytek.skillhub.search.postgres.PostgresFullTextQueryService` | 13 | 168 | 92.82% |
| `com.iflytek.skillhub.search.postgres.PostgresSearchRebuildService` | 27 | 118 | 81.38% |

## skillhub-storage

| Class | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `com.iflytek.skillhub.storage.LocalFileStorageService` | 2 | 27 | 93.10% |
| `com.iflytek.skillhub.storage.S3StorageProperties` | 9 | 27 | 75.00% |
| `com.iflytek.skillhub.storage.S3StorageService` | 62 | 68 | 52.31% |
| `com.iflytek.skillhub.storage.StorageAccessException` | 2 | 4 | 66.67% |
| `com.iflytek.skillhub.storage.StorageProperties` | 3 | 4 | 57.14% |
