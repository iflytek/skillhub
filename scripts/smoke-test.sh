#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
COOKIE_JAR="$(mktemp)"

cleanup() {
  rm -f "$COOKIE_JAR"
}

trap cleanup EXIT

check() {
  local desc="$1"
  local url="$2"
  local expected="$3"
  local status
  status="$(curl -s -o /dev/null -w "%{http_code}" "$url")"
  if [[ "$status" == "$expected" ]]; then
    echo "PASS: $desc (HTTP $status)"
    PASS=$((PASS + 1))
  else
    echo "FAIL: $desc (expected $expected, got $status)"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== SkillHub Smoke Test ==="
echo "Target: $BASE_URL"
echo

check "Health endpoint" "$BASE_URL/actuator/health" "200"
check "Prometheus metrics" "$BASE_URL/actuator/prometheus" "200"
check "Namespaces API" "$BASE_URL/api/v1/namespaces" "200"
check "Auth required" "$BASE_URL/api/v1/auth/me" "401"

curl -s -c "$COOKIE_JAR" "$BASE_URL/api/v1/auth/me" >/dev/null
CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_JAR" | tail -n 1)"

REGISTER_STATUS="$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/local/register" \
  -b "$COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"smoketest","password":"Smoke@2026","email":"smoketest@example.com"}')"
if [[ "$REGISTER_STATUS" == "200" || "$REGISTER_STATUS" == "409" ]]; then
  echo "PASS: Register (HTTP $REGISTER_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Register (got $REGISTER_STATUS)"
  FAIL=$((FAIL + 1))
fi

echo
echo "Results: $PASS passed, $FAIL failed"
if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
