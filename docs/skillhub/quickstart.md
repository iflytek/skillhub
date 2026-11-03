# 快速开始：安装并使用技能

安装第一个技能，并在 Agent 中使用。本页使用已有的注册中心，不需要先部署 SkillHub 服务。

## 开始前准备

- 一个支持技能目录的 Agent，例如 Codex 或 Claude Code。
- Node.js 18 或更新版本，以及可用的 npm；可用 `node --version` 确认版本。
- 一个准备使用技能的项目目录，以及能访问的 SkillHub 注册中心地址。

本页命令使用终端运行，`@team/example-skill` 是占位坐标，不是保证存在的公共技能。选择实际搜索结果后再替换。项目级安装前，先切换到准备让 Agent 打开的项目根目录。

## 1. 安装 CLI，选择注册中心

```bash
npm install -g @astron-team/skillhub
skillhub version
```

默认使用 `https://skill.xfyun.cn`。使用自托管实例时，在当前终端设置实际地址：

```bash
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

PowerShell 中设置注册中心使用：

```powershell
$env:SKILLHUB_REGISTRY="https://skillhub.example.com"
```

只想临时使用另一个实例时，可以在命令后添加 `--registry https://skillhub.example.com`。搜索、登录和安装应使用同一个注册中心；不同实例中的同名技能不是同一安装来源。

公开技能可匿名搜索和安装。发布、访问受限资源等操作需要登录：

```bash
skillhub login
skillhub whoami
```

没有从命令行、环境变量或该注册中心的保存记录读取到 Token 时，CLI 会进入设备授权；按终端提示在浏览器完成登录和设备码确认。已有 Token 时先用该 Token 验证身份，不会自动重新选账号。远程终端可用 `skillhub login --no-open`。账号是否可以注册、使用哪些登录方式，由实例配置决定。

## 2. 找到技能并安装

```bash
skillhub search pdf
```

在网页详情确认用途、版本、可见性及使用说明，然后复制真实坐标。下面的 `@team/example-skill` 是占位示例，必须替换成搜索结果中的坐标。

在目标项目根目录执行：

```bash
skillhub install @team/example-skill --scope project --agent codex
skillhub list --agent codex
```

Claude Code 用户改用 `--agent claude-code`。希望多个项目共用时，使用 `--scope user`。
项目级 Codex 安装目录为 `.codex/skills/<slug>/`，Claude Code 为 `.claude/skills/<slug>/`；用户级路径位于用户主目录。

### 确认安装到了哪里

查看安装命令输出的目标路径，再查看 `skillhub list --agent codex` 的记录：坐标、版本、Agent、目录和状态应符合刚才选择的目标。`list` 按当前注册中心过滤；切换注册中心后列表变化不代表本地文件被删除。

状态 `missing` 表示记录对应的目录不存在；`ok` 只表示目录存在，不代表 Agent 已加载或技能已执行成功。进入实际目录检查 `SKILL.md`，再继续下一步。

## 3. 在 Agent 中使用

安装完成后，在目标 Agent 中打开项目，必要时新建会话或重新加载技能。先确认 Agent 能发现该技能，再提出具体任务，例如：

> 请使用 example-skill，按它的说明处理以下材料：……

实际调用名称以技能 `SKILL.md` 中的 `name` 和 Agent 显示结果为准。安装不会自动执行技能，也不会代为配置成员所需的工具或凭据。
如果 Agent 没发现技能，检查 CLI 输出路径、当前项目、安装范围和目标 Agent，参见 [FAQ](./faq.html)。

整套安装使用 `skillhub suite install`，之后从套装的入口技能开始；详见 [技能套装使用指南](./guide/skill-suites.html)。

## 4. 更新和管理

```bash
skillhub upgrade @team/example-skill --check
skillhub upgrade @team/example-skill
```

检查计划后再更新。保留需要的本地修改；不要把 `--force` 当作默认参数。卸载、同步团队工作区和完整命令说明见 [CLI 指南](./guide/cli.html)。

## 常见问题与处理


| 现象 | 定位动作 | 正常结果或原因 | 检查与处理 |
| --- | --- | --- | --- |
| 搜索不到目标技能 | 同一终端执行 `skillhub search pdf` 与 `skillhub whoami`；在同一注册中心网页查实际坐标 | 搜索返回有权查看的已发布资源；公开搜索可匿名，身份接口未登录不代表公开搜索故障 | 校对实例、坐标和发布状态；受限资源先完成登录，不要通过重置账号或删除本地目录排障 |
| 安装时报同名目录冲突 | 执行 `skillhub list --agent codex --json`，将 `items[].installDir` 与冲突提示路径比较；打开该目录的 `SKILL.md` | 已记录安装应显示匹配的注册中心、版本与目录；无记录的已有目录也可能是手工文件 | 先备份并辨明目录来源，再选择正确项目或安装范围；不要用 `--force` 覆盖未知内容 |
| 安装成功但 Agent 看不到 | 在目标项目根目录执行 `skillhub list --agent codex --json`，检查 `items[].agent`、`installDir`、`status`，再打开实际目录的 `SKILL.md` | `status=ok` 仅表示目录存在；`missing` 表示记录对应目录不存在；Agent 还须发现该目录 | 核对项目与 Agent 类型，按 Agent 的发现方式重载或新建会话；不要仅凭 `ok` 认定技能已被加载 |
| Agent 已加载但执行失败 | 打开已安装目录的 `SKILL.md`，逐项核对所需工具、输入和凭据，并读取 Agent 返回的具体错误 | 文件安装正常仍可能缺运行依赖或权限；注册中心不代为配置外部工具 | 按技能说明在目标环境补齐依赖；不要把凭据写进共享技能包或公开错误报告 |

以上命令在安装技能的项目目录执行，`--agent codex` 必须与实际 Agent 一致，Claude Code 使用 `--agent claude-code`。`pdf` 是可替换的搜索词；排障命令不保证存在同名技能。CLI 字段与过滤规则见 [list 实现](https://github.com/iflytek/skillhub/blob/main/cli/src/commands/list.ts)及 [CLI 参数定义](https://github.com/iflytek/skillhub/blob/main/cli/src/index.ts)。

## 下一步

需要配套技能时，阅读[技能套装](./guide/skill-suites.html)；需要更多命令时，查阅 [CLI 指南](./guide/cli.html)。发布技能、管理团队、自托管和源码开发，请从[文档导航](./guide/index.html)选择对应任务。
