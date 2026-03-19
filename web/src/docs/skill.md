---
name: skillhub-registry
description: Use this when you need to search, inspect, install, or publish agent skills against a SkillHub registry. SkillHub is a skill registry with a ClawHub-compatible API layer, so prefer the `clawhub` CLI for registry operations instead of making raw HTTP calls.
---

# SkillHub Registry

Use this skill when you need to work with a SkillHub registry: search skills, inspect metadata, install a package, or publish a new version.

> Important: Prefer the `clawhub` CLI for registry workflows. SkillHub exposes a ClawHub-compatible API surface and a discovery endpoint at `/.well-known/clawhub.json`, so the CLI is the safest path for auth, resolution, and download behavior. Only fall back to raw HTTP when debugging the server itself.

## What SkillHub Is

SkillHub is an enterprise-oriented skill registry. It stores versioned skill packages, supports namespace-based skill management, and keeps `SKILL.md` compatibility with OpenSkills-style packages.

Key facts:

- Internal coordinates use `@{namespace}/{skill_slug}`.
- If using the clawhub CLI, the compatible format is `{namespace}--{skill_slug}`.
- ClawHub-compatible clients use a `{namespace}--{skill_slug}` slug instead.
- `latest` always means the latest published version, never draft or pending review.
- Public skills in `@global` can be downloaded anonymously.
- If no namespace is specified, it defaults to `@global`.
- `{skill_slug}` can be used instead of `global--{skill_slug}`
- Team namespace skills and non-public skills require authentication.

## Configure The CLI

Point `clawhub` at the SkillHub base URL:

```bash
export CLAWHUB_REGISTRY=https://skillhub.your-company.com
```

Alternatively, use the `--registry` parameter every time, for example:

```bash
npx clawhub install my-skill --registry https://skillhub.your-company.com
```


If you need authenticated access, provide an API token:

```bash
clawhub login --token sk_your_api_token_here
```

Optional local check:

```bash
curl https://skillhub.your-company.com/.well-known/clawhub.json
```

Expected response:

```json
{"apiBase":"/api/v1"}
```

## Coordinate Rules - IMPORTANT

SkillHub has two naming forms:

| SkillHub coordinate | Canonical slug for `clawhub` |
|---|---|
| `@global/my-skill` | `my-skill` |
| `@team-name/my-skill` | `team-name--my-skill` |

Rules:

- `--` is the namespace separator in the compatibility layer.
- If there is no `--`, the skill is treated as `@global/...`.
- `latest` resolves to the latest published version only.

Examples:

```bash
npx clawhub install my-skill
npx clawhub install my-skill@1.2.0
npx clawhub install team-name--my-skill
```

## Common Workflows

### Search

```bash
npx clawhub search email
```

Use an empty query when you want a broad listing:

```bash
npx clawhub search ""
```

### Inspect A Skill

```bash
npx clawhub info my-skill
npx clawhub info team-name--my-skill
```

### Install

```bash
npx clawhub install my-skill
npx clawhub install my-skill@1.2.0
npx clawhub install team-name--my-skill
```

### Publish

Prepare a skill package directory, then publish it:

```bash
npx clawhub publish ./my-skill
```

Publishing requires authentication and sufficient permissions in the target namespace.

## Authentication And Visibility

Download and search permissions depend on namespace and visibility:

- `@global` + `PUBLIC`: anonymous search, inspect, and download are allowed.
- Team namespace + `PUBLIC`: authentication required for download.
- `NAMESPACE_ONLY`: authenticated namespace members only.
- `PRIVATE`: owner or explicitly authorized users only.
- Publish, star, and other write operations always require authentication.

If a request fails with `403`, check:

- whether the skill belongs to a team namespace,
- whether the skill is `NAMESPACE_ONLY` or `PRIVATE`,
- whether your token is valid,
- whether you have namespace publish permissions.

## Skill Package Contract

SkillHub expects OpenSkills-style packages with `SKILL.md` as the entry point.

## Publishing Guidance

Just need to follow the OpenSkills-style standards.
