# 首版范围收敛与验证记录

2026-10-13：当前首版为浏览、详情、安装、已安装管理、主动升级、精确卸载，以及可选版本/安全审核/评论 Tab；登录和权限管理后置。以下记录按轮次保留，最新证据见末节。proposal、design、product-design、三项 capability specs 和 tasks 已同步。功能尚未实现。

此前全量规格和验证记录保存在 [历史快照](../../../docs/research/dsh-native-plugin-2026-10-12/history/full-scope-v4-before-mvp.zip)；旧 Q1–Q12、17 项需求和 47 个场景不再作为当前交付范围。

## 证据与限制

- 当前依据是仓库接口与 CLI 实现、此前固定提交的 DSH 官方源码及社区包调查；未执行真实 DSH 插件安装。
- 匿名列表、详情、下载的业务可用性尚待测试环境验证；路由 permitAll 不能作为通过依据。
- 插件构建、三平台兼容、浏览器交互、安装/发现/执行与远程单用户场景均未运行；所有实施任务保持未勾选。
- 图像是概念设计，不是产品截图；Mermaid 文本尚未经渲染器验证。
- 未创建 Trellis 任务，继续既有 OpenSpec；未提交、推送、发布或部署。

## 本轮文档验证

- `openspec validate add-dsh-native-plugin --strict --no-interactive`：通过；上一轮 9 项需求、22 个场景（已被下方修订记录覆盖）。
- 当前 change Markdown 本地链接、代码围栏配对及行尾空白检查：通过。
- 首版概念图已生成并人工查看，保留 DSH 外壳，移除登录及升级卸载入口；示例说明不作为运行能力证明。
- 本轮仅调整设计文档及图片，没有执行功能测试。

## 详细设计与 Tab 修订

- 产品文档扩展到场景、范围、交互取舍、逐页规则、15 项功能、异常矩阵、状态图及验收；增加接口与实现分册，架构包含数据存放和跨端时序。
- 用户最新补充覆盖上一轮“无已安装页”：增加已安装只读 Tab，详情支持版本/安全审核/评论的 auto/show/hide。
- 重新读当前仓库 Controller：搜索固定 newest/page 0；详情、版本、文件、评论读取路径存在；SecurityAuditController.canViewAudit 对匿名明确拒绝。本期不改访问政策。
- 独立子代理只读评审发现并修正四项：已安装固定版本离线契约、丢失响应同 key 恢复、下载前幂等检查、有界能力探测与正文懒加载分离。
- 官方仓库重新访问成功；固定提交 ui-layout 通过 raw 读取。部分网页/图像请求收到 429，仅将成功返回的图像纳入交付。
- 生成工具未披露模型版本，不能核实具体 Image 2 版本。图片均为示例和概念设计，未运行 DSH。

独立评审复查：上述四项设计缺口均闭合，未发现这些项的残余实质矛盾；仅文档检查，不代表实现或运行验证。

最终校验：OpenSpec strict 通过；当前 11 项需求、32 个场景。全部 Markdown 本地文件链接、围栏配对、行尾空白检查通过。四张当前 PNG 与提示词已保存并查看；已安装图的宿主外壳视觉偏差已在产品文档明确标注，校正编辑受到 429 限流。Mermaid 未运行渲染器；未做插件功能测试。


## 升级卸载与 DSH 接入深化（最新）

- 已阅读用户提供的《DSH插件形态与交互机制完整文档.md》，保存文件名、SHA-256 和采纳核对结果于 dsh-integration-design.md；没有修改原文件。
- 独立只读核对官方 HEAD 与参考资料：修正 bundle 层次、官方配置插槽、disabled 字段、service 原名注册和 WebServer disposer；采用原生 Remote 生成/挂载协议并保留实机验证条件。
- 新增 lifecycle-design.md：精确目标、共享版本、主动升级、离线卸载、计划、CAS、锁、日志与恢复；当前 scope 已同步到 proposal、specs、产品、接口、实现和任务。
- 第二次只读评审修正三个问题：CAS 提交后不得回滚旧目录；共享安装核心需提供可等待事务观察器；CLI 与插件统一 canonical lock helper。
- Mermaid 语法检查使用仓库声明的 mermaid 11.17.2 与 jsdom，依赖只装在临时目录，没有变更仓库依赖/锁文件。语法验证不等同于浏览器视觉渲染。
- 本轮不实施代码，不执行用户技能的安装/升级/卸载，不发布或部署。

最终文档检查：16 项需求、52 个场景（以本轮命令实际计数为准）；OpenSpec strict、本地链接/围栏/空白检查通过；14 个 Mermaid 图通过 mermaid.parse。未做浏览器图形渲染和插件运行测试。管理页与卸载确认页图片请求返回 HTTP 429/Cloudflare 验证页，未生成新图；提示词已保留，旧只读列表图已标记历史，当前行为以生命周期分册为准。已停止继续重试图片服务。


## 继续完善：图像服务仍受限

- 用户要求继续后，使用 imagegen 对原 DSH 广场参考图进行一次管理页编辑请求，仍返回 HTTP 429 和 Cloudflare 验证页面；未成功生成新图片，未继续密集重试。实际短提示词保存为 assets/dsh-management-manage-retry-prompt.txt。
- product-design.md 新增升级计划、卸载确认的两张 Mermaid 交互结构图及字段/按钮/焦点规则；它们是文字设计的补充，不冒充 Image 2 效果图。
- 当前剩余外部阻塞仅涉及新版管理/卸载效果图；插件功能尚未开始实施，属于原定后续阶段。

本次最终验证：16 个 Mermaid 图全部通过语法解析；OpenSpec strict、本地 Markdown 链接/围栏/空白检查通过。图片新增未完成，原因如上；未执行功能开发或实际技能变更。


## 对话交互纳入首期（2026-10-13 最新范围）

- 用户明确要求同类对话交互，已覆盖此前对话工具后置决定；新增 conversation-design.md，并同步产品、提案、架构、接口、实现、任务与正式验收规范。
- 新增搜索工具卡片、共享详情抽屉、显式安装工具与自定义结果卡片；历史结果、来源/目标变化、工具等待取消和双入口并发有明确规则。对话升级卸载工具未自动纳入范围。
- `openspec validate add-dsh-native-plugin --strict --no-interactive` 通过；当前 18 项需求、61 个场景。
- `node /tmp/skillhub-dsh-diagram-check/check.mjs openspec/changes/add-dsh-native-plugin`：18 张 Mermaid 全部语法通过。本地 Markdown 文件链接检查通过。
- 本轮仅修改设计文档，未做插件实现、真实 DSH 验收或技能文件变更。新增对话效果图未生成，已纳入任务 6.5，Mermaid 不冒充生成图片。

独立只读评审指出旧搜索选择缺少来源绑定：已补充 Host 签发的 selectionRef、来源/会话/项目绑定，以及历史选择不能降级为无引用新坐标安装的规则与验收场景。

## 对话设计图重试（2026-10-13）

用户明确要求再次生成后，使用原生 imagegen、以 dsh-native-v4-chat.png 为参考编辑对话搜索与详情抽屉图。请求约 26 秒后返回 HTTP 429，响应是 Cloudflare 验证 HTML，未得到图片。无法据此确认模型版本、账号额度或具体限流原因。未继续密集重试，未切换到独立计费 API。完整提示词保存为 assets/dsh-conversation-v5-drawer-prompt.txt；不存在对应新 PNG，不加入图片引用。


## 文档规范完善与一致性复核（2026-10-13）

- 图片生成按用户要求暂停。本轮新增 README.md 文档入口/职责与维护约定，acceptance.md 功能证据映射、M0–M4 出口和未验证项。
- 同步两入口架构、模块目录、共享 Client 操作状态、原生工具范围与阶段依赖；日志/恢复必须先于写入口开放。
- 独立只读评审提出四项：详情引用提升缺契约、accepted 与最终结果混淆、重启查重顺序、历史来源/主机/时间字段。均补齐接口和验收场景，评审复核确认这四项无残余阻断。
- OpenSpec strict 通过：18 项需求、65 个场景；19 张 Mermaid 通过语法解析；本地 Markdown 文件链接、围栏闭合及行尾空白检查通过。
- 未进行插件实现、真实 DSH/Registry 运行验证、浏览器图形渲染或图片生成；兼容发行版、工具展示与调用上下文仍是 M0 待验证事实。无技能文件变更、提交、推送、发布或部署。


## 基于用户完整参考文档的工程方案（2026-10-13）

- 重新读取《DSH插件形态与交互机制完整文档.md》，1224 行，SHA-256 与既有记录一致；未修改原文件。
- 新增 engineering-blueprint.md，14 节：逐章采纳、进程边界、加载/命名、Host 职责、工具 schema/执行/展示、ToolView、Remote、CLI 复用、执行次序、构建、实施切片和源码锚点。同步阅读入口及执行检查项。
- 重新检查官方本地源码固定提交 d743267388641bc76f17c45ce8b4c231aed1d32c，核实 callId、可选 agent、逐参数开放 schema、纯展示投影、ToolView 阶段和 Remote 参数约定；重新阅读本仓库安装/升级/卸载服务及 CLI package.json。
- 在线官方页面复核遭工具网关 HTTP 429，本轮不声称最新发行版已验证，结论限定固定源码。独立只读评审对照源码未发现新增方案阻断项。
- OpenSpec strict 通过；22 张 Mermaid 语法解析通过；本地 Markdown 链接、围栏、行尾空白检查通过。正式规格仍为 18 项需求、65 个场景，本轮细化实现而未新增产品范围。
- 未实施插件、未安装技能、未运行 DSH/Registry 功能验收、未生成图片、未发布或部署。外部插件打包兼容等仍按 M0 验证。


## 正式文档编辑与发布方案补充（2026-10-13）

- 按用户要求清除正式产品/设计/实现分册中的资料行数、摘要校验、网络失败、会话决策过程与生成图失败记录，保留必要设计限制、技术参考和兼容验收要求。过程记录仅在本文件留存。
- 新增 release-guide.md 和 registry-api-contract.md，覆盖验证、部署、独立插件发布、回退，以及 Registry 公共契约和开源/SaaS 同步；README 与 tasks 已关联。
- 对照 Makefile、README 和现有 CLI/镜像/Chart 工作流核对命令与触发条件。独立评审提出可选版本 Tab 的 show 行为及 Chart 已有过滤表述，均已修正。
- OpenSpec strict、24 张 Mermaid 解析、本地 Markdown 链接/围栏/空白检查通过。未执行测试环境部署、包发布、功能实现或生成图片。


## 最终规格收口（2026-10-13）

- 将开源/SaaS 公共契约、向后兼容、独立插件发布、支持矩阵及回退要求纳入正式 SHALL/Scenario；验收矩阵增加 A13–A15，并补正式需求覆盖表。
- 独立评审发现历史完成快照持久化承诺、计划到期错误码和 M0 安装依赖边界三项，已统一：聊天保存 accepted/context/operationId；终态依赖 Host 日志，过期显示不可核验；计划到期为 PLAN_EXPIRED；M0 用隔离发现探针，完整安装使用由后续阶段验收。
- OpenSpec strict 通过，正式规格为 23 项需求、77 个场景；24 张 Mermaid 语法通过；Markdown 本地链接、章节锚点、围栏及行尾空白检查通过。
- 本轮仅完善规格，未实施插件、运行实机验收、部署、发布或生成图片。实现与环境验收任务保持未完成状态。


## DSH 多版本兼容性（2026-10-13）

- 新增 compatibility-design.md，定义完整支持/部分可用/未验证/不兼容、精确发行组合、集中适配、运行时能力检查、写入限制、跨版本CI与版本线。同步正式规范、任务和A16验收组。
- npm Registry 实查：@deepseek-ai/dsh latest/next 为 0.2.0-rc.2，alpha 为 0.2.1-alpha.2；@deepseek-ai/dsh-tools latest 为 0.0.1-rc.1、next 为 0.2.0-rc.2、alpha 为 0.2.1-alpha.2。上述是候选来源信息，不是插件已支持结论。来源：https://registry.npmjs.org/@deepseek-ai%2fdsh 与 https://registry.npmjs.org/@deepseek-ai%2fdsh-tools 。
- 独立评审未发现新增设计阻断矛盾。OpenSpec strict、25张Mermaid解析、本地链接及围栏检查通过；当前25项需求、82个场景。未执行真实版本组合验收或插件开发。


## 首版单一 DSH 基线收敛（2026-10-13）

- 兼容分册、正式规范、提案、任务、验收和入口同步为单一精确版本；0.2.0-rc.2 为首选 M0 候选，通过后才能声明支持。
- 删除首版相邻发行线、最低/上界覆盖、多版本框架和强制前瞻检查要求；平台/Registry/拓扑验证保留。
- 必需能力缺口须先明确基线替换决策并重新验证，不静默混入 Alpha 或固定源码专有接口。
- 独立评审确认无首版多版本承诺残留。OpenSpec strict、25 张 Mermaid、本地链接及围栏检查通过；25 项需求、83 个场景。
- 当前为规格确认，尚未执行 M0 或证明候选发行版运行兼容。

## M0 工程与只读适配器实施（2026-10-13）

- 新增 `plugins/dsh/` 独立 private 开发包，含 Host、诊断 Client、Bundle patch、官方生成器构建脚本、锁文件、许可证和开发 README。
- 官方发行产物探针与生成器装配结果见 [M0 报告](../../../docs/research/dsh-native-plugin-2026-10-12/m0-release-probe.md)。构建只暂存官方原始声明，定向生成本插件的 Host/Remote；没有修改官方运行包或用户 Profile。
- 修正骨架测试：使用协议公开的 `parse` 接口检验 codec，并等待 Cordis fiber 加载完成后访问服务；构建路径通过 `fileURLToPath` 解析。
- 新增匿名只读 Registry 适配器：固定 20 条搜索、精确版本解析、固定版本 SKILL.md；校验响应、坐标与版本一致性、URL 分段编码、响应大小和取消状态。保留 401/403/404/429/5xx 差异；不读取 CLI 登录信息。适配器尚未接入 Remote 或业务页面。
- 验证：插件 `bun run check` 构建及类型检查通过；补充真实 loopback HTTP 测试后，`bun run typecheck && bun test` 通过，共 20 项测试、38 个断言。`npm pack --dry-run --ignore-scripts --json` 确认 13 个发布文件，包含许可证、Bundle 与生成产物；尚未执行干净 tarball 安装。
- OpenSpec strict 验证通过。未修改共享 CLI 或后端行为；没有部署、发布、模型调用或真实技能写入。
- 待完成：真实隔离 DSH Profile 加载、Client 挂载、Remote 往返、工具视图和技能发现。已有测试不足以将 `0.2.0-rc.2` 声明为受支持基线，M0 相关任务继续保持未完成。

## M0 原生页面与 Remote 实机贯通（2026-10-13）

- 在隔离的 DSH_HOME 中通过官方 `dsh plugin --profile skillhub-m0 add <tarball> --ignore-scripts` 安装插件，再启动 `0.2.0-rc.2` Web Profile。使用 Node 24.17.0、Playwright 1.58.2 和 Chromium Headless 145.0.7632.6；没有配置模型密钥或调用模型。
- 真实浏览器发现并修复三项问题：Bundle 新节点需要 `insert`；Client 必须编译生产 JSX runtime；`$mount` 后须通过子插件显式注入 `remote.skillhub` 才能调用命名空间。
- 实测侧栏显示 SkillHub，点击进入原生 main 面板，再点击检查按钮，页面显示 `SkillHub M0: connected (read-only)`；浏览器未捕获异常为零。截图及脱敏结果见 [截图](../../../docs/research/dsh-native-plugin-2026-10-12/m0-native-connection.png) 和 [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m0-browser-result.json)。
- `bun run check` 通过，20 项测试、39 个断言；新增执行 Client factory 的回归断言，拒绝未声明 external。实际 tarball 安装已通过，未发布包。
- 按证据完成 tasks 0.2、1.1。其余 M0 项保持未完成：宿主提示默认工作区创建失败，可信上下文及发现需继续调查；bundle 配置与重载、工具视图、Registry 实测亦尚未完成。安装时 pnpm 提示宿主 peers 未在 Profile 中安装，但 DSH 实际成功装配；此行为还需在最终依赖与兼容策略中核验。

## M0 会话作用域与技能发现（2026-10-13）

- 默认工作区失败原因：测试机 `xdg-user-dir DOCUMENTS` 返回用户主目录，rc.2 的默认目录解析明确拒绝该结果。隔离 Profile 使用官方 `workspace-controller.config.documentsDirectory` 指向临时目录；未修改用户 Documents 配置。
- 新增可复跑的 [Host 探针](../../../plugins/dsh/test/integration/discovery-probe.mjs) 与 [运行说明](../../../plugins/dsh/test/integration/README.md)，通过真实 rc.2 Session/Workspace/Skill/Tool 服务验证，无模型请求。
- 通过会话取得的 cwd 与宿主 Workspace 一致；自定义 DSH_HOME 的用户技能和项目技能均被发现、正文被读取，资源基路径保留。另一个项目不见前一项目技能，但可见用户技能；无 Agent scope 的顶层查询不见项目技能。
- 同一 Agent 运行期间新增/移除夹具，发现目录均成功更新，无需重启。探针不是插件安装事务，不据此宣称安装、升级或卸载已完成。
- 官方 `defineTool` 注册的只读测试工具通过宿主工具运行器执行，接收到预期 Agent 和 `exec.callId`。未验证模型自动调用、聊天持久化或 ToolView，tasks 6.1 保持未完成。
- 探针八项行为断言通过，见 [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m0-discovery-result.json)。完成 tasks 0.5，并将 Agent scope、正文 content 与发现路径核对规则回写接入设计。

## 原生配置 Schema 与只读目录服务（2026-10-13）

- 增加 Host Config Schema：registryUrl 默认为未配置，三个可选 Tab 默认为 auto；使用宿主同版 Schemastery 3.18.4。未实现登录或令牌配置。
- 增加 `getContext`、`listSkills`，通过匿名适配器读取真实 HTTP 协议；返回 total/returned/limit，不虚构分页。sourceRevision 在服务实例更换后失效，服务卸载会取消在途请求并返回 context-stale。
- 新增公开 `./types` 子路径，满足官方生成器对 Remote DTO 的要求；构建暂存保留 Host 源码目录结构并显式启用 Node 类型。
- 本地 Cordis 与 loopback HTTP 测试验证默认配置、匿名搜索、空摘要规范化、重载失效、拒绝错误映射及卸载取消。独立脚本确认取消机制正常；修正测试中过早等待 rejects 断言造成的调度问题后，27 项测试、57 个断言全部通过，构建和类型检查通过。
- 新增查询方法尚未完成真实 DSH Client 往返验证，原生配置编辑/持久化、业务 UI 和真实 Registry 契约测试仍未完成；tasks 1.3 保持未完成。

## M0 匿名响应边界补充测试（2026-10-13）

- 新增 `plugins/dsh/test/registry-stream.test.ts`：并发搜索中单个请求取消不影响另一请求；分块 UTF-8 文档正确解码；超限流式响应停止读取并取消流。三项均针对现有只读 Registry 适配器，不涉及技能安装事务。
- 新增测试先单独执行：3 项通过。随后 `bun run check` 首次因测试替身缺少 Bun `fetch.preconnect` 类型字段失败，补齐替身后复跑，构建、类型检查和 23 项测试全部通过，失败 0。
- `bun test --coverage --coverage-reporter=text --coverage-dir=/tmp/dsh-qualification-coverage-20261013`：23 项测试通过；Bun 报告的已加载文件合计函数覆盖率 91.38%、行覆盖率 93.63%。统计只包含本次被加载的 `lib/index.js`、`lib/typert.remote-client.js` 和 `src/host/registry-adapter.ts`，不代表整个插件，更不代表安装/升级/卸载复杂场景覆盖率。
- 这是对已存在实现补充测试并修正测试替身的记录，不作为“先写失败业务测试再实现功能”的 TDD 证据。未执行静态代码分析器、真实 Registry 联调或写操作验收。


## 原生配置持久化与固定版本内容接口（2026-10-13）

- 原生插件详情配置页已通过官方 configForms 保存 Registry 地址和 Tab 开关；隔离 Host 重启后重新打开配置页，地址、versions=show、reviews=hide 正确回显。再次只读浏览器验证通过，pageerror 为零。截图见 [配置页](../../../docs/research/dsh-native-plugin-2026-10-12/m0-native-config.png)。测试地址仅验证配置持久化，不代表真实 Registry 可访问。
- 配置使用 volatile 引用，Host 消费时读取最新值。来源改变更新 sourceRevision，Tab 开关改变保留来源身份。Client 用编辑时 revision 提交字段修改，避免全量覆盖配置。
- 新增 getSkillDetail 的只读内容阶段：显式版本 resolve 后读取同版本 SKILL.md，返回身份及说明状态；不返回下载链接。说明缺失不丢弃已解析身份。安装授权快照、元信息和可选模块尚未完成，该方法不提供安装授权。
- 抽取查询来源检查，成功和失败响应均拒绝已失效来源；新增固定版本路径、说明 404、请求中来源切换的成功/503 四项行为测试。
- `cd plugins/dsh && bun run check`：构建、类型检查通过，32 项测试、69 个断言通过。新增详情接口仅完成本地 Cordis/HTTP 测试与官方 Remote 生成，尚未完成真实 DSH Client 往返和业务页面验收。
- tasks 0.3、1.3、2.2 保持未完成：配置持久化证明不覆盖依赖重载、完整清理及全部业务契约。


## 原生浏览与详情页面首轮贯通（2026-10-13）

- Client 主面板从诊断页面切换为技能浏览：提交搜索、返回范围说明、空结果、清空和重试；点击卡片打开固定版本详情子页面，返回保留关键词并恢复卡片焦点。Client 生命周期内导航状态与 QueryClient 由插件持有，卸载清理缓存。
- 复用仓库前端锁定版本的 TanStack Query 5.90.21、react-markdown 10.1.0、rehype-sanitize 6.0.0，打入 Client 产物；宿主仍只提供 React/JSX runtime。新增 Playwright 1.58.2 为开发验证依赖。
- Markdown 禁止原始 HTML、危险链接及自动远程图片请求；真实浏览器验证实体解码正常、脚本未执行、危险链接不可点击、图片无外部请求。依赖在 factory 执行时创建实体解码元素，包测试用最小 DOM 替身验证延迟加载；正文渲染以实际 Chromium 为证据。
- 将本次 tarball 安装到隔离 DSH rc.2，通过真实 Client → Remote → Host → loopback HTTP 夹具完成搜索及详情；返回焦点、关键词保留、空结果及清空恢复通过，浏览器 pageerror 为零。新探针与夹具见 `plugins/dsh/test/integration/`，运行方式见其 README。
- [浏览结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-catalog-result.json)、[详情截图](../../../docs/research/dsh-native-plugin-2026-10-12/m1-catalog-detail.png)、[窄窗口列表截图](../../../docs/research/dsh-native-plugin-2026-10-12/m1-catalog-list.png)。这是 HTTP 夹具联调，不是实际 SkillHub 服务端验收。
- 最新构建、类型检查通过，32 项测试、70 个断言通过。实机复跑包含返回焦点修复；尚未证明长列表滚动、无活动会话隐藏返回入口、全部国际化和主题样式等完整交互。
- 已安装入口当前不可操作；可选详情 Tab、安装区、对话入口、完整元信息与安装快照仍待实现。tasks 2.1–2.5 保持未完成，不以本轮只读页面代替完整产品交付。

## 可选详情模块与只读快照（2026-10-13）

- 按实际 Java DTO 接入技能元信息、版本页及评论页；只保留公开展示字段。评论使用 skillId，与 resolve 返回的 versionId 分开，不将账号 ID、审核原因和权限字段发往 Client。
- getSkillDetail 生成 Host 内存中的只读 detailToken，保存来源、坐标、固定版本和指纹；最多保留 100 个引用，来源切换和卸载清空。当前引用未绑定写操作所需项目/会话上下文，不提供安装授权。
- auto 模式以 size=1 探测版本/评论，缓存资源能力（最多 200 条）；hide 不探测，show 延迟至切换 Tab 才读取。200 空集合表示可用，403 保持匿名拒绝，404/503 保持未知，不推断为不支持。已知匿名拒绝的审核不发请求。
- 新增 listVersions/listReviews Remote，只接受 Host 保存的详情引用和页码；正文分页 size=20，校验响应页码和大小，配置隐藏或来源失效后拒绝旧引用。
- 原生详情页支持四类 Tab、键盘方向键/Home/End、版本与评论分页、空内容、拒绝/失败提示和重试；版本历史展示不改变已选择的详情版本。
- 新 tarball 在隔离 DSH rc.2 实测：通过原生配置页设置版本/评论 auto、审核 show；切换 Tab 得到版本说明与评论正文，审核显示匿名不可访问提示；原搜索、返回焦点与 Markdown 安全检查继续通过，pageerror 为零。证据见 [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-modules-catalog-result.json) 与 [截图](../../../docs/research/dsh-native-plugin-2026-10-12/m1-modules-catalog-detail.png)。
- 构建通过；最新类型检查与测试为 38 项、108 个断言，全部通过。新增测试覆盖能力探测缓存、hide 零请求、ID 区分、源切换、可选字段投影、分页输入/响应错误与 403/404/503 语义。
- 实机仍使用 HTTP 夹具，不等于真实 Registry、SaaS 或多平台验收。可选模块并发探测合并、全部键盘/主题/国际化及写操作上下文仍须在后续阶段补齐；不据此勾选完整 2.3 验收。


## 原生对话搜索工具（2026-10-13）

- 确定本包工具前缀 `astron_skillhub_`：搜索 `astron_skillhub_search`，后续安装 `astron_skillhub_install`。同步正式文档中的拟议名称；参考插件的原始调研名称不改写。注册时检测同名工具并拒绝覆盖。
- 使用候选宿主同版本 `@deepseek-ai/dsh-tools` peer，Host 在 tools 服务可用时注册搜索，生命周期结束释放。工具仅接受 q，检查额外参数与长度，trim 后调用共享目录服务；要求真实 exec.agent.session 和 exec.callId。
- 输出使用官方工具 schema 校验，包含来源快照、时间及有界条目。presentationMeta 只投影保存结果，不请求当前来源。Host 为选择引用保存真实会话/项目/调用归属，最多 400 条，来源变化和卸载清空；这些引用尚未接入 Client 查看确认或安装，不代表写入授权。
- 真实 rc.2 隔离 Host 工具执行器通过业务搜索、trim、selectionRef、展示元数据一致性、无目录泄露、拒绝额外 path 参数与缺失 Agent；无模型密钥或模型调用。证据见 [原生搜索结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-native-search-result.json)。实机包为 build10；随后注册 helper 的入参从完整 Context 收窄到 get/register 接口，行为不变并补充单测。
- 最新构建、类型检查、40 项测试与 114 个断言通过。新增注册冲突/释放和历史展示纯投影测试。搜索方法不是 Remote 写入口，官方生成方法清单继续通过检查。
- 自定义 ToolView、对话详情抽屉、会话绑定 Remote、展示确认以及安装工具尚未完成；tasks 6.1–6.4 继续保持未完成。


## 原生搜索 ToolView 与历史重放（2026-10-13）

- 按已安装 rc.2 的 ToolCallViewProps 接入 `tool.call.toolview`，key 为 `astron_skillhub_search`；处理 preparing/start/result/error，不在历史渲染时查询当前 Registry。开发类型依赖固定为同版本 ui-tool/ui-slots/ui-chat/ui-conversation，仅有类型引用进入源码，Client 继续使用宿主 React。
- 对 presentationMeta 进行严格边界校验：schemaVersion、来源 URL、UUID 引用、时间、返回范围、条目字段。未知版本、数量不一致、危险/带凭证地址、额外路径或无效引用退回说明文字，不猜测操作目标。新增 11 个解析测试。
- 构建、类型检查和全部 51 项测试通过。修正测试对象字面量推断过宽引起的类型错误后复跑通过；未绕过检查。
- 真实 Host 工具输出写入隔离 Session；合成 Assistant/流事件明确标注测试夹具且无模型调用。初次浏览验证只见 Completed，进一步检查 DOM 确认宿主默认折叠回合及 Called tools 分组；修正探针为点击实际过程按钮和工具分组，未更改宿主 UI 或业务组件规避。
- 最新 tarball 的自定义搜索卡片已实际显示，浏览器刷新后重放通过，pageerror 为零。见 [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-search-card-result.json) 与 [截图](../../../docs/research/dsh-native-plugin-2026-10-12/m1-search-card.png)。可复跑脚本为 `test/integration/search-card-probe.mjs`。
- 当前卡片只展示历史摘要和调用详情入口；技能详情抽屉、宿主验证会话归属的 Remote、展示确认、安装工具/卡片仍未接入。此轮不勾选完整对话交付任务，也不代表真实模型或外部 Registry 验收。


## 会话作用域详情与展示确认 Host 链路（2026-10-13）

- 增加 `@RemoteScope('agent')` 的 openConversationDetail / acknowledgeConversationDetail。生成描述符包含官方 SessionId codec，业务参数只有引用及确认所需 detailToken，不接收项目路径。Host 比较真实 Agent 的 session.id/cwd 和保存的来源修订。
- 打开详情仅保存 pendingDetailToken；确认必须匹配该 token 及 Host 保存的坐标、版本和来源，成功后记录 viewedDetailToken。尚无安装入口，不把确认解释为安装授权。
- rc.2 生成器不能直接建模外部 Agent/Session 边界：临时工作区增加原始声明及公开导出映射，使用 scoped Remote 避免完整 Agent 进入业务 DTO。源码声明按原样复制，未修改官方生成器。
- 首次实机通过 ctx.get('agent') 未得到 Agent；改用 Gateway 校验后的 invocation.request.args.agentId 与宿主 agents 注册表后贯通。真实 Gateway 已验证同会话固定版本详情、跨会话拒绝、伪造 token 拒绝及正确确认。证据见 [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-scoped-detail-result.json)。测试在隔离 Profile、HTTP 夹具、无模型调用条件下执行。
- 最新构建、类型检查、51 项测试和 127 个断言通过，包测试增加两个方法的 agent 作用域描述符断言。实际安装包 build13 的 Host Gateway 调用通过；尚未验证 Client scoped Remote 往返、抽屉展示确认或完整 selectionRef 提升协议，相关对话任务不勾选完成。


## 对话详情抽屉 Client 贯通（2026-10-14）

- 搜索卡片增加查看技能详情入口；右侧原生 dialog 复用广场 DetailContent、可选 Tab 和安全 Markdown。Escape/关闭恢复触发按钮焦点，Tab 使用实例唯一 ID，避免多个详情容器的 ARIA 关联冲突。
- 通过 ToolView 提供的会话 ID 借用 sessions.scope；每次调用在保留身份的子上下文显式注入 remote.skillhub，结束后释放。首次实机发现直接访问借用上下文的 Remote 会被 Cordis 拒绝，修正后通过；未绕过宿主依赖检查。
- 页面可见且详情已渲染后提交展示确认；确认失败独立提示。确认不授权安装。Host 使用的 dsh-session 运行依赖已显式列入精确 peer。
- 构建、类型检查、51 项测试与 127 个断言通过。build16 tarball 在隔离 rc.2 Web Profile 的浏览器探针通过：固定版本详情、版本/评论内容、真实 scoped 确认响应、Escape/关闭、焦点恢复、历史卡片重放以及 Host 重启后旧引用拒绝。浏览器 pageerror 为零。
- 证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-conversation-detail-result.json)、[抽屉截图](../../../docs/research/dsh-native-plugin-2026-10-12/m1-conversation-detail.png)、[失效提示](../../../docs/research/dsh-native-plugin-2026-10-12/m1-conversation-detail-stale.png)。测试使用本地 HTTP 夹具和合成对话记录，未请求模型或真实 Registry。
- 提取共享 DetailContent 后，原广场浏览器探针回归通过：搜索、固定版本详情、可选 Tab、安全 Markdown、返回保留条件/焦点、空结果和清空；pageerror 为零。OpenSpec 严格校验通过。
- 尚缺固定版本 selectionRef 提升、重新查看失效记录的完整流程、安装目标/状态及模型驱动验收；6.3、7.2 等完整任务继续保持未完成。视觉、主题、国际化和完整平台验收也未完成。


## 已查看固定版本引用关联（2026-10-14）

- Host 使用有界 ConversationSelections 保存搜索候选、独立固定引用和展示关联。openConversationDetail 返回新的 selectionRef；Client 使用该引用及 detailToken 确认。未确认引用不能被内部安装适配入口解析。
- resolveConversationSelection 仅为 Host 内部方法，不新增 Remote 或模型工具。原搜索引用解析为已确认快照，携带精确版本、versionId 和 fingerprint；不请求 latest。不同已查看内容身份导致原搜索引用歧义，显式固定引用仍保留各自身份。真正写入前的远端内容及目标重校验属于尚未完成的安装事务。
- 测试覆盖未展示拒绝、旧确认与伪造 token 拒绝、确认后关联、返回值不可修改内部快照、内容变化歧义、会话/项目/来源变化、清空和父引用淘汰。构建、类型检查、56 项测试与 148 个断言通过。
- build17 在真实隔离 DSH 的 Gateway 和浏览器验证通过。探针从响应和请求中确认“打开返回新固定引用 → Client 使用该固定引用确认 → Host 返回成功”，并复测详情模块、关闭/焦点、历史重放和过期拒绝；pageerror 为零。见 [固定引用结果](../../../docs/research/dsh-native-plugin-2026-10-12/m1-fixed-selection-result.json)。仍使用 HTTP/对话夹具，无模型请求。
- 尚未接入安装工具和版本切换选择；不据此宣称安装授权、完整对话安装或 7.2 全部验收完成。


## 共享安装核心入口与规范化锁顺序（2026-10-14）

- 新增 `cli/src/integrations/local-skills.ts` 内部源码入口，导出安装/升级核心、清单与目标锁能力；不作为公开 SDK 发布。调用方显式传入来源、身份和目标，插件匿名调用不得传入 token。插件实际消费及事务观察器仍待接入。
- 新增 orderSkillTargetLocks，按 acquireSkillTargetLock 相同的规范化锁身份去重并稳定排序。installSkill 使用该顺序取锁，升级复用同一安装函数；未在插件外层重复取锁。安装预检仍拒绝多个目标指向同一目录，不改变 CLI 重复目标策略。
- 新增路径别名/相反输入顺序/相同锁去重测试，以及内部入口独立 Node bundle 测试。后者检查导入闭包不含 CLI main、commands、cac 或 prompts，在 Node 中动态导入成功、无 CLI 输出且未产生额外文件。
- 相关 25 项测试通过；`make test-cli` 全量 562 项测试、1778 个断言通过；`make typecheck-cli lint-cli build-cli` 全部通过。任务 1.2 完成。
- 事务日志、下载前幂等、提交 CAS、提交后告警、精确卸载仍未完成；3.4a 等完整事务任务不勾选。下一步需增加可等待观察器，并区分清单提交前可回滚和提交后仅报告警告的错误边界。


## 安装事务阶段观察与提交后告警（2026-10-14）

- InstallOptions 增加内部 transactionObserver；beforeMove/afterMove 使用预先确定的路径、规范化目标、Agent 和顺序。beforeMove 被 await 后才 rename；移动成功先更新核心实际状态，再 await afterMove，避免观察器失败时误判是否已经移动。备份路径使用随机唯一标识。
- InventoryStore 在持有清单锁期间提供提交前 before/after 副本；观察器不能改写待提交清单。beforeInventoryCommit 失败阻止清单写入并由安装核心恢复旧文件。完整预期记录/目标集 CAS 尚未补齐。
- 清单写入返回成功后标记逻辑提交完成。afterInventoryCommit 及 cleanupResult 失败仅返回结构化 transactionWarnings 和兼容的 CLI warnings；不回滚已提交文件。备份删除失败不再吞掉，报告实际残留路径。
- 新增故障测试：移动前/移动后/清单提交前日志失败恢复旧状态、移动前等待日志完成、提交后与清理记录失败保留新版、观察器无法改写清单、实际文件权限导致备份删除失败后保留提交结果并报告路径。权限故障测试在本次 Linux 非 root 环境实际执行通过；Windows/root 下跳过该权限用例。
- `make test-cli` 全量 568 项测试、1820 个断言通过；`make typecheck-cli lint-cli build-cli` 全部通过。
- 仍缺持久 operation/journal、崩溃恢复、CAS 未知结果核验、升级适配器观察器传递和暂存清理失败处理；事务相关完整任务继续保持未完成，插件写入口仍未开放。


## 清单身份与完整目标集合 CAS（2026-10-14）

- 安装核心在下载前捕获受影响清单基线，在持有目标锁后再次检查，并在 InventoryStore 的清单锁内、持久写入前检查最新 before 快照。比较来源坐标、版本、指纹、完整目标集合、安装时间与所有权；规范化路径及集合顺序，沿用旧记录缺省 direct 语义。
- 比较范围包含同来源坐标的完整记录及占用本次目标路径的其他来源记录；无关技能清单变更不造成冲突，提交时保留其最新值。
- InstallOptions 增加 expectedInventoryItem：明确计划快照必须匹配，null 表示预期该坐标尚未安装。executeSkillUpgradePlan 传入计划中的 inventoryItem，过期计划在下载前拒绝；不是仅比较新版本是否更大。
- 故障测试在文件已移动、清单提交之前插入版本、指纹、所有权、目标集合和来源变更；均拒绝提交，旧文件恢复，其他写入者的新清单记录不被覆盖。另验证无关记录可共存及过期计划不触发下载。目标集合用例保持同版本同指纹，证明不只是命中原部分升级限制。
- `make test-cli` 全量 574 项测试、1843 个断言通过；`make typecheck-cli lint-cli build-cli` 全部通过。
- 尚未实现持久操作日志、CAS 抛错后未知结果核验、崩溃恢复和插件可信目标/请求去重；不据此开放插件写入口或完成完整生命周期验收。


## 持久操作存储与重复请求恢复（2026-10-14）

- 新增内部 OperationStore：受保护状态目录、持久 hostInstanceId、同记录内的请求映射/操作数据、原子替换与 revision 更新。文件同步后 rename；POSIX 同步父目录。读取拒绝符号链接、超限和不匹配的记录；损坏记录不被当作请求不存在。
- 原始平面业务参数按字段排序后计算 SHA-256；仅保存摘要，不保存原 token/ref 内容。先按当前 Host/ownerId/requestKey 查重；同摘要返回原记录，冲突拒绝，缺失 retry 返回未知。新请求校验在日志锁外执行，随后加锁再次查重；存储层不执行安装或重放操作。
- 状态修订冲突和非法回退被拒绝，已完成文件结果不能被后续状态更新改写；允许完成后的发现结果确认。上下文中的原来源和创建时间保持不变。内部记录不直接作为 Client DTO。
- 针对性测试覆盖重启找回、旧 token 不重校验、冲突和未知重试、归属隔离、五个独立存储实例并发接受同一请求、修订冲突、终态不可重放、损坏日志及摘要稳定性。
- `make test-cli` 全量 580 项测试、1863 个断言通过；`make typecheck-cli lint-cli build-cli` 全部通过。独立集成入口 Node 打包测试随全量测试通过；OpenSpec 严格校验通过。
- 尚未把 store 绑定到 DSH 的可信状态目录/写入口，也未保存逐次移动路径、清单提交快照或执行启动恢复。Windows 持久化、断电/进程强杀及日志保留清理未验收；7.1 和完整恢复任务继续保持未完成。


## 安装阶段持久日志接通（2026-10-14）

- OperationStore 增加严格校验的内部 transaction 记录与 recordTransaction 修订更新；记录移动前意图、移动完成事实、清单 before/after、逻辑提交确认和备份清理结果。日志大小写入前限制为 1 MiB，超过限制不会产生无法再次读取的新记录。
- InstallMove 在每次移动前携带 stagingDir 和 backupDir；即使在备份移动后中断，也已经知道暂存路径。createInstallTransactionObserver 将共享安装核心的 awaited hooks 写入指定归属、指定 revision 的持久操作；只接受已经进入 staging 的 install/upgrade，不启动或重放任务。
- 未记录的 afterMove、未完成移动时提交清单、旧 revision、无提交事实的清理事件会被拒绝。清单快照使用现有 Inventory 结构，Registry 身份禁止凭证和查询参数，不保存归档、说明正文或下载 URL。内部 transaction 不直接提供给 Client。
- 真实临时目录安装测试验证：rename 前日志可读取且暂存内容仍在；清单实际写入前可读取完整 before/after；重新打开存储后仍可读取移动和提交事实。另验证事件顺序及旧写入者拒绝。该测试使用本地 ZIP 客户端，不访问外部服务。
- `make test-cli` 全量 582 项测试、1875 个断言通过。修正测试夹具的 Response 字节类型和必填 downloadUrl 后，针对性测试复跑通过；`make typecheck-cli lint-cli build-cli` 全部通过。
- 操作最终结果核验、失败/恢复记录、启动扫描、强杀窗口验证、精确卸载和 DSH Host 接入仍未完成；不能把日志 committed 字段当作完整安装验收。


## 提交结果不确定时的锁内核验（2026-10-14）

- InventoryStore.mutateAtomic 保留原始 before，写入报错后在仍持有清单锁时重读。与 after 一致视为已提交；与 before 一致抛出原未提交错误；无法读取或不匹配两者时抛出 InventoryCommitUnknownError。传给观察器的 before/after 均为副本，不能污染核验基线。
- InventoryStore.read 仅把 ENOENT 解释为空清单；权限错误、解析失败等继续抛出，避免把无法读取误判成未提交。
- 安装核心遇到无法判定的提交结果时保留新目录、旧备份及暂存路径，返回结构化 INSTALL_COMMIT_UNKNOWN/recovery=unknown，不执行普通失败回滚。当前适配错误不宣称已安装，也不自动重试。
- 新增故障用例覆盖 rename 前错误、rename 后响应丢失、落盘既非 before 也非 after、重读失败，以及确认/未知两种安装后目录状态；未知时新旧内容均保留。
- 首次全量回归发现原先引入的 CAS 可能把并发首次安装的目录冲突返回为 validation。已调整目标锁内检查顺序：先保留首次安装的既有目录冲突结果，再检查清单 CAS；跨进程并发和安装核心 34 项针对性测试复跑通过。
- 修正后 `make test-cli` 全量 588 项测试、1888 个断言通过；`make typecheck-cli lint-cli build-cli` 全部通过。
- 启动恢复、未解决目标的写入阻断及强杀多目标恢复仍未完成。


## 恢复扫描入口与目标写入阻断（2026-10-14）

- OperationStore 新增内部 listForRecovery，扫描未完成、恢复未结束的操作和已记录的清理残留；不向 Client 暴露跨会话日志。损坏记录仍阻止扫描/新接受，不当作空状态。
- 内部 accepted 输入可保存可信规范化 targetPaths。安装日志适配器要求目标绑定；beforeMove 也必须属于该集合。旧开发记录缺失绑定可读取，但不能直接创建可执行的安装观察器。
- beforeTargetMutation 在下载前和取得目标锁后检查：本次目标必须等于 accepted 绑定集合，不能由新参数替换。其他操作已有移动意图、未知提交或恢复未结束时，重叠目标返回 TARGET_RECOVERY_REQUIRED；无法确定这类记录的目标时保守阻断。无移动意图的纯准备请求不互相阻塞。
- 测试覆盖重开存储后的扫描、重叠阻断、无关目录允许、伪造集合拒绝以及显式 restored 状态后的放行。后者只证明状态门禁，不代表已经实现文件恢复核验器。
- 真实安装用例分别在下载前、下载期间写入另一会话的未核验移动意图；均在目标变更前阻断，清理本次暂存；前者未调用下载，后者在锁内二次检查被拒绝。
- `make test-cli` 全量 591 项测试、1897 个断言通过；`make typecheck-cli lint-cli build-cli` 全部通过。
- DSH 启动挂载、实际恢复分类/文件恢复、清理结果日志丢失后的残留发现和强杀验收仍未完成；此入口不会自动恢复文件，也不自动重放安装。

## 清理结果缺失的恢复扫描（2026-10-14）

- listForRecovery 对已确认清单提交的事务逐一核对备份清理结果；缺失成功确认的备份与明确清理失败的记录均进入核验队列，即使操作已标记 completed。相同路径以最后一次清理结果为准。
- 增加真实临时目录替换测试，覆盖清理日志写入抛错、记录失败及记录成功三种情况。日志写入抛错产生提交后告警；重开存储仍发现待核验记录；扫描不修改已安装文件、不重新执行历史操作。测试中的实际清理已经完成，因此日志缺失仅表示需要核验，不能当作备份仍存在的事实。
- `bun test test/unit/integrations`：15 项测试、60 个断言通过；CLI 类型检查、lint、构建通过。本轮修改只影响内部恢复扫描，未重跑 CLI 全量测试。
- 实际文件恢复分类、清理核验后的持久确认、DSH 启动绑定和强杀验收继续待实现，不据此勾选完整恢复任务。

## 提交后真实状态核验（2026-10-14）

- 新增内部 inspectInstallRecovery，重新验证可信目标集合、规范化根路径，按共享 canonical lock 顺序获取全部目标锁后读取原操作。检查真实 inventory 的受影响投影与 before/after，并逐目标验证 metadata、来源、版本、Agent、安装时间和完整文件指纹。符号链接、特殊文件、所有者不符和读取错误均不确认成功。
- 实际内容和 inventory 均匹配 after 时，即使 committed 日志未成功写入，也返回 committed。当前清单匹配 before 时只返回 inventory-before，不声称文件已恢复或操作从未提交。没有提交快照、无法区分的快照及冲突返回 unknown；扫描不更新原记录或移动/删除文件。
- 9 个真实临时目录用例覆盖两个目标、提交确认丢失、第二目标内容修改/缺失/符号链接/metadata 冲突/清单所有权变化、共享锁占用、目标集合缺失、无关清单新增及 before 快照。失败锁获取后释放已取得锁，后续可正常核验。使用本地 ZIP 夹具，不调用远端服务。
- 日志 schema 对可选 inventory 字段省略 undefined，使持久记录类型与共享 Inventory 的 exactOptionalPropertyTypes 一致；未改变已有字段含义。
- `make test-cli`：603 项测试、1933 个断言通过；锁释放代码调整后额外复跑 9 项恢复测试通过。`make typecheck-cli lint-cli build-cli`、OpenSpec 严格校验及 `git diff --check` 通过。
- 尚未实现提交前移动阶段恢复、备份恢复/清理执行器和核验结果持久确认，也未绑定 DSH 启动过程。该核验结果仅代表持锁检查时的状态，不能在释放锁后直接用于写入。

## 已提交结果的锁内持久确认（2026-10-14）

- 新增 reconcileCommittedInstallation，复用全部目标及清单核验，在目标锁仍持有时通过 InventoryStore.withLockedSnapshot 保留 inventory 锁，最后取得 journal 锁并按 revision 持久确认。操作完成状态和 committed 事实在同一原子记录内更新；重复确认不改 revision、不重放安装。
- 未确认备份清理时，完成状态保留单独告警且仍进入清理核验队列。发现状态保留 unconfirmed，不把文件成功等同于 DSH 已发现/加载；原请求映射、来源和创建时间保留。
- 测试在确认日志的实际调用点验证两个 target locks 与 inventory lock 均被持有；覆盖重开存储、同 requestKey 查询原完成记录、重复确认、内容冲突、before 状态、日志写失败、revision 竞争及双目标替换后清理确认缺失。另验证只读清单回调抛错不会写入修改且释放锁。
- `make test-cli`：610 项测试、1971 个断言通过；最终相关测试复跑 25 项、76 个断言通过。CLI typecheck/lint/build、OpenSpec 严格校验和 `git diff --check` 通过。
- 提交前恢复、备份恢复/清理执行器、DSH Host 启动绑定与进程强杀验收仍未完成。此次仅确认实际已提交的安装/升级，不对未知现场执行删除或回滚。

## 已提交事务的备份清理（2026-10-14）

- 新增 cleanupCommittedInstallation：在全部目标锁及 inventory 锁内先核验当前安装已提交，再逐项核验并清理日志绑定的旧备份。检查同级 UUID 路径、规范化父目录、before 版本/来源/时间/Agent/单一 direct 所有权、metadata 文件集合及实际指纹；不能确认时保留目录。
- recordRecoveredCleanup 按 revision 持久替换该路径的清理结果，保留安装完成事实；全部备份确认后清除专属告警并退出清理队列。日志写失败停止后续删除；下次对已不存在的路径按 ENOENT 核验后补记，不重放安装。已成功清理的路径后来出现新内容时不再处理。
- 故障夹具通过真实 inventory 提交后抛出未知结果，保留两个真实备份，验证安全清理。其他用例覆盖实际已删除但日志丢失、内容修改、嵌套符号链接、metadata 来源变化、伪造目标路径、首次清理结果写失败后的重开存储，以及 POSIX 权限拒绝；权限用例在本机实际执行通过，Windows/root 跳过该权限夹具。
- `make test-cli`：618 项测试、2041 个断言通过；最终恢复专项 23 项测试、128 个断言通过。CLI typecheck/lint/build、OpenSpec 严格校验通过。
- 提交前移动恢复、DSH 启动与 UI 接入、真实进程强杀和跨平台验收仍待完成。此次未修改用户真实技能目录，全部删除测试限定在临时夹具内。

## 第一处移动前的持久恢复基线（2026-10-14）

- 增加 InstallPreparation 和可等待 beforePreparation。全部目标锁持有期间，在 inventory 锁内重验当前清单，持久保存 before、固定 release 身份/版本/指纹及全部目标的暂存目录、安装目录、Agent、安装时间，再进入 rename 循环。
- 原生事务观察器拒绝未持久准备记录就调用 beforeMove；日志验证 release 与 accepted 一致、目标集合完整且无重复。后续移动的索引/目标/Agent/暂存路径必须符合准备记录，所有准备目标的 install 移动完成后才接受清单提交意图。旧开发记录缺少 preparation 时仍可读取，但不是可用的恢复基线。
- 真实双目标测试在第一处 beforeMove 重开日志，确认两份暂存内容都存在且目标均未移动；路径替换被拒绝。准备确认失败的首次安装与覆盖安装均保持原目录和清单，清理暂存目录；另覆盖跳过准备、重复准备和目标未全完成就提交。
- `make test-cli`：622 项测试、2068 个断言通过；事务专项最终复跑 11 项、58 个断言通过。CLI typecheck/lint/build、OpenSpec 严格校验及 diff 空白检查通过。
- 此次补齐提交前恢复所需证据，不代表已实现中断后的文件恢复执行器。进程强杀、恢复动作持久记录、DSH Host/UI 接入继续待完成。

## 提交前恢复执行器与真实进程强杀（2026-10-14）

- 新增 recoverInstallation 与内部 install-rollback：在全部目标锁及 inventory 锁内区分已提交 after 和未提交 preparation.before。已提交时确认并清理备份；未提交时先验证整个集合的旧/新身份、指纹、安装时间、所有权及实际位置，再按移回暂存、恢复旧备份、清理暂存的顺序执行。不存在可信基线或任何目标有冲突时保留现场。
- recoveryActions 在每次文件变更前持久写意图，执行前重新核验，变更后记录结果。重启依据实际文件位置重新计算剩余动作；最终验证所有目标与 before 清单后记录 failed/restored，不回写旧 inventory。恢复中断/日志失败仍保留阻断状态。
- 抽出恢复路径/文件类型核验 helper。指纹算法排除 `.skillhub`，因此恢复核验额外拒绝 metadata.json 以外的内部目录内容和嵌套 `.skillhub`，防止清理未计入指纹的用户文件。
- 真实子进程测试启动 14 组隔离安装夹具，在准备完成、备份/安装移动前后、清单提交前后强制 SIGKILL，并等待实际 target/inventory 锁超时。覆盖首次安装、双目标替换、提交后日志缺失和第二目标被修改。另在三个恢复动作完成而结果未记入日志时强杀恢复进程，重新打开存储后均完成核验与恢复。测试不调整锁时间戳，也不以单进程抛错替代强杀。
- POSIX 强杀专项：1 项矩阵测试、148 个断言通过，约 66 秒；Windows 明确跳过该专项，未声明通过。`make test-cli` 全量 624 项、2224 个断言通过，约 145 秒。CLI typecheck/lint/build、OpenSpec 严格校验及 diff 空白检查通过。
- tasks.md 的 3.4a 共享核心接缝标记完成。完整恢复任务仍未完成：DSH 正常禁用/启动挂载、跨平台强杀、准备日志写入前的中断处理以及插件实际安装入口仍待交付。

## 插件 tarball 内置共享安装核心（2026-10-14）

- plugins/dsh 构建新增 build-core.ts，复用 CLI 包元数据生成脚本，从内部集成入口生成 Node ESM 私有产物 lib/local-core.js，并纳入 npm files。核心依赖随产物打包，CLI Zod 与插件 Zod 不混用；构建闭包拒绝命令入口、凭证存储和交互依赖，没有新增生产依赖。
- 新增实际 npm pack 测试：tarball 解压到仓库外临时目录，没有源码及 node_modules，由 Node 导入内置核心、安装包含资源文件的技能、持久确认提交、重开操作存储并核对完成状态与空恢复队列。测试覆盖 OperationStore schema、目标锁、ZIP 解包、指纹、inventory 及恢复入口的实际执行，禁止外部 fetch，不调用 SkillHub CLI 可执行文件。
- `bun run check` 通过：插件构建、类型检查和 57 项测试、155 个断言通过；打包专项 1 项、7 个断言通过。仅改插件构建及测试，未改 CLI 核心，本轮不重复运行 CLI 全量回归。
- 更新插件 README 的构建依赖准备与内部产物说明。Host 尚未加载该核心来接收写操作；可信上下文、状态目录、启停挂载和安装按钮继续待完成，不据此声明 DSH 完整安装流程通过。

## DSH Host 内置核心初始化与持久身份（2026-10-14）

- 新增私有 NativeRuntime 契约与核心适配，构建时生成纯类型边界，Host 不导入 CLI 源码或 Zod 3 类型。按已安装官方发行包核对并固定 dsh-home-paths peer 为 0.2.0-rc.2，复用 resolveDshHome/canonicalizeWatchPath；状态日志固定在解析后 home 的 skillhub/operations。
- Host 在 sessions 注入就绪后加载包内核心、打开持久身份并扫描待恢复日志；不自动重放历史操作。依赖卸载/插件禁用清理引用，初始化失败不会破坏只读目录服务。内部 getLocalRuntimeStatus 未注册为 Remote；操作路径和跨会话记录不直接暴露给 Client。
- 编译 Host 的隔离 Node 测试覆盖：无 sessions 不创建状态、自定义 DSH_HOME、重载身份稳定、待处理记录只扫描不执行、依赖移除及损坏日志。插件 build/typecheck 与 58 项测试、157 个断言通过；CLI typecheck/lint/build 及相关 8 项测试、29 个断言通过。安装核心算法未改，本轮未重跑 CLI 全量。
- build18 tarball 在隔离 DSH 0.2.0-rc.2 安装。初次额外探针因位于同包目录导致 Client 来源冲突；已将额外探针复制到隔离根重新配置，重新启动并确认 Web 就绪与进程运行。随后正常关闭再启动，主机身份保持一致；证据见 m2-runtime-first-start.json 和 m2-runtime-restart-result.json。
- 重启后的真实浏览器回归通过搜索卡片、历史重放、详情抽屉、scoped acknowledgment、固定引用、可选模块、关闭及焦点恢复；见 m2-runtime-browser-result.json。本次未传旧历史夹具参数，因此不把 staleHistoryRejected=false 计为旧引用验收。
- 目标归属重新解析、启动恢复调度、运行中写操作的禁用收尾、页面/对话安装入口仍未接通。此次证明 Host 初始化及只读扫描，不证明完整安装管理交付。

## 安装目标引用校验（2026-10-14）

- 新增 Host 内部 InstallationTargets：对外描述仅包含随机 targetToken 和 scope，私有记录绑定会话、项目、来源、详情及规范化目标。消费时要求 Host 重新解析，并核对目标与 DSH home；解析期间发生清理或淘汰也拒绝旧引用。
- `bun test test/installation-targets.test.ts` 通过 5 项测试、15 个断言；`bun run typecheck` 通过。覆盖伪造及跨上下文引用、路径变化、策略拒绝、异步期间失效与容量淘汰。
- 尚未接入真实宿主路径/策略解析及 Remote 安装入口，不能将内部引用测试视作可信目标端到端验收；任务 3.1 保持未完成。

## 默认本机技能目录解析（2026-10-14）

- 新增 installation-paths.ts，依据已安装 DSH 0.2.0-rc.2 的 FileSystem 契约核对 Host 映射，使用官方 home 解析与路径规范化；项目根采用最近 .git 祖先，无 Git 时使用当前目录。解析过程不创建技能目录，拒绝技能根重定向、非本机路径映射及取消请求。
- 直接 Node 安装核心不能执行沙箱的逐调用策略，因此此解析器拒绝沙箱文件系统。自定义 skill provider 根仍需独立核验；本模块尚未接入 Remote，不声明所有宿主配置已支持。
- 目标解析及引用专项测试覆盖临时目录、Git worktree 文件、自定义 DSH_HOME、路径映射与策略拒绝；后续必须通过真实 Agent 上下文及安装链路验收。

## Host 安装目标查询接入（2026-10-14）

- 新增 agent-scoped listInstallTargets，使用已验证 invocation 查找 Agent 及其文件系统；要求本地运行时就绪及精确详情有效，解析后复查来源、项目、Agent、文件系统、运行时和详情身份。返回引用与范围，不返回文件路径。
- 来源切换、sessions 依赖卸载和插件销毁清空目标引用；取消请求不会被转成空目标列表。目录解析失败按范围报告 unavailable。
- 官方 Remote 生成、构建、类型检查与插件测试通过。新增生成物的 agent scope 检查及无会话请求拒绝测试。真实宿主成功路径、自定义 provider 配置和写入口仍待贯通；任务 3.1 尚未完成。

## 默认会话安装目标实测缺口（2026-10-14）

- build19/build20 在隔离 DSH 0.2.0-rc.2 中通过真实 Gateway 调用 listInstallTargets。实测发现 Cordis 的重复服务读取返回不同代理，直接比较文件系统对象造成误判；现使用该固定版本导出的 original 符号比较底层服务身份。修正后插件 check 通过 65 项测试、187 个断言。
- 修正代理身份后，默认 Agent 实际 sandboxMode 为 workspace-write，Host 路径映射成立，但当前解析器拒绝所有沙箱模式，导致两个安装范围均不可用。探针明确失败，见 m2-target-default-sandbox-result.json；不能将此前无沙箱单测视为默认 DSH 安装可用证据。
- 下一步必须适配有效沙箱策略及写入执行机制，使允许的默认项目目标可安装，并明确用户范围的策略处理；不得通过删除沙箱检查或将默认宿主排除出支持范围绕过此缺口。自定义 provider 根仍需核验。此项属于原任务 3.1 的必需实现。

## 会话策略目标预检查（2026-10-14）

- 固定 dsh-sandbox 与 dsh-sandbox-policy peer 为基线版本，使用 sandboxPolicy.resolve({ session }) 读取有效策略，复用官方 writableRoots；规范化目标后检查包含关系。拒绝只读策略和范围外目标，不再仅根据部署默认 sandboxMode 拒绝全部沙箱。异步查询结束时重新核对策略模式及 workspaceRoot。
- 插件 build/typecheck/tests 通过 66 项测试、191 个断言，覆盖会话覆盖默认策略、工作区、临时目录、相似前缀拒绝与只读拒绝。
- build21 隔离 DSH 的真实 Gateway 目标查询通过，伪造详情被拒绝，原有技能发现及对话详情探针亦通过；证据 m2-target-policy-result.json。测试的用户 home 和项目均在临时目录，因此两者符合官方临时目录允许规则；此证据不证明普通用户 home 在 workspace-write 下获准。
- 当前仅完成预检查。写入必须在目标锁内对实际目标及 staging/backup 等路径重新核验有效策略；用户 home 超出范围时应明确提示策略限制，不自动扩大权限。自定义 provider 及真实写入验收仍待完成。

## 目标引用消费与会话策略变化（2026-10-14）

- 新增 Host 内部 resolveInstallationTarget，按当前可信 Agent 重新解析引用、目录和有效策略；前后核对详情、来源、会话、文件系统及运行时，返回固定版本快照。此方法未注册为 Remote，生成端点清单测试继续限制公开面。
- 插件 check 通过 66 项测试、191 个断言。build22 在隔离 DSH 通过真实目标查询后消费，核对项目目录和固定版本；跨会话引用被拒绝。通过官方 setSandboxMode 将原会话改为 read-only 后，旧目标引用消费被拒绝，新查询也不提供可用目标，随后恢复测试会话模式。
- 实机证据 m2-target-consumption-result.json；本轮仍无安装写入。下一步在共享核心的写入边界接入重复核验，并处理 accepted 操作与执行归属，不能将本内部入口视为已完成安装 API。

## 共享核心写入策略接缝（2026-10-14）

- installSkill 增加可等待 authorizeTargetMutation 回调，覆盖暂存、解包、元数据、锁内替换及清理/回滚。策略拒绝时不执行该次文件操作；回滚拒绝保留新目录和旧备份并报告恢复未完成，提交后备份清理拒绝沿用独立告警。
- 新增首次暂存拒绝、替换拒绝及替换后策略撤销/回滚拒绝测试，检查实际文件与 inventory。安装服务专项 34 项测试、159 个断言通过。
- CLI 全量 627 项测试、2244 个断言通过（54 个文件，含真实进程崩溃恢复矩阵）；CLI typecheck、lint、build 通过。插件 check 通过 66 项测试、191 个断言；git diff --check 通过。
- 尚需在原生执行适配器传入此回调、持久接受操作并关联恢复。暂存阶段中断日志及清理拒绝后的完整恢复仍需补齐；本次不标记安装事务或首版交付完成。

## 已接受操作的原生执行适配（2026-10-14）

- 内部 NativeRuntime 增加 executeInstall，要求操作归属、accepted 状态、精确来源/版本和单个规范化目标一致；通过 revision CAS 领取执行权，随后重新 resolve 比对版本 ID 与指纹，再调用共享核心及持久事务观察器。策略回调是必填内部参数。
- 成功后核验实际 inventory 和文件并确认完成；领取后但尚未启动安装核心的失败记录为无恢复需要的失败；核心启动后的不确定失败保留为 recovery-required。重复执行已领取/完成操作不会重新下载，原 API 的幂等响应仍由后续接受/查询适配负责。
- 隔离 HTTP Registry 与真实 ZIP 测试验证落盘、日志完成、跨运行时单次领取、归属/目标/策略拒绝和版本漂移。相关 11 项测试、41 个断言通过；CLI typecheck/lint/build 通过；插件 check 通过 66 项测试、191 个断言。未改共享安装算法，本轮未重复上一轮 CLI 全量回归。
- 该接口仍为包内核心能力，未连接页面/对话写入口；接受请求、Host 生命周期执行管理、DSH 格式检查及恢复缺口继续待完成。

## 原生接受与状态投影（2026-10-14）

- NativeRuntime 增加 acceptInstall/getOperation：复用 OperationStore 的原参数摘要、持久请求查重、归属检查与新请求验证顺序。旧请求在详情引用验证前返回原操作；缺失 retry 不创建记录，同请求号不同参数拒绝。
- 新增 NativeOperation 投影，仅保留操作身份、来源/主机/时间上下文、阶段、文件/发现/恢复状态及告警数量，不输出目标路径、ownerId、请求摘要和事务内部结构。具体告警文案仍需 Host 错误归一。
- 重启、过期引用重试、参数冲突、外部归属查询和新请求验证失败测试通过；原生适配与操作存储共 13 项测试、58 个断言通过，CLI typecheck/lint/build 通过。插件构建、类型检查与 66 项测试、191 个断言通过。
- 此次完成包内接受/查询契约，尚未开启页面或工具写入口；Host 执行管理、写前策略回调连接、格式校验及真实安装继续待完成。

## Host 在途调用与关闭排空（2026-10-14）

- LocalRuntime 保存核心句柄，提供内部接受、查询、执行方法，跟踪在途 Promise。初始化单次执行；关闭立即拒绝新调用，等待已进入的调用成功或失败后释放句柄，延迟初始化不会重新激活已关闭实例。
- sessions 依赖的清理函数等待 runtime.dispose，避免原先丢弃关闭结果。调用失败保持返回给调用方，同时从跟踪集合清除，不阻塞关闭收尾。
- 新增慢安装关闭、新调用拒绝、初始化竞态、重复关闭、接受请求等待及失败排空测试。测试初稿提前对未拒绝 Promise 执行 Bun rejects 断言导致等待，改为先注册 catch、触发拒绝后检查结果；重跑 3 项生命周期测试及完整插件 check 均通过。最终插件 69 项测试、206 个断言，构建和类型检查通过。
- 页面/对话写入口尚未接入。正式执行协调器需在同一受跟踪任务内完成接受与调度，保持已接受操作的快照，避免关闭期间再调用公开接收入口；这一组合行为及实机在途卸载仍待验证。

## 接受与执行的组合调度（2026-10-14）

- LocalRuntime.submitInstall 在同一受跟踪任务内完成持久接受及执行。接受后先返回操作投影，执行持续占用跟踪任务直到结束；关闭期间不会通过已关闭的外层入口再次调度。已持久存在的请求不再解析旧引用或重放执行。
- 领取执行权后，目录规范化变化及首次策略拒绝记录为 failed/not-needed，避免已接受操作停留在等待态；不匹配归属或持久目标的调用仍不领取操作。
- 新增跨接受/执行边界的关闭等待及重复请求不调度测试。插件 build/typecheck 与 71 项测试、213 个断言通过；原生核心 6 项测试、37 个断言及 CLI typecheck/lint 通过。共享 CLI 安装算法本轮未改，未重复全量回归。
- 尚需接入 SkillHubService 的请求归属、固定快照和可在关闭期间完成的策略回调；页面、工具、DSH 格式验证及实机安装验收仍未完成。

## 暂存包的 DSH 官方格式校验（2026-10-14）

- 共享安装核心新增 validateStagedSkill 接缝，在完整 ZIP 提取和指纹核对后、metadata 与最终目录发布前执行；NativeInstallExecution 要求提供 validateStaged 回调。拒绝时沿用事务失败与暂存清理路径，不发布技能。
- 插件新增 validateDshSkill，使用固定 0.2.0-rc.2 的 FileSystemSkillProvider.list/get 读取暂存目录内精确 SKILL.md，检查有效候选和资源根；独立临时 provider 关闭默认根与 watcher，不注册到宿主 skills。新增精确 peer 复用官方解析逻辑，避免另写 YAML/invocation 规则。
- 官方 provider 测试覆盖有效带资源包、取消、缺少 frontmatter/description、无效调用配置及旧调用字段拒绝。原生执行测试验证校验器读取真实提取内容且失败不发布。CLI 安装与原生适配 41 项测试、200 个断言及 typecheck/lint/build 通过；插件 check 通过 76 项测试、219 个断言。
- 尚需由 Host 安装协调器传入官方校验器与策略回调；本轮不是完整 DSH 安装验收，也没有注册技能或执行技能内容。

## Host 内部协调器真实安装贯通（2026-10-14）

- submitInstallation 绑定可信 Agent、精确详情、目标及原请求摘要，传入官方暂存格式校验和每次变更前的策略/目录检查；已接受操作保留来源快照，独立于页面请求取消。getInstallationOperation 依归属查询投影，两个方法均未注册为 Remote。
- 新增实际路径保护：目标根变化、子目录符号链接重定向及越界路径拒绝。插件 check 76 项测试、219 个断言通过；随后新增路径保护测试单独通过（路径分册共 4 项、20 个断言），类型检查及 diff 检查通过。
- build23/build24 实机首次安装被指纹检查拒绝；定位为测试夹具排序与换行编码错误，修正夹具后使用不含临时诊断代码的 build25 重测通过。测试 HOME/USERPROFILE、DSH_HOME、项目目录及 inventory 均隔离在临时目录；先前失败记录保留为待恢复，不计为成功恢复。
- 真实 DSH 0.2.0-rc.2：内部协调器返回 accepted，最终日志 completed/installed；资源文件内容正确；ctx.skills.get 发现并读取新技能及正确资源目录。重复请求返回原操作，不同会话查询不到操作。证据 m2-native-install-result.json，探针环境要求已更新。
- 此项证明内部成功安装与 DSH 发现，不证明模型执行该技能、页面/对话安装交互、实际公开 Registry 或故障恢复已完成。公开写入口继续关闭，后续补齐剩余事务门槛及 UI/工具适配。

## 原生安装的下载前幂等核验（2026-10-14）

- 原生执行器在网络 resolve/download 前取得共享目标锁，核验待恢复阻断；存在目录时继续持有 inventory 锁，检查唯一目标归属、来源、版本、指纹、metadata、安装时间、Agent、直接安装来源及整个文件树。复用文件树安全检查拒绝符号链接和特殊文件，调用官方格式校验适配后才记录 already-installed。
- 核验无目标时释放锁进入正常安装，提交仍由共享核心锁内重验；已有不完整或冲突安装明确失败，不下载、不覆盖。不同请求号针对同一完整安装可记录独立的 already-installed 结果，原请求号重试仍返回原操作。
- 原生执行专项 9 项测试、39 个断言通过，覆盖重复完整安装及内容/metadata/符号链接冲突不下载。CLI typecheck/lint/build 通过；插件 check 77 项测试、222 个断言通过。此轮未重复完整实机安装或 CLI 全量矩阵，上一轮成功实机证据不扩展为本轮幂等实机验收。

## 暂存创建前持久意图（2026-10-14）

- 安装核心先生成 UUID 暂存路径并等待 beforeStaging 日志，再创建根目录和暂存目录；非递归目录创建拒绝碰撞。OperationStore 校验规范化根、已接受目标、UUID 及重复意图，完整 preparation 必须对应已记载的暂存路径。
- 新增日志失败不创建目录、碰撞目录内容保留、路径越界/重复意图拒绝，以及创建前/提取后 SIGKILL 的日志重开测试。后者确认进程被杀后仍能定位暂存路径，尚未把不完整暂存内容自动删除。
- 首次全量回归暴露恢复器只接受旧 6 位暂存后缀的问题；已兼容旧日志后缀及有意图绑定的新 UUID 路径。修正后 CLI 全量 642 项测试、2325 个断言通过（57 文件，含实际中断与恢复矩阵）；typecheck/lint/build 通过。随后日志专项 12 项测试、61 个断言通过；插件 check 77 项测试、222 个断言通过。
- 此次消除了早期暂存路径未记载的缺口；仅有暂存意图而没有完整 preparation 时的核验、清理与状态收敛仍需继续实现，不把可定位等同于已恢复。

## 早期暂存中断的无残留恢复（2026-10-14）

- recoverInstallation 在目标锁内识别只有 staging 意图、尚无 preparation/移动/提交/恢复动作的操作。已记录暂存目录全部明确不存在时持久化 failed/restored；重复恢复返回原结果，操作退出待恢复扫描，不修改 inventory 或创建技能根。
- 根目录尚不存在时核验已有祖先；目录被文件占用、符号链接重定向及非 ENOENT 读取错误不能当作不存在。暂存内容仍存在时返回 unknown 并保留内容；外部清理完成后允许重新核验。旧日志缺少暂存意图时不走此分支。
- 实际子进程 SIGKILL 测试覆盖创建前、解包后两处中断，以及目录冲突、重定向、内容保留、重复恢复和清理后收敛。恢复集成测试 58 项、458 个断言通过；随后新增重定向断言，专项 2 项、28 个断言通过。CLI typecheck/lint/build 及插件 check（77 项、222 个断言）通过。
- 本轮不开放恢复或安装 Remote。启动恢复编排、活跃执行排除、恢复写入的 Host 策略校验及页面/对话安装入口仍待完成；不声明完整安装生命周期已验收。

## 恢复写入的策略接缝（2026-10-14）

- NativeRuntime.recover 强制要求 Host 策略回调，并传入共享恢复器。每次回滚记录意图后校验源/目标路径；备份清理前校验待删除路径。异步校验后重新核对内容、路径及日志修订号，再执行变更。
- 策略拒绝时回滚保留意图并进入 recovery-required/incomplete；已提交操作维持安装完成，记录备份清理失败并保留待核验项。新增拒绝后重试、异步策略检查期间内容变化、JavaScript 调用遗漏回调拒绝测试。
- 恢复集成测试 60 项、495 个断言通过。随后增加策略拒绝前后目录列表不变断言，真实 SIGKILL 安装/恢复矩阵专项通过（1 项、172 个断言，含多个故障边界）。CLI typecheck/lint/build、插件 check（77 项、222 个断言）、diff 与 OpenSpec strict 校验通过。
- 这是核心与原生边界的策略接缝验证。Host 恢复编排尚未接入，执行与恢复的互斥、历史操作可信上下文解析和实际 DSH 策略切换验收仍待完成，不将回调单测视为宿主端到端验收。

## 原生执行与恢复互斥（2026-10-14）

- NativeRuntime 的 executeInstall/recover 复用共享文件锁，对同一操作的完整执行或恢复互斥。先校验 ID/归属，再取独立操作锁；目标锁之前的解包与校验阶段也受保护。输入在等待前复制，完成或失败均释放锁，不通过内存标志推断其他进程是否运行。
- 暂停真实包的暂存校验时，另一个运行时及独立子进程恢复均返回 TARGET_BUSY，不进入策略回调、不改变操作记录；执行完成后，两者均可核验已提交结果。另用真实 SIGKILL 杀死持锁原生执行器，验证陈旧锁期限前拒绝恢复、到期后允许核验，未完成日志仍保留，不将过期锁解释为成功。
- 原生执行/运行时测试 13 项、76 个断言通过；CLI typecheck/lint/build 通过。初次 lint 指出 finally 内抛出异常，已改为显式释放并合并双重失败，修正后重跑上述检查及插件 check（77 项、222 个断言）通过；diff 与 OpenSpec strict 校验通过。
- 本轮未更改共享 CLI 安装算法，未重复全量 CLI 回归。Host 恢复编排、历史上下文校验、实际 DSH 在途重载以及双入口安装仍待完成。

## Host 单目标安装恢复协调器（2026-10-14）

- LocalRuntime.recoverOperation 按 operationId/ownerId 筛选待恢复记录，向准备回调传递隔离快照，再调用受操作锁保护的原生恢复。整个调用参与关闭排空；完成或失败后重新扫描待恢复数量，重扫失败时服务进入 failed 并拒绝新调用。
- SkillHubService.recoverInstallationOperation 从可信 Agent 解析历史目标对应的用户/项目默认根，拒绝其他会话、不可匹配的路径及不支持的操作类型。恢复变更前重新检查会话/文件系统身份、默认根和当前策略。不读取当前 Registry，不重新下载历史技能；尚未注册为 Remote 或自动启动恢复任务。
- 编译产物在隔离 Node 子进程中，通过模拟 Agent/文件系统/策略验证只读拒绝、未知会话拒绝、历史路径不匹配、无残留恢复与 pending 数量更新。运行时测试覆盖归属筛选、回调快照隔离、关闭排空、恢复失败和重扫失败。
- 插件 check 的构建、类型检查及 82 项测试、240 个 Bun 断言通过；编译 Host 测试另含 Node assert 断言。共享 CLI 核心本轮未改，未重复 CLI 全量回归。DSH 实机会话恢复、在途重载、安装双入口与已安装列表仍待完成。

## DSH 实际会话的提交前恢复（2026-10-14）

- 当前候选 tarball 安装到隔离 DSH 0.2.0-rc.2 Profile。Host、Agent 和官方 sandboxPolicy 均为真实宿主服务，Registry 为本地 HTTP 夹具，HOME/USERPROFILE/DSH_HOME/DSH_AGENTS_HOME 的安装数据保持隔离。
- 发现探针在独立项目中准备完整技能包，在第一次目标移动之前注入失败并保留暂存目录，断言已到达预定边界。其他会话不能恢复该记录；原会话改为 read-only 时拒绝恢复且目录仍存在，恢复 workspace-write 后完成暂存删除，最终目标不存在，持久状态为 restored。
- 同次运行原生成功安装、资源读取、DSH 发现、重复请求、可信目录和对话搜索检查继续通过。报告见 [m2-native-recovery-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-native-recovery-result.json)，其中 passed/nativeInstallationCompleted/nativeRecoveryCompleted 均为 true。
- 运行时确认 Host 进程存活，匿名 HTTP 访问返回 401；本轮没有浏览器登录或页面验收。结束后已停止本次 Host 和 Registry 夹具。未验证 Host 崩溃重启、自动启动恢复、在途重载、对话/页面写入口及其他平台。
- 首次夹具缺少 downloadFromUrl 导致未到达中断边界，修正并增加边界断言后重新运行通过；未修改生产恢复实现来迁就夹具。探针说明同步更新，不将模拟 Registry 与真实服务联调混为一项。

## 会话作用域安装 Remote（2026-10-14）

- 新增 startInstall/getOperation 官方 RemoteScope(agent) 入口，共用持久接受/执行/查询。listInstallTargets 返回持久 hostInstanceId；不存在或不可见的操作返回 null，参数冲突和 Host 身份变化返回明确错误，原始文件异常不对外输出。
- 安装请求校验 UUID、引用长度、枚举及额外字段。核对固定发行版官方 Gateway 源码后，通过 ctx.invocation.request.args.input 检查原始请求，避免生成对象 schema 忽略额外字段。公开接口类型从 types 子路径导出，符合官方生成器要求。
- 插件构建、类型检查及 83 项测试、257 个断言通过。新候选包安装到隔离 DSH 后，探针经真实 typertGateway 完成提交、查询、原操作重试、参数冲突、多余路径字段拒绝、错误 Host 身份和跨会话隔离；成功安装/资源读取与提交前恢复检查同时通过。报告见 [m2-remote-install-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-remote-install-result.json)。
- 测试 Host 与 Registry 夹具已停止。此证据证明 Remote 到原生事务和 DSH 发现的链路，不证明页面/对话安装 UI、安装工具、共享轮询、已安装列表或发布兼容矩阵已完成；本轮未发布包。

## 对话详情抽屉安装与共享请求（2026-10-14）

- 新增 InstallControl，展示用户/项目范围、可安装状态、显式安装、接受/执行/未知/失败/恢复提示。文件完成与发现确认分别展示，不将 files=installed 解释成模型已使用技能。
- 插件级 InstallationRequests 按会话/Host/来源/技能版本/范围合并请求，在详情卸载、重新打开和响应丢失时保留原参数与 requestKey。并发点击共用 Promise；已接受记录不重提，响应不确定仅以 retry 查询原请求，null 不生成新安装键。
- 操作查询复用插件 QueryClient，按会话/Host/operationId 合并；可见且操作未终止时轮询，隐藏暂停，终止/错误/未知记录停止自动查询。插件卸载释放监听与缓存，迟到响应不恢复已销毁状态。
- 插件 build/typecheck 与 86 项测试、270 个断言通过。真实 DSH 浏览器点击安装、结果展示、关闭重开不重复提交（installSubmissions=1）、详情 Tab、Escape/关闭、焦点恢复及卡片重放通过，无 pageerror。报告见 [m2-conversation-install-ui-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-install-ui-result.json)，截图见 [m2-conversation-install-ui.png](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-install-ui.png)。
- 当前请求缓存只覆盖一次插件加载；浏览器完整刷新后的请求恢复、广场入口、显式安装工具、用户范围 UI、窄屏及失败场景浏览器验收仍待完成。不可据此勾选完整双入口交付。

## 浏览器刷新后的安装请求恢复（2026-10-14）

- InstallationRequests 在发送前保存带版本的原请求到 sessionStorage，响应后补 operationId。刷新恢复只加载请求/引用，不恢复成功快照；有 ID 查询 Host，无 ID 用原 key/参数 retry。存储损坏/不可用/写满时禁止首次发送，响应后补写失败仍保留原请求。
- 新增重新实例化恢复、响应丢失保持参数、存储不可用/满/损坏阻断发送和响应补写失败测试。插件 build/typecheck 与 90 项测试、283 个断言通过。
- 在真实 DSH 的隔离浏览器中，安装、关闭重开、完整刷新后重开详情均通过；刷新后观察到新的 getOperation 响应，仍只有 1 次 startInstall，pageerror 为空。报告见 [m2-install-reload-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-reload-result.json)，截图见 [m2-install-reloaded.png](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-reloaded.png)。
- 此次验证限定于同一 Host 运行实例和浏览器标签页。Host 重启后的 sourceRevision 变化、标签页关闭或清理存储后的历史入口仍需结合持久操作列表实现；广场安装、对话安装工具及其结果卡片尚未完成。

## 广场安装与对话共享结果（2026-10-14）

- 广场详情复用 InstallControl，通过官方 session-maybe 子槽位获取当前会话；无会话时展示进入会话提示。安装调用、请求缓存和查询与对话详情共用，按会话隔离。
- 当前源码 build/typecheck 与 90 项测试、283 个断言通过。隔离 DSH 0.2.0-rc.2 浏览器验证广场提交、返回会话并打开详情、关闭重开、刷新后查询原操作，只有一次 startInstall，pageerror 为空。返回会话时宿主折叠工具过程，探针按宿主行为展开后通过。
- 本次截图显示“此版本已安装”，证明已有目标的幂等提交及跨入口结果复用，不作为广场首次下载落盘的证据。Registry 为本地 HTTP 夹具；未验证真实 Registry、用户范围 UI、切换会话及窄屏。
- 报告见 [m2-plaza-install-ui-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-plaza-install-ui-result.json)，截图见 [m2-plaza-install-ui.png](../../../docs/research/dsh-native-plugin-2026-10-12/m2-plaza-install-ui.png)。显式安装工具、安装结果 ToolView 和已安装列表仍待实现，不勾选完整双入口验收。

## 对话安装输入与稳定调用身份（2026-10-14）

- 增加互斥输入 schema：selectionRef 或完整 namespace/slug，省略 scope 时默认 user。拒绝混合输入、不完整坐标、空引用、路径段和模型提供的内部令牌、请求键、Host 身份、下载地址等字段；历史引用不能通过传入新坐标静默降级。
- 基于持久 Host UUID、会话和 callId 生成 UUID v5 请求键；结构化编码区分字段边界，缺失可信身份时拒绝生成。请求参数不改变键，后续由持久日志比较原参数摘要，避免同一调用变参后生成新操作。
- 专项测试 2 项、41 个断言及插件 typecheck 通过。此模块尚未接入工具注册和 Host 提交协调器；不表示对话安装工具已经可用。

## 对话安装 Host 协调器（2026-10-14）

- 新增内部 installConversation，将原始引用/坐标、范围和项目上下文提交给共享持久接受流程；已有记录的查重先于来源读取和引用解析。相同调用变参保持原请求键，交由日志摘要校验冲突。
- 新请求的历史引用解析为已查看的固定详情；新坐标先解析版本，再读取精确详情。目标由同一 Host 目标解析器产生，指定项目不可用时拒绝，不回退用户范围。页面和对话共用 prepareInstallation，保留路径策略、格式校验和接受后独立生命周期。
- 新增 Host 适配测试，覆盖过期引用重试进入接受层、同调用变参保持键、上下文/额外字段拒绝、固定详情复用、项目不可用及过期引用不回退。测试以替代运行时隔离协调器行为，不冒充真实持久日志或宿主工具执行验收。
- 插件 build/typecheck 与全套 94 项测试、336 个断言通过。首次检查发现测试参数缺少类型注解，修正后重跑通过。共享 CLI 核心未改；尚未注册显式安装工具、实现对应 ToolView 或进行本轮实机安装回归。

## 显式安装工具与结果卡片接入（2026-10-14）

- 注册 astron_skillhub_install，逐参数 schema 配合互斥输入校验；从 exec 获取会话/callId/cwd。工具名称冲突不覆盖已有注册，卸载释放注册。显式安装语义写入工具说明，Host 继续强制校验上下文和文件操作。
- 工具返回 accepted、operationId 和原操作来源/主机/时间快照；不返回当前终态，不将重试查询到的 completed 写成一次新的安装成功。presentationMeta/render 为保存结果的纯投影，异常不泄露 Host 路径。
- Client 注册原生安装 ToolView，校验历史格式后查询原操作，展示准备/核验/安装/完成/失败/恢复及不可核验状态。查询 hook 与详情安装共享 QueryClient 和查询键，隐藏暂停、终态停止；仅提供查询按钮，不从历史卡片提交写操作。
- 新增注册冲突、输入拒绝、可信上下文、接受结果原快照、展示不重执行、异常脱敏和历史格式边界测试。修正测试语法及字面量类型问题后，插件 build/typecheck 与 98 项测试、368 个断言通过。
- 本轮未运行真实 DSH 工具执行或浏览器验证，不以组件编译声明视觉/交互验收。结构化错误分类、详情/已安装导航、复制操作 ID，以及真实宿主安装和历史重放仍需补齐；对应任务保持未勾选。

## DSH 原生安装工具实机执行（2026-10-14）

- 当前 tarball 安装到隔离 DSH 0.2.0-rc.2 Profile，经真实 tools.execute 调用 astron_skillhub_install。安装使用新建项目和匿名 HTTP 包夹具，断言最终 files=installed，并核对资源文件及 DSH skills.get 发现结果。
- 同一工具 callId、相同参数再次执行返回相同 operationId 和接受快照；同 callId 改为 user 范围拒绝，额外 path 参数拒绝。此前 Remote 安装、归属隔离、固定详情和提交前恢复探针同时通过。
- 报告见 [m2-native-install-tool-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-native-install-tool-result.json)，passed/nativeInstallToolCompleted 均为 true。此验证未调用模型，未使用真实 Registry，也未验收安装 ToolView 的浏览器展示或 Host 重启后的同调用恢复。

## 安装结果 ToolView 浏览器验收（2026-10-14）

- 在隔离 DSH 0.2.0-rc.2 中保存真实安装工具接受结果到独立会话。浏览器展开工作区与宿主工具过程，在原生工具区域显示 SkillHub 安装卡片，查询 Host 后展示文件完成和发现未确认。
- 完整刷新后重新查询 Host，操作 ID 保持一致；浏览器 startInstall 请求为 0，pageerror 为空。首次探针因工作区折叠找不到会话，补充对应工作区定位后通过；未修改生产 UI 来迁就探针。
- 报告见 [m2-install-tool-ui-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-tool-ui-result.json)，截图见 [m2-install-tool-ui.png](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-tool-ui.png) 和 [刷新后截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-tool-reloaded.png)。人工检查确认卡片嵌在 DSH 对话工具结果区。
- 本轮验证仅为成功终态及同 Host 的历史重放，Registry 为本地夹具，未调用模型。运行中/失败/窄屏、宿主重启、详情/已安装导航和复制操作 ID 仍未完成；不勾选完整对话验收。

## 安装请求失败分类（2026-10-14）

- 工具输出增加 not-confirmed 分支，使用官方 schema 支持的 oneOf 区分接受与未确认。已知 Host/Registry 错误映射为受限代码；未知异常不携带原始消息、路径或额外数据。目标不可用拥有独立 Remote 错误码。
- 结果卡片显示上下文、请求冲突、来源未配置/拒绝/不可用/限流、技能版本不可用、目标不可用等原因；失败历史不产生操作查询或安装动作。工具渲染按业务 status 输出接受或未确认说明。
- 新增错误映射和失败历史校验测试，调整工具异常测试及实机探针对变参冲突的结构化断言。修正 Remote 声明及测试字面量类型后，build/typecheck、100 项测试和 388 个断言通过。
- 本轮未重跑 DSH 实机工具/浏览器。新的 oneOf 输出与失败卡片仍需在实际宿主验收；此前成功卡片截图不能替代本轮失败交互证据。

## 失败结果协议与过期选择实机验收（2026-10-14）

- 新候选包在隔离 DSH 0.2.0-rc.2 安装，真实工具执行器接受 oneOf 成功/未确认协议。首次安装、原调用重试和恢复继续通过；变参返回 REQUEST_CONFLICT，过期 selectionRef 返回 CONTEXT_STALE，均为 not-confirmed 而非虚构接受记录。
- 浏览器先复验成功卡片与刷新后的同操作查询，再打开真实过期引用结果：显示上下文失效原因，不显示操作 ID/查询按钮；startInstall 为 0，pageerror 为空。报告见 [Host 结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-error-host-result.json)、[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-error-ui-result.json)，截图见 [失败卡片](../../../docs/research/dsh-native-plugin-2026-10-12/m2-install-error-ui.png)。
- 本轮未调用模型，Registry 仍为本地夹具。来源拒绝/限流/断网等其他失败分支尚未进行浏览器验证。探针语法、diff 与 OpenSpec strict 校验通过，未重复未修改的单元测试套件。

## 已安装列表的本地只读核心（2026-10-14）

- 增加 readInstalledProjection 内部入口，读取共享 inventory，限定当前 Registry、DSH Agent 和 Host 提供的绝对根。用户/项目安装分别展示；不遍历其他项目或 Agent，不发网络请求，不修复文件或清单。
- 对清单目标核对路径、目录、SKILL.md 与元数据身份/版本/安装时间。目录明确 ENOENT 标 missing；符号链接或路径不匹配、无效元数据和读取失败分别保留异常条目，读取问题标 partial。未做全量 hash，present 仅表示本轮核对的文件存在。
- 新增两个范围、版本保持、本地内容修改不冒充 hash 校验、其他来源/项目过滤、目录缺失、符号链接、损坏元数据、inventory 失败和越界目标测试：2 项、12 个断言通过。CLI typecheck/lint/build 通过；插件 build/typecheck 与 100 项测试、388 个断言通过。
- 本轮仅加入内部读取核心，尚未接入 NativeRuntime 契约、Host 只读目标解析、listInstalled Remote 或已安装 Tab。元数据发现清单遗漏、离线详情和页面刷新仍待后续实现；不勾选已安装产品验收。

## 已安装运行时与会话查询接口（2026-10-14）

- 内部 NativeRuntime/LocalRuntime 接入 listInstalled，共享清单读取参与运行时关闭排空。列表路径来自 Host；独立 resolveInstalledPath 复用默认根与文件系统映射核验，但不要求写入权限，原安装解析仍强制写策略。
- 新增 RemoteScope(agent) 的 listInstalled(sourceRevision)。逐范围解析根，返回 complete/partial 和 unavailable；读取前后核对来源、会话、文件系统及根。条目签发有界的 Host itemKey，公共响应不包含 targetPath，后续详情使用 Host 保存的原条目身份。
- 新增只读根解析断言，扩展生成协议接口清单和会话作用域断言。CLI 列表/运行时专项 4 项、30 个断言及 typecheck/lint/build 通过；插件 build/typecheck、100 项测试、390 个断言通过。首次生成协议清单测试因新增方法未登记失败，更新预期后重跑通过。
- 尚未在真实 DSH 经 Gateway 调用 listInstalled，也未接入已安装 Tab 和本地详情；不以生成器和根解析测试代替完整接口/浏览器验收。

## 已安装 Tab 与实际宿主读取（2026-10-14）

- 广场启用浏览/已安装切换，保留顶层选择；已安装视图使用会话作用域 Remote，提供全部/用户/项目筛选、手动刷新、加载/空数据/错误/部分读取反馈，条目显示固定版本、范围和本地状态。没有会话时提示先打开会话。未注册详情或管理占位按钮。
- 插件 build/typecheck、100 项测试和 390 个断言通过。新 tarball 在隔离 DSH 0.2.0-rc.2 安装后，Gateway 验证 read-only 会话可读、本地路径不外泄和其他项目记录隔离；页面实际显示正确版本/范围/文件状态并完成手动刷新，pageerror 为空。
- 报告见 [Host 验证](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-host-result.json)、[浏览器验证](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-ui-result.json)，截图见 [已安装页](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-ui.png)。安装工具成功、失败和历史重放同时复验通过。
- 尚未补齐本地详情、从结果卡片导航、安装后主动失效、升级/卸载入口及完整产品样式。Registry 离线、异常记录、用户范围和窄屏的浏览器验收仍待完成；不勾选完整已安装功能任务。

## 已安装固定版本详情与离线验收（2026-10-14）

- 新增会话作用域 getInstalledDetail，依据 Host itemKey 核对来源、会话、默认根、实际清单及版本，再读取精确版本远端详情。远端不可用返回 local-only，保留本地身份/版本/范围/状态，不解析 latest。公共响应不含文件路径。
- 已安装页提供详情子页面、返回、焦点恢复和重新读取，在线复用 DetailContent 及可选 Tab；本地信息持续可见，不提供重复安装入口。
- 插件 build/typecheck、100 项测试和 391 个断言通过。隔离 DSH Gateway 验证固定版本和跨会话拒绝；浏览器验证在线正文、返回焦点和列表刷新。随后停止本轮 Registry 夹具，保留 Host，浏览器验证离线列表与 local-only 详情，仍保留 1.2.0 版本，pageerror 为空。
- 证据见 [Host](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-detail-host-result.json)、[在线浏览器](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-detail-ui-result.json)、[离线浏览器](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-offline-ui-result.json)、[在线详情](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-detail-ui.png) 和 [离线详情](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-offline-ui.png)。
- 本轮未验证实际 SaaS/开源 Registry、用户范围详情、记录变更竞态及窄屏。卡片导航、升级/卸载、安装后主动失效和完整视觉规范仍需完成。

## 安装卡片导航、复制与本地投影刷新（2026-10-14）

- 安装结果卡片增加复制操作 ID，成功/失败均提供状态提示；已核验 completed 操作可进入已安装页。导航前检查当前来源修订/地址，页面再检查原会话与来源，变化时要求显式打开当前上下文列表。
- 共享操作查询在取得终态后失效同会话的已安装列表及详情缓存；不失效操作查询自身，不新增写操作。顶层目录按钮显示当前选中状态。
- 插件 build/typecheck、100 项测试和 391 个断言通过。真实 DSH 浏览器授权剪贴板并核对实际复制内容；从结果卡片直接进入列表和固定版本详情，返回焦点、刷新、失败卡片均通过，无 startInstall 请求和 pageerror。报告见 [卡片操作验收](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-navigation-result.json)，截图见 [复制结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-navigation-card.png)。
- 尚未验证剪贴板拒绝、来源/会话切换竞态、双入口同时可见时的缓存失效。升级、卸载及完整 UI 样式仍待完成。

## 主动检查更新与取消边界（2026-10-14）

- 已安装详情增加主动检查按钮；会话作用域 checkUpdate 只读取发布元数据，复用 CLI 版本排序与指纹判断，不下载归档、不修改技能。异常记录阻断；检查前后重新读取安装记录，指纹变化拒绝返回判断结果。
- 修复本地读取及异步版本判断结束后的取消检查，防止请求取消或插件释放后返回成功结果。Host 编排测试覆盖正常判断、异常记录不访问 Registry、检查中记录变化、来源离线、请求取消及生命周期取消。此测试使用编排替身，不代替 Gateway 或文件系统集成验收。
- CLI 专项测试通过：3 项测试、22 个断言，覆盖更新比较和已安装投影；typecheck、lint、build 通过。插件 build、typecheck 及 101 项测试、410 个断言通过。新增测试首次因跨目录导入不符合 NodeNext 失败，移除该导入并明确隔离编排测试后完整重跑通过。
- 真实 DSH 更新检查的 Gateway、浏览器及不同远端版本场景尚未验收；未勾选 tasks.md 4.1。升级计划、执行和卸载尚未完成。

## 主动检查更新实机验证（2026-10-14）

- 新打包产物安装到隔离 DSH 0.2.0-rc.2 Profile。Gateway 在 read-only 会话核对相同版本/指纹返回 current、same-content 和精确版本 1.2.0；跨会话 itemKey 被拒绝。
- 浏览器通过安装结果卡片进入已安装详情，确认打开详情不请求 checkUpdate，点击一次仅请求一次，显示记录指纹一致；安装提交数为 0，pageerror 为空。复制操作 ID、历史重放、失败卡片和返回焦点同时回归通过。
- 证据：[Host 结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-check-host-result.json)、[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-check-ui-result.json)、[实际界面](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-check-ui.png)。探针脚本通过 Node 语法检查。本轮仅启动隔离 Host 和 loopback Registry，结束后停止对应进程。
- 本轮只验证当前版本一致分支，远端新版本/旧版本/内容变化的真实 Gateway 与浏览器验证尚未完成；CLI 比较单元测试不代替这些实机场景。页面基础样式仍需完善，未声明视觉验收或 tasks.md 4.1 完成。

## 更新检查分支矩阵（2026-10-14）

- 隔离 Registry 增加可切换发布元数据，实际 DSH 浏览器依次检查新版 1.3.0、旧版 1.1.0、同版本不同指纹、未知顺序 next-release。逐项核对 Gateway state/reason 和对应中文提示；初始一致分支及四次主动检查共五个请求，没有自动检查或安装提交，pageerror 为空。
- 首轮探针误读响应封装层失败，按已有 DSH 探针的 result 字段修正后完整重跑通过。两份脚本 Node 语法检查及 git diff --check 通过。本轮未修改产品代码，不重复以构建测试代替浏览器证据。
- [结果报告](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-matrix-result.json)；[新版本](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-newer.png)、[旧版本](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-older.png)、[内容变化](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-changed.png)、[未知顺序](../../../docs/research/dsh-native-plugin-2026-10-12/m3-update-unknown.png)。结束后恢复夹具状态并停止本轮 Host/Registry。
- 这些场景仅模拟元数据，不证明新版本归档可安装或升级成功。真实 Registry 联调、升级计划/执行、卸载及完整视觉验收仍未完成。

## 升级计划的完整本地基线核验（2026-10-14）

- 新增内部 inspectUpgradeBaseline，经 local-skills 导出；只读核对来源和版本、完整目标集合、Host 批准的当前用户/项目根、direct 所有权及其他记录的目录冲突，再读取 metadata 与实际文件指纹。返回每个目标的范围、位置、metadata 和文件快照，供后续计划保存；不下载、不更新清单、不替换目录。
- 目标位于其他项目/Agent、共享来源、同目录重复/别名、缺失或不匹配的 metadata 基线、本地内容变化均阻断。文件读取后重读相关清单，记录发生变化时拒绝生成可用基线。此读取不是执行锁，不替代执行阶段的目标锁及 CAS。
- 新增真实临时目录测试，覆盖两个允许范围及上述阻断分支，1 项测试、12 个断言通过；CLI typecheck/lint/build 通过。插件重新打包内部核心后 build/typecheck 与 101 项测试、410 个断言通过。git diff --check 通过。
- 目前只完成共享核心核验，尚未接入 NativeRuntime、Host prepare/execute Remote、5 分钟计划存储和确认 UI；升级/卸载任务保持未完成，未对用户技能执行升级。

## 升级准备运行时与计划引用（2026-10-14）

- 升级基线类型纳入内部 portable contract，NativeRuntime 与 LocalRuntime 接入 inspectUpgrade，复制调用参数并沿用运行时生命周期跟踪。
- 新增 Host 私有 UpgradePlans，保存完整基线和精确候选的副本，5 分钟固定有效期，最多 400 项；绑定 session/sourceRevision/cwd，并限制同一计划只能 claim 一个 requestKey。输入和读取结果均不能修改内部快照。已接受请求必须先从持久操作存储查重，不能依赖过期计划重放。
- 新增会话作用域 prepareUpgrade：核验本地记录、待恢复状态、允许写入的用户/项目根、完整基线及版本顺序，返回 ready/unchanged/blocked；仅 ready 签发计划和完整目标位置。来源重载、运行时卸载、插件释放清除计划，执行接口尚未开放。
- 插件 build/typecheck 与 103 项测试、432 个断言通过。计划测试覆盖副本隔离、上下文失效、精确到期边界、请求键绑定及 clear；编译后 Host 编排测试覆盖计划生成、共享目标和待恢复状态阻断。新增接口已纳入生成器方法清单与会话作用域断言。CLI 基线测试 12 个断言及 typecheck/lint/build 通过。
- 初次构建发现未登记错误码，改用已有 invalid-installation-request；新增 Remote 方法清单及测试的 nullable planId 校验补齐后完整重跑通过。真实 DSH Gateway、确认 UI、锁内快照重校验、持久接受/执行和升级恢复仍待实现/验证。

## 原生升级事务核心与运行时（2026-10-14）

- NativeRuntime 新增 acceptUpgrade/executeUpgrade，接受参数摘要区分升级操作，持久重试先返回原操作，不重新解析计划。执行复制完整基线，经同一操作租约串行化；只允许 owned/accepted 升级，目标集合必须与接受记录一致。
- executeNativeUpgrade 在下载前核对完整基线和 Host 策略，按精确版本重新解析并核对 versionId/fingerprint。共享安装核心负责所有目标锁、inventory CAS 和备份替换；beforeTargetMutation 在锁内再次核对快照，备份移动后额外检查完整 metadata 和目录类型，文件内容由 expectedTargetFiles/allowTargetDrift:false 保护。复用现有事务日志和恢复，提交后的状态为 upgraded。
- LocalRuntime 新增 submitUpgrade，接受结果提前返回而完整工作流仍被跟踪；关闭等待执行结束，持久重试不重新 prepare 或 execute。尚未注册 Host 执行 Remote，不提供产品升级按钮。
- 新增真实 loopback ZIP 与双目录升级测试：完整目标成功升级、确认后本地修改在下载前阻断、暂存时修改在首次替换前阻断、第二目标替换失败恢复两个旧目录并完成日志核验。相关安装/升级/持久重试测试 17 项、100 个断言通过。
- make test-cli 完整通过：655 项测试、2458 个断言；make typecheck-cli lint-cli build-cli 通过。插件 build/typecheck 与原有 103 项测试、432 个断言通过；随后新增运行时升级生命周期测试，typecheck 与 runtime-execution 文件的 10 项测试、45 个断言通过。git diff --check 通过。
- 证据限于核心和运行时自动化：尚未完成 Host 执行接口、确认界面、真实 DSH 升级、远端 Registry、升级专属进程崩溃矩阵与卸载，不声明升级产品验收完成。

## Host 升级计划执行接口（2026-10-14）

- 新增会话作用域 executePlan(input)，只接受 planId/requestKey，并核对原始 wire 对象拒绝额外路径、版本等字段。先经持久接受查重，只有新请求读取并 claim 未到期、匹配当前来源/会话/项目的计划。
- 接受前重新解析全部目标根、检查来源与会话/文件系统身份；接受快照绑定候选精确版本和完整目标路径。执行阶段每次变更重读当前策略、根及 DSH home，拒绝确认范围外路径和已切换/失效会话，不使用请求 AbortSignal 取消已接受事务。
- 编译后 Host 编排测试覆盖额外字段拒绝、计划签发后的执行参数、范围外写入拒绝、会话改为 read-only 后拒绝、同计划不同请求键冲突，以及持久重试不读取已清空计划。生成 Remote 方法清单和作用域断言已更新。
- 初次生成器检查发现闭包数组隐式 any，补充已存在的 ResolvedInstallationTarget 类型后完整重跑：插件 build/typecheck、105 项测试、449 个断言通过。此轮没有改变共享事务核心，未重复完整 CLI 测试。
- 页面确认/请求持久化、真实 Gateway 与浏览器升级验收、升级发现反馈仍待完成，未将接口测试表述为产品升级验收。

## 升级确认组件与请求持久化（2026-10-14）

- 已安装详情在主动检查发现更新后展示升级入口；生成计划后展示来源、旧新版、全部目标位置及有效期，提供确认/取消，过期禁用确认并在点击时再次判断。Host 计划响应增加 hostInstanceId，供操作查询绑定原主机。
- UpgradeRequests 在发送前保存严格校验的 planId/requestKey/hostInstanceId，重复点击合并在途任务；响应丢失和页面重载保留原参数，不改用新计划。存储读取/写入失败阻止新提交；返回主机或操作类型不匹配时保持未确认。取得操作 ID 后复用可见性控制和终态刷新查询，分别展示文件与发现状态。
- 新增 3 项请求存储测试、10 个断言，覆盖丢响应后重载/重试、重复点击、持久成功后不重复提交、存储失败不发送、错误主机和释放后晚返回。最终插件 build/typecheck、108 项测试、459 个断言通过，git diff --check 通过。
- 本轮尚未运行真实 DSH 的升级确认页面：计划/取消、成功执行、失败重试、刷新恢复和无障碍浏览器验收均保持待验收。该实现不作为完整升级任务完成证据；未知请求结果的后续恢复体验还需联调。

## 真实 DSH 升级确认与文件结果（2026-10-14）

- 在独立临时 Profile 安装新 tarball，loopback Registry 提供 1.2.0/1.3.0 两套可校验归档，新版 references/note.md 内容不同。普通探针跨项目共用技能坐标，首次升级正确返回 target-outside-context；改用每轮独立测试 namespace 验证允许范围内升级，没有放松产品目标限制。
- 浏览器验证计划的精确版本和单个目标，取消时 executePlan 为 0，再生成并确认后恰好一次 executePlan。完成后列表版本为 1.3.0；直接读取 metadata 和资源文件确认实际新版落盘。没有 startInstall 提交或 pageerror。
- 首次截图发现成功后顶部仍显示旧版和旧详情错误，已修复为明确显示升级后版本、提供查看新版详情，并修复列表刷新后返回焦点对应新 itemKey。重新构建打包、安装和完整浏览器流程通过；插件 build/typecheck 与 108 项测试、459 个断言通过。
- [浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-ui-result.json)、[文件结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-files-result.json)、[计划截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-plan.png)、[完成截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-completed.png)。临时 Host/Registry 已停止，原有故障夹具及操作日志保留。
- 尚未验证升级中刷新/响应丢失/Host 重启、浏览器多目标升级和失败恢复；DSH 发现状态仍明确显示未确认，没有声称模型已使用新版本。真实 Registry、卸载和整体视觉验收未完成。

## 升级响应丢失与版本变化后的请求恢复（2026-10-14）

- 修复请求索引包含旧版本的问题：现按会话、来源、坐标和范围定位原请求，Host 完成升级后，新版本列表仍能恢复原计划与请求键。只有重新查询确认 completed 或已恢复/无需恢复的 failed，才可显式关闭本次结果；未知、待恢复、其他主机/操作结果不能清除请求。
- 单元测试新增版本变化后的索引稳定性、非终态拒绝关闭、终态关闭的持久删除及后续生成新请求键。插件 build/typecheck、109 项测试、466 个断言通过。
- 新 tarball 在隔离 DSH 中实测：第一次 executePlan 到达 Host 后，浏览器丢弃接受响应；页面显示未确认。刷新后列表已是 1.3.0，进入详情仍显示原请求；再次查询返回同一 operationId，最终显示成功，关闭结果后回到正常新版本详情。两次 executePlan 请求对应磁盘上恰好一条 upgraded/completed 操作，新资源内容和 metadata.version 均正确，pageerror 为空。
- [浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-response-loss-ui-result.json)、[文件与操作记录核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-response-loss-files-result.json)、[关闭已核验结果后的页面](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-response-loss-completed.png)。临时服务已停止。
- 本轮没有验证 Host 重启、提交前尚未接受即丢响应、升级中的多目标故障及未知记录人工处理；相关验收继续保留，不声明完整升级/恢复完成。

## 精确卸载只读核验（2026-10-14）

- 新增 inspectRemovalBaseline，只检查 Host 选定的单个 DSH 安装位置，同时保留完整 inventory item 供后续执行阶段比较。其他 Agent 或其他位置的 targets 不纳入本次卸载范围；共享归属、别名、元数据异常和本地修改阻断操作。
- 仅明确 ENOENT 返回 remove-stale-record；其他文件系统错误返回 target-unavailable。读取清单后再次检查缺失路径，核验期间重新出现的目录返回 record-changed，不提供失效记录清理计划。
- 临时目录测试覆盖多目标保留、共享 Suite、文件修改、缺失目录、其他来源占用、清单变化、目录重新出现、符号链接与 ENOTDIR。卸载和升级基线测试共 2 项、25 个断言通过；CLI typecheck/lint/build 通过；插件 build/typecheck 与 109 项测试、466 个断言通过，git diff --check 通过。
- 本轮仅完成只读核验及运行时接口，不执行删除。卸载计划、隔离事务、清单提交、崩溃恢复、Host 写接口及确认页面仍未完成；执行时必须在目标锁内再次核验，不能将只读计划作为写入时事实。

## 精确卸载清单提交（2026-10-14）

- 新增 commitRemovalInventory，复用 InventoryStore.mutateAtomic，在清单锁内比较完整原技能记录，删除唯一确认 target，保留其他 target 与无关记录。提交前复用 canonical 目标快照检查其他来源占用，再等待调用方写入 before/after 日志；日志失败时不提交清单。
- 临时目录测试覆盖精确删除、最后一个 target 删除、其他位置和技能保留、回调先于写入、重复提交拒绝、版本变化、其他来源占用，以及日志失败保持原清单。与只读核验合计 2 项测试、26 个断言通过；CLI typecheck/lint 与 git diff --check 通过。
- 此入口尚未接入 Host；调用方必须持有目标锁并完成文件核验。文件隔离、卸载专用日志及恢复协调仍待实现，不以清单提交测试代替可恢复卸载验收。

## 卸载日志与启动扫描（2026-10-14）

- OperationStore 增加独立 removal 记录及带 revision 比较的原子事件写入；记录精确目标、旧清单项、文件摘要、隔离位置、隔离完成、清单提交前后、残留清理及恢复意图。旧安装/升级 transaction 协议保持独立。
- 日志校验绑定操作来源、坐标、版本和唯一目标；隔离路径限定为目标根同级的 .skillhub-trash/operationId。正常卸载必须先记录隔离完成再提交清单；失效记录清理不能伪造隔离动作；清单 after 只能删除确认目标。实际同盘、宿主策略及目录别名仍由后续执行器核验。
- 未确认的隔离意图阻断同目标新写入；清单已提交但未确认清理成功的记录继续出现在启动核验列表。已提交记录拒绝恢复旧目录，未持久确认提交的卸载不能直接标记 completed。
- 针对性日志及原有操作日志测试 21 项、108 个断言通过；完整 integrations 测试 74 项、625 个断言通过，包含真实进程中断恢复回归。CLI typecheck/lint/build 通过，插件 build/typecheck 与 109 项测试、466 个断言通过，git diff --check 通过。
- 当前日志只保存事实，不自动执行文件移动或恢复；卸载执行器、恢复协调、Host 接口及 UI 仍待完成。尚无真实 DSH 卸载验收，不关闭完整卸载任务。

## 原生卸载执行器（2026-10-14）

- 新增 executeNativeRemoval：绑定已接受操作与完整确认基线，在共享目标锁内重新检查归属和文件；隔离目录限定为目标根同级 .skillhub-trash/operationId，并核验 canonical 路径、所有者、目录权限与同文件系统，不允许覆盖已有隔离位置。
- 隔离前持久记录意图，rename 后验证原 metadata 和文件摘要；清单锁内再次检查宿主策略、原位置缺失与隔离内容，等待日志后精确移除一个 target。失效记录清理不移动文件，提交前同样检查原位置仍不存在。
- 清单提交后再清理隔离文件；清理被拒绝时保留逻辑卸载结果、警告与可核验残留。意图写入后出现错误保留现场并标记 recovery-required，不根据异常推断清单未提交，也不自行恢复旧文件。
- 真实临时目录测试覆盖正常卸载、失效记录清理、本地修改阻断、隔离后策略拒绝、提交后清理拒绝、committed 日志已写但确认丢失。确认丢失时清单仍只保留其他 target，旧文件只在隔离目录中，原路径不被恢复。相关卸载测试共 10 项、95 个断言通过；CLI typecheck/lint/build 与 git diff --check 通过。
- 执行器仍为内部实现，尚未接入 NativeRuntime/Host 写入口；外层操作 lease、自动恢复核验、卸载确认页面及真实 DSH 验收仍待完成。本轮没有宣称完整卸载或崩溃恢复通过。

## 卸载恢复与内部运行时贯通（2026-10-14）

- 新增 recoverRemoval，在目标锁及 inventory 快照锁内核验日志关系、当前清单、原目录和隔离内容。清单仍为 before 时只恢复匹配基线且原路径空闲的目录；清单为 after 时只完成提交确认和隔离清理，不恢复旧文件。被占用、内容修改、策略拒绝及清单冲突返回 unknown 并保留现场。
- NativeRuntime 增加 acceptRemoval/executeRemoval，复用持久请求查重和完整操作 lease；recover 按操作类型分流。LocalRuntime 增加 submitRemoval，先返回已接受结果，完整执行仍受关闭等待跟踪；持久重试不重新读取过期计划或执行卸载。
- 临时目录测试覆盖恢复原文件、已提交残留清理、恢复时原路径被占用、隔离内容变化、策略拒绝、重复核验，以及清单已提交但 committed 日志失败或确认丢失。最后两种情况均由实际 inventory 确认提交，未恢复旧文件。内部运行时测试验证重开后原请求复用、失效记录清理及恢复分流。
- 完整 integrations 回归 81 项、702 个断言通过；随后新增 committed 日志写入失败场景，卸载文件测试单独重跑 7 项、82 个断言通过。CLI typecheck/lint/build 通过；插件 build/typecheck 与 110 项测试、472 个断言通过，git diff --check 通过。
- 尚未完成卸载专属真实进程强制中断矩阵、Host 卸载计划/执行 Remote、宿主隔离路径授权和恢复入口适配、页面及真实 DSH 验收。当前 Host 恢复入口仍仅支持单目标 install，不能将内部恢复测试视为产品入口已支持卸载。

## Host 卸载恢复路径授权（2026-10-14）

- 新增 assertRemovalMutation，授权范围仅包含确认的技能根、精确技能目录、同级隔离根及当前 operationId 的隔离目录；拒绝其他技能、其他操作、任意子路径、无效身份、符号链接重定向及当前策略拒绝。不扩大原安装授权范围。
- Host 内部恢复入口允许 uninstall/remove-stale-record，仍从真实 session、cwd、文件系统映射和当前策略重新解析历史目标；恢复回调绑定原操作 ID。升级的多目标恢复入口仍待补齐。
- 编译后的 Host 测试实际创建技能和清单，调用内置核心隔离后在提交前注入拒绝，再通过 Host 恢复：read-only 拒绝且保持 recovery-required；danger-full-access 恢复原文件和清单、清除该操作的待恢复状态。测试使用历史来源身份，无远端查询或重新安装。
- 插件 build/typecheck、111 项测试、480 个断言通过，git diff --check 通过。本轮未修改共享 CLI 核心，未重复核心回归。
- prepareUninstall、统一 executePlan 的持久操作类型分流、确认 UI 及真实 DSH 浏览器卸载尚未实现；不能以内部 Host 测试声明页面卸载已可用。

## 卸载预检计划与持久请求查询（2026-10-14）

- NativeRuntime/LocalRuntime 新增 findPlanOperation，查询原升级或卸载请求摘要，保留已有操作摘要兼容性；强制 retry 查询不创建操作。executePlan 优先返回匹配的已接受操作，再处理尚未执行的计划；计划失效不影响持久查重。
- 新增 agent 作用域 prepareUninstall，基于已安装 itemKey 及真实 Host 上下文核验唯一目标，离线区分 uninstall/remove-stale-record。可变更计划在宿主策略和隔离路径预检通过后签发，包含当前版本、精确目标、操作类型及五分钟有效期；不访问远端、不写文件。
- 升级与卸载计划复用确认引用存储逻辑，来源变化、依赖卸载及插件关闭时同时清理。失效记录与正常卸载须在后续 UI 中分别确认。
- 核心测试覆盖三种操作重开查询、错误 owner/host、参数变化、未知请求不新增操作：6 项测试、48 个断言通过，CLI typecheck/lint/build 通过。插件 build/typecheck、112 项测试、489 个断言通过；新增卸载预检测试和生成 Remote 的方法清单/agent 作用域断言，git diff --check 通过。
- 新卸载计划尚未接入 executePlan 的首次执行分支，页面尚无卸载入口。后续需接通计划 claim、执行期间路径授权、确认 UI 和浏览器验收，不能把预检接口视为完整卸载功能。

## Host 卸载计划执行（2026-10-14）

- executePlan 在持久查重之后按待执行计划分流升级与卸载。卸载新请求核验原会话、来源、项目、文件系统和根目录，claim 计划与唯一请求键，再通过 LocalRuntime.submitRemoval 接受并跟踪执行；输入仍严格限定 planId/requestKey。
- 卸载核心的内部授权回调携带已接受操作 ID，Host 每次授权重新解析当前策略与可信根，将隔离路径限制到该操作目录。该参数由核心提供，不新增 Client 路径或操作 ID 输入。已接受执行不受原 RPC 等待取消影响。
- 编译后 Host 测试覆盖 uninstall/remove-stale-record 分流、精确目标、其他操作隔离目录和其他技能拒绝、read-only 拒绝、计划不同请求键冲突、项目变化拒绝，以及清空计划后仍从持久查询返回原操作。统一接口的通用错误文案不再仅描述升级。
- 核心卸载与运行时测试 13 项、130 个断言通过，CLI typecheck/lint/build 通过；插件最终 build/typecheck、114 项测试、507 个断言通过，git diff --check 通过。
- 当前已具备 Host 预检与执行接口，但卸载确认页面、客户端请求持久化和真实 DSH Gateway/浏览器卸载验收仍待完成。未将 Host 编排测试视为真实用户操作闭环，也未关闭完整卸载任务。

## 客户端卸载请求持久化（2026-10-14）

- 升级与卸载请求复用确认请求存储实现，保留原升级存储前缀；卸载使用独立前缀，接受 uninstall/remove-stale-record 结果，拒绝其他操作类型或主机不匹配。提交前持久化 planId/requestKey/hostInstanceId，响应丢失和重载后复用原请求；在途重复点击合并。
- RemovalRequests 可按会话和来源枚举原请求坐标，不依赖仍存在的安装行，为卸载后列表消失、刷新后的结果入口提供依据。仅重新查询确认的终态结果可关闭；损坏存储阻止新提交，关闭卸载结果不影响同坐标的升级请求。
- 新增普通卸载/失效记录清理的响应丢失、重载、重复点击、会话/来源隔离、操作类型拒绝、终态关闭及损坏存储测试。相关请求测试 7 项、43 个断言通过；插件 build/typecheck、117 项测试、533 个断言通过，git diff --check 通过。
- 本轮仅完成请求管理器，尚未注入页面服务或渲染卸载按钮。下一步接入确认弹窗、列表中的未关闭卸载结果入口，并验证真实 DSH 里的刷新恢复、删除后焦点及页面状态。

## 卸载页面与真实 DSH 响应丢失恢复（2026-10-14）

- 已安装详情接入卸载预检和原生 dialog 确认，默认聚焦取消；确认显示来源、版本、范围、只读路径和有效期。普通卸载与仅清理失效记录使用不同说明及按钮，过期计划不能提交。
- 页面使用共享操作查询与卸载请求存储。列表新增未关闭的卸载结果区，即使安装行已消失，刷新后也能恢复原请求；已核验终态可关闭结果。成功后隐藏旧详情操作，返回列表时原按钮缺失则聚焦范围筛选。
- 插件 build/typecheck、117 项测试、533 个断言通过。新 tarball 安装到隔离 DSH Profile，经真实 Gateway/浏览器验证取消默认焦点和无提交，随后接受卸载但丢弃响应，刷新后从结果区重试并取得同一 operationId，显示已卸载及项目范围 0 项，关闭结果成功，无 pageerror。
- 请求计数已移到取消操作之前，并用新的夹具完整重跑，确认取消未调用 executePlan。最终两次 HTTP 请求对应恰好一条 uninstall/completed 记录；直接检查目标目录、隔离目录和 inventory，三者均无该安装残留。
- [浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-result.json)、[文件及操作核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-files-result.json)、[确认弹窗](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-confirmation.png)、[刷新恢复后的结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-recovered.png)。临时 Host/Registry 已停止。
- 本轮尚未实机验证失效记录清理页面、正常响应下详情返回焦点、关闭最后一条结果后的焦点、窄屏/深色外观、真实 Registry 和卸载专属强制中断矩阵。界面仍需整体视觉优化，不据此关闭完整产品验收。

## 失效记录清理与卸载焦点实机验证（2026-10-14）

- 关闭详情页卸载结果后聚焦详情标题；列表关闭最后一条结果后聚焦范围筛选。确认弹窗采用 border-box 限制视口宽高并允许长内容换行，避免窄屏横向溢出。
- 新 tarball 在隔离 DSH 中验证失效记录清理：将夹具技能移到扫描范围外的测试备份目录，保留安装记录；页面显示独立的清理确认，取消零提交，再次确认恰好一次 executePlan，结果为 stale-record-removed。inventory 移除该 target，未创建隔离目录，备份全部文件哈希保持不变。
- 正常响应流程验证详情结果关闭后标题获焦、返回列表后筛选获焦。390px 视口下确认弹窗边界和内容均无横向溢出；该证据仅覆盖弹窗，不覆盖整页移动端布局。
- 再用新夹具验证普通卸载丢响应流程，刷新恢复同一操作后关闭列表最后一条结果，筛选获得焦点。两轮均无 pageerror，临时 Host/Registry 已停止，测试备份保留。
- 插件 117 项测试、533 个断言通过；最终弹窗样式重新 build/typecheck 并打包实测，探针语法和 git diff --check 通过。[清理浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-stale-removal-result.json)、[清理文件核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-stale-removal-files-result.json)、[窄屏确认截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-stale-removal-confirmation.png)、[丢响应及焦点结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-focus-removal-result.json)。结合此前核心精确目标与 ENOENT 测试，任务 4.4、4.6 已勾选。
- 卸载专属真实进程强制中断矩阵、失败恢复产品入口、真实 Registry、深色及整页窄屏视觉验收仍未完成，不关闭整体生命周期或发布验收。

## 卸载真实进程强制中断矩阵（2026-10-14）

- 新增独立 removal-crash-child 夹具，通过 NativeRuntime 接受并执行操作，使用 SIGKILL 在日志意图前后、目录 rename 后、隔离确认后、清单提交前后、committed 确认后和清理文件后中断。失效记录清理另覆盖对应无文件移动边界。
- 恢复子进程再次在恢复意图写入后、恢复 rename 后尚未确认，以及已提交残留清理后尚未确认处被终止，随后重开运行时继续核验。共 16 个场景；实际等待目标/操作锁和 inventory 锁过期，不删除锁文件或模拟恢复成功。
- 验证清单 before/after 与磁盘结果一致、提交前恢复原文件、提交后不恢复旧文件、隔离残留清理、同技能其他安装位置内容及记录保留，以及重复恢复不再次变更、无遗留待恢复记录。
- `bun test test/unit/integrations/removal-crash-recovery.test.ts` 通过：1 项矩阵测试、145 个断言，约 65 秒；CLI typecheck/lint 和 git diff --check 通过。本轮只新增测试夹具，没有修改生产执行器或恢复实现。
- 证据为当前 Linux 环境的真实子进程中断；Windows 跳过 SIGKILL 矩阵，不能据此声明 Windows 或 macOS 验收通过。失败恢复的产品入口、升级多目标恢复入口、真实 Registry、整页视觉及发布验收仍待完成。
# 卸载恢复 Remote 与页面入口（2026-10-14）

- 新增 agent scope `recoverOperation({operationId,hostInstanceId})`，复用现有恢复协调器和原操作日志；不存在/其他会话的操作返回 null，Host 不匹配和额外字段被拒绝，底层路径异常不透传。
- 卸载结果增加“核验并恢复”按钮、运行中和失败状态；恢复结束或请求失败后重新查询原操作，保留原请求记录。升级多目标恢复尚未开放。
- 插件构建与类型检查通过；更新生成协议端点清单后，`bun test`：118 通过、0 失败、549 assertions。覆盖新增 Remote 边界以及既有真实文件恢复测试。
- 新增按钮尚未完成真实 DSH 浏览器点击和断线恢复验收，因此不勾选整体恢复/产品验收任务；本节不代表跨平台或正式发布验证。


## 升级完整目标集合恢复（2026-10-14）

- Host 恢复协调器支持 upgrade，原操作目标必须完整匹配当前用户级/项目级根；重复目标或上下文外目标拒绝。每一步清理/回滚前重新验证完整集合，再核验实际变更路径。
- 升级结果加入显式恢复按钮，复用原 operationId；请求完成或失败均重新查询状态，不重新生成升级计划。
- `plugins/dsh`: `bun run check` 通过（构建、类型检查；119 tests，559 assertions）。新增 Host 测试覆盖双目标、任意路径拒绝、只读策略、会话目录变化、外部项目及重复目标。
- `cli`: `bun test test/unit/integrations/install-recovery.test.ts` 26 tests / 151 assertions 通过；`bun test test/unit/integrations/native-upgrade.test.ts` 4 tests / 24 assertions 通过，包含替换后失败恢复两个旧目标。
- 以上分别证明 Host 授权边界与核心文件恢复行为；不替代真实 DSH 页面端到端恢复、Host 重启和跨平台验收。相关整体任务仍保持未完成。

## 真实 DSH 卸载恢复按钮与响应丢失（2026-10-14）

- 在隔离 DSH `0.2.0-rc.2` Profile 安装当前 tarball，发现探针通过。真实卸载执行在隔离目录形成后、inventory 提交前注入一次异常，页面显示恢复入口。
- 点击“核验并恢复”，真实 Remote 返回 restored 后刻意丢弃 HTTP 响应；页面重新查询原 operationId 显示“原文件已恢复”，浏览器刷新后历史结果仍可见。计数：executePlan 1 次、recoverOperation 1 次，浏览器 pageerror 为空。
- 独立核对日志中全部技能文件哈希一致、原 inventory item 完全保留、隔离目录不存在、restore.completed 已持久化。已查看截图，已停止本次 Host 和 Registry。
- 证据：[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-recovery-result.json)、[文件结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-recovery-files-result.json)、[截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-recovery-restored.png)。
- 本次仅验证 Linux 单目标卸载的提交前恢复及浏览器刷新，不替代升级恢复、Host 重启、提交后残留清理和跨平台验收。探针后续仅将成功分支改为正常 return/finally 清理，语法检查通过，调整后的清理分支未重跑浏览器。

## 真实 DSH Host 重启后的卸载恢复（2026-10-14）

- 隔离 DSH `0.2.0-rc.2`，在页面卸载进入 recovery-required 且原目标目录已隔离后，终止旧 Host 并确认其进程消失。新 Host 复用持久目录与监听端口，跳过夹具生成，不保留旧进程的运行时对象。
- 同一浏览器标签页进入新 Host，已安装列表中的卸载历史可查询原操作并显示恢复按钮。点击恢复，丢弃其首次响应，页面重新查询成功；再次刷新仍显示原文件已恢复。executePlan/recoverOperation 各 1 次，无 pageerror。
- 全部日志文件哈希一致，原 inventory item 保留，隔离目录不存在，restore.completed 持久化。截图已查看；旧 Host、新 Host、Registry 均已停止，临时访问地址文件已删除。
- 证据：[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-restart-result.json)、[进程重启结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-restart-host-result.json)、[文件核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-restart-files-result.json)、[截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-removal-restart-restored.png)。
- 覆盖正常终止后重启与持久记录恢复，不等同于 SIGKILL、浏览器完全关闭后恢复、多目标升级或跨平台验收。SIGKILL 核心矩阵另有记录，整体验收任务保持未完成。

## 升级独立历史与真实页面恢复（2026-10-14）

- 补齐已安装列表中的升级历史区域：原请求与结果不依赖安装列表行；持久请求枚举复用现有实现，保持升级/卸载命名空间隔离。增加缺失安装记录、刷新、原参数重试、跨会话/来源隔离及损坏存储测试。
- 真实 DSH `0.2.0-rc.2` 通过当前 tarball：升级备份后注入异常并阻止自动回滚，刷新后从升级历史点击恢复；恢复响应丢失后查询确认旧版本 1.2.0。关闭结果后焦点回到筛选框。executePlan/recoverOperation 各 1 次，pageerror 为空。
- 首次截图发现已恢复后仍展示过期请求错误；已修复升级和卸载控件，并重新构建、运行全套插件检查及真实升级浏览器验证，最终截图不含矛盾提示。
- `bun run check`：构建、类型检查通过；121 tests、570 assertions 通过。独立磁盘核对：完整 inventory 与事务 before 相等，旧版本全部 metadata 文件哈希匹配，备份/暂存目录消失，restored 已持久化。
- 证据：[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-recovery-result.json)、[文件核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-recovery-files-result.json)、[截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-recovery-restored.png)。本次 Host、Registry 已停止。
- 本次覆盖单项目目标升级；双目标浏览器升级恢复、升级 Host 重启、跨平台及完整产品验收仍待完成。

## 双目标升级真实浏览器恢复（2026-10-14）

- 隔离 DSH `0.2.0-rc.2` 通过真实安装工具在用户级和项目级创建同一版本，升级确认响应明确包含两个 scope。
- 第一目标已安装新版本，第二目标已备份旧版本时注入异常并阻止自动回滚。日志核对：completed install 1 条、completed backup 2 条。刷新后从升级历史恢复，恢复响应丢失后查询成功，关闭结果焦点回到筛选框；升级/恢复请求各 1 次，无 pageerror。
- 两个目标均恢复为 1.2.0，全部 metadata 文件哈希匹配；完整 inventory 与 before 一致，备份/暂存消失，restored 持久化。已查看截图，停止本次 Host/Registry。
- 证据：[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-two-target-result.json)、[文件及事务核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-two-target-files-result.json)、[截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-two-target-restored.png)。
- 本次验证 Linux 同一 Host 的双目标恢复及页面刷新；未将其扩展为升级 Host 重启、跨平台或真实生产 Registry 的验收结论。

## 共享 CLI 全量回归（2026-10-14）

- 在 `cli/` 执行 `bun test`：671 tests、2765 assertions 全部通过，0 failures，66 个测试文件，耗时 225.93 秒。覆盖现有 CLI 行为及安装/卸载真实强制中断恢复矩阵，不仅是插件测试。
- `bun run typecheck`、`bun run lint`、`bun run build` 均通过。插件最近一次 `bun run check` 为 121 tests、570 assertions，通过构建与类型检查。
- 本轮之后只调整测试探针与 README/验证记录，未改共享 CLI 生产实现。未将 Linux 回归结论扩展到 macOS/Windows；插件独立 lint 与发布检查仍须补齐，因此 tasks 5.1 暂不整体勾选。

## 插件 lint 与独立检查工作流（2026-10-14）

- 插件新增 `.eslintrc.cjs`，继承 CLI 已锁定的 TypeScript ESLint 规则；`bun run lint` 覆盖 src/scripts/test 的 ts/tsx/mjs，排除 lib 和生成契约，无新增依赖。修正卡片全角空白；两处安全输入正则显式标注控制字符拒绝用途，保持校验行为。
- `bun run check` 现顺序执行 build/typecheck/lint/tests，本轮全部通过：121 tests、570 assertions。结合上一节 CLI 全量检查，tasks 5.1 已完成；其余实机/发布验收不随之勾选。
- 新增 `check-dsh-plugin.yml`：插件或 CLI 相关 PR 触发，共享核心与插件检查通过后保存开发 tarball，仅 contents:read，不执行 npm 发布/Release/部署。YAML 解析及触发路径、只读权限、检查命令静态断言通过。
- 本地无 actionlint，未运行 GitHub 托管工作流；未将 YAML 静态检查或本地通过声明为远端 CI 已绿。Linux/Node 24 为当前检查环境，平台支持范围仍以兼容验收为准。

## 可选模块失败重试（2026-10-14）

- 核对当前 API/Host 行为：匿名拒绝、404 和临时错误分别保留原语义，安全审核遵循已确认的匿名限制。修复 auto 模块探测失败后没有用户重试入口的问题；不改变确认可用才展示 Tab 的产品规则。
- 新增测试证明评论探测 503 不缓存为永久不可用，恢复后重新读取详情可变为 available，空列表也是可用模块。插件 `bun run check` 全通过：122 tests、574 assertions；新增实机脚本后 lint/typecheck 再次通过。
- 当前 tarball 在真实 DSH `0.2.0-rc.2` + 隔离 Registry 中通过：503 时隐藏评论 Tab，展示未确认提示；点击重新检查后显示 Tab，激活后读取评论正文。写接口计数 0，无 pageerror。已查看前后截图并停止测试服务。
- 证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-optional-module-result.json)、[未确认](../../../docs/research/dsh-native-plugin-2026-10-12/m2-optional-module-unconfirmed.png)、[恢复可用](../../../docs/research/dsh-native-plugin-2026-10-12/m2-optional-module-available.png)。实际开源/SaaS 环境联调仍未完成。

## Host 依赖门禁（2026-10-14）

- 新增实际模块解析位置的精确依赖核验，基线与 package peers 有一致性测试。三种新写请求只在首次准备时校验，持久回执重试不重放；恢复写入也校验。查询不受该门禁影响。
- 新增只读 getCompatibility Remote 和页面核验失败提示；字段只代表依赖门禁，不冒充完整宿主兼容。
- 插件 `bun run check` 通过（125 tests、622 assertions）。补充恢复写入拒绝及其他会话隔离断言后，再跑 typecheck/lint 和相关测试：14 tests、102 assertions 通过。
- 未执行新提示的真实 DSH 浏览器验证或实际安装混装依赖；尚未完成主程序/Client 组合与平台矩阵。tasks 10.3/10.5 不整体勾选。

## 兼容性写入口限制（2026-10-14）

- 新增共享 Host 核验查询与写入状态提示，安装/升级/卸载/恢复控件在 pending/error/unverified 时禁用新写按钮并在提交处理函数再次检查；原请求查询和只读更新检查保留。页面和对话详情安装共用控件。
- 插件 `bun run check` 全通过：125 tests、625 assertions。新增浏览器探针后 lint/typecheck 再次通过。
- 当前 tarball 在真实 DSH `0.2.0-rc.2` 返回 matched；浏览器传输层故障注入 unverified 后，安装/升级/卸载入口禁用，检查更新仍返回结果；恢复真实响应并重试后按钮启用。写请求 0，无 pageerror。截图已查看，测试 Host/Registry 已停止。
- 证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m0-controls-compatibility-result.json)、[禁用状态截图](../../../docs/research/dsh-native-plugin-2026-10-12/m0-controls-compatibility-disabled.png)。未执行真实依赖混装、对话入口禁用或恢复按钮禁用的浏览器测试，不将本次结果扩展为完整兼容性验收。

## Registry 匿名契约检查入口（2026-10-14）

- 新增 `bun run probe:registry`，覆盖搜索、精确版本解析、元数据、说明、可选版本/评论、归档指纹和审核访问状态；只读 HTTP，解压目录独立且结束后清理，不写 DSH 技能目录。
- 六个本地 HTTP 夹具场景通过，共 221 assertions；包括真实命令行子进程的成功/失败退出码及 JSON 输出。指纹不符、危险归档、拒绝访问和版本不匹配均不能得到必需项通过结论；可选接口失败独立报告。
- 插件 typecheck/lint 通过。共享 CLI 导入补齐 ESM 扩展名后，CLI typecheck/lint/build 及平台测试（32 tests、46 assertions）通过。
- 使用方法与报告语义写入插件 README，Registry 契约分册链接同一入口。尚未连接真实开源或 SaaS Registry，不勾选 tasks 0.4、9.3、9.4；HTTP 夹具不证明实际服务端兼容。

## 当前包全量检查与使用说明校准（2026-10-14）

- 当前源码执行 `plugins/dsh` 的 `bun run check`，构建、类型检查、lint 及 131 tests / 846 assertions 全部通过。包含新增 Registry 契约命令的子进程测试；不将其视为真实 Registry 结果。
- 对照 Host/Client 实现及已保存实机报告，修正 README 中升级未开放、安装工具未实机验证等历史状态；补充卸载确认、失效记录清理、原请求查询及恢复操作说明。保留模型驱动对话、升级 Host 重启、真实 Registry 和跨平台未验收边界。
- README 相对链接检查及 `git diff --check` 通过。本轮未重新启动 DSH，不新增浏览器或平台兼容性结论，未发布包。

## Registry 元数据读取期限（2026-10-14）

- RegistryAdapter 为每个 JSON/SKILL.md 请求增加 30 秒读取期限，合并调用方取消信号并在请求结束释放计时器；覆盖响应头未返回及正文停滞。超时沿用可重试 NETWORK_ERROR，主动取消保留 CANCELLED。归档下载与已接受事务不受此计时器影响。
- 本地 HTTP 服务器测试分别模拟响应头停滞、正文停滞、恢复服务后同一适配器成功重试和调用方主动取消；使用短测试期限验证实际 fetch 中止路径。适配器专项 22 tests / 40 assertions 通过。
- 插件全量 `bun run check` 通过：构建、typecheck、lint、134 tests / 851 assertions。实现说明和 README 已同步。可选能力探测只缓存 available/anonymous-denied，NETWORK_ERROR 返回 unknown，不因超时永久隐藏模块。
- 未执行浏览器超时提示验收或真实 Registry 慢请求测试，不扩大为完整异常交互验收结论。

## 升级 Host 重启后的页面恢复（2026-10-14）

- 升级浏览器探针新增重启握手，保存原 operationId，恢复到相同 origin 后从独立升级历史继续查询。协调器确认持久日志 operation.status.phase=recovery-required、原目标已备份，再终止旧 Host 并等待退出；新进程复用数据目录和端口，跳过夹具生成及故障钩子。
- 新隔离 Profile 中，真实 DSH 0.2.0-rc.2 成功执行单项目目标升级失败、Host 重启、页面恢复。故意丢弃恢复响应后，原操作查询确认 restored；关闭结果后焦点回到范围筛选。executePlan/recoverOperation 各一次，pageerror 为空。
- 磁盘核验：完整 inventory 与升级前字节一致，目标目录全部 3 个文件哈希一致，backup/staging 均已清理。含测试访问令牌的握手文件已删除，测试 Host/Registry 均已退出；截图已人工查看。
- 首次协调器误读日志字段，修正为 operation.status.phase；第二次复用旧环境被遗留待恢复操作保护阻断，改用新隔离数据目录通过，未删除日志绕过保护。旧失败现场保留供排查。
- 证据：[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-restart-result.json)、[进程及文件结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-restart-files-result.json)、[恢复截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-upgrade-restart-restored.png)。探针 lint、Node 语法检查和 diff 检查通过。
- 本次为 Linux 单项目目标、正常终止 Host 后重启、同一浏览器标签页的恢复。未证明多目标升级 Host 重启、SIGKILL Host、浏览器完全关闭或其他平台通过；完整验收任务保持未完成。

## 已安装表格与本地过滤（2026-10-14）

- 已安装列表由卡片改为语义表格，包含技能坐标、版本、范围、本地状态与详情操作；新增忽略大小写的本地坐标/版本过滤和独立空结果提示，详情返回保留关键词。范围筛选、刷新和原操作历史保持原行为。
- 已安装区域统一控件字号、尺寸、边框、焦点和禁用样式，颜色继承 DSH。内容宽度不超过 680px 时记录纵向排列，保留字段标签；不增加主题依赖或影响宿主其他区域。
- 插件 `bun run check` 通过：134 tests / 851 assertions，构建/typecheck/lint 通过。增加浏览器探针后 lint/typecheck 再次通过。
- 当前 tarball 在真实 DSH 0.2.0-rc.2 验证本地过滤零额外请求、返回关键词/焦点保留、576px 内容区域无横向溢出、深色宿主计算颜色及控件颜色一致。写请求 0，pageerror 为空；三张截图已查看。测试 Host/Registry 已停止。
- 证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-layout-result.json)、[宽屏](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-wide.png)、[窄屏](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-narrow.png)、[深色](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-dark-preference.png)。
- 尚未实现表格行内更新状态及快捷操作；更新/升级/卸载仍在详情中。尚未覆盖 390px 视口、所有操作弹窗的深色/窄屏及完整可访问性验收，任务 2.5 不整体勾选。

## 已安装行内更新状态与快捷操作（2026-10-14）

- 表格增加更新状态及检查时间，每条记录提供主动检查更新、详情、升级及卸载/清理入口。升级仅在已确认可升级且当前检查没有错误时出现；记录异常时卸载入口禁用。
- 更新检查改为按会话、来源修订和 itemKey 隔离的 TanStack Query，默认 disabled，仅显式 refetch；行与详情共享结果，导航不重新检查。升级/卸载快捷入口只进入并聚焦相应详情操作区，不自动生成计划或执行写入；返回恢复原操作按钮焦点。
- 插件全量 check 通过（134 tests、851 assertions，含 build/typecheck/lint）；新增探针分支后 lint/typecheck 通过。实机第一次断言未包含单元格时间文本，修正为语义 cell 定位后通过，生产 UI 未为此调整。
- 真实 DSH 0.2.0-rc.2 验证初始无更新检查、一次主动检查后可升级、生成计划取消、卸载确认取消、两类入口及返回焦点、窄内容区和深色显示。总计 checkUpdate 1 次、写接口 0 次、pageerror 为空。截图已查看，隔离 Host/Registry 已退出。
- 证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-row-actions-installed-layout-result.json)、[宽屏](../../../docs/research/dsh-native-plugin-2026-10-12/m3-row-actions-installed-wide.png)、[窄屏](../../../docs/research/dsh-native-plugin-2026-10-12/m3-row-actions-installed-narrow.png)、[深色](../../../docs/research/dsh-native-plugin-2026-10-12/m3-row-actions-installed-dark-preference.png)。本轮未覆盖多行并发检查、行内检查失败重试、390px 或确认后的全生命周期，不关闭完整 M4 验收。

## 更新重查失败与 390px 深色卸载确认（2026-10-14）

- 实机验证显式成功检查后发生网络失败，升级入口隐藏；手动重试成功后恢复。列表/详情导航不增加检查，合计三次显式请求、零写接口、无 pageerror。
- 首轮截图发现失败后旧时间含义不明确、深色确认按钮固定红色对比度不足。已将旧时间标为“上次成功检查”，危险操作色随继承的 color-scheme 选择深浅配色，并收敛 dialog 边框。
- 当前 tarball 在隔离 DSH 0.2.0-rc.2 重跑通过。390px 视口下列表内容宽/滚动宽均 286px；深色确认弹窗宽 358px，内部无横向溢出，默认焦点为取消。确认文字与背景实测对比度 11.03:1，截图已查看。
- 插件完整 check 通过：134 tests / 851 assertions、build/typecheck/lint 均通过。测试 Host/Registry 已停止。
- 证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-installed-stress-installed-layout-result.json)、[重查失败](../../../docs/research/dsh-native-plugin-2026-10-12/m3-installed-stress-installed-recheck-failed.png)、[390px 列表](../../../docs/research/dsh-native-plugin-2026-10-12/m3-installed-stress-installed-mobile.png)、[深色确认](../../../docs/research/dsh-native-plugin-2026-10-12/m3-installed-stress-installed-dark-dialog-mobile.png)。仅覆盖该页面、单条记录及卸载确认，不替代多行并发、屏幕阅读器、所有页面和平台的完整验收。

## 已安装返回滚动与详情键盘导航（2026-10-14）

- 已安装列表在进入详情前保存实际广场滚动位置，返回先恢复原按钮焦点但禁止焦点引起滚动，再还原 scrollTop。关键词和范围沿用原本地状态。
- 每个可见详情 Tab 均拥有实际对应的 panel 节点；非当前面板 hidden 且不挂载正文/查询，当前 panel 可通过 Tab 键获得焦点。保持原方向键和 Home/End 切换行为。
- 插件完整 check 通过（134 tests、851 assertions，build/typecheck/lint 通过），最终浏览器探针 lint/typecheck 通过。实机探针首次在详情重读时取得脱离文档的旧节点，改为等待读取结束后验证，最终通过。
- 当前 tarball 在真实 DSH 0.2.0-rc.2 验证：390px 视口中列表 scrollTop 147px，详情返回后 147px，原按钮焦点恢复；Tab 关联及 ArrowRight/End/Home/Tab 验证通过；更新失败重试、行内取消、窄屏与深色确认同时复验通过，零写接口、无 pageerror。
- [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-installed-scroll-keyboard-result.json)。隔离 Host/Registry 已停止。未覆盖所有动态列表变化、对话抽屉键盘、屏幕阅读器和跨平台，不关闭完整可访问性验收。

## 开源后端消费契约核对（2026-10-14）

- 对照开源 Controller/DTO/服务实现核对插件使用的搜索、resolve、元信息、版本、评论、原始文件与下载路径。修正 Registry 分册下载基线：以 resolve.downloadUrl 为准，当前返回 /api/v1 路径，CLI 另提供同义下载路由；插件已有实现使用返回地址，无需更改生产代码。
- Registry 分册增加 SaaS 同步核对表，明确消费字段、nullable/可省略语义、原始流与接口分层。本轮未新增生产路由、DTO 字段、迁移或匿名访问授权，不需重新生成未变化的 OpenAPI。
- 在 SkillControllerTest 新增匿名固定版本 SKILL.md 原始 UTF-8 响应测试（Accept:text/plain，实际 application/octet-stream），及 size=1 版本探测分页与显式 nullable changelog/publishedAt 测试。
- 从 server 执行 Maven reactor 定向测试（-pl skillhub-app -am，-Dsurefire.failIfNoSpecifiedTests=false）：CliSkillControllerTest 12 项、SkillControllerTest 11 项、SkillReviewControllerTest 10 项、SecurityAuditControllerTest 9 项，共 42 项，失败/错误/跳过均 0。新增测试后已重跑 SkillControllerTest；未改动的其余类结果来自本轮先前执行。git diff --check 通过。
- [结构化结果](../../../docs/research/dsh-native-plugin-2026-10-12/registry-backend-contract-result.json)。这些 MockMvc 测试模拟业务服务，未验证真实数据库、对象存储、网关或 SaaS 部署。真实实例契约检查和 DSH 联调仍待完成，任务 0.4/9.3/9.4 不整体勾选。

## 双语开发指南与包维护命令（2026-10-14）

- 新增 README.en.md，覆盖开发包定位、构建、隔离 Profile 安装、配置、页面与对话操作、恢复、兼容限制、Registry 检查和发布边界；明确中文界面不等于已提供英文 UI。中文 README 增加英文入口与维护说明，package files 纳入英文文档。
- tarball 外部 Node 验证增加英文文档存在断言，测试通过（1 test、8 assertions），插件 lint/typecheck 通过，中英文仓库相对链接与 diff 检查通过。未重复未变更的完整业务测试。
- 当前 DSH plugin 命令将参数交给 pnpm，帮助查询也可能初始化 Profile。首次帮助调用意外新建默认 home 下空测试 Profile，已将本轮新建目录移动至临时备份，原路径不再存在；后续均显式设置隔离 DSH_HOME。
- 复制独立 Profile 实测移除与重装：首次 remove 携带 ignore-scripts 被 pnpm 拒绝，去掉该参数后成功；Profile 依赖中包移除后重新添加，Profile 外技能/日志/inventory 测试标记不变。[结果](../../../docs/research/dsh-native-plugin-2026-10-12/package-maintenance-result.json)。
- 该维护测试未启动 Host，标记不代表实际待恢复事务；未验证跨版本回退、日志降级兼容或正式发布。指南保留这些限制，任务 5.6/9.2/9.5 不整体勾选。

## 插件发布组件隔离与候选 tag 检查（2026-10-14）

- 固定插件 tag 前缀 dsh-v。publish-images 的 publish job 对该前缀的 Release 跳过；其 mirror-runtime-images job 保持 needs:publish，未增加 always 条件。其他 tag 和手动镜像执行条件不变。
- check-dsh-plugin 增加 dsh-v* push 触发，构建前通过环境变量传递 tag 并核对 package version，保留只读权限和 tarball 产物；不创建 Release、不上传 npm、不部署服务。
- 本地解析真实 YAML 并执行实际 job 条件表达式的 10 组输入，覆盖普通/预发布/大小写插件 tag、平台/CLI/Chart/Helm tag、其他 tag 和手动执行；预期全部通过。核对 CLI 原有 cli-v* 过滤和镜像同步依赖未变。校验脚本首次对 Python 导出的空值按 undefined 断言失败，修正为 JSON null 后通过。
- 直接运行候选工作流中的 Node 校验步骤：匹配当前包版本通过，错误版本及 CLI 前缀均失败。git diff --check 通过，中英文指南和发布分册同步。[结果](../../../docs/research/dsh-native-plugin-2026-10-12/release-isolation-result.json)。
- 未推送 tag、创建 Release、触发远端 Actions 或执行发布；本地条件核对不代表 GitHub 调度实测。正式 npm 发布工作流、发布权限/凭证配置、候选兼容矩阵与发布后验证仍待完成，任务 9.1 不整体勾选。

## Host 依赖真实 Node 解析验证（2026-10-14）

- 新增 `plugins/dsh/test/compatibility-node.test.ts`，将兼容门禁源码以 Node ESM 构建到仓库外临时目录，使用真实 `createRequire` 解析临时依赖包。9 个依赖分别覆盖错误版本、损坏 JSON、exports 禁止读取 package.json、缺失 manifest；完整基线在故障前及恢复后均通过，共 38 次独立 Node 进程检查。
- 每次检查启动新进程，避免 require 缓存掩盖包文件变化；不修改真实 DSH 或开发依赖。拒绝时验证固定错误信息且不暴露临时目录路径，最终清理本次测试创建的目录。
- 定向执行 `bun test test/compatibility.test.ts test/compatibility-node.test.ts`：3 tests、116 assertions，通过；插件 typecheck、lint 通过，git diff --check 通过。生产代码未变，本轮未重复完整业务回归。
- 该测试证明 Node 下依赖元信息门禁行为，不证明混装 DSH 整体能够启动，也不验证 Client 加载组合、运行时热替换、Node 支持范围或跨平台。任务 10.1/10.5 仍待完整验收。
- 核对候选发行产物的 `dsh-app-boot/lib/index.js`：`getDshRuntimeVersion()` 读取其自身 package.json；单独调用该函数不能证明实际主程序、Client 和全部加载依赖一致，不以此补写完整支持声明。

## 三平台自动化矩阵配置（2026-10-14）

- `check-dsh-plugin.yml` 扩展为 ubuntu-latest、macos-latest、windows-latest，保留 Node 24 / Bun 1.3.13、共享 CLI 回归、插件完整检查和 tarball 检查。显式 Bash、fail-fast:false、平台独立 artifact 名，保留 contents:read 及原触发条件；未添加发布或部署权限。
- 构建器临时依赖目录在 Windows 使用 junction，其他平台保留 dir symlink；仅作用于本次 mkdtemp 构建目录。中英文 README 和发布指南同步矩阵及验收边界。
- 本地解析实际 YAML，核对三平台、shell、产物名、权限、tag 过滤和完整检查入口。Linux 执行 `bun run check`：build/typecheck/lint 通过，135 tests、929 assertions，通过；git diff --check 通过。
- 未推送或触发远端 Actions，未执行 macOS/Windows；本地 Bun 为 1.3.14，CI 锁定 1.3.13 的执行结果仍待远端验证。矩阵尚未包含真实 DSH Profile 浏览器验收，故任务 5.4/10.4 不整体勾选。
- 工作流依据：[GitHub matrix](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/run-job-variations)、[Bash shell](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)、[artifact 命名](https://github.com/actions/upload-artifact/blob/main/README.md)。

## 对话详情抽屉键盘与窄屏验收（2026-10-14）

- 扩展 `search-card-probe.mjs`：打开默认焦点在关闭按钮，背景卡片不能通过 focus 获得焦点；验证 ArrowRight/End/Home 切换详情 Tab、Tab 进入正文 panel、tab/panel 双向关联，以及 390px 抽屉无横向溢出。保留 Escape/关闭后焦点返回、只读详情和历史卡片重放检查。
- 首次使用旧搜索记录时被失效保护拒绝，不能作为正常详情验收。随后通过隔离 Host 临时探针调用真实搜索工具，创建新会话和工具记录；未调用模型、未安装技能、未修改旧记录。当前候选 DSH 0.2.0-rc.2 已安装开发包运行通过，Host 与临时 Registry 均已停止。
- [结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-keyboard-result.json)：初始焦点、背景隔离、键盘 Tab、关闭返回均通过；移动抽屉外宽 390px，内容宽与滚动宽均 388px；安装请求 0、pageerror 0。[截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-detail-mobile.png) 已查看。
- 插件 typecheck/lint、git diff --check 通过。本轮生产代码未改动，未覆盖安装进行中关闭、完整键盘循环、屏幕阅读器、深色抽屉或其他平台；抽屉原生表单控件的视觉一致性仍需优化，不关闭完整对话验收任务。

## 安装中关闭抽屉与重新打开（2026-10-14）

- 测试 Registry 增加仅用于集成夹具的 `DSH_FIXTURE_DOWNLOAD_DELAY_MS`（整数 0–30000）；浏览器探针增加 `DSH_CLOSE_DURING_INSTALL=1` 分支，等待明确的安装进行中状态，关闭抽屉、验证返回焦点、重开并再次观察进行中状态，再等待完成和刷新浏览器核验。
- 在隔离临时项目创建新搜索会话，通过真实 DSH 工具执行搜索，使用 15 秒下载延迟。实机验证重开前后同一任务继续，刷新后恢复完成状态，整个流程仅一次 startInstall、无 pageerror。未调用模型或触碰用户真实项目。
- [浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-close-install-result.json)、[重开进行中截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-install-reopened-pending.png)、[刷新后截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-install-reloaded.png)。已查看进行中截图。
- [落盘核验](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-close-install-files.json)：该新目标仅匹配一条持久操作记录，状态 completed，SKILL.md、references/note.md 与安装元数据存在，并记录文件哈希。当前验证不等于模型实际使用技能或发现状态已确认。
- 插件 typecheck/lint、git diff --check 通过。Host/Registry 已停止，保留隔离目录作为证据；本轮未改生产代码，未覆盖安装中关闭整个浏览器、Host 强制退出或跨平台场景。

## 对话抽屉控件视觉统一（2026-10-14）

- 从已安装页面提取 `NativeControlStyles`，以 skillhub-controls 限定作用域；已安装列表、详情和对话抽屉共享按钮、输入、选择器的字体、边框、圆角、禁用及焦点状态。抽屉显式继承宿主字体，增加统一行高和长内容换行。不新增生产依赖，不改变请求或事务逻辑。
- 完整 `bun run check` 通过：build/typecheck/lint、135 tests、929 assertions；新 tarball 安装到隔离 Profile 后实机验证。最初误从仓库根目录调用插件 check 未执行，随后在 plugins/dsh 正确执行通过。
- [抽屉结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-styles-result.json)：390px 下内容宽和滚动宽均 388px；实际 color-scheme:dark，按钮与抽屉字体一致、文字均为白色、背景 rgb(18,18,18)，关闭按钮高 43.59px；键盘/焦点检查通过、零安装请求、零 pageerror。[浅色截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-styled-mobile.png)、[深色截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-conversation-styled-dark-mobile.png)，深色图已查看。
- [已安装页回归](../../../docs/research/dsh-native-plugin-2026-10-12/m2-shared-controls-installed-result.json)：过滤、返回焦点和滚动、详情键盘、窄屏和深色控件通过，零写请求/错误。未重跑未改变的升级卸载确认业务。两轮隔离 Host/Registry 均已停止。
- 本轮只验证 Chromium/Linux 上的相应控件，不代表全部页面设计、屏幕阅读器和跨平台验收完成。

## 版本与评论真实分页及窄屏修正（2026-10-15）

- Registry 集成夹具增加 `DSH_FIXTURE_PAGINATION=1`，生成 21 条版本/评论并按 page/size 真实切片；新增 `module-pagination-probe.mjs`。不改变生产 Registry 契约或首版目录前 20 项的展示范围。
- 首次探针在窄屏折叠侧栏无法打开入口，改为先打开插件再缩小窗口。随后验证两模块实际 Remote 请求各为 0→1→0，第二页为第 21 条，首尾翻页按钮禁用正确，无安装写请求和 pageerror。
- 初次截图发现“上一页”文字被挤成两行；分页栏改为允许整体换行，按钮标签不折行/不压缩，并给当前页信息增加 status。新 tarball 实机复验通过，增加 Range 测量验证两个按钮文字均只有一行；390px 视口中广场内容宽/滚动宽均 334px。
- [最终结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-module-pagination-fixed-result.json)、[修正后评论第二页](../../../docs/research/dsh-native-plugin-2026-10-12/m2-reviews-pagination-fixed-mobile.png)，截图已查看。完整插件 check 通过（135 tests、929 assertions，build/typecheck/lint）；git diff --check 通过。隔离 Host/Registry 已停止。
- 未覆盖翻页中的响应乱序、失败重试、数据总数变化或屏幕阅读器实际播报；本轮不关闭完整模块验收。

## 第二页网络失败与显式重试（2026-10-15）

- 分页实机探针增加 `DSH_PROBE_FAILURE_RETRY=1`，分别阻断版本/评论首次第二页 Remote 请求。断网错误展示重试入口，不显示“暂无信息”；等待 1.2 秒期间没有额外请求，点击重试后仍请求第二页，再返回第一页。
- 当前开发包在隔离 DSH 0.2.0-rc.2 验证通过，两模块请求序列均为 0→1（失败）→1（手动重试）→0；读取正确第二页内容，按钮状态和 390px 布局通过，写请求与 pageerror 均 0。[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m2-module-pagination-retry-result.json)、[评论第二页失败截图](../../../docs/research/dsh-native-plugin-2026-10-12/m2-reviews-page2-failed.png)。
- 插件 typecheck/lint、git diff --check 通过；生产代码未改变，未重复完整业务测试。隔离 Host/Registry 已停止。本测试模拟浏览器到 Host 的网络失败，不替代 Registry 业务错误、乱序响应或真实网络环境验收。

## 卸载后同版本重新安装修复（2026-10-15）

- 修复已接受安装回执长期占据同一技能/版本/范围请求槽，卸载后无法通过原详情重新安装的问题。InstallControl 在原操作完成后读取本地清单，区分原结果与当前状态；完整确认无记录才显示明确的重新安装按钮，不自动写入。InstallationRequests 校验原 operationId 后保存新 key，保留 Host 历史日志，沿用并发去重和响应丢失原 key 重试。
- 增加单元测试覆盖新旧 key、当前引用、重复点击、旧回执竞争、持久化重载及新请求响应丢失。完整插件 check 通过：137 tests、939 assertions，build/typecheck/lint 通过。新增浏览器探针 lint 通过，git diff --check 通过。
- 新 tarball 在隔离 DSH 0.2.0-rc.2 实测安装→卸载→重新打开详情→明确重新安装→浏览器刷新。首次探针错误读取 wire args，修正为 args.input 后在新临时项目重跑通过；两次 install 使用不同 key，卸载 1 次，刷新没有第三次安装，无 pageerror。[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-result.json)、[重新安装入口](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-ready.png)、[完成](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-completed.png)。
- [文件和日志核验](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-files-result.json)：新目标对应两次 install、一次 uninstall，三条历史记录均保留且 completed；SKILL.md、资源和元数据存在。Host/Registry 已停止。
- 本轮覆盖页面安装控件闭环；对话详情复用该控件，但未单独实测其卸载后重装；历史安装 ToolView 当前状态展示、清单异常分支及跨平台仍待继续验证。


## 历史安装卡片当前状态（2026-10-15）

- InstallToolView 将原操作回执和当前会话可见安装记录分开，按项目/用户范围显示当前版本及文件状态；无记录仅在完整读取后显示，失败或不完整明确提示。使用原来源修订读取，不静默切换来源；回执匹配增加来源身份、修订与目标标签检查。历史卡片不新增写入口。
- 完整插件 check 通过：137 tests、939 assertions，build/typecheck/lint 通过；新增集成探针 lint 通过。新 tarball 在隔离 DSH 0.2.0-rc.2 验证工具安装→查看卡片当前记录→页面卸载→返回历史→浏览器刷新，两次独立项目重复通过，浏览器新增安装请求 0、卸载 1、pageerror 0。
- [浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-install-history-current-result.json)、[当前记录可见截图](../../../docs/research/dsh-native-plugin-2026-10-12/m3-install-history-current-visible.png)。首轮截图被宿主工具区域滚动裁切，探针增加 scrollIntoViewIfNeeded 后第二轮留图。
- [文件和日志结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-install-history-files-result.json)：目标目录已不存在，原 install 和 uninstall 操作均 completed 且日志保留。Host/Registry 已停止。
- 未完成升级后历史卡片、来源变更/部分读取、跨平台及模型驱动的真实对话验收，不关闭完整历史状态任务。


## 重新安装入口的读取失败保护（2026-10-15）

- reinstall-probe 增加 DSH_PROBE_FAILURE_RETRY=1：卸载后将当前清单响应设为 partial，再阻断一次浏览器到 Host 的读取。两种情况均显示无法完整核验，不显示无记录结论或重新安装入口，安装提交数保持 1；恢复真实读取并明确点击后才提交第二次安装。
- 当前开发包在隔离 DSH 0.2.0-rc.2 实测通过，保留安装→卸载→重新安装→刷新检查，2 次安装使用不同 key、1 次卸载、刷新无新增提交、pageerror 0。[结果](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-read-failures-result.json)、[部分读取](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-partial-blocked.png)、[请求失败](../../../docs/research/dsh-native-plugin-2026-10-12/m3-reinstall-error-blocked.png)。
- 插件 typecheck/lint、git diff --check 通过。生产代码未改动，未重复完整业务测试；Host/Registry 已停止。本次是客户端故障注入，不证明 Host 在实际目录权限异常下生成相同部分结果，后者由投影及文件系统测试单独覆盖。


## 真实开源 Registry 只读契约与原生安装（2026-10-15）

- 发现本机运行中的独立开源预览实例，以匿名 GET 检查公开技能 global/pr952-ui-demo@1.0.0；未改该实例配置、账号、技能或容器生命周期。服务端为既有 sha-5a96590 镜像，不是本工作树部署，不作为 SaaS 证据。
- `probe:registry` 对实际 HTTP 服务验证搜索、精确版本 resolve、元信息、SKILL.md、版本、评论、下载归档及指纹全部通过；安全审核匿名请求 HTTP 401。[契约结果](../../../docs/research/dsh-native-plugin-2026-10-12/registry-local-preview-contract-result.json)。任务 0.4 据此完成，其范围不扩展为全产品验收。
- 通过启动时临时 overlay 将隔离 DSH 的插件连接到该实例，新建临时项目和会话。首次空会话未在侧栏显示，夹具增加明确标记的本地测试消息并 flush 后重跑；未调用模型。浏览器搜索、精确版本详情、版本和评论 Tab、项目级安装通过，安装请求 1 次，pageerror 0。[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/registry-local-preview-install-result.json)、[截图](../../../docs/research/dsh-native-plugin-2026-10-12/registry-local-preview-installed.png)。
- [文件与日志核验](../../../docs/research/dsh-native-plugin-2026-10-12/registry-local-preview-install-files.json)：单条 completed 操作，落盘 SKILL.md 存在，按实际文件重算的指纹与真实 Registry resolve.fingerprint 一致。该公开技能仅包含 SKILL.md，不能代替带资源技能的真实实例使用验收。
- 新增通用 live-registry-install-probe.mjs；插件 typecheck/lint、git diff --check 通过。生产代码未改，未重复业务全量测试。仅停止本轮隔离 DSH，既有预览服务保持运行；当前工作树后端部署、SaaS、带资源技能和模型实际使用仍未验证。


## 真实 Registry 带资源技能安装与发现（2026-10-15）

- 继续使用既有本地开源预览实例，匿名检查公开 global/zero-slop@2.10.2 包：69,822 字节、9 个文件，包含 SKILL.md、references、data、scripts 与许可证。仅检查/安装文件，没有执行下载内容中的指令或脚本。
- 在新的临时项目通过当前原生 DSH 插件搜索、查看版本/评论并安装，通过既有 live-registry-install-probe 实测：1 次安装、无 pageerror。[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/registry-resource-install-result.json)、[安装截图](../../../docs/research/dsh-native-plugin-2026-10-12/registry-resource-installed.png)。
- Host 临时观察器在实际会话 scope 中调用 ctx.skills.get，确认 DSH 发现 zero-slop，resourceBase.kind 为 directory，路径为该项目安装目录；通过该资源根读取 references/tells.md，22,567 字节。[发现与资源结果](../../../docs/research/dsh-native-plugin-2026-10-12/registry-resource-discovery-result.json)。
- 重新计算全部 9 个安装文件（排除安装器元数据）的聚合指纹，与真实 Registry resolve.fingerprint 一致；单条安装日志 completed。[文件结果](../../../docs/research/dsh-native-plugin-2026-10-12/registry-resource-install-files-result.json)。隔离 DSH 已停止，原 Registry 服务未修改或停止。
- 本轮未改生产代码，git diff --check 通过。证据证明真实服务的完整包安装、DSH 发现及资源可读，不证明模型实际选择/使用技能；后端仍为既有预览构建，当前工作树部署、SaaS 与跨平台未覆盖，任务 5.3 不整体勾选。


## 交付缺口核对（2026-10-15）

- 对照 tasks.md、acceptance.md、当前工具/引用/更新实现与测试及已保存 JSON 结果，更新 4.1、7.2、7.3 状态。其余宽泛任务不因已有部分截图或测试通过而批量勾选。acceptance.md 增加 A01–A16 现有证据与出口缺口表，作为执行状态而非第二套规格。
- 4.1：核对 CLI update-assessment 测试及 Host update-check、当前已安装行显式检查入口；m3-update-matrix-result.json 覆盖新版/旧版/同版本变内容/未知顺序，零自动安装。完整升级仍独立验收。
- 7.2：核对 ConversationSelections、open/ack 实机固定引用结果、conversation-install-host 的固定 detail 传递和拒绝后不回退；测试覆盖未展示、旧确认、同版本内容歧义、会话/项目/来源漂移及淘汰。早期记录所述安装适配未接入现已补齐，但完整对话产品验收仍保留。
- 7.3：核对 install-tool 仅输出 accepted/not-confirmed，接受回执不含终态；原来源/主机/创建时间来自持久操作。结果卡片通过共享 getOperation 读取终态；实机历史重载保持操作 ID，后续卸载后当前状态单独显示。它不代表全部取消/并发场景已通过。
- 本轮定向测试 conversation-selections、conversation-install-host、install-tool、update-check：11 tests、71 assertions，通过。无生产代码变更，未重复全量构建；git diff --check 通过。


### 宿主运行时解析基线探测

新增 `plugins/dsh/test/integration/runtime-baseline-probe.mjs`，在隔离 Profile 的实际 DSH Host 中读取 `pluginPackages.packageOf(specifier, pluginEntryURL)`，以及 `clientModules.clientPath(packageName)` 所属清单。结果见 `docs/research/dsh-native-plugin-2026-10-12/runtime-baseline-resolution-result.json`。

- Node 为 24.17.0；DSH、app-boot 和九项 Host 依赖的运行时解析版本符合当前候选版本（Cordis 4.0.4，其余 0.2.0-rc.2）。
- 七项声明依赖的 Client 模块均已加入当前宿主 Client 图，提供文件所属包版本为 0.2.0-rc.2；SkillHub Client 为 0.0.0-m0。
- 探针须复制到插件包目录外再作为临时 Loader 行加载。直接加载包内第二个探针会被 client-modules 识别为同一包的多个活动来源并拒绝启动；本次修正后运行成功。
- `passed` 仅表示采集成功。本结果不证明浏览器实际执行、混装阻断或跨平台兼容。
- 生产写入检查仍只核对九项 Host peer 清单。主程序、Client 图和 Node 检查尚未接入；0.1、10.1、10.3、10.5 保持未完成。包元数据存在缓存，后续须验证重载判定，不能把读取缓存当成重新加载证据。

本轮未修改正式安装行为。探针 lint 通过；隔离宿主已停止。


### 运行时基线接入写入守卫

`runtime-compatibility.ts` 已接入 `getCompatibility` 和 LocalRuntime 写入/恢复准备检查，覆盖实际解析的 DSH、app-boot、九项 Host peers，以及七项 Client 注入模块的活动路径和所属清单。缺失服务、模块未挂载或精确版本不符时拒绝写入；原请求查重顺序不变。此记录更新前一节“尚未接入”的实现状态。

验证：`cd plugins/dsh && bun run check` 通过，141 tests / 1755 assertions；包含逐包缺失/混装、Client 清单异常及解析表变化测试。编译后 Host 恢复测试增加缺少宿主服务时拒绝写入断言，提供显式夹具服务后继续验证原目标归属与策略行为。

新 tarball 安装到隔离 Profile 后，实际 DSH 的 getCompatibility 返回 matched / localWritesAllowed=true，结果见 `docs/research/dsh-native-plugin-2026-10-12/runtime-baseline-guard-result.json`。宿主已停止。未运行真实混装或浏览器写流程，本轮单测中的解析表变化不等于实机依赖重载。Node 范围尚未固定，完整兼容验收保持未完成。

### 官方 Client 实包混装、缺失及重启恢复

隔离 DSH 0.2.0-rc.2 中使用 npm 官方 `@deepseek-ai/dsh-client-ui-layout@0.2.0-rc.1` 原始 tarball 进行混装测试，校验下载归档与 npm pack integrity 一致。通过启动 overlay 禁用原 `ui-layout`、插入不同 ID 的解包入口，不修改共享安装目录。实际 Client 图确认加载 rc.1，其余候选 Host/Client 仍为 rc.2。

| 场景 | 实际结果 |
| --- | --- |
| rc.2 Host + rc.1 layout Client | getCompatibility 为 unverified，新安装在准备前拒绝，未新增操作 JSON |
| 禁用 layout Client | Client 图缺少 layout，同样拒绝新安装，未新增操作 JSON |
| 恢复原启动配置并重启 | layout 回到 rc.2，getCompatibility 为 matched |

证据：研究目录中的 `runtime-baseline-mixed-client-result.json`、`runtime-baseline-missing-client-result.json`、`runtime-baseline-restored-client-result.json` 和 `runtime-baseline-mixed-package.json`。探针为 `plugins/dsh/test/integration/runtime-baseline-probe.mjs`，运行说明见 integration/README.md。`bun run lint` 和 `git diff --check` 通过，所有本轮启动的隔离宿主均已停止。

首次试验发现 patch 的 name 只校验原行名称，无法替换模块；该次实际仍加载 rc.2，未作为混装成功证据。上述表格来自修正后的实包加载结果。

本轮证明 Host 内部实际写入口拒绝；没有调用浏览器 Remote 或运行安装下载，也未验证同进程热重载、Host 依赖实包混装或 Node 范围。10.5 的完整验收保持未完成。

### 同进程 Client 禁用与重新启用

通过官方 Loader Entry.update 在同一隔离 DSH 进程中禁用、重新启用 `@deepseek-ai/dsh-client-ui-layout`。探针逐阶段读取实际 Client 图，并在 finally 恢复原 disabled 值。

- 初始：Client 活跃、基线 matched。
- 禁用：Client 图移除 layout，基线 unverified；实际 LocalRuntime 新安装入口在准备回调前拒绝，操作 JSON 集合未增加。
- 恢复：Client 图重新加入 layout，基线 matched。全程没有重启宿主。

证据：`docs/research/dsh-native-plugin-2026-10-12/runtime-baseline-client-reload-result.json`。探针及用法维护在 integration/runtime-baseline-probe.mjs 和 integration/README.md；lint 与 diff 检查通过。此次只改验证探针与文档，未改变生产代码。隔离宿主已停止。

这项验证补齐 Client 模块生命周期检查，但不证明 Host 包替换后的解析缓存刷新、插件本身重载后的订阅清理、浏览器 HMR 或 Node 支持范围；相关完整任务仍未勾选。

### Client 清单真实文件读取回归

`test/runtime-compatibility.test.ts` 增加临时目录中的真实清单读取测试：有效清单通过，损坏 JSON、错误所属包名、缺失清单均拒绝写入，修复后重新通过。该用例调用默认文件读取实现，不替换 readClientManifest。定向测试 5 项通过，typecheck、lint、diff 检查通过。

Node 最低版本验证仍在进行，尚无 Node 24.0.0 运行结果；本轮不变更 engines 或支持范围。已验证的实际 DSH 宿主 Node 版本仍为 24.17.0。

### Node 下界实测与运行约束

隔离下载 Node 24.0.0、24.2.0 二进制，按官方 npm 元数据 SHA-512 校验镜像取得的归档，没有替换系统 Node。

- 24.0.0：Bun 测试套件中的 Node 子进程切到该版本后，142 tests / 1815 assertions 通过；但实际 DSH CLI 以退出码 0 静默结束，未启动 Host。源码入口使用 `if (import.meta.main) await runCli()`，该版本字段为 undefined。这是宿主启动不兼容，不能将测试套件通过视为支持。
- 24.2.0：实际隔离 DSH 启动、Host/Client 基线采集通过。Node 24.17.0 的既有宿主证据仍有效。
- 插件新增 `engines.node: >=24.2.0 <25`，Host 守卫同步拒绝旧版、其他主版本及非稳定版本字符串。三平台 CI 增加 24.2.0 下界与当前 24 双版本，制品名包含 Node 版本以避免覆盖。
- 修改后 `bun run check` 通过（143 tests / 1831 assertions）；将 Node 子进程切到 24.2.0 再跑插件测试，同样 143 项通过。新 tarball 装入隔离 Profile 后，在 Node 24.2.0 下返回 matched / localWritesAllowed=true。所有本轮隔离宿主已停止。

证据：研究目录 `node24-minimum-probe-result.json`、`node24-2-runtime-result.json`、`node24-2-guard-runtime-result.json`。Node API 加入版本见 [官方 ESM 文档](https://nodejs.org/api/esm.html#importmetamain)。测试由 Bun 驱动，PATH 切换影响测试中显式启动的 Node 子进程，不能称为整套 Bun 测试运行于 Node。

Node 范围现已明确为候选约束；远端六组合 CI 尚未执行，macOS/Windows 与最低 Node 下的完整浏览器用户流程尚未验收，不据此勾选全部兼容任务。

### Node 24.2.0 页面安装生命周期与控件样式

使用带 Node 守卫的新 tarball，在 Node 24.2.0 的隔离 DSH、Chromium 和本地 Registry 夹具中执行：广场查看详情 → 项目级安装 → 已安装详情 → 确认卸载 → 返回技能详情 → 明确重新安装 → 页面刷新。

- 两次安装使用不同 requestKey，一次卸载；刷新只查询，不重复提交。浏览器无 pageerror。
- Host 操作日志为两次 install、一次 uninstall，全部 completed。
- 最终 SKILL.md 和 references/note.md 存在，重新计算的指纹与 metadata、inventory 一致，版本为 1.2.0。
- 广场容器接入已有 NativeControlStyles，安装按钮、选择框和详情 Tab 继承宿主字体及焦点样式。浏览器断言核验按钮高度 43.59px，字体与容器一致，截图已人工查看。
- `bun run check` 通过：143 tests / 1831 assertions；扩展浏览器探针后 lint 通过，diff 检查通过。

证据位于研究目录：`node24-2-browser-reinstall-result.json`、`node24-2-browser-lifecycle-files-result.json`、`node24-2-browser-reinstall-completed.png`。首个夹具因新项目目录未建立而失败，建立新的隔离目录后重跑成功；未改动生产逻辑以绕过目录验证。

上述为真实 DSH 页面与本地 Registry 夹具验证，不是生产 Registry 或模型实际调用技能验收；最低 Node 下的升级、对话安装、窄屏和其他平台仍需覆盖。所有本轮启动的宿主与夹具服务已停止。

### Node 24.2.0 单项目正常升级

新增 `integration/upgrade-success-probe.mjs`，在独立来源和新项目中完成真实 DSH 页面流程：安装 1.2.0 → 检查更新 → 生成仅含当前项目的升级计划 → 确认 → 1.3.0 → 查看升级后详情 → 刷新。生成计划未调用 executePlan，总计一次安装、一次升级，刷新不重新提交，浏览器无 pageerror。

磁盘独立核验通过：资源文件内容为新版，重新计算的指纹与 metadata/inventory 一致，唯一安装目标版本为 1.3.0，install/upgrade 日志均 completed，无备份或暂存目录残留。宿主运行于 Node 24.2.0；夹具与浏览器驱动运行于开发机 Node。lint、diff 检查通过，宿主和夹具均已停止。

证据：研究目录 `node24-2-upgrade-success-result.json`、`node24-2-upgrade-files-result.json`、`node24-2-upgrade-plan.png` 和 `node24-2-upgrade-completed.png`。完成截图记录了本地版本及历史升级结果，远端详情仍处于读取状态，不能作为详情内容加载完成的证据。本轮没有验证模型调用、最低 Node 双目标升级或故障恢复。

### Node 24.2.0 用户/项目双目标正常升级

在全新 DSH Home、OS Home、Profile、项目及安装清单中，使用官方插件命令安装候选 tarball。升级探针先安装用户级 1.2.0，再安装项目级 1.2.0；检查更新并生成计划，断言完整目标集合恰好是 user/project，一次确认升级到 1.3.0。刷新后仍为新版，没有重复提交；本轮等待远端详情 Tab 加载完成后截图。

磁盘核验：两个目标的新版资源内容一致，指纹均匹配各自 metadata 和共享 inventory，清单只有这两个目标且版本为 1.3.0；两次 install 和一次 upgrade 日志均 completed，两处无 staging/backup 残留。浏览器无 pageerror。探针 lint、diff 检查通过，隔离宿主和 Registry 夹具已停止。

证据位于研究目录：`node24-2-dual-upgrade-success-result.json`、`node24-2-dual-upgrade-files-result.json`、`node24-2-dual-upgrade-plan.png`、`node24-2-dual-upgrade-completed.png`。初次复制 Profile 的 tarball 路径错误导致插件未加载；修正路径并在新 Profile 正式安装后才取得以上结果，未将启动页面可达视为插件可用。

此项证明双目标正常路径，未覆盖双目标升级中断后重启恢复、CLI 并发或模型调用。相关完整验收任务继续保留未完成状态。

### Node 24.2.0 双目标升级中断后的真实重启恢复

在新的隔离 Home/Profile 中由真实安装工具建立项目级与用户级 1.2.0。测试专用 authorize 包装在第一目标完成替换、第二目标备份后拒绝继续，并拒绝自动回滚。操作实际进入 recovery-required，两个目标中一处存在、一处已隔离。协调器保存升级前全部文件哈希和 inventory 字节，确认旧 Host 进程退出后，在相同持久目录和端口启动不同 PID 的新 Host。

浏览器从原升级历史发起一次恢复；恢复接口成功后模拟响应丢失，页面通过原操作查询仍正确显示已恢复旧版本，两项安装记录均为 1.2.0，关闭结果后的焦点正确。两处全部文件哈希与升级前完全一致，inventory 字节不变。总计一次升级提交和一次恢复请求，无 pageerror。操作最终为 failed/recovery=restored，未误报升级成功。所有测试子进程均已退出。

证据：研究目录 `node24-2-dual-upgrade-restart-files-result.json`、`node24-2-dual-upgrade-restart-browser-result.json`、`node24-2-dual-upgrade-restart-restored.png`、`node24-2-dual-recovery-discovery-result.json`。截图已查看。协调流程整理为 `plugins/dsh/test/integration/dual-upgrade-restart.py`，参数化后通过 Python 编译与 --help 检查；本次实机运行使用其整理前的同逻辑临时脚本。复用浏览器探针、故障注入和产品代码均未改动。

本结果证明事务错误后跨进程恢复，不等于执行中 SIGKILL 的所有崩溃边界，也不覆盖跨平台或模型实际使用；完整故障矩阵仍需逐项收口。

### SIGKILL 双目标升级：复现并修复重启后无恢复入口

在第二目标已备份、尚未替换时暂停测试事务并 SIGKILL 隔离 Host。首次实测复现缺陷：日志仍为 revalidating/not-needed，新 Host 原样返回该状态，浏览器持续显示升级中，30 秒内没有“核验并恢复”入口。失败证据见研究目录 `dual-upgrade-crash-regression-before.json`。

修复位于 `plugins/dsh/src/host/local-runtime.ts`：启动扫描保存待核验操作 ID；查询这些操作时，将未结束的执行阶段投影为 unknown/files=unknown/recovery=unknown。持久日志保持原状，不自动执行或判断成功；当前实例新操作、原操作终态不改变。恢复仍通过原执行锁、目标授权与事务核验。新增单测覆盖所有未完成阶段、当前新操作、调用归属、完成/恢复终态；编译 Host 测试确认投影不修改原日志。

修复后 `bun run check` 通过（144 tests / 1849 assertions）。使用新 tarball、Node 24.2.0 和仓库内 `dual-upgrade-restart.py --crash` 完整复测通过：旧 PID 退出码 -9，新 PID 同端口启动，等待心跳锁失效而不删除锁；浏览器一次恢复、恢复响应丢失后查询成功；两个目标全部文件哈希与升级前完全一致，inventory 字节不变，操作最终 failed/restored。无 pageerror，截图已查看，所有测试进程停止。

证据：`node24-2-dual-upgrade-sigkill-files-result.json`、`node24-2-dual-upgrade-sigkill-browser-result.json`、`node24-2-dual-upgrade-sigkill-restored.png`。Python 协调器编译检查与 diff 检查通过。此结果覆盖第二目标备份后的强制终止边界，其他崩溃阶段及跨平台验证仍按矩阵继续核对。

### 全新浏览器中的对话历史与进行中安装

Node 24.2.0 隔离 Host、新 tarball 下完成两组真实 Chromium 关闭/重新启动验证：

1. 安装工具原操作完成、页面卸载后关闭浏览器。新浏览器确认 SkillHub sessionStorage 为空，打开同一持久会话，卡片仍显示原安装成功，同时核验当前已卸载；无安装请求，卸载只提交一次。
2. Registry 下载延迟 30 秒，实际工具返回 accepted 后立即持久化并 flush 会话。浏览器看到安装进行中后关闭；新浏览器无旧存储，仍查询同一个 operationId，最终显示完成和项目安装记录。两个浏览器均未提交写接口，Host 只有一个对应完成日志，资源内容可读且指纹匹配。

无 pageerror。探针 lint、diff 检查通过，宿主及夹具已停止。证据位于研究目录：`browser-restart-history-current-result.json`、`pending-install-browser-restart-result.json`、`pending-install-browser-restart-files-result.json` 及同前缀截图。场景使用实际工具执行和合成持久会话，没有调用模型。

A12 浏览器进程关闭这一项已取得证据；页面升级/卸载的 sessionStorage 丢失恢复不在此次证明范围，不能据此扩展声明。矩阵同步更新双目标重启、SIGKILL 和 Node 基线已验证项，整组验收仍保留其他缺口。

### 可选模块配置与 HTTP 状态请求矩阵

`plugins/dsh/test/detail-modules.test.ts` 新增 12 组组合，使用编译后的插件 Host 服务和本地 HTTP 夹具覆盖 auto/show/hide × 200/403/404/503。版本和评论分别核验：auto 只请求 size=1 的能力探测；show 在读取详情时不请求模块正文；hide 不探测，直接调用正文接口也在 HTTP 请求前拒绝。显式读取使用 size=20，服务端拒绝或错误保留对应 HTTP 状态，不转换为空内容。审核信息保持匿名不可读且不发审核请求。

每组随后将配置切为 hide，验证先前签发的详情引用不能继续读取版本或评论，并以夹具请求总数不变证明拦截发生在网络访问之前。未改变产品代码或服务端访问政策。

验证：定向测试 17 pass / 143 assertions；重新构建后的 `bun run check` 全部通过（156 tests / 1957 assertions，包含 build、typecheck、lint）；`git diff --check` 通过。本结果为 Host 与 HTTP 边界证据，不替代浏览器 Tab 可见性、配置保存及切换交互验收，A07 保留对应缺口。

### 原生配置保存、刷新与三种 Tab 模式

Node 24.2.0 隔离 DSH Host 与 Chromium 中运行 `tab-config-probe.mjs`。分别在官方插件详情内的 SkillHub 配置表单保存 hide/show/auto，每次刷新浏览器后重新读取三个配置值，再进入广场详情。hide 隐藏全部可选 Tab 且零模块请求；show 展示全部可选 Tab，打开详情时零模块请求，点击版本/评论后分别请求 size=20；auto 发出两个 size=1 探测，只显示可读版本/评论，激活后请求正文。show 的审核入口展示匿名不可读提示，全部模式均未请求审核接口。无 pageerror，未执行安装写入。

结果与截图：研究目录 `native-tab-config-result.json`、`native-tabs-hide.png`、`native-tabs-show.png`、`native-tabs-auto.png`。已查看 show 截图。前两次因测试标签定位和刷新后宿主配置引导遮罩失败，修正探针后重新完整运行通过，未修改产品代码。探针及请求日志夹具 lint 通过；所有测试子进程已退出。

本轮证明浏览器刷新后配置保留，不等于 Host 重启持久化，也未覆盖无刷新配置切换及浏览器错误状态交叉矩阵；A07 保留这些缺口。

### 同一浏览器会话内即时切换详情 Tab 配置

扩展 `tab-config-probe.mjs` 的 `DSH_PROBE_LIVE_CONFIG=1` 路径，在隔离 DSH Host 中以 show→hide→auto 顺序保存配置，全程不刷新浏览器。show 时打开版本/评论正文及审核拒绝提示；hide 后直接返回广场详情，三个可选 Tab 均消失且无可选模块请求；auto 后返回详情，两个 size=1 探测成功，版本/评论重新可见并按点击读取 size=20 正文，审核保持隐藏。浏览器无 pageerror。

结果及截图：研究目录 `live-tab-config-result.json`、`live-tabs-hide.png`、`live-tabs-show.png`、`live-tabs-auto.png`。所有测试进程正常退出，探针 lint 与 diff 检查通过。本轮仅扩展验证脚本，未改产品代码；不宣称 Host 重启后配置持久化或浏览器错误交叉矩阵已通过。

### 实际 Host 退出重启后的配置持久化

仓库内 `config-host-restart.py` 在 Node 24.2.0、隔离 Profile 中运行通过。第一阶段由原生配置表单保存版本 hide、审核 show、评论 auto；关闭第一浏览器，终止并等待旧 Host 退出后启动不同 PID 的 Host。第二阶段使用全新 Chromium（无 SkillHub sessionStorage），只读取配置，不重新保存，三个选项与先前保存值一致。

进入广场后版本 Tab 不存在且零版本请求；审核 Tab 显示匿名不可读且无审核请求；评论 auto 恰有一个 size=1 探测，点击后一个 size=20 正文请求。两阶段无 pageerror，已查看重启后截图。协调器与浏览器均正常退出，只清理自身子进程。

证据：研究目录 `native-config-restart-result.json`、`native-config-save.png`、`native-config-verify.png`。探针 lint、Python 源码编译检查和 diff 检查通过。A07 的 Host 重启持久化缺口已补齐；浏览器错误状态交叉矩阵仍待验收。此场景未验证插件跨版本升级或回退配置迁移。

### 浏览器可选模块 HTTP 错误与配置交叉矩阵

扩展可选模块夹具及 `tab-config-probe.mjs`，分别启动独立 Host 进程运行 HTTP 403、404、503，每个状态在同一浏览器依次保存 show/hide/auto。共 9 个组合全部通过：hide 无可选请求；show 打开详情不探测，点击版本/评论后显示对应拒绝、未找到或暂不可用提示；auto 各发一个 size=1 探测，隐藏不可读入口，404/503 保留“尚未确认，不代表没有内容”提示；正文未返回时不显示“暂无评论”。安全审核均未绕过匿名限制。

全部运行无 pageerror，所有子进程已停止，lint 与 diff 检查通过。已查看 503/auto 截图。证据：研究目录 `http-{403,404,503}-tab-config-result.json` 与对应 `tabs-show.png`、`tabs-auto.png`。这些测试使用实际 Host/Client 和本地 HTTP 夹具，不证明 SaaS 实例兼容。规范还包含成功空集合的 UI 场景，已有 Host 层测试，但浏览器可见性与空态证据尚需补齐，A07 保留该出口项。

### 成功空集合与评论范围说明

`catalog-fixture.mjs` 增加空集合模式；真实 Node 24.2.0 Host 和 Chromium 中三模式验证通过：show/auto 均保留版本与评论 Tab，点击后显示“暂无版本信息。”或“暂无评论信息。”，上一页/下一页禁用；auto 仍只做 size=1 探测，正文按点击读取，hide 无请求。空内容未被误判为能力不支持。

逐项核对规范时发现评论视图缺少技能级范围说明。在共享 `DetailTabs` 的评论面板增加“评论针对整个技能，不限定当前版本。”，广场与对话共用，不改变接口或安装版本。`bun run check` 通过（156 tests / 1957 assertions，含构建、typecheck、lint）。重新打 tarball，使用官方插件安装命令更新隔离 Profile 后再次完整验证三模式通过，并断言实际页面出现范围说明；截图已查看。所有测试进程已退出，diff 检查通过。

证据：研究目录 `empty-tab-config-result.json`、`empty-tabs-auto.png`、`empty-tabs-hide.png`、`empty-tabs-show.png`。结合此前成功正文、分页、重试、HTTP 错误矩阵、配置切换与持久化证据，任务 2.3 完成，A07 既定场景取得对应证据。跨平台、SaaS 与整体插件交付仍由各自验收组约束，不能据此宣称整个插件完成。

### 独立 DSH 插件发布工作流

新增 `.github/workflows/release-dsh-plugin.yml`，仅手动运行，默认不发布；必须从版本一致的 dsh-v tag 执行。`release-policy.ts` 拒绝错误 tag/包名/版本格式，正式发布要求显式 private:false；预发行使用 next，正式版本使用 latest。`check-dsh-plugin.yml` 增加 workflow_call，发布流程复用同提交的三平台双 Node 矩阵，通过后选用 Linux/Node 24.2.0 已测 tarball。发布 job 使用 dsh-npm Environment，上传前校验 SHA-512，上传后比较 Registry dist.integrity。没有自动创建 GitHub Release 或部署服务端。

本地验证：actionlint 1.7.7 检查两个工作流通过；发布策略新增 2 个测试，覆盖错误 tag、private、包名、预发行和畸形版本；完整 `bun run check` 158 tests / 1974 assertions 通过，diff 检查通过。Node 24.2.0 子进程实测 dry-run 输出、private 发布阻断和分支阻断，结果为研究目录 `release-policy-node-result.json`。首次使用 import.meta.main 的 TypeScript 入口未实际执行，改为入口 URL 比较后重测通过，避免脚本静默跳过校验。

首次完整测试有一项既有 Registry stalled headers 测试超时；定向 22 项复测通过，随后完整检查通过，未修改该测试超时阈值。工作流没有远端执行，Environment 审批规则与 secret 未配置或核验，也未发布 npm、创建 tag 或改变当前 private 开发版本。发布指南明确这些外部准备项，A14 不标记整体完成。

### 发布候选包身份与命令入口回归

发布工作流的 package job 增加 tarball manifest 核对：确认唯一 tgz，从归档读取 `package/package.json`，使用同一发布策略校验包名、tag/version 和 private 条件，通过后才保存候选校验文件。策略脚本增加显式 manifest 路径参数，默认仍读取源码 manifest。

新增真实 Node 子进程测试，验证命令入口会执行并写入准确输出；分支、错误 tag、错误包版本、private 包和错误包名均拒绝且不会追加成功输出。3 项发布策略测试 / 29 assertions、typecheck、lint 和 actionlint 通过。实际开发 tarball 在 Node 24.2.0 下匹配 tag 的 dry-run 校验通过，错误 tag 被拒绝，未执行 npm publish。diff 检查通过。首次 typecheck 暴露测试中联合对象数组推断问题，改为明确的上下文参数类型后通过，未放宽生产类型检查。

本轮仅修改发布策略、测试和工作流，不影响运行时插件；远端流水线与正式发布仍未执行。

### Registry 停止时的本地详情与精确卸载

在 Node 24.2.0 隔离 Host 和真实 Chromium 中，使用已安装 1.2.0 的专用项目执行 `removal-probe.mjs` 离线路径。测试前后对 Registry 端口连接均明确 ConnectionRefused，未启动 Registry 进程，也未通过浏览器模拟远端失败。页面显示远端详情不可用但保留本地精确版本；首次取消确认不提交，第二次确认仅一次 executePlan，最终卸载完成，关闭结果后列表为零项且焦点恢复正确。

独立磁盘核验：选定项目目录不存在；inventory 与预期仅移除该 target 后的完整对象完全相同；其他三个目标的全部文件哈希不变；对应 operation 为 completed。浏览器无 pageerror，宿主和浏览器进程已停止，lint 与 diff 检查通过。已查看完成截图。

证据：研究目录 `offline-removal-result.json`、`offline-offline-files-result.json`、`offline-removal-confirmation.png`、`offline-removal-recovered.png`。本轮补齐 A09 离线卸载实机证据，权限异常及清理残留等其他组合仍未视为完成。隔离目录中该测试安装已按预期卸载，不影响开发者或其他项目的实际技能。

### 真实文件权限拒绝时卸载不修改原安装

Node 24.2.0 隔离 Host、真实 Chromium 和非 root 用户下，将专用测试项目 `.dsh` 目录临时设为 0555，确认无写权限且不存在隔离目录。Registry 同时保持关闭。通过原生 UI 查看已安装目标并提交一次卸载，隔离目录创建在写入 intent 前失败；页面显示“卸载未完成。无需恢复文件。”，未出现成功提示，关闭结果后该项目仍有一项安装记录。

独立核验：目标全部文件哈希和整个 inventory 均与操作前一致，其他安装目标文件也不变；日志 phase=failed。浏览器无 pageerror。finally 恢复目录原权限，随后检查已恢复可写；所有自建进程已退出。lint 与 diff 检查通过。

证据：研究目录 `permission-removal-result.json`、`permission-permission-files-result.json`、`permission-removal-permission-denied.png`。此项证明实际操作系统权限拒绝时的提交前保护；不能替代提交后权限变化或隔离残留清理验收，A09 保留对应缺口。

### 提交后真实清理权限失败与原操作恢复

在 Node 24.2.0、非 root POSIX 用户和真实 Chromium 中，由独立 Host observer 在卸载 inventory 提交后、隔离目录清理前，将隔离目录改为 0500。实际文件系统删除失败，产品仍记录 completed 和一条清理警告；浏览器同时显示“此安装位置已卸载”及残留核验提示，未误报为卸载失败。

恢复前独立核验：目标路径已不存在，inventory 恰好只移除选定 target，隔离目录的全部文件内容哈希与原目标一致。恢复原目录权限后，浏览器对同一个 operationId 发起一次“核验并恢复”，清理警告消失，仍为 completed；隔离目录被移除，目标没有重建，其他安装文件哈希不变。卸载总计一次提交，无 pageerror。observer 仅在隔离 Host 中通过官方 patch 加载，不进入生产包；测试进程已退出，lint、diff 检查通过。

证据：研究目录 `cleanup-removal-result.json`、`cleanup-cleanup-files-result.json`、`cleanup-removal-cleanup-warning.png`、`cleanup-removal-recovered.png`。结合此前提交前权限拒绝、离线和 Host 重启恢复，任务 4.5 完成；完整崩溃边界及跨平台仍继续在 A10/A16 核验，整体研发未完成。

### 双入口实际并发：发现冲突请求停留在恢复状态

新增 `concurrent-install-fixture.mjs` 和浏览器探针：真实页面提交项目安装后，在操作仍未结束时由同一个实际 Agent 调用官方安装工具；Registry 下载延迟用于制造重叠。首次因空会话未在侧栏显示而失败，加入明确标注的合成持久会话后，第二次成功触发两个真实入口；没有调用模型。

实测未通过：页面操作 completed，但对话操作进入 recovery-required。日志证明后者只有 staging intent，没有 preparation、目标移动或 inventory 提交；独立磁盘检查暂存目录已经不存在。页面目标安装成功且未被覆盖，但对话请求不能直接终结为失败，探针等待终态超时。相关进程已停止，保留隔离日志用于后续修复；未对失败操作自动执行恢复。

定位：`executeNativeInstall` 在 installSkill 已进入后发生任何异常，都按 coreStarted 标记 recovery-required。共享安装服务的 finally 已清理此次暂存目录，但 Native 层没有对“未准备目标移动且暂存已清理”进行终态核验。需复用已有 early-staging 恢复核验，避免把真正残留或已移动目标的失败一起简化为普通失败。证据见研究目录 `concurrent-install-before-result.json`。探针 lint、diff 检查通过；A12 双入口并发保持未完成，不能把“文件未覆盖”当成整项验收通过。

### 修复并发安装失败后的无残留状态收尾

在共享核心新增仅用于执行失败收尾的核验路径：`settleUnpreparedInstallationFailure` 只接受 staging/revalidating 且仅有 staging intent 的日志；复用目标锁、规范路径及暂存缺失检查，在无 preparation/目标移动/清单提交/恢复动作时记录 failed/not-needed。它不删除或回滚任何文件。`executeNativeInstall` 在共享安装器 finally 结束后尝试该核验；任何锁或检查异常、残留、准备或提交证据仍走原 recovery-required 路径，不放宽真正未知状态。

新增回归覆盖验证失败且暂存清理成功、清理被拒绝，以及两个不同 requestKey 同目标真实重叠。后者确认成功请求文件和 inventory 不变、失败请求明确终结、无 pending。测试初稿过早创建 Bun rejects 断言导致协调等待，改为先收集 Promise 拒绝再断言后通过；未修改生产等待或扩大测试超时。

构建新 tarball 并通过官方插件命令安装后，Node 24.2.0 真实 Host/Chromium 复测通过：页面仍处于 staging 时调用同一 Agent 的实际安装工具；两个入口均 accepted，页面最终 completed/installed，对话操作 failed/failed 且 recovery=not-needed。页面只发一次写请求，两个日志无遗留暂存，失败者无目标移动。磁盘资源可读，指纹与 metadata 和唯一目标 inventory 一致。没有调用模型，也未验证对话冲突结果卡片展示。

验证：共享 CLI typecheck、lint、完整测试（673 tests / 2778 assertions）及 build 通过；插件 `bun run check`（159 tests / 1986 assertions）通过；浏览器无 pageerror，隔离进程已退出，diff 检查通过。证据：研究目录 `concurrent-browser-result.json`、`concurrent-install-files-result.json`、`concurrent-install.png`，失败前证据继续保留。A12 更新为实际双入口重叠已验证，模型驱动和冲突卡片展示仍未完成。

### 对话并发冲突卡片与当前安装状态

InstallToolView 对身份匹配且可查询的 completed，或 failed/not-needed、failed/restored 操作独立读取当前安装记录。失败提示保留原操作结论，同时说明请求失败不代表技能未安装；unknown、recovery-required、查询失败及身份不匹配不进入该展示路径。产品规则已同步 conversation-design.md。

构建产物安装到隔离 DSH 0.2.0-rc.2 / Node 24.2.0 Host 后，实际 Chromium 并发场景通过：页面在 staging 时由同一 Agent 执行真实安装工具；页面 completed/installed，对话 failed/failed。刷新合成持久会话后，卡片显示“本次安装未完成。无需恢复文件。”及“当前项目 · 1.2.0 · 文件存在”，不显示“技能文件安装完成”。页面安装只提交一次，无 pageerror。

前几次浏览器尝试停在工具区域定位，未通过验收。探针原先固定 data-turn-process=1，而工具记录在第 2 轮；修正为可用的轮次按钮，并经正常刷新读取持久会话后通过。不据这些失败推断产品会话同步缺陷。会话记录由夹具构造，但工具执行、操作日志、页面请求和卡片渲染均为真实调用；未调用模型。A12 的模型驱动验收继续保留。

产物构建检查记录为 159 tests / 1986 assertions，typecheck/lint/build 通过；本轮只调整探针和文档，重新运行插件 lint、探针语法及 git diff --check 通过。协调进程退出码 0，自建 Host、Registry 和浏览器均已退出。已查看截图，证据为研究目录 concurrent-conflict-card-result.json 与 concurrent-conflict-card.png。此次结果不扩大平台或 SaaS 支持范围。

### 目录真实滚动与导航状态回归

新增 20 项目录夹具模式及 catalog-navigation-probe.mjs。已安装的候选包在 DSH 0.2.0-rc.2 / Node 24.2.0、Chromium 1440×700 中通过：目录真实滚动 1111px；首次进入详情前切换已安装再返回浏览，位置保持；打开固定版本详情后返回，原卡片恢复焦点且位置保持；再次切换 Tab、返回会话再打开 SkillHub，查询词和位置保持；搜索零结果后清空，恢复完整目录并回到顶部。无 pageerror，未执行文件写入或模型调用。

初次探针使用窄屏布局，因宿主侧栏收起、缺少可见会话及入口文本而未进入完整验证；修正为完整侧栏布局后通过。代码阅读曾怀疑 Tab 导航遗漏滚动保存，临时调整未安装到被测 Host；旧产物已通过上述回归，因此撤销该生产改动，不将其声明为缺陷修复。此次仅保留测试夹具、探针及验收证据。

证据：研究目录 catalog-navigation-result.json、catalog-navigation.png。协调进程退出码 0，自建 Host、Registry、浏览器均已停止，已查看截图。A03 的目录滚动证据补齐，已安装筛选与详情跨面板退出仍需验证；不由此推断窄屏全部导航、跨平台或 SaaS 通过。

撤回临时生产改动后的完整插件检查通过：159 tests / 1986 assertions，build、typecheck、lint 全部通过；git diff --check 通过。

### 已安装详情跨面板恢复与上下文隔离

真实 DSH 旧包复现：选择项目范围和关键词、进入已安装详情，再返回会话并重开 SkillHub，无法回到原详情。InstalledSkills 的筛选与选中状态仅在组件内部，面板卸载后丢失。新增 Client 生命周期内的 InstalledNavigation，由 CatalogNavigation 持有，绑定 sessionId/sourceRevision；组件提交后保存筛选、详情、操作显示状态及列表返回位置，重新挂载读取。切换会话或来源修订时创建新状态，不缓存多个历史上下文，不重放写操作。

新 tarball 经官方插件安装命令装入隔离 Profile 后，DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 验证通过：已安装详情跨会话面板往返恢复；返回列表保留 project 范围和 document-helper 关键词；切换浏览再回来保留筛选；选择第二个真实会话后恢复 all/空关键词且无旧详情。无 pageerror，无模型调用或文件管理写请求；本轮未以浏览器验证来源切换，该边界已有单元测试，实机验证仍属 A02。

完整插件 build/typecheck/lint/tests 通过，160 tests / 1995 assertions；新增边界测试覆盖同上下文保留、会话和来源修订变化重置及返回旧上下文不复用旧状态。浏览器探针更新后另跑 lint 通过。已查看截图，自建 Host、Registry、浏览器均退出。证据：研究目录 installed-navigation-result.json、installed-navigation.png。修复前隔离浏览器日志保留，未将旧包超时记为通过。

补充回归：初版导航状态修复使目录首次 Tab 往返的滚动恢复失败（真实浏览器断言 First tab return lost browse scroll）。进一步将浏览滚动在进入已安装时显式保存、切回浏览时恢复，并禁止已安装面板退出覆盖浏览位置；浏览恢复 effect 仅对 browse 生效，避免覆盖已安装自身恢复。最终重新构建并安装同一候选包，已安装导航及目录导航两条实机探针均通过，目录仍精确恢复 1111px。最终证据覆盖前述 installed-navigation-result.json/截图，并新增 catalog-navigation-after-installed-result.json。

最终复测的一次启动因探针过早判断会话不可见而误折叠工作区，未进入用例；增加有界等待会话加载后再展开工作区，复测通过。最终插件检查保持 160 tests / 1995 assertions、build/typecheck/lint 通过，探针调整后 lint 与 diff 检查通过。两次最终协调进程及其子进程均退出。A03 的上述导航路径完成；来源切换、窄屏宿主入口及跨平台仍不在此次证明范围。

### 双 Registry 配置切换与旧对话引用失效

在隔离 DSH 0.2.0-rc.2 / Node 24.2.0 Host 和真实 Chromium 中，启动两个 loopback Registry（43219/43220），用原生插件配置表单保存切换，不修改生产配置。页面探针通过：原来源的项目范围、关键词与已安装详情在换源后清除；第二来源列表没有原来源安装记录；切回原来源，项目安装重新可见。打开中的第二来源目录详情不会静默改成原来源详情。

对话 observer 创建独立临时项目和合成会话，通过真实 ctx.tools.execute 执行搜索并持久化实际工具回执。浏览器先成功打开新引用详情，再分别切换第二来源、切回原来源，同一历史搜索卡片的详情均提示失效，且无安装按钮。两个探针均无 pageerror，结束恢复原来源，协调进程及自建子进程退出。没有模型调用，不把合成会话等同于模型理解安装意图的证明。

本轮新增 Registry 夹具端口参数（校验范围并固定 loopback）、来源页面/对话探针和 observer；未改产品逻辑。lint 初次发现 try 块内函数声明不符合项目规则，调整为局部箭头函数后重新验证。证据：研究目录 source-navigation-result.json、source-navigation.png、source-reference-result.json、source-reference.png，已查看截图。A02 的配置切换及双入口失效取得实机证据，在途请求乱序实机验收仍未完成；不扩展平台、SaaS 或正式版本支持声明。

### 插件 Host entry 同进程禁用与恢复

新增包外 observer plugin-lifecycle-fixture.mjs，通过官方 Loader Entry.update 操作隔离 Profile 中唯一 SkillHub entry；不注入 skillhub，观察器在目标服务释放后继续运行。DSH 0.2.0-rc.2 / Node 24.2.0 下两次独立运行均通过，每次连续两轮：禁用后服务和 astron_skillhub_search/astron_skillhub_install 均不可获取；旧服务对象携带旧 sourceRevision 的读取被 context-stale 拒绝；finally 恢复原 disabled 设置，工具与服务恢复，sourceRevision 更新、实际 Registry 目录可读、依赖基线 matched，未增加重复 Loader entry。没有模型或技能文件写操作。

首次运行报告通用等待超时，缺乏阶段信息，原因尚未确定，不认定为产品缺陷或已修复。加入阶段及服务/工具存在诊断后，两次完整运行通过；保留首次失败与两次成功原始结果，见研究目录 plugin-host-lifecycle-result.json。协调进程及自建子进程已退出，lint 和 diff 检查通过。

观察到 Host entry disabled 时 ctx.clientModules.clientPath 仍返回插件 Client 模块。Host 服务清理不能替代完整 Bundle 关闭、Client 插槽、Remote Client 挂载及订阅清理证明。A01、0.3 继续保留浏览器生命周期缺口；本轮没有固定正式兼容基线或扩大支持平台。

### 官方 Bundle 禁用的浏览器验收失败与模块图定位

核对候选发行产物，官方插件页面通过 pluginManager.setBundleEnabled 控制 Bundle 层，与 Loader Entry.update 不同。新增 bundle-lifecycle-probe.mjs，从实际插件详情 Enable 开关关闭 SkillHub；HTTP 200 且开关关闭、组件为 Off，但 SkillHub 侧栏在 30 秒等待后仍可见，无 pageerror。验收失败，不标记 0.3 或 M0 完成。截图与结构化失败结果为研究目录 bundle-disabled-stale-sidebar.png、bundle-browser-lifecycle-failure.json。

扩展 Host observer 的 bundle 模式，使用同一官方 API 完成两轮禁用/恢复：Host 服务和两个工具消失，旧调用被拒绝；启用后新来源修订、工具和目录读取恢复。额外等待后直接读取 clientModules.graph().entries，禁用期间仍有 @astron-team/skillhub-dsh，clientPath 也仍存在。证据见 bundle-host-lifecycle-result.json；其中 passed 仅指 Host 生命周期观察，不是浏览器或完整 Bundle 验收通过。官方 client-modules 的 processOne 会排除 disabled/无 fiber 的 entry，但本次实际模块图未移除条目，尚需定位 dirty 通知与重组链路，不能仅凭此断言具体宿主源码缺陷。

浏览器探针的首次 finally 恢复失败；修改插件页导航的重复 Plugins 文本定位后，另通过官方 Bundle API 恢复，并从隔离 Profile package.json 核实 Bundle 已启用。重新安装同一 tarball 本身未恢复 bundles 选择，不能将安装成功当成启用证明。最终 Host/Registry 均停止。lint、语法与 diff 检查通过，本轮未修改产品逻辑。

操作纠正：一次 CLI 帮助查询遗漏 DSH_HOME，命令在用户目录新建了空 skillhub-m0 Profile；已核对仅含当次生成文件，完整移到 /tmp/skillhub-accidental-help-profile134 保留恢复副本，确认用户 profiles 中该新增目录已不存在。未删除或改写其他 Profile。后续所有宿主命令须显式使用隔离环境。

### 更正：Bundle 残留由包内 observer 污染导致，干净制品复测通过

前述“官方 Bundle 禁用的浏览器验收失败”不能作为宿主兼容缺陷结论。诊断确认 internal/plugin 禁用事件已发送，正式包的 Loader entry 已移除；Client registry 内仍存在的 source.loaderName 指向源码包内 test/integration/discovery-probe.mjs。隔离 Profile 长期保留该 m0-observer，即使 DSH_PROBE_SKIP_DISCOVERY=1，宿主仍按其 owning manifest 识别并注册 SkillHub Client。这也意味着此前该根目录下的 Client 测试可能运行源码构建文件，不能未经核对声称全部验证了安装 tarball 的 Client。

先用 overlay 禁用旧 observer：官方插件页面连续两轮禁用/启用通过。随后把 discovery-probe 复制到包外临时文件，备份后只替换隔离 Profile 的该 observer 路径；不带清理 overlay 再跑官方开关，两轮同样通过。禁用后侧栏移除、启用后唯一入口与搜索恢复、四次 setBundleEnabled 均 HTTP 200，无 pageerror。独立 Host Bundle 探针核对 clientPath realpath 确实来自 Profile 安装包，两轮禁用后 clientPath 和 graph 条目均消失，启用恢复及旧服务调用失效通过。

干净加载环境同时重跑目录导航、已安装详情/筛选/会话隔离、双 Registry 页面切换和对话引用失效，全部通过。来源已恢复，Bundle 已启用，自建进程均停止。没有修改产品代码、官方宿主源码或切换 DSH 基线；诊断时临时重发通知未改善污染结果，该操作不进入产品或正式验收步骤。

新增防回归约束：discovery observer 若从源码包目录直接执行即拒绝；生命周期探针要求已安装 Client 路径一致且禁用时图中无条目。`bun run lint`、观察器入口拒绝检查和 diff 检查通过，已查看干净禁用截图。证据：bundle-observer-contamination-result.json、bundle-clean-host-result.json、bundle-clean-browser-result.json、bundle-clean-disabled.png、bundle-clean-restored.png，以及 clean-catalog-navigation-result.json、clean-installed-navigation-result.json、clean-source-navigation-result.json、clean-source-reference-result.json。

保留历史失败作为测试环境问题证据，更正 acceptance.md 与 compatibility-design.md，不继续把它列为产品阻塞。近期上述用例已重跑；历史其他生命周期故障、实际技能使用等制品级结论仍须逐项重核，不能凭本轮扩大到全部历史测试。A01/A16 的完整支持基线及订阅释放仍未宣布完成。

截图复核补充：仅将 observer 移到包外后，禁用截图仍有测试 observer 等待 skillhub 导致的 Could not disable 警告。进一步以启动 overlay 同时禁用 m0-observer 和 skillhub-local-runtime-probe，最终再跑两轮官方 Bundle 开关，入口与搜索恢复、无 pageerror、无禁用失败提示均通过。最终 bundle-clean-browser-result.json 及禁用/恢复截图已替换为这次无测试依赖干扰的结果，已查看无警告禁用截图。不会把有测试警告的中间运行当作完整产品体验。最终 lint、diff 检查通过。

### 干净操作日志环境的安装、升级、卸载闭环

在新的隔离 HOME、DSH_HOME 和项目中，用既有候选安装包、DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 完成页面闭环。Registry 为本地 HTTP 夹具，独立命名空间避免历史安装记录干扰；原有两个 observer 禁用，创建会话的 observer 位于包外。没有调用模型，也未修改用户 Profile。

浏览器安装 1.2.0 后检查更新，生成 1.3.0 的单项目升级计划并确认；刷新页面后固定版本仍为 1.3.0，未重复提交。分别核验两次安装的资源正文、metadata 每文件 hash、聚合 fingerprint 及 inventory 唯一项目目标。卸载先取消再确认，取消不发送执行请求，确认后目录不存在、完整 inventory 恢复初始空集合、页面显示零项记录。已查看卸载截图；保留的升级结果属于历史操作，不表示技能仍安装。

事后独立读取三份持久日志，install、upgrade、uninstall 均为 completed、recovery=not-needed，无告警。三者 discovery 均为 unconfirmed，因此本次只证明文件操作闭环，不证明 DSH 已发现或模型已使用技能。探针旧字段 upgrades 统计 executePlan 请求，本次值 2 代表一次升级和一次卸载，不是两次升级；单独的日志检查报告给出明确计数含义。

首次使用旧故障测试根运行时，安装通过但升级计划被未完成恢复日志阻断。保留旧日志及安装文件，改用新隔离根完成正常路径；没有删除恢复记录或绕过写入保护。协调进程和相关 Host、Registry、浏览器现已退出。

证据：研究目录 clean-complete-lifecycle-result.json、clean-complete-lifecycle-journal-check.json、clean-lifecycle-upgraded.png、clean-lifecycle-uninstalled.png。该结果补充 A05/A08/A09 的 Linux 页面正常路径，不关闭故障矩阵、实际模型使用、远程 Host 或跨平台验收；未重新核验本次运行的 Client 模块实时路径，不将其替代独立 Bundle 探针的安装包路径证明。

### 安装后同会话原生发现核验

新增 installed-discovery-fixture.mjs。包外 Host observer 在隔离 Profile 中先核对实际 Client realpath 来自安装包，再创建独立项目和会话；安装前原生 snapshot 完整且无 document-helper。通过真实搜索工具、Remote 详情与目标选择、startInstall 完成安装。随后同一 Agent scope 下的官方 skills.get 能读取正文，resourceBase 精确指向该项目安装目录，资源正文匹配；完整 snapshot 包含新技能。无模型调用，未触发外部服务或生产配置修改。

DSH 0.2.0-rc.2 / Node 24.2.0 下通过，协调进程退出码 0，自建 Host 与 Registry 已停止。证据为 clean-installed-discovery-result.json。本次操作回执 discovery 仍为 unconfirmed；代码核对 Host 尚未将官方发现结果反馈到操作状态。因此确认文件发现链路可用，但发现状态反馈仍是实现缺口，不以本次 observer 代替产品实现。后续反馈必须验证当前会话、实际路径与固定版本内容，避免同名技能误确认；不能把发现确认表述为模型执行成功。

### 发现状态反馈实现与实包回归

共享核心增加 observeInstallation，只接受所属会话的已完成安装/升级，从日志绑定目标并校验可信根；官方读取前后均检查原提交的安装记录、metadata、资源指纹和操作 revision。Host 查询用官方 parser 对照同会话技能服务返回的正文、调用字段和真实资源位置；结果作为当前观察投影返回，不永久修改日志或文件成功状态。异常、超时、同名遮蔽或文件漂移保持 unconfirmed。

首次实包回归仍为 unconfirmed。分层诊断确认核心提交校验已通过，原因是直接比较 Cordis 两次读取产生的代理对象。改用现有 serviceIdentity 后重新构建、打包并经官方命令安装；实际 Client 路径已核对来自隔离 Profile 安装包。最终 DSH 0.2.0-rc.2 / Node 24.2.0 验证：安装前技能不存在，安装后同会话发现并读到正确资源，Remote 操作结果为 confirmed；修改本次隔离安装的资源正文后，查询变为 unconfirmed，files 保留 installed。没有模型调用，保留修改后的隔离夹具，不触碰用户技能。

新增单测覆盖未完成操作、其他 owner、错误根、缺失发现、回调异常、观察中资源变化、错误目录、旧正文及解析 metadata 差异。插件 build/typecheck/lint/tests 通过（161 tests / 2005 assertions）；CLI typecheck/lint/build 通过，全量测试 674 tests / 2794 assertions 通过。一次 make 命令误带不存在的附加目标，测试已完整成功但 make 随后返回 2；不将整条命令记为成功，以上测试结论来自测试运行器终态。

证据：installed-discovery-feedback-result.json；测试脚本 installed-discovery-fixture.mjs 已升级为断言 confirmed 和资源变更撤回确认。正式兼容基线、跨平台和真实模型使用仍未完成。

页面回归补充：同一新包在实际 Chromium 中完成安装 1.2.0、检查更新、确认升级 1.3.0、刷新无重放、取消卸载无提交、确认卸载和 inventory 原样恢复。逐文件 hash 及资源检查通过，无 pageerror。证据为 discovery-feedback-lifecycle-result.json、discovery-feedback-upgraded.png、discovery-feedback-uninstalled.png；该报告 upgrades=2 仍表示一次升级和一次卸载的 executePlan 总数。协调进程及自建子进程退出，已查看升级截图。

### 发现结果的核验时间与只读刷新

安装、升级与对话结果共用 DiscoveryStatus，明确发现结果属于上次核验，展示查询时间并提供“刷新发现状态”。广场补充原操作结果与当前安装记录的区分；不为终态增加持续轮询。刷新调用原 getOperation，不提交文件操作。

实际安装包在 DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 完成安装、升级、刷新页面、卸载闭环。升级结果刷新后确认发现；修改已知隔离资源，手动刷新显示未确认；finally 恢复原资源，再刷新重新确认。请求计数保持一次安装、一次升级，随后卸载产生第二次 executePlan，取消卸载没有请求。文件指纹、inventory 恢复、无 pageerror 验证通过，已查看核验时间与按钮截图。

证据：discovery-refresh-browser-result.json、discovery-refresh-browser.png。插件完整 build/typecheck/lint/tests 通过，161 tests / 2005 assertions；两条旧对话探针同步替换硬编码的“未确认”文案断言，不把未重新运行的对话路径算作本轮通过。自建协调进程、Host、Registry 与浏览器均退出。没有模型调用或外部发布。

### 原生对话安装卡片与已安装导航回归

新增包外 conversation-install-fixture.mjs，通过实际 astron_skillhub_install 工具在独立临时项目安装，持久化真实 accepted 工具回执为测试会话；确认安装包 Client 路径。会话为合成夹具，没有调用模型，不作为模型理解意图或自主使用技能的证明。

DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 实测通过：展开原生工具卡片、读取 Host 操作终态、只读刷新发现状态及核验时间、复制操作 ID、重载后保持同一 ID 并重新查询、打开已安装项目记录、固定版本详情、主动检查更新、返回列表恢复焦点、刷新列表和返回会话。浏览器 startInstall/executePlan 均为零，无 pageerror。

第一次在进入已安装后超时，截图显示安装记录存在；旧探针仍查找卡片 heading 和合并范围文本，而产品已改为表格。修正为实际文本与单元格定位后完整通过，没有修改产品代码。发现状态的新文案断言同时覆盖 confirmed/unconfirmed，具体发现确认的严格判定继续由 installed-discovery-fixture 和上一轮资源变化探针负责。

证据：clean-conversation-install-result.json、clean-conversation-install-card.png、clean-conversation-install-reloaded.png、clean-conversation-installed-detail.png。lint、git diff --check 通过，已查看卡片截图；协调进程、自建 Host/Registry/浏览器退出。工具等待取消、搜索抽屉完整回归、真实模型使用和完整可访问性仍未闭合。卡片截图反映当前功能实现，不作为最终视觉设计验收。

### 对话工具取消与已受理操作边界

新增 tool-cancellation-fixture.mjs，包外 observer 在真实 DSH 工具服务中验证取消；Registry 夹具增加可控 resolve 延迟，沿用下载延迟并记录请求。测试使用独立项目及命名空间，核对 Client 来自已安装包，不调用模型。

DSH 0.2.0-rc.2 / Node 24.2.0 下三种情况通过：（1）执行前 signal 已取消，未受理且没有新增操作日志或目录；（2）日志确认 Registry 已收到 resolve、响应尚未返回时取消，未受理且没有新增日志或目录；（3）工具返回 accepted、下载尚未完成时取消原 signal，文件安装仍正常完成。同 callId 用新的等待信号重试返回相同 operationId，仅产生一个新操作和一次下载，资源正文正确。原操作可继续查询，不把取消等同于回滚。

证据：tool-cancellation-result.json，包含独立核对的 Registry 请求计数。lint、git diff --check 通过，自建协调进程及 Host/Registry 已退出；未修改产品逻辑。该结果证明工具服务与文件操作的取消边界，不替代真实模型发起请求、桌面 Stop 按钮交互或完整可访问性验收。

### 对话控件样式修复与键盘、窄屏回归

搜索与安装 ToolView 原先未应用 NativeControlStyles，截图中的按钮使用浏览器默认字体及较小点击区。两类视图现在统一包在 skillhub-controls 中并复用已有样式，覆盖成功、失败及准备状态；不引入新组件库或改变宿主样式。

新 tarball 经官方命令安装后，真实 DSH/Chromium 的两个探针通过。搜索及安装卡片首个操作按钮均为 40px，字体与宿主一致；搜索详情验证 ArrowRight/Home/End、Tab 进入活动面板、tab/panel 双向关联、初始关闭按钮焦点、背景不可聚焦、Escape/关闭恢复卡片焦点及刷新历史会话。390px 抽屉无横向溢出，深色控件继承字体与前景色，关闭按钮约 43.6px。安装卡片的复制、刷新发现、重载和已安装导航回归通过，两条浏览器流程均未提交安装/升级请求，无 pageerror。

插件 build/typecheck/lint/tests 通过（161 tests / 2005 assertions），git diff --check 通过。证据：conversation-keyboard-layout-result.json、conversation-keyboard-dark-mobile.png、conversation-search-controls.png、conversation-install-controls-result.json、conversation-install-controls.png；已查看深色窄屏截图。协调进程与自建子进程退出。上述交互证据不代表完整 WCAG、读屏、全部页面窄屏或最终视觉设计验收；未调用模型。

### 双入口在途请求乱序与来源切换

新增 source-race-probe.mjs，在真实 DSH 浏览器中截留已经从 Host 成功返回的旧来源 Remote 响应，再通过官方插件配置切换 Registry，最后交付旧响应。该探针没有伪造响应内容；两个本地 Registry 的 namespace 不同，用于判断旧数据是否污染新页面。

对话路径：旧 openConversationDetail 成功响应被截留；关闭抽屉、换源后重新打开旧卡片，页面提示引用失效；交付旧响应后仍提示失效，无安装按钮。广场路径：截留第二来源 listSkills 成功响应，切回原来源并看到原 namespace，再交付第二来源响应，当前列表仍为原来源。两次 route.fulfill 都成功交付，不以请求取消当作乱序通过。原来源恢复，startInstall/executePlan 请求均为零，无 pageerror。

DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 通过。证据：source-race-result.json、source-race-detail.png、source-race-list.png；已查看失效详情截图。lint、git diff --check 通过，协调进程与自建子进程退出，未修改产品代码。结合已有来源配置和旧引用失效证据，A02 的双入口在途请求出口补齐；不扩展到跨平台、SaaS 或模型调用。

### 对话订阅释放与重载启动期查询恢复

新增 subscription-lifecycle-probe.mjs，从浏览器初始化起观察 document/window 的 visibilitychange 注册及移除，重复打开持久安装卡片、离开页面、官方 Bundle 禁用/启用三轮，同时计数 getOperation。禁用测试不加载注入 skillhub 的 observer，避免测试依赖阻止正常释放。

初次运行第一轮监听由 0→2→0，但重新启用后的卡片读取返回 installation-unavailable，因查询 retry=false 停在错误状态。诊断运行中手动查询即恢复，日志确认短暂不可用而非旧请求身份错误。仅对该只读查询的明确 retryable=true 错误增加两次有界重试（500ms、1000ms），新增单测验证上限及其他错误不重试。

最终安装包在 DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 连续三轮通过，无手动查询；其中一次短暂 installation-unavailable 后自动获得 completed。每轮监听 baseline=0、mounted=2、disabled=0，离开视图及禁用后 1500ms 观察窗没有新操作查询；启用恢复唯一入口，无 pageerror。正式安装记录及原请求不重放。

证据：subscription-lifecycle-result.json、subscription-lifecycle-before-retry-result.json。插件 build/typecheck/lint/tests 通过（162 tests / 2010 assertions），git diff --check 通过，自建进程退出。该观察证明本路径可见性监听和关闭后请求释放，不证明完整堆内存、全部错误路径或跨平台无泄漏。查询缓存清理由源码 dispose 路径支持，不能将其等同于堆快照实证。

### 升级提交前漂移与无需恢复的失败收敛

新增核心矩阵覆盖目标增加、目标减少、所有权、版本、指纹和元数据变化，分别发生在下载前和暂存校验后。初次运行的六个暂存后场景保留了原文件，但错误标记为 recovery-required。升级现复用安装已有的锁内失败收敛：确认未发生目标移动、未提交 inventory 且暂存已清理后结束为 failed / not-needed；无法证明清理完成时仍要求恢复。补充清理授权拒绝负例，证明暂存保留时不会错误解除恢复要求。定向测试 17 项、127 个断言通过。

新增 upgrade-drift-fixture.mjs，在包外加载观察器并验证 Client 来自已安装 tarball。真实 DSH 中先通过原生工具安装 1.2.0，再经 Remote 准备并执行升级；暂存校验后注入原目录资源修改。结果：升级拒绝，旧版本 1.2.0 和并发修改保留，操作 failed / not-needed。测试注入仅存在于观察器，结束时恢复包装方法及 Registry 夹具状态。证据：upgrade-staged-drift-result.json。

验证环境为隔离 Profile 的 DSH 0.2.0-rc.2 / Node 24.2.0；未调用模型。CLI 全量 687 tests / 2897 assertions、typecheck/lint/build 通过；插件 build/typecheck/lint/tests 通过（162 tests / 2010 assertions）。此证据覆盖本地提交前漂移，不替代远端候选包变化、计划过期和跨平台验收。

### Host 包元数据缓存的磁盘复核

检查候选 DSH 的 PluginPackages 实现发现，packageOf 按目录和包名缓存 readPackage 结果，replace 才清除缓存；每次调用 packageOf 并不足以发现同路径清单变更。兼容检查现同时校验宿主解析结果与 manifestPath 指向的当前清单，清单缺失、损坏、包名或版本不匹配时停止新写操作，不泄露文件路径。此检查不能证明已经加载的 JavaScript 与磁盘文件完全相同，宿主依赖升级仍需重启及实机验证。

新增测试对全部 Host 基线依赖保留旧缓存对象，逐项修改版本、损坏 JSON、修改名称、删除并恢复清单。裸 Cordis 恢复测试补充隔离目录中的真实清单。插件 build/typecheck/lint/tests 通过（163 tests / 2452 assertions），git diff --check 通过。真实宿主依赖替换、模块缓存与正式基线验收仍未完成，不据此关闭 A16。

### 官方 Host 包解析服务缓存回归与安装包验证

新增 host-package-cache.test.ts，直接实例化候选版本的 PluginPackages 服务，在隔离临时 node_modules 中创建全部 Host 基线清单。逐项替换清单版本后，验证官方 packageOf 仍返回原对象和旧版本，而插件兼容检查拒绝写入；删除清单仍拒绝，恢复清单后重新通过。该测试使用真实解析服务，不用 Map 模拟缓存；Client 清单仍是测试替身，不将其视为完整 DSH 启动验证。显式声明已有 dsh-app-boot 为开发依赖并更新锁文件，避免依赖偶然提升。

随后将新构建 tarball 经官方安装命令装入隔离 Profile，在 DSH 0.2.0-rc.2 / Node 24.2.0 中执行原生工具安装和 Remote 升级。新清单保护未阻断合法安装；升级暂存后资源漂移仍保留旧版本与并发修改，返回 failed / not-needed。Client 路径确认来自已安装包。证据：host-manifest-guard-install-result.json。

插件 build/typecheck/lint/tests 通过：164 tests / 2518 assertions。协调程序正常退出、自建进程清理。尚未验证完整宿主依赖代际替换及已加载模块与磁盘版本不一致的路径，因此 A16 仍保留正式基线与跨平台出口。

### 官方解析配置更新与 Node 模块缓存

新增 host-resolution-generation.test.ts，在独立 Node 子进程中通过官方 PluginPackages 安装真实运行时解析拦截，所有包和 Profile 均位于临时目录。先用 CommonJS require 和 ESM import 加载原包，再尝试将已有映射指向另一个同版本目录、删除映射；两者均由宿主拒绝，原缓存对象和模块实例保持不变。合法新增另一包映射后，元数据缓存重新读取，原有两种模块实例保持不变，新包经两种加载方式均可用。finally 释放解析拦截并清理临时目录。

Node 24.2.0 和 24.17.0 均通过，插件 typecheck/lint 及 git diff --check 通过。测试最初的 ESM 夹具因 declarer 与包目录不符合 Node 依赖布局而失败，修正为各代目录的 node_modules 后两种加载均通过；未修改宿主或放宽断言。此证据验证候选发行版的真实解析服务与 Node 缓存约束，不等于完整 DSH 升级或跨平台通过。方案已明确依赖版本变更要求重启后重新核验，无需增加重复的包映射冻结机制。

### 生命周期 §7.1 故障窗口对应检查

以下映射以具体测试内容为依据；核心级证据不自动代表 Host、浏览器或三平台验收。现有全量通过记录见前文，新增场景单独记录结果。

| 规范失败窗口 | 安装/升级测试 | 卸载测试 | 证据边界 |
| --- | --- | --- | --- |
| 移动前计划/日志失败 | transaction-journal 的 preparation acknowledgment failure；install-service 的 staging intent / beforeMove failure | removal-inventory 的 beforeCommit 抛错保留清单；removal-crash-recovery 的 before-intent/intent | 日志失败与进程终止是不同注入，分别保留 |
| 已移动，CAS 未提交 | install-crash-recovery 的 after-backup/after-install/before-commit；native-upgrade 的双目标恢复 | removal-crash-recovery 的 after-rename/isolated/before-commit | 检查原文件、清单和其他目标；恢复权限撤回另测 |
| CAS 返回结果未知 | install-service 的 inventory response loss confirmed/unknown | native-removal 新增 inventory-response-lost，实际清单提交后抛错，再恢复核验 | 卸载已提交及版本/所有权/其他来源冲突、原路径重新占用分支通过；详见下节 |
| CAS 成功，提交日志失败 | install-service 的 after-commit observer failure；install-recovery 的 lost commit acknowledgment | native-removal 的 commit-ack-lost/commit-log-failed | 不恢复旧文件；日志无法确认时保留待恢复状态直至核验 |
| CAS 成功，清理失败 | install-service 的真实 backup 权限失败；install-recovery 的 retained cleanup/conflict | native-removal 的 cleanup-denied；真实 DSH 清理权限验证见前文 | 权限用例在 Windows/root 环境有跳过条件，不可宣称通用通过 |
| 重读既非 before 也非 after | install-service 的 unknown inventory outcome；install-recovery 的内容/元数据/清单冲突 | native-removal 的 inventory-response-lost 下四种冲突及重新打开日志核验 | 保留现场，不以成功路径替代冲突证据 |
| 恢复过程中再次退出 | install-crash-recovery 的 return-staging/restore-backup/remove-staging | removal-crash-recovery 的 before-restore/after-restore/after-recovery-cleanup | Linux 子进程强制终止，使用真实锁过期；Windows 跳过 |

测试路径以 cli/test/unit/integrations 和 cli/test/unit/services 为根。上述映射不关闭 A10：平台特有恢复行为及必要 Host 出口继续追踪。

本轮补齐首次安装 before-install 和 after-commit 强制终止场景；原测试仅覆盖首次安装提交前的部分窗口。恢复后明确比较两个目标的文件、新版本及完整 after 清单，已提交的首次安装不被删除。该文件 1 项参数矩阵测试、197 个断言通过（约 65 秒，含真实锁过期）。卸载新增 inventory-response-lost 后共 8 项测试、93 个断言通过；CLI typecheck/lint 和 git diff --check 通过。无生产代码变更，无外部服务或模型调用。

### 卸载清单响应丢失后的冲突恢复

native-removal.test.ts 在实际清单已提交但返回异常后，分别引入四种变化：保留项版本改变、原目标以 Suite 所有权重新出现、另一 Registry 记录占用同一目标、原路径出现新文件。每种场景分别使用当前存储对象及重新打开的 OperationStore/InventoryStore 恢复，均返回 unknown；清单完整保持冲突快照、隔离正文保持原内容、新占用文件保持不变，操作继续出现在待恢复列表。测试仅在隔离夹具中撤销注入变化，随后原操作恢复为 committed，并验证再次恢复幂等、待恢复列表清空。

定向 8 tests / 133 assertions 通过。该测试证明核心从持久记录重建后仍保留冲突现场，不等同于进程强制终止或真实 Host UI 操作；这些证据分别由已有崩溃矩阵和后续 Host 验收承担。无生产代码改动。

### 升级固定候选、包篡改与下载消失

新增 upgrade-candidate-fixture.mjs，经真实 DSH 原生工具安装 1.2.0，再分别生成 1.3.0 升级计划，确认前把 Registry latest 切为更旧版本。执行依次遇到包内容篡改、下载 404、正常固定包。请求日志新增 version 查询字段，用于验证实际下载版本而非仅检查 UI 文案。

初次运行包篡改正常失败；下载 404 在尚未生成 staging intent 时被误标 recovery-required。修复共享失败收敛，仅在活执行器已退出且日志完全没有 transaction 时允许锁内核验并结束为 failed/not-needed；不扩展重启后的 unknown 恢复规则。新增 native-upgrade 下载失败单测，验证旧文件、清单和待恢复列表。

新包在独立操作日志目录的 DSH 0.2.0-rc.2 / Node 24.2.0 下通过：两个失败均保留 1.2.0，正常请求升级为 1.3.0；三次升级下载均明确请求 1.3.0。Client 来自实际安装包。失败运行的旧日志保留，未删除或重写来使测试通过。证据：upgrade-candidate-binding-result.json。插件 build/typecheck/lint/tests 通过（165 tests / 2520 assertions）。不涉及实际模型调用；共享 CLI 全量结果另行追加。

扩展实机矩阵至固定版本 resolve 指纹变化、resolve 返回其他版本、包篡改、下载消失及正常固定包五项。前两项在下载前失败，四个失败场景均保留旧版且无需恢复；正常场景仍下载并安装确认的 1.3.0。完整下载序列仅为初装 1.2.0 加后三项各一次 1.3.0，证明 resolve 变化未继续下载。证据文件已更新为五项结果，插件 lint 再次通过。

共享核心回归完成：make test-cli typecheck-cli lint-cli 通过，690 tests / 2984 assertions。首次安装同样补充下载 404、尚无 transaction 的失败收敛测试，确认目标文件未创建、操作 failed/not-needed、待恢复列表为空；该文件单独复测 15 tests / 92 assertions 通过。git diff --check 通过。

### 真实 Host 计划到期、会话隔离与已受理重试

新增包外 plan-context-fixture.mjs。在实际安装包的 DSH 0.2.0-rc.2 / Node 24.2.0 下，通过工具安装后分别创建升级和卸载计划。另一真实会话执行计划均返回 context-stale；将对应确认计划存储的 now 函数临时置于 expiresAt 精确边界，同会话执行也被拒绝。每次均比较持久 operations 目录，确认没有新增操作记录。时钟在 finally 恢复，不修改 Date.now、操作时间或其他服务；该结果是确定性边界注入，不声称等待真实五分钟。

另创建并受理一个升级请求，将计划时钟推进至过期后，原 requestKey 重试返回同一 operationId，新 requestKey 则被拒绝。最终升级完成，目录只新增一份操作记录，请求日志只有初装一次下载和升级一次下载。实际 Client 路径确认来自安装包，无模型调用。证据：plan-expiry-session-result.json；插件 lint、git diff --check 通过。来源切换及同会话项目变化的计划失效仍需单独核验，不由跨会话拒绝替代。

### 计划来源失效及官方会话目录归属

新增包外 plan-source-fixture.mjs，使用官方 loader entry.update 更新实际插件配置。通过真实 Remote 生成升级和卸载计划后切到第二 Registry，两种旧计划均返回 context-stale；恢复原配置后继续拒绝同一旧计划，sourceRevision 不复用。持久操作文件列表不增加，下载日志只有初装一次，旧版 1.2.0 保持不变。finally 恢复原配置及测试 Registry 状态。

另使用候选 DSH 的 sessionController.create({sessionId,cwd}) 尝试把已有会话指定到另一隔离目录，官方返回 session/conflict，重新 resolveAgent 的 cwd 仍是原项目。该官方接口不支持同会话迁移；跨会话计划隔离已由前一测试证明，内部上下文变化的防御性判断由 upgrade-plans 单测覆盖，不虚构界面中存在会话迁移功能。

DSH 0.2.0-rc.2 / Node 24.2.0 已安装包通过，无模型调用。证据：plan-source-project-result.json。初次运行包含直接比较 Cordis 服务代理身份的无关诊断，已移除该不可靠字段后重跑，不把代理对象比较当作服务重建证据。插件 lint、git diff --check 通过。

### 仓库内 Host 验收运行入口

新增 scripts/probe-host.mjs，将升级候选/暂存后漂移/计划上下文/计划来源四项真实 Host 验收的协调过程纳入仓库。运行前校验临时根、状态目录归属、候选宿主与已装插件路径及端口；启动本地 Registry，复制观察器到包外，使用隔离 HOME/DSH_HOME，产生唯一运行目录与报告。脚本只停止自己启动的进程，保留现场；Host 日志权限受限，不打印含令牌的启动 URL。运行期限与观察工具超时明确区分。

四个入口在现有隔离 Profile 的 DSH 0.2.0-rc.2 / Node 24.2.0 顺序执行均退出 0。另实测非临时根及端口占用提前拒绝，未创建运行产物。证据汇总：host-probe-runner-result.json。插件 lint、git diff --check 通过。测试环境仍需预先安装候选包和开发依赖，不冒充全自动环境安装或三平台通过。

修复 integration/README.md 原先直接加载仓库内 discovery observer 的命令，改为先复制到临时目录；补充候选 tarball 安装与本地 Registry 配置步骤。中英文插件指南链接到统一复跑入口，不再依赖个人 /tmp 协调脚本。

### 已安装状态双范围 Host 矩阵

新增 installed-state-fixture.mjs，并接入仓库 probe-host 入口。在新隔离 HOME/DSH_HOME 中通过原生工具分别安装项目级和用户级夹具，依次注入目录缺失、目标符号链接、元数据无效 JSON、元数据版本不符、SKILL.md 缺失、内容修改及真实目录 chmod 000。通过实际 Remote 检查当前项状态、另一范围保持 present，以及升级/卸载预检。

14 个组合全部通过。目录缺失返回 missing，只允许准备 remove-stale-record；权限拒绝返回 unreadable、列表标记不完整，不能当作 missing。内容修改在只读轻量投影中仍为 present，但两种写入预检均阻断。每次 finally 恢复原目录、文件、元数据和权限，最后两范围均恢复 present。保留夹具和操作日志，没有通过删除历史使测试通过。

环境：Linux 非 root、DSH 0.2.0-rc.2 / Node 24.2.0 实际安装包；permissionsCovered=true。证据：installed-state-matrix-result.json。插件 lint、git diff --check 通过。该结果为 Host 列表和预检验证，不代表相应浏览器状态文案、读屏或 Windows 权限语义全部完成。

### 已安装详情刷新后的写入口一致性

新增 installed-detail-state-probe.mjs，用真实 DSH 浏览器打开有效安装详情后破坏夹具元数据，并点击重新读取。初次实测确认页面已显示“安装记录无法识别”，卸载入口仍依赖 selected 的旧列表快照而可用；Host 仍会拒绝，但 UI 产生不一致。修复 InstalledSkills：新的升级/卸载入口以当前详情状态及读取成功结果为准，重新读取期间或读取失败时不开放新操作；已接受操作的结果追踪不受此 eligible 判断影响。

新 tarball 下浏览器回归通过：先取得可升级结果，再使详情元数据异常，升级和卸载入口均消失；恢复元数据并重新读取后入口恢复。另注入一次详情网络失败，两入口同样不可用，再次成功读取后恢复。浏览器 startInstall/executePlan/recoverOperation 请求为零。首次修复后复跑因测试等待 disabled 而实际组件隐藏入口超时，调整为核验入口不存在后通过，沿用组件既有交互而非放宽可操作性要求。

环境为 DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium 已安装包。插件 build/typecheck/lint/tests 通过（165 tests / 2520 assertions），git diff --check 通过。证据：installed-detail-state-result.json、installed-detail-invalid.png；截图已查看。该修复不宣称全部异常文案、视觉设计和读屏验收完成。

### 失效安装记录清理的浏览器闭环

RemovalControl 根据最新详情的 missing 状态显示“清理失效记录”，获得预检结果后以 Host 返回的计划类型为准。避免列表提示清理、进入详情却显示卸载的歧义；普通卸载仍沿用既有交互。

stale-record-browser-probe.mjs 在真实 DSH 会话中把专用夹具目录移出技能扫描位置，验证列表、详情和确认弹窗一致说明仅清理记录。取消按钮默认获得焦点，取消时写请求为零；重新预检并确认后仅一次写请求，页面显示清理成功，记录不再列出。被移走的资源文件内容保持完整，原目标仍不存在。提交后的备份保留，不重新创建无记录目录。

证据：[浏览器结果](../../../docs/research/dsh-native-plugin-2026-10-12/stale-record-browser-result.json)、[确认界面](../../../docs/research/dsh-native-plugin-2026-10-12/stale-record-confirmation.png)。截图已查看。环境为 Linux、DSH 0.2.0-rc.2、Node 24.2.0、Chromium，Client 来自安装的 tarball；未调用模型。对应产品改动的 build/typecheck/lint/tests 通过（165 tests / 2520 assertions），新增探针 lint 通过。此结果补充 A06/A09，不代表其他异常界面、跨平台或最终视觉验收完成。

### 运行时基线探针纳入统一复跑入口

probe-host.mjs 新增 runtime-baseline，复用既有 runtime-baseline-probe.mjs；探测依赖从已安装包的 peerDependencies 和 dsh.client.inject 读取，避免协调脚本维护另一份包清单。观察器仍复制到包外，沿用隔离状态目录、子进程清理和私有日志策略。

Linux / Node 24.2.0 / DSH 0.2.0-rc.2 实跑通过：初始允许写入，官方 Loader 停用 layout 后禁止写入，实际 submitInstall 在准备回调之前拒绝且操作日志不增加；恢复模块后重新允许写入。报告 runtime-baseline-runner-result.json 保留三阶段依赖与兼容状态。此项仅使已有同进程重载验证可从仓库复跑，完整发行版升级重启及正式基线验收仍未关闭。

### 官方发行通道复核与宿主变更使用说明

2026-10-15 只读查询 npm Registry：@deepseek-ai/dsh 的 latest/next 均为 0.2.0-rc.2，alpha 为 0.2.1-alpha.3。官方 GitHub Releases 将上述 RC 和 Alpha 均标记为 prerelease，Alpha.3 发布时间为 2026-10-14。保持既定 RC 候选，不因 Alpha 更新自动更换基线；该查询不关闭 M0。来源：[npm Registry](https://registry.npmjs.org/@deepseek-ai%2fdsh)、[官方 Alpha.3 发行记录](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.2.1-alpha.3)、[官方 RC.2 发行记录](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.2.0-rc.2)。

中英文插件指南补充宿主依赖变更后的停写、等待/恢复、完整重启和重新核验步骤，明确重试按钮不清除 Node 模块缓存，不能以刷新页面或重启插件替代 Host 重启。该使用说明依据已有官方解析缓存验证，不声称完整跨发行版升级已经通过。本轮不修改依赖、安装环境或产品代码。

### 取消卸载确认后的键盘焦点恢复

实际浏览器发现 Escape 关闭清理确认框后焦点未返回触发按钮，与产品设计 §10 的关闭恢复要求不符。RemovalControl 增加触发按钮引用，并让 Escape 与取消按钮共用关闭、清空未提交计划、恢复焦点的处理。保留原生 dialog，不改变提交和恢复协议。升级当前使用页内计划区，不把它描述为模态弹窗。

扩展 stale-record-browser-probe.mjs：Tab 从取消到确认、Shift+Tab 返回取消；Escape 关闭且恢复触发按钮，Enter 可再次打开；点击取消同样恢复焦点。两次取消均无写请求，最终明确确认仅一次写请求，备份资源保持完整。初次测试还要求原生 dialog 从首项 Shift+Tab 立即绕回末项，此断言涉及浏览器自身焦点导航，未作为产品缺陷；修正为验证两个动作的键盘可达性，不宣称完整焦点陷阱或读屏验收通过。

修复后的 tarball 在 Linux / DSH 0.2.0-rc.2 / Node 24.2.0 的实际 Host 与 Chromium 中通过，证据 removal-keyboard-result.json。build/typecheck/lint/tests 通过（165 tests / 2520 assertions）。安装期间 Corepack 查询网络失败，随后遇到原隔离环境 pnpm store 不匹配；使用本机缓存与该 Profile 原 store 后成功安装，未重建或删除现有状态。完整跨平台与可访问性验收仍未关闭。

### 升级计划的键盘连续操作

新增 upgrade-keyboard-probe.mjs，实机确认原实现生成计划后触发按钮被替换，焦点未转移到计划操作区。UpgradeControl 现在在计划结果首次显示时聚焦取消（被阻断/无需升级时对应返回），计划清空后聚焦重新挂载的生成按钮。每秒有效期刷新不会重复移动焦点，操作执行协议不变。

新 tarball 的真实 DSH 页面通过 Enter 生成计划、取消默认焦点、Enter 取消、恢复生成按钮焦点，所有写请求为零。证据 upgrade-keyboard-result.json。环境 Linux / DSH 0.2.0-rc.2 / Node 24.2.0 / Chromium；build/typecheck/lint/tests 通过（165 tests / 2520 assertions），git diff --check 通过。此次浏览器验证覆盖 ready 计划，不把未实测的 blocked/unchanged 分支或完整读屏行为算作通过。

### 真实 Alpha.3 宿主的安装拒绝及 Profile 恢复

在独立临时根通过 npm 安装官方 @deepseek-ai/dsh@0.2.1-alpha.3，再用 Node 24.2.0 的官方 plugin add 安装当前 tarball。安装器以插件声明的 RC.2 Host peers 与 Alpha.3 不匹配拒绝，退出 1，明确显示精确版本豁免未启用。未运行 allow-version，未改包清单绕过限制，插件 Host 因而没有进入可执行状态，不将此结果算作 runtime-unsupported 探针通过。

安装器自动回滚仅恢复 package.json，报告 node_modules 重新安装失败；实查依赖声明为空但插件文件仍残留。随后按官方提示执行隔离 Profile 的 plugin install，传入该测试环境原 pnpm store 与 ignore-scripts，退出 0。复核依赖声明为空、插件包路径不存在、Profile patch 为空数组、未创建技能操作日志。全程没有模型调用，不触及已有 RC.2 Profile。

证据 unsupported-alpha3-install-result.json。该结果证明真实新发行版的安装前拒绝及失败后 Profile 修复，不能替代运行中宿主升级、原操作日志跨版本回退或新宿主支持验证。首版候选仍为 RC.2。

### 已安装 Profile 的真实宿主版本往返

复制专用 RC.2 Profile 到独立临时根，保留其插件包和依赖记录，新建隔离 HOME/DSH_HOME/agents。依次使用官方 RC.2、Alpha.3、RC.2 的真实安装目录启动，每次完全结束前一 Host 后再切换，不编辑包版本字段或授予豁免。

RC.2 基线检查通过；Alpha.3 日志明确跳过不兼容的 SkillHub Bundle，Host Web 正常启动，依赖 skillhub 的观察器保持 pending。观察到官方拒绝后主动终止该探针，由运行器清理子进程；该次运行不是 runtime-unsupported 观察器通过。切回 RC.2 后插件和 Client 重载基线检查通过。Profile package.json、锁文件及持久 host.json 的内容 hash 均不变，无技能操作日志产生。

证据 host-version-roundtrip-result.json、host-roundtrip-before-result.json、host-roundtrip-after-result.json。此轮覆盖真实宿主重启后的拒绝与恢复加载，未覆盖有技能/待恢复操作的跨版本历史兼容。首次 RC.2 基线运行发生未定位的 true/false 断言失败；给断言补充阶段说明后复跑及回退后运行均通过，仍保留为未解决的偶发项，不据此关闭正式基线验收。

### 带已完成安装历史的宿主版本往返

在既有独立副本中通过真实工具完成项目级及用户级两次安装，记录完整文件、inventory、两项操作日志及 host.json 的 hash。完全结束 Host 后切换真实 Alpha.3，官方跳过不兼容 Bundle，未授予豁免；结束该 Host 后切回 RC.2。新 history-restart 探针通过官方 sessionController 解析原会话，通过 SkillHub 查询原操作 ID，并通过 scoped Remote 读取两种范围的 present 安装。

10 个快照文件逐项内容不变，两项原操作仍为 completed，未提交新写请求或调用模型。证据 completed-history-roundtrip-result.json。探针初次错误地读取持久记录 operation.phase，核对真实日志结构后改为 operation.status.phase 再运行通过；运行时查询仍核验公开 projection.phase，未改变产品数据格式。

此前同进程重载偶发断言在增加阶段说明后的连续五轮均未复现，仍不能认定原因已解决。本轮覆盖已完成历史的真实跨宿主往返，不关闭待恢复事务或跨插件版本日志兼容验收。

### 双目标待恢复升级经过 Alpha.3 后回退恢复

扩展 dual-upgrade-restart.py，增加可选的独立 unsupported DSH CLI 路径。新专用 Profile 先在 RC.2 完成用户级与项目级安装，浏览器发起一次双目标升级；既有夹具在第二目标备份后中断替换并阻断当次回滚，产生真实 recovery-required 和一处目标缺失的混合状态。正常结束原 Host 后，在同一 Profile 启动官方 Alpha.3，观察官方跳过不兼容 Bundle，未授予豁免；再正常结束中间 Host。原操作日志和 inventory 字节不变，两个目标存在状态不变。

重启原 RC.2，浏览器从历史操作执行一次恢复，注入恢复响应丢失后仍能查询原操作确认 restored。两个目标全部文件 hash 恢复到升级前，inventory 字节恢复一致，旧版本可见，关闭结果焦点回归通过，页面无错误。证据 pending-upgrade-host-roundtrip-result.json；Node 24.2.0 / Linux / Chromium。此轮使用真实进程与真实发行包，未调用模型。Python 语法检查及 git diff --check 通过。

本结果覆盖双目标升级待恢复状态的宿主版本往返，不替代跨插件版本 schema 回退、Windows/macOS 恢复语义或强杀路径的同类跨宿主组合。现有同版本强杀测试证据仍单独保留。

### 原生 Windows 检查与卸载根目录类型保护

在 Windows 原生临时目录复制 CLI/插件源码，独立安装锁定依赖，使用现有 Windows Node 24.16.0、Bun 1.3.14；未使用 Linux node_modules，不改用户 DSH Profile。工具链与 CI 固定 Bun 1.3.13 不同，结果不冒充固定工具链验收。初次 CLI 全量 677 pass / 7 skip / 6 fail / 2 errors，插件 build/typecheck/lint/tests 165 pass / 2520 assertions。CLI 失败包含三项默认五秒超时、两项硬编码 POSIX 路径以及一次符号链接 fixture 清理失败。

修正路径断言和使用 unlink 清理已知测试符号链接后，进一步暴露 Windows 实现差异：安装根被普通文件替代时，目标子路径 lstat 可返回 ENOENT，原预检错误地允许 remove-stale-record。增加安装根目录类型核验，并在执行、恢复时核验安装根及隔离根仍为目录。新增 stale-root-file 场景在提交前替换根目录，确认执行拒绝、清单和替换文件保留、恢复不误报成功；修复隔离夹具的根后可恢复原操作。

修复后 Linux CLI 全量 691 pass / 2991 assertions，typecheck/lint 通过；插件 check 165 pass / 2520 assertions。Windows 定向 65 pass / 1 POSIX 专用 skip / 397 assertions，使用 15000ms 上限；Windows 新插件 check 再次 165 pass / 2520 assertions。早先默认五秒定向复测的超时落在顺序启动所有帮助命令，用例行为在更长上限下通过。仅 DSH CI 的 Windows CLI 命令增加明确 15000ms 上限，保留全部断言及其他平台命令。Windows 修复后全量回归正在执行，尚不声明通过；原生 DSH Windows 浏览器验收、macOS 和远端 CI 未完成。结果摘要见 windows-directory-root-regression-result.json。

Windows 修复后全量回归补充：Node 24.16.0 / Bun 1.3.14，`bun test --timeout=15000` 退出 0，684 pass / 7 skip / 0 fail / 2597 assertions，共 691 tests。135 个 CLI/插件源码文件逐字节与当前工作树一致。跳过项为既有 POSIX 强杀、租约过期或权限故障用例，不计作 Windows 通过。DSH CI 工作流 actionlint 通过。Windows 原生 DSH 页面与固定 Bun 1.3.13 的远端 CI 仍未验收。

### Windows 原生宿主安装的网络前置检查

独立临时目录内的 DSH 候选依赖安装仍停在解析阶段；轮询确认安装会话仍在运行，未重复启动。Windows Node 独立请求 npm Registry 元数据在 10 秒期限内超时；DNS 成功返回 IPv4 地址，但强制 IPv4 请求元数据和候选发行包也均在 15 秒期限内失败。当前证据只能确认原生 Windows 直连请求未完成，不能确定网络根因，不能据此判定插件不兼容。

证据见 windows-host-network-result.json。Windows CLI/插件本地检查结果保持独立；真实 Windows DSH Host、页面和安装闭环尚未验证。

后续定位到 WSL 与 Windows 子进程的代理环境未继承：只传递现有代理环境、为 Node 启用环境代理后，同一 npm 元数据请求返回 HTTP 200。核验 PID、父进程及临时安装脚本归属后结束原挂起的 Bun，确认退出后以继承环境重新启动隔离安装；缓存已开始增长。未修改系统代理配置，未输出代理地址或凭证；依赖安装完成和真实宿主验收仍须分别确认。

Windows 依赖安装结果：继承代理后的 Bun 最终退出 1，报 LibreOffice Windows 可选包解压失败。随后使用现有 npm 在同一临时目录执行保留可选依赖、禁用生命周期脚本的修复安装，退出 0；确认 `@deepseek-ai/libreoffice-kit-win32-x64@0.1.5` 存在。该结果解除依赖准备阻塞，不代表 DSH Host 或浏览器验收通过；未更改项目包管理器、项目锁文件或系统工具版本。

### Windows 原生 Host 兼容门禁贯通

使用 Windows Node 24.16.0，在临时 Profile 中通过官方 CLI 安装当前插件 tarball，退出 0。真实 RC.2 Host 加载插件后，基线探针完成 Host/Client 依赖核验、同进程卸载 layout 后写入阻断、恢复 layout 后重新允许写入；报告 windows-runtime-baseline-result.json，未调用模型。

首轮探针在固定一秒延迟后看到本地运行时仍为 starting，ready 断言失败。探针调整为先等待磁盘恢复运行时 ready，最多十秒，failed/closing/disposed 立即失败；再执行原有兼容门禁断言。修复后的真实 Windows Host 探针退出 0，语法、ESLint 和 diff 检查通过。此次定位仅解释该 Windows 初始化时序失败，不据此认定此前 Linux 偶发 true/false 断言已有根因。Windows 页面、真实安装生命周期、macOS 及正式基线出口仍未完成。

### Windows 双范围安装及已安装状态矩阵

在原生 Windows Node 24.16.0 / DSH RC.2 / 当前已安装 tarball 中，通过真实对话工具分别完成项目级与用户级安装。随后通过 scoped Remote 核验每种范围的目录缺失、符号链接、元数据损坏、元数据不匹配、SKILL.md 缺失和本地内容修改，共 12 个场景；升级/卸载预检按预期阻断或提供失效记录清理计划，另一范围保持 present，结束后两范围均恢复 present。证据 windows-installed-state-result.json；未调用模型、未执行这些预检计划，POSIX 权限用例未覆盖。

Windows 浏览器入口尝试：真实 Host 和本地 Registry 已启动，但机器现有 Chrome、Edge 均在 Playwright launch 阶段退出，日志提示远程调试要求非默认数据目录；启动参数已含临时 user-data-dir，根因尚未确定。没有页面断言执行，不能计为 UI 通过；未更改浏览器策略。证据 windows-browser-launch-result.json。

### Windows 固定版本升级与候选内容校验

真实 Windows Host 通过项目级安装后执行五种升级场景：固定版本 resolve 的指纹变化、版本变化、下载内容篡改、下载 404 均失败且保留 1.2.0 和原资源；正常候选升级到 1.3.0，资源内容对应新版。每次准备计划后将 latest 改为旧版，操作仍使用已确认的精确版本；下载请求序列与预期一致。证据 windows-upgrade-candidate-result.json，探针退出 0，未调用模型。此项是 Host/Remote 执行验证，不替代 Windows 页面确认、双目标升级或崩溃恢复验收。

### Windows 操作计划有效期、归属与重复请求

在真实 Windows Host 中，升级和卸载计划均拒绝其他会话执行，并在精确到期时拒绝新操作，操作日志数量不变。已接受升级在计划过期后以同一 requestKey 重试返回原 operationId，新 key 被拒绝；最终升级完成且仅增加一个操作记录，下载总数为首次安装与升级各一次。有效期通过仅注入确认计划存储的时钟验证，不改变 Host 或日志时间。证据 windows-plan-context-result.json；未调用模型，不替代真实墙钟等待或浏览器确认交互。

### Windows 浏览与详情实机通过

Playwright 下载的 Chromium headless shell revision 1208 ZIP 全部 290 个条目通过 CRC 校验；原生安装器解压持续停留在未写完的文件，改在另一临时目录通过标准 ZIP 解压后，真实 Windows 浏览器能够启动。确认替代目录可用后结束原有安装器，未伪造安装标记或更改系统浏览器策略。

首次页面运行暴露宿主中文首次启动说明与原脚本英文入口不匹配，catalog-browser-probe.mjs 增加官方中英文引导处理。后续临时 --patch 配置覆盖下保存未被接受；将测试 Registry 写入专用 Profile 的持久 patch、取消临时覆盖后，原配置保存断言通过。两次失败保留在临时日志，不修改产品来规避配置冲突。

最终 Windows Node 24.16.0 / DSH RC.2 / 独立 headless shell 的真实浏览器通过配置保存、搜索、固定版本详情、详情/版本/安全审核/评论、返回保留搜索条件与焦点、空结果及清空、Markdown 实体和不安全内容处理；页面错误为零。证据 windows-catalog-browser-result.json、windows-catalog-detail.png、windows-catalog-list.png；截图已人工查看。探针 ESLint 与 diff 检查通过。此项覆盖浏览及详情功能，不代表最终视觉审查、Windows 页面写操作、对话交互或全部无障碍验收完成。

### Windows 页面安装、升级和卸载完整闭环

使用独立项目与命名空间，在真实 Windows Node 24.16.0 / DSH RC.2 / Chromium headless shell revision 1208 中完成页面安装 1.2.0、生成并确认 1.3.0 升级计划、刷新后查看固定版本、取消卸载和再次确认卸载。两版资源正文、metadata 逐文件 hash、聚合 fingerprint 和 inventory 唯一项目目标均通过；取消不发送执行请求，刷新不重新提交，卸载后目录不存在且完整 inventory 恢复运行前内容，其他既有测试安装保持不变。

首次只创建空会话导致宿主不显示测试标题，未进行安装；补充持久的合成消息后新建项目复测通过，没有调用模型。浏览器探针补齐中英文首次引导，ESLint 与 diff 检查通过。证据 windows-complete-lifecycle-result.json、windows-complete-lifecycle-journal-check.json 及 windows-upgrade-plan.png、windows-upgrade-completed.png、windows-lifecycle-uninstalled.png；卸载截图已查看。独立检查三份日志，install/upgrade/uninstall 均 completed、recovery=not-needed、无告警。探针 upgrades=2 表示一次升级和一次卸载的 executePlan 请求。discovery 仍为 unconfirmed，本轮不证明模型已使用或发现刷新已通过；Windows 对话、双目标/故障恢复和最终视觉验收仍待完成。

### Windows 对话安装卡片与已安装导航

真实 Windows Host 执行安装工具并持久化合成工具结果消息，使用独立浏览器验证原生 InstallToolView。卡片从 Host 查询终态、刷新发现状态、复制操作 ID、刷新页面后重放同一 ID 均通过；浏览器安装提交数为零。跳转已安装项目记录、固定版本详情、返回焦点和返回会话通过，主动检查更新覆盖当前相同、新版、旧版、同版本指纹变化和未知版本顺序，页面错误为零。控件字体与容器一致、按钮高度 40px。

本轮将探针浏览器 locale 固定为 en-US，与其宿主工具分组英文选择器一致，并补齐首次预览说明；中文宿主浏览/详情已由此前独立场景覆盖。证据 windows-install-card-result.json、windows-install-tool-card.png、windows-install-tool-card-reloaded.png、windows-installed-detail.png；卡片截图已查看。ESLint 与 diff 检查通过。消息为合成历史、安装为真实工具执行，未调用模型；未覆盖 Windows 搜索卡片安装、离线详情、失效选择卡片或对话响应丢失。

### Windows 对话搜索卡片、抽屉安装与重放

真实 Host 执行搜索工具并将真实结果写入合成历史，浏览器从原生搜索卡片打开固定引用详情，验证作用域确认、详情 Tab、首次焦点、背景不可交互、键盘切换、Escape/关闭焦点恢复，以及 390px 明暗布局无横向溢出。安装前明确断言浏览详情没有提交安装。显式点击后通过延迟下载夹具在进行中关闭抽屉并重新打开，原操作继续且只提交一次；完成后关闭重开、刷新浏览器历史再查看，仍无重复安装。页面错误为零。

首轮正常速度与随后延迟下载场景均通过，正式证据取后者：windows-search-card-result.json、windows-conversation-install-reopened-pending.png、windows-conversation-install-reloaded.png、windows-conversation-detail-dark-mobile.png。进行中截图已查看。探针固定英文宿主 locale、处理首次说明，并新增浏览零写入断言；ESLint 与 diff 检查通过。实际模型未调用；本轮不覆盖失效历史、双入口并发安装、跨进程故障恢复或完整无障碍审计。

### Windows 用户级与项目级双目标升级

新建隔离 HOME、DSH_HOME 与项目，通过临时 junction 复用已安装依赖和测试 Profile，操作清单与日志独立。浏览器分别安装用户级、项目级 1.2.0，生成的升级计划明确列出两个目标；确认后一次升级完成，刷新页面不重复提交。独立磁盘检查确认两个目标均为 1.3.0，资源正文、逐文件 hash、聚合 fingerprint 与 inventory 一致；清单仅含该技能的两个目标。两份 install 和一份 upgrade 操作均 completed、recovery=not-needed。

证据 windows-dual-upgrade-browser-result.json、windows-dual-upgrade-disk-result.json、windows-dual-upgrade-plan.png；计划截图已查看，diff 检查通过。首次独立日志读取误将 host.json 身份文件当作操作记录，修正为读取 UUID 命名操作文件后重新核对通过。此轮不修改产品代码、不调用模型；覆盖正常双目标升级，不替代中断恢复及强杀验收。

### Windows 双目标升级中断与恢复响应丢失

新的隔离状态目录中先通过真实工具安装项目级及用户级 1.2.0，再由包外测试观察器在第二目标备份后的替换授权点注入异常，同时阻断当次自动回滚，产生待恢复状态。浏览器确认双目标计划后执行一次升级；刷新页面后原结果仍可查询，执行一次恢复并故意丢弃恢复 HTTP 响应，随后通过原操作查询确认 restored。关闭结果后焦点回到范围筛选，页面错误为零。

独立读取磁盘确认两个目标均恢复 1.2.0、资源正文与全部文件 hash 正确、聚合 fingerprint 与清单一致，inventory 字节与升级前完全相同。原升级日志保持 phase=failed、recovery=restored，而非将失败升级误标成功。证据 windows-dual-upgrade-recovery-result.json、windows-dual-upgrade-recovery-disk-result.json、windows-dual-upgrade-restored.png；恢复截图已查看。探针补齐首次引导及固定宿主语言，ESLint 和 diff 检查通过。

该场景是同一 Windows Host 内的故障注入与浏览器重载，不是 Host 重启或强杀恢复；未调用模型。剩余进程退出边界继续单独验收。

### Windows 强制退出恢复：首轮页面失败，保留待定位项

在新隔离目录将双目标升级暂停于第二目标备份后，确认浏览器已收到原 operationId，再对本轮 Host 发出 SIGKILL 并等待其退出；新 PID 使用同一状态目录和浏览器 origin 启动。页面读取到原操作，但首次 recoverOperation 返回非成功结果，探针原断言缺少错误详情，浏览器进程退出。本轮不能计为强杀恢复页面验收通过。

保留原文件与日志后，再启动诊断 Host，解析原会话并等待本地运行时 ready，调用内部恢复接口成功返回 restored。独立核对两处 1.2.0 文件、资源及完整 inventory 字节均恢复。证据 windows-forced-exit-recovery-investigation.json。后续恢复成功不能反推首次失败原因；尚需区分启动时序、锁租约和其他错误。为下一轮给浏览器恢复断言补充实际错误结果，未更改产品恢复策略或删除锁文件。

Windows 强杀恢复失败定位补充：包外诊断观察器记录首次恢复时 runtime=ready、pendingOperations=1，实际错误为原操作 execution 锁 busy。共享 acquireTargetLock 使用 stale=10000ms、update=3000ms、retries=0；重启足够快时旧锁尚未过期，拒绝恢复符合不抢占执行锁的要求。接口对外返回 installation-unavailable/retryable=true，未暴露锁路径。证据 windows-forced-exit-lease-diagnosis.json。此前无细节的页面失败不能再归为未知启动故障；仍需验证可见拒绝后、租约过期再次明确恢复的闭环，不缩短租约或删除锁。一次诊断启动因 --patch 参数位置错误失败，保留该隔离现场，不计作产品失败或通过。

### Windows 强杀重启、租约拒绝与显式恢复重试通过

在第二目标备份后强制结束旧 Host，确认旧进程退出并以不同 PID 在同一状态目录、同一浏览器 origin 重启。首个恢复请求在 execution 租约未过期时返回 retryable 拒绝；包外诊断确认 runtime 已 ready，页面显示恢复未确认且没有误报 restored。保持生产锁参数不变，等待 11 秒期间没有自动提交；第二次用户点击恢复后丢弃成功 HTTP 响应，页面通过查询原操作确认恢复。共一次升级、两次明确恢复请求，浏览器无错误，关闭结果焦点正确。

独立磁盘核验两处旧版 1.2.0 的全部文件 hash、聚合 fingerprint、资源正文，完整 inventory 与升级前逐字节相同；原升级终态为 failed/restored。证据 windows-forced-exit-recovery-result.json、windows-forced-exit-upgrade-recovery-busy.png、windows-forced-exit-upgrade-restored.png，拒绝截图已查看。ESLint 与 diff 检查通过，未修改生产锁或绕过恢复授权，未调用模型。该结果关闭本次双目标替换边界的 Windows 强杀恢复问题，不扩大到所有安装/卸载崩溃窗口或 macOS。


### 默认用户级安装与双入口实包回归

对照产品和对话设计，修正共用 InstallControl 初始范围从 project 为 user；项目级用例均显式选择项目，不再依赖错误的默认值。安装工具原有用户级默认规则保持一致。

Windows 重新构建、打包并安装后，核对已安装 Client 与构建产物逐字节一致。页面探针断言首次默认 user，直接完成用户级安装，再显式选择项目级安装；两目标一次升级到 1.3.0，刷新未重放提交。对话探针断言首次及再次打开详情默认 user，显式选择项目级后安装，在延迟下载中关闭重开并刷新历史，安装提交次数始终为 1；键盘、焦点、390px 明暗布局检查通过，两条流程浏览器错误均为零。

Windows build/typecheck/lint/tests 通过，165 项测试、2520 项断言，无失败。证据：[windows-default-user-scope-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/windows-default-user-scope-result.json)。本次对话使用真实 Host 工具结果及合成历史，没有调用模型；不代表 macOS、远程 Host 或完整兼容矩阵通过。


### 原生配置与生命周期任务验收归档

核验 native-config-restart-result.json、bundle-clean-browser-result.json、bundle-clean-host-result.json 和 subscription-lifecycle-result.json：配置经两个独立 Host 进程和新浏览器保持；官方页面两轮禁用移除侧栏、启用恢复唯一入口及搜索；Host/工具和 Client 模块图释放；三轮可见性监听归零、卸载后停止操作查询。

当前 Windows 安装包再执行包外 plugin-lifecycle-fixture.mjs，使用官方 Bundle 开关完成两轮：已安装 Client 路径核验通过；禁用后 Host、两个工具、Client 路径及图条目均移除，旧服务请求拒绝；启用后来源修订更新、工具恢复、Registry 可读、依赖基线 matched，Loader 无重复项。结果见 [windows-bundle-lifecycle-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/windows-bundle-lifecycle-result.json)。隔离 Profile 最终恢复启用，测试进程正常退出，没有调用模型或执行技能安装。

本轮另运行 catalog-service 定向测试：9 pass / 24 assertions，覆盖配置更新、来源切换、旧引用拒绝及 dispose 取消在途读取。结合上述实际证据，关闭任务 0.3；不将其扩大为正式兼容基线、所有内存泄漏路径或 macOS 验收通过。


### 广场错误原因展示与手动重试

修正目录页面丢弃 Registry 结构化错误原因的问题。来源无效、匿名拒绝、未找到、限流、网络超时、服务不可用及响应格式/大小错误使用固定中文说明；不展示上游原始 message 或正文，保留原查询和手动重试。未知错误继续安全降级。

Linux 与 Windows 插件 build/typecheck/lint/tests 均通过：175 tests / 2546 assertions。新增 catalog-error 单测覆盖已知原因、未知/畸形 details 和诊断隔离。当前 tarball 在 Windows DSH 0.2.0-rc.2 运行，核对已安装 Client 与构建产物一致；catalog-error-probe 在真实 Remote 响应边界注入五类错误，页面均展示对应原因、不误报空列表、不展示原始诊断，移除注入后手动重试恢复真实目录，无写请求及 pageerror。证据 windows-catalog-error-result.json、windows-catalog-error.png，截图已查看。此浏览器测试验证展示，不把注入错误当作真实服务端故障证据；HTTP 映射由 Registry 定向测试覆盖。

任务 1.3 保持未完成：interface-design.md §7 要求 429 尊重 Retry-After，目前 RegistryError/Remote/UI 尚未传递并执行该等待约束，需后续补齐及验证。

### Retry-After 读取等待约束

RegistryAdapter 对 429 的有效 Retry-After 解析秒数或 HTTP 日期，保留来源适配器内的等待截止时间；等待中其他查询及说明读取在发起 fetch 前返回 RATE_LIMITED。Remote 错误增加可选 retryAfterSeconds，广场仅插入校验后的正整数秒数，仍不展示原始响应。到期不自动请求；取消仍返回 CANCELLED。等待状态不持久化，来源重建/Host 重启后的跨实例配额不在本次保证范围。

新增真实 loopback HTTP 用例：首次 429 后搜索及文档读取均被本地阻止，服务器请求数保持 1；等待超过期限仍没有自动请求，显式重试后请求数变为 2 并成功。Host 层用例确认不同关键词共享等待且保留结构化字段。解析覆盖秒数、HTTP 日期、过去日期、畸形值与溢出；客户端映射覆盖等待提示与非数字注入。

Linux 插件 check（build/typecheck/lint/tests）通过：179 tests / 2578 assertions。本轮尚未运行这次变更的 Windows 实包与浏览器等待提示验收，任务 1.3 保持未完成；此前 Windows 错误展示证据不覆盖新增字段。


### Windows 真实限流闭环与来源配置任务归档

当前源码在 Windows 通过 build/typecheck/lint/tests：179 tests / 2578 assertions。重新打包并由官方入口安装后，Host、Client 和 Remote 产物与构建文件逐字节一致。新增 retry-after-probe.mjs 使用真实 Registry 夹具返回 429 / Retry-After: 8；Remote 保留等待秒数，页面可见等待提示。等待期内点击重试，Host 再次返回 RATE_LIMITED，Registry 对该关键词仍仅收到一次请求；等待 8.5 秒后没有自动请求。再次手动重试恢复真实列表，关键词保持，Registry 请求数为 2，无技能写请求和 pageerror。

证据：[windows-retry-after-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/windows-retry-after-result.json)、windows-retry-after.png，截图已查看；测试子进程均退出。此验证没有替换 Remote 响应，也没有调用模型；不代表跨 Host 重启的持久配额管理。

结合此前配置持久化、sourceRevision/来源切换与晚到响应隔离、匿名及可选模块错误矩阵、Host dispose 证据，关闭 1.3。此前“Retry-After 未实现/未实包验证”的缺口由本节及上一节补齐；正式 DSH 基线、其他平台和真实模型验收仍由原有任务跟踪。


### 浏览详情导航验收归档

对照当前 CatalogPage 实现及 clean-catalog-navigation-result.json，复核列表/详情/已安装/会话切换的状态保存。catalog-navigation-probe.mjs 补齐中英文首次引导，将详情标题选择器与当前夹具 Document helper 对齐。当前 Windows DSH RC.2 安装包使用 20 卡片滚动夹具运行通过：首次与再次 Tab 往返保留浏览滚动，独立详情左上返回恢复原滚动及卡片焦点，返回会话再打开保留关键词和滚动，空结果后清空回到列表顶部；无 pageerror。

证据 windows-catalog-navigation-result.json、windows-catalog-navigation.png。探针 lint 与 diff 检查通过，未修改产品行为；自建进程已退出。结合已有 Linux 导航证据关闭任务 2.1；该回归不代表完整窄屏、所有错误状态或最终视觉验收。


### 目录范围与固定版本详情验收

新增 catalog-range-probe.mjs，以实际 HTTP 夹具返回 total=55、20 条结果，当前 Windows DSH RC.2 插件明确显示“共匹配 55 项，当前显示 20 项”及缩小关键词提示，卡片数为 20，没有加载更多或分页按钮。打开卡片后，实际 Registry 日志中的 resolve.version 和 SKILL.md 路径均为 1.2.0，没有重新解析 latest。

说明渲染实体为正常文字；未出现 script/img/危险链接节点，脚本标记未设置，也未请求远程图片。390px 下详情容器无横向溢出，截图已查看；此项不扩大为所有页面的窄屏或完整视觉验收。浏览器无 pageerror，自建进程已退出。证据 windows-catalog-range-result.json、windows-catalog-range-mobile.png；探针及夹具 lint、diff 检查通过。

结合此前真实 Registry 列表/解析/说明契约、搜索空结果与清空以及 Host 固定版本测试，关闭任务 2.2。未增加后端分页接口、修改正式目录数或将测试夹具计数当作真实实例统计。


### 窄屏加载、空态、传输失败与键盘回归

新增 catalog-states-probe.mjs。首轮从 390px 进入宿主时未找到会话入口，在插件场景前超时；修正测试为桌面宽度选择会话和打开广场，再缩至 390px。新隔离环境通过：暂挂列表请求显示加载而非空结果；空搜索后键盘清空恢复列表；中断 Remote 传输显示错误，恢复传输后 Enter 重试成功并保留关键词；Enter 打开卡片、返回详情后焦点回到卡片；键盘切换已安装显示明确空态，容器无横向溢出；返回会话隐藏广场。无安装/升级请求及 pageerror。

证据 windows-catalog-states-result.json、windows-catalog-loading-mobile.png、windows-installed-empty-mobile.png，已查看空态截图；探针 lint 与 diff 检查通过，自建进程退出。该结果不覆盖宿主移动端会话入口、已安装非空长表或安装/升级确认面板的全部窄屏状态；2.5 保持未完成，继续结合相应现有证据逐项验收。


### 窄屏管理闭环与界面状态任务归档

upgrade-success-probe 增加可选 390px 检查，保持完整文件及事务断言。当前 Windows DSH RC.2 实包在隔离项目真实安装 1.2.0、生成并确认升级到 1.3.0、刷新不重放、取消卸载不提交、确认卸载后目录消失且 inventory 恢复原内容。两个安装版本均核验资源正文、所有文件 hash、聚合 fingerprint 和精确项目目标；一次安装、一次升级、一次卸载，无 pageerror。

非空已安装列表、升级计划、升级完成、卸载确认、卸载后空态五阶段均无横向溢出，逐阶段截图留存，卸载确认截图已查看。证据 windows-mobile-lifecycle-result.json、windows-mobile-*.png。脚本 lint 和 diff 检查通过，自建进程退出。宿主会话选择和刷新后的入口导航使用桌面宽度，进入插件后缩窄；不声明宿主移动端导航或实际模型使用通过。

结合 windows-catalog-states-result.json 的加载/空态/传输失败恢复与键盘/返回会话，windows-catalog-error-result.json 的匿名拒绝及结构化错误、windows-catalog-range-result.json 的窄屏详情和此前搜索抽屉键盘验收，关闭 2.5。此项属于既定交互状态验收，不替代最终视觉审查、完整辅助技术审计或三平台支持。


### 安装核心边界验收

按 3.1 核对实际调用链：Host 目标引用绑定会话/来源/版本及可信根；resolveInstallationPath 使用有效策略、官方 DSH_HOME 与 Host 文件映射；executeNativeInstall 对已接受目标及解析快照复核，调用共享 installSkill 时固定 force:false。归档在暂存区完整解压，先比较聚合 fingerprint，再调用官方 FileSystemSkillProvider 验证 DSH 格式及 resourceBase；正式移动前重检目标与策略。

本轮定向验证 76 tests / 322 assertions、零失败：

| 边界 | 当前证据 |
| --- | --- |
| 自定义 DSH_HOME、最近 Git 根、只读策略、路径映射及符号链接拒绝 | installation-paths、installation-targets 测试 |
| 归档绝对路径/穿越、条目及解压大小上限、写入前拒绝 | CLI archive 测试 |
| 完整包指纹、已占用目录/所有权及提交前变化保护 | install-service、native-install 测试 |
| 官方 DSH 格式校验及暂存失败不发布 | skill-format、native-install 测试 |
| 带资源的用户/项目实际安装、逐文件及聚合指纹 | windows-default-user-scope-result.json、windows-mobile-lifecycle-result.json |

结构化结果 install-core-audit-result.json。关闭任务 3.1，不将安装边界通过扩展为全部崩溃恢复、macOS 或真实模型使用完成。本轮没有修改安装产品逻辑。


### 并发与重复请求协议验收

核对 confirmExistingNativeInstall 在下载前持有目标及清单锁核验既有完整安装，匹配则直接记录 already-installed；新安装的正式提交按 canonical target 排序取锁，重检占用、来源与相关 inventory 记录，清单锁内比较基线后提交。captureInstallInventory 只比较受影响记录，保留无关条目的并发更新。OperationStore 按 host/owner/key/digest 查找已接受记录，再执行新请求准备回调；重启后的旧引用重试不重新解析或创建操作。

本轮操作存储、目标锁、清单存储定向回归 24 tests / 74 assertions 全部通过，包含多实例同 key 去重、参数冲突拒绝、修订号 CAS、终态不能重放、锁身份及顺序、失效锁竞争和活跃锁保护。上一轮 native-install/install-service 定向测试已验证下载前幂等、目标变化及相关/无关清单变化。实机 concurrent-browser-result.json 与 concurrent-install-files-result.json 显示两个重叠操作仅一个成功，失败方无目标移动、无暂存残留、无待恢复状态，唯一安装内容及清单一致；Windows plan-context 证据确认过期后的同 key 恢复原 operation、新 key 拒绝。

结构化回归结果 concurrency-protocol-audit-result.json。关闭 3.2；崩溃/恢复状态的完整组合仍归 3.4/5.2，不以此代替全部平台或真实模型验收。本轮未修改产品逻辑。


### 确认计划与完整目标升级验收

本轮核对 ConfirmationPlans：不可变快照，五分钟有效期，精确到期拒绝，绑定 session/cwd/sourceRevision，并在异步接受前绑定唯一 requestKey。已接受请求先从持久存储恢复；过期计划不能生成新请求。Windows plan-context 及 plan-source-project 实机证据分别覆盖跨会话/过期、同 key 查询、换源切回仍失效、同会话项目变化拒绝与零额外写入。

核对 inspectUpgradeBaseline/executeNativeUpgrade：仅接受当前用户/项目的完整 DSH direct 目标集合；其他 Agent/项目、别名和共享所有权阻断。下载前及提交锁内复核全部目标、文件/metadata/版本/指纹/所有权，传入 allowTargetDrift:false、requireExistingTargets:true、expectedInventoryItem 和逐目标文件快照。确认后新增/删除目标或修改文件等均不能静默覆盖。

定向回归 25 tests / 201 assertions、零失败：插件计划/prepare/execute 五个文件 6 tests / 56 assertions，CLI native-upgrade/upgrade-baseline 两个文件 19 tests / 145 assertions。实际 Windows 双目标正常升级后的两个 1.3.0 资源/指纹与清单一致；中断恢复后的两个 1.2.0 资源/指纹正确且 inventory 与升级前逐字节相同，失败升级保持 failed/restored。

结构化结果 plan-upgrade-audit-result.json。关闭 3.3、4.2、4.3；此项验证既定计划/受保护替换机制，不扩大为所有崩溃窗口或跨插件日志版本兼容完成。本轮未修改产品代码。


### 日志与恢复机制验收

核对 OperationStore 的受保护目录、临时文件写入并 sync 后 rename、操作修订 CAS、持久主机身份及未解决目标阻断；LocalRuntime 正常关闭拒绝新提交并等待已接受执行/恢复排空，启动只扫描和提供原操作查询，不自动重放写入。

当前 Linux 独立运行四组定向检查，总计 65 tests / 805 assertions、零失败：安装强杀/暂存 3 tests / 225 assertions；卸载强杀/原生卸载 10 tests / 285 assertions；安装恢复/事务日志 38 tests / 212 assertions；Host runtime/关闭排空 14 tests / 83 assertions。真实子进程强杀覆盖首次安装、替换、卸载及失效记录清理的提交前后窗口，并覆盖恢复移动/清理时再次退出；两轮等待真实 target/inventory 锁租约过期，未删除锁或缩短租约。两组强杀矩阵分别约 66 秒。

各分支检查文件、完整清单及其他目标：未提交恢复旧状态，已提交保留新状态，冲突/权限变化保持未知且不覆盖；重复恢复幂等。日志和清单响应丢失、备份清理失败、损坏日志及新写阻断另有定向断言。结构化证据 recovery-boundary-audit-result.json；真实 Windows Host 强杀、租约拒绝后显式恢复见 windows-forced-exit-recovery-result.json。

关闭 3.4 的日志/生命周期机制实现验收。5.2 仍需按生命周期 §9 汇总其余业务场景的明确证据；三平台与跨插件日志版本兼容保持各自原任务，不把 POSIX 强杀用例当作 Windows/macOS 全矩阵通过。


### 生命周期验收场景总表

对照 lifecycle-design §7.1 的六类提交边界和 §9 的最低业务场景完成证据核对。下表明确区分核心测试与实际 Host/浏览器证据，不将单一场景扩展为所有平台的组合验收。

| 必需场景 | 已核验的证据 |
| --- | --- |
| 单目标升级、精确单目标卸载 | windows-mobile-lifecycle-result.json：真实安装/升级/卸载、文件指纹及清单恢复 |
| 两个允许目标共同升级且完整确认 | windows-dual-upgrade-browser-result.json、windows-dual-upgrade-disk-result.json |
| 外部 Agent/项目阻断、Suite 共享阻断 | upgrade-baseline.test.ts 的完整集合、other-agent、foreign、suite:demo 及跨 Registry 占用断言；plan-upgrade-audit-result.json |
| 无新版本、版本不可比较、同版本内容变化 | update-assessment.test.ts；m3-update-matrix-result.json 的真实 UI 分支 |
| 本地修改、确认后文件/metadata/清单/目标集合变化 | native-upgrade.test.ts、upgrade-baseline.test.ts；plan-upgrade-audit-result.json |
| 下载失败、指纹失败及候选漂移 | windows-upgrade-candidate-result.json：missing/tampered/resolve-version/resolve-fingerprint 均保留旧版 |
| 双击、双入口并发 | installation/upgrade/removal-requests 测试；concurrent-browser-result.json、concurrent-install-files-result.json |
| 响应丢失、刷新后原请求恢复 | 当前 requests 测试覆盖安装/升级/卸载；m3-upgrade-response-loss-files-result.json 确认仅一个 upgrade operation；Windows 双目标恢复响应丢失另见 windows-dual-upgrade-recovery-result.json |
| 目录移动后清单失败、提交后日志失败、未知提交结果及冲突 | recovery-boundary-audit-result.json 对应真实强杀矩阵与原生卸载/安装恢复测试，按 §7.1 分别处理，不回滚已提交状态 |
| 清理隔离失败 | cleanup-removal-result.json、cleanup-cleanup-files-result.json：真实文件权限拒绝、隔离内容保留、显式恢复后清理及告警消失 |
| 重启恢复、恢复中再次退出 | recovery-boundary-audit-result.json 的 POSIX 进程矩阵；windows-forced-exit-recovery-result.json 的真实 Host 强杀、锁租约拒绝及显式恢复 |
| 离线卸载、保留其他目标 | offline-removal-result.json、offline-offline-files-result.json：Registry 前后连接拒绝，精确目标删除，其他目标不变 |
| ENOENT 与 EACCES 区别 | removal-baseline/native-removal 测试；permission-removal-result.json、permission-permission-files-result.json：真实权限拒绝时文件/清单保留、不误报成功 |

本轮补跑版本判断、精确卸载基线及三个 Client 请求状态存储测试，共 21 tests / 131 assertions、零失败，结果 lifecycle-scenario-audit-result.json；其余最近定向回归见安装核心、并发协议、确认计划和恢复边界归档。由此关闭 5.2。5.4 的平台支持、真实技能使用及跨插件版本日志兼容仍分别保持原任务，不能依据本表宣布发布完成。


### 对话工具与历史结果验收

核对独立 astron_skillhub_search/install 注册、已有名称保护、官方逐参数 schema 及严格运行时校验。搜索仅调用只读目录服务；安装描述要求明确安装请求，输入仅接受 selectionRef 或完整 namespace/slug 二选一和 scope，不接受文件路径。用户范围默认仅作用于省略值，不将不可用项目范围改成用户范围。conversationInstallKey 由持久 Host ID、sessionId 和 callId 派生，参数摘要独立校验；页面与工具共用 Host 提交与操作存储，不提供对话升级/卸载工具。

InstallToolView 的 accepted 历史不是成功证明：来源/主机/时间来自原回执，按原 operationId 查询实际终态并比对身份；当前本地版本另查，旧成功不等同当前安装。presentationMeta 纯映射，未知 schema 保守展示，不执行历史动作。搜索引用经来源/项目/会话校验，歧义版本不能静默选 latest。

本轮 26 tests / 123 assertions 通过，结构化结果 conversation-contract-audit-result.json。Windows 实际工具执行和合成历史展示见 windows-install-card-result.json、windows-search-card-result.json：原生卡片、状态查询、关闭/重开/刷新及无重复安装；clean-source-reference-result.json 验证来源变更及切回后旧引用拒绝；concurrent-browser/result-files 证据确认双入口竞争不破坏成功目标。

关闭 6.2、6.4 的实现与协议验收。工具描述不是模型遵循意图的实证；真实模型、歧义对话及取消工具等待仍归 6.6。没有把合成会话或单元测试作为实际模型调用通过。


### 真实工具执行的取消信号边界

新增包外 tool-cancellation-fixture.mjs，在当前 Windows DSH RC.2 安装包的官方工具注册器执行 astron_skillhub_install。Registry resolve 延迟 2 秒、download 延迟 6 秒，使用真实请求日志确定取消时点。首次调用在 resolve 已到达、尚未 accepted 时取消：没有操作记录和下载。第二个新调用在收到 accepted 且下载已开始后取消调用信号：原操作仍 completed，资源文件正确。随后以同一 callId 重放，返回同一 operationId；全部流程仅一个操作、一次下载。

证据 windows-tool-cancellation-result.json；已核验 Client 来自 Profile 安装包。探针 lint 与 diff 检查通过，隔离 Host/Registry 子进程均退出。该场景直接取消官方工具执行信号，不等同于真实模型运行中用户点击 DSH 停止按钮；6.6 的真实模型意图和会话停止端到端行为仍未关闭。

### 发布文件与独立 Node 核心复核

重新执行 `bun run build` 后，运行 `bun test test/package.test.ts test/local-core-package.test.ts`：4 tests / 157 assertions 通过。环境为 Linux、Node 24.17.0、Bun 1.3.14；`bun run typecheck`、`bun run lint` 与 `git diff --check` 均通过。

新增实际 npm tarball 文件检查：所有 package exports 的运行入口和类型声明、Bundle patch、许可证必须存在，发布文件限定为包描述、指南、许可证、patch 与 lib；拒绝测试夹具、node_modules 和本地配置目录。现有仓库外 Node 子进程检查继续通过：不安装 node_modules、不访问外部网络，直接执行包内 local-core 完成带资源技能安装、清单与操作日志恢复。Host 服务加载/释放、官方生成 Remote 的方法及上下文声明、Client 惰性加载与 React external 检查通过。

本轮证明当前构建产物的发布文件与独立核心运行边界；未新增干净 DSH 全包安装或 macOS 验证，不能据此单独关闭 8.5 或平台门禁，也未执行 npm 发布。

后续补充 Host/Remote 构建产物的可解析 import 检查：index.js、local-core.js、typert.host.js、typert.remote-client.js 的裸包导入必须出现在 dependencies/peerDependencies，相对导入必须存在且位于发布的 lib 内；local-core 的外部依赖仅允许 Node 内置模块。Client 的实际 ModuleLoader factory 仍只接受声明的 React external。当前两项包测试文件合计 5 tests / 195 assertions 通过，typecheck/lint 通过。该扫描不覆盖计算生成的模块路径；Host 通过 URL 惰性加载 local-core 的路径已人工核对，对应文件存在及其独立 Node 执行由 tarball 测试覆盖。完整 Host 运行仍依赖实际 DSH 验收，不能用静态导入检查替代。

### 持久操作上下文与查重顺序验收

核对 OperationStore.open/accept/findRequest、native-runtime 与 Host submitInstallation/installConversation：Host 身份原子保存于操作目录；回执持久保存来源、版本、Host、发起入口及时间。请求参数先生成摘要，同 owner/key 的记录先于短期引用和来源解析查询；参数不一致拒绝，缺失的 retry 不创建记录，重复请求不重新调度执行。首次接受前与写入锁内各做一次查重；损坏记录阻断新请求。Host Remote 的上下文与原始输入校验仍保留，查重不绕过会话归属。

补充 native-runtime 的来源变更回归：首次接受后重新打开同一状态目录，提供新来源及新版的准备函数。原 key 的 new/retry 均返回原回执且准备函数调用为零；新 key 才接受新来源/版本，原记录保持不变。该测试验证核心持久协议，未模拟 DSH 配置 UI。已有 plan-source-project-result.json 另证实实际 Host 来源切换/切回及项目变化使尚未执行的计划失效，没有接受新操作。

本轮 CLI operation-store/native-runtime 共 14 tests / 79 assertions；插件 runtime-execution、conversation-install-host、conversation-install-request 共 17 tests / 134 assertions，全部通过。CLI typecheck/lint 与 diff 检查通过。由此关闭 7.1 的协议实现与定向验收；真实模型意图、正式兼容基线和平台范围仍由各自任务验收。

### 工具参数与可信调用上下文验收

核对已安装 RC.2 官方 defineTool：逐参数定义生成 JSON Schema，execute 包装先验证输入，再执行插件处理函数。插件安装参数另用 strict union 验证 selectionRef 与 namespace/slug 互斥、完整坐标及 scope，不接受模型提供路径、请求标识或下载地址。搜索仅接受 q。调用身份只取 exec.agent.session 和 exec.callId；Host 再核对会话与项目，并按持久 Host/session/call 派生请求 key。

扩展搜索和安装工具执行测试，覆盖 null、缺少字段、错误类型、额外 path/sessionId/callId、非法 scope、混合引用和坐标、路径式 slug，以及缺失 agent/callId。拒绝时 Host 回调调用数为零；合法搜索验证关键词清理及原始 AbortSignal、可信会话上下文传递。这里调用的是官方 defineTool 包装后的 execute，不是自制 schema 校验器。

install-tool、search-tool、conversation-install-request、conversation-install-host 共 10 tests / 99 assertions 通过；typecheck/lint/diff 检查通过。测试中故意构造的空 callId 通过 Reflect.apply 进入运行时负例，避免将普通字符串误当成官方品牌类型。关闭 8.2；没有运行模型，不能据此关闭用户意图或停止按钮的端到端验收。

### Windows 操作订阅可见性与释放

扩展 subscription-lifecycle-probe，在实际 Windows DSH RC.2 安装包上验证浏览器的隐藏/显示行为：将 getOperation 响应的状态投影暂置 staging，实际观察连续轮询后模拟 document 隐藏，2.5 秒内没有新增查询；恢复可见并移除投影后读取真实 completed。Host 操作记录未修改，该证据验证 Client 可见性处理，不等同于操作系统窗口最小化事件验收。

三轮挂载/禁用/恢复的 visibilitychange 监听数均为 0 → 2 → 0；卸载视图后不轮询，入口恢复唯一，无页面错误。第三轮 Host 重载的有限自动重试耗尽后，点击一次“查询原操作”恢复真实状态；manualReadRecovery 为 1，不宣称所有重载都能自动恢复。此前两次失败分别为探针仅支持英文按钮，以及将自动恢复误写为必达条件，已修正定位与验收逻辑，未修改产品的重试上限。

结果 windows-subscription-visibility-result.json；Host、Client 及两个 Remote 产物与 Windows 构建逐文件 hash 一致。探针 lint、diff 检查通过，运行器及其 Host/Registry/浏览器子进程均结束。8.4 保持待验收：多个同时可见消费者的同操作单次在途查询合并仍需直接证据，不能仅依据共享 queryKey 或本轮单卡片测试关闭。

### 共享操作查询的双消费者验收

新增 operation-dedup-fixture/probe，在 Windows RC.2 实际安装包中执行一次工具安装，将同一真实回执写入两个合成历史卡片。浏览器同时挂载两个卡片，前两次 getOperation 响应投影为 staging，每次延迟 1.5 秒，随后读取真实完成状态。全程共 3 次请求，最大在途数为 1，两个卡片均观察到进行中及完成，完成后 2.5 秒无新增请求，浏览器写请求为零。截图已检查，两个卡片均实际展示；不是仅用共享 queryKey 推断合并。

证据 windows-operation-dedup-result.json、windows-operation-dedup.png；探针及包外夹具 lint 通过。运行器及 Host/Registry/浏览器子进程已退出。结合上一节的隐藏/显示、三轮订阅释放和手动恢复证据，关闭 8.4。当前 Client 通过共享 QueryClient/queryKey 合并操作读取，持久 OperationStore 仍位于 Host，两者不混为同一实现。该测试使用合成历史与只读响应注入，不代表真实模型行为或跨平台全量验收。

### 独立发布工作流验收

核对 release-dsh-plugin/check-dsh-plugin、release-policy 及现有镜像、Chart、CLI 发布入口。插件仅手动发布，默认不上传；运行引用必须为与包版本一致的 dsh-v 标签，发布要求显式 private:false。构建矩阵全部成功后，从 Linux/Node 24.2.0 检查产物获取 tarball，校验包内 manifest、记录并核验 SHA-512，发布后对比 npm dist.integrity。预发行使用 next，正式版本使用 latest。发布凭证只用于 dsh-npm 发布 job。

现有 CLI 的自动标签匹配 cli-v；Chart 发布限定 v/chart-v/helm-v；镜像发布排除 dsh-v Release，保留原手动触发行为。actionlint 对上述五个工作流检查通过；release-policy 的 3 tests / 29 assertions 通过，含实际 Node 执行入口、分支拒绝、标签/包名/版本/private 不一致拒绝且不产生输出。

修正 release-guide：当前 CI 不自动启动 DSH 完成候选 UI/模型验收；审批前必须核对本次运行的候选 tarball 和验收记录。两次工作流运行即使 tag 相同，也须确认 SHA-512 相同才能复用包验收。关闭 9.1 的工作流实现任务，不代表远程 CI、Environment 审批规则、npm 权限或正式发布已验证；没有创建 tag、上传包或执行部署，9.5 保持未完成。

### Registry 契约与 SaaS 同步交接

核对 registry-api-contract、RegistryAdapter、匿名探针及已有实例报告，接口路径与消费语义一致。重新运行 registry-contract-probe、registry-adapter、registry-stream：31 tests / 267 assertions 通过，覆盖匿名无凭证、固定版本拒绝漂移、原始 UTF-8 流、分页/null、超时/取消/大小限制、归档路径及指纹、必需失败与可选失败分离。没有重新部署服务端或连接 SaaS。

完善 registry-api-contract §3.2–3.4：列出本次服务端生产路由/DTO/迁移无新增，已有 Controller 测试及插件探针的同步边界，分别记录开源预览实例的必需/可选结果与 SaaS 未验证状态，补齐目标实例执行和交接清单。关闭 9.4 的文档交付；SaaS 支持仍未验证，9.3 的公共 OpenAPI 核对及目标实例使用验收分别继续。

### 公共 OpenAPI 缺口核对

读取当前 OpenApiConfig、CliSkillController、SkillController、SkillVersionResponse 与生成 schema.d.ts，确认所需路由存在，但成功响应字段和传输语义描述仍不完整：CliResolveResponse 字段全部可选，file/download 仅生成 */* string，下载实际支持的 302 + Location 未描述，版本 nullable 字段没有完整表达。registry-api-contract §4.1 已明确修正来源和验收条件；9.3 保持未完成，不能用现有匿名探针通过代替描述同步。

本轮未改动生产 API、未手工修改生成类型，未运行共享 docker compose 的生成检查脚本；该脚本会启停仓库默认依赖，后续生成应使用隔离服务环境，避免影响已有预览实例。

### Registry DTO 的 OpenAPI 注解修正

CliResolveResponse 六项安装身份字段标注 REQUIRED；SkillVersionResponse 的 changelog/publishedAt 标注 nullable。新增 RegistryOpenApiSchemaTest，调用实际 Swagger ModelConverters 检查 required 集合、int64、nullable 与 date-time，不以反射检查注解存在代替转换结果。没有修改响应值或访问控制。

执行 `./mvnw -pl skillhub-app -am -Dtest=RegistryOpenApiSchemaTest,SkillControllerTest,CliSkillControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，25 tests、零失败/错误/跳过，BUILD SUCCESS。覆盖 DTO 转换及既有文件/分页/CLI Controller 行为。首次测试编译因 Swagger 原始泛型访问失败，改为 Schema<?> 后重新运行通过。diff 检查通过。

9.3 仍未完成：文件流和下载跳转注解、当前服务端完整 OpenAPI 生成及 web 生成类型同步继续待办。registry-api-contract 的 SaaS 清单同步加入 DTO 文档注解变更，不再将生产 DTO 描述笼统列为无修改。

### 公共 OpenAPI 响应与生成类型验收

SkillController 的固定版本 file 注解明确 application/octet-stream/binary；固定版本 download 明确 application/zip/binary 和 302/Location。仅补充文档注解，没有改变路由、匿名权限、内容协商或下载分支。新增 Controller 测试读取实际 /v3/api-docs，断言原始流类型、302 Location、resolve required、nullable；另验证匿名 ZIP 原始流与 302 响应，跳转分支不得打开内容流。

同前节 Maven 命令重新执行，SkillControllerTest 13 项、CliSkillControllerTest 12 项、RegistryOpenApiSchemaTest 2 项，共 27 项全部通过。测试使用隔离 H2 与模拟业务服务，不启动共享 Docker 依赖。实际 Springdoc 文档写入 target/registry-openapi.json；使用 web 锁定的 openapi-typescript 7.13.0 执行 `pnpm exec openapi-typescript ../server/skillhub-app/target/registry-openapi.json -o /tmp/skillhub-dsh-generated-schema.d.ts`，检查差异后将完整生成文件同步至 web/src/api/generated/schema.d.ts。未手工编辑类型；差异仅涉及这批响应描述及 DTO 字段。web 依赖通过 frozen-lockfile/offline 安装，锁文件未改，`pnpm --dir web run typecheck` 与 diff 检查通过。

结构化证据 registry-openapi-result.json。结合既有匿名探针 31 项回归与明确的接口复用清单，关闭 9.3；不声明当前工作树服务端已部署或 SaaS/对象存储已经验收。

### 兼容性保护复核与入口降级缺口

当前 compatibility/runtime-compatibility、host-package-cache、compatibility-node、runtime-execution、installation-paths、conversation-install-host 共 31 tests / 1638 assertions 通过。覆盖精确 peers、Node 范围、Host/Client 缺包与混装、磁盘 manifest 变化绕不过官方缓存、新安装/升级/卸载/恢复写入拒绝、原回执只读查询，以及项目不可用时不偷偷改成用户级安装。已有 m0-controls-compatibility-result.json 的 Client 响应注入证明按钮禁用及显式重查；runtime-baseline-missing-client-result.json 则是实际 Host 缺失 Client 图节点时拒绝准备写入。unsupported-alpha3-install-result.json 仅证明官方安装器拒绝不匹配 peers，不是 Alpha 运行兼容通过。

发现 10.3 尚未完全满足：client/index.tsx 顶层要求 layout，且 main/sidebar/ToolView 注册共用依赖 layout 的 panel 注入块。compatibility-design 要求缺少某个独立 UI 入口时保留已验证的另一入口；当前依赖结构不能证明这种独立降级。因此 10.3 保持未完成，需要拆分入口依赖，并在缺少布局入口的实际宿主上下文验证对话视图、只读工具及写入提示，不降低既定产品范围。上述测试通过不能代替这项验收，也不构成唯一正式版本基线确认。

### 页面布局与对话入口依赖拆分

client/index.tsx 不再将 layout 作为插件顶层及共享会话服务块的必需依赖。main/catalog/sidebar 注册移入独立 layout 注入块，对话搜索和安装 ToolView 保留在共享 Remote/session/slots 块；QueryClient 和请求状态仍共享。安装结果跳转广场时临时检查 layout，缺失或查询期间发生变化会返回明确错误，不继续调用失效布局。

新增 client-entry-lifecycle.test.ts，使用实际 Cordis Context/Service 生命周期与模拟 slots/Remote/session：无 layout 时两个 ToolView 已注册；两次提供/释放 layout 均只增删广场入口，ToolView 注册不重建；插件 dispose 后所有条目清空。该测试证明服务依赖边界，不声称模拟 slots 等同于真实宿主 UI。

`bun run check` 完整通过：构建、typecheck、lint、182 tests / 2769 assertions。当前 Client 产物已重新构建；Windows Profile 尚未更新这一版，不复用旧安装包浏览器证据为本改动背书。10.3 保持待验收，下一步验证新包的正常导航及真实布局缺失/恢复。


### Windows 独立布局入口降级实机验收

当前 Client 的广场不可用错误通过 CatalogUnavailableError 独立显示，避免被误报为来源或会话已变化。最新 Windows 原生构建通过 check（182 tests / 2769 assertions），隔离 Profile 已安装更新产物；Host、Client、local-core 与两个 Remote 文件逐项 SHA-256 与该构建一致。正常广场导航回归通过，结果为 windows-layout-navigation-result.json。

layout-degradation-probe 在页面初始化时包装官方 ModuleLoader 的插件 factory；保留加载器由队列模式切换为实时模式时的 load 包装，仅对 SkillHub apply 上下文执行官方 Cordis layout 隔离。真实安装回执的合成历史卡片在无广场入口时仍显示完成状态、支持查询原操作，并正确提示广场暂不可用。两次提供/释放原 layout 服务，均可跳转已安装、返回会话、重新展开宿主工具详情，并再次移除广场入口；浏览器写请求为零，无页面错误。未调用模型。

早期探针未覆盖 ModuleLoader 切换，未实际注入；修正后一次因返回会话默认折叠工具详情而超时，补充展开动作后完整通过。一次临时运行器因缺少 home 父目录而启动失败，补齐隔离目录后使用新的运行目录重跑。上述失败不计为通过证据。最终运行器及其子进程已结束。

结果与截图为 windows-layout-degradation-result.json / windows-layout-degradation.png；本轮探针 lint 与 client-entry-lifecycle 定向测试（1 test / 10 assertions）通过。此证据只覆盖已安装包中布局服务的独立降级，不证明整个布局包缺失时模块可加载，不替代只读搜索工具降级、真实模型或三平台验收。10.3 保持未完成，待按兼容性规格核对其余入口场景后统一关闭。


### Windows 搜索入口与详情的独立降级

新增 conversation-search-fixture，使用实际 Host 工具执行器完成只读搜索，将真实结果写入明确标记 test-fixture 的合成历史，不调用模型或安装工具。layout-degradation-probe 增加搜索模式，在已安装 Client 的 Cordis 上下文隔离 layout 后，验证没有 SkillHub 广场入口但搜索卡片仍呈现；打开详情得到真实成功的 acknowledgeConversationDetail 响应，版本 Tab 展示夹具内容；关闭抽屉后两轮提供/释放 layout，搜索卡片仍可用。Windows RC.2 实机通过，浏览器写请求为零、无页面错误，探针 lint 通过。

证据 windows-layout-search-degradation-result.json / windows-layout-search-degradation.png。该结果补齐前节对话搜索与详情的布局服务独立性；未更换已安装产品包。仍不代表缺失整个 Client 包的加载、ToolView 槽位反向降级、配置能力缺失提示、真实模型及跨平台验收。10.3 保持未完成，后续须核对这些能力对应的实际产品行为。


### 官方 SlotRegistry 的反向入口生命周期

新增 client-slot-lifecycle.test.ts，通过 ModuleLoader factory 加载候选发行包 dsh-client-ui-renderer 的实际 SlotRegistry，使用其实际 SlotCore、Cordis 服务和声明生命周期；仅 React DOM 渲染入口为空占位，未挂载 DOM。真实 SkillHub Client apply 在 ToolView 声明缺失时仍注册 main/sidebar，随后两轮声明/移除 ToolView 槽位分别产生两个/零个工具贡献，广场组件身份始终不变。配置槽位存在但 configForms 服务缺失时没有配置贡献，服务提供后注册、移除后释放。插件 dispose 后再声明工具槽位也不会重建贡献，证明等待订阅已经清理。

与 client-entry-lifecycle 合并运行：2 tests / 32 assertions 通过；类型检查、lint 与 diff 检查通过。该证据覆盖真实官方注册服务与插件入口的协作，不等同于浏览器里的部分可用提示、实际配置保存状态或整个模块缺包验收。10.3 保持未完成；下一步核对浏览器用户可见的入口能力提示与配置不可编辑状态。


### Windows 配置不可编辑状态验收

新增 config-availability-probe，在实际 Windows RC.2 已安装插件中投影 settings.describe 的真实响应，分别验证 writable=false、缺少 skillhub namespace、正常原始响应。只读状态有明确说明，Registry 输入和保存按钮禁用；namespace 缺失时显示“当前连接无法编辑 Host 配置。”且无表单/保存按钮；正常状态的新页面允许编辑本地草稿并启用保存。每个场景均捕获到匹配的真实响应，零写请求、零页面错误，没有调用 mutate 或模型。未改变 Host 配置或访问政策。

前几轮受宿主首次使用弹窗的条件显示及加载时序影响，在导航阶段超时；最终使用 locator handler 处理实际出现的弹窗，完整三态通过。证据 windows-config-availability-result.json 及对应 readonly/unavailable/restored 截图。lint 与 diff 检查通过。恢复使用新页面，不声称同一页面断线重连自动恢复；部分可用入口的统一提示仍需继续核对，因此 10.3 不关闭。


### 部分可用提示实现与 Windows 实包验证

新增 UiAvailability，跟踪 layout 依赖下的 main/sidebar 槽位、ToolView 槽位及 configForms 依赖下的配置槽位。广场及两类对话卡片通过 useSyncExternalStore 显示缺失入口；另一入口存在时才给出替代操作建议，全部恢复后移除提示。配置入口缺失提示当前来源保持不变，不改变 Host 版本门禁或写入授权。compatibility-design §11 记录该呈现边界。

Linux 与 Windows 原生 check 均通过：184 tests / 2802 assertions，build/typecheck/lint 成功。安装新 tarball 后五项 Host/Client/core/Remote 产物与 Windows 构建逐项一致。Windows 布局服务隔离探针新增部分可用提示断言：入口缺失时提示可继续使用对话卡片；两轮恢复时提示消失，跳转已安装和返回会话正常；移除入口后原操作仍可读。浏览器零写请求、无页面错误。证据 windows-ui-availability-result.json / windows-ui-availability.png。

首次实机运行在创建安装夹具阶段失败，持久状态为 failed/files=failed/recovery=not-needed，未进入浏览器；Host 日志未给出具体错误原因。新的隔离目录中安装夹具及全部浏览器断言通过，但不能据此将首次失败判为已定位。该间歇失败保留为后续安装诊断项。10.3 仍需核对反向入口提示的浏览器表现及其他兼容性要求，不作整体完成声明。


### 已接受操作异常的诊断缺口修复

调查 Windows 首次夹具失败发现 LocalRuntime.submitInstall/Upgrade/Removal 在 receipt.resolve 后仍只用 receipt.reject 消费后台异常；已完成 Promise 的 reject 无效，虽然核心已保存终态，异常没有可观察的诊断。新增共享 executeAccepted 包装，在执行失败时向 Host 标准错误输出固定事件及 operationId/kind/白名单 code，然后保留原异常传播和关闭排空语义。没有修改持久操作格式、Remote 协议、恢复规则或自动重试策略。

operationDiagnostic 仅允许固定 Node 文件系统/网络错误码和已核对的共享 CLI exitCode 类别；不输出 message、stack、details、路径或任意 code。分类测试覆盖敏感字段排除及未知回退；执行测试覆盖 accepted 回执不被后续失败改写、执行仅一次、诊断回调失败不影响关闭、接受前失败直接返回且不误记为已接受操作。完整 check 通过：187 tests / 2823 assertions，构建、typecheck、lint 成功。中英文 README 已说明关联方式、日志边界和不可回补旧异常。

本轮尚未把此 Host 新产物安装到 Windows Profile。原 Windows 失败的具体原因仍未知；下一步以新包复核安装并保留分类诊断，不能将增加日志视为安装故障已修复。


### Windows 新诊断包实机验证

包含 operationDiagnostic/executeAccepted 的新包在 Windows 原生 check 通过：187 tests / 2823 assertions，构建、类型及 lint 均通过。隔离 Profile 安装 tarball 后，五项 Host/Client/core/Remote 产物与该构建逐项一致。正常安装及布局降级/恢复浏览器回归通过，结果 windows-diagnostic-package-navigation-result.json。

新增 operation-failure-fixture，配合 catalog-fixture 的 DSH_FIXTURE_DOWNLOAD_FAILURE=1，在真实 Host 上注入下载 503。工具先返回 accepted，随后查询 failed/files=failed/recovery=not-needed，目标技能目录不存在；同一 callId 返回原 operationId。运行器捕获对应一次诊断事件，并逐字段核验仅 operationId、kind=install、code=NETWORK，不归档原始 Host 日志。结构化结果 windows-operation-failure-result.json。运行器及其 Host/Registry 子进程均已退出；探针 lint 和 diff 检查通过。

该验证证明已接受安装的网络失败可被安全诊断，不代表升级/卸载实机故障注入均已覆盖。此前没有诊断信息的 Windows 间歇失败仍无法归因，不将其等同于本轮人为注入的 503。


### Windows 对话入口缺失时的广场实机验收

新增 tool-entry-degradation-probe，实际 Windows 安装包中通过 ModuleLoader 保留官方 ToolView 模块导出但暂不执行 apply，使 tool.call.toolview 声明与贡献不可用。广场显示对应部分可用说明，仍能打开技能详情、版本内容和已安装 Tab；两轮使用官方 ctx.plugin/dispose 恢复/移除原模块，提示相应消失/重现，目录仍可读取。零浏览器写请求、零页面错误，未调用模型；lint、diff 检查通过。证据 windows-tool-entry-degradation-result.json / windows-tool-entry-degradation.png。

此测试补齐反向 UI 入口生命周期，不代表整个 npm 包缺失可正常加载。进一步核对发现 Catalog 的未配置来源空状态仍固定引导打开配置页，在配置入口同时缺失时会与部分可用提示冲突；此文案分支需要跟随入口状态，10.3 暂不关闭。


### 未配置来源空状态的入口感知修复

Catalog 订阅已有 UiAvailability，在来源未配置且配置入口缺失时说明需先恢复 DSH 插件配置功能，不再要求用户打开当前不可用的配置页；配置入口恢复后同页恢复正常配置步骤。没有引入新的服务状态或改变来源、写入权限与 API。compatibility-design §11 已同步。

Linux 和 Windows 完整 check 均通过：187 tests / 2823 assertions，build/typecheck/lint 成功。新 tarball 已装入隔离 Windows Profile，五项 Host/Client/core/Remote 文件与当前 Windows 构建相同。source-config-degradation-probe 在插件上下文隔离 configForms，将只读 getContext 响应投影为未配置来源；同一页面两轮提供/释放原服务，正确在不可用说明与配置步骤之间切换，且没有残留相反引导。零浏览器写请求、零页面错误，未修改 Host 实际来源，未调用模型。

证据 windows-source-config-degradation-result.json / windows-source-config-degradation.png；运行器及子进程已结束。该结果关闭上一节识别的空状态文案缺口，仍不证明网络断线重连自动恢复。兼容性任务需结合其余门禁证据统一审计，未据单项验证关闭整个交付。


### 兼容性实现任务统一审计

对照 compatibility-design 的支持等级、适配边界及能力表，重新检查当前源码和已有结构化实机报告，并运行当前兼容性定向回归：compatibility、runtime-compatibility、host-package-cache、compatibility-node、runtime-execution、installation-paths、conversation-install-host、client-entry-lifecycle、client-slot-lifecycle、ui-availability，共 36 tests / 1691 assertions 通过。

| 要求 | 当前实现与证据 | 判定 |
| --- | --- | --- |
| 宿主差异不进入安装事务 | compatibility/runtime-compatibility 集中核验；installation-paths、skill-discovery、Host index 的可信会话与 Remote、Client index 的槽位装配承担宿主适配。cli/src/integrations 无 DSH 发行版本分支或 @deepseek-ai 导入 | 实现完成，不增设多版本适配器 |
| 未验证版本与缺包/混装禁止新写 | 当前定向测试；runtime-baseline-missing-client/mixed-client 实机报告均 rejectedBeforePreparation=true，Client 同进程移除/恢复报告对应 matched→unverified→matched | 实现完成；历史报告不宣称与最新 tarball 字节相同 |
| 既有回执读取与写入限制分离 | runtime-execution 测试确认门禁拒绝新安装/升级/卸载/恢复写入，原回执查询保留；m0-controls-compatibility 报告验证按钮禁用和只读查询保留 | 实现完成，依赖门禁不是完整支持声明 |
| 可信项目与用户目标 | installation-paths 校验 Host 映射、规范路径、策略；conversation-install-host 覆盖项目不可用不静默改成用户范围 | 实现完成；远程单用户平台验收另行保留 |
| 双入口及配置独立降级、用户提示 | windows-ui-availability、windows-layout-search-degradation、windows-tool-entry-degradation、windows-config-availability、windows-source-config-degradation 报告；当前官方 SlotRegistry 生命周期测试 | 实现完成；整体包无法加载不保证插件能自行呈现提示 |
| 发现与 Registry 能力分离 | getOperation 在发现失败时返回 unconfirmed，不覆盖成功的文件结果；评论/审核走既有模块能力与 detailTabs，不计为 DSH 版本不匹配 | 当前源码与已有功能验收一致，不宣称模型已实际使用技能 |

关闭 10.2、10.3 的实现任务。0.1、10.1、10.4、10.5 保留：唯一发布基线确认、平台 CI 实际运行及三平台验收、宿主升级/回退与日志兼容仍需各自证据；本地工作流文件存在不等于远程 CI 已运行。5.3/5.4/6.6 等真实技能使用、远程拓扑与模型意图验收继续保留。此前无诊断的 Windows 间歇安装失败仍为未归因事项。


### Windows 未知历史格式的保守呈现

新增 unknown-history-fixture/probe，真实 Windows 安装包分别验证搜索和安装历史的 schemaVersion=999。搜索正文保留真实只读搜索输出；安装正文为形状有效的合成 accepted 回执，不调用安装工具。两种历史在首次打开及刷新重放后都只显示无法识别记录，没有正常结果卡片；没有 openConversationDetail、acknowledgeConversationDetail、getOperation、startInstall、executePlan 或 recoverOperation 请求。证明 Client 不从正文恢复操作身份，不以未知元数据生成可操作卡片。

证据 windows-unknown-search-history-result.json / windows-unknown-install-history-result.json 及对应截图。当前搜索/安装解析器定向回归 12 tests / 23 assertions 通过；探针 lint 与 diff 检查通过。运行器均已退出，没有调用模型或安装工具。8.3 的未知 schemaVersion 和正文隔离部分已留证，preparing/start 流式状态仍需实机证据，暂不关闭整项任务。


### Windows ToolView 三阶段与纯展示元数据验收

核对候选发行包 AssistantStreamFrame、SessionHistoryController 和 ToolCallTree 的阶段投影，新增 tool-phases-fixture/probe。在真实 Windows Host 会话中发出官方 agent/assistant-stream start/chunk 帧，浏览器实际显示搜索/安装的 preparing；浏览器确认后提交 assistant/message 与 tool/call，实际显示 start；再次确认后提交 tool/result，实际显示搜索结果与安装请求未确认卡片。安装结果为合成 not-confirmed，未调用安装工具或模型；搜索内容来自真实只读工具。结果正文刻意不含卡片数据，结果卡片由 presentation meta 正确呈现。

首轮错误等待仅历史回合存在的折叠按钮，虽准备文案已显示仍超时；探针按实时视图调整后全流程通过。无浏览器写请求或页面错误。证据 windows-tool-phases-result.json 及 preparing/start/result 三张截图；探针 lint 与 diff 检查通过。运行器及子进程均已结束。

结合前节搜索/安装未知 schemaVersion 的首次加载和刷新重放、正文不被用于推断操作的实机证据，以及此前实际 accepted 安装结果卡片证据，关闭 8.3。该任务关闭只代表原生 ToolView 阶段及重放契约验收，不关闭 6.6 的真实模型意图、停止等待等端到端场景。


### M0 对话接入协议收口

核对 m0-discovery-result、m2-native-install-tool-result、clean-conversation-install-result、m1-conversation-detail-result、conversation-contract-audit-result 及最新 windows-tool-phases-result。已有真实 Host 工具执行证明搜索与安装注册、可信会话/项目上下文、安装完成及异步操作查询；实际 ToolView 验证原 operationId 查询、复制、已安装跳转、刷新重放与固定操作身份。最新官方流事件夹具补齐 preparing/start/result，并明确未调用模型。

当前 install-tool、search-tool、conversation-install-request、conversation-install-host 回归 10 tests / 99 assertions 通过：拒绝额外参数及伪造调用身份、仅转发 exec.callId/agent 上下文，接受后保存原回执，过期选择不在重试中重解析。源码仍以 accepted 表示已受理，不将工具返回等同于文件完成；结果卡片查询原 operationId。关闭 6.1 的 M0 接入协议验证，6.6 的模型意图与真实停止按钮场景继续保留。

0.1/10.1 暂不关闭：8.1 参考插件的真实共存冲突核验及首版发行源码/产物对应还需收口；现有候选使用精确 RC.2，不因本节验收自动改称稳定版支持。

### 后端接口检查任务收口

本次服务端仅补齐已有 resolve/file/download/版本字段的 OpenAPI 注解和契约测试，没有新增路由、迁移或放开匿名审核。再次读取实际 Surefire XML：SkillControllerTest 13、CliSkillControllerTest 12、RegistryOpenApiSchemaTest 2，合计 27 项零失败/错误/跳过。当前 web 生成 schema SHA-256 与 registry-openapi-result.json 记录一致，生成类型及此前前端 typecheck 的证据仍对应现有文件。

关闭 5.5 的 API/后端对应检查，与已经关闭的 9.3 保持一致。本轮未重新运行 Maven 或部署服务端；复核的是此前成功运行的实际报告和未变化的对应产物，不声明 SaaS 或生产对象存储通过。部署、真实实例及发布验收继续由各自任务约束。


## 参考插件 Loader 标识冲突修正

核对本地保存的参考 npm tarball `@cocofhu/skillhub@0.2.16`，其 `cordis.patch.yml` 与本插件原先均插入 `id: skillhub`。本插件改为 `astron-skillhub`，同步 probe-host 配置 patch、集成测试说明及中英文 README。Host/Remote 服务名与现有操作记录保持原契约；未改动任何已安装 Profile。

新增包测试约束 Loader ID，防止重新占用参考插件标识。`bun test test/package.test.ts` 通过 5 tests / 67 assertions；插件 `bun run typecheck`、`bun run lint` 均通过。本次不涉及业务构建代码变更，包测试使用现有构建产物。此结果只证明配置标识修正与静态检查，不证明两个插件可在候选 Host 同时加载；8.1 保持未完成，仍需检查其他注册项以及干净 Profile 共存行为。已有使用旧 Loader ID 的历史验收不作为新包实机验证。


## 新 Loader ID 的干净包加载与生命周期验收

从当前构建执行 `npm pack --ignore-scripts`，安装到全新隔离 Web Profile，在 Linux / Node 24.17.0 / DSH 0.2.0-rc.2 运行 `probe-host.mjs --probe plugin-lifecycle`。探针验证实际安装包 Client 路径、Loader ID `astron-skillhub` 以及该行 Registry 配置，两轮官方 Bundle 禁用/启用均通过：Host、工具和 Client 移除；旧服务拒绝请求；恢复后来源修订更新、Registry 可读、依赖基线匹配，未增加重复 Loader entry。进程正常退出。结构化证据及包摘要见 [loader-identity-lifecycle-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/loader-identity-lifecycle-result.json)。

完整 `bun run check` 通过：188 tests / 2826 assertions；新增运行入口与生命周期断言之后 `bun run lint` 通过。此次实机测试没有启动浏览器或调用模型，也没有安装参考插件，故 8.1 共存验收仍未完成。

准备环境时首次包管理器因隔离 HOME 下 Corepack 下载失败而退出；指定已有 Corepack 缓存后安装成功。首次直接 `plugin add` 生成的基础 Profile 缺少 clientModules，探针未激活，已停止测试并保留该 Profile。随后按集成指南创建全新 Web Profile 后完成上述验收。这些失败不计为通过结果。


## 固定参考包的 Host 共存验收

在新的隔离 Web Profile 中通过官方插件安装器安装当前候选 tarball 与 `@cocofhu/skillhub@0.2.16`，均禁用安装脚本。Linux / Node 24.17.0 / DSH 0.2.0-rc.2 下运行新增 `probe-host.mjs --probe plugin-coexistence`，将参考包 apiBase/webBase 指向本地夹具。参考包会在加载时请求插件市场分页，夹具不实现该市场接口；不将其市场业务自检计为通过。

两轮本插件 Bundle 禁用/启用通过。每阶段确认参考插件仍只有一个 Loader entry，ID 为 `skillhub`，配置与启用状态不变，Host fiber 存在；`skillhub_search`、`skillhub_install`、`skillhub_list`、`skillhub_uninstall`、`skillhub_plugin_search`、`skillhub_plugin_install` 六个工具对象未被替换，Client 路径未变。本插件 Loader ID 为 `astron-skillhub`，独立工具、Host、Client 的释放与恢复通过。未调用模型、参考插件写工具或真实市场服务。结果见 [reference-host-coexistence-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/reference-host-coexistence-result.json)。

这是 Host 与 Client 模块注册共存证据，不是参考插件浏览器页面可用性或双插件模型选工具验收。8.1 保留未完成，继续确认浏览器注册与视图未替换。此前离线安装尝试因隔离 HOME 中缺少 pnpm 元数据失败，正常安装成功后才执行本次测试；失败尝试未计入通过结果。


## 固定参考包的浏览器注册与视图共存验收

在上述两包隔离 Profile 中启动真实 DSH Web Host，使用 Chromium 执行 `reference-client-probe.mjs`。两个实际 Client factory 均完成 apply；真实插槽中本插件的 `astron_skillhub_search`、`astron_skillhub_install` 与参考包的 `skillhub_search`、`skillhub_list`、`skillhub_plugin_search` 各有且仅有一个注册。本插件 main/panellist 与参考包 footer action 分别存在，广场成功读取本地 Registry 列表。

通过官方插件详情开关两轮禁用/启用本插件：参考包三个工具视图与 `skillhub-plaza` 入口的 StoredEntry 对象保持不变，参考入口持续可见；本插件恢复后广场均可读，无 pageerror。探针只观察 ModuleLoader apply 上下文及实际插槽，不替换组件。结果见 [reference-client-coexistence-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/reference-client-coexistence-result.json)，截图见 [reference-client-coexistence.png](../../../docs/research/dsh-native-plugin-2026-10-12/reference-client-coexistence.png)。lint 通过。

前置失败分别为首次启动预览弹窗未处理，以及禁用响应未完成时过早再次点击开关。补齐弹窗处理、等待 aria-checked 确认为 false、失败后恢复启用状态，最终两轮通过；这些早期失败不作为宿主缺陷或通过证据。测试结束的 Profile 保持本插件启用，测试进程已退出。

结合固定标识、包测试、Host 共存与本节浏览器证据，关闭 8.1。本结论限定参考包 0.2.16 和候选 DSH 0.2.0-rc.2，不声称参考插件所有旧版页面均可用（settings.plugin.item 没有注册项），不涵盖真实模型如何选择两个市场工具、参考市场业务或未来参考包版本。模型行为仍由 6.6 验收。


## 发布包运行依赖验收

加强 `test/local-core-package.test.ts` 的实际 Node 子进程环境：使用解析后的 Node 绝对路径，移除所有大小写形式的 PATH/NODE_PATH/NODE_OPTIONS 继承，将 PATH、HOME、USERPROFILE 限制到测试临时目录，并断言运行时不存在 Bun 全局。tarball 解包目录不含 node_modules，安装流程不能通过 PATH 找到 Bun 或 SkillHub CLI。真实执行完整技能及附属资源安装、事务提交核验、操作日志重新打开与资源内容校验均通过。

`bun test test/package.test.ts test/local-core-package.test.ts` 共 6 tests / 202 assertions 通过；插件目录内 `bun run typecheck`、`bun run lint` 通过。最初从仓库根调用 typecheck 返回脚本不存在，已在插件目录重新运行成功，不将错误命令计为通过。

包测试同时核对所有 exports、patch、许可证与发布文件边界；扫描 Host/core/生成 Remote 的导入，要求相对文件位于 lib 内、裸包导入有运行依赖声明、core 仅依赖 Node 内置模块；实际执行 Client factory，仅允许 React 两个声明 external。构建阶段通过加载路径检查阻断 CLI commands、入口和凭证读取模块。动态 local-core URL 入口另由源码核对及实际 Node 安装覆盖。

结合“新 Loader ID 的干净包加载与生命周期验收”和“固定参考包的浏览器注册与视图共存验收”中，实际安装 tarball 的 Host/Client/Remote 在 DSH 中成功运行，关闭 8.5。该项不替代三平台验收、正式 npm 发布后安装或真实模型交互；平台门禁仍保留在 5.4/10.4。


## RC.2 发行与源码对应核验

实时读取官方 GitHub 发布与 tag API：`dsh-v0.2.0-rc.2` 为 prerelease，对应提交 `639ed015397290b3745d163aafe02ffee4aa3f84`。此前调研提交 `d743267388641bc76f17c45ce8b4c231aed1d32c` 对应 `dsh-v0.2.1-alpha.2`，不能作为 RC.2 产物的源码来源。

核对 DSH 主包、Typert protocol 与 generator 三个精确 npm 版本。读取各自 repository.directory 对应的 RC.2 源码 package.json，包名和版本均一致；下载 npm tarball 并校验 SHA-512 integrity；除安装器可能处理的 package.json 外，将 55 个发布文件与本地实际依赖逐字节比对，全部一致。证据见 [rc2-release-mapping-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/rc2-release-mapping-result.json)。npm 元数据未提供 gitHead 或 attestations，因此此结论是官方发布标签、源码清单与发行包内容的对应核验，不冒充可重复构建或签名 provenance 证明。

结合已固定的 package peers、Node 范围、构建格式及既有实机证据，关闭 0.1 的研发基线固定任务。compatibility-design.md 补充固定组合；10.1 的正式支持状态、10.4 平台门禁和 10.5 升级回退仍保留，不将 RC 改称稳定版或宣称全面支持。


## 安装指南修正与未知日志版本拒绝

中英文 README 的隔离安装流程补齐 `--from-default-profile web` 初始化，使用独立 HOME/USERPROFILE、DSH_HOME、DSH_AGENTS_HOME 和工作目录；环境设置置于子 shell，退出后不影响调用者环境。明确初始化仅用于新 Profile；基础 Profile 自动初始化不能替代 Web Profile。该修正来自此前新 Profile 的实际失败与后续 Web 初始化成功证据，不新增宿主参数。

扩展 tarball 的独立 Node 安装/恢复测试：成功安装并完成操作后，将保留全部字段的持久记录 schemaVersion 改为 2，模拟旧运行时遇到未来日志。重新打开存储，get、listForRecovery、新操作 accept 全部返回 CORRUPT_RECORD，且未调用新操作准备回调；日志字节、日志文件集合、inventory 和技能资源保持不变。此为明确的未知格式拒绝测试，不宣称版本 2 是受支持格式，也不冒充两个已发布插件版本间的回退验收。

`bun test test/local-core-package.test.ts` 通过 1 test / 135 Bun assertions，Node 子进程中的实际文件与拒绝行为断言均通过；typecheck、lint 通过。9.2 和 10.5 仍保留，后续需完成插件包更新/回退全过程和对应日志兼容验证。


## 开发产物替换与回退的历史数据验收

隔离 RC.2 Web Profile 安装保留的旧开发 tarball，使用真实工具分别安装用户级/项目级夹具技能并等待两项操作完成。随后使用官方 `plugin add <tarball> --ignore-scripts` 替换为当前包，重启 Host 查询原会话与操作；再安装保留包并重启。为排除同版本本地缓存导致未实际换包，两阶段各核对安装目录的 Host、core、Client、两个 Remote 产物和 patch，共六文件与指定 tarball 原字节一致。

两阶段均成功解析原会话、读取两项 completed 操作，用户/项目条目均为 present；10 个日志、清单及技能资源文件摘要保持不变。结果见 [plugin-artifact-roundtrip-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/plugin-artifact-roundtrip-result.json)。Node 24.17.0 / Linux / DSH 0.2.0-rc.2，未调用模型；所有本次 Host 与 Registry 测试进程正常退出。探针支持读取当前或保留开发包的实际 Loader ID；共存模式拒绝旧的冲突 ID。lint 在插件目录运行通过。

两个产物都标记 0.0.0-m0，通过内容摘要区分，不能称为两个正式插件版本的升级回退。中英文 README 增加经过验证的替换/回退命令、重启与保留日志要求，并明确该限制。9.2/10.5 的跨版本与待恢复事务验收仍保留；不因本次成功提升正式支持声明。


## 双目标待恢复事务跨开发产物回退

扩展 dual-upgrade-restart.py 的 `--replacement-tarball` 路径。在新的隔离 RC.2 Web Profile 安装当前 tarball，使用真实工具安装用户/项目两个目标，再通过浏览器升级夹具制造仅一个目标目录仍存在的 recovery-required 状态。正常停止 Host 后安装保留的旧开发 tarball，确认六个实际安装产物与旧 tarball 一致，待恢复日志、inventory 和目标存在状态均未改变。

重启相同 DSH 后，浏览器基于原操作执行一次显式恢复，并注入恢复响应丢失。查询确认恢复为 restored，两个目标全部文件 hash 恢复到升级前，inventory 字节一致；旧版本可见，关闭后焦点回归，无 pageerror。结果见 [pending-plugin-artifact-rollback-result.json](../../../docs/research/dsh-native-plugin-2026-10-12/pending-plugin-artifact-rollback-result.json) 和 [恢复截图](../../../docs/research/dsh-native-plugin-2026-10-12/pending-plugin-artifact-rollback.png)。Python 语法检查、diff 检查通过，所有本轮进程退出。

此结果补齐“插件包替换期间有待恢复事务”的实际链路，但两个产物版本号均为 0.0.0-m0；不是不同正式版本的协议迁移证明。本轮没有强杀，没有调用模型，平台为 Linux / Node 24.17.0；强杀及其他平台沿用各自验收边界，不外推。探针失败时不再直接输出原始浏览器日志，以免访问令牌进入公共输出。


## 免费模型真实对话的只读搜索验收

在隔离 Linux / Node 24.17.0 / DSH 0.2.0-rc.2 Web Profile，安装用户指定仓库的 dsh-our-free-model 2.2.0 与 SkillHub 候选包。通过实际免费目录选取 `our-free-model` 提供方下的 `nvidia/nemotron-3.5-lightning:free`，由官方 sessionController 发起一次真实搜索对话；没有直接执行工具或合成会话事件。

实际发生两次上游推理请求和一次 `astron_skillhub_search`，工具成功、会话 completed，无其他工具请求。重启后禁止进一步推理，浏览器打开持久会话，搜索卡片及 document-helper 可见，startInstall 请求为零，无 pageerror。最终用户技能目录与新建项目为空，inventory 未创建，operations 仅包含宿主身份文件。结果见 [真实对话报告](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-readonly-search-result.json) 和 [实机截图](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-readonly-search.png)。测试进程已退出。

本轮使用真实模型与本地 Registry 夹具，不能替代真实 Registry 的模型安装/使用验收。测试设置仅搜索工具可执行的 guard；模型没有尝试其他工具，但不能据此证明未加 guard 的所有行为安全。上游调用限制为最多三次、每次输出上限 2048；实际两次。免费模型插件仅用于隔离验收，不成为 SkillHub 产品依赖。

发现两项后续事项：模型回答暴露内部 selectionRef，需要完善工具说明并重新验证；Linux 截图中文字体缺失，不能用作正式产品设计图。6.6 的其他真实模型交互、6.5 的完整设计图与交互验收仍未关闭。


## 内部引用展示指引与中文实机截图修正

搜索与安装工具描述明确内部引用仅用于工具调用及关联，不展示或要求用户复制；搜索结果说明同步该规则，用户回答应以技能坐标、版本、摘要和状态为主。保留结果 JSON、schema、presentationMeta 和执行行为，不删除后续安装需要的 selectionRef。既有搜索/安装工具测试 6 项、46 条断言通过，插件 typecheck 与 lint 通过。此处是模型指引，真实模型修正效果尚未复验，不能声称已强制屏蔽内部字段。

中文方框由隔离 HOME 下缺失用户字体搜索路径引起。在本轮临时浏览器环境的 Fontconfig 中显式引用本机已有中文字体后，重开原真实会话并截图；卡片和中文文字正常、无 pageerror、零安装提交。没有修改产品字体或会话内容，也没有再次推理。[可读实机截图](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-readonly-search-readable.png) 仍保留原模型回答中的 selectionRef，作为修正前证据，不充当修正后验收或正式设计稿。


## 真实搜索历史重启后的详情缺口

在前述真实模型会话的隔离 Profile 重启 Host，禁止额外推理后打开历史搜索卡片。卡片正常显示；详情抽屉返回 context-stale，关闭后焦点回到入口，零安装提交、无 pageerror。此结果证明旧引用受到保护，但不能证明历史详情重新查看功能完成。当前 UI 的“重试”再次发送已清空的 selectionRef，无法满足 conversation-design.md 所规定的主动重新查看、签发新引用流程。保留原需求，作为待修复缺口；修复需从可信持久会话历史恢复候选并重新核验来源/版本，不能直接信任客户端坐标或复用旧安装授权。

证据：[结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-history-stale-detail-result.json)、[抽屉截图](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-history-stale-detail.png)。仅验证已安装候选的历史读取行为，未重新调用模型，未安装技能。


## 历史详情显式重开的修复与实包验收

补齐会话作用域 reopenConversationDetail，Host 通过官方 sessionQuery 核验当前会话持久搜索调用及结果，坐标/精确版本来自历史，校验来源并重读详情，签发新引用。原引用不恢复，展示确认不授权安装。Client 在引用失效时提供显式重新查看，成功显示原/当前来源、精确版本与正常详情；禁止焦点/重连事件自动重复重开。普通网络失败仍可重试。

新候选包通过官方安装命令替换后重启，六个安装产物与 tarball 字节一致。使用先前真实模型产生的持久历史卡片，先确认旧引用失败且没有自动重开，再点击重新查看，确认新引用不同于原引用、展示确认使用新 token、版本与来源可见、关闭焦点恢复、原卡片不变。无安装提交、无 pageerror、未新增 inventory 或操作日志；本轮禁止模型请求。证据：[实包结果](../../../docs/research/dsh-native-plugin-2026-10-12/history-detail-reopen-result.json)、[详情抽屉](../../../docs/research/dsh-native-plugin-2026-10-12/history-detail-reopen.png)。

插件全量 192 tests / 3079 assertions 通过；build、typecheck、lint 通过。新增 Host 测试覆盖历史调用归属、失败/重复/继承结果、schema、来源、取消及 Agent/项目变化等边界。6.7 收口；完整模型安装、资源使用及其他平台仍按原任务验收。


## 历史重开复核与指南同步

独立只读复核未发现 6.7 的权限或版本阻塞问题；12 项历史/引用/工具定向测试、92 条断言及 typecheck、lint 通过。已知限制：官方 readSession 不接受 AbortSignal，关闭抽屉会阻止后续详情请求和引用签发，但已经开始的历史读取需自行返回；未宣称底层读取立即取消。

中英文使用指南补齐主动重新查看流程及验收边界，区分真实模型只读搜索、独立 Registry 验证和未完成的模型安装/资源使用。未知历史格式探针的禁止调用列表补入 reopenConversationDetail，避免新增接口逃过观察；本轮仅完成该探针语法/lint 检查，未重新运行未知格式浏览器用例。


## 剩余交付核对与禁用指南

按当前 tasks 的未关闭项重新核对，模型安装/资源使用、macOS、独立远程 Host、远端 CI 和发布包验收仍保留。中英文指南补齐经过原有共存实机验证的 Plugins 开关禁用/启用步骤，不编造 CLI 禁用命令；说明禁用不移除技能或日志，依赖变更仍需完整重启。验收总表同步已完成的模型只读搜索、历史详情重开、并发/故障归档及开发产物回退证据，未提升正式支持声明。

CI 增加隔离 Web Profile 初始化与真实 Host 冒烟入口；本轮 Linux 无缓存验证尚在执行，完成前不记为平台通过。脚本定向验证覆盖环境白名单、候选唯一性、私有日志及子进程树超时回收，具体最终结果随后回写。


## 隔离 CI 初始化的网络失败与清理复核

Linux 全新入口在 install-host 阶段失败：首次五分钟有界安装达到期限并清理；调整冷安装期限后的尝试遇到 npm ECONNRESET，未进入 Host 初始化。复用同一隔离目录公开包缓存的恢复尝试也以非零退出，不能作为完整入口通过。原失败与恢复尝试分别保留，见 [初始尝试摘要](../../../docs/research/dsh-native-plugin-2026-10-12/ci-native-smoke-initial-attempts.json)；摘要不含原始 npm/Host 日志。

只读复核发现 Windows taskkill 的非零退出未处理及成功父进程退出后的孙进程回收缺口，正在修复；当前定向 Linux 测试通过不能替代这两项 Windows 证明。因此 10.4 保持未关闭，不把已有 YAML 配置视为三平台 CI 验收。


## Windows CI 进程清理修复验收

隔离入口增加 Windows Job Object 包装：命令挂起创建、加入带 kill-on-close 的 Job 后恢复执行，父命令退出后清理 Job 中所有后代；结构化 JSON 传参并处理 Windows argv 引号，PowerShell 按 UTF-8 读取配置。外层同时处理 taskkill 非零退出，并设置独立清理期限，失败不能无限等待。无新增包依赖。

实际 Windows Node 24.16.0、正式隔离环境白名单下，复杂参数（含中文、空格、引号及尾反斜杠）、成功父退出后的后代清理、超时整树清理、模拟终止器失效后的有界失败与 Job 清理四项通过。结果见 [Windows 清理报告](../../../docs/research/dsh-native-plugin-2026-10-12/windows-ci-process-cleanup-result.json)。Linux 定向 6 tests / 22 assertions、typecheck、lint 通过。此结果证明测试进程包装行为，不代表 Windows DSH 冷安装、三平台远端 CI 或完整验收通过。前述 npm 网络失败记录保留，10.4 继续未关闭。


## Windows 历史详情重开的实包验证

在新的 Windows 临时 Profile 中，通过官方离线 plugin add 安装当前 tarball，复用本机既有原生依赖、公开包缓存与浏览器，没有下载大包或改动原 Profile。Windows Node 24.16.0 / DSH 0.2.0-rc.2 持久化合成搜索工具事件后关闭并重启 Host，运行统一 history-detail-reopen 浏览器探针通过。旧引用先失效，显式重新查看后取得新引用并确认展示；原卡片、来源/版本、安装控件、关闭焦点均符合预期，无安装提交、无 pageerror，保护文件不变，剩余进程为空。安装 Client 摘要与当前工作区产物一致。

证据：[Windows 结果](../../../docs/research/dsh-native-plugin-2026-10-12/windows-history-detail-reopen-result.json)、[Windows 抽屉截图](../../../docs/research/dsh-native-plugin-2026-10-12/windows-history-detail-reopen.png)。此处事件由夹具产生，没有模型调用，不能替代模型端到端验收或无缓存 CI 初始化。

历史重开与 CI 清理改动合并后的本地 `bun run check` 全部通过：构建、类型检查、lint、198 tests / 3101 assertions，51 个测试文件，0 失败。见 [本地检查摘要](../../../docs/research/dsh-native-plugin-2026-10-12/local-check-after-history-and-ci.json)。


## npm 下载链路诊断与平台验收范围调整

macOS 按用户决定延后验证，不阻塞本轮工作，也不计为已通过或已支持；任务、验收与发布指南已同步。

隔离安装环境继承 HTTP/HTTPS 代理，npm Registry 不在 NO_PROXY 中。原报错的两个公开包通过代理重新下载均为 HTTP 200；绕过代理均在 8 秒连接超时。相同隔离配置的 npm ping 首次 ECONNRESET，复测继承代理与显式代理均成功，直连 ETIMEDOUT。因此确认代理链路存在间歇连接重置，尚无法区分代理本身与其上游原因；关闭代理不能解决当前连接问题。未修改全局配置，未重跑完整冷安装。见 [诊断摘要](../../../docs/research/dsh-native-plugin-2026-10-12/npm-network-diagnostic.json)。


## 真实模型搜索复测与多候选前置失败

使用已授权匿名免费模型，在新的隔离 Profile 中执行当前插件。搜索复测实际使用 2 次模型请求、1 次原生搜索工具调用，正常结束；助手文字未暴露 selectionRef/sourceRevision，未新增技能文件。见 [搜索复测](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-search-reference-retest.json)。此项证明该次输出符合要求，不保证所有模型或后续输出。

多候选用例尚未进入第二轮歧义安装输入：模型在首次搜索阶段调用 astron_ability_search（非本插件搜索工具），随后尝试 bash，均被只读守卫拒绝。该场景实际消耗 10 次请求；共享持久化预算达到本批授权上限 12 后，网络守卫拒绝继续推理，回合以 TRANSPORT 结束。无安装、无命令执行、无新增技能文件。不能计为歧义选择通过，也不能归因于安装功能故障。见 [失败证据](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-ambiguity-precondition-failure.json)。

本批额度已用完，不再调用模型。临时观察器已保留原执行版本，并准备首次越界工具调用后立即取消回合的版本；该修改仅通过语法检查，尚未以真实模型验证。明确安装、资源读取、歧义选择等模型验收保持未完成。


## 开发包维护指南验收

按任务 9.2 的原始范围复核双语 README：构建与 tarball 生成、官方 Web Profile 初始化及 plugin add 安装、目标包替换、Plugins 开关禁用/启用、保留包回退均已有具体步骤。补齐中文版实际 npm pack 命令，英文版同步双目标待恢复事务的已验证结果。实际执行 npm pack --ignore-scripts --pack-destination 生成开发包，并从 tarball 读取两份 README，确认命令与修正内容均已包含。

构建依据 [本地检查](../../../docs/research/dsh-native-plugin-2026-10-12/local-check-after-history-and-ci.json)；替换与回退依据 [产物往返](../../../docs/research/dsh-native-plugin-2026-10-12/plugin-artifact-roundtrip-result.json) 和 [待恢复事务回退](../../../docs/research/dsh-native-plugin-2026-10-12/pending-plugin-artifact-rollback-result.json)；禁用/启用依据 [两轮浏览器验证](../../../docs/research/dsh-native-plugin-2026-10-12/reference-client-coexistence-result.json)。这些证据覆盖固定 RC.2 上的开发包维护操作，关闭 9.2。先前记录把跨正式插件版本验收也附加到 9.2；该项继续由 9.5/10.5 跟踪，不重复阻塞已验证的指南任务，不提升正式支持范围。


## Linux 隔离入口低并发恢复通过

保留此前失败证据，在同一隔离目录复用已获取的公开包缓存，仅为本次子进程设置 npm_config_maxsockets=3，未修改全局 npm 或代理配置。DSH 依赖安装、官方 Web Profile 初始化、候选 tarball 安装、runtime-baseline 与 plugin-lifecycle 探针全部通过；安装的五个运行产物与当前工作区构建逐字节一致。此次未调用模型。见 [结果摘要](../../../docs/research/dsh-native-plugin-2026-10-12/ci-native-low-concurrency-result.json)。

该结果证明隔离入口在当前网络条件下可完成，以及降低并发后的本次恢复成功；由于复用了失败尝试的缓存，不能称为空缓存首次安装通过，也不能据此认定网络问题的唯一根因。远端 CI 和 Windows 同入口验证仍未完成，10.4 保留。


## 冷安装入口并发限制与工具契约文档核对

隔离 smoke 入口的 npm install 固定 maxsockets=3，仅影响该安装子进程，不修改全局配置或模型调用预算。进程隔离/清理/参数传递等定向测试 6 pass、22 assertions，lint 通过。另启动独立空缓存入口，最终结果另行记录，不将启动或下载进度作为通过。

依据 search-tool.ts、install-tool.ts、Client 插槽注册和解析代码，修正 conversation-design.md：安装回执不直接携带文件/发现终态，Client 通过 getOperation 查询；搜索调用标识来自宿主 ToolView props.callId，而非搜索 envelope；presentationMeta 到 block.meta 的映射写明，并移除这些已实现接口的拟议表述。真实模型安装与完整交互验收仍未完成。


## 当前真实搜索会话的浏览器重放

仅重放本批已完成的真实搜索会话，网络守卫禁止推理；请求账本仍为 12，没有新增模型调用。搜索卡片可见，页面可见文字不含 selectionRef；从卡片打开历史详情、显式重新查看、关闭及焦点返回均通过，安装提交 0、pageerror 0。新截图加入 conversation-design.md，区分实机截图与概念图，并保留完整交互验收未完成状态。见 [搜索页面结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-search-replay-result.json) 和 [详情结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-retest-history-detail-result.json)。


## 空缓存首次安装结果

当前正式 smoke 脚本在全新隔离目录、空 npm 缓存中运行，maxsockets=3。DSH 依赖获取持续推进，但达到 600000ms 安装期限，返回 DEADLINE_OR_CANCELLED，尚未进入 Web 初始化。日志未出现 ECONNRESET；不能将此超时归为此前的连接重置，也不能将已有缓存恢复成功替代本次结果。本次进程已清理，没有残留 Node/Bun 测试进程。见 [空缓存结果](../../../docs/research/dsh-native-plugin-2026-10-12/ci-native-empty-cache-result.json)。

未自动延长 CI 期限或启动相同下载重试；首次安装耗时需要进一步分析，平台 CI 任务继续保持未完成。


## 搜索卡片跳转技能广场

补齐原产品契约中的“打开技能广场”：保留原关键词，切到浏览 Tab，清除其他详情/已安装跳转状态；读取当前来源并确认原会话仍存在，来源 URL 不匹配或广场入口不可用时留在卡片提示。此入口仅重新浏览，不消费历史选择引用或提交安装。搜索结果类型移到纯数据解析模块，避免客户端导入 Host 工具声明引发 Context.sessions 类型冲突。

完整 bun run check 通过（198 tests / 3101 assertions），官方 tarball 安装后的真实 RC.2 浏览器验证打开、保留关键词、返回会话、再次打开均通过；通过拦截只读 getContext 响应模拟来源变更并验证拒绝导航。0 安装提交、0 页面错误、未调用模型。见 [导航结果](../../../docs/research/dsh-native-plugin-2026-10-12/search-catalog-navigation-result.json) 和 [广场截图](../../../docs/research/dsh-native-plugin-2026-10-12/search-catalog-navigation.png)。

## 明确安装模型验收发现的回执指引缺口

追加授权下，模型实际调用 astron_skillhub_install，将 @demo/document-helper 1.2.0 安装到隔离项目。日志 phase=completed，文件与附属资源符合夹具。随后模型依据旧回执中的“Query the operation”尝试 bash；首次越界守卫拒绝并取消回合，未执行命令。该回合不能记为完整通过。见 [实际安装与后续失败](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-install-followup-failure.json)。

修正安装工具说明与 accepted 文案：明确没有操作查询工具，最终状态由 DSH 安装结果卡片展示，不引导模型使用 shell、读文件或虚构工具检查状态。3 项工具测试 / 31 assertions、typecheck、lint、build 通过。文案修正后的真实安装回合仍须复测；不把静态提示视为强制模型约束。

## 冷安装低并发策略复核

空缓存记录包含 613 次元数据获取与 291 次包归档获取，未出现 ECONNRESET；DSH 官方包本身包含 82 项直接运行依赖。日志记录的部分完成耗时超过 270 秒，可能包含并发排队，不能直接解释为单个包传输耗时。固定并发 3 的空缓存测试未在期限内完成，因而移除上轮加入 CI 入口的强制并发限制，恢复 npm 默认值；保留已通过的隔离、超时与进程清理逻辑，以及本地低并发恢复成功的历史证据。未再次启动同样下载。


## 真实模型加载已安装技能与附属资源

复用上项实际安装成功的隔离项目，创建新 DSH 会话并使用相同匿名免费模型，提示仅要求使用 document-helper 并按技能说明读取 references/note.md，没有给出资源内容。模型实际调用原生 skill 工具成功加载安装目录中的技能说明，再用 read 读取该技能的资源文件；最终回答为 Native installation resource，与磁盘字节内容一致。守卫只允许该技能与其两个文件，未拒绝任何调用，回合 completed。该场景消耗 3 次请求，连同前项安装回合，本追加批次共 5 次，每次输出上限 2048 tokens。

见 [资源使用证据](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-installed-resource-result.json)。这是本地夹具的真实模型加载与使用证明，不替代真实 Registry 技能使用、歧义选择、Stop 等其余验收；安装回执文案修正后的完整回合仍待复测。


## 安装修正文案后的真实模型回合

新隔离 Profile 通过官方 plugin add 安装含修正文案的 tarball，复用公开依赖缓存离线安装；核对安装后的 Host 产物包含新指引。初次准备受到 Corepack 网络请求、复制 Profile 的旧 store 地址及离线元数据缺失影响，分别通过指定已安装 pnpm、复用原公开 store 和公开包元数据解决，未改产品代码或个人配置。

模型实际仅调用一次 astron_skillhub_install，接受后回答安装已开始并引导查看结果卡片，回合 completed；没有 shell 或虚构工具调用，也未直接声称安装成功。共 2 次免费请求。磁盘资源内容一致，操作日志 completed。见 [明确安装结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-explicit-install-result.json)。

## 产品浏览器与带资源技能验收

独立只读审计逐项核对 tasks 5.3 原文，关闭该项。导航依据 clean-catalog-navigation-result.json / windows-catalog-navigation-result.json；Tab 模式和配置持久化依据 native-tab-config-result.json / live-tab-config-result.json；更新检查依据 m3-update-matrix-result.json；安装、升级、卸载及资源完整性依据 clean-complete-lifecycle-result.json / windows-mobile-lifecycle-result.json；以上报告均位于 docs/research/dsh-native-plugin-2026-10-12。生命周期报告的 upgrades:2 统计 executePlan 两次，分别为升级和卸载，不是两次升级。

带资源技能的实际模型安装依据本节明确安装报告，加载与资源使用依据 real-model-installed-resource-result.json。二者不是同一轮执行；资源使用复用了此前真实安装成功的隔离项目。5.3 原文不要求真实 Registry 或所有操作在同一会话执行；acceptance §7 独立的真实 Registry 技能使用仍未完成，不能用本地夹具替代。平台/远程 Host、剩余模型场景及正式发布验收继续保留。

安装会话浏览器重放通过：原操作完成与发现状态可见，Host 重启后的来源修订不匹配时当前清单明确无法核验；0 安装提交、0 pageerror、0 新模型请求。初次探针错误要求重启后直接显示当前清单，按既定历史契约修正断言后通过，未修改产品行为。见 [卡片结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-install-card-retest-result.json)，截图已加入对话设计文档。


## 多候选歧义选择的真实模型失败与修正

首次使用真实搜索返回 20 个同名候选，再输入“装那个文档技能”。模型自行选择引用并尝试安装，被只读测试守卫拒绝并立即取消；没有文件写入。这是实际失败，不把测试守卫当产品能力。见 [失败记录](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-ambiguous-selection-failure.json)。

补强搜索/安装工具说明及搜索渲染提示：显式安装意图不代表目标唯一；多个候选匹配时，必须先询问命名空间/slug 或可唯一定位的结果编号，不自行选择第一项或任意引用。完整 build/typecheck/lint 与 198 tests / 3110 assertions 通过。原测试逐字比较全部回执文案导致首次检查失败，改为核验保存的结构化结果与关键行为指引后完整检查通过。

官方安装新 tarball 后用相同提示复测，模型正常完成搜索，并在第二轮要求用户指定命名空间或编号。只有搜索工具调用，无安装调用、无守卫拒绝、无新增技能文件；修正后 3 次请求，连同失败用例本批共 6 次。见 [通过记录](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-ambiguous-selection-result.json)。该结论限于本次模型用例，Host 无法强制判断自然语言意图，不声明提示可保证所有模型行为。

Stop 按钮验收新增 conversation-stop-probe.mjs，语法及 lint 已通过；它等待真实会话和 Registry 请求，通过官方 Stop generating 按钮触发取消，不生成会话事件。接受前/后的实际模型运行尚未执行，6.6 继续未关闭。


## 官方 Stop 按钮与安装受理边界

使用真实免费模型和官方 DSH 浏览器 Stop 按钮完成两类验证，本批共 6 次请求，每次输出上限 2048 tokens。受理前在 resolve 延迟期间停止，会话 aborted；等待 31 秒后项目、用户和 agents 范围没有新增技能，inventory 未创建，操作记录为零。受理后先确认 accepted 再点击 Stop，会话 aborted，原操作仍完成：1 个 operation、1 个 inventory item/target、3 个目标文件；停止对话没有取消已经受理的文件事务。

随后以禁止模型请求的模式重启 Host，打开同一历史会话，通过两次 getOperation 查询确认原 operationId 已完成；无重复安装提交、无页面错误。见 [完整结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-browser-stop-result.json)、[受理前停止](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-stop-before.png)、[受理后停止](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-stop-after.png)、[历史查询](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-stop-after-history.png)。

受理后首次浏览器断言因官方停止后自动折叠工具记录而超时，探针已补充展开动作；本轮采用实际 Stop、落盘核对及独立历史查询的组合证据，未重新运行修正后的完整模型探针。前两次尝试分别因模型选错范围及测试服务端口冲突失败，均未计为通过。配置的模型响应延迟未触发。测试进程均已退出。


## 真实 Registry 技能的模型资源使用

复用此前从真实 Registry 安装的 global/zero-slop@2.10.2，在隔离 Profile 创建新模型会话。实际模型依次调用 skill 加载 zero-slop、read 读取安装目录内 references/tells.md，并按资源中的分类分析测试文案；回合 completed，无工具拒绝、无脚本执行。九个原始包文件逐一核对 SHA-256，与此前真实 Registry 安装证据一致。见 [模型资源使用结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-registry-model-resource-result.json)。

本次成功提示明确提供资源绝对路径。此前仅提供相对路径时，模型错误地读取项目根目录下的资源路径，被守卫停止；见 [路径解析失败记录](../../../docs/research/dsh-native-plugin-2026-10-12/real-registry-model-relative-path-failure.json)。因此本结果证明指定资源路径时的真实技能加载与使用，不证明模型可以稳定自行解析任意技能的相对路径。此前尝试还包括脚本调用被拒绝、输出额度耗尽和只读 glob 被过窄守卫拦截，失败记录均保留。

成功用例 3 次请求；当前追加批次实际 5/6 次，本测试根目录累计 10 次，每次上限 2048 tokens。Registry 在本次使用时离线，未重新下载或重新安装；安装来源证据与本轮使用证据分开记录。不以模型回答中的自述替代工具事件与文件校验，不把文案分析质量视作已完整评估。


## 真实模型安装历史与失效记录清理

复用官方 Stop 验收中真实模型安装的会话和隔离项目，禁止新增模型请求。将 document-helper 的完整安装目录移动到扫描范围外的备份目录，保留原 inventory。原对话卡片仍能查询原 operationId 和历史完成结果；已安装面板实际显示“安装目录缺失”，详情提供独立的失效记录清理确认。

第一次取消确认，startInstall/executePlan 写请求均为零；再次明确确认后只有一次 executePlan，操作类型为 remove-stale-record，终态 stale-record-removed。最终 inventory.items 为空；原安装与清理共两个日志记录，原安装完成事实未改变。三个备份文件的完整集合及 SHA-256 与安装后的证据逐一一致，没有创建文件隔离目录，没有重新安装，没有页面错误，本轮模型请求为零。

见 [完整结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-stale-history-result.json)、[清理确认](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-stale-confirmation.png)、[清理后原历史卡片](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-stale-history.png)。Host 重启后来源修订变化，历史卡片按既定契约不核验当前清单；本轮当前缺失状态由已安装页面独立核验，不宣称历史卡片直接展示了 missing。备份保留在隔离测试目录，测试 Host 和 Registry 已结束。

关闭详情另有 history-detail-reopen-result.json 的真实模型历史证据：用户显式重新查看后关闭，焦点恢复，原卡片保留，安装提交与新增操作均为零。因此关闭详情和失效记录两项已有对应证据；安装受理后断开浏览器再重开的完整时序继续核验，tasks 6.6 保持未关闭。


## 真实模型安装期间浏览器断线重开

使用新的隔离 Profile、工作目录和真实免费模型，下载夹具延迟 30 秒。模型调用一次 astron_skillhub_install；浏览器看到 accepted 卡片且目标文件仍不存在时，关闭整个浏览器进程，保持 Host 运行。启动全新浏览器进程并打开原会话，卡片关联同一个 operationId，原安装完成后查询成功。没有点击 Stop，没有取消请求，没有页面安装提交或页面错误。模型正常 completed，无守卫拒绝；实际 2/6 次免费请求，每次上限 2048 tokens。

落盘核验为一条 completed 安装操作、一个 inventory item/target、三个文件；SKILL.md 和附属资源的哈希与既有夹具证据相同。测试 Host、Registry 和浏览器进程均已退出。首次归档断言错误地假定资源正文末尾有换行，改为与既有夹具哈希比较后通过，未修改产品或资源。见 [完整结果](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-disconnect-reopen-result.json)、[重开后的原操作](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-disconnect-reopen.png)。本用例证明浏览器断开/重连，不扩展为 Host 崩溃、网络分区或远程部署验证。

## 对话交互任务 6.6 验收对应

以下均为研究目录中的实际报告；模型用例与对原模型历史进行的无推理浏览器操作分别注明，不使用合成对话替代模型意图验证。

| 要求 | 证据 | 结论及边界 |
| --- | --- | --- |
| 只搜索不写入 | real-model-readonly-search-result.json | 真实搜索调用；无安装提交、清单、技能文件或操作日志。内部引用曾出现在自然语言回答，后续工具指引修正另行记录，不将提示当强制约束 |
| 明确安装 | real-model-explicit-install-result.json、real-model-install-card-retest-result.json | 真实安装工具受理，文件和操作完成；卡片展示持久终态，无重复提交 |
| 歧义选择 | real-model-ambiguous-selection-result.json | 真实多候选下要求明确选择，无安装调用；保留修正前失败，不宣称所有模型都保证遵循 |
| 关闭抽屉 | history-detail-reopen-result.json | 真实模型历史显式重开后关闭，焦点恢复，卡片保留，零写入 |
| 取消工具等待 | real-model-browser-stop-result.json | 官方 Stop 受理前零写入、受理后原事务继续；后者为实际停止与独立历史查询组合证据 |
| 断线重开 | real-model-disconnect-reopen-result.json | 受理后关闭浏览器，新进程重开同一会话/操作，单次安装完成 |
| 失效记录 | real-model-stale-history-result.json | 真实安装历史保留；当前缺失通过已安装页核验，取消零写入，确认只清理记录，备份完整 |

据此关闭 6.6。本结论限于已执行的 Linux/DSH RC.2 组合及上述模型场景，平台出口、完整视觉交付、远程 Host 和发布验收仍按各自任务执行。


## 对话界面图与整条候选交互收口

逐张核对设计文档中的实际 DSH 截图，发现搜索图缺少后来增加的“打开技能广场”，且当前候选条目只有末尾按钮能打开详情，与整条可访问打开的设计要求不一致。将每个候选改为单一原生 button，覆盖名称、坐标、版本和摘要；保留明确动作文字，使用描述关联提供完整候选身份，不嵌套交互控件。

新 tarball 通过官方离线 plugin add 安装，Host/Client/共享核心产物与 tarball 和当前构建逐字节一致。真实模型历史无推理浏览器验证通过：点击候选名称、Enter 打开、Escape/关闭恢复原条目焦点；390px 下候选与抽屉无横向内容溢出，键盘关闭恢复焦点；跳转广场保留关键词，返回及再次打开通过；来源不匹配拒绝跳转。零安装提交、零页面错误、零新增模型请求。build/typecheck/lint 与 198 tests / 3110 assertions 通过，更新后的浏览器探针单独 lint 通过。

见 [实包报告](../../../docs/research/dsh-native-plugin-2026-10-12/search-whole-card-result.json)、[当前搜索卡片](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-search-catalog-entry.png)、[390px 搜索卡片](../../../docs/research/dsh-native-plugin-2026-10-12/real-model-search-card-mobile.png)。宽屏及窄屏截图已实际查看。设计分册保留详情抽屉图，补充安装卡片下半部分与当前记录图；明确两张安装图来自不同验收会话，宿主工具区可滚动，不把半张截图宣称为完整卡片。原失败/旧截图作为历史证据保留。

结合 history-detail-reopen-result.json 的抽屉实机交互、real-model-install-card-retest-result.json 和 real-model-disconnect-reopen-result.json 的安装结果实机交互，以及已关闭的 6.6 场景，关闭 6.5。该项为既定对话界面图与交互交付，不扩展为完整无障碍认证或所有平台视觉验收。中英文指南同步已完成的模型场景、Linux/Windows 分项证据、macOS 后置及未发布边界；5.6 的最终包与平台证据仍随其他交付门禁完成。


## 当前候选包清单与发布身份校验

对 search-whole-card 实机验收使用的实际 tarball 逐项审计：共 60 个文件，仅包含 package.json、双语 README、许可证、Bundle patch 和 lib 产物；归档路径不含越界分段。五个 Host/Client/共享核心/Remote 运行产物与当前构建字节一致。清单记录逐文件大小、SHA-256 和候选整体 SHA-512，见 [候选包报告](../../../docs/research/dsh-native-plugin-2026-10-12/candidate-package-audit-result.json)。本检查是内容范围和身份校验，不宣称完成敏感信息或依赖漏洞的全面扫描。

读取 tarball 内实际 manifest，在本地调用发布身份入口：匹配 dsh-v0.0.0-m0 的 tag 上下文、publish=false 通过；private=true 的 publish=true、CLI tag、分支上下文均拒绝，拒绝不写 GitHub 输出。未创建 tag、推送、执行远端 CI 或调用 npm publish。

新增 release-notes.md，明确开发候选身份、当前功能、配置与历史语义、Registry/SaaS 边界、模型资源路径限制和未完成发布门禁；阅读入口及发布指南已关联。9.5 保持未关闭，候选包清单不能替代干净安装、平台矩阵或发布后的真实包检查。独立远程 Host 连接信息已向用户请求；在该环境提供前不将本机 WSL 结果替代远程验收。


## 当前候选的 Windows 搜索条目与窄屏回归

使用独立 Windows 测试目录、原生 Node 24.16.0 和 DSH RC.2，通过官方 plugin add 安装当前候选 tarball。公开依赖复用既有离线 store；复制 Profile 的 node_modules 因旧 virtual-store 绝对地址被 pnpm 拒绝，已保留该副本并由官方安装器重新生成依赖目录。随后测试 Registry 因搬移后相对导入路径不匹配而启动失败，修正临时夹具的 fflate 路径后复测。两次准备失败均保留，不计为通过。

实际 Host 执行搜索工具，将真实结果写入明确标记为测试夹具的持久历史；正常停止并重启 Host 后，通过浏览器验证整个候选条目点击、Enter 打开、关闭恢复焦点、390px 候选与抽屉内容无横向溢出、窄屏键盘关闭恢复焦点，以及广场跳转保留查询、返回/再次打开、来源不匹配拒绝跳转。所有场景通过，安装提交为零、页面错误为零。未调用模型，不把合成持久消息称为模型验收。

五个运行产物与 tarball 及当前构建字节一致。受保护的工作区、用户目录、agents 和操作日志范围在浏览器操作及 Host 停止后保持一致；结束后按测试目录筛选实际 Windows Node 进程，剩余为零。见 [当前候选报告](../../../docs/research/dsh-native-plugin-2026-10-12/windows-final-search-result.json)、[宽屏截图](../../../docs/research/dsh-native-plugin-2026-10-12/windows-final-search-card.png)、[窄屏截图](../../../docs/research/dsh-native-plugin-2026-10-12/windows-final-search-card-mobile.png)。窄屏截图已查看。

此项补齐最近搜索条目改动的 Windows 实包回归，不替代空缓存依赖下载、远端 CI、完整平台业务矩阵或独立远程 Host。5.4、10.4 等剩余门禁继续开放。


## 双语指南与交付边界审查

逐项对照当前 package.json、发布/check 工作流、双语 README、release-guide 和 release-notes。两份指南均具备构建/实际打包、隔离 Web Profile 安装、替换/回退、Plugins 开关、Registry 与可选 Tab、原生对话、升级卸载、原请求查询及显式恢复说明。已区分 private 开发包与发布包、原操作与当前记录、文件安装与模型使用、浏览器断线与 Host 重启、开发产物回退与正式版本兼容。包内的仓库文档相对链接不能作为 npm 随包内容，英文指南已有明确说明。

当前包清单、Linux 和 Windows 的最近改动实包报告已关联；macOS 后置、远程 Host、远端 CI、SaaS 和发布后验证保持未完成，没有将它们写成支持声明。四份交付文档的本地链接和代码围栏检查通过；manifest 仍为 private，engines 与指南一致。关闭 5.6 的指南更新与审查交付，不关闭其余环境和发布门禁。

当前 Git 状态确认 check-dsh-plugin.yml、release-dsh-plugin.yml 与 plugins/dsh 仍为本地未跟踪内容，不能直接调度远端工作流来验证本工作树。未执行提交、推送、创建 tag 或发布。独立远程 Host 连接信息仍待用户提供。剩余条件统一列在 acceptance.md“剩余交付门禁”，不通过重复已经通过的本地测试代替。


## Draft PR 首轮 CI 与跨平台测试夹具修正

已获授权提交并推送功能分支，创建 [Draft PR #956](https://github.com/iflytek/skillhub/pull/956)。[首轮 DSH 工作流](https://github.com/iflytek/skillhub/actions/runs/38688617510) 未通过：Linux 唯一失败为测试依赖 readdir 的未保证顺序；Windows、macOS 共享核心测试以未经 realpath 规范化的临时目录调用要求 canonical 根目录的原生接口，触发 target-root-changed / target-alias。修正测试根目录创建，并排序目录枚举断言；生产路径安全检查和别名拒绝断言不变。

本地 Linux 共享核心 integration 测试 107 项通过、1116 次断言；插件完整 check 198 项通过、3110 次断言。Windows 隔离环境使用刻意改变大小写的 TEMP/TMP 别名复测，共享核心 integration 101 项通过、6 项既有进程中断用例跳过、735 次断言；同环境还原修正前的 removal-baseline 测试可复现失败，修正后通过。该结果不替代远端修复提交的完整工作流。

首轮后端工作流通过，Linux CLI、脚本回归与 RISC-V 构建通过；Draft PR 自动跳过的检查不记为通过。DCO 提示两个提交缺少 Signed-off-by，用户已授权使用当前 Git 作者身份补签并仅重写这两个提交、以 force-with-lease 更新本功能分支。未创建 tag、部署或发布 npm 包。

补充插件测试夹具的相同路径规范化，并修正模拟清理超时用例的测试收尾：先保留原有小于 3 秒的拒绝断言，再等待被强制终止的子进程发出 exit，避免 Windows cwd 句柄尚未释放时删除目录。最终 Windows 插件 build/typecheck/lint 通过，完整测试 198 项通过、3110 次断言；Linux 受影响插件测试及 lint/typecheck 通过。Windows 本机 Bun 为 1.3.14，CI 固定为 1.3.13，最终远端结果仍须单独核验。


## M0 能力汇总与首版回退决策

compatibility-design.md §1.4 将发行/生成器、Bundle、Client/Remote、工具卡片、可信上下文及匿名资源发现映射到已有报告；没有追加模型或宿主测试，也不将不同阶段产物视为当前候选全量验收。§6.1 明确首版没有旧正式版本时禁用并保留技能、inventory 和日志；指定开发包只用于隔离回退演练，未来正式回退目标须另验具体记录兼容性，不要求人为发布第二版。

本次核验该文档全部本地链接存在、引用 JSON 可解析，差异检查通过。未修改 tasks 或支持声明；平台 CI 尚未闭合，历史 Linux 基线偶发断言及 Windows 间歇安装失败仍要求发布前明确处置，不用后续成功替代归因。独立远程 Host、部署和实际发布包验收继续保留。


## 修复提交的 CI 结果与构建目录别名修正

提交 957c4737b 的 DCO 检查通过；[三平台 CLI 工作流](https://github.com/iflytek/skillhub/actions/runs/38691150529)全部通过。[DSH 工作流](https://github.com/iflytek/skillhub/actions/runs/38691150641)的 Linux 两个 Node 版本均通过，包含隔离 Web Profile 的真实 Host 冒烟；Windows 仍在执行。macOS 共享 CLI 通过，但插件构建的官方生成器将临时根解析成 /private/var，源文件仍使用 /var，触发 TS6059。

在 Linux 将 TMPDIR 指向符号链接目录，修复前稳定复现同一错误；build.ts 将 mkdtemp 返回的 staging 根目录经 realpath 规范化，编译选项和类型检查不变。相同输入下完整 check 通过：构建、typecheck、lint、198 项测试及 3110 次断言。真实 macOS 的修复提交仍待远端确认，不据本机模拟宣布平台支持。

已下载上述 Linux 两个任务的 sanitized Host 报告并归档为 [CI Linux 结果](../../../docs/research/dsh-native-plugin-2026-10-12/ci-linux-957c4737b-result.json)：Node 24.2.0 与 24.21.0 均完成 runtime-baseline/plugin-lifecycle，未调用模型，包摘要一致。报告仅代表提交 957c4737b 的两个 Linux 任务，不代表整个矩阵或后续构建修复提交。


## Windows Bun 1.3.13 测试服务关闭停滞

上述 Windows 两个 CI 任务持续停留在 Registry adapter 测试。使用官方 npm 分发并核验 integrity 的 Bun 1.3.13，在 Windows 隔离环境复现：请求超时、NETWORK_ERROR 及稍后重试均通过，finally 中 server.stop(true) 等待未完成的测试响应而不返回。局部诊断显式释放该响应后通过。取消已确认停滞的远端运行，取得日志确认同一位置；不是因观察超时直接重启任务。

测试夹具仅在 finally 显式释放 headers 阶段悬挂响应后再关闭服务，保留超时、重试与用户取消断言。未更改 RegistryAdapter 或升级 CI Bun。精确 Windows Bun 1.3.13 定向检查 22 项通过、40 次断言；修复版完整 check 的 build/typecheck/lint 及 198 项测试、3110 次断言全部通过，整轮约 61 秒。macOS 的构建路径修复与此测试收尾修复将一并推送后重跑矩阵。
