#!/usr/bin/env bash
set -euo pipefail

CHART_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TEST_VALUES="$CHART_DIR/tests/test-values.yaml"
SCENARIO=${HELM_SMOKE_SCENARIO:-default}
NAMESPACE=${HELM_SMOKE_NAMESPACE:-skillhub-helm-smoke-$SCENARIO}
RELEASE=${HELM_SMOKE_RELEASE:-skillhub-smoke}
TIMEOUT=${HELM_SMOKE_TIMEOUT:-15m}
KEEP_ENVIRONMENT=${KEEP_HELM_SMOKE:-false}
TMP_DIR=$(mktemp -d)
PORT_FORWARD_PID=""
OWNS_NAMESPACE=false
HELM_SCENARIO_ARGS=()

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for command in helm kubectl curl jq sha256sum; do
  command -v "$command" >/dev/null 2>&1 || fail "$command is required"
done

if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  fail "namespace $NAMESPACE already exists; choose an unused HELM_SMOKE_NAMESPACE"
fi

stop_port_forward() {
  if [[ -n "$PORT_FORWARD_PID" ]]; then
    kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    wait "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    PORT_FORWARD_PID=""
  fi
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  stop_port_forward

  if (( exit_code != 0 )) && kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
    echo "Helm smoke failed; collecting non-secret diagnostics" >&2
    helm status "$RELEASE" --namespace "$NAMESPACE" >&2 || true
    kubectl get pods,pvc,deployments,statefulsets --namespace "$NAMESPACE" -o wide >&2 || true
    kubectl get events --namespace "$NAMESPACE" --sort-by=.lastTimestamp >&2 || true
  fi

  if [[ "$KEEP_ENVIRONMENT" != "true" && "$OWNS_NAMESPACE" == "true" ]]; then
    helm uninstall "$RELEASE" --namespace "$NAMESPACE" --wait >/dev/null 2>&1 || true
    kubectl delete namespace "$NAMESPACE" --wait --timeout=5m >/dev/null 2>&1 || true
  fi

  rm -rf "$TMP_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

setup_scenario() {
  case "$SCENARIO" in
    default)
      ;;
    sentinel)
      HELM_SCENARIO_ARGS+=(
        --set redis.architecture=replication
        --set redis.sentinel.enabled=true
      )
      ;;
    s3)
      kubectl apply --namespace "$NAMESPACE" -f - <<'YAML'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: minio
spec:
  replicas: 1
  selector:
    matchLabels:
      app: minio
  template:
    metadata:
      labels:
        app: minio
    spec:
      containers:
        - name: minio
          image: docker.io/minio/minio:RELEASE.2025-09-07T16-13-09Z@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e
          args:
            - server
            - /data
          env:
            - name: MINIO_ROOT_USER
              value: smoke-access-key
            - name: MINIO_ROOT_PASSWORD
              value: smoke-secret-key
          ports:
            - name: api
              containerPort: 9000
          readinessProbe:
            httpGet:
              path: /minio/health/ready
              port: api
            periodSeconds: 2
---
apiVersion: v1
kind: Service
metadata:
  name: minio
spec:
  selector:
    app: minio
  ports:
    - name: api
      port: 9000
      targetPort: api
YAML
      kubectl rollout status deployment/minio \
        --namespace "$NAMESPACE" \
        --timeout=5m
      HELM_SCENARIO_ARGS+=(
        --set s3.enabled=true
        --set-string s3.endpoint=http://minio:9000
        --set-string s3.accessKey=smoke-access-key
        --set-string s3.secretKey=smoke-secret-key
        --set s3.autoCreateBucket=true
      )
      ;;
    ingress-tls)
      command -v openssl >/dev/null 2>&1 || fail "openssl is required for ingress-tls"
      openssl req -x509 -newkey rsa:2048 -nodes \
        -keyout "$TMP_DIR/tls.key" \
        -out "$TMP_DIR/tls.crt" \
        -days 1 \
        -subj /CN=skillhub-smoke.local \
        -addext subjectAltName=DNS:skillhub-smoke.local >/dev/null 2>&1
      kubectl create secret tls skillhub-smoke-tls \
        --namespace "$NAMESPACE" \
        --cert "$TMP_DIR/tls.crt" \
        --key "$TMP_DIR/tls.key"
      HELM_SCENARIO_ARGS+=(
        --set ingress.enabled=true
        --set-json 'ingress.hosts=[{"host":"skillhub-smoke.local","paths":[{"path":"/","pathType":"Prefix"}]}]'
        --set-json 'ingress.tls=[{"hosts":["skillhub-smoke.local"],"secretName":"skillhub-smoke-tls"}]'
      )
      ;;
    *)
      fail "unknown HELM_SMOKE_SCENARIO: $SCENARIO"
      ;;
  esac
}

assert_scenario_contract() {
  case "$SCENARIO" in
    default)
      ;;
    sentinel)
      kubectl get deployment "$RELEASE-server" --namespace "$NAMESPACE" -o json \
        | jq -e '
            [.spec.template.spec.containers[]
              | select(.name == "server")
              | .env[]
              | select(.name == "SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST")
              | .value] == ["false"]
          ' >/dev/null \
        || fail "Sentinel scenario did not apply the Kubernetes-only address-check override"
      ;;
    s3)
      local storage_provider
      storage_provider=$(kubectl get configmap "$RELEASE-config" \
        --namespace "$NAMESPACE" -o json | jq -r '.data["skillhub-storage-provider"]')
      [[ "$storage_provider" == "s3" ]] || fail "S3 scenario did not configure S3 storage"
      ;;
    ingress-tls)
      kubectl get ingress "$RELEASE" --namespace "$NAMESPACE" -o json \
        | jq -e --arg server "$RELEASE-server" '
            .spec.tls[0].secretName == "skillhub-smoke-tls"
            and (
              [.spec.rules[].http.paths[]
                | select(
                    .path == "/api"
                    or .path == "/oauth2"
                    or .path == "/login/oauth2"
                    or .path == "/.well-known"
                  )
                | .backend.service.name]
              | length == 4 and all(. == $server)
            )
          ' >/dev/null \
        || fail "TLS Ingress does not route every reserved path directly to the server"
      local cookie_secure
      cookie_secure=$(kubectl get configmap "$RELEASE-config" \
        --namespace "$NAMESPACE" -o json | jq -r '.data["session-cookie-secure"]')
      [[ "$cookie_secure" == "true" ]] || fail "TLS Ingress did not enable secure session cookies"
      ;;
  esac
}

probe_service() {
  local service=$1
  local service_port=$2
  local local_port=$3
  local path=$4
  local expected_status=${5:-200}
  local log_file="$TMP_DIR/${service}.port-forward.log"
  local status

  stop_port_forward
  kubectl port-forward \
    --namespace "$NAMESPACE" \
    "service/$service" \
    "$local_port:$service_port" >"$log_file" 2>&1 &
  PORT_FORWARD_PID=$!

  for _ in $(seq 1 60); do
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
      "http://127.0.0.1:$local_port$path" 2>/dev/null || true)
    if [[ "$status" == "$expected_status" ]]; then
      stop_port_forward
      return 0
    fi
    if ! kill -0 "$PORT_FORWARD_PID" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  cat "$log_file" >&2
  fail "$service$path did not return HTTP $expected_status"
}

snapshot_secrets() {
  local output=$1
  : >"$output"
  for secret in "$RELEASE-secret" "$RELEASE-postgresql" "$RELEASE-redis"; do
    printf '%s ' "$secret" >>"$output"
    kubectl get secret "$secret" --namespace "$NAMESPACE" -o json \
      | jq -cS '.data' \
      | sha256sum \
      | awk '{print $1}' >>"$output"
  done
}

snapshot_pvcs() {
  local output=$1
  kubectl get pvc --namespace "$NAMESPACE" -o json \
    | jq -r '.items[] | [.metadata.name, .metadata.uid, .spec.volumeName] | @tsv' \
    | sort >"$output"
  [[ -s "$output" ]] || fail "Helm install did not create any PVCs"
}

assert_ready_and_healthy() {
  kubectl wait pod \
    --namespace "$NAMESPACE" \
    --all \
    --for=condition=Ready \
    --timeout="$TIMEOUT"

  probe_service "$RELEASE-server" 8080 18081 /actuator/health
  probe_service "$RELEASE-web" 80 18080 /nginx-health
  probe_service "$RELEASE-web" 80 18080 /api/v1/auth/me 401
  probe_service "$RELEASE-scanner" 8000 18082 /health

  local restarts
  restarts=$(kubectl get pods --namespace "$NAMESPACE" -o json \
    | jq '[.items[].status.containerStatuses[]?.restartCount] | add // 0')
  [[ "$restarts" == "0" ]] || fail "workloads restarted $restarts time(s)"
}

helm dependency build "$CHART_DIR"
kubectl create namespace "$NAMESPACE"
OWNS_NAMESPACE=true
setup_scenario

helm install "$RELEASE" "$CHART_DIR" \
  --namespace "$NAMESPACE" \
  --values "$TEST_VALUES" \
  --set-string fullnameOverride="$RELEASE" \
  --set-string publicBaseUrl=http://skillhub-smoke.local \
  "${HELM_SCENARIO_ARGS[@]}" \
  --wait \
  --timeout "$TIMEOUT"

assert_ready_and_healthy
assert_scenario_contract
snapshot_secrets "$TMP_DIR/secrets-before"
snapshot_pvcs "$TMP_DIR/pvcs-before"
revision_before=$(helm history "$RELEASE" --namespace "$NAMESPACE" -o json \
  | jq -r '.[-1].revision')

helm upgrade "$RELEASE" "$CHART_DIR" \
  --namespace "$NAMESPACE" \
  --reuse-values \
  --set-string publicBaseUrl=https://skillhub-smoke.local \
  --set-string server.podAnnotations.helm-smoke-revision=revision-2 \
  --wait \
  --timeout "$TIMEOUT"

assert_ready_and_healthy
assert_scenario_contract
snapshot_secrets "$TMP_DIR/secrets-after"
snapshot_pvcs "$TMP_DIR/pvcs-after"
revision_after=$(helm history "$RELEASE" --namespace "$NAMESPACE" -o json \
  | jq -r '.[-1].revision')

(( revision_after == revision_before + 1 )) \
  || fail "Helm revision did not advance exactly once"
cmp "$TMP_DIR/secrets-before" "$TMP_DIR/secrets-after" \
  || fail "application or dependency Secret data changed during upgrade"
cmp "$TMP_DIR/pvcs-before" "$TMP_DIR/pvcs-after" \
  || fail "PVC identity or bound volume changed during upgrade"

public_base_url=$(kubectl get configmap "$RELEASE-config" \
  --namespace "$NAMESPACE" \
  -o json | jq -r '.data["public-base-url"]')
[[ "$public_base_url" == "https://skillhub-smoke.local" ]] \
  || fail "publicBaseUrl was not applied by the upgrade"

echo "Helm install/upgrade smoke passed for scenario: $SCENARIO"
