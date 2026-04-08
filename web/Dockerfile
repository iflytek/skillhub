FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/node-skillhub:22-alpine AS build
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ENV VITE_BASE_PATH=/skillhub-web/
RUN pnpm build

FROM jsessh-registry.cn-shanghai.cr.aliyuncs.com/base-images/nginx-skillhub:alpine

# 限制 envsubst 只替换 SKILLHUB_ 开头的变量，避免覆盖 nginx 的 $uri $host 等变量
# 设置默认值，部署时通过环境变量覆盖
ENV SKILLHUB_API_UPSTREAM=${SKILLHUB_API_UPSTREAM:-http://skillhub-server-dev:8000}
ENV SKILLHUB_WEB_API_BASE_URL=${SKILLHUB_WEB_API_BASE_URL:-http://sz-api.indusmind.local/skillhub-server}
ENV SKILLHUB_PUBLIC_BASE_URL=${SKILLHUB_PUBLIC_BASE_URL:-http://sz-api.indusmind.local/skillhub-web}

COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/src/docs/skill.md.template /usr/share/nginx/html/registry/skill.md.template
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY runtime-config.js.template /usr/share/nginx/html/runtime-config.js.template
COPY docker-entrypoint.d/30-runtime-config.sh /docker-entrypoint.d/30-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/30-runtime-config.sh
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://127.0.0.1:8000/nginx-health || exit 1
