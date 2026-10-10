#!/bin/sh

set -eu

SCANNER_URL="${1:-http://localhost:8000}"
SAMPLE_PACKAGE="${2:-}"

echo "== Skill Scanner Verification =="
echo "Scanner URL: $SCANNER_URL"

echo "[1/3] Health check"
if curl -fsS "$SCANNER_URL/health" >/dev/null; then
  echo "ok: /health"
else
  echo "error: scanner health check failed" >&2
  exit 1
fi

echo "[2/3] Analyzer inventory"
if curl -fsS "$SCANNER_URL/analyzers" >/dev/null; then
  echo "ok: /analyzers"
else
  echo "warn: /analyzers is unavailable; continue with health-only verification" >&2
fi

echo "[3/3] Upload smoke test"
if [ -n "$SAMPLE_PACKAGE" ]; then
  if [ ! -f "$SAMPLE_PACKAGE" ]; then
    echo "error: sample package not found: $SAMPLE_PACKAGE" >&2
    exit 1
  fi

  response="$(curl -fsS -X POST "$SCANNER_URL/scan-upload" -F "file=@$SAMPLE_PACKAGE")"
  case "$response" in
    *scan_id*|*scanId*)
      echo "ok: /scan-upload"
      ;;
    *)
      echo "error: upload smoke test did not return scan id" >&2
      echo "$response" >&2
      exit 1
      ;;
  esac
else
  echo "skip: no sample package provided"
  echo "      usage: sh scripts/verify-scanner.sh http://localhost:8000 /path/to/skill.zip"
fi

echo "Verification finished"
