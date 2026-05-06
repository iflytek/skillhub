# skillhub 搜索架构

## 1 SPI 接口

```java
public interface SearchIndexService {
    void index(SkillSearchDocument doc);
    void batchIndex(List<SkillSearchDocument> docs);
    void remove(Long skillId);
}

public interface SearchQueryService {
    SearchResult search(SearchQuery query);
}

public interface SearchRebuildService {
    void rebuildAll();
    void rebuildByNamespace(Long namespaceId);
    void rebuildBySkill(Long skillId);
}
```

## 2 SearchQuery 模型

```java
public record SearchQuery(
    String keyword,
    Long namespaceId,           // 可选，指定空间搜索
    String namespaceSlug,       // 可选
    SearchVisibilityScope scope, // ACL 投影，由应用服务层计算注入
    SortField sortBy,           // RELEVANCE / DOWNLOADS / RATING / NEWEST
    int page,
    int size
) {}

// 搜索可见范围投影，由应用服务层根据当前用户计算
public record SearchVisibilityScope(
    boolean includeAllPublic,        // 是否包含所有 PUBLIC 技能
    Set<Long> memberNamespaceIds,    // 用户是 MEMBER 的 namespace（可见 NAMESPACE_ONLY）
    Set<Long> adminNamespaceIds,     // 用户是 ADMIN 的 namespace（可见 PRIVATE）
    String userId                    // 当前用户 ID（可见自己的 PRIVATE skill），匿名为 null
) {}
```

ACL 投影计算规则：
- 匿名用户：`includeAllPublic=true`，其余为空集，`userId=null`
- 已登录用户：`includeAllPublic=true`，`memberNamespaceIds` = 用户所属空间，`adminNamespaceIds` = 用户是 ADMIN 以上的空间，`userId` = 当前用户 ID

当前 MySQL 运行时实现中，`SearchVisibilityScope` 转换为 WHERE 条件：
```sql
WHERE (visibility = 'PUBLIC')
   OR (visibility = 'NAMESPACE_ONLY' AND namespace_id IN (:memberNamespaceIds))
   OR (visibility = 'PRIVATE' AND (namespace_id IN (:adminNamespaceIds) OR owner_id = :userId))
```

迁移到 ES 时，`SearchVisibilityScope` 可直接映射为 bool query 的 should/filter 子句。

## 3 搜索文档表 skill_search_document

一个 skill 对应一条搜索文档，但文档内容的来源语义应严格收敛为“当前最新已发布版本”。实现上仍可由 `latest_version_id` 作为缓存指针承载，但它只允许指向 `PUBLISHED` 版本；搜索层不能再把它当作泛化的“当前版本”。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | 唯一，一 skill 一条 |
| namespace_id | bigint | 用于空间过滤 |
| owner_id | VARCHAR(128) | 用于 PRIVATE 可见性判定 |
| title | varchar(256) | |
| summary | varchar(512) | |
| keywords | varchar(512) | |
| search_text | text | `displayName`、`slug`、`summary`，以及 frontmatter 中除 `name` / `description` / `version` 外的字段展开结果 |
| visibility | enum | 冗余，避免搜索时 join |
| status | enum | |
| updated_at | datetime | |

唯一约束：`(skill_id)`

当前默认搜索运行时以 MySQL 作为权威数据源，并通过 `local-file-index`（Lucene）维护独立索引目录；`skill_search_document` 继续承担数据库侧权威搜索文档快照。

## 4 索引写入时机

以下场景触发搜索文档更新（upsert by skill_id）：
- 审核通过（`PENDING_REVIEW → PUBLISHED`）：重算“最新已发布版本”指针，并用该发布版本内容更新搜索文档
- 已发布版本被撤回（`PUBLISHED → YANKED`）：重算“最新已发布版本”指针；若不存在任何已发布版本，则移除搜索文档
- 技能状态变更（隐藏/归档/恢复）：更新搜索文档的 status 字段

## 5 搜索演进路线

### 5.1 一期数据建模约束

一期“每个 skill 一条搜索文档、内容永远取最新已发布版本”是有意的简化。当前实现仍使用 `latest_version_id` 作为持久化指针，但这里的语义已经收敛为 latest published pointer。这个模型在以下场景下会不够用：

- 版本级检索（搜索某个旧版本的内容）
- 自定义标签/通道检索（搜索 `@beta` 标签指向的版本内容）
- 向量 chunk 索引（一个 skill 的 SKILL.md 拆成多个 embedding chunk）

这些场景不是简单换 provider 能解决的，需要改表结构和索引写入逻辑。

**一期搜索能力边界（产品限制）：**
- 搜索只基于“最新已发布版本”的内容
- 不支持按 version 或 tag 搜索内容
- 搜索结果不区分 channel（`beta`、`stable` 等标签通道）
- 用户通过 tag 安装的技能内容可能与搜索结果展示的内容不一致（搜索展示 latest，安装的是 tag 指向的版本）
- 若要支持 channel-aware 搜索，必须升级到 version 级索引（二期 ES 实现）

### 5.2 演进阶段

| 阶段 | 实现 | 索引粒度 | 切换方式 |
|------|------|---------|---------|
| 当前 | MySQL + local-file-index (Lucene) | 每 skill 一条（latest published） | 默认 |
| 回退 | MySQL + mysql-like | 每 skill 一条（latest published） | 配置 `skillhub.search.provider=mysql-like` |
| 二期 | ES / OpenSearch | 每 skill_version 一条 + skill 聚合文档 | 配置 `search.provider=elasticsearch` |
| 三期 | 向量检索 | 每 skill_version 多条（chunk 级） | 配置 `search.provider=vector` |
| 四期 | 混合排序 | 关键词 + 向量混合 | 配置 `search.provider=hybrid` |

当前代码实现已落在 “MySQL + local-file-index” 阶段：
- MySQL 保持 `skill_search_document` 作为权威快照表
- `local-file-index` 负责 query/index/rebuild 三类最终默认 bean
- `mysql-like` 保留为显式回退路径
- 语义向量字段仍保留在文档模型中，供后续相关度增强使用

### 5.4 当前 provider 隔离

- 当前正式搜索运行时只保留 `skillhub.search.engine=mysql`。
- 在该 engine 下，由 `skillhub.search.provider` 在 `local-file-index` 与 `mysql-like` 之间切换最终实现。
- `prod` profile 默认走 `local-file-index`，`mysql-like` 仅作为显式回退路径；`dev` / `test` 默认走 `mysql-like`。

### 5.5 Phase 3 local-file-index 配置约定

- 对外搜索 provider 配置统一收敛为 `skillhub.search.provider`：
  - `mysql-like`：`local-file-index` 不可用时的显式回退值
  - `local-file-index`：第三阶段 Lucene provider，也是 `prod` 当前默认值
- 当前 bean 装配由 `skillhub.search.engine=mysql` 搭配 `skillhub.search.provider=mysql-like|local-file-index` 控制；`local-file-index` 负责 query/index/rebuild 三类 bean 的最终默认装配。
- `local-file-index` 采用嵌入式 Lucene，和应用进程同生命周期运行，不引入独立搜索 daemon。
- 嵌入式 Lucene 索引目录使用 `skillhub.search.local-file-index.directory`：
  - `application.yml` 默认值：`${user.home}/.skillhub/search-index`
  - `prod` 常见值：`/var/lib/skillhub/search-index`
- 目录策略：
  - 索引目录必须与 `skillhub.storage.local.base-path` 分离，避免清空索引时误删上传包内容。
  - 目录应位于单节点本地可写路径；当前不把多实例共享文件系统当作前提。
  - 目录不存在时允许启动期创建；空目录应被视为“需要 rebuild”的合法状态。
  - `rebuildAll()` 可以先清空并重建整个索引目录；`rebuildByNamespace()` / `rebuildBySkill()` 只允许重写受影响文档，不应直接删除整个目录。
  - 如果索引损坏或需要手工恢复，推荐删除该目录后执行全量 rebuild；业务主库 MySQL 仍是权威数据源。
  - 应用关闭或 skill 删除不自动清理整个目录；只有显式 rebuild/reset 操作才允许重置目录级状态。
- 文档模型约定：
  - 仍以 skill 为粒度建档，一条 Lucene 文档对应一个 skill，`skillId` 作为稳定主键。
  - 搜索、过滤、排序与 hydrate 所需的核心字段包括 `skillId`、`namespaceId`、`namespaceSlug`、`ownerId`、`title`、`summary`、`keywords`、`searchText`、`semanticVector`、`visibility`、`status`。
  - `title`、`summary`、`keywords`、`searchText` 负责关键词召回；`namespaceId` / `namespaceSlug` 负责 namespace 过滤；`ownerId`、`visibility`、`status` 负责 ACL 与生命周期过滤；`semanticVector` 仅用于相关度重排，不影响基础可用性。
  - 搜索结果仍返回 `skillIds + pagination metadata`，hydrate 继续走现有 skill 读取链路，Lucene 不直接承担 UI 投影。
  - `labelSlugs` 仍属于查询契约的一部分；如果后续需要把标签过滤下沉到 Lucene 索引层，应显式增加可检索的标签字段，而不是依赖隐式 join。
- 更新触发器约定：
  - `publish` / `PENDING_REVIEW -> PUBLISHED`：按最新已发布版本重建该 skill 的文档内容。
  - `archive` / `restore` / 可见性变化：保持文档主键不变，只更新 `status`、`visibility` 等生命周期字段。
  - `delete` / `PUBLISHED -> YANKED`：如果不存在任何已发布版本，则删除该 skill 的 Lucene 文档；如果仍存在已发布版本，则回写最新已发布版本对应的文档。
  - `rebuildAll()`：从 MySQL 权威数据源全量重建索引目录。
  - `rebuildByNamespace()` / `rebuildBySkill()`：只重建命中的范围，不影响其他 namespace 或 skill。
- 回退路径约定：
  - 运行时通过 `skillhub.search.provider=mysql-like|local-file-index` 切换最终搜索后端。
  - `mysql-like` 是 `local-file-index` 的显式回退方案；如果 Lucene 目录损坏、索引格式变更或本地文件系统不可写，优先切回 `mysql-like`，再执行重建或清理。
  - 回退不要求切到历史数据库实现；MySQL 仍是搜索文档的权威数据源。

### 5.3 SPI 演进策略

一期 SPI 接口（`SearchIndexService` / `SearchQueryService`）的入参是 `SkillSearchDocument`（skill 粒度）。二期切换到 ES 时：

1. 新增 `SkillVersionSearchDocument` 模型（version 粒度）
2. `SearchIndexService` 新增 `indexVersion()` 方法（向下兼容，一期实现空方法）
3. ES 实现同时写入 skill 聚合文档 + version 文档
4. `SearchQueryService.search()` 的返回结果不变（仍返回 skill 级摘要），内部实现切换为 ES 查询

这意味着二期切换不是零成本的——需要新增模型、扩展 SPI、重建索引。但一期不为此过度设计，SPI 抽象保证了切换时不需要改业务层代码。

通过 `@ConditionalOnProperty` 或自定义 SPI 加载机制切换。

## 6 分布式安全

`rebuildAll()` / `rebuildByNamespace()` 执行前获取 Redis 分布式锁（key: `search:rebuild:{scope}`，TTL: 10min），获取失败则跳过。

## 7 当前实现说明

当前仓库的有效搜索运行路径以 `MySQL + local-file-index` 为准，`mysql-like` 仅作为显式回退 provider。
