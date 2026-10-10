# DeepSeek Harness Integration Guide

This guide explains how to install SkillHub packages into
[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness), whose CLI is `dsh`.

## Verified scope

The directory behavior described here was verified on 2026-09-20 against
`@deepseek-ai/dsh-skill-filesystem` at DeepSeek Harness commit
[`ddefc45`](https://github.com/deepseek-ai/deepseek-harness/tree/ddefc45fbc7f8e46dd73185e68295696d1297887/packages/skill/skill-filesystem).
That release is still a `0.1.x` developer preview; recheck the roots after upgrading dsh.

## Install into native dsh roots

dsh uses `<repository>/.dsh/skills` for project skills and `$DSH_HOME/skills`
(default `~/.dsh/skills`) for user skills. With the default home:

```bash
# User scope: ~/.dsh/skills/<skill-slug>/
skillhub install my-skill --agent dsh --scope user

# Project scope: run from the repository root
cd "$(git rev-parse --show-toplevel)"
skillhub install my-skill --agent dsh --scope project
```

Explicit `--agent dsh` without `--scope` defaults to the user root. Verify the local
SkillHub record with:

```bash
skillhub list --agent dsh
```

dsh watches configured skill roots, so added, renamed, or removed skills appear after
the next catalog refresh without restarting the process.

## Use the shared `.agents/skills` roots

dsh also scans project `.agents/skills` and user `~/.agents/skills`. This works with an
older SkillHub CLI that lacks the profile and lets multiple agents share one installation:

```bash
skillhub install my-skill --dir "$HOME/.agents/skills"
skillhub install my-skill --dir "$(git rev-parse --show-toplevel)/.agents/skills"
```

## Custom DSH_HOME

The SkillHub profile maps the default `~/.dsh/skills` root. If dsh uses a custom
`DSH_HOME`, pass its actual path explicitly:

```bash
skillhub install my-skill --dir "${DSH_HOME:-$HOME/.dsh}/skills"
```

## Project-root difference

dsh finds the nearest `.git` ancestor and treats it as the project root. SkillHub CLI
profiles use the current working directory. Running `--scope project --agent dsh` from a
repository subdirectory would therefore write a `.dsh/skills` directory that dsh does not
treat as the project root. Change to the path returned by `git rev-parse --show-toplevel`
before project-scoped installation.

## Compatibility boundary

- SkillHub writes `<skill-slug>/SKILL.md`, matching dsh's one-level bundle discovery.
- dsh also accepts a flat `<name>.md`; SkillHub packages still require root-level `SKILL.md`.
- `SKILL.md` needs a valid kebab-case `name` and a non-empty `description` frontmatter field.
- Format compatibility does not guarantee runtime compatibility. Agent-specific tools,
  commands, MCP servers, environment variables, and operating-system requirements still
  need separate validation.
- Review package contents and the SkillHub security report before installation. Never put a
  registry token in a skill package.

## Troubleshooting

If dsh does not discover an installed skill:

1. Run `skillhub list` and verify the recorded directory and status.
2. Confirm the layout is `<skill-root>/<skill-slug>/SKILL.md` without another nesting level.
3. Check the `name` and `description` frontmatter in `SKILL.md`.
4. For project scope, confirm the directory is under the nearest `.git` ancestor.
5. When using `DSH_HOME` or `DSH_AGENTS_HOME`, confirm installation used the actual configured root.
