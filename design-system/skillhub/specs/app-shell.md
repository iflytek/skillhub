# 应用外壳套件（App Shell Suite）

> 版本：1.3.1
> 组件：`app-shell` / `top-nav` / `sidenav` / `page-header` / `page-tabs` / `search-input` / `method-label`
> 定位：只做骨架与槽位，**不定义品牌皮肤**。所有尺寸、间距、颜色必须来自 `colors_and_type.css` 的 `--sh-*` token。

---

## 1. 装配关系

```
.app-shell
├─ .top-nav                       （第一行，常驻）
└─ .app-shell-main                （第二行）
   ├─ .sidenav                   （左列，--sh-size-sidebar）
   └─ .app-shell-content          （右列，独立滚动）
      └─ .page-header            （标题区）
         └─ .page-tabs           （data-with-tabs 时）
      └─ 业务区块
```

规则：

1. 一个页面只允许一个 `.app-shell`、一个 `.top-nav`、一个 `.page-header`。
2. `.app-shell-content` 是唯一滚动容器；`.top-nav` 与 `.sidenav` 不随内容滚动。
3. `.page-header[data-with-tabs="true"]` 时必须紧跟 `.page-tabs`，且页头自身不再画底线。
4. 外壳内不嵌入业务卡片皮肤、品牌渐变或插画。

---

## 2. 使用规则（什么时候用 / 什么时候不许用）

### 2.1 `app-shell`

**用**
- 控制台、后台、工作台等需要常驻导航的页面。
- 需要把顶栏、侧栏、内容区明确分区的页面。

**不许用**
- 登录页、落地页、公开技能详情页等没有常驻导航的页面。
- 弹窗、抽屉内部（这些是局部容器，不是应用外壳）。
- 需要三列以上布局的页面；第三列由页面在 `.app-shell-content` 内自行组织。

**契约要点**
- 两行：`auto / minmax(0,1fr)`；两列：`--sh-size-sidebar / minmax(0,1fr)`。
- ≤1024px 折叠为单列并隐藏侧栏；抽屉/汉堡菜单不在本组件内。

### 2.2 `top-nav`

**用**
- 全站导航、控制台顶栏、管理员入口。

**不许用**
- 页面内局部工具条（用 `.filter-bar` 或 `.page-header-actions`）。
- 弹窗、卡片头部（用对应容器头部槽位）。
- 主导航项超过 5 个的复杂导航；超出部分收进产品层菜单。

**契约要点**
- `min-height: --sh-space-12`（48px），单行，不换行。
- 品牌位只放文字 + 方形标记，不使用图片 Logo。
- ≤768px 隐藏 `.top-nav-links`，不压缩字号。

### 2.3 `sidenav`

**用**
- 与 `.app-shell` 组合的控制台二级导航。
- 需要分组（工作台 / 管理）与数量提示的导航。

**不许用**
- 单页详情或设置页的局部切换（用 `.tabs` / `.page-tabs`）。
- 三级以上树形导航。
- 需要品牌运营位或插画的侧栏。

**契约要点**
- 宽度 `--sh-size-sidebar`，条目高 `--sh-control-md-height`。
- 计数只用等宽数字，不使用彩色徽标。
- ≤1024px 由 `.app-shell` 隐藏，不自行实现折叠。

### 2.4 `page-header`

**用**
- 控制台页、详情页、审核页的顶部标题区。
- 需要面包屑 + 标题 + 状态 + 描述 + 动作的页面。
- 需要页内 Tab 的详情页（`data-with-tabs="true"`）。

**不许用**
- 弹窗、抽屉、卡片内部。
- 同一页面出现第二个 `.page-header`（分区标题用 `.block-title`）。
- 页头内塞搜索框或筛选器（属于内容区工具条）。

**契约要点**
- 标题 `--sh-text-h4`；描述最多 `--sh-size-container-reading`。
- 状态徽标与标题同一行，最多 2 个。
- ≤640px 标题区与动作区纵排。

### 2.5 `page-tabs`

**用**
- 页头内嵌的分区切换（概览 / 文件 / 版本 / 审核）。
- 需要展示数量的分区（`.tab-count`）。

**不许用**
- 两个以下选项（用并列按钮或 segmented）。
- 独立页头内单独出现（必须与 `data-with-tabs="true"` 的页头组合）。
- 需要彩色胶囊或滑动指示条的 Tab。

**契约要点**
- 与 `.tabs-list` 共用一套容器：gap 24px、字号 13px、1px 底线。
- 激活项 2px 下划线，用 `calc(-1 * var(--sh-size-border-width-strong))` 压线。
- 计数胶囊 16px 高，中性底色。

### 2.6 `search-input`（搜索框图标槽）

**用**
- 列表、搜索页、审核队列顶部的关键词检索框。
- 需要在输入框左侧放一枚装饰性图标（放大镜）时。

**不许用**
- 普通表单字段（用 `input` / `form-field`，不要无意义加图标）。
- 图标需要点击（图标是装饰，必须 `aria-hidden` + `pointer-events:none`）。
- 需要在右侧追加第二个动作图标（当前契约只有前缀槽）。

**契约要点**
- 组合：`div.input-group` → `.input-prefix`（图标）+ `.input`。
- 容器 `position: relative`；前缀 `left: var(--sh-space-3)`、垂直居中；输入框左内边距 `calc(var(--sh-space-8) + var(--sh-space-1))`（36px）。
- 图标 16px，颜色 `--sh-text-placeholder`；不得改输入框高度、圆角或字号。
- 与筛选条组合时，在 `.input-group` 上追加 `.filter-search` 获得增长宽度；430px 下仍不溢出。

### 2.7 `method-label`（安装方式小标题）

**用**
- 安装命令上方，标注 CLI 来源（SkillHub CLI / ClawHub CLI）。
- 需要左右两端对齐的一行说明：左侧名称、右侧「推荐 / compat」标记。

**不许用**
- 页面区块标题（用 `section-head` / `h4`）。
- 需要背景、描边或胶囊的标签（用 `badge`）。
- 多个 CLI 合并成一行展示（一行只描述一种来源）。

**契约要点**
- 12px / 16px 行高，三级文字色 `--sh-text-tertiary`，左右两端对齐，gap 8px。
- 左名称在长文案下可换行，右标记保持贴右；不得改成上下堆叠。
- 与 `.code-block` 紧邻，中间不加分割线。

---

## 3. 主题与响应式

- 主题：祖先挂 `.dark`，所有颜色由 token 自动解析；组件内不写裸 hex / rgba。
- 断点：
  - `≤1024px`：`.app-shell` 单列、`.sidenav` 隐藏。
  - `≤768px`：`.top-nav-links` 隐藏。
  - `≤640px`：`.page-header-main` 纵排；`.page-tabs` 允许横向滚动（由产品层决定，组件默认不隐藏）。

---

## 4. 禁止清单（Do Not Invent）

- 品牌皮肤：渐变、专用字体、插画、运营位。
- 顶栏双层/多行、侧栏三级树、页头吸顶阴影。
- Tab 滑动指示条、彩色胶囊、图标 + 双行文字。
- 在组件 CSS 里写任何裸色值（`#...` / `rgb()` / `rgba()`）。

---

## 5. 验收口径

1. 库校验 `failures=0`、`undefinedCssVars=0`。
2. runtime CSS 不新增裸 hex / rgba。
3. 1440 / 1100 / 430 × 亮 / 暗：`scrollWidth == clientWidth`，控制台 0 错误。
4. 组件装配：外壳内不出现第二个 `.top-nav` / `.page-header`；带 Tab 页头只有一条 1px 底线。
