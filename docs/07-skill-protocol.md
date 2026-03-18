# skillhub 技能包协议

## 8.1 OpenSkills 互操作边界

skillhub 的目标是客户端可互操作：skillhub CLI 安装的技能可以被 Claude Code / OpenSkills 兼容客户端发现和使用，反之亦然。

### 互操作层（skillhub CLI 必须兼容）

- SKILL.md 格式（frontmatter + markdown body）
- 技能包目录结构约定（SKILL.md + references/ + scripts/ + assets/）
- 四级目录优先级：skillhub CLI 遵循 `.agent/skills` → `~/.agent/skills` → `.claude/skills` → `~/.claude/skills` 的发现顺序，与 OpenSkills/Claude 一致
- 目录名作为 lookup key：安装后的目录名等于 `skill.slug`（即 SKILL.md 的 `name` 字段），客户端通过目录名发现技能
- AGENTS.md `<skill>` 描述块格式：skillhub CLI 生成的 AGENTS.md 索引区块与 OpenSkills 格式兼容

### 服务端职责边界

- 服务端返回技能元数据（name, description, version），不返回 `location`
- `location` 是客户端本地安装路径，由 CLI 根据安装目录计算生成，写入 AGENTS.md
- 服务端不生成、不修改 AGENTS.md，这是客户端职责

### skillhub 私有扩展（不影响互操作）

- `<skills_system>` / `<available_skills>` 区块格式：skillhub CLI 可自定义，但必须保证 `<skill>` 节点格式与 OpenSkills 一致
- progressive disclosure（按需加载技能内容）：skillhub CLI 自行实现
- `.astron/metadata.json`：skillhub 私有元数据，其他客户端可忽略

## 8.2 SKILL.md 规范

服务端必须兼容的格式：

```yaml
---
name: my-skill              # 必需，kebab-case
description: When to use    # 必需，1-2 句话
---

# Markdown 正文（技能指令内容）
```

解析规则：
- `name` 和 `description` 为必需字段，缺失则校验失败
- `name` 映射为 `skill.slug`（首次发布时），后续版本不可变更
- `description` 映射为 `skill.summary`
- frontmatter 完整解析结果存入 `skill_version.parsed_metadata_json`

平台扩展字段（可选，`x-astron-` 前缀）：

```yaml
---
name: my-skill
description: When to use
x-astron-category: code-review
x-astron-runtime: claude-code        # 预留
x-astron-min-version: "1.0"          # 预留
---
```

## 8.3 技能包目录结构

```
my-skill/
├── SKILL.md              # 主入口文件（必需）
├── references/           # 参考资料（可选）
├── scripts/              # 脚本（可选）
└── assets/               # 静态资源（可选）
```

校验规则：
- 根目录必须包含 `SKILL.md`
- 文件类型白名单：`.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.js`, `.ts`, `.py`, `.sh`, `.png`, `.jpg`, `.svg`
- 单文件大小限制：1MB（可配置）
- 总包大小限制：10MB（可配置）
- 文件数量限制：100 个（可配置）

## 8.4 客户端安装目录约定

skillhub CLI 遵循以下目录优先级，与 OpenSkills/Claude 保持互操作：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | `./.agent/skills/` | 项目级，universal 模式 |
| 2 | `~/.agent/skills/` | 全局级，universal 模式 |
| 3 | `./.claude/skills/` | 项目级，Claude 默认 |
| 4 | `~/.claude/skills/` | 全局级，Claude 默认 |

安装后目录名等于 `skill.slug`（SKILL.md 的 `name` 字段），确保其他兼容客户端可通过目录名发现。

## 8.5 与 AGENTS.md 的关系

- skillhub CLI 安装技能后，通过 `sync` 命令在 AGENTS.md 中生成 `<skill>` 描述块
- `<skill>` 块包含 `name`、`description`、`location`（本地安装路径），格式与 OpenSkills 一致
- `location` 由 CLI 根据实际安装路径计算，不由服务端提供
- 服务端不直接生成或修改 AGENTS.md，这是客户端职责

## 8.6 客户端本地元数据文件（skillhub 私有实现）

以下为 skillhub CLI 的私有实现细节，不属于互操作协议的一部分。其他客户端可忽略此文件。

CLI 安装后在本地写入 `.astron/metadata.json`：

```json
{
  "source": "skillhub",
  "sourceType": "registry",
  "registryUrl": "https://skills.example.com",
  "namespace": "@ai-platform-team",
  "skillSlug": "code-review",
  "version": "1.2.0",
  "installedAt": "2026-03-11T10:00:00Z",
  "sha256": "abc123..."
}
```

## 8.7 版本解析规则

skillhub 自有 CLI 支持完整 namespace 坐标：

```
install @team/my-skill              → 最新已发布版本（实现上通常由 `latest_version_id` / published pointer 解析）
install @team/my-skill@1.2.0        → 精确版本
install @team/my-skill@latest        → 等同于不带版本号（系统保留标签，只读）
install @team/my-skill@beta          → beta 标签（自定义标签）
install my-skill                     → 等同于 @global/my-skill
```

ClawHub CLI 通过兼容层使用 canonical slug：

```
clawhub install my-skill             → @global/my-skill 的最新版本
clawhub install team-name--my-skill  → @team-name/my-skill 的最新版本
clawhub install my-skill@1.2.0       → @global/my-skill 的精确版本
```

## 8.8 坐标映射与 ClawHub CLI 兼容

skillhub 内部使用 `@{namespace_slug}/{skill_slug}` 坐标，ClawHub CLI 使用单一 slug。映射规则详见 `00-product-direction.md` 1.1 节。

安装后的本地目录名始终使用 `skill.slug`（不含 namespace 前缀），确保与 OpenSkills/Claude 兼容客户端的互操作性。

| skillhub 坐标 | ClawHub canonical slug | 本地安装目录名 |
|---|---|---|
| `@global/my-skill` | `my-skill` | `my-skill/` |
| `@team-name/my-skill` | `team-name--my-skill` | `my-skill/` |

注意：不同 namespace 下同名 skill 安装到本地时会产生目录冲突。skillhub CLI 应在安装时检测冲突并提示用户选择安装目录或使用别名。
