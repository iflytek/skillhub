---
title: User Management
sidebar_position: 3
description: Platform user management
---

# User Management

## User Status

| Status | Description |
|--------|-------------|
| `ACTIVE` | Normal use |
| `PENDING` | Pending approval |
| `DISABLED` | Disabled |
| `MERGED` | Merged into another account |

## User Admission

Configure whether new users require approval:
- Auto-admission: New users automatically activated after login
- Approval admission: New users require USER_ADMIN approval to activate

## Role Assignment

USER_ADMIN can assign platform roles:
- SKILL_ADMIN
- USER_ADMIN
- AUDITOR

Note: Cannot assign SUPER_ADMIN (only SUPER_ADMIN can assign)

## User Disable/Enable

USER_ADMIN or SUPER_ADMIN can disable/enable users.

## Account Merge

Supports merging multiple accounts into one, preserving operation history.

## Next Steps

- [Create Skill Package](../../user-guide/publishing/create-skill) - Start publishing skills
