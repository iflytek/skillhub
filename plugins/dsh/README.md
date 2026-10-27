# SkillHub for DeepSeek Harness

[English](README.en.md)

SkillHub 的 DSH 原生插件。在 DSH 侧栏打开 **技能中心**，匿名浏览、搜索、查看技能详情，并管理安装、升级和卸载；对话中也可搜索和明确请求安装技能。插件内置本地安装核心，无需另装 SkillHub CLI。

`0.2.0` 新增来源可核验的搜索安装状态：广场卡片和对话工具可识别当前来源的受管理安装版本、用户/项目范围及异常记录；对话保留查询时快照。未发现本来源记录不代表其他市场或手工安装不存在，文件存在也不代表 DSH 已启用该技能。按来源、命名空间和技能标识匹配；不按同名目录认领其他来源技能。

`0.2.1` 缺省连接 `https://skill.xfyun.cn`；显式空串或仅空白仍表示断开，已有自定义地址保留。插件不继承 CLI 的默认地址或凭证。

`0.4.0` 使用独立标签 `dsh-v0.4.0`，提供技能案例与原生输入框衔接、对话管理已安装技能及按安装位置独立维护版本。保留 `0.3.1` 的双 RC 兼容范围。公开可用状态以 npm 为准；本地构建的 tarball 可按下文独立验证。

`0.5.0` 新增技能卡片及对话中的只读可用性诊断，分别报告文件完整性、宿主发现和调用策略，保留 `0.4.0` 的兼容范围及库存格式。

## 使用已安装技能

在已安装卡片、已安装版本详情或安装成功结果点击“使用此技能”。技能中心入口在同一工作区新建对话，对话内入口保留当前会话。核验所选安装后，案例显示在原生输入框上方；确认案例或自定义任务后，带入可编辑的技能调用。原会话草稿不会被覆盖，对话内操作保留已有文字、引用和附件。补充任务和材料后自行发送；点击使用不会自动发送、调用模型或安装文件。

需要当前活动会话。被项目同名技能覆盖、本地文件变更、禁止用户调用或会话/来源变化时会提示原因；历史回执不能代替当前安装状态。此功能自 `0.4.0` 起提供。


## 环境与验证范围

| 项目 | 要求或范围 |
| --- | --- |
| DSH | `0.2.0-rc.2` 或 `0.2.1-rc.2`；Host、app-boot 与注入 Client 模块必须属于同一精确发行版 |
| Cordis | `0.2.0-rc.2` 保留以下两组完整组合；`0.2.1-rc.2` 仅使用 `4.0.5 / 1.0.6 / 1.0.10 / 1.1.7` |
| Host Node | `>=24.2.0 <25`；不能使用 Node 24.0.0 |
| 开发包管理器 | Bun `1.3.13`，按锁文件安装 |
| 当前 UI | 中文；支持 DSH 深浅主题和窄屏 |
| 已验证环境 | Linux 两版 DSH Host 严格安装、原生探针及浏览器用户/项目安装、升级、卸载与取消；Windows 浏览器访问真实 Host 的界面和交互 |
| 验证边界 | 功能实现已进行真实模型对话及跨平台 CI；构建产物与公开包的验收分别记录。真实桌面客户端、原生 Windows/macOS 人工交互和 SaaS 验收不由这些结果替代 |

Windows 浏览器验证不等于 Windows 原生 Host 的全部文件操作已经验证。CI 的平台矩阵也不替代真实桌面、模型及目标实例验收。完整支持边界见仓库内的 [兼容设计](../../openspec/changes/add-dsh-native-plugin/compatibility-design.md) 和 [验收标准](../../openspec/changes/add-dsh-native-plugin/acceptance.md)。

`0.3.1` 包含已合并的 [Issue #981 兼容修复](https://github.com/iflytek/skillhub/pull/988)；已发布的 `0.3.0` 不包含此修复。其他 RC、未来发行版、混装模块和 Host/Client 版本不一致均拒绝新写入。可选 `userQuestions` 按宿主服务是否可用启用，缺失时仍回退插件确认框；其版本不属于必需写入门禁。两个已列 RC 的问答 API 一致，peer 声明允许二者，不据此承诺其他版本兼容。

## 构建与打包

在仓库根目录执行：

```sh
cd cli
bun install --frozen-lockfile
cd ../plugins/dsh
bun install --frozen-lockfile
bun run check
npm pack --dry-run --ignore-scripts
```

`dsh-typert-generator` 固定在 `0.2.0-rc.2` 以生成两个宿主共用的 RPC 契约；其他 DSH 开发依赖按 `0.2.1-rc.2` 类型检查。生成器版本不是宿主版本绕过条件。

`check` 依次构建 Host、Client、Remote 绑定与类型声明，执行 typecheck、lint 和测试。插件 lint 使用 `cli/` 中锁定的工具。Bun 仅用于开发构建，包内 Host 在 Node 上运行；React/React DOM 由 DSH 提供。

检查通过后，将候选包保存到仓库外的独立目录：

```sh
npm pack --ignore-scripts --pack-destination /absolute/path/to/candidate-output
```

输出目录须先创建。`--dry-run` 不生成 tarball；实际安装使用生成的 `.tgz`。保留每次候选包及其校验值，不覆盖已经用于验收或回退的包。

本版沿用以下完整组合，不接受交叉混装或未列出的补丁版本。DSH 主包中的 `~` 依赖不会因主包版本固定而自动锁定；以实际宿主解析结果为准。

| 组合 | Cordis | Loader | Include | Timer |
| --- | --- | --- | --- | --- |
| 保留基线 | `4.0.4` | `1.0.5` | `1.0.9` | `1.1.6` |
| 新补丁组合 | `4.0.5` | `1.0.6` | `1.0.10` | `1.1.7` |

已发布的 `0.1.0` 尚不包含新补丁组合与历史回执修复；它仍要求原 Cordis `4.0.4` 基线。插件仅从核验后的当前上下文读取历史回执对应的库存，不恢复旧安装引用或计划的写授权。

## 从 npm 安装

先查询 npm 上的精确版本，确认可用后安装。核对 DSH、Node 和上表完整 Cordis 组合，停止目标 Web Profile 的 Host，再执行插件安装；也可按下一节安装已验证的本地 tarball：

```sh
npm view @astron-team/skillhub-dsh@0.5.0 version
dsh plugin --profile skillhub-test add @astron-team/skillhub-dsh@0.5.0 --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

`skillhub-test` 替换为实际 Web Profile；首次创建方法见下一节。未配置 `registryUrl` 时默认使用 `https://skill.xfyun.cn`。如需自建实例，在插件设置中修改来源并测试、保存；显式保存空地址可断开。npm 查询或下载失败与插件加载失败应分别诊断。

## 从旧版本升级

`0.4.0` 使用按安装位置记录版本的库存 v2。插件仍内置安装核心，无需为了使用插件单独安装 CLI；如果同一用户还使用 SkillHub CLI 管理这些技能，先将这些 CLI 升级到 `0.2.0`，再升级插件，并停止旧版 CLI 的定时或后台写入。先用 `npm view @astron-team/skillhub@0.2.0 version` 确认可用，再使用 `npm install -g @astron-team/skillhub@0.2.0`，确认 `skillhub version`，再执行上面的精确插件版本安装命令。

读取旧库存不迁移；首次写入 v2 前，在库存同目录保存经字节核对的 `inventory.v1.<sha256>.backup.json`。同一技能的用户与项目副本可保留不同版本。旧 CLI 与 v2 库存混写不受支持；已分位置更新后，不能只恢复旧库存文件或降级插件来回退，必须同时核对实际技能文件、安装元数据和操作记录。

案例由作者随技能包提供固定文件 `.skillhub/examples.json`，安装后保持相同位置，跟随技能版本和指纹，不创建另一个全局案例副本。无案例时使用自定义任务；无效声明按普通文件保留，不提供错误的案例入口。

## 安装本地候选包

以下命令针对上述 DSH Web 发行版。`skillhub-test` 是示例 Profile 名称，请始终使用同一 Profile 和 DSH home 安装、启动和维护。首次测试应使用独立测试账户或容器、工作区和测试技能；仅创建 Profile 或更改 `DSH_HOME` 不能隔离共享技能 inventory。

新建 Web Profile，仅首次执行：

```sh
dsh --profile skillhub-test --from-default-profile web --dump-config
```

停止使用该 Profile 的 Host，再安装本地包并启动：

```sh
dsh plugin --profile skillhub-test add /absolute/path/to/candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

打开 Host 输出的本机访问入口。入口可能含访问凭证，不要复制到报告、截图或提交中。示例绑定回环地址；远程访问需按环境既有访问控制部署，不直接公开 Host。

已有 Web Profile 不重复创建。`plugin add` 自动创建的基础 Profile 不等同于 Web Profile。若 `dsh` 不在 PATH，可用已安装发行版的 `node /absolute/path/to/@deepseek-ai/dsh/lib/bin.js` 替换命令中的 `dsh`。

Loader entry ID 为 `astron-skillhub`；`skillhub` 是 Host/Remote 服务名。手写配置 patch 必须定位正确包的 Loader ID，不能修改其他插件的同名入口。

## 配置技能来源

进入 DSH **Plugins/插件 → @astron-team/skillhub-dsh → SkillHub 设置**。

1. 在“技能来源”填写 **Registry 地址**，使用实例的 HTTP(S) 基础地址，可包含部署子路径；不要填写 API 路由、凭证、查询参数或片段。
2. 点击“测试连接”，使用当前草稿地址测试，不保存配置。结果显示公开目录可读或仅支持基础搜索及耗时；最长等待 10 秒，失败或超时可重试。修改地址后清除旧测试结果，连接成功不代表技能下载或所有可选接口可用。
3. 在“详情标签”设置版本、安全审核及评论的显示方式。
4. 点击“保存配置”，看到“配置已保存”后进入侧栏“技能中心”搜索公开测试技能。

“已配置”只表示 Host 已保存非空来源，不代表连通测试成功。空地址表示断开来源；“重置修改”恢复当前 Host 配置快照，不发起网络重载或保存。只读连接不可编辑。若保存发生 revision 冲突，先重置修改，再基于当前值编辑。

| 字段 | 行为 |
| --- | --- |
| `registryUrl` | Registry 基础地址；缺省为 `https://skill.xfyun.cn`；显式空串或仅空白断开，不回退；自定义值保留 |
| `detailTabs.versions` | 版本历史显示策略 |
| `detailTabs.securityAudit` | 安全审核显示策略 |
| `detailTabs.reviews` | 只读评论显示策略 |

三项策略均支持 `auto`（自动检测）、`show`（显示）、`hide`（隐藏）。自动检测按实际匿名读取能力决定展示；显示不会放开服务端权限；隐藏不请求对应模块。当前审核接口不允许匿名读取，不能把不可访问显示为审核通过。未上线评论的 SaaS 可先隐藏评论。

## 使用流程

- **浏览技能**：搜索、服务端排序、真实标签筛选、点击加载更多。新目录接口每批 12 项；旧来源首批 404/405 可回退受限搜索，明确提示不支持分页和排序。选中标签失败不会退回未过滤结果。
- **技能详情**：查看文档、版本和可选模块；可“查看此版本”后确认安装指定的可下载版本。已有其他版本时保留覆盖保护，不执行降级。
- **已安装**：按来源、范围和关键词查看本地记录。详情右侧“版本与更新”提供重新读取及检查更新；升级与卸载只影响选中的一个安装位置，其他位置的版本保持不变。目录缺失时可单独确认清理失效记录。
- **对话搜索**：`astron_skillhub_search` 只搜索，不安装或执行技能。结果提供明确详情动作；详情抽屉可确认安装。
- **对话安装**：用户明确要求安装唯一搜索结果时，不必先打开详情。`astron_skillhub_install` 核验搜索时的精确版本和快照；接受 `selectionRef` 或完整坐标，二者互斥，范围为 `user`/`project`。过期或歧义引用拒绝执行，不改装当前 latest。

本地操作需要活动会话，Host 据此解析可信用户目录和工作区。远程 Host 的文件安装在 Host 所在机器，不在浏览器机器。插件不提供登录、评论提交或权限管理。

安装受理、文件完成、DSH 发现及模型实际使用是不同状态。安装卡主区展示结果，展开“安装记录”可查看核验、当前库存和原操作信息。历史完成不代表当前仍安装该版本，也不代表模型已执行。Host 重启后的历史搜索需要主动“重新查看详情”重新核验，不能恢复旧引用的写权限。

## 验证候选包

从 `plugins/dsh` 执行仓库提供的隔离 Host 冒烟脚本：

```sh
node scripts/smoke-native-host.mjs --artifact /absolute/path/to/candidate.tgz --report /absolute/path/to/host-smoke-summary.json --dsh-version 0.2.1-rc.2
```

脚本使用隔离 Web Profile 验证候选安装与 Host 加载；不调用模型，不替代 UI 或完整技能生命周期验收。浏览器和文件事务探针见源码中的 [集成验证指南](test/integration/README.md)。

对公开测试 Registry 执行匿名契约检查：

```sh
bun run probe:registry --registry 'https://registry.example' --namespace 'demo' --slug 'test-skill' --version '1.0.0' --output /absolute/path/to/registry-report.json
```

替换示例地址和精确版本，使用新的输出文件。脚本检查搜索、解析、元信息、说明、可选模块及归档指纹，归档仅在临时目录解包，不安装或执行技能。该探针不替代目录分页、标签、排序和真实 DSH 页面测试，后者须另外验收。必需项失败产生非零退出码；审核 HTTP 成功不等于公开审核 schema 已通过验证。

人工至少核验来源保存/重置、搜索和详情、用户/项目安装、更新及卸载、历史结果、深浅主题与窄屏、失败重试和来源变更保护。模型验收需单独确定模型配置和调用预算，不能以页面测试代替。

## 更新、回退与缓存

1. 停止提交新操作，等待已受理操作结束；待恢复事务先在原兼容环境处理。
2. 保存当前 tarball，停止使用该 Profile 的 Host。
3. 使用上面的 `plugin ... add` 命令安装目标 tarball，保持 Profile、来源、会话和模型配置不变。
4. 重新启动 Host，重新进入其有效访问入口；浏览器仍显示旧 UI 时进行强制刷新（Windows：Ctrl+Shift+R）。
5. 检查技能中心、配置、现有安装记录和历史查询，再执行测试技能操作。

强制刷新只更新浏览器资源，不会重载 Host 的 Node 模块。依赖变化必须完整重启 Host；禁用/启用插件不能代替依赖重载。官方 RC.2 的 Host 同端口重启可能使原标签页空白，不能承诺无缝重连；保留原操作记录，用有效入口重新进入，不重复提交安装。

回退使用已保留、已验证兼容的 tarball，按相同步骤执行。开发候选替换验证不构成跨正式版本日志兼容承诺。未知日志格式、待恢复事务不能通过删除日志或重装包绕过。插件回退与技能版本降级不是同一功能。

临时停用可在 DSH 插件页关闭本包开关；启用后等待“技能中心”恢复。移除包使用：

```sh
dsh plugin --profile skillhub-test remove @astron-team/skillhub-dsh
```

`remove` 不接受 `--ignore-scripts`。禁用或移除插件不等于卸载技能，不删除技能目录、inventory 或操作日志。操作结果未知时查询原请求；需要恢复时使用界面的“核验并恢复”，不要发起新请求替代恢复。

## 服务端同步与发布

插件 Host 调用匿名 Registry HTTP API；本地安装事务在 Host 内完成，不需要 SaaS 新增操作用户文件系统的安装接口。开源先定义和验证契约，再将缺失的路径、字段、匿名策略和测试同步到 SaaS。公开 npm `0.2.0` 已对 `https://skill.xfyun.cn` 完成目录、搜索、文档、版本选择和固定版本项目安装及文件/指纹、库存、DSH 发现核验；各版本的构建产物与公开包分别验收。日期格式等无法可靠排序的非 SemVer 版本继续阻断自动升级，不显示可执行升级入口；这不影响已验证的固定版本首次安装。SaaS 技能的真实模型执行尚未验收。详细要求见 [Registry 契约](../../openspec/changes/add-dsh-native-plugin/registry-api-contract.md)。

插件独立使用 `dsh-v<version>` tag，不复用 CLI 或服务镜像发布。`check-dsh-plugin.yml` 配置 Linux/macOS/Windows × Node 24.2.0/当前 Node 24 的普通干净安装检查，并额外保留旧 Cordis 组合的隔离 Host 回归；`release-dsh-plugin.yml` 手动触发，默认只产出候选包。发布需正式版本、可发布 manifest、候选验收及明确发布授权；配置了环境保护规则时还须完成相应审批。版本更新需显式提交，npm 发布需明确授权后运行专用工作流。

具体部署、发布后检查与回退清单见 [发布指南](../../openspec/changes/add-dsh-native-plugin/release-guide.md)，变化见 [发布说明](../../openspec/changes/add-dsh-native-plugin/release-notes.md)。上述 OpenSpec 与测试链接面向源码仓库，不包含在 npm tarball 中。

### 市场交互与安装管理

市场卡片支持点击进入详情及独立安装、卸载、检查更新/升级操作；多范围记录先选择管理目标。用户明确要求安装后，未指定范围默认当前用户；明确指定当前项目时安装到项目。市场卡片的安装按钮也直接打开确认弹窗；默认预选当前用户，项目级位于高级选项，但不自动提交。用户后续明确范围也可发起新调用。详情隐藏完整首部元数据，保留安全 Markdown；公开读取缓存 60 秒，写入仍重新核验。版本 Tab 请求 `downloadableOnly=true`，需要本次开源后端契约；SaaS 尚需单独同步和验收。

安装确认后弹窗保留并向右展开：左侧固定技能、版本和范围，右侧展示简洁的实际进度与结果；窄屏上下排列。处理中不模拟百分比，关闭窗口不取消已提交的安装。

安装管理以当前库存为准，历史安装成功不代表目前仍已安装。已有记录时默认选中对应范围，并提供卸载入口；“仅此项目”对应项目目录，“所有项目”对应当前 Host 的当前用户级目录。切换范围不会自动安装；卸载后可再次明确安装。

市场卡片、已安装列表和详情中的卸载可直接打开确认弹窗，不再为卸载跳转到另一详情页。确认框保留技能、版本、所选范围和影响说明；打开仅核验，确认才执行。取消与 Esc 不卸载，过期计划和异常安装仍由原事务校验阻断。

切换版本只查看内容，不会自动更新。已安装时选择可核验的较新版本，可“升级到该版本”；同版本不重复升级，旧版本不支持降级，不可排序版本阻断升级。确认框只列出本次选定的安装位置。检查最新版仍可从市场或已安装列表使用；升级原地确认，保留本地修改保护与恢复。


### 对话管理已安装技能

- `astron_skillhub_list_installed`：当前来源在当前用户/项目中的受管理记录，不代表全机技能；文件存在不是内容完整性证明。
- `astron_skillhub_check_update`：以列表中的位置引用检查最新或指定较新版本；失败、无更新和不可升级分别展示。
- `astron_skillhub_upgrade` / `astron_skillhub_uninstall`：必须使用列表中的精确引用，并由 Host 发起真实用户确认。模型不能传路径、确认标志或执行计划。原生问答不可用时返回确认卡；取消不修改文件。
- `astron_skillhub_operation`：以原回执查询本会话操作结果，不重复提交操作；历史成功不代表当前仍安装。

多份安装先选择目标；更新不改变其他位置版本。本地修改、来源/会话变化和失效引用阻止操作。启用逐位置库存后，同机共同管理技能的 CLI 必须同步升级；旧版 CLI 与新库存混用不受支持。


### 技能可用性诊断（0.5.0）

在对话安装列表的技能卡片中选择“更多 → 检查可用性”，检查当前选定位置。也可以在对话中询问“这个技能为什么不能用”，由 `astron_skillhub_check_availability` 返回只读结果。

诊断分别核验本地文件、当前会话加载结果，以及用户/模型调用策略。同名技能生效、本地修改和宿主无法核验会给出不同结论。按技能名查询只说明当前宿主观察到的事实，不证明安装来源或内容完整性。检查不会修改、卸载、启停技能或发送消息；历史结果不保证下一次调用仍然可用。
