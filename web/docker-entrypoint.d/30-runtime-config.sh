#!/bin/sh
set -eu

: "${SKILLHUB_WEB_API_BASE_URL:=}"
: "${SKILLHUB_PUBLIC_BASE_URL:=}"
: "${SKILLHUB_WEB_AUTH_DIRECT_ENABLED:=false}"
: "${SKILLHUB_WEB_AUTH_DIRECT_PROVIDER:=}"

# Session-bootstrap variables are defaulted here so envsubst writes
# `authSessionBootstrapEnabled: "false"` into runtime-config.js instead of leaving
# the literal `${...}` placeholder. They are intentionally NOT exposed in
# compose.release.yml or .env.release.example: the matching server-side switch
# does not exist yet, so surfacing the toggle would let the frontend hit
# /api/v1/auth/session/bootstrap and receive 403. See PR #280 discussion.
: "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED:=false}"
: "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER:=}"
: "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO:=false}"
# NB: `${VAR:=default}` only sets a shell variable, not an exported one, so
# envsubst would still substitute an empty string. Assign and export explicitly.
SKILLHUB_WEB_REGISTRATION_ENABLED="${SKILLHUB_WEB_REGISTRATION_ENABLED:-true}"
export SKILLHUB_WEB_REGISTRATION_ENABLED

# Generate runtime-config.js
envsubst '${SKILLHUB_WEB_API_BASE_URL} ${SKILLHUB_PUBLIC_BASE_URL} ${SKILLHUB_WEB_AUTH_DIRECT_ENABLED} ${SKILLHUB_WEB_AUTH_DIRECT_PROVIDER} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO} ${SKILLHUB_WEB_REGISTRATION_ENABLED}' \
  < /usr/share/nginx/html/runtime-config.js.template \
  > /usr/share/nginx/html/runtime-config.js

# Generate both the preferred install guide and the compatibility route from
# one template so self-hosted deployments keep their own registry URL.
mkdir -p /usr/share/nginx/html/install
guide_public_base_url="$SKILLHUB_PUBLIC_BASE_URL"
guide_url_config="${SKILLHUB_NGINX_GUIDE_URL_CONFIG:-/etc/nginx/skillhub-guide-public-url.conf}"
if [ -z "$guide_public_base_url" ]; then
  # Nginx replaces this marker from the sanitized request scheme, a strictly
  # allowlisted Host, and the configured base path. A Host outside this safe
  # URL grammar must never reach copied shell commands in the guide.
  guide_public_base_url='__SKILLHUB_PUBLIC_BASE_URL__'
  printf '%s\n' \
    'if ($http_host !~ "^(?:[A-Za-z0-9.-]+|\\[[0-9A-Fa-f:.]+\\])(?::[0-9]{1,5})?$") { return 400; }' \
    > "$guide_url_config"
else
  printf '%s\n' '# Explicit public URL: request Host is not used in the guide.' > "$guide_url_config"
fi
SKILLHUB_PUBLIC_BASE_URL="$guide_public_base_url" envsubst '${SKILLHUB_PUBLIC_BASE_URL}' \
  < /usr/share/nginx/html/registry/skill.md.template \
  > /usr/share/nginx/html/registry/skill.md
cp /usr/share/nginx/html/registry/skill.md /usr/share/nginx/html/install/skillhub.md
