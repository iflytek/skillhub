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
- [ ] 4.3 Wire Docker/Compose/Helm defaults and reserved sub-path validation. (Implemented; Compose/runtime checks passed. Helm CLI rendering remains unverified.)
- [x] 4.4 Provide optional persistent self-hosted Compose combination without another Nginx.

## 5. PR-2 (follow-up): documentation
- [x] 5.1 Replace source-editing quickstart with configuration-only production paths for supported platforms; distinguish custom adapter development.
- [x] 5.2 Document fields, platform prerequisites, initialization, verification, troubleshooting, upgrade and shutdown.
- [x] 5.3 Link deployment entry points and document adapter extension and actual privacy boundaries.

## 6. PR-2 (follow-up): quality gates
- [ ] 6.1 Consolidated independent implementation review, fixes and final base checkpoint.
- [ ] 6.2 Reviewed requirement/scenario/test matrix and targeted source/deployment checks. (Worker matrix and local checks complete; independent review and Helm execution pending.)
- [ ] 6.3 Exact-SHA release Compose S3/auth smoke and Chromium normal/changed/failure scenarios, including default zero module/SDK/request and root/sub-path upload/login/download/CSP/cache compatibility. (Local browser/real-collector and HTTP proxy checks passed; full backend S3/auth smoke remains pending.)
- [ ] 6.4 CI/privacy/readiness, one validation report and retained preview for human acceptance.
- [ ] 6.5 Close Issue #989 only after configuration-only deployment is complete and verified, not when PR-1 merges.
