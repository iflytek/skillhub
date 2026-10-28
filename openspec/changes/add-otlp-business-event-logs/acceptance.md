# Acceptance evidence — OTLP business-event Logs

Base: `9ebb99226dd1b3e1d4fd8dd3e36baae70a4ed273`; branch `feat/otlp-business-events-1009`. These are executed results, not a claim of a deployed browser/CLI production stack. Raw logs and receiver output are retained in the implementation worker's evidence directory; the worker report gives absolute paths. / 以下为实际运行结果，不等同完整生产部署验收；原始输出路径见 worker report。

## Event/producer matrix

`BusinessLogsProducerPostgresTest` uses actual Spring service proxies, real JPA repositories, Flyway migrations and PostgreSQL 16. Storage/scanner/avatar/notification/audit/compensation collaborators and package-validation/metadata-parsing collaborators are isolated mocks; business state and transaction boundaries are real. Each row has a committed scenario and a rollback scenario. Rollback emits zero records. / 服务代理、仓储、迁移和事务为真实依赖；存储、扫描、头像、通知、审计、补偿及包校验/解析协作组件用 mock 隔离，不宣称验证它们的端到端副作用。

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
| Capture before commit | Valid unsampled span survives scope end before PostgreSQL commit; implementation reads standard SpanContext only, not request/MDC strings | PASS `BusinessLogsTransactionTest`; mapper/listener source boundary |
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

## Broad suite and environment limits

Broad `make test-backend` runs are NOT green. Initial candidate: 2732 tests, 2 failures, 1 error, 30 skips. Final candidate broad run: 2762 tests, 3 failures, 11 errors, 30 skips. Frozen base: 2712 tests, 2 failures, 2 errors, 30 skips. Failures are in GitHub integration tests; exact failing methods are retained in the count JSONs. Four methods occur in both the final candidate's and base's broad failure sets (Go executor, credential privacy, two budget cases), but the full failure sets and error modes vary. Do NOT infer that every candidate failure was proven pre-existing. Subsequent identical five-class GitHub focused commands PASS all 37 tests on BOTH the candidate and frozen base. An independent SQL/host clock probe found the Docker database clock ahead of the host in every sample, consistent with timing-sensitive eligibility checks, but it is NOT asserted as the proven cause of every failure. No clock/global configuration was changed. The authorized detached base worktree was removed. / 全量检查不是绿色；基线与 candidate 有四个相同失败方法，但集合及错误形式不同，不冒称全部故障已证实为基线问题。随后相同五类 GitHub 定向测试在两边均 37 项通过。独立探测发现 Docker 数据库时钟领先宿主机，与时间筛选类故障相符，但未证明所有故障根因；未改系统时钟/全局配置。基线临时工作树已移除。

The 13 added denial cases and two mapper edge assertions were finalized after the last broad run compiled its test classes; all final sources were then exercised by the successful 289-test focused run. / 最后一次全量编译测试类后补齐 13 项拒绝授权及两个 mapper 边界断言，最终源码由 289 项定向测试实际验证。

Docs site build was attempted. VitePress was absent; `npm ci` was blocked by the environment's network guard for both the lockfile registry and an explicit npmjs alternative. This is reported as BLOCKED, not passing. Markdown/source/link checks are separate from a built site. / 文档站构建受依赖下载网络限制，未冒称通过。

Not executed: real external Java-agent/plugin installation, TLS/mTLS production deployment, a browser/CLI deployment smoke, forced-process-crash recovery or sustained throughput/heap profiling. There is no outbox/replay guarantee to test. Agent duplicate suppression, authorized egress/DNS/redirect policy, production Secret provisioning, receiver persistence and retention remain deployment responsibilities. All application mode combinations were exercised without installing a Java agent. / 未执行真实 Agent 注入、生产 TLS/mTLS、完整 Web/CLI 部署或持续负载测试；相关限制和部署责任明确保留。
