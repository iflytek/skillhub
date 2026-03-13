#!/bin/sh
set -eu

: "${SKILLHUB_WEB_API_BASE_URL:=}"
: "${SKILLHUB_PUBLIC_BASE_URL:=}"

envsubst '${SKILLHUB_WEB_API_BASE_URL} ${SKILLHUB_PUBLIC_BASE_URL}' \
  < /usr/share/nginx/html/runtime-config.js.template \
  > /usr/share/nginx/html/runtime-config.js
