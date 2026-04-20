#!/bin/bash
# Send skillhub-cli release notification to Feishu group via bot webhook
# Usage: FEISHU_WEBHOOK_URL=xxx ./scripts/notify-feishu.sh <version> [prev_version]
set -euo pipefail

WEBHOOK_URL="${FEISHU_WEBHOOK_URL:-}"
VERSION="${1:-}"
PREV_VERSION="${2:-}"

if [ -z "$WEBHOOK_URL" ]; then
  echo "Error: FEISHU_WEBHOOK_URL is not set"
  echo "Usage: FEISHU_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxx $0 <version> [prev_version]"
  exit 1
fi

if [ -z "$VERSION" ]; then
  echo "Error: version is required"
  echo "Usage: $0 <version> [prev_version]"
  exit 1
fi

# Generate changelog from git log
if [ -n "$PREV_VERSION" ]; then
  RANGE="skillhub-cli/v${PREV_VERSION}..HEAD"
else
  # If no prev version, use last 20 commits touching skillhub-cli/
  RANGE="HEAD~20..HEAD"
fi

# Extract changelog lines, escape for JSON
CHANGELOG=$(git log "$RANGE" --oneline --no-merges -- skillhub-cli/ 2>/dev/null | head -20 | \
  sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')

if [ -z "$CHANGELOG" ]; then
  CHANGELOG="详见 npm 页面"
fi

BODY=$(cat <<EOF
{
  "msg_type": "interactive",
  "card": {
    "header": {
      "title": {
        "tag": "plain_text",
        "content": "skillhub-cli v${VERSION} 发布"
      },
      "template": "green"
    },
    "elements": [
      {
        "tag": "div",
        "text": {
          "tag": "lark_md",
          "content": "**更新内容：**\n${CHANGELOG}\n\n**安装/更新：**\n\`npm install -g motovis-skillhub\`"
        }
      },
      {
        "tag": "action",
        "actions": [
          {
            "tag": "button",
            "text": {
              "tag": "plain_text",
              "content": "npm 页面"
            },
            "url": "https://www.npmjs.com/package/motovis-skillhub",
            "type": "primary"
          }
        ]
      }
    ]
  }
}
EOF
)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$BODY")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY_RESPONSE=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
  echo "Feishu notification sent successfully"
  echo "$BODY_RESPONSE"
else
  echo "Failed to send Feishu notification (HTTP $HTTP_CODE)"
  echo "$BODY_RESPONSE"
  exit 1
fi
