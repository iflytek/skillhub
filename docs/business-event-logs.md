# Business-event Logs over OTLP / OTLP 业务事件日志

Default off. This is lossy analytics, NOT audit, billing, notification or installation confirmation. Use the official server image built from this change; no custom application code, vendor SDK or Collector is required. The receiver must support OTLP/HTTP protobuf Logs. / 默认关闭；用于有损分析，不替代审计、计费、可靠通知或安装确认。只需对应版本官方镜像与运维配置，Collector 可选。

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

## Direct receiver / 直连部署

For an OTLP/HTTP Logs-compatible receiver, set these on the official server container; tracing may remain off:

```dotenv
SKILLHUB_BUSINESS_LOGS_ENABLED=true
SKILLHUB_BUSINESS_LOGS_ENDPOINT=https://analytics.example.com/v1/logs
SKILLHUB_BUSINESS_LOGS_NAMESPACE_ALLOWLIST=7,12
SKILLHUB_BUSINESS_LOGS_EVENT_ALLOWLIST=skill.publication.effective,skill.download.prepared,skill.review.approved
```

Use the release Compose file plus deployment-controlled environment/Secret injection. Never commit real `HEADERS`; e.g. its format is `Authorization=Bearer%20<deployment-secret>` (encode comma, equals, percent and literal plus as needed; `+` decodes to space). Do not run `docker compose config` into shared logs with secrets loaded. Across trust boundaries require HTTPS and receiver-supported authentication. JVM default trusted certificates apply; for private CAs use a deployment-provided JVM truststore, not an insecure TLS bypass. mTLS can be terminated by a local Collector/proxy. / 通过部署侧 Secret 注入请求头；跨信任边界必须 HTTPS/鉴权，私有 CA 使用 JVM truststore；不要关闭 TLS 校验，不要公开渲染含密钥的 Compose 配置。

Docker Secret alternative (no credential in Compose interpolation): mount a Secret whose target filename is `skillhub.observability.business-logs.headers`, and set `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`. Spring config-tree keys override the lower-priority application default. Do not simultaneously define a nonempty `SKILLHUB_BUSINESS_LOGS_HEADERS`. Manage the actual Secret file outside the repository and protect access to Actuator/config diagnostics. / 可用 Docker Secret + Spring configtree，文件名即完整属性名；真实 Secret 文件放仓库外，不与非空环境变量重复设置。

Helm exposes `server.businessLogs.enabled`, `endpoint`, and `headersSecretRef.{name,key}`. The chart references an EXISTING Secret and does not create inline header secrets. Use existing `server.extraEnv` for every tuning/filter/Resource/Trace environment variable in the table. Do not duplicate the three built-in env names in extraEnv. / Helm 提供开关、目标及现有 Secret 引用；预算/过滤等使用 `server.extraEnv`，不要重复内建环境变量。

```yaml
server:
  businessLogs:
    enabled: true
    endpoint: https://analytics.example.com/v1/logs
    headersSecretRef:
      name: business-logs-credentials
      key: headers
  extraEnv:
    - name: SKILLHUB_BUSINESS_LOGS_NAMESPACE_ALLOWLIST
      value: "7,12"
```

## Optional Collector and real receive/query example / 可选 Collector 实收查询

The pinned example is `docs/examples/business-event-logs/compose.yaml` (Collector contrib `0.111.0`, 128 MiB / 0.5 CPU, only local HTTP port). It writes OTLP JSON to an ephemeral file, NOT production durable storage. Do not expose its unauthenticated port externally. It is separate from release Compose and is not started by default. / 示例仅本地验证，JSON 文件随容器删除，不是生产持久存储；无鉴权端口不得对外开放，不默认随正式部署启动。

From repository root, with port 4318 free:

```bash
docker compose -f docs/examples/business-event-logs/compose.yaml up -d
# Host-run app: SKILLHUB_BUSINESS_LOGS_ENDPOINT=http://127.0.0.1:4318/v1/logs
# Docker Desktop server container: use host.docker.internal:4318 instead of localhost.
# Linux containers: use a shared private network / explicit host-gateway routing.
# Enable business Logs on the app; publish/approve or request an authorized published download.
docker cp skillhub-1009-collector:/business-logs.json ./business-logs.jsonl
jq -r '.resourceLogs[].scopeLogs[].logRecords[] | (.attributes | map({key:.key,value:(.value.stringValue // .value.intValue // .value.boolValue)}) | from_entries) as $a | [.timeUnixNano, $a["skillhub.event.name"], $a["skillhub.skill.id"], $a["log.record.uid"]] | @tsv' business-logs.jsonl
docker compose -f docs/examples/business-event-logs/compose.yaml down
```

For a source-verifiable run of the REAL production exporter wiring against the same Collector config, without bootstrapping business accounts:

```bash
cd server
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -pl skillhub-app -am test \
  -Dtest=BusinessLogsCollectorTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dskillhub.test.evidence-dir=/absolute/operator-owned/evidence
```

Create that evidence directory first. The test uses named disposable containers and copies the actual sink output to `collector-received.jsonl`; apply the same jq query. It exercises all nine catalog records in all three Trace configurations (SDK trace sampling 0), checks IDs, UID uniqueness, field exclusion and receiver ACK metrics. This is real exporter/receiver integration, not a claim of a complete browser/CLI deployment smoke or loading an external Java agent. / 该测试使用真实应用导出装配及真实 Collector，保存实收结果，可用相同 jq 查询；不等同于完整 Web/CLI 部署验收或真实 Java Agent 注入测试。

Collector is optional. Production operators choose exporters, retention, TLS/auth, memory limiter and persistent sending queues appropriate to their sink. Collector persistence cannot recover records lost before application admission. File demo has no retention policy and must be stopped after verification. / 生产出口、保留策略、持久队列由运维选择；Collector 持久化不能补回应用侧漏报，示例用后停止。

## Reliability, metrics and troubleshooting / 可靠性、指标与排障

Business threads only map fixed bounded fields, capture context, register a transaction callback and attempt nonwaiting SDK admission. No DB enrichment or network IO is performed by the adapter on business threads. Standard BatchLogRecordProcessor handles a bounded record queue; a conservative admission permit counts queued PLUS in-flight records to expose exact queue-full drops without SDK internals. Only SDK exporters implement network retry (retryable HTTP status/IO); no caller-runs fallback. Batch export budget is `(timeout + retry-max-backoff) * retry-max-attempts + 1s`; receiver `Retry-After` and sender behavior still depend on the managed SDK. / 业务线程仅做固定字段映射及非等待入队，不发送网络请求；队列预算包含在途，重试交标准 SDK。

Each export has an owned completion guard: SDK completion OR the export-budget deadline, whichever wins, releases admission permits exactly once. An abandoned SDK result (including 1.31's truncated non-2xx response-body callback failure) becomes one failed batch at the deadline. Late SDK success/failure cannot add permits or change counters. Deadlines run on one daemon scheduler, canceled tasks are removed, and closing fails outstanding guards and stops the scheduler. This bounds local accounting, not remote cancellation or delivery: a timed-out request might still reach the receiver. / 每批导出由 SDK 完成或预算到期中先发生者结束，许可只释放一次；SDK 遗失完成信号也会计一次失败，迟到结果不重复释放或计数。关闭时结束守卫并停止调度器；本地超时不代表远端已撤销。

Loss is expected on commit-to-listener/admission gaps, crashes, forced exits, queue saturation, timeout or exhausted retry, and incomplete shutdown. No disk spool, outbox, replay, backfill or exactly-once. Ambiguous acknowledgement can duplicate the same UID; deduplicate by `log.record.uid`, not `(skill,version)` (multiple legitimate downloads), request ID or trace ID. Mapping a new domain-event dispatch creates a new UID; idempotency of repeated business operations is unchanged. / 提交到入队间隙、崩溃、满队列及重试耗尽都可能漏报；超时重试可能重复，接收方按 UID 去重，不按技能版本或 Trace 去重。

The queue budget starts at after-commit admission. Before commit, transaction synchronizations retain one bounded mapped snapshot per dispatched event until completion, outside that SDK budget. Avoid unbounded bulk work in one outer transaction; this configuration is not a process-wide heap cap. / 队列预算从提交后接纳开始；提交前每个事件的有界快照由事务回调保留，不计入 SDK 预算。不要在单个外层事务里执行无界批量操作，这不是整个进程的堆内存上限。

Graceful shutdown stops new admission, gives SDK drain a `shutdown-timeout` wait budget, then shuts down the exporter to cancel pending network work. Local cleanup and scheduling add some overhead; this is not a hard-real-time deadline. Remaining records may be lost; daemon completion may continue briefly. This differs deliberately from the draft RFC's discard-unsent-on-disable wording: configuration is NOT hot-reloaded; setting `enabled=false` takes effect on restart, whose old process gets a bounded drain opportunity. Already accepted receiver data cannot be recalled. / 正常退出停止接纳并限时等待排空，另有本地清理和调度开销，不是硬实时期限；剩余记录可丢失。关闭开关需重启，旧进程会限时发送，不承诺立即撤销已入队或接收的数据。

OTel 1.31 treats all HTTP 2xx responses as full success and does not expose `partial_success` rejected counts. `exported` means SDK/receiver ACK, NOT downstream storage confirmation. Do not replay whole partially accepted batches. Partial rejection diagnostics and durable retries belong at the receiver; this change does not upgrade the BOM. / 1.31 不读取部分拒收数量；成功指标不代表最终落库，不整批重发部分接收的数据。

Micrometer names on the existing protected Actuator metrics surface (Prometheus renders dots as underscores):

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
| Startup fails / 启动失败 | Enabled without endpoint, bad full URL, invalid bounds/allowlist/header encoding; do not paste config secrets / 检查目标、范围、编码，不分享 Secret |
| No records, no exporter metrics / 无记录或指标 | Default off? Environment explicitly forwarded? Container recreated? No historical backfill / 开关、透传、重建、新事件 |
| Enqueued but failed / 已入队但失败 | `/v1/logs`, HTTP/protobuf (not gRPC 4317), DNS/network/TLS/auth and receiver Logs pipeline / 路径、协议、网络及鉴权 |
| Drops / 丢弃 | Outstanding queue + in-flight, receiver throughput; tune bounded budgets, not an unbounded queue / 检查接收吞吐与在途占用，不改无界 |
| Missing one approval fact / 少一条批准事实 | Source vs target namespace allowlist; content vs metadata review; transaction rollback / 来源与目标空间过滤、内容与元数据审核区别、回滚 |
| Duplicate counts / 重复 | UID dedup, receiver ack ambiguity, agent bridges; never bridge ordinary app logs into this scope / UID 去重、确认歧义、Agent 日志桥接 |
| Exported count exceeds sink count / 指标大于入库量 | 2xx partial rejection blind spot, downstream filters/retention; inspect receiver / 部分拒收盲区及下游过滤 |

Trace modes remain independent: `none`/`external-agent` still forbid the TRACE endpoint, not the business Logs endpoint. External agents must not bridge/re-export this dedicated scope or attach a second business Logs processor; verify real deployment agent plugins for duplicates. No actual external-agent installation is implied by application configuration tests. / Trace 限制仅针对 Trace 目标；部署方须检查 Agent 插件是否重复导出业务记录。

For executed verification and explicit remaining limits, see [acceptance matrix](../openspec/changes/add-otlp-business-event-logs/acceptance.md).
