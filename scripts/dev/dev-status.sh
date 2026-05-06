#!/usr/bin/env bash
set -euo pipefail

printf '8080:\n'
lsof -nP -iTCP:8080 -sTCP:LISTEN || true
printf '\n3000:\n'
lsof -nP -iTCP:3000 -sTCP:LISTEN || true
printf '\nDocker:\n'
docker compose ps
printf '\nHealth:\n'
curl -fsS http://127.0.0.1:8080/actuator/health || true
printf '\n'
