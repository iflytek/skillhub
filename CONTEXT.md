# SkillHub 集成术语

此词汇表明确 SkillHub 与 Agent 消费端集成时的概念边界，不定义功能范围或实现方案。

## Language

**DSH 消费端**：使用 SkillHub 技能的 DeepSeek Harness 实例。
_Avoid_：用“DeepSeek 模型”指代 DeepSeek Harness。

**SkillHub 原生连接插件**：安装在 DSH 中、让用户访问 SkillHub 技能的扩展。
_Avoid_：将连接插件称为“SkillHub 的 DSH 插件市场”。

**SkillHub 技能**：由 SkillHub 管理和分发、供 Agent 加载使用的技能包。
_Avoid_：将所有技能包统称为 DSH 插件。

**DSH 插件托管**：由 SkillHub 管理和分发 DSH 插件这一类资产的能力，与 DSH 消费 SkillHub 技能是不同的产品范围。
_Avoid_：用“接入 DSH”同时指代插件托管和技能消费。

## 技能归属

**技能所有者（Skill Owner）**：当前持有某一条技能所有权的用户。技能所有者与该技能所在空间的负责人是不同概念。
_Avoid_: 将技能所有者与空间 OWNER 混称为“Owner”。

**空间负责人（Namespace OWNER）**：某个命名空间的负责人，属于空间成员角色。该身份不表示他是空间内每条技能的所有者。
_Avoid_: 将空间负责人直接称为所有技能的作者。

**历史作者**：某次历史内容提交的实际作者。历史作者与技能当前所有者可能是不同用户。
_Avoid_: 把所有权转移描述为改写历史作者。
