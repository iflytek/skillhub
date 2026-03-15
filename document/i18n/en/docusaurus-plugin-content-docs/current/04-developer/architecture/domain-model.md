---
title: Domain Model
sidebar_position: 2
description: Core domain entities and relationships
---

# Domain Model

## Core Entities

### Namespace

| Field | Type | Description |
|-------|------|-------------|
| id | bigint | Primary key |
| slug | varchar(64) | URL-friendly identifier |
| display_name | varchar(128) | Display name |
| type | enum | `GLOBAL` / `TEAM` |
| description | text | Description |
| status | enum | `ACTIVE` / `FROZEN` / `ARCHIVED` |

### NamespaceMember

| Field | Type | Description |
|-------|------|-------------|
| id | bigint | Primary key |
| namespace_id | bigint | Namespace ID |
| user_id | varchar(128) | User ID |
| role | enum | `OWNER` / `ADMIN` / `MEMBER` |

### Skill

| Field | Type | Description |
|-------|------|-------------|
| id | bigint | Primary key |
| namespace_id | bigint | Parent namespace |
| slug | varchar(128) | URL-friendly identifier |
| display_name | varchar(256) | Display name |
| summary | varchar(512) | Summary |
| owner_id | varchar(128) | Primary maintainer |
| visibility | enum | `PUBLIC` / `NAMESPACE_ONLY` / `PRIVATE` |
| status | enum | `ACTIVE` / `HIDDEN` / `ARCHIVED` |
| latest_version_id | bigint | Latest published version |

**Unique constraint**: `(namespace_id, slug)`

### SkillVersion

| Field | Type | Description |
|-------|------|-------------|
| id | bigint | Primary key |
| skill_id | bigint | Skill ID |
| version | varchar(32) | semver version |
| status | enum | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `REJECTED` / `YANKED` |
| manifest_json | json | File manifest |
| parsed_metadata_json | json | SKILL.md parsed result |

**Unique constraint**: `(skill_id, version)`

### SkillTag

| Field | Type | Description |
|-------|------|-------------|
| id | bigint | Primary key |
| skill_id | bigint | Skill ID |
| tag_name | varchar(64) | Tag name |
| target_version_id | bigint | Target version |

**Unique constraint**: `(skill_id, tag_name)`

## Coordinate System

Full skill address: `@{namespace_slug}/{skill_slug}`

## Next Steps

- [Security Architecture](./security) - Security design
