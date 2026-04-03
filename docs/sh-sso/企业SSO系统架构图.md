# 企业 SSO 系统架构图

## 总体架构

```mermaid
graph TB
    subgraph "用户层"
        U1[用户浏览器]
        U2[移动端 App]
    end
    
    subgraph "前端层"
        FE1[React 应用]
        FE2[登录页面]
        FE3[SSO 回调页面]
        FE4[租户切换组件]
    end
    
    subgraph "网关层"
        GW[API Gateway / Nginx]
    end
    
    subgraph "后端服务层"
        subgraph "认证服务"
            AS1[SsoController]
            AS2[EnterpriseDirectAuthProvider]
            AS3[SecurityFilterChain]
        end
        
        subgraph "业务服务"
            BS1[User Service]
            BS2[Project Service]
            BS3[其他业务服务]
        end
        
        subgraph "核心服务"
            CS1[IdentityBindingService]
            CS2[TokenService]
            CS3[TenantService]
        end
    end
    
    subgraph "数据层"
        DB[(MySQL Database)]
        Redis[(Redis Cache)]
    end
    
    subgraph "外部系统"
        SSO[企业 SSO 认证系统]
    end
    
    U1 --> FE1
    U2 --> FE1
    FE1 --> FE2
    FE1 --> FE3
    FE1 --> FE4
    
    FE1 --> GW
    FE2 --> GW
    FE3 --> GW
    FE4 --> GW
    
    GW --> AS1
    GW --> BS1
    GW --> BS2
    GW --> BS3
    
    AS1 --> AS2
    AS1 --> AS3
    AS2 --> CS1
    AS2 --> CS2
    AS1 --> CS3
    
    BS1 --> CS3
    BS2 --> CS3
    
    CS1 --> DB
    CS2 --> Redis
    CS3 --> DB
    CS3 --> Redis
    
    AS2 --> SSO
    AS1 --> SSO
```

## 认证流程架构

```mermaid
flowchart LR
    subgraph "请求入口"
        A[HTTP Request]
    end
    
    subgraph "Spring Security Filter Chain"
        B[ApiTokenAuthFilter]
        C[SessionAuthFilter]
        D[SsoAuthFilter]
        E[AnonymousAuthFilter]
    end
    
    subgraph "认证提供者"
        F[ApiTokenAuthProvider]
        G[SessionAuthProvider]
        H[EnterpriseDirectAuthProvider]
    end
    
    subgraph "认证结果"
        I[PlatformPrincipal]
        J[Authentication Success]
        K[Authentication Failure]
    end
    
    A --> B
    B -->|有 API Token| F
    B -->|无 API Token| C
    
    C -->|有 Session| G
    C -->|无 Session| D
    
    D -->|SSO Token| H
    D -->|无 Token| E
    
    F --> I
    G --> I
    H --> I
    
    I --> J
    E --> K
```

## Token 生命周期管理

```mermaid
stateDiagram-v2
    [*] --> 未登录
    
    未登录 --> SSO认证: 用户点击登录
    
    SSO认证 --> Token签发: SSO 验证成功
    Token签发 --> Token有效: 签发 Access + Refresh Token
    
    Token有效 --> Token即将过期: 使用 < 5分钟
    Token即将过期 --> 自动刷新: 后台刷新
    自动刷新 --> Token有效: 刷新成功
    
    Token有效 --> Token过期: 超过有效期
    Token过期 --> 尝试刷新: 前端拦截 401
    尝试刷新 --> Token有效: Refresh Token 有效
    尝试刷新 --> 未登录: Refresh Token 过期
    
    Token有效 --> 租户切换: 用户切换租户
    租户切换 --> Token签发: 刷新为新租户 Token
    
    Token有效 --> 未登录: 用户主动登出
```

## 数据库设计

```mermaid
erDiagram
    users ||--o{ enterprise_identity_binding : has
    users ||--o{ user_role_binding : has
    roles ||--o{ user_role_binding : has
    roles ||--o{ role_permission : has
    permissions ||--o{ role_permission : has
    users ||--o{ api_tokens : creates
    
    users {
        bigint id PK
        string username UK
        string display_name
        string email
        string phone
        string status
        timestamp created_at
        timestamp updated_at
    }
    
    enterprise_identity_binding {
        bigint id PK
        bigint user_id FK
        bigint enterprise_user_id
        bigint employee_id
        string tenant_no
        string provider
        timestamp created_at
        timestamp updated_at
    }
    
    user_role_binding {
        bigint id PK
        bigint user_id FK
        bigint role_id FK
        string tenant_no
        timestamp created_at
    }
    
    roles {
        bigint id PK
        string code UK
        string name
        string description
        timestamp created_at
    }
    
    role_permission {
        bigint id PK
        bigint role_id FK
        bigint permission_id FK
    }
    
    permissions {
        bigint id PK
        string code UK
        string name
        string resource
        string action
        timestamp created_at
    }
    
    api_tokens {
        bigint id PK
        bigint user_id FK
        string token_hash
        string scope
        timestamp expires_at
        timestamp created_at
    }
```

## Redis 缓存架构

```mermaid
graph TB
    subgraph "Token 缓存"
        T1["Key: sso:access_token:{userId}:{tenantNo}<br/>Value: Access Token<br/>TTL: 30分钟"]
        T2["Key: sso:refresh_token:{userId}<br/>Value: Refresh Token<br/>TTL: 2小时"]
    end
    
    subgraph "会话缓存"
        S1["Key: session:{sessionId}<br/>Value: Session Data<br/>TTL: 2小时"]
    end
    
    subgraph "用户信息缓存"
        U1["Key: user:info:{userId}<br/>Value: User Profile<br/>TTL: 1小时"]
        U2["Key: user:tenants:{userId}<br/>Value: Tenant List<br/>TTL: 1小时"]
    end
    
    subgraph "权限缓存"
        P1["Key: permissions:{userId}:{tenantNo}<br/>Value: Permission List<br/>TTL: 30分钟"]
    end
    
    subgraph "限流缓存"
        R1["Key: rate:limit:login:{ip}<br/>Value: Counter<br/>TTL: 5分钟"]
        R2["Key: rate:limit:switch:{userId}<br/>Value: Counter<br/>TTL: 1分钟"]
    end
    
    subgraph "防重放缓存"
        A1["Key: auth:state:{state}<br/>Value: Nonce<br/>TTL: 5分钟"]
    end
```

## 多租户数据隔离

```mermaid
flowchart TB
    subgraph "请求处理"
        A[API Request]
        B[Extract Token]
        C[Parse Tenant Info]
    end
    
    subgraph "租户上下文"
        D[TenantContext]
        E[ThreadLocal]
    end
    
    subgraph "数据访问层"
        F[MyBatis Interceptor]
        G[Auto Inject tenant_no]
        H[SQL: WHERE tenant_no = ?]
    end
    
    subgraph "数据库"
        I[(Multi-Tenant Data)]
    end
    
    A --> B
    B --> C
    C --> D
    D --> E
    
    E --> F
    F --> G
    G --> H
    H --> I
    
    style D fill:#f9f,stroke:#333
    style E fill:#f9f,stroke:#333
```

## 安全防护层次

```mermaid
graph TB
    subgraph "网络层"
        N1[HTTPS/TLS 1.3]
        N2[DDoS 防护]
        N3[WAF 防火墙]
    end
    
    subgraph "应用层"
        A1[CSRF Token]
        A2[XSS 过滤]
        A3[SQL 注入防护]
        A4[参数验证]
    end
    
    subgraph "认证层"
        AU1[Token 加密]
        AU2[Token 签名验证]
        AU3[State 参数验证]
        AU4[Refresh Token 轮转]
    end
    
    subgraph "授权层"
        AZ1[RBAC 权限控制]
        AZ2[租户隔离]
        AZ3[API 权限验证]
    end
    
    subgraph "审计层"
        L1[访问日志]
        L2[操作审计]
        L3[异常告警]
    end
    
    N1 --> A1
    N2 --> A2
    N3 --> A3
    A4 --> AU1
    AU2 --> AZ1
    AU3 --> AZ2
    AU4 --> AZ3
    AZ1 --> L1
    AZ2 --> L2
    AZ3 --> L3
```

## 部署架构

```mermaid
graph TB
    subgraph "负载均衡层"
        LB[Nginx / ALB]
    end
    
    subgraph "应用服务器集群"
        APP1[SkillHub Server 1]
        APP2[SkillHub Server 2]
        APP3[SkillHub Server 3]
    end
    
    subgraph "数据库集群"
        subgraph "主从复制"
            DBM[(MySQL Master)]
            DBS1[(MySQL Slave 1)]
            DBS2[(MySQL Slave 2)]
        end
    end
    
    subgraph "缓存集群"
        subgraph "Redis Sentinel"
            RM[(Redis Master)]
            RS1[(Redis Slave 1)]
            RS2[(Redis Slave 2)]
        end
    end
    
    subgraph "外部依赖"
        SSO[企业 SSO 系统]
    end
    
    subgraph "监控系统"
        M1[Prometheus]
        M2[Grafana]
        M3[AlertManager]
    end
    
    LB --> APP1
    LB --> APP2
    LB --> APP3
    
    APP1 --> DBM
    APP2 --> DBM
    APP3 --> DBM
    
    DBM --> DBS1
    DBM --> DBS2
    
    APP1 --> RM
    APP2 --> RM
    APP3 --> RM
    
    RM --> RS1
    RM --> RS2
    
    APP1 --> SSO
    APP2 --> SSO
    APP3 --> SSO
    
    APP1 --> M1
    APP2 --> M1
    APP3 --> M1
    
    M1 --> M2
    M1 --> M3
```

## 关键组件说明

### 1. EnterpriseDirectAuthProvider

**职责**:
- 实现 `DirectAuthProvider` 接口
- 对接企业 SSO 认证 API
- Token 获取与验证
- 用户信息映射

**核心方法**:

```java
public interface DirectAuthProvider {
    String providerCode();
    PlatformPrincipal authenticate(DirectAuthRequest request);
}

@Service
public class EnterpriseDirectAuthProvider implements DirectAuthProvider {
    
    @Override
    public String providerCode() {
        return "enterprise-sso";
    }
    
    @Override
    public PlatformPrincipal authenticate(DirectAuthRequest request) {
        // 1. 调用 SSO 登录 API
        // 2. 获取用户信息
        // 3. 绑定或创建平台账号
        // 4. 返回 PlatformPrincipal
    }
}
```

### 2. IdentityBindingService

**职责**:
- 企业账号与平台账号的绑定关系管理
- 用户信息同步
- 租户信息管理

**核心方法**:

```java
@Service
public class IdentityBindingService {
    
    // 绑定或创建账号
    PlatformPrincipal bindOrCreate(OAuthClaims claims, UserStatus status);
    
    // 查询允许访问的租户列表
    List<String> getAllowedTenants(String userId);
    
    // 更新当前租户
    void updateCurrentTenant(String userId, String tenantNo);
    
    // 同步用户信息
    void syncUserInfo(String userId, EnterpriseSsoUser ssoUser);
}
```

### 3. TokenService

**职责**:
- Token 缓存管理
- Token 刷新逻辑
- Token 验证

**核心方法**:

```java
@Service
public class TokenService {
    
    // 缓存 Token
    void cacheToken(String userId, String tenantNo, TokenPair tokens);
    
    // 获取 Token
    Optional<String> getAccessToken(String userId, String tenantNo);
    
    // 刷新 Token
    TokenPair refreshToken(String userId, String refreshToken);
    
    // 验证 Token
    boolean validateToken(String token);
}
```

### 4. TenantService

**职责**:
- 租户上下文管理
- 租户数据隔离
- 租户权限验证

**核心方法**:

```java
@Service
public class TenantService {
    
    // 设置当前租户上下文
    void setCurrentTenant(String tenantNo);
    
    // 获取当前租户
    String getCurrentTenant();
    
    // 验证租户访问权限
    boolean hasAccessToTenant(String userId, String tenantNo);
    
    // 清除租户上下文
    void clearTenantContext();
}
```

## 性能优化策略

### 1. 缓存策略

- **多级缓存**: 本地缓存 (Caffeine) + Redis
- **缓存预热**: 启动时预加载热点数据
- **缓存更新**: 使用 Redis Pub/Sub 同步多实例

### 2. 数据库优化

- **读写分离**: 查询走从库，写入走主库
- **连接池**: 使用 HikariCP，合理配置连接数
- **索引优化**: tenant_no、user_id 等常用字段建索引

### 3. 异步处理

- **用户信息同步**: 异步更新用户信息
- **审计日志**: 异步写入日志
- **通知发送**: 使用消息队列异步发送

### 4. 限流熔断

- **接口限流**: 使用 Guava RateLimiter 或 Sentinel
- **熔断降级**: 企业 SSO 不可用时启用降级方案
- **超时控制**: 设置合理的 HTTP 超时时间

## 监控指标

### 1. 业务指标

- 登录成功率
- 登录耗时
- Token 刷新成功率
- 租户切换成功率

### 2. 技术指标

- API 响应时间
- 数据库连接数
- Redis 命中率
- JVM 内存使用

### 3. 安全指标

- 登录失败次数
- 异常 IP 访问
- Token 泄露检测
- 权限越权尝试

---

**相关文档**:
- [企业 SSO 登录接入方案设计](./企业SSO登录接入方案设计.md)
- [企业 SSO 首次登录流程](./企业SSO首次登录流程.md)
- [企业 SSO 常规登录流程](./企业SSO常规登录流程.md)
- [企业 SSO 租户切换流程](./企业SSO租户切换流程.md)
