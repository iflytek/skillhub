#!/usr/bin/env bash

set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${SPRING_PROFILES_ACTIVE:-local}"

cd "$SERVER_DIR"

./mvnw -pl skillhub-app -am clean package -DskipTests >/dev/null

APP_JAR="$(find skillhub-app/target -maxdepth 1 -type f -name 'skillhub-app-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$APP_JAR" ]]; then
  echo "Could not locate packaged skillhub-app jar under skillhub-app/target" >&2
  exit 1
fi

exec "${JAVA_BIN:-java}" -jar "$APP_JAR" --spring.profiles.active="$PROFILE" "$@"
