#!/usr/bin/env bash

set -euo pipefail

# Publish-pipeline end-to-end evidence run for the authoring platform.
#
# Extends scripts/authoring-smoke-test.sh past the submit step: a validated
# draft is submitted as PUBLIC by a regular user, the security scanner
# (skillhub-skill-scanner-1) scans it asynchronously, a platform admin
# approves the review task, and the version reaches the PUBLISHED terminal
# state with the skill publicly retrievable.
#
# Each check prints PASS/FAIL plus the observed identifiers so the output can
# be pasted directly as evidence. Requires the local dev profile (mock auth,
# Postgres/Redis/scanner/minio from the compose stack). Usage:
#   scripts/authoring-publish-e2e.sh [base-url]

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
COOKIE="$(mktemp)"
ADMIN_COOKIE="$(mktemp)"
RUN_ID="$(date +%s)"
NAME="publish-evidence-$RUN_ID"
PSQL=(docker exec skillhub-postgres-1 psql -U skillhub -d skillhub -tAc)

cleanup() {
  rm -f "$COOKIE" "$ADMIN_COOKIE"
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

assert_equals() {
  local description="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    pass "$description"
  else
    fail "$description (expected '$expected', got '$actual')"
  fi
}

api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  if [[ -n "$data" ]]; then
    curl -sS -H "X-Mock-User-Id: local-user" -b "$COOKIE" -c "$COOKIE" \
      -H "X-XSRF-TOKEN: $CSRF" -H "Content-Type: application/json" \
      -X "$method" "$BASE_URL$path" -d "$data"
  else
    curl -sS -H "X-Mock-User-Id: local-user" -b "$COOKIE" -c "$COOKIE" \
      -H "X-XSRF-TOKEN: $CSRF" -X "$method" "$BASE_URL$path"
  fi
}

admin_api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  if [[ -n "$data" ]]; then
    curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
      -H "X-XSRF-TOKEN: $ADMIN_CSRF" -H "Content-Type: application/json" \
      -X "$method" "$BASE_URL$path" -d "$data"
  else
    curl -sS -H "X-Mock-User-Id: local-admin" -b "$ADMIN_COOKIE" -c "$ADMIN_COOKIE" \
      -H "X-XSRF-TOKEN: $ADMIN_CSRF" -X "$method" "$BASE_URL$path"
  fi
}

await_run_terminal() {
  local run_id="$1"
  local status=""
  for _ in $(seq 1 60); do
    status="$(json_field "$(api GET "/api/web/authoring/runs/$run_id")" "data.status")"
    case "$status" in
      SUCCEEDED|FAILED|CANCELLED|TIMED_OUT) break ;;
    esac
    sleep 1
  done
  echo "$status"
}

await_db_value() {
  local query="$1"
  local expected="$2"
  local timeout="${3:-60}"
  local value=""
  for _ in $(seq 1 "$timeout"); do
    value="$("${PSQL[@]}" "$query" 2>/dev/null || true)"
    [[ "$value" == "$expected" ]] && break
    sleep 1
  done
  echo "$value"
}

echo "=== Authoring → Publish Pipeline E2E Evidence ==="
echo "Target: $BASE_URL   Run: $RUN_ID"
echo

curl -s -c "$COOKIE" -H "X-Mock-User-Id: local-user" "$BASE_URL/api/v1/auth/providers" >/dev/null
CSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE" | tail -n 1)"
curl -s -c "$ADMIN_COOKIE" -H "X-Mock-User-Id: local-admin" "$BASE_URL/api/v1/auth/providers" >/dev/null
ADMIN_CSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$ADMIN_COOKIE" | tail -n 1)"

# ---------------------------------------------------------------- author → validate

echo "--- Stage 1: author and validate (regular user local-user) ---"

DRAFT_RESPONSE="$(api POST /api/web/authoring/drafts "{\"namespaceSlug\":\"global\",\"name\":\"$NAME\"}")"
assert_equals "Draft created" "0" "$(json_field "$DRAFT_RESPONSE" "code")"
DRAFT_ID="$(json_field "$DRAFT_RESPONSE" "data.id")"
echo "      draft id: $DRAFT_ID"

SKILL_MD="---
name: $NAME
description: publish pipeline evidence skill for run $RUN_ID
---

# Publish pipeline evidence

Runs a shell check that must succeed before submission.
"
api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  "{\"path\":\"SKILL.md\",\"content\":$(python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<< "$SKILL_MD"),\"contentType\":\"text/markdown\"}" >/dev/null
api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  "{\"path\":\"scripts/check.sh\",\"content\":\"#!/bin/sh\necho publish-evidence-ok\n\",\"contentType\":\"text/x-shellscript\"}" >/dev/null
api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  "{\"path\":\"validation.yaml\",\"content\":\"version: 1\\ntasks:\\n  - name: check\\n    description: script prints the evidence marker\\n    type: script\\n    script: scripts/check.sh\\n    args: []\\n    timeoutMs: 10000\\n    assertions:\\n      - type: exit_code\\n        equals: 0\\n      - type: stdout_contains\\n        value: publish-evidence-ok\\n\",\"contentType\":\"application/yaml\"}" >/dev/null

BINDING_RESPONSE="$(api PUT "/api/web/authoring/drafts/$DRAFT_ID/runtime" \
  '{"agentType":"local-script","config":{"interpreter":"sh"},"toolAllowlist":[]}')"
assert_equals "local-script binding saved" "0" "$(json_field "$BINDING_RESPONSE" "code")"

RUN_RESPONSE="$(api POST "/api/web/authoring/drafts/$DRAFT_ID/runs")"
RUN_ID_LOCAL="$(json_field "$RUN_RESPONSE" "data.id")"
assert_equals "Validation run SUCCEEDED" "SUCCEEDED" "$(await_run_terminal "$RUN_ID_LOCAL")"

# ---------------------------------------------------------------- submit PUBLIC

echo
echo "--- Stage 2: submit as PUBLIC (enters publish pipeline) ---"

SUBMIT_RESPONSE="$(api POST "/api/web/authoring/drafts/$DRAFT_ID/submit" '{"visibility":"PUBLIC"}')"
assert_equals "Draft submitted as PUBLIC" "0" "$(json_field "$SUBMIT_RESPONSE" "code")"
SKILL_ID="$(json_field "$SUBMIT_RESPONSE" "data.skillId")"
VERSION_ID="$(json_field "$SUBMIT_RESPONSE" "data.versionId")"
SKILL_VERSION="$(json_field "$SUBMIT_RESPONSE" "data.version")"
SLUG="$(json_field "$SUBMIT_RESPONSE" "data.slug")"
echo "      skill id: $SKILL_ID   version id: $VERSION_ID   slug: $SLUG   version: $SKILL_VERSION"

DB_STATUS="$("${PSQL[@]}" "SELECT status FROM skill_version WHERE id = $VERSION_ID")"
if [[ "$DB_STATUS" == "PENDING_REVIEW" || "$DB_STATUS" == "SCANNING" ]]; then
  pass "Version entered pipeline in $DB_STATUS (not auto-published)"
else
  fail "Version should be PENDING_REVIEW/SCANNING, got '$DB_STATUS'"
fi

# ---------------------------------------------------------------- security scan

echo
echo "--- Stage 3: asynchronous security scan (scanner on :8000) ---"

SCAN_STATUS="$(await_db_value \
  "SELECT status FROM skill_version WHERE id = $VERSION_ID" "PENDING_REVIEW" 90)"
assert_equals "Scan completed, version back to PENDING_REVIEW" "PENDING_REVIEW" "$SCAN_STATUS"

SCAN_ROW="$("${PSQL[@]}" "SELECT scanner_type || ':' || verdict || ':' || findings_count FROM security_audit WHERE skill_version_id = $VERSION_ID AND deleted_at IS NULL ORDER BY id DESC LIMIT 1")"
if [[ -n "$SCAN_ROW" && "$SCAN_ROW" != "" ]]; then
  pass "security_audit row recorded: $SCAN_ROW"
else
  fail "No security_audit row for version $VERSION_ID"
fi

REVIEW_TASK_ID="$("${PSQL[@]}" "SELECT id FROM review_task WHERE skill_version_id = $VERSION_ID ORDER BY id DESC LIMIT 1")"
if [[ -n "$REVIEW_TASK_ID" ]]; then
  pass "Review task $REVIEW_TASK_ID pending approval"
else
  fail "No review task created for version $VERSION_ID"
fi

# ---------------------------------------------------------------- admin approve

echo
echo "--- Stage 4: platform admin approves (local-admin) ---"

APPROVE_RESPONSE="$(admin_api POST "/api/web/reviews/$REVIEW_TASK_ID/approve" \
  '{"comment":"publish-pipeline evidence run"}')"
assert_equals "Review approved" "0" "$(json_field "$APPROVE_RESPONSE" "code")"
REVIEW_STATUS="$(json_field "$APPROVE_RESPONSE" "data.status")"
assert_equals "Review task status APPROVED" "APPROVED" "$REVIEW_STATUS"

# ---------------------------------------------------------------- terminal state

echo
echo "--- Stage 5: PUBLISHED terminal state ---"

DB_STATUS="$("${PSQL[@]}" "SELECT status FROM skill_version WHERE id = $VERSION_ID")"
assert_equals "skill_version.status = PUBLISHED (DB)" "PUBLISHED" "$DB_STATUS"

PUBLISHED_AT="$("${PSQL[@]}" "SELECT published_at FROM skill_version WHERE id = $VERSION_ID")"
if [[ -n "$PUBLISHED_AT" && "$PUBLISHED_AT" != "" ]]; then
  pass "published_at set: $PUBLISHED_AT"
else
  fail "published_at not set"
fi

VISIBILITY="$("${PSQL[@]}" "SELECT visibility FROM skill WHERE id = $SKILL_ID")"
assert_equals "skill.visibility = PUBLIC (DB)" "PUBLIC" "$VISIBILITY"

DETAIL="$(curl -sS -H "X-Mock-User-Id: local-plain-user" "$BASE_URL/api/web/skills/global/$SLUG")"
assert_equals "Skill publicly retrievable by another user" "0" "$(json_field "$DETAIL" "code")"
assert_equals "Public detail shows visibility PUBLIC" "PUBLIC" "$(json_field "$DETAIL" "data.visibility")"

SUBMITTED="$(api GET "/api/web/authoring/drafts/$DRAFT_ID")"
assert_equals "Draft shows submitted skill id $SKILL_ID" "$SKILL_ID" "$(json_field "$SUBMITTED" "data.submittedSkillId")"

# ---------------------------------------------------------------- summary

echo
if [[ "$FAIL" -eq 0 ]]; then
  echo "=== All $PASS checks passed — full chain: create → validate → submit → scan → review → publish ==="
  exit 0
fi
echo "=== $FAIL check(s) failed, $PASS passed ==="
exit 1
