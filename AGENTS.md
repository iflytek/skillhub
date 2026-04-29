# AGENTS.md

该文件为在本仓库内工作的 AI 编码代理提供项目级指导。

## 项目概览

SkillHub 是一个企业自托管的 agent skill registry 与治理平台。

- 项目类型：全栈 Web 应用 Monorepo
- 主要能力：技能发布、搜索、命名空间治理、审核/提升、安全审计、账号与令牌管理
- 仓库组成：
  - Java/Spring Boot 多模块后端
  - React/Vite 前端
  - 文档与设计文档
  - 本地安全扫描器集成

## 技术栈

| 技术 | 用途 |
|------|------|
| Java + Spring Boot | 后端 API、认证、治理、业务逻辑 |
| Maven 多模块 | 后端构建与模块管理 |
| PostgreSQL | 标准运行模式主数据库 |
| Redis + Spring Session + Redisson | 会话、限流、扫描任务流 |
| Flyway | PostgreSQL schema 初始化与迁移 |
| H2 | `local-h2` 轻量本地开发模式 |
| React 19 + Vite | 前端应用 |
| TypeScript | 前端类型系统 |
| TanStack Router / Query | 路由与数据获取 |
| Tailwind CSS | 前端样式 |
| Vitest | 前端单测 |
| Playwright | 前端 E2E |
| Docker Compose | 本地依赖与 staging 环境 |
| Makefile | 统一开发入口 |

## 常用命令

优先使用根目录 `Makefile`，不要绕开它直接拼装本地命令。

```bash
# 本地开发
make dev-all
make dev-all-down
make dev-server-restart
make dev-status

# 构建与测试
make build
make test
make test-backend-app
make test-frontend
make test-e2e-frontend

# 前端质量检查
make typecheck-web
make lint-web

# API 合同同步
make generate-api

# 预发回归
make staging
make staging-down
```

## 项目结构

```text
skillhub/
├── server/        # Spring Boot 多模块后端
├── web/           # React/Vite 前端
├── docs/          # 当前有效的设计/架构/流程文档入口
├── design/        # 对当前代码仍有解释价值的设计材料与已实现 PRD 归档
├── scanner/       # 本地 scanner Docker 构建上下文与说明
├── scripts/       # 辅助脚本
└── .agents/       # 代理命令与模板
```

## 架构

### 后端

- `server/pom.xml` 定义多模块工程：
  - `skillhub-app`：Spring Boot 入口、controller、app service、运行时配置
  - `skillhub-domain`：领域模型与核心业务规则
  - `skillhub-auth`：认证、OAuth、本地账号、device auth、token、session
  - `skillhub-search`：搜索 SPI 与 Postgres/H2 实现
  - `skillhub-storage`：对象存储抽象与实现
  - `skillhub-infra`：JPA 与外部集成适配
  - `skillhub-notification`：通知能力
- 常见调用链：`controller -> app service -> domain service / repository`
- 例外：搜索适配层允许直接写存储引擎相关 SQL，见 `server/skillhub-search/.../PostgresFullTextQueryService.java`

### 前端

- `web/src/app`：应用壳、provider、路由注册
- `web/src/pages`：路由页面层
- `web/src/features`：按业务域组织的功能模块
- `web/src/shared`：共享组件、hooks、工具
- `web/src/api/generated`：生成的 OpenAPI 类型

### 运行模式

- `local`：PostgreSQL + Redis + Flyway，接近真实环境
- `local-h2`：H2 文件库、无 Flyway、无 Redis 会话，面向轻量本地联调
- 搜索由 `skillhub.search.engine` 切换：
  - `postgres`：全文检索 + 可选语义重排
  - `h2`：LIKE 降级搜索

## 代码模式

### 命名约定

- Java 使用明确的职责命名：`*Controller`、`*AppService`、`*Service`、`*Repository`
- 前端页面文件通常与路由语义一致：`dashboard/*.tsx`、`settings/*.tsx`
- 测试文件与被测文件同目录，采用 `*.test.ts`、`*.test.tsx` 或 `*.spec.ts`

### 文件组织

- Controller 保持薄，只做参数整形、鉴权边界和响应映射
- 业务规则优先放在 `skillhub-domain`
- 前端路由集中定义在 `web/src/app/router.tsx`
- 前端运行时配置先由 `web/src/bootstrap.ts` 注入，再挂载 React

### 错误处理

- 后端使用领域异常和统一 API 响应工厂，常见类型见 `domain/shared/exception`
- 前端通过全局 query/mutation error handler 处理 API 错误
- 不要在 E2E 中用 API mock 掩盖真实行为，优先跑真实请求链路

### 文档治理

- `docs/README.md` 是当前主文档入口
- `design/implemented/` 只存放已实现需求归档
- 不要把一次性计划或已完成 PRD 继续堆在主 `docs/`

## 测试

- 后端测试：`make test-backend` 或 `make test-backend-app`
- 前端单测：`make test-frontend`
- 前端 E2E：`make test-e2e-frontend`
- 测试位置：
  - Java：各模块 `src/test/java`
  - 前端单测：`web/src/**/*test.ts(x)`
  - 前端 E2E：`web/e2e/**/*.spec.ts`

测试约定：

- 前端 E2E 默认走真实请求，不要新增 `page.route('**/api/...')` 这类 API mock
- 修改后端 API 合同时，需要同步更新生成的前端 schema
- 修改搜索、认证、命名空间、审核等主链路时，优先补对应单测或 E2E

## 验证

提交前按变更范围至少执行相应命令：

```bash
make test
make typecheck-web
make lint-web
make test-e2e-smoke-frontend
```

如修改了后端 API 合同，再执行：

```bash
make generate-api
```

如修改了后端模块依赖或 app 入口链路，优先执行：

```bash
make test-backend-app
```

## 关键文件

| 文件 | 用途 |
|------|------|
| `README.md` | 项目总览、运行方式、发布/runtime 说明 |
| `Makefile` | 本仓库统一命令入口 |
| `docs/README.md` | 当前有效文档索引 |
| `design/README.md` | 设计文档总索引与目录规则 |
| `docs/dev-workflow.md` | 本地开发到 staging/PR 的推荐流程 |
| `docs/e2e.md` | 前端 E2E 真实请求规范 |
| `server/pom.xml` | 后端模块边界与 Java 编译配置 |
| `server/skillhub-app/src/main/resources/application.yml` | 标准运行模式配置 |
| `server/skillhub-app/src/main/resources/application-local-h2.yml` | 轻量本地模式配置 |
| `server/skillhub-app/src/main/resources/db/README.md` | Flyway 收敛与迁移归档说明 |
| `web/package.json` | 前端脚本与依赖 |
| `web/src/app/router.tsx` | 前端路由总表 |
| `web/src/bootstrap.ts` | 前端运行时配置注入入口 |

## 按需上下文

| 主题 | 文件 |
|------|------|
| 系统架构 | `docs/01-system-architecture.md` |
| 领域模型 | `docs/02-domain-model.md` |
| 搜索架构 | `docs/04-search-architecture.md` |
| 前端架构 | `docs/08-frontend-architecture.md` |
| 技能生命周期 | `docs/14-skill-lifecycle.md` |
| 设计索引 | `design/README.md` |
| 文档治理 | `design/governance/documentation-governance.md` |
| 已实现 PRD 归档 | `design/implemented/README.md` |

## 备注

- 本地开发按 `docs/dev-workflow.md` 使用 Java 17，并与 `server/pom.xml` 保持一致。
- 不要在 `server/` 下直接运行 `./mvnw -pl skillhub-app clean test`；使用 `-am` 或 `make test-backend-app`，避免落到过期本地 Maven 产物。
- 新库初始化只走 `server/skillhub-app/src/main/resources/db/migration/V1__init_schema.sql`；历史迁移文件已归档到 `db/migration-archive/`，不要再把新迁移放回归档目录。
- 当前仓库正在做文档治理和 Flyway 迁移收敛；改动相关文件时，先确认你修改的是“当前入口文档”还是“归档材料”。
