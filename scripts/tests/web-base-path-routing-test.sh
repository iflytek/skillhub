#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
ENTRYPOINT="$ROOT_DIR/web/docker-entrypoint.d/20-base-path.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

web_root="$tmp/html"
routing_config="$tmp/skillhub-base-path.conf"
mkdir -p "$web_root"
printf '%s\n' \
  '<link rel="icon" href="/__SKILLHUB_WEB_BASE_PATH__/favicon.svg" />' \
  '/__SKILLHUB_WEB_BASE_PATH__/assets/index.js' >"$web_root/index.html"

SKILLHUB_WEB_BASE_PATH=/skillhub/ \
SKILLHUB_WEB_ROOT="$web_root" \
SKILLHUB_NGINX_BASE_PATH_CONFIG="$routing_config" \
sh "$ENTRYPOINT"

grep -F 'location = /skillhub {' "$routing_config" >/dev/null
grep -F 'return 301 /skillhub/;' "$routing_config" >/dev/null
grep -F 'location ^~ /skillhub/ {' "$routing_config" >/dev/null
grep -F 'rewrite ^/skillhub/(.*)$ /$1 last;' "$routing_config" >/dev/null
grep -F 'set $skillhub_forwarded_prefix /skillhub;' "$routing_config" >/dev/null
grep -F '/skillhub/assets/index.js' "$web_root/index.html" >/dev/null
# favicon (and any other absolute asset ref in index.html) picks up the sub-path prefix.
grep -F '/skillhub/favicon.svg' "$web_root/index.html" >/dev/null

# A same-origin API base that does not match the base path must be rejected at startup.
mismatch_root="$tmp/mismatch-html"
mismatch_config="$tmp/mismatch.conf"
mkdir -p "$mismatch_root"
if SKILLHUB_WEB_BASE_PATH=/skillhub/ \
   SKILLHUB_WEB_API_BASE_URL=/other \
   SKILLHUB_WEB_ROOT="$mismatch_root" \
   SKILLHUB_NGINX_BASE_PATH_CONFIG="$mismatch_config" \
   sh "$ENTRYPOINT" 2>/dev/null; then
  echo 'entrypoint must reject an API base that does not match the base path' >&2
  exit 1
fi

if [ "$(grep -Fc 'proxy_set_header X-Forwarded-Prefix $skillhub_forwarded_prefix;' "$ROOT_DIR/web/nginx.conf.template")" -ne 4 ]; then
  echo 'API, OAuth, and .well-known proxy locations must forward the configured base path' >&2
  exit 1
fi

root_web_root="$tmp/root-html"
root_routing_config="$tmp/root-base-path.conf"
mkdir -p "$root_web_root"
printf '%s\n' '/__SKILLHUB_WEB_BASE_PATH__/assets/index.js' >"$root_web_root/index.html"

SKILLHUB_WEB_BASE_PATH=/ \
SKILLHUB_WEB_ROOT="$root_web_root" \
SKILLHUB_NGINX_BASE_PATH_CONFIG="$root_routing_config" \
sh "$ENTRYPOINT"

grep -F '# No sub-path routing is required for root deployment.' "$root_routing_config" >/dev/null
grep -F 'set $skillhub_forwarded_prefix "";' "$root_routing_config" >/dev/null
if grep -F 'location ' "$root_routing_config" >/dev/null; then
  echo 'root deployment must not generate a duplicate Nginx location' >&2
  exit 1
fi
grep -F '/assets/index.js' "$root_web_root/index.html" >/dev/null

# Runtime input validation must reject '.'/'..' path segments (they normalize and
# would desync the generated location from the baked asset URLs).
for bad in '/foo/../bar/' '/foo/./bar/' '/foo//bar/' '/no-trailing' 'foo/' \
           '/api/' '/oauth2/' '/login/' '/assets/' '/registry/' '/nginx-health/' \
           '/.well-known/' '/runtime-config.js/' '/api/nested/'; do
  reject_root="$tmp/reject-html"
  reject_config="$tmp/reject.conf"
  mkdir -p "$reject_root"
  if SKILLHUB_WEB_BASE_PATH="$bad" \
     SKILLHUB_WEB_ROOT="$reject_root" \
     SKILLHUB_NGINX_BASE_PATH_CONFIG="$reject_config" \
     sh "$ENTRYPOINT" 2>/dev/null; then
    echo "entrypoint must reject invalid SKILLHUB_WEB_BASE_PATH: $bad" >&2
    exit 1
  fi
  rm -rf "$reject_root" "$reject_config"
done

# A fixed-base build (no runtime SKILLHUB_WEB_BASE_PATH) must default to the baked
# base recorded at build time and generate the matching Nginx routing.
baked_root="$tmp/baked-html"
baked_config="$tmp/baked.conf"
baked_file="$tmp/baked-base-path"
mkdir -p "$baked_root"
printf '%s\n' '/skillhub/assets/index.js' >"$baked_root/index.html"
printf '%s' '/skillhub/' >"$baked_file"

SKILLHUB_WEB_ROOT="$baked_root" \
SKILLHUB_NGINX_BASE_PATH_CONFIG="$baked_config" \
SKILLHUB_WEB_BAKED_BASE_PATH_FILE="$baked_file" \
sh "$ENTRYPOINT"

grep -F 'location ^~ /skillhub/ {' "$baked_config" >/dev/null
grep -F 'set $skillhub_forwarded_prefix /skillhub;' "$baked_config" >/dev/null

# The bundled Compose/K8s deploy configs pass SKILLHUB_WEB_BASE_PATH as an empty
# string (unset by the operator). An empty value must be treated as "use the
# baked base", not as root — otherwise a fixed-base image serves broken assets.
empty_config="$tmp/empty.conf"
SKILLHUB_WEB_BASE_PATH= \
SKILLHUB_WEB_ROOT="$baked_root" \
SKILLHUB_NGINX_BASE_PATH_CONFIG="$empty_config" \
SKILLHUB_WEB_BAKED_BASE_PATH_FILE="$baked_file" \
sh "$ENTRYPOINT"

grep -F 'location ^~ /skillhub/ {' "$empty_config" >/dev/null
grep -F 'set $skillhub_forwarded_prefix /skillhub;' "$empty_config" >/dev/null

# A runtime base that conflicts with the baked base must fail loudly rather than
# silently serving mismatched routing/assets.
conflict_config="$tmp/conflict.conf"
if SKILLHUB_WEB_BASE_PATH=/other/ \
   SKILLHUB_WEB_ROOT="$baked_root" \
   SKILLHUB_NGINX_BASE_PATH_CONFIG="$conflict_config" \
   SKILLHUB_WEB_BAKED_BASE_PATH_FILE="$baked_file" \
   sh "$ENTRYPOINT" 2>/dev/null; then
  echo 'entrypoint must reject a runtime base that conflicts with the baked base' >&2
  exit 1
fi

# An explicit runtime base matching the baked base is accepted.
match_config="$tmp/match.conf"
SKILLHUB_WEB_BASE_PATH=/skillhub/ \
SKILLHUB_WEB_ROOT="$baked_root" \
SKILLHUB_NGINX_BASE_PATH_CONFIG="$match_config" \
SKILLHUB_WEB_BAKED_BASE_PATH_FILE="$baked_file" \
sh "$ENTRYPOINT"
grep -F 'location ^~ /skillhub/ {' "$match_config" >/dev/null

printf '%s\n' 'web-base-path-routing-test passed'
