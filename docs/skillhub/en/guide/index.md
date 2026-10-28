# Documentation map

Choose the task you want to complete. Start with [Quick Start](../quickstart.md) to install your first skill; deployment and development guides are not prerequisites.

## Start

- [Accounts and access tokens](./account-access.md): web sign-in, CLI authorization, token expiry, and password reset.
- [Introduction](../introduction.md): understand the registry's purpose.
- [Quick start](../quickstart.md): find, install, and use a skill in your Agent.
- [FAQ](../faq.md): troubleshoot login, installation, and configuration.

## Use skills

| Your task | Read |
| --- | --- |
| Find a skill and inspect its versions and visibility | [Find and install skills](./skill-discovery.md) |
| Install pinned members together and start with their entry skill | [Manage skill suites](./skill-suites.md) |
| Choose an Agent and scope; update, remove, or sync a workspace | [CLI reference](./cli.md) |
| Save and rate skills, and check notifications | [Stars, ratings and notifications](./social.md) |

## Author skills

Prepare `SKILL.md` and its supporting files, then submit. An accepted submission still needs its scan, review, and final version state checked.

- [Publish and update skills](./skill-publish.md): package structure, metadata, uploading, and version management.
- [Import a bundle](./suite-bundle.md): upload content as an author, inspect member changes, and confirm. To install an existing suite, use the guide above.

## Team governance

- [Manage namespaces](./namespace.md): members, roles, and resource visibility.
- [Review submissions](./review.md): handle Skill and Suite applications.
- [Configure scanning](./scanner.md): connect the scanning service and interpret results.

## Deploy and operate

Read these guides when you need to run your own registry. Choose a deployment method before configuring optional integrations.

- [Deploy with Docker](./deployment.md): installation, configuration, upgrades, and backups.
- [Deploy on Kubernetes](./kubernetes.md): cluster and Helm deployment.
- [Skill avatars and moderation](./skill-avatars.md): administrator configuration for image moderation providers.

## Develop and integrate

- [Integrate a runtime](./runtime-integration.md): registry API contracts for external runtimes.
- [Development handbook (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md): local development and repository checks.
