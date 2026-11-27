## Why

Discussion #1051：GitHub 同步发布的版本号是 `0.0.0-g<内容指纹>-<摄入 ID>`，市场另有「第 N 次修订」（`syncRevision`）。
两者都对不上上游仓库自己的发布点。拿到一个已安装版本后，很难看出它对应上游哪个 release、是否值得升级。
例如 `obra/superpowers` 采到的 `2ce8aa15` 正好是 `v7.0.1`，`mattpocock/skills` 采到的 `4ae2317d` 在 `v1.3.2` 之后 3 个提交；
两个仓库的 `SKILL.md` 都没有声明 `version`。

讨论中确定的范围（FenjuFu 评论）：只做只读来源信息，采集时固定保存；保留现有技术版本号、内容指纹、`syncRevision`、
发布与审核规则；“只跟 release”另行讨论。

## What Changes

- 采集服务在固定 commit 之后，为每个仓库任务计算一次**仓库级 semver tag 关系**：正好对应某个 tag、在某个 tag 之后
  N 个提交、找不到合适 tag，或判定失败（未知）。tag 必须在该 commit 的可达历史内，无关分支上的 tag 不计入。
- 判定结果随逐包回报（`packages/report`）一起提交，SkillHub 写入该摄入记录，之后不再改写；上游移动或删除 tag 不影响已有快照。
- 判定失败、超出预算或遇到浅历史只记为“未知”并附原因，**不改变**包采集、校验、审核或发布结果。
- 版本详情 API、技能详情的 GitHub 来源（市场页）和 CLI `resolve` 返回同一份来源信息；`SKILL.md` 自己声明的版本另列为
  `declaredVersion`。CLI `outdated`／`install` 的人类输出附带上游 tag 关系。
- 旧快照（本变更前的摄入、或尚未升级的采集服务）没有记录，显示为“未记录”，API 返回 `status = UNRECORDED`。

## Capabilities

### New Capabilities

- `github-sync-source-tag`：同步版本的上游 tag 来源信息的判定规则、保存、兼容与展示。

### Modified Capabilities

无已归档 capability。`add-github-skill-sync` 中“市场 SHALL 展示可公开的 GitHub 来源”的展示内容在本变更中增加 tag 关系，
可见性规则不变。

## Impact

- **Go 采集服务**：新增有界的 tag 判定（`git ls-remote --tags` + 只拉提交对象的 `fetch --filter=tree:0`），复用现有工作区
  字节预算与取消机制；回报请求新增可选字段 `sourceTag`。
- **后端与数据**：Flyway `V127` 给 `github_sync_package_ingestion` 增加可空列 `source_tag_json`；内部回报接口接受可选字段；
  `GithubSkillSourceResponse` 增加 `upstreamTag`、`declaredVersion`；`SkillVersionDetailResponse`、`CliResolveResponse`
  增加可选 `githubSource`。
- **Web / CLI**：GitHub 来源卡片新增上游 tag 与声明版本两行（四种语言）；CLI 输出附带上游 tag；生成类型同步。
- **兼容与回滚**：全部为新增可选字段，旧 Go 服务与新 Java、新 Go 与旧 Java 均可互通（旧 Java 忽略未知字段，见 design D5）。
  回退应用镜像不需要回退数据库；新列为空即等同旧行为。
