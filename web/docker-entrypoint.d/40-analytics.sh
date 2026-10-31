#!/bin/sh
# Optional analytics must never fail the normal Web startup.
set -eu
root="${SKILLHUB_WEB_ROOT:-/usr/share/nginx/html}"
proxy="${SKILLHUB_NGINX_ANALYTICS_CONFIG:-/etc/nginx/skillhub-analytics.conf}"
validator="${SKILLHUB_ANALYTICS_VALIDATOR:-/etc/skillhub/analytics-config.awk}"
if [ ! -f "$validator" ]; then
  validator="$(dirname "$0")/../analytics-config.awk"
fi
raw="${SKILLHUB_WEB_ANALYTICS_CONFIG:-}"
printf '%s\n' '# Closed: the reserved prefix inherits return 404 from the server.' > "$proxy"
printf '%s\n' 'window.__SKILLHUB_RUNTIME_CONFIG__.analytics = undefined;' > "$root/analytics-runtime.js"
status=disabled
if [ -n "$raw" ]; then
  if [ "${#raw}" -gt 65536 ]; then
    status='invalid config'
  else
    status=$(printf '%s' "$raw" | LC_ALL=C awk -f "$validator") || :
  fi
fi
case "$status" in
  'invalid '*) printf '[analytics] %s: invalid configuration; disabled\n' "${status#invalid }" >&2 ;;
  enabled|denied)
    upstream="${SKILLHUB_WEB_ANALYTICS_UPSTREAM:-}"
    # Strict origin grammar; never interpolate unvalidated input into Nginx.
    safe_chars=true
    case "$upstream" in *[!A-Za-z0-9.:/\[\]-]*) safe_chars=false ;; esac
    if [ "$safe_chars" = true ] && printf '%s\n' "$upstream" | LC_ALL=C grep -Eq '^https?://([A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?|\[[0-9A-Fa-f:]+\])(:[0-9]{1,5})?$'; then
      authority=${upstream#*://}
      host=$authority
      port=''
      case "$authority" in
        \[*\]*) host=${authority%%\]*}; host=${host#\[}; suffix=${authority#*\]}; port=${suffix#:} ;;
        *:*) host=${authority%:*}; port=${authority##*:} ;;
      esac
      valid=true
      if ! printf '%s\n' "$host" | LC_ALL=C awk '
        /:/ {
          if ($0 ~ /:::/ || $0 ~ /::.*::/) exit 1
          n=split($0,a,":"); used=0
          for(i=1;i<=n;i++) if(a[i]!="") {
            if(length(a[i])>4 || a[i] ~ /[^0-9a-fA-F]/) exit 1
            used++
          }
          if(index($0,"::")) exit used<8 ? 0 : 1
          exit used==8 && n==8 ? 0 : 1
        }
        {
          if(length($0)>253) exit 1
          n=split($0,a,".")
          for(i=1;i<=n;i++) if(length(a[i])>63 || a[i] !~ /^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?$/) exit 1
          if($0 ~ /^[0-9.]+$/) {
            if(n!=4) exit 1
            for(i=1;i<=n;i++) if(a[i]+0>255) exit 1
          }
        }'; then valid=false; fi
      if [ -n "$port" ] && { [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; }; then valid=false; fi
    else
      valid=false
    fi
    if [ "$valid" = true ]; then
      encoded=$(printf '%s' "$raw" | base64 | tr -d '\n\r')
      # Base64 alphabet cannot terminate this JS string. JSON is parsed only as
      # data; quotes, newlines, U+2028 and closing tags cannot become executable.
      printf 'try { window.__SKILLHUB_RUNTIME_CONFIG__.analytics = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob("%s"), function(c) { return c.charCodeAt(0); }))); } catch (_) { console.error("[analytics] config: invalid JSON; disabled"); }\n' "$encoded" > "$root/analytics-runtime.js"
      printf 'set $skillhub_analytics_upstream "%s";\n' "$upstream" > "$proxy"
      for endpoint in script.js api/send; do
        method=GET
        [ "$endpoint" != api/send ] || method=POST
        printf 'location = /_skillhub-analytics/%s {\n    limit_except %s { deny all; }\n    proxy_pass $skillhub_analytics_upstream/%s;\n    proxy_set_header Host "%s";\n    proxy_set_header Cookie "";\n    proxy_set_header Authorization "";\n    proxy_set_header Referer "";\n    proxy_hide_header Set-Cookie;\n    proxy_ssl_verify on;\n    proxy_ssl_server_name on;\n    proxy_ssl_name %s;\n    proxy_ssl_trusted_certificate /etc/ssl/certs/ca-certificates.crt;\n    proxy_connect_timeout 3s;\n    proxy_read_timeout 3s;\n    proxy_cache off;\n}\n' "$endpoint" "$method" "$endpoint" "$authority" "$host" >> "$proxy"
      done
    else
      printf '%s\n' '[analytics] upstream: invalid origin; disabled' >&2
    fi
    ;;
  disabled) ;;
  *) printf '%s\n' '[analytics] config: validation failed; disabled' >&2 ;;
esac
cat "$root/analytics-runtime.js" >> "$root/runtime-config.js"
# The fragment has been appended to the existing no-store runtime config.
rm -f "$root/analytics-runtime.js"
