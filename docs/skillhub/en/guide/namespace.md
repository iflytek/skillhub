# Namespace & Team Management

## Overview

Namespaces are the core organizational unit in SkillHub. Each namespace represents a team or project with its own members, permissions, and skill packages.

![Concept Diagram](/diagrams/namespace-concept.png)

**Purpose of Namespaces**:

- **Isolation**: Skill packages from different teams don't interfere with each other
- **Permissions**: Role-based access control (RBAC)
- **Collaboration**: Team members can jointly manage skill packages
- **Governance**: Administrators can review, archive, and freeze skill packages

**Role System**:

| Role | Permissions |
|------|-------------|
| **Owner** | Full control, including deleting the namespace and managing all members |
| **Admin** | Manage members, review skill packages, modify settings |
| **Member** | Publish skill packages, view private skill packages |

**Namespace States**:

- **Active**: Normal operation
- **Frozen**: Cannot publish new skill packages
- **Archived**: Hidden from search results

## Use Cases

**Case 1: Create a Team Namespace**

A team lead creates a new namespace to manage the team's skill packages.

![Screenshot](/screenshots/namespace-create.png)

**Case 2: Add Team Members**

An administrator invites new members to join the namespace and assigns appropriate roles.

![Screenshot](/screenshots/namespace-members.png)

**Case 3: Permission Management**

Adjust member roles to control who can publish, review, and manage skill packages.

**Case 4: Namespace Freeze**

Discover a security issue in the namespace and temporarily freeze all publish operations.

## Step-by-Step Guide

**Create a Namespace**:

1. Navigate to `/dashboard/namespaces`
2. Click "Create Namespace"
3. Fill in the information:
   - Name: Team name (e.g., "iFlytek AI Team")
   - Slug: URL identifier (e.g., "iflytek")
   - Description: Brief description of the team's responsibilities and skill package scope

![Flow Diagram](/diagrams/namespace-create-flow.png)

4. Submit to create; you are automatically set as the Owner

**Add Members**:

1. Go to the namespace detail page
2. Click the "Members" tab
3. Click "Add Member"
4. Search for users (supports searching by username, email)
5. Select a role (Owner / Admin / Member)
6. Confirm to add

**Manage Permissions**:

1. Find the target user in the member list
2. Click "Change Role"
3. Select a new role and confirm
4. Permission changes are recorded in the audit log

**Freeze a Namespace**:

1. Go to namespace settings
2. Click "Freeze Namespace"
3. Provide a reason for freezing (optional)
4. Confirm to freeze

> After freezing, all skill packages in the namespace cannot publish new versions, but existing versions remain downloadable.

## API Reference

**Create a Namespace**:
```bash
POST /api/v1/namespaces
Content-Type: application/json

{
  "name": "iFlytek AI Team",
  "slug": "iflytek",
  "description": "iFlytek's AI agent skills"
}
```

**Parameter Reference**:
| Parameter | Type | Description |
|-----------|------|-------------|
| name | string | Namespace name (required, 2-50 characters) |
| slug | string | URL identifier (required, unique, 2-64 characters, lowercase letters, numbers, hyphens only) |
| description | string | Description (optional, max 500 characters) |

**Get Namespace Details**:
```bash
GET /api/v1/namespaces/{slug}
```

**Update a Namespace**:
```bash
PUT /api/v1/namespaces/{slug}
Content-Type: application/json

{
  "name": "iFlytek AI Team (Updated)",
  "description": "Updated description"
}
```

**Add a Member**:
```bash
POST /api/v1/namespaces/{slug}/members
Content-Type: application/json

{
  "userId": "user-123",
  "role": "MEMBER"
}
```

**Update Member Role**:
```bash
PUT /api/v1/namespaces/{slug}/members/{userId}/role
Content-Type: application/json

{
  "role": "ADMIN"
}
```

**Remove a Member**:
```bash
DELETE /api/v1/namespaces/{slug}/members/{userId}
```

**Freeze a Namespace**:
```bash
POST /api/v1/namespaces/{slug}/freeze
Content-Type: application/json

{
  "reason": "Security investigation"
}
```

**Unfreeze a Namespace**:
```bash
POST /api/v1/namespaces/{slug}/unfreeze
```

## Notes

> **Slug Uniqueness**: Namespace slugs must be globally unique and cannot be changed after creation. Use a short identifier for your team or project.

- **Owner Requirement**: Each namespace must have at least one Owner; the last Owner cannot be removed
- **Role Inheritance**: Namespace members automatically have access to all skill packages in that namespace
- **Freeze Mechanism**: Administrators can freeze a namespace; no new skill packages can be published while frozen
- **Archive Mechanism**: Archived namespaces are hidden from search results, but existing skill packages remain accessible
- **Audit Logs**: All member changes and permission adjustments are recorded in the audit log
