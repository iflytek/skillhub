# SkillHub CLI

SkillHub CLI 是 SkillHub 的第一方命令行工具，用于搜索、安装、管理和发布 Agent 技能包。

## 安装

```bash
# 通过 npm 全局安装
npm install -g @astron-team/skillhub

# 或使用 npx 直接运行（无需安装）
npx @astron-team/skillhub@latest version

# 或通过 Bun 全局安装
bun add -g @astron-team/skillhub
```

## 快速开始

```bash
# 登录
skillhub login --token sk_xxx

# 搜索技能
skillhub search pdf

# 安装技能到 Agent 目录
skillhub install pdf-parser --agent codex

# 查看已安装技能
skillhub list

# 发布技能
skillhub publish ./my-skill --namespace myspace
```

## Registry 配置

当前生效的 registry 按以下优先级解析：

1. `--registry <url>` 命令行参数
2. `SKILLHUB_REGISTRY` 环境变量
3. 用户配置文件 `~/.skillhub/config.json` 中的 `registry` 字段
4. 默认值 `https://skill.xfyun.cn`

```bash
# 临时使用其他 registry
skillhub search pdf --registry https://skillhub.example.com

# 通过环境变量设置（Linux/macOS）
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

**Windows PowerShell:**

```powershell
$env:SKILLHUB_REGISTRY="https://skillhub.example.com"
```

**Windows CMD:**

```cmd
set SKILLHUB_REGISTRY=https://skillhub.example.com
```

## 认证

Token 按以下优先级解析：

1. `--token <token>` 命令行参数
2. `SKILLHUB_TOKEN` 环境变量
3. `~/.skillhub/credentials.json` 中存储的 token（按 registry 区分）

### 登录

```bash
# 使用 API token 登录
skillhub login --token sk_xxx

# 指定 registry 登录
skillhub login --token sk_xxx --registry https://skillhub.example.com
```

`login` 会验证 token 有效性，然后将 token 存储到 `~/.skillhub/credentials.json`，同时将 registry 写入 `~/.skillhub/config.json`。

### 查看当前身份

```bash
skillhub whoami

# 指定 registry 查看
skillhub whoami --registry https://skillhub.example.com

# 临时使用其他 token
skillhub whoami --token sk_other
```

### 登出

```bash
skillhub logout

# 登出指定 registry
skillhub logout --registry https://skillhub.example.com
```

登出只删除对应 registry 的 token，保留 registry 配置和安装记录。

## 搜索

```bash
# 关键词搜索
skillhub search pdf

# 列出所有技能（空字符串查询）
skillhub search "" --limit 50

# JSON 输出
skillhub search pdf --json
```

输出格式：`namespace/slug  version  summary`

## 安装技能

```bash
# 安装到自动探测的 Agent 目录
skillhub install pdf-parser

# 指定 namespace（默认 global）
skillhub install pdf-parser --namespace myspace

# 指定版本
skillhub install pdf-parser --version 1.2.0

# 安装到指定 Agent
skillhub install pdf-parser --agent codex

# 安装到多个 Agent
skillhub install pdf-parser --agent codex --agent claude-code

# 安装到自定义目录
skillhub install pdf-parser --dir ~/.claude/skills

# 强制覆盖已存在的安装
skillhub install pdf-parser --force
```

### 安装目标解析

CLI 按以下逻辑确定安装位置：

1. 指定 `--dir`：安装到该目录，agent 标记为 `custom`
2. 指定 `--agent`：安装到对应 Agent 的 skills 目录
3. 未指定：自动扫描当前目录，探测已存在的 Agent 配置目录
   - 探测到 1 个 Agent → 直接安装
   - 探测到多个 Agent → 交互式选择（TTY 模式）或报错（非交互模式）
   - 未探测到 → 回退到 `<cwd>/.agents/skills/`

> `--dir` 和 `--agent` 不能同时使用。

### 安装路径

每个 Agent 有项目级和用户级两个 skills 目录：

| Agent | 项目级路径 | 用户级路径 |
|-------|-----------|-----------|
| `claude-code` | `<project>/.claude/skills/` | `~/.claude/skills/` |
| `codex` | `<project>/.codex/skills/` | `~/.codex/skills/` |
| `cursor` | `<project>/.cursor/skills/` | `~/.cursor/skills/` |
| `github-copilot` | `<project>/.github-copilot/skills/` | `~/.github-copilot/skills/` |
| `gemini-cli` | `<project>/.gemini-cli/skills/` | `~/.gemini-cli/skills/` |
| `windsurf` | `<project>/.windsurf/skills/` | `~/.windsurf/skills/` |
| `kiro-cli` | `<project>/.kiro-cli/skills/` | `~/.kiro-cli/skills/` |
| `roo` | `<project>/.roo/skills/` | `~/.roo/skills/` |
| `trae` | `<project>/.trae/skills/` | `~/.trae/skills/` |
| `trae-cn` | `<project>/.trae-cn/skills/` | `~/.trae-cn/skills/` |
| `openhands` | `<project>/.openhands/skills/` | `~/.openhands/skills/` |
| `openclaw` | `<project>/.openclaw/skills/` | `~/.openclaw/skills/` |
| `opencode` | `<project>/.opencode/skills/` | `~/.opencode/skills/` |
| `kilo` | `<project>/.kilo/skills/` | `~/.kilo/skills/` |

对于不在列表中的 Agent，使用 `--dir` 指定安装路径。

### 安装后的文件结构

```
.codex/skills/pdf-parser/
├── ...                          # 技能包解压后的文件
└── .skillhub/
    └── metadata.json            # 安装元数据
```

`metadata.json` 内容示例：

```json
{
  "registry": "https://skill.xfyun.cn",
  "namespace": "global",
  "slug": "pdf-parser",
  "version": "1.0.0",
  "agent": "codex",
  "installedAt": "2026-04-28T06:00:00.000Z"
}
```

## 本地管理

### 查看已安装技能

```bash
# 列出所有已安装技能
skillhub list

# 按 Agent 过滤
skillhub list --agent codex

# 按多个 Agent 过滤
skillhub list --agent codex --agent claude-code

# 按目录过滤
skillhub list --dir ~/.codex/skills

# JSON 输出
skillhub list --json
```

### 删除技能

```bash
# 删除所有本地安装目标
skillhub remove pdf-parser

# 只删除指定 Agent 的安装
skillhub remove pdf-parser --agent codex

# 删除所有目标（跳过交互确认）
skillhub remove pdf-parser --all

# 删除远程技能（需要认证，会弹出确认提示）
skillhub remove pdf-parser --remote --namespace myspace

# 跳过远程删除确认
skillhub remove pdf-parser --remote --hard --namespace myspace
```

> 参数互斥规则：
> - `--all` 不能与 `--agent` 同时使用
> - `--remote` 不能与 `--agent` 或 `--all` 同时使用
> - 非交互环境下远程删除必须加 `--hard`

### 重建本地清单

```bash
skillhub doctor
```

`doctor` 执行以下操作：

1. 扫描 `<cwd>/.<agent>/skills/<slug>/.skillhub/metadata.json`
2. 按 `registry + namespace + slug` 分组
3. 备份旧的 `inventory.json`（如果存在）
4. 写入新的 `inventory.json`

如果同一技能在不同目标中存在版本冲突，该技能会被跳过并报告。

## 发布

```bash
# 发布目录（自动打包为 zip）
skillhub publish ./my-skill --namespace myspace

# 发布已有的 zip 文件
skillhub publish ./my-skill.zip --namespace myspace

# 指定可见性
skillhub publish ./my-skill --namespace myspace --visibility private
```

可见性选项：
- `public`（默认）— 所有人可见
- `namespace-only` — 仅 namespace 成员可见
- `private` — 仅自己可见

发布成功后会输出技能详情页 URL。

## 自更新

```bash
# 检查是否有新版本
skillhub update --check

# 执行更新
skillhub update
```

更新机制：
- 通过 npm 全局安装：自动执行 `npm install -g @astron-team/skillhub@latest`
- 通过 Bun 全局安装：自动执行 `bun add -g @astron-team/skillhub@latest`
- 通过 npx 运行：提示手动更新命令
- 未知安装方式：提示手动更新

## 环境变量

| 变量 | 说明 | 优先级 |
|------|------|--------|
| `SKILLHUB_REGISTRY` | 默认 registry URL | 低于 `--registry` 参数 |
| `SKILLHUB_TOKEN` | API token | 低于 `--token` 参数，高于存储的 token |

## 本地文件结构

```
~/.skillhub/
├── config.json           # 用户配置（registry、defaultAgent 等）
├── credentials.json      # API tokens（按 registry 存储，权限 0600）
└── inventory.json        # 已安装技能清单
```

### config.json

```json
{
  "registry": "https://skill.xfyun.cn",
  "defaultAgent": "codex",
  "lastUpdateCheckAt": "2026-04-28T06:00:00.000Z"
}
```

### credentials.json

```json
{
  "tokens": {
    "https://skill.xfyun.cn": "sk_xxx",
    "https://skillhub.example.com": "sk_yyy"
  }
}
```

### inventory.json

```json
{
  "items": [
    {
      "registry": "https://skill.xfyun.cn",
      "namespace": "global",
      "slug": "pdf-parser",
      "version": "1.0.0",
      "targets": [
        {
          "agent": "codex",
          "rootDir": "/path/to/project/.codex/skills",
          "installDir": "/path/to/project/.codex/skills/pdf-parser",
          "installedAt": "2026-04-28T06:00:00.000Z"
        }
      ]
    }
  ]
}
```

## JSON 输出

所有命令都支持 `--json` 参数，输出机器可读的 JSON 格式：

```bash
skillhub search pdf --json
skillhub list --json
skillhub whoami --json
skillhub install pdf-parser --json
skillhub remove pdf-parser --json
skillhub doctor --json
```

成功响应格式：

```json
{
  "ok": true,
  ...
}
```

错误响应格式：

```json
{
  "ok": false,
  "message": "error message",
  "exitCode": 2,
  "details": {
    "registry": "https://skill.xfyun.cn",
    "next": "run `skillhub login`"
  }
}
```

## 退出码

| 退出码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1 | 通用错误 |
| 2 | 认证失败 |
| 3 | 网络错误 |
| 4 | 文件系统错误 |
| 5 | 参数错误 |

## 命令参考

### help

```bash
skillhub help
skillhub help install
```

显示帮助信息。

### version

```bash
skillhub version
skillhub version --json
```

显示 CLI 版本。

### login

```bash
skillhub login --token <token> [--registry <url>] [--json]
```

保存 token 和 registry 配置。

### logout

```bash
skillhub logout [--registry <url>] [--json]
```

删除指定 registry 的 token。

### whoami

```bash
skillhub whoami [--registry <url>] [--token <token>] [--json]
```

验证当前 token 并显示用户信息。

### search

```bash
skillhub search <query> [--registry <url>] [--limit <n>] [--json]
```

搜索已发布的技能。

### install

```bash
skillhub install <slug> [options]
```

选项：
- `--namespace <slug>` — namespace（默认 `global`）
- `--version <v>` — 版本（默认最新版本）
- `--agent <profile>` — Agent 配置（可重复）
- `--dir <path>` — 自定义安装目录
- `--force` — 覆盖已存在的安装
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON 输出

### list

```bash
skillhub list [options]
```

选项：
- `--agent <profile>` — 按 Agent 过滤（可重复）
- `--dir <path>` — 按目录过滤
- `--registry <url>` — Registry URL
- `--json` — JSON 输出

### remove

```bash
skillhub remove <slug> [options]
```

选项：
- `--agent <profile>` — 按 Agent 过滤（可重复）
- `--all` — 删除所有目标
- `--remote` — 删除远程技能
- `--hard` — 跳过远程删除确认
- `--namespace <slug>` — 远程删除的 namespace
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON 输出

### doctor

```bash
skillhub doctor [--json]
```

扫描项目目录，重建本地清单。

### publish

```bash
skillhub publish <path> [options]
```

选项：
- `--namespace <slug>` — Namespace
- `--visibility <v>` — 可见性（`public` | `namespace-only` | `private`）
- `--registry <url>` — Registry URL
- `--token <token>` — API token
- `--json` — JSON 输出

### update

```bash
skillhub update [--check] [--json]
```

检查或执行 CLI 自更新。

## 安全说明

- Token 只存储在用户目录 `~/.skillhub/credentials.json`
- 在 Linux/macOS 上，凭据文件权限自动设置为 `0600`
- 不会将 token 写入任何项目本地文件
- 远程删除操作需要显式确认或 `--hard` 参数
- `remove` 命令会验证路径安全性，防止删除非技能目录

## 故障排查

### 认证失败

```bash
# 验证 token 是否有效
skillhub whoami

# 重新登录
skillhub login --token sk_xxx
```

### 网络错误

```bash
# 检查 registry 是否可访问
curl https://skill.xfyun.cn/api/cli/v1/skills/search?q=test&limit=1

# 使用其他 registry
skillhub search test --registry https://skillhub.example.com
```

### 安装目录冲突

```bash
# 使用 --force 覆盖
skillhub install pdf-parser --force

# 或先删除再安装
skillhub remove pdf-parser
skillhub install pdf-parser
```

### 清单损坏

```bash
# 重建清单
skillhub doctor
```

## 本地开发验证

如果你在本地开发 SkillHub，可以这样验证 CLI：

```bash
# 1. 构建 CLI
cd cli
bun install
bun run build
bun link

# 2. 启动本地后端
cd ..
make dev-all

# 3. 配置 CLI 连接本地服务（Linux/macOS）
export SKILLHUB_REGISTRY=http://localhost:8080

# Windows PowerShell:
# $env:SKILLHUB_REGISTRY="http://localhost:8080"

# Windows CMD:
# set SKILLHUB_REGISTRY=http://localhost:8080

# 4. 测试命令
skillhub search test
skillhub install example-skill --agent codex
skillhub list
```

## 相关链接

- [SkillHub 主页](https://skill.xfyun.cn)
- [GitHub 仓库](https://github.com/iflytek/skillhub)
- [问题反馈](https://github.com/iflytek/skillhub/issues)

## 许可证

Apache-2.0

Copyright 2026 iFlytek Co., Ltd.
