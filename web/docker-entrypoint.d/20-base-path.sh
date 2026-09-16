#!/bin/sh
set -eu

# Substitute the build-time placeholder base with SKILLHUB_WEB_BASE_PATH
# (must start and end with '/'; defaults to '/'), so one image can serve
# any sub-path via env. It also configures Nginx to strip that prefix before
# dispatching to the existing static-file and API locations.
#
# When the image was built with a fixed base (--build-arg VITE_BASE_PATH=/foo/),
# that value is recorded at build time and used as the default here, so the
# baked assets and the generated Nginx routing stay in sync without requiring
# the operator to repeat SKILLHUB_WEB_BASE_PATH at runtime.
baked_base_path_file="${SKILLHUB_WEB_BAKED_BASE_PATH_FILE:-/etc/skillhub/baked-base-path}"
baked_base_path=""
if [ -f "$baked_base_path_file" ]; then
  baked_base_path=$(cat "$baked_base_path_file")
fi

if [ -z "${SKILLHUB_WEB_BASE_PATH:-}" ]; then
  # No runtime override (unset or empty): fall back to the fixed base baked at
  # build time, if any. This keeps the baked assets and generated routing in sync.
  SKILLHUB_WEB_BASE_PATH="$baked_base_path"
elif [ -n "$baked_base_path" ] && [ "$SKILLHUB_WEB_BASE_PATH" != "$baked_base_path" ]; then
  # A fixed-base image must not be served under a different runtime prefix: the
  # baked asset URLs would not match the generated Nginx routing. Fail loudly
  # instead of silently serving broken static assets.
  echo "SKILLHUB_WEB_BASE_PATH ($SKILLHUB_WEB_BASE_PATH) conflicts with the base path baked into this image ($baked_base_path); rebuild with a matching VITE_BASE_PATH or leave SKILLHUB_WEB_BASE_PATH unset." >&2
  exit 1
fi
: "${SKILLHUB_WEB_BASE_PATH:=/}"

case "$SKILLHUB_WEB_BASE_PATH" in
  /|/*/) ;;
  *)
    echo "SKILLHUB_WEB_BASE_PATH must be '/' or start and end with '/': $SKILLHUB_WEB_BASE_PATH" >&2
    exit 1
    ;;
esac

case "$SKILLHUB_WEB_BASE_PATH" in
  *//*|*[!A-Za-z0-9._~/-]*)
    echo "SKILLHUB_WEB_BASE_PATH contains unsupported characters: $SKILLHUB_WEB_BASE_PATH" >&2
    exit 1
    ;;
esac

# Reject '.'/'..' path segments: browsers and Nginx normalize them, so the baked
# asset URLs and the generated location would diverge. Mirrors the build-time
# check in web/base-path-config.ts.
case "$SKILLHUB_WEB_BASE_PATH" in
  */./*|*/../*)
    echo "SKILLHUB_WEB_BASE_PATH must not contain '.' or '..' path segments: $SKILLHUB_WEB_BASE_PATH" >&2
    exit 1
    ;;
esac

# Reject base paths whose first segment is reserved by the server's own Nginx
# locations (/api/, /oauth2/, /login/, /assets/, /registry/, /nginx-health,
# /.well-known/, /runtime-config.js). Generating `location ^~ /api/` would
# shadow the real API route and take down the whole app. Kept in sync with
# web/base-path-config.ts, validate-release-config.sh and the Helm checks.
if [ "$SKILLHUB_WEB_BASE_PATH" != / ]; then
  first_segment=${SKILLHUB_WEB_BASE_PATH#/}
  first_segment=${first_segment%%/*}
  case "$first_segment" in
    api|oauth2|login|assets|registry|nginx-health|.well-known|runtime-config.js)
      echo "SKILLHUB_WEB_BASE_PATH must not start with a segment reserved by the SkillHub server ($first_segment); it would shadow the server's own Nginx location: $SKILLHUB_WEB_BASE_PATH" >&2
      exit 1
      ;;
  esac
fi

# A same-origin API base must match the base path, otherwise the front end requests
# /<other>/api/... which the sub-path routing cannot reach. Mirrors validate-release-config.sh.
# Absolute URLs (separate API host) are allowed to differ.
if [ "$SKILLHUB_WEB_BASE_PATH" != / ] && [ -n "${SKILLHUB_WEB_API_BASE_URL:-}" ]; then
  case "$SKILLHUB_WEB_API_BASE_URL" in
    http://* | https://*) ;;
    *)
      if [ "$SKILLHUB_WEB_API_BASE_URL" != "${SKILLHUB_WEB_BASE_PATH%/}" ]; then
        echo "SKILLHUB_WEB_API_BASE_URL ($SKILLHUB_WEB_API_BASE_URL) must equal SKILLHUB_WEB_BASE_PATH without its trailing slash (${SKILLHUB_WEB_BASE_PATH%/}) for same-origin sub-path routing, or be an absolute URL for a separate API host" >&2
        exit 1
      fi
      ;;
  esac
fi

placeholder="/__SKILLHUB_WEB_BASE_PATH__/"
root="${SKILLHUB_WEB_ROOT:-/usr/share/nginx/html}"
routing_config="${SKILLHUB_NGINX_BASE_PATH_CONFIG:-/etc/nginx/skillhub-base-path.conf}"

if [ "$SKILLHUB_WEB_BASE_PATH" = / ]; then
  printf '%s\n' \
    '# No sub-path routing is required for root deployment.' \
    'set $skillhub_forwarded_prefix "";' \
    >"$routing_config"
else
  base_path_without_trailing_slash=${SKILLHUB_WEB_BASE_PATH%/}
  printf 'set $skillhub_forwarded_prefix %s;\n\nlocation = %s {\n    absolute_redirect off;\n    return 301 %s/;\n}\n\nlocation ^~ %s {\n    rewrite ^%s(.*)$ /$1 last;\n}\n' \
    "$base_path_without_trailing_slash" \
    "$base_path_without_trailing_slash" \
    "$base_path_without_trailing_slash" \
    "$SKILLHUB_WEB_BASE_PATH" \
    "$SKILLHUB_WEB_BASE_PATH" \
    >"$routing_config"
fi

if ! grep -rlq "$placeholder" "$root" 2>/dev/null; then
  exit 0
fi

escaped=$(printf '%s' "$SKILLHUB_WEB_BASE_PATH" | sed 's/[&/\]/\\&/g')

backup_suffix='.skillhub-base-path-backup'
find "$root" -type f \( -name '*.html' -o -name '*.js' -o -name '*.css' \) \
  -exec sed -i"$backup_suffix" "s#${placeholder}#${escaped}#g" {} +
find "$root" -type f -name "*${backup_suffix}" -delete
