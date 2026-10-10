#!/usr/bin/env bash

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

info() {
  echo "INFO: $*"
}

slugify() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'
}

repo_root() {
  git rev-parse --show-toplevel 2>/dev/null || fail "Run this script inside a git repository"
}

git_common_dir() {
  local root common_dir
  root="$(repo_root)"
  common_dir="$(git rev-parse --git-common-dir 2>/dev/null)" || fail "Unable to resolve the git common directory"

  if [ "${common_dir#/}" != "$common_dir" ]; then
    printf '%s\n' "$common_dir"
    return 0
  fi

  (
    cd "$root/$common_dir" && pwd
  )
}

repo_name() {
  basename "$(dirname "$(git_common_dir)")"
}

current_branch() {
  git rev-parse --abbrev-ref HEAD 2>/dev/null || fail "Unable to resolve the current branch"
}

integration_task_from_branch() {
  local branch="$1"

  case "$branch" in
    agent/integration/*)
      printf '%s\n' "${branch#agent/integration/}"
      ;;
    *)
      return 1
      ;;
  esac
}

require_integration_task() {
  local branch task
  branch="$(current_branch)"
  task="$(integration_task_from_branch "$branch")" || fail "Run this command inside the integration worktree on branch agent/integration/<task>"
  printf '%s\n' "$task"
}

worktree_root() {
  local root="${1:-$(repo_root)}"
  printf '%s\n' "${PARALLEL_WORKTREE_ROOT:-$(dirname "$root")}"
}

integration_dir_for_task() {
  local task="$1"
  local root
  root="$(repo_root)"
  printf '%s/%s-integration-%s\n' "$(worktree_root "$root")" "$(repo_name)" "$task"
}
