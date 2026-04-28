# 数据库可插拔本地运行时 - 产品需求文档 (PRD)

## 需求说明

### 背景
- 当前项目默认围绕 PostgreSQL 构建，包含 PostgreSQL 方言、Flyway 迁移链、全文检索、JSONB 字段和少量原生 SQL。
- 团队希望先落一个 `local-h2` 轻量本地模式，同时为未来切换到 `MySQL` 预留空间。
- 目标不是一次性完成所有数据库适配，而是设计出一个可演进的“数据库可插拔”版本：
  - 当前先支持 `local`(PostgreSQL) 与 `local-h2`
  - 未来能够增加 `local-mysql`
  - 未来从 `H2 -> MySQL` 尽量只修改配置和依赖 jar

### 核心目标
1. 定义一个数据库可插拔的本地运行时架构。
2. 让数据库差异收敛在配置、装配和基础设施实现层，而不是扩散到业务逻辑。
3. 为未来引入 `local-mysql` 做好结构准备。
4. 保留当前 PostgreSQL 路径不变，保证可随时回切和对比。

### 非目标
- 本 PRD 不要求立刻完成 MySQL 支持。
- 本 PRD 不要求所有数据库模式行为完全一致。
- 本 PRD 不要求一步到位消除所有 PostgreSQL 痕迹。

## 产品目标

### 最终希望达到的状态
在数据库可插拔版本落地后：
1. 新增数据库模式时，主要通过：
   - Spring profile
   - 配置项
   - 依赖 jar
   完成接入。
2. 业务层（Controller / Service / Domain）不因数据库切换而修改。
3. 搜索、schema 初始化、session/限流等能力通过数据库/运行时实现切换。

### 当前阶段目标
1. 保留 `local` PostgreSQL 模式作为标准开发路径。
2. 新增 `local-h2` 轻量模式。
3. 设计时必须考虑未来增加 `local-mysql` 的路径，避免 H2 方案写死。

## 核心原则

### 原则 1：数据库切换只能影响基础设施层
数据库相关差异必须收敛在以下层：
1. datasource / dialect 配置
2. schema 初始化策略
3. 搜索实现
4. 原生 SQL 适配层
5. Session / 限流 / 认证节流等运行时状态实现

禁止将数据库判断逻辑散落在：
- Controller
- Domain Service
- Application Service 主流程

### 原则 2：双模式长期并存
必须长期保留：
1. `local` = PostgreSQL 标准模式
2. `local-h2` = H2 轻量模式

未来新增：
3. `local-mysql` = MySQL 本地模式

### 原则 3：搜索实现必须可插拔
搜索是数据库耦合最强的区域，必须最先抽象。

### 原则 4：迁移策略必须可切换
不能让所有数据库模式都强依赖同一套 PostgreSQL Flyway 迁移链。

## 目标架构

### 1. Profile 层
建议形成以下运行模式：

#### `local`
- PostgreSQL
- Redis
- 本地文件存储
- PostgreSQL 搜索
- Flyway 开启

#### `local-h2`
- H2 文件型
- 内存 session
- 内存限流/认证节流
- 本地文件存储
- H2 LIKE 搜索
- Flyway 关闭

#### `local-mysql`
- MySQL
- Redis 或内存运行时状态
- 本地文件存储
- MySQL 搜索实现
- MySQL 对应 schema 初始化策略

### 2. 可插拔能力分层

#### A. 数据源与 ORM
通过配置切换：
- `spring.datasource.*`
- `spring.jpa.database-platform`

要求：
- 实体层尽量不再写死 PostgreSQL 特有定义，如 `columnDefinition = "jsonb"`。

#### B. Schema 初始化
统一抽象为“按模式选择初始化策略”：
- PostgreSQL: Flyway
- H2: Hibernate create/update 或 H2 init
- MySQL: MySQL schema/init 或 MySQL migration

#### C. 搜索能力
统一走接口：
- `SearchQueryService`
- `SearchIndexService`
- `SearchRebuildService`

不同实现：
- PostgreSQL 实现
- H2 LIKE 实现
- MySQL 实现

#### D. 运行时状态
统一抽象：
- Session
- RateLimiter
- AuthFailureThrottle
- DeviceAuth state
- Redis fast-path / stream

通过 profile/条件装配切换：
- Redis 实现
- Memory 实现
- Disabled 实现

## 必须先做的结构收敛

### 1. 搜索可插拔化
当前搜索实现对 PostgreSQL 强绑定。

目标状态：
- `PostgresSearchQueryService`
- `H2LikeSearchQueryService`
- `MysqlSearchQueryService`

以及：
- `PostgresSearchIndexService`
- `NoopSearchIndexService`
- `MysqlSearchIndexService`

要求：
- `SkillSearchAppService` 不感知底层数据库。
- 搜索切换仅通过配置完成。

### 2. Schema 初始化可切换
目标状态：
- PostgreSQL 不变，继续走 Flyway
- H2 不跑 PG Flyway
- MySQL 不复用 PG migration

要求：
- 初始化策略由 profile / property 控制
- 不在业务代码中出现数据库判断

### 3. JSON 列定义去 PostgreSQL 硬编码
目标状态：
- 尽量不在实体层写死 `jsonb`
- JSON 类型差异下沉到 schema/init 层

### 4. PostgreSQL 专属原生 SQL 收口
目标状态：
- 原生 SQL 不散落在业务流程中
- 使用基础设施适配层管理数据库差异

例如：
- PostgreSQL `ON CONFLICT`
- MySQL `ON DUPLICATE KEY UPDATE`

### 5. Redis 依赖可替换
目标状态：
- Redis 不是本地轻量模式的强依赖
- session / 限流 / 节流 / device auth / scanner stream 可被 Memory/Disabled 实现替换

## 为什么要先做这些再谈 MySQL

### 现状问题
如果现在直接从 PostgreSQL 切 MySQL：
1. schema 会断
2. 搜索会断
3. JSON 列定义会断
4. 原生 SQL 会断

所以“未来只改配置和依赖”的前提不是 MySQL 本身，而是：
- 先把项目改造成数据库可插拔结构

### 设计目标
等结构收敛完成后，未来新增 `local-mysql` 时：
1. 加 MySQL driver
2. 增加 MySQL profile/config
3. 配置搜索实现为 mysql
4. 配置 schema 初始化为 mysql

不再修改：
- Controller
- Application Service
- Domain Service
- 主流程业务逻辑

## 对 `local-h2` 的约束

### `local-h2` 不是终点
`local-h2` 必须按“未来可切 MySQL”的方式设计，而不是写死成一次性补丁。

### `local-h2` 设计要求
1. 搜索走可切换接口
2. session / 限流走可切换实现
3. schema 初始化独立
4. PostgreSQL 路径不被污染

## 验收标准

### 架构验收
1. 数据库切换相关改动集中在配置与基础设施装配层。
2. 搜索实现可以按 profile / property 切换。
3. 运行时状态（session / 限流 / 节流）可按模式切换。
4. PostgreSQL 标准路径不受影响。

### 演进验收
1. `local-h2` 可以作为轻量模式落地。
2. 未来引入 `local-mysql` 时，不需要修改业务层代码。
3. 未来数据库切换主要发生在：
   - 配置
   - 依赖
   - 新增实现类

## 风险

### 风险 1：为了追求“未来切 MySQL 只改配置”，当前改造范围扩大
缓解：
- 先收敛最关键耦合点：搜索、schema、Redis 运行时状态

### 风险 2：过早抽象导致复杂度增加
缓解：
- 只抽象真实存在的数据库差异点
- 不做过度泛化

### 风险 3：`local-h2` 为了未来 MySQL 设计而拖慢当前落地
缓解：
- 以 `local-h2` 可运行为第一目标
- 同时保证结构方向不写死

## 执行计划

### Phase 1：`local-h2` 落地
1. 文件型 H2
2. 关闭 Flyway
3. 本地文件存储
4. 内存 session / 限流 / 节流
5. H2 LIKE 搜索

### Phase 2：数据库差异收敛
1. 搜索接口化与条件装配完善
2. Redis 依赖条件化
3. 原生 SQL 差异收口
4. JSON 字段定义收敛

### Phase 3：`local-mysql` 准备
1. MySQL profile
2. MySQL driver
3. MySQL schema 初始化
4. MySQL 搜索实现

## 决策记录

### 为什么不直接做 MySQL
- 当前项目对 PostgreSQL 的依赖太深，直接改 MySQL 改动面太大。

### 为什么先做数据库可插拔架构
- 只有先把数据库差异从业务层剥离，未来才可能做到“切库只动配置和依赖”。

### 为什么 `local-h2` 要按未来 MySQL 的方向设计
- 避免 `local-h2` 成为一次性补丁，后续再次推翻重做。

