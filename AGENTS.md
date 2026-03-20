# Repository Guidelines

## Project Structure & Module Organization
`server/` contains the Java 21 Spring Boot backend as a multi-module Maven project: `skillhub-app`, `skillhub-domain`, `skillhub-auth`, `skillhub-search`, `skillhub-storage`, and `skillhub-infra`. `web/` is the React 19 + Vite frontend. `document/` hosts the Docusaurus documentation site, while `docs/` stores architecture notes, workflow guides, and product decisions. Deployment and ops assets live under `deploy/`, `monitoring/`, and `scripts/`.

## Build, Test, and Development Commands
Use the root `Makefile` as the default entry point.

- `make dev-all`: start Postgres, Redis, MinIO, the backend, and the Vite frontend.
- `make test`: run backend Maven tests and frontend Vitest suites.
- `make typecheck-web` / `make lint-web`: run TypeScript checks and ESLint for `web/`.
- `make build` or `make staging`: build everything; `make staging` also runs smoke validation in a Docker-like flow.
- `make generate-api`: regenerate `web/src/api/generated/schema.d.ts` after backend API changes.

## Coding Style & Naming Conventions
Follow the style already used in each module. Frontend code uses TypeScript, 2-space indentation, no semicolons, and ESLint from `web/.eslintrc.cjs`. Keep shared UI in `web/src/shared/ui/`; use PascalCase for React component symbols and lowercase or kebab-style filenames for feature files and tests such as `skill-delete-flow.test.ts`. Backend code uses standard Java formatting with 4-space indentation, `PascalCase` class names, and package paths under `com.iflytek.skillhub.*`.

## Testing Guidelines
Frontend tests use Vitest and typically live beside the feature area or under `web/test/`, with filenames ending in `.test.ts` or `.test.tsx`. Backend tests use JUnit under `src/test/java`, usually with `*Test.java` names. Add or update tests for behavior changes, and run the smallest relevant target first, then `make test` before opening a PR.

## Commit & Pull Request Guidelines
Use Conventional Commit style visible in recent history, for example `fix(web): restore clearable publish namespace select` or `test(review): stabilize skill detail i18n assertions`. Git commit authors must use a real user or team identity and must not contain model or tool CLI names such as `Claude Code`, `Codex`, or `Gemini`. Keep PRs focused, describe motivation and rollout impact, and link the relevant issue when applicable. Include screenshots for UI changes, note API or config changes explicitly, and mention if regenerated OpenAPI artifacts are part of the diff.

## Security & Configuration Tips
Do not commit secrets. Respect `.gitignore` and do not stage local-only or generated artifacts unless the repository explicitly tracks them; common examples here include `.env*`, `.dev/`, `node_modules/`, and `docs/superpowers/`. Start from `.env.release.example` and `deploy/k8s/secret.yaml.example` for deployable config. Report security issues privately rather than through public issues.
