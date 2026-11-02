# 前端动作统计：首次本地接入

本文带你在已有 SkillHub 前增加一个本地演示入口，连接 Umami 并确认事件入库。SkillHub 默认不采集。

::: warning 当前支持范围
Docker／Compose 和 Helm 已可只填配置开启统计，支持 Umami 和 DataFinder（火山引擎增长分析），步骤见[详细指南](./frontend-analytics-guide.md)。**一键脚本部署统计平台尚未交付**，需自行准备统计平台。本页下文是固定 `v0.2.25` 的旧版手动本地演示，仅供了解原理。
:::

## 1. 准备已有 Web 和示例文件

先确认 SkillHub Web 可以正常打开、搜索。尚未部署？先完成 [Docker 部署](./deployment.md)，再回来继续。本例适用于本机根路径部署；子路径、生产代理与 CSP 请用[详细指南](./frontend-analytics-guide.md)。需要 Docker、Compose 和 Git，且 8300、8081 未被占用。

| 地址／端口 | 用途 |
| --- | --- |
| 原 SkillHub Web（默认 80） | 已有业务服务，本例不会重新部署它 |
| `http://localhost:8300` | Umami 管理界面，创建网站和查看事件 |
| `http://localhost:8081` | 本例新增的 SkillHub 访问入口，经过 edge 注入采集脚本 |

一键安装目录只有部署文件，没有本例源码。从固定的 `v0.2.25` tag 获取演示配套，在另一个工作目录执行：

```sh
git clone --depth 1 --branch v0.2.25 https://github.com/iflytek/skillhub.git skillhub-analytics-demo
cd skillhub-analytics-demo
```

目标目录已存在时换用空目录。这只是获取演示配套，不会替换现有 SkillHub，也不要求重装它。使用已有仓库时先确认包含 `docs/examples/analytics/compose.yaml`。以下所有 shell 命令都在**仓库根目录、同一终端**执行，不能在一键安装目录直接运行。

示例默认连接原 Web 的宿主机 80 端口。如果实际是 18080，先设置：

```sh
export SKILLHUB_WEB_UPSTREAM=host.docker.internal:18080
```

将 `18080` 换成实际 **Web** 端口，不是后端端口；不要填写 `http://` 或路径。Web 必须监听容器可达的地址，仅回环监听或防火墙限制可能阻止 edge 访问。示例已为原生 Linux 配置 `host-gateway` 映射。

## 2. 启动 Umami，取得网站 UUID

启动前先执行 `docker compose ls --all`，确认专用项目名 `skillhub-analytics-demo` 未被其他项目占用。若已占用，选择一个未使用的新名字，并在本文所有命令中一致替换。所有演示命令显式使用 `-p`，防止继承终端已有的 `COMPOSE_PROJECT_NAME` 而误操作其他部署；换一个克隆目录本身不能隔离 Compose 项目。

先检查示例，再只启动统计数据库和 Umami：

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml config
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml up -d postgres umami
```

等待 `http://localhost:8300` 可访问，用演示账号 **`admin` / `umami`** 登录并立即修改密码。在网站管理中创建一个演示网站，复制它的 **UUID**（不是网站名称、网址或登录密码）。

此 Compose 使用公开演示密钥和临时数据库，不具备生产持久化、备份与访问控制方案，不要直接用作生产部署。

## 3. 配置本次演示要采集的动作

打开仓库中的 `docs/examples/analytics/observe/v1/observer.mjs`，保留现有 imports、`start()`、`stop()` 等代码，仅将 `const config` 和 `hasConsent()` 替换为下列内容：

```js
const config = Object.freeze({
  enabled: true,
  website: '替换为上一步的网站 UUID',
  prefix: '/_skillhub-umami',
  rules: DEFAULT_RULES,
})

// 仅用于本人明确同意采集的隔离演示；正式接入必须读取真实授权状态。
function hasConsent() { return true }
```

`DEFAULT_RULES` 已由文件开头导入，包含搜索提交、技能卡片等现有动作。示例原有的 `skill.example.open` 不对应产品元素，这里不使用它。不要将真实管理密钥或个人信息放进该文件，也不要对其他用户直接使用固定的 `true`。

## 4. 启动演示入口，检查真实事件

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml up -d --force-recreate edge
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml exec edge nginx -t
```

`nginx -t` 应成功。打开 **`http://localhost:8081`**，不要继续使用原 Web 地址验收采集。

1. 开发者工具 Network 勾选 **Disable cache** 和 **Preserve log**，刷新页面，筛选 `/_skillhub-umami/`。应成功加载 `script.js`，但不自动发送 pageview。
2. 搜索框输入测试文字并点击搜索，应有一条 `api/send` POST；再按 Enter 搜索一次，应再增加一条。
3. 检查 Payload：事件名为 `search.query.submit`，`data.event` 为 `submit`；不得包含搜索词或身份字段。点击技能卡片可检查 `skill.card.open`。
4. 在 8300 的 Umami 网站事件页选择当前时间范围，确认对应事件及次数。**HTTP 200 本身不能证明已入库**。点击事件只代表意图，不代表下载、发布或执行成功。

| 还需验收 | 预期 |
| --- | --- |
| 关闭采集、刷新 | 无 SDK／事件请求，业务正常 |
| 已启用时停止演示 Umami | 采集失败，搜索与技能页面仍可用 |
| 启动 Umami、刷新 | 新事件可入库；不保证补发丢失事件 |

上述故障测试的完整对照、恢复步骤见[详细指南](./frontend-analytics-guide.md)。只操作本例统计服务，不要停止共享平台。

## 5. 关闭与清理

当前页执行 `window.skillhubAnalytics.stop()` 可阻止后续派发，已发出的请求不能撤回。持续关闭时，将文件恢复为 `enabled: false`、`hasConsent()` 返回 `false`，再重建 edge 并禁用缓存刷新。


```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml up -d --no-deps --force-recreate edge
```

暂时停止演示、保留容器以便继续：

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml stop
```

演示数据库不是生产持久化方案；需要数据请先备份，不要把停止等同持久化保证。确定不再需要演示数据时才执行：

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml down -v
```

该命令删除本示例的容器和卷，演示数据应视为删除；不对现有 SkillHub 部署执行清理。关闭后回到原 Web 地址。

## 常见问题

| 现象 | 先检查 |
| --- | --- |
| 找不到 Compose 文件 | 当前目录应是克隆仓库根目录，而非 runtime 安装目录 |
| 8300／8081 无法启动 | 本机端口占用；看 `docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml logs` |
| 8081 返回 502 | Web 端口、容器可达监听和防火墙；不要填后端端口 |
| 没有采集请求 | 是否访问 8081，入口是否启用／获得授权，修改后是否重建及禁用缓存刷新 |
| 请求失败或平台无数据 | UUID 是否正确、Umami 是否就绪、时间范围是否匹配；检查 Network 响应 |

进阶：[完整操作指南](./frontend-analytics-guide.md) · [接口与隐私契约（英文）](./frontend-analytics-reference.md)。字段精简不等于匿名传输：IP、User-Agent 等仍可能到达平台，保留期限、访问控制和合规由部署方负责。
