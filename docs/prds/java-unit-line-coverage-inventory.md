# Java Unit Test Line Coverage Inventory

当前清单基于 `server/*/target/site/jacoco/jacoco.csv` 生成。

说明：
- 仅列出 `line_missed > 0` 的类
- 指标为 Java 生产代码的 JaCoCo line coverage
- 按模块分组，便于后续拆批补测
- 报告命令：`mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`
- 聚合报告路径：`server/skillhub-app/target/site/jacoco-aggregate/index.html`
- 当前后端多模块 aggregate line coverage 为 `99.19%`（`11840/11937`）

### Explicit Exclusions from Production Gate

以下类虽被跟踪，但因属于 mock / dev-only / platform-specific，明确排除在覆盖率门禁之外：

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
| `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository` | 14 | 148 | 91.36% |

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

