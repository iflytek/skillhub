# Documentation map

Use the documentation site for user workflows, repository guides for engineering and operations, and source/tests for exact current behavior. Numbered design documents are not a chronological tutorial or a promise that every proposed feature is implemented.

## Users and authors

| Task | 中文 | English |
| --- | --- | --- |
| Install and use your first skill | [快速开始](skillhub/quickstart.md) | [Quick Start](skillhub/en/quickstart.md) |
| CLI authentication, install, upgrade and sync | [CLI](skillhub/guide/cli.md) | [CLI](skillhub/en/guide/cli.md) |
| Install and use a versioned collection | [技能套装](skillhub/guide/skill-suites.md) | [Skill Suites](skillhub/en/guide/skill-suites.md) |
| Upload a collection and member packages | [Bundle 导入](skillhub/guide/suite-bundle.md) | [Bundle import](skillhub/en/guide/suite-bundle.md) |
| Publish skills and track versions | [发布](skillhub/guide/skill-publish.md) | [Publishing](skillhub/en/guide/skill-publish.md) |
| Manage access and review | [命名空间](skillhub/guide/namespace.md) · [审核](skillhub/guide/review.md) | [Namespaces](skillhub/en/guide/namespace.md) · [Review](skillhub/en/guide/review.md) |
| Troubleshoot | [FAQ](skillhub/faq.md) | [FAQ](skillhub/en/faq.md) |

## Operators

- [Docker deployment and upgrades](skillhub/en/guide/deployment.md) / [中文](skillhub/guide/deployment.md)
- [Kubernetes](skillhub/en/guide/kubernetes.md) / [中文](skillhub/guide/kubernetes.md)
- [Deployment reference](09-deployment.md), [object storage](object-storage.md), [SMTP and password reset](19-smtp-password-reset-email-setup.md)
- [Security scanning](security-scanning.md), [observability](observability-developer-guide.md), [private SSO integration](12-private-sso-integration-playbook.md)

## Contributors and integrators

- [Contributing](../CONTRIBUTING.md), [development workflow](dev-workflow.md), [engineering specs](../.trellis/spec/index.md)
- [System architecture](01-system-architecture.md), [domain model](02-domain-model.md), [API design](06-api-design.md)
- [Skill package protocol](07-skill-protocol.md), [lifecycle contract](14-skill-lifecycle.md), [Suite semantics](25-skill-suites.md), [authoring drafts](27-authoring-drafts.md)
- [Runtime integration](skillhub/en/guide/runtime-integration.md), [OpenClaw](openclaw-integration-en.md), [Hermes](hermes-integration-en.md), [DeepSeek Harness](dsh-integration-en.md)

## Plans and historical evidence

Roadmaps, files titled `plan`, dated design proposals, research under `research/`, and test reports record decisions or evidence at a point in time. Check their implementation status against current code before treating them as supported features. Examples include the [delivery roadmap](10-delivery-roadmap.md), [starter skills plan](21-official-starter-skills-plan.md), and [observability construction plan](2026-07-31-observability-construction-plan.md).

For documentation changes, update both language journeys and navigation, verify commands against the source, then run `npm ci` and `npm run build` from `docs/skillhub`. A documentation build checks rendering and links, not deployment or live registry behavior.
