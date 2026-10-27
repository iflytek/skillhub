## 1. Scope and integration
- [x] 1.1 Confirm maintainer authorization, preserve contributor history and resolve latest-main translation conflicts.
- [x] 1.2 Define runtime configuration, adapter boundary, failure behavior and production deployment scope.
- [ ] 1.3 Validate this OpenSpec change and bind the existing PR/task.

## 2. Optional frontend capability
- [ ] 2.1 Move helper/adapter to a single product source and preserve existing tests.
- [ ] 2.2 Add default-closed runtime loader and vendor-neutral controller/registry.
- [ ] 2.3 Cover consent, validation, duplicate init, async stop races and adapter failures.

## 3. Deployment
- [ ] 3.1 Safely serialize and validate configuration without breaking existing runtime config.
- [ ] 3.2 Add isolated proxy snippets with request-time DNS, certificate validation and exact endpoints.
- [ ] 3.3 Wire Compose/Helm defaults and reserved sub-path validation.
- [ ] 3.4 Provide optional persistent self-hosted Compose combination without another Nginx.

## 4. Documentation
- [ ] 4.1 Replace source-editing quickstart with configuration-only production paths.
- [ ] 4.2 Document fields, platform prerequisites, initialization, verification, upgrade and shutdown.
- [ ] 4.3 Link deployment entry points and document adapter extension and actual privacy boundaries.

## 5. Quality gates
- [ ] 5.1 Consolidated independent implementation review, fixes and final base checkpoint.
- [ ] 5.2 Reviewed requirement/scenario/test matrix and targeted source/deployment checks.
- [ ] 5.3 Exact-SHA release Compose S3/auth smoke and Chromium normal/changed/failure scenarios.
- [ ] 5.4 CI/privacy/readiness, one validation report and retained preview for human acceptance.
