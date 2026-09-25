#!/usr/bin/env bash

# RISC-V64 live verification of the authoring platform.
#
# Boots the linux/riscv64 server image (native on riscv64 hosts, QEMU-emulated
# elsewhere) against a dedicated PostgreSQL database and drives the full
# authoring flow over the REST API:
#   create draft → read scaffold → save script + validation.yaml → bind runtime
#   → validate → SUCCEEDED → script stdout visible in the event stream.
#
# The script assumes:
#   - the image exists (see "Building the image" below);
#   - PostgreSQL and Redis are reachable from the container (defaults use
#     host.docker.internal on ports 5432/6379);
#   - the DB given by SKILLHUB_RISCV_DB is EMPTY — Flyway migrates it from zero
#     (the script never touches your main dev database).
#
# Usage:
#   scripts/riscv64-verify.sh [image]        # default skillhub-server:riscv64
#   SKILLHUB_RISCV_DB=mydb scripts/riscv64-verify.sh myimage:tag
#
# Building the image (on any host; buildx + QEMU required on non-riscv64):
#   cd server && docker buildx build --platform linux/riscv64 \
#     -t skillhub-server:riscv64 --load .
#
# Native hardware note: on a real riscv64 host (uname -m == riscv64) the same
# script runs without emulation; the log line marks which mode you are in.
# QEMU mode proves architecture independence of the JVM workload, native mode
# additionally proves the platform services' riscv64 images.

set -u

IMAGE="${1:-skillhub-server:riscv64}"
DB_NAME="${SKILLHUB_RISCV_DB:-skillhub_riscv_check}"
HOST="${SKILLHUB_RISCV_DB_HOST:-host.docker.internal}"
CONTAINER="skillhub-riscv-check"
BASE_URL="http://localhost:18082"
COOKIE="$(mktemp)"
PASS=0
FAIL=0

pass() { echo "PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "FAIL: $1"; FAIL=$((FAIL + 1)); }

json_field() {
  JSON_INPUT="$1" python3 - "$2" <<'PY'
import json, os, sys
expr = sys.argv[1]
value = json.loads(os.environ["JSON_INPUT"])
for part in expr.split('.'):
    value = value[int(part)] if part.isdigit() else value[part]
print(json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else value)
PY
}

csrf_token() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE" | tail -n 1; }
bootstrap_csrf() {
  curl -s -c "$COOKIE" -H "X-Mock-User-Id: local-user" "$BASE_URL/api/v1/auth/providers" >/dev/null
}
api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -s -b "$COOKIE" -c "$COOKIE" -X "$method" -H "Content-Type: application/json" \
      -H "X-Mock-User-Id: local-user" -H "X-XSRF-TOKEN: $(csrf_token)" -d "$body" "$BASE_URL$path"
  else
    curl -s -b "$COOKIE" -c "$COOKIE" -X "$method" \
      -H "X-Mock-User-Id: local-user" -H "X-XSRF-TOKEN: $(csrf_token)" "$BASE_URL$path"
  fi
}

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -f "$COOKIE"
}
trap cleanup EXIT

echo "=== RISC-V64 authoring platform verification ==="
echo "image: $IMAGE"
echo "database: $DB_NAME on $HOST"
ARCH="$(uname -m)"
if [[ "$ARCH" == "riscv64" ]]; then
  echo "mode: NATIVE riscv64 host"
else
  echo "mode: QEMU emulation (host arch $ARCH)"
fi
echo

echo "--- 1. boot the linux/riscv64 image ---"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" -p 18082:8080 "$IMAGE" \
  --spring.profiles.active=local \
  --spring.datasource.url="jdbc:postgresql://$HOST:5432/$DB_NAME" \
  --spring.data.redis.host="$HOST" >/dev/null || { echo "docker run failed"; exit 1; }

IMAGE_ARCH="$(docker inspect "$CONTAINER" --format '{{.Image}}' >/dev/null 2>&1 && docker exec "$CONTAINER" uname -m 2>/dev/null || echo unknown)"
echo "arch inside container: $IMAGE_ARCH"
if [[ "$IMAGE_ARCH" == "riscv64" ]]; then
  pass "container userland is riscv64"
else
  fail "container userland is '$IMAGE_ARCH', expected riscv64"
fi

HEALTHY=0
for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health" 2>/dev/null || true)
  if [[ "$code" == "200" ]]; then
    echo "health 200 after ~$((i * 10))s"
    HEALTHY=1
    break
  fi
  if ! docker ps --format '{{.Names}}' | grep -q "^$CONTAINER$"; then
    echo "CONTAINER DIED — last logs:"
    docker logs "$CONTAINER" 2>&1 | tail -30
    exit 1
  fi
  sleep 10
done
if [[ "$HEALTHY" == "1" ]]; then
  pass "server boots and becomes healthy"
else
  fail "server did not become healthy in 900s"
  docker logs "$CONTAINER" 2>&1 | tail -30
  exit 1
fi

echo "--- 2. authoring flow over the API ---"
bootstrap_csrf
RUN_TAG="$(date +%s)"
DRAFT="$(api POST /api/web/authoring/drafts \
  "{\"namespaceSlug\":\"global\",\"name\":\"riscv-verify-$RUN_TAG\",\"description\":\"verifies the authoring platform on riscv64\"}")"
if [[ "$(json_field "$DRAFT" "code")" == "0" ]]; then
  pass "draft created"
else
  fail "draft create: $DRAFT"
  exit 1
fi
DRAFT_ID="$(json_field "$DRAFT" "data.id")"

SCAFFOLD="$(api GET "/api/web/authoring/drafts/$DRAFT_ID/files/content?path=SKILL.md")"
if echo "$SCAFFOLD" | grep -q "name: riscv-verify-$RUN_TAG"; then
  pass "scaffold SKILL.md persisted and readable"
else
  fail "scaffold read: $SCAFFOLD"
fi

api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  '{"path":"scripts/check.sh","content":"#!/bin/sh\necho riscv-script-OK\n","contentType":"text/x-shellscript"}' >/dev/null
api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  '{"path":"validation.yaml","content":"version: 1\ntasks:\n  - name: smoke\n    description: script runs on riscv64\n    type: script\n    script: scripts/check.sh\n    args: []\n    timeoutMs: 60000\n    assertions:\n      - type: exit_code\n        equals: 0\n      - type: stdout_contains\n        value: riscv-script-OK\n"}' >/dev/null

BINDING="$(api PUT "/api/web/authoring/drafts/$DRAFT_ID/runtime" \
  '{"agentType":"local-script","config":{"interpreter":"sh"},"toolAllowlist":[],"mcpServers":[]}')"
if [[ "$(json_field "$BINDING" "code")" == "0" ]]; then
  pass "runtime binding saved"
else
  fail "binding: $BINDING"
fi

RUN="$(api POST "/api/web/authoring/drafts/$DRAFT_ID/runs" '{}')"
RUN_ID="$(json_field "$RUN" "data.id")"

STATUS=""
for _ in $(seq 1 60); do
  STATUS="$(json_field "$(api GET "/api/web/authoring/runs/$RUN_ID")" "data.status")"
  case "$STATUS" in
    SUCCEEDED|FAILED|CANCELLED|TIMED_OUT) break ;;
  esac
  sleep 5
done
if [[ "$STATUS" == "SUCCEEDED" ]]; then
  pass "validation run SUCCEEDED (script executed in the container's riscv64 userland)"
else
  fail "validation run ended as $STATUS"
fi

EVENTS="$(api GET "/api/web/authoring/runs/$RUN_ID/events")"
EVENT_COUNT="$(JSON_INPUT="$EVENTS" python3 -c '
import json, os
print(len(json.loads(os.environ["JSON_INPUT"])["data"]))' 2>/dev/null || echo 0)"
if echo "$EVENTS" | grep -q "riscv-script-OK"; then
  pass "script stdout present in the event stream ($EVENT_COUNT events)"
else
  fail "script stdout missing from the event stream"
fi

echo
if [[ "$FAIL" -eq 0 ]]; then
  echo "=== All $PASS checks passed ==="
  exit 0
fi
echo "=== $FAIL check(s) failed, $PASS passed ==="
exit 1
