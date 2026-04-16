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
  echo "Using Aliyun OSS for runtime files: $SKILLHUB_RAW_BASE"
else
  SKILLHUB_RAW_BASE="${SKILLHUB_RAW_BASE:-https://raw.githubusercontent.com/iflytek/skillhub/$SKILLHUB_REF}"
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
  if grep -q "^$key=" "$ENV_FILE"; then
    sed "s|^$key=.*|$key=$value|" "$ENV_FILE" >"$tmp"
  else
    cat "$ENV_FILE" >"$tmp"
    printf '%s=%s\n' "$key" "$value" >>"$tmp"
  fi
  mv "$tmp" "$ENV_FILE"
}

prepare_runtime_files() {
  mkdir -p "$SKILLHUB_HOME"
  download_file "$SKILLHUB_RAW_BASE/compose.release.yml" "$COMPOSE_FILE"
  download_file "$SKILLHUB_RAW_BASE/.env.release.example" "$ENV_EXAMPLE_FILE"

  if [ ! -f "$ENV_FILE" ]; then
    cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
  fi

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
}

run_compose() {
  compose_cmd="$(find_compose)"
  # shellcheck disable=SC2086
  $compose_cmd --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

prepare_runtime_files

case "$COMMAND" in
  up)
    if [ "$DISABLE_SCANNER" = "true" ]; then
      SKILLHUB_SECURITY_SCANNER_ENABLED=false run_compose up -d --scale skill-scanner=0
    else
      run_compose up -d
    fi
    PUBLIC_URL="${SKILLHUB_PUBLIC_BASE_URL_VALUE:-http://localhost}"
    HOME_ARG=""
    if [ "$SKILLHUB_HOME" != "$SKILLHUB_HOME_DEFAULT" ]; then
      HOME_ARG=" --home $SKILLHUB_HOME"
    fi
    cat <<EOF
SkillHub runtime started.
Web UI: $PUBLIC_URL
Backend API: http://localhost:8080
Runtime dir: $SKILLHUB_HOME
Stop with:
  curl -fsSL $SKILLHUB_RAW_BASE/scripts/runtime.sh | sh -s -- down$HOME_ARG
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
