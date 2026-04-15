#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./parallel-common.sh
source "$SCRIPT_DIR/parallel-common.sh"

usage() {
  cat <<'EOF'
Usage: parallel-up.sh [task-slug] [source-branch...]

Syncs Claude and Codex into the integration worktree, then starts the local dev
Docker Compose stack (`compose.dev.yml`).

Arguments:
  task-slug      Optional task identifier. When omitted, infer it from the
                 current integration branch.
  source-branch  Optional source branches passed through to parallel-sync.sh

Examples:
  cd ../skillhub-integration-legal-pages && ./scripts/parallel-up.sh
  ./scripts/parallel-up.sh legal-pages
EOF
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

if [ ! -e "$INTEGRATION_DIR/.git" ]; then
  fail "Integration worktree not found: $INTEGRATION_DIR"
fi

PARALLEL_WORKTREE_ROOT="$(worktree_root "$REPO_ROOT")" "$REPO_ROOT/scripts/parallel-sync.sh" "$TASK_SLUG" "$@"
info "Starting integration stack in $INTEGRATION_DIR"
COMPOSE_ARGS="$(compose_dev_args "$INTEGRATION_DIR")"
(
  cd "$INTEGRATION_DIR"
  # shellcheck disable=SC2086
  docker compose $COMPOSE_ARGS -f compose.dev.yml up -d --build
)

cat <<EOF

Integration environment is running:
  Worktree: $INTEGRATION_DIR
  Web UI:   http://localhost:3000
  Backend:  http://localhost:8080

Next steps:
  1. Open http://localhost:3000
  2. Verify the merged behavior
  3. Stop when done:
       cd "$INTEGRATION_DIR" && ./scripts/parallel-down.sh
EOF
