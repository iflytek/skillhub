# skillhub 部署架构与运维

## 1 运行模型

当前仓库只保留两种运行方式：

- 开发环境：`make dev-all`
  - 前端和后端运行在宿主机
  - `docker-compose.yml` 只负责 PostgreSQL、Redis、MinIO
- 单机交付环境：`docker compose --env-file .env.release -f compose.release.yml up -d`
  - 前端和后端都运行在容器内
- 使用 GitHub Actions 发布到 GHCR 的镜像
- 默认发布 `linux/amd64` 与 `linux/arm64` 多架构镜像
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
- 后端运行 `local,docker` profile 组合
- 技能包文件默认落在容器卷 `skillhub_storage`，保证单机环境开箱即用

## 3 Profile 约定

| Profile | 用途 | 说明 |
|---------|------|------|
| `local` | 本地源码开发能力 | 启用 mock 登录、开发种子账号、调试日志 |
| `docker` | 容器网络适配 | 将数据库和 Redis 地址切换到 Compose 内网 |

单机交付环境使用 `SPRING_PROFILES_ACTIVE=local,docker`，原因很明确：

- 这是当前唯一能保证“镜像拉起后直接可用”的 profile 组合
- 用户无需先配置 GitHub OAuth，先用 mock 身份即可浏览和联调主要流程
- 后续如果引入专用 `runtime` / `demo` profile，可以替换这层组合，但当前方案不再新增第三条部署路径

默认可用账号：

- `local-user`
- `local-admin`

鉴权方式：

- 向后端请求携带 `X-Mock-User-Id: local-user`
- 或 `X-Mock-User-Id: local-admin`

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
docker compose --env-file .env.release -f compose.release.yml up -d
```

默认访问地址：

- Web UI: `http://localhost`
- Backend API: `http://localhost:8080`

### 5.2 关键文件

- `compose.release.yml`
  - 使用发布镜像，不在用户机器上执行本地构建
  - 负责拉起 PostgreSQL、Redis、server、web
  - 使用独立 Compose project name，避免与开发环境容器互相污染
- `.env.release.example`
  - 运行时变量模板
  - 包含镜像名、镜像版本、端口和数据库凭证

### 5.3 镜像标签约定

- `edge`
  - `main` 分支最新构建
  - 用于内部持续验证
- `vX.Y.Z`
  - 对应 Git tag
  - 用于稳定版本交付
- `latest`
  - 仅在语义化版本 tag 发布时更新

推荐：

- 团队内部试用：`SKILLHUB_VERSION=edge`
- 对外演示或文档引用：固定为某个 `vX.Y.Z`

## 6 GitHub Actions 发布流程

发布工作流文件：`.github/workflows/publish-images.yml`

触发条件：

- push 到 `main`
- push 语义化版本 tag，例如 `v1.2.0`
- 手动 `workflow_dispatch`

流程：

1. 检出代码
2. 登录 GHCR
3. 分别构建 `server/Dockerfile` 与 `web/Dockerfile`
4. 推送镜像：
   - `ghcr.io/iflytek/skillhub-server`
   - `ghcr.io/iflytek/skillhub-web`
5. 写入 `edge` / `vX.Y.Z` / `latest` / `sha-*` 标签
6. 同时发布 `linux/amd64` 与 `linux/arm64` manifest，避免 Apple Silicon / ARM 主机依赖模拟层

## 7 配置管理

开发环境：

- 本地命令与 `docker-compose.yml`
- 非敏感默认值可直接落库或写入本地配置

单机交付环境：

- 使用 `.env.release` 管理 Compose 变量
- 如果 GHCR 包保持私有，用户需要先 `docker login ghcr.io`
- 如果要开放真实登录，再补充 `OAUTH2_GITHUB_CLIENT_ID` / `OAUTH2_GITHUB_CLIENT_SECRET`

## 8 可观测性

| 维度 | 方案 |
|------|------|
| 健康检查 | `web/nginx-health`、`server/actuator/health` |
| 日志 | 容器 stdout / stderr |
| 指标 | Spring Boot Actuator，后续可接 Prometheus |

## 9 数据迁移

Flyway 仍是唯一 schema 变更入口：

- 路径：`server/skillhub-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`
- 启动策略：应用容器启动时自动执行迁移
