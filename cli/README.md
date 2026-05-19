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

# List all skills (empty query)
skillhub search "" --limit 50

# JSON output
skillhub search pdf --json
```

Output format: `namespace/slug  version  summary`

## 📥 Install Skills

```bash
# Install to auto-detected Agent directory
skillhub install pdf-parser

# Specify namespace (default: global)
skillhub install pdf-parser --namespace myspace

# Specify version
skillhub install pdf-parser --version 1.2.0

# Install to specific Agent
skillhub install pdf-parser --agent codex

# Install to multiple Agents
skillhub install pdf-parser --agent codex --agent claude-code

# Install to custom directory
skillhub install pdf-parser --dir ~/.claude/skills

# Force overwrite existing installation
skillhub install pdf-parser --force
```

### Install Target Resolution

The CLI determines the installation location using the following logic:

1. If `--dir` is specified: Install to that directory, agent marked as `custom`
2. If `--agent` is specified: Install to the corresponding Agent's skills directory
3. If neither is specified: Auto-scan current directory to detect existing Agent config directories
   - 1 Agent detected → Install directly
   - Multiple Agents detected → Interactive selection (TTY mode) or error (non-interactive mode)
   - No Agent detected → Fallback to `<cwd>/.agents/skills/`

> `--dir` and `--agent` cannot be used together.

### Install Paths

Each Agent has both project-level and user-level skills directories:

| Agent | Project-level Path | User-level Path |
|-------|-------------------|-----------------|
| `claude-code` | `<project>/.claude/skills/` | `~/.claude/skills/` |
| `codex` | `<project>/.codex/skills/` | `~/.codex/skills/` |
| `cursor` | `<project>/.cursor/skills/` | `~/.cursor/skills/` |
| `github-copilot` | `<project>/.github-copilot/skills/` | `~/.github-copilot/skills/` |
| `gemini-cli` | `<project>/.gemini-cli/skills/` | `~/.gemini-cli/skills/` |
| `windsurf` | `<project>/.windsurf/skills/` | `~/.windsurf/skills/` |
| `kiro-cli` | `<project>/.kiro-cli/skills/` | `~/.kiro-cli/skills/` |
| `roo` | `<project>/.roo/skills/` | `~/.roo/skills/` |
| `trae` | `<project>/.trae/skills/` | `~/.trae/skills/` |
| `trae-cn` | `<project>/.trae-cn/skills/` | `~/.trae-cn/skills/` |
| `openhands` | `<project>/.openhands/skills/` | `~/.openhands/skills/` |
| `openclaw` | `<project>/.openclaw/skills/` | `~/.openclaw/skills/` |
| `opencode` | `<project>/.opencode/skills/` | `~/.opencode/skills/` |
| `kilo` | `<project>/.kilo/skills/` | `~/.kilo/skills/` |

For Agents not in the list, use `--dir` to specify the installation path.

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
  "registry": "https://skill.xfyun.cn",
  "namespace": "global",
  "slug": "pdf-parser",
  "version": "1.0.0",
  "agent": "codex",
  "installedAt": "2026-04-28T06:00:00.000Z"
}
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
# Remove all local installation targets
skillhub remove pdf-parser

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
| `skillhub search <query> [--registry <url>] [--limit <n>] [--json]` | Search published skills |
| `skillhub install <slug> [--namespace <slug>] [--version <v>] [--agent <profile>] [--dir <path>] [--force] [--registry <url>] [--token <token>] [--json]` | Install a skill |
| `skillhub list [--agent <profile>] [--dir <path>] [--registry <url>] [--json]` | List installed skills |
| `skillhub remove <slug> [--agent <profile>] [--all] [--remote] [--hard] [--namespace <slug>] [--registry <url>] [--token <token>] [--json]` | Remove a skill |
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
skillhub install pdf-parser --force

# Or remove first then install
skillhub remove pdf-parser
skillhub install pdf-parser
```

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
