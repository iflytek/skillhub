## Implementation

- [x] Inventory every existing producer/consumer for the nine included event types; preserve notification/audit/permission behavior.
- [x] Add scalar producer snapshots (time, namespace ID, visibility, actor/type, fixed source), without domain telemetry dependencies.
- [x] Add a dedicated default-off Logs pipeline; no global registration, application-log bridge or second Trace pipeline.
- [x] Capture valid span identity before handoff; admit only after commit with nontransactional fallback.
- [x] Central allowlist mapper, private-ID policy, stable per-record UUID, fixed attribute/string limits.
- [x] Standard SDK batching/retry, bounded outstanding admission, timeout and shutdown; fixed-cardinality Micrometer counters and rate-bounded safe diagnostics.
- [x] Explicit application/release environment mappings, release validator, Helm defaults and existing-Secret reference; no new vendor/third-party dependency or BOM upgrade.
- [x] Bilingual operator documentation, deployment links, event catalog, configuration table, direct/Collector and Secret guidance.

## Verification

- [x] Write initial mapper/configuration tests before implementation; retain RED compiler output and subsequent GREEN output.
- [x] Execute mapper/filter/anonymous/system/UID/config validation tests.
- [x] Execute PostgreSQL transaction listener commit/rollback tests for all nine catalog facts, nontransactional real download/permission tests and unsampled context capture.
- [x] Execute SDK HTTP retry/identical-payload UID/auth-only tests and partial-success blind-spot test.
- [x] Execute real Collector receive-and-query with all nine events and three Trace configurations.
- [x] Execute real-service/JPA commit/rollback and denied-actor matrix for all producer paths (39 scenarios).
- [x] Execute paused/stopped live Collector saturation/outage and record measured latency/resource limits.
- [x] Execute synthetic release validation, existing release regression checks, Helm lint/render/contracts and new deployment configuration tests.
- [x] Finish broad backend/base runs and report reproduced versus unreproduced failures explicitly (full suite remains FAIL; identical GitHub focused checks pass on both).
- [x] Run final focused checks after the last code edits, query actual receiver evidence with documented jq command, validate local docs/links and diff (site build BLOCKED by dependency-network guard).
- [x] Verify cleanup of every owned temporary container/network/volume and the authorized baseline worktree; leave changes uncommitted.

See acceptance.md for final executed results and explicit unverified deployment scenarios; a task checkbox is not itself acceptance evidence.

## Repair 1

- [x] Reproduce truncated HTTP error-body permit leak before production changes; add bounded first-completion-wins export accounting and late success/failure regression cases.
- [x] Reproduce synthetic credential disclosure in receiver Status and transport-error diagnostics; disable raw exporter/Logs SDK loggers by default and verify both shipped log formats retain safe operator signals.
- [x] Explicitly exclude NESTED savepoint rollback from suppression; verify PostgreSQL NESTED rollback/outer commit and REQUIRES_NEW commit/outer rollback plus documentation consistency.
- [x] Make MDC/Baggage exclusion non-vacuous with distinct seeded sentinels, dispatch preconditions and exported attributes/body/resource checks.
- [x] Retain RED/GREEN evidence and rerun full focused command: 299 tests green; deployment contracts: 4 Python cases plus Helm/release shell suites green.

Repair 1 results and intermediate failures are recorded in acceptance.md and the appended worker report; earlier broad-suite/site-build limitations remain explicit.

## Repair 2

- [x] Save runtime RED for malformed-chunked 401 response-body exceptions in both shipped logging formats, including captured stderr disclosure despite permit recovery.
- [x] Verify actual managed 1.31 builder API and dispatcher factory; contain only owned dispatcher uncaught failures with a private ThreadGroup, no global default-handler mutation or BOM upgrade.
- [x] Verify fixed diagnostics, errors/failure accounting, permit recovery, later successful export, lifecycle shutdown and unchanged previous handling of unrelated threads.
- [x] Assert complete exported identities/source/visibility/old-new fields for every committed producer row; add four real-producer initial/update source-only/target-only promotion filter cases; narrow persistent rollback claim.
- [x] Update operator guide/design/spec with the exact safe-diagnostics scope and residual risks; retain evidence of intermediate fixture failures.
- [x] Rerun full focused selector/environment: 307 tests green (81 business); real Collector query 27/9/27; deployment contracts 4/4 plus Helm/release shell suites green; diff and scoped cleanup checked.

Repair 2 exact commands/counts/evidence and proposed fixup are appended to the worker report. No commit/push/comment was performed.

## Repair 4

- [x] Run the unmodified Diagnostics + Trace selector twice in default ordering and twice in diagnostic-first Surefire alphabetical ordering; retain the reproduced MDC failure and XML counts.
- [x] Isolate process-global diagnostics in a dedicated, non-reused test fork without removing assertions; final minimal combination passes twice, exact focused selector passes 312 tests, full observability package passes 117.
- [x] Add default-health/info metrics exposure passthrough in application, Compose and Helm with validation/schema contracts; prove live super-admin 200, ordinary-user 403, anonymous 401, and default admin 404.
- [x] Add POSIX runtime entry points for existing receiver and optional downstream-forwarding Collector, shared private network overlay, repeatable updates/status/stop/disable, protected deployment env files and flat OSS retrieval contracts.
- [x] Add Makefile demo entry points with automatic host/container endpoint selection, actual JSON queries and expected-event verification; preserve trial records with append mode and a project-scoped local volume across up/stop/start.
- [x] Execute a branch Dockerfile build and real bootstrap-admin publication/download matrix for default off, existing receiver, optional Collector, application endpoint update, Collector downstream update, stop/start and disable on an isolated non-default-port deployment.
- [x] Execute actual make dev-server with offline packaging, PostgreSQL/Redis and a demo Collector; execute the containerized backend path and prove new skill records plus retained records after restart.
- [x] Extend the real Collector integration test with a restarted HTTP readiness probe and preservation of all 27 original UIDs; handle Docker's changed dynamic host-port binding; final broad focused/package runs include the assertion.
- [x] Run fake-Docker lifecycle tests, six Python deployment contracts, relevant existing script suites, Helm contracts/strict lint, VitePress and weekly site validation; record platform prerequisite failures separately from passing reruns.
- [x] Rewrite the guide as production integration/local trial, add short site/chart pointers, admin queries/counter meanings, operational updates and cleanup steps; document required release/OSS file upload.
- [x] Verify real clean retains deployment files byte-for-byte at mode 600; remove the identified test containers, project volumes/networks and test images, preserve pre-existing images, stop the authorized dind and local relay, and verify owned ports are closed.

Repair 4 real runs, template-only checks, intermediate fixture failures and remaining deployment limits are recorded in acceptance.md and the worker report. Commits remain local; publication is left to the owner.
