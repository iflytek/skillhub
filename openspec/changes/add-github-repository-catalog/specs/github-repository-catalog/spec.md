## ADDED Requirements

### Requirement: Browse open-source Skills by repository

The open-source catalog SHALL provide repository browsing while retaining the existing default Skill view.

#### Scenario: Explore a synchronized repository

- **WHEN** a user selects the repository view
- **THEN** repositories with available members are listed with their current names and visible member counts
- **AND** opening a repository shows its available members with links to their existing Skill detail pages

#### Scenario: Follow a link after a repository rename

- **WHEN** the viewer opens a saved repository ID after its display name changes
- **THEN** the same repository is shown with the current name

### Requirement: Apply visibility before repository aggregation

Repository listing and repository detail SHALL use the same visibility and publication criteria for membership.

#### Scenario: Members are pending, unavailable or inaccessible

- **WHEN** a repository has members that the viewer cannot access or that are not currently available for download
- **THEN** those members are omitted from the results and counts
- **AND** a repository with zero visible members is not disclosed through the public catalog

#### Scenario: Historical repository association is missing

- **WHEN** an existing Skill has no verified source binding
- **THEN** the catalog does not place it in a repository group
- **AND** its existing Skill access behavior is preserved
