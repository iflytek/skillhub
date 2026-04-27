---
id: source-2026-04-chenmoer-skillhub
type: source
status: published
date: 2026-04-25
updated: 2026-04-25
confidentiality: public
source_type: article
source_url: https://mp.weixin.qq.com/s/KL92y4eytpa9C5Q1FnH6Ww
people: [沉默王二]
tags: [external-coverage, wechat, deep-dive, security-scan, cli, resume]
related_docs: []
related_prs: []
related_issues: []
related_commits: []
related_sources: []
summary: 沉默王二对 SkillHub 进行深度体验，覆盖部署、发布、发现、治理、安全扫描、CLI 使用全流程，并给出简历写法建议，GitHub 已达 1.5K Star
---

# 沉默王二：科大讯飞官宣开源，有点猛啊。SkillHub 免折腾，三分钟给 AI Agent 搭建技能商店。

## 基本信息

- 公众号：沉默王二
- 作者：沉默王二
- 发布日期：2026 年 4 月（具体日期未显示）
- GitHub Star 数：1.5K（文章发布时）

## 文章摘要

沉默王二对 SkillHub 进行了全流程深度体验，将其定位为"AI Agent 世界里的 npm 或 Docker Hub"。文章覆盖了从部署到 CLI 使用的完整链路，是所有报道中内容最详尽的一篇，并附有简历写法建议。

## 核心内容

### 定位
> 科大讯飞这次干的漂亮。他们开源了一个叫 SkillHub 的项目，定位是"企业级 Agent Skill 商店"。说人话就是：给 AI Agent 造了一个 npm，或者说是 Docker Hub，但专门为 Skills 设计。

### 一键部署
```bash
curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up
```
整个过程约两三分钟，无需配置数据库、安装依赖、修改配置文件。

### Skill 包格式
核心格式为 `SKILL.md`，包含 frontmatter（元数据）和 body（内容）两部分，与 Hugo、Jekyll 等静态博客引擎格式一致。

### 安全扫描机制（详解）
文章专门用一节详解了安全扫描流程：
1. **结构校验**：验证 SKILL.md 是否存在、frontmatter 是否合法、必填字段是否完整
2. **代码质量扫描**：静态分析工具检查代码风格、潜在 bug、复杂度，不实际执行代码
3. **安全漏洞检测**：检查命令注入风险、路径穿越风险、敏感信息硬编码，标注风险等级（高/中/低）
4. **依赖链扫描**：递归检查依赖链中的已知漏洞，类似 `npm audit` 机制

### CLI 使用指南
```bash
# 测试兼容发现端点
curl http://localhost:8080/.well-known/clawhub.json

# 搜索技能
npx clawhub search ifly

# 查看详情
npx clawhub inspect ifly-pdf-image-ocr

# 直接安装（指定 registry）
npx clawhub install ifly-pdf-image-ocr --registry http://localhost:3001
```

### 技术栈
- 后端：Spring Boot 3.2.3、Java 21
- 存储：PostgreSQL + Redis
- 存储插件：本地文件系统、MinIO、AWS S3、阿里云 OSS、腾讯云 COS

### 简历写法建议
文章给出了将 SkillHub 使用经验写入简历的具体模板，定位为"企业级 AI 技能管理平台（基于 SkillHub 私有部署）"。

## 结语观点

> 科大讯飞这次开源 SkillHub，是站在了"企业级"这个细分赛道上。企业需要什么？私有部署、权限管理、审核流程、数据主权。这些需求，公有平台满足不了，但 SkillHub 满足得了。

> SkillHub 已经 1.5K Star 了，如果你也在做企业 AI 平台，建议去看一眼源码，一定能给你很多很多启发。

## 相关链接

- [原文链接](https://mp.weixin.qq.com/s/KL92y4eytpa9C5Q1FnH6Ww)
- [SkillHub GitHub](https://github.com/iflytek/skillhub)
