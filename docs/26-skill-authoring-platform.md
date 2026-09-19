# Skill 创作与验证平台设计、部署与测试

## 定位

Skill 创作与验证平台让作者在 SkillHub 内完成 Skill 的**创建、编辑、受控验证和提交发布**，
而不是在仓库外部手工打包再上传。核心闭环是：

```text
创建草稿 → 编辑 SKILL.md/资源文件 → 绑定 Agent/工具/MCP 运行时
        → 三层验证（结构/配置/行为，含日志与调用轨迹）
        → 定位失败步骤 → 应用修复建议 → 复验
        → 达标草稿提交，复用既有扫描/审核/发布管线
```

平台不引入第二套发布通道：提交动作直接复用 `SkillPublishService`，草稿只是把
"写一个符合规范的 Skill" 变成了带验证门槛的结构化流程。

## 整体架构

```text
web/  React 工作台
  pages/authoring/drafts.tsx          草稿列表 + 创建
  pages/authoring/draft-detail.tsx    文件/运行时/运行记录三个标签页
  pages/authoring/run-detail.tsx      运行详情：事件控制台 + 发现列表
  features/authoring/…                SSE hook、事件合并、diff 预览、发现卡片
        │ REST + SSE
        ▼
skillhub-app
  controller/SkillAuthoringController       19 个 REST 端点
  controller/ValidationStreamController     SSE 事件流（回放 + 直播）
  service/authoring/ValidationRunOrchestrator   编排三层验证、事件、终态
  service/authoring/adapter/*              LocalScript（inline/docker）/ OpenAiCompatible 运行时适配器
  service/authoring/mcp/*                  MCP 客户端（http/sse/stdio）与配置层连通探测
  service/authoring/ValidationEventBroadcaster SSE 广播
        ▼
skillhub-domain
  authoring/           SkillDraft、DraftFile、RuntimeBinding 实体与服务
  authoring/validation ValidationRun / ValidationEvent / ValidationFinding
  authoring/spec       validation.yaml 解析（防御式 SnakeYAML）
  authoring/runtime    任务执行结果与断言求值器
  skill/validation/    SkillPackagePolicy（结构规则与发布侧共享，规则不漂移）
        ▼
skillhub-storage       草稿文件按内容寻址存入对象存储（与 Skill 包同机制）
```

## 领域模型与数据表

迁移 `V60__authoring_platform.sql` 新增六张表：

| 表 | 职责 |
| --- | --- |
| `skill_draft` | 草稿元数据：归属人、名称、`revision`（每次内容变更 +1）、`contentDigest`、`validatedRevision`/`validatedRunId`、提交回填的 `submittedSkillId` |
| `draft_file` | 草稿内单个文件（路径、内容类型、对象存储 key、sha256） |
| `runtime_binding` | Agent 类型 + 适配器配置 + 工具白名单 + MCP 服务器声明（JSONB） |
| `validation_run` | 一次验证运行的快照：draft revision、状态、各层统计 |
| `validation_event` | 运行事件（日志、工具调用、阶段切换），`seq` 单调递增，供 SSE 回放 |
| `validation_finding` | 结构化发现：层级、规则码、严重度、`filePath`/位置、建议（JSONB） |

关键不变量：

- **乐观并发**：草稿保存文件带 `expectedRevision`，冲突返回 409，前端提示刷新后重试。
- **验证即快照**：运行绑定启动时的 revision；`SUCCEEDED` 且 0 错误时回写
  `validatedRevision`。提交门槛是"当前 revision 必须恰好等于 `validatedRevision`"，
  验证通过后再改动文件会自动失去提交资格，必须复验。
- **运行状态机**：`QUEUED → PREPARING → RUNNING → SUCCEEDED / FAILED / CANCELLED / TIMED_OUT`；
  活跃运行支持协作式取消，超时由任务级 `timeoutMs` 与运行级 `run-timeout-ms` 双层控制，
  崩溃残留的活跃运行由维护任务按 `stale-run-minutes` 清扫为 `TIMED_OUT`。

## 三层验证流水线

`ValidationRunOrchestrator` 顺序执行三层，每层产出结构化发现而非异常字符串：

1. **STRUCTURE（结构）**：`DraftStructureValidator` 检查包布局、路径合法性与去重、
   扩展名白名单、内容与扩展名一致性、文件数与体积上限、SKILL.md frontmatter
   必填字段（name、description）。规则全部来自 `SkillPackagePolicy`，与发布侧
   校验逐条对齐，草稿期能过的包到发布期不会再因结构被拒。
2. **CONFIG（配置）**：`RuntimeBindingValidator` 校验 Agent 类型（`local-script` /
   `openai-compatible`）、解释器白名单（`sh/bash/python3/node`）、工具白名单与
   MCP 声明（名称、transport、endpoint/command、工具过滤、env 引用），并扫描
   绑定中任何凭据形态的字段（`CREDENTIAL_EMBEDDED`）——密钥只能存服务端配置。
   同时对 validation.yaml 的全部断言做语法预检（类型合法、必填参数齐全、正则可编译），
   让错误在执行前暴露。声明的 MCP 服务器随后被**真实探测**：逐台连接、完成
   initialize 握手并 tools/list——连不上记 `MCP_CONNECT_FAILED` 错误（行为层
   因此被阻断），`toolFilters` 引用服务器未提供的工具记 `MCP_TOOL_FILTER_UNKNOWN`
   告警，连接成功则在事件流留下工具清单日志。MCP 声明由此从"格式正确"变成
   "验证时确实可用"。
3. **BEHAVIOR（行为）**：把草稿文件物化进一次性隔离工作区，按 validation.yaml
   逐任务执行（script 任务跑 `scripts/` 下的脚本，prompt 任务走 LLM 适配器），
   用 `AssertionEvaluator` 求值断言：`exit_code`、`stdout_contains`、`stdout_matches`、
   `stdout_json`（JSON Pointer 等值）、`artifact_exists`（可附 sha256）、
   `tool_call_count`，以及 prompt 侧的 `response_*` 系列。行为被真实执行验证，
   而不是靠元数据推断。

### 受控执行环境

`LocalScriptRuntimeAdapter` 是行为层默认运行时，有两种执行后端
（`local-script.execution-mode`）：

- **inline**（默认，本地开发）：脚本在服务器主机上以子进程执行。安全基线：
  每次运行独立临时工作区（`workspace-root` 下），脚本路径归一化后必须落在
  工作区内，越界路径直接拒绝；进程环境被清空，只保留 `PATH`、`HOME`
  （指向工作区）、`LANG`，外加绑定 `envAllowlist` 中显式点名的变量；
  stdout/stderr 逐行流入事件流（作者实时可见），同时进入 256KB 截断缓冲用于
  发现；解释器白名单外的命令不可执行；任务超时或运行取消时强制销毁进程。
- **docker**（生产推荐）：同一套脚本在锁定的容器内执行——`--network none`
  （容器内无任何网络路由）、`--memory`/`--cpus`/`--pids-limit` 资源上限、
  只读根文件系统（`--read-only`，工作区读写挂载到 `/workspace`、`/tmp` 为
  限量 tmpfs）、`--cap-drop ALL` + `--security-opt no-new-privileges`、日志
  驱动关闭。容器按运行标识命名，超时/取消时强制删除；Docker 不可用时该后端
  报告不可用而不会静默回退。隔离效果由真实容器内的行为测试实证
  （`DockerScriptRuntimeAdapterTest`：路由表为空、根文件系统只读、工作区可写）。

`OpenAiCompatibleRuntimeAdapter` 处理 prompt 任务，是带工具执行的 agent 循环：
endpoint/model 来自绑定，API key 只从服务端
`skillhub.authoring.openai-compatible.api-key` 解析，默认关闭。任务开始时连接
绑定声明的每台 MCP 服务器并 tools/list，工具经服务器 `toolFilters` 与绑定全局
`toolAllowlist`（空 = 不限制；条目匹配裸工具名或 `server.tool`）双重过滤后以
function 形式暴露给模型；模型发起 tool_calls 时**真实调用**对应 MCP 服务器，
结果回填对话进入下一轮，轮数上限 `max-tool-rounds`（默认 4）。每轮对话、每次
工具调用与结果都是事件流里的 `TOOL_CALL`/`TOOL_RESULT` 记录，`tool_call_count`
断言统计实际执行的工具调用数。

### 事件流（日志与调用轨迹）

每个动作都是一条持久化事件：运行启停、阶段切换、任务工具调用（TOOL_CALL，含
命令与参数）、任务结果、stdout/stderr 逐行日志、断言通过摘要。事件带单调 `seq`，
SSE 端点 `/api/web/authoring/runs/{runId}/events/stream` 支持从 `afterSeq` /
`Last-Event-ID` 回放再续播，终端运行的流会在回放后正常收尾，刷新页面不丢历史。

## 失败定位与修复建议闭环

发现（finding）携带层级、规则码、`filePath` + 位置（失败步骤定位）和可选的
`FixSuggestion`：一个或多个 `FilePatch`（文件路径、旧内容 sha256、旧值、新值）。
前端 `FindingCard` 渲染红绿 diff 预览，应用走两步确认（Apply → Confirm），后端
`FindingFixService.apply` 校验文件未被并发修改后写入补丁并推进 `revision`；
`dismiss` 记录作者已知悉。修复后一键"Fix and re-validate"复验——示例 Skill 的
实测：frontmatter 缺 `description` → `FRONTMATTER_FIELD_MISSING` 带定位与补丁 →
应用后 revision 2→3 → 复验 `SUCCEEDED`。

## 提交与发布管线衔接

`DraftSubmitService.submit` 的门槛：当前 revision 存在 0 错误的 `SUCCEEDED` 运行
（告警已由验证过程向作者展示，视为已确认）。通过后把草稿文件打包交给
`SkillPublishService`：重新校验包结构、触发安全扫描、进入既有审核流，草稿回填
`submittedSkillId`/`submittedVersionId`。也就是说验证平台产出的是"已自证行为"的
Skill 版本，扫描与审核沿用平台原有机制，没有旁路。

## API 一览

前缀 `/api/web/authoring`（同时挂 `/api/v1/authoring`）：

| 端点 | 说明 |
| --- | --- |
| `POST /drafts`、`GET /drafts`、`GET/DELETE /drafts/{id}` | 草稿 CRUD（创建时生成 SKILL.md 脚手架，validation.yaml 由作者按需在工作台新建） |
| `GET /drafts/{id}/files`、`PUT /drafts/{id}/files`、`GET /drafts/{id}/files/content`、`DELETE /drafts/{id}/files` | 文件列表 / 保存（带 expectedRevision）/ 读取 / 删除 |
| `GET/PUT /drafts/{id}/runtime` | 运行时绑定读写 |
| `POST /drafts/{id}/runs`、`GET /drafts/{id}/runs` | 启动验证 / 运行历史（版本与验证记录持久化） |
| `GET /runs/{id}`、`POST /runs/{id}/cancel` | 运行详情 / 取消活跃运行 |
| `GET /runs/{id}/events`、`GET /runs/{id}/events/stream` | 事件轮询 / SSE 流 |
| `GET /runs/{id}/findings`、`POST /runs/{id}/findings/{fid}/apply`、`POST /runs/{id}/findings/{fid}/dismiss` | 发现列表 / 应用修复 / 忽略 |
| `POST /drafts/{id}/submit` | 达标草稿提交发布 |

## 前端工作台

路由（均需登录）：`/dashboard/authoring`（列表）、`/dashboard/authoring/$draftId`
（文件 / 运行时 / 运行记录）、`/dashboard/authoring/$draftId/runs/$runId`（运行详情）。

- 文件编辑器支持新建/删除/保存，未保存标记 + 乐观并发冲突提示；
- 运行时表单按 Agent 类型切换字段（解释器 vs endpoint/model），工具白名单与
  MCP 服务器声明有即时校验；
- 运行详情页 = 状态卡 + 自动滚动事件控制台（SSE 直播，断线 2 秒轮询降级）+
  发现卡片列表；活跃运行每 2 秒轮询状态直到终态；
- 文案覆盖 en/zh/ru 三语言。

## 部署

### 配置项（`skillhub.authoring.*`，均可经环境变量覆盖）

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `workspace-root` | `${java.io.tmpdir}/skillhub-authoring` | 隔离工作区根目录（`SKILLHUB_AUTHORING_WORKSPACE_ROOT`） |
| `executor-threads` | 4 | 进程内并发验证线程数 |
| `run-timeout-ms` | 900000 | 单次运行硬上限（15 分钟） |
| `stale-run-minutes` | 30 | 崩溃恢复清扫阈值 |
| `local-script.enabled` | true | 本地脚本运行时开关 |
| `local-script.execution-mode` | inline | 脚本执行后端：`inline`（主机子进程）/ `docker`（锁定容器，`SKILLHUB_AUTHORING_LOCAL_SCRIPT_MODE`） |
| `local-script.docker.image` / `memory` / `cpus` / `pids-limit` / `tmpfs-size` | alpine:3.20 / 256m / 1.0 / 128 / 64m | 容器隔离参数（`SKILLHUB_AUTHORING_DOCKER_*`）；镜像需自带 Skill 用到的解释器 |
| `openai-compatible.enabled` | false | LLM 运行时开关（默认关闭） |
| `openai-compatible.api-key` | 空 | 服务端密钥，绝不写入草稿绑定 |
| `openai-compatible.default-endpoint` / `default-model` | 空 | prompt 任务的默认 LLM |
| `openai-compatible.max-tool-rounds` | 4 | 单个 prompt 任务的最大对话轮数（含 MCP 工具执行） |

### 部署要点

- **数据库**：Flyway 迁移 V60 自动建表，无手工步骤；JSONB 列要求 PostgreSQL
  （平台既有要求，无新增）。
- **启用 prompt 任务**：配置 `SKILLHUB_AUTHORING_LLM_ENABLED=true` 并给出
  endpoint/model/api-key；不启用时含 prompt 任务的草稿会得到 `RUNTIME_DISABLED`
  配置层发现，script 流程不受影响。
- **启用容器隔离**：`SKILLHUB_AUTHORING_LOCAL_SCRIPT_MODE=docker` 并确保运行节点
  可访问 Docker daemon。镜像需包含 Skill 用到的解释器（alpine 只带 `sh`；
  python3/node 的 Skill 应换基础镜像）。脚本执行不再依赖主机环境。
- **MCP 服务器**：stdio 传输的 MCP 命令在服务器进程侧 spawn，`envRefs` 点名的
  环境变量从服务器环境注入（这是凭据到达 MCP 服务器的唯一通道）；http/sse
  传输直接访问声明的 endpoint。验证时每台声明的服务器都会被真实探测。
- **工作区**：验证运行会在 `workspace-root` 下创建一次性目录并在结束后清理，
  生产环境建议独立磁盘分区并纳入监控；多实例部署时运行在工作区所在节点本地
  执行，无需共享存储。
- **RISC-V64**：后端是架构无关的 Java 21 JAR，沿用 `skillhub-server` 既有
  `linux/riscv64` 构建路径（见 `docs/RISCV64.md`）。local-script 运行时只依赖
  容器内 POSIX 工具（`sh`、`wc` 等），Temurin 运行时镜像自带；安全扫描器的
  RISC-V 限制与创作平台无关（扫描发生在提交后的发布管线）。
- **安全基线**：脚本在清空环境的最小工作区执行、解释器白名单、路径不得越界、
  绑定禁止内嵌凭据、单文件 10MB / 包 100MB / 500 文件上限与发布侧一致。

### RISC-V64 验证记录（2026-09-20）

在 arm64 宿主机上用 Buildx + QEMU 对本功能构建 `linux/riscv64` 服务端镜像并完成
活体验证（对 `docs/RISCV64.md` 组件镜像边界的实测补充）：

- **镜像构建**：`server/Dockerfile` 原生路径（JAR 在构建宿主侧编译，运行时基于
  `eclipse-temurin:21-jre-noble` 的 riscv64 官方变体），`docker image inspect`
  确认 `linux/riscv64`。
- **启动**：QEMU 模拟下 Spring Boot 91.9 秒完成启动，`/actuator/health` 返回
  200；Flyway 在全新 PostgreSQL 16 上完成全部迁移（含 V60 创作平台表）。
- **创作平台全流程（REST API）**：创建草稿 → 读取脚手架 SKILL.md → 保存
  `scripts/check.sh` 与 `validation.yaml` → 保存 local-script 绑定 → 启动验证 →
  运行 `SUCCEEDED`（0 错误 0 警告），12 条事件完整落库且脚本 stdout 出现在事件
  流中 —— 行为层的脚本子进程真实运行在 riscv64 用户态内。
- **结论**：创作与验证平台不引入任何架构相关依赖（纯 JVM 字节码 + POSIX
  工具），随 `skillhub-server` 既有 `linux/riscv64` 发布路径交付即可；
  `execution-mode: docker` 时基础镜像需有 riscv64 变体（默认 `alpine:3.20`
  官方提供）。
- **边界**：本记录为 QEMU 模拟验证，与 CI 的 riscv64 镜像 guardrail 同级；原生
  RISC-V 硬件上的全栈冒烟（PostgreSQL/Redis/对象存储/scanner）仍按
  `docs/RISCV64.md` 的边界声明执行。

## 测试

| 层 | 位置 | 覆盖 |
| --- | --- | --- |
| 领域单测 | `skillhub-domain/…/authoring/` | `DraftStructureValidatorTest`（结构规则与修复建议）、`ValidationSpecParserTest`（防御式 YAML 解析、任务/断言语义）、`AssertionEvaluatorTest`（全部断言类型与失败信息）、`SkillScaffoldGeneratorTest` |
| 运行时单测 | `skillhub-app/…/authoring/adapter/` | `DockerScriptCommandBuilderTest`（隔离参数与挂载构造）、`DockerScriptRuntimeAdapterTest`（真实容器内实证：无网络路由、只读根文件系统、工作区可写、输出捕获；无 Docker 时静默跳过）、`OpenAiCompatibleRuntimeAdapterToolLoopTest`（agent 循环：MCP 工具发现→模型调用→真实执行→轨迹回填，toolFilters/toolAllowlist 过滤、轮数上限） |
| MCP 单测 | `skillhub-app/…/authoring/mcp/` | `HttpMcpClientTest`（initialize/tools 握手、会话头复用、SSE 帧解析、错误结果、不可达报错，对真实本地 HTTP 服务器）、`StdioMcpClientTest`（stdio 传输对 python3 子进程）、`McpProbeServiceTest`（连不上→`MCP_CONNECT_FAILED`、未知 toolFilter→告警、可跳过畸形声明） |
| 端到端集成 | `skillhub-app/…/authoring/AuthoringFlowIntegrationTest` | Testcontainers 真实 PostgreSQL 上跑通完整闭环：建草稿 → 改文件 → 绑定运行时 → 三层验证 → 修复发现 → 复验 → 提交（`ddl-auto=validate` 顺带校验 V60 与实体映射一致） |
| 前端单测 | `web/src/features/authoring/*.test.*` | 事件按 seq 合并去重、SSE 生命周期（回放合并、终态关闭、断线轮询降级）、修复 diff 预览、二进制文件 base64 处理 |
| 浏览器 E2E | `web/e2e/authoring-flow.spec.ts` | Playwright 真实 API 全 UI 闭环：创建草稿（命名空间/名称对话框）→ 文件编辑（SKILL.md/脚本/validation.yaml）→ 保存运行时绑定 → 启动验证至 Succeeded（事件控制台含脚本输出）→ 提交对话框过闸；坏 frontmatter → 失败发现 → diff 预览 → 两步确认应用修复 → 一键复验通过；二进制资源上传 → 只读面板（大小/类型/sha256）与字节级校验。断言落在持久 UI 状态（按钮态、徽章、响应体）而非易失 toast |
| 活体冒烟 | `scripts/authoring-smoke-test.sh` | 对运行中的服务器 35 项检查、四个场景：全层验证并提交；坏 frontmatter → 定位 → 应用修复 → 复验；守卫（未验证不可提交、跨用户不可读他人草稿）；MCP 探测（死端点报 `MCP_CONNECT_FAILED`、本地假 MCP 服务器工具被发现并写入事件流、未知 toolFilter 仅告警），幂等可重复执行 |
| 示例 Skill | `examples/skill-authoring/` | 可直接装载的完整示例（含 4 个行为验证用例，覆盖全部 6 种 script 断言），README 给出 UI 与 API 两种装载方式 |

常用命令：

```bash
# 后端（需 Java 21；默认 JVM 24 会破坏 Mockito/ByteBuddy）
cd server && JAVA_HOME=<jdk21> ./mvnw -pl skillhub-app -am test

# 前端
cd web && pnpm lint && pnpm typecheck && pnpm test

# 活体冒烟（先启动 dev profile 服务器）
./scripts/authoring-smoke-test.sh http://localhost:8082

# 浏览器 E2E（后端跑在 8082，vite 由 Playwright 拉起并把 /api 代理过去）
cd web && VITE_API_PROXY_TARGET=http://localhost:8082 pnpm test:e2e -- e2e/authoring-flow.spec.ts

# 重新生成前端 API 类型
cd web && pnpm exec openapi-typescript http://localhost:8082/v3/api-docs \
  -o src/api/generated/schema.d.ts
```

## 边界与非目标

- v1 的行为验证以确定性断言为主，语义质量评判不强行近似（归属模型辅助审核）；
- 不支持草稿多人协作编辑（单一 owner + 乐观锁防误覆盖）；
- 提交后草稿回填 `submittedSkillId`/`submittedVersionId` 作为回执，但并不锁定：
  继续编辑会推进 revision 并使已验证标记失效，再次提交前必须重新验证；
- MCP 工具调用发生在验证运行内（配置层探测 + prompt 任务执行），平台不充当
  常驻的 MCP 网关；stdio MCP 命令与脚本任务共享同一台运行节点。
