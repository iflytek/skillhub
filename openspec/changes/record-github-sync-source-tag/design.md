## Context

GitHub 同步的现状（main `ad9d70a5`）：

- Go 采集服务按仓库任务固定一个 commit（`FixedCommitSHA` 或默认分支 HEAD），逐包用
  `git fetch --no-tags --filter=blob:none --depth=1 <commit>` 取文件，打包上传后调用 `packages/report` 回报。
  这条路径拿不到 tag，也没有历史。
- Java 在 `acceptReport` 时创建 `github_sync_package_ingestion` 记录；校验、发布后把版本挂到 `published_version_id`。
- `SkillPublishService` 把同步版本号写成 `0.0.0-g<指纹40位>-<摄入ID前16位>`，并把 `SKILL.md` frontmatter 的
  `version` 记为 parsed metadata 的 `upstreamVersion`（只是备注）。
- 技能详情的 `githubSource`（`GithubSkillSourceQueryRepository.findPublishedVersion`）展示仓库、路径、commit、
  `syncRevision` 与许可证；版本详情 API 与 CLI `resolve` 没有来源信息。

### Ubiquitous language

| Term | Definition |
| --- | --- |
| 采集 commit | 本次摄入使用的固定 commit，即 `github_sync_package_ingestion.commit_sha`。 |
| 仓库 tag | 上游仓库 `refs/tags/` 下、名称本身符合 semver 的顶层 tag；作用于整个仓库，不属于某个 Skill。 |
| 可达 | tag 指向的提交是采集 commit 本身或其祖先。 |
| 距离 | 采集 commit 可达、而 tag 提交不可达的提交数，与 `git describe` 的计数相同。 |
| 来源 tag 快照 | 采集时得出的判定结果，随摄入记录保存，之后不改写。 |
| 声明版本 | `SKILL.md` frontmatter 里的 `version`，即现有的 `upstreamVersion`，按 Skill 单独存在。 |

## Goals / Non-Goals

**Goals:**

- 在采集时得到并固定“采集 commit 与仓库 semver tag 的关系”，覆盖正好对应、之后 N 个提交、无 tag、未知四种结果。
- 判定只读、有界，失败只影响这份来源信息，不影响同步、校验、审核或发布。
- 版本详情 API、技能详情（市场页）与 CLI 返回同一份信息；旧快照有明确的兼容表示。

**Non-Goals:**

- 不改变技术版本号、内容指纹、`syncRevision`、任务调度、审核门禁或发布规则。
- 不做“只跟 release”，不按 tag 筛选或延后同步审核项。
- 不读取 GitHub Releases、`plugin.json` 或其他版本来源；不为单个 Skill 推导版本。
- 不回填已有摄入的来源 tag（回填需要按历史 commit 重新联网判定，且结果反映的是现在的 tag 而不是采集时的）。

## Decisions

### D1：判定放在 Go 采集服务，每个仓库任务只算一次

候选方案：

| 方案 | 请求与资源 | 问题 |
| --- | --- | --- |
| A. GitHub REST：`/tags` 分页 + `/compare/<tag>...<commit>` | 每个 tag 一次 compare，tag 多时请求数线性增长 | 消耗 REST 额度，与发现、树枚举争用；分页上限也要另定 |
| B. Java 侧查询 | Java 目前不直接访问 GitHub 仓库内容 | 打破“Go 访问 GitHub、Java 不出网”的既有分工 |
| **C. Go 用 Git 协议** | `ls-remote --tags` 1 次 + `fetch --filter=tree:0` 1 次 | 需要拉提交对象（无树、无文件）；用现有工作区字节预算兜底 |

选 C。Git 协议请求不计入 REST 额度；提交对象很小：实测 `obra/superpowers` 全历史 686 个提交、工作区 560 KB、约 1.5 秒；
`mattpocock/skills` 539 个提交。一次仓库任务内所有包共用同一个采集 commit，结果只算一次并复用到该任务的每个包回报。
计算时机是该任务第一个需要上传的包取得准入之后、打包之前；没有包需要上传（全部已接收、忽略或失败）时不发起任何额外请求。
任务恢复后重新计算一次；同一采集 commit 的多次计算之间若上游移动了 tag，各摄入保存的是各自采集时的结果。

### D2：判定规则

1. **取 tag**：`git ls-remote --tags <公开克隆地址>`。注释 tag 以剥离后的 `^{}` 提交为准；轻量 tag 直接取其对象。
   名称必须是顶层名称（不含 `/`）且整体匹配 semver 2.0（允许前缀 `v`、预发布和构建元数据）。
   `skill-a/v1.0.0` 这类带路径前缀的 tag 通常是 monorepo 里某个包的版本，不是仓库级 tag，不参与判定。
2. **取历史**：在独立临时目录 `git fetch --no-tags --filter=tree:0 origin <采集 commit>`，不加 `--depth`，只拉提交对象。
   结束后存在 `.git/shallow`（服务端只给了部分历史）时判为未知 `SHALLOW_HISTORY`，不按不完整的历史计算距离。
3. **可达性**：`git rev-list --parents <采集 commit>` 得到祖先图。tag 提交不在祖先图中（其他分支、已被改写的历史、指向树或
   blob 的 tag）一律排除。
4. **正好对应**：存在指向采集 commit 本身的 tag → `EXACT`，距离 0。多个时取 semver 优先级最高者；优先级相同
   （如 `v1.0.0` 与 `1.0.0`、仅构建元数据不同）取名称字典序最小者。
5. **之后 N 个提交**：否则在可达 tag 中取距离最小者 → `AFTER_TAG`；距离相同取 semver 优先级最高者，再取名称字典序最小者。
   距离 = 祖先图节点数 − 该 tag 提交的祖先数（在同一祖先图上遍历，不再联网）。
6. **无 tag**：没有任何符合条件且可达的 tag → `NO_TAG`。仓库根本没有 tag 也属于此类。
7. **未知**：任何一步失败或超出预算 → `UNKNOWN` 并附原因码，不猜测结果。

预算（常量，按已有 `AcquisitionLimits` 的工作区与可用空间检查执行）：整体 60 秒；`ls-remote` 输出上限 4 MiB；
祖先图上限 200,000 个提交；距离计算累计遍历上限 5,000,000 个节点。原因码：`TAG_LIST_FAILED`、`TOO_MANY_TAGS`、
`HISTORY_FETCH_FAILED`、`SHALLOW_HISTORY`、`HISTORY_TOO_LARGE`、`DISTANCE_BUDGET_EXCEEDED`、`WORKSPACE_BUDGET`、
`TIMEOUT`、`QUOTA_COOLDOWN`、`INVALID_REPORT`（Java 收到无法识别的上报时使用）。

### D3：失败隔离

来源 tag 判定的任何错误只体现为 `UNKNOWN`，Go 不因此改变包的 outcome、不延后任务、不上报凭证问题。
官方深度探索任务在 tag 判定前做只读的冷却检查：进程内冷却或 Java 下发的部署级 `QUOTA_COOLDOWN` 生效时直接记
`QUOTA_COOLDOWN`，不发请求；这项检查不会停下包采集的守门状态，包仍按原有规则决定。tag 判定的失败也不调用后置的配额观测，
避免一次 tag 失败触发整个进程的冷却。父进程取消（停机、租约丢失）仍按现有逻辑中止任务。

### D4：保存与不可变

Flyway `V127` 给 `github_sync_package_ingestion` 增加可空列 `source_tag_json JSONB`（约束为 JSON 对象或空）。
`acceptReport` 插入摄入记录时一并写入；同一回报的幂等重放与已存在的摄入记录都不更新该列。之后的发布、复用版本、
清理任务不读写它。上游后来移动或删除 tag 不会改写已有快照。

存储形状（内部格式，`schemaVersion` 便于以后扩展）：

```json
{"schemaVersion":1,"status":"AFTER_TAG","tag":"v1.3.2",
 "tagCommitSha":"d1478fcb…","commitsAfterTag":3}
```

### D5：接口与兼容

- 内部 `packages/report` 请求新增可选字段 `sourceTag`（形状同上，无 `schemaVersion`）。Java 校验：状态枚举；
  `EXACT`/`AFTER_TAG` 必须带合法 semver tag 名（≤128 字符）、40 位小写十六进制提交、距离（`EXACT` 为 0，`AFTER_TAG` ≥ 1）；
  `NO_TAG` 不带 tag 字段；`UNKNOWN` 的原因码为 ≤48 位大写字母与下划线。不合法时存 `UNKNOWN/INVALID_REPORT`，**不拒收包**：
请求体按未类型化的 JSON 接收该字段，类型不符、数字溢出、数组等无法绑定的形状也走这条路，不会让整个回报变成 400。
  缺省时存空，表示未记录。
- 新 Go + 旧 Java：Spring Boot 默认的 Jackson 配置忽略未知字段，旧 Java 照常接收包，只是不保存来源 tag。
  旧 Go + 新 Java：请求没有 `sourceTag`，记录为未记录。两种组合都不影响同步。
- 公开接口：`GithubSkillSourceResponse` 增加 `upstreamTag` 与 `declaredVersion`：

  ```json
  "upstreamTag": {"status":"AFTER_TAG","scope":"REPOSITORY","tag":"v1.3.2",
                  "tagCommitSha":"d1478fcb…","commitsAfterTag":3,"reason":null},
  "declaredVersion": null
  ```

  `status` 取 `EXACT`、`AFTER_TAG`、`NO_TAG`、`UNKNOWN`、`UNRECORDED`（列为空的旧快照）。`scope` 固定为 `REPOSITORY`，
  提醒消费方这是仓库 tag，单仓多 Skill 时每个 Skill 显示同一个仓库 tag。`declaredVersion` 取自该版本 parsed metadata 的
  `upstreamVersion`，未声明为 `null`。
- `SkillVersionDetailResponse` 与 `CliResolveResponse` 增加可选 `githubSource`（同一 DTO），只对来自 GitHub 同步的版本返回；
  调用前已由现有服务完成可见性授权，不新增查询入口，也不暴露待审摄入或运行状态。
- 技术版本号、`syncRevision`、指纹、`resolve` 的版本选择都不变；CLI 旧版本忽略新字段。

### D6：展示

- Web 技能详情的“GitHub 同步来源”卡片新增两行：上游版本（“正好对应 v7.0.1”“v1.3.2 之后 3 个提交 · 4ae2317”
  “未找到仓库 tag”“未知”“未记录”），标注“仓库 tag”；`SKILL.md` 声明版本（未声明显示“未声明”）。四种语言文案。
- CLI `outdated` 的人类输出在每行后追加上游关系（`upstream v1.3.2+3`），JSON 输出增加 `remoteSource`；
  `install` 成功行追加同样的上游关系。没有来源信息时输出与现在一致。

## Risks / Trade-offs

- **超大仓库**：提交对象过多会触发工作区预算或 20 万提交上限，结果为未知；不影响包采集。
- **额外的公网请求**：每个有包需要上传的仓库任务多 2 次 Git 请求；不消耗 REST 额度。官方探索的冷却规则照常适用。
- **tag 选择的主观性**：多个可达 tag 时按“距离最近、semver 最高、名称最小”确定，与 `git describe` 的“最近”一致，
  但不考虑 tag 创建时间；规则写进 spec，消费方可以只看 `tag` 与 `commitsAfterTag`。
- **回滚**：回退镜像后新列保留但不读；再次升级后已有数据继续可用。不需要数据库回退。

## Migration Plan

1. 部署包含 `V127` 的 Java；新列为空，所有现有版本显示“未记录”。
2. 部署新 Go 采集服务；之后新摄入的包带来源 tag。顺序反过来也可以（见 D5）。
3. 已发布版本只有在上游内容变化、产生新摄入后才会有来源 tag；不做回填。
