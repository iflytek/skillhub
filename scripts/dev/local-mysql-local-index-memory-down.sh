#!/usr/bin/env bash
set -euo pipefail

PID_DIR="/tmp/skillhub-pids"

BACKEND_PID_FILE="$PID_DIR/backend-local-mysql-local-index-memory.pid"
WEB_PID_FILE="$PID_DIR/web-3000.pid"
MOCK_PID_FILE="$PID_DIR/web-3001.pid"

for file in "$BACKEND_PID_FILE" "$WEB_PID_FILE" "$MOCK_PID_FILE"; do
  if [ -f "$file" ]; then
    pid="$(cat "$file")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" || true
    fi
    rm -f "$file"
  fi
done

docker compose stop mysql >/dev/null 2>&1 || true

echo "Stopped local mysql + local-file-index + memory runtime."
