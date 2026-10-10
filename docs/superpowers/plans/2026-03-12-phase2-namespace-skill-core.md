# Phase 2: 命名空间 + Skill 核心链路 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Phase 1 工程骨架和认证体系基础上，实现命名空间管理、对象存储、技能发布/查询/下载完整链路、标签管理、PostgreSQL 全文搜索、异步事件和应用层精细限流。

**Architecture:** Maven 多模块后端（6 模块）扩展 + React 前端页面。后端采用领域服务集中式架构，domain 模块包含领域服务和应用服务。对象存储 SPI 双实现（LocalFile + S3），搜索 SPI PostgreSQL Full-Text 实现。发布流程 Phase 2 跳过审核直接到 PUBLISHED。

**身份主键约束：** 用户身份主键全链路统一使用 `string`。本计划中所有 `userId` / `ownerId` / `createdBy` / `updatedBy` / `reviewedBy` 等用户标识字段均按字符串实现；旧的 `Long` / `BIGINT` 表述仅代表历史残留，不得继续照抄到代码或数据库设计。

**Tech Stack:**
- Backend: Spring Boot 3.x + JDK 21 + PostgreSQL 16 + Redis 7 + Spring Data JPA + Flyway + AWS SDK v2 (S3) + SnakeYAML
- Frontend: React 19 + TypeScript + Vite + TanStack Router + TanStack Query + shadcn/ui + Tailwind CSS + react-markdown + react-dropzone
- DevOps: Docker Compose (PostgreSQL + Redis + MinIO) + Maven Wrapper + Makefile

**前置条件:** Phase 1 全部 3 个 Chunk 完成（后端骨架 + 认证授权 + 前端骨架）

---

## Chunk 1: 后端全部

本块实现 Phase 2 全部后端功能：数据库迁移、对象存储、命名空间管理、技能发布/查询/下载、标签管理、搜索、异步事件、限流。

### 文件结构映射

```
server/
├── skillhub-app/
│   └── src/
│       ├── main/
│       │   ├── java/com/iflytek/skillhub/
│       │   │   ├── config/
│       │   │   │   ├── AsyncConfig.java                    # 异步线程池配置
│       │   │   │   └── WebMvcRateLimitConfig.java          # 限流拦截器注册
│       │   │   ├── controller/
│       │   │   │   └── portal/
│       │   │   │       ├── NamespaceController.java        # 命名空间公开 API
│       │   │   │       ├── SkillController.java            # 技能公开查询/下载 API
│       │   │   │       ├── SkillPublishController.java     # Web 端发布 API
│       │   │   │       ├── SkillTagController.java         # 标签管理 API
│       │   │   │       └── SkillSearchController.java      # 搜索 API
│       │   │   │   └── cli/
│       │   │   │       └── CliPublishController.java       # CLI 发布 API
│       │   │   ├── service/
│       │   │   │   └── SkillSearchAppService.java          # 搜索应用服务（app 层）
│       │   │   ├── ratelimit/
│       │   │   │   ├── RateLimit.java                      # 限流注解
│       │   │   │   ├── RateLimitInterceptor.java           # 限流拦截器
│       │   │   │   └── SlidingWindowRateLimiter.java       # Redis 滑动窗口实现
│       │   │   └── dto/
│       │   │       ├── NamespaceRequest.java               # 命名空间请求 DTO
│       │   │       ├── NamespaceResponse.java              # 命名空间响应 DTO
│       │   │       ├── MemberRequest.java                  # 成员管理请求 DTO
│       │   │       ├── MemberResponse.java                 # 成员管理响应 DTO
│       │   │       ├── SkillDetailResponse.java            # 技能详情响应
│       │   │       ├── SkillSummaryResponse.java           # 技能摘要响应
│       │   │       ├── SkillVersionResponse.java           # 版本响应
│       │   │       ├── SkillFileResponse.java              # 文件响应
│       │   │       ├── PublishRequest.java                 # 发布请求
│       │   │       ├── PublishResponse.java                # 发布响应
│       │   │       ├── TagRequest.java                     # 标签请求
│       │   │       ├── TagResponse.java                    # 标签响应
│       │   │       └── SearchResponse.java                 # 搜索响应
│       │   └── resources/
│       │       ├── db/migration/
│       │       │   └── V2__phase2_skill_tables.sql         # Phase 2 数据库迁移
│       │       ├── ratelimit.lua                           # Redis 滑动窗口 Lua 脚本
│       │       ├── application.yml                         # 更新：新增 storage/search/publish 配置
│       │       └── application-local.yml                   # 更新：新增 local storage 配置
│       └── test/java/com/iflytek/skillhub/
│           ├── controller/
│           │   ├── NamespaceControllerTest.java
│           │   ├── SkillControllerTest.java
│           │   ├── SkillPublishControllerTest.java
│           │   └── SkillSearchControllerTest.java
│           └── ratelimit/
│               └── RateLimitInterceptorTest.java
├── skillhub-domain/
│   └── src/
│       ├── main/java/com/iflytek/skillhub/domain/
│       │   ├── namespace/
│       │   │   ├── Namespace.java                          # 修改：补齐 type/avatarUrl
│       │   │   ├── NamespaceMember.java                    # 修改：补齐 updatedAt
│       │   │   ├── NamespaceType.java                      # 新增枚举
│       │   │   ├── NamespaceRepository.java                # 修改：新增方法
│       │   │   ├── NamespaceMemberRepository.java          # 修改：新增方法
│       │   │   ├── NamespaceService.java                   # 新增
│       │   │   ├── NamespaceMemberService.java             # 新增
│       │   │   └── SlugValidator.java                      # 新增
│       │   ├── skill/
│       │   │   ├── Skill.java                              # 新增实体
│       │   │   ├── SkillVersion.java                       # 新增实体
│       │   │   ├── SkillFile.java                          # 新增实体
│       │   │   ├── SkillTag.java                           # 新增实体
│       │   │   ├── SkillStatus.java                        # 新增枚举
│       │   │   ├── SkillVersionStatus.java                 # 新增枚举
│       │   │   ├── SkillVisibility.java                    # 新增枚举
│       │   │   ├── SkillRepository.java                    # 新增接口
│       │   │   ├── SkillVersionRepository.java             # 新增接口
│       │   │   ├── SkillFileRepository.java                # 新增接口
│       │   │   ├── SkillTagRepository.java                 # 新增接口
│       │   │   └── VisibilityChecker.java                  # 新增
│       │   │   ├── service/
│       │   │   │   ├── SkillPublishService.java            # 新增
│       │   │   │   ├── SkillQueryService.java              # 新增
│       │   │   │   ├── SkillDownloadService.java           # 新增
│       │   │   │   └── SkillTagService.java                # 新增
│       │   │   ├── validation/
│       │   │   │   ├── SkillPackageValidator.java          # 新增
│       │   │   │   ├── PrePublishValidator.java            # 新增接口
│       │   │   │   ├── NoOpPrePublishValidator.java        # 新增默认实现
│       │   │   │   └── PackageEntry.java                   # 新增 record
│       │   │   └── metadata/
│       │   │       ├── SkillMetadataParser.java            # 新增
│       │   │       └── SkillMetadata.java                  # 新增 record
│       │   └── event/
│       │       ├── SkillPublishedEvent.java                # 新增
│       │       ├── SkillDownloadedEvent.java               # 新增
│       │       └── SkillStatusChangedEvent.java            # 新增
│       └── test/java/com/iflytek/skillhub/domain/
│           ├── namespace/
│           │   ├── SlugValidatorTest.java
│           │   ├── NamespaceServiceTest.java
│           │   └── NamespaceMemberServiceTest.java
│           └── skill/
│               ├── VisibilityCheckerTest.java
│               ├── validation/
│               │   └── SkillPackageValidatorTest.java
│               ├── metadata/
│               │   └── SkillMetadataParserTest.java
│               └── service/
│                   ├── SkillPublishServiceTest.java
│                   ├── SkillQueryServiceTest.java
│                   ├── SkillDownloadServiceTest.java
│                   └── SkillTagServiceTest.java
├── skillhub-storage/
│   └── src/
│       ├── main/java/com/iflytek/skillhub/storage/
│       │   ├── ObjectStorageService.java                   # 新增 SPI 接口
│       │   ├── ObjectMetadata.java                         # 新增 record
│       │   ├── StorageProperties.java                      # 新增配置属性
│       │   ├── LocalFileStorageService.java                # 新增
│       │   ├── S3StorageService.java                       # 新增
│       │   └── S3StorageProperties.java                    # 新增配置属性
│       └── test/java/com/iflytek/skillhub/storage/
│           ├── LocalFileStorageServiceTest.java
│           └── S3StorageServiceTest.java
├── skillhub-search/
│   └── src/
│       ├── main/java/com/iflytek/skillhub/search/
│       │   ├── SearchIndexService.java                     # 新增 SPI 接口
│       │   ├── SearchQueryService.java                     # 新增 SPI 接口
│       │   ├── SearchRebuildService.java                   # 新增 SPI 接口
│       │   ├── SearchQuery.java                            # 新增 record
│       │   ├── SearchVisibilityScope.java                  # 新增 record
│       │   ├── SearchResult.java                           # 新增 record
│       │   ├── SkillSearchDocument.java                    # 新增 record
│       │   ├── postgres/
│       │   │   ├── PostgresFullTextIndexService.java       # 新增
│       │   │   ├── PostgresFullTextQueryService.java       # 新增
│       │   │   └── PostgresSearchRebuildService.java       # 新增
│       │   └── event/
│       │       ├── SearchIndexEventListener.java           # 新增
│       │       └── DownloadCountEventListener.java         # 新增
│       └── test/java/com/iflytek/skillhub/search/
│           └── postgres/
│               ├── PostgresFullTextIndexServiceTest.java
│               └── PostgresFullTextQueryServiceTest.java
├── skillhub-infra/
│   └── src/main/java/com/iflytek/skillhub/infra/jpa/
│       ├── SkillJpaRepository.java                         # 新增
│       ├── SkillVersionJpaRepository.java                  # 新增
│       ├── SkillFileJpaRepository.java                     # 新增
│       ├── SkillTagJpaRepository.java                      # 新增
│       └── SkillSearchDocumentJpaRepository.java           # 新增
└── docker-compose.yml                                      # 修改：确认 MinIO 配置
```

### Task 1: 数据库迁移 — Phase 2 表结构

**Files:**
- Create: `server/skillhub-app/src/main/resources/db/migration/V2__phase2_skill_tables.sql`

- [ ] **Step 1: 创建 V2 迁移脚本**

```sql
-- V2__phase2_skill_tables.sql
-- Phase 2: 命名空间 + Skill 核心链路

-- 技能主表
CREATE TABLE skill (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    summary VARCHAR(512),
    owner_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    source_skill_id BIGINT,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    latest_version_id BIGINT,
    download_count BIGINT NOT NULL DEFAULT 0,
    star_count INT NOT NULL DEFAULT 0,
    rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128) REFERENCES user_account(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace_id, slug)
);

CREATE INDEX idx_skill_namespace_status ON skill(namespace_id, status);

-- 技能版本表
CREATE TABLE skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    changelog TEXT,
    parsed_metadata_json JSONB,
    manifest_json JSONB,
    file_count INT NOT NULL DEFAULT 0,
    total_size BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, version)
);

CREATE INDEX idx_skill_version_skill_status ON skill_version(skill_id, status);

ALTER TABLE skill ADD CONSTRAINT fk_skill_latest_version
    FOREIGN KEY (latest_version_id) REFERENCES skill_version(id);

-- 技能文件表
CREATE TABLE skill_file (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES skill_version(id),
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(128),
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(version_id, file_path)
);

-- 技能标签表
CREATE TABLE skill_tag (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    tag_name VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL REFERENCES skill_version(id),
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, tag_name)
);

-- 搜索文档表
CREATE TABLE skill_search_document (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL UNIQUE REFERENCES skill(id),
    namespace_id BIGINT NOT NULL,
    namespace_slug VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    title VARCHAR(256),
    summary VARCHAR(512),
    keywords VARCHAR(512),
    search_text TEXT,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);
CREATE INDEX idx_search_doc_namespace ON skill_search_document(namespace_id);
CREATE INDEX idx_search_doc_visibility ON skill_search_document(visibility);
```

- [ ] **Step 2: 启动依赖服务并执行迁移**

Run: `make dev && cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.main.web-application-type=none" 2>&1 | head -50`

Expected: Flyway 输出 `Successfully applied 1 migration`，应用启动后 Ctrl+C 停止

- [ ] **Step 3: 验证表创建**

Run: `docker compose exec postgres psql -U skillhub -d skillhub -c "\dt skill*"`

Expected: 列出 skill, skill_version, skill_file, skill_tag, skill_search_document 五张表

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-app/src/main/resources/db/migration/V2__phase2_skill_tables.sql
git commit -m "feat(db): add Phase 2 skill tables migration

- Add skill, skill_version, skill_file, skill_tag tables
- Add skill_search_document with tsvector generated column + GIN index
- Add foreign keys, unique constraints, and indexes per domain model spec"
```

### Task 2: Phase 1 实体补齐 — Namespace 和 NamespaceMember

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/Namespace.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMember.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceType.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceRepository.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberRepository.java`
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceJpaRepository.java`
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceMemberJpaRepository.java`

- [ ] **Step 1: 创建 NamespaceType 枚举**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceType.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

public enum NamespaceType {
    GLOBAL,
    TEAM
}
EOF
```

- [ ] **Step 2: 补齐 Namespace 实体字段（type 和 avatarUrl）**

在 `Namespace.java` 中添加缺失字段：

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 32)
private NamespaceType type = NamespaceType.TEAM;

@Column(name = "avatar_url", length = 512)
private String avatarUrl;
```

添加 getter/setter：

```java
public NamespaceType getType() { return type; }
public void setType(NamespaceType type) { this.type = type; }
public String getAvatarUrl() { return avatarUrl; }
public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
public String getDescription() { return description; }
public void setDescription(String description) { this.description = description; }
public void setDisplayName(String displayName) { this.displayName = displayName; }
```

- [ ] **Step 3: 补齐 NamespaceMember 实体字段（updatedAt）**

在 `NamespaceMember.java` 中添加 updatedAt 字段：

```java
@Column(name = "updated_at", nullable = false)
private LocalDateTime updatedAt;
```

修改 `@PrePersist` 和添加 `@PreUpdate`：

```java
@PrePersist
void prePersist() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = this.createdAt;
}

@PreUpdate
void preUpdate() {
    this.updatedAt = LocalDateTime.now();
}
```

添加 getter：

```java
public LocalDateTime getUpdatedAt() { return updatedAt; }
```

- [ ] **Step 4: 扩展 NamespaceRepository 接口**

在 `NamespaceRepository.java` 中添加新方法：

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);
```

- [ ] **Step 5: 扩展 NamespaceMemberRepository 接口**

在 `NamespaceMemberRepository.java` 中添加新方法：

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable);
void deleteByNamespaceIdAndUserId(Long namespaceId, String userId);
```

- [ ] **Step 6: 更新 NamespaceJpaRepository 实现**

在 `NamespaceJpaRepository.java` 中添加方法声明（Spring Data JPA 自动实现）：

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;

Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);
```

- [ ] **Step 7: 更新 NamespaceMemberJpaRepository 实现**

在 `NamespaceMemberJpaRepository.java` 中添加方法声明：

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable);
void deleteByNamespaceIdAndUserId(Long namespaceId, String userId);
```

- [ ] **Step 8: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/ \
        server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/
git commit -m "feat(domain): complete Phase 1 entity fields for Namespace and NamespaceMember

- Add NamespaceType enum (GLOBAL/TEAM)
- Add Namespace.type and Namespace.avatarUrl fields
- Add NamespaceMember.updatedAt field with @PreUpdate
- Extend NamespaceRepository with findByStatus pagination method
- Extend NamespaceMemberRepository with findByNamespaceId and deleteByNamespaceIdAndUserId
- Update JPA repository implementations"
```

### Task 3: SlugValidator — Slug 格式校验器

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/SlugValidator.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/SlugValidatorTest.java`

- [ ] **Step 1: 编写 SlugValidator 测试**

```bash
mkdir -p server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/SlugValidatorTest.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlugValidatorTest {

    @Test
    void shouldAcceptValidSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("my-namespace"));
        assertDoesNotThrow(() -> SlugValidator.validate("ab"));
        assertDoesNotThrow(() -> SlugValidator.validate("test123"));
        assertDoesNotThrow(() -> SlugValidator.validate("my-team-2024"));
    }

    @Test
    void shouldRejectTooShort() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("a"));
        assertTrue(ex.getMessage().contains("length"));
    }

    @Test
    void shouldRejectTooLong() {
        String longSlug = "a".repeat(65);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate(longSlug));
        assertTrue(ex.getMessage().contains("length"));
    }

    @Test
    void shouldRejectUpperCase() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("MyNamespace"));
        assertTrue(ex.getMessage().contains("lowercase"));
    }

    @Test
    void shouldRejectStartingWithHyphen() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("-namespace"));
        assertTrue(ex.getMessage().contains("alphanumeric"));
    }

    @Test
    void shouldRejectEndingWithHyphen() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("namespace-"));
        assertTrue(ex.getMessage().contains("alphanumeric"));
    }

    @Test
    void shouldRejectDoubleHyphen() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("my--namespace"));
        assertTrue(ex.getMessage().contains("consecutive"));
    }

    @Test
    void shouldRejectReservedWords() {
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("admin"));
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("api"));
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("global"));
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("system"));
    }

    @Test
    void shouldRejectSpecialCharacters() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("my_namespace"));
        assertTrue(ex.getMessage().contains("lowercase") || ex.getMessage().contains("alphanumeric"));
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -Dtest=SlugValidatorTest`

Expected: FAIL - SlugValidator class not found

- [ ] **Step 3: 实现 SlugValidator**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/SlugValidator.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

import java.util.Set;
import java.util.regex.Pattern;

public class SlugValidator {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 64;
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "admin", "api", "dashboard", "search", "auth",
            "me", "global", "system", "static", "assets", "health"
    );

    public static void validate(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Slug cannot be null or blank");
        }

        if (slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Slug length must be between %d and %d characters", MIN_LENGTH, MAX_LENGTH)
            );
        }

        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug must contain only lowercase alphanumeric characters and hyphens, " +
                    "and must start and end with an alphanumeric character"
            );
        }

        if (slug.contains("--")) {
            throw new IllegalArgumentException("Slug cannot contain consecutive hyphens");
        }

        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Slug '" + slug + "' is reserved and cannot be used");
        }
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -Dtest=SlugValidatorTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/SlugValidator.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/SlugValidatorTest.java
git commit -m "feat(domain): add SlugValidator with comprehensive validation rules

- Validate slug length (2-64 characters)
- Enforce lowercase alphanumeric + hyphens only
- Reject consecutive hyphens (--)
- Reject reserved words (admin, api, global, etc.)
- Add comprehensive test coverage"
```

### Task 4: Object Storage SPI + Configuration

**Files:**
- Modify: `server/skillhub-storage/pom.xml`
- Create: `server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/ObjectStorageService.java`
- Create: `server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/ObjectMetadata.java`
- Create: `server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/StorageProperties.java`

- [ ] **Step 1: 添加 SnakeYAML 和 AWS SDK 依赖到 skillhub-storage/pom.xml**

```bash
cat > server/skillhub-storage/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.iflytek.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>skillhub-storage</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>s3</artifactId>
            <version>2.20.26</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
EOF
```

- [ ] **Step 2: 创建 ObjectStorageService SPI 接口**

```bash
mkdir -p server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage
cat > server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/ObjectStorageService.java << 'EOF'
package com.iflytek.skillhub.storage;

import java.io.InputStream;
import java.util.List;

public interface ObjectStorageService {

    /**
     * 上传对象到存储
     * @param key 对象 key
     * @param data 数据流
     * @param size 数据大小（字节）
     * @param contentType MIME 类型
     */
    void putObject(String key, InputStream data, long size, String contentType);

    /**
     * 获取对象数据流
     * @param key 对象 key
     * @return 数据流
     */
    InputStream getObject(String key);

    /**
     * 删除单个对象
     * @param key 对象 key
     */
    void deleteObject(String key);

    /**
     * 批量删除对象
     * @param keys 对象 key 列表
     */
    void deleteObjects(List<String> keys);

    /**
     * 检查对象是否存在
     * @param key 对象 key
     * @return 是否存在
     */
    boolean exists(String key);

    /**
     * 获取对象元数据
     * @param key 对象 key
     * @return 元数据
     */
    ObjectMetadata getMetadata(String key);
}
EOF
```

- [ ] **Step 3: 创建 ObjectMetadata record**

```bash
cat > server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/ObjectMetadata.java << 'EOF'
package com.iflytek.skillhub.storage;

import java.time.Instant;

public record ObjectMetadata(
        long size,
        String contentType,
        Instant lastModified
) {}
EOF
```

- [ ] **Step 4: 创建 StorageProperties 配置类**

```bash
cat > server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/StorageProperties.java << 'EOF'
package com.iflytek.skillhub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.storage")
public class StorageProperties {

    private String provider = "local";
    private LocalProperties local = new LocalProperties();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalProperties getLocal() {
        return local;
    }

    public void setLocal(LocalProperties local) {
        this.local = local;
    }

    public static class LocalProperties {
        private String basePath = "./data/storage";

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }
}
EOF
```

- [ ] **Step 5: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-storage/pom.xml \
        server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/
git commit -m "feat(storage): add object storage SPI and configuration

- Add ObjectStorageService interface with put/get/delete/exists/getMetadata methods
- Add ObjectMetadata record for metadata representation
- Add StorageProperties for provider configuration
- Add SnakeYAML and AWS SDK v2 dependencies to pom.xml"
```

### Task 5: LocalFileStorageService — 本地文件存储实现

**Files:**
- Create: `server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/LocalFileStorageService.java`
- Test: `server/skillhub-storage/src/test/java/com/iflytek/skillhub/storage/LocalFileStorageServiceTest.java`

- [ ] **Step 1: 编写 LocalFileStorageService 测试**

```bash
mkdir -p server/skillhub-storage/src/test/java/com/iflytek/skillhub/storage
cat > server/skillhub-storage/src/test/java/com/iflytek/skillhub/storage/LocalFileStorageServiceTest.java << 'EOF'
package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getLocal().setBasePath(tempDir.toString());
        storageService = new LocalFileStorageService(props);
    }

    @Test
    void shouldPutAndGetObject() throws Exception {
        String key = "skills/1/1/SKILL.md";
        byte[] content = "# Hello".getBytes(StandardCharsets.UTF_8);
        storageService.putObject(key, new ByteArrayInputStream(content), content.length, "text/markdown");

        try (InputStream result = storageService.getObject(key)) {
            assertArrayEquals(content, result.readAllBytes());
        }
    }

    @Test
    void shouldCheckExistence() {
        String key = "test/exists.txt";
        assertFalse(storageService.exists(key));

        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        storageService.putObject(key, new ByteArrayInputStream(content), content.length, "text/plain");

        assertTrue(storageService.exists(key));
    }

    @Test
    void shouldDeleteObject() {
        String key = "test/delete.txt";
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        storageService.putObject(key, new ByteArrayInputStream(content), content.length, "text/plain");

        assertTrue(storageService.exists(key));
        storageService.deleteObject(key);
        assertFalse(storageService.exists(key));
    }

    @Test
    void shouldDeleteMultipleObjects() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        storageService.putObject("a/1.txt", new ByteArrayInputStream(content), content.length, "text/plain");
        storageService.putObject("a/2.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        storageService.deleteObjects(List.of("a/1.txt", "a/2.txt"));
        assertFalse(storageService.exists("a/1.txt"));
        assertFalse(storageService.exists("a/2.txt"));
    }

    @Test
    void shouldGetMetadata() {
        String key = "test/meta.txt";
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        storageService.putObject(key, new ByteArrayInputStream(content), content.length, "text/plain");

        ObjectMetadata metadata = storageService.getMetadata(key);
        assertEquals(content.length, metadata.size());
        assertNotNull(metadata.lastModified());
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-storage -Dtest=LocalFileStorageServiceTest`

Expected: FAIL - LocalFileStorageService class not found

- [ ] **Step 3: 实现 LocalFileStorageService**

```bash
cat > server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/LocalFileStorageService.java << 'EOF'
package com.iflytek.skillhub.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements ObjectStorageService {

    private final Path basePath;

    public LocalFileStorageService(StorageProperties properties) {
        this.basePath = Paths.get(properties.getLocal().getBasePath());
    }

    @Override
    public void putObject(String key, InputStream data, long size, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                data.transferTo(out);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to put object: " + key, e);
        }
    }

    @Override
    public InputStream getObject(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get object: " + key, e);
        }
    }

    @Override
    public void deleteObject(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete object: " + key, e);
        }
    }

    @Override
    public void deleteObjects(List<String> keys) {
        keys.forEach(this::deleteObject);
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public ObjectMetadata getMetadata(String key) {
        try {
            Path path = resolve(key);
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return new ObjectMetadata(
                    attrs.size(),
                    Files.probeContentType(path),
                    attrs.lastModifiedTime().toInstant()
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get metadata: " + key, e);
        }
    }

    private Path resolve(String key) {
        return basePath.resolve(key);
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-storage -Dtest=LocalFileStorageServiceTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/LocalFileStorageService.java \
        server/skillhub-storage/src/test/java/com/iflytek/skillhub/storage/LocalFileStorageServiceTest.java
git commit -m "feat(storage): implement LocalFileStorageService with atomic write

- Implement local file system storage using java.nio.file
- Atomic write via .tmp file + rename
- Support put/get/delete/exists/getMetadata operations
- Add comprehensive unit tests with @TempDir"
```

### Task 6: S3StorageService — AWS S3 存储实现

**Files:**
- Create: `server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/S3StorageProperties.java`
- Create: `server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/S3StorageService.java`

- [ ] **Step 1: 创建 S3StorageProperties 配置类**

```bash
cat > server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/S3StorageProperties.java << 'EOF'
package com.iflytek.skillhub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.storage.s3")
public class S3StorageProperties {

    private String endpoint;
    private String bucket = "skillhub";
    private String accessKey;
    private String secretKey;
    private String region = "us-east-1";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
EOF
```

- [ ] **Step 2: 实现 S3StorageService**

```bash
cat > server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/S3StorageService.java << 'EOF'
package com.iflytek.skillhub.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "s3")
public class S3StorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3StorageProperties properties;
    private S3Client s3Client;

    public S3StorageService(S3StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                ))
                .forcePathStyle(true);

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        this.s3Client = builder.build();
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (NoSuchBucketException e) {
            log.info("Bucket '{}' does not exist, creating...", properties.getBucket());
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        }
    }

    @Override
    public void putObject(String key, InputStream data, long size, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                        .build(),
                RequestBody.fromInputStream(data, size)
        );
    }

    @Override
    public InputStream getObject(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .build()
        );
    }

    @Override
    public void deleteObject(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .build()
        );
    }

    @Override
    public void deleteObjects(List<String> keys) {
        if (keys.isEmpty()) return;
        List<ObjectIdentifier> ids = keys.stream()
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .toList();
        s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                        .bucket(properties.getBucket())
                        .delete(Delete.builder().objects(ids).build())
                        .build()
        );
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public ObjectMetadata getMetadata(String key) {
        HeadObjectResponse resp = s3Client.headObject(
                HeadObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .build()
        );
        return new ObjectMetadata(
                resp.contentLength(),
                resp.contentType(),
                resp.lastModified()
        );
    }
}
EOF
```

- [ ] **Step 3: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/S3StorageProperties.java \
        server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/S3StorageService.java
git commit -m "feat(storage): implement S3StorageService with auto-create bucket

- Add S3StorageProperties for endpoint/bucket/credentials configuration
- Implement S3StorageService using AWS SDK v2 S3Client
- Auto-create bucket on startup if not exists
- Support path-style access for MinIO compatibility
- Activated via skillhub.storage.provider=s3"
```

### Task 7: Skill Domain Entities — 技能实体和枚举

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVisibility.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/Skill.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersion.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillFile.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillTag.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillFileRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillTagRepository.java`

- [ ] **Step 1: 创建枚举类型**

```bash
mkdir -p server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillStatus.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

public enum SkillStatus {
    ACTIVE,
    HIDDEN,
    ARCHIVED
}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

public enum SkillVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED
}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVisibility.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

public enum SkillVisibility {
    PUBLIC,
    NAMESPACE_ONLY,
    PRIVATE
}
EOF
```

- [ ] **Step 2: 创建 Skill 实体**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/Skill.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill", uniqueConstraints = @UniqueConstraint(columnNames = {"namespace_id", "slug"}))
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(length = 512)
    private String summary;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "source_skill_id")
    private Long sourceSkillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillVisibility visibility = SkillVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillStatus status = SkillStatus.ACTIVE;

    @Column(name = "latest_version_id")
    private Long latestVersionId;

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "star_count", nullable = false)
    private Integer starCount = 0;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Skill() {}

    public Skill(Long namespaceId, String slug, Long ownerId, SkillVisibility visibility) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.ownerId = ownerId;
        this.visibility = visibility;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getOwnerId() { return ownerId; }
    public Long getSourceSkillId() { return sourceSkillId; }
    public void setSourceSkillId(Long sourceSkillId) { this.sourceSkillId = sourceSkillId; }
    public SkillVisibility getVisibility() { return visibility; }
    public void setVisibility(SkillVisibility visibility) { this.visibility = visibility; }
    public SkillStatus getStatus() { return status; }
    public void setStatus(SkillStatus status) { this.status = status; }
    public Long getLatestVersionId() { return latestVersionId; }
    public void setLatestVersionId(Long latestVersionId) { this.latestVersionId = latestVersionId; }
    public Long getDownloadCount() { return downloadCount; }
    public Integer getStarCount() { return starCount; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
EOF
```

- [ ] **Step 3: 创建 SkillVersion 实体**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersion.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_version", uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "version"}))
public class SkillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(nullable = false, length = 64)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillVersionStatus status = SkillVersionStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "parsed_metadata_json", columnDefinition = "JSONB")
    private String parsedMetadataJson;

    @Column(name = "manifest_json", columnDefinition = "JSONB")
    private String manifestJson;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 0;

    @Column(name = "total_size", nullable = false)
    private Long totalSize = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SkillVersion() {}

    public SkillVersion(Long skillId, String version, Long createdBy) {
        this.skillId = skillId;
        this.version = version;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public String getVersion() { return version; }
    public SkillVersionStatus getStatus() { return status; }
    public void setStatus(SkillVersionStatus status) { this.status = status; }
    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }
    public String getParsedMetadataJson() { return parsedMetadataJson; }
    public void setParsedMetadataJson(String parsedMetadataJson) { this.parsedMetadataJson = parsedMetadataJson; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
EOF
```

- [ ] **Step 4: 创建 SkillFile 实体**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillFile.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_file", uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "file_path"}))
public class SkillFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SkillFile() {}

    public SkillFile(Long versionId, String filePath, Long fileSize, String contentType, String sha256, String storageKey) {
        this.versionId = versionId;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.storageKey = storageKey;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public String getFilePath() { return filePath; }
    public Long getFileSize() { return fileSize; }
    public String getContentType() { return contentType; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
EOF
```

- [ ] **Step 5: 创建 SkillTag 实体**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillTag.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_tag", uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "tag_name"}))
public class SkillTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "tag_name", nullable = false, length = 64)
    private String tagName;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SkillTag() {}

    public SkillTag(Long skillId, String tagName, Long versionId, Long createdBy) {
        this.skillId = skillId;
        this.tagName = tagName;
        this.versionId = versionId;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public String getTagName() { return tagName; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
EOF
```

- [ ] **Step 6: 创建 Repository 接口**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillRepository.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SkillRepository {
    Optional<Skill> findById(Long id);
    Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Page<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status, Pageable pageable);
    Skill save(Skill skill);
    List<Skill> findByOwnerId(Long ownerId);
    void incrementDownloadCount(Long skillId);
}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionRepository.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SkillVersionRepository {
    Optional<SkillVersion> findById(Long id);
    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);
    Page<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status, Pageable pageable);
    SkillVersion save(SkillVersion version);
}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillFileRepository.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import java.util.List;

public interface SkillFileRepository {
    List<SkillFile> findByVersionId(Long versionId);
    SkillFile save(SkillFile file);
    void saveAll(List<SkillFile> files);
    void deleteByVersionId(Long versionId);
}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillTagRepository.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillTagRepository {
    Optional<SkillTag> findBySkillIdAndTagName(Long skillId, String tagName);
    List<SkillTag> findBySkillId(Long skillId);
    SkillTag save(SkillTag tag);
    void delete(SkillTag tag);
}
EOF
```

- [ ] **Step 7: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/
git commit -m "feat(domain): add Skill domain entities and repository interfaces

- Add SkillStatus, SkillVersionStatus, SkillVisibility enums
- Add Skill entity with namespace/owner/visibility/status/stats fields
- Add SkillVersion entity with status/metadata/manifest JSONB fields
- Add SkillFile entity with storage_key and sha256
- Add SkillTag entity with version reference
- Add repository interfaces for all entities with query methods"
```

### Task 8: Skill JPA Repository Implementations

**Files:**
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillJpaRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillVersionJpaRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillFileJpaRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillTagJpaRepository.java`

- [ ] **Step 1: 创建 SkillJpaRepository**

```bash
cat > server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillJpaRepository.java << 'EOF'
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillJpaRepository extends JpaRepository<Skill, Long>, SkillRepository {

    Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);

    Page<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status, Pageable pageable);

    List<Skill> findByOwnerId(Long ownerId);

    @Modifying
    @Query("UPDATE Skill s SET s.downloadCount = s.downloadCount + 1 WHERE s.id = :skillId")
    void incrementDownloadCount(@Param("skillId") Long skillId);
}
EOF
```

- [ ] **Step 2: 创建 SkillVersionJpaRepository**

```bash
cat > server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillVersionJpaRepository.java << 'EOF'
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillVersionJpaRepository extends JpaRepository<SkillVersion, Long>, SkillVersionRepository {

    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);

    Page<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status, Pageable pageable);
}
EOF
```

- [ ] **Step 3: 创建 SkillFileJpaRepository**

```bash
cat > server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillFileJpaRepository.java << 'EOF'
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillFileJpaRepository extends JpaRepository<SkillFile, Long>, SkillFileRepository {

    List<SkillFile> findByVersionId(Long versionId);

    void deleteByVersionId(Long versionId);
}
EOF
```

- [ ] **Step 4: 创建 SkillTagJpaRepository**

```bash
cat > server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillTagJpaRepository.java << 'EOF'
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillTag;
import com.iflytek.skillhub.domain.skill.SkillTagRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillTagJpaRepository extends JpaRepository<SkillTag, Long>, SkillTagRepository {

    Optional<SkillTag> findBySkillIdAndTagName(Long skillId, String tagName);

    List<SkillTag> findBySkillId(Long skillId);
}
EOF
```

- [ ] **Step 5: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/Skill*.java
git commit -m "feat(infra): add JPA repository implementations for Skill entities

- Add SkillJpaRepository with incrementDownloadCount custom query
- Add SkillVersionJpaRepository with skill/version lookup
- Add SkillFileJpaRepository with version-based queries
- Add SkillTagJpaRepository with skill/tag lookup
- All extend domain repository interfaces and JpaRepository"
```

### Task 9: SkillMetadataParser — SKILL.md Frontmatter 解析器

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadata.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadataParser.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadataParserTest.java`

- [ ] **Step 1: 创建 SkillMetadata record**

```bash
mkdir -p server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadata.java << 'EOF'
package com.iflytek.skillhub.domain.skill.metadata;

import java.util.Map;

public record SkillMetadata(
        String name,
        String description,
        String version,
        String body,
        Map<String, Object> frontmatter
) {}
EOF
```

- [ ] **Step 2: 编写 SkillMetadataParser 测试**

```bash
mkdir -p server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/metadata
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadataParserTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill.metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillMetadataParserTest {

    private SkillMetadataParser parser;

    @BeforeEach
    void setUp() {
        parser = new SkillMetadataParser();
    }

    @Test
    void shouldParseStandardFrontmatterAndBody() {
        String content = """
                ---
                name: my-skill
                description: A test skill
                version: 1.0.0
                ---
                # My Skill

                This is the body content.
                """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("my-skill", metadata.name());
        assertEquals("A test skill", metadata.description());
        assertEquals("1.0.0", metadata.version());
        assertTrue(metadata.body().contains("# My Skill"));
        assertTrue(metadata.body().contains("This is the body content."));
    }

    @Test
    void shouldPreserveExtensionFields() {
        String content = """
                ---
                name: my-skill
                description: A test skill
                version: 1.0.0
                x-astron-category: productivity
                ---
                Body text
                """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("productivity", metadata.frontmatter().get("x-astron-category"));
    }

    @Test
    void shouldThrowWhenNoFrontmatter() {
        String content = "# Just a markdown file\nNo frontmatter here.";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
    }

    @Test
    void shouldThrowWhenMissingName() {
        String content = """
                ---
                description: A test skill
                version: 1.0.0
                ---
                Body
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
    }

    @Test
    void shouldThrowWhenMissingDescription() {
        String content = """
                ---
                name: my-skill
                version: 1.0.0
                ---
                Body
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
    }

    @Test
    void shouldThrowWhenMissingVersion() {
        String content = """
                ---
                name: my-skill
                description: A test skill
                ---
                Body
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
    }

    @Test
    void shouldThrowWhenInvalidFrontmatterYaml() {
        String content = """
                ---
                name: [invalid yaml
                ---
                Body
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
    }
}
EOF
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillMetadataParserTest`

Expected: FAIL - SkillMetadataParser class not found

- [ ] **Step 4: 确认 SnakeYAML 依赖在 skillhub-domain/pom.xml 中**

在 `server/skillhub-domain/pom.xml` 中添加 SnakeYAML 依赖（如果尚未存在）：

```xml
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
</dependency>
```

- [ ] **Step 5: 实现 SkillMetadataParser**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadataParser.java << 'EOF'
package com.iflytek.skillhub.domain.skill.metadata;

import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.Map;

public class SkillMetadataParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    /**
     * 解析 SKILL.md 内容，提取 frontmatter 和正文
     * @param skillMdContent SKILL.md 文件完整内容
     * @return 解析后的元数据
     * @throws IllegalArgumentException 如果格式不合法或缺少必需字段
     */
    @SuppressWarnings("unchecked")
    public SkillMetadata parse(String skillMdContent) {
        if (skillMdContent == null || skillMdContent.isBlank()) {
            throw new IllegalArgumentException("SKILL.md content cannot be empty");
        }

        String trimmed = skillMdContent.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            throw new IllegalArgumentException("SKILL.md must start with frontmatter (---)");
        }

        int secondDelimiter = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("SKILL.md frontmatter is not properly closed with ---");
        }

        String yamlContent = trimmed.substring(FRONTMATTER_DELIMITER.length(), secondDelimiter).strip();
        String body = trimmed.substring(secondDelimiter + FRONTMATTER_DELIMITER.length()).strip();

        Map<String, Object> frontmatter;
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlContent);
            if (parsed instanceof Map) {
                frontmatter = (Map<String, Object>) parsed;
            } else {
                throw new IllegalArgumentException("Frontmatter must be a YAML mapping");
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("Failed to parse frontmatter YAML: " + e.getMessage(), e);
        }

        String name = getRequiredString(frontmatter, "name");
        String description = getRequiredString(frontmatter, "description");
        String version = getRequiredString(frontmatter, "version");

        return new SkillMetadata(name, description, version, body, Collections.unmodifiableMap(frontmatter));
    }

    private String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required field '" + key + "' is missing in frontmatter");
        }
        String str = value.toString().strip();
        if (str.isEmpty()) {
            throw new IllegalArgumentException("Required field '" + key + "' cannot be empty");
        }
        return str;
    }
}
EOF
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillMetadataParserTest`

Expected: PASS - All tests pass

- [ ] **Step 7: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/ \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/metadata/ \
        server/skillhub-domain/pom.xml
git commit -m "feat(domain): add SkillMetadataParser for SKILL.md frontmatter parsing

- Add SkillMetadata record with name/description/version/body/frontmatter
- Implement SkillMetadataParser using SnakeYAML
- Validate required fields (name, description, version)
- Preserve extension fields (x-astron-*) in frontmatter map
- Add comprehensive test coverage for valid/invalid inputs"
```

### Task 10: SkillPackageValidator + PrePublishValidator — 技能包校验

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/PackageEntry.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/ValidationResult.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/SkillPackageValidator.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/PrePublishValidator.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/NoOpPrePublishValidator.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/validation/SkillPackageValidatorTest.java`

- [ ] **Step 1: 创建 PackageEntry record 和 ValidationResult record**

```bash
mkdir -p server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/PackageEntry.java << 'EOF'
package com.iflytek.skillhub.domain.skill.validation;

public record PackageEntry(
        String path,
        byte[] content,
        long size,
        String contentType
) {}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/ValidationResult.java << 'EOF'
package com.iflytek.skillhub.domain.skill.validation;

import java.util.List;

public record ValidationResult(boolean passed, List<String> errors) {

    public static ValidationResult pass() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public static ValidationResult fail(String error) {
        return new ValidationResult(false, List.of(error));
    }
}
EOF
```

- [ ] **Step 2: 创建 PrePublishValidator 接口和 NoOpPrePublishValidator**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/PrePublishValidator.java << 'EOF'
package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;

import java.util.List;

public interface PrePublishValidator {

    ValidationResult validate(SkillPackageContext context);

    record SkillPackageContext(
            List<PackageEntry> entries,
            SkillMetadata metadata,
            String publisherId,
            Long namespaceId
    ) {}
}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/NoOpPrePublishValidator.java << 'EOF'
package com.iflytek.skillhub.domain.skill.validation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(PrePublishValidator.class)
public class NoOpPrePublishValidator implements PrePublishValidator {

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        return ValidationResult.pass();
    }
}
EOF
```

- [ ] **Step 3: 编写 SkillPackageValidator 测试**

```bash
mkdir -p server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/validation
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/validation/SkillPackageValidatorTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillPackageValidatorTest {

    private SkillPackageValidator validator;

    private static final String VALID_SKILL_MD = """
            ---
            name: my-skill
            description: A test skill
            version: 1.0.0
            ---
            # My Skill
            """;

    @BeforeEach
    void setUp() {
        validator = new SkillPackageValidator();
    }

    @Test
    void shouldPassValidPackage() {
        List<PackageEntry> entries = List.of(
                entry("SKILL.md", VALID_SKILL_MD),
                entry("README.md", "# README")
        );
        ValidationResult result = validator.validate(entries);
        assertTrue(result.passed());
    }

    @Test
    void shouldFailWhenSkillMdMissing() {
        List<PackageEntry> entries = List.of(entry("README.md", "# README"));
        ValidationResult result = validator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("SKILL.md")));
    }

    @Test
    void shouldFailWhenDisallowedFileExtension() {
        List<PackageEntry> entries = List.of(
                entry("SKILL.md", VALID_SKILL_MD),
                entry("malware.exe", "bad")
        );
        ValidationResult result = validator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains(".exe")));
    }

    @Test
    void shouldFailWhenSingleFileExceedsLimit() {
        byte[] largeContent = new byte[1024 * 1024 + 1]; // > 1MB
        List<PackageEntry> entries = List.of(
                entry("SKILL.md", VALID_SKILL_MD),
                new PackageEntry("large.md", largeContent, largeContent.length, "text/markdown")
        );
        ValidationResult result = validator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("size")));
    }

    @Test
    void shouldFailWhenTooManyFiles() {
        List<PackageEntry> entries = new ArrayList<>();
        entries.add(entry("SKILL.md", VALID_SKILL_MD));
        for (int i = 0; i < 100; i++) {
            entries.add(entry("file" + i + ".md", "content"));
        }
        ValidationResult result = validator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("count")));
    }

    @Test
    void shouldFailWhenFrontmatterMissingName() {
        String noName = """
                ---
                description: A test skill
                version: 1.0.0
                ---
                Body
                """;
        List<PackageEntry> entries = List.of(entry("SKILL.md", noName));
        ValidationResult result = validator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("name")));
    }

    private PackageEntry entry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PackageEntry(path, bytes, bytes.length, "text/plain");
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillPackageValidatorTest`

Expected: FAIL - SkillPackageValidator class not found

- [ ] **Step 5: 实现 SkillPackageValidator**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/SkillPackageValidator.java << 'EOF'
package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SkillPackageValidator {

    private static final long DEFAULT_MAX_FILE_SIZE = 1024 * 1024;       // 1MB
    private static final long DEFAULT_MAX_PACKAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_MAX_FILE_COUNT = 100;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml",
            ".js", ".ts", ".py", ".sh",
            ".png", ".jpg", ".svg"
    );

    private final long maxFileSize;
    private final long maxPackageSize;
    private final int maxFileCount;

    public SkillPackageValidator() {
        this(DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_PACKAGE_SIZE, DEFAULT_MAX_FILE_COUNT);
    }

    public SkillPackageValidator(long maxFileSize, long maxPackageSize, int maxFileCount) {
        this.maxFileSize = maxFileSize;
        this.maxPackageSize = maxPackageSize;
        this.maxFileCount = maxFileCount;
    }

    public ValidationResult validate(List<PackageEntry> entries) {
        List<String> errors = new ArrayList<>();

        // 1. SKILL.md 存在性
        PackageEntry skillMd = entries.stream()
                .filter(e -> "SKILL.md".equals(e.path()))
                .findFirst()
                .orElse(null);

        if (skillMd == null) {
            errors.add("SKILL.md is required at the package root");
            return ValidationResult.fail(errors);
        }

        // 2. frontmatter 校验
        try {
            SkillMetadataParser parser = new SkillMetadataParser();
            parser.parse(new String(skillMd.content()));
        } catch (IllegalArgumentException e) {
            errors.add("SKILL.md frontmatter error: " + e.getMessage());
        }

        // 3. 文件数量
        if (entries.size() > maxFileCount) {
            errors.add("File count " + entries.size() + " exceeds maximum " + maxFileCount);
        }

        // 4. 逐文件校验
        long totalSize = 0;
        for (PackageEntry entry : entries) {
            // 文件扩展名
            String ext = getExtension(entry.path());
            if (ext != null && !ALLOWED_EXTENSIONS.contains(ext)) {
                errors.add("File type " + ext + " is not allowed: " + entry.path());
            }

            // 单文件大小
            if (entry.size() > maxFileSize) {
                errors.add("File " + entry.path() + " size " + entry.size() + " exceeds maximum file size");
            }

            totalSize += entry.size();
        }

        // 5. 总包大小
        if (totalSize > maxPackageSize) {
            errors.add("Total package size " + totalSize + " exceeds maximum " + maxPackageSize);
        }

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return null;
        return path.substring(dot).toLowerCase();
    }
}
EOF
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillPackageValidatorTest`

Expected: PASS - All tests pass

- [ ] **Step 7: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/ \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/validation/
git commit -m "feat(domain): add SkillPackageValidator and PrePublishValidator

- Add PackageEntry record for zip entry representation
- Add ValidationResult record with pass/fail factory methods
- Add SkillPackageValidator with configurable limits:
  - SKILL.md existence and frontmatter validation
  - File extension whitelist (.md/.json/.yaml/.js/.ts/.py etc.)
  - Single file size limit (1MB default)
  - Total package size limit (10MB default)
  - File count limit (100 default)
- Add PrePublishValidator SPI interface for extensible validation
- Add NoOpPrePublishValidator as default pass-through implementation
- Add comprehensive test coverage"
```

### Task 11: VisibilityChecker — 可见性检查器

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/VisibilityChecker.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/VisibilityCheckerTest.java`

- [ ] **Step 1: 编写 VisibilityChecker 测试**

```bash
mkdir -p server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/VisibilityCheckerTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VisibilityCheckerTest {

    private final VisibilityChecker checker = new VisibilityChecker();

    private Skill createSkill(SkillVisibility visibility, Long namespaceId, Long ownerId) {
        Skill skill = new Skill(namespaceId, "test-skill", ownerId, visibility);
        return skill;
    }

    @Test
    void publicSkillAccessibleByAnonymous() {
        Skill skill = createSkill(SkillVisibility.PUBLIC, 1L, 100L);
        assertTrue(checker.canAccess(skill, null, Map.of()));
    }

    @Test
    void publicSkillAccessibleByAnyUser() {
        Skill skill = createSkill(SkillVisibility.PUBLIC, 1L, 100L);
        assertTrue(checker.canAccess(skill, 200L, Map.of()));
    }

    @Test
    void namespaceOnlySkillNotAccessibleByAnonymous() {
        Skill skill = createSkill(SkillVisibility.NAMESPACE_ONLY, 1L, 100L);
        assertFalse(checker.canAccess(skill, null, Map.of()));
    }

    @Test
    void namespaceOnlySkillNotAccessibleByNonMember() {
        Skill skill = createSkill(SkillVisibility.NAMESPACE_ONLY, 1L, 100L);
        assertFalse(checker.canAccess(skill, 200L, Map.of(2L, NamespaceRole.MEMBER)));
    }

    @Test
    void namespaceOnlySkillAccessibleByMember() {
        Skill skill = createSkill(SkillVisibility.NAMESPACE_ONLY, 1L, 100L);
        assertTrue(checker.canAccess(skill, 200L, Map.of(1L, NamespaceRole.MEMBER)));
    }

    @Test
    void privateSkillNotAccessibleByAnonymous() {
        Skill skill = createSkill(SkillVisibility.PRIVATE, 1L, 100L);
        assertFalse(checker.canAccess(skill, null, Map.of()));
    }

    @Test
    void privateSkillAccessibleByOwner() {
        Skill skill = createSkill(SkillVisibility.PRIVATE, 1L, 100L);
        assertTrue(checker.canAccess(skill, 100L, Map.of()));
    }

    @Test
    void privateSkillAccessibleByNamespaceAdmin() {
        Skill skill = createSkill(SkillVisibility.PRIVATE, 1L, 100L);
        assertTrue(checker.canAccess(skill, 200L, Map.of(1L, NamespaceRole.ADMIN)));
    }

    @Test
    void privateSkillAccessibleByNamespaceOwner() {
        Skill skill = createSkill(SkillVisibility.PRIVATE, 1L, 100L);
        assertTrue(checker.canAccess(skill, 200L, Map.of(1L, NamespaceRole.OWNER)));
    }

    @Test
    void privateSkillNotAccessibleByRegularMember() {
        Skill skill = createSkill(SkillVisibility.PRIVATE, 1L, 100L);
        assertFalse(checker.canAccess(skill, 200L, Map.of(1L, NamespaceRole.MEMBER)));
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=VisibilityCheckerTest`

Expected: FAIL - VisibilityChecker class not found

- [ ] **Step 3: 实现 VisibilityChecker**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/VisibilityChecker.java << 'EOF'
package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.util.Map;

public class VisibilityChecker {

    public boolean canAccess(Skill skill, String currentUserId,
                             Map<Long, NamespaceRole> userNamespaceRoles) {
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> userNamespaceRoles.containsKey(skill.getNamespaceId());
            case PRIVATE -> isOwner(skill, currentUserId)
                    || isAdminOrAbove(userNamespaceRoles.get(skill.getNamespaceId()));
        };
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && skill.getOwnerId().equals(currentUserId);
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=VisibilityCheckerTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/VisibilityChecker.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/VisibilityCheckerTest.java
git commit -m "feat(domain): add VisibilityChecker for skill access control

- PUBLIC: accessible by anyone including anonymous
- NAMESPACE_ONLY: accessible by namespace members
- PRIVATE: accessible by owner or namespace ADMIN/OWNER
- Add comprehensive test coverage for all visibility scenarios"
```

### Task 12: NamespaceService + NamespaceMemberService — 命名空间领域服务

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberService.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceServiceTest.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberServiceTest.java`

- [ ] **Step 1: 编写 NamespaceService 测试**

```bash
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceServiceTest.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private NamespaceMemberRepository memberRepository;

    private NamespaceService service;

    @BeforeEach
    void setUp() {
        service = new NamespaceService(namespaceRepository, memberRepository);
    }

    @Test
    void shouldCreateNamespaceAndAssignOwner() {
        when(namespaceRepository.findBySlug("my-team")).thenReturn(Optional.empty());
        when(namespaceRepository.save(any(Namespace.class))).thenAnswer(inv -> {
            Namespace ns = inv.getArgument(0);
            return ns;
        });
        when(memberRepository.save(any(NamespaceMember.class))).thenAnswer(inv -> inv.getArgument(0));

        Namespace result = service.createNamespace("my-team", "My Team", "A team namespace", 1L);

        assertEquals("my-team", result.getSlug());
        assertEquals(NamespaceType.TEAM, result.getType());
        verify(memberRepository).save(argThat(m ->
                m.getRole() == NamespaceRole.OWNER && m.getUserId().equals(1L)));
    }

    @Test
    void shouldRejectDuplicateSlug() {
        when(namespaceRepository.findBySlug("existing")).thenReturn(Optional.of(new Namespace()));

        assertThrows(IllegalArgumentException.class,
                () -> service.createNamespace("existing", "Existing", "desc", 1L));
    }

    @Test
    void shouldRejectReservedSlug() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createNamespace("admin", "Admin", "desc", 1L));
    }

    @Test
    void shouldUpdateNamespace() {
        Namespace ns = new Namespace();
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(namespaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Namespace result = service.updateNamespace(1L, "New Name", "New Desc", "https://avatar.url");

        assertEquals("New Name", result.getDisplayName());
        assertEquals("New Desc", result.getDescription());
        assertEquals("https://avatar.url", result.getAvatarUrl());
    }

    @Test
    void shouldGetNamespaceBySlug() {
        Namespace ns = new Namespace();
        when(namespaceRepository.findBySlug("my-team")).thenReturn(Optional.of(ns));

        Namespace result = service.getNamespaceBySlug("my-team");
        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenNamespaceNotFound() {
        when(namespaceRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getNamespaceBySlug("nonexistent"));
    }
}
EOF
```

- [ ] **Step 2: 编写 NamespaceMemberService 测试**

```bash
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberServiceTest.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceMemberServiceTest {

    @Mock
    private NamespaceMemberRepository memberRepository;

    private NamespaceMemberService service;

    @BeforeEach
    void setUp() {
        service = new NamespaceMemberService(memberRepository);
    }

    @Test
    void shouldAddMember() {
        when(memberRepository.findByNamespaceIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NamespaceMember result = service.addMember(1L, 2L, NamespaceRole.MEMBER);

        assertEquals(NamespaceRole.MEMBER, result.getRole());
        assertEquals(2L, result.getUserId());
    }

    @Test
    void shouldRejectAddingAsOwner() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addMember(1L, 2L, NamespaceRole.OWNER));
    }

    @Test
    void shouldRejectDuplicateMember() {
        when(memberRepository.findByNamespaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(new NamespaceMember()));

        assertThrows(IllegalArgumentException.class,
                () -> service.addMember(1L, 2L, NamespaceRole.MEMBER));
    }

    @Test
    void shouldRemoveMember() {
        NamespaceMember member = new NamespaceMember();
        member.setRole(NamespaceRole.MEMBER);
        when(memberRepository.findByNamespaceIdAndUserId(1L, 2L)).thenReturn(Optional.of(member));

        assertDoesNotThrow(() -> service.removeMember(1L, 2L));
        verify(memberRepository).deleteByNamespaceIdAndUserId(1L, 2L);
    }

    @Test
    void shouldRejectRemovingOwner() {
        NamespaceMember owner = new NamespaceMember();
        owner.setRole(NamespaceRole.OWNER);
        when(memberRepository.findByNamespaceIdAndUserId(1L, 2L)).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class,
                () -> service.removeMember(1L, 2L));
    }

    @Test
    void shouldTransferOwnership() {
        NamespaceMember currentOwner = new NamespaceMember();
        currentOwner.setRole(NamespaceRole.OWNER);
        NamespaceMember newOwner = new NamespaceMember();
        newOwner.setRole(NamespaceRole.ADMIN);

        when(memberRepository.findByNamespaceIdAndUserId(1L, 10L)).thenReturn(Optional.of(currentOwner));
        when(memberRepository.findByNamespaceIdAndUserId(1L, 20L)).thenReturn(Optional.of(newOwner));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transferOwnership(1L, 10L, 20L);

        assertEquals(NamespaceRole.ADMIN, currentOwner.getRole());
        assertEquals(NamespaceRole.OWNER, newOwner.getRole());
    }

    @Test
    void shouldUpdateMemberRole() {
        NamespaceMember member = new NamespaceMember();
        member.setRole(NamespaceRole.MEMBER);
        when(memberRepository.findByNamespaceIdAndUserId(1L, 2L)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMemberRole(1L, 2L, NamespaceRole.ADMIN);

        assertEquals(NamespaceRole.ADMIN, member.getRole());
    }

    @Test
    void shouldRejectSettingOwnerViaUpdateRole() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateMemberRole(1L, 2L, NamespaceRole.OWNER));
    }

    @Test
    void shouldGetMemberRole() {
        NamespaceMember member = new NamespaceMember();
        member.setRole(NamespaceRole.ADMIN);
        when(memberRepository.findByNamespaceIdAndUserId(1L, 2L)).thenReturn(Optional.of(member));

        Optional<NamespaceRole> role = service.getMemberRole(1L, 2L);
        assertTrue(role.isPresent());
        assertEquals(NamespaceRole.ADMIN, role.get());
    }
}
EOF
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest="NamespaceServiceTest,NamespaceMemberServiceTest"`

Expected: FAIL - NamespaceService/NamespaceMemberService class not found

- [ ] **Step 4: 实现 NamespaceService**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NamespaceService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository memberRepository;

    public NamespaceService(NamespaceRepository namespaceRepository,
                            NamespaceMemberRepository memberRepository) {
        this.namespaceRepository = namespaceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Namespace createNamespace(String slug, String displayName,
                                     String description, Long creatorUserId) {
        SlugValidator.validate(slug);

        namespaceRepository.findBySlug(slug).ifPresent(ns -> {
            throw new IllegalArgumentException("Namespace slug '" + slug + "' already exists");
        });

        Namespace namespace = new Namespace();
        namespace.setSlug(slug);
        namespace.setDisplayName(displayName);
        namespace.setDescription(description);
        namespace.setType(NamespaceType.TEAM);
        namespace.setCreatedBy(creatorUserId);
        Namespace saved = namespaceRepository.save(namespace);

        NamespaceMember ownerMember = new NamespaceMember();
        ownerMember.setNamespaceId(saved.getId());
        ownerMember.setUserId(creatorUserId);
        ownerMember.setRole(NamespaceRole.OWNER);
        memberRepository.save(ownerMember);

        return saved;
    }

    @Transactional
    public Namespace updateNamespace(Long namespaceId, String displayName,
                                     String description, String avatarUrl) {
        Namespace ns = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + namespaceId));
        ns.setDisplayName(displayName);
        ns.setDescription(description);
        ns.setAvatarUrl(avatarUrl);
        return namespaceRepository.save(ns);
    }

    public Namespace getNamespaceBySlug(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + slug));
    }
}
EOF
```

- [ ] **Step 5: 实现 NamespaceMemberService**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberService.java << 'EOF'
package com.iflytek.skillhub.domain.namespace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class NamespaceMemberService {

    private final NamespaceMemberRepository memberRepository;

    public NamespaceMemberService(NamespaceMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public NamespaceMember addMember(Long namespaceId, String userId, NamespaceRole role) {
        if (role == NamespaceRole.OWNER) {
            throw new IllegalArgumentException("Cannot directly add member as OWNER, use transferOwnership");
        }
        memberRepository.findByNamespaceIdAndUserId(namespaceId, userId).ifPresent(m -> {
            throw new IllegalArgumentException("User " + userId + " is already a member of namespace " + namespaceId);
        });

        NamespaceMember member = new NamespaceMember();
        member.setNamespaceId(namespaceId);
        member.setUserId(userId);
        member.setRole(role);
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long namespaceId, String userId) {
        NamespaceMember member = memberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (member.getRole() == NamespaceRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove namespace OWNER");
        }
        memberRepository.deleteByNamespaceIdAndUserId(namespaceId, userId);
    }

    @Transactional
    public void updateMemberRole(Long namespaceId, String userId, NamespaceRole newRole) {
        if (newRole == NamespaceRole.OWNER) {
            throw new IllegalArgumentException("Cannot set OWNER via updateMemberRole, use transferOwnership");
        }
        NamespaceMember member = memberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        member.setRole(newRole);
        memberRepository.save(member);
    }

    @Transactional
    public void transferOwnership(Long namespaceId, String currentOwnerId, String newOwnerId) {
        NamespaceMember currentOwner = memberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("Current owner not found"));
        NamespaceMember newOwner = memberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("New owner not found"));

        currentOwner.setRole(NamespaceRole.ADMIN);
        newOwner.setRole(NamespaceRole.OWNER);
        memberRepository.save(currentOwner);
        memberRepository.save(newOwner);
    }

    public Optional<NamespaceRole> getMemberRole(Long namespaceId, String userId) {
        return memberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .map(NamespaceMember::getRole);
    }
}
EOF
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest="NamespaceServiceTest,NamespaceMemberServiceTest"`

Expected: PASS - All tests pass

- [ ] **Step 7: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java \
        server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceServiceTest.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberServiceTest.java
git commit -m "feat(domain): add NamespaceService and NamespaceMemberService

- NamespaceService: create (with slug validation + auto OWNER), update, getBySlug
- NamespaceMemberService: add/remove/updateRole/transferOwnership/getMemberRole
- OWNER cannot be removed or set via updateRole
- transferOwnership: current OWNER → ADMIN, target → OWNER
- Add comprehensive unit tests with Mockito"
```

### Task 13: NamespaceController + 成员管理 API

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/MemberRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/MemberResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/NamespaceControllerTest.java`

- [ ] **Step 1: 创建 DTO 类**

```bash
mkdir -p server/skillhub-app/src/main/java/com/iflytek/skillhub/dto

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceRequest.java << 'EOF'
package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NamespaceRequest(
        @NotBlank @Size(min = 2, max = 64) String slug,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 512) String description
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.Namespace;

import java.time.LocalDateTime;

public record NamespaceResponse(
        Long id,
        String slug,
        String displayName,
        String description,
        String type,
        String avatarUrl,
        String status,
        LocalDateTime createdAt
) {
    public static NamespaceResponse from(Namespace ns) {
        return new NamespaceResponse(
                ns.getId(), ns.getSlug(), ns.getDisplayName(),
                ns.getDescription(), ns.getType().name(),
                ns.getAvatarUrl(), ns.getStatus().name(), ns.getCreatedAt()
        );
    }
}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/MemberRequest.java << 'EOF'
package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberRequest(
        @NotNull String userId,
        @NotBlank String role
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/MemberResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        Long namespaceId,
        String userId,
        String role,
        LocalDateTime createdAt
) {
    public static MemberResponse from(NamespaceMember m) {
        return new MemberResponse(
                m.getId(), m.getNamespaceId(), m.getUserId(),
                m.getRole().name(), m.getCreatedAt()
        );
    }
}
EOF
```

- [ ] **Step 2: 创建 NamespaceController**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java << 'EOF'
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/namespaces")
public class NamespaceController {

    private final NamespaceService namespaceService;
    private final NamespaceMemberService memberService;
    private final NamespaceRepository namespaceRepository;

    public NamespaceController(NamespaceService namespaceService,
                               NamespaceMemberService memberService,
                               NamespaceRepository namespaceRepository) {
        this.namespaceService = namespaceService;
        this.memberService = memberService;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping
    public ResponseEntity<?> listNamespaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Namespace> result = namespaceRepository.findByStatus(
                NamespaceStatus.ACTIVE, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "data", Map.of(
                        "items", result.getContent().stream()
                                .map(NamespaceResponse::from).toList(),
                        "total", result.getTotalElements(),
                        "page", page,
                        "size", size
                )
        ));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getNamespace(@PathVariable String slug) {
        Namespace ns = namespaceService.getNamespaceBySlug(slug);
        return ResponseEntity.ok(Map.of("code", 0, "data", NamespaceResponse.from(ns)));
    }

    @PostMapping
    public ResponseEntity<?> createNamespace(
            @Valid @RequestBody NamespaceRequest request,
            @AuthenticationPrincipal String userId) {
        Namespace ns = namespaceService.createNamespace(
                request.slug(), request.displayName(), request.description(), userId);
        return ResponseEntity.ok(Map.of("code", 0, "data", NamespaceResponse.from(ns)));
    }

    @PutMapping("/{slug}")
    public ResponseEntity<?> updateNamespace(
            @PathVariable String slug,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        Namespace ns = namespaceService.getNamespaceBySlug(slug);
        Namespace updated = namespaceService.updateNamespace(
                ns.getId(),
                body.getOrDefault("displayName", ns.getDisplayName()),
                body.getOrDefault("description", ns.getDescription()),
                body.getOrDefault("avatarUrl", ns.getAvatarUrl()));
        return ResponseEntity.ok(Map.of("code", 0, "data", NamespaceResponse.from(updated)));
    }

    @GetMapping("/{slug}/members")
    public ResponseEntity<?> listMembers(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Namespace ns = namespaceService.getNamespaceBySlug(slug);
        Page<NamespaceMember> members = memberService.listMembers(
                ns.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "data", Map.of(
                        "items", members.getContent().stream()
                                .map(MemberResponse::from).toList(),
                        "total", members.getTotalElements()
                )
        ));
    }

    @PostMapping("/{slug}/members")
    public ResponseEntity<?> addMember(
            @PathVariable String slug,
            @Valid @RequestBody MemberRequest request) {
        Namespace ns = namespaceService.getNamespaceBySlug(slug);
        NamespaceMember member = memberService.addMember(
                ns.getId(), request.userId(), NamespaceRole.valueOf(request.role()));
        return ResponseEntity.ok(Map.of("code", 0, "data", MemberResponse.from(member)));
    }

    @DeleteMapping("/{slug}/members/{userId}")
    public ResponseEntity<?> removeMember(
            @PathVariable String slug,
            @PathVariable String userId) {
        Namespace ns = namespaceService.getNamespaceBySlug(slug);
        memberService.removeMember(ns.getId(), userId);
        return ResponseEntity.ok(Map.of("code", 0, "message", "Member removed"));
    }

    @PutMapping("/{slug}/members/{userId}/role")
    public ResponseEntity<?> updateMemberRole(
            @PathVariable String slug,
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        Namespace ns = namespaceService.getNamespaceBySlug(slug);
        memberService.updateMemberRole(ns.getId(), userId,
                NamespaceRole.valueOf(body.get("role")));
        return ResponseEntity.ok(Map.of("code", 0, "message", "Role updated"));
    }
}
EOF
```

- [ ] **Step 3: 补充 NamespaceMemberService.listMembers 方法**

在 `NamespaceMemberService.java` 中添加：

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public Page<NamespaceMember> listMembers(Long namespaceId, Pageable pageable) {
    return memberRepository.findByNamespaceId(namespaceId, pageable);
}
```

- [ ] **Step 4: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java \
        server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberService.java
git commit -m "feat(api): add NamespaceController with CRUD and member management

- Add NamespaceRequest/Response, MemberRequest/Response DTOs
- GET/POST namespaces, GET/PUT namespace by slug
- GET/POST/DELETE members, PUT member role
- Add listMembers method to NamespaceMemberService"
```

### Task 14: Domain Events — 领域事件定义

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/SkillPublishedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/SkillDownloadedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/SkillStatusChangedEvent.java`

- [ ] **Step 1: 创建领域事件 record**

```bash
mkdir -p server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/SkillPublishedEvent.java << 'EOF'
package com.iflytek.skillhub.domain.event;

public record SkillPublishedEvent(Long skillId, Long versionId, String publisherId) {}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/SkillDownloadedEvent.java << 'EOF'
package com.iflytek.skillhub.domain.event;

public record SkillDownloadedEvent(Long skillId, Long versionId) {}
EOF

cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/SkillStatusChangedEvent.java << 'EOF'
package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.skill.SkillStatus;

public record SkillStatusChangedEvent(Long skillId, SkillStatus oldStatus, SkillStatus newStatus) {}
EOF
```

- [ ] **Step 2: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/
git commit -m "feat(domain): add domain event records

- SkillPublishedEvent: triggered after skill version published
- SkillDownloadedEvent: triggered after skill downloaded
- SkillStatusChangedEvent: triggered on skill status transitions"
```

### Task 15: SkillPublishService — 技能发布服务

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java`

- [ ] **Step 1: 编写 SkillPublishService 测试**

```bash
mkdir -p server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillPublishServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository memberRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository versionRepository;
    @Mock private SkillFileRepository fileRepository;
    @Mock private ObjectStorageService storageService;
    @Mock private PrePublishValidator prePublishValidator;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SkillPublishService publishService;

    private static final String VALID_SKILL_MD = """
            ---
            name: my-skill
            description: A test skill
            version: 1.0.0
            ---
            # My Skill
            Body content.
            """;

    @BeforeEach
    void setUp() {
        publishService = new SkillPublishService(
                namespaceRepository, memberRepository,
                skillRepository, versionRepository, fileRepository,
                storageService, new SkillPackageValidator(),
                new SkillMetadataParser(), prePublishValidator,
                eventPublisher);
    }

    @Test
    void shouldPublishNewSkill() {
        Namespace ns = new Namespace();
        ns.setSlug("global");
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(memberRepository.findByNamespaceIdAndUserId(any(), eq(1L)))
                .thenReturn(Optional.of(new NamespaceMember()));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.empty());
        when(skillRepository.save(any())).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            return s;
        });
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());

        List<PackageEntry> entries = List.of(
                entry("SKILL.md", VALID_SKILL_MD),
                entry("README.md", "# README")
        );

        SkillVersion result = publishService.publishFromEntries("global", entries, 1L, SkillVisibility.PUBLIC);

        assertNotNull(result);
        assertEquals("1.0.0", result.getVersion());
        assertEquals(SkillVersionStatus.PUBLISHED, result.getStatus());
        verify(storageService, atLeastOnce()).putObject(any(), any(), anyLong(), any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void shouldRejectWhenNamespaceNotFound() {
        when(namespaceRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        List<PackageEntry> entries = List.of(entry("SKILL.md", VALID_SKILL_MD));

        assertThrows(IllegalArgumentException.class,
                () -> publishService.publishFromEntries("nonexistent", entries, 1L, SkillVisibility.PUBLIC));
    }

    @Test
    void shouldRejectWhenNotMember() {
        Namespace ns = new Namespace();
        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(ns));
        when(memberRepository.findByNamespaceIdAndUserId(any(), eq(1L)))
                .thenReturn(Optional.empty());

        List<PackageEntry> entries = List.of(entry("SKILL.md", VALID_SKILL_MD));

        assertThrows(IllegalArgumentException.class,
                () -> publishService.publishFromEntries("team", entries, 1L, SkillVisibility.PUBLIC));
    }

    private PackageEntry entry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PackageEntry(path, bytes, bytes.length, "text/plain");
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillPublishServiceTest`

Expected: FAIL - SkillPublishService class not found

- [ ] **Step 3: 实现 SkillPublishService**

```bash
mkdir -p server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java << 'JAVAEOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class SkillPublishService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository memberRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillFileRepository fileRepository;
    private final ObjectStorageService storageService;
    private final SkillPackageValidator packageValidator;
    private final SkillMetadataParser metadataParser;
    private final PrePublishValidator prePublishValidator;
    private final ApplicationEventPublisher eventPublisher;

    public SkillPublishService(NamespaceRepository namespaceRepository,
                               NamespaceMemberRepository memberRepository,
                               SkillRepository skillRepository,
                               SkillVersionRepository versionRepository,
                               SkillFileRepository fileRepository,
                               ObjectStorageService storageService,
                               SkillPackageValidator packageValidator,
                               SkillMetadataParser metadataParser,
                               PrePublishValidator prePublishValidator,
                               ApplicationEventPublisher eventPublisher) {
        this.namespaceRepository = namespaceRepository;
        this.memberRepository = memberRepository;
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.packageValidator = packageValidator;
        this.metadataParser = metadataParser;
        this.prePublishValidator = prePublishValidator;
        this.eventPublisher = eventPublisher;
    }
JAVAEOF
```

在同一文件末尾追加 `publishFromEntries` 方法（使用 `cat >>` 追加）：

```bash
cat >> server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java << 'JAVAEOF'

    @Transactional
    public SkillVersion publishFromEntries(String namespaceSlug,
                                            List<PackageEntry> entries,
                                            String publisherId,
                                            SkillVisibility visibility) {
        // ① 解析 namespace
        Namespace ns = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + namespaceSlug));

        // ② 权限校验
        memberRepository.findByNamespaceIdAndUserId(ns.getId(), publisherId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + publisherId + " is not a member of namespace " + namespaceSlug));

        // ③ 技能包校验
        ValidationResult validationResult = packageValidator.validate(entries);
        if (!validationResult.passed()) {
            throw new IllegalArgumentException("Package validation failed: " +
                    String.join("; ", validationResult.errors()));
        }

        // ④ 解析 SKILL.md
        PackageEntry skillMdEntry = entries.stream()
                .filter(e -> "SKILL.md".equals(e.path()))
                .findFirst().orElseThrow();
        SkillMetadata metadata = metadataParser.parse(
                new String(skillMdEntry.content(), StandardCharsets.UTF_8));

        // ⑤ PrePublishValidator
        ValidationResult preResult = prePublishValidator.validate(
                new PrePublishValidator.SkillPackageContext(entries, metadata, publisherId, ns.getId()));
        if (!preResult.passed()) {
            throw new IllegalArgumentException("Pre-publish validation failed: " +
                    String.join("; ", preResult.errors()));
        }

        // ⑥ 创建/关联 skill
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), metadata.name())
                .orElseGet(() -> {
                    Skill s = new Skill(ns.getId(), metadata.name(), publisherId, visibility);
                    s.setDisplayName(metadata.name());
                    s.setSummary(metadata.description());
                    s.setCreatedBy(publisherId);
                    return skillRepository.save(s);
                });

        // ⑦ 版本冲突检查
        versionRepository.findBySkillIdAndVersion(skill.getId(), metadata.version())
                .ifPresent(v -> {
                    throw new IllegalArgumentException("Version " + metadata.version() + " already exists");
                });

        // ⑧ 创建版本
        SkillVersion version = new SkillVersion(skill.getId(), metadata.version(), publisherId);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        version.setChangelog(metadata.description());
        version.setParsedMetadataJson(metadata.frontmatter().toString());
        version = versionRepository.save(version);

        // ⑨ 写入对象存储 + 持久化文件记录
        long totalSize = 0;
        List<SkillFile> files = new ArrayList<>();
        for (PackageEntry entry : entries) {
            String storageKey = String.format("skills/%d/%d/%s",
                    skill.getId(), version.getId(), entry.path());
            storageService.putObject(storageKey,
                    new ByteArrayInputStream(entry.content()),
                    entry.size(), entry.contentType());

            String sha256 = sha256Hex(entry.content());
            files.add(new SkillFile(version.getId(), entry.path(),
                    entry.size(), entry.contentType(), sha256, storageKey));
            totalSize += entry.size();
        }
        fileRepository.saveAll(files);

        // ⑩ 更新版本统计
        version.setFileCount(files.size());
        version.setTotalSize(totalSize);
        versionRepository.save(version);

        // ⑪ 更新 skill
        skill.setLatestVersionId(version.getId());
        skill.setDisplayName(metadata.name());
        skill.setSummary(metadata.description());
        skill.setUpdatedBy(publisherId);
        skillRepository.save(skill);

        // ⑫ 发布事件
        eventPublisher.publishEvent(
                new SkillPublishedEvent(skill.getId(), version.getId(), publisherId));

        return version;
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
JAVAEOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillPublishServiceTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java
git commit -m "feat(domain): add SkillPublishService with full publish flow

- Namespace lookup + membership check
- Package validation + SKILL.md parsing
- PrePublishValidator extension point
- Create/reuse skill record, version conflict check
- Upload files to object storage with SHA-256
- Persist skill_version + skill_file records
- Update skill.latest_version_id
- Publish SkillPublishedEvent for async processing"
```

### Task 16: SkillQueryService — 技能查询服务

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillQueryServiceTest.java`

- [ ] **Step 1: 编写 SkillQueryService 测试**

```bash
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillQueryServiceTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillQueryServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository memberRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository versionRepository;
    @Mock private SkillFileRepository fileRepository;
    @Mock private ObjectStorageService storageService;

    private SkillQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new SkillQueryService(
                namespaceRepository, memberRepository,
                skillRepository, versionRepository,
                fileRepository, storageService,
                new VisibilityChecker());
    }

    @Test
    void shouldGetSkillDetail() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", 100L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(version));

        SkillQueryService.SkillDetailDTO result = queryService.getSkillDetail(
                "global", "my-skill", null, Map.of());

        assertNotNull(result);
        assertEquals("my-skill", result.slug());
        assertEquals("1.0.0", result.latestVersion());
    }

    @Test
    void shouldRejectAccessToPrivateSkillByAnonymous() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "secret", 100L, SkillVisibility.PRIVATE);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("secret")))
                .thenReturn(Optional.of(skill));

        assertThrows(IllegalArgumentException.class,
                () -> queryService.getSkillDetail("team", "secret", null, Map.of()));
    }

    @Test
    void shouldListSkillsByNamespace() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "skill-1", 100L, SkillVisibility.PUBLIC);
        Page<Skill> page = new PageImpl<>(List.of(skill));

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndStatus(any(), eq(SkillStatus.ACTIVE), any()))
                .thenReturn(page);

        Page<Skill> result = queryService.listSkillsByNamespace(
                "global", null, Map.of(), PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldListFiles() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        SkillVersion version = new SkillVersion(1L, "1.0.0", 100L);
        SkillFile file = new SkillFile(1L, "SKILL.md", 100L, "text/markdown", "abc123", "key");

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(versionRepository.findBySkillIdAndVersion(any(), eq("1.0.0")))
                .thenReturn(Optional.of(version));
        when(fileRepository.findByVersionId(any())).thenReturn(List.of(file));

        List<SkillFile> files = queryService.listFiles("global", "my-skill", "1.0.0");

        assertEquals(1, files.size());
        assertEquals("SKILL.md", files.get(0).getFilePath());
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillQueryServiceTest`

Expected: FAIL - SkillQueryService class not found

- [ ] **Step 3: 实现 SkillQueryService**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class SkillQueryService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository memberRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillFileRepository fileRepository;
    private final ObjectStorageService storageService;
    private final VisibilityChecker visibilityChecker;

    public SkillQueryService(NamespaceRepository namespaceRepository,
                             NamespaceMemberRepository memberRepository,
                             SkillRepository skillRepository,
                             SkillVersionRepository versionRepository,
                             SkillFileRepository fileRepository,
                             ObjectStorageService storageService,
                             VisibilityChecker visibilityChecker) {
        this.namespaceRepository = namespaceRepository;
        this.memberRepository = memberRepository;
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.visibilityChecker = visibilityChecker;
    }

    public record SkillDetailDTO(
            Long id, String slug, String displayName, String summary,
            String visibility, String status, Long downloadCount,
            Integer starCount, String latestVersion, Long namespaceId
    ) {}

    public SkillDetailDTO getSkillDetail(String namespaceSlug, String skillSlug,
                                          String currentUserId,
                                          Map<Long, NamespaceRole> userNsRoles) {
        Namespace ns = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new IllegalArgumentException("Access denied to skill: " + skillSlug);
        }

        String latestVersion = null;
        if (skill.getLatestVersionId() != null) {
            latestVersion = versionRepository.findById(skill.getLatestVersionId())
                    .map(SkillVersion::getVersion).orElse(null);
        }

        return new SkillDetailDTO(
                skill.getId(), skill.getSlug(), skill.getDisplayName(),
                skill.getSummary(), skill.getVisibility().name(),
                skill.getStatus().name(), skill.getDownloadCount(),
                skill.getStarCount(), latestVersion, skill.getNamespaceId());
    }

    public Page<Skill> listSkillsByNamespace(String namespaceSlug,
                                              String currentUserId,
                                              Map<Long, NamespaceRole> userNsRoles,
                                              Pageable pageable) {
        Namespace ns = findNamespace(namespaceSlug);
        return skillRepository.findByNamespaceIdAndStatus(
                ns.getId(), SkillStatus.ACTIVE, pageable);
    }

    public List<SkillFile> listFiles(String namespaceSlug, String skillSlug, String version) {
        Namespace ns = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));
        SkillVersion sv = versionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + version));
        return fileRepository.findByVersionId(sv.getId());
    }

    public InputStream getFileContent(String namespaceSlug, String skillSlug,
                                       String version, String filePath) {
        Namespace ns = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        SkillVersion sv = versionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        String storageKey = String.format("skills/%d/%d/%s", skill.getId(), sv.getId(), filePath);
        return storageService.getObject(storageKey);
    }

    public Page<SkillVersion> listVersions(String namespaceSlug, String skillSlug,
                                            Pageable pageable) {
        Namespace ns = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        return versionRepository.findBySkillIdAndStatus(
                skill.getId(), SkillVersionStatus.PUBLISHED, pageable);
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + slug));
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillQueryServiceTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillQueryServiceTest.java
git commit -m "feat(domain): add SkillQueryService for skill detail/list/files/versions

- getSkillDetail with visibility check
- listSkillsByNamespace with ACTIVE filter
- listFiles by namespace/slug/version
- getFileContent from object storage
- listVersions (PUBLISHED only)
- Add unit tests with Mockito"
```

### Task 17: SkillDownloadService — 技能下载服务

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadService.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadServiceTest.java`

- [ ] **Step 1: 编写 SkillDownloadService 测试**

```bash
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadServiceTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillDownloadServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository versionRepository;
    @Mock private SkillTagRepository tagRepository;
    @Mock private ObjectStorageService storageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SkillDownloadService downloadService;

    @BeforeEach
    void setUp() {
        downloadService = new SkillDownloadService(
                namespaceRepository, skillRepository, versionRepository,
                tagRepository, storageService, new VisibilityChecker(),
                eventPublisher);
    }

    @Test
    void shouldDownloadLatestVersion() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", 100L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(storageService.getObject(any()))
                .thenReturn(new ByteArrayInputStream("zip-data".getBytes()));

        SkillDownloadService.DownloadResult result = downloadService.downloadLatest(
                "global", "my-skill", null, Map.of());

        assertNotNull(result);
        assertEquals("my-skill-1.0.0.zip", result.filename());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void shouldDownloadByTag() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        SkillTag tag = new SkillTag(1L, "stable", 10L, 100L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", 100L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(tagRepository.findBySkillIdAndTagName(any(), eq("stable")))
                .thenReturn(Optional.of(tag));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(storageService.getObject(any()))
                .thenReturn(new ByteArrayInputStream("zip-data".getBytes()));

        SkillDownloadService.DownloadResult result = downloadService.downloadByTag(
                "global", "my-skill", "stable", null, Map.of());

        assertNotNull(result);
        verify(eventPublisher).publishEvent(any());
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillDownloadServiceTest`

Expected: FAIL - SkillDownloadService class not found

- [ ] **Step 3: 实现 SkillDownloadService**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadService.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

@Service
public class SkillDownloadService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillTagRepository tagRepository;
    private final ObjectStorageService storageService;
    private final VisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher eventPublisher;

    public record DownloadResult(
            InputStream content, String filename,
            long contentLength, String contentType
    ) {}

    public SkillDownloadService(NamespaceRepository namespaceRepository,
                                SkillRepository skillRepository,
                                SkillVersionRepository versionRepository,
                                SkillTagRepository tagRepository,
                                ObjectStorageService storageService,
                                VisibilityChecker visibilityChecker,
                                ApplicationEventPublisher eventPublisher) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.tagRepository = tagRepository;
        this.storageService = storageService;
        this.visibilityChecker = visibilityChecker;
        this.eventPublisher = eventPublisher;
    }

    public DownloadResult downloadLatest(String namespaceSlug, String skillSlug,
                                          String currentUserId,
                                          Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findAndCheckAccess(namespaceSlug, skillSlug, currentUserId, userNsRoles);
        if (skill.getLatestVersionId() == null) {
            throw new IllegalArgumentException("No published version for skill: " + skillSlug);
        }
        SkillVersion version = versionRepository.findById(skill.getLatestVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        return buildDownloadResult(skill, version);
    }

    public DownloadResult downloadVersion(String namespaceSlug, String skillSlug,
                                           String versionStr, String currentUserId,
                                           Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findAndCheckAccess(namespaceSlug, skillSlug, currentUserId, userNsRoles);
        SkillVersion version = versionRepository.findBySkillIdAndVersion(skill.getId(), versionStr)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionStr));
        return buildDownloadResult(skill, version);
    }

    public DownloadResult downloadByTag(String namespaceSlug, String skillSlug,
                                         String tagName, String currentUserId,
                                         Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findAndCheckAccess(namespaceSlug, skillSlug, currentUserId, userNsRoles);
        SkillTag tag = tagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagName));
        SkillVersion version = versionRepository.findById(tag.getVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Version not found for tag"));
        return buildDownloadResult(skill, version);
    }

    private Skill findAndCheckAccess(String namespaceSlug, String skillSlug,
                                      String currentUserId,
                                      Map<Long, NamespaceRole> userNsRoles) {
        Namespace ns = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found"));
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new IllegalArgumentException("Access denied");
        }
        return skill;
    }

    private DownloadResult buildDownloadResult(Skill skill, SkillVersion version) {
        String bundleKey = String.format("packages/%d/%d/bundle.zip",
                skill.getId(), version.getId());
        InputStream content = storageService.getObject(bundleKey);
        String filename = skill.getSlug() + "-" + version.getVersion() + ".zip";
        eventPublisher.publishEvent(
                new SkillDownloadedEvent(skill.getId(), version.getId()));
        return new DownloadResult(content, filename, version.getTotalSize(), "application/zip");
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillDownloadServiceTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadServiceTest.java
git commit -m "feat(domain): add SkillDownloadService with latest/version/tag download

- downloadLatest: get bundle.zip for latest published version
- downloadVersion: get bundle.zip for specific version
- downloadByTag: resolve tag to version then download
- Visibility check before download
- Publish SkillDownloadedEvent for async count update
- Add unit tests with Mockito"
```

### Task 18: SkillTagService — 标签管理服务

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillTagService.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillTagServiceTest.java`

- [ ] **Step 1: 编写 SkillTagService 测试**

```bash
cat > server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillTagServiceTest.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillTagServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository versionRepository;
    @Mock private SkillTagRepository tagRepository;

    private SkillTagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new SkillTagService(
                namespaceRepository, skillRepository, versionRepository, tagRepository);
    }

    @Test
    void shouldCreateNewTag() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        SkillVersion version = new SkillVersion(1L, "1.0.0", 100L);
        version.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(versionRepository.findBySkillIdAndVersion(any(), eq("1.0.0")))
                .thenReturn(Optional.of(version));
        when(tagRepository.findBySkillIdAndTagName(any(), eq("stable")))
                .thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillTag result = tagService.createOrMoveTag(
                "global", "my-skill", "stable", "1.0.0", 100L);

        assertNotNull(result);
        assertEquals("stable", result.getTagName());
    }

    @Test
    void shouldRejectLatestTagName() {
        assertThrows(IllegalArgumentException.class,
                () -> tagService.createOrMoveTag(
                        "global", "my-skill", "latest", "1.0.0", 100L));
    }

    @Test
    void shouldDeleteTag() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        SkillTag tag = new SkillTag(1L, "old-tag", 10L, 100L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(tagRepository.findBySkillIdAndTagName(any(), eq("old-tag")))
                .thenReturn(Optional.of(tag));

        assertDoesNotThrow(() -> tagService.deleteTag(
                "global", "my-skill", "old-tag", 100L));
        verify(tagRepository).delete(tag);
    }

    @Test
    void shouldRejectDeleteLatestTag() {
        assertThrows(IllegalArgumentException.class,
                () -> tagService.deleteTag(
                        "global", "my-skill", "latest", 100L));
    }

    @Test
    void shouldListTags() {
        Namespace ns = new Namespace();
        Skill skill = new Skill(1L, "my-skill", 100L, SkillVisibility.PUBLIC);
        SkillTag tag = new SkillTag(1L, "stable", 10L, 100L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("my-skill")))
                .thenReturn(Optional.of(skill));
        when(tagRepository.findBySkillId(any())).thenReturn(List.of(tag));

        List<SkillTag> tags = tagService.listTags("global", "my-skill");
        assertEquals(1, tags.size());
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillTagServiceTest`

Expected: FAIL - SkillTagService class not found

- [ ] **Step 3: 实现 SkillTagService**

```bash
cat > server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillTagService.java << 'EOF'
package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkillTagService {

    private static final String RESERVED_TAG_LATEST = "latest";

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillTagRepository tagRepository;

    public SkillTagService(NamespaceRepository namespaceRepository,
                           SkillRepository skillRepository,
                           SkillVersionRepository versionRepository,
                           SkillTagRepository tagRepository) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.tagRepository = tagRepository;
    }

    public List<SkillTag> listTags(String namespaceSlug, String skillSlug) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        return tagRepository.findBySkillId(skill.getId());
    }

    @Transactional
    public SkillTag createOrMoveTag(String namespaceSlug, String skillSlug,
                                     String tagName, String targetVersion,
                                     Long operatorId) {
        if (RESERVED_TAG_LATEST.equals(tagName)) {
            throw new IllegalArgumentException("Tag name 'latest' is reserved");
        }

        Skill skill = findSkill(namespaceSlug, skillSlug);
        SkillVersion version = versionRepository.findBySkillIdAndVersion(skill.getId(), targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + targetVersion));

        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("Target version must be PUBLISHED");
        }

        SkillTag tag = tagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElse(null);

        if (tag != null) {
            tag.setVersionId(version.getId());
            return tagRepository.save(tag);
        } else {
            SkillTag newTag = new SkillTag(skill.getId(), tagName, version.getId(), operatorId);
            return tagRepository.save(newTag);
        }
    }

    @Transactional
    public void deleteTag(String namespaceSlug, String skillSlug,
                          String tagName, Long operatorId) {
        if (RESERVED_TAG_LATEST.equals(tagName)) {
            throw new IllegalArgumentException("Cannot delete reserved tag 'latest'");
        }

        Skill skill = findSkill(namespaceSlug, skillSlug);
        SkillTag tag = tagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagName));
        tagRepository.delete(tag);
    }

    private Skill findSkill(String namespaceSlug, String skillSlug) {
        Namespace ns = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found"));
        return skillRepository.findByNamespaceIdAndSlug(ns.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -pl skillhub-domain -Dtest=SkillTagServiceTest`

Expected: PASS - All tests pass

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillTagService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillTagServiceTest.java
git commit -m "feat(domain): add SkillTagService for tag CRUD

- listTags: return all custom tags for a skill
- createOrMoveTag: create new or move existing tag to target version
- deleteTag: remove custom tag
- Reject 'latest' as reserved tag name
- Target version must be PUBLISHED
- Add unit tests with Mockito"
```

### Task 19: Skill Controllers — 发布/查询/下载/标签 API

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PublishRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PublishResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillDetailResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillFileResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/TagRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/TagResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/cli/CliPublishController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillPublishController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillTagController.java`

- [ ] **Step 1: 创建 Skill 相关 DTO**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PublishResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

public record PublishResponse(
        Long skillId, String namespace, String slug,
        String version, String status,
        int fileCount, long totalSize
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillDetailResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

public record SkillDetailResponse(
        Long id, String slug, String displayName, String summary,
        String visibility, String status, Long downloadCount,
        Integer starCount, String latestVersion, String namespace
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

public record SkillSummaryResponse(
        Long id, String slug, String displayName, String summary,
        Long downloadCount, String latestVersion, String namespace
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;

public record SkillVersionResponse(
        Long id, String version, String status, String changelog,
        int fileCount, long totalSize, LocalDateTime publishedAt
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillFileResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

public record SkillFileResponse(
        Long id, String filePath, long fileSize,
        String contentType, String sha256
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/TagRequest.java << 'EOF'
package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(
        @NotBlank String tagName,
        @NotBlank String targetVersion
) {}
EOF

cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/TagResponse.java << 'EOF'
package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillTag;

import java.time.LocalDateTime;

public record TagResponse(
        Long id, String tagName, Long versionId, LocalDateTime createdAt
) {
    public static TagResponse from(SkillTag tag) {
        return new TagResponse(tag.getId(), tag.getTagName(),
                tag.getVersionId(), tag.getCreatedAt());
    }
}
EOF
```

- [ ] **Step 2: 创建 CliPublishController**

```bash
mkdir -p server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/cli
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/cli/CliPublishController.java << 'EOF'
package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.PublishResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/v1/cli")
public class CliPublishController {

    private final SkillPublishService publishService;

    public CliPublishController(SkillPublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam("namespace") String namespace,
            @RequestParam(value = "visibility", defaultValue = "PUBLIC") String visibility,
            @AuthenticationPrincipal String userId) throws IOException {

        List<PackageEntry> entries = extractZip(file);
        SkillVisibility vis = SkillVisibility.valueOf(visibility);
        SkillVersion version = publishService.publishFromEntries(namespace, entries, userId, vis);

        PublishResponse response = new PublishResponse(
                version.getSkillId(), namespace, null,
                version.getVersion(), version.getStatus().name(),
                version.getFileCount(), version.getTotalSize());

        return ResponseEntity.ok(Map.of("code", 0, "data", response));
    }

    private List<PackageEntry> extractZip(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) continue;
                byte[] content = zis.readAllBytes();
                entries.add(new PackageEntry(
                        zipEntry.getName(), content, content.length,
                        "application/octet-stream"));
            }
        }
        return entries;
    }
}
EOF
```

- [ ] **Step 3: 创建 SkillPublishController（Web 端发布）**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillPublishController.java << 'EOF'
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.PublishResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillPublishController {

    private final SkillPublishService publishService;

    public SkillPublishController(SkillPublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping("/{namespace}/publish")
    public ResponseEntity<?> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PUBLIC") String visibility,
            @AuthenticationPrincipal String userId) throws IOException {

        List<PackageEntry> entries = extractZip(file);
        SkillVisibility vis = SkillVisibility.valueOf(visibility);
        SkillVersion version = publishService.publishFromEntries(namespace, entries, userId, vis);

        PublishResponse response = new PublishResponse(
                version.getSkillId(), namespace, null,
                version.getVersion(), version.getStatus().name(),
                version.getFileCount(), version.getTotalSize());

        return ResponseEntity.ok(Map.of("code", 0, "data", response));
    }

    private List<PackageEntry> extractZip(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) continue;
                byte[] content = zis.readAllBytes();
                entries.add(new PackageEntry(
                        zipEntry.getName(), content, content.length,
                        "application/octet-stream"));
            }
        }
        return entries;
    }
}
EOF
```

- [ ] **Step 4: 创建 SkillController（公开查询/下载 API）**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillController.java << 'EOF'
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillQueryService queryService;
    private final SkillDownloadService downloadService;

    public SkillController(SkillQueryService queryService,
                           SkillDownloadService downloadService) {
        this.queryService = queryService;
        this.downloadService = downloadService;
    }

    @GetMapping("/{namespace}/{slug}")
    public ResponseEntity<?> getSkillDetail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal String userId) {
        var detail = queryService.getSkillDetail(namespace, slug, userId, Map.of());
        return ResponseEntity.ok(Map.of("code", 0, "data", detail));
    }

    @GetMapping("/{namespace}/{slug}/versions")
    public ResponseEntity<?> listVersions(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SkillVersion> versions = queryService.listVersions(
                namespace, slug, PageRequest.of(page, size));
        List<SkillVersionResponse> items = versions.getContent().stream()
                .map(v -> new SkillVersionResponse(v.getId(), v.getVersion(),
                        v.getStatus().name(), v.getChangelog(),
                        v.getFileCount(), v.getTotalSize(), v.getPublishedAt()))
                .toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", Map.of(
                "items", items, "total", versions.getTotalElements())));
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/files")
    public ResponseEntity<?> listFiles(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version) {
        List<SkillFile> files = queryService.listFiles(namespace, slug, version);
        List<SkillFileResponse> items = files.stream()
                .map(f -> new SkillFileResponse(f.getId(), f.getFilePath(),
                        f.getFileSize(), f.getContentType(), f.getSha256()))
                .toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", items));
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/file")
    public ResponseEntity<?> getFileContent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam String path) {
        InputStream content = queryService.getFileContent(namespace, slug, version, path);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(new InputStreamResource(content));
    }

    @GetMapping("/{namespace}/{slug}/download")
    public ResponseEntity<?> downloadLatest(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal String userId) {
        var result = downloadService.downloadLatest(namespace, slug, userId, Map.of());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.content()));
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/download")
    public ResponseEntity<?> downloadVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @AuthenticationPrincipal String userId) {
        var result = downloadService.downloadVersion(
                namespace, slug, version, userId, Map.of());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(result.content()));
    }
}
EOF
```

- [ ] **Step 5: 创建 SkillTagController**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillTagController.java << 'EOF'
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.skill.SkillTag;
import com.iflytek.skillhub.domain.skill.service.SkillTagService;
import com.iflytek.skillhub.dto.TagRequest;
import com.iflytek.skillhub.dto.TagResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills/{namespace}/{slug}/tags")
public class SkillTagController {

    private final SkillTagService tagService;

    public SkillTagController(SkillTagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ResponseEntity<?> listTags(
            @PathVariable String namespace,
            @PathVariable String slug) {
        List<SkillTag> tags = tagService.listTags(namespace, slug);
        List<TagResponse> items = tags.stream().map(TagResponse::from).toList();
        return ResponseEntity.ok(Map.of("code", 0, "data", items));
    }

    @PutMapping("/{tagName}")
    public ResponseEntity<?> createOrMoveTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal String userId) {
        SkillTag tag = tagService.createOrMoveTag(
                namespace, slug, tagName, request.targetVersion(), userId);
        return ResponseEntity.ok(Map.of("code", 0, "data", TagResponse.from(tag)));
    }

    @DeleteMapping("/{tagName}")
    public ResponseEntity<?> deleteTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @AuthenticationPrincipal String userId) {
        tagService.deleteTag(namespace, slug, tagName, userId);
        return ResponseEntity.ok(Map.of("code", 0, "message", "Tag deleted"));
    }
}
EOF
```

- [ ] **Step 6: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/
git commit -m "feat(api): add Skill controllers for publish/query/download/tag

- CliPublishController: POST /api/v1/cli/publish (multipart zip)
- SkillPublishController: POST /api/v1/skills/{ns}/publish (web)
- SkillController: GET detail/versions/files/download endpoints
- SkillTagController: GET/PUT/DELETE tag management
- Add PublishResponse, SkillDetailResponse, SkillVersionResponse,
  SkillFileResponse, TagRequest, TagResponse DTOs"
```

### Task 20: Search SPI + PostgreSQL Full-Text 实现

**Files:**
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchIndexService.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQueryService.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchRebuildService.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQuery.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchVisibilityScope.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchResult.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SkillSearchDocument.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextIndexService.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresSearchRebuildService.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillSearchDocumentJpaRepository.java`

- [ ] **Step 1: 创建 Search SPI 接口和 record**

```bash
mkdir -p server/skillhub-search/src/main/java/com/iflytek/skillhub/search

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SkillSearchDocument.java << 'EOF'
package com.iflytek.skillhub.search;

public record SkillSearchDocument(
        Long skillId, Long namespaceId, String namespaceSlug,
        Long ownerId, String title, String summary,
        String keywords, String searchText,
        String visibility, String status
) {}
EOF

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQuery.java << 'EOF'
package com.iflytek.skillhub.search;

public record SearchQuery(
        String keyword, Long namespaceId,
        SearchVisibilityScope visibilityScope,
        String sortBy, int page, int size
) {}
EOF

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchVisibilityScope.java << 'EOF'
package com.iflytek.skillhub.search;

import java.util.Set;

public record SearchVisibilityScope(
        String userId,
        Set<Long> memberNamespaceIds,
        Set<Long> adminNamespaceIds
) {
    public static SearchVisibilityScope anonymous() {
        return new SearchVisibilityScope(null, Set.of(), Set.of());
    }
}
EOF

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchResult.java << 'EOF'
package com.iflytek.skillhub.search;

import java.util.List;

public record SearchResult(
        List<Long> skillIds, long total, int page, int size
) {}
EOF

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchIndexService.java << 'EOF'
package com.iflytek.skillhub.search;

import java.util.List;

public interface SearchIndexService {
    void index(SkillSearchDocument doc);
    void batchIndex(List<SkillSearchDocument> docs);
    void remove(Long skillId);
}
EOF

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQueryService.java << 'EOF'
package com.iflytek.skillhub.search;

public interface SearchQueryService {
    SearchResult search(SearchQuery query);
}
EOF

cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchRebuildService.java << 'EOF'
package com.iflytek.skillhub.search;

public interface SearchRebuildService {
    void rebuildAll();
    void rebuildByNamespace(Long namespaceId);
    void rebuildBySkill(Long skillId);
}
EOF
```

- [ ] **Step 2: 创建 SkillSearchDocumentEntity JPA 实体**

```bash
cat > server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillSearchDocumentEntity.java << 'EOF'
package com.iflytek.skillhub.infra.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_search_document")
public class SkillSearchDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false, unique = true)
    private Long skillId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "namespace_slug", nullable = false, length = 64)
    private String namespaceSlug;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(length = 256)
    private String title;

    @Column(length = 512)
    private String summary;

    @Column(length = 512)
    private String keywords;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(nullable = false, length = 32)
    private String visibility;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() { this.updatedAt = LocalDateTime.now(); }

    // Getters and setters
    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
    public Long getNamespaceId() { return namespaceId; }
    public void setNamespaceId(Long namespaceId) { this.namespaceId = namespaceId; }
    public String getNamespaceSlug() { return namespaceSlug; }
    public void setNamespaceSlug(String namespaceSlug) { this.namespaceSlug = namespaceSlug; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
EOF

cat > server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillSearchDocumentJpaRepository.java << 'EOF'
package com.iflytek.skillhub.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillSearchDocumentJpaRepository extends JpaRepository<SkillSearchDocumentEntity, Long> {
    Optional<SkillSearchDocumentEntity> findBySkillId(Long skillId);
    void deleteBySkillId(Long skillId);
}
EOF
```

- [ ] **Step 3: 实现 PostgresFullTextIndexService**

```bash
mkdir -p server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres
cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextIndexService.java << 'EOF'
package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(name = "skillhub.search.provider", havingValue = "postgres", matchIfMissing = true)
public class PostgresFullTextIndexService implements SearchIndexService {

    private final SkillSearchDocumentJpaRepository repository;

    public PostgresFullTextIndexService(SkillSearchDocumentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void index(SkillSearchDocument doc) {
        SkillSearchDocumentEntity entity = repository.findBySkillId(doc.skillId())
                .orElse(new SkillSearchDocumentEntity());
        mapToEntity(doc, entity);
        repository.save(entity);
    }

    @Override
    @Transactional
    public void batchIndex(List<SkillSearchDocument> docs) {
        docs.forEach(this::index);
    }

    @Override
    @Transactional
    public void remove(Long skillId) {
        repository.deleteBySkillId(skillId);
    }

    private void mapToEntity(SkillSearchDocument doc, SkillSearchDocumentEntity entity) {
        entity.setSkillId(doc.skillId());
        entity.setNamespaceId(doc.namespaceId());
        entity.setNamespaceSlug(doc.namespaceSlug());
        entity.setOwnerId(doc.ownerId());
        entity.setTitle(doc.title());
        entity.setSummary(doc.summary());
        entity.setKeywords(doc.keywords());
        entity.setSearchText(doc.searchText());
        entity.setVisibility(doc.visibility());
        entity.setStatus(doc.status());
    }
}
EOF
```

- [ ] **Step 4: 实现 PostgresFullTextQueryService**

```bash
cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java << 'EOF'
package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.search.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "skillhub.search.provider", havingValue = "postgres", matchIfMissing = true)
public class PostgresFullTextQueryService implements SearchQueryService {

    private final EntityManager entityManager;

    public PostgresFullTextQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SearchResult search(SearchQuery query) {
        StringBuilder sql = new StringBuilder();
        StringBuilder countSql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        int paramIdx = 1;

        sql.append("SELECT sd.skill_id FROM skill_search_document sd ");
        sql.append("JOIN skill s ON s.id = sd.skill_id ");
        countSql.append("SELECT COUNT(*) FROM skill_search_document sd ");
        countSql.append("JOIN skill s ON s.id = sd.skill_id ");

        StringBuilder where = new StringBuilder("WHERE sd.status = 'ACTIVE' ");

        // 关键词匹配
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append("AND sd.search_vector @@ plainto_tsquery('simple', ?").append(paramIdx).append(") ");
            params.add(query.keyword());
            paramIdx++;
        }

        // 命名空间过滤
        if (query.namespaceId() != null) {
            where.append("AND sd.namespace_id = ?").append(paramIdx).append(" ");
            params.add(query.namespaceId());
            paramIdx++;
        }

        // 可见性过滤
        SearchVisibilityScope scope = query.visibilityScope();
        if (scope.userId() == null) {
            where.append("AND sd.visibility = 'PUBLIC' ");
        } else {
            where.append("AND (sd.visibility = 'PUBLIC' ");
            if (!scope.memberNamespaceIds().isEmpty()) {
                where.append("OR (sd.visibility = 'NAMESPACE_ONLY' AND sd.namespace_id IN (");
                for (Long nsId : scope.memberNamespaceIds()) {
                    where.append("?").append(paramIdx++).append(",");
                    params.add(nsId);
                }
                where.setLength(where.length() - 1);
                where.append(")) ");
            }
            if (!scope.adminNamespaceIds().isEmpty() || scope.userId() != null) {
                where.append("OR (sd.visibility = 'PRIVATE' AND (");
                where.append("sd.owner_id = ?").append(paramIdx).append(" ");
                params.add(scope.userId());
                paramIdx++;
                if (!scope.adminNamespaceIds().isEmpty()) {
                    where.append("OR sd.namespace_id IN (");
                    for (Long nsId : scope.adminNamespaceIds()) {
                        where.append("?").append(paramIdx++).append(",");
                        params.add(nsId);
                    }
                    where.setLength(where.length() - 1);
                    where.append(")");
                }
                where.append(")) ");
            }
            where.append(") ");
        }

        sql.append(where);
        countSql.append(where);

        // 排序
        String sortBy = query.sortBy() != null ? query.sortBy() : "relevance";
        switch (sortBy) {
            case "downloads" -> sql.append("ORDER BY s.download_count DESC ");
            case "newest" -> sql.append("ORDER BY sd.updated_at DESC ");
            default -> {
                if (query.keyword() != null && !query.keyword().isBlank()) {
                    sql.append("ORDER BY ts_rank_cd(sd.search_vector, plainto_tsquery('simple', ?"))
                            .append(paramIdx).append(")) DESC ");
                    params.add(query.keyword());
                    paramIdx++;
                } else {
                    sql.append("ORDER BY s.download_count DESC ");
                }
            }
        }

        sql.append("LIMIT ").append(query.size()).append(" OFFSET ").append(query.page() * query.size());

        // Execute queries
        Query nativeQuery = entityManager.createNativeQuery(sql.toString());
        Query nativeCountQuery = entityManager.createNativeQuery(countSql.toString());
        for (int i = 0; i < params.size(); i++) {
            nativeQuery.setParameter(i + 1, params.get(i));
            if (i < params.size()) {
                nativeCountQuery.setParameter(i + 1, params.get(i));
            }
        }

        List<Number> ids = nativeQuery.getResultList();
        List<Long> skillIds = ids.stream().map(Number::longValue).toList();
        long total = ((Number) nativeCountQuery.getSingleResult()).longValue();

        return new SearchResult(skillIds, total, query.page(), query.size());
    }
}
EOF
```

- [ ] **Step 5: 实现 PostgresSearchRebuildService**

```bash
cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresSearchRebuildService.java << 'EOF'
package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostgresSearchRebuildService implements SearchRebuildService {

    private static final Logger log = LoggerFactory.getLogger(PostgresSearchRebuildService.class);
    private static final int BATCH_SIZE = 100;

    private final SkillRepository skillRepository;
    private final SkillVersionRepository versionRepository;
    private final NamespaceRepository namespaceRepository;
    private final SearchIndexService indexService;

    public PostgresSearchRebuildService(SkillRepository skillRepository,
                                         SkillVersionRepository versionRepository,
                                         NamespaceRepository namespaceRepository,
                                         SearchIndexService indexService) {
        this.skillRepository = skillRepository;
        this.versionRepository = versionRepository;
        this.namespaceRepository = namespaceRepository;
        this.indexService = indexService;
    }

    @Override
    public void rebuildAll() {
        log.info("Starting full search index rebuild");
        int page = 0;
        Page<Skill> batch;
        do {
            batch = skillRepository.findByNamespaceIdAndStatus(
                    null, SkillStatus.ACTIVE, PageRequest.of(page, BATCH_SIZE));
            List<SkillSearchDocument> docs = batch.getContent().stream()
                    .map(this::buildDocument).toList();
            indexService.batchIndex(docs);
            page++;
        } while (batch.hasNext());
        log.info("Search index rebuild complete");
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        Page<Skill> skills = skillRepository.findByNamespaceIdAndStatus(
                namespaceId, SkillStatus.ACTIVE, PageRequest.of(0, BATCH_SIZE));
        List<SkillSearchDocument> docs = skills.getContent().stream()
                .map(this::buildDocument).toList();
        indexService.batchIndex(docs);
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        skillRepository.findById(skillId).ifPresent(skill -> {
            SkillSearchDocument doc = buildDocument(skill);
            indexService.index(doc);
        });
    }

    private SkillSearchDocument buildDocument(Skill skill) {
        String namespaceSlug = namespaceRepository.findById(skill.getNamespaceId())
                .map(Namespace::getSlug).orElse("");
        String body = "";
        if (skill.getLatestVersionId() != null) {
            body = versionRepository.findById(skill.getLatestVersionId())
                    .map(v -> v.getParsedMetadataJson() != null ? v.getParsedMetadataJson() : "")
                    .orElse("");
        }
        return new SkillSearchDocument(
                skill.getId(), skill.getNamespaceId(), namespaceSlug,
                skill.getOwnerId(), skill.getDisplayName(), skill.getSummary(),
                "", body, skill.getVisibility().name(), skill.getStatus().name());
    }
}
EOF
```

- [ ] **Step 6: 创建 SkillSearchAppService + SearchController**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java << 'EOF'
package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.search.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SkillSearchAppService {

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository memberRepository;

    public SkillSearchAppService(SearchQueryService searchQueryService,
                                  SkillRepository skillRepository,
                                  NamespaceRepository namespaceRepository,
                                  NamespaceMemberRepository memberRepository) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.memberRepository = memberRepository;
    }

    public SearchResultDTO searchSkills(String keyword, String namespaceSlug,
                                         String sortBy, int page, int size,
                                         String currentUserId) {
        Long namespaceId = null;
        if (namespaceSlug != null && !namespaceSlug.isBlank()) {
            namespaceId = namespaceRepository.findBySlug(namespaceSlug)
                    .orElseThrow(() -> new IllegalArgumentException("Namespace not found"))
                    .getId();
        }

        SearchVisibilityScope scope = currentUserId != null
                ? buildScope(currentUserId)
                : SearchVisibilityScope.anonymous();

        SearchQuery query = new SearchQuery(keyword, namespaceId, scope, sortBy, page, size);
        SearchResult result = searchQueryService.search(query);

        List<SkillSummaryDTO> items = result.skillIds().stream()
                .map(id -> skillRepository.findById(id).orElse(null))
                .filter(s -> s != null)
                .map(this::toSummary)
                .toList();

        return new SearchResultDTO(items, result.total(), result.page(), result.size());
    }

    private SearchVisibilityScope buildScope(String userId) {
        // Simplified: in production, load user's namespace memberships
        return new SearchVisibilityScope(userId, Set.of(), Set.of());
    }

    private SkillSummaryDTO toSummary(Skill skill) {
        return new SkillSummaryDTO(
                skill.getId(), skill.getSlug(), skill.getDisplayName(),
                skill.getSummary(), skill.getDownloadCount(),
                skill.getStarCount(), skill.getRatingAvg(), skill.getRatingCount(),
                skill.getNamespaceId());
    }

    public record SkillSummaryDTO(
            Long id, String slug, String displayName, String summary,
            Long downloadCount, Integer starCount,
            java.math.BigDecimal ratingAvg, Integer ratingCount,
            Long namespaceId
    ) {}

    public record SearchResultDTO(
            List<SkillSummaryDTO> items, long total, int page, int size
    ) {}
}
EOF
```

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillSearchController.java << 'EOF'
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.service.SkillSearchAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillSearchController {

    private final SkillSearchAppService searchAppService;

    public SkillSearchController(SkillSearchAppService searchAppService) {
        this.searchAppService = searchAppService;
    }

    @GetMapping
    public ResponseEntity<?> searchSkills(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "namespace", required = false) String namespace,
            @RequestParam(value = "sort", defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String userId) {
        var result = searchAppService.searchSkills(keyword, namespace, sort, page, size, userId);
        return ResponseEntity.ok(Map.of("code", 0, "data", Map.of(
                "items", result.items(),
                "total", result.total(),
                "page", result.page(),
                "size", result.size()
        )));
    }
}
EOF
```

- [ ] **Step 7: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add server/skillhub-search/src/main/java/com/iflytek/skillhub/search/ \
        server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillSearchDocument*.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillSearchController.java
git commit -m "feat(search): add PostgreSQL full-text search implementation

- Add Search SPI: SearchIndexService, SearchQueryService, SearchRebuildService
- Add SearchQuery, SearchResult, SearchVisibilityScope, SkillSearchDocument records
- Implement PostgresFullTextIndexService with upsert by skill_id
- Implement PostgresFullTextQueryService with tsvector/tsquery, visibility filter, sorting
- Implement PostgresSearchRebuildService with batch rebuild
- Add SkillSearchDocumentEntity JPA entity and repository
- Add SkillSearchAppService for search orchestration
- Add SkillSearchController: GET /api/v1/skills?q=&namespace=&sort=&page=&size="
```

### Task 21: 异步事件监听器 + AsyncConfig

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AsyncConfig.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event/SearchIndexEventListener.java`
- Create: `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event/DownloadCountEventListener.java`

- [ ] **Step 1: 创建 AsyncConfig**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AsyncConfig.java << 'EOF'
package com.iflytek.skillhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("skillhubEventExecutor")
    public TaskExecutor skillhubEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(25);
        executor.initialize();
        return executor;
    }
}
EOF
```

- [ ] **Step 2: 创建 SearchIndexEventListener**

```bash
mkdir -p server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event
cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event/SearchIndexEventListener.java << 'EOF'
package com.iflytek.skillhub.search.event;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillStatusChangedEvent;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SearchIndexEventListener {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexEventListener.class);

    private final SearchRebuildService rebuildService;
    private final SearchIndexService indexService;

    public SearchIndexEventListener(SearchRebuildService rebuildService,
                                     SearchIndexService indexService) {
        this.rebuildService = rebuildService;
        this.indexService = indexService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    public void onSkillPublished(SkillPublishedEvent event) {
        log.info("Indexing skill {} after publish", event.skillId());
        rebuildService.rebuildBySkill(event.skillId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    public void onSkillStatusChanged(SkillStatusChangedEvent event) {
        if (event.newStatus() == SkillStatus.ARCHIVED) {
            log.info("Removing skill {} from search index", event.skillId());
            indexService.remove(event.skillId());
        } else {
            log.info("Updating search index for skill {}", event.skillId());
            rebuildService.rebuildBySkill(event.skillId());
        }
    }
}
EOF
```

- [ ] **Step 3: 创建 DownloadCountEventListener**

```bash
cat > server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event/DownloadCountEventListener.java << 'EOF'
package com.iflytek.skillhub.search.event;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DownloadCountEventListener {

    private static final Logger log = LoggerFactory.getLogger(DownloadCountEventListener.class);

    private final SkillRepository skillRepository;

    public DownloadCountEventListener(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @EventListener
    @Async("skillhubEventExecutor")
    public void onSkillDownloaded(SkillDownloadedEvent event) {
        log.debug("Incrementing download count for skill {}", event.skillId());
        skillRepository.incrementDownloadCount(event.skillId());
    }
}
EOF
```

- [ ] **Step 4: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AsyncConfig.java \
        server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event/
git commit -m "feat(event): add async event listeners and thread pool config

- AsyncConfig: skillhubEventExecutor (core=2, max=4, queue=100, CallerRunsPolicy)
- SearchIndexEventListener: index on publish, remove on archive
- DownloadCountEventListener: increment download_count on download
- Use @TransactionalEventListener for search, @EventListener for download count"
```

### Task 22: 应用层精细限流 — Redis 滑动窗口

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/RateLimit.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/SlidingWindowRateLimiter.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/RateLimitInterceptor.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/WebMvcRateLimitConfig.java`
- Create: `server/skillhub-app/src/main/resources/ratelimit.lua`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/ratelimit/RateLimitInterceptorTest.java`

- [ ] **Step 1: 创建 RateLimit 注解**

```bash
mkdir -p server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/RateLimit.java << 'EOF'
package com.iflytek.skillhub.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String category();
    int authenticated() default 60;
    int anonymous() default 20;
    int windowSeconds() default 60;
}
EOF
```

- [ ] **Step 2: 创建 Redis Lua 脚本**

```bash
cat > server/skillhub-app/src/main/resources/ratelimit.lua << 'EOF'
-- Sliding window rate limiter
-- KEYS[1] = rate limit key
-- ARGV[1] = window size in milliseconds
-- ARGV[2] = max requests
-- ARGV[3] = current timestamp in milliseconds

local key = KEYS[1]
local window = tonumber(ARGV[1])
local maxRequests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local windowStart = now - window

-- Remove expired entries
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

-- Count current requests
local count = redis.call('ZCARD', key)

if count < maxRequests then
    -- Add current request
    redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
    redis.call('PEXPIRE', key, window)
    return maxRequests - count - 1
else
    return -1
end
EOF
```

- [ ] **Step 3: 创建 SlidingWindowRateLimiter**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/SlidingWindowRateLimiter.java << 'EOF'
package com.iflytek.skillhub.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("ratelimit.lua"));
        this.rateLimitScript.setResultType(Long.class);
    }

    /**
     * @return remaining requests, or -1 if rate limited
     */
    public long tryAcquire(String key, int maxRequests, int windowSeconds) {
        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(windowSeconds * 1000L),
                    String.valueOf(maxRequests),
                    String.valueOf(System.currentTimeMillis()));
            return result != null ? result : -1;
        } catch (Exception e) {
            log.warn("Rate limiter Redis error, fail-open: {}", e.getMessage());
            return maxRequests; // fail-open
        }
    }
}
EOF
```

- [ ] **Step 4: 创建 RateLimitInterceptor**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/RateLimitInterceptor.java << 'EOF'
package com.iflytek.skillhub.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final SlidingWindowRateLimiter rateLimiter;

    public RateLimitInterceptor(SlidingWindowRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        Principal principal = request.getUserPrincipal();
        boolean authenticated = principal != null;
        int maxRequests = authenticated ? rateLimit.authenticated() : rateLimit.anonymous();

        String identity = authenticated ? principal.getName() : getClientIp(request);
        String key = "ratelimit:" + rateLimit.category() + ":" + identity;

        long remaining = rateLimiter.tryAcquire(key, maxRequests, rateLimit.windowSeconds());

        if (remaining < 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(rateLimit.windowSeconds()));
            response.getWriter().write("{\"code\":429,\"message\":\"Rate limit exceeded\"}");
            return false;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
EOF
```

- [ ] **Step 5: 创建 WebMvcRateLimitConfig**

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/config/WebMvcRateLimitConfig.java << 'EOF'
package com.iflytek.skillhub.config;

import com.iflytek.skillhub.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcRateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcRateLimitConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }
}
EOF
```

- [ ] **Step 6: 在关键 Controller 方法上添加 @RateLimit 注解**

在 `SkillSearchController.searchSkills` 方法上添加：
```java
@RateLimit(category = "search", authenticated = 60, anonymous = 20, windowSeconds = 60)
```

在 `SkillController.downloadLatest` 和 `downloadVersion` 方法上添加：
```java
@RateLimit(category = "download", authenticated = 120, anonymous = 30, windowSeconds = 60)
```

在 `CliPublishController.publish` 和 `SkillPublishController.publish` 方法上添加：
```java
@RateLimit(category = "publish", authenticated = 10, anonymous = 0, windowSeconds = 3600)
```

- [ ] **Step 7: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/ratelimit/ \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/config/WebMvcRateLimitConfig.java \
        server/skillhub-app/src/main/resources/ratelimit.lua
git commit -m "feat(ratelimit): add Redis sliding window rate limiting

- Add @RateLimit annotation with category/authenticated/anonymous/windowSeconds
- Implement SlidingWindowRateLimiter with Redis ZSET + Lua script
- Add RateLimitInterceptor: 429 + Retry-After on exceed, X-RateLimit-Remaining header
- Fail-open on Redis errors (log WARN, allow request)
- Register interceptor for /api/** paths
- Apply limits: search 60/20, download 120/30, publish 10/0 per window"
```

### Task 23: 应用配置更新 + SkillPackageValidator Bean 注册

**Files:**
- Modify: `server/skillhub-app/src/main/resources/application.yml`
- Modify: `server/skillhub-app/src/main/resources/application-local.yml`

- [ ] **Step 1: 更新 application.yml 添加 Phase 2 配置**

在 `application.yml` 中追加以下配置：

```yaml
# Phase 2 配置
skillhub:
  storage:
    provider: local
    local:
      base-path: ./data/storage
  search:
    provider: postgres
  publish:
    max-file-size: 1048576      # 1MB
    max-package-size: 10485760  # 10MB
    max-file-count: 100

spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
```

- [ ] **Step 2: 更新 application-local.yml 添加 S3/MinIO 配置**

在 `application-local.yml` 中追加：

```yaml
skillhub:
  storage:
    provider: local
    local:
      base-path: ./data/storage
    s3:
      endpoint: http://localhost:9000
      bucket: skillhub
      access-key: minioadmin
      secret-key: minioadmin
      region: us-east-1
```

- [ ] **Step 3: 注册 SkillPackageValidator 和 VisibilityChecker 为 Spring Bean**

创建配置类：

```bash
cat > server/skillhub-app/src/main/java/com/iflytek/skillhub/config/DomainBeanConfig.java << 'EOF'
package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainBeanConfig {

    @Bean
    public SkillPackageValidator skillPackageValidator() {
        return new SkillPackageValidator();
    }

    @Bean
    public SkillMetadataParser skillMetadataParser() {
        return new SkillMetadataParser();
    }

    @Bean
    public VisibilityChecker visibilityChecker() {
        return new VisibilityChecker();
    }
}
EOF
```

- [ ] **Step 4: 编译验证**

Run: `cd server && ./mvnw clean compile -DskipTests`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/resources/application.yml \
        server/skillhub-app/src/main/resources/application-local.yml \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/config/DomainBeanConfig.java
git commit -m "feat(config): add Phase 2 application configuration

- Add storage provider config (local default, S3 optional)
- Add search provider config (postgres default)
- Add publish limits config (file size, package size, file count)
- Add multipart upload size limits (20MB)
- Register SkillPackageValidator, SkillMetadataParser, VisibilityChecker beans"
```

### Task 24: 全量编译 + 测试验证

- [ ] **Step 1: 全量编译**

Run: `cd server && ./mvnw clean compile`

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行所有单元测试**

Run: `cd server && ./mvnw test`

Expected: All tests pass

- [ ] **Step 3: 修复编译或测试错误（如有）**

根据错误信息逐一修复，直到全部通过。

- [ ] **Step 4: Final Commit**

```bash
git add -A
git commit -m "feat(phase2-chunk1): complete Phase 2 backend implementation

Phase 2 Chunk 1 backend complete:
- DB migration V2 with skill/version/file/tag/search tables
- Object storage SPI (LocalFile + S3)
- Namespace management (CRUD + member management)
- Skill publish flow (zip → validate → store → persist → event)
- Skill query/download/tag management
- PostgreSQL full-text search with tsvector
- Async events (publish → index, download → count)
- Redis sliding window rate limiting"
```

### Chunk 1 验收标准

运行以下命令验证 Chunk 1 完成：

```bash
# 1. 启动依赖服务
make dev

# 2. 运行所有后端测试
cd server && ./mvnw test
# Expected: BUILD SUCCESS, all tests pass

# 3. 启动后端应用
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local &
sleep 15

# 4. 验证 Phase 2 数据库迁移
docker compose exec postgres psql -U skillhub -d skillhub -c "\dt skill*"
# Expected: 列出 skill, skill_version, skill_file, skill_tag, skill_search_document 五张表

# 5. 验证命名空间 CRUD（创建命名空间）
curl -s -X POST http://localhost:8080/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"slug":"test-ns","displayName":"Test NS","description":"test"}' | jq .
# Expected: {"code":0,"data":{"slug":"test-ns","displayName":"Test NS",...}}

# 6. 验证命名空间查询
curl -s http://localhost:8080/api/v1/namespaces/test-ns | jq .
# Expected: {"code":0,"data":{"slug":"test-ns",...}}

# 7. 验证 CLI 发布接口（创建测试 zip）
mkdir -p /tmp/test-skill && cat > /tmp/test-skill/SKILL.md << 'SKILLEOF'
---
name: hello-world
description: A test skill
version: 1.0.0
---
# Hello World
Test skill body.
SKILLEOF
cd /tmp/test-skill && zip -r /tmp/test-skill.zip . && cd -

curl -s -X POST http://localhost:8080/api/v1/cli/publish \
  -F "file=@/tmp/test-skill.zip" \
  -F "namespace=test-ns" \
  -F "visibility=PUBLIC" | jq .
# Expected: {"code":0,"data":{"slug":"hello-world","version":"1.0.0","status":"PUBLISHED",...}}

# 8. 验证技能详情查询
curl -s http://localhost:8080/api/v1/skills/test-ns/hello-world | jq .
# Expected: {"code":0,"data":{"slug":"hello-world","displayName":"hello-world",...}}

# 9. 验证版本列表
curl -s http://localhost:8080/api/v1/skills/test-ns/hello-world/versions | jq .
# Expected: {"code":0,"data":{"content":[{"version":"1.0.0","status":"PUBLISHED",...}],...}}

# 10. 验证文件清单
curl -s "http://localhost:8080/api/v1/skills/test-ns/hello-world/versions/1.0.0/files" | jq .
# Expected: {"code":0,"data":[{"filePath":"SKILL.md",...}]}

# 11. 验证下载最新版本
curl -s -o /tmp/download-test.zip -w "%{http_code}" \
  http://localhost:8080/api/v1/skills/test-ns/hello-world/download
# Expected: 200, /tmp/download-test.zip 为有效 zip 文件

# 12. 验证标签管理（创建标签）
curl -s -X PUT http://localhost:8080/api/v1/skills/test-ns/hello-world/tags/stable \
  -H "Content-Type: application/json" \
  -d '{"targetVersion":"1.0.0"}' | jq .
# Expected: {"code":0,"data":{"tagName":"stable","version":"1.0.0",...}}

# 13. 验证标签列表
curl -s http://localhost:8080/api/v1/skills/test-ns/hello-world/tags | jq .
# Expected: 包含 "stable" 和虚拟 "latest" 标签

# 14. 验证按标签下载
curl -s -o /tmp/download-tag.zip -w "%{http_code}" \
  http://localhost:8080/api/v1/skills/test-ns/hello-world/tags/stable/download
# Expected: 200

# 15. 验证搜索 API
curl -s "http://localhost:8080/api/v1/skills?q=hello&sort=relevance&page=0&size=20" | jq .
# Expected: {"code":0,"data":{"items":[{"slug":"hello-world",...}],"total":1,...}}

# 16. 验证搜索空关键词（列表模式）
curl -s "http://localhost:8080/api/v1/skills?sort=newest&page=0&size=20" | jq .
# Expected: {"code":0,"data":{"items":[...],"total":...}}

# 17. 验证限流（连续请求触发 429）
for i in $(seq 1 25); do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/skills?q=test")
  echo "Request $i: $code"
done
# Expected: 前 20 次返回 200，之后返回 429（匿名 search 限额 20 次/60s）

# 18. 验证限流 header
curl -s -D - "http://localhost:8080/api/v1/skills?q=test" 2>&1 | grep -i "x-ratelimit\|retry-after"
# Expected: X-RateLimit-Remaining header 存在

# 19. 停止应用并清理
kill %1 2>/dev/null
rm -rf /tmp/test-skill /tmp/test-skill.zip /tmp/download-test.zip /tmp/download-tag.zip
make dev-down
```

Chunk 1 产出：Phase 2 全部后端功能 — 数据库迁移 + 对象存储 + 命名空间管理 + 技能发布/查询/下载 + 标签管理 + PostgreSQL 全文搜索 + 异步事件 + Redis 滑动窗口限流。

---

## Chunk 2: 前端全部

本块实现 Phase 2 全部前端页面：首页、搜索页、命名空间主页、技能详情页、版本历史页、发布页、我的技能、我的命名空间、成员管理。

### 前端文件结构映射

```
web/src/
├── api/
│   └── client.ts                    # 已有，扩展新 API 类型
├── pages/
│   ├── search.tsx                   # 搜索页
│   ├── namespace.tsx                # 命名空间主页
│   ├── skill-detail.tsx             # 技能详情页
│   ├── skill-versions.tsx           # 版本历史页
│   └── dashboard/
│       ├── skills.tsx               # 我的技能
│       ├── publish.tsx              # 发布技能
│       ├── namespaces.tsx           # 我的命名空间
│       └── namespace-members.tsx    # 成员管理
├── features/
│   ├── skill/
│   │   ├── skill-card.tsx
│   │   ├── skill-detail-view.tsx
│   │   ├── skill-version-list.tsx
│   │   ├── file-tree.tsx
│   │   ├── markdown-renderer.tsx
│   │   ├── install-command.tsx
│   │   ├── use-skill-detail.ts
│   │   ├── use-skill-versions.ts
│   │   ├── use-skill-files.ts
│   │   └── use-search-skills.ts
│   ├── publish/
│   │   ├── publish-form.tsx
│   │   ├── upload-zone.tsx
│   │   ├── publish-preview.tsx
│   │   └── use-publish-skill.ts
│   ├── namespace/
│   │   ├── namespace-card.tsx
│   │   ├── namespace-header.tsx
│   │   ├── member-table.tsx
│   │   ├── add-member-dialog.tsx
│   │   ├── create-namespace-dialog.tsx
│   │   ├── use-namespace-detail.ts
│   │   ├── use-namespace-members.ts
│   │   └── use-my-namespaces.ts
│   └── search/
│       ├── search-bar.tsx
│       ├── search-filters.tsx
│       ├── search-results.tsx
│       └── use-search.ts
├── shared/
│   ├── components/
│   │   ├── pagination.tsx
│   │   ├── empty-state.tsx
│   │   ├── skeleton-loader.tsx
│   │   ├── copy-button.tsx
│   │   └── namespace-badge.tsx
│   └── hooks/
│       └── use-debounce.ts
└── routes/                          # TanStack Router 路由配置
```

### Task 25: 前端依赖安装 + API 类型定义

**Files:**
- Modify: `web/package.json`
- Create: `web/src/api/types.ts`
- Create: `web/src/shared/hooks/use-debounce.ts`

- [ ] **Step 1: 安装新依赖**

Run: `cd web && npm install react-markdown rehype-highlight react-dropzone zustand`

- [ ] **Step 2: 创建 API 类型定义**

```bash
cat > web/src/api/types.ts << 'EOF'
// Namespace types
export interface Namespace {
  id: number;
  slug: string;
  displayName: string;
  description: string;
  type: string;
  avatarUrl: string | null;
  status: string;
  createdAt: string;
}

export interface NamespaceMember {
  id: number;
  namespaceId: number;
  userId: string;
  role: string;
  createdAt: string;
}

// Skill types
export interface SkillSummary {
  id: number;
  slug: string;
  displayName: string;
  summary: string;
  downloadCount: number;
  starCount: number;
  ratingAvg: number;
  ratingCount: number;
  latestVersion: string;
  namespace: string;
  updatedAt: string;
}

export interface SkillDetail {
  id: number;
  slug: string;
  displayName: string;
  summary: string;
  visibility: string;
  status: string;
  downloadCount: number;
  starCount: number;
  latestVersion: string;
  namespaceId: number;
}

export interface SkillVersion {
  id: number;
  version: string;
  status: string;
  changelog: string;
  fileCount: number;
  totalSize: number;
  publishedAt: string;
}

export interface SkillFile {
  id: number;
  filePath: string;
  fileSize: number;
  contentType: string;
  sha256: string;
}

export interface SkillTag {
  id: number;
  tagName: string;
  versionId: number;
  createdAt: string;
}

// Search types
export interface SearchParams {
  q?: string;
  namespace?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export interface PagedResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

// Publish types
export interface PublishResult {
  skillId: number;
  namespace: string;
  slug: string;
  version: string;
  status: string;
  fileCount: number;
  totalSize: number;
}
EOF
```

- [ ] **Step 3: 创建 useDebounce hook**

```bash
mkdir -p web/src/shared/hooks
cat > web/src/shared/hooks/use-debounce.ts << 'EOF'
import { useState, useEffect } from 'react';

export function useDebounce<T>(value: T, delay: number = 300): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}
EOF
```

- [ ] **Step 4: Commit**

```bash
cd web && git add package.json package-lock.json src/api/types.ts src/shared/hooks/use-debounce.ts
git commit -m "feat(web): add Phase 2 dependencies and API type definitions

- Add react-markdown, rehype-highlight, react-dropzone, zustand
- Add comprehensive TypeScript types for all API entities
- Add useDebounce hook for search input"
```

### Task 26: 共享组件 — Pagination, EmptyState, SkeletonLoader, CopyButton, NamespaceBadge

**Files:**
- Create: `web/src/shared/components/pagination.tsx`
- Create: `web/src/shared/components/empty-state.tsx`
- Create: `web/src/shared/components/skeleton-loader.tsx`
- Create: `web/src/shared/components/copy-button.tsx`
- Create: `web/src/shared/components/namespace-badge.tsx`

- [ ] **Step 1: 创建 Pagination 组件**

```tsx
// web/src/shared/components/pagination.tsx
import { Button } from '@/shared/ui/button';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  return (
    <div className="flex items-center gap-2 justify-center mt-6">
      <Button variant="outline" size="sm" disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}>上一页</Button>
      <span className="text-sm text-muted-foreground">
        {page + 1} / {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}>下一页</Button>
    </div>
  );
}
```

- [ ] **Step 2: 创建 EmptyState 组件**

```tsx
// web/src/shared/components/empty-state.tsx
interface EmptyStateProps {
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="text-4xl mb-4">📭</div>
      <h3 className="text-lg font-medium text-foreground">{title}</h3>
      {description && <p className="text-sm text-muted-foreground mt-1">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
```

- [ ] **Step 3: 创建 SkeletonLoader 组件**

```tsx
// web/src/shared/components/skeleton-loader.tsx
export function SkeletonCard() {
  return (
    <div className="rounded-lg border p-4 animate-pulse">
      <div className="h-4 bg-muted rounded w-3/4 mb-3" />
      <div className="h-3 bg-muted rounded w-full mb-2" />
      <div className="h-3 bg-muted rounded w-2/3" />
    </div>
  );
}

export function SkeletonList({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {Array.from({ length: count }).map((_, i) => <SkeletonCard key={i} />)}
    </div>
  );
}
```

- [ ] **Step 4: 创建 CopyButton 组件**

```tsx
// web/src/shared/components/copy-button.tsx
import { useState } from 'react';
import { Button } from '@/shared/ui/button';

interface CopyButtonProps {
  text: string;
  label?: string;
}

export function CopyButton({ text, label = '复制' }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Button variant="outline" size="sm" onClick={handleCopy}>
      {copied ? '已复制' : label}
    </Button>
  );
}
```

- [ ] **Step 5: 创建 NamespaceBadge 组件**

```tsx
// web/src/shared/components/namespace-badge.tsx
interface NamespaceBadgeProps {
  slug: string;
  type?: string;
}

export function NamespaceBadge({ slug, type }: NamespaceBadgeProps) {
  const isGlobal = type === 'GLOBAL' || slug === 'global';
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      isGlobal ? 'bg-green-100 text-green-800' : 'bg-blue-100 text-blue-800'
    }`}>
      @{slug}
    </span>
  );
}
```

- [ ] **Step 6: Commit**

```bash
cd web && git add src/shared/components/
git commit -m "feat(web): add shared UI components

- Pagination with prev/next buttons
- EmptyState with icon, title, description, action slot
- SkeletonCard/SkeletonList for loading states
- CopyButton with clipboard API and feedback
- NamespaceBadge with global(green)/team(blue) styling"
```

### Task 27: TanStack Query Hooks — 数据获取层

**Files:**
- Create: `web/src/features/skill/use-skill-detail.ts`
- Create: `web/src/features/skill/use-skill-versions.ts`
- Create: `web/src/features/skill/use-skill-files.ts`
- Create: `web/src/features/skill/use-search-skills.ts`
- Create: `web/src/features/namespace/use-namespace-detail.ts`
- Create: `web/src/features/namespace/use-namespace-members.ts`
- Create: `web/src/features/namespace/use-my-namespaces.ts`
- Create: `web/src/features/publish/use-publish-skill.ts`

- [ ] **Step 1: 创建 Skill 查询 hooks**

```typescript
// web/src/features/skill/use-skill-detail.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SkillDetail } from '@/api/types';

export function useSkillDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug],
    queryFn: () => apiClient.get(`/api/v1/skills/${namespace}/${slug}`)
      .then(r => r.data.data as SkillDetail),
    enabled: !!namespace && !!slug,
  });
}
```

```typescript
// web/src/features/skill/use-skill-versions.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SkillVersion, PagedResponse } from '@/api/types';

export function useSkillVersions(namespace: string, slug: string, page = 0) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', page],
    queryFn: () => apiClient.get(`/api/v1/skills/${namespace}/${slug}/versions`, {
      params: { page, size: 20 }
    }).then(r => r.data.data as PagedResponse<SkillVersion>),
    enabled: !!namespace && !!slug,
  });
}
```

```typescript
// web/src/features/skill/use-skill-files.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SkillFile } from '@/api/types';

export function useSkillFiles(namespace: string, slug: string, version: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'files'],
    queryFn: () => apiClient.get(
      `/api/v1/skills/${namespace}/${slug}/versions/${version}/files`
    ).then(r => r.data.data as SkillFile[]),
    enabled: !!namespace && !!slug && !!version,
  });
}
```

```typescript
// web/src/features/skill/use-search-skills.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SearchParams, SkillSummary, PagedResponse } from '@/api/types';

export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', 'search', params],
    queryFn: () => apiClient.get('/api/v1/skills', { params })
      .then(r => r.data.data as PagedResponse<SkillSummary>),
    placeholderData: (prev) => prev,
  });
}
```

- [ ] **Step 2: 创建 Namespace 查询 hooks**

```typescript
// web/src/features/namespace/use-namespace-detail.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { Namespace } from '@/api/types';

export function useNamespaceDetail(slug: string) {
  return useQuery({
    queryKey: ['namespaces', slug],
    queryFn: () => apiClient.get(`/api/v1/namespaces/${slug}`)
      .then(r => r.data.data as Namespace),
    enabled: !!slug,
  });
}
```

```typescript
// web/src/features/namespace/use-namespace-members.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { NamespaceMember, PagedResponse } from '@/api/types';

export function useNamespaceMembers(slug: string, page = 0) {
  return useQuery({
    queryKey: ['namespaces', slug, 'members', page],
    queryFn: () => apiClient.get(`/api/v1/namespaces/${slug}/members`, {
      params: { page, size: 20 }
    }).then(r => r.data.data as PagedResponse<NamespaceMember>),
    enabled: !!slug,
  });
}
```

```typescript
// web/src/features/namespace/use-my-namespaces.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { Namespace } from '@/api/types';

export function useMyNamespaces() {
  return useQuery({
    queryKey: ['me', 'namespaces'],
    queryFn: () => apiClient.get('/api/v1/namespaces')
      .then(r => r.data.data.items as Namespace[]),
  });
}
```

- [ ] **Step 3: 创建 Publish mutation hook**

```typescript
// web/src/features/publish/use-publish-skill.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { PublishResult } from '@/api/types';

export function usePublishSkill() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file, namespace, visibility }: {
      file: File; namespace: string; visibility: string;
    }) => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('visibility', visibility);
      const res = await apiClient.post(
        `/api/v1/skills/${namespace}/publish`, formData,
        { headers: { 'Content-Type': 'multipart/form-data' } });
      return res.data.data as PublishResult;
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['me', 'skills'] });
      queryClient.invalidateQueries({ queryKey: ['namespaces', variables.namespace, 'skills'] });
    },
  });
}
```

- [ ] **Step 4: Commit**

```bash
cd web && git add src/features/
git commit -m "feat(web): add TanStack Query hooks for all API endpoints

- useSkillDetail, useSkillVersions, useSkillFiles, useSearchSkills
- useNamespaceDetail, useNamespaceMembers, useMyNamespaces
- usePublishSkill mutation with cache invalidation
- placeholderData for search to avoid flicker"
```

### Task 28: Feature 组件 — SkillCard, MarkdownRenderer, FileTree, SearchBar, InstallCommand

**Files:**
- Create: `web/src/features/skill/skill-card.tsx`
- Create: `web/src/features/skill/markdown-renderer.tsx`
- Create: `web/src/features/skill/file-tree.tsx`
- Create: `web/src/features/skill/install-command.tsx`
- Create: `web/src/features/search/search-bar.tsx`
- Create: `web/src/features/search/search-filters.tsx`
- Create: `web/src/features/publish/upload-zone.tsx`

- [ ] **Step 1: 创建 SkillCard**

```tsx
// web/src/features/skill/skill-card.tsx
import { Link } from '@tanstack/react-router';
import { NamespaceBadge } from '@/shared/components/namespace-badge';
import type { SkillSummary } from '@/api/types';

export function SkillCard({ skill }: { skill: SkillSummary }) {
  return (
    <Link to={`/@${skill.namespace}/${skill.slug}`}
      className="block rounded-lg border p-4 hover:shadow-md transition-shadow">
      <div className="flex items-center gap-2 mb-2">
        <NamespaceBadge slug={skill.namespace} />
        <span className="text-sm font-medium truncate">{skill.displayName || skill.slug}</span>
      </div>
      <p className="text-sm text-muted-foreground line-clamp-2 mb-3">{skill.summary}</p>
      <div className="flex items-center gap-4 text-xs text-muted-foreground">
        <span>v{skill.latestVersion}</span>
        <span>↓ {skill.downloadCount}</span>
        {skill.ratingAvg > 0 && <span>★ {skill.ratingAvg.toFixed(1)}</span>}
      </div>
    </Link>
  );
}
```

- [ ] **Step 2: 创建 MarkdownRenderer**

```tsx
// web/src/features/skill/markdown-renderer.tsx
import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';

export function MarkdownRenderer({ content }: { content: string }) {
  return (
    <div className="prose prose-sm max-w-none dark:prose-invert">
      <ReactMarkdown rehypePlugins={[rehypeHighlight]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
```

- [ ] **Step 3: 创建 FileTree**

```tsx
// web/src/features/skill/file-tree.tsx
import type { SkillFile } from '@/api/types';

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

export function FileTree({ files, onSelect }: {
  files: SkillFile[];
  onSelect?: (file: SkillFile) => void;
}) {
  return (
    <div className="border rounded-lg divide-y">
      {files.map(file => (
        <button key={file.id}
          className="w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-muted/50 text-left"
          onClick={() => onSelect?.(file)}>
          <span className="font-mono truncate">{file.filePath}</span>
          <span className="text-muted-foreground ml-2 shrink-0">{formatSize(file.fileSize)}</span>
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: 创建 InstallCommand**

```tsx
// web/src/features/skill/install-command.tsx
import { CopyButton } from '@/shared/components/copy-button';

export function InstallCommand({ namespace, slug }: { namespace: string; slug: string }) {
  const command = `clawhub install @${namespace}/${slug}`;
  return (
    <div className="flex items-center gap-2 bg-muted rounded-lg px-3 py-2">
      <code className="text-sm flex-1 font-mono truncate">{command}</code>
      <CopyButton text={command} />
    </div>
  );
}
```

- [ ] **Step 5: 创建 SearchBar**

```tsx
// web/src/features/search/search-bar.tsx
import { useState } from 'react';
import { useNavigate } from '@tanstack/react-router';

export function SearchBar({ defaultValue = '', className = '' }: {
  defaultValue?: string; className?: string;
}) {
  const [query, setQuery] = useState(defaultValue);
  const navigate = useNavigate();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate({ to: '/search', search: { q: query } });
  };

  return (
    <form onSubmit={handleSubmit} className={className}>
      <input type="text" value={query} onChange={e => setQuery(e.target.value)}
        placeholder="搜索技能..."
        className="w-full rounded-lg border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary" />
    </form>
  );
}
```

- [ ] **Step 6: 创建 UploadZone**

```tsx
// web/src/features/publish/upload-zone.tsx
import { useCallback } from 'react';
import { useDropzone } from 'react-dropzone';

export function UploadZone({ onFileSelect }: { onFileSelect: (file: File) => void }) {
  const onDrop = useCallback((files: File[]) => {
    if (files.length > 0) onFileSelect(files[0]);
  }, [onFileSelect]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop, accept: { 'application/zip': ['.zip'] }, maxFiles: 1,
  });

  return (
    <div {...getRootProps()} className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
      isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/25 hover:border-primary/50'
    }`}>
      <input {...getInputProps()} />
      <div className="text-3xl mb-2">📦</div>
      <p className="text-sm text-muted-foreground">
        {isDragActive ? '释放文件...' : '拖拽 .zip 文件到此处，或点击选择'}
      </p>
    </div>
  );
}
```

- [ ] **Step 7: Commit**

```bash
cd web && git add src/features/
git commit -m "feat(web): add feature components

- SkillCard with namespace badge, stats, hover shadow
- MarkdownRenderer with react-markdown + rehype-highlight
- FileTree with file size formatting and selection
- InstallCommand with copy button
- SearchBar with form submit navigation
- UploadZone with react-dropzone drag-and-drop"
```

### Task 29: 首页 + 搜索页

**Files:**
- Modify: `web/src/pages/home.tsx` (或已有首页文件)
- Create: `web/src/pages/search.tsx`

- [ ] **Step 1: 实现首页**

首页包含：Hero 区域（标题 + 搜索框）、三栏卡片区（精选/热门/最新各 top 6）。

```tsx
// web/src/pages/home.tsx
import { SearchBar } from '@/features/search/search-bar';
import { SkillCard } from '@/features/skill/skill-card';
import { SkeletonList } from '@/shared/components/skeleton-loader';
import { useSearchSkills } from '@/features/skill/use-search-skills';

function SkillSection({ title, sort }: { title: string; sort: string }) {
  const { data, isLoading } = useSearchSkills({ sort, size: 6 });
  return (
    <section className="mb-12">
      <h2 className="text-xl font-semibold mb-4">{title}</h2>
      {isLoading ? <SkeletonList count={6} /> : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {data?.items.map(skill => <SkillCard key={skill.id} skill={skill} />)}
        </div>
      )}
    </section>
  );
}

export default function HomePage() {
  return (
    <div className="max-w-6xl mx-auto px-4">
      <section className="py-16 text-center">
        <h1 className="text-4xl font-bold mb-3">SkillHub</h1>
        <p className="text-lg text-muted-foreground mb-8">发现、分享和管理 AI 技能</p>
        <SearchBar className="max-w-xl mx-auto" />
      </section>
      <SkillSection title="热门下载" sort="downloads" />
      <SkillSection title="最新发布" sort="newest" />
    </div>
  );
}
```

- [ ] **Step 2: 实现搜索页**

```tsx
// web/src/pages/search.tsx
import { useState } from 'react';
import { useSearch } from '@tanstack/react-router';
import { SearchBar } from '@/features/search/search-bar';
import { SkillCard } from '@/features/skill/skill-card';
import { Pagination } from '@/shared/components/pagination';
import { EmptyState } from '@/shared/components/empty-state';
import { SkeletonList } from '@/shared/components/skeleton-loader';
import { useSearchSkills } from '@/features/skill/use-search-skills';

export default function SearchPage() {
  const search = useSearch({ from: '/search' });
  const [page, setPage] = useState(search.page ?? 0);
  const sort = search.sort ?? 'relevance';

  const { data, isLoading } = useSearchSkills({
    q: search.q, namespace: search.namespace, sort, page, size: 20,
  });

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <SearchBar defaultValue={search.q} className="mb-6" />
      <div className="flex items-center gap-4 mb-6">
        <select value={sort} onChange={e => {/* update URL */}}
          className="rounded border px-3 py-1.5 text-sm">
          <option value="relevance">相关度</option>
          <option value="downloads">下载量</option>
          <option value="newest">最新</option>
        </select>
      </div>
      {isLoading ? <SkeletonList /> : data?.items.length === 0 ? (
        <EmptyState title="未找到结果" description="尝试其他关键词" />
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {data?.items.map(skill => <SkillCard key={skill.id} skill={skill} />)}
          </div>
          {data && <Pagination page={page} totalPages={Math.ceil(data.total / 20)}
            onPageChange={setPage} />}
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
cd web && git add src/pages/home.tsx src/pages/search.tsx
git commit -m "feat(web): add home page and search page

- Home: hero section with search bar, popular/newest skill grids
- Search: keyword search, sort selector, paginated results grid
- Loading skeletons and empty state handling"
```

### Task 30: 命名空间主页 + 技能详情页

**Files:**
- Create: `web/src/pages/namespace.tsx`
- Create: `web/src/pages/skill-detail.tsx`
- Create: `web/src/features/skill/skill-detail-view.tsx`
- Create: `web/src/features/namespace/namespace-header.tsx`

- [ ] **Step 1: 创建 NamespaceHeader 组件**

```tsx
// web/src/features/namespace/namespace-header.tsx
import { NamespaceBadge } from '@/shared/components/namespace-badge';
import type { Namespace } from '@/api/types';

export function NamespaceHeader({ namespace }: { namespace: Namespace }) {
  return (
    <div className="flex items-center gap-4 mb-8">
      {namespace.avatarUrl ? (
        <img src={namespace.avatarUrl} alt="" className="w-16 h-16 rounded-full" />
      ) : (
        <div className="w-16 h-16 rounded-full bg-muted flex items-center justify-center text-2xl">
          {namespace.displayName[0]}
        </div>
      )}
      <div>
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold">{namespace.displayName}</h1>
          <NamespaceBadge slug={namespace.slug} type={namespace.type} />
        </div>
        {namespace.description && (
          <p className="text-muted-foreground mt-1">{namespace.description}</p>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 实现命名空间主页**

```tsx
// web/src/pages/namespace.tsx
import { useParams } from '@tanstack/react-router';
import { useNamespaceDetail } from '@/features/namespace/use-namespace-detail';
import { useSearchSkills } from '@/features/skill/use-search-skills';
import { NamespaceHeader } from '@/features/namespace/namespace-header';
import { SkillCard } from '@/features/skill/skill-card';
import { SkeletonList } from '@/shared/components/skeleton-loader';
import { EmptyState } from '@/shared/components/empty-state';

export default function NamespacePage() {
  const { namespace } = useParams({ from: '/@$namespace' });
  const { data: ns, isLoading: nsLoading } = useNamespaceDetail(namespace);
  const { data: skills, isLoading: skillsLoading } = useSearchSkills({
    namespace, sort: 'downloads', size: 50,
  });

  if (nsLoading) return <SkeletonList />;
  if (!ns) return <EmptyState title="命名空间不存在" />;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <NamespaceHeader namespace={ns} />
      {skillsLoading ? <SkeletonList /> : skills?.items.length === 0 ? (
        <EmptyState title="暂无技能" description="该命名空间下还没有发布技能" />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {skills?.items.map(s => <SkillCard key={s.id} skill={s} />)}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: 创建 SkillDetailView 组件**

```tsx
// web/src/features/skill/skill-detail-view.tsx
import { useState } from 'react';
import { MarkdownRenderer } from './markdown-renderer';
import { FileTree } from './file-tree';
import { InstallCommand } from './install-command';
import { NamespaceBadge } from '@/shared/components/namespace-badge';
import type { SkillDetail, SkillFile } from '@/api/types';

type Tab = 'readme' | 'files' | 'versions';

export function SkillDetailView({ skill, namespace, files, readmeContent }: {
  skill: SkillDetail; namespace: string;
  files: SkillFile[]; readmeContent: string;
}) {
  const [tab, setTab] = useState<Tab>('readme');

  return (
    <div className="flex gap-8">
      <div className="flex-1 min-w-0">
        <div className="flex gap-4 border-b mb-4">
          {(['readme', 'files', 'versions'] as Tab[]).map(t => (
            <button key={t} onClick={() => setTab(t)}
              className={`pb-2 text-sm font-medium border-b-2 ${
                tab === t ? 'border-primary text-primary' : 'border-transparent text-muted-foreground'
              }`}>
              {t === 'readme' ? 'README' : t === 'files' ? '文件' : '版本'}
            </button>
          ))}
        </div>
        {tab === 'readme' && <MarkdownRenderer content={readmeContent} />}
        {tab === 'files' && <FileTree files={files} />}
      </div>
      <aside className="w-72 shrink-0">
        <div className="space-y-4">
          <div>
            <div className="text-sm text-muted-foreground mb-1">版本</div>
            <div className="font-medium">v{skill.latestVersion}</div>
          </div>
          <div>
            <div className="text-sm text-muted-foreground mb-1">下载量</div>
            <div className="font-medium">{skill.downloadCount}</div>
          </div>
          <div>
            <div className="text-sm text-muted-foreground mb-1">命名空间</div>
            <NamespaceBadge slug={namespace} />
          </div>
          <InstallCommand namespace={namespace} slug={skill.slug} />
          <a href={`/api/v1/skills/${namespace}/${skill.slug}/download`}
            className="block w-full text-center rounded-lg bg-primary text-primary-foreground py-2 text-sm font-medium hover:bg-primary/90">
            下载
          </a>
        </div>
      </aside>
    </div>
  );
}
```

- [ ] **Step 4: 实现技能详情页**

```tsx
// web/src/pages/skill-detail.tsx
import { useParams } from '@tanstack/react-router';
import { useSkillDetail } from '@/features/skill/use-skill-detail';
import { useSkillFiles } from '@/features/skill/use-skill-files';
import { SkillDetailView } from '@/features/skill/skill-detail-view';
import { SkeletonList } from '@/shared/components/skeleton-loader';

export default function SkillDetailPage() {
  const { namespace, slug } = useParams({ from: '/@$namespace/$slug' });
  const { data: skill, isLoading } = useSkillDetail(namespace, slug);
  const { data: files } = useSkillFiles(namespace, slug, skill?.latestVersion ?? '');

  if (isLoading) return <div className="max-w-6xl mx-auto px-4 py-8"><SkeletonList count={3} /></div>;
  if (!skill) return null;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">{skill.displayName || skill.slug}</h1>
      <SkillDetailView skill={skill} namespace={namespace}
        files={files ?? []} readmeContent={skill.summary ?? ''} />
    </div>
  );
}
```

- [ ] **Step 5: Commit**

```bash
cd web && git add src/pages/namespace.tsx src/pages/skill-detail.tsx \
  src/features/skill/skill-detail-view.tsx src/features/namespace/namespace-header.tsx
git commit -m "feat(web): add namespace page and skill detail page

- Namespace page: header with avatar/badge, skill grid
- Skill detail: tabbed view (README/files/versions), sidebar with stats/install/download
- NamespaceHeader and SkillDetailView components"
```

### Task 31: Dashboard 页面 — 发布/我的技能/命名空间管理

**Files:**
- Create: `web/src/pages/dashboard/publish.tsx`
- Create: `web/src/pages/dashboard/skills.tsx`
- Create: `web/src/pages/dashboard/namespaces.tsx`
- Create: `web/src/pages/dashboard/namespace-members.tsx`
- Create: `web/src/features/publish/publish-form.tsx`
- Create: `web/src/features/namespace/create-namespace-dialog.tsx`
- Create: `web/src/features/namespace/member-table.tsx`

- [ ] **Step 1: 创建 PublishForm 组件**

```tsx
// web/src/features/publish/publish-form.tsx
import { useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { UploadZone } from './upload-zone';
import { usePublishSkill } from './use-publish-skill';
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces';
import { Button } from '@/shared/ui/button';

export function PublishForm() {
  const [file, setFile] = useState<File | null>(null);
  const [namespace, setNamespace] = useState('');
  const [visibility, setVisibility] = useState('PUBLIC');
  const { data: namespaces } = useMyNamespaces();
  const publish = usePublishSkill();
  const navigate = useNavigate();

  const handlePublish = () => {
    if (!file || !namespace) return;
    publish.mutate({ file, namespace, visibility }, {
      onSuccess: (result) => {
        navigate({ to: `/@${result.namespace}/${result.slug}` });
      },
    });
  };

  return (
    <div className="space-y-6">
      <div>
        <label className="block text-sm font-medium mb-2">命名空间</label>
        <select value={namespace} onChange={e => setNamespace(e.target.value)}
          className="w-full rounded border px-3 py-2 text-sm">
          <option value="">选择命名空间</option>
          {namespaces?.map(ns => (
            <option key={ns.id} value={ns.slug}>{ns.displayName} (@{ns.slug})</option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-sm font-medium mb-2">上传技能包</label>
        <UploadZone onFileSelect={setFile} />
        {file && <p className="text-sm text-muted-foreground mt-2">已选择: {file.name}</p>}
      </div>
      <div>
        <label className="block text-sm font-medium mb-2">可见性</label>
        <select value={visibility} onChange={e => setVisibility(e.target.value)}
          className="w-full rounded border px-3 py-2 text-sm">
          <option value="PUBLIC">公开</option>
          <option value="NAMESPACE_ONLY">命名空间内可见</option>
          <option value="PRIVATE">私有</option>
        </select>
      </div>
      <Button onClick={handlePublish} disabled={!file || !namespace || publish.isPending}
        className="w-full">
        {publish.isPending ? '发布中...' : '确认发布'}
      </Button>
    </div>
  );
}
```

- [ ] **Step 2: 实现发布页**

```tsx
// web/src/pages/dashboard/publish.tsx
import { PublishForm } from '@/features/publish/publish-form';

export default function PublishPage() {
  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">发布技能</h1>
      <PublishForm />
    </div>
  );
}
```

- [ ] **Step 3: 实现我的技能页**

```tsx
// web/src/pages/dashboard/skills.tsx
import { Link } from '@tanstack/react-router';
import { useSearchSkills } from '@/features/skill/use-search-skills';
import { EmptyState } from '@/shared/components/empty-state';
import { Button } from '@/shared/ui/button';

export default function MySkillsPage() {
  const { data, isLoading } = useSearchSkills({ sort: 'newest', size: 50 });

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">我的技能</h1>
        <Link to="/dashboard/publish"><Button>发布技能</Button></Link>
      </div>
      {isLoading ? <p>加载中...</p> : data?.items.length === 0 ? (
        <EmptyState title="还没有技能" description="发布你的第一个技能吧"
          action={<Link to="/dashboard/publish"><Button>去发布</Button></Link>} />
      ) : (
        <div className="border rounded-lg divide-y">
          {data?.items.map(skill => (
            <Link key={skill.id} to={`/@${skill.namespace}/${skill.slug}`}
              className="flex items-center justify-between px-4 py-3 hover:bg-muted/50">
              <div>
                <div className="font-medium">{skill.displayName || skill.slug}</div>
                <div className="text-sm text-muted-foreground">@{skill.namespace}</div>
              </div>
              <div className="text-sm text-muted-foreground">
                v{skill.latestVersion} · ↓{skill.downloadCount}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: 实现我的命名空间页**

```tsx
// web/src/pages/dashboard/namespaces.tsx
import { Link } from '@tanstack/react-router';
import { useMyNamespaces } from '@/features/namespace/use-my-namespaces';
import { NamespaceBadge } from '@/shared/components/namespace-badge';
import { EmptyState } from '@/shared/components/empty-state';
import { Button } from '@/shared/ui/button';

export default function MyNamespacesPage() {
  const { data: namespaces, isLoading } = useMyNamespaces();

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">我的命名空间</h1>
        <Button>创建命名空间</Button>
      </div>
      {isLoading ? <p>加载中...</p> : namespaces?.length === 0 ? (
        <EmptyState title="还没有命名空间" description="创建一个团队命名空间" />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {namespaces?.map(ns => (
            <Link key={ns.id} to={`/dashboard/namespaces/${ns.slug}/members`}
              className="block rounded-lg border p-4 hover:shadow-md transition-shadow">
              <div className="flex items-center gap-2 mb-2">
                <NamespaceBadge slug={ns.slug} type={ns.type} />
                <span className="font-medium">{ns.displayName}</span>
              </div>
              <p className="text-sm text-muted-foreground">{ns.description}</p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 5: 创建 MemberTable 组件**

```tsx
// web/src/features/namespace/member-table.tsx
import type { NamespaceMember } from '@/api/types';
import { Button } from '@/shared/ui/button';

export function MemberTable({ members, onRemove, onRoleChange }: {
  members: NamespaceMember[];
  onRemove?: (userId: string) => void;
  onRoleChange?: (userId: string, role: string) => void;
}) {
  return (
    <div className="border rounded-lg">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th className="text-left px-4 py-2">用户 ID</th>
            <th className="text-left px-4 py-2">角色</th>
            <th className="text-left px-4 py-2">加入时间</th>
            <th className="text-right px-4 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          {members.map(m => (
            <tr key={m.id} className="border-b last:border-0">
              <td className="px-4 py-2">{m.userId}</td>
              <td className="px-4 py-2">
                <select value={m.role} onChange={e => onRoleChange?.(m.userId, e.target.value)}
                  className="rounded border px-2 py-1 text-xs" disabled={m.role === 'OWNER'}>
                  <option value="MEMBER">MEMBER</option>
                  <option value="ADMIN">ADMIN</option>
                  <option value="OWNER">OWNER</option>
                </select>
              </td>
              <td className="px-4 py-2 text-muted-foreground">
                {new Date(m.createdAt).toLocaleDateString()}
              </td>
              <td className="px-4 py-2 text-right">
                {m.role !== 'OWNER' && (
                  <Button variant="ghost" size="sm" onClick={() => onRemove?.(m.userId)}>
                    移除
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 6: 实现成员管理页**

```tsx
// web/src/pages/dashboard/namespace-members.tsx
import { useParams } from '@tanstack/react-router';
import { useNamespaceDetail } from '@/features/namespace/use-namespace-detail';
import { useNamespaceMembers } from '@/features/namespace/use-namespace-members';
import { MemberTable } from '@/features/namespace/member-table';
import { NamespaceHeader } from '@/features/namespace/namespace-header';
import { Button } from '@/shared/ui/button';

export default function NamespaceMembersPage() {
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/members' });
  const { data: ns } = useNamespaceDetail(slug);
  const { data: members } = useNamespaceMembers(slug);

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {ns && <NamespaceHeader namespace={ns} />}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold">成员管理</h2>
        <Button size="sm">添加成员</Button>
      </div>
      {members && <MemberTable members={members.items} />}
    </div>
  );
}
```

- [ ] **Step 7: Commit**

```bash
cd web && git add src/pages/dashboard/ src/features/publish/publish-form.tsx \
  src/features/namespace/member-table.tsx src/features/namespace/create-namespace-dialog.tsx
git commit -m "feat(web): add dashboard pages

- Publish page: namespace selector, upload zone, visibility, confirm
- My Skills page: skill list with stats, empty state with CTA
- My Namespaces page: namespace cards with badge
- Namespace Members page: member table with role change and remove
- PublishForm, MemberTable components"
```

### Task 32: TanStack Router 路由配置

**Files:**
- Modify: `web/src/routes/` (路由配置文件)

- [ ] **Step 1: 配置 Phase 2 路由**

根据项目已有的 TanStack Router 配置模式，添加以下路由：

```
/                                      → HomePage
/search                                → SearchPage (search params: q, namespace, sort, page)
/@{namespace}                          → NamespacePage
/@{namespace}/{slug}                   → SkillDetailPage
/@{namespace}/{slug}/versions          → SkillVersionsPage
/dashboard/skills                      → MySkillsPage (需登录)
/dashboard/publish                     → PublishPage (需登录)
/dashboard/namespaces                  → MyNamespacesPage (需登录)
/dashboard/namespaces/{slug}/members   → NamespaceMembersPage (需登录)
```

门户区（`/`, `/search`, `/@*`）匿名可访问。
Dashboard 区需登录（复用 Phase 1 路由守卫）。

- [ ] **Step 2: 验证路由可访问**

Run: `cd web && npm run build`

Expected: BUILD SUCCESS，无路由错误

- [ ] **Step 3: Commit**

```bash
cd web && git add src/routes/
git commit -m "feat(web): configure Phase 2 routes

- Public routes: /, /search, /@namespace, /@namespace/slug
- Dashboard routes: /dashboard/skills, /dashboard/publish, /dashboard/namespaces
- Dashboard routes require authentication (Phase 1 guard)"
```

### Task 33: 前端全量构建 + 验证

- [ ] **Step 1: 全量构建**

Run: `cd web && npm run build`

Expected: BUILD SUCCESS

- [ ] **Step 2: 类型检查**

Run: `cd web && npx tsc --noEmit`

Expected: 无类型错误

- [ ] **Step 3: 修复构建或类型错误（如有）**

根据错误信息逐一修复。

- [ ] **Step 4: Final Commit**

```bash
cd web && git add -A
git commit -m "feat(phase2-chunk2): complete Phase 2 frontend implementation

Phase 2 Chunk 2 frontend complete:
- Home page with hero search and skill grids
- Search page with keyword/sort/pagination
- Namespace page with header and skill list
- Skill detail page with README/files/versions tabs
- Publish page with drag-drop upload
- Dashboard: my skills, my namespaces, member management
- Shared components: Pagination, EmptyState, SkeletonLoader, CopyButton, NamespaceBadge
- TanStack Query hooks for all API endpoints
- Feature components: SkillCard, MarkdownRenderer, FileTree, InstallCommand, SearchBar"
```

### Chunk 2 验收标准

1. ✅ 首页展示热门/最新技能，搜索框可跳转搜索页
2. ✅ 搜索页：关键词搜索 + 排序 + 分页，URL 驱动
3. ✅ 命名空间主页：展示空间信息 + 技能列表
4. ✅ 技能详情页：Markdown 渲染 + 文件树 + 安装命令 + 下载
5. ✅ 发布页：拖拽上传 + 选择命名空间/可见性 + 确认发布
6. ✅ 我的技能：列表展示 + 跳转详情
7. ✅ 命名空间管理：创建 + 成员管理（添加/移除/角色变更）
8. ✅ 匿名用户可浏览/搜索/下载 PUBLIC 技能
9. ✅ 前端构建通过，无类型错误
