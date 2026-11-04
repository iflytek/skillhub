# Quickstart: install and use a skill

This page starts from an existing accessible registry and covers installation, Agent discovery and a first-use check for one skill. It does not cover registry deployment or skill publication. The endpoint is an Agent reading the skill and completing a concrete task, rather than only a successful CLI installation message.

![Sequence for selecting a registry, installing files, checking discovery and assessing execution](/diagrams/first-install-flow-en.svg)

Figure: each stage has its own completion criterion. A later failure should be diagnosed at that stage instead of reinstalling everything.

## Prerequisites

Run commands in a terminal and first change to the project root that the target Agent will open. Prepare an accessible registry and an Agent supporting skill directories, such as Codex or Claude Code.

| Item | Requirement or value | Check and activation |
| --- | --- | --- |
| Node.js | 18 or newer, with npm available | Run `node --version` and `npm --version`; reopen the terminal after changing the runtime environment |
| Registry | Built-in address: `https://skill.xfyun.cn` | Priority: command argument, `SKILLHUB_REGISTRY`, local configuration, built-in address; environment changes apply to the current terminal |
| Installation scope | `project` or `user` | This page selects project scope explicitly; user scope is under the home directory and does not imply automatic discovery by every Agent |
| Target Agent | This page uses `codex` | Use `claude-code` for Claude Code; the selection must match the Agent opening the project |

`skillhub.example.com` and `@team/example-skill` are placeholders, not guaranteed resources. Replace them with the actual instance address and a coordinate returned by search. Runtime requirements and options are defined by the [CLI manifest](https://github.com/iflytek/skillhub/blob/main/cli/package.json) and [command definitions](https://github.com/iflytek/skillhub/blob/main/cli/src/index.ts).

## 1. Install the CLI and select a registry

```bash
npm install -g @astron-team/skillhub
skillhub version
```

The version command should display `SkillHub CLI` followed by the installed version. If the command is unavailable, check that the npm global executable directory is on the current terminal's `PATH` before attempting skill installation.

The default instance needs no address setting. For self-hosting, set the actual address in the current terminal and retain the same source for subsequent commands:

```bash
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

The PowerShell equivalent is:

```powershell
$env:SKILLHUB_REGISTRY="https://skillhub.example.com"
```

A single command can instead specify `--registry https://skillhub.example.com`. Identically named skills on different instances have different sources. Changing the source does not relocate installed files.

## 2. Search and verify identity

```bash
skillhub search pdf
```

Each output line contains namespace/slug, latest version and summary. An empty result displays `No skills found.`. Inspect the purpose, version, visibility and dependencies on the same instance's detail page, then copy the actual coordinate.

Public skills support anonymous search and installation. Restricted resources require login and remain subject to resource permissions:

```bash
skillhub login
skillhub whoami
```

When no Token is available from command arguments, environment variables or the registry's saved credentials, login starts device authorization and prints an authorization address and device code. Browser login must be followed by device-code confirmation; an existing browser session is not a CLI credential. An existing Token is verified first, without automatically selecting another account. Remote terminals can use `skillhub login --no-open`. Available login methods and registration depend on instance configuration. `whoami` should identify the selected registry and account; see [accounts and access tokens](./guide/account-access.html).

## 3. Install and inspect files and records

Run from the target project root after replacing the placeholder coordinate:

```bash
skillhub install @team/example-skill --scope project --agent codex
skillhub list --agent codex --json
```

The following is an output-format example, not a real download result. The installation path should match the current project. The directory must contain `SKILL.md`; other files depend on the package:

```text
Installed team/example-skill -> /work/project/.codex/skills/example-skill (codex)

/work/project/.codex/skills/example-skill/
└── SKILL.md
```

The JSON returned by `list` should contain a matching record. This is a complete structure example; paths, time and version must be checked against actual output:

```json
{"ok":true,"items":[{"namespace":"team","slug":"example-skill","version":"1.0.0","agent":"codex","installDir":"/work/project/.codex/skills/example-skill","installedAt":"2026-11-03T08:00:00.000Z","status":"ok"}]}
```

Check the coordinate, Agent and `installDir`, then open that directory's `SKILL.md`. Status `ok` only establishes that the directory exists, not that its content executed; `missing` means that the recorded directory is absent. The list filters by the current registry, not only the current project, so the path must also be checked. Claude Code uses `.claude/skills/<slug>/` for project scope. With `--scope user`, installation is under the home directory and discovery follows the Agent's rules.

Output definitions are in the [install command](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/install.ts) and [list command](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/list.ts).

## 4. Verify Agent discovery and execute a task

Open that project in the target Agent, then reload skills or create a session according to that product's loading mechanism. Check that the target name appears in the Agent's available skills, then provide a concrete task, input and expected artifact as required by the skill. The invocation name depends on the actual `name` in `SKILL.md` and the Agent's discovery result.

| Stage | Observable completion criterion | What remains unproven |
| --- | --- | --- |
| File installation | `SKILL.md` exists at the installation path and the matching record has `status=ok` | Whether the Agent loaded the files |
| Agent discovery | The Agent's skill list or session context contains the target skill | Whether external tools, credentials and business dependencies work |
| Task execution | The Agent produces a verifiable result according to the instructions | Whether other inputs, environments or later versions are equally suitable |

Installation does not execute skills or configure external tools. For cooperation between skills, inspect the entry and member instructions. Shared installation uses `skillhub suite install`; see [Skill Suites](./guide/skill-suites.html).

## 5. Verify rejection of invalid arguments

The following command uses an invalid scope to demonstrate rejection by local argument validation:

```bash
skillhub install @team/example-skill --scope invalid --agent codex
```

The expected standard-error output is shown below, with exit code 5. The command stops before reading installation configuration or downloading files, so it does not install the example skill:

```text
Error: --scope must be "user" or "project"
```

Restore `--scope project` and substitute a real coordinate before installation. Do not use `--force` to address an argument error. The message and execution order are defined by [installation argument validation](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/install.ts).

## Diagnose and resolve failures

| Symptom | Most likely cause | Diagnostic action | Resolution |
| --- | --- | --- | --- |
| Search does not return the target | Source, permissions or publication state differ | Check `skillhub search pdf` and `skillhub whoami` in the same terminal; inspect the coordinate on the same instance | Correct the source and publication state, and log in when required; do not reset the account to troubleshoot |
| Installation reports an existing directory | An existing installation or manual files occupy the path | Compare the reported path with `installDir` in `skillhub list --agent codex --json`, then read its `SKILL.md` | Back up and identify its source, then select the correct project or scope; do not overwrite unknown content |
| The Agent does not discover the installed skill | The project, scope or target Agent differs | Check `agent`, `installDir` and `status` in the list and the project opened by the Agent | Correct the target and reload through the Agent's mechanism; do not interpret `ok` as successful loading |
| The skill is discovered but execution fails | Missing dependencies, input or permissions | Read local `SKILL.md` and compare tool and credential requirements with the Agent's error | Supply dependencies in the target environment; do not place credentials in shared packages or public logs |

## Updates and subsequent tasks

After these checks, inspect an upgrade plan before choosing to apply it:

```bash
skillhub upgrade @team/example-skill --check
skillhub upgrade @team/example-skill
```

Local changes, shared directories or source protections may block an upgrade, so the plan must be read first. Do not add `--force` by default. This page ends with first use of one skill. Removal, version management and full options are in the [CLI reference](./guide/cli.html); authoring and deployment tasks start from the [documentation navigator](./guide/index.html).
