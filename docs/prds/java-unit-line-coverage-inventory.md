# Java Unit Test Line Coverage Inventory

当前清单基于 `server/*/target/site/jacoco/jacoco.csv` 生成。

说明：
- 仅列出 `line_missed > 0` 的类
- 指标为 Java 生产代码的 JaCoCo line coverage
- 按模块分组，便于后续拆批补测
- 报告命令：`mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`
- 聚合报告路径：`server/skillhub-app/target/site/jacoco-aggregate/index.html`
- 当前后端多模块 aggregate line coverage 为 `100.00%`（`11943/11943`）

### Explicit Exclusions from Production Gate

以下类虽被跟踪，但因属于 mock / dev-only / platform-specific，明确排除在覆盖率门禁之外：

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

（本模块所有生产类 line_missed = 0）


## skillhub-auth

（本模块所有生产类 line_missed = 0）


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

