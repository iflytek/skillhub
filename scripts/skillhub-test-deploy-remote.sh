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
EOF
}

runtime_dir="/opt/skillhub-runtime"
deploy_tag=""
immutable_tag=""
merged_sha=""
pr_csv=""
run_url=""

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

cd "${runtime_dir}"

test -f .env.release
test -f compose.release.yml

cp .env.release ".env.release.bak.$(date +%Y%m%d%H%M%S)"

set_env_value "SKILLHUB_VERSION" "${deploy_tag}"

cat > manual-test-deployment.txt <<METADATA
deployed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
deploy_tag=${deploy_tag}
immutable_tag=${immutable_tag}
merged_sha=${merged_sha}
pr_numbers=${pr_csv}
run_url=${run_url}
METADATA

docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
docker compose --env-file .env.release -f compose.release.yml ps

web_port="$(awk -F= '/^WEB_PORT=/{print $2}' .env.release | tail -n 1)"
if [[ -z "${web_port}" ]]; then
  web_port="80"
fi

curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
curl -fsS "http://127.0.0.1:${web_port}/nginx-health" >/dev/null
