# GitHub 公开 Skill 采集服务

此目录是独立 Go 服务。它从 SkillHub Java 的内部接口领取发现或仓库任务，向 GitHub 读取公开仓库，逐个 Skill 包上传到 Java 授权的临时对象，再回报采集结果。来源配置、任务状态、审核和发布归 Java 管理。首期不读取私有仓库。

## 当前接入状态

Go 的发现、仓库树枚举、逐包采集、续租和回报代码已接入单进程双工作池，Java 的配置、来源转换、Skill 版本与审核流程也已接入。本地存储与 MinIO 应分别验收；ARM64 整镜像与长期容量须独立验证。启动容器本身不会让未经审核的 Skill 出现在公开市场。

官方深度探索已有独立搜索执行路径：metadata、README 和 topic 查询按固定 UTC 整秒半开窗口执行，逐页检查控制状态，保存分页/页内游标和出处，预算耗尽后保留 IN_PROGRESS。截断或不完整页面中已返回的仓库仍按候选预算保存；结果上限内可访问的页面继续采集，缺口原因跨页保留，最终按预算提交时间子分片或记录 GAP。GAP 按持久游标递增退避（暂为 5 分钟至 6 小时），普通工作预算续排暂为 1 秒；这些值仍需调度校准。批次响应丢失可重放相同请求，坏检查点请求服务端安全重放。非空二级分片暂不支持；SPLIT 由 Java 快照中的剩余深度与根范围子任务额度控制，额度为 0 或 1 时不能二分。父分片交接后不代表全部覆盖；恢复到已确认的 SPLIT 检查点只重试父任务结算，不重查父范围。

带 `official-pause/v1` 标记的候选仓库任务已接入控制检查：metadata/HEAD、每个树请求、下一个包准入及结算前查询；已获准包完成上传和回报后再暂停。暂停保存固定 commit、路径进度和未完成子树，尚未取得 commit 时使用空值；已接收包由 Java 摄入记录保留。候选途中绑定来源或获得独立入口时按最新控制结果继续，来源自身暂停以 `SOURCE_PAUSED` 区分。

作者扩展使用独立 `official-owner/v1` 能力：按稳定账号 ID 获取当前 login，复用 public owner 仓库分页并逐仓库核对 owner ID，终页再次核验账号身份。身份查询与分页均计 CORE 请求预算，终页核验预留请求额度；检查点页码支持正 int32，达到上限仍有下一页时记录 GAP。作者列表不按 pushed 时间过滤、不做时间二分，GAP 保持 RETRY_WAIT；已确认检查点支持暂停与跨租约续跑。GitHub 元数据上报可选 ownerId，供 Java 的有效 seed 事务使用。

官方配额观察通过租约绑定的 `official-quota` 接口持久化，包含匿名请求；REST 的 metadata、HEAD、commit、tree、search、owner 请求以及可能联网的 Git fetch / sparse checkout / checkout 都有请求守门。主配额耗尽按资源退避；二级限流与观察持久化失败触发进程共享退避，不能通过更换官方任务 token 绕过。回报失败锁住当前租约后续请求，进程失败退避暂为 1、2、4、8、15 分钟；Git 无 REST 响应头只报 UNKNOWN，失败附短退避，成功不虚构余额。已生成 artifact 的上传与结果回报不受 Git 请求守门影响。进程退避不是跨实例持久状态；跨实例冷却由 Java 的官方配额状态和控制 / 领取门控负责。

生产工作池通过 `ClaimWithCapabilities` 和 `HeartbeatWithCapabilities` 声明 `official-discovery/v1`、`official-checkpoint/v1`、`official-pause/v1`、`official-owner/v1` 和 `platform-pause/v1`；旧客户端方法仍可调用，但V119之后缺少平台暂停能力的实例不能领取平台任务，TEAM规则不变。Java 官方探索配置默认关闭，管理员启用后才允许领取官方任务。首次启用前必须整体升级 Java、数据库和 Go；不混跑早期显式 opt-in 的旧执行器。真实 GitHub 召回、预算档位校准和并发生产验收需要独立验证。策略目录及版本由 Java 维护。

平台 `@github` 的全部采集还受独立 `platform-pause/v1` 控制：Java 保存暂停意图，Go 在请求安全点及目标5秒观察间隔检查，停止外部工作并完成清理后由执行线程确认。暂停中不是已停止，离线与租约过期不能代替确认；TEAM不受平台开关控制。Java已接收的包可继续校验和审核，恢复不重置检查点或各入口独立启停。部署需配套升级Java/Go，操作见[管理员手册](../docs/github-sync-operations.md#平台暂停与恢复)，协议见[内部接口第9节](../docs/api/github-sync-internal.md#9-平台github采集安全暂停)。

## 构建与启动

```bash
cd github-sync-go
go build ./...
```

先在仓库根目录运行 `make staging` 准备预发布服务，再用同一个 Compose 项目启动 Go 容器：

```bash
SKILLHUB_GITHUB_SYNC_SERVICE_TOKEN='<至少 32 字符的随机服务密钥>' \
SKILLHUB_GITHUB_SYNC_SERVICE_TOKENS='<同一服务密钥>' \
docker compose -p skillhub-staging -f docker-compose.yml -f docker-compose.staging.yml \
  --profile github-sync up -d github-sync
```

上述命令是参数示例；正式环境应通过受保护的环境文件或容器 Secret 提供密钥，不把真实值写进命令历史、仓库或日志。发布 Compose 使用 `compose.release.yml` 和相同的 `github-sync` profile。默认不启动 Go 服务；Helm 对应 `githubSync.enabled: false`。

| 环境变量 | 用途 | 默认值或要求 |
| --- | --- | --- |
| `SKILLHUB_GITHUB_SYNC_JAVA_URL` | Java 内部合同根地址 | 必填；Compose 已设置。 |
| `SKILLHUB_GITHUB_SYNC_SERVICE_TOKEN` | Go 调用 Java 的服务身份 | 必填，至少 32 字符；Java 的 `SKILLHUB_GITHUB_SYNC_SERVICE_TOKENS` 须包含它。 |
| `SKILLHUB_GITHUB_SYNC_TEMP_ROOT` | 受限临时目录 | 必填绝对路径；镜像默认 `/var/lib/skillhub-github-sync`。同一目录一次只能供一个 Go 进程使用。 |
| `SKILLHUB_GITHUB_SYNC_DISCOVERY_WORKERS` / `SKILLHUB_GITHUB_SYNC_REPOSITORY_WORKERS` | 两类工作池并发数 | 各 1。 |
| `SKILLHUB_GITHUB_SYNC_POLL_INTERVAL` | 无任务或领取失败后的轮询间隔 | `5s`。 |
| `SKILLHUB_GITHUB_SYNC_MAX_WORKSPACE_BYTES` | 单仓库工作目录预算 | Compose/Helm 默认 512 MiB。 |
| `SKILLHUB_GITHUB_SYNC_TOTAL_WORKSPACE_BYTES` | 所有仓库 worker 的总预算 | Compose/Helm 默认 512 MiB。 |
| `SKILLHUB_GITHUB_SYNC_MIN_FREE_BYTES` | 临时目录预留空间 | Compose/Helm 默认 128 MiB。 |
| `SKILLHUB_GITHUB_SYNC_ALLOWED_S3_HOSTS` | 允许上传的对象存储主机和端口，逗号分隔 | S3/MinIO 模式必填；应与 Java 返回的内部预签名 PUT 主机一致。 |
| `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY` | GitHub 出站代理 | 按网络环境设置；Go→Java 内部请求与暂存对象上传直连，不走该代理。 |

Compose 从 `.env.release` 中的 `SKILLHUB_GITHUB_SYNC_HTTP_PROXY`、`SKILLHUB_GITHUB_SYNC_HTTPS_PROXY`、`SKILLHUB_GITHUB_SYNC_NO_PROXY` 显式注入对应标准代理变量；staging 覆盖文件也使用这三个宿主变量。留空时不会自动继承宿主机的代理配置。Helm 使用 `githubSync.httpProxy`、`githubSync.httpsProxy`、`githubSync.noProxy`。

镜像提供 `GET /health/live`、`GET /health/ready` 和 `GET /metrics`，端口为 8090。就绪状态要求发现池和仓库池都曾成功访问 Java 领取接口。`/metrics` 只暴露固定种类的低基数计数，不包含服务密钥或 Skill 正文。

运行镜像使用 `tini` 作为 PID 1，以回收 Git 子进程并转发停止信号；应避免直接由 Go 充当 PID 1 时无法完整回收 Git 子进程。停止 Go 后，Java 保存的租约和逐包检查点供下一实例续跑。

临时目录使用独立的 768 MiB tmpfs，内存限制默认 1.5 GiB；tmpfs 占用计入容器内存。采集会预检并逐包释放目录，仍应监控容器内存与容量阻塞状态。S3/MinIO 预签名 PUT 的大小由 Go 上传前和 Java 摄入前复核；不能把 URL 票据字段当作对象存储的接收时硬限制。

Go 启动时会独占临时目录，并只清理该目录下本服务命名的遗留包工作区（`skillhub-github-package-*`）。进程被强制终止后，重启可先释放这些本地文件，再按 Java 保存的固定 commit 和检查点续跑；同一临时目录已有运行中的 Go 进程时，新进程启动失败。多实例部署须为每个实例分配独立临时目录。

S3/MinIO 开启桶自动创建时，Java 会在签发首张预签名 PUT 票据前建桶。对象存储仍可能接收超过票据声明大小的直接 PUT；Java 在接收采集回报前按真实对象大小复核，超限对象标失败并清理。

Go 在下载包文件前，为 Git 对象、检出文件、ZIP 和至少 16 MiB 基础 Git 元数据预留工作区；预留不足的仓库任务回报容量延后，不启动该包下载。仓库树元数据的实际大小无法事先精确获知，因此 Git 每一步会复查真实目录占用和最低剩余空间，写 ZIP 前也会复查剩余空间。下载前估算不是磁盘硬配额。

若单包在当前配置下根本无法容纳，任务以 `CAPACITY` 延后约 24 小时，保留固定 commit 和已完成路径检查点；Java 释放本次包准入，不生成摄入包。若是运行时暂存空间逼近阈值，使用 `LOCAL_DISK_BUDGET` 延后约 15 分钟。两者都不会在同一租约内继续下载后续包；先检查临时目录容量、`MAX_WORKSPACE_BYTES`、`TOTAL_WORKSPACE_BYTES` 和 `MIN_FREE_BYTES` 的组合，再决定是否调整配置。

归档写入时如果先触及工作区剩余预算，或文件系统返回空间/配额不足，也按 `LOCAL_DISK_BUDGET` 延后；只有归档达到任务下发的上传大小上限才记为 `ARCHIVE_TOO_LARGE`。

需要复核公开 GitHub 的实际受限 Git 获取时，可在 `github-sync-go/` 运行 `SKILLHUB_GITHUB_SYNC_LIVE=1 go test ./internal/repository -run TestLiveAcquireFixedPublicSkill -count=1 -v`。它使用固定公开 commit，检查 ZIP 文件、摘要和临时目录清理；普通 `go test ./...` 不访问 GitHub。

测试专用的 `SKILLHUB_GITHUB_SYNC_LIVE_SMALL_VOLUME=1` 将上述用例的单包工作区预算设为 32 MiB、保留空闲设为 8 MiB，便于在小 tmpfs 中复核。容器复核时应使用非 root 身份、只读根文件系统和有硬容量限制的 tmpfs，按网络配置显式提供 GitHub 出站代理。该用例只处理一个小 Skill，不代表大仓库峰值或完整 worker 停机续跑。

同一固定 commit 的双 Skill 顺序采集可用 `SKILLHUB_GITHUB_SYNC_LIVE=1 go test ./internal/repository -run TestLiveAcquireTwoFixedPublicSkillsSequentially -count=1 -v` 复核。测试在首包关闭后确认临时目录为空，再采集第二个 Skill；输出分别给出归档和采集结束时的工作目录字节数。这是单仓库小范围演练，不是并发或大规模容量基线。

较多文件的公开仓库可用 `SKILLHUB_GITHUB_SYNC_LIVE_LARGE_REPO=1 go test ./internal/repository -run '^TestLiveAcquireLargerPublicRepositorySkill$' -count=1 -v` 复核一个固定 commit 的单 Skill 稀疏获取。此用例要求临时文件系统至少容纳 64 MiB 工作区及 16 MiB 保留空间；建议在有硬容量限制的临时卷中运行。它不覆盖完整 GitHub Tree API 枚举、多包并发或进程瞬时峰值。

仓库任务在第一个需要上传的包取得准入后，用 `git ls-remote --tags` 与只拉提交对象的 `fetch --filter=tree:0` 判定一次固定 commit 与仓库级 semver tag 的关系，随每个包回报为 `sourceTag`；失败、浅历史或超出预算只记为 `UNKNOWN`，不改变包结果。用 `SKILLHUB_GITHUB_SYNC_LIVE=1 go test ./internal/repository -run TestLiveSourceTagPublicRepositories -count=1 -v` 可对两个固定公开 commit 复核判定结果（`obra/superpowers` 正好是 `v7.0.1`，`mattpocock/skills` 在 `v1.3.2` 之后 3 个提交）。

要手工复核 Go/Java 包格式互通，给上述 Go 测试额外设置 `SKILLHUB_GITHUB_SYNC_CAPTURE_ARCHIVE` 为一个尚不存在的临时 ZIP 路径；随后在 `server/` 给 `GithubSyncArchiveValidatorInteropTest` 设置同一路径的 `SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE` 并运行 Maven 定向测试，最后删除临时 ZIP。两个变量仅供测试使用，不参与服务启动。

要继续复核实际 Go→Java HTTP 合同，将同一个 ZIP 路径传给 `GithubSyncHttpIngestionInteropTest`：在 `server/` 运行 `SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE=/path/to/captured.zip ./mvnw -q -pl skillhub-app -am '-Dtest=GithubSyncHttpIngestionInteropTest' -Dsurefire.failIfNoSpecifiedTests=false test`。此测试需要本机 Go 与 Docker；它在隔离的 PostgreSQL、Redis 容器和随机 HTTP 端口上让真正的 Go 控制面客户端完成领取、准入、本地暂存上传、重复与冲突回报、仓库完成，再验证 Java 摄入校验、后台发布、Scanner outbox、独立同步待审任务及审核前市场版本指针为空。Scanner 消费者默认调用隔离的 HTTP 测试桩取得安全结果后，超级管理员批准同步审核，才将版本发布为市场最新版本。第二次同内容完整摄入必须落为 `NO_CHANGE`，不能新增版本或审核项；TEAM 来源另验证 `NAMESPACE_ONLY` Skill 被拒后不发布，同内容重采也不重复审核。显式请求逐 Skill 重试后，即使 commit 和包内容相同，下一次完整采集也必须建立新的同步待审项，并清除重试标记。测试还让 Go 对第一完整 Java 应用进程回报包，停掉该进程后由第二进程从同一数据库与存储自动续跑，检查唯一版本和同步待审项。加上 `SKILLHUB_GITHUB_SYNC_INTEROP_STORAGE_MODE=s3` 后，同一测试会启动隔离的 MinIO，验证改写预签名 URL 的对象 key 被拒且未生成对象、原签名正常上传、首次建桶、对象摄入以及超额对象被 Java 拒绝并清理。测试结束后删除临时 ZIP。新增用例还会在 Go 持有仓库任务租约时关闭 Java，确认续租连接失败，再在原端口启动 Java，由第二个 Go 进程接管并完成已接收路径；测试提前设置租约到期时间，避免等待生产环境的完整租约周期。

如需验证实际仓库 worker 的双 Skill 逐包路径，先用上面的 `SKILLHUB_GITHUB_SYNC_CAPTURE_ARCHIVE` 从固定公开 commit 生成第一个 Skill 的参考 ZIP，再在 `server/` 运行 `SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE=/path/to/captured.zip SKILLHUB_GITHUB_SYNC_LIVE_WORKER=1 ./mvnw -q -pl skillhub-app -am '-Dtest=GithubSyncHttpIngestionInteropTest#repositoryWorkerAcquiresTwoPublicSkillsThroughJava' -Dsurefire.failIfNoSpecifiedTests=false test`。Go 使用受控 Tree 元数据绕开 GitHub REST 额度，从公开固定 commit 实际稀疏获取两个 Skill；Java 完成领取、准入、上传、校验并生成两条独立同步待审项，第一个摄入包的 SHA-256 与参考 ZIP 一致。隔离 Scanner HTTP 测试桩将两包判为安全后，测试逐条批准：首条批准时另一条仍未发布，两条批准后均为已发布版本；TEAM 的两条 Skill 对匿名访问均返回 403。额外设置 `SKILLHUB_GITHUB_SYNC_INTEROP_STORAGE_MODE=s3` 可复核隔离 MinIO 模式。此用例不验证真实 GitHub REST Tree 枚举或真实 Scanner 判定；运行后删除参考 ZIP。

同一固定 commit 的在途恢复用例可在 `server/` 运行 `SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE=/path/to/captured.zip SKILLHUB_GITHUB_SYNC_LIVE_INTERRUPT_INTEROP=1 ./mvnw -q -pl skillhub-app -am '-Dtest=GithubSyncHttpIngestionInteropTest#repositoryWorkerResumesAfterSigtermBetweenReceiptAndAcknowledgement' -Dsurefire.failIfNoSpecifiedTests=false test`；加 `SKILLHUB_GITHUB_SYNC_INTEROP_STORAGE_MODE=s3` 可复核 MinIO。Go 测试进程在首包已被 Java 持久接收、确认尚未送达时收到 SIGTERM；新进程领取到期租约并只采集剩余包。测试检查首包摄入 ID、路径结果和准入次数不变，及中断后工作区为空。Tree 元数据受控，此用例仍不能代替完整服务容器在小临时卷中的停启演练。

实际服务镜像的在途续跑可在仓库根目录先执行 `docker build -t skillhub-github-sync:interop github-sync-go`，再在 `server/` 执行 `SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE=/path/to/captured.zip SKILLHUB_GITHUB_SYNC_INTEROP_IMAGE=skillhub-github-sync:interop ./mvnw -q -pl skillhub-app -am '-Dtest=GithubSyncHttpIngestionInteropTest#serviceContainerResumesPublicRepositoryOnSmallTmpfs' -Dsurefire.failIfNoSpecifiedTests=false test`；额外设置 `SKILLHUB_GITHUB_SYNC_INTEROP_STORAGE_MODE=s3` 可验证隔离 MinIO。参考 ZIP 依上文单包采集命令生成，测试从 `SKILLHUB_GITHUB_SYNC_LIVE_TOKEN` 或本机 `gh auth token` 取得公开读取凭证；容器需要可用的 GitHub 出站代理。此显式公网用例在 96 MiB tmpfs 中首包持久接收后停止容器，由新容器按租约续跑真实 Tree 的 43 个合规包，并检查首包不重复摄入、容器进程表无僵尸。完成后删除参考 ZIP。

要把同一双 Skill 用例改为真实 GitHub REST Tree 元数据，在上述 Maven 命令额外设置 `SKILLHUB_GITHUB_SYNC_LIVE_WORKER_REAL_TREE=1`。测试进程从 `SKILLHUB_GITHUB_SYNC_LIVE_TOKEN` 或本机 `gh auth token` 读取凭证，仅用于公开 API 的 GET 请求；测试代码不写入文件或日志，也不影响正式服务的 Java 凭证池。Go 核对真实 repository ID、固定 commit 和完整递归 Tree 响应后，仅选择上述两个 Skill 的元数据，以控制此次联调的包数。可以先在 `github-sync-go/` 用 `SKILLHUB_GITHUB_SYNC_LIVE_TREE=1 go test ./internal/repository -run '^TestLiveEnumerateFixedPublicGitHubTree$' -count=1 -v` 验证完整 Tree 枚举及所有候选 Skill 的元数据预检；预检采用当前 `application.yml` 的 100 文件、单文件 10 MiB、包总量 100 MiB 限制，不下载其余包。真实 Tree 双包联调已在本地存储与 MinIO 模式通过，但不能据此认定全部 44 个 Skill、其他仓库或高并发容量已验收。

如需在同一固定公开仓库中顺序获取所有通过预检的包，可在 `github-sync-go/` 运行 `SKILLHUB_GITHUB_SYNC_LIVE_ALL_PACKAGES=1 go test ./internal/repository -run '^TestLiveAcquireAllFixedPublicSkillsSequentially$' -count=1 -v -timeout=16m`。此显式公网用例从真实 Tree 生成包计划，在单包 64 MiB 工作区预算和 16 MiB 保留空间下逐包执行 Git 获取，核对 ZIP 条目、摘要，并在下一包开始前确认工作区为空。当前固定 commit 的 43 个可采包全部通过，另 1 个因 312 个文件超过 100 文件政策而被拒。用例不经过 Java 摄入或 Scanner；完成时的目录占用不等于 Git 执行期间的严格瞬时峰值。

如需检查整仓的 Go→Java 接收与后台处理，可先按上文生成单包参考 ZIP，再在 `server/` 运行 `SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE=/path/to/creator.zip SKILLHUB_GITHUB_SYNC_LIVE_WORKER_REAL_TREE=1 SKILLHUB_GITHUB_SYNC_LIVE_ALL_PACKAGES_INTEROP=1 ./mvnw -q -pl skillhub-app -am '-Dtest=GithubSyncHttpIngestionInteropTest#repositoryWorkerReportsEveryEligiblePublicSkillThroughJava' -Dsurefire.failIfNoSpecifiedTests=false test`；加 `SKILLHUB_GITHUB_SYNC_INTEROP_STORAGE_MODE=s3` 验证隔离 MinIO。此显式用例把包上限恢复为当前 100 文件、100 MiB 政策，约需数分钟，验证 43 包接收后有 42 个同步待审项、1 个同 namespace slug 冲突，另一个超文件数目录不下载；待审前无市场最新版本。用例不自动批准待审项，不代表跨仓库并发或长期容量验收。

需要验证真实 Scanner 时，先从当前 `scanner/Dockerfile` 构建并启动独立 Scanner，确认其 `/health` 可访问；然后为上述定向测试额外设置 `SKILLHUB_GITHUB_SYNC_INTEROP_SCANNER_URL=http://127.0.0.1:<scanner-port>`。单包测试可用 `GithubSyncHttpIngestionInteropTest#goArchiveCanTravelThroughHttpReceiptAndPersistedIngestion`，双包 worker 测试可用 `GithubSyncHttpIngestionInteropTest#repositoryWorkerAcquiresTwoPublicSkillsThroughJava`。该模式使用正式 Compose 相同的 `upload` 扫描方式；不设置此变量时仍使用 HTTP 测试桩。真实 Scanner 的结论取决于测试包内容和当前规则，不应固定假设所有包都安全。

内部接口字段、错误码和恢复语义见 [合同说明](../docs/api/github-sync-internal.md) 与 [OpenAPI](../docs/api/github-sync-internal.openapi.yaml)。

上述互操作测试还从同一公开采集包生成四个受控内容 revision，用不同固定 commit 模拟上游连续更新。它验证旧审核被新目标替代、旧目标不能批准、已发布版本在新审核期间保持可用，以及较旧包迟到时不能覆盖较新的来源绑定。这是协议和发布事务测试，不代表已对四个真实 GitHub commit 做公网采集。
同一测试继续以另一个固定 commit 回报完全相同的包，验证 `NO_CHANGE`；再只调整包内 `LICENSE.txt`，验证内容指纹和同步待审目标发生变化。这两步也使用受控 revision，不代表对应 commit 曾在 GitHub 公网真实存在。
摄入后还会启动独立 Java JVM 领取校验租约并退出，再由测试中的 Java 应用实例在租约过期后完成校验；发布阶段重复相同的进程丢失与恢复检查。这验证持久租约和后台续跑，不替代完整应用容器的停止、重新启动及部署配置验收。
