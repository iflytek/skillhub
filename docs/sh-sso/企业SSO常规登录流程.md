# 企业 SSO 常规登录流程

## 流程图

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户浏览器
    participant Frontend as SkillHub 前端
    participant Backend as SkillHub 后端
    participant SSO as 企业 SSO 系统
    participant Redis as Redis 缓存
    participant DB as 数据库

    User->>Frontend: 访问登录页
    Frontend->>User: 展示登录页面
    
    User->>Frontend: 点击"企业账号登录"
    Frontend->>Backend: GET /api/auth/sso/login-url?redirectUri={callback}
    
    Backend->>Backend: 生成 state 参数
    Backend->>Redis: 缓存 state (TTL: 5分钟)
    Backend-->>Frontend: 返回 SSO 登录地址
    
    Frontend->>User: 重定向到企业 SSO
    Note over User,SSO: 跳转到企业 SSO 认证系统
    
    User->>SSO: 访问 SSO 登录页
    
    alt 用户已在 SSO 系统登录（SSO Session 有效）
        Note over SSO: 检测到有效 SSO Session
        SSO->>SSO: 直接生成授权码（无需输入密码）
        SSO->>User: 立即重定向回 SkillHub
    else 用户未登录或 Session 过期
        SSO->>User: 展示登录表单
        User->>SSO: 输入企业账号和密码
        SSO->>SSO: 验证用户凭证
        
        alt 认证失败
            SSO->>User: 显示错误信息
            User->>SSO: 重新输入
        end
        
        SSO->>SSO: 认证成功，生成授权码
        SSO->>User: 重定向回 SkillHub
    end
    
    User->>Frontend: 访问 /auth/sso/callback?code=xxx&state=yyy
    Frontend->>Backend: POST /api/auth/sso/callback<br/>{code, state}
    
    Backend->>Redis: 验证 state 参数
    alt state 无效
        Backend-->>Frontend: 返回 CSRF 错误
        Frontend->>User: 提示重新登录
    end
    
    Backend->>SSO: POST /auth/sso/login<br/>使用 code 换取 Token
    SSO-->>Backend: 返回 Access Token + Refresh Token
    
    Backend->>Backend: 解析 Token 中的用户 ID
    Backend->>DB: 查询身份绑定记录<br/>WHERE enterprise_user_id = ?
    
    alt 绑定记录不存在
        Note over Backend: 用户从未登录过，应走首次登录流程
        Backend-->>Frontend: 返回错误（未绑定）
        Frontend->>User: 提示联系管理员
    end
    
    Backend->>SSO: GET /userinfo<br/>Header: Authorization: Bearer {token}
    SSO-->>Backend: 返回最新用户信息
    
    Backend->>DB: 更新用户信息<br/>UPDATE users SET<br/>nick_name=?, phone=?, updated_at=NOW()
    Backend->>DB: 更新身份绑定信息<br/>UPDATE enterprise_identity_binding<br/>SET tenant_no=?, updated_at=NOW()
    
    Backend->>Redis: 缓存 Access Token<br/>Key: sso:access_token:{userId}<br/>TTL: 1800 (30分钟)
    Backend->>Redis: 缓存 Refresh Token<br/>Key: sso:refresh_token:{userId}<br/>TTL: 7200 (2小时)
    Backend->>Redis: 缓存用户会话<br/>Key: session:{sessionId}<br/>TTL: 7200
    
    Backend->>Backend: 生成 SkillHub JWT Token
    Backend-->>Frontend: 返回登录成功<br/>{user, token, requirePasswordChange: false}
    
    Frontend->>Frontend: 保存 Token 到内存
    Frontend->>Frontend: 更新全局认证状态
    
    alt 有 returnTo 参数
        Frontend->>User: 跳转到指定页面
    else 无 returnTo 参数
        Frontend->>User: 跳转到首页
    end
    
    User->>Frontend: 访问目标页面
    Frontend->>Backend: API 请求<br/>Header: Authorization: Bearer {token}
    
    Backend->>Redis: 验证 Token 有效性
    alt Token 即将过期（剩余时间 < 5分钟）
        Backend->>Backend: 触发自动刷新机制
        Backend->>SSO: POST /auth/sso/refresh-token<br/>{refreshToken}
        SSO-->>Backend: 返回新的 Access Token
        Backend->>Redis: 更新缓存的 Token
        Backend-->>Frontend: 返回数据 + 新 Token
        Frontend->>Frontend: 更新内存中的 Token
    else Token 有效
        Backend->>DB: 查询数据
        Backend-->>Frontend: 返回数据
    end
    
    Frontend->>User: 展示页面内容
```

## 常规登录 vs 首次登录对比

| 特性 | 首次登录 | 常规登录 |
|------|----------|----------|
| **SSO Session** | 不存在 | 可能存在（免密登录） |
| **绑定记录** | 不存在，需创建 | 已存在，直接使用 |
| **修改密码** | 必须修改 | 不需要 |
| **账号创建** | 需要创建平台账号 | 已有账号，更新信息 |
| **用户体验** | 需要额外操作 | 流畅快速 |

## SSO Session 免密登录

### 工作原理

当用户在企业 SSO 系统已登录时：

1. 用户访问 SkillHub 登录页
2. 跳转到 SSO 登录页
3. **SSO 检测到有效 Session**
4. SSO 直接生成授权码，无需输入密码
5. 自动重定向回 SkillHub
6. 用户感知：几乎无感知，秒级完成登录

### 优点

- **用户体验佳**: 无需重复输入密码
- **真正的单点登录**: 一次登录，多系统通用
- **安全性高**: Session 有有效期控制

### Session 过期处理

```
SSO Session 有效期: 8 小时（企业可配置）

过期后:
  1. 用户访问 SkillHub
  2. 跳转到 SSO 登录页
  3. SSO 检测 Session 过期
  4. 要求用户重新输入密码
  5. 完成登录流程
```

## Token 自动刷新机制

### 刷新时机

- **方式一**: Access Token 过期前 5 分钟自动刷新
- **方式二**: API 返回 401 时触发刷新

### 刷新流程

```mermaid
sequenceDiagram
    participant Frontend as 前端
    participant Backend as 后端
    participant SSO as SSO 系统
    participant Redis as Redis

    Frontend->>Backend: API 请求
    Backend->>Redis: 检查 Token 有效期
    
    alt Token 剩余时间 < 5分钟
        Backend->>SSO: POST /auth/sso/refresh-token<br/>{refreshToken}
        SSO->>SSO: 验证 Refresh Token
        
        alt Refresh Token 有效
            SSO-->>Backend: 返回新 Access Token
            Backend->>Redis: 更新缓存<br/>sso:access_token:{userId}
            Backend-->>Frontend: 返回数据 + 新 Token
            Frontend->>Frontend: 更新 Token
        else Refresh Token 过期
            SSO-->>Backend: 返回 401
            Backend-->>Frontend: 返回 401
            Frontend->>Frontend: 清除认证状态
            Frontend->>Frontend: 跳转到登录页
        end
    else Token 仍然有效
        Backend-->>Frontend: 返回数据
    end
```

### 前端拦截器实现

```typescript
// Axios 响应拦截器
axios.interceptors.response.use(
  (response) => {
    // 检查响应头中是否有新 Token
    const newToken = response.headers['x-new-access-token']
    if (newToken) {
      // 更新内存中的 Token
      updateAccessToken(newToken)
    }
    return response
  },
  async (error) => {
    if (error.response?.status === 401) {
      // Token 过期，尝试刷新
      try {
        const newToken = await refreshAccessToken()
        updateAccessToken(newToken)
        // 重试原请求
        return axios.request(error.config)
      } catch (refreshError) {
        // Refresh Token 也过期，跳转登录
        redirectToLogin()
      }
    }
    return Promise.reject(error)
  }
)
```

## 用户信息同步策略

### 同步时机

1. **每次登录**: 从 SSO 获取最新信息并更新
2. **租户切换**: 切换租户时更新租户相关信息
3. **定时同步**: 后台任务每天同步（可选）

### 同步字段

```sql
UPDATE users SET
  nick_name = ?,      -- 昵称/姓名
  phone = ?,          -- 手机号
  email = ?,          -- 邮箱
  updated_at = NOW()
WHERE id = ?;

UPDATE enterprise_identity_binding SET
  tenant_no = ?,      -- 当前租户
  employee_id = ?,    -- 员工 ID
  updated_at = NOW()
WHERE user_id = ?;
```

### 不同步的字段

- `username`: 不变，作为唯一标识
- `id`: 平台内部 ID，不变
- `roles`: 平台角色，手动管理
- `created_at`: 创建时间，不变

## 错误处理

### 常见错误

| 错误码 | 场景 | 处理方式 |
|--------|------|----------|
| 401 | Token 过期 | 自动刷新，刷新失败则跳转登录 |
| 403 | 账号被禁用 | 显示提示，联系管理员 |
| 404 | 绑定记录不存在 | 引导重新登录或联系管理员 |
| 500 | SSO 系统异常 | 显示错误，提供降级方案 |

### 降级方案

当企业 SSO 不可用时：

```
1. 检测 SSO 服务健康状态
2. 超过 3 次连续失败，触发降级
3. 临时启用本地管理员登录
4. 发送告警通知运维团队
5. 显示维护公告给用户
```

## 性能监控

### 关键指标

- **登录耗时**: 从点击登录到进入首页的总耗时
  - 目标: < 3 秒
  - 告警阈值: > 5 秒

- **Token 刷新成功率**: 成功刷新次数 / 总刷新次数
  - 目标: > 99%
  - 告警阈值: < 95%

- **SSO API 响应时间**: 调用 SSO 接口的平均耗时
  - 目标: < 500ms
  - 告警阈值: > 2s

### 性能优化

1. **Redis 缓存**: 缓存 Token 和用户信息
2. **并发请求**: 用户信息查询和 Token 缓存并发执行
3. **CDN 加速**: 前端资源使用 CDN
4. **连接复用**: 复用 HTTP 连接减少握手时间

## 测试用例

### 正常流程

- [x] 已登录用户访问 SkillHub（SSO Session 有效）
- [x] 未登录用户访问（需输入密码）
- [x] Token 自动刷新成功
- [x] 用户信息同步成功
- [x] 多标签页 Token 同步

### 异常流程

- [x] SSO Session 过期
- [x] Refresh Token 过期
- [x] 绑定记录被删除
- [x] 账号被禁用
- [x] SSO 系统不可用

### 性能测试

- [x] 100 并发用户同时登录
- [x] 1000 并发 API 请求
- [x] Token 刷新频繁场景

---

**相关文档**:
- [企业 SSO 登录接入方案设计](./企业SSO登录接入方案设计.md)
- [企业 SSO 首次登录流程](./企业SSO首次登录流程.md)
- [企业 SSO 租户切换流程](./企业SSO租户切换流程.md)
