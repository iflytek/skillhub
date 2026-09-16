#!/usr/bin/env bash
set -euo pipefail

CHART_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TEST_VALUES="$CHART_DIR/tests/test-values.yaml"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

render() {
  helm template "$@" -f "$TEST_VALUES"
}

assert_rejected() {
  local name=$1
  shift
  if render "$name" "$CHART_DIR" "$@" >"$TMP_DIR/$name.yaml" 2>"$TMP_DIR/$name.err"; then
    fail "$name should have been rejected"
  fi
}

render verify "$CHART_DIR" >"$TMP_DIR/default.yaml"
grep -Fq 'name: POSTGRESQL_MAX_CONNECTIONS' "$TMP_DIR/default.yaml"
grep -Fq 'value: "verify-postgresql"' "$TMP_DIR/default.yaml"
grep -Fq 'value: "verify-redis-master"' "$TMP_DIR/default.yaml"
grep -Fq 'bitnami/postgresql@sha256:db2312d9b243afa8c3b3f5496e478d17d0dff9791d06f3b93b9567abd86ae92f' "$TMP_DIR/default.yaml"
grep -Fq 'bitnami/postgres-exporter@sha256:53ab72a1b940d7637e91619f1000da9ebef14bc7dad74321a78731d65c79f55b' "$TMP_DIR/default.yaml"
grep -Fq 'bitnami/redis@sha256:08863c2c3f4e051fb6139b38fa223e9c13be5033326a59bead182860d899bf98' "$TMP_DIR/default.yaml"
grep -Fq 'bitnami/redis-exporter@sha256:fb1dae6add1e1104989d086d9407f7d65f58968550aa5fddea20637a758c0773' "$TMP_DIR/default.yaml"
if grep -Eq 'image:.*:latest([@"[:space:]]|$)' "$TMP_DIR/default.yaml"; then
  fail "default workloads must not use mutable latest image tags"
fi
grep -Fq 'fsGroup: 101' "$TMP_DIR/default.yaml"
grep -Fq 'fsGroupChangePolicy: OnRootMismatch' "$TMP_DIR/default.yaml"
grep -Fq 'type: Recreate' "$TMP_DIR/default.yaml"
grep -A1 -F 'name: SKILLHUB_SUITE_REVIEW_WRITES_ENABLED' "$TMP_DIR/default.yaml" \
  | grep -Fq 'value: "false"'

render suite-review-enabled "$CHART_DIR" \
  --set server.suiteReviewWritesEnabled=true \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/suite-review-enabled.yaml"
grep -A1 -F 'name: SKILLHUB_SUITE_REVIEW_WRITES_ENABLED' "$TMP_DIR/suite-review-enabled.yaml" \
  | grep -Fq 'value: "true"'

render custom-server-fsgroup "$CHART_DIR" \
  --set server.podSecurityContext.fsGroup=2000 \
  --set server.podSecurityContext.fsGroupChangePolicy=Always \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/custom-server-fsgroup.yaml"
grep -Fq 'fsGroup: 2000' "$TMP_DIR/custom-server-fsgroup.yaml"
grep -Fq 'fsGroupChangePolicy: Always' "$TMP_DIR/custom-server-fsgroup.yaml"

stable_args=(
  --set-string secrets.bootstrapAdminPassword=stable-bootstrap-password
  --set-string secrets.downloadAnonCookieSecret=stable-download-cookie-secret
  --set-string postgresql.auth.postgresPassword=stable-postgres-password
  --set-string postgresql.auth.password=stable-user-password
  --set-string redis.auth.password=stable-redis-password
)
render stable "$CHART_DIR" "${stable_args[@]}" >"$TMP_DIR/stable-a.yaml"
render stable "$CHART_DIR" "${stable_args[@]}" >"$TMP_DIR/stable-b.yaml"
cmp "$TMP_DIR/stable-a.yaml" "$TMP_DIR/stable-b.yaml"

render private-registry "$CHART_DIR" \
  --set server.dependencyWait.image.registry=registry.example.com \
  --set server.dependencyWait.image.repository=library/busybox \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/private-registry.yaml"
grep -Fq 'image: "registry.example.com/library/busybox:1.37"' "$TMP_DIR/private-registry.yaml"

render postgresql-replication "$CHART_DIR" \
  --set postgresql.architecture=replication >"$TMP_DIR/postgresql-replication.yaml"
if [[ $(grep -Fc 'name: POSTGRESQL_MAX_CONNECTIONS' "$TMP_DIR/postgresql-replication.yaml") -ne 2 ]]; then
  fail "PostgreSQL primary and read replica must use the same max_connections setting"
fi

render custom "$CHART_DIR" \
  --set postgresql.auth.existingSecret=custom-pg \
  --set postgresql.auth.secretKeys.userPasswordKey=custom-pg-key \
  --set redis.auth.existingSecret=custom-redis \
  --set redis.auth.existingSecretPasswordKey=custom-redis-key \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/custom.yaml"
grep -Fq 'name: custom-pg' "$TMP_DIR/custom.yaml"
grep -Fq 'key: custom-pg-key' "$TMP_DIR/custom.yaml"
grep -Fq 'name: custom-redis' "$TMP_DIR/custom.yaml"
grep -Fq 'key: custom-redis-key' "$TMP_DIR/custom.yaml"

render postgresql-admin "$CHART_DIR" \
  --set postgresql.auth.username=postgres \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/postgresql-admin.yaml"
grep -Fq 'value: "postgres"' "$TMP_DIR/postgresql-admin.yaml"
grep -Fq 'key: postgres-password' "$TMP_DIR/postgresql-admin.yaml"
render postgresql-admin-secret "$CHART_DIR" \
  --set postgresql.auth.username=postgres \
  --show-only charts/postgresql/templates/secrets.yaml >"$TMP_DIR/postgresql-admin-secret.yaml"
grep -Eq '^  postgres-password:' "$TMP_DIR/postgresql-admin-secret.yaml"
if grep -Eq '^  password:' "$TMP_DIR/postgresql-admin-secret.yaml"; then
  fail "Bitnami PostgreSQL must not create a custom-user password key for username=postgres"
fi

render postgresql-admin-existing-secret "$CHART_DIR" \
  --set postgresql.auth.username=postgres \
  --set postgresql.auth.existingSecret=custom-pg-admin \
  --set postgresql.auth.secretKeys.adminPasswordKey=custom-admin-key \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/postgresql-admin-existing-secret.yaml"
grep -Fq 'name: custom-pg-admin' "$TMP_DIR/postgresql-admin-existing-secret.yaml"
grep -Fq 'key: custom-admin-key' "$TMP_DIR/postgresql-admin-existing-secret.yaml"

render sentinel "$CHART_DIR" \
  --set redis.architecture=replication \
  --set redis.sentinel.enabled=true \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/sentinel.yaml"
grep -Fq 'value: "docker,redis-sentinel"' "$TMP_DIR/sentinel.yaml"
grep -Fq 'value: "mymaster"' "$TMP_DIR/sentinel.yaml"
grep -Fq '.svc.cluster.local:26379' "$TMP_DIR/sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_PASSWORD' "$TMP_DIR/sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_SENTINEL_PASSWORD' "$TMP_DIR/sentinel.yaml"
grep -A1 -F 'name: SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST' "$TMP_DIR/sentinel.yaml" \
  | grep -Fq 'value: "false"'
render sentinel-full "$CHART_DIR" \
  --set redis.architecture=replication \
  --set redis.sentinel.enabled=true >"$TMP_DIR/sentinel-full.yaml"
grep -Fq 'bitnami/redis-sentinel@sha256:ae75dd69c192a632bdeb21baa6721080be5b12347e52add922036398b47631da' "$TMP_DIR/sentinel-full.yaml"
if grep -Eq 'image:.*:latest([@"[:space:]]|$)' "$TMP_DIR/sentinel-full.yaml"; then
  fail "Sentinel workloads must not use mutable latest image tags"
fi

render external-sentinel "$CHART_DIR" \
  --set postgresql.enabled=false \
  --set externalDatabase.host=db.example.com \
  --set redis.enabled=false \
  --set externalRedis.username=redis-user \
  --set externalRedis.password=redis-password \
  --set externalRedis.sentinel.enabled=true \
  --set externalRedis.sentinel.username=sentinel-user \
  --set externalRedis.sentinel.password=sentinel-password \
  --set-json 'externalRedis.sentinel.nodes=["sentinel-a:26379","sentinel-b:26379"]' \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/external-sentinel.yaml"
grep -Fq 'value: "sentinel-a"' "$TMP_DIR/external-sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_PASSWORD' "$TMP_DIR/external-sentinel.yaml"
grep -Fq 'name: SPRING_DATA_REDIS_SENTINEL_PASSWORD' "$TMP_DIR/external-sentinel.yaml"
grep -A1 -F 'name: SPRING_DATA_REDIS_USERNAME' "$TMP_DIR/external-sentinel.yaml" \
  | grep -Fq 'value: "redis-user"'
grep -A1 -F 'name: SPRING_DATA_REDIS_SENTINEL_USERNAME' "$TMP_DIR/external-sentinel.yaml" \
  | grep -Fq 'value: "sentinel-user"'
if grep -Fq 'name: SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST' "$TMP_DIR/external-sentinel.yaml"; then
  fail "external Sentinel must preserve Redisson address consistency checks by default"
fi

render external-cluster "$CHART_DIR" \
  --set postgresql.enabled=false \
  --set externalDatabase.host=db.example.com \
  --set redis.enabled=false \
  --set existingSecret=skillhub-production-secret \
  --set externalRedis.username=skillhub \
  --set externalRedis.tls.enabled=true \
  --set externalRedis.connectTimeout=5s \
  --set externalRedis.timeout=3s \
  --set externalRedis.clientName=skillhub-server \
  --set externalRedis.cluster.enabled=true \
  --set externalRedis.cluster.maxRedirects=7 \
  --set-json 'externalRedis.cluster.nodes=["redis-a.example.com:6379","redis-b.example.com:6380"]' \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/external-cluster.yaml"
grep -A1 -F 'name: REDIS_HOST' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "redis-a.example.com"'
grep -A1 -F 'name: REDIS_PORT' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "6379"'
grep -A1 -F 'name: SPRING_DATA_REDIS_CLUSTER_NODES' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "redis-a.example.com:6379,redis-b.example.com:6380"'
grep -A1 -F 'name: SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "7"'
grep -A1 -F 'name: SPRING_DATA_REDIS_USERNAME' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "skillhub"'
grep -A1 -F 'name: SPRING_DATA_REDIS_SSL_ENABLED' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "true"'
grep -A1 -F 'name: SPRING_DATA_REDIS_CONNECT_TIMEOUT' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "5s"'
grep -A1 -F 'name: SPRING_DATA_REDIS_TIMEOUT' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "3s"'
grep -A1 -F 'name: SPRING_DATA_REDIS_CLIENT_NAME' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'value: "skillhub-server"'
grep -A4 -F 'name: SPRING_DATA_REDIS_PASSWORD' "$TMP_DIR/external-cluster.yaml" \
  | grep -Fq 'name: skillhub-production-secret'
if grep -Fq 'name: SPRING_DATA_REDIS_HOST' "$TMP_DIR/external-cluster.yaml"; then
  fail "external Redis Cluster must not render standalone host configuration"
fi
if grep -Fq 'name: SPRING_DATA_REDIS_SENTINEL_NODES' "$TMP_DIR/external-cluster.yaml"; then
  fail "external Redis Cluster must not render Sentinel configuration"
fi

render special "$CHART_DIR" \
  --set-string 'bootstrapAdmin.displayName=Ops: Admin' \
  --show-only templates/configmap.yaml >"$TMP_DIR/special.yaml"
grep -Fq 'bootstrap-admin-display-name: "Ops: Admin"' "$TMP_DIR/special.yaml"

render device "$CHART_DIR" \
  --set publicBaseUrl=https://skills.example.com \
  --show-only templates/configmap.yaml >"$TMP_DIR/device.yaml"
grep -Fq 'device-auth-verification-uri: "https://skills.example.com/cli/auth"' "$TMP_DIR/device.yaml"

render tls "$CHART_DIR" \
  --set ingress.enabled=true \
  --set-json 'ingress.tls=[{"hosts":["skills.example.com"],"secretName":"skills-tls"}]' \
  --show-only templates/configmap.yaml >"$TMP_DIR/tls.yaml"
grep -Fq 'session-cookie-secure: "true"' "$TMP_DIR/tls.yaml"
render tls "$CHART_DIR" \
  --set ingress.enabled=true \
  --set-json 'ingress.tls=[{"hosts":["skills.example.com"],"secretName":"skills-tls"}]' \
  --show-only templates/ingress.yaml >"$TMP_DIR/tls-ingress.yaml"
for server_path in /api /oauth2 /login/oauth2 /.well-known; do
  grep -Fq -- "- path: $server_path" "$TMP_DIR/tls-ingress.yaml"
done
if [[ $(grep -Fc 'name: tls-skillhub-server' "$TMP_DIR/tls-ingress.yaml") -ne 4 ]]; then
  fail "API and OAuth ingress paths must route directly to the SkillHub server"
fi

render legacy-ingress "$CHART_DIR" \
  --set ingress.enabled=true \
  --set-string ingress.className= \
  --set-json 'ingress.annotations={"kubernetes.io/ingress.class":"alb","alb.ingress.kubernetes.io/listen-ports":"[{\"HTTPS\":6443}]"}' \
  --show-only templates/ingress.yaml >"$TMP_DIR/legacy-ingress.yaml"
grep -Fq 'kubernetes.io/ingress.class: alb' "$TMP_DIR/legacy-ingress.yaml"
grep -Fq 'alb.ingress.kubernetes.io/listen-ports:' "$TMP_DIR/legacy-ingress.yaml"
if grep -Fq 'ingressClassName:' "$TMP_DIR/legacy-ingress.yaml"; then
  fail "empty ingress.className must omit spec.ingressClassName"
fi

render multi-host-ingress "$CHART_DIR" \
  --set ingress.enabled=true \
  --set ingress.certManager.enabled=true \
  --set-json 'ingress.hosts=[{"host":"skills-a.example.com","paths":[{"path":"/","pathType":"Prefix"}]},{"host":"skills-b.example.com","paths":[{"path":"/portal","pathType":"Prefix"}]}]' \
  --set-json 'ingress.tls=[{"hosts":["skills-a.example.com","skills-b.example.com"],"secretName":"skills-tls"}]' \
  --show-only templates/ingress.yaml \
  --show-only templates/certificate.yaml >"$TMP_DIR/multi-host-ingress.yaml"
if [[ $(grep -Fc 'skills-a.example.com' "$TMP_DIR/multi-host-ingress.yaml") -ne 3 ]]; then
  fail "first ingress host must be rendered in rule, TLS and Certificate"
fi
if [[ $(grep -Fc 'skills-b.example.com' "$TMP_DIR/multi-host-ingress.yaml") -ne 3 ]]; then
  fail "second ingress host must be rendered in rule, TLS and Certificate"
fi

render scanner-off "$CHART_DIR" \
  --set scanner.enabled=false \
  --set scanner.autoscaling.enabled=true \
  --set scanner.podDisruptionBudget.enabled=true >"$TMP_DIR/scanner-off.yaml"
if awk '
  $1 == "kind:" { kind=$2 }
  kind ~ /^(Deployment|Service|HorizontalPodAutoscaler|PodDisruptionBudget)$/ &&
    $1 == "name:" && $2 == "scanner-off-skillhub-scanner" { found=1 }
  END { exit found ? 0 : 1 }
' "$TMP_DIR/scanner-off.yaml"; then
  fail "disabled scanner rendered workload resources"
fi

render multi-rwx "$CHART_DIR" \
  --set server.replicaCount=2 \
  --set server.storage.accessMode=ReadWriteMany >"$TMP_DIR/multi-rwx.yaml"
grep -Fq -- '- ReadWriteMany' "$TMP_DIR/multi-rwx.yaml"
grep -Fq 'type: RollingUpdate' "$TMP_DIR/multi-rwx.yaml"

render s3-rolling "$CHART_DIR" \
  --set s3.enabled=true \
  --set s3.bucket=skillhub \
  --set s3.endpoint=https://s3.example.com \
  --set s3.accessKey=access-key \
  --set s3.secretKey=secret-key \
  --show-only templates/server-deployment.yaml >"$TMP_DIR/s3-rolling.yaml"
grep -Fq 'type: RollingUpdate' "$TMP_DIR/s3-rolling.yaml"

assert_rejected server-off --set server.enabled=false
assert_rejected direct-auth-without-provider \
  --set auth.direct.enabled=true \
  --set-string auth.direct.provider=
assert_rejected ingress-without-server-service --set ingress.enabled=true --set server.service.enabled=false
assert_rejected ingress-without-web-service --set ingress.enabled=true --set web.service.enabled=false
assert_rejected multi-without-rwx --set server.replicaCount=2
assert_rejected hpa-without-metrics \
  --set server.autoscaling.enabled=true \
  --set server.autoscaling.targetCPUUtilizationPercentage=0 \
  --set server.autoscaling.targetMemoryUtilizationPercentage=0
assert_rejected old-postgres-env --set-json 'postgresql.primary.extraEnv=[{"name":"X","value":"Y"}]'
assert_rejected old-sentinel-password --set redis.auth.sentinelPassword=unused
assert_rejected old-sentinel-nodes --set redis.sentinel.nodes=unused
assert_rejected old-sentinel-service-switch --set redis.sentinel.service.enabled=false
assert_rejected internal-and-external-cluster \
  --set externalRedis.cluster.enabled=true \
  --set-json 'externalRedis.cluster.nodes=["redis-a.example.com:6379"]'
assert_rejected sentinel-and-cluster \
  --set redis.enabled=false \
  --set externalRedis.sentinel.enabled=true \
  --set externalRedis.cluster.enabled=true \
  --set-json 'externalRedis.sentinel.nodes=["sentinel-a.example.com:26379"]' \
  --set-json 'externalRedis.cluster.nodes=["redis-a.example.com:6379"]'
assert_rejected cluster-without-nodes \
  --set redis.enabled=false \
  --set externalRedis.cluster.enabled=true
assert_rejected cluster-invalid-node \
  --set redis.enabled=false \
  --set externalRedis.cluster.enabled=true \
  --set-json 'externalRedis.cluster.nodes=["redis-a.example.com"]'
assert_rejected cluster-invalid-port \
  --set redis.enabled=false \
  --set externalRedis.cluster.enabled=true \
  --set-json 'externalRedis.cluster.nodes=["redis-a.example.com:65536"]'
assert_rejected invalid-fullname --set fullnameOverride=INVALID_NAME
assert_rejected old-ingress-host --set ingress.host=old.example.com
assert_rejected old-ingress-tls-object --set ingress.tls.enabled=true
assert_rejected reserved-oauth-ingress-path \
  --set ingress.enabled=true \
  --set-json 'ingress.hosts=[{"host":"skills.example.com","paths":[{"path":"/oauth2","pathType":"Prefix"}]}]'
assert_rejected reserved-oauth-ingress-child-path \
  --set ingress.enabled=true \
  --set-json 'ingress.hosts=[{"host":"skills.example.com","paths":[{"path":"/login/oauth2/code/github","pathType":"Prefix"}]}]'
assert_rejected invalid-s3-endpoint --set s3.endpoint=s3.amazonaws.com
assert_rejected invalid-s3-public-endpoint --set s3.publicEndpoint=cdn.example.com
assert_rejected invalid-s3-empty-authority --set-string 's3.endpoint=https://?'
assert_rejected invalid-s3-whitespace-authority --set-string 's3.publicEndpoint=https:// '
assert_rejected empty-ingress-hosts --set-json 'ingress.hosts=[]'
assert_rejected cert-manager-without-tls \
  --set ingress.enabled=true \
  --set ingress.certManager.enabled=true \
  --set-json 'ingress.tls=[]'
if helm template missing-credentials "$CHART_DIR" >"$TMP_DIR/missing-credentials.yaml" 2>"$TMP_DIR/missing-credentials.err"; then
  fail "default rendering without stable credentials should have been rejected"
fi

# Sub-path deployment: base path and API prefix must flow from values into the
# config map and be injected into the web deployment.
grep -Fq 'web-base-path: ""' "$TMP_DIR/default.yaml" \
  || fail "default config map web-base-path must be empty so a fixed-base image is honored"

render subpath "$CHART_DIR" \
  --set web.basePath=/portal/ \
  --set web.apiBaseUrl=/portal >"$TMP_DIR/subpath.yaml"
grep -Fq 'web-base-path: "/portal/"' "$TMP_DIR/subpath.yaml" \
  || fail "config map must expose the configured web base path"
grep -Fq 'web-api-base-url: "/portal"' "$TMP_DIR/subpath.yaml" \
  || fail "config map must expose the configured web API base url"
grep -Fq 'name: SKILLHUB_WEB_BASE_PATH' "$TMP_DIR/subpath.yaml" \
  || fail "web deployment must set SKILLHUB_WEB_BASE_PATH"
grep -Fq 'key: web-base-path' "$TMP_DIR/subpath.yaml" \
  || fail "web deployment must source SKILLHUB_WEB_BASE_PATH from the config map"
grep -Fq 'key: web-api-base-url' "$TMP_DIR/subpath.yaml" \
  || fail "web deployment must source SKILLHUB_WEB_API_BASE_URL from the config map"

# A sub-path base must be consistent with publicBaseUrl and be a normalized path.
assert_rejected subpath-public-mismatch \
  --set web.basePath=/portal/ \
  --set-string publicBaseUrl=https://skills.example.com
assert_rejected subpath-dot-segment --set web.basePath=/foo/../bar/
assert_rejected subpath-missing-trailing --set-string web.basePath=/portal
assert_rejected subpath-api-base-mismatch \
  --set web.basePath=/portal/ \
  --set-string web.apiBaseUrl=/other \
  --set-string publicBaseUrl=https://skills.example.com/portal
# A base path whose first segment is reserved by the server would shadow the
# server's own Nginx location and break the app.
assert_rejected subpath-reserved-api --set-string web.basePath=/api/
assert_rejected subpath-reserved-assets --set-string web.basePath=/assets/
assert_rejected subpath-reserved-well-known --set-string web.basePath=/.well-known/
assert_rejected subpath-reserved-nested --set-string web.basePath=/api/nested/

# publicBaseUrl is concatenated with paths (/cli/auth, /.well-known/clawhub.json),
# so a query or fragment corrupts the generated URLs. Reject it independently of
# web.basePath (these cases use the default root deployment).
assert_rejected public-base-url-query --set-string publicBaseUrl=https://skills.example.com/skillhub?ref=1
assert_rejected public-base-url-fragment --set-string publicBaseUrl=https://skills.example.com#frag
assert_rejected public-base-url-no-host --set-string publicBaseUrl=https://

echo "Helm configuration contract tests passed"
