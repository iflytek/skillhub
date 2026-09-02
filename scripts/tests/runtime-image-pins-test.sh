#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MINIO_RELEASE="RELEASE.2025-09-07T16-13-09Z"
MINIO_DIGEST="sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"
MINIO_IMAGE="minio/minio:${MINIO_RELEASE}@${MINIO_DIGEST}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq "image: \${MINIO_IMAGE:-${MINIO_IMAGE}}" "$REPO_ROOT/docker-compose.yml" \
  || fail "docker-compose.yml must default to the pinned MinIO release and digest"

grep -Fxq "${MINIO_IMAGE} minio:${MINIO_RELEASE}" "$REPO_ROOT/deploy/runtime-mirror-images.txt" \
  || fail "runtime mirror mapping must use the pinned MinIO source and release target"

grep -Fq "image: docker.io/${MINIO_IMAGE}" "$REPO_ROOT/charts/skillhub/tests/install-upgrade-smoke.sh" \
  || fail "Helm S3 smoke must use the same pinned MinIO release and digest"

if grep -Fq 'minio/minio:latest' \
  "$REPO_ROOT/docker-compose.yml" \
  "$REPO_ROOT/deploy/runtime-mirror-images.txt" \
  "$REPO_ROOT/charts/skillhub/tests/install-upgrade-smoke.sh"; then
  fail "validated MinIO runtime surfaces must not use the floating latest tag"
fi

echo "PASS: MinIO runtime images are pinned to ${MINIO_RELEASE}@${MINIO_DIGEST}"
