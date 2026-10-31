# Acceptance evidence — OTLP business-event Logs

Base: `9ebb99226dd1b3e1d4fd8dd3e36baae70a4ed273`; branch `feat/otlp-business-events-1009`. These are executed results, not a claim of a deployed browser/CLI production stack. Raw logs and receiver output are retained in the implementation worker's evidence directory; the worker report gives absolute paths. / 以下为实际运行结果，不等同完整生产部署验收；原始输出路径见 worker report。

## Event/producer matrix

`BusinessLogsProducerPostgresTest` uses actual Spring service proxies, real JPA repositories, Flyway migrations and PostgreSQL 16. Storage/scanner/avatar/notification/audit/compensation collaborators and package-validation/metadata-parsing collaborators are isolated mocks; business state and transaction boundaries are real. Each row has a committed scenario and a physical-transaction rollback scenario. Physical rollback emits zero records; `PROPAGATION_NESTED` savepoint rollback is excluded (it can export after outer commit). `REQUIRES_NEW` commit exports independently of outer rollback. / 服务代理、仓储、迁移和物理事务为真实依赖；物理事务回滚不导出，但保存点回滚明确不保证抑制；独立新事务提交不受外层回滚影响。外部协作组件用 mock 隔离，不宣称验证它们的端到端副作用。

| Producer path | Committed facts (exactly) | Rollback | Actor |
| --- | --- | --- | --- |
| Privileged auto-publication | publication effective | 0 | privileged submitting user |
| PRIVATE confirmation | publication effective | 0 | confirmer |
| Content review approval | publication effective + review approved | 0 | actual reviewer, not owner |
| Content review rejection | review rejected | 0 | actual reviewer |
| Initial promotion approval | target publication effective + source promotion approved | 0 | actual approver |
| Promotion update approval | target publication effective + source promotion approved | 0 | actual approver |
| Promotion rejection | source promotion rejected | 0 | actual reviewer |
| Archive | status changed ACTIVE → ARCHIVED | 0 | owner |
| Unarchive | status changed ARCHIVED → ACTIVE | 0 | owner |
| Immediate narrowing | visibility changed to PRIVATE | 0 | requester |
| Metadata-review expansion | visibility changed to PUBLIC, no new publication | 0 | expansion approver |
| Published version yank | version yanked | 0 | admin actor |
| Published download inside outer transaction | download prepared | 0 | anonymous: user type without user.id |

Result: 39 real-service/JPA scenarios passed in `final-focused-3.log`: the 26 commit/rollback scenarios above plus 13 denied-actor cases, one for every path, asserting no exported fact and unchanged persisted skill/version/review/promotion state. Includes PRIVATE publication and review-task rollback assertions, actor checks and sensitive-field exclusion. Dedicated nontransactional real-download-service test separately passed: one anonymous preparation; no facts for review package or denied anonymous/unauthorized PRIVATE access. / 上表 26 个提交/回滚场景及逐路径 13 个拒绝授权场景通过；拒绝时不发事件、持久状态不变。无事务真实下载服务另验证一次匿名准备，以及审核包、匿名/无权限私有请求不发事件。

## Cross-cutting acceptance

| Requirement | Executed assertion | Result / evidence |
| --- | --- | --- |
| TDD | New mapper/config tests compiled before implementation and failed on missing implementation symbols | RED `red-unit.log`; initial GREEN 12 tests in `green-unit-2.log` |
| Mapper boundary | Exact fixed keys; no rejection reason/submitter/unknown event serialization; namespace/event allowlists; anonymous omission; system actor; length limits; reserved built-in publisher | PASS `BusinessEventMapperTest` |
| Default off | No pipeline/listener/provider bean; no default receiver | PASS `BusinessLogsConfigurationTest` |
| Invalid configuration | Enabled+empty URL rejected; metadata/userinfo/query/file URL and bad budgets/event names rejected without echoing supplied URL; URL-encoded headers and control-character rejection | PASS `BusinessLogsConfigurationTest` |
| Capture before commit | Valid unsampled span survives scope end before PostgreSQL commit; Repair 1 additionally seeds distinct MDC/Baggage secrets and checks exported attributes/body/resource | PASS `BusinessLogsTransactionTest`; see Repair 1 evidence below |
| Trace coexistence | Actual production configuration exports 9 records each with none, otel-sdk (sampling=0), external-agent mode | PASS, actual Collector output 27 records / 9 names / 27 unique UIDs |
| Actual OTLP receive/query | Pinned Collector contrib 0.111.0 file exporter; documented jq query run against real received JSON | PASS `collector-received.jsonl`, `collector-query.tsv`, `collector-query-summary.json` |
| Queue full, deterministic | 1000 caller events with blocked exporter; exactly 999 drops; bounded shutdown | PASS final focused run: 12 ms caller loop, 169 ms close for 100 ms wait budget (includes scheduling/cleanup overhead) |
| Live receiver stalled | Pause real Collector; queue=2, batch=1, HTTP timeout=500ms; 1000 caller events; failures counted | PASS final focused run: 10 ms caller loop, 998 drops |
| Live receiver down | Stop real Collector; publishing returns without exception; export failure counter increments | PASS focused run: caller 0 ms |
| Duplicate semantics | HTTP receiver first responds 503 then 200; retry bytes identical and include the same UID; auth appears only in transport header | PASS `BusinessLogsHttpTest`, 2 requests / 1 SDK-exported record |
| Partial rejection blind spot | Real protobuf HTTP 200 partial_success rejected=1; no whole-batch replay; SDK reports success | PASS expected SDK limitation, 1 request / 1 SDK-success counter |
| Resource bounds | Java test fork capped at 512 MiB; Collector Docker inspect verifies 128 MiB and 0.5 CPU; at most 16 record attributes; string cap tested | PASS focused command with `-DargLine=-Xmx512m`; not a throughput/byte-accurate heap benchmark |
| Existing consumers | All affected domain service tests, 20 notification tests and 13 Trace configuration/filter tests | PASS final focused run: 289 total tests, no failures/errors/skips |
| Release config | Actual make target with a synthetic valid fixture (removed afterward); existing release shell regression suite | PASS `validate-release-config.log`, `release-config-tests.log`; no real credential file used |
| Helm/Compose | Helm 3.17.3 lint/render, existing chart contracts; new Python deployment contracts assert default-off, endpoint requirement, Secret reference and env forwarding | PASS 1 chart, 4 Python tests without skips (`business-logs-config-with-helm-final.log`), `helm-contracts.log` |
| Backend packaging | Reactor app+dependencies package with tests explicitly skipped (tests verified separately) | PASS `backend-package-final.log` |
| Local doc/source checks | Changed Markdown relative targets and code-fence pairing; required OpenSpec headings | PASS 11 files, 19 relative targets, 5 requirements / 9 scenarios (`docs-source-check.json`); not a site build or OpenSpec CLI run |

The raw measurement values above are from the named focused run, not promised production latency percentiles. Shutdown has a bounded SDK wait budget plus local cleanup/scheduling overhead. / 延迟数字是该次测试实测，不是生产百分位承诺；退出等待预算之外有本地清理和调度开销。

## Repair 1 — independent review RETURN

Repair base: `894e5860ce19059a27dab8cf94642aba56d33a29`; changes remain uncommitted. Evidence filenames in this section are relative to `evidence/repair1/` in the worker report, not the earlier evidence directory. The original results above remain historical; the current focused result is **299 tests, 0 failures, 0 errors, 0 skips** (193 domain + 106 app; 73 business-Logs tests). The same full focused selector/environment was rerun, including the new `Business*Test` classes. / 本轮在独立评审退回后修复，未提交；本节路径属于 repair1 子目录。之前结果保留为历史，当前定向结果为 299 项全部通过，其中业务日志 73 项。

| Finding / boundary | Executed assertion and change | RED → GREEN evidence |
| --- | --- | --- |
| Abandoned SDK result / permit leak | Production exporter receives HTTP 400, declares 100 body bytes but sends one and closes; export-budget guard fails once, then two HTTP 200 exports succeed. Requests=3, enqueued=3, exported=2, failures=1, drops=0; no restart | RED `red-permit-http.log` (1 test, failure-counter await times out at zero); GREEN `green-repair-focused.log` and `final-focused-green.log`, `BusinessLogsHttpTest` |
| Exactly-once cleanup | Deadline wins over an unfinished result; a second export occupies the recovered capacity; late SDK success OR failure cannot admit a third record or alter the failed outcome. Two parameter cases pass | RED `red-guard-diagnostics-boundaries.log` (2 guard timeouts); GREEN `final-focused-green.log`, `BusinessLogsPipelineTest` |
| SDK credential disclosure | Real 401 protobuf Status echoes synthetic Authorization; real malformed HTTP status line produces a credential-bearing ProtocolException. Shipped Logback package OFF rules suppress raw SDK output; fixed warning + failure counter remain. Captured stdout/stderr checked in BOTH text and async JSON modes; no synthetic credential | RED `red-guard-diagnostics-boundaries.log` (2 log assertions fail with credential output); GREEN `final-focused-green.log`, 4 `BusinessLogsDiagnosticsTest` cases |
| Savepoint contract | Explicitly exclude `PROPAGATION_NESTED` rollback suppression in operator guide/design/spec/acceptance. Real PostgreSQL: nested rollback + outer commit leaves 0 rows but exports 1 record; `REQUIRES_NEW` commit + outer rollback leaves only the independent row and exports only its record. A documentation consistency test covers all four contract files | RED `red-guard-diagnostics-boundaries.log` (documentation assertion fails; both runtime boundary tests already pass); GREEN `final-focused-green.log`, `BusinessLogsTransactionTest` |
| Non-vacuous MDC/Baggage exclusion | Assert distinct sensitive keys/values are actually present at dispatch, retain unsampled span across scope end, then assert neither keys nor values in exported attributes, body or resource | RED `red-guard-diagnostics-boundaries.log` (fixture precondition fails on missing MDC sentinel, not a product disclosure); GREEN `final-focused-green.log`, `BusinessLogsTransactionTest` |
| Deployment regression | New deployment contracts 4/4, no skips; existing Helm configuration and release validation regression suites both pass; no Helm/Compose setting changed | `deployment-business-logs.log`, `deployment-helm-contracts.log`, `deployment-release-contracts.log` |
| Live receiver regression | Real pinned Collector receives 27 records / 9 names / 27 distinct UIDs again. Paused receiver: 1000 caller events in 11 ms, 998 drops; stopped receiver: caller 0 ms, one failure. Blocked exporter: 12 ms caller loop, 999 drops, close 216 ms for 100 ms wait budget plus overhead | `final-focused-green.log`, current `collector-received.jsonl`; recorded values are observations, not SLOs |

The first full rerun (`final-focused.log`) executed 299 tests but had 4 diagnostic-test setup errors: querying an uncreated Logback child returned null. The fixture now creates the child before checking its inherited OFF level; the subsequent complete rerun above is green. `green-repair-focused.log` is an earlier 15-test pass before extending diagnostics to both log formats; it is not the final count. No broad-backend/base comparison or docs-site build was rerun in Repair 1; the limitations below are unchanged. / 首次全定向检查中诊断测试的子 logger 尚未创建，4 项 setup 报错；修正测试 fixture 后完整重跑通过。不将中间失败或早期 15 项绿色记录冒充最终结果；本轮未重跑全量后端/基线对比或文档站构建。

## Repair 2 — second independent review RETURN

Repair base and unchanged HEAD: `74e2bc1457bcb1046036ed1aee185b92e9751ab7`, branch `feat/otlp-business-events-1009`; changes are uncommitted. This section supersedes Repair 1's incomplete diagnostic-containment claim, not its historical results. Evidence filenames below are relative to `evidence/repair2/` in the worker report. / 第二轮修复以此提交为基线且不提交；本节替代上一轮不完整的诊断隔离声明，历史运行结果仍保留。

Full Repair 1 focused command/environment rerun: **307 tests, 0 failures, 0 errors, 0 skips** (193 domain + 114 app; 81 business-Logs cases). Counts include nested JUnit classes and are parsed from this run, not stale Surefire output. Business cases: mapper 6, configuration 10, HTTP 3, transaction 8, pipeline guard 2, diagnostics 6, exporter boundary 2, producer/JPA 43, Collector 1. / 完整定向命令重跑 307 项全部通过，包含嵌套测试类，非旧报告计数。

| Finding / boundary | Executed assertion / implementation | RED → GREEN evidence |
| --- | --- | --- |
| Uncaught credential-bearing callback | Exact local raw HTTP 401 + Transfer-Encoding chunked + invalid first chunk `1 Bearer <synthetic placeholder>`. BEFORE fix both shipped text/JSON modes print raw IllegalStateException/ProtocolException to captured stderr, despite successful deadline recovery. AFTER fix neither stdout nor stderr (including real Logback console output) contains the placeholder or uncaught-exception text; fixed warning remains. Requests=2, enqueued=2, failures=1, callback errors=1, exported=1, queue-full=0; later event exports without restart | RED `red-chunked-diagnostics-runtime.log`: 2 tests / 2 assertion failures / 0 errors; GREEN `green-diagnostics-boundary-final.log` and `final-focused-green.log`, 6 diagnostic cases |
| Narrow ownership/lifecycle | Private ThreadGroup contains only its `okhttp-dispatch-*` uncaught exceptions; actual production dispatcher group asserted, dispatcher threads terminate after context close. JVM default handler identity unchanged before/during/after. Same-named unrelated thread outside the group and non-dispatcher inside it both reach the previous default handler with original throwable; initialization failure is fixed text with no cause, interrupted caller's flag restored | PASS `BusinessLogsExporterBoundaryTest` (2 cases), plus text/JSON lifecycle assertions in diagnostics |
| Actual managed API | Public 1.31 Logs builder has no executor/transport/thread-factory setter. Bytecode confirms per-exporter OkHttp dispatcher factory captures constructor thread's group. No BOM/dependency upgrade, global handler replacement, SDK-private import or reflection | `otel-1.31-builder-api.txt`, `otel-1.31-dispatcher-bytecode.txt`, `otel-1.31-thread-factory-bytecode.txt` |
| Producer identity gap | Every committed matrix row asserts exact skill/version/namespace IDs (including absence where not defined), source, current visibility, status old/new and visibility old/new (including absence on other events), plus review/promotion IDs. Promotion uses persisted distinct target skill/version/namespace for publication and source for decision. All 26 commit/rollback and 13 denial rows remain green | PASS `green-producer-identities.log`, `final-focused-green.log`; no observed identity bug or invented identity RED |
| Promotion filter gap | Both initial AND update approvals run with source-only and target-only namespace allowlists through real Spring producers and the production mapper/listener/pipeline; retain only source decision or target publication respectively; assert exact surviving record identity and no precommit record | PASS 4 additional PostgreSQL cases; producer total 43 |
| Rollback claim scope | Narrowed code comment: all 13 rollback rows prove zero telemetry; independent persisted rollback assertions additionally cover PRIVATE confirmation and content review approval only, not every row's full mutation set. Denied-row state checks remain separate | Source/comment and assertions in `BusinessLogsProducerPostgresTest` |
| Deployment regression | No external setting or logging XML changed; exporter wiring changed, so Python deployment contracts and existing Helm/release shell suites rerun with synthetic fixtures | PASS `deployment-business-logs.log`: 4/4, no skips; `deployment-helm-contracts.log`, `deployment-release-contracts.log`: suite exit 0 |
| Real receiver regression | Pinned Collector receives/query confirms 27 records / 9 event names / 27 unique UIDs. Paused receiver: 1000 caller events in 8 ms, 998 drops; stopped receiver: caller 0 ms, failure counted. Blocked exporter: 12 ms loop, 999 drops, close 218 ms with 100 ms SDK wait budget plus local overhead | PASS `final-focused-green.log`, `collector-received.jsonl`, `collector-query.tsv`, `collector-query-summary.json`; observations, not SLOs |

An initial RED fixture run (`red-chunked-diagnostics.log`) failed 2 startup assertions because retry-initial-backoff exceeded the test's lowered maximum; corrected before the runtime RED above. The first boundary GREEN attempt (`green-diagnostics-boundary.log`) had 2 fixture failures: enumerating all private groups included shared OkHttp housekeeping retained from another context, rather than just live dispatcher groups. The final fixture filters dispatcher threads; all 8 diagnostic/boundary cases pass in `green-diagnostics-boundary-final.log`, then the full run passes. Neither intermediate failure is presented as evidence of a passing run. / 初次 RED 为测试退避参数不一致；修正后实际复现泄露。首次 GREEN 因统计了共享 housekeeping 的线程组而失败，改为检查实际 dispatcher 后 8 项通过，再完整重跑通过。

Scope: containment is private to this verified managed business sender, with shipped SDK logger OFF defaults. Non-dispatcher housekeeping delegates unchanged; its inert group may remain alive but weakly references metrics. No global handler is installed/restored; containment lasts through owned callback exit. Unrelated exporters/application failures, explicit logging/SDK overrides, exception-intercepting agents, heap dumps and receiver logs remain outside the guarantee. SDK upgrades require revalidation. Broad backend/base comparisons, packaging and docs-site build were not rerun in Repair 2; previous limitations below remain unchanged. `git diff --check` passes; owned containers/networks/volumes are cleaned up. / 隔离非全 JVM 脱敏，版本与默认日志范围及剩余部署责任详见设计和运维文档；本轮未重跑全量后端、打包或文档站构建，不冒称消除既有环境限制。

## Repair 3 — maintainer review (PR #1029, `bd30ac1`)

Finding: the global `io.opentelemetry.exporter = OFF` rule also hid existing Trace exporter errors when business Logs were disabled (reviewer reproduced 2 Trace 401s with 0 error lines vs. 2 `Failed to export spans` on main's logback config). / 维护者复现：全局 OFF 在业务 Logs 关闭时也屏蔽了既有 Trace 导出错误。

Change: removed the global exporter OFF; added `BusinessLogsDiagnosticsFilter` (Logback TurboFilter) that drops `io.opentelemetry.*` / `okhttp3.*` events only on threads of the business exporter's private `BusinessLogsExporterBoundary` group; kept `io.opentelemetry.sdk.logs` OFF (business pipeline only). / 去掉全局 OFF，改为只在业务 exporter 私有线程组上过滤 SDK 诊断。

| Scenario | Assertion | RED → GREEN |
| --- | --- | --- |
| Logs disabled + Trace 401 | Real `OtlpHttpSpanExporter` → local receiver returns 401 on `/v1/traces`; captured output contains `Failed to export spans`, `401` and the receiver message, text and JSON formats | RED with `bd30ac1` logback (await timeout, 0 lines); GREEN |
| Logs enabled + Trace 401 coexist | Business Logs 401 echoing synthetic credential and Trace 401 in one logging setup: Trace diagnostic present; credential and `Failed to export logs` absent; fixed business warning present | RED with `bd30ac1` logback (Trace line missing); GREEN |
| Sensitivity of the filter | Same tests with the global OFF removed but no filter: existing credential tests fail (6 failures: credential / `Failed to export logs` printed) | Proves the filter, not the logger level, provides the business containment |

Focused command (Repair 1 set, JDK 21): **311 tests, 0 failures, 0 errors, 0 skips** (adds 4 Trace-coexistence cases). Broad suite, deployment contracts and real Collector run were not rerun in Repair 3; no deployment setting changed. / 定向 311 项全部通过；本轮未重跑全量与部署契约。

## Broad suite and environment limits

Broad `make test-backend` runs are NOT green. Initial candidate: 2732 tests, 2 failures, 1 error, 30 skips. Final candidate broad run: 2762 tests, 3 failures, 11 errors, 30 skips. Frozen base: 2712 tests, 2 failures, 2 errors, 30 skips. Failures are in GitHub integration tests; exact failing methods are retained in the count JSONs. Four methods occur in both the final candidate's and base's broad failure sets (Go executor, credential privacy, two budget cases), but the full failure sets and error modes vary. Do NOT infer that every candidate failure was proven pre-existing. Subsequent identical five-class GitHub focused commands PASS all 37 tests on BOTH the candidate and frozen base. An independent SQL/host clock probe found the Docker database clock ahead of the host in every sample, consistent with timing-sensitive eligibility checks, but it is NOT asserted as the proven cause of every failure. No clock/global configuration was changed. The authorized detached base worktree was removed. / 全量检查不是绿色；基线与 candidate 有四个相同失败方法，但集合及错误形式不同，不冒称全部故障已证实为基线问题。随后相同五类 GitHub 定向测试在两边均 37 项通过。独立探测发现 Docker 数据库时钟领先宿主机，与时间筛选类故障相符，但未证明所有故障根因；未改系统时钟/全局配置。基线临时工作树已移除。

The 13 added denial cases and two mapper edge assertions were finalized after the last broad run compiled its test classes; all final sources were then exercised by the successful 289-test focused run. / 最后一次全量编译测试类后补齐 13 项拒绝授权及两个 mapper 边界断言，最终源码由 289 项定向测试实际验证。

Docs site build was attempted. VitePress was absent; `npm ci` was blocked by the environment's network guard for both the lockfile registry and an explicit npmjs alternative. This is reported as BLOCKED, not passing. Markdown/source/link checks are separate from a built site. / 文档站构建受依赖下载网络限制，未冒称通过。

Not executed: real external Java-agent/plugin installation, TLS/mTLS production deployment, a browser/CLI deployment smoke, forced-process-crash recovery or sustained throughput/heap profiling. There is no outbox/replay guarantee to test. Agent duplicate suppression, authorized egress/DNS/redirect policy, production Secret provisioning, receiver persistence and retention remain deployment responsibilities. All application mode combinations were exercised without installing a Java agent. / 未执行真实 Agent 注入、生产 TLS/mTLS、完整 Web/CLI 部署或持续负载测试；相关限制和部署责任明确保留。
