# Introduction

SkillHub is a self-hosted Agent Skill registry built for the enterprise.

In the age of AI Agents, every team accumulates its own skill packages (Skills). But these packages are scattered everywhere: some on developers' local machines, some in Git repositories, and some buried in internal documentation. Team members struggle to discover each other's work, let alone reuse existing capabilities.

SkillHub solves this problem. It provides a **private, controllable, and easy-to-use** skill package registry, allowing teams to manage Agent Skills just like using npm or PyPI.

![Architecture Diagram](/diagrams/architecture.png)

## Core Value

- **Publish in 3 minutes**: From local development to global distribution with a single command
- **Enterprise-grade permissions**: Namespace-based RBAC with team collaboration and review workflows
- **Full lifecycle**: Version management, tag system, review workflows, and archiving mechanisms
- **Ready out of the box**: Start the full environment with a single curl command
- **Security scanning**: Built-in Skill Scanner for automatic security risk detection
- **Data sovereignty**: Fully self-hosted — all data stays within your firewall

## Tech Stack

![Tech Stack Diagram](/diagrams/tech-stack.png)

| Layer | Technology | Description |
|-------|------------|-------------|
| **Frontend** | React 19 + Vite + TanStack Router | Modern SPA with Chinese/English language support |
| **Backend** | Java 21 + Spring Boot 3.2 | Enterprise-grade REST API |
| **Database** | PostgreSQL 16 | Full-text search, Flyway auto-migration |
| **Cache** | Redis 7 | Session management, hot data caching |
| **Storage** | MinIO / S3 | Skill package file storage, supports local and cloud |
| **Deployment** | Docker Compose / K8s | One-click startup, supports self-hosting |

## Feature Overview

| Feature | Description |
|---------|-------------|
| [Skill Publishing & Version Management](/en/guide/skill-publish) | One-click skill package publishing with semantic versioning |
| [Skill Search & Discovery](/en/guide/skill-discovery) | Full-text search, smart filtering, permission-aware |
| [Namespace & Team Management](/en/guide/namespace) | Namespace-based RBAC permission system |
| [Review & Governance](/en/guide/review) | Multi-level review workflows, reporting system |
| [Security Scanning](/en/guide/scanner) | Built-in Skill Scanner with multi-engine security analysis |
| [User Interaction & Social](/en/guide/social) | Starring, ratings, notification system |
