---
title: System Architecture
sidebar_position: 1
description: SkillHub system architecture overview
---

# System Architecture

## Architecture Principles

- **Monolith-first**: Phase 1 uses modular monolith, no microservices
- **Dependency Inversion**: Domain layer does not depend on infrastructure
- **Replaceable Boundaries**: Search and storage both have SPI abstractions

## Module Structure

```
server/
├── skillhub-app/          # Startup, configuration assembly, Controllers
├── skillhub-domain/       # Domain models + domain services + application services
├── skillhub-auth/         # OAuth2 authentication + RBAC + authorization decisions
├── skillhub-search/       # Search SPI + PostgreSQL full-text implementation
├── skillhub-storage/      # Object storage abstraction + LocalFile/S3
└── skillhub-infra/        # JPA, utilities, configuration foundation
```

## Module Dependencies

```
app → domain, auth, search, storage, infra
infra → domain
auth → domain
search → domain
storage → (independent)
```

## Tech Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.2.3 |
| Database | PostgreSQL | 16.x |
| Cache/Session | Redis | 7.x |

## Deployment Architecture

```
┌──────────────┐
│ Browser / CLI│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Web/Nginx   │
└──────┬───────┘
       │ /api/*
       ▼
┌──────────────┐
│ Spring Boot  │
└───┬────┬─────┘
    │    │
    ▼    ▼
PostgreSQL  Redis
```

## Next Steps

- [Domain Model](./domain-model) - Core entities
