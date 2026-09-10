---
title: FAQ
sidebar_position: 1
description: Frequently asked questions
---

# FAQ

## Deployment Related

### How to change default port?

Modify port configuration in `.env.release`.

### How to configure HTTPS?

Recommended to use reverse proxy (Nginx/Ingress) for TLS termination.

### How to backup database?

Use PostgreSQL standard backup tools (pg_dump).

## Usage Related

### How to reset admin password?

If you forgot admin password, you can reconfigure bootstrap admin via environment variables or directly operate the database.

### Skill package upload failed?

Check:
1. Whether file size exceeds limit
2. Whether file type is in whitelist
3. Whether required SKILL.md is included
4. Whether SKILL.md frontmatter format is correct

### CLI install reports `namespace not found`?

Usually the CLI is not pointing at your own SkillHub instance, or the namespace format is wrong:

1. **Set the registry and log in**: point at your instance via an environment variable or `--registry`, e.g. `clawhub --registry https://skillhub.your-company.com install <skill>`. Logging in requires an API Token generated in the web console first.
2. **Namespace slug format**: skills in the global namespace use the bare name (e.g. `my-skill`); team namespaces use the `team--skill` form (`@team/skill` → `team--skill`).
3. The most reliable way is to click the **Install** button on the skill's page in the SkillHub web UI and copy the command, which already includes the correct registry and namespace.

> SkillHub ships both a `clawhub` and a `skillhub` CLI (see their respective READMEs); installing a skill through an OpenClaw conversation calls the CLI under the hood as well.

## Development Related

### How to extend OAuth Provider?

Refer to existing GitHub implementation, add new OAuth Provider configuration.

### How to customize search implementation?

Implement `SearchIndexService` and `SearchQueryService` interfaces.

## Next Steps

- [Troubleshooting](./troubleshooting) - Problem diagnosis
