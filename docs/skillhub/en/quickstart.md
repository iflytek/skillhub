# Quick Start

Choose your goal first. You do not need to deploy SkillHub to consume skills from an existing registry.

| Goal | Guide |
| --- | --- |
| Find, install, and use a skill | Follow this page |
| Install a coordinated collection | [Skill Suites](./guide/skill-suites.md) |
| Publish your own skill | [Publishing and versioning](./guide/skill-publish.md) |
| Upload a suite and its packages | [Suite Bundle import](./guide/suite-bundle.md) |
| Host a team registry | [Docker deployment](./guide/deployment.md) / [Kubernetes](./guide/kubernetes.md) |
| Develop SkillHub | [Development handbook](https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md) |

## 1. Install the CLI and select a registry

```bash
npm install -g @astron-team/skillhub
skillhub version
```

The default registry is `https://skill.xfyun.cn`. For a self-hosted instance, set its actual URL in your terminal:

```bash
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

Public skills support anonymous search and installation. Publishing and restricted resources require authentication:

```bash
skillhub login
skillhub whoami
```

Follow the terminal instructions to authenticate in the browser and confirm the device code. Use `skillhub login --no-open` on a remote terminal. Available sign-up and login methods depend on the instance configuration.

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

## 3. Use it in your Agent

Open the project in your Agent and start a new session or reload skills if required. Confirm that the Agent discovers the skill, then make a concrete request:

> Use example-skill and follow its instructions to process the following material: …

Use the name in `SKILL.md` and the Agent's discovery results. Installation does not execute a skill or configure its required tools and credentials.
If discovery fails, check the CLI's destination, current project, scope, and target Agent; see the [FAQ](./faq.md).

For a collection, use `skillhub suite install`, then start with its entry skill. See [Skill Suites](./guide/skill-suites.md).

## 4. Update and manage installations

```bash
skillhub upgrade @team/example-skill --check
skillhub upgrade @team/example-skill
```

Review the plan before updating and preserve local changes you need. Do not use `--force` by default. See the [CLI guide](./guide/cli.md) for removal, team workspace synchronization, and command details.

## 5. Publish your own skill

The package root must contain uppercase `SKILL.md`. A minimal file is:

```markdown
---
name: example-skill
description: Describe when to use this skill and what it does.
---

# Example skill

Explain the inputs, steps, expected output, and required tools.
```

Add `scripts/`, `references/`, and `assets/` as needed; `package.json` is not required.
Create or join a namespace where you can publish, then follow the [publishing guide](./guide/skill-publish.md) to prepare a version and upload:

```bash
skillhub publish ./example-skill --namespace my-team
```

A successful submission is not a published version. Open the returned detail URL to follow scanning, review, and the final status before attempting normal installation.
