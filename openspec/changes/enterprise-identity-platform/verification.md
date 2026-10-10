# Enterprise Identity Platform Release 1 Verification

## 2026-09-17 post-manual-acceptance regression plan

- Scope: current dirty local preview atop `7d8383860a9d9bfbf55d64bf62ee7935108ce3a9`; no new deployment, data reset, commit, PR or push. User confirmed avatar recovery and reported other reviewed login behavior acceptable. Exact-SHA certification remains pending.
- Follow-up from manual enterprise login: `alice@acme.identity-lab.test` successfully completed the Keycloak callback and received an active Organization membership, then hit expected server-side 403 on the Organization administration detail because it has no Organization roles. The Web Organization list now keeps member-only Organizations visible but not clickable into administration; backend 403 remains the final authorization boundary.
- P0 compatibility: run full frontend unit suite with one worker and targeted backend local-password/OAuth/Device Flow/API Token/enterprise Session tests, with bounded JVM resources. Passing requires zero failures; retain actual counts after execution.
- Existing browser-contract cases map to `web/e2e/enterprise-identity-flow.spec.ts`: P0 connection draft/test/explicit activation (mock admin/catalog/API, verify password is not echoed); P1 invalid connection (mock failed test, verify bounded safe error and no secret/internal error); P0 discovery redirect (mock enterprise-only catalog and successful redirect, verify expected explicit target and authenticated heading). These cases change fixture state only, not lab database data.
- Browser executor: existing matching cached Chromium; target `http://127.0.0.1:13000`, one worker. Native Windows Chrome remains the manual-review browser. Capture Playwright screenshots/failure traces in ignored test-results. No real credentials enter browser-contract reports.
- Real public-provider authorization is blocked by configuration: the live catalog contains only PASSWORD and ENTERPRISE_DISCOVERY. No GitHub/DingTalk/Feishu authentication claim will be made from mocked configuration tests. Do not silently configure or merge pending vendor adapters.

### Completed results

- `cd web && pnpm exec vitest run --maxWorkers=1`: 207 files / 818 tests passed, 119.63s. The jsdom navigation warning does not represent a real OAuth callback test.
- `cd server && MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am test -Dtest=LocalAuthServiceTest,LocalAuthControllerTest,DeviceAuthServiceTest,DeviceAuthControllerTest,DeviceAuthWebControllerTest,ApiTokenServiceTest,ApiTokenAuthenticationFilterTest,ApiTokenScopeServiceTest,OAuth2LoginHandlersTest,OAuth2AuthorizationRequestResolverTest,OAuthLoginRedirectSupportTest,OAuthLoginFlowServiceTest,AuthMethodCatalogTest,EnterpriseLoginAppServiceTest,EnterpriseBrowserSessionServiceTest,EnterpriseSessionAccessServiceTest,ExpiredPublicSessionFilterTest -Dsurefire.failIfNoSpecifiedTests=false`: 17 classes / 119 tests passed, zero failures/errors/skips, 48.333s. Counts checked from the selected Surefire XML files, not all historical reports. This is a targeted compatibility suite, not a new full backend run.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:13000 PLAYWRIGHT_WORKERS=1 PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/home/ylhu16/.cache/ms-playwright/chromium-1208/chrome-linux64/chrome pnpm exec playwright test e2e/enterprise-identity-flow.spec.ts --project=chromium --workers=1 --reporter=line`: 3 passed, 12.5s. Initial default launch failed because the headless-shell cache was absent; reused the matching full Chromium executable without download or new services. First executable-enabled run exposed a stale fixture lacking LOGIN_SECRET_ADMIN; two creation cases timed out and the remaining run was cancelled. Added that role to the mock admin only, then reran all three successfully. Existing UI unit test still asserts IDENTITY_ADMIN alone cannot create a confidential connection. No product authorization bypass was added.
- `cd server && MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -DskipTests package`: passed, including `skillhub-auth` and `skillhub-app` compilation/package.
- `cd web && pnpm exec vitest run src/pages/login.test.tsx src/pages/register.test.tsx src/features/auth/enterprise-login-discovery.test.tsx src/features/auth/login-button.test.tsx src/shared/lib/auth-route.test.ts src/pages/dashboard/organization-admin-pages.test.tsx --maxWorkers=1`: 6 files / 45 tests passed.
- `cd web && pnpm run typecheck && pnpm run lint`: passed.
- `cd web && pnpm run build`: passed. Existing runtime-config, mixed static/dynamic dashboard import and large chunk warnings remain.
- `cd web && pnpm exec vitest run src/pages/register.test.tsx src/pages/login.test.tsx src/features/auth/enterprise-login-discovery.test.tsx --maxWorkers=1`: 3 files / 19 tests passed after adding registration safe-return verification and enterprise-discovery identifier trimming.
- `cd web && pnpm run typecheck && pnpm run lint`: passed after the registration and enterprise-discovery test hardening.
- `cd web && pnpm exec vitest run src/features/organization/organization-admin-shell.test.ts src/pages/dashboard/organization-admin-pages.test.tsx --maxWorkers=1 && pnpm run typecheck`: 2 files / 20 tests passed plus TypeScript check after adding direct Organization role-helper coverage.
- Login/register/enterprise/Organization i18n key scan against `zh`, `en` and `ru`: zero missing keys after switching the disabled-account login banner to the existing `error.auth.local.accountDisabled` key.
- `cd web && pnpm exec vitest run src/pages/login.test.tsx --maxWorkers=1`: 1 file / 11 tests passed after the i18n key correction.
- `cd web && pnpm run typecheck && pnpm run lint`: passed after the i18n key correction and Organization role-helper coverage.
- `cd server && MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -Dtest=EnterpriseLoginAppServiceTest,AuthMethodCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test`: 16 tests passed after filtering `ENTERPRISE_DISCOVERY` out of enterprise-discovery fallback public methods.
- `cd server && ./mvnw -pl skillhub-auth -am -Dtest=OAuth2LoginHandlersTest,OAuthLoginRedirectSupportTest,OAuthLoginFlowServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`: 20 tests passed after the final OAuth redirect comment/import cleanup.
- `cd server && MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -Dtest=AuthMethodCatalogTest,EnterpriseLoginAppServiceTest,OAuth2LoginHandlersTest,OAuthLoginRedirectSupportTest,OAuthLoginFlowServiceTest,LegacyPlatformIdentityCoreBridgeTest -Dsurefire.failIfNoSpecifiedTests=false test`: app/auth combined regression passed, 40 tests total.
- `cd server && MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -DskipTests package`: passed after the final enterprise discovery fallback change.
- `cd web && pnpm exec vitest run src/pages/login.test.tsx src/pages/register.test.tsx src/features/auth/enterprise-login-discovery.test.tsx src/features/auth/login-button.test.tsx src/shared/lib/auth-route.test.ts src/features/organization/organization-admin-shell.test.ts src/pages/dashboard/organization-admin-pages.test.tsx --maxWorkers=1 && pnpm run typecheck && pnpm run lint && pnpm run build`: 7 files / 54 tests passed, TypeScript check, lint and production build passed. Existing Vite runtime-config, mixed static/dynamic dashboard import and large chunk warnings remain.
- 2026-09-17 14:54 CST rerun: the same app/auth combined regression passed again, 40 tests total, using `MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2'`.
- 2026-09-17 14:55 CST rerun: the same seven-file frontend targeted suite passed again, 54 tests total, followed by passing `pnpm run typecheck`, `pnpm run lint` and `pnpm run build`. Existing Vite runtime-config, mixed static/dynamic dashboard import and large chunk warnings remain.
- 2026-09-17 14:55 CST rerun: `cd server && MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -DskipTests package` passed.
- 2026-09-17 14:55 CST secret hygiene check: exact local scan for the previously used disposable test token and private-key markers returned no repository hits, excluding generated build and dependency directories. The token value is intentionally not recorded in this file.
- Generated OpenAPI types from the running review backend into ignored `reports/openapi-current.d.ts`; `cmp -s` against checked-in `web/src/api/generated/schema.d.ts` returned 0. The standard OpenAPI check was not run because it starts/stops another Compose stack and overwrites the checked-in file.
- Architecture check, diff whitespace check and strict OpenSpec validation passed. Screenshots for the successful three UI cases are in ignored `web/test-results/`; backend results are in module Surefire reports. No real vendor login, current-candidate real Keycloak callback, data reset or new exact-SHA certification was performed in this batch. Staging was not launched to avoid introducing another stack under WSL resource constraints.

### Manual acceptance steps for this batch

1. Password UI: open `http://127.0.0.1:13000/login` using the existing local test account. Without returnTo, success goes to `/`. Logout, visit a public Skill detail, click navbar login and verify it returns to that exact detail. Refresh three times and verify the avatar remains visible and its menu opens.
2. Session: refresh after successful login and remain logged in. Logout through avatar menu, refresh again and verify the navbar shows login. Visiting `/dashboard/organizations` while logged out must require authentication.
3. Source safety: use login with `returnTo=%2F%3Fq%3Dplayer%23results` and verify the final address preserves `/?q=player#results`; use `returnTo=https%3A%2F%2Fexample.com` and verify it returns home, not outside SkillHub.
4. Registration UI: from logged-out login click register, verify one create-personal-account heading, return-login operation, stable brand position and short-height scrolling. Actual submission requires an explicitly disposable account; no enterprise membership should be created by personal registration.
5. Existing enterprise reference login (separate from public vendors): use the mapped `https://skillhub.identity-lab.test:18445/login` origin; enter organization `identity-lab-real-acme`, discover and continue to the configured Keycloak option. Use only the existing disposable IdP credentials. Verify redirect to Keycloak (no enterprise password collected by SkillHub), successful callback to homepage/source, avatar and logout/re-login. The HTTP direct origin is not a substitute for the configured HTTPS callback. If credentials or enterprise entry are unavailable, mark blocked rather than passed.
6. Admin read-only review: with an authorized Organization admin account open `/dashboard/organizations`, select the lab Organization and inspect login connections. Secrets must not be displayed. Create button requires both IDENTITY_ADMIN and LOGIN_SECRET_ADMIN; absence with only the former is expected. Do not suspend/rotate/delete the existing connection as part of this read-only review.
7. Real public providers: after a supported adapter and valid application registration are configured, check only those providers appear, enterprise discovery can be disabled without hiding public OAuth, and each authorization/callback returns to the source/home. Current live catalog has no public providers, so this step is blocked. No fake button is a substitute for successful upstream authentication.

## 2026-09-17 header avatar and browser test cleanup

- User reported avatar disappearing after refresh and recovering after window resize. Found review helpers leaving a 1366px CDP device-metrics override active after testing, while the maximized Windows window has 1280px available width. A simulated screenshot alone does not establish physical-window containment.
- Cleared the override in the active review browser and added cleanup to the ignored public-provider and login-UI helpers. No application CSS/authentication change was made on this finding.
- Native-window navigation and three cache-bypassing reloads at 1280px retained the 32px avatar button within the viewport (right edge 1224px). The report's containment checks passed; a fresh screenshot was captured. This is a local browser-state correction; the user's exact intermittent disappearance still requires confirmation after refresh, not a claimed application regression fix.

## 2026-09-17 source-page redirects and configuration-driven external entries

- Password login/registration and backend OAuth fallback now target `/`, not `/dashboard`. Safe explicit source targets remain unchanged. Navbar login records pathname/query/hash. Frontend and backend reject unsafe return targets; explicit CLI authorization targets are preserved.
- Frontend targeted suite: 5 files / 38 tests passed. After the generic vendor-icon adjustment, the 4 button tests, lint and production build including TypeScript checks passed again. Backend redirect/resolver/flow/catalog/enterprise service suite: 36 tests passed, package build succeeded; expanded five-provider catalog test rerun: 5 tests passed. Existing build warnings remain.
- Windows Chrome tested an intercepted catalog without enterprise discovery: GitHub, GitLab, OIDC, DingTalk and Feishu buttons rendered and each actual button navigated to its configured authorization action with `/search?q=player#results` preserved. Browser exceptions: 0. This verifies presentation/navigation only, NOT real vendor authentication or callback; fixture credentials were not deployed.
- Provider list remains server-owned (`AuthMethodCatalog`, OAuth2 registrations); the page contains no fixed button fallback. Current validity filtering hides absent/blank/placeholder client IDs. GitHub/GitLab configuration exists in application.yml; vendor registration visibility does not implement DingTalk/Feishu protocol handling. Those adapters and real credentials are separate integration prerequisites.
- Current review lab actually advertises password and enterprise discovery, without public GitHub credentials. No environment switch or enterprise feature was removed based on the ambiguous statement that its entry had been cancelled. No PR/push or production mutation.

## 2026-09-17 account-type labels and registration heading

- User approved neutral enterprise/personal account labels. Shared switch now displays “企业账号 / 个人账号”; registration has one “创建个人账号” h1 and no “登录 SkillHub” or duplicate creation heading. Chinese/English/Russian translations updated; auth APIs, validation and permission behavior unchanged.
- Targeted 4-file/15-test suite, lint, production build including TypeScript checks, diff whitespace check and OpenSpec strict validation passed. Tests assert a single registration heading and absence of the login heading.
- Completed Windows Chrome rerun explicitly checked both neutral labels, the registration heading and absence of login copy. Existing ordinary/scaled no-overflow, fixed brand, short-screen scrolling, mobile containment and real Keycloak redirect assertions passed; exceptions/console errors 0. Registration screenshot reopened and confirmed. First attempt encountered a missing DOM control during the resize/navigation sequence and failed; the completed rerun is recorded, not the failed attempt. Full frontend/backend/release matrix and registration submission not repeated for this copy-only adjustment.

## 2026-09-17 compact login and provider integration advice

- User screenshot showed an internal right-main scrollbar and oversized controls. Previous document-level overflow assertions did not prove internal-main containment. Added explicit main overflow assertions; do not hide scrollbars to bypass failures.
- Compact UI: max form width 440px, password inputs/main action 40px, right heading 24px, left headline 36–48px; tighter header/footer/group spacing. Mixed mode retains accessible headings but removes visually duplicated mode titles. Registration/discovery controls follow the smaller shell.
- Final targeted frontend tests: 4 files / 15 tests passed. Lint and production build (including TypeScript project checks) passed. Existing Vite warnings remain. The full 804-test result below is historical, not a new full-suite run for this CSS-focused change.
- Windows Chrome CDP completed: ordinary 1366×768 personal form main overflow **false**; 1280×678 at deviceScaleFactor 1.5 main overflow **false**, measured input height **40px**. This is a CSS viewport/pixel-scale check, not proof of the user's actual browser zoom setting. Document overflow false; fixed brand, integrated registration, short-screen containment, mobile horizontal containment, real discovery and Keycloak redirect passed. Exceptions and console errors **0**. First attempt failed a DOM evaluation during resize; the completed rerun is the recorded result.
- Captures: ignored review-lab reports `login-ui-{desktop,password,scaled,register,mobile,discovery}.png`; JSON contains desktopFormOverflow and scaledFormOverflow in addition to existing assertions. No full authentication/callback or real vendor login was repeated.
- Fresh independent visual review compared the user's rejected screenshot with all six new captures and returned `Ship`, with no material clipping/readability findings. Functional/short-screen evidence is from the executed Chrome checks, not screenshot-only claims. UI detector returned `[]`.
- Provider PR read-only advice: Feishu #696 head `d3f1d5e65af8139a6021eca52a5d7a114156f3c3`, DingTalk #467 head `0b21fe2f34c6901abf5bc017e37f1d432e66fe33`. Their platform OAuth registrations are not Organization-scoped LoginConnections. See design.md's provider integration section for staged reuse and identity-coordinate constraints. No PR mutation, merge, push or real provider verification.

## 2026-09-16 dedicated login local review

### Revised interaction and authorized lab reset

- User authorized resetting only `skillhub-identity-lab-review`. Its containers, five volumes and generated state were removed; test passwords, OIDC secrets, encryption key and self-signed certificates were regenerated. No old credentials were backed up; only the credential-free Chrome helper and Nginx config were retained temporarily. Other Docker projects were untouched.
- Recreated the existing lab services and moved the existing preview Web container into the same Compose project. No additional long-running service type or production dependency was introduced. Disposable Keycloak organizations/connections were seeded again using the existing script.
- Local-only ignored `preview.compose.yml` mounts the current built backend JAR and frontend dist and labels containers `uncommitted-login-preview`. The committed harness's clean-tree/exact-SHA guards are unchanged. This remains a development preview, not release certification.
- Revised UI: fixed-height split shell; segmented enterprise/personal switching; `/register` uses the same shell and existing validation/API; discovery replaces input with real returned organization/options and supports changing enterprise. Only configured OAuth buttons appear.
- Targeted frontend regression after the visual fix batch: `pnpm exec vitest run src/pages/login.test.tsx src/pages/register.test.tsx src/features/auth/enterprise-login-discovery.test.tsx src/features/auth/login-button.test.tsx --maxWorkers=2`: **4 files / 15 tests passed**.
- Full frontend regression after the visual fix batch: `pnpm exec vitest run --maxWorkers=2`: **207 files / 804 tests passed**. `pnpm run typecheck`, `pnpm run lint`, `pnpm run build`, `git diff --check`, OpenSpec strict validation and enterprise architecture check/self-test passed. Build includes TypeScript project checking. Existing Vite runtime-config/dynamic-import/large-chunk warnings remain.
- Independent visual review identified missing registration segmented entry and misleading unconfigured OAuth subtitle; both were fixed in one UI batch. The reviewer confirmed both resolved and returned `ship` at that two-fix scope. See [design-qa.md](../../../design-qa.md).
- Windows Chrome CDP revised interaction: **passed** at 1366×768 and 390×844. Desktop document has no vertical/horizontal overflow; brand headline bounds are identical across enterprise/personal/register. At 1366×550 registration scrolls only its main area. `/register` route and its email field are present in the shared shell. Real lab discovery and Keycloak redirect passed; no authentication credentials entered, no callback/registration submission repeated. Exceptions and console errors: **0**.
- Captures/report: ignored state `reports/login-ui-{desktop,password,register,mobile,discovery}.png` and `reports/login-ui-browser.json`. Initial inspection raced backend readiness/lazy loading; the helper now waits for rendered controls. A transient Windows/WSL process-launch timeout succeeded on retry. These failures were not recorded as successful checks.
- Final post-fix Chrome rerun passed all recorded flags, including registration's retained segmented switch/local-only subtitle and short-screen containment, with 0 exceptions/console errors. A frame-based resize wait stalled in the background tab and was terminated; a bounded native delay replaced it. The final report reflects the completed rerun, not that interrupted attempt.
- Lab startup requires a full first Keycloak realm import under the existing 1 CPU limit. Current lab remains six protocol/dependency services plus the existing Web preview, now Compose-managed and limited to 128 MiB. No production registry/data accessed and no PR/push.

This subsection describes an **uncommitted worktree preview**, not the historical exact-SHA candidate below. The previous assertion of byte-identical `server/` and `web/` trees applies only to the older candidate. Current login modifications need a new exact-SHA release record after user acceptance.

- Worktree: `skillhub-enterprise-login-release`, branch `feature/enterprise-login-release`.
- Preview: `http://127.0.0.1:13000/login`; full enterprise redirect uses the mapped Windows Chrome window at `https://skillhub.identity-lab.test:18445/login`.
- Local backend JAR and frontend static build were copied into the existing isolated review containers. This is a development preview: image revision labels do not certify the replaced artifacts.
- No new services, dependencies, migrations, real users, PR or push. Existing test-only Keycloak and data are reused.
- Server command: `MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am package -Dtest=AuthMethodCatalogTest,AuthControllerTest,EnterpriseLoginAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false`: **22 passed**, package succeeded.
- Web: `pnpm exec tsc --noEmit`, `pnpm run lint`, `pnpm run build`: passed. Existing runtime-config/dynamic-import/large-chunk build warnings remain.
- Web full suite before final test additions: **206 files / 798 tests passed**. Final targeted suite including discovery and configured OAuth rendering: **4 files / 13 tests passed**.
- OpenSpec strict validation, enterprise architecture check/self-test and UI mechanical detector passed; asset provenance scan found no missing metadata.
- Real Windows Chrome CDP: desktop 1487×1058 and mobile 390×844; enterprise default, mutually exclusive switching, no horizontal overflow, no marketplace navigation, real Organization lookup and redirect to Keycloak all passed. No credentials were entered and full authentication/callback was not repeated in this UI review.
- Browser exception and console-error counts: zero. Reports/screenshots are in ignored lab state `reports/login-ui-*`.
- Visual review and remaining scope: see [design-qa.md](../../../design-qa.md).
- Existing broader Playwright E2E file was updated for the capability catalog but not rerun; full backend/staging/exact-SHA release matrices were not rerun for this scoped UI change.
- Operational incident: a process-list diagnostic printed the lab-generated trust-store password into tool output. It was not a real API token and was not copied into source or reports. Treat that disposable lab password as exposed; reset/recreate this exact lab after manual acceptance, not production credentials.

The prior backend JAR is preserved in ignored lab state as `app-pre-login-ui.jar`. Containers remain running for manual review. Cleanup/reset requires a separate user decision so review data is not removed while acceptance is in progress.

## Candidate

- Runtime candidate SHA: `854a4f0b067e5cc8a9fb78c3528f86fc68bb5526`
- Base `main` SHA: `36de54157bff59c18c5eff255d2c158df45a3e2a`
- Scope: unified identity core, minimal Organization security boundary and dynamic OIDC
- Excluded: SAML, CAS, LDAP login, Directory/SCIM, Identity Link, Account Merge and Namespace entitlement automation

This document is a verification record layered on the tested runtime candidate. Its own documentation
commit does not alter `server/` or `web/`; both trees remain byte-identical to the fully tested source
trees recorded below.

This evidence applies to the complete Release 1 reference candidate. If the implementation is
reassembled into the staged merge units defined in [rollout-plan.md](./rollout-plan.md), every unit
must produce a new exact-SHA verification record; this document cannot be used as a substitute.

## Built artifacts

| Artifact | Image ID | Revision label |
|---|---|---|
| `skillhub-server:staging` | `sha256:bef814a5352ce590f0a48236e9b8c88f60d67d5d2952699c18b57faaa2660c6a` | candidate SHA |
| `skillhub-web:enterprise-login-candidate` | `sha256:9cf47c0eb6d00bef9a028114e4707caec0b0d3ec3c58ad291a04cd2d391a20f3` | candidate SHA |

The frontend image returned HTTP 200 from `/nginx-health` and `/` with the production-equivalent
`SKILLHUB_API_UPSTREAM` setting. The backend image reported healthy in both OIDC lab profiles.

## Source and migration verification

| Verification | Result |
|---|---|
| Full Java 21 backend suite | 1,029 passed, 0 failed, 1 skipped |
| Frontend unit suite | 206 files and 794 tests passed |
| Frontend typecheck, lint and production build | passed |
| `ExternalIdentityV2MigrationTest` on Java 21 | 9 passed, including populated V53 to V60 preservation and canonical digest |
| Fresh candidate database | Flyway V60, success |
| Runtime OpenAPI regenerated against candidate image | byte-identical to checked-in `schema.d.ts` |
| Strict OpenSpec and architecture checks | passed |

The complete backend and frontend suites ran at `acce4b9c87310f617133dbc445c15201a6fe39df`.
The corresponding Git tree IDs are unchanged at the runtime candidate:

- `server/`: `1ec4e4b5ff1273cbc06cc7f134c9cc6ffcc3dac4`
- `web/`: `02b9771b21698ac3a5ff977606758db55916cbef`

Later commits only modify identity-lab controls, documentation and seed/verifier behavior. The
candidate-specific V53 to V60 migration test was also rerun after those commits.

This migration evidence proves the expand-only schema chain and legacy-data preservation for the
complete Release 1 reference candidate. It does **not** make public GitHub/GitLab OAuth V2
persistence a gate for the staged R1-A rollout: R1-A keeps legacy `identity_binding` authoritative
for public OAuth, routes facts through the unified identity-core decision gate, and fails closed in
ACTIVE mode before creating active or pending legacy bindings when the unified decision returns
Denied or Conflict.

## Runtime acceptance

### OIDC fast profile

- Exact candidate SHA and backend image revision matched.
- Fresh PostgreSQL database migrated to V60.
- OIDC discovery, metadata, JWKS, authorization-code callback, JIT association and Session origin passed.
- An ID Token with the wrong audience failed closed without creating an association.
- Generated report: L2, passed, `containsCredentials: false`.
- Observed steady-state memory was approximately 788 MiB across five bounded containers.

### OIDC real profile

- Digest-pinned Keycloak, Caddy, CoreDNS, PostgreSQL and Valkey ran with the candidate SkillHub image.
- Containerized real Chromium authorization-code journey passed.
- First-login binding, persisted Session origin, disabled upstream user rejection and
  cross-Organization subject collision rejection passed.
- Generated report: L3, passed, `containsCredentials: false`.
- Observed steady-state memory was approximately 1.44 GiB across six bounded containers.
- The short-lived Playwright container was removed automatically.

### Windows browser

Windows Chrome `152.0.7977.83` was launched with one complete host-resolver argument for the two
lab-only domains. The following checks passed through the real Keycloak page:

- discovery and redirect;
- IdP credential authentication;
- callback and enterprise Session establishment;
- first association;
- logout and subsequent `/api/v1/auth/me` rejection;
- repeat login reusing exactly one External Identity and one user.

The Windows browser Profile was isolated from the user's normal Profile. All 11 processes using
that exact Profile were stopped, and the Profile directory was moved to the Windows recycle bin.

## Security and rollback

- Invalid state, nonce, signature, audience, issuer, callback replay and unsafe return targets are
  covered by passing OIDC unit/contract tests.
- Cross-Organization collision and disabled upstream account behavior passed against Keycloak.
- Member, Organization and Login Connection authority changes reject stale access before Session TTL.
- Every generated lab secret is compared against every JSON report and persistent container log.
- The lab now avoids `JAVA_TOOL_OPTIONS`, which exposed the generated trust-store password in JVM
  startup output; the regression is enforced by the lab contract test.
- Exact-candidate rollback order passed: remove Organization allowlist, disable enterprise OIDC
  login, switch identity core to `LEGACY`, then restore. Each disabled state returned a structured
  fail-closed response while the server remained healthy and database data remained at V60.

## Cleanup and merge assessment

The exact fast, real, rollback and Windows-browser lab projects were reset. Their containers,
networks, volumes, generated credentials and transient browser artifacts are absent. No production
service, shared database or real identity was used.

The runtime candidate contains 52 purpose-specific commits because the Release 1 architecture was
developed in small reviewable stages; this record adds one documentation-only commit. A path and
commit-title audit found no Directory/SCIM, CAS, SAML, Skill Suite or unrelated product
implementation mixed into the branch. The PR will still be large because the release intentionally
introduces the unified core, Organization boundary, OIDC adapter, management API/UI, migrations and
their security tests together.

Release 1 is technically ready for review and merge. It must still follow normal repository CI,
DCO/CLA and human review gates. This verification does not authorize a push, PR, merge, tag,
deployment or package release.
