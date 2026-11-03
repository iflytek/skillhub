#!/usr/bin/env bash
# Run against an installed SkillHub Helm release and independent stats namespace.
# Port-forward Web beforehand; the values file must reproduce the installed release.
set -euo pipefail
CHART_DIR=$(cd "$(dirname "$0")/.." && pwd)
WEBSITE_ID=${1:?Usage: analytics-smoke.sh WEBSITE_UUID VALUES_FILE [release] [namespace]}
VALUES_FILE=${2:?Specify the Helm override values used for the installation}
RELEASE=${3:-sh1037}
NAMESPACE=${4:-skillhub}
WEB_URL=${ANALYTICS_SMOKE_WEB_URL:-http://127.0.0.1:18082}
[[ "$WEBSITE_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || { echo 'Invalid website UUID' >&2; exit 1; }
for bin in helm kubectl curl; do command -v "$bin" >/dev/null || exit 1; done
pod_uid() {
  kubectl -n "$NAMESPACE" get pods -l "app.kubernetes.io/name=skillhub,app.kubernetes.io/component=$1" -o json \
    | python3 -c 'import json,sys; pods=[p for p in json.load(sys.stdin)["items"] if not p["metadata"].get("deletionTimestamp") and any(c.get("type")=="Ready" and c.get("status")=="True" for c in p.get("status",{}).get("conditions",[]))]; assert len(pods)==1,pods; print(pods[0]["metadata"]["uid"])'
}
count_events() {
  kubectl -n stats exec deployment/umami-postgres -- psql -U umami -d umami -tAc \
    "select count(*) from website_event where website_id='$WEBSITE_ID' and event_name='skill.card.open'" | tr -d '[:space:]'
}
server_before=$(pod_uid server)
web_before=$(pod_uid web)
before=$(count_events)
script_code=$(curl -sS -o /dev/null -w '%{http_code}' "$WEB_URL/_skillhub-analytics/script.js")
[[ "$script_code" == 200 ]] || { echo "Tracker failed: $script_code" >&2; exit 1; }
send_code=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$WEB_URL/_skillhub-analytics/api/send" \
  -H 'content-type: application/json' -H 'user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36' \
  -H "origin: $WEB_URL" \
  --data "{\"type\":\"event\",\"payload\":{\"website\":\"$WEBSITE_ID\",\"name\":\"skill.card.open\",\"data\":{\"event\":\"click\"},\"hostname\":\"localhost\",\"url\":\"/skills\",\"screen\":\"1440x900\",\"language\":\"en-US\",\"title\":\"SkillHub\"}}")
[[ "$send_code" == 200 ]] || { echo "Event send failed: $send_code" >&2; exit 1; }
after=$(count_events)
[[ "$after" -gt "$before" ]] || { echo 'HTTP 200 but event not stored' >&2; exit 1; }
echo "Independent stats DB: event count $before -> $after (script=$script_code send=$send_code)"
# Reuse installed values (including secrets); the caller file must reproduce the install.
# Never apply chart test fixtures to an existing release.
changed="{\"enabled\":true,\"adapter\":\"umami\",\"consent\":\"granted\",\"rules\":{},\"adapterConfig\":{\"websiteId\":\"$WEBSITE_ID\"}}"
change_values=$(mktemp "${TMPDIR:-/tmp}/skillhub-analytics-smoke.XXXXXX")
trap 'rm -f "$change_values"' EXIT
printf "web:\n  analytics:\n    config: '%s'\n" "$changed" >"$change_values"
helm upgrade "$RELEASE" "$CHART_DIR" -n "$NAMESPACE" --reuse-values -f "$VALUES_FILE" \
  -f "$change_values" --wait --timeout 5m >/dev/null
kubectl -n "$NAMESPACE" rollout status "deployment/$RELEASE-skillhub-web" --timeout=180s >/dev/null
server_after=$(pod_uid server)
web_after=$(pod_uid web)
[[ "$server_before" == "$server_after" ]] || { echo 'Server pod rolled unexpectedly' >&2; exit 1; }
[[ "$web_before" != "$web_after" ]] || { echo 'Web pod did not roll' >&2; exit 1; }
echo "analytics-only Helm upgrade: web pod UID $web_before -> $web_after; server UID unchanged $server_after"
