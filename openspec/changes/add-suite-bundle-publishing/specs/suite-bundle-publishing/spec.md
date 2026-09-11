## Purpose

让维护者通过一个经过安全校验的 ZIP 或根目录创建或更新 Suite，同时保持 Skill 独立所有权、
独立审核以及 Suite 精确版本快照不可变。

## ADDED Requirements

### Requirement: REQ-SBP-01 Bundle 导入 SHALL 支持创建和更新 Suite

系统 SHALL 使用同一套预览、确认和进度协议支持创建 Suite 或为已有 Suite 创建新版本。创建模式
要求目标 Namespace 可写且操作者具备现有 Suite 创建权限；更新模式要求 Suite 为 ACTIVE 且操作者
具备创建新版本权限。确认前不得创建 Suite 或 SuiteVersion。

#### Scenario: 创建新 Suite
- **WHEN** 有权限的操作者为尚不存在的 Suite 坐标提交合法 Bundle
- **THEN** 系统在没有基准 SuiteVersion 的情况下预览全部目标成员
- **AND** 确认及成员发布成功前不创建空 Suite

#### Scenario: 刷新已有 Suite
- **WHEN** 有权限的操作者为 ACTIVE Suite 上传合法 Bundle
- **THEN** 系统以用户明确选择的 SuiteVersion 为基准分析 Bundle

#### Scenario: 创建目标 Suite 坐标已存在
- **WHEN** 创建模式指向已经存在或被其他非终态操作占用的 Suite 坐标
- **THEN** 预览或确认以可操作冲突拒绝请求
- **AND** 不覆盖现有 Suite

#### Scenario: 操作者不能管理 Suite
- **WHEN** 当前用户没有 Suite 管理权限却上传 Bundle
- **THEN** 系统拒绝请求，且不泄露其无权访问的成员元数据

### Requirement: REQ-SBP-02 Bundle Manifest SHALL 区分携带包成员和精确引用成员

Manifest SHALL 描述完整目标成员顺序和 Entry Skill。每个成员 SHALL 只能是以下一种形式：
操作者有权创建或发布的 Skill 包，或者一个合规的精确 PUBLISHED SkillVersion 引用。纯引用成员
SHALL NOT 要求操作者拥有该 Skill，也不要求提供包目录。创建模式把全部成员视为新增；更新模式
只有基准成员未出现在完整 Manifest 中时，才视为移除。

#### Scenario: 从文件夹创建新 Skill
- **WHEN** 携带包成员指向尚不存在的 Skill 坐标
- **AND** 操作者在目标 Namespace 具备 Skill 创建权限且 Namespace 可写
- **THEN** 预览把发布动作标记为 `CREATE_SKILL`
- **AND** 只有确认后才能创建 Skill 及其首个 SkillVersion

#### Scenario: 无权在目标 Namespace 创建 Skill
- **WHEN** 携带包成员指向不存在的 Skill，但操作者没有对应 Namespace 的创建权限
- **THEN** 预览阻塞整个计划
- **AND** 不创建 Skill、Suite、版本、扫描或审核任务

#### Scenario: 更新有权发布的已有 Skill
- **WHEN** 携带包成员指向已有 Skill 且内容变化，并且操作者具备独立发布权限
- **THEN** 预览把发布动作标记为 `CREATE_VERSION`

#### Scenario: 新 Skill 坐标与不可管理 Skill 冲突
- **WHEN** 携带包坐标已经属于操作者无权发布的 Skill
- **THEN** 预览拒绝携带包，而不是将其作为新 Skill 或覆盖现有内容

#### Scenario: 保留非本人所有的公开成员
- **WHEN** Manifest 以纯引用形式保留其他用户拥有的合规精确 PUBLIC SkillVersion
- **THEN** 预览按照现有 Suite 可见性规则接受该引用
- **AND** 不复制、发布、扫描、审核、设置 Label/Tag 或以其他方式修改该 Skill

#### Scenario: 添加已有精确引用
- **WHEN** 操作者不提供包，仅添加一个合规精确 PUBLISHED SkillVersion
- **THEN** 预览将成员关系标记为 `ADDED`，发布动作标记为 `REFERENCE_VERSION`
- **AND** 最终 Suite 草稿可以引用它，但不改变其所有权或生命周期

#### Scenario: 重新固定引用成员版本
- **WHEN** Manifest 将引用成员从一个合规 PUBLISHED SkillVersion 改为另一个
- **THEN** 预览将成员关系标记为 `UPDATED`，发布动作标记为 `REFERENCE_VERSION`
- **AND** 不发布新的 SkillVersion

#### Scenario: 为非本人所有 Skill 提供包
- **WHEN** 操作者可以引用某个 Skill，但没有独立发布权限
- **AND** Manifest 为该 Skill 提供包内容
- **THEN** 预览拒绝该携带包成员，并返回不泄露隐私的可操作原因

#### Scenario: 移除当前成员
- **WHEN** 当前成员未出现在完整 Manifest 中
- **THEN** 预览将其标记为 `REMOVED`
- **AND** 在后续 SuiteVersion 正式发布前，现有已发布 Suite 保持不变

### Requirement: REQ-SBP-03 Bundle 预览 SHALL 完整且无业务副作用

确认前，预览 SHALL 返回完整目标 Suite 快照，并区分携带包成员和纯引用成员。预览 SHALL NOT
创建 Skill、SkillVersion、Suite、SuiteVersion、扫描、审核任务、Label 或 Tag 变更。临时归档、
预览记录和目标坐标占用不属于生命周期对象，并且 SHALL 受有效期和清理策略约束。

#### Scenario: 预览混合变更
- **WHEN** 合法 Bundle 同时包含未变化/变化的包、新增/更新/未变化的引用以及被移除成员
- **THEN** 预览分别返回成员关系变化 `ADDED/UPDATED/UNCHANGED/REMOVED`、发布动作
  `CREATE_SKILL/CREATE_VERSION/REUSE_VERSION/REFERENCE_VERSION/NONE`、当前/目标版本、顺序、
  Entry 标记、警告和阻塞原因
- **AND** 不产生发布或元数据副作用

#### Scenario: 预览不存在有效变化
- **WHEN** 包 fingerprint、精确引用、顺序、Entry Skill、可见性和 Suite 元数据均与当前快照一致
- **THEN** 预览报告没有有效变化
- **AND** 确认不能创建冗余 SkillVersion 或 SuiteVersion

### Requirement: REQ-SBP-04 携带包成员差异 SHALL 使用规范化 Skill fingerprint

系统 SHALL 使用普通 Skill fingerprint 相同的规范化路径和文件内容 hash 比较携带包成员。ZIP
顺序、压缩元数据、压缩级别和外层目录 SHALL NOT 导致版本变化。纯引用成员 SHALL 比较精确
SkillVersion 身份，不下载或计算包内容。

#### Scenario: 重新打包但内容未变化
- **WHEN** 携带包成员的规范化路径和文件内容相同，只改变 ZIP 元数据或归档顺序
- **THEN** 预览将发布动作标记为 `REUSE_VERSION`
- **AND** 继续使用当前精确 SkillVersion

#### Scenario: 自有成员内容发生变化
- **WHEN** 至少一个规范化路径或文件内容 hash 发生变化
- **THEN** 预览将发布动作标记为 `CREATE_VERSION`

#### Scenario: 保留相同精确引用
- **WHEN** 纯引用成员指向与当前快照相同的 SkillVersion
- **THEN** 预览将成员关系标记为 `UNCHANGED`，发布动作标记为 `REFERENCE_VERSION`
- **AND** 不下载引用包，也不计算引用 fingerprint

### Requirement: REQ-SBP-05 Bundle 归档 SHALL 在发布前完成安全校验

系统 SHALL 对每个携带包成员执行现有 Skill 校验，并执行配置的归档总量、解压膨胀、成员数、
文件数和单文件大小限制。危险路径、链接、重复规范化成员、同时声明包和引用、非法 Manifest 或
非法包 SHALL 在产生副作用前拒绝整个预览。

#### Scenario: 一个携带包成员不合法
- **WHEN** 任一包未通过现有 Skill 包校验或预发布校验
- **THEN** 预览返回对应成员和可操作原因
- **AND** 不发布任何成员

#### Scenario: 归档尝试路径穿越或过度解压
- **WHEN** 归档包含危险路径、不支持的链接或超过配置的安全限制
- **THEN** 系统在写入 Skill/Suite 生命周期数据前拒绝归档

#### Scenario: 一个成员同时声明包和引用
- **WHEN** Manifest 中同一个成员同时使用两种形式
- **THEN** 预览拒绝 Manifest，而不是猜测发布意图

### Requirement: REQ-SBP-06 Bundle 外层结构 SHALL 唯一映射每个 Skill 文件夹

Bundle 根 SHALL 包含且仅包含一个可识别的 Manifest。每个携带包成员 SHALL 在 Manifest 中声明一个
规范化相对目录，该目录必须唯一、不得与其他成员目录重叠或互相嵌套。每个声明目录必须且只能把
一个位于该目录根部的 `SKILL.md` 识别为成员入口。除明确允许忽略的操作系统元数据外，未归属于
Manifest 或任何声明成员目录的文件 SHALL 作为格式错误处理。

#### Scenario: 一个合法的多 Skill Bundle
- **WHEN** Manifest 声明三个互不重叠的成员目录，且每个目录根部各有一个 `SKILL.md`
- **THEN** 解析器生成三个彼此隔离的成员包
- **AND** 任一成员的文件不会进入另一个成员的校验或 fingerprint

#### Scenario: 成员目录缺少根部 SKILL.md
- **WHEN** 声明目录没有 `SKILL.md`，或者只在更深层级出现 `SKILL.md`
- **THEN** 预览把该成员报告为阻塞性格式错误
- **AND** 不通过猜测目录结构自动选择入口

#### Scenario: 成员目录重叠或嵌套
- **WHEN** 两个 Manifest 成员指向相同目录，或者一个成员目录位于另一个成员目录内
- **THEN** 系统拒绝整个 Bundle
- **AND** 不允许同一文件归属于多个 Skill

#### Scenario: 存在未声明的 Skill 文件夹
- **WHEN** 归档中出现包含 `SKILL.md`、但未被 Manifest 声明的目录
- **THEN** 系统报告该目录未被声明并阻止确认

#### Scenario: ZIP 与目录选择产生相同内容
- **WHEN** 用户分别通过 ZIP 和浏览器根目录选择提交相同的规范化文件树
- **THEN** 服务端得到相同的 Manifest、成员边界和 fingerprint 结果

### Requirement: REQ-SBP-07 每个成员包 SHALL 独立通过现有 Skill 协议校验

解析外层结构后，系统 SHALL 把每个成员目录去除自身前缀，并作为以 `SKILL.md` 为根的普通 Skill
包交给现有 SkillPackageValidator、元数据解析和合规校验。路径、扩展名、内容签名、文件数量、
单文件大小、总大小、YAML 安全限制和必填字段 SHALL 复用普通 Skill 发布的同一策略及错误/警告
等级，不得为 Bundle 放宽。Manifest 与 `SKILL.md` 重复表达的信息 SHALL 按协议定义的唯一优先级
解析；互相矛盾时必须阻止确认。

#### Scenario: 一个成员 SKILL.md frontmatter 无效
- **WHEN** 任一成员缺少必填字段、YAML 语法错误或超过解析安全限制
- **THEN** 预览在对应成员下返回定位明确的错误
- **AND** 整个 Bundle 不能确认

#### Scenario: 两个路径规范化后冲突
- **WHEN** 同一成员内两个原始路径规范化或规范化 `SKILL.md` 大小写后得到相同路径
- **THEN** 成员校验以重复路径错误失败

#### Scenario: Manifest 与 SKILL.md 元数据冲突
- **WHEN** Manifest 和成员 `SKILL.md` 对协议规定必须一致的坐标或版本信息给出不同值
- **THEN** 预览展示冲突字段并阻止确认
- **AND** 服务端不静默选择其中一个值

#### Scenario: 一个成员超过普通 Skill 包限制
- **WHEN** 单个成员的文件数、单文件大小或总大小超过普通 Skill 发布限制
- **THEN** 即使整个 Bundle 未超过总限制，该成员仍然校验失败

#### Scenario: 所有成员格式校验通过
- **WHEN** 外层结构和每个成员包均通过阻塞性格式校验
- **THEN** 预览才继续执行 fingerprint、权限和目标版本分析
- **AND** 普通安全扫描仍在用户确认后的现有 Skill 生命周期中独立执行

### Requirement: REQ-SBP-08 Bundle 确认 SHALL 绑定已检查的预览并重新授权

确认 SHALL 使用带有效期的不透明预览标识，并绑定操作者、创建/更新模式、目标 Namespace/Suite
坐标、归档摘要、目标 Suite 版本和完整成员计划。确认和重试 SHALL 保持预览阶段解析出的包版本
和精确引用 ID，并满足幂等性。确认时 SHALL 重新检查 Suite、Namespace、Skill 和引用权限。

#### Scenario: 确认已检查计划
- **WHEN** 同一有权限操作者确认仍有效且相关状态未变化的预览
- **THEN** 系统针对该精确计划启动一个持久化 Bundle 操作
- **AND** 返回操作 ID

#### Scenario: 预览过期或相关状态变化
- **WHEN** 确认使用过期预览，或者 Suite、权限、包、引用相关状态已经变化
- **THEN** 不产生新的发布副作用
- **AND** 要求重新预览

#### Scenario: 确认前权限被撤销
- **WHEN** 操作者在预览后失去 Suite 创建/管理、Skill 创建/发布或引用读取权限
- **THEN** 确认拒绝对应计划且不启动成员发布
- **AND** 错误不泄露操作者已经无权读取的资源元数据

#### Scenario: 响应丢失后重试
- **WHEN** 操作者使用相同幂等标识重试确认
- **THEN** 系统返回已有操作
- **AND** 不重复创建成员版本或审核任务

### Requirement: REQ-SBP-09 Bundle 预览 SHALL 解析可发布的 Suite 展示信息

预览 SHALL 展示目标 SuiteVersion 最终使用的 summary 和 overview。更新模式可以使用 Manifest
提供的值，也可以继承基准 SuiteVersion 的非空值；创建模式必须由 Manifest 提供。任一最终值为空
时 SHALL 阻止确认。Bundle 处理 SHALL NOT
增加、删除或向成员扩散 Suite/Skill Label 与 Tag。

#### Scenario: 继承当前完整展示信息
- **WHEN** Manifest 未填写 summary 和 overview，且当前 SuiteVersion 两者均非空
- **THEN** 预览展示继承后的最终值
- **AND** 最终 DRAFT 保留这些值

#### Scenario: 展示信息仍不完整
- **WHEN** Manifest 和当前 SuiteVersion 无法得到非空 summary 或 overview
- **THEN** 预览返回可操作的阻塞错误
- **AND** 在发布包内容前阻止确认

#### Scenario: Bundle 只修改成员组成
- **WHEN** 已确认 Bundle 更新包或引用
- **THEN** Suite Label 及全部成员 Skill Label/Tag 保持不变

### Requirement: REQ-SBP-10 携带包成员 SHALL 保持独立 Skill 权限和生命周期

只有操作者在对应 Namespace 具备创建权限的新 Skill，或已经具备独立发布权限的已有 Skill，才能
进入普通所有权、版本、校验、存储、扫描、可见性、审核和审计流程。未变化的包和全部纯引用成员
SHALL 不产生 Skill 发布副作用。服务端 SHALL 在每次实际创建 Skill 或 SkillVersion 前重新授权。

#### Scenario: 确认后创建新 Skill
- **WHEN** 已确认计划包含通过预览的新 Skill 包，且写入时权限仍然有效
- **THEN** 系统通过现有 Skill 创建和首版发布流程处理该成员
- **AND** 操作记录新 Skill、SkillVersion 及其状态

#### Scenario: 发布有权限的变化包
- **WHEN** 已确认计划包含操作者有权发布的变化包
- **THEN** 独立 SkillVersion 进入现有扫描和审核流程
- **AND** 操作记录每个版本及其状态

#### Scenario: 复用未变化包
- **WHEN** 携带包成员的发布动作被判断为 `REUSE_VERSION`
- **THEN** 继续使用当前精确 PUBLISHED SkillVersion
- **AND** 不复制对象、不扫描、不审核、不创建版本

#### Scenario: Suite 管理权限不授予 Skill 发布权限
- **WHEN** 操作者可以管理 Suite，但不能发布某个携带包 Skill
- **THEN** 该包不能进入发布流程
- **AND** Suite 所有权不会扩大 Skill 权限

#### Scenario: 异步写入前 Skill 权限被撤销
- **WHEN** 操作者在确认后、创建成员版本前失去对应权限
- **THEN** 该成员进入 `BLOCKED` 且不创建新版本
- **AND** 其他已创建成员保持独立生命周期

### Requirement: REQ-SBP-11 纯引用成员 SHALL 保持独立所有权和生命周期

系统 SHALL 只校验引用身份、当前操作者可见性、目标 Suite 受众兼容性和可安装性。系统 SHALL NOT
修改引用成员的包、版本、所有者、Label、Tag、审核、扫描或生命周期。

#### Scenario: 引用其他所有者的公开 Skill
- **WHEN** Suite 维护者选择其他用户拥有的合规精确 PUBLIC SkillVersion
- **THEN** 系统不要求管理权限即可接受该引用

#### Scenario: 操作者个人可见但目标受众不兼容
- **WHEN** 操作者可以读取精确 SkillVersion，但 Suite 目标受众不能读取
- **THEN** 系统按照现有 Suite 可见性规则拒绝引用

### Requirement: REQ-SBP-12 Suite 草稿创建 SHALL 等待包发布成功和引用最终有效

只有全部变化包版本均为 PUBLISHED，且全部未变化/纯引用成员仍然有效后，系统 SHALL 创建且仅创建
一个 DRAFT SuiteVersion。更新模式在已有 Suite 下创建草稿；创建模式 SHALL 原子创建 Suite 容器
和首个 DRAFT。草稿 SHALL 保存已确认的精确引用、顺序、Entry Skill、元数据和移除结果，并继续
执行现有 Suite 审核生命周期。

#### Scenario: 全部包已发布且引用仍有效
- **WHEN** 所有需要发布的包均达到 PUBLISHED，且最终成员检查通过
- **THEN** 系统根据已确认快照创建一个 SuiteVersion DRAFT
- **AND** 操作状态变为 `SUITE_DRAFT_CREATED`

#### Scenario: 创建模式全部成员就绪
- **WHEN** 创建模式的全部包版本均为 PUBLISHED，引用最终检查通过，目标坐标仍可用
- **THEN** 系统在一个事务中创建 Suite 及其首个 DRAFT SuiteVersion
- **AND** 不向普通发现入口暴露无版本的空 Suite

#### Scenario: 包仍在扫描或审核
- **WHEN** 至少一个必要包仍处于扫描或审核中
- **THEN** 操作状态为 `WAITING_FOR_MEMBERS`
- **AND** 不创建临时 SuiteVersion

#### Scenario: 引用成员失效
- **WHEN** 创建草稿前精确引用被下架、隐藏、删除或不再符合受众可见性
- **THEN** 操作状态为 `BLOCKED`
- **AND** 不创建 SuiteVersion

### Requirement: REQ-SBP-13 Bundle 恢复 SHALL 幂等且不破坏成员

系统 SHALL 展示成员级进度，并能处理重复、延迟或丢失的生命周期通知。重试 SHALL 只处理未完成
工作。取消 SHALL 停止后续编排和 SuiteVersion 创建，但 SHALL NOT 修改已创建 SkillVersion、
精确引用或已完成审核。

#### Scenario: 生命周期通知重复到达
- **WHEN** 同一成员生命周期通知被重复处理
- **THEN** 成员结果和 Suite 草稿数量保持不变

#### Scenario: 恢复丢失的通知
- **WHEN** 所有必要包已经 PUBLISHED，但对应事件丢失
- **THEN** 有界恢复任务发现最终状态
- **AND** 最多创建一个 Suite 草稿

#### Scenario: 取消等待中的操作
- **WHEN** 有权限操作者在 Suite 草稿创建前取消操作
- **THEN** 操作变为 `CANCELLED`，后续不能再创建 SuiteVersion
- **AND** 成员 Skill 保持独立状态

### Requirement: REQ-SBP-14 并发导入 SHALL NOT 占用相同 Suite 坐标或目标版本

系统 SHALL 阻止两个非终态创建操作占用相同 Suite 坐标，并阻止两个非终态更新操作或已有
SuiteVersion 使用同一 Suite 和目标版本。最终创建草稿时 SHALL 重新检查坐标、版本和当前状态。

#### Scenario: 并发创建相同 Suite 坐标
- **WHEN** 两个操作者同时确认创建相同 Namespace 和 Suite slug
- **THEN** 最多一个操作获得目标坐标占用
- **AND** 另一个操作在创建任何 Skill 前失败或必须重新预览

#### Scenario: 并发确认指向相同版本
- **WHEN** 两个操作者同时确认同一 Suite 和目标版本的计划
- **THEN** 最多一个操作获得目标版本占用
- **AND** 另一个操作必须重新预览或选择新版本

### Requirement: REQ-SBP-15 Bundle 处理 SHALL 控制资源消耗

系统 SHALL 流式校验归档，每次预览对每个携带包成员最多解压和计算一次 hash，批量解析成员、
引用和权限，并避免读取、扫描或写入纯引用和未变化成员。状态轮询 SHALL NOT 重新读取归档。

#### Scenario: 预览最大合法 Suite
- **WHEN** Bundle 使用包和精确引用描述允许的最大成员数
- **THEN** 预览使用有界归档处理和集合查询
- **AND** 返回完整计划且不产生逐成员查询放大

#### Scenario: 轮询等待中的操作
- **WHEN** 客户端反复查询操作状态
- **THEN** 系统直接读取持久化进度
- **AND** 不解压归档、不计算 hash、不下载引用、不重复扫描包

### Requirement: REQ-SBP-16 现有 Skill 和 Suite 契约 SHALL 保持兼容

Bundle 接口 SHALL 是增量接口。单 Skill 发布、精确 Suite 管理和安装、技能市场、套件专区、
单 Skill Label/Tag API 和旧客户端 SHALL 保持现有行为。已发布 SuiteVersion SHALL 继续是不可变的
精确 PUBLISHED SkillVersion 快照。

#### Scenario: 使用现有客户端和页面
- **WHEN** 用户从不调用 Bundle 接口
- **THEN** 现有 Skill 和 Suite 行为保持不变

#### Scenario: Bundle 操作完成
- **WHEN** Bundle 操作创建 SuiteVersion DRAFT
- **THEN** 现有 Suite API 可以查看、在允许时编辑、提交、审核和发布该草稿
- **AND** 安装客户端不需要理解 Bundle 归档

### Requirement: REQ-SBP-17 Web 导入 SHALL 复用现有 Suite 创建和新版本入口

“创建 Suite”和“创建新版本”页面 SHALL 同时提供“从技能市场组合”和“从本地导入”。本地导入
SHALL 支持上传一个 ZIP；浏览器支持安全目录选择时，也可以选择一个根目录并按同一归档协议提交。
更新模式 SHALL 把未出现在完整 Manifest 中的基准成员作为高风险移除项单独展示。

#### Scenario: 创建 Suite 时选择本地导入
- **WHEN** 用户在创建入口选择本地导入并提交合法文件
- **THEN** 页面展示没有基准版本的创建预览
- **AND** 不要求用户先逐个进入 Skill 发布页

#### Scenario: 更新 Suite 时选择本地导入
- **WHEN** 用户从已有 Suite 的“创建新版本”入口选择本地导入
- **THEN** 页面展示相对于明确基准版本的成员和发布动作差异
- **AND** 现有手工组合入口保持可用

#### Scenario: 预览包含移除成员
- **WHEN** 更新 Manifest 未包含至少一个基准成员
- **THEN** 页面在确认区单独列出移除项及数量
- **AND** 确认文案说明当前已发布 Suite 不受影响

### Requirement: REQ-SBP-18 Web 确认和进度 SHALL 准确展示副作用

确认页面 SHALL 展示将创建的 Skill、将创建的 SkillVersion、复用版本、纯引用和移除成员数量，
并说明成员就绪后只生成 Suite 草稿。确认后，进度页 SHALL 展示每个成员的创建、扫描、审核、复用、
引用、失败或阻塞状态，并能通过操作 ID 在刷新或重新登录后恢复。

#### Scenario: 用户确认混合计划
- **WHEN** 预览同时包含创建 Skill、创建版本、复用、引用和移除
- **THEN** 确认界面分别列出每类数量和关键成员
- **AND** 不使用仅包含“是否继续”的笼统确认

#### Scenario: 成员等待审核
- **WHEN** 已确认操作至少有一个成员等待独立审核
- **THEN** 进度页展示 `WAITING_FOR_MEMBERS` 和成员审核入口
- **AND** 不把操作展示为 Suite 已更新

#### Scenario: 操作创建 Suite 草稿
- **WHEN** 操作达到 `SUITE_DRAFT_CREATED`
- **THEN** 页面提供新 Suite 草稿入口及后续提交审核说明
