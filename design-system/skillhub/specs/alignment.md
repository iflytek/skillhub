# 设计库 ↔ 产品实现对齐表

> 设计目标与产品实现的差异记录，不修改产品源码。2026-11-10 核对源码快照 `a022279986a1436e95b5398db3870d78a25595f5`；来源：`web/src/index.css`、`web/tailwind.config.ts`、`web/src/shared/ui/`。颜色为声明值换算，几何为源码推导；未运行浏览器测量，不代表生产环境。

## 0. 结论先行

- 本轮把对照样本从上一轮的 25 行扩到 **70 行**（亮色颜色 24 + 暗色颜色 14 + 几何/字体/阴影 32）。
- 对照包含源码声明比较与待浏览器验证项；各节统计见对应表后，不把静态推导当作实测。
- 上一轮的 `8 一致 / 9 近似 / 8 漂移` 是 25 行抽样，样本不同，**不能与本轮数字直接比较**；趋势一致：漂移集中在文字层级、语义色、暗色表面与组件几何。
- 主要差异包括文字层级、语义色、字体目标及组件几何。表格最终行高尚待浏览器验证；不能把所有语义色差异概括为产品更鲜艳。
- §5–6 保留历史组件候选，引用次数未重算，不能据此认定当前缺失；独立封装、业务实现与设计契约分别判断。

## 1. 判定口径

| 判定 | 定义 | 处理 |
|---|---|---|
| **严格一致** | 色值完全相同，或产品 Tailwind 值由同一 CSS 变量直接推导 | 无需动作；作为库的“锚点” |
| **近似** | 任一 RGB 通道差 ≤ 12，色相方向一致；**几何差 ≤ 2px** | 记录差异，不强制改；换主题时逐屏复核 |
| **漂移** | 任一通道差 > 12，或色相/饱和度方向不同，或产品无对应变量，或**几何差 > 2px** | 记录到当前变更的差异清单，按第 7 节顺序处理 |

几何类（高度/间距/圆角/内边距）与颜色类共用同一套两档阈值，不再单独放宽；2px 是肉眼在 1x 屏上开始可辨的分界。

## 2. 亮色颜色 token（24 项）

| 库 token | 库值 | 产品实现 | 产品值 | 判定 | 漂移原因 / 建议动作 |
|---|---|---|---|---|---|
| `--sh-bg-page` | `#ffffff` | `--background: 0 0% 100%` | `#ffffff` | 严格一致 | 声明色值一致。 |
| `--sh-bg-raised` | `#ffffff` | `--card: 0 0% 100%` | `#ffffff` | 严格一致 | 声明色值一致。 |
| `--sh-bg-overlay` | `#ffffff` | `--popover: 0 0% 100%` | `#ffffff` | 严格一致 | 声明色值一致。 |
| `--sh-bg-sunken` | `#f6f6f7` | `--secondary: 240 3% 97%` | `#f7f7f8` | 近似 | 最大 RGB 通道差 1；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-bg-muted` | `#efeff0` | `--muted: 240 3% 94%` | `#efeff0` | 严格一致 | 声明色值一致。 |
| `--sh-bg-inverse` | `#17191d` | 无对应变量 | — | 漂移 | 产品没有深色反底表面；库为暗色/代码区预留 |
| `--sh-text-primary` | `#171717` | `--foreground: 0 0% 0%` | `#000000` | 漂移 | 最大 RGB 通道差 23；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-text-secondary` | `#575757` | `--text-secondary: 0 0% 45%` | `#737373` | 漂移 | 最大 RGB 通道差 28；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-text-tertiary` | `#737373` | `--muted-foreground: 0 0% 45%` | `#737373` | 严格一致 | 声明色值一致。 |
| `--sh-text-placeholder` | `#767676` | `--text-placeholder: 0 0% 45%` | `#737373` | 近似 | 最大 RGB 通道差 3；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-text-disabled` | `#949494` | 无变量（用 `opacity-50`） | — | 漂移 | 产品用透明度表达禁用，暗色下不可控；建议补 token |
| `--sh-border-subtle` | `#f0f0f0` | `--border-card: 0 0% 94%` | `#f0f0f0` | 严格一致 | 声明色值一致。 |
| `--sh-border-default` | `#e5e5e5` | `--border: 0 0% 92%` | `#ebebeb` | 近似 | 最大 RGB 通道差 6；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-border-strong` | `#d4d4d4` | 无对应变量 | — | 漂移 | 产品只有 border 一档；建议补 strong/contrast |
| `--sh-border-focus` | `#171717` | `--ring: 0 0% 13%` | `#212121` | 近似 | 最大 RGB 通道差 10；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-primary-base` | `#212121` | `--primary: 0 0% 13%` | `#212121` | 严格一致 | 声明色值一致。 |
| `--sh-primary-foreground` | `#ffffff` | `--primary-foreground: 0 0% 100%` | `#ffffff` | 严格一致 | 声明色值一致。 |
| `--sh-primary-hover` | `#3a3a3a` | `hover:bg-primary/92` | 背景参与 alpha 合成，无单一固定值 | 待浏览器验证 | 不能将旧估算色值当作声明值；亮暗及不同表面分别验证 |
| `--sh-accent-base` | `#eef4fc` | `--accent: 214 70% 96%` | `#eef4fc` | 严格一致 | 声明色值一致。 |
| `--sh-accent-foreground` | `#171717` | `--accent-foreground: 0 0% 7%` | `#121212` | 近似 | 最大 RGB 通道差 5；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-success-base` | `#1f7a4d` | `--success: 160 60% 28%` | `#1d7256` | 近似 | 最大 RGB 通道差 9；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-warning-base` | `#9a6a00` | `--warning: 38 90% 50%` | `#f29e0d` | 漂移 | 最大 RGB 通道差 88；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-danger-base` | `#b42318` | `--destructive: 0 55% 48%` | `#be3737` | 漂移 | 最大 RGB 通道差 31；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-info-base` | `#14618f` | `--info: 217 75% 45%` | `#1d5fc9` | 漂移 | 最大 RGB 通道差 58；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |

**小计：严格一致 9 / 近似 6 / 漂移 8 / 待浏览器验证 1。**

## 3. 暗色颜色 token（14 项）

| 库 token | 库值 | 产品实现 | 产品值 | 判定 | 漂移原因 / 建议动作 |
|---|---|---|---|---|---|
| `--sh-bg-page` | `#101010` | `--background: 0 0% 6%` | `#0f0f0f` | 近似 | 最大 RGB 通道差 1；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-bg-raised` | `#181818` | `--card: 0 0% 10%` | `#1a1a1a` | 近似 | 最大 RGB 通道差 2；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-bg-overlay` | `#222222` | `--popover: 0 0% 11%` | `#1c1c1c` | 近似 | 最大 RGB 通道差 6；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-bg-muted` | `#2a2a2a` | `--muted: 0 0% 18%` | `#2e2e2e` | 近似 | 最大 RGB 通道差 4；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-text-primary` | `#f4f4f4` | `--foreground: 0 0% 94%` | `#f0f0f0` | 近似 | 最大 RGB 通道差 4；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-text-secondary` | `#b9b9b9` | `--text-secondary: 0 0% 75%` | `#bfbfbf` | 近似 | 最大 RGB 通道差 6；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-text-tertiary` | `#909090` | `--muted-foreground: 0 0% 68%` | `#adadad` | 漂移 | 最大 RGB 通道差 29；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-border-default` | `#303030` | `--border: 0 0% 20%` | `#333333` | 近似 | 最大 RGB 通道差 3；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-border-focus` | `#e6e6e6` | `--ring: 0 0% 85%` | `#d9d9d9` | 漂移 | 最大 RGB 通道差 13；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-primary-base` | `#ededed` | `--primary: 0 0% 94%` | `#f0f0f0` | 近似 | 最大 RGB 通道差 3；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-accent-base` | `#232324` | `--accent: 0 0% 22%` | `#383838` | 漂移 | 最大 RGB 通道差 21；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-success-base` | `#83c3a5` | `--success: 160 50% 48%` | `#3db88f` | 漂移 | 最大 RGB 通道差 70；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-warning-base` | `#cdb17a` | `--warning: 38 90% 60%` | `#f5b13d` | 漂移 | 最大 RGB 通道差 61；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |
| `--sh-danger-base` | `#e3a6a0` | `--destructive: 0 45% 48%` | `#b14343` | 漂移 | 最大 RGB 通道差 99；产品已有变量，按设计目标评审取值；实际对比度另在渲染页面验证。 |

**小计：近似 8 / 漂移 6。**

## 4. 几何、字体、阴影（32 项）

| 库 token / 契约 | 库值 | 产品实现 | 产品值 | 判定 | 漂移原因 / 建议动作 |
|---|---|---|---|---|---|
| `--sh-font-sans` | PingFang SC 起始系统栈 | `index.css` 的 `--font-sans` / body | Inter 起始系统栈 | 漂移 | 是库与产品的目标字体差异，不是产品存在两套正文字体 |
| `--sh-font-sans` | 同上 | `tailwind.config.ts → sans/display/heading` | 均引用 `var(--font-sans)` | 漂移 | 产品内部已同源；采用设计字体时修改统一变量并验证跨平台回退 |
| `--sh-font-mono` | JetBrains Mono | `index.css` | JetBrains Mono | 严格一致 | — |
| `--sh-radius-sm` | 8px | Tailwind `sm: calc(--radius - 2px)` | 8px | 严格一致 | — |
| `--sh-radius-md` | 10px | Tailwind `md: var(--radius)` | 10px | 严格一致 | — |
| `--sh-radius-lg` | 12px | Tailwind `lg: var(--radius)` | 10px | 近似 | Δ2；库已按 1.3.1 上移一档，产品当前为 10px |
| `--sh-radius-xl` | 16px | Tailwind `xl: calc(--radius + 4px)` | 14px | 近似 | Δ2；库已按 1.3.1 上移一档，产品当前为 14px |
| `--sh-radius-2xl` | 20px | Tailwind `2xl: calc(--radius + 8px)` | 18px | 近似 | Δ2；库已按 1.3.1 上移一档，产品当前为 18px |
| `--sh-radius-2xs` | 4px | 无对应 | — | 漂移 | 产品无 4px 档；库用于徽标内小元素 |
| `--sh-text-sm` | 14px | Tailwind `text-sm` | 14px | 严格一致 | — |
| `--sh-space-4` | 16px | Tailwind `p-4 / gap-4` | 16px | 严格一致 | — |
| `--sh-duration-fast` | 150ms | Tailwind `duration-150` | 150ms | 严格一致 | — |
| `button.sm` | 28px | `h-8` | 32px | 漂移 | Δ4；库 28 比产品低 4px，产品 `h-8` 未跟上四档阶梯 |
| `button.md` | 32px | `h-[var(--control-height)]` | regular 36px / compact 32px | 漂移 | 默认差4px；compact一致，不能忽略容器密度 |
| `button.lg` | 36px | `h-10` | 40px | 漂移 | Δ4；库 lg 比产品低 4px（方向已修正）；建议产品改 `h-9` 或逐项评审 |
| `button` 焦点环 | 2px + 2px offset | `ring-2 ring-ring/40 offset-1` | 2px + 1px offset + 40% 透明 | 漂移 | 库更实、更可见；产品焦点环在暗色下偏淡 |
| `input` 高度 | 32px | `h-[var(--control-height)]` | regular 36px / compact 32px | 漂移 | 产品高度受密度容器控制；默认差 4px，compact 同为 32px |
| `input` 圆角 | 6px | `rounded-md` | 10px | 漂移 | Δ4；库取 `--sh-control-md-radius` 6px，产品 `rounded-md` 为 10px |
| `select` 触发器圆角 | 6px | `rounded-md` | 10px | 漂移 | 契约统一 `--sh-control-md-radius` 6px；产品用 10px，需产品侧对齐或接受差异 |
| `textarea` 圆角 | 6px | `rounded-lg` | 10px | 漂移 | Δ4；契约统一 `--sh-control-md-radius` 6px，产品用 10px，需产品侧对齐或接受差异 |
| `textarea` 最小高度 | 100px | `min-h-[100px]` | 100px | 严格一致 | — |
| `card` 内边距 | 20px | `p-[var(--card-padding)]` | regular 20px / compact 16px | 严格一致 | — |
| `card` 圆角 | 12px | `rounded-md` | 10px | 近似 | Δ2；库取 `--sh-radius-lg` 12px，产品仍为 10px |
| `table` 表头高度 | 基线40px；task变体36px | `h-[var(--control-height)]` + 密度 padding | regular 高度属性36px / compact 32px；最终高度受内容与padding影响 | 待浏览器验证 | 不再按旧 h-12 判定48px；基线与 task 变体分开比较 |
| `table` 行高 | 基线40px；task单行40px/双行52px | `p-[var(--table-cell-padding)]` + `text-sm` | regular padding12px / compact8px；最终行高由内容决定 | 待浏览器验证 | 不能仅从类名认定固定行高；需验证换行、字体和密度容器 |
| `tabs` 间距 | 24px | `gap-6` | 24px | 严格一致 | — |
| `tabs` 纵向内边距 | 8px | `py-3` | 12px | 漂移 | Δ4；库取 `--sh-space-2` 8px，产品 `py-3` 为 12px |
| `dialog` 最大宽度 | 480px | `w-[min(calc(100vw-2rem),30rem)]` | 480px | 严格一致 | — |
| `dialog` 内边距 | 20px | `p-6` | 24px | 漂移 | 产品更松；契约 20px 未落地 |
| `dialog` 圆角 | 12px | `rounded-2xl` | 18px | 漂移 | 产品弹窗用 18px，库取 `--sh-radius-lg` 12px |
| `--sh-shadow-card` ↔ `index.css` | `0 0 0 1px rgb(0 0 0/3%), 0 1px 3px rgb(0 0 0/4%)` | `--shadow-card: 0 1px 3px 0 rgb(0 0 0/4%)` | 少 1px 环 | 近似 | 库多一层 1px 环，暗色下更清楚 |
| `--sh-shadow-card` ↔ Tailwind | 同上 | `shadow-card: 0 4px 24px -4px …` | 完全不同 | 漂移 | 产品内两套阴影（G-007） |

**小计：严格一致 10 / 近似 5 / 漂移 15 / 待浏览器验证 2。**

## 5. 历史组件候选清单（13 项，引用数未重新统计）

以下保留原始候选；“只有基础契约”不等于完全无契约，不能作为当前覆盖率结论。

按产品引用文件数排序。证据为 `web/src/shared/components/*.tsx`。

| # | 产品实现 | 引用文件数 | 库现状 | 建议 |
|---|---|---|---|---|
| 1 | `dashboard-page-header.tsx` | 22 | 无契约 | 补 `page-header` 契约（标题/面包屑/操作区/密度） |
| 2 | `confirm-dialog.tsx` | 13 | 只有 `dialog` | 补 `confirm-dialog` 变体契约（危险确认、无撤销） |
| 3 | `namespace-badge.tsx` | 7 | 只有 `badge` | 补 `namespace-badge` 契约（`@namespace` 单行省略） |
| 4 | `legal-document.tsx` | 4 | 无契约 | 补排版契约（阅读宽度 760px、段落节奏） |
| 5 | `quick-start.tsx` | 4 | 无契约 | 补引导块契约（步骤/代码片段/复制） |
| 6 | `brand-mark.tsx` | 3 | 明确不定义 logo | 品牌资产单独评审，见 `graphics.md`；不因灰阶控件规则自动移除产品标志 |
| 7 | `landing-quick-start.tsx` | 2 | 无契约 | 与 `quick-start` 合并为一个契约 |
| 8 | `user-menu.tsx` | 2 | 只有 `dropdown-menu` | 补复合契约（头像 + 菜单 + 退出） |
| 9 | `theme-toggle.tsx` | 2 | 无契约 | 补开关契约（含亮/暗/系统三态） |
| 10 | `toaster.tsx` | 2 | 无 `toast` 契约 | 补 toast 契约（360px、2 行、状态色） |
| 11 | `role-guard.tsx` | 2 | 无契约 | 非视觉组件，只需文档化权限边界 |
| 12 | `route-error.tsx` | 1 | 无契约 | 与 `empty-state` 合并错误态契约 |
| 13 | `language-switcher.tsx` | 1 | 无契约 | 补下拉契约（语言名 + 当前态） |

## 6. 待核对的产品封装候选（11 项）

指尚未确认独立共享封装，不代表产品没有对应业务能力。例如安装命令、状态徽标应先复用已有业务组件。

按详情页/列表页真实使用频率排序（频率来自页面组合，不是代码引用数）。

| # | 库契约 | 详情页用途 | 列表页用途 | 建议优先级 |
|---|---|---|---|---|
| 1 | `badge` | 版本状态、审核状态 | 卡片状态 | P0（两页都高频） |
| 2 | `avatar` | 维护者、审核人 | 卡片作者 | P0 |
| 3 | `tooltip` | 安装命令、截断说明 | 命名空间说明 | P0 |
| 4 | `breadcrumb` | 命名空间 / 技能层级 | 搜索结果层级 | P1 |
| 5 | `code-block` | 安装命令、SKILL.md 示例 | 无 | P1 |
| 6 | `alert` | 安全提示、审核驳回原因 | 无 | P1 |
| 7 | `form-field` | 举报/治理表单 | 筛选表单 | P1 |
| 8 | `checkbox` | 文件多选、条款确认 | 批量选择 | P2 |
| 9 | `switch` | 发布设置 | 仅显示已发布 | P2 |
| 10 | `radio` | 可见性选择 | 排序方式 | P2 |
| 11 | `sheet` | 移动端权限面板 | 移动端筛选 | P2 |

## 7. 建议实现顺序与理由

1. **先处理 3 处几何差异**（select / textarea 圆角、dialog 内边距）：1.3.1 契约中 select 触发器与 textarea 均统一为 `--sh-control-md-radius` 6px，产品 select 为 `rounded-md` 10px、textarea 为 `rounded-lg` 10px；dialog 内边距契约 20px、产品 24px。契约已冻结，本表只记录差异，不再采用 1.0.0 的「6→10 / 8→10」建议。
2. **按需要落地共享封装**（badge / avatar / tooltip / breadcrumb）：库已有对应契约；先核对产品实现，再决定是否补封装，避免重复造组件。
3. **补 code-block / alert / form-field**：详情页的安装命令、安全提示、举报表单都依赖它们。
4. **补 confirm-dialog / namespace-badge / page-header / toast**：产品已有实现且引用多，库应追上，避免“产品先行、库滞后”。
5. **最后补 sheet / radio / checkbox / switch**：只在移动端或设置页出现，频率低。
6. **brand-mark 单独决策**：产品 favicon 是蓝色 package 图标，与库的灰阶体系冲突；保留现有品牌资产，是否替换另行确认；不能从控件灰阶规则推导删除标志。

不按此顺序做的风险：先补低频组件会让契约数量好看，但详情页首屏仍靠临时样式，漂移继续扩大。

## 8. 漂移原因归类

| 原因 | 涉及项 | 建议动作 |
|---|---|---|
| 文字层级更浅 | `text-primary` / `text-secondary` / `text-disabled` / 暗色 `text-tertiary` | 产品侧统一走 `--text-*` 变量；库不改值 |
| 语义色取值不同 | success / warning / danger / info（差异以颜色表为准） | 产品侧按库值替换，先核对对比度；库不改值 |
| 暗色表面更亮 | `bg-overlay` / `accent-base` / `border-focus` | 产品侧跟随库的层级差；库已冻结 |
| 组件几何差异 | select / textarea 圆角、table 表头、dialog 内边距与圆角、button lg | 契约已按 1.3.1 冻结，记录差异，逐项评审 |
| 字体目标差异 / 两套阴影 | 字体已同源；阴影仍有 CSS / Tailwind 两套声明 | 字体按目标栈迁移；阴影按实际调用收敛 |
| 产品缺失语义变量 | `bg-inverse` / `text-disabled` / `border-strong` / `radius-2xs` | 产品补变量，或接受库独有 |

## 9. 复核方法

1. 产品改动 `index.css` / `tailwind.config.ts` / `shared/ui` 后，重跑本表对应行。
2. 颜色用 HSL→HEX 换算后比较，不看变量名；几何直接读 Tailwind 类名。
3. 在当前变更中记录新增漂移项，写清证据文件与行号。
4. 本表只读，不触发产品代码修改；任何“建议动作”需产品/前端负责人确认后另行执行。
