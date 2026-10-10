# Hermes Agent Integration Guide

This guide explains how to install skills from SkillHub into [NousResearch Hermes Agent](https://github.com/NousResearch/hermes-agent), then discover, load, update, and remove those skills in Hermes.

“Hermes” in this guide means `NousResearch/hermes-agent`; it does not cover other projects with the same name.

## Validated scope

| Component | Validated version | Notes |
|-----------|-------------------|-------|
| SkillHub Server | `v0.2.13` | Public or self-hosted registry |
| SkillHub CLI | `0.1.8` | npm package `@astron-team/skillhub` |
| Hermes Agent | `0.18.2` | Upstream tag [`v2026.7.7.2`](https://github.com/NousResearch/hermes-agent/tree/v2026.7.7.2) |

Validation date: 2026-07-17.

Hermes 0.18.2 uses an [Agent Skills](https://agentskills.io/)-compatible `SKILL.md` format and recursively scans `$HERMES_HOME/skills/`. SkillHub CLI can extract a complete skill package into any explicit `--dir` target. The current integration therefore needs no format conversion, Hermes-specific CLI profile, or server adapter:

```text
SkillHub registry
  -> skillhub install --dir <Hermes skills directory>
  -> <Hermes skills directory>/<skill-slug>/SKILL.md
  -> Hermes discovers and loads the skill on demand
```

> Hermes 0.18.2 has no native SkillHub registry source. This guide uses SkillHub CLI for search, download, and local installation, while Hermes handles discovery and execution.

## Prerequisites

1. Install and initialize Hermes Agent.
2. Install SkillHub CLI:

```bash
npm install -g @astron-team/skillhub

skillhub version
hermes version
```

3. Ensure the skill package has a valid root `SKILL.md` with at least `name` and `description` frontmatter.

The examples below use Bash/zsh. On Windows, use the same directory structure, replace the default Hermes home with `$HOME\.hermes`, and set variables using PowerShell syntax.

## Quick start

### 1. Configure the SkillHub registry

Set the public or self-hosted SkillHub URL:

```bash
export SKILLHUB_REGISTRY=https://skillhub.your-company.com
```

You can skip login for public skills that allow anonymous downloads. For team namespaces, restricted skills, or private deployments, save an API token first:

```bash
skillhub login \
  --registry "$SKILLHUB_REGISTRY" \
  --token YOUR_API_TOKEN

skillhub whoami --registry "$SKILLHUB_REGISTRY"
```

Use placeholder tokens in examples. Never write a real token into `SKILL.md`, scripts, or version control.

### 2. Search for a skill

```bash
skillhub search "pdf" --registry "$SKILLHUB_REGISTRY"
```

Record the namespace, slug, and required version. The following examples use `my-team/my-skill`:

```bash
export SKILLHUB_NAMESPACE=my-team
export SKILLHUB_SKILL=my-skill
```

### 3. Install into the primary Hermes skills directory

Set the home of the active Hermes profile. The default profile normally uses `~/.hermes`; if you use a custom `HERMES_HOME` or a named profile, point it at the actual profile directory:

```bash
export HERMES_HOME="${HERMES_HOME:-$HOME/.hermes}"
export HERMES_SKILLHUB_DIR="$HERMES_HOME/skills/skillhub/$SKILLHUB_NAMESPACE"
```

Install the skill:

```bash
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY"
```

SkillHub CLI preserves `SKILL.md`, `references/`, `scripts/`, `templates/`, `assets/`, and other package files. It also writes `.skillhub/metadata.json` to record the installation source. The resulting layout looks like this:

```text
$HERMES_HOME/skills/
└── skillhub/
    └── my-team/
        └── my-skill/
            ├── SKILL.md
            ├── references/          # optional
            ├── scripts/             # optional
            └── .skillhub/
                └── metadata.json
```

Separating target directories by namespace reduces filesystem collisions between skills with the same slug. Hermes recursively scans these levels.

### 4. Verify and load the skill in Hermes

First, confirm that Hermes discovers the skill:

```bash
hermes skills list --source local --enabled-only
```

Then start Hermes and invoke the slash command normalized from the skill `name`:

```bash
hermes
```

```text
/my-skill
```

You can also ask Hermes in natural language to use the skill. Hermes lists the raw `SKILL.md` frontmatter `name`, but its slash command lowercases that name, replaces spaces and underscores with hyphens, removes other characters outside `a-z0-9-`, and collapses repeated hyphens. For example, `PDF_Tools` becomes `/pdf-tools`. The command may therefore differ from the SkillHub slug.

If a running session does not immediately show a new skill, run `/reload-skills` or restart the session.

## Update a skill

SkillHub CLI 0.1.8 overwrites a local skill by repeating the install command with `--force`. Omitting `--version` resolves the latest published version; you can also pin one explicitly:

```bash
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY" \
  --force

# Pinned version example
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --version 1.2.0 \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY" \
  --force
```

Review the new version before overwriting because `--force` replaces the existing skill directory. Afterward, run:

```bash
skillhub list \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY"

hermes skills list --source local --enabled-only
```

> `skillhub update` updates SkillHub CLI itself; it does not update installed skills. Refresh an installed skill with `skillhub install ... --force`.

## Remove a skill

First, list every installation from the same registry and confirm that there are no other same-slug skills you need to keep:

```bash
skillhub list \
  --registry "$SKILLHUB_REGISTRY"
```

Then remove the local installation:

```bash
skillhub remove "$SKILLHUB_SKILL" \
  --registry "$SKILLHUB_REGISTRY"
```

SkillHub CLI deletes both the skill directory and the local inventory record. Local `remove` in this version matches only registry and slug; it does not filter by namespace or directory. Every same-slug target from that registry, across all namespaces and installation directories, is removed. If the unfiltered `skillhub list` shows a match you need to keep, do not run the command; namespace- or directory-scoped removal requires a future CLI capability.

After removal, run `/reload-skills`, restart the Hermes session, or confirm that the skill is gone with:

```bash
hermes skills list --source local --enabled-only
```

## Optional: use a shared external skills directory

When several agents share `~/.agents/skills`, install SkillHub skills into that shared tree instead of the primary Hermes directory:

```bash
export SHARED_SKILLHUB_DIR="$HOME/.agents/skills/skillhub/$SKILLHUB_NAMESPACE"

skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$SHARED_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY"
```

Merge the shared root into `$HERMES_HOME/config.yaml` without replacing existing `skills` settings:

```yaml
skills:
  external_dirs:
    - ~/.agents/skills
```

Hermes lists and loads external skills alongside local skills. Do not rely on local shadowing: Hermes 0.18.2 refuses ambiguous `skill_view` matches across the local skills directory and `external_dirs`. Rename or remove a colliding copy instead.

> `external_dirs` is not a read-only boundary. If the Hermes process can write to an external directory, Hermes skill-management tools can modify its files. Use filesystem permissions or an isolated Hermes profile when shared skills must remain read-only.

## Compatibility and security boundaries

- **Format compatibility is not complete runtime compatibility.** Hermes can read `SKILL.md` and supporting files, but agent-specific tools, MCP servers, commands, environment variables, and platform capabilities referenced by a skill still need individual verification.
- **Hermes treats this path as local.** A skill copied by SkillHub CLI does not run through the Hermes Skills Hub community-install scanner. Review the SkillHub security report and the skill contents before installation, and use Hermes terminal isolation where appropriate.
- **Keep multi-file packages intact.** Do not replace SkillHub CLI with the Hermes 0.18.2 direct-URL source for multi-file skills. That release guarantees a single `SKILL.md` for URL installs, whereas SkillHub CLI extracts the complete package.
- **Avoid name collisions.** Namespace-separated filesystem paths do not resolve slash-command collisions. Keep normalized command names unique within one Hermes profile. For example, `PDF Tools` and `pdf_tools` both become `/pdf-tools`.
- **Protect credentials.** A registry token is only for SkillHub access and does not belong in a skill package. Skills that need runtime secrets should use Hermes environment-variable and security settings.

## Troubleshooting

### The new skill is missing from the Hermes list

Check these items in order:

1. The current session uses the same `HERMES_HOME` used during installation.
2. The final path contains `<skill-directory>/SKILL.md`.
3. `SKILL.md` contains valid `name` and `description` fields.
4. `platforms` or other frontmatter does not exclude the current operating system.
5. The skill appears after `/reload-skills` or in a new session.

```bash
skillhub list --dir "$HERMES_SKILLHUB_DIR" --registry "$SKILLHUB_REGISTRY"
hermes skills list --source local
```

### Installation reports `skill already installed`

Existing directories are not overwritten by default. Review the target version, then add `--force`:

```bash
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY" \
  --force
```

### The CLI reports `registry unreachable` or a download failure

- Confirm that `SKILLHUB_REGISTRY` is the SkillHub root URL.
- Run `skillhub search` against the same registry to distinguish registry reachability from a download failure.
- Check proxy, DNS, certificate, and self-hosted service status.
- Retry a transient network error only after confirming the service is healthy; do not bypass certificate failures by disabling TLS verification.

### The skill is listed but fails during execution

Check tool names, shell commands, script runtimes, packages, MCP servers, environment variables, and operating-system restrictions referenced by that skill. Those are skill-specific runtime compatibility concerns, not failures of `SKILL.md` discovery.

### Can `hermes skills install` consume a SkillHub coordinate directly?

Hermes 0.18.2 has no SkillHub registry source and cannot resolve a SkillHub namespace/slug directly. Use `skillhub install --dir ...` as shown in this guide. Native search, installation, updates, and security scanning inside Hermes would require a separately designed Hermes source adapter with its own protocol and acceptance scope.

## Regression checks after upgrades

After upgrading SkillHub CLI or Hermes, verify at least the following:

1. `skillhub install --dir` still creates `<slug>/SKILL.md` and preserves support files.
2. `hermes skills list --source local --enabled-only` discovers the skill.
3. `/skill-name` loads `SKILL.md` and exposes support-file paths; then read one referenced file with `skill_view(name, file_path)` or exercise the script/asset the skill actually uses.
4. `skillhub install --force` overwrites the skill while keeping a healthy inventory.
5. Hermes no longer discovers the skill after `skillhub remove`.

Upstream reference: [Hermes Skills System at v0.18.2](https://github.com/NousResearch/hermes-agent/blob/v2026.7.7.2/website/docs/user-guide/features/skills.md).
