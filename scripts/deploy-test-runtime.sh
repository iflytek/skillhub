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
  "${run_url}" <<'EOF'
set -euo pipefail

deploy_tag="$1"
immutable_tag="$2"
merged_sha="$3"
pr_csv="$4"
run_url="${5:-}"

sudo /usr/local/bin/skillhub-test-deploy \
  --deploy-tag "${deploy_tag}" \
  --immutable-tag "${immutable_tag}" \
  --merged-sha "${merged_sha}" \
  --pr-csv "${pr_csv}" \
  --run-url "${run_url}"
EOF
