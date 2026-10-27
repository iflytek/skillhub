FROM --platform=$BUILDPLATFORM golang:1.27.3-alpine3.24 AS build

WORKDIR /src
COPY go.mod ./
COPY cmd/ ./cmd/
COPY internal/ ./internal/

ARG TARGETOS
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=${TARGETOS:-linux} GOARCH=${TARGETARCH:-amd64} \
    go build -trimpath -ldflags="-s -w" -o /github-sync ./cmd/github-sync

FROM alpine:3.24.2
RUN apk add --no-cache ca-certificates git tini && \
    addgroup -S -g 10001 skillhub && \
    adduser -S -D -u 10001 -G skillhub skillhub && \
    mkdir -p /var/lib/skillhub-github-sync && \
    chown skillhub:skillhub /var/lib/skillhub-github-sync

COPY --from=build /github-sync /usr/local/bin/github-sync

USER 10001:10001
ENV SKILLHUB_GITHUB_SYNC_TEMP_ROOT=/var/lib/skillhub-github-sync
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -Y off -qO- http://127.0.0.1:8090/health/live >/dev/null || exit 1
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/github-sync"]
