# Phase 3: 审核流程 + CLI API + 评分收藏 + 兼容层 设计文档

> **Goal:** 在 Phase 2 命名空间和技能核心链路基础上，建立完整的治理体系、CLI 生态和社交功能。实现审核流程、团队技能提升、评分收藏、CLI API、ClawHub 兼容层、幂等去重和管理后台。

> **前置条件:** Phase 1 完成（工程骨架 + 认证授权）+ Phase 2 完成（命名空间 + 技能核心链路）

## 关键设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 审核模式 | 严格审核（所有版本都需审核） | 企业内部平台治理需求，确保技能质量 |
| 审核权限 | 严格分级（团队自治，平台不越权） | 保持团队自治性，平台管理员只管全局空间和提升审核 |
| 审核并发控制 | 乐观锁（version 字段） + partial unique index | 防止多 Pod 并发审核同一任务 |
| CLI 认证 | OAuth Device Flow（Web 授权） | 现代 CLI 标准做法，用户体验最佳 |
| 兼容层范围 | 核心操作（search、resolve、download、publish、whoami） | 平衡兼容性和实现复杂度 |
| 评分收藏 | 仅登录用户可用 | 确保数据可信度，避免刷分/刷收藏 |
| 幂等去重 | Redis SETNX + PostgreSQL idempotency_record | 双层防护，Redis 快速去重，PostgreSQL 持久化兜底 |
| 实施策略 | 审核优先 + 渐进式 CLI（5 个 Chunk） | 渐进式交付，风险可控 |

## Tech Stack（沿用 Phase 1/2 + 新增）

- 沿用：Spring Boot 3.x + JDK 21 + PostgreSQL 16 + Redis 7 + Spring Security + Spring Data JPA + Flyway
- 沿用前端：React 19 + TypeScript + Vite + TanStack Router + TanStack Query + shadcn/ui + Tailwind CSS
- 新增前端：react-rating-stars-component（评分组件）

---

## 1. 数据库迁移（V3__phase3_review_social_tables.sql）

Phase 2 已有表：`user_account`, `identity_binding`, `api_token`, `role`, `permission`, `role_permission`, `user_role_binding`, `namespace`, `namespace_member`, `audit_log`, `skill`, `skill_version`, `skill_file`, `skill_tag`, `skill_search_document`

### 1.1 新增表

#### review_task（发布审核任务）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| skill_version_id | BIGINT NOT NULL FK → skill_version | 关联的版本 |
| namespace_id | BIGINT NOT NULL FK → namespace | 所属空间（决定谁能审核） |
| status | VARCHAR(32) NOT NULL DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| version | INT NOT NULL DEFAULT 1 | 乐观锁版本号 |
| submitted_by | BIGINT NOT NULL FK → user_account | 提交人 |
| reviewed_by | BIGINT FK → user_account | 审核人 |
| review_comment | TEXT | 审核意见 |
| submitted_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| reviewed_at | TIMESTAMP | |

索引：
- `(namespace_id, status)` — 审核列表
- `(submitted_by, status)` — 我的提交
- `(skill_version_id) WHERE status = 'PENDING'` — partial unique index，防止重复提交

业务约束：
- 同一 `skill_version_id` 在 `status=PENDING` 时只能存在一条记录
- 撤回时物理删除 review_task 记录，依赖 partial unique index 防并发

#### promotion_request（提升审核任务）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| source_skill_id | BIGINT NOT NULL FK → skill | 来源团队 skill |
| source_version_id | BIGINT NOT NULL FK → skill_version | 申请提升的版本 |
| target_namespace_id | BIGINT NOT NULL FK → namespace | 目标全局 namespace |
| target_skill_id | BIGINT FK → skill | 审批通过后生成的全局 skill ID |
| status | VARCHAR(32) NOT NULL DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| version | INT NOT NULL DEFAULT 1 | 乐观锁版本号 |
| submitted_by | BIGINT NOT NULL FK → user_account | 提交人 |
| reviewed_by | BIGINT FK → user_account | 审核人 |
| review_comment | TEXT | 审核意见 |
| submitted_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| reviewed_at | TIMESTAMP | |

索引：
- `(source_skill_id)` — 按来源 skill 查询
- `(status)` — 待审核列表
- `(source_version_id) WHERE status = 'PENDING'` — partial unique index，防止重复提交

业务约束：
- 同一 `source_version_id` 在 `status=PENDING` 时只能存在一条记录
- 审批通过后填充 `target_skill_id`

#### skill_star（技能收藏）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| skill_id | BIGINT NOT NULL FK → skill | |
| user_id | BIGINT NOT NULL FK → user_account | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `(skill_id, user_id)` UNIQUE — 唯一约束
- `(user_id)` — 我的收藏
- `(skill_id)` — 技能收藏数

#### skill_rating（技能评分）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| skill_id | BIGINT NOT NULL FK → skill | |
| user_id | BIGINT NOT NULL FK → user_account | |
| score | SMALLINT NOT NULL CHECK (score >= 1 AND score <= 5) | 1-5 分 |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `(skill_id, user_id)` UNIQUE — 唯一约束，每人每技能一条
- `(skill_id)` — 评分聚合

#### idempotency_record（幂等记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| request_id | VARCHAR(64) PK | 客户端传入的 UUID v4 |
| resource_type | VARCHAR(64) NOT NULL | 如 skill_version, api_token |
| resource_id | BIGINT | 业务操作产生的资源 ID |
| status | VARCHAR(32) NOT NULL | PROCESSING / COMPLETED / FAILED |
| response_status_code | INT | 原始响应状态码 |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| expires_at | TIMESTAMP NOT NULL | 过期时间（默认 24h） |

索引：
- `(expires_at)` — 过期清理
- `(status, created_at)` — 僵死任务检测

### 1.2 Phase 2 表结构调整

Phase 2 设计中 `skill_version.status` 直接到 PUBLISHED，Phase 3 需要调整为审核流程：

**skill_version 表无需修改** - status 枚举已包含 DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED / YANKED

**Phase 2 → Phase 3 迁移策略：**
- Phase 2 发布流程：上传 → DRAFT → 自动提交 → PENDING_REVIEW → 自动通过 → PUBLISHED
- Phase 3 发布流程：上传 → DRAFT → 提交审核 → PENDING_REVIEW → 人工审核 → PUBLISHED/REJECTED

Phase 2 代码中的自动审核逻辑在 Phase 3 移除，改为创建 review_task 等待人工审核。

**Phase 2 已有数据的处理：**

1. **已发布的技能（status=PUBLISHED）**
   - 保持现状，无需补充审核
   - 视为已通过审核的技能
   - 后续新版本发布需要经过审核流程

2. **草稿状态的技能（status=DRAFT）**
   - 保持 DRAFT 状态
   - 用户需要手动提交审核才能发布

3. **待审核状态的技能（status=PENDING_REVIEW）**
   - Phase 2 中不应该存在此状态（因为自动通过）
   - 如果存在，需要补充创建 review_task 记录
   - 迁移脚本：
     ```sql
     -- 为 Phase 2 遗留的 PENDING_REVIEW 版本创建审核任务
     INSERT INTO review_task (skill_version_id, namespace_id, status, submitted_by, submitted_at)
     SELECT
       sv.id,
       s.namespace_id,
       'PENDING',
       sv.created_by,
       sv.created_at
     FROM skill_version sv
     JOIN skill s ON sv.skill_id = s.id
     WHERE sv.status = 'PENDING_REVIEW'
       AND NOT EXISTS (
         SELECT 1 FROM review_task rt WHERE rt.skill_version_id = sv.id
       );
     ```

4. **数据一致性检查**
   - 检查所有 PUBLISHED 版本是否有对应的 skill.latest_version_id
   - 检查所有 PENDING_REVIEW 版本是否有对应的 review_task
   - 迁移脚本在 Phase 3 部署前执行

---

## 2. 审核流程设计

### 2.1 普通发布审核流程

```
用户发布技能（Phase 2 已实现）
    │
    ▼
① skill_version 创建（status=DRAFT）
    │
    ▼
② 用户提交审核（POST /api/v1/skills/{namespace}/{slug}/versions/{version}/submit）
    │
    ▼
③ 创建 review_task
   - skill_version.status → PENDING_REVIEW
   - INSERT INTO review_task (skill_version_id, namespace_id, status=PENDING, submitted_by)
   - 检查 partial unique index 防止重复提交
    │
    ▼
④ 审核人审核（PUT /api/v1/review-tasks/{id}/approve 或 /reject）
   ├── 通过 →
   │   ① 乐观锁更新：UPDATE review_task SET status='APPROVED', reviewed_by=?, reviewed_at=?, version=version+1 WHERE id=? AND version=?
   │   ② skill_version.status → PUBLISHED
   │   ③ 更新 skill.latest_version_id（如果是最新版本）
   │   ④ 发布 SkillPublishedEvent（触发搜索索引更新）
   │   ⑤ 同步写入 audit_log
   │
   └── 拒绝 →
       ① 乐观锁更新：UPDATE review_task SET status='REJECTED', reviewed_by=?, reviewed_at=?, review_comment=?, version=version+1 WHERE id=? AND version=?
       ② skill_version.status → REJECTED
       ③ 记录 reject_reason
       ④ 同步写入 audit_log
```

**撤回审核：**
```
用户撤回审核（DELETE /api/v1/skills/{namespace}/{slug}/versions/{version}/review）
    │
    ▼
① 检查 review_task.status = PENDING（只能撤回待审核的）
    │
    ▼
② 物理删除 review_task 记录
    │
    ▼
③ skill_version.status → DRAFT
```

### 2.2 提升审核流程

```
团队空间技能（已发布，status=PUBLISHED）
    │
    ▼
① 技能 owner 或 namespace ADMIN 发起提升申请
   POST /api/v1/skills/{namespace}/{slug}/promote
   Body: { "targetNamespaceSlug": "global", "versionId": 123 }
    │
    ▼
② 创建 promotion_request
   - INSERT INTO promotion_request (source_skill_id, source_version_id, target_namespace_id, status=PENDING, submitted_by)
   - 检查 partial unique index 防止重复提交
   - 检查 source_version.status = PUBLISHED（只能提升已发布版本）
   - 检查 target_namespace.type = GLOBAL（只能提升到全局空间）
    │
    ▼
③ 平台管理员审核（PUT /api/v1/promotion-requests/{id}/approve 或 /reject）
   ├── 通过 →
   │   ① 乐观锁更新：UPDATE promotion_request SET status='APPROVED', reviewed_by=?, reviewed_at=?, version=version+1 WHERE id=? AND version=?
   │   ② 在全局空间创建新 skill
   │      - namespace_id = target_namespace_id
   │      - slug = 原 skill.slug（如果冲突则拒绝）
   │      - source_skill_id = 原 skill.id
   │      - owner_id = 原 skill.owner_id
   │      - visibility = PUBLIC
   │   ③ 复制 source_version_id 对应版本的文件和元数据到新 skill
   │      - 创建新 skill_version（status=PUBLISHED）
   │      - 复制 skill_file 记录（对象存储文件复用，只复制元数据）
   │      - 更新新 skill.latest_version_id
   │   ④ 更新 promotion_request.target_skill_id = 新 skill.id
   │   ⑤ 发布 SkillPromotedEvent（触发搜索索引写入新 skill）
   │   ⑥ 同步写入 audit_log
   │
   └── 拒绝 →
       ① 乐观锁更新：UPDATE promotion_request SET status='REJECTED', reviewed_by=?, reviewed_at=?, review_comment=?, version=version+1 WHERE id=? AND version=?
       ② 同步写入 audit_log
```

**提升后的版本管理：**
- 全局空间的新 skill 由其 owner 独立管理版本
- 原团队 skill 可继续独立迭代
- 两者版本不自动同步，如需同步由 owner 手动操作

### 2.3 审核权限判定

#### ReviewPermissionChecker（`domain.review.ReviewPermissionChecker`）

```java
public class ReviewPermissionChecker {

    /**
     * 检查用户是否有权审核指定的 review_task
     */
    public boolean canReview(ReviewTask task, Long userId,
                             Map<Long, NamespaceRole> userNamespaceRoles,
                             Set<String> platformRoles) {
        // 不能审核自己提交的
        if (task.getSubmittedBy().equals(userId)) {
            return false;
        }

        // 全局空间：只有平台 SKILL_ADMIN 或 SUPER_ADMIN 可以审核
        if (task.getNamespace().getType() == NamespaceType.GLOBAL) {
            return platformRoles.contains("SKILL_ADMIN")
                || platformRoles.contains("SUPER_ADMIN");
        }

        // 团队空间：该 namespace 的 ADMIN 或 OWNER 可以审核
        NamespaceRole role = userNamespaceRoles.get(task.getNamespaceId());
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    /**
     * 检查用户是否有权审核提升请求
     */
    public boolean canReviewPromotion(PromotionRequest request, Long userId,
                                      Set<String> platformRoles) {
        // 只有平台 SKILL_ADMIN 或 SUPER_ADMIN 可以审核提升请求
        return platformRoles.contains("SKILL_ADMIN")
            || platformRoles.contains("SUPER_ADMIN");
    }
}
```

#### 权限矩阵

| 操作 | 团队空间 | 全局空间 | 提升请求 |
|------|---------|---------|---------|
| 提交审核 | namespace MEMBER+ | 平台 SKILL_ADMIN+ | namespace ADMIN+ |
| 审核通过/拒绝 | namespace ADMIN+ | 平台 SKILL_ADMIN+ | 平台 SKILL_ADMIN+ |
| 撤回审核 | 提交人本人 | 提交人本人 | 提交人本人 |

### 2.4 乐观锁并发控制

**问题：** 多个审核人同时审核同一任务，可能导致重复审核或状态不一致。

**解决方案：** 使用乐观锁（version 字段）+ 数据库 UPDATE 影响行数判定。

```java
@Service
public class ReviewService {

    @Transactional
    public void approveReview(Long reviewTaskId, Long reviewerId, String comment) {
        // 1. 加载 review_task（带 version）
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
            .orElseThrow(() -> new NotFoundException("Review task not found"));

        // 2. 检查状态（只能审核 PENDING 状态）
        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new BusinessException("Review task is not pending");
        }

        // 3. 检查权限
        if (!reviewPermissionChecker.canReview(task, reviewerId, ...)) {
            throw new ForbiddenException("No permission to review");
        }

        // 4. 乐观锁更新
        int updated = reviewTaskRepository.updateStatusWithVersion(
            reviewTaskId,
            ReviewTaskStatus.APPROVED,
            reviewerId,
            comment,
            task.getVersion()  // WHERE version = ?
        );

        // 5. 检查更新结果
        if (updated == 0) {
            throw new ConcurrentModificationException("Review task was modified by another user");
        }

        // 6. 更新 skill_version.status
        skillVersionRepository.updateStatus(task.getSkillVersionId(), SkillVersionStatus.PUBLISHED);

        // 7. 更新 skill.latest_version_id
        // ...

        // 8. 发布事件
        eventPublisher.publishEvent(new SkillPublishedEvent(...));

        // 9. 写入审计日志
        auditLogService.log(...);
    }
}
```

**Repository 实现：**

```java
@Repository
public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {

    @Modifying
    @Query("""
        UPDATE ReviewTask t
        SET t.status = :status,
            t.reviewedBy = :reviewerId,
            t.reviewComment = :comment,
            t.reviewedAt = CURRENT_TIMESTAMP,
            t.version = t.version + 1
        WHERE t.id = :id AND t.version = :expectedVersion
    """)
    int updateStatusWithVersion(
        @Param("id") Long id,
        @Param("status") ReviewTaskStatus status,
        @Param("reviewerId") Long reviewerId,
        @Param("comment") String comment,
        @Param("expectedVersion") Integer expectedVersion
    );
}
```

**并发场景：**
- 审核人 A 和 B 同时审核任务 T（version=1）
- A 先提交：UPDATE ... WHERE id=T AND version=1 → 成功，version 变为 2
- B 后提交：UPDATE ... WHERE id=T AND version=1 → 失败（version 已变为 2），返回 409 Conflict

---

## 3. 评分收藏设计

### 3.1 收藏功能

#### SkillStarService（`domain.skill.service.SkillStarService`）

```java
@Service
public class SkillStarService {

    /**
     * 收藏技能
     */
    @Transactional
    public void starSkill(Long skillId, Long userId) {
        // 1. 检查技能存在性和可见性
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new NotFoundException("Skill not found"));

        if (!visibilityChecker.canAccess(skill, userId, ...)) {
            throw new ForbiddenException("No permission to access this skill");
        }

        // 2. 插入 skill_star（唯一约束自动去重）
        try {
            SkillStar star = new SkillStar(skillId, userId);
            skillStarRepository.save(star);
        } catch (DataIntegrityViolationException e) {
            // 已收藏，幂等返回成功
            return;
        }

        // 3. 异步更新计数器
        eventPublisher.publishEvent(new SkillStarredEvent(skillId, true));
    }

    /**
     * 取消收藏
     */
    @Transactional
    public void unstarSkill(Long skillId, Long userId) {
        int deleted = skillStarRepository.deleteBySkillIdAndUserId(skillId, userId);

        if (deleted > 0) {
            // 异步更新计数器
            eventPublisher.publishEvent(new SkillStarredEvent(skillId, false));
        }
    }

    /**
     * 检查是否已收藏
     */
    public boolean isStarred(Long skillId, Long userId) {
        return skillStarRepository.existsBySkillIdAndUserId(skillId, userId);
    }

    /**
     * 获取用户的收藏列表
     */
    public Page<Skill> getStarredSkills(Long userId, Pageable pageable) {
        return skillStarRepository.findStarredSkillsByUserId(userId, pageable);
    }
}
```

#### 计数器更新（异步事件）

```java
@Component
public class SkillStarEventListener {

    @EventListener
    @Async("skillhubEventExecutor")
    public void onSkillStarred(SkillStarredEvent event) {
        if (event.isStarred()) {
            // 原子递增
            skillRepository.incrementStarCount(event.skillId());
        } else {
            // 原子递减
            skillRepository.decrementStarCount(event.skillId());
        }
    }
}
```

**Repository 实现：**

```java
@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    @Modifying
    @Query("UPDATE Skill s SET s.starCount = s.starCount + 1 WHERE s.id = :id")
    void incrementStarCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Skill s SET s.starCount = s.starCount - 1 WHERE s.id = :id AND s.starCount > 0")
    void decrementStarCount(@Param("id") Long id);
}
```

### 3.2 评分功能

#### SkillRatingService（`domain.skill.service.SkillRatingService`）

```java
@Service
public class SkillRatingService {

    /**
     * 提交评分（新增或更新）
     */
    @Transactional
    public void rateSkill(Long skillId, Long userId, int score) {
        // 1. 校验评分范围
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }

        // 2. 检查技能存在性和可见性
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new NotFoundException("Skill not found"));

        if (!visibilityChecker.canAccess(skill, userId, ...)) {
            throw new ForbiddenException("No permission to access this skill");
        }

        // 3. 插入或更新评分
        SkillRating rating = skillRatingRepository
            .findBySkillIdAndUserId(skillId, userId)
            .orElse(new SkillRating(skillId, userId));

        rating.setScore(score);
        rating.setUpdatedAt(Instant.now());
        skillRatingRepository.save(rating);

        // 4. 异步重算平均分
        eventPublisher.publishEvent(new SkillRatedEvent(skillId));
    }

    /**
     * 获取用户对技能的评分
     */
    public Optional<Integer> getUserRating(Long skillId, Long userId) {
        return skillRatingRepository.findBySkillIdAndUserId(skillId, userId)
            .map(SkillRating::getScore);
    }
}
```

#### 评分重算（异步事件 + Redis 分布式锁）

```java
@Component
public class SkillRatingEventListener {

    @EventListener
    @Async("skillhubEventExecutor")
    public void onSkillRated(SkillRatedEvent event) {
        String lockKey = "rating:recalc:" + event.skillId();

        // 获取 Redis 分布式锁（TTL 10s）
        boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

        if (!locked) {
            // 已有其他线程在重算，跳过
            return;
        }

        try {
            // 重新计算平均分和评分人数
            RatingStats stats = skillRatingRepository.calculateStats(event.skillId());

            // 更新 skill 表
            skillRepository.updateRatingStats(
                event.skillId(),
                stats.avgScore(),
                stats.count()
            );
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }
}
```

**Repository 实现：**

```java
@Repository
public interface SkillRatingRepository extends JpaRepository<SkillRating, Long> {

    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, Long userId);

    @Query("""
        SELECT new com.iflytek.skillhub.domain.skill.RatingStats(
            COALESCE(AVG(r.score), 0.0),
            COUNT(r)
        )
        FROM SkillRating r
        WHERE r.skillId = :skillId
    """)
    RatingStats calculateStats(@Param("skillId") Long skillId);
}

public record RatingStats(Double avgScore, Long count) {}
```

**容错机制：**
- 如果 Redis 不可用，跳过分布式锁，直接重算（可能重复计算，但结果最终一致）
- 定时任务每天凌晨从 `skill_rating` 表重算所有技能的评分，修正异步事件丢失导致的不一致

---

## 4. CLI API 设计

### 4.1 OAuth Device Flow 认证

**标准流程（RFC 8628）：**

```
CLI 用户运行 skillhub login
    │
    ▼
① CLI 请求 device code
   POST /api/v1/cli/auth/device/code
   Response: {
     "device_code": "xxx",
     "user_code": "ABCD-1234",
     "verification_uri": "https://skills.example.com/device",
     "expires_in": 900,
     "interval": 5
   }
    │
    ▼
② CLI 显示提示信息
   "Please visit https://skills.example.com/device and enter code: ABCD-1234"
   CLI 自动打开浏览器（可选）
    │
    ▼
③ 用户在浏览器中访问 verification_uri
   输入 user_code
   登录（如果未登录）
   确认授权
    │
    ▼
④ CLI 轮询 token 端点
   POST /api/v1/cli/auth/device/token
   Body: { "device_code": "xxx" }

   - 授权前：返回 { "error": "authorization_pending" }
   - 授权后：返回 { "access_token": "sk_xxx", "token_type": "Bearer", "expires_in": null }
    │
    ▼
⑤ CLI 保存 token 到本地配置文件
   ~/.skillhub/config.json: { "token": "sk_xxx" }
```

#### 后端实现

**DeviceAuthService（`skillhub-auth` 模块）**

```java
@Service
public class DeviceAuthService {

    /**
     * 生成 device code 和 user code
     */
    public DeviceCodeResponse generateDeviceCode() {
        String deviceCode = generateSecureToken(32);  // 长随机字符串
        String userCode = generateUserFriendlyCode();  // ABCD-1234 格式

        // 存储到 Redis（TTL 15 分钟）
        DeviceCodeData data = new DeviceCodeData(
            deviceCode,
            userCode,
            DeviceCodeStatus.PENDING,
            null  // userId，授权后填充
        );
        redisTemplate.opsForValue().set(
            "device:code:" + deviceCode,
            data,
            Duration.ofMinutes(15)
        );

        return new DeviceCodeResponse(
            deviceCode,
            userCode,
            "https://skills.example.com/device",
            900,  // expires_in
            5     // interval
        );
    }

    /**
     * 用户授权 device code
     */
    public void authorizeDeviceCode(String userCode, Long userId) {
        // 1. 通过 user_code 查找 device_code
        String deviceCode = findDeviceCodeByUserCode(userCode);
        if (deviceCode == null) {
            throw new NotFoundException("Invalid user code");
        }

        // 2. 更新状态为 AUTHORIZED，填充 userId
        DeviceCodeData data = getDeviceCodeData(deviceCode);
        data.setStatus(DeviceCodeStatus.AUTHORIZED);
        data.setUserId(userId);
        redisTemplate.opsForValue().set(
            "device:code:" + deviceCode,
            data,
            Duration.ofMinutes(15)
        );
    }

    /**
     * CLI 轮询获取 token
     */
    public DeviceTokenResponse pollToken(String deviceCode) {
        DeviceCodeData data = getDeviceCodeData(deviceCode);

        if (data == null) {
            throw new NotFoundException("Invalid or expired device code");
        }

        return switch (data.getStatus()) {
            case PENDING -> DeviceTokenResponse.pending();
            case AUTHORIZED -> {
                // 生成 API Token
                ApiToken token = apiTokenService.createToken(
                    data.getUserId(),
                    "CLI Device Auth",
                    null  // 永不过期
                );

                // 标记为已使用，防止重复获取
                data.setStatus(DeviceCodeStatus.USED);
                redisTemplate.opsForValue().set(
                    "device:code:" + deviceCode,
                    data,
                    Duration.ofMinutes(1)  // 短 TTL，快速清理
                );

                yield DeviceTokenResponse.success(token.getTokenString());
            }
            case USED -> throw new BusinessException("Device code already used");
        };
    }

    private String generateUserFriendlyCode() {
        // 生成 ABCD-1234 格式的 8 字符码
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // 去除易混淆字符
        Random random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) code.append('-');
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
}
```

**Controller 层**

```java
@RestController
@RequestMapping("/api/v1/cli/auth/device")
public class DeviceAuthController {

    @PostMapping("/code")
    public DeviceCodeResponse requestDeviceCode() {
        return deviceAuthService.generateDeviceCode();
    }

    @PostMapping("/token")
    public DeviceTokenResponse pollToken(@RequestBody DeviceTokenRequest request) {
        return deviceAuthService.pollToken(request.deviceCode());
    }
}

@RestController
@RequestMapping("/api/v1/device")
public class DeviceAuthWebController {

    @PostMapping("/authorize")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> authorizeDevice(
            @RequestBody AuthorizeDeviceRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        deviceAuthService.authorizeDeviceCode(request.userCode(), principal.getUserId());
        return ResponseEntity.ok().build();
    }
}

public record AuthorizeDeviceRequest(String userCode) {}
```

**说明：**
- 前端是 React SPA，后端只提供 REST API 端点，不需要 Thymeleaf 模板
- `/device` 路由由前端 React Router 处理
- 后端只提供 `/api/v1/device/authorize` API 端点用于授权操作

### 4.2 CLI API 端点

#### whoami - 查询当前用户信息

```
GET /api/v1/cli/whoami
Authorization: Bearer sk_xxx

Response 200:
{
  "code": 0,
  "data": {
    "userId": 123,
    "displayName": "张三",
    "email": "zhangsan@example.com",
    "namespaces": [
      {
        "slug": "global",
        "displayName": "Global",
        "role": null
      },
      {
        "slug": "team-ai",
        "displayName": "AI Team",
        "role": "ADMIN"
      }
    ]
  }
}
```

#### publish - 发布技能

```
POST /api/v1/cli/publish
Authorization: Bearer sk_xxx
Content-Type: multipart/form-data
X-Request-Id: uuid-v4（可选，用于幂等）

Parts:
  - file: zip 包（必需）
  - namespace: 目标命名空间 slug（必需）
  - visibility: PUBLIC / NAMESPACE_ONLY / PRIVATE（可选，默认 PUBLIC）
  - auto_submit: boolean（可选，默认 true，自动提交审核）

Response 200:
{
  "code": 0,
  "data": {
    "skillId": 456,
    "skillVersionId": 123,
    "namespace": "team-ai",
    "slug": "my-skill",
    "version": "1.2.0",
    "status": "PENDING_REVIEW",  // auto_submit=true 时
    "fileCount": 5,
    "totalSize": 12345
  }
}
```

**与 Phase 2 的差异：**
- Phase 2：上传 → DRAFT → 自动 PUBLISHED
- Phase 3：上传 → DRAFT → 提交审核 → PENDING_REVIEW → 人工审核 → PUBLISHED

#### resolve - 解析技能版本

```
GET /api/v1/cli/resolve?skill=@team-ai/my-skill&version=1.2.0
Authorization: Bearer sk_xxx（可选，匿名可访问 PUBLIC 技能）

Query Parameters:
  - skill: 技能坐标（@namespace/slug）
  - version: 版本号 / 标签名 / "latest"（可选，默认 latest）

Response 200:
{
  "code": 0,
  "data": {
    "skillId": 456,
    "namespace": "team-ai",
    "slug": "my-skill",
    "displayName": "My Skill",
    "version": "1.2.0",
    "versionId": 123,
    "status": "PUBLISHED",
    "downloadUrl": "/api/v1/skills/team-ai/my-skill/versions/1.2.0/download",
    "fileCount": 5,
    "totalSize": 12345,
    "publishedAt": "2026-03-12T10:00:00Z"
  }
}
```

#### check - 检查技能包有效性

```
POST /api/v1/cli/check
Authorization: Bearer sk_xxx
Content-Type: multipart/form-data

Parts:
  - file: zip 包（必需）

Response 200:
{
  "code": 0,
  "data": {
    "valid": true,
    "metadata": {
      "name": "my-skill",
      "version": "1.2.0",
      "description": "..."
    },
    "fileCount": 5,
    "totalSize": 12345,
    "errors": []
  }
}

Response 200（校验失败）:
{
  "code": 0,
  "data": {
    "valid": false,
    "errors": [
      "SKILL.md not found",
      "Invalid version format: 1.2"
    ]
  }
}
```

---

## 5. ClawHub 兼容层设计

### 5.1 Canonical Slug 映射规则

根据 `00-product-direction.md` 1.1 节的冻结决策：

| skillhub 坐标 | ClawHub canonical slug | 说明 |
|--------------|----------------------|------|
| `@global/my-skill` | `my-skill` | 全局空间省略前缀 |
| `@team-ai/my-skill` | `team-ai--my-skill` | 团队空间使用双连字符 |

**映射规则：**
- 分隔符为双连字符 `--`
- skill slug 和 namespace slug 均禁止包含 `--`（在校验规则中已强制）
- 兼容层解析 canonical slug 时：
  - 包含 `--` → 拆分为 `namespace_slug` + `skill_slug`
  - 不包含 `--` → 视为 `@global/{slug}`

**CanonicalSlugMapper（`skillhub-app` 模块）**

```java
@Component
public class CanonicalSlugMapper {

    /**
     * skillhub 坐标 → canonical slug
     */
    public String toCanonical(String namespaceSlug, String skillSlug) {
        if ("global".equals(namespaceSlug)) {
            return skillSlug;
        }
        return namespaceSlug + "--" + skillSlug;
    }

    /**
     * canonical slug → skillhub 坐标
     */
    public SkillCoordinate fromCanonical(String canonicalSlug) {
        int separatorIndex = canonicalSlug.indexOf("--");

        if (separatorIndex == -1) {
            // 无 --，视为全局空间
            return new SkillCoordinate("global", canonicalSlug);
        }

        // 有 --，拆分为 namespace + skill
        String namespaceSlug = canonicalSlug.substring(0, separatorIndex);
        String skillSlug = canonicalSlug.substring(separatorIndex + 2);
        return new SkillCoordinate(namespaceSlug, skillSlug);
    }
}

public record SkillCoordinate(String namespaceSlug, String skillSlug) {}
```

### 5.2 兼容层端点

#### /.well-known/clawhub.json - 服务发现

```
GET /.well-known/clawhub.json

Response 200:
{
  "apiBase": "/api/compat/v1"
}
```

#### search - 搜索技能

```
GET /api/compat/v1/search?q=keyword&page=0&size=20
Authorization: Bearer sk_xxx（可选）

Response 200:
{
  "items": [
    {
      "slug": "my-skill",  // canonical slug
      "name": "My Skill",
      "description": "...",
      "version": "1.2.0",
      "downloads": 1234,
      "stars": 56
    },
    {
      "slug": "team-ai--another-skill",
      "name": "Another Skill",
      "description": "...",
      "version": "2.0.0",
      "downloads": 567,
      "stars": 23
    }
  ],
  "total": 42,
  "page": 0,
  "size": 20
}
```

**实现：** 调用 skillhub 搜索 API，将结果转换为 canonical slug 格式。

#### resolve - 解析技能版本

```
GET /api/compat/v1/resolve?slug=my-skill&version=1.2.0
Authorization: Bearer sk_xxx（可选）

Response 200:
{
  "slug": "my-skill",
  "name": "My Skill",
  "version": "1.2.0",
  "downloadUrl": "/api/compat/v1/download/my-skill/1.2.0",
  "fileCount": 5,
  "totalSize": 12345
}
```

**实现：**
1. 解析 canonical slug → skillhub 坐标
2. 调用 skillhub resolve API
3. 转换响应格式

#### download - 下载技能包

```
GET /api/compat/v1/download/{slug}/{version}
Authorization: Bearer sk_xxx（可选）

Response 200:
Content-Type: application/zip
Content-Disposition: attachment; filename="my-skill-1.2.0.zip"

<binary data>
```

**实现：**
1. 解析 canonical slug → skillhub 坐标
2. 调用 skillhub download API
3. 透传 zip 文件

#### publish - 发布技能

```
POST /api/compat/v1/publish
Authorization: Bearer sk_xxx
Content-Type: multipart/form-data

Parts:
  - file: zip 包（必需）
  - namespace: 目标命名空间 slug（可选，默认 global）

Response 200:
{
  "slug": "my-skill",
  "version": "1.2.0",
  "status": "pending_review"
}
```

**实现：** 调用 skillhub publish API，转换响应格式。

#### whoami - 查询当前用户

```
GET /api/compat/v1/whoami
Authorization: Bearer sk_xxx

Response 200:
{
  "userId": 123,
  "username": "zhangsan",
  "email": "zhangsan@example.com"
}
```

**实现：** 调用 skillhub whoami API，转换响应格式。

### 5.3 兼容层 Controller 实现

```java
@RestController
@RequestMapping("/api/compat/v1")
public class ClawHubCompatController {

    private final CanonicalSlugMapper slugMapper;
    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;
    private final SkillPublishService skillPublishService;
    private final SkillSearchAppService skillSearchAppService;

    @GetMapping("/search")
    public ClawHubSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal) {

        // 调用 skillhub 搜索
        SearchResultDTO result = skillSearchAppService.searchSkills(
            q, null, "relevance", page, size, principal
        );

        // 转换为 ClawHub 格式
        List<ClawHubSkillItem> items = result.items().stream()
            .map(item -> new ClawHubSkillItem(
                slugMapper.toCanonical(item.namespace(), item.slug()),
                item.displayName(),
                item.summary(),
                item.latestVersion(),
                item.downloadCount(),
                item.starCount()
            ))
            .toList();

        return new ClawHubSearchResponse(items, result.total(), page, size);
    }

    @GetMapping("/resolve")
    public ClawHubResolveResponse resolve(
            @RequestParam String slug,
            @RequestParam(defaultValue = "latest") String version,
            @AuthenticationPrincipal PlatformPrincipal principal) {

        // 解析 canonical slug
        SkillCoordinate coord = slugMapper.fromCanonical(slug);

        // 调用 skillhub resolve
        SkillVersionDetailDTO detail = skillQueryService.getVersionDetail(
            coord.namespaceSlug(),
            coord.skillSlug(),
            version,
            principal
        );

        // 转换为 ClawHub 格式
        return new ClawHubResolveResponse(
            slug,
            detail.displayName(),
            detail.version(),
            "/api/compat/v1/download/" + slug + "/" + detail.version(),
            detail.fileCount(),
            detail.totalSize()
        );
    }

    @GetMapping("/download/{slug}/{version}")
    public ResponseEntity<Resource> download(
            @PathVariable String slug,
            @PathVariable String version,
            @AuthenticationPrincipal PlatformPrincipal principal) {

        // 解析 canonical slug
        SkillCoordinate coord = slugMapper.fromCanonical(slug);

        // 调用 skillhub download
        DownloadResult result = skillDownloadService.downloadVersion(
            coord.namespaceSlug(),
            coord.skillSlug(),
            version,
            principal
        );

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + result.filename() + "\"")
            .contentLength(result.contentLength())
            .body(new InputStreamResource(result.content()));
    }

    @PostMapping("/publish")
    public ClawHubPublishResponse publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "global") String namespace,
            @AuthenticationPrincipal PlatformPrincipal principal) {

        // 调用 skillhub publish
        SkillVersion version = skillPublishService.publishSkill(
            namespace,
            file.getInputStream(),
            principal.getUserId(),
            SkillVisibility.PUBLIC
        );

        // 转换为 ClawHub 格式
        String canonicalSlug = slugMapper.toCanonical(namespace, version.getSkill().getSlug());
        return new ClawHubPublishResponse(
            canonicalSlug,
            version.getVersion(),
            version.getStatus().name().toLowerCase()
        );
    }

    @GetMapping("/whoami")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        UserAccount user = userAccountRepository.findById(principal.getUserId())
            .orElseThrow();

        return new ClawHubWhoamiResponse(
            user.getId(),
            user.getDisplayName(),
            user.getEmail()
        );
    }
}
```

---

## 6. 幂等去重设计

### 6.1 双层防护架构

**Redis 层（快速去重）：**
- Key: `idempotent:{requestId}`
- Value: "1"
- TTL: 24 小时
- 作用：快速拦截重复请求，避免数据库查询

**PostgreSQL 层（持久化兜底）：**
- 表：`idempotency_record`
- 作用：持久化幂等记录，Redis 失效后仍能去重

### 6.2 幂等拦截器

```java
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String IDEMPOTENCY_ATTR = "idempotency.requestId";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 只拦截写操作
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return true;
        }

        // 获取 Request-Id
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            // 客户端未传 Request-Id，不做幂等处理
            return true;
        }

        // 校验 UUID 格式
        if (!isValidUUID(requestId)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().write("{\"error\":\"Invalid X-Request-Id format\"}");
            return false;
        }

        // 检查 Redis 快速去重
        String redisKey = "idempotent:" + requestId;
        Boolean exists = redisTemplate.opsForValue().setIfAbsent(
            redisKey,
            "1",
            Duration.ofHours(24)
        );

        if (Boolean.FALSE.equals(exists)) {
            // Redis 中已存在，查询 PostgreSQL 获取原始结果
            IdempotencyRecord record = idempotencyRecordRepository
                .findById(requestId)
                .orElse(null);

            if (record == null) {
                // Redis 有但 PostgreSQL 无，可能是脏数据，删除 Redis key 允许重试
                redisTemplate.delete(redisKey);
                return true;
            }

            return switch (record.getStatus()) {
                case COMPLETED -> {
                    // 返回原始结果
                    response.setStatus(record.getResponseStatusCode());
                    response.setContentType("application/json");
                    response.getWriter().write(buildIdempotentResponse(record));
                    yield false;  // 拦截请求
                }
                case PROCESSING -> {
                    // 正在处理中，返回 409 Conflict
                    response.setStatus(HttpStatus.CONFLICT.value());
                    response.getWriter().write("{\"error\":\"Request is being processed\"}");
                    yield false;
                }
                case FAILED -> {
                    // 失败状态，允许重试
                    redisTemplate.delete(redisKey);
                    yield true;
                }
            };
        }

        // Redis SETNX 成功，插入 PostgreSQL PROCESSING 记录
        IdempotencyRecord record = new IdempotencyRecord(
            requestId,
            null,  // resourceType，业务层填充
            null,  // resourceId，业务层填充
            IdempotencyStatus.PROCESSING,
            null,
            Instant.now(),
            Instant.now().plus(24, ChronoUnit.HOURS)
        );
        idempotencyRecordRepository.save(record);

        // 将 requestId 存入 request attribute，供业务层使用
        request.setAttribute(IDEMPOTENCY_ATTR, requestId);

        return true;
    }

    private String buildIdempotentResponse(IdempotencyRecord record) {
        // 根据 resourceType 和 resourceId 构建响应
        return String.format(
            "{\"code\":0,\"data\":{\"resourceType\":\"%s\",\"resourceId\":%d}}",
            record.getResourceType(),
            record.getResourceId()
        );
    }
}
```

### 6.3 业务层使用

```java
@Service
public class SkillPublishService {

    @Transactional
    public SkillVersion publishSkill(..., HttpServletRequest request) {
        // 业务逻辑
        SkillVersion version = doPublish(...);

        // 更新幂等记录为 COMPLETED
        String requestId = (String) request.getAttribute("idempotency.requestId");
        if (requestId != null) {
            idempotencyRecordRepository.updateToCompleted(
                requestId,
                "skill_version",
                version.getId(),
                200
            );
        }

        return version;
    }
}
```

### 6.4 定时清理任务

```java
@Component
public class IdempotencyCleanupTask {

    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
    public void cleanupExpiredRecords() {
        int deleted = idempotencyRecordRepository.deleteExpired(Instant.now());
        log.info("Cleaned up {} expired idempotency records", deleted);
    }

    @Scheduled(fixedDelay = 300000)  // 每 5 分钟
    public void cleanupStaleProcessing() {
        // 清理超过 5 分钟仍在 PROCESSING 状态的记录（视为僵死）
        Instant staleThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        int updated = idempotencyRecordRepository.markStaleAsFailed(staleThreshold);
        if (updated > 0) {
            log.warn("Marked {} stale PROCESSING records as FAILED", updated);
        }
    }
}
```

---

## 7. 前端设计

### 7.1 审核中心

#### 路由结构

```
/dashboard/reviews                          → 我的审核任务（我有权审核的）
/dashboard/reviews/my-submissions           → 我的提交（我提交的审核）
/dashboard/reviews/{id}                     → 审核详情页
/dashboard/promotions                       → 提升审核列表（仅平台管理员）
/dashboard/promotions/{id}                  → 提升审核详情页
```

#### 审核任务列表页（`/dashboard/reviews`）

**布局：**
- Tab 切换：待审核 / 已审核 / 全部
- 筛选器：命名空间下拉、提交人搜索、提交时间范围
- 表格列：技能名、版本号、提交人、提交时间、状态、操作

**表格列定义：**

| 列 | 内容 |
|----|------|
| 技能名 | `@namespace/slug` + displayName |
| 版本号 | `1.2.0` |
| 提交人 | 用户头像 + 名称 |
| 提交时间 | 相对时间（2 小时前） |
| 状态 | Badge（PENDING 黄色 / APPROVED 绿色 / REJECTED 红色） |
| 操作 | 查看详情按钮 |

**权限过滤：**
- 团队管理员：只看到自己管理的 namespace 的审核任务
- 平台 SKILL_ADMIN：只看到全局空间的审核任务
- SUPER_ADMIN：看到所有审核任务

#### 审核详情页（`/dashboard/reviews/{id}`）

**布局：**
- 左侧主区域（70%）：
  - 技能信息卡片：名称、版本、提交人、提交时间
  - Tab 切换：README / 文件列表 / 变更历史
  - README tab：Markdown 渲染
  - 文件列表 tab：文件树 + 文件内容预览
  - 变更历史 tab：与上一版本的 diff（如果有）
- 右侧操作栏（30%）：
  - 审核状态 Badge
  - 审核意见输入框（Textarea）
  - 通过按钮（绿色，带确认对话框）
  - 拒绝按钮（红色，必须填写拒绝原因）
  - 审核历史（如果已审核）

**通过确认对话框：**
```
标题：确认通过审核
内容：确认通过技能 @team-ai/my-skill v1.2.0 的审核？通过后技能将立即发布。
按钮：取消 / 确认通过
```

**拒绝对话框：**
```
标题：拒绝审核
内容：
  - 拒绝原因（必填，Textarea）
  - 提示：拒绝原因将发送给提交人
按钮：取消 / 确认拒绝
```

#### 我的提交列表页（`/dashboard/reviews/my-submissions`）

**布局：** 与审核任务列表页类似，但只显示当前用户提交的审核。

**操作列：**
- PENDING 状态：撤回按钮
- APPROVED 状态：查看详情
- REJECTED 状态：查看详情 + 查看拒绝原因

#### 提升审核列表页（`/dashboard/promotions`）

**仅平台 SKILL_ADMIN 和 SUPER_ADMIN 可访问。**

**布局：**
- Tab 切换：待审核 / 已审核 / 全部
- 表格列：来源技能、目标空间、申请版本、提交人、提交时间、状态、操作

**表格列定义：**

| 列 | 内容 |
|----|------|
| 来源技能 | `@team-ai/my-skill` |
| 目标空间 | `@global` |
| 申请版本 | `1.2.0` |
| 提交人 | 用户头像 + 名称 |
| 提交时间 | 相对时间 |
| 状态 | Badge |
| 操作 | 查看详情按钮 |

#### 提升审核详情页（`/dashboard/promotions/{id}`）

**布局：** 与审核详情页类似，但增加提升信息：
- 来源技能：`@team-ai/my-skill`
- 目标空间：`@global`
- 提升后坐标：`@global/my-skill`
- 冲突检查：如果目标空间已存在同名技能，显示警告

### 7.2 评分收藏 UI

#### 技能详情页增强（Phase 2 已有，Phase 3 增强）

**右侧信息栏新增：**

```tsx
// 评分组件
<div className="rating-section">
  <div className="rating-display">
    <StarRating value={skill.ratingAvg} readonly />
    <span className="rating-text">
      {skill.ratingAvg.toFixed(1)} ({skill.ratingCount} 评分)
    </span>
  </div>

  {isAuthenticated ? (
    <div className="user-rating">
      <label>你的评分：</label>
      <StarRating
        value={userRating}
        onChange={handleRatingChange}
      />
    </div>
  ) : (
    <p className="login-prompt">
      <Link to="/login">登录</Link> 后可评分
    </p>
  )}
</div>

// 收藏按钮
<Button
  variant={isStarred ? "default" : "outline"}
  onClick={handleStarToggle}
  disabled={!isAuthenticated}
>
  <Star className={isStarred ? "fill-current" : ""} />
  {isStarred ? "已收藏" : "收藏"}
  <span className="star-count">({skill.starCount})</span>
</Button>
```

**匿名用户点击评分/收藏：**
- 弹出 Toast 提示："请先登录"
- 点击 Toast 跳转到登录页

#### 我的收藏页（`/dashboard/favorites`）

**布局：**
- 网格布局 SkillCard 列表（与搜索页类似）
- 排序选项：收藏时间 / 下载量 / 评分
- 空状态：引导用户浏览技能并收藏

**SkillCard 增强：**
- 右上角显示收藏时间（相对时间）
- 悬浮显示取消收藏按钮

### 7.3 Device Auth 页面（CLI 授权）

#### 路由：`/device`

**权限要求：** 需要登录（未登录用户跳转到登录页）

**页面布局：**

```
┌─────────────────────────────────────────┐
│  skillhub Logo                          │
│                                         │
│  授权 CLI 设备访问                       │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │  请输入 CLI 显示的授权码：          │ │
│  │                                   │ │
│  │  ┌─────┐   ┌─────┐               │ │
│  │  │ABCD │ - │1234 │               │ │
│  │  └─────┘   └─────┘               │ │
│  │                                   │ │
│  │  [确认授权]                        │ │
│  └───────────────────────────────────┘ │
│                                         │
│  提示：授权后，CLI 将获得访问你账号的权限  │
└─────────────────────────────────────────┘
```

**组件设计：**

1. **User Code 输入表单**
   - 两个输入框，分别输入 4 个字符
   - 自动格式化为大写字母和数字
   - 自动聚焦到第一个输入框
   - 输入 4 个字符后自动跳转到第二个输入框
   - 支持粘贴完整的 8 字符码（自动拆分）
   - 实时校验：只允许字母和数字

2. **确认授权按钮**
   - 只有输入完整 8 字符后才启用
   - 点击后弹出确认对话框

3. **授权确认对话框**
   ```
   标题：确认授权 CLI 设备
   内容：
     - 授权码：ABCD-1234
     - 设备信息：skillhub CLI
     - 权限：读取和管理你的技能、命名空间
     - 警告：请确认这是你正在使用的 CLI 设备
   按钮：取消 / 确认授权
   ```

4. **授权成功页面**
   ```
   ┌─────────────────────────────────────────┐
   │  ✓ 授权成功                              │
   │                                         │
   │  你的 CLI 设备已成功授权                  │
   │                                         │
   │  请返回 CLI 继续操作                      │
   │                                         │
   │  [关闭窗口]                              │
   └─────────────────────────────────────────┘
   ```

5. **错误处理**

   | 错误类型 | 提示信息 | 用户操作 |
   |---------|---------|---------|
   | 无效授权码 | "授权码无效，请检查后重试" | 重新输入 |
   | 授权码已过期 | "授权码已过期（15 分钟有效期），请返回 CLI 重新获取" | 返回 CLI |
   | 授权码已使用 | "授权码已被使用，请返回 CLI 重新获取" | 返回 CLI |
   | 网络错误 | "网络错误，请稍后重试" | 重试按钮 |

**交互流程：**

```
用户访问 /device
    │
    ▼
检查登录状态
    │
    ├── 未登录 → 跳转到登录页（带 returnUrl=/device）
    │
    └── 已登录 → 显示授权页面
            │
            ▼
        用户输入 user code
            │
            ▼
        点击"确认授权"
            │
            ▼
        弹出确认对话框
            │
            ▼
        用户确认
            │
            ▼
        调用后端 API：POST /device/authorize
            │
            ├── 成功 → 显示授权成功页面
            │
            └── 失败 → 显示错误提示
```

**前端实现文件：**
- `web/src/pages/device-auth.tsx` - 主页面
- `web/src/features/device-auth/user-code-input.tsx` - User Code 输入组件
- `web/src/features/device-auth/authorize-confirm-dialog.tsx` - 授权确认对话框
- `web/src/features/device-auth/authorize-success.tsx` - 授权成功页面
- `web/src/features/device-auth/use-authorize-device.ts` - 授权 Hook

### 7.4 Token 管理页

#### 路由：`/dashboard/tokens`

**布局：**
- 顶部：创建 Token 按钮
- 表格列：名称、前缀、创建时间、最后使用时间、过期时间、操作

**表格列定义：**

| 列 | 内容 |
|----|------|
| 名称 | Token 名称（如"CI/CD"、"本地开发"） |
| 前缀 | `sk_abc...`（只显示前 10 个字符） |
| 创建时间 | 相对时间 |
| 最后使用时间 | 相对时间 / "从未使用" |
| 过期时间 | 日期 / "永不过期" |
| 操作 | 吊销按钮（红色，带确认） |

**创建 Token 对话框：**

```
标题：创建 API Token
内容：
  - Token 名称（必填，Text Input）
  - 过期时间（可选，Date Picker / "永不过期"）
  - 提示：Token 只会显示一次，请妥善保存
按钮：取消 / 创建
```

**创建成功对话框：**

```
标题：Token 创建成功
内容：
  - Token 字符串（Monospace 字体，带复制按钮）
  - 警告：此 Token 只会显示一次，请立即复制保存
按钮：我已复制
```

**吊销确认对话框：**

```
标题：吊销 Token
内容：确认吊销 Token "CI/CD"？吊销后无法恢复，使用此 Token 的应用将无法访问。
按钮：取消 / 确认吊销
```

### 7.5 管理后台

#### 路由结构

```
/admin                                      → 管理后台首页（仅平台管理员）
/admin/users                                → 用户管理
/admin/users/{id}                           → 用户详情
/admin/roles                                → 角色管理
/admin/audit-logs                           → 审计日志
```

**权限要求：**
- `/admin/users`：USER_ADMIN 或 SUPER_ADMIN
- `/admin/roles`：SUPER_ADMIN
- `/admin/audit-logs`：AUDITOR 或 SUPER_ADMIN

#### 用户管理页（`/admin/users`）

**布局：**
- 搜索框：按用户名/邮箱搜索
- 筛选器：状态（ACTIVE / PENDING / DISABLED / MERGED）
- 表格列：用户名、邮箱、状态、角色、创建时间、操作

**表格列定义：**

| 列 | 内容 |
|----|------|
| 用户名 | 头像 + displayName |
| 邮箱 | email |
| 状态 | Badge（ACTIVE 绿色 / PENDING 黄色 / DISABLED 红色） |
| 角色 | 平台角色列表（Tag） |
| 创建时间 | 相对时间 |
| 操作 | 查看详情 / 编辑角色 / 封禁/解封 |

**操作按钮：**
- **查看详情** - 跳转到用户详情页
- **编辑角色** - 弹出对话框，多选平台角色（SKILL_ADMIN / USER_ADMIN / AUDITOR）
- **封禁/解封** - 切换用户状态（ACTIVE ↔ DISABLED），带确认对话框

**编辑角色对话框：**

```
标题：编辑用户角色
内容：
  - 用户：张三 (zhangsan@example.com)
  - 角色（多选 Checkbox）：
    □ SKILL_ADMIN - 技能治理
    □ USER_ADMIN - 用户治理
    □ AUDITOR - 审计只读
  - 提示：SUPER_ADMIN 角色只能由超管分配
按钮：取消 / 保存
```

#### 用户详情页（`/admin/users/{id}`）

**布局：**
- 用户信息卡片：头像、名称、邮箱、状态、创建时间
- Tab 切换：基本信息 / 平台角色 / 命名空间成员 / 操作历史

**基本信息 Tab：**
- 显示用户的所有身份绑定（GitHub、GitLab 等）
- 显示用户的 API Token 列表（只显示前缀和创建时间）

**平台角色 Tab：**
- 显示用户的平台角色列表
- 添加/移除角色按钮

**命名空间成员 Tab：**
- 显示用户所属的命名空间及角色
- 表格列：命名空间、角色、加入时间

**操作历史 Tab：**
- 显示用户的审计日志（最近 100 条）
- 表格列：操作、目标、时间、IP

#### 审计日志页（`/admin/audit-logs`）

**布局：**
- 筛选器：
  - 操作类型下拉（发布、审核、下载、删除等）
  - 用户搜索
  - 时间范围选择器
  - 目标类型下拉（skill、namespace、user 等）
- 表格列：时间、操作人、操作、目标、IP、详情

**表格列定义：**

| 列 | 内容 |
|----|------|
| 时间 | 精确时间（2026-03-12 10:30:45） |
| 操作人 | 用户头像 + 名称 |
| 操作 | Badge（PUBLISH / APPROVE / REJECT / DELETE 等） |
| 目标 | 目标类型 + ID（如"skill #123"） |
| IP | 客户端 IP |
| 详情 | 展开按钮，显示 detail_json |

**详情展开：**
- JSON 格式化显示
- 语法高亮
- 可复制

### 7.6 前端文件结构（Phase 3 新增）

```
web/src/
├── pages/
│   ├── device-auth.tsx                     # Device Auth 授权页面
│   ├── dashboard/
│   │   ├── reviews.tsx                    # 审核任务列表
│   │   ├── review-detail.tsx              # 审核详情
│   │   ├── my-submissions.tsx             # 我的提交
│   │   ├── promotions.tsx                 # 提升审核列表
│   │   ├── promotion-detail.tsx           # 提升审核详情
│   │   ├── favorites.tsx                  # 我的收藏
│   │   └── tokens.tsx                     # Token 管理
│   └── admin/
│       ├── users.tsx                      # 用户管理
│       ├── user-detail.tsx                # 用户详情
│       ├── roles.tsx                      # 角色管理
│       └── audit-logs.tsx                 # 审计日志
├── features/
│   ├── device-auth/
│   │   ├── user-code-input.tsx            # User Code 输入组件
│   │   ├── authorize-confirm-dialog.tsx   # 授权确认对话框
│   │   ├── authorize-success.tsx          # 授权成功页面
│   │   └── use-authorize-device.ts        # 授权 Hook
│   ├── review/
│   │   ├── review-task-table.tsx
│   │   ├── review-detail-view.tsx
│   │   ├── review-action-panel.tsx
│   │   ├── approve-dialog.tsx
│   │   ├── reject-dialog.tsx
│   │   ├── use-review-tasks.ts
│   │   ├── use-approve-review.ts
│   │   └── use-reject-review.ts
│   ├── promotion/
│   │   ├── promotion-table.tsx
│   │   ├── promotion-detail-view.tsx
│   │   ├── use-promotions.ts
│   │   └── use-approve-promotion.ts
│   ├── rating/
│   │   ├── star-rating.tsx                # 评分组件
│   │   ├── rating-display.tsx             # 评分展示
│   │   ├── use-rate-skill.ts
│   │   └── use-user-rating.ts
│   ├── star/
│   │   ├── star-button.tsx                # 收藏按钮
│   │   ├── use-star-skill.ts
│   │   └── use-starred-skills.ts
│   ├── token/
│   │   ├── token-table.tsx
│   │   ├── create-token-dialog.tsx
│   │   ├── token-created-dialog.tsx
│   │   ├── revoke-token-dialog.tsx
│   │   ├── use-tokens.ts
│   │   ├── use-create-token.ts
│   │   └── use-revoke-token.ts
│   └── admin/
│       ├── user-table.tsx
│       ├── user-detail-view.tsx
│       ├── edit-roles-dialog.tsx
│       ├── audit-log-table.tsx
│       ├── use-users.ts
│       ├── use-audit-logs.ts
│       └── use-update-user-roles.ts
└── shared/
    └── components/
        ├── confirm-dialog.tsx             # 通用确认对话框
        └── json-viewer.tsx                # JSON 查看器
```

---

## 8. Chunk 划分与验收标准

### Chunk 1：审核流程核心（后端）

**范围：** 数据库迁移 + 审核流程 + 提升流程 + 乐观锁 + 分级权限

**任务清单：**
1. 数据库迁移 `V3__phase3_review_social_tables.sql`
   - 创建 review_task、promotion_request、skill_star、skill_rating、idempotency_record 表
   - 创建 partial unique index
2. 领域实体
   - ReviewTask、PromotionRequest、SkillStar、SkillRating、IdempotencyRecord
3. 审核服务
   - ReviewService：提交审核、审核通过/拒绝、撤回审核
   - PromotionService：提交提升、审核提升
   - ReviewPermissionChecker：权限判定
4. Repository 实现
   - ReviewTaskRepository：乐观锁更新方法
   - PromotionRequestRepository：乐观锁更新方法
5. Controller 层
   - ReviewController：审核任务 CRUD、审核操作
   - PromotionController：提升请求 CRUD、审核操作
6. 单元测试 + 集成测试

**验收标准：**
1. 用户可以提交审核，创建 review_task（status=PENDING）
2. 审核人可以通过/拒绝审核，乐观锁防止并发冲突
3. 审核通过后，skill_version.status → PUBLISHED，触发搜索索引更新
4. 审核拒绝后，skill_version.status → REJECTED，记录拒绝原因
5. 用户可以撤回 PENDING 状态的审核
6. 团队管理员只能审核自己管理的 namespace 的技能
7. 平台 SKILL_ADMIN 只能审核全局空间的技能
8. 用户可以提交提升请求，创建 promotion_request（status=PENDING）
9. 平台 SKILL_ADMIN 可以审核提升请求
10. 提升通过后，在全局空间创建新 skill，复制版本和文件
11. 所有审核操作写入 audit_log
12. 所有测试通过

### Chunk 2：评分收藏 + 前端审核中心

**范围：** 评分收藏后端 + 审核中心前端 + Token 管理前端

**任务清单：**

**后端：**
1. 评分收藏服务
   - SkillStarService：收藏/取消收藏
   - SkillRatingService：提交评分
2. 异步事件监听器
   - SkillStarEventListener：更新 star_count
   - SkillRatingEventListener：重算 rating_avg
3. Controller 层
   - SkillStarController：收藏操作、我的收藏列表
   - SkillRatingController：评分操作、获取用户评分

**前端：**
1. 审核中心页面
   - 审核任务列表页
   - 审核详情页
   - 我的提交列表页
   - 提升审核列表页
   - 提升审核详情页
2. 评分收藏组件
   - StarRating 组件
   - StarButton 组件
   - 技能详情页集成
   - 我的收藏页
3. Token 管理页
   - Token 列表
   - 创建 Token 对话框
   - 吊销 Token 对话框

**验收标准：**
1. 用户可以收藏技能，skill.star_count 异步更新
2. 用户可以取消收藏，star_count 异步递减
3. 用户可以对技能评分（1-5 分），skill.rating_avg 异步重算
4. 用户可以修改评分，rating_avg 重新计算
5. 匿名用户点击评分/收藏，提示登录
6. 审核中心：审核人可以查看待审核任务列表
7. 审核中心：审核人可以查看审核详情，通过/拒绝审核
8. 审核中心：用户可以查看自己的提交列表，撤回 PENDING 审核
9. 提升审核：平台管理员可以查看提升请求列表，审核提升
10. Token 管理：用户可以创建 Token，查看 Token 列表，吊销 Token
11. 前端测试通过

### Chunk 3：CLI API + Web 授权

**范围：** OAuth Device Flow + CLI API 端点

**任务清单：**
1. OAuth Device Flow 实现
   - DeviceAuthService：生成 device code、授权、轮询 token
   - DeviceAuthController：device code 端点、token 端点
   - DeviceAuthWebController：Web 授权页面后端
2. CLI API 端点
   - whoami：查询当前用户信息
   - publish：发布技能（复用 Phase 2 逻辑）
   - resolve：解析技能版本
   - check：检查技能包有效性
3. 前端 Device Auth 页面（`/device`）
   - 页面组件：`web/src/pages/device-auth.tsx`
   - User Code 输入表单（8 字符，自动格式化为 ABCD-1234）
   - 输入校验（格式校验、存在性校验）
   - 授权确认对话框（显示 CLI 设备信息）
   - 授权成功页面（提示用户返回 CLI）
   - 错误处理（无效 code、过期 code、已使用 code）
   - 路由配置：`/device` 路由需要登录
4. CLI 工具集成测试（手动测试）

**验收标准：**
1. CLI 运行 `skillhub login`，获取 device code 和 user code
2. CLI 打开浏览器，跳转到授权页面
3. 用户输入 user code，确认授权
4. CLI 轮询获取 token，保存到本地配置文件
5. CLI 运行 `skillhub whoami`，返回当前用户信息
6. CLI 运行 `skillhub publish`，上传技能包，提交审核
7. CLI 运行 `skillhub resolve @team-ai/my-skill`，返回版本信息
8. CLI 运行 `skillhub check skill.zip`，返回校验结果
9. 所有 CLI API 端点测试通过

### Chunk 4：ClawHub 兼容层

**范围：** canonical slug 映射 + 兼容层端点

**任务清单：**
1. CanonicalSlugMapper 实现
2. Well-known 端点：`/.well-known/clawhub.json`
3. 兼容层 Controller
   - search：搜索技能
   - resolve：解析技能版本
   - download：下载技能包
   - publish：发布技能
   - whoami：查询当前用户
4. 协议适配测试（使用 ClawHub CLI 真实请求）

**验收标准：**
1. ClawHub CLI 可以通过 `/.well-known/clawhub.json` 发现兼容层 API
2. ClawHub CLI 可以搜索技能，返回 canonical slug 格式
3. ClawHub CLI 可以解析技能版本（`my-skill` 和 `team-ai--my-skill`）
4. ClawHub CLI 可以下载技能包
5. ClawHub CLI 可以发布技能（需要 Token 认证）
6. ClawHub CLI 可以查询当前用户信息
7. 所有兼容层端点测试通过

### Chunk 5：幂等去重 + 管理后台

**范围：** 幂等拦截器 + 管理后台前端

**任务清单：**

**后端：**
1. 幂等拦截器
   - IdempotencyInterceptor：Redis + PostgreSQL 双层去重
   - IdempotencyCleanupTask：定时清理过期记录
2. 管理后台 API
   - UserManagementController：用户管理、角色分配
   - AuditLogController：审计日志查询

**前端：**
1. 管理后台页面
   - 用户管理页
   - 用户详情页
   - 审计日志页
2. 权限守卫
   - 路由守卫：检查平台角色
   - 组件级权限控制

**验收标准：**
1. 写操作带 `X-Request-Id` 时，重复请求返回原始结果
2. Redis 不可用时，PostgreSQL 兜底去重
3. 定时任务清理过期幂等记录
4. 管理后台：USER_ADMIN 可以查看用户列表，编辑角色，封禁/解封用户
5. 管理后台：AUDITOR 可以查看审计日志，筛选和搜索
6. 管理后台：SUPER_ADMIN 可以访问所有管理功能
7. 前端路由守卫：非管理员访问 `/admin` 跳转到 403 页面
8. 所有测试通过

---

## 9. 测试策略

### 9.1 后端测试

| 层级 | 范围 | 工具 | 覆盖重点 |
|------|------|------|---------|
| 单元测试 | 领域服务、权限检查器 | JUnit 5 + Mockito | ReviewService、PromotionService、ReviewPermissionChecker、SkillStarService、SkillRatingService |
| 集成测试 | Repository + DB | @DataJpaTest + Testcontainers | 乐观锁更新、partial unique index、计数器原子操作 |
| 集成测试 | Redis 幂等去重 | @SpringBootTest + Testcontainers Redis | IdempotencyInterceptor、Redis SETNX |
| API 测试 | Controller | @WebMvcTest + MockMvc | 审核操作、评分收藏、CLI API、兼容层端点 |
| 端到端测试 | 审核全链路 | @SpringBootTest + Testcontainers | 提交审核 → 审核通过 → 发布 → 搜索索引更新 |

### 9.2 关键测试用例

#### 审核流程测试

- 提交审核 → review_task 创建，skill_version.status → PENDING_REVIEW
- 审核通过 → skill_version.status → PUBLISHED，搜索索引更新
- 审核拒绝 → skill_version.status → REJECTED，记录拒绝原因
- 撤回审核 → review_task 删除，skill_version.status → DRAFT
- 并发审核 → 乐观锁冲突，第二个审核人返回 409 Conflict
- 重复提交 → partial unique index 防止重复

#### 提升审核测试

- 提交提升 → promotion_request 创建
- 提升通过 → 全局空间创建新 skill，复制版本和文件
- 提升拒绝 → 原技能不受影响
- slug 冲突 → 提升失败，返回错误

#### 评分收藏测试

- 收藏技能 → skill_star 创建，star_count 异步递增
- 取消收藏 → skill_star 删除，star_count 异步递减
- 重复收藏 → 幂等，不重复计数
- 提交评分 → skill_rating 创建/更新，rating_avg 异步重算
- 修改评分 → rating_avg 重新计算

#### CLI API 测试

- Device Flow → 生成 device code → 授权 → 轮询获取 token
- whoami → 返回当前用户信息
- publish → 上传技能包，提交审核
- resolve → 解析技能版本
- check → 校验技能包

#### 兼容层测试

- canonical slug 映射 → `my-skill` ↔ `@global/my-skill`，`team-ai--my-skill` ↔ `@team-ai/my-skill`
- search → 返回 canonical slug 格式
- resolve → 解析 canonical slug
- download → 下载技能包
- publish → 发布技能

#### 幂等去重测试

- 带 Request-Id 的重复请求 → 返回原始结果
- Redis 不可用 → PostgreSQL 兜底
- PROCESSING 状态超时 → 标记为 FAILED

### 9.3 前端测试

| 类型 | 工具 | 覆盖重点 |
|------|------|---------|
| 组件测试 | Vitest + React Testing Library | StarRating、StarButton、ReviewTaskTable、TokenTable |
| Hook 测试 | renderHook | useReviewTasks、useRateSkill、useStarSkill、useTokens |
| 页面测试 | Vitest + MSW | 审核中心交互、评分收藏交互、Token 管理交互 |

---

## 10. 风险与应对

| 风险 | 应对 |
|------|------|
| 审核流程需求变更 | 状态机设计灵活，支持扩展新状态 |
| 乐观锁冲突频繁 | 前端提示用户刷新重试，后端记录冲突日志监控 |
| 评分重算性能问题 | Redis 分布式锁防止重复重算，定时任务兜底修正 |
| ClawHub CLI 协议细节不一致 | 兼容层独立 Controller，协议回归测试覆盖 |
| Device Flow 用户体验问题 | 提供手动 Token 配置备选方案 |
| 幂等去重 Redis 不可用 | PostgreSQL 兜底，容错设计 |

---

## 11. 总结

Phase 3 在 Phase 2 的基础上，建立了完整的治理体系、CLI 生态和社交功能：

**核心价值：**
1. **审核流程** - 所有技能发布必须经过审核，建立分级审核权限体系，确保技能质量
2. **提升机制** - 团队技能可以申请提升到全局空间，经平台管理员审核，建立技能晋升通道
3. **评分收藏** - 用户可以对技能评分和收藏，建立技能质量反馈机制
4. **CLI API** - 提供 skillhub CLI 所需的核心 API，支持 Web 授权流程，用户体验最佳
5. **ClawHub 兼容层** - 实现 ClawHub CLI 协议兼容，支持现有 ClawHub CLI 用户无缝迁移
6. **幂等去重** - 基于 Redis + PostgreSQL 的双层幂等机制，保证写操作的幂等性
7. **管理后台** - 用户管理、角色分配、审计日志查询，建立完整的运营能力

**技术亮点：**
- 乐观锁 + partial unique index 防止并发冲突
- Redis + PostgreSQL 双层幂等去重
- OAuth Device Flow 提供最佳 CLI 认证体验
- Canonical slug 映射实现 ClawHub 兼容
- 异步事件 + 分布式锁实现计数器更新
- 严格分级权限体系保持团队自治

**交付策略：**
- 5 个 Chunk 渐进式交付，每个 Chunk 都有明确的验收标准
- 审核流程优先，建立治理能力
- CLI API 和兼容层后置，不阻塞核心功能
- 前后端并行开发，提高交付效率

Phase 3 完成后，skillhub 将具备完整的企业内部技能注册中心能力，支持 Web 端和 CLI 端的完整工作流，兼容 ClawHub CLI，建立完善的治理体系和社交功能。
