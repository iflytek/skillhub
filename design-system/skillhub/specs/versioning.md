# 版本与治理

来源版本、许可与入口见 metadata.json；工作修订见 library-consumption.json，具体内容通过 Git 提交固定。
来源版本不等于当前快照已完成全部视觉验收，也不继承原工程的历史通过记录。

## 2. 版本位判定（可查表）

| 变更 | 版本位 | 判据 | 例子 |
|---|---|---|---|
| token 改名 / 删除；组件契约删除变体或尺寸；选择器语义改变；默认主题 token 值改变；目录结构迁移 | **MAJOR**（1.0.0 前记 MINOR） | 已有页面不改代码会**视觉或行为改变** | 删除 `--sh-border-default`；按钮主色反转；`tabs` 从 3 个变 4 个 |
| 新增 token；新增组件/预览/契约；新增主题变体；新增规范章节；新增契约字段（`stateCoverage.matrix`、`sizeDeltas`）；警告处置策略变更但不影响视觉 | **MINOR** | 只增不改；旧调用点无需迁移 | 新增 `select` / `textarea` / `label`；新增 `tokens.full.json`；暗色整族重算（0.2.0） |
| 文案修正、注释、预览修饰、错别字、无视觉变化的值纠错、文档中的数字校正 | **PATCH** | 不改 token、不改契约结构 | 修正 `themes.md` 表格笔误 |

补充规则：
- 一次发版只取**最高位**：同时含 MINOR 与 PATCH，记 MINOR。
- 新增 token 必须同时写入 `colors_and_type.css` 与由脚本生成的 `tokens.full.json`，否则不算完成。
- 组件契约的 `doNotInvent`、`unknowns` 变更视为 MINOR；删除变体视为 MAJOR（0.x 记 MINOR）。
- 主题变体只允许新增，不允许在默认 `:root` 内直接改值；“扩增项，需评审”转正需 MAJOR。

## 维护检查

变更说明记录范围、兼容性、影响组件、迁移和实际验证。源主题只维护 colors_and_type.css；投影用仓库读取器的 refresh/check 更新校验。
组件变更同时核对 contractData、实际 CSS、示例与 React 消费方；缺失的原工程工具不列为检查命令。
新数值、删改变体或默认行为应审阅，失败和未验证内容不能视为通过。
