#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/home"
cat >"$TMP/bin/docker" <<'SH'
#!/bin/sh
printf '%s\n' "$*" >>"$DOCKER_LOG"
exit 0
SH
chmod +x "$TMP/bin/docker"
export DOCKER_LOG="$TMP/docker.log" SKILLHUB_HOME="$TMP/home" SKILLHUB_RAW_BASE="file://$ROOT" PATH="$TMP/bin:$PATH"
run() { sh "$ROOT/scripts/runtime.sh" "$@" >"$TMP/output"; }
fail() { echo "FAIL: $*" >&2; exit 1; }
run analytics-up
[ -f "$TMP/home/analytics/compose.analytics-umami.yml" ] || fail 'missing platform file'
[ "$(stat -f %Lp "$TMP/home/analytics/.env")" = 600 ] || fail 'secret permissions'
secret_before=$(shasum -a 256 "$TMP/home/analytics/.env" | cut -d' ' -f1)
run analytics-up
[ "$(shasum -a 256 "$TMP/home/analytics/.env" | cut -d' ' -f1)" = "$secret_before" ] || fail 'secrets overwritten'
if run analytics-enable --website-id not-a-uuid; then fail 'accepted invalid UUID'; fi
if run analytics-enable --website-id 4398de8e-40df-4483-9eea-ed213643525f --upstream 'https://evil.test/path'; then fail 'accepted path'; fi
run analytics-enable --website-id 4398de8e-40df-4483-9eea-ed213643525f
grep -q '^SKILLHUB_ANALYTICS_NETWORK_ENABLED=true$' "$TMP/home/.env.release" || fail 'network not persisted'
grep -q -- '-f .*compose.release.analytics.yml up -d --no-deps --force-recreate web' "$DOCKER_LOG" || fail 'overlay missing'
run up --no-scanner
grep -q -- '-f .*compose.release.analytics.yml up -d postgres' "$DOCKER_LOG" || fail 'up lost overlay'
run analytics-disable
grep -q '^SKILLHUB_ANALYTICS_NETWORK_ENABLED=false$' "$TMP/home/.env.release" || fail 'network not detached'
grep -q '^SKILLHUB_WEB_ANALYTICS_CONFIG=$' "$TMP/home/.env.release" || fail 'collection still enabled'
run analytics-enable --website-id 4398de8e-40df-4483-9eea-ed213643525f --upstream https://stats.example.com
grep -q '^SKILLHUB_ANALYTICS_NETWORK_ENABLED=false$' "$TMP/home/.env.release" || fail 'external platform joined local network'
run analytics-down
grep -q -- 'compose.analytics-umami.yml stop' "$DOCKER_LOG" || fail 'analytics down removed volumes'
! grep -q -- 'compose.analytics-umami.yml down' "$DOCKER_LOG" || fail 'analytics down removed project'
DOCKER_LOG="$TMP/docker.log" SKILLHUB_HOME="$TMP/home" SKILLHUB_RAW_BASE="file://$ROOT" PATH="$TMP/bin:$PATH" sh "$ROOT/scripts/runtime.sh" clean >"$TMP/output"
[ -f "$TMP/home/analytics/.env" ] || fail 'clean deleted independent analytics credentials'
[ ! -f "$TMP/home/.env.release" ] || fail 'clean left SkillHub credentials'
echo 'runtime-analytics-test passed'
