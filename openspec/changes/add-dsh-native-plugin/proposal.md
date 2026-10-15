## Why

在 DSH 内提供 SkillHub 技能的浏览、安装与本地升级卸载入口，让用户不用离开当前工具即可找到并安装可匿名获取的技能。

本期范围：匿名浏览、详情、安装、已安装列表、主动升级与精确卸载，以及对话搜索卡片、详情抽屉和显式安装。详情支持实例级可选 Tab。

## What Changes

- 当前仓库 `plugins/dsh/` 开发独立的 DSH 原生插件；不要求用户另装 SkillHub CLI。
- 在 DSH 宿主中提供一个 SkillHub 技能广场：浏览/搜索、详情子页面、安装及结果反馈；补充已安装管理 Tab 和按实例配置的版本/安全审核/评论详情 Tab。
- 连接一个指定的 SkillHub 实例，以匿名请求访问；地址是必要的来源配置，不建设账号或认证中心。
- 复用完整技能包安装、指纹、路径保护、metadata、inventory 和锁；保留宿主已有策略，不开发额外权限流程。
- 默认用户级安装；项目级仍以宿主能确认的当前项目为前提。不引入任意路径输入。
- 区分文件安装与 DSH 发现结果；同目标已装不重复覆盖，冲突明确阻断；已有可信安装可通过独立计划升级或卸载。

## Capabilities

### New Capabilities

- `dsh-registry-connection`: 单一匿名来源、开源/SaaS 公共 API 契约与兼容演进。
- `dsh-skill-consumption`: 浏览、详情、完整安装、冲突保护与发现反馈。
- `dsh-native-experience`: 技能广场、对话交互、独立安装包及发布回退要求。

### Modified Capabilities

无。已有 CLI 默认行为保持兼容。

## Compatibility

首版固定一个精确 DSH 版本，首选候选为 0.2.0-rc.2，通过 M0 后确认为支持基线。不承诺多发行线兼容；候选缺少必需能力时，先完成基线替换决策再实施。

## Impact

- 同仓库独立插件包，复用 CLI 内部消费能力；首版不建设管理 SDK。
- M0 先验证真实 Registry 的匿名浏览、详情和下载，以及 DSH 外部插件加载/安装目标。
- 需要分页或详情接口增量时先明确最小契约；不能将 Web 会话接口或路由 permitAll 当作匿名可用证明。
- 不含登录、Token、私有技能访问、角色/权限管理、自动更新、降级、技能发布、DSH 插件市场、自动重启。
- 对话搜索卡片、详情抽屉、显式安装工具与持久结果卡片纳入首期；与广场共享 Host 服务和安装核心，见 [对话交互设计](conversation-design.md)。
- 不绕过已有服务端/宿主策略；拒绝时显示限制，不弹出自建登录/审批流程。

## Design Documents

- [产品与交互](product-design.md)
- [对话设计](conversation-design.md)
- [总体架构](design.md)
- [详细实现方案](engineering-blueprint.md)
- [本地生命周期](lifecycle-design.md)
- [DSH 原生接入](dsh-integration-design.md)
