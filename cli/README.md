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
# Login
skillhub login --token sk_xxx

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

## 🔐 Authentication

Token resolution priority:

1. `--token <token>` command-line argument
2. `SKILLHUB_TOKEN` environment variable
3. Token stored in `~/.skillhub/credentials.json` (per registry)

### Login

```bash
# Login with API token
skillhub login --token sk_xxx

# Login to specific registry
skillhub login --token sk_xxx --registry https://skillhub.example.com
```

`login` validates the token, stores it in `~/.skillhub/credentials.json`, and writes the registry to `~/.skillhub/config.json`.

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

Each Agent has both project-level and user-level skills directories. Use `--scope user|project` to control which one is used.

| Agent | Project-level Path | User-level Path |
|-------|-------------------|-----------------|
| `claude-code` | `<project>/.claude/skills/` | `~/.claude/skills/` |
| `codex` | `<project>/.codex/skills/` | `~/.codex/skills/` |
| `cursor` | `<project>/.cursor/skills/` | `~/.cursor/skills/` |
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
| _fallback_ | `<project>/.agents/skills/` | `~/.agents/skills/` |

For a custom path or an unsupported Agent directory, use `--dir` to specify the installation path. In interactive user scope, the `generic` target is offered alongside detected Agent targets. When `--scope user|project` finds no matching agent directory, the CLI falls back to the `_fallback_` row above.

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
skillhub upgrade @global/skillhub-registry --check

# Upgrade one or a bounded list of installed Skills
skillhub upgrade @global/skillhub-registry
skillhub upgrade @team/code-review @team/java-guide

# Machine-readable plan
skillhub upgrade @team/code-review --check --json
```

The source identity is `registry + namespace + slug`. `--force` may replace local changes only when
that full identity matches the installation metadata; it never overwrites an unmanaged directory or
a Skill installed from another source.

All targets in one inventory entry are upgraded together. A filter that selects only part of that
entry is rejected because the current inventory format stores one shared version for all targets.
The command also keeps the local files when the registry resolves to an older version.
If a multi-Skill run fails after an earlier upgrade commits, execution stops and reports each item
as `upgraded`, `failed`, or `not-attempted`; a committed upgrade is never rolled back implicitly.
New installations store absolute target paths. An older inventory entry with relative target paths
must be reinstalled before upgrade because its original working directory cannot be recovered safely.

## 🔄 Namespace Workspaces

Use namespace synchronization when an Agent workspace should maintain all installable skills from one team space.

```bash
# Pull new and updated skills into ./.agents/skills
skillhub sync pull --namespace team-a

# Use an explicit workspace directory
skillhub sync pull --namespace team-a --dir ./.claude/skills

# Check without downloading
skillhub sync pull --namespace team-a --check

# Show local edits and remote updates
skillhub sync status --namespace team-a --json
skillhub sync diff --namespace team-a

# Remove only unchanged SkillHub-managed skills that no longer exist remotely
skillhub sync pull --namespace team-a --prune

# Validate and upload every local skill for review
skillhub sync push --all --namespace team-a --dry-run
skillhub sync push --all --namespace team-a --submit-review
```

The default workspace is `<cwd>/.agents/skills`. Pull never overwrites local changes unless `--force` is supplied. Remote removals are reported as `orphaned` and are retained unless `--prune` is supplied. Both destructive cases still require explicit flags.

Sync compares both the published version and package fingerprint. An exact match is `up-to-date`,
while a newer version is `update-available` even when its content is unchanged. An older remote
version, an unorderable version pair, or changed remote content without a version bump is `blocked`.
`--force` cannot bypass these release-safety checks; verify the release and use an explicit
`skillhub install` when replacement is intentional.

Workspace push is non-overwriting: an existing namespace/slug/version is reported as a conflict, including versions that are still uploaded or pending review. Other skills in the same `--all` run continue processing.

Namespace sync writes `.skillhub/namespace-sync.json` in the workspace and per-skill `.skillhub/metadata.json` files. These files contain the registry coordinate, published version, aggregate fingerprint, and file hashes used by `status` and `diff`.

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

If the same skill has version conflicts across different targets, that skill will be skipped and reported.

## 🚢 Publishing

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

After successful publication, the skill detail page URL will be displayed.

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
| `skillhub version [--json]` | Display CLI version |
| `skillhub login --token <token> [--registry <url>] [--json]` | Save token and registry configuration |
| `skillhub logout [--registry <url>] [--json]` | Remove token for specified registry |
| `skillhub whoami [--registry <url>] [--token <token>] [--json]` | Validate current token and display user information |
| `skillhub search <query> [--registry <url>] [--token <token>] [--limit <n>] [--json]` | Search published skills |
| `skillhub install <coordinate> [--scope <user\|project>] [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--registry <url>] [--token <token>] [--json]` | Install a skill |
| `skillhub upgrade <coordinate...> [--namespace <slug>] [--agent <profile>] [--dir <path>] [--registry <url>] [--check] [--force] [--json]` | Upgrade explicitly selected installed skills |
| `skillhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]` | List installed skills |
| `skillhub remove <coordinate> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--registry <url>] [--token <token>] [--json]` | Remove a skill |
| `skillhub doctor [--json]` | Scan project directory and rebuild local inventory |
| `skillhub publish <path> [--namespace <slug>] [--visibility <v>] [--registry <url>] [--token <token>] [--json]` | Publish a skill |
| `skillhub update [--check] [--json]` | Check or execute CLI self-update |

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

# Re-login
skillhub login --token sk_xxx
```

For structured registry failures, the CLI prints the server's public `msg` and
`requestId`. HTTP 403 without a public message falls back to `access denied`;
it is not automatically described as a missing token scope. Include the
request ID when asking a registry operator to investigate.

### Network Error

```bash
# Check if registry is accessible
curl https://skill.xfyun.cn/api/cli/v1/skills/search?q=test&limit=1

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
