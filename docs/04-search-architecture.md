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
    Set<Long> adminNamespaceIds,     // 管理上下文，门户搜索不因此展示 PRIVATE
    String userId                    // 当前用户 ID，匿名为 null；PRIVATE 在“我的技能”管理
) {}
```

ACL 投影计算规则：
- 匿名用户：`includeAllPublic=true`，其余为空集，`userId=null`
- 已登录用户：`includeAllPublic=true`，`memberNamespaceIds` = 用户所属空间，`adminNamespaceIds` = 用户是 ADMIN 以上的空间，`userId` = 当前用户 ID

一期 PostgreSQL 实现中，`SearchVisibilityScope` 转换为 WHERE 条件：
```sql
WHERE (visibility = 'PUBLIC')
   OR (visibility = 'NAMESPACE_ONLY' AND namespace_id IN (:memberNamespaceIds))
```

门户市场仅展示公开技能和当前用户所属空间的成员可见技能；即使本人或管理员有权访问 PRIVATE 详情，也不会将其列入市场。私有技能在“我的技能”管理。查询权限使用当前 `skill.visibility`，不信任可能尚未刷新的索引范围。

迁移到 ES 时，`SearchVisibilityScope` 可直接映射为 bool query 的 should/filter 子句。

## 3 搜索文档表 skill_search_document

一个 skill 对应一条搜索文档，但文档内容的来源语义应严格收敛为“当前最新已发布版本”。搜索消费方必须验证发布与可安装条件，不能仅凭 `latest_version_id` 非空认定可公开分发；该原始指针存在 PRIVATE 上传例外，见[生命周期 Latest 约束](14-skill-lifecycle.md#31-latest)。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | 唯一，一 skill 一条 |
| namespace_id | bigint | 用于空间过滤 |
| owner_id | VARCHAR(128) | 用于 PRIVATE 可见性判定 |
| title | varchar(256) | |
| summary | varchar(512) | |
| keywords | varchar(512) | |
| search_text | text | `displayName`、`slug`、有效简短描述、原始包 `description`，以及 frontmatter 中除 `name` / `description` / `version` 与仅用于详情的 `metadata.overview` 外的字段展开结果 |
| visibility | enum | 冗余，避免搜索时 join |
| status | enum | |
| updated_at | datetime | |

唯一约束：`(skill_id)`

PostgreSQL 全文搜索索引：表增加 `search_vector tsvector` 生成列，基于 `title`、`summary`、`keywords`、`search_text` 自动维护，建立 GIN 索引。详见第 7 节。

## 4 索引写入时机

当前 `SearchIndexEventListener` 在事务提交后异步消费以下事件：
- `SkillPublishedEvent`（审核通过、直发等）：重建该 skill 的搜索文档。生命周期服务负责更新版本指针，索引监听器不负责重算指针。
- `SkillVersionYankedEvent`：撤回服务重算发布指针，监听器调用 `rebuildBySkill`。当前重建器即使找不到版本，仍可按 skill 信息生成文档，不会因此直接移除索引行；索引行存在不等于公开可检索。
- `SkillStatusChangedEvent`：归档时移除搜索文档，取消归档时重建。
- hide/unhide 当前不发上述状态事件，不靠索引 status 更新生效；查询端关联 skill 并过滤 `hidden`，公开搜索还检查发布状态和下载就绪。

实现依据：`SearchIndexEventListener`、`PostgresSearchRebuildService.rebuildBySkill/toDocument` 和 `PostgresFullTextQueryService`。撤回后无发布版本应对公众不可检索，但不能将这一结果写成已物理删除搜索文档。

## 5 搜索演进路线

### 5.1 一期数据建模约束

一期“每个 skill 一条搜索文档、内容永远取最新已发布版本”是有意的简化。公开搜索的 latest 语义是 latest published；原始持久化指针的例外不能改变这个公开契约。这个模型在以下场景下会不够用：

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
| 一期 | PostgreSQL Full-Text (tsvector + GIN) | 每 skill 一条（latest published） | 默认 |
| 一点五期 | PostgreSQL Full-Text + 语义向量重排 | 每 skill 一条（latest published） | 配置 `skillhub.search.semantic.enabled=true` |
| 二期 | ES / OpenSearch | 每 skill_version 一条 + skill 聚合文档 | 配置 `search.provider=elasticsearch` |
| 三期 | 向量检索 | 每 skill_version 多条（chunk 级） | 配置 `search.provider=vector` |
| 四期 | 混合排序 | 关键词 + 向量混合 | 配置 `search.provider=hybrid` |

当前代码实现已落在“一点五期”：
- 仍然使用 PostgreSQL 全文搜索作为主召回
- 搜索文档表新增 `semantic_vector` 缓存字段
- relevance 排序下，对全文候选集追加语义向量重排
- 语义向量不可用时自动降级为现有全文相关度排序

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

## 7 PostgreSQL 全文搜索中文支持

PostgreSQL 全文搜索使用 `tsvector` + `tsquery` + GIN 索引：

```sql
-- 增加 tsvector 生成列
ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

-- 建立 GIN 索引
CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);
```

中文支持方案：
- 一期使用 `simple` 分词配置（按空格和标点分词），对中文支持有限但零依赖
- 如需更好的中文分词，可安装 `zhparser` 或 `pg_jieba` 扩展，替换为对应的 text search configuration
- PostgreSQL 的 `tsvector` 支持权重（A/B/C/D），可对 title 赋予更高权重，提升搜索相关性

已知局限：`simple` 分词对中文的精度不如专业搜索引擎。建议 Phase 2 完成后评估搜索效果，如不满足需求则在 Phase 3 提前引入 ES。
