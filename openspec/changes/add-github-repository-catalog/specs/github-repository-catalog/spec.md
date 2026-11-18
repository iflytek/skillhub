## ADDED Requirements

### Requirement: Browse open-source Skills by repository

The open-source catalog SHALL provide repository browsing while retaining the existing default Skill view.

#### Scenario: Explore a synchronized repository

- **WHEN** a user selects the repository view
- **THEN** repositories with available members are listed with their current names and visible member counts
- **AND** opening a repository shows its available members with links to their existing Skill detail pages

#### Scenario: Return to a repository search

- **WHEN** a user searches for repositories, opens a result, filters its members and opens a Skill
- **THEN** returning from the Skill preserves the member query and page
- **AND** returning from the repository preserves the repository query and page

#### Scenario: Follow a link after a repository rename

- **WHEN** the viewer opens a saved repository ID after its display name changes
- **THEN** the same repository is shown with the current name

### Requirement: Preserve the repository as a distinct resource

The catalog SHALL present a GitHub repository as a source grouping of independently usable Skills.

#### Scenario: Open a repository whose members also belong to a Suite

- **WHEN** the same Skill versions appear in a GitHub repository and a separately maintained Suite
- **THEN** the repository view uses its own source identity and member list
- **AND** viewing the repository does not create or modify a Suite, designate an Entry Skill, or offer Suite installation commands

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
