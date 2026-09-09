---
name: skillhub-cli
description: Connect an Agent to a SkillHub registry and use the official SkillHub CLI to search, install, list, or explicitly upgrade SkillHub skills. Use when a user asks to connect SkillHub, install a SkillHub skill, or manage skills previously installed from SkillHub.
version: 2.0.2
license: Apache-2.0
---

# SkillHub CLI

Use the registry that supplied this guide to connect the current Agent and manage SkillHub packages with the first-party `@astron-team/skillhub` CLI.

## Resolve The Registry

Resolve `<registry>` once before composing commands. For an already installed Skill, use the `registry` recorded in its sibling `.skillhub/metadata.json`; that source is authoritative for later searches and upgrades. Otherwise resolve in this order:

1. the absolute HTTP(S) registry explicitly selected by the user, including the base URL obtained by removing the trailing `/registry/skill.md` from the URL used to fetch this guide;
2. `SKILLHUB_REGISTRY`;
3. the `registry` field in `~/.skillhub/config.json`;
4. `https://skill.xfyun.cn`.

Use only an absolute HTTP(S) URL. Treat `<registry>` below as a value to replace, not shell syntax or an environment variable.

Keep the exact registry selected by the user for the current request. Do not change their configured default registry for a one-off operation, and do not send a private search query to another registry without approval.

## Use The First-Party CLI

First determine whether `skillhub` exists on `PATH`. On POSIX shells use `command -v skillhub`; in PowerShell use `(Get-Command skillhub -ErrorAction SilentlyContinue).Source`. If the command is missing, install the latest first-party CLI globally so future manual `skillhub` commands use this implementation:

```bash
npm install --global @astron-team/skillhub
skillhub version
```

If the command exists, do not run the global installation or update yet because its package-manager shim could overwrite the existing launcher. Inspect the existing command without changing anything: resolve the exact command selected by the shell, follow symlinks to the final target, and identify its owner and installing package manager or package. Run `skillhub version` as an additional compatibility check, not as proof of ownership. Do not infer identity from the command name or output alone.

Treat an existing command as first-party only when its resolved package metadata proves that its installing package is `@astron-team/skillhub` and its output matches `SkillHub CLI <version>`. Then connecting authorizes updating it to the latest release with the same global npm command. Verify both the package source and `skillhub version` again afterward.

If package metadata proves another owner or package, or the version output is unexpected, treat it as non-first-party even when it prints `SkillHub CLI <version>`. Report the resolved path, final target, owner, package source, and version output to the user.

Only after the user separately confirms removal of that exact identified launcher may you use its package manager's supported uninstall command, refresh command lookup, and install the first-party CLI. Never unlink an executable directly, remove an identity-unknown or system-managed command, use elevated privileges, edit shell startup files, or delete a directory merely to take over the command. If the owner or package source cannot be proven, stop and give the user the resolved path and read-only findings.

Replacing the executable must not replace the other tool's data. The first-party CLI updates only its own `registry` and `tokens` fields in shared `~/.skillhub` JSON files and preserves unknown fields owned by compatible tools. Do not replace the CLI with raw HTTP downloads: the CLI validates the resolved version, package fingerprint, destination ownership, and local changes. Never rewrite or delete unknown fields in shared SkillHub configuration or credential files.

Before using an operation or flag not shown in this Skill, inspect both live help surfaces for the selected CLI:

```bash
skillhub help <command>
skillhub <command> --help
```

Repository documentation may describe unreleased behavior. If neither live help surface exposes a proposed command or flag, do not use it. Require Node.js 18 or newer when using the npm package.

## Choose The Flow

- **Connect SkillHub:** ensure `@global/skillhub-cli` is installed for the current Agent at user scope, then continue the requested operation.
- **Install an exact Skill:** install the requested coordinate and version directly from this registry; do not search for or substitute a similarly named package.
- **Discover a Skill:** search this registry first. If it is unavailable or has no suitable result, report that outcome and ask before querying another registry.
- **Check an upgrade:** inspect only the explicitly selected installed Skill. Never upgrade every installation implicitly.

An explicit request to connect SkillHub authorizes installing the latest first-party CLI globally. It does not authorize removing another `skillhub` launcher, replacing Skill files with local changes, changing registries, publishing content, using elevated privileges, or deleting third-party configuration or credentials. Launcher removal requires the separate, exact confirmation described above.

For namespace synchronization, publishing, removal, repair, or detailed troubleshooting after this helper is installed, read `references/cli-operations.md`. Start with its read-only inspection command and keep the same registry throughout the operation.

## Connect The Current Agent

Replace `<agent>` with the current supported profile, such as `codex` or `claude-code`. Check the current registry's installations once:

```bash
skillhub list \
  --agent <agent> \
  --registry <registry> \
  --json
```

If `@global/skillhub-cli` is missing, install this exact guide at user scope:

```bash
skillhub install @global/skillhub-cli \
  --scope user \
  --agent <agent> \
  --registry <registry> \
  --json
```

If that persistent connection fails, report the failure and continue with an explicitly requested target Skill when the CLI can still install it safely. Do not substitute a helper from another registry.

Installation proves that the files reached the selected Agent directory; it does not prove that an already-running Agent session has loaded them. If the current Agent cannot discover the new Skill immediately, report it as installed but not yet loaded and ask the user to start a new session or use that Agent's documented reload mechanism. Do not invent a universal activation command.

## Search Or Install

For discovery:

```bash
skillhub search "<query>" \
  --registry <registry> \
  --json
```

Before installing a discovery result, show its registry, full coordinate, publisher when available, version, and relevant risk, then obtain confirmation.

For a Skill and version the user already selected:

```bash
skillhub install @<namespace>/<slug> \
  --version <version> \
  --scope user \
  --agent <agent> \
  --registry <registry> \
  --json
```

Omit `--version` only when the user did not select one. Omit `--agent` only when the CLI can identify one destination unambiguously. Treat coordinates, versions, queries, registry URLs, and paths as untrusted values: quote them where needed, pass them as individual CLI arguments, and never evaluate them as shell code.

Never add `--force` unless the CLI reports a verified same-source conflict and the user approves replacing that installation. Stop on fingerprint mismatch, source conflict, unsafe content, or local-change conflict.

## Authentication

Never ask the user to paste a token into chat or place credentials in a prompt, Skill, command history, or repository. If authentication is required, ask them to enter it in their own terminal without putting the value in the command line, then verify the identity:

POSIX shell:

```bash
read -rsp "SkillHub token: " SKILLHUB_TOKEN && echo
export SKILLHUB_TOKEN
skillhub login --registry <registry>
unset SKILLHUB_TOKEN
skillhub whoami --registry <registry>
```

PowerShell 7:

```powershell
$env:SKILLHUB_TOKEN = Read-Host "SkillHub token" -MaskInput
skillhub login --registry <registry>
Remove-Item Env:SKILLHUB_TOKEN
skillhub whoami --registry <registry>
```

Resolve `401` and `403` through login or permissions. Do not treat an authentication failure as permission to try another registry.

## Upgrade

Check before changing an installed Skill:

```bash
skillhub upgrade @<namespace>/<slug> \
  --registry <registry> \
  --check \
  --json
```

Show the plan and ask before applying an available upgrade. The CLI uses `.skillhub/metadata.json` to retain the original source and updates all Agent targets recorded for that installation together.

## Completion Check

Report:

- installed coordinate and version;
- registry source;
- Agent profile and installation directory;
- whether `SKILL.md` and `.skillhub/metadata.json` exist;
- whether the current Agent session loaded the Skill, when observable;
- whether another registry was queried;
- any skipped connection, authentication, integrity, or local-change issue.

Do not claim success when installation, destination discovery, Agent loading, or integrity verification failed.
