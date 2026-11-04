# Documentation map

Select the required task. [Quick Start](../quickstart.html) covers the first installation; deployment and development guides are not prerequisites.

## Start

- [Accounts and access tokens](./account-access.html): web sign-in, CLI authorization, token expiry, and password reset.
- [Introduction](../introduction.html): understand the registry's purpose.
- [Quick start](../quickstart.html): find, install, and use a skill in the target Agent.
- [FAQ](../faq.html): troubleshoot login, installation, and configuration.

## Use skills

| Task | Read |
| --- | --- |
| Find a skill and inspect its versions and visibility | [Find and install skills](./skill-discovery.html) |
| Install pinned members together and start with their entry skill | [Manage skill suites](./skill-suites.html) |
| Choose an Agent and scope; update, remove, or sync a workspace | [CLI reference](./cli.html) |
| Save and rate skills, and check notifications | [Stars, ratings and notifications](./social.html) |

## Author skills

Prepare `SKILL.md` and its supporting files, then submit. An accepted submission still needs its scan, review, and final version state checked.

- [Publish and update skills](./skill-publish.html): package structure, metadata, uploading, and version management.
- [Import a bundle](./suite-bundle.html): upload content as an author, inspect member changes, and confirm. To install an existing suite, use the guide above.

## Team governance

- [Manage namespaces](./namespace.html): members, roles, and resource visibility.
- [Review submissions](./review.html): handle Skill and Suite applications.
- [Configure scanning](./scanner.html): connect the scanning service and interpret results.
- [Skill avatars and moderation](./skill-avatars.html): skill-owner submissions, authorized review, and moderation policy managed by super administrators.

## Deploy and operate

These guides apply to self-hosted registry operation. Select a deployment method before configuring optional integrations.

- [Deploy with Docker](./deployment.html): installation, configuration, upgrades, and backups.
- [Kubernetes deployment (Kustomize / Helm)](./kubernetes.html): choose either the manifest path or the separate Chart path.
- [Frontend analytics](./frontend-analytics.html): optional configuration-based integration, consent, and failure checks; match the guide to the deployed version.
- [Configure avatar machine moderation](./avatar-moderation-configuration.html): provider selection, private credentials, service updates, and actual-call acceptance for deployment operators.

## Develop and integrate

- [Integrate a runtime](./runtime-integration.html): registry API contracts for external runtimes.
- [Development handbook (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md): local development and repository checks.
