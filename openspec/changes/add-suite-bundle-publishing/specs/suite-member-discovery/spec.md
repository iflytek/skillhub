## Purpose

保持技能市场和套件专区两个独立入口，同时让任意成员 Skill 都能安全展示所属 Suite，包括不是
Suite 入口的普通成员。

## ADDED Requirements

### Requirement: REQ-SMD-01 技能市场与套件专区 SHALL 保持独立

技能市场 SHALL 继续通过 `/search` 发现 Skill，并保留 Skill 搜索、Label、收藏和排序行为。
套件专区 SHALL 继续通过 `/suites` 发现 Suite，并使用类型化 Suite 搜索和 Suite 卡片。本变更
SHALL NOT 在技能市场增加 Skill/Suite 类型切换。

#### Scenario: 搜索技能市场
- **WHEN** 用户在 `/search` 搜索
- **THEN** 搜索结果仍然是 Skill
- **AND** 现有 Skill 筛选语义保持不变

#### Scenario: 搜索套件专区
- **WHEN** 用户在 `/suites` 搜索
- **THEN** 搜索结果仍然是当前用户可见的 Suite
- **AND** 结果链接到 Suite 详情

### Requirement: REQ-SMD-02 任意成员 Skill SHALL 展示当前用户可见的所属 Suite

Skill 详情 SHALL 只依据每个 ACTIVE、非隐藏 Suite 当前 `latestVersionId` 指向的 PUBLISHED
SuiteVersion 判断是否包含当前 Skill，不再只查询当前 Skill 是 Entry Skill 的情况。每条引用
SHALL 标明当前 Skill 是否为 Entry Skill；已从当前 latestVersionId 快照移除的 Skill 不得继续展示
该 Suite，即使历史版本仍包含它。

#### Scenario: 当前 Skill 是普通成员
- **WHEN** 当前用户可见的最新 SuiteVersion 将该 Skill 作为非 Entry 成员
- **THEN** Skill 详情展示该 Suite 和精确 Suite 版本
- **AND** 标明当前 Skill 是普通成员

#### Scenario: 当前 Skill 是 Entry 成员
- **WHEN** 当前 Skill 是当前用户可见 SuiteVersion 的 Entry Skill
- **THEN** 所属 Suite 信息保留明确的 Entry 标记

#### Scenario: 引用该 Skill 的 Suite 不可见
- **WHEN** Suite 属于其他人的 PRIVATE 内容，或者已隐藏、归档、未发布、当前用户无权访问
- **THEN** Skill 详情既不暴露 Suite 坐标，也不暴露成员关系

#### Scenario: Skill 只存在于 Suite 历史版本
- **WHEN** 当前 Skill 存在于历史 PUBLISHED SuiteVersion，但不在 Suite 当前 latestVersionId 快照中
- **THEN** Skill 详情不再把该 Suite 显示为当前所属 Suite

### Requirement: REQ-SMD-03 所属 Suite 信息 SHALL 保护兄弟成员元数据

所属 Suite 信息 SHALL 保护兄弟成员元数据。它可以展示紧凑的兄弟成员列表，但只允许返回当前用户有权读取的 Skill。不可访问或已删除
的兄弟成员只能按数量表示，不得暴露坐标、名称、摘要、版本、fingerprint 或下载信息。

#### Scenario: 所有兄弟成员均可见
- **WHEN** 当前用户可以读取所属 Suite 的全部成员
- **THEN** 所属 Suite 信息可以按顺序展示紧凑成员列表和 Entry 标记

#### Scenario: 存在不可访问的兄弟成员
- **WHEN** 当前用户不能读取至少一个兄弟成员
- **THEN** 返回结果省略该成员的身份信息
- **AND** 只能选择性展示受限或不可用成员数量

### Requirement: REQ-SMD-04 Suite 成员反向发现 SHALL 控制查询规模

所属 Suite 引用 SHALL 分页或设置明确上限，并通过集合查询完成，不得为每个 Suite 或兄弟成员
分别发起查询。

#### Scenario: 一个 Skill 属于大量 Suite
- **WHEN** 引用该 Skill 的 Suite 数量超过详情响应上限
- **THEN** 响应使用确定性上限或分页契约
- **AND** 提供继续查看其他结果所需的信息
