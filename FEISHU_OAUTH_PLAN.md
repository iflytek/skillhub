# 飞书 OAuth2.0 登录接入计划

## 目标
在 skillhub 项目中接入飞书 OAuth2.0 登录功能，使用 git worktree 隔离开发。

## 工作目录
- **Worktree**: `/tmp/skillhub-feishu-oauth2`
- **Branch**: `feature/feishu-oauth2-v2`
- **基于**: `feat/skillhub-cli`

## Architecture Decision (from /plan-eng-review)

**Approach:** Standard Spring OAuth2 Client (NOT Custom Controller)

Rationale:
- Less code — no custom controller needed
- Consistent with GitHub OAuth implementation
- Leverages Spring Security's built-in security defaults
- FeishuClaimsExtractor already exists in `/tmp/skillhub-feishu-oauth/` and can be copied

## Engineering Review Findings

### Completed: Step 0 Scope Challenge
- Task 1 (FeishuClaimsExtractor) → Copy from existing worktree
- Task 2 (FeishuOAuthController) → NOT NEEDED with Spring OAuth2 Client
- Task 4 (SecurityConfig) → Verify, likely no changes needed

### Action Items from Review
1. **P2:** Verify Feishu OAuth scope — `user:email,user:id` may need `user:basic` for full profile
2. **P2:** Ensure `/auth/feishu/callback` route is NOT carried over from old worktree
3. **P1:** Test with real Feishu app to verify user-info response always includes `open_id`

## 现有 OAuth 架构分析

### 核心组件 (server/skillhub-auth)
```
OAuthLoginFlowService
    ├── GitHubClaimsExtractor → OAuthClaims
    ├── CustomOAuth2UserService → OAuth2User (with platformPrincipal)
    ├── IdentityBindingService → UserAccount + IdentityBinding
    └── PlatformPrincipal → Session
```

### 关键文件
| 文件 | 作用 |
|------|------|
| `OAuthClaimsExtractor.java` | Provider-agnostic 接口 |
| `OAuthClaims.java` | 标准化 Claims 数据结构 |
| `GitHubClaimsExtractor.java` | GitHub Provider 实现 (参考) |
| `OAuthLoginFlowService.java` | OAuth 登录流程核心 |
| `IdentityBindingService.java` | 身份绑定服务 |
| `SecurityConfig.java` | 安全配置 |

### OAuth 流程
1. 用户点击登录 → `/oauth2/authorization/{provider}`
2. `SkillHubOAuth2AuthorizationRequestResolver` 记住 `returnTo`
3. Spring Security 处理 OAuth 回调
4. `CustomOAuth2UserService` → `OAuthLoginFlowService.loadLoginContext()`
5. Provider-specific `OAuthClaimsExtractor` 提取用户信息
6. `AccessPolicy` 评估访问权限
7. `IdentityBindingService.bindOrCreate()` 创建/绑定用户
8. `PlatformPrincipal` 存入 Session

## 实现任务

### Task 1: 复制 FeishuClaimsExtractor (从现有 worktree)
**源文件**: `/tmp/skillhub-feishu-oauth/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/FeishuClaimsExtractor.java`
**目标文件**: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/FeishuClaimsExtractor.java`

直接复制现有实现，该文件已完整实现 `OAuthClaimsExtractor` 接口：
- Provider name: `"feishu"`
- 提取: open_id, union_id, user_id, email, name, avatar_url
- 优先使用 union_id 作为 subject
- Fallback 调用飞书 `/open-apis/authen/v1/user_info`

### Task 2: FeishuOAuthController — NOT NEEDED
**状态**: 使用 Spring OAuth2 Client，不需要的自定义 Controller

标准 Spring OAuth2 回调路径: `/login/oauth2/code/feishu`
旧 worktree 中的 `/auth/feishu/callback` 不会被使用

### Task 3: 配置 Feishu OAuth
**文件**: `server/skillhub-app/src/main/resources/application.yml`

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          feishu:
            client-id: ${FEISHU_CLIENT_ID}
            client-secret: ${FEISHU_CLIENT_SECRET}
            scope: user:email,user:id
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          feishu:
            authorization-uri: https://accounts.feishu.cn/open-apis/authen/v1/authorize
            token-uri: https://open.feishu.cn/open-apis/authen/v2/oauth/token
            user-info-uri: https://open.feishu.cn/open-apis/authen/v1/user_info
            user-name-attribute: union_id
```

### Task 4: 验证 SecurityConfig
**文件**: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/SecurityConfig.java`

检查是否需要添加变更：
- 确认 OAuth2Login 配置支持 feishu provider
- 如果 `/auth/feishu/callback` 在任何地方被引用，移除该路由（使用标准 `/login/oauth2/code/feishu`）
- 当前 SecurityConfig 已配置 `oauth2Login`，应该自动支持新的 feishu registration

### Task 5: 添加测试
**文件**: `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/oauth/FeishuClaimsExtractorTest.java`

TDD - RED phase：先写测试

**Required test cases:**
| Scenario | Test Method |
|----------|-------------|
| All attributes present | `extract_withAllAttributes_returnsCorrectClaims` |
| Missing open_id - fallback to userInfo | `extract_missingOpenId_callsUserInfoEndpoint` |
| Only union_id - uses as subject | `extract_onlyUnionId_usesUnionIdAsSubject` |
| No open/union - uses user_id as fallback | `extract_noOpenIdNoUnionId_usesUserId` |
| Null email | `extract_nullEmail_returnsNullEmail` |

## 飞书 OAuth2.0 端点

| 端点 | URL |
|------|-----|
| Authorization | `https://accounts.feishu.cn/open-apis/authen/v1/authorize` |
| Token | `https://open.feishu.cn/open-apis/authen/v2/oauth/token` |
| User Info | `https://open.feishu.cn/open-apis/authen/v1/user_info` |

## 环境变量
```
FEISHU_CLIENT_ID=cli_xxx
FEISHU_CLIENT_SECRET=xxx
SKILLHUB_PUBLIC_BASE_URL=http://localhost:8080
```

## 依赖
- Spring Security OAuth2 Client
- RestClient (已有)

## NOT in Scope
- 前端 UI 更改 (login button)
- 现有的 GitHub OAuth 实现修改
- 数据库迁移 (现有表结构已支持)

## 风险
1. 飞书不同版本 (feishu/lark) 端点可能不同
2. 现有 /tmp/skillhub-feishu-oauth 实现可能使用了不同的 token exchange 方式
3. **OPEN:** Feishu OAuth scope 需要验证 — `user:email,user:id` 可能需要额外 scope

## 验证清单
- [x] FeishuClaimsExtractor.java 复制完成
- [x] application.yml 配置添加
- [x] FeishuClaimsExtractorTest.java 通过 (6 个测试)
- [ ] 本地测试 OAuth flow 成功 (需要真实飞书 App credentials)
- [x] 安全配置无路由冲突 (仅 plan 文档中有引用，无源代码冲突)
