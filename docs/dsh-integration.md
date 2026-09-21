# DeepSeek Harness 集成指南

本文说明如何把 SkillHub 中的技能安装到
[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)（CLI 名为 `dsh`）。

## 已验证范围

本文依据 DeepSeek Harness 提交
[`ddefc45`](https://github.com/deepseek-ai/deepseek-harness/tree/ddefc45fbc7f8e46dd73185e68295696d1297887/packages/skill/skill-filesystem)
中的 `@deepseek-ai/dsh-skill-filesystem` 行为编写，验证日期为 2026-09-20。
该版本仍处于 `0.1.x` developer preview；升级 dsh 后请重新核对技能根目录约定。

## 安装到 dsh 原生目录

dsh 的原生项目级和用户级技能根分别是 `<仓库>/.dsh/skills` 与
`$DSH_HOME/skills`（默认 `~/.dsh/skills`）。使用默认目录时：

```bash
# 用户级：安装到 ~/.dsh/skills/<skill-slug>/
skillhub install my-skill --agent dsh --scope user

# 项目级：从仓库根目录执行，安装到 .dsh/skills/<skill-slug>/
cd "$(git rev-parse --show-toplevel)"
skillhub install my-skill --agent dsh --scope project
```

省略 `--scope` 时，显式的 `--agent dsh` 默认选择用户级目录。安装后可用
SkillHub CLI 核对记录：

```bash
skillhub list --agent dsh
```

dsh 会监听已配置的技能根；新增、重命名或删除技能后，无需重启进程即可在后续技能目录
刷新中看到变化。

## 使用共享 `.agents/skills`

dsh 也原生扫描项目级 `.agents/skills` 和用户级 `~/.agents/skills`。因此在尚未升级到
含 `dsh` profile 的 SkillHub CLI 时，或需要与其他 Agent 共享同一份技能时，可以使用：

```bash
# 用户级共享目录
skillhub install my-skill --dir "$HOME/.agents/skills"

# 项目级共享目录；仍建议从仓库根目录执行
skillhub install my-skill --dir "$(git rev-parse --show-toplevel)/.agents/skills"
```

## 自定义 DSH_HOME

SkillHub CLI 的 `dsh` profile 对应默认的 `~/.dsh/skills`。如果 dsh 使用了自定义
`DSH_HOME`，请显式指定实际目录：

```bash
skillhub install my-skill --dir "${DSH_HOME:-$HOME/.dsh}/skills"
```

## 项目根目录差异

dsh 会向上查找最近的 `.git` 祖先作为项目根目录；SkillHub CLI 的项目级 profile
则以当前工作目录为根。如果在仓库子目录执行 `--scope project --agent dsh`，SkillHub
CLI 会写入子目录下的 `.dsh/skills`，而 dsh 不会把它当作项目根。项目级安装前应先
切换到 `git rev-parse --show-toplevel` 返回的目录。

## 兼容性边界

- SkillHub 安装目录采用 `<skill-slug>/SKILL.md`，符合 dsh 对根目录一级技能包的发现规则。
- dsh 还支持根目录中的单文件 `<name>.md`；SkillHub 包仍以根级 `SKILL.md` 为规范入口。
- `SKILL.md` 至少需要合法的 kebab-case `name` 和非空 `description` frontmatter。
- 格式兼容不代表运行时能力完全相同。技能依赖的 Agent 专用工具、命令、MCP server、
  环境变量和操作系统能力仍需单独验证。
- 安装前应审查技能内容和 SkillHub 安全报告；Registry Token 不应写入技能包。

## 故障排查

如果 dsh 未发现已安装技能：

1. 运行 `skillhub list`，确认安装目录和状态。
2. 确认目录结构为 `<技能根>/<skill-slug>/SKILL.md`，没有额外嵌套层级。
3. 检查 `SKILL.md` 的 `name` 与 `description` frontmatter。
4. 项目级安装确认位于最近的 `.git` 祖先下，而不是仓库子目录。
5. 自定义 `DSH_HOME` 或 `DSH_AGENTS_HOME` 时，确认安装命令使用了对应实际路径。
