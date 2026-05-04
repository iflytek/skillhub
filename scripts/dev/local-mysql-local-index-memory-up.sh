#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_DIR="/tmp/skillhub-logs"
PID_DIR="/tmp/skillhub-pids"

BACKEND_PID_FILE="$PID_DIR/backend-local-mysql-local-index-memory.pid"
WEB_PID_FILE="$PID_DIR/web-3000.pid"
MOCK_PID_FILE="$PID_DIR/web-3001.pid"

mkdir -p "$LOG_DIR" "$PID_DIR"

cd "$ROOT_DIR"

docker compose up -d mysql

if [ -f "$BACKEND_PID_FILE" ] && kill -0 "$(cat "$BACKEND_PID_FILE")" 2>/dev/null; then
  kill "$(cat "$BACKEND_PID_FILE")" || true
fi
if [ -f "$WEB_PID_FILE" ] && kill -0 "$(cat "$WEB_PID_FILE")" 2>/dev/null; then
  kill "$(cat "$WEB_PID_FILE")" || true
fi
if [ -f "$MOCK_PID_FILE" ] && kill -0 "$(cat "$MOCK_PID_FILE")" 2>/dev/null; then
  kill "$(cat "$MOCK_PID_FILE")" || true
fi

nohup pnpm --dir web dev --host 127.0.0.1 --port 3000 --strictPort > "$LOG_DIR/web-3000.log" 2>&1 &
echo $! > "$WEB_PID_FILE"

nohup pnpm --dir web dev --host 127.0.0.1 --port 3001 --strictPort > "$LOG_DIR/web-3001.log" 2>&1 &
echo $! > "$MOCK_PID_FILE"

nohup env \
  SKILLHUB_SEARCH_PROVIDER=local-file-index \
  SKILLHUB_RUNTIME_STATE_PROVIDER=memory \
  SPRING_PROFILES_ACTIVE=local-mysql \
  SKILLHUB_AUTH_UASS_ENABLED=true \
  SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL=http://localhost:3001 \
  java -jar server/skillhub-app/target/skillhub-app-0.1.0.jar > "$LOG_DIR/backend-local-mysql-local-index-memory.log" 2>&1 &
echo $! > "$BACKEND_PID_FILE"

echo "Waiting for backend on http://127.0.0.1:8080/actuator/health ..."
for _ in $(seq 1 120); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

curl -fsS http://127.0.0.1:8080/actuator/health
echo
echo "Web:  http://127.0.0.1:3000"
echo "Mock: http://127.0.0.1:3001/mock-uass"
