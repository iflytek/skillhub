# MySQL 8 运行时迁移与本地文件索引路线图 - 实施任务清单

## 1. Purpose

本文档对应以下 PRD：

- [mysql8-runtime-and-local-file-index-v1.1-prd.md](./mysql8-runtime-and-local-file-index-v1.1-prd.md)

目标：

- 将 PostgreSQL 退出主运行时的工作拆成可执行任务
- 明确 `MySQL 8 + 本地缓存 + mysql-like -> MySQL 8 + Redis + mysql-like -> MySQL 8 + Redis + local-file-index` 的阶段顺序
- 保留 H2 作为测试数据库，不要求全面切换单元测试

## 2. Execution Rules

- 迁移阶段优先保证主业务链路可运行，而不是一开始就追求最终搜索质量。
- 数据库迁移与搜索后端迁移必须拆开推进，不允许一次同时替换。
- 单元测试默认继续使用 H2；只有数据库方言敏感能力才补 MySQL 专项验证。
- Controller / Application Service / Domain Service 主流程不得因数据库切换被迫引入分支判断。
- PostgreSQL 相关运行时代码最终必须被移除，而不是长期保留为平行标准路径。
- 柔性回退只适用于迁移后的运行路线，不适用于 PostgreSQL。

## 3. Delivery Phases

### Phase A: MySQL 8 + 本地缓存 + mysql-like

- T-001 到 T-006

### Phase B: MySQL 8 + Redis + mysql-like

- T-007 到 T-009

### Phase C: MySQL 8 + Redis + local-file-index

- T-010 到 T-014

### Phase D: 全量端到端验证

- T-015

### Phase E: Java 后台单元测试与覆盖率收口

- T-016

## 4. Task List

### T-001: 增加 MySQL 8 运行 profile
**Goal**
- 建立 `local-mysql` 或等价 profile，使应用可在 MySQL 8 下启动。

**Primary Files**
- `server/skillhub-app/pom.xml`
- `server/skillhub-app/src/main/resources/application-*.yml`

**Acceptance**
- [ ] 新增 MySQL 8 driver
- [ ] 新增 MySQL profile
- [ ] 应用在 MySQL 8 下成功启动
- [ ] 不要求此时接入 Redis
- [ ] `memory` 路线作为当前阶段默认或回退路径保留

### T-002: 清理 PostgreSQL 类型与 schema 假设
**Goal**
- 消除运行时对 `jsonb` 等 PostgreSQL 特有定义的强依赖。

**Primary Files**
- `server/skillhub-auth/src/main/java/**/*.java`
- `server/skillhub-domain/src/main/java/**/*.java`
- `server/skillhub-infra/src/main/java/**/*.java`

**Acceptance**
- [ ] 实体层不再写死 PostgreSQL 必须存在的列定义
- [ ] MySQL 8 下 schema 初始化不依赖 PostgreSQL 方言特性

### T-003: 收口 PostgreSQL 原生 SQL
**Goal**
- 替换或隔离 `ON CONFLICT`、FTS SQL 等 PostgreSQL 专属语法。

**Primary Files**
- `server/skillhub-infra/src/main/java/**/*.java`
- `server/skillhub-search/src/main/java/**/*.java`

**Acceptance**
- [ ] MySQL 8 主链路不再依赖 PostgreSQL 原生 SQL
- [ ] PostgreSQL 专属 SQL 仅允许存在于迁移期隔离实现中

### T-004: 新增 `mysql-like` 搜索查询实现
**Goal**
- 在本地文件索引服务接入前，提供 MySQL 下可运行的搜索兜底。

**Primary Files**
- `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/**/*`
- `server/skillhub-app/src/main/resources/application*.yml`

**Acceptance**
- [ ] 新增 `MysqlLikeQueryService`
- [ ] `SearchQueryService` 可按配置切换到 `mysql-like`
- [ ] 搜索接口在 MySQL 8 下返回 200
- [ ] 关键词在 `title/summary/keywords/search_text` 上具备基础命中能力
- [ ] 后续允许按配置切换到 `local-file-index`

### T-005: 过渡期索引写入与重建策略收敛
**Goal**
- 在文件索引服务落地前，统一 index/rebuild 侧接口边界，避免后续返工。

**Primary Files**
- `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/**/*`

**Acceptance**
- [ ] `SearchIndexService` / `SearchRebuildService` 的主依赖关系不再带有 PostgreSQL 语义泄漏
- [ ] 允许提供 `NoopSearchIndexService` / `NoopSearchRebuildService` 作为 MySQL 过渡态实现

### T-006: H2 测试保留与 MySQL 专项验证补齐
**Goal**
- 不替换现有 H2 单元测试，同时补足 MySQL 风险点验证。

**Acceptance**
- [ ] 单元测试默认仍可使用 H2
- [ ] 增加少量 MySQL 专项验证：
  - [ ] 原生 SQL
  - [ ] JSON 映射
  - [ ] 分页/排序
  - [ ] 搜索查询

### T-007: Session 切换到 Redis
**Goal**
- 在 MySQL 8 下完成 Redis Session 收敛。

**Acceptance**
- [ ] Session 可在 Redis 下工作
- [ ] 跨节点访问不丢登录态
- [ ] `memory` 实现不删除，可通过配置回切

### T-008: 运行时状态切换到 Redis
**Goal**
- 将限流、认证节流、UASS 状态等统一切换到 Redis。

**Acceptance**
- [ ] rate limit 使用 Redis
- [ ] auth throttle 使用 Redis
- [ ] UASS state store 使用 Redis
- [ ] 行为不依赖单机 JVM 内存
- [ ] 本地缓存实现仍保留为显式回退路径

### T-009: MySQL 8 + Redis 运行模式验收
**Goal**
- 验证过渡目标态可作为真实部署前形态。

**Acceptance**
- [ ] 登录、用户、命名空间、技能基础 CRUD 可用
- [ ] 搜索接口可用（`mysql-like`）
- [ ] UASS mock 仍然可用
- [ ] 可通过配置回切到 `MySQL 8 + 本地缓存 + mysql-like`

### T-010: 定义本地文件索引服务形态
**Goal**
- 明确最终搜索后端的部署和数据模型边界。

**Acceptance**
- [ ] 明确索引服务是嵌入式还是独立进程
- [ ] 明确索引目录位置
- [ ] 明确索引文档粒度（skill / version）
- [ ] 明确更新和重建策略
- [ ] 明确与 `mysql-like` 的配置切换和回退方式

### T-011: 实现 `local-file-index` 查询能力
**Goal**
- 提供 `LocalFileIndexQueryService`。

**Acceptance**
- [ ] 关键词查询可用
- [ ] 返回结果能与现有摘要 hydrate 链路对接
- [ ] 可按配置从 `mysql-like` 切换到 `local-file-index`

### T-012: 实现 `local-file-index` 写入能力
**Goal**
- 提供 `LocalFileIndexService` 或等价实现。

**Acceptance**
- [ ] 发布、归档、删除都能正确更新索引
- [ ] 支持 remove / batch rebuild

### T-013: 实现 `local-file-index` 重建与恢复
**Goal**
- 提供 `LocalFileIndexRebuildService` 和故障恢复策略。

**Acceptance**
- [ ] 支持全量 rebuild
- [ ] 支持按 namespace / skill rebuild
- [ ] 索引损坏后可恢复

### T-014: 切换默认搜索后端并清理 PostgreSQL 遗留
**Goal**
- 将 `local-file-index` 提升为默认搜索后端，并移除 PostgreSQL 主路径。

**Acceptance**
- [ ] 默认搜索 provider 切换到 `local-file-index`
- [ ] PostgreSQL driver 移除
- [ ] PostgreSQL profile 从主文档和主运行路径移除
- [ ] PostgreSQL FTS 相关运行时代码归档或删除
- [ ] `mysql-like` 仍保留为显式回退实现

### T-015: 完成全量端到端功能验证
**Goal**
- 在全部功能开发完成后，执行一轮覆盖主要功能点的全量端到端验证。

**Acceptance**
- [ ] 端到端验证覆盖登录、用户、命名空间、技能基础 CRUD、搜索、管理后台和启用中的认证主链路
- [ ] 验证过程中允许继续修改后端代码直到所有功能点通过
- [ ] 所有主要功能点在真实浏览器联调下通过，而不是只通过单点冒烟验证

### T-016: 在全量 E2E 通过后补齐 Java 后台单元测试并收口覆盖率
**Goal**
- 在功能链路稳定后，再集中补齐 Java 后台单元测试和 100% 行覆盖率。

**Acceptance**
- [ ] Java 后台单元测试补齐工作在 T-015 完成后开始
- [ ] 对迁移相关新增和重度修改的 Java 生产类补充单元测试
- [ ] 最终相关 Java 生产代码单元测试行覆盖率达到 100%
- [ ] 覆盖率检查在最终收口阶段通过
