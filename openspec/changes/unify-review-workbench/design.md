# 技术与交付设计

## 权威设计来源

用户确认的Figma：列表筛选以v6原位尺寸修正稿为准，详情/右侧/治理以v4统一token稿为准。实现与验收对照这些明确尺寸和状态，不在实现时重新选择风格。

- 列表：https://www.figma.com/design/ixPCzrfgZ8V9lX6mWDWBCx?node-id=79-19170
- 状态菜单：https://www.figma.com/design/ixPCzrfgZ8V9lX6mWDWBCx?node-id=79-19397
- 详情：https://www.figma.com/design/ixPCzrfgZ8V9lX6mWDWBCx?node-id=76-7438
- 范围详情：https://www.figma.com/design/ixPCzrfgZ8V9lX6mWDWBCx?node-id=76-7945
- 组件：https://www.figma.com/design/ixPCzrfgZ8V9lX6mWDWBCx?node-id=77-18418

## 读模型与权限

扩展GET /reviews，保留旧参数兼容，增加q/reviewKind/originType及status=ALL。reviewKind取FIRST_PUBLISH/VERSION_UPDATE/SUITE_PUBLISH/VISIBILITY_CHANGE/AVATAR_CHANGE；ALL为筛选值而非领域状态。originType取ALL/USER/GITHUB_SYNC。关键词、授权范围、状态和事项在服务端同一查询内过滤后排序分页与计数，不能前端按当前页过滤或枚举全部数据。USER_ADMIN不得自动获取技能审核权限；TEAM Owner/Admin仅授权命名空间集合。

详情沿用任务授权，新增presentation和comparison读投影；复用当前版本展示解析器，绑定审核任务版本。comparison仅比较真实已发布基线，禁止任取另一待审版本；元数据申请无新版本比较。普通文件只列ADDED/MODIFIED/REMOVED/UNCHANGED，不新增逐行diff实现。原始包和自定义展示内容独立读取。

## 前端

查询草稿与已应用条件分离；点击查询统一应用、回第一页并写URL。重置恢复PENDING与其余空/全部范围并查询。列表、详情返回和浏览器前进后退保留有效范围、条件、排序与页码。旧sync队列链接映射来源条件；命名空间旧入口进入同一列表预选slug。保留独立资料审核，不把资料头像当技能头像。

详情顶部仅任务标题与事项，主区有效简介/概述及原文件，右侧标准Card集中摘要和证据。按任务决定Tab，包内容加载失败不能用已发布旧内容冒充；扫描、头像、许可证和并发决定仍遵守原服务条件。已有任务已处理后只读。

## 取舍

只做样式覆盖会保留重复入口与缺失待审内容，因此同时收敛读模型。前端聚合各命名空间分页会导致排序与计数不正确，因此采用服务端授权过滤。新增完整通用工作流引擎或普通zip逐行diff均超出当前需求。

## 验证

覆盖筛选查询/重置/URL与分页、平台及TEAM权限隔离、用户资料权限、原文与自定义内容、基线及首次无基线、元数据/套装/GitHub任务、失败重试/已处理/并发、菜单尺寸/键盘/窄屏。使用真实本地Compose、S3依赖和浏览器完成端到端验收；测试矩阵与结果以交付报告链接回写，不把旧截图当新实现验收。
