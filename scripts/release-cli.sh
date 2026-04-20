#!/bin/bash
# Release skillhub-cli: build, test, version bump, publish, notify
# Usage: ./scripts/release-cli.sh [patch|minor|major] [prev_version]
# Examples:
#   ./scripts/release-cli.sh patch          # 1.0.7 -> 1.0.8
#   ./scripts/release-cli.sh minor          # 1.0.7 -> 1.1.0
#   ./scripts/release-cli.sh patch 1.0.6    # with prev_version for changelog
set -euo pipefail

BUMP="${1:-patch}"
PREV_VERSION="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CLI_DIR="$PROJECT_DIR/skillhub-cli"

echo "=== skillhub-cli release ==="
echo ""

# Step 1: Build
echo "[1/5] Building..."
cd "$CLI_DIR"
pnpm build
echo "Build OK"
echo ""

# Step 2: Run tests
echo "[2/5] Running tests..."
if pnpm test; then
  echo "Tests OK"
else
  echo "Tests FAILED. Aborting release."
  exit 1
fi
echo ""

# Step 3: Bump version
OLD_VERSION=$(node -p "require('./package.json').version")
npm version "$BUMP" --no-git-tag-version -m "chore(cli): release v%s"
NEW_VERSION=$(node -p "require('./package.json').version")
echo "[3/5] Version bumped: $OLD_VERSION -> $NEW_VERSION"
echo ""

# Step 4: Publish
echo "[4/5] Publishing to npm..."
npm publish --access public
echo "Published motovis-skillhub@$NEW_VERSION"
echo ""

# Step 5: Notify via Feishu
echo "[5/5] Sending Feishu notification..."
if [ -n "${FEISHU_WEBHOOK_URL:-}" ]; then
  "$SCRIPT_DIR/notify-feishu.sh" "$NEW_VERSION" "$PREV_VERSION"
else
  echo "FEISHU_WEBHOOK_URL not set, skipping notification"
  echo "To send manually: FEISHU_WEBHOOK_URL=xxx $SCRIPT_DIR/notify-feishu.sh $NEW_VERSION $PREV_VERSION"
fi
echo ""

echo "=== Release complete: v$NEW_VERSION ==="
