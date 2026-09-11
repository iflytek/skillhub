# AI 变更记录：CLI OAuth Device Flow 登录

## 需求背景

SkillHub 服务端已经提供 Device Code 申请、授权轮询和浏览器确认能力，但 CLI `0.1.12`
在没有 API Token 时仍直接返回 `token is required`，无法使用既有的 OAuth 登录链路。
对应社区需求记录在 Issue #856。

## 实现内容

- `skillhub login` 未显式提供 Token 时，调用现有 `/api/v1/auth/device/code` 发起授权。
- 展示服务端返回的验证地址和用户码，并在受支持的平台上尝试打开默认浏览器。
- 增加 `--no-open`，支持远程服务器和无图形界面的终端。
- 按服务端返回的 `interval` 和 `expiresIn` 轮询 `/api/v1/auth/device/token`，处理等待、拒绝、过期和网络失败。
- 获取 Bearer Token 后先调用 `whoami` 验证，成功后才写入现有配置与凭据存储。
- 保留 `--token`、`SKILLHUB_TOKEN` 和已存 Token 的兼容行为，供 CI 与自动化使用。
- JSON 模式只输出验证地址、用户码、有效期和浏览器打开状态，不输出 device code 或 access token。

## 安全边界

- 只允许打开 HTTP/HTTPS 验证地址，不通过 shell 拼接 URL。
- access token 不进入标准输出或错误输出。
- Device Flow 失败、拒绝或超时时不更新本地凭据。
- 浏览器打开失败不会中断流程，用户仍可复制终端中的验证地址继续授权。

## 测试策略

- 单元测试覆盖轮询成功、等待、拒绝、过期、非法服务端参数和跨平台浏览器启动。
- 客户端测试验证 Device Flow 使用公开端点且不携带已有 Bearer Token。
- 集成测试覆盖普通输出、JSON 输出、`--no-open`、Token 不泄漏及原有 Token 登录兼容性。
