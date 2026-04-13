# @motovis/skillhub CLI 命令参考

> **版本**: v1.0.0  
> **更新**: 2026-04-08

---

## 命令列表

### 认证

| 命令 | 别名 | 说明 |
|------|------|------|
| `login --token <token>` | | 使用 Token 登录 |
| `logout` | | 登出并清除 Token |
| `whoami` | | 显示当前登录用户 |

### 搜索和发现

| 命令 | 别名 | 说明 |
|------|------|------|
| `search <query...>` | | 搜索 skills |
| `namespaces` | | 列出用户有权限的命名空间 |
| `info <slug>` | `view` | 查看 skill 详情 (单命名空间) |
| `inspect <slug>` | | 查看 skill 详情 (跨所有命名空间搜索) |
| `explore` | | 浏览最新更新的 skills |
| `versions <slug>` | | 列出 skill 的所有版本 |
| `resolve <slug>` | | 获取 skill 最新版本信息 |

### 安装和发布

| 命令 | 别名 | 说明 |
|------|------|------|
| `install <slug>` | `i` | 从 registry 安装 skill |
| `add <source>` | | 从 GitHub/URL/本地路径安装 |
| `publish [path]` | | 发布本地 skill 到 registry |
| `sync [path]` | | 扫描目录并批量发布 |
| `download <slug>` | | 下载 skill 包到本地 |
| `init [name]` | | 创建 SKILL.md 模板 |

### 本地管理

| 命令 | 别名 | 说明 |
|------|------|------|
| `list` | `ls` | 列出已安装的 skills |
| `remove <name>` | `rm` | 移除已安装的 skill |
| `uninstall <name>` | `un` | 卸载 skill |
| `update [slug]` | `up` | 更新已安装的 skill |
| `archive <slug>` | | 归档 skill |

### 社交功能

| 命令 | 别名 | 说明 |
|------|------|------|
| `star <slug>` | | 给 skill 加星 |
| `rating <slug>` | | 查看对 skill 的评分 |
| `rate <slug> <score>` | | 给 skill 评分 (1-5) |
| `me skills` | | 查看我发布的 skills |
| `me stars` | | 查看我加星的 skills |
| `reviews` | | 管理 skill 审核 |
| `notifications` | `notif` | 管理通知 |
| `report <slug>` | | 举报 skill |

### 删除和清理

| 命令 | 别名 | 说明 |
|------|------|------|
| `delete <slug>` | `del` | 删除 skill (需是 owner) |

### 全局选项

| 选项 | 说明 |
|------|------|
| `--registry <url>` | 指定 registry URL (默认: http://localhost:8080) |
| `--no-input` | 禁用所有交互提示 |
| `--json` | 以 JSON 格式输出 |

---

## 选项详解

### install 选项

```bash
node dist/cli.mjs install <slug> [选项]

选项:
  --namespace <ns>     命名空间 (默认: global)
  --agent <agents...>  目标 agent (多个用空格分隔)
  -g, --global         安装到全局目录
  --copy               复制而非 symlink
  -y, --yes            跳过确认
```

### search 选项

```bash
node dist/cli.mjs search <query...> [选项]

选项:
  --namespace <ns>  限定命名空间
  --json            JSON 输出
```

### publish 选项

```bash
node dist/cli.mjs publish [path] [选项]

选项:
  --namespace <ns>      目标命名空间 (默认: global)
  --slug <slug>         Skill slug
  -v, --ver <ver>       版本号 (semver)
  --name <name>         显示名称
  --changelog <text>    更新日志
  --tags <tags>         标签 (逗号分隔)
```

### sync 选项

```bash
node dist/cli.mjs sync [path] [选项]

选项:
  --namespace <ns>  目标命名空间 (默认: global)
  --all             包含所有 skills (即使无变化)
  -y, --yes         跳过确认
  --dry-run          预览模式
```

### explore 选项

```bash
node dist/cli.mjs explore [选项]

选项:
  -n, --limit <n>   最大结果数 (默认: 20)
```

### update 选项

```bash
node dist/cli.mjs update [slug] [选项]

选项:
  -a, --all         更新所有已安装的 skills
  -g, --global      只更新全局 skills
```

### inspect vs info 区别

| 特性 | `inspect` | `info` |
|------|-----------|--------|
| 命名空间搜索 | 跨所有命名空间 | 单命名空间 |
| 默认命名空间 | 所有可访问的命名空间 | global |
| API 调用 | 多次 (每个命名空间一次) | 一次 |
| 用途 | 不确定 skill 在哪个命名空间时 | 知道命名空间时 |

---

## 使用示例

### 基本工作流

```bash
# 1. 登录
node dist/cli.mjs login --token YOUR_TOKEN

# 2. 搜索 skill
node dist/cli.mjs search openspec

# 3. 查看详情
node dist/cli.mjs inspect openspec
node dist/cli.mjs info openspec --namespace my-team

# 4. 安装
node dist/cli.mjs install openspec --global

# 5. 发布更新
cd my-skill
node dist/cli.mjs publish -v 1.1.0 --namespace my-team
```

### 多 Agent 安装

```bash
# 安装到多个 agents
node dist/cli.mjs install openspec --agent claude cursor codex

# 全局安装到所有 agents
node dist/cli.mjs install openspec --global

# 复制模式安装
node dist/cli.mjs install openspec --copy
```

### 批量发布

```bash
# 预览要发布的 skills
node dist/cli.mjs sync /path/to/skills --namespace my-team --dry-run

# 实际发布
node dist/cli.mjs sync /path/to/skills --namespace my-team
```

---

## 支持的 Agents (46个)

```
claude, claude-code, cursor, codex, opencode, github-copilot, cline,
windsurf, gemini, gemini-cli, roo, continue, openhands, qoder, trae,
kiro-cli, qwen-code, ollama, llama, codellama, wizardcoder, phi,
mistral, anthropic, cohere, ai21, stability, deepseek, local, jina,
perplexity, groq, fireworks, together, litellm, vllm, anyscale,
baseten, modal, replicate, bolt, goose, devin, swethe
```

---

## 与 Clawhub 对比

| 功能 | Clawhub | @motovis/skillhub |
|------|---------|-------------------|
| **命名空间支持** | ❌ 无 | ✅ 支持 |
| **多 Agent 安装** | ✅ 41+ agents | ✅ 46+ agents |
| **Inspect 跨命名空间** | ❌ 无 | ✅ 支持 |
| **Explore 浏览** | ✅ 支持 | ✅ 支持 |
| **Sync 批量发布** | ❌ 无 | ✅ 支持 |
| **update 命令** | ❌ 无 | ✅ 支持 |
| **uninstall 命令** | ❌ 无 | ✅ 支持 |
| **--json 全局选项** | ❌ 无 | ✅ 支持 |
| **--copy 安装选项** | ❌ 无 | ✅ 支持 |
| **星标/评分** | ✅ 支持 | ✅ 支持 |
| **通知管理** | ❌ 无 | ✅ 支持 |
| **审核管理** | ❌ 无 | ✅ 支持 |

---

## 命名空间功能

### 为什么需要命名空间？

- **团队隔离**: 不同团队可以有自己的 skills
- **权限控制**: 每个命名空间有独立的权限管理
- **可见性**: 可以设置为私有或公开

### 使用命名空间

```bash
# 搜索特定命名空间
node dist/cli.mjs search openspec --namespace my-team

# 从特定命名空间安装
node dist/cli.mjs install my-team--openspec --namespace my-team

# 发布到特定命名空间
node dist/cli.mjs publish -v 1.0.0 --namespace my-team

# 查看所有可访问的命名空间
node dist/cli.mjs namespaces
```

### 命名空间格式

- **Slug 格式**: `namespace--skillname` 或 `skillname`
- **默认命名空间**: `global`
- **示例**: `my-team--openspec`, `vision2group--claude-code`
