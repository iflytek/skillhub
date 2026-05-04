# FAQ

This page keeps only high-frequency questions. Detailed procedures, deployment flows, and feature walkthroughs live on dedicated guide pages instead of being duplicated here.

## Q: What is the difference between SkillHub and ClawHub?

A: SkillHub is the enterprise self-hosted option focused on data sovereignty, namespace governance, review workflows, and security scanning. ClawHub is closer to a public registry.

## Q: How do I back up data?

A: Back up two things first:

- MySQL
- Object storage such as MinIO or S3

For production, make backups part of your deployment and operations workflow.

## Q: What authentication methods are supported?

A: The main path currently includes:

- OAuth2
- Local username/password accounts
- Extensible enterprise SSO integration

The repository-level auth and SSO design docs cover the detailed model.

## Q: Is there a size limit for skill packages?

A: The default limit is `100MB`. Actual behavior depends on backend multipart settings and publish policy.

## Q: How do I configure HTTPS?

A: For production, use Nginx or Traefik as a reverse proxy. See:

- [Kubernetes Deployment](/en/guide/kubernetes)
- Repository deployment documentation

## Q: How do I monitor SkillHub?

A: Common entry points include:

- `GET /actuator/health`
- `GET /actuator/metrics`
- Security scanner health checks
- Audit logs and application logs

## Q: Does it support multi-tenancy?

A: SkillHub uses namespaces for logical isolation. Each namespace has its own members, permissions, and skill packages.

## Q: How do I upgrade SkillHub?

A: See [Quick Start](/en/quickstart) and the deployment docs for upgrade steps, runtime parameters, and rollback preparation. Back up the database and object storage before upgrading.

## Q: How do I search, install, or publish skill packages?

A: Start with:

- [Quick Start](/en/quickstart)
- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Skill Search & Discovery](/en/guide/skill-discovery)
- [Namespace & Team Management](/en/guide/namespace)

## Q: What should I do if local development fails to start?

A: Start with the “Local Development” section in [Quick Start](/en/quickstart). If that does not resolve the issue, move on to the repository-level developer docs and backend logs.

## Q: What should I do if I run into issues?

A: Use:

- GitHub Issues
- GitHub Discussions
- The repository root `README.md`
