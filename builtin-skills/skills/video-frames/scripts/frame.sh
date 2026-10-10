#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  frame.sh <video-file> [--time HH:MM:SS] [--index N] --out /path/to/frame.jpg

Examples:
  frame.sh video.mp4 --out /tmp/frame.jpg
  frame.sh video.mp4 --time 00:00:10 --out /tmp/frame-10s.jpg
  frame.sh video.mp4 --index 0 --out /tmp/frame0.png
EOF
  exit 2
}

require_value() {
  local option="$1"
  local value="${2:-}"
  if [[ -z "$value" || "$value" == --* ]]; then
    echo "Missing value for $option" >&2
    usage
  fi
}

if [[ "${1:-}" == "" || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
fi

in="${1:-}"
shift || true

time=""
index=""
out=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --time)
      require_value "$1" "${2:-}"
      time="${2:-}"
      shift 2
      ;;
    --index)
      require_value "$1" "${2:-}"
      index="${2:-}"
      shift 2
      ;;
    --out)
      require_value "$1" "${2:-}"
      out="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage
      ;;
  esac
done

if [[ ! -f "$in" ]]; then
  echo "File not found: $in" >&2
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required but was not found in PATH" >&2
  exit 1
fi

if [[ "$out" == "" ]]; then
  echo "Missing --out" >&2
  usage
fi

if [[ "$index" != "" && ! "$index" =~ ^[0-9]+$ ]]; then
  echo "--index must be a non-negative integer: $index" >&2
  exit 2
fi

if [[ "$index" != "" && "$time" != "" ]]; then
  echo "Use either --index or --time, not both" >&2
  exit 2
fi

if [[ -e "$out" || -L "$out" ]]; then
  echo "Output already exists; refusing to overwrite: $out" >&2
  exit 1
fi

mkdir -p "$(dirname "$out")"

if [[ "$index" != "" ]]; then
  ffmpeg -hide_banner -loglevel error -n \
    -i "$in" \
    -vf "select=eq(n\\,${index})" \
    -vframes 1 \
    "$out"
elif [[ "$time" != "" ]]; then
  ffmpeg -hide_banner -loglevel error -n \
    -ss "$time" \
    -i "$in" \
    -frames:v 1 \
    "$out"
else
  ffmpeg -hide_banner -loglevel error -n \
    -i "$in" \
    -vf "select=eq(n\\,0)" \
    -vframes 1 \
    "$out"
fi

echo "$out"
