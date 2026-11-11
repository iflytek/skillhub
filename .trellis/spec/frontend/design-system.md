# 设计系统的采用

SkillHub 前端可见 UI 的默认目标为 SkillHub 设计库的当前已审阅快照，无需用户每次点名。
适用于主题、组件、页面布局改造与审查。用户明确指定其他目标时以该目标为准并记录来源。纯类型、请求逻辑或显示文字小修
不默认读取全部设计资料。先读[前端入口](./index.md)，保留业务、权限与接口契约。

## 必需上下文

实现和只读设计审查均读取以下正文；不运行测试不等于可以省略验收规则。设计组件按实际使用读取完整契约，主题与状态/无障碍专题按任务追加，缺少来源明确保留未验证。

- [视觉基础](./visual-foundations.md)
- [组件工程规则](./component-guidelines.md)
- [前端质量与验收](./quality-guidelines.md)
- [测试选择入口](../testing/index.md)
- [前端测试规范](../testing/frontend-testing.md)

设计状态与无障碍的共同底线由以下查询提供；同样适用于只读设计审查。具体组件仍按实际使用追加。

```json
[{"design":{"command":"spec","names":["component-state-matrix"]}},{"design":{"command":"spec","names":["accessibility"]}}]
```

## 三类权威来源

| 内容 | 来源与读取方式 | 工程落点 |
|---|---|---|
| 主题 | 库内 `colors_and_type.css` 为源；`tokens.full.json` 含亮暗投影；追加 `specs/themes.md` | `web/src/index.css`、`web/tailwind.config.ts` |
| 组件 | `components/index.json` 中按 slug 读取完整 `contractData`，预览提供视觉参考 | 先核对 `shared/ui`、`shared/components` 与业务组件，再选择复用或扩展 |
| 规范 | 库内 `specs/` 按任务读取；页面组合用最新有效 `task-composition.md/json` | 组件规则、页面模式、响应式及无障碍检查 |

选定设计描述目标，当前源码描述实际能力；不能用旧产品样式覆盖本次设计目标，也不能
从原型推断后端已有能力。遇到库内规则冲突，记录对应条目和影响，不自行发明第三套数值。
实现与检查固定同一份来源摘要；设计快照变化后重核受影响契约。

## 本地资源与按需读取

库优先从被忽略的 `.design_library/skillhub/` 读取；缺少时自动定位本机已登记、带内容哈希的私有快照；也可用命令参数选择其他目录。
Agent 负责检查和资料准备，不要求用户手动调用 Skill。快照失效时明确失败，不悄悄降级为旧视觉基线。用户提供的
内部资源不默认纳入公开仓库。共享工程入口不能依赖本机私人绝对路径；新检出缺少库时，
工具应明确失败并说明未加载设计约束。非设计任务继续按现有规范工作，不伪造已采用设计库。

以下命令从仓库根目录执行，只读取文件，不启动服务、不生成 CSS 或修改产品：

```sh
python3 scripts/read-design-system.py check
python3 scripts/read-design-system.py theme control
python3 scripts/read-design-system.py component button
python3 scripts/read-design-system.py spec themes
python3 scripts/read-design-system.py spec task-composition
```

工具需要 Python 3 标准库，输出选中正文及来源文件 SHA-256。`component` 保留完整契约，
大上下文逐组件读取；`theme` 可只选需要的完整分组。不要把数百KB索引直接塞入受限的
任务注入。输出被宿主截断时继续读完整内容，摘要不代表已读取正文。

`check` 检查结构与源 CSS 的完整可投影分组一致性、源哈希、别名和缺项；`theme`/`component` 也拒绝陈旧投影。
`refresh` 由 Agent 在源 CSS 已审阅后重建投影，不修改 CSS；`register` 登记本机私有内容快照，供新 worktree 自动定位。
这些检查仍不证明规范无矛盾、Trae兼容或页面视觉通过。
正文默认小分页，不允许大于2400字符的请求；续页使用 `--offset N --expect-sha256 HASH`，按 nextOffset 继续。恢复和完整性检查遵循[有界读取](../workflow/context-reading.md)，最后一段不等于完整送达。

## 与现有 React 的映射

- 颜色、字体与密度先映射现有变量，不叠加第二套全局 `.btn/.input/.card` 样式。
- 设计 md 档对应当前 Button `size="default"`；本次已提供 xs/sm/lg/icon，其他档位仍先查真实 API。设计中的其他档位
  必须核对本次实际 React API，不能直接传入未实现的值。
- 现有 regular/compact 与库的目标密度不同，记录本次采用的上下文；共享默认修改
  核对全部受影响调用方，不在单页局部适配中默改全站。
- HTML 原型 JS 仅作参考，产品继续保留既有 Radix、Portal、键盘和受控行为。
- 图形资产按实际任务加载，不作为主题/组件任务的默认上下文；品牌资源独立核对。

## 验证与设计审阅

遵循[测试入口](../testing/index.md)和[前端验收](./quality-guidelines.md)。实际改动主题或
组件时检查明暗、长内容、窄屏、焦点、弹层及完成态；只调整上下文入口不要求启动产品。
Figma默认不启用，不是实施门禁；用户选择画板时通过 `$figma-web-bridge` 并追加
[设计证据](./design-evidence.md)，保持文件节点与实现、检查结果可追溯，不隐式调用官方MCP。

## 当前基础映射与验收范围

本次产品基础采用中文优先字体、源主题的亮暗语义色；默认控件32px、compact28px；
Button 的 xs/sm/default/lg 对应24/28/32/36（default随容器compact变28），icon28。
非文本 Button 使用键盘 outline，Input/Select/Textarea 使用边框焦点。
既有 props/ref/受控与 Radix 行为保留。其他共享组件和页面局部覆盖仍需按实际任务核对，
不因基础主题已映射就宣称44个组件或全部页面已完成迁移。
