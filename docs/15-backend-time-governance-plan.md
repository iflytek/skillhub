# skillhub 后端日期时间治理计划

## 1. 当前结论

当前主系统已经基本完成 UTC 语义收口：

- 核心业务时间字段大多已迁到 `Instant`
- 核心事件时间列大多已迁到 `TIMESTAMPTZ`
- 服务层“当前时间”大多已统一走注入 `Clock`
- 普通 API 和后台 DTO 的绝对时间已基本统一输出 UTC ISO-8601

系统当前保留的已经不是大范围混用，而是少量兼容尾项。剩余风险主要集中在：

- 个别旧接口仍允许无时区字符串输入
- 新增代码如果重新引入 `LocalDateTime.now()`，可能把系统带回默认时区依赖
- 缺少跨时区自动化回归时，仍可能遗漏边界问题

## 2. 目标

治理目标不是“所有地方都只用一种类型”，而是统一时间语义：

- 绝对时间点：统一使用 UTC 语义，Java 使用 `Instant`
- 面向业务输入的本地时间：只有在需求明确要求“本地日历时间”时才允许保留 `LocalDateTime`
- 数据库存储绝对时间点时，统一使用 `TIMESTAMPTZ`
- 对外 API 返回绝对时间点时，统一输出 ISO-8601 UTC 字符串，例如 `2026-03-18T06:30:00Z`
- 不再把没有时区语义的 `LocalDateTime` 继续向领域层传播

这里要明确区分：

- i18n 解决的是语言、文案、本地化展示
- 时间统一到 UTC 解决的是跨时区一致性

## 3. 目标模型

建议把后端时间字段分成三类管理：

### 3.1 系统事件时间

适用字段：

- `createdAt`
- `updatedAt`
- `publishedAt`
- `submittedAt`
- `reviewedAt`
- `hiddenAt`
- `yankedAt`
- `lastUsedAt`
- `revokedAt`
- `readAt`
- `handledAt`
- `tokenExpiresAt`

约束：

- Java 类型统一为 `Instant`
- 数据库列统一为 `TIMESTAMPTZ`
- 读写都按 UTC 绝对时间处理

### 3.2 业务输入时间

适用场景：

- 用户手工输入一个“到某天某时截止”的字段
- 规则明确绑定某个业务时区，而不是系统时区

约束：

- 如果该时间代表真实绝对时刻，入口就应要求带时区或明确时区来源，然后在服务层立刻转换为 `Instant`
- 不允许把用户输入的裸 `yyyy-MM-ddTHH:mm:ss` 长期保存在核心领域模型中

### 3.3 纯日期字段

适用场景：

- 生日
- 账期
- 结算日
- 自然日统计

约束：

- 使用 `LocalDate`
- 不参与 UTC/时区转换

## 4. 现状问题

### 4.1 历史问题已基本清理

此前系统的主要问题包括：

- 领域层大量使用 `LocalDateTime`
- 服务层散落 `LocalDateTime.now()`
- 数据库 DDL 大量使用 `TIMESTAMP`
- 兼容层存在隐式 UTC 假设和冲突解释

当前这些问题在主链代码中已基本完成治理，保留它们主要是为了说明为什么迁移顺序必须先做基础设施，再做模型与数据库。

### 4.2 当前仍存在的实际问题

- `ApiTokenService` 仍兼容裸时间字符串输入
- 尚未建立静态约束来阻止未来重新引入 `LocalDateTime.now()`
- 尚未形成系统性的跨时区回归基线

## 5. 治理原则

- 先统一新增代码，再迁移存量代码
- 先统一领域模型，再迁移数据库，再收口 API
- 所有“当前时间”获取统一从 `Clock` 注入，禁止继续散落 `now()`
- 迁移期间优先保证 API 兼容，避免前端和 CLI 同时破坏
- 对外只暴露明确语义的时间格式，不暴露“无时区但又默认是 UTC”的灰色状态

## 6. 分阶段计划

### Phase 0：基线审计

产出：

- 全量时间字段清单
- `LocalDateTime` / `Instant` / `LocalDate` 使用清单
- `TIMESTAMP` / `TIMESTAMPTZ` 列清单
- API 请求与响应中的时间字段清单
- 兼容层中所有 epoch 转换点清单

当前状态：

- 已完成初版盘点
- 已同步到当前代码真实进展

### Phase 1：统一规范与基础设施

执行内容：

- 新增全局 UTC `Clock`
- 配置 Hibernate JDBC 时区为 UTC
- 配置 Jackson UTC 输出
- 建立“绝对时间用 `Instant`”规范

当前状态：

- 已完成

### Phase 2：代码层迁移到 `Instant`

执行内容：

- 实体字段改为 `Instant`
- `LocalDateTime.now()` 改为 `Instant.now(clock)`
- 比较逻辑统一为 `Instant`
- DTO 与服务同步迁移

当前状态：

- 主链已基本完成
- 生产代码中仅剩极少数兼容解析代码保留 `LocalDateTime`

### Phase 3：数据库迁移到 `TIMESTAMPTZ`

执行内容：

- 为核心表新增 Flyway migration
- 明确历史 `TIMESTAMP` 数据按 UTC 解释

当前状态：

- 主链核心事件时间列已基本完成
- 已落地 migration `V13` 到 `V23`

### Phase 4：API 契约收口

执行内容：

- 普通 JSON API 中所有绝对时间字段统一输出 UTC 字符串
- 禁止接口返回裸 `LocalDateTime.toString()`
- 逐步淘汰无时区输入

当前状态：

- 普通 API 与后台 DTO 已基本完成 UTC 输出收口
- 剩余兼容重点是旧接口对裸时间字符串输入的处理策略

### Phase 5：清理与强约束

执行内容：

- 清理遗留兼容时区假设
- 增加 ArchUnit 或静态扫描规则
- 增加跨时区测试，例如 `UTC` 与 `Asia/Shanghai`

当前状态：

- 尚未完成
- 这是下一阶段最有价值的工作

## 7. 重点技术决策

### 7.1 为什么用 `Clock` 而不是只用 `Instant.now()`

- `Instant` 解决“时间如何表达”
- `Clock` 解决“当前时间从哪里来”
- 推荐组合是 `Instant.now(clock)`

这使服务层可测试、可固定时间、可避免机器本地时区干扰。

### 7.2 是否统一引入 `OffsetDateTime`

本项目更适合以 `Instant` 作为核心绝对时间类型，原因是：

- 多数字段表达的是事件发生时刻
- 业务侧通常不需要保留原始 offset
- `Instant` 更能防止“看起来像本地时间”的误解

只有在必须保留调用方原始 offset 的场景下，才考虑 `OffsetDateTime`。

### 7.3 `expiresAt` 这类用户输入字段怎么处理

长期目标：

- API 约定输入为 RFC 3339 / ISO-8601 带时区时间
- 服务层解析后立即转换为 `Instant`

短期兼容：

- 旧接口若仍接受裸字符串，应在 controller 或 service 边界集中兜底
- 必须明确记录这是兼容逻辑，而不是长期契约

## 8. 风险与应对

| 风险 | 应对 |
|------|------|
| 历史 `TIMESTAMP` 数据真实语义不一致 | 先做抽样和数据画像，必要时分批迁移 |
| 前端或 CLI 已依赖不带时区的旧格式 | 保留短期兼容解析，同时明确废弃计划 |
| 新代码继续引入 `LocalDateTime.now()` | 加静态扫描和 review 规则阻断 |
| 缺少跨时区回归导致边界问题漏检 | 增加 `UTC` / `Asia/Shanghai` 双时区测试矩阵 |

## 9. 推荐后续顺序

1. 为 `LocalDateTime.now()` 和实体层 `LocalDateTime` 增加静态约束
2. 增加跨时区回归测试
3. 梳理并逐步淘汰裸时间字符串输入兼容
4. 对生产历史数据做一次抽样校验，确认所有 `TIMESTAMPTZ` 迁移都符合 UTC 解释假设
