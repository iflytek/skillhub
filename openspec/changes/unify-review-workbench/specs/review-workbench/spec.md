## ADDED Requirements

### Requirement: One authorized review center
The system MUST present one review queue for authorized platform reviewers and TEAM owners/admins, limited by their existing review permissions. Namespace entries MUST preselect this same queue. Legacy links and the independent profile-review workflow MUST remain accessible. Governance MUST NOT duplicate the review task queue.

#### Scenario: A namespace administrator opens the center
- **GIVEN** an account manages one TEAM namespace but has no platform skill-review role
- **WHEN** it opens the center with no namespace filter
- **THEN** only tasks it is already authorized to review are returned and counted
- **AND** another namespace filter does not reveal unauthorized tasks

#### Scenario: A user administrator opens review management
- **GIVEN** an account has only USER_ADMIN
- **WHEN** it opens review management
- **THEN** its profile-review workflow remains available without granting skill-review access

### Requirement: One complete filter form
The queue MUST have no status tabs. One filter region MUST contain keyword, review status, namespace scope, request kind and origin plus Query and Reset. Filtering MUST occur on the server before pagination and counting. Draft conditions MUST not change results before Query; Query MUST apply them together and reset page. Reset MUST restore PENDING and other empty/all defaults. URL and detail return MUST retain applied filters, sorting and page.

#### Scenario: Query a filtered second page
- **GIVEN** a reviewer is browsing a later page and edits several filter fields
- **WHEN** Query is submitted
- **THEN** all conditions apply together, page becomes the first page, and the server count and rows use identical predicates

#### Scenario: Restore the queue from a detail page
- **GIVEN** a filtered queue and a selected review task
- **WHEN** the reviewer returns or uses browser history
- **THEN** the same authorized scope, conditions, sort and page are restored

### Requirement: Consistent compact dropdowns
Desktop filter dropdown triggers MUST be 176 by 32 pixels, with matching-width menus, 32-pixel options and 6-pixel menu padding. The mobile filter controls MUST remain 44 pixels high. Keyboard focus and selection MUST remain usable and content MUST not be clipped.

#### Scenario: Expand a desktop status filter
- **WHEN** the status dropdown is opened
- **THEN** its menu aligns with and has the same width as the trigger
- **AND** option text and selection indicators remain inside their rows

### Requirement: Review the effective bound presentation
The review detail MUST show the effective display name, summary and overview for the task-bound version using the same resolution rules as publication. Original package files MUST remain separately accessible. A failed bound-content read MUST not be replaced by another version's content.

#### Scenario: Review a custom pending update
- **GIVEN** an old published version and a pending version with web custom summary and overview
- **WHEN** an authorized reviewer opens the pending task
- **THEN** the custom pending content is visible for review and the public version remains unchanged until approval

### Requirement: Evidence-based changes
A normal version update MUST compare the task-bound presentation and package file list against a real published baseline. Missing published baseline MUST not be replaced by another pending version. Visibility and independent avatar requests MUST not pretend to be new content versions. GitHub source evidence and existing file diffs MUST remain available.

#### Scenario: A first publication has no baseline
- **WHEN** its task is viewed
- **THEN** the full pending content is reviewable without a fabricated comparison tab

### Requirement: Task-specific workbench layout
The confirmed dual-column detail MUST put readable target content in the main column and concise task/scope/version/evidence in the side card. The neutral segmented navigation MUST show only applicable sections and wrap without internal vertical scrolling on narrow screens. Approval/rejection, permissions, required reasons and processed read-only states MUST preserve existing domain rules.

#### Scenario: Inspect a visibility expansion
- **WHEN** its task is viewed
- **THEN** the previous and requested scopes and bound version are clear
- **AND** its bound scan evidence is not contradicted by an unrelated generic no-scan message


### Requirement: Stable detail navigation and content boundaries
The review detail MUST group the navigation and active content in one bounded surface without a nested page scrollbar. A user-initiated tab switch or evidence shortcut MUST align that surface below the fixed header after the new panel commits, including selecting the current evidence tab again. Query completion, initial rendering and comment edits MUST NOT trigger this navigation scroll.

#### Scenario: A long comparison is replaced with a short history panel
- **WHEN** the reviewer switches from a scrolled long comparison to history or opens scan evidence from the side panel
- **THEN** the selected panel starts below the fixed header rather than at the document bottom
- **AND** later query completion does not reset the reviewer's reading position
