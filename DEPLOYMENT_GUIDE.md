# SkillHub 飞书 OAuth 部署指南

## 概述

本文档说明如何在 SkillHub 项目中添加飞书 OAuth2.0 登录功能，包括开发测试方案和团队生产部署方案。

---

## Part 1: 开发环境测试方案

### 1.1 问题背景

飞书 OAuth 需要公网可访问的回调 URL (`/login/oauth2/code/feishu`)，但开发服务器通常没有公网 IP。

### 1.2 解决方案：内网穿透

使用 ngrok 或 frp 将本地服务暴露到公网。

#### 步骤 1: 安装 ngrok

```bash
# Linux/macOS
curl -s https://ngrok-agent.s3.amazonaws.com/ngrok.asc | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null
echo "deb https://ngrok-agent.s3.amazonaws.com buster main" | sudo tee /etc/apt/sources.list.d/ngrok.list
sudo apt update && sudo apt install ngrok

# 或直接下载: https://ngrok.com/download
```

#### 步骤 2: 配置 ngrok

1. 注册 ngrok 账号: https://ngrok.com
2. 获取 Authtoken
3. 配置:

```bash
ngrok config add-authtoken <YOUR_NGROK_TOKEN>
```

#### 步骤 3: 启动穿透

在启动 SkillHub 后，新终端执行:

```bash
ngrok http 8080
```

输出示例:
```
Forwarding  https://abc123.ngrok.io -> http://localhost:8080
```

#### 步骤 4: 配置飞书开放平台

1. 访问 [飞书开放平台](https://open.feishu.cn/app)
2. 创建自建应用 (或编辑现有应用)
3. 安全设置 → 重定向 URL → 添加:
   ```
   https://abc123.ngrok.io/login/oauth2/code/feishu
   ```
4. 权限管理 → 开通以下权限:
   - `user:email` - 获取用户邮箱
   - `user:id` - 获取用户 ID

#### 步骤 5: 配置本地环境变量

```bash
cd /path/to/skillhub

cat > .env.local << 'EOF'
# ===================
# 基础配置
# ===================
SKILLHUB_PUBLIC_BASE_URL=https://abc123.ngrok.io
SKILLHUB_AUTH_MOCK_ENABLED=false

# ===================
# 飞书 OAuth2 (必需)
# ===================
FEISHU_CLIENT_ID=cli_xxxxxxxxxxxxxxxxxx
FEISHU_CLIENT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# ===================
# 数据库 (开发环境)
# ===================
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/skillhub
SPRING_DATASOURCE_USERNAME=skillhub
SPRING_DATASOURCE_PASSWORD=skillhub_dev

# ===================
# Redis (开发环境)
# ===================
REDIS_HOST=localhost
REDIS_PORT=6379

# ===================
# 存储 (本地开发)
# ===================
SKILLHUB_STORAGE_PROVIDER=local
EOF
```

#### 步骤 6: 启动本地环境

```bash
# 启动依赖服务 (PostgreSQL, Redis, MinIO)
docker compose --env-file .env.local -f docker-compose.yml up -d

# 等待服务健康 (约 30 秒)
docker compose ps

# 启动后端 (带飞书配置)
docker compose --env-file .env.local -f docker-compose.yml up -d server

# 或使用 make (如果已配置)
make dev-all
```

#### 步骤 7: 验证飞书登录

**方式 A: Web UI**
访问 http://localhost:3000 → 登录 → 选择飞书登录

**方式 B: 直接触发 OAuth**
浏览器访问:
```
http://localhost:8080/oauth2/authorization/feishu
```

#### 预期结果

成功登录后:
1. 页面跳转到飞书授权页 → 授权 → 返回 SkillHub
2. 用户信息正确显示 (用户名、邮箱)
3. Session 保持登录状态

#### 常见问题排查

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `redirect_uri_mismatch` | 重定向 URL 不匹配 | 飞书平台添加 `https://abc123.ngrok.io/login/oauth2/code/feishu` |
| `invalid_client` | App credentials 错误 | 检查 `FEISHU_CLIENT_ID` 和 `FEISHU_CLIENT_SECRET` |
| `scope insufficient` | 权限未开通 | 飞书平台开通 `user:email` 和 `user:id` |
| 一直返回登录页 | Mock Auth 未关闭 | 设置 `SKILLHUB_AUTH_MOCK_ENABLED=false` |
| 500 错误 | 数据库未初始化 | `docker compose down -v && docker compose up -d` 重置 |

---

## Part 2: 团队生产部署方案

### 2.1 概述

生产环境使用 Docker Compose + 预编译镜像，无需开发服务器。团队可以:

1. 启用 Rsweater fork 的 GitHub Actions 构建自定义镜像
2. 部署到自己的服务器或 K8s 集群

### 2.2 方案 A: 使用 Rsweater Fork + GitHub Actions (推荐)

#### 优势
- 无需额外构建基础设施
- GitHub Actions 自动构建 multi-arch 镜像 (amd64 + arm64)
- 支持推送到阿里云等国内镜像仓库

#### 步骤 1: 准备镜像仓库

**选项 A: GitHub Container Registry (GHCR)**
- 默认，无需配置
- 镜像地址: `ghcr.io/Rsweater/skillhub-server`

**选项 B: 阿里云容器镜像 ACR**
1. 创建阿里云账号，开通容器镜像服务
2. 创建命名空间 (namespace)
3. 获取访问凭证 (AccessKey ID/Secret)

#### 步骤 2: 修改 Workflow 配置

编辑 `.github/workflows/publish-images.yml`:

```yaml
# ===================
# 方式 A: GHCR (默认)
# ===================
# 无需修改，直接推送到 Rsweater 的 GHCR

# ===================
# 方式 B: 阿里云 ACR
# ===================
# 添加 secrets: ALIYUN_REGISTRY, ALIYUN_NAME_SPACE, ALIYUN_REGISTRY_USER, ALIYUN_REGISTRY_PASSWORD
# 或修改 env 部分:
env:
  MIRROR_REGISTRY: registry.cn-beijing.aliyuncs.com
  MIRROR_NAMESPACE: your-namespace
  MIRROR_REGISTRY_USERNAME: your-username
  MIRROR_REGISTRY_PASSWORD: your-password
```

#### 步骤 3: 启用 GitHub Actions

1. Fork Rsweater/skillhub 仓库
2. Settings → Actions → Enable Actions
3. (可选) 添加仓库 Secrets:
   - `ALIYUN_REGISTRY` - 阿里云 registry 地址
   - `ALIYUN_NAME_SPACE` - 命名空间
   - `ALIYUN_REGISTRY_USER` - 用户名
   - `ALIYUN_REGISTRY_PASSWORD` - 密码

#### 步骤 4: 触发镜像构建

```bash
# 通过 GitHub CLI
cd /path/to/skillhub
GH_REPO=Rsweater/skillhub gh workflow run publish-images.yml --ref feature/feishu-oauth2-v2
```

或 GitHub Web:
1. Actions → Publish Images → Run workflow
2. 选择分支: `feature/feishu-oauth2-v2`

#### 步骤 5: 生产服务器部署

```bash
# ===================
# 创建生产环境配置
# ===================
cat > .env.prod << 'EOF'
# ===================
# 镜像版本
# ===================
SKILLHUB_VERSION=edge
SKILLHUB_SERVER_IMAGE=ghcr.io/Rsweater/skillhub-server
SKILLHUB_WEB_IMAGE=ghcr.io/Rsweater/skillhub-web
SKILLHUB_SCANNER_IMAGE=ghcr.io/Rsweater/skillhub-scanner

# 阿里云 ACR (如果使用)
# SKILLHUB_SERVER_IMAGE=registry.cn-beijing.aliyuncs.com/your-namespace/skillhub-server
# SKILLHUB_WEB_IMAGE=registry.cn-beijing.aliyuncs.com/your-namespace/skillhub-web
# SKILLHUB_SCANNER_IMAGE=registry.cn-beijing.aliyuncs.com/your-namespace/skillhub-scanner

# ===================
# 公共 URL (必需 - 公网可达)
# ===================
SKILLHUB_PUBLIC_BASE_URL=https://skillhub.your-company.com

# ===================
# 飞书 OAuth2
# ===================
FEISHU_CLIENT_ID=cli_xxxxxxxxxxxxxxxxxx
FEISHU_CLIENT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# ===================
# GitHub OAuth (可选)
# ===================
OAUTH2_GITHUB_CLIENT_ID=
OAUTH2_GITHUB_CLIENT_SECRET=

# ===================
# 数据库
# ===================
POSTGRES_PASSWORD=your-secure-password
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/skillhub
SPRING_DATASOURCE_USERNAME=skillhub
SPRING_DATASOURCE_PASSWORD=your-secure-password

# ===================
# Redis
# ===================
REDIS_PASSWORD=

# ===================
# 存储 (生产建议 S3)
# ===================
SKILLHUB_STORAGE_PROVIDER=s3
SKILLHUB_STORAGE_S3_ENDPOINT=https://oss-cn-beijing.aliyuncs.com
SKILLHUB_STORAGE_S3_BUCKET=skillhub-prod
SKILLHUB_STORAGE_S3_ACCESS_KEY=your-access-key
SKILLHUB_STORAGE_S3_SECRET_KEY=your-secret-key
SKILLHUB_STORAGE_S3_REGION=cn-beijing

# ===================
# 安全设置
# ===================
SKILLHUB_SECURITY_SCANNER_ENABLED=true
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_PASSWORD=ChangeMe!2026  # 首次部署后立即修改
EOF
```

#### 步骤 6: 部署

```bash
# 拉取最新镜像
docker compose --env-file .env.prod -f compose.release.yml pull

# 重启服务
docker compose --env-file .env.prod -f compose.release.yml up -d

# 检查状态
docker compose --env-file .env.prod -f compose.release.yml ps

# 查看日志
docker compose --env-file .env.prod -f compose.release.yml logs -f server
```

### 2.3 方案 B: 本地构建镜像

如果无法使用 GitHub Actions，可以在本地构建镜像:

```bash
cd /path/to/skillhub

# 构建镜像
docker build -t skillhub-server:local -f server/Dockerfile server/
docker build -t skillhub-web:local -f web/Dockerfile web/
docker build -t skillhub-scanner:local -f scanner/Dockerfile scanner/

# 打标签
docker tag skillhub-server:local your-registry.com/skillhub-server:v1.0.0
docker push your-registry.com/skillhub-server:v1.0.0

# 使用本地镜像部署
cat > .env.local-build << 'EOF'
SKILLHUB_VERSION=v1.0.0
SKILLHUB_SERVER_IMAGE=your-registry.com/skillhub-server
SKILLHUB_WEB_IMAGE=your-registry.com/skillhub-web
SKILLHUB_SCANNER_IMAGE=your-registry.com/skillhub-scanner
...
EOF
```

---

## Part 3: 飞书应用配置

### 3.1 创建飞书应用

1. 访问 [飞书开放平台](https://open.feishu.cn/app)
2. 点击 "创建自建应用"
3. 填写应用名称和描述
4. 获取凭证:
   - `App ID` → 环境变量 `FEISHU_CLIENT_ID`
   - `App Secret` → 环境变量 `FEISHU_CLIENT_SECRET`

### 3.2 配置权限

1. 进入应用 → 权限管理
2. 开通以下权限:

| 权限名称 | 权限标识 | 用途 |
|---------|---------|------|
| 获取用户基本信息 | `user:basic` | 获取用户 name, avatar |
| 获取用户邮箱 | `user:email` | 获取用户邮箱 |
| 获取用户 user_id | `user:id` | 获取用户唯一标识 |

### 3.3 配置重定向 URL

1. 进入应用 → 安全设置
2. 重定向 URL → 添加:
   ```
   生产环境: https://skillhub.your-company.com/login/oauth2/code/feishu
   开发环境: https://abc123.ngrok.io/login/oauth2/code/feishu
   ```

### 3.4 发布应用

1. 开发阶段: 可使用 "测试企业" 功能
2. 生产阶段: 需要提交审核并发布

---

## Part 4: 环境变量参考

### 完整环境变量列表

```bash
# ===================
# 基础配置
# ===================
SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com  # 必需，公网 URL
SKILLHUB_VERSION=edge  # 镜像版本

# ===================
# 飞书 OAuth2
# ===================
FEISHU_CLIENT_ID=cli_xxx           # 飞书 App ID
FEISHU_CLIENT_SECRET=xxx           # 飞书 App Secret

# ===================
# GitHub OAuth (可选)
# ===================
OAUTH2_GITHUB_CLIENT_ID=           # GitHub Client ID
OAUTH2_GITHUB_CLIENT_SECRET=       # GitHub Client Secret

# ===================
# 数据库
# ===================
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/skillhub
SPRING_DATASOURCE_USERNAME=skillhub
SPRING_DATASOURCE_PASSWORD=your-password
POSTGRES_PASSWORD=your-password

# ===================
# Redis
# ===================
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# ===================
# 存储
# ===================
SKILLHUB_STORAGE_PROVIDER=local|s3
STORAGE_BASE_PATH=/var/lib/skillhub/storage
SKILLHUB_STORAGE_S3_ENDPOINT=https://oss-cn-beijing.aliyuncs.com
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_ACCESS_KEY=
SKILLHUB_STORAGE_S3_SECRET_KEY=
SKILLHUB_STORAGE_S3_REGION=cn-beijing

# ===================
# 安全
# ===================
SKILLHUB_SECURITY_SCANNER_ENABLED=true|false
BOOTSTRAP_ADMIN_ENABLED=true|false
BOOTSTRAP_ADMIN_PASSWORD=ChangeMe!2026

# ===================
# 会话
# ===================
SESSION_COOKIE_SECURE=true  # 生产环境设为 true
```

---

## Part 5: Docker Compose 配置

### 5.1 compose.release.yml 服务映射

| 服务 | 端口 | 说明 |
|------|------|------|
| server | 8080 | Spring Boot 后端 |
| web | 80 | React 前端 (Nginx) |
| postgres | 5432 | PostgreSQL 数据库 |
| redis | 6379 | Redis 会话存储 |
| skill-scanner | 8000 | 安全扫描器 |

### 5.2 健康检查

```bash
# 检查所有服务健康状态
docker compose --env-file .env.prod -f compose.release.yml ps

# 预期输出:
# Name           Command               Service    Status     Health
# server         java -jar app.jar     server     running    healthy
# web            nginx -g ...          web        running    healthy
# postgres       postgres             postgres   running    healthy
# redis          redis-server ...      redis      running    healthy
```

---

## Part 6: 回滚方案

### 6.1 紧急回滚

```bash
# 查看最近 5 个版本
docker images | grep skillhub-server

# 回滚到上一个版本
docker compose --env-file .env.prod -f compose.release.yml pull server
docker compose --env-file .env.prod -f compose.release.yml up -d server

# 或指定版本
SKILLHUB_VERSION=v0.9.0 docker compose --env-file .env.prod -f compose.release.yml up -d
```

### 6.2 数据库回滚

如果需要回滚数据库:
```bash
# 进入 postgres 容器
docker compose --env-file .env.prod -f compose.release.yml exec postgres psql -U skillhub

# 查看 migrations
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
```

---

## 附录 A: CI/CD 流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                        开发流程                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 本地开发 (feature/feishu-oauth2-v2)                         │
│           ↓                                                      │
│  2. GitHub Fork → Rsweater/skillhub                            │
│           ↓                                                      │
│  3. GitHub Actions 自动构建镜像                                   │
│     - server: ghcr.io/Rsweater/skillhub-server:edge            │
│     - web:    ghcr.io/Rsweater/skillhub-web:edge               │
│     - scanner: ghcr.io/Rsweater/skillhub-scanner:edge         │
│           ↓                                                      │
│  4. PR Review & Merge                                           │
│           ↓                                                      │
│  5. GitHub Actions 发布正式版本镜像                              │
│     - skillhub-server:sha-xxx                                   │
│     - skillhub-server:0.x.y                                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        生产部署                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 服务器拉取新镜像                                              │
│     docker compose pull                                         │
│           ↓                                                      │
│  2. 重启服务 (零停机滚动更新)                                     │
│     docker compose up -d --remove-orphans                        │
│           ↓                                                      │
│  3. 健康检查                                                     │
│     curl http://localhost:8080/actuator/health                  │
│           ↓                                                      │
│  4. 验证飞书登录                                                 │
│     访问 https://skillhub.example.com                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 附录 B: 快速参考命令

```bash
# ===================
# 开发
# ===================
make dev-all                  # 启动本地开发环境
make dev-all-down             # 停止本地环境
make dev-all-reset            # 重置本地环境

# ===================
# 测试
# ===================
./mvnw test                   # 运行所有测试
./mvnw test -Dtest=FeishuClaimsExtractorTest  # 运行特定测试

# ===================
# 构建
# ===================
docker build -t skillhub-server -f server/Dockerfile server/
docker build -t skillhub-web -f web/Dockerfile web/

# ===================
# 部署
# ===================
docker compose --env-file .env.prod -f compose.release.yml up -d
docker compose --env-file .env.prod -f compose.release.yml logs -f
docker compose --env-file .env.prod -f compose.release.yml pull

# ===================
# 清理
# ===================
docker compose down -v        # 删除数据卷
docker system prune -a        # 清理未使用镜像
```

---

## 附录 C: 联系支持

- 飞书群: [二维码](./WeChatWork.png)
- GitHub Issues: https://github.com/Rsweater/skillhub/issues
