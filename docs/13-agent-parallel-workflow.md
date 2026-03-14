# Claude + Codex Parallel Workflow

This document defines the recommended way to run Claude and Codex in parallel on SkillHub without letting them overwrite each other.

## Goals

- Keep Claude and Codex isolated while they write code.
- Reserve a single browser test environment at `http://localhost:3000`.
- Make integration predictable before opening a pull request.

## Core Rule

Do not let two agents write to the same checkout at the same time.

Instead, use three sibling git worktrees per task:

- `...-claude-<task>`: Claude writes code here
- `...-codex-<task>`: Codex writes code here
- `...-integration-<task>`: merged result, browser verification, final regression

Only the integration worktree should run `make dev-all`. That keeps `http://localhost:3000` and `http://localhost:8080` as a single source of truth for the merged feature.

All worktrees share the same local Docker dependency project name, so `make dev` and `make dev-all` reuse one set of Postgres, Redis, and MinIO containers across checkouts.

## One-Time Setup Per Task

From the main repository:

```bash
make parallel-init TASK=legal-pages
```

This creates:

- `../skillhub-claude-legal-pages`
- `../skillhub-codex-legal-pages`
- `../skillhub-integration-legal-pages`

And matching local branches:

- `agent/claude/legal-pages`
- `agent/codex/legal-pages`
- `agent/integration/legal-pages`

You can override the base branch or destination root:

```bash
make parallel-init TASK=legal-pages PARALLEL_BASE_REF=origin/main PARALLEL_WORKTREE_ROOT=/Users/wowo/workspace
```

If you are iterating on this workflow itself before it lands on `origin/main`, create the worktrees from your current branch instead:

```bash
make parallel-init TASK=legal-pages PARALLEL_BASE_REF=HEAD
```

## Recommended Responsibility Split

Keep the split coarse and explicit before either agent starts:

- Claude: requirements, review, copy, UI structure, edge cases, regression review
- Codex: implementation, wiring, scripts, tests, refactors, fixes

Avoid assigning the same file to both agents. If both agents must touch one file, switch to sequential editing for that file.

## Daily Loop

### 1. Parallel implementation

Run each agent only in its assigned worktree:

- Claude works in `../skillhub-claude-<task>`
- Codex works in `../skillhub-codex-<task>`

Each agent should commit its own work before integration.

### 2. Merge into integration

From the integration worktree:

```bash
cd ../skillhub-integration-legal-pages
make parallel-up
```

This does both routine steps in one command:

- merges the default source branches into the current integration branch
- starts the integration worktree with `make dev-all`

The default source branches are:

- `agent/claude/legal-pages`
- `agent/codex/legal-pages`

If you need manual control, you can still split the flow:

```bash
cd ../skillhub-integration-legal-pages
make parallel-sync
make dev-all
```

If needed, you can override the source list on either command:

```bash
make parallel-sync SOURCES="agent/claude/legal-pages agent/codex/legal-pages"
make parallel-up SOURCES="agent/claude/legal-pages agent/codex/legal-pages"
```

If a merge conflict happens, resolve it in the integration worktree. Do not resolve it inside the Claude or Codex worktree unless you intentionally want to rewrite that branch.

### 3. Browser verification

Run the local stack only in the integration worktree:

```bash
cd ../skillhub-integration-legal-pages
make parallel-up
```

Then verify the merged result in the browser:

- Web UI: `http://localhost:3000`
- Backend: `http://localhost:8080`

When you are done:

```bash
make parallel-down
```

Legacy compatibility remains available from the main repository if needed:

```bash
make agent-sync TASK=legal-pages
make agent-verify TASK=legal-pages
```

## Validation Checklist

Before opening a PR from the integration branch:

1. Run the smallest relevant local verification first, for example `pnpm --dir web typecheck` or backend tests.
2. From the integration worktree, run `make parallel-up`.
3. Verify the final merged behavior in `http://localhost:3000`.
4. Run `make staging` if the change needs Docker-path or smoke-test confidence.

## Recovery Rules

- If one agent’s branch goes in the wrong direction, reset or discard that branch only in its own worktree.
- If integration becomes messy, recreate only the integration worktree branch from the original base and merge again.
- If ports are already in use, make sure no Claude or Codex worktree is running its own local stack.

## PR Strategy

There are two safe choices:

- Open the PR from the integration branch after merged verification passes.
- Or cherry-pick the validated commits onto a clean feature branch and open the PR from there.

For this repository, the simplest path is usually:

1. Claude branch commits
2. Codex branch commits
3. In the integration worktree, run `make parallel-up`
4. Verify on `localhost:3000`
5. Open the PR from integration

## Commands Summary

```bash
make parallel-init TASK=legal-pages
cd ../skillhub-integration-legal-pages
make parallel-up
open http://localhost:3000
make parallel-down
```
