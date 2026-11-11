# SkillHub 设计系统

随仓库提供的核心设计资源，采用 Apache-2.0；第三方图标许可见 THIRD_PARTY_NOTICES.md。
主题、44份完整组件契约、14篇设计规范、44个组件预览及必要图标/主题脚本可在干净检出中读取。
个人原型、截图、任务记录、编辑器插件和原工程的私有生成脚本不随本包发布。

## 使用

从仓库根目录执行（Python 3.9+，标准库）：

```sh
python3 scripts/read-design-system.py check
python3 scripts/read-design-system.py theme control
python3 scripts/read-design-system.py component button
python3 scripts/read-design-system.py spec task-composition
```

读取器默认使用本目录；可用 `--library PATH` 显式选择其他来源。
正文默认分页；续页沿用同一查询及返回的 nextOffset、payloadSha256，传入 `--offset N --expect-sha256 HASH`。
主题源为 colors_and_type.css；tokens.full.json 为完整亮暗投影。组件以 components/index.json 的 contractData 为准。
按任务选择 specs 中的布局、状态、无障碍、内容、主题与组件规则，不能只读目录或来源哈希。

直接打开 preview/component-button.html 等页面可看静态示例；也可在仓库根运行
`python3 -m http.server 8765 --bind 127.0.0.1`，访问 `/design-system/skillhub/preview/component-button.html`。
示例数据与动作用于设计演示，不代表业务接口已实现。

## React 产品实现

产品继续复用 web/src/shared/ui、Radix、现有 props 与键盘/焦点行为。
主题映射在 web/src/index.css 与 web/tailwind.config.ts；不要把整套静态示例 CSS 再叠到产品全局。
基础主题和 Button/Input/Select/Textarea 已开始采用；其他组件仍需逐项对照，不能视为44项已全部迁移。
已知差异包括部分禁用态、加载态描述与 React 样式；遇到契约冲突应记录并审阅，不能自行掩盖。

## 维护与验证

本包保留来源版本1.3.1；本次核心整理通过 library-consumption.json 的工作修订与 Git 提交识别，不宣称复现旧编辑器发布验收。
组件契约字段保留；未发布的历史来源证据改为说明，不继承旧验收结果。索引省略编辑器规划提示与缺失侧文件入口。
修改源主题后用 `python3 scripts/read-design-system.py refresh` 更新投影，再运行 check 及回归。
CSS、契约、预览变动需同步核对；原工程的 CSS 生成器未随包提供，不列作可执行维护命令。

```sh
python3 scripts/tests/read-design-system-test.py
python3 scripts/tests/design-system-package-test.py
```

结构/投影检查不能代替浏览器、无障碍或真实产品验收；记录当前改动实际通过与未验证的范围。
