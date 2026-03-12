# Phase 4: 运维增强 + 打磨 + 开源就绪 设计文档

> **Goal:** 在 Phase 1-3 完成核心功能的基础上，扩展认证体系（用户名密码登录 + 多账号合并）、完善平台治理（技能隐藏/撤回 + 审计日志查询）、提升可观测性（Prometheus 指标）、优化性能（数据库索引 + 预签名 URL + 前端代码分割）、加固安全、实现 Docker 一键启动和 K8s 基础部署，并建立完整的开源项目基础设施。

> **前置条件:** Phase 1 完成（工程骨架 + 认证授权）+ Phase 2 完成（命名空间 + 技能核心链路）+ Phase 3 完成（审核流程 + CLI API + 评分收藏 + 兼容层）

## 关键设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 密码登录体系 | 独立注册体系，与 OAuth 完全独立 | 降低耦合，两种方式各自独立，通过多账号合并关联 |
| 密码哈希 | BCrypt (strength=12) | 安全性与性能平衡，约 250ms/次 |
| 多账号合并 | 用户主动发起 + 验证 secondary 身份 + 确认 | 安全可控，避免误合并 |
| 版本撤回语义 | YANKED（借鉴 crates.io） | 精确版本仍可下载，兼容已有 lockfile |
| 下载优化 | S3 预签名 URL + 302 重定向 | 减少后端带宽压力，LocalFile 降级代理 |
| Docker 一键启动 | docker Profile + ApplicationRunner 种子数据 | 零配置体验，clone 即用 |
| K8s 部署 | 基础版（Deployment + Service + Ingress） | 满足基本部署需求，不过度设计 |
| 开源许可 | Apache 2.0 | 企业友好，允许商业使用 |
| Chunk 策略 | 4 Chunk 渐进交付 | 每个 Chunk 范围清晰、风险可控 |

## Tech Stack（沿用 Phase 1-3 + 新增）

- 沿用：Spring Boot 3.x + JDK 21 + PostgreSQL 16 + Redis 7 + Spring Security + Spring Data JPA + Flyway
- 沿用前端：React 19 + TypeScript + Vite + TanStack Router + TanStack Query + shadcn/ui + Tailwind CSS
- 新增后端：spring-security-crypto（BCrypt）、Micrometer Prometheus Registry
- 新增前端：rehype-sanitize（XSS 防护）

---

## 1. 数据库迁移（V4__phase4_auth_governance.sql）

Phase 3 已有表：`user_account`, `identity_binding`, `api_token`, `role`, `permission`, `role_permission`, `user_role_binding`, `namespace`, `namespace_member`, `audit_log`, `skill`, `skill_version`, `skill_file`, `skill_tag`, `skill_search_document`, `review_task`, `promotion_request`, `skill_star`, `skill_rating`, `idempotency_record`

### 1.1 新增表

#### local_credential（本地密码凭证）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT NOT NULL FK → user_account | 关联用户 |
| username | VARCHAR(64) NOT NULL UNIQUE | 登录用户名（字母数字下划线，3-64 字符） |
| password_hash | VARCHAR(255) NOT NULL | BCrypt 哈希值 |
| failed_attempts | INT NOT NULL DEFAULT 0 | 连续失败次数 |
| locked_until | TIMESTAMP | 锁定截止时间（NULL 表示未锁定） |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `(username)` UNIQUE — 用户名唯一
- `(user_id)` UNIQUE — 每个用户最多一个本地凭证

#### account_merge_request（账号合并请求）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| primary_user_id | BIGINT NOT NULL FK → user_account | 主账号（保留） |
| secondary_user_id | BIGINT NOT NULL FK → user_account | 副账号（合并后停用） |
| status | VARCHAR(32) NOT NULL DEFAULT 'PENDING' | PENDING / VERIFIED / COMPLETED / CANCELLED |
| verification_token | VARCHAR(255) | 副账号验证令牌（BCrypt 哈希存储） |
| token_expires_at | TIMESTAMP | 令牌过期时间（30 分钟） |
| completed_at | TIMESTAMP | 合并完成时间 |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `(primary_user_id, status)` — 用户的合并请求列表
- `(secondary_user_id) WHERE status = 'PENDING'` — partial unique index，防止重复合并
- `(verification_token) WHERE status = 'PENDING'` — 令牌查找

### 1.2 Phase 3 表结构调整

#### skill 表新增字段

```sql
ALTER TABLE skill ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE skill ADD COLUMN hidden_at TIMESTAMP;
ALTER TABLE skill ADD COLUMN hidden_by BIGINT REFERENCES user_account(id);
CREATE INDEX idx_skill_hidden ON skill(hidden) WHERE hidden = TRUE;
```

#### skill_version 表调整

`skill_version.status` 枚举已包含 YANKED（Phase 2 预留），Phase 4 启用：

```sql
-- YANKED 状态的版本：精确版本号仍可下载，但不出现在版本列表和搜索结果中
-- 借鉴 crates.io 语义：yank 不是删除，是标记"不推荐"
ALTER TABLE skill_version ADD COLUMN yanked_at TIMESTAMP;
ALTER TABLE skill_version ADD COLUMN yanked_by BIGINT REFERENCES user_account(id);
ALTER TABLE skill_version ADD COLUMN yank_reason TEXT;
```

### 1.3 性能优化索引

```sql
-- 搜索性能优化
CREATE INDEX idx_skill_search_doc_rank ON skill_search_document USING gin(search_vector);
CREATE INDEX idx_skill_namespace_status ON skill(namespace_id, hidden) WHERE hidden = FALSE;

-- 审计日志查询优化
CREATE INDEX idx_audit_log_actor_time ON audit_log(actor_id, created_at DESC);
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id, created_at DESC);
CREATE INDEX idx_audit_log_action_time ON audit_log(action, created_at DESC);

-- 下载统计优化
CREATE INDEX idx_skill_version_download ON skill_version(skill_id, status) WHERE status = 'PUBLISHED';
```

---

## 2. 本地认证体系

### 2.1 注册流程

```
用户访问 /register
    │
    ▼
填写表单：username + password + email（可选）
    │
    ▼
前端校验：
  - username: 3-64 字符，字母数字下划线
  - password: 8-128 字符，至少包含 3 种字符类型（大写、小写、数字、特殊字符）
    │
    ▼
POST /api/v1/auth/local/register
    │
    ▼
LocalAuthService.register():
  ① 检查 username 是否已存在（local_credential.username）
  ② 检查 email 是否已被 OAuth 用户占用（user_account.email）
  ③ 密码强度校验（后端二次校验）
  ④ BCrypt 哈希密码（strength=12）
  ⑤ 创建 user_account（status=ACTIVE）
  — 认证方式通过 local_credential 表的存在性隐式判断，不在 user_account 新增字段
  ⑥ 创建 local_credential
  ⑦ 写入 audit_log（action=USER_REGISTERED）
    │
    ▼
自动登录：创建 Spring Session
    │
    ▼
重定向到首页
```

### 2.2 登录流程

```
用户访问 /login
    │
    ▼
填写表单：username + password
    │
    ▼
POST /api/v1/auth/local/login
    │
    ▼
LocalAuthService.login():
  ① 查询 local_credential（by username）
  ② 检查账号状态：
     - user_account.status = DISABLED → 返回 403 "账号已被封禁"
     - locked_until > now → 返回 423 "账号已锁定，请 X 分钟后重试"
  ③ BCrypt 校验密码
     - 成功 → 重置 failed_attempts = 0，清除 locked_until
     - 失败 → failed_attempts++
       - failed_attempts >= 5 → locked_until = now + 15 分钟
       - 返回 401 "用户名或密码错误"
  ④ 写入 audit_log（action=USER_LOGIN）
    │
    ▼
创建 Spring Session
    │
    ▼
返回 200 + 用户信息
```

### 2.3 密码策略

```java
public class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    public static final int MIN_CHAR_TYPES = 3; // 至少 3 种字符类型

    // 字符类型：大写字母、小写字母、数字、特殊字符
    public ValidationResult validate(String password) {
        // 1. 长度检查
        // 2. 字符类型计数
        // 3. 常见弱密码黑名单检查（top 1000）
    }
}
```

### 2.4 登录锁定机制

| 参数 | 值 | 说明 |
|------|------|------|
| 最大失败次数 | 5 | 连续失败 5 次触发锁定 |
| 锁定时长 | 15 分钟 | 锁定期间拒绝登录 |
| 重置条件 | 成功登录 | 成功登录后 failed_attempts 归零 |
| 锁定粒度 | 账号级 | 按 username 锁定，非 IP 级 |

### 2.5 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/local/register` | 用户名密码注册 |
| POST | `/api/v1/auth/local/login` | 用户名密码登录 |
| POST | `/api/v1/auth/local/change-password` | 修改密码（需登录） |

### 2.6 前端页面

- `/register` — 注册页（username + password + 密码强度指示器）
- `/login` 页面扩展 — 增加用户名密码登录表单（与 OAuth 登录按钮并列）
- `/settings/security` — 密码修改（已有本地凭证时显示）

### 2.7 Spring Security 集成

本地登录与 OAuth 登录并存，共享同一套 Session 和 RBAC 体系：

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 现有 OAuth2 登录配置保持不变
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(info -> info.userService(customOAuth2UserService))
            .successHandler(oAuth2SuccessHandler)
            .failureHandler(oAuth2FailureHandler))
        // 新增：本地登录 API 不需要 CSRF 豁免（使用 JSON body，非 form）
        // LocalAuthController 手动处理认证，不使用 Spring Security formLogin
        ;
}
```

本地登录不使用 Spring Security 的 `formLogin`，而是通过自定义 `LocalAuthController` + `LocalAuthService` 手动校验密码并创建 Session。原因：
- 避免与 OAuth2 登录的 `successHandler` 冲突
- JSON API 风格与现有 API 一致
- 更灵活的错误响应控制

---

## 3. 多账号合并

### 3.1 合并流程

```
用户 A（主账号，已登录）发起合并
    │
    ▼
① POST /api/v1/account/merge/initiate
   Body: { "secondaryIdentifier": "username 或 OAuth provider:externalId" }
    │
    ▼
② 后端创建 account_merge_request（status=PENDING）
   生成 verification_token（随机 32 字节 + BCrypt 哈希存储）
   token_expires_at = now + 30 分钟
    │
    ▼
③ 返回验证方式：
   - 副账号是本地账号 → 要求输入副账号密码
   - 副账号是 OAuth 账号 → 要求通过 OAuth 重新登录验证
    │
    ▼
④ POST /api/v1/account/merge/{id}/verify
   - 本地账号：Body: { "password": "xxx" }
   - OAuth 账号：通过 OAuth 回调验证（带 merge_request_id 参数）
    │
    ▼
⑤ 验证通过 → status = VERIFIED
    │
    ▼
⑥ POST /api/v1/account/merge/{id}/confirm
   用户确认合并（显示将要合并的数据摘要）
    │
    ▼
⑦ 执行合并（事务内）：
   a. 迁移 identity_binding：secondary → primary
   b. 迁移 local_credential：secondary → primary（如果 primary 没有）
   c. 迁移 skill ownership：UPDATE skill SET owner_id = primary WHERE owner_id = secondary
   d. 迁移 namespace_member：合并角色取高权限
   e. 迁移 api_token：secondary → primary
   f. 迁移 skill_star、skill_rating：secondary → primary（冲突时保留 primary）
   g. 合并 user_role_binding：取并集
   h. 标记 secondary user_account.status = MERGED
   i. 写入 audit_log（action=ACCOUNT_MERGED，detail 包含完整迁移清单）
    │
    ▼
⑧ status = COMPLETED，返回合并结果摘要
```

### 3.2 合并 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/merge/initiate` | 发起合并请求 |
| POST | `/api/v1/auth/merge/{id}/verify` | 验证副账号身份 |
| POST | `/api/v1/auth/merge/{id}/confirm` | 确认执行合并 |
| POST | `/api/v1/auth/merge/{id}/cancel` | 取消合并请求 |
| GET | `/api/v1/auth/merge/history` | 合并历史记录 |

### 3.3 安全约束

- 合并操作不可逆，确认前展示完整数据迁移摘要
- verification_token 30 分钟过期，过期后需重新发起
- 同一副账号同时只能有一个 PENDING 合并请求（partial unique index）
- MERGED 状态的用户无法登录，无法被再次合并
- 合并操作写入 audit_log，包含完整迁移清单

### 3.4 前端页面

- `/settings/accounts` — 账号管理页
  - 显示当前绑定的所有登录方式（OAuth + 本地）
  - "合并其他账号"按钮 → 合并向导
  - 合并历史记录

---

## 4. 技能治理（隐藏/恢复/版本撤回）

### 4.1 技能隐藏/恢复

隐藏技能后，技能不出现在搜索结果和公开列表中，但已有的直接链接仍可访问（显示"此技能已被管理员隐藏"提示）。

```
POST /api/v1/admin/skills/{id}/hide
  权限：SKILL_ADMIN / SUPER_ADMIN
  效果：
    - skill.hidden = true
    - skill.hidden_at = now
    - skill.hidden_by = current_user_id
    - 搜索索引中移除
    - audit_log 记录

POST /api/v1/admin/skills/{id}/unhide
  权限：SKILL_ADMIN / SUPER_ADMIN
  效果：
    - skill.hidden = false
    - 搜索索引中恢复
    - audit_log 记录
```

### 4.2 版本撤回（YANK）

借鉴 crates.io 语义：YANKED 版本不出现在版本列表和搜索中，但通过精确版本号仍可下载（兼容已有 lockfile）。

```
POST /api/v1/admin/skills/{id}/yank/{versionId}
  权限：SKILL_ADMIN / SUPER_ADMIN
  Body: { "reason": "安全漏洞 CVE-2026-xxxx" }
  效果：
    - skill_version.status = YANKED
    - skill_version.yanked_at = now
    - skill_version.yanked_by = current_user_id
    - skill_version.yank_reason = reason
    - 如果是 latest_version_id → 回退到上一个 PUBLISHED 版本
    - 搜索索引更新
    - audit_log 记录

POST /api/v1/admin/skills/{id}/unyank/{versionId}
  权限：SKILL_ADMIN / SUPER_ADMIN
  效果：
    - skill_version.status = PUBLISHED
    - 清除 yanked_at/yanked_by/yank_reason
    - audit_log 记录
```

### 4.3 下载行为

| 请求方式 | YANKED 版本行为 |
|----------|----------------|
| `GET /download`（不带版本号） | 返回最新非 YANKED 的 PUBLISHED 版本 |
| `GET /versions/{version}/download`（精确版本） | 正常下载，响应头附加 `X-Skillhub-Yanked: true` + `X-Skillhub-Yank-Reason: ...` |
| `GET /resolve`（不带版本号） | 返回最新非 YANKED 的 PUBLISHED 版本 |
| `GET /resolve?version=1.0.0`（精确版本） | 正常解析，响应中标记 `yanked: true` |

---

## 5. 审计日志查询

### 5.1 查询 API

```
GET /api/v1/admin/audit-logs
  权限：AUDITOR / SUPER_ADMIN
  Query Params:
    - actor_id: BIGINT — 操作人
    - action: STRING — 操作类型（USER_LOGIN, SKILL_PUBLISHED, REVIEW_APPROVED, ...）
    - target_type: STRING — 目标类型（USER, SKILL, SKILL_VERSION, NAMESPACE, ...）
    - target_id: BIGINT — 目标 ID
    - from: TIMESTAMP — 起始时间
    - to: TIMESTAMP — 截止时间
    - page: INT — 页码
    - size: INT — 每页条数（默认 20，最大 100）
```

响应：
```json
{
  "code": 0,
  "msg": "获取成功",
  "data": {
    "items": [
      {
        "id": 1,
        "actorId": 42,
        "actorName": "zhangsan",
        "action": "SKILL_PUBLISHED",
        "targetType": "SKILL_VERSION",
        "targetId": 123,
        "detail": "{\"namespace\":\"ai-team\",\"slug\":\"my-skill\",\"version\":\"1.0.0\"}",
        "ipAddress": "192.168.1.1",
        "createdAt": "2026-03-12T06:00:00Z"
      }
    ],
    "total": 100,
    "page": 1,
    "size": 20
  },
  "timestamp": "2026-03-12T06:00:00Z",
  "requestId": "req-123"
}
```

### 5.2 前端审计日志页

- 路由：`/admin/audit-logs`
- 权限：AUDITOR / SUPER_ADMIN
- 功能：
  - 时间范围筛选（日期选择器）
  - 操作类型下拉筛选
  - 操作人搜索（模糊匹配）
  - 目标类型 + 目标 ID 筛选
  - 分页浏览
  - 详情展开（JSON 格式化显示）

---

## 6. 可观测性（Prometheus 指标）

### 6.1 Spring Boot Actuator + Micrometer

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    tags:
      application: skillhub
    export:
      prometheus:
        enabled: true
```

### 6.2 自定义业务指标

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `skillhub_skill_publish_total` | Counter | namespace, status | 技能发布计数 |
| `skillhub_skill_download_total` | Counter | namespace, slug | 技能下载计数 |
| `skillhub_review_total` | Counter | action(approve/reject), namespace | 审核操作计数 |
| `skillhub_auth_login_total` | Counter | method(oauth/local), result(success/fail) | 登录计数 |
| `skillhub_auth_lockout_total` | Counter | — | 账号锁定计数 |
| `skillhub_search_duration_seconds` | Histogram | — | 搜索耗时分布 |
| `skillhub_active_sessions` | Gauge | — | 活跃 Session 数 |

### 6.3 实现方式

```java
@Component
@RequiredArgsConstructor
public class SkillhubMetrics {
    private final MeterRegistry registry;

    public void recordPublish(String namespace, String status) {
        registry.counter("skillhub_skill_publish_total",
            "namespace", namespace, "status", status).increment();
    }

    public void recordDownload(String namespace, String slug) {
        registry.counter("skillhub_skill_download_total",
            "namespace", namespace, "slug", slug).increment();
    }

    public void recordLogin(String method, String result) {
        registry.counter("skillhub_auth_login_total",
            "method", method, "result", result).increment();
    }
}
```

端点：`GET /actuator/prometheus` — Prometheus 拉取指标（仅内网可访问，通过 Spring Security 配置限制）

---

## 7. 性能优化

### 7.1 数据库查询优化

除 1.3 节的索引外，还需：

- 慢查询日志：PostgreSQL `log_min_duration_statement = 500`（记录 >500ms 的查询）
- 连接池调优：HikariCP `maximum-pool-size` 根据部署环境调整（开发 5，生产 20）
- 搜索查询优化：确保 `skill_search_document` 的 GIN 索引被正确使用

### 7.2 对象存储优化（S3 预签名 URL）

```java
@Service
public class StorageService {
    // 现有方法：直接代理下载
    public StreamingResponseBody download(String key) { ... }

    // 新增：预签名 URL 下载（S3 实现）
    public String generatePresignedUrl(String key, Duration expiry) {
        // S3 实现：生成预签名 URL（默认 10 分钟有效）
        // LocalFile 实现：返回 null（降级为代理下载）
    }
}
```

下载 Controller 改造：

```java
@GetMapping("/download")
public ResponseEntity<?> download(...) {
    String presignedUrl = storageService.generatePresignedUrl(key, Duration.ofMinutes(10));
    if (presignedUrl != null) {
        // S3 模式：302 重定向到预签名 URL
        return ResponseEntity.status(302)
            .header("Location", presignedUrl)
            .build();
    }
    // LocalFile 模式：直接代理下载（保持现有行为）
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(storageService.download(key));
}
```

### 7.3 前端性能优化

#### 代码分割（TanStack Router lazy routes）

```typescript
// 改造前：所有页面同步导入
import { SkillDetailPage } from './pages/skill-detail'

// 改造后：按路由懒加载
const SkillDetailPage = lazy(() => import('./pages/skill-detail'))

// TanStack Router 配置
const skillDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/skills/$namespace/$slug',
  component: SkillDetailPage,
})
```

#### 分割策略

| 路由组 | 分割方式 | 说明 |
|--------|----------|------|
| 公开页面（首页、搜索、详情） | 主 bundle | 首屏必需 |
| 认证页面（登录、注册） | lazy | 非首屏 |
| Dashboard 页面 | lazy | 需登录 |
| Admin 页面 | lazy | 仅管理员 |
| 审核中心 | lazy | 仅审核人 |

#### 其他优化

- 图片懒加载：用户头像、技能图标使用 `loading="lazy"`
- API 响应缓存：TanStack Query `staleTime` 合理设置（搜索 30s，详情 60s，用户信息 300s）

---

## 8. 安全加固

### 8.1 Session 安全

```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true      # 防止 JS 读取 Session Cookie
        secure: true          # 仅 HTTPS 传输（生产环境）
        same-site: lax        # 防止 CSRF（允许顶级导航）
        max-age: 28800        # 8 小时
```

### 8.2 XSS 防护

SKILL.md 内容渲染使用 `rehype-sanitize` 过滤危险 HTML：

```typescript
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import ReactMarkdown from 'react-markdown'

<ReactMarkdown
  remarkPlugins={[remarkGfm]}
  rehypePlugins={[rehypeSanitize]}
>
  {skillMdContent}
</ReactMarkdown>
```

### 8.3 安全响应头

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.headers(headers -> headers
        .contentTypeOptions(Customizer.withDefaults())     // X-Content-Type-Options: nosniff
        .frameOptions(frame -> frame.deny())                // X-Frame-Options: DENY
        .httpStrictTransportSecurity(hsts -> hsts           // HSTS
            .includeSubDomains(true)
            .maxAgeInSeconds(31536000))
        .referrerPolicy(referrer -> referrer
            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    );
}
```

### 8.4 密码安全

- BCrypt strength=12（约 250ms/次，防暴力破解）
- 密码不在日志中输出（LoggingFilter 排除 password 字段）
- 密码修改后使该用户所有其他 Session 失效
- API 响应中永远不返回 password_hash

---

## 9. Docker 一键启动

### 9.1 docker-compose.yml（开发环境）

现有 `docker-compose.yml` 已包含 PostgreSQL、Redis、MinIO。Phase 4 扩展为完整的一键启动方案：

```yaml
# docker-compose.yml — 开发环境完整启动
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: skillhub
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

  backend:
    build:
      context: .
      dockerfile: Dockerfile
      target: backend
    depends_on:
      - postgres
      - redis
      - minio
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/skillhub
      SPRING_DATASOURCE_USERNAME: skillhub
      SPRING_DATASOURCE_PASSWORD: skillhub
      SPRING_DATA_REDIS_HOST: redis
      SKILLHUB_STORAGE_TYPE: s3
      SKILLHUB_STORAGE_S3_ENDPOINT: http://minio:9000
      SKILLHUB_STORAGE_S3_ACCESS_KEY: minioadmin
      SKILLHUB_STORAGE_S3_SECRET_KEY: minioadmin
    ports:
      - "8080:8080"

  frontend:
    build:
      context: .
      dockerfile: Dockerfile
      target: frontend
    depends_on:
      - backend
    ports:
      - "3000:80"

volumes:
  postgres_data:
  minio_data:
```

### 9.2 Dockerfile（多阶段构建）

```dockerfile
# === Backend Build ===
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /app
COPY pom.xml .
COPY skillhub-*/pom.xml ./
# Maven 依赖缓存层
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS backend
WORKDIR /app
COPY --from=backend-build /app/skillhub-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# === Frontend Build ===
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ .
RUN npm run build

FROM nginx:alpine AS frontend
COPY --from=frontend-build /app/dist /usr/share/nginx/html
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 9.3 种子数据（ApplicationRunner）

```java
@Component
@Profile("docker")
@RequiredArgsConstructor
public class SeedDataRunner implements ApplicationRunner {
    private final UserAccountRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final LocalCredentialRepository localCredentialRepo;
    private final UserRoleBindingRepository userRoleBindingRepo;
    private final NamespaceRepository namespaceRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepo.count() > 0) return; // 幂等：已有数据则跳过

        // 创建管理员账号（密码已 BCrypt 哈希）
        var admin = new UserAccount();
        admin.setDisplayName("Admin");
        admin.setEmail("admin@skillhub.dev");
        admin.setStatus(UserStatus.ACTIVE);
        userRepo.save(admin);

        // 创建本地凭证
        var credential = new LocalCredential();
        credential.setUserId(admin.getId());
        credential.setUsername("admin");
        credential.setPasswordHash(passwordEncoder.encode("Admin@2026"));
        localCredentialRepo.save(credential);

        // 分配 SUPER_ADMIN 角色
        var superAdminRole = roleRepo.findByName("SUPER_ADMIN").orElseThrow();
        var binding = new UserRoleBinding();
        binding.setUserId(admin.getId());
        binding.setRoleId(superAdminRole.getId());
        userRoleBindingRepo.save(binding);

        // 创建全局命名空间
        var globalNs = new Namespace();
        globalNs.setSlug("global");
        globalNs.setDisplayName("Global");
        globalNs.setDescription("全局公共命名空间");
        namespaceRepo.save(globalNs);
    }
}
```

### 9.4 Nginx 配置（前端 SPA）

```nginx
# deploy/nginx/default.conf
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA 路由：所有非文件请求回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth 回调代理
    location /oauth2/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /login/oauth2/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # ClawHub CLI 兼容层服务发现
    location /.well-known/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Actuator 端点（仅内网）
    location /actuator/ {
        proxy_pass http://backend:8080;
        allow 10.0.0.0/8;
        allow 172.16.0.0/12;
        allow 192.168.0.0/16;
        deny all;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

---

## 10. K8s 基础部署

### 10.1 Deployment（后端）

```yaml
# deploy/k8s/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: skillhub-backend
  labels:
    app: skillhub
    component: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: skillhub
      component: backend
  template:
    metadata:
      labels:
        app: skillhub
        component: backend
    spec:
      containers:
        - name: backend
          image: skillhub/backend:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "production"
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                secretKeyRef:
                  name: skillhub-db
                  key: url
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: skillhub-db
                  key: username
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: skillhub-db
                  key: password
            - name: SPRING_DATA_REDIS_HOST
              valueFrom:
                configMapKeyRef:
                  name: skillhub-config
                  key: redis-host
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: 1000m
              memory: 1Gi
```

### 10.2 Deployment（前端）

```yaml
# deploy/k8s/frontend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: skillhub-frontend
  labels:
    app: skillhub
    component: frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: skillhub
      component: frontend
  template:
    metadata:
      labels:
        app: skillhub
        component: frontend
    spec:
      containers:
        - name: frontend
          image: skillhub/frontend:latest
          ports:
            - containerPort: 80
          resources:
            requests:
              cpu: 100m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
```

### 10.3 Service

```yaml
# deploy/k8s/services.yaml
apiVersion: v1
kind: Service
metadata:
  name: skillhub-backend
spec:
  selector:
    app: skillhub
    component: backend
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
---
apiVersion: v1
kind: Service
metadata:
  name: skillhub-frontend
spec:
  selector:
    app: skillhub
    component: frontend
  ports:
    - port: 80
      targetPort: 80
  type: ClusterIP
```

### 10.4 Ingress

```yaml
# deploy/k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: skillhub-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/limit-rps: "30"
spec:
  ingressClassName: nginx
  rules:
    - host: skillhub.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: skillhub-backend
                port:
                  number: 8080
          - path: /oauth2
            pathType: Prefix
            backend:
              service:
                name: skillhub-backend
                port:
                  number: 8080
          - path: /login/oauth2
            pathType: Prefix
            backend:
              service:
                name: skillhub-backend
                port:
                  number: 8080
          - path: /actuator/prometheus
            pathType: Exact
            backend:
              service:
                name: skillhub-backend
                port:
                  number: 8080
          - path: /.well-known
            pathType: Prefix
            backend:
              service:
                name: skillhub-backend
                port:
                  number: 8080
          - path: /
            pathType: Prefix
            backend:
              service:
                name: skillhub-frontend
                port:
                  number: 80
  tls:
    - hosts:
        - skillhub.example.com
      secretName: skillhub-tls
```

### 10.5 ConfigMap & Secret 模板

```yaml
# deploy/k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: skillhub-config
data:
  redis-host: "redis-service"
  storage-type: "s3"
  s3-endpoint: "http://minio-service:9000"
---
# deploy/k8s/secret.yaml.example（不提交到 Git）
apiVersion: v1
kind: Secret
metadata:
  name: skillhub-db
type: Opaque
stringData:
  url: "jdbc:postgresql://postgres-service:5432/skillhub"
  username: "skillhub"
  password: "CHANGE_ME"
```

---

## 11. 开源项目基础设施

### 11.1 README.md

```markdown
# skillhub

企业级技能注册中心，支持技能发布、版本管理、审核流程和 CLI 工具链。

## 功能特性

- 技能发布与版本管理（语义化版本、标签、草稿/审核/发布流程）
- 命名空间隔离（团队空间 + 全局空间）
- 多种认证方式（GitHub OAuth + 用户名密码）
- 完整的 RBAC 权限体系
- CLI 工具支持（OAuth Device Flow 认证）
- ClawHub CLI 兼容层
- 全文搜索（PostgreSQL Full-Text Search）
- 评分与收藏
- 审计日志
- Prometheus 可观测性指标

## 快速开始

### 前置条件

- Docker & Docker Compose
- Git

### 一键启动

\```bash
git clone https://github.com/your-org/skillhub.git
cd skillhub
docker compose up -d
\```

服务启动后访问：
- 前端：http://localhost:3000
- 后端 API：http://localhost:8080
- MinIO 控制台：http://localhost:9001（minioadmin / minioadmin）

默认管理员账号：`admin` / `Admin@2026`

### 本地开发

\```bash
# 启动基础设施
docker compose up -d postgres redis minio

# 后端（需要 JDK 21 + Maven）
cd skillhub-app
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 前端（需要 Node.js 20+）
cd web
npm install
npm run dev
\```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.x, JDK 21, Spring Security, Spring Data JPA |
| 前端 | React 19, TypeScript, Vite, TanStack Router/Query, shadcn/ui |
| 数据库 | PostgreSQL 16, Redis 7 |
| 对象存储 | MinIO (S3 兼容) |
| 部署 | Docker Compose, Kubernetes |

## 项目结构

\```
skillhub/
├── skillhub-app/          # Spring Boot 启动模块
├── skillhub-core/         # 领域模型 + 服务
├── skillhub-infra/        # 基础设施（存储、缓存）
├── skillhub-security/     # 认证授权
├── skillhub-api/          # REST Controller
├── skillhub-common/       # 公共工具
├── web/                   # React 前端
├── deploy/                # 部署配置（Nginx、K8s）
└── docs/                  # 设计文档
\```

## 文档

- [产品方向](docs/00-product-direction.md)
- [系统架构](docs/01-system-architecture.md)
- [领域模型](docs/02-domain-model.md)
- [认证设计](docs/03-authentication-design.md)
- [API 设计](docs/06-api-design.md)
- [交付路线](docs/10-delivery-roadmap.md)

## 贡献

请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解贡献流程。

## 许可证

[Apache License 2.0](LICENSE)
```

### 11.2 CONTRIBUTING.md

```markdown
# 贡献指南

感谢你对 skillhub 的关注！以下是参与贡献的流程。

## 开发环境

1. Fork 并 clone 仓库
2. 安装依赖：JDK 21、Maven 3.9+、Node.js 20+、Docker
3. 启动基础设施：`docker compose up -d postgres redis minio`
4. 后端：`mvn spring-boot:run -Dspring-boot.run.profiles=local`
5. 前端：`cd web && npm install && npm run dev`

## 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

- `feat(scope): 描述` — 新功能
- `fix(scope): 描述` — Bug 修复
- `docs(scope): 描述` — 文档更新
- `refactor(scope): 描述` — 重构
- `test(scope): 描述` — 测试
- `chore(scope): 描述` — 构建/工具

scope 示例：`auth`, `skill`, `web`, `deploy`, `api`

## Pull Request 流程

1. 从 `main` 创建 feature 分支：`git checkout -b feat/your-feature`
2. 编写代码和测试
3. 确保所有测试通过：`mvn verify` + `cd web && npm test`
4. 提交 PR，填写模板中的信息
5. 等待 Code Review

## 代码规范

- 后端：遵循项目现有的 Spring Boot 代码风格
- 前端：ESLint + Prettier 自动格式化
- 所有 API 变更需同步更新 OpenAPI spec
- 新功能需附带单元测试

## Issue 反馈

- Bug 报告请使用 Bug Report 模板
- 功能建议请使用 Feature Request 模板
- 提问请使用 Discussions
```

### 11.3 GitHub Issue / PR 模板

#### `.github/ISSUE_TEMPLATE/bug_report.md`

```markdown
---
name: Bug Report
about: 报告一个 Bug
title: '[Bug] '
labels: bug
---

**描述**
简要描述 Bug 现象。

**复现步骤**
1. ...
2. ...
3. ...

**期望行为**
描述你期望的正确行为。

**实际行为**
描述实际发生的错误行为。

**环境信息**
- OS:
- Browser:
- skillhub 版本:
- 部署方式（Docker / K8s / 本地开发）:

**截图/日志**
如有相关截图或错误日志，请附上。
```

#### `.github/ISSUE_TEMPLATE/feature_request.md`

```markdown
---
name: Feature Request
about: 提出功能建议
title: '[Feature] '
labels: enhancement
---

**需求描述**
简要描述你希望实现的功能。

**使用场景**
描述这个功能的使用场景和动机。

**期望方案**
描述你期望的实现方式。

**备选方案**
是否有其他替代方案？

**补充信息**
其他相关信息。
```

#### `.github/pull_request_template.md`

```markdown
## 变更说明

简要描述本次变更的内容和目的。

## 变更类型

- [ ] 新功能（feat）
- [ ] Bug 修复（fix）
- [ ] 重构（refactor）
- [ ] 文档（docs）
- [ ] 测试（test）
- [ ] 其他

## 关联 Issue

Closes #

## 检查清单

- [ ] 代码已自测通过
- [ ] 单元测试已添加/更新
- [ ] API 变更已更新 OpenAPI spec
- [ ] 文档已更新（如适用）
- [ ] 无安全风险引入
```

### 11.4 其他开源文件

#### LICENSE

Apache License 2.0 标准文本。

#### CODE_OF_CONDUCT.md

采用 [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/)。

#### .github/FUNDING.yml（可选）

```yaml
github: [your-username]
```

#### .gitignore 补充

```gitignore
# Phase 4 新增
deploy/k8s/secret.yaml
*.env.local
```

---

## 12. Chunk 划分与验收标准

### Chunk 1：本地认证 + 多账号合并

**范围：** 用户名密码注册/登录 + 密码策略 + 账号锁定 + 多账号合并流程

**后端任务：**
1. Flyway 迁移：`local_credential` 表、`account_merge_request` 表
2. `LocalAuthService`：注册、登录、密码修改、密码策略校验
3. `LocalAuthController`：`/api/v1/auth/local/*` 端点
4. `AccountMergeService`：发起合并、验证、确认、取消
5. `AccountMergeController`：`/api/v1/auth/merge/*` 端点
6. Spring Security 配置扩展：本地认证 + OAuth 并存
7. 种子数据 `SeedDataRunner`（docker profile）

**前端任务：**
1. 注册页 `/register`
2. 登录页扩展（用户名密码 Tab + OAuth Tab）
3. 密码修改页 `/settings/security`
4. 账号合并页 `/settings/accounts`

**验收标准：**
1. 用户可通过用户名密码注册，密码强度校验生效
2. 用户可通过用户名密码登录，登录失败 5 次后锁定 15 分钟
3. 用户可修改密码，修改后其他 Session 失效
4. 用户可发起账号合并，验证副账号身份后完成合并
5. 合并后副账号状态为 MERGED，无法登录
6. 合并后数据（技能、收藏、角色等）正确迁移到主账号
7. Docker 启动后种子数据正确初始化（admin 账号可登录）
8. 所有测试通过

### Chunk 2：技能治理 + 审计日志查询 + 可观测性

**范围：** 技能隐藏/恢复 + 版本撤回 + 审计日志查询 API 和前端 + Prometheus 指标

**后端任务：**
1. Flyway 迁移：skill 表新增 hidden 字段、skill_version 表新增 yank 字段、性能索引
2. `SkillGovernanceService`：隐藏/恢复/撤回
3. `AdminSkillController`：`/api/v1/admin/skills/{id}/hide|unhide|yank`
4. `AuditLogQueryService`：多条件查询
5. `AuditLogController`：`GET /api/v1/admin/audit-logs`
6. `SkillhubMetrics`：Prometheus 自定义指标
7. Actuator + Micrometer 配置

**前端任务：**
1. 审计日志查询页 `/admin/audit-logs`
2. 技能详情页增加隐藏/恢复操作（管理员可见）
3. 版本列表增加撤回操作（管理员可见）

**验收标准：**
1. SKILL_ADMIN 可隐藏技能，隐藏后搜索不可见，直接链接显示隐藏提示
2. SKILL_ADMIN 可恢复隐藏的技能
3. SKILL_ADMIN 可撤回已发布版本，YANKED 版本精确版本号仍可下载
4. 撤回 latest 版本后，latest_version_id 回退到上一个 PUBLISHED 版本
5. AUDITOR 可查询审计日志，支持多条件筛选和分页
6. `/actuator/prometheus` 返回自定义业务指标
7. 所有测试通过

### Chunk 3：性能优化 + 安全加固

**范围：** 数据库索引优化 + S3 预签名 URL + 前端代码分割 + 安全响应头 + Session 安全

**后端任务：**
1. `StorageService` 扩展：`generatePresignedUrl()` 方法
2. 下载 Controller 改造：S3 模式 302 重定向
3. Session Cookie 安全配置
4. 安全响应头配置（SecurityFilterChain）
5. 密码安全措施（日志排除、Session 失效）

**前端任务：**
1. TanStack Router lazy routes 改造
2. `rehype-sanitize` 集成
3. 图片懒加载
4. TanStack Query staleTime 调优

**验收标准：**
1. S3 模式下载返回 302 + 预签名 URL
2. LocalFile 模式下载保持直接代理（向后兼容）
3. 前端 bundle 分析：Admin/Dashboard/Auth 页面独立 chunk
4. 安全响应头正确设置（X-Content-Type-Options、X-Frame-Options、HSTS）
5. Session Cookie 设置 HttpOnly + SameSite
6. SKILL.md 渲染经过 XSS 过滤
7. 所有测试通过

### Chunk 4：Docker 一键启动 + K8s 部署 + 开源基础设施

**范围：** Dockerfile + docker-compose + K8s 清单 + README + CONTRIBUTING + GitHub 模板

**任务：**
1. 多阶段 Dockerfile（backend + frontend）
2. docker-compose.yml 完善（5 个服务 + 健康检查）
3. Nginx 配置（SPA 路由 + API 代理）
4. K8s 清单（Deployment + Service + Ingress + ConfigMap）
5. README.md（快速开始 + 技术栈 + 项目结构）
6. CONTRIBUTING.md（开发环境 + 提交规范 + PR 流程）
7. GitHub Issue/PR 模板
8. LICENSE（Apache 2.0）
9. CODE_OF_CONDUCT.md

**验收标准：**
1. `git clone && docker compose up -d` 后所有服务正常启动
2. 前端可访问，后端 API 可调用
3. 默认管理员账号可登录
4. K8s 清单可通过 `kubectl apply -f deploy/k8s/` 部署
5. README 包含完整的快速开始指南
6. GitHub 模板文件齐全
7. 所有测试通过

---

## 13. 测试策略

### 13.1 后端测试

| 层级 | 范围 | 工具 | 覆盖重点 |
|------|------|------|----------|
| 单元测试 | 领域服务 | JUnit 5 + Mockito | LocalAuthService（密码策略、锁定逻辑）、AccountMergeService（状态机、数据迁移）、SkillGovernanceService（隐藏/撤回逻辑） |
| 集成测试 | Repository + DB | @DataJpaTest + Testcontainers | local_credential 唯一约束、account_merge_request partial unique index、性能索引验证 |
| API 测试 | Controller | @WebMvcTest + MockMvc | 本地认证端点、合并端点、治理端点、审计日志查询 |
| 端到端测试 | 全链路 | @SpringBootTest + Testcontainers | 注册 → 登录 → 合并 → 验证数据迁移 |

### 13.2 关键测试用例

**本地认证：**
- 注册成功 → user_account + local_credential 创建，自动登录
- 注册失败 → 用户名已存在、密码强度不足、邮箱已占用
- 登录成功 → Session 创建，failed_attempts 重置
- 登录失败 → failed_attempts 递增，5 次后锁定
- 锁定期间登录 → 返回 423
- 密码修改 → 旧密码校验、新密码哈希、其他 Session 失效

**多账号合并：**
- 发起合并 → 创建 PENDING 请求，生成验证令牌
- 验证副账号 → 本地密码验证 / OAuth 重新授权
- 确认合并 → 数据迁移事务（技能、收藏、角色、Token）
- 合并冲突 → 收藏去重、评分保留最新、角色取并集
- 副账号已 MERGED → 无法登录

**技能治理：**
- 隐藏技能 → 搜索不可见，直接链接可访问
- 恢复技能 → 搜索重新可见
- 撤回版本 → YANKED 状态，精确版本可下载
- 撤回 latest → latest_version_id 回退

### 13.3 前端测试

| 类型 | 工具 | 覆盖重点 |
|------|------|----------|
| 组件测试 | Vitest + React Testing Library | 注册表单校验、登录表单、合并向导、审计日志筛选器 |
| Hook 测试 | renderHook | useLocalAuth、useAccountMerge、useAuditLogs |
| 页面测试 | Vitest + MSW | 注册/登录交互、合并流程、审计日志查询 |

---

## 14. 风险与应对

| 风险 | 应对 |
|------|------|
| 本地认证与 OAuth Session 冲突 | 两种认证方式共享 Spring Session，通过 `local_credential` 表存在性判断认证来源，无需额外字段 |
| 多账号合并数据不一致 | 合并操作在单个事务中执行，失败自动回滚；合并前生成数据摘要供用户确认 |
| BCrypt 性能影响 | strength=12 约 250ms/次，登录接口限流 30 次/分钟/IP 防暴力破解 |
| Docker 构建缓存失效 | 多阶段构建 + 依赖层分离，Maven/npm 依赖变更才触发重新下载 |
| K8s 配置与实际环境差异 | 提供 ConfigMap/Secret 模板，文档说明必须修改的配置项 |
| 前端代码分割导致首屏闪烁 | 公开页面保留在主 bundle，仅非首屏页面 lazy load |
| YANKED 版本语义理解偏差 | 文档明确说明 YANKED ≠ 删除，精确版本仍可下载 |

---

## 15. 总结

Phase 4 在 Phase 1-3 的基础上，完成运维增强、安全加固和开源就绪：

**核心价值：**
1. **本地认证** — 独立的用户名密码登录体系，降低 OAuth 依赖
2. **多账号合并** — 安全的账号关联机制，支持用户统一身份
3. **技能治理** — 隐藏/恢复/撤回能力，完善平台治理闭环
4. **可观测性** — Prometheus 指标暴露，支持生产环境监控
5. **性能优化** — 数据库索引、预签名 URL、前端代码分割
6. **安全加固** — Session 安全、XSS 防护、安全响应头
7. **一键启动** — Docker Compose 零配置体验，clone 即用
8. **开源就绪** — 完整的开源项目基础设施

**交付策略：**
- 4 个 Chunk 渐进式交付
- Chunk 1（认证）→ Chunk 2（治理）→ Chunk 3（性能安全）→ Chunk 4（部署开源）
- 每个 Chunk 独立可验收，风险可控