---
title: Review Workflow
sidebar_position: 2
description: Skill publishing review workflow configuration
---

# Review Workflow

SkillHub uses a two-tier review mechanism to ensure skill quality.

## Review Workflow

### Team Namespace Skills

1. Team member submits publishing
2. Create review task (PENDING)
3. Team ADMIN or OWNER reviews
   - Approve → Skill published (PUBLISHED)
   - Reject → Return for modification (REJECTED)

### Global Namespace Skills

1. Submit publishing
2. Platform SKILL_ADMIN or SUPER_ADMIN reviews
3. Published after review approval

## Promote Team Skill to Global

1. Team skill is published
2. Team ADMIN or OWNER applies "Promote to Global"
3. Platform admin reviews
4. Creates new skill in global namespace after approval

## Review Permissions

| Review Type | Required Role |
|------------|---------------|
| Team namespace skill review | Namespace ADMIN/OWNER |
| Global namespace skill review | SKILL_ADMIN/SUPER_ADMIN |
| Promotion request review | SKILL_ADMIN/SUPER_ADMIN |

## Next Steps

- [User Management](./user-management) - Manage platform users
