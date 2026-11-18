# GitHub 公开 Skill 同步：管理员运行手册

对象存储可选部署、凭证和迁移说明见[对象存储手册](object-storage.md)。

本功能只采集 GitHub 公开仓库。SkillHub Java 保存配置、来源、任务、审核和发布状态；独立 Go 服务轮询 Java 的内部接口执行发现与采集。Go 停止后，Java 中已保存的任务和检查点仍在；重新启动 Go 后由租约机制继续处理。功能仍在实施验收阶段，生产启用前须完成 [OpenSpec 验收任务](../openspec/changes/add-github-skill-sync/tasks.md) 中未勾选的部署检查。

## 启用前配置

1. 检查 `@github` namespace 和 `github-sync-publisher` 账户没有被现有用户数据占用；V76 迁移遇到冲突会失败并回滚，见[部署文档](09-deployment.md)。
2. 在 Java 与 Go 两侧配置同一条至少 32 字符的服务密钥。Compose 分别使用 `SKILLHUB_GITHUB_SYNC_SERVICE_TOKENS`（Java，可配置轮换窗口内的多枚）和 `SKILLHUB_GITHUB_SYNC_SERVICE_TOKEN`（Go，单枚）。Helm 的 `secrets.githubSyncServiceToken` 或 `existingSecret` 中的 `github-sync-service-token` 会注入两侧。密钥放在受保护的环境文件或 Kubernetes Secret，不要放入命令参数、仓库或日志。
3. 在管理后台先设置全平台、单 namespace 的未解决项高低水位，再启用采集。新部署的水位为空，Java 此时拒绝新的逐包准入。候选预算初值为每次发现租约 100、每 namespace 待验证 1000；水位和预算都应结合试运行调整，字段与范围见[管理接口说明](api/github-sync-admin.md#积压准入水位)。
4. 如需保存系统级 GitHub Token，Java 还须配置 `SKILLHUB_GITHUB_SYNC_CREDENTIAL_KEYS` 与 `SKILLHUB_GITHUB_SYNC_ACTIVE_CREDENTIAL_KEY_VERSION`。不配置时仍可匿名读取公开资源，但后台不能保存 Token。只为公开读取创建低权限凭证，不授予私有仓库或写入权限；具体访问能力以 [GitHub 官方端点权限说明](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens) 和实际受控请求为准。Token 在 Java 侧加密保存，Go 只在领取任务时获得当次凭证；更换加密密钥时，旧版本须保留到旧密文完成替换。
5. Go 的临时目录必须是独立受限目录。默认单仓库工作区 512 MiB、总工作区 512 MiB、保留空闲 128 MiB，部署提供 768 MiB 内存型临时卷。增加仓库并发数时，同时检查 `REPOSITORY_WORKERS × MAX_WORKSPACE_BYTES ≤ TOTAL_WORKSPACE_BYTES`，且临时卷至少容纳总预算与保留空闲。同一目录一次只供一个 Go 进程使用；多实例各自使用独立临时目录。S3/MinIO 模式还须设置 Go 的 `SKILLHUB_GITHUB_SYNC_ALLOWED_S3_HOSTS` 和 Java 的 `SKILLHUB_GITHUB_SYNC_STAGING_CAPACITY_LIMIT_BYTES`。
6. Java 校验摄入包和生成待审版本时也会使用自己的系统临时目录存放 ZIP 与解压文件。每次处理前会检查至少有“当前 ZIP 大小 + 当前发布包大小上限”的可用空间；这只是单次检查，不预留并发处理容量。部署时要让 Java 临时目录可写，并为同时运行的校验和发布留足空间；它不使用 Go 的专用临时卷。

在 staging Compose 中，Java 的 `SKILLHUB_STORAGE_S3_ENDPOINT=http://minio:9000` 用于生成 Go 可访问的暂存 PUT 签名，`SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT=http://localhost:9000` 用于宿主机访问的下载链接。Go 的允许主机填写 `minio:9000`；不要把只供宿主机访问的 `localhost:9000` 当作 Go 容器内的对象存储地址。

Go 镜像由 `tini` 作为 PID 1 转发停止信号并回收 Git 子进程。停机后以 Java 持久租约和检查点恢复；需分别验证本地存储与 MinIO 模式。

发布版 Compose 在启动 Java 前运行一次 `storage-permissions`，把命名卷的挂载根目录设为镜像中 `app` 用户的 UID/GID `100:101`；Docker 新建卷会覆盖镜像内目录的属主。基础 K8s Deployment 和 Helm 使用 `fsGroup: 101` 处理本地存储 PVC；实际存储驱动仍须支持该权限策略。卷权限不正确会导致暂存包上传失败，部署验收必须检查非 root 用户的实际写入能力。

Go 到 Java 的内部 HTTP 连接默认直连。跨主机部署时要在可信网络和 TLS 边界内传输服务密钥与当次 GitHub Token；本功能不要求额外部署 mTLS、队列或 KMS。Compose 从 `.env.release` 的 `SKILLHUB_GITHUB_SYNC_HTTP_PROXY`、`SKILLHUB_GITHUB_SYNC_HTTPS_PROXY`、`SKILLHUB_GITHUB_SYNC_NO_PROXY` 注入标准代理变量；Helm 对应 `githubSync.httpProxy`、`githubSync.httpsProxy`、`githubSync.noProxy`。代理只用于 GitHub 出站访问，不用于内部 Java 请求或对象存储暂存 PUT。

## 官方深度探索的启用与观察

管理员统一入口为 `/dashboard/github-sync`，打开“采集配置”设置自动探索；旧 `/dashboard/github-deep-discovery` 链接会转到这里。自动探索默认关闭。先完成 Java 的数据库迁移及全部 Java/Go 实例升级，再由管理员启用；不要让早期显式声明官方能力但不理解新分片预算的 worker 混跑。Go 生产入口自动声明官方探索及 `platform-pause/v1` 能力，不需要另加探索环境开关。管理员保存的是启用意图，不是立即完成一次搜索的命令，页面没有“执行一次”按钮。

启用后的正常链路：Java 周期性生成到期查询任务，Go 轮询领取，分页搜索并回报候选；Java 保存去重仓库、多个发现出处与检查点，再通过既有仓库采集链路检查 Skill、生成版本并进入独立同步审核队列。发现仓库数量不等于 Skill 数量，也不等于已发布数量。

- **概览与采集配置**：查看准备状态、等待原因和最近业务进度；探索配置集中在抽屉中。服务“已联系”只说明心跳有效，不能据此判断 GitHub 请求正在执行。
- **仓库**：统一查看自动探索、指定仓库及其他配置发现的仓库，按登记阶段、来源状态、出处和仓库坐标筛选；同一个 GitHub 仓库合并展示多个出处。服务端分页，默认每页20条。新结果不会自动打乱当前页，使用刷新查看。超过允许浏览范围时缩小筛选；总数不是当前已下载的条数。
- **详情抽屉**：按需查看来源出处、Skill 路径结果，以及已有来源、运行和审核入口。关联运行被保留期清理后可能无法再打开，不表示仓库或版本被删除。
- **资源预算**：节省、均衡、加速都保留同样渠道。实际预约规则和试运行初值见[设计第3节](../openspec/changes/add-github-skill-sync/deep-discovery-design.md#3-预算凭证与暂停)。预算不代表 GitHub 允许额度，也不统计后续全部下载成本；主额度、共享冷却与容量保护仍可使采集暂停。
- **多个 Token**：用于同一系统级凭证池。不能把显示余额简单相加，也不能保证每枚 Token 有独立额度。池内凭证全部不可用时等待修复，不自动退回匿名继续冲击请求。

关闭探索后保留进度、候选和出处，暂停官方新发现及仅由它触发的未登记候选采集；已登记来源和其他独立启用入口按自身开关继续。重新开启由 Go 续领持久进度，无需手工创建运行。到达预计重试时间只表示任务可能可领；Go 停机时仍不会执行 GitHub 请求，恢复联系后才能继续。

只需停用官方新发现时关闭自动探索；需要暂停整个平台采集时使用“暂停平台采集”，观察在途任务到安全边界确认。已接收包可完成结算，不删除版本或历史作为回退。回退到不理解官方任务的旧 Java 前，需要隔离或排空官方队列并停止对应 worker；单纯关闭页面不能约束旧版本服务。完整反向版本回退仍在验收范围内，不推荐直接降级数据库。

验证范围见[验证说明](../openspec/changes/add-github-skill-sync/verification.md)。受控 GitHub fixture、合成仓库性能数据与公网采集结果必须分别记录；不得把测试候选当作真实探索成果。

## 从源码构建 Java 镜像

从仓库根目录使用 `server/Dockerfile` 构建时，容器不会自动读取宿主机的 Maven settings。需要构建期 Maven 镜像源、代理或仓库认证时，可通过可选的 BuildKit secret 提供受保护的 settings 文件：

```bash
docker buildx build --load -f server/Dockerfile -t skillhub-server:local \
  --secret id=maven_settings,src="${MAVEN_SETTINGS_FILE:?请先设置受保护的settings.xml文件路径}" \
  server
```

`MAVEN_SETTINGS_FILE` 指向部署方自行维护的文件。无需自定义 settings 时去掉 `--secret` 参数，沿用 Maven 默认配置；提供时仅在两条 Maven 构建指令期间挂载到 `/root/.m2/settings.xml`，不通过 ARG、COPY 或最终镜像保存。此配置不替代 Docker 拉取基础镜像或 Maven Wrapper 下载自身发行包的网络配置。Secret 用法见 [Docker 构建密钥说明](https://docs.docker.com/build/building/secrets/)。

源码镜像执行 `clean package`，防止共享构建上下文中的宿主 `target/` 产物被误当成本次源码编译结果；`Dockerfile.dev` 仍用于明确选择预构建 JAR 的流程。

## 启动、停用和恢复

本地开发使用宿主机 Java 与 Go：在同一 shell 导出匹配的 `SKILLHUB_GITHUB_SYNC_SERVICE_TOKENS` 和 `SKILLHUB_GITHUB_SYNC_SERVICE_TOKEN` 后，先运行 `make dev-all`，再在另一个终端运行 `make dev-github-sync`。第二个终端需继承同一枚 Go 服务密钥；该目标构建并前台启动 Go，不修改开发 Compose 的依赖服务。默认临时目录为 `.dev/github-sync-tmp`，工作区、总预算和保留空闲值可用对应环境变量调整。

Go 启动时独占临时目录，只清理上次异常退出遗留的 `skillhub-github-package-*` 包工作区；其他文件不会被清理。若目录已被另一 Go 进程占用，启动会失败并输出 `TEMP_WORKSPACE_UNAVAILABLE`，应检查重复实例或目录配置。清理本地遗留包不改变 Java 持久化的任务、固定 commit 和路径检查点。

发布版 Compose 在受保护的 `.env.release` 已完成配置后，先只检查合并配置，再启动独立 Go 服务：

```bash
docker compose --env-file .env.release -f compose.release.yml --profile github-sync config --quiet
docker compose --env-file .env.release -f compose.release.yml --profile github-sync up -d github-sync
docker compose --env-file .env.release -f compose.release.yml --profile github-sync ps github-sync
```

临时停止领取新任务和恢复轮询：

```bash
docker compose --env-file .env.release -f compose.release.yml --profile github-sync stop github-sync
docker compose --env-file .env.release -f compose.release.yml --profile github-sync up -d github-sync
```

停用某条发现规则或指定入口，使用后台“技能同步 → 采集配置”的启停操作；这不会删除已登记来源或已发布 Skill。TEAM 来源在对应 namespace 管理页启停。停用后，已排队或等待重试的发现任务不会再领取；已经取得租约的任务不会被强制中断。恢复入口后，原等待任务可继续领取。Helm 默认 `githubSync.enabled: false`，启用和 Secret 字段见 [Chart 说明](../charts/skillhub/README.md#可选的公开-github-采集服务)；基础 K8s Deployment 默认不在 Kustomize 清单中，见 [K8s 说明](../deploy/k8s/README.md)。

## 后台操作顺序

1. 打开“技能同步”，日常页面只有“概览、仓库、运行记录”三个页签。先看概览的准备清单：采集服务、准入水位和采集入口分别显示实际状态；凭证可选，读取失败或未知不会显示为就绪。页面不会替管理员自动启用探索。
2. 点击“采集配置”打开抽屉。自动探索设置启停、预算及排除项；指定仓库只需填写 GitHub URL 或 `owner/repository`，固定进入 `@github`。重复添加返回已有入口，不重新启用已停用配置。尚未解析仓库 ID 的配置留在抽屉中展示执行状态，不虚增仓库数量。“其他采集方式”折叠展示指定账号和自定义规则；搜索预览沿用既有 Go 执行机制，预览不创建正式来源。首期不采集私有仓库。
3. 在“仓库”查看合并后的登记与采集状态，进入抽屉按需加载出处、Skill 路径、关联运行与审核。注册为来源不代表已发布。首次发现后的 HEAD 检查只记录仓库提交，后续 FULL 任务才逐包采集；相同提交的周期任务只检查 HEAD。提交、忽略规则变化或既有手动重扫/路径重试仍受水位、额度及重试约束。部分目录失败不会丢弃其他已接收包。
4. 在“运行记录”按类型和状态筛选。真实运行类型是“发现”与“仓库检查与采集”；HEAD_ONLY/FULL 阶段在详情中区分。列表时间为创建时间，单目标显示安全摘要，多目标显示数量；发现运行可能没有 Skill 路径结果。运行历史与仓库当前状态不是同一时间口径。
5. 点击“系统设置”打开容量保护、采集周期和系统凭证配置。Token 仅在添加表单中输入，关闭后清空，不回显明文。同步审核保持独立队列，未通过审核的新版本不会成为市场最新版本。
6. TEAM 管理员继续在所属 namespace 管理公开来源；本平台页面不新增 TEAM 管理入口。TEAM Skill 默认 `NAMESPACE_ONLY`，`@github` 已发布 Skill 全局可见。目录消失、部分扫描、来源暂停和不可达都不会自动删除或撤回已发布版本。

### 平台暂停与恢复

“暂停平台采集”只约束 `@github`，不影响 TEAM，也不修改各采集入口自己的开关。暂停后阻止新任务领取及新包准入；Go 将在途任务停在安全边界，保存确认进度后回报。尚有任务未确认时显示“暂停中”；租约过期或服务离线不会被当作已经停止。全部确认或原本没有在途任务时才显示“已暂停”。界面同时展示尚未确认和过期未确认数量。

已由 Java 正式接收的包可继续校验、安全扫描并进入审核；仅上传到暂存存储不等于已接收。暂停期间仍可保存配置，恢复后按原进度和独立启停状态继续。恢复采集只改变控制意图，不代表 Go 已在线或任务已开始。若服务离线，先恢复服务连接；不要靠删除任务消除“暂停中”。

V119 引入持久平台控制与任务确认记录。Java/Go 必须配套升级：新 Java 只向声明 `platform-pause/v1` 的 worker 发放新的平台任务，旧 Go 会等待升级，TEAM 沿用原规则。在途旧 worker 无法证明安全停止时保持待确认；升级前应安排其有界停止。停止观察目标间隔为5秒，实际确认时间仍受在途请求超时和网络状态影响，不是即时停止承诺。

公开市场的“开源技能”入口为 `/github`，页面注明首期仅收录 GitHub 公开仓库；调用现有市场搜索并固定 `@github` namespace，仅显示当前已发布、公开可见的 Skill；可以搜索、按最近更新或下载量排序及分页。页面数量来自市场查询，不包含发现候选或待审包。点击 Skill 后，在详情页查看来源仓库、路径、固定提交和许可证。当前页面不展示尚无可靠聚合接口的“精选仓库数量”或 GitHub 星标数。页面中性表面、文字和边框继承全站明暗主题，局部语义颜色 token 仅用于状态、数字和细线，定义见 `web/src/features/github-sync/github-sync-colors.css`。

页面权限、状态字段、配置请求和手动操作的精确合同见[管理接口说明](api/github-sync-admin.md)。

审核页的文本差异组件通过本地 Blob Worker 计算差异，Web 页面的 CSP 包含 `worker-src 'self' blob:`，`script-src` 不增加 `blob:` 来源。部署侧若额外为 HTML 添加 CSP 响应头，也需保留该 Worker 来源；多个 CSP 会同时约束页面，不能仅靠页面内的策略放宽响应头。浏览器若报告差异 Worker 被阻止，应先检查实际 HTML 响应的策略，避免组件回退到主线程计算。

## 开源技能仓库快照

打开「开源技能 → 按仓库浏览」，在详情中查看快照记录、当前可下载成员和相邻可用快照的版本差异。新版成员仍在审核时，默认展示最近的可用快照。历史技能缺少完整采集记录时仍可浏览，后续完整采集会生成首个可验证快照。

下载 ZIP 时，服务端重新检查该快照的成员版本与当前访问权限。ZIP 中的 `snapshot.json` 包含 Git 提交、快照编号、每个技能的确切版本、独立包文件名和 SHA-256。任一成员撤回或包文件缺失时，整次下载失败。单次上限为 250 个成员、256 MiB 技能包数据。每个 Java 实例最多同时处理两个快照下载，部署时应在 Java 临时目录中为它们留出最多约 512 MiB 额外空间。

## 故障排查

| 现象 | 先检查 | 处理 |
| --- | --- | --- |
| Go 容器未就绪 | `ps github-sync`、Go `/health/ready`；Java 内部地址与服务密钥是否匹配 | 修复配置和网络后重启 Go；就绪要求两个工作池都曾成功访问 Java 领取接口。 |
| 没有新的待审包 | 平台是否暂停、水位是否设置或已到高水位、来源/规则是否启用、Go 是否运行且支持平台暂停能力 | 先恢复容量或修正配置；高水位时已登记来源只检查 HEAD，新包不会下载。 |
| 任务显示 `CAPACITY` | 临时卷、单仓库/总工作区预算、最低空闲值 | 合规单包装不下时保留固定 commit 与检查点，约 24 小时后再试；修正容量配置后再观察。 |
| 任务显示 `LOCAL_DISK_BUDGET` | 临时卷实际剩余空间和容器内存 | 运行中接近空间保护线时约 15 分钟后重试；检查并发数与临时卷容量。 |
| 摄入校验或待审版本生成显示 `LOCAL_CAPACITY` | Java 容器的系统临时目录可用空间，区别于 Go 临时卷和对象存储容量 | 扩充 Java 临时空间或降低同时处理量后，等待持久重试；不要删除已接收的暂存对象。 |
| GitHub 限流或凭证失效 | 后台“凭证”中 `SEARCH`/`CORE` 额度、重置时间、认证状态 | 等待限流重置；只有认证失效才替换 Token，不用反复轮换来规避限流。 |
| S3/MinIO 上传或摄入失败 | 预签名 PUT 的内部主机是否在 Go 允许列表、Java 暂存前缀预算与对象实际大小 | 按任务和路径错误码排查；不要把票据声明的大小误认为对象存储接收时的硬限制。 |
| 上游路径显示缺失 | 最近一次扫描是否完整、来源是否暂停、忽略规则是否变化 | 先查看固定 commit 和扫描覆盖状态；部分扫描不据此认定 Skill 已删除。 |

服务指标与暂存容量告警见[内部协议说明](api/github-sync-internal.md#暂存容量监控)。Go 镜像在 8090 端口提供 `/health/live`、`/health/ready`、`/metrics`。暂存对象和容器日志的保留、对象存储整体剩余容量由部署侧监控；`/metrics` 不暴露 Token 或 Skill 正文。
