# 组件状态矩阵（速查）

图例：`边` = 边框，`底` = 背景，`字` = 文字，`影` = 阴影，`—` = 不适用。
所有值来自 `colors_and_type.css`，实现时不得写死颜色。

**focus 统一约定（1.0.0 起）**：文本录入控件（`input` / `textarea` / `.select-trigger` / 搜索框）只用「边框转 `--sh-border-focus`」表达焦点，不补 outline；非文本控件（按钮 / Tab / checkbox / radio / switch / 可聚焦卡片 / 链接）只在键盘态用 `outline: var(--sh-focus-ring-width) solid var(--sh-border-focus)` + `outline-offset: var(--sh-focus-ring-offset)`。同一控件不得同时出现边框转色与外环，禁止 `box-shadow` 焦点环。详见 `specs/accessibility.md` §2。

| 组件 | default | hover | active / pressed | focus-visible | disabled | loading | error |
|---|---|---|---|---|---|---|---|
| Button | 主：`--sh-primary-base` 底 + `--sh-primary-foreground` 字；outline：`--sh-bg-page` 底 + `--sh-border-default` 边 | 主：`--sh-primary-hover`；outline：`--sh-interaction-hover-bg` + hover 边 | `.is-active` 使用 `scale(.97)` | `outline: var(--sh-focus-ring-width) solid var(--sh-border-focus)`，offset `var(--sh-focus-ring-offset)` | `--sh-bg-muted` 底 + `--sh-text-disabled` 字 + `--sh-border-default` 边，光标禁止；不统一降透明度 | 文案不变，左侧 14px 旋转图标，宽度不变 | — |
| Input | `--sh-bg-page` 底、`--sh-border-default` 边、6px 圆角 | `border` 加深 | — | 边框转 `border-focus`，无外环、无 box-shadow | `--sh-bg-muted` 底、`--sh-text-disabled` 字 | 右侧 16px 旋转图标，禁止输入 | 边 `destructive`、字 `destructive`、`aria-invalid` |
| Textarea | 同 Input，圆角 6px，最小高 100px | 同 Input | — | 同 Input | 同 Input | 同 Input | 同 Input |
| Card | 白/`card` 底、1px `--sh-border-default`、12px 圆角、`shadow-card` | 可点卡片：hover 浅底 + hover 边 + `shadow-card-hover`，不位移 | 无额外位移 | 整卡焦点环（仅可点卡） | 50% 不透明 | 骨架三行（`skeleton`） | — |
| Badge | 软底 + 同色系深字；生命周期/结论为 6px 圆角，标签/命名空间为胶囊 | 无 | 无 | 无（不可交互） | 50% 不透明 | — | 错误状态用 `destructive` 软底 |
| Tabs | 透明底、`text-secondary` | `foreground` + `muted` 底 | 同 hover | 2px 焦点环在 tab 内 | 50% 不透明、不可点 | tab 内 12px 旋转图标 | — |
| Table | 行高 40 / 52 / 70（单 / 双 / 三行）、发丝线分隔、表头 13px `text-secondary` + `bg-muted` 底（暗色 `#2a2a2a`）、行头 `th[scope="row"]` 与数据行同规格 | 行 `muted/50` 底 | 行 `muted` 底 | 行内按钮焦点环 | 行 50% 不透明 | 三行骨架，保留表头 | 单元格字 `destructive` + 图标 |
| DropdownMenu | 触发器按钮态；菜单 `popover` 底 + 1px 边 + `--sh-shadow-popover` | 菜单项 `muted` 底 | 菜单项 `muted` 加深 | 菜单项焦点环 | 菜单项 50%、不可点 | 菜单内单行 loading | — |
| Dialog | 遮罩 40% 黑；面板 `card` 底、12px 圆角、`shadow-dialog` | 关闭按钮 `muted` 底 | 按钮下移 1px | 面板内首个可聚焦元素 | 确认按钮 50% | 确认按钮 loading，Esc 仍可关 | 表单字段错误 + 顶部 Alert |
| EmptyState | 24px 图标 + 标题 + 说明，居中，白底 | 主按钮 hover | 主按钮 active | 主按钮焦点环 | — | — | 错误空态图标用 `destructive` |
| Skeleton | `muted` 底、圆角 6px | — | — | — | — | shimmer 1.8s；降级为静态 | — |
| Pagination | 28px 方块、透明底、`text-secondary` | `muted` 底 | `muted` 加深 | 焦点环 | 50%、不可点 | 当前页按钮 loading | — |
| CodeBlock | `--sh-bg-muted` 底 + `--sh-border-default` 边、`--sh-radius-sm`(8px)、等宽 `--sh-text-mono-sm` | 复制按钮 `muted` 底加深 | 复制按钮按下 | 复制按钮焦点环（`--sh-focus-ring-*`） | 复制按钮 50% | 复制按钮旋转图标 | 复制失败文字 `destructive` + 1.5s |
| Breadcrumb | 13px、`text-secondary`、`/` 分隔 | `foreground` 字 | — | 链接焦点环 | — | — | — |
| Alert | 软底 + 1px 边 + 16px 图标；info/success/warning/danger | 关闭按钮 `muted` 底 | 关闭按钮下移 | 关闭按钮焦点环 | — | — | danger 变体即错误态，`role="alert"` |
| Tooltip | `popover` 底 + 1px 边 + `shadow-card`，12px 字，6px 内边距 | — | — | 触发器聚焦即显示 | — | — | — |
| Avatar | 圆形、1px 边、首字母或图片；24/32/40 | 可点头像加 `muted` 环 | — | 焦点环 | 50% | 加载态用占位圆 + shimmer | 加载失败回落首字母 |
| Checkbox | 16px 方框、`border` 边、4px 圆角 | `border` 加深 | 勾选动画 120ms | 焦点环 + 2px offset | `muted` 底、不可点 | 勾选框内 12px 旋转图标 | 边 `destructive` + 错误文案 |
| Radio | 16px 圆、`border` 边 | `border` 加深 | 内点 120ms 出现 | 焦点环 | `muted` 底 | — | 边 `destructive` + 错误文案 |
| Switch | 32×18 / 36×20，`muted` 轨道 + 白滑块 | 轨道 `border` 加深 | 滑块拉伸 2px | 焦点环 | 轨道 50%、不可点 | 滑块内 10px 旋转图标 | — |
| Sheet | 遮罩 40%；右/底面板 `card` 底、`shadow-dialog`、档位 sm/md/lg = 360/480/640（`border-box` 外框宽度） | 关闭按钮 `muted` 底 | 下移 1px | 面板内首个可聚焦元素 | 底部主按钮 50% | 面板内骨架或按钮 loading | 面板顶部 Alert |
| CopyButton | 28/32px、透明底、`text-secondary` | `muted` 底 | 下移 1px | 焦点环 | 50% | 旋转图标，禁止重复点击 | 失败切换 `destructive` 图标并 1.5s 复原 |
| FormField | label 12px/500 + 控件 + 12px 帮助文案，字段间距 24 | 帮助文案不变 | — | 控件边框转 `border-focus`，无外环 | 整组 50% | 控件右侧 loading | 控件边/文案 `destructive`，`aria-describedby` |

## 宿主标签解耦（自 1.3 冻结版起）

**硬规则**

1. 组件类不得假定宿主标签。可交互 / 可复用的类（按钮、菜单项、选项卡、分页项、开关、复选行、单选行、卡片可点击行、徽标、面包屑项、导航项、图标按钮等）必须在 `components.runtime.css` 中同时复位 `button` 与 `a` 的 UA 默认值。
2. 复位写在组件类自身，使用「定向复位」；禁止用全局 `*` 或 `button, input, a {}` 重置污染消费方页面。
3. 预览页每个交互组件至少覆盖 `div` 与 `button`/`a` 两种宿主。新增组件或改动交互类时，必须补真实标签用法（参考 Button / DropdownMenu / Tabs 的「宿主标签解耦」区块）。
4. 交互类的最小复位集合：`appearance: none`、`border`（无描边时显式 `border: 0`）、`background`、`font-family`（或 `font: inherit`）、`text-align`、`text-decoration`、`box-sizing: border-box`，以及会被 UA 撑高或内缩的 `margin` / `padding`。
5. 验收以计算样式为准：同一类在 `div` / `button` / `a` / `label` 上的 `border`、`background`、`font-family`、`text-decoration`、`text-align`、`box-sizing` 必须逐项一致。

**UA 默认值对照**

| UA 默认 | `button` | `a` | `input` / `label` |
|---|---|---|---|
| 边框 | `2px outset` | 无 | `input` 原生内描边 |
| 底色 | `buttonface` 灰 | 透明 | `input` 为 field 色 |
| 字体 | 系统字体，不继承 | 继承 | `input` 不继承 |
| 文字装饰 | 无 | 下划线 | 无 |
| 文字对齐 | 居中 | 继承 | 左对齐 |
| 盒模型 | `border-box` | `content-box` | 多为 `content-box` |
| appearance | 原生控件 | — | 原生控件 |

## 类名作用域与保留类名（1.0.1 起）

**硬规则**

1. 组件的内部结构类必须写在所属组件根选择器下，不得以裸类名出现在 `components.runtime.css`。例如 Card 的标题区只能是 `.card > .card-head`，不能是 `.card-head`。
2. 页面不得在组件外部复用下表中「保留全局」的类名。页面需要同名语义时，必须加页面前缀（如 `.dk-`、`.pg-`）。
3. `components.preview.css` 中的单词级类只服务预览页，页面不得引用；页面也不得依赖这些类获得样式。

**已收紧（内部结构 → 组件作用域）**

| 原裸名 | 收紧后 | 所属组件 | 说明 |
|---|---|---|---|
| `.card-head` | `.card > .card-head` | Card | 页面同名 `.card-head` 不再被纵向规则接管 |
| `.card-title` / `.card-desc` | `.card .card-title, .skill-card .card-title` / 同名 desc | Card、SkillCard | 两处消费方都在作用域内 |
| `.rows` | `.card .rows` | Card | 原误落在 preview CSS，运行时缺失 |
| `.meta` | `.card-meta` | Card | 与预览页的 `.meta` 胶囊列表彻底分离 |
| `.dot` | `.card-dot`（页面侧用 `.status-dot`） | Card | 详情页改用 `.status-dot`，不再复用裸 `.dot` |
| `.code` | `.card .code` | Card | 同时修掉 `.copy` 被代码块按钮样式接管的既有冲突 |
| `.copy` | `.card .copy, .code-block .copy` | Card、CodeBlock | 复制按钮的绝对定位不再作用到卡片文案 |
| `.glyph` | `.copy-btn .glyph` | CopyButton | Toast 继续走 `.toast .glyph` |
| `.version` / `.action` / `.action + .action` | `.data-table .version` / `.data-table .action` | DataTable | 表格外的同名类不再获样式 |
| `.ellipsis` | `.pagination .ellipsis` | Pagination | — |

**已确认只在 preview CSS（不进入产品）**

`.head`、`.body`、`.desc` 在库内不存在；`.foot`、`.title`、`.state`、`.grid`、`.meta`、`.dot`、`.crumb`、`.sk`、`.sk-pill`、`.h12`、`.h16`、`.h20`、`.w14`、`.w18`、`.w40`、`.w45`、`.w64`、`.w70`、`.w72`、`.w80`、`.w90`、`.w100`、`.mt-2`、`.status` 均只出现在 `components.preview.css`（拆分脚本的 `PREVIEW_CLASSES` 负责拦截）。

**保留全局（公开 API 或跨组件工具类，页面禁止复用同名类）**

- 组件根：`.alert`、`.avatar`、`.badge`、`.btn`、`.card`、`.dialog`、`.field`、`.input`、`.label`、`.lifecycle`、`.menu`、`.pagination`、`.sidenav`、`.switch`、`.tab`、`.tabs`、`.textarea`、`.toast`、`.tooltip`、`.verdict`。
- 跨组件工具：`.icon`（图标槽，14px）、`.spinner`（加载态；默认 `inline-block` 流内，用于 `.loading-chip`、状态行等）、`.spinner--overlay`（绝对定位覆盖，仅用于输入框尾随图标 / 按钮图标位）、`.help`、`.hint`、`.error`、`.counter`、`.req`（表单辅助文案与计数）。
- `.menu` 与 `.tooltip` 无法在不破坏公开 API 的前提下收紧，页面若需要自己的菜单/浮层，必须使用带前缀的类名。
