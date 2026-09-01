# Changelog

All notable CLI behavior changes are documented in this file.

## Unreleased

### Added

- Add `skillhub upgrade <coordinate...>` for bounded, explicit upgrades of already-installed Skills,
  including side-effect-free `--check`, structured `--json`, local-change protection, and target
  filters.

### Fixed

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
