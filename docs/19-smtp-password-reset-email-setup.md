# SkillHub SMTP 邮箱配置指南（验证码邮件）

本文说明如何为 SkillHub 配置 SMTP，用于发送“密码重置验证码”邮件。

适用场景：
- 生产/预发布环境（`compose.release.yml` + `.env.release`）
- 本地联调环境（直接注入后端环境变量）

补充说明：
- SMTP 本质是邮件传输协议，不是单一厂商产品。
- 你可以使用企业邮箱、云邮箱或本地测试 SMTP 服务（例如 MailHog）作为 SMTP 服务端。

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
- 若出现 `PKIX path building failed` / `SSLHandshakeException`，可尝试增加 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=<SMTP_HOST>`（本地联调常用）。
- 生产环境默认不建议配置 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST`，仅在证书链异常时临时启用。
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
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.mail.example
SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name
```

本地 `export` 示例写法：

```bash
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.mail.example
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
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.mail.example
SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name
```

## 2. 单机交付（Compose）配置步骤

1. 复制环境模板（若尚未创建）：

```bash
cp .env.release.example .env.release
```

2. 编辑 `.env.release`，填写 SMTP 变量：

```dotenv
SPRING_MAIL_HOST=smtp.mail.example
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=mailer@example.com
SPRING_MAIL_PASSWORD=your-smtp-app-password
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.mail.example

SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name
```

3. 重启后端容器使配置生效：

```bash
docker compose --env-file .env.release -f compose.release.yml up -d server
```

4. 查看后端日志确认启动正常：

```bash
docker compose --env-file .env.release -f compose.release.yml logs -f server
```

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
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.mail.example \
SKILLHUB_AUTH_PASSWORD_RESET_CODE_EXPIRY=PT10M \
SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS=mailer@example.com \
SKILLHUB_AUTH_PASSWORD_RESET_FROM_NAME=your-from-name \
make dev-server
```

### 3.2 长期生效（shell 配置）

如果你写到了 `~/.zshrc`，请注意：
- 必须 `source ~/.zshrc` 或重开终端后变量才会生效
- 需要在“同一个终端”启动 `make dev-server`

可先确认变量是否在当前 shell 中：

```bash
env | rg '^(SPRING_MAIL_|SKILLHUB_AUTH_PASSWORD_RESET_)'
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
env | rg '^(SPRING_MAIL_|SKILLHUB_AUTH_PASSWORD_RESET_)'
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

处理建议（本地联调）：
- 增加：

```dotenv
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.mail.example
```

- 然后重启后端，再触发一次“发送验证码”。

补充：
- 该配置用于指定信任主机，适合本地排障与联调。
- 生产环境默认不建议长期启用该配置，更推荐使用规范 CA 证书链或将企业 CA 导入 Java truststore。

## 6. 安全建议

- 不要把 SMTP 密码提交到仓库；仅写入受控的 `.env.release` 或密钥管理系统。
- 使用专用发信账号，避免使用个人邮箱主密码。
- 生产环境建议定期轮换 SMTP 授权码。
