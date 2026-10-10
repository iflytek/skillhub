# 认证扩展与私有 SSO 兼容设计

## 1. 目标

在不影响当前开源版 OAuth 和本地账号登录能力的前提下，为未来私有仓库接入企业 SSO 预留稳定扩展点，并把代码差异控制在 provider 实现层和少量配置层。

## 2. 已确认约束

- 私有 SSO 能提供稳定唯一 UID
- 用户名密码校验与 Cookie 会话校验都会返回同一稳定 UID
- 生产部署预期为 `skill.xxx.com` 与 `sso.xxx.com`
- 私有版可通过后端内部接口/RPC 代调用 SSO 校验用户名密码
- 首次 SSO 登录自动创建 skillhub 账号
- 不做账号合并设计，不依赖 email
- 登出联动可保留扩展点，但不是近期目标

## 3. 开源版兼容策略

### 3.1 不改变现有主链路

- 现有 OAuth 登录流程保持不变
- 现有本地用户名密码登录保持不变
- 现有 `/api/v1/auth/providers` 协议保持不变
- 不在开源版中引入私有 SSO 的真实实现

### 3.2 新增的公共扩展协议

开源版新增显式被动会话引导接口：

- `POST /api/v1/auth/session/bootstrap`

请求：

```json
{
  "provider": "private-sso"
}
```

行为约束：

- 默认关闭，由 `skillhub.auth.session-bootstrap.enabled=false` 控制
- 关闭时返回 `403`
- provider 不存在时返回 `400`
- 外部会话校验失败时返回 `401`
- 成功时建立 skillhub Session，并返回当前用户信息

同时新增默认关闭的直连认证兼容接口：

- `POST /api/v1/auth/direct/login`

请求：

```json
{
  "provider": "private-sso",
  "username": "alice",
  "password": "secret"
}
```

行为约束：

- 默认关闭，由 `skillhub.auth.direct.enabled=false` 控制
- 关闭时返回 `403`
- provider 不存在时返回 `400`
- 成功时建立 skillhub Session，并返回当前用户信息
- 开源版仍保留原始 `/api/v1/auth/local/login`

### 3.3 代码级扩展点

```java
public interface PassiveSessionAuthenticator {
    String providerCode();
    Optional<PlatformPrincipal> authenticate(HttpServletRequest request);
}
```

```java
public interface DirectAuthProvider {
    String providerCode();
    PlatformPrincipal authenticate(DirectAuthRequest request);
}
```

私有版只需要新增实现，例如：

- `private-sso-cookie`：读取共享 Cookie 并向 SSO 校验
- 后续如果需要，也可以补“用户名密码直连认证 provider”扩展点

为减少私有 fork 的前端硬编码，扩展 provider 可额外声明展示名称：

- `DirectAuthProvider.displayName()` 默认回退为 `providerCode()`
- `PassiveSessionAuthenticator.displayName()` 默认回退为 `providerCode()`
- `GET /api/v1/auth/methods` 会返回该展示名称，供登录页直接渲染

## 4. 本轮已落地内容

- 新增 `PassiveSessionAuthenticator` SPI
- 新增 `DirectAuthProvider` SPI
- 新增统一会话建立服务 `PlatformSessionService`
- 新增 `POST /api/v1/auth/session/bootstrap` 协议
- 新增 `POST /api/v1/auth/direct/login` 协议
- 新增 `skillhub.auth.direct.enabled` 开关，默认关闭
- 新增 `skillhub.auth.session-bootstrap.enabled` 开关，默认关闭
- 前端新增基于运行时配置的账号密码兼容接入层
- 前端新增基于运行时配置的被动会话兼容入口
- 前端新增显式按钮和可选自动尝试逻辑，默认都不启用
- 增加 controller 集成测试，验证：
  - 默认关闭时不会影响现有系统
  - 启用并提供 authenticator 时可以建立 skillhub Session

统一会话建立约束：

- 本地登录、OAuth 成功回调、direct auth、session bootstrap、mock 登录旁路都走 `PlatformSessionService`
- 会话写入统一依赖 `HttpSession` 属性：`platformPrincipal` 与 `SPRING_SECURITY_CONTEXT`
- 因此在生产环境启用 Spring Session Redis 时，不需要为不同登录方式分别处理 Session 序列化或存储逻辑
- 交互式登录默认轮换 session id；OAuth 这类已在 Spring Security 认证链中的流程复用现有 `Authentication`

前端运行时配置：

- `SKILLHUB_WEB_AUTH_DIRECT_ENABLED`
- `SKILLHUB_WEB_AUTH_DIRECT_PROVIDER`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO`

使用方式：

1. 若要做密码直连，后端启用 `skillhub.auth.direct.enabled=true`
2. 私有版提供 `DirectAuthProvider` 实现
3. 前端设置 `SKILLHUB_WEB_AUTH_DIRECT_*`
4. 若要做被动会话，后端启用 `skillhub.auth.session-bootstrap.enabled=true`
5. 私有版提供 `PassiveSessionAuthenticator` 实现
6. 前端设置 bootstrap provider 和开关
7. 登录页显示兼容入口，或在配置允许时自动尝试一次 bootstrap

## 5. 后续建议

- 私有版实现 `DirectAuthProvider` 和 / 或 `PassiveSessionAuthenticator` 时，只扩展 provider 层，不复制 session 建立逻辑
- 私有版优先采用显式 bootstrap，而不是透明全局拦截器自动登录
- 如后续需要登出联动，只通过 `LogoutPropagationHandler` 扩展，不改动现有主登出链路

## 6. 实施手册

更详细的私有 SSO 接入步骤、最佳实践、测试矩阵和给后续 coding agent 的执行约束，见：

- [12-private-sso-integration-playbook.md](/Users/xudongsun/github/skillhub/docs/12-private-sso-integration-playbook.md)
