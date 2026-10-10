# 可观测性开发者接入指南

本文说明 SkillHub 代码如何接入统一的日志关联和链路追踪标准。
开发者不需要直接操作 MDC、OpenTelemetry SDK 或 SkyWalking API。

## 1. 统一标准

| 信息 | 来源 | 日志字段 | 传播方式 |
|---|---|---|---|
| 请求关联 ID | `RequestIdFilter` / `RequestIdAccessor` | `request.id` | `X-Request-Id` |
| 分布式 Trace ID | Micrometer Tracing | `trace.id` | W3C `traceparent` |
| Span ID | Micrometer Tracing | `span.id` | 当前 Trace Scope |

`request.id` 是 SkillHub 的请求/审计关联标识，不等同于 `trace.id`。
请求没有链路追踪时仍应保留 `request.id`。

## 2. 运行模式

通过 `SKILLHUB_TRACING_MODE` 选择一种模式，修改后重启应用：

- `none`：默认模式。无应用内 OTel SDK 和 OTLP 导出，只保留 `request.id`。
- `otel-sdk`：使用 Micrometer Tracing + OTel Bridge；配置
  `MANAGEMENT_OTLP_TRACING_ENDPOINT` 后才向 Collector 导出。
- `external-agent`：应用内 Tracer 为 NOOP，由部署环境提供唯一的外部 Agent。
  SkillHub 只能校验自身配置，不能识别任意 JVM Agent；唯一 Agent 是部署检查项。

`none`/`external-agent` 不能配置 OTLP endpoint；`otel-sdk` 与外部 Tracing Agent
不得在同一进程中叠加。

## 3. 开发者接入方式

### 3.1 普通 HTTP 请求

不需要增加代码。`RequestIdFilter` 会生成或校验 `X-Request-Id`，并在请求结束时清理
线程上下文。Micrometer Tracing 负责在 `otel-sdk` 模式下创建 HTTP Observation 和 Trace。

业务代码不要：

- `MDC.put` / `MDC.remove` 写入请求关联字段；
- 手工解析或拼接 `traceparent`；
- 在日志中输出完整 MDC Map。

### 3.2 Spring 异步任务

优先使用已有的 `skillhubEventExecutor`：

```java
@Async("skillhubEventExecutor")
public void handleEvent(SkillPublishedEvent event) {
    // 直接记录日志即可，request.id/trace.id/span.id 会按提交时的上下文恢复
}
```

新增 Spring 管理的线程池时，注入统一的
`ContextPropagatingTaskDecorator`，不要自己复制 MDC：

```java
@Bean
ThreadPoolTaskExecutor myExecutor(
        ContextPropagatingTaskDecorator contextDecorator
) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(contextDecorator);
    executor.initialize();
    return executor;
}
```

该装饰器负责捕获、恢复和清理 `RequestIdAccessor` 与 OTel Observation Scope。

### 3.3 内部 HTTP 服务

内部服务调用必须使用 Spring 管理的 `WebClient.Builder`，这样 `otel-sdk` 模式下会
自动传播 W3C Trace Context：

```java
@Bean
HttpClient scannerClient(
        WebClient.Builder builder
) {
    return new WebClientHttpClient(builder.build());
}
```

Scanner 是当前已接入的内部客户端。新增内部客户端时，应补一个测试，断言请求包含合法
的 `traceparent`。

### 3.4 外部 HTTP 服务

面向用户配置的 GitLab、第三方 API 等外部服务不要复用内部观测 Builder，也不要手工
删除 Header。使用明确不接入 SkillHub Observation 的客户端，并补测试断言请求不包含
`traceparent`。

### 3.5 Redis Stream 和定时任务

Redis Stream 已通过 `MessageObservationSupport` 接入通用消息传播：

- Producer 把 `traceparent`、`tracestate` 和受控的 `skillhub.request_id` 写入 Stream
  transport metadata，不修改 `ScanTask` 等业务对象；
- `AbstractStreamConsumer` 逐条提取上下文并建立 `CONSUMER` Observation，在 `finally`
  中恢复线程原状态；
- Consumer 内部调用 Scanner 时，Spring 管理的 `WebClient` 自动创建同一 Trace 的子 Span；
- 重试发布发生在当前 Consumer Scope 内，新消息继续携带关联上下文；Reclaimer 处理原消息
  时重新从消息提取，不继承 Reclaimer 线程的上下文；
- `none` 和 `external-agent` 模式仍传播 Request ID；应用保证完整 W3C Trace 的模式是
  `otel-sdk`，外部 Agent 的跨 Stream Trace 能力取决于对应 Agent 插件。

新增 Redis Stream Consumer 应继承 `AbstractStreamConsumer`，新增 Producer 应调用
`MessageObservationSupport.observePublish`。其他消息中间件只实现自身 carrier 的
`MessageCarrierAdapter`；传播核心不依赖 Redis、Redisson 或 `Map`。不要在业务 DTO、MDC
或日志代码中复制上下文。

普通 `@Scheduled` 任务没有上游消息 carrier，仍是独立后台边界；需要长期任务关联时应使用
稳定任务 ID，而不是把任意历史 HTTP Span 保持为超长父 Span。

## 4. 可扩展点

| 扩展需求 | 应扩展的位置 | 不应修改的位置 |
|---|---|---|
| 新增请求关联来源 | `RequestIdFilter` / `RequestIdAccessor` | 业务 Controller、DTO |
| 新增线程上下文 | `RequestIdThreadLocalAccessor` / `ContextRegistry` | 每个任务的 `MDC` 代码 |
| 新增 Tracing 后端 | Micrometer Bridge / Collector 配置 | 业务服务 |
| 新增日志字段 | `SkillHubEcsEncoder` 白名单 | “输出全部 MDC” |
| 新增内部 HTTP 客户端 | Spring `WebClient.Builder` + propagation test | URL 正则删 Header |
| 新增外部 HTTP 客户端 | 独立客户端构建入口 + no-propagation test | 依赖全局默认行为 |
| 新增消息队列边界 | `MessageObservationSupport` + `MessageCarrierAdapter` | 业务 DTO、手工 MDC/OTel API |

## 5. 接入验收清单

新增一个执行边界或客户端时，至少补充：

1. `none` 模式下业务结果不变；
2. `otel-sdk` 模式下内部调用的 `traceparent` 合法；
3. 外部调用不携带 `traceparent`；
4. 线程复用后上下文被清理，不发生串号；
5. 日志只出现 `request.id`、`trace.id`、`span.id` 等白名单字段；
6. Collector 不可用时不影响业务结果。
7. 消息 Producer/Consumer 使用同一 Trace，Request ID 不串号，重试和 Reclaimer 不丢关联。

运行后端验证使用：

```bash
make test-backend-app
```

部署级变更再运行：

```bash
make staging
```
