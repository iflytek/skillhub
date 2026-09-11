## Context

需求背景与范围见 `proposal.md`。当前 Web 可以手工创建 Suite、编辑 DRAFT，并基于已有快照创建
新 SuiteVersion，但不能一次提交多个本地 Skill 文件夹、自动比较内容并统一跟踪成员发布。当前
Suite 使用不可变 SuiteVersion 快照，成员必须是精确的
PUBLISHED SkillVersion，并允许引用符合可见性要求的跨 Namespace PUBLIC Skill。普通 Skill 发布
可能涉及对象存储、异步扫描和独立人工审核。技能市场已经使用 `/search`，套件专区已经使用
`/suites`、类型化资源接口和 Suite 卡片。Skill 详情目前只有在该 Skill 是 Entry Skill 时才展示
所属 Suite。Suite 的 summary 和 overview 当前可为空，因此已发布 Suite 可能只能显示空内容提示。
现有 Label 用于 Skill 容器分类，Tag 是后端版本别名。

## Goals / Non-Goals

**目标：**

- 将一次 ZIP 或根目录上传转换为稳定、可检查的 Suite 创建或更新计划。
- 允许在操作者具备 Skill 创建权限的 Namespace 中，从携带包创建新 Skill。
- 允许继续精确引用合规的非本人所有 Skill，且不尝试发布它们。
- 只发布操作者已经具备独立发布权限的携带包成员。
- 扫描和审核期间不创建临时或部分有效的 SuiteVersion。
- 让重试、异步审核、权限变化和终止失败都可观察。
- 在任意成员 Skill 上安全展示当前用户可见的所属 Suite。
- 让 Suite 拥有自己的 Label，并要求新发布版本具备有效摘要和概述。
- 限制归档处理、数据库查询、扫描和反向引用投影的资源消耗。

**非目标：**

- 为操作者只能引用、不能管理的 Skill 发布新内容。
- 替代单 Skill 的独立扫描/审核或 Suite 自身审核。
- 合并技能市场和套件专区，或者改变两个入口的路由与产品定位。
- 批量修改成员 Label、增加 Suite Tag、迁移 Namespace、按成员关键词参与排名、CLI 上传
  Bundle、嵌套 Suite 或跨 Registry 成员。

## Decisions

### 1. 创建和更新共用 Manifest，成员分为“携带包”和“精确引用”

Manifest 描述完整目标成员集合和顺序，每个成员只能选择一种形式：

- **携带包成员**：指向一个以 `SKILL.md` 为根的包目录。Skill 不存在时，操作者必须在目标
  Namespace 具备创建权限；Skill 已存在时，操作者必须具备该 Skill 的发布权限。通过规范化包
  fingerprint 判断创建 Skill、创建 SkillVersion，还是复用现有精确版本。
- **精确引用成员**：指向一个已有、精确、PUBLISHED 的 SkillVersion，不携带包，Bundle 不修改它。
  操作者只需满足现有 Suite 编排和读取权限，不需要拥有该 Skill。

创建模式没有基准快照，全部成员均为新增。更新模式以明确的 SuiteVersion 为基准，只有基准成员
未出现在 Manifest 中时才视为移除，不能因为没有包目录就视为移除。引用成员可以新增，也可以
显式重新固定到另一个合规 PUBLISHED 版本。Entry Skill 可以使用任一成员形式。

**备选方案：要求每个成员都有包目录。** 不采用。Suite 可以合法引用其他用户的公开 Skill，强制
打包会错误暗示操作者可以重新发布或复制这些内容。

### 2. Suite 创建和更新使用同一个持久化 Saga

预览阶段保存有期限且归属当前操作者的 `PreviewSession`、临时归档和完整计划，但不占用 Suite 坐标
或版本。`PREVIEW_READY` 是 PreviewSession 状态，不是执行操作状态。确认事务先创建持久化
`ExecutionOperation` 并原子获取目标 Suite 坐标或版本占用；获取失败时必须在任何成员生命周期
副作用前结束，并要求重新预览。

ExecutionOperation 保存创建/更新模式、目标 Namespace/Suite/版本、操作者、归档摘要、完整成员计划、
已创建 Skill/SkillVersion ID、精确引用 ID 和失败原因。对外状态为 `RUNNING`、
`WAITING_FOR_MEMBERS`、`BLOCKED`、`SUITE_DRAFT_CREATED`、`CANCELLED` 和 `EXPIRED`。

只有需要创建或发生变化且有权限的携带包成员进入现有 Skill 发布流程。所有新版本成为 PUBLISHED，
并且复用成员和引用成员最终仍符合要求后，更新模式才创建 SuiteVersion DRAFT；创建模式原子创建
Suite 容器及首个 DRAFT。任一成员失败都会阻止生成 Suite 草稿，但不会生成部分 SuiteVersion。
领域事件触发状态协调，同时使用有界定时恢复处理事件丢失或乱序。

**备选方案：SuiteVersion 暂时引用未发布成员。** 不采用。这会破坏 #828 的精确 PUBLISHED
版本不变量，并把临时成员处理扩散到 Suite 校验、审核、详情、删除和安装全链路。

### 3. 文件只提交一次，先预览再确认

Web 接受一个 ZIP；支持目录选择的浏览器还可以选择一个根目录，并以同一归档协议提交。服务端将
归档流式写入临时对象存储，校验 Manifest 和携带包成员，计算 fingerprint，批量读取基准快照、
精确引用和操作者权限。预览为每个成员分别返回成员关系变化 `ADDED`、`UPDATED`、`UNCHANGED`、
`REMOVED`，以及发布动作 `CREATE_SKILL`、`CREATE_VERSION`、`REUSE_VERSION`、`REFERENCE_VERSION`、
`NONE`，避免混淆“加入 Suite”和“创建 Skill”。预览不产生任何生命周期副作用。

确认使用不透明 token，并绑定操作者、模式、目标坐标、归档摘要、目标 Suite 版本和完整计划。
只有确认成功创建的非终态 ExecutionOperation 才独占目标 Suite 坐标或版本。预览阶段解析出的包
版本和引用 ID 在确认与重试过程中保持稳定。创建模式在成员就绪前只通过执行操作占用目标坐标，
不创建对普通用户可见的空 Suite。

### 4. 每个异步边界都保留独立权限

预览明确区分四种权限：

- 在目标 Namespace 创建 Suite，或管理已有 Suite 并创建新版本的权限；
- 在目标 Namespace 创建新 Skill 的权限；
- 读取和编排精确引用版本的权限；
- 发布携带包 Skill 的权限。

服务端在预览、确认、每次创建 Skill/SkillVersion 和 Suite 草稿创建时重新检查对应权限。新 Skill
坐标冲突、目标 Namespace 不可写、失去包发布权限或引用失效都会阻止对应写入或最终创建 Suite
草稿。Suite 所有权不会扩大成员 Skill 权限，错误响应不得泄露无权读取的资源元数据。

### 5. 部分成员发布必须可观察且不可破坏性补偿

确认前尽量发现包、版本、所有权、可见性和策略等确定性错误。但运行期仍可能出现某个成员版本
已经创建、另一个成员失败的情况。重试只处理未完成工作。取消会停止后续编排并阻止创建
SuiteVersion，但不会删除独立创建的 SkillVersion 或已经形成的审核历史。

Bundle 不得调用会自动撤回其他待审版本或删除替换已有版本的发布路径。预览发现同一 Skill 已有
`PENDING_REVIEW` 版本，或目标版本号已经对应任一非 `PUBLISHED` 版本时，直接阻塞并要求用户先在
现有 Skill 流程中处理。这里复用的是普通 Skill 的校验、存储、扫描、审核和审计规则，不是无条件
复用当前具有替换副作用的内存型发布方法。

成员状态收敛规则如下：

| SkillVersion 状态或事件 | Bundle 状态 | 可执行动作 | ID 处理 |
|---|---|---|---|
| `SCANNING`、`PENDING_REVIEW` | `WAITING_FOR_MEMBERS` | 查看扫描或审核进度 | 保持预览绑定 ID |
| PRIVATE 新版本进入 `UPLOADED` | `RUNNING` | 使用 Bundle 确认中已明确授予的私有发布授权，重新鉴权后执行现有 confirm-publish 转换 | 保持 ID |
| `PUBLISHED` | 该成员完成 | 等待其他成员或创建 Suite 草稿 | 保持 ID |
| `SCAN_FAILED` | `BLOCKED` | 仅在现有重扫动作能保留同一 ID 时允许重试，否则重新上传并预览 | 不得静默换 ID |
| `REJECTED` | `BLOCKED` | 修改内容后重新上传并预览 | 原 ID 不再自动恢复 |
| 待审版本被撤回为 `UPLOADED` | `BLOCKED` | 重新预览 | 不自动重新提交审核 |
| 已绑定版本被删除、替换、下架或失去权限 | `BLOCKED` | 重新预览 | 不跟随新 ID |

状态读取只允许原操作者或当前有权治理目标 Suite/Namespace 的角色。读取时仍按当前权限过滤成员
元数据；重试和取消必须重新授权。最终创建 Suite 草稿前再次检查 Namespace 可写、Suite 创建或
管理权限、已有 Suite 仍为 ACTIVE、目标坐标/版本占用和全部成员资格，任一失败均进入 `BLOCKED`。

### 6. 保持现有 Skill fingerprint 和 Suite 快照模型

每个包目录都规范化为以自身 `SKILL.md` 为根的普通 Skill 包，并使用现有校验器和 fingerprint
算法。ZIP 文件顺序、压缩元数据、压缩级别和外层目录不参与内容身份。纯引用成员只比较精确
SkillVersion 身份和有效性，不下载或计算其包内容。

Manifest 提供目标 Suite/SuiteVersion 的展示信息、完整顺序和 Entry Skill。新建和变化包继续
使用现有发布版本规则；自动解析的版本在预览时固定。最终结果仍是一个只包含精确 PUBLISHED
引用的普通 DRAFT SuiteVersion。

### 7. 限制资源成本并保证归档安全

归档采用流式解析，拒绝危险路径、链接、重复规范化名称、过度解压以及超过配置的归档/成员/文件
限制。预览把成员文件保存为临时对象定位信息、大小、内容类型和已计算摘要，而不是把最大 Bundle
展开为一组常驻内存的 `byte[]`。确认后的发布边界从暂存对象流式读取并复用预览摘要，不重新解压
或计算 fingerprint；普通发布需要的文件 hash、包归档和存储对象也从该流式输入生成。当前成员、
引用、版本和权限批量读取。纯引用和未变化成员不复制对象、不扫描、不审核。

外层解析和成员解析分开执行。外层只负责唯一 Manifest、成员目录边界和文件归属；成员目录去除
自身前缀后，必须成为一个普通的、根部含 `SKILL.md` 的 Skill 包，并复用现有
`SkillPackageValidator`、元数据解析、合规规则和错误/警告等级。成员目录不得重叠或嵌套，未声明的
`SKILL.md` 和无法归属的普通文件均阻止确认，避免服务端猜测用户意图。Manifest 与 `SKILL.md`
重复字段的优先级必须在协议中唯一确定；必须一致的字段发生冲突时直接报错。

格式校验、fingerprint 和权限分析都发生在预览阶段；Scanner 仍属于确认后的普通 Skill 生命周期，
不为了预览创建扫描任务。任一成员存在阻塞性格式错误时，页面可以展示全部已发现问题，但整个
Bundle 不可确认。

协调任务依靠带索引的操作/成员状态，不重新读取归档。反向引用使用集合式、有上限或分页的查询，
不得逐条查询 Suite。

### 8. 技能市场与套件专区保持分开

技能市场不增加类型切换。`/search` 继续只展示 Skill，并保留 Skill Label、收藏和排序。
`/suites` 继续作为 Suite 专区，使用类型化资源接口和现有 Suite 卡片。

唯一新增的发现能力是反向成员关系：Skill 详情查询最新、ACTIVE、非隐藏、PUBLISHED 且包含当前
Skill 的 SuiteVersion，不再要求当前 Skill 必须是 Entry。结果标明 Entry 身份，只返回用户有权
读取的兄弟成员摘要；无权读取的成员仅显示数量。成员关键词暂不影响 Suite 排名。

### 9. Suite 拥有自己的 Label，但不向成员级联

复用 `LabelDefinition`、本地化、`visibleInFilter`、排序以及 NORMAL/PRIVILEGED 权限语义，但使用
独立的 Suite-to-Label 关联。Suite Label 用于 Suite 容器展示和套件专区筛选。增删 Label 不创建
SuiteVersion，也不修改任何成员 Skill Label。

**备选方案：把现有 Skill Label 关联迁移为通用多态资源表。** 暂不采用。这会迁移稳定的 Skill
数据并削弱数据库外键约束，当前收益不足。独立关联可以复用 Label 定义，同时保持所有权清晰。

Tag 继续属于 SkillVersion。SuiteVersion 已经有显式版本，因此本次不增加 Suite Tag，也不向
成员批量设置 Tag。

### 10. 在发布边界要求有效摘要和概述

DRAFT SuiteVersion 可以在 summary 或 overview 尚未完成时保存。提交审核、PRIVATE 直接发布和
最终批准时，两者都必须非空。summary 是套件专区、卡片和引用使用的简短说明；overview 是
Markdown，说明用途、成员职责或顺序、预期输入输出和使用边界。服务端负责强制校验，不自动写入
“精选技能组合”之类的泛化文案。

已有字段为空的 PUBLISHED SuiteVersion 仍可读取和安装，避免破坏历史数据；创建下一个版本时
必须补齐。Bundle 可以显式提供新值，也可以继承当前 SuiteVersion 的非空值，预览必须展示最终
解析结果。

### 11. Suite 概述和 Entry Skill 说明各自承担职责

Suite overview 说明组合用途、适用场景、成员分工或调用顺序、预期输入输出和使用边界。编辑器提供
这些章节的结构化模板和完整性提示，但不以机械字数门槛鼓励填充内容，也不自动拿成员文档冒充
Suite 作者的概述。

Suite 详情可以在独立折叠区域按需展示 Entry Skill 固定 SkillVersion 的 `SKILL.md`。内容必须延迟
加载、复用该精确版本现有读取权限和安全 Markdown 渲染，并明确标注来源坐标与版本。Entry Skill
说明不能替代 Suite overview；无权访问、版本失效或加载失败时，只保留合规的成员状态或跳转入口。

### 12. Web 将导入作为现有创建新版本流程的一种方式

“创建 Suite”和“创建新版本”均先选择“从技能市场组合”或“从本地导入”。手工组合保留现有页面
和 API；本地导入进入上传、差异预览、明确确认和操作进度。更新预览必须突出显示因未出现在完整
Manifest 中而被移除的成员。确认文案列出将创建的 Skill/SkillVersion 数量、复用和引用数量、
移除数量以及最终只创建 Suite 草稿这一结果。

进度页逐成员展示创建、扫描、审核、复用、引用、失败和阻塞状态，并提供允许范围内的重试、取消、
审核详情和最终草稿入口。页面刷新或重新登录后可以依靠操作 ID 恢复进度，不依赖仍保留在浏览器
内存中的文件。

## Risks / Trade-offs

- **引用的公开 Skill 后续可能失效** → 创建 Suite 草稿前重新校验；发布后继续使用现有 degraded
  机制。
- **Bundle 阻塞后可能留下独立成员版本** → 展示每个成员结果；重试和取消不得破坏成员生命周期。
- **事件可能重复、丢失或乱序** → 使用幂等协调和有界恢复任务。
- **预览后权限可能变化** → 每个异步边界重新检查对应权限，失败时阻止而不是放宽权限。
- **并发创建可能争用坐标，更新可能争用版本** → 操作记录独占目标 Suite 坐标和版本，最终创建
  草稿时重新检查唯一性并保留乐观锁。
- **创建模式的某些 Skill 已发布、Suite 最终失败** → 独立 Skill 生命周期不回滚；进度页明确展示，
  重试只继续未完成工作。
- **恶意归档可能消耗 CPU 或内存** → 流式处理、限制解压与文件规模，每个包只计算一次 hash。
- **反向引用可能泄露私有成员构成** → 服务端过滤 Suite 和兄弟成员，只暴露不可访问成员数量。
- **新必填展示信息可能阻塞历史 Suite 更新** → 历史发布快照保持可读，仅在提交新版本时要求补齐。
- **普通作者可能尝试自助配置特权 Label** → 复用现有 Label 权限类型并由服务端执行。

## Migration Plan

1. 增加操作表和索引，不修改现有 Skill/Suite 生命周期表。
2. 先部署预览和状态读取，再启用确认接口与协调任务。
3. 所有实例均能识别操作记录和协调事件后，再开启确认。
4. Bundle 进度、Suite Label/展示信息校验和任意成员反向引用可以独立部署；`/search` 与
   `/suites` 路由保持不变。
5. 回滚时关闭新增接口和任务。已有 SkillVersion、SuiteVersion 继续有效；非终态操作保留，供兼容
   版本取消或过期处理。
