# 前端动作统计接入指南

## 1. 默认关闭，按需开启

不配置时，不加载采集模块或 SDK，也不发送统计请求。正常业务不等待统计平台。
现在 Docker、Compose、Helm 用户只需填写配置。不需要改 JS、复制文件或手写 Nginx。
第一版支持 Umami 3.4.1。其他平台需要开发并发布新的注册 adapter，不是任意远程脚本插件。

点击下载只是下载意图，不是下载或安装成功。提交发布也不是发布成功。
部署方负责授权策略、平台安全、数据保留和合规。接口和事件边界见[英文契约](frontend-analytics.md)。

## 2. 复用已有 Umami

前提：SkillHub 已正常部署；Web 镜像包含本功能；有可达的 Umami 3.4.1。
先在 Umami 创建网站，拿到公开的网站 UUID。管理员密码、API token 不要放入前端配置。
确认 Web 容器能够访问平台的 HTTP(S) origin。HTTPS 证书需由镜像的系统 CA 信任。
自签名平台需先按组织规范配置受信任 CA，不要关闭证书验证。

在受保护的 `.env.release` 中填写下面两项。把示例 UUID 换成自己网站的 UUID。
JSON 保持一行。单引号用于 Compose 的 dotenv 文件，不是 JSON 的一部分。

```dotenv
SKILLHUB_WEB_ANALYTICS_CONFIG='{"enabled":true,"adapter":"umami","consent":"granted","adapterConfig":{"websiteId":"4398de8e-40df-4483-9eea-ed213643525f"}}'
SKILLHUB_WEB_ANALYTICS_UPSTREAM=https://stats.example.com
```

`granted` 表示部署方确认当前策略允许采集，不表示每位用户都点击了同意。
需要用户单独授权时，初始使用 `denied`，再接入第 5 节的公开接口。本功能不提供同意弹窗。

所有命令从仓库根目录运行。正常发布配置仍需包含数据库、存储和认证等参数。

```sh
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml config --quiet
docker compose --env-file .env.release -f compose.release.yml up -d --no-deps --force-recreate web
docker compose --env-file .env.release -f compose.release.yml logs --tail=100 web
```

不要公开分享完整的 Compose 渲染结果，其中可能包含业务凭证。
Web 启动时自动生成同源代理，只开放 `/_skillhub-analytics/script.js` 和 `/_skillhub-analytics/api/send`。
其他采集路径返回 404，不公开 Umami 管理界面。统计 DNS 在请求时解析，不是 Web 启动前提。
统计配置错误只关闭统计。容器日志中的 `[analytics]` 提示字段，不回显原值。
原有 API/auth 配置、100 MiB 上传上限、TLS、业务缓存及权限不变。不放宽 CSP。

直接运行 Docker 时，把同样两项用 `--env-file` 传给正常的 Web 容器即可。文件使用原始
`KEY=value` 格式，JSON 外不要加 dotenv 单引号。保留原有 API、公开地址和端口配置。
不要把 API 上游改成统计平台地址。

## 3. 可选自托管组合

需要 Docker Compose、可用的镜像网络及持久磁盘。这个组合增加 Umami 和它自己的
Postgres，不增加 Nginx，也不修改 SkillHub 数据库。正式使用前配置备份和访问控制。

在 `.env.release` 追加独立随机凭证。先在本地生成，再把输出填入文件，不要提交文件。

```sh
openssl rand -hex 24
openssl rand -hex 32
```

```dotenv
UMAMI_DB_PASSWORD=<第一条命令生成的值>
UMAMI_APP_SECRET=<第二条命令生成的值>
UMAMI_ADMIN_PORT=8300
```

数据库口令使用字母和数字最简单。包含特殊字符时，另填 `UMAMI_DATABASE_URL`，对 URL
密码部分做百分号编码；Postgres 的 `UMAMI_DB_PASSWORD` 仍保留原值。
先只启动平台，此时统计配置可以留空：

```sh
docker compose --env-file .env.release -f compose.release.yml -f compose.analytics-umami.yml config --quiet
docker compose --env-file .env.release -f compose.release.yml -f compose.analytics-umami.yml up -d analytics-postgres umami
```

管理端口只绑定宿主机回环地址。远程机器需使用自己的 SSH 转发或受控管理入口。
本机打开 `http://localhost:8300`。Umami 初始登录是 `admin` / `umami`，立即修改密码。
修改密码会使旧会话失效。用新密码重新登录，再创建网站，复制 UUID。
此步骤不能省略，启动容器不会自动创建网站。
将第 2 节 JSON 的 websiteId 换成该 UUID，再填写：

```dotenv
SKILLHUB_WEB_ANALYTICS_UPSTREAM=http://umami:3000
```

这里是 Compose 内部服务名。然后重建正常 Web：

```sh
docker compose --env-file .env.release -f compose.release.yml -f compose.analytics-umami.yml up -d --no-deps --force-recreate web
```

同一 Compose 网络提供 DNS。Web 在独立容器或主机时需填写真正可达的内部 origin。
不使用旧示例的 edge 或宿主机 Web 代理。统计平台初始化、改密、备份仍是运维步骤，
不能把首次部署称为无需准备的一条命令。

## 4. Helm

保留已有生产 values 和 Secret。在 values 中增加以下内容：

```yaml
web:
  analytics:
    config: '{"enabled":true,"adapter":"umami","consent":"granted","adapterConfig":{"websiteId":"4398de8e-40df-4483-9eea-ed213643525f"}}'
    upstream: https://stats.example.com
```

```sh
helm lint --strict charts/skillhub -f values-production.yaml
helm upgrade --install skillhub ./charts/skillhub -n skillhub -f values-production.yaml
kubectl -n skillhub rollout status deployment/skillhub-web
kubectl -n skillhub logs deployment/skillhub-web --tail=100
```

deployment 名称使用 Chart 默认 release 名 `skillhub`；自定义名称时替换命令中的名称。
值进入 ConfigMap 和 Web 环境变量，checksum 改变会触发 Web 滚动更新。
Chart 不自动部署 Umami。先在已有平台创建网站，并确认 Pod 出站网络、DNS 和证书可用。
集群内 origin 使用完整服务名，例如 `http://umami.stats.svc.cluster.local:3000`，
Nginx 的运行时 DNS 不自动补 Pod 的搜索后缀。

## 5. 字段、规则和动态授权

`SKILLHUB_WEB_ANALYTICS_CONFIG` 是可选 JSON 对象，最大 64 KiB，嵌套最多 32 层。
空值等同关闭。重复 JSON 键拒绝，避免部署和浏览器解释不同。

| 字段 | 含义 |
| --- | --- |
| enabled | 布尔值，默认 false。总开关，start() 不能绕过它。 |
| adapter | 静态注册名，目前只有 umami。 |
| consent | denied 或 granted，默认 denied。 |
| rules | 可选规则对象。省略用默认 12 条；显式提供会替换；空对象不采集。 |
| adapterConfig | 适配器公开参数对象。Umami 只接受 websiteId UUID。 |

未知配置字段或不合法的平台参数关闭统计。rules 沿用纯 `resolveRules` 的 active/skipped
语义：非法容器没有 active，非法条目跳过，其他合法规则继续工作。日志不输出非法 ID
或原配置。开发者可本地检查 `resolveRules(rules).skipped`，不要发送诊断给平台。

`SKILLHUB_WEB_ANALYTICS_UPSTREAM` 只供部署代理使用，不进入浏览器配置。
只接受 HTTP(S) origin，无凭证、路径、查询、fragment、空白或末尾斜杠。
不要放入平台管理密钥。浏览器前缀由 Web 自动生成，不能配置任意脚本 URL。

默认规则保持冻结：

| ID | 类型 |
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

例如只统计搜索和首页导航，可在 JSON 中增加：

```json
{"rules":{"search.query.submit":"submit","navigation.home.open":"click"}}
```

这是 rules 字段示意，不是完整的开启配置。要保留默认值，需显式把默认条目也写进去。
ID 匹配 `^[a-z]+(?:\.[a-z]+){2,}$`，只用静态动作类别，不拼用户或资源信息。
`click` 用于普通按钮/链接，排除表单、选择器、文本框和非按钮 INPUT；`submit` 只用于
FORM；`card` 只用于已有键盘处理的 DIV role=link。选择、文件、拖放、裁剪不支持这些规则，
不要猜成 click。`skill.detail.render` 不是自动 pageview。

当前页面挂载后提供接口，可接入已有同意组件；浏览器 Console 也可用于验收：

```js
window.skillhubAnalytics.stop()
// 仅在重新获准后：
await window.skillhubAnalytics.start()
```

start() 是当前页的显式授权，支持初始 consent=denied。重复 start 不重复安装。
stop() 幂等；等待中的模块/adapter 不能迟到后重启采集。stop 返回后不新派发请求，
但已经开始的 fetch 无法撤回。规则变更使用 stop 再初始化；环境变量变化需重建或重启 Web。
旧标签页不会自动收到新环境参数。修改 env 文件本身不会改变正在运行的容器。

## 6. 根路径、子路径与验收

根路径无需改路由。子路径只需保持现有三项一致：

```dotenv
SKILLHUB_WEB_BASE_PATH=/skillhub/
SKILLHUB_WEB_API_BASE_URL=/skillhub
SKILLHUB_PUBLIC_BASE_URL=https://skills.example.com/skillhub
```

浏览器采集路径自动变为 `/skillhub/_skillhub-analytics/`，正常入口只重写一次。
`/_skillhub-analytics/` 是保留前缀，不能作为 Web base path。
保留当前 CSP；需要允许已批准的同源外部脚本和连接。nonce-only/strict-dynamic 部署仍需
按本身的安全策略授权，无法授权就不开启。不要增加 unsafe-inline、unsafe-eval 或通配符。

按顺序验收。下面用本机默认 Web 端口，实际环境替换 origin 和子路径：

```sh
curl -i http://localhost/runtime-config.js
curl -i http://localhost/_skillhub-analytics/script.js
curl -i http://localhost/_skillhub-analytics/api/websites
```

1. runtime-config 为 no-store；原 API/auth 键仍在；不得包含 upstream。
2. 开启且 granted 时 script.js 应是 JS 200；未注册路径应为 404。
3. 新开 SkillHub 页面，Network 保留日志。先看到应用挂载，再按需加载 controller、adapter
   和 script.js。不会自动发送 pageview。
4. 搜索框输入本地测试文字，点击搜索，再按 Enter 搜索。每次应各有一条 api/send，页面正常跳转。
5. 检查真正出站 JSON，只允许 `{type:'event',payload:{website,name,data:{event}}}`。
   搜索 name 为 search.query.submit，event 为 submit。不能有查询文字、URL、标题、身份或额外 data。
6. 在 Umami 网站事件页核对事件和数量。HTTP 200 不能单独证明入库。
7. 调用 stop，再搜索。业务继续，不能新增 send。重新 start 后允许继续。
8. 关闭配置或 denied 后重建 Web，打开新页面：不得加载 controller/adapter、script.js 或 send。
   denied 页面只有显式 start 才会加载。
9. 在隔离测试环境停止 Umami，或用不可解析的内部域名启动 Web。Web 仍应就绪，搜索、
   登录、卡片、下载继续遵守原业务行为。不要停止共享或生产平台来测试。
10. 根/子路径都测试正常上传（大于 1 MiB 且业务有效）、登录与下载。统计不代表业务成功。

## 7. 排查、升级和关闭

| 现象 | 检查 |
| --- | --- |
| 没有模块/事件 | 总开关、授权、容器是否重建、是否新页面；默认关闭是正常情况。 |
| 配置报错 | 看 `[analytics]` 字段诊断；JSON 用双引号，enabled 是布尔值，UUID 正确；不要发出原值。 |
| script.js 502 | Web 到平台的 DNS、网络、端口或证书；origin 不带路径；业务应仍可用。 |
| SDK 失败后无事件 | 加载失败/超时后不重试；恢复平台后新开页面，或 stop 再 start。 |
| 模块/资源返回 HTML | 检查部署版本、base path 和入口路由，不能改成任意远程脚本。 |
| CSP 拦截 | 按原策略授权，不删除 CSP 或放宽通配符。 |
| 有点击但没有统计 | 规则未配置、宿主不支持、超过 Umami 名称 50 字符，或 best-effort 丢失。 |
| 上传 413 | 检查整条外层代理链，正常 Web 仍是 100M，统计未降低它。 |

升级先比较[manifest](frontend-analytics-manifest.json)，已有 ID 含义冻结，新 ID 不自动加入默认规则。
消失的配置 ID 不再触发。SDK 固定 Umami 3.4.1；升级需重测真实 payload、stop、并发和
根/子路径业务。回滚 Web 镜像要同时使用该版本支持的配置；先关闭统计最稳妥。

持久关闭：把配置留空或 enabled 改 false，按第 2 节重新创建 Web。新页面应零模块/SDK/请求。
老标签页不自动更新；需让用户关闭/刷新，或在现有同意组件调用 stop。
紧急全局阻断可在平台或独立采集路由禁用接收，再处理旧页；不能撤回已发出的请求。

自托管只停止统计服务，不要对整个业务栈执行 down：

```sh
docker compose --env-file .env.release -f compose.release.yml -f compose.analytics-umami.yml stop umami analytics-postgres
```

数据库卷保留。删除统计数据需另行备份、确认后只处理 `analytics_postgres_data` 对应的卷，
不要使用整个项目的 `down -v`。

## 8. 隐私和自定义开发边界

JSON 只保留静态 ID 和原生 event。helper 不读内容、输入值、URL、Cookie、存储或身份。
SDK 在清洗前仍会读取页面标题、URL、referrer 和自己的 localStorage 开关。
少字段不等于匿名：IP、User-Agent、Origin 仍到平台；Umami 还有 website、实际 hostname、
会话 cache 请求头。Nginx 在统计上游前移除 Cookie、Authorization、Referer，并隐藏 Set-Cookie。
浏览器到 Web 本身仍可能带这些 HTTP 元数据，需按原 Web 日志和权限策略管理。
Umami 仍可能由传输元数据推导浏览器、系统、设备、地域和会话。数据库缺失 URL 的回退
是空 path/query 和 localhost，不是用户当前路径。访问日志、保留和权限要单独审核。

尽力发送，无重试或持久队列。SDK 加载期最多内存暂存 20 条，默认等三秒；超额、失败、
超时或 stop 会丢弃。就绪后的请求彼此独立。不要同时加载第二个 Umami tracker。
stop 的派发保护依赖固定版本在 fetch 前同步序列化的顺序，升级必须重新验证。

自定义 adapter 属于开发流程，不是部署流程：在 `web/src/features/analytics/adapters/`
增加参数校验与 send/stop factory，注册到静态 registry，再增加部署端校验和精确代理端点。
factory 检查取消信号后才创建 SDK。通用 controller 不认识厂商字段，业务组件只提供被动标签。
复用唯一的 `skillhub-intents.mjs`，不复制 helper，也不开放远程 JS URL。
新增标签时运行 `node web/scripts/analytics-manifest.mjs --write` 并审核语义和原生交互。
测试命令为 `pnpm -C web test src/features/analytics` 和 `python3 scripts/tests/web-analytics-test.py`。
