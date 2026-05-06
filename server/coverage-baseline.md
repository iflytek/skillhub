# MySQL Main Path Coverage Baseline
Generated: 2026-05-06

## Scope Definition

- **Profile:** MySQL main path
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
| Total Lines | 11937 |
| Covered Lines | 10971 |
| Missed Lines | 966 |
| Line Coverage | 91.91% |

## Per-Module Line Coverage
| Module | Total Lines | Covered | Missed | Coverage |
|--------|-------------|---------|--------|----------|
| skillhub-app | 5107 | 4195 | 912 | 82.14% |
| skillhub-auth | 2083 | 2029 | 54 | 97.41% |
| skillhub-domain | 3310 | 3310 | 0 | 100.00% |
| skillhub-infra | 308 | 308 | 0 | 100.00% |
| skillhub-notification | 207 | 207 | 0 | 100.00% |
| skillhub-search | 709 | 709 | 0 | 100.00% |
| skillhub-storage | 213 | 213 | 0 | 100.00% |

## Uncovered Classes Inventory (by module, sorted by missed lines desc)

### skillhub-app
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| SkillController | com.iflytek.skillhub.controller.portal | 135 | 6 |
| SkillLifecycleAppService | com.iflytek.skillhub.service | 75 | 24 |
| ReviewController | com.iflytek.skillhub.controller.portal | 50 | 3 |
| SecurityAuditController | com.iflytek.skillhub.controller.portal | 47 | 7 |
| ReviewPortalAppService | com.iflytek.skillhub.service | 39 | 64 |
| PromotionPortalAppService | com.iflytek.skillhub.service | 33 | 21 |
| NamespaceController | com.iflytek.skillhub.controller.portal | 31 | 6 |
| UassAuthController | com.iflytek.skillhub.controller | 29 | 0 |
| AdminUserSearchRepository | com.iflytek.skillhub.repository | 29 | 3 |
| GovernanceWorkflowAppService | com.iflytek.skillhub.service | 29 | 10 |
| SkillLifecycleController | com.iflytek.skillhub.controller.portal | 24 | 3 |
| LocalAuthController | com.iflytek.skillhub.controller | 23 | 19 |
| ClawHubCompatController | com.iflytek.skillhub.compat | 21 | 3 |
| AuthMethodCatalog | com.iflytek.skillhub.service | 20 | 36 |
| TokenController | com.iflytek.skillhub.controller | 19 | 19 |
| SkillSearchController | com.iflytek.skillhub.controller.portal | 19 | 5 |
| AdminSkillReportController | com.iflytek.skillhub.controller.admin | 18 | 4 |
| AdminSkillController | com.iflytek.skillhub.controller.admin | 17 | 3 |
| SkillPublishController | com.iflytek.skillhub.controller.portal | 17 | 5 |
| AdminProfileReviewController | com.iflytek.skillhub.controller.admin | 16 | 12 |
| SkillDeleteController | com.iflytek.skillhub.controller.portal | 16 | 3 |
| PromotionController | com.iflytek.skillhub.controller.portal | 15 | 3 |
| SkillLifecycleDeleteController | com.iflytek.skillhub.controller.portal | 14 | 3 |
| JpaGovernanceQueryRepository | com.iflytek.skillhub.repository | 14 | 148 |
| AccountMergeController | com.iflytek.skillhub.controller | 13 | 9 |
| SkillReportController | com.iflytek.skillhub.controller.portal | 13 | 5 |
| SkillTagController | com.iflytek.skillhub.controller.portal | 11 | 3 |
| RateLimitInterceptor | com.iflytek.skillhub.ratelimit | 11 | 51 |
| SkillLabelController | com.iflytek.skillhub.controller.portal | 10 | 3 |
| ApiAccessDeniedHandler | com.iflytek.skillhub.security | 10 | 6 |
| AdminLabelController | com.iflytek.skillhub.controller.admin | 9 | 3 |
| UserManagementController | com.iflytek.skillhub.controller.admin | 9 | 6 |
| SkillRatingController | com.iflytek.skillhub.controller.portal | 8 | 3 |
| AdminSearchController | com.iflytek.skillhub.controller.admin | 7 | 4 |
| TagResponse | com.iflytek.skillhub.dto | 6 | 0 |
| ProfileFieldPolicyProperties | com.iflytek.skillhub.config | 4 | 5 |
| AuthController | com.iflytek.skillhub.controller | 3 | 55 |
| NotificationEventListener | com.iflytek.skillhub.listener | 3 | 130 |
| AuditRequestContext | com.iflytek.skillhub.service | 3 | 1 |
| ProfileFieldPolicyProperties.FieldEntry | com.iflytek.skillhub.config | 2 | 3 |
| ClawHubCompatAppService | com.iflytek.skillhub.compat | 1 | 172 |
| ClawHubSkillResponse.OwnerInfo | com.iflytek.skillhub.compat.dto | 1 | 0 |
| AuditLogController | com.iflytek.skillhub.controller.admin | 1 | 3 |
| LabelController | com.iflytek.skillhub.controller.portal | 1 | 3 |
| SecurityAuditController.new TypeReference() {...} | com.iflytek.skillhub.controller.portal | 1 | 0 |
| AdminSkillMutationResponse | com.iflytek.skillhub.dto | 1 | 0 |
| AdminSkillReportActionRequest | com.iflytek.skillhub.dto | 1 | 0 |
| AdminUserRoleUpdateRequest | com.iflytek.skillhub.dto | 1 | 0 |
| AdminUserStatusUpdateRequest | com.iflytek.skillhub.dto | 1 | 0 |
| AuthProviderResponse | com.iflytek.skillhub.dto | 1 | 0 |
| BatchMemberRequest | com.iflytek.skillhub.dto | 1 | 0 |
| ChangePasswordRequest | com.iflytek.skillhub.dto | 1 | 0 |
| LocalRegisterRequest | com.iflytek.skillhub.dto | 1 | 0 |
| MergeInitiateResponse | com.iflytek.skillhub.dto | 1 | 0 |
| PasswordResetConfirmRequest | com.iflytek.skillhub.dto | 1 | 0 |
| PasswordResetRequestDto | com.iflytek.skillhub.dto | 1 | 0 |
| ProfileReviewMutationResponse | com.iflytek.skillhub.dto | 1 | 0 |
| ProfileReviewRejectRequest | com.iflytek.skillhub.dto | 1 | 0 |
| PromotionActionRequest | com.iflytek.skillhub.dto | 1 | 0 |
| PromotionRequestDto | com.iflytek.skillhub.dto | 1 | 0 |
| PublishResponse | com.iflytek.skillhub.dto | 1 | 0 |
| ResolveVersionResponse | com.iflytek.skillhub.dto | 1 | 0 |
| ReviewActionRequest | com.iflytek.skillhub.dto | 1 | 0 |
| ReviewTaskRequest | com.iflytek.skillhub.dto | 1 | 0 |
| SecurityAuditResponse | com.iflytek.skillhub.dto | 1 | 0 |
| SkillDeleteResponse | com.iflytek.skillhub.dto | 1 | 0 |
| SkillRatingRequest | com.iflytek.skillhub.dto | 1 | 0 |
| SkillRatingStatusResponse | com.iflytek.skillhub.dto | 1 | 0 |
| SkillReportMutationResponse | com.iflytek.skillhub.dto | 1 | 0 |
| SkillReportSubmitRequest | com.iflytek.skillhub.dto | 1 | 0 |
| SkillVersionDetailResponse | com.iflytek.skillhub.dto | 1 | 0 |
| SkillVersionRereleaseRequest | com.iflytek.skillhub.dto | 1 | 0 |
| TokenExpirationUpdateRequest | com.iflytek.skillhub.dto | 1 | 0 |
| TokenSummaryResponse | com.iflytek.skillhub.dto | 1 | 0 |
| UassLoginStatusResponse | com.iflytek.skillhub.dto | 1 | 0 |
| UassLoginUrlResponse | com.iflytek.skillhub.dto | 1 | 0 |
| ForbiddenException | com.iflytek.skillhub.exception | 1 | 2 |
| UnauthorizedException | com.iflytek.skillhub.exception | 1 | 2 |
| DirectAuthService | com.iflytek.skillhub.service | 1 | 14 |
| SessionBootstrapService | com.iflytek.skillhub.service | 1 | 17 |

### skillhub-auth
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| GitHubClaimsExtractor | com.iflytek.skillhub.auth.oauth | 27 | 0 |
| MockAuthFilter | com.iflytek.skillhub.auth.mock | 17 | 5 |
| GitLabClaimsExtractor | com.iflytek.skillhub.auth.oauth | 8 | 43 |
| GitHubClaimsExtractor.GitHubEmail | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| GitHubClaimsExtractor.new ParameterizedTypeReference() {...} | com.iflytek.skillhub.auth.oauth | 1 | 0 |
