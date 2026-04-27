---
id: source-2026-04-aiying-skillhub
type: source
status: published
date: 2026-04-25
updated: 2026-04-25
confidentiality: public
source_type: article
source_url: https://mp.weixin.qq.com/s/L5aVbOEi2v9-66tbD465lg
people: [AI产品阿颖]
tags: [external-coverage, wechat, team-use-case, knowledge-management, skill-asset]
related_docs: []
related_prs: []
related_issues: []
related_commits: []
related_sources: []
summary: AI产品阿颖 从团队实际使用 Skill 的痛点出发，将 SkillHub 定位为"组织沉淀经验的正确方式"，分享了在亦庄大会上得知该项目并当天部署试用的真实经历
---

# AI产品阿颖：这个神级开源项目，解决了 Skill 的最大痛点。

## 基本信息

- 公众号：AI产品阿颖
- 作者：AI产品阿颖
- 发布日期：2026 年 4 月（具体日期未显示）

## 文章摘要

作者从团队实际使用 Skill 的痛点出发，将 SkillHub 定位为"组织沉淀经验的正确方式"。文章以第一人称叙述了在亦庄卡兹克大会茶歇时得知 SkillHub、当天回公司部署试用的真实经历，视角真实接地气。

## 核心内容

### 团队使用 Skill 的痛点
作者以筹备 AI Maker Summit 深圳场为例，说明团队内部定制 Skill 的价值：
- 新人培训靠手把手，经验随人员流失
- Skill 不是让人去读的，是让 Agent 去跑的，把隐性知识变成可执行能力
- 多人多版本，不知道谁有什么、谁在用哪个版本
- 不可能把团队 Skill 放到 ClawHub 上公开

### 发现 SkillHub 的经过
> 昨天在亦庄参加卡兹克的大会，茶歇的时候碰到一个老朋友。聊到 Skill 共享这个痛点，他说你可以看看 SkillHub，一个开源项目，科大讯飞团队做的，专门解决这个问题。下午回到公司，麻溜装上试了试。

### 部署方式
```bash
curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up
```
也可以直接把 GitHub 地址发给 Claude Code 或 Codex，让 AI 帮忙完成安装。

### 核心功能体验
- 命名空间：按部门隔离，支持权限管理
- 上传审核：上传后需管理员审核，系统自动标出可能有问题的地方
- 版本管理：统一放 SkillHub，更新了就是更新了，大家拿到的都是同一份
- Token 登录：权限控制体系，需登录才能使用

### 对 SkillHub 价值的判断
> 往长远看，Skill 一定会成为企业最重要的资产。但这个资产之前一直是散的。今天这个开源项目，算是把最后一块拼图给补上了。

## 结语观点

> 接下来我准备把它部署到公司局域网，把这段时间常用的 Skill 都放进去。以后团队要用什么，直接从 SkillHub 上找就行。

## 相关链接

- [原文链接](https://mp.weixin.qq.com/s/L5aVbOEi2v9-66tbD465lg)
- [SkillHub GitHub](https://github.com/iflytek/skillhub)
