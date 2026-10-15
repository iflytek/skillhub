#!/bin/sh
# text-stats.sh — print word/line/character statistics for a text file as JSON.
#
# Usage: sh scripts/text-stats.sh <input-file>
# Emits a single-line JSON object on stdout and mirrors it to artifacts/report.json.
set -eu

if [ "$#" -ne 1 ]; then
    echo "usage: text-stats.sh <input-file>" >&2
    exit 2
fi

INPUT="$1"
if [ ! -f "$INPUT" ]; then
    echo "input file not found: $INPUT" >&2
    exit 2
fi

LINES=$(wc -l < "$INPUT" | tr -d ' ')
WORDS=$(wc -w < "$INPUT" | tr -d ' ')
CHARS=$(wc -c < "$INPUT" | tr -d ' ')

mkdir -p artifacts
printf '{"input":"%s","lines":%s,"words":%s,"characters":%s}\n' \
    "$INPUT" "$LINES" "$WORDS" "$CHARS" | tee artifacts/report.json
