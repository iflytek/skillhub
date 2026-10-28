# 常见问题

## Q: SkillHub 和 ClawHub 有什么区别？

A: SkillHub 提供自托管注册中心、命名空间权限、发布审核、安全扫描和审计。第三方服务的能力会变化，不应将“无审核/无扫描”作为固定比较结论。兼容范围见 [OpenClaw 集成](https://github.com/iflytek/skillhub/blob/main/docs/openclaw-integration.md)。

## Q: 如何备份数据？

A: 备份 PostgreSQL、实际使用的存储后端及部署配置。默认 release Compose 使用本地文件存储，需要备份 `skillhub_storage` 数据卷；使用 S3 时备份对应 bucket/对象。数据库和技能文件需要来自一致的备份窗口。操作步骤见 [部署指南](./guide/deployment.md)，恢复能力应通过独立环境演练验证。

## Q: 支持哪些认证方式？

A: 支持本地账号及已配置的 OAuth2 登录入口。当前预置注册包括 GitHub、GitLab、飞书和钉钉；实际入口以登录页和 `/api/v1/auth/methods` 为准。企业 SSO 需要按接入契约配置，不能把可扩展性当成已经内置 LDAP/SAML。参见 [认证与私有 SSO](https://github.com/iflytek/skillhub/blob/main/docs/11-auth-extensibility-and-private-sso.md)。

## Q: 技能包大小有限制吗？

A: 默认限制为 **100MB**。可以通过配置调整：

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
skillhub:
  publish:
    max-package-size: 104857600
```

还需核对代理请求体和 Scanner 上传限制；只调 multipart 不会绕过包校验。

## Q: 如何使用 CLI 工具管理技能包？

A: 优先使用第一方 CLI：

```bash
npm install -g @astron-team/skillhub
skillhub search pdf
```

按 [快速开始](./quickstart.md) 选择注册中心、安装位置和 Agent。发布和受限资源访问需要登录。ClawHub 兼容接口的范围见 [集成说明](https://github.com/iflytek/skillhub/blob/main/docs/openclaw-integration.md)，不要将其发布协议与 SkillHub CLI 混用。

## Q: 如何配置 HTTPS？

A: 在外部代理终止 TLS，并转发到实际映射的 Web 端口（release Compose 默认 `WEB_PORT=80`），Web 已代理后端路由。设置真实 `SKILLHUB_PUBLIC_BASE_URL`，正确传递原始 Host 和代理协议头，并验证登录与 Cookie。见 [部署指南](./guide/deployment.md)。

## Q: 如何监控 SkillHub？

A: 默认配置暴露 `/actuator/health` 和 `/actuator/info`。`/actuator/metrics` 不是 Prometheus 文本接口，当前基础配置也未默认暴露它或启用 Prometheus 导出。结合服务日志、审计记录和请求的 `requestId` 排查；监控接入需要单独核对部署配置，见 [可观测性指南](https://github.com/iflytek/skillhub/blob/main/docs/observability-developer-guide.md)。

## Q: 支持多租户吗？

A: SkillHub 通过命名空间实现了逻辑上的多租户隔离。每个命名空间相当于一个租户，拥有独立的成员、权限和技能包。

如果需要物理隔离，可以为每个租户部署独立的 SkillHub 实例。

## Q: 如何升级 SkillHub？

A: 按 [部署与升级指南](./guide/deployment.md) 选择目标版本，先备份数据库、技能存储和配置，再升级并验证健康、登录及代表性技能操作。Flyway 会执行数据库迁移；不能仅凭容器启动成功认定升级成功，也不能假设降级镜像即可回滚数据库。

## Q: 为什么管理员（admin）和普通用户都无法创建命名空间？

A: 先查看页面或接口的错误信息和 `requestId`，核对当前账号权限、名称校验及实例限制。不要直接推断为版本太旧；管理员身份也不能绕过全部业务规则。参见 [命名空间管理](./guide/namespace.md)。

## Q: 如何搜索或操作指定命名空间中的技能包（Skill）？

A: 在网页中选择命名空间；CLI 安装可使用 `skillhub install @team/example-skill`，其中坐标应替换成真实资源。`skillhub search` 搜索技能，`skillhub suite install` 安装套装。命名空间迁移与搜索不是同一操作，不需要通过导出再导入来解决搜索问题。

## Q: 推荐的部署方式是什么？可以自己拉镜像手动部署吗？

A: 支持官方 runtime 脚本、release Compose 和 Kubernetes/Helm。选择一种方式并保留对应的配置与数据目录，参见 [Docker 部署](./guide/deployment.md) 和 [Kubernetes 部署](./guide/kubernetes.md)。手动部署需要正确配置数据库、Redis、存储、认证和代理，不能只启动应用镜像。

## Q: 用 Kubernetes 部署对集群版本有要求吗？

A: 官方 [Kubernetes 部署指南](./guide/kubernetes.md) 要求 Kubernetes **v1.24 及以上**，并需要已配置好的 `kubectl` 与默认 StorageClass（用于 PVC）；如需域名访问，还需安装 nginx ingress controller。更低版本的集群未经验证，建议先升级集群再部署。

## Q: 部署后输入正确的账号密码，却又跳回登录页？

A: 先在浏览器网络面板检查登录请求及后续身份请求的状态，核对实例公网 URL、代理转发、Cookie 和后端会话配置，并关联服务端 `requestId` 与日志。此现象本身不能确定是密码错误或数据库问题。不要清空 PostgreSQL 数据卷来尝试修复登录。部署参数见 [部署指南](./guide/deployment.md)。

## Q: 如何修改 admin 密码？修改配置后不生效？

A: `BOOTSTRAP_ADMIN_PASSWORD` 用于初始化账号。`BootstrapAdminInitializer` 在本地凭证已存在时保留原密码，因此修改环境变量或重建容器不会重置已有账号密码。使用实例提供的修改/找回密码流程；邮件验证码需要正确配置 SMTP。不要删除用户或数据库重新初始化。

## Q: 修改 / 找回密码必须使用邮箱验证码吗？

A: 网页找回密码使用邮箱验证码；已登录且知道原密码的修改接口不使用该验证码。找回流程需先配置 [SMTP](https://github.com/iflytek/skillhub/blob/main/docs/19-smtp-password-reset-email-setup.md)。修改 bootstrap 环境变量不能重置已经初始化的密码。

## Q: skill 可以起中文名吗？

A: 技能标识 `name` / slug 使用符合校验规则的名称；面向用户的展示名支持中文，可通过 `metadata.display-name` 配置。展示名不改变安装坐标或目录名。见 [发布指南](./guide/skill-publish.md)。

## Q: 未审核的 skill 可以下载吗？

A: 正常安装面向已发布且当前用户可访问的版本。能查看详情不等于能下载任意状态的版本；作者或审核人员的预览权限与公开分发权限不同。见 [审核与治理](./guide/review.md)。

## Q: 如何隐藏或删除登录页的第三方 SSO 登录方式？

A: 登录入口是配置驱动的：`/api/v1/auth/methods` 只返回配置了真实 client id 的
注册，client id 为空或包含 `placeholder` 时该入口不会出现在登录页。

所以隐藏某个入口有两种方式：

- 留空对应的环境变量即可（例如不设置 `OAUTH2_FEISHU_CLIENT_ID`），无需改动配置文件。
- 或修改 `application.yml`，注释/删除 `spring.security.oauth2.client.registration`
  下对应的注册块（`github`、`gitlab`、`feishu`、`dingtalk`）以及对应的 `provider` 段，
  Spring Boot 启动时便不会创建该注册。

## Q: SkillHub 的安全扫描（Skill Scanner）是讯飞自研的吗？使用什么协议？

A: SkillHub 内置安全扫描能力。其中扫描接入、任务编排、审计落库和部署集成由讯飞团队实现；底层扫描服务使用 Cisco 的 [cisco-ai-skill-scanner](https://github.com/cisco-ai-defense/skill-scanner)（Apache License 2.0，版权归 Cisco）。

## Q: SkillHub 使用的 cisco-ai-skill-scanner 是哪个版本？

A: `scanner/Dockerfile` 已固定使用 `cisco-ai-skill-scanner==2.1.0`。Scanner 镜像基于 glibc Linux，支持 `linux/amd64` 和 `linux/arm64`。

## Q: Scanner 应该使用 upload mode 还是 local mode？

A: 官方 Compose 和 Kubernetes 部署使用 `upload` mode，通过 `POST /scan-upload` 上传技能包。上传大小上限由 `SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES` 控制，默认是 `110100480` 字节（105 MiB）。

`local` mode 仅适用于 Server 与 Scanner 能在**相同路径**看到同一目录的部署。双方需要共享挂载路径，并在 Scanner 中设置允许的根目录；使用标准路径时配置 `SKILL_SCANNER_ALLOWED_ROOTS=/tmp/skillhub-scans`。

## Q: 安全审计显示“未发现高风险问题”，是否代表没有任何 findings？

A: 不是。Scanner 返回 `is_safe=true`、UI 显示“未发现高风险问题”，仅表示没有发现高风险问题；低等级 findings 仍可能存在，`findingsCount` 也可能大于 0。请继续查看 findings 明细，而不要把该状态理解为无条件安全保证。

## Q: 如何滚动升级到 Scanner 2.1.0？

A: 必须先部署兼容 2.1.0 协议的 Server，在旧 Scanner 仍运行时排空并下线所有旧 Server 实例及其进行中的扫描，然后再升级 Scanner，最后验证 `/health` 和一次 upload mode 扫描。不要让旧 Server 连接 Scanner 2.1.0。

混合版本期间，upload 和 local mode 都应保持 AI Defense 关闭（默认 `SKILLHUB_SCANNER_USE_AI_DEFENSE=false`）。如果升级前必须继续使用 AI Defense，应按旧 Scanner 版本支持的环境变量把凭据直接配置到旧 Scanner 环境中；不要把 AI Defense key 放入 URL query 参数或请求体。

## Q: 使用 CLI `skillhub publish` 报错 `registry returned 400` 怎么排查？

A: 400 通常是后端校验未通过。常见原因：

- `SKILL.md` 不在技能包根目录；
- `SKILL.md` 的 frontmatter 缺少 `name` / `description` 或格式错误；
- 名称或版本冲突（如 `error.skill.publish.nameConflict`，表示该 namespace 下已存在同名的已发布技能）——可改 `SKILL.md` 里的 `name`、换一个 namespace，或让管理员处理已有同名技能；
- namespace 不存在，或你不是该 namespace 的成员；
- 包内含疑似 token/secret，CLI 无法确认跳过；
- 文件类型 / 大小 / 路径不合规。

可用以下命令查看服务端日志定位：

```bash
docker logs --tail=300 <skillhub-server 容器名> 2>&1 | grep -Ei 'publish|SKILL.md|namespace|400|BadRequest'
```

## Q: 技能包的目录结构有什么要求？

A: 技能包根目录必须包含一个 `SKILL.md` 文件，且其 frontmatter 需包含 `name`、`description` 等字段。

## Q: 技能包里可以放 API Key、Token 等凭据吗？

A: 不建议。发布前校验会识别疑似硬编码的 token / secret，并拦截或要求确认（见上文 `registry returned 400` 的排查项）；自 v0.2.20 起，已减少对普通标识符、函数返回值、属性访问和字符串表达式的误报。这只是针对部分文件类型和凭据格式的启发式校验，不能保证发现所有真实密钥，也不会自动脱敏；发布前仍需自行检查并移除真实凭据。

推荐做法是：技能包里只写需要的环境变量名或占位符，并在 `SKILL.md` 中说明需要配置哪些凭据，由使用方在运行 Skill 的 Agent 环境中配置真实值。目前 SkillHub 不提供为 Skill 托管或注入密钥的功能。

## Q: 发布时报“技能包校验失败 / malformed input”怎么办？

A: 该错误发生在 zip 解包读取文件名阶段，通常是压缩包不是 UTF-8 编码（例如用 Windows 自带压缩工具生成）或包内含中文路径导致。请使用 UTF-8 编码重新打包，并避免中文 / 特殊字符路径。

## Q: 技能包能包含多少个文件？提示文件数超限怎么办？

A: 默认上限为 **100 个文件**（这与 100MB 的大小限制是两回事）。如需放宽，修改配置项 `skillhub.publish.max-file-count`，或在部署时用环境变量覆盖：

```bash
SKILLHUB_PUBLISH_MAX_FILE_COUNT=500
```

修改后需重新创建容器才会生效；仅执行 `restart` 不会重新注入环境变量。注意 `compose.release.yml` 中也需引用该变量；较旧版本（如 v0.2.6）可能将该值写死，建议升级到最新版本。

## Q: 使用 CLI（发布 / 下载等）对服务端版本有要求吗？

A: 兼容性取决于具体命令。旧版本支持基础 CLI 不代表支持 Device Flow、Suite 或同步；请核对 CLI 版本与目标服务端发行说明。Suite 要求服务端声明 `skill-suite-v1`。见 [CLI 指南](./guide/cli.md)。

## Q: SkillHub 支持 MySQL 数据库吗？

A: 目前仅支持 PostgreSQL，暂不支持 MySQL。

## Q: SkillHub 可以用来分发 Plugin 吗？

A: 暂不支持。

## Q: 如何查看 SkillHub 的版本？想做定制（如修改 logo）怎么办？

A:

- 查看服务端镜像版本：

```bash
docker image inspect ghcr.io/iflytek/skillhub-server:latest --format '{{index .Config.Labels "org.opencontainers.image.version"}}'
```

- 查看 CLI 版本：`skillhub version`。
- 如需定制（如修改 logo 等），建议基于最新代码进行二次开发并自行构建 docker 镜像。

## Q: 页面能打开，但登录 / 注册接口返回 502？

A: 页面由 `web` 容器提供，登录、注册等接口由 `web` 转发给 `server`（默认 `SKILLHUB_API_UPSTREAM=http://server:8080`）。出现「页面正常但 API 502」时，通常先检查 `server` 是否正常启动；upstream 配置、DNS 或容器网络异常也可能返回 502。

排查顺序：

```bash
# 1. 看 server 是否处于运行状态
docker compose --env-file .env.release -f compose.release.yml ps

# 2. 看 server 启动日志中的第一条错误
docker compose --env-file .env.release -f compose.release.yml logs server | head -50
```

一条常见的启动失败日志是：

```
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must not use the default placeholder
```

说明 `server` 读到的仍是模板里的占位值。在 `.env.release` 中改成自己的随机字符串（**至少 32 个字符**）后重建容器即可：

```bash
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<替换成你自己的随机字符串，至少 32 个字符>
```

启动前可以先执行 `make validate-release-config`，它会校验 `.env.release`，提前暴露这类占位值和缺失项。

## Q: 改了配置为什么不生效？

A: 两个高频原因：

1. **改错了文件**：`.env.release.example` 只是模板，Compose 实际读取的是 `--env-file` 指定的 `.env.release`。请先 `cp .env.release.example .env.release`，然后修改 `.env.release`。
2. **只重启没重建**：环境变量在容器创建时注入，`restart` 不会重新注入。改完配置需要重建容器：

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --force-recreate
```

## Q: 启动时报 `bind: address already in use`（80 端口被占用）怎么办？

A: 官方 Compose 默认把 Web 入口映射到宿主机 `80` 端口、API 映射到 `8080` 端口。端口被占用时，可以先用 `netstat -tlnp` 或 `lsof -i :80` 找到占用进程；如果不方便停止它，就在 `.env.release` 中修改对外端口（一键部署脚本使用 `${TMPDIR:-/tmp}/skillhub-runtime/.env.release`；若设置了 `SKILLHUB_HOME` 或通过 `--home` 指定目录，则以该目录为准）：

```bash
WEB_PORT=8081
# 如 8080 也被占用
API_PORT=18080
```

修改端口后，请同步把 `SKILLHUB_PUBLIC_BASE_URL` 改成包含新端口的实际访问地址（例如 `http://your-host:8081`），否则登录回调等跳转可能出错。然后进入 `.env.release` 与 `compose.release.yml` 所在的实际运行目录，按上一条的方法用 `up -d --force-recreate` 重建容器。

## Q: SkillHub 运行时需要哪些外部依赖？

A: 必需 PostgreSQL 和 Redis；对象存储支持 `local` 与 S3 两种模式，由 `SKILLHUB_STORAGE_PROVIDER` 控制。`.env.release.example` 显式配置为 `local`，但如果使用 `compose.release.yml` 时完全没有设置该变量，Compose 的回退值是 `s3`。建议始终显式设置；生产环境推荐使用 S3（通过 `SKILLHUB_STORAGE_S3_*` 配置）。数据库仅支持 PostgreSQL，暂不支持 MySQL。

发布版 Compose 已内置 PostgreSQL 与 Redis，默认只绑定在 `127.0.0.1`。

## Q: PostgreSQL 容器写入 `postmaster.pid` 或 `pg_wal` 时报告 `operation not permitted` 怎么办？

A: SkillHub 默认的 Compose 和 `runtime.sh` 使用 Docker named volume（`postgres_data`），通常不需要手工处理宿主机目录权限。这个错误更常见于将 PostgreSQL 数据目录改成宿主机 bind mount，例如 `/data/skillhub/postgres:/var/lib/postgresql/data`。

按以下顺序排查：

1. 优先恢复为 Docker named volume，或使用官方 `runtime.sh`，避免手写 Compose 时漏配权限。
2. 如果必须使用 bind mount，先确认 `.env.release` 或 `runtime.sh` 参数最终选择的 `POSTGRES_IMAGE`，将该值导出到当前 shell 后运行 `docker run --rm "$POSTGRES_IMAGE" id postgres`。再按输出的实际 UID/GID 调整数据目录属主，例如 `chown -R <uid>:<gid> <数据目录>`。不要固定假设镜像是 `postgres:16-alpine`，也不要假设所有环境都是 `999:999`。
3. 在 RHEL/CentOS 上检查 SELinux；使用 AppArmor、rootless Docker、NFS、CIFS 或 NAS 时，也要确认宿主文件系统允许 PostgreSQL 写入、加锁和更改权限。
4. 不建议把 PostgreSQL `PGDATA` 放在缺少完整 POSIX 权限语义的网络文件系统上。生产环境优先使用本地盘、Docker named volume、块存储或外部 PostgreSQL。

## Q: 通过 OAuth（GitHub / GitLab 等）登录的账号，如何取得管理员权限？

A: OAuth 首次登录创建的是普通用户。需要由已有的 `SUPER_ADMIN`（例如初始化时的 bootstrap admin）在后台将其提升为管理员。

`USER_ADMIN` 可以管理用户状态，并分配除 `SUPER_ADMIN` 之外的平台角色；但不能向任何账号授予 `SUPER_ADMIN`，也不能修改已有 `SUPER_ADMIN` 账号的角色。这两类操作只有 `SUPER_ADMIN` 可以执行。

## Q: 如何批量安装多个技能包？

A: 需要固定成员版本并统一升级时，使用 [技能套装](./guide/skill-suites.md) 的 `skillhub suite install`。同步团队命名空间工作区时，使用 [CLI](./guide/cli.md) 的 `skillhub sync pull`。若只是独立安装几个无关技能，可分别运行 `skillhub install`；Suite Bundle 是发布者的导入格式，不是本地安装命令。

## Q: 遇到问题怎么办？

A: 可以通过以下方式获取帮助：

- **GitHub Issues**: https://github.com/iflytek/skillhub/issues
- **在线文档**: https://iflytek.github.io/skillhub/
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
# 确认进程用途后，通过其启动工具停止服务
```

### 4. 查看详细日志

如果以上方案无法解决，查看后端日志：

```bash
make dev-logs SERVICE=backend
# 或直接查看
cat .dev/server.log
```
