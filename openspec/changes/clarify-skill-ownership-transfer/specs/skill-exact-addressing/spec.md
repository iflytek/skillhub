## ADDED Requirements

### Requirement: REQ-ADDR-01 Same-slug Skills SHALL coexist as separate records

Within one namespace, user-owned Skills SHALL stay unique per namespace, slug and owner. Namespace-owned Skills with the same slug MAY coexist with each other and with user-owned Skills. Each record SHALL keep its own ID, content, versions, visibility, statistics and dependencies. The system SHALL NOT rename, overwrite, merge or drop a record to resolve a same-slug situation. Existing publication-coordinate conflict rules SHALL NOT be loosened by the existence of separate same-slug records.

#### Scenario: Recipient owns an unpublished Skill with the same slug
- **WHEN** a Skill is handed over to a user who owns an unpublished PRIVATE Skill with the same slug
- **THEN** both records remain, and the recipient's own Skill is unchanged

### Requirement: REQ-ADDR-02 An explicit skillId SHALL select an exact record

Existing routes that select a Skill by namespace and slug SHALL accept an optional `skillId`, and the CLI SHALL offer a matching selector. The existing resolution service SHALL be extended, not replaced by a second resolver. A supplied `skillId` SHALL be checked against the namespace and slug in the request and then authorized like any other request. Knowing an ID SHALL NOT grant access.

#### Scenario: ID without rights
- **WHEN** a user supplies the ID of a Skill they cannot see
- **THEN** the request is denied exactly as if the ID did not exist

#### Scenario: ID does not match the path
- **WHEN** a request supplies a `skillId` whose namespace or slug differs from the path
- **THEN** the request is rejected without side effects

### Requirement: REQ-ADDR-07 A selected record SHALL stay selected

Once a record is selected, Web links, detail, versions, files, downloads and later mutations SHALL carry that record's identity. Update, delete, archive and handover SHALL NOT resolve the target again from the slug in a later step.

#### Scenario: Web keeps the selected record
- **WHEN** a user opens Skill 202 from My Skills, then opens versions, downloads a file and archives it
- **THEN** every step acts on 202, even if another record with the same slug exists

### Requirement: REQ-ADDR-03 Slug-only requests SHALL keep their established resolution

A request without `skillId` SHALL resolve the way that endpoint resolves today whenever today's result is unambiguous. A new maintenance relationship SHALL NOT change which record an existing request selects.

#### Scenario: Personal Skill stays the target
- **WHEN** B owns personal Skill 101, later becomes maintainer of same-slug namespace-owned Skill 202, and repeats an existing slug-only call
- **THEN** the call still targets 101, and operating on 202 requires selecting it explicitly

### Requirement: REQ-ADDR-08 A selection SHALL be required only for genuine ambiguity

The system SHALL require a selection only when the target genuinely cannot be determined. The selection-required error SHALL list only candidates the caller may access, SHALL explain how to select one, and SHALL have no side effects. Requests without `skillId` SHALL NOT all become errors.

#### Scenario: Public download with private same-slug records
- **WHEN** a caller downloads `namespace/slug`, exactly one published visible record matches, and other private records share the slug
- **THEN** the download works as before

#### Scenario: Genuine ambiguity
- **WHEN** a request without `skillId` matches more than one record the caller may use for that operation and no established rule picks one
- **THEN** it is rejected with the selection-required error listing only the candidates the caller may access

### Requirement: REQ-ADDR-04 Handover cases that depend on exact addressing SHALL wait for validation

Before exact addressing is built, the team SHALL deliver a call-site inventory and a historical-data and legacy-client compatibility matrix. The inventory marks each place that looks up a Skill by slug or reads its owner as "must change" or "retest only". A handover that would leave the recipient or the former owner with same-slug records SHALL stay disabled until the addressing requirements are implemented and validated against that matrix.

#### Scenario: Validation not finished
- **WHEN** compatibility validation is not complete and a handover would leave a party with same-slug records
- **THEN** the request is refused with a clear reason, and nothing changes

### Requirement: REQ-ADDR-05 Uploads SHALL distinguish creating from updating

New management flows SHALL distinguish creating a Skill from updating a particular existing Skill. When the intended update target is unclear, the Web UI SHALL offer a selection and the CLI SHALL require an explicit selector. The system SHALL NOT pick the first, the most recently updated or any other guessed record. Personal-ownership uniqueness and existing publication-coordinate conflict rules SHALL still apply.

#### Scenario: Maintainer without a personal record
- **WHEN** B has no user-owned Skill with slug `x`, maintains namespace-owned Skill 202 with slug `x`, and uploads `namespace/x` without a selector
- **THEN** the upload is refused with the selection-required error listing 202, and no Skill or version is created

#### Scenario: Explicit update
- **WHEN** B uploads with `skillId` 202
- **THEN** only 202 gets the new version, subject to the existing permission, publication and review checks

### Requirement: REQ-ADDR-06 Historical data and existing clients SHALL keep working

Release SHALL require that historical Skills are not renamed, recreated or renumbered, and keep their version and file associations and usable links. Existing clients SHALL NOT be forced to upgrade at once. Existing CLI installation records and later update and download calls SHALL be verified. A new handover case that cannot yet keep compatibility SHALL be restricted until it can.

#### Scenario: Old CLI update after a handover
- **WHEN** a Skill installed by an old CLI is handed over, and the user who installed it runs the old CLI's update
- **THEN** the update resolves the same record as before and succeeds if the user may still read it

#### Scenario: Removed user is not a compatibility case
- **WHEN** a removed member's old CLI tries to update an internal Skill
- **THEN** the request is denied under REQ-DEP-01, which is an agreed authorization change, not a compatibility break
