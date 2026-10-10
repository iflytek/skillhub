#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
COOKIE_FILE="$(mktemp)"

cleanup() {
  rm -f "$COOKIE_FILE"
}

trap cleanup EXIT

pass() {
  echo "PASS: $1"
  PASS=$((PASS + 1))
}

fail() {
  echo "FAIL: $1"
  FAIL=$((FAIL + 1))
}

json_field() {
  local json="$1"
  local expr="$2"
  JSON_INPUT="$json" python3 - "$expr" <<'PY'
import json
import os
import sys

expr = sys.argv[1]
value = json.loads(os.environ["JSON_INPUT"])
for part in expr.split('.'):
    if not part:
        continue
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]
if isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)
PY
}

assert_code() {
  local description="$1"
  local json="$2"
  local expected="$3"
  local actual
  actual="$(json_field "$json" "code")"
  if [[ "$actual" == "$expected" ]]; then
    pass "$description"
  else
    fail "$description (expected code $expected, got $actual)"
  fi
}

assert_json_expr() {
  local description="$1"
  local json="$2"
  local script="$3"
  if JSON_INPUT="$json" python3 - <<PY
import json
import os

data = json.loads(os.environ["JSON_INPUT"])
$script
PY
  then
    pass "$description"
  else
    fail "$description"
  fi
}

echo "=== Governance Workflow Smoke Test ==="
echo "Target: $BASE_URL"
echo

curl -s -c "$COOKIE_FILE" -H "X-Mock-User-Id: local-admin" "$BASE_URL/api/v1/auth/providers" >/dev/null

ADMIN_HEADERS=(-H "X-Mock-User-Id: local-admin" -b "$COOKIE_FILE" -c "$COOKIE_FILE")

SUMMARY_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/web/governance/summary")"
assert_code "Governance summary endpoint is available" "$SUMMARY_RESPONSE" "0"
assert_json_expr "Governance summary exposes review/promotion/report counts" "$SUMMARY_RESPONSE" $'summary = data["data"]\nassert "pendingReviews" in summary\nassert "pendingPromotions" in summary\nassert "pendingReports" in summary'

INBOX_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/web/governance/inbox")"
assert_code "Governance inbox endpoint is available" "$INBOX_RESPONSE" "0"
assert_json_expr "Governance inbox returns paged items" "$INBOX_RESPONSE" $'payload = data["data"]\nassert isinstance(payload["items"], list)\nassert payload["page"] == 0'

ACTIVITY_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/web/governance/activity")"
assert_code "Governance activity endpoint is available" "$ACTIVITY_RESPONSE" "0"
assert_json_expr "Governance activity returns paged items" "$ACTIVITY_RESPONSE" $'payload = data["data"]\nassert isinstance(payload["items"], list)\nassert payload["page"] == 0'

NOTIFICATIONS_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/web/governance/notifications")"
assert_code "Governance notifications endpoint is available" "$NOTIFICATIONS_RESPONSE" "0"
assert_json_expr "Governance notifications returns a list" "$NOTIFICATIONS_RESPONSE" $'assert isinstance(data["data"], list)'

REPORTS_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/v1/admin/skill-reports?status=PENDING&page=0&size=5")"
assert_code "Admin report list endpoint is available" "$REPORTS_RESPONSE" "0"
assert_json_expr "Admin report list returns page metadata" "$REPORTS_RESPONSE" $'payload = data["data"]\nassert isinstance(payload["items"], list)\nassert payload["size"] == 5'

AUDIT_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/v1/admin/audit-logs?action=REVIEW_APPROVE&page=0&size=5")"
assert_code "Audit log endpoint is available for governance filters" "$AUDIT_RESPONSE" "0"
assert_json_expr "Audit log endpoint returns page metadata" "$AUDIT_RESPONSE" $'payload = data["data"]\nassert isinstance(payload["items"], list)\nassert payload["size"] == 5'

echo
echo "Results: $PASS passed, $FAIL failed"
if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
