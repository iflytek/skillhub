# MySQL 8 运行时迁移与本地文件索引路线图 - 产品需求文档 (PRD) v1.1

## 0. 当前状态快照（2026-05-04）

该 PRD 立项后的主链路迁移已经基本完成。

已完成：

- 默认运行态已切换到 `MySQL 8 + Redis + local-file-index`
- `mysql-like` 过渡搜索 provider 已落地，并保留为显式回退路径
- `local-file-index` 的 query / index / rebuild 主链路已落地
- PostgreSQL 默认配置、标准 Compose 入口和 PostgreSQL FTS 默认代码路径已退出当前主运行时
- 真实运行态与浏览器端到端验证已经覆盖认证、用户、命名空间、技能主链路、搜索和管理后台

剩余待办：

- 迁移范围 Java 单元测试补齐
- 最终 JaCoCo 覆盖率门禁收口

## 1. 介绍

本 PRD 立项时，仓库基线是：

- `local` PostgreSQL 标准运行模式
- `local-h2` 轻量本地联调模式
- 基于 PostgreSQL Full-Text 的主搜索实现

但实际目标已经收敛为：

1. 最终部署环境不再包含 PostgreSQL
2. 主业务数据库统一为 `MySQL 8`
3. 运行时状态统一收敛到 `Redis`
4. 搜索最终收敛为“本地文件索引服务”，而不是继续依赖 PostgreSQL Full-Text

因此，本 PRD 不再把 PostgreSQL 视为长期标准路径，而是将它定义为迁移期遗留实现；同时明确：

- `H2` 继续保留，但仅用于单元测试和少量轻量集成测试
- 运行时迁移分三步完成：
  1. `MySQL 8 + 本地缓存 + mysql-like`
  2. `MySQL 8 + Redis + mysql-like`
  3. `MySQL 8 + Redis + local-file-index`

## 2. 目标

- 让 PostgreSQL 从主运行时依赖中退出。
- 让 `MySQL 8` 成为唯一关系型主库。
- 在不替换现有业务层逻辑的前提下完成数据库迁移。
- 在迁移中间阶段提供 `mysql-like` 搜索兜底，保证主链路可用。
- 后续将搜索后端切换为 `local-file-index`，使搜索能力不再依赖关系库全文检索能力。
- 保留 `H2` 作为测试用数据库，不要求将现有单元测试全部切换到 MySQL。
- 阶段二、阶段三完成后，必须允许通过配置开关切回前一阶段路线；但 PostgreSQL 属于单向退出范围，不要求保留回切能力。

## 3. 目标状态

### 3.1 运行时三阶段

#### 阶段一：`MySQL 8 + 本地缓存 + mysql-like`

- 关系型数据库：MySQL 8
- 运行时状态：本地缓存 / memory 实现
- 搜索：`mysql-like`
- 用途：本地迁移与基础联调阶段

#### 阶段二：`MySQL 8 + Redis + mysql-like`

- 关系型数据库：MySQL 8
- 运行时状态：Redis
- 搜索：`mysql-like`
- 用途：运行时高可用收敛阶段

#### 阶段三：`MySQL 8 + Redis + local-file-index`

- 关系型数据库：MySQL 8
- 运行时状态：Redis
- 搜索：`local-file-index`
- 用途：最终目标态

### 3.2 Provider 维度

#### Relational DB Provider

- `h2`
- `mysql8`

> `postgres` 仅作为迁移期遗留实现，最终从主运行时中移除。

#### Runtime State Provider

- `memory`
- `redis`
- `disabled`

#### Search Provider

- `h2-like`
- `mysql-like`
- `local-file-index`

> `postgres-fts` 仅作为迁移期遗留实现，最终移除。

## 4. 用户故事

### US-001: 将主业务库切换到 MySQL 8
**描述：** 作为后端维护者，我希望主运行时完全基于 MySQL 8，以便与真实部署环境保持一致。

**Acceptance Criteria：**
- [x] 新增 MySQL 8 运行 profile 和对应配置
- [x] 应用可以在 MySQL 8 下成功启动
- [x] 登录、用户、命名空间、技能基础 CRUD 在 MySQL 8 下可用
- [x] 主运行时不再依赖 PostgreSQL driver

### US-002: 在迁移期间保留 mysql-like 搜索兜底
**描述：** 作为开发者，我希望在本地文件索引服务接入前仍有一个可工作的搜索兜底方案，以便先迁通主链路。

**Acceptance Criteria：**
- [x] 新增 `mysql-like` 搜索查询实现
- [x] 搜索接口在 MySQL 8 下返回 200 且可检索基础关键词
- [x] `SkillSearchAppService` 不感知底层搜索 provider
- [x] `mysql-like` 明确标记为过渡方案，而不是最终搜索形态

### US-003: Redis 统一接管运行时状态
**描述：** 作为运维人员，我希望 MySQL 运行时最终统一使用 Redis 处理会话和状态，以便支撑多实例部署。

**Acceptance Criteria：**
- [x] Session 可切换到 Redis
- [x] rate limit / auth throttle / UASS state store 可切换到 Redis
- [x] 相关装配集中在基础设施层，不扩散到业务层
- [x] 多实例场景下不会因节点切换丢失登录态

### US-004: 后续切换到本地文件索引服务
**描述：** 作为平台维护者，我希望最终搜索不再依赖关系库，以便彻底摆脱 PostgreSQL Full-Text 并减少对 MySQL LIKE 的长期依赖。

**Acceptance Criteria：**
- [x] 定义本地文件索引服务的文档模型
- [x] 定义索引目录、写入、删除、重建策略
- [x] 提供 `Query / Index / Rebuild` 三类实现
- [x] 搜索主路径可从 `mysql-like` 切换为 `local-file-index`

### US-005: 保留 H2 作为测试数据库
**描述：** 作为开发者，我希望现有单元测试继续沿用 H2，以便保持测试速度和维护成本可控。

**Acceptance Criteria：**
- [x] 单元测试默认仍可使用 H2
- [x] 不要求将全部单元测试替换为 MySQL
- [x] 对数据库方言敏感的能力补充 MySQL 专项验证
- [x] H2 仅作为测试辅助，不再被视为最终运行时目标的一部分

### US-006: 最终移除 PostgreSQL 相关运行时代码
**描述：** 作为维护者，我希望在迁移完成后彻底移除 PostgreSQL 相关运行时代码，以便减少维护成本和误用空间。

**Acceptance Criteria：**
- [x] PostgreSQL driver 从主运行依赖中移除
- [x] PostgreSQL profile 不再作为标准路径
- [x] PostgreSQL FTS 搜索实现退出主运行时装配
- [x] PostgreSQL 专属 schema / SQL / 文档完成清理或归档

### US-007: 完成功能后的全量端到端验证
**描述：** 作为交付负责人，我希望在所有功能开发完成后执行一轮全量端到端验证，以便确认整个 MySQL 运行时迁移链路在真实浏览器和真实后端联调下可用。

**Acceptance Criteria：**
- [x] 在所有功能开发完成后，执行覆盖主要功能点的全量端到端验证
- [x] 端到端验证覆盖登录、用户、命名空间、技能基础 CRUD、搜索、管理后台以及已启用的认证主链路
- [x] 端到端验证过程中允许继续修改后端代码，直到所有功能点都通过
- [x] 端到端验证完成后，形成明确的通过结果，而不是只验证部分关键页面

### US-008: 在全量 E2E 通过后补齐 Java 后台单元测试和 100% 行覆盖率
**描述：** 作为维护者，我希望在全量端到端验证通过之后，再集中补齐 Java 后台单元测试并把行覆盖率提升到 100%，以便在功能稳定后统一做质量收口。

**Acceptance Criteria：**
- [x] Java 后台单元测试补齐工作在全量端到端验证通过之后再开始
- [ ] 对本次迁移新增和重度修改的 Java 生产代码补充单元测试
- [ ] 最终目标是相关 Java 生产代码单元测试行覆盖率达到 100%
- [ ] 覆盖率检查进入最终收口阶段，而不是在主要功能链路尚未稳定时提前阻塞开发

## 5. 功能需求

- FR-1: 系统必须支持 `MySQL 8` 作为主业务数据库运行。
- FR-2: 系统必须支持 `mysql-like` 搜索 provider 作为迁移期搜索兜底方案。
- FR-3: 系统必须支持将 Session、限流、认证节流、UASS 状态存储切换到 Redis。
- FR-4: 系统必须支持在未接入 Redis 的阶段使用本地缓存完成基础运行。
- FR-5: 系统必须保留 H2 作为测试数据库，不要求将现有单元测试全面迁移到 MySQL。
- FR-6: 系统必须为后续 `local-file-index` 搜索后端预留稳定 SPI 边界。
- FR-7: 系统最终必须移除 PostgreSQL 作为主运行时前提。
- FR-8: 数据库切换不得要求修改 Controller、Application Service、Domain Service 主流程逻辑。
- FR-9: PostgreSQL 专属类型定义（例如 `jsonb`）必须从运行时关键路径中清理或下沉。
- FR-10: PostgreSQL 专属原生 SQL（例如 `ON CONFLICT`、FTS SQL）必须被替换、隔离或彻底移除。
- FR-11: 系统必须支持通过配置开关在 `mysql-like` 与 `local-file-index` 搜索后端之间切换。
- FR-12: 系统必须支持通过配置开关在 `memory` 与 `redis` 运行时状态实现之间切换。
- FR-13: PostgreSQL 完成移除后，不要求保留切换回 PostgreSQL 的能力。
- FR-14: 在所有功能开发完成后，系统必须执行一轮覆盖主要功能点的全量端到端验证。
- FR-15: 端到端验证阶段允许继续修正后端代码，直到所有功能点通过。
- FR-16: Java 后台单元测试补齐与 100% 行覆盖率收口必须排在全量端到端验证之后。

## 6. 非目标

- 本 PRD 不要求一步到位交付 `local-file-index` 全部能力。
- 本 PRD 不要求 H2 与 MySQL 行为完全一致。
- 本 PRD 不要求将所有单元测试迁移到 MySQL。
- 本 PRD 不要求在阶段一就实现多实例高可用。
- 本 PRD 不要求在阶段一就删除 H2。

## 7. 设计考虑

### 7.1 架构解耦原则

数据库迁移与搜索后端迁移是两条独立演进线：

- 数据库迁移：`PostgreSQL -> MySQL 8`
- 搜索迁移：`postgres-fts -> mysql-like -> local-file-index`

不得将两者绑成一次切换。

柔性切换仅适用于迁移后的运行路线：

- `memory <-> redis`
- `mysql-like <-> local-file-index`

不适用于 PostgreSQL 回切。

### 7.2 搜索 SPI 目标

当前和后续实现统一经过：

- `SearchQueryService`
- `SearchIndexService`
- `SearchRebuildService`

推荐目标实现名：

- `H2LikeSearchQueryService`
- `MysqlLikeQueryService`
- `LocalFileIndexQueryService`

- `NoopSearchIndexService`（迁移期可用）
- `LocalFileIndexService`

- `NoopSearchRebuildService`
- `LocalFileIndexRebuildService`

### 7.3 本地文件索引服务需要提前明确的点

即使阶段三再实现，也必须在阶段一/二的设计里明确以下问题：

1. 索引服务是嵌入 app 进程，还是独立本地进程
2. 索引目录位置与生命周期
3. 索引文档模型（skill 级还是 version 级）
4. 发布/归档/删除时的索引更新策略
5. rebuild 入口与幂等性策略
6. 索引损坏后的恢复机制

## 8. 技术考虑

### 8.1 MySQL 8

- 版本固定为 `MySQL 8`
- 字符集建议统一为 `utf8mb4`
- 排序规则需显式确认，避免与现有 PostgreSQL / H2 行为差异过大
- JSON 字段应改为可兼容 MySQL 8 的映射策略，不再依赖 `jsonb`

### 8.2 测试策略

- 单元测试与大部分轻量集成测试继续使用 H2
- 对数据库方言敏感的能力增加少量 MySQL 专项验证：
  - 原生 SQL
  - JSON 字段映射
  - upsert / 计数更新语义
  - schema 初始化 / migration
  - 搜索查询适配
- 推荐在配置层显式提供：
  - `skillhub.search.provider=mysql-like|local-file-index`
  - `skillhub.runtime.state-provider=memory|redis`
- PostgreSQL 不再作为长期可切换 provider 暴露给主运行时。

### 8.3 PostgreSQL 清理范围

需要逐步清理：

- PostgreSQL driver
- PostgreSQL profile
- PostgreSQL FTS SQL
- `jsonb` 列定义
- `ON CONFLICT`
- `search_vector / tsvector / GIN` 相关运行时依赖

## 9. 成功指标

- MySQL 8 下应用稳定启动
- 基础业务链路在 MySQL 8 下可用
- 阶段一不再要求 PostgreSQL 才能运行
- 阶段二在 Redis 下完成会话与状态统一
- 阶段三搜索不再依赖关系库 LIKE 或 PostgreSQL Full-Text
- PostgreSQL 从主运行时和部署说明中完全退出
- 阶段二、阶段三上线后，可通过配置回切到上一阶段路线，但不能回切到 PostgreSQL

## 10. 历史开放问题与当前结论

1. `local-file-index` 已按嵌入式 Lucene 落地，和应用进程同生命周期运行，不引入独立搜索 daemon。
2. 当前索引文档模型已经收敛到每 skill 一条 `SkillSearchDocument`，查询合同继续返回 `skillIds + pagination metadata`。
3. PostgreSQL 没有继续保留为标准运行 profile，只保留必要的历史材料和非当前入口说明。
4. MySQL 空库启动路径已收敛到专用 `migration-mysql` Flyway 目录，而不是依赖 Hibernate 自动建表。
5. 多实例部署下的 `local-file-index` 仍按“单节点本地可写目录”假设设计；如果后续需要跨节点共享或远程查询，应另开新 PRD，而不是继续在本 PRD 中追加。

## 11. 实施阶段

### Phase 1: `MySQL 8 + 本地缓存 + mysql-like`

1. 增加 MySQL 8 profile / driver / 配置
2. 去除 PostgreSQL 运行时强依赖
3. 增加 `mysql-like` 搜索查询实现
4. 保持本地缓存运行能力
5. 保留 H2 作为测试数据库

### Phase 2: `MySQL 8 + Redis + mysql-like`

1. Session 切 Redis
2. 限流 / 节流 / UASS state store 切 Redis
3. 完成多实例运行时状态验证
4. 保留 `memory` provider，支持配置回退

### Phase 3: `MySQL 8 + Redis + local-file-index`

1. 定义索引服务形态与文档模型
2. 实现 `local-file-index` 的 query/index/rebuild
3. 切换默认搜索后端
4. 保留 `mysql-like` 作为兜底实现和配置回退路径
5. 最终清理 PostgreSQL 相关遗留代码和文档

### Phase 4: 全量端到端验证

1. 在功能开发全部完成后执行全量端到端验证
2. 覆盖所有主要功能点，而不是只跑单点冒烟
3. 允许在 E2E 过程中继续修正后端代码
4. 直到所有功能点都稳定通过

### Phase 5: Java 后台单元测试补齐与覆盖率收口

1. 在全量 E2E 通过后补齐 Java 后台单元测试
2. 对迁移相关新增和重度修改类做覆盖率补强
3. 将相关 Java 生产代码行覆盖率提升到 100%
4. 最终再打开或收紧覆盖率门禁

## 12. 决策记录

### 为什么不一步切到本地文件索引

- 切数据库和切搜索后端同时进行，风险过高
- 先用 `mysql-like` 迁通主业务链路，再引入文件索引，分层更清晰
- 阶段推进后保留上一阶段的配置回退路线，可以降低部署切换和故障回退风险

### 为什么 H2 继续保留

- H2 适合单元测试和轻量集成测试
- 没必要为实现 MySQL 运行时目标而替换掉全部测试数据库

### 为什么 PostgreSQL 最终必须移除

- 实际部署环境不具备 PostgreSQL
- 长期保留 PostgreSQL 主路径会持续增加维护成本和误用风险
