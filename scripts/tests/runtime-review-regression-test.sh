#!/usr/bin/env bash
# Fake-Docker regressions for independent review; no daemon or network required.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/skillhub-runtime-review.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
export ROOT DOCKER_LOG="$TMP/docker.log" FETCH_LOG="$TMP/fetch.log"
export REAL_CURL
REAL_CURL=$(command -v curl)
cat >"$TMP/bin/docker" <<'SH'
#!/bin/sh
printf '%s\n' "$*" >>"$DOCKER_LOG"
exit 0
SH
cat >"$TMP/bin/curl" <<'SH'
#!/bin/sh
printf '%s\n' "$2" >>"$FETCH_LOG"
case "$2" in
  https://raw.githubusercontent.com/iflytek/skillhub/*)
    path=${2#https://raw.githubusercontent.com/iflytek/skillhub/}
    # Record the real ref selection, but retrieve fixture files without a network.
    exec "$REAL_CURL" "$1" "file://$ROOT/${path#*/}" "$3" "$4"
    ;;
  *) exec "$REAL_CURL" "$@" ;;
esac
SH
chmod +x "$TMP/bin/docker" "$TMP/bin/curl"
export PATH="$TMP/bin:$PATH" SKILLHUB_RAW_BASE="file://$ROOT"
unset SKILLHUB_REF SKILLHUB_VERSION
run() { sh "$ROOT/scripts/runtime.sh" "$@" >"$TMP/output" 2>&1; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
fresh() {
  export SKILLHUB_HOME="$TMP/$1"
  mkdir -p "$SKILLHUB_HOME"
  : >"$DOCKER_LOG"
  : >"$FETCH_LOG"
}
assert_env() {
  grep -Fxq "$1=$2" "$SKILLHUB_HOME/.env.release" || fail "$1 expected $2"
}

b1() {
  unset SKILLHUB_RAW_BASE
  broken=false
  for ref in main x; do
    fresh "b1-$ref"
    # Existing analytics installations fetch this overlay even for ps/down/clean.
    touch "$SKILLHUB_HOME/compose.release.analytics.yml"
    if [ "$ref" = main ]; then unset SKILLHUB_REF; else export SKILLHUB_REF="$ref"; fi
    run ps --version v0.2.25
    printf 'B1 requested URLs (expected ref=%s):\n' "$ref"
    cat "$FETCH_LOG"
    if ! grep -Fxq "https://raw.githubusercontent.com/iflytek/skillhub/$ref/compose.release.analytics.yml" "$FETCH_LOG"; then
      printf 'FAIL: --version changed file ref from %s\n' "$ref"
      broken=true
    fi
  done
  # An env ref equal to the image tag must still be carried into lifecycle commands.
  run up --version x --no-scanner
  if ! grep -q 'sh -s -- ps --ref x --version x' "$TMP/output"; then
    printf 'FAIL: lifecycle omitted environment ref equal to version\n'
    broken=true
  fi
  [ "$broken" = false ] || fail 'B1 ref/lifecycle regression'
  printf 'PASS B1: analytics ps keeps main/env ref; lifecycle preserves --ref x with --version x\n'
}

b2() {
  export SKILLHUB_RAW_BASE="file://$ROOT"
  unset SKILLHUB_REF
  broken=false
  for receiver in private collector public; do
    fresh "b2-$receiver"
    run up --no-scanner
    case "$receiver" in
      private) run business-logs-enable --logs-network synthetic-review-private --logs-endpoint http://existing:4318/v1/logs ;;
      collector)
        if run business-logs-up; then fail 'unconfigured downstream accepted'; fi
        printf 'SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_ENDPOINT=https://sink.example.com\n' >>"$SKILLHUB_HOME/business-logs/.env"
        run business-logs-up
        ;;
      public) run business-logs-enable --logs-endpoint https://receiver.example.com/v1/logs ;;
    esac
    run business-logs-disable
    assert_env SKILLHUB_BUSINESS_LOGS_ENABLED false
    : >"$DOCKER_LOG"
    run business-logs-enable
    assert_env SKILLHUB_BUSINESS_LOGS_ENABLED true
    if [ "$receiver" = public ]; then
      assert_env SKILLHUB_BUSINESS_LOGS_NETWORK_ENABLED false
      ! grep -q 'compose.release.business-logs.yml\|^network ' "$DOCKER_LOG" || fail 'public HTTPS receiver requires private network'
      assert_env SKILLHUB_BUSINESS_LOGS_ENDPOINT https://receiver.example.com/v1/logs
    else
      grep 'compose .*up -d --no-deps --force-recreate server' "$DOCKER_LOG"
      if ! grep -Fxq 'SKILLHUB_BUSINESS_LOGS_NETWORK_ENABLED=true' "$SKILLHUB_HOME/.env.release"; then
        printf 'FAIL: B2 %s re-enable lost saved network attachment\n' "$receiver"
        broken=true
        continue
      fi
      grep -q 'compose.release.business-logs.yml up -d --no-deps --force-recreate server' "$DOCKER_LOG" || fail "$receiver re-enable lost network overlay"
      if [ "$receiver" = private ]; then
        assert_env SKILLHUB_BUSINESS_LOGS_NETWORK synthetic-review-private
        assert_env SKILLHUB_BUSINESS_LOGS_ENDPOINT http://existing:4318/v1/logs
      else
        assert_env SKILLHUB_BUSINESS_LOGS_NETWORK skillhub-business-logs
        assert_env SKILLHUB_BUSINESS_LOGS_ENDPOINT http://business-logs-collector:4318/v1/logs
      fi
    fi
    printf 'PASS B2 %s: enable -> disable -> enable restores saved state\n' "$receiver"
  done
  [ "$broken" = false ] || fail 'B2 private/Collector network regression'
}

n1() {
  export SKILLHUB_RAW_BASE="file://$ROOT"
  unset SKILLHUB_REF
  fresh n1
  if run business-logs-up --logs-network synthetic-review-seeded; then fail 'unconfigured downstream accepted'; fi
  grep '^SKILLHUB_BUSINESS_LOGS_NETWORK=' "$SKILLHUB_HOME/business-logs/.env"
  grep -Fxq 'SKILLHUB_BUSINESS_LOGS_NETWORK=synthetic-review-seeded' "$SKILLHUB_HOME/business-logs/.env" || fail 'first-time --logs-network ignored'
  printf 'SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_ENDPOINT=https://sink.example.com\n' >>"$SKILLHUB_HOME/business-logs/.env"
  before=$(shasum -a 256 "$SKILLHUB_HOME/business-logs/.env")
  run business-logs-up --logs-network synthetic-review-ignored
  [ "$(shasum -a 256 "$SKILLHUB_HOME/business-logs/.env")" = "$before" ] || fail 'existing Collector env rewritten'
  assert_env SKILLHUB_BUSINESS_LOGS_NETWORK synthetic-review-seeded
  grep -q 'network inspect synthetic-review-seeded' "$DOCKER_LOG" || fail 'seeded network not used'
  printf 'PASS N1: first creation seeds CLI network; existing env bytes and saved network retained\n'
}

n2() {
  export SKILLHUB_RAW_BASE="file://$ROOT"
  fresh n2
  run ps
  # Documentation contract only: Helm access must bypass ingress and preserve
  # the forwarded origin in the shared login + metrics commands.
  grep -Fq 'kubectl port-forward -n skillhub service/skillhub-server 18080:8080' "$ROOT/docs/business-event-logs.md" || fail 'Helm metrics Service port-forward missing'
  grep -Fq 'BASE=http://127.0.0.1:18080' "$ROOT/docs/business-event-logs.md" || fail 'Helm forwarded login/query origin missing'
  grep -Fq 'BASE=${BASE:-http://127.0.0.1:8080}' "$ROOT/docs/business-event-logs.md" || fail 'shared query overwrites Helm forwarded origin'
  grep -Fq '"$BASE/api/v1/auth/local/login"' "$ROOT/docs/business-event-logs.md" || fail 'login origin differs'
  grep -Fq '"$BASE/actuator/metrics/skillhub.business.logs.enqueued"' "$ROOT/docs/business-event-logs.md" || fail 'metrics origin differs'
  printf 'PASS N2: Helm metrics Service port-forward and same-origin admin query documented (contract, not live Kubernetes)\n'
}

case "${1:-all}" in
  b1|b2|n1|n2) "$1" ;;
  all) b1; b2; n1; n2 ;;
  *) fail 'expected b1, b2, n1, n2 or all' ;;
esac
