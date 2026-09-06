#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILDER="$REPO_ROOT/scripts/build-builtin-skills.py"

tmp="$(mktemp -d)"
cleanup() {
  rm -rf "$tmp"
}
trap cleanup EXIT

first="$tmp/first"
second="$tmp/second"

python3 "$BUILDER" --output "$first"
python3 "$BUILDER" --output "$second"

cmp "$first/artifacts.json" "$second/artifacts.json"

runtime_manifest="$REPO_ROOT/server/skillhub-app/src/main/resources/builtin-skills/manifest.json"
python3 - "$first/artifacts.json" "$runtime_manifest" <<'PY'
import json
import sys
from pathlib import Path
from urllib.parse import urlsplit

artifacts = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["artifacts"]
runtime_items = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))["skills"]
runtime_by_coordinate = {}
for item in runtime_items:
    coordinate = (item["slug"], item["version"])
    assert coordinate not in runtime_by_coordinate, coordinate
    runtime_by_coordinate[coordinate] = item

artifacts_by_coordinate = {
    (item["slug"], item["version"]): item for item in artifacts
}
legacy_coordinates = {("skillhub-hello", "1.0.0"), ("agentguard", "1.1")}
runtime_coordinates = set(runtime_by_coordinate)
assert legacy_coordinates <= runtime_coordinates
packaged_runtime_coordinates = runtime_coordinates - legacy_coordinates
assert packaged_runtime_coordinates <= set(artifacts_by_coordinate)

for coordinate in packaged_runtime_coordinates:
    artifact = artifacts_by_coordinate[coordinate]
    runtime_item = runtime_by_coordinate[coordinate]
    assert runtime_item["sha256"] == artifact["sha256"], coordinate
    parsed_url = urlsplit(runtime_item["url"])
    assert parsed_url.scheme == "https", coordinate
    assert parsed_url.hostname == "bjcdn.openstorage.cn", coordinate
    assert not parsed_url.query and not parsed_url.fragment, coordinate
    assert parsed_url.path.endswith(f'/{artifact["sha256"]}.zip'), coordinate
PY

python3 - "$first" <<'PY'
import json
import sys
import zipfile
from pathlib import Path

output = Path(sys.argv[1])
manifest = json.loads((output / "artifacts.json").read_text(encoding="utf-8"))
artifacts = manifest["artifacts"]
assert [item["slug"] for item in artifacts] == sorted(item["slug"] for item in artifacts)

for item in artifacts:
    archive_path = output / item["file"]
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        assert names == sorted(names), item["slug"]
        assert "SKILL.md" in names, item["slug"]
        assert "LICENSE.txt" in names, item["slug"]
        assert "NOTICE.md" in names, item["slug"]
        assert all(not name.startswith("/") and ".." not in Path(name).parts for name in names)
PY

while IFS= read -r filename; do
  cmp "$first/$filename" "$second/$filename"
done < <(python3 - "$first/artifacts.json" <<'PY'
import json
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
for artifact in data["artifacts"]:
    print(artifact["file"])
PY
)

mini_source="$tmp/mini-source"
mkdir -p "$mini_source"
cp -R "$REPO_ROOT/builtin-skills/skills/exam-ready" "$mini_source/exam-ready"
ln -s /etc/passwd "$mini_source/exam-ready/outside.txt"

mini_catalog="$tmp/mini-catalog.json"
printf '%s\n' \
  '{"schemaVersion":1,"skills":[{"slug":"exam-ready","version":"1.0.0","license":"MIT","upstream":{"repository":"https://github.com/github/awesome-copilot","commit":"be7a1cf734f427d50266335b461b86977299d953","path":"skills/exam-ready"}}]}' \
  >"$mini_catalog"
mini_evals="$tmp/mini-evals.json"
printf '%s\n' \
  '{"schemaVersion":1,"cases":[{"slug":"exam-ready","prompt":"test","acceptance":["safe"],"forbidden":["unsafe"]}]}' \
  >"$mini_evals"

if python3 "$BUILDER" \
  --source-root "$mini_source" \
  --catalog "$mini_catalog" \
  --evals "$mini_evals" \
  --output "$tmp/invalid-output" >"$tmp/invalid.stdout" 2>"$tmp/invalid.stderr"; then
  echo "FAIL: expected symlink validation to fail" >&2
  exit 1
fi
grep -q "symbolic links are not allowed" "$tmp/invalid.stderr"

echo "build-builtin-skills-test passed"
