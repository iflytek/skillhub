# CLI reference

SkillHub CLI is the official command-line tool for SkillHub, designed for searching, installing, managing, and publishing Agent skill packages.

## Choose the object you are managing

| Goal | Command | Scope |
| --- | --- | --- |
| Install a remote Skill | install | Selected Agent, scope or custom directory |
| Update installed skills | upgrade | Explicit coordinates and targets; no implicit upgrade-all |
| Discover installed skill updates | outdated | Read-only, scoped to the effective registry |
| Install or update skills by GitHub repository | repository | Requires a compatible repository plan; separate from Suites and server-side GitHub synchronization |
| Manage pinned collections | suite | Suite inventory and member ownership |
| Sync team authoring files | sync | Non-global namespace workspace, not Suite lifecycle |
| Update the CLI executable | update | CLI installation, not installed skills |
| Recover local records | doctor | Project metadata and inventory, not remote upgrades |

Existing command names and compatibility entry points remain supported. `repository check` and
`repository upgrade --check` use the same read-only plan. `outdated` discovers updates, selected-skill
`upgrade --check` previews an upgrade, and `update` updates the CLI itself. `suite check` inspects the
installed Suite and member state; `suite upgrade --check` previews remote member changes. `sync`
manages namespace authoring workspaces, not server-side GitHub synchronization; `status` lists all
states and `diff` shows differences. See the [complete CLI command reference](https://github.com/iflytek/skillhub/blob/main/cli/README.md#-command-reference).

## Automation and failure handling

Specify `--scope` and `--agent`, or use `--dir` alone, for deterministic installation targets. Do not combine custom directory with scope/agent. Check `version` and `help <command>` against the installed CLI; older releases may not provide every documented capability.

Keep search, login, install and check on the same registry. Use explicit `--registry` for individual commands or an environment variable for the process tree. Credentials are registry-specific; verify identity with `whoami` after changing endpoints.

Use `--json` for machine-readable output and do not expect interactive prompts. Capture status and output; retain public error messages and requestId for registry errors. Suite check's `ok: false` may simply indicate changes, so inspect its fields. `--check` is a preview option on applicable upgrade/sync commands, not a universal option.

For directory conflicts, inspect source ownership before using `--force`: it does not take over unmanaged or different-source targets. Preserve files and metadata before recovering inventory. Do not delete all of `~/.skillhub`; it also holds other registry credentials and installation records.

## Installation

```bash
# Install globally via npm
npm install -g @astron-team/skillhub

# Or run directly with npx
npx @astron-team/skillhub@latest version

# Or install globally via Bun
bun add -g @astron-team/skillhub
```

## Quick Start

```bash
# Login
skillhub login

# Search skills
skillhub search pdf

# Install skill to Agent directory
skillhub install pdf-parser --agent codex

# List installed skills
skillhub list

# Publish skill
skillhub publish ./my-skill --namespace myspace
```

## Registry Configuration

The active registry is resolved in the following priority order:

1. `--registry <url>` command-line argument
2. `SKILLHUB_REGISTRY` environment variable
3. `registry` in `~/.skillhub/config.json`
4. Default value `https://skill.xfyun.cn`

```bash
# Temporarily use another registry
skillhub search pdf --registry https://skillhub.example.com

# Set via environment variable (Linux/macOS)
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

**Windows PowerShell:**

```powershell
$env:SKILLHUB_REGISTRY="https://skillhub.example.com"
```

**Windows CMD:**

```cmd
set SKILLHUB_REGISTRY=https://skillhub.example.com
```

## Authentication

Token resolution priority:

1. `--token <token>` command-line argument
2. `SKILLHUB_TOKEN` environment variable
3. Token stored in `~/.skillhub/credentials.json` (per registry)

### Login

`skillhub login` resolves a token from arguments, environment variables, or saved registry credentials, and starts OAuth Device Flow when none is resolved. A saved credential that the registry rejects with HTTP 401 is reported and followed by Device Flow, so the account can be recovered without a separate `logout`. A token supplied by `--token` or `SKILLHUB_TOKEN` is never replaced automatically: the CLI names the rejected input and explains how to retry without it. Follow the verification URL and user code to authorize in a browser. Use `skillhub login --no-open` on headless terminals, or an API token for automation.

```bash
# Login with API token
skillhub login --token sk_xxx

# Login to specific registry
skillhub login --token sk_xxx --registry https://skillhub.example.com
```

`login` validates the token, stores it in `~/.skillhub/credentials.json`, and writes the registry to `~/.skillhub/config.json`.

Both files are updated non-destructively: SkillHub CLI changes only its own `tokens` and `registry`
fields and preserves unknown fields written by other compatible tools.

When an API-token request is denied, the CLI shows the safe reason returned by the server and its `Request ID`. Use that ID to correlate the failure with server logs. Other authorization failures continue to use a generic message.

### Check Current Identity

```bash
skillhub whoami

# Check specific registry
skillhub whoami --registry https://skillhub.example.com

# Temporarily use different token
skillhub whoami --token sk_other
```

### Logout

```bash
skillhub logout

# Logout from specific registry
skillhub logout --registry https://skillhub.example.com
```

Logout only removes the token for the specified registry, preserving registry configuration and installation records.

## Search

```bash
# Keyword search
skillhub search pdf

# List all skills (empty query)
skillhub search "" --limit 50

# JSON output
skillhub search pdf --json
```

Output format: `namespace/slug  version  summary`

## Install Skills

Install coordinates accept a bare slug (resolved to `global` by default) and
three equivalent explicit namespace forms. When an explicit coordinate and
`--namespace` are both present, they must match.

```bash
# Install to auto-detected Agent directory
skillhub install pdf-parser

# Equivalent namespace coordinates
skillhub install team/my-skill
skillhub install @team/my-skill
skillhub install team--my-skill

# Choose install scope explicitly
skillhub install pdf-parser --scope user
skillhub install pdf-parser --scope project --agent codex

# Specify a namespace for a bare slug
skillhub install pdf-parser --namespace myspace

# Specify version
skillhub install pdf-parser --version 1.2.0

# Install to specific Agent
skillhub install pdf-parser --agent codex

# Install to AStudio's fixed user-level directory
skillhub install pdf-parser --agent astudio

# Install to Pi's user-level directory (use --scope project for the project directory)
skillhub install pdf-parser --agent pi

# Install to DeepSeek Harness (use --scope project from the repository root for project skills)
skillhub install pdf-parser --agent dsh

# Install to multiple Agents
skillhub install pdf-parser --agent codex --agent claude-code

# Install to custom directory
skillhub install pdf-parser --dir ~/.claude/skills

# Reinstall a SkillHub-managed installation from the same source
skillhub install pdf-parser --force
```

### Install Target Resolution

The CLI determines the installation location using the following logic:

1. If `--dir` is specified: Install to that directory, agent marked as `custom`. `--dir` is mutually exclusive with `--scope` and `--agent`.
2. If `--scope user|project` is specified: Limit detection to the chosen scope.
   - With `--agent <profile>`: Install to that profile's user or project skills directory directly.
   - Without `--agent`: Detect existing skills directories within the chosen scope only. In interactive user scope, the `generic` target (`<home>/.agents/skills/`) is always also offered and can be selected alone or together with detected targets.
   - No detected directory in the chosen scope → Fallback to `<home>/.agents/skills/` for `--scope user` or `<cwd>/.agents/skills/` for `--scope project`.
3. If `--agent` is specified (no `--scope`): Install to the corresponding Agent's skills directory (existing behaviour, unchanged).
4. If none of the above is specified:
   - **Interactive mode** (stdin and stdout are both TTY, no `--json`): Prompt for `user` or `project` scope first, then continue per the `--scope` rule above.
   - **Non-interactive mode**: Auto-scan current directory to detect existing Agent config directories. 1 Agent detected → install directly; multiple → error; none detected → fallback to `<cwd>/.agents/skills/`.

> `--dir` cannot be combined with `--scope` or `--agent`.

### Install Paths

Most Agents have both project-level and user-level skills directories. Use `--scope user|project` to control which one is used. AStudio uses its fixed user-level directory only.

| Agent | Project-level Path | User-level Path |
|-------|-------------------|-----------------|
| `astudio` (AStudio) | Not supported | `~/.acode/skills/` |
| `claude-code` | `<project>/.claude/skills/` | `~/.claude/skills/` |
| `codex` | `<project>/.codex/skills/` | `~/.codex/skills/` |
| `cursor` | `<project>/.cursor/skills/` | `~/.cursor/skills/` |
| `dsh` (DeepSeek Harness) | `<project>/.dsh/skills/` | `~/.dsh/skills/` |
| `github-copilot` | `<project>/.github-copilot/skills/` | `~/.github-copilot/skills/` |
| `gemini-cli` | `<project>/.gemini/skills/` | `~/.gemini/skills/` |
| `windsurf` | `<project>/.windsurf/skills/` | `~/.windsurf/skills/` |
| `kiro-cli` | `<project>/.kiro/skills/` | `~/.kiro/skills/` |
| `roo` | `<project>/.roo/skills/` | `~/.roo/skills/` |
| `trae` | `<project>/.trae/skills/` | `~/.trae/skills/` |
| `trae-cn` | `<project>/.trae-cn/skills/` | `~/.trae-cn/skills/` |
| `openhands` | `<project>/.openhands/skills/` | `~/.openhands/skills/` |
| `openclaw` | `<project>/.openclaw/skills/` | `~/.openclaw/skills/` |
| `opencode` | `<project>/.opencode/skills/` | `~/.opencode/skills/` |
| `kilo` | `<project>/.kilo/skills/` | `~/.kilo/skills/` |
| `pi` (Pi) | `<project>/.pi/skills/` | `~/.pi/agent/skills/` |
| `zcode` | `<project>/.zcode/skills/` | `~/.zcode/skills/` |
| _fallback_ | `<project>/.agents/skills/` | `~/.agents/skills/` |

For a custom path or an unsupported Agent directory, use `--dir` to specify the installation path. In interactive user scope, the `generic` target is offered alongside detected Agent targets. AStudio appears in that selector when `~/.acode/skills/` exists. When `--scope user|project` finds no matching agent directory, the CLI falls back to the `_fallback_` row above.

DeepSeek Harness resolves project skills from the nearest Git repository root, while SkillHub CLI uses the current directory for project-scoped profiles. Run `--scope project --agent dsh` from the repository root. If `DSH_HOME` overrides the default `~/.dsh`, install with `--dir "$DSH_HOME/skills"`.

### File Structure After Installation

```
.codex/skills/pdf-parser/
├── ...                          # Extracted skill package files
└── .skillhub/
    └── metadata.json            # Installation metadata
```

`metadata.json` example:

```json
{
  "schemaVersion": 1,
  "registry": "https://skill.xfyun.cn",
  "namespace": "global",
  "slug": "pdf-parser",
  "version": "1.0.0",
  "versionId": 123,
  "fingerprint": "sha256:...",
  "files": { "SKILL.md": "sha256..." },
  "agent": "codex",
  "installedAt": "2026-04-28T06:00:00.000Z"
}
```

The CLI creates this file after extracting the registry package. It is not included in the downloaded
ZIP, and `.skillhub/` is excluded when an installed directory is published again.

## Check Installed Skills for Updates

`outdated` requires a CLI version containing this command; upgrading the server does not add it to an older CLI.

```bash
skillhub outdated --json
skillhub outdated --namespace team --agent codex --registry https://skillhub.example.com
```

The command checks installations in the effective registry only. It does not download, acquire
installation locks, write local files, migrate inventory or apply updates. Results include local
modification state and numeric version-change labels; `0.1.0 -> 0.2.0` is `minor`, without promising
compatibility. GitHub content-hash versions are `unknown` unless unchanged; hash text is not chronology.

A complete check exits zero, including when there are updates or unknown version ordering.
Failed lookups preserve the available results but produce a nonzero exit, `ok: false`, and
`summary.failed`; stdout remains a single JSON report and stderr contains the error. The first
lookup failure supplies the existing CLI exit code. Use explicit `upgrade --check` to plan changes.

## Upgrade Installed Skills

```bash
# Preview without changing files
skillhub upgrade @global/skillhub-cli --check

# Upgrade one or a bounded list of installed Skills
skillhub upgrade @global/skillhub-cli
skillhub upgrade @team/code-review @team/java-guide

# Deterministic machine-readable plan
skillhub upgrade @team/code-review --check --json
```

`upgrade` never installs a missing Skill and has no implicit upgrade-all mode. Local changes require
`--force`; it only replaces a managed installation whose full `registry + namespace + slug` source
matches.

Versions are tracked per target. `--agent` and `--dir` select installed targets; Suite-owned targets are protected from direct upgrade and should use `suite upgrade`.
The command also keeps the local files when the registry resolves to an older version.
If a multi-Skill run fails after an earlier upgrade commits, execution stops and reports each item
as `upgraded`, `failed`, or `not-attempted`; a committed upgrade is never rolled back implicitly.
New installations store absolute target paths. Reinstall an older entry that still contains relative
target paths before upgrading it; the CLI cannot safely infer the original working directory.

## Using installed skills and Suites

Installation downloads Skill files; it does not execute a task. Open the target project in your Agent, verify it discovers `SKILL.md`, configure the documented dependencies, and ask it to use the Skill. See the [Suite usage guide](./skill-suites).

```bash
# Replace with a real Suite coordinate
skillhub suite install @team/example-suite --scope project --agent codex
skillhub suite check @team/example-suite
skillhub suite upgrade @team/example-suite --check
skillhub suite upgrade @team/example-suite
skillhub suite remove @team/example-suite
```

## Team workspace sync

`sync` operates on non-global namespaces: pull selected skills, inspect status/diff, then push local content. The default workspace is `.agents/skills` in the current directory. Non-interactive pull requires explicit `--skill` selections; `--check` only inspects.

```bash
skillhub sync pull --namespace team --check
skillhub sync pull --namespace team --skill example-skill
skillhub sync status --namespace team
skillhub sync diff --namespace team
skillhub sync push --namespace team --all --dry-run
```

## Local Management

### List Installed Skills

```bash
# List all installed skills
skillhub list

# Filter by Agent
skillhub list --agent codex

# Filter by multiple Agents
skillhub list --agent codex --agent claude-code

# Filter by directory
skillhub list --dir ~/.codex/skills

# JSON output
skillhub list --json
```

### Remove Skills

```bash
# A bare slug removes same-named local installations across namespaces
skillhub remove pdf-parser

# An explicit namespaced coordinate removes only that namespace
skillhub remove myspace/pdf-parser
skillhub remove @myspace/pdf-parser
skillhub remove myspace--pdf-parser

# Equivalent precise local removal with an explicit namespace
skillhub remove pdf-parser --namespace myspace

# Remove only specific Agent's installation
skillhub remove pdf-parser --agent codex

# Remove all targets (skip interactive confirmation)
skillhub remove pdf-parser --all

# Remove remote skill (requires authentication, prompts for confirmation)
skillhub remove pdf-parser --remote --namespace myspace

# Skip remote deletion confirmation
skillhub remove pdf-parser --remote --hard --namespace myspace
```

> Parameter exclusivity rules:
> - `--all` cannot be used with `--agent`
> - `--remote` cannot be used with `--agent` or `--all`
> - Remote deletion in non-interactive environments requires `--hard`

### Rebuild Local Inventory

```bash
skillhub doctor
```

`doctor` performs the following operations:

1. Scans `<cwd>/.<agent>/skills/<slug>/.skillhub/metadata.json`
2. Groups by `registry + namespace + slug`
3. Backs up old `inventory.json` (if exists)
4. Writes new `inventory.json`

Versions are tracked per target. Doctor reports recovery problems; it does not download or upgrade skills.

### Read-only Health Diagnosis

```bash
skillhub doctor --health
skillhub doctor --health --registry https://skillhub.example.com --json
skillhub doctor --health --offline --json
```

`--health` explicitly selects diagnosis instead of inventory reconstruction. It checks actual state
paths, configuration and credential sources, the selected registry, and its registered installations.
It never repairs files, creates backups, acquires installation locks, deletes credentials or starts login.
`--offline` makes no network requests and marks remote checks as skipped; saved-token validity remains unknown.

Online mode sends one request to the existing CLI identity endpoint with a five-second limit and no
redirect following. It distinguishes observed transport, HTTP and authentication failures without
printing tokens or raw error bodies. Optional GitHub/Suite features and server internals are not probed.
Accepted identity does not certify publish/delete permissions, and read access does not certify write access.

Missing login, modified content and missing legacy integrity baselines are warnings. Unsafe, unreadable,
missing or inconsistent installations are failures. Only selected-registry records are inspected;
enabled/disabled state and all local content are preserved.

Health JSON is one stdout object with `schemaVersion: 1`, `mode: "health"`, `ok`, selected registry and
credential sources, state `paths`, `checks` and `exitCode`. Check statuses are `pass`, `warn`, `fail`
or `skipped`, with stable identifiers/codes, safe messages and optional next steps. Failed runs still
produce the full report on stdout without stderr chatter. Exit 0 permits warnings/skipped checks;
failure priority is filesystem 4, authentication 2, network 3, then other HTTP/protocol failure 1.
Usage errors remain 5. Legacy `doctor --json` is unchanged. Health-only options require `--health`.

Saved credentials rejected with HTTP 401 get registry-scoped login guidance without requiring logout;
HTTP 403 calls for checking permissions. Override sources get separate instructions. No recovery action is performed. Use `whoami` for direct identity verification
and `outdated` for installed-update discovery.

## Publishing

```bash
# Publish directory (auto-packaged as zip)
skillhub publish ./my-skill --namespace myspace

# Publish existing zip file
skillhub publish ./my-skill.zip --namespace myspace

# Specify visibility
skillhub publish ./my-skill --namespace myspace --visibility private
```

Visibility options:
- `public` (default) — Visible to everyone
- `namespace-only` — Visible to namespace members only
- `private` — Visible to yourself only

After the server accepts the submission, the CLI shows its current status and detail URL. `SCANNING` and `PENDING_REVIEW` are asynchronous states, not final publication; check the detail page for completion.

## Self-Update

```bash
# Check for new version
skillhub update --check

# Execute update
skillhub update
```

Update mechanism:
- Installed via npm globally: Auto-executes `npm install -g @astron-team/skillhub@latest`
- Installed via Bun globally: Auto-executes `bun add -g @astron-team/skillhub@latest`
- Run via npx: Prompts manual update command
- Unknown installation method: Prompts manual update

## Environment Variables

| Variable | Description | Priority |
|----------|-------------|----------|
| `SKILLHUB_REGISTRY` | Default registry URL | Lower than `--registry` parameter |
| `SKILLHUB_TOKEN` | API token | Lower than `--token` parameter, higher than stored token |

## Local File Structure

```
~/.skillhub/
├── config.json           # User configuration (registry, defaultAgent, etc.)
├── credentials.json      # API tokens (stored per registry, permissions 0600)
└── inventory.json        # Installed skills inventory
```

### config.json

```json
{
  "registry": "https://skill.xfyun.cn",
  "defaultAgent": "codex",
  "lastUpdateCheckAt": "2026-04-28T06:00:00.000Z"
}
```

### credentials.json

```json
{
  "tokens": {
    "https://skill.xfyun.cn": "sk_xxx",
    "https://skillhub.example.com": "sk_yyy"
  }
}
```

### inventory.json

CLI-managed installation records, not a file to hand-edit. Each target tracks its own version, fingerprint and ownership; compatible writers migrate legacy records when needed. A backup is recovery evidence, not permission to overwrite a newer inventory.

Current main contains DSH enable/disable support using inventory v3. Do not assume your published CLI supports it or mix incompatible writers. There are no CLI enable/disable commands. In that increment, disabled targets cannot be upgraded with CLI `--force`, and doctor does not reconstruct disabled ownership from a loose `SKILL.md.disabled` file. Manage activation through a compatible DSH version and follow the matched release instructions.

## JSON Output

All commands support the `--json` parameter for machine-readable JSON output:

```bash
skillhub search pdf --json
skillhub list --json
skillhub whoami --json
skillhub install pdf-parser --json
skillhub remove pdf-parser --json
skillhub doctor --json
```

The following JSON excerpt shows only the success flag, not a complete command response. Result fields depend on the command:

```json
{"ok": true}
```

Error response format:

```json
{
  "ok": false,
  "message": "error message",
  "exitCode": 2,
  "details": {
    "registry": "https://skill.xfyun.cn",
    "next": "run `skillhub login`"
  }
}
```

## Exit Codes

| Exit Code | Description |
|-----------|-------------|
| 0 | Success |
| 1 | General error |
| 2 | Authentication failure |
| 3 | Network error |
| 4 | File system error |
| 5 | Parameter error |
| 6 | Validation or installation ownership conflict |

## Command Reference

### help

```bash
skillhub help
skillhub help install
```

Display help information.

### version

```bash
skillhub version
skillhub version --json
```

Display CLI version.

### login

```bash
skillhub login [--no-open] [--token <token>] [--registry <url>] [--json]
```

Authenticate with Device Flow or a supplied API token, then save credentials and registry configuration.

The CLI preserves unknown fields in the shared configuration and credentials documents when it
updates its own `registry` and `tokens` fields.

### logout

```bash
skillhub logout [--registry <url>] [--json]
```

Remove token for specified registry.

### whoami

```bash
skillhub whoami [--registry <url>] [--token <token>] [--json]
```

Validate current token and display user information.

### search

```bash
skillhub search <query> [--registry <url>] [--limit <n>] [--json]
```

Search published skills.

### install

```bash
skillhub install <coordinate> [options]
```

`<coordinate>` accepts a bare slug (`my-skill`, resolved as `global/my-skill`)
or any of the equivalent explicit namespace forms: `team/my-skill`,
`@team/my-skill`, and `team--my-skill`. Use `--namespace team` to select a
non-global namespace for a bare slug. An explicit coordinate may be combined
with the same `--namespace`; a conflicting value is rejected as a usage error.

Options:
- `--scope <user|project>` — Install scope (omit for interactive prompt in TTY, or fall back to existing detection in non-TTY)
- `--namespace <slug>` — Namespace for a bare slug
- `--version <v>` — Version (default: latest)
- `--agent <profile>` — Agent profile (repeatable)
- `--dir <path>` — Custom installation directory (mutually exclusive with `--scope` and `--agent`)
- `--force` — Replace an existing SkillHub-managed installation from the same source
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON output

### upgrade

```bash
skillhub upgrade <coordinate...> [options]
```

Options:
- `--namespace <slug>` — Filter a bare slug by namespace
- `--agent <profile>` — Filter installed targets by Agent (repeatable)
- `--dir <path>` — Filter installed targets by directory
- `--registry <url>` — Filter by installation source registry
- `--check` — Print the exact plan without modifying files
- `--force` — Replace local changes only for the same full source identity
- `--json` — JSON output

### list

```bash
skillhub list [options]
```

Options:
- `--agent <profile>` — Filter by Agent (repeatable)
- `--dir <path>` — Filter by directory
- `--registry <url>` — Registry URL
- `--json` — JSON output

### remove

```bash
skillhub remove <coordinate> [options]
```

Options:
- `--agent <profile>` — Filter by Agent (repeatable)
- `--all` — Remove all targets
- `--remote` — Remove remote skill
- `--hard` — Skip remote deletion confirmation
- `--namespace <slug>` — Namespace for local or remote deletion
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON output

An explicit namespaced coordinate (`team/my-skill`, `@team/my-skill`, or
`team--my-skill`) or `--namespace team` removes local installations only from
that namespace. For compatibility, a bare slug removes same-named local
installations across all namespaces in the current registry.

### doctor

```bash
skillhub doctor [--json]
skillhub doctor --health [--offline] [--registry <url>] [--token <token>] [--json]
```

Scan project directory and rebuild local inventory.
With `--health`, diagnose without inventory maintenance; `--offline` skips network access.

### publish

```bash
skillhub publish <path> [options]
```

Options:
- `--namespace <slug>` — Namespace
- `--visibility <v>` — Visibility (`public` | `namespace-only` | `private`)
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON output

### suite

```bash
skillhub suite <install|check|upgrade|remove> <coordinate> [options]
```

`install` supports `--version`, `--scope`, `--agent`, and `--dir`; `install` and `upgrade` support `--force`. `upgrade --check` previews changes. All actions support `--registry`, `--token`, and `--json`. See [Suites](./skill-suites).

### sync

```bash
skillhub sync <pull|status|diff|push> --namespace <slug> [options]
```

Run `skillhub help sync` for selection, workspace and publishing options. Validate with `push --dry-run` before publishing.

### update

```bash
skillhub update [--check] [--json]
```

Check or execute CLI self-update.

## Security Notes

- Tokens are stored only in user directory `~/.skillhub/credentials.json`
- On Linux/macOS, credential file permissions are automatically set to `0600`
- Tokens are never written to any project-local files
- Remote delete operations require explicit confirmation or `--hard` parameter
- `remove` command validates path safety to prevent deletion of non-skill directories

## Troubleshooting

### Authentication Failure

```bash
# Verify token validity
skillhub whoami

# Re-login
skillhub login --token sk_xxx
```

### Network Error

```bash
# Check if registry is accessible
curl 'https://skill.xfyun.cn/api/cli/v1/skills/search?q=test&limit=1'

# Use alternative registry
skillhub search test --registry https://skillhub.example.com
```

### Installation Directory Conflict

Check the active registry, target Agent, installation directory, source metadata, and local changes before replacing files. The following example targets Codex; use the actual Agent identifier for other targets.

```bash
skillhub list --agent codex --json
```

Compare `installDir` with the conflicting path and read that directory's `SKILL.md`. Use `--force` only after confirming the same installation source, making a backup, and explicitly accepting the loss of local changes. The option does not take over unmanaged, different-source, or disabled targets. If provenance is uncertain, choose another directory or scope; do not delete files or inventory records as a diagnostic step.

### Corrupted Inventory

```bash
# Rebuild inventory
skillhub doctor
```

## Local Development Verification

If you're developing SkillHub locally, you can verify the CLI like this:

```bash
# 1. Build CLI
cd cli
bun install
bun run build
bun link

# 2. Start local backend
cd ..
make dev-all

# 3. Configure CLI to connect to local service (Linux/macOS)
export SKILLHUB_REGISTRY=http://localhost:8080

# Windows PowerShell:
# $env:SKILLHUB_REGISTRY="http://localhost:8080"

# Windows CMD:
# set SKILLHUB_REGISTRY=http://localhost:8080

# 4. Test commands
skillhub search test
skillhub install example-skill --agent codex
skillhub list
```

## Related Links

- [SkillHub Homepage](https://skill.xfyun.cn)
- [GitHub Repository](https://github.com/iflytek/skillhub)
- [Issue Tracker](https://github.com/iflytek/skillhub/issues)

## License

Apache-2.0

Copyright 2026 iFlytek Co., Ltd.

## Install skills from a GitHub repository

Open **Open-source skills → Repositories**, expand **Install repository skills**, and copy its stable repository ID:

```bash
skillhub repository install 123456 --scope project --agent codex --registry https://skillhub.example.com
```

Requires the repository install-plan API. Public content can be read anonymously; a supplied Token requires `skill:read` and remains subject to account and namespace permissions. A single plan pins all currently accessible, published, downloadable member versions and fingerprints (up to 1000). Versions may come from different commits; a newer pending revision does not block an older usable publication. Excluded private or unpublished details and counts are not disclosed. The Web list is a preview; the CLI resolves a fresh plan when run.

SaaS and self-hosted registries may expose different GitHub features. Synced skills do not imply
repository batch support, and the CLI does not infer support from a hostname. An unavailable endpoint
gets a feature-availability hint; an untyped 404 explicitly leaves both a disabled feature and an
unavailable repository possible. HTML returned instead of a compatible plan gets a contract error.
These failures return nonzero without changing local skills, credentials or inventory. Authentication,
permission, network and structured repository-not-found errors retain their meaning. Ordinary skill
commands do not require this repository endpoint and can still operate on accessible published skills.

No batch `--force` is supported. Existing directories remain protected, successful members survive partial failures, and a partial failure returns a nonzero exit code with per-member results. This reuses individual installation records without creating a Suite or repository installation ledger. Complete snapshot ZIP downloads remain separate and require every pinned snapshot member to be available.

### Check and upgrade installed repository skills

```bash
skillhub repository check 123456 --registry https://skillhub.example.com --json
skillhub repository upgrade 123456 --agent codex --registry https://skillhub.example.com --check
skillhub repository upgrade 123456 --agent codex --registry https://skillhub.example.com
```

Checks are read-only. Use `--agent` and/or `--dir` to filter existing targets; `--scope` is installation-only. Compare exact versions and fingerprints rather than commit equality. Do not add new upstream members or delete local files for removed/unavailable members. Legacy metadata stays readable without a forced migration.

Local edits, other owners, disabled state, source checks, locks and inventory consistency remain protected. Safe targets can upgrade independently while protected targets remain unchanged. Any protected or failed item makes the overall result nonzero, with explicit per-target results. Runtime failure retains completed work and reports later unattempted targets. No batch `--force`, cross-directory atomicity, new repository installation ledger or server installation telemetry is introduced. Existing download quotas apply. The Web offers commands only and cannot know local installation or drift counts.
