---
name: prd
description: "为新功能生成 Product Requirements Document (PRD)。在规划功能、启动新项目或需要创建 PRD 时使用。触发词：创建一个prd"
---

# PRD Generator

创建清晰、可执行且适合实施的详细 Product Requirements Document。

---

## 工作流程

1. 接收用户的功能描述
2. 提出 3-5 个关键的澄清问题（带字母选项）
3. 根据答案生成结构化的 PRD
4. 保存到 `tasks/prd-[feature-name].md`

**重要提示：** 不要开始实施。只需创建 PRD。

---

## 步骤 1：澄清问题

仅在初始提示不明确时提出关键问题。重点关注：

- **问题/目标：** 这解决了什么问题？
- **核心功能：** 关键操作是什么？
- **范围/边界：** 它不应该做什么？
- **成功标准：** 我们如何知道它已完成？

### 问题格式如下：

```
1. 这个功能的主要目标是什么？
   A. 改善用户 onboarding 体验
   B. 提高用户 retention
   C. 减少 support 负担
   D. 其他：[请说明]

2. 目标用户是谁？
   A. 仅新用户
   B. 仅现有用户
   C. 所有用户
   D. 仅管理员用户

3. 范围是什么？
   A. 最小可行版本 (Minimal viable version)
   B. 完整功能实现 (Full-featured implementation)
   C. 仅 backend/API
   D. 仅 UI
```

这样用户可以快速回复 "1A, 2C, 3B" 进行快速迭代。

---

## 步骤 2：PRD 结构

生成包含以下部分的 PRD：

### 1. 介绍/概述
简要描述功能及其解决的问题。

### 2. 目标
具体、可衡量的目标（列表形式）。

### 3. User Stories
每个 story 需要包含：
- **标题：** 简短的描述性名称
- **描述：** "作为 [用户]，我想要 [功能]，以便 [收益]"
- **Acceptance Criteria：** 可验证的"完成"标准清单

每个 story 应该足够小，可以在一次专注的会话中实现。

**格式：**
```markdown
### US-001: [标题]
**描述：** 作为 [用户]，我想要 [功能]，以便 [收益]。

**Acceptance Criteria：**
- [ ] 具体的可验证标准
- [ ] 另一个标准
- [ ] Typecheck/lint 通过
- [ ] **[仅 UI stories]** 使用 agent-browser 在浏览器中验证
```

**重要提示：** 
- Acceptance criteria 必须是可验证的，不能模糊。"工作正常"是不好的。"删除前按钮显示确认对话框"是好的。
- **对于任何有 UI 变更的 story：** 始终将"使用 agent-browser 在浏览器中验证"作为 acceptance criteria。这确保了对 frontend 工作的视觉验证。

### 4. Functional Requirements
具体功能的编号列表：
- "FR-1: 系统必须允许用户..."
- "FR-2: 当用户点击 X 时，系统必须..."

要明确且无歧义。

### 5. Non-Goals（超出范围）
此功能将不包括的内容。对管理范围至关重要。

### 6. Design Considerations（可选）
- UI/UX 要求
- 如有可用，提供 mockups 链接
- 可重用的相关现有 components

### 7. Technical Considerations（可选）
- 已知的约束或依赖
- 与现有系统的集成点
- Performance 要求

### 8. Success Metrics
如何衡量成功？
- "将完成 X 的时间减少 50%"
- "将 conversion rate 提高 10%"

### 9. Open Questions
剩余的问题或需要澄清的领域。

---

## 为初级开发者编写

PRD 的读者可能是初级开发者或 AI agent。因此：

- 要明确且无歧义
- 避免行话或解释它
- 提供足够的细节以理解目的和核心逻辑
- 为便于参考，对需求进行编号
- 在有用时使用具体示例

---

## 输出

- **格式：** Markdown (`.md`)
- **位置：** `tasks/`
- **文件名：** `prd-[feature-name].md` (kebab-case)

---

## PRD 示例

```markdown
# PRD: Task Priority System

## Introduction

为任务添加优先级级别，以便用户专注于最重要的事情。任务可以标记为高、中或低优先级，带有视觉指示器和过滤功能，帮助用户有效管理工作负载。

## Goals

- 允许为任何任务分配优先级（high/medium/low）
- 提供优先级级别之间的清晰视觉区分
- 支持按优先级过滤和排序
- 新任务默认为 medium 优先级

## User Stories

### US-001: 向 database 添加 priority 字段
**描述：** 作为开发者，我需要存储任务优先级，以便它在会话之间持久化。

**Acceptance Criteria：**
- [ ] 向 tasks 表添加 priority 列：'high' | 'medium' | 'low' (默认 'medium')
- [ ] 成功生成并运行 migration
- [ ] Typecheck 通过

### US-002: 在 task cards 上显示 priority 指示器
**描述：** 作为用户，我想一眼看到任务优先级，以便我知道首先需要注意什么。

**Acceptance Criteria：**
- [ ] 每个 task card 显示彩色 priority badge（红色=high，黄色=medium，灰色=low）
- [ ] 无需悬停或点击即可看到 priority
- [ ] Typecheck 通过
- [ ] 使用 agent-browser 在浏览器中验证

### US-003: 向 task edit 添加 priority 选择器
**描述：** 作为用户，我想在编辑任务时更改任务的优先级。

**Acceptance Criteria：**
- [ ] task edit modal 中的 priority 下拉菜单
- [ ] 显示当前 priority 为选中状态
- [ ] 选择更改时立即保存
- [ ] Typecheck 通过
- [ ] 使用 agent-browser 在浏览器中验证

### US-004: 按 priority 过滤任务
**描述：** 作为用户，我想过滤任务列表，以便在我专注时只看到高优先级项目。

**Acceptance Criteria：**
- [ ] 带有选项的过滤下拉菜单：All | High | Medium | Low
- [ ] 过滤状态持久化在 URL params 中
- [ ] 没有任务匹配过滤条件时显示空状态消息
- [ ] Typecheck 通过
- [ ] 使用 agent-browser 在浏览器中验证

## Functional Requirements

- FR-1: 向 tasks 表添加 `priority` 字段（'high' | 'medium' | 'low'，默认 'medium'）
- FR-2: 在每个 task card 上显示彩色 priority badge
- FR-3: 在 task edit modal 中包含 priority 选择器
- FR-4: 在任务列表标题中添加 priority 过滤下拉菜单
- FR-5: 在每个 status 列内按 priority 排序（high 到 medium 到 low）

## Non-Goals

- 不包含基于 priority 的通知或提醒
- 不包含基于截止日期的自动 priority 分配
- 不包含 subtasks 的 priority 继承

## Technical Considerations

- 重用带有颜色变体的现有 badge component
- 通过 URL search params 管理过滤状态
- priority 存储在 database 中，不计算

## Success Metrics

- 用户可以在 2 次点击内更改 priority
- 高优先级任务立即在列表顶部可见
- 任务列表 performance 无回归

## Open Questions

- priority 是否应该影响列内的任务排序？
- 我们是否应该为 priority 更改添加键盘快捷键？
```

---

## 检查清单

保存 PRD 之前：

- [ ] 提出了带字母选项的澄清问题
- [ ] 融入了用户的答案
- [ ] User stories 小而具体
- [ ] Functional requirements 已编号且无歧义
- [ ] Non-goals 部分定义了清晰的边界
- [ ] 已保存到 `tasks/prd-[feature-name].md`
