## ADDED Requirements

### Requirement: REQ-HO-01 Handover SHALL be started only by an eligible user

| Kind | Started by | Condition | Completes when |
| --- | --- | --- | --- |
| Voluntary | The responsible user (owner or maintainer) | They are active and a member of the namespace | The recipient accepts |
| Recovery | Namespace OWNER | The responsible user has left the namespace or their account is not active. Reason required | The recipient accepts |
| Self-recovery | Namespace OWNER, to themselves | Same as recovery. Reason required | One explicit confirmation |
| Governance | Namespace OWNER or ADMIN, for a namespace-owned Skill only (**proposed default**) | Reason required | The recipient accepts, or one explicit confirmation when assigning themselves |

A namespace OWNER or ADMIN SHALL NOT hand over a user-owned Skill while its owner is an active member. Handover management SHALL be available only in browser sessions; Tokens SHALL NOT start, accept, reject or cancel a handover. System-owned and GitHub-sync-managed Skills SHALL NOT be handed over. A global copy SHALL NOT be handed over on its own.

#### Scenario: Namespace OWNER and an active member's Skill
- **WHEN** a namespace OWNER tries to hand over a Skill whose owner is an active member
- **THEN** the request is refused, and nothing changes

#### Scenario: Token
- **WHEN** a Token with any scope calls a handover endpoint
- **THEN** the request is refused

### Requirement: REQ-HO-02 The recipient SHALL be eligible

The recipient SHALL be an active, non-system member of the Skill's namespace and SHALL NOT be the current responsible user. Recipient search SHALL be server-side and paginated over eligible members only. A pending request SHALL NOT give the recipient access to PRIVATE package files. Before accepting, the recipient SHALL see the Skill's name, namespace, visibility, linked global copy, and the rights they will gain.

#### Scenario: Large namespace
- **WHEN** the namespace has more than 100 eligible members
- **THEN** the initiator can find any of them by search

### Requirement: REQ-HO-03 A handover request SHALL follow one lifecycle

A request SHALL be in one of these states: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `INVALIDATED`. Only `PENDING` can change, and only once. A Skill SHALL have at most one `PENDING` request. The recipient accepts or rejects. The initiator cancels; for recovery and governance requests, a namespace OWNER can also cancel. A request SHALL expire 14 days after creation by default. Expiry SHALL be enforced whenever someone acts on the request, not only by a scheduled job, and an expired request SHALL free the Skill for a new request. Self-recovery and governance self-assignment SHALL be recorded as `ACCEPTED` requests, so every change has a request record.

#### Scenario: Duplicate accept
- **WHEN** the recipient accepts twice at the same time
- **THEN** one call succeeds and the other is rejected without side effects

#### Scenario: Accept after expiry, before the sweep
- **WHEN** the recipient accepts a request whose expiry time has passed but which the scheduler has not yet marked
- **THEN** the accept is refused, and the request becomes `EXPIRED`

### Requirement: REQ-HO-04 Completion SHALL re-check everything under lock

Before completing, the system SHALL re-check, inside the same locked transaction that changes the Skill:

- the request is `PENDING` and not expired;
- the Skill's ownership and responsible user still match those recorded on the request;
- the initiator's condition for the request kind still holds; a recovery request SHALL become `INVALIDATED` if the original user is again an active member with an active account;
- the recipient is still eligible;
- the namespace is active;
- the Skill and its global copy are consistent;
- nothing blocking is pending (REQ-HO-05).

If any check fails, the system SHALL change nothing on the Skill and SHALL mark the request `INVALIDATED`, or leave it `PENDING` with the reason when the blocker is temporary. A stale or repeated action SHALL NOT override a newer result. Capabilities of the former responsible user, such as their Tokens, SHALL NOT pass to the recipient.

#### Scenario: Recipient removed before accept
- **WHEN** the recipient is removed from the namespace while the request is pending
- **THEN** a later accept is refused, and the request becomes `INVALIDATED`

#### Scenario: Ownership changed by another path
- **WHEN** the Skill's ownership changed after the request was created
- **THEN** the request cannot complete

### Requirement: REQ-HO-05 Pending work SHALL block completion

Completion SHALL be blocked, and the request SHALL stay `PENDING` showing the blocking item, while the Skill or its global copy has any of: a version review waiting for decision, a pending promotion request, a pending visibility change, a pending avatar review, or a version in `SCANNING` (**proposed default**). `DRAFT`, `UPLOADED` and `SCAN_FAILED` versions SHALL NOT block (**proposed default**). The check SHALL be serialized with the creation of new pending work, so new work cannot slip in between the check and the change.

#### Scenario: Review pending
- **WHEN** the recipient accepts while a version review is waiting
- **THEN** the accept is refused with the blocking review named, and the request stays `PENDING`

### Requirement: REQ-HO-06 Completion SHALL produce a namespace-owned Skill

On completion, the Skill SHALL become namespace-owned with the recipient as maintainer (**proposed default** for all handovers; required when the recipient has a same-slug Skill). A Skill that is already namespace-owned SHALL only change maintainer. The Skill's ID, namespace, slug, visibility, versions, statistics, attribution, review history, labels and suite memberships SHALL stay unchanged. Its active global copy SHALL change in the same transaction.

#### Scenario: Handover of a user-owned PUBLIC Skill
- **WHEN** the recipient accepts
- **THEN** the Skill is namespace-owned, the recipient is maintainer, and its address and visibility are unchanged

### Requirement: REQ-HO-07 Handover SHALL notify and audit

The system SHALL send in-app notifications: to the recipient when a request is created, to the initiator on every outcome, and to the previous responsible user, if active, on completion. Email SHALL be optional and SHALL NOT be needed to finish the workflow. Every state change SHALL write an audit record with the real operator, request, kind, reason, and the before and after ownership and maintainer.

#### Scenario: Email disabled
- **WHEN** email delivery is not configured
- **THEN** the recipient can still find and accept the request in the app
