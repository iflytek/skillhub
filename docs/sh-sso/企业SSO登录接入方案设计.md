# SkillHub 企业 SSO 登录接入方案设计

## 文档信息

- **版本**: v1.0
- **创建日期**: 2026-04-03
- **设计目标**: 将 SkillHub 平台改造为支持企业 SSO 统一登录，实现与企业认证系统的无缝集成

---

## 1. 方案概述

### 1.1 背景

SkillHub 当前支持以下认证方式：
- 本地用户名/密码登录（LocalDirectAuthProvider）
- OAuth2 第三方登录（GitHub 等）
- API Token 认证

为适配企业内部使用场景，需要接入企业 SSO 认证系统，实现：
- **统一身份认证**: 员工使用企业账号登录
- **多租户支持**: 支持不同租户（部门/子公司）的用户访问
- **租户切换**: 用户可在多个租户间切换
- **首次登录强制修改密码**: 安全合规要求

### 1.2 设计原则

1. **最小侵入**: 基于现有的 `DirectAuthProvider` 接口扩展，不破坏现有认证体系
2. **安全第一**: 遵循 OAuth 2.0 标准，Token 安全存储和传输
3. **可扩展性**: 支持未来对接其他企业 SSO 系统
4. **用户体验**: 无缝的单点登录体验，最少的跳转次数

### 1.3 技术选型

- **认证协议**: 基于 OAuth 2.0 Authorization Code Flow
- **Token 管理**: Access Token + Refresh Token 双 Token 机制
- **会话管理**: Spring Security Session + Redis 分布式会话
- **前端框架**: React + TanStack Query
- **状态管理**: React Context + Hooks

---

## 2. 架构设计

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                          用户浏览器                              │
│  ┌──────────────┐         ┌──────────────┐                     │
│  │  前端应用     │◄────────►│  SSO 登录页  │                     │
│  │  (React)     │         │              │                     │
│  └──────┬───────┘         └──────────────┘                     │
└─────────┼───────────────────────────────────────────────────────┘
          │
          │ HTTPS
          │
┌─────────▼───────────────────────────────────────────────────────┐
│                    SkillHub 后端服务                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │             Spring Security Filter Chain                 │   │
│  │  ┌────────────┐  ┌──────────────┐  ┌────────────────┐  │   │
│  │  │ SSO Auth   │  │ Session Auth │  │ API Token Auth │  │   │
│  │  │ Filter     │  │ Filter       │  │ Filter         │  │   │
│  │  └─────┬──────┘  └──────────────┘  └────────────────┘  │   │
│  └────────┼─────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼──────────────────────────────────────────────┐     │
│  │       EnterpriseDirectAuthProvider                      │     │
│  │  (实现 DirectAuthProvider 接口)                         │     │
│  │  ┌──────────────────────────────────────────────┐     │     │
│  │  │ - 调用企业 SSO API                            │     │     │
│  │  │ - Token 验证与刷新                            │     │     │
│  │  │ - 用户信息同步                                │     │     │
│  │  │ - 租户信息管理                                │     │     │
│  │  └──────────────────────────────────────────────┘     │     │
│  └────────┬─────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼──────────────────────────────────────────────┐     │
│  │         Identity Binding Service                       │     │
│  │  - 企业账号与平台账号绑定                               │     │
│  │  - 用户信息同步与更新                                   │     │
│  └───────────────────────────────────────────────────────┘     │
│                                                                  │
│  ┌──────────────────────────────────────────────────────┐      │
│  │              Redis 会话存储                           │      │
│  │  - Access Token 缓存                                  │      │
│  │  - Refresh Token 缓存                                 │      │
│  │  - 用户会话信息                                        │      │
│  └──────────────────────────────────────────────────────┘      │
└──────────────────┬───────────────────────────────────────────┘
                   │
                   │ HTTPS
                   │
┌──────────────────▼───────────────────────────────────────────┐
│                    企业 SSO 认证系统                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ - 用户认证 API                                       │    │
│  │ - Token 签发与验证                                   │    │
│  │ - 用户信息查询                                       │    │
│  │ - 租户管理                                           │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 认证流程分层

#### 第一层：前端认证入口

- 登录页面展示企业 SSO 登录入口
- 跳转到企业 SSO 登录页面（携带 redirectUri）
- 接收 SSO 回调并处理

#### 第二层：SkillHub 认证适配

- 实现 `EnterpriseDirectAuthProvider`
- 与企业 SSO API 交互
- 用户信息映射与绑定
- 租户信息管理

#### 第三层：平台身份管理

- 用户账号创建与绑定
- 权限角色分配
- 会话管理

---

## 3. 详细流程设计

### 3.1 首次登录流程

详见流程图：`企业SSO首次登录流程.md`

**关键步骤**：

1. 用户访问 SkillHub 登录页
2. 点击"企业账号登录"按钮
3. 前端调用 `/api/auth/sso/login-url?redirectUri={前端回调地址}`
4. 后端返回企业 SSO 登录地址
5. 前端重定向到企业 SSO 登录页
6. 用户在 SSO 页面输入企业账号密码
7. SSO 验证成功后回调 SkillHub（携带临时授权码）
8. SkillHub 后端通过授权码换取 Access Token 和 Refresh Token
9. 后端验证 Token 并获取用户信息
10. 判断是否首次登录（firstLogin = true）
11. 如果是首次登录，返回前端标记，提示修改密码
12. 用户修改密码后完成首次登录
13. 创建 SkillHub 平台账号并绑定企业账号
14. 返回 JWT 或 Session Cookie
15. 前端跳转到首页

### 3.2 常规登录流程

详见流程图：`企业SSO常规登录流程.md`

**关键步骤**：

1-10. 同首次登录流程
11. 判断 firstLogin = false，直接返回 Token
12. 查询已绑定的平台账号
13. 更新用户信息（姓名、部门、职位等）
14. 返回 JWT 或 Session Cookie
15. 前端跳转到首页

### 3.3 租户切换流程

详见流程图：`企业SSO租户切换流程.md`

**关键步骤**：

1. 用户在平台选择切换租户
2. 前端调用 `/api/auth/sso/switch-tenant`（携带目标租户号和 Refresh Token）
3. 后端调用企业 SSO 刷新 Token API
4. SSO 返回新的 Access Token（包含目标租户信息）
5. 后端更新会话中的租户信息
6. 返回新的 Token 和用户信息
7. 前端更新全局状态并刷新页面

### 3.4 Token 刷新流程

**Access Token 过期处理**：

```
用户请求 API
    ↓
检测到 401 (Token 过期)
    ↓
前端拦截器自动调用 /api/auth/sso/refresh-token
    ↓
后端使用 Refresh Token 换取新的 Access Token
    ↓
返回新 Token 给前端
    ↓
前端重试原请求
```

### 3.5 退出登录流程

**本地退出 vs 全局退出**：

#### 本地退出（推荐）

1. 前端调用 `/api/auth/logout`
2. 后端清除 SkillHub 会话
3. 前端清除本地 Token
4. 跳转到登录页

**优点**: 不影响用户在其他企业系统的登录状态

#### 全局退出（可选）

1. 前端调用 `/api/auth/sso/logout`
2. 后端调用企业 SSO 退出 API
3. 清除 SkillHub 会话和企业 SSO 会话
4. 跳转到登录页

**优点**: 完全注销，安全性更高

---

## 4. 接口设计

### 4.1 后端接口

#### 4.1.1 获取 SSO 登录地址

```http
GET /api/auth/sso/login-url
```

**Query 参数**:

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `redirectUri` | String | 是 | 登录成功后的前端回调地址 |

**响应**:

```json
{
  "success": true,
  "data": {
    "loginUrl": "http://sso.company.com/login?clientId=skillhub&redirectUri=https://skillhub.company.com/auth/callback"
  }
}
```

#### 4.1.2 SSO 回调处理

```http
GET /api/auth/sso/callback
```

**Query 参数**:

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `code` | String | 是 | 企业 SSO 返回的授权码 |
| `state` | String | 是 | 防 CSRF 攻击的状态码 |

**响应**:

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 123,
      "username": "zhangbo",
      "nickName": "张波",
      "phone": "18217371537",
      "tenantNo": "8000",
      "tenantName": "盛虹石化",
      "firstLogin": false,
      "employments": [
        {
          "employeeId": 74,
          "orgCodePath": "/60000000/66000078/66000022/",
          "orgCodePathName": "/石化板块/原油供应链中心/盛虹新加坡/",
          "positionCode": "63002474",
          "positionName": "综合副经理"
        }
      ]
    },
    "accessToken": "AT-xxx",
    "refreshToken": "RT-xxx",
    "expiresIn": 1800
  }
}
```

#### 4.1.3 修改密码

```http
POST /api/auth/sso/change-password
Content-Type: application/json
Authorization: Bearer {accessToken}
```

**请求体**:

```json
{
  "oldPassword": "OldPass123",
  "newPassword": "NewPass456",
  "logoutAfterChange": true
}
```

**响应**:

```json
{
  "success": true,
  "message": "密码修改成功"
}
```

#### 4.1.4 租户切换

```http
POST /api/auth/sso/switch-tenant
Content-Type: application/json
Authorization: Bearer {accessToken}
```

**请求体**:

```json
{
  "targetTenantNo": "6000",
  "refreshToken": "RT-xxx"
}
```

**响应**:

```json
{
  "success": true,
  "data": {
    "accessToken": "AT-new-xxx",
    "refreshToken": "RT-new-xxx",
    "expiresIn": 1800,
    "user": {
      "tenantNo": "6000",
      "tenantName": "江苏东方盛虹股份有限公司"
    }
  }
}
```

#### 4.1.5 刷新 Token

```http
POST /api/auth/sso/refresh-token
Content-Type: application/json
```

**请求体**:

```json
{
  "refreshToken": "RT-xxx"
}
```

**响应**:

```json
{
  "success": true,
  "data": {
    "accessToken": "AT-new-xxx",
    "refreshToken": "RT-new-xxx",
    "expiresIn": 1800
  }
}
```

#### 4.1.6 退出登录

```http
POST /api/auth/sso/logout
Authorization: Bearer {accessToken}
```

**响应**:

```json
{
  "success": true,
  "message": "退出成功"
}
```

### 4.2 前端接口（Hooks）

#### 4.2.1 useEnterpriseLogin

```typescript
interface EnterpriseLoginOptions {
  redirectUri?: string
}

interface EnterpriseLoginResult {
  loginUrl: string
  redirectToSso: () => void
  isLoading: boolean
  error: Error | null
}

export function useEnterpriseLogin(
  options?: EnterpriseLoginOptions
): EnterpriseLoginResult
```

#### 4.2.2 useSsoCallback

```typescript
interface SsoCallbackResult {
  user: User | null
  isLoading: boolean
  error: Error | null
  requirePasswordChange: boolean
}

export function useSsoCallback(
  code: string | null,
  state: string | null
): SsoCallbackResult
```

#### 4.2.3 useTenantSwitch

```typescript
interface TenantSwitchOptions {
  onSuccess?: (user: User) => void
  onError?: (error: Error) => void
}

export function useTenantSwitch(
  options?: TenantSwitchOptions
): {
  switchTenant: (tenantNo: string) => Promise<void>
  isLoading: boolean
  error: Error | null
}
```

---

## 5. 数据模型设计

### 5.1 企业 SSO 用户信息映射

#### 企业 SSO 用户模型

```typescript
interface EnterpriseSsoUser {
  id: number
  employeeId: number
  username: string
  nickName: string
  phone: string
  email?: string
  tenantNo: string
  tenantName: string
  roleCode: string
  roleName: string
  positionCode?: string
  positionName?: string
  firstLogin: boolean
  tenantList: TenantInfo[]
  employments: Employment[]
}

interface TenantInfo {
  employeeId: number
  empName: string
  tenantNo: string
  tenantName: string
  position?: string
}

interface Employment {
  employeeId: number
  orgCodePath: string
  orgCodePathName: string
  positionCode: string
  positionName: string
}
```

#### SkillHub 平台用户模型

```typescript
interface SkillHubUser {
  id: number
  username: string
  displayName: string
  email?: string
  phone?: string
  status: UserStatus
  roles: Role[]
  createdAt: Date
  updatedAt: Date
}

interface EnterpriseIdentityBinding {
  id: number
  userId: number // SkillHub 用户 ID
  enterpriseUserId: number // 企业 SSO 用户 ID
  employeeId: number // 员工 ID
  tenantNo: string // 当前租户
  provider: string // 'enterprise-sso'
  createdAt: Date
  updatedAt: Date
}
```

### 5.2 Token 存储模型

#### Redis 存储结构

```
# Access Token 存储
Key: sso:access_token:{userId}:{tenantNo}
Value: {
  "token": "AT-xxx",
  "expiresAt": 1680000000,
  "scope": "read write"
}
TTL: 30 分钟

# Refresh Token 存储
Key: sso:refresh_token:{userId}
Value: {
  "token": "RT-xxx",
  "expiresAt": 1680000000
}
TTL: 2 小时

# 用户会话存储
Key: session:{sessionId}
Value: {
  "userId": 123,
  "tenantNo": "8000",
  "accessToken": "AT-xxx",
  "refreshToken": "RT-xxx",
  "userInfo": {...}
}
TTL: 2 小时
```

---

## 6. 安全设计

### 6.1 Token 安全

#### 6.1.1 Token 传输

- **HTTPS Only**: 所有 Token 传输必须使用 HTTPS
- **HttpOnly Cookie**: Refresh Token 存储在 HttpOnly Cookie 中，防止 XSS 攻击
- **Header 传输**: Access Token 通过 Authorization Header 传输

#### 6.1.2 Token 存储

- **前端存储**: Access Token 存储在内存中（不使用 LocalStorage）
- **后端存储**: Token 存储在 Redis，设置合理的过期时间
- **加密存储**: 敏感 Token 使用 AES 加密后存储

#### 6.1.3 Token 刷新

- **自动刷新**: Access Token 过期前 5 分钟自动刷新
- **静默刷新**: 后台刷新，不影响用户操作
- **Refresh Token 轮转**: 每次刷新后更新 Refresh Token

### 6.2 CSRF 防护

- **State 参数**: OAuth 流程中使用 state 参数防止 CSRF
- **CSRF Token**: 表单提交时验证 CSRF Token
- **SameSite Cookie**: Cookie 设置 SameSite=Lax

### 6.3 XSS 防护

- **输入验证**: 所有用户输入进行验证和转义
- **输出编码**: HTML 输出时进行编码
- **CSP 策略**: 配置 Content-Security-Policy

### 6.4 日志审计

记录以下安全事件：

- 登录成功/失败
- Token 刷新
- 租户切换
- 密码修改
- 退出登录
- 异常访问

---

## 7. 实施步骤

### 7.1 阶段一：基础框架搭建（1-2 天）

**后端任务**：

1. 创建 `EnterpriseDirectAuthProvider` 实现类
2. 创建 `EnterpriseSsoClient` 用于调用企业 SSO API
3. 配置 Spring Security Filter Chain
4. 实现 `EnterpriseIdentityBinding` 数据模型和 Repository

**前端任务**：

1. 创建 SSO 登录入口组件
2. 实现 `useEnterpriseLogin` Hook
3. 创建 SSO 回调处理页面

### 7.2 阶段二：核心流程实现（3-4 天）

**后端任务**：

1. 实现获取 SSO 登录地址接口
2. 实现 SSO 回调处理逻辑
3. 实现用户信息同步逻辑
4. 实现 Token 刷新机制
5. 集成 Redis 会话存储

**前端任务**：

1. 实现 `useSsoCallback` Hook
2. 实现首次登录修改密码流程
3. 实现 Token 自动刷新拦截器
4. 更新全局认证状态管理

### 7.3 阶段三：租户管理功能（2-3 天）

**后端任务**：

1. 实现租户切换接口
2. 实现租户信息查询接口
3. 实现多租户数据隔离

**前端任务**：

1. 实现租户切换组件
2. 实现 `useTenantSwitch` Hook
3. 实现租户信息展示

### 7.4 阶段四：安全加固和测试（2-3 天）

**安全加固**：

1. 实现 Token 加密存储
2. 配置 CSRF 防护
3. 配置 CSP 策略
4. 实现日志审计

**测试**：

1. 单元测试（覆盖率 80%+）
2. 集成测试
3. E2E 测试（关键流程）
4. 安全测试

### 7.5 阶段五：文档和部署（1 天）

1. 编写接口文档
2. 编写运维文档
3. 配置生产环境
4. 灰度发布

---

## 8. 配置管理

### 8.1 后端配置（application.yml）

```yaml
skillhub:
  auth:
    enterprise-sso:
      enabled: true
      provider-code: "enterprise-sso"
      display-name: "企业账号登录"
      sso-base-url: "http://sso.company.com"
      client-id: "skillhub"
      client-secret: "${SSO_CLIENT_SECRET}"
      callback-url: "https://skillhub.company.com/api/auth/sso/callback"
      token-endpoint: "/auth/sso/login"
      userinfo-endpoint: "/userinfo"
      logout-endpoint: "/logout"
      refresh-token-endpoint: "/auth/sso/refresh-token"
      access-token-expire: 1800 # 30分钟
      refresh-token-expire: 7200 # 2小时

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 2
```

### 8.2 前端配置（.env）

```bash
# 企业 SSO 配置
VITE_ENTERPRISE_SSO_ENABLED=true
VITE_ENTERPRISE_SSO_DISPLAY_NAME=企业账号登录

# API 配置
VITE_API_BASE_URL=http://localhost:8080/api
VITE_SSO_CALLBACK_PATH=/auth/sso/callback

# Token 配置
VITE_ACCESS_TOKEN_REFRESH_BEFORE_EXPIRE=300 # 5分钟
```

---

## 9. 监控和告警

### 9.1 监控指标

- **认证成功率**: 登录成功次数 / 总登录次数
- **Token 刷新成功率**: Token 刷新成功次数 / 总刷新次数
- **SSO API 响应时间**: 调用企业 SSO API 的平均响应时间
- **登录耗时**: 从点击登录到进入首页的平均耗时
- **异常登录次数**: 失败次数、异常 IP 等

### 9.2 告警规则

- 认证成功率 < 95%，触发告警
- SSO API 响应时间 > 3s，触发告警
- Token 刷新失败率 > 10%，触发告警
- 单用户 1 小时内登录失败 > 5 次，触发风控

---

## 10. 风险评估和应对

### 10.1 潜在风险

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 企业 SSO 服务不可用 | 高 | 中 | 提供降级方案，允许管理员本地登录 |
| Token 泄露 | 高 | 低 | Token 加密存储，设置短过期时间 |
| 租户数据泄露 | 高 | 低 | 实现严格的数据隔离和权限控制 |
| 性能问题 | 中 | 中 | Redis 缓存，减少 SSO API 调用 |
| 兼容性问题 | 中 | 低 | 保留原有登录方式作为备选 |

### 10.2 回滚方案

- **配置开关**: 通过配置快速关闭企业 SSO
- **数据库备份**: 实施前备份身份绑定数据
- **灰度发布**: 小范围验证后再全量发布
- **监控告警**: 实时监控异常并快速响应

---

## 11. FAQ

### Q1: 企业 SSO 和原有登录方式是否可以共存？

是的。设计支持多种登录方式共存：
- 企业 SSO 登录（主要方式）
- 本地账号登录（管理员备用）
- OAuth2 第三方登录（保留）

### Q2: 用户首次登录必须修改密码吗？

根据企业安全策略决定。设计方案支持：
- 强制修改密码（默认）
- 可选修改密码
- 不修改密码（不推荐）

### Q3: 租户切换后原有的数据是否可见？

不可见。租户切换后，用户只能访问当前租户的数据。如需访问其他租户数据，需要再次切换。

### Q4: Token 过期后如何处理？

自动刷新机制：
- Access Token 过期前 5 分钟自动刷新
- 过期后前端拦截器自动调用刷新接口
- Refresh Token 过期则需要重新登录

### Q5: 如何防止 CSRF 攻击？

采用多重防护：
- OAuth 流程中使用 state 参数
- Cookie 设置 SameSite 属性
- 关键操作验证 CSRF Token

---

## 12. 附录

### 12.1 参考资料

- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [Spring Security OAuth2](https://spring.io/projects/spring-security-oauth)
- [企业 SSO 接口文档](./sh-sso-借口设计文档.md)

### 12.2 相关流程图

- [企业 SSO 首次登录流程](./企业SSO首次登录流程.md)
- [企业 SSO 常规登录流程](./企业SSO常规登录流程.md)
- [企业 SSO 租户切换流程](./企业SSO租户切换流程.md)
- [企业 SSO 系统架构图](./企业SSO系统架构图.md)

### 12.3 更新日志

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0 | 2026-04-03 | Claude | 初始版本 |

---

**文档结束**
