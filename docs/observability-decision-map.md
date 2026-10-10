# 通用可观测性决策图

目标：为 SkillHub 建立独立、通用、可插拔的日志关联、指标和链路追踪基础设施。
当前实现覆盖 HTTP、SkillHub 管理的线程池、Redis Stream 和明确接入的内部 HTTP Client；
普通定时任务没有上游 carrier，仍是独立后台边界。Redis Stream 上下文只进入 transport
metadata，不进入业务模型和业务载荷。

边界：

- Servlet Filter、执行器装饰器、消息 Observation 和明确接入的 Client Builder 负责
  建立/恢复上下文。
- Redis Stream Producer 注入、Consumer/Reclaimer 逐条提取；定时任务不继承任意请求。
- 业务代码不读写 MDC，不负责创建通用 Span，也不负责统计任务生命周期指标。
- 使用 W3C Trace Context；日志后端、Metrics 后端和 Trace Exporter 均可替换。
- 上下文是有长度限制的基础设施元数据，不进入业务 payload。
- Collector、Exporter 或 Metrics 后端不可用时，主业务和任务内核继续工作。

必须满足的不变量：

- 每个已纳入本期的执行边界都正确建立作用域并在 `finally` 清理，线程复用不得串号。
- 日志稳定输出 `requestId`、`traceId`、`spanId`（存在时）；任务执行资源标识不由本期
  可观测性自动生成。
- Trace 与 Metrics 可关闭、可替换；关闭后不得改变业务行为。
- 指标只使用低基数维度，业务 ID 不进入标签。
- 采集端不可用必须异步、限时、限队列并 fail-open。

## #1：可观测性是否与业务和任务状态机彻底分离？

Blocked by: 无
Type: Grilling

### Question

可观测性是否只通过通用执行边界和生命周期信号接入，不进入业务处理器？

### Answer

已确认。可靠任务内核只发布通用生命周期信号；可观测性拦截器把执行资源标识加入日志、
Span 和指标。搜索处理器只处理搜索，不认识 MDC、OpenTelemetry 或 Prometheus。

## #2：通用关联身份和传播协议是什么？

Blocked by: #1
Type: Research

### Question

如何区分现有 `X-Request-Id`、W3C `traceId/spanId` 和执行资源标识，并跨 HTTP、线程池
边界传播？

### Answer

已确定：

- `requestId` 是 SkillHub 的请求/审计关联标识，不冒充分布式 Trace。
- `traceId/spanId` 由 Tracer 生成，跨进程只使用 W3C `traceparent/tracestate`。
- 定时任务或可靠任务的执行资源 ID 只作为当前执行作用域属性，不进入业务 payload。
- HTTP、线程池和消息 carrier 的注入/提取位于基础设施层；消息上下文是 transport
  metadata，不是任务业务字段。
- 不传播任意 MDC Map；baggage 默认关闭，任何允许项都必须低敏、限长、显式配置。
- 无效或不可信的公网 Trace Context 按 W3C 规则丢弃，服务端控制采样。

常见方案和候选组合见
[Java / Spring 通用日志关联与链路追踪方案调研](./research/2026-07-31-observability-common-solutions.md)。

## #3：采用 Micrometer Observation、OpenTelemetry API/SDK 还是 Java Agent？

Blocked by: #2
Type: Research

### Question

哪种组合最适配 Spring Boot 3.2.3，并同时支持无 Collector 运行、可选 OTLP 和稳定日志关联？

### Answer

已选择三模式：

- `none`：不创建应用内 OTel SDK 或 Exporter，只保留 Request ID。
- `otel-sdk`：使用 Micrometer Tracing + OTel Bridge；配置 OTLP endpoint 时才导出。
- `external-agent`：应用内使用 NOOP Tracer，由部署环境提供唯一的外部 Agent。

应用代码只依赖 Micrometer/Observation 边界，不依赖 OTel SDK 或 SkyWalking API。
自动配置测试已证明三种模式互斥，错误的 endpoint/mode 组合会在启动时失败。

## #4：如何证明上下文传播、日志输出和故障降级正确？

Blocked by: #3
Type: Prototype

### Question

验证线程复用隔离、嵌套作用域、异步任务边界、消息传播、采样、Exporter 超时、
Collector 中断、队列打满和关闭观测能力等场景。调度任务验证不继承请求上下文；
Redis Stream 验证逐条注入、提取和清理。

### Answer

本地原型已证明：

- Request ID Scope 在线程复用、嵌套 Scope、异常退出和 `CallerRunsPolicy` 下均能恢复并
  清理。
- Micrometer 手工 Span 和 Observation 均能随 `skillhubEventExecutor` 传播。
- Redis Stream Producer/Consumer 通过通用消息 Observation 传播 W3C Trace Context 和
  受控 Request ID，Reclaimer 从原消息重新提取。
- Scanner 使用 Spring 管理的 `WebClient.Builder` 传播 W3C `traceparent`。
- 面向用户配置的 GitLab 外部 Client 不传播 Trace Context。
- `none / otel-sdk / external-agent` 的应用上下文和 Exporter 条件符合设计。
- `@Scheduled` 保持独立后台执行边界；Redis Stream/Reclaimer 不继承线程上下文，而是
  从每条消息的 transport metadata 恢复。

Collector 中断、日志背压、采样率和关闭行为仍由 `big-main` 精确 SHA 镜像的远端原型验证。

## #5：如何形成可部署闭环？

Blocked by: #4
Type: Research

### Question

确定 stdout 格式、可选 JSON、Prometheus 或 OTLP Metrics、Trace Exporter、暴露边界、
低基数告警和运维文档。

### Answer

已确定最小交付：

- 文本日志用于本地开发，ECS 风格 JSON stdout 用于部署环境。
- JSON 日志只输出白名单关联字段，通过有界非阻塞 AsyncAppender 保护业务线程。
- Trace 可经 OTLP Collector 路由到 SkyWalking；需要 SkyWalking 原生能力时改用唯一的
  Java Agent。
- Prometheus 继续作为可选 Metrics 后端，不是本期链路关联的前置条件。

部署配置和三模式操作说明写入 `docs/09-deployment.md`；远端实测结果只保存在本地私有
中文报告中。
