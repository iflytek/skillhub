#!/bin/bash
# Stop CLI Testing Environment

SKILLHUB_HOME="/tmp/skillhub-cli-test"

if [ -f "$SKILLHUB_HOME/backend.pid" ]; then
    BACKEND_PID=$(cat "$SKILLHUB_HOME/backend.pid")
    echo "Stopping backend (PID: $BACKEND_PID)..."
    kill $BACKEND_PID 2>/dev/null || true
    rm "$SKILLHUB_HOME/backend.pid"
fi

if [ -f "$SKILLHUB_HOME/frontend.pid" ]; then
    FRONTEND_PID=$(cat "$SKILLHUB_HOME/frontend.pid")
    echo "Stopping frontend (PID: $FRONTEND_PID)..."
    kill $FRONTEND_PID 2>/dev/null || true
    rm "$SKILLHUB_HOME/frontend.pid"
fi

echo "CLI testing environment stopped."
