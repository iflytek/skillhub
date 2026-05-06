#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env.release}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

get_value() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  if [ -z "$line" ]; then
    return 1
  fi
  printf '%s' "${line#*=}"
}

public_base_url="$(get_value SKILLHUB_PUBLIC_BASE_URL || true)"
bootstrap_enabled="$(get_value BOOTSTRAP_ADMIN_ENABLED || true)"
bootstrap_password="$(get_value BOOTSTRAP_ADMIN_PASSWORD || true)"
mysql_password="$(get_value MYSQL_PASSWORD || true)"
mysql_root_password="$(get_value MYSQL_ROOT_PASSWORD || true)"

if [ -z "${public_base_url}" ]; then
  echo "SKILLHUB_PUBLIC_BASE_URL must be set in $ENV_FILE" >&2
  exit 1
fi

if [ "$bootstrap_enabled" = "true" ] && [ "${bootstrap_password}" = "ChangeMe!2026" ]; then
  echo "BOOTSTRAP_ADMIN_PASSWORD must not use the default ChangeMe!2026 when bootstrap admin is enabled" >&2
  exit 1
fi

if [ "${mysql_password}" = "skillhub_demo" ] || [ "${mysql_password}" = "change-me" ]; then
  echo "MYSQL_PASSWORD must be replaced with a non-placeholder value" >&2
  exit 1
fi

if [ "${mysql_root_password}" = "skillhub_root_demo" ] || [ "${mysql_root_password}" = "change-me-root" ]; then
  echo "MYSQL_ROOT_PASSWORD must be replaced with a non-placeholder value" >&2
  exit 1
fi

echo "Release config looks valid: $ENV_FILE"
