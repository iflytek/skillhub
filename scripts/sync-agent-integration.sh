#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: sync-agent-integration.sh <task-slug> [source-branch...]

Merges Claude and Codex task branches into the matching integration worktree.

Arguments:
  task-slug      Required task identifier, for example legal-pages
  source-branch  Optional source branches. Defaults to:
                 agent/claude/<task-slug>
                 agent/codex/<task-slug>

Example:
  ./scripts/sync-agent-integration.sh legal-pages
  ./scripts/sync-agent-integration.sh legal-pages agent/claude/legal-pages agent/codex/legal-pages
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

require_clean_worktree() {
  local dir="$1"
  if ! git -C "$dir" diff --quiet || ! git -C "$dir" diff --cached --quiet; then
    fail "Integration worktree has uncommitted changes: $dir"
  fi
}

TASK_INPUT="${1:-}"
shift || true

if [ -z "$TASK_INPUT" ]; then
  usage >&2
  exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || fail "Run this script inside a git repository"
REPO_NAME="$(basename "$REPO_ROOT")"
TASK_SLUG="$(slugify "$TASK_INPUT")"
WORKTREE_ROOT="$(dirname "$REPO_ROOT")"
INTEGRATION_DIR="$WORKTREE_ROOT/${REPO_NAME}-integration-$TASK_SLUG"
INTEGRATION_BRANCH="agent/integration/$TASK_SLUG"

if [ ! -e "$INTEGRATION_DIR/.git" ]; then
  fail "Integration worktree not found: $INTEGRATION_DIR"
fi

CURRENT_BRANCH="$(git -C "$INTEGRATION_DIR" rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "$INTEGRATION_BRANCH" ]; then
  fail "Integration worktree is on $CURRENT_BRANCH, expected $INTEGRATION_BRANCH"
fi

require_clean_worktree "$INTEGRATION_DIR"

if [ "$#" -gt 0 ]; then
  SOURCES=("$@")
else
  SOURCES=("agent/claude/$TASK_SLUG" "agent/codex/$TASK_SLUG")
fi

for source in "${SOURCES[@]}"; do
  git rev-parse --verify --quiet "$source^{commit}" >/dev/null || fail "Source ref not found: $source"
done

for source in "${SOURCES[@]}"; do
  info "Merging $source into $INTEGRATION_BRANCH"
  if ! git -C "$INTEGRATION_DIR" merge --no-ff --no-edit "$source"; then
    echo "ERROR: Merge failed for $source. Resolve conflicts in $INTEGRATION_DIR and continue manually." >&2
    exit 1
  fi
done

cat <<EOF

Integration branch is up to date:
  Worktree: $INTEGRATION_DIR
  Branch:   $INTEGRATION_BRANCH

Next steps:
  cd "$INTEGRATION_DIR"
  make dev-all
  open http://localhost:3000
EOF
