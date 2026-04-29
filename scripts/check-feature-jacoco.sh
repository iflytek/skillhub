#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <jacoco-csv>" >&2
  exit 2
fi

csv_path="$1"
if [ ! -f "$csv_path" ]; then
  echo "JaCoCo aggregate report not found: $csv_path" >&2
  exit 1
fi

patterns=(
  "com.iflytek.skillhub.auth.uass."
  "com.iflytek.skillhub.auth.uass.config."
  "com.iflytek.skillhub.auth.uass.store."
  "com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry"
  "com.iflytek.skillhub.controller.UassAuthController"
  "com.iflytek.skillhub.config.SessionCookieConfiguration"
  "com.iflytek.skillhub.dto.UassLoginStatusResponse"
  "com.iflytek.skillhub.dto.UassLoginUrlResponse"
  "com.iflytek.skillhub.service.AuthMethodCatalog"
)

matched=0
failures=()

while IFS=, read -r _group package class _instr_missed _instr_covered _branch_missed _branch_covered line_missed line_covered _complexity_missed _complexity_covered _method_missed _method_covered; do
  if [ "$package" = "PACKAGE" ]; then
    continue
  fi
  fqcn="${package}.${class}"
  include=0
  for pattern in "${patterns[@]}"; do
    if [[ "$fqcn" == "$pattern"* ]]; then
      include=1
      break
    fi
  done
  if [ "$include" -eq 0 ]; then
    continue
  fi
  matched=1
  if [ "${line_missed}" != "0" ]; then
    failures+=("${fqcn} (line_missed=${line_missed}, line_covered=${line_covered})")
  fi
done < "$csv_path"

if [ "$matched" -eq 0 ]; then
  echo "No feature-scope coverage rows matched in ${csv_path}" >&2
  exit 1
fi

if [ "${#failures[@]}" -ne 0 ]; then
  echo "UASS feature coverage gate failed:" >&2
  printf ' - %s\n' "${failures[@]}" >&2
  exit 1
fi

echo "UASS feature coverage gate passed for $((${#patterns[@]})) include patterns."
