#!/bin/sh
# Run from the skill directory. Python 3 provides JSON-safe filename encoding.
set -eu
exec python3 - "$@" <<'PY'
import json
import pathlib
import sys

if len(sys.argv) != 2:
    print("usage: text-stats.sh <input-file>", file=sys.stderr)
    sys.exit(2)

try:
    content = pathlib.Path(sys.argv[1]).read_bytes()
    report = {
        "input": sys.argv[1],
        "lines": content.count(b"\n"),
        "words": len(content.split()),
        "characters": len(content),
    }
    encoded = json.dumps(report, ensure_ascii=True, separators=(",", ":"))
    pathlib.Path("artifacts").mkdir(exist_ok=True)
    pathlib.Path("artifacts/report.json").write_text(encoded + "\n", encoding="utf-8")
except OSError as error:
    print(str(error), file=sys.stderr)
    sys.exit(2)
print(encoded)
PY
