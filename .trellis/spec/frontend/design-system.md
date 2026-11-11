# 主题、组件与设计规范

可见 UI 修改默认对照仓库中的[SkillHub核心设计系统](../../../design-system/skillhub/README.md)，无需用户点名 Skill。
主题、44份完整组件契约、规范与组件预览随分支提供，不依赖私人目录或缓存。
用户指定其他设计目标时记录实际来源；纯类型、请求逻辑或文案小修按影响选读。

## 必需上下文

实现与只读设计审查均读取以下适用正文，并检查实际组件和调用方。

- [视觉基础](./visual-foundations.md)
- [组件工程规则](./component-guidelines.md)
- [前端质量与验收](./quality-guidelines.md)
- [测试选择入口](../testing/index.md)
- [前端测试规范](../testing/frontend-testing.md)

共同状态和无障碍规则也需读取：

```json
[{"design":{"command":"spec","names":["component-state-matrix"]}},{"design":{"command":"spec","names":["accessibility"]}}]
```

## 仓库内的依据

| 内容 | 工程来源 | 检查重点 |
|---|---|---|
| 主题 | `design-system/skillhub/colors_and_type.css`、`tokens.full.json`；产品映射在 `web/src/index.css`、`web/tailwind.config.ts` | 明暗语义色、字体、密度、尺寸与圆角 |
| 组件 | `design-system/skillhub/components/index.json` 的完整 contractData；再核对 `web/src/shared/ui/`、调用方和测试 | 真实 props、变体、受控状态、键盘与焦点 |
| 页面与质量 | [页面模式](./page-patterns.md)、视觉及组件专题 | 页面组合、加载、空态、错误、响应式与无障碍 |

规范描述目标，源码描述实际能力；有差异时记录，不将当前源码自动视作正确标准。
不从原型推断后端已有能力，不在单页适配中默改全站默认。

## 按需读取

从仓库根执行：

```sh
python3 scripts/read-design-system.py check
python3 scripts/read-design-system.py theme control
python3 scripts/read-design-system.py component button
python3 scripts/read-design-system.py spec task-composition
```

读取器默认选择已发布核心资源；`--library PATH` 可显式指定用户提供的其他来源。
每次读取完整选定的主题分组、组件契约和相关规范；默认分页，按 nextOffset 和 payloadSha256 继续。
恢复与完整性检查按[有界读取](../workflow/context-reading.md)，来源摘要不替代正文。
默认资源损坏时明确失败，不静默切到其他来源。库中规则冲突、与 React 的差异分别报告，不自行填值或视为全部迁移。

## 当前基础映射与验收

基础主题使用中文优先字体和亮暗语义色；默认控件32px、compact28px。
Button 的 xs/sm/default/lg 对应24/28/32/36（default随容器compact变28），icon28。
非文本 Button 使用键盘 outline，Input/Select/Textarea 使用边框焦点。
具体 props/ref/受控与 Radix 行为以源码和测试核对，不传入尚未实现的档位。

按测试与前端验收规则检查明暗、长内容、窄屏、焦点、弹层及完成态。
其他共享组件和页面局部覆盖按实际任务核对，不因基础映射就宣称全部组件或页面完成迁移。
