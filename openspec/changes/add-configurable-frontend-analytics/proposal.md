## Why

Issue #989 / PR #1010 已提供被动动作标签、通用 helper 和独立平台适配示例，但部署方仍需修改 JS、提供文件和手工接入 Nginx。维护者确认新的交付目标：部署只填配置、尽量一次启动、文档可独立复现，同时控制对原业务的影响。此目标是原评审之后新增的正式产品能力，不将其描述为原贡献者返修遗漏。

## 分两次交付

- PR-1（#1010）：完成被动标签、厂商中立 helper、示例 adapter、edge 上传上限与默认 Web 上游修正，以及可独立照做的中文接入指南。示例仍由部署方手工接入，不增加产品运行时开关。
- PR-2（后续 PR）：交付下述配置化产品能力，包括 `web/src/features/analytics` 单一产品源码、runtime-config 可选加载、同源代理路由及 Docker／Compose／Helm 配置。Umami 是首个注册 adapter，通用配置和加载入口不绑定平台。
- 两个 PR 分别合并。Issue #989 保留到 PR-2 完成并验证后才关闭；PR-1 不自动关闭该 Issue。

## What Changes

以下产品加载和部署变更由 PR-2 交付；PR-1 的稳定接口与验收边界见 design 和 specs。

- 保留原作者的被动标签及协议，增加默认关闭的通用可选加载入口。
- 复用现有 runtime-config；采集参数通过部署 JSON 配置提供，平台参数放在 adapterConfig，不编辑源码。
- 模块随 Web 发布，平台适配器按需加载；第一版以 Umami 作为一个注册实现，不把厂商 SDK 或字段耦合进业务页面。
- 现有 Web Nginx 只增加独立的精确采集路由，不再依赖 HTML 注入或额外 Nginx。统计上游地址不公开给浏览器，DNS 仅请求时解析。
- Compose 与 Helm 透传配置，提供可选自托管 Compose 组合；必要平台信息仍明确由部署方提供。
- 增补关闭、故障、子路径、上传及登录回归与详细部署文档。

## Capabilities

### New Capabilities

- `configurable-frontend-analytics`: 配置化、厂商中立的可选采集加载与部署接入。

### Modified Capabilities

无数据库/API能力变更。原被动动作类别及原业务语义不变。

## Impact

影响按阶段区分：PR-1 仅涉及被动标签契约、示例、测试和接入文档；以下产品部署影响属于 PR-2。影响 Web 可选模块、运行时配置、容器启动、Compose/Helm、部署测试和文档。新增小型 jq 镜像依赖用于标准 JSON 校验与安全转义，避免用 envsubst 拼接任意配置。默认部署不启动统计服务、不加载采集模块、不外发事件。

范围外：后台采集配置页、SkillHub 分析数据库、通用远程代码插件平台、用户级同意弹窗、可靠消息/重试队列、服务端业务成功事件、生产环境部署和自动合并。统计平台自己的数据库属于可选外部服务，不修改 SkillHub schema。
