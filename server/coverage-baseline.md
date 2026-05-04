# MySQL Main Path Coverage Baseline
Generated: 2026-05-04

## Overall Line Coverage
| Metric | Value |
|--------|-------|
| Total Lines | 11945 |
| Covered Lines | 9473 |
| Missed Lines | 2472 |
| Line Coverage | 79.31% |

## Per-Module Line Coverage
| Module | Total Lines | Covered | Missed | Coverage |
|--------|-------------|---------|--------|----------|
| skillhub-domain | 3312 | 2595 | 717 | 78.35% |
| skillhub-auth | 2083 | 1718 | 365 | 82.48% |
| skillhub-infra | 308 | 136 | 172 | 44.16% |
| skillhub-search | 713 | 588 | 125 | 82.47% |
| skillhub-notification | 207 | 190 | 17 | 91.79% |
| skillhub-storage | 213 | 135 | 78 | 63.38% |
| skillhub-app | 5109 | 4111 | 998 | 80.47% |

## Uncovered Classes Inventory (by module, sorted by missed lines desc)
### skillhub-domain
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| SkillQueryService | com.iflytek.skillhub.domain.skill.service | 98 | 222 |
| SkillLifecycleProjectionService | com.iflytek.skillhub.domain.skill.service | 41 | 34 |
| LabelDefinitionService | com.iflytek.skillhub.domain.label | 37 | 65 |
| PromotionService | com.iflytek.skillhub.domain.review | 32 | 137 |
| SkillPackagePolicy | com.iflytek.skillhub.domain.skill.validation | 29 | 58 |
| SkillLabelService | com.iflytek.skillhub.domain.label | 28 | 7 |
| ReviewService | com.iflytek.skillhub.domain.review | 26 | 141 |
| PasswordResetRequest | com.iflytek.skillhub.domain.auth | 24 | 0 |
| IdempotencyRecord | com.iflytek.skillhub.domain.idempotency | 22 | 0 |
| SecurityScanService | com.iflytek.skillhub.domain.security | 20 | 70 |
| UserAccount | com.iflytek.skillhub.domain.user | 20 | 10 |
| SkillPublishService | com.iflytek.skillhub.domain.skill.service | 19 | 249 |
| SkillGovernanceService | com.iflytek.skillhub.domain.skill.service | 16 | 125 |
| SkillReportService | com.iflytek.skillhub.domain.report | 16 | 51 |
| NamespaceGovernanceService | com.iflytek.skillhub.domain.namespace | 15 | 42 |
| SkillPackageValidator | com.iflytek.skillhub.domain.skill.validation | 14 | 53 |
| SkillTag | com.iflytek.skillhub.domain.skill | 14 | 8 |
| SkillMetadataParser | com.iflytek.skillhub.domain.skill.metadata | 14 | 49 |
| LabelDefinition | com.iflytek.skillhub.domain.label | 14 | 16 |
| SkillLabel | com.iflytek.skillhub.domain.label | 14 | 0 |
| BasicPrePublishValidator | com.iflytek.skillhub.domain.skill.validation | 12 | 32 |
| LabelTranslation | com.iflytek.skillhub.domain.label | 12 | 7 |
| NamespaceService | com.iflytek.skillhub.domain.namespace | 11 | 44 |
| ProfileReviewService | com.iflytek.skillhub.domain.user | 11 | 37 |
| SkillRating | com.iflytek.skillhub.domain.social | 11 | 11 |
| SkillDownloadService | com.iflytek.skillhub.domain.skill.service | 10 | 102 |
| SkillStorageDeletionCompensation | com.iflytek.skillhub.domain.skill | 10 | 23 |
| NamespaceMember | com.iflytek.skillhub.domain.namespace | 10 | 10 |
| Namespace | com.iflytek.skillhub.domain.namespace | 10 | 18 |
| LabelPermissionChecker | com.iflytek.skillhub.domain.label | 10 | 0 |
| Skill | com.iflytek.skillhub.domain.skill | 8 | 57 |
| SkillReport | com.iflytek.skillhub.domain.report | 8 | 23 |
| SkillFile | com.iflytek.skillhub.domain.skill | 7 | 13 |
| SkillStar | com.iflytek.skillhub.domain.social | 7 | 4 |
| UserNotification | com.iflytek.skillhub.domain.governance | 7 | 18 |
| SkillTagService | com.iflytek.skillhub.domain.skill.service | 5 | 46 |
| SkillStorageDeletionCompensationService | com.iflytek.skillhub.domain.skill.service | 4 | 21 |
| AuditLogQueryService | com.iflytek.skillhub.domain.audit | 4 | 0 |
| NamespaceMemberService | com.iflytek.skillhub.domain.namespace | 4 | 48 |
| ScannerType | com.iflytek.skillhub.domain.security | 4 | 7 |
| IdempotencyStatus | com.iflytek.skillhub.domain.idempotency | 4 | 0 |
| ValidationResult | com.iflytek.skillhub.domain.skill.validation | 3 | 6 |
| SkillHardDeleteService | com.iflytek.skillhub.domain.skill.service | 3 | 72 |
| SlugValidator | com.iflytek.skillhub.domain.namespace | 3 | 23 |
| ReviewPermissionChecker | com.iflytek.skillhub.domain.review | 3 | 37 |
| UserProfileService | com.iflytek.skillhub.domain.user | 3 | 60 |
| LabelSlugValidator | com.iflytek.skillhub.domain.label | 3 | 12 |
| NoOpPrePublishValidator | com.iflytek.skillhub.domain.skill.validation | 2 | 0 |
| SkillReviewSubmitService | com.iflytek.skillhub.domain.skill.service | 2 | 46 |
| ReviewTask | com.iflytek.skillhub.domain.review | 2 | 25 |
| PromotionRequest | com.iflytek.skillhub.domain.review | 2 | 30 |
| SkillQueryService.ReviewSkillSnapshotDTO | com.iflytek.skillhub.domain.skill.service | 1 | 0 |
| DomainForbiddenException | com.iflytek.skillhub.domain.shared.exception | 1 | 2 |
| DomainNotFoundException | com.iflytek.skillhub.domain.shared.exception | 1 | 2 |
| DomainBadRequestException | com.iflytek.skillhub.domain.shared.exception | 1 | 2 |
| SecurityScanRequest | com.iflytek.skillhub.domain.security | 1 | 0 |
| UpdateProfileResult | com.iflytek.skillhub.domain.user | 1 | 2 |
| ModerationResult | com.iflytek.skillhub.domain.user | 1 | 3 |
| UpdateProfileResult.Mixed | com.iflytek.skillhub.domain.user | 1 | 0 |
| GovernanceNotificationService | com.iflytek.skillhub.domain.governance | 1 | 15 |
### skillhub-auth
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| AccountMergeService | com.iflytek.skillhub.auth.merge | 34 | 120 |
| GitHubClaimsExtractor | com.iflytek.skillhub.auth.oauth | 27 | 0 |
| MockAuthFilter | com.iflytek.skillhub.auth.mock | 22 | 0 |
| RbacService | com.iflytek.skillhub.auth.rbac | 22 | 0 |
| OAuthLoginFlowService | com.iflytek.skillhub.auth.oauth | 20 | 23 |
| PasswordResetService | com.iflytek.skillhub.auth.local | 17 | 97 |
| LocalAuthService | com.iflytek.skillhub.auth.local | 16 | 86 |
| CustomOAuth2UserService | com.iflytek.skillhub.auth.oauth | 13 | 0 |
| GitLabClaimsExtractor | com.iflytek.skillhub.auth.oauth | 8 | 43 |
| AccountMergeRequest | com.iflytek.skillhub.auth.merge | 6 | 17 |
| IdentityBindingService | com.iflytek.skillhub.auth.identity | 6 | 75 |
| UserRoleBinding | com.iflytek.skillhub.auth.entity | 6 | 5 |
| LocalDirectAuthProvider | com.iflytek.skillhub.auth.direct | 6 | 0 |
| Role | com.iflytek.skillhub.auth.entity | 5 | 2 |
| RolePermission.RolePermissionId | com.iflytek.skillhub.auth.entity | 5 | 0 |
| DeviceAuthService | com.iflytek.skillhub.auth.device | 5 | 52 |
| Permission | com.iflytek.skillhub.auth.entity | 4 | 0 |
| SkillHubOAuth2AuthorizationRequestResolver | com.iflytek.skillhub.auth.oauth | 3 | 7 |
| RolePermission | com.iflytek.skillhub.auth.entity | 3 | 0 |
| AuthFlowException | com.iflytek.skillhub.auth.exception | 3 | 7 |
| OAuth2LoginSuccessHandler | com.iflytek.skillhub.auth.oauth | 2 | 14 |
| OAuthLoginRedirectSupport | com.iflytek.skillhub.auth.oauth | 2 | 6 |
| OAuth2LoginFailureHandler | com.iflytek.skillhub.auth.oauth | 2 | 8 |
| GitHubClaimsExtractor.GitHubEmail | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| OAuthLoginFlowService.AuthenticatedLoginContext | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| GitHubClaimsExtractor.new ParameterizedTypeReference() {...} | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| DeviceCodeData | com.iflytek.skillhub.auth.device | 1 | 12 |
| UassProperties | com.iflytek.skillhub.auth.uass | 1 | 43 |
| DirectAuthProvider | com.iflytek.skillhub.auth.direct | 1 | 0 |
| DirectAuthRequest | com.iflytek.skillhub.auth.direct | 1 | 0 |
### skillhub-infra
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| SkillSearchDocumentEntity | com.iflytek.skillhub.infra.jpa | 50 | 0 |
| WebClientHttpClient | com.iflytek.skillhub.infra.http | 49 | 0 |
| JpaSkillRepositoryAdapter | com.iflytek.skillhub.infra.jpa | 21 | 0 |
| WebClientConfig | com.iflytek.skillhub.infra.http | 14 | 0 |
| SkillScannerAdapter | com.iflytek.skillhub.infra.scanner | 7 | 52 |
| SkillScannerService | com.iflytek.skillhub.infra.scanner | 6 | 58 |
| AuditLogJpaRepository | com.iflytek.skillhub.infra.jpa | 6 | 0 |
| JpaSkillStorageDeletionCompensationRepositoryAdapter | com.iflytek.skillhub.infra.jpa | 5 | 0 |
| HttpClientException | com.iflytek.skillhub.infra.http | 4 | 6 |
| SecurityScanException | com.iflytek.skillhub.infra.scanner | 2 | 2 |
| NotificationJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillVersionJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillReportJpaRepository | com.iflytek.skillhub.infra.jpa | 1 | 0 |
| SecurityAuditJpaRepository | com.iflytek.skillhub.infra.jpa | 1 | 0 |
### skillhub-search
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| AbstractJpaSearchRebuildService | com.iflytek.skillhub.search | 87 | 85 |
| AbstractJpaSearchIndexService | com.iflytek.skillhub.search | 20 | 47 |
| HashingSearchEmbeddingService | com.iflytek.skillhub.search | 4 | 49 |
| SkillSearchDocument | com.iflytek.skillhub.search | 3 | 1 |
| JpaSearchRebuildService | com.iflytek.skillhub.search | 2 | 0 |
| SearchTextTokenizer | com.iflytek.skillhub.search | 2 | 31 |
| SearchVisibilityScope | com.iflytek.skillhub.search | 2 | 2 |
| SearchQuery | com.iflytek.skillhub.search | 2 | 1 |
| JpaSearchIndexService | com.iflytek.skillhub.search | 2 | 0 |
| SearchIndexEventListener | com.iflytek.skillhub.search.event | 1 | 9 |
### skillhub-notification
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| NotificationPreferenceService | com.iflytek.skillhub.notification.service | 5 | 29 |
| NotificationPreference | com.iflytek.skillhub.notification.domain | 5 | 9 |
| SseEmitterManager | com.iflytek.skillhub.notification.sse | 5 | 56 |
| Notification | com.iflytek.skillhub.notification.domain | 2 | 24 |
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
| ClawHubCompatAppService | com.iflytek.skillhub.compat | 70 | 103 |
| AbstractStreamConsumer | com.iflytek.skillhub.stream | 55 | 68 |
| SkillLabelAppService | com.iflytek.skillhub.service | 47 | 27 |
| SkillScannerConfig | com.iflytek.skillhub.config | 43 | 3 |
| NotificationEventListener | com.iflytek.skillhub.listener | 39 | 94 |
| ReviewPortalAppService | com.iflytek.skillhub.service | 32 | 71 |
| SkillController | com.iflytek.skillhub.controller.portal | 32 | 109 |
| GlobalExceptionHandler | com.iflytek.skillhub.exception | 30 | 44 |
| AdminUserSearchRepository | com.iflytek.skillhub.repository | 29 | 3 |
| SkillLifecycleAppService | com.iflytek.skillhub.service | 29 | 70 |
| ReviewController | com.iflytek.skillhub.controller.portal | 26 | 27 |
| AnonymousDownloadIdentityService | com.iflytek.skillhub.ratelimit | 23 | 38 |
| AdminAuditLogAppService | com.iflytek.skillhub.service | 21 | 37 |
| ZipPackageExtractor | com.iflytek.skillhub.controller.support | 21 | 54 |
| PromotionPortalAppService | com.iflytek.skillhub.service | 20 | 34 |
| SkillPackageArchiveExtractor | com.iflytek.skillhub.controller.support | 20 | 56 |
| MockUassController | com.iflytek.skillhub.controller | 17 | 3 |
| LabelLocalizationService | com.iflytek.skillhub.service | 17 | 1 |
| ScanTaskConsumer | com.iflytek.skillhub.stream | 16 | 103 |
| GovernanceWorkflowAppService | com.iflytek.skillhub.service | 15 | 24 |
| CompatSkillLookupService | com.iflytek.skillhub.compat | 13 | 19 |
| ClawHubRegistryFacade | com.iflytek.skillhub.compat | 13 | 71 |
| MultipartPackageExtractor | com.iflytek.skillhub.controller.support | 13 | 32 |
| NotificationController | com.iflytek.skillhub.controller.portal | 13 | 46 |
| JpaGovernanceQueryRepository | com.iflytek.skillhub.repository | 12 | 150 |
| DeviceAuthWebController | com.iflytek.skillhub.controller | 12 | 0 |
| UserProfileController | com.iflytek.skillhub.controller | 12 | 71 |
| PublicLabelAppService | com.iflytek.skillhub.service | 12 | 4 |
| IdempotencyInterceptor | com.iflytek.skillhub.filter | 12 | 50 |
| SecurityAuditController | com.iflytek.skillhub.controller.portal | 11 | 43 |
| ClawHubCompatController | com.iflytek.skillhub.compat | 9 | 15 |
| NamespacePortalCommandAppService | com.iflytek.skillhub.service | 9 | 70 |
| MySkillAppService | com.iflytek.skillhub.service | 9 | 53 |
| SkillLifecycleController | com.iflytek.skillhub.controller.portal | 9 | 18 |
| NamespaceController | com.iflytek.skillhub.controller.portal | 9 | 28 |
| CliWhoamiResponse | com.iflytek.skillhub.dto | 8 | 0 |
| LabelSearchSyncListener | com.iflytek.skillhub.service | 8 | 4 |
| SkillLabelController | com.iflytek.skillhub.controller.portal | 8 | 5 |
| AuthController | com.iflytek.skillhub.controller | 7 | 51 |
| AuthFailureThrottleService | com.iflytek.skillhub.security | 7 | 83 |
| ClientIpResolver | com.iflytek.skillhub.ratelimit | 7 | 18 |
| MemberResponse | com.iflytek.skillhub.dto | 7 | 10 |
| NamespacePortalQueryAppService | com.iflytek.skillhub.service | 7 | 43 |
| SkillScannerProperties.Analyzers | com.iflytek.skillhub.config | 6 | 31 |
| AdminSkillReportController | com.iflytek.skillhub.controller.admin | 6 | 16 |
| LabelSearchSyncService | com.iflytek.skillhub.service | 6 | 15 |
| SkillDeleteAppService | com.iflytek.skillhub.service | 6 | 53 |
| SkillScannerProperties | com.iflytek.skillhub.config | 5 | 36 |
| DeviceAuthController | com.iflytek.skillhub.controller | 5 | 0 |
| LocalFileIndexStartupSynchronizer | com.iflytek.skillhub.bootstrap | 5 | 24 |
| SkillHubMetrics | com.iflytek.skillhub.metrics | 5 | 19 |
| AdminSkillController | com.iflytek.skillhub.controller.admin | 5 | 15 |
| LabelAdminAppService | com.iflytek.skillhub.service | 5 | 72 |
| PromotionController | com.iflytek.skillhub.controller.portal | 5 | 13 |
| SkillTagController | com.iflytek.skillhub.controller.portal | 5 | 9 |
| TokenController | com.iflytek.skillhub.controller | 4 | 34 |
| LocalAuthController | com.iflytek.skillhub.controller | 4 | 38 |
| NamespaceMemberCandidateService | com.iflytek.skillhub.service | 4 | 29 |
| NotificationPreferenceController | com.iflytek.skillhub.controller.portal | 4 | 26 |
| JpaMySkillQueryRepository | com.iflytek.skillhub.repository | 3 | 51 |
| SkillScannerProperties.Policy | com.iflytek.skillhub.config | 3 | 10 |
| RedisStreamConfig | com.iflytek.skillhub.config | 3 | 0 |
| RedissonConfig | com.iflytek.skillhub.config | 3 | 43 |
| AccountMergeController | com.iflytek.skillhub.controller | 3 | 19 |
| InMemorySlidingWindowRateLimiter | com.iflytek.skillhub.ratelimit | 3 | 15 |
| ClawHubDeleteResponse | com.iflytek.skillhub.compat.dto | 3 | 0 |
| LabelAdminAppService.new TransactionSynchronization() {...} | com.iflytek.skillhub.service | 3 | 0 |
| SkillLabelAppService.new TransactionSynchronization() {...} | com.iflytek.skillhub.service | 3 | 0 |
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
| AdminLabelController | com.iflytek.skillhub.controller.admin | 2 | 10 |
| AdminUserAppService | com.iflytek.skillhub.service | 2 | 67 |
| MeController | com.iflytek.skillhub.controller.portal | 2 | 8 |
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
| ClawHubSkillListResponse.SkillListItem | com.iflytek.skillhub.compat.dto | 1 | 0 |
| ClawHubSkillListResponse | com.iflytek.skillhub.compat.dto | 1 | 0 |
| ClawHubSkillListResponse.SkillListItem.LatestVersion | com.iflytek.skillhub.compat.dto | 1 | 0 |
| ClawHubSkillResponse.VersionInfo | com.iflytek.skillhub.compat.dto | 1 | 0 |
| ClawHubSkillResponse.OwnerInfo | com.iflytek.skillhub.compat.dto | 1 | 0 |
| SkillSearchAppService | com.iflytek.skillhub.service | 1 | 86 |
| LabelSearchSyncRequestedEvent | com.iflytek.skillhub.service | 1 | 0 |
| NoOpProfileModerationService | com.iflytek.skillhub.service | 1 | 1 |
| GovernanceWorkbenchAppService | com.iflytek.skillhub.service | 1 | 70 |
| MultipartPackageExtractor.PublishPayload.ForkOf | com.iflytek.skillhub.controller.support | 1 | 0 |
| SkillRatingController | com.iflytek.skillhub.controller.portal | 1 | 10 |
| SkillStarController | com.iflytek.skillhub.controller.portal | 1 | 10 |
