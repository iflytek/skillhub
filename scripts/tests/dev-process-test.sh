#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEV_PROCESS="$REPO_ROOT/scripts/dev-process.sh"
TMP_DIR="$(mktemp -d)"
PID_FILE="$TMP_DIR/process.pid"
LOG_FILE="$TMP_DIR/process.log"

cleanup() {
  bash "$DEV_PROCESS" stop --pid-file "$PID_FILE" >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "0" >"$PID_FILE"
if bash "$DEV_PROCESS" status --pid-file "$PID_FILE" >/dev/null 2>&1; then
  echo "expected PID 0 to be treated as not running" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  echo "expected invalid PID file to be removed" >&2
  exit 1
fi

bash "$DEV_PROCESS" start \
  --pid-file "$PID_FILE" \
  --log-file "$LOG_FILE" \
  --cwd "$REPO_ROOT" \
  -- /bin/sh -lc 'sleep 30'

bash "$DEV_PROCESS" status --pid-file "$PID_FILE" >/dev/null

bash "$DEV_PROCESS" stop --pid-file "$PID_FILE"

if [[ -f "$PID_FILE" ]]; then
  echo "expected PID file to be removed after stop" >&2
  exit 1
fi
