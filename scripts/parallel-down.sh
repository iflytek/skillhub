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
REPO_ROOT="$(repo_root)"

info "Stopping integration stack for task $TASK_SLUG in $REPO_ROOT"
make -C "$REPO_ROOT" dev-all-down
