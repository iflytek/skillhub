# 快速开始：安装并使用技能

本页从已有可访问的注册中心开始，完成单个技能的安装、Agent 发现及首次使用检查。不涉及部署注册中心或发布技能。流程终点是 Agent 能读取技能并完成一次具体任务，而不只是 CLI 返回安装成功。

![选择注册中心、安装文件、确认发现与验收执行的顺序](/diagrams/first-install-flow.svg)

图：每一步均有独立完成标准；后一步失败时，应先定位对应阶段，而不是重新安装全部内容。

## 前置准备

在终端执行命令，并先切换至目标 Agent 将打开的项目根目录。准备一个可访问的注册中心以及 Codex、Claude Code 等支持技能目录的 Agent。

| 项目 | 要求或取值 | 检查与生效方式 |
| --- | --- | --- |
| Node.js | 最低18，且 npm 可用 | 执行 `node --version`、`npm --version`；更换运行环境后重新打开终端 |
| 注册中心 | 内置地址为 `https://skill.xfyun.cn` | 选择顺序为命令参数、`SKILLHUB_REGISTRY`、本地配置、内置地址；环境变量在当前终端生效 |
| 安装范围 | `project` 或 `user` | 本页显式使用项目级；用户级位于主目录，不等于所有 Agent 都会自动加载 |
| 目标 Agent | 本页使用 `codex` | Claude Code 改用 `claude-code`；必须与实际打开项目的 Agent 一致 |

`skillhub.example.com` 与 `@team/example-skill` 均为占位示例，不保证存在。必须分别替换为实际实例地址及搜索结果中的技能坐标。最低运行版本与参数来源见 [CLI 清单](https://github.com/iflytek/skillhub/blob/main/cli/package.json)及[命令定义](https://github.com/iflytek/skillhub/blob/main/cli/src/index.ts)。

## 1. 安装 CLI 并选择注册中心

```bash
npm install -g @astron-team/skillhub
skillhub version
```

版本命令应输出 `SkillHub CLI` 及实际安装版本。出现命令不存在时，应先检查 npm 全局可执行目录是否位于当前终端的 `PATH`，不要继续执行安装技能命令。

使用默认实例时无需设置地址。自托管实例在当前终端设置实际地址，随后所有命令保持同一来源：

```bash
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

PowerShell 对应设置为：

```powershell
$env:SKILLHUB_REGISTRY="https://skillhub.example.com"
```

单次操作也可添加 `--registry https://skillhub.example.com`。不同实例的同名技能不是同一来源；切换来源不会搬迁已安装文件。

## 2. 搜索技能并核对身份

```bash
skillhub search pdf
```

输出每行依次为命名空间／技能标识、最新版本和摘要；无结果时显示 `No skills found.`。在同一实例的网页详情核对用途、版本、访问范围及依赖，然后复制真实坐标。

公开技能支持匿名搜索和安装；受限资源需要登录，并仍受资源权限约束：

```bash
skillhub login
skillhub whoami
```

不存在命令参数、环境变量或该注册中心保存的 Token 时，登录进入设备授权，终端显示授权地址与设备码。浏览器登录后须完成设备码确认；网页已有登录态不等于 CLI 已获凭据。已有 Token 时会先验证该凭据，不会自动选择其他账号。远程终端使用 `skillhub login --no-open`；可用登录方式和注册入口由实例配置决定。`whoami` 应显示所选注册中心和账号，具体处理见[账号与访问令牌](./guide/account-access.html)。

## 3. 安装并检查文件与记录

在目标项目根目录执行，先将占位坐标替换为真实结果：

```bash
skillhub install @team/example-skill --scope project --agent codex
skillhub list --agent codex --json
```

以下为格式示例，不是真实下载结果。安装命令输出的路径应对应当前项目；项目级目录结构应包含 `SKILL.md`，其他文件取决于技能包内容：

```text
Installed team/example-skill -> /work/project/.codex/skills/example-skill (codex)

/work/project/.codex/skills/example-skill/
└── SKILL.md
```

`list` 的 JSON 中应存在对应记录。以下是完整结构示例，路径、时间及版本必须以本次实际输出为准：

```json
{"ok":true,"items":[{"namespace":"team","slug":"example-skill","version":"1.0.0","agent":"codex","installDir":"/work/project/.codex/skills/example-skill","installedAt":"2026-11-03T08:00:00.000Z","status":"ok"}]}
```

核对坐标、Agent 与 `installDir`，并打开该目录的 `SKILL.md`。状态 `ok` 仅表示目录存在，不代表内容已执行；`missing` 表示记录对应目录缺失。列表按当前注册中心过滤，但并非仅列当前项目，必须同时核对路径。Claude Code 项目级目录为 `.claude/skills/<slug>/`。使用 `--scope user` 时目录位于主目录，对应文件发现规则由 Agent 决定。

输出依据见[安装命令](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/install.ts)与[安装清单命令](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/list.ts)。

## 4. 验证 Agent 发现并执行任务

在目标 Agent 中打开上述项目，按该产品的加载机制重载技能或新建会话。核对 Agent 可用技能中出现目标名称，再按技能说明提供一次具体任务、输入与预期产物。调用名称以实际 `SKILL.md` 的 `name` 及 Agent 识别结果为准。

| 阶段 | 可观察完成标准 | 尚不能据此确认的事项 |
| --- | --- | --- |
| 文件安装 | 安装路径有 `SKILL.md`，清单记录匹配且 `status=ok` | Agent 是否已加载文件 |
| Agent 发现 | Agent 的技能列表或会话上下文包含目标技能 | 外部工具、凭据与业务依赖是否可用 |
| 任务执行 | Agent 按技能要求产出可核对的结果 | 其他输入、环境或后续版本是否同样适用 |

安装不会自动执行技能或配置外部工具。多技能协作需核对入口与成员说明；共同安装使用 `skillhub suite install`，操作见[技能套装](./guide/skill-suites.html)。

## 5. 验证参数错误的拒绝行为

以下命令使用无效安装范围，演示本地参数校验的拒绝行为：

```bash
skillhub install @team/example-skill --scope invalid --agent codex
```

预期在标准错误输出中看到下列内容，退出码为5；命令在读取安装配置和发起下载前即停止，不会安装示例技能：

```text
Error: --scope must be "user" or "project"
```

改回 `--scope project`，并替换真实坐标后再执行。不要用 `--force` 处理参数错误。该示例的错误消息与执行顺序见[安装参数校验](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/install.ts)。

## 失败定位与处置

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 搜索无目标结果 | 来源、权限或发布状态不符 | 在同一终端检查 `skillhub search pdf`、`skillhub whoami`，并在同一实例核对坐标 | 核对来源与发布状态，必要时登录；不要通过重置账号排障 |
| 安装提示同名目录已存在 | 既有安装或手工文件占用路径 | 对照提示路径与 `skillhub list --agent codex --json` 的 `installDir`，读取目录内 `SKILL.md` | 备份并辨明来源，选择正确项目或范围；不要覆盖未知内容 |
| 安装后 Agent 未发现技能 | 项目、范围或目标 Agent 不符 | 核对清单中的 `agent`、`installDir`、`status`，确认 Agent 打开的项目 | 修正目标并按 Agent 机制重载；不要将 `ok` 当作加载完成 |
| 已发现技能但执行失败 | 缺少运行依赖、输入或权限 | 读取本地 `SKILL.md`，对照 Agent 错误检查工具及凭据要求 | 在目标环境补齐依赖；不要把凭据写入共享技能包或公开日志 |

## 更新与后续任务

在已完成上述检查后，先查看升级计划，再决定执行更新：

```bash
skillhub upgrade @team/example-skill --check
skillhub upgrade @team/example-skill
```

升级可能受本地修改、共享目录或来源保护限制，必须先读取计划；不要默认添加 `--force`。本页终点是单技能首次可用，后续卸载、版本管理及完整选项见[CLI 参考](./guide/cli.html)；作者发布和部署任务从[文档导航](./guide/index.html)进入。
