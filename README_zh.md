<div align="center">
  <img src="./skillhub-logo.svg" alt="SkillHub Logo" width="120" height="120" />
  <h1>SkillHub</h1>
  <p>企业级开源智能体技能注册中心 — 在组织内发布、发现和管理可复用的技能包</p>
</div>

<div align="center">

[![文档](https://img.shields.io/badge/docs-zread.ai-4A90E2?logo=gitbook&logoColor=white)](https://zread.ai/iflytek/skillhub)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![构建](https://github.com/iflytek/skillhub/actions/workflows/publish-images.yml/badge.svg)](https://github.com/iflytek/skillhub/actions/workflows/publish-images.yml)
[![Docker](https://img.shields.io/badge/docker-ghcr.io-2496ED?logo=docker&logoColor=white)](https://ghcr.io/iflytek/skillhub)
[![Java](https://img.shields.io/badge/java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![React](https://img.shields.io/badge/react-19-61DAFB?logo=react&logoColor=black)](https://react.dev)

</div>

---

SkillHub 是一个自托管平台，为团队提供私有的、受治理的智能体技能共享空间。发布技能包，推送到命名空间，让其他人通过搜索发现或通过 CLI 安装。专为防火墙后的本地部署而构建，提供与公共注册中心相同的精致体验。

## 文档

- 📖 **[用户指南](https://iflytek.github.io/skillhub/)** — 技能发布、搜索、CLI 使用等用户操作指南
- 🛠️ **[开发者文档](https://zread.ai/iflytek/skillhub)** — 架构设计、API 参考、本地开发、部署运维等技术文档

## 核心特性

- **自托管与私有化** — 部署在您自己的基础设施上。将专有技能保留在防火墙后，完全掌控数据主权。一条 `make dev-all` 命令即可在本地运行。
- **发布与版本管理** — 上传智能体技能包，支持语义化版本控制、自定义标签（`beta`、`stable`）和自动 `latest` 跟踪。
- **发现** — 全文搜索，支持按命名空间、下载量、评分和时间筛选。可见性规则确保用户只能看到其有权访问的内容。
- **团队命名空间** — 在团队或全局范围下组织技能。每个命名空间拥有自己的成员、角色（Owner / Admin / Member）和发布策略。
- **审核与治理** — 团队管理员在其命名空间内审核；平台管理员控制向全局范围的推广。治理操作记录审计日志以满足合规要求。
- **社交功能** — 收藏技能、评分并跟踪下载量。围绕组织的最佳实践构建社区。
- **账户合并** — 将多个 OAuth 身份和 API 令牌整合到单个用户账户下。
- **API 令牌管理** — 为 CLI 和程序化访问生成作用域令牌，采用基于前缀的安全哈希。
- **CLI 优先** — 原生 REST API，加上对现有 ClawHub 风格注册中心客户端的兼容层。原生 CLI API 是主要支持路径，协议兼容性持续扩展中。
- **可插拔存储** — 开发环境使用本地文件系统，生产环境使用 S3 / MinIO。通过配置切换。
- **国际化** — 使用 i18next 支持多语言。

## 快速开始

使用以下命令启动完整的本地环境：

```bash
rm -rf /tmp/skillhub-runtime
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up
```

默认命令会拉取 `latest` 稳定版镜像；如果你想跟随 `main` 的最新构建，请显式传 `--version edge`。

**配置公网访问地址（生产环境推荐）：**

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --public-url https://skillhub.your-company.com
```

`--public-url` 参数用于设置 SkillHub 实例的公网访问地址。配置后：
- CLI 安装命令会显示正确的注册中心地址
- Agent 设置指引会显示正确的 skill.md URL
- OAuth 回调和设备认证链接能正常工作

**国内用户（阿里云镜像）：**

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun --public-url https://skillhub.your-company.com
```

如果部署遇到问题，请清除现有的运行时目录并重试。

### 前置要求

- Docker & Docker Compose

### 访问应用

- Web UI: http://localhost:3000
- 后端 API: http://localhost:8080

### 默认账户

默认执行 `make dev-all` 时，后端以 `local` profile 启动。
在这个模式下，本地开发会保留下面两个模拟用户，同时默认创建一个可账号密码登录的 bootstrap 管理员：

- `local-user` — 普通用户，用于发布和命名空间操作
- `local-admin` — 超级管理员，用于审核和管理流程

在本地开发中使用 `X-Mock-User-Id` 请求头切换用户。
本地 bootstrap 管理员默认已在 `application-local.yml` 中开启：

- 用户名：`admin`
- 密码：`ChangeMe!2026`
- 如需关闭，请在启动后端前设置环境变量 `BOOTSTRAP_ADMIN_ENABLED=false`

通过 `runtime.sh` 或 `compose.release.yml` 部署时，发布模板同样默认开启管理员，
使用相同的默认账号密码（`admin` / `ChangeMe!2026`），零配置即可登录。
**生产环境请务必修改密码**——`validate-release-config.sh` 会拒绝默认值

### 停止服务

```bash
/tmp/skillhub-runtime/runtime.sh down
```

## 开发

### 前置要求

- Java 21+
- Node.js 20+
- Docker & Docker Compose
- Make

### 启动开发环境

```bash
# 克隆仓库
git clone https://github.com/iflytek/skillhub.git
cd skillhub

# 启动完整的本地开发栈（后端 + 前端 + 依赖）
make dev-all

# 或者分别启动
make dev-backend    # 仅后端
make dev-web        # 仅前端
```

> **国内开发者**：如果 Maven 依赖下载超时，需配置阿里云镜像。详见 [本地开发指南](https://iflytek.github.io/skillhub/quickstart.html#本地开发)。

### 常用命令

```bash
make help                    # 显示所有可用命令
make test                    # 运行后端测试
make test-backend-app        # 运行 skillhub-app 及其依赖模块测试
make build-backend-app       # 构建 skillhub-app 及其依赖模块
make typecheck-web          # TypeScript 类型检查
make build-web              # 构建前端
make generate-api           # 重新生成 OpenAPI 类型
./scripts/check-openapi-generated.sh  # 验证 API 契约同步
./scripts/smoke-test.sh http://localhost:8080  # 运行冒烟测试
```

说明：不要在 `server/` 下直接执行 `./mvnw -pl skillhub-app clean test`。`skillhub-app` 依赖同仓库的 sibling modules，单独 clean 构建时会回退到本地 Maven 仓库里的旧产物并出现大量 `cannot find symbol` / 签名不匹配错误。需要使用 `-am`，或者直接使用上面的 `make test-backend-app` / `make build-backend-app`。

### 项目结构

```
skillhub/
├── server/                 # 后端（Java/Spring Boot）
│   ├── skillhub-app/      # 主应用程序
│   ├── skillhub-domain/   # 核心业务逻辑
│   ├── skillhub-auth/     # 认证授权
│   ├── skillhub-search/   # 搜索功能
│   ├── skillhub-storage/  # 存储层
│   └── skillhub-infra/    # 基础设施
├── web/                   # 前端（React/TypeScript）
├── docs/                  # 文档
├── scripts/               # 实用脚本
├── deploy/                # 部署配置
├── monitoring/            # Prometheus + Grafana
├── Makefile              # 常用任务
└── docker-compose.yml    # 本地开发栈
```

## 部署

### 使用 Docker Compose

```bash
# 默认（GHCR 镜像）
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --public-url https://skillhub.your-company.com

# 阿里云镜像（国内推荐）
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun --public-url https://skillhub.your-company.com
```

### 配置参数说明

| 参数 | 说明 | 示例 |
|------|------|------|
| `--public-url <url>` | 公网访问地址（推荐配置） | `--public-url https://skill.example.com` |
| `--version <tag>` | 指定镜像版本 | `--version v0.2.0` |
| `--aliyun` | 使用阿里云镜像（国内推荐） | `--aliyun` |
| `--home <dir>` | 指定运行时目录 | `--home /opt/skillhub` |
| `--no-scanner` | 禁用安全扫描服务 | `--no-scanner` |

> **重要**：生产环境请务必配置 `--public-url`，确保 CLI 安装命令和 Agent 设置指引显示正确的地址。

### 使用 Kubernetes

```bash
# 应用 Kubernetes 清单
kubectl apply -f deploy/k8s/

# 或使用 Helm（即将推出）
helm install skillhub ./deploy/helm
```

### 环境变量

关键配置选项：

```bash
# 数据库
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/skillhub
SPRING_DATASOURCE_USERNAME=skillhub
SPRING_DATASOURCE_PASSWORD=skillhub

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# 存储（S3/MinIO）
STORAGE_TYPE=s3
STORAGE_S3_ENDPOINT=http://localhost:9000
STORAGE_S3_ACCESS_KEY=minioadmin
STORAGE_S3_SECRET_KEY=minioadmin
STORAGE_S3_BUCKET=skillhub

# 认证
AUTH_JWT_SECRET=your-secret-key
AUTH_SESSION_TIMEOUT=30m
```

完整配置参考请查看 [`application.yml`](./server/skillhub-app/src/main/resources/application.yml)。

### 上传白名单覆盖

技能包上传校验默认使用
[`SkillPackagePolicy.java`](./server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/SkillPackagePolicy.java)
中的扩展名白名单。`SkillPublishProperties` 默认也会把这份列表作为
`skillhub.publish.allowed-file-extensions` 的值。

如果需要在运行时整体替换默认白名单，可以设置：

```bash
SKILLHUB_PUBLISH_ALLOWED_FILE_EXTENSIONS=.md,.json,.xsd,.xsl,.dtd,.docx,.xlsx,.pptx
```

Spring Boot 会把这个环境变量绑定到
`skillhub.publish.allowed-file-extensions`。一旦设置，该配置会替换默认白名单，
而不是在默认列表后追加。

## 架构

SkillHub 采用清晰的分层架构：

- **表现层**：REST API（Spring Boot）+ React 前端
- **应用层**：用例编排和 DTO 转换
- **领域层**：核心业务逻辑和实体
- **基础设施层**：数据库、存储、搜索

关键设计决策：

- **多模块 Maven 项目**：清晰的模块边界和依赖管理
- **领域驱动设计**：丰富的领域模型和业务规则
- **CQRS 模式**：读写分离以优化性能
- **事件溯源**：审计日志和治理操作
- **可插拔存储**：通过配置在本地/S3/MinIO 之间切换

详细架构文档请参阅 [`docs/`](./docs/) 目录。

## 技术栈

### 后端
- **语言**：Java 21
- **框架**：Spring Boot 3.2.3
- **数据库**：PostgreSQL 16 + Flyway 迁移
- **缓存**：Redis 7
- **存储**：S3/MinIO
- **搜索**：PostgreSQL 全文搜索

### 前端
- **语言**：TypeScript
- **框架**：React 19
- **构建工具**：Vite
- **路由**：TanStack Router
- **数据获取**：TanStack Query
- **样式**：Tailwind CSS + Radix UI
- **API 客户端**：OpenAPI TypeScript（类型安全）
- **国际化**：i18next

### 基础设施
- **容器化**：Docker & Docker Compose
- **监控**：Prometheus + Grafana
- **部署**：Kubernetes 清单
- **CI/CD**：GitHub Actions

## 路线图

- [x] 核心技能注册功能
- [x] 命名空间和团队管理
- [x] 审核和治理工作流
- [x] 全文搜索和筛选
- [x] 社交功能（收藏、评分、下载）
- [x] API 令牌管理
- [x] 账户合并
- [x] 国际化支持
- [ ] Helm Chart 部署
- [ ] 高级搜索过滤器
- [ ] 技能依赖管理
- [ ] Webhook 集成
- [ ] 审计日志导出
- [ ] LDAP/SAML 集成

完整路线图请参阅 [`docs/10-delivery-roadmap.md`](./docs/10-delivery-roadmap.md)。

## 与智能体平台集成

SkillHub 设计为与各种智能体平台和框架无缝集成。

### [OpenClaw](https://github.com/openclaw/openclaw)

[OpenClaw](https://github.com/openclaw/openclaw) 是开源的智能体技能 CLI 工具。配置它使用您的 SkillHub 端点作为注册中心：

```bash
# 配置注册中心地址
export CLAWHUB_REGISTRY=https://skillhub.your-company.com

# 如需认证，先登录一次
clawhub login --token YOUR_API_TOKEN

# 搜索和安装技能
npx clawhub search email
npx clawhub install my-skill
npx clawhub install my-namespace--my-skill

# 发布技能
npx clawhub publish ./my-skill
```

> 💡 **提示**：上述命令不仅适用于 OpenClaw，通过指定安装目录（`--dir`），也可适用于其他的 CLI Coding Agent 或 Agent 助手。例如：`npx clawhub --dir ~/.claude/skills install my-skill`

📖 **[完整 OpenClaw 集成指南 →](./docs/openclaw-integration.md)**

### [AstronClaw](https://agent.xfyun.cn/astron-claw)

[AstronClaw](https://agent.xfyun.cn/astron-claw) 是基于 OpenClaw 核心能力打造的云端 AI 助手，提供全天候在线服务，随时随地通过企业微信、钉钉、飞书等渠道提供服务。它内置了丰富的技能系统，您可以将其连接到自托管的 SkillHub 注册中心，支持技能市场一键安装、仓库搜索、对话自动安装，甚至管理和分发组织内部的自定义私有技能。

### [Loomy](https://loomy.xunfei.cn/)

[Loomy](https://loomy.xunfei.cn/) 是聚焦真实办公场景的桌面端 AI 工作搭子。它深入打通本地文件和系统工具，为个人及小团队构建高效的自动化工作流。通过将 Loomy 连接到您的 SkillHub 注册中心，您可以轻松发现并安装组织内部的专属技能，从而增强本地桌面端的自动化与协同办公能力。

### [astron-agent](https://github.com/iflytek/astron-agent)

[astron-agent](https://github.com/iflytek/astron-agent) 是科大讯飞星火智能体框架。存储在 SkillHub 中的技能可以被 astron-agent 引用和加载，实现从开发到生产的受治理、版本化的技能生命周期。

---

> 🌟 **展示与分享** — 您使用 SkillHub 构建了什么？我们很想听听！
> 在 [**Discussions → Show and Tell**](https://github.com/iflytek/skillhub/discussions/categories/show-and-tell) 分类中分享您的用例、集成或部署故事。

## 贡献

欢迎贡献。请先开启 issue 讨论您想要更改的内容。

- 贡献指南：[`CONTRIBUTING.md`](./CONTRIBUTING.md)
- 行为准则：[`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md)

## 📞 支持

- 💬 **社区讨论**：[GitHub Discussions](https://github.com/iflytek/skillhub/discussions)
- 🐛 **Bug 报告**：[Issues](https://github.com/iflytek/skillhub/issues)
- 👥 **企业微信群**：

  ![企业微信群](https://github.com/iflytek/astron-agent/raw/main/docs/imgs/WeCom_Group.png)

## 许可证

Apache License 2.0
