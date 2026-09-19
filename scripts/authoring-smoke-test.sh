#!/usr/bin/env bash

set -euo pipefail

# Authoring platform live smoke test.
#
# Drives the full draft lifecycle over HTTP against a running SkillHub server:
#   case 1 — happy path: create draft → edit files → bind runtime → validate
#            (structure + config + behavior) → SSE replay → submit
#   case 2 — fix loop: broken SKILL.md → FAILED run → apply suggested fix →
#            revision bump → re-validate → SUCCEEDED
#   case 3 — guards: submit without validation and cross-user access are rejected
#   case 4 — MCP probe: dead MCP endpoint → MCP_CONNECT_FAILED finding; a local
#            fake MCP server → tools discovered in the event log; unknown
#            toolFilters → warning without failing the run
#
# Requires the local dev profile (mock auth enabled). Usage:
#   scripts/authoring-smoke-test.sh [base-url]

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
COOKIE="$(mktemp)"
RUN_ID="$(date +%s)"
FAKE_MCP_PID=""
FAKE_MCP_PORT_FILE="$(mktemp)"

cleanup() {
  [[ -n "$FAKE_MCP_PID" ]] && kill "$FAKE_MCP_PID" 2>/dev/null || true
  rm -f "$COOKIE" "$FAKE_MCP_PORT_FILE"
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
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE" | tail -n 1
}

bootstrap_csrf() {
  curl -s -c "$COOKIE" -H "X-Mock-User-Id: local-user" "$BASE_URL/api/v1/auth/providers" >/dev/null
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

# Polls a run until terminal; echoes the terminal status.
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

echo "=== Authoring Platform Smoke Test ==="
echo "Target: $BASE_URL"
echo

bootstrap_csrf
CSRF="$(csrf_token "$COOKIE")"
if [[ -z "$CSRF" ]]; then
  echo "Could not bootstrap CSRF token (is mock auth enabled?)"
  exit 1
fi

# ---------------------------------------------------------------- fixture namespace

# Discover an existing namespace the user can author in (namespace creation is
# admin-gated on the dev profile, so the smoke test reuses "global" or any
# namespace the mock user already owns).
NS_SLUG="$(api GET /api/web/me/namespaces | python3 -c 'import json,os,sys; data=json.loads(sys.stdin.read())["data"]; print(data[0]["slug"] if data else "")')"
if [[ -n "$NS_SLUG" ]]; then
  pass "Authoring namespace available: $NS_SLUG"
else
  fail "No accessible namespace for the mock user"
  echo "=== $FAIL check(s) failed ==="
  exit 1
fi
SLUG="$NS_SLUG"

# ---------------------------------------------------------------- case 1: happy path

echo
echo "--- Case 1: draft validates across all layers and submits ---"

# Draft names are unique per run so the smoke test is re-runnable against a
# shared dev database.
NAME="smoke-skill-$RUN_ID"
DRAFT_RESPONSE="$(api POST /api/web/authoring/drafts \
  "{\"namespaceSlug\":\"$SLUG\",\"name\":\"$NAME\",\"requirement\":\"smoke test skill\"}")"
assert_code "Draft created with scaffold" "$DRAFT_RESPONSE" "0"
DRAFT_ID="$(json_field "$DRAFT_RESPONSE" "data.id")"

SKILL_MD="---
name: $NAME
description: greets the caller with a fixed message
---

# Smoke skill

## Overview

greets the caller with a fixed message
"
FILES_RESPONSE="$(api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  "{\"path\":\"SKILL.md\",\"content\":$(python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<< "$SKILL_MD"),\"contentType\":\"text/markdown\"}")"
assert_code "SKILL.md saved" "$FILES_RESPONSE" "0"

SCRIPT_RESPONSE="$(api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  '{"path":"scripts/greet.sh","content":"echo hello from smoke\n"}')"
assert_code "Script file saved" "$SCRIPT_RESPONSE" "0"

VALIDATION_YAML='version: 1
tasks:
  - name: greet
    description: script prints the greeting
    type: script
    script: scripts/greet.sh
    args: []
    timeoutMs: 10000
    assertions:
      - type: exit_code
        equals: 0
      - type: stdout_contains
        value: hello from smoke
'
SPEC_RESPONSE="$(api PUT "/api/web/authoring/drafts/$DRAFT_ID/files" \
  "{\"path\":\"validation.yaml\",\"content\":$(python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<< "$VALIDATION_YAML")}")"
assert_code "validation.yaml saved" "$SPEC_RESPONSE" "0"

BINDING_RESPONSE="$(api PUT "/api/web/authoring/drafts/$DRAFT_ID/runtime" \
  '{"agentType":"local-script","config":{"interpreter":"sh"},"toolAllowlist":[],"mcpServers":[]}')"
assert_code "Runtime binding saved" "$BINDING_RESPONSE" "0"

RUN_RESPONSE="$(api POST "/api/web/authoring/drafts/$DRAFT_ID/runs")"
assert_code "Validation run started" "$RUN_RESPONSE" "0"
RUN_ID="$(json_field "$RUN_RESPONSE" "data.id")"

RUN_STATUS="$(await_run_terminal "$RUN_ID")"
assert_equals "Run reaches SUCCEEDED" "SUCCEEDED" "$RUN_STATUS"

FINDINGS_RESPONSE="$(api GET "/api/web/authoring/runs/$RUN_ID/findings")"
assert_code "Findings readable" "$FINDINGS_RESPONSE" "0"
FINDING_COUNT="$(JSON_INPUT="$FINDINGS_RESPONSE" python3 -c 'import json,os; print(len(json.loads(os.environ["JSON_INPUT"])["data"]))')"
assert_equals "Zero findings on valid draft" "0" "$FINDING_COUNT"

EVENTS_RESPONSE="$(api GET "/api/web/authoring/runs/$RUN_ID/events")"
assert_code "Events readable (polling API)" "$EVENTS_RESPONSE" "0"
EVENT_COUNT="$(JSON_INPUT="$EVENTS_RESPONSE" python3 -c 'import json,os; print(len(json.loads(os.environ["JSON_INPUT"])["data"]))')"
if [[ "$EVENT_COUNT" -gt 4 ]]; then
  pass "Run produced $EVENT_COUNT events"
else
  fail "Expected more than 4 events, got $EVENT_COUNT"
fi

SSE_OUTPUT="$(curl -sN --max-time 10 -H "X-Mock-User-Id: local-user" -b "$COOKIE" \
  "$BASE_URL/api/web/authoring/runs/$RUN_ID/events/stream" || true)"
if grep -q "RUN_STARTED" <<< "$SSE_OUTPUT" && grep -q "RUN_FINISHED" <<< "$SSE_OUTPUT"; then
  pass "SSE stream replays all events and terminates"
else
  fail "SSE stream did not replay the full run"
fi

DRAFT_AFTER="$(api GET "/api/web/authoring/drafts/$DRAFT_ID")"
assert_equals "Draft marked validated" "True" "$(json_field "$DRAFT_AFTER" "data.validated")"

SUBMIT_RESPONSE="$(api POST "/api/web/authoring/drafts/$DRAFT_ID/submit" '{"visibility":"PRIVATE"}')"
assert_code "Validated draft submits to publish pipeline" "$SUBMIT_RESPONSE" "0"
SKILL_ID="$(json_field "$SUBMIT_RESPONSE" "data.skillId")"
if [[ "$SKILL_ID" != "None" && -n "$SKILL_ID" ]]; then
  pass "Submit returned skill id $SKILL_ID"
else
  fail "Submit did not return a skill id"
fi

# ---------------------------------------------------------------- case 2: fix loop

echo
echo "--- Case 2: broken draft gets a fixable finding and revalidates ---"

BROKEN_NAME="broken-skill-$RUN_ID"
BROKEN_RESPONSE="$(api POST /api/web/authoring/drafts \
  "{\"namespaceSlug\":\"$SLUG\",\"name\":\"$BROKEN_NAME\"}")"
assert_code "Broken draft created" "$BROKEN_RESPONSE" "0"
BROKEN_ID="$(json_field "$BROKEN_RESPONSE" "data.id")"

BROKEN_MD="---
name: $BROKEN_NAME
---

# Broken
"
api PUT "/api/web/authoring/drafts/$BROKEN_ID/files" \
  "{\"path\":\"SKILL.md\",\"content\":$(python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<< "$BROKEN_MD"),\"contentType\":\"text/markdown\"}" >/dev/null

BROKEN_RUN="$(api POST "/api/web/authoring/drafts/$BROKEN_ID/runs")"
BROKEN_RUN_ID="$(json_field "$BROKEN_RUN" "data.id")"
assert_equals "Broken draft fails validation" "FAILED" "$(await_run_terminal "$BROKEN_RUN_ID")"

BROKEN_FINDINGS="$(api GET "/api/web/authoring/runs/$BROKEN_RUN_ID/findings")"
FINDING_ID="$(JSON_INPUT="$BROKEN_FINDINGS" python3 -c '
import json, os
findings = json.loads(os.environ["JSON_INPUT"])["data"]
match = next((f for f in findings if f["ruleCode"] == "FRONTMATTER_FIELD_MISSING"), None)
print(match["id"] if match else "none")')"
if [[ "$FINDING_ID" != "none" ]]; then
  pass "FRONTMATTER_FIELD_MISSING finding reported with location"
else
  fail "Expected FRONTMATTER_FIELD_MISSING finding"
fi

REVISION_BEFORE="$(json_field "$(api GET "/api/web/authoring/drafts/$BROKEN_ID")" "data.revision")"
APPLY_RESPONSE="$(api POST "/api/web/authoring/runs/$BROKEN_RUN_ID/findings/$FINDING_ID/apply")"
assert_code "Suggested fix applied" "$APPLY_RESPONSE" "0"
REVISION_AFTER="$(json_field "$(api GET "/api/web/authoring/drafts/$BROKEN_ID")" "data.revision")"
if [[ "$REVISION_AFTER" -gt "$REVISION_BEFORE" ]]; then
  pass "Applying fix advanced revision $REVISION_BEFORE → $REVISION_AFTER"
else
  fail "Applying fix did not advance the revision"
fi

FIXED_RUN="$(api POST "/api/web/authoring/drafts/$BROKEN_ID/runs")"
FIXED_RUN_ID="$(json_field "$FIXED_RUN" "data.id")"
assert_equals "Re-validation after fix succeeds" "SUCCEEDED" "$(await_run_terminal "$FIXED_RUN_ID")"

# ---------------------------------------------------------------- case 3: guards

echo
echo "--- Case 3: guards ---"

UNVALIDATED_RESPONSE="$(api POST /api/web/authoring/drafts \
  "{\"namespaceSlug\":\"$SLUG\",\"name\":\"unvalidated-skill-$RUN_ID\"}")"
UNVALIDATED_ID="$(json_field "$UNVALIDATED_RESPONSE" "data.id")"
SUBMIT_UNVALIDATED="$(api POST "/api/web/authoring/drafts/$UNVALIDATED_ID/submit" '{"visibility":"PRIVATE"}')"
if [[ "$(json_field "$SUBMIT_UNVALIDATED" "code")" != "0" ]]; then
  pass "Submit rejected for unvalidated draft"
else
  fail "Submit should be rejected for an unvalidated draft"
fi

OTHER_USER_COOKIE="$(mktemp)"
# A plain second user (no admin roles): local-admin holds SUPER_ADMIN in dev data
# and platform admins are allowed to read any draft by design.
curl -s -c "$OTHER_USER_COOKIE" -H "X-Mock-User-Id: local-plain-user" "$BASE_URL/api/v1/auth/providers" >/dev/null
OTHER_CSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$OTHER_USER_COOKIE" | tail -n 1)"
CROSS_RESPONSE="$(curl -sS -H "X-Mock-User-Id: local-plain-user" -b "$OTHER_USER_COOKIE" \
  -H "X-XSRF-TOKEN: $OTHER_CSRF" "$BASE_URL/api/web/authoring/drafts/$DRAFT_ID")"
if [[ "$(json_field "$CROSS_RESPONSE" "code")" != "0" ]]; then
  pass "Cross-user draft access rejected"
else
  fail "Another user should not read someone else's draft"
fi
rm -f "$OTHER_USER_COOKIE"

# ---------------------------------------------------------------- case 4: MCP probe

echo
echo "--- Case 4: declared MCP servers are probed for real ---"

MCP_NAME="mcp-skill-$RUN_ID"
MCP_RESPONSE="$(api POST /api/web/authoring/drafts \
  "{\"namespaceSlug\":\"$SLUG\",\"name\":\"$MCP_NAME\"}")"
assert_code "MCP probe draft created" "$MCP_RESPONSE" "0"
MCP_DRAFT_ID="$(json_field "$MCP_RESPONSE" "data.id")"

MCP_MD="---
name: $MCP_NAME
description: declares MCP servers on its runtime binding
---

# MCP probe skill
"
api PUT "/api/web/authoring/drafts/$MCP_DRAFT_ID/files" \
  "{\"path\":\"SKILL.md\",\"content\":$(python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<< "$MCP_MD"),\"contentType\":\"text/markdown\"}" >/dev/null

# Negative: an endpoint nothing listens on must surface as a config-layer error.
DEAD_BINDING="$(api PUT "/api/web/authoring/drafts/$MCP_DRAFT_ID/runtime" \
  '{"agentType":"local-script","config":{"interpreter":"sh"},"toolAllowlist":[],"mcpServers":[{"name":"dead-server","transport":"http","endpoint":"http://127.0.0.1:9/mcp"}]}')"
assert_code "Binding with dead MCP server accepted" "$DEAD_BINDING" "0"

DEAD_RUN_ID="$(json_field "$(api POST "/api/web/authoring/drafts/$MCP_DRAFT_ID/runs")" "data.id")"
assert_equals "Dead MCP server fails the run" "FAILED" "$(await_run_terminal "$DEAD_RUN_ID")"

DEAD_FINDINGS="$(api GET "/api/web/authoring/runs/$DEAD_RUN_ID/findings")"
DEAD_RULE="$(JSON_INPUT="$DEAD_FINDINGS" python3 -c '
import json, os
findings = json.loads(os.environ["JSON_INPUT"])["data"]
match = next((f for f in findings if f["ruleCode"] == "MCP_CONNECT_FAILED"), None)
print(match["ruleCode"] if match else "none")')"
assert_equals "MCP_CONNECT_FAILED finding reported" "MCP_CONNECT_FAILED" "$DEAD_RULE"

# Positive: a local fake MCP server; the probe must connect and list its tools.
FAKE_MCP_SCRIPT='
import json
from http.server import BaseHTTPRequestHandler, HTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        msg = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        method = msg.get("method", "")
        if method == "initialize":
            self.reply(msg, {"protocolVersion": "2024-11-05", "capabilities": {},
                             "serverInfo": {"name": "smoke-mcp", "version": "1.0"}},
                       session="smoke-session-1")
        elif method == "tools/list":
            self.reply(msg, {"tools": [{"name": "echo", "description": "echo text",
                                        "inputSchema": {"type": "object"}}]})
        elif method == "tools/call":
            self.reply(msg, {"content": [{"type": "text", "text": "ok"}]})
        else:
            self.reply(msg, {})

    def reply(self, msg, result, session=None):
        body = json.dumps({"jsonrpc": "2.0", "id": msg.get("id"), "result": result}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if session:
            self.send_header("Mcp-Session-Id", session)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass

server = HTTPServer(("127.0.0.1", 0), Handler)
print(server.server_address[1], flush=True)
server.serve_forever()
'
python3 -c "$FAKE_MCP_SCRIPT" > "$FAKE_MCP_PORT_FILE" 2>/dev/null &
FAKE_MCP_PID=$!
MCP_PORT=""
for _ in $(seq 1 25); do
  MCP_PORT="$(cat "$FAKE_MCP_PORT_FILE" 2>/dev/null || true)"
  [[ -n "$MCP_PORT" ]] && break
  sleep 0.2
done
if [[ -n "$MCP_PORT" ]]; then
  pass "Fake MCP server listening on port $MCP_PORT"
else
  fail "Fake MCP server did not start"
fi

LIVE_BINDING="$(api PUT "/api/web/authoring/drafts/$MCP_DRAFT_ID/runtime" \
  "{\"agentType\":\"local-script\",\"config\":{\"interpreter\":\"sh\"},\"toolAllowlist\":[],\"mcpServers\":[{\"name\":\"smoke-mcp\",\"transport\":\"http\",\"endpoint\":\"http://127.0.0.1:$MCP_PORT/mcp\",\"toolFilters\":[\"echo\"]}]}")"
assert_code "Binding with live MCP server accepted" "$LIVE_BINDING" "0"

LIVE_RUN_ID="$(json_field "$(api POST "/api/web/authoring/drafts/$MCP_DRAFT_ID/runs")" "data.id")"
assert_equals "Live MCP server lets the run pass" "SUCCEEDED" "$(await_run_terminal "$LIVE_RUN_ID")"

LIVE_EVENTS="$(api GET "/api/web/authoring/runs/$LIVE_RUN_ID/events")"
if grep -q "connected; tools: echo" <<< "$LIVE_EVENTS"; then
  pass "Event log records discovered MCP tools"
else
  fail "Event log lacks the MCP tools discovery line"
fi

# toolFilters naming a tool the server does not expose is a warning, not an error.
FILTERED_BINDING="$(api PUT "/api/web/authoring/drafts/$MCP_DRAFT_ID/runtime" \
  "{\"agentType\":\"local-script\",\"config\":{\"interpreter\":\"sh\"},\"toolAllowlist\":[],\"mcpServers\":[{\"name\":\"smoke-mcp\",\"transport\":\"http\",\"endpoint\":\"http://127.0.0.1:$MCP_PORT/mcp\",\"toolFilters\":[\"echo\",\"does_not_exist\"]}]}")"
assert_code "Binding with unknown tool filter accepted" "$FILTERED_BINDING" "0"

FILTERED_RUN_ID="$(json_field "$(api POST "/api/web/authoring/drafts/$MCP_DRAFT_ID/runs")" "data.id")"
assert_equals "Unknown tool filter still succeeds" "SUCCEEDED" "$(await_run_terminal "$FILTERED_RUN_ID")"

FILTERED_FINDINGS="$(api GET "/api/web/authoring/runs/$FILTERED_RUN_ID/findings")"
FILTERED_RULE="$(JSON_INPUT="$FILTERED_FINDINGS" python3 -c '
import json, os
findings = json.loads(os.environ["JSON_INPUT"])["data"]
match = next((f for f in findings if f["ruleCode"] == "MCP_TOOL_FILTER_UNKNOWN"), None)
print(match["ruleCode"] if match else "none")')"
assert_equals "MCP_TOOL_FILTER_UNKNOWN warning reported" "MCP_TOOL_FILTER_UNKNOWN" "$FILTERED_RULE"

kill "$FAKE_MCP_PID" 2>/dev/null || true
FAKE_MCP_PID=""

# ---------------------------------------------------------------- summary

echo
if [[ "$FAIL" -eq 0 ]]; then
  echo "=== All $PASS checks passed ==="
  exit 0
fi
echo "=== $FAIL check(s) failed, $PASS passed ==="
exit 1
