---
title: User Management
sidebar_position: 3
description: Platform user management
---

# User Management

## User Status

| Status | Effective behavior |
|--------|--------------------|
| `ACTIVE` | Can log in and use the system normally. OAuth auto-admission and local registration both create users in this state. |
| `PENDING` | Account exists but cannot log in. Under approval-required OAuth flows, the system creates a `PENDING` user and redirects to the pending-approval page. Local login also rejects this status. |
| `DISABLED` | Cannot log in. Both OAuth and local auth reject it. `/api/v1/auth/me` will invalidate the current session if the backing user has been disabled. |
| `MERGED` | Account has been merged into another account and can no longer log in. This is mainly written by the account-merge flow, not by normal user administration. |

## User Admission

Configure whether new users require approval:
- Auto-admission: New users automatically activated after login
- Approval admission: New users require USER_ADMIN approval to activate

## Role Assignment

`USER_ADMIN` or `SUPER_ADMIN` can call the user-management API to change platform roles, but the implementation has a few important constraints:

- The API sets exactly one target platform role at a time.
- It deletes the user's existing explicit platform-role bindings before writing the new one.
- If the target role is `USER`, no `user_role_binding` row is written; runtime defaulting adds it later.
- `USER_ADMIN` cannot assign `SUPER_ADMIN`; only `SUPER_ADMIN` can do that.

The currently supported target roles in practice are:

- `USER`
- `SKILL_ADMIN`
- `USER_ADMIN`
- `AUDITOR`
- `SUPER_ADMIN`

## User Disable/Enable

`USER_ADMIN` or `SUPER_ADMIN` can disable or enable users.

The current public management API only supports changing status to:

- `ACTIVE`
- `DISABLED`

In practice:

- "Approve user" is implemented as changing the status to `ACTIVE`.
- The API does not directly set users to `PENDING` or `MERGED`.

## Account Merge

Supports merging multiple accounts into one, preserving operation history.

## Next Steps

- [Create Skill Package](../../user-guide/publishing/create-skill) - Start publishing skills
