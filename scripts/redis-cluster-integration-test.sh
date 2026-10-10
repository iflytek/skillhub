#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
NODES=${REDIS_CLUSTER_TEST_NODES:-${SPRING_DATA_REDIS_CLUSTER_NODES:-}}

if [[ -z "$NODES" ]]; then
  echo "ERROR: REDIS_CLUSTER_TEST_NODES or SPRING_DATA_REDIS_CLUSTER_NODES is required" >&2
  exit 2
fi

export REDIS_CLUSTER_TEST_NODES="$NODES"
export REDIS_CLUSTER_TEST_USERNAME="${REDIS_CLUSTER_TEST_USERNAME:-${SPRING_DATA_REDIS_USERNAME:-}}"
export REDIS_CLUSTER_TEST_PASSWORD="${REDIS_CLUSTER_TEST_PASSWORD:-${SPRING_DATA_REDIS_PASSWORD:-}}"
export REDIS_CLUSTER_TEST_SSL_ENABLED="${REDIS_CLUSTER_TEST_SSL_ENABLED:-${SPRING_DATA_REDIS_SSL_ENABLED:-false}}"

cd "$REPO_ROOT/server"
JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS:-} -XX:+EnableDynamicAgentLoading" \
  ./mvnw -pl skillhub-app -am test \
  -Dtest=RedisClusterIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
