## Purpose

确保每个新发布 Suite 都有可理解、可发现的摘要、概述和自有 Label，同时不修改被引用成员 Skill
的元数据或版本别名。

## ADDED Requirements

### Requirement: REQ-SMT-01 新 Suite 发布 SHALL 要求摘要和概述

SuiteVersion DRAFT 可以在展示信息未完成时保存，但提交审核、PRIVATE 直接发布和审核批准时，
summary 与 Markdown overview SHALL 均为非空。服务端 SHALL 独立于 Web 校验执行该规则。

#### Scenario: 保存展示信息不完整的草稿
- **WHEN** 有权限的作者尚未完成 summary 或 overview
- **THEN** DRAFT 可以保存并继续编辑
- **AND** 作者可以看到其未完成状态

#### Scenario: 提交缺少摘要的 Suite
- **WHEN** 作者提交或直接发布 summary 为空的 SuiteVersion
- **THEN** 系统以可操作的摘要必填提示拒绝该动作
- **AND** SuiteVersion 保持 DRAFT

#### Scenario: 提交缺少概述的 Suite
- **WHEN** 作者提交或直接发布 overview 为空的 SuiteVersion
- **THEN** 系统以可操作的概述必填提示拒绝该动作
- **AND** SuiteVersion 保持 DRAFT

#### Scenario: 审核批准时展示信息已不合规
- **WHEN** PENDING_REVIEW SuiteVersion 在批准时不再满足展示信息要求
- **THEN** 系统拒绝批准且不发布该 SuiteVersion

### Requirement: REQ-SMT-02 摘要和概述 SHALL 承担不同展示职责

summary SHALL 为套件专区、卡片和所属 Suite 引用提供简明说明。overview SHALL 说明 Suite 用途、
成员职责或使用顺序、预期输入输出和重要使用边界。发布后的展示 SHALL 使用作者提供的内容，不得
静默使用泛化生成文案冒充真实内容。

#### Scenario: 展示信息完整的已发布 Suite
- **WHEN** 用户在套件专区或详情查看新发布 Suite
- **THEN** 卡片和引用展示 summary
- **AND** 详情将 Markdown overview 与成员列表分开呈现

#### Scenario: 通用兜底文案不算作者内容
- **WHEN** SuiteVersion 没有作者提供的 summary 或 overview
- **THEN** “精选技能组合”等通用文案不能通过发布校验

#### Scenario: 编辑 Suite 概述
- **WHEN** 作者创建或编辑 SuiteVersion
- **THEN** 编辑器提供适用场景、使用准备、成员分工或顺序、输入输出和注意事项的结构化写作提示
- **AND** 系统不以机械字数门槛替代内容完整性，也不自动复制成员文档充当概述

### Requirement: REQ-SMT-03 历史空展示信息 SHALL 保持兼容

本要求上线前已经发布的 SuiteVersion 即使 summary 或 overview 为空，仍 SHALL 保持可读取和可安装。
该 Suite 后续提交的新版本 SHALL 满足新的展示信息要求。

#### Scenario: 读取历史空概述 Suite
- **WHEN** 已有 PUBLISHED SuiteVersion 的展示信息为空
- **THEN** 用户仍可按照现有可见性和可用性规则查看与安装
- **AND** UI 如实提示缺少内容，不得编造说明

#### Scenario: 创建历史 Suite 的下一个版本
- **WHEN** 作者基于展示信息为空的历史快照提交新 SuiteVersion
- **THEN** 发布前必须补齐非空 summary 和 overview

### Requirement: REQ-SMT-04 Bundle 导入 SHALL 解析完整展示信息

Bundle 预览 SHALL 展示目标 SuiteVersion 最终使用的 summary 和 overview。Manifest 可以提供新值，
也可以继承当前 SuiteVersion 的非空值；最终任一字段为空时 SHALL NOT 允许确认。

#### Scenario: 继承当前完整展示信息
- **WHEN** Bundle 未填写展示信息，且当前 SuiteVersion 的 summary 和 overview 均非空
- **THEN** 预览将继承值作为目标展示信息展示

#### Scenario: 当前 Suite 的概述为空
- **WHEN** Bundle 未提供 overview，且当前 SuiteVersion overview 也为空
- **THEN** 预览报告阻塞性展示信息错误
- **AND** 用户必须更新 Manifest 或 Suite 草稿后才能确认

#### Scenario: 通过 Bundle 创建 Suite
- **WHEN** 创建模式的 Manifest 没有提供非空 summary 或 overview
- **THEN** 预览报告阻塞性展示信息错误
- **AND** 不从 Entry Skill 的 `SKILL.md` 自动生成或复制 Suite 概述

### Requirement: REQ-SMT-05 Suite SHALL 支持自己的 Label

系统 SHALL 允许有权限的用户把已有 Registry LabelDefinition 关联到 Suite 容器或解除关联。
Suite Label SHALL 使用独立于成员 Skill Label 的关联，并 SHALL NOT 创建或修改 SuiteVersion。

#### Scenario: 为 Suite 添加普通 Label
- **WHEN** 有权限的 Suite 管理者添加一个允许使用的普通 Label
- **THEN** 该 Label 出现在 Suite 及其发现投影中
- **AND** 所有成员 Skill Label 保持不变

#### Scenario: 删除 Suite Label
- **WHEN** 有权限的 Suite 管理者解除一个 Suite Label
- **THEN** 只删除 Suite-to-Label 关联
- **AND** SuiteVersion 和成员元数据保持不变

#### Scenario: Suite 引用了其他人的 Skill
- **WHEN** 带 Label 的 Suite 包含非本人所有的引用 Skill
- **THEN** 除非该 Label 在 Skill 上被独立配置，否则不会出现在这些 Skill 上

### Requirement: REQ-SMT-06 Suite Label 权限 SHALL 复用现有 Label 权限类型

Suite Label 变更 SHALL 同时检查 Suite 管理权限和现有 LabelDefinition 权限类型。PRIVILEGED Label
继续仅允许现有 Label 策略授权的平台角色操作。读取响应 SHALL 按现有可见规则过滤 LabelDefinition。

#### Scenario: 普通管理者尝试配置特权 Label
- **WHEN** Suite 管理者没有使用 PRIVILEGED Label 的平台权限
- **THEN** 系统拒绝变更且不修改 Suite 或成员

#### Scenario: 平台管理员配置特权 Label
- **WHEN** 获得授权的平台角色为可管理 Suite 添加 PRIVILEGED Label
- **THEN** 系统建立关联并记录实际操作者

### Requirement: REQ-SMT-07 套件专区 SHALL 展示并筛选 Suite Label

套件专区卡片和详情 SHALL 展示 Suite 直接关联的可见 Label。套件专区 SHALL 支持按 Suite Label
筛选，不得修改技能市场筛选，也不得把成员 Skill Label 当成 Suite Label。

#### Scenario: 按 Label 筛选套件专区
- **WHEN** 用户在套件专区选择一个可见 Label
- **THEN** 结果只匹配直接关联该 Label 的可见 Suite
- **AND** 不会因为某个成员 Skill 有该 Label 就匹配 Suite

#### Scenario: Skill 与 Suite 使用同一个 LabelDefinition
- **WHEN** 同一个 LabelDefinition 分别关联 Skill 和 Suite
- **THEN** 技能市场筛选 Skill 关联
- **AND** 套件专区筛选 Suite 关联

### Requirement: REQ-SMT-08 Suite SHALL NOT 支持 SkillVersion Tag

本变更 SHALL NOT 创建 Suite Tag，也不向成员批量设置 Tag。现有 Skill Tag 继续指向精确 PUBLISHED
SkillVersion，并保留系统现有 `latest` 行为。

#### Scenario: 管理 Suite 元数据
- **WHEN** 作者修改 Suite Label、summary 或 overview
- **THEN** 不创建、移动或删除任何 Skill Tag

### Requirement: REQ-SMT-09 Suite Label 读取 SHALL 批量完成并可审计

Suite 列表和搜索 SHALL 以集合方式加载 Label 投影，不得逐 Suite 查询。每次 Suite Label 变更
SHALL 记录操作者、Suite ID、Label、动作和请求关联信息，不得记录成员包内容。

#### Scenario: 展示一页带 Label 的 Suite
- **WHEN** 套件专区展示一页结果
- **THEN** 可见 LabelDefinition 和关联通过有界集合查询完成

#### Scenario: 审计 Suite Label 变更
- **WHEN** Suite 添加或删除 Label
- **THEN** 审计记录指向 Suite，而不是任何成员 Skill

### Requirement: REQ-SMT-10 Entry Skill 说明 SHALL 与 Suite 概述分开展示

Suite 详情 MAY 在独立折叠区域按需展示 Entry Skill 固定 SkillVersion 的 `SKILL.md`，但 SHALL NOT
将其作为 Suite overview 的替代或兜底。内容 SHALL 标明精确坐标和版本，并复用现有版本读取权限、
内容安全策略和 Markdown 渲染。

#### Scenario: 用户展开可读的 Entry Skill 说明
- **WHEN** 当前用户有权读取 Entry Skill 固定版本并主动展开说明
- **THEN** 页面延迟加载并展示该精确 SkillVersion 的 `SKILL.md`
- **AND** 明确标注内容来源坐标和版本

#### Scenario: Entry Skill 存在更新版本
- **WHEN** Suite 固定的 Entry SkillVersion 不是该 Skill 的最新版本
- **THEN** 页面仍读取 Suite 固定版本的说明
- **AND** 不使用 `latest` 内容替换固定快照

#### Scenario: Entry Skill 不可读或内容加载失败
- **WHEN** 当前用户无权读取、固定版本失效或说明加载失败
- **THEN** 页面不展示 `SKILL.md` 内容
- **AND** Suite 作者编写的 overview 和经过权限过滤的成员状态仍可正常展示
