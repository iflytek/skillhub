#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
CONTAINERS=()

cleanup() {
  local status=$?
  trap - EXIT
  if ((${#CONTAINERS[@]})); then
    docker rm -f "${CONTAINERS[@]}" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_DIR"
  exit "$status"
}
trap cleanup EXIT

if [[ -n "${SCANNER_IMAGE:-}" ]]; then
  IMAGE="$SCANNER_IMAGE"
else
  IMAGE="skillhub-scanner-contract-test:$(date +%s)"
  docker build -t "$IMAGE" "$REPO_ROOT/scanner"
fi

python3 - "$TMP_DIR" <<'PY'
import json
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
github_canary = "ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
openai_canary = "sk-proj-" + "Z9y8X7w6V5u4T3s2R1q0" * 3


def write_zip(name, files):
    with zipfile.ZipFile(root / name, "w", compression=zipfile.ZIP_STORED) as archive:
        for path, content in files.items():
            archive.writestr(path, content)


manifest = """---
name: contract-skill
description: Scanner 2.1 runtime contract fixture.
---

Harmless contract fixture.
"""
write_zip("safe.zip", {"contract-skill/SKILL.md": manifest})
write_zip(
    "javascript-secret.zip",
    {
        "contract-skill/SKILL.md": manifest,
        "contract-skill/index.js": f'const githubToken = "{github_canary}";\n',
    },
)
write_zip(
    "dotenv-secret.zip",
    {
        "contract-skill/SKILL.md": manifest,
        "contract-skill/.env": f"OPENAI_API_KEY={openai_canary}\n",
    },
)

large_files = {"large-skill/SKILL.md": manifest.replace("contract-skill", "large-skill")}
for index in range(5):
    large_files[f"large-skill/payload-{index}.txt"] = b"x" * (10 * 1024 * 1024)
large_files["large-skill/payload-5.txt"] = b"x" * (1024 * 1024)
write_zip("large-51mib.zip", large_files)

(root / "canaries.json").write_text(
    json.dumps({"github": github_canary, "openai": openai_canary}), encoding="utf-8"
)

local_skill = root / "local-scan" / "contract-skill"
local_skill.mkdir(parents=True)
(local_skill / "SKILL.md").write_text(manifest, encoding="utf-8")
PY

start_scanner() {
  local name=$1
  shift
  docker run -d --name "$name" -p 127.0.0.1::8000 "$@" "$IMAGE" >/dev/null
  CONTAINERS+=("$name")
  SCANNER_PORT="$(docker port "$name" 8000/tcp | awk -F: 'END {print $NF}')"
}

wait_for_health() {
  local url=$1
  python3 - "$url" <<'PY'
import json
import sys
import time
import urllib.error
import urllib.request

url = sys.argv[1]
last_error = None
for _ in range(120):
    try:
        with urllib.request.urlopen(url + "/health", timeout=2) as response:
            payload = json.load(response)
            if response.status == 200:
                break
    except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
        last_error = error
    time.sleep(1)
else:
    raise SystemExit(f"scanner did not become healthy: {last_error}")

if payload.get("version") != "2.1.0":
    raise SystemExit(f"expected scanner 2.1.0, got {payload!r}")
expected = {"static_analyzer", "bytecode_analyzer", "pipeline_analyzer"}
available = set(payload.get("analyzers_available", []))
if not expected.issubset(available):
    raise SystemExit(f"missing deterministic analyzers: {sorted(expected - available)}")
PY
}

post_zip() {
  local url=$1
  local archive=$2
  local output=$3
  python3 - "$url" "$archive" "$output" <<'PY'
import json
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

url, archive_path, output_path = sys.argv[1:]
boundary = "----skillhub-" + uuid.uuid4().hex
archive = Path(archive_path).read_bytes()
prefix = (
    f"--{boundary}\r\n"
    'Content-Disposition: form-data; name="policy"\r\n\r\n'
    "balanced\r\n"
    f"--{boundary}\r\n"
    f'Content-Disposition: form-data; name="file"; filename="{Path(archive_path).name}"\r\n'
    "Content-Type: application/zip\r\n\r\n"
).encode()
body = prefix + archive + f"\r\n--{boundary}--\r\n".encode()
request = urllib.request.Request(
    url + "/scan-upload",
    data=body,
    headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    method="POST",
)
try:
    with urllib.request.urlopen(request, timeout=900) as response:
        raw = response.read()
        status = response.status
except urllib.error.HTTPError as error:
    raw = error.read()
    status = error.code
Path(output_path).write_bytes(raw)
if status != 200:
    raise SystemExit(f"scan upload returned HTTP {status}: {raw[:1000]!r}")
try:
    payload = json.loads(raw)
except json.JSONDecodeError as error:
    raise SystemExit(f"scan upload did not return JSON: {error}") from error
required = {"scan_id", "skill_name", "findings", "scan_metadata"}
if not required.issubset(payload) or not isinstance(payload["findings"], list):
    raise SystemExit(f"invalid scan response contract: {payload!r}")
PY
}

assert_hardcoded_secret() {
  local response=$1
  python3 - "$response" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
if not any(finding.get("category") == "hardcoded_secrets" for finding in payload["findings"]):
    raise SystemExit("expected a hardcoded_secrets finding")
PY
}

MAIN_CONTAINER="skillhub-scanner-contract-main-$$"
start_scanner "$MAIN_CONTAINER"
MAIN_PORT="$SCANNER_PORT"
MAIN_URL="http://127.0.0.1:$MAIN_PORT"
wait_for_health "$MAIN_URL"

docker exec -i "$MAIN_CONTAINER" python - <<'PY'
from skill_scanner.core.analyzers.llm_analyzer import LLMProvider
from skillhub_scanner_app import _redact_supported_tokens

canaries = {
    "aws": "AKIA1234567890ABCDEF",
    "github": "github_pat_abcdefghijklmnopqrstuvwxyz",
    "jwt": "eyJabcde.abcdefgh.ijklmnop",
    "labeled": "custom-secret-1234567890",
    "private_key": "-----BEGIN PRIVATE KEY-----\nabc123\n-----END PRIVATE KEY-----",
}
findings = {
    "aws": canaries["aws"],
    "github": canaries["github"],
    "authorization": f"Bearer {canaries['jwt']}",
    "labeled": f"api_key={canaries['labeled']}",
    "private_key": canaries["private_key"],
}
redacted = str(_redact_supported_tokens(findings))
for label, canary in canaries.items():
    if canary in redacted:
        raise SystemExit(f"{label} canary was not redacted")

safe_values = ["line one\n\tline two", "x" * 5000]
if _redact_supported_tokens(safe_values) != safe_values:
    raise SystemExit("redaction changed safe multiline or long finding text")
mixed = "before\napi_key=custom-secret-1234567890\tafter"
if _redact_supported_tokens(mixed) != "before\napi_key=<redacted>\tafter":
    raise SystemExit("redaction changed non-secret characters around a credential")
if not LLMProvider.is_valid_provider("azure-openai") or LLMProvider.is_valid_provider("azure"):
    raise SystemExit("unexpected Scanner 2.1 Azure provider contract")
PY

docker exec "$MAIN_CONTAINER" python -c \
  'from pathlib import Path; Path("/tmp/skillhub-scanner-runtime/stale-after-timeout").write_text("stale")'
docker restart "$MAIN_CONTAINER" >/dev/null
MAIN_PORT="$(docker port "$MAIN_CONTAINER" 8000/tcp | awk -F: 'END {print $NF}')"
MAIN_URL="http://127.0.0.1:$MAIN_PORT"
wait_for_health "$MAIN_URL"
docker exec "$MAIN_CONTAINER" python -c \
  'from pathlib import Path; assert not Path("/tmp/skillhub-scanner-runtime/stale-after-timeout").exists()'

post_zip "$MAIN_URL" "$TMP_DIR/safe.zip" "$TMP_DIR/safe.json"
post_zip "$MAIN_URL" "$TMP_DIR/javascript-secret.zip" "$TMP_DIR/javascript-secret.json"
post_zip "$MAIN_URL" "$TMP_DIR/dotenv-secret.zip" "$TMP_DIR/dotenv-secret.json"
post_zip "$MAIN_URL" "$TMP_DIR/large-51mib.zip" "$TMP_DIR/large-51mib.json"

assert_hardcoded_secret "$TMP_DIR/javascript-secret.json"
assert_hardcoded_secret "$TMP_DIR/dotenv-secret.json"

python3 - "$TMP_DIR/safe.json" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
cel = payload.get("scan_metadata", {}).get("cel", {})
if cel.get("runtime") != "cel-go":
    raise SystemExit(f"expected CEL runtime cel-go, got {cel!r}")
if not cel.get("runtime_version"):
    raise SystemExit(f"expected a non-empty CEL runtime version, got {cel!r}")
if cel.get("fallbacks") != 0 or cel.get("errors") != []:
    raise SystemExit(f"unexpected CEL fallback/error telemetry: {cel!r}")
PY

docker logs "$MAIN_CONTAINER" >"$TMP_DIR/main-container.log" 2>&1
python3 - "$TMP_DIR/canaries.json" "$TMP_DIR" <<'PY'
import json
import sys
from pathlib import Path

canaries = json.load(open(sys.argv[1], encoding="utf-8"))
root = Path(sys.argv[2])
for path in [*root.glob("*.json"), root / "main-container.log"]:
    if path.name == "canaries.json":
        continue
    content = path.read_text(encoding="utf-8", errors="replace")
    for label, canary in canaries.items():
        if canary in content:
            def find_canary(value, location="$"):
                if isinstance(value, dict):
                    for key, child in value.items():
                        found = find_canary(child, f"{location}.{key}")
                        if found:
                            return found
                elif isinstance(value, list):
                    for index, child in enumerate(value):
                        found = find_canary(child, f"{location}[{index}]")
                        if found:
                            return found
                elif isinstance(value, str) and canary in value:
                    return location
                return None

            location = None
            if path.suffix == ".json":
                location = find_canary(json.loads(content))
            raise SystemExit(
                f"full {label} canary leaked through {path.name}"
                + (f" at {location}" if location else "")
            )
PY

LOCAL_CONTAINER="skillhub-scanner-contract-local-$$"
start_scanner "$LOCAL_CONTAINER" \
  -e SKILL_SCANNER_ALLOWED_ROOTS=/tmp/skillhub-scans \
  -v "$TMP_DIR/local-scan:/tmp/skillhub-scans:ro"
LOCAL_PORT="$SCANNER_PORT"
LOCAL_URL="http://127.0.0.1:$LOCAL_PORT"
wait_for_health "$LOCAL_URL"

python3 - "$LOCAL_URL" <<'PY'
import json
import sys
import urllib.error
import urllib.request

url = sys.argv[1]


def scan(path):
    request = urllib.request.Request(
        url + "/scan",
        data=json.dumps({"skill_directory": path, "policy": "balanced"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=900) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


inside_status, inside = scan("/tmp/skillhub-scans/contract-skill")
if inside_status != 200 or not isinstance(inside, dict):
    raise SystemExit(f"allowed local scan failed: HTTP {inside_status}: {inside!r}")
outside_status, outside = scan("/etc")
if outside_status not in (403, 404) or not isinstance(outside, dict):
    raise SystemExit(f"outside-root scan should be denied, got HTTP {outside_status}: {outside!r}")
PY

echo "scanner-2-1-contract-test passed"
