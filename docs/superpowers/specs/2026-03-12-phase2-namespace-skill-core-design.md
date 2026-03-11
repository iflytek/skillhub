# Phase 2: 命名空间 + Skill 核心链路 设计文档

> **Goal:** 在 Phase 1 工程骨架和认证体系基础上，实现命名空间管理、对象存储、技能发布/查询/下载完整链路、标签管理、PostgreSQL 全文搜索、异步事件基础设施和应用层精细限流。

> **前置条件:** Phase 1 全部 3 个 Chunk 完成（后端骨架 + 认证授权 + 前端骨架）

## 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 发布流程 | 直接到 PUBLISHED，跳过审核 | Phase 3 再加审核拦截，Phase 2 先跑通完整链路 |
| 对象存储 | LocalFile + S3 双实现，Profile 可配置 | 本地开发零依赖，集成测试/生产用 MinIO/S3 |
| 架构模式 | 领域服务集中式（方案 A） | 与 Phase 1 一致，domain 模块包含领域服务 + 应用服务 |
| Chunk 策略 | 后端先行（Chunk 1 后端，Chunk 2 前端） | API 稳定后再做前端，减少联调返工 |
| 前端风格 | 现代产品风（Vercel/Linear 风格） | 使用 frontend-design 技能优化设计质量 |
| CLI publish 接口 | Phase 2 去掉 `auto_submit` 参数，直接返回 PUBLISHED | 与 `05-business-flows.md` 有意偏差，Phase 3 回补 `auto_submit` 并调整默认 status |
| Web publish 接口 | `POST /api/v1/skills/{namespace}/publish` | 与 CLI 分开路径但共用 service，`06-api-design.md` 未定义此路径，Phase 2 新增 |

## Tech Stack（沿用 Phase 1 + 新增）

- 沿用: Spring Boot 3.x + JDK 21 + PostgreSQL 16 + Redis 7 + Spring Security + Spring Data JPA + Flyway
- 新增后端: AWS SDK for Java v2 (S3 Client) + SnakeYAML (frontmatter 解析)
- 沿用前端: React 19 + TypeScript + Vite + TanStack Router + TanStack Query + shadcn/ui + Tailwind CSS
- 新增前端: react-markdown + rehype-highlight (Markdown 渲染) + react-dropzone (文件上传)

---

## 1. 数据库迁移（V2__phase2_skill_tables.sql）

Phase 1 已有表：`user_account`, `identity_binding`, `api_token`, `role`, `permission`, `role_permission`, `user_role_binding`, `namespace`, `namespace_member`, `audit_log`

### 1.1 新增表

#### skill

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| namespace_id | BIGINT NOT NULL FK → namespace | 所属命名空间 |
| slug | VARCHAR(128) NOT NULL | URL 友好标识，来自 SKILL.md name |
| display_name | VARCHAR(256) | |
| summary | VARCHAR(512) | |
| owner_id | BIGINT NOT NULL FK → user_account | 主要维护人 |
| source_skill_id | BIGINT | 派生来源（团队提升到全局时记录） |
| visibility | VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' | PUBLIC / NAMESPACE_ONLY / PRIVATE |
| status | VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' | ACTIVE / HIDDEN / ARCHIVED |
| latest_version_id | BIGINT | 最新已发布版本 |
| download_count | BIGINT NOT NULL DEFAULT 0 | |
| star_count | INT NOT NULL DEFAULT 0 | |
| rating_avg | DECIMAL(3,2) NOT NULL DEFAULT 0.00 | |
| rating_count | INT NOT NULL DEFAULT 0 | |
| created_by | BIGINT FK → user_account | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| updated_by | BIGINT FK → user_account | |
| updated_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `UNIQUE(namespace_id, slug)`
- `(namespace_id, status)` — 命名空间内技能列表

#### skill_version

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| skill_id | BIGINT NOT NULL FK → skill | |
| version | VARCHAR(64) NOT NULL | semver 版本号 |
| status | VARCHAR(32) NOT NULL DEFAULT 'DRAFT' | DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED |
| changelog | TEXT | 变更说明 |
| parsed_metadata_json | JSONB | SKILL.md frontmatter 完整解析 |
| manifest_json | JSONB | 文件清单摘要 |
| file_count | INT NOT NULL DEFAULT 0 | |
| total_size | BIGINT NOT NULL DEFAULT 0 | 总字节数 |
| published_at | TIMESTAMP | 发布时间 |
| created_by | BIGINT FK → user_account | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `UNIQUE(skill_id, version)`
- `(skill_id, status)` — 版本列表

#### skill_file

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| version_id | BIGINT NOT NULL FK → skill_version | |
| file_path | VARCHAR(512) NOT NULL | 包内相对路径 |
| file_size | BIGINT NOT NULL | 字节数 |
| content_type | VARCHAR(128) | MIME 类型 |
| sha256 | VARCHAR(64) NOT NULL | 文件哈希 |
| storage_key | VARCHAR(512) NOT NULL | 对象存储 key |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `UNIQUE(version_id, file_path)`

#### skill_tag

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| skill_id | BIGINT NOT NULL FK → skill | |
| tag_name | VARCHAR(64) NOT NULL | 标签名 |
| version_id | BIGINT NOT NULL FK → skill_version | 指向的版本 |
| created_by | BIGINT FK → user_account | |
| created_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `UNIQUE(skill_id, tag_name)`

#### skill_search_document

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| skill_id | BIGINT NOT NULL UNIQUE FK → skill | 一 skill 一条 |
| namespace_id | BIGINT NOT NULL | 用于空间过滤 |
| namespace_slug | VARCHAR(64) NOT NULL | 冗余，搜索结果直接返回无需 join |
| owner_id | BIGINT NOT NULL | 用于 PRIVATE 可见性判定 |
| title | VARCHAR(256) | |
| summary | VARCHAR(512) | |
| keywords | VARCHAR(512) | |
| search_text | TEXT | SKILL.md 正文 + frontmatter 拼接 |
| visibility | VARCHAR(32) NOT NULL | 冗余，避免搜索时 join |
| status | VARCHAR(32) NOT NULL | |
| search_vector | TSVECTOR GENERATED ALWAYS AS (...) STORED | 全文搜索向量 |
| updated_at | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：
- `UNIQUE(skill_id)`
- GIN 索引 on `search_vector`
- `(namespace_id)` — 空间过滤
- `(visibility)` — 可见性过滤

### 1.2 search_vector 生成列定义

```sql
ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);
```

权重分配：title(A 最高) > summary+keywords(B) > search_text(C)。使用 `simple` 分词配置，零依赖，对中文支持有限但满足一期需求。

---

## 2. 对象存储模块（skillhub-storage）

### 2.1 SPI 接口

```java
package com.iflytek.skillhub.storage;

public interface ObjectStorageService {
    void putObject(String key, InputStream data, long size, String contentType);
    InputStream getObject(String key);
    void deleteObject(String key);
    void deleteObjects(List<String> keys);
    boolean exists(String key);
    ObjectMetadata getMetadata(String key);
}

public record ObjectMetadata(long size, String contentType, Instant lastModified) {}
```

### 2.2 双实现

| 实现类 | 激活条件 | 存储位置 |
|--------|---------|---------|
| `LocalFileStorageService` | `skillhub.storage.provider=local` | 本地文件系统 `${skillhub.storage.local.base-path}` |
| `S3StorageService` | `skillhub.storage.provider=s3` | MinIO / AWS S3 |

通过 `@ConditionalOnProperty(name = "skillhub.storage.provider")` 切换。

### 2.3 LocalFileStorageService 实现要点

- 基于 `java.nio.file.Path` 操作
- key 映射为文件路径：`basePath/key`（key 中的 `/` 映射为目录层级）
- `putObject`：创建父目录 + 写入文件（原子写入：先写 `.tmp` 再 rename）
- `deleteObjects`：逐个删除 + 清理空目录
- 线程安全：文件操作本身是原子的，无需额外锁

### 2.4 S3StorageService 实现要点

- 使用 AWS SDK for Java v2 的 `S3Client`（同步客户端，一期同步发布足够）
- 配置类 `S3StorageProperties`：endpoint, bucket, accessKey, secretKey, region
- `putObject`：`PutObjectRequest` + `RequestBody.fromInputStream()`
- `getObject`：`GetObjectRequest` → `ResponseInputStream`
- bucket 不存在时自动创建（启动时检查）

### 2.5 配置示例

```yaml
# application.yml（默认 local）
skillhub:
  storage:
    provider: local
    local:
      base-path: ./data/storage

# application-s3.yml
skillhub:
  storage:
    provider: s3
    s3:
      endpoint: http://localhost:9000
      bucket: skillhub
      access-key: minioadmin
      secret-key: minioadmin
      region: us-east-1
```

### 2.6 对象 Key 规则

- 文件：`skills/{skillId}/{versionId}/{filePath}`
- 打包 zip：`packages/{skillId}/{versionId}/bundle.zip`
- 使用不可变 ID（skillId/versionId），避免 slug 变更导致 key 失效

---

## 3. 命名空间管理

### 3.1 领域服务

Phase 1 已创建 `Namespace` 和 `NamespaceMember` 实体及 JPA Repository。Phase 2 新增领域服务。

> **注意：Phase 1 实体补齐** — 现有 `Namespace.java` 缺少 `type`（NamespaceType 枚举：GLOBAL/TEAM）和 `avatar_url` 字段，`NamespaceMember.java` 缺少 `updatedAt` 字段，但 V1 数据库表已包含这些列。Phase 2 实现时需先补齐这些字段，确保 JPA 实体与数据库 schema 完全对齐。新增 `NamespaceType` 枚举。

#### NamespaceService（`skillhub-domain`）

```
createNamespace(slug, displayName, description, creatorUserId) → Namespace
  - slug 格式校验 + 保留词校验 + 唯一性校验
  - type 固定为 TEAM（用户不可创建 GLOBAL 类型，GLOBAL 由 Flyway 预置）
  - 创建 namespace 记录
  - 创建者自动成为 OWNER（插入 namespace_member）

updateNamespace(namespaceId, displayName, description, avatarUrl) → Namespace

getNamespaceBySlug(slug) → Namespace

listPublicNamespaces(page, size) → Page<Namespace>
  - 只返回 ACTIVE 状态的命名空间

changeNamespaceStatus(namespaceId, newStatus)
  - ACTIVE ↔ FROZEN ↔ ARCHIVED 状态流转
  - FROZEN：只读不可发布新版本
  - ARCHIVED：对外不可见
```

#### NamespaceMemberService（`skillhub-domain`）

```
addMember(namespaceId, userId, role) → NamespaceMember
  - 唯一约束：一个用户在一个空间只有一个角色
  - 不可直接添加为 OWNER

removeMember(namespaceId, userId)
  - OWNER 不可被移除

updateMemberRole(namespaceId, userId, newRole)
  - 不可通过此方法设置 OWNER（需用 transferOwnership）

transferOwnership(namespaceId, currentOwnerId, newOwnerId)
  - 原 OWNER → ADMIN
  - 目标用户 → OWNER
  - 事务内完成

listMembers(namespaceId, page, size) → Page<NamespaceMember>

getMemberRole(namespaceId, userId) → Optional<NamespaceRole>
```

> **Repository 补充** — Phase 1 的 `NamespaceRepository` 需新增 `Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable)` 方法。`NamespaceMemberRepository` 需新增 `Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable)` 和 `void deleteByNamespaceIdAndUserId(Long namespaceId, Long userId)` 方法。

### 3.2 Slug 校验规则

```java
public class SlugValidator {
    private static final Pattern SLUG_PATTERN =
        Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Set<String> RESERVED_SLUGS = Set.of(
        "admin", "api", "dashboard", "search", "auth",
        "me", "global", "system", "static", "assets", "health"
    );

    public static void validate(String slug) {
        // 长度 2-64
        // 匹配 SLUG_PATTERN
        // 不含连续双连字符 --
        // 不在保留词列表中
    }
}
```

### 3.3 Controller 层

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/v1/namespaces` | 公开 |
| GET | `/api/v1/namespaces/{slug}` | 公开 |
| POST | `/api/v1/namespaces` | 已登录 |
| PUT | `/api/v1/namespaces/{slug}` | namespace ADMIN+ |
| POST | `/api/v1/namespaces/{slug}/members` | namespace ADMIN+ |
| DELETE | `/api/v1/namespaces/{slug}/members/{userId}` | namespace ADMIN+ |
| PUT | `/api/v1/namespaces/{slug}/members/{userId}/role` | namespace OWNER |
| GET | `/api/v1/namespaces/{slug}/members` | namespace MEMBER+ |

权限判定复用 Phase 1 的 `RbacService`，新增 namespace 级别检查方法。

> **SecurityConfig 更新** — Phase 1 的 `SecurityConfig` 中 `.requestMatchers("/api/v1/skills/**", "/api/v1/namespaces/**").permitAll()` 放行了所有 skills/namespaces 路径。Phase 2 新增了需要认证的写操作（publish、tag 管理等），需要细化安全配置：GET 请求 permitAll，POST/PUT/DELETE 请求 authenticated。具体做法：按 HTTP method + path 组合配置，或在 Controller 层通过 `@PreAuthorize` 注解做权限校验（推荐后者，更灵活）。

---

## 4. 技能发布核心链路

### 4.1 领域实体（`domain.skill` 包）

新增实体：

| 实体 | 说明 |
|------|------|
| `Skill` | 技能主表，含 namespace 关联、可见性、统计计数 |
| `SkillVersion` | 版本记录，含 status 状态机、元数据 JSON |
| `SkillFile` | 文件清单，含 storage_key 和 sha256 |
| `SkillTag` | 自定义标签，指向某个 version |

新增枚举：

| 枚举 | 值 |
|------|------|
| `SkillVersionStatus` | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`（Phase 2 只用 DRAFT/PUBLISHED） |
| `SkillVisibility` | `PUBLIC`, `NAMESPACE_ONLY`, `PRIVATE` |
| `SkillStatus` | `ACTIVE`, `HIDDEN`, `ARCHIVED` |

新增 Repository 接口（`domain.skill` 包）：

```java
public interface SkillRepository {
    Optional<Skill> findById(Long id);
    Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Page<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status, Pageable pageable);
    Skill save(Skill skill);
    List<Skill> findByOwnerId(Long ownerId);
    void incrementDownloadCount(Long skillId);  // UPDATE skill SET download_count = download_count + 1 WHERE id = ?
}

public interface SkillVersionRepository {
    Optional<SkillVersion> findById(Long id);
    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);
    Page<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status, Pageable pageable);
    SkillVersion save(SkillVersion version);
}

public interface SkillFileRepository {
    List<SkillFile> findByVersionId(Long versionId);
    SkillFile save(SkillFile file);
    void saveAll(List<SkillFile> files);
    void deleteByVersionId(Long versionId);
}

public interface SkillTagRepository {
    Optional<SkillTag> findBySkillIdAndTagName(Long skillId, String tagName);
    List<SkillTag> findBySkillId(Long skillId);
    SkillTag save(SkillTag tag);
    void delete(SkillTag tag);
}
```

### 4.2 技能包校验器（`domain.skill.validation` 包）

#### SkillPackageValidator

负责解压 zip 后的完整校验：

```
validate(List<PackageEntry> entries) → ValidationResult

校验项：
1. SKILL.md 存在性 — 根目录必须包含 SKILL.md
2. frontmatter 格式 — name 和 description 必需
3. name 字段格式 — 符合 slug 校验规则
4. version 字段 — 必需，semver 格式
5. 文件类型白名单 — .md/.txt/.json/.yaml/.yml/.js/.ts/.py/.sh/.png/.jpg/.svg
6. 单文件大小 ≤ 1MB（可配置）
7. 总包大小 ≤ 10MB（可配置）
8. 文件数量 ≤ 100（可配置）
9. 版本号不与已有版本冲突
```

配置化限制：

```yaml
skillhub:
  publish:
    max-file-size: 1MB
    max-package-size: 10MB
    max-file-count: 100
    allowed-extensions:
      - .md
      - .txt
      - .json
      - .yaml
      - .yml
      - .js
      - .ts
      - .py
      - .sh
      - .png
      - .jpg
      - .svg
```

#### PrePublishValidator 扩展点

```java
public interface PrePublishValidator {
    ValidationResult validate(SkillPackageContext context);
}

public record SkillPackageContext(
    List<PackageEntry> entries,
    SkillMetadata metadata,
    Long publisherId,
    Long namespaceId
) {}

public record ValidationResult(boolean passed, List<String> errors) {
    public static ValidationResult pass() { return new ValidationResult(true, List.of()); }
    public static ValidationResult fail(List<String> errors) { return new ValidationResult(false, errors); }
}

// Phase 2 默认实现
@Component
@ConditionalOnMissingBean(PrePublishValidator.class)
public class NoOpPrePublishValidator implements PrePublishValidator {
    public ValidationResult validate(SkillPackageContext context) {
        return ValidationResult.pass();
    }
}
```

### 4.3 SKILL.md 解析器（`domain.skill.metadata` 包）

```java
public class SkillMetadataParser {
    /**
     * 解析 SKILL.md 内容，提取 frontmatter 和正文
     */
    public SkillMetadata parse(String skillMdContent) { ... }
}

public record SkillMetadata(
    String name,           // → skill.slug（首次发布后不可变更）
    String description,    // → skill.summary
    String version,        // semver 版本号
    String body,           // Markdown 正文
    Map<String, Object> frontmatter  // 完整 frontmatter → parsed_metadata_json
) {}
```

解析规则：
- 使用 SnakeYAML 解析 `---` 之间的 frontmatter
- `name` 和 `description` 为必需字段
- `version` 为必需字段，semver 格式校验
- `x-astron-*` 前缀的扩展字段保留在 frontmatter map 中
- 正文部分（frontmatter 之后的内容）作为 `body` 返回

### 4.4 发布服务（`domain.skill.service` 包）

#### SkillPublishService

```
publishSkill(namespaceSlug, zipInputStream, publisherId, visibility) → SkillVersion

完整流程：
① 解析 namespace — 通过 slug 查找，校验 ACTIVE 状态
② 权限校验 — 用户是该 namespace 的 MEMBER 以上
③ 解压 zip — 内存中解压为 List<PackageEntry>
④ 技能包校验 — SkillPackageValidator.validate()
⑤ PrePublishValidator 链 — 扩展点校验
⑥ 解析 SKILL.md — SkillMetadataParser.parse()
⑦ 创建/关联 skill 记录
   - 首次发布：创建 skill（slug = metadata.name）
   - 后续发布：校验 slug 一致性（不可变更）
⑧ 写入对象存储
   - 逐文件上传到 skills/{skillId}/{versionId}/{filePath}
   - 计算每个文件的 SHA-256
   - 生成 bundle.zip：在服务端内存中重新打包（仅包含通过校验的文件），而非直接使用用户上传的原始 zip
   - bundle.zip 写入 packages/{skillId}/{versionId}/bundle.zip
⑨ 持久化
   - 创建 skill_version（status=PUBLISHED）
   - 批量创建 skill_file 记录
   - 更新 skill.latest_version_id
   - 更新 skill.display_name 和 skill.summary（取最新版本）
⑩ 发布事件
   - 发布 SkillPublishedEvent（触发搜索索引更新）
```

#### PackageEntry 模型

```java
public record PackageEntry(
    String path,          // 包内相对路径
    byte[] content,       // 文件内容
    long size,            // 字节数
    String contentType    // MIME 类型
) {}
```

### 4.5 Controller 层

#### CLI 发布接口

```
POST /api/v1/cli/publish
Content-Type: multipart/form-data
Parts:
  - file: zip 包（必需）
  - namespace: 目标命名空间 slug（必需）
  - visibility: PUBLIC / NAMESPACE_ONLY / PRIVATE（可选，默认 PUBLIC）

Response 200:
{
  "code": 0,
  "data": {
    "skillId": 1,
    "namespace": "global",
    "slug": "my-skill",
    "version": "1.0.0",
    "status": "PUBLISHED",
    "fileCount": 5,
    "totalSize": 12345
  }
}
```

#### Web 端发布接口

```
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data
Parts:
  - file: zip 包（必需）
  - visibility: PUBLIC / NAMESPACE_ONLY / PRIVATE（可选，默认 PUBLIC）

Response: 同 CLI 发布接口
```

Web 端和 CLI 共用 `SkillPublishService`，Controller 层分开。

---

## 5. 技能查询 + 下载 + 标签管理

### 5.1 技能查询服务（`domain.skill.service.SkillQueryService`）

```
getSkillDetail(namespaceSlug, skillSlug, currentUser) → SkillDetailDTO
  - 查找 skill + latest_version 信息
  - 可见性检查
  - 返回：skill 基本信息 + latest version 元数据 + 统计数据

listSkillsByNamespace(namespaceSlug, status, page, size, currentUser) → Page<SkillSummaryDTO>
  - 可见性过滤
  - 默认只返回 ACTIVE 状态

getVersionDetail(namespaceSlug, skillSlug, version) → SkillVersionDetailDTO
  - 返回：版本元数据 + parsed_metadata_json + manifest_json

listVersions(namespaceSlug, skillSlug, page, size) → Page<SkillVersionSummaryDTO>
  - 只返回 PUBLISHED 版本（Phase 2）

listFiles(namespaceSlug, skillSlug, version) → List<SkillFileDTO>
  - 返回文件清单（路径、大小、contentType、sha256）

getFileContent(namespaceSlug, skillSlug, version, filePath) → FileContentDTO
  - 从对象存储读取文件内容
  - 文本文件返回内容字符串，二进制文件返回 base64 或下载链接
```

### 5.2 可见性检查器（`domain.skill.visibility.VisibilityChecker`）

```java
public class VisibilityChecker {
    /**
     * 检查当前用户是否有权访问指定 skill
     * @param skill 目标技能
     * @param currentUser 当前用户（null 表示匿名）
     * @param userNamespaceRoles 用户在各 namespace 的角色（预加载）
     */
    public boolean canAccess(Skill skill, Long currentUserId,
                             Map<Long, NamespaceRole> userNamespaceRoles) {
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> userNamespaceRoles.containsKey(skill.getNamespaceId());
            case PRIVATE -> skill.getOwnerId().equals(currentUserId)
                || isAdminOrAbove(userNamespaceRoles.get(skill.getNamespaceId()));
        };
    }
}
```

查询服务和搜索服务共用此检查器。

### 5.3 下载服务（`domain.skill.service.SkillDownloadService`）

```
downloadLatest(namespaceSlug, skillSlug, currentUser) → DownloadResult
  - 可见性检查
  - 读取 skill.latest_version_id 对应的 bundle.zip
  - 发布 SkillDownloadedEvent（异步更新下载计数）

downloadVersion(namespaceSlug, skillSlug, version, currentUser) → DownloadResult
  - 可见性检查
  - 读取指定版本的 bundle.zip

downloadByTag(namespaceSlug, skillSlug, tagName, currentUser) → DownloadResult
  - 解析 tag → version_id
  - 委托给 downloadVersion
```

```java
public record DownloadResult(
    InputStream content,
    String filename,       // {slug}-{version}.zip
    long contentLength,
    String contentType     // application/zip
) {}
```

### 5.4 标签管理服务（`domain.skill.service.SkillTagService`）

```
listTags(namespaceSlug, skillSlug) → List<SkillTagDTO>
  - 返回所有自定义标签 + 虚拟 latest 标签（指向 skill.latest_version_id）

createOrMoveTag(namespaceSlug, skillSlug, tagName, targetVersion, operatorId) → SkillTag
  - tagName 不可为 "latest"（系统保留）
  - targetVersion 必须是 PUBLISHED 状态
  - 标签已存在则移动（更新 version_id），不存在则创建
  - 权限：skill owner 或 namespace ADMIN+

deleteTag(namespaceSlug, skillSlug, tagName, operatorId)
  - tagName 不可为 "latest"
  - 权限：skill owner 或 namespace ADMIN+
```

### 5.5 Controller 层

#### Public API（`controller.portal.SkillController`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/skills/{namespace}/{slug}` | 技能详情 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions` | 版本列表 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}` | 版本详情 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}/files` | 文件清单 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}/file?path=...` | 读取单个文件 |
| GET | `/api/v1/skills/{namespace}/{slug}/download` | 下载最新版本 |
| GET | `/api/v1/skills/{namespace}/{slug}/versions/{version}/download` | 下载指定版本 |
| GET | `/api/v1/skills/{namespace}/{slug}/tags` | 标签列表 |
| GET | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}/download` | 按标签下载 |
| GET | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}/files` | 按标签查看文件 |
| GET | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}/file?path=...` | 按标签读取文件 |
| GET | `/api/v1/skills/{namespace}/{slug}/resolve` | 解析版本（query: version/tag/hash） |

#### Authenticated API（`controller.portal.SkillManageController`）

| 方法 | 路径 | 权限 |
|------|------|------|
| PUT | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}` | owner / ADMIN+ |
| DELETE | `/api/v1/skills/{namespace}/{slug}/tags/{tagName}` | owner / ADMIN+ |
| GET | `/api/v1/me/skills` | 已登录 |

---

## 6. 搜索实现（PostgreSQL Full-Text）

### 6.1 SPI 实现（`skillhub-search`）

#### PostgresFullTextIndexService

```java
@Service
@ConditionalOnProperty(name = "skillhub.search.provider", havingValue = "postgres", matchIfMissing = true)
public class PostgresFullTextIndexService implements SearchIndexService {

    // upsert by skill_id
    // INSERT INTO skill_search_document (...) VALUES (...)
    // ON CONFLICT (skill_id) DO UPDATE SET title=..., summary=..., ...
    void index(SkillSearchDocument doc);

    // 批量 upsert，使用 batch insert
    void batchIndex(List<SkillSearchDocument> docs);

    // DELETE FROM skill_search_document WHERE skill_id = ?
    void remove(Long skillId);
}
```

#### PostgresFullTextQueryService

```java
@Service
@ConditionalOnProperty(name = "skillhub.search.provider", havingValue = "postgres", matchIfMissing = true)
public class PostgresFullTextQueryService implements SearchQueryService {

    SearchResult search(SearchQuery query) {
        // 1. 构建 tsquery
        //    - 关键词按空格分词，每个词用 & 连接
        //    - 使用 plainto_tsquery('simple', ?) 简化处理
        //
        // 2. 构建 WHERE 条件
        //    - search_vector @@ tsquery（关键词匹配）
        //    - status = 'ACTIVE'（只搜索活跃技能）
        //    - 可见性条件（由 SearchVisibilityScope 转换）：
        //      WHERE (visibility = 'PUBLIC')
        //         OR (visibility = 'NAMESPACE_ONLY' AND namespace_id IN (:memberNsIds))
        //         OR (visibility = 'PRIVATE' AND (namespace_id IN (:adminNsIds) OR owner_id = :userId))
        //    - 可选 namespace_id 过滤
        //
        // 3. 排序
        //    - RELEVANCE: ts_rank_cd(search_vector, tsquery) DESC
        //    - DOWNLOADS: download_count DESC
        //    - RATING: rating_avg DESC
        //    - NEWEST: updated_at DESC
        //
        // 4. 分页
        //    - LIMIT :size OFFSET :page * :size
        //    - COUNT(*) 获取总数
        //
        // 5. 空关键词时退化为列表查询（跳过 tsquery 条件）
    }
}
```

#### PostgresSearchRebuildService

```java
@Service
public class PostgresSearchRebuildService implements SearchRebuildService {

    void rebuildAll() {
        // 获取 Redis 分布式锁 search:rebuild:all（TTL 10min）
        // 分批加载所有 ACTIVE skill（batch 100）
        // 对每个 skill 取 latest_version_id 对应版本内容
        // 批量 upsert 搜索文档
    }

    void rebuildByNamespace(Long namespaceId) {
        // 锁 key: search:rebuild:ns:{namespaceId}
    }

    void rebuildBySkill(Long skillId) {
        // 无需锁，单条 upsert
    }
}
```

### 6.2 搜索应用服务（`domain.skill.service.SkillSearchAppService`）

位于 `skillhub-domain`，面向 Controller 层：

```
searchSkills(keyword, namespaceSlug, sortBy, page, size, currentUser) → SearchResultDTO

内部流程：
1. 计算 SearchVisibilityScope（根据 currentUser 的 namespace 成员关系）
2. 构建 SearchQuery
3. 调用 SearchQueryService.search()
4. 转换为 DTO 返回
```

### 6.3 搜索文档更新时机

| 触发场景 | 事件 | 处理 |
|---------|------|------|
| 技能发布 | `SkillPublishedEvent` | 用最新版本内容 upsert 搜索文档 |
| 技能状态变更 | `SkillStatusChangedEvent` | 更新搜索文档 status |
| 技能归档 | `SkillStatusChangedEvent(ARCHIVED)` | 删除搜索文档 |

### 6.4 搜索 Controller

```
GET /api/v1/skills?q=keyword&namespace=slug&sort=relevance&page=0&size=20

q 参数为空时退化为列表查询（按 sort 字段排序，返回所有可见技能）。

首页精选/热门/最新列表复用搜索接口：
- 精选：GET /api/v1/skills?sort=relevance&size=6（无 q 参数，按综合排序）
- 热门下载：GET /api/v1/skills?sort=downloads&size=6
- 最新发布：GET /api/v1/skills?sort=newest&size=6

Response:
{
  "code": 0,
  "data": {
    "items": [
      {
        "namespace": "global",
        "slug": "code-review",
        "displayName": "Code Review",
        "summary": "...",
        "downloadCount": 1234,
        "starCount": 56,
        "ratingAvg": 4.5,
        "ratingCount": 10,
        "latestVersion": "1.2.0",
        "updatedAt": "2026-03-12T10:00:00Z"
      }
    ],
    "total": 42,
    "page": 0,
    "size": 20
  }
}
```

---

## 7. 异步事件基础设施

### 7.1 事件定义（`domain.event` 包）

```java
public record SkillPublishedEvent(Long skillId, Long versionId, Long publisherId) {}
public record SkillDownloadedEvent(Long skillId, Long versionId) {}
public record SkillStatusChangedEvent(Long skillId, SkillStatus oldStatus, SkillStatus newStatus) {}
```

### 7.2 事件发布

业务服务通过 Spring `ApplicationEventPublisher` 发布：

```java
@Service
public class SkillPublishService {
    private final ApplicationEventPublisher eventPublisher;

    public SkillVersion publishSkill(...) {
        // ... 业务逻辑 ...
        eventPublisher.publishEvent(new SkillPublishedEvent(skill.getId(), version.getId(), publisherId));
        return version;
    }
}
```

### 7.3 事件监听器

#### SearchIndexEventListener

```java
@Component
public class SearchIndexEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    public void onSkillPublished(SkillPublishedEvent event) {
        // 加载 skill + latest version 内容
        // 构建 SkillSearchDocument
        // 调用 SearchIndexService.index()
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    public void onSkillStatusChanged(SkillStatusChangedEvent event) {
        if (event.newStatus() == SkillStatus.ARCHIVED) {
            searchIndexService.remove(event.skillId());
        } else {
            // 更新搜索文档 status
            searchRebuildService.rebuildBySkill(event.skillId());
        }
    }
}
```

#### DownloadCountEventListener

```java
@Component
public class DownloadCountEventListener {

    @EventListener
    @Async("skillhubEventExecutor")
    public void onSkillDownloaded(SkillDownloadedEvent event) {
        // UPDATE skill SET download_count = download_count + 1 WHERE id = ?
        // 原子 SQL，无需乐观锁
        skillRepository.incrementDownloadCount(event.skillId());
    }
}
```

注意：`DownloadCountEventListener` 使用 `@EventListener` 而非 `@TransactionalEventListener`，因为下载操作本身不在事务中。

### 7.4 异步线程池配置

```java
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
        executor.setAwaitTerminationSeconds(25);  // 配合 30s shutdown timeout 优雅停机
        executor.initialize();
        return executor;
    }
}
```

- `CallerRunsPolicy`：队列满时由调用线程执行，保证事件不丢失
- 单体应用规模，core=2 max=4 足够

---

## 8. 应用层精细限流

### 8.1 自定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String category();
    int authenticated() default 60;
    int anonymous() default 20;
    int windowSeconds() default 60;
}
```

### 8.2 Redis 滑动窗口实现

使用 ZSET + Lua 脚本实现原子操作：

```
Key: ratelimit:{category}:{userId 或 IP}
Score: 请求时间戳（毫秒）
```

Lua 脚本流程：
1. `ZREMRANGEBYSCORE` 清理窗口外的过期记录
2. `ZCARD` 获取当前窗口内请求数
3. 如果未超限，`ZADD` 记录当前请求 + 设置 key TTL
4. 返回剩余配额

### 8.3 拦截器

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    // 1. 从 HandlerMethod 获取 @RateLimit 注解
    // 2. 判断已登录/匿名，选择对应限额
    // 3. 构建 Redis key（已登录用 userId，匿名用 IP）
    // 4. 执行 Lua 脚本判定
    // 5. 超限：返回 429 + Retry-After header
    // 6. 未超限：放行 + 设置 X-RateLimit-Remaining header
}
```

### 8.4 限流配置

| 端点类别 | 已登录 | 匿名（按 IP） | 窗口 |
|---------|--------|-------------|------|
| search | 60 次 | 20 次 | 60s |
| download | 120 次 | 30 次 | 60s |
| publish | 10 次 | N/A | 3600s |
| auth | N/A | 30 次 | 60s |

### 8.5 容错

Redis 不可用时 fail-open（放行请求），记录 WARN 日志。不因限流组件故障阻塞业务。

---

## 9. 前端 Chunk 2

### 9.1 路由结构

```
/                                      → 首页
/search                                → 搜索页
/@{namespace}                          → 命名空间主页
/@{namespace}/{slug}                   → 技能详情页
/@{namespace}/{slug}/versions          → 版本历史页
/dashboard/skills                      → 我的技能
/dashboard/publish                     → 发布技能
/dashboard/namespaces                  → 我的命名空间
/dashboard/namespaces/{slug}/members   → 成员管理
```

门户区（`/`, `/search`, `/@*`）匿名可访问。
Dashboard 区需登录（复用 Phase 1 路由守卫）。

### 9.2 新增依赖

| 包 | 用途 |
|----|------|
| `react-markdown` | SKILL.md Markdown 渲染 |
| `rehype-highlight` | 代码块语法高亮 |
| `react-dropzone` | 拖拽上传 |
| `zustand` | 客户端状态（UI 偏好、过滤器） |

### 9.3 共享组件（`shared/`）

| 组件 | 说明 |
|------|------|
| `SkillCard` | 技能卡片：名称、摘要、namespace 标签、下载量、版本号 |
| `SearchBar` | 搜索输入框，debounce 300ms |
| `MarkdownRenderer` | react-markdown + rehype-highlight 渲染 SKILL.md |
| `FileTree` | 文件树组件，展示技能包目录结构 |
| `CopyButton` | 一键复制安装命令 |
| `Pagination` | 分页组件 |
| `EmptyState` | 空状态占位图 |
| `SkeletonLoader` | 骨架屏加载态 |
| `NamespaceBadge` | 命名空间标签（@global 绿色，@team 蓝色） |

### 9.4 页面设计

#### 首页（`/`）

- Hero 区域：居中大标题 + 副标题 + 搜索框
- 三栏卡片区：精选 / 热门下载 / 最新发布（各 top 6）
- 底部：命名空间快速入口卡片
- 现代产品风：大留白、渐变背景、卡片悬浮阴影

#### 搜索页（`/search`）

- 顶部搜索栏 + 过滤器行（命名空间下拉、排序选择器）
- 网格布局 SkillCard 列表（响应式 1-3 列）
- URL query params 驱动：`?q=xxx&namespace=xxx&sort=relevance&page=1`
- 空结果状态 + 搜索建议
- TanStack Query `keepPreviousData: true` 避免翻页闪烁

#### 命名空间主页（`/@{namespace}`）

- 顶部：namespace 头像 + 名称 + 描述 + 成员数
- 技能列表：该空间下的技能卡片网格
- 分页 + 排序

#### 技能详情页（`/@{namespace}/{slug}`）

- 左侧主区域（70%）：
  - Tab 切换：README / 文件列表 / 版本历史
  - README tab：MarkdownRenderer 渲染 SKILL.md 正文
  - 文件列表 tab：FileTree + 文件内容预览
  - 版本历史 tab：版本列表 + changelog
- 右侧信息栏（30%）：
  - 版本号 + 发布时间
  - 下载量统计
  - 命名空间标签（NamespaceBadge）
  - 标签列表（latest + 自定义标签）
  - 安装命令（双格式：skillhub CLI + ClawHub CLI）+ CopyButton
  - 下载按钮
- 匿名用户可浏览和下载 PUBLIC 技能

#### 版本历史页（`/@{namespace}/{slug}/versions`）

- 版本列表：版本号、发布时间、文件数、总大小
- 每个版本可展开查看 changelog
- 下载指定版本按钮

#### 发布页（`/dashboard/publish`）

- Step 1：选择命名空间（下拉，用户所属的 namespace 列表）
- Step 2：拖拽上传区域（react-dropzone）+ 进度条
- Step 3：上传后预览
  - SKILL.md Markdown 渲染
  - 文件树
  - 元数据摘要（name、version、description）
- Step 4：选择可见性（PUBLIC / NAMESPACE_ONLY / PRIVATE）
- Step 5：确认发布按钮
- 发布成功后跳转到技能详情页

#### 我的技能（`/dashboard/skills`）

- 表格列表：技能名、命名空间、最新版本、状态、下载量、发布时间
- 点击跳转到技能详情页
- 空状态引导发布

#### 我的命名空间（`/dashboard/namespaces`）

- 卡片列表：namespace 名称、角色、成员数、技能数
- 创建命名空间按钮 + 对话框表单
- 点击进入成员管理

#### 成员管理（`/dashboard/namespaces/{slug}/members`）

- 成员表格：用户名、角色、加入时间
- 添加成员按钮 + 对话框（用户搜索 + 角色选择）
- 角色变更下拉
- 移除成员按钮（带确认）
- OWNER 转让操作（独立按钮，二次确认）

### 9.5 数据获取模式（TanStack Query）

每个 API 端点封装为自定义 hook：

| Hook | Query Key | 说明 |
|------|-----------|------|
| `useSkillDetail(ns, slug)` | `['skills', ns, slug]` | 技能详情 |
| `useSkillVersions(ns, slug)` | `['skills', ns, slug, 'versions']` | 版本列表 |
| `useSkillFiles(ns, slug, ver)` | `['skills', ns, slug, 'versions', ver, 'files']` | 文件清单 |
| `useSearchSkills(params)` | `['skills', 'search', params]` | 搜索 |
| `useNamespaceDetail(slug)` | `['namespaces', slug]` | 命名空间详情 |
| `useNamespaceSkills(slug)` | `['namespaces', slug, 'skills']` | 空间技能列表 |
| `useNamespaceMembers(slug)` | `['namespaces', slug, 'members']` | 成员列表 |
| `useMySkills()` | `['me', 'skills']` | 我的技能 |
| `useMyNamespaces()` | `['me', 'namespaces']` | 我的命名空间 |
| `useFeaturedSkills()` | `['skills', 'featured']` | 首页精选 |
| `usePopularSkills()` | `['skills', 'popular']` | 首页热门 |
| `useRecentSkills()` | `['skills', 'recent']` | 首页最新 |

Mutation hooks：

| Hook | 成功后 invalidate |
|------|-------------------|
| `usePublishSkill()` | `['me', 'skills']`, `['namespaces', ns, 'skills']` |
| `useCreateNamespace()` | `['me', 'namespaces']`, `['namespaces']` |
| `useAddMember()` | `['namespaces', slug, 'members']` |
| `useRemoveMember()` | `['namespaces', slug, 'members']` |
| `useCreateTag()` | `['skills', ns, slug, 'tags']` |
| `useDeleteTag()` | `['skills', ns, slug, 'tags']` |

### 9.6 前端文件结构（Phase 2 新增）

```
web/src/
├── pages/
│   ├── search.tsx
│   ├── namespace.tsx
│   ├── skill-detail.tsx
│   ├── skill-versions.tsx
│   ├── dashboard/
│   │   ├── skills.tsx
│   │   ├── publish.tsx
│   │   ├── namespaces.tsx
│   │   └── namespace-members.tsx
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
│   ├── ui/                    # shadcn/ui 组件（已有）
│   ├── components/
│   │   ├── pagination.tsx
│   │   ├── empty-state.tsx
│   │   ├── skeleton-loader.tsx
│   │   ├── copy-button.tsx
│   │   └── namespace-badge.tsx
│   └── hooks/
│       └── use-debounce.ts
└── api/
    └── client.ts              # 已有，Phase 2 自动生成新类型
```

---

## 10. 测试策略

### 10.1 后端测试分层

| 层级 | 范围 | 工具 | 覆盖重点 |
|------|------|------|---------|
| 单元测试 | 领域服务、校验器、解析器 | JUnit 5 + Mockito | SkillPackageValidator、SkillMetadataParser、VisibilityChecker、SlugValidator |
| 集成测试 | Repository + DB | @DataJpaTest + Testcontainers PostgreSQL | JPA 映射、唯一约束、全文搜索查询 |
| 集成测试 | 对象存储 | @SpringBootTest + LocalFileStorage | 上传/下载/删除流程 |
| API 测试 | Controller | @WebMvcTest + MockMvc | 请求/响应格式、权限校验、参数校验 |
| 端到端测试 | 发布全链路 | @SpringBootTest + Testcontainers | 上传 → 校验 → 存储 → 持久化 → 搜索 |

### 10.2 关键测试用例

#### SkillPackageValidator 测试

- 有效技能包 → 通过
- 缺少 SKILL.md → 失败
- frontmatter 缺少 name → 失败
- frontmatter 缺少 version → 失败
- 非法文件类型（.exe） → 失败
- 单文件超 1MB → 失败
- 总包超 10MB → 失败
- 文件数超 100 → 失败
- 版本号非 semver → 失败
- 版本号与已有冲突 → 失败

#### SkillMetadataParser 测试

- 标准 frontmatter + body → 正确解析
- 含 x-astron- 扩展字段 → 保留在 frontmatter map
- 无 frontmatter → 失败
- frontmatter 格式错误 → 失败

#### VisibilityChecker 测试

- PUBLIC skill + 匿名用户 → 可访问
- NAMESPACE_ONLY skill + 非成员 → 不可访问
- NAMESPACE_ONLY skill + 成员 → 可访问
- PRIVATE skill + owner → 可访问
- PRIVATE skill + namespace ADMIN → 可访问
- PRIVATE skill + 其他用户 → 不可访问

#### 命名空间管理测试

- 创建 namespace → 创建者成为 OWNER
- slug 保留词 → 拒绝
- slug 含 -- → 拒绝
- 移除 OWNER → 拒绝
- 转让 ownership → 原 OWNER 降为 ADMIN

#### 搜索测试

- 关键词匹配 title → 返回结果
- 关键词匹配 summary → 返回结果
- 匿名搜索 → 只返回 PUBLIC
- namespace 过滤 → 只返回指定空间
- 排序：RELEVANCE / DOWNLOADS / NEWEST → 正确排序

#### 限流测试

- 未超限 → 放行 + 返回 X-RateLimit-Remaining
- 超限 → 429 + Retry-After
- Redis 不可用 → fail-open 放行

### 10.3 前端测试

| 类型 | 工具 | 覆盖重点 |
|------|------|---------|
| 组件测试 | Vitest + React Testing Library | SkillCard、SearchBar、FileTree、MarkdownRenderer |
| Hook 测试 | renderHook | useSearchSkills、useSkillDetail、usePublishSkill |
| 页面测试 | Vitest + MSW (Mock Service Worker) | 搜索页交互、发布流程、路由守卫 |

---

## 11. Chunk 划分与验收标准

### Chunk 1：后端全部

**范围：** 数据库迁移 + 对象存储 + 命名空间管理 + 技能发布/查询/下载 + 标签管理 + 搜索 + 异步事件 + 限流

**验收标准：**

1. `V2__phase2_skill_tables.sql` 迁移成功，所有新表和索引创建
2. Phase 1 实体补齐：`Namespace.java` 补 type/avatarUrl，`NamespaceMember.java` 补 updatedAt，新增 `NamespaceType` 枚举
3. 对象存储 LocalFile 实现可用，S3 实现可用（Docker Compose MinIO）
3. 命名空间 CRUD + 成员管理 API 全部可用
4. CLI 发布接口：上传 zip → 校验 → 存储 → PUBLISHED，返回版本信息
5. 技能详情、版本列表、文件清单、文件内容 API 可用
6. 下载 API：latest / 指定版本 / 按标签，返回 zip
7. 标签 CRUD API 可用，latest 标签不可操作
8. 搜索 API：关键词搜索 + 可见性过滤 + 排序 + 分页
9. 异步事件：发布后搜索索引自动更新，下载后计数自动递增
10. 限流：超限返回 429，Redis 不可用时 fail-open
11. 所有后端测试通过

### Chunk 2：前端全部

**范围：** 首页 + 搜索页 + 命名空间主页 + 技能详情页 + 版本历史页 + 发布页 + 我的技能 + 我的命名空间 + 成员管理

**前置：** Chunk 1 后端 API 全部就绪

**验收标准：**

1. 首页展示精选/热门/最新技能，搜索框可跳转搜索页
2. 搜索页：关键词搜索 + 命名空间过滤 + 排序 + 分页，URL 驱动
3. 命名空间主页：展示空间信息 + 技能列表
4. 技能详情页：Markdown 渲染 + 文件树 + 版本历史 + 安装命令 + 下载
5. 发布页：拖拽上传 + 预览 + 选择命名空间/可见性 + 确认发布
6. 我的技能：列表展示 + 跳转详情
7. 命名空间管理：创建 + 成员管理（添加/移除/角色变更/转让）
8. 匿名用户可浏览/搜索/下载 PUBLIC 技能
9. 现代产品风视觉设计（使用 frontend-design 技能优化）
10. 前端测试通过
