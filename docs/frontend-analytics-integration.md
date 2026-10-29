# 前端动作统计接入指南

## 1. 先了解边界

SkillHub 页面只提供被动的 `data-analytics-id` 标签，默认不采集、不加载统计 SDK、不发送事件。可选 helper 把允许的原生交互转换成 `{ id, event }`，再交给部署方选择的 adapter。点击下载只表示下载意图，不表示下载、安装成功；提交发布也不表示发布成功。部署方负责授权、隐私合规、保留期限和平台安全。

本文对应 PR-1（#1010）的手工接入示例，不是产品内置开关。仅通过 Docker／Compose 或 Helm 配置开启、默认关闭和按需加载的能力计划在 PR-2 交付，见 [OpenSpec 变更](../openspec/changes/add-configurable-frontend-analytics/proposal.md)。Issue #989 要等 PR-2 完成并验证后才关闭。精确接口、事件边界和隐私约束以[英文契约](frontend-analytics.md)为准。

## 2. 选择接入路径

### 路径 A：复用已有统计平台

先确认平台接收地址、公开站点标识、授权策略和 CSP。不要把平台管理密钥放到浏览器。平台不受支持时，需要部署方编写自己的 adapter；当前版本不能只填平台名字就接入任意平台。

1. 复用 [helper](examples/analytics/observe/v1/skillhub-intents.mjs)，不要修改业务页面或引入厂商 SDK。
2. 参照 [Umami adapter](examples/analytics/observe/v1/umami-adapter.mjs)实现 `send(intent) -> Promise<void>` 和 `stop()`。send 必须消化同步异常和异步拒绝，不向页面抛错；stop 后不能再派发请求，已经开始的 fetch 无法撤回。
3. 由 adapter 验证平台参数、清洗最终发送字段、管理 SDK 加载和超时。不要只清洗 helper 回调后就信任 SDK 自动附加的 URL、标题或身份字段。
4. 在部署方入口中连接 helper 与 adapter，只有策略允许时才初始化：

```js
import { observeSkillhubIntents } from './skillhub-intents.mjs'
// adapter 由部署方创建；必须先确认当前授权。
const stopObserver = observeSkillhubIntents({
  root: document,
  onIntent: intent => adapter.send(intent),
})
function stop() { stopObserver(); adapter.stop() }
```

入口交付、同源代理、授权回调和 CSP 由部署方维护。下面的 edge 仅演示 Umami 两个端点；使用其他平台时，需要自行审核并替换 adapter 和代理端点。不要把平台管理界面代理到公开采集前缀。

### 路径 B：本地自托管 Umami 演示

前提：已有能正常使用的 SkillHub Web；已安装 Docker 和 Compose；能使用示例指定的 Umami 3.4.1、Postgres 16、Nginx 1.30.6 镜像；本机 8300 和 8081 端口空闲。所有 shell 命令从仓库根目录运行。示例会创建独立演示网络和临时数据库，不用于生产持久化。请在隔离环境运行。

先检查配置，再启动：

```sh
docker compose -f docs/examples/analytics/compose.yaml config
docker compose -f docs/examples/analytics/compose.yaml up -d
```

默认上游是 `host.docker.internal:80`，对应 release Compose 的 `WEB_PORT` 默认 80。这里是容器访问宿主机的 Web 入口，不是后端端口，也不是 edge 自己的 8081。若 Web 实际发布在其他端口（比如 18080），先在当前终端导出，再启动。后面所有 compose 命令都要在这个终端里执行，否则重建 edge 时会回到默认的 80：

```sh
export SKILLHUB_WEB_UPSTREAM=host.docker.internal:18080
docker compose -f docs/examples/analytics/compose.yaml up -d
```

原生 Linux Docker 需要 `host.docker.internal` 映射，示例 edge 已包含 `extra_hosts: ["host.docker.internal:host-gateway"]`。Docker Desktop／OrbStack 已提供这个名字。宿主机 Web 必须监听容器可达的地址；如果防火墙或仅回环监听阻止访问，先调整可达性，或填写容器可以访问的其他 `host:port`。不要填写 `http://` 或 URL 路径。

打开 `http://localhost:8300`。首次演示登录是 `admin` / `umami`，立即改密码。示例公开的数据库口令和 APP_SECRET 也只能用于演示；生产需要独立密钥、持久化、备份和访问控制。

在 Umami 创建一个演示网站，记录网站 UUID。打开 `docs/examples/analytics/observe/v1/observer.mjs`：

1. 将全零 `website` 改为刚创建的网站 UUID。
2. 将 `enabled: false` 改为 `enabled: true`。
3. 将 `hasConsent()` 接入自己的当前授权判断。仅在隔离演示、明确允许采集时可暂改为返回 true；正式接入不能把它当作用户同意。
4. 按下一节配置 rules。示例额外的 `skill.example.open` 没有对应产品元素，不会凭空产生事件。

然后重建 edge，使单文件挂载更新可靠生效：

```sh
docker compose -f docs/examples/analytics/compose.yaml up -d --force-recreate edge
docker compose -f docs/examples/analytics/compose.yaml exec edge nginx -t
docker compose -f docs/examples/analytics/compose.yaml exec edge nginx -T
```

打开 `http://localhost:8081`，不要从原 Web 入口验收注入。

注意缓存：模块按 immutable 长期缓存，浏览器会一直用第一次拿到的 `observer.mjs`。本地演示每次改完入口文件，都要在开发者工具 Network 勾选「Disable cache」后再刷新，或者用一个新的无痕窗口打开。正式交付中不能原地改文件，要发布到新的版本目录（如 `v2`）并同步注入路径，不能覆盖旧版本字节。

## 3. 配置规则和增加动作

省略 `rules` 使用冻结的 `DEFAULT_RULES`，共 12 条：

| ID | 规则 |
| --- | --- |
| search.query.submit | submit |
| skill.card.open | card |
| suite.card.open | card |
| skill.download.request | click |
| skill.file.download | click |
| skill.command.copy | click |
| skill.prompt.copy | click |
| skill.publish.request | click |
| skill.publish.confirm | click |
| skill.publish.cancel | click |
| skill.delete.confirm | click |
| skill.delete.cancel | click |

显式传入 rules 会替换默认值，空对象表示没有动作。要保留默认值再增加已存在的动作，先合并：

```js
import { DEFAULT_RULES, resolveRules } from './skillhub-intents.mjs'
const rules = { ...DEFAULT_RULES, 'navigation.home.open': 'click' }
const { active, skipped } = resolveRules(rules)
console.table(skipped) // 只在本地检查，不发送这些诊断。
```

把 `active` 传给 helper 的 `rules`。也可在浏览器开发者工具 Console 中直接执行以下代码，无需编辑源码，就能查看无效项：

```js
const { resolveRules } = await import('/_skillhub-observe/v1/skillhub-intents.mjs')
console.table(resolveRules({ 'skill.example.open': 'drop' }).skipped)
```

应看到 `id: skill.example.open`、`reason: unknown rule type`。非法 ID、非字符串规则也会跳过；非法容器返回 `id: null` 的诊断和空 active。该检查只验证配置，不能证明 DOM 元素支持某类事件。

仅支持三种规则：

- `click`：普通按钮、链接；忽略 FORM、SELECT、TEXTAREA、非按钮 INPUT（包括文件选择器）。
- `submit`：只用于 FORM。点击提交和按 Enter 都归一到原生 submit；原生校验失败可能没有事件。
- `card`：已有 Enter／Space 处理的 DIV role=link，观察 click 和对应 keydown。helper 不会替应用增加键盘行为。

不支持的选择器、拖放、文件和裁剪行为不要硬填 click。未配置的动作不采集。`skill.detail.render` 是结构标签，不是自动页面访问量。

新增产品动作时，开发者在正确的现有元素上增加静态字面量 ID，再从仓库根目录运行：

```sh
node web/scripts/analytics-manifest.mjs --write
node web/scripts/analytics-manifest.mjs
```

审核生成的 [manifest](frontend-analytics-manifest.json) 差异和原生交互测试。部署方再加一条规则即可。ID 必须匹配 `^[a-z]+(?:\.[a-z]+){2,}$`：至少三段小写字母，不含数字、下划线或资源信息。已有 ID 含义冻结，不能把用户、技能名、版本、路径拼进去。manifest 是版本清单，不是运行时封闭枚举。

## 4. 部署、子路径和 CSP

edge 仅在 UI HTML 注入一个延迟执行的 module，不改变 API、登录、下载、静态资源或 runtime-config。server 级 `client_max_body_size 100M` 保留标准 Web 的 100 MiB 请求体上限；实际业务校验仍可能拒绝上传。不要在应用代理 location 覆盖成更小值。

正式启用前检查 Nginx 含 http_sub_module、`nginx -t` 成功，并审核 `nginx -T`。HTML 输入不能仍被上游 gzip／Brotli 压缩。示例禁用 HTML 缓存和旧校验器；启用或回退都应清理 CDN／浏览器旧 HTML。不要修改 API 或 immutable 资源的缓存规则。

根路径演示的 CSP 需要允许同源外部 module 和同源连接。保留原有 CSP；nonce-only 或 strict-dynamic 策略不能只靠同源满足。需要部署方协调每响应 nonce 或已批准的 hash／SRI；做不到就不要启用。不要加 unsafe-inline、unsafe-eval 或通配符。还要保留原安全头，注意 Nginx add_header 的 location 继承规则。

`/skillhub/` 等子路径不是只改一个 URL。先按正常部署方式配置 `SKILLHUB_WEB_BASE_PATH=/skillhub/` 和匹配的同源 API base，再同时调整 UI location、无尾斜线重定向、旁路正则、模块目录／注入 src、两个 Umami 精确端点和 adapter prefix。edge 到 Web 的 proxy_pass 不带尾斜线，保留原 URI，不能再次重写。逐项配置见[英文契约的子路径映射](frontend-analytics.md#supported-sub-path-mapping)。OAuth、公网 HTTPS 和资源地址仍遵守原部署要求。

## 5. 验收：照这个顺序检查

下面命令假定根路径和默认 edge 端口。子路径时为所有采集 URL 加相同前缀；自定义端口时统一替换地址。

1. 检查模块确实由 edge 提供，不是 SPA 返回的 HTML：

```sh
curl -i http://localhost:8081/_skillhub-observe/v1/skillhub-intents.mjs
curl -i http://localhost:8081/_skillhub-observe/v1/missing.mjs
curl -I http://localhost:8081/skills
```

第一个应为 200、JavaScript MIME、nosniff 和 immutable。第二个应为非 HTML 的 404。第三个应为 200、Cache-Control: no-store，并保留原 CSP（若原部署配置了 CSP）。

2. 打开 edge 的 SkillHub 页面，在开发者工具 Network 勾选「Preserve log」和「Disable cache」，筛选 `/_skillhub-umami/`。刷新后应加载 script.js，但没有自动 pageview 或 send。
3. 搜索框输入任意本地测试文字，点击搜索。应恰好有一条 `/_skillhub-umami/api/send` POST，响应 200。再按 Enter 搜索一次，应新增一条，不是两条。切换 Network 的 Payload 查看真正出站 JSON，不要只看回调。
4. JSON 只能包含 `{ type: 'event', payload: { website, name, data: { event } } }`。website 是配置的 UUID；搜索 name 为 search.query.submit，event 为 submit。不得出现搜索词、完整 URL、标题、referrer、身份或额外 data 字段。
5. 打开 Umami 网站事件页，选择当前时间范围，应看到 search.query.submit 和对应数量。平台处理可能稍有延迟；不能把 HTTP 200 单独当作入库证明。
6. 执行下面的 Console 命令后再搜索，页面仍能使用，但 Network 不得新增 send：

```js
window.skillhubAnalytics.stop()
```

stop 不能撤回已开始的 fetch。重新获准后才可调用 start()；同一个 root 重复初始化保留第一次规则和回调，修改规则需要先 stop 再初始化。

7. 将入口恢复 `enabled: false`，重新创建 edge。保持「Disable cache」勾选（或换一个新的无痕窗口），刷新页面后再搜索：不得加载 script.js、产生采集回调或 send。完全不注入时也应零采集。最后在隔离演示中停止 Umami，再打开新页面搜索和打开技能卡片，应仍正常，无统计依赖导致的页面错误：

```sh
docker compose -f docs/examples/analytics/compose.yaml stop umami
```

验收后如需继续演示，使用上面的 up 命令恢复。不要为了测试停止共享平台或生产服务。

## 6. 故障排查

| 现象 | 常见原因 | 处理 |
| --- | --- | --- |
| module 404 | 未挂载模块，版本目录或子路径不一致 | 对照 nginx -T、挂载路径、注入 src 和相对 import，三份 mjs 都要可访问；不要回退到 index.html |
| MIME 报错或返回 HTML | 模块误入 SPA fallback，缺少 mjs MIME | 使用示例独立前缀、try_files 404 和 JavaScript MIME，保留 nosniff |
| CSP 拦截 | script-src／connect-src 不允许，或 nonce-only 策略 | 按原 CSP 的授权机制交付；不要删除 CSP 或放宽为通配符 |
| 上传 413 | 外层代理仍默认 1 MiB，或请求超过 100 MiB | 用 nginx -T 检查整条代理链；应用代理继承 100M，超过标准上限仍应拒绝 |
| 没有事件 | 走了原 Web 而非 edge，enabled／授权未开，UUID 错，规则未配置或宿主不支持 | 按第 5 节从模块、skipped、Network 到平台事件页逐步确认；检查时间范围；不要把结构标签当 pageview |
| edge 502 | Web 上游地址／端口错误，宿主不可达 | 核对 WEB_PORT、host-gateway、防火墙和监听地址；不要把后端或 edge 端口填成 Web 上游 |
| Umami 不可用 | SDK 404、超时、平台停止或网络故障 | 页面应继续正常；检查平台和代理日志，恢复后重新打开页面；没有重试补发保证 |

## 7. 升级、关闭和已知限制

升级时先比较新旧 manifest。已有 ID 语义不变；含义变化需要新 ID，重命名／删除必须有发布说明。消失的 ID 不再触发，新 ID 默认不自动加入规则。模块／SDK 升级要重新验证真实出站字段、stop 派发边界、并发发送、根／子路径和业务行为，不能只看构建成功。

临时关闭当前页用 stop()；持久关闭把入口改回 enabled:false，并按版本发布规则更新、清理 HTML 缓存和刷新。彻底关闭则撤掉注入／edge，让访问回到正常 Web 入口，确认新 HTML 不引用模块、Network 无采集。关闭平台网站或封锁采集路由是额外控制，不能代替授权撤回。

结束本地演示（会删除演示数据库，先确认无需要保留的数据）：

```sh
docker compose -f docs/examples/analytics/compose.yaml down -v
```

限制必须一起评估：

- 尽力发送，无重试、无持久队列，可能少计。SDK 加载期最多在内存暂存 20 条，默认最多等三秒；超额、失败、超时或 stop 都丢弃。已就绪发送彼此独立，不因一个请求挂起而阻塞后续动作。
- 示例固定 Umami 3.4.1。stop 的派发保护依赖此版本在 fetch 前同步序列化的顺序，升级必须重跑适配器测试和真实浏览器验收；不要同时加载第二个 Umami tracker。
- JSON 字段少不等于匿名传输。IP、User-Agent、Origin 仍到平台；SDK 还带网站、实际 hostname 和后续会话 cache 头。edge 去掉采集代理的 Cookie、Authorization、Referer，并使用 no-referrer。Umami 仍可能据传输元数据推导浏览器、系统、设备、地域和会话。
- SDK 在清洗前仍会在页面内读取标题、URL、referrer 和自身 localStorage 开关；通用 helper 不读取这些内容。平台日志、数据保留和权限需另审计，详见[隐私契约](frontend-analytics.md#privacy-and-integration-modes)。
