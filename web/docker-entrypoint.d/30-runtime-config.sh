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

# Generate runtime-config.js
envsubst '${SKILLHUB_WEB_API_BASE_URL} ${SKILLHUB_PUBLIC_BASE_URL} ${SKILLHUB_WEB_AUTH_DIRECT_ENABLED} ${SKILLHUB_WEB_AUTH_DIRECT_PROVIDER} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER} ${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO}' \
  < /usr/share/nginx/html/runtime-config.js.template \
  > /usr/share/nginx/html/runtime-config.js

# Generate registry/skill.md with actual public URL
envsubst '${SKILLHUB_PUBLIC_BASE_URL}' \
  < /usr/share/nginx/html/registry/skill.md.template \
  > /usr/share/nginx/html/registry/skill.md
