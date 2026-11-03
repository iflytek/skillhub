# 审核技能与套装

本页面向具备审核权限的操作者，说明待审内容核查、决定提交及结果确认；作者可按职责表跟踪申请。审核决定针对具体申请及版本，不替代 Agent 执行测试、合规认证或全局提升审批。

## 前置条件与职责

| 操作 | 身份与条件 | 入口 |
| --- | --- | --- |
| 团队命名空间审核 | 该命名空间所有者（Owner）或空间管理员（Admin） | 命名空间详情的“审核”页签 |
| 跨命名空间审核 | 平台技能管理员（SKILL_ADMIN）或超级管理员（SUPER_ADMIN） | `/dashboard/reviews` |
| 跟踪本人申请 | 提交者 | “我的审核进度”；申请可读不代表可以批准 |
| 全局提升与撤销 | 提交者及有权平台审核人 | `/dashboard/promotions`，与普通版本审核分别处理 |

团队命名空间的所有者及空间管理员可审核本人提交；超级管理员可自审。其他本人提交不能仅凭平台技能管理员身份自审；全局提升的本人申请仅超级管理员可审核。普通审核决定还要求命名空间为 `ACTIVE`。规则见[审核权限策略](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewPermissionChecker.java)。

普通公开（PUBLIC）或空间成员可见（NAMESPACE_ONLY）上传进入扫描与审核流程；私有（PRIVATE）上传先保留，仍须确认发布或另行提审。新版审核期间，旧发布版可能继续可用。

## 核查内容并提交决定

![核对申请、检查证据、提交决定并重新读取结果的审核流程](/diagrams/review-decision-flow.svg)

图：申请仍为待审且具备处理权限时才可作出决定；读取旧版本不等于核对本次申请。

1. 在待审列表打开申请，核对提交者、对象、目标版本、可见范围及命名空间。GitHub 同步项另有内容修订号、来源仓库和固定 commit；安装使用的技术版本在技术信息中查看。
2. 在“技能内容”检查本次说明文档和文件树；进入页签后读取说明，选择文件后读取正文。GitHub 更新默认进入“变更对比”，首次导入和普通技能默认进入“技能内容”。对象存储读取失败时不得以空白预览判定内容合格。
3. 在“来源与安全”核对许可证依据、扫描报告和发现项。区分执行失败、风险发现和分析器未配置。普通版本扫描未结束不能批准；许可证未识别或依据缺失时，须读取具体状态及证据，不能将其解释为许可已确认。
4. 若有头像或可见范围变更，核对申请中的候选和目标；若为技能套装（Skill Suite），核对待审快照、入口技能及成员固定版本。快照未加载时批准不可用，成员获批不等于套装获批。套装定义与套装 Bundle 导入操作不是同一对象，见[技能套装](./skill-suites.html)。
5. 在“审核记录”读取历史意见，完成证据检查后提交批准或拒绝。拒绝须写明具体文件、问题和修订条件；批准意见可选。切换页签保留意见，切换申请会清空意见和未确认弹窗。本版不提供“附条件批准”状态。

| 检查结果 | 处理依据 | 后续动作 |
| --- | --- | --- |
| 当前内容、目标及证据一致 | 已检查本次申请，且无待解决问题 | 批准后重新核对任务和对象结果 |
| 文件、证据或声明存在明确问题 | 记录可定位的问题及修订条件 | 拒绝，作者按意见修订 |
| 扫描、快照或文件未就绪 | 无法获得作决定所需证据 | 先排障或等待，不将缺失结果当作合格 |

在线预览限制集中如下；超限不表示文件被删除，须按下载权限获取后核查。

| 项目 | 固定限制 | 超限表现 |
| --- | --- | --- |
| 逐行比较 | 文件超过64KiB或1000行 | 跳过逐行比较 |
| 当前正文在线加载 | 文件超过256KiB | 不在线加载正文 |
| 已删除旧文件的基线下载 | 文件超过1MiB | 审核页不能下载该旧文件 |

这些是前端/接口实现限制，不由审核页面配置；依据[审核详情](https://github.com/iflytek/skillhub/blob/main/web/src/pages/dashboard/review-detail.tsx)及[审核接口](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ReviewController.java)。`GET /api/v1/reviews/{id}/skill-detail?includeDocumentation=false` 只读元数据及说明路径，不读取对象存储正文；缺省为 `true`。

### 核查合规声明

含 `x-astron-compliance` 的版本展示合规快照和新增、删除、修改差异；`complianceSnapshot.digest` 变化表示规范化声明变化。必须将声明与实际能力、控制项、证据路径和外部材料对应核查，不能只看标准名称。证据不可访问、内容不匹配或删除影响下游引用时，应先记录问题并要求修订。注册中心保存结构化声明和版本快照，不替作者认证实际合规；依据见[合规元数据](./skill-publish.html)。

## 状态对象对照

任务 `PENDING` 时，版本可能仍为 `SCANNING`；不得用任务状态代替版本状态。以下四类对象分别读取。

### 审核任务状态

| 状态 | 含义 | 处理者与下一步 |
| --- | --- | --- |
| `PENDING` | 等待决定 | 有权审核人读取当前申请后处理 |
| `APPROVED` | 已批准 | 提交者核对版本或资料生效 |
| `REJECTED` | 已拒绝并保留意见 | 提交者修订后重提 |
| `SUPERSEDED` | 被后续申请替代 | 提交者及审核人查看当前申请 |

[任务枚举](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskStatus.java)没有 `WITHDRAWN`。普通技能版本撤回删除待审任务并调整版本状态，不删除已发布旧版，也不撤销已批准的全局提升，见[撤回服务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java)。

### 技能版本状态

| 状态 | 含义 | 处理者与下一步 |
| --- | --- | --- |
| `DRAFT` | 版本草稿，不是创作 API 草稿对象 | 技能所有者完成准备 |
| `SCANNING` | 扫描运行中 | 扫描服务完成后继续流程 |
| `SCAN_FAILED` | 扫描执行失败 | 部署方及技能所有者排障后重试 |
| `UPLOADED` | 已上传，尚未发布 | 所有者确认发布或提交审核 |
| `PENDING_REVIEW` | 版本等待人工决定 | 有权审核人核对本次版本 |
| `PUBLISHED` | 已发布 | 使用者按可见范围访问，执行效果另验 |
| `REJECTED` | 未获批准 | 所有者修订并重提 |
| `YANKED` | 已撤下 | 使用者选择可安装替代版本 |

依据：[版本枚举](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java)。

### 套装导入操作状态

| 状态 | 含义 | 处理者与下一步 |
| --- | --- | --- |
| `RUNNING` | 正在执行 | 导入者跟踪原操作，不重复确认 |
| `WAITING_FOR_MEMBERS` | 等待成员就绪 | 导入服务及成员审核人处理成员 |
| `BLOCKED_RETRYABLE` | 可重试阻塞 | 导入者排障后重试原操作 |
| `REPREVIEW_REQUIRED` | 原预览不可继续 | 导入者重新预览并核对差异 |
| `SUITE_DRAFT_CREATED` | 已创建套装草稿，尚未批准 | 套装所有者继续发布 |
| `CANCELLED` | 已取消，不代表全部回滚 | 导入者核对已完成成员动作 |

依据：[导入状态](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/suite/bundle/SkillSuiteBundleOperationStatus.java)；恢复步骤见[套装 Bundle 导入](./suite-bundle.html)。

### 头像审核结果

头像复用技能审核申请，但字段维度独立；机器阻止不等于人工已拒绝。

| 字段 | 取值与含义 | 处理者与下一步 |
| --- | --- | --- |
| 候选 `status` | `PENDING`待处理、`ACTIVE`使用中、`REJECTED`拒绝、`SUPERSEDED`替代、`REMOVED`移除 | 所有者及审核人核对候选与当前头像；响应名为 `candidateStatus` |
| `machineStatus` | `SKIPPED`跳过、`PENDING`待处理、`PROCESSING`执行中、`PASSED`允许、`BLOCKED`阻止、`ERROR`执行错误 | 机器服务处理；部署方排查 `ERROR` |
| `reviewStatus` | `NOT_REQUIRED`无需人工、`PENDING`待审、`APPROVED`批准、`REJECTED`拒绝、`APPEAL_PENDING`申诉待审 | 有权审核人及所有者按意见处理 |

依据：[头像候选模型](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillAvatarCandidate.java)；配置与重试见[技能头像](./skill-avatars.html)。

## 完成标准与失败处理

普通版本批准后，任务为 `APPROVED`、目标版本为 `PUBLISHED`，再按目标身份访问验证。资料申请须核对资料实际生效，不能仅查看版本号。拒绝后，普通版本为 `REJECTED` 并有修订意见；作者在当前版本入口修订重提。审核批准不代表 Agent 执行成功或安全风险已消除。

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 列表存在但无法批准 | 自审、命名空间状态或身份不满足 | 核对提交者、角色及命名空间状态 | 交由符合条件者处理；不要切换请求绕过限制 |
| 批准按钮禁用 | 扫描或头像未就绪、套装快照加载失败 | 查看扫描、候选和快照错误 | 完成依赖或重试读取；不要以缺失结果代替合格证据 |
| 文件或说明为空 | 对象读取失败或超预览上限 | 查看文件大小及预览错误，按权限下载 | 补齐可读材料后再决定；不要直接批准空白预览 |
| 提交决定报状态冲突 | 任务已被处理或替代 | 刷新记录，核对任务ID及当前状态 | 以新状态为准；不要连续重复批准或拒绝 |

## 全局撤销与治理边界

删除已提升技能被阻止时，从来源技能详情的“提升到全局”区域申请撤销。平台技能管理员或超级管理员在 `/dashboard/promotions` 的“撤销待审核”处理；本人提交受上述自审限制。撤销记录为 `APPROVED` 后，须核对活动关联已解除，再按权限删除来源技能。隐藏、归档或撤回普通审核不替代撤销。

无待审撤销申请时，有权平台操作者可在详情直接撤销并留存记录；存在待审申请则从队列处理。批准撤销会删除整条全局技能及其版本、评价和统计，来源技能保留；客户端文件无法收回，旧地址允许重新占用。若记录已批准而删除仍受阻，须核对是否再次提升，并记录对象ID、申请ID、部署版本与失败响应；不要直接修改数据库关系。流程见[撤销服务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRevocationService.java)。

举报入口为 `/dashboard/reports`，须按具体举报和现有治理权限处理；举报调查不自动批准或拒绝版本。审核任务撤回仅针对待审申请，历史意见与当前申请的可操作性须分别判断。

## 延伸阅读

- [发布与更新技能](./skill-publish.html)：准备、提交与版本管理。
- [安全扫描](./scanner.html)：解读报告及定位执行失败。
- [管理命名空间](./namespace.html)：成员角色及命名空间状态。
