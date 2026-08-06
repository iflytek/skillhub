#!/bin/sh
set -eu

: "${SKILLHUB_WEB_API_BASE_URL:=}"
: "${SKILLHUB_PUBLIC_BASE_URL:=}"
: "${SKILLHUB_WEB_AUTH_DIRECT_ENABLED:=false}"
: "${SKILLHUB_WEB_AUTH_DIRECT_PROVIDER:=}"

baked_api_base_url_file="${SKILLHUB_WEB_BAKED_API_BASE_URL_FILE:-/etc/skillhub/baked-api-base-url}"
baked_public_base_url_file="${SKILLHUB_WEB_BAKED_PUBLIC_BASE_URL_FILE:-/etc/skillhub/baked-public-base-url}"

if [ -z "$SKILLHUB_WEB_API_BASE_URL" ] && [ -f "$baked_api_base_url_file" ]; then
  SKILLHUB_WEB_API_BASE_URL=$(cat "$baked_api_base_url_file")
fi

if [ -z "$SKILLHUB_PUBLIC_BASE_URL" ] && [ -f "$baked_public_base_url_file" ]; then
  SKILLHUB_PUBLIC_BASE_URL=$(cat "$baked_public_base_url_file")
fi

# Session-bootstrap variables are defaulted here so envsubst writes
# `authSessionBootstrapEnabled: "false"` into runtime-config.js instead of leaving
# the literal `${...}` placeholder. They are intentionally NOT exposed in
# compose.release.yml or .env.release.example: the matching server-side switch
# does not exist yet, so surfacing the toggle would let the frontend hit
# /api/v1/auth/session/bootstrap and receive 403. See PR #280 discussion.
: "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED:=false}"
: "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER:=}"
: "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO:=false}"

# Generate runtime-config.js
envsubst '${SKILLHUB_WEB_API_BASE_URL} ${SKILLHUB_PUBLIC_BASE_URL} ${SKILLHUB_WEB_AUTH_DIRECT_ENABLED} ${SKILLHUB_WEB_AUTH_DIRECT_PROVIDER} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO}' \
  < /usr/share/nginx/html/runtime-config.js.template \
  > /usr/share/nginx/html/runtime-config.js

# Generate registry/skill.md with actual public URL
envsubst '${SKILLHUB_PUBLIC_BASE_URL}' \
  < /usr/share/nginx/html/registry/skill.md.template \
  > /usr/share/nginx/html/registry/skill.md
