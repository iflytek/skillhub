# 常见问题

## Q: SkillHub 和 ClawHub 有什么区别？

A: SkillHub 是企业级的自托管方案，提供了更强的权限控制、审核机制和治理能力。ClawHub 是公共注册中心，类似 npm。

**主要区别**：

| 特性 | SkillHub | ClawHub |
|------|----------|---------|
| **部署方式** | 自托管 | 公共云 |
| **权限控制** | 命名空间 RBAC | 基础权限 |
| **审核机制** | 多级审核 | 无 |
| **安全扫描** | 内置 Skill Scanner | 无 |
| **数据主权** | 完全自主 | 托管在云端 |
| **适用场景** | 企业内部 | 公开分享 |

## Q: 如何备份数据？

A: SkillHub 的数据存储在 PostgreSQL 和对象存储中。定期备份这两部分即可。

**备份 PostgreSQL**：
```bash
pg_dump -h localhost -U postgres skillhub > backup.sql
```

**备份对象存储**：
- 如果使用 MinIO，备份 MinIO 数据目录
- 如果使用 S3，使用 AWS CLI 或 S3 备份工具

## Q: 支持哪些认证方式？

A: SkillHub 支持多种认证方式：

- **OAuth2**：GitHub、Google、GitLab 等
- **本地账号**：用户名密码登录（内置管理员：admin / ChangeMe!2026）
- **企业 SSO**：可以集成 LDAP、SAML 等

配置方式参考项目 README 中的认证配置章节。

## Q: 技能包大小有限制吗？

A: 默认限制为 **100MB**。可以通过配置调整：

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

## Q: 如何使用 CLI 工具管理技能包？

A: SkillHub 兼容 OpenClaw CLI，使用 `npx clawhub` 命令即可操作：

```bash
# 配置注册中心地址
export CLAWHUB_REGISTRY=http://your-skillhub-host:8080

# 搜索技能包
npx clawhub search email

# 安装技能包
npx clawhub install my-skill

# 发布技能包
npx clawhub publish ./my-skill
```

## Q: 如何配置 HTTPS？

A: 生产环境建议使用 Nginx 或 Traefik 作为反向代理，配置 SSL 证书。

**Nginx 配置示例**：
```nginx
server {
    listen 443 ssl;
    server_name skillhub.example.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:3000;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

## Q: 如何监控 SkillHub？

A: SkillHub 提供了多种监控方式：

- **健康检查**：`GET /actuator/health`
- **Scanner 健康检查**：`GET http://localhost:8000/health`
- **指标监控**：`GET /actuator/metrics`（Prometheus 格式）
- **审计日志**：所有关键操作都会记录到审计日志
- **应用日志**：使用 ELK 或 Loki 收集日志

## Q: 支持多租户吗？

A: SkillHub 通过命名空间实现了逻辑上的多租户隔离。每个命名空间相当于一个租户，拥有独立的成员、权限和技能包。

如果需要物理隔离，可以为每个租户部署独立的 SkillHub 实例。

## Q: 如何升级 SkillHub？

A: 使用 curl 命令升级：

```bash
# 拉取最新镜像并重启
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- pull
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up

# 或直接指定版本升级
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version v0.2.0
```

> **注意**：升级前建议先备份数据库和对象存储。数据库迁移由 Flyway 自动执行。

## Q: 遇到问题怎么办？

A: 可以通过以下方式获取帮助：

- **GitHub Issues**: https://github.com/iflytek/skillhub/issues
- **文档**: 参考项目 README.md
- **社区讨论**: https://github.com/iflytek/skillhub/discussions

## Q: 本地开发启动失败怎么办？

A: `make dev-all` 后端启动失败时，会显示详细的错误提示。常见问题：

### 1. Maven 依赖下载失败（网络超时）

**症状**：后端日志显示 `Could not transfer artifact` 或连接超时

**解决方案**：配置阿里云镜像

```bash
# 复制项目内置的镜像配置到用户目录
mkdir -p ~/.m2
cp server/.mvn/settings.xml ~/.m2/settings.xml
```

或手动创建 `~/.m2/settings.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

参考：[阿里云 Maven 镜像配置指南](https://maven.aliyun.com/mvn/guide)

### 2. Java 版本不匹配

**症状**：`Unsupported class file major version` 或 `java.lang.NoSuchMethodError`

**解决方案**：安装 Java 21+

```bash
# macOS
brew install openjdk@21

# 验证版本
java -version
```

### 3. 端口被占用

**症状**：`Port 8080 already in use`

**解决方案**：

```bash
# 查看占用端口的进程
lsof -i :8080

# 终止进程
kill -9 <PID>
```

### 4. 查看详细日志

如果以上方案无法解决，查看后端日志：

```bash
make dev-logs SERVICE=backend
# 或直接查看
cat .dev/server.log
```
