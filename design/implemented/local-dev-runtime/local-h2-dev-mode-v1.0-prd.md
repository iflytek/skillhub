# Local H2 轻量开发模式 - 产品需求文档 (PRD)

> 状态更新（2026-05-04）：本文形成于 PostgreSQL 仍是标准本地运行时的阶段。当前仓库默认/标准运行时已切换为 `MySQL 8 + Redis + local-file-index`，`local-h2` 保留为轻量联调与测试模式。下文涉及“PostgreSQL 标准模式 / PG 对比”的表述应按历史背景理解，而不是当前默认配置。

## 需求说明

### 背景
- 当前本地开发默认依赖 PostgreSQL，能够完整支持 Flyway、PostgreSQL 全文检索、JSONB 与原生 SQL 语义。
- 某些开发环境不方便提供 PostgreSQL，但仍需要快速拉起前后端进行本地联调。
- 项目已经在测试配置中部分使用 H2，但当前能力仅用于测试场景，且通过 `flyway.enabled=false` 与 `ddl-auto=create` 绕开了生产 schema 路径，不能直接作为本地运行模式复用。
- 当前团队希望新增一个轻量本地模式，在不破坏现有 PostgreSQL 能力的前提下，提供一个可持久化到文件、重启不丢数据、支持基础搜索的 H2 模式。

### 目标
- 新增 `local-h2` 本地开发模式。
- 保留现有 `local` PostgreSQL 模式，不替换、不降级、不破坏。
- `local-h2` 使用文件型 H2，重启后数据可保留。
- `local-h2` 关闭 Flyway，改为 Hibernate 自动建表。
- `local-h2` 提供降级版技能搜索，使用 `LIKE`/`LOWER(...) LIKE ...` 替代 PostgreSQL 全文检索。
- 允许 `local-h2` 与 PostgreSQL 模式结果不完全一致，但必须可用于页面联调和基础 CRUD 验证。

### 非目标
- 不要求 `local-h2` 与 PostgreSQL 结果完全一致。
- 不要求在 H2 上支持 PostgreSQL 全文检索能力。
- 不要求 `local-h2` 验证生产 Flyway 迁移链。
- 不要求将所有 PostgreSQL 特性一比一映射到 H2。

## 用户场景

### 目标用户
- 本地开发前后端联调的开发者
- 不方便安装或接入 PostgreSQL 的开发环境使用者

### 典型场景
1. 开发者只想快速启动应用，验证页面、接口、登录、命名空间与技能基础流程。
2. 开发者需要重启应用而不丢失测试数据。
3. 开发者在调试基础搜索入口时，可以接受搜索质量下降，但不能完全没有搜索功能。
4. 开发者在需要验证真实 PostgreSQL 行为时，可随时切回 `local` 模式做对比。

## 功能范围

### `local-h2` 模式必须支持
1. 本地应用启动成功。
2. 文件型 H2 数据库存储，重启不丢数据。
3. 登录、本地账号、用户资料、命名空间、技能基础 CRUD 可用。
4. 技能搜索入口可用，支持基础关键词匹配。
5. 与 Redis 现有本地依赖兼容，尽量不改动其他基础设施依赖。
6. 与现有 `local` PostgreSQL 模式并存，开发者可通过 profile 或命令切换。
7. 在无 Redis 的前提下，允许本地联调企业跳转式登录闭环，例如 `mock://self` 模式下的 UASS 模拟登录页。

### `local-h2` 模式允许降级
1. 技能搜索相关性排序不再依赖 `ts_rank_cd`。
2. 中文分词、短词前缀、复杂多关键词检索精度下降。
3. 搜索索引维护、全文检索向量列、GIN/tsvector 能力关闭。
4. 少量与 PostgreSQL 强绑定的边角 SQL 可在 H2 模式中走替代逻辑。
5. `relevance` 在 H2 下仍是降级实现，但当前已保证与 PostgreSQL 在 `title/summary/keywords/search_text` 这些基础字段上的回退匹配范围一致。

### 明确不支持
1. PostgreSQL 全文检索完全等价行为。
2. Flyway 迁移链在 H2 上执行。
3. H2 模式下搜索结果与 PostgreSQL 线上结果完全一致。

## 关键设计原则

### 原则 1：双模式并存
- 保留现有 `local` PostgreSQL 模式作为标准开发模式。
- 新增 `local-h2` 作为轻量开发模式。
- 所有 H2 适配不得破坏现有 PostgreSQL 路径。

### 原则 2：搜索实现可切换
- PostgreSQL 搜索实现继续保留。
- 新增 H2 降级搜索实现。
- 搜索实现必须通过 profile / 条件注入切换，禁止在一个实现中塞满数据库判断分支。

### 原则 3：数据可持久化
- `local-h2` 必须使用文件库，不可使用纯内存数据库。
- 数据文件位置需要可配置，并提供合理默认值。

### 原则 4：最小改造
- 优先通过 profile 配置、条件装配、替代 bean 解决问题。
- 尽量避免大规模改动领域模型、控制器、服务编排。

## 技术方案

### 1. 配置层方案

#### 新增 profile
- 新增 `application-local-h2.yml`

#### 预期配置
- datasource:
  - H2 JDBC URL 使用 `MODE=PostgreSQL`
  - 使用文件型数据库，例如 `${user.home}/.skillhub/local-h2/skillhub`
- Hibernate:
  - `H2Dialect`
  - `ddl-auto=create` 或 `update`
- Flyway:
  - `enabled=false`
- 搜索:
  - 新增配置项或 profile 条件，切换到 H2 搜索实现

#### 持久化文件要求
- 默认文件路径建议放在用户目录或项目 `.dev` 目录下
- 必须避免纯 `mem:` 模式
- 需要文档说明如何清空 H2 本地数据

### 2. 搜索层方案

#### PostgreSQL 模式
- 保持现有搜索实现不动：
  - `PostgresFullTextQueryService`
  - `PostgresFullTextIndexService`
  - `PostgresSearchRebuildService`
  - 事件监听与索引刷新逻辑

#### H2 模式
- 新增 H2 降级搜索实现，核心策略：
  - `LOWER(title) LIKE :keyword`
  - 必要时增加 `summary`、`slug` 搜索
  - 排序优先复用现有时间、热度排序规则
  - 不使用 `tsvector / to_tsquery / ts_rank_cd`

#### 预期边界
- 单关键词命中可用
- 多关键词可能拆词后逐项 `LIKE`，但不保证与 PG 一致
- 结果相关性不作为目标

### 3. Schema 方案

#### PostgreSQL 模式
- 继续使用现有 Flyway 迁移

#### H2 模式
- 不跑 Flyway
- 依赖 Hibernate 自动建表
- 如有必要，补充 H2 初始化 SQL，例如：
  - 自定义 `JSONB` 域
  - 少量兼容对象

### 4. 原生 SQL / 仓储兼容
- 识别 H2 不兼容的 PostgreSQL 原生 SQL：
  - `ON CONFLICT`
  - PG 专属 cast / index / FTS
- 对于 H2 模式命中的仓储：
  - 优先用 JPA/Java 逻辑替代
  - 或新增 H2 条件实现

## 当前实现增补（2026-05-02）

- `application-local-h2.yml` 当前已内置 `skillhub.auth.uass` 模板，默认可通过 `base-url=mock://self` 走本地模拟登录闭环。
- `skillhub.auth.uass.mock-login-base-url` 当前支持把第三方模拟页独立跑在 `http://localhost:3001`，便于前后端跳转联调。
- H2 搜索当前除了修复 `relevance` 计数查询异常外，也补齐了 `title/summary/keywords/searchText` 的统一回退匹配，避免本地模式与 PostgreSQL 在基础关键词场景下差异过大。
- 搜索接口当前在控制器层统一收紧了分页边界：`size <= 100`、`page <= 10000`，本地模式与 PostgreSQL 模式保持一致。

## 影响范围

### 预计需要修改的区域
1. Spring profile 配置
2. 搜索服务装配与实现
3. 少量原生 SQL / repository
4. 启动文档与开发命令

### 明确不希望修改的区域
1. 现有 `local` PostgreSQL 主路径
2. 现有生产/部署配置
3. 领域模型主业务逻辑

## 回切与对比方案

### 切换要求
- 开发者必须能显式选择：
  - `SPRING_PROFILES_ACTIVE=local`
  - `SPRING_PROFILES_ACTIVE=local-h2`

### 对比要求
- 同一套前端页面、同一组 API，在两种模式下都能启动
- 对比重点：
  1. 基础 CRUD 是否一致
  2. 搜索入口是否可用
  3. 搜索质量是否存在预期中的降级

### 风险控制
- H2 实现必须通过条件 bean 装配，不允许污染 PostgreSQL 逻辑
- PostgreSQL 搜索实现与 H2 搜索实现需要边界清晰

## 验收标准

### 功能验收
1. `local-h2` 模式下应用可启动。
2. 关闭 Flyway 后，H2 可完成自动建表并正常运行。
3. 重启应用后，H2 数据文件中的数据仍然存在。
4. 登录、命名空间、技能基础 CRUD 可正常使用。
5. 技能搜索入口在 H2 模式下可用，能返回结果。
6. 切回 `local` PostgreSQL 模式后，现有功能不受影响。

### 技术验收
1. PostgreSQL 模式搜索实现保持不变。
2. H2 模式不再依赖 PG FTS SQL。
3. H2 模式配置独立，不影响现有 `local`。
4. 文档中明确说明两种模式的用途与差异。

## 风险评估

### 风险 1：H2 自动建表与 PG schema 不一致
- 影响：某些表结构在 H2 可跑，在 PG 不一致
- 缓解：明确 `local-h2` 仅用于轻量联调，不作为 schema 验证模式

### 风险 2：搜索降级导致体验偏差
- 影响：搜索命中、排序和线上不一致
- 缓解：文档明确说明 H2 搜索仅保证“可搜”，不保证“高质量搜”

### 风险 3：个别原生 SQL 仍然卡 H2
- 影响：某些功能启动后运行时报错
- 缓解：按真实报错逐步替换为 H2 条件实现

## 执行计划

### Phase 1: 配置与启动
1. 新增 `application-local-h2.yml`
2. 配置文件型 H2 datasource
3. 关闭 Flyway
4. 验证基础启动

### Phase 2: 搜索降级
1. 提取搜索接口装配点
2. 新增 H2 LIKE 搜索实现
3. 在 `local-h2` 下切换到 H2 搜索实现
4. 验证搜索入口可用

### Phase 3: 持久化与文档
1. 明确 H2 数据文件目录
2. 增加清理/重建本地 H2 数据说明
3. 增加双模式切换说明

### Phase 4: PG/H2 对比验证
1. 在 `local` 下验证现有路径不受影响
2. 在 `local-h2` 下验证基础联调路径
3. 输出已知差异清单

## 决策记录

### 为什么不直接用 H2 替代 PostgreSQL
- 当前项目深度依赖 PostgreSQL 全文检索与 Flyway 迁移链
- 完全替代会导致大量搜索与 schema 行为偏差

### 为什么仍然选择引入 `local-h2`
- 当前目标是“轻量启动”和“联调效率”，而不是“生产等价”
- 在接受搜索降级与关闭 Flyway 的前提下，H2 是成本最低的轻量方案
