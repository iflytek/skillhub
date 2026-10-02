#!/usr/bin/env bash

# One-shot native riscv64 verification setup for the SkillHub authoring
# platform. Runs ON the native riscv64 machine (e.g. a PLCT/INCHI RVLab
# board) after SSH access is granted:
#
#   1. installs Docker, PostgreSQL and Redis (Ubuntu/Debian via apt,
#      openEuler via dnf);
#   2. creates the empty database the verification run migrates from zero
#      (user skillhub / password skillhub_dev, matching the local profile);
#   3. opens PostgreSQL and Redis to the docker bridge so the server
#      container can reach them (lab machine scope only);
#   4. builds the linux/riscv64 server image natively (no QEMU) — the
#      Alpine JDK build stage has no riscv64 variant, so this passes the
#      Noble one via --build-arg;
#   5. runs scripts/riscv64-verify.sh against the freshly built image and
#      tees everything into an evidence log.
#
# Usage (from the skillhub checkout, as a user with sudo):
#   sudo scripts/riscv64-native-setup.sh
#
# Evidence lands in riscv64-native-evidence-<date>.log next to the script.
# Reviewers may additionally ask for: uname -a, /proc/cpuinfo head, and the
# absence of qemu strings in the boot log (native-proof items from
# docs/26-skill-authoring-platform.md).

set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"
DB_NAME="${SKILLHUB_RISCV_DB:-skillhub_riscv_check}"
BRIDGE_HOST="${SKILLHUB_RISCV_DB_HOST:-172.17.0.1}"
IMAGE="${SKILLHUB_RISCV_IMAGE:-skillhub-server:riscv64-native}"
EVIDENCE="riscv64-native-evidence-$(date +%Y%m%d-%H%M%S).log"

log() { echo "$@" | tee -a "$EVIDENCE"; }

[[ "$(uname -m)" == "riscv64" ]] || { echo "this script targets native riscv64 hosts; here uname -m is $(uname -m)" >&2; exit 1; }
[[ $EUID -eq 0 ]] || { echo "run with sudo (needs to install packages and edit service configs)" >&2; exit 1; }

log "=== native riscv64 verification setup — $(date -u +%FT%TZ) ==="
log "host: $(uname -a)"
log "cpu:  $(head -4 /proc/cpuinfo | tr '\n' ' ')"
if lsmod 2>/dev/null | grep -q qemu || grep -qi qemu /proc/cpuinfo; then
  log "WARNING: qemu markers found — this may not be a native machine"
fi
log

PKG=""
if command -v apt-get >/dev/null 2>&1; then
  PKG=apt
elif command -v dnf >/dev/null 2>&1; then
  PKG=dnf
else
  echo "unsupported distro: need apt-get or dnf" >&2; exit 1
fi
log "--- 1. install docker / postgresql / redis ($PKG) ---"
if [[ "$PKG" == "apt" ]]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y docker.io postgresql redis-server
else
  dnf install -y docker postgresql-server redis || dnf install -y docker podman postgresql-server redis
fi
systemctl enable --now docker 2>/dev/null || service docker start 2>/dev/null || true
systemctl enable --now redis 2>/dev/null || systemctl enable --now redis-server 2>/dev/null || service redis-server start 2>/dev/null || true
log "docker: $(docker --version 2>&1)"

log "--- 2. prepare empty database $DB_NAME (Flyway migrates from zero) ---"
if [[ "$PKG" == "apt" ]]; then
  PG_CONF=/etc/postgresql/*/main
else
  PG_CONF=/var/lib/pgsql/data
fi
# listen on all interfaces and accept the docker bridge with password auth
if [[ "$PKG" == "apt" ]]; then
  sed -i "s/^#\?listen_addresses.*/listen_addresses = '*'/" $PG_CONF/postgresql.conf
  grep -q "172.17.0.0/16" $PG_CONF/pg_hba.conf || echo "host all all 172.17.0.0/16 scram-sha-256" >> $PG_CONF/pg_hba.conf
  systemctl reload postgresql 2>/dev/null || systemctl restart postgresql
else
  postgresql-setup --initdb 2>/dev/null || true
  sed -i "s/^#\?listen_addresses.*/listen_addresses = '*'/" $PG_CONF/postgresql.conf
  grep -q "172.17.0.0/16" $PG_CONF/pg_hba.conf || echo "host all all 172.17.0.0/16 scram-sha-256" >> $PG_CONF/pg_hba.conf
  systemctl enable --now postgresql
fi
sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'skillhub') THEN
    CREATE ROLE skillhub LOGIN PASSWORD 'skillhub_dev';
  END IF;
END \$\$;
DROP DATABASE IF EXISTS $DB_NAME;
CREATE DATABASE $DB_NAME OWNER skillhub;
SQL
log "database $DB_NAME ready (owner skillhub)"

log "--- 3. expose redis to the docker bridge (lab machine only) ---"
REDIS_CONF=$(find /etc/redis* /etc -maxdepth 2 -name 'redis.conf' 2>/dev/null | head -1)
if [[ -n "$REDIS_CONF" ]]; then
  sed -i 's/^#\?bind .*/bind 127.0.0.1 172.17.0.1/' "$REDIS_CONF"
  sed -i 's/^#\?protected-mode .*/protected-mode no/' "$REDIS_CONF"
  systemctl restart redis 2>/dev/null || systemctl restart redis-server 2>/dev/null || service redis-server restart 2>/dev/null || true
  log "redis listening on 127.0.0.1 + $BRIDGE_HOST"
else
  log "redis.conf not found automatically — ensure redis answers on $BRIDGE_HOST:6379"
fi

log "--- 4. build the image natively (no QEMU) ---"
docker build --build-arg BUILD_IMAGE=eclipse-temurin:21-jdk-noble \
  -t "$IMAGE" server/ 2>&1 | tee -a "$EVIDENCE" | tail -5
docker image inspect "$IMAGE" --format 'built: {{.Os}}/{{.Architecture}}' | tee -a "$EVIDENCE"

log "--- 5. run the authoring verification ---"
SKILLHUB_RISCV_DB="$DB_NAME" SKILLHUB_RISCV_DB_HOST="$BRIDGE_HOST" \
  bash scripts/riscv64-verify.sh "$IMAGE" 2>&1 | tee -a "$EVIDENCE"

log
log "=== done — evidence saved to $EVIDENCE ==="
