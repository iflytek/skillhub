# 企业 SSO 租户切换流程

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

    Note over User: 用户已登录，当前租户: 盛虹石化(8000)
    
    User->>Frontend: 点击租户切换下拉菜单
    Frontend->>Frontend: 从用户信息中读取 tenantList
    Frontend->>User: 展示可切换的租户列表
    
    Note over Frontend: tenantList 示例:<br/>1. 盛虹石化 (8000) - 当前<br/>2. 江苏东方盛虹 (6000)
    
    User->>Frontend: 选择目标租户 "江苏东方盛虹 (6000)"
    Frontend->>Frontend: 弹出确认对话框<br/>"切换租户将刷新页面，是否继续？"
    
    User->>Frontend: 点击"确定"
    
    Frontend->>Frontend: 从 Cookie/内存中获取 Refresh Token
    Frontend->>Backend: POST /api/auth/sso/switch-tenant<br/>{<br/>  targetTenantNo: "6000",<br/>  refreshToken: "RT-xxx"<br/>}<br/>Header: Authorization: Bearer {currentToken}
    
    Backend->>Redis: 验证当前 Access Token
    alt Token 无效或过期
        Backend-->>Frontend: 返回 401 Unauthorized
        Frontend->>User: 提示"登录已过期，请重新登录"
        Frontend->>Frontend: 跳转到登录页
    end
    
    Backend->>Backend: 从 Token 中解析当前用户 ID
    Backend->>DB: 查询用户的租户列表<br/>SELECT * FROM enterprise_identity_binding<br/>WHERE user_id = ?
    
    Backend->>Backend: 验证目标租户是否在允许列表中
    alt 目标租户不在允许列表
        Backend-->>Frontend: 返回 403 Forbidden<br/>{message: "无权访问该租户"}
        Frontend->>User: 显示错误提示
    end
    
    Backend->>SSO: POST /auth/sso/refresh-token<br/>{<br/>  targetTenantNo: "6000",<br/>  refreshToken: "RT-xxx"<br/>}
    
    SSO->>SSO: 验证 Refresh Token
    alt Refresh Token 无效或过期
        SSO-->>Backend: 返回 401/415 (缺少或无效 Token)
        Backend-->>Frontend: 返回 401
        Frontend->>User: 提示"会话已过期，请重新登录"
        Frontend->>Frontend: 清除本地认证状态
        Frontend->>Frontend: 跳转到登录页
    end
    
    SSO->>SSO: 验证用户对目标租户的访问权限
    alt 用户无权访问目标租户
        SSO-->>Backend: 返回 403
        Backend-->>Frontend: 返回 403
        Frontend->>User: 提示"无权访问该租户"
    end
    
    SSO->>SSO: 生成新的 Token（包含目标租户信息）
    SSO-->>Backend: 返回新的 Token 和用户信息<br/>{<br/>  accessToken: "AT-new-xxx",<br/>  refreshToken: "RT-new-xxx",<br/>  expiresIn: 1800,<br/>  user: {<br/>    tenantNo: "6000",<br/>    tenantName: "江苏东方盛虹",<br/>    ...<br/>  }<br/>}
    
    Backend->>Backend: 解析新 Token 中的用户信息
    Backend->>DB: 更新身份绑定记录的当前租户<br/>UPDATE enterprise_identity_binding<br/>SET tenant_no = "6000", updated_at = NOW()<br/>WHERE user_id = ?
    
    Backend->>Redis: 清除旧租户的 Token 缓存<br/>DEL sso:access_token:{userId}:8000
    Backend->>Redis: 缓存新租户的 Access Token<br/>SET sso:access_token:{userId}:6000 {token}<br/>EXPIRE 1800
    
    Backend->>Redis: 更新 Refresh Token 缓存<br/>SET sso:refresh_token:{userId} {newRefreshToken}<br/>EXPIRE 7200
    
    Backend->>Redis: 更新用户会话<br/>HSET session:{sessionId}<br/>  tenantNo "6000"<br/>  accessToken "AT-new-xxx"<br/>  refreshToken "RT-new-xxx"
    
    Backend->>SSO: GET /userinfo<br/>Header: Authorization: Bearer {newAccessToken}
    SSO-->>Backend: 返回目标租户下的完整用户信息<br/>(包含该租户的部门任职信息)
    
    Backend->>DB: 更新用户在该租户的信息<br/>UPDATE users SET<br/>  position_code = ?,<br/>  position_name = ?,<br/>  updated_at = NOW()
    
    Backend->>Backend: 生成新的 SkillHub JWT Token
    Backend-->>Frontend: 返回切换成功<br/>{<br/>  success: true,<br/>  data: {<br/>    accessToken: "AT-new-xxx",<br/>    refreshToken: "RT-new-xxx",<br/>    user: {...},<br/>    message: "已切换到江苏东方盛虹"<br/>  }<br/>}
    
    Frontend->>Frontend: 更新内存中的 Token
    Frontend->>Frontend: 更新全局用户状态<br/>(包括租户信息、部门任职等)
    Frontend->>Frontend: 清除所有 API 查询缓存<br/>(React Query: queryClient.clear())
    
    Frontend->>User: 显示成功提示<br/>"已切换到江苏东方盛虹"
    Frontend->>Frontend: 刷新当前页面<br/>window.location.reload()
    
    User->>Frontend: 页面重新加载
    Frontend->>Backend: 重新请求数据<br/>(使用新租户的 Token)
    
    Backend->>DB: 查询该租户的数据<br/>WHERE tenant_no = "6000"
    Backend-->>Frontend: 返回新租户的数据
    
    Frontend->>User: 展示新租户的页面内容
    
    Note over User,Frontend: 租户切换完成<br/>当前租户: 江苏东方盛虹(6000)
```

## 租户切换机制详解

### 1. 租户列表来源

用户的租户列表在登录时从 SSO 获取：

```json
{
  "tenantList": [
    {
      "employeeId": 74,
      "empName": "张波",
      "tenantNo": "8000",
      "tenantName": "盛虹石化",
      "position": "综合副经理"
    },
    {
      "employeeId": 66,
      "empName": "用户-QGJNDWWD",
      "tenantNo": "6000",
      "tenantName": "江苏东方盛虹股份有限公司",
      "position": "部门经理"
    }
  ]
}
```

**前端展示逻辑**：

```typescript
// 租户下拉菜单组件
function TenantSwitcher() {
  const { user } = useAuth()
  const { switchTenant, isLoading } = useTenantSwitch()
  
  const currentTenant = user?.tenantNo
  const tenantList = user?.tenantList || []
  
  return (
    <Select
      value={currentTenant}
      onChange={(tenantNo) => switchTenant(tenantNo)}
      disabled={isLoading}
    >
      {tenantList.map(tenant => (
        <Option key={tenant.tenantNo} value={tenant.tenantNo}>
          {tenant.tenantName}
          {tenant.tenantNo === currentTenant && ' (当前)'}
        </Option>
      ))}
    </Select>
  )
}
```

### 2. Token 刷新策略

切换租户时，必须刷新 Token 的原因：

1. **租户隔离**: Access Token 中包含租户信息，用于后端数据隔离
2. **权限变更**: 不同租户下用户可能有不同的权限
3. **审计追踪**: 新 Token 记录租户切换操作

**Token 中的租户信息**：

```json
{
  "userId": 123,
  "username": "zhangbo",
  "tenantNo": "6000",  // 当前租户
  "employeeId": 66,    // 该租户下的员工 ID
  "iat": 1680000000,
  "exp": 1680001800
}
```

### 3. 数据隔离机制

#### 后端数据查询

所有数据查询都带租户过滤：

```java
@RestController
public class DataController {
    
    @GetMapping("/api/projects")
    public List<Project> getProjects(@AuthenticationPrincipal PlatformPrincipal principal) {
        String tenantNo = principal.getTenantNo();
        // 只查询当前租户的数据
        return projectRepository.findByTenantNo(tenantNo);
    }
}
```

#### 数据库设计

所有业务表都包含租户字段：

```sql
CREATE TABLE projects (
    id BIGINT PRIMARY KEY,
    tenant_no VARCHAR(32) NOT NULL,  -- 租户号
    name VARCHAR(255),
    -- 其他字段...
    INDEX idx_tenant (tenant_no)
);
```

### 4. 缓存清除策略

切换租户后需要清除的缓存：

```typescript
// 前端缓存清除
async function switchTenant(tenantNo: string) {
  // 1. 调用切换接口
  const result = await api.switchTenant(tenantNo)
  
  // 2. 清除 React Query 缓存
  queryClient.clear()
  
  // 3. 清除本地存储（如果有）
  localStorage.removeItem('cached-data')
  
  // 4. 重置全局状态
  resetGlobalState()
  
  // 5. 刷新页面
  window.location.reload()
}
```

```java
// 后端缓存清除
public void switchTenant(String userId, String newTenantNo) {
    // 清除旧租户的数据缓存
    redisTemplate.delete("user:" + userId + ":data:*");
    
    // 清除旧租户的权限缓存
    redisTemplate.delete("user:" + userId + ":permissions");
    
    // 更新会话中的租户信息
    updateSessionTenant(userId, newTenantNo);
}
```

## 租户切换的前端实现

### useTenantSwitch Hook

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'

interface TenantSwitchOptions {
  onSuccess?: (user: User) => void
  onError?: (error: Error) => void
}

export function useTenantSwitch(options?: TenantSwitchOptions) {
  const queryClient = useQueryClient()
  
  const mutation = useMutation({
    mutationFn: async (targetTenantNo: string) => {
      // 获取当前的 Refresh Token
      const refreshToken = getRefreshToken()
      
      if (!refreshToken) {
        throw new Error('Refresh Token 不存在，请重新登录')
      }
      
      // 调用租户切换接口
      return authApi.switchTenant({
        targetTenantNo,
        refreshToken
      })
    },
    
    onSuccess: (data) => {
      const { accessToken, refreshToken, user } = data
      
      // 更新 Token
      setAccessToken(accessToken)
      setRefreshToken(refreshToken)
      
      // 更新用户信息
      queryClient.setQueryData<User>(['auth', 'me'], user)
      
      // 清除所有查询缓存
      queryClient.clear()
      
      // 回调
      options?.onSuccess?.(user)
      
      // 刷新页面以确保数据完全更新
      setTimeout(() => {
        window.location.reload()
      }, 500)
    },
    
    onError: (error) => {
      if (error.message.includes('401')) {
        // Token 过期，跳转登录
        redirectToLogin()
      } else if (error.message.includes('403')) {
        // 无权访问
        showError('您无权访问该租户')
      }
      
      options?.onError?.(error)
    }
  })
  
  return {
    switchTenant: mutation.mutate,
    isLoading: mutation.isPending,
    error: mutation.error
  }
}
```

### 租户选择器组件

```typescript
import { useState } from 'react'
import { Select, Modal, message } from 'antd'
import { useAuth } from '@/features/auth/use-auth'
import { useTenantSwitch } from '@/features/auth/use-tenant-switch'

export function TenantSwitcher() {
  const { user } = useAuth()
  const { switchTenant, isLoading } = useTenantSwitch({
    onSuccess: (newUser) => {
      message.success(`已切换到 ${newUser.tenantName}`)
    },
    onError: (error) => {
      message.error(error.message)
    }
  })
  
  const [confirmVisible, setConfirmVisible] = useState(false)
  const [targetTenant, setTargetTenant] = useState<string>()
  
  const handleTenantChange = (tenantNo: string) => {
    if (tenantNo === user?.tenantNo) {
      return // 当前租户，不切换
    }
    
    setTargetTenant(tenantNo)
    setConfirmVisible(true)
  }
  
  const handleConfirm = () => {
    if (targetTenant) {
      switchTenant(targetTenant)
      setConfirmVisible(false)
    }
  }
  
  const currentTenant = user?.tenantNo
  const tenantList = user?.tenantList || []
  
  if (tenantList.length <= 1) {
    // 只有一个租户，不显示切换器
    return null
  }
  
  return (
    <>
      <Select
        value={currentTenant}
        onChange={handleTenantChange}
        loading={isLoading}
        style={{ width: 200 }}
        placeholder="选择租户"
      >
        {tenantList.map(tenant => (
          <Select.Option key={tenant.tenantNo} value={tenant.tenantNo}>
            {tenant.tenantName}
            {tenant.tenantNo === currentTenant && ' ✓'}
          </Select.Option>
        ))}
      </Select>
      
      <Modal
        title="确认切换租户"
        open={confirmVisible}
        onOk={handleConfirm}
        onCancel={() => setConfirmVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <p>切换租户将刷新页面，未保存的数据可能会丢失。</p>
        <p>是否继续切换？</p>
      </Modal>
    </>
  )
}
```

## 后端实现

### SsoController - 租户切换接口

```java
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {
    
    @Autowired
    private EnterpriseSsoClient ssoClient;
    
    @Autowired
    private IdentityBindingService identityBindingService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @PostMapping("/switch-tenant")
    public ResponseEntity<SwitchTenantResponse> switchTenant(
            @RequestBody SwitchTenantRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        
        String userId = principal.getUserId();
        String targetTenantNo = request.getTargetTenantNo();
        String refreshToken = request.getRefreshToken();
        
        // 1. 验证用户是否有权访问目标租户
        List<String> allowedTenants = identityBindingService.getAllowedTenants(userId);
        if (!allowedTenants.contains(targetTenantNo)) {
            throw new AccessDeniedException("无权访问该租户");
        }
        
        // 2. 调用 SSO 刷新 Token
        SsoTokenResponse tokenResponse = ssoClient.refreshToken(
            targetTenantNo, 
            refreshToken
        );
        
        // 3. 更新身份绑定记录
        identityBindingService.updateCurrentTenant(userId, targetTenantNo);
        
        // 4. 清除旧租户缓存
        clearTenantCache(userId);
        
        // 5. 缓存新 Token
        cacheTokens(userId, targetTenantNo, tokenResponse);
        
        // 6. 获取目标租户的用户信息
        User user = ssoClient.getUserInfo(tokenResponse.getAccessToken());
        
        // 7. 更新用户信息
        identityBindingService.updateUserInfo(userId, user);
        
        // 8. 返回结果
        return ResponseEntity.ok(SwitchTenantResponse.builder()
            .accessToken(tokenResponse.getAccessToken())
            .refreshToken(tokenResponse.getRefreshToken())
            .expiresIn(tokenResponse.getExpiresIn())
            .user(user)
            .build());
    }
    
    private void clearTenantCache(String userId) {
        // 清除数据缓存
        redisTemplate.delete("user:" + userId + ":*");
        
        // 清除权限缓存
        redisTemplate.delete("permissions:" + userId);
    }
    
    private void cacheTokens(String userId, String tenantNo, SsoTokenResponse tokens) {
        // 缓存 Access Token
        String accessTokenKey = String.format("sso:access_token:%s:%s", userId, tenantNo);
        redisTemplate.opsForValue().set(
            accessTokenKey, 
            tokens.getAccessToken(),
            tokens.getExpiresIn(), 
            TimeUnit.SECONDS
        );
        
        // 缓存 Refresh Token
        String refreshTokenKey = String.format("sso:refresh_token:%s", userId);
        redisTemplate.opsForValue().set(
            refreshTokenKey,
            tokens.getRefreshToken(),
            tokens.getRefreshExpiresIn(),
            TimeUnit.SECONDS
        );
    }
}
```

## 安全考虑

### 1. 权限验证

严格验证用户对目标租户的访问权限：

```java
public boolean hasAccessToTenant(String userId, String tenantNo) {
    // 查询用户的租户列表
    List<TenantInfo> tenants = identityBindingRepository
        .findTenantsByUserId(userId);
    
    // 检查目标租户是否在列表中
    return tenants.stream()
        .anyMatch(t -> t.getTenantNo().equals(tenantNo));
}
```

### 2. 审计日志

记录所有租户切换操作：

```java
@Aspect
public class TenantSwitchAudit {
    
    @AfterReturning("execution(* switchTenant(..))")
    public void auditTenantSwitch(JoinPoint joinPoint) {
        SwitchTenantRequest request = (SwitchTenantRequest) joinPoint.getArgs()[0];
        PlatformPrincipal principal = (PlatformPrincipal) joinPoint.getArgs()[1];
        
        auditLog.info("用户 {} 从租户 {} 切换到租户 {}",
            principal.getUserId(),
            principal.getTenantNo(),
            request.getTargetTenantNo()
        );
    }
}
```

### 3. 频率限制

防止恶意频繁切换：

```java
public void switchTenant(...) {
    String rateLimitKey = "tenant:switch:limit:" + userId;
    Long count = redisTemplate.opsForValue().increment(rateLimitKey);
    
    if (count == 1) {
        // 首次设置过期时间
        redisTemplate.expire(rateLimitKey, 1, TimeUnit.MINUTES);
    }
    
    if (count > 10) {
        throw new RateLimitException("切换过于频繁，请稍后再试");
    }
    
    // ... 正常切换逻辑
}
```

## 测试用例

### 正常流程测试

- [x] 切换到有权限的租户成功
- [x] 数据正确隔离
- [x] Token 正确刷新
- [x] 用户信息正确更新
- [x] 缓存正确清除

### 异常流程测试

- [x] 切换到无权限租户被拒绝
- [x] Refresh Token 过期跳转登录
- [x] 频繁切换触发限流
- [x] SSO 系统不可用
- [x] 并发切换处理

### 性能测试

- [x] 100 用户同时切换租户
- [x] 切换后首页加载速度
- [x] 缓存清除性能影响

---

**相关文档**:
- [企业 SSO 登录接入方案设计](./企业SSO登录接入方案设计.md)
- [企业 SSO 首次登录流程](./企业SSO首次登录流程.md)
- [企业 SSO 常规登录流程](./企业SSO常规登录流程.md)
