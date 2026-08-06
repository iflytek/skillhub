#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: /usr/local/bin/skillhub-test-deploy [options]

Options:
  --deploy-tag <tag>       Floating image tag to deploy
  --immutable-tag <tag>    Immutable image tag for traceability
  --merged-sha <sha>       Synthetic merge commit SHA
  --pr-csv <list>          Comma-separated PR numbers
  --run-url <url>          GitHub Actions run URL
  --public-url <url>       Public URL configured on the remote runtime
  --web-base-path <path>   Optional Web UI base path, for example /skillhub/
EOF
}

runtime_dir="/opt/skillhub-runtime"
deploy_tag=""
immutable_tag=""
merged_sha=""
pr_csv=""
run_url=""
public_url=""
web_base_path=""

if [[ $# -ge 7 && "${1:-}" != --* ]]; then
  set -- \
    --deploy-tag "$1" \
    --immutable-tag "$2" \
    --merged-sha "$3" \
    --pr-csv "$4" \
    --run-url "$5" \
    --public-url "$6" \
    --web-base-path "$7" \
    "${@:8}"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-tag)
      [[ $# -ge 2 ]] || { echo "Missing value for --deploy-tag" >&2; exit 1; }
      deploy_tag="$2"
      shift 2
      ;;
    --immutable-tag)
      [[ $# -ge 2 ]] || { echo "Missing value for --immutable-tag" >&2; exit 1; }
      immutable_tag="$2"
      shift 2
      ;;
    --merged-sha)
      [[ $# -ge 2 ]] || { echo "Missing value for --merged-sha" >&2; exit 1; }
      merged_sha="$2"
      shift 2
      ;;
    --pr-csv)
      [[ $# -ge 2 ]] || { echo "Missing value for --pr-csv" >&2; exit 1; }
      pr_csv="$2"
      shift 2
      ;;
    --run-url)
      [[ $# -ge 2 ]] || { echo "Missing value for --run-url" >&2; exit 1; }
      run_url="$2"
      shift 2
      ;;
    --public-url)
      [[ $# -ge 2 ]] || { echo "Missing value for --public-url" >&2; exit 1; }
      public_url="$2"
      shift 2
      ;;
    --web-base-path)
      [[ $# -ge 2 ]] || { echo "Missing value for --web-base-path" >&2; exit 1; }
      web_base_path="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unsupported argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

[[ -n "${deploy_tag}" ]] || { echo "--deploy-tag is required" >&2; exit 1; }
[[ -n "${immutable_tag}" ]] || { echo "--immutable-tag is required" >&2; exit 1; }

if [[ ! "${deploy_tag}" =~ ^[a-z0-9._-]+$ ]]; then
  echo "Invalid deploy tag: ${deploy_tag}" >&2
  exit 1
fi

if [[ ! "${immutable_tag}" =~ ^[a-z0-9._-]+$ ]]; then
  echo "Invalid immutable tag: ${immutable_tag}" >&2
  exit 1
fi

if [[ -n "${merged_sha}" && ! "${merged_sha}" =~ ^[0-9a-f]{7,64}$ ]]; then
  echo "Invalid merged SHA: ${merged_sha}" >&2
  exit 1
fi

if [[ -n "${pr_csv}" && ! "${pr_csv}" =~ ^[0-9]+(,[0-9]+)*$ ]]; then
  echo "Invalid PR list: ${pr_csv}" >&2
  exit 1
fi

if [[ -n "${run_url}" && ! "${run_url}" =~ ^https://github\.com/.+/actions/runs/[0-9]+$ ]]; then
  echo "Invalid run URL: ${run_url}" >&2
  exit 1
fi

if [[ -n "${public_url}" && ! "${public_url}" =~ ^https?://[^[:space:]/?#]+(:[0-9]+)?(/[^[:space:]?#]*)?$ ]]; then
  echo "Invalid public URL: ${public_url}" >&2
  exit 1
fi

set_env_value() {
  key="$1"
  value="$2"
  tmp=".env.release.tmp"

  if grep -q "^${key}=" .env.release; then
    sed "s|^${key}=.*|${key}=${value}|" .env.release > "${tmp}"
  else
    cp .env.release "${tmp}"
    printf '%s=%s\n' "${key}" "${value}" >> "${tmp}"
  fi

  mv "${tmp}" .env.release
}

get_env_value() {
  key="$1"
  default_value="${2:-}"
  value="$(grep -E "^${key}=" .env.release | tail -n 1 | cut -d= -f2- || true)"

  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
  else
    printf '%s' "${default_value}"
  fi
}

normalize_base_path() {
  local value="$1"

  if [[ -z "${value}" || "${value}" == "/" ]]; then
    printf '/'
    return 0
  fi

  case "${value}" in
    /*/) ;;
    /*) value="${value}/" ;;
    *) value="/${value}/" ;;
  esac

  case "${value}" in
    *//*|*[!A-Za-z0-9._~/-]*)
      echo "Invalid web base path: ${value}" >&2
      exit 1
      ;;
    */./*|*/../*)
      echo "Web base path must not contain '.' or '..' path segments: ${value}" >&2
      exit 1
      ;;
  esac

  local first_segment
  first_segment="${value#/}"
  first_segment="${first_segment%%/*}"
  case "${first_segment}" in
    api|oauth2|login|assets|registry|nginx-health|.well-known|runtime-config.js)
      echo "Web base path must not start with reserved SkillHub path segment: ${first_segment}" >&2
      exit 1
      ;;
  esac

  printf '%s' "${value}"
}

wait_for_postgres_ready() {
  postgres_user="$1"
  postgres_db="$2"

  for attempt in $(seq 1 60); do
    if docker compose --env-file .env.release -f compose.release.yml exec -T postgres \
      pg_isready -U "${postgres_user}" -d "${postgres_db}" >/dev/null 2>&1; then
      return 0
    fi

    sleep 2
  done

  echo "PostgreSQL did not become ready in time" >&2
  docker compose --env-file .env.release -f compose.release.yml logs postgres >&2 || true
  exit 1
}

wait_for_web_ready() {
  local web_port="$1"
  local health_path="$2"

  for attempt in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${web_port}${health_path}" >/dev/null 2>&1; then
      return 0
    fi

    sleep 2
  done

  echo "Web did not become ready in time: http://127.0.0.1:${web_port}${health_path}" >&2
  docker compose --env-file .env.release -f compose.release.yml logs web >&2 || true
  exit 1
}

ensure_postgres_password_matches_env() {
  postgres_user="$(get_env_value "POSTGRES_USER" "skillhub")"
  postgres_db="$(get_env_value "POSTGRES_DB" "skillhub")"
  postgres_password="$(get_env_value "POSTGRES_PASSWORD" "skillhub_demo")"

  if [[ -z "${postgres_password}" ]]; then
    echo "POSTGRES_PASSWORD must not be empty" >&2
    exit 1
  fi

  wait_for_postgres_ready "${postgres_user}" "${postgres_db}"

  docker compose --env-file .env.release -f compose.release.yml exec -T postgres \
    psql -U "${postgres_user}" -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -v password="${postgres_password}" <<'SQL' >/dev/null
SELECT format('ALTER ROLE %I WITH PASSWORD %L', current_user, :'password');
\gexec
SQL

  docker compose --env-file .env.release -f compose.release.yml exec -T \
    -e PGPASSWORD="${postgres_password}" postgres \
    psql -h 127.0.0.1 -U "${postgres_user}" -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -c 'select current_user;' >/dev/null
}

cd "${runtime_dir}"

test -f .env.release
test -f compose.release.yml

cp .env.release ".env.release.bak.$(date +%Y%m%d%H%M%S)"

set_env_value "SKILLHUB_VERSION" "${deploy_tag}"

if [[ -n "${public_url}" ]]; then
  set_env_value "SKILLHUB_PUBLIC_BASE_URL" "${public_url%/}"
fi

normalized_web_base_path=""
if [[ -n "${web_base_path}" ]]; then
  normalized_web_base_path="$(normalize_base_path "${web_base_path}")"
  set_env_value "SKILLHUB_WEB_BASE_PATH" "${normalized_web_base_path}"
  if [[ "${normalized_web_base_path}" == "/" ]]; then
    set_env_value "SKILLHUB_WEB_API_BASE_URL" ""
  else
    set_env_value "SKILLHUB_WEB_API_BASE_URL" "${normalized_web_base_path%/}"
  fi
fi

cat > manual-test-deployment.txt <<METADATA
deployed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
deploy_tag=${deploy_tag}
immutable_tag=${immutable_tag}
merged_sha=${merged_sha}
pr_numbers=${pr_csv}
run_url=${run_url}
public_url=${public_url}
web_base_path=${normalized_web_base_path:-}
METADATA

docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d postgres
ensure_postgres_password_matches_env
docker compose --env-file .env.release -f compose.release.yml up -d
docker compose --env-file .env.release -f compose.release.yml ps

web_port="$(awk -F= '/^WEB_PORT=/{print $2}' .env.release | tail -n 1)"
if [[ -z "${web_port}" ]]; then
  web_port="80"
fi

curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
web_health_path="/nginx-health"
if [[ -n "${normalized_web_base_path}" && "${normalized_web_base_path}" != "/" ]]; then
  web_health_path="${normalized_web_base_path%/}/nginx-health"
fi
wait_for_web_ready "${web_port}" "${web_health_path}"
