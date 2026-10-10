# Phase 3: 审核流程 + CLI API + 评分收藏 + 兼容层 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Phase 2 基础上建立完整的治理体系、CLI 生态和社交功能，实现审核流程、提升机制、评分收藏、CLI API、ClawHub 兼容层和管理后台。

**Architecture:**
- 审核流程：乐观锁 + partial unique index 防止并发冲突，分级权限控制
- 评分收藏：异步事件 + Redis 分布式锁更新计数器
- CLI API：OAuth Device Flow 标准认证流程
- 兼容层：Canonical slug 映射实现 ClawHub CLI 协议兼容
- 幂等去重：Redis SETNX + PostgreSQL 双层防护

**身份主键约束：** 用户身份主键全链路统一使用 `string`。本计划里所有 `userId`、`submittedBy`、`reviewedBy`、`ownerId`、`actorUserId` 等用户标识字段均按字符串实现；历史 `Long` / `BIGINT` 描述不再有效。

**Tech Stack:**
- 后端：Spring Boot 3.x + JDK 21 + PostgreSQL 16 + Redis 7 + Spring Security + Flyway
- 前端：React 19 + TypeScript + Vite + TanStack Router + TanStack Query + shadcn/ui
- 新增：react-rating-stars-component（评分组件）

---

## Chunk 1: 审核流程核心（后端）

**范围：** 数据库迁移 + 审核流程 + 提升流程 + 乐观锁 + 分级权限

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

### Task 1: 数据库迁移脚本

**Files:**
- Create: `server/skillhub-app/src/main/resources/db/migration/V3__phase3_review_social_tables.sql`

- [ ] **Step 1: 创建数据库迁移脚本**

创建 `V3__phase3_review_social_tables.sql`，包含 5 个新表：

```sql
-- review_task 表
CREATE TABLE review_task (
    id BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id),
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    submitted_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    reviewed_by VARCHAR(128) REFERENCES user_account(id),
    review_comment TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_review_task_namespace_status ON review_task(namespace_id, status);
CREATE INDEX idx_review_task_submitted_by_status ON review_task(submitted_by, status);
CREATE UNIQUE INDEX idx_review_task_version_pending ON review_task(skill_version_id) WHERE status = 'PENDING';

-- promotion_request 表
CREATE TABLE promotion_request (
    id BIGSERIAL PRIMARY KEY,
    source_skill_id BIGINT NOT NULL REFERENCES skill(id),
    source_version_id BIGINT NOT NULL REFERENCES skill_version(id),
    target_namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    target_skill_id BIGINT REFERENCES skill(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    submitted_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    reviewed_by VARCHAR(128) REFERENCES user_account(id),
    review_comment TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_promotion_request_source_skill ON promotion_request(source_skill_id);
CREATE INDEX idx_promotion_request_status ON promotion_request(status);
CREATE UNIQUE INDEX idx_promotion_request_version_pending ON promotion_request(source_version_id) WHERE status = 'PENDING';

-- skill_star 表
CREATE TABLE skill_star (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, user_id)
);

CREATE INDEX idx_skill_star_user_id ON skill_star(user_id);
CREATE INDEX idx_skill_star_skill_id ON skill_star(skill_id);

-- skill_rating 表
CREATE TABLE skill_rating (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    score SMALLINT NOT NULL CHECK (score >= 1 AND score <= 5),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, user_id)
);

CREATE INDEX idx_skill_rating_skill_id ON skill_rating(skill_id);

-- idempotency_record 表
CREATE TABLE idempotency_record (
    request_id VARCHAR(64) PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT,
    status VARCHAR(32) NOT NULL,
    response_status_code INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_record_expires_at ON idempotency_record(expires_at);
CREATE INDEX idx_idempotency_record_status_created ON idempotency_record(status, created_at);
```

- [ ] **Step 2: 验证迁移脚本语法**

运行：`cd server && ./mvnw flyway:validate`
预期：SUCCESS

- [ ] **Step 3: 执行数据库迁移**

运行：`cd server && ./mvnw flyway:migrate`
预期：V3 迁移成功，5 个新表创建

- [ ] **Step 4: 验证表结构**

运行：`psql -d skillhub -c "\d review_task"`
预期：显示表结构，包含 partial unique index

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/resources/db/migration/V3__phase3_review_social_tables.sql
git commit -m "feat(db): add Phase 3 database migration

- Add review_task table with partial unique index
- Add promotion_request table
- Add skill_star and skill_rating tables
- Add idempotency_record table"
```

### Task 2: 审核流程领域实体

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTask.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequest.java`

- [ ] **Step 1: 创建 ReviewTaskStatus 枚举**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskStatus.java`:

```java
package com.iflytek.skillhub.domain.review;

public enum ReviewTaskStatus {
    PENDING,
    APPROVED,
    REJECTED
}
```

- [ ] **Step 2: 创建 ReviewTask 实体**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTask.java`:

```java
package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_task")
public class ReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // Constructors
    protected ReviewTask() {}

    public ReviewTask(Long skillVersionId, Long namespaceId, String submittedBy) {
        this.skillVersionId = skillVersionId;
        this.namespaceId = namespaceId;
        this.submittedBy = submittedBy;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public Long getSkillVersionId() { return skillVersionId; }
    public Long getNamespaceId() { return namespaceId; }
    public ReviewTaskStatus getStatus() { return status; }
    public void setStatus(ReviewTaskStatus status) { this.status = status; }
    public Integer getVersion() { return version; }
    public Long getSubmittedBy() { return submittedBy; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
```

- [ ] **Step 3: 创建 PromotionRequest 实体**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequest.java`:

```java
package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "promotion_request")
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_skill_id", nullable = false)
    private Long sourceSkillId;

    @Column(name = "source_version_id", nullable = false)
    private Long sourceVersionId;

    @Column(name = "target_namespace_id", nullable = false)
    private Long targetNamespaceId;

    @Column(name = "target_skill_id")
    private Long targetSkillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // Constructors
    protected PromotionRequest() {}

    public PromotionRequest(Long sourceSkillId, Long sourceVersionId,
                           Long targetNamespaceId, String submittedBy) {
        this.sourceSkillId = sourceSkillId;
        this.sourceVersionId = sourceVersionId;
        this.targetNamespaceId = targetNamespaceId;
        this.submittedBy = submittedBy;
    }

    // Getters and Setters (similar to ReviewTask)
    public Long getId() { return id; }
    public Long getSourceSkillId() { return sourceSkillId; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public Long getTargetNamespaceId() { return targetNamespaceId; }
    public Long getTargetSkillId() { return targetSkillId; }
    public void setTargetSkillId(Long targetSkillId) { this.targetSkillId = targetSkillId; }
    public ReviewTaskStatus getStatus() { return status; }
    public void setStatus(ReviewTaskStatus status) { this.status = status; }
    public Integer getVersion() { return version; }
    public Long getSubmittedBy() { return submittedBy; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
```

- [ ] **Step 4: 编译验证**

运行：`cd server && ./mvnw compile`
预期：编译成功

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/
git commit -m "feat(domain): add review entities

- Add ReviewTaskStatus enum
- Add ReviewTask entity with optimistic locking
- Add PromotionRequest entity"
```

### Task 3: Repository 层实现

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/ReviewTaskJpaRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequestRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/PromotionRequestJpaRepository.java`

- [ ] **Step 1: 创建 ReviewTaskRepository 接口**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskRepository.java`:

```java
package com.iflytek.skillhub.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface ReviewTaskRepository {
    ReviewTask save(ReviewTask reviewTask);
    Optional<ReviewTask> findById(Long id);
    Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status);
    Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId, ReviewTaskStatus status, Pageable pageable);
    Page<ReviewTask> findBySubmittedByAndStatus(String submittedBy, ReviewTaskStatus status, Pageable pageable);
    void delete(ReviewTask reviewTask);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                               String reviewComment, Integer expectedVersion);
}
```

- [ ] **Step 2: 创建 ReviewTaskJpaRepository 实现**

创建 `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/ReviewTaskJpaRepository.java`:

```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface ReviewTaskJpaRepository extends JpaRepository<ReviewTask, Long>, ReviewTaskRepository {

    Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status);

    Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId, ReviewTaskStatus status, Pageable pageable);

    Page<ReviewTask> findBySubmittedByAndStatus(String submittedBy, ReviewTaskStatus status, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE ReviewTask t
        SET t.status = :status,
            t.reviewedBy = :reviewedBy,
            t.reviewComment = :reviewComment,
            t.reviewedAt = CURRENT_TIMESTAMP,
            t.version = t.version + 1
        WHERE t.id = :id AND t.version = :expectedVersion
    """)
    int updateStatusWithVersion(@Param("id") Long id,
                               @Param("status") ReviewTaskStatus status,
                               @Param("reviewedBy") String reviewedBy,
                               @Param("reviewComment") String reviewComment,
                               @Param("expectedVersion") Integer expectedVersion);
}
```

- [ ] **Step 3: 创建 PromotionRequestRepository 接口和实现**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequestRepository.java`:

```java
package com.iflytek.skillhub.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface PromotionRequestRepository {
    PromotionRequest save(PromotionRequest request);
    Optional<PromotionRequest> findById(Long id);
    Optional<PromotionRequest> findBySourceVersionIdAndStatus(Long sourceVersionId, ReviewTaskStatus status);
    Page<PromotionRequest> findByStatus(ReviewTaskStatus status, Pageable pageable);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                               String reviewComment, Long targetSkillId, Integer expectedVersion);
}
```

创建 `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/PromotionRequestJpaRepository.java`:

```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PromotionRequestJpaRepository extends JpaRepository<PromotionRequest, Long>,
                                                       PromotionRequestRepository {

    Optional<PromotionRequest> findBySourceVersionIdAndStatus(Long sourceVersionId, ReviewTaskStatus status);

    Page<PromotionRequest> findByStatus(ReviewTaskStatus status, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE PromotionRequest p
        SET p.status = :status,
            p.reviewedBy = :reviewedBy,
            p.reviewComment = :reviewComment,
            p.targetSkillId = :targetSkillId,
            p.reviewedAt = CURRENT_TIMESTAMP,
            p.version = p.version + 1
        WHERE p.id = :id AND p.version = :expectedVersion
    """)
    int updateStatusWithVersion(@Param("id") Long id,
                               @Param("status") ReviewTaskStatus status,
                               @Param("reviewedBy") String reviewedBy,
                               @Param("reviewComment") String reviewComment,
                               @Param("targetSkillId") Long targetSkillId,
                               @Param("expectedVersion") Integer expectedVersion);
}
```

- [ ] **Step 4: 编译验证**

运行：`cd server && ./mvnw compile`
预期：编译成功

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/*Repository.java
git add server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/*Repository.java
git commit -m "feat(repo): add review repositories

- Add ReviewTaskRepository with optimistic lock update
- Add PromotionRequestRepository
- Implement JPA repositories in infra module"
```

### Task 4: 审核权限检查器

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewPermissionChecker.java`
- Create: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/ReviewPermissionCheckerTest.java`

- [ ] **Step 1: 编写权限检查器测试**

创建测试文件，验证权限逻辑：

```java
package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ReviewPermissionCheckerTest {

    private final ReviewPermissionChecker checker = new ReviewPermissionChecker();

    @Test
    void cannotReviewOwnSubmission() {
        String userId = 1L;
        ReviewTask task = createTask(1L, NamespaceType.TEAM, userId);

        boolean canReview = checker.canReview(task, userId, Map.of(), Set.of());

        assertFalse(canReview, "Cannot review own submission");
    }

    @Test
    void teamAdminCanReviewTeamSkill() {
        ReviewTask task = createTask(1L, NamespaceType.TEAM, 2L);

        boolean canReview = checker.canReview(task, 1L,
            Map.of(1L, NamespaceRole.ADMIN), Set.of());

        assertTrue(canReview, "Team ADMIN can review team skill");
    }

    @Test
    void skillAdminCanReviewGlobalSkill() {
        ReviewTask task = createTask(1L, NamespaceType.GLOBAL, 2L);

        boolean canReview = checker.canReview(task, 1L,
            Map.of(), Set.of("SKILL_ADMIN"));

        assertTrue(canReview, "SKILL_ADMIN can review global skill");
    }

    @Test
    void skillAdminCannotReviewTeamSkill() {
        ReviewTask task = createTask(1L, NamespaceType.TEAM, 2L);

        boolean canReview = checker.canReview(task, 1L,
            Map.of(), Set.of("SKILL_ADMIN"));

        assertFalse(canReview, "SKILL_ADMIN cannot review team skill");
    }

    private ReviewTask createTask(Long namespaceId, NamespaceType type, String submittedBy) {
        // Mock ReviewTask with namespace info
        return new ReviewTask(1L, namespaceId, submittedBy);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`cd server && ./mvnw test -Dtest=ReviewPermissionCheckerTest`
预期：测试失败（类不存在）

- [ ] **Step 3: 实现权限检查器**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewPermissionChecker.java`:

```java
package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class ReviewPermissionChecker {

    public boolean canReview(ReviewTask task, String userId,
                            Map<Long, NamespaceRole> userNamespaceRoles,
                            Set<String> platformRoles) {
        // Cannot review own submission
        if (task.getSubmittedBy().equals(userId)) {
            return false;
        }

        // Get namespace type (需要从 task 中获取，这里简化处理)
        NamespaceType namespaceType = getNamespaceType(task.getNamespaceId());

        // Global namespace: only SKILL_ADMIN or SUPER_ADMIN
        if (namespaceType == NamespaceType.GLOBAL) {
            return platformRoles.contains("SKILL_ADMIN")
                || platformRoles.contains("SUPER_ADMIN");
        }

        // Team namespace: namespace ADMIN or OWNER
        NamespaceRole role = userNamespaceRoles.get(task.getNamespaceId());
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    public boolean canReviewPromotion(PromotionRequest request, String userId,
                                     Set<String> platformRoles) {
        // Only SKILL_ADMIN or SUPER_ADMIN can review promotion
        return platformRoles.contains("SKILL_ADMIN")
            || platformRoles.contains("SUPER_ADMIN");
    }

    private NamespaceType getNamespaceType(Long namespaceId) {
        // TODO: 实际实现需要查询 namespace 表
        // 这里简化处理，假设 id=1 是 GLOBAL
        return namespaceId == 1L ? NamespaceType.GLOBAL : NamespaceType.TEAM;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

运行：`cd server && ./mvnw test -Dtest=ReviewPermissionCheckerTest`
预期：所有测试通过

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewPermissionChecker.java
git add server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/ReviewPermissionCheckerTest.java
git commit -m "feat(review): add permission checker with tests

- Implement ReviewPermissionChecker
- Add unit tests for permission logic
- Verify team admin can only review team skills
- Verify SKILL_ADMIN can only review global skills"
```

### Task 5: 审核服务实现（核心逻辑）

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java`
- Create: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/ReviewServiceTest.java`

由于篇幅限制，这里提供关键方法的实现框架：

- [ ] **Step 1: 创建 ReviewService 接口**

```java
package com.iflytek.skillhub.domain.review;

public interface ReviewService {
    ReviewTask submitReview(Long skillVersionId, Long namespaceId, String userId);
    void approveReview(Long reviewTaskId, String reviewerId, String comment);
    void rejectReview(Long reviewTaskId, String reviewerId, String comment);
    void withdrawReview(Long skillVersionId, String userId);
}
```

- [ ] **Step 2-5: 实现服务方法（TDD 循环）**

参考设计文档第 2.1 节的流程图实现每个方法，包括：
- 乐观锁更新
- 状态机转换
- 事件发布
- 审计日志

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(review): implement review service

- Add submitReview with duplicate check
- Add approveReview with optimistic locking
- Add rejectReview with reason recording
- Add withdrawReview with PENDING check"
```

### Task 6: PromotionService（提升流程服务）

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionRequest.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionRequestRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/service/PromotionService.java`
- Create: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/promotion/service/PromotionServiceTest.java`

- [ ] **Step 1: 创建 PromotionStatus 枚举**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionStatus.java`：

```java
package com.iflytek.skillhub.domain.promotion;

public enum PromotionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
```

- [ ] **Step 2: 创建 PromotionRequest 实体**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionRequest.java`：

```java
package com.iflytek.skillhub.domain.promotion;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_request")
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_skill_id", nullable = false)
    private Long sourceSkillId;

    @Column(name = "source_version_id", nullable = false)
    private Long sourceVersionId;

    @Column(name = "target_namespace_id", nullable = false)
    private Long targetNamespaceId;

    @Column(name = "target_skill_id")
    private Long targetSkillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PromotionStatus status = PromotionStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // Constructors
    public PromotionRequest() {}

    public PromotionRequest(Long sourceSkillId, Long sourceVersionId, Long targetNamespaceId, String submittedBy) {
        this.sourceSkillId = sourceSkillId;
        this.sourceVersionId = sourceVersionId;
        this.targetNamespaceId = targetNamespaceId;
        this.submittedBy = submittedBy;
        this.submittedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceSkillId() {
        return sourceSkillId;
    }

    public void setSourceSkillId(Long sourceSkillId) {
        this.sourceSkillId = sourceSkillId;
    }

    public Long getSourceVersionId() {
        return sourceVersionId;
    }

    public void setSourceVersionId(Long sourceVersionId) {
        this.sourceVersionId = sourceVersionId;
    }

    public Long getTargetNamespaceId() {
        return targetNamespaceId;
    }

    public void setTargetNamespaceId(Long targetNamespaceId) {
        this.targetNamespaceId = targetNamespaceId;
    }

    public Long getTargetSkillId() {
        return targetSkillId;
    }

    public void setTargetSkillId(Long targetSkillId) {
        this.targetSkillId = targetSkillId;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public void setStatus(PromotionStatus status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
```

- [ ] **Step 3: 创建 PromotionRequestRepository**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionRequestRepository.java`：

```java
package com.iflytek.skillhub.domain.promotion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionRequestRepository extends JpaRepository<PromotionRequest, Long> {

    @Query("SELECT pr FROM PromotionRequest pr WHERE pr.sourceSkillId = :sourceSkillId AND pr.sourceVersionId = :sourceVersionId AND pr.status = 'PENDING'")
    Optional<PromotionRequest> findPendingBySourceVersion(Long sourceSkillId, Long sourceVersionId);

    @Query("SELECT pr FROM PromotionRequest pr WHERE pr.targetNamespaceId = :targetNamespaceId AND pr.status = :status")
    Page<PromotionRequest> findByTargetNamespaceAndStatus(Long targetNamespaceId, PromotionStatus status, Pageable pageable);

    @Query("SELECT pr FROM PromotionRequest pr WHERE pr.submittedBy = :userId")
    Page<PromotionRequest> findBySubmittedBy(String userId, Pageable pageable);
}
```

- [ ] **Step 4: 创建 PromotionService**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/service/PromotionService.java`：

```java
package com.iflytek.skillhub.domain.promotion.service;

import com.iflytek.skillhub.domain.event.PromotionApprovedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.promotion.PromotionRequest;
import com.iflytek.skillhub.domain.promotion.PromotionRequestRepository;
import com.iflytek.skillhub.domain.promotion.PromotionStatus;
import com.iflytek.skillhub.domain.skill.*;
import jakarta.persistence.OptimisticLockException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PromotionService {

    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final NamespaceRepository namespaceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PromotionService(
            PromotionRequestRepository promotionRequestRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            NamespaceRepository namespaceRepository,
            ApplicationEventPublisher eventPublisher) {
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.namespaceRepository = namespaceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PromotionRequest submitPromotion(Long sourceSkillId, Long sourceVersionId, Long targetNamespaceId, String userId) {
        // 1. Check if source skill and version exist
        Skill sourceSkill = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new IllegalArgumentException("Source skill not found"));

        SkillVersion sourceVersion = skillVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Source version not found"));

        if (!sourceVersion.getSkillId().equals(sourceSkillId)) {
            throw new IllegalArgumentException("Version does not belong to skill");
        }

        // 2. Check if version is published
        if (sourceVersion.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("Only published versions can be promoted");
        }

        // 3. Check if target namespace exists and is global
        Namespace targetNamespace = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new IllegalArgumentException("Target namespace not found"));

        if (!targetNamespace.isGlobal()) {
            throw new IllegalArgumentException("Can only promote to global namespace");
        }

        // 4. Check if there's already a pending promotion
        promotionRequestRepository.findPendingBySourceVersion(sourceSkillId, sourceVersionId)
                .ifPresent(pr -> {
                    throw new IllegalArgumentException("A pending promotion request already exists");
                });

        // 5. Create promotion request
        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
        return promotionRequestRepository.save(request);
    }

    @Transactional
    public PromotionRequest approvePromotion(Long promotionId, String reviewerId, String comment) {
        // 1. Load promotion request with optimistic lock
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion request not found"));

        // 2. Check status
        if (request.getStatus() != PromotionStatus.PENDING) {
            throw new IllegalStateException("Promotion request is not pending");
        }

        // 3. Load source skill and version
        Skill sourceSkill = skillRepository.findById(request.getSourceSkillId())
                .orElseThrow(() -> new IllegalArgumentException("Source skill not found"));

        SkillVersion sourceVersion = skillVersionRepository.findById(request.getSourceVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Source version not found"));

        // 4. Check if target skill already exists
        Namespace targetNamespace = namespaceRepository.findById(request.getTargetNamespaceId())
                .orElseThrow(() -> new IllegalArgumentException("Target namespace not found"));

        Skill targetSkill = skillRepository.findByNamespaceIdAndSlug(targetNamespace.getId(), sourceSkill.getSlug())
                .orElseGet(() -> {
                    // Create new skill in global namespace
                    Skill newSkill = new Skill(
                            targetNamespace.getId(),
                            sourceSkill.getSlug(),
                            sourceSkill.getDisplayName(),
                            sourceSkill.getSummary(),
                            sourceSkill.getVisibility(),
                            reviewerId
                    );
                    return skillRepository.save(newSkill);
                });

        // 5. Copy version
        SkillVersion targetVersion = new SkillVersion(
                targetSkill.getId(),
                sourceVersion.getVersionNumber(),
                sourceVersion.getMetadataJson(),
                reviewerId
        );
        targetVersion.setStatus(SkillVersionStatus.PUBLISHED);
        targetVersion.setFileCount(sourceVersion.getFileCount());
        targetVersion.setTotalSize(sourceVersion.getTotalSize());
        targetVersion = skillVersionRepository.save(targetVersion);

        // 6. Copy files
        List<SkillFile> sourceFiles = skillFileRepository.findByVersionId(sourceVersion.getId());
        for (SkillFile sourceFile : sourceFiles) {
            SkillFile targetFile = new SkillFile(
                    targetVersion.getId(),
                    sourceFile.getFilePath(),
                    sourceFile.getFileSize(),
                    sourceFile.getContentType(),
                    sourceFile.getSha256(),
                    sourceFile.getStorageKey() // Reuse same storage key
            );
            skillFileRepository.save(targetFile);
        }

        // 7. Update target skill
        targetSkill.setLatestVersionId(targetVersion.getId());
        targetSkill.setUpdatedBy(reviewerId);
        skillRepository.save(targetSkill);

        // 8. Update promotion request
        request.setStatus(PromotionStatus.APPROVED);
        request.setReviewedBy(reviewerId);
        request.setReviewComment(comment);
        request.setReviewedAt(LocalDateTime.now());
        request.setTargetSkillId(targetSkill.getId());

        try {
            request = promotionRequestRepository.save(request);
        } catch (OptimisticLockException e) {
            throw new IllegalStateException("Promotion request was modified by another process", e);
        }

        // 9. Publish event
        eventPublisher.publishEvent(new PromotionApprovedEvent(
                request.getId(),
                targetSkill.getId(),
                targetVersion.getId(),
                reviewerId
        ));

        return request;
    }

    @Transactional
    public PromotionRequest rejectPromotion(Long promotionId, String reviewerId, String comment) {
        // 1. Load promotion request with optimistic lock
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion request not found"));

        // 2. Check status
        if (request.getStatus() != PromotionStatus.PENDING) {
            throw new IllegalStateException("Promotion request is not pending");
        }

        // 3. Update status
        request.setStatus(PromotionStatus.REJECTED);
        request.setReviewedBy(reviewerId);
        request.setReviewComment(comment);
        request.setReviewedAt(LocalDateTime.now());

        try {
            return promotionRequestRepository.save(request);
        } catch (OptimisticLockException e) {
            throw new IllegalStateException("Promotion request was modified by another process", e);
        }
    }

    @Transactional
    public PromotionRequest withdrawPromotion(Long promotionId, String userId) {
        // 1. Load promotion request
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion request not found"));

        // 2. Check ownership
        if (!request.getSubmittedBy().equals(userId)) {
            throw new IllegalArgumentException("Only the submitter can withdraw the promotion");
        }

        // 3. Check status
        if (request.getStatus() != PromotionStatus.PENDING) {
            throw new IllegalStateException("Only pending promotions can be withdrawn");
        }

        // 4. Update status
        request.setStatus(PromotionStatus.WITHDRAWN);
        request.setReviewedAt(LocalDateTime.now());

        try {
            return promotionRequestRepository.save(request);
        } catch (OptimisticLockException e) {
            throw new IllegalStateException("Promotion request was modified by another process", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<PromotionRequest> listPendingPromotions(Long targetNamespaceId, Pageable pageable) {
        return promotionRequestRepository.findByTargetNamespaceAndStatus(
                targetNamespaceId, PromotionStatus.PENDING, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PromotionRequest> listMyPromotions(String userId, Pageable pageable) {
        return promotionRequestRepository.findBySubmittedBy(userId, pageable);
    }

    @Transactional(readOnly = true)
    public PromotionRequest getPromotionDetail(Long promotionId) {
        return promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion request not found"));
    }
}
```

- [ ] **Step 5: 创建 PromotionApprovedEvent**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionApprovedEvent.java`：

```java
package com.iflytek.skillhub.domain.event;

public record PromotionApprovedEvent(
        Long promotionId,
        Long targetSkillId,
        Long targetVersionId,
        String reviewerId
) {}
```

- [ ] **Step 6: 编写 PromotionService 测试**

创建 `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/promotion/service/PromotionServiceTest.java`：

```java
package com.iflytek.skillhub.domain.promotion.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.promotion.PromotionRequest;
import com.iflytek.skillhub.domain.promotion.PromotionRequestRepository;
import com.iflytek.skillhub.domain.promotion.PromotionStatus;
import com.iflytek.skillhub.domain.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PromotionServiceTest {

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private PromotionRequestRepository promotionRequestRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private SkillFileRepository skillFileRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    private Namespace teamNamespace;
    private Namespace globalNamespace;
    private Skill teamSkill;
    private SkillVersion publishedVersion;
    private String userId = 1L;

    @BeforeEach
    void setUp() {
        // Create team namespace
        teamNamespace = new Namespace("team-alpha", "Team Alpha", false, userId);
        teamNamespace = namespaceRepository.save(teamNamespace);

        // Create global namespace
        globalNamespace = new Namespace("global", "Global", true, userId);
        globalNamespace = namespaceRepository.save(globalNamespace);

        // Create team skill
        teamSkill = new Skill(teamNamespace.getId(), "awesome-skill", "Awesome Skill", "A great skill", SkillVisibility.PUBLIC, userId);
        teamSkill = skillRepository.save(teamSkill);

        // Create published version
        publishedVersion = new SkillVersion(teamSkill.getId(), "1.0.0", "{}", userId);
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);
        publishedVersion = skillVersionRepository.save(publishedVersion);

        teamSkill.setLatestVersionId(publishedVersion.getId());
        skillRepository.save(teamSkill);
    }

    @Test
    void submitPromotion_success() {
        PromotionRequest request = promotionService.submitPromotion(
                teamSkill.getId(), publishedVersion.getId(), globalNamespace.getId(), userId);

        assertThat(request.getId()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.PENDING);
        assertThat(request.getSourceSkillId()).isEqualTo(teamSkill.getId());
        assertThat(request.getTargetNamespaceId()).isEqualTo(globalNamespace.getId());
    }

    @Test
    void submitPromotion_duplicatePending_throwsException() {
        promotionService.submitPromotion(teamSkill.getId(), publishedVersion.getId(), globalNamespace.getId(), userId);

        assertThatThrownBy(() -> promotionService.submitPromotion(
                teamSkill.getId(), publishedVersion.getId(), globalNamespace.getId(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending promotion request already exists");
    }

    @Test
    void approvePromotion_success() {
        PromotionRequest request = promotionService.submitPromotion(
                teamSkill.getId(), publishedVersion.getId(), globalNamespace.getId(), userId);

        PromotionRequest approved = promotionService.approvePromotion(request.getId(), userId, "Looks good");

        assertThat(approved.getStatus()).isEqualTo(PromotionStatus.APPROVED);
        assertThat(approved.getReviewedBy()).isEqualTo(userId);
        assertThat(approved.getTargetSkillId()).isNotNull();

        // Verify target skill created
        Skill targetSkill = skillRepository.findById(approved.getTargetSkillId()).orElseThrow();
        assertThat(targetSkill.getNamespaceId()).isEqualTo(globalNamespace.getId());
        assertThat(targetSkill.getSlug()).isEqualTo(teamSkill.getSlug());
    }

    @Test
    void rejectPromotion_success() {
        PromotionRequest request = promotionService.submitPromotion(
                teamSkill.getId(), publishedVersion.getId(), globalNamespace.getId(), userId);

        PromotionRequest rejected = promotionService.rejectPromotion(request.getId(), userId, "Not ready");

        assertThat(rejected.getStatus()).isEqualTo(PromotionStatus.REJECTED);
        assertThat(rejected.getReviewComment()).isEqualTo("Not ready");
    }

    @Test
    void withdrawPromotion_success() {
        PromotionRequest request = promotionService.submitPromotion(
                teamSkill.getId(), publishedVersion.getId(), globalNamespace.getId(), userId);

        PromotionRequest withdrawn = promotionService.withdrawPromotion(request.getId(), userId);

        assertThat(withdrawn.getStatus()).isEqualTo(PromotionStatus.WITHDRAWN);
    }
}
```

---


### Task 7: ReviewController + PromotionController（审核和提升 REST API）

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ReviewController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewTaskRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewTaskResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewActionRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionRequestDto.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionResponseDto.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionActionRequest.java`

- [ ] **Step 1: 创建 Review DTOs**

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewTaskRequest.java`：

```java
package com.iflytek.skillhub.dto;

public record ReviewTaskRequest(
        Long skillVersionId
) {}
```

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewTaskResponse.java`：

```java
package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;

public record ReviewTaskResponse(
        Long id,
        Long skillVersionId,
        String namespace,
        String skillSlug,
        String version,
        String status,
        String submittedBy,
        String submittedByUsername,
        String reviewedBy,
        String reviewedByUsername,
        String reviewComment,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {}
```

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewActionRequest.java`：

```java
package com.iflytek.skillhub.dto;

public record ReviewActionRequest(
        String comment
) {}
```

- [ ] **Step 2: 创建 Promotion DTOs**

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionRequestDto.java`：

```java
package com.iflytek.skillhub.dto;

public record PromotionRequestDto(
        Long sourceSkillId,
        Long sourceVersionId,
        String targetNamespace
) {}
```

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionResponseDto.java`：

```java
package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;

public record PromotionResponseDto(
        Long id,
        Long sourceSkillId,
        String sourceNamespace,
        String sourceSkillSlug,
        String sourceVersion,
        String targetNamespace,
        Long targetSkillId,
        String status,
        String submittedBy,
        String submittedByUsername,
        String reviewedBy,
        String reviewedByUsername,
        String reviewComment,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {}
```

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionActionRequest.java`：

```java
package com.iflytek.skillhub.dto;

public record PromotionActionRequest(
        String comment
) {}
```

- [ ] **Step 3: 创建 ReviewController**

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ReviewController.java`：

```java
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.service.ReviewService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.ReviewActionRequest;
import com.iflytek.skillhub.dto.ReviewTaskRequest;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewTaskRepository reviewTaskRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;

    public ReviewController(
            ReviewService reviewService,
            ReviewTaskRepository reviewTaskRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            NamespaceRepository namespaceRepository,
            UserAccountRepository userAccountRepository,
            RbacService rbacService) {
        this.reviewService = reviewService;
        this.reviewTaskRepository = reviewTaskRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
    }

    @PostMapping
    public ResponseEntity<ReviewTaskResponse> submitReview(
            @RequestBody ReviewTaskRequest request,
            @RequestAttribute("userId") String userId) {

        ReviewTask task = reviewService.submitReview(request.skillVersionId(), userId);
        return ResponseEntity.ok(toResponse(task));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ReviewTaskResponse> approveReview(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewActionRequest request,
            @RequestAttribute("userId") String userId) {

        String comment = request != null ? request.comment() : null;
        ReviewTask task = reviewService.approveReview(id, userId, comment);
        return ResponseEntity.ok(toResponse(task));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ReviewTaskResponse> rejectReview(
            @PathVariable Long id,
            @RequestBody ReviewActionRequest request,
            @RequestAttribute("userId") String userId) {

        ReviewTask task = reviewService.rejectReview(id, userId, request.comment());
        return ResponseEntity.ok(toResponse(task));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ReviewTaskResponse> withdrawReview(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId) {

        ReviewTask task = reviewService.withdrawReview(id, userId);
        return ResponseEntity.ok(toResponse(task));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<ReviewTaskResponse>> listPendingReviews(
            @RequestParam(required = false) String namespace,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId) {

        Page<ReviewTask> tasks;
        if (namespace != null) {
            Namespace ns = namespaceRepository.findBySlug(namespace)
                    .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + namespace));
            tasks = reviewTaskRepository.findPendingByNamespace(ns.getId(), PageRequest.of(page, size));
        } else {
            // List all pending reviews user can access
            boolean isSkillAdmin = rbacService.hasRole(userId, "SKILL_ADMIN");
            if (isSkillAdmin) {
                // SKILL_ADMIN can see all pending reviews
                tasks = reviewTaskRepository.findByStatus(com.iflytek.skillhub.domain.review.ReviewStatus.PENDING, PageRequest.of(page, size));
            } else {
                // Regular users see reviews for namespaces they manage
                tasks = Page.empty();
            }
        }

        return ResponseEntity.ok(tasks.map(this::toResponse));
    }

    @GetMapping("/my-submissions")
    public ResponseEntity<Page<ReviewTaskResponse>> listMySubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId) {

        Page<ReviewTask> tasks = reviewTaskRepository.findBySubmittedBy(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(tasks.map(this::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewTaskResponse> getReviewDetail(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId) {

        ReviewTask task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Review task not found: " + id));

        return ResponseEntity.ok(toResponse(task));
    }

    private ReviewTaskResponse toResponse(ReviewTask task) {
        SkillVersion version = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new IllegalStateException("Version not found"));
        Skill skill = skillRepository.findById(version.getSkillId())
                .orElseThrow(() -> new IllegalStateException("Skill not found"));
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new IllegalStateException("Namespace not found"));

        UserAccount submitter = userAccountRepository.findById(task.getSubmittedBy())
                .orElseThrow(() -> new IllegalStateException("Submitter not found"));

        String reviewedByUsername = null;
        if (task.getReviewedBy() != null) {
            reviewedByUsername = userAccountRepository.findById(task.getReviewedBy())
                    .map(UserAccount::getUsername)
                    .orElse(null);
        }

        return new ReviewTaskResponse(
                task.getId(),
                task.getSkillVersionId(),
                namespace.getSlug(),
                skill.getSlug(),
                version.getVersion(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                submitter.getUsername(),
                task.getReviewedBy(),
                reviewedByUsername,
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
    }
}
```

- [ ] **Step 4: 创建 PromotionController**

创建 `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java`：

```java
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.promotion.PromotionRequest;
import com.iflytek.skillhub.domain.promotion.PromotionRequestRepository;
import com.iflytek.skillhub.domain.promotion.PromotionStatus;
import com.iflytek.skillhub.domain.promotion.service.PromotionService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PromotionActionRequest;
import com.iflytek.skillhub.dto.PromotionRequestDto;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;

    public PromotionController(
            PromotionService promotionService,
            PromotionRequestRepository promotionRequestRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            NamespaceRepository namespaceRepository,
            UserAccountRepository userAccountRepository,
            RbacService rbacService) {
        this.promotionService = promotionService;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
    }

    @PostMapping
    public ResponseEntity<PromotionResponseDto> submitPromotion(
            @RequestBody PromotionRequestDto request,
            @RequestAttribute("userId") String userId) {

        PromotionRequest promotion = promotionService.submitPromotion(
                request.sourceSkillId(),
                request.sourceVersionId(),
                request.targetNamespace(),
                userId
        );

        return ResponseEntity.ok(toResponse(promotion));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PromotionResponseDto> approvePromotion(
            @PathVariable Long id,
            @RequestBody(required = false) PromotionActionRequest request,
            @RequestAttribute("userId") String userId) {

        String comment = request != null ? request.comment() : null;
        PromotionRequest promotion = promotionService.approvePromotion(id, userId, comment);
        return ResponseEntity.ok(toResponse(promotion));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PromotionResponseDto> rejectPromotion(
            @PathVariable Long id,
            @RequestBody PromotionActionRequest request,
            @RequestAttribute("userId") String userId) {

        PromotionRequest promotion = promotionService.rejectPromotion(id, userId, request.comment());
        return ResponseEntity.ok(toResponse(promotion));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<PromotionResponseDto>> listPendingPromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId) {

        // Only SKILL_ADMIN can list pending promotions
        if (!rbacService.hasRole(userId, "SKILL_ADMIN")) {
            throw new IllegalArgumentException("Only SKILL_ADMIN can list pending promotions");
        }

        Page<PromotionRequest> promotions = promotionRequestRepository.findByStatus(
                PromotionStatus.PENDING,
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(promotions.map(this::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponseDto> getPromotionDetail(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId) {

        PromotionRequest promotion = promotionRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion request not found: " + id));

        return ResponseEntity.ok(toResponse(promotion));
    }

    private PromotionResponseDto toResponse(PromotionRequest promotion) {
        Skill sourceSkill = skillRepository.findById(promotion.getSourceSkillId())
                .orElseThrow(() -> new IllegalStateException("Source skill not found"));
        SkillVersion sourceVersion = skillVersionRepository.findById(promotion.getSourceVersionId())
                .orElseThrow(() -> new IllegalStateException("Source version not found"));
        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new IllegalStateException("Source namespace not found"));
        Namespace targetNamespace = namespaceRepository.findById(promotion.getTargetNamespaceId())
                .orElseThrow(() -> new IllegalStateException("Target namespace not found"));

        UserAccount submitter = userAccountRepository.findById(promotion.getSubmittedBy())
                .orElseThrow(() -> new IllegalStateException("Submitter not found"));

        String reviewedByUsername = null;
        if (promotion.getReviewedBy() != null) {
            reviewedByUsername = userAccountRepository.findById(promotion.getReviewedBy())
                    .map(UserAccount::getUsername)
                    .orElse(null);
        }

        return new PromotionResponseDto(
                promotion.getId(),
                promotion.getSourceSkillId(),
                sourceNamespace.getSlug(),
                sourceSkill.getSlug(),
                sourceVersion.getVersion(),
                targetNamespace.getSlug(),
                promotion.getTargetSkillId(),
                promotion.getStatus().name(),
                promotion.getSubmittedBy(),
                submitter.getUsername(),
                promotion.getReviewedBy(),
                reviewedByUsername,
                promotion.getReviewComment(),
                promotion.getSubmittedAt(),
                promotion.getReviewedAt()
        );
    }
}
```

- [ ] **Step 5: 编写 Controller 集成测试**

创建 `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/ReviewControllerTest.java`：

```java
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.review.ReviewStatus;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private ReviewTaskRepository reviewTaskRepository;

    @Test
    void submitReview_shouldReturn200() throws Exception {
        ReviewTask task = new ReviewTask(1L, 1L, 1L);
        task.setId(1L);
        when(reviewService.submitReview(anyLong(), anyLong())).thenReturn(task);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillVersionId\": 1}")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void approveReview_shouldReturn200() throws Exception {
        ReviewTask task = new ReviewTask(1L, 1L, 1L);
        task.setId(1L);
        task.setStatus(ReviewStatus.APPROVED);
        when(reviewService.approveReview(anyLong(), anyLong(), anyString())).thenReturn(task);

        mockMvc.perform(post("/api/v1/reviews/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"LGTM\"}")
                        .requestAttr("userId", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
```

---


### Task 8: 发布流程改造（修改 SkillPublishService）

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java`
- Create: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceReviewTest.java`

- [ ] **Step 1: 修改 SkillVersionStatus 枚举（已完成）**

确认 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java` 已包含 `PENDING_REVIEW`：

```java
package com.iflytek.skillhub.domain.skill;

public enum SkillVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED
}
```

- [ ] **Step 2: 修改 SkillPublishService 创建 PENDING_REVIEW 版本**

修改 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`：

在 `publishFromEntries` 方法中，找到创建 `SkillVersion` 的代码（约第 120 行）：

```java
// 原代码：
SkillVersion version = new SkillVersion(
        skill.getId(),
        metadata.version(),
        SkillVersionStatus.PUBLISHED,  // <-- 修改这里
        publisherId
);
```

修改为：

```java
// 新代码：
SkillVersion version = new SkillVersion(
        skill.getId(),
        metadata.version(),
        SkillVersionStatus.PENDING_REVIEW,  // <-- 改为 PENDING_REVIEW
        publisherId
);
```

- [ ] **Step 3: 在 SkillPublishService 中自动创建 ReviewTask**

在 `SkillPublishService` 类中添加依赖注入：

```java
// 在类的字段声明部分添加：
private final ReviewTaskRepository reviewTaskRepository;

// 在构造函数中添加参数：
public SkillPublishService(
        NamespaceRepository namespaceRepository,
        NamespaceMemberRepository namespaceMemberRepository,
        SkillRepository skillRepository,
        SkillVersionRepository skillVersionRepository,
        SkillFileRepository skillFileRepository,
        ObjectStorageService objectStorageService,
        SkillPackageValidator skillPackageValidator,
        SkillMetadataParser skillMetadataParser,
        PrePublishValidator prePublishValidator,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper,
        ReviewTaskRepository reviewTaskRepository) {  // <-- 新增
    this.namespaceRepository = namespaceRepository;
    this.namespaceMemberRepository = namespaceMemberRepository;
    this.skillRepository = skillRepository;
    this.skillVersionRepository = skillVersionRepository;
    this.skillFileRepository = skillFileRepository;
    this.objectStorageService = objectStorageService;
    this.skillPackageValidator = skillPackageValidator;
    this.skillMetadataParser = skillMetadataParser;
    this.prePublishValidator = prePublishValidator;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.reviewTaskRepository = reviewTaskRepository;  // <-- 新增
}
```

在 `publishFromEntries` 方法的最后，在发布事件之前（约第 189 行），添加创建 ReviewTask 的代码：

```java
// 在 eventPublisher.publishEvent(...) 之前添加：

// 12.5. Auto-create ReviewTask
ReviewTask reviewTask = new ReviewTask(
        version.getId(),
        namespace.getId(),
        publisherId
);
reviewTaskRepository.save(reviewTask);
```

同时需要在文件顶部添加 import：

```java
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
```

- [ ] **Step 4: 修改 SkillPublishedEvent 的触发时机**

在 `publishFromEntries` 方法中，找到发布事件的代码（约第 190 行）：

```java
// 原代码：
eventPublisher.publishEvent(new SkillPublishedEvent(skill.getId(), version.getId(), publisherId));
```

注释掉或删除这行代码，因为现在版本状态是 PENDING_REVIEW，不应该立即触发 SkillPublishedEvent。该事件将在审核通过后由 ReviewApprovedEvent 监听器触发。

```java
// 13. Publish SkillPublishedEvent - 移除，改为审核通过后触发
// eventPublisher.publishEvent(new SkillPublishedEvent(skill.getId(), version.getId(), publisherId));
```

- [ ] **Step 5: 编写测试验证发布流程改造**

创建 `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceReviewTest.java`：

```java
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillPublishServiceReviewTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private SkillPackageValidator skillPackageValidator;
    @Mock
    private SkillMetadataParser skillMetadataParser;
    @Mock
    private PrePublishValidator prePublishValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @InjectMocks
    private SkillPublishService skillPublishService;

    @Test
    void publishFromEntries_shouldCreatePendingReviewVersion() {
        // Arrange
        String publisherId = 100L;
        String namespaceSlug = "test-ns";
        
        Namespace namespace = new Namespace();
        namespace.setId(1L);
        namespace.setSlug(namespaceSlug);
        
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, publisherId))
                .thenReturn(Optional.of(mock(com.iflytek.skillhub.domain.namespace.NamespaceMember.class)));
        
        SkillMetadata metadata = new SkillMetadata("test-skill", "1.0.0", "Test Skill", "Description", null, null, null);
        when(skillMetadataParser.parse(any())).thenReturn(metadata);
        
        Skill skill = new Skill();
        skill.setId(10L);
        skill.setSlug("test-skill");
        when(skillRepository.findByNamespaceIdAndSlug(1L, "test-skill")).thenReturn(Optional.of(skill));
        
        SkillVersion version = new SkillVersion();
        version.setId(20L);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        when(skillVersionRepository.save(any(SkillVersion.class))).thenReturn(version);
        
        PackageEntry entry = new PackageEntry("skill.json", "application/json", 100L, new ByteArrayInputStream("{}".getBytes()));
        List<PackageEntry> entries = List.of(entry);
        
        // Act
        SkillVersion result = skillPublishService.publishFromEntries(namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC);
        
        // Assert
        assertEquals(SkillVersionStatus.PENDING_REVIEW, result.getStatus());
        verify(reviewTaskRepository, times(1)).save(any(ReviewTask.class));
        verify(eventPublisher, never()).publishEvent(any());  // 不应该触发 SkillPublishedEvent
    }

    @Test
    void publishFromEntries_shouldAutoCreateReviewTask() {
        // Arrange
        String publisherId = 100L;
        String namespaceSlug = "test-ns";
        
        Namespace namespace = new Namespace();
        namespace.setId(1L);
        namespace.setSlug(namespaceSlug);
        
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, publisherId))
                .thenReturn(Optional.of(mock(com.iflytek.skillhub.domain.namespace.NamespaceMember.class)));
        
        SkillMetadata metadata = new SkillMetadata("test-skill", "1.0.0", "Test Skill", "Description", null, null, null);
        when(skillMetadataParser.parse(any())).thenReturn(metadata);
        
        Skill skill = new Skill();
        skill.setId(10L);
        skill.setSlug("test-skill");
        when(skillRepository.findByNamespaceIdAndSlug(1L, "test-skill")).thenReturn(Optional.of(skill));
        
        SkillVersion version = new SkillVersion();
        version.setId(20L);
        when(skillVersionRepository.save(any(SkillVersion.class))).thenReturn(version);
        
        PackageEntry entry = new PackageEntry("skill.json", "application/json", 100L, new ByteArrayInputStream("{}".getBytes()));
        List<PackageEntry> entries = List.of(entry);
        
        // Act
        skillPublishService.publishFromEntries(namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC);
        
        // Assert
        ArgumentCaptor<ReviewTask> captor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(reviewTaskRepository).save(captor.capture());
        
        ReviewTask savedTask = captor.getValue();
        assertEquals(20L, savedTask.getSkillVersionId());
        assertEquals(1L, savedTask.getNamespaceId());
        assertEquals(publisherId, savedTask.getSubmittedBy());
    }
}
```

- [ ] **Step 6: 运行测试验证**

```bash
cd /Users/xudongsun/github/skillhub/server
./mvnw test -Dtest=SkillPublishServiceReviewTest
```

---


### Task 9: 审核事件监听器（Event Listeners）

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewApprovedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewRejectedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionApprovedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/event/ReviewEventListener.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/event/PromotionEventListener.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLog.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLogRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditAction.java`

- [ ] **Step 1: 创建审核事件类**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewApprovedEvent.java`：

```java
package com.iflytek.skillhub.domain.event;

public record ReviewApprovedEvent(
        Long reviewTaskId,
        Long skillId,
        Long versionId,
        String reviewerId,
        String comment
) {}
```

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewRejectedEvent.java`：

```java
package com.iflytek.skillhub.domain.event;

public record ReviewRejectedEvent(
        Long reviewTaskId,
        Long skillId,
        Long versionId,
        String reviewerId,
        String comment
) {}
```

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionApprovedEvent.java`：

```java
package com.iflytek.skillhub.domain.event;

public record PromotionApprovedEvent(
        Long promotionRequestId,
        Long sourceSkillId,
        Long sourceVersionId,
        Long targetSkillId,
        String reviewerId
) {}
```

- [ ] **Step 2: 创建 AuditLog 实体和枚举**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditAction.java`：

```java
package com.iflytek.skillhub.domain.audit;

public enum AuditAction {
    REVIEW_SUBMITTED,
    REVIEW_APPROVED,
    REVIEW_REJECTED,
    REVIEW_WITHDRAWN,
    PROMOTION_SUBMITTED,
    PROMOTION_APPROVED,
    PROMOTION_REJECTED,
    PROMOTION_WITHDRAWN,
    SKILL_PUBLISHED,
    SKILL_ARCHIVED
}
```

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLog.java`：

```java
package com.iflytek.skillhub.domain.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors
    public AuditLog() {}

    public AuditLog(AuditAction action, String entityType, Long entityId, String userId, String details) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.userId = userId;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
```

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLogRepository.java`：

```java
package com.iflytek.skillhub.domain.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
```

- [ ] **Step 3: 创建 ReviewEventListener**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/event/ReviewEventListener.java`：

```java
package com.iflytek.skillhub.domain.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditAction;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import com.iflytek.skillhub.domain.event.ReviewApprovedEvent;
import com.iflytek.skillhub.domain.event.ReviewRejectedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
public class ReviewEventListener {

    private final SkillVersionRepository skillVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ReviewEventListener(
            SkillVersionRepository skillVersionRepository,
            AuditLogRepository auditLogRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.skillVersionRepository = skillVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    @Transactional
    public void onReviewApproved(ReviewApprovedEvent event) {
        // 1. Update version status to PUBLISHED
        SkillVersion version = skillVersionRepository.findById(event.versionId())
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + event.versionId()));
        
        version.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersionRepository.save(version);

        // 2. Write audit log
        Map<String, Object> details = new HashMap<>();
        details.put("reviewTaskId", event.reviewTaskId());
        details.put("versionId", event.versionId());
        details.put("comment", event.comment());
        
        try {
            String detailsJson = objectMapper.writeValueAsString(details);
            AuditLog log = new AuditLog(
                    AuditAction.REVIEW_APPROVED,
                    "skill",
                    event.skillId(),
                    event.reviewerId(),
                    detailsJson
            );
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Log error but don't fail the transaction
            System.err.println("Failed to write audit log: " + e.getMessage());
        }

        // 3. Trigger SkillPublishedEvent for search indexing
        eventPublisher.publishEvent(new SkillPublishedEvent(
                event.skillId(),
                event.versionId(),
                event.reviewerId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    @Transactional
    public void onReviewRejected(ReviewRejectedEvent event) {
        // 1. Update version status to REJECTED
        SkillVersion version = skillVersionRepository.findById(event.versionId())
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + event.versionId()));
        
        version.setStatus(SkillVersionStatus.REJECTED);
        skillVersionRepository.save(version);

        // 2. Write audit log
        Map<String, Object> details = new HashMap<>();
        details.put("reviewTaskId", event.reviewTaskId());
        details.put("versionId", event.versionId());
        details.put("comment", event.comment());
        
        try {
            String detailsJson = objectMapper.writeValueAsString(details);
            AuditLog log = new AuditLog(
                    AuditAction.REVIEW_REJECTED,
                    "skill",
                    event.skillId(),
                    event.reviewerId(),
                    detailsJson
            );
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 创建 PromotionEventListener**

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/event/PromotionEventListener.java`：

```java
package com.iflytek.skillhub.domain.promotion.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditAction;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import com.iflytek.skillhub.domain.event.PromotionApprovedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
public class PromotionEventListener {

    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PromotionEventListener(
            AuditLogRepository auditLogRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    @Transactional
    public void onPromotionApproved(PromotionApprovedEvent event) {
        // 1. Write audit log
        Map<String, Object> details = new HashMap<>();
        details.put("promotionRequestId", event.promotionRequestId());
        details.put("sourceSkillId", event.sourceSkillId());
        details.put("sourceVersionId", event.sourceVersionId());
        details.put("targetSkillId", event.targetSkillId());
        
        try {
            String detailsJson = objectMapper.writeValueAsString(details);
            AuditLog log = new AuditLog(
                    AuditAction.PROMOTION_APPROVED,
                    "skill",
                    event.targetSkillId(),
                    event.reviewerId(),
                    detailsJson
            );
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }

        // 2. Trigger SkillPublishedEvent for search indexing of the new global skill
        eventPublisher.publishEvent(new SkillPublishedEvent(
                event.targetSkillId(),
                event.sourceVersionId(),
                event.reviewerId()
        ));
    }
}
```

- [ ] **Step 5: 配置异步执行器（如果尚未配置）**

检查是否存在 `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AsyncConfig.java`，如果不存在则创建：

```java
package com.iflytek.skillhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "skillhubEventExecutor")
    public Executor skillhubEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 6: 编写测试验证事件监听器**

创建 `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/event/ReviewEventListenerTest.java`：

```java
package com.iflytek.skillhub.domain.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditAction;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import com.iflytek.skillhub.domain.event.ReviewApprovedEvent;
import com.iflytek.skillhub.domain.event.ReviewRejectedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReviewEventListener listener;

    @Test
    void onReviewApproved_shouldUpdateStatusAndTriggerEvents() throws Exception {
        // Given
        Long skillId = 1L;
        Long versionId = 10L;
        String reviewerId = 5L;
        
        SkillVersion version = new SkillVersion();
        version.setId(versionId);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        
        when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"data\"}");
        
        ReviewApprovedEvent event = new ReviewApprovedEvent(100L, skillId, versionId, reviewerId, "LGTM");

        // When
        listener.onReviewApproved(event);

        // Then
        assertEquals(SkillVersionStatus.PUBLISHED, version.getStatus());
        verify(skillVersionRepository).save(version);
        
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog savedLog = auditCaptor.getValue();
        assertEquals(AuditAction.REVIEW_APPROVED, savedLog.getAction());
        assertEquals("skill", savedLog.getEntityType());
        assertEquals(skillId, savedLog.getEntityId());
        assertEquals(reviewerId, savedLog.getUserId());
        
        ArgumentCaptor<SkillPublishedEvent> eventCaptor = ArgumentCaptor.forClass(SkillPublishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SkillPublishedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(skillId, publishedEvent.skillId());
        assertEquals(versionId, publishedEvent.versionId());
    }

    @Test
    void onReviewRejected_shouldUpdateStatusToRejected() throws Exception {
        // Given
        Long skillId = 1L;
        Long versionId = 10L;
        String reviewerId = 5L;
        
        SkillVersion version = new SkillVersion();
        version.setId(versionId);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        
        when(skillVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"data\"}");
        
        ReviewRejectedEvent event = new ReviewRejectedEvent(100L, skillId, versionId, reviewerId, "Issues found");

        // When
        listener.onReviewRejected(event);

        // Then
        assertEquals(SkillVersionStatus.REJECTED, version.getStatus());
        verify(skillVersionRepository).save(version);
        
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog savedLog = auditCaptor.getValue();
        assertEquals(AuditAction.REVIEW_REJECTED, savedLog.getAction());
        
        // Should NOT trigger SkillPublishedEvent for rejected reviews
        verify(eventPublisher, never()).publishEvent(any(SkillPublishedEvent.class));
    }
}
```


### Task 10: Chunk 1 验收（编译、测试、验证脚本）

**Files:**
- Create: `server/verify-phase3-chunk1.sh`

- [ ] **Step 1: 编译整个项目**

在项目根目录执行：

```bash
cd /Users/xudongsun/github/skillhub/server
./mvnw clean compile -DskipTests
```

验证所有模块编译成功，无错误。

- [ ] **Step 2: 运行所有测试**

```bash
./mvnw test
```

验证所有测试通过，特别关注：
- `ReviewServiceTest`
- `PromotionServiceTest`
- `SkillPublishServiceReviewTest`
- `ReviewEventListenerTest`
- `PromotionEventListenerTest`

- [ ] **Step 3: 创建验收验证脚本**

创建 `server/verify-phase3-chunk1.sh`：

```bash
#!/bin/bash

set -e

echo "=========================================="
echo "Phase 3 Chunk 1 验收脚本"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $1"
        return 0
    else
        echo -e "${RED}✗${NC} $1 (缺失)"
        return 1
    fi
}

check_class() {
    local file="$1"
    local class_name="$2"
    if grep -q "class $class_name\|interface $class_name\|enum $class_name\|record $class_name" "$file" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} $class_name 定义正确"
        return 0
    else
        echo -e "${RED}✗${NC} $class_name 定义缺失或错误"
        return 1
    fi
}

FAILED=0

echo "1. 检查数据库迁移脚本..."
check_file "skillhub-app/src/main/resources/db/migration/V3__phase3_review_social_tables.sql" || FAILED=1
echo ""

echo "2. 检查 Review 实体和 Repository..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTask.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskRepository.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewStatus.java" || FAILED=1
echo ""

echo "3. 检查 Promotion 实体和 Repository..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionRequest.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionRequestRepository.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/PromotionStatus.java" || FAILED=1
echo ""

echo "4. 检查权限检查服务..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/service/ReviewPermissionService.java" || FAILED=1
echo ""

echo "5. 检查 ReviewService..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/service/ReviewService.java" || FAILED=1
echo ""

echo "6. 检查 PromotionService..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/service/PromotionService.java" || FAILED=1
echo ""

echo "7. 检查 Controllers 和 DTOs..."
check_file "skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ReviewController.java" || FAILED=1
check_file "skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java" || FAILED=1
check_file "skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewTaskRequest.java" || FAILED=1
check_file "skillhub-app/src/main/java/com/iflytek/skillhub/dto/ReviewTaskResponse.java" || FAILED=1
check_file "skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionRequestDto.java" || FAILED=1
check_file "skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionResponseDto.java" || FAILED=1
echo ""

echo "8. 检查 SkillPublishService 改造..."
if grep -q "PENDING_REVIEW" "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} SkillPublishService 已改造为创建 PENDING_REVIEW 状态"
else
    echo -e "${RED}✗${NC} SkillPublishService 未改造"
    FAILED=1
fi

if grep -q "ReviewTaskRepository" "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} SkillPublishService 已集成 ReviewTaskRepository"
else
    echo -e "${RED}✗${NC} SkillPublishService 未集成 ReviewTaskRepository"
    FAILED=1
fi
echo ""

echo "9. 检查事件和监听器..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewApprovedEvent.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewRejectedEvent.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionApprovedEvent.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/event/ReviewEventListener.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/promotion/event/PromotionEventListener.java" || FAILED=1
echo ""

echo "10. 检查 AuditLog..."
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLog.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLogRepository.java" || FAILED=1
check_file "skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditAction.java" || FAILED=1
echo ""

echo "11. 检查测试文件..."
check_file "skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/service/ReviewServiceTest.java" || FAILED=1
check_file "skillhub-domain/src/test/java/com/iflytek/skillhub/domain/promotion/service/PromotionServiceTest.java" || FAILED=1
check_file "skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceReviewTest.java" || FAILED=1
check_file "skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/event/ReviewEventListenerTest.java" || FAILED=1
check_file "skillhub-domain/src/test/java/com/iflytek/skillhub/domain/promotion/event/PromotionEventListenerTest.java" || FAILED=1
echo ""

echo "=========================================="
echo "12. 编译项目..."
echo "=========================================="
if ./mvnw clean compile -DskipTests > /tmp/compile.log 2>&1; then
    echo -e "${GREEN}✓${NC} 编译成功"
else
    echo -e "${RED}✗${NC} 编译失败，查看 /tmp/compile.log"
    FAILED=1
fi
echo ""

echo "=========================================="
echo "13. 运行测试..."
echo "=========================================="
if ./mvnw test -Dtest="ReviewServiceTest,PromotionServiceTest,SkillPublishServiceReviewTest,ReviewEventListenerTest,PromotionEventListenerTest" > /tmp/test.log 2>&1; then
    echo -e "${GREEN}✓${NC} 所有测试通过"
else
    echo -e "${RED}✗${NC} 测试失败，查看 /tmp/test.log"
    FAILED=1
fi
echo ""

echo "=========================================="
echo "验收结果"
echo "=========================================="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ Phase 3 Chunk 1 验收通过！${NC}"
    echo ""
    echo "已完成功能："
    echo "  1. ✓ 数据库迁移脚本（review_task, promotion_request, audit_log 等 5 张表）"
    echo "  2. ✓ ReviewTask 实体和 Repository"
    echo "  3. ✓ PromotionRequest 实体和 Repository"
    echo "  4. ✓ ReviewPermissionService（分级权限检查）"
    echo "  5. ✓ ReviewService（提交/审核/拒绝/撤回，乐观锁）"
    echo "  6. ✓ PromotionService（提交/审核/拒绝，复制技能到全局空间）"
    echo "  7. ✓ ReviewController + PromotionController（REST API）"
    echo "  8. ✓ SkillPublishService 改造（PENDING_REVIEW + 自动创建 ReviewTask）"
    echo "  9. ✓ 审核事件监听器（更新状态 + 触发搜索索引 + 写入 audit_log）"
    echo "  10. ✓ 所有测试通过"
    echo ""
    echo "下一步："
    echo "  - 启动应用，手动测试审核流程"
    echo "  - 使用 Postman/curl 测试 API 端点"
    echo "  - 验证乐观锁并发控制"
    echo "  - 验证分级权限（团队管理员 vs 平台管理员）"
    echo "  - 开始 Chunk 2: 评分收藏功能"
    exit 0
else
    echo -e "${RED}✗ Phase 3 Chunk 1 验收失败${NC}"
    echo ""
    echo "请检查上述失败项，修复后重新运行验收脚本。"
    exit 1
fi
```

- [ ] **Step 4: 赋予脚本执行权限并运行**

```bash
chmod +x server/verify-phase3-chunk1.sh
cd server
./verify-phase3-chunk1.sh
```

- [ ] **Step 5: 手动验证审核流程**

启动应用后，使用 Postman 或 curl 测试以下场景：

**场景 1: 用户发布技能并提交审核**

```bash
# 1. 发布技能（自动创建 PENDING_REVIEW 版本和 ReviewTask）
curl -X POST http://localhost:8080/api/v1/skills/publish \
  -H "Authorization: Bearer <user_token>" \
  -F "file=@skill-package.zip" \
  -F "namespace=my-team" \
  -F "visibility=PUBLIC"

# 2. 查看待审核列表（团队管理员）
curl -X GET "http://localhost:8080/api/v1/reviews/pending?namespace=my-team" \
  -H "Authorization: Bearer <admin_token>"

# 3. 审核通过
curl -X POST http://localhost:8080/api/v1/reviews/123/approve \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"comment": "LGTM"}'

# 4. 验证版本状态变为 PUBLISHED
curl -X GET http://localhost:8080/api/v1/skills/my-team/my-skill \
  -H "Authorization: Bearer <user_token>"
```

**场景 2: 审核拒绝**

```bash
# 1. 拒绝审核
curl -X POST http://localhost:8080/api/v1/reviews/124/reject \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"comment": "需要修复安全问题"}'

# 2. 验证版本状态变为 REJECTED
curl -X GET http://localhost:8080/api/v1/skills/my-team/my-skill/versions/1.0.1
```

**场景 3: 提升到全局空间**

```bash
# 1. 提交提升请求
curl -X POST http://localhost:8080/api/v1/promotions \
  -H "Authorization: Bearer <user_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSkillId": 456,
    "sourceVersionId": 789,
    "targetNamespace": "global"
  }'

# 2. 平台管理员审核通过
curl -X POST http://localhost:8080/api/v1/promotions/10/approve \
  -H "Authorization: Bearer <platform_admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"comment": "优秀的技能，批准提升"}'

# 3. 验证全局空间中创建了新技能
curl -X GET http://localhost:8080/api/v1/skills/global/my-skill
```

**场景 4: 并发审核（乐观锁验证）**

使用两个终端同时执行审核操作，验证只有一个成功，另一个返回 409 Conflict。

**场景 5: 权限验证**

```bash
# 1. 团队管理员尝试审核其他团队的技能（应失败）
curl -X POST http://localhost:8080/api/v1/reviews/125/approve \
  -H "Authorization: Bearer <team_a_admin_token>"
# 预期: 403 Forbidden

# 2. 平台管理员尝试审核团队空间的技能（应失败）
curl -X POST http://localhost:8080/api/v1/reviews/126/approve \
  -H "Authorization: Bearer <platform_admin_token>"
# 预期: 403 Forbidden（平台管理员只能审核全局空间）

# 3. 普通用户尝试审核（应失败）
curl -X POST http://localhost:8080/api/v1/reviews/127/approve \
  -H "Authorization: Bearer <normal_user_token>"
# 预期: 403 Forbidden
```

- [ ] **Step 6: 验证 audit_log 记录**

连接数据库，检查 audit_log 表：

```sql
-- 查看所有审核操作的审计日志
SELECT * FROM audit_log 
WHERE action IN ('REVIEW_SUBMITTED', 'REVIEW_APPROVED', 'REVIEW_REJECTED', 'REVIEW_WITHDRAWN')
ORDER BY created_at DESC;

-- 查看所有提升操作的审计日志
SELECT * FROM audit_log 
WHERE action IN ('PROMOTION_SUBMITTED', 'PROMOTION_APPROVED', 'PROMOTION_REJECTED')
ORDER BY created_at DESC;
```

验证每个审核操作都有对应的审计日志记录。

- [ ] **Step 7: 验证搜索索引更新**

```bash
# 1. 审核通过后，搜索新发布的技能
curl -X GET "http://localhost:8080/api/v1/search?q=my-skill"

# 2. 验证搜索结果中包含新发布的技能
```

- [ ] **Step 8: Chunk 1 验收完成确认**

确认以下所有验收标准已满足：

1. ✓ 用户可以提交审核，创建 review_task（status=PENDING）
2. ✓ 审核人可以通过/拒绝审核，乐观锁防止并发冲突
3. ✓ 审核通过后，skill_version.status → PUBLISHED，触发搜索索引更新
4. ✓ 审核拒绝后，skill_version.status → REJECTED，记录拒绝原因
5. ✓ 用户可以撤回 PENDING 状态的审核
6. ✓ 团队管理员只能审核自己管理的 namespace 的技能
7. ✓ 平台 SKILL_ADMIN 只能审核全局空间的技能
8. ✓ 用户可以提交提升请求，创建 promotion_request（status=PENDING）
9. ✓ 平台 SKILL_ADMIN 可以审核提升请求
10. ✓ 提升通过后，在全局空间创建新 skill，复制版本和文件
11. ✓ 所有审核操作写入 audit_log
12. ✓ 所有测试通过

**Chunk 1 完成！可以开始 Chunk 2: 评分收藏功能。**

---

## 总结

Phase 3 Chunk 1 实现了完整的审核流程核心功能：

**核心组件：**
1. 数据库迁移（5 张新表）
2. ReviewTask + PromotionRequest 实体和 Repository
3. ReviewPermissionService（分级权限检查）
4. ReviewService + PromotionService（核心业务逻辑）
5. ReviewController + PromotionController（REST API）
6. SkillPublishService 改造（PENDING_REVIEW + 自动创建 ReviewTask）
7. 事件监听器（更新状态 + 触发搜索索引 + 写入 audit_log）

**关键技术点：**
- 乐观锁（@Version）防止并发冲突
- Partial unique index 防止重复提交
- 分级权限控制（团队自治 + 平台管理）
- 事件驱动架构（@TransactionalEventListener）
- 审计日志（所有操作可追溯）

**下一步：**
- Chunk 2: 评分收藏功能（rating, favorite, 异步计数器更新）
- Chunk 3: CLI API（OAuth Device Flow, API 端点）
- Chunk 4: ClawHub 兼容层（Canonical slug 映射）
- Chunk 5: 幂等去重 + 管理后台

---


## Chunk 2: 评分收藏 + 前端审核中心

**范围：** 评分收藏后端 + 审核中心前端 + Token 管理前端

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

### Task 1: SkillStar 和 SkillRating 领域实体

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillStar.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillRating.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillStarRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillRatingRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaSkillStarRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaSkillRatingRepository.java`

- [ ] **Step 1: 创建 SkillStar 实体**

```java
package com.iflytek.skillhub.domain.social;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_star",
    uniqueConstraints = @UniqueConstraint(columns = {"skill_id", "user_id"}))
public class SkillStar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SkillStar() {}

    public SkillStar(Long skillId, String userId) {
        this.skillId = skillId;
        this.userId = userId;
    }

    // getters
    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: 创建 SkillRating 实体**

```java
package com.iflytek.skillhub.domain.social;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_rating",
    uniqueConstraints = @UniqueConstraint(columns = {"skill_id", "user_id"}))
public class SkillRating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Short score;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected SkillRating() {}

    public SkillRating(Long skillId, String userId, short score) {
        if (score < 1 || score > 5) throw new IllegalArgumentException("Score must be 1-5");
        this.skillId = skillId;
        this.userId = userId;
        this.score = score;
    }

    public void updateScore(short newScore) {
        if (newScore < 1 || newScore > 5) throw new IllegalArgumentException("Score must be 1-5");
        this.score = newScore;
        this.updatedAt = LocalDateTime.now();
    }

    // getters
    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public Long getUserId() { return userId; }
    public Short getScore() { return score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 3: 创建 Repository 接口**

`SkillStarRepository.java`:
```java
package com.iflytek.skillhub.domain.social;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillStarRepository {
    SkillStar save(SkillStar star);
    Optional<SkillStar> findBySkillIdAndUserId(Long skillId, String userId);
    void delete(SkillStar star);
    Page<SkillStar> findByUserId(String userId, Pageable pageable);
    long countBySkillId(Long skillId);
}
```

`SkillRatingRepository.java`:
```java
package com.iflytek.skillhub.domain.social;

import java.util.Optional;

public interface SkillRatingRepository {
    SkillRating save(SkillRating rating);
    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, String userId);
    double averageScoreBySkillId(Long skillId);
    int countBySkillId(Long skillId);
}
```

- [ ] **Step 4: 实现 JPA Repository**

`JpaSkillStarRepository.java`:
```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface JpaSkillStarRepository extends JpaRepository<SkillStar, Long>, SkillStarRepository {
    Optional<SkillStar> findBySkillIdAndUserId(Long skillId, String userId);
    Page<SkillStar> findByUserId(String userId, Pageable pageable);
    long countBySkillId(Long skillId);
}
```

`JpaSkillRatingRepository.java`:
```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JpaSkillRatingRepository extends JpaRepository<SkillRating, Long>, SkillRatingRepository {
    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, String userId);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM SkillRating r WHERE r.skillId = :skillId")
    double averageScoreBySkillId(Long skillId);

    int countBySkillId(Long skillId);
}
```

- [ ] **Step 5: 编译验证**

运行：`cd server && ./mvnw compile`
预期：编译成功

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/
git add server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaSkillStar*
git add server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaSkillRating*
git commit -m "feat(social): add SkillStar and SkillRating entities and repositories"
```

### Task 2: SkillStarService 和 SkillRatingService

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillStarService.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillRatingService.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/SkillStarredEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/SkillUnstarredEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/SkillRatedEvent.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillStarServiceTest.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillRatingServiceTest.java`

- [ ] **Step 1: 创建领域事件类**

`SkillStarredEvent.java`:
```java
package com.iflytek.skillhub.domain.social.event;

public record SkillStarredEvent(Long skillId, String userId) {}
```

`SkillUnstarredEvent.java`:
```java
package com.iflytek.skillhub.domain.social.event;

public record SkillUnstarredEvent(Long skillId, String userId) {}
```

`SkillRatedEvent.java`:
```java
package com.iflytek.skillhub.domain.social.event;

public record SkillRatedEvent(Long skillId, String userId, short score) {}
```

- [ ] **Step 2: 编写 SkillStarService 测试**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillStarServiceTest {
    @Mock SkillStarRepository starRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks SkillStarService service;

    @Test
    void star_skill_creates_record_and_publishes_event() {
        when(starRepository.findBySkillIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(starRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.star(1L, 10L);

        verify(starRepository).save(any(SkillStar.class));
        verify(eventPublisher).publishEvent(any(SkillStarredEvent.class));
    }

    @Test
    void star_skill_already_starred_is_idempotent() {
        when(starRepository.findBySkillIdAndUserId(1L, 10L))
            .thenReturn(Optional.of(new SkillStar(1L, 10L)));

        service.star(1L, 10L);

        verify(starRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unstar_skill_deletes_record_and_publishes_event() {
        SkillStar existing = new SkillStar(1L, 10L);
        when(starRepository.findBySkillIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));

        service.unstar(1L, 10L);

        verify(starRepository).delete(existing);
        verify(eventPublisher).publishEvent(any(SkillUnstarredEvent.class));
    }

    @Test
    void unstar_skill_not_starred_is_noop() {
        when(starRepository.findBySkillIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        service.unstar(1L, 10L);

        verify(starRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void isStarred_returns_true_when_exists() {
        when(starRepository.findBySkillIdAndUserId(1L, 10L))
            .thenReturn(Optional.of(new SkillStar(1L, 10L)));
        assertThat(service.isStarred(1L, 10L)).isTrue();
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

运行：`cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillStarServiceTest`
预期：编译失败，SkillStarService 不存在

- [ ] **Step 4: 实现 SkillStarService**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillStarService {
    private final SkillStarRepository starRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillStarService(SkillStarRepository starRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.starRepository = starRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void star(Long skillId, String userId) {
        if (starRepository.findBySkillIdAndUserId(skillId, userId).isPresent()) {
            return; // idempotent
        }
        starRepository.save(new SkillStar(skillId, userId));
        eventPublisher.publishEvent(new SkillStarredEvent(skillId, userId));
    }

    @Transactional
    public void unstar(Long skillId, String userId) {
        starRepository.findBySkillIdAndUserId(skillId, userId).ifPresent(star -> {
            starRepository.delete(star);
            eventPublisher.publishEvent(new SkillUnstarredEvent(skillId, userId));
        });
    }

    public boolean isStarred(Long skillId, String userId) {
        return starRepository.findBySkillIdAndUserId(skillId, userId).isPresent();
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillStarServiceTest`
预期：5 个测试全部 PASS

- [ ] **Step 6: 编写 SkillRatingService 测试**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRatingServiceTest {
    @Mock SkillRatingRepository ratingRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks SkillRatingService service;

    @Test
    void rate_creates_new_rating() {
        when(ratingRepository.findBySkillIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rate(1L, 10L, (short) 4);

        verify(ratingRepository).save(argThat(r -> r.getScore() == 4));
        verify(eventPublisher).publishEvent(any(SkillRatedEvent.class));
    }

    @Test
    void rate_updates_existing_rating() {
        SkillRating existing = new SkillRating(1L, 10L, (short) 3);
        when(ratingRepository.findBySkillIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rate(1L, 10L, (short) 5);

        assertThat(existing.getScore()).isEqualTo((short) 5);
        verify(ratingRepository).save(existing);
        verify(eventPublisher).publishEvent(any(SkillRatedEvent.class));
    }

    @Test
    void rate_invalid_score_throws() {
        assertThatThrownBy(() -> service.rate(1L, 10L, (short) 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rate(1L, 10L, (short) 6))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getUserRating_returns_score() {
        SkillRating existing = new SkillRating(1L, 10L, (short) 4);
        when(ratingRepository.findBySkillIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));
        assertThat(service.getUserRating(1L, 10L)).hasValue((short) 4);
    }
}
```

- [ ] **Step 7: 实现 SkillRatingService**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SkillRatingService {
    private final SkillRatingRepository ratingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillRatingService(SkillRatingRepository ratingRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.ratingRepository = ratingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void rate(Long skillId, String userId, short score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score must be 1-5");
        }
        Optional<SkillRating> existing = ratingRepository.findBySkillIdAndUserId(skillId, userId);
        if (existing.isPresent()) {
            existing.get().updateScore(score);
            ratingRepository.save(existing.get());
        } else {
            ratingRepository.save(new SkillRating(skillId, userId, score));
        }
        eventPublisher.publishEvent(new SkillRatedEvent(skillId, userId, score));
    }

    public Optional<Short> getUserRating(Long skillId, String userId) {
        return ratingRepository.findBySkillIdAndUserId(skillId, userId)
            .map(SkillRating::getScore);
    }
}
```

- [ ] **Step 8: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillRatingServiceTest`
预期：4 个测试全部 PASS

- [ ] **Step 9: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillStarService.java
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillRatingService.java
git add server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/
git commit -m "feat(social): add SkillStarService and SkillRatingService with events"
```

### Task 3: 异步事件监听器（计数器更新）

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/listener/SkillStarEventListener.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/listener/SkillRatingEventListener.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/listener/SkillStarEventListenerTest.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/listener/SkillRatingEventListenerTest.java`

- [ ] **Step 1: 编写 SkillStarEventListener 测试**

```java
package com.iflytek.skillhub.app.listener;

import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillStarEventListenerTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock SkillStarRepository starRepository;
    @InjectMocks SkillStarEventListener listener;

    @Test
    void onStarred_updates_star_count() {
        when(starRepository.countBySkillId(1L)).thenReturn(42L);
        listener.onStarred(new SkillStarredEvent(1L, 10L));
        verify(jdbcTemplate).update("UPDATE skill SET star_count = ? WHERE id = ?", 42, 1L);
    }

    @Test
    void onUnstarred_updates_star_count() {
        when(starRepository.countBySkillId(1L)).thenReturn(41L);
        listener.onUnstarred(new SkillUnstarredEvent(1L, 10L));
        verify(jdbcTemplate).update("UPDATE skill SET star_count = ? WHERE id = ?", 41, 1L);
    }
}
```

- [ ] **Step 2: 实现 SkillStarEventListener**

```java
package com.iflytek.skillhub.app.listener;

import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SkillStarEventListener {
    private final JdbcTemplate jdbcTemplate;
    private final SkillStarRepository starRepository;

    public SkillStarEventListener(JdbcTemplate jdbcTemplate, SkillStarRepository starRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.starRepository = starRepository;
    }

    @Async
    @TransactionalEventListener
    public void onStarred(SkillStarredEvent event) {
        updateStarCount(event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onUnstarred(SkillUnstarredEvent event) {
        updateStarCount(event.skillId());
    }

    private void updateStarCount(Long skillId) {
        long count = starRepository.countBySkillId(skillId);
        jdbcTemplate.update("UPDATE skill SET star_count = ? WHERE id = ?", count, skillId);
    }
}
```

- [ ] **Step 3: 编写 SkillRatingEventListener 测试**

```java
package com.iflytek.skillhub.app.listener;

import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRatingEventListenerTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock SkillRatingRepository ratingRepository;
    @InjectMocks SkillRatingEventListener listener;

    @Test
    void onRated_updates_rating_avg_and_count() {
        when(ratingRepository.averageScoreBySkillId(1L)).thenReturn(4.2);
        when(ratingRepository.countBySkillId(1L)).thenReturn(10);
        listener.onRated(new SkillRatedEvent(1L, 10L, (short) 5));
        verify(jdbcTemplate).update(
            "UPDATE skill SET rating_avg = ?, rating_count = ? WHERE id = ?",
            4.2, 10, 1L);
    }
}
```

- [ ] **Step 4: 实现 SkillRatingEventListener**

```java
package com.iflytek.skillhub.app.listener;

import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SkillRatingEventListener {
    private final JdbcTemplate jdbcTemplate;
    private final SkillRatingRepository ratingRepository;

    public SkillRatingEventListener(JdbcTemplate jdbcTemplate, SkillRatingRepository ratingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.ratingRepository = ratingRepository;
    }

    @Async
    @TransactionalEventListener
    public void onRated(SkillRatedEvent event) {
        double avg = ratingRepository.averageScoreBySkillId(event.skillId());
        int count = ratingRepository.countBySkillId(event.skillId());
        jdbcTemplate.update(
            "UPDATE skill SET rating_avg = ?, rating_count = ? WHERE id = ?",
            avg, count, event.skillId());
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest="SkillStarEventListenerTest,SkillRatingEventListenerTest"`
预期：3 个测试全部 PASS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/listener/Skill*EventListener.java
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/listener/Skill*EventListenerTest.java
git commit -m "feat(social): add async event listeners for star_count and rating_avg"
```

### Task 4: SkillStarController 和 SkillRatingController

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/SkillStarController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/SkillRatingController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/SkillStarControllerTest.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/SkillRatingControllerTest.java`

- [ ] **Step 1: 编写 SkillStarController 测试**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.domain.social.SkillStarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillStarController.class)
class SkillStarControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean SkillStarService starService;

    @Test
    @WithMockUser
    void star_skill_returns_204() throws Exception {
        mockMvc.perform(put("/api/v1/skills/1/star"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void unstar_skill_returns_204() throws Exception {
        mockMvc.perform(delete("/api/v1/skills/1/star"))
            .andExpect(status().isNoContent());
    }

    @Test
    void star_skill_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/v1/skills/1/star"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 实现 SkillStarController**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.domain.social.SkillStarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/skills/{skillId}/star")
public class SkillStarController {
    private final SkillStarService starService;

    public SkillStarController(SkillStarService starService) {
        this.starService = starService;
    }

    @PutMapping
    public ResponseEntity<Void> star(@PathVariable Long skillId,
                                     @AuthenticationPrincipal String userId) {
        starService.star(skillId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unstar(@PathVariable Long skillId,
                                       @AuthenticationPrincipal String userId) {
        starService.unstar(skillId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Boolean> isStarred(@PathVariable Long skillId,
                                             @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(starService.isStarred(skillId, userId));
    }
}
```

- [ ] **Step 3: 编写 SkillRatingController 测试**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.domain.social.SkillRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillRatingController.class)
class SkillRatingControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean SkillRatingService ratingService;

    @Test
    @WithMockUser
    void rate_skill_returns_204() throws Exception {
        mockMvc.perform(put("/api/v1/skills/1/rating")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\": 4}"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void get_user_rating_returns_score() throws Exception {
        when(ratingService.getUserRating(1L, any())).thenReturn(Optional.of((short) 4));
        mockMvc.perform(get("/api/v1/skills/1/rating"))
            .andExpect(status().isOk());
    }

    @Test
    void rate_skill_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/v1/skills/1/rating")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\": 4}"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 4: 实现 SkillRatingController**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.domain.social.SkillRatingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/skills/{skillId}/rating")
public class SkillRatingController {
    private final SkillRatingService ratingService;

    public SkillRatingController(SkillRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PutMapping
    public ResponseEntity<Void> rate(@PathVariable Long skillId,
                                     @AuthenticationPrincipal String userId,
                                     @RequestBody Map<String, Integer> body) {
        short score = body.get("score").shortValue();
        ratingService.rate(skillId, userId, score);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<?> getUserRating(@PathVariable Long skillId,
                                           @AuthenticationPrincipal String userId) {
        Optional<Short> score = ratingService.getUserRating(skillId, userId);
        return ResponseEntity.ok(Map.of("score", score.orElse(null), "rated", score.isPresent()));
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest="SkillStarControllerTest,SkillRatingControllerTest"`
预期：6 个测试全部 PASS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/SkillStar*
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/SkillRating*
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/SkillStar*
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/SkillRating*
git commit -m "feat(social): add SkillStar and SkillRating controllers"
```

### Task 5: 前端审核中心

#### 5.1 创建审核列表 Hook

**文件：** `web/src/features/review/use-review-list.ts`

```typescript
import { useQuery } from '@tanstack/react-query'

export interface ReviewTask {
  id: number
  skillVersionId: number
  skillName: string
  skillSlug: string
  namespace: string
  version: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  comment?: string
}

export function useReviewList(status?: string) {
  return useQuery({
    queryKey: ['reviews', status],
    queryFn: async () => {
      const url = status 
        ? `/api/v1/reviews?status=${status}`
        : '/api/v1/reviews'
      const res = await fetch(url, {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json.tasks as ReviewTask[]
    },
  })
}
```

**验收：**
- [ ] useReviewList hook 创建完成
- [ ] 支持按状态筛选
- [ ] 返回 ReviewTask 数组

#### 5.2 创建审核详情 Hook

**文件：** `web/src/features/review/use-review-detail.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { ReviewTask } from './use-review-list'

export function useReviewDetail(taskId: number) {
  return useQuery({
    queryKey: ['review', taskId],
    queryFn: async () => {
      const res = await fetch(`/api/v1/reviews/${taskId}`, {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as ReviewTask
    },
    enabled: !!taskId,
  })
}

export function useApproveReview() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: async ({ taskId, comment }: { taskId: number; comment?: string }) => {
      const res = await fetch(`/api/v1/reviews/${taskId}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ comment }),
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })
}

export function useRejectReview() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: async ({ taskId, comment }: { taskId: number; comment: string }) => {
      const res = await fetch(`/api/v1/reviews/${taskId}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ comment }),
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })
}
```

**验收：**
- [ ] useReviewDetail hook 创建完成
- [ ] useApproveReview mutation 创建完成
- [ ] useRejectReview mutation 创建完成
- [ ] 审核操作后刷新列表

#### 5.3 创建审核列表页面

**文件：** `web/src/pages/dashboard/reviews.tsx`

```typescript
import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useReviewList } from '@/features/review/use-review-list'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'

export function ReviewsPage() {
  const [status, setStatus] = useState<string>('PENDING')
  const { data: reviews, isLoading } = useReviewList(status)

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">审核中心</h1>
        <p className="text-muted-foreground">管理技能发布审核</p>
      </div>

      <Tabs value={status} onValueChange={setStatus}>
        <TabsList>
          <TabsTrigger value="PENDING">待审核</TabsTrigger>
          <TabsTrigger value="APPROVED">已通过</TabsTrigger>
          <TabsTrigger value="REJECTED">已拒绝</TabsTrigger>
        </TabsList>

        <TabsContent value={status} className="mt-4">
          {reviews && reviews.length > 0 ? (
            <div className="space-y-4">
              {reviews.map((review) => (
                <Card key={review.id} className="p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-semibold">
                        {review.namespace}/{review.skillSlug}@{review.version}
                      </h3>
                      <p className="text-sm text-muted-foreground">
                        提交者: {review.submittedBy} · {new Date(review.submittedAt).toLocaleString('zh-CN')}
                      </p>
                      {review.reviewedBy && (
                        <p className="text-sm text-muted-foreground">
                          审核者: {review.reviewedBy} · {new Date(review.reviewedAt!).toLocaleString('zh-CN')}
                        </p>
                      )}
                    </div>
                    <Link to={`/dashboard/reviews/${review.id}`}>
                      <Button variant="outline">查看详情</Button>
                    </Link>
                  </div>
                </Card>
              ))}
            </div>
          ) : (
            <Card className="p-6 text-center text-muted-foreground">
              暂无审核任务
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
```

**验收：**
- [ ] 审核列表页面创建完成
- [ ] 支持按状态切换（待审核/已通过/已拒绝）
- [ ] 显示技能名称、版本、提交者、提交时间
- [ ] 点击查看详情跳转到审核详情页

#### 5.4 创建审核详情页面

**文件：** `web/src/pages/dashboard/reviews/[id].tsx`

```typescript
import { useState } from 'react'
import { useParams, useNavigate } from '@tanstack/react-router'
import { useReviewDetail, useApproveReview, useRejectReview } from '@/features/review/use-review-detail'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'

export function ReviewDetailPage() {
  const { id } = useParams({ from: '/dashboard/reviews/$id' })
  const navigate = useNavigate()
  const [comment, setComment] = useState('')
  
  const { data: review, isLoading } = useReviewDetail(Number(id))
  const approveMutation = useApproveReview()
  const rejectMutation = useRejectReview()

  const handleApprove = async () => {
    try {
      await approveMutation.mutateAsync({ taskId: Number(id), comment })
      alert('审核通过')
      navigate({ to: '/dashboard/reviews' })
    } catch (error) {
      alert('操作失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  const handleReject = async () => {
    if (!comment.trim()) {
      alert('拒绝时必须填写原因')
      return
    }
    try {
      await rejectMutation.mutateAsync({ taskId: Number(id), comment })
      alert('审核拒绝')
      navigate({ to: '/dashboard/reviews' })
    } catch (error) {
      alert('操作失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  if (!review) {
    return <div>审核任务不存在</div>
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">审核详情</h1>
        <p className="text-muted-foreground">
          {review.namespace}/{review.skillSlug}@{review.version}
        </p>
      </div>

      <Card className="p-6 space-y-4">
        <div>
          <div className="text-sm text-muted-foreground mb-1">状态</div>
          <div className="font-semibold">{review.status}</div>
        </div>

        <div>
          <div className="text-sm text-muted-foreground mb-1">提交者</div>
          <div>{review.submittedBy}</div>
        </div>

        <div>
          <div className="text-sm text-muted-foreground mb-1">提交时间</div>
          <div>{new Date(review.submittedAt).toLocaleString('zh-CN')}</div>
        </div>

        {review.reviewedBy && (
          <>
            <div>
              <div className="text-sm text-muted-foreground mb-1">审核者</div>
              <div>{review.reviewedBy}</div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground mb-1">审核时间</div>
              <div>{new Date(review.reviewedAt!).toLocaleString('zh-CN')}</div>
            </div>
          </>
        )}

        {review.comment && (
          <div>
            <div className="text-sm text-muted-foreground mb-1">审核意见</div>
            <div className="whitespace-pre-wrap">{review.comment}</div>
          </div>
        )}
      </Card>

      {review.status === 'PENDING' && (
        <Card className="p-6 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="comment">审核意见</Label>
            <Textarea
              id="comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="填写审核意见（拒绝时必填）"
              rows={4}
            />
          </div>

          <div className="flex gap-4">
            <Button
              className="flex-1"
              onClick={handleApprove}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              通过
            </Button>
            <Button
              className="flex-1"
              variant="destructive"
              onClick={handleReject}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              拒绝
            </Button>
          </div>
        </Card>
      )}
    </div>
  )
}
```

**验收：**
- [ ] 审核详情页面创建完成
- [ ] 显示审核任务详细信息
- [ ] 待审核状态显示审核操作按钮
- [ ] 支持通过/拒绝操作
- [ ] 拒绝时必须填写原因

#### 5.5 创建我的提交页面

**文件：** `web/src/pages/dashboard/my-submissions.tsx`

```typescript
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import type { ReviewTask } from '@/features/review/use-review-list'

export function MySubmissionsPage() {
  const { data: submissions, isLoading } = useQuery({
    queryKey: ['my-submissions'],
    queryFn: async () => {
      const res = await fetch('/api/v1/reviews/my-submissions', {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json.tasks as ReviewTask[]
    },
  })

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">我的提交</h1>
        <p className="text-muted-foreground">查看我提交的审核任务</p>
      </div>

      {submissions && submissions.length > 0 ? (
        <div className="space-y-4">
          {submissions.map((submission) => (
            <Card key={submission.id} className="p-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="font-semibold">
                    {submission.namespace}/{submission.skillSlug}@{submission.version}
                  </h3>
                  <p className="text-sm text-muted-foreground">
                    状态: {submission.status} · 提交时间: {new Date(submission.submittedAt).toLocaleString('zh-CN')}
                  </p>
                  {submission.comment && (
                    <p className="text-sm text-muted-foreground mt-1">
                      审核意见: {submission.comment}
                    </p>
                  )}
                </div>
                <Link to={`/@${submission.namespace}/${submission.skillSlug}`}>
                  <Button variant="outline">查看技能</Button>
                </Link>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center text-muted-foreground">
          暂无提交记录
        </Card>
      )}
    </div>
  )
}
```

**验收：**
- [ ] 我的提交页面创建完成
- [ ] 显示当前用户提交的所有审核任务
- [ ] 显示状态、提交时间、审核意见
- [ ] 点击查看技能跳转到技能详情页

---

### Task 6: 前端提升页面

#### 6.1 创建提升列表 Hook

**文件：** `web/src/features/promotion/use-promotion-list.ts`

```typescript
import { useQuery } from '@tanstack/react-query'

export interface PromotionTask {
  id: number
  skillId: number
  skillName: string
  skillSlug: string
  currentNamespace: string
  targetNamespace: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedBy: string
  requestedAt: string
  reviewedBy?: string
  reviewedAt?: string
  comment?: string
}

export function usePromotionList(status?: string) {
  return useQuery({
    queryKey: ['promotions', status],
    queryFn: async () => {
      const url = status 
        ? `/api/v1/promotions?status=${status}`
        : '/api/v1/promotions'
      const res = await fetch(url, {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json.tasks as PromotionTask[]
    },
  })
}
```

**验收：**
- [ ] usePromotionList hook 创建完成
- [ ] 支持按状态筛选
- [ ] 返回 PromotionTask 数组

#### 6.2 创建提升详情 Hook

**文件：** `web/src/features/promotion/use-promotion-detail.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { PromotionTask } from './use-promotion-list'

export function usePromotionDetail(taskId: number) {
  return useQuery({
    queryKey: ['promotion', taskId],
    queryFn: async () => {
      const res = await fetch(`/api/v1/promotions/${taskId}`, {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as PromotionTask
    },
    enabled: !!taskId,
  })
}

export function useApprovePromotion() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: async ({ taskId, comment }: { taskId: number; comment?: string }) => {
      const res = await fetch(`/api/v1/promotions/${taskId}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ comment }),
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
    },
  })
}

export function useRejectPromotion() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: async ({ taskId, comment }: { taskId: number; comment: string }) => {
      const res = await fetch(`/api/v1/promotions/${taskId}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ comment }),
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
    },
  })
}
```

**验收：**
- [ ] usePromotionDetail hook 创建完成
- [ ] useApprovePromotion mutation 创建完成
- [ ] useRejectPromotion mutation 创建完成
- [ ] 审核操作后刷新列表

#### 6.3 创建提升列表页面

**文件：** `web/src/pages/dashboard/promotions.tsx`

```typescript
import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { usePromotionList } from '@/features/promotion/use-promotion-list'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'

export function PromotionsPage() {
  const [status, setStatus] = useState<string>('PENDING')
  const { data: promotions, isLoading } = usePromotionList(status)

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">提升审核</h1>
        <p className="text-muted-foreground">管理技能提升申请</p>
      </div>

      <Tabs value={status} onValueChange={setStatus}>
        <TabsList>
          <TabsTrigger value="PENDING">待审核</TabsTrigger>
          <TabsTrigger value="APPROVED">已通过</TabsTrigger>
          <TabsTrigger value="REJECTED">已拒绝</TabsTrigger>
        </TabsList>

        <TabsContent value={status} className="mt-4">
          {promotions && promotions.length > 0 ? (
            <div className="space-y-4">
              {promotions.map((promotion) => (
                <Card key={promotion.id} className="p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-semibold">
                        {promotion.currentNamespace}/{promotion.skillSlug} → {promotion.targetNamespace}
                      </h3>
                      <p className="text-sm text-muted-foreground">
                        申请者: {promotion.requestedBy} · {new Date(promotion.requestedAt).toLocaleString('zh-CN')}
                      </p>
                      {promotion.reviewedBy && (
                        <p className="text-sm text-muted-foreground">
                          审核者: {promotion.reviewedBy} · {new Date(promotion.reviewedAt!).toLocaleString('zh-CN')}
                        </p>
                      )}
                    </div>
                    <Link to={`/dashboard/promotions/${promotion.id}`}>
                      <Button variant="outline">查看详情</Button>
                    </Link>
                  </div>
                </Card>
              ))}
            </div>
          ) : (
            <Card className="p-6 text-center text-muted-foreground">
              暂无提升申请
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
```

**验收：**
- [ ] 提升列表页面创建完成
- [ ] 支持按状态切换（待审核/已通过/已拒绝）
- [ ] 显示技能名称、当前空间、目标空间、申请者、申请时间
- [ ] 点击查看详情跳转到提升详情页

#### 6.4 创建提升详情页面

**文件：** `web/src/pages/dashboard/promotions/[id].tsx`

```typescript
import { useState } from 'react'
import { useParams, useNavigate } from '@tanstack/react-router'
import { usePromotionDetail, useApprovePromotion, useRejectPromotion } from '@/features/promotion/use-promotion-detail'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'

export function PromotionDetailPage() {
  const { id } = useParams({ from: '/dashboard/promotions/$id' })
  const navigate = useNavigate()
  const [comment, setComment] = useState('')
  
  const { data: promotion, isLoading } = usePromotionDetail(Number(id))
  const approveMutation = useApprovePromotion()
  const rejectMutation = useRejectPromotion()

  const handleApprove = async () => {
    try {
      await approveMutation.mutateAsync({ taskId: Number(id), comment })
      alert('提升通过')
      navigate({ to: '/dashboard/promotions' })
    } catch (error) {
      alert('操作失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  const handleReject = async () => {
    if (!comment.trim()) {
      alert('拒绝时必须填写原因')
      return
    }
    try {
      await rejectMutation.mutateAsync({ taskId: Number(id), comment })
      alert('提升拒绝')
      navigate({ to: '/dashboard/promotions' })
    } catch (error) {
      alert('操作失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  if (!promotion) {
    return <div>提升申请不存在</div>
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">提升详情</h1>
        <p className="text-muted-foreground">
          {promotion.currentNamespace}/{promotion.skillSlug} → {promotion.targetNamespace}
        </p>
      </div>

      <Card className="p-6 space-y-4">
        <div>
          <div className="text-sm text-muted-foreground mb-1">状态</div>
          <div className="font-semibold">{promotion.status}</div>
        </div>

        <div>
          <div className="text-sm text-muted-foreground mb-1">申请者</div>
          <div>{promotion.requestedBy}</div>
        </div>

        <div>
          <div className="text-sm text-muted-foreground mb-1">申请时间</div>
          <div>{new Date(promotion.requestedAt).toLocaleString('zh-CN')}</div>
        </div>

        {promotion.reviewedBy && (
          <>
            <div>
              <div className="text-sm text-muted-foreground mb-1">审核者</div>
              <div>{promotion.reviewedBy}</div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground mb-1">审核时间</div>
              <div>{new Date(promotion.reviewedAt!).toLocaleString('zh-CN')}</div>
            </div>
          </>
        )}

        {promotion.comment && (
          <div>
            <div className="text-sm text-muted-foreground mb-1">审核意见</div>
            <div className="whitespace-pre-wrap">{promotion.comment}</div>
          </div>
        )}
      </Card>

      {promotion.status === 'PENDING' && (
        <Card className="p-6 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="comment">审核意见</Label>
            <Textarea
              id="comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="填写审核意见（拒绝时必填）"
              rows={4}
            />
          </div>

          <div className="flex gap-4">
            <Button
              className="flex-1"
              onClick={handleApprove}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              通过
            </Button>
            <Button
              className="flex-1"
              variant="destructive"
              onClick={handleReject}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              拒绝
            </Button>
          </div>
        </Card>
      )}
    </div>
  )
}
```

**验收：**
- [ ] 提升详情页面创建完成
- [ ] 显示提升申请详细信息
- [ ] 待审核状态显示审核操作按钮
- [ ] 支持通过/拒绝操作
- [ ] 拒绝时必须填写原因

---

### Task 7: 前端收藏评分组件

#### 7.1 创建收藏按钮组件

**文件：** `web/src/features/skill/star-button.tsx`

```typescript
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/shared/ui/button'

interface StarButtonProps {
  skillId: number
}

export function StarButton({ skillId }: StarButtonProps) {
  const queryClient = useQueryClient()
  
  const { data: starData } = useQuery({
    queryKey: ['skill-star', skillId],
    queryFn: async () => {
      const res = await fetch(`/api/v1/skills/${skillId}/star`, {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as { starred: boolean }
    },
  })

  const starMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`/api/v1/skills/${skillId}/star`, {
        method: 'POST',
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-star', skillId] })
      queryClient.invalidateQueries({ queryKey: ['skill', skillId] })
    },
  })

  const unstarMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`/api/v1/skills/${skillId}/star`, {
        method: 'DELETE',
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-star', skillId] })
      queryClient.invalidateQueries({ queryKey: ['skill', skillId] })
    },
  })

  const handleClick = () => {
    if (starData?.starred) {
      unstarMutation.mutate()
    } else {
      starMutation.mutate()
    }
  }

  const isLoading = starMutation.isPending || unstarMutation.isPending

  return (
    <Button
      variant={starData?.starred ? 'default' : 'outline'}
      onClick={handleClick}
      disabled={isLoading}
      className="w-full"
    >
      <svg
        className="w-4 h-4 mr-2"
        fill={starData?.starred ? 'currentColor' : 'none'}
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z"
        />
      </svg>
      {starData?.starred ? '已收藏' : '收藏'}
    </Button>
  )
}
```

**验收：**
- [ ] StarButton 组件创建完成
- [ ] 显示收藏/已收藏状态
- [ ] 点击切换收藏状态
- [ ] 使用自定义 SVG 星星图标

#### 7.2 创建评分组件

**文件：** `web/src/features/skill/star-rating.tsx`

```typescript
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

interface StarRatingProps {
  skillId: number
}

export function StarRating({ skillId }: StarRatingProps) {
  const queryClient = useQueryClient()
  const [hoverRating, setHoverRating] = useState(0)
  
  const { data: ratingData } = useQuery({
    queryKey: ['skill-rating', skillId],
    queryFn: async () => {
      const res = await fetch(`/api/v1/skills/${skillId}/rating`, {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as { score?: number }
    },
  })

  const rateMutation = useMutation({
    mutationFn: async (score: number) => {
      const res = await fetch(`/api/v1/skills/${skillId}/rating`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ score }),
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-rating', skillId] })
      queryClient.invalidateQueries({ queryKey: ['skill', skillId] })
    },
  })

  const handleClick = (score: number) => {
    rateMutation.mutate(score)
  }

  const currentRating = ratingData?.score || 0

  return (
    <div className="space-y-2">
      <div className="text-sm font-medium">评分</div>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            key={star}
            type="button"
            onClick={() => handleClick(star)}
            onMouseEnter={() => setHoverRating(star)}
            onMouseLeave={() => setHoverRating(0)}
            disabled={rateMutation.isPending}
            className="focus:outline-none transition-transform hover:scale-110"
          >
            <svg
              className="w-6 h-6"
              fill={star <= (hoverRating || currentRating) ? 'currentColor' : 'none'}
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z"
              />
            </svg>
          </button>
        ))}
      </div>
      {currentRating > 0 && (
        <div className="text-sm text-muted-foreground">
          你的评分: {currentRating} 星
        </div>
      )}
    </div>
  )
}
```

**验收：**
- [ ] StarRating 组件创建完成
- [ ] 显示 1-5 星评分
- [ ] 支持鼠标悬停预览
- [ ] 点击提交评分
- [ ] 显示当前用户评分
- [ ] 使用自定义 SVG 星星图标

#### 7.3 集成到技能详情页

**文件：** `web/src/pages/skill-detail.tsx`（修改）

在 Sidebar 部分添加收藏和评分组件：

```typescript
import { StarButton } from '@/features/skill/star-button'
import { StarRating } from '@/features/skill/star-rating'

// 在 Sidebar 的 Card 中添加：
<Card className="p-4 space-y-4">
  <div>
    <div className="text-sm text-muted-foreground mb-1">版本</div>
    <div className="font-semibold">
      {skill.latestVersion ? `v${skill.latestVersion}` : '暂无版本'}
    </div>
  </div>

  <div>
    <div className="text-sm text-muted-foreground mb-1">下载量</div>
    <div className="font-semibold">{skill.downloadCount}</div>
  </div>

  <div>
    <div className="text-sm text-muted-foreground mb-1">收藏数</div>
    <div className="font-semibold">{skill.starCount || 0}</div>
  </div>

  <div>
    <div className="text-sm text-muted-foreground mb-1">平均评分</div>
    <div className="font-semibold">
      {skill.averageRating ? skill.averageRating.toFixed(1) : '暂无评分'} 
      {skill.ratingCount > 0 && ` (${skill.ratingCount} 人评分)`}
    </div>
  </div>

  <div>
    <div className="text-sm text-muted-foreground mb-1">命名空间</div>
    <NamespaceBadge type="GLOBAL" name={namespace} />
  </div>
</Card>

<StarButton skillId={skill.id} />

<Card className="p-4">
  <StarRating skillId={skill.id} />
</Card>
```

**验收：**
- [ ] 技能详情页集成收藏按钮
- [ ] 技能详情页集成评分组件
- [ ] 显示收藏数、平均评分、评分人数

#### 7.4 创建我的收藏页面

**文件：** `web/src/pages/dashboard/my-stars.tsx`

```typescript
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'

interface Skill {
  id: number
  namespace: string
  slug: string
  displayName: string
  summary?: string
  latestVersion?: string
  downloadCount: number
  starCount: number
  averageRating?: number
}

export function MyStarsPage() {
  const { data: starredSkillIds, isLoading: isLoadingIds } = useQuery({
    queryKey: ['starred-skills'],
    queryFn: async () => {
      const res = await fetch('/api/v1/skills/starred', {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json.skillIds as number[]
    },
  })

  const { data: skills, isLoading: isLoadingSkills } = useQuery({
    queryKey: ['starred-skills-details', starredSkillIds],
    queryFn: async () => {
      if (!starredSkillIds || starredSkillIds.length === 0) {
        return []
      }
      const promises = starredSkillIds.map(async (id) => {
        const res = await fetch(`/api/v1/skills/${id}`, {
          credentials: 'include',
        })
        if (!res.ok) {
          return null
        }
        return res.json() as Promise<Skill>
      })
      const results = await Promise.all(promises)
      return results.filter((s): s is Skill => s !== null)
    },
    enabled: !!starredSkillIds && starredSkillIds.length > 0,
  })

  if (isLoadingIds || isLoadingSkills) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">我的收藏</h1>
        <p className="text-muted-foreground">查看我收藏的技能</p>
      </div>

      {skills && skills.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {skills.map((skill) => (
            <Card key={skill.id} className="p-4 space-y-3">
              <div>
                <h3 className="font-semibold">{skill.displayName}</h3>
                <p className="text-sm text-muted-foreground">
                  @{skill.namespace}/{skill.slug}
                </p>
              </div>
              {skill.summary && (
                <p className="text-sm text-muted-foreground line-clamp-2">
                  {skill.summary}
                </p>
              )}
              <div className="flex items-center gap-4 text-sm text-muted-foreground">
                <span>{skill.downloadCount} 下载</span>
                <span>{skill.starCount} 收藏</span>
                {skill.averageRating && (
                  <span>{skill.averageRating.toFixed(1)} ⭐</span>
                )}
              </div>
              <Link to={`/@${skill.namespace}/${skill.slug}`}>
                <Button variant="outline" className="w-full">
                  查看详情
                </Button>
              </Link>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center text-muted-foreground">
          暂无收藏
        </Card>
      )}
    </div>
  )
}
```

**验收：**
- [ ] 我的收藏页面创建完成
- [ ] 显示用户收藏的所有技能
- [ ] 显示技能名称、命名空间、摘要、下载量、收藏数、评分
- [ ] 点击查看详情跳转到技能详情页

---

### Task 8: 前端 Token 管理

#### 8.1 创建 Token 列表 Hook

**文件：** `web/src/features/token/use-token-list.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'

export interface ApiToken {
  id: number
  name: string
  token: string
  expiresAt?: string
  createdAt: string
  lastUsedAt?: string
}

export function useTokenList() {
  return useQuery({
    queryKey: ['api-tokens'],
    queryFn: async () => {
      const res = await fetch('/api/v1/tokens', {
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json.tokens as ApiToken[]
    },
  })
}

export function useCreateToken() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: async ({ name, expiresAt }: { name: string; expiresAt?: string }) => {
      const res = await fetch('/api/v1/tokens', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ name, expiresAt }),
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json() as Promise<ApiToken>
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['api-tokens'] })
    },
  })
}

export function useRevokeToken() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: async (tokenId: number) => {
      const res = await fetch(`/api/v1/tokens/${tokenId}`, {
        method: 'DELETE',
        credentials: 'include',
      })
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['api-tokens'] })
    },
  })
}
```

**验收：**
- [ ] useTokenList hook 创建完成
- [ ] useCreateToken mutation 创建完成
- [ ] useRevokeToken mutation 创建完成
- [ ] 操作后刷新列表

#### 8.2 创建 Token 列表页面

**文件：** `web/src/pages/dashboard/tokens.tsx`

```typescript
import { useState } from 'react'
import { useTokenList, useRevokeToken } from '@/features/token/use-token-list'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { CreateTokenDialog } from '@/features/token/create-token-dialog'

export function TokensPage() {
  const [showCreateDialog, setShowCreateDialog] = useState(false)
  const { data: tokens, isLoading } = useTokenList()
  const revokeMutation = useRevokeToken()

  const handleRevoke = async (tokenId: number, tokenName: string) => {
    if (!confirm(`确定要撤销 Token "${tokenName}" 吗？`)) {
      return
    }
    try {
      await revokeMutation.mutateAsync(tokenId)
      alert('Token 已撤销')
    } catch (error) {
      alert('撤销失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold mb-2">API Tokens</h1>
          <p className="text-muted-foreground">管理你的 API 访问令牌</p>
        </div>
        <Button onClick={() => setShowCreateDialog(true)}>
          创建 Token
        </Button>
      </div>

      {tokens && tokens.length > 0 ? (
        <div className="space-y-4">
          {tokens.map((token) => (
            <Card key={token.id} className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex-1">
                  <h3 className="font-semibold">{token.name}</h3>
                  <p className="text-sm text-muted-foreground font-mono">
                    {token.token}
                  </p>
                  <div className="text-sm text-muted-foreground mt-2">
                    创建时间: {new Date(token.createdAt).toLocaleString('zh-CN')}
                    {token.expiresAt && (
                      <> · 过期时间: {new Date(token.expiresAt).toLocaleString('zh-CN')}</>
                    )}
                    {token.lastUsedAt && (
                      <> · 最后使用: {new Date(token.lastUsedAt).toLocaleString('zh-CN')}</>
                    )}
                  </div>
                </div>
                <Button
                  variant="destructive"
                  onClick={() => handleRevoke(token.id, token.name)}
                  disabled={revokeMutation.isPending}
                >
                  撤销
                </Button>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="p-6 text-center text-muted-foreground">
          暂无 Token
        </Card>
      )}

      <CreateTokenDialog
        open={showCreateDialog}
        onClose={() => setShowCreateDialog(false)}
      />
    </div>
  )
}
```

**验收：**
- [ ] Token 列表页面创建完成
- [ ] 显示所有 Token 及其详细信息
- [ ] 支持创建和撤销 Token
- [ ] 显示创建时间、过期时间、最后使用时间

#### 8.3 创建 Token 创建对话框

**文件：** `web/src/features/token/create-token-dialog.tsx`

```typescript
import { useState } from 'react'
import { useCreateToken } from './use-token-list'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

interface CreateTokenDialogProps {
  open: boolean
  onClose: () => void
}

export function CreateTokenDialog({ open, onClose }: CreateTokenDialogProps) {
  const [name, setName] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [createdToken, setCreatedToken] = useState<string | null>(null)
  
  const createMutation = useCreateToken()

  const handleCreate = async () => {
    if (!name.trim()) {
      alert('请输入 Token 名称')
      return
    }
    try {
      const result = await createMutation.mutateAsync({
        name,
        expiresAt: expiresAt || undefined,
      })
      setCreatedToken(result.token)
      setName('')
      setExpiresAt('')
    } catch (error) {
      alert('创建失败: ' + (error instanceof Error ? error.message : '未知错误'))
    }
  }

  const handleClose = () => {
    setCreatedToken(null)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>创建 API Token</DialogTitle>
        </DialogHeader>

        {createdToken ? (
          <div className="space-y-4">
            <div className="p-4 bg-muted rounded-md">
              <p className="text-sm text-muted-foreground mb-2">
                请妥善保存你的 Token，关闭后将无法再次查看：
              </p>
              <p className="font-mono text-sm break-all">{createdToken}</p>
            </div>
            <Button className="w-full" onClick={handleClose}>
              关闭
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">Token 名称</Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例如: CLI Token"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="expiresAt">过期时间（可选）</Label>
              <Input
                id="expiresAt"
                type="datetime-local"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
              />
            </div>

            <div className="flex gap-4">
              <Button
                className="flex-1"
                onClick={handleCreate}
                disabled={createMutation.isPending}
              >
                创建
              </Button>
              <Button
                className="flex-1"
                variant="outline"
                onClick={handleClose}
              >
                取消
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
```

**验收：**
- [ ] 创建 Token 对话框创建完成
- [ ] 支持输入 Token 名称和过期时间
- [ ] 创建成功后显示 Token（仅一次）
- [ ] 提示用户妥善保存 Token

---

### Task 9: 路由更新 + Chunk 2 验收

#### 9.1 更新路由配置

**文件：** `web/src/router.tsx`（或路由配置文件）

添加以下路由：

```typescript
// 审核中心
{
  path: '/dashboard/reviews',
  component: ReviewsPage,
},
{
  path: '/dashboard/reviews/:id',
  component: ReviewDetailPage,
},
{
  path: '/dashboard/my-submissions',
  component: MySubmissionsPage,
},

// 提升审核
{
  path: '/dashboard/promotions',
  component: PromotionsPage,
},
{
  path: '/dashboard/promotions/:id',
  component: PromotionDetailPage,
},

// Token 管理
{
  path: '/dashboard/tokens',
  component: TokensPage,
},

// 我的收藏
{
  path: '/dashboard/my-stars',
  component: MyStarsPage,
},
```

**验收：**
- [ ] 所有新页面路由已添加
- [ ] 路由参数正确配置

#### 9.2 更新导航菜单

**文件：** `web/src/layouts/dashboard-layout.tsx`（或导航组件）

在 Dashboard 导航菜单中添加：

```typescript
<nav>
  <Link to="/dashboard/skills">我的技能</Link>
  <Link to="/dashboard/publish">发布技能</Link>
  <Link to="/dashboard/my-submissions">我的提交</Link>
  <Link to="/dashboard/my-stars">我的收藏</Link>
  <Link to="/dashboard/reviews">审核中心</Link>
  <Link to="/dashboard/promotions">提升审核</Link>
  <Link to="/dashboard/tokens">API Tokens</Link>
</nav>
```

**验收：**
- [ ] 导航菜单包含所有新页面链接
- [ ] 链接正确跳转

#### 9.3 Chunk 2 验收测试

**验收清单：**

**后端 API：**
- [ ] POST /api/v1/skills/{skillId}/star - 收藏技能
- [ ] DELETE /api/v1/skills/{skillId}/star - 取消收藏
- [ ] GET /api/v1/skills/{skillId}/star - 查询收藏状态
- [ ] GET /api/v1/skills/starred - 获取收藏列表
- [ ] POST /api/v1/skills/{skillId}/rating - 评分
- [ ] GET /api/v1/skills/{skillId}/rating - 获取用户评分

**前端页面：**
- [ ] /dashboard/reviews - 审核列表页
- [ ] /dashboard/reviews/:id - 审核详情页
- [ ] /dashboard/my-submissions - 我的提交页
- [ ] /dashboard/promotions - 提升列表页
- [ ] /dashboard/promotions/:id - 提升详情页
- [ ] /dashboard/tokens - Token 管理页
- [ ] /dashboard/my-stars - 我的收藏页

**功能测试：**
- [ ] 用户可以收藏/取消收藏技能
- [ ] 用户可以对技能评分（1-5 星）
- [ ] 技能详情页显示收藏数、平均评分、评分人数
- [ ] 审核中心可以查看待审核任务
- [ ] 审核中心可以通过/拒绝审核
- [ ] 我的提交页显示用户提交的审核任务
- [ ] 提升审核页可以查看提升申请
- [ ] 提升审核页可以通过/拒绝提升
- [ ] Token 管理页可以创建/撤销 Token
- [ ] 我的收藏页显示用户收藏的技能

**数据一致性：**
- [ ] 收藏/取消收藏后，Skill 的 starCount 正确更新
- [ ] 评分后，Skill 的 ratingCount 和 averageRating 正确更新
- [ ] 使用 Redis 分布式锁防止评分并发重复计算

---

## Chunk 2 完成标志

- [ ] 所有 Task 11-19 验收项通过
- [ ] 后端 API 测试通过
- [ ] 前端页面功能正常
- [ ] 数据一致性验证通过
- [ ] 代码审查通过
- [ ] 文档更新完成

**下一步：** 进入 Chunk 3（CLI API + Device Flow）

EOF

---


## Chunk 3: CLI API + Web 授权

**范围：** OAuth Device Flow + CLI API 端点

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

### Task 1: Device Flow 数据模型和 Redis 存储

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceCodeData.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceCodeStatus.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceCodeResponse.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceTokenResponse.java`

- [ ] **Step 1: 创建 DeviceCodeStatus 枚举**

```java
package com.iflytek.skillhub.auth.device;

public enum DeviceCodeStatus {
    PENDING,
    AUTHORIZED,
    USED
}
```

- [ ] **Step 2: 创建 DeviceCodeData**

```java
package com.iflytek.skillhub.auth.device;

import java.io.Serializable;

public class DeviceCodeData implements Serializable {
    private String deviceCode;
    private String userCode;
    private DeviceCodeStatus status;
    private String userId;

    public DeviceCodeData() {}

    public DeviceCodeData(String deviceCode, String userCode,
                          DeviceCodeStatus status, String userId) {
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.status = status;
        this.userId = userId;
    }

    public String getDeviceCode() { return deviceCode; }
    public String getUserCode() { return userCode; }
    public DeviceCodeStatus getStatus() { return status; }
    public void setStatus(DeviceCodeStatus status) { this.status = status; }
    public Long getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
```

- [ ] **Step 3: 创建 DeviceCodeResponse**

```java
package com.iflytek.skillhub.auth.device;

public record DeviceCodeResponse(
    String deviceCode,
    String userCode,
    String verificationUri,
    int expiresIn,
    int interval
) {}
```

- [ ] **Step 4: 创建 DeviceTokenResponse**

```java
package com.iflytek.skillhub.auth.device;

public record DeviceTokenResponse(
    String accessToken,
    String tokenType,
    String error
) {
    public static DeviceTokenResponse pending() {
        return new DeviceTokenResponse(null, null, "authorization_pending");
    }

    public static DeviceTokenResponse success(String token) {
        return new DeviceTokenResponse(token, "Bearer", null);
    }
}
```

- [ ] **Step 5: 编译验证**

运行：`cd server && ./mvnw compile -pl skillhub-auth`
预期：编译成功

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/
git commit -m "feat(cli): add Device Flow data models"
```

### Task 2: DeviceAuthService 实现

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceAuthService.java`
- Test: `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/device/DeviceAuthServiceTest.java`

- [ ] **Step 1: 编写 DeviceAuthService 测试**

```java
package com.iflytek.skillhub.auth.device;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceAuthServiceTest {
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @InjectMocks DeviceAuthService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void generateDeviceCode_returns_valid_response() {
        DeviceCodeResponse resp = service.generateDeviceCode();
        assertThat(resp.deviceCode()).isNotBlank();
        assertThat(resp.userCode()).matches("[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(resp.expiresIn()).isEqualTo(900);
        assertThat(resp.interval()).isEqualTo(5);
        verify(valueOps, times(2)).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void pollToken_returns_pending_when_not_authorized() {
        DeviceCodeData data = new DeviceCodeData("dc", "UC", DeviceCodeStatus.PENDING, null);
        when(valueOps.get("device:code:dc")).thenReturn(data);
        DeviceTokenResponse resp = service.pollToken("dc");
        assertThat(resp.error()).isEqualTo("authorization_pending");
        assertThat(resp.accessToken()).isNull();
    }

    @Test
    void pollToken_returns_error_when_expired() {
        when(valueOps.get("device:code:dc")).thenReturn(null);
        assertThatThrownBy(() -> service.pollToken("dc"))
            .hasMessageContaining("expired");
    }

    @Test
    void authorizeDeviceCode_updates_status() {
        DeviceCodeData data = new DeviceCodeData("dc", "ABCD-1234", DeviceCodeStatus.PENDING, null);
        when(valueOps.get("device:usercode:ABCD-1234")).thenReturn("dc");
        when(valueOps.get("device:code:dc")).thenReturn(data);

        service.authorizeDeviceCode("ABCD-1234", 42L);

        assertThat(data.getStatus()).isEqualTo(DeviceCodeStatus.AUTHORIZED);
        assertThat(data.getUserId()).isEqualTo(42L);
        verify(valueOps).set(eq("device:code:dc"), eq(data), any(Duration.class));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd server && ./mvnw test -pl skillhub-auth -Dtest=DeviceAuthServiceTest`
预期：编译失败，DeviceAuthService 不存在

- [ ] **Step 3: 实现 DeviceAuthService**

```java
package com.iflytek.skillhub.auth.device;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class DeviceAuthService {
    private static final String DEVICE_CODE_PREFIX = "device:code:";
    private static final String USER_CODE_PREFIX = "device:usercode:";
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public DeviceAuthService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public DeviceCodeResponse generateDeviceCode() {
        String deviceCode = generateSecureToken();
        String userCode = generateUserFriendlyCode();

        DeviceCodeData data = new DeviceCodeData(deviceCode, userCode,
            DeviceCodeStatus.PENDING, null);

        redisTemplate.opsForValue().set(DEVICE_CODE_PREFIX + deviceCode, data, TTL);
        redisTemplate.opsForValue().set(USER_CODE_PREFIX + userCode, deviceCode, TTL);

        return new DeviceCodeResponse(deviceCode, userCode,
            "/device", 900, 5);
    }

    public void authorizeDeviceCode(String userCode, String userId) {
        String deviceCode = (String) redisTemplate.opsForValue()
            .get(USER_CODE_PREFIX + userCode);
        if (deviceCode == null) {
            throw new IllegalArgumentException("Invalid or expired user code");
        }

        DeviceCodeData data = (DeviceCodeData) redisTemplate.opsForValue()
            .get(DEVICE_CODE_PREFIX + deviceCode);
        if (data == null) {
            throw new IllegalArgumentException("Invalid or expired device code");
        }

        data.setStatus(DeviceCodeStatus.AUTHORIZED);
        data.setUserId(userId);
        redisTemplate.opsForValue().set(DEVICE_CODE_PREFIX + deviceCode, data, TTL);
    }

    public DeviceTokenResponse pollToken(String deviceCode) {
        DeviceCodeData data = (DeviceCodeData) redisTemplate.opsForValue()
            .get(DEVICE_CODE_PREFIX + deviceCode);

        if (data == null) {
            throw new IllegalArgumentException("Invalid or expired device code");
        }

        return switch (data.getStatus()) {
            case PENDING -> DeviceTokenResponse.pending();
            case AUTHORIZED -> {
                data.setStatus(DeviceCodeStatus.USED);
                redisTemplate.opsForValue().set(
                    DEVICE_CODE_PREFIX + deviceCode, data, Duration.ofMinutes(1));
                // Token 生成委托给调用方（Controller 层调用 ApiTokenService）
                yield DeviceTokenResponse.success(null);
            }
            case USED -> throw new IllegalStateException("Device code already used");
        };
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateUserFriendlyCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) code.append('-');
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return code.toString();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-auth -Dtest=DeviceAuthServiceTest`
预期：4 个测试全部 PASS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/device/DeviceAuthService.java
git add server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/device/DeviceAuthServiceTest.java
git commit -m "feat(cli): implement DeviceAuthService with Redis storage"
```

### Task 3: Device Auth Controller 层

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/DeviceAuthController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/DeviceAuthWebController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/DeviceAuthControllerTest.java`

- [ ] **Step 1: 编写 DeviceAuthController 测试**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.device.DeviceCodeResponse;
import com.iflytek.skillhub.auth.device.DeviceTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceAuthController.class)
class DeviceAuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean DeviceAuthService deviceAuthService;

    @Test
    void requestDeviceCode_returns_code() throws Exception {
        when(deviceAuthService.generateDeviceCode())
            .thenReturn(new DeviceCodeResponse("dc123", "ABCD-1234", "/device", 900, 5));

        mockMvc.perform(post("/api/v1/cli/auth/device/code"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceCode").value("dc123"))
            .andExpect(jsonPath("$.userCode").value("ABCD-1234"))
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void pollToken_returns_pending() throws Exception {
        when(deviceAuthService.pollToken("dc123"))
            .thenReturn(DeviceTokenResponse.pending());

        mockMvc.perform(post("/api/v1/cli/auth/device/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"dc123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error").value("authorization_pending"));
    }
}
```

- [ ] **Step 2: 实现 DeviceAuthController**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.device.DeviceCodeResponse;
import com.iflytek.skillhub.auth.device.DeviceTokenResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cli/auth/device")
public class DeviceAuthController {
    private final DeviceAuthService deviceAuthService;

    public DeviceAuthController(DeviceAuthService deviceAuthService) {
        this.deviceAuthService = deviceAuthService;
    }

    @PostMapping("/code")
    public DeviceCodeResponse requestDeviceCode() {
        return deviceAuthService.generateDeviceCode();
    }

    @PostMapping("/token")
    public DeviceTokenResponse pollToken(@RequestBody Map<String, String> body) {
        return deviceAuthService.pollToken(body.get("deviceCode"));
    }
}
```

- [ ] **Step 3: 实现 DeviceAuthWebController**

```java
package com.iflytek.skillhub.app.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceAuthWebController {
    private final DeviceAuthService deviceAuthService;

    public DeviceAuthWebController(DeviceAuthService deviceAuthService) {
        this.deviceAuthService = deviceAuthService;
    }

    @PostMapping("/authorize")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> authorizeDevice(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        deviceAuthService.authorizeDeviceCode(body.get("userCode"), userId);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=DeviceAuthControllerTest`
预期：2 个测试全部 PASS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/DeviceAuth*
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/DeviceAuth*
git commit -m "feat(cli): add Device Auth controllers"
```

### Task 4: CLI API 端点（whoami + resolve + check）

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/CliApiController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/CliApiControllerTest.java`

- [ ] **Step 1: 编写 CliApiController 测试**

```java
package com.iflytek.skillhub.app.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CliApiController.class)
class CliApiControllerTest {
    @Autowired MockMvc mockMvc;
    // @MockBean 各依赖服务...

    @Test
    @WithMockUser
    void whoami_returns_user_info() throws Exception {
        mockMvc.perform(get("/api/v1/cli/whoami"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void whoami_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/cli/whoami"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void resolve_returns_version_info() throws Exception {
        mockMvc.perform(get("/api/v1/cli/resolve")
                .param("skill", "@global/my-skill")
                .param("version", "latest"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 实现 CliApiController**

```java
package com.iflytek.skillhub.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cli")
public class CliApiController {

    // 注入 SkillQueryService, SkillPublishService, UserAccountRepository 等

    @GetMapping("/whoami")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> whoami(@AuthenticationPrincipal String userId) {
        // 查询用户信息 + 所属 namespace 列表
        return ResponseEntity.ok(Map.of("code", 0, "data", Map.of("userId", userId)));
    }

    @GetMapping("/resolve")
    public ResponseEntity<?> resolve(
            @RequestParam String skill,
            @RequestParam(defaultValue = "latest") String version,
            @AuthenticationPrincipal String userId) {
        // 解析 @namespace/slug 格式
        // 调用 SkillQueryService 获取版本详情
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @PostMapping("/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> check(@RequestParam("file") MultipartFile file) {
        // 解压 zip，调用 SkillPackageValidator 校验
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @PostMapping("/publish")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam String namespace,
            @RequestParam(defaultValue = "PUBLIC") String visibility,
            @AuthenticationPrincipal String userId) {
        // 调用 SkillPublishService
        return ResponseEntity.ok(Map.of("code", 0));
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=CliApiControllerTest`
预期：3 个测试全部 PASS

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/CliApiController.java
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/CliApiControllerTest.java
git commit -m "feat(cli): add CLI API endpoints (whoami, resolve, check, publish)"
```

### Task 5: 前端 Device Auth 授权页面

**Files:**
- Create: `web/src/pages/device-auth.tsx`
- Create: `web/src/features/device-auth/user-code-input.tsx`
- Create: `web/src/features/device-auth/authorize-confirm-dialog.tsx`
- Create: `web/src/features/device-auth/authorize-success.tsx`
- Create: `web/src/features/device-auth/use-authorize-device.ts`

- [ ] **Step 1: 创建 use-authorize-device hook**

```typescript
// web/src/features/device-auth/use-authorize-device.ts
import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export function useAuthorizeDevice() {
  return useMutation({
    mutationFn: (userCode: string) =>
      apiClient.post('/api/v1/device/authorize', { userCode }),
  });
}
```

- [ ] **Step 2: 创建 UserCodeInput 组件**

```tsx
// web/src/features/device-auth/user-code-input.tsx
import { useState, useRef } from 'react';
import { Input } from '@/shared/ui/input';

interface UserCodeInputProps {
  onComplete: (code: string) => void;
}

export function UserCodeInput({ onComplete }: UserCodeInputProps) {
  const [part1, setPart1] = useState('');
  const [part2, setPart2] = useState('');
  const ref2 = useRef<HTMLInputElement>(null);

  const handlePart1Change = (value: string) => {
    const clean = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4);
    setPart1(clean);
    if (clean.length === 4) ref2.current?.focus();
  };

  const handlePart2Change = (value: string) => {
    const clean = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4);
    setPart2(clean);
    if (clean.length === 4 && part1.length === 4) {
      onComplete(`${part1}-${clean}`);
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    const text = e.clipboardData.getData('text').replace(/\s/g, '');
    const match = text.match(/^([A-Z0-9]{4})-?([A-Z0-9]{4})$/i);
    if (match) {
      e.preventDefault();
      setPart1(match[1].toUpperCase());
      setPart2(match[2].toUpperCase());
      onComplete(`${match[1].toUpperCase()}-${match[2].toUpperCase()}`);
    }
  };

  return (
    <div className="flex items-center gap-2" onPaste={handlePaste}>
      <Input value={part1} onChange={e => handlePart1Change(e.target.value)}
        className="w-24 text-center text-2xl font-mono tracking-widest"
        maxLength={4} placeholder="ABCD" autoFocus />
      <span className="text-2xl font-bold">-</span>
      <Input ref={ref2} value={part2}
        onChange={e => handlePart2Change(e.target.value)}
        className="w-24 text-center text-2xl font-mono tracking-widest"
        maxLength={4} placeholder="1234" />
    </div>
  );
}
```

- [ ] **Step 3: 创建授权确认对话框和成功页面**

`authorize-confirm-dialog.tsx`:
```tsx
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter }
  from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';

interface Props {
  open: boolean;
  userCode: string;
  onConfirm: () => void;
  onCancel: () => void;
  loading: boolean;
}

export function AuthorizeConfirmDialog({ open, userCode, onConfirm, onCancel, loading }: Props) {
  return (
    <Dialog open={open} onOpenChange={v => !v && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认授权 CLI 设备</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 text-sm">
          <p>授权码：<span className="font-mono font-bold">{userCode}</span></p>
          <p>权限：读取和管理你的技能、命名空间</p>
          <p className="text-amber-600">请确认这是你正在使用的 CLI 设备</p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>取消</Button>
          <Button onClick={onConfirm} disabled={loading}>
            {loading ? '授权中...' : '确认授权'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
```

`authorize-success.tsx`:
```tsx
import { CheckCircle } from 'lucide-react';
import { Button } from '@/shared/ui/button';

export function AuthorizeSuccess() {
  return (
    <div className="text-center space-y-4">
      <CheckCircle className="mx-auto h-16 w-16 text-green-500" />
      <h2 className="text-xl font-semibold">授权成功</h2>
      <p className="text-muted-foreground">你的 CLI 设备已成功授权，请返回 CLI 继续操作</p>
      <Button variant="outline" onClick={() => window.close()}>关闭窗口</Button>
    </div>
  );
}
```

- [ ] **Step 4: 创建 Device Auth 主页面**

```tsx
// web/src/pages/device-auth.tsx
import { useState } from 'react';
import { UserCodeInput } from '@/features/device-auth/user-code-input';
import { AuthorizeConfirmDialog } from '@/features/device-auth/authorize-confirm-dialog';
import { AuthorizeSuccess } from '@/features/device-auth/authorize-success';
import { useAuthorizeDevice } from '@/features/device-auth/use-authorize-device';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';

export default function DeviceAuthPage() {
  const [userCode, setUserCode] = useState('');
  const [showConfirm, setShowConfirm] = useState(false);
  const [authorized, setAuthorized] = useState(false);
  const mutation = useAuthorizeDevice();

  const handleComplete = (code: string) => {
    setUserCode(code);
    setShowConfirm(true);
  };

  const handleConfirm = () => {
    mutation.mutate(userCode, {
      onSuccess: () => { setShowConfirm(false); setAuthorized(true); },
    });
  };

  if (authorized) return <AuthorizeSuccess />;

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>授权 CLI 设备访问</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <p className="text-sm text-muted-foreground">请输入 CLI 显示的授权码：</p>
          <UserCodeInput onComplete={handleComplete} />
          {mutation.isError && (
            <p className="text-sm text-red-500">授权码无效，请检查后重试</p>
          )}
        </CardContent>
      </Card>
      <AuthorizeConfirmDialog
        open={showConfirm} userCode={userCode}
        onConfirm={handleConfirm} onCancel={() => setShowConfirm(false)}
        loading={mutation.isPending} />
    </div>
  );
}
```

- [ ] **Step 5: 添加路由配置**

在 `web/src/router.tsx` 中添加 `/device` 路由（需要登录守卫）。

- [ ] **Step 6: Commit**

```bash
git add web/src/pages/device-auth.tsx
git add web/src/features/device-auth/
git commit -m "feat(cli): add Device Auth frontend page"
```

### Task 6: Chunk 3 验收

- [ ] **Step 1: 运行后端测试**

运行：`cd server && ./mvnw test`
预期：所有测试通过

- [ ] **Step 2: 运行前端测试**

运行：`cd web && npm test`
预期：所有测试通过

- [ ] **Step 3: 验证 9 个验收标准**

逐一验证 Chunk 3 的验收标准。

---

## Chunk 4: ClawHub 兼容层

**范围：** Canonical slug 映射 + 兼容层端点

**验收标准：**
1. ClawHub CLI 可以通过 `/.well-known/clawhub.json` 发现兼容层 API
2. ClawHub CLI 可以搜索技能，返回 canonical slug 格式
3. ClawHub CLI 可以解析技能版本（`my-skill` 和 `team-ai--my-skill`）
4. ClawHub CLI 可以下载技能包
5. ClawHub CLI 可以发布技能（需要 Token 认证）
6. ClawHub CLI 可以查询当前用户信息
7. 所有兼容层端点测试通过

### Task 1: CanonicalSlugMapper 实现

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/CanonicalSlugMapper.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/SkillCoordinate.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/compat/CanonicalSlugMapperTest.java`

- [ ] **Step 1: 编写 CanonicalSlugMapper 测试**

```java
package com.iflytek.skillhub.app.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class CanonicalSlugMapperTest {
    private final CanonicalSlugMapper mapper = new CanonicalSlugMapper();

    @ParameterizedTest
    @CsvSource({
        "global, my-skill, my-skill",
        "team-ai, my-skill, team-ai--my-skill",
        "dev-team, code-review, dev-team--code-review"
    })
    void toCanonical(String namespace, String slug, String expected) {
        assertThat(mapper.toCanonical(namespace, slug)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "my-skill, global, my-skill",
        "team-ai--my-skill, team-ai, my-skill",
        "dev-team--code-review, dev-team, code-review"
    })
    void fromCanonical(String canonical, String expectedNs, String expectedSlug) {
        SkillCoordinate coord = mapper.fromCanonical(canonical);
        assertThat(coord.namespaceSlug()).isEqualTo(expectedNs);
        assertThat(coord.skillSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void fromCanonical_no_separator_defaults_to_global() {
        SkillCoordinate coord = mapper.fromCanonical("simple-skill");
        assertThat(coord.namespaceSlug()).isEqualTo("global");
        assertThat(coord.skillSlug()).isEqualTo("simple-skill");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=CanonicalSlugMapperTest`
预期：编译失败

- [ ] **Step 3: 创建 SkillCoordinate record**

```java
package com.iflytek.skillhub.app.compat;

public record SkillCoordinate(String namespaceSlug, String skillSlug) {}
```

- [ ] **Step 4: 实现 CanonicalSlugMapper**

```java
package com.iflytek.skillhub.app.compat;

import org.springframework.stereotype.Component;

@Component
public class CanonicalSlugMapper {
    private static final String SEPARATOR = "--";

    public String toCanonical(String namespaceSlug, String skillSlug) {
        if ("global".equals(namespaceSlug)) {
            return skillSlug;
        }
        return namespaceSlug + SEPARATOR + skillSlug;
    }

    public SkillCoordinate fromCanonical(String canonicalSlug) {
        int idx = canonicalSlug.indexOf(SEPARATOR);
        if (idx == -1) {
            return new SkillCoordinate("global", canonicalSlug);
        }
        return new SkillCoordinate(
            canonicalSlug.substring(0, idx),
            canonicalSlug.substring(idx + SEPARATOR.length()));
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=CanonicalSlugMapperTest`
预期：5 个测试全部 PASS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/compat/
git commit -m "feat(compat): add CanonicalSlugMapper with tests"
```

### Task 2: Well-Known 端点 + 兼容层 DTO

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/WellKnownController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/dto/ClawHubSearchResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/dto/ClawHubSkillItem.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/dto/ClawHubResolveResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/dto/ClawHubPublishResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/dto/ClawHubWhoamiResponse.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/compat/WellKnownControllerTest.java`

- [ ] **Step 1: 编写 WellKnownController 测试**

```java
package com.iflytek.skillhub.app.compat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WellKnownController.class)
class WellKnownControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void returns_api_base() throws Exception {
        mockMvc.perform(get("/.well-known/clawhub.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiBase").value("/api/v1"));
    }
}
```

- [ ] **Step 2: 实现 WellKnownController**

```java
package com.iflytek.skillhub.app.compat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class WellKnownController {
    @GetMapping("/.well-known/clawhub.json")
    public Map<String, String> clawHubDiscovery() {
        return Map.of("apiBase", "/api/v1");
    }
}
```

- [ ] **Step 3: 创建兼容层 DTO**

```java
// ClawHubSkillItem.java
package com.iflytek.skillhub.app.compat.dto;

public record ClawHubSkillItem(
    String slug, String name, String description,
    String version, long downloads, int stars) {}

// ClawHubSearchResponse.java
package com.iflytek.skillhub.app.compat.dto;

import java.util.List;

public record ClawHubSearchResponse(
    List<ClawHubSkillItem> items, long total, int page, int size) {}

// ClawHubResolveResponse.java
package com.iflytek.skillhub.app.compat.dto;

public record ClawHubResolveResponse(
    String slug, String name, String version,
    String downloadUrl, int fileCount, long totalSize) {}

// ClawHubPublishResponse.java
package com.iflytek.skillhub.app.compat.dto;

public record ClawHubPublishResponse(String slug, String version, String status) {}

// ClawHubWhoamiResponse.java
package com.iflytek.skillhub.app.compat.dto;

public record ClawHubWhoamiResponse(String userId, String username, String email) {}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=WellKnownControllerTest`
预期：PASS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/compat/
git commit -m "feat(compat): add well-known endpoint and compat DTOs"
```

### Task 3: ClawHubCompatController 实现

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/ClawHubCompatController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/compat/ClawHubCompatControllerTest.java`

- [ ] **Step 1: 编写 ClawHubCompatController 测试**

```java
package com.iflytek.skillhub.app.compat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClawHubCompatController.class)
class ClawHubCompatControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CanonicalSlugMapper slugMapper;

    @Test
    void search_returns_compat_format() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void resolve_parses_canonical_slug() throws Exception {
        when(slugMapper.fromCanonical("my-skill"))
            .thenReturn(new SkillCoordinate("global", "my-skill"));
        mockMvc.perform(get("/api/v1/resolve")
                .param("slug", "my-skill"))
            .andExpect(status().isOk());
    }

    @Test
    void whoami_requires_auth() throws Exception {
        mockMvc.perform(get("/api/v1/whoami"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 实现 ClawHubCompatController**

```java
package com.iflytek.skillhub.app.compat;

import com.iflytek.skillhub.app.compat.dto.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ClawHubCompatController {
    private final CanonicalSlugMapper slugMapper;

    public ClawHubCompatController(CanonicalSlugMapper slugMapper) {
        this.slugMapper = slugMapper;
    }

    @GetMapping("/search")
    public ClawHubSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 调用 skillhub 搜索服务，转换为 canonical slug 格式
        // TODO: 注入 SkillSearchAppService 并调用
        return new ClawHubSearchResponse(List.of(), 0, page, size);
    }

    @GetMapping("/resolve")
    public ClawHubResolveResponse resolve(
            @RequestParam String slug,
            @RequestParam(defaultValue = "latest") String version) {
        SkillCoordinate coord = slugMapper.fromCanonical(slug);
        // TODO: 调用 SkillQueryService 获取版本详情
        return new ClawHubResolveResponse(slug, "", version,
            "/api/v1/download/" + slug + "/" + version, 0, 0);
    }

    @GetMapping("/download/{slug}/{version}")
    public ResponseEntity<Resource> download(
            @PathVariable String slug,
            @PathVariable String version) {
        SkillCoordinate coord = slugMapper.fromCanonical(slug);
        // TODO: 调用 SkillDownloadService
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/publish")
    @PreAuthorize("isAuthenticated()")
    public ClawHubPublishResponse publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "global") String namespace,
            @AuthenticationPrincipal String userId) {
        // TODO: 调用 SkillPublishService
        return new ClawHubPublishResponse("", "", "pending_review");
    }

    @GetMapping("/whoami")
    @PreAuthorize("isAuthenticated()")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal String userId) {
        // TODO: 查询用户信息
        return new ClawHubWhoamiResponse(userId, "", "");
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=ClawHubCompatControllerTest`
预期：3 个测试全部 PASS

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/compat/ClawHubCompatController.java
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/compat/ClawHubCompatControllerTest.java
git commit -m "feat(compat): add ClawHub compatibility controller"
```

### Task 4: Chunk 4 验收

- [ ] **Step 1: 运行所有测试**

运行：`cd server && ./mvnw test`
预期：所有测试通过

- [ ] **Step 2: 验证 7 个验收标准**

逐一验证 Chunk 4 的验收标准。

---

## Chunk 5: 幂等去重 + 管理后台

**范围：** 幂等拦截器 + 管理后台前端

**验收标准：**
1. 写操作带 `X-Request-Id` 时，重复请求返回原始结果
2. Redis 不可用时，PostgreSQL 兜底去重
3. 定时任务清理过期幂等记录
4. 管理后台：USER_ADMIN 可以查看用户列表，编辑角色，封禁/解封用户
5. 管理后台：AUDITOR 可以查看审计日志，筛选和搜索
6. 管理后台：SUPER_ADMIN 可以访问所有管理功能
7. 前端路由守卫：非管理员访问 `/admin` 跳转到 403 页面
8. 所有测试通过

### Task 1: IdempotencyRecord 实体和 Repository

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/idempotency/IdempotencyRecord.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/idempotency/IdempotencyStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/idempotency/IdempotencyRecordRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaIdempotencyRecordRepository.java`

- [ ] **Step 1: 创建 IdempotencyStatus 枚举**

```java
package com.iflytek.skillhub.domain.idempotency;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 2: 创建 IdempotencyRecord 实体**

```java
package com.iflytek.skillhub.domain.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    @Id
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "resource_type", length = 64, nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyStatus status;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String requestId, String resourceType,
                             IdempotencyStatus status, Instant expiresAt) {
        this.requestId = requestId;
        this.resourceType = resourceType;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public String getRequestId() { return requestId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public IdempotencyStatus getStatus() { return status; }
    public void setStatus(IdempotencyStatus status) { this.status = status; }
    public Integer getResponseStatusCode() { return responseStatusCode; }
    public void setResponseStatusCode(Integer code) { this.responseStatusCode = code; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
```

- [ ] **Step 3: 创建 Repository 接口**

```java
package com.iflytek.skillhub.domain.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository {
    IdempotencyRecord save(IdempotencyRecord record);
    Optional<IdempotencyRecord> findById(String requestId);
    int deleteExpired(Instant now);
    int markStaleAsFailed(Instant threshold);
    void updateToCompleted(String requestId, String resourceType,
                           Long resourceId, int statusCode);
}
```

- [ ] **Step 4: 实现 JPA Repository**

```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.idempotency.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface JpaIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, String>, IdempotencyRecordRepository {

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE IdempotencyRecord r SET r.status = 'FAILED'
        WHERE r.status = 'PROCESSING' AND r.createdAt < :threshold
    """)
    int markStaleAsFailed(@Param("threshold") Instant threshold);

    @Modifying
    @Query("""
        UPDATE IdempotencyRecord r
        SET r.status = 'COMPLETED', r.resourceType = :resourceType,
            r.resourceId = :resourceId, r.responseStatusCode = :statusCode
        WHERE r.requestId = :requestId
    """)
    void updateToCompleted(@Param("requestId") String requestId,
                           @Param("resourceType") String resourceType,
                           @Param("resourceId") Long resourceId,
                           @Param("statusCode") int statusCode);
}
```

- [ ] **Step 5: 编译验证**

运行：`cd server && ./mvnw compile`
预期：编译成功

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/idempotency/
git add server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaIdempotencyRecordRepository.java
git commit -m "feat(idempotency): add IdempotencyRecord entity and repository"
```

### Task 2: IdempotencyInterceptor 实现

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/interceptor/IdempotencyInterceptor.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/config/WebMvcIdempotencyConfig.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/interceptor/IdempotencyInterceptorTest.java`

- [ ] **Step 1: 编写 IdempotencyInterceptor 测试**

```java
package com.iflytek.skillhub.app.interceptor;

import com.iflytek.skillhub.domain.idempotency.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock IdempotencyRecordRepository recordRepository;
    @InjectMocks IdempotencyInterceptor interceptor;

    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void get_request_passes_through() throws Exception {
        request.setMethod("GET");
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }

    @Test
    void post_without_request_id_passes_through() throws Exception {
        request.setMethod("POST");
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }

    @Test
    void post_with_new_request_id_passes_through() throws Exception {
        request.setMethod("POST");
        request.addHeader("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
        verify(recordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    void post_with_duplicate_request_id_returns_completed_result() throws Exception {
        request.setMethod("POST");
        request.addHeader("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false);

        IdempotencyRecord record = new IdempotencyRecord(
            "550e8400-e29b-41d4-a716-446655440000", "skill_version",
            IdempotencyStatus.COMPLETED, null);
        record.setResourceId(123L);
        record.setResponseStatusCode(200);
        when(recordRepository.findById(anyString())).thenReturn(Optional.of(record));

        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void post_with_processing_request_returns_409() throws Exception {
        request.setMethod("POST");
        request.addHeader("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false);

        IdempotencyRecord record = new IdempotencyRecord(
            "550e8400-e29b-41d4-a716-446655440000", "skill_version",
            IdempotencyStatus.PROCESSING, null);
        when(recordRepository.findById(anyString())).thenReturn(Optional.of(record));

        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=IdempotencyInterceptorTest`
预期：编译失败

- [ ] **Step 3: 实现 IdempotencyInterceptor**

```java
package com.iflytek.skillhub.app.interceptor;

import com.iflytek.skillhub.domain.idempotency.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.regex.Pattern;

public class IdempotencyInterceptor implements HandlerInterceptor {
    private static final String HEADER = "X-Request-Id";
    private static final String ATTR = "idempotency.requestId";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final RedisTemplate<String, String> redisTemplate;
    private final IdempotencyRecordRepository recordRepository;

    public IdempotencyInterceptor(RedisTemplate<String, String> redisTemplate,
                                  IdempotencyRecordRepository recordRepository) {
        this.redisTemplate = redisTemplate;
        this.recordRepository = recordRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!WRITE_METHODS.contains(request.getMethod())) return true;

        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) return true;

        if (!UUID_PATTERN.matcher(requestId.toLowerCase()).matches()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid X-Request-Id format\"}");
            return false;
        }

        String redisKey = "idempotent:" + requestId;
        Boolean isNew;
        try {
            isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", Duration.ofHours(24));
        } catch (Exception e) {
            // Redis 不可用，fall through 到 PostgreSQL
            isNew = true;
        }

        if (Boolean.FALSE.equals(isNew)) {
            return handleDuplicate(requestId, response);
        }

        // 新请求，插入 PROCESSING 记录
        IdempotencyRecord record = new IdempotencyRecord(
            requestId, "unknown", IdempotencyStatus.PROCESSING,
            Instant.now().plus(24, ChronoUnit.HOURS));
        recordRepository.save(record);
        request.setAttribute(ATTR, requestId);
        return true;
    }

    private boolean handleDuplicate(String requestId, HttpServletResponse response)
            throws Exception {
        var record = recordRepository.findById(requestId).orElse(null);
        if (record == null) {
            // Redis 有但 DB 无，可能脏数据，允许重试
            redisTemplate.delete("idempotent:" + requestId);
            return true;
        }
        return switch (record.getStatus()) {
            case COMPLETED -> {
                response.setStatus(record.getResponseStatusCode());
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"code\":0,\"data\":{\"resourceType\":\"%s\",\"resourceId\":%d}}",
                    record.getResourceType(), record.getResourceId()));
                yield false;
            }
            case PROCESSING -> {
                response.setStatus(HttpStatus.CONFLICT.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Request is being processed\"}");
                yield false;
            }
            case FAILED -> {
                redisTemplate.delete("idempotent:" + requestId);
                yield true;
            }
        };
    }
}
```

- [ ] **Step 4: 注册拦截器**

```java
package com.iflytek.skillhub.app.config;

import com.iflytek.skillhub.app.interceptor.IdempotencyInterceptor;
import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcIdempotencyConfig implements WebMvcConfigurer {
    private final RedisTemplate<String, String> redisTemplate;
    private final IdempotencyRecordRepository recordRepository;

    public WebMvcIdempotencyConfig(RedisTemplate<String, String> redisTemplate,
                                   IdempotencyRecordRepository recordRepository) {
        this.redisTemplate = redisTemplate;
        this.recordRepository = recordRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(redisTemplate, recordRepository))
            .addPathPatterns("/api/**");
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=IdempotencyInterceptorTest`
预期：5 个测试全部 PASS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/interceptor/
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/config/WebMvcIdempotencyConfig.java
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/interceptor/
git commit -m "feat(idempotency): add IdempotencyInterceptor with Redis + PostgreSQL"
```

### Task 3: 幂等记录定时清理

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/task/IdempotencyCleanupTask.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/task/IdempotencyCleanupTaskTest.java`

- [ ] **Step 1: 编写清理任务测试**

```java
package com.iflytek.skillhub.app.task;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupTaskTest {
    @Mock IdempotencyRecordRepository repository;
    @InjectMocks IdempotencyCleanupTask task;

    @Test
    void cleanupExpired_deletes_old_records() {
        when(repository.deleteExpired(any(Instant.class))).thenReturn(5);
        task.cleanupExpiredRecords();
        verify(repository).deleteExpired(any(Instant.class));
    }

    @Test
    void cleanupStale_marks_processing_as_failed() {
        when(repository.markStaleAsFailed(any(Instant.class))).thenReturn(2);
        task.cleanupStaleProcessing();
        verify(repository).markStaleAsFailed(any(Instant.class));
    }
}
```

- [ ] **Step 2: 实现清理任务**

```java
package com.iflytek.skillhub.app.task;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class IdempotencyCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupTask.class);
    private final IdempotencyRecordRepository repository;

    public IdempotencyCleanupTask(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredRecords() {
        int deleted = repository.deleteExpired(Instant.now());
        log.info("Cleaned up {} expired idempotency records", deleted);
    }

    @Scheduled(fixedDelay = 300000)
    public void cleanupStaleProcessing() {
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        int updated = repository.markStaleAsFailed(threshold);
        if (updated > 0) {
            log.warn("Marked {} stale PROCESSING records as FAILED", updated);
        }
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest=IdempotencyCleanupTaskTest`
预期：2 个测试全部 PASS

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/task/
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/task/
git commit -m "feat(idempotency): add cleanup scheduled tasks"
```

### Task 4: 管理后台 API（用户管理 + 审计日志）

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/admin/UserManagementController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/admin/AuditLogController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/admin/UserManagementControllerTest.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/admin/AuditLogControllerTest.java`

- [ ] **Step 1: 编写 UserManagementController 测试**

```java
package com.iflytek.skillhub.app.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserManagementController.class)
class UserManagementControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void list_users_requires_admin_role() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER_ADMIN")
    void list_users_accessible_by_user_admin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void list_users_accessible_by_super_admin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 实现 UserManagementController**

```java
package com.iflytek.skillhub.app.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
public class UserManagementController {

    @GetMapping
    public ResponseEntity<?> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // TODO: 注入 UserAccountRepository 查询
        return ResponseEntity.ok(Map.of("items", List.of(), "total", 0));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserDetail(@PathVariable String userId) {
        // TODO: 查询用户详情 + 角色 + namespace 成员
        return ResponseEntity.ok(Map.of("userId", userId));
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable String userId,
            @RequestBody Map<String, List<String>> body) {
        // TODO: 更新用户平台角色
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        // TODO: 封禁/解封用户
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: 编写 AuditLogController 测试**

```java
package com.iflytek.skillhub.app.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void audit_logs_requires_auditor_role() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void audit_logs_accessible_by_auditor() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 4: 实现 AuditLogController**

```java
package com.iflytek.skillhub.app.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasAnyRole('AUDITOR', 'SUPER_ADMIN')")
public class AuditLogController {

    @GetMapping
    public ResponseEntity<?> listAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // TODO: 注入 AuditLogRepository 查询
        return ResponseEntity.ok(Map.of("items", List.of(), "total", 0));
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd server && ./mvnw test -pl skillhub-app -Dtest="UserManagementControllerTest,AuditLogControllerTest"`
预期：5 个测试全部 PASS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/app/controller/admin/
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/app/controller/admin/
git commit -m "feat(admin): add UserManagement and AuditLog controllers"
```

### Task 5: 管理后台前端页面

**Files:**
- Create: `web/src/pages/admin/users.tsx`
- Create: `web/src/pages/admin/user-detail.tsx`
- Create: `web/src/pages/admin/audit-logs.tsx`
- Create: `web/src/features/admin/user-table.tsx`
- Create: `web/src/features/admin/edit-roles-dialog.tsx`
- Create: `web/src/features/admin/audit-log-table.tsx`
- Create: `web/src/features/admin/use-users.ts`
- Create: `web/src/features/admin/use-audit-logs.ts`
- Create: `web/src/features/admin/use-update-user-roles.ts`

- [ ] **Step 1: 创建 admin hooks**

```typescript
// web/src/features/admin/use-users.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export function useUsers(params: { search?: string; status?: string; page: number }) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => apiClient.get('/api/v1/admin/users', { params }),
  });
}

// web/src/features/admin/use-audit-logs.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export function useAuditLogs(params: {
  action?: string; actorUserId?: string;
  startTime?: string; endTime?: string; page: number;
}) {
  return useQuery({
    queryKey: ['admin', 'audit-logs', params],
    queryFn: () => apiClient.get('/api/v1/admin/audit-logs', { params }),
  });
}

// web/src/features/admin/use-update-user-roles.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

export function useUpdateUserRoles() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, roles }: { userId: string; roles: string[] }) =>
      apiClient.put(`/api/v1/admin/users/${userId}/roles`, { roles }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });
}
```

- [ ] **Step 2: 创建用户管理页面**

```tsx
// web/src/pages/admin/users.tsx
import { useState } from 'react';
import { useUsers } from '@/features/admin/use-users';
import { Input } from '@/shared/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue }
  from '@/shared/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow }
  from '@/shared/ui/table';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Link } from '@tanstack/react-router';

export default function AdminUsersPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>();
  const [page, setPage] = useState(0);
  const { data, isLoading } = useUsers({ search, status, page });

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">用户管理</h1>
      <div className="flex gap-4">
        <Input placeholder="搜索用户名/邮箱" value={search}
          onChange={e => setSearch(e.target.value)} className="max-w-xs" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-40"><SelectValue placeholder="状态" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="ACTIVE">活跃</SelectItem>
            <SelectItem value="DISABLED">已封禁</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>用户名</TableHead>
            <TableHead>邮箱</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>角色</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data?.items?.map((user: any) => (
            <TableRow key={user.id}>
              <TableCell>{user.displayName}</TableCell>
              <TableCell>{user.email}</TableCell>
              <TableCell>
                <Badge variant={user.status === 'ACTIVE' ? 'default' : 'destructive'}>
                  {user.status}
                </Badge>
              </TableCell>
              <TableCell>{user.roles?.join(', ')}</TableCell>
              <TableCell>
                <Button variant="ghost" size="sm" asChild>
                  <Link to={`/admin/users/${user.id}`}>详情</Link>
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
```

- [ ] **Step 3: 创建审计日志页面**

```tsx
// web/src/pages/admin/audit-logs.tsx
import { useState } from 'react';
import { useAuditLogs } from '@/features/admin/use-audit-logs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue }
  from '@/shared/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow }
  from '@/shared/ui/table';
import { Badge } from '@/shared/ui/badge';

export default function AuditLogsPage() {
  const [action, setAction] = useState<string>();
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAuditLogs({ action, page });

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">审计日志</h1>
      <div className="flex gap-4">
        <Select value={action} onValueChange={setAction}>
          <SelectTrigger className="w-40"><SelectValue placeholder="操作类型" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="PUBLISH">发布</SelectItem>
            <SelectItem value="APPROVE">审核通过</SelectItem>
            <SelectItem value="REJECT">审核拒绝</SelectItem>
            <SelectItem value="DELETE">删除</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>时间</TableHead>
            <TableHead>操作人</TableHead>
            <TableHead>操作</TableHead>
            <TableHead>目标</TableHead>
            <TableHead>IP</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data?.items?.map((log: any) => (
            <TableRow key={log.id}>
              <TableCell className="text-sm">{log.createdAt}</TableCell>
              <TableCell>{log.actorName}</TableCell>
              <TableCell><Badge>{log.action}</Badge></TableCell>
              <TableCell>{log.targetType} #{log.targetId}</TableCell>
              <TableCell className="font-mono text-xs">{log.clientIp}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
```

- [ ] **Step 4: 添加管理后台路由守卫**

在 `web/src/router.tsx` 中添加 `/admin` 路由组，配置角色守卫：

```typescript
// 管理后台路由守卫 - 检查用户是否有管理员角色
function AdminGuard({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const adminRoles = ['SUPER_ADMIN', 'USER_ADMIN', 'AUDITOR'];
  const hasAdminRole = user?.roles?.some((r: string) => adminRoles.includes(r));
  if (!hasAdminRole) return <Navigate to="/403" />;
  return <>{children}</>;
}
```

- [ ] **Step 5: Commit**

```bash
git add web/src/pages/admin/
git add web/src/features/admin/
git commit -m "feat(admin): add admin dashboard pages (users, audit-logs)"
```

### Task 6: Chunk 5 验收

- [ ] **Step 1: 运行后端测试**

运行：`cd server && ./mvnw test`
预期：所有测试通过

- [ ] **Step 2: 运行前端测试**

运行：`cd web && npm test`
预期：所有测试通过

- [ ] **Step 3: 验证 8 个验收标准**

逐一验证 Chunk 5 的验收标准。

- [ ] **Step 4: 最终代码审查**

运行：`cd server && ./mvnw compile && cd ../web && npm run build`
预期：编译和构建全部成功

---

## 实施说明

**当前文档状态：**
- ✅ Chunk 1：审核流程核心（后端）— Task 1-10 详细 TDD 步骤
- ✅ Chunk 2：评分收藏 + 前端审核中心 — Task 1-9 详细 TDD 步骤
- ✅ Chunk 3：CLI API + Web 授权 — Task 1-6 详细 TDD 步骤
- ✅ Chunk 4：ClawHub 兼容层 — Task 1-4 详细 TDD 步骤
- ✅ Chunk 5：幂等去重 + 管理后台 — Task 1-6 详细 TDD 步骤

**建议的实施方式：**

1. **使用 superpowers:subagent-driven-development** — 为每个 Chunk 派发独立的子代理
2. **渐进式实施** — 先完成 Chunk 1，验收通过后再进行 Chunk 2
3. **参考设计文档** — 每个任务的详细实现逻辑参考 `docs/superpowers/specs/2026-03-12-phase3-review-cli-social-design.md`
