## ADDED Requirements

### Requirement: CSS-authoritative design projection
Theme and component readers SHALL verify the complete supported token projection and source hash against the reviewed CSS, and reject stale values, missing fields, unresolved aliases and cycles without partial success. Refresh SHALL change only the derived projection.

#### Scenario: Stale or missing value
- **WHEN** the projection has an incorrect value or omits a supported CSS token
- **THEN** check and dependent theme/component reads fail with the discrepancy
- **AND** refreshing reviewed CSS produces a stable projection including light/dark aliases and control proportions

### Requirement: Automatic scoped design context
SkillHub visible UI work SHALL read the applicable repository visual and component rules without requiring the user to name a Skill. The shared design core SHALL ship under its reviewed license and be the default source; another source requires explicit task selection. Pure data work SHALL retain proportional context.

#### Scenario: Clean public checkout
- **WHEN** a contributor checks out the repository without private design files, editor plugins or registered caches
- **THEN** the default reader obtains the repository-owned theme, complete component contracts and applicable design rules
- **AND** private resources and personal work records are not required or published

#### Scenario: Changed or unavailable source
- **WHEN** a required snapshot is absent or its content differs from registration
- **THEN** dependent design work reports unresolved context rather than inventing values or silently reverting to the old theme

### Requirement: Complete bounded design reads
Design reads SHALL support bounded chunks with a stable payload identity and continuation offset, preserving complete component contracts and source values.

#### Scenario: Multi-chunk contract
- **WHEN** a selected component exceeds one output budget
- **THEN** all chunks reconstruct the original complete payload
- **AND** the final chunk alone is not represented as complete delivery

### Requirement: Preserve product behavior during foundational adoption
The foundational theme and Button/Input/Select/Textarea mapping SHALL preserve existing API, native and controlled behavior while adopting the reviewed sizes and focus rules. Verification SHALL separate component/browser checks from backend acceptance.

#### Scenario: Desktop and narrow viewport controls
- **WHEN** controls are exercised in light/dark and regular/compact contexts
- **THEN** their scoped sizes, visible keyboard focus, invalid/loading/completion states and popup focus behavior are verified
- **AND** fixed-response product checks are not described as live backend or production acceptance
