# PRD 文档索引

本目录优先保留“仍处于需求/规划阶段”的 PRD。

已经完成实现并沉淀为设计/实现资料的文档，已收敛到：
- [design/implemented/README.md](../../design/implemented/README.md)

## 当前活跃 PRD

### 认证与质量联合交付
- [uass-session-auth-and-java-coverage-v1.0-prd.md](./uass-session-auth-and-java-coverage-v1.0-prd.md)
- [uass-session-auth-and-java-coverage-v1.0-task-breakdown.md](./uass-session-auth-and-java-coverage-v1.0-task-breakdown.md)

保留原因：
- 企业内部 UASS 认证尚未接入，仍属于明确的规划与实现前设计阶段。
- 团队计划在同一个 feature 中同时完成认证接入与 Java 质量门禁收敛，因此使用统一 PRD 管理功能范围和测试要求。
- 该方案同时覆盖私有 jar 适配、Session 建立、Redis / 本地缓存双模式、高可用边界，以及本次 feature 相关 Java 代码 100% 行覆盖率门禁。
- task breakdown 文档用于将统一 PRD 进一步拆解成可执行任务，便于在同一个 feature 分阶段实施。

## 历史迁移记录（保留参考，不视为当前活跃 PRD）

### 本地开发与运行时迁移
- [local-dev-runtime/database-pluggable-local-runtime-v1.0-prd.md](./local-dev-runtime/database-pluggable-local-runtime-v1.0-prd.md)
- [local-dev-runtime/mysql8-runtime-and-local-file-index-v1.1-prd.md](./local-dev-runtime/mysql8-runtime-and-local-file-index-v1.1-prd.md)
- [local-dev-runtime/mysql8-runtime-and-local-file-index-v1.1-task-breakdown.md](./local-dev-runtime/mysql8-runtime-and-local-file-index-v1.1-task-breakdown.md)
- [local-dev-runtime/postgres-runtime-exit-and-migration-coverage-gate-v1.0-prd.md](./local-dev-runtime/postgres-runtime-exit-and-migration-coverage-gate-v1.0-prd.md)
- [local-dev-runtime/postgres-runtime-exit-and-migration-coverage-gate-v1.0-task-breakdown.md](./local-dev-runtime/postgres-runtime-exit-and-migration-coverage-gate-v1.0-task-breakdown.md)

保留原因：
- 该文档讨论的是“数据库可插拔本地运行时”的长期演进方向。
- `MySQL 8 + Redis + local-file-index` 主运行态、PostgreSQL 默认路径清理、以及主要 E2E 验证已经落地。
- 当前剩余待办已经明显收敛到“迁移范围 Java 单元测试补齐与最终覆盖率门禁”。
- v1.1 与 PostgreSQL 退出相关文档保留在本目录，主要用于追溯迁移过程与质量门禁收尾，不应被理解为“当前主运行时尚未确定”。
- PostgreSQL 退出与迁移门禁文档用于跟踪最后的质量门禁和状态收尾，而不是重新定义主运行时方向。
- 可长期复用的实现规则已经沉淀到 [design/runtime/mysql-runtime-and-search-provider-migration.md](../../design/runtime/mysql-runtime-and-search-provider-migration.md)。

### 测试与质量支持资料
- [java-unit-line-coverage-inventory.md](./java-unit-line-coverage-inventory.md)

保留原因：
- inventory 文档用于固定当前低覆盖率范围，便于统一 feature 在实现过程中随时核对整改范围。

## 整理原则

- `docs/prds`：当前活跃 PRD 与少量仍有追溯价值的迁移记录。
- `design/implemented`：已经实现、可作为设计回顾或实现归档的文档。
- 与当前标准运行时无关的历史迁移材料，可以继续保留，但应在索引层清楚标注为“历史迁移记录”而不是“当前活跃需求”。
