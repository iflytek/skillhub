## ADDED Requirements

### Requirement: Describe verified deployment and scanning behavior
The documentation SHALL distinguish independent Kustomize and Helm deployment paths, co-locate application defaults with deployment overrides, and explain the actual consequences of disabling scanning.

#### Scenario: Select a Kubernetes deployment path
- **WHEN** a deployment operator opens the guide or FAQ
- **THEN** Kustomize and Helm are identified as independent paths with their correct configuration sources
- **AND** a rendering prerequisite is not presented as real-cluster validation

#### Scenario: Diagnose a scanner problem
- **WHEN** an operator follows a diagnostic action
- **THEN** the action specifies its working context, a real command or UI location, expected observation and failure direction
- **AND** it does not print credentials or instruct destructive retries

### Requirement: Keep state and permission objects distinct
The documentation SHALL use actual enums, API fields and policy conditions rather than borrowing labels across review tasks, skill versions, bundle operations or avatar processing.

#### Scenario: Decide whether a namespace operation is available
- **WHEN** an operator reads creation or governance instructions
- **THEN** platform and namespace roles are distinguished and the type/state/action prerequisites are explicit
- **AND** the instructions do not grant capabilities absent from the server

### Requirement: Explain complete authoring and first-use actions
The documentation SHALL explain implemented authoring draft endpoints and their relationship to upload, validation, submission and published versions; quickstart failures SHALL provide an immediate verifiable action.

#### Scenario: Submit an authoring draft
- **WHEN** an author follows the documented draft path
- **THEN** method/path, authorization, required input and resulting object are grounded in actual code
- **AND** structure validation is distinguished from scanning, review and publication

#### Scenario: First installation fails
- **WHEN** a reader selects a quickstart failure row
- **THEN** it includes a local check, observable result and next step before any reference link

### Requirement: Deliver bilingual staged documentation
Each batch SHALL preserve existing routes and product behavior, synchronize Chinese and English, and validate affected content without claiming deferred site-wide criteria are complete.

#### Scenario: Complete the first documentation batch
- **WHEN** the P0 correction PR is delivered
- **THEN** P0-1 through P0-9 have explicit source and verification records
- **AND** document build, links and browser checks are reported separately from application runtime tests
- **AND** later depth and navigation work remains explicitly pending

### Requirement: Provide formal and complete documentation depth samples
The second batch SHALL improve the introduction, quickstart, namespace, review, social and scanner pages in both languages without moving routes or changing product behavior. The prose SHALL be formal, source-grounded and task-oriented. Diagrams and tables SHALL clarify relationships and decisions without replacing operational text.

#### Scenario: Complete an operational task without external explanation
- **WHEN** a reader follows an affected operational guide
- **THEN** the applicable role, prerequisites, action sequence, decision basis, observable outcome, failure handling and downstream effects are explicit
- **AND** duplicate legacy template sections are consolidated rather than retained in parallel

#### Scenario: Understand the first installation
- **WHEN** a reader follows the quickstart
- **THEN** complete commands, representative output and a non-destructive failure example distinguish installation, Agent discovery and actual execution

#### Scenario: Review a visual explanation
- **WHEN** a reader opens the diagrams or tables at desktop or mobile width
- **THEN** the visual and surrounding text describe the same verified behavior and remain readable in light and dark themes
- **AND** the page does not depend on an added rendering service or package


### Requirement: Route avatar instructions by operator responsibility
The documentation SHALL separate skill-avatar submission and review from moderation-provider deployment configuration, preserving the existing operational page URL and synchronizing both locales, navigation, and ordered page links.

#### Scenario: Locate an avatar task
- **WHEN** a skill owner or reviewer opens team-governance guidance
- **THEN** avatar operations and review decisions are available there
- **AND** provider credentials and deployment verification are linked to a distinct deployment guide

### Requirement: State the scope and limitations of documentation review
A scoped review SHALL NOT be reported as complete verification of all site content. Known unresolved structural issues and unverified technical behavior SHALL remain explicit.

#### Scenario: Report an incremental review
- **WHEN** a document batch passes source, build, and browser checks
- **THEN** the report distinguishes verified changed paragraphs, site-structure observations, unresolved issues, and unexecuted business or external-service validation
