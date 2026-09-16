#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
COOKIE_FILE="$(mktemp)"
WORK_DIR="$(mktemp -d)"
TOKEN="$(date +%s)${RANDOM}"
SKILL_NAME="suite-smoke-member-${TOKEN}"
SUITE_SLUG="suite-smoke-${TOKEN}"
SKILL_ID=""
SKILL_VERSION_ID=""
SUITE_ID=""
SMOKE_ADMIN_USERNAME="${SMOKE_ADMIN_USERNAME:-}"
SMOKE_ADMIN_PASSWORD="${SMOKE_ADMIN_PASSWORD:-}"
AUTH_HEADERS=()

json_field() {
  JSON_INPUT="$1" python3 - "$2" <<'PY'
import json
import os
import sys

value = json.loads(os.environ["JSON_INPUT"])
for part in sys.argv[1].split("."):
    value = value[int(part)] if part.isdigit() else value[part]
print(json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else value)
PY
}

assert_code() {
  local description="$1"
  local body="$2"
  local expected="$3"
  local actual
  actual="$(json_field "$body" code)"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $description (expected code $expected, got $actual)"
    exit 1
  fi
  echo "PASS: $description"
}

assert_suite_availability() {
  local description="$1"
  local expected_available="$2"
  local expected_reason="${3:-}"
  local response
  response="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
    "${AUTH_HEADERS[@]}" \
    "$BASE_URL/api/web/suites/global/$SUITE_SLUG?version=1.0.0")"
  assert_code "$description" "$response" 0
  JSON_INPUT="$response" EXPECTED_AVAILABLE="$expected_available" EXPECTED_REASON="$expected_reason" \
    python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"])["data"]
expected_available = os.environ["EXPECTED_AVAILABLE"] == "true"
expected_reason = os.environ["EXPECTED_REASON"] or None
reasons = {member.get("blockingReason") for member in data["members"]}
if data["available"] is not expected_available:
    raise SystemExit(1)
if expected_reason is not None and expected_reason not in reasons:
    raise SystemExit(1)
PY
  echo "PASS: $description has the expected availability"
}

assert_install_plan_rejected() {
  local description="$1"
  local key="$2"
  local status
  status="$(curl -sS -o "$WORK_DIR/blocked-plan.json" -w '%{http_code}' \
    -b "$COOKIE_FILE" -c "$COOKIE_FILE" "${AUTH_HEADERS[@]}" \
    -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H "Idempotency-Key: $key" -X POST \
    "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
  if [[ "$status" != "400" ]]; then
    echo "FAIL: $description should return HTTP 400, got $status"
    exit 1
  fi
  echo "PASS: $description"
}

assert_install_plan_available() {
  local description="$1"
  local key="$2"
  local response
  response="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
    "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -H "Idempotency-Key: $key" -X POST \
    "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
  assert_code "$description" "$response" 0
}

cleanup() {
  if [[ -n "$SUITE_ID" && -n "${CSRF_TOKEN:-}" ]]; then
    curl -sS -o /dev/null -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
      "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
      -X DELETE "$BASE_URL/api/web/suites/$SUITE_ID" || true
  fi
  if [[ -n "$SKILL_ID" && -n "${CSRF_TOKEN:-}" ]]; then
    curl -sS -o /dev/null -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
      "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
      -X DELETE "$BASE_URL/api/v1/skills/id/$SKILL_ID" || true
  fi
  rm -f "$COOKIE_FILE"
  rm -rf "$WORK_DIR"
}

trap cleanup EXIT

echo "=== Skill Suite Smoke Test ==="
echo "Target: $BASE_URL"
echo "Suite:  @global/$SUITE_SLUG"

if [[ -n "$SMOKE_ADMIN_USERNAME" || -n "$SMOKE_ADMIN_PASSWORD" ]]; then
  if [[ -z "$SMOKE_ADMIN_USERNAME" || -z "$SMOKE_ADMIN_PASSWORD" ]]; then
    echo "FAIL: SMOKE_ADMIN_USERNAME and SMOKE_ADMIN_PASSWORD must be set together"
    exit 1
  fi
  curl -sS -c "$COOKIE_FILE" "$BASE_URL/api/v1/auth/me" >/dev/null
else
  AUTH_HEADERS=(-H "X-Mock-User-Id: local-admin")
  curl -sS -c "$COOKIE_FILE" "${AUTH_HEADERS[@]}" \
    "$BASE_URL/api/v1/auth/providers" >/dev/null
fi
CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_FILE" | tail -n 1)"
if [[ -z "$CSRF_TOKEN" ]]; then
  echo "FAIL: could not bootstrap CSRF token"
  exit 1
fi

if [[ -n "$SMOKE_ADMIN_USERNAME" ]]; then
  LOGIN_PAYLOAD="$(SMOKE_ADMIN_USERNAME="$SMOKE_ADMIN_USERNAME" SMOKE_ADMIN_PASSWORD="$SMOKE_ADMIN_PASSWORD" \
    python3 - <<'PY'
import json
import os

print(json.dumps({
    "username": os.environ["SMOKE_ADMIN_USERNAME"],
    "password": os.environ["SMOKE_ADMIN_PASSWORD"],
}))
PY
)"
  LOGIN_STATUS="$(curl -sS -o "$WORK_DIR/login.json" -w '%{http_code}' \
    -b "$COOKIE_FILE" -c "$COOKIE_FILE" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -H "Content-Type: application/json" -X POST \
    "$BASE_URL/api/v1/auth/local/login" -d "$LOGIN_PAYLOAD")"
  if [[ "$LOGIN_STATUS" != "200" ]]; then
    echo "FAIL: local administrator login returned HTTP $LOGIN_STATUS"
    exit 1
  fi
  CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_FILE" | tail -n 1)"
  echo "PASS: authenticated with the local administrator account"
else
  echo "PASS: authenticated with the local mock administrator"
fi

cat > "$WORK_DIR/SKILL.md" <<EOF
---
name: $SKILL_NAME
description: Temporary member for the Skill Suite smoke test
version: 1.0.0
---
# Suite smoke member
EOF
python3 - "$WORK_DIR" <<'PY'
from pathlib import Path
import sys
import zipfile

root = Path(sys.argv[1])
with zipfile.ZipFile(root / "member.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / "SKILL.md", "SKILL.md")
PY

PUBLISH_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -F "file=@$WORK_DIR/member.zip;type=application/zip" -F "visibility=PUBLIC" \
  "$BASE_URL/api/web/skills/global/publish")"
assert_code "publish the temporary member Skill" "$PUBLISH_RESPONSE" 0
SKILL_ID="$(json_field "$PUBLISH_RESPONSE" data.skillId)"
SKILL_SLUG="$(json_field "$PUBLISH_RESPONSE" data.slug)"

SKILL_DETAIL=""
for _ in $(seq 1 60); do
  SKILL_DETAIL="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
    "${AUTH_HEADERS[@]}" "$BASE_URL/api/web/skills/global/$SKILL_SLUG")"
  SKILL_VERSION_ID="$(JSON_INPUT="$SKILL_DETAIL" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"]).get("data") or {}
versions = [data.get("headlineVersion") or {}, data.get("ownerPreviewVersion") or {}]
match = next((item for item in versions if item.get("version") == "1.0.0" and item.get("status") == "PUBLISHED"), {})
print(match.get("id", ""))
PY
)"
  [[ -n "$SKILL_VERSION_ID" ]] && break
  sleep 1
done
if [[ -z "$SKILL_VERSION_ID" ]]; then
  echo "FAIL: member Skill did not become PUBLISHED within 60 seconds"
  exit 1
fi
echo "PASS: member Skill is published and downloadable"

SUITE_PAYLOAD="$(python3 - "$SUITE_SLUG" "$SKILL_SLUG" "$SKILL_VERSION_ID" <<'PY'
import json
import sys

member = {"skillVersionId": int(sys.argv[3]), "namespace": "global", "slug": sys.argv[2], "version": "1.0.0"}
print(json.dumps({
    "namespace": "global",
    "slug": sys.argv[1],
    "displayName": "Suite smoke test",
    "summary": "Temporary private Suite",
    "version": "1.0.0",
    "visibility": "PRIVATE",
    "changelog": "Initial smoke version",
    "entrySkill": member,
    "members": [member],
}))
PY
)"
MISSING_ENTRY_PAYLOAD="$(JSON_INPUT="$SUITE_PAYLOAD" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["JSON_INPUT"])
payload.pop("entrySkill")
print(json.dumps(payload))
PY
)"
MISSING_ENTRY_STATUS="$(curl -sS -o "$WORK_DIR/missing-entry.json" -w '%{http_code}' \
  -b "$COOKIE_FILE" -c "$COOKIE_FILE" "${AUTH_HEADERS[@]}" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/web/suites" -d "$MISSING_ENTRY_PAYLOAD")"
if [[ "$MISSING_ENTRY_STATUS" != "400" ]]; then
  echo "FAIL: creating a Suite without an Entry Skill should return HTTP 400, got $MISSING_ENTRY_STATUS"
  exit 1
fi
echo "PASS: creating a Suite without an Entry Skill is rejected"

CREATE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" -X POST "$BASE_URL/api/web/suites" \
  -d "$SUITE_PAYLOAD")"
assert_code "create a Suite draft with one exact member" "$CREATE_RESPONSE" 0
SUITE_ID="$(json_field "$CREATE_RESPONSE" data.id)"
SUITE_VERSION_ID="$(json_field "$CREATE_RESPONSE" data.versionId)"

PUBLISH_SUITE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -X POST "$BASE_URL/api/web/suites/$SUITE_ID/versions/$SUITE_VERSION_ID/publish")"
assert_code "publish the private Suite directly" "$PUBLISH_SUITE_RESPONSE" 0

MY_SUITES_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" "$BASE_URL/api/web/me/suites?q=$SUITE_SLUG")"
assert_code "discover the published Suite in the owner dashboard" "$MY_SUITES_RESPONSE" 0
JSON_INPUT="$MY_SUITES_RESPONSE" python3 - "$SUITE_SLUG" <<'PY'
import json
import os
import sys

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
raise SystemExit(0 if any(item["slug"] == sys.argv[1] and item["versionStatus"] == "PUBLISHED" for item in items) else 1)
PY
echo "PASS: owner dashboard contains the published Suite"

IDEMPOTENCY_KEY="suite-smoke-$TOKEN"
PLAN_ONE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" -X POST \
  "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
assert_code "issue an exact-member install plan" "$PLAN_ONE" 0
if [[ "$(json_field "$PLAN_ONE" data.members.0.entry)" != "True" ]]; then
  echo "FAIL: the install plan did not preserve the Entry Skill role"
  exit 1
fi
echo "PASS: install plan marks the exact Entry Skill"
PLAN_TWO="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" -X POST \
  "$BASE_URL/api/web/suites/global/$SUITE_SLUG/install-plan?version=1.0.0")"
assert_code "safely replay the install plan" "$PLAN_TWO" 0
if [[ "$(json_field "$PLAN_ONE" data.operationId)" != "$(json_field "$PLAN_TWO" data.operationId)" ]]; then
  echo "FAIL: replayed plan returned a different operation ID"
  exit 1
fi
echo "PASS: replayed plan keeps the server operation ID"

RESOURCE_RESPONSE="$(curl -sS "$BASE_URL/api/v1/resources?resourceType=SKILL&q=$SKILL_SLUG")"
assert_code "ordinary Skill discovery remains available" "$RESOURCE_RESPONSE" 0
JSON_INPUT="$RESOURCE_RESPONSE" python3 - "$SKILL_SLUG" <<'PY'
import json
import os
import sys

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
raise SystemExit(0 if any(item["resourceType"] == "SKILL" and item["slug"] == sys.argv[1] for item in items) else 1)
PY
echo "PASS: typed discovery still returns the ordinary Skill"

ENTRY_DETAIL="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" "$BASE_URL/api/web/skills/global/$SKILL_SLUG")"
assert_code "load the Entry Skill detail" "$ENTRY_DETAIL" 0
JSON_INPUT="$ENTRY_DETAIL" python3 - "$SUITE_SLUG" <<'PY'
import json
import os
import sys

references = json.loads(os.environ["JSON_INPUT"])["data"]["entryForSuites"]
raise SystemExit(0 if any(item["slug"] == sys.argv[1] and item["version"] == "1.0.0" for item in references) else 1)
PY
echo "PASS: Entry Skill detail links back to the visible Suite"

HIDE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" -X POST \
  "$BASE_URL/api/v1/admin/skills/$SKILL_ID/hide" -d '{"reason":"suite smoke"}')"
assert_code "hide the member Skill" "$HIDE_RESPONSE" 0
assert_suite_availability "load the Suite after its member is hidden" false SKILL_HIDDEN
assert_install_plan_rejected "hidden member blocks a new install plan" "hidden-$TOKEN"

UNHIDE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -X POST \
  "$BASE_URL/api/v1/admin/skills/$SKILL_ID/unhide")"
assert_code "restore the hidden member Skill" "$UNHIDE_RESPONSE" 0
assert_suite_availability "load the Suite after its hidden member is restored" true
assert_install_plan_available "restored hidden member allows a new install plan" "unhidden-$TOKEN"

ARCHIVE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" -X POST \
  "$BASE_URL/api/web/skills/global/$SKILL_SLUG/archive" -d '{"reason":"suite smoke"}')"
assert_code "archive the member Skill" "$ARCHIVE_RESPONSE" 0
assert_suite_availability "load the Suite after its member is archived" false SKILL_ARCHIVED
assert_install_plan_rejected "archived member blocks a new install plan" "archived-$TOKEN"

UNARCHIVE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -X POST \
  "$BASE_URL/api/web/skills/global/$SKILL_SLUG/unarchive")"
assert_code "restore the archived member Skill" "$UNARCHIVE_RESPONSE" 0
assert_suite_availability "load the Suite after its archived member is restored" true
assert_install_plan_available "restored archived member allows a new install plan" "unarchived-$TOKEN"

YANK_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -H "Content-Type: application/json" -X POST \
  "$BASE_URL/api/v1/admin/skills/versions/$SKILL_VERSION_ID/yank" -d '{"reason":"suite smoke"}')"
assert_code "yank the exact member version" "$YANK_RESPONSE" 0
assert_suite_availability "load the Suite after its exact member version is yanked" false VERSION_UNAVAILABLE
assert_install_plan_rejected "yanked member blocks a new install plan" "yanked-$TOKEN"

DELETE_RESPONSE="$(curl -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE" \
  "${AUTH_HEADERS[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -X DELETE "$BASE_URL/api/v1/skills/id/$SKILL_ID")"
assert_code "hard-delete the member Skill" "$DELETE_RESPONSE" 0
SKILL_ID=""
assert_suite_availability "load the Suite after its member is hard-deleted" false DELETED
assert_install_plan_rejected "deleted member blocks a new install plan" "deleted-$TOKEN"
echo "=== Skill Suite Smoke Test Passed ==="
