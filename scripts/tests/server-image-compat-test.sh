#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKERFILE="$REPO_ROOT/server/Dockerfile"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'groupadd --system --gid 101 app' "$DOCKERFILE" \
  || fail 'server runtime group must retain the v0.2.17 gid 101 for storage-volume upgrades'
grep -Fq 'useradd --system --uid 100 --gid app --create-home app' "$DOCKERFILE" \
  || fail 'server runtime user must retain the v0.2.17 uid 100 for storage-volume upgrades'
grep -Eq '^USER app[[:space:]]*$' "$DOCKERFILE" \
  || fail 'server runtime must continue to run as the non-root app user'

echo 'server-image-compat-test passed'
