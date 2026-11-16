## ADDED Requirements

### Requirement: REQ-OM-01 A Skill SHALL be user-owned or namespace-owned

Every Skill SHALL have an ownership type: user or namespace. A user-owned Skill SHALL have an owner user. A namespace-owned Skill SHALL have no owner user and SHALL belong to the namespace that stores it. Namespace ownership SHALL NOT be represented by a synthetic user account. Existing Skills SHALL remain user-owned after the schema upgrade.

#### Scenario: Upgrade keeps existing ownership
- **WHEN** the schema upgrade runs on a database with existing Skills
- **THEN** every existing Skill is user-owned with its current owner, and no Skill is converted

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

### Requirement: REQ-OM-04 Authorization SHALL follow current ownership, maintenance and membership

The responsible user is the owner of a user-owned Skill, or the maintainer of a namespace-owned Skill. The responsible user SHALL be able to maintain and delete the Skill only while they are an active member of the Skill's namespace, subject to existing lifecycle, review and dependency checks. Namespace OWNER and ADMIN SHALL keep their existing governance rights and SHALL be able to govern namespace-owned Skills. Namespace ownership SHALL NOT give ordinary members write access or access to PRIVATE content. Former owners and former maintainers SHALL have no remaining management right. Rights SHALL change when the change commits and SHALL NOT depend on search reindexing. The same rules SHALL apply to browser sessions and Tokens.

#### Scenario: Ordinary member and a namespace-owned PRIVATE Skill
- **WHEN** an ordinary member who is not the maintainer requests a namespace-owned PRIVATE Skill
- **THEN** the request is denied as it is today for another member's PRIVATE Skill

#### Scenario: Rights right after commit
- **WHEN** a handover commits and the search reindex has not run yet
- **THEN** the new maintainer can maintain the Skill, and the previous owner is denied

### Requirement: REQ-OM-05 A linked global copy SHALL follow its source

An active global copy SHALL have the same ownership type and responsible user as its source after every ownership or maintainer change. It SHALL keep its storage namespace and provenance. It SHALL NOT be handed over on its own. Rights over the global copy SHALL NOT grant access to PRIVATE content of the source.

#### Scenario: Source handed over
- **WHEN** a source Skill with an active global copy is handed over
- **THEN** the source and the copy change in the same transaction, and the copy stays in the global namespace
