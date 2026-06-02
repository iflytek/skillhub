# LibreChat SkillHub Hermes Integration Implementation Plan

## Status

Retired historical draft. Do not follow this plan for new Glaux implementation work.

Glaux has pivoted away from LibreChat in favor of Control Tower-governed Hermes Desktop/Web. The
active roadmap is in the parent Glaux workspace:

- `../docs/phase-1-implementation-plan.md`
- `../docs/hermes-desktop-transition-plan.md`
- `../docs/decisions/ADR-002-hermes-desktop-frontend-pivot.md`

This plan remains only as historical SkillHub integration evidence. New implementation should mine
frontend-agnostic SkillHub lessons from it when useful, but must not build LibreChat-specific
routes, service credentials, profile-pin storage, or runtime sync contracts from this document.

## Principles

- Land the feature in small, independently testable slices.
- Keep SkillHub, Control Tower, Hermes Desktop/Web, and Hermes Agent changes behind feature flags until the full flow is ready.
- Prefer additive contracts and adapters over replacing existing systems in one step.
- Do not trust browser-supplied authorization state.
- Treat Control Tower permission as necessary and SkillHub lifecycle enforcement as still mandatory.

## Prerequisites And Integration Config

Required before code work starts:

- Agreed branches or worktrees for SkillHub, Control Tower, LibreChat, and Hermes.
- Internal-only SkillHub network route for integrated deployment.
- Service credentials for LibreChat to Control Tower, LibreChat to SkillHub, and Hermes worker to LibreChat.
- Control Tower decision-token private signing key, key ID, public-key publication path, and rotation plan.
- SkillHub decision-token public verification keyring and replay store.
- Redis or equivalent durable replay/nonce store for SkillHub decision tokens.
- Clock-skew policy: 60 seconds accepted skew, 5 minute decision-token TTL.
- Feature flags and defaults from the rollout table below.
- Contract version header shared by LibreChat, Control Tower, SkillHub, and Hermes adapters.

Minimum config surface and ownership:

| Repo | Required Config | Secret/Rotation Owner | Rollback Owner |
| --- | --- | --- | --- |
| Control Tower | `CONTROL_TOWER_SKILLHUB_INVENTORY`, `CONTROL_TOWER_SKILLHUB_REPORT_ONLY`, SkillHub inventory source URL, decision-token key ID/private key, public-key publication endpoint or config, decision-token TTL, contract version. | Control Tower platform owner rotates signing keys and publishes active/previous `kid` metadata. | Control Tower platform owner disables inventory sync and token issuance. |
| SkillHub | `SKILLHUB_INTEGRATED_MODE`, Control Tower issuer/audience, public verification keyring, replay store, internal endpoint service auth, contract version. | SkillHub operator owns verification keyring sync and replay-store availability alerts. | SkillHub operator disables integrated mode; standalone APIs remain enabled. |
| LibreChat | `LIBRECHAT_SKILLHUB_REGISTRY`, `LIBRECHAT_SKILLHUB_PINS`, `LIBRECHAT_SKILLHUB_RUNTIME_REQUESTS`, SkillHub internal base URL, Control Tower base URL, service credentials. | LibreChat operator owns service credentials to Control Tower and SkillHub. | LibreChat operator disables registry, pin mutation, and runtime request flags. |
| Hermes | `HERMES_SKILLHUB_SYNC`, `HERMES_SKILL_INVOKE_ENFORCEMENT`, LibreChat worker API base URL, worker service credential, per-user profile root. | Hermes operator owns worker service credential and profile-root permissions. | Hermes operator disables sync; synced bundles remain ignored. |

Dashboards and alerts must exist before Phase 6 enforcement for Control Tower decision latency/denial rate, SkillHub token validation failures, SkillHub replay-store failures, LibreChat pin invalidation rate, Hermes sync failures, and Hermes invocation denials.

## Shared Contract Artifacts

The technical design plus the fixture pack are the normative documentation contract until a generated contract package exists. Phase 0 must add golden fixtures consumed by all four repos for resource IDs, decision tokens, batch decisions, inventory records, artifact fingerprints, runtime envelope entries, internal API request/response shapes, and error shapes.

The source-of-truth fixture pack lives in the SkillHub repo at `docs/contracts/librechat-skillhub-hermes/v1/` until a shared contract package is published. Other repos may vendor or mirror these fixtures, but the SkillHub copy is reviewed as the canonical input for V1. Fixture changes require reviewers from SkillHub, Control Tower, LibreChat, and Hermes when they change cross-repo behavior.

Phase boundary rule: Phase 1 Control Tower work consumes the Phase 0 fixture pack and fixture validation command only. Live SkillHub capability export is implemented in Phase 2 and exercised end-to-end in Phase 6. Phase 1 must not claim live inventory sync acceptance until the Phase 2 SkillHub export endpoint exists.

Required fixture files:

- `resource-ids.valid.json`
- `resource-ids.invalid.json`
- `capability-inventory.records.json`
- `batch-decisions.responses.json`
- `decision-token.allow.json`
- `decision-token.rejections.json`
- `artifact-fingerprints.json`
- `profile-pin.records.json`
- `runtime-envelope.requested-skills.json`
- `internal-api.errors.json`
- `internal-api.requests-responses.json`

Versioning rule: additive fixture fields are allowed within `v1` when consumers ignore unknown fields; renamed, removed, or semantic-breaking fields require a new `v2` directory and explicit migration notes.

| Artifact | Producer | Consumers | Review Owner | Acceptance Test |
| --- | --- | --- | --- | --- |
| Resource ID grammar | Control Tower | All repos | SkillHub + Control Tower | Golden valid/invalid IDs pass in all repos. |
| Capability inventory record | SkillHub | Control Tower, LibreChat | Control Tower | Phase 1 imports fixture records without local transforms; Phase 2/6 prove live SkillHub export imports the same shape. |
| Signed decision token | Control Tower | SkillHub, LibreChat | Security reviewer | SkillHub accepts golden asymmetric token and rejects tampered variants without sharing Control Tower private keys. |
| Artifact fingerprint | SkillHub | LibreChat, Hermes | SkillHub + Hermes | Same downloaded bundle bytes hash to the same `sha256:{hex}` value. |
| Batch decision response | Control Tower | LibreChat | LibreChat | LibreChat maps allow/deny/requestable states without guessing. |
| Profile pin schema | LibreChat | Hermes, Control Tower | Hermes | Hermes sync accepts exact pin and rejects missing fingerprint. |
| Runtime `requested.skills` shape | LibreChat | Control Tower, Hermes | Control Tower | Envelope fixture includes `skill.invoke` with `SKILL_VERSION`. |
| Error envelope | Owning service | All callers | API reviewers | Callers handle denied, unavailable, replay, validation, lifecycle conflict, and scanner-blocked errors consistently. |

## Phase 0: Documentation And Contract Review

- Add PRD, technical design, and implementation plan documents.
- Review the docs with two independent reviewers per document.
- Reconcile review findings before starting code changes.
- Update docs to use `skill.invoke` consistently.
- Record review dispositions in `docs/librechat-skillhub-hermes-review-log.md`.
- Add the first version of shared golden fixtures before implementation begins.
- Add `scripts/validate-skillhub-contract-fixtures.sh` in SkillHub and equivalent commands in Control Tower, LibreChat, and Hermes before Phase 1 code merges. The SkillHub command must load every fixture, validate JSON syntax, verify exact-byte body hashes, verify asymmetric token signatures against the fixture public key, assert token rejection coverage, and fail when required request/response or error cases are missing.

Verification:

- Documentation review findings are recorded or resolved.
- `git diff -- docs/prds/librechat-skillhub-hermes-prd.md docs/librechat-skillhub-hermes-design.md docs/librechat-skillhub-hermes-implementation-plan.md` is reviewed before code starts.
- Golden fixtures exist under `docs/contracts/librechat-skillhub-hermes/v1/`.
- Each repo has a real fixture validation command that loads the SkillHub fixture pack before Phase 1 code merges. Planned or stubbed commands do not satisfy the Phase 0 exit gate; as of this document patch, those commands are required follow-up artifacts, not existing commands.
- The PRD is visible in normal `git status` and `git diff` output, either by an explicit `.gitignore` exception or by moving it outside an ignored directory.

## Phase 1: Control Tower Capability And Policy Foundation

Scope:

- Add or confirm `SKILL_NAMESPACE`, `SKILL`, `SKILL_VERSION`, and `SKILL_REVIEW_TASK` in Control Tower capability types and Cedar resource mapping.
- Define SkillHub resource ID helpers for namespaces, containers, versions, and review tasks.
- Add integrated publish/rerelease validation for SkillHub versions used by this contract: `[0-9A-Za-z][0-9A-Za-z._+-]{0,127}`, excluding `/`, whitespace, `%`, and `@`.
- Add SkillHub capability inventory import helpers against the Phase 0 fixture pack. Do not require a live SkillHub export endpoint in this phase.
- Add SkillHub inventory-backed registry search that evaluates `skill.view` before LibreChat hydrates SkillHub details.
- Add or extend batch effective permission evaluation for concrete action/resource checks.
- Add signed decision tokens for allowed protected SkillHub operations, bound to audience, method, path, action, resource, workspace, expiry, nonce, and exact body hash for every protected request body.
- Ensure `skill.invoke` is the canonical runtime action in docs and code paths.

Tests:

- Unit tests for resource ID normalization and validation.
- Unit tests for asymmetric signed decision token creation and verification, expiry, tamper detection, wrong audience, wrong method, wrong path, wrong query hash, wrong body hash, tenant/workspace mismatch, unknown `kid`, active/previous key rotation overlap, clock-skew boundaries, replay rejection, replay-store unavailable fail-closed behavior, and canonical method/path/query/body-hash normalization.
- Unit tests proving `LIMIT` and `DENY` decisions do not receive SkillHub decision tokens and are rejected if presented to SkillHub.
- Contract tests for fixture-based SkillHub inventory import creating namespace, container, version, and pending review-task capabilities as `SKILL_NAMESPACE`, `SKILL`, `SKILL_VERSION`, and `SKILL_REVIEW_TASK`.
- Fixture-delta tests for hide, yank, archive, delete, scanner-blocked policy changes, pending review creation, review approval, review rejection, review withdrawal, stale review-task disablement, and restore.
- Integration tests for batch decisions resolving LibreChat external users through Control Tower subject mapping.
- Envelope tests showing requested skills compile as `skill.invoke` resources.

Acceptance:

- Control Tower can represent SkillHub namespaces, containers, versions, and review tasks.
- LibreChat backend can request multiple skill decisions in one service call.
- Protected SkillHub operation decisions can be passed as short-lived signed tokens.
- Control Tower search returns only fixture-imported resources allowed for `skill.view` and safe to hydrate. Live SkillHub inventory sync acceptance is deferred until Phase 2/6.

## Phase 1A: SkillHub Lifecycle And Distribution Safety Prerequisites

Scope:

- Align current SkillHub manual and auto-withdraw code paths with `docs/14-skill-lifecycle.md`: pending review withdrawal returns the version to `DRAFT`, not `UPLOADED`.
- Add direct-publish distribution safety so integrated resolve/download/sync/runtime treats direct-published versions as not download-ready until scanner/package policy is acceptable.
- Ensure `SkillDownloadService` and integrated download paths reject `PUBLISHED` versions whose scanner verdict is pending, failed, expired, missing when required, or policy-blocked.
- Add migration notes for existing withdrawn `UPLOADED` versions if any deployed data needs reconciliation before integrated enforcement.

Tests:

- Withdraw tests prove only the original Control Tower subject can withdraw a pending review and the version transitions to `DRAFT`.
- Auto-withdraw tests prove publishing a replacement pending version also returns the old pending version to `DRAFT`.
- Direct-publish tests prove a newly direct-published version is not installable, downloadable, syncable, or invokable until scanner/package policy marks the exact artifact download-ready.
- Download tests cover scanner pending, scanner failed, scanner unavailable, scanner expired, and scanner-blocked overlays on otherwise `PUBLISHED` versions.

Acceptance:

- Manual and auto-withdraw both transition `PENDING_REVIEW -> DRAFT`.
- A direct-published version cannot be distributed through integrated endpoints until scanner/package policy permits distribution.
- This phase is complete before Phase 2 internal endpoints are exposed beyond local contract tests.

## Phase 2: SkillHub Internal Integration Layer

Scope:

- Add integrated deployment mode that makes protected registry operations service/internal-only.
- Add internal configuration for trusted Control Tower decision validation.
- Add request filter or argument resolver that validates signed decision context and projects actor context.
- Add explicit integration permission adapter for internal endpoints. Do not fabricate local SkillHub roles from Control Tower decisions.
- Add replay protection for decision token nonce.
- Reject `LIMIT` and `DENY` tokens at SkillHub; V1 protected integration operations accept only signed full `ALLOW`.
- Add internal integration endpoints for:
  - Capability inventory export for Control Tower service principals with `control-tower:capability-read`.
  - Hydrate allowed search/detail results by resource ID.
  - Exact version resolve and download.
  - Draft package validation.
  - Publish submission.
  - Review/admin actions.
  - Audit read.
- Keep controllers transport-only and place workflow orchestration in app services.
- Preserve existing public and CLI APIs for standalone deployments.
- Add ingress/app-level tests proving raw user-facing SkillHub protected routes cannot bypass Control Tower in integrated mode.

Tests:

- Filter/service tests reject missing, empty, duplicate, malformed, expired, tampered, mismatched, replayed, `LIMIT`, `DENY`, unknown-key, wrong-issuer, wrong-query-hash, duplicate-claim, unsupported-algorithm, and replay-store-unavailable decision tokens.
- Controller tests verify protected endpoints require trusted decision context.
- Negative tests reject missing/invalid service bearer, valid decision token without service auth, wrong service principal, missing/wrong contract version, and missing `Idempotency-Key` on mutating endpoints.
- Contract tests verify internal endpoint request/response shapes, status codes, error envelopes, headers, pagination, and idempotency behavior against the shared fixture pack.
- Hydrate tests prove each requested resource ID requires its own matching `skill.view` token and extra IDs in the request are not returned.
- App service tests verify SkillHub lifecycle restrictions still apply after Control Tower allow decisions.
- Publish tests cover warning confirmation, scanner status, review vs direct publish, and audit actor projection.
- Download tests cover exact version pins and yanked/hidden/archive restrictions.
- Dedicated install/runtime download tests prove only exact `PUBLISHED`, scanner-acceptable, fingerprinted versions are returned.
- Admin action tests cover `skill.review.decide`, `skill.review.withdraw`, `skill.archive`, `skill.visibility.manage`, `skill.yank`, `skill.label.attach`, `skill.label.definition.manage`, `skill.audit.read`, `skill.publish`, and `skill.publish.direct`.
- Publish authorization tests prove new skills require namespace-scoped `skill.publish` or `skill.publish.direct`, existing skills require container-scoped tokens, and namespace/slug mismatches between token and request are rejected.
- Live inventory sync tests import SkillHub capability export into Control Tower and verify deltas disable stale capabilities.

Acceptance:

- SkillHub can serve internal LibreChat registry operations without trusting browser headers.
- SkillHub audit includes mapped LibreChat external user and Control Tower subject.
- Control Tower allow does not bypass SkillHub lifecycle or package safety.
- Direct raw SkillHub protected calls fail in integrated mode without signed Control Tower context.

## Phase 3: LibreChat Backend Integration

Scope:

- Add feature flags for SkillHub registry integration.
- Add Control Tower batch permission client methods.
- Add SkillHub integration client methods.
- Add per-user SkillHub skill pin storage.
- Require artifact fingerprint on every installed pin.
- Add backend routes for:
  - Registry search/list/detail.
  - Install pinned version.
  - Remove installed pin.
  - Update pin to selected version.
  - Draft validate.
  - Draft publish confirmation submission.
  - Admin review actions.
- Add run request population from server-side profile pins behind a disabled runtime flag.
- Hard gate: do not send SkillHub pins in `requested.skills` until Phase 5 per-invocation denial tests pass and `HERMES_SKILL_INVOKE_ENFORCEMENT` is enabled in the same target environment.

Tests:

- Backend route tests for allowed and denied decisions.
- Pin persistence tests for install, remove, and explicit update.
- Draft publish tests requiring confirmation.
- Run request tests proving browser-supplied skill IDs are ignored.
- Disabled-flag tests proving pinned skills are not sent in `requested.skills` before runtime enforcement is enabled.
- Failure tests for Control Tower or SkillHub unavailable states.

Acceptance:

- Browser talks only to LibreChat for registry operations.
- User profile pins are exact version resource IDs.
- Denied users cannot install or publish through LibreChat routes.
- Existing invalidated pins remain visible as disabled diagnostics and are not sent to Hermes.

## Phase 4: LibreChat UI

Scope:

- Add Skill Registry navigation under the Control Tower-enabled settings or tools surface.
- Add registry search, list, detail, version, install, update, and remove interactions.
- Add installed skills profile view.
- Add draft skill authoring/import surface if not already available in the selected LibreChat branch.
- Add publish confirmation UI that shows validation results and warnings.
- Add admin review/governance screens for authorized users.
- Use TanStack Query for server data and existing LibreChat UI conventions.

Tests:

- Unit/component tests for registry list, install, update, remove, and denied states.
- UI tests for publish confirmation and validation warnings.
- E2E smoke flow for allowed user installing a published skill.
- E2E smoke flow for denied user seeing no install action.

Acceptance:

- Users can complete registry and profile pin workflows in LibreChat.
- Admin surfaces are hidden or disabled without permission and backend still enforces decisions.
- UI handles loading, empty, denied, and error states.
- Disabled pins show the current lifecycle or policy reason and a safe update/remove action.

## Phase 5: Hermes SkillHub Sync And Runtime Enforcement

Scope:

- Add SkillHub source/download support for exact pinned version resource IDs.
- Add per-user server-worker skill profile paths.
- Sync pinned bundles through LibreChat only, with quarantine, scan, install, provenance, fingerprint, checksum, and lock metadata.
- Store synced SkillHub bundles outside legacy/local skill loader roots and require provenance `source=skillhub` plus exact resource ID before a loader can consider them.
- Add runtime guard that calls Control Tower invocation authorization before `skill.invoke`.
- Reject direct Hermes to SkillHub download paths in integrated mode.
- Treat Hermes publish as LibreChat draft proposal in V1.
- Add run activity events for sync success/failure and skill invocation authorization outcomes.

Tests:

- Unit tests for SkillHub resource ID parsing.
- Sync tests for exact version install and provenance recording.
- Tests proving a present-on-disk skill is denied without envelope or invocation authorization.
- Loader isolation tests proving a synced SkillHub bundle under a legacy/local loader root is ignored and a bundle in the SkillHub sync root is still denied without provenance, envelope membership, and per-invocation authorization.
- Tests proving version updates are not automatic.
- Worker profile isolation tests.
- Fingerprint mismatch tests discard the bundle and fail closed.
- Revalidation tests deny yanked, hidden, archived, rejected, scanner-blocked, deleted, and policy-revoked installed pins.

Acceptance:

- Hermes can sync and invoke allowed pinned SkillHub skills.
- Unauthorized skills cannot run even if installed locally.
- Publish attempts route to LibreChat draft confirmation rather than direct SkillHub publish.
- LibreChat may enable `requested.skills` population only after these denial tests pass and Hermes invoke enforcement is enabled in the same target environment.

## Phase 6: End-To-End Rollout

Scope:

- Enable report-only Control Tower decisions in a development deployment.
- Enable install/download enforcement.
- Enable publish/review enforcement.
- Enable runtime invocation enforcement.
- Add operational dashboards or logs for registry decision denials, sync failures, and invocation denials.
- Document rollback steps.

Tests:

- End-to-end: allowed user installs and uses a skill.
- End-to-end: denied user cannot see, install, download, or invoke a skill.
- End-to-end: draft to review to approved publish to install.
- End-to-end: direct raw SkillHub user path unavailable in integrated deployment.
- End-to-end revocation-after-sync: allowed user installs, Hermes syncs, invocation succeeds, then policy or lifecycle revokes the skill through yank, hide, archive, delete, scanner block, or Control Tower policy removal; inventory refresh disables the pin, LibreChat sends no `requested.skills`, and Hermes denies invocation while the local bundle remains on disk.
- Report-only: would-deny checks mint no SkillHub decision token and cannot mutate pins, drafts, downloads, synced bundles, publish/review state, governance state, or runtime invocation state.
- Smoke tests for existing standalone SkillHub search, publish, review, CLI, and download flows.
- Rollback tests turn all integration flags off after pins exist, bundles are synced, and a run is attempting invocation; expected behavior is no `requested.skills`, no local invocation, inert bundles, and green standalone SkillHub smoke flows.

Acceptance:

- Feature can be enabled incrementally.
- Rollback disables integrated UI and Hermes sync without breaking standalone SkillHub.
- Existing SkillHub tests and LibreChat/Hermes baseline flows remain green.

## Rollout Flags

| Flag | Repo | Default | May Enable After | Rollback Behavior |
| --- | --- | --- | --- | --- |
| `CONTROL_TOWER_SKILLHUB_INVENTORY` | Control Tower | off | Inventory sync tests pass. | Stop sync; leave capabilities disabled/inert. |
| `CONTROL_TOWER_SKILLHUB_REPORT_ONLY` | Control Tower | off | Visibility diagnostics and no-token would-deny tests pass. | Stop report-only diagnostics. |
| `SKILLHUB_INTEGRATED_MODE` | SkillHub | off | Internal endpoint and direct-bypass tests pass. | Internal endpoints disabled; standalone APIs unchanged. |
| `LIBRECHAT_SKILLHUB_REGISTRY` | LibreChat | off | Search/hydrate route tests pass. | Hide registry UI. |
| `LIBRECHAT_SKILLHUB_PINS` | LibreChat | off | Install/remove/update pin tests pass. | Pins read-only diagnostics; no mutation. |
| `HERMES_SKILL_INVOKE_ENFORCEMENT` | Hermes | off | Invocation authorization tests and fail-closed local-present denial tests pass. | SkillHub pinned skills cannot run. |
| `HERMES_SKILLHUB_SYNC` | Hermes | off | Sync, fingerprint, disabled-pin, and local-present denial tests pass with invoke enforcement enabled in the same environment. | Ignore synced bundles for governed runs. |
| `LIBRECHAT_SKILLHUB_RUNTIME_REQUESTS` | LibreChat | off | `HERMES_SKILL_INVOKE_ENFORCEMENT` is enabled in the same environment and Hermes denial tests pass. | Do not include pins in `requested.skills`. |

Partial rollout invariant: every flag combination that has synced bundles but lacks a valid envelope or invocation authorization must deny invocation. CI must include a flag-matrix test for `HERMES_SKILLHUB_SYNC`, `HERMES_SKILL_INVOKE_ENFORCEMENT`, and `LIBRECHAT_SKILLHUB_RUNTIME_REQUESTS`.

Cross-repo fail-closed matrix cases must also cover Control Tower inventory off or stale, SkillHub integrated mode off, LibreChat registry/pins on while SkillHub or Control Tower is unavailable, decision keyring unavailable, and replay store unavailable. Expected behavior is no pin mutation, no draft mutation, no download, no sync, no governance mutation, no runtime invocation, and no fallback to raw SkillHub routes.

## Rollback State Matrix

| Persisted State | Flag-Off Behavior |
| --- | --- |
| LibreChat profile pins | Remain readable as diagnostics; not sent to Hermes and not mutated. |
| Control Tower capabilities | Remain for audit; disabled or ignored by policy when inventory flag is off. |
| SkillHub internal endpoint config | Disabled when integrated mode flag is off. |
| SkillHub replay records | Expire normally; no cleanup required during rollback. |
| Hermes synced bundles | Ignored for governed runs; optional cleanup task can remove quarantine/install dirs. |
| Database migrations | Additive only; rollback relies on flags rather than destructive schema reversal. |

## Workstream Ownership

| Workstream | Owns | Blocks | Review Gate |
| --- | --- | --- | --- |
| Control Tower | Capability model, batch decision API, signed decision tokens, inventory sync, runtime envelope coverage. | SkillHub token validation, LibreChat registry decisions, Hermes invocation authorization. | API/security reviewers approve fixtures, token claims, and `skill.invoke` envelope behavior. |
| SkillHub | Trusted integration auth, internal endpoints, capability export, lifecycle and audit integration. | Control Tower inventory quality, LibreChat registry operations, Hermes bundle sync through LibreChat. | SkillHub domain/API reviewers approve lifecycle enforcement, endpoint contracts, and audit projection. |
| LibreChat | Backend adapters, profile pins, registry routes, UI surfaces, governed run request wiring. | Hermes runtime requests and user-facing publish/admin workflows. | LibreChat backend/UI reviewers approve server-side pin resolution, disabled states, and confirmation flows. |
| Hermes | SkillHub source support, per-user sync, provenance, invocation guard, draft proposal publish behavior. | Runtime enablement and `LIBRECHAT_SKILLHUB_RUNTIME_REQUESTS`. | Hermes runtime/security reviewers approve per-user isolation, fingerprint enforcement, and fail-closed invocation. |

## Implementation Slice Matrix

| Phase | Artifact | Validation | Done Signal |
| --- | --- | --- | --- |
| 0 | Golden fixtures and validation command | Contract validators in each repo | Fixture command exists and fails on malformed JSON, stale hashes, token signature drift, missing error cases, or unresolved token references. |
| 1 | Resource ID helpers | Unit tests in each repo | Same valid/invalid fixture behavior everywhere. |
| 1 | Decision token signer/verifier | Control Tower signer tests and SkillHub verifier tests | Golden asymmetric allow token accepted; all tamper fixtures rejected. |
| 1 | Fixture inventory import | Control Tower contract tests | Namespace/container/version/review-task capabilities imported from fixtures and deltas disable stale capabilities. |
| 1A | SkillHub review lifecycle alignment | SkillHub domain/app service tests | Manual and auto-withdraw both transition `PENDING_REVIEW -> DRAFT`. |
| 1A | Direct-publish scanner distribution gate | SkillHub app service tests | Direct-published exact versions remain undistributable until scanner/package policy is acceptable. |
| 1 | Artifact fingerprint fixtures | SkillHub + Hermes contract tests | Same bundle bytes produce and verify the same `sha256:{hex}` fingerprint. |
| 2 | SkillHub integrated mode | SkillHub controller/filter tests | Direct protected raw routes fail without signed context. |
| 2 | Live SkillHub capability export | SkillHub + Control Tower contract tests | SkillHub export imports into Control Tower without local transforms. |
| 2 | SkillHub internal download | SkillHub app service tests | Only published, scanner-acceptable exact versions with fingerprint and one-time internal handle download. |
| 2 | SkillHub audit read | SkillHub controller/app service tests | Audit reads require `skill.audit.read` and return redacted scoped records. |
| 3 | LibreChat profile pins | LibreChat backend tests | Pin CRUD stores exact version and fingerprint, ignores browser-supplied run IDs. |
| 4 | LibreChat registry UI | LibreChat component/E2E tests | Allowed, denied, disabled, and update states render correctly. |
| 5 | Hermes sync | Hermes skills tests | Bundle fingerprint verified and per-user profile isolated. |
| 5 | Hermes invocation guard | Hermes runtime tests | Local installed skill denied without envelope/invocation authorization. |
| 6 | Integrated deployment | Cross-repo smoke tests | Allowed and denied end-to-end flows pass under enforcement flags. |

## Suggested Verification Commands

Run the relevant subset after each slice:

- SkillHub backend: `make test-backend-app`
- SkillHub full staging gate before release: `make staging`
- SkillHub API regeneration against a running backend: `make generate-api`
- SkillHub API drift verification that starts its own backend/dependencies: `./scripts/check-openapi-generated.sh`
- SkillHub frontend if touched: `make typecheck-web` and `make lint-web`
- Control Tower: from `/Users/dev/Documents/Glaux/control-tower`, `UV_CACHE_DIR=.uv-cache uv run pytest`
- LibreChat full Control Tower-focused gate: from `/Users/dev/Documents/Glaux/librechat`, `npm run control-tower:ci`
- LibreChat targeted suites: `npm run control-tower:test:api`, `npm run control-tower:test:client`, and `npm run control-tower:test:data-provider`
- Hermes targeted tests: from `/Users/dev/Documents/Glaux/hermes-agent`, `scripts/run_tests.sh tests/skills/ tests/hermes_cli/ -- -q`
- Hermes full suite when runtime paths change: `scripts/run_tests.sh`

Contract and rollout gate commands to add before implementation handoff:

| Gate | Required Command |
| --- | --- |
| Fixture validation | Commands to add before Phase 1 code merges, not present yet: SkillHub `scripts/validate-skillhub-contract-fixtures.sh docs/contracts/librechat-skillhub-hermes/v1`; Control Tower `UV_CACHE_DIR=.uv-cache uv run pytest tests/contracts/test_skillhub_fixtures.py`; LibreChat `npm run control-tower:contracts -- --fixture ../skillhub/docs/contracts/librechat-skillhub-hermes/v1`; Hermes `scripts/run_tests.sh tests/contracts/test_skillhub_fixtures.py -- -q`. |
| Integrated allowed smoke | From integration workspace: `scripts/skillhub-integrated-smoke.sh --scenario allowed-install-invoke --env .env.skillhub-integrated`. Required flags: inventory, integrated mode, registry, pins, Hermes sync, Hermes invoke enforcement, and LibreChat runtime requests enabled. |
| Integrated denied smoke | From integration workspace: `scripts/skillhub-integrated-smoke.sh --scenario denied-no-see-install-download-invoke --env .env.skillhub-integrated`. Assertion: no bundle invocation even when a bundle is already present on disk. |
| Revocation-after-sync smoke | From integration workspace: `scripts/skillhub-integrated-smoke.sh --scenario revoke-after-sync-denies-local-bundle --env .env.skillhub-integrated`. Assertion: after yank/hide/archive/delete/scanner-block/policy revoke and inventory refresh, pin is disabled, no `requested.skills` are sent, and Hermes denies invocation while the bundle still exists on disk. |
| Report-only no-token smoke | From integration workspace: `scripts/skillhub-integrated-smoke.sh --scenario report-only-no-token-mutation --env .env.skillhub-report-only`. Assertion: would-deny checks mint no SkillHub decision token and produce no protected mutations. |
| Rollback smoke | From integration workspace: `scripts/skillhub-integrated-smoke.sh --scenario rollback-after-pins-and-bundles --env .env.skillhub-integrated`. Assertion: no `requested.skills`, no local invocation, inert bundles, and `make staging` remains green for standalone SkillHub. |

## Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| Duplicate skill authority between LibreChat and SkillHub | SkillHub is authoritative for shared/published skills; LibreChat local skills are drafts only. |
| RBAC bypass through direct SkillHub calls | Keep SkillHub internal behind LibreChat and validate signed Control Tower decision context. |
| Runtime bypass through locally installed skills | Require Control Tower envelope and per-invocation authorization for `skill.invoke`. |
| Large cross-repo blast radius | Ship behind flags and land workstream slices independently. |
| Version drift | Store exact version pins and require explicit user update. |
| Review/publish confusion | Keep publish confirmation in LibreChat and lifecycle decisions in SkillHub. |
| Runtime enabled before authorization guard | Keep `LIBRECHAT_SKILLHUB_RUNTIME_REQUESTS` off until Hermes invoke enforcement is enabled in the same environment and `skill.invoke` denial tests pass. |
| Confused-deputy token reuse | Bind decision tokens to audience, method, path, body hash, workspace, action, resource, and nonce. |

## Done Criteria

- Docs reviewed and updated.
- Phase 0 fixture validation commands exist in all participating repos and pass against the SkillHub fixture pack.
- Control Tower can model and authorize SkillHub skill resources.
- SkillHub validates signed Control Tower decisions for internal operations.
- LibreChat exposes registry, pin, draft publish, and admin review flows.
- Hermes syncs and invokes pinned skills only when authorized.
- Tests cover policy denial, token tamper/replay, lifecycle restrictions, pinned version behavior, and end-to-end allowed/denied flows.
