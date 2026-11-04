# Design

- Parse `metadata.short-description` and `metadata.overview` from SKILL.md, with limits of 200 and 20,000 characters.
- Accept multipart `shortDescription`, `overview`, and `resetPresentation`; web values override package metadata and are stored in `parsedMetadataJson` as `shortDescriptionOverride`, `overviewOverride`, and `presentationInheritedFrom`.
- Keep the existing display-name/version snapshot model and publish/review/rollback lifecycle. Pending versions never update the Skill container summary.
- The effective short description feeds `skill.summary`; search also retains the original package description in `search_text`. Overview is detail-only and falls back to README then SKILL.md in the existing documentation reader.
- Web package preview supplies defaults without editing the uploaded archive. Generated API types remain authoritative.

## 详情阅读与版本导航

详情页采用“概述／技能指令／文件／版本”四个 Tab。概述面向使用者展示版本介绍；技能指令展示该版本根目录的原始 `SKILL.md`，提供渲染与源码两种阅读方式，不通过弹窗或概述上方的额外按钮承载入口。指令读取复用版本文件接口，加载、读取失败与缺少文件分别表达，失败不得回退为其他版本的正文。

版本选择放在“版本”Tab 的每条记录中。选择“查看此版本”只更新现有 URL 的 `version` 条件，保留其他导航上下文，并返回概述；浏览器前进、后退与刷新继续按照 URL 读取版本。简介、概述、技能指令、文件和安装／下载目标使用同一选中版本。历史版本显示明确提示，返回最新版使用当前已发布版本，不把待审版本误称为最新版。选择版本不创建版本、不提审、不重新发布，访问边界沿用服务端授权。

折叠状态与技能指令阅读模式按版本重置，避免旧正文或阅读状态在版本之间串用。本次交互调整不替代此前字段继承、审核展示等诊断缺陷的修复及验收。
