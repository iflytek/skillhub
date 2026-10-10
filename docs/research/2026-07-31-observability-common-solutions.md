# Java / Spring 通用日志关联与链路追踪方案调研

调研时间：2026-07-31
适用基线：SkillHub，Spring Boot 3.2.3、Java 21、Logback、Micrometer Actuator

## 结论

当前 Java/Spring 生态已经基本收敛到以下组合：

1. 使用 W3C `traceparent` / `tracestate` 作为跨进程传播协议。
2. Spring 应用内使用 Micrometer Observation/Tracing，底层桥接 OpenTelemetry。
3. 云原生或需要广覆盖自动插桩时使用 OpenTelemetry Java Agent。
4. 使用 OTLP 把 Trace 发往 Collector，再由 Collector 路由到 Tempo、Jaeger、Zipkin、
   SkyWalking 或商业后端。
5. 日志只消费当前上下文中的 `traceId` / `spanId`，业务代码不操作 MDC。

Spring Cloud Sleuth、手写 MDC/TID、TLog/TTL 和厂商 Agent 仍能见到，但不应作为
SkillHub 新机制的协议核心。

## 常见方案比较

| 方案 | 常见使用场景 | 优点 | 主要缺口 |
|---|---|---|---|
| Filter + MDC + TaskDecorator | 单体应用、只要求按 ID 查日志 | 简单、无采集端 | 没有真实 Span；容易漏线程/客户端边界；手写传播易串号 |
| Micrometer Tracing + OTel bridge | Spring Boot 3.x 应用内建观测 | Spring 官方路径；自动日志关联；便于自定义基础设施 Observation | 覆盖依赖 Spring 已观测的组件；线程池仍要正确配置上下文传播 |
| OpenTelemetry Java Agent | Kubernetes、统一运维、需要 JDBC/Redis/HTTP 等广覆盖 | OTel 官方对 Spring Boot 的默认建议；零代码；覆盖面最大 | 需要部署 Agent；必须实测启动/CPU/内存开销；自定义持久化任务边界仍需扩展 |
| OpenTelemetry Spring Boot Starter | Native Image、不能挂 Agent、需要应用 YAML 配置 | OTel SDK 原生集成；适合 Agent 不可用场景 | OTel 官方不把它作为普通 Spring Boot 的默认选择；需要单独管理 OTel BOM |
| SkyWalking/Elastic/Pinpoint 等 Agent | 已统一采购或部署特定 APM 的企业 | 自动插桩成熟、开箱 UI | 协议和后端绑定更强；不适合作为开源产品内部 API |
| Spring Cloud Sleuth | Spring Boot 2.x 历史项目 | 旧生态成熟 | 官方明确不支持 Spring Boot 3.x，核心已迁移到 Micrometer Tracing |

## 官方事实

### Spring Boot

- Spring Boot 3.2.3 Actuator 为 Micrometer Tracing 提供依赖管理和自动配置。
- OTel 组合使用 `micrometer-tracing-bridge-otel`；OTLP 使用
  `opentelemetry-exporter-otlp`。
- 启用 Micrometer Tracing 后，Spring Boot 默认把 `traceId`、`spanId` 放入 MDC，并
  支持通过 `logging.pattern.correlation` 固定日志格式。
- Spring Boot 3.2.3 默认产生 W3C 上下文，并可消费 W3C、B3、B3 Multi；新设计应只
  产生 W3C，兼容消费策略可单独配置。
- 自动 HTTP 传播依赖 Spring 自动配置的 HTTP Client Builder；自行 `new` 客户端会
  绕过传播。
- Spring Framework 6.1 提供 `ContextPropagatingTaskDecorator`，用于恢复日志和
  Observation 上下文；官方同时提醒大量极小任务会有传播开销。

### OpenTelemetry

- OTel 官方把 Java Agent 列为普通 Spring Boot 应用的默认零代码方案，因为它比
  Spring Boot Starter 提供更多开箱插桩。
- Starter 主要面向 Native Image、Agent 启动开销不满足要求、已有其他 Java Agent，
  或需要通过 Spring 配置文件管理 OTel 的场景。
- Java Agent 覆盖 Spring Web MVC、JDBC、Lettuce、Java Executors、Logback 等
  SkillHub 关键边界。
- Agent 的 Logback MDC 默认键为 `trace_id`、`span_id`、`trace_flags`；Micrometer
  默认键为 `traceId`、`spanId`。若支持两种运行模式，必须统一日志字段，不能让查询方
  感知两套命名。
- OTel 官方要求在目标部署环境实测 Agent 开销，没有通用的固定开销数字；采样率、
  JDBC/Redis Span 数量和资源限制都会影响结果。

### W3C Trace Context

- `traceparent` / `tracestate` 是厂商中立的传播协议。
- Header 必须按标准校验；无效上下文应丢弃并创建新 Trace。
- Trace Context 不得携带用户身份、IP、Token 或其他敏感信息。
- 公网调用方可伪造 sampled 标志，因此采样和费用控制必须由服务端约束。

## 开源项目观察

- OpenTelemetry Demo 的 Java 服务直接在镜像中挂载
  `opentelemetry-javaagent.jar`，通过标准 `OTEL_*` 配置连接 Collector，代表
  云原生 Agent 路径。
- Spring Petclinic Microservices 使用 Spring Boot tracing starter 和 Zipkin 后端，
  代表 Spring 原生集成路径。后端选择不同，但应用侧仍依赖 Spring 观测抽象。
- RuoYi-Cloud-Plus 预留 SkyWalking Java Agent 和 OAP/UI，代表厂商 Agent 路径；
  适用于组织已统一使用 SkyWalking 的情况，不适合作为 SkillHub 的内部协议。

## 对 SkillHub 的候选结论

应用代码的稳定边界应是 Spring 的 Observation/Tracing 抽象与 W3C 协议，而不是某个
日志或 APM 产品：

```text
HTTP / Executor / Scheduler / Reliable Task boundary
                      │
              Observability interceptor
                      │
         Micrometer Observation / Tracing facade
                      │
             OpenTelemetry bridge + W3C
                      │
          optional OTLP exporter / Collector
```

候选主运行模式：

- 应用内使用 Micrometer Tracing + OpenTelemetry bridge，保证 Spring Boot 3.2.3
  原生整合、统一 MDC 字段和自定义基础设施 Observation。
- OTLP Exporter 默认关闭；开启后只负责异步导出，不改变请求结果。
- Java Agent 作为高级部署模式，用于获得 JDBC、Redis、第三方 HTTP Client 等更广
  自动插桩。Agent 与应用内自动插桩不得同时启用，除非原型证明不会产生重复 Span。
- 无 Trace SDK/Agent 时仍保留 `requestId` 日志关联；Trace 是增强能力，不是业务前置条件。

最终选择仍需原型验证：同一请求的 Span 是否重复、线程池上下文是否串号、Collector
中断是否影响延迟、日志字段是否一致、关闭 tracing 后业务行为是否完全不变。

## 参考资料

- [Spring Boot 3.2.3 Tracing](https://docs.spring.io/spring-boot/docs/3.2.3/reference/html/actuator.html#actuator.micrometer-tracing)
- [Spring Boot current Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Framework 6.1 ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.1.4/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [OpenTelemetry Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
- [OpenTelemetry Spring Boot Starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
- [OpenTelemetry Java supported libraries](https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/)
- [OpenTelemetry Java Agent performance](https://opentelemetry.io/docs/zero-code/java/agent/performance/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [Spring Cloud Sleuth end-of-line notice](https://docs.spring.io/spring-cloud-sleuth/docs/current/reference/html/)
- [OpenTelemetry Demo](https://github.com/open-telemetry/opentelemetry-demo)
- [Spring Petclinic Microservices](https://github.com/spring-petclinic/spring-petclinic-microservices)
- [RuoYi-Cloud-Plus](https://github.com/dromara/RuoYi-Cloud-Plus)
