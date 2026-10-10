#!/usr/bin/env bash

set -euo pipefail

usage() {
  echo "Usage:" >&2
  echo "  $0 status --pid-file <file>" >&2
  echo "  $0 stop --pid-file <file>" >&2
  echo "  $0 start --pid-file <file> --log-file <file> --cwd <dir> -- <command...>" >&2
  exit 2
}

require_value() {
  local flag="$1"
  local value="${2:-}"
  if [[ -z "$value" ]]; then
    echo "Missing value for $flag" >&2
    usage
  fi
}

resolve_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s/%s\n' "$(pwd)" "$path"
  fi
}

is_running() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 1

  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  (( pid > 1 )) || {
    rm -f "$pid_file"
    return 1
  }

  if kill -0 "$pid" 2>/dev/null; then
    return 0
  fi

  rm -f "$pid_file"
  return 1
}

wait_for_exit() {
  local pid="$1"
  for _ in $(seq 1 50); do
    if ! kill -0 "$pid" 2>/dev/null; then
      return 0
    fi
    sleep 0.1
  done
  return 1
}

cmd="${1:-}"
[[ -n "$cmd" ]] || usage
shift || true

pid_file=""
log_file=""
cwd=""

case "$cmd" in
  status|stop)
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --pid-file)
          require_value "$1" "${2:-}"
          pid_file="$2"
          shift 2
          ;;
        *)
          usage
          ;;
      esac
    done
    [[ -n "$pid_file" ]] || usage
    ;;
  start)
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --pid-file)
          require_value "$1" "${2:-}"
          pid_file="$2"
          shift 2
          ;;
        --log-file)
          require_value "$1" "${2:-}"
          log_file="$2"
          shift 2
          ;;
        --cwd)
          require_value "$1" "${2:-}"
          cwd="$2"
          shift 2
          ;;
        --)
          shift
          break
          ;;
        *)
          usage
          ;;
      esac
    done
    [[ -n "$pid_file" && -n "$log_file" && -n "$cwd" && $# -gt 0 ]] || usage
    ;;
  *)
    usage
    ;;
esac

case "$cmd" in
  status)
    is_running "$pid_file"
    ;;
  stop)
    if ! is_running "$pid_file"; then
      rm -f "$pid_file"
      exit 0
    fi

    pid="$(cat "$pid_file")"
    kill "$pid" 2>/dev/null || true
    if ! wait_for_exit "$pid"; then
      kill -9 "$pid" 2>/dev/null || true
      wait_for_exit "$pid" || true
    fi
    rm -f "$pid_file"
    ;;
  start)
    pid_file="$(resolve_path "$pid_file")"
    log_file="$(resolve_path "$log_file")"
    cwd="$(resolve_path "$cwd")"
    mkdir -p "$(dirname "$pid_file")" "$(dirname "$log_file")"
    if is_running "$pid_file"; then
      echo "Process already running with PID $(cat "$pid_file")" >&2
      exit 1
    fi

    (
      cd "$cwd"
      if command -v setsid >/dev/null 2>&1; then
        setsid "$@" >>"$log_file" 2>&1 < /dev/null &
      else
        nohup "$@" >>"$log_file" 2>&1 < /dev/null &
      fi
      child_pid=$!
      disown "$child_pid" 2>/dev/null || true
      echo "$child_pid" >"$pid_file"
    )
    ;;
esac
