# skillhub 部署架构与运维

## 1 K8s 部署拓扑

```
                    ┌─────────────┐
                    │   Ingress   │
                    │  (Nginx)    │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │ /api/*                  │ /*
              ▼                         ▼
    ┌──────────────────┐    ┌──────────────────┐
    │  Spring Boot     │    │  Nginx / CDN     │
    │  replicas: 2+    │    │  静态资源          │
    └────────┬─────────┘    └──────────────────┘
             │
    ┌────────┴──────────────────────┐
    │            │                  │
    ▼            ▼                  ▼
┌────────┐  ┌────────┐    ┌──────────────┐
│ PostgreSQL│  │ Redis  │    │ S3 / MinIO   │
│ (主从)    │  │        │    │              │
└────────┘  └────────┘    └──────────────┘
```

## 2 服务配置

- 无状态设计，所有状态存储在 PostgreSQL / Redis / S3
- 健康检查：`/actuator/health`（liveness + readiness 分离）
- 优雅停机：`spring.lifecycle.timeout-per-shutdown-phase=30s`
- JVM：`-XX:MaxRAMPercentage=75.0`

## 3 环境 Profile

| Profile | 用途 | 特点 |
|---------|------|------|
| `local` | 本地开发 | Docker Compose 一键启动（PostgreSQL/Redis/MinIO），Mock OAuth（见下方说明） |
| `dev` | 开发环境 | 共享基础设施，GitHub OAuth 测试应用 |
| `staging` | 预发布 | 与生产同构 |
| `prod` | 生产 | 多 Pod，完整基础设施 |

### 本地开发 Mock 登录

`local` profile 下提供两种开发登录方式：

1. **MockAuthFilter**（默认）：通过 `X-Mock-User-Id` Header 模拟登录，自动创建 Session，无需真实 OAuth 流程
2. **GitHub OAuth 测试应用**：配置 `OAUTH2_GITHUB_CLIENT_ID` / `OAUTH2_GITHUB_CLIENT_SECRET` 后可走真实 OAuth 流程（GitHub 支持 `http://localhost` 回调）

MockAuthFilter 仅在 `local` profile 激活，通过 `@Profile("local")` 注解保证不会泄漏到其他环境。

### Docker Compose 说明

当前推荐的本地启动入口是 `make dev-all`。Docker Compose 在当前项目里主要承担本地依赖服务启动。

常用命令：

```bash
make dev-all
make dev-all-down
make dev-all-reset
```

#### docker-compose.yml — 本地开发（仅依赖服务）

本地开发时前后端在宿主机运行，Docker Compose 只拉起依赖服务：

```yaml
# docker-compose.yml（项目根目录）
services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: skillhub_dev
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"    # MinIO Console
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

volumes:
  postgres_data:
  minio_data:
```

生产环境文档不再提供 Compose 一键部署入口。当前仓库只保留本地开发所需的 `docker-compose.yml`，正式部署以镜像构建 + K8s 编排为准。

#### 前后端 Dockerfile

后端 Dockerfile（`server/Dockerfile`）：
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY skillhub-app/pom.xml skillhub-app/
COPY skillhub-domain/pom.xml skillhub-domain/
COPY skillhub-auth/pom.xml skillhub-auth/
COPY skillhub-search/pom.xml skillhub-search/
COPY skillhub-storage/pom.xml skillhub-storage/
COPY skillhub-infra/pom.xml skillhub-infra/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/skillhub-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

前端 Dockerfile（`web/Dockerfile`）：
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

前端 Nginx 配置（`web/nginx.conf`）：
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 回调反向代理
    location /oauth2/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /login/oauth2/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Well-known 发现端点
    location /.well-known/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
    }
}
```

### Spring Boot 配置文件分层

```
server/skillhub-app/src/main/resources/
├── application.yml              # 公共配置（所有 profile 共享）
├── application-local.yml        # 本地开发（Docker Compose 服务地址）
├── application-dev.yml          # 开发环境
├── application-staging.yml      # 预发布
└── application-prod.yml         # 生产
```

`application.yml`（公共配置）：
```yaml
spring:
  application:
    name: skillhub
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate    # 由 Flyway 管理 schema，Hibernate 仅校验
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  shutdown: graceful

spring.lifecycle.timeout-per-shutdown-phase: 30s
```

`application-local.yml`（本地开发，对应 Docker Compose）：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/skillhub
    username: skillhub
    password: skillhub_dev
  data:
    redis:
      host: localhost
      port: 6379
  jpa:
    show-sql: true

skillhub:
  storage:
    type: s3
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: skillhub
    region: us-east-1
  access-policy:
    mode: OPEN    # 本地开发默认开放准入
```

`application-prod.yml`（生产环境，凭证从环境变量/K8s Secret 注入）：
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
  jpa:
    show-sql: false

skillhub:
  storage:
    type: s3
    endpoint: ${S3_ENDPOINT}
    access-key: ${S3_ACCESS_KEY}
    secret-key: ${S3_SECRET_KEY}
    bucket: ${S3_BUCKET:skillhub}
    region: ${S3_REGION:us-east-1}
```

### 本地开发启动流程

```bash
# 一键启动依赖 + 后端 + 前端
make dev-all
```

启动后可直接访问：

- Web UI: `http://localhost:3000`
- Backend API: `http://localhost:8080`

停止：

```bash
make dev-all-down
```

如需分步启动：

```bash
make dev          # 仅依赖服务
make dev-server   # 仅后端
make dev-web      # 仅前端
```

### Makefile 命令

```bash
make dev              # 仅启动本地依赖服务
make dev-all          # 一键启动本地依赖 + 后端 + 前端
make dev-down         # 停止本地依赖服务
make dev-all-down     # 停止本地依赖 + 后端 + 前端
make build            # 构建后端
make generate-api     # 生成 OpenAPI 类型
```

## 4 配置管理

- 敏感配置：K8s Secret（数据库/Redis/S3 凭证、OAuth2 Client ID/Secret）
- 非敏感配置：K8s ConfigMap（文件大小限制、Session TTL 等）

## 5 可观测性

| 维度 | 方案 |
|------|------|
| 日志 | JSON 格式 stdout，包含 traceId/requestId |
| 指标 | Actuator + Micrometer → Prometheus |
| 链路追踪 | 一期 requestId 透传，后续接 Jaeger/Zipkin |
| 告警 | 基于 Prometheus（5xx 率、延迟 P99、Pod 重启） |

requestId 透传：Ingress 注入 → Spring Filter 读取放入 MDC → 日志自动携带 → 响应 Header 回传。

## 6 构建与发布

### CI Pipeline 构建

```
代码提交 → CI Pipeline
    ├── server: mvn package → JAR
    └── web: pnpm build → dist/
         │
         ▼
    Docker 多阶段构建
    ├── server → eclipse-temurin:21-jre-alpine
    └── web → nginx:alpine
         │
         ▼
    推送镜像 → K8s 滚动更新
```

Makefile 顶层命令：`make dev`, `make dev-all`, `make dev-down`, `make dev-all-down`, `make build`, `make generate-api`

## 7 数据库迁移

Flyway 管理 schema 变更：
- 脚本路径：`server/skillhub-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`
- 多 Pod 安全：Flyway 自带数据库锁
