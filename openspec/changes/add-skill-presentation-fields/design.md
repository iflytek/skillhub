# Design

- Parse `metadata.short-description` and `metadata.overview` from SKILL.md, with limits of 200 and 20,000 characters.
- Accept multipart `shortDescription`, `overview`, and `resetPresentation`; web values override package metadata and are stored in `parsedMetadataJson` as `shortDescriptionOverride`, `overviewOverride`, and `presentationInheritedFrom`.
- Keep the existing display-name/version snapshot model and publish/review/rollback lifecycle. Pending versions never update the Skill container summary.
- The effective short description feeds `skill.summary`; search also retains the original package description in `search_text`. Overview is detail-only and falls back to README then SKILL.md in the existing documentation reader.
- Web package preview supplies defaults without editing the uploaded archive. Generated API types remain authoritative.

## 发布表单与概述编辑

发布页采用后续确认的 A1 分栏方案：最大1200px，左侧为主表单，右侧仅放命名空间、可见范围及必要的策略说明。主表单依次为技能包、基本信息、展示介绍；头像放在展示名称左侧。手机布局将发布设置移到基本信息之后、介绍之前。移除顶部步骤条、来源胶囊、常驻使用者卡片预览及独立大块审核说明。提交操作保持在所有内容之后。

上传组件复用既有 react-dropzone、按钮与图标，不新增上传组件运行时依赖。未选包时整区支持点击与键盘选择 ZIP，文件夹选择为独立入口；选包后收起为文件信息条。选择、目录打包、读取结构、读取失败与解析完成分别表达；没有真实上传进度时不得显示虚构百分比。格式不支持和多个 ZIP 要明确拒绝，更换选择被取消或拒绝时保留有效包。解析完成不表示扫描、审核或正式发布完成。

简要描述直接编辑，概述主表单只读且在字段标题右上固定提供“编辑概述”，无需选择包内／自定义模式。未修改的内容保持包默认来源；实际修改后作为本次自定义值，替换技能包不得覆盖自定义草稿。更新时沿用已发布的自定义覆盖值，并保留原有迟到快照和跨技能切换保护。单纯打开、查看或不改值完成编辑不得把包内值转为自定义。

两个字段独立显示来源的次要说明，仅自定义字段提供“恢复包内内容”。恢复需要确认，取消保留原稿；确认后只将对应字段恢复为默认状态，采用当前包内容，下次更换包继续跟随新默认，不把旧默认文本冻结成自定义。有效简介与概述在网页版本提交前必须非空，并遵循200与20,000字符上限；不把包内可选 metadata 字段变为 CLI/API 强制参数。缺失、读取中和读取失败分别显示，错误与操作位置关联，失败不清空已有介绍。

专注编辑界面复用成熟开源编辑内核，自定义现有组件体系的工具栏与胶囊模式切换，提供可视化、Markdown源码和发布预览；源码支持单栏与分栏。预览复用详情页 Markdown 渲染组件。内容仍按 Markdown 提交，不改写包内文件。无法安全转换的语法保留原始内容，并提供源码继续编辑，不能静默丢弃。

实现选择 Vditor 的所见即所得内核，工具栏使用既有 Lucide 图标并局部适配主题；Lute 与所需语言资源通过 Vite 输出为同源静态资源，不依赖默认外部 CDN，关闭默认本地内容缓存。包预览没有概述时，以 fflate 有界读取包内文档，仅用于可选预览，不将回退正文自动提交为自定义值；本地预览预算不是上传限额，读取失败和无内容分别显示。

包预览响应的概述只表示显式 `metadata.overview`，缺失时不提前填入 SKILL.md 正文，否则会掩盖详情阅读路径的 README 优先规则。此修正不改变域对象既有正文回退方法、版本发布或审核流程，也不新增响应字段。

进入编辑时创建本地编辑副本；“完成编辑”校验长度后更新表单预览并返回，不提交审核、不声称服务端草稿已保存。“取消修改”保留进入前的表单内容，存在改动时确认丢弃。替换包、恢复默认、权限限制和现有头像更新仍沿用原有业务边界，不因视觉改造新增发布权限。

## 提交状态与恢复

原型中的提交中、已提交和版本冲突表示互斥状态，不在正式页面同时堆叠。开始提交后锁定重复动作；成功收据按实际版本状态区分扫描中、待审核、仅上传、已发布与扫描失败，不能将非已发布状态统一称为已发布或待审核。结果页提供只读查看本次版本和返回我的技能的入口，不立即跳转吞掉结果，也不替换公众仍可见的旧发布内容。

版本冲突保留文件选择、字段来源状态与自定义内容，引导更换具有新版本号的包并聚焦替换入口，不自动改包或版本号。普通失败保留表单；预检警告继续沿用现有确认流程，权限错误刷新真实权限。超时或传输中断可能无法确认结果，不自动重试写入，应先核对我的技能。错误摘要与字段内错误用于定位未完成项，避免重复 toast；普通校验失败不只通过禁用按钮表达原因。

## 详情阅读与版本导航

详情页采用“概述／技能指令／文件／版本”四个 Tab。概述面向使用者展示版本介绍；技能指令展示该版本根目录的原始 `SKILL.md`，提供渲染与源码两种阅读方式，不通过弹窗或概述上方的额外按钮承载入口。指令读取复用版本文件接口，加载、读取失败与缺少文件分别表达，失败不得回退为其他版本的正文。

版本选择放在“版本”Tab 的每条记录中。选择“查看此版本”只更新现有 URL 的 `version` 条件，保留其他导航上下文，并返回概述；浏览器前进、后退与刷新继续按照 URL 读取版本。简介、概述、技能指令、文件和安装／下载目标使用同一选中版本。历史版本显示明确提示，返回最新版使用当前已发布版本，不把待审版本误称为最新版。选择版本不创建版本、不提审、不重新发布，访问边界沿用服务端授权。

折叠状态与技能指令阅读模式按版本重置，避免旧正文或阅读状态在版本之间串用。本次交互调整不替代此前字段继承、审核展示等诊断缺陷的修复及验收。
