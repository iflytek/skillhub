# skillhub 认证与授权设计

## 0. 身份标识约束

- `PlatformPrincipal.userId` 必须是稳定的字符串标识，而不是 `Long`。
- 用户身份在系统内的主契约是字符串 `userId`；认证、授权、审计、资源 owner 判定都基于该字符串进行。
- 外部身份源的 `subject`、企业 SSO UID、工号型字符串等都必须可以原样或经确定性映射后进入系统，禁止先压缩成自增整数再作为正式用户主键在全链路传播。
- 历史草案里的整型用户主键描述全部废弃，当前认证与授权设计只承认字符串身份主键。

## 1. 认证架构

```
请求进入
  │
  ▼
┌─────────────────────────────┐
│  Layer 1: OAuth2 Login      │  Spring Security OAuth2 Client
│  (一期 GitHub，可扩展)        │  授权码模式 (Authorization Code)
│  Layer 1b: Session Bootstrap│  显式被动会话引导（默认关闭）
└─────────────┬───────────────┘
              │ OAuth2User
              ▼
┌─────────────────────────────┐
│  Layer 2: Access Policy     │  准入策略判定
│  (认证成功 ≠ 有权使用平台)    │  白名单/邮箱域名/开放注册
└─────────────┬───────────────┘
              │ 准入通过
              ▼
┌─────────────────────────────┐
│  Layer 3: Identity Mapping  │  OAuth2 用户 → 平台用户
│  (查询/创建 identity_binding) │  自动注册 + 信息同步
└─────────────┬───────────────┘
              │ PlatformPrincipal
              ▼
┌─────────────────────────────┐
│  Layer 4: Session / Token   │  Web: Spring Session (Redis)
│                             │  CLI: Device Flow + Bearer Token
└─────────────┬───────────────┘
              │ SecurityContext
              ▼
┌─────────────────────────────┐
│  Layer 5: Authorization     │  RBAC + 资源级判定
└─────────────────────────────┘
```

## 2. 准入策略（Access Policy）

OAuth 认证成功仅代表身份可信，不代表有权使用平台。准入层在认证成功后、创建平台用户前执行。

```java
// 基于 claims 的准入策略，与 Provider 无关
public interface AccessPolicy {
    AccessDecision evaluate(OAuthClaims claims);
}

public record OAuthClaims(
    String provider,          // github, google, wechat
    String subject,           // provider 唯一 ID
    String email,             // nullable（微信等可能无邮箱）
    boolean emailVerified,    // 是否已验证
    String providerLogin,     // 如 GitHub login
    Map<String, Object> extra
) {}

public enum AccessDecision {
    ALLOW,              // 准入，继续创建/绑定平台用户
    DENY,               // 拒绝，不建立 Session，重定向到拒绝页
    PENDING_APPROVAL    // 等待管理员审批，不建立业务 Session
}
```

### 2.1 一期支持的策略（通过配置切换）

```yaml
astron:
  access-policy:
    mode: EMAIL_DOMAIN   # OPEN / PROVIDER_ALLOWLIST / EMAIL_DOMAIN / SUBJECT_WHITELIST
    allowed-providers:
      - github
    allowed-email-domains:
      - company.com
      - subsidiary.com
```

| 策略 | 判定依据 | 说明 |
|------|---------|------|
| `OPEN` | 无限制 | 所有 OAuth 登录用户自动准入 |
| `PROVIDER_ALLOWLIST` | `claims.provider` | 仅允许指定 Provider 登录 |
| `EMAIL_DOMAIN` | `claims.email` + `claims.emailVerified` | 仅允许已验证邮箱且域名匹配（email 为空或未验证则 DENY） |
| `SUBJECT_WHITELIST` | `claims.provider` + `claims.subject` | 按 `provider:subject` 白名单，管理员预添加 |

### 2.2 准入失败处理

- `DENY`：抛出 `OAuth2AccessDeniedException`，由 `failureHandler` 重定向到 `/access-denied` 页面。不创建用户，不建立 Session。
- `PENDING_APPROVAL`：创建 `user_account`（status=`PENDING`），但不建立业务 Session。抛出 `AccountPendingException`，由 `failureHandler` 重定向到 `/pending-approval` 页面（纯静态提示页，无需登录态）。管理员在后台审批后状态变为 `ACTIVE`，用户下次 OAuth 登录才会正常建立 Session。

安全边界：PENDING / DISABLED 用户绝不会拥有有效的业务 Session，从根源上杜绝"待审批账号已认证"的风险。

### 2.3 扩展性

后续新增 OAuth Provider（Google、GitLab、微信）时，准入策略与 Provider 无关，统一在 AccessPolicy 层判定，不需要重做入驻逻辑。

## 3. Web 认证流程（OAuth2 Authorization Code）

```
浏览器点击"登录"
    │
    ▼
前端跳转: /oauth2/authorization/github
    │
    ▼
Spring Security 重定向到 GitHub 授权页
    │
    ▼
用户在 GitHub 授权
    │
    ▼
GitHub 回调: /login/oauth2/code/github?code=xxx&state=xxx
    │
    ▼
Spring Security 自动完成:
  ① 用 code 换取 access_token
  ② 调用 GitHub API 获取用户信息
  ③ 触发自定义 OAuth2UserService
    │
    ▼
CustomOAuth2UserService:
  ① 从 OAuth2User 提取 provider + externalId → 构建 OAuthClaims
  ② AccessPolicy.evaluate(claims) → 准入判定
  │
  ├── DENY → 抛出 OAuth2AccessDeniedException → failureHandler 重定向 /access-denied（不建立 Session）
  ├── PENDING_APPROVAL → 创建 PENDING 用户 → 抛出 AccountPendingException → failureHandler 重定向 /pending-approval（不建立 Session）
  └── ALLOW ↓
  │
  ③ 查询 identity_binding 是否已绑定
  ├── 已绑定 → 加载平台用户，检查用户状态（DISABLED → 抛异常），同步最新头像/昵称
  └── 未绑定 → 创建 user_account(ACTIVE) + identity_binding
    │
    ▼
AuthenticationSuccessHandler:
  ① 创建 Spring Session (Redis)
  ② 重定向到前端页面 (可配置的 redirect_uri)
```

### 3.1 统一 Session 建立约束

所有 Web 登录入口都必须通过统一的 `PlatformSessionService` 建立登录态，包括：

- 本地用户名密码登录
- OAuth 登录成功回调
- `POST /api/v1/auth/direct/login`
- `POST /api/v1/auth/session/bootstrap`
- 本地开发态 `MockAuthFilter`

统一约束如下：

- 统一写入 `platformPrincipal`
- 统一写入 `SPRING_SECURITY_CONTEXT`
- 统一通过 `HttpSession` 持久化，确保 Spring Session Redis 能无差别接管
- 交互式登录默认调用 `changeSessionId()`，降低 session fixation 风险
- 已由 Spring Security 完成认证的入口可以复用现有 `Authentication`，避免重复构造认证结果

这意味着未来私有版新增企业 SSO provider 时，只能扩展认证来源本身，不能绕开统一的 session 建立服务直接操作 Session。

## 3.3 Session Bootstrap 扩展点

为了兼容未来私有部署中的企业 SSO 被动登录，开源版预留显式会话引导协议：

- 接口：`POST /api/v1/auth/session/bootstrap`
- 用途：前端在同域场景下显式触发一次“读取外部会话并尝试换取 skillhub Session”的流程
- 默认状态：关闭，开源版不提供任何 `PassiveSessionAuthenticator` 实现
- 安全边界：默认不做全局自动登录 filter，避免匿名访问时隐式建会话、放大 CSRF 和审计复杂度

扩展接口如下：

```java
public interface PassiveSessionAuthenticator {
    String providerCode();
    Optional<PlatformPrincipal> authenticate(HttpServletRequest request);
}
```

约束如下：

- `authenticate()` 只负责验证外部被动会话并返回平台登录所需主体
- 是否允许启用该入口由 `skillhub.auth.session-bootstrap.enabled` 控制，默认 `false`
- 未启用时接口返回 `403`
- 启用但 provider 不受支持时返回 `400`
- 启用但请求中不存在有效外部会话时返回 `401`
- 成功时建立标准 Spring Security Session，并返回与 `/api/v1/auth/me` 一致的用户结构

## 3.4 Direct Authentication 扩展点

为兼容未来“前端收集用户名密码，后端调用企业 SSO / RPC 校验”的私有部署模式，开源版增加默认关闭的直连认证抽象：

```java
public interface DirectAuthProvider {
    String providerCode();
    PlatformPrincipal authenticate(DirectAuthRequest request);
}
```

对应公共协议：

- `POST /api/v1/auth/direct/login`

约束如下：

- 开源版默认关闭，由 `skillhub.auth.direct.enabled` 控制
- 关闭时返回 `403`
- provider 不受支持时返回 `400`
- provider 认证失败时沿用 provider 自身的认证异常语义
- 成功时建立标准 Session，并返回与 `/api/v1/auth/me` 一致的用户结构
- 现有 `/api/v1/auth/local/login` 保持不变，兼容层只是新增可选入口

### 3.5 Spring Security 配置要点

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(info -> info
                    .userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/v1/**"))
            // ...
        ;
    }
}
```

### 3.6 OAuth2 Provider 扩展设计

一期只实现 GitHub，但架构支持后续扩展：

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${OAUTH2_GITHUB_CLIENT_ID}
            client-secret: ${OAUTH2_GITHUB_CLIENT_SECRET}
            scope: read:user,user:email
          # 二期扩展示例:
          # gitlab:
          #   client-id: ...
          #   authorization-grant-type: authorization_code
          # google:
          #   client-id: ...
```

Spring Security OAuth2 Client 原生支持多 Provider 并存，新增 Provider 只需：
1. `application.yml` 添加 registration 配置
2. `CustomOAuth2UserService` 中按 `registrationId` 分支处理用户属性映射
3. 前端登录页增加对应按钮（通过 `/api/v1/auth/providers` 自动发现）

## 4. 核心接口设计

```java
// 自定义 OAuth2 用户服务，处理准入 + 用户映射
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User oAuth2User = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        // 提取标准化 claims（传入 accessToken 用于调用 Provider API，如 GitHub /user/emails）
        OAuthClaims claims = OAuthClaimsExtractor.extract(registrationId, oAuth2User, request.getAccessToken());

        // 准入策略判定（基于 claims，与 Provider 无关）
        AccessDecision decision = accessPolicy.evaluate(claims);
        if (decision == AccessDecision.DENY) {
            throw new OAuth2AccessDeniedException("Access denied by policy");
        }
        if (decision == AccessDecision.PENDING_APPROVAL) {
            // 创建 PENDING 用户但不返回有效 principal，不建立业务 Session
            identityBindingService.createPendingUser(registrationId, claims);
            throw new AccountPendingException("Account pending approval");
        }

        // 绑定或创建平台用户（仅 ALLOW 才走到这里）
        UserAccount account = identityBindingService.bindOrCreate(registrationId, claims);
        if (account.getStatus() == UserStatus.DISABLED) {
            throw new AccountDisabledException("Account is disabled");
        }

        return new PlatformOAuth2User(account, oAuth2User.getAuthorities());
    }
}

// 按 Provider 提取标准化 claims（每个 Provider 有自己的可信字段契约）
public class OAuthClaimsExtractor {
    public static OAuthClaims extract(String registrationId, OAuth2User user,
                                      OAuth2AccessToken accessToken) {
        return switch (registrationId) {
            case "github" -> extractGitHub(user, accessToken);
            // 后续扩展其他 Provider
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        };
    }

    // GitHub: 公开 email 可能为空，需调用 /user/emails API 获取已验证邮箱
    private static OAuthClaims extractGitHub(OAuth2User user, OAuth2AccessToken accessToken) {
        String verifiedEmail = GitHubEmailFetcher.fetchVerifiedEmail(accessToken);
        return new OAuthClaims(
            "github",
            String.valueOf(user.getAttribute("id")),
            verifiedEmail,                    // 从 /user/emails 获取的已验证邮箱，可能为 null
            verifiedEmail != null,            // 只有确认 verified 才为 true
            user.getAttribute("login"),
            Map.of("avatar_url", user.getAttribute("avatar_url"))
        );
    }

    // GitHubEmailFetcher: 调用 GitHub /user/emails API，
    // 返回 primary + verified 的邮箱，无则返回 null
}
```

### 4.1 多 Provider 账号合并策略

同一个员工通过不同 OAuth Provider 登录时，可能产生多个 `user_account`。

一期策略：默认关闭自动合并，仅支持管理员手动合并。

- 一期 GitHub-only：不需要自动合并，每个 Provider 登录独立创建用户
- 多 Provider 上线时，再引入显式绑定/合并流程（用户主动发起 + 邮箱验证确认）
- 管理员可在后台手动合并两个 user_account（合并 identity_binding、迁移 skill ownership、合并角色取并集）

合并操作规则：
- 合并操作写入审计日志
- 合并后原 user_account 标记为 `MERGED`，保留记录不物理删除
- 预留扩展位：未来可配置 `astron.identity.auto-merge-on-verified-email=true` 开启基于已验证邮箱的自动合并

## 5. CLI 认证（OAuth Device Flow + 平台凭证）

CLI 主认证基线调整为 OAuth Device Flow。用户在 CLI 中发起授权，浏览器侧完成登录与确认，CLI 轮询后获取平台签发的凭证并访问 CLI API。

- 发起：CLI 请求 device code，展示 `user_code` 与验证地址
- 授权：用户在浏览器完成 GitHub OAuth 登录并确认绑定
- 轮询：CLI 使用 `device_code` 轮询授权结果
- 完成：服务端签发 CLI 可用凭证，CLI 持 `Authorization: Bearer <token>` 调用后续接口

API Token 仍保留，但定位从“CLI 唯一认证方式”调整为“平台通用凭证能力”：

- 用途：自动化脚本、兼容层调用、手工 Token 管理、后续系统集成
- 存储：只存 SHA-256 哈希，明文只展示一次
- 校验：从 `Authorization: Bearer <token>` 提取 → 哈希比对 → 加载关联用户 → 检查用户状态
- 作用域：`skill:read`, `skill:publish`, `skill:delete`, `token:manage`

> **一期作用域说明（非最小权限）**：一期 Token 作用域为粗粒度动作级别，不与 namespace 绑定。Token 继承用户的全部权限——如果用户是某个 namespace 的 MEMBER，则该用户的任何 Token（只要包含 `skill:publish` scope）都可以向该 namespace 发布技能。这是有意的一期简化，不满足最小权限原则。后续版本计划引入 namespace 级别的 Token 作用域限定（如 `namespace:ai-team:skill:publish`），或通过 `api_token_scope` 子表实现 Token 与 namespace 的绑定。

## 6. RBAC 授权判定

```
权限判定 = 平台角色权限（role → permission 查询） ∪ 命名空间角色（namespace_member.role）
```

一期即上线完整 RBAC，平台角色按最小权限拆分：

| 平台角色 | 职责 |
|---------|------|
| `SUPER_ADMIN` | 全部权限，硬判定短路 |
| `SKILL_ADMIN` | 全局空间审核、提升审核、隐藏/撤回技能 |
| `USER_ADMIN` | 准入审批、封禁/解封、角色分配（不可分配 SUPER_ADMIN） |
| `AUDITOR` | 审计日志只读 |

- 命名空间权限仍由 `namespace_member.role`（OWNER / ADMIN / MEMBER）决定
- 一个用户可持有多个平台角色
- 普通用户无平台角色，仅通过 namespace 成员关系获得操作权限

判定逻辑：
1. 从 SecurityContext 获取当前用户
2. 检查用户状态（`DISABLED` → 拒绝所有操作）
3. 查询用户的平台角色（`user_role_binding` → `role` → `role_permission`）
4. `SUPER_ADMIN` 短路：直接通过所有权限检查
5. 如果涉及命名空间资源，查询用户在该命名空间的角色（`namespace_member.role`）
6. 检查命名空间状态（`FROZEN` → 拒绝写操作）
7. 合并平台权限 + 命名空间角色，判定是否满足

| 操作 | 所需权限 | 判定逻辑 |
|------|---------|---------|
| 提交发布审核 | `skill:publish` | 用户是该 namespace 的 MEMBER 以上，且 namespace 非 FROZEN |
| 管理技能（归档/版本管理） | `skill:manage` | namespace ADMIN 以上，或 owner 本人 |
| 提升到全局 | `skill:promote` | namespace ADMIN 以上，或 owner 本人 |
| 审核团队空间技能 | `review:approve` | 该 namespace 的 ADMIN 或 OWNER |
| 审核全局空间技能 | `review:approve` | 持有 SKILL_ADMIN / SUPER_ADMIN |
| 审核提升申请 | `promotion:approve` | 持有 SKILL_ADMIN / SUPER_ADMIN |
| 隐藏/撤回技能 | `skill:manage` | 持有 SKILL_ADMIN / SUPER_ADMIN |
| 管理用户角色 | `user:manage` | 持有 USER_ADMIN / SUPER_ADMIN |
| 审批用户准入 | `user:approve` | 持有 USER_ADMIN / SUPER_ADMIN |
| 查看审计日志 | `audit:read` | 持有 AUDITOR / SUPER_ADMIN |

权限主轴说明：
- namespace role 是权限主轴，namespace ADMIN 对空间内所有 skill 有完整管理权，不受 owner 限制
- `owner_id` 语义为"主要维护人"，owner 作为 MEMBER 时仅可管理自己创建的 skill
- 企业场景人员流动频繁，owner 离职后 namespace ADMIN 仍能完整管理所有技能

### 6.1 审核与提升 API 路径适用范围

| API 路径 | 适用范围 | 权限要求 |
|----------|---------|---------|
| `POST /api/v1/admin/reviews/{id}/approve` | 全局空间审核 | SKILL_ADMIN / SUPER_ADMIN |
| `POST /api/v1/admin/promotions/{id}/approve` | 提升到全局审核 | SKILL_ADMIN / SUPER_ADMIN |
| `POST /api/v1/namespaces/{slug}/reviews/{id}/approve` | 团队空间内发布审核 | 该空间 ADMIN / OWNER |
| `GET /api/v1/admin/audit-logs` | 审计日志查询 | AUDITOR / SUPER_ADMIN |
| `PUT /api/v1/admin/users/{id}/roles` | 用户角色管理 | USER_ADMIN / SUPER_ADMIN |
| `POST /api/v1/admin/users/{id}/approve` | 用户准入审批 | USER_ADMIN / SUPER_ADMIN |

SUPER_ADMIN 和持有对应角色的用户均可通过 Admin API 操作；团队空间审核限定通过 Namespace API 完成，平台管理员不越权进入团队空间审核流程。

## 7. Session 设计

- 存储：Spring Session + Redis（必须，多 Pod 环境刚需）
- 序列化：JSON
- 过期：默认 8 小时，Redis TTL 自动清理

### 7.1 Session 内容

Session 中存储以下字段：
- `userId`：平台用户 ID
- `displayName`：展示名
- `oauthProvider`：登录使用的 OAuth Provider
- `currentNamespaceId`：当前选中的命名空间（可选）
- `platformRoles`：平台角色列表（如 `["SKILL_ADMIN", "AUDITOR"]`），登录时从 `user_role_binding` → `role` 查询写入
- `roleVersion`：角色版本号，用于缓存一致性

### 7.2 角色缓存一致性机制

平台角色变更需要即时生效（如撤销审核权限），不能等 Session 过期：

1. 每次请求时从 Session 读取 `roleVersion`
2. 与 Redis 中的 `user:{userId}:roleVersion` 比对
3. 版本一致 → 直接使用 Session 中的 `platformRoles`
4. 版本不一致 → 从数据库重新加载角色，更新 Session

管理员修改用户角色时，递增 Redis 中该用户的 `roleVersion`。

## 8. CSRF 防护

采用 Cookie-to-Header 模式：
- 后端设置 `XSRF-TOKEN` Cookie（`HttpOnly=false`）
- 前端从 Cookie 读取 Token，放入请求 Header `X-XSRF-TOKEN`
- 后端校验 Header 与 Cookie 是否一致
- CLI API（`/api/v1/**`）与兼容层（`/api/compat/v1/**`）豁免 CSRF（使用 Bearer Token，无 Cookie）

## 9. 前端权限控制

### 9.1 `/api/v1/auth/me` 响应结构

```json
{
  "code": 0,
  "msg": "获取成功",
  "data": {
    "userId": 42,
    "displayName": "zhangsan",
    "email": "zhangsan@company.com",
    "avatarUrl": "https://...",
    "oauthProvider": "github",
    "platformRoles": ["SKILL_ADMIN", "AUDITOR"],
    "namespaces": [
      { "slug": "ai-team", "role": "ADMIN" },
      { "slug": "global", "role": "MEMBER" }
    ]
  },
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

前端权限判定基于 `platformRoles` + `namespaces[].role`，后端通过 `role_permission` 表查询权限码。

统一约束：
- `/api/v1/auth/me`、`/api/v1/auth/providers` 等 JSON 响应必须统一使用 `code/msg/data/timestamp/requestId` 外层结构。
- `/api/v1/auth/session/bootstrap` 也必须遵守同一统一响应结构。
- `msg` 必须走 Spring Boot 标准 `MessageSource` i18n 机制。
- locale 必须通过请求上下文自动获取，不在 controller 中显式传递。
- 认证失败返回 `401`，但 JSON 外层结构仍保持一致，例如 `{"code":401,"msg":"需要先登录","data":null,...}`。

### 9.2 usePermission() Hook

```typescript
function usePermission() {
  const { data: me } = useQuery({ queryKey: ['auth', 'me'], queryFn: fetchMe })

  const hasRole = (role: string) => me?.platformRoles.includes(role) ?? false
  const isSuperAdmin = () => hasRole('SUPER_ADMIN')
  const isSkillAdmin = () => hasRole('SKILL_ADMIN') || isSuperAdmin()
  const isUserAdmin = () => hasRole('USER_ADMIN') || isSuperAdmin()
  const isAuditor = () => hasRole('AUDITOR') || isSuperAdmin()

  return {
    isLoggedIn: !!me,
    isSuperAdmin,
    isSkillAdmin,
    isUserAdmin,
    isAuditor,

    // 命名空间角色判定
    getNamespaceRole: (slug: string) =>
      me?.namespaces.find(n => n.slug === slug)?.role,
    isNamespaceAdmin: (slug: string) =>
      ['OWNER', 'ADMIN'].includes(me?.namespaces.find(n => n.slug === slug)?.role ?? ''),
    isNamespaceMember: (slug: string) =>
      ['OWNER', 'ADMIN', 'MEMBER'].includes(me?.namespaces.find(n => n.slug === slug)?.role ?? ''),
  }
}
```

### 9.3 路由级守卫

在 TanStack Router `beforeLoad` 中判定：

| 路由 | 条件 |
|------|------|
| `/dashboard/*` | 已登录 |
| `/dashboard/namespaces/{slug}/reviews` | 已登录 + 该 namespace 的 ADMIN 以上 |
| `/admin/*` | 已登录 + 持有任一平台角色（SUPER_ADMIN / SKILL_ADMIN / USER_ADMIN / AUDITOR） |

不满足条件时：未登录 → 重定向登录；已登录但无权限 → 显示 403 页面。

### 9.4 操作级控制

| 场景 | 判定逻辑 | UI 行为 |
|------|---------|---------|
| 技能详情页"提交发布"按钮 | `isNamespaceMember(namespace)` | 非成员不显示 |
| 审核列表"通过/拒绝"按钮 | 团队空间：`isNamespaceAdmin(namespace)`；全局空间：`isSkillAdmin()` | 无权限不显示 |
| 用户管理页 | `isUserAdmin()` | 无权限不显示 |
| 用户管理页"设为 SUPER_ADMIN" | `isSuperAdmin()` | 仅超管可见 |
| 审计日志页 | `isAuditor()` | 无权限不显示 |
| 技能详情页"归档"按钮 | `isNamespaceAdmin(namespace)` 或当前用户是 owner | 否则不显示 |
| 命名空间"添加成员"按钮 | `isNamespaceAdmin(namespace)` | 非管理员不显示 |
| 收藏/评分按钮 | `isLoggedIn` | 未登录时点击提示登录 |

### 9.5 登录交互

```
前端登录按钮
    │
    ▼
window.location.href = '/oauth2/authorization/github'
    │
    ▼
(后端 OAuth2 流程，用户无感)
    │
    ▼
回调后重定向到前端 (如 /?login=success)
    │
    ▼
前端检测 URL 参数 → 调用 /api/v1/auth/me → 更新登录态
```

前端无需引入额外 OAuth 库，登录流程完全由后端 Spring Security 处理。前端只需：
- 调用 `/api/v1/auth/providers` 获取可用 Provider 列表，动态渲染登录按钮
- 处理登录后的重定向
- 通过 `/api/v1/auth/me` 检测登录状态

### 9.6 安全边界原则

- 前端权限控制是 UX 优化，不是安全边界
- 后端每个写操作接口独立校验权限，不信任前端判定
- 前端隐藏按钮 ≠ 安全，用户可以直接调 API，后端必须拦截

## 10. 权限矩阵（完整）

以下矩阵列出每个 API 接口的权限判定来源，作为后端实现的唯一参考。

### 10.1 Public API（匿名可访问）

| 接口 | 匿名 | 已登录 | 判定逻辑 |
|------|------|--------|---------|
| `GET /api/v1/skills`（搜索） | PUBLIC 技能 | PUBLIC + NAMESPACE_ONLY（成员空间）+ PRIVATE（owner/admin） | `SearchVisibilityScope` 投影 |
| `GET /api/v1/skills/{ns}/{slug}` | PUBLIC 技能 | 同上 | visibility + namespace 成员关系 |
| `GET /api/v1/skills/{ns}/{slug}/versions` | PUBLIC 技能 | 同上 | 同上 |
| `GET /api/v1/skills/{ns}/{slug}/download` | PUBLIC 技能 | 同上 | 同上 |
| `GET /api/v1/skills/{ns}/{slug}/resolve` | PUBLIC 技能 | 同上 | 同上 |
| `GET /api/v1/namespaces` | 全部 | 全部 | 无限制 |

### 10.2 Authenticated API

| 接口 | 所需权限 | 判定来源 |
|------|---------|---------|
| `POST /api/v1/skills/{ns}/{slug}/star` | 已登录 | Session/Token |
| `POST /api/v1/skills/{ns}/{slug}/rating` | 已登录 | Session/Token |
| `POST .../versions/{ver}/submit-review` | namespace MEMBER 以上 | `namespace_member.role` |
| `POST .../versions/{ver}/withdraw-review` | 提交人本人 或 namespace ADMIN | `review_task.submitted_by` 或 `namespace_member.role` |
| `PUT /api/v1/skills/{ns}/{slug}/tags/{tag}` | namespace ADMIN 以上 或 owner | `namespace_member.role` 或 `skill.owner_id` |
| `POST /api/v1/skills/{ns}/{slug}/archive` | namespace ADMIN 以上 或 owner | `namespace_member.role` 或 `skill.owner_id` |
| `DELETE .../versions/{ver}` | namespace ADMIN 以上 或 owner（仅 DRAFT/REJECTED） | `namespace_member.role` 或 `skill.owner_id` + `skill_version.status` |

### 10.3 CLI API

| 接口 | 所需凭证 | 额外判定 |
|------|---------|---------|
| `GET /api/v1/whoami` | 任意有效 Bearer Token | 无 |
| `POST /api/v1/publish` | Bearer Token + `skill:publish` | 用户是目标 namespace 的 MEMBER 以上 |

### 10.4 Admin API

| 接口 | 所需平台角色 | 判定来源 |
|------|------------|---------|
| `POST /api/v1/admin/reviews/{id}/approve` | SKILL_ADMIN / SUPER_ADMIN | `user_role_binding` → `role_permission` |
| `POST /api/v1/admin/reviews/{id}/reject` | SKILL_ADMIN / SUPER_ADMIN | 同上 |
| `POST /api/v1/admin/promotions/{id}/approve` | SKILL_ADMIN / SUPER_ADMIN | 同上 |
| `POST /api/v1/admin/promotions/{id}/reject` | SKILL_ADMIN / SUPER_ADMIN | 同上 |
| `PUT /api/v1/admin/users/{id}/roles` | USER_ADMIN / SUPER_ADMIN | 同上，且 USER_ADMIN 不可分配 SUPER_ADMIN |
| `POST /api/v1/admin/users/{id}/approve` | USER_ADMIN / SUPER_ADMIN | 同上 |
| `POST /api/v1/admin/users/{id}/ban` | USER_ADMIN / SUPER_ADMIN | 同上 |
| `GET /api/v1/admin/audit-logs` | AUDITOR / SUPER_ADMIN | 同上 |

### 10.5 Namespace API

| 接口 | 所需 namespace 角色 | 判定来源 |
|------|-------------------|---------|
| `POST /api/v1/namespaces/{slug}/reviews/{id}/approve` | 该空间 ADMIN / OWNER | `namespace_member.role` |
| `POST /api/v1/namespaces/{slug}/reviews/{id}/reject` | 该空间 ADMIN / OWNER | `namespace_member.role` |
| `POST /api/v1/namespaces/{slug}/members` | 该空间 ADMIN 以上 | `namespace_member.role` |
| `DELETE /api/v1/namespaces/{slug}/members/{userId}` | 该空间 ADMIN 以上 | `namespace_member.role` |
| `POST .../skills/{skillId}/promote` | 该空间 ADMIN 以上 或 owner | `namespace_member.role` 或 `skill.owner_id` |

### 10.6 Compatibility API（Bearer Token 认证）

| 接口 | 所需凭证 | 额外判定 |
|------|---------|---------|
| `GET /api/compat/v1/whoami` | 任意有效 Bearer Token | 无 |
| `GET /api/compat/v1/search` | 可选（匿名限 PUBLIC） | `SearchVisibilityScope` |
| `GET /api/compat/v1/resolve` | 可选（匿名限 PUBLIC） | visibility |
| `GET /api/compat/v1/download/{slug}/{version}` | 可选（匿名限 PUBLIC） | visibility |
| `POST /api/compat/v1/publish` | Bearer Token + `skill:publish` | 用户是目标 namespace 的 MEMBER 以上（namespace 由 canonical slug 解析） |
