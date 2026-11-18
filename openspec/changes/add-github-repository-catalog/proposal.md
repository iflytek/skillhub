## Why

The open-source catalog lists synchronized Skills separately. Users who recognize a repository cannot browse its published Skills as a group.

## What Changes

- Add an optional repository view and a shareable repository detail view to the existing open-source catalog.
- Group available Skills by verified GitHub repository identity. Existing imports with a source binding appear without reimporting.
- Deliver repository browsing as the first slice of #1094. Pinned installation and repository-level review are separate deliveries.

## Capabilities

### New Capabilities

- `github-repository-catalog`: Browse available published GitHub Skills by verified repository.

### Modified Capabilities

None.

## Impact

Portal UI, localized copy, read API and GitHub source projections. Existing Skill links and workflows continue to use their existing contracts.
