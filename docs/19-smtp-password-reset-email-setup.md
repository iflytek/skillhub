# SkillHub SMTP 邮箱配置指南（验证码邮件）

本页说明如何为 SkillHub 配置 SMTP，用于发送“密码重置验证码”邮件。

审核、发布、订阅更新等业务邮件及通知收件验证，请另见[邮件通知接入与使用](notification-email.md)。两类邮件复用 SMTP 连接，但发件人配置、验证码用途和用户订阅相互独立。

适用场景：
- 生产/预发布环境（`compose.release.yml` + `.env.release`）
- 本地联调环境（直接注入后端环境变量）

补充说明：
- SMTP 本质是邮件传输协议，不是单一厂商产品。
- 可使用企业邮箱、云邮箱或本地测试 SMTP 服务（例如 MailHog）作为 SMTP 服务端。

当前密码重置页面入口说明：
- 当前前端统一使用 `/reset-password` 页面。
- 该页面同时包含“发送验证码”和“提交新密码”两步，不再单独使用 `/forgot-password`。

## 1. 需要配置的环境变量

以下变量已被后端读取：

| 变量名 | 说明 | 示例 |
|---|---|---|
| `SPRING_MAIL_HOST` | SMTP 服务器地址 | `smtp.example.com` |
| `SPRING_MAIL_PORT` | SMTP 端口 | `465` |
| `SPRING_MAIL_USERNAME` | SMTP 用户名 | `noreply@example.com` |
| `SPRING_MAIL_PASSWORD` | SMTP 密码/授权码 | `xxxxxx` |
| `SPRING_MAIL_SMTP_AUTH` | 是否启用 SMTP AUTH | `true` |
| `SPRING_MAIL_SMTP_STARTTLS_ENABLE` | 是否启用 STARTTLS | `false` |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | 是否启用 SMTP SSL 直连 | `true` |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST` | SSL 信任主机（用于规避部分环境下证书链校验失败） | `smtp.mail.example` |
| `SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY` | 验证码有效期（ISO-8601 Duration） | `PT10M` |
| `SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS` | 发件人邮箱 | `noreply@example.com` |
| `SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME` | 发件人名称 | `SkillHub` |

说明：
- 当前文档统一按 `465 + SSL` 配置，不再展开 `587 + STARTTLS` 方案。
- 使用 `465` 时配置：`STARTTLS=false`、`SSL_ENABLE=true`。
- 若出现 `PKIX path building failed` / `SSLHandshakeException`，先检查 SMTP 证书链与 JVM 信任库；不要直接关闭 TLS 或绕过证书校验。
- 默认不得配置 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST`，以使用 JVM 证书信任库。旧 `.env.release` 若包含 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=` 空行配置，必须删除该配置行后重建后端；空字符串会导致 `Server is not trusted`，不能用它表示“使用默认信任库”。只有经过部署方确认的证书信任例外才填写明确主机名，不得填写 `*`。
- `SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY` 支持如 `PT5M`、`PT10M`、`PT30M`。

## 1.1 配置方案速查（推荐）

### A. 通用 SMTP 邮箱（本地直连真实邮箱）

```dotenv
SPRING_MAIL_HOST=smtp.mail.example
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=mailer@example.com
SPRING_MAIL_PASSWORD=your-smtp-app-password
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name
```

本地 `export` 示例写法：

```bash
export SPRING_MAIL_HOST=smtp.mail.example
export SPRING_MAIL_PORT=465
export SPRING_MAIL_USERNAME=mailer@example.com
export SPRING_MAIL_PASSWORD=your-smtp-app-password
export SPRING_MAIL_SMTP_AUTH=true
export SPRING_MAIL_SMTP_STARTTLS_ENABLE=false
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
export SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M
export SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com
export SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name
```

### B. MailHog（本地联调推荐）

```dotenv
SPRING_MAIL_HOST=127.0.0.1
SPRING_MAIL_PORT=1025
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=false
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=noreply@skillhub.local
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=SkillHub
```

### C. 线上部署（465 端口示例）

```dotenv
SPRING_MAIL_HOST=smtp.mail.example
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=mailer@example.com
SPRING_MAIL_PASSWORD=your-smtp-app-password
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name
```

## 2. 单机交付配置步骤

### 一键安装的已有实例

1. 从安装输出找到 **Runtime dir** 和 **Lifecycle commands**。只编辑该实例 Runtime dir 中已有的 `.env.release`；不要重新复制模板，不要改用新的 `--home`。未指定目录时脚本默认 `${TMPDIR:-/tmp}/skillhub-runtime`，不是 `~/.skillhub`。
2. 文件权限保持 `600`，按第 1 节填写 SMTP、TLS、密码或授权码，并设置获 SMTP 许可的 `SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS`。真实秘密不得放在命令参数、日志或 Git 中；不要执行 `source .env.release`。包含特殊字符的密码按 Compose `.env` 单引号字面值语法保存。
3. 若该 SMTP 还用于业务通知，先在后台关闭业务邮件；修改现有环境的 seed 开关不会覆盖已保存的平台设置。业务邮件与通知验邮使用 `SKILLHUB_NOTIFICATION_EMAIL_FROM_ADDRESS`，不是密码重置发件人参数。
4. 保存后重新执行该实例输出的 **up** 命令，保留原 `--home`、文件来源及镜像参数。没有单独的 SMTP 命令；不能只执行 restart 或 pull。脚本保留现存环境文件，更新配置后由 Compose 重建受影响容器；使用 ps／logs 命令核对后端状态。
5. 在 `/reset-password` 为受控测试账号申请验证码，实际检查收件箱及垃圾邮件并验证重置流程。SMTP 已配置或容器健康不证明实际收件。密码重置不要求先开启业务邮件，也不要求用户先完成通知邮箱验邮。
6. 同时使用业务邮件时，按[一键部署邮件接入流程](notification-email.md)先在协调窗口重新启用平台，再用未验邮测试账号验证通知验证码，或用已验邮账号触发新业务事件验证即时／摘要；不能在平台关闭时进行这些测试。既有合格订阅用户在启用期间的新事件也可能发信；失败则后台关闭，不盲目重试 UNKNOWN，测试通过后恢复测试偏好并正常开放。

包含本次修复的脚本会移除旧 `.env.release` 中空的 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST`，保留明确主机例外及其他 SMTP 行。旧脚本需先更新，或手动删除该空值行后重跑 up。不要配置 `*`；正常服务端证书使用 JVM 信任库。

运行目录只含部署文件，不包含 Makefile 和源码检查脚本，不在该目录运行 `make validate-release-config`。固定发行时匹配已发布的脚本、`--ref` 和镜像 `--version`；使用阿里云、镜像仓库或其他选项的原实例继续保留这些选项。详细来源与升级边界见[部署运维手册](09-deployment.md)。

### 已有源码仓库的 Compose 实例

首次准备时才复制 `.env.release.example`；已有配置文件不覆盖。编辑 SMTP 参数后，在仓库根目录执行：

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d --no-deps server
docker compose --env-file .env.release -f compose.release.yml logs --tail=100 server
```

以上适用于未自定义项目名或 override 的已有栈；原部署若使用 `-p`、`COMPOSE_PROJECT_NAME` 或其他 `-f` 文件，必须保留全部原参数，防止操作另一实例或丢失设置。首次部署使用完整栈启动，不照抄 `--no-deps`。`restart` 不更新容器环境。配置检查输出可能带配置值，不公开粘贴完整输出。

## 3. 本地开发配置与验证

### 3.1 一次性临时生效（推荐）

适合当前终端临时测试，重开终端后失效。

```bash
SPRING_MAIL_HOST=smtp.mail.example \
SPRING_MAIL_PORT=465 \
SPRING_MAIL_USERNAME=mailer@example.com \
SPRING_MAIL_PASSWORD=your-smtp-app-password \
SPRING_MAIL_SMTP_AUTH=true \
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false \
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true \
SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M \
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com \
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name \
make dev-server
```

### 3.2 长期生效（shell 配置）

如果你写到了 `~/.zshrc`，请注意：
- 必须 `source ~/.zshrc` 或重开终端后变量才会生效
- 需要在“同一个终端”启动 `make dev-server`

只确认所需变量是否存在，不输出变量值：

```bash
for key in SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS; do
  if [ -n "$(printenv "$key")" ]; then
    printf '%s: configured\n' "$key"
  else
    printf '%s: missing\n' "$key"
  fi
done
```

### 3.3 推荐联调方式（MailHog）

如果你只是本地验证验证码链路，建议用 MailHog 作为本地 SMTP 服务：

1. 启动 MailHog：

```bash
docker run -d --name skillhub-mailhog \
  -p 1025:1025 \
  -p 8025:8025 \
  mailhog/mailhog
```

2. 启动依赖服务（Postgres/Redis）：

```bash
make dev
```

3. 启动后端时注入 SMTP 环境变量（示例）：

```bash
SPRING_MAIL_HOST=127.0.0.1 \
SPRING_MAIL_PORT=1025 \
SPRING_MAIL_USERNAME= \
SPRING_MAIL_PASSWORD= \
SPRING_MAIL_SMTP_AUTH=false \
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false \
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false \
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=noreply@skillhub.local \
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=SkillHub \
make dev-server
```

4. 打开 MailHog Web UI 查看邮件：

```text
http://localhost:8025
```

5. 在 SkillHub 页面验证流程：
- 打开 `/reset-password`
- 输入邮箱并点击“发送验证码”
- 在 MailHog 中查看验证码邮件
- 输入邮箱 + 验证码 + 新密码完成重置

6. 也可使用接口做快速验证（示例）：

```bash
curl -X POST http://localhost:8080/api/v1/auth/local/password-reset/request \
  -H 'Content-Type: application/json' \
  -d '{"email":"your-email@example.com"}'
```

## 4. 功能验证（验证码邮件）

### 4.1 用户自助找回

在 `/reset-password` 页面点击“发送验证码”后，系统会尝试发送验证码邮件。

说明：
- 为防止账号枚举，自助接口总是返回通用成功提示。
- 即使邮件发送失败，接口也可能返回成功；请结合后端日志确认实际发送结果。

### 4.2 管理员触发重置

管理员在用户管理页触发“重置密码”时，系统会强制发送验证码；
若 SMTP 发送失败，会返回错误（便于运维排障）。

## 5. 常见问题排查

### 5.1 认证失败（`535 Authentication failed`）

排查方向：
- 用户名/密码是否正确
- 邮箱服务是否要求“客户端授权码”而非登录密码
- 发件账号是否已开启 SMTP 服务

### 5.2 连接超时或拒绝连接

排查方向：
- 主机到 SMTP 服务端口 `465` 是否可达
- 安全组/防火墙是否放行出站连接
- SMTP 服务地址是否填写正确

### 5.3 本地明明配置了变量但不生效

排查方向：
- 是否只是编辑了 `~/.zshrc` 但没有 `source ~/.zshrc`
- 启动后端的终端是否与配置变量的终端是同一个
- `8080` 是否被旧进程占用，导致新进程没启动成功

可执行以下命令快速检查：

```bash
# 查看 8080 是否被旧进程占用
lsof -nP -iTCP:8080 -sTCP:LISTEN

# 查看当前 shell 是否有 SMTP 环境变量
for key in SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS; do
  if [ -n "$(printenv "$key")" ]; then
    printf '%s: configured\n' "$key"
  else
    printf '%s: missing\n' "$key"
  fi
done
```

### 5.4 发件人被拒绝

排查方向：
- `SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS` 是否与 SMTP 账号一致或已验证
- 邮箱服务是否限制别名发件

### 5.5 健康检查是否校验 SMTP

默认配置下，邮件健康检查关闭，不会因为 SMTP 不可达导致 `health` 失败。

若需要将 SMTP 连通性纳入健康检查，可设置：

```dotenv
MANAGEMENT_HEALTH_MAIL_ENABLED=true
```

### 5.6 SMTP 报 `PKIX path building failed`（证书链校验失败）

典型日志：
- `SSLHandshakeException`
- `unable to find valid certification path to requested target`

先核对 SMTP 证书链及 JVM 信任库；企业自签 CA 必须通过部署方认可的证书管理流程导入信任库。不要直接关闭 TLS 或配置通配信任。

仅经部署方确认的临时主机信任例外，才按第 1 节设置明确的 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST` 主机名；修复证书链后必须移除该例外。修改部署配置后重建后端，再验证 SMTP 连接及实际收件。

## 6. 安全建议

- 不要把 SMTP 密码提交到仓库；仅写入受控的 `.env.release` 或密钥管理系统。
- 使用专用发信账号，避免使用个人邮箱主密码。
- 生产环境建议定期轮换 SMTP 授权码。
