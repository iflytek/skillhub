#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$REPO_ROOT/deploy/k8s/overlays/with-infra/postgres-statefulset.yaml"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
RUN_ID="skillhub-k8s-pgdata-$$"
FRESH_VOLUME="${RUN_ID}-fresh"
LEGACY_VOLUME="${RUN_ID}-legacy"
CONTAINERS=()
VOLUMES=("$FRESH_VOLUME" "$LEGACY_VOLUME")

cleanup() {
  for container in "${CONTAINERS[@]}"; do
    docker stop "$container" >/dev/null 2>&1 || true
  done
  for volume in "${VOLUMES[@]}"; do
    docker volume rm "$volume" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

for command in docker sed; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "required command not found: $command" >&2
    exit 1
  fi
done

POSTGRES_START_COMMAND="$(
  sed -n '/^            - |$/,/^          ports:$/p' "$MANIFEST" \
    | sed '1d;$d;s/^              //'
)"
if [[ -z "$POSTGRES_START_COMMAND" ]]; then
  echo "could not extract the PostgreSQL startup command from $MANIFEST" >&2
  exit 1
fi

wait_for_postgres() {
  local container="$1"
  for _ in $(seq 1 30); do
    # docker-entrypoint.sh briefly starts a temporary PostgreSQL process while
    # initializing a fresh cluster. Wait until PID 1 is the final server so a
    # successful readiness probe cannot race with that temporary shutdown.
    if docker exec "$container" sh -ec \
      'test "$(cat /proc/1/comm)" = postgres' >/dev/null 2>&1 \
      && docker exec "$container" psql -U skillhub -d skillhub -Atqc \
        'SELECT 1' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  docker logs "$container" >&2
  return 1
}

start_postgres() {
  local container="$1"
  local volume="$2"
  local mode="${3:-default}"
  CONTAINERS+=("$container")
  if [[ "$mode" == "patched" ]]; then
    docker run --detach --rm \
      --name "$container" \
      --env POSTGRES_DB=skillhub \
      --env POSTGRES_USER=skillhub \
      --env POSTGRES_PASSWORD=storage-test \
      --volume "$volume:/var/lib/postgresql/data" \
      --entrypoint sh \
      "$POSTGRES_IMAGE" \
      -ec "$POSTGRES_START_COMMAND" >/dev/null
  else
    docker run --detach --rm \
      --name "$container" \
      --env POSTGRES_DB=skillhub \
      --env POSTGRES_USER=skillhub \
      --env POSTGRES_PASSWORD=storage-test \
      --volume "$volume:/var/lib/postgresql/data" \
      "$POSTGRES_IMAGE" >/dev/null
  fi
  wait_for_postgres "$container"
}

stop_postgres() {
  docker stop "$1" >/dev/null
}

assert_marker() {
  local container="$1"
  local expected="$2"
  local actual
  actual="$(docker exec "$container" psql -U skillhub -d skillhub -Atqc \
    'SELECT value FROM storage_upgrade_verification WHERE id = 1')"
  [[ "$actual" == "$expected" ]]
}

docker volume create "$FRESH_VOLUME" >/dev/null
docker run --rm \
  --volume "$FRESH_VOLUME:/data" \
  --entrypoint sh \
  "$POSTGRES_IMAGE" \
  -ec 'mkdir -p /data/lost+found'

start_postgres "${RUN_ID}-fresh-1" "$FRESH_VOLUME" patched
docker exec "${RUN_ID}-fresh-1" test -f /var/lib/postgresql/data/pgdata/PG_VERSION
docker exec "${RUN_ID}-fresh-1" psql -U skillhub -d skillhub -v ON_ERROR_STOP=1 -qc \
  "CREATE TABLE storage_upgrade_verification (id integer PRIMARY KEY, value text NOT NULL); \
   INSERT INTO storage_upgrade_verification VALUES (1, 'fresh-volume');"
stop_postgres "${RUN_ID}-fresh-1"

start_postgres "${RUN_ID}-fresh-2" "$FRESH_VOLUME" patched
assert_marker "${RUN_ID}-fresh-2" "fresh-volume"
stop_postgres "${RUN_ID}-fresh-2"
echo "PASS: fresh volume with lost+found initializes in pgdata and survives restart"

docker volume create "$LEGACY_VOLUME" >/dev/null
start_postgres "${RUN_ID}-legacy-1" "$LEGACY_VOLUME"
docker exec "${RUN_ID}-legacy-1" test -f /var/lib/postgresql/data/PG_VERSION
docker exec "${RUN_ID}-legacy-1" psql -U skillhub -d skillhub -v ON_ERROR_STOP=1 -qc \
  "CREATE TABLE storage_upgrade_verification (id integer PRIMARY KEY, value text NOT NULL); \
   INSERT INTO storage_upgrade_verification VALUES (1, 'legacy-root');"
stop_postgres "${RUN_ID}-legacy-1"

start_postgres "${RUN_ID}-legacy-2" "$LEGACY_VOLUME" patched
assert_marker "${RUN_ID}-legacy-2" "legacy-root"
stop_postgres "${RUN_ID}-legacy-2"
echo "PASS: legacy root-level cluster remains visible after the manifest upgrade"
