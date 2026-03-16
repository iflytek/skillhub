#!/bin/bash
# Verify skill-scanner service integration

set -e

SCANNER_URL="${1:-http://localhost:8000}"

echo "=== Skill Scanner Verification ==="
echo "Scanner URL: $SCANNER_URL"
echo ""

echo "[1/3] Checking health endpoint..."
if curl -sf "$SCANNER_URL/health" > /dev/null; then
    echo "✓ Health check passed"
else
    echo "✗ Health check failed"
    exit 1
fi

echo ""
echo "[2/3] Listing available analyzers..."
ANALYZERS=$(curl -sf "$SCANNER_URL/analyzers")
if [ -n "$ANALYZERS" ]; then
    echo "✓ Analyzers endpoint accessible"
    echo "$ANALYZERS" | python3 -m json.tool 2>/dev/null || echo "$ANALYZERS"
else
    echo "✗ Failed to retrieve analyzers"
    exit 1
fi

echo ""
echo "[3/3] Checking API documentation..."
if curl -sf "$SCANNER_URL/docs" > /dev/null; then
    echo "✓ API docs available at $SCANNER_URL/docs"
else
    echo "⚠ API docs not available (this is optional)"
fi

echo ""
echo "=== Verification Complete ==="
echo "Scanner service is operational"
