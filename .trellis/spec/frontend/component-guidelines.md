# 组件复用与定制

先读[视觉基础](./visual-foundations.md)，再按需读[组件目录](./component-catalog.md)。
读取当前检出适用的 AGENTS/CLAUDE、[前端入口](./index.md)、架构文档、源码与测试；不存在的文件
不假称已读。不同检出的工程依赖或 API 有差异时先核对，不继承其他工作树的实现状态。

采用 shadcn/ui 源码体系，保留 React 19、Tailwind 3 与既有 Radix。
“不使用 shadcn 运行时库”不等于禁止参考官方源码；不安装另一套整包 UI 体系。
官方 shadcn Skill 提供 API/CLI 用法，项目规范提供设计与兼容边界。
默认按[设计系统采用](./design-system.md)完整读取相关组件契约；
契约定义目标外观，源码定义实际 API。新增档位或行为明确影响范围，不从 HTML 原型推断现有能力。

## 选择与放置

选择顺序：已有 shared/ui、shared/components、业务组件 → 兼容官方源码 →
布局组合 → 必要的共享变体或实现调整。图标复用 Lucide；合并用既有 cn；文案用 i18n。

| 需求 | 放置位置 |
|---|---|
| 单处宽度、排列、响应式 | 调用处 className |
| 多处复用的语义外观或尺寸 | 检查既有变体，再扩展基础组件变体 |
| 所有控件的默认样式、焦点、键盘 | shared/ui 实现，核对直接/间接调用方 |
| 全站字体、主题、密度 | CSS 变量与 Tailwind 映射 |
| 安装面板、技能卡片、业务权限 | 原业务目录，不塞进基础组件 |

例：单页面 `className="w-full sm:w-auto"` 合理。
例：每页重写整套按钮颜色和焦点方案会破坏一致性，应使用已有变体。
不要为文档树把已有效运行的业务组件搬到另一个 FSD 层。

## 官方组件适配

在当前 web 工程核对 CLI 实际版本和帮助，再执行 info 与代表组件 dry-run。
安装版本、命令、路径、依赖预告和执行结果写入任务记录，不固定成模板的永久版本。
核对 components/ui/utils/lib/hooks 与 TypeScript、Vite 别名，不能产生第二套 ui 目录。
兼容 Tailwind 3 的来源逐项核对；修改 components.json style 不等于迁移已有组件。
禁止无差别 init、批量 add 或覆盖定制源码。候选组件不意味着必须加依赖。

## 组件契约

保留原生属性、事件、ref、受控/非受控状态、className/style、标签、aria、表单语义、
Portal、滚动与焦点。先检查实际 API，不能把官方或其他工作树的属性当作本地已提供。
自有 Dialog/Tabs 即使外观相似，也不能宣称完整等价于 Radix。

Tabs 的 Tab 入口、方向键、Home/End、禁用项处理属于组件基础可用性。
存在 enabled 项而无有效选中项时，应保留 enabled 键盘入口；入口恢复不能自动选择或
触发业务回调。全部禁用时没有可操作入口。受控值、动态移除/禁用都需行为验证。
这不要求增加全站快捷键功能。

弹层验证 Escape、Tab 范围、焦点返回和嵌套 Portal。表单验证标签、错误关联与提交语义。
详细检查与证据规则见[验收规范](./quality-guidelines.md)。
