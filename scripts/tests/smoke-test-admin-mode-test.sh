#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SMOKE_SCRIPT="$REPO_ROOT/scripts/smoke-test.sh"

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

mkdir -p "$TMP_DIR/bin"
cat >"$TMP_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

url=""
method="GET"
data=""
cookie_in=""
cookie_out=""
write_code=false
output_file=""
while (($#)); do
  case "$1" in
    -X)
      method="$2"
      shift 2
      ;;
    -d)
      data="$2"
      shift 2
      ;;
    -b)
      cookie_in="$2"
      shift 2
      ;;
    -c)
      cookie_out="$2"
      shift 2
      ;;
    -w)
      write_code=true
      shift 2
      ;;
    -o)
      output_file="$2"
      shift 2
      ;;
    -H|--max-time|--retry|--retry-delay)
      shift 2
      ;;
    -s|-sS)
      shift
      ;;
    http://*|https://*)
      url="$1"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

if [[ -n "$cookie_out" ]]; then
  printf '%s\n' "localhost FALSE / FALSE 0 XSRF-TOKEN csrf-token" >>"$cookie_out"
fi

printf '%s\n' "$method $url $data" >>"${SMOKE_CURL_LOG:?SMOKE_CURL_LOG is required}"

status=200
case "$url" in
  */actuator/health) status=200 ;;
  */actuator/prometheus) status=401 ;;
  */api/v1/namespaces)
    if [[ -n "$cookie_in" && -f "$cookie_in.session" ]]; then status=200; else status=401; fi
    ;;
  */api/v1/auth/me)
    if [[ -n "$cookie_in" && -f "$cookie_in.session" && ! -f "$cookie_in.logged-out" ]]; then status=200; else status=401; fi
    ;;
  */api/v1/auth/local/register)
    [[ -n "$cookie_out" ]] && touch "$cookie_out.session"
    status=200
    ;;
  */api/v1/auth/local/change-password) status=200 ;;
  */api/v1/auth/logout)
    [[ -n "$cookie_out" ]] && touch "$cookie_out.logged-out"
    status=200
    ;;
  */api/v1/auth/local/login)
    if [[ "$data" == *'"username":"current-admin"'* && "$data" == *'"password":"current-secret"'* ]]; then
      [[ -n "$cookie_out" ]] && touch "$cookie_out.session"
      status=200
    else
      status=401
    fi
    ;;
  */api/v1/admin/labels|*/api/v1/labels|*/api/v1/admin/labels/*)
    status=200
    ;;
esac

if [[ "$output_file" != "/dev/null" && -n "$output_file" ]]; then
  printf '{}\n' >"$output_file"
fi
if [[ "$write_code" == true ]]; then
  printf '%s' "$status"
fi
EOF
chmod +x "$TMP_DIR/bin/curl"

run_smoke() {
  local name="$1"
  shift
  local log="$TMP_DIR/$name.curl.log"
  local out="$TMP_DIR/$name.out"
  local status=0
  env PATH="$TMP_DIR/bin:$PATH" SMOKE_CURL_LOG="$log" "$@" "$SMOKE_SCRIPT" http://skillhub.test >"$out" 2>&1 || status=$?
  printf '%s\n' "$status"
}

status="$(run_smoke skip-admin env)"
[[ "$status" == "0" ]] || fail "default smoke without admin credentials should pass"
grep -Fq "SKIP: Admin label management" "$TMP_DIR/skip-admin.out" \
  || fail "default smoke should skip admin section without explicit credentials"
if grep -Fq "/api/v1/auth/local/login" "$TMP_DIR/skip-admin.curl.log"; then
  fail "default smoke must not attempt admin login without explicit credentials"
fi

status="$(run_smoke missing-admin env SMOKE_ADMIN_CHECKS=true)"
[[ "$status" != "0" ]] || fail "SMOKE_ADMIN_CHECKS=true without credentials should fail"
grep -Fq "SMOKE_ADMIN_CHECKS=true requires SMOKE_ADMIN_USERNAME and SMOKE_ADMIN_PASSWORD" "$TMP_DIR/missing-admin.out" \
  || fail "missing admin credentials should produce an actionable error"
if grep -Fq "/api/v1/auth/local/login" "$TMP_DIR/missing-admin.curl.log"; then
  fail "missing explicit admin credentials must stop before admin login"
fi

status="$(run_smoke explicit-admin env SMOKE_ADMIN_USERNAME=current-admin SMOKE_ADMIN_PASSWORD=current-secret BOOTSTRAP_ADMIN_PASSWORD=wrong-bootstrap)"
[[ "$status" == "0" ]] || fail "explicit admin credentials should run full smoke successfully"
grep -Fq '"username":"current-admin","password":"current-secret"' "$TMP_DIR/explicit-admin.curl.log" \
  || fail "admin login should use SMOKE_ADMIN credentials"
if grep -Fq 'wrong-bootstrap' "$TMP_DIR/explicit-admin.curl.log"; then
  fail "admin login must not use BOOTSTRAP_ADMIN_PASSWORD"
fi
if grep -Fq 'ChangeMe!2026' "$TMP_DIR/explicit-admin.curl.log"; then
  fail "admin login must not fall back to the bootstrap default password"
fi

echo "smoke-test-admin-mode-test passed"
