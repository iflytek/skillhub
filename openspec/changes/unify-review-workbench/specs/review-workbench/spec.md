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
The v7 detail MUST integrate title, task metadata and evidence into one page header. All tabs MUST share the same available main width without a review-summary side card or oversized disclosure. The neutral segmented navigation MUST show only applicable sections and wrap without internal vertical scrolling on narrow screens. Approval/rejection, permissions, required reasons and processed read-only states MUST preserve existing domain rules.

#### Scenario: Inspect a visibility expansion
- **WHEN** its task is viewed
- **THEN** the previous and requested scopes and bound version are clear
- **AND** its bound scan evidence is not contradicted by an unrelated generic no-scan message


### Requirement: Stable detail navigation and content boundaries
The review detail MUST group the navigation and active content in one bounded surface without a nested page scrollbar. A user-initiated tab switch or evidence shortcut MUST return to the detail page top, including its heading, after the new panel commits, including selecting the current evidence tab again. Query completion, initial rendering and comment edits MUST NOT trigger this navigation scroll.

#### Scenario: A long comparison is replaced with a short history panel
- **WHEN** the reviewer switches from a scrolled long comparison to history or opens scan evidence from the page header
- **THEN** the detail heading is visible at the page top rather than leaving the reviewer at the document bottom
- **AND** later query completion does not reset the reviewer's reading position


#### Scenario: Navigation respects motion preferences
- **WHEN** the reviewer switches tabs or opens evidence
- **THEN** normal motion preferences use smooth scrolling to the page top
- **AND** reduced motion uses direct positioning without a queued animation

#### Scenario: Presentation and files are discoverable before a long overview
- **THEN** the summary is visually distinct from the overview
- **AND** file browsing and package download are available before the long overview
- **AND** comparison uses the available main width with responsive columns and explicit changed states


### Requirement: Meaningful compliance and version sections
The version context MUST use only the server-selected baseline and task-bound version, with their actual package counts and sizes and the pending changelog when available. It MUST NOT render a full version-history disclosure or infer an arbitrary baseline. Columns MUST align at the top without stretching short content to match long content.

#### Scenario: There is no displayable compliance declaration
- **THEN** no empty compliance section is rendered
- **AND** a missing snapshot is not converted into an empty declaration to infer additions or removals

#### Scenario: Compliance evidence is incomplete
- **THEN** the available declaration remains inspectable with an incomplete-comparison message
- **AND** a first publication shows only its current declaration without a fabricated historical diff

### Requirement: Source navigation and fixed GitHub review groups
The Web workbench SHALL present compact source capsules in the header for all submissions, user submissions and GitHub sync. Changing source or group with selected tasks SHALL require confirmation before clearing selection. GitHub-only grouping SHALL paginate complete authorized namespace/repository/acquisition/commit groups before member pagination; counts SHALL never be derived from a single page of members. Unassociated historical tasks SHALL remain accessible in the row view. Existing batch limits, independent transactions, scan/permission rechecks and audit records SHALL remain unchanged. No Suite workflow, Token scope, CLI command or automatic approval is added.

#### Scenario: Repository update contains more than one page of tasks
- **WHEN** an authorized reviewer opens a GitHub acquisition group
- **THEN** the summary counts the complete matching group and members paginate independently
- **AND** a decision applies only to explicit selected task IDs, at most 50
- **AND** partial success does not mark an entire repository snapshot distributable

#### Scenario: A reviewer changes source with a selection
- **WHEN** the reviewer selects another source
- **THEN** cancellation preserves the original source and selection
- **AND** confirmation clears selection and resets pagination before showing the new source

### Requirement: Discoverable skill repository provenance
GitHub skill detail SHALL reuse its existing provenance card and provide a source-repository action. Fixed commit/path/license information SHALL remain bound to the displayed source data. User-submitted skills SHALL not show a fabricated repository or description; missing repository descriptions SHALL remain omitted.
### Requirement: Review actions belong to their result table
The system SHALL place selection actions within the same result surface as its table rows and pagination, for ordinary review lists and repository member reviews.

#### Scenario: Selection replaces the result toolbar in place
- **WHEN** a reviewer selects one or more eligible tasks
- **THEN** the table toolbar shows the selection count, clear, reject and approve actions in place of its normal result controls, without creating a separate page-level action panel
- **AND** existing preview, confirmation, authorization and decision semantics remain unchanged

### Requirement: Repository snapshot review has an independent page
The system SHALL open a repository snapshot's review members on a dedicated authenticated route rather than expanding the review-center list.

#### Scenario: Navigate through the review hierarchy
- **WHEN** a reviewer opens a snapshot, opens a member review, and returns
- **THEN** the member returns to the same snapshot page with its member filters, page, valid selection and reading position restored
- **AND** returning from that page restores the original repository-list filters, page and reading position without replacing them with member filters
- **AND** legacy snapshot query links resolve to the dedicated snapshot page
