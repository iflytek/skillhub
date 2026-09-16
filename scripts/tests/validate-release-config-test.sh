#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/validate-release-config.sh"

TMP_DIRS=()
cleanup() {
  local d
  for d in "${TMP_DIRS[@]+"${TMP_DIRS[@]}"}"; do
    rm -rf "$d"
  done
}
trap cleanup EXIT

new_tmp() {
  local d
  d="$(mktemp -d)"
  TMP_DIRS+=("$d")
  echo "$d"
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

write_env() {
  local file="$1"
  local secret="${2:-}"
  local include_secret="${3:-yes}"
  cat >"$file" <<EOF
SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com
POSTGRES_DB=skillhub
POSTGRES_USER=skillhub
POSTGRES_PASSWORD=strong-postgres-password
SESSION_COOKIE_SECURE=true
BOOTSTRAP_ADMIN_ENABLED=false
SKILLHUB_TRUST_FORWARDED_PROTO=false
SKILLHUB_BUILTIN_SKILLS_ENABLED=true
SKILLHUB_STORAGE_PROVIDER=s3
SKILLHUB_STORAGE_S3_ENDPOINT=https://storage.example.com
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_ACCESS_KEY=release-access-key
SKILLHUB_STORAGE_S3_SECRET_KEY=release-secret-key
SKILLHUB_STORAGE_S3_REGION=us-east-1
SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE=false
SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET=false
EOF
  if [[ "$include_secret" == "yes" ]]; then
    printf 'SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=%s\n' "$secret" >>"$file"
  fi
}

expect_fail() {
  local file="$1"
  local expected="$2"
  local output
  if output="$("$SCRIPT" "$file" 2>&1)"; then
    fail "expected validation to fail for $file"
  fi
  if [[ "$output" != *"$expected"* ]]; then
    fail "expected output to contain '$expected', got: $output"
  fi
}

tmp="$(new_tmp)"

valid_env="$tmp/valid.env"
write_env "$valid_env" "release-download-secret-32-bytes-minimum"
"$SCRIPT" "$valid_env" >/dev/null

disabled_builtin_skills_env="$tmp/disabled-builtin-skills.env"
write_env "$disabled_builtin_skills_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SKILLHUB_BUILTIN_SKILLS_ENABLED=false" >>"$disabled_builtin_skills_env"
"$SCRIPT" "$disabled_builtin_skills_env" >/dev/null

relative_api_base_env="$tmp/relative-api-base.env"
write_env "$relative_api_base_env" "release-download-secret-32-bytes-minimum"
printf 'SKILLHUB_WEB_API_BASE_URL=/skillhub\n' >>"$relative_api_base_env"
"$SCRIPT" "$relative_api_base_env" >/dev/null

sub_path_env="$tmp/sub-path.env"
write_env "$sub_path_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' \
  'SKILLHUB_WEB_BASE_PATH=/skillhub/' \
  'SKILLHUB_WEB_API_BASE_URL=/skillhub' \
  'SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com/skillhub' \
  >"$tmp/sub-path-overrides"
cat "$tmp/sub-path-overrides" >>"$sub_path_env"
"$SCRIPT" "$sub_path_env" >/dev/null

missing_public_sub_path_env="$tmp/missing-public-sub-path.env"
write_env "$missing_public_sub_path_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' \
  'SKILLHUB_WEB_BASE_PATH=/skillhub/' \
  'SKILLHUB_WEB_API_BASE_URL=/skillhub' \
  >>"$missing_public_sub_path_env"
expect_fail "$missing_public_sub_path_env" "SKILLHUB_PUBLIC_BASE_URL path"

# A public URL that merely ends with the base path but serves it under a different path
# (/other/skillhub) must be rejected: suffix match is not enough, the path must be exact.
wrong_public_path_env="$tmp/wrong-public-path.env"
write_env "$wrong_public_path_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' \
  'SKILLHUB_WEB_BASE_PATH=/skillhub/' \
  'SKILLHUB_WEB_API_BASE_URL=/skillhub' \
  'SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com/other/skillhub' \
  >>"$wrong_public_path_env"
expect_fail "$wrong_public_path_env" "must equal SKILLHUB_WEB_BASE_PATH without its trailing slash"

# A query or fragment in SKILLHUB_PUBLIC_BASE_URL corrupts concatenated URLs
# (e.g. ${SKILLHUB_PUBLIC_BASE_URL}/cli/auth). Reject it independently of the
# base path, so even a root deployment (no SKILLHUB_WEB_BASE_PATH) is covered.
public_query_env="$tmp/public-query.env"
write_env "$public_query_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' 'SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com/skillhub?ref=1' \
  >>"$public_query_env"
expect_fail "$public_query_env" "must not contain a query"

public_fragment_env="$tmp/public-fragment.env"
write_env "$public_fragment_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' 'SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com#frag' \
  >>"$public_fragment_env"
expect_fail "$public_fragment_env" "must not contain a query"

# A scheme with no host (https://) passes a naive prefix check but concatenates
# into an invalid URL like https:///cli/auth. Must be rejected.
public_no_host_env="$tmp/public-no-host.env"
write_env "$public_no_host_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' 'SKILLHUB_PUBLIC_BASE_URL=https://' >>"$public_no_host_env"
expect_fail "$public_no_host_env" "must include a host"

# Base path format must be rejected here, matching the runtime entrypoint check,
# rather than passing config validation and only failing at container start.
dot_segment_base_env="$tmp/dot-segment-base.env"
write_env "$dot_segment_base_env" "release-download-secret-32-bytes-minimum"
printf 'SKILLHUB_WEB_BASE_PATH=/foo/../bar/\n' >>"$dot_segment_base_env"
expect_fail "$dot_segment_base_env" "must not contain '.' or '..' path segments"

double_slash_base_env="$tmp/double-slash-base.env"
write_env "$double_slash_base_env" "release-download-secret-32-bytes-minimum"
printf 'SKILLHUB_WEB_BASE_PATH=/foo//bar/\n' >>"$double_slash_base_env"
expect_fail "$double_slash_base_env" "SKILLHUB_WEB_BASE_PATH contains unsupported characters"

missing_trailing_base_env="$tmp/missing-trailing-base.env"
write_env "$missing_trailing_base_env" "release-download-secret-32-bytes-minimum"
printf 'SKILLHUB_WEB_BASE_PATH=/skillhub\n' >>"$missing_trailing_base_env"
expect_fail "$missing_trailing_base_env" "must be '/' or start and end with '/'"

# A base path whose first segment collides with a server Nginx location (/api/,
# /oauth2/, ...) must be rejected: it would shadow the real route and break the app.
for reserved in /api/ /oauth2/ /login/ /assets/ /registry/ /nginx-health/ /.well-known/ /runtime-config.js/ /api/nested/; do
  reserved_base_env="$tmp/reserved-base.env"
  write_env "$reserved_base_env" "release-download-secret-32-bytes-minimum"
  printf 'SKILLHUB_WEB_BASE_PATH=%s\n' "$reserved" >>"$reserved_base_env"
  expect_fail "$reserved_base_env" "reserved by the SkillHub server"
done

# A same-origin API base that disagrees with the web base path routes to the wrong prefix.
mismatch_api_base_env="$tmp/mismatch-api-base.env"
write_env "$mismatch_api_base_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' \
  'SKILLHUB_WEB_BASE_PATH=/skillhub/' \
  'SKILLHUB_WEB_API_BASE_URL=/other' \
  'SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com/skillhub' \
  >>"$mismatch_api_base_env"
expect_fail "$mismatch_api_base_env" "must equal SKILLHUB_WEB_BASE_PATH without its trailing slash"

missing_env="$tmp/missing.env"
write_env "$missing_env" "" no
expect_fail "$missing_env" "SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET is required"

placeholder_env="$tmp/placeholder.env"
write_env "$placeholder_env" "change-me-in-production"
expect_fail "$placeholder_env" "SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET still uses placeholder/default value"

short_env="$tmp/short.env"
write_env "$short_env" "too-short"
expect_fail "$short_env" "SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must be at least 32 characters"

invalid_forwarded_proto_env="$tmp/invalid-forwarded-proto.env"
write_env "$invalid_forwarded_proto_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SKILLHUB_TRUST_FORWARDED_PROTO=yes" >>"$invalid_forwarded_proto_env"
expect_fail "$invalid_forwarded_proto_env" "SKILLHUB_TRUST_FORWARDED_PROTO must be true or false"

invalid_builtin_skills_env="$tmp/invalid-builtin-skills.env"
write_env "$invalid_builtin_skills_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SKILLHUB_BUILTIN_SKILLS_ENABLED=yes" >>"$invalid_builtin_skills_env"
expect_fail "$invalid_builtin_skills_env" "SKILLHUB_BUILTIN_SKILLS_ENABLED must be true or false"

valid_redis_cluster_env="$tmp/valid-redis-cluster.env"
write_env "$valid_redis_cluster_env" "release-download-secret-32-bytes-minimum"
cat >>"$valid_redis_cluster_env" <<'EOF'
SPRING_DATA_REDIS_CLUSTER_NODES=redis-a.example.com:6379,redis-b.example.com:6380
SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS=5
SPRING_DATA_REDIS_SSL_ENABLED=true
EOF
"$SCRIPT" "$valid_redis_cluster_env" >/dev/null

invalid_redis_cluster_node_env="$tmp/invalid-redis-cluster-node.env"
write_env "$invalid_redis_cluster_node_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SPRING_DATA_REDIS_CLUSTER_NODES=redis-a.example.com" >>"$invalid_redis_cluster_node_env"
expect_fail "$invalid_redis_cluster_node_env" "SPRING_DATA_REDIS_CLUSTER_NODES entries must use host:port"

invalid_redis_cluster_port_env="$tmp/invalid-redis-cluster-port.env"
write_env "$invalid_redis_cluster_port_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SPRING_DATA_REDIS_CLUSTER_NODES=redis-a.example.com:65536" >>"$invalid_redis_cluster_port_env"
expect_fail "$invalid_redis_cluster_port_env" "SPRING_DATA_REDIS_CLUSTER_NODES port must be between 1 and 65535"

invalid_redis_redirects_env="$tmp/invalid-redis-redirects.env"
write_env "$invalid_redis_redirects_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS=-1" >>"$invalid_redis_redirects_env"
expect_fail "$invalid_redis_redirects_env" "SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS must be a non-negative integer"

invalid_redis_database_env="$tmp/invalid-redis-database.env"
write_env "$invalid_redis_database_env" "release-download-secret-32-bytes-minimum"
cat >>"$invalid_redis_database_env" <<'EOF'
SPRING_DATA_REDIS_CLUSTER_NODES=redis-a.example.com:6379
SPRING_DATA_REDIS_DATABASE=1
EOF
expect_fail "$invalid_redis_database_env" "SPRING_DATA_REDIS_DATABASE must be 0 when SPRING_DATA_REDIS_CLUSTER_NODES is set"

valid_redis_sentinel_env="$tmp/valid-redis-sentinel.env"
write_env "$valid_redis_sentinel_env" "release-download-secret-32-bytes-minimum"
cat >>"$valid_redis_sentinel_env" <<'EOF'
SPRING_DATA_REDIS_SENTINEL_MASTER=mymaster
SPRING_DATA_REDIS_SENTINEL_NODES=sentinel-a.example.com:26379,sentinel-b.example.com:26379
SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST=false
EOF
"$SCRIPT" "$valid_redis_sentinel_env" >/dev/null

missing_redis_sentinel_nodes_env="$tmp/missing-redis-sentinel-nodes.env"
write_env "$missing_redis_sentinel_nodes_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SPRING_DATA_REDIS_SENTINEL_MASTER=mymaster" >>"$missing_redis_sentinel_nodes_env"
expect_fail "$missing_redis_sentinel_nodes_env" "SPRING_DATA_REDIS_SENTINEL_NODES is required when SPRING_DATA_REDIS_SENTINEL_MASTER is set"

missing_redis_sentinel_master_env="$tmp/missing-redis-sentinel-master.env"
write_env "$missing_redis_sentinel_master_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SPRING_DATA_REDIS_SENTINEL_NODES=sentinel-a.example.com:26379" >>"$missing_redis_sentinel_master_env"
expect_fail "$missing_redis_sentinel_master_env" "SPRING_DATA_REDIS_SENTINEL_MASTER is required when SPRING_DATA_REDIS_SENTINEL_NODES is set"

invalid_redis_sentinel_port_env="$tmp/invalid-redis-sentinel-port.env"
write_env "$invalid_redis_sentinel_port_env" "release-download-secret-32-bytes-minimum"
cat >>"$invalid_redis_sentinel_port_env" <<'EOF'
SPRING_DATA_REDIS_SENTINEL_MASTER=mymaster
SPRING_DATA_REDIS_SENTINEL_NODES=sentinel-a.example.com:70000
EOF
expect_fail "$invalid_redis_sentinel_port_env" "SPRING_DATA_REDIS_SENTINEL_NODES port must be between 1 and 65535"

invalid_redis_sentinel_check_env="$tmp/invalid-redis-sentinel-check.env"
write_env "$invalid_redis_sentinel_check_env" "release-download-secret-32-bytes-minimum"
printf '%s\n' "SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST=yes" >>"$invalid_redis_sentinel_check_env"
expect_fail "$invalid_redis_sentinel_check_env" "SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST must be true or false"

draft_env="$tmp/draft.env"
while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=*)
      printf '%s\n' "SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=release-download-secret-32-bytes-minimum"
      ;;
    *)
      printf '%s\n' "$line"
      ;;
  esac
done <"$REPO_ROOT/.env.release.draft" >"$draft_env"
expect_fail "$draft_env" "POSTGRES_PASSWORD"

echo "validate-release-config-test passed"
