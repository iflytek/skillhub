#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/home"
cat >"$TMP/bin/docker" <<'SH'
#!/bin/sh
printf '%s\n' "$*" >>"$DOCKER_LOG"
case "$*" in
  'network inspect '*) [ -f "$NETWORK_MARKER" ] ;;
  'network create '*) touch "$NETWORK_MARKER" ;;
  *' cp business-logs-collector:'*)
    for last do :; done
    printf '%s\n' '{"resourceLogs":[{"scopeLogs":[{"logRecords":[{"attributes":[{"key":"skillhub.event.name","value":{"stringValue":"skill.download.prepared"}}]}]}]}]}' >"$last"
    ;;
esac
SH
chmod +x "$TMP/bin/docker"
export DOCKER_LOG="$TMP/docker.log" NETWORK_MARKER="$TMP/network" SKILLHUB_HOME="$TMP/home" SKILLHUB_RAW_BASE="file://$ROOT" PATH="$TMP/bin:$PATH"
run() { sh "$ROOT/scripts/runtime.sh" "$@" >"$TMP/output" 2>&1; }
fail() { echo "FAIL: $*" >&2; exit 1; }
mode() { if stat -c %a "$1" >/dev/null 2>&1; then stat -c %a "$1"; else stat -f %Lp "$1"; fi; }
run up --no-scanner
! grep -q 'compose.business-logs\|network create' "$DOCKER_LOG" || fail 'default started collection'
grep -q '^SKILLHUB_BUSINESS_LOGS_ENABLED=false$' "$TMP/home/.env.release" || fail 'not default off'
if run business-logs-enable --logs-endpoint 'https://fake:secret@receiver.invalid/v1/logs'; then fail 'credential URL accepted'; fi
! grep -q 'fake:secret' "$TMP/output" || fail 'URL leaked'
if run business-logs-enable --logs-endpoint 'http://receiver\invalid/v1/logs'; then fail 'backslash URL accepted'; fi
run business-logs-enable --logs-endpoint https://receiver.example.com/v1/logs
grep -q '^SKILLHUB_BUSINESS_LOGS_ENABLED=true$' "$TMP/home/.env.release" || fail 'existing receiver not enabled'
grep -q '^SKILLHUB_BUSINESS_LOGS_NETWORK_ENABLED=false$' "$TMP/home/.env.release" || fail 'public receiver joined network'
run business-logs-ps
grep -q 'Business Logs enabled: true' "$TMP/output" || fail 'existing receiver status missing'
grep -q 'managed externally' "$TMP/output" || fail 'existing receiver status requires Collector'
run business-logs-enable --logs-network synthetic-business-logs --logs-endpoint http://existing:4318/v1/logs
run up --no-scanner
grep -q 'compose.release.business-logs.yml up -d postgres' "$DOCKER_LOG" || fail 'plain up lost network overlay'
[ "$(grep -c '^network create ' "$DOCKER_LOG")" = 1 ] || fail 'network not idempotent'
if run business-logs-up; then fail 'missing downstream accepted'; fi
[ "$(mode "$TMP/home/business-logs/.env")" = 600 ] || fail 'Collector env permissions'
cat >>"$TMP/home/business-logs/.env" <<'ENV'
SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_ENDPOINT=https://sink.example.com
SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_HEADERS='{"Authorization":"Bearer SYNTHETIC-NOT-A-SECRET"}'
ENV
before=$(shasum -a 256 "$TMP/home/business-logs/.env")
run business-logs-up
run business-logs-up
[ "$(shasum -a 256 "$TMP/home/business-logs/.env")" = "$before" ] || fail 'Collector secrets overwritten'
! grep -q SYNTHETIC-NOT-A-SECRET "$TMP/output" "$DOCKER_LOG" || fail 'header leaked'
grep -q 'compose.business-logs-collector.yml up -d --force-recreate' "$DOCKER_LOG" || fail 'Collector update not applied'
grep -q '^SKILLHUB_BUSINESS_LOGS_ENDPOINT=http://business-logs-collector:4318/v1/logs$' "$TMP/home/.env.release" || fail 'wrong internal endpoint'
run business-logs-down
run business-logs-ps
run business-logs-up
grep -q 'compose.business-logs-collector.yml stop' "$DOCKER_LOG" || fail 'stop missing'
! grep -q 'compose.business-logs-collector.yml down' "$DOCKER_LOG" || fail 'stop deletes project'
run business-logs-disable
grep -q '^SKILLHUB_BUSINESS_LOGS_ENABLED=false$' "$TMP/home/.env.release" || fail 'disable failed'
run clean
[ -f "$TMP/home/.env.release" ] && [ -f "$TMP/home/business-logs/.env" ] || fail 'clean removed operator secrets'
run business-logs-up --aliyun
[ -f "$TMP/home/business-logs/business-logs-collector.yaml" ] || fail 'flat OSS retrieval failed'

export BUSINESS_LOGS_DEMO_STATE_DIR="$TMP/state" SKILLHUB_BUSINESS_LOGS_DEMO_PORT=14318
sh "$ROOT/scripts/business-logs-demo.sh" >"$TMP/demo-output"
sh "$ROOT/scripts/business-logs-demo.sh" up >"$TMP/demo-output"
result=$(sh "$ROOT/scripts/business-logs-demo.sh" run-host sh -c 'printf "%s %s" "$SKILLHUB_BUSINESS_LOGS_ENABLED" "$SKILLHUB_BUSINESS_LOGS_ENDPOINT"')
[ "$result" = 'true http://127.0.0.1:14318/v1/logs' ] || fail 'host receive address'
export BUSINESS_LOGS_BACKEND=container BUSINESS_LOGS_BACKEND_PROJECT=synthetic-backend BUSINESS_LOGS_BACKEND_ENV_FILE="$TMP/home/.env.release"
make -C "$ROOT" business-logs-demo-up business-logs-demo-status business-logs-demo-records business-logs-demo-verify business-logs-demo-down >"$TMP/demo-output"
grep -q 'synthetic-backend .*compose.release.business-logs.yml up -d --no-deps --force-recreate server' "$DOCKER_LOG" || fail 'container network overlay missing'
[ ! -f "$TMP/state/host-port" ] || fail 'host state not disabled'
echo 'runtime-business-logs-test passed (fake-Docker lifecycle and demo contracts)'
