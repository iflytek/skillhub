---
title: Overview
sidebar_position: 1
description: SkillHub product overview and core features
---

# Overview

SkillHub is an enterprise-grade AI skill registry platform for publishing, discovering, and managing skills with a self-hosted architecture ensuring data security.

## Core Features

### Publishing Management
- Version control and Semantic Versioning
- Custom tags (like `beta`/`stable`)
- `latest` tag automatically follows the latest published version

### Discovery
- Full-text search
- Multi-dimensional filtering (namespace, downloads, ratings)
- Visibility control

### Organization
- Namespace isolation
- Role-based access control (RBAC)
- Team and global two-tier scopes

### Governance
- Two-tier review workflow
- Audit logs
- Permission separation

### Storage and Deployment
- S3/MinIO/Local storage support
- Docker/Kubernetes deployment
- Enterprise-grade observability

## Tech Stack

### Backend
- **Java 21** - Runtime
- **Spring Boot 3.2.3** - Application framework
- **PostgreSQL 16.x** - Primary database + full-text search
- **Redis 7.x** - Cache and session storage

### Frontend
- **React 19** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool
- **Tailwind CSS** - Styling framework

### Deployment
- **Docker Compose** - Single-machine deployment
- **Kubernetes** - Production orchestration

## Core Concepts

### Namespace
Skill isolation boundary, supporting `@global` (global) and `@team-*` (team) prefixes.

### Coordinate System
Skill identifier format: `@{namespace_slug}/{skill_slug}`, supports semantic versioning.

### Compatibility
Provides REST API and ClawHub compatibility layer for existing tool integration.

## Next Steps

- [Quick Start](./quick-start) - One-click startup experience
- [Use Cases](./use-cases) - Explore enterprise application scenarios
