#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
COOKIE_JAR="$(mktemp)"
USERNAME="smoketest_$(date +%s)"
EMAIL="${USERNAME}@example.com"
PASSWORD="Smoke@2026"
NEW_PASSWORD="Smoke@2027"

cleanup() {
  rm -f "$COOKIE_JAR"
}

trap cleanup EXIT

check() {
  local desc="$1"
  local url="$2"
  local expected="$3"
  local status
  status="$(curl --retry 3 --retry-delay 1 --max-time 10 -s -o /dev/null -w "%{http_code}" "$url" || true)"
  if [[ "$status" == "$expected" ]]; then
    echo "PASS: $desc (HTTP $status)"
    PASS=$((PASS + 1))
  else
    echo "FAIL: $desc (expected $expected, got $status)"
    FAIL=$((FAIL + 1))
  fi
}

finish() {
  echo
  echo "Results: $PASS passed, $FAIL failed"
  [[ "$FAIL" -eq 0 ]]
}

echo "=== SkillHub Smoke Test ==="
echo "Target: $BASE_URL"
echo

check "Health endpoint" "$BASE_URL/actuator/health" "200"
check "Prometheus metrics requires auth" "$BASE_URL/actuator/prometheus" "401"
check "Namespaces API requires auth" "$BASE_URL/api/v1/namespaces" "401"
check "Auth required" "$BASE_URL/api/v1/auth/me" "401"

curl -s -c "$COOKIE_JAR" "$BASE_URL/api/v1/auth/me" >/dev/null
CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_JAR" | tail -n 1)"

REGISTER_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/local/register" \
  -b "$COOKIE_JAR" \
  -c "$COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"email\":\"$EMAIL\"}" || true)"
if [[ "$REGISTER_STATUS" == "200" ]]; then
  echo "PASS: Register (HTTP $REGISTER_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Register (got $REGISTER_STATUS)"
  FAIL=$((FAIL + 1))
fi

AUTH_ME_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/api/v1/auth/me" || true)"
if [[ "$AUTH_ME_STATUS" == "200" ]]; then
  echo "PASS: Auth me with session (HTTP $AUTH_ME_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Auth me with session (got $AUTH_ME_STATUS)"
  FAIL=$((FAIL + 1))
fi

NAMESPACES_AUTH_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/api/v1/namespaces" || true)"
if [[ "$NAMESPACES_AUTH_STATUS" == "200" ]]; then
  echo "PASS: Namespaces API with session (HTTP $NAMESPACES_AUTH_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Namespaces API with session (got $NAMESPACES_AUTH_STATUS)"
  FAIL=$((FAIL + 1))
fi

CHANGE_PASSWORD_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/local/change-password" \
  -b "$COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"$NEW_PASSWORD\"}" || true)"
if [[ "$CHANGE_PASSWORD_STATUS" == "200" ]]; then
  echo "PASS: Change password (HTTP $CHANGE_PASSWORD_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Change password (got $CHANGE_PASSWORD_STATUS)"
  FAIL=$((FAIL + 1))
fi

LOGOUT_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/logout" \
  -b "$COOKIE_JAR" \
  -c "$COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" || true)"
if [[ "$LOGOUT_STATUS" == "302" || "$LOGOUT_STATUS" == "200" || "$LOGOUT_STATUS" == "204" ]]; then
  echo "PASS: Logout (HTTP $LOGOUT_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Logout (got $LOGOUT_STATUS)"
  FAIL=$((FAIL + 1))
fi

POST_LOGOUT_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/api/v1/auth/me" || true)"
if [[ "$POST_LOGOUT_STATUS" == "401" ]]; then
  echo "PASS: Auth me after logout (HTTP $POST_LOGOUT_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Auth me after logout (got $POST_LOGOUT_STATUS)"
  FAIL=$((FAIL + 1))
fi

# ---- Label Management (requires admin) ----
SMOKE_ADMIN_CHECKS="${SMOKE_ADMIN_CHECKS:-auto}"
SMOKE_ADMIN_USERNAME="${SMOKE_ADMIN_USERNAME:-}"
SMOKE_ADMIN_PASSWORD="${SMOKE_ADMIN_PASSWORD:-}"
ADMIN_CONFIG_FAILED=0
if [[ "$SMOKE_ADMIN_CHECKS" != "auto" && "$SMOKE_ADMIN_CHECKS" != "true" && "$SMOKE_ADMIN_CHECKS" != "false" ]]; then
  echo "FAIL: SMOKE_ADMIN_CHECKS must be auto, true, or false"
  FAIL=$((FAIL + 1))
  ADMIN_CONFIG_FAILED=1
fi
if [[ "$SMOKE_ADMIN_CHECKS" == "true" && ( -z "$SMOKE_ADMIN_USERNAME" || -z "$SMOKE_ADMIN_PASSWORD" ) ]]; then
  echo "FAIL: SMOKE_ADMIN_CHECKS=true requires SMOKE_ADMIN_USERNAME and SMOKE_ADMIN_PASSWORD"
  FAIL=$((FAIL + 1))
  ADMIN_CONFIG_FAILED=1
fi
if [[ "$ADMIN_CONFIG_FAILED" -ne 0 ]]; then
  finish
  exit $?
fi
if [[ "$SMOKE_ADMIN_CHECKS" == "false" || ( "$SMOKE_ADMIN_CHECKS" == "auto" && ( -z "$SMOKE_ADMIN_USERNAME" || -z "$SMOKE_ADMIN_PASSWORD" ) ) ]]; then
  echo "SKIP: Admin label management (set SMOKE_ADMIN_USERNAME and SMOKE_ADMIN_PASSWORD to enable)"
  finish
  exit $?
fi

ADMIN_COOKIE_JAR="$(mktemp)"
LABEL_SLUG="smoke-label-$(date +%s)"

cleanup_admin() {
  rm -f "$ADMIN_COOKIE_JAR"
}
trap 'cleanup; cleanup_admin' EXIT

# Get CSRF token for admin session
curl -s -c "$ADMIN_COOKIE_JAR" "$BASE_URL/api/v1/auth/me" >/dev/null
ADMIN_CSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$ADMIN_COOKIE_JAR" | tail -n 1)"

# Login as admin
ADMIN_LOGIN_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/local/login" \
  -b "$ADMIN_COOKIE_JAR" \
  -c "$ADMIN_COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$SMOKE_ADMIN_USERNAME\",\"password\":\"$SMOKE_ADMIN_PASSWORD\"}" || true)"
if [[ "$ADMIN_LOGIN_STATUS" == "200" ]]; then
  echo "PASS: Admin login (HTTP $ADMIN_LOGIN_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Admin login (got $ADMIN_LOGIN_STATUS)"
  FAIL=$((FAIL + 1))
fi

# Refresh CSRF after login
ADMIN_CSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$ADMIN_COOKIE_JAR" | tail -n 1)"

# Create label definition
CREATE_LABEL_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/admin/labels" \
  -b "$ADMIN_COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -d "{\"slug\":\"$LABEL_SLUG\",\"type\":\"RECOMMENDED\",\"visibleInFilter\":true,\"sortOrder\":99,\"translations\":[{\"locale\":\"en\",\"displayName\":\"Smoke Label\"}]}" || true)"
if [[ "$CREATE_LABEL_STATUS" == "200" ]]; then
  echo "PASS: Create label definition (HTTP $CREATE_LABEL_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Create label definition (got $CREATE_LABEL_STATUS)"
  FAIL=$((FAIL + 1))
fi

# List admin label definitions
LIST_LABELS_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -b "$ADMIN_COOKIE_JAR" "$BASE_URL/api/v1/admin/labels" || true)"
if [[ "$LIST_LABELS_STATUS" == "200" ]]; then
  echo "PASS: List admin label definitions (HTTP $LIST_LABELS_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: List admin label definitions (got $LIST_LABELS_STATUS)"
  FAIL=$((FAIL + 1))
fi

# List visible labels (public)
VISIBLE_LABELS_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/v1/labels" || true)"
if [[ "$VISIBLE_LABELS_STATUS" == "200" ]]; then
  echo "PASS: List visible labels (HTTP $VISIBLE_LABELS_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: List visible labels (got $VISIBLE_LABELS_STATUS)"
  FAIL=$((FAIL + 1))
fi

# Delete label definition (cleanup)
DELETE_LABEL_STATUS="$(curl --max-time 10 -s -o /dev/null -w "%{http_code}" \
  -X DELETE "$BASE_URL/api/v1/admin/labels/$LABEL_SLUG" \
  -b "$ADMIN_COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" || true)"
if [[ "$DELETE_LABEL_STATUS" == "200" || "$DELETE_LABEL_STATUS" == "204" ]]; then
  echo "PASS: Delete label definition (HTTP $DELETE_LABEL_STATUS)"
  PASS=$((PASS + 1))
else
  echo "FAIL: Delete label definition (got $DELETE_LABEL_STATUS)"
  FAIL=$((FAIL + 1))
fi

finish
