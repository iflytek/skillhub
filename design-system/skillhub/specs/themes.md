# 主题 Token 映射（亮 / 暗）

来源：`colors_and_type.css`；用仓库内读取工具核对 `tokens.full.json` 投影。

分组参考内置库（TraeWork 的 bg / text / icon / border / accent / status 语义分组、Doubao 的“暗色表面逐层提亮”写法），保留 `--sh-` 品牌前缀。**共 17 个颜色组、123 个颜色 token**：62 个字面值 token + 61 个别名 token（interaction 4、tone 15、status 6、lifecycle 16、verdict 12、chart 3、viz 3、bg 1、primary 1）。别名不存字面值，亮暗值都随目标 token 自动解析；另有 5 个阴影别名（`--sh-shadow-*` → `--shadow-*`）在“阴影”一节说明。

| 组 | 数量 | 类型 |
|---|---|---|
| bg | 9 | 8 字面值 + 1 别名 |
| text | 7 | 字面值 |
| border | 6 | 字面值 |
| primary | 6 | 5 字面值 + 1 别名 |
| accent | 5 | 字面值 |
| interaction | 4 | **别名** |
| tone | 15 | **别名** |
| status | 8 | 2 字面值 + 6 别名 |
| lifecycle | 16 | **别名** |
| verdict | 12 | **别名** |
| success | 5 | 字面值 |
| warning | 5 | 字面值 |
| danger | 6 | 字面值 |
| info | 5 | 字面值 |
| chart | 4 | 1 字面值 + 3 别名 |
| viz | 3 | **别名** |
| code | 7 | 字面值 |
| **合计** | **123** | 62 字面值 + 61 别名 |

**暗色不是反相**：暗色表面为纯中性灰阶（HSL 饱和度 0%），从 sunken 3.5% → page 6.3% → raised 9.4% → overlay 13.3% → muted 16.5% 逐层提亮；边框与文字不含蓝色色偏；阴影退化为 1px 描边 + 极低透明底影；禁用文字使用独立 token，不靠 opacity。

来源标记：**现有**=来自 `web/src`；**推导**=按既有色阶重算或为满足对比度微调；**扩增项**=本库新增、需评审。暗色值统一标为「推导（暗色重算）」。

## bg（9）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-bg-scrim` | `#000000` | `#000000` | 遮罩底（配 40% alpha） | 现有 / 推导（暗色重算） |
| `--sh-bg-inverse` | `#17191d` | `#ededed` | 反色块 | 推导 / 推导（暗色重算） |
| `--sh-bg-muted` | `#efeff0` | `#2a2a2a` | 次级表面 / 标签底 / input、textarea 禁用底 | 现有 / 推导（暗色重算） |
| `--sh-bg-sunken` | `#f6f6f7` | `#090909` | 次级 / 工具条 / 禁用表面底（bulk-bar、filter-bar、sidenav、tab-count、menu-item 禁用、btn-secondary hover、top-nav、upload-field、skeleton shimmer 中段） | 现有 / 推导（暗色重算） |
| `--sh-bg-page` | `#ffffff` | `#101010` | 页面底色 / input、textarea 默认底 | 现有 / 推导（暗色重算） |
| `--sh-bg-raised` | `#ffffff` | `#181818` | 卡片 / 侧栏 / 弹窗内容面 | 现有 / 推导（暗色重算） |
| `--sh-bg-overlay` | `#ffffff` | `#222222` | 下拉菜单 / popover | 现有 / 推导（暗色重算） |
| `--sh-bg-glass` | `#ffffff` | `#181818` | 毛玻璃表面（配 72% alpha） | 推导 / 推导（暗色重算） |
| `--sh-switch-track` | → `--sh-border-contrast` | 同左 | Switch 关闭态轨迹（非文本 3:1 对比；1.1.0 收敛为 border-contrast 别名） | 1.0.1 扩增 / 1.1.0 收敛 |

## text（7）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-text-primary` | `#171717` | `#f4f4f4` | 主文字 | 推导 / 推导（暗色重算） |
| `--sh-text-secondary` | `#575757` | `#b9b9b9` | 次要文字 | 推导 / 推导（暗色重算） |
| `--sh-text-link` | `#156f99` | `#9bc1d4` | 链接 | 现有 / 推导（暗色重算） |
| `--sh-text-tertiary` | `#737373` | `#909090` | 辅助文字 / 表头 | 现有 / 推导（暗色重算） |
| `--sh-text-placeholder` | `#767676` | `#8a8a8a` | 输入占位 | 推导 / 推导（暗色重算） |
| `--sh-text-disabled` | `#949494` | `#6e6e6e` | 禁用文字 | 扩增 / 推导（暗色重算） |
| `--sh-text-inverse` | `#ffffff` | `#101010` | 反色文字 | 推导 / 推导（暗色重算） |

## border（6）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-border-focus` | `#171717` | `#e6e6e6` | 焦点环 | 现有 / 推导（暗色重算） |
| `--sh-border-contrast` | `#8f8f8f` | `#787878` | 控件必需边界（≥3:1） | 扩增 / 推导（暗色重算） |
| `--sh-border-strong` | `#d4d4d4` | `#3e3e3e` | 强调容器描边 | 扩增 / 推导（暗色重算） |
| `--sh-border-default` | `#e5e5e5` | `#303030` | 容器 1px 描边 | 推导 / 推导（暗色重算） |
| `--sh-border-subtle` | `#f0f0f0` | `#242424` | 装饰性分隔线 | 推导 / 推导（暗色重算） |
| `--sh-border-inverse` | `#ffffff` | `#ffffff` | 反色描边 | 推导 / 推导（暗色重算） |

## primary（6）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-primary-active` | `#151515` | `#d4d4d4` | 主按钮 pressed | 推导 / 推导（暗色重算） |
| `--sh-primary-base` | `#212121` | `#ededed` | 主按钮底 | 现有 / 推导（暗色重算） |
| `--sh-primary-hover` | `#3a3a3a` | `#ffffff` | 主按钮 hover | 推导 / 推导（暗色重算） |
| `--sh-primary-disabled` | → `--sh-border-strong` | 同左 | 禁用主按钮底 | 扩增 / 1.1.0 收敛为别名 |
| `--sh-primary-soft` | `#f2f2f2` | `#262626` | 次级按钮底 | 推导 / 推导（暗色重算） |
| `--sh-primary-foreground` | `#ffffff` | `#101010` | 主色上的文字 | 现有 / 推导（暗色重算） |

## accent（5）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-accent-foreground` | `#171717` | `#e5e5e6` | 强调块文字 | 现有 / 推导（暗色重算） |
| `--sh-accent-border` | `#d1def0` | `#404244` | 强调块描边 | 推导 / 推导（暗色重算） |
| `--sh-accent-hover` | `#e2edfa` | `#2d2e2f` | 更强 hover 底 | 扩增 / 推导（暗色重算） |
| `--sh-accent-base` | `#eef4fc` | `#232324` | hover 底 / 静态浅强调底（选中态见 `--sh-interaction-selected-bg`） | 现有 / 推导（暗色重算） |
| `--sh-accent-soft` | `#f4f8fd` | `#19191a` | 最浅强调底 | 推导 / 推导（暗色重算） |

## interaction（4，全部别名）

> 别名 token：亮值与暗值都不单独存字面值，统一写成解析目标；目标在 `.dark` 下重新赋值后自动跟随。

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-interaction-hover-bg` | `--sh-accent-base` | hover 底 |
| `--sh-interaction-hover-border` | `--sh-accent-border` | hover 描边 |
| `--sh-interaction-pressed-bg` | `--sh-accent-hover` | pressed 底 |
| `--sh-interaction-selected-bg` | `--sh-primary-soft` | 选中底 |

## tone（15，全部别名）

> 5 个色调 × weak / fg / hover。全部别名到既有语义色，8 个版本态与 6 个安全结论收敛到 5 档；亮暗随目标 token 解析。

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-tone-neutral-weak` | `--sh-bg-muted` | 中性徽标底 |
| `--sh-tone-neutral-fg` | `--sh-text-secondary` | 中性徽标文字 |
| `--sh-tone-neutral-hover` | `--sh-border-default` | 中性 hover / 描边 |
| `--sh-tone-success-weak` | `--sh-success-weak` | 成功徽标底 |
| `--sh-tone-success-fg` | `--sh-success-on-weak` | 成功徽标文字 |
| `--sh-tone-success-hover` | `--sh-success-border` | 成功描边 |
| `--sh-tone-warning-weak` | `--sh-warning-weak` | 警告徽标底 |
| `--sh-tone-warning-fg` | `--sh-warning-on-weak` | 警告徽标文字 |
| `--sh-tone-warning-hover` | `--sh-warning-border` | 警告描边 |
| `--sh-tone-danger-weak` | `--sh-danger-weak` | 危险徽标底 |
| `--sh-tone-danger-fg` | `--sh-danger-on-weak` | 危险徽标文字 |
| `--sh-tone-danger-hover` | `--sh-danger-border` | 危险描边 |
| `--sh-tone-info-weak` | `--sh-info-weak` | 信息徽标底 |
| `--sh-tone-info-fg` | `--sh-info-on-weak` | 信息徽标文字 |
| `--sh-tone-info-hover` | `--sh-info-border` | 信息描边 |

## status（8）

> 唯一的状态调色板。base 绑定 success / warning / danger，weak 绑定对应 tint；仅 archived 保留独立灰，用于“已归档 / 草稿”这类中性状态。

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-status-published` | `--sh-success-base` | 已发布徽标文字 |
| `--sh-status-published-weak` | `--sh-success-weak` | 已发布徽标底 |
| `--sh-status-review` | `--sh-warning-base` | 待审核徽标文字 |
| `--sh-status-review-weak` | `--sh-warning-weak` | 待审核徽标底 |
| `--sh-status-archived` | `#656d79` / `#a1a1a1`（保留字面值） | 已归档徽标文字 |
| `--sh-status-archived-weak` | `#f1f2f4` / `#232324`（保留字面值） | 已归档徽标底 |
| `--sh-status-rejected` | `--sh-danger-base` | 已拒绝徽标文字 |
| `--sh-status-rejected-weak` | `--sh-danger-weak` | 已拒绝徽标底 |

## lifecycle（16，全部别名）

> 8 个版本状态 × base / weak，全部降级为域词汇别名；不再持有独立颜色。weak 只做弱底，状态点/文字用 base。

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-lifecycle-draft` | `--sh-status-archived` | 草稿文字 |
| `--sh-lifecycle-draft-weak` | `--sh-status-archived-weak` | 草稿弱底 |
| `--sh-lifecycle-scanning` | `--sh-info-base` | 扫描中文字 |
| `--sh-lifecycle-scanning-weak` | `--sh-info-weak` | 扫描中弱底 |
| `--sh-lifecycle-scan-failed` | `--sh-status-rejected` | 扫描失败文字 |
| `--sh-lifecycle-scan-failed-weak` | `--sh-status-rejected-weak` | 扫描失败弱底 |
| `--sh-lifecycle-uploaded` | `--sh-text-secondary` | 已上传文字 |
| `--sh-lifecycle-uploaded-weak` | `--sh-status-archived-weak` | 已上传弱底 |
| `--sh-lifecycle-pending-review` | `--sh-status-review` | 待审文字 |
| `--sh-lifecycle-pending-review-weak` | `--sh-status-review-weak` | 待审弱底 |
| `--sh-lifecycle-published` | `--sh-status-published` | 已发布文字 |
| `--sh-lifecycle-published-weak` | `--sh-status-published-weak` | 已发布弱底 |
| `--sh-lifecycle-rejected` | `--sh-status-rejected` | 已驳回文字 |
| `--sh-lifecycle-rejected-weak` | `--sh-status-rejected-weak` | 已驳回弱底 |
| `--sh-lifecycle-yanked` | `--sh-text-primary` | 已撤回文字 |
| `--sh-lifecycle-yanked-weak` | `--sh-bg-muted` | 已撤回弱底 |

## verdict（12，全部别名）

> 6 个安全结论 × base / weak，全部降级为域词汇别名。BLOCKED 是唯一使用实心 danger 的结论，其余用 tint 胶囊，不新增第 6 个色相。

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-verdict-scanning` | `--sh-info-base` | 扫描中文字 |
| `--sh-verdict-scanning-weak` | `--sh-info-weak` | 扫描中弱底 |
| `--sh-verdict-scan-failed` | `--sh-status-archived` | 扫描失败文字 |
| `--sh-verdict-scan-failed-weak` | `--sh-status-archived-weak` | 扫描失败弱底 |
| `--sh-verdict-safe` | `--sh-status-published` | 安全文字 |
| `--sh-verdict-safe-weak` | `--sh-status-published-weak` | 安全弱底 |
| `--sh-verdict-suspicious` | `--sh-warning-base` | 可疑文字 |
| `--sh-verdict-suspicious-weak` | `--sh-warning-weak` | 可疑弱底 |
| `--sh-verdict-dangerous` | `--sh-status-rejected` | 危险文字 |
| `--sh-verdict-dangerous-weak` | `--sh-status-rejected-weak` | 危险弱底 |
| `--sh-verdict-blocked` | `--sh-text-primary` | 已拦截文字（实心） |
| `--sh-verdict-blocked-weak` | `--sh-bg-muted` | 已拦截弱底 |

## success（5）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-success-on-weak` | `#135c3a` | `#b6d8c8` | 成功弱底上的文字 | 推导 / 推导（暗色重算） |
| `--sh-success-hover` | `#186a42` | `#9bcfb7` | 成功按钮 hover | 推导 / 推导（暗色重算） |
| `--sh-success-base` | `#1f7a4d` | `#83c3a5` | 成功图标 / 文字 | 推导 / 推导（暗色重算） |
| `--sh-success-border` | `#a7d9bf` | `#34463f` | 成功弱底描边 | 扩增 / 推导（暗色重算） |
| `--sh-success-weak` | `#e8f5ee` | `#161d1a` | 成功提示底 | 现有 / 推导（暗色重算） |

## warning（5）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-warning-on-weak` | `#7a4f00` | `#ddceb0` | 警告弱底上的文字 | 扩增 / 推导（暗色重算） |
| `--sh-warning-hover` | `#855a00` | `#d6c094` | 警告按钮 hover | 推导 / 推导（暗色重算） |
| `--sh-warning-base` | `#9a6a00` | `#cdb17a` | 警告图标 / 文字 | 推导 / 推导（暗色重算） |
| `--sh-warning-border` | `#e8c98f` | `#484132` | 警告弱底描边 | 扩增 / 推导（暗色重算） |
| `--sh-warning-weak` | `#fdf3e0` | `#1e1b15` | 警告提示底 | 扩增 / 推导（暗色重算） |

## danger（6）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-danger-on-weak` | `#8f1c12` | `#f0c4c0` | 危险弱底上的文字 | 扩增 / 推导（暗色重算） |
| `--sh-danger-hover` | `#97190f` | `#ebc0bc` | 危险按钮 hover | 推导 / 推导（暗色重算） |
| `--sh-danger-base` | `#b42318` | `#e3a6a0` | 危险图标 / 文字 | 推导 / 推导（暗色重算） |
| `--sh-danger-border` | `#eeb4ae` | `#483432` | 危险弱底描边 | 扩增 / 推导（暗色重算） |
| `--sh-danger-weak` | `#fdeceb` | `#1e1615` | 危险提示底 | 扩增 / 推导（暗色重算） |
| `--sh-danger-foreground` | `#ffffff` | `#1e1615` | 危险按钮文字 | 推导 / 推导（暗色重算） |

## info（5）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-info-on-weak` | `#104d72` | `#c1d6e1` | 信息弱底上的文字 | 推导 / 推导（暗色重算） |
| `--sh-info-hover` | `#0f5279` | `#a6c8d9` | 信息按钮 hover | 推导 / 推导（暗色重算） |
| `--sh-info-base` | `#14618f` | `#89b6cd` | 信息图标 / 文字 | 现有 / 推导（暗色重算） |
| `--sh-info-border` | `#a9cfe4` | `#314149` | 信息弱底描边 | 推导 / 推导（暗色重算） |
| `--sh-info-weak` | `#e8f2fb` | `#141b1f` | 信息提示底 | 现有 / 推导（暗色重算） |

## chart（4）

> 仅 `chart-3` 保留字面值（灰阶中点没有等价语义 token）；其余绑定 core scale。库内暂无图表组件，保留供产品图表接入。

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-chart-1` | `--sh-primary-base` | 图表序列 1（主） |
| `--sh-chart-2` | `--sh-text-tertiary` | 图表序列 2 |
| `--sh-chart-3` | `#a3a3a3` / `#7a7a7a`（保留字面值） | 图表序列 3 |
| `--sh-chart-4` | `--sh-border-strong` | 图表序列 4（网格弱） |

## viz（3，全部别名）

| 变量 | 解析为 | 用途 |
|---|---|---|
| `--sh-viz-accent` | `--sh-text-link` | 图表强调 / 高亮 |
| `--sh-viz-axis` | `--sh-text-tertiary` | 图表坐标轴 |
| `--sh-viz-grid` | `--sh-border-default` | 图表网格 |

## code（7）

| 变量 | 亮值 | 暗值 | 用途 | 来源 |
|---|---|---|---|---|
| `--sh-code-bg` | `#1a202c` | `#0c0c0c` | 代码块底 | 现有 / 推导（暗色重算） |
| `--sh-code-comment` | `#6a9955` | `#7f8c74` | 注释 | 扩增 / 推导（暗色重算） |
| `--sh-code-string` | `#ce9178` | `#ce9178` | 字符串 | 扩增 / 推导（暗色重算） |
| `--sh-code-tag` | `#d7ba7d` | `#d7ba7d` | 标签 | 扩增 / 推导（暗色重算） |
| `--sh-code-number` | `#b5cea8` | `#b5cea8` | 数字 | 扩增 / 推导（暗色重算） |
| `--sh-code-keyword` | `#9cdcfe` | `#9cdcfe` | 关键字 | 扩增 / 推导（暗色重算） |
| `--sh-code-fg` | `#e5e7eb` | `#e5e7eb` | 代码块正文 | 现有 / 推导（暗色重算） |

## 对比度实测（WCAG 2.1 相对亮度公式，脚本计算）

以下 37 组为本轮重算后的实测值。亮色 16 组沿用未改动值；暗色 21 组按新中性灰阶重算。

| # | 场景 | 前景 | 背景 | 实测 | 下限 | 结果 |
|---|---|---|---|---|---|---|
| 1 | 亮：正文 / 页面 | `#171717` | `#ffffff` | 17.93:1 | 4.5:1 | PASS |
| 2 | 亮：次要文字 / 页面 | `#575757` | `#ffffff` | 7.23:1 | 4.5:1 | PASS |
| 3 | 亮：辅助文字 / 页面 | `#737373` | `#ffffff` | 4.74:1 | 4.5:1 | PASS |
| 4 | 亮：占位文字 / 页面 | `#767676` | `#ffffff` | 4.54:1 | 4.5:1 | PASS |
| 5 | 亮：禁用文字 / 页面 | `#949494` | `#ffffff` | 3.03:1 | 3:1 | PASS |
| 6 | 亮：链接 / 页面 | `#156f99` | `#ffffff` | 5.58:1 | 4.5:1 | PASS |
| 7 | 亮：控件边界 / 页面 | `#8f8f8f` | `#ffffff` | 3.23:1 | 3:1 | PASS |
| 8 | 亮：装饰分隔 / 页面 | `#f0f0f0` | `#ffffff` | 1.14:1 | — | — |
| 9 | 亮：主按钮文字 / 主按钮底 | `#ffffff` | `#212121` | 16.10:1 | 4.5:1 | PASS |
| 10 | 亮：强调块文字 / 强调底 | `#171717` | `#eef4fc` | 16.20:1 | 4.5:1 | PASS |
| 11 | 亮：成功文字 / 页面 | `#1f7a4d` | `#ffffff` | 5.32:1 | 4.5:1 | PASS |
| 12 | 亮：成功文字 / 成功弱底 | `#135c3a` | `#e8f5ee` | 7.15:1 | 4.5:1 | PASS |
| 13 | 亮：警告文字 / 警告弱底 | `#7a4f00` | `#fdf3e0` | 6.47:1 | 4.5:1 | PASS |
| 14 | 亮：危险文字 / 危险弱底 | `#8f1c12` | `#fdeceb` | 7.84:1 | 4.5:1 | PASS |
| 15 | 亮：信息文字 / 信息弱底 | `#104d72` | `#e8f2fb` | 7.96:1 | 4.5:1 | PASS |
| 16 | 亮：已归档 / 归档弱底 | `#656d79` | `#f1f2f4` | 4.67:1 | 4.5:1 | PASS |
| 17 | 暗：正文 / page | `#f4f4f4` | `#101010` | 17.30:1 | 4.5:1 | PASS |
| 18 | 暗：正文 / raised | `#f4f4f4` | `#181818` | 16.14:1 | 4.5:1 | PASS |
| 19 | 暗：正文 / overlay | `#f4f4f4` | `#222222` | 14.47:1 | 4.5:1 | PASS |
| 20 | 暗：次要文字 / raised | `#b9b9b9` | `#181818` | 9.05:1 | 4.5:1 | PASS |
| 21 | 暗：辅助文字 / raised | `#909090` | `#181818` | 5.56:1 | 4.5:1 | PASS |
| 22 | 暗：占位文字 / raised | `#8a8a8a` | `#181818` | 5.14:1 | 4.5:1 | PASS |
| 23 | 暗：禁用文字 / raised | `#6e6e6e` | `#181818` | 3.48:1 | 3:1 | PASS |
| 24 | 暗：链接 / raised | `#9bc1d4` | `#181818` | 9.28:1 | 4.5:1 | PASS |
| 25 | 暗：控件边界 / raised | `#787878` | `#181818` | 4.02:1 | 3:1 | PASS |
| 26 | 暗：焦点环 / raised | `#e6e6e6` | `#181818` | 14.23:1 | 3:1 | PASS |
| 27 | 暗：raised / page（层级） | `#181818` | `#101010` | 1.07:1 | — | — |
| 28 | 暗：overlay / raised（层级） | `#222222` | `#181818` | 1.12:1 | — | — |
| 29 | 暗：muted / overlay（层级） | `#2a2a2a` | `#222222` | 1.11:1 | — | — |
| 30 | 暗：主按钮文字 / 主按钮底 | `#101010` | `#ededed` | 16.25:1 | 4.5:1 | PASS |
| 31 | 暗：强调块文字 / 强调底 | `#e5e5e6` | `#232324` | 12.47:1 | 4.5:1 | PASS |
| 32 | 暗：成功文字 / raised | `#83c3a5` | `#181818` | 8.72:1 | 4.5:1 | PASS |
| 33 | 暗：成功文字 / 成功弱底 | `#b6d8c8` | `#161d1a` | 11.14:1 | 4.5:1 | PASS |
| 34 | 暗：警告文字 / 警告弱底 | `#ddceb0` | `#1e1b15` | 11.06:1 | 4.5:1 | PASS |
| 35 | 暗：危险文字 / 危险弱底 | `#f0c4c0` | `#1e1615` | 11.32:1 | 4.5:1 | PASS |
| 36 | 暗：危险按钮文字 / 危险底 | `#1e1615` | `#e3a6a0` | 8.66:1 | 4.5:1 | PASS |
| 37 | 暗：信息文字 / 信息弱底 | `#c1d6e1` | `#141b1f` | 11.58:1 | 4.5:1 | PASS |

0 FAIL；正文/次要/辅助/占位全部 ≥4.5:1，禁用与控件边界 ≥3:1。

说明：

- 暗色中性面使用纯灰阶（R=G=B），HSL 饱和度 0%，视觉上不读出蓝；`--sh-accent-*` 保留 220° 色相但饱和度 ≤3%，作为中性 hover 叠层。
- 语义色（success / warning / danger / info / status）保留色相，暗色版饱和度较旧值下降、明度提高；弱底为低饱和同色相深色（饱和度 14%–22%）。
- 装饰性分隔线（`--sh-border-subtle` / `--sh-border-default` / `--sh-border-strong`）不承担“识别控件”的职责，按 WCAG 1.4.11 豁免；控件边界统一用 `--sh-border-contrast`（≥3:1）。
- 禁用文字按 WCAG 1.4.3 豁免，但仍保留独立 token（亮 `#949494` ≈ 3.03:1，暗 `#6e6e6e` ≈ 3.48:1），不使用 opacity 降层级。
- 暗色层级以 L 百分点计算：page 6.3 → raised 9.4（+3.1）→ overlay 13.3（+3.9）→ muted 16.5（+3.2），相邻差 ≥3；sunken 3.5% 为内凹面。1px 描边用于强化浮层边界，不依赖对比度数值表达层级。

## 主题切换（唯一实现）

主题切换不再由页面各自实现。全库共用一份样式与行为：

| 角色 | 文件 |
|---|---|
| 契约 | `components/index.json` 的 `components[]` 里按 `slug=theme-toggle` 找到那一条，取其 `contractData` |
| 视觉对照 | `preview/component-theme-toggle.html` |
| 样式 | `assets/theme-toggle.css` |
| 行为 | `assets/theme-toggle.js` |
| 图标 | `assets/icons.css` → `i16-moon` / `i16-sun` |

唯一标记：

```html
<button class="sh-theme-toggle" type="button" data-theme-toggle
        aria-pressed="false" aria-label="切换明暗主题" title="切换明暗主题">
  <span class="sh-i i16-moon" data-theme-icon="moon" aria-hidden="true"></span>
  <span class="sh-i i16-sun" data-theme-icon="sun" aria-hidden="true"></span>
</button>
```

页面只引两份共享文件，不复制规则：

```html
<link rel="stylesheet" href="../assets/icons.css" />
<link rel="stylesheet" href="../assets/theme-toggle.css" />
...
<script src="../assets/theme-toggle.js" defer></script>
```

### 状态矩阵

| 上下文 | 可见图标 | aria-pressed | `<html>` | localStorage |
|---|---|---|---|---|
| 亮色（未按） | moon | `false` | 无 `dark`，`data-theme="light"` | `light` |
| 暗色（已按） | sun | `true` | `class="dark"`，`data-theme="dark"` | `dark` |
| hover | 不变 | 不变 | 不变 | 不变 |
| focus-visible | 不变 | 不变 | 不变 | 不变 |
| `file://` 且 localStorage 被拒 | 不变 | 不变 | 不变 | 静默降级，不报错 |

### 定位修饰符

| 修饰符 | 场景 | 位置 |
|---|---|---|
| 默认（无修饰） | 预览页右上角 | `absolute`，`top: --sh-space-3; right: --sh-space-6` |
| `.sh-theme-toggle--fixed` | 独立预览页面 | `fixed`，右侧含 safe-area |
| `.sh-theme-toggle--inline` | 工具条 / demo 顶栏 | `static`，由容器 flex 对齐 |

### 迁移对照（旧 → 新）

| 旧写法 | 新写法 | 处理 |
|---|---|---|
| 原生 `<input type="checkbox" class="sh-theme-toggle">` + `<label>暗色</label>` | `button.sh-theme-toggle[data-theme-toggle]` + sun / moon | 删除，全量替换 |
| 每页各写一段 `.sh-theme-toggle { font-size: 12px; … }` | 引用 `assets/theme-toggle.css` | 删除本地规则 |
| 页面自写 `onchange` / `onclick` 切换 `.dark` | 引用 `assets/theme-toggle.js` | 删除本地脚本 |
| 只切 `.dark`、不更新可访问状态 | `aria-pressed` + `data-theme` 同步 | 由共享脚本保证 |
| 用外部 `<use>` 切 sun / moon（`file://` 静默失败） | `assets/icons.css` 的 `i16-moon` / `i16-sun` | 统一 |

示例主题切换不代表产品或未发布原型已完成验收。

**禁忌**：不要再用 checkbox + 文字；不要每页复制 CSS；不要用 emoji / 字符替代 sun / moon；不要只切 class 不更新 `aria-pressed`。

## Token 收敛（1.1.0）

### 1. 字阶：13 / 15 px 归位

| 原值 | 处理 | 依据 |
|---|---|---|
| 13px（历史示例与预览页） | 换成 `--sh-text-caption`（0.8125rem = 13px） | 既有 token 就是 13px，**不新增** `--sh-text-md` |
| 15px（历史示例1处） | 换成 `--sh-text-body-sm`（14px） | 单点值，不为它加 token；降到最近档 |
| 12px / 14px / 20px（预览页） | 换成 `--sh-text-xs` / `--sh-text-body-sm` / `--sh-text-xl` | 均已在字阶内 |
| 11px（15 处，全部在预览脚手架） | **保留字面值** | 仅 `.verify` / `.lang-tag` / `.mock-*` / `.crumb` / 状态标签等预览标注使用，不属于产品字阶；已在本节声明为 preview-only |

结果：`components.runtime.css` 裸字号 **0**；`components.preview.css` 只剩 2 处 11px（脚手架，非产品）。

### 2. 状态命名：三套并行 → 一套调色板

`status-*`（4 组）是唯一的状态调色板，值绑定 `success / warning / danger`。`lifecycle-*`（8 状态）与 `verdict-*`（6 结论）降级为**域词汇别名**，不再持有独立颜色：

| 域词汇 | 1.1.0 值来源 |
|---|---|
| `lifecycle-draft` / `-weak` | `status-archived` / `-weak` |
| `lifecycle-scanning` / `-weak` | `info-base` / `info-weak` |
| `lifecycle-scan-failed` / `-weak` | `status-rejected` / `-weak` |
| `lifecycle-uploaded` / `-weak` | `text-secondary` / `status-archived-weak` |
| `lifecycle-pending-review` / `-weak` | `status-review` / `-weak` |
| `lifecycle-published` / `-weak` | `status-published` / `-weak` |
| `lifecycle-rejected` / `-weak` | `status-rejected` / `-weak` |
| `lifecycle-yanked` / `-weak` | `text-primary` / `bg-muted` |
| `verdict-scanning` / `-weak` | `info-base` / `info-weak` |
| `verdict-scan-failed` / `-weak` | `status-archived` / `-weak` |
| `verdict-safe` / `-weak` | `status-published` / `-weak` |
| `verdict-suspicious` / `-weak` | `warning-base` / `warning-weak` |
| `verdict-dangerous` / `-weak` | `status-rejected` / `-weak` |
| `verdict-blocked` / `-weak` | `text-primary` / `bg-muted` |

视觉差 ≤ 1 个色阶（仅 draft / yanked-weak / uploaded-weak 三处微调）；这 28 个 token 在库内原本 0 引用，无回归风险。

### 3. 重复值与孤儿 token

| 项 | 收敛前 | 收敛后 |
|---|---|---|
| 重复字面值分组 | 20 组 | **2 组**（`#171717` 三义、`#ffffff` 八义）；亮暗取值分叉，保留语义命名，见下 |
| 孤儿 token | 46（颜色口径） | **89**（全量口径，见下表；其中颜色字面孤儿 15） |
| 指向别名的 token | 26 | **67** |

孤儿处理（全量 89 个，全部有结论）：

| 类别 | 数量 | 处理 |
|---|---|---|
| `lifecycle-*` / `verdict-*` 域别名 | 28 | 合并为别名（本表第 2 节） |
| `chart-*` / `viz-*` | 7 | 保留；值绑定 core scale，产品图表接入预留 |
| `code-keyword/string/number/comment/tag` | 5 | 保留；代码块语法高亮预留（当前组件只用 bg/fg） |
| 反色与玻璃面（`bg-inverse` / `bg-glass` / `text-inverse` / `border-inverse`） | 4 | 保留；深底反白区块需要独立语义 |
| 交互与语义 hover（`primary-active` / `primary-disabled` / `accent-soft` / `success-hover` / `warning-hover` / `info-hover` / `interaction-pressed-bg` / `interaction-selected-bg`） | 8 | 保留；产品 hover / pressed / selected 预留 |
| 完整字阶（display / h1 / body-lg / mono / tracking / weight-bold） | 13 | 保留；公开字阶，预览组件未全部用到 |
| 布局 / 尺寸 | 11 | 保留；断点 5、容器 dialog、control-lg、行高、卡片内边距 3 |
| 控件比例（`control-sm/ lg` pad-y / icon） | 4 | 保留；与 height/font 成组，删除会破坏比例表 |
| 缓动 / 层级 / 遮罩 | 9 | 保留；`ease-*` 3、`z-*` 5、`overlay-glass-alpha` 1 |

**删除**：`--sh-control-xl-*` 7 个（48px 档，库内 0 实现 0 引用）。

保留的 2 组重复字面值不合并的原因：`#171717`（text-primary / border-focus / accent-foreground）与 `#ffffff`（8 个 surface / foreground）在暗色块里各走不同取值（`#f4f4f4` / `#e6e6e6` / `#e5e5e6`、`#101010` / `#181818` / `#222222` 等），合并会破坏暗色层级。

### 4. 几何字面值

间距 / 边框类已全部走 token（`gap` / `padding` / `margin` / `top` / `1px solid`）。`components.runtime.css` 仍剩 **61 处** px 字面值，全部是组件固有几何：圆点 / 进度条宽度（6/8/10/12px）、头像与开关尺寸（28/34/36/40/52px）、栅格列宽（104/260/280/360/420/560px）、光学偏移（1/2/3px）。这些值只服务单一组件，不为它们再造一次性 token；已在契约 `sizeDeltas` / `anatomy` 中描述。
