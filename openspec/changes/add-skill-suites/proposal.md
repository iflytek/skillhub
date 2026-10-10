## Why

SkillHub 当前只能逐个发布和安装 Skill，无法把一组已经发布、彼此协作的 Skill 作为一个可版本化、可审核、可一次安装的产品交付。Issue #715 提出的真实需求是保留成员 Skill 的独立性，同时为团队工作流、入门工具集和专家套件提供稳定的集合身份、成员快照和安装生命周期。

## What Changes

- 新增 Namespace 所有的 `SkillSuite` 及不可变的 `SkillSuiteVersion`。
- SuiteVersion 只引用当前 Registry 中已经发布的精确 SkillVersion，不重新上传、创建、扫描或发布成员 Skill。
- Skill 与 Suite 使用类型化身份；同一 Namespace 下允许二者使用相同 slug，同类型 Suite 仍保持 `(namespace, slug)` 唯一。
- 新增 Suite 创建、编辑、审核、发布、下架、归档、查询和安装接口；新增显式类型化的资源发现入口，现有 Skill 搜索接口继续只返回 Skill。
- 新增 `skillhub suite install/check/upgrade/remove`，成员继续安装为标准 Agent Skill；Suite 本身不生成同名 `SKILL.md`。
- Suite 安装采用完整预检、全部暂存、fingerprint 校验和整体提交/回滚，避免部分安装。
- CLI inventory 记录 Suite 快照以及每个成员的直接安装和 Suite 来源，安全处理共享成员的卸载。
- 定义人类可编辑的 `suite.yaml` 交换格式，为后续 CLI 导入预留稳定边界；v1 仍通过 Web/API 创作，不改变现有单 Skill ZIP 协议。

## Decision Relative to Issue #715

本变更保留 Issue #715 的核心目标——版本化管理一组 Skill 并一次安装——但有意调整原提案中的三项实现：

- 不接受一份 Suite ZIP 自动创建多个 Skill；v1 只组合已经独立发布的 SkillVersion，避免部分发布、所有权冲突和重复扫描。
- 不为每个 Member 复制审核任务；Member 已经完成自身审核，Suite 只审核集合元数据、成员关系、可见性和可安装性。
- 不让既有 `skillhub install` 猜测资源类型；Suite 使用 `skillhub suite install`，保证旧 CLI 和 Skill/Suite 同 slug 时行为确定。

“批量发布多个 Skill 后创建 Suite”可以作为后续 CLI 编排能力，但不会改变上述领域模型。

## Capabilities

### New Capabilities

- `skill-suites`: 定义 Skill Suite 的身份、成员关系、版本与审核生命周期、可见性、查询、原子安装、升级和卸载行为。

### Modified Capabilities

无。仓库尚无已归档 OpenSpec capability；现有 Skill 发布和安装契约保持兼容。

## Impact

- **Domain / persistence**：新增 Suite 聚合、版本、成员快照和仓储；审核任务支持类型化目标。
- **API / OpenAPI**：新增 Suite 管理、审核、解析、安装计划和类型化资源发现接口；现有 Skill API 响应保持兼容；需要重新生成 Web 类型。
- **Web**：新增 Suite 列表、详情、编辑、版本和安装指引，并在搜索结果中展示 Skill/Suite 类型。
- **CLI**：新增 Suite 子命令、整组安装事务和 inventory schema 的向后兼容扩展。
- **Security / governance**：成员仍使用现有扫描和审核结果；Suite 只审核元数据与成员组成，不重复扫描成员包。
- **Compatibility**：现有 `skillhub install`、单 Skill ZIP、Skill URL 和已安装 Skill 不改变；旧 CLI 只是无法使用新 Suite 命令。
- **Out of scope**：嵌套 Suite、版本范围、跨 Registry 成员、可选成员、MCP/Hook/Agent 插件包、Suite 自动批量创建成员 Skill。
