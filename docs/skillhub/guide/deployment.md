# Docker Compose 部署与升级

使用已有 Registry 安装技能不需要部署服务。只有需要自托管 Registry 时才执行本页流程。源码开发使用 `make dev-all`；容器回归使用 `make staging`；正式单机交付使用 `compose.release.yml`。集群部署见 [Kubernetes](kubernetes.html)。

## 一键脚本部署

一键安装仍使用原入口，不要求先配置 SMTP；不启用邮件时，Registry 的部署和使用流程保持不变。正式实例使用固定且可持久保存的目录：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up \
  --home /opt/skillhub \
  --public-url https://skills.example.com
```

目录与域名为示例，执行账号必须能写入目录并访问 Docker；公开地址不会自动配置 DNS、TLS 或反向代理。记录脚本输出的 **Runtime dir** 和 **Lifecycle commands**；已有实例后续使用输出的同一实例命令，不改用示例目录。不指定 `--home` 时默认目录是 `${TMPDIR:-/tmp}/skillhub-runtime`，不是 `~/.skillhub`。

脚本下载 Compose 清单和 `.env.release.example`，仅在私有 `.env.release` 不存在时创建它；已有文件及 SMTP 密钥保留。该目录不包含 Makefile 和源码校验工具，不要在 Runtime dir 执行下面源码部署专用的 `make validate-release-config`。

| 操作 | 执行方式 | 说明 |
|---|---|---|
| 修改 SMTP 或其他环境配置 | 编辑原 Runtime dir 的 `.env.release`，重跑该实例 up 命令 | 环境变化后更新容器；不能仅 restart |
| 更新镜像 | 保留原 home 和来源参数，使用目标发行对应的 up 命令 | pull 只下载镜像，不启用新容器 |
| 固定发行 | 使用匹配的已发布脚本、`--ref` 和 `--version` | ref 选择 GitHub 配置来源，version 选择镜像；只指定一个不构成完整固定 |
| 查看与停止 | 使用输出的 ps、logs、down 命令 | down 不附加 `-v`；不要用 clean 作为排障步骤 |

原部署使用 `--aliyun`、镜像仓库或额外 Compose 配置时，必须保留这些选项。`--aliyun` 的运行文件来自 OSS，不能仅凭 `--ref` 推定 OSS 文件已固定到某 Git tag；升级前核对实际文件来源与镜像版本。

### 配置业务邮件

目标发行须包含[业务邮件通知改动](https://github.com/iflytek/skillhub/pull/1004)。在后台先关闭业务邮件，再编辑原运行目录的 `.env.release`，填入 SMTP 地址、账号／授权码、端口、TLS 模式及获准发件地址；文件权限保持 `600`，不提交或分享。不要重新复制环境模板覆盖数据库、存储或邮件凭据。

重跑原 up 命令后先核对健康和 SMTP 配置，再由超级管理员在 **系统配置 → 业务邮件通知** 设置真实站点地址、摘要周期，并在协调窗口受控启用；未验邮测试账号再进入 **通知设置** 验邮，已验邮测试账号直接触发新业务事件，验证即时或摘要。平台关闭时不能测试通知验证码或业务邮件；启用期间既有合格订阅用户的新事件也可能发信，并非测试账号隔离。失败则在后台关闭且不盲目重试 UNKNOWN，通过后恢复测试偏好并正常开放。仅轮换 SMTP 凭据不要求全部用户重新验邮，账号邮箱变化后才必须重新验证。SMTP 凭据属于部署配置；平台开关、周期、地址已入库后由后台管理；用户验邮及类别偏好独立管理，不能通过改环境变量替代。

旧配置中的空 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST` 会被包含本次修复的一键脚本移除，明确主机例外保留；不关闭证书校验。仍使用旧脚本时应手动删除空覆盖再重建。默认不设置该变量，不能填写 `*`。

邮件验收至少包括：真实收到验证码、即时事件收件、同窗口摘要合并、用收件账号打开邮件明细。后台显示配置存在或容器健康，均不等于实际收件成功。升级保留私有配置；已有平台设置不被部署初值覆盖。完整参数、首次配置、轮换、关闭及失败定位见[邮件运维手册](https://github.com/iflytek/skillhub/blob/main/docs/notification-email.md)。

## 定制站点品牌与首页

目标发行需要包含站点展示设置功能。一键部署入口和 `.env.release` 不需要增加品牌参数。首次安装保持现有 SkillHub 界面；由超级管理员登录控制台，在 **系统配置 → 站点展示** 修改实例配置。

| 分组 | 可配置内容 | 默认与边界 |
| --- | --- | --- |
| 站点品牌 | 显示名称、Logo、浏览器图标 | 未自定义时使用 SkillHub 名称与内置图片；不修改 API、CLI 或技能坐标 |
| 首页 | 介绍型或目录型 | 介绍型保留原首页结构；目录型优先展示搜索和已有技能区块 |
| 首页文案 | 各界面语言的标题、说明 | 支持中文、英文、韩文、俄文；当前语言 → 英文自定义文案 → 产品默认文案 |
| 首页区块 | 热门、最新、快速入门 | 关闭区块不改变用户的数据权限，也不关闭对应业务 API |
| 页脚 | 显示或隐藏 | 默认显示现有页脚 |

目录型首页保留品牌介绍和搜索入口，按现有可见标签浏览技能，并展示启用的热门、最新区块。标签较多时可展开，快速入门默认收起；无结果与加载失败分别显示提示，失败可重试。即使关闭所有可选区块，也保留搜索和完整目录入口。独立技能市场 `/skills` 不受首页区块开关影响。

### 编辑、预览与保存

1. 在站点展示区域加载当前设置。更改名称、首页模式、文案或显示选项。
2. 需要图片时上传 PNG 或 JPEG，单文件不超过 2 MiB、宽高各不超过 1024 像素；服务器验证后统一转换为 PNG。浏览器图标也使用 PNG，不接受 SVG、ICO 或 WebP。图片上传后即可通过独立资源地址公开读取，即使设置尚未保存，也不要上传敏感图片。
3. 使用预览核对当前草稿；预览不修改访客看到的内容。分组恢复默认也只修改草稿。
4. 点击保存，成功后当前客户端更新展示；其他已打开页面在重新获取配置或刷新后生效。不需要重建前端镜像或重启容器。
5. 保存失败时保留草稿。版本冲突表示其他管理员已经保存，先重新加载最新设置并核对，再应用仍需保留的修改；不要反复覆盖提交。

修改期间离开页面会提示未保存内容。设置只接受普通文本和受控图片资源，不支持自定义 HTML、CSS 或脚本。用户自己选择的主题、语言不被管理员品牌设置覆盖。

### 升级、备份与失败定位

配置保存在原 PostgreSQL 的 `system_setting` 表中，图片保存在实例当前使用的文件存储中。升级沿用原数据库和存储，不会通过容器启动重新写入品牌初值；无需在新版本重复修改源码。

备份和恢复必须同时覆盖数据库及本地存储卷或对象存储，详见本页备份与恢复流程。替换图片或恢复默认不会立即删除历史上传对象，以免破坏已有引用与缓存；历史对象清理不在本次功能范围。只有数据库备份而没有品牌图片对象，恢复后可能出现缺图；不要为了恢复默认界面清空数据库或数据卷。回退镜像不等于恢复数据库。

| 现象 | 检查位置与处理 |
| --- | --- |
| 看不到编辑入口或保存被拒绝 | 核对当前账号是否为超级管理员；普通成员只能读取公开展示信息 |
| 上传被拒绝 | 核对文件真实格式、2 MiB 限制和 1024 像素限制；不要只修改扩展名 |
| 保存后另一浏览器仍显示旧值 | 刷新页面重新获取配置；确认访问的是同一个实例 |
| Logo 加载失败 | 浏览器网络面板查看品牌资源请求；核对图片对象与持久存储是否保留，界面会回退内置图片 |
| 配置读取失败 | 查看公开展示请求与后端日志，修复连接后重新加载；默认界面回退不代表自定义配置被删除 |

## 准备配置

以下为已有源码仓库的部署方式；一键安装实例使用上一节的 Runtime dir 流程。需要 Docker、Docker Compose，以及与目标版本匹配的仓库文件。在仓库根目录执行：

```bash
cp .env.release.example .env.release
```

编辑 `.env.release` 后再启动：

| 配置 | 需要确认 |
| --- | --- |
| `SKILLHUB_VERSION` | 固定已发布版本，避免升级时无意更换镜像；`latest` 跟踪稳定发布，`edge` 跟踪主分支构建 |
| `SKILLHUB_PUBLIC_BASE_URL` | 用户实际访问的地址，生产环境使用 HTTPS |
| `WEB_PORT` / `API_PORT` | 默认分别为 80 / 8080，确认端口可用 |
| `SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET` | 自行生成至少 32 字符的随机值；默认占位值会阻止启动 |
| `POSTGRES_PASSWORD` | 设置自己的数据库密码 |
| `BOOTSTRAP_ADMIN_ENABLED` / `BOOTSTRAP_ADMIN_PASSWORD` | 是否初始化首个管理员；启用时必须更换示例密码 |
| 存储配置 | 默认及可选配置以模板为准；外部存储按[对象存储手册](https://github.com/iflytek/skillhub/blob/main/docs/object-storage.md)配置 |

`.env.release` 包含敏感信息，不要提交或分享。已有数据库的密码不会因修改环境变量自动轮换。发布环境不要启用 `local` profile 的 Mock 登录。

## 启动与确认

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
docker compose --env-file .env.release -f compose.release.yml ps
docker compose --env-file .env.release -f compose.release.yml logs --tail=100 server
```

校验通过后再启动。首次启动或升级时，后端会执行 Flyway 数据库迁移。访问配置的公开地址，使用自己配置的管理员凭证登录，并验证搜索、下载以及一次符合权限的发布/审核流程；容器健康不代表这些业务路径已通过。

Suite 审核写入在单机 release Compose 中默认开启；Suite Bundle 确认默认关闭。需要批量导入时，按 [Bundle 导入](suite-bundle.html#部署开关)启用对应开关。滚动升级新旧后端共存时，另按[套装部署顺序](https://github.com/iflytek/skillhub/blob/main/docs/25-skill-suites.md#部署顺序)操作。

## 可选的业务事件 OTLP Logs

业务分析默认关闭，与 Trace 独立。生产使用 `runtime.sh business-logs-enable` 接已有接收端，
或用 `business-logs-up` 启动有明确下游的可选 Collector。本地使用 `make business-logs-demo-up`，
自动适配主机 Java 与容器后端。runtime 发布部署同时传 `--ref <tag> --version <tag>`：前者选文件，后者只选镜像。
指标需显式启用 `health,info,metrics,prometheus` 并重建 server。
[运行手册](https://github.com/iflytek/skillhub/blob/main/docs/business-event-logs.md) 提供生产接入、
本地试用、Helm Secret、管理员查询、排障与停止步骤。下载准备不代表完成或安装成功。

## 升级、停止与排障

升级前保存当前镜像版本、配置，并备份数据库和技能对象存储。修改 `SKILLHUB_VERSION` 为目标发布版本，重新执行校验、`pull`、`up -d` 和业务验证。回退镜像不等于回退数据库；先检查目标迁移的兼容性。

```bash
# 停止本实例；不要附加 -v，除非明确要删除数据卷
docker compose --env-file .env.release -f compose.release.yml down
```

启动失败先查看 `ps` 和服务日志，核对数据库、Redis、存储和公开地址配置。提交问题时提供镜像版本、脱敏日志和请求 ID，不提供 `.env.release` 或凭证。更多 Redis、反向代理、采集服务和迁移配置见[运维手册](https://github.com/iflytek/skillhub/blob/main/docs/09-deployment.md)。

## 配置检查与上线验收

发布模板和 Compose 需来自同一版本。普通 `.env.release` 是 Compose 的替换值来源，只有清单引用的变量才会进入进程。额外配置应通过自己的 override 显式传入，不能只在环境文件追加任意属性。重建服务后检查行为；`restart` 不更新容器环境。

| 检查层 | 操作与通过标准 |
| --- | --- |
| 配置 | `make validate-release-config` 无错误；警告逐项确认，不公开包含配置值的输出 |
| 依赖和进程 | `ps` 中相关服务运行，`server` 日志没有持续连接失败或迁移失败 |
| 对外入口 | 浏览器从最终域名访问、登录；CLI 对同一 Registry 执行 `whoami` |
| 业务与持久化 | 发布测试技能，跟踪最终状态，安装固定版本并检查 `SKILL.md`；重启后资源和登录能力仍可恢复 |
| 运行依赖 | 技能包存储可读写；扫描服务可达，真实扫描任务能完成，而非仅容器健康 |

生产域名走 HTTPS 时设置 `SESSION_COOKIE_SECURE=true`。子路径部署需同时配置 `SKILLHUB_WEB_BASE_PATH=/skillhub/`、同源 `SKILLHUB_WEB_API_BASE_URL=/skillhub` 和带 `/skillhub` 的公开地址。只设置公网 URL 不会自动配置 TLS 证书、代理或 DNS。

| 现象 | 首先检查 |
| --- | --- |
| 校验器拒绝启动 | 数据库/管理员密码是否仍为示例，匿名下载密钥长度，存储 provider 和 S3 必需项 |
| 页面正常但 API 502 | `server` 日志、数据库迁移、`SKILLHUB_API_UPSTREAM` 和容器网络 |
| 登录回调或设备码地址错误 | `SKILLHUB_PUBLIC_BASE_URL` 是否与真实域名和子路径一致 |
| 配置保存后没变化 | 是否重建正确 Compose 项目，是否保留原 override，变量是否实际透传 |
| 上传进入扫描但不结束 | 扫描器日志、配置、后端到扫描器网络；不要将失败视为审核通过 |

备份至少覆盖 PostgreSQL、技能与头像存储、私有运行配置及加密密钥。恢复时使用匹配的应用版本和配置，先在隔离环境验证恢复后的登录与下载；不要以删除卷或清空数据库作为升级失败的默认处理。
