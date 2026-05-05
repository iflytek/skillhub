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
| Total Lines | 11939 |
| Covered Lines | 11596 |
| Missed Lines | 343 |
| Line Coverage | 97.13% |

## Per-Module Line Coverage
| Module | Total Lines | Covered | Missed | Coverage |
|--------|-------------|---------|--------|----------|
| skillhub-app | 5107 | 5039 | 68 | 98.67% |
| skillhub-auth | 2083 | 1982 | 101 | 95.15% |
| skillhub-domain | 3312 | 3200 | 112 | 96.62% |
| skillhub-infra | 308 | 308 | 0 | 100.00% |
| skillhub-notification | 207 | 207 | 0 | 100.00% |
| skillhub-search | 709 | 709 | 0 | 100.00% |
| skillhub-storage | 213 | 151 | 62 | 70.89% |

## Uncovered Classes Inventory (by module, sorted by missed lines desc)

### skillhub-app
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| AdminUserSearchRepository | com.iflytek.skillhub.repository | 29 | 3 |
| JpaGovernanceQueryRepository | com.iflytek.skillhub.repository | 14 | 148 |
| UserProfileController | com.iflytek.skillhub.controller | 12 | 71 |
| AuthController | com.iflytek.skillhub.controller | 7 | 51 |
| SkillDeleteAppService | com.iflytek.skillhub.service | 6 | 53 |

### skillhub-auth
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| GitHubClaimsExtractor | com.iflytek.skillhub.auth.oauth | 27 | 0 |
| MockAuthFilter | com.iflytek.skillhub.auth.mock | 22 | 0 |
| PasswordResetService | com.iflytek.skillhub.auth.local | 17 | 97 |
| LocalAuthService | com.iflytek.skillhub.auth.local | 16 | 86 |
| GitLabClaimsExtractor | com.iflytek.skillhub.auth.oauth | 8 | 43 |
| DeviceAuthService | com.iflytek.skillhub.auth.device | 5 | 52 |
| SecurityConfig | com.iflytek.skillhub.auth.config | 4 | 116 |
| GitHubClaimsExtractor.GitHubEmail | com.iflytek.skillhub.auth.oauth | 1 | 0 |
| GitHubClaimsExtractor.new ParameterizedTypeReference() {...} | com.iflytek.skillhub.auth.oauth | 1 | 0 |

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

### skillhub-storage
| Class | Package | Missed Lines | Covered Lines |
|-------|---------|--------------|---------------|
| S3StorageService | com.iflytek.skillhub.storage | 62 | 68 |
