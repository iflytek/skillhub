# 搜索与安装技能

从已有注册中心找到一个技能，确认可用版本，再安装到实际使用的 Agent。搜索结果、网页详情和本地安装是三个不同环节；看到卡片不等于已经安装。

## 开始前

公开技能可匿名浏览。查找团队或私有资源时先登录正确实例，并确认拥有对应命名空间权限。准备 CLI 和 Agent 的步骤见[快速开始](../quickstart.md)。以下路径相对于实际部署地址，不固定开发端口。

## 在网页查找

1. 打开首页搜索框或 `/search`，输入任务关键词，例如 `pdf`。
2. 要限定空间，输入 `@team pdf`；只输入 `@team` 可浏览该空间。`team` 必须替换为真实空间标识。
3. 点击标签缩小范围；再次点击选中的标签取消筛选。当前网页一次选择一个标签。
4. 按相关性、下载量或最新排序。网页默认“最新”，没有独立的评分排序。
5. 打开技能卡片，核对名称、坐标、说明、版本和文件，而不是仅凭标题决定安装。

搜索词、空间、标签、排序和页码会保留在 URL 中，可复制链接或返回结果页继续浏览。关键词最多取前 50 个字符，长篇任务应提炼成关键词。

## 仅查看收藏

登录后在搜索页启用“仅收藏”；未登录时会引导登录。此模式在已获取的收藏中按名称、摘要、空间和 slug 匹配关键词，不使用普通服务端全文搜索。可按空间和下载量筛选/排序；“相关性”和“最新”均按更新时间排序。当前收藏分支不应用标签过滤，使用标签检索时先关闭“仅收藏”。

“我的收藏”位于 `/dashboard/stars`，提供分页浏览；详细操作见[收藏、评分与通知](./social.md)。

## 从详情决定安装范围

核对技能说明是否匹配任务，以及所需脚本、工具和服务是否可用。安装坐标使用 `@namespace/slug`，展示名变化不会改变坐标。

“关联套件”表示该技能被当前用户可见的套装收录。进入详情可查看入口/普通成员身份及配套成员；同一技能可能属于多个套装。需要整套使用时进入[技能套装指南](./skill-suites.md)，否则可以单独安装。

## 用 CLI 安装并确认

以下坐标是示例，先替换为实际搜索结果；默认注册中心与自托管实例不能混为一谈。

```bash
skillhub search pdf
# 在目标项目根目录安装给 Codex
skillhub install @team/example-skill --scope project --agent codex
skillhub list --agent codex
# 需要复现特定版本时显式指定
skillhub install @team/example-skill --version 1.0.0 --scope project --agent codex
```

CLI 普通搜索返回 Skill，不搜索 Suite。`search` 不提供网页全部筛选参数，使用 `skillhub help search` 核对支持的参数。私有实例可为每条命令追加 `--registry https://skillhub.example.com`。

安装成功后检查输出目录和 `SKILL.md`，让 Agent 发现该技能并提出实际任务。下载 ZIP 只是获得文件，并不会自动放入 Agent 目录。更新、目标冲突、版本保护和卸载参见 [CLI 使用参考](./cli.md)。

## 找不到或无法安装时

| 现象 | 检查与处理 |
| --- | --- |
| 搜索为空 | 清除标签、空间和仅收藏条件，再尝试短关键词；确认连接的是正确实例 |
| 同事可见而自己不可见 | 核对登录身份与空间成员权限；PRIVATE 不对全部成员开放 |
| 刚提交的版本没有出现 | 在“我的技能”检查本次版本的扫描/审核状态；提交不等于发布，索引更新也可能延迟 |
| 卡片可见但下载失败 | 重新打开详情核对版本、权限和当前资源状态；保留错误信息与 requestId |
| 安装后 Agent 找不到 | 对照 CLI 输出检查项目根目录、scope、agent 及 `SKILL.md`；按 Agent 机制重新加载 |

不要把搜索排名当作安全或质量认证；阅读说明、版本与扫描结果后再选择。

## 搜索接口边界

```text
GET /api/web/skills?q=pdf&namespace=team&label=data-processing&sort=downloads&page=0&size=20
```

`q`、`namespace`、`label` 可选；API 的 `label` 可重复传入，网页选择器则是单选。`sort` 使用 `relevance`、`downloads` 或 `newest`，页码从 0 开始。API 默认每页 20 项，网页每页 12 项。接口返回 `items`、`total`、`page`、`size` 并遵循当前身份可见范围；实际响应外层封装以实例 OpenAPI 为准。

## 图示与界面参考

![概念图](/diagrams/skill-discovery-concept.png)

![操作截图](/screenshots/skill-discovery-search.png)

![流程图](/diagrams/skill-discovery-flow.png)
