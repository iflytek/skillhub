#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
USER_COOKIE="$(mktemp)"
ADMIN_COOKIE="$(mktemp)"
SLUG="nsmoke$(date +%s)"

cleanup() {
  rm -f "$USER_COOKIE" "$ADMIN_COOKIE"
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

csrf_token() {
  local cookie_file="$1"
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$cookie_file" | tail -n 1
}

bootstrap_csrf() {
  local cookie_file="$1"
  local user_id="$2"
  curl -s -c "$cookie_file" -H "X-Mock-User-Id: $user_id" "$BASE_URL/api/v1/auth/providers" >/dev/null
}

json_field() {
  local json="$1"
  local expr="$2"
  JSON_INPUT="$json" python3 - "$expr" <<'PY'
import json
import os
import sys

expr = sys.argv[1]
data = json.loads(os.environ["JSON_INPUT"])
value = data
for part in expr.split('.'):
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

USER_HEADERS=(-H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE")
ADMIN_HEADERS=(-H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE")

echo "=== Namespace Workflow Smoke Test ==="
echo "Target: $BASE_URL"
echo "Slug:   $SLUG"
echo

bootstrap_csrf "$USER_COOKIE" "local-user"
bootstrap_csrf "$ADMIN_COOKIE" "local-admin"

USER_CSRF="$(csrf_token "$USER_COOKIE")"
ADMIN_CSRF="$(csrf_token "$ADMIN_COOKIE")"

if [[ -z "$USER_CSRF" || -z "$ADMIN_CSRF" ]]; then
  echo "Could not bootstrap CSRF tokens"
  exit 1
fi

CREATE_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces" \
  -d "{\"slug\":\"$SLUG\",\"displayName\":\"Namespace Smoke $SLUG\",\"description\":\"namespace workflow smoke test\"}")"
assert_code "Owner can create namespace" "$CREATE_RESPONSE" "0"
NAMESPACE_ID="$(json_field "$CREATE_RESPONSE" "data.id")"

MINE_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" "$BASE_URL/api/web/me/namespaces")"
assert_code "Owner can list my namespaces" "$MINE_RESPONSE" "0"
if JSON_INPUT="$MINE_RESPONSE" python3 - "$SLUG" <<'PY'
import json
import os
import sys
slug = sys.argv[1]
data = json.loads(os.environ["JSON_INPUT"])
items = data["data"]
match = next((item for item in items if item["slug"] == slug), None)
if not match:
    raise SystemExit(1)
if match["currentUserRole"] != "OWNER":
    raise SystemExit(2)
if match["status"] != "ACTIVE":
    raise SystemExit(3)
PY
then
  pass "Created namespace shows up as ACTIVE owner namespace"
else
  fail "Created namespace should appear in owner namespace list with OWNER role"
fi

ADMIN_MINE_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" "$BASE_URL/api/web/me/namespaces")"
assert_code "Other user can list my namespaces" "$ADMIN_MINE_RESPONSE" "0"
if JSON_INPUT="$ADMIN_MINE_RESPONSE" python3 - "$SLUG" <<'PY'
import json
import os
import sys
slug = sys.argv[1]
data = json.loads(os.environ["JSON_INPUT"])
items = data["data"]
raise SystemExit(0 if all(item["slug"] != slug for item in items) else 1)
PY
then
  pass "Namespace is not visible to unrelated users in my namespaces"
else
  fail "Unrelated user should not see team namespace in my namespaces"
fi

FREEZE_FORBIDDEN_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/freeze")"
assert_code "Unrelated user cannot freeze namespace" "$FREEZE_FORBIDDEN_RESPONSE" "403"

CANDIDATES_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" "$BASE_URL/api/web/namespaces/$SLUG/member-candidates?search=local")"
assert_code "Owner can search namespace member candidates" "$CANDIDATES_RESPONSE" "0"
if JSON_INPUT="$CANDIDATES_RESPONSE" python3 - <<'PY'
import json
import os
import sys
data = json.loads(os.environ["JSON_INPUT"])
ids = {item["userId"] for item in data["data"]}
raise SystemExit(0 if "local-admin" in ids else 1)
PY
then
  pass "Candidate search returns local-admin"
else
  fail "Candidate search should include local-admin"
fi

ADD_MEMBER_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/members" \
  -d '{"userId":"local-admin","role":"MEMBER"}')"
assert_code "Owner can add namespace members" "$ADD_MEMBER_RESPONSE" "0"

MEMBERS_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" "$BASE_URL/api/web/namespaces/$SLUG/members")"
assert_code "Owner can list namespace members" "$MEMBERS_RESPONSE" "0"
if JSON_INPUT="$MEMBERS_RESPONSE" python3 - <<'PY'
import json
import os
import sys
data = json.loads(os.environ["JSON_INPUT"])
items = data["data"]["items"]
ids = {item["userId"] for item in items}
raise SystemExit(0 if {"local-user", "local-admin"}.issubset(ids) else 1)
PY
then
  pass "Member list shows owner and invited admin user"
else
  fail "Member list should contain owner and invited user"
fi

REVIEWS_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" "$BASE_URL/api/web/reviews?status=PENDING&namespaceId=$NAMESPACE_ID")"
assert_code "Owner can open namespace review list" "$REVIEWS_RESPONSE" "0"

PROMOTE_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X PUT "$BASE_URL/api/web/namespaces/$SLUG/members/local-admin/role" \
  -d '{"role":"ADMIN"}')"
assert_code "Owner can promote member to admin" "$PROMOTE_RESPONSE" "0"

ADMIN_FREEZE_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/freeze")"
assert_code "Namespace admin can freeze namespace" "$ADMIN_FREEZE_RESPONSE" "0"
if [[ "$(json_field "$ADMIN_FREEZE_RESPONSE" "data.status")" == "FROZEN" ]]; then
  pass "Freeze changes namespace status to FROZEN"
else
  fail "Freeze should set namespace status to FROZEN"
fi

ADD_WHILE_FROZEN_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/members" \
  -d '{"userId":"local-user","role":"MEMBER"}')"
assert_code "Frozen namespace rejects member mutation" "$ADD_WHILE_FROZEN_RESPONSE" "400"

ADMIN_UNFREEZE_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/unfreeze")"
assert_code "Namespace admin can unfreeze namespace" "$ADMIN_UNFREEZE_RESPONSE" "0"

ADMIN_ARCHIVE_RESPONSE="$(curl -sS "${ADMIN_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/archive" \
  -d '{"reason":"smoke"}')"
assert_code "Namespace admin cannot archive namespace" "$ADMIN_ARCHIVE_RESPONSE" "403"

OWNER_ARCHIVE_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/archive" \
  -d '{"reason":"smoke"}')"
assert_code "Owner can archive namespace" "$OWNER_ARCHIVE_RESPONSE" "0"
if [[ "$(json_field "$OWNER_ARCHIVE_RESPONSE" "data.status")" == "ARCHIVED" ]]; then
  pass "Archive changes namespace status to ARCHIVED"
else
  fail "Archive should set namespace status to ARCHIVED"
fi

OWNER_RESTORE_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/restore")"
assert_code "Owner can restore archived namespace" "$OWNER_RESTORE_RESPONSE" "0"
if [[ "$(json_field "$OWNER_RESTORE_RESPONSE" "data.status")" == "ACTIVE" ]]; then
  pass "Restore changes namespace status back to ACTIVE"
else
  fail "Restore should set namespace status back to ACTIVE"
fi

REMOVE_MEMBER_RESPONSE="$(curl -sS "${USER_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -X DELETE "$BASE_URL/api/web/namespaces/$SLUG/members/local-admin")"
assert_code "Owner can remove namespace admin" "$REMOVE_MEMBER_RESPONSE" "0"

echo
echo "Results: $PASS passed, $FAIL failed"
if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
