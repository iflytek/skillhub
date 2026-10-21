# Contributing to SkillHub

## Scope

SkillHub is a self-hosted registry for agent skills. Contributions should
preserve the existing architecture and product direction documented in
[`docs/`](./docs).

AI coding agents working in this repository should follow the rules in
[`AGENTS.md`](./AGENTS.md), which routes to the engineering conventions in
[`.trellis/spec/index.md`](./.trellis/spec/index.md). These documents can be read
without installing Trellis or any engineering Skill. Architecture and business
design remain in `docs/`; execution tools and task records remain local.

## Before You Start

- Read [`README.md`](./README.md) for local development commands.
- Check the relevant design docs before changing behavior.
- Open an issue for non-trivial changes before sending a large pull request.

## Development Setup

Prerequisites:

- Docker and Docker Compose
- Java 21
- For the web frontend: Node.js 22.13+ (22.x, default in `.nvmrc`) or 24.x, and the pnpm version declared in `web/package.json`. Other Node.js majors are not supported for frontend development (see #991).

Use `nvm install && nvm use` from the repository root, then enable Corepack (`corepack enable`). Frontend dependency installation enforces this Node.js range via `web/.npmrc`; CI builds and tests both supported majors. CLI and integration packages retain their own runtime requirements.

Start the local stack:

```bash
make dev-all
```

Useful commands:

```bash
make test
make typecheck-web
make build-web
make generate-api
./scripts/check-openapi-generated.sh
./scripts/smoke-test.sh
```

Stop the stack:

```bash
make dev-all-down
```

## Change Guidelines

- Keep changes focused. Avoid mixing refactors with behavior changes.
- Follow existing module boundaries across `server/`, `web/`, and `docs/`.
- Add or update tests when behavior changes.
- Update docs when APIs, auth flows, deployment, or operator workflows change.
- Regenerate and commit `web/src/api/generated/schema.d.ts` when backend OpenAPI
  contracts change.
- Prefer backward-compatible changes unless the issue explicitly allows a break.

## Pull Requests

Before opening a pull request, make sure:

- The branch is rebased or merged cleanly from the target branch.
- Relevant backend tests pass.
- Frontend checks follow the [change-impact matrix](.trellis/spec/testing/index.md).
  Changes limited to existing translation display text use its narrow exception;
  frontend behavior or tooling changes require the applicable typecheck/build checks.
  This does not waive required CI checks or failures in relevant existing tests.
- `make generate-api` or `./scripts/check-openapi-generated.sh` has been run when
  backend API contracts changed.
- Smoke coverage is updated when operator-facing workflows change.
- The pull request description explains motivation, scope, and rollout impact.
- Every commit is signed off and the CLA is signed (see below).

## Commit Style

Conventional-style subjects are preferred, for example:

- `feat(auth): add local account login`
- `fix(ops): align smoke test with csrf flow`
- `docs(deploy): clarify runtime image usage`

## Sign-off and CLA

Two checks must pass before a pull request can be merged:

- **DCO**: every commit needs a `Signed-off-by` trailer that matches the commit
  author, certifying the [Developer Certificate of Origin](https://developercertificate.org/).
  Use `git commit -s` when committing. To sign off commits you already pushed,
  run `git commit --amend -s --no-edit` for the last commit, or
  `git rebase --signoff <base>` for several, then `git push --force-with-lease`.
- **CLA**: sign the Contributor License Agreement once through the
  `license/cla` link that the CLA assistant posts on your first pull request.

Both are your own attestations, so maintainers cannot complete them for you.
Workflow runs on a first-time contributor's pull request also wait for a
maintainer to approve them; that pending state is not a test failure.

## Reporting Security Issues

Do not open public issues for suspected security vulnerabilities.

Use GitHub Security Advisories or your internal security process to report them
privately to the maintainers.
