---
title: Version Management
sidebar_position: 3
description: Skill version and tag management
---

# Version Management

## Semantic Versioning

SkillHub uses Semantic Versioning: `MAJOR.MINOR.PATCH`

- `MAJOR`: Incompatible API changes
- `MINOR`: Backward compatible feature additions
- `PATCH`: Backward compatible bug fixes

Examples: `1.0.0`, `1.1.0`, `2.0.0`

## latest Tag

`latest` is a system reserved tag that automatically follows the latest published version and cannot be manually moved.

## Custom Tags

You can create custom tags for version channel management:

- `beta` - Beta version
- `stable` - Stable version
- `stable-2026q1` - Quarterly stable version

### Create/Move Tag

```bash
clawhub tag set @team/my-skill beta 1.2.0
```

### Delete Tag

```bash
clawhub tag delete @team/my-skill beta
```

## Version Withdrawal

Published versions with issues can be withdrawn:

1. Go to skill detail page
2. Find target version
3. Click "Withdraw Version"
4. Confirm withdrawal

Withdrawn versions remain visible but are marked as not recommended for use.

## Next Steps

- [Search Skills](../discovery/search) - Discover skills
