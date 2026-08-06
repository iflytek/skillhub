#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/runtime.sh"

TMP_DIRS=()
cleanup() {
  local d
  for d in "${TMP_DIRS[@]+"${TMP_DIRS[@]}"}"; do
    rm -rf "$d"
  done
}
trap cleanup EXIT

new_tmp() {
  local d
  d="$(mktemp -d)"
  TMP_DIRS+=("$d")
  echo "$d"
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

install_fake_tools() {
  local bin_dir="$1"
  mkdir -p "$bin_dir"
  cat >"$bin_dir/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${DOCKER_LOG:?DOCKER_LOG is required}"
if [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then
  exit 0
fi
exit 0
EOF
  chmod +x "$bin_dir/docker"

  cat >"$bin_dir/openssl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "rand" && "${2:-}" == "-hex" && "${3:-}" == "32" ]]; then
  printf '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n'
  exit 0
fi
echo "unsupported openssl args: $*" >&2
exit 1
EOF
  chmod +x "$bin_dir/openssl"
}

run_runtime() {
  local home="$1"
  local bin_dir="$2"
  local stdout="$3"
  shift 3
  DOCKER_LOG="$home/docker.log" \
    SKILLHUB_HOME="$home" \
    SKILLHUB_RAW_BASE="file://$REPO_ROOT" \
    PATH="$bin_dir:$PATH" \
    sh "$SCRIPT" up --version sha-test --public-url http://localhost "$@" >"$stdout"
}

file_mode() {
  local file="$1"
  if stat -c %a "$file" >/dev/null 2>&1; then
    stat -c %a "$file"
  else
    stat -f %Lp "$file"
  fi
}

tmp="$(new_tmp)"
bin_dir="$tmp/bin"
install_fake_tools "$bin_dir"

home_generated="$tmp/generated"
stdout_generated="$tmp/generated.out"
mkdir -p "$home_generated"
run_runtime "$home_generated" "$bin_dir" "$stdout_generated"

generated_secret="$(grep '^SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=' "$home_generated/.env.release" | cut -d= -f2-)"
[[ "$generated_secret" == "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" ]] \
  || fail "runtime should generate a persisted anonymous download secret"
[[ "$(file_mode "$home_generated/.env.release")" == "600" ]] \
  || fail "runtime env file must be readable only by the owner"
grep -Fq "Generated SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET" "$stdout_generated" \
  || fail "runtime should explain that it generated the secret"
if grep -Fq "$generated_secret" "$stdout_generated"; then
  fail "runtime must not print the generated secret value"
fi

home_preserved="$tmp/preserved"
stdout_preserved="$tmp/preserved.out"
mkdir -p "$home_preserved"
cat >"$home_preserved/.env.release" <<'EOF'
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=already-valid-runtime-secret-32-bytes
EOF
run_runtime "$home_preserved" "$bin_dir" "$stdout_preserved"

preserved_secret="$(grep '^SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=' "$home_preserved/.env.release" | cut -d= -f2-)"
[[ "$preserved_secret" == "already-valid-runtime-secret-32-bytes" ]] \
  || fail "runtime must preserve an existing valid anonymous download secret"
[[ "$(file_mode "$home_preserved/.env.release")" == "600" ]] \
  || fail "runtime env file must remain owner-readable only when an existing secret is preserved"
if grep -Fq "Generated SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET" "$stdout_preserved"; then
  fail "runtime must not regenerate an existing valid secret"
fi

home_no_scanner="$tmp/no-scanner"
stdout_no_scanner="$tmp/no-scanner.out"
mkdir -p "$home_no_scanner"
run_runtime "$home_no_scanner" "$bin_dir" "$stdout_no_scanner" --no-scanner

grep -Fq "SKILLHUB_SECURITY_SCANNER_ENABLED=false" "$home_no_scanner/.env.release" \
  || fail "runtime should persist scanner disabled state for --no-scanner"
grep -Fq -- "up -d --no-deps --scale skill-scanner=0 server web" "$home_no_scanner/docker.log" \
  || fail "runtime --no-scanner should start server/web without waiting on scanner dependencies"

home_sub_path="$tmp/sub-path"
stdout_sub_path="$tmp/sub-path.out"
mkdir -p "$home_sub_path"
run_runtime "$home_sub_path" "$bin_dir" "$stdout_sub_path" \
  --public-url http://localhost/skillhub \
  --base-path skillhub

grep -Fq "SKILLHUB_PUBLIC_BASE_URL=http://localhost/skillhub" "$home_sub_path/.env.release" \
  || fail "runtime should persist the public URL for sub-path deployments"
grep -Fq "SKILLHUB_WEB_BASE_PATH=/skillhub/" "$home_sub_path/.env.release" \
  || fail "runtime should normalize and persist SKILLHUB_WEB_BASE_PATH"
grep -Fq "SKILLHUB_WEB_API_BASE_URL=/skillhub" "$home_sub_path/.env.release" \
  || fail "runtime should align SKILLHUB_WEB_API_BASE_URL with the base path"

echo "runtime-secret-test passed"
