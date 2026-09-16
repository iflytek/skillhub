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

reject_patterns() {
  var_name="$1"
  shift
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    return 0
  fi
  for pattern in "$@"; do
    case "$var_value" in
      $pattern)
        error "$var_name still uses placeholder/default pattern: $var_value"
        return 0
        ;;
    esac
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
  # A query or fragment corrupts these base URLs: they are string-concatenated
  # with paths (e.g. ${SKILLHUB_PUBLIC_BASE_URL}/cli/auth), so a trailing
  # ?query/#fragment would swallow the appended path.
  case "$var_value" in
    *'?'*|*'#'*) error "$var_name must not contain a query ('?') or fragment ('#')" ;;
  esac
  # Reject a scheme with no host (e.g. https://), which would concatenate into an
  # invalid URL such as https:///cli/auth.
  case "$var_value" in
    http://*|https://*)
      rest=${var_value#*://}
      host=${rest%%[/?#]*}
      if [ -z "$host" ]; then
        error "$var_name must include a host (e.g. https://skills.example.com): $var_value"
      fi
      ;;
  esac
}

validate_web_api_base_url() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    return 0
  fi
  case "$var_value" in
    http://*|https://*) ;;
    //*) error "$var_name must be an absolute http(s) URL or a root-relative path" ;;
    /*) ;;
    *) error "$var_name must be an absolute http(s) URL or a root-relative path" ;;
  esac
}

validate_no_trailing_slash() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  case "$var_value" in
    */) error "$var_name must not have a trailing slash" ;;
  esac
}

validate_web_base_path_format() {
  # Mirror the runtime check in web/docker-entrypoint.d/20-base-path.sh so invalid
  # values are rejected here instead of only failing at container start.
  value="${SKILLHUB_WEB_BASE_PATH:-}"
  [ -z "$value" ] && return 0
  case "$value" in
    /|/*/) ;;
    *) error "SKILLHUB_WEB_BASE_PATH must be '/' or start and end with '/': $value"; return ;;
  esac
  case "$value" in
    *//*|*[!A-Za-z0-9._~/-]*) error "SKILLHUB_WEB_BASE_PATH contains unsupported characters: $value"; return ;;
  esac
  case "$value" in
    */./*|*/../*) error "SKILLHUB_WEB_BASE_PATH must not contain '.' or '..' path segments: $value" ;;
  esac
  if [ "$value" != / ]; then
    first_segment=${value#/}
    first_segment=${first_segment%%/*}
    case "$first_segment" in
      api|oauth2|login|assets|registry|nginx-health|.well-known|runtime-config.js)
        error "SKILLHUB_WEB_BASE_PATH must not start with a segment reserved by the SkillHub server ($first_segment); it would shadow the server's own Nginx location: $value"
        ;;
    esac
  fi
}

validate_api_base_path_alignment() {
  web_base_path="${SKILLHUB_WEB_BASE_PATH:-/}"
  api_base="${SKILLHUB_WEB_API_BASE_URL:-}"
  if [ "$web_base_path" = / ] || [ -z "$api_base" ]; then
    return 0
  fi
  # An absolute API URL points at a separate host and is allowed to differ.
  case "$api_base" in
    http://* | https://*) return 0 ;;
  esac
  expected="${web_base_path%/}"
  if [ "$api_base" != "$expected" ]; then
    error "SKILLHUB_WEB_API_BASE_URL ($api_base) must equal SKILLHUB_WEB_BASE_PATH without its trailing slash ($expected) for same-origin sub-path routing, or be an absolute URL for a separate API host"
  fi
}

validate_public_base_path_alignment() {
  web_base_path="${SKILLHUB_WEB_BASE_PATH:-/}"
  public_base_url="${SKILLHUB_PUBLIC_BASE_URL:-}"
  if [ "$web_base_path" = / ] || [ -z "$public_base_url" ]; then
    return 0
  fi

  # Compare the URL path component exactly, not just the suffix: https://host/other/skillhub
  # ends with /skillhub but serves the app at /other/skillhub, which would not match.
  rest="${public_base_url#*://}"
  case "$rest" in
    */*) public_path="/${rest#*/}" ;;
    *) public_path="" ;;
  esac
  public_path="${public_path%/}"
  required="${web_base_path%/}"
  if [ "$public_path" != "$required" ]; then
    error "SKILLHUB_PUBLIC_BASE_URL path ($public_path) must equal SKILLHUB_WEB_BASE_PATH without its trailing slash ($required)"
  fi
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

validate_min_length() {
  var_name="$1"
  min_length="$2"
  eval "var_value=\${$var_name:-}"
  if [ -z "$var_value" ]; then
    return 0
  fi
  if [ "${#var_value}" -lt "$min_length" ]; then
    error "$var_name must be at least $min_length characters"
  fi
}

validate_non_negative_integer() {
  var_name="$1"
  eval "var_value=\${$var_name:-}"
  case "$var_value" in
    "") ;;
    *[!0-9]*) error "$var_name must be a non-negative integer" ;;
  esac
}

validate_redis_nodes() {
  var_name="$1"
  eval "nodes=\${$var_name:-}"
  if [ -z "$nodes" ]; then
    return 0
  fi

  old_ifs="$IFS"
  IFS=","
  for node in $nodes; do
    host=${node%:*}
    port=${node##*:}
    if [ -z "$host" ] || [ "$host" = "$node" ]; then
      error "$var_name entries must use host:port"
      continue
    fi
    case "$host" in
      *[!A-Za-z0-9._-]*)
        error "$var_name contains an invalid host: $host"
        ;;
    esac
    case "$port" in
      *[!0-9]*|"")
        error "$var_name contains an invalid port: $node"
        ;;
      *)
        if [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
          error "$var_name port must be between 1 and 65535: $node"
        fi
        ;;
    esac
  done
  IFS="$old_ifs"
}

validate_redis_sentinel_configuration() {
  master="${SPRING_DATA_REDIS_SENTINEL_MASTER:-}"
  nodes="${SPRING_DATA_REDIS_SENTINEL_NODES:-}"

  if [ -n "$master" ] && [ -z "$nodes" ]; then
    error "SPRING_DATA_REDIS_SENTINEL_NODES is required when SPRING_DATA_REDIS_SENTINEL_MASTER is set"
  fi
  if [ -n "$nodes" ] && [ -z "$master" ]; then
    error "SPRING_DATA_REDIS_SENTINEL_MASTER is required when SPRING_DATA_REDIS_SENTINEL_NODES is set"
  fi
  validate_redis_nodes SPRING_DATA_REDIS_SENTINEL_NODES
}

validate_redis_cluster_database() {
  if [ -z "${SPRING_DATA_REDIS_CLUSTER_NODES:-}" ]; then
    return 0
  fi

  case "${SPRING_DATA_REDIS_DATABASE:-0}" in
    0) ;;
    *) error "SPRING_DATA_REDIS_DATABASE must be 0 when SPRING_DATA_REDIS_CLUSTER_NODES is set" ;;
  esac
}

require_non_empty SKILLHUB_PUBLIC_BASE_URL
validate_url SKILLHUB_PUBLIC_BASE_URL
validate_no_trailing_slash SKILLHUB_PUBLIC_BASE_URL

require_non_empty SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET
reject_values SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET "change-me-in-production" "replace-me" "replace-with-random-download-secret-32-bytes"
reject_patterns SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET "TODO_*" "todo_*" "replace*"
validate_min_length SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET 32

reject_values POSTGRES_PASSWORD "change-this-postgres-password" "skillhub_demo" "skillhub_dev"
reject_patterns POSTGRES_PASSWORD "TODO_*" "todo_*"
reject_values BOOTSTRAP_ADMIN_PASSWORD "replace-this-admin-password" "ChangeMe!2026" "Admin@2026"
reject_patterns BOOTSTRAP_ADMIN_PASSWORD "TODO_*" "todo_*" "replace*"
if [ "${BOOTSTRAP_ADMIN_ENABLED:-false}" = "true" ]; then
  require_non_empty BOOTSTRAP_ADMIN_PASSWORD
fi
reject_values SKILLHUB_STORAGE_S3_ACCESS_KEY "replace-me"
reject_values SKILLHUB_STORAGE_S3_SECRET_KEY "replace-me"
reject_patterns SKILLHUB_STORAGE_S3_ACCESS_KEY "TODO_*" "todo_*" "replace*"
reject_patterns SKILLHUB_STORAGE_S3_SECRET_KEY "TODO_*" "todo_*" "replace*"
reject_patterns SPRING_MAIL_USERNAME "TODO_*" "todo_*" "replace*"
reject_patterns SPRING_MAIL_PASSWORD "TODO_*" "todo_*" "replace*"

validate_boolean SESSION_COOKIE_SECURE
validate_boolean BOOTSTRAP_ADMIN_ENABLED
validate_boolean SKILLHUB_TRUST_FORWARDED_PROTO
validate_boolean SKILLHUB_BUILTIN_SKILLS_ENABLED
validate_boolean SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE
validate_boolean SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET
validate_boolean SPRING_DATA_REDIS_SSL_ENABLED
validate_boolean SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST

validate_port POSTGRES_PORT
validate_port REDIS_PORT
validate_port API_PORT
validate_port WEB_PORT
validate_non_negative_integer SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS
validate_redis_nodes SPRING_DATA_REDIS_CLUSTER_NODES
validate_redis_cluster_database
validate_redis_sentinel_configuration

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
  validate_web_api_base_url SKILLHUB_WEB_API_BASE_URL
  validate_no_trailing_slash SKILLHUB_WEB_API_BASE_URL
fi

validate_web_base_path_format
validate_public_base_path_alignment
validate_api_base_path_alignment

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
