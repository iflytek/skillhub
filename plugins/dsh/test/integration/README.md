# DSH 实机探针

`discovery-probe.mjs` 是由真实 DSH 加载的测试插件，不包含在发布包中，也不由 `bun test` 自动执行。它不调用模型，只在临时目录创建测试技能、工作区和空会话。

覆盖：

- 从宿主解析的 Agent/Session 取得项目 cwd。
- 自定义 DSH_HOME 下的用户技能、当前项目技能和资源目录。
- 项目隔离、顶层注册表与 Agent 作用域差异。
- 同一运行实例中的新增和移除发现。
- 官方工具注册、执行时的 Agent 与 callId 传递。
- 原生内部协调器接受请求、下载真实 ZIP、校验并安装、DSH 发现与资源读取、重复请求和操作归属。
- 真实 typertGateway 调用 startInstall/getOperation，检查原始请求多余字段拒绝、同 key 参数冲突、Host 身份不匹配及跨会话查询隔离。
- 提交前注入中断并保留暂存目录，实际会话只读策略拒绝恢复，恢复可写策略后清理暂存并持久化 restored；跨会话拒绝。

## 运行条件

使用临时根目录，内部已有 `node_modules/@deepseek-ai/dsh` 的精确 `0.2.0-rc.2` 或 `0.2.1-rc.2` 发行包。探针验证 DSH_HOME、DSH_AGENTS_HOME 和 HOME 均指向该临时根下的专用目录，拒绝写入真实用户安装清单。需先安装当前插件 tarball、将 registryUrl 配置为 `http://127.0.0.1:43219`，并在单独终端从仓库根运行 `node plugins/dsh/test/integration/catalog-fixture.mjs`。夹具使用 CLI 开发依赖生成 ZIP，需预先安装 CLI 依赖。

从仓库根目录准备一个新的环境：

```bash
probe_root=$(mktemp -d -t skillhub-dsh-probe-XXXXXX)
mkdir -p "$probe_root/native-install-home" "$probe_root/agents"
probe_module="$probe_root/discovery-probe.mjs"
cp "$PWD/plugins/dsh/test/integration/discovery-probe.mjs" "$probe_module"
printf '{"private":true,"type":"module"}\n' > "$probe_root/package.json"
pnpm --dir "$probe_root" add --ignore-scripts --save-exact @deepseek-ai/dsh@0.2.0-rc.2
```

使用以下环境变量启动和初始化临时 Profile；这些变量只传给该次命令：

```bash
HOME="$probe_root/native-install-home" USERPROFILE="$probe_root/native-install-home" DSH_HOME="$probe_root/home" DSH_AGENTS_HOME="$probe_root/agents" DSH_TELEMETRY_DISABLED=1 \
  node "$probe_root/node_modules/@deepseek-ai/dsh/lib/bin.js" \
  --profile skillhub-m0 --from-default-profile web --dump-config > "$probe_root/composed.yml"
```

初始化 Profile 后安装已构建的候选 tarball（将占位路径替换为实际文件）：

```sh
HOME="$probe_root/native-install-home" USERPROFILE="$probe_root/native-install-home" DSH_HOME="$probe_root/home" DSH_AGENTS_HOME="$probe_root/agents" DSH_TELEMETRY_DISABLED=1 \
  node "$probe_root/node_modules/@deepseek-ai/dsh/lib/bin.js" \
  plugin --profile skillhub-m0 add /absolute/path/to/candidate.tgz --ignore-scripts
```

向这个新 Profile 的 `cordis.patch.yml` 追加测试插件及配置。以下命令将实际路径编码成合法 YAML 字符串，不依赖当前目录：

```bash
node --input-type=module - "$probe_root" "$probe_module" <<'JS'
import { appendFile } from 'node:fs/promises';
import { join } from 'node:path';
const [root, module] = process.argv.slice(2);
await appendFile(join(root, 'home/profiles/skillhub-m0/cordis.patch.yml'),
  `\n- id: workspace-controller\n  config:\n    documentsDirectory: ${JSON.stringify(join(root, 'documents'))}\n` +
  `- id: astron-skillhub\n  config:\n    registryUrl: http://127.0.0.1:43219\n` +
  `- insert:\n    - id: skillhub-discovery-probe\n      name: ${JSON.stringify(module)}\n      config:\n        root: ${JSON.stringify(root)}\n`);
JS
HOME="$probe_root/native-install-home" USERPROFILE="$probe_root/native-install-home" DSH_HOME="$probe_root/home" DSH_AGENTS_HOME="$probe_root/agents" DSH_TELEMETRY_DISABLED=1 \
  node "$probe_root/node_modules/@deepseek-ai/dsh/lib/bin.js" \
  --profile skillhub-m0 --host 127.0.0.1 --port 0 --no-open > "$probe_root/host.log" 2>&1
```

Host 保持前台运行。在另一个终端查看临时根下的 `discovery-report.json`：`passed: true` 且各断言为 true 表示通过。无报告、`passed: false` 或进程启动失败均不算通过。结束后用 Ctrl+C 关闭本次 Host。`host.log` 含临时浏览器访问令牌，不上传或直接粘贴日志。

此探针覆盖内部协调器的成功安装路径和一处提交前中断恢复。nativeRecoveryCompleted 为 true 才证明实际执行过恢复变更。它不能证明页面按钮、对话安装卡片、模型调用、Host 崩溃重启、自动启动恢复、远程 Host 或其他操作系统已经通过验收；相关测试需独立完成。

## 本地核心启动与主机身份

`local-runtime-probe.mjs` 验证原生 Host 的内置核心初始化、官方 DSH home 下的持久身份以及待恢复记录数量。将探针文件复制到隔离根目录，再作为测试插件加载，配置 `root` 指向同一隔离根。不要直接从本包目录同时加载多个探针：DSH 会把它们识别为同一 Client 包的多个 Loader 来源而拒绝启动。

报告写入 `<probe_root>/local-runtime-report.json`。必须同时确认 Host Web 启动成功及进程仍在运行；单独看到报告通过不足以证明整机启动成功。重启前保存本轮报告，再比较新报告的 hostInstanceId。报告仅证明状态存储与只读扫描，不证明历史操作已执行恢复或安装按钮已可用。

## 浏览与固定版本详情

`catalog-fixture.mjs` 在 `127.0.0.1:43219` 提供 HTTP 夹具，仅用于隔离 Profile。它包含一条固定版本技能、空搜索结果、不安全 Markdown 样本以及带资源文件的 ZIP 下载。目录读取和安装使用同一个夹具，不构成真实 Registry 验证。

1. 启动夹具：`node test/integration/catalog-fixture.mjs`。
2. 将本次 tarball 安装到隔离 DSH，在原生插件配置页把 Registry 设为 `http://127.0.0.1:43219`。
3. 安装 Playwright 浏览器（`bun x playwright install chromium`），或通过 `DSH_PROBE_CHROMIUM` 指定本机可运行的 Chromium。
4. 通过环境变量 `DSH_PROBE_URL` 传入隔离 DSH 的认证入口地址，`DSH_PROBE_OUTPUT` 指向已存在的测试输出目录。不要把带认证参数的 URL 写入仓库或日志。
5. 执行 `node test/integration/catalog-browser-probe.mjs`。探针处理隔离 Profile 的 Configure later 页面，不调用模型；将版本/评论设为 auto、审核设为 show；验证搜索、固定版本说明、可选 Tab 读取、匿名审核提示、危险内容处理、返回后的查询和焦点、空结果和清空。配置修改仅作用于隔离 Profile。

浏览器结果及截图写入输出目录。此探针需要一个已完成首次 Preview Notice 确认、可显示 Configure later 的独立 Profile。不要对日常使用的 DSH Profile 执行。


当隔离 Profile 加载了本插件的 `astron_skillhub_search`，发现探针额外调用真实工具执行器验证搜索、展示元数据、额外参数拒绝和缺失 Agent 拒绝。此模式需先启动 catalog-fixture，并将隔离 Profile 的 Registry 配置为其地址。结果中的 `nativeSkillHubSearch: true` 才能证明业务搜索工具执行过；该字段为 false 时只完成原发现探针。


## 原生搜索卡片重放

设置 `DSH_INSTALL_PROBE=1` 时，搜索卡片浏览器探针额外在详情抽屉点击“安装到当前项目”，等待文件安装结果，关闭并重新打开后检查原操作仍显示且 startInstall 请求总数为 1；随后刷新浏览器，重新打开详情，等待 getOperation 响应并验证结果，提交次数仍须为 1。此模式会向隔离 Profile 的 project 工作区写入夹具技能，必须与发现探针相同地隔离 HOME 和 DSH 数据目录；默认不执行安装。installationVerified/reloadInstallationVerified 分别标记安装交互和同 Host、同标签页刷新恢复通过，不能据此声明 Host 重启后的历史恢复已验证。

发现探针在独立测试 Session 中记录真实搜索工具输出，并补充明确标注为 test-fixture 的合成 Assistant/流事件，不请求模型。报告中的 searchFixtureTitle 是对应会话标题。

沿用浏览器探针的环境变量，额外设置 `DSH_SEARCH_FIXTURE_TITLE` 为报告中的标题，执行 `node test/integration/search-card-probe.mjs`。它打开该会话，展开宿主默认折叠的回合过程及 Called tools 分组，检查 SkillHub 卡片，再刷新浏览器验证历史重放。不要通过点击仅供读屏的 Completed 状态文本展开回合；探针使用宿主实际的过程按钮。探针还打开详情抽屉，校验真实展示确认响应、版本/评论内容、Escape 和关闭按钮、焦点恢复。可选设置 `DSH_STALE_FIXTURE_TITLE` 为之前 Host 运行期间生成的夹具会话标题，验证重启后旧引用被拒绝；未设置时结果 `staleHistoryRejected` 为 false，不代表此项已验证。此测试不证明模型自主调用、固定版本引用提升或安装流程已完成。


发现探针还通过真实 typertGateway 调用会话作用域详情方法，验证固定版本读取、跨会话拒绝、伪造 detailToken 拒绝和匹配的展示确认。结果 `scopedConversationDetail: true` 是该链路证据；它仅证明 Host 链路，不替代上述浏览器抽屉验证，也不构成安装授权。

### 广场与对话安装共享验证

在 `DSH_INSTALL_PROBE=1` 的基础上设置 `DSH_PLAZA_INSTALL_PROBE=1`，同一浏览器探针先从广场详情提交安装，再返回会话检查详情抽屉复用结果，并刷新页面查询原操作。输出 `plaza-install.png` 与报告字段 `plazaInstallationVerified`；断言全流程仅一次 `startInstall`。

已有技能可能返回“此版本已安装”。该结果验证幂等和跨入口共享，不证明首次下载落盘；首次安装验收须使用新的隔离项目，不能删除未知目录来清空状态。

### 原生安装工具探针

发现探针通过真实 `tools.execute` 调用 `astron_skillhub_install`，在每次新建的隔离项目中安装夹具技能，检查资源文件与 DSH 发现结果。同 callId 原参数重试必须返回原 operationId/接受快照，变更 scope 和额外 path 参数必须拒绝。成功报告包含 `nativeInstallToolCompleted: true`。该项不调用模型，不代表浏览器安装结果卡片或真实 Registry 联调已验收。

### 安装结果 ToolView 浏览器探针

发现探针保存真实安装工具接受结果到独立隔离会话，报告包含 `installFixtureTitle` 和 `installFixtureWorkspace`。浏览器探针 `install-card-probe.mjs` 使用通用 `DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`、可选 `DSH_PROBE_CHROMIUM`，另设置 `DSH_INSTALL_FIXTURE_TITLE`/`DSH_INSTALL_FIXTURE_WORKSPACE` 为这两个值。先展开对应工作区与工具过程，再验证卡片查询 Host 终态、原来源/版本展示和刷新后相同操作 ID；断言没有 startInstall 请求和 pageerror。输出 `install-card-result.json`、`install-tool-card.png` 和 `install-tool-card-reloaded.png`。

该会话为保存真实工具结果的合成记录，没有调用模型；本项不覆盖运行中/失败/窄屏状态或宿主重启。

安装失败验证：发现探针另保存过期 selectionRef 的真实 not-confirmed 工具结果，报告字段为 `installFailureFixtureTitle`。设置 `DSH_INSTALL_FAILURE_FIXTURE_TITLE` 后，浏览器探针在成功/刷新验收后打开该会话，检查 CONTEXT_STALE 文案、没有操作 ID/查询按钮以及没有安装提交；输出 `staleSelectionFailure: true` 和 `install-tool-card-failed.png`。其他失败分支仍需分别验收。

已安装验证：发现探针经真实会话 Gateway 调用 listInstalled，确认 read-only 会话可读、本地路径不外泄及其他项目隔离，成功时 `installedProjectionVerified: true`。安装卡片浏览器探针设置 `DSH_INSTALLED_LIST_PROBE=1` 后进入 SkillHub 已安装页，选择当前项目，检查版本/文件状态并刷新，输出 `installedListVerified: true` 与 `installed-list.png`。本项尚未模拟 Registry 离线或损坏本地记录。

已安装详情：Gateway 探针核对 getInstalledDetail 返回固定版本及跨会话拒绝。浏览器已安装模式进入详情、检查正文并返回，断言焦点恢复。在线通过后，仅停止本轮本地 Registry 夹具，保留 DSH Host；额外设置 `DSH_INSTALLED_OFFLINE_PROBE=1` 重跑，检查列表和详情保留本地版本、显示远端不可用。输出 `installedOfflineVerified: true` 和 `installed-detail-offline.png`。不要停止非本轮服务来模拟离线。

卡片操作验证：浏览器探针在隔离浏览器授权剪贴板后点击“复制操作 ID”，读取并比较实际剪贴板内容；已安装模式改由“在已安装中查看”进入列表，不依赖侧栏切换。成功报告包含 `operationIdCopied` 和 `installedNavigationVerified`。这不覆盖拒绝剪贴板权限或来源切换后的导航阻断。

更新检查验证：发现探针在 read-only 会话通过 Gateway 检查已安装夹具，断言相同版本/指纹返回 current，并拒绝跨会话引用。在线已安装浏览器模式额外断言打开详情不发起 checkUpdate；点击“检查更新”后恰好发起一次请求，显示版本及记录指纹一致，输出 `updateCheckVerified: true`。这不证明不同远端版本、真实 Registry、升级执行或文件完整性验收通过。

更新分支矩阵：创建仅供本轮夹具使用的状态文件，初始内容为 `current`；为 Registry 夹具和浏览器探针同时传入 `DSH_FIXTURE_UPDATE_STATE`，值为该文件绝对路径。浏览器依次切换 newer/older/changed/unknown，核对真实 Gateway 响应及页面文案，并在结束时恢复 current。只有未指定版本的 resolve 请求受场景切换影响。成功报告 `updateScenarios` 包含四项；旧版、同版本内容变化和未知顺序仅模拟元数据，1.3.0 的实际归档升级验证见下文。

升级验收：夹具现额外提供精确版本 1.3.0 及不同资源内容的 ZIP/指纹；旧版仍为 1.2.0。启动发现探针时设置 `DSH_UPGRADE_FIXTURE=1`，工具安装使用独立随机 namespace，避免常规安装探针的跨项目共享记录阻断升级。浏览器开启 `DSH_INSTALLED_LIST_PROBE=1`、`DSH_UPGRADE_PROBE=1` 和上述状态文件，验证计划目标/版本、取消零提交、再次确认仅一次 executePlan、完成状态及列表版本。成功报告包含 `upgradeVerified: true`，截图为 upgrade-plan.png、upgrade-completed.png。另须核对对应临时工作区 metadata.version 和 references/note.md 实际内容；浏览器状态不代替文件证据。

使用没有历史待恢复操作的独立临时根，保持 DSH_HOME=`<root>/home`、DSH_AGENTS_HOME=`<root>/agents`、HOME=`<root>/native-install-home` 与探针配置一致。不要删除旧测试日志来绕过恢复阻断；应在新 Profile 独立安装 tarball，不能用 node_modules 软链接替代宿主依赖装配。此流程仍不覆盖升级中的浏览器刷新、响应丢失或 Host 重启。

升级响应丢失：在升级验收模式追加 `DSH_UPGRADE_RESPONSE_LOSS=1`。浏览器将第一次 executePlan 实际发送至 Host，取得接受结果后丢弃响应，刷新并从新版本已安装详情恢复原请求；第二次请求必须返回相同 operationId。核对完成后关闭本次结果，报告 `upgradeResponseLossVerified: true`、`upgradeSubmissions: 2`，另检查对应技能只有一条升级操作记录。该项覆盖同 Host 的响应丢失与刷新，不覆盖 Host 重启。

卸载响应丢失：使用新的隔离安装夹具运行 `removal-probe.mjs`，提供 `DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`、发现报告中的 `DSH_INSTALL_FIXTURE_TITLE`/`DSH_INSTALL_FIXTURE_WORKSPACE`，以及可选的 `DSH_PROBE_CHROMIUM`。输出目录须先创建；Host URL 只通过环境传入，不写入报告。探针先确认取消默认焦点及零 executePlan 请求，再确认卸载并丢弃第一次响应，刷新后从已安装列表的卸载结果区重试原请求。成功输出 `removal-result.json`、`removal-confirmation.png` 和 `removal-recovered.png`。另须按报告 operationId 检查只有一条完成的卸载记录、目标和隔离目录不存在、inventory 已移除精确 target。该探针会卸载夹具技能，只能针对临时 Profile 和测试目录运行。

失效记录清理与焦点：在新夹具安装完成后，将该测试技能目录移到扫描范围外的测试备份目录，保留 inventory；设置 `DSH_REMOVAL_KIND=stale`，预期确认文案为“仅清理失效记录”。设置 `DSH_REMOVAL_RESPONSE_LOSS=0` 验证正常响应、详情结果关闭及返回列表后的焦点；默认丢响应模式则检查列表结果关闭后的焦点。`DSH_PROBE_NARROW=1` 仅在确认弹窗阶段切换到 390px 视口并断言无横向溢出，不代表整页窄屏验收。清理后另核验记录移除、未创建隔离目录、测试备份目录的全部文件哈希不变。

### 卸载中断后的页面恢复

隔离 Host 启动时同时设置 `DSH_UPGRADE_FIXTURE=1` 和 `DSH_REMOVAL_RECOVERY_FIXTURE=1`。发现探针为测试命名空间的首个卸载在目录隔离后、安装记录提交前注入一次异常；该钩子仅存在于测试脚本，不进入发布包。浏览器运行 `removal-probe.mjs` 时也设置 `DSH_REMOVAL_RECOVERY_FIXTURE=1`，其余环境变量沿用卸载探针。

脚本点击真实卸载与恢复按钮，丢弃首次恢复响应，验证原操作查询、刷新后 restored 结果，以及只有一次卸载和一次恢复请求。另需按日志快照核对全部文件哈希、安装记录及隔离目录，不能仅凭页面文案判断恢复成功。此模式不验证 Host 重启；运行后停止本次隔离 Host 和 Registry，保留测试数据供核验。

#### 在恢复前重启 Host

浏览器探针额外设置 `DSH_PROBE_RESTART=1`。进入 recovery-required 后，它向输出目录写入 `restart-ready.json`（仅原 operationId），最长等待 120 秒。测试协调器确认日志状态和目标已隔离后，终止本次隔离 Host 并等待进程退出，以同一 DSH_HOME、HOME、Profile 和监听端口启动新 Host。新进程设置 `DSH_PROBE_SKIP_DISCOVERY=1`，避免重新生成夹具或再次注入故障。

新 Host 就绪后，协调器向输出目录写入权限为 0600 的 `restart-resume.json`，内容为 `{ "entry": "新 Host 的本地访问地址" }`。地址含测试访问令牌，不输出、不提交，完成后删除该文件。浏览器检查 origin 不变以保留 sessionStorage，再进入已安装列表中的原卸载记录，执行恢复并验证刷新后结果。必须另行确认旧进程已退出、新进程启动与磁盘恢复结果；仅设置环境变量不构成重启证明。

### 升级中断后的独立历史恢复

Host 使用 `DSH_UPGRADE_FIXTURE=1 DSH_UPGRADE_RECOVERY_FIXTURE=1` 启动隔离发现探针。测试钩子在备份后阻止替换并阻止自动回滚，保留真实恢复现场；不进入插件发布包。运行 `upgrade-recovery-probe.mjs`，沿用浏览器环境变量，另将 `DSH_FIXTURE_UPDATE_STATE` 指向测试 Registry 的状态文件。

探针执行检查更新、确认升级，刷新页面后从独立“升级操作”区域恢复；丢弃恢复响应，断言查询显示旧版本已恢复且不残留过期错误提示。关闭结果后焦点回到筛选框。全程只允许一次 executePlan、一次 recoverOperation。另核对完整 inventory 不变、旧版本文件哈希及备份/暂存清理。当前夹具是一项目目标，不能作为用户级+项目级双目标浏览器验收。

#### 升级恢复前重启 Host

升级探针也支持 `DSH_PROBE_RESTART=1`：升级进入待恢复状态后写入 `restart-ready.json`，使用上文同一重启握手。协调器应核对持久日志的 `operation.status.phase` 为 `recovery-required`，终止旧 Host 并确认退出，在同端口和相同持久目录启动新 Host，再提供新访问地址。浏览器保留原标签页的 sessionStorage，从升级历史恢复原操作；读取后删除含访问令牌的恢复文件。报告中的 `hostRestartRequested` 仅表示启用了该分支，须配合协调器的进程退出/启动证明及完整文件、inventory 核验，才能认定重启恢复通过。

首次执行使用没有遗留待恢复操作的隔离 Profile。失败现场应保留并恢复或另建隔离环境，不删除日志绕过待恢复保护。

#### 双目标升级恢复

Host 与浏览器均额外设置 `DSH_UPGRADE_TWO_TARGETS=1`。隔离用户技能根必须没有同名 `document-helper`，不要删除已有技能来满足此条件，需使用新隔离根或核验专用夹具环境。发现探针通过第二次真实安装工具调用补齐同一技能的用户级目标；浏览器断言升级计划恰好包含 user/project 两个位置。故障注入改为第一目标安装完成、第二目标备份完成后阻止替换与自动回滚。

成功要求：事务日志证明一处新版本曾安装完成、两处旧版本曾备份；恢复后两个位置均为旧版本、文件哈希匹配、完整 inventory 不变、备份与暂存消失。浏览器只提交一次升级和一次恢复，刷新后能从独立历史区域恢复。此模式不能重复覆盖上次留下的用户级夹具，重复运行须重新准备隔离环境。

### 可选模块探测失败后的重试

Registry 夹具启动时设置 `DSH_FIXTURE_MODULE_STATE` 为隔离目录中的状态文件。`unavailable` 表示评论接口返回 503；其他值返回原评论夹具。新 Host 使用 `DSH_PROBE_SKIP_DISCOVERY=1`，防止自动夹具预先缓存能力。运行 `optional-module-probe.mjs`，传入同一状态文件以及浏览器标准环境变量。

探针验证 auto 评论 Tab 未确认时隐藏，但详情显示明确提示和重试入口；恢复接口后点击“重新检查可选内容”，评论 Tab 出现，激活后展示正文。整个过程不得触发安装/升级/恢复写接口。只验证隔离夹具行为，不代表真实开源或 SaaS API 已联调。

### 兼容性提示与写按钮

`compatibility-controls-probe.mjs` 复用已有安装夹具会话，传入标准 Host/输出/会话环境变量及 `DSH_FIXTURE_UPDATE_STATE`。Host 可使用 `DSH_PROBE_SKIP_DISCOVERY=1`，避免重新安装已存在的用户级夹具。

探针先确认真实 getCompatibility 响应为 matched，再仅在浏览器传输层注入 unverified，断言安装、升级、卸载按钮禁用且只读更新检查可用；恢复真实响应并重新核验后按钮启用。写接口计数必须为 0。此探针只证明 Client 行为，不能用作实际不同发行版或依赖混装的兼容证据。

### 已安装表格、本地过滤与主题

`installed-layout-probe.mjs` 复用一个已安装夹具会话，传入标准 Host、输出目录、会话标题及工作区变量。验证无匹配过滤、忽略大小写匹配、详情返回后关键词和焦点保留，且过滤期间没有目录/安装记录请求。随后在 1440px 和 680px 浏览器视口截图，断言窄内容区记录纵向排列且无横向溢出，再切换浏览器深色偏好检查控件继承宿主颜色。全程不得调用安装、执行计划或恢复写接口。

结果为 `installed-layout-result.json`，截图为 `installed-wide.png`、`installed-narrow.png`、`installed-dark-preference.png`。深色偏好本身不等于深色已生效，需结合结果中的实际计算颜色及截图核对。本探针不覆盖所有详情、操作弹窗和屏幕阅读器行为。

行内操作：额外传入 `DSH_FIXTURE_UPDATE_STATE`，探针检查初始零更新请求，主动检查后显示新版本；通过“升级”进入详情生成计划后取消，通过“卸载”进入确认框后取消，返回各自原按钮。要求总计一次更新检查、零写请求；列表与详情共享显式检查结果，不因导航自动查询。

异常与小屏：在行内操作模式额外设置 `DSH_PROBE_FAILURE_RETRY=1`，先成功检查，再丢弃下一次 checkUpdate 请求，验证错误提示、旧升级入口隐藏及“上次成功检查”时间，随后手动重试成功。总计三次显式检查，没有自动重试。探针另验证 390px 视口下列表及深色卸载弹窗不溢出、取消按钮默认焦点，并按实际计算颜色核验危险操作文字与弹窗背景对比度至少 4.5:1。该对比度检查只覆盖这一对文字和背景，不代表全页面无障碍审计通过。

滚动与键盘：390px 模式滚动到详情按钮并记录实际广场 scrollTop，进入详情后等待固定版本读取结束，核对所有 Tab 的 aria-controls 关联、ArrowRight/End/Home 切换及 Tab 键进入当前面板。返回列表后要求滚动偏差不超过 1px，焦点回到原按钮；测试必须确实发生滚动，不能用起点 0px 代替。等待详情稳定避免将查询刷新淘汰的旧节点误作当前页面。

### 运行时版本、Client 缺失与混装

`runtime-baseline-probe.mjs` 是临时 Host 观察器。必须先复制到包目录外；直接加载包内多个探针会使官方 Client 注册器将它们识别为同一包的多个 Loader 来源。

配置字段：`root` 为已建立的隔离夹具根，`output` 为该根下唯一 JSON 文件名，`pluginEntry` 为已安装 tarball 的 Host 入口绝对路径，`hostPackages` / `clientPackages` 为待采集包名数组。配置 `expectBlocked: true` 时还会调用实际 LocalRuntime 的新安装入口，断言在准备回调前拒绝，并检查未新增操作 JSON。它不冒充浏览器或 Remote 请求，不下载或写入技能。

验证三种启动配置：正常候选组合；禁用 `ui-layout`；禁用 `ui-layout` 并插入指向 npm 官方 `@deepseek-ai/dsh-client-ui-layout@0.2.0-rc.1` 解包入口的新 Loader 行。后两者均应阻止新写，恢复正常配置并重启后应重新匹配。混装包保持原始内容，核对 npm pack 返回的 integrity；不修改共享 node_modules。官方 patch 的 `name` 是目标校验条件，不是重命名字段，不能用它替换已有行的模块。

结果必须核对 Client 图中的实际版本或缺失状态，不能仅凭配置存在判定混装成功。这组检查覆盖进程重启，不覆盖同一进程的依赖热重载，也不证明浏览器执行或页面按钮行为。

配置 `reloadClient: '@deepseek-ai/dsh-client-ui-layout'` 可验证同进程生命周期：探针找到唯一对应 Loader 行，确认初始状态匹配，调用官方 `Entry.update({ disabled: true })`，等待 Client 图移除模块，再断言新安装在准备前拒绝且无新增操作 JSON；finally 恢复原 disabled 值，等待模块重新加入并确认匹配。该模式会修改隔离 Profile 的 Loader 配置，禁止用于用户 Profile。它覆盖模块禁用/重新启用，不覆盖包版本替换或 ESM 模块缓存失效。

### 正常升级与最低 Node 回归

`upgrade-success-probe.mjs` 从新项目安装 1.2.0，使用 `DSH_FIXTURE_UPDATE_STATE` 切换夹具到 newer，检查升级计划只含一个项目目标，确认升级到 1.3.0，刷新后确认版本且无重复提交。需要设置与 reinstall-probe 相同的 Host、会话、Chromium 和输出变量，并同时把同一个 update-state 文件传给 catalog-fixture。运行前将该文件写为 current。

使用独立 Registry 来源和新项目，确保 inventory 中该来源/坐标没有其他目标，否则升级应按产品规则阻断，不能修改已有 inventory 来强行通过。Node 最低版本验证须用对应 Node 二进制启动 DSH；浏览器驱动仍可由开发机 Node 运行。浏览器结果之外，还须核对资源内容、metadata/inventory 指纹、两个完成日志以及无 staging/backup 残留。

设置 `DSH_UPGRADE_TWO_TARGETS=1` 时，正常升级探针先安装到当前用户，再安装到当前项目；计划必须精确包含 user/project 两项，一次确认完成联合升级。使用新的 DSH_HOME、HOME 和项目，避免碰撞已有用户级目录。新 Profile 应通过官方插件安装命令安装 tarball，不直接复用另一个 Profile 的 node_modules 链接；复制夹具配置时保留实际存在的 tarball 路径。完成后核对两处资源、两处 metadata、共享 inventory 和事务日志。探针等待升级后远端详情 Tab 加载完毕再截图。

### 双目标升级重启恢复协调器

`dual-upgrade-restart.py` 将双目标故障注入、两个真实 Host 进程、浏览器恢复和磁盘比对串成一次验证。先按上文建立全新临时根及 skillhub-m0 Profile，安装候选 tarball，配置 discovery observer 的 root，且不要提前启动发现探针。临时根须有独立 native-install-home、agents 和 documents 目录；node_modules 提供已固定的 DSH 安装。

```bash
python3 plugins/dsh/test/integration/dual-upgrade-restart.py \
  --root /absolute/temporary/fixture-root \
  --node /absolute/path/to/node \
  --chromium /absolute/path/to/chrome-headless-shell
```

路径均替换为实际隔离路径。协调器拒绝临时目录外的根及已有 discovery-report.json 的夹具，自动启动本地 Registry，保存两个目标与 inventory 的升级前快照；待真实文件事务进入 recovery-required 后，终止并等待旧 Host，保持同一端口启动新 Host。浏览器从原操作恢复并模拟恢复响应丢失，最后核对两个目标逐文件哈希及 inventory 字节完全回到升级前。输出位于 `<root>/recovery-result`。仅停止本次创建的子进程，含访问令牌的临时握手文件在 finally 删除；不要提交原始 Host 日志。

该场景是注入事务错误后重启恢复，不是 SIGKILL 正在执行中的系统调用，也不能代表所有崩溃时间点。

在上述协调器追加 `--crash`，可验证 Linux SIGKILL：Host 专用测试钩子在第二目标备份后写入 marker 并保持事务未结束；协调器确认现场和非终态日志后 kill 本次子进程，验证退出码 -9，再启动新 Host，等待旧心跳锁的正常失效窗口后继续浏览器恢复。不会手动删除锁。此模式要求全新夹具，覆盖精确注入边界，不代表所有系统调用内部中断点。

### 浏览器进程关闭后的对话结果

`install-history-current-probe.mjs` 设置 `DSH_PROBE_RESTART_BROWSER=1`，会在卸载后关闭 Chromium，再启动全新浏览器；确认 SkillHub sessionStorage 为空，重新打开持久会话，核对原安装成功和当前已卸载两个不同事实，无重新安装请求。

`install-browser-restart-probe.mjs` 使用尚未完成的安装工具历史夹具：Host 工具返回 accepted 后立即将原结果写入并 flush 会话，不等待安装完成。Registry 夹具可设置 `DSH_FIXTURE_DOWNLOAD_DELAY_MS=30000`。探针先确认“正在安装”，关闭 Chromium，再启动全新浏览器打开同一会话，核对相同 operationId 最终完成及当前项目记录，两个浏览器的写接口计数均为零。浏览器变量与其他对话探针一致。此能力来自持久工具结果，不表示页面升级/卸载的 sessionStorage 请求也能在全新浏览器恢复。

### 原生配置保存与详情 Tab

`tab-config-probe.mjs` 在原生插件配置页依次保存 hide/show/auto，刷新浏览器核对三项配置，进入广场检查 Tab 与实际 Registry 请求。使用 `catalog-fixture.mjs`，将 `DSH_FIXTURE_REQUEST_LOG` 设置为同一个隔离 JSONL 文件路径并同时传给夹具和浏览器探针；其余使用 `DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`、`DSH_PROBE_CHROMIUM`。请求日志只记录路径和分页参数，不记录请求头或认证信息。探针应使用专用 Profile，因为会保存配置，最终保留 auto。

断言 hide 不请求可选模块、show 正文懒加载、auto 每个模块 size=1 探测、版本/评论激活后 size=20 请求，以及审核入口不绕过匿名拒绝。截图及 `tab-config-result.json` 保存到输出目录。此探针覆盖成功夹具的刷新持久化，不证明 Host 重启、无刷新配置切换或全部 HTTP 错误组合。

设置 `DSH_PROBE_LIVE_CONFIG=1` 时，上述探针改为同一浏览器会话内按 show→hide→auto 顺序保存并返回详情，不刷新页面；同时兼容 DSH 保留详情或返回目录的导航行为。结果中的 `appliedWithoutReload` 和 `persistedAfterReload` 分别标明两种验证，不能相互替代。该路径先读取模块正文再隐藏，以检查已有内容不会导致隐藏配置失效。

### Host 重启后的配置持久化

`config-host-restart.py --root <隔离临时根> --node <Node 可执行文件> --chromium <Chromium 可执行文件>` 使用已安装插件的专用 `skillhub-m0` Profile。根目录须位于系统临时目录下，并沿用其他实机夹具的 home/agents/native-install-home 结构，Registry 地址应指向本地夹具。协调器启动 Registry 和 Host，通过 `config-persistence-probe.mjs` 保存非默认组合（版本 hide、审核 show、评论 auto），等待旧 Host 退出，再启动不同 PID 的 Host；新 Chromium 只读配置并检查实际 Tab/HTTP 行为，不重复保存配置。

每次创建独立输出目录；结果为 `config-restart-result.json`，含两阶段结果和 Node 版本。协调器只关闭自己启动的进程，URL 认证参数不写入结果；原始 Host 日志仅保留在隔离临时目录。此场景修改专用 Profile，结束后保留被验证的配置组合。

可选模块错误矩阵：将 `DSH_FIXTURE_MODULE_STATUS` 同时传给 `catalog-fixture.mjs` 与 `tab-config-probe.mjs`，取值 403、404 或 503（默认 200）。每个状态使用新 Host 进程，避免缓存能力跨状态污染。探针覆盖三种配置，核验隐藏无请求、show 的错误文案与正文按需请求、auto 的入口隐藏及 unknown 提示；不会将错误当成空评论。请求日志配置沿用上述说明。

空集合场景：向夹具和 Tab 探针同时传 `DSH_FIXTURE_EMPTY_MODULES=1`（HTTP 状态保持默认 200）。探针验证 show/auto 保留可选入口，点击后展示版本/评论空态且翻页按钮禁用；hide 仍无请求。评论 Tab 另断言技能级评论范围提示。

### Registry 离线卸载

已有 `removal-probe.mjs` 支持 `DSH_REMOVAL_OFFLINE=1`：打开已安装详情后，先断言远端不可用、本地版本仍保留，再进行取消/确认卸载流程。调用者必须事先准备专用项目安装、停止测试 Registry，并独立验证 Registry 确实不可达；浏览器断言本身不证明服务已停止。与常规卸载相同，需提供 `DSH_INSTALL_FIXTURE_TITLE`、`DSH_INSTALL_FIXTURE_WORKSPACE` 和 Host/输出/Chromium 环境参数。使用 `DSH_REMOVAL_RESPONSE_LOSS=0` 可单独验证一次正常离线卸载。操作后应核对选定目录、inventory、其他安装目标哈希及 completed 日志。

权限拒绝分支：`DSH_REMOVAL_PERMISSION_DENIED=1` 与 `DSH_REMOVAL_RESPONSE_LOSS=0` 验证提交失败、未误报成功、关闭结果后安装记录仍在。调用者需在非 root 身份下对专用项目的 `.dsh` 父目录施加真实不可写权限，且事先确认不存在 `.skillhub-trash`，让隔离目录创建在 intent 写入前失败。务必保存权限并在 finally 恢复；独立核对目标全部文件哈希、完整 inventory 及 failed/not-needed 日志。本分支不代表提交后清理权限错误。

### 提交后隔离目录清理失败

`removal-cleanup-fixture.mjs` 是仅测试使用的 Host observer。复制到插件包外的临时路径，通过隔离 Profile patch 插入并传入 `root`。它要求非 root POSIX 用户及系统临时根，确认 DSH_HOME 一致后包装该测试 Host 的 submitRemoval；在首次 cleanup 授权通过后将唯一隔离目录设为 0500，让真实文件删除因权限失败。observer 不修改产品 bundle，卸载时恢复原方法，并在目录仍存在时恢复原权限。

等待 `<root>/cleanup-fixture-ready.json` 的 ready=true 后运行 `removal-probe.mjs`，设置 `DSH_REMOVAL_CLEANUP_FAILURE=1`、`DSH_REMOVAL_RESPONSE_LOSS=0`。浏览器看到完成及残留警告后写 `<output>/cleanup-ready.json`。协调器应核对 committed 清单、隔离文件哈希和 completed+warnings 日志，根据 `<root>/cleanup-injected.json` 恢复精确目录权限，再写 `<output>/cleanup-resume.json` 为 `{"restored":true}`。浏览器发起一次恢复并断言警告消失，之后仍须独立核验隔离目录消失、目标未重建、清单及其他目标不变。使用全新标记路径，故障退出也必须恢复权限并停止自己的 Host。

### 广场与原生对话工具重叠安装

`concurrent-install-fixture.mjs` 作为包外 Host observer 加载，传入隔离 `root`。它创建独立项目及合成持久会话，写 `dual-ready.json`；浏览器探针 `concurrent-install-probe.mjs` 使用 `DSH_DUAL_ROOT` 读取会话，页面提交安装后写 `dual-trigger.json`。observer 确认页面操作尚未结束，立即通过真实 `ctx.tools.execute` 对同一 Agent/目标发起安装，检查仅一个 completed，另一个必须终结。Registry 夹具应设置 `DSH_FIXTURE_DOWNLOAD_DELAY_MS=10000` 以稳定制造重叠。

使用无旧 rendezvous 文件的临时根；失败后保留证据，不复用旧 ready/result。探针不调用模型，合成会话只用于打开真实 Agent；不能据此声称模型主动调用工具或冲突卡片展示已验证。结束后核对唯一目标记录、资源指纹、无暂存/待恢复状态。

并发探针还将实际工具回执写入明确标注的合成会话，刷新后展开包含工具调用的轮次，核验失败卡片与当前有效安装并存。轮次按钮使用 `button[data-turn-process]:enabled`，不将轮次编号固定为 1。此项不调用模型，也不证明模型能自主选择并使用技能。

### 目录导航状态

Registry 夹具设置 `DSH_FIXTURE_CATALOG_SCROLL=1` 返回 20 条不同命名空间的目录项。`catalog-navigation-probe.mjs` 使用 `DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`、`DSH_PROBE_CHROMIUM`，以及已有隔离会话的 `DSH_SEARCH_FIXTURE_TITLE`、`DSH_SEARCH_FIXTURE_WORKSPACE`。探针在 1440×700 的真实 DSH 中验证实际滚动、首次 Tab 往返、详情返回焦点与滚动、搜索词保留、会话往返和空查询清空。使用键盘触发顶部导航时保留原滚动，避免点击自动滚动掩盖恢复行为。此探针只读，不创建安装或调用模型；窗口窄于宿主完整侧栏布局时不能直接沿用文本入口选择器。

`installed-navigation-probe.mjs` 使用同样的连接和当前会话参数，并要求 `DSH_OTHER_FIXTURE_TITLE`、`DSH_OTHER_FIXTURE_WORKSPACE` 指向第二个隔离会话。当前项目须已有 document-helper 安装。验证已安装范围和关键词、详情跨会话面板往返、Tab 切换保留；随后切换实际会话，验证旧筛选和选中详情清除。探针不执行安装、升级或卸载。

### 来源切换双入口

同时运行两个 catalog-fixture 实例：默认端口 43219，第二个设置 `DSH_FIXTURE_PORT=43220`，均仅监听 loopback。`source-navigation-probe.mjs` 使用已有隔离会话的标题和工作区参数，先进入该来源的项目安装详情，再通过原生配置表单切换来源，验证导航重置、来源安装记录隔离和旧目录详情处理，结束时切回 43219。

对话探针使用包外加载的 `source-reference-fixture.mjs`，config.root 必须为专用临时 DSH 根。observer 创建隔离项目和会话，调用真实搜索工具并持久化标注为 test-fixture 的合成会话，写 source-reference-ready.json（标题、项目、Node、无模型标记）。将标题/项目名传给 `source-reference-probe.mjs`：先证明新引用可打开详情，再切到 43220 和切回 43219，两次均要求旧引用失效且无安装入口。没有安装、升级、卸载或模型调用。两个探针会修改隔离 Profile 来源配置，成功结束恢复 43219；失败时须检查配置后再使用该隔离 Profile。

### 插件 Host 禁用与重新启用

将 `plugin-lifecycle-fixture.mjs` 复制到包外，通过官方 patch 加载，config.root 指向隔离临时 DSH 根，Registry 夹具运行在已配置地址（默认 43219）。observer 不注入 skillhub，以免目标服务释放时一起销毁。它用官方 Loader Entry.update 对唯一 SkillHub Host entry 连续执行两次禁用/恢复，finally 恢复原 disabled 值；逐阶段检查服务及两个工具移除、旧服务调用失效、启用后新来源修订、目录可读和依赖基线匹配，输出 plugin-lifecycle-result.json。

此探针只证明 Host entry 生命周期。当前候选宿主在 Host entry 禁用期间仍保留该包的 Client 模块路径，不能据此认定浏览器插槽、Remote Client 或订阅已卸载。浏览器完整 Bundle 生命周期另行验证。禁止用于真实用户 Profile；本夹具会修改隔离 entry 的禁用状态，不执行技能文件写入或模型请求。

`plugin-lifecycle-fixture.mjs` 支持 config.bundle=true，改用官方 pluginManager.setBundleEnabled 操作整个 Bundle；恢复阶段重新启用同一 Bundle。结果区分 Host 服务/工具与 Client graph 条目，仅前者通过不代表完整生命周期通过。`bundle-lifecycle-probe.mjs` 从官方插件页面的 Enable 开关进行两轮浏览器验收，要求禁用时 SkillHub 入口移除、启用后唯一入口恢复且搜索可用；失败时尝试在 finally 重新启用，仍须核对隔离 Profile 的 bundles 记录。排除包内旧 observer 后该浏览器验收通过，见 verification.md。

#### Observer 隔离是制品验收前提

禁止在 Profile 中直接引用 `plugins/dsh/test/integration/*.mjs`：宿主会把包内 observer 识别为 SkillHub 包的另一个 Client 来源，跳过 observer 执行也不会消除这种注册。所有 observer 先复制到包外临时目录，Profile patch 引用复制后的文件。discovery-probe 增加临时目录入口检查；生命周期探针核对 clientPath 的 realpath 必须等于隔离 Profile 安装包的 lib/client.js，禁用阶段还要求 Client graph 与 clientPath 都移除。夹具当前默认使用 skillhub-m0 Profile。

历史测试根中的 m0-observer 已改为包外文件；旧 Profile 配置有备份。不要把残留源码 observer 下通过的测试直接描述为纯 tarball Client 验收。最新 clean-* 结果及 bundle-clean-* 结果是清理后复测证据；更早的故障注入、资源使用和跨平台制品出口仍需分别核对。

完整 Bundle 浏览器验收还须通过启动 overlay 禁用专用测试根内的 `m0-observer` 和 `skillhub-local-runtime-probe`：这些测试插件依赖 skillhub，产品禁用后会进入 pending，导致宿主把测试依赖等待显示为禁用警告。最终 Bundle 探针除入口清理外，也断言无 Could not disable 提示。此设置只用于已知隔离 Profile，不应用到用户 Profile。

## 可复跑的 Host 计划与升级验收

仓库提供 `scripts/probe-host.mjs`，替代个人临时协调脚本。它使用运行该脚本的 Node 启动 Registry 和 DSH，因此应在候选 Node 范围内运行。执行前需要：

- 位于操作系统临时目录下的专用根目录，根目录存在 `node_modules/@deepseek-ai/dsh`。
- `<根目录>/home/profiles/skillhub-m0` 已经安装待验收的插件 tarball。
- 仅用于本次验收的 HOME、DSH_HOME、项目与清单；不要对正在运行或有待恢复操作的验收环境运行。
- 本仓库 CLI 与插件开发依赖已安装，43219、43220 两个本地端口空闲。脚本遇到占用会退出，不终止占用进程。

从仓库根目录运行，每次选择一项，按顺序执行：

```sh
node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe upgrade-candidate
node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe upgrade-drift
node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe plan-context
node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe plan-source
```

| 探针 | 验证内容 |
| --- | --- |
| installed-state | 用户/项目两范围的缺失、链接、元数据、内容和权限状态；保留另一范围 |
| upgrade-candidate | 固定版本/指纹、latest 变化、包篡改、下载消失及正常升级 |
| upgrade-drift | 暂存后原文件被修改，保留旧版和并发修改 |
| plan-context | 跨会话拒绝、精确到期边界、已受理请求过期后重试不重复执行 |
| plan-source | 官方配置切换与切回后旧计划失效；官方拒绝同会话目录变更 |

脚本创建唯一运行目录、技能命名空间和报告文件，把观察器复制到包外，并检查 Client 来自已安装包。每个探针只使用隔离技能夹具，不调用模型。计划到期使用局部时钟注入，不等待真实五分钟。Registry 地址通过本次启动 overlay 指向本地夹具；不要把这个地址用于生产配置。

成功要求退出码 0 且报告 `passed: true`。输出只包含结果路径，不打印 Host 访问令牌。`host.log` 仅保存在权限受限的运行目录，仍含临时令牌，不上传。报告缺失、失败或超过 90 秒执行期限均返回非零；退出时终止本次启动的子进程。该执行期限是脚本自身的测试截止条件，与外部观察工具超时不同。

运行目录、操作日志和安装夹具保留供复核；脚本不自动删除历史或清除待恢复状态。失败后先分析报告与现场，再决定恢复或另建环境，不通过删除日志让用例通过。以上不覆盖浏览器界面、实际模型、远程 Host 或三平台完整验收。

`installed-state` 额外验证两种安装范围。为它准备新的隔离 HOME/DSH_HOME（可复用已安装包，但不能复用已有用户级同名技能），再运行：

```sh
node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe installed-state
```

它先安装用户级与项目级夹具，逐项改变并恢复目标目录/元数据/文件；只做升级和卸载预检，不执行这些计划。结束保留已恢复的两份安装供复核。真实 chmod 权限拒绝仅在非 root 的 POSIX 平台执行，报告的 `permissionsCovered` 标明是否覆盖；Windows/root 的通过结果不能代替权限场景通过。

### 详情刷新与写入口状态

`installed-detail-state-probe.mjs` 使用 `conversation-install-fixture.mjs` 创建的真实安装会话。除通用浏览器变量外，传入 `DSH_INSTALL_FIXTURE_TITLE`、`DSH_INSTALL_FIXTURE_WORKSPACE`、`DSH_INSTALL_FIXTURE_PROJECT` 和 Registry 夹具的 `DSH_FIXTURE_UPDATE_STATE`。项目必须位于临时目录，探针只修改这份夹具元数据，并在 finally 恢复。

它先获取可升级结果，再验证元数据异常及一次详情读取失败会移除新写入口；成功重读后入口恢复。它不提交升级或卸载，断言写请求为零。输出 `installed-detail-state-result.json` 与 `installed-detail-invalid.png`；页面验证不能替代 Host 状态矩阵。

### 失效安装记录清理

`stale-record-browser-probe.mjs` 使用 `conversation-install-fixture.mjs` 创建的专用安装会话。设置 `DSH_PROBE_URL`、已存在的 `DSH_PROBE_OUTPUT` 目录、`DSH_INSTALL_FIXTURE_TITLE`、`DSH_INSTALL_FIXTURE_WORKSPACE`、`DSH_INSTALL_FIXTURE_PROJECT`；可通过 `DSH_PROBE_CHROMIUM` 指定浏览器。项目必须位于临时目录，且包含夹具技能 `document-helper` 及其 `references/note.md`。不要复用需要保留安装记录的会话。

探针先把技能目录移动到项目父目录下的唯一备份，再验证列表与详情的清理入口、确认说明、取消焦点及取消零写入。确认后要求恰好一次提交、记录消失、原目录仍缺失且备份资源未变。输出 `stale-record-browser-result.json` 与 `stale-record-confirmation.png`。提交前失败会恢复目录；一旦尝试提交则保留备份供核验，避免响应不确定时重新制造无记录目录。它不调用模型，不代表普通卸载或其他平台通过。

### 运行时兼容性与 Client 重载

在相同准备条件下执行：

```sh
node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe runtime-baseline
```

该入口从已安装插件 manifest 读取 Host peer 和 Client 依赖列表，通过官方 Loader 暂时停用 layout 模块，验证兼容状态禁止新写入，安装请求在准备阶段之前被拒绝且不新增操作日志；finally 恢复模块并核验重新开放。仅用于隔离 Profile，测试期间页面布局会暂时不可用。报告保留 initial/blocked/restored 三个状态，不调用模型。该测试证明同进程 Client 模块重载，不证明 DSH 发行版升级或 Node 模块缓存被清空。

失效记录探针同时覆盖确认框的键盘取消：Tab/Shift+Tab 可在两个动作间导航，Escape 关闭后焦点回到触发按钮，Enter 重新打开；点击取消同样恢复焦点。报告的 `keyboardActionsReachable`、`escapeCancels`、`focusRestored` 分别记录这些断言，不代表完整读屏或浏览器焦点循环验收。

### 升级计划键盘操作

`upgrade-keyboard-probe.mjs` 复用真实安装会话的浏览器环境变量，并要求 `DSH_FIXTURE_UPDATE_STATE` 指向该本地 Registry 的状态文件。它主动检查更新，通过 Enter 生成计划，验证取消获得焦点；再用 Enter 取消并核验焦点返回生成按钮。写请求必须为零，finally 恢复 Registry 状态。输出 `upgrade-keyboard-result.json`；不会执行升级或调用模型。

`runtime-unsupported` 使用同一运行器验证未支持的真实宿主组合：在单独准备的临时根内安装明确版本的 DSH 和待测插件，再执行 `node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe runtime-unsupported`。它要求插件仍能加载，兼容门禁拒绝新安装且准备回调未执行、日志不增加。若宿主在加载插件前拒绝依赖，该探针不能出具通过报告，应单独记录加载器拒绝。不要在正式 Profile 中更换依赖或编辑包版本来制造结果；此探针不代表该宿主获得支持。

### 重启后读取已有安装及操作

`history-restart` 需要先在新的专用根运行 `installed-state`，保留其同会话的两项已完成安装。重新启动时运行 `node plugins/dsh/scripts/probe-host.mjs --root "$probe_root" --probe history-restart`，读取原会话、原操作 ID 和用户/项目两项安装；不提交写入，不调用模型。夹具要求恰好两项完成记录，不适用于混有其他操作的测试根。

跨宿主版本验证另需在所有 Host 完全停止后切换真实发行包，保留同一隔离状态目录，并比较安装文件、inventory、操作日志和 host identity。仅此只读探针通过不证明发生过版本切换，也不证明待恢复事务兼容。

### 待恢复升级经过不受支持的宿主

`dual-upgrade-restart.py` 可增加 `--unsupported-dsh-bin /absolute/temporary/path/to/@deepseek-ai/dsh/lib/bin.js`。该入口必须来自单独安装到临时目录的真实非 RC.2 DSH 发行包；原测试根仍保持 RC.2，且须满足既有新隔离 Profile 和包外 discovery observer 前置条件。

协调器在双目标升级进入待恢复状态、原 Host 退出后，用指定宿主启动同一 Profile，核验官方跳过不兼容 Bundle 且未授予豁免，再停止中间 Host。待恢复日志、inventory 和目标存在状态须不变；最后重新启动原 Host，由浏览器执行恢复并核验两个目标的完整文件 hash 和原清单。此参数用于拒绝与回退验证，不用于声明新宿主兼容，也不执行版本豁免。完整结果在 `recovery-result/host-files-result.json`；原始 Host 日志包含临时访问令牌，不上传。

### 广场结构化错误提示

`catalog-error-probe.mjs` 使用隔离 Host URL、输出目录和可选浏览器路径（`DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`、`DSH_PROBE_CHROMIUM`）。测试 Profile 需配置运行中的 catalog-fixture Registry。探针保留真实 Host/Client 通道，在浏览器边界替换 listSkills 的 RemoteResult，依次验证匿名拒绝、限流、网络失败、服务不可用和响应格式错误：对应原因可见、原始诊断不展示、不误报空列表；移除注入后手动重试恢复真实列表，写请求为零。该探针验证 Client 展示与恢复，不替代 Registry HTTP 状态映射测试。

### 真实 Registry 限流与手动重试

`retry-after-probe.mjs` 复用隔离 Host/输出/浏览器路径配置，并要求 `DSH_FIXTURE_REQUEST_LOG` 指向本轮夹具日志。Registry 以 `DSH_FIXTURE_RETRY_AFTER=1` 启动：首次 rate-limit 搜索实际返回 HTTP 429、Retry-After: 8，后续正常返回。探针核验 Remote 剩余秒数、页面提示、提前重试被 Host 阻止（Registry 仍仅一次请求）、到期无自动重发、用户点击后恢复真实列表且保留关键词。测试不替换 Remote 响应，不调用模型或写入技能。

### 目录范围、固定版本与说明渲染

`catalog-range-probe.mjs` 要求夹具设置 `DSH_FIXTURE_CATALOG_SCROLL=1`、`DSH_FIXTURE_CATALOG_TOTAL=55` 和 `DSH_FIXTURE_REQUEST_LOG`，其余使用标准隔离 Host/输出/浏览器路径。探针确认展示 55 个匹配结果中的 20 项，不提供虚假分页；打开第一项后从真实 Registry 请求日志核验 resolve 与 SKILL.md 固定为 1.2.0。另检查 HTML/脚本及危险链接不执行、远程图片不请求、实体正确显示，以及 390px 详情容器无横向溢出。测试通过不代表所有窄屏页面或完整无障碍验收完成。

### 窄屏加载、空态、传输失败与键盘操作

`catalog-states-probe.mjs` 使用标准隔离 Host/输出/浏览器路径及 `DSH_SEARCH_FIXTURE_TITLE`，配合空安装目录和 catalog-fixture。390px 视口内暂挂列表请求，检查加载提示不会变为空列表；搜索空结果后用键盘清空，阻断 Remote 传输后用键盘重试并保留关键词，再验证卡片 Enter 打开、详情返回焦点、已安装空态、容器无横向溢出及返回会话。仅测试 Remote 传输失败展示，HTTP 匿名拒绝仍由 catalog-error-probe 和 Registry 测试覆盖。

`upgrade-success-probe.mjs` 额外支持 `DSH_LIFECYCLE_MOBILE=1`：在宿主选中会话并打开 SkillHub 后切换 390px，检查非空已安装列表、升级计划、升级完成、卸载确认和卸载后空态均无横向溢出，并逐阶段截图。与 `DSH_COMPLETE_LIFECYCLE=1` 合用会保留真实文件 hash、资源、清单恢复和取消不提交断言；刷新后的宿主导航临时恢复桌面宽度，进入插件后再次缩窄。该模式不验证宿主自身的移动端导航。

### 真实工具调用的取消边界

`tool-cancellation-fixture.mjs` 必须复制到临时目录、从包外加载，config.root 指向隔离 Host 根。配套 Registry 设置 `DSH_FIXTURE_RESOLVE_DELAY_MS=2000`、`DSH_FIXTURE_DOWNLOAD_DELAY_MS=6000`、`DSH_FIXTURE_REQUEST_LOG` 和独立 namespace。探针先在真实工具的 resolve 请求到达后取消 signal，断言无接受记录及下载；再发起新调用，收到 accepted 且下载开始后取消 signal，确认原操作仍完成、资源完整。同一 callId 重放必须返回同一 operationId，总共只有一个操作和一次下载。不会发起模型推理；它验证工具执行信号边界，不等同于用户点击 DSH 停止按钮的端到端行为。

### 操作订阅的可见性与释放

`subscription-lifecycle-probe.mjs` 使用隔离 Host、实际安装回执的历史卡片及 `DSH_INSTALL_FIXTURE_TITLE`，执行三轮挂载/禁用/恢复并记录 visibilitychange 监听数。第一轮在浏览器 Remote 响应边界将只读状态投影暂改为 staging，确认实际发出轮询后，通过 document.visibilityState 与 visibilitychange 模拟隐藏，观察 2.5 秒无新增查询；恢复可见并撤销投影后应重新查询真实完成状态。该模拟不修改 Host 操作记录，不证明操作系统真实窗口最小化事件分发。

重载时若有限次数自动重试耗尽，探针点击一次“查询原操作”验证手动只读恢复，并在 manualReadRecovery 记录次数。三轮须保持监听数不累积、禁用后不轮询、入口恢复唯一且无页面异常。该探针不单独证明多个同时可见消费者的请求合并。

`operation-dedup-fixture.mjs` 从隔离临时目录、包外加载，config.root 指向隔离根，并设置 catalog-fixture 的 namespace。它执行一次真实安装，再将同一回执写成两个合成历史卡片；输出 operation-dedup-session.json。不会调用模型，也不声称模型自然生成了重复卡片。将输出 title 传入 `DSH_INSTALL_FIXTURE_TITLE`，运行 `operation-dedup-probe.mjs`：前两次 getOperation 只读响应投影为 staging，每次响应延迟 1.5 秒，之后返回真实终态。断言两个可见卡片均经过进行中/完成、同操作最大在途请求为 1、终态停止轮询、浏览器不提交写请求；保留 JSON 和截图。

### 独立布局入口降级

`layout-degradation-probe.mjs` 需要实际安装回执的单张历史卡片及标准 Host/输出/浏览器参数、`DSH_INSTALL_FIXTURE_TITLE`。它在页面初始化时包装官方 ModuleLoader 对已安装 Client 的 factory 注册，再仅包装插件 apply，以官方 Cordis `ctx.isolate('layout')` 隔离插件所见的布局服务；DSH 外壳不变，Host 依赖与兼容性判定不被替换。先验证无广场入口时卡片仍可查询原操作且跳转提示准确，再两次提供/释放原布局服务，验证广场跳转恢复、返回会话及卡片保留，要求浏览器写请求为零。此测试覆盖服务可用性降级，不代表缺失整个布局包时仍可加载插件，也不绕过未验证 Host 的写入保护。


搜索场景使用 `conversation-search-fixture.mjs`：复制到插件包外的隔离临时目录并作为 Host observer 加载，传入 `config.root`。它验证实际 Client 来自隔离 Profile，通过真实搜索工具执行器获取结果，将其保存为明确标记 test-fixture 的合成历史，输出 `conversation-search-ready.json`；不调用模型或安装工具。向 `layout-degradation-probe.mjs` 传入该标题（沿用 `DSH_INSTALL_FIXTURE_TITLE`）并设置 `DSH_LAYOUT_SEARCH_PROBE=1`，验证无布局服务时搜索卡片、详情抽屉、固定详情确认和版本 Tab，两轮布局提供/释放后卡片保留，浏览器写请求为零。输出 `layout-search-degradation-result.json` 和截图。它不模拟整个布局包缺失，也不证明模型能自主选择搜索工具。


### 配置编辑能力降级

`config-availability-probe.mjs` 使用标准隔离 Host、输出目录和浏览器路径参数。在三个独立页面分别投影真实 settings.describe 响应为只读、缺少 skillhub namespace、原始可写状态。只读时断言 Registry 地址与保存禁用；namespace 不可用时只显示说明且没有保存按钮；原始响应下修改本地草稿可启用保存，但不提交。对首次使用弹窗使用 locator handler，仅在出现并阻挡操作时关闭。输出 config-availability-result.json 与三张状态截图。该探针验证实际安装插件的界面响应，不改变 Host 授权或持久配置，不证明同一页面的断线自动恢复。


### 已接受安装失败的 Host 诊断

catalog-fixture 设置 `DSH_FIXTURE_DOWNLOAD_FAILURE=1` 时仅下载接口返回 503。将 `operation-failure-fixture.mjs` 复制到插件包外的隔离临时目录，作为 Host observer 加载并传入 `config.root`；它调用真实安装工具，验证先返回 accepted、之后 failed/files=failed/recovery=not-needed，安装目标目录不存在，再以同一 callId 调用并核验原操作编号。输出 operation-failure-ready.json，不调用模型。

运行器需等待 Host 标准错误出现 `SkillHub accepted operation failed`，按该 operationId 验证事件仅包含 operationId、kind=install、code=NETWORK，且本次执行只出现一次。不得将完整 Host 日志归档，日志可能含临时访问凭证；只保存白名单结果。该故障注入仅证明 503 下载失败路径，不证明自然发生的其他失败原因。


### 对话入口缺失时的广场降级

`tool-entry-degradation-probe.mjs` 使用标准 Host/输出/浏览器变量及 `DSH_INSTALL_FIXTURE_TITLE`（传只读搜索夹具的标题）。初始化时包装官方 ToolView 模块的 ModuleLoader factory，保留模块导出但暂不执行 apply；随后通过官方 ctx.plugin/dispose 两轮提供和移除原模块贡献。验证广场提示、详情和版本读取、已安装 Tab、恢复后提示消失以及移除后提示重现，要求浏览器写请求为零。输出 tool-entry-degradation-result.json 和截图。此测试覆盖 ToolView 声明和贡献生命周期，不代表整个 npm 包缺失仍能加载，也不证明模型交互。


### 无来源且配置入口不可用

`source-config-degradation-probe.mjs` 使用标准 Host/输出/浏览器参数及 `DSH_INSTALL_FIXTURE_TITLE`（只读搜索夹具会话标题）。它仅在 SkillHub Client apply 上下文隔离 configForms，并把 getContext 的成功响应投影为未配置来源；不修改 Host 的真实来源。验证同一广场页面显示恢复配置功能的说明，两次提供/移除原 configForms 服务后在配置步骤和不可用说明之间切换，不残留相反的引导。输出 source-config-degradation-result.json 和截图，浏览器写请求必须为零。该场景证明入口服务变化的同页响应，不等同于网络断线重连或实际配置保存验收。


### 未知历史元数据与正文隔离

将 `unknown-history-fixture.mjs` 复制到包外隔离目录作为 Host observer 加载。默认执行真实只读搜索，把正文保留为真实搜索输出、presentation metadata 的 schemaVersion 改为 999 后写入合成历史。设置 `DSH_HISTORY_KIND=install` 时生成测试安装历史：正文是形状有效的合成 accepted 回执，元数据版本未知，不调用安装工具，也没有真实对应安装操作。

`unknown-history-probe.mjs` 使用标准 Host/输出/浏览器变量及 `DSH_INSTALL_FIXTURE_TITLE`；安装场景需同样设置 DSH_HISTORY_KIND=install。验证首次打开及刷新重放均仅显示不可识别记录，没有结果卡片；全程详情、展示确认、操作查询、安装、执行计划与恢复请求均为零。输出 unknown-history-result.json 和截图。此测试不证明 preparing/start 流式阶段，也不模拟模型选择工具。


### ToolView 实时阶段

`tool-phases-fixture.mjs` 在包外隔离目录创建会话，实际执行只读搜索，并按官方 agent/assistant-stream 的 start/chunk/end 帧及持久 assistant/message、tool/call、tool/result 事件驱动两个工具的展示。不调用模型或安装工具；安装结果为明确的合成 not-confirmed。准备阶段只发出流式帧，执行阶段待浏览器确认后才提交 tool/call，结果阶段再次等待浏览器确认。结果正文故意不含卡片数据，仅 meta 承载展示数据。

`tool-phases-probe.mjs` 接收标准 Host/输出/浏览器参数、DSH_INSTALL_FIXTURE_TITLE 和隔离夹具根目录 DSH_PHASE_CONTROL_DIR，向根目录 phase-command 写入 start/result 来推进阶段。逐阶段验证搜索和安装文案，结束时验证搜索结果和安装未确认卡片；结果阶段兼容宿主默认折叠的工具历史。输出 tool-phases-result.json 与三张截图。测试证明官方事件投影与实际 Client 的协作，不证明真实模型意图或工具等待取消行为。


### 新包 Loader 与 Bundle 生命周期验证

先按上文使用 `--from-default-profile web` 创建新的 `skillhub-m0` Profile，再安装当前 tarball。直接对不存在的 Profile 执行 `plugin add` 只会初始化基础 Profile，缺少 Web Client 服务，不能用于此探针。

```sh
node plugins/dsh/scripts/probe-host.mjs --root /absolute/path/to/prepared-temporary-root --probe plugin-lifecycle
```

该探针启动本地 Registry 夹具并验证：正式安装包的 Client 路径、`astron-skillhub` Loader ID、该行 Registry 配置，以及两轮 Bundle 禁用/启用后的 Host、工具、Client 清理与恢复。结果不涉及浏览器实际渲染或模型调用。运行结束自动停止本次启动的进程，私有 Host 日志保留在临时运行目录，不提交原始日志。


共存检查在专用 Web Profile 中额外安装固定参考 tarball `@cocofhu/skillhub@0.2.16`，保留 `--ignore-scripts`，然后运行：

```sh
node plugins/dsh/scripts/probe-host.mjs --root /absolute/path/to/prepared-temporary-root --probe plugin-coexistence
```

此入口将参考插件的 apiBase/webBase 覆盖为本地夹具地址，避免启动自检访问真实市场。探针在本插件两轮禁用/启用前后检查参考插件的单一 Loader 行、配置、启用状态、六个工具对象和 Client 路径未变；不会调用参考插件的安装或卸载工具。它验证 Host 与模块注册共存，不证明参考插件的旧版浏览器 UI 在候选 DSH 中可用，也不验证两个插件同时启用时的模型选工具行为。


`reference-client-probe.mjs` 验证同一 Profile 的浏览器共存。运行前启动已配置两个插件的隔离 DSH Web Host 和本地 Registry 夹具，通过环境变量传入 `DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`，可选 `DSH_PROBE_CHROMIUM` 指定浏览器。URL 可能含访问令牌，不输出到公共日志。探针通过观察真实 ModuleLoader 的 apply 上下文读取插槽注册，不替换组件；检查各工具视图的独立 key，并通过官方插件页面两轮禁用/启用本插件，核对参考插件三个 ToolView 和广场入口的对象未变。本插件广场在恢复后须重新可用。探针不会打开参考市场或调用模型。


`dual-upgrade-restart.py --replacement-tarball /absolute/path/to/retained-candidate.tgz` 在双目标升级进入待恢复状态并停止 Host 后，使用官方插件安装器替换包，再重启执行浏览器恢复。必须使用新建的临时 Profile 和包外 discovery observer；不得对真实工作目录运行。该选项核对替换后的六个安装产物与 tarball，并确认换包本身未修改待恢复日志、inventory 和目标存在状态。包配置 overlay 根据实际 Loader ID 生成，可覆盖旧开发包 `skillhub` 与当前 `astron-skillhub` 的差异。正常停止与 `--crash` 强杀是不同验收组合，分别记录，不相互代替。


## 重启后历史搜索详情的显式重开

`history-detail-reopen-probe.mjs` 使用已有持久搜索会话，不调用模型、不创建搜索结果或安装技能。先重启隔离 Host 使旧引用失效；提供 `DSH_PROBE_URL`、`DSH_PROBE_OUTPUT`、`DSH_SEARCH_FIXTURE_TITLE`，必要时提供 `DSH_SEARCH_FIXTURE_WORKSPACE` 和 `DSH_PROBE_CHROMIUM`。探针验证打开历史卡片不发详情请求、失效后不自动重开、点击“重新查看详情”使用真实 callId 和原引用、返回新引用并确认展示、来源版本与安装控件可见、关闭焦点恢复、零安装/恢复请求。输出 `history-detail-reopen-result.json` 与截图。成功仅证明该隔离环境的历史读取链路，不证明模型安装或所有平台通过。


## 搜索结果中的本地安装状态

将 `search-installation-fixture.mjs` 复制到新建的临时根目录中，作为包外 Host observer 加载，配置包含绝对路径 `root`，可选 `profile`（默认 `search-installation`）、`namespace`（默认 `meeting`）、`slug`（默认 `meeting-notes`）、`query`（默认 `助手`）。先在该 Profile 中通过官方安装器安装当前候选包，配置本地 Registry 夹具；查询必须能返回指定坐标，且其最新版本可下载。此探针会真实安装技能，因此只允许新建隔离环境：`HOME=<root>/native-install-home`、`DSH_HOME=<root>/home`、`DSH_AGENTS_HOME=<root>/agents`。不要加载到个人 Profile 或真实项目中。调度观察器前会检查三个环境目录均真实存在且没有符号链接或路径重定向；备份和临时写入 inventory、metadata 前另检查目标为根目录内的普通文件且父路径未重定向。

探针通过官方 `ctx.tools.execute` 执行搜索、项目安装、用户安装，再搜索并检查两个范围的版本和文件状态；同坐标两个范围安装相同版本，保留现有多目标版本一致性保护。随后依次构造其他 Registry、同名不同命名空间、损坏元数据和不可读 inventory 的隔离夹具，检查不误认来源、异常和未知状态。其中不同命名空间夹具故意保留原文件元数据，因此结果应为无当前坐标记录且查询不完整，不能把这个合成的不一致清单认定为正常外部安装。所有临时文件修改均在 `finally` 中逐字节恢复；安装完成的两个测试目标保留，以供浏览器查看。

`<root>/search-installation-ready.json` 包含通过标志、会话标题、会话 ID、隔离工作目录和各阶段快照。会话使用真实工具结果生成**明确标注“非模型”的合成历史**，并验证旧搜索对象不随新安装而改变；这不等同于真实模型理解安装状态，也不代替重启后浏览器历史重放验证。浏览器可按报告中的标题打开会话，检查查询时状态与未知、异常提示，并在技能中心查看当前安装状态。成功报告已存在时，重启只复用该报告，不重复安装。报告与截图留在 worktree 外，不提交原始宿主日志。

## 三平台 CI 的隔离 Host 冒烟入口

在插件目录构建并打包候选后执行：

```sh
node scripts/smoke-native-host.mjs --artifact artifacts --report host-smoke-summary.json
```

`--artifact` 接受单个 tgz，或恰好包含一个 tgz 的目录。入口创建新的 OS 临时目录，安装固定 DSH/包管理器，使用独立 HOME、DSH_HOME、DSH_AGENTS_HOME 初始化 Web Profile，安装候选并依次运行既有 runtime-baseline、plugin-lifecycle 探针。过程不调用模型。运行环境不继承模型凭据、Node preload 和用户 npm 配置；网络下载需要访问 npm。

上传报告仅包含固定字段的版本、平台、候选摘要、阶段与通过结果。原始 Host 日志保存在临时目录，不可作为 CI artifact 上传。失败时从本机私有诊断目录排查，不粘贴含入口令牌的日志。此入口用于实际 Host 加载和生命周期检查，不替代浏览器、真实模型、远程落盘或完整平台验收；CI 配置存在也不代表远端作业已经执行。


### 双 RC 宿主 smoke

从 `plugins/dsh` 运行 `node scripts/smoke-native-host.mjs --artifact /absolute/path/to/candidate.tgz --report /absolute/path/to/summary.json --dsh-version 0.2.1-rc.2`。省略版本时保留 `0.2.0-rc.2` 基线；脚本只接受这两个精确版本。新 RC 不接受 retained Cordis 模式。fresh 安装不添加 overrides 或 legacy peer 选项；retained 模式仅用于保留旧 Cordis 家族的独立回归。

需要把本机原始日志保存在仓库外时，将 `TMPDIR` 设置为仓库外报告目录；脚本的所有 Host/profile/HOME/探针日志都位于该目录下的独立根。不要指向现有 DSH 安装。

新 RC 的 Gateway 要求 in-process 调用显式携带 Peer；观察器使用该 Connection 的 `browserPeer`，旧 RC 使用原有 `operator`。不伪造身份，不使用浏览器输入创建主机身份。发布插件通过生成的 Remote 绑定调用，不直接构造 Gateway invocation。

## 已安装更新总览

`installed-update-overview-probe.mjs` 导出 `probeInstalledUpdateOverview(page, screenshot)`，由已进入活动会话的真实 DSH Playwright 页面调用。夹具需要至少一个真实可更新安装、至少四个安装位置和一个多位置技能；按当前实际结果选择更新确认目标，不固定某技能的旧版本。先执行真实只读检查并取消逐位置更新确认，扫描断言一次 `beginInstalledUpdateScan`、逐位置 `checkInstalledUpdateScan` 与一次 `endInstalledUpdateScan`，再仅拦截 `checkInstalledUpdateScan` 响应验证混合结果、三并发、失败重试、关键词叠加、详情返回与迟到结果丢弃。不会确认安装写入或调用模型。`screenshot(name)` 由运行器保存图片；深浅色和 360px 布局均有断言。

`installed-update-context-component.tsx` 是独立 React + QueryClient 组件夹具，使用 Bun 的浏览器构建后挂到含 `#root` 的测试 HTML，供 `installed-update-context-probe.mjs` 的 `probeInstalledUpdateContext(page)` 调用。它验证库存 itemKey 替换、来源/会话切换、同会话工作区 A→B→A 与迟到响应。仅内存服务，不更改真实 DSH 工作区或来源配置；与真实 Host 验收分别记录。上述浏览器探针不由 `bun test` 自动运行。
