---
name: skillhub-design
description: SkillHub 页面、主题和组件视觉工作的默认入口。自动读取已审阅主题、完整组件契约和设计规范，核对 React 实现；纯数据逻辑不加载视觉资料，Figma 仅在用户选择时使用。
---

# SkillHub 设计系统

先读 `.trellis/spec/frontend/index.md` 与 `design-system.md`，并按 `.trellis/spec/workflow/context-reading.md` 逐页读取和恢复。本 Skill 编排读取，
设计值由所选设计库维护，工程约束由项目规范维护。纯 API/数据逻辑不加载视觉资产。

Agent 自行准备入口，无需用户点名。优先使用分支内 `scripts/read-design-system.py`；不存在时使用本机同步准备的 `.trellis/scripts/read-design-system.py`。
工具自动选择项目本地库或获准登记的私有快照。两者都没有时明确缺失，不能伪称符合目标或回退旧值。

从仓库根目录使用只读工具（下面路径按实际存在的入口选择）：

```sh
python3 scripts/read-design-system.py check
python3 scripts/read-design-system.py theme control
python3 scripts/read-design-system.py component button
python3 scripts/read-design-system.py spec task-composition
```

默认库为 `.design_library/skillhub/`，也可在子命令前指定 `--library PATH`。
缺库或校验失败时明确报告，不编造默认设计值。大契约逐组件读取并确认输出完整，默认小分页，续页必须传 `--offset N --expect-sha256 HASH`，按 nextOffset 取得同一 payloadSha256 的全部正文；
每次工具输出只取一个有界分段，避免把多个大分段合并后再次截断。
不要把完整组件索引交给有单文件截断限制的上下文注入器。

- 主题：读取亮暗值与相关规范，再核对 CSS / Tailwind 的现有映射。
- 组件：读取完整契约、实际源码、调用方和测试；保留受控状态、键盘、焦点与权限。
- 页面：追加 task-composition，按用户任务组合，不照搬原型演示数据或业务能力。
- 检查：使用实现阶段相同的来源摘要，按改动影响运行工程检查与浏览器验收。只读审查也先核对正文送达；可用 `python3 scripts/read-context.py check --source .trellis/spec/frontend/design-system.md`，再追加本次组件查询。该检查自动展开必需关联；缺项先补读或保留未验证。只读任务不必为此写文件。

Figma 是可选辅助，默认不启用；主题、组件与规范的读取和前端实现不依赖画板。
用户选择使用 Figma 时，读取 `.trellis/spec/frontend/design-evidence.md`，使用
`$figma-web-bridge`。按该 Skill 检查实际桥接工具、relay、本次 channel 和插件响应；
先确定文件与节点，不猜测链接或 ID。不自动调用官方 Figma MCP，也不将其作为桥接失败的回退。
Figma 图稿、结构回读、视觉审查和真实产品验收分别记录，复用已有任务与归档入口。
