#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMPLATE="$REPO_ROOT/web/nginx.conf.template"
NGINX_IMAGE="${NGINX_TEST_IMAGE:-nginx:alpine}"
TEST_ID="skillhub-nginx-forwarded-proto-$$"
NETWORK="${TEST_ID}-network"
BACKEND="${TEST_ID}-backend"
DEFAULT_PROXY="${TEST_ID}-default"
TRUSTED_PROXY="${TEST_ID}-trusted"
TMP_DIR="$(mktemp -d)"
CONTAINERS=()

cleanup() {
  if ((${#CONTAINERS[@]} > 0)); then
    docker rm -f "${CONTAINERS[@]}" >/dev/null 2>&1 || true
  fi
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

wait_for_nginx() {
  local container="$1"
  local attempt
  for attempt in {1..30}; do
    if docker exec "$container" wget -qO- http://127.0.0.1/nginx-health >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.2
  done
  docker logs "$container" >&2 || true
  fail "$container did not become healthy"
}

start_proxy() {
  local container="$1"
  local trust_forwarded_proto="$2"
  local backend_name="${3:-$BACKEND}"
  docker run --detach \
    --name "$container" \
    --network "$NETWORK" \
    --env "NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1" \
    --env "SKILLHUB_API_UPSTREAM=http://$backend_name:8080" \
    --env "SKILLHUB_TRUST_FORWARDED_PROTO=$trust_forwarded_proto" \
    --volume "$TEMPLATE:/etc/nginx/templates/default.conf.template:ro" \
    "$NGINX_IMAGE" >/dev/null
  CONTAINERS+=("$container")
  wait_for_nginx "$container"
}

assert_proto() {
  local container="$1"
  local expected="$2"
  local header="${3:-}"
  local path="${4:-/api/proto}"
  local actual
  if [[ -n "$header" ]]; then
    actual="$(docker exec "$container" wget -qO- \
      --header="X-Forwarded-Proto: $header" \
      "http://127.0.0.1$path")"
  else
    actual="$(docker exec "$container" wget -qO- "http://127.0.0.1$path")"
  fi
  [[ "$actual" == "$expected" ]] \
    || fail "$container forwarded proto '$actual', expected '$expected' for $path with header '${header:-<none>}'"
}

cat >"$TMP_DIR/backend.conf" <<'EOF'
server {
    listen 8080;
    location / {
        default_type text/plain;
        return 200 $http_x_forwarded_proto;
    }
}
EOF

docker network create "$NETWORK" >/dev/null
docker run --detach \
  --name "$BACKEND" \
  --network "$NETWORK" \
  --volume "$TMP_DIR/backend.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$NGINX_IMAGE" >/dev/null
CONTAINERS+=("$BACKEND")

start_proxy "$DEFAULT_PROXY" false
start_proxy "$TRUSTED_PROXY" true

for path in /api/proto /oauth2/proto /login/oauth2/proto /.well-known/proto; do
  assert_proto "$DEFAULT_PROXY" http https "$path"
  assert_proto "$TRUSTED_PROXY" https https "$path"
done
assert_proto "$TRUSTED_PROXY" http
assert_proto "$TRUSTED_PROXY" http "https,http"

DNS_BACKEND="${TEST_ID}-dns-backend"
DNS_OLD_BACKEND="${TEST_ID}-dns-old"
DNS_NEW_BACKEND="${TEST_ID}-dns-new"
DNS_STALE_BACKEND="${TEST_ID}-dns-stale"
DNS_PROXY="${TEST_ID}-dns-proxy"

start_dns_backend() {
  local container="$1"
  local response="$2"
  local alias="${3:-}"
  local static_ip="${4:-}"
  local config="$TMP_DIR/$container.conf"
  cat >"$config" <<EOF
server {
    listen 8080;
    location / {
        default_type text/plain;
        return 200 "$response";
    }
}
EOF

  local alias_args=()
  local ip_args=()
  if [[ -n "$alias" ]]; then
    alias_args+=(--network-alias "$alias")
  fi
  if [[ -n "$static_ip" ]]; then
    ip_args+=(--ip "$static_ip")
  fi

  docker run --detach \
    --name "$container" \
    --network "$NETWORK" \
    "${alias_args[@]}" \
    "${ip_args[@]}" \
    --volume "$config:/etc/nginx/conf.d/default.conf:ro" \
    "$NGINX_IMAGE" >/dev/null
  CONTAINERS+=("$container")
}

container_ip() {
  docker inspect --format "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" "$1"
}

start_dns_backend "$DNS_OLD_BACKEND" first "$DNS_BACKEND"
start_proxy "$DNS_PROXY" false "$DNS_BACKEND"
assert_dns_response() {
  local expected="$1"
  local actual
  actual="$(docker exec "$DNS_PROXY" wget -qO- http://127.0.0.1/api/dns)" \
    || fail "DNS proxy request failed; expected '$expected'"
  [[ "$actual" == "$expected" ]] \
    || fail "DNS proxy returned '$actual', expected '$expected'"
}
assert_dns_response first

old_ip="$(container_ip "$DNS_OLD_BACKEND")"
docker rm -f "$DNS_OLD_BACKEND" >/dev/null
start_dns_backend "$DNS_STALE_BACKEND" stale "" "$old_ip"
start_dns_backend "$DNS_NEW_BACKEND" second "$DNS_BACKEND"
new_ip="$(container_ip "$DNS_NEW_BACKEND")"
[[ "$old_ip" != "$new_ip" ]] || fail "replacement backend reused old IP $old_ip"

refreshed=false
for attempt in {1..36}; do
  if actual="$(docker exec "$DNS_PROXY" wget -qO- http://127.0.0.1/api/dns 2>/dev/null)" \
    && [[ "$actual" == second ]]; then
    refreshed=true
    break
  fi
  sleep 0.5
done
[[ "$refreshed" == true ]] \
  || { docker logs "$DNS_PROXY" >&2 || true; fail "unchanged proxy did not reach replacement backend $new_ip"; }

echo "nginx-forwarded-proto-test passed, including DNS refresh ($old_ip -> $new_ip)"
