# PostgreSQL 运行时退出与迁移覆盖率门禁修正 - 产品需求文档 (PRD) v1.0

## 0. 当前状态快照（2026-05-04）

这份 PRD 中，PostgreSQL 默认路径清理已经基本完成，当前剩余工作主要集中在覆盖率门禁收口。

已完成：

- 默认 `application.yml` 和标准本地 profile 已切换到 MySQL 目标态
- `docker-compose.yml`、`docker-compose.staging.yml`、`compose.release.yml` 已退出 PostgreSQL 标准依赖
- `SearchRuntimeProperties` 默认值已切到 `local-file-index`
- PostgreSQL FTS 主代码路径及对应测试已退出当前主实现
- 已完成真实 `MySQL 8 + Redis + local-file-index` 启动与搜索验收

仍待完成：

- 迁移范围覆盖率门禁清单与 `verify` 收口
- 覆盖率门禁完成后的最终复跑和状态闭环

## 1. 介绍

`MySQL 8 + Redis + local-file-index` 主链路已经可以在真实运行态下启动，并且登录、资料页、命名空间、技能发布/删除、搜索、退出登录等关键流程已经具备可复现的端到端验证证据。

但在本 PRD 立项时，仓库层面的收口工作还没有完成，当时至少存在两类遗留问题：

1. PostgreSQL 仍然出现在主运行时默认配置、标准本地 profile、staging/release Compose 以及默认搜索装配路径中。
2. 当前 Maven `verify` 阶段挂载的覆盖率门禁仍然是 UASS feature-scope 门禁，而不是本次 `mysql8-runtime-and-local-file-index` 迁移范围门禁。

这意味着仓库虽然已经具备“最终目标态可跑通”的能力，但还没有满足“PostgreSQL 从主运行时退出”和“迁移范围质量门禁最终收口”这两个交付标准。

本 PRD 用于定义最后一轮收尾工作，使仓库状态与既有迁移 PRD 的目标一致。

## 2. 目标

- 让 PostgreSQL 从主运行时默认配置、标准开发入口、staging/release 入口和主文档入口中退出。
- 让 `MySQL 8 + Redis + local-file-index` 成为仓库层面的标准运行路径，而不是仅存在于 `local-mysql` 特殊 profile 中。
- 清理或归档 PostgreSQL FTS 相关主运行时代码，避免默认装配或误用。
- 将迁移范围覆盖率门禁改为真实反映 `mysql8-runtime-and-local-file-index` 相关生产代码，而不是继续复用 UASS feature-scope 门禁。
- 以真实构建、真实接口和真实浏览器联调重新完成最终验收，并形成明确完成记录。

## 3. 立项时的遗留基线

本 PRD 立项时，可确认的未收口点包括：

- `application.yml` 仍默认使用 PostgreSQL datasource、`PostgreSQLDialect`、`skillhub.search.engine=postgres` 和 `postgres-fts` provider。
- `application-local.yml` 仍然将 PostgreSQL 作为标准本地 profile 的数据库。
- `compose.release.yml` 与 `docker-compose.staging.yml` 仍将 PostgreSQL 作为标准运行依赖。
- `SearchRuntimeProperties` 默认 provider 仍是 `postgres-fts`。
- `search/postgres` 包下的 FTS 查询、索引、重建实现仍保留在主代码路径中，并使用 `matchIfMissing = true` 的默认装配策略。
- 当前 `verify` 阶段调用的覆盖率脚本只校验 UASS 范围，而不是 MySQL 迁移范围。

## 4. 用户故事

### US-001: 切换仓库默认运行时到 MySQL 最终态
**描述：** 作为维护者，我希望仓库的默认运行时配置直接对准 `MySQL 8 + Redis + local-file-index`，以便标准开发、回归和发布入口不再误导到 PostgreSQL 路线。

**Acceptance Criteria：**
- [x] `application.yml` 不再默认指向 PostgreSQL datasource、PostgreSQL dialect 或 `postgres` 搜索引擎。
- [x] 标准本地 profile 不再以 PostgreSQL 作为默认数据库。
- [x] `SearchRuntimeProperties` 以及等价默认绑定不再以 `postgres-fts` 为默认值。
- [x] Typecheck/lint 通过。
- [x] Tests pass。

### US-002: 让标准交付入口不再依赖 PostgreSQL
**描述：** 作为维护者，我希望 staging/release 和本地依赖编排入口不再把 PostgreSQL 当成标准依赖，以便部署路径与最终目标态保持一致。

**Acceptance Criteria：**
- [x] `docker-compose*.yml`、`compose.release.yml` 或等价运行入口不再把 PostgreSQL 作为标准主库。
- [x] 发布或预发入口使用 MySQL 8 对应的连接配置与健康依赖。
- [x] 文档中的本地开发、staging 和 release 说明与新入口一致。
- [x] Tests pass。

### US-003: 清理 PostgreSQL FTS 主运行时代码路径
**描述：** 作为维护者，我希望 PostgreSQL FTS 相关实现退出主运行时代码路径，以便避免默认装配、误配置和维护分叉。

**Acceptance Criteria：**
- [x] PostgreSQL FTS 查询、索引和重建实现不再作为主运行时默认代码路径存在。
- [x] 任何仍需保留的 PostgreSQL 资料只存在于归档或历史文档中，而不是当前主配置入口。
- [x] 搜索运行时选择测试能够证明非 PostgreSQL 运行态不会再落回 PostgreSQL bean。
- [x] Tests pass。

### US-004: 修正迁移范围覆盖率门禁
**描述：** 作为维护者，我希望最终覆盖率门禁准确校验本次 `mysql8-runtime-and-local-file-index` 迁移范围，以便 `US-036` 的完成状态具备真实技术证据。

**Acceptance Criteria：**
- [ ] 明确迁移范围内需要纳入门禁的 Java 生产类清单。
- [ ] Maven `verify` 或等价入口执行的门禁脚本校验的是迁移范围，而不是 UASS feature-scope。
- [ ] fresh JaCoCo 报告中，迁移范围内生产类 `line_missed = 0`。
- [ ] 门禁失败时构建明确失败，且错误输出可定位到具体类。
- [ ] Tests pass。

### US-005: 完成最终回归与状态收口
**描述：** 作为交付负责人，我希望在 PostgreSQL 清理和覆盖率门禁修正完成后，重新执行最终回归，以便确认迁移真正结束。

**Acceptance Criteria：**
- [ ] 重新执行后端构建和迁移范围测试链路。
- [x] 重新执行前端 typecheck/lint。
- [x] 在真实 `MySQL 8 + Redis + local-file-index` 运行态下验证搜索和至少一条受保护业务链路。
- [ ] 产出明确的最终通过记录，可用于更新任务状态。
- [x] **如涉及 UI/运行态验证** 使用 agent-browser 或现有 Playwright 真实请求用例完成浏览器验证。

## 5. 功能需求

- FR-1: 系统必须让 `MySQL 8` 成为仓库主运行时默认数据库，而不是仅作为 `local-mysql` 特例存在。
- FR-2: 系统必须让 `Redis` 成为标准运行态的会话和状态存储默认路径。
- FR-3: 系统必须让 `local-file-index` 成为默认搜索 provider，`mysql-like` 仅保留为显式回退。
- FR-4: 系统必须移除 PostgreSQL 作为 `application.yml`、标准本地 profile、staging 和 release 入口的默认前提。
- FR-5: 系统必须避免在主运行时代码路径中继续默认装配 PostgreSQL FTS 查询、索引和重建实现。
- FR-6: 系统必须提供迁移范围覆盖率门禁，并在 `verify` 阶段自动执行。
- FR-7: 覆盖率门禁必须以 JaCoCo fresh report 为事实来源，并能定位具体未覆盖类。
- FR-8: 迁移完成后，文档入口必须反映 MySQL 最终态，而不是继续把 PostgreSQL 作为当前标准路径。

## 6. 非目标

- 不要求删除所有历史归档中提到 PostgreSQL 的设计材料。
- 不要求移除 H2 作为测试数据库。
- 不要求在本轮提升所有 Java 生产代码到全仓 100% 覆盖率；本轮只处理迁移范围门禁。
- 不要求新增新的搜索能力或改变现有 `local-file-index` 功能语义。
- 不要求重做已经通过的 MySQL 最终态业务实现。

## 7. 设计考虑

### 7.1 清理口径

- “清理 PostgreSQL”指的是退出主运行时、主入口和主文档，而不是抹掉所有历史痕迹。
- 历史背景、迁移过程和旧设计可以保留在归档或 implemented/design 材料中。
- 当前入口文档、当前默认配置、当前 Compose 和当前主代码路径不应再把 PostgreSQL 作为默认选项。

### 7.2 默认值策略

- `application.yml` 代表主默认值，不应继续保留 PostgreSQL 作为缺省路径。
- `application-local.yml` 若继续存在，应与新的标准本地运行态一致；若保留旧语义，则需重新命名并退出主入口。
- `SearchRuntimeProperties` 这类配置绑定默认值必须和 profile YAML 的默认语义一致，避免代码默认值与配置默认值漂移。

### 7.3 覆盖率门禁策略

- 门禁目标不是“全仓所有类 100%”，而是“本次迁移范围相关生产类 line_missed = 0”。
- 范围应由明确清单驱动，避免后续因为 unrelated class 波动导致误判。
- 脚本输出需要直接给出失败类名和 `line_missed`，方便回归定位。

## 8. 技术考虑

### 8.1 配置与运行入口

- 需要同步检查：
  - `server/skillhub-app/src/main/resources/application*.yml`
  - `compose.release.yml`
  - `docker-compose.yml`
  - `docker-compose.staging.yml`
  - `README.md`
  - `docs/dev-workflow.md`
  - `docs/09-deployment.md`

### 8.2 PostgreSQL 代码遗留

- 需要同步检查：
  - `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/**`
  - `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/SearchRuntimeProperties.java`
  - 搜索运行时装配相关测试与文档

### 8.3 验证链路

- 推荐最终验收至少包含：
  - `mvn -f server/pom.xml -pl skillhub-app -am verify -DskipITs`
  - `pnpm --dir web typecheck`
  - `pnpm --dir web lint`
  - 至少一条真实 `local-mysql` Playwright smoke
  - 一次真实 `actuator/health` 与搜索接口验收

## 9. 成功标准

- `application.yml`、标准本地 profile、staging/release 入口不再默认使用 PostgreSQL。
- PostgreSQL FTS 主运行时代码不再作为当前默认路径保留。
- migration-scope 覆盖率门禁在 `verify` 阶段自动执行并通过。
- 在 fresh `MySQL 8 + Redis + local-file-index` 运行态下，关键真实回归通过。
- 本轮完成后，可将 `mysql8-runtime-and-local-file-index` 迁移 PRD 中与 PostgreSQL 退出和最终门禁相关的遗留项视为收口完成。

## 10. Remaining Open Questions

- migration-scope 的最终清单是否以现有 `scripts/ralph/prd.json` 对应改动类为准，还是以专门维护的清单文件为准。
