#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RUNTIME_SCRIPT=${RUNTIME_SCRIPT:-$ROOT/scripts/runtime.sh}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/home"
cat >"$TMP/bin/docker" <<'SH'
#!/bin/sh
printf '%s\n' "$*" >>"$DOCKER_LOG"
printf 'identity project=%s network=%s\n' "${SKILLHUB_ANALYTICS_PROJECT:-}" "${SKILLHUB_ANALYTICS_NETWORK:-}" >>"$DOCKER_LOG"
# Exercise network creation without contacting a daemon.
if [ "$1 $2" = "network inspect" ]; then exit 1; fi
exit 0
SH
chmod +x "$TMP/bin/docker"
export DOCKER_LOG="$TMP/docker.log" SKILLHUB_HOME="$TMP/home" SKILLHUB_RAW_BASE="file://$ROOT" PATH="$TMP/bin:$PATH"
run() { sh "$RUNTIME_SCRIPT" "$@" >"$TMP/output"; }
fail() { echo "FAIL: $*" >&2; exit 1; }
run analytics-up
[ -f "$TMP/home/analytics/compose.analytics-umami.yml" ] || fail 'missing platform file'
file_mode() {
  if stat -c %a "$1" >/dev/null 2>&1; then stat -c %a "$1"; else stat -f %Lp "$1"; fi
}
[ "$(file_mode "$TMP/home/analytics/.env")" = 600 ] || fail 'secret permissions'
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
DOCKER_LOG="$TMP/docker.log" SKILLHUB_HOME="$TMP/home" SKILLHUB_RAW_BASE="file://$ROOT" PATH="$TMP/bin:$PATH" sh "$RUNTIME_SCRIPT" clean >"$TMP/output"
[ -f "$TMP/home/analytics/.env" ] || fail 'clean deleted independent analytics credentials'
[ ! -f "$TMP/home/.env.release" ] || fail 'clean left SkillHub credentials'


# Each home is an independent instance, including custom project/network names.
# External environment must never override its saved independent identity.
for instance in default tenant-a tenant-b; do
  export SKILLHUB_HOME="$TMP/$instance"
  run ps
  project=skillhub-analytics
  network=skillhub-analytics
  if [ "$instance" != default ]; then
    project="$instance-analytics"
    network="$instance-network"
    printf 'SKILLHUB_ANALYTICS_PROJECT=%s\nSKILLHUB_ANALYTICS_NETWORK=%s\n' "$project" "$network" >>"$SKILLHUB_HOME/.env.release"
  fi
  run analytics-up
  config="$SKILLHUB_HOME/analytics/.env"
  grep -qx "SKILLHUB_ANALYTICS_PROJECT=$project" "$config" || fail "$instance project not saved"
  grep -qx "SKILLHUB_ANALYTICS_NETWORK=$network" "$config" || fail "$instance network not saved"
  config_hash=$(shasum -a 256 "$config" | cut -d' ' -f1)
  run clean
  [ ! -f "$SKILLHUB_HOME/.env.release" ] || fail "$instance clean retained main config"
  for command in analytics-ps analytics-logs analytics-pull analytics-down analytics-up analytics-up; do
    : >"$DOCKER_LOG"
    SKILLHUB_ANALYTICS_PROJECT=unrelated-project SKILLHUB_ANALYTICS_NETWORK=unrelated-network COMPOSE_PROJECT_NAME=unrelated-main run "$command"
    grep -q -- "-p $project --env-file $config" "$DOCKER_LOG" || fail "$instance $command changed project after clean"
    grep -qx "identity project=$project network=$network" "$DOCKER_LOG" || fail "$instance $command lost Compose identity"
    ! grep -q -- '-p unrelated-project' "$DOCKER_LOG" || fail 'inherited project override'
    [ "$(shasum -a 256 "$config" | cut -d' ' -f1)" = "$config_hash" ] || fail "$instance config or secrets changed"
  done
  # A newly generated release file has defaults; later unrelated edits are also
  # harmless. Local Web must attach to the saved analytics network in both cases.
  for outer in recreated conflicting; do
    if [ "$outer" = conflicting ]; then
      printf 'SKILLHUB_ANALYTICS_PROJECT=other-project\nSKILLHUB_ANALYTICS_NETWORK=other-network\n' >>"$SKILLHUB_HOME/.env.release"
    fi
    : >"$DOCKER_LOG"
    SKILLHUB_ANALYTICS_NETWORK=unrelated-network run analytics-enable --website-id 4398de8e-40df-4483-9eea-ed213643525f
    grep -q "network create $network" "$DOCKER_LOG" || fail "$instance Web created wrong network"
    grep -qx "identity project=$project network=$network" "$DOCKER_LOG" || fail "$instance Web interpolation wrong"
    grep -qx "SKILLHUB_ANALYTICS_NETWORK=$network" "$SKILLHUB_HOME/.env.release" || fail 'Web network not synchronized'
    : >"$DOCKER_LOG"
    SKILLHUB_ANALYTICS_NETWORK=unrelated-network run up --no-scanner
    grep -q "network create $network" "$DOCKER_LOG" || fail "$instance main up lost network"
    grep -q -- 'compose.release.analytics.yml up -d postgres' "$DOCKER_LOG" || fail "$instance main up lost overlay"
    run analytics-disable
  done
  chmod 644 "$config"
  run analytics-ps
  [ "$(file_mode "$config")" = 600 ] || fail 'existing analytics permissions not secured'
  [ "$(file_mode "$SKILLHUB_HOME/.env.release")" = 600 ] || fail 'main config permissions'
  [ "$(shasum -a 256 "$config" | cut -d' ' -f1)" = "$config_hash" ] || fail "$instance secret changed after enable/up/disable"
  ! grep -q -- 'compose.analytics-umami.yml down' "$DOCKER_LOG" || fail 'analytics volumes removed'
done

# Legacy files always contained the network, but not the project. Preserve the
# original custom project before clean deletes its only previous source.
export SKILLHUB_HOME="$TMP/legacy"
run ps
printf 'SKILLHUB_ANALYTICS_PROJECT=legacy-project\nSKILLHUB_ANALYTICS_NETWORK=legacy-network\n' >>"$SKILLHUB_HOME/.env.release"
mkdir -p "$SKILLHUB_HOME/analytics"
printf 'UMAMI_DB_PASSWORD=test-db-secret\nUMAMI_APP_SECRET=test-app-secret\nUMAMI_ADMIN_PORT=8300\nSKILLHUB_ANALYTICS_NETWORK=legacy-network\n' >"$SKILLHUB_HOME/analytics/.env"
legacy_secrets=$(sed -n '/^UMAMI_/p' "$SKILLHUB_HOME/analytics/.env" | shasum -a 256)
run clean
grep -qx 'SKILLHUB_ANALYTICS_PROJECT=legacy-project' "$SKILLHUB_HOME/analytics/.env" || fail 'legacy project not migrated before clean'
[ "$(sed -n '/^UMAMI_/p' "$SKILLHUB_HOME/analytics/.env" | shasum -a 256)" = "$legacy_secrets" ] || fail 'legacy secrets changed'
: >"$DOCKER_LOG"
run analytics-ps
grep -q -- '-p legacy-project ' "$DOCKER_LOG" || fail 'legacy project reset'

# If the original main config is already lost, do not trust newly generated
# defaults as migration evidence. Operators must restore a verified identifier.
for command in analytics-ps analytics-logs analytics-pull analytics-down analytics-up clean analytics-enable analytics-disable up down ps logs pull; do
  export SKILLHUB_HOME="$TMP/lost-$command"
  mkdir -p "$SKILLHUB_HOME/analytics"
  printf 'UMAMI_DB_PASSWORD=test-db-secret\nUMAMI_APP_SECRET=test-app-secret\nSKILLHUB_ANALYTICS_NETWORK=legacy-network\n' >"$SKILLHUB_HOME/analytics/.env"
  : >"$DOCKER_LOG"
  if run "$command" --website-id 4398de8e-40df-4483-9eea-ed213643525f 2>"$TMP/error"; then fail "$command guessed lost project"; fi
  grep -q 'Restore the original .env.release' "$TMP/error" || fail 'missing actionable legacy error'
  [ ! -s "$DOCKER_LOG" ] || fail "$command contacted Docker for unidentified deployment"
  ! grep -q 'test-.*-secret' "$TMP/error" || fail 'error disclosed secrets'
  ! grep -q '^SKILLHUB_ANALYTICS_PROJECT=' "$SKILLHUB_HOME/analytics/.env" || fail 'lost legacy project overwritten'
  [ ! -f "$SKILLHUB_HOME/.env.release" ] || fail 'failed migration created misleading defaults'
  if run "$command" --website-id 4398de8e-40df-4483-9eea-ed213643525f 2>"$TMP/error"; then fail "$command retry guessed lost project"; fi
done

# No configured local platform is needed to use an existing external service.
export SKILLHUB_HOME="$TMP/external-only"
run analytics-enable --website-id 4398de8e-40df-4483-9eea-ed213643525f --upstream https://stats.example.com
[ ! -e "$SKILLHUB_HOME/analytics/.env" ] || fail 'external enable created local platform'
grep -qx 'SKILLHUB_ANALYTICS_NETWORK_ENABLED=false' "$SKILLHUB_HOME/.env.release" || fail 'external enable attached network'
run analytics-disable

# An original release file without explicit identifiers used historical defaults.
export SKILLHUB_HOME="$TMP/legacy-default"
mkdir -p "$SKILLHUB_HOME/analytics"
printf 'POSTGRES_USER=skillhub\n' >"$SKILLHUB_HOME/.env.release"
printf 'UMAMI_DB_PASSWORD=test-db-secret\nUMAMI_APP_SECRET=test-app-secret\nSKILLHUB_ANALYTICS_NETWORK=skillhub-analytics\n' >"$SKILLHUB_HOME/analytics/.env"
run analytics-ps
grep -qx 'SKILLHUB_ANALYTICS_PROJECT=skillhub-analytics' "$SKILLHUB_HOME/analytics/.env" || fail 'legacy default project migration'

echo 'runtime-analytics-test passed'
