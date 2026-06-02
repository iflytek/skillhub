# Hermes Agent App SkillHub Governance Plan

## Status

Current SkillHub planning note for the Glaux Hermes Agent desktop/web app baseline captured in
ADR-005.

The earlier frontend integration draft and its fixtures are removed and are not part of the active
SkillHub implementation backlog. New work must not add retired frontend routes, service
credentials, profile pin tables, runtime sync contracts, or fixture packages unless a future Glaux
decision record reintroduces that surface.

## Source Of Truth

This SkillHub note is derived from the current Glaux workspace plan:

- `../../docs/phase-1-implementation-plan.md`
- `../../docs/hermes-agent-app-integration-plan.md`
- `../../docs/decisions/ADR-005-hermes-agent-app-integration.md`

When this note disagrees with those parent Glaux documents, the parent Glaux documents win. The
SkillHub-local lifecycle authority remains `docs/14-skill-lifecycle.md`.

## Direction

Glaux now uses Hermes Agent desktop/web apps as the governed app surfaces and Control Tower as the
policy authority. SkillHub remains the shared skill registry and catalog reference. SkillHub owns
package validation, scanner state, lifecycle transitions, review workflow, artifact storage,
download readiness, and audit evidence. Control Tower owns policy decisions, client entitlements,
envelopes, and runtime authorization. Hermes Agent apps display and apply the resulting
permissions, but do not become a policy authority.

SkillHub integration work should therefore be frontend-agnostic. Any internal or cross-service
contract should be named around SkillHub, Control Tower, Hermes client entitlements, or generic
client-visible skill governance. Do not name active APIs, DTOs, configs, tests, fixtures, or docs
after the retired frontend.

## Active SkillHub Backlog

### S1: Confirm Lifecycle And Download Safety

- Keep `PENDING_REVIEW -> DRAFT` as the withdrawal target.
- Treat `hidden` as a governance overlay rather than a lifecycle state.
- Ensure direct-published versions are not distributed until scanner and package policy mark the
  exact artifact download-ready.
- Reject install, download, sync, and runtime use for yanked, hidden, archived, rejected,
  scanner-blocked, missing-fingerprint, or deleted versions.

Verification:

- Backend lifecycle and download tests cover withdrawal, direct publish, scanner pending, scanner
  failed, scanner expired, scanner unavailable, scanner-blocked, yanked, hidden, and archived cases.

### S2: Define Skill Catalog Records For Control Tower

- Emit stable resource identifiers for namespaces, skill containers, versions, and review tasks.
- Include trusted-source metadata, version pinning fields, scanner/download readiness, review
  status, script policy indicators, owner/workspace metadata where applicable, and update
  timestamps.
- Keep SkillHub lifecycle and package checks mandatory even after Control Tower allows an action.

Suggested resource IDs:

```text
namespace    skillhub:@namespace
skill        skillhub:@namespace/slug
version      skillhub:@namespace/slug@version
review task  skillhub:review-task:{reviewTaskPublicId}
```

Verification:

- Contract or unit tests prove resource ID normalization, invalid version rejection, and lifecycle
  delta handling for hide, restore, yank, archive, delete, scanner-block, review creation, review
  approval, review rejection, and review withdrawal.

### S3: Publish Client-Visible Skill Entitlement Data

- Provide catalog state that Control Tower can project through the generic `/v1/client/...`
  entitlement contract.
- Represent allowed, denied, requestable, disabled, and stale states without trusting
  browser-supplied policy context.
- Keep exact version pins and artifact fingerprints immutable unless the user or policy workflow
  explicitly updates them.

Verification:

- Tests cover disabled skill use, policy changes after a pin exists, stale version pins, missing or
  mismatched fingerprints, and attempts by a client or host adapter to expand skill access.

### S4: Preserve Review, Publication, And Audit Invariants

- Keep publication request, review decision, and governance actions governed by SkillHub lifecycle
  rules and auditable actor context.
- Record enough audit data for Control Tower to correlate policy decisions with SkillHub registry
  state without storing Control Tower secrets or client-local preferences.
- Redact unsafe script metadata, scanner details, download handles, and credentials from client and
  export surfaces.

Verification:

- Tests cover private draft isolation, publication request routing, reviewer decision rules,
  high-risk script approval requirements, untrusted-source denial, and redacted audit output.

## Non-Goals

- No retired frontend-specific SkillHub routes or adapters.
- No retired frontend profile pin persistence in SkillHub.
- No runtime sync path from SkillHub to a retired frontend.
- No browser-held Control Tower service credentials, signing keys, or HMAC secrets.
- No compatibility aliases for retired frontend paths in new SkillHub work.

## Review Checklist

- Does the change preserve SkillHub lifecycle, scanner, package, and audit enforcement after policy
  approval?
- Are all new externally visible shapes frontend-agnostic or Hermes Agent app generic?
- Can Control Tower derive policy resources without re-parsing untrusted skill metadata?
- Can Hermes Agent apps display the state without expanding access locally?
- Do tests prove denial and disabled-state behavior, not just successful catalog reads?
