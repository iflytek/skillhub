# 快速开始

本页只保留最短启动路径。更深入的发布、搜索、审核、扫描与部署说明，统一下钻到功能指南页面，不在这里重复展开。

## 一键部署

使用 curl 命令快速部署 SkillHub（包含 Web UI、Backend API、MySQL、Redis、Skill Scanner）：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up
```

国内用户可使用阿里云镜像：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun
```

常用参数：

| 参数 | 说明 | 示例 |
|------|------|------|
| `--version <tag>` | 指定版本 | `--version v0.2.0` |
| `--aliyun` | 使用阿里云镜像 | `--aliyun` |
| `--home <dir>` | 指定安装目录 | `--home /opt/skillhub` |
| `--no-scanner` | 禁用安全扫描服务 | `--no-scanner` |

其他常用命令：

```bash
# 停止服务
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down

# 查看状态
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- ps

# 查看日志
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- logs
```

部署成功后访问：

- Web UI: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- API 文档: `http://localhost:8080/swagger-ui.html`
- Skill Scanner: `http://localhost:8000`

## 本地开发

如需从源码启动，当前仓库推荐直接使用仓库内脚本，而不是依赖历史 `make dev-all` 入口：

```bash
git clone https://github.com/iflytek/skillhub.git
cd skillhub
scripts/dev/local-mysql-local-index-memory-up.sh
```

常见前置要求：

- Java 17+
- Docker & Docker Compose
- Node.js / pnpm（前端开发时）

如果源码启动失败，先检查：

1. Maven 依赖是否能正常下载
2. `java -version` 是否满足要求
3. `8080` / `3000` / `3001` 端口是否被占用
4. `docker compose ps` 中 MySQL 是否 healthy

源码启动默认组合：

- MySQL
- `local-file-index`
- memory runtime state
- 本地 mock UASS 页在 `3001`

更完整的组合说明见：

- [../../local-runtime-quickstart.md](../../local-runtime-quickstart.md)

更详细的排障说明见 [常见问题](/faq)。

## 登录系统

可选方式：

- 内置管理员账号
  - 用户名：`admin`
  - 密码：`ChangeMe!2026`
- 注册新账号：`http://localhost:3000/register`
- 本地开发 Mock 用户：

```bash
# 普通用户
curl -H "X-Mock-User-Id: local-user" http://localhost:8080/api/v1/auth/me

# 超级管理员
curl -H "X-Mock-User-Id: local-admin" http://localhost:8080/api/v1/auth/me
```

生产环境部署后，请立即修改默认管理员密码。

## 下一步

- [项目简介](/introduction)
- [Skill 发布与版本管理](/guide/skill-publish)
- [Skill 搜索与发现](/guide/skill-discovery)
- [命名空间与团队管理](/guide/namespace)
- [审核与治理](/guide/review)
- [安全扫描](/guide/scanner)
- [Kubernetes 部署](/guide/kubernetes)
- [常见问题](/faq)
