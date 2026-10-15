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
