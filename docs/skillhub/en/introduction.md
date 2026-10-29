# Introduction

SkillHub is a self-hosted Agent Skill registry built for the enterprise.

In the age of AI Agents, every team accumulates its own skill packages (Skills). But these packages are scattered everywhere: some on developers' local machines, some in Git repositories, and some buried in internal documentation. Team members struggle to discover each other's work, let alone reuse existing capabilities.

SkillHub solves this problem. It provides a **private, controllable, and easy-to-use** skill package registry, allowing teams to manage Agent Skills just like using npm or PyPI.

![Architecture Diagram](/diagrams/architecture.png)

## Core Value

- **Managed distribution**: Upload packages and follow scanning, review, and publication
- **Enterprise-grade permissions**: Namespace-based RBAC with team collaboration and review workflows
- **Full lifecycle**: Version management, tag system, review workflows, and archiving mechanisms
- **Deployment choices**: Docker Compose and Kubernetes with explicit dependency and authentication configuration
- **Security scanning**: Built-in Skill Scanner for automatic security risk detection
- **Data sovereignty**: Fully self-hosted — all data stays within your firewall

## Tech Stack

![Frontend, backend, data and storage components](/diagrams/tech-stack-sketch.png)

Storage can use local files or an S3-compatible provider; select it explicitly for your deployment.

| Layer | Technology | Description |
|-------|------------|-------------|
| **Frontend** | React + Vite + TanStack Router | Modern SPA with multiple interface languages |
| **Backend** | Java 21 + Spring Boot | Enterprise-grade REST API |
| **Database** | PostgreSQL | Full-text search, Flyway auto-migration |
| **Cache** | Redis | Session management, hot data caching |
| **Storage** | Local files / S3-compatible storage | Skill package file storage, supports local and cloud |
| **Deployment** | Docker Compose / K8s | Configured self-hosting |

## Feature Overview

| Feature | Description |
|---------|-------------|
| [Skill Publishing & Version Management](/en/guide/skill-publish) | One-click skill package publishing with semantic versioning |
| [Skill Search & Discovery](/en/guide/skill-discovery) | Full-text search, smart filtering, permission-aware |
| [Namespace & Team Management](/en/guide/namespace) | Namespace-based RBAC permission system |
| [Review & Governance](/en/guide/review) | Skill and suite review workflows, reporting system |
| [Security Scanning](/en/guide/scanner) | Built-in Skill Scanner with multi-engine security analysis |
| [User Interaction & Social](/en/guide/social) | Starring, ratings, notification system |

## Three different objects

| Object | Purpose | Next step |
| --- | --- | --- |
| Skill | An individual package with `SKILL.md`, discovered and used by an Agent after installation | [Quick Start](./quickstart.md) |
| Skill Suite | A collection of pinned skill versions with one member designated as its entry skill | [Install and use suites](./guide/skill-suites.md) |
| Suite Bundle | An authoring format for importing a suite definition and member packages | [Bundle import](./guide/suite-bundle.md) |

The registry distributes and governs skills; execution happens in the user's Agent environment. Installing a suite does not automatically run all its members.

## Read by role

- Consumers: [CLI](./guide/cli.md), [discovery](./guide/skill-discovery.md), [FAQ](./faq.md).
- Authors: [publishing](./guide/skill-publish.md), [suites](./guide/skill-suites.md), [review](./guide/review.md).
- Operators: [namespaces](./guide/namespace.md), [deployment and upgrades](./guide/deployment.md), [Kubernetes](./guide/kubernetes.md).
- Integrators: [Runtime contract](./guide/runtime-integration.md), [repository documentation index](https://github.com/iflytek/skillhub/blob/main/docs/README.md).
