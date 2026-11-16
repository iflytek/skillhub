## ADDED Requirements

### Requirement: REQ-ADDR-01 Same-slug Skills SHALL coexist as separate records

Within one namespace, user-owned Skills SHALL stay unique per namespace, slug and owner. Namespace-owned Skills with the same slug MAY coexist with each other and with user-owned Skills. Each record SHALL keep its own ID, content, versions, visibility, statistics and dependencies. The system SHALL NOT rename, overwrite, merge or drop a record to resolve a same-slug situation. Existing publication rules SHALL NOT be loosened: at most one published Skill per namespace and slug, as today.

#### Scenario: Recipient owns an unpublished Skill with the same slug
- **WHEN** a Skill is handed over to a user who owns an unpublished PRIVATE Skill with the same slug
- **THEN** both records remain, each unchanged apart from the handed-over Skill's ownership and maintainer

### Requirement: REQ-ADDR-02 Every Skill operation SHALL target the exact record

Every route that selects a Skill by namespace and slug SHALL resolve exactly one intended record, then check authorization on that record. This covers detail, file and preview reads, downloads, uploads of new versions, review submission and decisions, lifecycle actions, visibility, avatar, labels, promotion and deletion, for Web, API and CLI. A route SHALL NOT pick the first match from several candidates. Knowing a Skill ID SHALL NOT grant access.

#### Scenario: Update targets the right record
- **WHEN** a user who maintains a namespace-owned Skill and owns a personal Skill with the same slug uploads a new version with an explicit selector
- **THEN** only the selected Skill gets the new version

#### Scenario: ID without rights
- **WHEN** a user supplies the ID of a Skill they cannot see
- **THEN** the request is denied exactly as if the ID did not exist

### Requirement: REQ-ADDR-03 Ambiguous slug-only requests SHALL fail safely

A slug-only request that resolves to exactly one record the caller may use for that operation SHALL behave as today. When it could match more than one, the system SHALL reject it without side effects and return an ambiguity error. The error SHALL list only candidates the caller may see, and SHALL explain how to select one explicitly. Web, API and CLI SHALL offer an explicit selection path.

#### Scenario: Legacy client, single candidate
- **WHEN** an older CLI downloads `namespace/slug` and only one published Skill matches
- **THEN** the download works as before

#### Scenario: Legacy client, several candidates
- **WHEN** an older CLI uploads to `namespace/slug` and the caller may update two Skills with that slug
- **THEN** the upload is rejected with the ambiguity error, and no version is created

### Requirement: REQ-ADDR-04 Same-slug handover SHALL wait for exact addressing

Handover to a recipient who already has a Skill with the same slug SHALL stay disabled until REQ-ADDR-02 and REQ-ADDR-03 are complete for all routes.

#### Scenario: Addressing not finished
- **WHEN** exact addressing is not complete and a handover would create a same-slug pair for the recipient
- **THEN** the request is refused with a clear reason
