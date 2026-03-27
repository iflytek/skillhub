#!/bin/sh
set -eu

ENV_FILE="${1:-.env.release}"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: env file not found: $ENV_FILE" >&2
  exit 1
fi

while IFS= read -r raw_line || [ -n "$raw_line" ]; do
  line=$(printf '%s' "$raw_line" | tr -d '\r')
  case "$line" in
    ""|\#*) continue ;;
  esac
  export "$line"
done < "$ENV_FILE"

errors=0
warnings=0

error() {
  errors=$((errors + 1))
  echo "ERROR: $*" >&2
}

warn() {
  warnings=$((warnings + 1))
  echo "WARN: $*" >&2
}

require_non_empty() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    error "$var_name is required"
  fi
}

reject_values() {
  var_name="$1"
  shift
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    return 0
  fi
  for bad in "$@"; do
    if [ "$var_value" = "$bad" ]; then
      error "$var_name still uses placeholder/default value: $bad"
      return 0
    fi
  done
}

validate_url() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    return 0
  fi
  case "$var_value" in
    http://*|https://*) ;;
    *) error "$var_name must start with http:// or https://" ;;
  esac
}

validate_no_trailing_slash() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  case "$var_value" in
    */) error "$var_name must not have a trailing slash" ;;
  esac
}

validate_boolean() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  case "$var_value" in
    ""|true|false) ;;
    *) error "$var_name must be true or false" ;;
  esac
}

validate_port() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    return 0
  fi
  case "$var_value" in
    *[!0-9]*|"") error "$var_name must be numeric" ;;
    *)
      if [ "$var_value" -lt 1 ] || [ "$var_value" -gt 65535 ]; then
        error "$var_name must be between 1 and 65535"
      fi
      ;;
  esac
}

require_non_empty SKILLHUB_PUBLIC_BASE_URL
validate_url SKILLHUB_PUBLIC_BASE_URL
validate_no_trailing_slash SKILLHUB_PUBLIC_BASE_URL

reject_values POSTGRES_PASSWORD "change-this-postgres-password" "skillhub_demo" "skillhub_dev"
reject_values BOOTSTRAP_ADMIN_PASSWORD "replace-this-admin-password" "ChangeMe!2026" "Admin@2026"
if [ "${BOOTSTRAP_ADMIN_ENABLED:-false}" = "true" ]; then
  require_non_empty BOOTSTRAP_ADMIN_PASSWORD
fi
reject_values SKILLHUB_STORAGE_S3_ACCESS_KEY "replace-me"
reject_values SKILLHUB_STORAGE_S3_SECRET_KEY "replace-me"

validate_boolean SESSION_COOKIE_SECURE
validate_boolean BOOTSTRAP_ADMIN_ENABLED
validate_boolean SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE
validate_boolean SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET

validate_port POSTGRES_PORT
validate_port REDIS_PORT
validate_port API_PORT
validate_port WEB_PORT

require_non_empty POSTGRES_DB
require_non_empty POSTGRES_USER
require_non_empty POSTGRES_PASSWORD

storage_provider="${SKILLHUB_STORAGE_PROVIDER:-}"
case "$storage_provider" in
  s3)
    require_non_empty SKILLHUB_STORAGE_S3_ENDPOINT
    require_non_empty SKILLHUB_STORAGE_S3_BUCKET
    require_non_empty SKILLHUB_STORAGE_S3_ACCESS_KEY
    require_non_empty SKILLHUB_STORAGE_S3_SECRET_KEY
    require_non_empty SKILLHUB_STORAGE_S3_REGION
    validate_url SKILLHUB_STORAGE_S3_ENDPOINT
    validate_url SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT
    ;;
  local)
    warn "SKILLHUB_STORAGE_PROVIDER=local is only suitable for non-production or temporary validation"
    ;;
  "")
    error "SKILLHUB_STORAGE_PROVIDER is required"
    ;;
  *)
    error "SKILLHUB_STORAGE_PROVIDER must be either local or s3"
    ;;
esac

if [ -n "${SKILLHUB_WEB_API_BASE_URL:-}" ]; then
  validate_url SKILLHUB_WEB_API_BASE_URL
  validate_no_trailing_slash SKILLHUB_WEB_API_BASE_URL
fi

if [ -n "${DEVICE_AUTH_VERIFICATION_URI:-}" ]; then
  validate_url DEVICE_AUTH_VERIFICATION_URI
fi

if [ "${SESSION_COOKIE_SECURE:-true}" != "true" ]; then
  warn "SESSION_COOKIE_SECURE is not true; only acceptable behind plain HTTP during temporary local verification"
fi

if [ "${POSTGRES_BIND_ADDRESS:-127.0.0.1}" != "127.0.0.1" ]; then
  warn "POSTGRES_BIND_ADDRESS is not 127.0.0.1; confirm database exposure is intended"
fi

if [ "${REDIS_BIND_ADDRESS:-127.0.0.1}" != "127.0.0.1" ]; then
  warn "REDIS_BIND_ADDRESS is not 127.0.0.1; confirm Redis exposure is intended"
fi

oauth_id="${OAUTH2_GITHUB_CLIENT_ID:-}"
oauth_secret="${OAUTH2_GITHUB_CLIENT_SECRET:-}"
if [ -n "$oauth_id" ] && [ -z "$oauth_secret" ]; then
  error "OAUTH2_GITHUB_CLIENT_SECRET is required when OAUTH2_GITHUB_CLIENT_ID is set"
fi
if [ -n "$oauth_secret" ] && [ -z "$oauth_id" ]; then
  error "OAUTH2_GITHUB_CLIENT_ID is required when OAUTH2_GITHUB_CLIENT_SECRET is set"
fi

if [ "$errors" -gt 0 ]; then
  echo "Release config validation failed: $errors error(s), $warnings warning(s)." >&2
  exit 1
fi

echo "Release config validation passed with $warnings warning(s)."
