#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./parallel-common.sh
source "$SCRIPT_DIR/parallel-common.sh"

usage() {
  cat <<'EOF'
Usage: parallel-sync.sh [task-slug] [source-branch...]

Merges Claude and Codex task branches into the matching integration worktree.

Arguments:
  task-slug      Optional task identifier, for example legal-pages.
                 When omitted, infer it from the current integration branch.
  source-branch  Optional source branches. Defaults to:
                 agent/claude/<task-slug>
                 agent/codex/<task-slug>

Example:
  ./scripts/parallel-sync.sh legal-pages
  cd ../skillhub-integration-legal-pages && ./scripts/parallel-sync.sh
  ./scripts/parallel-sync.sh legal-pages agent/claude/legal-pages agent/codex/legal-pages

Environment:
  PARALLEL_WORKTREE_ROOT  Optional parent directory for parallel worktrees
EOF
}

require_clean_worktree() {
  local dir="$1"
  if ! git -C "$dir" diff --quiet || ! git -C "$dir" diff --cached --quiet; then
    fail "Integration worktree has uncommitted changes: $dir"
  fi
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

TASK_INPUT="${1:-}"
if [ -n "$TASK_INPUT" ] && [[ "$TASK_INPUT" != */* ]]; then
  shift
  TASK_SLUG="$(slugify "$TASK_INPUT")"
else
  TASK_SLUG="$(require_integration_task)"
fi

REPO_ROOT="$(repo_root)"
INTEGRATION_DIR="$(integration_dir_for_task "$TASK_SLUG")"
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
