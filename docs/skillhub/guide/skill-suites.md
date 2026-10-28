# 技能套装：安装与使用

Skill Suite（技能套装）把一组已发布 Skill 的精确版本组织成一个有独立名称和版本的集合，并指定一个 Entry Skill（入口技能）。安装后，每个成员仍是独立的技能目录。

## Skill、Suite 和 Bundle 的区别

| 类型 | 用途 | 使用方式 |
| --- | --- | --- |
| Skill | 一个包含 `SKILL.md` 的技能包 | `skillhub install` 安装，交给 Agent 使用 |
| Skill Suite | 固定版本的技能集合，包含一个入口技能 | `skillhub suite install` 安装全部成员 |
| Suite Bundle | 作者批量上传成员和套装定义的 ZIP 或文件夹 | 在网页中导入、预览、确认，再完成发布审核 |

Bundle 是[创作与导入格式](./suite-bundle)，不是安装后需要执行的程序。套装也不是工作流引擎：是否串联成员、如何分派任务，由入口及成员的 `SKILL.md` 决定。普通技能可以独立安装，不必安装它关联的所有套装。

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
