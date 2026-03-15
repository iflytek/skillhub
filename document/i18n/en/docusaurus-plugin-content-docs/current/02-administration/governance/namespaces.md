---
title: Namespace Management
sidebar_position: 1
description: Namespace creation and management
---

# Namespace Management

Namespaces are the isolation boundary and collaboration unit for skills in SkillHub.

## Namespace Types

| Type | Prefix | Description |
|------|--------|-------------|
| Global | `@global` | Platform-level public space, managed by platform admins |
| Team | `@team-*` | Team/department space, managed by team admins |

## Create Namespace

1. After login, go to "My Namespaces"
2. Click "Create Namespace"
3. Fill in information:
   - Slug: URL-friendly name
   - Display name: Display name
   - Description: Space purpose description
4. Submit creation

## Namespace Member Management

### Add Member

1. Go to namespace settings
2. Go to "Member Management"
3. Enter username to search
4. Select role (OWNER/ADMIN/MEMBER)
5. Confirm addition

### Role Change

Namespace OWNER or ADMIN can change member roles.

### Remove Member

Namespace OWNER or ADMIN can remove members.

## Namespace Status

| Status | Description |
|--------|-------------|
| `ACTIVE` | Normal use |
| `FROZEN` | Frozen, read-only, cannot publish |
| `ARCHIVED` | Archived, not visible externally |

## Next Steps

- [Review Workflow](./review-workflow) - Understand skill review
