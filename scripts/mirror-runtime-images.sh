#!/usr/bin/env bash

set -euo pipefail

# The publishing workflow installs the checksum-pinned regctl used below.
# Python 3 is used only to select descriptors from the public source metadata.
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

  if regctl manifest head "${target_prefix}/${target}" >/dev/null 2>&1; then
    echo "Skipping existing image ${target_prefix}/${target}"
    continue
  fi

  echo "Mirroring ${source} -> ${target_prefix}/${target}"
  manifest="$(regctl manifest get "${source}" --format raw-body)"
  # Copy immutable runnable children, not the index's attestations: some mirror
  # registries reject their application/vnd.oci.empty.v1+json config media type.
  # Keep every image platform (including variants), rather than hardcoding amd64.
  sources="$(printf '%s' "$manifest" | python3 -c '
import json, sys
source = sys.argv[1]
index = json.load(sys.stdin)
media_type = index.get("mediaType")
print(media_type)
if "manifests" not in index:
    if index.get("artifactType") or media_type not in (
        "application/vnd.oci.image.manifest.v1+json",
        "application/vnd.docker.distribution.manifest.v2+json",
    ):
        sys.exit("Source is not a runnable image manifest")
    print(source)
else:
    if media_type not in ("application/vnd.oci.image.index.v1+json",
                          "application/vnd.docker.distribution.manifest.list.v2+json"):
        sys.exit("Source has an unsupported image index media type")
    refs = []
    for item in index["manifests"]:
        if (item.get("annotations", {}).get("vnd.docker.reference.type") == "attestation-manifest"
                or item.get("artifactType")):
            continue
        platform = item.get("platform", {})
        if any(platform.get(key) in (None, "", "unknown") for key in ("os", "architecture")):
            sys.exit("Source contains an unclassified descriptor; refusing to drop a platform")
        digest = item.get("digest")
        if not isinstance(digest, str) or ":" not in digest or any(c.isspace() for c in digest):
            sys.exit("Source contains an invalid image digest")
        refs.append(source.split("@", 1)[0] + "@" + digest)
    if not refs:
        sys.exit("Source contains no runnable image manifests")
    print("\n".join(refs))
' "$source")"
  mapfile -t source_refs <<<"$sources"
  source_media_type="${source_refs[0]}"
  source_refs=("${source_refs[@]:1}")
  # Buildx also discovers and copies referrers even with explicit child sources.
  # regctl can copy the original image blobs without adding those artifacts.
  if [[ ${#source_refs[@]} -eq 1 && "${source_refs[0]}" == "$source" ]]; then
    regctl image copy --referrers=false "$source" "${target_prefix}/${target}"
  else
    mirror_args=(index create "${target_prefix}/${target}" --media-type "$source_media_type" --referrers=false)
    for source_ref in "${source_refs[@]}"; do
      mirror_args+=(--ref "$source_ref")
    done
    regctl "${mirror_args[@]}"
  fi
done < "${IMAGES_FILE}"
