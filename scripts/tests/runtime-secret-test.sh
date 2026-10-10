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
grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- down --version sha-test --home $home_generated --public-url http://localhost" "$stdout_generated" \
  || fail "GitHub runtime should print a reproducible down command"
grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- up --version sha-test --home $home_generated --public-url http://localhost" "$stdout_generated" \
  || fail "GitHub runtime should print a reproducible up command"
grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- ps --version sha-test --home $home_generated --public-url http://localhost" "$stdout_generated" \
  || fail "GitHub runtime should print a reproducible ps command"
grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- logs --version sha-test --home $home_generated --public-url http://localhost" "$stdout_generated" \
  || fail "GitHub runtime should print a reproducible logs command"
grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- pull --version sha-test --home $home_generated --public-url http://localhost" "$stdout_generated" \
  || fail "GitHub runtime should print a reproducible pull command"

home_aliyun="$tmp/aliyun"
stdout_aliyun="$tmp/aliyun.out"
mkdir -p "$home_aliyun"
run_runtime "$home_aliyun" "$bin_dir" "$stdout_aliyun" --aliyun

grep -Fq "curl -fsSL file://$REPO_ROOT/runtime.sh | sh -s -- down --aliyun --version sha-test --home $home_aliyun --public-url http://localhost" "$stdout_aliyun" \
  || fail "Aliyun runtime should preserve the root runtime.sh URL and --aliyun source mode"
if grep -Fq "file://$REPO_ROOT/scripts/runtime.sh" "$stdout_aliyun"; then
  fail "Aliyun runtime must not print the GitHub scripts/runtime.sh path"
fi

home_ref="$tmp/ref"
stdout_ref="$tmp/ref.out"
mkdir -p "$home_ref"
run_runtime "$home_ref" "$bin_dir" "$stdout_ref" --ref 36de54157bff59c18c5eff255d2c158df45a3e2a --no-scanner

grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- down --ref 36de54157bff59c18c5eff255d2c158df45a3e2a --version sha-test --home $home_ref --public-url http://localhost --no-scanner" "$stdout_ref" \
  || fail "GitHub runtime should preserve pinned --ref and --no-scanner in down command"
grep -Fq "curl -fsSL file://$REPO_ROOT/scripts/runtime.sh | sh -s -- up --ref 36de54157bff59c18c5eff255d2c158df45a3e2a --version sha-test --home $home_ref --public-url http://localhost --no-scanner" "$stdout_ref" \
  || fail "GitHub runtime should preserve pinned --ref and --no-scanner in up command"

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

echo "runtime-secret-test passed"
