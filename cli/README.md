# SkillHub CLI

SkillHub CLI is the official command-line tool for SkillHub, designed for searching, installing, managing, and publishing Agent skill packages.

## 📦 Installation

```bash
# Install globally via npm
npm install -g @astron-team/skillhub

# Or run directly with npx
npx @astron-team/skillhub@latest version

# Or install globally via Bun
bun add -g @astron-team/skillhub
```

## 🚀 Quick Start

```bash
# Log in interactively with OAuth Device Flow
skillhub login

# Search skills
skillhub search pdf

# Install skill to Agent directory
skillhub install pdf-parser --agent codex

# List installed skills
skillhub list

# Publish skill
skillhub publish ./my-skill --namespace myspace

# Synchronize a team workspace
skillhub sync pull --namespace myspace
```

## Presentation metadata for published packages

Add optional `metadata.short-description` (up to 200 characters) and `metadata.overview` (Markdown, up to 20,000 characters) to `SKILL.md` frontmatter. The former is the card/search summary; the latter is the detail-page overview. Neither changes the original required `description`, slug, or archive contents. If absent, summary uses `description` and the detail view uses README.md then SKILL.md. CLI publication reads these package keys directly; it does not need web-only multipart fields. Custom web values belong to version snapshots, not the package.

```yaml
---
name: my-skill
description: When to use this skill
metadata:
  short-description: Brief card text
  overview: "# Detailed overview"
---
```

## 🌐 Registry Configuration

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

For registries mounted below a URL prefix, include it in `--registry` (for example,
`https://skillhub.example.com/skillhub`). Installation and namespace synchronization
retain that prefix when following application-relative download URLs.

## 🔐 Authentication

`skillhub login` resolves a token from arguments, environment variables, or saved credentials for the selected registry, and starts OAuth Device Flow when none is resolved. A saved credential that the registry rejects with HTTP 401 does not dead-end the command: `login` reports it and continues with Device Flow, leaving the saved credential on disk until a new login succeeds. A token supplied by `--token` or `SKILLHUB_TOKEN` is never replaced automatically: the CLI names the rejected input and explains how to retry without it. HTTP 403 is a permission denial and does not start a new login. Token resolution priority for
explicit token-based login and all other authenticated commands is:

1. `--token <token>` command-line argument
2. `SKILLHUB_TOKEN` environment variable
3. Token stored in `~/.skillhub/credentials.json` (per registry)

### Login

```bash
# Interactive login: opens the registry's verification page and displays a user code
skillhub login

# Interactive login on a remote/headless terminal
skillhub login --no-open --registry https://skillhub.example.com

# Non-interactive login with an API token
skillhub login --token sk_xxx --registry https://skillhub.example.com
```

During interactive login, complete authentication in the browser and enter the displayed user code.
The CLI polls only until the server-provided expiry. It then validates the issued token, stores it in
`~/.skillhub/credentials.json`, and writes the registry to `~/.skillhub/config.json`. `--no-open`
suppresses automatic browser launch while retaining the verification URL and code in the terminal.

Token-based login remains available for CI and other non-interactive automation. In both modes,
credentials are persisted only after `whoami` succeeds.

Device-code and browser-callback login issue `skill:read + skill:submit`: download, upload,
validate and submit for review remain available. PRIVATE uploads are candidates, not publication.
Explicit private confirmation needs `skill:publish-private`; management needs `skill:manage`.
Account or namespace roles do not broaden token capabilities.

After logging in again, remote removal needs an explicitly authorized `skill:delete` token.
Create it using the Web Custom preset, then run `skillhub login --token <token>`.
The CLI reports this next step for missing-delete errors and never reissues a broader token.
Existing legacy tokens retain their stored scopes and route authorization until replaced.
`whoami` shows effective capabilities, legacy compatibility and expiration; older servers remain supported.

Both files are updated non-destructively: SkillHub CLI changes only its own `tokens` and `registry`
fields and preserves unknown fields written by other compatible tools. This allows tools that share
the `~/.skillhub` directory to keep independently named state in the same JSON documents.

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

## 🔍 Search

```bash
# Keyword search
skillhub search pdf

# Search with a one-off token
skillhub search pdf --token sk_xxx

# List all skills (empty query)
skillhub search "" --limit 50

# JSON output
skillhub search pdf --json
```

Output format: `namespace/slug  version  summary`

## 📥 Install Skills

The install coordinate accepts a bare slug or any of the equivalent namespace
forms below:

| Coordinate | Resolved namespace | Resolved slug |
|------------|--------------------|---------------|
| `my-skill` | `global` | `my-skill` |
| `team/my-skill` | `team` | `my-skill` |
| `@team/my-skill` | `team` | `my-skill` |
| `team--my-skill` | `team` | `my-skill` |

For a bare slug, `--namespace team` selects a non-global namespace. A
namespaced coordinate may be combined with the same `--namespace` value, but a
conflicting value is rejected instead of silently overriding the coordinate.

```bash
# Install to auto-detected Agent directory
skillhub install pdf-parser

# Equivalent namespaced coordinates
skillhub install team/my-skill
skillhub install @team/my-skill
skillhub install team--my-skill

# Choose install scope explicitly
skillhub install pdf-parser --scope user
skillhub install pdf-parser --scope project --agent codex

# Specify namespace for a bare slug (default: global)
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
  "source": "skillhub",
  "agent": "codex",
  "installedAt": "2026-04-28T06:00:00.000Z"
}
```

The CLI creates `.skillhub/metadata.json` after extracting a downloaded package. It is not part of
the published ZIP and is excluded when a managed directory is published again.

## ⬆️ Upgrade Installed Skills

`upgrade` only operates on explicitly selected, SkillHub-managed local installations. It never
installs a missing Skill and has no implicit upgrade-all mode.

```bash
# Preview without changing files
skillhub upgrade @global/skillhub-cli --check

# Upgrade one or a bounded list of installed Skills
skillhub upgrade @global/skillhub-cli
skillhub upgrade @team/code-review @team/java-guide

# Machine-readable plan
skillhub upgrade @team/code-review --check --json
```

The source identity is `registry + namespace + slug`. `--force` may replace local changes only when
that full identity matches the installation metadata; it never overwrites an unmanaged directory or
a Skill installed from another source.

Each selected installation position is upgraded independently. Filters can select one position;
unselected positions keep their own versions and content. Plans and results report one row per position.
The command also keeps the local files when the registry resolves to an older version.
If a multi-Skill run fails after an earlier upgrade commits, execution stops and reports each item
as `upgraded`, `failed`, or `not-attempted`; a committed upgrade is never rolled back implicitly.
New installations store absolute target paths. An older inventory entry with relative target paths
must be reinstalled before upgrade because its original working directory cannot be recovered safely.

`upgrade --check` (text and `--json`) also reports the version change of each planned row as
`change`: `major`, `minor`, `patch`, `prerelease`, `none`, `downgrade` or `unknown` (see below).

### Find Outdated Skills

`outdated` is read-only discovery. It checks every SkillHub-managed installation that `list` shows
for the effective registry, asks that registry for the current version, and prints the installations
with a newer version. It never downloads packages, writes files or upgrades anything; applying an
update stays an explicit `skillhub upgrade <coordinate>`.

```bash
# Installations with newer versions in the effective registry
skillhub outdated

# Narrow the check like list/upgrade
skillhub outdated --namespace team --agent codex
skillhub outdated --dir ./.agents/skills --registry https://skillhub.example.com

# Machine-readable report, e.g. for an Agent session hook
skillhub outdated --json
```

Example output:

```text
Outdated: 2 with newer versions, 0 unknown, 0 failed, 3 checked
patch      @team/code-review 1.0.0 -> 1.0.1  codex  /work/repo/.agents/skills/code-review [local changes: 1 file]
major      @global/example-skill 1.4.2 -> 2.0.0  claude  /home/user/.claude/skills/example-skill
Run "skillhub upgrade <coordinate> --check" to review an upgrade; nothing was changed.
```

`change` compares the installed and registry versions with semver:

| Value | Meaning |
|-------|---------|
| `major` / `minor` / `patch` | The first differing release component of a newer stable version |
| `prerelease` | The newer version is a prerelease, or only the prerelease part changed (for example `1.0.0-rc.1 -> 1.0.0`) |
| `unknown` | A version is not valid semver, or the registry version could not be resolved (`reason` explains) |

These labels describe version numbers, not a guarantee of compatibility. For example,
`0.1.0 -> 0.2.0` is `minor`. Registry-generated GitHub hash versions have no SemVer chronology;
different hash versions are reported as `unknown`, not ordered alphabetically. Use repository
checks for ordering backed by verified source version IDs.

Installations at the same or an older registry version are not listed. `localState` reuses the
local-change inspection of `upgrade`: `clean`, `modified` (files differ from the installed baseline;
`upgrade` would require `--force`) or `unverified` (no baseline, missing target, invalid metadata or
source conflict; `localReason` explains). A complete check exits `0` whether or not updates exist.
Version-order uncertainty alone is an `unknown` result, not a failed lookup. If any registry lookup
fails, the command still prints all available results, sets `ok: false`, reports `summary.failed`,
and exits nonzero using the first lookup error's existing CLI exit code. Error details are written
to stderr; stdout remains one JSON report. Usage errors keep the usual exit codes.

JSON shape (`--json`):

```json
{
  "ok": true,
  "registry": "https://skillhub.example.com",
  "summary": { "checked": 3, "outdated": 2, "unknown": 0, "failed": 0 },
  "items": [
    {
      "coordinate": "@team/code-review",
      "registry": "https://skillhub.example.com",
      "agent": "codex",
      "installDir": "/work/repo/.agents/skills/code-review",
      "currentVersion": "1.0.0",
      "remoteVersion": "1.0.1",
      "change": "patch",
      "localState": "modified",
      "changedFiles": ["/work/repo/.agents/skills/code-review:SKILL.md"]
    }
  ]
}
```

`items` has one row per installation position. `remoteVersion` is `null` when the registry could not
resolve the skill. `reason` and `localReason` are present only when they apply. `summary.checked`
counts the installation positions examined after filters; `outdated` and `unknown` count `items`.
`failed` counts positions whose remote lookup failed, and is a subset of `unknown`.

## 🔄 Namespace Workspaces

Use namespace synchronization to maintain explicitly selected skills from one team space. Every sync action requires
`--namespace`; `global` is not a valid sync target because it has no namespace membership.

```bash
# Interactively select new or updated skills in a TTY
skillhub sync pull --namespace team-a

# Non-interactive/CI pull: repeat --skill for every explicit target
skillhub sync pull --namespace team-a --skill code-review --skill java-guide

# Use an explicit workspace directory and target
skillhub sync pull --namespace team-a --skill code-review --dir ./.claude/skills

# Check without downloading
skillhub sync pull --namespace team-a --check

# Show local edits and remote updates
skillhub sync status --namespace team-a --json
skillhub sync diff --namespace team-a

# Remove only an explicitly selected, unchanged managed skill that no longer exists remotely
skillhub sync pull --namespace team-a --skill retired-guide --prune

# Validate and upload every local skill for review
skillhub sync push --all --namespace team-a --dry-run
skillhub sync push --all --namespace team-a --submit-review
```

The default workspace is `<cwd>/.agents/skills`. In an interactive TTY, pull presents a multi-select list; an empty
selection changes nothing. Outside a TTY, and always with `--json`, pull requires one or more repeatable
`--skill <slug>` options and never prompts. `sync pull --check` is the exception: it checks the entire namespace and
never writes local files. Pull never overwrites local changes unless `--force` is supplied, and `--force` applies only
to explicitly selected skills. Remote removals are reported as `orphaned` and are retained unless both `--prune` and
the matching `--skill` are supplied.

Sync compares both the published version and package fingerprint. An exact match is `up-to-date`,
while a newer version is `update-available` even when its content is unchanged. An older remote
version, an unorderable version pair, or changed remote content without a version bump is `blocked`.
`--force` cannot bypass these release-safety checks; verify the release and use an explicit
`skillhub install` when replacement is intentional.

Workspace push is non-overwriting: an existing namespace/slug/version is reported as a conflict, including versions that are still uploaded or pending review. Other skills in the same `--all` run continue processing.

Namespace sync writes `.skillhub/namespace-sync.json` in the workspace and per-skill `.skillhub/metadata.json` files. These files contain the registry coordinate, published version, aggregate fingerprint, and file hashes used by `status` and `diff`.

## Using installed skills and Suites

Installation downloads Skill files; it does not execute a task. Open the target project in your Agent, verify it discovers `SKILL.md`, configure the documented dependencies, and ask it to use the Skill. See the [Suite usage guide](../docs/skillhub/en/guide/skill-suites.md).

```bash
# Replace with a real Suite coordinate
skillhub suite install @team/example-suite --scope project --agent codex
skillhub suite check @team/example-suite
skillhub suite upgrade @team/example-suite --check
skillhub suite upgrade @team/example-suite
skillhub suite remove @team/example-suite
```

## 📋 Local Management

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
# A bare slug removes matching local installations across namespaces
skillhub remove pdf-parser

# A namespaced coordinate removes only that namespace
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

`--health` selects diagnosis instead of inventory maintenance. It reports the actual config,
credential and inventory paths, registry/credential sources, selected-registry connectivity and
registered installation findings. It never repairs files, creates inventory backups, locks an
installation, removes credentials or starts login. `--offline` makes no network requests and
marks connectivity and identity checks as skipped; it does not certify a saved token.

Online diagnosis makes one identity request to the selected registry with a five-second limit.
Redirects are refused, credentials and raw server error bodies are not printed, and no optional
GitHub/Suite or server-internal health endpoints are probed. A successful identity check does not
certify publish/delete permissions. Read access does not prove future write access.

Missing login, modified content and legacy missing integrity baselines are warnings, not automatic
repairs. Unsafe, unreadable or inconsistent installations are failures. Both enabled and disabled
installations retain their state. Only records for the selected registry are inspected.

Health JSON is one stdout object with `schemaVersion: 1`, `mode: "health"`, `ok`, `registry`,
`registrySource`, `credentialSource`, `paths`, `checks` and `exitCode`. Checks use `pass`, `warn`,
`fail` or `skipped`, with stable `id`/`code`, a safe message and an optional next step. Failed
health runs still print the complete JSON report to stdout without stderr chatter.

Exit 0 means no failed checks (warnings and skipped checks can remain). When failures coexist,
the priority is filesystem 4, authentication 2, network 3, then other HTTP/protocol failures 1.
Usage errors remain exit 5. Plain `doctor --json` retains its existing inventory schema; health-only
options `--offline`, `--registry` and `--token` require `--health` so they cannot trigger maintenance.

If a stored credential is rejected with HTTP 401, registry-scoped login starts sign-in again;
logout is not required. HTTP 403 calls for checking permissions, not replacing the credential. Argument/environment overrides are identified separately. Diagnosis does not start login or alter credentials. `whoami` remains the direct identity command and `outdated` remains
the installed-update check.

## 🚢 Publishing

```bash
# Publish directory (auto-packaged as zip)
skillhub publish ./my-skill --namespace myspace

# Publish existing zip file
skillhub publish ./my-skill.zip --namespace myspace

# Specify visibility
skillhub publish ./my-skill --namespace myspace --visibility private

# Record what changed in this version (shown in the skill's version list)
skillhub publish ./my-skill --namespace myspace --changelog "Fixed the parser, added a regression test"
```

Visibility options:
- `public` (default) — Visible to everyone
- `namespace-only` — Visible to namespace members only
- `private` — Visible to yourself only

After the server accepts a submission, the CLI displays the server's current status and the skill detail page URL.
Statuses such as `SCANNING` and `PENDING_REVIEW` are successful asynchronous submissions, not confirmation that the
skill is finally published. Check the Web page for the final publish or review state.

## ⬆️ Self-Update

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

## 🔧 Environment Variables

| Variable | Description | Priority |
|----------|-------------|----------|
| `SKILLHUB_REGISTRY` | Default registry URL | Lower than `--registry` parameter |
| `SKILLHUB_TOKEN` | API token | Lower than `--token` parameter, higher than stored token |

## 📂 Local File Structure

```
~/.skillhub/
├── config.json           # User configuration (registry, defaultAgent, etc.)
├── credentials.json      # API tokens (stored per registry, permissions 0600)
└── inventory.json        # Installed skills inventory
```

## 📖 Command Reference

| Command | Description |
|---------|-------------|
| `skillhub help [command]` | Display help information |
| `skillhub version [--json]`, `skillhub --version`, `skillhub -v` | Display CLI version |
| `skillhub login [--no-open] [--token <token>] [--registry <url>] [--json]` | Log in with OAuth Device Flow or an API token |
| `skillhub logout [--registry <url>] [--json]` | Remove token for specified registry |
| `skillhub whoami [--registry <url>] [--token <token>] [--json]` | Validate current token and display user information |
| `skillhub search [query] [--registry <url>] [--token <token>] [--limit <n>] [--json]` | Search published skills |
| `skillhub install <coordinate> [--scope <user\|project>] [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--registry <url>] [--token <token>] [--json]` | Install a skill |
| `skillhub upgrade <coordinate...> [--namespace <slug>] [--agent <profile>] [--dir <path>] [--registry <url>] [--token <token>] [--check] [--force] [--json]` | Upgrade explicitly selected installed skills |
| `skillhub outdated [--namespace <slug>] [--agent <profile>] [--dir <path>] [--registry <url>] [--token <token>] [--json]` | List installed skills with newer registry versions (read-only) |
| `skillhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]` | List installed skills |
| `skillhub remove <coordinate> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--registry <url>] [--token <token>] [--json]` | Remove a skill |
| `skillhub doctor [--json]` | Scan project metadata and merge into inventory, preserving entries outside the scan |
| `skillhub doctor --health [--offline] [--registry <url>] [--token <token>] [--json]` | Read-only health diagnosis; does not repair inventory or start login |
| `skillhub publish <path> [--namespace <slug>] [--visibility <v>] [--changelog <text>] [--registry <url>] [--token <token>] [--json]` | Publish a skill |
| `skillhub sync pull --namespace <slug> [--skill <slug>]... [options]` | Pull explicitly selected skills from a non-global namespace |
| `skillhub sync <status\|diff\|push> --namespace <slug> [options]` | Inspect or push a non-global namespace workspace |
| `skillhub suite <install\|check\|upgrade\|remove> <coordinate> [options]` | Manage a pinned Skill Suite installation |
| `skillhub repository <install\|check\|upgrade> <repository-id> [options]` | Manage GitHub repository skills on registries that expose a compatible installation plan |
| `skillhub update [--check] [--json]` | Check or execute CLI self-update |

### Choosing commands and preserving compatibility

- `update` updates the CLI executable; `upgrade` updates explicitly selected installed skills.
  `outdated` discovers possible updates in the effective Registry without installing anything;
  `upgrade --check` previews the selected operation. Neither is a CLI self-update.
- `repository` groups ordinary skills by their Registry repository ID. It does not clone arbitrary
  GitHub URLs or configure server-side GitHub synchronization. `repository check` and
  `repository upgrade --check` are compatible entry points to the same read-only plan; both remain supported.
- `suite` manages a versioned Suite and its member ownership. `suite check` checks the installed
  Suite's availability and member files; `suite upgrade --check` previews a remote Suite version's
  member changes. They are not interchangeable with repository or individual-skill operations.
- `sync` manages a non-global namespace authoring workspace, not GitHub ingestion. `status` lists
  workspace states; `diff` shows changed entries; `pull --check` previews pulling the namespace.
  `publish` uploads one local package, while `sync push` also supports workspace selection and review
  submission. Server policy decides whether an upload is published or requires review.
- `search` finds remote skills; `list` reads installed records; `doctor` scans local metadata and can
  write inventory. `remove` is local by default; `--remote` explicitly selects remote deletion.
  `login` stores a verified credential, `whoami` verifies the current identity, and `logout` removes
  that Registry's credential. `help` and `version` remain available offline.

Existing names, parameters, exit-code categories and compatibility entry points are retained.
No automatic command substitution or new aliases are introduced.

## 🔒 Security Notes

- Tokens are stored only in user directory `~/.skillhub/credentials.json`
- On Linux/macOS, credential file permissions are automatically set to `0600`
- Tokens are never written to any project-local files
- Remote delete operations require explicit confirmation or `--hard` parameter
- `remove` command validates path safety to prevent deletion of non-skill directories

## 🐛 Troubleshooting

### Authentication Failure

```bash
# Verify token validity
skillhub whoami

# Re-login interactively
skillhub login

# Or use a token for non-interactive automation
skillhub login --token sk_xxx
```

For structured registry failures, the CLI prints the server's public `msg` and
`requestId`. HTTP 403 without a public message falls back to `access denied`;
it is not automatically described as a missing token scope. Include the
request ID when asking a registry operator to investigate.

### Network Error

```bash
# Check if registry is accessible
curl 'https://skill.xfyun.cn/api/cli/v1/skills/search?q=test&limit=1'

# Use alternative registry
skillhub search test --registry https://skillhub.example.com
```

### Installation Directory Conflict

```bash
# Use --force to overwrite
skillhub install pdf-parser --force # same SkillHub source only

# Or remove first then install
skillhub remove pdf-parser
skillhub install pdf-parser
```

`--force` does not bypass source ownership. Move or explicitly remove an unmanaged or different-source
directory before installing another Skill with the same visible slug.

### Corrupted Inventory

```bash
# Rebuild inventory
skillhub doctor
```

## 📚 Documentation

- [SkillHub Homepage](https://skill.xfyun.cn)
- [GitHub Repository](https://github.com/iflytek/skillhub)
- [CLI Documentation](https://github.com/iflytek/skillhub/blob/main/docs/skillhub/en/guide/cli.md)
- [Issue Tracker](https://github.com/iflytek/skillhub/issues)

## 📄 License

Apache-2.0

Copyright 2026 iFlytek Co., Ltd.

## Packaged task examples

Authors can include `.skillhub/examples.json` in a skill package. The file stays at the same
path after publishing and installation. It is versioned skill content, including when its
JSON does not conform to the examples protocol; local edits are protected by existing
upgrade/sync integrity checks. No separate Web configuration is required.

```json
{
  "schemaVersion": 1,
  "defaultLocale": "zh-CN",
  "examples": {
    "zh-CN": [{ "id": "summary", "title": "整理纪要", "prompt": "请用 {{skill}} 整理会议记录。" }],
    "en": [{ "id": "summary", "title": "Summarize notes", "prompt": "Use {{skill}} to summarize meeting notes." }]
  }
}
```

The native DSH plugin recognizes this bounded UTF-8 declaration and offers editable task
prompts. Unsupported declarations remain ordinary package files. See the
[protocol and limits](../openspec/changes/add-dsh-native-plugin/skill-examples-design.md).
Only `.skillhub/metadata.json` is an installer-owned record: directory publishing and sync
push omit it, and installation rejects a package that supplies it (including path aliases).
Other authored `.skillhub` files are retained and fingerprinted.

Older consumers that ignore the whole `.skillhub` directory do not support packages with
these files. Publish/install them only with lifecycle-capable CLI/plugin builds; old package
bytes and published fingerprints must remain unchanged. This is not transparent backward
compatibility for existing binaries.

Directory and ZIP publishing (`publish`, including `--dry-run`, and `sync push`) inspect the
actual outgoing archive for task examples. Human output reports the recognition summary;
`--json` keeps it in `examplesStatus`, `examplesCount` and `warnings` fields. Counts include
all declared languages. An unsupported declaration generates a nonblocking warning and is
still uploaded unchanged; an unreadable archive produces an inspection warning and remains
subject to the server's package validation.

### Installation inventory compatibility

Version `0.3.0` retains installation inventory schema version 2. Each installation directory owns its
version, fingerprint, timestamp and ownership, so a user installation and a project installation
of the same skill can remain on different versions. `list --json` reports the actual version for
each target. `upgrade --check --json` and upgrade results contain one row per selected installation
position; the same coordinate can therefore occur more than once with different versions and
`targets`. `--dir` or `--agent` selects positions without updating unselected siblings.
Direct install/upgrade/remove and sync pruning refuse Suite-owned or shared positions, even
with `--force`. Manage those positions through Suite commands; unrelated direct installations
remain independently manageable.

Shared inventory remains strict v2 without a persisted `installationState`; activation is derived from trusted `SKILL.md` / `SKILL.md.disabled` entries. A coordinated CLI upgrade is not required. Published CLI `0.2.0` must retain normal queries and ordinary-target management; default update, sync and Suite paths must not unintentionally enable disabled targets. Historical explicit force install, upgrade and sync retain their overwrite semantics and may restore the standard entry; explicit remove retains deletion semantics. New readers must reflect these actual file changes.

Reading v1/v2 is non-mutating. Unpublished v3 inventories require validated, locked conversion to v2 with original-byte backup; never restore stale inventory over newer records. Ordinary DSH journals remain compatible; toggle journals must not block unrelated historical operations. Compatibility fixes and real historical-package acceptance are in progress; release remains blocked until they pass.

This CLI does not add enable/disable commands. Use DSH for explicit toggles and disabled updates;
CLI upgrade refuses disabled targets even with `--force`. Namespace sync retains disabled
installations without update/pruning, Suite cannot adopt them, and doctor preserves trusted
records without claiming loose `.disabled` files. Standard publishing requires `SKILL.md`;
restore the entry through the compatible manager before publishing a disabled installation.

## GitHub repository batch installation

Open a repository on the registry's **Open-source skills → Repositories** page and expand **Install repository skills**. Copy its stable repository ID from the command:

```bash
skillhub repository install 123456 --scope project --agent codex --registry https://skillhub.example.com
# Or select an explicit installation root (cannot combine with --scope/--agent):
skillhub repository install 123456 --dir ./skills --json
```

This requires a registry with the repository install-plan endpoint. Public repositories support anonymous reads; a supplied Token must have `skill:read`. Use `skillhub login --registry ...` for restricted content. Registry and credential precedence are the same as individual installation.

SaaS and self-hosted registries can expose different GitHub synchronization features. The CLI does
not infer repository support from the hostname or from the presence of GitHub-synced skills.
Only this optional command requires the compatible repository plan. An unavailable endpoint reports
that repository batch operations are not available; an untyped 404 explains that the feature may not
be enabled **or** the repository may be unavailable. A successful HTML fallback reports an incompatible
plan. These errors are nonzero and do not write local state. Authentication, permission, network and
structured repository-not-found errors keep their original meaning. Ordinary skill commands remain
independent; use `install <coordinate>` for individually published skills where the Registry allows it.

The CLI resolves all currently accessible, published, download-ready members once (maximum 1000), then pins their exact versions and content fingerprints for that invocation. Members may come from different upstream commits. A newer pending revision does not block an older published version. Hidden, withdrawn, inaccessible or not-yet-published members are not included, and their private details/counts are not disclosed. The Web list is a preview; running the command obtains a fresh plan.

Each member uses normal Skill installation locks, fingerprint checks, ownership protection and inventory. This creates neither a Suite nor a repository installation ledger. Existing directories are never overwritten by this batch command; no batch `--force` is accepted. Successful members remain installed if another fails. The output lists every attempted member, counts installed/skipped/failed, and returns nonzero on partial failure or no usable plan. In `--json` mode stdout is one result object; a nonzero command additionally writes its standard error object to stderr. Excluded private/unpublished members are not counted as successful or as a disclosed skip count.

A complete historical snapshot ZIP is a separate download: every pinned snapshot member must be available. Batch installation does not promise that all members belong to one commit. Use the repository-scoped check and upgrade commands below for existing installations.

If an inventory commit is indeterminate or rollback is incomplete, the batch stops further writes and reports a recovery state with retained target/staging/backup paths and a safe next step. It does not serialize arbitrary error details or credentials.

## Check and upgrade installed repository skills

```bash
skillhub repository check 123456 --registry https://skillhub.example.com --json
skillhub repository upgrade 123456 --agent codex --registry https://skillhub.example.com --check
skillhub repository upgrade 123456 --agent codex --registry https://skillhub.example.com
```

`check` and `upgrade --check` are read-only. `--agent` and/or `--dir` filter existing targets; `--scope` applies only to installation. New upstream members are not automatically installed. Local files for removed, hidden or unavailable members are not deleted. A per-Skill provenance hint lets the CLI explain that a previously installed member no longer appears in the accessible published plan without guessing why it is hidden. Older metadata without this hint stays readable and can match verified current members.

Checks compare exact versions and content fingerprints, not commit equality. Each invocation fetches the repository plan once and pins all selected releases. Existing source, ownership, local-change, disabled-state, lock and inventory protections remain in effect. No repository batch `--force` is offered. Protected targets remain unchanged while safe targets may upgrade; the overall result is nonzero if anything is protected or fails. A runtime failure preserves completed work and reports subsequent unattempted targets. The CLI does not claim a single transaction across every skill directory.

The Web page offers these commands but cannot inspect installed local files or display an installed drift count. These operations create no Suite, repository installation ledger, server installation telemetry or lockstep commit policy. Existing registry download quotas still apply.

GitHub sync release strings contain content hashes and ingestion IDs rather than sortable upstream SemVer. Repository checks use the validated same-source metadata version ID as the creation-order baseline. Exact matching versions/fingerprints are unchanged; older metadata lacking a reliable ordering ID is protected when a different release must be compared. No hash-lexical fallback or forced migration is used. Disabled targets requiring an update are protected during planning; enabled independent targets can proceed.
