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

## Development Related

### How to extend OAuth Provider?

Refer to existing GitHub implementation, add new OAuth Provider configuration.

### How to customize search implementation?

Implement `SearchIndexService` and `SearchQueryService` interfaces.

## Next Steps

- [Troubleshooting](./troubleshooting) - Problem diagnosis
