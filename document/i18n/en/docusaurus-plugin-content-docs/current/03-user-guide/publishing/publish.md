---
title: Publish Workflow
sidebar_position: 2
description: Publish skills to SkillHub
---

# Publish Workflow

## Publish via Web

1. Login to SkillHub
2. Click "Publish Skill"
3. Select target namespace
4. Upload skill package ZIP file
5. Fill in version information (changelog, etc.)
6. Submit publishing
7. Wait for review (if required)
8. Published successfully after review approval

## Publish via CLI

```bash
# 1. Login
skillhub login

# 2. Publish
skillhub publish ./my-skill.zip --namespace @team-myteam
```

## Publish via ClawHub CLI

Use after configuring registry:

```bash
clawhub publish ./my-skill.zip
```

## Publishing Status

| Status | Description |
|--------|-------------|
| `DRAFT` | Draft, not submitted for review |
| `PENDING_REVIEW` | Pending review |
| `PUBLISHED` | Published, discoverable and downloadable |
| `REJECTED` | Rejected, need modification and resubmit |
| `YANKED` | Withdrawn, no longer recommended for use |

## Next Steps

- [Version Management](./versioning) - Manage skill versions
