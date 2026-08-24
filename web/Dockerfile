# The frontend output is static and architecture-neutral. Build it on the
# Buildx host so linux/riscv64 does not depend on a target-architecture Node.js
# image; only the multi-architecture Nginx runtime is target-specific.
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG VITE_BASE_PATH=/__SKILLHUB_WEB_BASE_PATH__/
ENV VITE_BASE_PATH=$VITE_BASE_PATH
RUN pnpm build

FROM nginx:alpine
ENV SKILLHUB_TRUST_FORWARDED_PROTO=false
# Record a fixed build-time base so the entrypoint defaults SKILLHUB_WEB_BASE_PATH
# to it and generates matching Nginx routing without repeating the value at runtime.
# Placeholder builds (the default) intentionally write no file and default to '/'.
ARG VITE_BASE_PATH=/__SKILLHUB_WEB_BASE_PATH__/
RUN if [ "$VITE_BASE_PATH" != "/__SKILLHUB_WEB_BASE_PATH__/" ]; then \
      mkdir -p /etc/skillhub && printf '%s' "$VITE_BASE_PATH" > /etc/skillhub/baked-base-path; \
    fi
COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/src/docs/skill.md.template /usr/share/nginx/html/registry/skill.md.template
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY runtime-config.js.template /usr/share/nginx/html/runtime-config.js.template
COPY docker-entrypoint.d/20-base-path.sh /docker-entrypoint.d/20-base-path.sh
COPY docker-entrypoint.d/30-runtime-config.sh /docker-entrypoint.d/30-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/20-base-path.sh /docker-entrypoint.d/30-runtime-config.sh
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://127.0.0.1/nginx-health || exit 1
