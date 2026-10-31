## Context and decisions

本变更在 PR #1010 上保留原贡献历史并合入最新 main，保留 footer 翻译及 Trans 登录协议。使用已有运行时配置比 Nginx HTML 注入更易自动交付；完整标签管理平台增加额外服务与权限体系，本期不采用。

## 分阶段交付与接口对齐

PR-1（#1010）仅完成被动标签、厂商中立 helper 与示例 adapter、edge 示例问题修正和中文接入指南。它不修改产品入口或部署开关，手工接入方式继续明确标为可选示例。PR-2 独立交付配置化加载、产品模块、runtime-config、同源代理和 Compose／Helm。两者可分别合并，Issue #989 仅在 PR-2 完成并验证后关闭。

PR-2 必须采用 PR-1 实际交付的接口，不能另起一套不兼容协议：

| PR-1 接口 | PR-2 采用方式 |
| --- | --- |
| `data-analytics-id` 匹配 `^[a-z]+(?:\.[a-z]+){2,}$`，由 manifest 索引 | 保持已发布 ID 含义冻结；manifest 不是运行时封闭枚举，不插值资源／身份信息 |
| `observeSkillhubIntents({ root, rules, onIntent }) -> stop` | controller 按授权安装委托观察；不改变页面原生事件和业务处理 |
| 冻结的 `DEFAULT_RULES`（12 条） | rules 省略用默认值，显式提供为替换，空对象不采集；增加默认规则需显式合并 |
| `resolveRules(rules) -> { active, skipped: [{ id, reason }] }` | 使用相同纯校验；非法容器的 id 为 null；诊断只留本地，不发送原配置 |
| 有限类型 `click`／`submit`／`card` | 保持宿主限制和原生事件边界；未知类型／ID 格式／不支持宿主跳过，不猜测选择、文件、拖放或裁剪事件 |
| intent 仅 `{ id, event }` | event 为原生 click／submit／keydown，不表示业务成功；同版本只允许增加可选的静态白名单字段，消费者忽略未知字段；破坏性变更另版本 |
| adapter `send(intent) -> Promise<void>`、`stop()` | send 消化异常、永不 reject；平台参数只归 adapter；停止后不得派发新请求，已开始 fetch 不可撤回 |
| 同 root 重复初始化保留第一次 config／callback 并返回相同 stop | 改配置先 stop 再初始化；旧 stop 不得撤掉新安装，stop 可重复调用；嵌套观察共享事件去重 |

配置映射：enabled=false 不安装观察、不加载 SDK；consent=denied 同样禁止初始化。enabled 与 consent 的联合判断由通用 controller 管理，不放进业务组件。rules 原样通过 resolveRules，保留 active／skipped 语义。adapter 仅选择静态注册 factory；adapterConfig 只交给所选适配层校验，不把 websiteId 等厂商字段提升到通用配置。PR-1 示例的 website／prefix 对应 PR-2 Umami 的 adapterConfig.websiteId／部署生成的同源前缀；PR-1 的 hasConsent hook 对应 PR-2 consent 和动态 start／stop。PR-2 新增默认关闭的模块加载边界，不能声称 PR-1 示例已具备产品配置化加载。

PR-2 将 helper 迁入 `web/src/features/analytics` 的单一产品源码，迁移而非复制，保留 PR-1 测试的全部断言，包括 grammar、有限规则、生命周期、隐私和 dispatch 边界。示例 adapter 仍不是任意远程 JS 插件机制。

## PR-2 产品设计（下列能力尚未在 PR-1 实现）

### 1. Stable configuration contract

- `SKILLHUB_WEB_ANALYTICS_CONFIG`：可选 JSON 对象，空值等同关闭。字段为 enabled（boolean，默认 false）、adapter（注册适配器名）、consent（`denied`/`granted`，默认 denied）、rules（可选动作→有限规则对象，省略沿用默认规则，显式空对象不采集）、adapterConfig（平台自有公开参数对象）。
- `SKILLHUB_WEB_ANALYTICS_UPSTREAM`：仅部署可见的代理 origin，不能含凭证、路径、查询、fragment、空白或 Nginx 注入字符；不进入浏览器 runtime-config。
- 内置 Umami 适配器在 adapterConfig 中读取 websiteId（公开 UUID），不让通用 controller 认识该字段。平台密钥不允许写入浏览器配置。
- `consent=granted` 表示部署方显式确认其部署策略允许本次采集，不等同每个用户单独点击同意。需动态用户授权的部署可使用公开 start/stop 接口接入既有同意组件；本期不新增同意 UI。
- 不合法配置只关闭采集，并给出不包含原值的字段级诊断；不可破坏 API/auth 等原 runtime-config 或阻止 Web 启动。

### 2. Module boundary and single source

业务 TSX 仅有被动标签。采集源码在 web/src/features/analytics 下单一维护，包含通用类型、controller、规则 helper、静态注册表和独立 adapters/umami。迁移既有示例测试到正式源码，保留既有断言；不维护 docs 和产品两份 helper。

bootstrap 完成原应用启动后，以不等待 SDK 的独立异步路径读取 analytics 配置并按需导入 controller。enabled=false 或 consent=denied 时不得导入采集模块、加载 SDK 或请求统计平台。适配器接口只要求 send(intent)、stop()；factory 注册与配置校验由适配层完成，不开放任意远程 JS URL。

controller 负责一次初始化、规则校验、代际隔离和停止。异步初始化期间被关闭后，迟到的模块或适配器不能重新启动。stop 后不得新派发请求，已开始的 fetch 无法撤回。保留既有队列上限、超时及 best-effort 不重试行为。异常、拒绝或统计服务不可用不得影响业务。

### 3. Same-origin optional proxy

新增保留前缀 `/_skillhub-analytics/`，同步现有 base-path 的 shell、TS、配置校验及 Helm 保留规则。浏览器侧自动使用 BASE_PATH；既有 Nginx 子路径 rewrite 只处理一次。

Nginx 通过独立 include 片段选择已注册适配器的精确端点：Umami 仅 script.js 和 api/send；其余路径拒绝。使用变量 proxy_pass 和已有 resolver，使统计域名解析不成为启动依赖。HTTPS 上游必须验证证书并带正确 SNI。采集路由移除 Cookie、Authorization、Referer，不扩大原 API/auth 路由、不修改原 100M 上传限制、TLS、业务缓存或权限。禁止为接入放宽 CSP。公开 runtime 配置只含适配器必须的同源前缀和公开参数。

### 4. Deployment and lifecycle

现有平台接入：填写 JSON 和内部 upstream，更新 Web。可选自托管组合：在 release Compose 上叠加统计服务与其持久化数据库，使用显式凭证，不新增 Nginx；创建网站/取得 UUID 等一次性平台步骤必须诚实说明。入口和 Helm 均默认关闭。

部署配置变更经重建/重启 Web 使新页面生效；旧标签页不自动接收新环境参数。动态停止通过当前页面接口生效；紧急全局停止可关闭独立采集路由。文档不得宣称仅修改 env 即刻撤销已在发送的请求。模块使用正常发布构建产物与缓存策略，不把可变配置放入 immutable JS。

### 5. Serialization and loading implementation

PR-2 不新增 jq 或 Node 运行时依赖。镜像已有 BusyBox awk 负责有界 JSON 语法和部署控制字段校验，base64 将 JSON 作为数据追加到现有 no-store runtime-config.js；浏览器用 JSON.parse 解码，不经 eval，也不把任意 JSON 插入 JS 字面量。配置最大 64 KiB、最多 32 层，重复键拒绝。非法 rules 仍按既有 resolveRules 跳过，不升级为整个配置失败。

bootstrap 仅静态包含小型 gate 和注册参数校验；controller/helper 与 adapter 保持独立懒加载 chunk。在首个 React DOM commit 后的独立任务中启动 gate，不把正常挂载与 SDK 绑定。enabled=true 且 consent=denied 时不加载模块，但代理保留精确端点，供当前页显式 start() 授权后使用。静态 adapter factory 在动态导入返回后检查取消信号，迟到的导入不能重新创建 SDK。

旧示例目录仅保留指南指针。helper/adapter 与全部断言迁入产品测试树；历史 edge/Compose 只保留为测试 fixture，不是部署路径，不维护第二份采集实现。

## Risks and verification

重点验证默认零 SDK/采集请求、错误配置降级、页面启动不等统计 SDK、DNS失败不阻断Web、stop/init竞争、适配器字段清洗、根/子路径、100M原业务上传、登录/下载及配置回滚。固定 SDK 升级须验证真实发送顺序；不能将静态配置或模拟API测试描述为真实业务E2E。正式测试矩阵在实现和独立Review收敛后生成。
