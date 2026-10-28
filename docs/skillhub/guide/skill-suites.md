# 管理技能套装

Skill Suite（技能套装）把一组已发布 Skill 的精确版本组织成一个有独立名称和版本的集合，并指定一个 Entry Skill（入口技能）。安装后，每个成员仍是独立的技能目录。

## Skill、Suite 和 Bundle 的区别

| 类型 | 用途 | 使用方式 |
| --- | --- | --- |
| Skill | 一个包含 `SKILL.md` 的技能包 | `skillhub install` 安装，交给 Agent 使用 |
| Skill Suite | 固定版本的技能集合，包含一个入口技能 | `skillhub suite install` 安装全部成员 |
| Suite Bundle | 作者批量上传成员和套装定义的 ZIP 或文件夹 | 在网页中导入、预览、确认，再完成发布审核 |

Bundle 是[创作与导入格式](./suite-bundle)，不是安装后需要执行的程序。套装也不是工作流引擎：是否串联成员、如何分派任务，由入口及成员的 `SKILL.md` 决定。普通技能可以独立安装，不必安装它关联的所有套装。

![套装安装与入口技能使用流程](/diagrams/skill-suite-usage-sketch.png)

安装交付的是独立成员文件；在 Agent 中提出任务后才开始使用。

## 找到并安装套装

在技能详情中查看关联套装，进入套装详情，确认入口、成员版本和可安装状态。卡片上的关联标识只表示收录关系。普通 `skillhub search` 搜索 Skill；安装同名 Suite 必须显式使用 `suite` 子命令。

以下 `@team/example-suite` 是占位示例，请替换为实际坐标；需要访问受限内容时先登录对应 Registry。

```bash
skillhub login
# 在目标项目根目录安装给 Codex
skillhub suite install @team/example-suite --scope project --agent codex
# 或安装给 Claude Code
skillhub suite install @team/example-suite --scope project --agent claude-code
# 指定套装版本；用户级安装供该用户的项目使用
skillhub suite install @team/example-suite --version 1.0.0 --scope user --agent codex
```

`--registry <url>` 可选择私有部署。服务端必须声明 `skill-suite-v1` 能力；CLI 出现 unknown command 时先检查 `skillhub version` 和 `skillhub help suite`，不要把单个 Skill 安装命令当成替代。

项目级 Codex 安装结果如下；Claude Code 使用 `.claude/skills`，用户级则在主目录下的对应位置：

```text
.codex/skills/
├── entry-skill/SKILL.md
├── member-a/SKILL.md
└── member-b/SKILL.md
```

目录名是各成员的 slug，不会生成一个合并后的同名套装技能。安装会下载并校验全部成员；成员不可访问或不可安装时，不能用最新版本悄悄替换固定版本。

## 安装后开始使用

1. 在对应 Agent 中打开安装目标项目，按该 Agent 的机制刷新技能或开启新会话。
2. 检查安装输出中的路径，确认入口目录有 `SKILL.md`，并确认 Agent 能发现它。
3. 阅读入口说明，补齐其要求的工具、环境变量或外部服务配置。安装技能文件不等于安装这些运行依赖。
4. 明确请求使用入口，例如：“请使用入口技能 `<入口名称>` 完成下面的任务，并按照它的说明使用其他成员：……”

套装坐标用于安装管理；调用时使用实际入口技能的名称，也可直接使用单个成员。Agent 的触发语法和加载时机由对应产品决定，SkillHub 不提供通用的 `suite run` 命令。

## 检查、升级与卸载

```bash
skillhub suite check @team/example-suite
skillhub suite upgrade @team/example-suite --check
skillhub suite upgrade @team/example-suite
skillhub suite remove @team/example-suite
```

`check` 显示本地成员状态和远端版本信息；`upgrade --check` 只预览变更。成员单独发布新版本不会改变已安装套装，升级遵循新 SuiteVersion 的固定成员清单。套装管理的目标应使用套装升级流程。

`--scope`、`--agent`、`--dir` 和 `--version` 仅用于 `suite install`；后续操作使用本地安装记录。卸载保留直接安装、被其他套装共享或本地修改过的成员。不要为消除提示直接删除 inventory。

## 创建套装

已有成员均已发布时，可通过网页套装编辑器选择精确成员版本和入口。需要同时上传多个本地成员时使用 [Suite Bundle](./suite-bundle)。PUBLIC 和 NAMESPACE_ONLY 套装需要套装级审核；PRIVATE 可直接发布。成员扫描和审核仍按各自 Skill 的流程执行。

每个版本最多 100 个不同成员，必须从成员中指定一个入口；不支持嵌套套装、版本范围或外部 Registry 成员。CLI 当前提供安装管理，不提供 `suite publish` 或直接读取创作定义文件的命令。

## 读懂本地检查结果

`skillhub suite check` 同时读取本地记录和远端套装状态，不是完全离线检查。成员状态 `ok` 表示目录内容与固定 fingerprint 一致，`missing` 表示目录缺失，`modified` 表示内容已变更。只有远端最新版与已安装版本一致、已安装版本仍可安装、所有成员均为 `ok` 时，整体才显示 `current`。

`--json` 中 `ok: false` 也可能表示检测到变化，不一定是命令抛出错误。脚本应检查 JSON 中的成员及远端状态，不能只看进程退出码。

## 共享成员与升级决策

两个套装可以复用同来源、同内容的成员目录。某个目标还被另一个套装拥有时，不允许通过升级当前套装原地替换它的版本；`--force` 不是绕过共享来源保护的选项。先检查两个套装的成员计划，选择兼容版本或分离安装目标。

安装与升级先下载校验全部成员，再提交目录和 inventory；提交中途失败会尝试恢复本次替换的目录。失败后保留错误信息，重新 `check` 核对，不要手改安装记录伪造成功。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `unknown suite action` | 只支持 install、check、upgrade、remove；没有 run/publish 子命令 |
| 服务端不支持套装能力 | 核对 registry 与服务端版本；普通 install 不会自动安装套装 |
| `modified` | 先备份或移走需要的修改，再决定是否接受安装/升级覆盖 |
| 成员下架或权限收窄 | 套装快照仍保留，但安装计划会阻止安装；联系维护者发布可安装的新版本 |
| 卸载显示 `Preserved` | 检查 shared、modified、missing 原因；保留成员不等于卸载失败 |
| 指定 agent 检查/卸载被拒绝 | 这些选项仅属于 install；后续操作按本地套装记录执行 |
