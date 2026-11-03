## 1. Scope and integration
- [x] 1.1 Confirm maintainer authorization, preserve contributor history and resolve latest-main translation conflicts.
- [x] 1.2 Define runtime configuration, adapter boundary, failure behavior and production deployment scope.

## 2. PR-1 (#1010)
- [x] 2.1 Verify passive markers, frozen published IDs and manifest drift against the current base.
- [x] 2.2 Verify the vendor-neutral helper, frozen defaults, finite rules, skipped diagnostics, repeat-init/stop and example adapter assertions.
- [x] 2.3 Fix and regression-test the example edge's server-level 100M application upload limit, release Web upstream default and native Linux host-gateway mapping.
- [x] 2.4 Provide an independently followable Chinese guide for existing-platform adapters and the local Umami demo, including configuration, deployment, acceptance, troubleshooting, upgrade and shutdown; link the English contract.
- [x] 2.5 Align the two-PR interfaces and manually check OpenSpec structure against add-skill-suites (CLI unavailable; not CLI validation).
- [x] 2.6 Run focused/full tests, typecheck, lint, build, manifest, Compose rendering and diff checks; repeat built-frontend/real-collector browser acceptance and the guide walkthrough, including upload boundaries.
- [ ] 2.7 Independent parent review and publication; keep Issue #989 open.

## 3. PR-2 (follow-up): optional frontend capability
- [x] 3.1 Move helper/adapter to a single product source under web/src/features/analytics and preserve all PR-1 assertions.
- [x] 3.2 Add default-closed runtime loader and vendor-neutral controller/registry; register Umami as the first adapter.
- [x] 3.3 Cover consent, validation, duplicate init, async stop races and adapter failures.
- [ ] 3.4 Validate this OpenSpec change with the CLI when available and bind the follow-up PR/task.

## 4. PR-2 (follow-up): deployment
- [x] 4.1 Safely serialize and validate configuration without breaking existing runtime config.
- [x] 4.2 Add isolated proxy snippets with request-time DNS, certificate validation and exact endpoints.
- [x] 4.3 Wire Docker/Compose/Helm defaults and reserved sub-path validation. Helm keeps analytics in a Web-only ConfigMap. Template checks executed with helm 3.19.0: `helm lint --strict` and `configuration-contracts.sh`, which renders before/after values and asserts that analytics-only changes roll only Web while backend changes still roll the server. A real-cluster Helm install/upgrade with analytics enabled has not been run.
- [x] 4.4 Provide optional persistent self-hosted Compose combination without another Nginx.

## 5. PR-2 (follow-up): documentation
- [x] 5.1 Replace source-editing quickstart with configuration-only production paths for supported platforms; distinguish custom adapter development.
- [x] 5.2 Document fields, platform prerequisites, initialization, verification, troubleshooting, upgrade and shutdown.
- [x] 5.3 Link deployment entry points and document adapter extension and actual privacy boundaries.

## 6. PR-2 (follow-up): quality gates
- [ ] 6.1 Consolidated independent implementation review, fixes and final base checkpoint.
- [ ] 6.2 Reviewed requirement/scenario/test matrix and targeted source/deployment checks. (Worker matrix, local checks and Helm template checks complete; maintainer verified the real image, backend, Umami, Postgres and browser path. Real-cluster Helm verification and independent review pending.)
- [ ] 6.3 Exact-SHA release Compose S3/auth smoke and Chromium normal/changed/failure scenarios, including default zero module/SDK/request and root/sub-path upload/login/download/CSP/cache compatibility. (Local browser/real-collector and HTTP proxy checks passed; full backend S3/auth smoke remains pending.)
- [ ] 6.4 CI/privacy/readiness, one validation report and retained preview for human acceptance.
- [ ] 6.5 Close Issue #989 only after configuration-only deployment is complete and verified, not when PR-1 merges.

## 7. PR-3: DataFinder adapter
- [x] 7.1 Register DataFinder as a lazy second adapter; validate appId/product without SDK or browser secrets.
- [x] 7.2 Pass optional vendor-neutral page context from the router; send templates and anonymous browser UUID only, with storage fallback.
- [x] 7.3 Generate one POST-only list proxy, preserving browser User-Agent and existing proxy isolation; cover validation, payload, deadlines and stop races.
- [x] 7.4 Verify unit/build/script gates, Compose rendering and local mock-collector browser integration at root and sub-path; document setup and privacy.
- [x] 7.5 Run full Helm lint/configuration-contracts with the declared PostgreSQL/Redis subcharts available (helm 3.19.0 container; template checks only, no real cluster).
- [ ] 7.6 Independent parent review, real-platform acceptance and publication.

## 8. Maintainer one-click follow-up
- [x] 8.1 Split Umami and SkillHub Compose projects, add shared external network to Web/Umami only, and implement two-step `runtime.sh` lifecycle with persisted 0600 secrets. `clean` retains independent analytics credentials; no volume deletion.
- [x] 8.2 Add independent Kubernetes Umami/Postgres unit with Secret, PVC, services and port-forward management path; chart remains optional and unchanged.
- [x] 8.3 Document script-new, script-existing and Helm paths, UUID acquisition, upgrades, shutdown and retention; replace stale PR-1 edge fixtures with current deployment assertions.
- [x] 8.4 Verify isolated Compose from an empty runtime directory, repeat secrets/data, Web recreation/network, analytics stop/restart, existing-platform enable, disable and business routes. Evidence: `skillhub-1037-oneclick/evidence/compose-smoke.log`.
- [x] 8.5 Verify real kind: separate stats unit and SkillHub Helm release, Umami DB event increase via Web proxy, analytics-only Helm upgrade rolls Web while server Pod UID remains unchanged. Evidence: `skillhub-1037-oneclick/evidence/kind-smoke.log`.
- [ ] 8.6 Independent maintainer review and PR acceptance remain external; do not close Issue #989 here.

## 9. Maintainer deployment guard fixes
- [x] 9.1 Keep installed-release smoke values isolated: reuse release values, remove implicit test credentials, and cover successful and rejected event/rollout paths plus incomplete-fix mutations.
- [x] 9.2 Persist independent project/network identity, migrate only from known original configuration, and reject ambiguous legacy state before defaults are generated; cover custom/default multi-instance lifecycle and secret preservation.
- [x] 9.3 Clarify file/image version pinning, the three operator paths, independent configuration ownership, cleanup/upgrade boundaries and smoke upgrade side effects; remove duplicate operational descriptions from the contract.
- [x] 9.4 Complete one consolidated independent review and the focused source/deployment validation matrix for these fixes. Script/model checks, Helm lint/render contracts and an isolated real two-instance Umami/PostgreSQL lifecycle passed; no real-cluster Helm upgrade or Secret lookup was repeated for these fixes.
- [x] 9.5 Build the affected documentation and verify the rendered operator instructions; keep real-cluster and publication status explicit. VitePress build and Chromium 145 checks passed for Chinese/English navigation and 390px layout. Publication and full PR acceptance remain separate from these focused documentation checks.
- [x] 9.6 Reuse one application Secret rendering for its resource and all deployment checksums without mutating user values; preserve explicit-secret checksum bytes and genuine rotation, and document the legacy first-upgrade correction. Real Helm template regression rejects the original chart and passes the fix; real-cluster fresh/legacy upgrade results are retained in the separate final validation record.
