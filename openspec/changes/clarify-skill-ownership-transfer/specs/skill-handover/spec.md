## ADDED Requirements

### Requirement: REQ-HO-01 Handover SHALL be started only by an eligible user

| Kind | Skill | Started by | Condition |
| --- | --- | --- | --- |
| Voluntary | Any | Responsible user | Active member |
| Recovery | User-owned | Namespace OWNER | Owner left or is disabled; reason |
| Governance | Namespace-owned | Namespace OWNER or ADMIN | Reason |

The recipient SHALL accept before the change takes effect. When the initiator of a recovery or governance request assigns the Skill to themselves, one explicit confirmation with a reason SHALL replace acceptance.

#### Scenario: Governance reassignment
- **WHEN** a namespace ADMIN reassigns a namespace-owned Skill to member C with a reason
- **THEN** a request is created, and the maintainer changes only when C accepts

#### Scenario: Self-assignment
- **WHEN** a namespace OWNER assigns a namespace-owned Skill to themselves with a reason and confirms
- **THEN** they become maintainer, and the change is recorded as an accepted request

### Requirement: REQ-HO-09 Handover SHALL stay within its limits

A namespace OWNER or ADMIN SHALL NOT hand over a user-owned Skill while its owner is an active member. Handover SHALL be available only in browser sessions; Tokens SHALL NOT start, accept, reject or cancel one, and no Token capability for it SHALL be added. System-owned and GitHub-sync-managed Skills SHALL NOT be handed over. A global copy SHALL NOT be handed over on its own.

#### Scenario: Namespace OWNER and an active member's Skill
- **WHEN** a namespace OWNER tries to hand over a user-owned Skill whose owner is an active member
- **THEN** the request is refused, and nothing changes

#### Scenario: Token
- **WHEN** a Token with any scope calls a handover endpoint
- **THEN** the request is refused

### Requirement: REQ-HO-02 The recipient SHALL be eligible

The recipient SHALL be an active, non-system member of the Skill's namespace and SHALL NOT be the current responsible user. Recipient search SHALL be server-side and paginated over eligible members only. A pending request SHALL NOT give the recipient access to PRIVATE package files. Before accepting, the recipient SHALL see the Skill's name, namespace, visibility, archived or hidden state, linked global copy, and the rights they will gain.

#### Scenario: Large namespace
- **WHEN** the namespace has more than 100 eligible members
- **THEN** the initiator can find any of them by search

### Requirement: REQ-HO-03 A handover request SHALL follow one lifecycle

A request SHALL be in one of these states: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `INVALIDATED`. Only `PENDING` can change, and only once. A Skill SHALL have at most one `PENDING` request. The recipient accepts or rejects. The initiator cancels; for recovery and governance requests, a namespace OWNER can also cancel. Self-assignments SHALL be recorded as `ACCEPTED` requests.

#### Scenario: Duplicate accept
- **WHEN** the recipient accepts twice at the same time
- **THEN** one call succeeds and the other is rejected without side effects

### Requirement: REQ-HO-10 A handover request SHALL expire

A request SHALL expire 14 days after creation by default. Expiry SHALL be enforced whenever someone acts on the request, not only by a scheduled job. An expired request SHALL free the Skill for a new request.

#### Scenario: Accept after expiry, before the sweep
- **WHEN** the recipient accepts a request whose expiry time has passed but which the scheduler has not yet marked
- **THEN** the accept is refused, and the request becomes `EXPIRED`

### Requirement: REQ-HO-04 Completion SHALL re-check its conditions under lock

Inside the locked transaction that changes the Skill, the system SHALL re-check that the request is `PENDING` and not expired; the Skill's ownership and responsible user match the request; the initiator's condition still holds; the recipient is eligible; the namespace is active; and REQ-ADDR-04 allows the case. If any check fails, nothing on the Skill SHALL change and the request SHALL become `INVALIDATED` or `EXPIRED`.

#### Scenario: Recipient removed before accept
- **WHEN** the recipient is removed from the namespace while the request is pending
- **THEN** a later accept is refused, and the request becomes `INVALIDATED`

#### Scenario: Original owner returns during recovery
- **WHEN** a recovery request is pending and the original owner is again an active member with an active account
- **THEN** the request becomes `INVALIDATED`

#### Scenario: Ownership changed by another path
- **WHEN** the Skill's ownership changed after the request was created
- **THEN** the request cannot complete

### Requirement: REQ-HO-11 Completion SHALL NOT race decisions or carry over capabilities

Completion SHALL be serialized with review decisions, promotion approval and automatic approval for the same Skill, so neither side decides on stale responsibility. A stale or repeated action SHALL NOT override a newer result. Capabilities of the former responsible user, such as their Tokens, SHALL NOT pass to the recipient.

#### Scenario: Accept and approve at once
- **WHEN** a handover is accepted while a reviewer approves a pending version of the same Skill
- **THEN** one runs after the other, and the approval checks the responsibility that is current when it runs

### Requirement: REQ-HO-05 In-flight work SHALL NOT block handover

Handover changes who is responsible for maintenance. It SHALL NOT publish content, approve an application or widen visibility. Scans and pending version, promotion, visibility and avatar applications SHALL NOT, on their own, block completion. Scans SHALL continue against the exact version they started on. Pending applications SHALL stay recorded and follow REQ-DEP-03. Each later decision or publication step SHALL re-check current conditions.

#### Scenario: Review pending
- **WHEN** the recipient accepts while a version review is waiting
- **THEN** the handover completes, the review stays pending for a human reviewer, and nothing is published by the handover

#### Scenario: Scan running
- **WHEN** the recipient accepts while a version is in `SCANNING`
- **THEN** the handover completes, and the scan finishes against that same version

### Requirement: REQ-HO-06 Completion SHALL produce a namespace-owned Skill

Every accepted handover SHALL leave the Skill namespace-owned with the recipient as maintainer, whether or not there is a same-slug conflict. A Skill that is already namespace-owned SHALL only change maintainer. Everything else about the Skill SHALL stay unchanged. The recipient's own same-slug Skill SHALL stay unchanged. The active global copy SHALL follow at the same commit (REQ-OM-05).

#### Scenario: Handover of a user-owned PUBLIC Skill
- **WHEN** the recipient accepts
- **THEN** the Skill is namespace-owned, the recipient is maintainer, and its ID, namespace, slug, visibility, versions, files, statistics, attribution, labels, suite memberships and review history are unchanged

#### Scenario: Second handover
- **WHEN** the maintainer of a namespace-owned Skill hands it to another member, who accepts
- **THEN** only the maintainer changes

### Requirement: REQ-HO-07 Handover SHALL notify and audit

The system SHALL notify the recipient when a request is created, the initiator on every outcome, and the previous responsible user, if active, on completion. Notifications SHALL be sent only after the change commits. Email SHALL be optional. Every state change SHALL write an audit record with the real operator, request, kind, reason, and the before and after ownership and maintainer.

#### Scenario: Email disabled
- **WHEN** email delivery is not configured
- **THEN** the recipient can still find and accept the request in the app

### Requirement: REQ-HO-08 Archived and hidden Skills SHALL keep their state

An archived Skill MAY be handed over and SHALL stay archived. A hidden Skill MAY be handed over through the voluntary, recovery or governance path without being unhidden first, and SHALL stay hidden with its existing restrictions: out of public listings, slug-only published resolution, promotion and reports.

#### Scenario: Archived Skill handed over
- **WHEN** an archived Skill is handed over and accepted
- **THEN** it stays archived until someone with the existing right restores it

#### Scenario: Hidden Skill handed over
- **WHEN** a hidden Skill is handed over and accepted
- **THEN** it stays hidden and out of public listings

### Requirement: REQ-HO-12 A maintainer SHALL be able to work on a hidden Skill

While a Skill stays hidden, the new maintainer SHALL have exactly the access the responsible user of a hidden Skill has under existing rules. They can open its detail page, list all versions including unpublished ones, read and download files through protected routes, and take the maintenance actions existing rules allow on a hidden Skill.

#### Scenario: Maintainer opens a hidden Skill
- **WHEN** the new maintainer opens a hidden Skill they accepted
- **THEN** they can see its detail, versions and files, and download through protected routes

### Requirement: REQ-HO-13 Handover SHALL NOT grant unhide, restore, publish or promote

Handover SHALL NOT unhide, restore, publish or promote anything. It SHALL NOT grant any right to do so beyond what the responsible user already has under existing rules. Unhiding SHALL remain a platform action, and namespace governance SHALL NOT gain unhide authority through this workflow.

#### Scenario: Maintainer tries to unhide
- **WHEN** the new maintainer, or a namespace OWNER who assigned them, tries to unhide the Skill
- **THEN** the request is refused, as it is today for anyone without platform authority
