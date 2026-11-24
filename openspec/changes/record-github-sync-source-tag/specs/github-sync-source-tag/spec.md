## ADDED Requirements

### Requirement: 采集 SHALL 判定采集 commit 与仓库 semver tag 的关系

采集服务 SHALL 在为仓库任务上传第一个包之前，对该任务的采集 commit 判定一次仓库级 semver tag 关系，并把结果附在该任务
每个包的回报中。只有名称为顶层名称、整体符合 semver 2.0（允许前缀 `v`）的 tag 参与判定；注释 tag 以剥离后的提交为准。
只有采集 commit 本身或其祖先上的 tag 可以作为来源；其他分支、被改写历史或指向非提交对象的 tag SHALL 被排除。

#### Scenario: 采集 commit 正好是某个 tag
- **WHEN** 仓库 `obra/superpowers` 的采集 commit `2ce8aa15…` 正是 `v7.0.1` 剥离后的提交
- **THEN** 来源 tag 为 `status = EXACT`、`tag = v7.0.1`、`commitsAfterTag = 0`

#### Scenario: 采集 commit 在 tag 之后
- **WHEN** 仓库 `mattpocock/skills` 的采集 commit `4ae2317d…` 比可达的 `v1.3.2` 多 3 个提交，且没有更近的可达 tag
- **THEN** 来源 tag 为 `status = AFTER_TAG`、`tag = v1.3.2`、`commitsAfterTag = 3`，`tagCommitSha` 为 `v1.3.2` 剥离后的提交

#### Scenario: 没有合适的 tag
- **WHEN** 仓库没有 tag，或所有 tag 都不符合 semver、带路径前缀，或都不在采集 commit 的历史中
- **THEN** 来源 tag 为 `status = NO_TAG`，不带 tag 字段

#### Scenario: 其他分支上的 tag 不计入
- **WHEN** 一个 semver 更高的 tag 只在另一分支上，不是采集 commit 的祖先
- **THEN** 该 tag 被忽略，结果只取可达的 tag

#### Scenario: 多个 tag 指向同一提交或距离相同
- **WHEN** 采集 commit 同时被 `v2.0.0` 与 `v2.0.0-rc.1` 指向
- **THEN** 结果为 semver 优先级最高的 `v2.0.0`
- **AND** 距离相同的多个可达 tag 同样取 semver 优先级最高者，优先级相同时取名称字典序最小者

#### Scenario: 轻量 tag 与注释 tag
- **WHEN** 一个轻量 tag 与一个注释 tag 分别标在不同的祖先提交上
- **THEN** 两者按各自指向的提交参与同一套距离规则

### Requirement: 判定失败 SHALL 记为未知且不影响同步

来源 tag 判定 SHALL 只读、有时间与资源上限。取 tag 失败、历史为浅历史、超出提交数或计算预算、工作区预算不足、超时或处于
配额冷却时，结果 SHALL 为 `status = UNKNOWN` 并附原因码，SHALL NOT 改变包的采集结果、任务延后、凭证状态、审核项或发布。

#### Scenario: 取 tag 失败
- **WHEN** `ls-remote` 失败或超时
- **THEN** 该任务的包照常采集与回报，来源 tag 为 `UNKNOWN` 并带原因码

#### Scenario: 服务端只返回浅历史
- **WHEN** 拉取历史后仓库仍是浅克隆
- **THEN** 来源 tag 为 `UNKNOWN/SHALLOW_HISTORY`，不按不完整历史计算距离

#### Scenario: 没有包需要上传
- **WHEN** 仓库任务中所有路径都已接收、被忽略或在准入前失败
- **THEN** 采集服务不为来源 tag 发起任何额外请求

### Requirement: 来源 tag SHALL 随摄入记录固定保存

SkillHub SHALL 在接收包回报、创建摄入记录时保存来源 tag，之后 SHALL NOT 因重放回报、上游移动或删除 tag、发布或清理而改写。
不合法的上报 SHALL 存为 `UNKNOWN/INVALID_REPORT` 而不拒收包；没有上报时 SHALL 存为空，表示未记录。

#### Scenario: 上游移动 tag
- **WHEN** 已发布版本的来源 tag 为 `EXACT v7.0.1`，之后上游把 `v7.0.1` 移到另一个提交
- **THEN** 该版本仍显示 `EXACT v7.0.1` 与原 `tagCommitSha`

#### Scenario: 旧采集服务或旧快照
- **WHEN** 回报不带 `sourceTag`，或摄入记录早于本功能
- **THEN** 包照常接收；对外接口返回 `upstreamTag.status = UNRECORDED`

#### Scenario: 上报格式不合法
- **WHEN** 回报的 `sourceTag` 状态未知、tag 名不符合 semver 或提交不是 40 位十六进制
- **THEN** 包照常接收，来源 tag 存为 `UNKNOWN/INVALID_REPORT`

### Requirement: 版本详情、市场页与 CLI SHALL 展示同一份来源信息

技能详情的 `githubSource`、版本详情 API 和 CLI `resolve` SHALL 对来自 GitHub 同步的版本返回同一结构：`upstreamTag`
（状态、`scope = REPOSITORY`、tag、tag 提交、距离、未知原因）与 `declaredVersion`（`SKILL.md` 声明的版本，未声明为空）。
现有可见性规则不变；不是 GitHub 同步的版本不返回该结构。技术版本号、`syncRevision` 与内容指纹 SHALL 保持不变。

#### Scenario: 单仓库多个 Skill
- **WHEN** 同一仓库同一采集 commit 发布了两个 Skill，其中一个在 `SKILL.md` 声明 `version: 2.1.0`
- **THEN** 两个 Skill 的 `upstreamTag` 相同且 `scope = REPOSITORY`
- **AND** 声明了版本的 Skill `declaredVersion = 2.1.0`，另一个为空
- **AND** 市场页标明这是仓库 tag，并单独显示声明版本

#### Scenario: 版本详情 API
- **WHEN** 有权限的用户读取某个同步版本的版本详情
- **THEN** 响应包含该版本自己的 `githubSource`（commit、`upstreamTag`、`declaredVersion`）

#### Scenario: CLI 检查更新
- **WHEN** 用户运行 `skillhub outdated`，远端最新版本是同步版本且来源 tag 为 `AFTER_TAG v1.3.2 +3`
- **THEN** 人类输出该行附带 `upstream v1.3.2+3`，`--json` 输出附带 `remoteSource`
- **AND** 远端版本没有来源信息时，输出与此前一致

#### Scenario: 非同步版本
- **WHEN** 读取人工上传技能的版本详情或 CLI resolve
- **THEN** 不返回 `githubSource`
