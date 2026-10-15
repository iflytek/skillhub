# SkillHub for DeepSeek Harness

插件的 Loader entry ID 为 `astron-skillhub`，手写 patch 必须使用此 ID；`skillhub` 是 Host/Remote 服务名，不能用它代替 Loader ID。已在 RC.2 隔离环境验证与参考包 `@cocofhu/skillhub@0.2.16` 的 Host、浏览器注册共存；不包含参考插件全部业务或双插件模型选工具行为。旧开发 Profile 中如有针对 `id: skillhub` 的手写 patch，需核对其所属包后再修改；不要修改参考插件的 entry。

首期候选 Node 范围为 `>=24.2.0 <25`，包 engines 与 Host 写入守卫执行相同限制。已在 Linux 的 Node 24.2.0 和 24.17.0 验证候选宿主；这不代表所有平台已通过。Node 24.0.0 缺少候选 DSH CLI 使用的 import.meta.main，会静默退出，不能使用。


[English guide](README.en.md)

SkillHub 的 DSH 原生插件，同仓库独立构建。目标功能为匿名浏览、详情、对话搜索与安装，以及本地技能升级和卸载。

当前是 **开发包**（`private: true`）。已实现原生浏览、详情与可选 Tab、配置、对话搜索/安装工具和结果卡片、已安装列表、更新检查、升级、单目标卸载及显式恢复入口。升级和卸载历史独立于安装列表行，刷新后可继续查询原操作。隔离 DSH `0.2.0-rc.2` 已通过多项实机验证，包括卸载 Host 重启恢复和用户级/项目级双目标升级恢复；开源测试 Registry 的匿名接口与原生安装已有验证；真实模型搜索、明确安装、歧义澄清、资源读取、停止及浏览器断线重开已有证据；完整平台和发布验收尚未完成，不声明正式发布支持范围。验证明细见 [OpenSpec 验证记录](../../openspec/changes/add-dsh-native-plugin/verification.md)。

## 开发

首次开发先在仓库 `cli/` 目录执行 `bun install --frozen-lockfile`，安装共享核心的构建依赖；无需全局安装 CLI。然后在本目录执行：

```bash
bun install --frozen-lockfile
bun run check
npm pack --dry-run --ignore-scripts
```

`check` 顺序运行构建、类型检查、lint 和测试。`bun run lint` 复用 `cli/` 中已锁定的 ESLint 及 TypeScript 规则，检查插件源码、构建脚本与测试，排除构建产物和自动生成契约；无需新增 lint 依赖。构建工具使用 Bun，Host 产物面向 Node，Client 产物通过 DSH ModuleLoader 加载。运行时不调用 SkillHub CLI 可执行文件。

检查通过后生成实际候选包；将输出路径替换为已存在的空目录：

```sh
npm pack --ignore-scripts --pack-destination /absolute/path/to/candidate-output
```

每个候选包单独保存，不覆盖已用于验收或回退的 tarball。`--dry-run` 只预览打包内容，不生成安装包。

构建将 `cli/src/integrations/local-skills.ts` 及其依赖打入私有 `lib/local-core.js`，随插件 tarball 分发；其中包含安装、操作日志及恢复入口，不对外发布独立 SDK。核心保留 CLI 的依赖版本，与插件自身的 Zod 版本隔离。构建检查拒绝引入 CLI 命令入口、登录凭证存储及交互依赖。内部类型契约从 `cli/src/integrations/native-contract.d.ts` 生成到插件，不把 CLI 的 schema 库类型带入 Remote。该产物已通过仓库外 Node 安装/恢复测试，并由会话作用域安装 Remote 调用。

Host 在 sessions 依赖就绪时加载内置核心，通过官方 `@deepseek-ai/dsh-home-paths` 解析并规范化 DSH home，在 `<DSH_HOME>/skillhub/operations/` 保存主机身份及操作日志。默认 home 由官方函数解析；该目录不位于技能发现目录内。启动只扫描历史操作，将上次实例遗留的未完成状态显示为待核验，不继续显示为正在执行，也不根据日志路径自动恢复文件；后续执行恢复须重新验证会话及目标归属。依赖卸载或插件禁用后清理本地状态引用，异步初始化不能重新发布已卸载状态。日志损坏时本地操作状态失败，只读目录查询保持可用。会话作用域 `getOperation` 提供操作状态查询；恢复协调器经会话作用域 `recoverOperation` 显式调用，未启用自动启动恢复。

## 开发包安装与维护

先显式设置隔离测试目录的 `DSH_HOME`，下列 `skillhub-test` 为测试 Profile；技能写入测试还需隔离操作系统 home 和工作区，避免复用共享 inventory。当前 DSH 的 `plugin` 命令会初始化不存在的 Profile，即使参数只是 pnpm 帮助查询，也应先设置隔离环境。

```sh
(
probe_root="$(mktemp -d -t skillhub-dsh-XXXXXX)"
mkdir -p "$probe_root/home" "$probe_root/agents" "$probe_root/workspace"
export HOME="$probe_root/home"
export USERPROFILE="$probe_root/home"
export DSH_HOME="$probe_root/dsh"
export DSH_AGENTS_HOME="$probe_root/agents"
cd "$probe_root/workspace"
dsh --profile skillhub-test --from-default-profile web --dump-config
dsh plugin --profile skillhub-test add /absolute/path/to/candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
)
```

以上为 POSIX shell 示例，先确认 `dsh` 可执行文件的绝对路径，避免隔离 HOME 后工具管理器无法定位原安装。`--from-default-profile web` 仅用于首次创建未存在的 Profile；已有 Web Profile 不重复执行此步骤。直接 `plugin add` 自动创建的基础 Profile 不能代替 Web Profile。命令中的 tarball 绝对路径需替换为实际测试路径。若 DSH 不在 PATH，将 `dsh` 替换为目标安装中的 `node /absolute/path/to/@deepseek-ai/dsh/lib/bin.js`。这里仅说明候选 DSH Web Profile 的开发操作，不作为 Desktop 或正式 npm 发布指南。

移除插件包前停止新技能操作并处理待恢复事务，再执行：

```sh
dsh plugin --profile skillhub-test remove @astron-team/skillhub-dsh
```

`remove` 不接受 `--ignore-scripts`。已在复制的隔离 Profile 验证包移除、通过 `add` 重装保留的候选包，以及包目录外测试标记保持不变；未将此验证扩展为运行中卸载或跨版本回退。包移除不等于技能卸载，不应删除 DSH home、共享 inventory 或操作日志。跨版本回退前须核对日志兼容性，不能用重装包代替事务恢复。

### 更新开发包与回退

先停止新操作，等待已接受的操作完成，并在原环境处理待恢复事务。保存当前 tarball，停止使用该 Profile 的 DSH Host 后，用同一个 Profile 安装目标包：

```sh
dsh plugin --profile skillhub-test add /absolute/path/to/target-candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

回退时将 target-candidate.tgz 替换为保留的兼容 tarball，仍须先停止 Host。不要覆盖留存包文件，不删除技能、inventory 或操作日志。Loader ID 从旧开发包的 `skillhub` 改为当前 `astron-skillhub`；如有自定义配置 patch，须同步定位到实际包的 ID，不能误改参考插件。

已验证两个不同 `0.0.0-m0` 开发产物之间的替换、重启和回退：用户/项目技能及已完成日志保持不变。另已验证双目标待恢复事务跨两个开发产物回退后显式恢复。两者都不是不同正式版本的兼容承诺，不证明日志格式迁移。未知日志格式会阻断本地操作，不应通过删除日志绕过。

### Host 依赖核验

安装、升级、卸载的新请求和显式恢复写入前，会通过宿主运行时解析服务检查 DSH 主程序、app-boot 和 Host peers，并检查七项声明注入的 Client 模块是否已挂载且所属清单版本匹配。不匹配或无法确认时拒绝写入；原操作查询和原请求回执仍可读取。广场提供核验失败提示和重新核验入口。依赖匹配只是一项必要检查，不代表浏览器实际执行、依赖重载、Node 和所有平台已完成兼容验收。

### DSH 升级后的处理

1. 升级前停止提交新的技能操作，等待执行结束；如有待恢复操作，先在原兼容环境处理并保留操作日志。
2. 宿主依赖发生变更后，完整退出并重新启动使用该 Profile 的 DSH Host。仅禁用/启用 SkillHub 或刷新页面不会清空 Node 已加载模块。
3. 重新连接页面并核验兼容性。当前开发包只接受固定候选依赖组合；换到其他 DSH 版本后即使页面可打开，也不代表允许安装、升级、卸载或恢复写入。
4. 若核验失败，保留安装记录和操作日志，使用与插件匹配的 DSH 发行组合，或等待针对新版本验证的插件包。不要分别升级宿主子包，也不要通过修改包版本字段绕过检查。

“重新核验写入兼容性”只重新检查当前依赖，不更新包、不重启 Host、不清除模块缓存。完整跨发行版升级/回退仍在验收中，不能把本流程当作已完成升级兼容验证的声明。

DSH 安装器可能在加载前直接拒绝不匹配的插件，届时不会出现 SkillHub 自身的兼容提示。不要通过版本豁免把当前包当作受支持组合运行。若安装失败后提示 Profile 回滚不完整，按安装器提示修复该 Profile 的依赖；不要删除技能目录或操作日志。已在独立 Alpha.3 环境验证安装拒绝及 `plugin install` 修复，这不代表支持 Alpha.3。

## 持续集成

`.github/workflows/check-dsh-plugin.yml` 在涉及 `plugins/dsh/`、`cli/` 或该工作流的 PR 中执行，也支持手动运行和 `dsh-v*` tag 推送。tag 构建先核对 tag 与本包 version 一致，再安装 CLI 与插件的锁定开发依赖，执行共享 CLI 全量检查和插件 `check`，保存开发 tarball。工作流仅授予只读仓库权限，不上传 npm、不创建 Release、不部署服务。创建 tag 本身仍须经过发布授权，工作流配置不代表已发布。

工作流配置 Linux、macOS、Windows × Node 24.2.0 / 当前 Node 24 构建测试矩阵，各平台独立保存 tarball；一个平台失败不会取消其他平台检查。当前已有 Linux 和 Windows 的分项本地验证，远端矩阵尚未执行；macOS 按计划延后，不能列为已支持平台。真实 DSH 浏览器验收仍按 OpenSpec 单独执行；CI 产物不能直接视为发布验收通过。

## 工程结构

卸载结果中的“核验并恢复”通过会话作用域 `recoverOperation` 核验原操作：提交前可恢复文件，提交后只清理残留。不存在或不属于当前会话的操作不会触发恢复；Host 重新核对当前目标与写入策略。请求失败后查询原操作，不重新卸载。恢复覆盖单目标安装、卸载、失效记录清理，以及用户级/当前项目级完整目标集合升级。升级恢复前必须核验全部历史目标，每一步变更前重新验证完整目标集合；任一目标不属于当前会话上下文则拒绝恢复。启动时不自动恢复。升级和卸载页面均提供恢复入口；已通过隔离 DSH 的页面恢复验证，具体场景及未覆盖范围见验证记录。

| 路径 | 职责 |
| --- | --- |
| `src/host/index.ts` | Cordis 服务与 Remote 方法 |
| `src/host/registry-adapter.ts` | 匿名搜索、精确版本解析、固定版本说明读取 |
| `src/host/local-runtime.ts` | 内置核心加载、主机身份、启动日志扫描和依赖清理 |
| `src/client/index.tsx` | DSH 侧栏、浏览页面及 Remote 挂载 |
| `scripts/build.ts` | 官方协议生成器装配、Host/Client 和类型产物构建 |
| `scripts/build-core.ts` | 共享安装核心及自身依赖的独立内部产物构建 |
| `cordis.patch.yml` | Bundle Host 入口 |
| `test/` | 包装配与 Registry 边界行为测试 |

rc.2 的官方生成器要求工作区注册协议声明。构建脚本在临时目录复制已安装的官方声明，限定本插件生成 Host/Remote，结束后清理；不改写协议或向官方包写入插件类型。

Registry 适配器仅发匿名 GET 请求，不读取登录信息。元数据请求不跟随重定向，单次 JSON/说明响应上限为 1 MiB，每次读取具有覆盖响应头与正文的 30 秒期限；超时按可重试网络错误处理，用户主动取消单独识别。归档下载由安装核心处理，不使用此元数据读取期限。

Host 已提供 `getContext`、`listSkills`、`getSkillDetail`、`listVersions` 和 `listReviews` 只读 Remote 方法。详情要求显式版本，先核对解析结果再读取该版本 SKILL.md；说明失败独立反馈，不向 Client 暴露下载地址。这些方法仅用于内容读取。安装另经会话作用域 `listInstallTargets`、`startInstall` 和 `getOperation` 执行；目标引用由 Host 绑定真实会话和可信根，不接受客户端文件路径。版本和评论支持分页，auto 先探测首屏，show 在激活时读取，hide 不请求。审核当前只显示匿名不可访问说明。

配置字段为 `registryUrl` 与 `detailTabs`（`versions`、`securityAudit`、`reviews`，各支持 `auto/show/hide`）；空地址表示尚未配置，不默认请求外部实例。搜索使用上下文返回的 `sourceRevision`，重载后的旧上下文会被拒绝。在 DSH 插件详情页打开本包配置，保存地址和开关。编辑通过宿主配置表单及 revision 校验提交，冲突时重新加载配置再编辑。浏览页支持提交搜索、空结果、重试及固定版本详情；返回时保留查询和位置。已安装入口读取当前来源在当前用户/项目中的本地记录，支持范围筛选、手动刷新和部分读取失败提示。Markdown 不执行 HTML、不加载远程图片，只允许 HTTP(S) 外链。

## 对话搜索

原生工具名为 `astron_skillhub_search`，参数仅为 `q`。它通过同一个 Host 目录服务匿名查询；不执行安装或技能。结果包含来源、时间和 selectionRef，Host 在内存中绑定真实会话、调用 ID、项目目录与来源。最多保留 400 个选择引用，来源变化/重载后失效，不暴露真实项目路径。

工具名已存在时不覆盖其他插件。自定义搜索卡片已接入原生 ToolView，并验证持久化记录的浏览器重放；会话作用域详情读取和展示确认已通过 Host Gateway 验证，Client 详情抽屉已复用广场详情内容，通过保留的会话上下文调用 scoped Remote，并显式注入命名空间。实机验证包含版本/评论切换、展示确认响应、关闭、焦点恢复和重启后旧引用拒绝。打开详情会签发新的固定版本引用；Client 展示确认后，Host 可将原搜索引用解析为已查看的版本、versionId 和 fingerprint，不重新解析 latest。多个已查看内容身份造成歧义时拒绝原搜索引用；会话、项目、来源变化及快照淘汰均拒绝使用。详情抽屉已接入显式安装按钮；搜索和安装工具已通过真实 DSH 工具执行器验证，安装结果卡片已验证成功状态、过期引用失败及同 Host 刷新重放。以上工具与 UI 机制使用本地 HTTP 夹具验证。真实模型已通过只读搜索、明确安装、歧义澄清和夹具资源使用；官方 Stop 的受理前/后边界、安装期间浏览器关闭重开、真实安装历史的失效记录清理已有实机证据。真实 Registry 技能 zero-slop 的模型加载和资源读取也已通过，但成功用例明确提供资源绝对路径，不能据此保证所有技能的相对路径解析。详见 OpenSpec 验收记录。

Host 重启后，旧搜索引用失效。打开历史卡片详情时，可主动点击“重新查看详情”；Host 从当前会话的原搜索日志核验来源与固定版本，返回新详情，并显示原来源和当前来源。此动作不会安装技能。来源、会话或历史记录无法核验时，需要重新搜索；旧引用不会恢复安装权限。

## 对话安装

`astron_skillhub_install` 接收 `selectionRef` 或完整 `namespace`/`slug`，两者互斥；可选 `scope` 为 `user`/`project`，默认 `user`。只在用户明确要求安装时调用，历史搜索选择使用原引用，不将过期引用改成新坐标。文件路径、来源、目标令牌和请求键由 Host 管理。

成功工具输出只保存 `accepted` 与原操作身份，结果卡片通过会话作用域查询后续结果。查询复用详情页缓存；页面隐藏暂停轮询，终态、错误和记录缺失停止自动轮询。历史卡片不重新安装，未知格式保守展示。卡片支持复制操作 ID，并在来源和会话匹配时进入已安装列表。失败原因按受限代码分类，过期选择已通过实机验证，其他失败分支仍需补验。

## 临时禁用与重新启用插件

先等待已受理的技能操作结束或完成恢复，再打开 DSH 的“Plugins/插件”，选择 `@astron-team/skillhub-dsh`，关闭启用开关。等待开关显示关闭、SkillHub 入口消失后再进行下一步。禁用不会卸载技能或清除 inventory、操作日志；不要通过删除数据目录实现禁用。

重新打开同一开关后，等待 SkillHub 入口恢复，再进入广场检查加载情况。已有隔离 RC.2 Host/浏览器验证两轮禁用和启用，参考插件的工具与视图保持不变。若修改了宿主依赖，仍须完整重启 Host；开关不能代替依赖重载。禁用期间已安装技能仍可能被 DSH 发现，停止使用某技能应通过“已安装”执行精确卸载。

## 检查更新

在已安装详情中主动点击“检查更新”，插件读取当前来源的发布元数据，比较已安装版本及记录指纹，不下载技能包、不修改本地技能，也不自动检查全部条目。检查前后重新核对本地记录；记录变化时要求刷新列表。同版本内容变化、版本顺序未知、远端版本更旧或缺少指纹时不提供可升级结论。版本及记录指纹一致不代表本地文件完整性已验证。

当前已接入更新检查接口和详情按钮，自动化测试覆盖版本判断与 Host 编排；隔离 DSH 已验证相同版本/指纹、新版本、旧版本、同版本内容变化和未知版本顺序的 Gateway 与浏览器结果，打开详情不自动检查、一次点击仅请求一次。真实 Registry 联调尚未完成。可升级结果提供升级计划与确认执行入口。

升级准备接口 `prepareUpgrade` 已接入会话作用域：只读核验完整目标集合与本地基线后固定候选版本，返回计划引用、5 分钟有效期及全部目标清单；来源重载、会话/项目变化或计划过期后不可继续。已安装详情检查到可更新后可生成计划，界面展示来源、版本变化、全部位置和有效期，提供确认/取消；单目标计划、取消、确认及新版落盘已在隔离 DSH 验证。

内部核心已接入升级操作的持久接受和多目标执行：下载前及目标锁内核对已确认快照，复用安装事务日志、备份替换与恢复，拒绝本地内容和元数据变化。LocalRuntime 跟踪接受后的执行，关闭时等待其结束；持久重试不重新读取过期计划或重复执行。Host 的会话作用域 `executePlan` 仅接收 `planId` 和 `requestKey`，每次文件变更重新核验会话、文件系统、规范化目录和宿主写策略。当前通过核心、运行时和 Host 编排测试；隔离 DSH 已验证单目标升级及用户级/项目级双目标失败恢复，单项目目标升级在 Host 正常终止后重启、再从历史恢复也已验证；用户/项目双目标升级的重启、强制终止及页面恢复已有 Linux/Windows 隔离场景证据，详见 verification.md；未声明 macOS 或所有平台故障组合通过。

确认前先将计划引用、主机身份和请求键保存到 sessionStorage；保存失败不发送请求。响应不确定时保留原参数，通过“查询原升级请求”重新确认，重复点击合并同一次提交。取得操作 ID 后复用操作查询，显示文件结果与独立的 DSH 发现状态；同 Host 下接受响应丢失、刷新后从新版本详情查询原请求已通过浏览器验证；未知和待恢复结果不能清除。

## 卸载与恢复

在“已安装”中选择用户或项目范围，打开目标技能详情并发起卸载。确认框显示版本、来源、精确安装位置和计划有效期，默认聚焦“取消”。确认只删除选定位置的技能文件及对应记录，其他安装位置和远端技能不受影响；不会终止正在运行的任务。同名技能仍可能从其他位置加载。

目录已经缺失时，界面改为“仅清理失效记录”，明确确认后只清理记录。计划过期、来源或目标发生变化时，取消并重新核验，不继续使用原确认。

提交前保存原请求。响应丢失时点击“查询原卸载请求”，不要重新发起卸载；记录从列表消失后，仍可在“卸载操作”区域查询结果。同一标签页刷新会恢复该入口，关闭浏览器后的请求入口恢复尚未保证。

出现待恢复或清理告警时，使用“核验并恢复”：Host 核验原操作，提交前恢复原文件，提交后仅清理残留。依赖核验不通过时禁止恢复写入，原结果仍可查询。未核验的结果不能关闭；恢复失败保留操作 ID 供排查，不手工删除未知目录或操作日志。用户级/项目级双目标升级采用同一恢复原则，并逐一核验全部目标。

## 页面安装

打开 DSH 会话后，在 SkillHub 广场详情或对话搜索详情中选择当前项目/用户范围，点击安装。广场通过官方 `session-maybe` 子槽位获取当前会话；未打开会话时允许浏览，安装区域提示先进入会话，不猜测项目目录。

两个入口共用请求缓存和操作查询；同一标签页刷新后从 sessionStorage 恢复原请求，再向 Host 查询实际结果。重复提交和响应不确定时保留原 requestKey，不自动生成新请求。文件安装完成与 DSH 技能发现分开显示，不能将文件完成理解为模型已使用技能。

## 验证边界

### Registry 匿名契约检查

在 `plugins/dsh` 目录运行以下命令，将占位值替换为测试实例地址和已发布、可匿名下载的技能精确版本。需先安装本包及 `cli` 的开发依赖。

```bash
bun run probe:registry --registry '<测试实例地址>' --namespace '<命名空间>' --slug '<技能名称>' --version '<精确版本>' --output /tmp/skillhub-registry-report.json
```

脚本只发送匿名 GET，不读取登录配置，不执行技能，不修改 DSH 目录。归档解压至独立临时目录，核验 `SKILL.md` 和内容指纹后清理。输出路径必须不存在；每次运行使用新路径，避免覆盖已有证据。

JSON 报告包含检查时间、来源、技能坐标、逐项结果和 `requiredPassed`。搜索、精确版本解析、元数据、说明与归档完整性为必需项；任一失败或被阻塞时退出码为 1。版本列表、评论与安全审核为可选项，失败不会改变必需项结论。安全审核返回 401/403 记为 `anonymous-denied`；返回成功仅记为 `public-response-unvalidated`，不代表审核响应契约已验证。报告不包含下载签名地址或服务端错误正文。

开源和 SaaS 实例分别运行并保存报告，不能以一个实例的结果替代另一个实例。此检查不验证 DSH 加载、安装事务或模型实际使用，仍需完成实机验收。

本地测试涵盖服务注册与卸载、生成 codec、Client factory 的实际依赖加载及 HTTP 边界。另已在隔离的 DSH `0.2.0-rc.2` Web Profile 安装 tarball，并通过原生侧栏打开诊断页面、完成真实 Remote 往返。

`test/local-core-package.test.ts` 实际打包并解压 npm tarball，在仓库外、没有 `node_modules` 的临时目录通过 Node 完成带资源技能安装、操作提交确认和重开日志；测试禁止外部 fetch，使用内存 ZIP 夹具。这项验证证明内部核心可独立运行，不代表 DSH 的安装入口或真实 Registry 联调已完成。

隔离 Host 探针另已验证会话 cwd、用户/项目技能发现、资源目录、项目隔离、文件新增/移除后的发现更新，以及只读测试工具的 Agent/callId 传递。复跑方法见 [实机探针](test/integration/README.md)。

原生配置页保存及 Host 重启后的值回显已在隔离 Profile 验证。对话卡片、详情抽屉安装、广场安装幂等及刷新后的原操作查询已在 HTTP 夹具下验证；真实开源 Registry 的匿名接口、原生安装及已安装技能模型使用已有独立证据。完整平台、远程 Host 和发布验收仍待完成，因此本指南限于开发候选包，不声明正式支持基线。

正式需求、架构、验收和发布步骤见 [OpenSpec](../../openspec/changes/add-dsh-native-plugin/README.md)。

隔离 DSH 已完成单目标升级的计划、取消、确认、列表刷新与实际新版资源落盘验证。成功后的详情显示新版本，并提供查看升级后详情入口。已接受请求的响应丢失与刷新恢复已通过；单目标升级 Host 重启恢复和双目标部分执行后的浏览器恢复已通过，提交前断线及双目标 Host 重启已有隔离场景证据，其他平台及完整模型验收仍待完成；当前不能将文件升级成功视为 DSH 已发现或模型已使用新版。

### 可复跑 Host 验收

升级候选保护、升级期间文件变化、计划过期/会话隔离及切换来源后的失效，可通过仓库中的 `scripts/probe-host.mjs` 顺序执行。入口管理本地 Registry、包外观察器、宿主进程和独立报告，无需个人临时协调脚本。准备隔离 Profile、命令和证据边界见 [实机探针指南](test/integration/README.md#可复跑的-host-计划与升级验收)。


### 已接受操作的失败诊断

安装、升级或卸载返回 accepted 后，后台执行失败会在 Host 标准错误日志输出 `SkillHub accepted operation failed`，包含 `operationId`、`kind` 和白名单 `code`。可按界面中的操作编号关联持久操作记录。分类包括已识别的文件系统/网络错误码及共享核心的错误类别；`UNCLASSIFIED` 表示未能安全分类，不能据此判断具体原因。日志不输出异常原文、堆栈、文件路径、Registry 凭证或请求参数。

诊断不代替操作日志，也不会自动重试。终态和恢复要求仍以原操作查询为准；进程崩溃或日志未保留时，不保证该诊断可用。新诊断不能补回旧版本已经丢失的异常信息。
