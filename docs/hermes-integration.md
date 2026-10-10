# Hermes Agent 集成指南

本文档说明如何把 SkillHub 中的技能安装到 [NousResearch Hermes Agent](https://github.com/NousResearch/hermes-agent)，并在 Hermes 中发现、加载、更新和移除这些技能。

本文中的 “Hermes” 特指 `NousResearch/hermes-agent`，不适用于其他同名项目。

## 已验证范围

| 组件 | 已验证版本 | 说明 |
|------|------------|------|
| SkillHub Server | `v0.2.13` | 公开或自托管 registry |
| SkillHub CLI | `0.1.8` | npm 包 `@astron-team/skillhub` |
| Hermes Agent | `0.18.2` | 上游 tag [`v2026.7.7.2`](https://github.com/NousResearch/hermes-agent/tree/v2026.7.7.2) |

验证日期：2026-07-17。

Hermes 0.18.2 使用兼容 [Agent Skills](https://agentskills.io/) 的 `SKILL.md` 格式，并递归扫描 `$HERMES_HOME/skills/`。SkillHub CLI 可以通过 `--dir` 把完整技能包解压到指定目录。因此，当前兼容链路不需要格式转换、Hermes 专用 CLI profile 或服务端适配：

```text
SkillHub registry
  -> skillhub install --dir <Hermes 技能目录>
  -> <Hermes 技能目录>/<skill-slug>/SKILL.md
  -> Hermes 发现并按需加载
```

> Hermes 0.18.2 没有原生 SkillHub registry source。本指南使用 SkillHub CLI 负责搜索、下载和本地安装，Hermes 负责发现和执行技能。

## 前置条件

1. 已安装并初始化 Hermes Agent。
2. 已安装 SkillHub CLI：

```bash
npm install -g @astron-team/skillhub

skillhub version
hermes version
```

3. 技能包根目录包含有效的 `SKILL.md`，其中至少有 `name` 和 `description` frontmatter。

以下示例使用 Bash/zsh。Windows 用户可使用同一目录结构，将默认 Hermes 主目录替换为 `$HOME\.hermes`，并按 PowerShell 语法设置变量。

## 快速开始

### 1. 配置 SkillHub registry

设置公开或自托管 SkillHub 地址：

```bash
export SKILLHUB_REGISTRY=https://skillhub.your-company.com
```

公开且允许匿名下载的技能可以跳过登录。访问团队命名空间、受限技能或私有部署时，先保存 API Token：

```bash
skillhub login \
  --registry "$SKILLHUB_REGISTRY" \
  --token YOUR_API_TOKEN

skillhub whoami --registry "$SKILLHUB_REGISTRY"
```

请使用占位 Token 演示，不要把真实 Token 写入 `SKILL.md`、脚本或版本库。

### 2. 搜索技能

```bash
skillhub search "pdf" --registry "$SKILLHUB_REGISTRY"
```

记录结果中的 namespace、slug 和所需版本。下面以 `my-team/my-skill` 为例：

```bash
export SKILLHUB_NAMESPACE=my-team
export SKILLHUB_SKILL=my-skill
```

### 3. 安装到 Hermes 主技能目录

设置当前 Hermes profile 的主目录。默认 profile 通常是 `~/.hermes`；如果使用自定义 `HERMES_HOME` 或命名 profile，请指向实际 profile 目录：

```bash
export HERMES_HOME="${HERMES_HOME:-$HOME/.hermes}"
export HERMES_SKILLHUB_DIR="$HERMES_HOME/skills/skillhub/$SKILLHUB_NAMESPACE"
```

安装技能：

```bash
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY"
```

SkillHub CLI 会保留技能包中的 `SKILL.md`、`references/`、`scripts/`、`templates/`、`assets/` 等文件，并额外写入 `.skillhub/metadata.json` 记录安装来源。目录结构类似：

```text
$HERMES_HOME/skills/
└── skillhub/
    └── my-team/
        └── my-skill/
            ├── SKILL.md
            ├── references/          # 可选
            ├── scripts/             # 可选
            └── .skillhub/
                └── metadata.json
```

按 namespace 分目录可以减少不同命名空间中同 slug 技能的文件路径冲突。Hermes 会递归扫描这些层级。

### 4. 在 Hermes 中验证和加载

先确认 Hermes 发现了技能：

```bash
hermes skills list --source local --enabled-only
```

然后启动 Hermes，在会话中使用由技能 `name` 规范化得到的斜杠命令：

```bash
hermes
```

```text
/my-skill
```

也可以在自然语言请求中明确要求 Hermes 使用该技能。Hermes 列表显示 `SKILL.md` frontmatter 中的原始 `name`，斜杠命令会把它转为小写、把空格和下划线替换为连字符、移除其他非 `a-z0-9-` 字符，并合并重复连字符。例如 `PDF_Tools` 对应 `/pdf-tools`。该命令不一定与 SkillHub slug 相同。

已经运行的会话未立即显示新技能时，执行 `/reload-skills` 或重新启动会话。

## 更新技能

SkillHub CLI 0.1.8 使用同一安装命令加 `--force` 覆盖本地技能。省略 `--version` 会解析最新已发布版本；也可以显式固定版本：

```bash
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY" \
  --force

# 固定版本示例
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --version 1.2.0 \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY" \
  --force
```

覆盖前请先审查新版本，因为 `--force` 会替换现有技能目录。更新后重新运行：

```bash
skillhub list \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY"

hermes skills list --source local --enabled-only
```

> `skillhub update` 更新的是 SkillHub CLI 自身，不会更新已安装技能。已安装技能使用 `skillhub install ... --force` 刷新。

## 移除技能

先列出同一 registry 中的全部安装，确认没有其他需要保留的同 slug 技能：

```bash
skillhub list \
  --registry "$SKILLHUB_REGISTRY"
```

再移除本地安装：

```bash
skillhub remove "$SKILLHUB_SKILL" \
  --registry "$SKILLHUB_REGISTRY"
```

SkillHub CLI 会同时删除技能目录和本地 inventory 记录。当前版本的本地 `remove` 仅按 registry 和 slug 匹配，不按 namespace 或目录过滤；同一 registry 下所有 namespace、所有安装目录中的相同 slug 都会被移除。如果未过滤的 `skillhub list` 中存在需要保留的匹配项，请不要执行该命令；按 namespace 或目录精确移除需要后续 CLI 能力支持。

移除后，使用 `/reload-skills`、重启 Hermes 会话，或运行以下命令确认技能已消失：

```bash
hermes skills list --source local --enabled-only
```

## 可选：使用共享的 external skill 目录

如果多个 Agent 共用 `~/.agents/skills`，可以把 SkillHub 技能安装到共享目录，而不是 Hermes 主目录：

```bash
export SHARED_SKILLHUB_DIR="$HOME/.agents/skills/skillhub/$SKILLHUB_NAMESPACE"

skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$SHARED_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY"
```

然后把共享根目录合并到 `$HERMES_HOME/config.yaml`，不要覆盖已有的 `skills` 配置：

```yaml
skills:
  external_dirs:
    - ~/.agents/skills
```

Hermes 会把 external skill 与本地技能一起列出和加载。不要依赖本地技能覆盖 external skill：Hermes 0.18.2 会拒绝加载本地技能目录与 `external_dirs` 之间存在歧义的 `skill_view` 匹配；请改名或移除其中一个冲突副本。

> `external_dirs` 不是只读边界。只要 Hermes 进程拥有写权限，Hermes 的技能管理工具就可能修改其中的文件。共享目录需要只读保护时，请使用文件系统权限或隔离的 Hermes profile。

## 兼容性与安全边界

- **格式兼容不等于运行时完全兼容。** Hermes 能读取 `SKILL.md` 和配套文件，但技能引用的 Agent 专用工具、MCP server、命令、环境变量或平台能力仍需逐项验证。
- **Hermes 将此路径识别为 local skill。** 通过 SkillHub CLI 复制到本地的技能不会经过 Hermes Skills Hub 的 community 安装扫描。安装前应查看 SkillHub 安全报告并审查技能内容，必要时使用 Hermes 的终端隔离能力。
- **保留多文件包。** 不要把多文件 SkillHub 技能改成 Hermes 0.18.2 的直接 URL 安装；该版本的 URL source 只保证单个 `SKILL.md`，而 SkillHub CLI 会解压完整包。
- **避免名称冲突。** 文件路径按 namespace 隔离仍不能解决斜杠命令冲突；同一 Hermes profile 内应保持规范化后的命令名唯一。例如 `PDF Tools` 和 `pdf_tools` 都会变成 `/pdf-tools`。
- **保护凭证。** Token 只用于 SkillHub registry 访问，不应写进技能包。需要运行时 secret 的技能应遵循 Hermes 的环境变量和安全设置方式。

## 常见问题

### Hermes 列表中没有新技能

依次检查：

1. 当前会话的 `HERMES_HOME` 是否与安装时一致。
2. 最终路径下是否存在 `<skill-directory>/SKILL.md`。
3. `SKILL.md` 是否包含有效的 `name` 和 `description`。
4. `platforms` 等 frontmatter 是否排除了当前操作系统。
5. 执行 `/reload-skills` 或启动新会话后是否出现。

```bash
skillhub list --dir "$HERMES_SKILLHUB_DIR" --registry "$SKILLHUB_REGISTRY"
hermes skills list --source local
```

### 安装提示 `skill already installed`

已有目录默认不会被覆盖。先审查目标版本，再增加 `--force`：

```bash
skillhub install "$SKILLHUB_SKILL" \
  --namespace "$SKILLHUB_NAMESPACE" \
  --dir "$HERMES_SKILLHUB_DIR" \
  --registry "$SKILLHUB_REGISTRY" \
  --force
```

### 提示 `registry unreachable` 或下载失败

- 核对 `SKILLHUB_REGISTRY` 是否是 SkillHub 根地址。
- 先运行同一 registry 的 `skillhub search` 判断 registry 是否可达。
- 检查代理、DNS、证书和自托管服务状态。
- 短暂网络错误可以在确认服务正常后重试；不要通过关闭 TLS 校验绕过证书问题。

### 技能已列出但执行失败

检查技能引用的工具名称、shell 命令、脚本解释器、依赖包、MCP server、环境变量和操作系统限制。此类问题属于具体技能的运行时兼容性，不代表 `SKILL.md` 发现链路失败。

### 能否直接运行 `hermes skills install` 安装 SkillHub 坐标？

Hermes 0.18.2 没有 SkillHub registry source，不能直接解析 SkillHub 的 namespace/slug。请使用本指南中的 `skillhub install --dir ...`。如果未来需要 Hermes 内原生搜索、安装、更新和安全扫描，应单独设计 Hermes source adapter，并重新定义协议和验收范围。

## 升级后的回归检查

升级 SkillHub CLI 或 Hermes 后，至少重新验证：

1. `skillhub install --dir` 仍生成 `<slug>/SKILL.md` 并保留配套文件。
2. `hermes skills list --source local --enabled-only` 能发现技能。
3. `/skill-name` 能加载 `SKILL.md` 并暴露配套文件路径；再通过 `skill_view(name, file_path)` 读取一个实际引用文件，或执行技能使用的脚本/资产验证其运行时路径。
4. `skillhub install --force` 能覆盖更新且 inventory 正常。
5. `skillhub remove` 后 Hermes 不再发现该技能。

上游参考：[Hermes Skills System（v0.18.2）](https://github.com/NousResearch/hermes-agent/blob/v2026.7.7.2/website/docs/user-guide/features/skills.md)。
