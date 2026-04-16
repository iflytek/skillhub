#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/prepare-pr-batch.sh --pr-list "123,456" [options]

Options:
  --base-ref <ref>         Base branch to merge onto. Default: main
  --deploy-channel <tag>   Floating image tag for the shared test runtime.
                           Default: manual-test-hk
EOF
}

base_ref="main"
deploy_channel="manual-test-hk"
pr_input=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-ref)
      [[ $# -ge 2 ]] || { echo "Missing value for --base-ref" >&2; exit 1; }
      base_ref="$2"
      shift 2
      ;;
    --deploy-channel)
      [[ $# -ge 2 ]] || { echo "Missing value for --deploy-channel" >&2; exit 1; }
      deploy_channel="$2"
      shift 2
      ;;
    --pr-list)
      [[ $# -ge 2 ]] || { echo "Missing value for --pr-list" >&2; exit 1; }
      pr_input="$2"
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

: "${GH_TOKEN:?GH_TOKEN is required}"

if [[ -z "${pr_input}" ]]; then
  echo "--pr-list is required" >&2
  exit 1
fi

normalized_input="$(printf '%s' "${pr_input}" | tr ',;\r\n\t' '     ')"

declare -a pr_numbers=()

for token in ${normalized_input}; do
  if [[ ! "${token}" =~ ^[0-9]+$ ]]; then
    echo "Invalid PR number: ${token}" >&2
    exit 1
  fi

  already_seen=false
  if [[ "${#pr_numbers[@]}" -gt 0 ]]; then
    for existing in "${pr_numbers[@]}"; do
      if [[ "${existing}" == "${token}" ]]; then
        already_seen=true
        break
      fi
    done
  fi

  if [[ "${already_seen}" == "true" ]]; then
    continue
  fi

  pr_numbers+=("${token}")
done

if [[ "${#pr_numbers[@]}" -eq 0 ]]; then
  echo "No PR numbers were parsed from --pr-list" >&2
  exit 1
fi

sanitized_channel="$(
  printf '%s' "${deploy_channel}" |
    tr '[:upper:]' '[:lower:]' |
    sed -E 's/[^a-z0-9._-]+/-/g; s/^-+//; s/-+$//; s/-{2,}/-/g'
)"

if [[ -z "${sanitized_channel}" ]]; then
  echo "Deploy channel resolved to an empty tag" >&2
  exit 1
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

git fetch --no-tags origin "${base_ref}"
git checkout -B manual-test-batch "origin/${base_ref}"

summary_file="${RUNNER_TEMP:-/tmp}/manual-test-batch-summary.md"
current_pr=""
trap 'status=$?; if [[ $status -ne 0 && -n "${current_pr}" ]]; then echo "Failed while merging PR #${current_pr}" >&2; fi' EXIT

{
  echo "### Manual Test Batch"
  echo
  echo "- Base ref: \`${base_ref}\`"
  echo "- Deploy channel: \`${sanitized_channel}\`"
  echo "- Selected PRs:"
} > "${summary_file}"

for pr in "${pr_numbers[@]}"; do
  current_pr="${pr}"

  IFS=$'\t' read -r state pr_base is_draft title url <<EOF
$(gh pr view "${pr}" \
    --json state,baseRefName,isDraft,title,url \
    --jq '[.state, .baseRefName, (.isDraft|tostring), .title, .url] | @tsv')
EOF

  if [[ "${state}" != "OPEN" ]]; then
    echo "PR #${pr} is not open (state=${state})" >&2
    exit 1
  fi

  if [[ "${pr_base}" != "${base_ref}" ]]; then
    echo "PR #${pr} targets ${pr_base}, expected ${base_ref}" >&2
    exit 1
  fi

  git fetch --no-tags origin "pull/${pr}/head:refs/remotes/origin/manual-test-pr-${pr}"
  git merge --no-ff --no-edit \
    -m "Merge PR #${pr} for manual test batch" \
    "refs/remotes/origin/manual-test-pr-${pr}"

  if [[ "${is_draft}" == "true" ]]; then
    title="${title} [draft]"
  fi

  echo "  - #${pr} ${title} (${url})" >> "${summary_file}"
done

merged_sha="$(git rev-parse HEAD)"
short_sha="$(git rev-parse --short=12 HEAD)"
run_token="${GITHUB_RUN_NUMBER:-manual}"
immutable_tag="${sanitized_channel}-${run_token}-${short_sha}"
pr_csv="$(IFS=,; echo "${pr_numbers[*]}")"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "base_ref=${base_ref}"
    echo "deploy_tag=${sanitized_channel}"
    echo "immutable_tag=${immutable_tag}"
    echo "merged_sha=${merged_sha}"
    echo "short_sha=${short_sha}"
    echo "pr_csv=${pr_csv}"
    echo "summary_file=${summary_file}"
  } >> "${GITHUB_OUTPUT}"
fi

{
  echo "- Merged SHA: \`${merged_sha}\`"
  echo "- Floating tag: \`${sanitized_channel}\`"
  echo "- Immutable tag: \`${immutable_tag}\`"
} >> "${summary_file}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "${summary_file}" >> "${GITHUB_STEP_SUMMARY}"
fi
