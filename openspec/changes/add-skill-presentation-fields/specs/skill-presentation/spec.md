# Requirements

## Presentation metadata

The system MUST accept optional `metadata.short-description` and `metadata.overview` in SKILL.md. The short description MUST be at most 200 characters and the overview at most 20,000 characters.

## Web publish precedence

A web-provided value MUST override the package value for that submitted version. The archive MUST remain unchanged. Reset MUST remove the custom override so the package/default resolution applies.

## Lifecycle isolation

A pending version MUST NOT replace the currently published title, summary, or overview. Published metadata MUST be restored by rollback/yank using the selected published version snapshot.

## Search and detail

The effective short description MUST be used as the public summary. The original package description MUST remain searchable. The detailed overview MUST NOT be indexed and MUST fall back to README.md then SKILL.md when no explicit/package overview exists.

## Scenarios

- Given package metadata and web values, when previewing, the form is prefilled from package defaults and explicit edits win.
- Given a pending update, when it is submitted, public summary remains unchanged until publication.
- Given reset, when the version publishes, it uses package defaults and later default versions read their own package defaults.
