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
    --help|-h)
      cat <<EOF
Usage: sh runtime.sh [up|down|clean|ps|logs|pull] [options]

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

  if [ ! -f "$ENV_FILE" ]; then
    return 0
  fi

  tmp="$ENV_FILE.tmp"
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
    done <"$ENV_FILE"
    if [ "$found" = "false" ]; then
      printf '%s=%s\n' "$key" "$value"
    fi
  } >"$tmp"
  umask "$old_umask"
  mv "$tmp" "$ENV_FILE"
  secure_env_file
}

secure_env_file() {
  if [ -f "$ENV_FILE" ]; then
    chmod 600 "$ENV_FILE"
  fi
}

get_env_value() {
  key="$1"
  default_value="${2:-}"
  value="$(grep "^$key=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)"

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
  mkdir -p "$SKILLHUB_HOME"
  download_file "$SKILLHUB_RAW_BASE/compose.release.yml" "$COMPOSE_FILE"
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
  # shellcheck disable=SC2086
  $compose_cmd --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
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
    rm -rf "$SKILLHUB_HOME"
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
  *)
    echo "Unsupported command: $COMMAND" >&2
    echo "Usage: sh runtime.sh [up|down|clean|ps|logs|pull] [options]" >&2
    exit 1
    ;;
esac
