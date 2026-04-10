#!/bin/bash
set -e

SKILLHUB_HOME="${SKILLHUB_HOME:-/tmp/skillhub-cli-test}"
BACKEND_PORT=8081
FRONTEND_PORT=3001

echo "=== SkillHub CLI Testing Environment ==="
echo "Home: $SKILLHUB_HOME"
echo ""

mkdir -p "$SKILLHUB_HOME"

export SKILLHUB_HOME
export SKILLHUB_PUBLIC_BASE_URL="http://0.0.0.0:$BACKEND_PORT"
export SPRING_PROFILES_ACTIVE=local

# PostgreSQL / Redis / MinIO via docker compose
echo "[1/5] Starting PostgreSQL, Redis, MinIO..."
cd /mnt/cfs/chenbaowang/skillhub
docker compose up -d postgres redis minio
echo "Waiting for PostgreSQL..."
for i in {1..30}; do
    if docker compose exec -T postgres pg_isready -U skillhub > /dev/null 2>&1; then
        echo "PostgreSQL ready!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "PostgreSQL failed to start."
        docker compose logs postgres
        exit 1
    fi
    sleep 2
done
echo "Waiting for Redis..."
for i in {1..15}; do
    if docker compose exec -T redis redis-cli ping > /dev/null 2>&1; then
        echo "Redis ready!"
        break
    fi
    if [ $i -eq 15 ]; then
        echo "Redis failed to start."
        docker compose logs redis
        exit 1
    fi
    sleep 2
done

echo "[2/5] Building and packaging skillhub-app..."
cd /mnt/cfs/chenbaowang/skillhub/server
./mvnw -pl skillhub-app -am clean package -DskipTests -q
APP_JAR="$(find skillhub-app/target -maxdepth 1 -type f -name 'skillhub-app-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$APP_JAR" ]]; then
    echo "ERROR: Could not locate packaged skillhub-app jar"
    exit 1
fi
echo "Built: $APP_JAR"

echo "[3/5] Starting backend on port $BACKEND_PORT (binding to 0.0.0.0)..."
java -jar "$APP_JAR" \
    --server.port=$BACKEND_PORT \
    --server.address=0.0.0.0 \
    --spring.datasource.url=jdbc:postgresql://localhost:5432/skillhub \
    --spring.datasource.username=skillhub \
    --spring.datasource.password=skillhub_dev \
    --spring.data.redis.host=localhost \
    --spring.data.redis.port=6379 \
    > "$SKILLHUB_HOME/backend.log" 2>&1 &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID (log: $SKILLHUB_HOME/backend.log)"

echo "Waiting for backend to start..."
for i in {1..60}; do
    if curl -sf "http://localhost:$BACKEND_PORT/actuator/health" > /dev/null 2>&1; then
        echo "Backend ready!"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "Backend failed to start. Check log: $SKILLHUB_HOME/backend.log"
        tail -50 "$SKILLHUB_HOME/backend.log"
        exit 1
    fi
    sleep 3
done

echo "[4/5] Starting frontend on port $FRONTEND_PORT (binding to 0.0.0.0)..."
cd /mnt/cfs/chenbaowang/skillhub/web
pnpm exec vite --host 0.0.0.0 --port $FRONTEND_PORT > "$SKILLHUB_HOME/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID (log: $SKILLHUB_HOME/frontend.log)"

echo ""
echo "[5/5] Environment ready!"
echo ""
echo "=== LAN Access URLs ==="
HOST_IP=$(hostname -I | awk '{print $1}')
echo "Backend API: http://$HOST_IP:$BACKEND_PORT"
echo "Frontend:   http://$HOST_IP:$FRONTEND_PORT"
echo ""
echo "=== Local URLs ==="
echo "Backend API: http://localhost:$BACKEND_PORT"
echo "Frontend:   http://localhost:$FRONTEND_PORT"
echo ""
echo "=== CLI Test Commands ==="
echo "node dist/cli.mjs --registry http://$HOST_IP:$BACKEND_PORT --help"
echo "node dist/cli.mjs --registry http://localhost:$BACKEND_PORT --help"
echo ""
echo "=== Logs ==="
echo "Backend: $SKILLHUB_HOME/backend.log"
echo "Frontend: $SKILLHUB_HOME/frontend.log"
echo ""
echo "Press Ctrl+C to stop"

echo "$BACKEND_PID" > "$SKILLHUB_HOME/backend.pid"
echo "$FRONTEND_PID" > "$SKILLHUB_HOME/frontend.pid"

# Cleanup on Ctrl+C
trap 'echo "Stopping services..."; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; docker compose stop postgres redis minio 2>/dev/null; exit 0' INT

wait
