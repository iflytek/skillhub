---
id: source-2026-04-01-githubdaily-skillhub
type: source
status: published
date: 2026-04-01
updated: 2026-04-25
confidentiality: public
source_type: article
source_url: https://mp.weixin.qq.com/s/JVXPMNrCC3b3fcvZ32WYkA
people: [小G]
tags: [external-coverage, wechat, githubdaily, deployment, namespace]
related_docs: []
related_prs: []
related_issues: []
related_commits: []
related_sources: []
summary: GitHubDaily 公众号发布 SkillHub 开源介绍，详细演示部署、命名空间、发布审核、搜索、API Token 等核心功能
---

# GitHubDaily：SkillHub，开源了！

## 基本信息

- 公众号：GitHubDaily
- 作者：小G
- 发布日期：2026 年 4 月 1 日
- 地区：广东

## 文章摘要

文章以"给 AI 装 Skill 就像以前装插件"为切入点，指出随着 Skill 技能包大量涌现，企业面临迭代管理、权限分配、数据安全三大痛点，引出科大讯飞开源的 SkillHub 作为解决方案。

文章将 SkillHub 定位为"技能商店"，支持私有化部署，数据完全自主可控，并兼容 ClawHub CLI 协议。

## 核心内容

### 一条命令，直接运行
```bash
curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up
```
安装完成后访问 `http://localhost` 即可看到 SkillHub 首页。

### 命名空间：成员管理，权限分配
每个团队拥有独立空间，技术和产品团队互不干扰，支持公开或仅团队可见两种可见性。

### 发布技能：一键上传到空间
上传 `.zip` 文件即可发布，普通成员发布需管理员审核，版本号自动生成，支持"申请提升到全局"。

### 审核流：进入团队前的最后一关
管理员在审核中心查看待审核列表，可浏览技能文件内容并给出评估建议。

### 搜索发现：找技能跟搜代码一样快
支持按相关性、下载量、最新三个维度排序，可勾选"只看已收藏"快速过滤。

### API Token：接入 CI/CD，自动化发布
支持 7 天、30 天、90 天、永不过期或自定义有效期，Token 以 `sk_` 为前缀，创建后仅展示一次。

### 一次发布，多平台复用
已支持 OpenClaw、AstronClaw、Loomy、astron-agent 等平台。

## 结语观点

> ClawHub、skills.sh 这些公开技能平台，已经验证了技能共享这件事的价值。但公开技能平台天然解决不了团队内部的需求，私有技能的治理，是这些平台覆盖不到的地方。

> SkillHub，可以说是这个方向上，现阶段最为完善的开源解决方案之一。

## 相关链接

- [原文链接](https://mp.weixin.qq.com/s/JVXPMNrCC3b3fcvZ32WYkA)
- [SkillHub GitHub](https://github.com/iflytek/skillhub)
