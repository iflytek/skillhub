# MySQL Main Path Coverage Baseline
Generated: 2026-05-05

## Scope Definition

- **Profile:** MySQL main path (post-H2 removal)
- **Source of truth:** Module-level JaCoCo CSVs at `server/*/target/site/jacoco/jacoco.csv`
- **Report command:** `mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`
- **Aggregate report path:** `server/skillhub-app/target/site/jacoco-aggregate/index.html`

### Explicit Exclusions from Production Gate

The following classes are tracked but explicitly excluded from the production coverage gate because they are mock, dev-only, or platform-specific:

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

## Overall Line Coverage
| Metric | Value |
|--------|-------|
| Total Lines | 11945 |
| Covered Lines | 11397 |
| Missed Lines | 548 |
| Line Coverage | 95.41% |

## Per-Module Line Coverage
| Module | Total Lines | Covered | Missed | Coverage |
|--------|-------------|---------|--------|----------|
| skillhub-app | 5109 | 4926 | 183 | 96.42% |
| skillhub-auth | 2083 | 1967 | 116 | 94.43% |
| skillhub-domain | 3312 | 3153 | 159 | 95.20% |
| skillhub-infra | 308 | 296 | 12 | 96.10% |
| skillhub-notification | 207 | 207 | 0 | 100.00% |
| skillhub-search | 713 | 713 | 0 | 100.00% |
| skillhub-storage | 213 | 135 | 78 | 63.38% |

## Uncovered Classes Inventory (by module, sorted by missed lines desc)

### skillhub-app
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| AdminUserSearchRepository | com.iflytek.skillhub.repository | 29 | 3 |
| MockUassController | com.iflytek.skillhub.controller | 17 | 3 |
| DeviceAuthWebController | com.iflytek.skillhub.controller | 12 | 0 |
| UserProfileController | com.iflytek.skillhub.controller | 12 | 71 |
| JpaGovernanceQueryRepository | com.iflytek.skillhub.repository | 12 | 150 |
| AuthController | com.iflytek.skillhub.controller | 7 | 51 |
| AuthFailureThrottleService | com.iflytek.skillhub.security | 7 | 83 |
| SkillDeleteAppService | com.iflytek.skillhub.service | 6 | 53 |
| LocalFileIndexStartupSynchronizer | com.iflytek.skillhub.bootstrap | 5 | 24 |
| DeviceAuthController | com.iflytek.skillhub.controller | 5 | 0 |
| LabelAdminAppService | com.iflytek.skillhub.service | 5 | 72 |
| LocalAuthController | com.iflytek.skillhub.controller | 4 | 38 |
| TokenController | com.iflytek.skillhub.controller | 4 | 34 |
| NamespaceMemberCandidateService | com.iflytek.skillhub.service | 4 | 29 |
| AccountMergeController | com.iflytek.skillhub.controller | 3 | 19 |
| AuthContextFilter | com.iflytek.skillhub.filter | 3 | 50 |
| RequestLoggingFilter | com.iflytek.skillhub.filter | 3 | 45 |
| InMemorySlidingWindowRateLimiter | com.iflytek.skillhub.ratelimit | 3 | 15 |
| AdminProfileReviewAppService | com.iflytek.skillhub.service | 3 | 12 |
| AdminSkillReportAppService | com.iflytek.skillhub.service | 3 | 10 |
| LabelAdminAppService.new TransactionSynchronization() {...} | com.iflytek.skillhub.service | 3 | 0 |
| ReviewSkillDetailAppService | com.iflytek.skillhub.service | 3 | 69 |
| SkillhubApplication | com.iflytek.skillhub | 2 | 1 |
| RuntimeStateEnvironmentPostProcessor | com.iflytek.skillhub.config | 2 | 9 |
| SkillPublishController | com.iflytek.skillhub.controller.portal | 2 | 20 |
| SkillSearchController | com.iflytek.skillhub.controller.portal | 2 | 22 |
| RateLimitInterceptor | com.iflytek.skillhub.ratelimit | 2 | 60 |
| JpaAdminSkillReportQueryRepository | com.iflytek.skillhub.repository | 2 | 31 |
| JpaMySkillQueryRepository | com.iflytek.skillhub.repository | 2 | 52 |
| JpaProfileReviewQueryRepository | com.iflytek.skillhub.repository | 2 | 38 |
| AdminUserAppService | com.iflytek.skillhub.service | 2 | 67 |
| RedissonConfig | com.iflytek.skillhub.config | 1 | 45 |
| BaseApiController | com.iflytek.skillhub.controller | 1 | 4 |
| DeviceAuthController.TokenRequest | com.iflytek.skillhub.controller | 1 | 0 |
| DeviceAuthWebController.AuthorizeRequest | com.iflytek.skillhub.controller | 1 | 0 |
| AdminProfileReviewController | com.iflytek.skillhub.controller.admin | 1 | 27 |
| UserManagementController | com.iflytek.skillhub.controller.admin | 1 | 14 |
| SkillRatingController | com.iflytek.skillhub.controller.portal | 1 | 10 |
| SkillStarController | com.iflytek.skillhub.controller.portal | 1 | 10 |
| MockUassLoginRequest | com.iflytek.skillhub.dto | 1 | 0 |
| MockUassLoginResponse | com.iflytek.skillhub.dto | 1 | 0 |
| GovernanceWorkbenchAppService | com.iflytek.skillhub.service | 1 | 70 |
| SkillSearchAppService | com.iflytek.skillhub.service | 1 | 86 |

### skillhub-auth
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| GitHubClaimsExtractor | com.iflytek.skillhub.auth.oauth | 27 | 0 |
| MockAuthFilter | com.iflytek.skillhub.auth.mock | 22 | 0 |
| PasswordResetService | com.iflytek.skillhub.auth.local | 17 | 97 |
| LocalAuthService | com.iflytek.skillhub.auth.local | 16 | 86 |
| GitLabClaimsExtractor | com.iflytek.skillhub.auth.oauth | 8 | 43 |
| DeviceAuthService | com.iflytek.skillhub.auth.device | 5 | 52 |
| AccountMergeRequest | com.iflytek.skillhub.auth.merge | 5 | 18 |
| SecurityConfig | com.iflytek.skillhub.auth.config | 4 | 116 |
| SkillHubOAuth2AuthorizationRequestResolver | com.iflytek.skillhub.auth.oauth | 3 | 7 |
| OAuth2LoginFailureHandler | com.iflytek.skillhub.auth.oauth | 2 | 8 |
| OAuth2LoginSuccessHandler | com.iflytek.skillhub.auth.oauth | 2 | 14 |
| DeviceCodeData | com.iflytek.skillhub.auth.device | 1 | 12 |
| GitHubClaimsExtractor.GitHubEmail | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| GitHubClaimsExtractor.new ParameterizedTypeReference() {...} | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| OAuthLoginRedirectSupport | com.iflytek.skillhub.auth.oauth | 1 | 7 |
| UassProperties | com.iflytek.skillhub.auth.uass | 1 | 43 |

### skillhub-domain
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| SkillPackagePolicy | com.iflytek.skillhub.domain.skill.validation | 29 | 58 |
| SkillMetadataParser | com.iflytek.skillhub.domain.skill.metadata | 14 | 49 |
| SkillPackageValidator | com.iflytek.skillhub.domain.skill.validation | 14 | 53 |
| BasicPrePublishValidator | com.iflytek.skillhub.domain.skill.validation | 12 | 32 |
| NamespaceService | com.iflytek.skillhub.domain.namespace | 11 | 44 |
| SkillRating | com.iflytek.skillhub.domain.social | 11 | 11 |
| ProfileReviewService | com.iflytek.skillhub.domain.user | 11 | 37 |
| LabelPermissionChecker | com.iflytek.skillhub.domain.label | 10 | 0 |
| SkillReport | com.iflytek.skillhub.domain.report | 7 | 24 |
| SkillTagService | com.iflytek.skillhub.domain.skill.service | 5 | 46 |
| AuditLogQueryService | com.iflytek.skillhub.domain.audit | 4 | 0 |
| NamespaceMemberService | com.iflytek.skillhub.domain.namespace | 4 | 48 |
| SkillStorageDeletionCompensationService | com.iflytek.skillhub.domain.skill.service | 4 | 21 |
| LabelSlugValidator | com.iflytek.skillhub.domain.label | 3 | 12 |
| ReviewPermissionChecker | com.iflytek.skillhub.domain.review | 3 | 37 |
| SkillHardDeleteService | com.iflytek.skillhub.domain.skill.service | 3 | 72 |
| UserProfileService | com.iflytek.skillhub.domain.user | 3 | 60 |
| SecurityScanService | com.iflytek.skillhub.domain.security | 2 | 88 |
| SkillReviewSubmitService | com.iflytek.skillhub.domain.skill.service | 2 | 46 |
| NoOpPrePublishValidator | com.iflytek.skillhub.domain.skill.validation | 2 | 0 |
| GovernanceNotificationService | com.iflytek.skillhub.domain.governance | 1 | 15 |
| SlugValidator | com.iflytek.skillhub.domain.namespace | 1 | 25 |
| ModerationResult | com.iflytek.skillhub.domain.user | 1 | 3 |
| UpdateProfileResult | com.iflytek.skillhub.domain.user | 1 | 2 |
| UpdateProfileResult.Mixed | com.iflytek.skillhub.domain.user | 1 | 0 |

### skillhub-infra
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| NotificationJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillVersionJpaRepository | com.iflytek.skillhub.infra.jpa | 2 | 0 |
| SkillScannerAdapter | com.iflytek.skillhub.infra.scanner | 2 | 57 |
| SkillScannerService | com.iflytek.skillhub.infra.scanner | 2 | 62 |
| SecurityAuditJpaRepository | com.iflytek.skillhub.infra.jpa | 1 | 0 |
| SkillReportJpaRepository | com.iflytek.skillhub.infra.jpa | 1 | 0 |

### skillhub-storage
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| S3StorageService | com.iflytek.skillhub.storage | 62 | 68 |
| S3StorageProperties | com.iflytek.skillhub.storage | 9 | 27 |
| StorageProperties | com.iflytek.skillhub.storage | 3 | 4 |
| LocalFileStorageService | com.iflytek.skillhub.storage | 2 | 27 |
| StorageAccessException | com.iflytek.skillhub.storage | 2 | 4 |
