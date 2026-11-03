# Quick Start: Install and Use a Skill

Install your first skill and use it in your Agent. This guide uses an existing registry; no SkillHub server deployment is needed.

## Before you begin

- An Agent that supports skill directories, such as Codex or Claude Code.
- Node.js 18 or later and npm; check with `node --version`.
- A project directory and a reachable SkillHub registry.

Run the commands in a terminal. `@team/example-skill` is a placeholder, not a guaranteed public package. Replace it with an actual search result. Before a project-scoped installation, enter the project root that your Agent will open.

## 1. Install the CLI and select a registry

```bash
npm install -g @astron-team/skillhub
skillhub version
```

The default registry is `https://skill.xfyun.cn`. For a self-hosted instance, set its actual URL in your terminal:

```bash
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

For PowerShell, set the registry with:

```powershell
$env:SKILLHUB_REGISTRY="https://skillhub.example.com"
```

For a single command, append `--registry https://skillhub.example.com`. Use the same registry for search, login, and installation. Identically named skills from different registries are different installation sources.

Public skills support anonymous search and installation. Publishing and restricted resources require authentication:

```bash
skillhub login
skillhub whoami
```

If no token is resolved from command arguments, environment variables, or saved registry credentials, follow the device-authorization instructions in the browser. When a token is found, login validates that identity rather than automatically asking you to choose another account. Use `skillhub login --no-open` on a remote terminal. Available sign-up and login methods depend on the instance configuration.

## 2. Find and install a skill

```bash
skillhub search pdf
```

Review the skill's purpose, version, visibility, and instructions on its detail page. Replace the placeholder `@team/example-skill` below with a real search result.

From the target project root:

```bash
skillhub install @team/example-skill --scope project --agent codex
skillhub list --agent codex
```

For Claude Code, use `--agent claude-code`. Use `--scope user` to share an installation across projects.
Codex project installations use `.codex/skills/<slug>/`; Claude Code uses `.claude/skills/<slug>/`. User installations use the corresponding directory under your home directory.

### Verify the destination

Read the destination printed by installation, then inspect `skillhub list --agent codex`: the coordinate, version, Agent, directory, and status should match your choice. `list` filters by the active registry; switching registries does not delete local files.

`missing` means the recorded directory does not exist. `ok` only means the directory exists, not that the Agent loaded or successfully executed the skill. Check `SKILL.md` in the actual destination before continuing.

## 3. Use it in your Agent

Open the project in your Agent and start a new session or reload skills if required. Confirm that the Agent discovers the skill, then make a concrete request:

> Use example-skill and follow its instructions to process the following material: …

Use the name in `SKILL.md` and the Agent's discovery results. Installation does not execute a skill or configure its required tools and credentials.
If discovery fails, check the CLI's destination, current project, scope, and target Agent; see the [FAQ](./faq.html).

For a collection, use `skillhub suite install`, then start with its entry skill. See [Skill Suites](./guide/skill-suites.html).

## 4. Update and manage installations

```bash
skillhub upgrade @team/example-skill --check
skillhub upgrade @team/example-skill
```

Review the plan before updating and preserve local changes you need. Do not use `--force` by default. See the [CLI guide](./guide/cli.html) for removal, team workspace synchronization, and command details.

## Troubleshoot the first installation


| Symptom | Locate the cause | Expected result or cause | Action |
| --- | --- | --- | --- |
| Skill not found | In the same terminal run `skillhub search pdf` and `skillhub whoami`; find the actual coordinate in the same registry's UI | Search returns published resources visible to the caller; anonymous public search can work even when the identity request is unauthenticated | Check registry, coordinate, and publication state; log in for restricted resources. Do not reset accounts or delete local files to troubleshoot search |
| Directory conflict | Run `skillhub list --agent codex --json`; compare `items[].installDir` with the conflict path and open that directory's `SKILL.md` | A recorded install should match its source, version, and directory; an unrecorded directory may contain manual work | Back up and identify the contents, then choose the intended project or scope. Do not force-overwrite unknown files |
| Installed but not discovered | From the target project run `skillhub list --agent codex --json`; inspect `items[].agent`, `installDir`, and `status`, then open the actual `SKILL.md` | `status=ok` only means the directory exists; `missing` means the recorded directory is absent. Agent discovery is a separate step | Check project and Agent profile, then reload or start a session according to the Agent's rules. Do not treat `ok` as proof of discovery |
| Discovered but execution fails | Open the installed `SKILL.md`, check its tool/input/credential requirements, and read the actual Agent error | Files may be installed correctly while tools or permissions are missing; the registry does not configure external tools | Configure the target environment as instructed. Do not include credentials in shared packages or public error reports |

Run these commands in the project containing the installation. Match `--agent codex` to the actual Agent; use `--agent claude-code` for Claude Code. `pdf` is a replaceable search term, not a guaranteed skill name. See the [list implementation](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/list.ts) and [CLI option definitions](https://github.com/iflytek/skillhub/blob/main/cli/src/index.ts) for fields and filtering.

## Next steps

Install a [Skill Suite](./guide/skill-suites.html) when you need a coordinated collection, or use the [CLI reference](./guide/cli.html) for more commands. For publishing, team administration, hosting, or development, choose your next task in the [documentation map](./guide/index.html).
