# Introduction

SkillHub is an enterprise self-hosted Agent Skill registry for publishing, discovering, governing, and reusing skill packages in one place.

In many teams, skill packages are scattered across local folders, Git repositories, and internal docs. That usually means:

- people struggle to discover existing work
- it is hard to tell which version is usable and trustworthy
- permissions and governance stay inconsistent

SkillHub is designed to pull those concerns into a private, controllable, and auditable platform.

![Architecture Diagram](/diagrams/architecture.png)

## Core Value

- Private deployment with strong data sovereignty
- Namespace + RBAC collaboration model
- Full lifecycle from publishing to review and scanning
- Both Web and CLI entry points

## Technology Baseline

| Layer | Technology |
|-------|------------|
| Frontend | React 19 + Vite |
| Backend | Java 17 + Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Storage | MinIO / S3 |
| Deployment | Docker Compose / Kubernetes |

## Next Steps

- [Quick Start](/en/quickstart)
- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Skill Search & Discovery](/en/guide/skill-discovery)
- [Namespace & Team Management](/en/guide/namespace)
