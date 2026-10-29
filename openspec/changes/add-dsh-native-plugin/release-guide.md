# 验证、部署与发布方案

## 1. 发布对象与职责

| 对象 | 交付形式 | 安装或部署位置 | 更新条件 |
| --- | --- | --- | --- |
| DSH 插件 | 独立 npm 包及同版本 tarball | 用户的 DSH Host/Profile | 页面、工具、安装逻辑或内部通信发生变化 |
| SkillHub 开源服务端 | 服务镜像及部署配置 | 开源实例运行环境 | 新增或调整 Registry API |
| SkillHub SaaS 服务端 | SaaS 发布流水线产物 | SaaS 环境 | 同步公共 API 契约和实现 |

插件运行在 DSH 主机，不部署进 SkillHub 网站，也不要求网站嵌入插件 UI。插件内部 Remote 接口随插件版本交付；Registry API 随服务端交付。两者分别管理版本和发布节奏。

### 1.1 当前候选与发布顺序

本轮拟发布 CLI `0.3.0`（`cli-v0.3.0`）和 DSH `0.6.0`（`dsh-v0.6.0`），范围为技能启停、统一只读检查更新、紧凑已安装卡片，以及发布前库存兼容修复。不发布服务端镜像或部署 SaaS。

共享库存保持严格 v2，不写入 `installationState`；启停状态从可信的 `SKILL.md` / `SKILL.md.disabled` 入口派生，不要求 CLI 用户同步升级。已发布 CLI `0.2.0` 的正常查询和普通目标管理必须继续兼容；默认更新、同步及 Suite 不得无意重新启用停用目标。旧 CLI 显式 force 重装、更新、同步仍按原覆盖语义执行，可能恢复标准入口；显式 remove 仍可删除，新版重新读取须反映真实变化。

1. 使用真实旧公开 CLI `0.2.0`、DSH `0.5.0` 建立基线，在同一隔离 HOME 交替操作，验证普通/停用目标混存、来源与位置隔离、默认保护和显式覆盖语义。仅证明拒写或字节未损坏不算兼容通过。
2. 读 v1/v2 不迁移。未发布 v3 必须在锁内校验身份、入口与未完成事务，备份原始字节后安全转换 v2；普通操作日志保持旧 DSH 可读，启停日志隔离影响。并发及五阶段强杀恢复重新验收。
3. 完成候选检查、双 RC、跨平台、真实浏览器及草稿保护；旧候选结果仅在输入等价时可复用。兼容修复正在进行，未通过前阻断发布。
4. 通过后按专用工作流依次发布 CLI 和 DSH，核对公开字节、干净安装和升级保留。此顺序是交付安排，不是要求用户同步升级 CLI。当前不声明任何候选已发布。

旧消费者不承诺识别启停专用日志：中断启用已留下合法标准入口时，旧版仍可能执行合法管理。新恢复必须先核精确目标身份、版本、时间、指纹和归属，发现旧版变更即保留现场并返回 unknown，不能用旧意图覆盖新内容。CLI 的 v3 转换只核当前库存和入口并保留日志，不扫描全机 Host；NativeRuntime 在自己的日志域先处理恢复。

插件内置核心，不调用 CLI。回退不能只恢复库存：须核对入口、内容、元数据和日志，不能用过时备份覆盖新记录。完整协议以 [启停设计 §7](skill-toggle-design.md#7-库存协议与实施门禁) 为准。

### 1.2 历史：0.4.0 库存升级说明

此前库存升级交付 CLI `0.2.0` 和 DSH 插件 `0.4.0`，不发布服务端镜像、不部署 SaaS。插件内置安装核心，不要求另装 CLI；但同一用户使用 CLI 共同管理技能时，两者必须使用兼容库存格式。

1. 完成候选 tarball 的干净安装与旧库存升级验证。保留旧版公开包及未迁移的隔离数据，用真实旧包建立升级基线；不能只用新代码构造旧 JSON 就声称完成旧版升级。
2. 先发布 `@astron-team/skillhub@0.2.0`，检查公开包版本、命令输出、库存 v1 只读行为、首次写入的 v2 迁移与备份、用户/项目版本隔离。
3. 再发布 `@astron-team/skillhub-dsh@0.4.0`，核对公开包与已测候选完整性，在支持的 DSH Profile 中干净安装或升级，确认既有技能保留、管理与使用入口正常。
4. 已同时使用 CLI 的用户先升级这些 CLI，再升级插件，并停止旧 CLI 的后台写入。只有插件的用户无需额外安装 CLI。旧 CLI 与 v2 库存混写不受支持。

读取库存 v1 不会写盘迁移；首次写入 v2 前在库存同目录生成并核对 `inventory.v1.<sha256>.backup.json`。备份只覆盖旧库存，不覆盖之后改变的技能目录、安装元数据或事务日志。位置版本已经分化后，不能仅替换库存或降级包；回退需停止所有写入并核对对应文件与记录，不能把库存备份称为完整回滚点。

`.skillhub/examples.json` 是作者随包提供的版本内容，安装后路径不变，参与指纹与生命周期；它不是全局配置或安装器生成的副本。升级验证应包含带案例与无案例的技能，并确认无效声明按普通文件保留。

## 2. 验证流程

```mermaid
flowchart LR
  C[代码与契约检查] --> U[单元及集成测试]
  U --> P[构建并检查插件包]
  P --> D[隔离 DSH 环境安装]
  D --> O[开源 Registry 联调]
  O --> S[SaaS 测试环境契约验证]
  S --> R[候选版本验收]
  R --> PUB[发布插件与对应服务端]
  PUB --> CHECK[发布后安装和接口检查]
```

SaaS 验证决定该版本能否声明支持 SaaS，不阻止已通过开源环境验收的插件版本交付。支持范围在兼容矩阵中分别列出。

### 2.1 本地与 CI 检查

仓库根目录执行已有 CLI 检查：

```bash
make test-cli
make typecheck-cli
make lint-cli
make build-cli
```

服务端有接口改动时执行：

```bash
make test-backend-app
make build-backend-app
make generate-api
```

`make generate-api` 后检查生成文件差异。完整接口漂移检查使用 `./scripts/check-openapi-generated.sh`，该脚本启动本地依赖及后端，应在独立开发或 CI 环境执行。

插件检查入口已在 `plugins/dsh/package.json` 定义。首次从仓库根目录安装两组锁定依赖，再执行：

```sh
cd cli
bun install --frozen-lockfile
cd ../plugins/dsh
bun install --frozen-lockfile
bun run check
npm pack --dry-run --ignore-scripts
npm pack --ignore-scripts --pack-destination /absolute/path/to/candidate-output
```

候选输出目录须事先创建并位于仓库外，每轮使用独立目录。`check` 包含 build、typecheck、lint、test；仅 CLI 检查通过不能替代插件检查。记录实际 tarball 校验值、源码版本和验收环境，不能用文件名或相同开发版本号判断两个包相同。

`check-dsh-plugin.yml` 使用 Linux、macOS、Windows 三平台矩阵，Node 24.2.0 与当前 Node 24 双版本、Bun 1.3.13，分别运行共享 CLI 检查、插件 `bun run check` 和隔离 Web Profile 的 Host 安装冒烟，并保存带平台名的 tarball 与脱敏摘要。新 RC `0.2.1-rc.2` 另有 Linux 双 Node 原生 Host 检查，复用对应 Node 档的原矩阵产物；不将此结果扩大为新 RC 的 Windows/macOS 原生文件操作验收。步骤显式使用 Bash，避免 Windows 默认 shell 对脚本的不同解释；平台失败不取消其他平台任务。矩阵通过只证明该次构建与自动化测试结果，真实 DSH Profile 安装、页面和对话验收仍须单独完成。平台测试中的跳过项必须记录，不能算作通过。

新增独立安装启停验收矩阵：Linux/macOS/Windows × DSH `0.2.0-rc.2`/`0.2.1-rc.2`，固定 Node `24.2.0`，复用对应平台检查过的候选 tarball。`--scenario skill-toggle` 与 `--scenario skill-toggle-watch-off` 分别创建全新隔离 Host/Profile，不能复用可用性探针遗留的安装状态。核心恢复测试在五个持久化边界终止本次测试子进程；Windows 使用原生进程终止，POSIX 使用 SIGKILL。核心进程恢复、Host 启停发现及真实浏览器交互分别记录，不将核心测试称为完整 Host 强杀重启验收。矩阵配置不等于本次已通过，结果见最终发布验证记录。

### 2.2 插件包检查

- Host、Client、patch、Remote 元数据、声明及许可证包含在包中，exports 指向有效文件。
- Host bundle 不包含 CLI 命令入口、登录读取、交互终端或 process.exit 副作用。
- Client 不包含 Node 文件系统能力，不携带第二套冲突的 React 运行时。
- 在仓库外安装 tarball，不依赖源码目录、开发 node_modules、额外 SkillHub CLI 或 Bun 运行时。
- 检查包名、版本、工具名与面板标识冲突；重载后不产生重复入口或监听。

### 2.3 隔离环境验证

本次 `0.6.0` 候选分别验证 DSH `0.2.0-rc.2` 与 `0.2.1-rc.2`，Node `>=24.2.0 <25`。Host、app-boot、核心模块及注入 Client 模块须为同一精确发行版。新 RC 仅使用 `4.0.5 / 1.0.6 / 1.0.10 / 1.1.7`；旧 RC 的 Cordis、Loader、Include、Timer 须为已验证的完整组合：`4.0.4 / 1.0.5 / 1.0.9 / 1.1.6` 或 `4.0.5 / 1.0.6 / 1.0.10 / 1.1.7`，不得交叉混装；以实际 Host 解析结果为准。已发布 `0.1.0` 尚不包含后一组合支持及历史回执修复，`0.1.1` 起包含这些修复，`0.2.0` 延续此兼容基线；发布前使用本次 tarball 验证。在独立测试账户或容器中准备 Web Profile、工作区和测试技能，连接测试 Registry。仅隔离 Profile/DSH home 不足以隔离共享 inventory。

首次创建测试 Web Profile，然后安装本地候选包：

```sh
dsh --profile skillhub-test --from-default-profile web --dump-config
dsh plugin --profile skillhub-test add /absolute/path/to/candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

已有 Web Profile 跳过创建步骤。包替换前停止该 Profile 的 Host；安装与启动保持相同 DSH home。入口 URL 可能含访问凭证，只用于本机访问，不写入发布记录。

也可在插件源码目录执行自动隔离冒烟：

```sh
node scripts/smoke-native-host.mjs --artifact /absolute/path/to/candidate.tgz --report /absolute/path/to/host-smoke-summary.json
```

默认执行普通全新安装。保留旧组合的回归需另行执行：

```sh
node scripts/smoke-native-host.mjs --artifact /absolute/path/to/candidate.tgz --report /absolute/path/to/host-smoke-retained-summary.json --cordis-family retained
```

脚本分别核对实际 Host 依赖解析与插件生命周期；旧组合同时约束 Host 和 Profile 的依赖，不能只固定顶层 Cordis。该脚本不调用模型，不能替代浏览器和技能写入验收。历史回执用例须真实停止并重启同一 Profile，验证核验状态、跳转已安装及无重复安装请求；页面刷新不能代替 Host 重启。

按 [验收矩阵](acceptance.md) 完成以下主路径：

1. 广场搜索、详情及可选 Tab；对话搜索卡片与详情抽屉。
2. 用户级与可信项目级安装；确认带资源包完整、记录一致、发现状态正确。
3. 页面与工具同时提交、响应丢失、Host 重启，确认没有重复写入。
4. 检查更新、全部允许目标升级、单目标卸载；离线卸载及本地修改阻断。
5. 更新来源、切换项目、打开历史卡片，确认旧引用不能写入新上下文。
6. 禁用、重载、升级插件，确认既有技能保留且操作日志可核验。
7. 搜索按来源、命名空间和当前可信范围展示安装版本及异常；安装、升级、卸载后刷新当前状态，历史对话保留查询时快照。

Linux、macOS、Windows 分别记录 DSH 发行版、插件版本、Registry 契约版本及结果。未覆盖组合标为“未验证”，不列入支持列表。

当前已有 Linux Host、真实浏览器交互及 Windows 浏览器访问 Host 的验收，界面已完成人工 review。Windows 浏览器验收不等于 Windows 原生 Host 全部文件事务验收。`0.1.1` 发布标签的 Linux/macOS/Windows 原生 Host CI 六组均通过；个人电脑桌面客户端人工验收、其他 DSH 发行版及 SaaS 不列为本轮已验证。CI 配置、已有版本的历史报告和当前候选人工验收分别记录，不能互相替代。

### 2.4 配置与人工验收

进入 DSH“Plugins/插件 → @astron-team/skillhub-dsh → SkillHub 设置”，填写 Registry 基础地址，可先点击“测试连接”验证当前草稿：不保存配置，显示公开目录或基础搜索可读性及耗时，最长等待 10 秒。改地址清除旧结果；失败或超时可重试。测试成功仅证明该读取路径可用，不代表下载或全部模块可用。确认后保存。配置页“已配置”仅反映保存状态；“重置修改”清除未保存草稿，不是网络重载。只读状态下输入、选择器和保存均应禁用。来源地址不允许凭证、查询参数或片段。

在侧栏“技能中心”按顺序核验：

| 范围 | 可观察结果 |
| --- | --- |
| 配置 | 草稿连接测试不保存、显示能力和耗时、10 秒超时与改地址清结果；地址与三个模块策略保存；冲突不覆盖新 revision；重置不发送保存请求 |
| 初次加载 | 顶部布局保留，六张 Skeleton 卡；请求结束展示真实数据，失败可重试；后台刷新保留已有列表 |
| 目录 | 关键词、排序、标签作用于真实结果；加载更多追加且去重；详情返回保留位置 |
| 详情 | 文档、版本和可选 Tab 正确；切换版本重新取得固定版本凭据；原来源信息默认收起 |
| 安装与管理 | 用户/项目目标明确，确认后写入；升级核验完整目标集合，卸载只影响选定位置；资源和清单一致 |
| 对话 | 搜索不自动安装；用户明确请求后才安装；历史结果与当前库存不同能正确说明 |
| 异常 | 401/403、429、网络错误、来源切换、响应丢失、恢复状态不被当成空数据或成功 |
| 外观与输入 | DSH 深浅主题、390px 窄屏、键盘焦点、Select/确认弹窗、减少动画设置 |

搜索和技能安装的模型行为需单独验证，记录使用模型和授权调用范围；无模型的页面或夹具测试不算模型验收。

## 3. 服务端部署

插件使用 `/api/web/skills` 的分页/排序及 `label` 筛选、`/api/web/labels` 的实际可见标签，以及既有搜索、resolve、元信息、固定版本文档/下载、版本及评论接口。标签不是版本 tags；标签筛选失败不能退回未过滤搜索。服务端契约与可选模块访问限制见 [Registry 同步方案](registry-api-contract.md)。

未修改服务端接口时，无需因插件发布重新部署 SkillHub。新增接口采用向后兼容发布：先部署服务端，再发布依赖新接口的插件；已部署旧插件应继续工作。

开源实例可沿用仓库现有 Compose 发布方式：

1. 按部署指南配置 `.env.release`，将 `SKILLHUB_VERSION` 设置为目标固定发布版本。
2. 备份运行配置；存在数据库迁移时执行对应备份和迁移检查。
3. 在目标环境执行配置检查和服务更新：

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d
```

4. 检查服务健康、数据库迁移状态及插件使用的匿名接口。
5. 使用固定插件版本执行搜索、详情和测试技能下载验证。

SaaS 使用其现有流水线部署；公共接口契约、测试数据规则与兼容判定与开源一致。环境地址、流水线名称及凭证保存在环境配置中，不写入插件包。

## 4. 插件发布

### 4.1 独立发布流程

插件使用独立版本，tag 前缀固定为 `dsh-v`（本次为 `dsh-v0.6.0`），不复用 CLI 的 `cli-v*` 发布流程。专用工作流为 `.github/workflows/release-dsh-plugin.yml`；发布前仍须确认 npm 包名、兼容基线及候选包验收，本次候选为 `0.6.0`，manifest 已显式设为 `private: false`；缺省 Registry 为 `https://skill.xfyun.cn`，显式空串/空白仍断开，已有自定义来源保留。发布前分别验证缺省、断开和自定义配置，以及正式域名的固定版本安装、状态识别和本地管理。正式发布仍以最终提交、候选验收和审批结果为准。

发布流程依次完成：校验版本 → 测试与构建 → 生成并检查 tarball → 隔离 DSH 候选验收 → 发布审批 → 发布 npm → 校验已发布包。当前工作流自动执行版本校验、测试与构建、包检查、上传及完整性校验；隔离 DSH 的 UI、真实模型和平台验收按本次确认范围另行执行，未验项目须单独注明；CI 矩阵成功不等同于这些验收通过。工作流使用仓库的 `NPM_TOKEN` 发布凭证，源码及 npm 包不包含凭证；配置了环境保护规则时还须完成对应审批。

`publish-images.yml` 的 publish job 在 Release tag 以 `dsh-v` 开头时跳过，其依赖的运行时镜像同步 job 随之跳过；手动执行和其他 Release 行为保留。`publish-chart.yml` 的发布 job 限定为手动触发或 `v`、`chart-v`、`helm-v` 前缀。`release-cli.yml` 自动触发仅匹配 `cli-v*`，并支持手动执行。插件 Release 必须使用 `dsh-v` 前缀，不能使用平台或 CLI 前缀。以上隔离不等于插件已获发布授权，发布前仍需完成专用发布工作流和候选验收。

### 4.2 发布工作流操作

`Release DSH Plugin` 仅接受手动触发，运行引用必须选择与 `plugins/dsh/package.json` 版本一致的 `dsh-v<version>` tag，不接受分支。默认 `publish=false`，只运行三平台、两个 Node 版本检查并生成候选 tarball 与 SHA-512 校验文件，不访问发布凭证。工作流不自动修改版本或移除 `private`。

发布前须完成以下准备：

1. 审核包名及正式版本，将可发布 manifest 显式设为 `private: false`，完成候选版本验收并创建对应 tag。
2. 核验仓库现有 `NPM_TOKEN` 对目标包和 tag 的发布权限，可以复用 CLI 使用的仓库 secret，无需重复配置。工作流引用的 `dsh-npm` Environment 不存在时 GitHub 会自动创建，但不自动配置审批规则；如设置了保护规则，发布还须满足这些规则。
3. 从同一 tag 手动运行工作流并选择 `publish=true`。版本身份校验后复用 `check-dsh-plugin.yml` 的完整矩阵，所有组合成功才进入发布环境。发布负责人须下载本次运行的 `skillhub-dsh-release-candidate`，核对 tarball 与 SHA512SUMS，并关联该候选包的隔离 DSH 验收记录及本次明确授权；配置了审批规则时由审批者核对后批准。缺少验收结果时不继续发布。`publish=false` 和后续 `publish=true` 是两次独立构建，不能仅凭 tag 相同认定包字节相同；如果复用前次验收，须先确认 SHA-512 一致，否则重新验收本次候选包。
4. 发布固定的 Linux/Node 24.2.0 检查产物，不在发布阶段重新构建。候选归档前读取 tarball 内的 `package/package.json`，重新验证包名、版本与 private 条件；不只信任源码 manifest。上传前校验 SHA-512，npm 返回成功后比较 Registry 的 `dist.integrity` 与候选 tarball。npm 会在开放安装前扫描新上传的包，处理期间公开读取可能返回 404，并非单纯客户端缓存。使用 `--prefer-online` 读取，最多读取 40 次、失败后间隔 30 秒，单次 fetch 超时为 10 秒，提供约 20 分钟的等待窗口；不重复执行 `npm publish`。npm 处理可能超过该窗口，参见 [官方发布扫描说明](https://github.blog/changelog/2026-07-28-npm-publish-time-malware-scanning-and-dual-use-metadata/)。这些是读取重试参数，不是 npm 进程的严格墙钟时限。上传成功与公开包验证成功分别记录，超过验证期限仍须报告未完成。

预发行版本使用 npm `next`，正式版本使用 `latest`。版本已经存在或发布失败时工作流失败，不把错误当成成功；排查后按实际 Registry 状态处理，不覆盖已发布版本。该工作流不创建 GitHub Release、不部署服务端；发布后仍须按下节在干净 DSH 环境安装验收，完整性相同不能替代功能验收。

实现依据：[GitHub 可复用工作流](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows)、[npm publish](https://docs.npmjs.com/cli/v11/commands/npm-publish/)。

### 4.3 发布说明

`0.6.0` 的变更说明见 [release-notes.md](release-notes.md)。正式版本发布前按最终候选及验收结果更新，不沿用开发版本的支持声明。

每个版本包含：

- 插件版本、支持的 DSH 发行版与操作系统。
- 必需 Registry API 与可选能力；已验证的开源/SaaS 组合。
- 功能变化、配置变化和升级注意事项。
- 安装、升级、禁用及恢复方法。
- 已知限制，特别是匿名访问限制、发现状态与历史操作保留规则。

### 4.4 发布后检查

从 npm 下载实际发布包，在干净环境重复安装检查；确认其内容与候选 tarball 一致。验证宿主能加载 Host/Client、打开广场、执行搜索工具并安装一个测试技能。发布成功不以 npm 接受上传作为唯一判定。

## 5. 更新、回退与故障处理

更新前保存原候选 tarball 和校验值，停止新操作并完成待恢复事务。停止当前 Profile 的 Host，执行：

```sh
dsh plugin --profile skillhub-test add /absolute/path/to/target-candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

保持原 Profile、来源、会话和模型配置。回退时目标为已留存且与日志格式兼容的包。当前日志支持可选 `existingInstallation` 只读基线，新构建可读取不含该字段的旧日志，但旧构建的严格解析器可能拒绝新增字段；完成过幂等安装后，不得直接回退到不识别该字段的包，也不能删除字段或日志来绕过检查。重新连接后检查侧栏、配置、已安装记录和原操作查询；不要把删除数据目录作为升级或回退步骤。

浏览器仍显示旧页面时，使用有效入口重新进入并强制刷新（Windows：Ctrl+Shift+R）。这只刷新浏览器资源；Host 依赖变化必须完整重启进程，不能只开关插件。官方 RC.2 存在同端口 Host 重启后原标签页空白的已知限制；保留原操作记录，重新进入，不以重复安装修复页面。详细边界见 [兼容设计](compatibility-design.md)。


| 情况 | 处理方式 | 数据保护 |
| --- | --- | --- |
| 插件加载或 UI 故障 | 停止新写操作，禁用或安装上一兼容版本 | 不删除已安装技能与操作日志 |
| 文件事务中断 | 按操作日志和实际 inventory 核验恢复 | 不用重装插件替代事务恢复 |
| Registry 新接口异常 | 回退服务版本或关闭新增能力 | 保留旧 API；已有插件能继续基本功能 |
| SaaS 未同步某可选能力 | 隐藏入口或显示不可用 | 不伪装空列表，不回退到不受支持接口 |
| 日志格式不兼容旧插件 | 禁止直接降级至不能识别日志的版本 | 先完成恢复或使用兼容修复版本 |

服务回退须评估数据库迁移的可逆性，不能仅切换镜像而忽略 schema。包版本回退与技能版本降级是不同操作，首期不提供技能降级功能。

## 6. 发布交付清单

发布前应具备插件 README、兼容矩阵、Registry 契约、测试报告、包清单、配置说明、变更说明和回退步骤。操作人按已批准的环境与版本执行发布；验证、包上传、服务部署和发布后检查分别记录结果。


精确发行版支持范围、依赖锁定、能力检测及平台验收遵循 [DSH 兼容性设计](compatibility-design.md)。
