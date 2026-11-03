# Business-event Logs over OTLP / OTLP 业务事件日志

Default off. This is lossy analytics, NOT audit, billing, notification or installation confirmation. Use the official server image built from this change; no custom application code, vendor SDK or Collector is required. The receiver must support OTLP/HTTP protobuf Logs. / 默认关闭；用于有损分析，不替代审计、计费、可靠通知或安装确认。只需对应版本官方镜像与运维配置，Collector 可选。

## Choose a path / 选择路径

- Production: `runtime.sh business-logs-enable` connects an existing receiver; `business-logs-up` installs an optional private Collector with an explicit downstream. See [production integration](#production-integration).
- Local trial: `make business-logs-demo-up` supports `make dev-server` or a containerized backend. See [local trial](#local-trial).
- Both paths include configuration updates, new-event queries, metrics, troubleshooting and cleanup. Headers stay in deployment-owned files / 两条路径均包含配置更新、新事件查询、指标、排障与清理，请求头保留在部署侧。

## Event catalog / 事件目录 (contract version 1)

Every row requires `skillhub.event.name`, `skillhub.skill.id`, `skillhub.namespace.id`, `skillhub.visibility`, `skillhub.source`, `skillhub.actor.type`, `log.record.uid` and producer occurrence `Timestamp`. IDs are numeric except the local opaque human `user.id` and UUID UID. Actor type is `user` (including anonymous/unknown user requests) or `system`. A known human adds `user.id`; anonymous/unknown and system actors omit it. / 所有事件都有上述字段；主体类型为 user（含匿名或未知请求）或 system；已知人类操作者额外使用本实例不透明 `user.id`，匿名、未知及系统主体省略该字段。

| Event | When / 时机 | Extra fields / 额外字段 | Actor and source / 操作者与来源 |
| --- | --- | --- | --- |
| `skill.publication.effective` | Actual transition into PUBLISHED, exported only after commit / 实际发布且事务提交 | `skillhub.version.id` | Auto-publication: submitting privileged human, `auto_publish`; internal forced publication: `system`, no `user.id`. PRIVATE confirmation: confirmer, `private_confirm`. Review/promotion approval: actual approver, `review` / `promotion`, NOT necessarily uploader or owner / 审核或提升时为实际批准人而非上传者 |
| `skill.download.prepared` | Authorized PUBLISHED request has built its download result and updated existing counters; before stream opens/redirect / 已授权的已发布版本已构建下载结果，尚未打开流或跳转 | `skillhub.version.id` | Current authenticated local user, otherwise anonymous (`user` without `user.id`); `download` / 当前调用人，否则 user 类型且省略 ID |
| `skill.review.approved` | Existing content-version review approved, after commit / 内容版本审核通过并提交 | `skillhub.version.id`, `skillhub.review.id` | Actual reviewer; `review` / 实际审核人 |
| `skill.review.rejected` | Existing content-version review rejected, after commit / 内容版本审核拒绝并提交 | `skillhub.version.id`, `skillhub.review.id` | Actual reviewer; `review`; no reason/comment / 不导出拒绝原因 |
| `skill.promotion.approved` | Initial or update promotion approved, after commit / 首次或更新提升批准并提交 | `skillhub.promotion.id` | Actual approver; `promotion`; skill/namespace refer to SOURCE, not derived target / 技能及空间指来源而非全局目标 |
| `skill.promotion.rejected` | Promotion rejected, after commit / 提升拒绝并提交 | `skillhub.promotion.id` | Actual reviewer; `promotion`; SOURCE scope; no reason / 来源范围，不导出原因 |
| `skill.status.changed` | Archive/unarchive actually changes status, after commit / 归档或取消归档状态实际变化并提交 | `skillhub.status.old`, `skillhub.status.new` | Owner, namespace manager or authorized admin invoking operation; `governance` / 实际执行的所有者、空间管理者或管理员 |
| `skill.visibility.changed` | Effective access-scope change: immediate narrowing OR approved expansion, after commit / 缩小范围立即生效，扩大范围审核通过后生效；均提交后导出 | `skillhub.visibility.old`, `skillhub.visibility.new` | Narrowing requester or expansion approver; `visibility` / 缩小时请求人，扩大时批准人 |
| `skill.version.yanked` | Existing published-version yank, after commit / 已发布版本撤回分发并提交 | `skillhub.version.id` | Actual owner/manager/admin; `governance` / 实际执行人 |

Publication covers all four producers: `SkillPublishService`, `SkillReviewSubmitService`, `ReviewService`, `PromotionService`. The reserved `builtin-skill-publisher` identity is classified as `system` even through the ordinary privileged auto-publication path (bootstrap verifies that it is a system account). Approval may correctly create TWO different facts: publication plus review/promotion approval. These are not duplicate records. Promotion approval's publication references the TARGET skill/version/namespace; its promotion fact references the SOURCE. Namespace filtering may therefore keep only one. / 发布覆盖四条生产路径；内置技能的保留系统身份即便走普通特权直发，也标记为 system，启动器会校验其系统账号属性；批准同时产生发布及审核/提升两个不同事实，不是重复。提升的发布事件指目标，提升决定事件指来源，空间过滤可能只保留其一。

Timestamp is captured immediately before the producer publishes its domain event, not `publishedAt`, transaction commit time or async-send time. Transaction synchronization captures the record and valid span identity at event dispatch, admits only after the enclosing physical transaction commits, and suppresses records when that physical transaction rolls back. Outside a transaction (notably the shared download service), admission is immediate. No entity lookup happens in the telemetry listener. / 时间是生产点发布领域事件前的时刻，不是提交或发送时刻；事务内仅物理事务提交后入队，物理事务回滚不发；无事务下载直接入队。

Savepoint exclusion: rollback suppression does NOT cover `PROPAGATION_NESTED` savepoint rollback. Its synchronization belongs to the enclosing physical transaction: a nested event can still export when the outer transaction commits, even though the nested database write was rolled back. Do not use this pipeline as evidence of savepoint-level persistence; producers requiring that guarantee must not dispatch from nested/savepoint work. No current included production path configures NESTED propagation. `REQUIRES_NEW` is a separate physical transaction: its committed event exports even if the suspended outer transaction later rolls back; the outer event is suppressed. / 保存点例外：不保证 `PROPAGATION_NESTED` 回滚时抑制事件；外层物理事务提交仍可导出已回滚保存点内的事件。需要该保证的生产点不得在保存点内发布；当前纳入路径未配置 NESTED。`REQUIRES_NEW` 独立提交不受外层之后回滚影响。

Download means prepared, NOT delivered, complete, installed or executed. Web/v1/CLI share the producer; authorized previews of unpublished versions and review packages do not emit. Compat redirects alone, Suite plans, resolve/detail polling, direct object-storage access, URL reuse and client caches are not new registry download-prepared facts. / 下载准备不等于送达、传输完成、安装或执行；未发布预览、审核包、套装计划、查询轮询不计入。对象存储直取、URL 重用及缓存可绕过注册表。

Visibility facts cover the existing dedicated `SkillVisibilityService` event producers. Visibility assigned as part of uploading/publishing a content version has no separate visibility event in the existing domain model; its final value is included on the publication fact. / 可见范围事实覆盖独立范围调整的既有生产点；内容上传或发布时顺带赋予的可见范围没有独立领域事件，只在发布事实中携带最终值。

Deferred: metadata/avatar review decisions without an existing decision event (effective visibility changes ARE included), hide/unhide, promotion revocation, review submission/withdrawal, Suite events, searches/views, transfer completion, installation and durable replay. Existing audit/notification/search listeners and authorization rules are unchanged. Legacy events constructed without a producer snapshot are intentionally ignored, not enriched from a database at export time. / 其余事件分期；不变更既有审计、通知、搜索或授权；缺少生产点快照的旧式事件不导出，不在导出时查库补全。

## Representation and data boundary / 数据模型与边界

- Dedicated scope `com.iflytek.skillhub.business`, instrumentation version `1`; Spring Boot 3.2.3 BOM / OTel Java 1.31.0 unchanged. `skillhub.event.name` is a documented custom attribute because 1.31 lacks `setEventName`. This is standard OTLP LogRecord transport, not a claim of implementing the latest experimental OTel Events semantic conventions. `log.record.uid` borrows the experimental uniqueness convention; contract version 1 locks its meaning here, not an unpinned global semconv schema.
- Resource is ONLY `service.name` (existing `spring.application.name`), `service.version` (existing SkillHub setting), `deployment.environment` (existing SkillHub environment). No host/cloud/process/resource autodetection. Occurrence uses Timestamp; ObservedTimestamp is left to SDK. Body is empty.
- Only an already-valid current OTel span context is associated, including unsampled spans. No span is created and no MDC/request ID is converted into trace identity. Trace absence or sampling never filters business records; an associated unsampled trace may not be queryable.
- The sole mapper copies explicit fixed fields, never an event/entity map. Maximum 16 attributes, 256 UTF-16 code units per string (SDK limit as defense-in-depth), three bounded Resource strings, no arbitrary arrays/maps. Unknown event types/sources are ignored. IDs of private skills and namespaces ARE included by default when this instance-level feature is enabled. Empty namespace allowlist means all namespaces. This keeps review/governance analytics interpretable without exporting names or performing enrichment queries. IDs remain linkable/personal data, NOT anonymized; restrict the allowlist, receiver access, retention and destination accordingly.
- Never exported: skill/namespace names or slugs, package/body/metadata content, titles, summaries, rejection comments/reasons, submitter/owner identity, email/name, query terms, tokens/passwords/cookies, signed URLs, IP, UA, arbitrary request headers, request IDs, MDC or baggage. Authentication headers are transport-only, not attributes.

中文：独立记录器，不桥接应用日志，不更改 Trace；Resource 仅包含服务名、版本及环境。白名单最多 16 个属性、字符串最多 256 个 UTF-16 单元。默认包括私有技能及空间的 ID，但不包括名称/内容；ID 仍可关联个人或资源，须按敏感数据管理。命名空间白名单建议上线前明确限制。正文、原因、密钥、签名 URL、IP/UA、任意请求头和 baggage 一律不输出。

## Complete configuration / 完整配置

All settings are startup-only: recreate/restart the server after changes. Property prefix is `skillhub.observability.business-logs.`. Environment suffixes below use `SKILLHUB_BUSINESS_LOGS_`. `application.yml` and release Compose explicitly map them; manual SDK builders DO NOT automatically read `OTEL_*`. / 所有配置启动时读取，需重建/重启；以下环境变量使用统一前缀，不自动读取 `OTEL_*`。

| Property suffix / 属性 | Env suffix / 环境变量后缀 | Default / 默认 | Meaning / 范围与含义 |
| --- | --- | --- | --- |
| `enabled` | `ENABLED` | `false` | Opt-in, independent of tracing / 独立开关 |
| `endpoint` | `ENDPOINT` | empty | Required when enabled; full HTTP(S) Logs URL, used AS-IS, normally `/v1/logs`; NOT `/v1/traces`. No userinfo/query/fragment or known metadata literal targets / 启用必填完整 Logs URL，不追加路径 |
| `headers` | `HEADERS` | empty | At most 16 comma-separated URL-encoded `name=value` pairs; total 8192 characters; ASCII values, no controls / 最多 16 对 URL 编码请求头，总长 8192；使用 Secret |
| `timeout` | `TIMEOUT` | `3s` | SDK HTTP timeout; 100ms..30s / 有界超时 |
| `compression` | `COMPRESSION` | `gzip` | `gzip` or `none` |
| `max-queue-size` | `MAX_QUEUE_SIZE` | `2048` | 1..65536 admitted records INCLUDING queued and in-flight, not bytes or precommit callbacks / 接纳后的队列及在途记录，不按字节，不包括提交前回调 |
| `max-export-batch-size` | `MAX_EXPORT_BATCH_SIZE` | `256` | 1..min(4096,queue size) / 每批上限 |
| `schedule-delay` | `SCHEDULE_DELAY` | `1s` | 10ms..60s; SDK batch schedule / 批处理调度间隔 |
| `shutdown-timeout` | `SHUTDOWN_TIMEOUT` | `5s` | 100ms..30s; maximum caller wait for graceful drain / 退出等待上限 |
| `retry-max-attempts` | `RETRY_MAX_ATTEMPTS` | `3` | 1..5 INCLUDING first request; 1 disables retry / 含首次请求，1 为不重试 |
| `retry-initial-backoff` | `RETRY_INITIAL_BACKOFF` | `200ms` | 1ms..5s, no greater than max backoff / 初始退避 |
| `retry-max-backoff` | `RETRY_MAX_BACKOFF` | `1s` | 1ms..10s, SDK exponential multiplier 2 with jitter / 最大退避 |
| `event-allowlist` | `EVENT_ALLOWLIST` | all 9 catalog names | Comma-separated exact names; empty exports none; unknown name fails startup / 精确事件名，空为不导出，未知值启动失败 |
| `namespace-allowlist` | `NAMESPACE_ALLOWLIST` | empty | Empty = all including private; otherwise up to 1000 positive numeric namespace IDs, comma-separated / 空为全部（含私有），否则最多 1000 个正整数 ID |

Related existing Resource settings: `spring.application.name` (`skillhub`, environment `SPRING_APPLICATION_NAME`), `skillhub.observability.service-version` (`SKILLHUB_SERVICE_VERSION`, `unknown`), `skillhub.observability.service-environment` (`SKILLHUB_SERVICE_ENVIRONMENT`, `local`; release Compose `production`). These strings are bounded to 256 code units. Do not put credentials in service identity. / 沿用既有服务标识配置；不要在服务标识中放凭证。

A generic OTLP base endpoint normally appends a signal path in OTel autoconfiguration; THIS setting is Logs-specific, uses the given URL unchanged, and requires an explicit non-root path. Invalid enabled configuration fails startup without echoing endpoint/header values. Localhost/private-network receivers are valid. Destination is trusted operator configuration, not an end-user setting: use egress firewall/NetworkPolicy and DNS controls to allow only the authorized receiver and block cloud metadata, including aliases/rebinding. The managed 1.31 OkHttp sender follows redirects and exposes no redirect switch on its public Logs exporter builder: configure the receiver not to redirect and enforce destination/HTTPS restrictions at egress. Literal validation is not a complete SSRF firewall. / 本配置不是通用 base endpoint，不追加信号路径；允许自托管内网目标。1.31 发送器会跟随重定向，公开 builder 无关闭开关，接收端应禁止重定向，并配合出口/DNS/HTTPS 策略；字面量校验不能替代防火墙。

<a id="production-integration"></a>

## Production integration / 生产接入

### Retrieve and apply / 获取与应用

使用包含本功能的同一版本脚本、Compose 和镜像。正式发布后把 `REF`、`VERSION` 固定为同一发布标签。开发验证可使用 `main` / `edge`。脚本的 `--version <tag>` 默认从同名 Git 标签取文件；显式 `--ref` 优先。浮动镜像别名需要显式指定文件版本。

```sh
REF=main
VERSION=edge
RUNTIME_HOME="$HOME/skillhub-runtime"
curl -fsSL "https://raw.githubusercontent.com/iflytek/skillhub/$REF/scripts/runtime.sh" -o runtime.sh
sh runtime.sh ps --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
# 在仓库外编辑 $RUNTIME_HOME/.env.release：数据库、存储、管理员和公开地址。
chmod 600 "$RUNTIME_HOME/.env.release"
sh runtime.sh up --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
```

默认 `SKILLHUB_BUSINESS_LOGS_ENABLED=false`，不启动 Collector，不创建业务日志网络，也不向接收端发送请求。已部署实例升级时先拉取包含本功能的镜像，再用 `up` 重建。仅 `restart` 不读取新的 Compose 环境变量。

已有兼容 OTLP/HTTP protobuf Logs 接收端时，在部署侧 `.env.release` 配置过滤范围与请求头，然后执行：

```sh
sh runtime.sh business-logs-enable --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION" \
  --logs-endpoint https://receiver.example.com/v1/logs
# 同一 Docker 主机上的接收容器可使用已有私有网络；脚本自动检查或创建网络并重建 server。
# sh runtime.sh business-logs-enable ... --logs-network receiver-private --logs-endpoint http://receiver:4318/v1/logs
```

`--logs-endpoint` 是完整 Logs URL，原样使用，通常以 `/v1/logs` 结尾。已有远端接收器不需要本地 Collector。容器接收器在其自身部署中声明共享的外部网络；SkillHub 侧由小型 `compose.release.business-logs.yml` 自动接入。正常 `up` 会保留这个 overlay。每个实例使用独立网络名与项目名。

所有请求头保留在部署侧。应用的 `SKILLHUB_BUSINESS_LOGS_HEADERS` 是 URL 编码的 `name=value` 对，以逗号分隔。`+` 解码为空格；逗号、等号、百分号和字面量加号需编码。真实值不进入仓库、命令行、截图或共享日志。不要把加载了密钥的 `docker compose config` 输出到日志。跨信任边界使用 HTTPS 与接收端鉴权。私有 CA 使用部署提供的 JVM truststore；mTLS 可由本地 Collector / 代理终止。

Docker Secret 也可挂载为 `/run/secrets/skillhub.observability.business-logs.headers`，并通过部署 overlay 设置 `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`。同时保持 `SKILLHUB_BUSINESS_LOGS_HEADERS` 为空。Secret 文件放在仓库外。

### Optional production Collector / 可选生产 Collector

此路径使用独立 Compose 项目、外部私有网络、128 MiB 内存上限和 0.5 CPU。Collector 不发布主机端口。生产配置只有明确的 `otlphttp` 下游出口，没有 JSON 文件存储。

```sh
sh runtime.sh business-logs-up --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
# 首次运行创建 business-logs/.env（0600），提示填写下游后退出，不启动半配置的 Collector。
# 在部署侧编辑 $RUNTIME_HOME/business-logs/.env：
# SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_ENDPOINT=https://receiver.example.com
# SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_HEADERS='{}'
# 有鉴权时把 headers 改成部署侧 JSON 对象，例如 Authorization 键；不在命令行传值。
sh runtime.sh business-logs-up --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
sh runtime.sh business-logs-ps --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
```

下游设置是 OTLP base URL；Collector 自动追加 `/v1/logs`。应用指向 `http://business-logs-collector:4318/v1/logs`。脚本从同一 `REF` 获取根目录下的 `compose.business-logs-collector.yml`、`business-logs-collector.yaml` 和网络 overlay，自动接网并重建 server。下游负责持久存储、查询、访问控制和保留期。默认模板使用内存队列；生产容量与持久发送队列由部署方按接收系统配置。

更新 `.env.release` 后，重跑 `business-logs-enable` 应用地址、过滤或指标配置。更新 `business-logs/.env` 后，重跑 `business-logs-up` 应用下游配置。脚本保留已有文件中的密钥，不覆盖 `.env`。需要自定义 Collector 管道时，维护部署方自己的 Compose / 配置文件；脚本会更新下载的模板。

`--aliyun` 使用扁平 OSS 根目录。发布流程须将上述三个新文件与原有 `runtime.sh`、`compose.release.yml`、`.env.release.example` 一起上传，版本与镜像一致。OSS 根目录不是按 Git ref 划分的版本仓库，发布方负责匹配。

### Produce, query and stop / 产生、查询与停止

以超级管理员登录，发布一个新版本，或通过 `/api/v1/skills/<namespace>/<slug>/download` 下载已发布技能。没有历史补发。接收端按 `skillhub.event.name=skill.publication.effective` / `skill.download.prepared` 查询新时间窗口，查看 `skillhub.skill.id`、`skillhub.namespace.id`、`log.record.uid`。OTLP 是写入协议，没有统一的读 API；生产查询使用配置的下游系统自身的查询界面或 API。下文的本地路径提供可直接执行的 JSON 查询。

```sh
sh runtime.sh business-logs-logs --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
# Ctrl-C 退出跟随日志。检查固定告警和接收端健康，不输出部署密钥。
sh runtime.sh business-logs-disable --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
sh runtime.sh business-logs-down --home "$RUNTIME_HOME" --ref "$REF" --version "$VERSION"
# 再启动：已有 SkillHub 依赖先 up，再 business-logs-up；已有下游数据和网络保留。
```

`business-logs-disable` 重建 server 并关闭导出。`business-logs-down` 只停止可选 Collector；单独停止它时应用仍会尝试导出。`down` 停止 SkillHub，保留数据卷。已配置业务日志接收端或可选 Collector 时，`clean` 也保留部署环境、密钥和 Collector 文件。彻底删除前先停两个项目，备份部署配置，再由运维显式删除自己创建的项目和网络。

### Helm endpoint and Secret / Helm 接入

复用已有接收器，不安装 Collector 子 Chart。先在目标 namespace 创建 Secret：`kubectl create secret generic business-logs-credentials --from-file=headers=/secure/otlp-headers.txt -n skillhub`。文件内容为应用使用的 URL 编码头，不是 Collector 的 JSON 格式。此文件由部署方管理。

```yaml
server:
  actuatorExposureInclude: health,info,metrics,prometheus
  businessLogs:
    enabled: true
    endpoint: https://receiver.example.com/v1/logs
    headersSecretRef:
      name: business-logs-credentials
      key: headers
  extraEnv:
    - name: SKILLHUB_BUSINESS_LOGS_NAMESPACE_ALLOWLIST
      value: "7,12"
```

将片段保存为部署侧 `business-logs-values.yaml`。执行 `helm upgrade --install skillhub charts/skillhub -n skillhub -f production-values.yaml -f business-logs-values.yaml`，再执行 `kubectl rollout status deployment/skillhub-server -n skillhub`。按上面的方式产生新事件并在下游查询。Secret 轮换后执行 `kubectl rollout restart deployment/skillhub-server -n skillhub`。停用时将 `enabled` 改为 `false` 后再次 upgrade；现有 Secret 和下游数据保留。其它预算、过滤和 Resource / Trace 配置使用 `server.extraEnv`，不重复内建变量。

<a id="local-trial"></a>

## Local trial / 本地试用

需要 Docker Compose、JDK 21、Maven wrapper 和 jq。从源码根目录执行。示例 Collector 固定为 contrib `0.111.0`，仅发布回环地址。JSON 追加写入项目独立的本地命名卷，没有保留策略，仅用于临时验证。

### Host Java process / 主机 Java 进程

```sh
make dev
make business-logs-demo-up
make dev-server
```

`business-logs-demo-up` 保存本地试用标记。`make dev-server` 自动使用 `http://127.0.0.1:4318/v1/logs` 并打开业务日志；无需编辑应用源码或手工设置接收地址。后端已运行时重新启动它。需要其它端口时，对整个试用过程统一设置 `SKILLHUB_BUSINESS_LOGS_DEMO_PORT`；需要并行实例时还统一设置 `SKILLHUB_BUSINESS_LOGS_DEMO_PROJECT`、`SKILLHUB_BUSINESS_LOGS_NETWORK` 和 `BUSINESS_LOGS_DEMO_STATE_DIR`。

### Containerized backend / 容器后端

先用生产路径启动一个独立试用实例。它使用同一份 release Compose 和应用配置。然后从源码根目录设置该实例的环境文件和项目名：

```sh
export BUSINESS_LOGS_BACKEND=container
export BUSINESS_LOGS_BACKEND_PROJECT=skillhub-local-trial
export BUSINESS_LOGS_BACKEND_ENV_FILE="$HOME/skillhub-local-trial/.env.release"
# BUSINESS_LOGS_BACKEND_PROJECT 必须与已启动实例的 COMPOSE_PROJECT_NAME 相同。
make business-logs-demo-up
```

Makefile 自动创建试用私有网络，通过同一个 release 网络 overlay 重建 server，并使用 `http://business-logs-collector:4318/v1/logs`。保留这些变量，以便后续命令操作同一实例。需要指定不同版本的基础 Compose 时设置 `BUSINESS_LOGS_BACKEND_COMPOSE`。此模式的启用是本地试用状态；正常生产 `runtime.sh up` 仍以 `.env.release` 为准。

### Query and cleanup / 查询与清理

在本地界面登录管理员，发布新版本或下载已发布技能，然后执行：

```sh
make business-logs-demo-status
make business-logs-demo-records
make business-logs-demo-verify
# 默认验证 skill.download.prepared；也可指定其它已产生的事件。
make business-logs-demo-verify BUSINESS_LOGS_EXPECT_EVENT=skill.publication.effective
# 仅取 JSON 行进行结构化查询：
make -s business-logs-demo-records | jq -r '.resourceLogs[].scopeLogs[].logRecords[] | (.attributes | map({key:.key,value:(.value.stringValue // .value.intValue // .value.boolValue)}) | from_entries) as $a | [$a["skillhub.event.name"], $a["skillhub.skill.id"], $a["log.record.uid"]] | @tsv'
make business-logs-demo-down
```

`records` 输出实际 JSON 行；`verify` 在缺少预期事件时返回非零。`up` 重建 Collector 以应用配置，命名卷与追加模式保留已有记录。`down` 停止进程，再次 `up` 后仍可查询原记录。主机进程模式停止后还需重启 Java 进程以关闭导出；容器模式自动重建并关闭导出。试用结束后及时停止；需要保留的记录先保存到仓库外。彻底删除时先停止后端，再用该试用项目名执行 `docker compose -p <demo-project> -f docs/examples/business-event-logs/compose.yaml down -v`，显式删除试用卷，最后删除已无容器连接的试用网络。命名卷不是生产存储或可靠投递保证。

### Metrics enablement and admin query / 指标启用与管理员查询

默认只暴露 `health,info`。即使已有业务事件，管理员访问 metrics / prometheus 也会得到 404。Compose 在 `.env.release` 设置 `SKILLHUB_ACTUATOR_EXPOSURE_INCLUDE=health,info,metrics,prometheus`，然后通过 `business-logs-enable`（收集启用时）或正常 `up` 重建 server。Helm 使用上面的 `server.actuatorExposureInclude`。主机 Java 进程则在启动前导出同名变量。不要使用 `*`。

源代码验证先执行 `make build-backend`，再执行 `docker build -t skillhub-server:local -f server/Dockerfile server`，并让 runtime 的 `--server-image skillhub-server --version local --ref <对应源码ref>` 指向新镜像。正式镜像升级使用发布标签，不依赖旧镜像中的新配置。

可复用浏览器中的超级管理员会话。命令行查询时，在仓库外准备仅本人可读的登录 JSON 文件，内容为管理员 username / password。以下命令不打印密码或 Cookie：

```sh
BASE=http://127.0.0.1:8080
QUERY_DIR=$(mktemp -d)
chmod 700 "$QUERY_DIR"
# LOGIN_JSON=/secure/admin-login.json，文件 mode 600；由部署方创建。
curl -fsS -c "$QUERY_DIR/cookies" "$BASE/api/v1/auth/methods" -o /dev/null
CSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$QUERY_DIR/cookies")
curl -fsS -b "$QUERY_DIR/cookies" -c "$QUERY_DIR/cookies" -H "X-XSRF-TOKEN: $CSRF" \
  -H 'Content-Type: application/json' --data-binary @"$LOGIN_JSON" "$BASE/api/v1/auth/local/login" -o /dev/null
curl -fsS -b "$QUERY_DIR/cookies" "$BASE/actuator/metrics/skillhub.business.logs.enqueued"
curl -fsS -b "$QUERY_DIR/cookies" "$BASE/actuator/prometheus"
rm -rf "$QUERY_DIR"
```

超级管理员可查询这两个指标入口。普通用户返回 403，匿名请求返回 401。Prometheus 沿用已有 `AUDITOR` 权限；单项 metrics 仅超级管理员。未启用业务导出时不会创建这些业务计数器；启用但未产生事件时计数为零。计数器是当前进程累计值，重启后归零，含义见下表。计数器反映应用接纳 / SDK 确认，不替代下游实收查询。

## Reliability, metrics and troubleshooting / 可靠性、指标与排障

Business threads only map fixed bounded fields, capture context, register a transaction callback and attempt nonwaiting SDK admission. No DB enrichment or network IO is performed by the adapter on business threads. Standard BatchLogRecordProcessor handles a bounded record queue; a conservative admission permit counts queued PLUS in-flight records to expose exact queue-full drops without SDK internals. Only SDK exporters implement network retry (retryable HTTP status/IO); no caller-runs fallback. Batch export budget is `(timeout + retry-max-backoff) * retry-max-attempts + 1s`; receiver `Retry-After` and sender behavior still depend on the managed SDK. / 业务线程仅做固定字段映射及非等待入队，不发送网络请求；队列预算包含在途，重试交标准 SDK。

Each export has an owned completion guard: SDK completion OR the export-budget deadline, whichever wins, releases admission permits exactly once. An abandoned SDK result (including 1.31's truncated non-2xx response-body callback failure) becomes one failed batch at the deadline. Late SDK success/failure cannot add permits or change counters. Deadlines run on one daemon scheduler, canceled tasks are removed, and closing fails outstanding guards and stops the scheduler. This bounds local accounting, not remote cancellation or delivery: a timed-out request might still reach the receiver. / 每批导出由 SDK 完成或预算到期中先发生者结束，许可只释放一次；SDK 遗失完成信号也会计一次失败，迟到结果不重复释放或计数。关闭时结束守卫并停止调度器；本地超时不代表远端已撤销。

Loss is expected on commit-to-listener/admission gaps, crashes, forced exits, queue saturation, timeout or exhausted retry, and incomplete shutdown. No disk spool, outbox, replay, backfill or exactly-once. Ambiguous acknowledgement can duplicate the same UID; deduplicate by `log.record.uid`, not `(skill,version)` (multiple legitimate downloads), request ID or trace ID. Mapping a new domain-event dispatch creates a new UID; idempotency of repeated business operations is unchanged. / 提交到入队间隙、崩溃、满队列及重试耗尽都可能漏报；超时重试可能重复，接收方按 UID 去重，不按技能版本或 Trace 去重。

The queue budget starts at after-commit admission. Before commit, transaction synchronizations retain one bounded mapped snapshot per dispatched event until completion, outside that SDK budget. Avoid unbounded bulk work in one outer transaction; this configuration is not a process-wide heap cap. / 队列预算从提交后接纳开始；提交前每个事件的有界快照由事务回调保留，不计入 SDK 预算。不要在单个外层事务里执行无界批量操作，这不是整个进程的堆内存上限。

Graceful shutdown stops new admission, gives SDK drain a `shutdown-timeout` wait budget, then shuts down the exporter to cancel pending network work. Local cleanup and scheduling add some overhead; this is not a hard-real-time deadline. Remaining records may be lost; daemon completion may continue briefly. This differs deliberately from the draft RFC's discard-unsent-on-disable wording: configuration is NOT hot-reloaded; setting `enabled=false` takes effect on restart, whose old process gets a bounded drain opportunity. Already accepted receiver data cannot be recalled. / 正常退出停止接纳并限时等待排空，另有本地清理和调度开销，不是硬实时期限；剩余记录可丢失。关闭开关需重启，旧进程会限时发送，不承诺立即撤销已入队或接收的数据。

OTel 1.31 treats all HTTP 2xx responses as full success and does not expose `partial_success` rejected counts. `exported` means SDK/receiver ACK, NOT downstream storage confirmation. Do not replay whole partially accepted batches. Partial rejection diagnostics and durable retries belong at the receiver; this change does not upgrade the BOM. / 1.31 不读取部分拒收数量；成功指标不代表最终落库，不整批重发部分接收的数据。

Micrometer names on the existing protected Actuator metrics surface (Prometheus renders dots as underscores and appends `_total` for counters, e.g. `skillhub_business_logs_enqueued_total`):

| Metric | Unit / 含义 |
| --- | --- |
| `skillhub.business.logs.enqueued` | Records admitted / 已接纳记录 |
| `skillhub.business.logs.dropped{reason=queue_full}` | Records refused because outstanding budget is full / 满队列丢弃 |
| `skillhub.business.logs.dropped{reason=shutdown}` | New records refused during closing / 退出时拒绝新记录 |
| `skillhub.business.logs.errors` | Adapter mapping/admission/lifecycle exceptions and contained exporter dispatcher exceptions, swallowed / 适配器及已隔离的导出回调异常（不传播） |
| `skillhub.business.logs.export.failures` | Batches whose SDK export fails/throws, exceeds the export budget, or is still outstanding at close / 最终失败、超时或关闭时仍未结束的批次 |
| `skillhub.business.logs.exported` | Records in SDK-successful batches; may include receiver partial rejections / SDK 成功记录，含部分拒收盲区 |

No per-user/skill/namespace metric tags. Adapter diagnostics are fixed text, at most once per minute per pipeline, without exception messages, headers or event payloads: `Business Logs telemetry failed; inspect skillhub.business.logs metrics and receiver health`. Raw SDK diagnostics can echo receiver error bodies or transport exception text containing credentials; throttling or ERROR level alone is NOT safe. Shipped `logback-spring.xml` therefore installs `BusinessLogsDiagnosticsFilter`, which drops `io.opentelemetry.*` / `okhttp3.*` events (including those routed from JUL by Spring Boot) only on the business exporter's own network threads, and sets `io.opentelemetry.sdk.logs` (used only by this pipeline) to OFF. Shared exporter loggers keep their normal levels, so existing Trace export diagnostics such as `Failed to export spans ... 401` are unchanged whether business Logs are on or off. Custom logging configurations must retain the filter and the `sdk.logs` OFF rule. / 指标无高基数标签；适配器每分钟最多一次固定文字。原始 SDK 诊断可能回显凭证，ERROR 级别也不安全；默认配置只在业务 exporter 自己的网络线程上丢弃 OTel/OkHttp 诊断（含 JUL 桥接），并关闭仅本管道使用的 sdk.logs。共享 exporter 记录器级别不变，Trace 导出错误提示无论业务 Logs 开关均保持原样。自定义日志配置需保留该过滤器和 sdk.logs 规则。

Log filtering alone does not contain OTel 1.31's uncaught asynchronous callback failures (for example, an HTTP 401 with a malformed chunked body). The production business exporter is constructed in a private ThreadGroup: only its `okhttp-dispatch-*` threads' uncaught exceptions are discarded without rendering or forwarding the throwable. This increments `errors` and retains the same fixed warning; the existing completion deadline separately counts one failed batch and recovers permits. The JVM default handler is never changed. Other threads, including same-named threads outside that group and shared OkHttp housekeeping threads, delegate to their normal parent/default handler unchanged. Pipeline shutdown cancels its dispatcher executor; containment stays attached until its last callback exits, then no owned dispatcher remains. An inert group can outlive the pipeline for shared OkHttp housekeeping, but delegates those exceptions and only weakly references metrics. / 仅过滤日志不足以阻止 1.31 异步回调异常直写 stderr。业务 exporter 在私有线程组内构建，只隔离该组导出 dispatcher 的未捕获异常，计 errors 并保留固定告警；预算到期另计一批失败并恢复许可。不改 JVM 默认 handler，其它线程仍按原处理链执行。关闭时取消导出线程，隔离保留至回调退出；共享 housekeeping 可保留空闲边界组，但不受抑制且不强引用指标。

This safe-diagnostics boundary covers this dedicated business Logs pipeline with the shipped diagnostics filter defaults and the verified managed 1.31 sender. It is not process-wide exception redaction: unrelated Trace/agent exporters keep their own (unchanged) diagnostics, and application failures, custom logging/SDK overrides, agents intercepting exceptions, heap dumps and receiver-side logs are outside it. SDK upgrades must reverify that the sender reports results on dispatcher threads owned by the private group, plus the captured stdout/stderr and Trace-coexistence tests; no BOM upgrade is included here. / 安全诊断范围限于默认日志规则和已验证的 1.31 业务管道，不是整个 JVM 的异常脱敏；Trace 等其它 exporter 保留各自原有诊断，应用异常、配置覆盖、Agent、堆转储和接收端日志不在范围内。升级 SDK 必须重验回调线程归属、输出回归与 Trace 共存测试。

Troubleshoot using the protected Actuator counters above, this fixed warning, receiver-side status/health, and independent DNS/network/TLS/auth checks with secret-safe tools. If raw SDK diagnostics are indispensable, first remove production credentials or reproduce with synthetic credentials in an isolated environment, restrict log access/retention, and knowingly accept disclosure risk. Business exporter raw diagnostics are dropped by the filter regardless of logger level; reproduce with a deployment-owned logging config that omits `BusinessLogsDiagnosticsFilter` (and, for processor diagnostics, sets `io.opentelemetry.sdk.logs` to DEBUG), then restore the shipped config and restart; do not share unredacted SDK output. / 排障优先使用受保护指标、固定告警及接收端健康，不输出 Secret。确需 SDK 原始细节时，在隔离环境用假凭证复现，限制访问与保留并明确接受泄露风险；业务 exporter 的原始诊断不受日志级别影响，需用部署方自有日志配置临时去掉过滤器复现，结束后恢复默认配置并重启，勿分享未脱敏输出。

| Symptom / 现象 | Check / 排查 |
| --- | --- |
| Metrics 404 / 指标 404 | 显式启用 exposure 列表并重建镜像/容器；指标权限与导出开关独立。默认 404 是预期行为 |
| Demo no records / 本地无记录 | 先运行 demo-up；主机模式重启 make dev-server；容器模式确认项目名、env 文件与共享网络，避免把容器 localhost 当接收器 |
| Startup fails / 启动失败 | Enabled without endpoint, bad full URL, invalid bounds/allowlist/header encoding; do not paste config secrets / 检查目标、范围、编码，不分享 Secret |
| No records, no exporter metrics / 无记录或指标 | Default off? Environment explicitly forwarded? Container recreated? No historical backfill / 开关、透传、重建、新事件 |
| Enqueued but failed / 已入队但失败 | `/v1/logs`, HTTP/protobuf (not gRPC 4317), DNS/network/TLS/auth and receiver Logs pipeline / 路径、协议、网络及鉴权 |
| Drops / 丢弃 | Outstanding queue + in-flight, receiver throughput; tune bounded budgets, not an unbounded queue / 检查接收吞吐与在途占用，不改无界 |
| Missing one approval fact / 少一条批准事实 | Source vs target namespace allowlist; content vs metadata review; transaction rollback / 来源与目标空间过滤、内容与元数据审核区别、回滚 |
| Duplicate counts / 重复 | UID dedup, receiver ack ambiguity, agent bridges; never bridge ordinary app logs into this scope / UID 去重、确认歧义、Agent 日志桥接 |
| Exported count exceeds sink count / 指标大于入库量 | 2xx partial rejection blind spot, downstream filters/retention; inspect receiver / 部分拒收盲区及下游过滤 |

Trace modes remain independent: `none`/`external-agent` still forbid the TRACE endpoint, not the business Logs endpoint. External agents must not bridge/re-export this dedicated scope or attach a second business Logs processor; verify real deployment agent plugins for duplicates. No actual external-agent installation is implied by application configuration tests. / Trace 限制仅针对 Trace 目标；部署方须检查 Agent 插件是否重复导出业务记录。

For executed verification and explicit remaining limits, see [acceptance matrix](../openspec/changes/add-otlp-business-event-logs/acceptance.md).
