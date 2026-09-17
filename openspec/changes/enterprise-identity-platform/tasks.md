## 1. Release boundary and architecture

- [x] 1.1 Freeze Release 1 as unified identity core, minimal Organization boundary and dynamic OIDC only.
- [x] 1.2 Explicitly exclude SAML, CAS, LDAP login, Identity Link, Account Merge, Directory/SCIM and Namespace entitlement automation.
- [x] 1.3 Add an automated architecture check that keeps protocol types out of organization/directory/entitlement domain packages and prevents adapters from owning identity decisions.
- [x] 1.4 Define the staged rollout and non-destructive rollback order.

## 2. Organization security boundary

- [x] 2.1 Add Organization, verified domain, Membership and role-binding models with tenant-scoped uniqueness and optimistic locking.
- [x] 2.2 Implement Organization and Membership lifecycle guards with authority-version increments only on effective state changes.
- [x] 2.3 Separate platform Organization creation from tenant administration; verify platform roles do not bypass Organization membership.
- [x] 2.4 Implement stable, bounded Organization/member/domain/role list and management APIs.
- [x] 2.5 Record allowlisted, redacted audit events for Organization administration.
- [x] 2.6 Add minimal Organization and member/domain administration pages without Namespace mapping or directory controls; member-only organizations are listed without a management-detail entry.

## 3. Unified identity and compatibility

- [x] 3.1 Define versioned Adapter descriptors, interaction models, normalized assertions and standard failure taxonomy.
- [x] 3.2 Implement the single identity decision module for binding, pre-provisioned subject, verified-email correlation, JIT and account/membership guards.
- [x] 3.3 Persist External Identity V2 and pre-provisioned immutable subjects with concurrency-safe uniqueness.
- [x] 3.4 Implement field-authority-aware profile resolution and block unverified email from trusted fields.
- [x] 3.5 Persist enterprise session origin using a one-way session-key digest and Organization/Membership authority versions.
- [x] 3.6 Recheck enterprise authority before Device Flow redemption and enterprise-resource access.
- [x] 3.7 Route legacy public OAuth facts through the unified core decision gate while preserving LEGACY/SHADOW compatibility and legacy binding persistence.
- [x] 3.8 Keep legacy public OAuth bindings authoritative in R1-A; fail closed on ACTIVE unified-core Denied/Conflict before active or pending legacy writes.

## 4. Login Connection and dynamic OIDC

- [x] 4.1 Implement immutable Login Connection revisions, lifecycle rules, test-before-activate and runtime snapshot materialization.
- [x] 4.2 Encrypt client secrets separately from typed configuration and expose only redacted secret summaries.
- [x] 4.3 Implement secure OIDC discovery/JWKS retrieval with outbound target, redirect, timeout and body-size controls.
- [x] 4.4 Implement one-time OIDC authorization transactions bound to state, nonce, PKCE, connection revision, browser and configured callback origin.
- [x] 4.5 Validate token endpoint response and ID Token issuer, algorithm, signature, key, audience/azp, expiry, issued-at and nonce.
- [x] 4.6 Implement enumeration-resistant login discovery plus anonymous start/callback endpoints using opaque public handles.
- [x] 4.7 Make dynamic OIDC default-off and require both the global switch and Organization allowlist.
- [x] 4.8 Add bounded anonymous rate limits and redact all query values and authentication secrets from logs/errors.
- [x] 4.9 Implement Organization Login Connection management UI and API for create, revision, test, activate, suspend and disable.

## 5. Database and contract convergence

- [x] 5.1 Place enterprise authentication migrations at V54–V60 after main's Skill Suites V49–V53 migrations.
- [x] 5.2 Add migration guardrails for immutable released migrations, legacy backfill safety and required uniqueness constraints.
- [x] 5.3 Run empty-database migration from V1 through V60 using the exact candidate image.
- [x] 5.4 Run upgrade migration from a main/V53 database through V60 and verify public OAuth legacy bindings remain the runtime write authority; V60 V2 shadow backfill is only a consistency and rollback aid.
- [x] 5.5 Regenerate checked-in OpenAPI types from the candidate source and prove no uncommitted generated diff remains.

## 6. Source verification

- [x] 6.1 Pass strict OpenSpec validation for this three-capability release scope.
- [x] 6.2 Pass enterprise architecture check and its negative self-test fixtures.
- [x] 6.3 Pass targeted migration, connection, controller, association, session, logging and rate-limit tests.
- [x] 6.4 Pass full backend test suite from a clean candidate worktree.
- [x] 6.5 Pass Web typecheck, lint and unit tests.
- [x] 6.6 Pass generated OpenAPI freshness and repository diff/secret scans.

## 7. Exact-SHA runtime acceptance

- [x] 7.1 Freeze the candidate SHA and build backend/frontend images only from that SHA; record image digests.
- [x] 7.2 Start a resource-bounded isolated OIDC reference lab with PostgreSQL and Redis and prove health/readiness.
- [x] 7.3 Prove existing local/OAuth configuration, CLI Device Flow and API Token compatibility with enterprise OIDC disabled.
- [x] 7.4 Prove discovery, redirect, IdP authentication, callback, first association, repeat login and logout in a real Windows browser.
- [x] 7.5 Prove invalid state/nonce/signature/audience/issuer, callback replay, cross-Organization subject collision and unsafe return targets fail closed.
- [x] 7.6 Prove member/Organization/connection suspension rejects stale enterprise access without waiting for session expiry.
- [x] 7.7 Prove logs and generated reports contain no token, authorization code, state, nonce, client secret, cookie or test password.
- [x] 7.8 Exercise allowlist removal, enterprise OIDC disable and identity-core LEGACY rollback in order.
- [x] 7.9 Stop the lab and prove containers, temporary networks, volumes, credentials and browser artifacts are cleaned.
- [x] 7.10 Produce one exact-SHA readiness report; only then request permission to push or create a PR.

## 8. Configuration-driven dedicated login page (local review)

- [x] 8.1 Replace embedded login card with a dedicated responsive split-screen shell.
- [x] 8.2 Advertise enterprise discovery using existing OIDC switches, allowlist and ACTIVE identity core.
- [x] 8.3 Render only configured methods and mutually exclusive enterprise/account panels; preserve existing direct-password/bootstrap integration.
- [x] 8.4 Test configuration combinations, catalog loading/error/empty states, validation, retained input and discovery request errors.
- [x] 8.5 Pass targeted backend tests, frontend checks and OpenSpec/architecture validation.
- [x] 8.6 Complete final Windows Chrome desktop/mobile interaction and visual review.
- [ ] 8.7 Obtain user acceptance and repeat exact-SHA release checks after committing the approved UI; no PR/push authorization is implied.
- [x] 8.8 Rotate the authorized review lab credentials, certificates and disposable test data without touching other projects.
- [x] 8.9 Replace accordion interaction with segmented entry switching and shared registration shell; retain existing registration validation/API.
- [x] 8.10 Recheck laptop/short-screen/mobile layouts, fixed brand position, discovery redirect and full frontend regression for the revised interaction.
- [x] 8.11 Compact login controls after user feedback and prove the right main does not overflow at ordinary/scaled desktop sizes; retain short-screen scrolling.
- [x] 8.12 Preserve the login source and default successful authentication to homepage; test catalog-driven public providers independently of enterprise discovery. Real vendor authentication remains a separate credential-dependent acceptance check.

## 9. Public Provider Adapter follow-ups

- [x] 9.1 Document provider protocol versus login context as a first-class architecture boundary: public provider login must not create Organization Membership, enterprise session origin, or Namespace permissions.
- [x] 9.2 Route GitHub/GitLab public OAuth through the unified identity core decision gate while retaining legacy `identity_binding` persistence.
  - [x] 9.2.1 Make ACTIVE public OAuth fail closed on unified-core Denied/Conflict before creating active or pending legacy bindings; LEGACY and SHADOW keep existing behavior.
  - [ ] 9.2.2 Optional follow-up: evaluate whether platform-scoped public OAuth should switch runtime write authority to V2 after R1-A is stable; do not block unified authentication rollout on this cutover.
- [ ] 9.3 Rework the Feishu PR as a public Provider Adapter first, with stable subject, verified email semantics, catalog rendering, real login validation, and no enterprise side effects.
- [ ] 9.4 Rework the DingTalk PR as a public Provider Adapter first; define a stable primary subject and explicit alias/migration policy before any merge.
- [ ] 9.5 Add a repeatable public-provider acceptance path using real vendor test credentials or an explicit reference service for each provider; mocked catalog buttons only prove presentation and navigation.
- [ ] 9.6 Add enterprise Feishu/DingTalk Login Connection adapters only after public-provider behavior is stable, reusing protocol code but adding Organization/LoginConnection context and enterprise session guards.
