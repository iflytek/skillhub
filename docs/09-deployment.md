# skillhub 部署架构与运维

## 1 运行模型

当前仓库只保留两种运行方式：

- 开发环境：`make dev-all`
  - 前端和后端运行在宿主机
  - `docker-compose.yml` 只负责 PostgreSQL、Redis、MinIO
- 单机交付环境：`docker compose --env-file .env.release -f compose.release.yml up -d`
  - 前端和后端都运行在容器内
- 使用 GitHub Actions 发布到 GHCR 的镜像
- 默认发布多架构镜像：`server` / `web` 覆盖 `linux/amd64`、`linux/arm64` 与
  `linux/riscv64`，`scanner` 暂保持 `linux/amd64` 与 `linux/arm64`
  - PostgreSQL、Redis 与应用容器一起通过 Compose 启动

不再维护本地构建整套 demo 容器的中间模式，也不再保留 `docker-compose.prod.yml`。

## 2 单机交付拓扑

```
┌──────────────┐
│ Browser / CLI│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Web/Nginx  │  published image
└──────┬───────┘
       │ /api/*
       ▼
┌──────────────┐
│ Spring Boot  │  published image
└───┬────┬─────┘
    │    │
    ▼    ▼
 PostgreSQL  Redis
```

说明：
- Web 容器提供静态资源，并将 `/api/*`、`/oauth2/*`、`/.well-known/*` 反代到后端
- 后端默认运行 `docker` profile，不再启用本地 mock 登录
- PostgreSQL / Redis 默认只绑定 `127.0.0.1`
- 对象存储推荐使用外部 S3 / OSS，通过环境变量注入

## 3 Profile 约定

| Profile | 用途 | 说明 |
|---------|------|------|
| `local` | 本地源码开发能力 | 启用 mock 登录、开发种子账号、调试日志 |
| `docker` | 容器运行时能力 | 启用容器运行时相关能力，不会自动打开首登管理员 |

单机交付环境使用 `SPRING_PROFILES_ACTIVE=docker`，原因如下：

- 生产环境不应开启 `X-Mock-User-Id` 这一类本地开发旁路能力
- 容器环境仍然保留 `docker` profile 的运行时能力，首个管理员账户初始化不依赖该 profile，通过环境变量控制
- 数据库、Redis、OSS、站点公网地址全部改为环境变量优先

如需启用首登管理员，来源于以下环境变量：

- `BOOTSTRAP_ADMIN_ENABLED=true`（发布模板默认已开启）
- `BOOTSTRAP_ADMIN_USERNAME`（默认 `admin`）
- `BOOTSTRAP_ADMIN_PASSWORD`（默认 `ChangeMe!2026`）

建议：

- 生产环境务必修改 `BOOTSTRAP_ADMIN_PASSWORD`（`validate-release-config.sh` 会拒绝默认值）
- 完成首次登录后立即修改管理员密码
- 如果已有外部身份源，通常不需要启用 bootstrap admin
- `SKILLHUB_PUBLIC_BASE_URL` 应配置为最终 HTTPS 域名，避免 OAuth / Cookie / 设备码链接异常

## 4 开发环境

开发入口保持不变：

```bash
make dev-all
```

行为：

- `docker-compose.yml` 启动 PostgreSQL、Redis、MinIO
- `server` 在宿主机通过 Maven Wrapper 启动
- `web` 在宿主机通过 Vite 启动

常用命令：

```bash
make dev
make dev-all
make dev-down
make dev-all-down
make dev-all-reset
```

## 5 单机交付环境

### 5.1 启动

```bash
cp .env.release.example .env.release
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d
```

默认访问地址：

- Web UI: `SKILLHUB_PUBLIC_BASE_URL`
- Backend API: `http://localhost:8080`

### 5.2 连接外部 Redis Cluster

发布 Compose 默认仍使用内置单机 Redis。连接外部 Redis Cluster 时，在
`.env.release` 中设置标准 Spring Boot 配置，不需要额外的模式开关：

```dotenv
SPRING_DATA_REDIS_CLUSTER_NODES=redis-0.example.com:6379,redis-1.example.com:6379,redis-2.example.com:6379
SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS=5
SPRING_DATA_REDIS_USERNAME=skillhub
SPRING_DATA_REDIS_PASSWORD=replace-with-secret
SPRING_DATA_REDIS_SSL_ENABLED=true
SPRING_DATA_REDIS_CONNECT_TIMEOUT=5s
SPRING_DATA_REDIS_TIMEOUT=3s
```

Cluster 节点返回给客户端的所有地址必须能从 `server` 容器访问。Redis Cluster
只支持数据库 `0`；不要为 Cluster 设置非零的
`SPRING_DATA_REDIS_DATABASE`。配置 Cluster 节点后，Spring Boot 自动忽略单机
`host`/`port`，Compose 中的内置 Redis 容器仍会启动，但不会被 Server 使用。

对真实 Cluster 运行功能检查：

```bash
REDIS_CLUSTER_TEST_NODES=redis-0.example.com:6379,redis-1.example.com:6379,redis-2.example.com:6379 \
REDIS_CLUSTER_TEST_USERNAME=skillhub \
REDIS_CLUSTER_TEST_PASSWORD=replace-with-secret \
make test-redis-cluster
```

该检查覆盖 Spring Data 读写、Spring Session 保存/读取/删除和 Redisson Stream。

### 5.3 连接外部 Redis Sentinel

Sentinel 使用标准 Spring Boot 配置。数据节点和 Sentinel 可以使用不同 ACL：

```dotenv
SPRING_DATA_REDIS_SENTINEL_MASTER=mymaster
SPRING_DATA_REDIS_SENTINEL_NODES=sentinel-0.example.com:26379,sentinel-1.example.com:26379,sentinel-2.example.com:26379
SPRING_DATA_REDIS_USERNAME=skillhub
SPRING_DATA_REDIS_PASSWORD=replace-with-data-node-secret
SPRING_DATA_REDIS_SENTINEL_USERNAME=sentinel-user
SPRING_DATA_REDIS_SENTINEL_PASSWORD=replace-with-sentinel-secret
SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST=true
```

Sentinel 配置优先于 Cluster 和单机 `host`/`port`。在 Kubernetes 等 Sentinel
返回地址与客户端入口不一致的环境中，可以将
`SKILLHUB_REDIS_SENTINEL_CHECK_SENTINELS_LIST` 设为 `false`。

### 5.4 关键文件

- `compose.release.yml`
  - 使用发布镜像，不在用户机器上执行本地构建
  - 负责拉起 PostgreSQL、Redis、server、web
  - PostgreSQL、Redis 默认只绑定到 `127.0.0.1`
  - Web 和后端都支持运行时环境变量注入，不需要为每个环境重建镜像
- `.env.release.example`
  - 运行时变量模板
  - 包含镜像名、镜像版本、端口、数据库凭证、外部 OSS、站点公网地址和首登管理员参数
- `scripts/validate-release-config.sh`
  - 在启动前校验 `.env.release`
  - 可提前拦截占位值、URL 格式错误、缺失的 OSS 凭据、危险的明文默认值

### 5.5 镜像标签约定

- `edge`
  - `main` 分支最新构建
  - 用于内部持续验证
- `vX.Y.Z`
  - 对应 Git tag
  - 用于稳定版本交付
- `latest`
  - 仅在语义化版本 tag 发布时更新

推荐：

- 默认快速启动：`SKILLHUB_VERSION=latest`
- 团队内部试用：`SKILLHUB_VERSION=edge`
- 对外演示或严格可复现环境：固定为某个 `vX.Y.Z`

## 6 GitHub Actions 发布流程

发布工作流文件：`.github/workflows/publish-images.yml`

触发条件：

- `release.published`
- 手动 `workflow_dispatch`

流程：

1. 检出代码
2. 登录 GHCR
3. 分别构建 `server/Dockerfile` 与 `web/Dockerfile`
4. 推送镜像：
   - `ghcr.io/iflytek/skillhub-server`
   - `ghcr.io/iflytek/skillhub-web`
5. 写入 `edge` / `vX.Y.Z` / `latest` / `sha-*` 标签
6. 同时发布多架构 manifest：`server` / `web` 覆盖 `linux/amd64`、`linux/arm64` 与
   `linux/riscv64`，`scanner` 暂保持 `linux/amd64` 与 `linux/arm64`

## 7 配置管理

### 7.1 请求限流配置

限流默认开启。未配置分类覆盖时，各接口使用代码中 `@RateLimit` 声明的默认值，现有部署无需调整。

可通过环境变量关闭全部限流，或按分类覆盖额度和时间窗口：

```bash
SKILLHUB_RATELIMIT_ENABLED=false
SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_ANONYMOUS=100
SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_AUTHENTICATED=300
SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_WINDOW_SECONDS=60
```

支持的配置字段为 `authenticated`、`anonymous` 和 `window-seconds`。分类名称来自接口的
`@RateLimit(category = "...")`，例如 `search`、`download`、`publish` 和 `resolve`。只设置其中一个字段时，
其他字段仍回退到接口默认值。

Docker Compose 用户需要显式传入变量，宿主机环境变量不会自动注入容器：

```yaml
services:
  server:
    environment:
      SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_ANONYMOUS: "100"
      SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_AUTHENTICATED: "300"
      SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_WINDOW_SECONDS: "60"
```

修改后重启 server 容器生效。超过额度时接口返回 HTTP `429`；该配置只调整阈值，不改变 Redis 限流算法或响应格式。

前端运行时配置通过 `web/runtime-config.js.template` 注入。与认证兼容层相关的新变量如下：

- `SKILLHUB_WEB_AUTH_DIRECT_ENABLED`
  - 是否在前端打开账号密码兼容接入层
  - 默认应为 `false`
- `SKILLHUB_WEB_AUTH_DIRECT_PROVIDER`
  - 前端调用 `/api/v1/auth/direct/login` 时使用的 provider，例如 `private-sso`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED`
  - 是否在前端打开企业 SSO 被动会话兼容入口
  - 默认应为 `false`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER`
  - 前端调用 `/api/v1/auth/session/bootstrap` 时使用的 provider，例如 `private-sso`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO`
  - 是否在登录页加载后自动尝试一次 bootstrap
  - 建议私有版初期保持 `false`

注意：

- 前端密码兼容层打开之前，后端仍必须同步打开 `skillhub.auth.direct.enabled=true`
- 前端开关打开之前，后端仍必须同步打开 `skillhub.auth.session-bootstrap.enabled=true`
- 前后端任一侧未开启，都不会破坏原有登录方式；只会使该兼容入口不可用或不显示

开发环境：

- 本地命令与 `docker-compose.yml`
- 非敏感默认值可直接落库或写入本地配置

单机交付环境：

- 使用 `.env.release` 管理 Compose 变量
- 如果 GHCR 包保持私有，用户需要先 `docker login ghcr.io`
- 推荐将敏感变量放入 CI/CD Secret 或主机上的受控 `.env.release`
- 外部对象存储通过 `SKILLHUB_STORAGE_S3_*` 注入
- 前端反代和运行时 API 地址通过 `SKILLHUB_API_UPSTREAM` / `SKILLHUB_WEB_API_BASE_URL` 注入
- `SKILLHUB_TRUST_FORWARDED_PROTO` 默认保持 `false`。只有 Web 容器仅能经由可信
  TLS 终止代理访问，且该代理会覆盖客户端传入的 `X-Forwarded-Proto` 时才设为
  `true`；否则客户端可伪造协议并影响 OAuth 回调、重定向和安全 Cookie 判断
- 如果通过网关部署在 `/skillhub/` 等子路径，需同时配置：
  - `SKILLHUB_WEB_BASE_PATH=/skillhub/`
  - `SKILLHUB_WEB_API_BASE_URL=/skillhub`
  - `SKILLHUB_PUBLIC_BASE_URL=https://example.com/skillhub`
  网关可以在转发到 Web 容器前将该前缀重写掉，但公网 URL 仍必须保留前缀，确保 OAuth、CLI 和 registry 链接正确。
- 如果要开放真实登录，再补充 `OAUTH2_GITHUB_CLIENT_ID` / `OAUTH2_GITHUB_CLIENT_SECRET`
- 如果要启用密码重置验证码邮件，参见：`docs/19-smtp-password-reset-email-setup.md`

## 8 OIDC 登录配置

SkillHub 复用 Spring Security OAuth2 Client 的 OIDC 支持。前端不需要单独
配置回调页；登录页会从 `/api/v1/auth/methods` 读取后端暴露的
`OAUTH_REDIRECT` 方法并跳转到 `/oauth2/authorization/{registrationId}`。

生产环境接入 OIDC 时，为后端增加一组 OAuth2 client registration 配置即可。
下面以 `oidc` 作为 registration id：

```bash
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID=replace-me
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET=replace-me
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_PROVIDER=oidc
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_AUTHORIZATION_GRANT_TYPE=authorization_code
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_REDIRECT_URI={baseUrl}/login/oauth2/code/{registrationId}
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE=openid,profile,email
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_NAME=OIDC
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI=https://idp.example.com/realms/skillhub
```

要接入多个 OIDC IdP，使用不同 registration id，例如 `okta`、`keycloak`，
并把上面的环境变量中的 `OIDC` 替换为对应大写 id。registration id 会作为
`identity_binding.provider_code`，请保持稳定。

> **警告：Registration ID 冲突**
>
> 每个 OIDC 提供商必须使用唯一的 registration ID。Registration ID 作为
> `identity_binding.provider_code` 存储在数据库中，用于将外部身份映射到平台
> 用户。如果两个不同的 IdP 使用了相同的 registration ID（例如都使用 `oidc`），
> 会导致不同 IdP 的用户 `sub` 值空间混用，可能出现身份绑定错误或账户冲突。
>
> 建议使用有意义的 registration ID，例如 `okta`、`keycloak`、`azure-ad`，
> 而不是通用的 `oidc`。一旦投入使用，不要更改 registration ID，否则现有用户
> 将无法登录。

Docker Compose 发布模板默认只透传常用变量。若使用 OIDC，请通过 compose
override 或部署平台环境变量把上述 `SPRING_SECURITY_*` 变量注入 `server`
容器。Kubernetes 部署同理，将这些变量放入 `backend-deployment.yaml` 的
`server` 容器环境变量或统一的配置管理系统中。

## 9 裸金属上线清单

推荐顺序：

1. 准备服务器基础环境
   - 安装 Docker Engine 与 Docker Compose Plugin
   - 配置公网 HTTPS 入口，确保最终访问域名已经确定
   - 打开 `80` / `443`，避免直接暴露 `5432` / `6379`
2. 填写 `.env.release`
   - `SKILLHUB_PUBLIC_BASE_URL` 填最终 HTTPS 域名，且不要带尾部 `/`；子路径部署时必须包含外部路径前缀
   - `SKILLHUB_STORAGE_PROVIDER=s3`
   - 按云厂商 OSS / S3 兼容参数填写 `SKILLHUB_STORAGE_S3_*`
   - 设置非默认的 `POSTGRES_PASSWORD`
   - 模板默认已开启首登管理员，务必将 `BOOTSTRAP_ADMIN_PASSWORD` 改为强密码
3. 启动前校验
   - 运行 `make validate-release-config`
   - 确认没有 `replace-me`、`change-this-*`、`ChangeMe!2026` 之类的占位值
4. 首次启动
   - 运行 `docker compose --env-file .env.release -f compose.release.yml up -d`
   - 检查 `docker compose --env-file .env.release -f compose.release.yml ps`
   - 检查 `curl -i http://127.0.0.1:8080/actuator/health`
5. 首登收尾
   - 仅在启用了 `BOOTSTRAP_ADMIN_ENABLED=true` 时，使用 `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` 登录
   - 立即修改管理员密码
   - 如果后续完全走 OAuth，可将 `BOOTSTRAP_ADMIN_ENABLED=false`

## 10 可观测性

| 维度 | 方案 |
|------|------|
| 健康检查 | `web/nginx-health`、`server/actuator/health` |
| 请求关联 | 响应头和日志中的 `X-Request-Id` / `request.id` |
| 日志 | 文本或 ECS 风格 JSON，均输出到容器 stdout / stderr |
| Trace | `none`、Micrometer + OTel SDK、或外部 Java Agent 三选一 |
| 指标 | Spring Boot Actuator；Prometheus 是可选后端，不是 Trace 前置条件 |

### 10.1 通用配置

默认配置不要求 Collector、SkyWalking 或 Elasticsearch：

```dotenv
SKILLHUB_TRACING_MODE=none
SKILLHUB_LOG_FORMAT=json
SKILLHUB_SERVICE_VERSION=v0.2.15
SKILLHUB_SERVICE_ENVIRONMENT=production
```

发布 Compose 默认使用 ECS 风格 JSON，由 Filebeat、Fluent Bit 或容器平台采集 stdout。
本地源码开发仍可使用 `SKILLHUB_LOG_FORMAT=text`。SkillHub 不直接连接 Elasticsearch。
JSON 日志使用以下稳定字段：

- `request.id`：SkillHub 请求、响应和审计关联 ID。
- `trace.id`、`span.id`：当前存在有效 Trace 时输出。
- `service.name`、`service.version`、`service.environment`。

`SKILLHUB_LOG_ASYNC_QUEUE_SIZE` 默认是 `1024`。JSON 日志队列是有界且非阻塞的；采集端
阻塞时允许丢弃日志以保护业务线程，数据库中的 `audit_log` 仍是审计事实来源。

### 10.2 三种 Tracing 模式

三种模式只能选择一种，切换后需要重启：

| 模式 | 适用场景 | 必需配置 |
|------|----------|----------|
| `none` | 不部署链路追踪 | `SKILLHUB_TRACING_MODE=none` |
| `otel-sdk` | 厂商中立 OTLP/Collector | 模式、采样率；需要导出时再配置 endpoint |
| `external-agent` | 使用 SkyWalking Agent 原生能力 | 模式、唯一的外部 Agent；不得配置 OTLP endpoint |

OTel SDK 模式的最小配置：

```dotenv
SKILLHUB_TRACING_MODE=otel-sdk
SKILLHUB_LOG_FORMAT=json
SKILLHUB_TRACING_SAMPLING_PROBABILITY=0.1
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces
SKILLHUB_OTLP_TIMEOUT=5s
SKILLHUB_OTLP_COMPRESSION=gzip
```

未设置 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 时，`otel-sdk` 仍可建立进程内 Trace，但不会
创建 OTLP Exporter，也不会尝试连接默认地址。`none` 或 `external-agent` 模式配置
endpoint 会启动失败。

External Agent 模式的应用侧配置：

```dotenv
SKILLHUB_TRACING_MODE=external-agent
SKILLHUB_LOG_FORMAT=json
```

部署平台还必须通过 JVM 启动参数挂载且只挂载一个 Agent。SkillHub 无法可靠识别任意
Java Agent，因此上线前应检查实际 `JAVA_TOOL_OPTIONS` 或容器启动命令，确认没有同时启用
OTel Agent、SkyWalking Agent 和应用内 `otel-sdk`。SkyWalking Agent 模式可以通过官方
Logback Toolkit 输出 `trace.id`；`span.id` 是否可用取决于 Agent 版本。

### 10.3 OTel Collector 接入 SkyWalking

下面是只转发 Trace 的最小 Collector 配置：

```yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch: {}

exporters:
  otlp/skywalking:
    endpoint: skywalking-oap:11800
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlp/skywalking]
```

SkyWalking OAP 10.3 还需要启用 OTLP Trace handler、Zipkin receiver 和 Zipkin query：

```dotenv
SW_OTEL_RECEIVER_ENABLED_HANDLERS=otlp-traces
SW_RECEIVER_ZIPKIN=default
SW_QUERY_ZIPKIN=default
```

应用使用 Collector 的 OTLP/HTTP `4318` 端口，Collector 使用 OAP 的 OTLP/gRPC
`11800` 端口。生产环境应按网络边界配置 TLS；上例中的 `insecure: true` 只适用于受控的
容器内部网络。

SkyWalking 10.3 会把 OTLP Trace 转换为 Zipkin Trace，并通过 Zipkin Query/Lens 查询。
这条路径不提供 SkyWalking Java Agent 的完整原生拓扑、慢 SQL 和 Profiling 能力。需要
这些能力时使用 `external-agent`，不要同时启用 `otel-sdk`。

### 10.4 日志与 Trace 联查

JSON 日志由采集器写入 Elasticsearch 后，在 Kibana 通过 `trace.id` 查询；同一个
`trace.id` 可在 SkyWalking 的 Zipkin Query/Lens 或 Agent 原生查询界面中定位调用链。
`request.id` 始终可以用于 SkillHub 内部日志和审计关联。

当采样率小于 `1.0` 时，日志仍是全量输出，因此部分日志虽有请求关联信息，但在
SkyWalking 中没有被保留的 Trace。这是头部采样的预期行为。

### 10.5 回滚

遇到观测后端异常时：

1. 将 `SKILLHUB_TRACING_MODE` 改为 `none`。
2. 删除 `MANAGEMENT_OTLP_TRACING_ENDPOINT`。
3. 需要进一步降低日志开销时，将 `SKILLHUB_LOG_FORMAT` 改为 `text`。
4. 滚动重启 Server。

关闭 Trace 和 JSON 日志不会改变请求、数据库或异步任务的业务语义。

开发者接入统一标准的最小步骤、内部/外部 HTTP Client 传播边界和扩展点见：
[可观测性开发者接入指南](./observability-developer-guide.md)。

## 11 安全扫描服务

如果要启用 `skill-scanner` 后端链路，当前仓库建议按下面的方式部署：

- 本地共享目录场景可以使用 `local` 模式
- Kubernetes 或分离部署场景应使用 `upload` 模式

当前 `deploy/k8s` 已按分离部署建模，因此推荐：

- `SKILLHUB_SECURITY_SCANNER_ENABLED=true`
- `SKILLHUB_SECURITY_SCANNER_URL=http://skillhub-scanner:8000`
- `SKILLHUB_SECURITY_SCANNER_MODE=upload`

相关文件：

- `deploy/k8s/scanner-deployment.yaml`
- `deploy/k8s/services.yaml`
- `deploy/k8s/backend-deployment.yaml`
- `scripts/verify-scanner.sh`
- `docs/security-scanning.md`

## 12 数据迁移

Flyway 仍是唯一 schema 变更入口：

- 路径：`server/skillhub-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`
- 启动策略：应用容器启动时自动执行迁移
