#!/bin/sh
# Source-tree trial only; reuse the release network overlay and pinned demo config.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
STATE_DIR=${BUSINESS_LOGS_DEMO_STATE_DIR:-$ROOT/.dev/business-logs}
COMMAND=${1:-status}
[ "$#" -eq 0 ] || shift

if [ "$COMMAND" = run-host ]; then
  if [ -f "$STATE_DIR/host-port" ]; then
    SKILLHUB_BUSINESS_LOGS_ENABLED=true
    SKILLHUB_BUSINESS_LOGS_ENDPOINT="http://127.0.0.1:$(cat "$STATE_DIR/host-port")/v1/logs"
    export SKILLHUB_BUSINESS_LOGS_ENABLED SKILLHUB_BUSINESS_LOGS_ENDPOINT
  fi
  exec "$@"
fi

export SKILLHUB_BUSINESS_LOGS_NETWORK=${SKILLHUB_BUSINESS_LOGS_NETWORK:-skillhub-business-logs-demo}
export SKILLHUB_BUSINESS_LOGS_DEMO_PORT=${SKILLHUB_BUSINESS_LOGS_DEMO_PORT:-4318}
export SKILLHUB_BUSINESS_LOGS_DEMO_PROJECT=${SKILLHUB_BUSINESS_LOGS_DEMO_PROJECT:-skillhub-business-logs-demo}
BACKEND=${BUSINESS_LOGS_BACKEND:-host}
compose() {
  docker compose -p "$SKILLHUB_BUSINESS_LOGS_DEMO_PROJECT" -f "$ROOT/docs/examples/business-event-logs/compose.yaml" "$@"
}
backend_compose() {
  : "${BUSINESS_LOGS_BACKEND_ENV_FILE:?provide the backend release env file}"
  : "${BUSINESS_LOGS_BACKEND_PROJECT:?provide the backend Compose project name}"
  docker compose -p "$BUSINESS_LOGS_BACKEND_PROJECT" --env-file "$BUSINESS_LOGS_BACKEND_ENV_FILE" \
    -f "${BUSINESS_LOGS_BACKEND_COMPOSE:-$ROOT/compose.release.yml}" \
    -f "$ROOT/compose.release.business-logs.yml" "$@"
}
case "$COMMAND" in
  up)
    case "$BACKEND" in host|container) ;; *) echo 'BUSINESS_LOGS_BACKEND must be host or container' >&2; exit 1 ;; esac
    docker network inspect "$SKILLHUB_BUSINESS_LOGS_NETWORK" >/dev/null 2>&1 || docker network create "$SKILLHUB_BUSINESS_LOGS_NETWORK" >/dev/null
    compose up -d
    if [ "$BACKEND" = host ]; then
      mkdir -p "$STATE_DIR"
      printf '%s\n' "$SKILLHUB_BUSINESS_LOGS_DEMO_PORT" >"$STATE_DIR/host-port"
      echo 'Collector ready. Start/restart the host backend with make dev-server.'
    else
      SKILLHUB_BUSINESS_LOGS_ENABLED=true
      SKILLHUB_BUSINESS_LOGS_ENDPOINT=http://business-logs-collector:4318/v1/logs
      export SKILLHUB_BUSINESS_LOGS_ENABLED SKILLHUB_BUSINESS_LOGS_ENDPOINT
      backend_compose up -d --no-deps --force-recreate server
    fi
    ;;
  status) compose ps ;;
  records|verify)
    command -v jq >/dev/null || { echo 'Install jq to query demo records' >&2; exit 1; }
    scratch=$(mktemp -d)
    trap 'rm -rf "$scratch"' EXIT HUP INT TERM
    compose cp business-logs-collector:/business-logs.json "$scratch/records.jsonl" >/dev/null
    if [ "$COMMAND" = records ]; then
      cat "$scratch/records.jsonl"
    else
      jq -e -s --arg event "${BUSINESS_LOGS_EXPECT_EVENT:-skill.download.prepared}" \
        'any(.[].resourceLogs[].scopeLogs[].logRecords[].attributes[]; .key == "skillhub.event.name" and .value.stringValue == $event)' \
        "$scratch/records.jsonl" >/dev/null
      printf 'Verified event.name=%s in actual Collector records\n' "${BUSINESS_LOGS_EXPECT_EVENT:-skill.download.prepared}"
    fi
    ;;
  down)
    rm -f "$STATE_DIR/host-port"
    if [ "$BACKEND" = container ]; then
      SKILLHUB_BUSINESS_LOGS_ENABLED=false
      export SKILLHUB_BUSINESS_LOGS_ENABLED
      backend_compose up -d --no-deps --force-recreate server
    fi
    compose stop
    echo 'Collector stopped; container records and external network retained. Restart a running host backend to disable collection.'
    ;;
  *) echo 'Usage: business-logs-demo.sh up|status|records|verify|down|run-host command...' >&2; exit 1 ;;
esac
