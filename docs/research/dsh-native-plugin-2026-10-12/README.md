# DSH 原生连接插件：需求澄清记录

日期：2026-10-12；2026-10-13 范围变更：用户决定首版做浏览和安装，随后补充已安装 Tab、可选版本/安全审核/评论 Tab、主动升级与精确卸载；仍不做登录和权限管理。以下 Q1–Q12 为历史讨论，不是当前交付清单；当前范围以 OpenSpec 为准。
正式需求、设计、验收和任务拆解已进入 [OpenSpec add-dsh-native-plugin](../../../openspec/changes/add-dsh-native-plugin/proposal.md)，后续行为变更只在 OpenSpec 维护，不在本文另立规格。

完整阅读入口：[需求与详细设计（含架构、流程和时序图）](../../../openspec/changes/add-dsh-native-plugin/design.md)。

## 历史确认（已被首版收敛决定部分覆盖）

- 开发安装到 DSH 中的 SkillHub 原生连接插件。
- Q1 B：对话工具、连接设置页、技能浏览与管理页。
- Q2 B：搜索、详情、安装、加载使用、已安装列表、检查更新、主动升级、卸载；本轮未选择发布能力。
- Q3 B：公共或企业自建 SkillHub，可配置地址；一次使用一个实例，支持登录后按权限访问。
- Q4 B：个人电脑或远程服务器上的单用户 DSH；多人共享服务的身份隔离不在当前选择内。
- Q5 B：在现有 SkillHub 仓库新增插件模块，独立打包发布；不另建插件仓库。
- Q6 B：支持用户级和项目级安装，默认用户级，安装时可切换。
- Q7 B：浏览器授权登录为主，API Token 配置作为备用。
- Q8 A：复用 DSH 授权机制；明确操作请求不额外重复询问，搜索推荐结果需用户选择后才能安装；本地修改或覆盖冲突单独处理。
- Q9 A：用户安装插件后即可使用，不要求额外手动安装 SkillHub CLI；必要依赖随插件处理。
- Q10 A：第一版支持 Linux、macOS、Windows 上的 DSH Web，包括本机和远程单用户运行；不承诺 DSH 桌面客户端的专门适配。
- Q11 B：展示 DSH 可发现的技能并区分来源；对可确认来源的 SkillHub 安装支持管理，其他来源只读展示。兼容识别现有 CLI 安装记录，不能确认归属的技能不直接升级或删除。
- Q12 A：用户表达查找、安装、管理技能意图时调用 SkillHub，不要求明确提及品牌；普通业务任务不额外触发技能搜索。
- 用户要求使用 Image 2 生成设计图。当前调用内置图像生成工具，其接口没有模型版本选择字段，无法据此验证具体模型版本。

## 待确认

- 底层复用已在 OpenSpec 选定：增加 CLI 内部消费入口，随插件构建打包；用户无需手动安装 CLI，具体接口尚未实施。
- 产品选择 Q1–Q12 已确认，完整需求与验收场景见上述 OpenSpec。
- 待技术核验：DSH 兼容版本、安装记录兼容与所有权识别、加载状态检测、失败处理、插件包名与发布配置。

## 仓库决策

用户选择同仓库新增模块。候选路径为 plugins/dsh/，尚未创建；它是独立的 Node/TypeScript 插件包，不是 Java Maven 模块，也不是 SkillHub 网站页面。
源码位置与发布单元互相独立：插件可以在主仓库开发并单独打包发布，DSH 通过本地包目录或发布产物安装，不需要修改 DSH 官方仓库。
此前独立仓库建议不再采用。底层 CLI 复用方案以 OpenSpec 详细设计为准；未创建远端仓库或发布软件包。

## 设计探索

概念图呈现技能浏览与管理、连接设置、对话搜索安装三种交互。
示例数据不代表真实 Registry 内容；宿主框架布局不是已验证的 DSH 截图。
图中的浏览器授权及默认用户级安装已由 Q6/Q7 确认。安装确认卡片只代表从搜索结果选择的场景，不意味着所有明确操作均需插件额外确认；实现按 Q8 A 执行。
生成图用于讨论，不是功能验收证据。

- [概念图 v1](concept-v1.png)：三种视图的展示板，不要求实际产品同时显示全部区域。
- [完整生成提示词](image-prompt.txt)：通过内置图像生成工具生成，未指定或验证底层模型版本。

## 官方事实基线

同仓库开发、插件挂载与分发流程见 [开发调研](development-research.md)。
用户指定参考插件的核对结果见 [cocofhu/skillhub 对照](reference-cocofhu.md)。

调查基线：DeepSeek Harness master 提交 d743267388641bc76f17c45ce8b4c231aed1d32c；这是源码基线，不等于已验证的 npm 发行版。

- [工具接口](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/docs/subsystems/tools.md)
- [设置能力](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/settings/settings/README.md)：支持 volatile Config 表单服务，当前没有已交付客户端自动生成完整设置页。
- [技能来源机制](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/skill/skill/README.md)：provider 可提供技能目录和内容；完整包落地及远端刷新策略需集成实现。

## 验证边界

本轮仅做需求记录和概念图。未安装 DSH 插件，未执行真实认证、安装或加载测试。
