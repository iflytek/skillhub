## ADDED Requirements

### Requirement: OpenSpec owns the formal change lifecycle

The project workflow MUST use OpenSpec artifacts as the authoritative requirements, design, implementation tasks, and acceptance source for an OpenSpec-managed change. Trellis MUST reference those artifacts rather than maintaining a competing formal planning package or task checklist.

#### Scenario: Implement an existing OpenSpec change
- **WHEN** an Agent starts implementing a change whose OpenSpec planning artifacts exist
- **THEN** it reads the artifacts resolved by the active schema and uses the tracked OpenSpec task source
- **AND** it does not require a duplicate Trellis PRD, design, task list, or planning approval sequence

#### Scenario: Perform a bounded small edit
- **WHEN** an authorized small edit does not require a formal change or task record under project policy
- **THEN** the Agent can read applicable engineering rules and verify the edit without creating records solely for context loading

### Requirement: Applicable engineering context reaches implementation

Before performing changes that depend on engineering rules, the workflow MUST select applicable domain and topic documents using the request and actual impact, and MUST obtain their bodies through model-visible runtime instructions or successful tool reads. Paths, indexes, markers, and hashes alone MUST NOT be treated as body delivery.

#### Scenario: Implement backend behavior
- **WHEN** the change affects backend API, authorization, transaction, or persistence behavior
- **THEN** the Agent obtains the applicable backend topic bodies and current OpenSpec acceptance context before dependent implementation

#### Scenario: Implement a cross-layer change
- **WHEN** the implementation or actual diff affects additional layers beyond the initially selected domain
- **THEN** the Agent adds and reads the newly applicable topic documents before relying on their contracts

### Requirement: Design-system context is concrete and scoped

The workflow MUST obtain the actual theme sources, used component contracts, and applicable page or interaction rules for a design-system-dependent task. It MUST preserve the optional nature of Figma and MUST NOT claim design-system adoption when required design resources are unavailable.

#### Scenario: Implement a page using the selected design system
- **WHEN** the task changes a page or shared component according to the selected design system
- **THEN** the Agent reads the relevant theme, component, and design rule bodies
- **AND** verification includes the applicable visible states, focus behavior, long content, narrow layout, and completion behavior

#### Scenario: Required design resources are missing
- **WHEN** a new checkout lacks resources required by the selected design task
- **THEN** the workflow reports the missing context and does not claim the dependent task is prepared or design-system compliant
- **AND** unrelated authorized work remains possible

### Requirement: Verification reloads applicable rules without implementing

The OpenSpec verification integration MUST obtain current planning artifacts and applicable engineering rule bodies, compare them with the implementation sources, and retain report-oriented verification semantics. It MUST NOT treat apply readiness or task checkmarks as proof of correctness or automatically perform product fixes or archive the change.

#### Scenario: Verification discovers a mechanical or behavioral problem
- **WHEN** verification finds an issue that would require changing product code
- **THEN** it reports the issue and returns repair work to apply rather than silently modifying the implementation during verify

#### Scenario: Verification cannot establish coverage
- **WHEN** an applicable requirement, design constraint, or runtime check has no usable evidence
- **THEN** it reports the limitation as not verified or failed as appropriate rather than passing it because task checkboxes are complete

### Requirement: Context gaps and freshness are visible

The workflow MUST distinguish complete body delivery from missing, truncated, or non-inlined material. It MUST resolve required gaps before dependent implementation and MUST reassess relevant context after resumption, compaction, source changes, or impact changes. Diagnostic and unrelated authorized work MUST remain possible.

#### Scenario: A referenced specification is truncated or missing
- **WHEN** context delivery does not provide a required body in full
- **THEN** the Agent explicitly reads the remaining material or reports the unresolved gap before dependent implementation
- **AND** an injection marker or receipt does not override the gap

#### Scenario: A session resumes after specification changes
- **WHEN** work resumes and relevant specification or design content differs from the previously used source
- **THEN** the changed material is obtained again and affected implementation and verification evidence is reassessed

### Requirement: Host delivery is verified and has a fallback

The integration MUST distinguish configured hooks, loaded and trusted thread hooks, and actual event delivery. Main and child contexts MUST use the intended task sources, and missing automatic delivery MUST have an explicit-reading fallback without borrowing another session's task or bypassing host trust.

#### Scenario: A configured hook is absent from the loaded thread
- **WHEN** configuration files contain the hook but the current host thread has not loaded it
- **THEN** the integration reports automatic delivery as unavailable and obtains the required bodies explicitly
- **AND** it does not use a fresh diagnostic process's configuration as proof of this thread's event delivery

#### Scenario: A child Agent starts without inherited bodies
- **WHEN** a child has no proven complete context from its parent or hook
- **THEN** it loads the supplied task and applicable sources directly or reports the missing association
- **AND** it does not infer a task from another active session

### Requirement: Optional path injection declares its coverage

If enabled, path-scoped injection MUST use reviewed specification path declarations and a verified host integration. It MUST distinguish hook subprocess output from actual host enforcement and MUST document uncovered routes, error behavior, body limits, and recovery. It MUST NOT be a prerequisite for the stable explicit-reading workflow.

#### Scenario: A matching structured edit first receives a governing specification
- **WHEN** the optional integration is enabled and the first matching edit is attempted
- **THEN** acceptance checks actual body delivery and the host's handling of the first edit, followed by retry behavior

#### Scenario: A write route is outside path-hook coverage
- **WHEN** the Agent uses a route that the selected path hook does not cover
- **THEN** the integration does not claim that route was protected and retains the base engineering-context requirements

### Requirement: Completion uses relevant current evidence

Completion reporting MUST separate configuration validity, body delivery, and implementation compliance. Applicable checks MUST retain their actual results and scope, and changes after verification MUST cause reassessment of affected evidence. Local evidence archival MUST NOT substitute for OpenSpec specification archival or grant Git or deployment authorization.

#### Scenario: A required verification command fails
- **WHEN** a check required for the affected behavior fails
- **THEN** the change is not reported as verified complete and the actual failure remains available for repair

#### Scenario: Implementation changes after a successful check
- **WHEN** implementation changes affect previously verified behavior
- **THEN** the workflow reassesses and reruns the affected checks before using a completion claim

### Requirement: Shared guidance is portable and updates preserve customization

The shared guidance MUST be readable without personal tooling or private absolute paths. Optional integration setup and updates MUST preserve reviewed customization, expose missing required workflows, and avoid silently changing global profiles, hook trust, or other worktrees.

#### Scenario: A contributor uses a clean checkout
- **WHEN** the contributor has no maintainer-specific Codex configuration or local Trellis runtime
- **THEN** project instructions and engineering specifications remain discoverable and readable
- **AND** optional automation and private design resources are not silently assumed to exist

#### Scenario: Tool-generated instructions are refreshed
- **WHEN** an update would affect customized entrypoints or required apply/verify reading steps
- **THEN** the changes are previewed and the resulting entrypoints are verified before the integration is reported as usable

### Requirement: Delivery is demonstrated with real tasks

The integration MUST demonstrate engineering-context delivery and implementation compliance separately on a backend task and a design-system-dependent frontend task, with explicit results for recovery and failure handling. It MUST NOT claim measured efficiency gains without corresponding measurements.

#### Scenario: Complete the two pilot tasks
- **WHEN** the integration is evaluated for project acceptance
- **THEN** each pilot records its formal requirements, applicable bodies, delivery evidence, actual verification results, human corrections, and remaining limits
- **AND** configuration-only and isolated-hook tests are not substituted for those task results

### Requirement: Bounded cross-domain delivery and recovery evidence

Readers SHALL bound actual serialized output by default for frontend, backend, testing, deployment and CLI sources. Continuation SHALL pin the current payload identity. Delivery audits SHALL observe actual tool-output bodies for the current session/compaction epoch and task-scoped sources, without treating memory, emitted pages or checksums alone as proof of current delivery.

#### Scenario: Excessive or stale continuation request
- **WHEN** a request exceeds the supported page budget or continues against changed source/query content
- **THEN** the reader rejects it without partial success and requires a bounded restart

#### Scenario: A source list is checked after compaction
- **WHEN** the available transcript contains complete sources only before its latest compaction
- **THEN** the audit reports missing current delivery rather than reusing earlier success
- **AND** missing pages, source changes, malformed pages and unavailable resources remain unresolved; model comprehension and edit enforcement are outside the audit claim

#### Scenario: A caller omits declared required context
- **WHEN** a selected topic declares required local documents or design queries that are missing from the caller source list
- **THEN** the audit expands and checks those dependencies, deduplicating cycles
- **AND** a read-only task can supply inline sources without creating a task file or borrowing another session log
