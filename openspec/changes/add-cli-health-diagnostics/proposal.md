## Why

Issue #1122 asks for a single, safe explanation of CLI configuration, registry connectivity, credential validity and local installation health. Existing `doctor` reconstructs inventory and may write backups and installation locks; `whoami` validates a credential but does not assemble a diagnostic report. Users need an explicitly selected read-only path without changing either existing command's contract.

## What Changes

- Add `skillhub doctor --health`, with `--offline`, `--registry`, `--token` and `--json` options for that mode. Bare `doctor` and its inventory JSON retain their current behavior.
- Report the selected registry and credential source, readable/parseable local state, and registered installation findings without modifying files, locking installations, deleting credentials or initiating login.
- Online mode checks only the selected registry using its existing CLI identity endpoint, with a bounded timeout, classified network/HTTP failures and no redirect forwarding of credentials. Offline mode makes no network requests.
- Return stable structured checks, actionable recovery guidance and documented exit semantics. Missing login and locally modified content are warnings; rejected supplied credentials and incomplete required checks are failures.
- Update command help and English/Chinese CLI documentation. No server, Web, database or package dependency changes are required.

## Capabilities

### New Capabilities

- `cli-health-diagnostics`: Explicit read-only local and selected-registry diagnosis, safe reporting, offline operation and legacy doctor compatibility.

### Modified Capabilities

None. There are no published main specifications for the existing CLI commands in this checkout. This change defines preservation requirements rather than replacing the legacy inventory workflow.

## Impact

- CLI entry/help, a dedicated health command/service and existing configuration, credential, inventory and installed-snapshot readers.
- New unit and real subprocess/HTTP-fixture tests, plus unchanged legacy doctor/auth regression checks.
- Existing `whoami`, `outdated`, login recovery (#1119/#1121) and inventory reconstruction keep their responsibilities.
- An eventual CLI release is needed to distribute the new mode; this change does not publish a release or deploy services.
