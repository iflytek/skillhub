#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
ADMIN_USERNAME="${SMOKE_ADMIN_USERNAME:-}"
ADMIN_PASSWORD="${SMOKE_ADMIN_PASSWORD:-}"
WORK_DIR="$(mktemp -d)"
ADMIN_COOKIE="$(mktemp)"
RECOVERY_COOKIE="$(mktemp)"
USER_COOKIE="$(mktemp)"
TOKEN="$(date +%s)${RANDOM}"
ENTRY_SLUG="bundle-entry-${TOKEN}"
REFERENCE_SLUG="bundle-reference-${TOKEN}"
SUITE_SLUG="bundle-suite-${TOKEN}"
LABEL_SLUG="bundle-label-${TOKEN}"
USER_NAME="bundle_user_${TOKEN}"
USER_PASSWORD="BundleUser${TOKEN}!Aa9"
SUITE_ID=""
ENTRY_SKILL_ID=""
REFERENCE_SKILL_ID=""
LABEL_CREATED=false

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
  local response="$2"
  local expected="${3:-0}"
  local actual
  actual="$(json_field "$response" code)"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $description (expected code $expected, got $actual)" >&2
    exit 1
  fi
  echo "PASS: $description"
}

csrf_token() {
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$1" | tail -n 1
}

bootstrap_cookie() {
  curl -sS -c "$1" "$BASE_URL/api/v1/auth/me" >/dev/null
}

login_admin() {
  local cookie_file="$1"
  bootstrap_cookie "$cookie_file"
  local csrf
  csrf="$(csrf_token "$cookie_file")"
  local response
  response="$(curl -fsS -b "$cookie_file" -c "$cookie_file" \
    -H "X-XSRF-TOKEN: $csrf" -H "Content-Type: application/json" \
    -X POST "$BASE_URL/api/v1/auth/local/login" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
  assert_code "authenticate local administrator" "$response"
}

cleanup() {
  local csrf=""
  csrf="$(csrf_token "$ADMIN_COOKIE" || true)"
  if [[ -n "$csrf" ]]; then
    if [[ -n "$SUITE_ID" ]]; then
      curl -sS -o /dev/null -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $csrf" \
        -X DELETE "$BASE_URL/api/web/suites/$SUITE_ID" || true
    fi
    if [[ -n "$ENTRY_SKILL_ID" ]]; then
      curl -sS -o /dev/null -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $csrf" \
        -X DELETE "$BASE_URL/api/v1/skills/id/$ENTRY_SKILL_ID" || true
    fi
    if [[ -n "$REFERENCE_SKILL_ID" ]]; then
      curl -sS -o /dev/null -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $csrf" \
        -X DELETE "$BASE_URL/api/v1/skills/id/$REFERENCE_SKILL_ID" || true
    fi
    if [[ "$LABEL_CREATED" == true ]]; then
      curl -sS -o /dev/null -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $csrf" \
        -X DELETE "$BASE_URL/api/v1/admin/labels/$LABEL_SLUG" || true
    fi
  fi
  rm -f "$ADMIN_COOKIE" "$RECOVERY_COOKIE" "$USER_COOKIE"
  rm -rf "$WORK_DIR"
}

trap cleanup EXIT

if [[ -z "$ADMIN_USERNAME" || -z "$ADMIN_PASSWORD" ]]; then
  echo "FAIL: SMOKE_ADMIN_USERNAME and SMOKE_ADMIN_PASSWORD are required" >&2
  exit 1
fi

make_skill_zip() {
  local slug="$1"
  local version="$2"
  local output="$3"
  SLUG="$slug" VERSION="$version" OUTPUT="$output" python3 - "$WORK_DIR" <<'PY'
from pathlib import Path
import os
import sys
import zipfile

root = Path(sys.argv[1])
skill_md = root / f"{os.environ['SLUG']}-{os.environ['VERSION']}.md"
skill_md.write_text(
    "---\n"
    f"name: {os.environ['SLUG']}\n"
    f"description: Release Compose Bundle smoke member {os.environ['SLUG']}\n"
    f"version: {os.environ['VERSION']}\n"
    "---\n\n# Bundle smoke member\n",
    encoding="utf-8",
)
with zipfile.ZipFile(os.environ["OUTPUT"], "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(skill_md, "SKILL.md")
PY
}

make_bundle_zip() {
  local mode="$1"
  local version="$2"
  local base_version="$3"
  local output="$4"
  MODE="$mode" VERSION="$version" BASE_VERSION="$base_version" OUTPUT="$output" \
    SUITE_SLUG="$SUITE_SLUG" ENTRY_SLUG="$ENTRY_SLUG" REFERENCE_SLUG="$REFERENCE_SLUG" \
    python3 - "$WORK_DIR" <<'PY'
from pathlib import Path
import os
import sys
import zipfile

root = Path(sys.argv[1])
member = root / f"entry-{os.environ['VERSION']}.md"
member.write_text(
    "---\n"
    f"name: {os.environ['ENTRY_SLUG']}\n"
    "description: Entry member created and updated through Suite Bundle smoke\n"
    f"version: {os.environ['VERSION']}\n"
    "---\n\n# Bundle entry\n",
    encoding="utf-8",
)
base = "" if not os.environ["BASE_VERSION"] else f"  baseVersion: {os.environ['BASE_VERSION']}\n"
manifest = (
    "apiVersion: skillhub.iflytek.com/v1alpha1\n"
    "kind: SkillSuiteBundle\n"
    "metadata:\n"
    "  namespace: global\n"
    f"  slug: {os.environ['SUITE_SLUG']}\n"
    "spec:\n"
    f"  mode: {os.environ['MODE']}\n"
    f"  version: {os.environ['VERSION']}\n"
    f"{base}"
    "  displayName: Release Compose Bundle smoke\n"
    "  summary: Authenticated release Compose Bundle smoke workflow\n"
    "  overview: |\n"
    "    # Release Compose Bundle smoke\n\n"
    "    Creates and updates a Suite with one package and one exact reference.\n"
    "  visibility: PUBLIC\n"
    f"  entry: \"@global/{os.environ['ENTRY_SLUG']}\"\n"
    "  members:\n"
    f"    - skill: \"@global/{os.environ['ENTRY_SLUG']}\"\n"
    "      package:\n"
    "        path: skills/entry\n"
    "        visibility: PUBLIC\n"
    f"    - skill: \"@global/{os.environ['REFERENCE_SLUG']}\"\n"
    "      reference:\n"
    "        version: 1.0.0\n"
  )
with zipfile.ZipFile(os.environ["OUTPUT"], "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("SUITE.yaml", manifest)
    archive.write(member, "skills/entry/SKILL.md")
PY
}

poll_skill_status() {
  local cookie_file="$1"
  local slug="$2"
  local expected="$3"
  local response=""
  for _ in $(seq 1 120); do
    response="$(curl -fsS -b "$cookie_file" "$BASE_URL/api/web/skills/global/$slug")"
    if JSON_INPUT="$response" EXPECTED="$expected" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"]).get("data") or {}
versions = [data.get("headlineVersion") or {}, data.get("ownerPreviewVersion") or {}, data.get("publishedVersion") or {}]
raise SystemExit(0 if any(item.get("status") == os.environ["EXPECTED"] for item in versions) else 1)
PY
    then
      printf '%s' "$response"
      return 0
    fi
    sleep 1
  done
  echo "FAIL: $slug did not reach $expected" >&2
  return 1
}

poll_operation() {
  local cookie_file="$1"
  local operation_id="$2"
  local response=""
  for _ in $(seq 1 120); do
    response="$(curl -fsS -b "$cookie_file" "$BASE_URL/api/web/suite-bundles/operations/$operation_id")"
    if [[ "$(json_field "$response" data.status)" == "SUITE_DRAFT_CREATED" ]]; then
      printf '%s' "$response"
      return 0
    fi
    sleep 1
  done
  echo "FAIL: Bundle operation $operation_id did not create a Suite draft" >&2
  return 1
}

echo "=== Suite Bundle Release Compose Smoke Test ==="
echo "Target: $BASE_URL"
echo "Suite:  @global/$SUITE_SLUG"

login_admin "$ADMIN_COOKIE"
ADMIN_CSRF="$(csrf_token "$ADMIN_COOKIE")"

bootstrap_cookie "$USER_COOKIE"
USER_CSRF="$(csrf_token "$USER_COOKIE")"
REGISTER_RESPONSE="$(curl -fsS -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" -H "Content-Type: application/json" \
  -X POST "$BASE_URL/api/v1/auth/local/register" \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASSWORD\",\"email\":\"$USER_NAME@example.test\"}")"
assert_code "register a non-admin Skill owner" "$REGISTER_RESPONSE"
USER_CSRF="$(csrf_token "$USER_COOKIE")"

make_skill_zip "$REFERENCE_SLUG" 1.0.0 "$WORK_DIR/reference.zip"
REFERENCE_PUBLISH="$(curl -fsS -b "$USER_COOKIE" -c "$USER_COOKIE" \
  -H "X-XSRF-TOKEN: $USER_CSRF" \
  -F "file=@$WORK_DIR/reference.zip;type=application/zip" -F "visibility=PUBLIC" \
  "$BASE_URL/api/web/skills/global/publish")"
assert_code "non-admin publishes reference Skill for review" "$REFERENCE_PUBLISH"
REFERENCE_SKILL_ID="$(json_field "$REFERENCE_PUBLISH" data.skillId)"
poll_skill_status "$USER_COOKIE" "$REFERENCE_SLUG" PENDING_REVIEW >/dev/null
echo "PASS: reference Skill reaches PENDING_REVIEW"

GLOBAL_NAMESPACE="$(curl -fsS -b "$ADMIN_COOKIE" "$BASE_URL/api/web/namespaces/global")"
GLOBAL_NAMESPACE_ID="$(json_field "$GLOBAL_NAMESPACE" data.id)"
REVIEWS="$(curl -fsS -b "$ADMIN_COOKIE" "$BASE_URL/api/web/reviews?status=PENDING&namespaceId=$GLOBAL_NAMESPACE_ID")"
REVIEW_ID="$(JSON_INPUT="$REVIEWS" SLUG="$REFERENCE_SLUG" python3 - <<'PY'
import json
import os

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
match = next((item for item in items if item["skillSlug"] == os.environ["SLUG"]), None)
print(match["id"] if match else "")
PY
)"
[[ -n "$REVIEW_ID" ]] || { echo "FAIL: pending review was not found" >&2; exit 1; }
APPROVE="$(curl -fsS -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" -X POST \
  "$BASE_URL/api/web/reviews/$REVIEW_ID/approve" -d '{"comment":"release compose Bundle smoke"}')"
assert_code "administrator approves the foreign-owned reference Skill" "$APPROVE"
poll_skill_status "$USER_COOKIE" "$REFERENCE_SLUG" PUBLISHED >/dev/null
echo "PASS: foreign-owned reference Skill is PUBLISHED"

run_bundle() {
  local mode="$1"
  local version="$2"
  local base_version="$3"
  local archive="$WORK_DIR/bundle-${version}.zip"
  make_bundle_zip "$mode" "$version" "$base_version" "$archive"
  local preview
  preview="$(curl -fsS -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
    -F "file=@$archive;type=application/zip" "$BASE_URL/api/web/suite-bundles/preview")"
  assert_code "$mode Bundle preview" "$preview"
  JSON_INPUT="$preview" MODE="$mode" VERSION="$version" ENTRY="$ENTRY_SLUG" REFERENCE="$REFERENCE_SLUG" python3 - <<'PY'
import json
import os

data = json.loads(os.environ["JSON_INPUT"])["data"]
members = {item["coordinate"]: item for item in data["members"]}
assert data["confirmable"] is True
assert data["target"]["mode"] == os.environ["MODE"]
assert data["target"]["targetVersion"] == os.environ["VERSION"]
entry = members[f"@global/{os.environ['ENTRY']}"]
reference = members[f"@global/{os.environ['REFERENCE']}"]
assert entry["sourceType"] == "PACKAGE"
assert entry["packagePath"] == "skills/entry"
assert entry["publishAction"] in {"CREATE_SKILL", "CREATE_VERSION"}
assert reference["sourceType"] == "REFERENCE"
assert reference["publishAction"] == "REFERENCE_VERSION"
PY
  echo "PASS: $mode preview exposes package path, actions, and exact foreign reference"
  local token digest confirmation operation_id
  token="$(json_field "$preview" data.previewToken)"
  digest="$(json_field "$preview" data.warningDigest)"
  confirmation="$(curl -fsS -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
    -H "Content-Type: application/json" -H "Idempotency-Key: $mode-$TOKEN" \
    -X POST "$BASE_URL/api/web/suite-bundles/previews/$token/confirm" \
    -d "{\"warningDigest\":\"$digest\"}")"
  assert_code "$mode Bundle confirmation" "$confirmation"
  operation_id="$(json_field "$confirmation" data.operationId)"

  login_admin "$RECOVERY_COOKIE"
  local recovered
  recovered="$(poll_operation "$RECOVERY_COOKIE" "$operation_id")"
  assert_code "$mode operation is recoverable after a fresh login" "$recovered"
  echo "PASS: $mode operation reaches SUITE_DRAFT_CREATED"
}

publish_public_suite() {
  local version_id="$1"
  local submit reviews review_id approve
  submit="$(curl -fsS -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
    -X POST "$BASE_URL/api/web/suites/$SUITE_ID/versions/$version_id/submit")"
  assert_code "submit public Suite version for review" "$submit"
  reviews="$(curl -fsS -b "$ADMIN_COOKIE" \
    "$BASE_URL/api/web/reviews?status=PENDING&namespaceId=$GLOBAL_NAMESPACE_ID")"
  review_id="$(JSON_INPUT="$reviews" VERSION_ID="$version_id" python3 - <<'PY'
import json
import os

items = json.loads(os.environ["JSON_INPUT"])["data"]["items"]
match = next((item for item in items
              if item.get("subjectType") == "SUITE_VERSION"
              and str(item.get("subjectVersionId")) == os.environ["VERSION_ID"]), None)
print(match["id"] if match else "")
PY
)"
  [[ -n "$review_id" ]] || { echo "FAIL: pending Suite review was not found" >&2; exit 1; }
  approve="$(curl -fsS -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
    -H "Content-Type: application/json" -X POST \
    "$BASE_URL/api/web/suites/reviews/$review_id/approve" \
    -d '{"comment":"release compose Bundle smoke"}')"
  assert_code "approve public Suite version review" "$approve"
}

run_bundle CREATE 1.0.0 ""
CREATE_DETAIL="$(curl -fsS -b "$ADMIN_COOKIE" "$BASE_URL/api/web/suites/global/$SUITE_SLUG?version=1.0.0")"
assert_code "load created Suite draft" "$CREATE_DETAIL"
SUITE_ID="$(json_field "$CREATE_DETAIL" data.id)"
SUITE_VERSION_ID="$(json_field "$CREATE_DETAIL" data.versionId)"
ENTRY_SKILL_ID="$(json_field "$CREATE_DETAIL" data.members.0.skillId)"

LABEL_CREATE="$(curl -fsS -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -H "Content-Type: application/json" -X POST "$BASE_URL/api/v1/admin/labels" \
  -d "{\"slug\":\"$LABEL_SLUG\",\"type\":\"RECOMMENDED\",\"visibleInFilter\":true,\"sortOrder\":10,\"translations\":[{\"locale\":\"en\",\"displayName\":\"Bundle smoke\"}]}")"
assert_code "create Suite smoke label definition" "$LABEL_CREATE"
LABEL_CREATED=true
LABEL_ATTACH="$(curl -fsS -b "$ADMIN_COOKIE" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -X PUT "$BASE_URL/api/web/suites/global/$SUITE_SLUG/labels/$LABEL_SLUG")"
assert_code "attach direct Suite label" "$LABEL_ATTACH"

publish_public_suite "$SUITE_VERSION_ID"

ENTRY_DETAIL="$(curl -fsS -b "$ADMIN_COOKIE" "$BASE_URL/api/web/skills/global/$ENTRY_SLUG")"
JSON_INPUT="$ENTRY_DETAIL" SUITE="$SUITE_SLUG" python3 - <<'PY'
import json
import os

items = json.loads(os.environ["JSON_INPUT"])["data"]["memberOfSuites"]["items"]
match = next(item for item in items if item["slug"] == os.environ["SUITE"])
assert match["currentSkillEntry"] is True
PY
echo "PASS: Entry Skill reverse discovery identifies the published Suite"

REFERENCE_DETAIL="$(curl -fsS -b "$USER_COOKIE" "$BASE_URL/api/web/skills/global/$REFERENCE_SLUG")"
JSON_INPUT="$REFERENCE_DETAIL" SUITE="$SUITE_SLUG" python3 - <<'PY'
import json
import os

items = json.loads(os.environ["JSON_INPUT"])["data"]["memberOfSuites"]["items"]
match = next(item for item in items if item["slug"] == os.environ["SUITE"])
assert match["currentSkillEntry"] is False
PY
echo "PASS: non-entry foreign Skill reverse discovery identifies the published Suite"

ENTRY_FILE="$(curl -fsS -b "$ADMIN_COOKIE" \
  "$BASE_URL/api/web/skills/global/$ENTRY_SLUG/versions/1.0.0/file?path=SKILL.md")"
if [[ "$ENTRY_FILE" != *"# Bundle entry"* ]]; then
  echo "FAIL: pinned Entry Skill instructions do not contain the expected content" >&2
  exit 1
fi
echo "PASS: read the pinned Entry Skill instructions"

run_bundle UPDATE 1.1.0 1.0.0
UPDATE_DETAIL="$(curl -fsS -b "$ADMIN_COOKIE" "$BASE_URL/api/web/suites/global/$SUITE_SLUG?version=1.1.0")"
assert_code "load updated Suite draft" "$UPDATE_DETAIL"
UPDATE_VERSION_ID="$(json_field "$UPDATE_DETAIL" data.versionId)"
publish_public_suite "$UPDATE_VERSION_ID"

LABELS="$(curl -fsS -b "$ADMIN_COOKIE" "$BASE_URL/api/web/suites/global/$SUITE_SLUG/labels")"
JSON_INPUT="$LABELS" LABEL="$LABEL_SLUG" python3 - <<'PY'
import json
import os

items = json.loads(os.environ["JSON_INPUT"])["data"]
assert any(item["slug"] == os.environ["LABEL"] for item in items)
PY
echo "PASS: Suite label persists after Bundle version update"

echo "=== Suite Bundle Release Compose Smoke Test Passed ==="
