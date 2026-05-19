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

### 5.2 关键文件

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
6. 同时发布 `linux/amd64` 与 `linux/arm64` manifest，避免 Apple Silicon / ARM 主机依赖模拟层

## 7 配置管理

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
   - `SKILLHUB_PUBLIC_BASE_URL` 填最终 HTTPS 域名，且不要带尾部 `/`
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
| 日志 | 容器 stdout / stderr |
| 指标 | Spring Boot Actuator，后续可接 Prometheus |

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
