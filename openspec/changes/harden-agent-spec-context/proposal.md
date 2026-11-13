## Why

SkillHub 已维护工程规范和 OpenSpec 变更，但当前项目没有完整接通 OpenSpec 实施、验证入口与
Trellis 规范读取。规范文件存在、hook 配置存在、CLI 输出包含路径，都不足以证明正文已进入
执行任务的 Agent 上下文。入口覆盖、宿主漏载、清单遗漏、截断和中断恢复会使实际上下文不完整。

本变更以 OpenSpec 原生开发流程为主，利用 Trellis 的规范组织与上下文能力，建立可观察、
可回归的规范读取和结果验证链路。

## What Changes

- 明确 OpenSpec、Trellis、宿主运行时和项目检查工具的职责，避免重复需求、设计与任务列表。
- 在原生 OpenSpec apply、verify 阶段明确选择并读取适用工程规范正文，支持主会话与可选子 Agent。
- 接入 OpenSpec 项目 context 与 apply guidance，保持 verify 的报告式检查语义。
- 修复指令入口覆盖和启动准备问题，明确缺失、截断、恢复和来源变化时的处理。
- 建立配置、正文送达、实现遵循三层验收；增加可移植回归与前后端真实任务试点。
- 将 Trellis 路径注入作为可选增强，先验证版本、宿主覆盖和降级，不强制采用测试版。

## Capabilities

### New Capabilities

- `design-system-consumption`: 源 CSS 一致的设计投影、默认视觉路由、私有快照定位和基础 React 映射验收。

- `agent-spec-context`: OpenSpec 开发流程中的工程规范选择、正文投递、验证、恢复和证据边界。

## Impact

涉及 `openspec/config.yaml`、项目工程工作流规范、可选 Agent 集成及相关检查和使用说明。
本机入口与共享模板按独立授权调整；默认保留任务、会话和个人工具在 Git 外的约定。
用户追加授权的设计修复涉及基础主题及 Button/Input/Select/Textarea，保持业务行为与接口不变；不修改模型核心、不自动提交、推送、合并或部署。

详细方案见 [design.md](./design.md)，实施清单见 [tasks.md](./tasks.md)。
