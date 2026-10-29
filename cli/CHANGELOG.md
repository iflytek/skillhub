# Changelog

All notable CLI behavior changes are documented in this file.

## 0.3.0

### Changed

- Retain strict inventory v2 and derive activation from trusted entry files, without a second
  persisted activation state. Preserve normal published CLI 0.2.0 interoperability.
- Preserve disabled installations across namespace synchronization and doctor reconciliation.
  CLI upgrade refuses disabled targets, including with `--force`; Suite operations cannot adopt
  disabled positions. This release does not add CLI enable/disable commands.
- Supply the compatible local installation core for DSH `0.6.0`, including entry-only
  enable/disable and recovery. The DSH plugin bundles this core and does not invoke CLI commands.

### Upgrade and rollback

- No coordinated CLI upgrade is required for shared inventory. Historical explicit force
  install/upgrade/sync may overwrite disabled entries; explicit remove may delete them.
  New readers must reflect the resulting files; default paths must not unintentionally enable.
- Unpublished v3 inventories require validated, locked conversion with original-byte backup.
  Compatibility implementation and real old-package acceptance are in progress; publication
  remains blocked until they pass.
- Candidate versions are not a publication claim. Final package integrity, clean installation
  and migration acceptance must be recorded separately for the published packages.

## 0.2.0

### Added

- Add Skill Suite installation, checking, upgrade planning/execution and removal with exact
  member versions, shared ownership and local-change protection. Suite commands require a
  Registry implementing the Suite API; this release does not add that API to an existing server.
- Add OAuth Device Flow to `skillhub login` when no API token is supplied, including browser
  launch, `--no-open`, bounded polling and non-secret JSON progress.
- Add `pi`, `dsh` and `zcode` agent profiles and their user/project directory detection.
- Preserve authored `.skillhub/examples.json` with the skill package through publication,
  installation, synchronization and upgrades. Invalid example declarations remain ordinary
  files; only installer-owned `.skillhub/metadata.json` is excluded from directory publication.

### Changed

- Store installed versions per location in inventory schema v2. User and project copies can
  have different versions; updates and removals preserve unselected locations and Suite owners.
- Reading v1 inventory does not migrate it. Before the first successful v2 write, preserve a
  byte-verified `inventory.v1.<sha256>.backup.json` alongside the inventory.
- Upgrade all CLI clients sharing the inventory to `0.2.0` before using DSH plugin `0.4.0`.
  Old CLI/new inventory mixed writes are unsupported. Inventory backup alone is not a rollback
  of skill files, metadata or operation journals changed after migration.
- Provide the shared native installation core used by the DSH plugin, with precise target,
  confirmation, transaction recovery and local-change protection.

### Fixed

- Retain Registry URL prefixes when following application-relative download URLs, including
  install and namespace synchronization.
- Preserve numeric-looking version strings and reject repeated or conflicting version options.
- Preserve authored `.skillhub` files in fingerprints and packages instead of excluding the
  whole directory; normalize ZIP directory separators consistently.

## Notes retained from 0.1.12 and earlier

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
