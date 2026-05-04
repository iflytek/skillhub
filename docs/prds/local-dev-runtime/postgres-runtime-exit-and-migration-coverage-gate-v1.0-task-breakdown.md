# PostgreSQL 运行时退出与迁移覆盖率门禁修正 - 实施任务清单

## 0. 当前状态快照（2026-05-04）

当前拆解项的状态如下：

- `Phase A` 已完成
- `Phase B` 已完成
- `Phase C` 未完成
- `Phase D` 有前置验证证据，但需要在 `Phase C` 完成后做最终复跑闭环

## 1. Purpose

本文档对应以下 PRD：

- [postgres-runtime-exit-and-migration-coverage-gate-v1.0-prd.md](./postgres-runtime-exit-and-migration-coverage-gate-v1.0-prd.md)

目标：

- 完成 `mysql8-runtime-and-local-file-index` 路线中尚未收口的 PostgreSQL 默认路径清理。
- 将当前错误对焦到 UASS 的覆盖率门禁修正为迁移范围门禁。
- 通过最终构建和真实运行态验证，为任务状态更新提供可复核证据。

## 2. Execution Rules

- 不允许只改 `prd.json` 状态位，不补真实技术证据。
- 默认值清理必须覆盖代码、配置、Compose、README 和开发文档，而不是只改单一 profile。
- PostgreSQL 历史材料可以归档，但不能继续出现在当前主入口文档和主运行时默认值中。
- 覆盖率门禁必须在 fresh report 上验证，不允许沿用旧 report 或手工口头说明。
- 保持 `H2` 测试路径存在，不在本轮扩大成全仓数据库切换重构。

## 3. Delivery Phases

### Phase A: 默认运行态与文档入口清理

- T-001 到 T-003
- 当前状态：已完成

### Phase B: PostgreSQL 主代码路径退出

- T-004 到 T-005
- 当前状态：已完成

### Phase C: 迁移范围覆盖率门禁修正

- T-006 到 T-008
- 当前状态：未完成

### Phase D: 最终回归与状态收口

- T-009
- 当前状态：待 `Phase C` 完成后复跑收口

## 4. Task List

### T-001: 定义最终主运行态默认值
**Goal**
- 明确仓库当前标准运行态应以 `MySQL 8 + Redis + local-file-index` 为主，而不是 PostgreSQL。

**Primary Files**
- `server/skillhub-app/src/main/resources/application.yml`
- `server/skillhub-app/src/main/resources/application-local.yml`
- `server/skillhub-app/src/main/resources/application-local-mysql.yml`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/SearchRuntimeProperties.java`

**Acceptance**
- [x] `application.yml` 不再默认使用 `jdbc:postgresql://...`
- [x] `application.yml` 不再默认使用 `PostgreSQLDialect`
- [x] `application.yml` 默认搜索引擎/provider 对准 MySQL 最终态
- [x] `SearchRuntimeProperties` 默认值与 profile 语义一致
- [x] 新增或更新配置绑定测试

### T-002: 清理标准本地与开发入口中的 PostgreSQL 预设
**Goal**
- 让仓库标准开发入口不再默认落到 PostgreSQL。

**Primary Files**
- `docker-compose.yml`
- `docker-compose.staging.yml`
- `docs/dev-workflow.md`
- `README.md`

**Acceptance**
- [x] 本地依赖编排不再把 PostgreSQL 作为标准前提
- [x] staging 运行配置不再默认连 PostgreSQL
- [x] README 和开发工作流说明与新默认值一致
- [x] 如保留兼容模式，文档中明确标为历史/兼容，不作为推荐默认值

### T-003: 清理 release/deployment 入口中的 PostgreSQL 预设
**Goal**
- 让 release 运行入口与目标态保持一致。

**Primary Files**
- `compose.release.yml`
- `docs/09-deployment.md`
- `.env.release.example` 或等价 release 模板文件

**Acceptance**
- [x] release Compose 不再默认使用 PostgreSQL 容器与 PostgreSQL JDBC URL
- [x] release 文档不再将 PostgreSQL 描述为当前标准主库
- [x] 健康依赖和环境变量说明与 MySQL 最终态一致

### T-004: 退出 PostgreSQL 搜索主运行时代码路径
**Goal**
- 让 `search/postgres` 不再作为主运行时默认实现存在。

**Primary Files**
- `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/**`
- `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQueryService.java`
- `server/skillhub-search/src/test/java/com/iflytek/skillhub/search/SearchRuntimeSelectionTest.java`
- `docs/04-search-architecture.md`

**Acceptance**
- [x] PostgreSQL 搜索 bean 不再通过 `matchIfMissing = true` 成为默认装配
- [x] PostgreSQL-only 搜索实现被删除、归档，或至少退出主默认路径
- [x] 搜索运行时选择测试覆盖新的默认装配行为
- [x] 主架构文档不再把 PostgreSQL FTS 写成当前默认实现

### T-005: 清理 PostgreSQL 相关 SQL/文档遗留入口
**Goal**
- 收敛仍在“当前入口”里的 PostgreSQL SQL 和说明。

**Primary Files**
- `server/skillhub-app/src/main/resources/sql/README.md`
- `docs/04-search-architecture.md`
- `docs/README.md`
- `docs/prds/README.md`

**Acceptance**
- [x] 当前 SQL 入口文档不再把 PostgreSQL reset 脚本当成主路径
- [x] PostgreSQL 相关说明移动到 archive/implemented 或明确标识为历史材料
- [x] 当前索引文档只保留仍需推进的文档和仍有效的入口

### T-006: 定义迁移范围覆盖率清单
**Goal**
- 明确这次门禁到底校验哪些类。

**Primary Files**
- `scripts/check-feature-jacoco.sh` 或新脚本
- `docs/prds/java-unit-line-coverage-inventory.md`
- 新增的 migration-scope 清单文件（如需要）

**Acceptance**
- [ ] 输出迁移范围生产类清单
- [ ] 清单覆盖 app/auth/domain/infra/search 中与 MySQL runtime、Redis runtime-state、local-file-index 相关类
- [ ] 清单与当前遗留 PRD 目标一致，而不是沿用 UASS feature-scope

### T-007: 将 Maven verify 门禁切换到迁移范围
**Goal**
- 让 `verify` 真正代表 `US-036` 的验收口径。

**Primary Files**
- `server/skillhub-app/pom.xml`
- `scripts/check-feature-jacoco.sh` 或替代脚本

**Acceptance**
- [ ] `verify` 执行的脚本校验迁移范围而不是 UASS 范围
- [ ] fresh aggregate JaCoCo 报告作为唯一事实来源
- [ ] 失败输出包含类名、`line_missed` 和可定位信息
- [ ] 成功路径在本地可稳定复现

### T-008: 补齐迁移范围残余未覆盖分支
**Goal**
- 把迁移范围内仍有 `line_missed > 0` 的类补到归零。

**Primary Files**
- `server/skillhub-app/src/test/java/**`
- `server/skillhub-auth/src/test/java/**`
- `server/skillhub-search/src/test/java/**`
- `server/skillhub-infra/src/test/java/**`

**Acceptance**
- [ ] fresh JaCoCo 报告中，迁移范围生产类 `line_missed = 0`
- [ ] 配置类、runtime selection、Redis wiring、search provider 切换、local-file-index 分支都有显式测试
- [ ] 不通过删除生产逻辑来规避覆盖率缺口

### T-009: 完成最终回归并更新完成记录
**Goal**
- 用真实验证证据完成这轮迁移收口。

**Primary Files**
- `scripts/ralph/prd.json`
- `scripts/ralph/progress.txt`
- 必要的 README / docs 状态说明

**Acceptance**
- [ ] 执行 `mvn -f server/pom.xml -pl skillhub-app -am verify -DskipITs`
- [x] 执行 `pnpm --dir web typecheck`
- [x] 执行 `pnpm --dir web lint`
- [x] 执行至少一条真实 `local-mysql` Playwright smoke
- [x] 真实 `actuator/health` 与搜索接口验收通过
- [ ] 仅在以上证据具备后更新状态文档

## 5. Suggested Command Checklist

```bash
# backend verify
mvn -f server/pom.xml -pl skillhub-app -am verify -DskipITs

# frontend quality
pnpm --dir web typecheck
pnpm --dir web lint

# focused E2E
PLAYWRIGHT_WEB_PORT=13100 SKILLHUB_API_PROXY_TARGET=http://127.0.0.1:19180 \
  pnpm --dir web exec playwright test e2e/local-mysql-runtime-smoke.spec.ts --project chromium

# optional supporting E2E
PLAYWRIGHT_WEB_PORT=13100 SKILLHUB_API_PROXY_TARGET=http://127.0.0.1:19180 \
  pnpm --dir web exec playwright test e2e/search-flow.spec.ts e2e/dashboard-routes.spec.ts --project chromium
```

## 6. Risks

- 如果只切换 `application.yml`，但不清理 `compose.release.yml` / `docker-compose.staging.yml`，仓库入口仍会继续把 PostgreSQL 暴露给后续维护者。
- 如果只改脚本名字，不改 pattern 清单，`US-036` 仍然会是假阳性。
- 如果保留 `matchIfMissing = true` 的 PostgreSQL 搜索装配，默认路径仍可能在新环境下被误触发。
- 如果清理 PostgreSQL 代码时没有同步更新文档和测试，容易产生“代码已变、文档仍旧”的二次回归。

## 7. Done Definition

满足以下条件，视为本轮未完成任务收口：

- PostgreSQL 不再出现在主运行时默认配置、标准开发入口和标准 release/staging 入口中。
- PostgreSQL FTS 不再作为主默认运行时代码路径保留。
- 迁移范围覆盖率门禁在 `verify` 中真实生效并通过。
- 真实 `MySQL 8 + Redis + local-file-index` 冒烟与浏览器验证通过。
- 状态文档更新与技术证据一致。
