## 1. Read-only local diagnosis

- [x] 1.1 Add typed health checks, explicit read-only state loading and scoped registry/credential source resolution, including malformed/unreadable files and safe URL validation.
- [x] 1.2 Inspect registered installations using existing metadata, entry-state and trusted snapshot contracts; warn on modifications or absent baselines and preserve enabled/disabled state.
- [x] 1.3 Add focused local-service tests proving missing-state, legacy inventory, modified content, source isolation and unsafe/unreadable cases without file or lock changes.

## 2. Bounded registry diagnosis

- [x] 2.1 Reuse the existing identity client through a bounded, no-redirect fetch adapter; classify transport, HTTP, invalid-response, rejected credential and missing-login outcomes without leaking raw errors or secrets.
- [x] 2.2 Add deterministic tests for offline zero requests, timeout/DNS/TLS/connection classification, identity success, 401/403/5xx, unsupported response and credential-safe redirects.

## 3. CLI contract and documentation

- [x] 3.1 Wire doctor health options and stable human/JSON reports with documented exit priority; reject health-only options without the mode and preserve legacy doctor behavior.
- [x] 3.2 Add subprocess tests against real local HTTP fixtures for selected-source precedence, rejected credentials, unavailable registry, unchanged disk state, JSON/nonzero exits and legacy doctor compatibility.
- [x] 3.3 Update command help, CLI README and English/Chinese user guides with the opt-in mode, warning/failure distinction, offline behavior and diagnostic limits.

## 4. Verification and handoff

- [x] 4.1 Run strict OpenSpec validation, focused and complete CLI tests with isolated HOME, lint, typecheck, production build and Node-built-artifact smoke checks; retain actual failures and boundaries.
- [x] 4.2 Review the complete diff directly in the main session, verify privacy and scope, update the Draft PR with actual evidence and leave merging/publication for explicit authorization.
