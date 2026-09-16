## ADDED Requirements

### Requirement: Suite SHALL be a typed Namespace resource

系统 SHALL 将 Suite 作为 Namespace 所有的独立资源，并以 `SUITE + namespace + slug` 标识。系统 SHALL 在同一 Namespace 内保持 Suite slug 唯一，但 SHALL 允许 Skill 与 Suite 使用相同 slug。

#### Scenario: Skill and Suite share a slug
- **WHEN** `SKILL @global/marketing` 已存在
- **THEN** 授权用户可以创建 `SUITE @global/marketing`
- **AND** API、URL、搜索结果和 CLI 操作通过资源类型明确区分二者

#### Scenario: Duplicate Suite slug
- **WHEN** 同一 Namespace 已存在 `SUITE @global/marketing`
- **THEN** 系统拒绝再次创建同 slug Suite
- **AND** 返回可操作的冲突说明

#### Scenario: Legacy install remains Skill-specific
- **WHEN** Skill 和 Suite 共用 `@global/marketing`
- **AND** 用户执行 `skillhub install @global/marketing`
- **THEN** CLI 只解析并安装 Skill
- **AND** 只有 `skillhub suite install @global/marketing` 才解析 Suite

### Requirement: SuiteVersion SHALL reference immutable published Skill versions

系统 SHALL 只允许 SuiteVersion 引用同一 Registry 中状态为 PUBLISHED 的精确 SkillVersion。创作请求 SHALL 携带候选接口返回的 `skillVersionId`，服务端 SHALL 按该 ID 读取版本并校验随请求提交的坐标与版本一致，避免同名 Skill 被重新解析到其他所有者。系统 SHALL 保存成员坐标、版本和 fingerprint 快照。一个 SuiteVersion SHALL 最多包含 100 个不同 Skill。

#### Scenario: Create a valid SuiteVersion
- **WHEN** 管理者提交不超过 100 个不同的已发布 SkillVersion
- **THEN** 系统创建 DRAFT SuiteVersion
- **AND** 每个 Member 保存精确 SkillVersion ID、坐标、版本、fingerprint 和顺序

#### Scenario: Reject mismatched member identity
- **WHEN** 请求中的 `skillVersionId` 与同时提交的坐标或版本不一致
- **THEN** 系统拒绝该 SuiteVersion 定义
- **AND** 不按坐标重新解析到另一个同名 SkillVersion

#### Scenario: Add a Member without choosing a version
- **WHEN** 作者添加一个 Skill 且没有显式选择版本
- **THEN** 系统向作者推荐当前可安装的最新 SkillVersion
- **AND** 保存前明确展示解析出的版本
- **AND** Member 最终保存为精确 SkillVersion ID、版本和 fingerprint，而不是 `latest`

#### Scenario: Select an older published version
- **WHEN** 作者显式选择一个仍处于 PUBLISHED 且可访问的历史 SkillVersion
- **THEN** 系统允许将该精确版本保存为 Member

#### Scenario: Reject an unresolved or unpublished member
- **WHEN** 成员坐标或版本不存在，或对应 SkillVersion 不是 PUBLISHED
- **THEN** 系统拒绝创建或提交该 SuiteVersion
- **AND** 返回每个无效成员的坐标和原因

#### Scenario: Update Members to currently installable versions
- **WHEN** 作者对 DRAFT SuiteVersion 请求更新 Member 版本
- **THEN** 系统先展示每个拟议版本变化
- **AND** 只有作者确认后才更新 DRAFT 的精确 Member 引用
- **AND** PUBLISHED SuiteVersion 保持不变

#### Scenario: Reject duplicate or excessive members
- **WHEN** SuiteVersion 重复引用同一个 Skill，或成员数超过 100
- **THEN** 系统拒绝该定义
- **AND** 不创建部分成员关系

### Requirement: Entry Skill SHALL be one explicit ordinary Member

SuiteVersion SHALL 指定且仅指定一个 Entry Skill。Entry Skill SHALL 精确指向该 SuiteVersion 的一个普通 Member，保留完整 Skill 包和独立安装能力；系统 SHALL NOT 根据 Suite 和 Skill 的同名关系推断入口，也 SHALL NOT 要求 Entry 与 Suite 属于同一 Namespace。

#### Scenario: Valid Entry Skill
- **WHEN** SuiteVersion 将一个现有 Member 指定为 Entry Skill
- **THEN** 系统保存该精确 SkillVersion 关系
- **AND** Suite 详情和安装计划将其标记为入口

#### Scenario: Entry Skill is not a Member
- **WHEN** 提交的 Entry Skill 不在 Member 集合中
- **THEN** 系统拒绝该 SuiteVersion

#### Scenario: Suite has no Entry Skill
- **WHEN** 提交的 SuiteVersion 没有指定 Entry Skill
- **THEN** 系统拒绝该 SuiteVersion

#### Scenario: Public cross-Namespace Entry Skill
- **WHEN** SuiteVersion 将其他 Namespace 中符合目标受众规则的 PUBLIC Member 指定为 Entry Skill
- **THEN** 系统允许该 Entry Skill
- **AND** 引用不改变该 Skill 的所有权或生命周期

### Requirement: Member candidates SHALL be filtered by the Server

系统 SHALL 从当前 Registry 的 SkillVersion 数据中提供 Suite Member 候选，并 SHALL 根据当前用户访问权、Skill/Namespace 状态、PUBLISHED 可安装版本、Suite Namespace 和目标 SuiteVersion 可见性在服务端过滤。候选查询 SHALL NOT 泄露用户无权查看的 Skill，v1 SHALL NOT 返回外部 Registry 成员。

#### Scenario: Open the Member picker
- **WHEN** 有投稿权限的用户为 DRAFT SuiteVersion 搜索 Member
- **THEN** 系统只搜索当前用户可读取的 ACTIVE、非 hidden 且具有 PUBLISHED 可安装版本的 Skill
- **AND** 返回精确版本选择以及与目标 Suite 可见性的兼容结果

#### Scenario: Search for an inaccessible private Skill
- **WHEN** 用户搜索自己无权访问的 PRIVATE Skill
- **THEN** 候选查询不返回该 Skill、版本、fingerprint 或其他私有元数据

#### Scenario: Search for a personally accessible but audience-incompatible Skill
- **WHEN** 用户可以访问一个跨 Namespace 的非 PUBLIC Skill
- **AND** 该 Skill 不满足目标 Suite 的受众规则
- **THEN** 系统不允许选择该 Skill
- **AND** 可以向用户说明目标可见性或 Namespace 不兼容，但不得向其他用户暴露该 Skill

### Requirement: Suite SHALL NOT own or mutate Member lifecycle

创建、提交、审核、发布、拒绝、下架、隐藏、归档或删除 Suite SHALL NOT 创建、发布、重新扫描、重新审核、下架、隐藏、归档或删除任何 Member Skill 或 SkillVersion。

#### Scenario: Publish a Suite of existing Skills
- **WHEN** SuiteVersion 通过发布流程
- **THEN** 系统只改变 Suite 和 SuiteVersion 状态
- **AND** 所有 Member 的状态、所有权、统计和审核记录保持不变

#### Scenario: Remove a Suite
- **WHEN** 授权管理者下架、归档或删除 Suite
- **THEN** Member Skill 仍可按其自身权限独立搜索、安装和管理

### Requirement: Suite SHALL use a scan-free version lifecycle

SuiteVersion SHALL 使用 DRAFT、PENDING_REVIEW、PUBLISHED、REJECTED、YANKED 状态，且 SHALL NOT 进入 SCANNING、SCAN_FAILED 或 UPLOADED。PUBLIC 和 NAMESPACE_ONLY Suite SHALL 完成一次 Suite 级审核；PRIVATE Suite SHALL 按现有 PRIVATE Skill 的直接发布原则处理。

#### Scenario: Submit a public Suite for review
- **WHEN** 授权管理者提交有效的 PUBLIC SuiteVersion
- **THEN** SuiteVersion 转为 PENDING_REVIEW
- **AND** 系统创建一个目标类型为 SUITE_VERSION 的审核任务
- **AND** 不为 Member 创建新的审核任务或扫描任务

#### Scenario: Approve a Suite
- **WHEN** 有权限的审核者批准 PENDING_REVIEW SuiteVersion
- **AND** 全部成员仍满足发布条件
- **THEN** SuiteVersion 转为 PUBLISHED
- **AND** Suite.latestVersionId 指向该版本

#### Scenario: Reject a Suite
- **WHEN** 有权限的审核者拒绝 PENDING_REVIEW SuiteVersion
- **THEN** SuiteVersion 转为 REJECTED
- **AND** Member 状态保持不变

#### Scenario: Publish a private Suite
- **WHEN** 授权管理者确认发布有效的 PRIVATE DRAFT SuiteVersion
- **THEN** SuiteVersion 直接转为 PUBLISHED
- **AND** 系统不创建审核或扫描任务

### Requirement: Suite publication SHALL revalidate Member eligibility

系统 SHALL 在提交审核、审核批准和直接发布时重新验证全部 Member 的状态、可见性和访问范围。验证失败 SHALL 保持原状态并阻止发布动作。

#### Scenario: Member becomes unavailable during review
- **WHEN** SuiteVersion 处于 PENDING_REVIEW
- **AND** 一个 Member 在批准前被 YANKED、隐藏、归档或删除
- **THEN** 批准操作失败且 SuiteVersion 保持 PENDING_REVIEW
- **AND** 响应指出阻塞 Member 和当前状态

### Requirement: Published SuiteVersion SHALL be immutable

PUBLISHED 和 YANKED SuiteVersion 的元数据、Member、顺序、Entry Skill、成员版本和 fingerprint 快照 SHALL 不可修改。任何组成变化 SHALL 创建新的 SuiteVersion。

#### Scenario: Member publishes a newer version
- **WHEN** Member Skill 发布一个新版本
- **THEN** 现有 SuiteVersion 仍引用原 SkillVersion
- **AND** Suite 安装不会自动解析到新版本

#### Scenario: Change Suite membership
- **WHEN** 管理者添加、移除、重排 Member 或改变 Entry Skill
- **THEN** 系统要求创建一个新的 SuiteVersion
- **AND** 旧 SuiteVersion 保持不变

### Requirement: Suite visibility SHALL not exceed Member accessibility

SuiteVersion SHALL 保存自身经过审核的可见性快照，其发布范围 SHALL 不得宽于任何 Member 的可访问范围。安装历史版本时 SHALL 使用该 SuiteVersion 的可见性，而不是最新版本的可见性。安装时系统 SHALL 再次对当前用户逐个检查 Suite 和 Member 的实时权限。

#### Scenario: Public Suite contains a non-public Member
- **WHEN** PUBLIC SuiteVersion 包含 NAMESPACE_ONLY 或 PRIVATE Member
- **THEN** 系统拒绝提交或发布
- **AND** 指出可见性不兼容的 Member

#### Scenario: Namespace Suite uses an allowed Member
- **WHEN** NAMESPACE_ONLY SuiteVersion 引用 PUBLIC Member，或同 Namespace 的 NAMESPACE_ONLY Member
- **THEN** 该 Member 通过可见性验证

#### Scenario: Namespace Suite contains a private Member
- **WHEN** NAMESPACE_ONLY SuiteVersion 引用 PRIVATE Member
- **THEN** 系统拒绝提交或发布
- **AND** 即使当前提交者本人可以访问该 Member 也不例外

#### Scenario: Private Suite uses same-Namespace Members
- **WHEN** PRIVATE SuiteVersion 引用 PUBLIC Member，或同 Namespace 的 NAMESPACE_ONLY/PRIVATE Member
- **AND** 操作者具备 Suite Namespace 管理权限和所有 Member 读取权限
- **THEN** Member 通过静态可见性验证

#### Scenario: Suite references a cross-Namespace non-public Member
- **WHEN** SuiteVersion 引用其他 Namespace 的 NAMESPACE_ONLY 或 PRIVATE Member
- **THEN** 系统拒绝提交或发布
- **AND** 不以提交者个人的跨 Namespace 权限替代目标受众校验

#### Scenario: Installer loses Member access
- **WHEN** 用户能够读取 Suite，但当前无权下载至少一个 Member
- **THEN** 安装预检整体失败
- **AND** 不下载或修改任何本地 Skill

#### Scenario: Change Suite visibility
- **WHEN** 管理者希望扩大或收窄已发布 Suite 的可见性
- **THEN** 系统要求创建包含目标可见性的新 SuiteVersion 并走对应审核流程
- **AND** 已发布历史 SuiteVersion 的可见性保持不变

#### Scenario: Install a historical SuiteVersion
- **WHEN** 用户请求安装非最新的 PUBLISHED SuiteVersion
- **THEN** 系统按该历史版本自己的可见性检查 Suite 访问权
- **AND** 继续逐个检查 Member 的当前访问权和可下载性

#### Scenario: Do not disclose inaccessible Member metadata
- **WHEN** 用户能够定位 Suite 但无权查看阻塞的 PRIVATE Member
- **THEN** 安装预检返回不泄露 Member 私有名称、下载地址或元数据的失败信息
- **AND** 有治理权限的管理员可以查看具体阻塞 Member 和原因

### Requirement: Unavailable Members SHALL degrade rather than rewrite a SuiteVersion

Member 在 Suite 发布后变得不可用时，系统 SHALL 保留 SuiteVersion 历史快照并将其显示为 degraded。系统 SHALL NOT 删除成员关系或自动替换为其他版本。

#### Scenario: Member is yanked or hidden after Suite publication
- **WHEN** PUBLISHED SuiteVersion 的 Member 被 YANKED、隐藏或归档
- **THEN** Suite 详情保留原成员坐标和版本
- **AND** 标记该成员不可用及原因
- **AND** Suite 安装预检失败

#### Scenario: Member visibility becomes narrower
- **WHEN** PUBLIC SuiteVersion 的 Member 从 PUBLIC 变为 NAMESPACE_ONLY 或 PRIVATE
- **THEN** SuiteVersion 保持 PUBLISHED 且显示 degraded
- **AND** 新安装整体失败
- **AND** 系统不自动选择该 Skill 的其他版本

#### Scenario: A reversible Member restriction is restored
- **WHEN** Member 因 hidden、archive 或可见范围收窄导致 Suite degraded
- **AND** 后续恢复后该精确 SkillVersion 再次满足 Suite 可见性和安装条件
- **THEN** Suite 自动重新计算为可安装
- **AND** 不创建或修改 SuiteVersion

#### Scenario: A permanently unavailable Member has a newer version
- **WHEN** Suite Member 已 YANKED 或硬删除
- **AND** 同一 Skill 存在其他 PUBLISHED 版本
- **THEN** 原 SuiteVersion 仍保持 degraded
- **AND** 管理者必须创建新 SuiteVersion 才能采用有效版本

#### Scenario: Member is hard-deleted
- **WHEN** PUBLISHED SuiteVersion 引用的 SkillVersion 被治理性硬删除
- **THEN** Suite 历史保留成员坐标、版本和 fingerprint 快照
- **AND** 成员外键可以为空并标记为已删除
- **AND** Suite 安装预检失败

#### Scenario: A deleted coordinate is recreated
- **WHEN** 被硬删除的 Member 后续以相同 namespace、slug 和 version 字符串重新创建
- **THEN** 原 SuiteVersion 不自动关联新 SkillVersion
- **AND** 原成员快照保持 tombstoned 和 degraded
- **AND** 管理者必须创建新 SuiteVersion 才能引用新实体及其 fingerprint

### Requirement: Suite installation SHALL be atomic across Members and targets

CLI SHALL 在修改目标目录前完成全部成员和全部 Agent 目标的解析、权限、冲突和下载预检。CLI SHALL 暂存并校验全部 Member 后统一提交；失败时 SHALL 恢复已有目录和 inventory。

#### Scenario: Install all Members successfully
- **WHEN** 全部 Member 可下载、fingerprint 匹配且目标可写
- **THEN** CLI 将全部 Member 安装到每个选定 Agent 的标准 Skill 目录
- **AND** 一次性记录 Suite 和 Member inventory

#### Scenario: A Member download or fingerprint fails
- **WHEN** 任一 Member 下载失败或 fingerprint 不匹配
- **THEN** Suite 安装整体失败
- **AND** 所有安装前已存在的目录和 inventory 保持不变
- **AND** 不留下已提交的部分 Member

#### Scenario: Commit fails after replacing some targets
- **WHEN** CLI 在文件提交阶段替换部分目标后发生错误
- **THEN** CLI 回滚本次已经替换的全部目标
- **AND** 恢复备份和安装前 inventory
- **AND** 无法完成的回滚必须保留备份路径并明确报告

#### Scenario: Concurrent operations target the same local Suite
- **WHEN** 两个 CLI 进程并发安装、升级或卸载同一 registry 和 Suite 坐标
- **THEN** CLI 通过 Suite 级本地锁只允许一个操作进入事务
- **AND** 另一个操作明确报告繁忙，不得基于旧 inventory 提交

#### Scenario: Existing Member has local changes
- **WHEN** Suite 安装或升级将复用或替换一个已登记但 fingerprint 已变化的 Member 目录
- **AND** 用户未明确传入 `--force`
- **THEN** CLI 在写入任何目标或 inventory 前拒绝该操作
- **AND** 保留本地文件和现有 inventory
- **AND** 只有用户显式传入 `--force` 时才允许覆盖本地修改

### Requirement: Suite installation SHALL preserve Agent Skills compatibility

CLI SHALL 将每个 Member 作为普通 Skill 安装到 Agent 已支持的 Skill 根目录。CLI SHALL NOT 为 Suite 创建同名 `SKILL.md` 或要求 Agent 理解 Suite 协议。

#### Scenario: Suite and Skill share a slug locally
- **WHEN** `SKILL @global/marketing` 与 `SUITE @global/marketing` 同时存在
- **AND** 用户安装该 Suite
- **THEN** CLI 只安装 Suite 的 Member
- **AND** 不创建或覆盖 `<skills-root>/marketing/SKILL.md` 作为 Suite 编排文件

### Requirement: Inventory SHALL track shared installation provenance

CLI inventory SHALL 记录已安装 SuiteVersion、精确成员快照，以及每个 Skill 目标的直接安装和 Suite 来源集合。旧 inventory SHALL 能够无损读取并迁移缺省字段。

#### Scenario: A Skill is direct-installed and Suite-installed
- **WHEN** 同一精确 SkillVersion 已直接安装，随后又被 Suite 引用
- **THEN** CLI 复用兼容的本地内容
- **AND** inventory 同时记录 direct 和 Suite 来源

#### Scenario: Multiple Suites share a Member
- **WHEN** 两个已安装 Suite 引用同一精确 SkillVersion 和目标目录
- **THEN** CLI 保留一个成员目录
- **AND** inventory 记录两个 Suite 来源

#### Scenario: Existing inventory has no Suite fields
- **WHEN** CLI 读取升级前的 inventory schema
- **THEN** CLI 将缺失的 Suite 和来源集合按空值处理
- **AND** 已安装 Skill 记录和目标路径保持不变

#### Scenario: Same Suite coordinate is installed from different registries
- **WHEN** 两个 Registry 各自安装了相同 Namespace 和 slug 的 Suite
- **THEN** inventory 按 Registry 分别记录 Suite 与 Member 来源
- **AND** 移除其中一个 Registry 的 Suite 不得修改另一个 Registry 的来源或文件

### Requirement: Suite removal SHALL be ownership-safe

`skillhub suite remove` SHALL 仅移除该 Suite 的来源记录。CLI SHALL 只自动删除不再被直接安装、未被其他 Suite 引用且未被本地修改的 Member 目录。

#### Scenario: Remove an exclusively Suite-installed Member
- **WHEN** Member 仅由被删除 Suite 安装且本地文件未修改
- **THEN** CLI 删除该 Member 目录和对应 inventory 来源

#### Scenario: Preserve a shared or direct-installed Member
- **WHEN** Member 仍有 direct 来源或其他 Suite 来源
- **THEN** CLI 保留 Member 目录及剩余来源

#### Scenario: Preserve a locally modified Member
- **WHEN** 待清理 Member 的当前 fingerprint 与安装基线不同
- **THEN** CLI 保留该目录
- **AND** 报告本地修改和人工处理建议

### Requirement: Suite check and upgrade SHALL use exact snapshots

CLI SHALL 提供 Suite 状态检查和升级计划。升级 SHALL 解析目标 SuiteVersion 的精确 Member 集合并使用与安装相同的原子事务，不得逐个追随 Member latest。

#### Scenario: Check an intact Suite
- **WHEN** inventory、磁盘内容和远端 SuiteVersion 快照一致
- **THEN** `skillhub suite check` 报告最新且完整

#### Scenario: Check a degraded local Suite
- **WHEN** Member 缺失、本地修改、版本不符或远端已不可用
- **THEN** `skillhub suite check` 按 Member 报告差异和阻塞原因

#### Scenario: Upgrade to a new SuiteVersion
- **WHEN** 用户确认从一个 SuiteVersion 升级到另一个版本
- **THEN** CLI 展示成员新增、删除和版本变化
- **AND** 通过原子安装事务应用完整目标快照

### Requirement: Suite governance actions SHALL not cascade to Members

Suite SHALL 支持版本下架、容器隐藏、恢复和归档，并 SHALL 与 Member 治理状态解耦。

#### Scenario: Yank the latest SuiteVersion
- **WHEN** 授权管理者下架 Suite 的最新 PUBLISHED 版本
- **THEN** 该版本转为 YANKED
- **AND** Suite.latestVersionId 重新指向仍可发布的最新 SuiteVersion 或为空
- **AND** Member 状态保持不变

#### Scenario: Hide or archive a Suite
- **WHEN** 管理者隐藏或归档 Suite
- **THEN** Suite 按现有治理规则停止普通发现或新版本操作
- **AND** Member 的发现和生命周期保持不变

### Requirement: Typed discovery SHALL preserve existing Skill search contracts

Suite SHALL 有独立 API 和 Web URL。新的类型化资源发现结果中，每条 Skill 或 Suite 结果 SHALL 返回 `resourceType`。现有 Skill 搜索接口 SHALL 继续只返回 Skill，并保持原响应契约。Suite 详情 SHALL 返回精确版本、成员、Entry Skill、可用性和 degraded 原因。

#### Scenario: Search returns same-slug resources
- **WHEN** 搜索命中同 Namespace、同 slug 的 Skill 和 Suite
- **THEN** 系统返回两条独立结果
- **AND** 每条结果包含不同的 `resourceType` 和详情 URL

#### Scenario: Existing Skill search remains Skill-only
- **WHEN** 旧 CLI 或第三方客户端调用现有 Skill 搜索接口
- **THEN** 响应只包含 Skill
- **AND** 字段、枚举含义和解析行为与引入 Suite 前保持兼容

#### Scenario: Resolve a Suite install plan
- **WHEN** 授权用户通过 Suite 专用接口解析某个版本
- **THEN** 响应包含 SuiteVersion 身份以及有序的精确 Member 版本、fingerprint 和可下载状态

#### Scenario: Skill detail shows visible Suite entry references
- **WHEN** 当前 Skill 是一个或多个最新 PUBLISHED SuiteVersion 的 Entry Skill
- **THEN** Skill 详情返回当前查看者有权读取的 Suite 摘要、精确版本和成员数量
- **AND** Web 将其表达为“被套件用作入口”并链接到完整 Suite
- **AND** Skill 仍保留普通的独立安装入口
- **AND** 系统不返回对当前查看者不可见、已隐藏或已归档的 Suite 信息

### Requirement: Suite authors SHALL have a complete Web management flow

Web SHALL expose only the Suite actions authorized by the Server. An authorized author SHALL be
able to create and inspect a Suite, edit a DRAFT, reopen a REJECTED version before editing, and
create a new immutable version from a published snapshot. Namespace administrators SHALL additionally
be able to yank a published version, hide or restore discovery, archive or restore the Suite container,
and delete a Suite when no review is pending. These actions SHALL NOT modify Member Skills.

#### Scenario: Author manages editable and immutable versions
- **WHEN** an authorized author opens a DRAFT, REJECTED, PUBLISHED, or YANKED SuiteVersion
- **THEN** Web shows only actions permitted for that actor and state
- **AND** REJECTED is explicitly reopened before editing
- **AND** PUBLISHED and YANKED versions remain immutable and changes create a new version

#### Scenario: Administrator governs or deletes a Suite
- **WHEN** a Namespace administrator yanks, hides, restores, archives, unarchives, or deletes a Suite
- **THEN** Web requires confirmation for destructive container or publication actions
- **AND** a yank requires an audit reason
- **AND** deletion is unavailable while a review is pending
- **AND** hard deletion removes Suite-owned review tasks while retaining the deletion audit record
- **AND** no Member Skill lifecycle or content changes

#### Scenario: Public reader views a manageable Suite
- **WHEN** an unauthenticated or unauthorized reader opens a public Suite detail page
- **THEN** Web shows the Suite summary and its author-provided Markdown overview as separate information levels
- **AND** Web shows every ordered Member as a Skill card with its exact pinned version, Entry Skill marker, and current availability
- **AND** an available Member exposes live display metadata and links to Skill detail only when the current viewer can read that Skill
- **AND** a viewer-restricted or deleted Member remains a non-navigable snapshot without exposing live display metadata
- **AND** Web does not display edit, version creation, governance, or deletion controls
- **AND** Server authorization remains the enforcement boundary

### Requirement: Suite operations SHALL be authorized and audited

Suite 创建、编辑、提交、审核、发布、下架、隐藏、恢复、归档和删除 SHALL 使用现有 Namespace 与平台角色原则，并 SHALL 产生包含 Suite 类型、Suite ID、SuiteVersion ID、操作者和变更摘要的审计记录。

#### Scenario: Unauthorized user modifies a Suite
- **WHEN** 用户不具备该 Namespace 的 Suite 管理权限
- **THEN** 系统拒绝修改且不改变 Suite 状态

#### Scenario: Namespace Member creates a Suite
- **WHEN** 当前 Namespace MEMBER 创建 Suite
- **THEN** 系统允许创建并记录 createdBy
- **AND** 该用户在仍属于 Namespace 时可以维护自己的 DRAFT/REJECTED Suite 和提交新版本

#### Scenario: Suite creator leaves the Namespace
- **WHEN** Suite 创建者不再是 Suite Namespace 成员
- **THEN** createdBy 继续作为审计事实保留
- **AND** 该用户立即失去基于创建者身份的编辑、提交和 PRIVATE Suite 访问权
- **AND** 已发布 Suite、SuiteVersion 和审核记录保持不变
- **AND** Namespace ADMIN/OWNER 可以接管后续维护

#### Scenario: Public user views and installs a public Suite
- **WHEN** 用户访问 PUBLISHED PUBLIC SuiteVersion
- **THEN** 用户可以查看 Suite 公开元数据
- **AND** 只有全部 Member 仍公开且可安装时才能获得完整安装计划

#### Scenario: Anonymous user accesses a Suite in an archived Namespace
- **WHEN** Namespace 已归档且匿名用户访问其中的 PUBLISHED PUBLIC SuiteVersion
- **THEN** 系统拒绝查看和安装
- **AND** Namespace 成员和平台管理员仍按现有归档 Namespace 规则访问

#### Scenario: Namespace member accesses a namespace Suite
- **WHEN** 当前 Namespace MEMBER 访问 PUBLISHED NAMESPACE_ONLY SuiteVersion
- **THEN** 用户可以查看并在全部 Member 校验通过后安装

#### Scenario: Regular member accesses a private Suite
- **WHEN** 普通 Namespace MEMBER 不是 Suite 当前创建者且尝试访问 PRIVATE SuiteVersion
- **THEN** 系统拒绝查看和安装
- **AND** Namespace ADMIN/OWNER 及仍在 Namespace 内的当前创建者可以按规则访问

#### Scenario: Suite review is recorded once
- **WHEN** SuiteVersion 被提交并完成审核
- **THEN** 审核中心记录一个 SUITE_VERSION 审核任务及决定
- **AND** 不为 Member 复制审核记录

### Requirement: Rejected SuiteVersions SHALL preserve review history

REJECTED SuiteVersion MAY 由有权限的管理者退回 DRAFT、修改并重新提交。系统 SHALL 保留每次审核轮次和决定。PUBLISHED 或 YANKED SuiteVersion SHALL NOT 退回可编辑状态。

#### Scenario: Edit and resubmit a rejected SuiteVersion
- **WHEN** 管理者将 REJECTED SuiteVersion 退回 DRAFT、修正成员或元数据并重新提交
- **THEN** 系统创建新的审核轮次
- **AND** 原拒绝决定、审核意见和操作者记录保持可查询

#### Scenario: Attempt to edit a published SuiteVersion
- **WHEN** 管理者尝试修改 PUBLISHED 或 YANKED SuiteVersion
- **THEN** 系统拒绝修改
- **AND** 提示创建新的 SuiteVersion

#### Scenario: A stale draft edit races with publication
- **WHEN** 一个请求读取 DRAFT 后，另一事务先将同一 SuiteVersion 发布
- **AND** 旧请求随后尝试保存编辑结果
- **THEN** 系统拒绝旧请求的并发更新
- **AND** 已发布状态、发布时间和发布内容保持不变

### Requirement: Suite plan and Member download metrics SHALL remain attributable and idempotent

客户端 SHALL 为一次安装计划生成独立的 idempotency key，并在安全重试时复用；服务端 SHALL 按调用者隔离该 key，并生成 operation ID 关联该计划的审计记录。服务端成功签发完整计划后 SHALL 记录一次 Suite 安装请求，但 SHALL NOT 在此时预增 Member 下载数。每个 Member 继续通过现有 Skill 下载接口按实际下载请求计数，避免计划签发与文件下载对同一 Member 重复计数。这些指标表示服务端计划签发和实际下载请求，不表示 CLI 本地安装成功。

#### Scenario: Issue a complete Suite install plan
- **WHEN** 服务端完成 Suite 和全部 Member 的权限、状态及可下载性预检并签发完整安装计划
- **THEN** Suite 安装请求数增加一次
- **AND** 此时不增加 Member SkillVersion 下载数
- **AND** 相关审计记录共享同一个 operation ID

#### Scenario: Download an exact Member from the issued plan
- **WHEN** CLI 使用安装计划中的下载地址请求某个精确 Member SkillVersion
- **THEN** 现有 Skill 下载接口按原有口径记录一次该 Member 的下载
- **AND** 同一 Member 不因此前签发安装计划而重复计数

#### Scenario: Suite plan preflight fails
- **WHEN** 服务端因权限、状态或成员不可用而无法签发完整安装计划
- **THEN** 不增加 Suite 安装请求数
- **AND** 不增加 Member 下载数

#### Scenario: Local installation fails after plan issuance
- **WHEN** CLI 在服务端签发计划后因下载、校验或文件提交失败并回滚
- **THEN** 服务端已记录的计划计数保持不变
- **AND** 仅实际发出的 Member 下载请求按现有口径保留计数
- **AND** 系统不将这些计数描述为本地安装成功数

#### Scenario: Retry an already issued plan
- **WHEN** 客户端因网络或响应读取失败，使用相同 idempotency key 重试安装计划请求
- **THEN** 服务端返回同一 operation ID 对应的计划或幂等成功
- **AND** Suite 安装请求数和审计记录不重复增加
- **AND** 该重试保证至少覆盖服务端约定的 24 小时幂等窗口

#### Scenario: Anonymous callers reuse the same client key
- **WHEN** 两个匿名调用者对 Suite 安装计划使用相同的 idempotency key
- **THEN** 服务端使用经过哈希的调用者上下文和 Suite 坐标隔离幂等记录
- **AND** 不在幂等 actor key 中保存原始 IP 或 User-Agent

### Requirement: Existing Skill workflows SHALL remain compatible

引入 Suite 后，现有单 Skill 包协议、发布、扫描、审核、URL、API 和 CLI 安装行为 SHALL 保持不变。Suite 专用能力 SHALL 是增量接口。

#### Scenario: Publish and install an ordinary Skill
- **WHEN** 用户在不使用 Suite 的情况下发布并安装一个 Skill
- **THEN** 系统继续使用现有 Skill 生命周期和安装路径
- **AND** 不要求 Suite manifest 或新版 Suite inventory 数据

#### Scenario: Old CLI accesses a registry with Suites
- **WHEN** 不支持 Suite 的旧 CLI 使用现有 Skill API
- **THEN** Skill 搜索、解析、下载和安装仍正常工作
- **AND** 旧 CLI 不会把 Suite 误识别为 Skill

### Requirement: Skill and Suite lifecycle types SHALL remain isolated

系统 SHALL 为 SkillVersion 和 SuiteVersion 使用独立生命周期状态类型。Suite 专属状态 SHALL NOT 改变现有 `SkillVersionStatus` 的字段或枚举含义。Suite 的 degraded 可用性 SHALL 由成员当前状态计算，不得作为对 SkillVersion 状态的反向写入。

#### Scenario: Member availability changes
- **WHEN** PUBLISHED SuiteVersion 的 Member 变为不可安装或重新恢复可用
- **THEN** 系统重新计算 Suite 的可用性和阻塞原因
- **AND** SuiteVersion 的 PUBLISHED 状态保持不变
- **AND** Member SkillVersion 的生命周期状态不被 Suite 修改

#### Scenario: Existing client parses Skill status
- **WHEN** 旧客户端读取引入 Suite 后的 Skill API
- **THEN** 其看到的 Skill 状态枚举和值域与引入 Suite 前一致

### Requirement: Server and CLI versions SHALL fail compatibly

Suite 能力 SHALL 以增量方式提供。旧 CLI 使用新 Server 时 SHALL 保持全部普通 Skill 行为；新 CLI 使用不支持 Suite 的旧 Server 时 SHALL 保持普通 Skill 命令可用，并 SHALL 对 Suite 命令返回明确的不支持结果。

#### Scenario: New CLI uses an old Server
- **WHEN** CLI 请求 Suite 能力而 Server 未声明支持
- **THEN** CLI 停止 Suite 操作并说明 Server 不支持该能力
- **AND** 不将 Suite 命令降级为普通 Skill 安装
- **AND** 普通 Skill 命令仍可使用

#### Scenario: Upgrade a legacy inventory
- **WHEN** 新 CLI 首次向没有 Suite 字段的旧 inventory 写入 Suite 安装结果
- **THEN** CLI 在同一次原子写入中增加新版字段
- **AND** 已有 Skill、目标路径、版本和 fingerprint 记录保持不变

### Requirement: Review storage migration SHALL support rolling compatibility

审核存储从 Skill 专用关联扩展为类型化 subject 时，系统 SHALL 回填现有任务为 `SKILL_VERSION`，并 SHALL 在兼容窗口保留旧 Skill 关联的可读性。数据库迁移 SHALL 为增量迁移，应用回滚 SHALL NOT 要求删除新增 Suite 数据结构。

#### Scenario: Read an existing Skill review after migration
- **WHEN** 数据库迁移前已经存在 Skill 审核任务
- **THEN** 新版本应用仍按原 SkillVersion 读取和处理该任务
- **AND** 其审核决定、权限和审计语义保持不变

#### Scenario: Enable Suite review in a non-overlapping deployment
- **WHEN** 用户通过官方单实例 Compose 或本地 profile 运行 Server
- **THEN** Suite 审核写入默认可用
- **AND** 用户无需修改环境变量或数据库才能提交 Suite 审核

#### Scenario: Mixed application versions during rollout
- **WHEN** 部署期间同时存在支持和不支持 Suite subject 的应用实例
- **THEN** 现有 Skill 审核流程保持可用
- **AND** 数据库为旧版实例写入的 Skill 审核补全类型化 subject
- **AND** Suite 审核写入只在所有处理实例均支持类型化 subject 后启用
