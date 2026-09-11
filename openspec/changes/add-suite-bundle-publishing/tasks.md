## 1. 协议与操作基础

- [x] 1.1 `[REQ-SBP-02, REQ-SBP-06, REQ-SBP-07]` 确定同时支持创建/更新模式、“携带包成员”和“精确引用成员”的 Manifest，包括唯一根 Manifest、成员相对目录、新 Skill 必填可见性、已有 Skill 可见性继承、重复字段优先级和冲突规则；通过 fixture 验证新 Skill、自有已有 Skill、非本人所有的公开引用、重新固定版本、移除、歧义定义、危险路径和非法可见性。
- [x] 1.2 `[REQ-SBP-01, REQ-SBP-08, REQ-SBP-13, REQ-SBP-14]` 新增有期限且不占位的 PreviewSession，以及确认后才创建的 ExecutionOperation、目标 Suite 坐标/版本占用、成员结果、幂等字段和索引；通过 PostgreSQL 集成测试验证并发创建/更新唯一性、重启恢复和回滚不会修改现有生命周期数据。
- [ ] 1.3 `[REQ-SBP-08, REQ-SBP-13, REQ-SBP-17, REQ-SBP-18]` 新增预览、确认、状态、重试和取消的 API 契约，明确状态读取脱敏和操作重新授权；运行 `make generate-api` 和 `scripts/check-openapi-generated.sh` 验证生成类型无漂移。

## 2. Bundle 预览

- [x] 2.1 `[REQ-SBP-05, REQ-SBP-06, REQ-SBP-07]` 实现流式两层解析：先验证唯一 Manifest、目录不重叠、文件唯一归属、逻辑 Skill 唯一和未声明内容，再把每个目录去前缀后交给现有 SkillPackageValidator、元数据与合规校验；通过 fixture 覆盖缺失/嵌套 `SKILL.md`、重复规范化路径/坐标、目录嵌套、未声明目录、元数据冲突和单成员限制。
- [x] 2.2 `[REQ-SBP-04, REQ-SBP-15]` 复用现有 fingerprint，并以临时对象定位和摘要连接预览与发布，保证每个文件最多解压和计算一次；通过测试证明 ZIP 元数据、合法外层目录以及 ZIP/目录选择不影响成员边界和比较结果，最大合法 Bundle 不常驻内存。
- [x] 2.3 `[REQ-SBP-01, REQ-SBP-02, REQ-SBP-08, REQ-SBP-10, REQ-SBP-11, REQ-SBP-14]` 批量解析基准成员、精确引用、Suite 创建/管理权限、新 Skill 创建权限、已有 Skill 发布权限、最终可见性/受众兼容、warning、已有待审/未发布版本和目标坐标/版本冲突；通过测试验证 100 个成员时查询次数仍受控，且不会自动确认 warning、撤回或替换现有版本。
- [x] 2.4 `[REQ-SBP-03, REQ-SBP-09]` 分别生成成员关系变化和发布动作，且不产生生命周期副作用；通过测试确认预览不占用目标坐标，也不会创建 Skill、SkillVersion、Suite、SuiteVersion、扫描、审核、Label 或 Tag 变更。
- [x] 2.5 `[REQ-SBP-08]` 将带有效期的预览与操作者、创建/更新模式、目标坐标、归档摘要、精确引用、已解析包版本和计划绑定；通过测试确认过期或相关状态变化后必须重新预览，多个预览可以并存且只有确认事务获取占用。
- [ ] 2.6 `[REQ-SBP-05, REQ-SBP-06, REQ-SBP-07, REQ-SBP-15, REQ-SBP-17]` 实现 ZIP 上传和受支持浏览器的根目录选择，保证两者生成同一协议并只上传一次；通过大包、取消、重试和兼容性测试验证内存与网络消耗受控。
- [ ] 2.7 `[REQ-SBP-03, REQ-SBP-17, REQ-SBP-18]` 实现 Web 差异预览，按成员目录展示格式错误、warning、最终可见性和审核/PRIVATE 发布路径，分别展示新增/更新/不变/移除和创建 Skill/创建版本/复用/引用，突出高风险移除、已有待审版本、加载、过期和无变化状态；通过 Vitest 和浏览器用例验证。

## 3. Bundle 发布编排

- [x] 3.1 `[REQ-SBP-08, REQ-SBP-14]` 实现幂等确认事务，并在任何成员写入前原子获取目标 Suite 坐标/版本占用；通过并发创建、并发更新和响应丢失测试确认最多创建一个操作且不重复创建包版本。
- [ ] 3.2 `[REQ-SBP-10, REQ-SBP-11]` 将有 Namespace 创建权限的新 Skill 和有独立发布权限的变化包送入非破坏性的现有 Skill 规则/生命周期边界，并在每次写入前重新授权；验证 Suite 权限不能扩大 Skill 权限，不会撤回或替换已有版本，纯引用和未变化成员不会产生发布副作用。
- [ ] 3.3 `[REQ-SBP-12, REQ-SBP-13]` 记录包和引用结果，并按规范状态矩阵通过领域事件与有界恢复任务完成协调；覆盖 PRIVATE `UPLOADED`、`SCANNING`、`SCAN_FAILED`、`PENDING_REVIEW`、`REJECTED`、外部撤回/删除/下架、`BLOCKED_RETRYABLE` 与 `REPREVIEW_REQUIRED`，验证占用随终态原子释放，重复、延迟、丢失和乱序时按原 ID 收敛或明确要求重新预览。
- [ ] 3.4 `[REQ-SBP-12]` 仅在包发布成功、引用最终有效且最终权限/状态检查通过时，原子创建新 Suite 及首个 DRAFT，或为已有 Suite 创建一个 SuiteVersion DRAFT；验证权限撤销、Namespace 冻结、待审核、被拒绝、失败包和失效引用不会产生空 Suite 或部分草稿。
- [ ] 3.5 `[REQ-SBP-13, REQ-SBP-15]` 实现带当前授权和响应脱敏的状态读取、原 ID 重试、非破坏性取消、终态占用释放、PreviewSession/暂存对象过期和限定范围补偿清理；验证等待审核的 ExecutionOperation 不随预览过期，已有 SkillVersion 和引用保持不变，清理失败仍有可操作记录。
- [ ] 3.6 `[REQ-SBP-17, REQ-SBP-18]` 在“创建 Suite”和“创建新版本”入口提供手工组合/本地导入选择，并实现准确列出副作用、最终可见性、发布路径和逐成员 warning 确认的确认页及可恢复进度页；展示包/引用状态、Skill 审核链接、阻塞原因、重试/取消和生成的 Suite 草稿，通过正常、等待、可重试阻塞、必须重预览、取消、权限变化、刷新和重新登录用例验证。

## 4. 任意成员的所属 Suite

- [ ] 4.1 `[REQ-SMD-01]` 保持技能市场和套件专区的路由、API、筛选和卡片相互独立；验证现有 `/search` 和 `/suites` 测试不变。
- [ ] 4.2 `[REQ-SMD-02, REQ-SMD-03, REQ-SMD-04]` 将 Entry-only 查询改为基于 Suite 当前 `latestVersionId` 的集合式、隐私过滤任意成员查询，返回 Entry 标记和有界兄弟成员摘要；验证历史已移除成员、PUBLIC、NAMESPACE_ONLY、PRIVATE、隐藏、归档、删除和无权访问场景，且无信息泄露或 N+1 查询。
- [ ] 4.3 `[REQ-SMD-02, REQ-SMD-03]` 更新 Skill 详情中的所属 Suite 和可见兄弟成员展示；通过浏览器验证加载、空态、历史移除、降级、受限、Entry/非 Entry、多 Suite 和响应式状态。

## 5. Suite 展示信息与 Label

- [ ] 5.1 `[REQ-SMT-01, REQ-SMT-02, REQ-SMT-03]` 在 Suite 提交、直接发布和批准边界要求非空摘要和概述，同时允许不完整 DRAFT 和历史发布数据读取；通过生命周期测试覆盖所有边界和兼容场景。
- [ ] 5.2 `[REQ-SMT-04]` Bundle 从 Manifest 显式值或当前非空值解析展示内容；验证创建/更新模式展示信息不完整时，在发布包副作用发生前阻止确认。
- [ ] 5.3 `[REQ-SMT-05, REQ-SMT-06, REQ-SMT-08, REQ-SMT-09]` 新增 Suite-to-Label 关联并复用 Label 定义、本地化和权限类型；通过持久化与权限测试覆盖普通、特权、重复、数量上限和删除行为，确保不修改成员或 Skill Tag。
- [ ] 5.4 `[REQ-SMT-05, REQ-SMT-06, REQ-SMT-07, REQ-SMT-09]` 新增 Suite Label 查询、变更和筛选 API 并重新生成 OpenAPI 类型；验证批量投影、Suite 专属审计和搜索筛选不会使用成员 Skill 关联。
- [ ] 5.5 `[REQ-SMT-01, REQ-SMT-02, REQ-SMT-03, REQ-SMT-05, REQ-SMT-06, REQ-SMT-07]` 在 Suite 编辑、详情、卡片和套件专区增加必填展示信息与 Suite Label；通过测试验证草稿校验、历史缺失信息、国际化标签、筛选、权限和响应式状态。
- [ ] 5.6 `[REQ-SMT-02, REQ-SMT-03, REQ-SMT-10]` 为 Suite 概述编辑器增加结构化提示，并在详情页增加 Entry Skill 固定版本说明的独立按需展开区域；通过测试验证精确版本、延迟加载、读取权限、安全 Markdown、无权访问和加载失败不会替代或泄露概述内容。

## 6. 兼容性、安全、性能与交付验证

- [ ] 6.1 `[REQ-SBP-16, REQ-SMD-01, REQ-SMT-03, REQ-SMT-08]` 增加单 Skill 发布、Suite 草稿/审核/安装、技能与套件独立发现、单 Skill Label/Tag API 和旧客户端回归覆盖，验证现有契约保持兼容。
- [ ] 6.2 `[REQ-SBP-03, REQ-SBP-04, REQ-SBP-05, REQ-SBP-06, REQ-SBP-07, REQ-SBP-08, REQ-SBP-09, REQ-SBP-10, REQ-SBP-11, REQ-SBP-12, REQ-SBP-13, REQ-SBP-14, REQ-SBP-15, REQ-SMD-02, REQ-SMD-03, REQ-SMD-04, REQ-SMT-05, REQ-SMT-06, REQ-SMT-07, REQ-SMT-08, REQ-SMT-09, REQ-SMT-10]` 增加目录/ZIP 等价、归档限制、ZIP traversal/bomb、fingerprint 等价、100 成员批量解析、状态轮询、新 Skill 坐标冲突、重复逻辑 Skill、跨所有者引用、多阶段权限撤销、成员状态矩阵、Label 权限、Suite Label 批量读取和私有信息保护测试，并以确定性断言验证边界。
- [ ] 6.3 `[REQ-SBP-01, REQ-SBP-02, REQ-SBP-03, REQ-SBP-04, REQ-SBP-05, REQ-SBP-06, REQ-SBP-07, REQ-SBP-08, REQ-SBP-09, REQ-SBP-10, REQ-SBP-11, REQ-SBP-12, REQ-SBP-13, REQ-SBP-14, REQ-SBP-15, REQ-SBP-16, REQ-SBP-17, REQ-SBP-18, REQ-SMD-01, REQ-SMD-02, REQ-SMD-03, REQ-SMD-04, REQ-SMT-01, REQ-SMT-02, REQ-SMT-03, REQ-SMT-04, REQ-SMT-05, REQ-SMT-06, REQ-SMT-07, REQ-SMT-08, REQ-SMT-09, REQ-SMT-10]` 运行后端、前端、CLI、OpenAPI 漂移检查和 `openspec validate add-suite-bundle-publishing --strict`，按精确 feature SHA 记录结果。
- [ ] 6.4 `[REQ-SBP-01, REQ-SBP-03, REQ-SBP-08, REQ-SBP-10, REQ-SBP-11, REQ-SBP-12, REQ-SBP-13, REQ-SBP-14, REQ-SBP-15, REQ-SBP-17, REQ-SBP-18, REQ-SMD-02, REQ-SMD-03, REQ-SMD-04, REQ-SMT-01, REQ-SMT-04, REQ-SMT-05, REQ-SMT-06, REQ-SMT-07, REQ-SMT-09, REQ-SMT-10]` 构建精确 SHA 的 Server/Web 镜像并运行带认证的 release Compose 场景，覆盖创建 Suite、更新 Suite、新建 Skill、已有 Skill 发布/审核、非本人所有引用、协调恢复、Suite 草稿/审核、展示信息校验、Entry Skill 说明权限、Suite Label 和反向引用，同时使用本地 S3 与 Scanner 路径。
- [ ] 6.5 `[REQ-SBP-01..18, REQ-SMD-01..04, REQ-SMT-01..10]` 完成独立实现评审、测试设计评审、CI/隐私/就绪检查、人工 Web 复测说明和中文合并报告，再申请合并授权；最终追踪矩阵必须展开每个完整 REQ ID，不得只保留范围缩写。
