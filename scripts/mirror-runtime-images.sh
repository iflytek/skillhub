#!/usr/bin/env bash

set -euo pipefail

IMAGES_FILE="${1:-deploy/runtime-mirror-images.txt}"

: "${MIRROR_REGISTRY:?MIRROR_REGISTRY is required}"
: "${MIRROR_NAMESPACE:?MIRROR_NAMESPACE is required}"

target_prefix="${MIRROR_REGISTRY%/}/${MIRROR_NAMESPACE}"

while read -r source target; do
  if [[ -z "${source}" || "${source}" == \#* ]]; then
    continue
  fi

  if [[ -z "${target:-}" ]]; then
    echo "Missing target image mapping for ${source}" >&2
    exit 1
  fi

  if docker buildx imagetools inspect "${target_prefix}/${target}" >/dev/null 2>&1; then
    echo "Skipping existing image ${target_prefix}/${target}"
    continue
  fi

  echo "Mirroring ${source} -> ${target_prefix}/${target}"
  docker buildx imagetools create --tag "${target_prefix}/${target}" "${source}"
done < "${IMAGES_FILE}"
