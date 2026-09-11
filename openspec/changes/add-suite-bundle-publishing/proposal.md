## Why

PR #828 已经完成 Suite 版本模型、套件专区搜索和原子安装，但创建或更新包含多个本地 Skill 的
Suite 时，维护者仍需逐个创建或上传 Skill，再手工把 Suite 固定到对应版本。Suite 还可能引用
维护者并不拥有的公开 Skill，因此 Bundle 发布必须区分“操作者有权创建或发布的包内容”和
“操作者只能复用的外部精确引用”。

## What Changes

- 为创建和更新 Suite 增加共用的两阶段导入：通过一个 ZIP，或受支持浏览器选择的一个根目录，
  一次提交多个 Skill 文件夹；先预览成员与发布动作，再明确确认。确认前不得创建 Skill、
  SkillVersion、Suite、SuiteVersion、扫描或审核任务。
- Manifest 使用两种成员形式描述完整的目标 Suite 快照：一种是操作者有权发布的已有 Skill 包，
  或操作者有权在目标 Namespace 创建的新 Skill 包；另一种是不修改、也不要求归 Suite 维护者
  所有的精确 PUBLISHED SkillVersion 引用。
- Bundle 先校验唯一 Manifest、成员目录边界和文件归属，再将每个目录独立还原为以 `SKILL.md`
  为根的普通 Skill 包，复用现有路径、大小、扩展名、内容签名、YAML、元数据和合规校验。任一成员
  存在阻塞错误时不得确认，也不得通过猜测目录或静默选择冲突字段继续。
- 新增持久化 Bundle 发布操作。只有具备独立创建或发布权限且携带包的成员进入现有校验、扫描和
  审核流程；所有包版本均为 PUBLISHED 且全部引用仍然有效后，才原子创建新 Suite 及首个草稿，
  或为已有 Suite 创建 DRAFT SuiteVersion。
- 未变化成员继续使用原精确版本，不创建版本、扫描或审核；支持添加、移除、调整顺序和显式重新
  固定引用成员版本。
- 任意成员 Skill 均可展示当前用户可见的所属 Suite，不再只支持 Entry Skill。
- 技能市场（`/search`）与套件专区（`/suites`）保持独立。#828 已经交付的套件专区和类型化 Suite
  搜索无需替换。
- 新 SuiteVersion 提交或发布前必须具备非空摘要和 Markdown 概述；不完整的 DRAFT 仍可保存，
  已发布历史数据仍可读取。
- Suite 可以配置自己的 Label，并复用 Registry 的 Label 定义和权限类型，用于套件专区筛选。
  Suite Label 不向成员 Skill 级联。Skill Tag 仍是指向 PUBLISHED SkillVersion 的版本别名，不引入 Suite。
- 暂不支持覆盖无权管理的已有 Skill、批量迁移 Namespace、按成员关键词提升 Suite 排名、CLI 上传
  Bundle、嵌套 Suite 和跨 Registry 成员。

## 相对 Issue #847 的需求取舍

| Issue 原提议 | 结论 | OpenSpec 决策 |
|---|---|---|
| 上传一个多 Skill 归档 | 保留 | 预览和确认复用一个带有效期的服务端操作，归档只上传一次。 |
| 每个 Suite 成员都必须在归档中有目录 | 调整 | 只有需要发布内容的成员才提供包目录；纯引用成员在 Manifest 中填写精确已发布版本。 |
| 被引用 Skill 必须属于 Suite 维护者 | 拒绝 | Suite 可以引用其他人发布的合规公开 Skill；引用权限与发布权限相互独立。 |
| 通过 fingerprint 判断包内容变化 | 保留并澄清 | 携带包的成员使用现有 Skill 规范化 fingerprint；纯引用成员比较精确 SkillVersion 身份。 |
| 未变化的包不升版、不重新扫描 | 保留 | 继续引用当前精确 SkillVersion。 |
| 变化的包沿用现有 Skill 发布流程 | 保留 | 仅限操作者已经具备该 Skill 的独立发布权限。 |
| 包成员变化后立刻创建 SuiteVersion | 调整 | 等全部包版本 PUBLISHED、全部引用仍有效后，再创建一个 DRAFT SuiteVersion；创建模式同时原子创建 Suite 容器。 |
| 消除 N 次上传和 N 个审核任务 | 调整 | 消除 N 次人工上传，但变化 Skill 仍独立审核，Suite 仍保留自身审核。 |
| Bundle 自动创建不存在的 Skill | 保留并收紧 | 仅允许在操作者具备 Skill 创建权限的 Namespace 中创建；坐标冲突、越权或 Namespace 不可写时阻塞整个预览。 |
| 批量设置 labels/tags | 调整 | 为 Suite 自身配置 Label，不向成员 Skill 扩散；Suite 不支持 SkillVersion Tag。 |
| 把 Suite 结果并入技能市场 | 拒绝 | 技能市场与套件专区保持分开；`/suites` 已提供 Suite 搜索和卡片。 |
| 成员关键词提升 Suite 排名 | 延后 | 相关性和隐私安全索引需要单独提案。 |
| 任意成员展示所属 Suite | 保留 | 仅返回最新、可见、ACTIVE、非隐藏、PUBLISHED 的 Suite 引用，并标明是否为 Entry。 |
| Suite 摘要或概述为空 | 调整 | DRAFT 可暂时不完整，但新提交或直接发布必须同时具备摘要和概述，发布页不能用泛化文案冒充。 |
| 保留 #828 精确引用模型 | 保持不变 | SuiteVersion 仍只引用精确 PUBLISHED SkillVersion，发布后保持不可变。 |

## Capabilities

### New Capabilities

- `suite-bundle-publishing`：从一个归档或根目录预览、确认、跟踪、恢复并完成 Suite 创建或更新，
  同时严格区分有权创建/发布的包内容与非本人所有的精确版本引用。
- `suite-member-discovery`：保持技能市场与套件专区独立，同时让任意成员 Skill 展示经过隐私过滤的
  所属 Suite。
- `suite-metadata`：要求 Suite 具备有效展示内容并支持 Suite 自有 Label，不修改成员 Skill 元数据
  或 SkillVersion Tag。

### Modified Capabilities

无。现有 `add-skill-suites` 变更尚未归档为主 OpenSpec capability；这些增量 capability 依赖它，
但不修改其精确引用和生命周期要求。

## Impact

- **领域与持久化**：新增 Bundle 操作、目标坐标占用、成员结果和 Suite-to-Label 关联；不增加
  SkillVersion 或 SuiteVersion 生命周期状态。
- **API / OpenAPI**：新增预览、确认、状态、取消、重试和 Suite Label 接口；扩展 Skill 详情中的
  Suite 引用和套件专区标签筛选；技能与套件发现 API 继续分开。
- **对象存储**：按操作临时保存一个上传归档，支持过期和补偿清理；日志不得保存归档内容。
- **安全与治理**：复用包校验、Scanner、Namespace 权限、单 Skill 审核、Suite 审核和审计规则；
  管理 Suite 不会获得被引用 Skill 的发布权限。
- **性能**：每个携带包成员只解压和计算一次 hash；批量读取引用与权限；未变化成员不扫描；限制
  归档和成员规模；反向引用不得产生 N+1 查询。
- **Web**：在“创建 Suite”和“创建新版本”中增加手工组合/本地导入选择，新增 Bundle 差异预览、
  权限提示和进度页；增加所属 Suite、发布信息校验、Suite Label，以及按需展开的 Entry Skill
  固定版本说明。技能市场与套件专区导航保持不变。
- **兼容性**：现有单 Skill 发布、单 Skill Label/Tag 管理、Suite 管理和安装 API、旧客户端以及
  已发布 SuiteVersion 快照保持不变。

## 分阶段交付

OpenSpec 保留完整产品方向，但实现和 PR 按以下边界拆分，后续阶段不得绕过第一阶段建立的权限、
生命周期和性能约束：

1. Suite 创建/更新 Bundle、成员格式验证、权限重检、生命周期协调和 Web 进度闭环。
2. 基于 Suite 当前 `latestVersionId` 的任意成员反向发现。
3. Suite Label、展示信息发布校验、概述模板和 Entry Skill 固定版本说明。
