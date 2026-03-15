---
title: Authorization Management
sidebar_position: 2
description: RBAC permission system configuration
---

# Authorization Management

SkillHub uses a Role-Based Access Control (RBAC) system.

## Platform Roles

| Role | Code | Description |
|------|------|-------------|
| Super Admin | `SUPER_ADMIN` | Has all permissions |
| Skill Admin | `SKILL_ADMIN` | Global namespace review, skill governance |
| User Admin | `USER_ADMIN` | User management, role assignment |
| Auditor | `AUDITOR` | Audit log read-only |

## Namespace Roles

| Role | Description |
|------|-------------|
| `OWNER` | Namespace owner, can transfer ownership |
| `ADMIN` | Namespace admin, can review, manage members |
| `MEMBER` | Regular member, can publish skills |

## Permission Configuration

Assign platform roles and namespace roles through the admin dashboard.

## Next Steps

- [Audit Logs](./audit-logs) - View operation audits
