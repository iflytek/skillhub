# MySQL Main Path Coverage Baseline
Generated: 2026-05-04

## Overall Line Coverage
| Metric | Value |
|--------|-------|
| Total Lines | 11945 |
| Covered Lines | 9756 |
| Missed Lines | 2093 |
| Line Coverage | 81.67% |

## Per-Module Line Coverage
| Module | Total Lines | Covered | Missed | Coverage |
|--------|-------------|---------|--------|----------|
| skillhub-domain | 3312 | 2595 | 717 | 78.35% |
| skillhub-auth | 2083 | 1718 | 365 | 82.48% |
| skillhub-infra | 308 | 136 | 172 | 44.16% |
| skillhub-search | 713 | 588 | 125 | 82.47% |
| skillhub-notification | 207 | 190 | 17 | 91.79% |
| skillhub-storage | 213 | 135 | 78 | 63.38% |
| skillhub-app | 5109 | 4511 | 719 | 88.30% |

## Uncovered Classes Inventory (by module, sorted by missed lines desc)
### skillhub-domain
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| SkillPackagePolicy | com.iflytek.skillhub.domain.skill.validation | 29 | 58 |
| UserAccount | com.iflytek.skillhub.domain.user | 20 | 10 |
| SkillPackageValidator | com.iflytek.skillhub.domain.skill.validation | 14 | 53 |
| SkillMetadataParser | com.iflytek.skillhub.domain.skill.metadata | 14 | 49 |
| BasicPrePublishValidator | com.iflytek.skillhub.domain.skill.validation | 12 | 32 |
| NamespaceService | com.iflytek.skillhub.domain.namespace | 11 | 44 |
| ProfileReviewService | com.iflytek.skillhub.domain.user | 11 | 37 |
| SkillRating | com.iflytek.skillhub.domain.social | 11 | 11 |
| LabelPermissionChecker | com.iflytek.skillhub.domain.label | 10 | 0 |
| SkillReport | com.iflytek.skillhub.domain.report | 8 | 23 |
| SkillStar | com.iflytek.skillhub.domain.social | 7 | 4 |
| SkillTagService | com.iflytek.skillhub.domain.skill.service | 5 | 46 |
| SkillStorageDeletionCompensationService | com.iflytek.skillhub.domain.skill.service | 4 | 21 |
| AuditLogQueryService | com.iflytek.skillhub.domain.audit | 4 | 0 |
| NamespaceMemberService | com.iflytek.skillhub.domain.namespace | 4 | 48 |
| ValidationResult | com.iflytek.skillhub.domain.skill.validation | 3 | 6 |
| SkillHardDeleteService | com.iflytek.skillhub.domain.skill.service | 3 | 72 |
| SlugValidator | com.iflytek.skillhub.domain.namespace | 3 | 23 |
| ReviewPermissionChecker | com.iflytek.skillhub.domain.review | 3 | 37 |
| UserProfileService | com.iflytek.skillhub.domain.user | 3 | 60 |
| LabelSlugValidator | com.iflytek.skillhub.domain.label | 3 | 12 |
| NoOpPrePublishValidator | com.iflytek.skillhub.domain.skill.validation | 2 | 0 |
| SkillReviewSubmitService | com.iflytek.skillhub.domain.skill.service | 2 | 46 |
| UpdateProfileResult | com.iflytek.skillhub.domain.user | 1 | 2 |
| ModerationResult | com.iflytek.skillhub.domain.user | 1 | 3 |
| UpdateProfileResult.Mixed | com.iflytek.skillhub.domain.user | 1 | 0 |
| GovernanceNotificationService | com.iflytek.skillhub.domain.governance | 1 | 15 |
### skillhub-auth
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| GitHubClaimsExtractor | com.iflytek.skillhub.auth.oauth | 27 | 0 |
| MockAuthFilter | com.iflytek.skillhub.auth.mock | 22 | 0 |
| RbacService | com.iflytek.skillhub.auth.rbac | 22 | 0 |
| PasswordResetService | com.iflytek.skillhub.auth.local | 17 | 97 |
| LocalAuthService | com.iflytek.skillhub.auth.local | 16 | 86 |
| GitLabClaimsExtractor | com.iflytek.skillhub.auth.oauth | 8 | 43 |
| AccountMergeRequest | com.iflytek.skillhub.auth.merge | 6 | 17 |
| DeviceAuthService | com.iflytek.skillhub.auth.device | 5 | 52 |
| SkillHubOAuth2AuthorizationRequestResolver | com.iflytek.skillhub.auth.oauth | 3 | 7 |
| OAuth2LoginSuccessHandler | com.iflytek.skillhub.auth.oauth | 2 | 14 |
| OAuthLoginRedirectSupport | com.iflytek.skillhub.auth.oauth | 2 | 6 |
| OAuth2LoginFailureHandler | com.iflytek.skillhub.auth.oauth | 2 | 8 |
| GitHubClaimsExtractor.GitHubEmail | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| GitHubClaimsExtractor.new ParameterizedTypeReference() {...} | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| DeviceCodeData | com.iflytek.skillhub.auth.device | 1 | 12 |
| UassProperties | com.iflytek.skillhub.auth.uass | 1 | 43 |
### skillhub-infra
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| SkillScannerAdapter | com.iflytek.skillhub.infra.scanner | 7 | 52 |
| SkillScannerService | com.iflytek.skillhub.infra.scanner | 6 | 58 |
| NotificationJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillVersionJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillReportJpaRepository | com.iflytek.skillhub.infra.jpa | 1 | 0 |
| SecurityAuditJpaRepository | com.iflytek.skillhub.infra.jpa | 1 | 0 |
### skillhub-search
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
### skillhub-notification
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
### skillhub-storage
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| S3StorageService | com.iflytek.skillhub.storage | 62 | 68 |
| S3StorageProperties | com.iflytek.skillhub.storage | 9 | 27 |
| StorageProperties | com.iflytek.skillhub.storage | 3 | 4 |
| LocalFileStorageService | com.iflytek.skillhub.storage | 2 | 27 |
| StorageAccessException | com.iflytek.skillhub.storage | 2 | 4 |
### skillhub-app
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| AbstractStreamConsumer | com.iflytek.skillhub.stream | 55 | 68 |
| SkillScannerConfig | com.iflytek.skillhub.config | 43 | 3 |
| NotificationEventListener | com.iflytek.skillhub.listener | 39 | 94 |
| GlobalExceptionHandler | com.iflytek.skillhub.exception | 30 | 44 |
| AdminUserSearchRepository | com.iflytek.skillhub.repository | 29 | 3 |
| AnonymousDownloadIdentityService | com.iflytek.skillhub.ratelimit | 23 | 38 |
| ZipPackageExtractor | com.iflytek.skillhub.controller.support | 21 | 54 |
| SkillPackageArchiveExtractor | com.iflytek.skillhub.controller.support | 20 | 56 |
| MockUassController | com.iflytek.skillhub.controller | 17 | 3 |
| ScanTaskConsumer | com.iflytek.skillhub.stream | 16 | 103 |
| MultipartPackageExtractor | com.iflytek.skillhub.controller.support | 13 | 32 |
| MultipartPackageExtractor | com.iflytek.skillhub.controller.support | 13 | 32 |
| JpaGovernanceQueryRepository | com.iflytek.skillhub.repository | 12 | 150 |
| DeviceAuthWebController | com.iflytek.skillhub.controller | 12 | 0 |
| UserProfileController | com.iflytek.skillhub.controller | 12 | 71 |
| IdempotencyInterceptor | com.iflytek.skillhub.filter | 12 | 50 |
| CliWhoamiResponse | com.iflytek.skillhub.dto | 8 | 0 |
| CliWhoamiResponse | com.iflytek.skillhub.dto | 8 | 0 |
| AuthController | com.iflytek.skillhub.controller | 7 | 51 |
| AuthController | com.iflytek.skillhub.controller | 7 | 51 |
| AuthFailureThrottleService | com.iflytek.skillhub.security | 7 | 83 |
| ClientIpResolver | com.iflytek.skillhub.ratelimit | 7 | 18 |
| MemberResponse | com.iflytek.skillhub.dto | 7 | 10 |
| SkillScannerProperties.Analyzers | com.iflytek.skillhub.config | 6 | 31 |
| SkillDeleteAppService | com.iflytek.skillhub.service | 6 | 53 |
| SkillScannerProperties | com.iflytek.skillhub.config | 5 | 36 |
| DeviceAuthController | com.iflytek.skillhub.controller | 5 | 0 |
| LocalFileIndexStartupSynchronizer | com.iflytek.skillhub.bootstrap | 5 | 24 |
| SkillHubMetrics | com.iflytek.skillhub.metrics | 5 | 19 |
| LabelAdminAppService | com.iflytek.skillhub.service | 5 | 72 |
| TokenController | com.iflytek.skillhub.controller | 4 | 34 |
| TokenController | com.iflytek.skillhub.controller | 4 | 34 |
| LocalAuthController | com.iflytek.skillhub.controller | 4 | 38 |
| NamespaceMemberCandidateService | com.iflytek.skillhub.service | 4 | 29 |
| JpaMySkillQueryRepository | com.iflytek.skillhub.repository | 3 | 51 |
| SkillScannerProperties.Policy | com.iflytek.skillhub.config | 3 | 10 |
| RedisStreamConfig | com.iflytek.skillhub.config | 3 | 0 |
| RedissonConfig | com.iflytek.skillhub.config | 3 | 43 |
| AccountMergeController | com.iflytek.skillhub.controller | 3 | 19 |
| InMemorySlidingWindowRateLimiter | com.iflytek.skillhub.ratelimit | 3 | 15 |
| LabelAdminAppService.new TransactionSynchronization() {...} | com.iflytek.skillhub.service | 3 | 0 |
| ReviewSkillDetailAppService | com.iflytek.skillhub.service | 3 | 69 |
| AdminSkillReportAppService | com.iflytek.skillhub.service | 3 | 10 |
| AdminProfileReviewAppService | com.iflytek.skillhub.service | 3 | 12 |
| RequestLoggingFilter | com.iflytek.skillhub.filter | 3 | 45 |
| AuthContextFilter | com.iflytek.skillhub.filter | 3 | 50 |
| JpaProfileReviewQueryRepository | com.iflytek.skillhub.repository | 2 | 38 |
| JpaAdminSkillReportQueryRepository | com.iflytek.skillhub.repository | 2 | 31 |
| RuntimeStateEnvironmentPostProcessor | com.iflytek.skillhub.config | 2 | 9 |
| SkillhubApplication | com.iflytek.skillhub | 2 | 1 |
| SensitiveLogSanitizer | com.iflytek.skillhub.security | 2 | 16 |
| RateLimitInterceptor | com.iflytek.skillhub.ratelimit | 2 | 60 |
| AdminUserAppService | com.iflytek.skillhub.service | 2 | 67 |
| SkillPublishController | com.iflytek.skillhub.controller.portal | 2 | 20 |
| SkillSearchController | com.iflytek.skillhub.controller.portal | 2 | 22 |
| ProfileModerationProperties | com.iflytek.skillhub.config | 1 | 1 |
| DeviceAuthWebController.AuthorizeRequest | com.iflytek.skillhub.controller | 1 | 0 |
| DeviceAuthController.TokenRequest | com.iflytek.skillhub.controller | 1 | 0 |
| BaseApiController | com.iflytek.skillhub.controller | 1 | 4 |
| ApiResponseFactory | com.iflytek.skillhub.dto | 1 | 8 |
| SubmitReviewRequest | com.iflytek.skillhub.dto | 1 | 0 |
| SkillCheckResponse | com.iflytek.skillhub.dto | 1 | 0 |
| MockUassLoginRequest | com.iflytek.skillhub.dto | 1 | 0 |
| MockUassLoginResponse | com.iflytek.skillhub.dto | 1 | 0 |
| TagRequest | com.iflytek.skillhub.dto | 1 | 0 |
| ConfirmPublishRequest | com.iflytek.skillhub.dto | 1 | 0 |
| AdminProfileReviewController | com.iflytek.skillhub.controller.admin | 1 | 27 |
| UserManagementController | com.iflytek.skillhub.controller.admin | 1 | 14 |
| SkillSearchAppService | com.iflytek.skillhub.service | 1 | 86 |
| NoOpProfileModerationService | com.iflytek.skillhub.service | 1 | 1 |
| GovernanceWorkbenchAppService | com.iflytek.skillhub.service | 1 | 70 |
| MultipartPackageExtractor.PublishPayload.ForkOf | com.iflytek.skillhub.controller.support | 1 | 0 |
| SkillRatingController | com.iflytek.skillhub.controller.portal | 1 | 10 |
| SkillStarController | com.iflytek.skillhub.controller.portal | 1 | 10 |
