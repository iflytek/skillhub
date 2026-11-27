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

# The anonymous Agent bootstrap route and the installable helper Skill share
# one reviewed instruction body. The web copy exists only because its Docker
# build context is intentionally limited to web/.
cmp \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" \
  "$REPO_ROOT/web/src/docs/skill.md.template"
test -f "$REPO_ROOT/builtin-skills/skills/skillhub-cli/references/cli-operations.md"
grep -F 'npm install --global @astron-team/skillhub' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" >/dev/null
grep -F 'version: 2.0.2' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" >/dev/null
grep -F 'separately confirms removal of that exact identified launcher' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" >/dev/null
grep -F 'Never unlink an executable directly' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" >/dev/null
grep -F 'do not run the global installation or update yet' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" >/dev/null
grep -F 'installed but not yet loaded' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" >/dev/null
grep -F 'skillhub sync pull --namespace team-a' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/references/cli-operations.md" >/dev/null
grep -F 'skillhub publish ./my-skill' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/references/cli-operations.md" >/dev/null
grep -F '`--dry-run` sends the package bytes to the selected registry' \
  "$REPO_ROOT/builtin-skills/skills/skillhub-cli/references/cli-operations.md" >/dev/null

# Keep the launcher takeover decision executable as a contract instead of only
# checking for isolated safety phrases. A connect request has three disjoint
# states; the non-first-party state must identify provenance and obtain a
# separate confirmation before the first permitted write.
python3 - "$REPO_ROOT/builtin-skills/skills/skillhub-cli/SKILL.md" <<'PY'
import sys
from pathlib import Path

guide = Path(sys.argv[1]).read_text(encoding="utf-8")
missing = guide.index("If the command is missing")
install = guide.index("npm install --global @astron-team/skillhub", missing)
existing = guide.index("If the command exists")
verified = guide.index("Treat an existing command as first-party only")
foreign = guide.index("If package metadata proves another owner or package")
confirm = guide.index("Only after the user separately confirms removal", foreign)

assert missing < install < existing < verified < foreign < confirm
inspection = guide[existing:verified]
for required in ("exact command selected by the shell", "follow symlinks", "owner", "package manager or package"):
    assert required in inspection, required
for forbidden in ("npm install --global", "supported uninstall command", "unlink an executable"):
    assert forbidden not in inspection, forbidden
assert "resolved package metadata proves" in guide[verified:foreign]
assert "installing package is `@astron-team/skillhub`" in guide[verified:foreign]
assert "even when it prints `SkillHub CLI <version>`" in guide[foreign:confirm]

authorization = guide[guide.index("An explicit request to connect SkillHub authorizes"):]
assert "does not authorize removing another `skillhub` launcher" in authorization
assert "Launcher removal requires the separate, exact confirmation" in authorization
assert "If the owner or package source cannot be proven, stop" in guide
PY

# The guide response stays constant-time: its request handler returns the
# startup-loaded template without network calls or directory traversal. The
# container entrypoint performs one local guide copy and no guide-time fetch.
python3 - \
  "$REPO_ROOT/web/vite.config.ts" \
  "$REPO_ROOT/web/docker-entrypoint.d/30-runtime-config.sh" <<'PY'
import sys
from pathlib import Path

vite = Path(sys.argv[1]).read_text(encoding="utf-8")
handler = vite[vite.index("configureServer(server)"):vite.index("export default defineConfig")]
assert "response.end(guideTemplate)" in handler
for forbidden in ("fetch(", "readFile", "readdir", "glob("):
    assert forbidden not in handler, forbidden

entrypoint = Path(sys.argv[2]).read_text(encoding="utf-8")
guide_setup = entrypoint[entrypoint.index("# The guide derives its registry"):]
assert guide_setup.count("\ncp ") == 1
for forbidden in ("curl ", "wget ", "find ", "envsubst"):
    assert forbidden not in guide_setup, forbidden
PY

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
    # Both hosts serve the organisation's immutable skill artifacts; keep the check narrow.
    assert parsed_url.hostname in {"bjcdn.openstorage.cn", "openres.xfyun.cn"}, coordinate
    assert not parsed_url.query and not parsed_url.fragment, coordinate
    assert parsed_url.path.lower().endswith('.zip'), coordinate
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

python3 "$REPO_ROOT/scripts/tests/test_zero_slop.py"

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

# An original package declares "upstream": null and carries an original-work notice instead of
# pinned upstream provenance. Both halves are required: the flag alone is not provenance.
original_source="$tmp/original-source"
mkdir -p "$original_source"
cp -R "$REPO_ROOT/builtin-skills/skills/exam-ready" "$original_source/exam-ready"
cat >"$original_source/exam-ready/NOTICE.md" <<'NOTICE'
# Original work notice

- Origin: this repository, `builtin-skills/skills/exam-ready`
- Author: the SkillHub authors
- License: MIT; see `LICENSE.txt`
NOTICE

original_catalog="$tmp/original-catalog.json"
printf '%s\n' \
  '{"schemaVersion":1,"skills":[{"slug":"exam-ready","version":"1.0.0","license":"MIT","upstream":null}]}' \
  >"$original_catalog"

python3 "$BUILDER" \
  --source-root "$original_source" \
  --catalog "$original_catalog" \
  --evals "$mini_evals" \
  --output "$tmp/original-output" >/dev/null
grep -q '"origin": "original"' "$tmp/original-output/artifacts.json"

# A placeholder commit is rejected: original packages must say so, not pin 40 zeros.
printf '%s\n' \
  '{"schemaVersion":1,"skills":[{"slug":"exam-ready","version":"1.0.0","license":"MIT","upstream":{"repository":"https://github.com/iflytek/skillhub","commit":"0000000000000000000000000000000000000000","path":"builtin-skills/skills/exam-ready"}}]}' \
  >"$tmp/zero-catalog.json"
if python3 "$BUILDER" \
  --source-root "$original_source" \
  --catalog "$tmp/zero-catalog.json" \
  --evals "$mini_evals" \
  --output "$tmp/zero-output" >"$tmp/zero.stdout" 2>"$tmp/zero.stderr"; then
  echo "FAIL: expected the placeholder upstream commit to be rejected" >&2
  exit 1
fi
grep -q 'must be a real revision' "$tmp/zero.stderr"

# The notice is what makes an original claim checkable; without it the build stops.
cp -R "$original_source/exam-ready" "$tmp/noticeless-source-exam-ready"
printf '# Upstream notice\n\n- License: MIT; see LICENSE.txt\n' \
  >"$tmp/noticeless-source-exam-ready/NOTICE.md"
mkdir -p "$tmp/noticeless-source"
mv "$tmp/noticeless-source-exam-ready" "$tmp/noticeless-source/exam-ready"
if python3 "$BUILDER" \
  --source-root "$tmp/noticeless-source" \
  --catalog "$original_catalog" \
  --evals "$mini_evals" \
  --output "$tmp/noticeless-output" >"$tmp/noticeless.stdout" 2>"$tmp/noticeless.stderr"; then
  echo "FAIL: expected a missing original-work notice to be rejected" >&2
  exit 1
fi
grep -q "Original work notice" "$tmp/noticeless.stderr"

echo "build-builtin-skills-test passed"
