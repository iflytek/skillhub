## ADDED Requirements

### Requirement: REQ-OM-01 A Skill SHALL be user-owned or namespace-owned

Every Skill SHALL have an ownership type: user or namespace. A user-owned Skill SHALL have an owner user. A namespace-owned Skill SHALL have no owner user and SHALL belong to the namespace that stores it. Namespace ownership SHALL NOT be represented by a synthetic user account. Existing Skills SHALL remain user-owned after the schema upgrade. The upgrade SHALL NOT rename, recreate or renumber any Skill.

#### Scenario: Upgrade keeps existing ownership
- **WHEN** the schema upgrade runs on a database with existing Skills
- **THEN** every existing Skill is user-owned with its current owner, keeps its ID, and no Skill is converted

### Requirement: REQ-OM-02 A namespace-owned Skill SHALL have at most one maintainer

A namespace-owned Skill SHALL have zero or one maintainer. The maintainer SHALL be an active, non-system member of the Skill's namespace when assigned. A user-owned Skill SHALL NOT have a maintainer. A namespace-owned Skill SHALL NOT become user-owned again through handover or rejoining.

#### Scenario: No maintainer
- **WHEN** a namespace-owned Skill has no maintainer
- **THEN** the Skill keeps its content and visibility, and only namespace OWNER/ADMIN governance can act on it

#### Scenario: Former owner rejoins
- **WHEN** a user whose Skill became namespace-owned rejoins the namespace
- **THEN** the Skill stays namespace-owned, and the user gets no maintenance right from their former ownership

### Requirement: REQ-OM-03 Ownership and maintenance changes SHALL NOT rewrite attribution

Original creator, version publisher and contribution records SHALL stay unchanged when ownership or maintainer changes. The previous owner SHALL NOT be recorded as the creator unless existing records already say so.

#### Scenario: Version history after handover
- **WHEN** a Skill is handed over
- **THEN** each version still shows the user who published it

### Requirement: REQ-OM-04 The responsible user SHALL maintain a Skill only while a member

The responsible user is the owner of a user-owned Skill, or the maintainer of a namespace-owned Skill. The responsible user SHALL be able to maintain and delete the Skill only while they are an active member of its namespace, subject to existing lifecycle, review, hidden-state and dependency checks. The same rules SHALL apply to browser sessions and Tokens.

#### Scenario: Maintainer leaves
- **WHEN** the maintainer of a namespace-owned Skill is removed from the namespace
- **THEN** they can no longer maintain it, through the Web UI or a Token

### Requirement: REQ-OM-07 Namespace ownership SHALL NOT widen member rights

Namespace OWNER and ADMIN SHALL keep their existing governance rights and SHALL be able to govern namespace-owned Skills. Namespace ownership SHALL NOT give ordinary members write access or access to PRIVATE content. Former owners and former maintainers SHALL have no remaining management right. Platform roles such as SUPER_ADMIN SHALL be evaluated as today.

#### Scenario: Ordinary member and a namespace-owned PRIVATE Skill
- **WHEN** an ordinary member who is not the maintainer requests a namespace-owned PRIVATE Skill
- **THEN** the request is denied as it is today for another member's PRIVATE Skill

### Requirement: REQ-OM-08 Rights SHALL change when the change commits

Rights SHALL follow the committed Skill record. They SHALL NOT depend on search reindexing or on a cached projection.

#### Scenario: Rights right after commit
- **WHEN** a handover commits and the search reindex has not run yet
- **THEN** the new maintainer can maintain the Skill, and the previous owner is denied

### Requirement: REQ-OM-05 A linked global copy SHALL take responsibility from its source

An active global copy SHALL take its effective ownership and responsible user from its source Skill through the existing source association. It SHALL NOT have a maintainer or responsibility relationship that can change independently of the source. A copy SHALL NOT be handed over on its own. Code that reads the copy's stored owner SHALL move to the source-derived responsibility before handover or conversion is enabled.

#### Scenario: Source handed over
- **WHEN** a source Skill with an active global copy is handed over to B
- **THEN** B is responsible for the copy from the same commit

#### Scenario: Source loses its maintainer
- **WHEN** the source is namespace-owned and has no maintainer
- **THEN** the copy has no individual responsible user either

#### Scenario: Later assignment
- **WHEN** a new maintainer is assigned on the source
- **THEN** the same user is responsible for the copy, with no separate step

### Requirement: REQ-OM-09 A global copy SHALL keep its own address and restrictions

The copy SHALL keep its global namespace, address, provenance, publication state and governance restrictions. Public access to the copy and platform governance of it SHALL be evaluated separately from source responsibility. Maintaining the copy SHALL NOT give access to PRIVATE content of the source, and SHALL NOT unhide or publish either record.

#### Scenario: Copy after handover
- **WHEN** the source is handed over
- **THEN** the copy stays in the global namespace with its address and publication state unchanged, and public reads of it work as before

#### Scenario: PRIVATE source
- **WHEN** B maintains the copy and the source has PRIVATE content B could not otherwise read
- **THEN** maintaining the copy does not let B read that content

### Requirement: REQ-OM-06 Auto-provisioned namespaces SHALL follow TEAM rules

Auto-provisioned personal namespaces are TEAM namespaces. How a namespace was created SHALL NOT add an authorization, handover or conversion exception. Account disabling alone SHALL NOT convert ownership, and existing restrictions on removing a namespace OWNER SHALL still apply. This requirement SHALL NOT add account deletion, namespace dissolution or a separate personal-asset lifecycle.

#### Scenario: Handover in an auto-provisioned namespace
- **WHEN** the responsible user of a Skill in an auto-provisioned namespace hands it to another active member of that namespace
- **THEN** the same handover rules apply as in any other TEAM namespace
