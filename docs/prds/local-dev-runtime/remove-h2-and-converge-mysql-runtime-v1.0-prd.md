# 移除 H2 并收敛到 MySQL 主运行时 - 产品需求文档 (PRD) v1.0

> 状态检查（2026-05-05）：
> 本 PRD 对应的“正式主路径收敛”目标已大体完成，但“彻底移除所有 H2 残留”尚未完成。
> 当前应按以下口径理解：
> - `local-h2` 已不再属于当前正式源码运行入口
> - `h2-like` 已不再属于当前正式搜索主路径
> - 但仓库中仍存在 H2 残留依赖、少量 H2 测试、局部 profile/bean 条件与历史 SQL/文档引用，尚不能宣称“全仓彻底去 H2”

## 1. 背景

当前仓库已经明确将 `MySQL 8` 作为后续主流运行时方向，但代码、测试和文档中仍保留一条 `local-h2 + h2-like` 轻量路径。

这条 H2 路线带来以下问题：

- 测试与运行时口径分裂，容易出现 “H2 可过、MySQL 不一致”
- 搜索 provider、profile、测试 profile、文档入口需要同时维护多条路径
- 质量收口成本上升，尤其在要求 Java 生产代码最终达到 `100%` 行覆盖率时
- 运维与投产入口不够聚焦，影响仓库整体可交付性

因此，本轮需要将仓库主线进一步收敛到 `MySQL`，并让 H2 退出正式运行方案与主测试链路。

## 2. 目标

- 将 `MySQL 8` 确立为唯一正式支持的源码运行时数据库。
- 移除 `local-h2` 作为正式运行模式的入口与代码实现。
- 移除 `h2-like` 搜索 provider 及其运行时装配。
- 将关键测试基线迁移到 `MySQL + Testcontainers`。
- 收口文档，使仓库主入口、开发入口、运行时参考与测试口径保持一致。

## 2A. 当前状态快照（2026-05-05）

### 已完成部分

- `application-local-h2.yml` 已不再作为当前源码运行入口存在。
- 主 README 与当前 docs 入口已经把 `local-h2` / `h2-like` 归类为历史背景，而不是当前标准运行方案。
- `h2-like` 的旧实现类已不再作为当前正式 provider 保留在源码中。
- MySQL 主路径的覆盖率基线与后续补测计划已经围绕模块级 JaCoCo 重新建立。

### 未完成残留

- `LocalDevDataInitializer` 仍带有 `local-h2` profile 标记。
- `server/skillhub-app/src/main/resources/sql/data-local-h2.sql` 仍然存在。
- `server/skillhub-app/src/main/resources/sql/README.md` 仍在描述 `local-h2` 路径。
- `skillhub-search` 中仍有 `havingValue = "h2"` 的 bean 装配残留。
- Maven 仍保留 H2 依赖，且至少有一个 focused test 显式使用 H2。
- `AGENTS.md` 与部分 coverage/inventory 文档仍残留 H2 时代描述。

### 当前结论

- 可以说“正式主路径已收敛到 MySQL”
- 不能说“仓库已经彻底完成去 H2”

## 3. User Stories

### US-001: 退出 local-h2 运行 profile
**描述：** 作为维护者，我希望移除 `local-h2` 运行 profile，以便仓库源码运行时只保留 MySQL 主路径。

**Acceptance Criteria：**
- [ ] 删除或归档 `application-local-h2.yml`，不再把它作为源码运行入口。
- [ ] 本地源码启动文档不再推荐或引用 `local-h2`。
- [ ] 运行时入口文档明确 `local-mysql` 是唯一正式源码运行 profile。
- [ ] Typecheck passes
- [ ] Tests pass

### US-002: 移除 h2-like 搜索 provider
**描述：** 作为维护者，我希望移除 `h2-like` 搜索实现，以便搜索运行时只围绕 MySQL 主路径收敛。

**Acceptance Criteria：**
- [ ] 删除 `H2LikeSearchQueryService` 及其相关装配路径。
- [ ] 搜索 provider 切换测试不再要求覆盖 `h2-like`。
- [ ] 文档中不再把 `h2-like` 作为当前可选 provider 描述。
- [ ] Typecheck passes
- [ ] Tests pass

### US-003: 迁移测试默认数据库配置到 MySQL 主路径
**描述：** 作为维护者，我希望测试默认数据库口径不再隐式落到 H2，以便测试结果更贴近后续投产主路径。

**Acceptance Criteria：**
- [ ] `application-test.yml` 不再默认提供 H2 datasource 作为主测试入口。
- [ ] 测试环境中的运行时副作用（启动同步、定时任务等）对主测试链路有明确隔离策略。
- [ ] 主测试配置与 MySQL 运行时方向保持一致。
- [ ] Typecheck passes
- [ ] Tests pass

### US-004: 将持久化测试迁移到 MySQL Testcontainers
**描述：** 作为开发者，我希望关键持久化测试统一迁移到 MySQL Testcontainers，以便 JSON 字段、方言和 schema 行为按真实数据库验证。

**Acceptance Criteria：**
- [ ] 关键 JSON / JPA persistence tests 使用 MySQL Testcontainers + `migration-mysql`。
- [ ] H2 不再承担 MySQL 关键持久化行为验证责任。
- [ ] 测试结果可证明主路径 JSON 持久化与 schema 初始化正常。
- [ ] Typecheck passes
- [ ] Tests pass

### US-005: 修复并收敛 skillhub-app 测试基线到 MySQL 主路径
**描述：** 作为维护者，我希望 `skillhub-app` 模块的控制器和集成测试围绕 MySQL 主路径稳定运行，以便后续覆盖率冲刺建立在可信基线上。

**Acceptance Criteria：**
- [ ] `skillhub-app` 中与 MySQL 主路径相关的 `SpringBootTest` / integration tests 可以稳定运行。
- [ ] 已知的测试编译漂移、旧 API 引用、旧类名引用被收口到当前实现。
- [ ] `mvn -q -f server/pom.xml test` 至少不再因 H2 运行时残留而系统性失败。
- [ ] Typecheck passes
- [ ] Tests pass

### US-006: 移除 Maven 中的 H2 主依赖
**描述：** 作为维护者，我希望在测试迁移完成后移除 H2 Maven 依赖，以便仓库代码与构建层彻底脱离 H2。

**Acceptance Criteria：**
- [ ] `server/skillhub-app/pom.xml` 中不再依赖 H2。
- [ ] `server/skillhub-infra/pom.xml` 中不再依赖 H2。
- [ ] 全仓测试和打包在不依赖 H2 的情况下可运行。
- [ ] Typecheck passes
- [ ] Tests pass

### US-007: 清理和归档 H2 相关文档
**描述：** 作为维护者，我希望 H2 文档从主入口退出并被归档或删除，以便仓库文档只描述当前主流方案。

**Acceptance Criteria：**
- [ ] 主 README、开发入口文档、runtime 参考不再把 H2 作为正式运行路径。
- [ ] 仍有历史价值的 H2 材料被归档或明确标识为历史背景。
- [ ] 文档站入口不再误导读者认为 H2 是当前标准运行方案。
- [ ] Typecheck passes
- [ ] Tests pass

### US-008: 基于 MySQL 主路径重建覆盖率基线
**描述：** 作为交付负责人，我希望在移除 H2 主路径后重新生成 Java 单元测试覆盖率基线，以便后续 100% 行覆盖率冲刺围绕真实主路径展开。

**Acceptance Criteria：**
- [ ] 在 MySQL 主路径测试基线稳定后重新生成 JaCoCo 聚合报告。
- [ ] 输出当前未覆盖类清单与模块级覆盖率。
- [ ] 明确后续 100% 行覆盖率收口范围与优先级顺序。
- [ ] Typecheck passes
- [ ] Tests pass

## 4. Functional Requirements

- FR-1: 系统必须将 `local-mysql` 作为唯一正式源码运行 profile。
- FR-2: 系统必须移除 `h2-like` 搜索 provider 的正式运行时装配。
- FR-3: 系统必须将关键持久化测试迁移到 `MySQL + Testcontainers`。
- FR-4: 系统不得继续依赖 H2 作为投产主路径或主测试链路的默认数据库。
- FR-5: 系统必须收口测试配置，使主测试链路围绕 MySQL 运行时稳定执行。
- FR-6: 系统必须在文档层清楚说明 MySQL 是唯一正式主运行时。
- FR-7: 系统必须在完成 H2 收口后重新生成覆盖率基线，为 100% 行覆盖率专项做准备。

## 5. Non-Goals

- 不在本 PRD 中直接完成全仓 Java 生产代码 100% 行覆盖率。
- 不在本 PRD 中重构全部持久化架构。
- 不在本 PRD 中引入新的搜索后端或新的数据库选型。
- 不要求一次性删除所有历史设计材料中对 H2 的背景性描述。

## 6. Technical Considerations

- MySQL persistence tests 优先复用已有 `Testcontainers + migration-mysql + ddl-auto=none` 模式。
- `skillhub-app` 的测试运行时需避免 `startup synchronizer`、`@Scheduled` 任务等副作用污染测试上下文。
- 对旧测试引用的过时类名、过时 record 构造签名、旧 API 调用需要同步修正。
- 如果某些测试只能通过保留少量 H2 辅助配置运行，应在文档与命名上明确它不再属于主路径。

## 7. Success Metrics

- 仓库主入口文档中不再将 H2 作为正式运行方案。
- `local-h2` 和 `h2-like` 不再属于当前标准运行时与主测试链路。
- 关键 MySQL 持久化与集成测试通过率达到 100%。
- 能重新生成可信的 MySQL 主路径覆盖率基线。

补充说明（2026-05-05）：

- 前两条指标目前只在“正式主路径”口径上基本达成
- 若要宣称本 PRD 完整收尾，还需清掉代码、依赖、测试和项目指导中的 H2 残留

## 8. Open Questions

- 是否保留极少数纯轻量测试场景下的 H2 临时辅助能力，还是完全删除所有 H2 依赖？
- 100% 行覆盖率门禁是要求“全仓 Java 生产代码”还是“主运行时相关生产代码”先达标？


### US-009: 完成去 H2 后的 MySQL 主路径全量端到端验证
**描述：** 作为交付负责人，我希望在去 H2 和 MySQL 主路径收敛完成后先做一轮全量端到端验证，以便确认后续覆盖率专项建立在真实可运行的主路径之上。

**Acceptance Criteria：**
- [ ] 使用浏览器自动化验证登录、搜索、至少一条发布或治理相关主路径。
- [ ] `/actuator/health` 在验证过程中返回 `UP`。
- [ ] 全量 E2E 结果被记录到项目文档或执行说明中。
- [ ] Typecheck passes
- [ ] Tests pass

### US-010: 全量 E2E 通过后开始 100% 行覆盖率专项
**描述：** 作为交付负责人，我希望在 E2E 通过后再进入 Java 100% 行覆盖率专项，以便质量收口建立在稳定主路径之上。

**Acceptance Criteria：**
- [ ] 重新生成 MySQL 主路径覆盖率基线。
- [ ] 基于基线拆分核心服务类、配置/适配器/DTO 类的补测任务。
- [ ] 最终建立自动门禁，保证约定范围内 Java 生产代码行覆盖率达到 100%。
- [ ] Typecheck passes
- [ ] Tests pass
