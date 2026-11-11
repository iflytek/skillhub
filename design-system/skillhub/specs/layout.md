# 栅格、布局与响应式

## 1. 页面骨架

| 场景 | 容器最大宽 | 左右留白 | 栅格 | 关键约束 |
|---|---|---|---|---|
| 列表页 | 1152px | 24 / 48px（`px-6 md:px-12`） | 12 列，gutter 24；卡片 4 列（≥1280） | 首屏出现搜索 + 筛选 + 首个结果 |
| 详情页 | 1152px | 24 / 48px | 12 列：正文 8 列（760px）+ 侧栏 4 列（320px），gap 48 | 首屏必须有身份区 + 安装命令 + Tab |
| 表单页 | 640px | 24px | 单列；label 在上，字段间距 24 | 主提交按钮常驻底部，不与字段同色 |
| 空态页 | 480px（内容） | 24px | 单列居中，垂直留白 80px | 一句原因 + 最多一个操作 |

来源：`max-w-6xl`、`max-w-[760px]`、`w-[320px]`、`px-6 md:px-12 py-10` 均为现有实现；640px 表单宽与 480px 空态宽为**扩增，需评审**。

> 控制台/后台页面从 0.5.8 起使用 `.app-shell` 三槽骨架（顶栏 + `--sh-size-sidebar` 侧栏 + 独立滚动内容区），详见 [`app-shell.md`](./app-shell.md)。上表的最大宽与留白仍适用于内容区内部，不与外壳叠加重复留白。

## 2. 详情页栅格

- 12 列 = 8 + 4；正文列 `minmax(0, 760px)`，侧栏 `320px`，列间距 `48px`。
- 正文列内不再分栏；README 行高 1.7，段落间距 16px。
- 侧栏卡片间距 16px；卡片内边距 20px；同组卡片最多 4 张。
- 阅读宽度上限 760px 是硬约束，超过会降低长文可读性（来源：现有 v2 与详情实现）。

## 3. 列表页栅格

- ≥1280：4 列卡片，gap 20px，卡片最小宽 260px。
- 1024–1279：3 列。
- 768–1023：2 列。
- <768：1 列，卡片横向内边距降到 16px。

## 4. 密度

| 档位 | 行最小高 | 纵向 padding | 横向 padding | 用途 |
|---|---|---|---|---|
| default | 40px | 8px | 12px | 详情、列表、默认后台 |
| compact | 32px | 4px | 8px | 审核队列、版本表、日志表 |
| comfortable | 48px | 12px | 12px | 引导页、空态、设置分组 |

token：`--sh-size-row-*`、`--sh-size-table-pad-x`、`--sh-size-table-pad-y`、`--sh-size-card-padding-*`；compact 变体见 `themes/compact.css`。

表头单独一档：default 40px / 13px / 500 / 横向 12px；compact 36px / 13px；comfortable 44px / 13px。横向 padding 只有 `--sh-size-table-pad-x` 一个机制：默认 12px、compact 主题覆盖为 8px；comfortable 没有独立横向机制，仍为 12px。

## 5. 响应式规则

| 断点 | 详情页 | 列表页 | 全局 |
|---|---|---|---|
| ≥1280 | 8+4 双列，侧栏 sticky | 4 列 | 页头完整，显示次要指标 |
| 1024–1279 | 双列，侧栏 280px，gap 32 | 3 列 | 页头操作可换行 |
| 768–1023 | 单列，侧栏移到正文后 | 2 列 | 隐藏面包屑，改为返回按钮；指标只留 2 个 |
| <768 | 单列，间距 16px | 1 列 | 安装面板全宽；Tab 横向滚动；侧栏卡片全部堆叠 |

**允许折叠**：面包屑（→ 返回按钮）、次要指标、贡献者、相关技能、长描述（“展开”）。
**必须保留**：技能名与命名空间、主操作（安装/复制）、Tab（概览/文件/版本）、安全与合规摘要、审核状态、语言切换。
**禁止**：在 <768 隐藏安全摘要；把安装命令移出首屏；用横向滚动承载整页内容。

## 6. ≤640px 断点契约（0.5.5）

断点统一为 `@media (max-width: 640px)`，规则由 `components.runtime.css` 提供，页面不得各自重复写。

| 区块 | 类 | 640px 行为 |
|---|---|---|
| 卡片网格 | `.skill-grid` | 单列 `minmax(0, 1fr)`，行距 `--sh-space-3` |
| 技能卡片 | `.skill-card-tags` / `.skill-card-top` | ≤640 标签换行展示，不用隐藏横滚；状态徽标 `flex: none`；`.skill-grid` 行内等高 |
| 详情头部 | `.detail-header` / `.detail-actions` | 头部纵向堆叠；操作区整行换行，按钮等分 |
| 筛选栏 | `.filter-bar` / `.filter-search` / `.filter-pills` / `.filter-actions` | 搜索独占一行；筛选胶囊 ≤640 换行；动作整行靠左；`.filter-bar .select-trigger` / `.select-native` 宽度 auto |
| 批量条 | `.bulk-bar` / `.bulk-bar-text` / `.bulk-bar-actions` | 文案独占一行；动作整行靠左 |
| 表格 | `.table-shell` / `.data-table` | 容器横向滚动，表格 `min-width: 560px`；`.col-secondary` 列隐藏 |

**标记规则**：次要列在 `th` 与对应 `td` 上同时加 `.col-secondary`，不要用 `nth-child` 或业务类名判断。
**禁止**：≤640 隐藏状态列与操作列；用横向滚动承载整页；把主操作压到低于 28px；为单个页面重复定义断点。

## 7. 控件档位与容器对应（1.2.0 硬规则）

控件四档固定为 **24 / 28 / 32 / 36**，token 名 `--sh-control-{xs,sm,md,lg}-*`；**默认 md = 32**（13/18，横向 padding 12）。

| 档位 | 高 | 字号/行高 | 横向 padding | 圆角 | 允许出现的位置 |
|---|---|---|---|---|---|
| xs 24 | 24 | 12/16 | 8 | 6 | 表格行内、卡片角标、行内工具条、徽标旁附操作 |
| sm 28 | 28 | 13/18 | 10 | 6 | 筛选栏、工具栏、分页、面板内操作 |
| md 32 | 32 | 13/18 | 12 | 6 | 页面主操作、表单、页头默认、对话框底部 |
| lg 36 | 36 | 14/22 | 12 | 8 | 引导页 / 空态的唯一 CTA |

**硬约束**

- **xs 不得出现在页头**（页头操作区只用 md 或 lg）。
- **md 不得放进表格行内**；表格行内操作统一 24（`.data-table .btn-icon`、`.data-table .action` 已内置）。
- 行内控件高度不大于 `行高 − 16`，且垂直居中；default 40 行 → 行内最大 24，compact 32 行 → 行内最大 16（实际只用文字链）。
- 同一行/同一操作区只用一个档位，主次靠实心/描边区分，不靠尺寸。
- 输入框（`--sh-control-sm/md-*`）与 Select 触发器必须同高；禁止一高一低。
- 徽标 / 标签 / lifecycle / verdict 不参与这套档位，固定 24px，见 `specs/component-state-matrix.md`。

**禁忌**：页头用 xs；行内用 md；把 lg 当默认按钮；给表格行内塞 32px 控件导致行被撑高。

## 8. 行高与内容行数换算（1.2.0 硬规则）

行高由 **最小行高** 与 **内容实际需要** 取大值，不再写死 `td height`：

```
实际行高 = max(最小行高, Σ(每行 line-height) + 2 × padding-block)
```

**1.2.2 契约（混排行高）**：单元格内所有文本行必须共用 `--sh-control-md-line`（18px）。小字号不直接写 `line-height`，改用辅助类 `.cell-sub`（字号 `--sh-size-badge-font` 12，行高仍 18，颜色 `--sh-text-secondary`）。只有 Σ = 内容行数 × 18 时，上表的三档密度才成立。

| 密度 | 最小行高 | padding-block | 单行 | 双行 | 三行 |
|---|---|---|---|---|---|
| default | 40 | 8 | 40 | 52 | 70 |
| compact | 32 | 4 | 32 | 44 | 62 |
| comfortable | 48 | 12 | 48 | 60 | 78 |

- comfortable 只有最小行高 token 与契约固定的 `padding-block: 12px`，没有独立横向机制：横向仍取 `--sh-size-table-pad-x` 12px，compact 主题覆盖为 8px。

- 18px 是 `--sh-control-md-line`；compact 字号 12/18 时仍按 18 计算，避免行高抖动。
- `td { height: var(--sh-size-row-*) }` 在表格布局里是最小高度，**不要改成固定 height**，也不要在单元格里再写 `line-height` 覆盖；小字一律走 `.cell-sub`。
- 单元格默认 `white-space: normal`；只有 `.num` / `.mono` / `.nowrap` 允许单行，长文案必须在列宽内换行。
- 操作列右对齐；行内操作最多 3 个，超出收进下拉菜单。

**方案取舍（1.2.2）**：

- **方案 A（公式取最大行高）**：不成立。真实行高是各行 line-height 的**求和**，不是 `行数 × 最大行高`；一行 18 + 一行 24 实际是 42，按最大行高算会得到 48，高估 6px。改成求和虽准确，但页面一旦混排小行高，双行会静默掉到 51px，已发布的 40/52/70 密度数字不再可验证。
- **方案 B（契约统一 + `.cell-sub`）**：选定。行高 token 是密度契约，单元格必须共用 18px；小字用 `.cell-sub` 保持 18px 行高。代价是小字行距比自然排版松 2px，但换行行为可预算、三档密度不会漂移。

**混排复测（1440，行 rect 含 1px 下边框）**：

| 密度 | 单行 | 双行均匀（18+18） | 双行混排原始（18+16，块级 caption） | 双行混排 `.cell-sub`（18+18） |
|---|---|---|---|---|
| default | 40 | 53 | **51** | **53** |
| compact | 32 | 45 | **43** | **45** |
| comfortable | 48 | 61 | **59** | **61** |

- 改前依靠页面 `line-height: inherit` 才能把案例 B 的 51 救回 53；现在库内 `.cell-sub` 直接锁定 18，不依赖页面兜底。

**禁忌**：`td { height: 52px }` 写死；用 `line-height` 撑行高；给整列加 `white-space:nowrap`；在单元格里给 caption 直接写更小 `line-height`。

**行头单元格（1.2.1）**：`tbody th[scope="row"]` 属于数据行，必须与 `td` 共用同一套 `--sh-size-row-*`、`--sh-size-table-pad-y`、`--sh-control-md-font/line`，不得沿用 `thead th` 的 `padding: 0 …` 与表头高度 token。实测（行 rect，含 1px 下边框）：

| 密度 | 单行 | 双行 | 改前（单 / 双） |
|---|---|---|---|
| default | 40 | 53（内容+padding 52） | 40 / 40 |
| compact | 32 | 45（44） | 36 / 37 |
| comfortable | 48 | 61（60） | 44 / 44 |

**禁忌**：给行头单独写 `height`；用 `th { padding: 0 … }` 继承表头规则；把行头改成 `nowrap` 截断双行内容。

## 9. 选择控件规格（1.2.0 硬规则）

| 部位 | 规格 |
|---|---|
| 触发器 md | 高 32、padding 0 12、圆角 6、字号 13/18、箭头 16 |
| 面板 | offset 4、padding 6、圆角 10、border default + `--sh-shadow-popover` |
| 面板尺寸 | `min-width` = 触发器实测宽（宿主写 `--sh-select-min-width`）、`max-width` 360、`max-height` 320 可滚动 |
| 选项行 | `min-height` 32、padding `0 36px 0 12px`、13/18、圆角 6、项间距 2 |
| 选中态 | `--sh-interaction-selected-bg` + `--sh-primary-base` + 500 + 右侧 16px 勾（right 12，绝对定位） |
| 分组标题 | 12/20，padding `8px 12px 4px` |
| 空态 | 高 64（`--sh-size-empty-compact`） |
| 多选 | 左侧 16px 复选框 + 8px 间距，整行可点，选中整行底色 |
| 原生 select | `appearance:none` + 自绘 16px chevron，高度/内边距/圆角与触发器一致 |
| 筛选栏原生 select | `.filter-bar` 内必须 `width:auto`、`flex:0 0 auto`、`min-width:var(--sh-select-min-width)`，与搜索框/胶囊同排，不得占满整行 |

**禁忌**：把勾写在左侧与文字重叠；面板写死固定宽度；面板做成整屏弹层；选项行 28px 以下；同页两种触发器高度；筛选栏里让 `.select-native` 独占一行。

> 触发器只有 md 一档。文档曾承诺的 `sm` 触发器（高 28、padding 0 10、仅用于筛选栏）在 runtime 中不存在，故不在此列出，见 `backlog/gaps.md` G-093。

### 9.1 `.select` 与 `.select-native` 职责分界（1.2.2 硬规则）

`select-native` 的 `appearance:none` 只作用于**关闭态触发器**；展开后的选项列表由操作系统渲染（灰底、系统蓝高亮、系统勾号），CSS 无法接管。因此按「展开面板是否会出现在产品界面/截图」判定：

| 判定项 | 允许 `select-native` | 必须 `.select` |
|---|---|---|
| 选项数量 | ≤4 个单值选项，且无分组 / 多选 / 搜索 / 空态 | ≥5 个，或需要分组 / 多选 / 搜索 / 空态 |
| 位置 | 仅内联极简场景：与文本同行、控件宽 ≤120px | 工具栏、筛选栏、表格表头筛选行、页头动作区、表单字段、设置项 |
| 展开态可见性 | 从不在产品界面 / 截图 / 演示 / 评审中出现 | 任何会展示展开态的页面或产物 |
| 交互语义 | 低风险、可随时改选、无禁用原因 | 需要明确当前值、分组标题、禁用原因、空态 |

**硬约束（1.2.2 收紧）**：库已提供 `.select` 的参考实现（CSS + `assets/select.js` + 契约 `referenceImplementation`），因此「护栏密度」不再是选 native 的理由。

- 工具栏 / 筛选栏里只要选项 **≥5**，或**需要展开态展示**，一律用 `.select`，没有例外。
- `select-native` 只留给真正极简的内联场景：≤4 个单值选项、控件宽 ≤120px、与文本同行、无分组 / 多选 / 搜索 / 禁用原因 / 空态，且从不在任何产物中展开。
- 表格表头筛选行、页头动作区、主筛选条禁止使用 native。
- 建议把这条收紧到「native 是例外而非默认」：评审时要求每处 native 能一句话说清为什么不用 `.select`；说不出就改 `.select`。

**`.select` 现状（1.2.2 已修）**：视觉、定位、层级、键盘、`aria` 均已随库提供。CSS 负责 `.select` 相对定位、面板 offset 4、`--sh-z-dropdown`、min-width 跟随触发器、max 360 / 320；[assets/select.js](../assets/select.js) 覆盖向上翻 / 贴边回退、↑↓ / Home / End / Enter / Esc / Tab / 首字母、combobox / listbox / `aria-activedescendant`、单选与多选、点外部关闭。宿主只负责选项数据（静态或异步）与业务格式化。G-082 已关闭。

### 9.1.1 满宽用法（1.2.2）

`.select` 默认 `inline-flex` + `min-width: 180`，服务筛选条；放进表单字段或栅格后必须由库接管宽度，消费方不需要写任何 `width`：

| 容器 | 写法 | 结果 |
|---|---|---|
| 表单字段 | `.field > .select` | 自动 `width: 100%` + `min-width: 0`，与同字段 input / textarea 左右对齐；容器可窄于 180 |
| 栅格单元格、表格筛选格 | `.select.select--block` | 撑满单元格，`min-width: 0`；触发器和面板宽度完全由容器 content 宽决定 |
| 筛选条、工具栏 | 默认 `.select` | 保持 `inline-flex` + `min-width: 180`，不独占一行 |

**最小宽归零（1.2.2）**：两个满宽规则都声明 `min-width: 0`，覆盖组件默认的 `min-width: var(--sh-select-min-width)`（180）。因此 150 / 172 / 180 三种窄容器下，`.select` 外框分别等于 150 / 172 / 180，不溢出、不吃列间距；消费方不需要保证格子 ≥180，也不需要再写 `min-width` 补丁。默认 `inline-flex` 场景不受影响：筛选条仍保持 180 下限，内容更长时按内容增长。

**理由**：只做 `.select--block` 会让表单字段仍要手写变体；只做 `.field > .select` 覆盖不到栅格与表格。两者都给，才能同时满足「表单零补丁」与「栅格一格一控件」。消费方源码里不得再出现 `.field > .select { width: 100% }`、`.case-filter-grid .select { width: 100% }` 这类补丁。

### 9.1.2 滚动容器与裁剪（1.2.2）

面板仍锚定在 `.select` 内，不引入 portal。定位计算以「最近的 `overflow` 非 `visible` 祖先 + 视口」为可用空间：

1. 下方空间足够 → 向下展开；
2. 下方不足且上方更大 → `.is-up` 向上翻；
3. 上翻后仍不足 → 把面板 `max-height` 压到该侧可用空间（仍受 320 / 60vh 上限），面板内部滚动。

**为什么 1.2.2 不做 portal**：裁剪感知 + 上翻 + 压高已能覆盖弹窗、抽屉、表格滚动区；portal 会改变 DOM 归属，需额外处理挂载/卸载、滚动跟随、外部点击、焦点与 `aria-controls` 关系，收益不足以抵消回归风险。仅当裁剪祖先的可用高度小于「触发器 + 一个选项」时才是纯 CSS 定位的死角；届时再评估 portal。宿主不得用 `overflow: visible` 打补丁，否则会破坏弹窗自身滚动。

## 10. 首列冻结宽度约束（1.2.1 硬规则）

`.data-table--freeze-first` 的首列必须同时受「档位上限」与「视口比例」约束，不能只做 `sticky`：

```css
/* 表头首列：低特异性，页面显式宽度优先 */
.data-table--freeze-first :where(thead th:first-child) { width: min(212px, 40vw); }
/* 冻结单元格：上限 + sticky */
.data-table--freeze-first th[scope="row"]:first-child,
.data-table--freeze-first td:first-child { position: sticky; left: 0; max-width: min(212px, 40vw); }
```

**定档依据**（案例 D 实测，首列 / 占 `.table-shell` 比 / 标题列剩余）：

| 视口 | 改前首列 | 改前占比 | 改后首列 | 改后占比 | 改后标题列 | 改后容器宽 |
|---|---|---|---|---|---|---|
| 430 | 206.9 | 54.4% | 172 | 45.3% | 236（可见 208） | 380 |
| 768 | 190.8 | 27.2% | 190.8 | 27.2% | 78.7 | 702 |
| 1024 | 196 | 20.5% | 196 | 20.5% | 118.4 | 958 |
| 1440 | 212 | 18.5% | 212 | 18.5% | 240 | 1146 |

- `212px` 是 default 档的内容上限，1440 下占比 18.5%，足够放 `IR-2026-1107-014` 这类标识。
- `40vw` 是窄视口兜底：430 下 40vw = 172px，首列占比从 54.4% 降到 45.3%，标题列列宽从 201.1px 增到 236px（首屏可见 380 − 172 = 208px）；768 起 40vw = 307px > 212px，上限由 212px 接管，因此 768/1024/1440 改前改后一致。
- 页面给首列写了显式 `min-width` 时（如案例 B 的 176px 矩阵列），库内上限无法覆盖页面显式最小值：案例 B 在 430 仍为 176 / 44.4% / 144，属页面级约束，页面需自行保证标题列可用宽度。
- sticky 首列不得因宽度上限而改变 `left: 0` 偏移；横向滚动时首列仍贴容器左缘。

**禁忌**：只加 `sticky` 不给上限；用固定 `212px` 覆盖窄视口（430 会占 55%）；用 `nth-child` 给首列设上限（无法兼容行头 `<th scope="row">`）。

## 11. 抽屉 / 面板档位（1.2.1 硬规则）

`.sheet-panel` 必须 `box-sizing: border-box`；**档位 token 直接等于实际外框宽度**，内边距 20px 与 1px 边框从宽度内扣，不再外扩：

| 档位 | token | 实际外框宽 | 改前实测 |
|---|---|---|---|
| sm | `--sh-size-sheet-sm` 360 | 360 | 401 |
| md | `--sh-size-sheet-md` 480 | 480 | 521 |
| lg | `--sh-size-sheet-lg` 640 | 640 | 681 |

**禁忌**：给 `.sheet-panel` 设 `width` 后额外加左右 padding 补偿；把 token 当内容宽度用。

## 12. 响应式验证口径（1.2.1）

表格与面板类改动必须在 **430 / 768 / 1024 / 1440** 四个宽度实测，并同时记录：

- `.table-shell` 的 `scrollWidth` / `clientWidth`（横向溢出）；
- 冻结首列宽度、占容器比、标题列剩余宽度；
- 行头单行与双行行高（三档密度）；
- `.sheet-panel` 各档外框宽度与 token 值是否相等。

## 13. 尺寸 token = 外框尺寸（1.2.2 硬规则）

**约定**：凡是 token 用来表示控件「外框」的组件（`--sh-control-*-height`、`--sh-size-badge-height`、`--sh-size-sheet-*`、`--sh-icon-*`、`--sh-size-control-icon`），token 值必须等于 `offsetWidth` / `offsetHeight`（含边框与内边距）。实现上必须 `box-sizing: border-box`；不允许把 token 当内容宽度再用 border/padding 外扩。

- `button` / `input` / `select` 走浏览器 UA 的 `border-box`，但仍需在新增控件上显式声明，避免宿主 reset 改变行为。
- 用 `min-height` 表示外框的控件（如 `.check-row`）同样必须 `border-box`；`min-height` 只是下限，双行内容超出时按内容撑开，不得靠压缩 padding 硬压。
- `span` / `svg` 类控件（`.check-box`、`.radio-circle`、`.switch-knob`、`.spinner`、`.loading-chip`、`.select-check`）默认 `content-box`，必须显式 `box-sizing: border-box`，否则 1px 边框会把 16px 渲成 18px。
- 自动高度的容器（card / dialog）不在此约定：它们的 token 是内边距或 `max-width`，不是外框尺寸。

**内边距如何从外框反推**：

- 单行定高控件：`height = token`、`padding: 0 pad-x`，纵向剩余空间由 `align-items: center` / 行内居中吸收，**不要**再用 `padding-y` 叠加高度。
- 需要纵向内边距的多行容器（textarea）：`pad-y = (token − 2 × border − line-height) / 2`，四档预算 xs 3 / sm 4 / md 6 / lg 6；现有 `--sh-control-md-pad-y: 5px` 只服务多行 textarea（其最小高度 96px，不由 32px 外框推导），不参与控件定高。
- 横向：`pad-x` 直接取 `--sh-control-*-pad-x`；固定宽度控件（图标按钮、复选框、开关）`padding: 0`。

**全库实测（1440 亮色，`file://` + `offsetWidth/offsetHeight`）**：

| 组件 | 档位 | token | 改前实测 | 改后实测 | 处理 |
|---|---|---|---|---|---|
| button | xs | `--sh-control-xs-height` 24 | 24 | 24 | 已符合（UA border-box） |
| button | sm | `--sh-control-sm-height` 28 | 28 | 28 | 已符合 |
| button | md | `--sh-control-md-height` 32 | 32 | 32 | 已符合 |
| button | lg | `--sh-control-lg-height` 36 | 36 | 36 | 已符合 |
| button | icon md | `--sh-size-control-icon` 28 | 28×28 | 28×28 | 已符合 |
| button | icon xs | `--sh-control-xs-height` 24 | 24×24 | 24×24 | 已符合 |
| input | md | `--sh-control-md-height` 32 | 32 | 32 | 已符合 |
| input | sm | `--sh-control-sm-height` 28 | — | — | 保留说明：库内只提供 md 与 `field--sm`，xs/lg 无对应类，不虚构实测 |
| select | md | `--sh-control-md-height` 32 | 32 | 32 | 已符合 |
| checkbox | md | `--sh-control-md-icon` 16 | 18×18 | 16×16 | 1.2.2 已修 |
| checkbox | 20 | `--sh-icon-20` 20 | 22×22 | 20×20 | 1.2.2 已修 |
| radio | md | `--sh-control-md-icon` 16 | 18×18 | 16×16 | 本轮修（补 border-box） |
| radio | 20 | `--sh-icon-20` 20 | 22×22 | 20×20 | 本轮修 |
| switch | track md | 规格 36×20（无 token） | 36×20 | 36×20 | 已符合（button UA border-box） |
| switch | track sm | 规格 32×18（无 token） | 32×18 | 32×18 | 已符合 |
| switch | knob md | 16×16 | 18×18 | 16×16 | 本轮修（补 border-box） |
| switch | knob sm | 14×14 | 16×16 | 14×14 | 本轮修 |
| badge | — | `--sh-size-badge-height` 24 | 24 | 24 | 已符合（无边框） |
| tag | color | 对齐 `--sh-size-badge-height` 24 | 20 | 24 | 本轮修：裸行高 1.3 → 定高 24 |
| tag | handle | 对齐 24 | 22 | 24 | 本轮修：裸行高 1.25 + border-box |
| status-pill | — | 对齐 24 | 23 | 24 | 本轮修：裸行高 1.25 → 定高 24 |
| filter-pill | — | `--sh-size-badge-height` 24 | 24 | 24 | 已符合（button UA border-box） |
| progress | sm / md / lg | `--sh-size-progress-*` 4 / 8 / 12 | 4 / 8 / 12 | 4 / 8 / 12 | 已符合（无边框） |
| spinner | copy-btn | `--sh-icon-14` 14 | 18×18 | 14×14 | 本轮修（补 border-box） |
| spinner | form-field | `--sh-control-md-icon` 16 | 18×18 | 16×16 | 本轮修 |
| loading-chip | — | `--sh-size-badge-height` 24 | 26 | 24 | 本轮修（补 border-box） |
| select-check | — | `--sh-icon-16` 16 | 18×18 | 16×16 | 本轮修（补 border-box） |
| sheet | sm / md / lg | `--sh-size-sheet-*` 360 / 480 / 640 | 401 / 521 / 681 | 360 / 480 / 640 | 1.2.1 已修 |
| dialog | — | `--sh-size-container-dialog` 480 | 294 宽 | 294 宽 | 保留：token 是 `max-width`，宽度随内容与视口 |
| card | — | `--sh-space-card-padding` 20 | padding 20 | padding 20 | 保留：token 是内边距，不是外框 |
| table | 表头单元格 | `--sh-size-row-header` 40 | 40 | 40 | 已符合 |
| table | 正文单元格（纯文本） | `--sh-size-row-default` 40 | 40 | 40 | 已符合（含 1px 行分隔线） |
| table | 正文单元格（含 badge） | 40 | 41 | 41 | 保留说明：badge 24 + 上下 16 = 40 内容，加 1px 分隔线 = 41；`row-*` 是最小行高语义，内容超出按内容撑开 |
| table | 行头单元格（双行） | 52 + 1 | 53 | 53 | 保留说明：与 `row-height = 2 × line + 2 × pad-y + 1` 公式一致 |

**禁忌**：给 span 控件只写 `width/height` 不写 `box-sizing`；用 `padding` 反向撑高定高控件；把 `--sh-size-row-*` 当硬高度去压缩 badge/双行内容。
