# Local H2 文件持久化与 Redis 剪裁策略 - 产品需求文档 (PRD)

## 需求说明

### 背景
- 项目计划新增 `local-h2` 轻量开发模式，用于不依赖 PostgreSQL 的本地快速启动。
- 当前已有共识：
  - `local-h2` 只用于轻量本地联调，不追求与 PostgreSQL 生产行为完全一致。
  - 技能搜索允许降级为 `LIKE`/`LOWER(...) LIKE ...`。
  - Flyway 允许关闭，改用 Hibernate 自动建表。
- 新增共识：
  - `local-h2` 不能是纯内存数据库，必须使用文件型 H2，保证应用重启后数据不丢失。
  - 需要明确 Redis 在轻量模式下哪些必须保留，哪些可以关闭，哪些可以替换为内存实现。

### 目标
1. 定义 `local-h2` 模式下 H2 的文件持久化要求。
2. 定义 `local-h2` 模式下 Redis 的保留、关闭、替代策略。
3. 保留现有 PostgreSQL 标准开发模式，不破坏、不替换。
4. 让开发者能在 “轻量模式” 与 “真实 PostgreSQL 模式” 间随时切换并对比。

### 非目标
- 不在本 PRD 中要求 `local-h2` 达到 PostgreSQL 生产等价行为。
- 不要求 `local-h2` 完整保留 Redis 的分布式能力。
- 不要求在 `local-h2` 中支持真实 PostgreSQL 全文检索。

## 核心决策

### 决策 1：H2 必须为文件型持久化
- `local-h2` 不允许使用纯内存模式：
  - 禁止 `jdbc:h2:mem:...`
- 必须使用文件型数据库：
  - 推荐 `jdbc:h2:file:...`
- 重启应用后，本地数据库内容必须保留。

### 决策 2：Redis 在 `local-h2` 中不是全量保留
- Redis 不是单纯“展示可选”，但也不是所有用途都必须保留。
- `local-h2` 中 Redis 应按功能拆分：
  - 必须保留
  - 可以关闭
  - 可以用内存替代

### 决策 3：保留双模式
- 保留现有 `local` PostgreSQL 模式。
- 新增 `local-h2` 模式。
- 两种模式都必须可显式切换：
  - `SPRING_PROFILES_ACTIVE=local`
  - `SPRING_PROFILES_ACTIVE=local-h2`

## H2 文件持久化要求

### 功能要求
1. `local-h2` 模式启动时自动使用文件型 H2。
2. 默认数据库文件路径必须可预测、可清理、可配置。
3. 重启进程后，数据必须仍然存在。
4. 提供明确文档说明如何清空 H2 数据文件，便于重置环境。

### 路径策略
建议默认路径使用以下任一方案：
1. 用户目录下：
   - `${user.home}/.skillhub/local-h2/skillhub`
2. 项目本地开发目录：
   - `${projectRoot}/.dev/h2/skillhub`

### 配置要求
- 路径必须允许通过环境变量覆盖，例如：
  - `LOCAL_H2_FILE_PATH`
- 文档中必须明确：
  - 默认路径
  - 自定义路径方式
  - 清理方式

## Redis 使用分析与策略

### A. 可以替代为内存的 Redis 用途

#### 1. Spring Session
当前配置：
- [application.yml](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/resources/application.yml#L45)
- [PlatformSessionService.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/session/PlatformSessionService.java#L67)

结论：
- 在单机本地联调场景中，不需要分布式 session。
- `local-h2` 中应改为默认 servlet 内存 session。

要求：
1. `local-h2` 关闭 `spring.session.store-type=redis`
2. 保留现有 `local` 模式的 Redis session 行为不变

#### 2. API Rate Limit
当前 Redis 限流实现：
- [RedisSlidingWindowRateLimiter.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/RedisSlidingWindowRateLimiter.java#L16)
- [WebMvcRateLimitConfig.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/config/WebMvcRateLimitConfig.java#L22)

现有可复用内存实现：
- [InMemorySlidingWindowRateLimiter.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/InMemorySlidingWindowRateLimiter.java#L11)

结论：
- `local-h2` 中可以将 Redis 限流切换为内存限流。

要求：
1. `local` 模式保持 Redis 限流
2. `local-h2` 模式切换为 InMemory 限流实现

#### 3. Auth Failure Throttle
当前实现：
- [AuthFailureThrottleService.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/security/AuthFailureThrottleService.java#L14)

结论：
- 本地联调不是安全压测环境。
- 可以替换成内存计数，或在 `local-h2` 中关闭。

### B. 可以关闭的 Redis 用途

#### 4. 扫描任务流 / Redisson Stream
相关代码：
- [RedisStreamConfig.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/config/RedisStreamConfig.java#L17)
- [RedissonScanTaskProducer.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/stream/RedissonScanTaskProducer.java#L16)
- [AbstractStreamConsumer.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/stream/AbstractStreamConsumer.java#L28)

结论：
- 安全扫描异步流不是轻量本地联调必需。
- `local-h2` 可直接关闭扫描 stream 相关 bean。

#### 5. Device Auth
相关代码：
- [DeviceAuthService.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceAuthService.java#L20)

结论：
- 如果 `local-h2` 目标是前后端页面和普通 API 联调，CLI device auth 可暂不支持。

要求：
1. `local-h2` 可直接关闭 device auth
2. 文档中明确这是已知裁剪项

#### 6. Idempotency Redis Fast-path
相关代码：
- [IdempotencyInterceptor.java](/Users/robin-mac/ai-code/jk-code/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/filter/IdempotencyInterceptor.java#L23)

结论：
- Redis 在这里是 fast-path，不是唯一真相源。
- `local-h2` 可关闭该 Redis 快路径，直接依赖数据库路径。

### C. 当前不建议作为第一版必须保留的 Redis 用途

本 PRD的第一版轻量模式中，不建议为了保持 Redis 功能完整而引入额外复杂性。
优先级更低的能力：
1. 分布式 session
2. Redis Stream 异步任务恢复
3. CLI Device Auth 状态持久化
4. 与生产完全一致的限流行为

## `local-h2` 第一版外部依赖清单

### 必须保留
1. 无

说明：
- 在 `local-h2` 第一版目标下，不要求依赖 PostgreSQL、MinIO、Redis、Scanner 等外部服务。
- 目标是实现“零必须外部服务”的本地轻量启动模式。

### 可裁剪

#### 1. PostgreSQL
- `local-h2` 使用文件型 H2 代替 PostgreSQL。
- PostgreSQL 只保留在现有 `local` 标准开发模式中。

#### 2. MinIO / S3 兼容对象存储
- `local-h2` 第一版不依赖 MinIO。
- 存储统一回退到本地文件系统。
- `skillhub.storage.provider` 在 `local-h2` 中应固定为 `local`。

#### 3. Scanner / Skill Scanner
- `local-h2` 第一版不要求启动扫描服务。
- 可直接关闭安全扫描相关功能。

#### 4. Redis Stream / Redisson 扫描链路
- 不作为本地轻量模式必需能力。
- 相关 producer / consumer / stream group 可直接禁用。

#### 5. Device Auth
- `local-h2` 第一版不要求支持 CLI Device Auth。
- 可直接关闭对应功能入口或条件 bean。

### 可替代

#### 1. Redis Session → 内存 Session
- 将分布式 session 降级为单机 servlet session。
- 仅保证单机本地联调可用。

#### 2. Redis RateLimiter → 内存限流
- 使用内存限流替代 Redis 滑动窗口限流。
- 只保留基本保护能力，不追求生产一致性。

#### 3. Auth Failure Throttle → 内存计数或关闭
- 对本地账号登录失败计数使用内存实现，或按需弱化。

#### 4. Idempotency Redis Fast-path → 数据库路径 / 简化逻辑
- 去掉 Redis 快路径缓存。
- 保留数据库侧语义，或在本地模式下进一步简化。

### 本地资源要求

#### 1. H2 文件
- 必须使用文件型数据库。
- 禁止使用纯内存模式。
- 重启后数据必须保留。

#### 2. 本地文件存储目录
- 必须保留本地文件存储目录，用于技能包和相关文件读写。
- 推荐与 H2 文件一样落到用户目录或 `.dev` 目录下，便于统一清理。

### 第一版目标形态
1. 数据库：H2 文件型
2. 文件存储：本地目录
3. Session：内存
4. 搜索：LIKE 降级
5. Flyway：关闭
6. Redis：不依赖
7. MinIO：不依赖
8. Scanner：关闭

## 轻量模式能力边界

### `local-h2` 第一版建议保留
1. 登录/本地账号
2. 用户资料
3. 命名空间基础流程
4. 技能基础 CRUD
5. 页面联调
6. 基础关键词搜索

### `local-h2` 第一版建议降级
1. 技能搜索排序和搜索质量
2. 限流语义
3. 会话持久性跨节点能力

### `local-h2` 第一版建议关闭
1. Redis Stream 扫描链路
2. Device Auth
3. Redis Fast-path 幂等增强

## 技术实施方案

### 1. 配置层
- 新增 `application-local-h2.yml`
- H2 使用文件型 JDBC URL
- 禁用 Flyway
- 关闭 Redis Session
- 增加本地 H2 路径配置项

### 2. 条件装配
- 基于 profile 或属性，切换：
  - Session 模式
  - RateLimiter 实现
  - Device Auth 相关 bean
  - Scanner stream 相关 bean

### 3. Redis 替代策略
- Session：Servlet in-memory
- RateLimiter：InMemorySlidingWindowRateLimiter
- Auth failure throttle：内存实现或关闭
- Scanner stream：关闭
- Device auth：关闭

## 验收标准

### H2 文件持久化验收
1. `local-h2` 模式下生成 H2 文件。
2. 停止并重启服务后，之前创建的数据仍存在。
3. 删除 H2 文件后，环境可重建。

### Redis 剪裁验收
1. `local-h2` 启动不再强依赖 Redis。
2. 登录态在单机本地可用。
3. 基础 API 不因 Redis 缺失而报错。
4. 扫描流、Device Auth 等非核心能力明确关闭或替代。
5. 现有 `local` PostgreSQL + Redis 模式不受影响。

## 风险

### 风险 1：有些被认为“可关闭”的 Redis 能力，实际在链路上仍被间接依赖
缓解：
- 通过条件 bean 装配做分层替换
- 先跑基础链路冒烟，再逐项补缺

### 风险 2：内存 session 与现有认证流程存在隐式耦合
缓解：
- 先验证登录、登出、session bootstrap、local auth 主链路

### 风险 3：RateLimit 注解覆盖的接口较多
缓解：
- 明确 `local-h2` 中统一使用内存限流实现，而不是粗暴移除注解

## 执行计划

### Phase 1
- 新增 `local-h2` 文件型 H2 配置
- 关闭 Flyway
- 保持 PostgreSQL 模式不变

### Phase 2
- 替换 Redis session 为内存 session
- 替换 Redis 限流为内存限流

### Phase 3
- 关闭 Scanner Stream / Device Auth / Redis 幂等快路径
- 验证基础登录、CRUD、页面联调

### Phase 4
- 输出 H2 与 PG 模式的差异说明
- 提供切换和清理文档
