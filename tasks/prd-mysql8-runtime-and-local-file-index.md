# PRD: MySQL 8 Runtime Migration And Local File Index

## 1. Introduction

SkillHub 当前主运行时仍然围绕 PostgreSQL 设计，而实际部署环境不具备 PostgreSQL。项目已经落地了 `local-h2` 轻量模式，但这只是过渡方案，不是最终运行时方向。

当前明确的目标是：

- 最终完全移除 PostgreSQL 相关运行时代码和依赖
- 主业务数据库统一为 `MySQL 8`
- 运行时状态最终统一收敛到 `Redis`
- 搜索最终收敛为嵌入式 `Apache Lucene` 本地文件索引
- `H2` 继续保留，但仅作为测试数据库，不要求替换为 MySQL

为了降低改造风险，迁移分三阶段推进：

1. `MySQL 8 + 本地缓存 + mysql-like`
2. `MySQL 8 + Redis + mysql-like`
3. `MySQL 8 + Redis + local-file-index`

其中 `mysql-like` 是过渡阶段的降级搜索实现，只保证搜索入口可用，不作为最终搜索形态。

## 2. Goals

- 让 PostgreSQL 从主运行时依赖中退出。
- 让 `MySQL 8` 成为唯一关系型主库。
- 在不修改主业务流程的前提下完成数据库迁移。
- 提供过渡性的 `mysql-like` 搜索方案，保证主链路先迁通。
- 后续将搜索切换到嵌入式 `Apache Lucene` 本地文件索引。
- 保留 `H2` 作为默认单元测试数据库。
- 将运行时状态从本地缓存过渡到 `Redis`。
- 阶段二、阶段三完成后，必须允许通过配置开关切回前一阶段路线；但 PostgreSQL 属于单向退出范围，不要求保留回切能力。

## 3. User Stories

### US-001: 以 MySQL 8 运行应用
**描述：** 作为后端维护者，我希望应用可以在 `MySQL 8` 下完整启动和运行，以便与真实部署环境一致。

**Acceptance Criteria：**
- [ ] 新增 `MySQL 8` 运行 profile 与对应 datasource 配置
- [ ] 应用在 `MySQL 8` 下可以成功启动
- [ ] 登录、用户、命名空间、技能基础 CRUD 在 `MySQL 8` 下可用
- [ ] 主运行时不再依赖 PostgreSQL driver

### US-002: 在迁移期保留 mysql-like 搜索兜底
**描述：** 作为开发者，我希望在接入 Lucene 前仍能在 `MySQL 8` 下使用基础搜索，以便先迁通主链路。

**Acceptance Criteria：**
- [ ] 新增 `mysql-like` 搜索查询实现
- [ ] 搜索接口在 `MySQL 8` 下返回 `200`
- [ ] 搜索支持 `title`、`summary`、`keywords`、`searchText` 的基础关键词匹配
- [ ] `SkillSearchAppService` 不感知底层搜索实现

### US-003: 将运行时状态统一切换到 Redis
**描述：** 作为运维人员，我希望在 MySQL 迁移完成后，运行时状态统一使用 `Redis`，以便支撑多实例部署。

**Acceptance Criteria：**
- [ ] Session 可切换到 `Redis`
- [ ] rate limit 可切换到 `Redis`
- [ ] auth failure throttle 可切换到 `Redis`
- [ ] UASS state store 可切换到 `Redis`
- [ ] 多实例场景下节点切换不会丢失登录态

### US-004: 最终切换到本地文件索引
**描述：** 作为平台维护者，我希望搜索最终不依赖数据库全文检索，而是依赖嵌入式 Lucene 本地文件索引，以便彻底摆脱 PostgreSQL Full-Text 和长期 MySQL LIKE 降级。

**Acceptance Criteria：**
- [ ] 搜索后端明确采用嵌入式 `Apache Lucene`
- [ ] 明确索引目录位置和生命周期
- [ ] 明确索引文档模型
- [ ] 提供 `query / index / rebuild` 三类 Lucene 实现
- [ ] 搜索主路径可从 `mysql-like` 切换为 `local-file-index`

### US-005: 保留 H2 作为测试数据库
**描述：** 作为开发者，我希望现有单元测试继续使用 `H2`，以便保持测试速度和维护成本可控。

**Acceptance Criteria：**
- [ ] 单元测试默认仍可使用 `H2`
- [ ] 不要求将现有单元测试全面迁移到 `MySQL`
- [ ] 对数据库方言敏感能力补充少量 `MySQL` 专项验证
- [ ] `H2` 明确只属于测试辅助，不属于最终运行时目标

### US-006: 最终移除 PostgreSQL 运行时代码
**描述：** 作为维护者，我希望迁移完成后彻底移除 PostgreSQL 相关运行时代码，以便减少维护成本和误用空间。

**Acceptance Criteria：**
- [ ] PostgreSQL driver 从主运行依赖中移除
- [ ] PostgreSQL profile 从主运行路径中移除
- [ ] PostgreSQL FTS 搜索实现退出主运行时装配
- [ ] PostgreSQL 专属 schema / SQL / 文档完成清理或归档

### US-007: 完成功能后的全量端到端验证
**描述：** 作为交付负责人，我希望在所有功能开发完成后执行一轮全量端到端验证，以便确认整个 MySQL 运行时迁移链路在真实浏览器和真实后端联调下可用。

**Acceptance Criteria：**
- [ ] 在所有功能开发完成后，执行覆盖主要功能点的全量端到端验证
- [ ] 端到端验证覆盖登录、用户、命名空间、技能基础 CRUD、搜索、管理后台以及已启用的认证主链路
- [ ] 端到端验证过程中允许继续修改后端代码，直到所有功能点都通过
- [ ] 端到端验证完成后，形成明确的通过结果，而不是只验证部分关键页面

### US-008: 在全量 E2E 通过后补齐 Java 后台单元测试和 100% 行覆盖率
**描述：** 作为维护者，我希望在全量端到端验证通过之后，再集中补齐 Java 后台单元测试并把行覆盖率提升到 100%，以便在功能稳定后统一做质量收口。

**Acceptance Criteria：**
- [ ] Java 后台单元测试补齐工作在全量端到端验证通过之后再开始
- [ ] 对本次迁移新增和重度修改的 Java 生产代码补充单元测试
- [ ] 最终目标是相关 Java 生产代码单元测试行覆盖率达到 100%
- [ ] 覆盖率检查进入最终收口阶段，而不是在主要功能链路尚未稳定时提前阻塞开发

## 4. Functional Requirements

- FR-1: 系统必须支持 `MySQL 8` 作为主业务数据库运行。
- FR-2: 系统必须提供 `mysql-like` 搜索 provider 作为迁移期搜索兜底方案。
- FR-3: 系统必须支持将 Session、限流、认证节流、UASS 状态存储切换到 `Redis`。
- FR-4: 系统必须支持在未接入 `Redis` 的阶段使用本地缓存完成基础运行。
- FR-5: 系统必须保留 `H2` 作为测试数据库，不要求将现有单元测试迁移到 `MySQL`。
- FR-6: 系统必须为后续 `local-file-index` 搜索后端预留稳定 SPI 边界。
- FR-7: 系统最终必须移除 PostgreSQL 作为主运行时前提。
- FR-8: 数据库切换不得要求修改 `Controller`、`Application Service`、`Domain Service` 主流程逻辑。
- FR-9: PostgreSQL 专属类型定义（例如 `jsonb`）必须从运行时关键路径中清理或下沉。
- FR-10: PostgreSQL 专属原生 SQL（例如 `ON CONFLICT`、FTS SQL）必须被替换、隔离或移除。
- FR-11: `local-file-index` 必须采用嵌入式 `Apache Lucene`，不单独引入索引服务进程。
- FR-12: `local-file-index` 必须使用可配置本地目录持久化索引文件。
- FR-13: 系统必须支持通过配置开关在 `mysql-like` 与 `local-file-index` 搜索后端之间切换。
- FR-14: 系统必须支持通过配置开关在 `memory` 与 `redis` 运行时状态实现之间切换。
- FR-15: PostgreSQL 移除完成后，不要求保留切换回 PostgreSQL 的能力。
- FR-16: 在所有功能开发完成后，系统必须执行一轮覆盖主要功能点的全量端到端验证。
- FR-17: 端到端验证阶段允许继续修正后端代码，直到所有功能点通过。
- FR-18: Java 后台单元测试补齐与 100% 行覆盖率收口必须排在全量端到端验证之后。

## 5. Non-Goals

- 不要求在第一阶段就交付 Lucene 文件索引能力。
- 不要求 `H2` 与 `MySQL 8` 行为完全一致。
- 不要求将全部单元测试迁移到 `MySQL`。
- 不要求在第一阶段实现多实例高可用。
- 不要求保留 PostgreSQL 作为长期兼容运行模式。

## 6. Design Considerations

- 数据库迁移与搜索后端迁移必须拆开推进：
  - 数据库迁移：`PostgreSQL -> MySQL 8`
  - 搜索迁移：`postgres-fts -> mysql-like -> local-file-index`
- 柔性切换只适用于迁移后的运行路线，不适用于 PostgreSQL 回切。
- `local-file-index` 明确采用嵌入式 `Apache Lucene`，索引跟随主应用进程运行，不拆分为独立本地服务。
- `H2` 保留为测试数据库，不再承担目标运行时职责。
- 现有 `local-h2` 已实现能力可继续保留，用于轻量测试/联调辅助，但不应再主导长期架构设计。

## 7. Technical Considerations

- `MySQL` 版本固定为 `MySQL 8`。
- 字符集建议统一使用 `utf8mb4`。
- `Flyway` 第一阶段继续保留，但迁移脚本改为 `MySQL` 路径，不再复用 PostgreSQL migration 作为运行时前提。
- `mysql-like` 推荐继续沿用当前搜索文档模型，先保证 `skillId` 级召回和后续 hydrate 兼容。
- `local-file-index` 建议文档字段至少包含：
  - `skillId`
  - `namespaceId`
  - `ownerId`
  - `title`
  - `summary`
  - `keywords`
  - `searchText`
  - `visibility`
  - `status`
  - `updatedAt`
  - `downloadCount`
  - `ratingAvg`
- H2 单元测试保留，但必须增加少量 `MySQL` 专项验证：
  - 原生 SQL 兼容性
  - JSON 字段映射
  - upsert / 计数更新语义
  - schema 初始化 / Flyway 行为
  - 搜索查询适配
- 推荐显式提供：
  - `skillhub.search.provider=mysql-like|local-file-index`
  - `skillhub.runtime.state-provider=memory|redis`
- PostgreSQL 不再作为长期可切换 provider 暴露给主运行时。

## 8. Success Metrics

- `MySQL 8` 下应用稳定启动。
- 基础业务链路在 `MySQL 8` 下可用。
- 阶段一不再要求 PostgreSQL 才能运行。
- 阶段二在 `Redis` 下完成会话和状态统一。
- 阶段三搜索不再依赖数据库 `LIKE` 或 PostgreSQL FTS。
- PostgreSQL 从主运行时和主部署说明中完全退出。
- 阶段二、阶段三上线后，可通过配置回切到上一阶段路线，但不能回切到 PostgreSQL。

## 9. Open Questions

- Lucene 索引文档粒度最终采用 `skill` 级还是 `version` 级？
- `local-file-index` 的默认索引目录放在 `${user.home}` 还是项目 `.dev` 目录？
- 第一阶段的 `MySQL 8` schema 初始化是否全部依赖 Flyway，还是允许少量 Hibernate / init SQL 过渡？
- 阶段一完成后是否立即删除 PostgreSQL profile，还是保留极短期迁移兼容窗口？
