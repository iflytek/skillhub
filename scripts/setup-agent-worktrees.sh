#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: setup-agent-worktrees.sh <task-slug> [base-ref] [worktree-root]

Creates three sibling git worktrees for parallel Claude + Codex development:
  - <repo>-claude-<task-slug>
  - <repo>-codex-<task-slug>
  - <repo>-integration-<task-slug>

Arguments:
  task-slug      Required task identifier, for example legal-pages
  base-ref       Optional git ref to branch from (default: origin/main)
  worktree-root  Optional parent directory for new worktrees

Example:
  ./scripts/setup-agent-worktrees.sh legal-pages origin/main /Users/me/workspace
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

info() {
  echo "INFO: $*"
}

slugify() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'
}

branch_in_use() {
  local branch="$1"
  git worktree list --porcelain | awk '/^branch / { print $2 }' | grep -Fxq "refs/heads/$branch"
}

ensure_worktree() {
  local label="$1"
  local branch="$2"
  local dir="$3"
  local base_ref="$4"

  if [ -e "$dir/.git" ]; then
    info "$label worktree already exists at $dir"
    return 0
  fi

  if [ -e "$dir" ] && [ ! -e "$dir/.git" ]; then
    fail "$label directory already exists but is not a git worktree: $dir"
  fi

  if branch_in_use "$branch"; then
    fail "$branch is already checked out in another worktree"
  fi

  if git show-ref --verify --quiet "refs/heads/$branch"; then
    info "Adding existing $label branch $branch at $dir"
    git worktree add "$dir" "$branch"
  else
    info "Creating $label branch $branch from $base_ref at $dir"
    git worktree add -b "$branch" "$dir" "$base_ref"
  fi
}

TASK_INPUT="${1:-}"
BASE_REF="${2:-origin/main}"
WORKTREE_ROOT="${3:-}"

if [ -z "$TASK_INPUT" ]; then
  usage >&2
  exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || fail "Run this script inside a git repository"
REPO_NAME="$(basename "$REPO_ROOT")"
TASK_SLUG="$(slugify "$TASK_INPUT")"

if [ -z "$TASK_SLUG" ]; then
  fail "Task slug must contain at least one letter or number"
fi

if [ -z "$WORKTREE_ROOT" ]; then
  WORKTREE_ROOT="$(dirname "$REPO_ROOT")"
fi

git rev-parse --verify --quiet "$BASE_REF^{commit}" >/dev/null || fail "Base ref not found: $BASE_REF"

CLAUDE_BRANCH="agent/claude/$TASK_SLUG"
CODEX_BRANCH="agent/codex/$TASK_SLUG"
INTEGRATION_BRANCH="agent/integration/$TASK_SLUG"

CLAUDE_DIR="$WORKTREE_ROOT/${REPO_NAME}-claude-$TASK_SLUG"
CODEX_DIR="$WORKTREE_ROOT/${REPO_NAME}-codex-$TASK_SLUG"
INTEGRATION_DIR="$WORKTREE_ROOT/${REPO_NAME}-integration-$TASK_SLUG"

ensure_worktree "Claude" "$CLAUDE_BRANCH" "$CLAUDE_DIR" "$BASE_REF"
ensure_worktree "Codex" "$CODEX_BRANCH" "$CODEX_DIR" "$BASE_REF"
ensure_worktree "integration" "$INTEGRATION_BRANCH" "$INTEGRATION_DIR" "$BASE_REF"

cat <<EOF

Parallel worktrees are ready:
  Claude:      $CLAUDE_DIR
               branch $CLAUDE_BRANCH
  Codex:       $CODEX_DIR
               branch $CODEX_BRANCH
  Integration: $INTEGRATION_DIR
               branch $INTEGRATION_BRANCH

Recommended flow:
  1. Run Claude only in: $CLAUDE_DIR
  2. Run Codex only in:  $CODEX_DIR
  3. Keep localhost:3000 reserved for the integration worktree:
       cd "$INTEGRATION_DIR" && make dev-all
  4. After each agent commits, sync both branches into integration:
       make agent-sync TASK=$TASK_SLUG
  5. Verify the merged result in the browser:
       http://localhost:3000
EOF
