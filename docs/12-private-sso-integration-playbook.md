# 私有 SSO 接入兼容层实施手册

## 1. 文档目的

本文档面向两类读者：

- 后续在私有仓库中接入企业 SSO 的开发者
- 需要基于当前开源版兼容层继续开发的 coding agent

本文档不是认证架构总览，而是实施手册。目标是让后续执行者在不了解全部历史上下文的情况下，也能基于当前成果直接开始接入工作，并且尽量把私有仓库与开源仓库的差异控制在 provider 实现层和少量配置层。

相关文档：

- [03-authentication-design.md](/Users/xudongsun/github/skillhub/docs/03-authentication-design.md)
- [06-api-design.md](/Users/xudongsun/github/skillhub/docs/06-api-design.md)
- [08-frontend-architecture.md](/Users/xudongsun/github/skillhub/docs/08-frontend-architecture.md)
- [11-auth-extensibility-and-private-sso.md](/Users/xudongsun/github/skillhub/docs/11-auth-extensibility-and-private-sso.md)

## 2. 当前上下文与已确认约束

本轮改造的真实目标不是在开源版里实现私有 SSO，而是先把开源版前后端改造成一个稳定的兼容接入层。

已经确认的业务前提如下：

- 私有 SSO 能返回稳定且唯一的 UID
- 用户名密码校验接口与基于 Cookie 的会话校验接口都返回同一个 UID
- SkillHub 私有版与私有 SSO 会部署在统一主域下，例如 `skill.xxx.com` 与 `sso.xxx.com`
- 私有版可以通过内部接口或 RPC 调用 SSO 的用户名密码校验能力
- 首次 SSO 登录自动创建 SkillHub 账号
- 不考虑账号合并
- 不依赖 email 字段
- 不要求联动登出，但可保留低优先级扩展点

这意味着后续私有 SSO 的正确接入方式是：

- 把 SSO 建模为新的认证来源 `private-sso`
- 用 `providerCode + subject` 表示外部身份，其中 `subject` 就是 SSO UID
- 复用当前平台的统一 Session 建立逻辑，而不是再造一套登录态机制

## 3. 当前兼容层已经提供了什么

### 3.1 后端扩展点

当前开源版已经提供以下后端兼容能力：

- `DirectAuthProvider`
  - 用于“前端收集用户名密码，后端调用外部系统校验”的模式
- `PassiveSessionAuthenticator`
  - 用于“浏览器自动带上 SSO Cookie，后端读取请求并向 SSO 校验”的模式
- `PlatformSessionService`
  - 用于统一建立 SkillHub Web Session
- `LogoutPropagationHandler`
  - 用于未来低优先级登出联动

关键代码位置：

- [DirectAuthProvider.java](/Users/xudongsun/github/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/direct/DirectAuthProvider.java)
- [PassiveSessionAuthenticator.java](/Users/xudongsun/github/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/bootstrap/PassiveSessionAuthenticator.java)
- [PlatformSessionService.java](/Users/xudongsun/github/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/session/PlatformSessionService.java)

### 3.2 后端公共协议

当前开源版已经提供以下兼容协议：

- `POST /api/v1/auth/direct/login`
- `POST /api/v1/auth/session/bootstrap`
- `GET /api/v1/auth/methods`

这些协议的设计原则如下：

- 默认关闭
- 默认没有私有 SSO 实现
- 启用后由 provider 扩展驱动
- 成功后统一建立标准 Spring Security Session
- 不替换现有 `/api/v1/auth/local/login`
- 不替换现有 OAuth 登录

### 3.3 前端兼容层

当前开源版前端已经支持通过运行时配置开启兼容入口：

- `SKILLHUB_WEB_AUTH_DIRECT_ENABLED`
- `SKILLHUB_WEB_AUTH_DIRECT_PROVIDER`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO`

前端设计原则如下：

- 默认不启用任何私有登录入口
- 开启后通过兼容层切换，不破坏现有登录页默认行为
- 优先走统一目录接口 `/api/v1/auth/methods`
- 被动会话登录优先使用显式 bootstrap，而不是页面加载时偷偷尝试多次

## 4. 私有 SSO 的推荐接入方案

### 4.1 推荐总策略

最佳实践不是只选一种方式，而是同时支持两条链路：

1. 主路径：`DirectAuthProvider`
   - 登录页展示企业 SSO 用户名密码表单
   - 后端通过内部接口或 RPC 调用私有 SSO 校验
   - 校验成功后给用户建立 SkillHub Session

2. 补充路径：`PassiveSessionAuthenticator`
   - 当用户已经在 SSO 系统登录过，并且浏览器会自动带上共享 Cookie 时
   - 登录页允许用户主动点击“从企业 SSO 登录”
   - 或在非常谨慎的前提下自动尝试一次 bootstrap

这样做的理由：

- 覆盖“尚未登录 SSO”和“已登录 SSO”两种用户状态
- 不依赖浏览器一定已持有 Cookie
- 不把所有登录成功率押在 Cookie 域、SameSite、过期策略等细节上
- 不改变开源版原始登录逻辑

### 4.2 不推荐的做法

以下做法不建议在私有版采用：

- 在全局 servlet filter 中对所有匿名请求自动尝试 SSO 登录
- 直接在 controller、filter 或 provider 里手写 `HttpSession` 和 `SecurityContext` 逻辑
- 把私有 SSO 的 UID 映射成临时整数 ID 再作为用户主标识
- 按 email 自动合并账号
- 让前端直接调用私有 SSO 的内部校验接口
- 为私有版新增一整套与开源版平行的“私有登录 session 机制”

## 5. 私有版最小差异实施方案

### 5.1 后端应新增什么

私有仓库建议只新增以下实现类，不改主链路：

1. 一个 `DirectAuthProvider` 实现
2. 一个 `PassiveSessionAuthenticator` 实现
3. 可选的 `LogoutPropagationHandler` 实现
4. 私有配置属性类或私有配置项
5. 若 SSO 返回的是外部 UID 而不是现成平台用户，需要补充“根据 SSO UID 查询或创建平台用户”的私有服务

建议命名示例：

- `PrivateSsoDirectAuthProvider`
- `PrivateSsoPassiveSessionAuthenticator`
- `PrivateSsoLogoutPropagationHandler`
- `PrivateSsoProperties`
- `PrivateSsoIdentityService`

不建议修改这些公共类的职责：

- `PlatformSessionService`
- `LocalAuthController`
- `AuthController`
- `SecurityConfig`

### 5.2 后端建议实现步骤

#### 步骤 1：定义 provider code

私有版统一使用稳定 provider code：

```text
private-sso
```

要求：

- `DirectAuthProvider.providerCode()` 和 `PassiveSessionAuthenticator.providerCode()` 返回同一个值
- 不要为“用户名密码登录”和“Cookie 登录”定义两个不同 provider code
- 如需更友好的登录页文案，请同时覆盖 provider 的 `displayName()`，避免前端再维护一份私有显示名映射

#### 步骤 2：封装 SSO 客户端

不要在 provider 实现里直接散落 HTTP 或 RPC 调用。建议先抽一层私有客户端：

```java
public interface PrivateSsoClient {
    PrivateSsoUser verifyPassword(String username, String password);
    Optional<PrivateSsoUser> verifySession(HttpServletRequest request);
}
```

其中 `PrivateSsoUser` 至少应包含：

- `uid`
- `username`
- `displayName`

最佳实践：

- 所有超时、重试、日志脱敏、错误码翻译都放在客户端层
- provider 层只负责把外部结果映射成平台所需的身份对象
- 禁止记录明文密码

#### 步骤 3：实现用户映射服务

私有 SSO 不依赖 email，也不做账号合并，因此建议私有版实现一个专用服务：

```java
public interface PrivateSsoIdentityService {
    PlatformPrincipal resolveOrCreate(PrivateSsoUser ssoUser);
}
```

推荐逻辑：

1. 按 `providerCode=private-sso` 和 `subject=ssoUid` 查现有绑定
2. 若已存在，加载对应平台用户
3. 若不存在，则自动创建平台用户
4. 创建新的身份绑定
5. 返回 `PlatformPrincipal`

要求：

- 自动创建出的用户默认应是 `ACTIVE`
- 不要尝试和现有本地账号或 OAuth 账号按 email 合并

#### 步骤 4：实现 `DirectAuthProvider`

伪代码如下：

```java
@Component
public class PrivateSsoDirectAuthProvider implements DirectAuthProvider {

    @Override
    public String providerCode() {
        return "private-sso";
    }

    @Override
    public PlatformPrincipal authenticate(DirectAuthRequest request) {
        PrivateSsoUser ssoUser = privateSsoClient.verifyPassword(
            request.username(),
            request.password()
        );
        return privateSsoIdentityService.resolveOrCreate(ssoUser);
    }
}
```

要求：

- 只返回认证成功后的 `PlatformPrincipal`
- 不在这里建立 Session
- 不在这里写 `SecurityContext`

#### 步骤 5：实现 `PassiveSessionAuthenticator`

伪代码如下：

```java
@Component
public class PrivateSsoPassiveSessionAuthenticator implements PassiveSessionAuthenticator {

    @Override
    public String providerCode() {
        return "private-sso";
    }

    @Override
    public Optional<PlatformPrincipal> authenticate(HttpServletRequest request) {
        return privateSsoClient.verifySession(request)
            .map(privateSsoIdentityService::resolveOrCreate);
    }
}
```

要求：

- 只消费当前请求已带上的 Cookie 或其他被动凭证
- 不主动重定向到 SSO
- 不在这里自行创建 Session

#### 步骤 6：开启配置

私有版部署时启用：

```yaml
skillhub:
  auth:
    direct:
      enabled: true
    session-bootstrap:
      enabled: true
```

建议：

- 预发环境先只开 direct auth
- passive bootstrap 在确认 Cookie 域和 SameSite 行为可靠后再开启

## 6. 前端最佳实践

### 6.1 推荐的登录页策略

私有版推荐保留当前开源登录页结构，但增加企业 SSO 入口：

- 保留 OAuth 按钮
- 本地账号登录是否保留，由私有版自行决定
- 增加企业 SSO 用户名密码表单，或将现有密码表单切换到 direct auth 兼容接口
- 增加“从企业 SSO 登录”按钮，对应 `session/bootstrap`

推荐优先级：

1. 首先提供明确可见的企业用户名密码登录
2. 其次提供“从企业 SSO 登录”按钮
3. 最后才考虑自动 bootstrap

### 6.2 自动 bootstrap 的使用建议

只有在以下条件同时满足时才建议开启 `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO=true`：

- 已确认浏览器在 `skill.xxx.com` 下能稳定带上 SSO Cookie
- 失败时 UI 不会卡死或重复重试
- 页面只会自动尝试一次
- 前端不会因为自动尝试失败而阻断正常密码登录

如果以上条件不满足，建议只显示一个显式按钮，让用户主动触发。

### 6.3 前端禁止事项

- 不要把密码提交给非 SkillHub 后端地址
- 不要在浏览器里解析或操作私有 SSO 内部 Cookie 细节
- 不要把 bootstrap 失败当成页面级致命错误

## 7. Spring Session Redis 相关约束

当前平台的统一 Web 登录态是 Spring Session。

后续私有版继续接入时，必须遵守以下规则：

- 所有成功登录都必须通过 `PlatformSessionService`
- 所有 Web 会话都通过 `HttpSession` 持久化
- 不要手动维护第二份“私有 SSO session”
- 不要在 Redis 中自行定义另一套认证缓存结构来替代 Session

当前统一服务会做的事：

- 写入 `platformPrincipal`
- 写入 `SPRING_SECURITY_CONTEXT`
- 在交互式登录流程中轮换 session id

## 8. 安全最佳实践

### 8.1 用户名密码直连场景

- SkillHub 后端与私有 SSO 之间必须走内网或可信 RPC
- 明文密码只允许存在于浏览器提交和后端调用 SSO 的瞬时链路中
- 日志、埋点、异常信息中禁止出现密码
- 对下游 SSO 调用应设置超时和熔断策略

### 8.2 Cookie 被动会话场景

- 必须先确认 Cookie 域、路径、SameSite、Secure 策略能满足 `skill.xxx.com` 使用
- bootstrap 接口应保留 CSRF 防护
- 失败时只返回认证失败，不泄露过多 Cookie 校验细节
- 除非有明确产品要求，否则不要做无感知的全站自动登录 filter

### 8.3 身份映射场景

- 只信任稳定 UID，不信任显示名作为主身份依据
- 不按 email 合并
- 不按 username 合并

## 9. 建议测试矩阵

### 9.1 后端单元测试

- `DirectAuthProvider` 成功认证
- `DirectAuthProvider` 认证失败
- `PassiveSessionAuthenticator` 在有效 Cookie 下成功返回主体
- `PassiveSessionAuthenticator` 在无效 Cookie 下返回空或失败
- `PrivateSsoIdentityService` 首次登录自动建号
- `PrivateSsoIdentityService` 再次登录复用已有绑定

### 9.2 后端集成测试

- `POST /api/v1/auth/direct/login` 在开启配置后能建立 Session
- `POST /api/v1/auth/session/bootstrap` 在开启配置后能建立 Session
- 成功登录后 `/api/v1/auth/me` 返回正确用户
- direct auth 与现有 `/api/v1/auth/local/login` 不互相影响
- bootstrap 关闭时仍返回 `403`
- direct auth 关闭时仍返回 `403`

### 9.3 前端测试

- 未开启运行时开关时，登录页与开源版默认行为一致
- 开启 direct auth 后，密码表单请求走 `/api/v1/auth/direct/login`
- 开启 bootstrap 按钮后，点击能触发 bootstrap 请求
- 自动 bootstrap 失败后，用户仍可正常使用其它登录入口

### 9.4 手工验收

- 已登录 SSO 的浏览器中，bootstrap 能成功建立 SkillHub 登录态
- 未登录 SSO 的浏览器中，bootstrap 失败但不影响密码登录
- direct auth 登录成功后，刷新页面仍保持登录态
- 多 Pod 环境下，借助 Spring Session Redis，切换实例后 session 仍有效

## 10. 推荐开发顺序

如果后续在私有仓库中真正开始接入，建议按下面顺序推进：

1. 实现 `PrivateSsoClient`
2. 实现 `PrivateSsoIdentityService`
3. 实现 `PrivateSsoDirectAuthProvider`
4. 先启用 `skillhub.auth.direct.enabled=true`
5. 前端接通 direct auth 入口并完成测试
6. 再实现 `PrivateSsoPassiveSessionAuthenticator`
7. 确认 Cookie 作用域和浏览器行为
8. 启用 `session-bootstrap`
9. 视需要决定是否开启自动 bootstrap

## 11. 给 coding agent 的执行指令

如果后续由 AI 继续在私有仓库上完成接入，建议严格遵守以下执行规则：

- 先读 [11-auth-extensibility-and-private-sso.md](/Users/xudongsun/github/skillhub/docs/11-auth-extensibility-and-private-sso.md) 和本文档
- 不要重构现有公共认证主链路，除非发现明确 bug
- 私有 SSO 的具体实现优先写成 provider、authenticator、client、identity service
- 不要复制 `PlatformSessionService` 逻辑
- 不要在多个 controller 或 filter 中重复写 Session 建立代码
- 任何新增前端行为都必须保证运行时配置关闭时完全不影响开源版
- 所有新增协议和运行时配置必须同步更新文档
- 每完成一个阶段都跑后端测试；涉及前端改动时再补跑 `pnpm typecheck` 和 `pnpm build`

## 12. 完成定义

当私有版 SSO 接入完成时，应满足以下标准：

- 开源版默认登录方式仍然不变
- 私有版只通过扩展点接入，没有复制一套独立登录架构
- direct auth 可用
- session bootstrap 可用
- 首次 SSO 登录自动建号
- 统一使用 Spring Session Redis 承载 Web 登录态
- `/api/v1/auth/me`、RBAC、现有业务接口对登录来源无感知
- 文档、配置、测试都完整
