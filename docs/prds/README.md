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

### 测试与质量支持资料
- [java-unit-line-coverage-inventory.md](./java-unit-line-coverage-inventory.md)

保留原因：
- inventory 文档用于固定当前低覆盖率范围，便于统一 feature 在实现过程中随时核对整改范围。

## 整理原则

- `docs/prds`：当前活跃 PRD 与直接支撑当前 feature 的质量资料。
- `design/implemented`：已经实现、可作为设计回顾或实现归档的文档。
