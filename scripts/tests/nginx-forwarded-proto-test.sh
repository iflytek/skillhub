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
ORIGIN_ASSERTIONS=0

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

create_test_network() {
  local subnet
  for subnet in 172.29.0.0/24 172.30.0.0/24 192.168.252.0/24 10.254.0.0/24; do
    if docker network create --driver bridge --subnet "$subnet" "$NETWORK" >/dev/null 2>&1; then
      return 0
    fi
  done
  fail "could not create a test network with an explicit subnet"
}

start_proxy() {
  local container="$1"
  local trust_forwarded_proto="$2"
  local backend_name="${3:-$BACKEND}"
  local base_path="${4:-/}"
  docker run --detach \
    --name "$container" \
    --network "$NETWORK" \
    --publish 127.0.0.1::80 \
    --env "NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1" \
    --env "SKILLHUB_API_UPSTREAM=http://$backend_name:8080" \
    --env "SKILLHUB_TRUST_FORWARDED_PROTO=$trust_forwarded_proto" \
    --env "SKILLHUB_WEB_BASE_PATH=$base_path" \
    --volume "$TEMPLATE:/etc/nginx/templates/default.conf.template:ro" \
    --volume "$TMP_DIR/20-base-path.sh:/docker-entrypoint.d/20-base-path.sh:ro" \
    "$NGINX_IMAGE" >/dev/null
  CONTAINERS+=("$container")
  wait_for_nginx "$container"
}

# This backend observes actual forwarded headers, not a simulated redirect. The
# release gate separately exercises Spring's redirect construction and logout.
assert_origin() {
  local container="$1" authority="$2" expected_proto="$3" expected_prefix="$4" path="$5"
  local proto_header="${6:-https}" version="${7:---http1.1}" port actual expected host_header="Host:"
  [[ -z "$authority" ]] || host_header="Host: $authority"
  port="$(docker inspect --format '{{(index (index .NetworkSettings.Ports "80/tcp") 0).HostPort}}' "$container")"
  actual="$(curl --noproxy '*' -fsS "$version" \
    -H "$host_header" \
    -H "X-Forwarded-Proto: $proto_header" \
    -H 'Forwarded: for=192.0.2.1;host="attacker.example:9443";proto=https' \
    -H 'X-Forwarded-Host: attacker.example:9443' \
    -H 'X-Forwarded-Port: 9443' \
    -H 'X-Forwarded-Prefix: /attacker' \
    "http://127.0.0.1:$port$path")" \
    || fail "origin request rejected: $container $path Host='${authority:-<none>}' ($version)"
  expected="${authority:-_}|${authority:-_}|||$expected_proto|$expected_prefix"
  [[ "$actual" == "$expected" ]] \
    || fail "origin $container $path Host='${authority:-<none>}': '$actual', expected '$expected'"
  ORIGIN_ASSERTIONS=$((ORIGIN_ASSERTIONS + 1))
}

assert_invalid_host() {
  local container="$1" authority="$2" version="${3:---http1.1}" port status
  port="$(docker inspect --format '{{(index (index .NetworkSettings.Ports "80/tcp") 0).HostPort}}' "$container")"
  status="$(curl --noproxy '*' -sS "$version" -o /dev/null -w '%{http_code}' \
    -H "Host: $authority" "http://127.0.0.1:$port/api/origin")"
  [[ "$status" == 400 ]] || fail "invalid Host '$authority' returned $status, expected 400"
  ORIGIN_ASSERTIONS=$((ORIGIN_ASSERTIONS + 1))
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
    location ~ /origin$ {
        default_type text/plain;
        return 200 "$http_host|$http_x_forwarded_host|$http_x_forwarded_port|$http_forwarded|$http_x_forwarded_proto|$http_x_forwarded_prefix";
    }
    location / {
        default_type text/plain;
        return 200 $http_x_forwarded_proto;
    }
}
EOF

create_test_network
cp "$REPO_ROOT/web/docker-entrypoint.d/20-base-path.sh" "$TMP_DIR/20-base-path.sh"
chmod +x "$TMP_DIR/20-base-path.sh"
docker run --detach \
  --name "$BACKEND" \
  --network "$NETWORK" \
  --volume "$TMP_DIR/backend.conf:/etc/nginx/conf.d/default.conf:ro" \
  --entrypoint nginx "$NGINX_IMAGE" -g 'daemon off;' >/dev/null
CONTAINERS+=("$BACKEND")

start_proxy "$DEFAULT_PROXY" false
start_proxy "$TRUSTED_PROXY" true

for path in /api/proto /oauth2/proto /login/oauth2/proto /.well-known/proto; do
  assert_proto "$DEFAULT_PROXY" http https "$path"
  assert_proto "$TRUSTED_PROXY" https https "$path"
done
assert_proto "$TRUSTED_PROXY" http
assert_proto "$TRUSTED_PROXY" http "https,http"

SUBPATH_DEFAULT="${TEST_ID}-subpath-default"
SUBPATH_TRUSTED="${TEST_ID}-subpath-trusted"
start_proxy "$SUBPATH_DEFAULT" false "$BACKEND" /skillhub/
start_proxy "$SUBPATH_TRUSTED" true "$BACKEND" /skillhub/

for path in /api/origin /oauth2/origin /login/oauth2/origin /.well-known/origin; do
  for authority in skill.example.com:28086 skill.example.com skill.example.com:80 \
    skill.example.com:443 skill.example.com:8443 127.0.0.1:28086 \
    skillhub_web:28086 '[::1]' '[2001:db8::1]:8443'; do
    assert_origin "$DEFAULT_PROXY" "$authority" http '' "$path"
    assert_origin "$TRUSTED_PROXY" "$authority" https '' "$path"
    assert_origin "$SUBPATH_DEFAULT" "$authority" http /skillhub "/skillhub$path"
    assert_origin "$SUBPATH_TRUSTED" "$authority" https /skillhub "/skillhub$path"
  done
  assert_origin "$TRUSTED_PROXY" skill.example.com:28086 http '' "$path" http
  assert_origin "$TRUSTED_PROXY" skill.example.com:28086 http '' "$path" 'https,http'
  assert_origin "$SUBPATH_TRUSTED" skill.example.com:28086 http /skillhub "/skillhub$path" 'invalid'
  assert_origin "$DEFAULT_PROXY" '' http '' "$path" http --http1.0
done
for container in "$DEFAULT_PROXY" "$TRUSTED_PROXY" "$SUBPATH_DEFAULT" "$SUBPATH_TRUSTED"; do
  for bad_host in 'bad/path' 'bad..host' 'user@host.example' 'host.example:invalid' 'bad,host' 'bad host'; do
    assert_invalid_host "$container" "$bad_host"
  done
  assert_invalid_host "$container" ''
done
echo "origin forwarding passed ($ORIGIN_ASSERTIONS assertions); spoofed headers never reached upstream"

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
    --entrypoint nginx "$NGINX_IMAGE" -g 'daemon off;' >/dev/null
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
saw_stale_response=false
refresh_started=$SECONDS
for attempt in {1..36}; do
  if actual="$(docker exec "$DNS_PROXY" wget -qO- http://127.0.0.1/api/dns 2>/dev/null)"; then
    if [[ "$actual" == second ]]; then
      refreshed=true
      break
    fi
    [[ "$actual" == stale ]] \
      || fail "DNS proxy returned unexpected backend marker '$actual'"
    saw_stale_response=true
  fi
  sleep 0.5
done
[[ "$refreshed" == true ]] \
  || { docker logs "$DNS_PROXY" >&2 || true; fail "unchanged proxy did not reach replacement backend $new_ip"; }
refresh_elapsed=$((SECONDS - refresh_started))
((refresh_elapsed <= 12)) \
  || fail "DNS refresh took ${refresh_elapsed}s, exceeding the 12s test boundary"

echo "nginx-forwarded-proto-test passed, including DNS refresh ($old_ip -> $new_ip) in ${refresh_elapsed}s; stale cache observed=$saw_stale_response"
