---
name: skillhub-design
description: SkillHub 页面、主题和组件视觉工作的入口。读取仓库随附的主题、完整组件契约、设计规范和实际 React API，按需加载并核对正文完整性。
---

# SkillHub 视觉工作

先读 `.trellis/spec/frontend/index.md` 和 `design-system.md`，按任务选择视觉基础、组件与质量规范。
按 `.trellis/spec/workflow/context-reading.md` 有界读取和恢复；无需用户手动触发本 Skill。

- 主题：读取 `design-system/skillhub` 中的主题与相关规范，再核对 `web/src/index.css` 与 `web/tailwind.config.ts` 的实际映射。
- 组件：读取核心包中所用组件的完整 contractData，再核对 shared/ui、调用方和测试，保留受控状态、键盘、焦点与权限。
- 页面：读取页面模式和相关业务契约，不从原型推断业务能力。
- 读取：使用 `scripts/read-design-system.py` 默认读取分支内核心资源；主题/组件/规范按任务选择并逐页补齐。只有用户指定其他资源时才使用 `--library PATH`，不隐式替换默认来源。
- 检查：使用实施阶段相同的适用规范，按影响运行工程检查及浏览器验收。只读审查同样核对必需正文，不能以未运行测试为由省略质量要求。

可用 `python3 scripts/read-context.py check --source .trellis/spec/frontend/design-system.md` 核对正文送达，
再追加本次实际依赖的来源。只读任务无需写任务清单。来源缺失、截断或变化时先补读，
不以目录、哈希或过去的检查通过替代当前完整正文，也不声称遵循未取得的外部设计契约。
