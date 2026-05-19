---
name: pr-submission
description: PR title format, commit conventions, and pre-PR checklist for SkillHub. Use when preparing or reviewing pull requests.
license: Apache-2.0
---

# PR Submission Skill

## Workflow

1. Identify the scope of your change (feature, bug fix, docs, test, refactor, chore)
2. Format PR title and commits using the conventions below
3. Run the pre-PR checklist commands
4. Open the PR with a descriptive body

## PR Title Format

Use conventional commit style:

```
<type>(<scope>): <description>
```

**Types:**

| Type | When to Use |
|------|-------------|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `docs` | Documentation changes only |
| `test` | Adding or updating tests |
| `refactor` | Code restructuring with no behavior change |
| `chore` | Build, CI, tooling, or maintenance tasks |

**Scopes:** Use module or domain names: `auth`, `search`, `publish`, `review`, `namespace`, `governance`, `deploy`, `ci`, `frontend`, `scanner`

**Examples:**
```
feat(auth): add local account login with password reset
fix(publish): resolve null pointer when skill metadata is missing name
docs(deploy): clarify runtime image usage
test(namespace): add membership service edge case tests
refactor(review): extract query repository for governance list
chore(ci): add parallel workflow scripts for multi-agent development
```

## Commit Message Format

Same convention as PR titles. One logical change per commit.

**Types:**

- **feat**: A new feature for the user
- **fix**: A bug fix for the user
- **docs**: Documentation changes only
- **test**: Adding or updating tests
- **refactor**: Code change that neither fixes a bug nor adds a feature
- **chore**: Changes to build process, CI, or maintenance tasks

**Examples:**
```
fix(auth): resolve session cookie conflict in device flow
feat(publish): support security scan before review submission
docs(skill-protocol): add nested SKILL.md discovery rules
test(search): verify jieba analysis with Chinese skill descriptions
refactor(storage): simplify LocalFile path normalization
```

## Pre-PR Checklist

- [ ] Backend tests pass: `make test-backend-app`
- [ ] Frontend typecheck passes: `make typecheck-web`
- [ ] If API changed: `make generate-api` was run and `web/src/api/generated/schema.d.ts` is committed
- [ ] Smoke test passes: `make staging`
- [ ] Follow existing module boundaries and dependency direction
- [ ] Add/update tests for new behavior
- [ ] Update design docs when APIs, auth flows, deployment, or operator workflows change

## PR Body Structure

When creating a PR, include:

1. **What** — Summary of the change
2. **Why** — Motivation (link to issue if applicable)
3. **How** — Key implementation details (especially for non-obvious decisions)
4. **Testing** — How to verify the change works
5. **Impact** — Breaking changes, migration notes, or rollout considerations

## Review Conventions

- When reviewing, cite the specific AGENTS.md rule that applies if suggesting a convention change
- For backend code, check dependency direction does not violate clean architecture rules
- For frontend code, check OpenAPI types are regenerated if API changed
