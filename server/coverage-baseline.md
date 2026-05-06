# MySQL Main Path Coverage Baseline
Generated: 2026-05-06

## Scope Definition

- **Profile:** MySQL main path
- **Source of truth:** Module-level JaCoCo CSVs at `server/*/target/site/jacoco/jacoco.csv`
- **Report command:** `mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`
- **Aggregate report path:** `server/skillhub-app/target/site/jacoco-aggregate/index.html`

### Explicit Exclusions from Production Gate

The following classes are tracked but explicitly excluded from the production coverage gate because they are mock, dev-only, or platform-specific:

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
| Total Lines | 11943 |
| Covered Lines | 11943 |
| Missed Lines | 0 |
| Line Coverage | 100.00% |

## Per-Module Line Coverage
| Module | Total Lines | Covered | Missed | Coverage |
|--------|-------------|---------|--------|----------|
| skillhub-app | 5107 | 5107 | 0 | 100.00% |
| skillhub-auth | 2089 | 2089 | 0 | 100.00% |
| skillhub-domain | 3310 | 3310 | 0 | 100.00% |
| skillhub-infra | 308 | 308 | 0 | 100.00% |
| skillhub-notification | 207 | 207 | 0 | 100.00% |
| skillhub-search | 709 | 709 | 0 | 100.00% |
| skillhub-storage | 213 | 213 | 0 | 100.00% |

## Uncovered Classes Inventory (by module, sorted by missed lines desc)
