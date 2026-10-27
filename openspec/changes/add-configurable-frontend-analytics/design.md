## Context and decisions

本变更在 PR #1010 上保留原贡献历史并合入最新 main，保留 footer 翻译及 Trans 登录协议。使用已有运行时配置比 Nginx HTML 注入更易自动交付；完整标签管理平台增加额外服务与权限体系，本期不采用。

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

## Risks and verification

重点验证默认零 SDK/采集请求、错误配置降级、页面启动不等统计 SDK、DNS失败不阻断Web、stop/init竞争、适配器字段清洗、根/子路径、100M原业务上传、登录/下载及配置回滚。固定 SDK 升级须验证真实发送顺序；不能将静态配置或模拟API测试描述为真实业务E2E。正式测试矩阵在实现和独立Review收敛后生成。
