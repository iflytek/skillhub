#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/deploy-test-runtime.sh [options]

Options:
  --host <host>              Remote SSH host
  --user <user>              Remote SSH user. Default: skillhub-deploy
  --port <port>              Remote SSH port. Default: 22
  --key-file <path>          SSH private key for deployment
  --deploy-tag <tag>         Floating image tag to deploy
  --immutable-tag <tag>      Immutable image tag for traceability
  --merged-sha <sha>         Synthetic merge commit SHA
  --pr-csv <list>            Comma-separated PR numbers
  --run-url <url>            GitHub Actions run URL
  --public-url <url>         Public URL configured on the remote runtime
  --web-base-path <path>     Optional Web UI base path, for example /skillhub/
EOF
}

ssh_host=""
ssh_user="skillhub-deploy"
ssh_port="22"
ssh_key_file=""
deploy_tag=""
immutable_tag=""
merged_sha=""
pr_csv=""
run_url=""
public_url=""
web_base_path=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || { echo "Missing value for --host" >&2; exit 1; }
      ssh_host="$2"
      shift 2
      ;;
    --user)
      [[ $# -ge 2 ]] || { echo "Missing value for --user" >&2; exit 1; }
      ssh_user="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || { echo "Missing value for --port" >&2; exit 1; }
      ssh_port="$2"
      shift 2
      ;;
    --key-file)
      [[ $# -ge 2 ]] || { echo "Missing value for --key-file" >&2; exit 1; }
      ssh_key_file="$2"
      shift 2
      ;;
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

[[ -n "${ssh_host}" ]] || { echo "--host is required" >&2; exit 1; }
[[ -n "${ssh_key_file}" ]] || { echo "--key-file is required" >&2; exit 1; }
[[ -n "${deploy_tag}" ]] || { echo "--deploy-tag is required" >&2; exit 1; }
[[ -n "${immutable_tag}" ]] || { echo "--immutable-tag is required" >&2; exit 1; }

ssh_opts=(
  -i "${ssh_key_file}"
  -o BatchMode=yes
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=accept-new
  -o ServerAliveInterval=15
  -o ServerAliveCountMax=3
  -o TCPKeepAlive=yes
  -o ConnectTimeout=10
  -p "${ssh_port}"
)

ssh "${ssh_opts[@]}" "${ssh_user}@${ssh_host}" bash -s -- \
  "${deploy_tag}" \
  "${immutable_tag}" \
  "${merged_sha}" \
  "${pr_csv}" \
  "${run_url}" \
  "${public_url}" \
  "${web_base_path}" <<'EOF'
set -euo pipefail

deploy_tag="$1"
immutable_tag="$2"
merged_sha="$3"
pr_csv="$4"
run_url="${5:-}"
public_url="${6:-}"
web_base_path="${7:-}"
runtime_dir="/opt/skillhub-runtime"
env_file="${runtime_dir}/.env.release"
env_example_file="${runtime_dir}/.env.release.example"

set_env_value() {
  local key="$1"
  local value="$2"
  local tmp

  tmp="$(mktemp "${env_file}.tmp.XXXXXX")"
  if grep -q "^${key}=" "${env_file}"; then
    sed "s|^${key}=.*|${key}=${value}|" "${env_file}" >"${tmp}"
  else
    cp "${env_file}" "${tmp}"
    printf '%s=%s\n' "${key}" "${value}" >>"${tmp}"
  fi
  mv "${tmp}" "${env_file}"
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

if [[ ! -f "${env_file}" ]]; then
  if [[ ! -f "${env_example_file}" ]]; then
    echo "Missing HK runtime env file: ${env_file}" >&2
    echo "Missing HK runtime env example: ${env_example_file}" >&2
    echo "Initialize the HK runtime directory with scripts/runtime.sh before deployment." >&2
    exit 1
  fi

  old_umask="$(umask)"
  umask 077
  cp "${env_example_file}" "${env_file}"
  umask "${old_umask}"
  echo "Initialized HK runtime env file from ${env_example_file}"
fi

if [[ -n "${public_url}" ]]; then
  if [[ ! "${public_url}" =~ ^https?://[^[:space:]/?#]+(:[0-9]+)?(/[^[:space:]?#]*)?$ ]]; then
    echo "Invalid public URL: ${public_url}" >&2
    exit 1
  fi
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

deploy_status=0
sudo /usr/local/bin/skillhub-test-deploy \
  --deploy-tag "${deploy_tag}" \
  --immutable-tag "${immutable_tag}" \
  --merged-sha "${merged_sha}" \
  --pr-csv "${pr_csv}" \
  --run-url "${run_url}" || deploy_status=$?

if [[ ! -r /opt/skillhub-runtime/manual-test-deployment.txt ]] || \
   ! grep -Fq "deploy_tag=${deploy_tag}" /opt/skillhub-runtime/manual-test-deployment.txt || \
   ! grep -Fq "immutable_tag=${immutable_tag}" /opt/skillhub-runtime/manual-test-deployment.txt; then
  echo "HK deployment metadata does not match the requested image tags." >&2
  exit 1
fi

web_health_paths=("/nginx-health")
if [[ -n "${normalized_web_base_path}" && "${normalized_web_base_path}" != "/" ]]; then
  web_health_paths+=("${normalized_web_base_path%/}/nginx-health")
fi

for health_path in "${web_health_paths[@]}"; do
  ready=false
  for attempt in $(seq 1 60); do
    health_body="$(curl -fsS "http://127.0.0.1:8081${health_path}" 2>/dev/null || true)"
    if [[ "${health_body}" == "ok" ]]; then
      ready=true
      break
    fi
    sleep 2
  done

  if [[ "${ready}" != "true" ]]; then
    echo "HK runtime web health check failed: http://127.0.0.1:8081${health_path}" >&2
    exit 1
  fi
done

if [[ -n "${public_url}" ]]; then
  curl -fsS "${public_url%/}/runtime-config.js" | grep -Fq "window.__SKILLHUB_RUNTIME_CONFIG__"
fi

if [[ "${deploy_status}" -ne 0 ]]; then
  echo "HK deploy helper exited with ${deploy_status}, but post-deploy runtime checks passed." >&2
fi
EOF
