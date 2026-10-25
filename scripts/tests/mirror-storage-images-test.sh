#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/bin"
cat >"$work/bin/regctl" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$MIRROR_COMMAND_LOG"
case "$*" in
  'manifest get '*)
    [[ "${INSPECT_EXIT:-0}" == 0 ]] || exit "$INSPECT_EXIT"
    case "${SOURCE_KIND:-index}" in
      index)
        printf '%s\n' '{"mediaType":"application/vnd.oci.image.index.v1+json","manifests":[
          {"digest":"sha256:aaa","platform":{"os":"linux","architecture":"amd64"}},
          {"digest":"sha256:bbb","platform":{"os":"linux","architecture":"arm64","variant":"v8"}},
          {"digest":"sha256:ccc","platform":{"os":"linux","architecture":"riscv64"}},
          {"digest":"sha256:attestation","platform":{"os":"unknown","architecture":"unknown"},"annotations":{"vnd.docker.reference.type":"attestation-manifest"}},
          {"digest":"sha256:artifact","artifactType":"application/vnd.example.signature"}]}' ;;
      single) printf '%s\n' '{"mediaType":"application/vnd.oci.image.manifest.v1+json","config":{}}' ;;
      empty) printf '%s\n' '{"mediaType":"application/vnd.oci.image.index.v1+json","manifests":[]}' ;;
      unknown) printf '%s\n' '{"mediaType":"application/vnd.oci.image.index.v1+json","manifests":[{"digest":"sha256:unknown","platform":{"os":"unknown","architecture":"unknown"}}]}' ;;
      malformed) printf '%s\n' 'not-json' ;;
    esac ;;
  'manifest head '*) [[ "${TARGET_EXISTS:-false}" == true ]] ;;
  'index create '*|'image copy '*)
    if [[ "$*" == *'@sha256:abc'* && "${SOURCE_KIND:-index}" == index ]]; then
      echo 'denied: unknown manifest class for application/vnd.oci.empty.v1+json' >&2
      exit 42
    fi
    exit "${CREATE_EXIT:-0}" ;;
  *) exit 2 ;;
esac
STUB
chmod +x "$work/bin/regctl"
export PATH="$work/bin:$PATH" MIRROR_COMMAND_LOG="$work/commands"
export MIRROR_REGISTRY=registry.example.test MIRROR_NAMESPACE=skillhub
printf '%s\n' 'docker.io/rustfs/rustfs@sha256:abc rustfs:immutable-test' >"$work/images"
TARGET_EXISTS=true bash "$REPO_ROOT/scripts/mirror-runtime-images.sh" "$work/images" >"$work/output"
if grep -Eq '^index create|^image copy' "$work/commands"; then echo 'FAIL: existing target was refreshed' >&2; exit 1; fi
TARGET_EXISTS=false bash "$REPO_ROOT/scripts/mirror-runtime-images.sh" "$work/images" >"$work/output"
grep -Fxq 'index create registry.example.test/skillhub/rustfs:immutable-test --media-type application/vnd.oci.image.index.v1+json --referrers=false --ref docker.io/rustfs/rustfs@sha256:aaa --ref docker.io/rustfs/rustfs@sha256:bbb --ref docker.io/rustfs/rustfs@sha256:ccc' "$work/commands"
if grep '^index create' "$work/commands" | grep -Eq 'attestation|artifact'; then
  echo 'FAIL: artifact descriptors were copied' >&2; exit 1
fi
SOURCE_KIND=single TARGET_EXISTS=false bash "$REPO_ROOT/scripts/mirror-runtime-images.sh" "$work/images" >"$work/output"
grep -Fxq 'image copy --referrers=false docker.io/rustfs/rustfs@sha256:abc registry.example.test/skillhub/rustfs:immutable-test' "$work/commands"
for kind in empty unknown malformed; do
  : >"$work/commands"
  if SOURCE_KIND="$kind" TARGET_EXISTS=false bash "$REPO_ROOT/scripts/mirror-runtime-images.sh" "$work/images" >"$work/output" 2>&1; then
    echo "FAIL: invalid source $kind was accepted" >&2; exit 1
  fi
  if grep -Eq '^index create|^image copy' "$work/commands"; then
    echo "FAIL: invalid source $kind reached the registry write" >&2; exit 1
  fi
done
for stage in inspect create; do
  expected=23
  if [[ "$stage" == inspect ]]; then
    if INSPECT_EXIT="$expected" TARGET_EXISTS=false bash "$REPO_ROOT/scripts/mirror-runtime-images.sh" "$work/images" >"$work/output" 2>&1; then
      echo 'FAIL: inspection failure was swallowed' >&2; exit 1
    else [[ $? == "$expected" ]]; fi
  else
    if CREATE_EXIT="$expected" TARGET_EXISTS=false bash "$REPO_ROOT/scripts/mirror-runtime-images.sh" "$work/images" >"$work/output" 2>&1; then
      echo 'FAIL: registry copy failure was swallowed' >&2; exit 1
    else [[ $? == "$expected" ]]; fi
  fi
done
grep -q '^docker.io/pgsty/silo:RELEASE.2026-09-16T00-00-00Z minio:latest$' "$REPO_ROOT/deploy/runtime-mirror-images.txt"
grep -Fxq 'docker.io/pgsty/silo:RELEASE.2026-10-18T00-00-00Z@sha256:2636fc4155d9a7e41ff58cdb65ddd13ba9304e67184e13c25bc5cb34016269d7 silo:20261018-2636fc4155d9' "$REPO_ROOT/deploy/runtime-mirror-images.txt"
grep -Fq 'https://github.com/regclient/regclient/releases/download/v0.11.7/regctl-linux-amd64' "$REPO_ROOT/.github/workflows/publish-images.yml"
grep -Fq '3737fbbdd791e04e7a831640b681f3397a376498e80e2ad9b1b2d27296f239bc' "$REPO_ROOT/.github/workflows/publish-images.yml"
grep -Fq "'.github/workflows/publish-images.yml'" "$REPO_ROOT/.github/workflows/pr-scripts.yml"
echo 'Storage mirror platform filtering, skip, failure propagation and legacy alias tests passed'
