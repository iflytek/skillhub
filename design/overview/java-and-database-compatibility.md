# Tech Stack 兼容性分析

> 分析日期：2026-04-28
>
> 状态更新（2026-05-04）：本文形成于仓库仍以 PostgreSQL 路线做数据库选型分析的阶段，用于保留历史方案比较背景。它不再代表当前默认运行时。当前标准运行时请以 `MySQL 8 + Redis + local-file-index` 为准，参见 [../runtime/mysql-runtime-and-search-provider-migration.md](../runtime/mysql-runtime-and-search-provider-migration.md)。

## 1. 历史背景：PostgreSQL 版本兼容性

### 1.1 当前配置

以下内容描述的是项目在迁移前使用 PostgreSQL 作为主运行时时的关键特性：

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

### 1.2 最低兼容版本：PostgreSQL 12

| 特性 | 项目中使用的位置 | 最低 PG 版本 | PG 16 支持 |
|------|---------------|------------|-----------|
| **GENERATED ALWAYS ... STORED** | 全文搜索向量列（V2, V9, V31） | **PG 12** | ✅ |
| **JSONB** | 5 个字段存储 JSON | PG 9.4 | ✅ |
| **全文搜索** (tsvector/tsquery/setweight) | 搜索服务 | PG 8.3 | ✅ |
| **GIN 索引** | 全文搜索索引 | 始终支持 | ✅ |

> 注：`GENERATED ALWAYS ... STORED` 语法从 PostgreSQL 12 开始支持，是项目中版本要求最高的关键特性。

### 1.3 结论

```
PostgreSQL 12 ≤ 你的环境 ≤ PostgreSQL 16  →  都可以用 ✅
PostgreSQL 11 及以下                       →  不兼容 ❌
```

---

## 2. 国产数据库替代方案分析

### 2.1 方案概览

| 数据库 | 内核兼容 | JSON 存储 | 全文搜索 | 迁移难度 |
|--------|--------|------|---------|---------|
| GoldenDB（中兴） | MySQL | ⚠️ JSON 类型 | ❌ | 高 |
| TiDB（PingCAP） | MySQL | ⚠️ JSON 类型 | ❌ | 高 |
| TDSQL（腾讯云）- PG 模式 | PostgreSQL | ✅ | ✅ | **中** |
| GaussDB（华为）- PG 模式 | PostgreSQL | ✅ | ✅ | **中** |

### 2.2 GoldenDB（中兴，MySQL 兼容）

| 指标 | 详情 |
|------|------|
| **数据库内核** | MySQL 5.7 兼容 |
| **最低推荐版本** | GoldenDB 6.0+（支持 MySQL 5.7 特性） |
| **JSON 存储** | ⚠️ MySQL JSON 类型，语法与 JSONB 不同（查询用 `JSON_EXTRACT`/`JSON_VALUE` 而非 `->`/`->>`） |
| **全文搜索** | ❌ 无 tsvector/tsquery/setweight。MySQL FULLTEXT 不支持权重排名 |
| **GENERATED STORED 列** | ✅ MySQL 5.7+ 支持 |
| **分布式事务** | ✅ GoldenDB 强项（基于 Paxos/GTID） |
| **Java 代码改动** | 高。必须引入 Elasticsearch 替代全文搜索，所有 `PostgresFullText*Service` 重写 |
| **风险点** | 全文搜索功能必须用外部引擎替代，搜索权重语义丢失 |

### 2.3 TiDB（PingCAP，MySQL 兼容）

| 指标 | 详情 |
|------|------|
| **数据库内核** | MySQL 5.7 兼容 |
| **最低推荐版本** | TiDB 5.0+ |
| **JSON 存储** | ⚠️ 支持 JSON 类型 |
| **全文搜索** | ❌ TiDB 无 tsvector/tsquery，即使在 MySQL 兼容模式下也不支持全文搜索扩展 |
| **GENERATED STORED 列** | ✅ 支持 |
| **分布式 HTAP** | ✅ TiDB 强项（OLTP + OLAP 混合） |
| **Java 代码改动** | 高。同 GoldenDB，必须引入 Elasticsearch |
| **风险点** | TiDB v5+ 有 `LIKE` 模糊搜索增强，但远不如 PG 全文搜索。需要引入外部搜索服务 |

### 2.4 TDSQL（腾讯云）

分两种兼容模式：

#### 2.4.1 TDSQL MySQL 模式

| 指标 | 详情 |
|------|------|
| **最低推荐版本** | TDSQL MySQL 5.7+ |
| **全文搜索** | ❌ 与 GoldenDB/TiDB 问题相同 |
| **结论** | 不推荐 |

#### 2.4.2 TDSQL PostgreSQL 模式（推荐）

| 指标 | 详情 |
|------|------|
| **数据库内核** | PostgreSQL 10/12/14 |
| **最低推荐版本** | **TDSQL PostgreSQL 版 v2.0+**（建议确认内核版本 ≥ 12） |
| **JSONB** | ✅ 完全兼容 |
| **全文搜索** | ✅ tsvector/tsquery/setweight 理论上继承 PostgreSQL 内核能力 |
| **GENERATED ALWAYS STORED** | ✅ PG 内核支持 |
| **GIN 索引** | ✅ 支持 |
| **Java 代码改动** | **低**。若 PG 内核兼容性完整，`PostgresFullText*Service` 可直接复用 |
| **需确认事项** | `to_tsvector('simple', ...)` 配置与标准 PG 一致性；GIN 索引在分布式下的性能表现 |

### 2.5 GaussDB（华为）

分两种兼容模式：

#### 2.5.1 GaussDB MySQL 兼容模式

| 指标 | 详情 |
|------|------|
| **最低推荐版本** | GaussDB 100/200 MySQL 兼容版 |
| **全文搜索** | ❌ 与 GoldenDB/TiDB 问题相同 |
| **结论** | 不推荐 |

#### 2.5.2 GaussDB PostgreSQL 兼容模式（推荐）

| 指标 | 详情 |
|------|------|
| **数据库内核** | PostgreSQL 内核（企业版基于 PG 9.4/12/14 定制） |
| **最低推荐版本** | **GaussDB(DWS) 3.0+** 或 **GaussDB 200 企业版 3.0+**（建议内核 ≥ PG 12） |
| **JSONB** | ✅ 完全兼容（华为在 PG 内核上深度定制） |
| **全文搜索** | ✅ tsvector/tsquery/setweight 理论上继承 PostgreSQL 内核能力 |
| **GENERATED ALWAYS STORED** | ✅ PG 内核支持 |
| **GIN 索引** | ✅ 支持 |
| **Java 代码改动** | **低**。若 PG 内核兼容性完整，`PostgresFullText*Service` 可直接复用 |
| **需确认事项** | `to_tsvector('simple', ...)` 配置与标准 PG 一致性；分布式模式下 GIN 索引性能；华为 PG 版生产案例较少 |
| **注意事项** | GaussDB 有多个产品线（FusionStorage GJD / GaussDB T / GaussDB(DWS) / GaussDB 200），需确认选用 **PostgreSQL 内核版本** 的产品 |

### 2.6 国产数据库替代方案汇总

| 方案 | 最低推荐版本 | 全文搜索 | JSONB | Java 改动 | 推荐度 |
|------|------------|---------|-------|---------|-------|
| GoldenDB + Elasticsearch | GoldenDB 6.0 + ES 8.x | ✅ ES | JSON 类型 | 高（需重写搜索服务） | ⭐⭐ |
| TiDB + Elasticsearch | TiDB 5.0 + ES 8.x | ✅ ES | JSON 类型 | 高（需重写搜索服务） | ⭐⭐ |
| **TDSQL PostgreSQL 版** | TDSQL-PG v2.0+（内核 ≥ PG 12） | ✅ PG FTS | ✅ JSONB | **低** | ⭐⭐⭐⭐ |
| **GaussDB PostgreSQL 版** | GaussDB 3.0+（内核 ≥ PG 12） | ✅ PG FTS | ✅ JSONB | **低** | ⭐⭐⭐⭐ |
| **标准 PostgreSQL** | PG 12+（建议 PG 14/15/16） | ✅ PG FTS | ✅ JSONB | **无** | ⭐⭐⭐⭐⭐ |

### 2.7 迁移建议

1. **首选：标准 PostgreSQL 12+**（或云托管版）
   - 版本要求最低，兼容性最完整，无额外风险

2. **次选：GaussDB PostgreSQL 模式 或 TDSQL PostgreSQL 模式**
   - 若 PG 内核兼容性完整，迁移成本最低
   - 迁移前必须在测试环境验证全文搜索功能和性能

3. **若必须用 MySQL 内核产品（GoldenDB/TiDB）**
   - 必须同步引入 Elasticsearch 8.x 作为全文搜索后端
   - `PostgresFullTextQueryService` → `ElasticsearchFullTextQueryService`
   - `PostgresFullTextIndexService` → `ElasticsearchFullTextIndexService`
   - 全文搜索从数据库内建能力变为独立搜索服务

---

## 3. 方案对比：TDSQL-PG / GaussDB-PG vs MySQL 8 + ES

### 3.1 方案一：TDSQL PostgreSQL 版 或 GaussDB PostgreSQL 版

> 沿用 PostgreSQL 内核，理论上无需修改 Java 代码和搜索逻辑。

#### 开发成本

| 维度 | 工作量 | 说明 |
|------|--------|------|
| **数据库迁移** | 低 | 无需修改 Flyway 迁移脚本（若 PG 内核 ≥ 12）。JSONB、tsvector、GIN 索引语法完全兼容。 |
| **Java 代码修改** | **理论上为零** | `PostgresFullTextQueryService` / `PostgresFullTextIndexService` 可直接复用 |
| **搜索逻辑** | 无 | 语义重排序（Semantic Rerank）、分词、权重排名全部保留 |
| **JSONB 字段映射** | 无 | Hibernate `columnDefinition = "jsonb"` 理论上兼容 |
| **测试用例** | 低 | 现有测试应可直接通过（仅需确认 TDSQL-PG / GaussDB-PG 环境） |
| **配置变更** | 低 | 仅修改数据库连接配置（Host/Port/JDBC URL） |

#### 运维成本

| 维度 | 成本 | 说明 |
|------|------|------|
| **数据库运维** | 中 | 需学习 TDSQL-PG 或 GaussDB 的运维体系（备份、扩容、故障恢复） |
| **监控告警** | 中 | 需对接内部监控平台（Prometheus / 华为/腾讯云监控） |
| **全链路追踪** | 低 | 现有 APM 工具（如链路追踪）通常兼容 |
| **版本升级** | 中 | 跟随云厂商版本，有版本锁定风险 |
| **技术支持** | 中 | 依赖云厂商文档和工单支持（不如开源社区活跃） |
| **分布式能力** | 高 | 自动分片 + 高可用，开箱即用 |

#### 潜在风险

| 风险 | 等级 | 应对 |
|------|------|------|
| PG 内核兼容性不及预期 | 中 | 必须在测试环境完整验证全文搜索功能 |
| GIN 索引在分布式下性能不达预期 | 中 | 需压测验证搜索 QPS |
| `to_tsvector('simple', ...)` 分词结果与标准 PG 不一致 | 低-中 | 需对比验证中文分词效果 |
| 云厂商 PG 版本长期不更新 | 低 | 定期评估版本路线图 |
| 生产案例少，问题排查困难 | 中 | 华为 PG 版生产案例远少于标准 PG |

---

### 3.2 方案二：MySQL 8 + Elasticsearch

> 放弃 PostgreSQL 全文搜索能力，引入独立的 Elasticsearch 搜索服务。

#### 开发成本

| 维度 | 工作量 | 说明 |
|------|--------|------|
| **数据库迁移** | 中 | JSONB → JSON 类型（5 个字段），查询语法需适配（`->>` / `JSON_EXTRACT`） |
| **Java 代码修改** | **高** | 核心搜索服务必须重写，详见下方 |
| **搜索逻辑** | 高 | 全文搜索从数据库迁移到 ES，需重新实现以下逻辑： |
| **分词器** | 高 | `SearchTextTokenizer`（中英文混合分词）需迁移到 ES Analyzer |
| **权重排名** | 高 | 标题 > 摘要 > 关键词 > 正文 的 `setweight` 权重需在 ES `boost` 中重新建模 |
| **语义重排序** | 高 | `SearchEmbeddingService` 语义向量相似度搜索需迁移到 ES 向量检索 |
| **JSONB 字段映射** | 中 | 4 个实体类的 `@Column(columnDefinition = "jsonb")` 需改为 JSON 或 TEXT |
| **测试用例** | 高 | 所有 `PostgresFullText*Test` 需重写为 `ElasticsearchFullText*Test` |
| **配置变更** | 高 | 新增 ES 连接配置、ES Index Mapping、Index Lifecycle Management |

#### Java 代码改动清单

以下是需要重写的文件：

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `PostgresFullTextQueryService.java` | **完全重写** | 约 360 行，含搜索 SQL 构建、排序、语义重排 |
| `PostgresFullTextIndexService.java` | **完全重写** | 约 120 行，索引写入逻辑 |
| `PostgresFullTextQueryServiceTest.java` | 重写 | 约 260 行，SQL 断言改为 ES 查询断言 |
| `PostgresFullTextIndexServiceTest.java` | 重写 | 索引写入测试 |
| `SkillSearchDocumentEntity.java` | 修改 | JSONB 字段类型映射调整 |
| `SkillVersion.java` | 修改 | JSONB 字段类型映射调整 |
| `IdentityBinding.java` | 修改 | JSONB 字段类型映射调整 |
| `ApiToken.java` | 修改 | JSONB 字段类型映射调整 |
| `SecurityAudit.java` | 修改 | JSONB 字段类型映射调整 |
| **新增** `ElasticsearchFullTextQueryService.java` | 新增 | ES 搜索实现 |
| **新增** `ElasticsearchFullTextIndexService.java` | 新增 | ES 索引写入 |
| **新增** `ElasticsearchConfig.java` | 新增 | ES 客户端配置 |
| **新增** `skill-search-index-template.json` | 新增 | ES Index Mapping（含分词器、权重配置） |

#### 运维成本

| 维度 | 成本 | 说明 |
|------|------|------|
| **数据库运维** | 低-中 | MySQL 8 成熟，团队通常熟悉 |
| **Elasticsearch 运维** | **高** | 需独立运维 ES 集群（Shard 分配、段合并、OOM 调优、冷热分离） |
| **监控告警** | 高 | ES 集群 + MySQL 双系统监控 |
| **全链路追踪** | 高 | ES 查询延迟需单独埋点监控 |
| **数据一致性** | 高 | MySQL 写成功后需同步 ES，任何失败都需补偿机制 |
| **版本升级** | 高 | MySQL 和 ES 两个系统独立升级 |
| **技术支持** | 低-中 | ES 有活跃社区，但国内企业级支持弱于云厂商 |

#### 潜在风险

| 风险 | 等级 | 应对 |
|------|------|------|
| 数据一致性问题（MySQL 与 ES 同步延迟/失败） | **高** | 引入 Canal / Debezium CDC 同步，或应用层双写 + 补偿 |
| ES 集群故障导致搜索不可用 | **高** | ES 集群高可用部署（3 节点 + 副本分片） |
| 搜索结果与 PG 版本不一致 | 中 | 需完整回归测试搜索相关性 |
| 中文分词效果不如 PG `simple` 分词器 | 中 | 需评估 IK 等中文分词器效果 |
| 引入额外运维复杂度 | 高 | ES 集群运维人才稀缺 |

---

### 3.3 综合成本对比

| 维度 | TDSQL-PG / GaussDB-PG | MySQL 8 + ES |
|------|----------------------|--------------|
| **数据库代码修改** | 低（理论上零修改） | 中（JSONB → JSON 映射） |
| **搜索代码修改** | **低** | **高（完全重写）** |
| **新增服务依赖** | 无 | Elasticsearch 8.x |
| **测试用例修改** | 低 | 高（全部重写） |
| **配置变更** | 低 | 高 |
| **开发工期估算** | **1-2 周** | **8-16 周** |
| **运维系统数** | 1 | 2（MySQL + ES） |
| **数据一致性风险** | 低 | **高** |
| **运维复杂度** | 中 | **高** |
| **搜索功能保真度** | ✅ 完全一致 | ⚠️ 需重新调优 |
| **中文搜索效果** | ✅ 已验证 | ⚠️ 需重新验证 |
| **团队学习成本** | 中（学习云厂商运维） | 高（学习 ES） |
| **长期维护成本** | 低-中 | **高** |
| **国产化合规** | ✅ 满足 | ⚠️ ES 需确认合规要求 |

### 3.4 结论与建议

```
开发成本：TDSQL-PG / GaussDB-PG  <<  MySQL 8 + ES
运维成本：TDSQL-PG / GaussDB-PG  <   MySQL 8 + ES
风险等级：TDSQL-PG / GaussDB-PG  <   MySQL 8 + ES
```

**如果目标就是国产化：**

- **优先选择 TDSQL PostgreSQL 版 或 GaussDB PostgreSQL 版**
  - 开发成本接近零，运维在可控范围内
  - 唯一需要做的是在测试环境验证 PG 内核兼容性

- **MySQL 8 + ES 仅在以下情况考虑**：
  - 团队已有成熟的 Elasticsearch 运维能力
  - 公司已强制要求 MySQL 作为主数据库
  - 愿意投入 2-4 个月的迁移工期

**最终建议：**

> 在做出数据库选型决定前，**必须先在测试环境验证 TDSQL-PG / GaussDB-PG 对标准 PG 语法的完整兼容性**，特别是 `to_tsvector('simple', ...)` 的分词结果和 GIN 索引的查询性能。如果兼容性验证通过，TDSQL-PG / GaussDB-PG 是成本最低、风险最小的方案。

---

## 4. Java 21 降级到 Java 17 分析

### 3.1 当前配置

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.3</version>
</parent>

<java.version>17</java.version>
<maven.compiler.target>17</maven.compiler.target>
```

### 3.2 Java 版本特性使用情况

| 特性 | 引入版本 | 使用情况 |
|------|---------|---------|
| Record | Java 16 | 大量使用（约 70+ 处），所有 DTO、事件、嵌套数据类 |
| Sealed Classes | Java 17 | `UpdateProfileResult` 接口 + 3 个 record 实现 |
| Pattern Matching instanceof | Java 16 | `instanceof String str` 模式匹配 |
| Switch Expressions | Java 14 | `switch` 表达式广泛使用 |
| Text Blocks | Java 15 | `AdminAuditLogAppService.java` 中的 SQL 拼接 |

### 3.3 Java 21 独有特性使用情况

经过代码扫描，**项目中没有任何 Java 21 独有特性**：

- 无 VirtualThread 使用（`Thread.ofVirtual`、`Executors.newVirtualThreadPerTaskExecutor` 未出现）
- 无 unnamed variables（`_` 下划线模式）
- 无 String Templates（预览功能）
- 无 unnamed classes and instance main methods
- 无 sequencial flow scoped values
- 无 scoped values

### 3.4 结论

**可以安全降级到 Java 17，无需修改任何代码。**

### 4.5 降级步骤

1. 修改 Maven 配置（如尚未修改）：

```xml
<java.version>17</java.version>
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

2. 同步更新基础设施配置（Dockerfile、CI/CD 等）中的 Java 版本引用

### 3.6 Spring Boot 版本兼容性

| Spring Boot 版本 | 最低 Java 版本 | 当前配置 |
|-----------------|---------------|----------|
| 3.2.x | Java 17 | 3.2.3 + Java 17 ✅ |

**无需调整 Spring Boot 版本。**

### 3.7 降级影响评估

| 方面 | 影响 |
|------|------|
| 编译运行 | 无影响，Java 17 完全兼容 |
| Spring Boot 3.2 | 支持 Java 17 |
| Hibernate 6.x | 支持 Java 17 |
| 性能 | 无差异（项目未使用虚拟线程） |
| 业务代码 | 无需修改 |
