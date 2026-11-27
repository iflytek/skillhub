## Context

See proposal.md for motivation. `doctorCommand` calls `runDoctor`, which invokes `ensureWritable`, writes `.bak`, acquires target locks and mutates inventory. It cannot be reused as a read-only diagnostic. `ConfigStore` and `CredentialsStore` expose paths, but their existence helper hides access errors; health must distinguish missing from unreadable files. `parseInventory`, `readInstalledSkillMetadata`, `readInstallationState` and `snapshotInstalledSkillDirectory` provide existing compatibility and integrity rules. CLI subprocess helpers isolate HOME and SKILLHUB variables.

## Goals / Non-Goals

Goals: preserve existing command behavior, give deterministic scoped findings, retain all local bytes, bound online waiting, and make failures useful to people and scripts.

Non-goals: automatic remediation, login recovery implementation, registry-wide monitoring, version/update discovery, optional GitHub feature probes, server dependency checks, new production dependencies, or publishing the CLI.

## Decisions

1. **Explicit mode, separate implementation.** Add `doctor --health` rather than a duplicate top-level command or changing bare doctor to read-only. Health-only flags are rejected without the mode before legacy execution. A small health command renders a typed service report. The index prints that report and sets its exit status without changing the common renderer or other commands.
2. **Read files directly, reuse their contracts.** Obtain actual paths from existing stores, but read JSON with explicit ENOENT handling; other errors must remain visible. Validate object roots and relevant fields without printing content. Parse inventory through its existing version-aware parser. Do not call mutation/migration/lock helpers. Registry precedence remains argument, environment, configuration, default; token precedence remains argument, environment, matching saved credential. A bad config without an override suppresses network access.
3. **One selected-registry probe.** Reuse `SkillHubClient.whoami` with a diagnostic fetch adapter that adds a five-second abort deadline, refuses redirects and records transport/status evidence. Do not propagate raw client/server exception text. No new endpoint or optional-module probing. Missing credentials produce an unauthenticated probe; a 401 then establishes an HTTP response, not a rejected credential.
4. **Scoped existing integrity helpers.** Parse persisted inventory read-only, select matching-registry records, validate source identity and observe entry state before taking existing trusted snapshots. Modified content is a warning; legacy missing baselines are explicitly unverified. Reject unsafe/unreadable installations without mutation. A concurrent installation change can produce an inconclusive/failure finding; health never acquires a lock to make the observation atomic.
5. **Stable report.** Statuses are `pass`, `warn`, `fail`, `skipped`; messages are fixed, safe descriptions with actionable next steps. JSON uses schemaVersion 1 and mode `health`, including report exitCode. File failures outrank authentication, then network, then generic failures. Warnings (including not logged in, modified content and absent baseline) do not fail the command. Offline remote checks are skipped. Read-access checks do not certify future write permissions or operation authorization.
6. **Recovery advice works on current main.** #1121 is not merged, so a rejected stored credential gets explicit, registry-scoped logout followed by login instructions. Explicit/environment overrides get instructions to remove the override and recheck identity. Commands use a static `<registry>` placeholder instead of interpolating URLs that a shell could interpret. Diagnosis performs none of those actions itself.

## Risks / Trade-offs

- Mixing legacy repair and new diagnosis under one command → explicit mode, flag rejection, unchanged legacy subprocess tests and clear help.
- Credential reflection, URL userinfo or cross-host redirects → reject unsafe URLs, never print server bodies, redact known token values and refuse redirects.
- Different OS filesystem/transport behavior → injected error-code unit cases plus subprocess fixture checks; avoid claiming POSIX permission probes prove Windows ACLs or future writes.
- Installed trees may be large or concurrently changing → only registered targets for the selected registry, existing safe-tree checks, no full-home discovery or consistency claim under concurrent mutation.
- Remote status can change after the request → report only the observation; no claim of ongoing health or permission to publish.

## Migration Plan

No data migration. New options are opt-in and old doctor output is unchanged. Deliver as a normal CLI code change with updated help and bilingual guides. Rollback removes the added mode; it has written no user state that requires restoration.
