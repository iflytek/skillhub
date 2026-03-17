---
title: Authorization Management
sidebar_position: 2
description: RBAC permission system configuration
---

# Authorization Management

SkillHub uses a Role-Based Access Control (RBAC) system.

The current codebase actually uses two parallel role systems:

- Platform roles: control platform-wide governance, user administration, and audit capabilities.
- Namespace roles: control actions inside a specific team namespace.

They participate in authorization together, but they are not a single hierarchy.

## Platform Roles

### Explicit platform roles seeded by code

The database migration seeds only 4 explicit platform roles:

| Role | Code | Effective behavior |
|------|------|--------------------|
| Super Admin | `SUPER_ADMIN` | Has all permissions. `RbacService#getUserPermissions` returns all permission codes for this role. Can access all endpoints available to `SUPER_ADMIN` / `SKILL_ADMIN` / `USER_ADMIN` / `AUDITOR`. Can assign `SUPER_ADMIN`. Can bypass namespace membership checks during publish and auto-publish directly. Still cannot approve their own promotion request, and for normal review tasks the self-submission exception is only bypassed by `SUPER_ADMIN`. |
| Skill Admin | `SKILL_ADMIN` | Can access skill governance admin endpoints. Can hide/unhide skills, yank versions, and resolve/dismiss skill reports. Can review global namespace review tasks, promotion requests, and governance inbox items for review/promotion/report. Cannot manage users or read audit logs. |
| User Admin | `USER_ADMIN` | Can access user management endpoints. Can list users, approve users, enable/disable users, and change platform roles. Cannot assign `SUPER_ADMIN`. Cannot perform skill governance or read audit logs. |
| Auditor | `AUDITOR` | Read-only audit access. Can access `/api/v1/admin/audit-logs` and `/actuator/prometheus`. In the governance workbench this role can read activity, but cannot process review/promotion/report items and cannot manage users or skills. |

### Runtime default platform role

| Role | Code | Effective behavior |
|------|------|--------------------|
| Default User | `USER` | Not an explicitly seeded row in the `role` table. If a user has no explicit platform-role binding, login/session resolution and `RbacService#getUserRoleCodes` automatically add `USER`. It represents a normal signed-in user with no extra governance privileges. |

### Important implementation details

- The current admin API changes platform role by replacement, not by append: `PUT /api/v1/admin/users/{userId}/role` deletes existing explicit platform-role bindings first, then writes one target role.
- If the target role is `USER`, no explicit binding is stored; the role is supplied later by runtime defaulting.
- The lower-level auth/session/RBAC code still supports multiple explicit platform roles on one user because it works with role sets. The current admin API simply does not assign roles that way.
- `SUPER_ADMIN` is the only role treated as "all permissions" during permission lookup. Other roles depend on `role_permission`.

## Namespace Roles

| Role | Effective behavior |
|------|--------------------|
| `OWNER` | Automatically assigned when creating a team namespace. Can update namespace settings, manage members, freeze/unfreeze the namespace, archive/restore it, and transfer ownership. Can submit reviews, review team-namespace review tasks, access private skills, and manage restricted skill lifecycle. |
| `ADMIN` | Can update namespace settings, manage members, and freeze/unfreeze the namespace. Cannot archive/restore the namespace and cannot directly set someone to `OWNER`. Can submit reviews, review team-namespace review tasks, access private skills, and manage restricted skill lifecycle. |
| `MEMBER` | Common member role, including auto-membership in the global namespace. Can publish skills in namespaces they belong to and can submit reviews. Cannot review review tasks, manage members, or freeze/archive namespaces. `MEMBER` alone is not enough to access private skills. |

### Namespace role boundaries

- The `GLOBAL` namespace is effectively immutable in namespace-governance flows. Review/promotion/report handling there depends on platform roles `SKILL_ADMIN` / `SUPER_ADMIN`, not on global namespace membership alone.
- For `NAMESPACE_ONLY` visibility, any namespace member can access the skill.
- For `PRIVATE` visibility, access is limited to the skill owner or namespace `ADMIN` / `OWNER`; `MEMBER` is not enough.

## Permission Configuration

Assign platform roles through admin user management, and namespace roles through namespace membership.

## Next Steps

- [Audit Logs](./audit-logs) - View operation audits
