#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
USER_COOKIE="$(mktemp)"
ADMIN_COOKIE="$(mktemp)"
WORK_DIR="$(mktemp -d)"
SLUG="psmoke$(date +%s)${RANDOM}"

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

GLOBAL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  "$BASE_URL/api/web/namespaces/global")"
assert_code "Global namespace detail is available" "$GLOBAL_RESPONSE" "0"
GLOBAL_NAMESPACE_ID="$(json_field "$GLOBAL_RESPONSE" "data.id")"

CREATE_NAMESPACE_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces" \
  -d "{\"slug\":\"$SLUG\",\"displayName\":\"Promotion Smoke $SLUG\",\"description\":\"promotion smoke test\"}")"
assert_code "Admin can create promotion smoke namespace" "$CREATE_NAMESPACE_RESPONSE" "0"
NAMESPACE_ID="$(json_field "$CREATE_NAMESPACE_RESPONSE" "data.id")"

ADD_MEMBER_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/namespaces/$SLUG/members" \
  -d '{"userId":"local-user","role":"MEMBER"}')"
assert_code "Admin can add the regular user as a namespace member" "$ADD_MEMBER_RESPONSE" "0"

cat > "$WORK_DIR/SKILL.md" <<EOF
---
name: Promotion Smoke $SLUG
description: Promotion smoke test
version: 1.0.0
---
Body
EOF
python3 - "$WORK_DIR" <<'PY'
from pathlib import Path
import sys
import zipfile

work_dir = Path(sys.argv[1])
with zipfile.ZipFile(work_dir / "skill.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(work_dir / "SKILL.md", "SKILL.md")
PY

PUBLISH_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -F "file=@$WORK_DIR/skill.zip;type=application/zip" \
  -F "visibility=PUBLIC" \
  "$BASE_URL/api/web/skills/$SLUG/publish")"
assert_code "Regular user can publish a team skill for review" "$PUBLISH_RESPONSE" "0"
SKILL_ID="$(json_field "$PUBLISH_RESPONSE" "data.skillId")"
SKILL_SLUG="$(json_field "$PUBLISH_RESPONSE" "data.slug")"

REVIEW_READY=false
SKILL_DETAIL_RESPONSE=""
for _ in $(seq 1 60); do
  SKILL_DETAIL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
    "$BASE_URL/api/web/skills/$SLUG/$SKILL_SLUG")"
  if JSON_INPUT="$SKILL_DETAIL_RESPONSE" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"]).get("data") or {}
versions = [data.get("ownerPreviewVersion") or {}, data.get("headlineVersion") or {}]
raise SystemExit(0 if any(version.get("status") == "PENDING_REVIEW" for version in versions) else 1)
PY
  then
    REVIEW_READY=true
    break
  fi
  sleep 1
done
assert_code "Regular user can load the submitted team skill" "$SKILL_DETAIL_RESPONSE" "0"
if [[ "$REVIEW_READY" != "true" ]]; then
  fail "Published team skill did not finish scanning within 60 seconds"
  exit 1
fi
pass "Published team skill is ready for review"

PENDING_REVIEWS_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  "$BASE_URL/api/web/reviews?status=PENDING&namespaceId=$NAMESPACE_ID")"
assert_code "Admin can list pending skill reviews" "$PENDING_REVIEWS_RESPONSE" "0"
REVIEW_ID="$(JSON_INPUT="$PENDING_REVIEWS_RESPONSE" python3 - "$SKILL_SLUG" <<'PY'
import json
import os
import sys

skill_slug = sys.argv[1]
items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
match = next((item for item in items if item["skillSlug"] == skill_slug), None)
print(match["id"] if match else "")
PY
)"
if [[ -z "$REVIEW_ID" ]]; then
  fail "Pending skill reviews should contain the published team skill"
  exit 1
fi
pass "Pending skill reviews contain the published team skill"

APPROVE_REVIEW_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/reviews/$REVIEW_ID/approve" \
  -d '{"comment":"approved by promotion smoke"}')"
assert_code "Admin can approve the regular user's team skill" "$APPROVE_REVIEW_RESPONSE" "0"

SKILL_DETAIL_RESPONSE=""
PROMOTION_READY=false
for _ in $(seq 1 60); do
  SKILL_DETAIL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
    "$BASE_URL/api/web/skills/$SLUG/$SKILL_SLUG")"
  if JSON_INPUT="$SKILL_DETAIL_RESPONSE" python3 - <<'PY'
import json
import os

response = json.loads(os.environ["JSON_INPUT"])
data = response.get("data") or {}
headline_version = data.get("headlineVersion") or {}
raise SystemExit(0 if headline_version.get("id") and data.get("canSubmitPromotion") else 1)
PY
  then
    PROMOTION_READY=true
    break
  fi
  sleep 1
done
assert_code "Regular user can load team skill detail" "$SKILL_DETAIL_RESPONSE" "0"
if [[ "$PROMOTION_READY" != "true" ]]; then
  fail "Published team skill did not become promotable within 60 seconds"
  exit 1
fi
VERSION_ID="$(json_field "$SKILL_DETAIL_RESPONSE" "data.headlineVersion.id")"
CAN_SUBMIT_PROMOTION="$(json_field "$SKILL_DETAIL_RESPONSE" "data.canSubmitPromotion")"
if [[ "$CAN_SUBMIT_PROMOTION" == "True" || "$CAN_SUBMIT_PROMOTION" == "true" ]]; then
  pass "Approved team skill is marked promotable"
else
  fail "Approved team skill should expose canSubmitPromotion=true"
fi

MY_SKILLS_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  "$BASE_URL/api/web/me/skills")"
assert_code "Regular user can list my skills with promotion metadata" "$MY_SKILLS_RESPONSE" "0"
if JSON_INPUT="$MY_SKILLS_RESPONSE" python3 - "$SKILL_ID" <<'PY'
import json
import os
import sys

skill_id = int(sys.argv[1])
items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
match = next(item for item in items if item["id"] == skill_id)
headline_version = match.get("headlineVersion") or {}
raise SystemExit(0 if match["canSubmitPromotion"] and headline_version.get("id") else 1)
PY
then
  pass "My skills response exposes promotion submission fields"
else
  fail "My skills response should expose headlineVersion and canSubmitPromotion"
fi

SUBMIT_PROMOTION_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/promotions" \
  -d "{\"sourceSkillId\":$SKILL_ID,\"sourceVersionId\":$VERSION_ID,\"targetNamespaceId\":$GLOBAL_NAMESPACE_ID}")"
assert_code "Regular user can submit promotion to global namespace" "$SUBMIT_PROMOTION_RESPONSE" "0"

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

PROMOTION_ID="$(JSON_INPUT="$PENDING_PROMOTIONS_RESPONSE" python3 - "$SKILL_ID" <<'PY'
import json
import os
import sys

skill_id = int(sys.argv[1])
items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
match = next((item for item in items if item["sourceSkillId"] == skill_id), None)
print(match["id"] if match else "")
PY
)"
if [[ -z "$PROMOTION_ID" ]]; then
  fail "Pending promotions should contain the submitted team skill"
  exit 1
fi

UNAUTHORIZED_APPROVAL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-user" -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/promotions/$PROMOTION_ID/approve" \
  -d '{"comment":"unauthorized"}')"
assert_code "Regular user cannot approve a promotion" "$UNAUTHORIZED_APPROVAL_RESPONSE" "403"

PROMOTION_AFTER_DENIAL_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  "$BASE_URL/api/web/promotions?status=PENDING")"
assert_code "Admin can reload pending promotions after denied approval" "$PROMOTION_AFTER_DENIAL_RESPONSE" "0"
if JSON_INPUT="$PROMOTION_AFTER_DENIAL_RESPONSE" python3 - "$PROMOTION_ID" <<'PY'
import json
import os
import sys

promotion_id = int(sys.argv[1])
items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
match = next((item for item in items if item["id"] == promotion_id), None)
raise SystemExit(0 if match and match["status"] == "PENDING" and match.get("targetSkillId") is None else 1)
PY
then
  pass "Denied approval keeps the promotion pending without a target"
else
  fail "Denied approval should keep the promotion pending without a target"
fi

APPROVE_PROMOTION_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/promotions/$PROMOTION_ID/approve" \
  -d '{"comment":"approved by promotion smoke"}')"
assert_code "Admin can approve promotion to global namespace" "$APPROVE_PROMOTION_RESPONSE" "0"
TARGET_SKILL_ID="$(json_field "$APPROVE_PROMOTION_RESPONSE" "data.targetSkillId")"
if [[ -n "$TARGET_SKILL_ID" && "$TARGET_SKILL_ID" != "None" && "$TARGET_SKILL_ID" != "null" ]]; then
  pass "Approved promotion exposes target skill id"
else
  fail "Approved promotion should expose target skill id"
fi

GLOBAL_VERSIONS_RESPONSE="$(curl -sS \
  "$BASE_URL/api/web/skills/global/$SKILL_SLUG/versions")"
assert_code "Promoted global skill versions are visible" "$GLOBAL_VERSIONS_RESPONSE" "0"
if JSON_INPUT="$GLOBAL_VERSIONS_RESPONSE" python3 - <<'PY'
import json
import os

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
raise SystemExit(0 if any(
    item["version"] == "1.0.0"
    and item["status"] == "PUBLISHED"
    and item["downloadAvailable"]
    for item in items
) else 1)
PY
then
  pass "Promoted global version is published and downloadable"
else
  fail "Promoted global version should be PUBLISHED with downloadAvailable=true"
fi

GLOBAL_BUNDLE="$WORK_DIR/global-skill.zip"
GLOBAL_DOWNLOAD_STATUS="$(curl -sS -L -o "$GLOBAL_BUNDLE" -w '%{http_code}' \
  "$BASE_URL/api/web/skills/global/$SKILL_SLUG/versions/1.0.0/download")"
if [[ "$GLOBAL_DOWNLOAD_STATUS" == "200" ]]; then
  pass "Promoted global version download returns HTTP 200"
else
  fail "Promoted global version download should return HTTP 200 (got $GLOBAL_DOWNLOAD_STATUS)"
fi

if python3 - "$GLOBAL_BUNDLE" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    content = archive.read("SKILL.md").decode("utf-8")
raise SystemExit(0 if "Body" in content.splitlines() else 1)
PY
then
  pass "Promoted global bundle contains the source SKILL.md"
else
  fail "Promoted global bundle should contain the source SKILL.md"
fi

echo
echo "Results: $PASS passed, $FAIL failed"
if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
