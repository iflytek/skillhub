#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
USER_COOKIE="$(mktemp)"
ADMIN_COOKIE="$(mktemp)"
WORK_DIR="$(mktemp -d)"
SLUG="psmoke$(date +%s)"

cleanup() {
  rm -f "$USER_COOKIE" "$ADMIN_COOKIE"
  rm -rf "$WORK_DIR"
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
value = json.loads(os.environ["JSON_INPUT"])
for part in expr.split("."):
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

echo "=== Promotion Workflow Smoke Test ==="
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

GLOBAL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  "$BASE_URL/api/web/namespaces/global")"
assert_code "Global namespace detail is available" "$GLOBAL_RESPONSE" "0"
GLOBAL_NAMESPACE_ID="$(json_field "$GLOBAL_RESPONSE" "data.id")"

CREATE_NAMESPACE_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces" \
  -d "{\"slug\":\"$SLUG\",\"displayName\":\"Promotion Smoke $SLUG\",\"description\":\"promotion smoke test\"}")"
assert_code "Owner can create promotion smoke namespace" "$CREATE_NAMESPACE_RESPONSE" "0"
NAMESPACE_ID="$(json_field "$CREATE_NAMESPACE_RESPONSE" "data.id")"

cat > "$WORK_DIR/SKILL.md" <<'EOF'
---
name: Promotion Smoke Skill
description: Promotion smoke test
version: 1.0.0
---
Body
EOF
(cd "$WORK_DIR" && zip -q skill.zip SKILL.md)

PUBLISH_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -F "file=@$WORK_DIR/skill.zip;type=application/zip" \
  -F "visibility=PUBLIC" \
  "$BASE_URL/api/web/skills/$SLUG/publish")"
assert_code "Owner can publish a team skill" "$PUBLISH_RESPONSE" "0"
SKILL_ID="$(json_field "$PUBLISH_RESPONSE" "data.skillId")"
SKILL_SLUG="$(json_field "$PUBLISH_RESPONSE" "data.slug")"

PENDING_REVIEWS_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  "$BASE_URL/api/web/reviews?status=PENDING&namespaceId=$NAMESPACE_ID")"
assert_code "Admin can list pending namespace reviews" "$PENDING_REVIEWS_RESPONSE" "0"
REVIEW_ID="$(json_field "$PENDING_REVIEWS_RESPONSE" "data.items.0.id")"

APPROVE_REVIEW_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/reviews/$REVIEW_ID/approve" \
  -d '{"comment":"ok"}')"
assert_code "Admin can approve team skill review" "$APPROVE_REVIEW_RESPONSE" "0"

SKILL_DETAIL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  "$BASE_URL/api/web/skills/$SLUG/$SKILL_SLUG")"
assert_code "Owner can load team skill detail" "$SKILL_DETAIL_RESPONSE" "0"
VERSION_ID="$(json_field "$SKILL_DETAIL_RESPONSE" "data.latestVersionId")"
CAN_SUBMIT_PROMOTION="$(json_field "$SKILL_DETAIL_RESPONSE" "data.canSubmitPromotion")"
if [[ "$CAN_SUBMIT_PROMOTION" == "True" || "$CAN_SUBMIT_PROMOTION" == "true" ]]; then
  pass "Approved team skill is marked promotable"
else
  fail "Approved team skill should expose canSubmitPromotion=true"
fi

MY_SKILLS_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  "$BASE_URL/api/web/me/skills")"
assert_code "Owner can list my skills with promotion metadata" "$MY_SKILLS_RESPONSE" "0"
if JSON_INPUT="$MY_SKILLS_RESPONSE" python3 - "$SKILL_ID" <<'PY'
import json
import os
import sys

skill_id = int(sys.argv[1])
items = json.loads(os.environ["JSON_INPUT"])["data"]
match = next(item for item in items if item["id"] == skill_id)
raise SystemExit(0 if match["canSubmitPromotion"] and match["latestVersionId"] else 1)
PY
then
  pass "My skills response exposes promotion submission fields"
else
  fail "My skills response should expose latestVersionId and canSubmitPromotion"
fi

SUBMIT_PROMOTION_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/promotions" \
  -d "{\"sourceSkillId\":$SKILL_ID,\"sourceVersionId\":$VERSION_ID,\"targetNamespaceId\":$GLOBAL_NAMESPACE_ID}")"
assert_code "Owner can submit promotion to global namespace" "$SUBMIT_PROMOTION_RESPONSE" "0"

PENDING_PROMOTIONS_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  "$BASE_URL/api/web/promotions?status=PENDING")"
assert_code "Admin can list pending promotions" "$PENDING_PROMOTIONS_RESPONSE" "0"
if JSON_INPUT="$PENDING_PROMOTIONS_RESPONSE" python3 - "$SKILL_ID" <<'PY'
import json
import os
import sys

skill_id = int(sys.argv[1])
items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
raise SystemExit(0 if any(item["sourceSkillId"] == skill_id for item in items) else 1)
PY
then
  pass "Pending promotions list contains the submitted team skill"
else
  fail "Pending promotions list should include submitted team skill"
fi

echo
echo "Results: $PASS passed, $FAIL failed"
if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
