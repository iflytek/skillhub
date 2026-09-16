# Changelog

All notable CLI behavior changes are documented in this file.

## Unreleased

### Added

- Add the user-level `astudio` agent profile, displayed as AStudio, with automatic detection of
  `~/.acode/skills` on Linux, macOS, and Windows.
- Add repeatable `sync pull --skill <slug>` selection for non-interactive and JSON workflows, with
  interactive multi-select in a TTY.
- Add `skillhub upgrade <coordinate...>` for bounded, explicit upgrades of already-installed Skills,
  including side-effect-free `--check`, structured `--json`, local-change protection, and target
  filters.

### Fixed

- Preserve unknown fields in shared `~/.skillhub/config.json` and `credentials.json` files, and
  treat a compatible credentials document without first-party `tokens` as logged out instead of
  failing. Login and logout now modify only the first-party registry state.
- Return structured JSON from `help --json` and topic help, report unknown help topics as usage
  errors, and support `--version` / `-v` alongside the existing `version` command.
- Report successful publish and sync push requests as submissions, preserving the registry's raw
  `SCANNING`, `UPLOADED`, `PENDING_REVIEW`, or `PUBLISHED` status without implying final publication.
- Require an explicit non-`global` namespace for every sync action. `sync pull --check` remains a
  whole-namespace read-only check; mutating pulls now affect only explicitly selected skills.
- Prevent `--force` from overwriting an unmanaged or different-source Skill at the same target path.
  Source ownership is the full `registry + namespace + slug` identity.
- Reject registry downgrades and partial-target updates that the shared inventory version cannot
  represent safely.
- Exclude installer-owned `.skillhub/` state when publishing a local Skill directory.

- Resolve `namespace/slug`, `@namespace/slug`, and `namespace--slug`
  coordinates against their declared namespace instead of silently falling
  back to `global`.
- Reject a namespaced coordinate combined with a conflicting `--namespace`
  value; a matching value remains valid.
- Limit local removal with a namespaced coordinate or explicit `--namespace`
  to the matching namespace, preventing collateral deletion of same-slug
  installations in other namespaces. Bare-slug removal retains its existing
  cross-namespace behavior for compatibility.
- Preserve public registry `msg` and `requestId` fields for unsuccessful
  responses. HTTP 403 without a public message now reports the neutral
  `access denied` fallback instead of assuming the token lacks scope.
