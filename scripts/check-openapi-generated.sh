#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT_DIR/server"
WEB_DIR="$ROOT_DIR/web"
API_LOG="${TMPDIR:-/tmp}/skillhub-openapi-check.log"
SERVER_PID=""

cleanup() {
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  (cd "$ROOT_DIR" && docker compose down >/dev/null 2>&1) || true
}

trap cleanup EXIT

cd "$ROOT_DIR"
docker compose up -d --wait postgres redis

(
  cd "$SERVER_DIR"
  SPRING_PROFILES_ACTIVE=local ./mvnw -pl skillhub-app spring-boot:run
) >"$API_LOG" 2>&1 &
SERVER_PID=$!

for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:8080/v3/api-docs" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS "http://127.0.0.1:8080/v3/api-docs" >/dev/null 2>&1; then
  echo "Backend did not expose /v3/api-docs. See $API_LOG" >&2
  exit 1
fi

cd "$WEB_DIR"
pnpm run generate-api

cd "$ROOT_DIR"
git diff --exit-code -- web/src/api/generated/schema.d.ts
