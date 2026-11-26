#!/usr/bin/env bash
set -euo pipefail

# Repeatable SCIM client fixture. It talks to a temporary SkillHub Directory
# Connection; it does not start a long-lived service and never prints the token.
: "${SCIM_BASE_URL:?set SCIM_BASE_URL to the /scim/v2 endpoint}"
: "${SCIM_TOKEN:?set SCIM_TOKEN in the environment}"
: "${SCIM_EXTERNAL_ID:=fixture-user-001}"
: "${SCIM_USERNAME:=fixture.user}"
: "${SCIM_EMAIL:=fixture.user@example.invalid}"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

request() {
  local method="$1" path="$2" body="${3:-}" etag="${4:-}"
  local headers=( -H "Authorization: Bearer ${SCIM_TOKEN}" -H 'Accept: application/scim+json' )
  if [[ -n "$body" ]]; then
    headers+=( -H 'Content-Type: application/scim+json' --data "$body" )
  fi
  if [[ -n "$etag" ]]; then
    headers+=( -H "If-Match: ${etag}" )
  fi
  curl --silent --show-error --fail-with-body -X "$method" "${headers[@]}" \
    -D "$tmp_dir/headers" -o "$tmp_dir/body" "${SCIM_BASE_URL}${path}"
  cat "$tmp_dir/body"
}

echo "1. ServiceProviderConfig"
request GET /ServiceProviderConfig | jq -e '.patch.supported == true and .etag.supported == true' >/dev/null

payload="$(jq -nc \
  --arg externalId "$SCIM_EXTERNAL_ID" \
  --arg userName "$SCIM_USERNAME" \
  --arg email "$SCIM_EMAIL" \
  '{schemas:["urn:ietf:params:scim:schemas:core:2.0:User"], externalId:$externalId, userName:$userName, displayName:"Fixture User", active:true, emails:[{value:$email,type:"work",primary:true}]}')"

echo "2. Create user"
request POST /Users "$payload" | tee "$tmp_dir/create.json" | jq -e '.externalId == $ENV.SCIM_EXTERNAL_ID and .active == true' >/dev/null
user_id="$(jq -r '.id' "$tmp_dir/create.json")"
etag="$(awk 'BEGIN{IGNORECASE=1} /^ETag:/{sub(/[[:space:]]+$/, ""); sub(/^ETag:[[:space:]]*/, ""); print; exit}' "$tmp_dir/headers")"
[[ -n "$user_id" && -n "$etag" ]]

echo "3. List, filter and replay"
request GET '/Users?startIndex=1&count=10' | jq -e --arg user_id "$user_id" '.totalResults >= 1 and (.Resources | map(select(.id == $user_id)) | length == 1)' >/dev/null
request GET "/Users?filter=externalId%20eq%20%22${SCIM_EXTERNAL_ID}%22" | jq -e '.totalResults == 1' >/dev/null
request POST /Users "$payload" | jq -e --arg user_id "$user_id" '.id == $user_id' >/dev/null

echo "4. Replace with current ETag"
replace_payload="$(jq -nc \
  --arg externalId "$SCIM_EXTERNAL_ID" \
  --arg userName "$SCIM_USERNAME" \
  --arg email "$SCIM_EMAIL" \
  '{schemas:["urn:ietf:params:scim:schemas:core:2.0:User"], externalId:$externalId, userName:$userName, displayName:"Fixture User Updated", active:true, emails:[{value:$email,type:"work",primary:true}]}')"
request PUT "/Users/${user_id}" "$replace_payload" "$etag" | tee "$tmp_dir/replace.json" | jq -e '.displayName == "Fixture User Updated"' >/dev/null
etag="$(awk 'BEGIN{IGNORECASE=1} /^ETag:/{sub(/[[:space:]]+$/, ""); sub(/^ETag:[[:space:]]*/, ""); print; exit}' "$tmp_dir/headers")"

echo "5. Patch and deactivate"
patch_payload='{"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],"Operations":[{"op":"replace","path":"active","value":false}]}'
request PATCH "/Users/${user_id}" "$patch_payload" "$etag" | jq -e '.active == false' >/dev/null

echo "SCIM lifecycle fixture passed (create/list/filter/replay/replace/patch/deactivate)."
