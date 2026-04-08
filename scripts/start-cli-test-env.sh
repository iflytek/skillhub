#!/bin/bash
set -e

SKILLHUB_HOME="/tmp/skillhub-cli-test"
BACKEND_PORT=8081
FRONTEND_PORT=3001

echo "=== SkillHub CLI Testing Environment ==="
echo "Home: $SKILLHUB_HOME"
echo "Backend: http://localhost:$BACKEND_PORT"
echo "Frontend: http://localhost:$FRONTEND_PORT"
echo ""

mkdir -p "$SKILLHUB_HOME"

export SKILLHUB_HOME
export SKILLHUB_PUBLIC_BASE_URL="http://localhost:$BACKEND_PORT"
export SPRING_PROFILES_ACTIVE=local
export SKILLHUB_DB_URL="jdbc:postgresql://localhost:5432/skillhub"
export SKILLHUB_DB_USERNAME="skillhub"
export SKILLHUB_DB_PASSWORD="skillhub_dev"
export SKILLHUB_REDIS_HOST="localhost"
export SKILLHUB_REDIS_PORT="6379"

echo "[1/4] Building Maven modules..."
cd /mnt/cfs/chenbaowang/skillhub/server
./mvnw install -DskipTests -pl skillhub-domain,skillhub-auth,skillhub-infra,skillhub-search,skillhub-notification -am -q
echo "Maven modules built."

echo "[2/4] Starting backend on port $BACKEND_PORT..."
cd /mnt/cfs/chenbaowang/skillhub/server
./mvnw spring-boot:run -pl skillhub-app -Dspring-boot.run.arguments="--server.port=$BACKEND_PORT" > "$SKILLHUB_HOME/backend.log" 2>&1 &
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

echo "[3/4] Starting frontend on port $FRONTEND_PORT..."
cd /mnt/cfs/chenbaowang/skillhub/web
pnpm exec vite --host 0.0.0.0 --port $FRONTEND_PORT > "$SKILLHUB_HOME/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID (log: $SKILLHUB_HOME/frontend.log)"

echo ""
echo "[4/4] Environment ready!"
echo ""
echo "=== Testing Commands ==="
echo ""
echo "export SKILLHUB_REGISTRY=http://localhost:$BACKEND_PORT"
echo "skillhub --registry http://localhost:$BACKEND_PORT --help"
echo ""
echo "=== Access URLs ==="
echo "Backend API: http://localhost:$BACKEND_PORT"
echo "Frontend:   http://localhost:$FRONTEND_PORT"
echo ""
echo "=== Logs ==="
echo "Backend: $SKILLHUB_HOME/backend.log"
echo "Frontend: $SKILLHUB_HOME/frontend.log"
echo ""
echo "Press Ctrl+C to stop"

echo "$BACKEND_PID" > "$SKILLHUB_HOME/backend.pid"
echo "$FRONTEND_PID" > "$SKILLHUB_HOME/frontend.pid"

wait
