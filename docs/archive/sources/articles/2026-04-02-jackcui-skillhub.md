---
id: source-2026-04-02-jackcui-skillhub
type: source
status: published
date: 2026-04-02
updated: 2026-04-25
confidentiality: public
source_type: article
source_url: https://mp.weixin.qq.com/s/5RzZZWLx6c1NRtz-6ii6SQ
people: [JackCui]
tags: [external-coverage, wechat, security, skill-poisoning, enterprise]
related_docs: []
related_prs: []
related_issues: []
related_commits: []
related_sources: []
summary: JackCui 从 Claude Code 源码泄露事件和 Skill 投毒安全风险切入，引出 SkillHub 作为企业级安全管控方案，深度分析了 Skill 投毒的三种攻击模式
---

# JackCui：SkillHub，开源了！

## 基本信息

- 公众号：JackCui
- 作者：JackCui
- 发布日期：2026 年 4 月 2 日
- 地区：北京

## 文章摘要

文章以 Claude Code 源码泄露事件为背景，深入分析了 AI 工具生态中的安全风险，尤其是 **Skill 投毒**问题，并将 SkillHub 的审核机制定位为应对这一威胁的企业级解决方案。

这是所有报道中安全视角最深入的一篇，具有较高的技术参考价值。

## 核心内容

### Skill 投毒的三种攻击模式

**第一：恶意逻辑藏在自然语言里，极其隐蔽**
攻击者在 skill 的 `description` 字段中嵌入隐藏的 prompt injection 指令，LLM 在选择工具时会读取 description，恶意指令可能被当作系统级指示执行。

**第二：分发链条松散，信任体系几乎为零**
社区里大量 skill 通过 GitHub repo、论坛等方式传播，没有审核、没有签名、没有沙箱隔离，全凭社区口碑。

**第三：可以做到"延迟触发"，测试根本防不住**
前 99 次调用都正常，检测到特定条件（如 `production` 关键词、AWS credentials 文件存在）时才激活恶意行为。

### 历史类比：AI 时代的"蛮荒年代"
文章将当前 AI 工具生态类比为 2000 年前后个人电脑刚普及时的安全蛮荒期，认为行业必然会重走 PC 安全的老路，建议主动建立安全基础设施而非等待"熊猫烧香"爆发。

### SkillHub 作为解决方案
- 私有化部署，数据完全自主可控
- 内置完整审核工作流，管理员集中审核
- 把"每个人自己甄别安全性"的不可能任务，变成"少数专业人员集中审核"的可控任务

### 分层建议
- **个人开发者**：用社区 skill 时至少花两分钟看完整 prompt 定义
- **小团队**：一条命令用 Docker 跑起来，把内部常用 skill 收拢统一管理
- **企业**：认真规划命名空间、审核流程和 CI/CD 集成，纳入 DevOps 体系

## 结语观点

> 代码有 Git 仓库管，容器镜像有 Docker Hub 分发，而团队自己的 AI 技能库管理，现在才处于起步阶段。

> 我们不需要等到 AI 时代的"熊猫烧香"真正爆发了，才开始重视这件事。

## 相关链接

- [原文链接](https://mp.weixin.qq.com/s/5RzZZWLx6c1NRtz-6ii6SQ)
- [SkillHub GitHub](https://github.com/iflytek/skillhub)
