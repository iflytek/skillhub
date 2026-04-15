#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./parallel-common.sh
source "$SCRIPT_DIR/parallel-common.sh"

usage() {
  cat <<'EOF'
Usage: parallel-down.sh

Stops the integration worktree development stack.

Run this inside an integration worktree on branch agent/integration/<task>.
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

TASK_SLUG="$(require_integration_task)"
INTEGRATION_DIR="$(integration_dir_for_task "$TASK_SLUG")"

if [ ! -e "$INTEGRATION_DIR/.git" ]; then
  fail "Integration worktree not found: $INTEGRATION_DIR"
fi

info "Stopping integration stack for task $TASK_SLUG in $INTEGRATION_DIR"
COMPOSE_ARGS="$(compose_dev_args "$INTEGRATION_DIR")"
(
  cd "$INTEGRATION_DIR"
  # shellcheck disable=SC2086
  docker compose $COMPOSE_ARGS -f compose.dev.yml down --remove-orphans
)
