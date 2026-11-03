#!/bin/sh

set -eu

COMMAND="up"
if [ "$#" -gt 0 ] && [ "${1#-}" = "$1" ]; then
  COMMAND="$1"
  shift
fi

SKILLHUB_REF="${SKILLHUB_REF:-main}"
SKILLHUB_HOME_DEFAULT="${TMPDIR:-/tmp}/skillhub-runtime"
SKILLHUB_HOME="${SKILLHUB_HOME:-$SKILLHUB_HOME_DEFAULT}"
SKILLHUB_VERSION_VALUE="${SKILLHUB_VERSION:-}"
SKILLHUB_PUBLIC_BASE_URL_VALUE="${SKILLHUB_PUBLIC_BASE_URL:-}"
SKILLHUB_ALIYUN_REGISTRY="${SKILLHUB_ALIYUN_REGISTRY:-crpi-ptu2rqimrigtq0qx.cn-hangzhou.personal.cr.aliyuncs.com}"
SKILLHUB_ALIYUN_NAMESPACE="${SKILLHUB_ALIYUN_NAMESPACE:-skill_hub}"
SKILLHUB_MIRROR_REGISTRY_VALUE="${SKILLHUB_MIRROR_REGISTRY:-}"
SKILLHUB_SERVER_IMAGE_VALUE="${SKILLHUB_SERVER_IMAGE:-}"
SKILLHUB_WEB_IMAGE_VALUE="${SKILLHUB_WEB_IMAGE:-}"
SKILLHUB_SCANNER_IMAGE_VALUE="${SKILLHUB_SCANNER_IMAGE:-}"
POSTGRES_IMAGE_VALUE="${POSTGRES_IMAGE:-}"
REDIS_IMAGE_VALUE="${REDIS_IMAGE:-}"
DISABLE_SCANNER=false
USE_ALIYUN=false
EXPLICIT_REF=false
WEBSITE_ID=""
ANALYTICS_UPSTREAM=""
ANALYTICS_ADAPTER="umami"
ANALYTICS_APP_ID=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || { echo "Missing value for --version" >&2; exit 1; }
      SKILLHUB_VERSION_VALUE="$2"
      shift 2
      ;;
    --aliyun)
      if [ -z "$SKILLHUB_ALIYUN_REGISTRY" ] || [ -z "$SKILLHUB_ALIYUN_NAMESPACE" ]; then
        echo "SKILLHUB_ALIYUN_REGISTRY and SKILLHUB_ALIYUN_NAMESPACE must be configured for --aliyun" >&2
        exit 1
      fi
      SKILLHUB_MIRROR_REGISTRY_VALUE="${SKILLHUB_ALIYUN_REGISTRY%/}/${SKILLHUB_ALIYUN_NAMESPACE}"
      USE_ALIYUN=true
      shift
      ;;
    --mirror-registry)
      [ "$#" -ge 2 ] || { echo "Missing value for --mirror-registry" >&2; exit 1; }
      SKILLHUB_MIRROR_REGISTRY_VALUE="$2"
      shift 2
      ;;
    --home)
      [ "$#" -ge 2 ] || { echo "Missing value for --home" >&2; exit 1; }
      SKILLHUB_HOME="$2"
      shift 2
      ;;
    --ref)
      [ "$#" -ge 2 ] || { echo "Missing value for --ref" >&2; exit 1; }
      SKILLHUB_REF="$2"
      EXPLICIT_REF=true
      shift 2
      ;;
    --server-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --server-image" >&2; exit 1; }
      SKILLHUB_SERVER_IMAGE_VALUE="$2"
      shift 2
      ;;
    --web-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --web-image" >&2; exit 1; }
      SKILLHUB_WEB_IMAGE_VALUE="$2"
      shift 2
      ;;
    --postgres-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --postgres-image" >&2; exit 1; }
      POSTGRES_IMAGE_VALUE="$2"
      shift 2
      ;;
    --redis-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --redis-image" >&2; exit 1; }
      REDIS_IMAGE_VALUE="$2"
      shift 2
      ;;
    --scanner-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --scanner-image" >&2; exit 1; }
      SKILLHUB_SCANNER_IMAGE_VALUE="$2"
      shift 2
      ;;
    --no-scanner)
      DISABLE_SCANNER=true
      shift
      ;;
    --public-url)
      [ "$#" -ge 2 ] || { echo "Missing value for --public-url" >&2; exit 1; }
      SKILLHUB_PUBLIC_BASE_URL_VALUE="$2"
      shift 2
      ;;
    --website-id)
      [ "$#" -ge 2 ] || { echo "Missing value for --website-id" >&2; exit 1; }
      WEBSITE_ID="$2"
      shift 2
      ;;
    --upstream)
      [ "$#" -ge 2 ] || { echo "Missing value for --upstream" >&2; exit 1; }
      ANALYTICS_UPSTREAM="$2"
      shift 2
      ;;
    --adapter)
      [ "$#" -ge 2 ] || { echo "Missing value for --adapter" >&2; exit 1; }
      ANALYTICS_ADAPTER="$2"
      shift 2
      ;;
    --app-id)
      [ "$#" -ge 2 ] || { echo "Missing value for --app-id" >&2; exit 1; }
      ANALYTICS_APP_ID="$2"
      shift 2
      ;;
    --help|-h)
      cat <<EOF
Usage: sh runtime.sh [up|down|clean|ps|logs|pull|analytics-up|analytics-enable|analytics-disable|analytics-down|analytics-ps|analytics-logs|analytics-pull] [options]

Analytics:
  analytics-up          Install independent Umami and PostgreSQL; then create a website
  analytics-enable      Enable Web collection with --website-id UUID [--upstream origin]
  analytics-disable     Stop Web collection without stopping either platform
  analytics-down        Stop Umami and its database without deleting volumes
  analytics-ps/logs/pull  Operate only the independent analytics project
  --adapter <name>      umami (default) or datafinder (with --app-id)
  --app-id <integer>    Existing DataFinder application ID

Options:
  --version <tag>       Use a specific image tag, for example v0.1.0
  --aliyun              Use the configured Aliyun mirror registry
  --mirror-registry <r> Use mirrored images from <registry>/<namespace>
  --home <dir>          Store runtime files in a specific directory
  --ref <git-ref>       Download runtime files from a specific Git ref
  --public-url <url>    Public access URL (e.g. https://skill.example.com)
  --server-image <img>  Override backend image repository
  --web-image <img>     Override frontend image repository
  --scanner-image <img> Override scanner image repository
  --postgres-image <i>  Override PostgreSQL image
  --redis-image <img>   Override Redis image
  --no-scanner          Do not start the skill-scanner service
EOF
      exit 0
      ;;
    *)
      echo "Unsupported argument: $1" >&2
      exit 1
      ;;
  esac
done

if [ "$USE_ALIYUN" = "true" ]; then
  SKILLHUB_RAW_BASE="${SKILLHUB_RAW_BASE:-https://imageless.oss-cn-beijing.aliyuncs.com}"
  RUNTIME_SCRIPT_URL="$SKILLHUB_RAW_BASE/runtime.sh"
  echo "Using Aliyun OSS for runtime files: $SKILLHUB_RAW_BASE"
else
  SKILLHUB_RAW_BASE="${SKILLHUB_RAW_BASE:-https://raw.githubusercontent.com/iflytek/skillhub/$SKILLHUB_REF}"
  RUNTIME_SCRIPT_URL="$SKILLHUB_RAW_BASE/scripts/runtime.sh"
  echo "Using GitHub raw for runtime files: $SKILLHUB_RAW_BASE"
fi
COMPOSE_FILE="$SKILLHUB_HOME/compose.release.yml"
OVERLAY_FILE="$SKILLHUB_HOME/compose.release.analytics.yml"
ANALYTICS_HOME="$SKILLHUB_HOME/analytics"
ANALYTICS_COMPOSE_FILE="$ANALYTICS_HOME/compose.analytics-umami.yml"
ANALYTICS_ENV_FILE="$ANALYTICS_HOME/.env"
ENV_EXAMPLE_FILE="$SKILLHUB_HOME/.env.release.example"
ENV_FILE="$SKILLHUB_HOME/.env.release"

find_compose() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
    return 0
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
    return 0
  fi

  echo "Docker Compose is required." >&2
  exit 1
}

download_file() {
  src="$1"
  dest="$2"
  tmp="$dest.tmp"
  curl -fsSL "$src" -o "$tmp"
  mv "$tmp" "$dest"
}

set_env_value() {
  key="$1"
  value="$2"
  target_env_file="${3:-$ENV_FILE}"

  if [ ! -f "$target_env_file" ]; then
    return 0
  fi

  tmp="$target_env_file.tmp"
  found=false
  old_umask="$(umask)"
  umask 077
  {
    while IFS= read -r line || [ -n "$line" ]; do
      case "$line" in
        "$key="*)
          printf '%s=%s\n' "$key" "$value"
          found=true
          ;;
        *)
          printf '%s\n' "$line"
          ;;
      esac
    done <"$target_env_file"
    if [ "$found" = "false" ]; then
      printf '%s=%s\n' "$key" "$value"
    fi
  } >"$tmp"
  umask "$old_umask"
  mv "$tmp" "$target_env_file"
  chmod 600 "$target_env_file"
}

secure_env_file() {
  if [ -f "$ENV_FILE" ]; then
    chmod 600 "$ENV_FILE"
  fi
}

get_env_value() {
  key="$1"
  default_value="${2:-}"
  value="$(grep "^$key=" "${3:-$ENV_FILE}" | tail -n 1 | cut -d= -f2- || true)"

  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
    return 0
  fi

  if [ -r /dev/urandom ] && command -v od >/dev/null 2>&1; then
    dd if=/dev/urandom bs=32 count=1 2>/dev/null | od -An -tx1 | tr -d ' \n'
    return 0
  fi

  echo "Unable to generate SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET. Install openssl or configure it manually." >&2
  exit 1
}

is_placeholder_secret() {
  case "$1" in
    ""|change-me-in-production|replace-me|replace-with-random-download-secret-32-bytes|TODO*|todo*|replace*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

ensure_anonymous_download_secret() {
  secret="$(get_env_value "SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET" "")"
  if ! is_placeholder_secret "$secret" && [ "${#secret}" -ge 32 ]; then
    return 0
  fi

  set_env_value "SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET" "$(generate_secret)"
  echo "Generated SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET in $ENV_FILE"
}

wait_for_postgres_ready() {
  postgres_user="$1"
  postgres_db="$2"
  attempt=1

  while [ "$attempt" -le 60 ]; do
    if run_compose exec -T postgres pg_isready -U "$postgres_user" -d "$postgres_db" >/dev/null 2>&1; then
      return 0
    fi

    attempt=$((attempt + 1))
    sleep 2
  done

  echo "PostgreSQL did not become ready in time." >&2
  run_compose logs postgres >&2 || true
  exit 1
}

wait_for_redis_ready() {
  attempt=1

  while [ "$attempt" -le 60 ]; do
    if run_compose exec -T redis redis-cli ping >/dev/null 2>&1; then
      return 0
    fi

    attempt=$((attempt + 1))
    sleep 2
  done

  echo "Redis did not become ready in time." >&2
  run_compose logs redis >&2 || true
  exit 1
}

ensure_postgres_password_matches_env() {
  postgres_user="$(get_env_value "POSTGRES_USER" "skillhub")"
  postgres_db="$(get_env_value "POSTGRES_DB" "skillhub")"
  postgres_password="$(get_env_value "POSTGRES_PASSWORD" "skillhub_demo")"

  if [ -z "$postgres_password" ]; then
    echo "POSTGRES_PASSWORD must not be empty." >&2
    exit 1
  fi

  wait_for_postgres_ready "$postgres_user" "$postgres_db"

  run_compose exec -T postgres \
    psql -U "$postgres_user" -d "$postgres_db" \
    -v ON_ERROR_STOP=1 \
    -v password="$postgres_password" <<'SQL' >/dev/null
SELECT format('ALTER ROLE %I WITH PASSWORD %L', current_user, :'password');
\gexec
SQL

  run_compose exec -T -e "PGPASSWORD=$postgres_password" postgres \
    psql -h 127.0.0.1 -U "$postgres_user" -d "$postgres_db" \
    -v ON_ERROR_STOP=1 \
    -c 'select current_user;' >/dev/null
}

prepare_runtime_files() {
  # Migrate legacy identity before creating a replacement release file. Failed
  # migration must not leave fresh defaults that a later retry could trust.
  if [ -f "$ANALYTICS_ENV_FILE" ]; then
    ensure_analytics_identity
  fi
  mkdir -p "$SKILLHUB_HOME"
  download_file "$SKILLHUB_RAW_BASE/compose.release.yml" "$COMPOSE_FILE"
  # GitHub and OSS both publish this root-level overlay; fetch only when used.
  if [ "$COMMAND" = "analytics-enable" ] || [ -f "$OVERLAY_FILE" ]; then
    download_file "$SKILLHUB_RAW_BASE/compose.release.analytics.yml" "$OVERLAY_FILE"
  fi
  download_file "$SKILLHUB_RAW_BASE/.env.release.example" "$ENV_EXAMPLE_FILE"

  if [ ! -f "$ENV_FILE" ]; then
    old_umask="$(umask)"
    umask 077
    cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
    umask "$old_umask"
  fi
  secure_env_file

  if [ -n "$SKILLHUB_MIRROR_REGISTRY_VALUE" ]; then
    mirror_registry="${SKILLHUB_MIRROR_REGISTRY_VALUE%/}"
    if [ -z "$POSTGRES_IMAGE_VALUE" ]; then
      POSTGRES_IMAGE_VALUE="$mirror_registry/postgres:16-alpine"
    fi
    if [ -z "$REDIS_IMAGE_VALUE" ]; then
      REDIS_IMAGE_VALUE="$mirror_registry/redis:7-alpine"
    fi
    if [ -z "$SKILLHUB_SERVER_IMAGE_VALUE" ]; then
      SKILLHUB_SERVER_IMAGE_VALUE="$mirror_registry/skillhub-server"
    fi
    if [ -z "$SKILLHUB_WEB_IMAGE_VALUE" ]; then
      SKILLHUB_WEB_IMAGE_VALUE="$mirror_registry/skillhub-web"
    fi
    if [ -z "$SKILLHUB_SCANNER_IMAGE_VALUE" ]; then
      SKILLHUB_SCANNER_IMAGE_VALUE="$mirror_registry/skillhub-scanner"
    fi
  fi

  if [ -n "$SKILLHUB_VERSION_VALUE" ]; then
    set_env_value "SKILLHUB_VERSION" "$SKILLHUB_VERSION_VALUE"
  fi

  if [ -n "$POSTGRES_IMAGE_VALUE" ]; then
    set_env_value "POSTGRES_IMAGE" "$POSTGRES_IMAGE_VALUE"
  fi

  if [ -n "$REDIS_IMAGE_VALUE" ]; then
    set_env_value "REDIS_IMAGE" "$REDIS_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_SERVER_IMAGE_VALUE" ]; then
    set_env_value "SKILLHUB_SERVER_IMAGE" "$SKILLHUB_SERVER_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_WEB_IMAGE_VALUE" ]; then
    set_env_value "SKILLHUB_WEB_IMAGE" "$SKILLHUB_WEB_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_SCANNER_IMAGE_VALUE" ]; then
    set_env_value "SKILLHUB_SCANNER_IMAGE" "$SKILLHUB_SCANNER_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_PUBLIC_BASE_URL_VALUE" ]; then
    set_env_value "SKILLHUB_PUBLIC_BASE_URL" "$SKILLHUB_PUBLIC_BASE_URL_VALUE"
  fi

  if [ "$DISABLE_SCANNER" = "true" ]; then
    set_env_value "SKILLHUB_SECURITY_SCANNER_ENABLED" "false"
  fi

  ensure_anonymous_download_secret
}

run_compose() {
  compose_cmd="$(find_compose)"
  if [ "$(get_env_value SKILLHUB_ANALYTICS_NETWORK_ENABLED false)" = "true" ]; then
    ensure_analytics_network
    set_env_value SKILLHUB_ANALYTICS_NETWORK "$SKILLHUB_ANALYTICS_NETWORK"
    # Preserve the normal default network and attach only Web to analytics.
    set -- -f "$OVERLAY_FILE" "$@"
  fi
  # shellcheck disable=SC2086
  $compose_cmd --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

ensure_analytics_identity() {
  # Once initialized, the independent directory owns both identifiers. Neither
  # a recreated release file nor inherited Compose variables may redirect it.
  for identity_key in SKILLHUB_ANALYTICS_PROJECT SKILLHUB_ANALYTICS_NETWORK; do
    identity_value="$(get_env_value "$identity_key" "" "$ANALYTICS_ENV_FILE")"
    if [ -z "$identity_value" ]; then
      if [ "$ORIGINAL_ENV_FILE_EXISTS" != "true" ]; then
        echo "Missing $identity_key in $ANALYTICS_ENV_FILE. Restore the original .env.release or set the verified original identifier in analytics/.env before retrying; no project discovery is performed." >&2
        exit 1
      fi
      identity_value="$(get_env_value "$identity_key" skillhub-analytics)"
      set_env_value "$identity_key" "$identity_value" "$ANALYTICS_ENV_FILE"
    fi
    case "$identity_key" in
      SKILLHUB_ANALYTICS_PROJECT) SKILLHUB_ANALYTICS_PROJECT="$identity_value" ;;
      SKILLHUB_ANALYTICS_NETWORK) SKILLHUB_ANALYTICS_NETWORK="$identity_value" ;;
    esac
  done
  chmod 600 "$ANALYTICS_ENV_FILE"
  # Shell variables take precedence over --env-file in Compose interpolation.
  export SKILLHUB_ANALYTICS_PROJECT SKILLHUB_ANALYTICS_NETWORK
}

ensure_analytics_network() {
  ensure_analytics_identity
  network_name="$SKILLHUB_ANALYTICS_NETWORK"
  if ! docker network inspect "$network_name" >/dev/null 2>&1; then
    docker network create "$network_name" >/dev/null
  fi
}

run_analytics_compose() {
  ensure_analytics_identity
  compose_cmd="$(find_compose)"
  # Explicit -p also protects against an inherited COMPOSE_PROJECT_NAME.
  # shellcheck disable=SC2086
  $compose_cmd -p "$SKILLHUB_ANALYTICS_PROJECT" --env-file "$ANALYTICS_ENV_FILE" -f "$ANALYTICS_COMPOSE_FILE" "$@"
}

prepare_analytics_files() {
  mkdir -p "$ANALYTICS_HOME"
  # GitHub raw and the OSS mirror both publish files at the repository root.
  download_file "$SKILLHUB_RAW_BASE/compose.analytics-umami.yml" "$ANALYTICS_COMPOSE_FILE"
  if [ ! -f "$ANALYTICS_ENV_FILE" ]; then
    old_umask="$(umask)"
    umask 077
    {
      printf 'UMAMI_DB_PASSWORD=%s\n' "$(generate_secret)"
      printf 'UMAMI_APP_SECRET=%s\n' "$(generate_secret)"
      printf 'UMAMI_ADMIN_PORT=8300\n'
      printf 'SKILLHUB_ANALYTICS_PROJECT=%s\n' "$(get_env_value SKILLHUB_ANALYTICS_PROJECT skillhub-analytics)"
      printf 'SKILLHUB_ANALYTICS_NETWORK=%s\n' "$(get_env_value SKILLHUB_ANALYTICS_NETWORK skillhub-analytics)"
    } >"$ANALYTICS_ENV_FILE"
    umask "$old_umask"
  fi
  ensure_analytics_identity
}

validate_analytics_input() {
  case "$ANALYTICS_UPSTREAM" in
    http://*|https://*) ;;
    *) echo "--upstream must be an HTTP(S) origin" >&2; exit 1 ;;
  esac
  case "${ANALYTICS_UPSTREAM#*://}" in
    ""|*[!A-Za-z0-9._:-]*|*..*) echo "--upstream must not contain paths, credentials or whitespace" >&2; exit 1 ;;
  esac
  case "$ANALYTICS_ADAPTER" in
    umami)
      if ! printf '%s' "$WEBSITE_ID" | grep -Eiq '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'; then
        echo "--website-id must be a UUID" >&2; exit 1
      fi
      ;;
    datafinder)
      case "$ANALYTICS_APP_ID" in
        ""|0*|*[!0-9]*) echo "--app-id must be a positive decimal integer" >&2; exit 1 ;;
      esac
      [ "$ANALYTICS_APP_ID" -le 2147483647 ] || { echo "--app-id is out of range" >&2; exit 1; }
      ;;
    *) echo "Unsupported analytics adapter" >&2; exit 1 ;;
  esac
}

shell_quote() {
  case "$1" in
    *[!A-Za-z0-9_./:=@%+-]*)
      printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
      ;;
    *)
      printf '%s' "$1"
      ;;
  esac
}

append_runtime_flag() {
  RUNTIME_LIFECYCLE_ARGS="$RUNTIME_LIFECYCLE_ARGS $1"
}

append_runtime_option() {
  RUNTIME_LIFECYCLE_ARGS="$RUNTIME_LIFECYCLE_ARGS $1 $(shell_quote "$2")"
}

build_runtime_lifecycle_args() {
  RUNTIME_LIFECYCLE_ARGS=""

  if [ "$USE_ALIYUN" = "true" ]; then
    append_runtime_flag "--aliyun"
  elif [ "$EXPLICIT_REF" = "true" ] || [ "$SKILLHUB_REF" != "main" ]; then
    append_runtime_option "--ref" "$SKILLHUB_REF"
  fi

  if [ -n "$SKILLHUB_VERSION_VALUE" ]; then
    append_runtime_option "--version" "$SKILLHUB_VERSION_VALUE"
  fi

  if [ "$SKILLHUB_HOME" != "$SKILLHUB_HOME_DEFAULT" ]; then
    append_runtime_option "--home" "$SKILLHUB_HOME"
  fi

  if [ -n "$SKILLHUB_PUBLIC_BASE_URL_VALUE" ]; then
    append_runtime_option "--public-url" "$SKILLHUB_PUBLIC_BASE_URL_VALUE"
  fi

  if [ -n "$SKILLHUB_MIRROR_REGISTRY_VALUE" ] && [ "$USE_ALIYUN" != "true" ]; then
    append_runtime_option "--mirror-registry" "$SKILLHUB_MIRROR_REGISTRY_VALUE"
  fi

  if [ -n "$SKILLHUB_SERVER_IMAGE_VALUE" ]; then
    append_runtime_option "--server-image" "$SKILLHUB_SERVER_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_WEB_IMAGE_VALUE" ]; then
    append_runtime_option "--web-image" "$SKILLHUB_WEB_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_SCANNER_IMAGE_VALUE" ]; then
    append_runtime_option "--scanner-image" "$SKILLHUB_SCANNER_IMAGE_VALUE"
  fi

  if [ -n "$POSTGRES_IMAGE_VALUE" ]; then
    append_runtime_option "--postgres-image" "$POSTGRES_IMAGE_VALUE"
  fi

  if [ -n "$REDIS_IMAGE_VALUE" ]; then
    append_runtime_option "--redis-image" "$REDIS_IMAGE_VALUE"
  fi

  if [ "$DISABLE_SCANNER" = "true" ]; then
    append_runtime_flag "--no-scanner"
  fi
}

print_runtime_command() {
  lifecycle_command="$1"
  printf '  curl -fsSL %s | sh -s -- %s%s\n' \
    "$RUNTIME_SCRIPT_URL" \
    "$lifecycle_command" \
    "$RUNTIME_LIFECYCLE_ARGS"
}

build_runtime_lifecycle_args

# Remember this before preparation can recreate defaults after clean.
ORIGINAL_ENV_FILE_EXISTS=false
[ ! -f "$ENV_FILE" ] || ORIGINAL_ENV_FILE_EXISTS=true
prepare_runtime_files

case "$COMMAND" in
  up)
    run_compose up -d postgres
    ensure_postgres_password_matches_env
    if [ "$DISABLE_SCANNER" = "true" ]; then
      run_compose up -d redis
      wait_for_redis_ready
      SKILLHUB_SECURITY_SCANNER_ENABLED=false run_compose up -d --no-deps --scale skill-scanner=0 server web
    else
      run_compose up -d
    fi
    PUBLIC_URL="${SKILLHUB_PUBLIC_BASE_URL_VALUE:-http://localhost}"
    cat <<EOF
SkillHub runtime started.
Web UI: $PUBLIC_URL
Backend API: http://localhost:8080
Runtime dir: $SKILLHUB_HOME
Lifecycle commands:
$(print_runtime_command up)
$(print_runtime_command down)
$(print_runtime_command ps)
$(print_runtime_command logs)
$(print_runtime_command pull)
EOF
    ;;
  down)
    run_compose down
    ;;
  clean)
    run_compose down
    if [ -d "$ANALYTICS_HOME" ]; then
      # Keep independent platform credentials and files; never strand its database.
      rm -f "$COMPOSE_FILE" "$OVERLAY_FILE" "$ENV_EXAMPLE_FILE" "$ENV_FILE"
      echo "SkillHub files removed; independent analytics files retained in $ANALYTICS_HOME"
    else
      rm -rf "$SKILLHUB_HOME"
    fi
    ;;
  ps)
    run_compose ps
    ;;
  logs)
    run_compose logs -f
    ;;
  pull)
    run_compose pull
    ;;
  analytics-up)
    prepare_analytics_files
    ensure_analytics_network
    run_analytics_compose up -d
    admin_port="$(grep '^UMAMI_ADMIN_PORT=' "$ANALYTICS_ENV_FILE" | cut -d= -f2-)"
    printf 'Umami management: http://127.0.0.1:%s\nChange the default admin/umami password, create a website, copy its UUID, then run analytics-enable --website-id <UUID>.\nAnalytics files: %s\n' "$admin_port" "$ANALYTICS_HOME"
    ;;
  analytics-enable)
    if [ -z "$ANALYTICS_UPSTREAM" ] && [ "$ANALYTICS_ADAPTER" != "umami" ]; then
      echo "--upstream is required for an existing platform" >&2
      exit 1
    fi
    if [ -z "$ANALYTICS_UPSTREAM" ]; then
      ANALYTICS_UPSTREAM=http://umami:3000
      local_analytics=true
    else
      local_analytics=false
    fi
    validate_analytics_input
    if [ "$ANALYTICS_ADAPTER" = "umami" ]; then
      analytics_config="{\"enabled\":true,\"adapter\":\"umami\",\"consent\":\"granted\",\"adapterConfig\":{\"websiteId\":\"$WEBSITE_ID\"}}"
    else
      analytics_config="{\"enabled\":true,\"adapter\":\"datafinder\",\"consent\":\"granted\",\"adapterConfig\":{\"appId\":$ANALYTICS_APP_ID}}"
    fi
    if [ "$local_analytics" = "true" ]; then
      [ -f "$ANALYTICS_ENV_FILE" ] || { echo "Run analytics-up first or provide --upstream for an existing platform" >&2; exit 1; }
      ensure_analytics_network
      set_env_value SKILLHUB_ANALYTICS_NETWORK_ENABLED true
    else
      set_env_value SKILLHUB_ANALYTICS_NETWORK_ENABLED false
    fi
    set_env_value SKILLHUB_WEB_ANALYTICS_CONFIG "'$analytics_config'"
    set_env_value SKILLHUB_WEB_ANALYTICS_UPSTREAM "$ANALYTICS_UPSTREAM"
    run_compose up -d --no-deps --force-recreate web
    echo "Web analytics enabled; refresh open browser tabs and verify events in the platform."
    ;;
  analytics-disable)
    set_env_value SKILLHUB_WEB_ANALYTICS_CONFIG ""
    set_env_value SKILLHUB_WEB_ANALYTICS_UPSTREAM ""
    set_env_value SKILLHUB_ANALYTICS_NETWORK_ENABLED false
    run_compose up -d --no-deps --force-recreate web
    echo "Web analytics disabled; platform and data remain unchanged."
    ;;
  analytics-down|analytics-ps|analytics-logs|analytics-pull)
    [ -f "$ANALYTICS_ENV_FILE" ] || { echo "No local analytics deployment; run analytics-up first" >&2; exit 1; }
    prepare_analytics_files
    case "$COMMAND" in
      analytics-down) run_analytics_compose stop ;;
      analytics-ps) run_analytics_compose ps ;;
      analytics-logs) run_analytics_compose logs -f ;;
      analytics-pull) run_analytics_compose pull ;;
    esac
    ;;
  *)
    echo "Unsupported command: $COMMAND" >&2
    echo "Usage: sh runtime.sh [up|down|clean|ps|logs|pull] [options]" >&2
    exit 1
    ;;
esac
