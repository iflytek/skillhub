# SkillHub 日志关联与链路追踪建设方案

> 日期：2026-07-31
>
> 状态：Accepted（2026-07-31，按本文分阶段实施和验证）
>
> 关联：GitHub Issue #597
> 适用基线：Spring Boot 3.2.3、Java 21、Logback、Micrometer Actuator

## 1. 背景

SkillHub 已经使用 `X-Request-Id` 关联 API 响应、业务日志和审计记录，但目前仍存在以下问题：

- 部分应用服务和 DTO 直接读取 SLF4J MDC，可观测性实现泄漏到了业务代码。
- `X-Request-Id` 接受任意客户端输入，没有统一的长度和字符约束。
- `@Async` 线程池没有显式传播请求和 Trace 上下文，异步日志可能丢失关联信息。
- 当前没有标准分布式 Trace，无法通过一个 ID 串联 SkillHub、Scanner 等服务调用。
- 日志字段尚未形成适合 Elasticsearch/Kibana 查询的稳定结构。

本方案用最小建设成本建立通用日志关联与链路追踪基础设施。它不负责建设完整的企业
可观测性平台，也不把日志、Trace 或 Metrics 逻辑写入业务处理器。

Issue #597 中“搜索索引可靠异步交付”应作为独立问题处理，不属于本文范围。

## 2. 建设目标

一期需要实现：

1. 每个 HTTP 请求都有合法的 `request.id`。
2. 启用 Tracing 时，日志包含标准 `trace.id` 和 `span.id`。
3. `otel-sdk` 模式使用 W3C `traceparent` / `tracestate` 传播 Trace Context。
4. 业务代码不直接读写 MDC，也不直接依赖 OpenTelemetry 或 SkyWalking API。
5. 现有 Spring `@Async` 执行器能够正确传播并清理上下文。
6. 日志以结构化 JSON 输出到 stdout，可由 Filebeat/Fluent Bit 采集到
   Elasticsearch/Kibana。
7. Trace 可以选择通过 OTLP Collector 接入 SkyWalking。
8. Collector、SkyWalking、Elasticsearch 或日志采集器不可用时，SkillHub 业务继续运行。
9. SkillHub 应用配置只能启用一个应用内 Tracer；`external-agent` 模式下唯一外部
   Agent 由部署参数和发布检查保证。

本方案按多个小阶段、小提交实施和验证，全部通过后再统一创建一个替代 PR。

## 3. 非目标

一期不建设：

- 搜索索引可靠队列、重试、死信和重放。
- 多租户差异化采样和运行时动态采样。
- Spring Cloud Config、Nacos 或可写 Actuator 配置端点。
- 应用内 OTLP 熔断器或自定义重试框架。
- 审计日志归档、物理隔离和 WORM 存储。
- 通用 PII/DLP 检测平台。
- Prometheus/Grafana/Kibana 告警模板和容量规划平台。
- Spring Boot 2.x 或 Java 17 兼容。
- 在业务类上增加 Trace 注解或要求业务开发者操作 Span。

## 4. 总体架构

```text
HTTP request
    │
    ├─ RequestIdFilter
    │     └─ request.id
    │
    └─ Micrometer Observation / Tracing
          ├─ MDC correlation
          │     └─ JSON stdout
          │           └─ Filebeat / Fluent Bit
          │                 └─ Elasticsearch / Kibana
          │
          └─ OpenTelemetry Bridge
                └─ OTLP
                      └─ OpenTelemetry Collector
                            └─ SkyWalking OAP
```

稳定边界是：

- 应用内使用 Micrometer Observation/Tracing。
- `otel-sdk` 模式跨进程使用 W3C Trace Context。
- Trace 导出使用 OTLP。
- 日志使用 ECS 风格字段。
- SkyWalking、Elasticsearch 和 Kibana 都是部署适配器，不进入业务模型。

## 5. 运行模式

通过一个启动期配置选择运行模式：

```yaml
skillhub:
  observability:
    tracing-mode: ${SKILLHUB_TRACING_MODE:none}
```

允许值和确定行为：

| 模式 | Micrometer Tracer | OTLP Exporter | 外部 Agent | 无 Agent/endpoint 时 |
|------|-------------------|---------------|------------|---------------------|
| `none` | NOOP | 无 | 不支持 | 只有 `request.id` |
| `otel-sdk` | OTel Bridge | 配置 endpoint 时创建 | 不支持 | 仍建立进程内 Trace，但不导出 |
| `external-agent` | NOOP | 无 | 可选 | 记录警告并退化为只有 `request.id` |

运行模式是启动期不变量，不支持热切换。

必须保证：

- `none` 和 `external-agent` 不创建应用内 OTel Span。
- `otel-sdk` 不支持同时启用 SkyWalking、OTel 或其他外部 Tracing Agent；应用只能校验
  自身 endpoint/mode 冲突，不能可靠识别任意 JVM Agent。
- `external-agent` 不创建 OTLP Exporter。
- SkillHub 配置能够识别的冲突应在启动时失败；任意 Java Agent 无法被应用可靠识别，因此
  部署检查和原型测试还必须验证实际 JVM 参数中只有一个 Tracer。

一期实现并验证三种模式的应用上下文互斥边界和日志关联。`external-agent` 只验证
SkyWalking Agent 接管 Trace 时应用内 OTel Tracer/Exporter 不工作；“只挂载一个外部
Agent”属于部署验收项。SkyWalking 特有高级能力不进入 SkillHub 核心代码。

## 6. 关联字段契约

### 6.1 对外日志字段

日志输出统一使用：

| 字段 | 必需性 | 含义 |
|------|--------|------|
| `request.id` | HTTP 请求或显式任务上下文中存在 | SkillHub API、响应和审计关联 ID |
| `trace.id` | 当前存在有效 Trace 时 | 分布式 Trace ID |
| `span.id` | 当前 Tracer 能提供时 | 当前调用节点 ID |
| `service.name` | 始终存在 | 固定为 `skillhub` |
| `service.version` | 部署时提供 | 发布版本或镜像对应 Commit |
| `service.environment` | 部署时提供 | 当前部署环境 |

`request.id` 与 `trace.id` 不能合并：

- `request.id` 属于 SkillHub API 契约，可出现在响应和审计记录中。
- `trace.id` 属于可选的分布式追踪上下文，可能被采样或关闭。

启动日志以及没有显式任务上下文的后台维护日志允许不包含 `request.id`。

### 6.2 内部字段映射

日志基础设施负责字段映射，业务代码不感知具体 MDC 键：

| 来源 | 内部字段 | 输出字段 |
|------|----------|----------|
| SkillHub Request Context | `requestId` | `request.id` |
| Micrometer OTel Bridge | `traceId` | `trace.id` |
| Micrometer OTel Bridge | `spanId` | `span.id` |
| SkyWalking Logback Toolkit 事件转换器 | `tid` | `trace.id` |

SkyWalking Agent 是否能稳定提供独立 `span.id` 以实际原型结果为准。无法稳定提供时允许只
输出 `trace.id`，不得解析不稳定的内部字符串格式。

External Agent 模式通过 SkyWalking 官方 Logback Toolkit 从当前日志事件读取 `tid`；
这不是业务代码读取 MDC，也不能假定 `tid` 一定存在于异步日志线程的 MDC 中。日志编码器
只读取允许的关联字段，不得把整个 MDC Map 自动写入 JSON。

## 7. Request ID

### 7.1 输入规则

客户端可以传入 `X-Request-Id`，但必须同时满足：

- 长度为 1–64 个字符。
- 首字符是字母或数字。
- 其余字符只允许字母、数字、`.`、`_`、`:`、`-`。

建议校验表达式：

```regex
^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$
```

请求头缺失、为空或不合法时，服务端生成 UUID。响应始终返回最终采用的
`X-Request-Id`。

### 7.2 代码边界

新增通用 `RequestIdAccessor` 和对应的 Request ID Scope：

- Filter 负责解析、校验、建立和清理 Request ID 上下文。
- 独立 ThreadLocal Scope 是 Request ID 的进程内权威来源。
- 为该 Scope 注册 Micrometer `ThreadLocalAccessor`，由
  `ContextPropagatingTaskDecorator` 捕获、恢复和清理。
- Scope 同步维护日志所需的 MDC 镜像，但读取方不能把 MDC 当作权威来源。
- API 响应工厂通过该抽象读取 Request ID。
- 审计编排通过该抽象或明确参数读取 Request ID。
- 应用服务、Controller 和 DTO 不再直接调用 `MDC.get()`。
- MDC 只作为日志适配器，不再作为业务上下文的权威来源。

## 8. Tracing 配置

`skillhub-app` 使用 Spring Boot 3.2.3 管理的依赖版本：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

基础配置：

```yaml
management:
  tracing:
    sampling:
      probability: ${SKILLHUB_TRACING_SAMPLING_PROBABILITY:0.1}
    baggage:
      enabled: false
    propagation:
      type: W3C
  otlp:
    tracing:
      timeout: ${SKILLHUB_OTLP_TIMEOUT:5s}
      compression: ${SKILLHUB_OTLP_COMPRESSION:gzip}
```

基础配置不得为 OTLP endpoint 提供默认地址。只有 `otel-sdk` 部署显式设置以下标准
Spring Boot 配置时才创建 Exporter：

```bash
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces
```

一期沿用 OpenTelemetry 1.31 的默认 BatchSpanProcessor 有界队列和丢弃策略，不增加应用内
重试、熔断或自定义队列实现。

## 9. 日志输出

### 9.1 输出模式

- 本地开发默认使用可读的文本日志。
- `SKILLHUB_LOG_FORMAT=json` 启用 ECS 风格 JSON stdout。
- JSON 编码器显式输出标准字段和三个关联字段，不启用“输出全部 MDC”。
- JSON ConsoleAppender 外包一层 Logback AsyncAppender，初始队列容量为 1024，并允许通过
  `SKILLHUB_LOG_ASYNC_QUEUE_SIZE` 调整。
- AsyncAppender 使用非阻塞策略；队列耗尽时日志可能丢失，审计事实不依赖该通道。
- 异常使用 `error.type`、`error.message`、`error.stack_trace`。
- 队列容量保持可配置，默认值在原型压测后固定，不在设计阶段猜测。
- 异常和队列丢弃行为必须在测试中验证。

示例：

```json
{
  "@timestamp": "2026-07-31T10:10:10.123Z",
  "log.level": "INFO",
  "service.name": "skillhub",
  "service.version": "0.2.15",
  "service.environment": "test",
  "request.id": "req-123",
  "trace.id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span.id": "00f067aa0ba902b7",
  "log.logger": "com.iflytek.skillhub...",
  "message": "..."
}
```

应用只输出 stdout，不直接依赖 Elasticsearch SDK，也不直接写 Elasticsearch。

### 9.2 审计边界

`audit_log` 数据库记录仍是审计事实来源。stdout 日志不能代替审计记录，审计留存和归档
不在本方案中处理。

## 10. 上下文传播

### 10.1 Spring 异步执行器

为现有 `skillhubEventExecutor` 配置 Spring Framework 6.1 的
`ContextPropagatingTaskDecorator`：

- 提交任务时捕获 Request ID 和 Trace Context。
- 执行任务时恢复上下文。
- 执行完成后在 `finally` 中清理。
- `CallerRunsPolicy` 触发时也必须保持正确的嵌套作用域。

测试必须重复复用同一工作线程，证明不同请求之间不会串号。

### 10.2 消息队列与长生命周期后台线程

Redis Stream 消费循环和 Reclaimer 不继承应用启动线程或任意请求的 MDC。Producer 通过
通用消息 Observation 把 W3C Trace Context 与受控 Request ID 注入 transport metadata；
Consumer/Reclaimer 逐条提取、建立 Scope，并在处理结束后清理。Scanner HTTP 调用自然成为
Consumer Span 的子调用。

上下文不写入 `ScanTask` 或搜索业务 payload，也不改变可靠任务状态机。普通定时任务没有
上游 carrier，仍建立独立执行上下文；长期延迟任务使用稳定任务 ID 或 Span Link，不维持
超长父 Span。

### 10.3 HTTP 出站

一期只管理两类 HTTP Client：

- 内部 Scanner Client：使用 Spring 管理且带 Observation 的 Builder，传播 W3C Trace
  Context。
- 其他现有 Client：GitHub、GitLab、内置 Skill 公网下载和 S3 Client 均不在一期新增
  Trace Context 传播。

后续新增 Client 必须明确选择内部或外部配置，不能依赖全局 Host 正则或在业务代码中手工
删除 Header。

## 11. SkyWalking 与 Elasticsearch 接入

### 11.1 OTel SDK 模式

推荐链路：

```text
SkillHub
  → OTLP/HTTP
  → OpenTelemetry Collector
  → OTLP
  → SkyWalking OAP
```

Collector 用于协议适配和后端路由，不是 SkillHub 的启动依赖。

SkyWalking 10.3 的 OTLP Trace 会转换为 Zipkin Trace，并通过 Zipkin Query/Lens UI 查询。
它不等价于 SkyWalking Java Agent 的原生拓扑、慢 SQL 和 Profiling 能力，部署文档必须
明确该差异。原型报告必须记录实际使用的 Maven 依赖、Collector、OAP 和 Agent 版本及
查询结果。

### 11.2 External Agent 模式

需要 SkyWalking 原生能力时：

- 使用 `external-agent`。
- 不配置 SkillHub OTLP endpoint。
- 由部署环境挂载并启动 SkyWalking Java Agent。
- 使用 SkyWalking 官方 Logback Toolkit 提供 Trace ID。
- 日志基础设施将 `tid` 映射为 `trace.id`。

### 11.3 日志链路

```text
SkillHub JSON stdout
  → Filebeat / Fluent Bit
  → Elasticsearch
  → Kibana
```

Kibana 使用 `trace.id` 查询日志，SkyWalking 使用同一个 Trace ID 查询调用链。

## 12. 实施步骤

### 阶段一：Request ID 与日志边界

1. 增加 Request ID 校验。
2. 建立 `RequestIdAccessor`。
3. 移除应用服务、Controller、DTO 对 MDC 的直接读取。
4. 增加允许字段明确的结构化日志配置。
5. 增加 Request ID 和日志字段测试。

可观察结果：

- 非法 Request ID 被替换。
- API 响应和审计记录仍使用同一 Request ID。
- 业务类不再 import `org.slf4j.MDC`。

### 阶段二：Micrometer + OTel

1. 增加 Tracing Bridge 和 OTLP Exporter 依赖。
2. 增加 `none`、`otel-sdk`、`external-agent` 模式。
3. 设置 W3C、关闭 baggage、配置采样率。
4. 保证无 endpoint 时不会产生网络连接。
5. 保证每个模式只存在一个实际 Tracer。

可观察结果：

- `none` 模式只有 `request.id`。
- `otel-sdk` 模式日志出现标准 Trace 字段。
- `external-agent` 模式不会产生应用内 OTel Trace。

### 阶段三：传播边界

1. 为 `skillhubEventExecutor` 增加上下文传播。
2. 验证线程复用、嵌套任务和 `CallerRunsPolicy`。
3. 让内部 Scanner Client 使用 Spring 管理且可观测的 Client Builder。
4. 验证外部 HTTP Client 不发送 Trace Context。

### 阶段四：部署示例与远端验证

1. 提供最小 OTel Collector 配置示例。
2. 补充 SkyWalking OTLP 与 Agent 模式差异。
3. 将待测分支合入 `big-main`，记录合入后的精确 Commit SHA。
4. 构建绑定 `big-main` SHA 的测试镜像。
5. 在共享测试机使用独立容器、网络、数据卷和动态端口运行三个原型。
6. 生成中文测试报告并保存在本地私有目录，不提交开源仓库。

每个阶段使用独立的小提交并保留在同一实现分支；前一阶段的范围测试通过后再进入下一
阶段。公开 Issue 和 PR 统一在阶段五创建。

### 阶段五：社区交付（最后执行）

该阶段必须在远端验证全部通过后执行：

1. 创建新的可观测性建设 Issue，说明它承接 #597 中的“通用日志关联与链路追踪”部分。
2. 搜索索引可靠异步交付继续作为独立问题，不混入新的可观测性 Issue。
3. 从经过验证的实现分支创建新的 PR，并关联新 Issue。
4. PR 只包含公开代码、配置、自动化测试和公开部署说明；不得包含测试机地址、凭证、
   私有端口、原始远端日志或本地中文测试报告。
5. 在 #597、#644 及其他被替代的关联项中回复：
   - 原问题是否真实存在。
   - 为什么不采用原 PR 的实现。
   - 新方案的边界和主要改动。
   - 已完成的自动化及远端验证摘要。
   - 新 Issue 和替代 PR 的链接。
6. 确认维护者需要的信息完整后，关闭已被替代的 PR；不在验证完成前抢先关闭。
7. #597 等关联 Issue 只根据剩余问题是否已有明确承接决定关闭、缩小范围或继续保留，
   不因替代 PR 创建而自动关闭。
8. 新 PR 通过 Review 和 CI 后，确认 PR Head 仍等于已验证的功能 SHA，且该 SHA 可从已
   测试的 `big-main` SHA 到达；满足后才允许更新 `main`。
9. 如果 Review 或 CI 修复改变了代码、配置或测试脚本，则原验证证据失效：先将新 SHA
   合入 `big-main`，重新构建镜像并完成受影响的远端验证，再更新 `main`。

## 13. 验证方案

### 13.1 自动化测试

至少覆盖：

- 未传 Request ID 时自动生成。
- 合法 Request ID 被保留。
- 空值、超长值和非法字符被替换。
- Filter 正常、异常退出后都清理上下文。
- API 响应、审计和日志中的 Request ID 一致。
- JSON 只输出允许的关联字段。
- Trace 采样率在测试中设为 `1.0` 后可稳定断言。
- `@Async` 线程恢复父上下文。
- 连续复用同一线程执行不同请求时不串号。
- `CallerRunsPolicy` 下上下文正确恢复。
- `none`、`otel-sdk`、`external-agent` 的 Spring Context 互斥。
- 未配置 OTLP endpoint 时不创建网络导出。
- 内部 Scanner 请求携带 `traceparent`。
- Redis Stream Producer/Consumer 保持同一 Trace 和 Request ID，处理结束后线程不串号。
- 重试发布和 Reclaimer 重新消费仍能恢复消息关联上下文。
- 外部 HTTP 请求不携带 `traceparent`。

### 13.2 远端原型

#### 原型 A：none

- 不部署 Collector。
- SkillHub 正常启动并完成核心 Smoke Test。
- 日志存在 `request.id`，不存在伪造的 Trace 字段。

#### 原型 B：otel-sdk

- SkillHub → Collector → SkyWalking 跑通。
- JSON 日志进入 Elasticsearch/Kibana。
- Kibana 与 SkyWalking 能用同一 `trace.id` 查询。
- Collector 停止后 SkillHub API 和异步任务继续工作。

#### 原型 C：external-agent

- SkyWalking Java Agent 提供原生 Trace。
- 应用内 OTel Exporter 不工作。
- 日志能用 SkyWalking Trace ID 关联。
- 不产生双 Trace、重复 Span 或两个冲突的 Trace ID。

### 13.3 远端测试场景

- HTTP 成功、4xx、5xx 和未认证请求。
- Scanner 成功、超时和失败。
- 异步事件正常执行和抛出异常。
- Redis Stream 正常消费、失败重试、Pending Reclaim 和重复投递。
- 并发请求重复使用线程池。
- Collector 启动、停止和恢复。
- 日志采集器停止或消费变慢。
- 采样率 `0.0`、`0.1` 和 `1.0`。
- 容器收到 SIGTERM 后日志和 Trace 的关闭行为。
- 日志中不出现 Authorization、Cookie、Token、密码和完整请求体。

## 14. 验收标准

以下条件全部满足后，一期才算完成：

- [ ] 三种模式行为与本文一致。
- [ ] 业务代码不再直接读取或写入 MDC。
- [ ] Request ID 校验、响应和审计关联测试通过。
- [ ] 日志字段符合约定，且不输出完整 MDC。
- [ ] Spring 异步执行器上下文传播和隔离测试通过。
- [ ] Redis Stream 消息上下文传播、重试、Reclaimer 和隔离测试通过。
- [ ] 内外部 HTTP 传播边界测试通过。
- [ ] 无 OTLP endpoint 时不存在外部连接尝试。
- [ ] Collector 中断不影响 SkillHub 业务结果。
- [ ] OTel SDK 与 SkyWalking Agent 不会同时产生 Trace。
- [ ] `make test-backend-app` 通过。
- [ ] `make typecheck-web` 和 `make lint-web` 通过。
- [ ] 基于 `big-main` 合入后精确 SHA 构建的远端三个原型通过。
- [ ] 中文测试报告保存在本地私有目录。
- [ ] 新的可观测性 Issue 和替代 PR 已创建并互相关联。
- [ ] #597、#644 等关联项已获得清晰回复，被替代的旧 PR 已关闭。
- [ ] 关联 Issue 已根据剩余范围分别关闭、缩小范围或保留，且状态理由清楚。
- [ ] 新 PR Head 与已验证功能 SHA 一致，且可从已测试的 `big-main` SHA 到达。
- [ ] 通过验证后才允许更新 `main`。

## 15. 回滚

出现问题时：

1. 将 `SKILLHUB_TRACING_MODE` 改为 `none`。
2. 删除 `MANAGEMENT_OTLP_TRACING_ENDPOINT`。
3. 将 `SKILLHUB_LOG_FORMAT` 改为 `text`。
4. 保留 Request ID 和原有文本日志能力。
5. 通过滚动重启恢复，不进行运行时模式切换。

Tracing 和结构化日志关闭后不得影响 SkillHub 的业务状态、数据库状态或任务执行语义。

## 16. 已知限制

- 10% Head Sampling 下，全量日志中的部分 `trace.id` 在 SkyWalking 中没有对应 Trace。
- SkyWalking OTLP 模式的展示能力弱于原生 Java Agent。
- 日志队列在背压时可能丢弃日志，这是保护业务线程的预期行为。
- External Agent 提供哪些 MDC 字段取决于具体 Agent 和版本。
- 一期只处理通用关联和传播，不保证搜索索引异步交付可靠性。

## 17. 参考资料

- [Spring Boot 3.2.3 Tracing](https://docs.spring.io/spring-boot/docs/3.2.3/reference/html/actuator.html#actuator.micrometer-tracing)
- [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/)
- [OpenTelemetry Java OTLP Exporter](https://opentelemetry.io/docs/languages/java/exporters/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [SkyWalking OpenTelemetry Trace](https://skywalking.apache.org/docs/main/v10.3.0/en/setup/backend/otlp-trace/)
- [SkyWalking Logback Toolkit](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-logback-1.x/)
- [Elastic ECS Tracing Fields](https://www.elastic.co/docs/reference/ecs/ecs-tracing)
- [方案调研](./research/2026-07-31-observability-common-solutions.md)
