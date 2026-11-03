# 前端动作统计：部署与接入

SkillHub 支持通过配置接入 Umami 或 DataFinder。默认关闭；开启后按已配置的动作规则采集，正常业务不等待统计平台。不需要修改业务 JS 或手写 Nginx。

请先使用**包含本功能的 Web 镜像**。脚本文件版本与镜像标签需要分别选择并匹配；只更新 `runtime.sh` 不会让旧镜像获得新的采集模块。完整命令、配置示例和排障见[详细接入指南](./frontend-analytics-guide.md)，字段与隐私边界见[接口契约](./frontend-analytics-reference.md)。

## 选择部署路径

| 你的情况 | 接入方式 | 完整步骤 |
| --- | --- | --- |
| 一键脚本部署 SkillHub，还没有统计平台 | 脚本启动独立 Umami 和数据库，取得网站 UUID 后开启采集 | [详细指南第 3 节](./frontend-analytics-guide.md) |
| 已有 Umami 或 DataFinder | 填写平台公开标识和采集地址，重建 Web；不新建统计服务 | [详细指南第 2、3.1 节](./frontend-analytics-guide.md) |
| 使用 Kubernetes／Helm | 统计平台独立部署，SkillHub 通过 Service DNS 连接 | [详细指南第 4 节](./frontend-analytics-guide.md) |

## 没有统计平台时，先部署，再拿 UUID

1. 按详细指南获取所选版本的 `runtime.sh`，固定运行目录，保留原有业务配置。
2. 执行 `analytics-up`，脚本获取配套文件并启动 Umami 和它自己的 PostgreSQL。
3. 打开脚本输出的管理地址；默认绑定本机 `127.0.0.1:8300`，远程机器通过 SSH 转发访问。
4. 使用初始账号登录后立即修改密码，在网站管理中创建网站，取得网站 **UUID**。
5. 执行 `analytics-enable --website-id <网站UUID>`，脚本写入配置并重新创建 Web。
6. 刷新页面，触发已配置的动作，并到 Umami 核对实际记录。

**启动统计平台不需要网站 UUID。** UUID 在平台启动后创建网站时取得。SkillHub 也可以先按默认关闭状态正常运行。

已有平台可以跳过部署步骤：Umami 需要网站 UUID，DataFinder 需要数字应用 ID 和采集域名。管理密码、API token、App Key 不得写入浏览器配置。

## 两个独立项目，按需连接

浏览器向 SkillHub Web 的同源采集路径发请求，再由 Web 转发到统计平台。独立部署的 Umami 和 Web 通过共享私网连接，SkillHub 后端与业务数据库不加入该统计网络。

- `.env.release` 保存 SkillHub 业务及 Web 采集配置。
- `analytics/.env` 保存独立统计凭据、项目名和网络名。初始化后，管理命令使用这里的标识，不能靠改名迁移原数据。
- `analytics-disable` 关闭采集并重建 Web，统计平台继续运行。
- `analytics-down` 停止统计服务，数据与密钥保留。
- `down`／`clean` 处理 SkillHub；存在独立统计目录时，`clean` 保留该目录和数据。重新部署业务前需要恢复业务配置。

旧配置迁入、多个实例、升级与恢复的完整说明见[详细指南](./frontend-analytics-guide.md)。

## 如何确认接入成功

- 配置关闭或未获授权时，新页面不加载采集模块、SDK，也不发送统计请求。
- 开启后，Network 中应只出现配置允许的事件，不包含搜索词、真实资源路径或登录凭证。
- 平台故障时，搜索、上传、下载等原业务仍应正常。
- HTTP 200 不等于记录已经入库；还要到统计平台核对。DataFinder 同时要求响应 `e:0`、`sc:1`。

动作表示用户意图，例如“点击下载”，不代表下载或安装成功。`consent=granted` 是部署方授权，不代替逐用户同意弹窗；需要逐用户同意时，按指南接入现有授权组件。
