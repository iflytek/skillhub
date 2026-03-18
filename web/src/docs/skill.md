---
name: skillhub-registry
description: Use this when you need to search, inspect, install, or publish agent skills against a SkillHub registry. SkillHub is a self-hosted skill registry with a ClawHub-compatible API layer, so prefer the `clawhub` CLI for registry operations instead of making raw HTTP calls.
---

# SkillHub Registry

Use this skill when you need to work with a SkillHub deployment: search skills, inspect metadata, install a package, or publish a new version.

> Important: Prefer the `clawhub` CLI for registry workflows. SkillHub exposes a ClawHub-compatible API surface and a discovery endpoint at `/.well-known/clawhub.json`, so the CLI is the safest path for auth, resolution, and download behavior. Only fall back to raw HTTP when debugging the server itself.

## What SkillHub Is

SkillHub is a self-hosted, enterprise-oriented skill registry. It stores versioned skill packages, supports namespace-based governance, and keeps `SKILL.md` compatibility with OpenSkills-style packages.

Key facts:

- Internal coordinates use `@{namespace}/{skill_slug}`.
- ClawHub-compatible clients use a canonical slug instead.
- `latest` always means the latest published version, never draft or pending review.
- Public skills in `@global` can be downloaded anonymously.
- Team namespace skills and non-public skills require authentication.

## Configure The CLI

Point `clawhub` at the SkillHub base URL:

```bash
export CLAWHUB_REGISTRY_URL=https://skillhub.your-company.com
```

If you need authenticated access, provide an API token:

```bash
export CLAWHUB_API_TOKEN=sk_your_api_token_here
```

Optional local check:

```bash
curl https://skillhub.your-company.com/.well-known/clawhub.json
```

Expected response:

```json
{ "apiBase": "/api/v1" }
```

## Coordinate Rules

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

Minimum valid `SKILL.md` frontmatter:

```yaml
---
name: my-skill
description: When to use this skill
---
```

Required structure:

```text
my-skill/
├── SKILL.md
├── references/
├── scripts/
└── assets/
```

Contract notes:

- `name` and `description` are required.
- `name` becomes the immutable skill slug on first publish.
- `description` becomes the registry summary.
- `references/`, `scripts/`, and `assets/` are optional.
- The package is treated as a text-first resource bundle, not a binary artifact bucket.

## Publishing Guidance

Before publishing:

1. Ensure `SKILL.md` exists at the package root.
2. Keep the skill name in kebab-case.
3. Make sure the version you are publishing is semver-compatible.
4. Avoid relying on `latest` as a rollback tool; SkillHub keeps `latest` automatically pinned to the newest published version.
5. Use custom tags like `beta` or `stable` for release channels when needed.

## When To Use Raw HTTP

Use direct HTTP only for server debugging, contract testing, or compatibility work. Relevant endpoints exposed by the current codebase include:

- `GET /.well-known/clawhub.json`
- `GET /api/v1/search`
- `GET /api/v1/resolve`
- `GET /api/v1/download/{slug}`
- `GET /api/v1/skills/{slug}`
- `POST /api/v1/publish`
- `GET /api/v1/whoami`

For normal registry usage, stay on the `clawhub` CLI.

## Project References

Read these local documents when you need more detail about SkillHub behavior:

- `docs/00-product-direction.md`
- `docs/06-api-design.md`
- `docs/07-skill-protocol.md`
- `docs/14-skill-lifecycle.md`
- `docs/openclaw-integration.md`
- `README.md`
