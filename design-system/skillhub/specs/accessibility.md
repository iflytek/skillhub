# 可访问性规范

## 1. 对比度下限

| 对象 | 下限 | 本库目标 | 依据 |
|---|---|---|---|
| 正文 / 标题 | 4.5:1 | ≥ 7:1（`high-contrast` 变体） | WCAG AA / AAA |
| 大号文字（≥18.66px 粗体或 ≥24px） | 3:1 | 4.5:1 | AA |
| 图标、边框、焦点环等非文本 | 3:1 | 4.5:1 | AA 非文本 |
| 禁用态文字 | 不受限 | 仍保持可辨识（不低于 3:1） | 最佳实践 |
| 状态色（成功/警告/错误） | 与底色 ≥ 3:1 | 同时提供图标或文字，不仅靠色 | AA |

现有 token 实测（亮底 `#ffffff` / 暗底 `#101010`）：`#737373` 亮色辅助文字 ≈ 4.74:1；`#575757` 亮色次要文字 ≈ 7.23:1；暗色 `#909090` 辅助文字 ≈ 5.96:1、`#b9b9b9` 次要文字 ≈ 9.70:1。禁用态独立计：亮 `#949494` ≈ 3.03:1、暗 `#6e6e6e` ≈ 3.73:1，按 WCAG 1.4.3 豁免但仍保持可辨识。

## 2. 焦点表达

两种控件、两种表达，同一控件上**只允许出现一种**：

| 控件类型 | 指针点击 / 键入 | 键盘导航 | 实现 |
|---|---|---|---|
| 文本录入：`input` / `textarea` / `.select-trigger` / 搜索框 | 边框转为 `--sh-border-focus` | 同左（不补外环） | `:focus` 改 `border-color` |
| 非文本：`.btn` / `.tab` / checkbox / radio / switch / 可聚焦卡片 / 图标按钮 / 链接 | 无焦点环 | `outline` 2px + offset 2px | `:focus-visible` |

- 规格：`--sh-focus-ring-width: 2px`、`--sh-focus-ring-offset: 2px`、`--sh-border-focus`（亮 `#171717`、暗 `#e6e6e6`）。
- 文本录入控件不再对 `:focus-visible` 输出 outline：Chrome 对文本输入在鼠标点击时也会命中 `:focus-visible`，若同时保留边框转色与 outline，就会出现「内层边框 + 外层环」的双层圈。本包不附带原工程截图；验收应在当前实现中实际检查。
- **硬约束：禁止用 `box-shadow` 表达 focus**（包括 `box-shadow: 0 0 0 Npx ...` 形式的环）。焦点只能用 `border-color` 或 `outline`，且不允许在同一控件上叠加。
- **禁止**只做 `outline: none` 而不提供替代；非文本控件必须保留 `outline` 焦点环。
- 对比度：焦点指示与相邻底色 ≥ 3:1。实测亮色 `#171717` 对白底 17.9:1、对默认边框 `#e5e5e5` 15.4:1；暗色 `#e6e6e6` 对 `#101010` 15.5:1。
- 高对比度变体：3px 纯黑（亮）/纯白（暗）。

## 3. 键盘路径

| 场景 | 要求 |
|---|---|
| Tab 顺序 | 跟随视觉顺序（页头 → 主操作 → Tab → 正文 → 侧栏），不跳读 |
| 按钮/链接 | Enter / Space 触发 |
| Tabs | 左右方向键切换，Home/End 到首尾；`aria-selected` 同步 |
| Dialog / Sheet | 打开时焦点入内，焦点陷阱，Esc 关闭，关闭后焦点归还触发元素 |
| Dropdown / Select | 上下键移动，Enter 选择，Esc 关闭 |
| Radio | 同组方向键切换，`role="radiogroup"` |
| Checkbox | Space 切换，三态用 `aria-checked="mixed"` |
| Toast | `aria-live="polite"`；错误用 `assertive`，不抢焦点 |

## 4. 动效降级

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

- 只允许 `opacity` / `transform` 动画，时长 ≤ 200ms；页面入场 600ms 仅用于一次性叙事，降级时直接显示。
- 禁止视差滚动、无限循环装饰动画（loading 骨架的 shimmer 在降级时改为静态底色）。
- 状态切换必须在降级模式下仍然可感知（用边框/文字，不依赖位移）。

## 5. 动效时长与缓动

所有过渡只用 `--sh-duration-*` 与 `--sh-ease-*` token，**禁止裸写毫秒或贝塞尔**（运行时扫描要求 0 处裸时长/裸缓动）。

| 场景 | 时长 | 缓动 | 变化属性 | 备注 |
|---|---|---|---|---|
| 悬停 / 按下（按钮、卡片、行、链接） | `--sh-duration-fast`（150ms） | `--sh-ease-standard` | `background-color` / `border-color` / `color` | 只改颜色，不改尺寸与位置 |
| 焦点 | 0（不设过渡） | — | `border-color` / `outline-color` | 焦点必须即时出现，不允许淡入延迟 |
| 展开 / 收起（菜单、下拉、折叠区） | `--sh-duration-base`（200ms） | `--sh-ease-emphasized` | `opacity` + `transform` | 只用 `translateY(-4px → 0)` 与 `scale(0.98 → 1)` |
| 状态切换（Tab 指示条、开关、勾选） | `--sh-duration-fast`（150ms） | `--sh-ease-standard` | `background-color` / `transform` | 开关滑块允许 `translateX` |
| 通知入退场（`.notification`） | 入场 `--sh-duration-base`（200ms）/ 退场 `--sh-duration-fast`（150ms） | 入场 `--sh-ease-emphasized` / 退场 `--sh-ease-in` | `opacity` + `transform` | 对应 `notification-in` / `notification-out` |
| 页面一次性入场（叙事页） | `--sh-duration-slow`（600ms） | `--sh-ease-out` | `opacity` + `transform` | **runtime 未提供通用 `fade-up` 入场**（见 `backlog/gaps.md` G-095）；仅预览/叙事页可自行实现，产品页面不使用 |
| 加载骨架 shimmer | `1.8s`（唯一豁免，写在骨架组件内） | `ease-in-out` | `background-position` | 已被 `prefers-reduced-motion` 覆盖为静态底色 |

规则：

- 单次过渡 ≤ 200ms；超过 200ms 只允许叙事页入场与骨架 shimmer。
- 同一元素不得同时过渡 `color` 与 `transform` 之外的第三个属性；`box-shadow` 不参与过渡（焦点已禁止用 box-shadow）。
- 入场用 `--sh-ease-out`（先快后慢），退场用 `--sh-ease-in`；不用对称缓动制造“回弹”。
- 不使用 `transition: all`；必须列出具体属性。
- 禁止循环动画（除骨架 shimmer），禁止视差与自动播放装饰。
- 降级：`prefers-reduced-motion: reduce` 下所有时长归零，骨架 shimmer 变静态底色（见 §4）。

## 6. 组件矩阵

| 组件 | 角色 / 属性 | 键盘 | 焦点 | 备注 |
|---|---|---|---|---|
| Button | `<button>` / `<a>` | Enter / Space | 必须 | 图标按钮需 `aria-label` |
| Input / Textarea | 原生控件 + `label for` | 原生 | 必须 | 错误用 `aria-invalid` + `aria-describedby` |
| Tabs | `role="tablist"` / `tab` / `tabpanel` | 方向键 | 必须 | 隐藏面板 `hidden` |
| Dialog | `role="dialog"` `aria-modal` | Esc / Tab 陷阱 | 入内 | Radix Dialog |
| DropdownMenu | `role="menu"` / `menuitem` | 上下 / Enter / Esc | 必须 | 触发器 `aria-haspopup="menu"` |
| Table | 原生 `<table>` + `<th scope>` | 原生 | 行内可点需可聚焦 | 数字列 `tabular-nums` |
| Tooltip | `role="tooltip"` | 触发器聚焦即显示 | 继承 | 不可含可点内容 |
| Switch | `button[role="switch"]` + `aria-checked` | Space | 必须 | 即时生效需反馈 |
| Checkbox / Radio | 原生 input 或等价 role | Space / 方向键 | 必须 | 点击 label 同样生效 |
| Sheet | Radix Dialog 语义 | Esc / Tab 陷阱 | 入内 | 关闭归还焦点 |
| Alert | `role="alert"`（错误）/ `status`（提示） | 无 | 无 | 关闭按钮可聚焦 |
| CodeBlock / CopyButton | `pre` + `aria-label` | Enter / Space | 必须 | 复制结果 `aria-live` |

## 7. 国际化与屏幕阅读器

- `lang` 随界面语言切换（`zh-CN` / `en` / `ru`）。
- 图标与纯色块不作为唯一信息来源；状态徽标必须带文字。
- 代码块命令保留英文原文，不翻译。
