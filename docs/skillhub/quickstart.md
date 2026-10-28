# 快速开始

先选择你的目标：使用已有技能不需要部署 SkillHub；团队自托管和源码开发使用不同入口。

| 目标 | 从这里开始 |
| --- | --- |
| 搜索、安装并使用技能 | 本页的安装流程 |
| 使用一组配套技能 | [技能套装](./guide/skill-suites.md) |
| 发布自己的技能 | [发布与版本管理](./guide/skill-publish.md) |
| 批量上传套装和成员 | [Suite Bundle 导入](./guide/suite-bundle.md) |
| 部署团队注册中心 | [Docker 部署](./guide/deployment.md) / [Kubernetes](./guide/kubernetes.md) |
| 修改 SkillHub 源码 | [开发手册](https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md) |

## 1. 安装 CLI，选择注册中心

```bash
npm install -g @astron-team/skillhub
skillhub version
```

默认使用 `https://skill.xfyun.cn`。使用自托管实例时，在当前终端设置实际地址：

```bash
export SKILLHUB_REGISTRY=https://skillhub.example.com
```

公开技能可匿名搜索和安装。发布、访问受限资源等操作需要登录：

```bash
skillhub login
skillhub whoami
```

按终端提示在浏览器完成登录和设备码确认。远程终端可用 `skillhub login --no-open`。账号是否可以注册、使用哪些登录方式，由实例配置决定。

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

## 3. 在 Agent 中使用

安装完成后，在目标 Agent 中打开项目，必要时新建会话或重新加载技能。先确认 Agent 能发现该技能，再提出具体任务，例如：

> 请使用 example-skill，按它的说明处理以下材料：……

实际调用名称以技能 `SKILL.md` 中的 `name` 和 Agent 显示结果为准。安装不会自动执行技能，也不会代为配置成员所需的工具或凭据。
如果 Agent 没发现技能，检查 CLI 输出路径、当前项目、安装范围和目标 Agent，参见 [FAQ](./faq.md)。

整套安装使用 `skillhub suite install`，之后从套装的入口技能开始；详见 [技能套装使用指南](./guide/skill-suites.md)。

## 4. 更新和管理

```bash
skillhub upgrade @team/example-skill --check
skillhub upgrade @team/example-skill
```

检查计划后再更新。保留需要的本地修改；不要把 `--force` 当作默认参数。卸载、同步团队工作区和完整命令说明见 [CLI 指南](./guide/cli.md)。

## 5. 发布自己的技能

技能包根目录必须包含大写 `SKILL.md`，最小内容如下：

```markdown
---
name: example-skill
description: Describe when to use this skill and what it does.
---

# Example skill

Explain the inputs, steps, expected output, and required tools.
```

可以按需添加 `scripts/`、`references/` 和 `assets/`；不要求 `package.json`。
先在 Web 创建或加入有发布权限的命名空间，再按照 [发布指南](./guide/skill-publish.md) 准备版本并上传：

```bash
skillhub publish ./example-skill --namespace my-team
```

命令提交成功不代表版本已发布。查看返回的详情地址，跟踪扫描、审核和最终状态；只有满足发布条件的版本才进入正常安装流程。
