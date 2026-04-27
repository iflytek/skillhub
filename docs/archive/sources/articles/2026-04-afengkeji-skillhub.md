---
id: source-2026-04-afengkeji-skillhub
type: source
status: published
date: 2026-04-25
updated: 2026-04-25
confidentiality: public
source_type: article
source_url: https://mp.weixin.qq.com/s/ZVTXhK_l1HYPNLHKB_axgQ
people: [阿枫科技]
tags: [external-coverage, wechat, small-team, use-case, open-source-spirit]
related_docs: []
related_prs: []
related_issues: []
related_commits: []
related_sources: []
summary: 阿枫科技 以小团队视角分享自建 Skill 市场的实际体验，强调 SkillHub 对中大型企业的提效价值，并称赞科大讯飞"给中大型企业开发的平台却选择免费开源，格局相当大"
---

# 阿枫科技：科大讯飞开源了个狠东西，Skillhub 真的太实用了！

## 基本信息

- 公众号：阿枫科技
- 作者：阿枫科技
- 发布日期：2026 年 4 月（具体日期未显示）

## 文章摘要

作者以小团队（十来号人）的视角，分享了自建 Skill 市场的实际动机和体验。文章从"想把自己写的大纲 Skill 分享给团队成员，但每次手动发文件太麻烦"这一具体场景出发，引出 SkillHub 作为解决方案。

## 核心内容

### 使用动机
> 我前两天总结了一下我写大纲的技巧，搞出了一个大纲 Skill，想要发给团队成员使用。但我发现还要把文件一个一个地给他们发，很是有点麻烦。一次两次还好，但我后面肯定还会经常做一些对工作有帮助的 Skill，每次都手动推送，效率太低了。所以我就直接搭建了一个只属于我们团队的 Skill 市场。

### 与 ClawHub 的对比
> 经常用小龙虾的同学，应该都用过 Clawhub 下载技能吧？Skillhub 和它有点类似。但区别在于，我们自建的技能平台只允许团队成员使用，可以很好地保护我们的团队隐私。

### 部署体验
```bash
curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up
```
运行完毕后会给出访问地址，直接访问即可打开已部署好的平台。

### 功能评价
- 首页支持模糊搜索
- 技能详情页包含概述、安装依赖、文件目录，下载安装入口在右下角
- 只有经过管理员授权的账号才能下载
- 上传技能需经管理员审核后发布
- 界面简洁，"该有的功能一个都不少"

### 对中大型企业的价值
> 如果你的企业规模超过百人，那 Skillhub 就更有价值了。在这上百名成员中，可能随时会有人突发奇想，做出一个对团队工作流非常有帮助的技能。如果正常报给领导……可能小一周都过去了。但如果团队里有了这样的平台，普通成员把技能上传后，只要通过审核，就可以发布。快的话，可能一个小时内，同事们就能用上这个技能了。

## 结语观点

> 给中大型企业开发的平台，却选择了免费开源，说实话格局还是相当大的，也算是为国产 Agent 生态的发展贡献了一份不小的力量。

## 相关链接

- [原文链接](https://mp.weixin.qq.com/s/ZVTXhK_l1HYPNLHKB_axgQ)
- [SkillHub GitHub](https://github.com/iflytek/skillhub)
