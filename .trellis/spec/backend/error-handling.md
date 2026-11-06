# 错误处理

适用领域失败、HTTP 映射和面向用户的错误信息。

- 领域失败沿用 `LocalizedDomainException` 及现有 bad-request/not-found/forbidden 子类，携带消息键与安全参数；不要在领域层返回 HTTP DTO。
- 认证链、Controller advice 和兼容层各按已有协议处理，不能假设所有错误必然经过同一个 advice。
- 复用 ApiResponseFactory 和现有消息资源。面向用户的业务提示可本地化，内部异常详情不直接拼进响应。
- 不用异常文案匹配来新增业务分类。发现存量特殊兼容处理时保持其适用范围，避免扩展成通用规则。
- 错误响应验证 HTTP 状态、响应结构、消息与失败后数据状态；只断言“抛了异常”不足以证明事务回滚。
- 不吞掉基础设施失败并返回业务成功；是否重试取决于错误类别及重复执行安全性。

当前统一 handler 将乐观锁冲突映射为 409、对象存储访问故障映射为 503，并隐藏内部错误细节。
这些是实现锚点，不代表所有失败都应转换成同一状态码。

来源：[异常实现](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/exception/GlobalExceptionHandler.java)、
[响应与脱敏断言](../../../server/skillhub-app/src/test/java/com/iflytek/skillhub/exception/GlobalExceptionHandlerTest.java)、
[领域异常](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/shared/exception/LocalizedDomainException.java)。

## 场景：新增失败分类或修改统一异常处理

先定位失败属于领域、MVC 参数绑定、认证过滤器还是对象存储边界，再选现有异常与 handler。
例如对象存储读取失败应检查 `handleStorageAccess`；不能在下载服务捕获后返回空 ZIP 并声称成功。

| 输入/场景 | 当前可观察契约 | 回归锚点（GlobalExceptionHandlerTest） |
|---|---|---|
| `ObjectOptimisticLockingFailureException` | HTTP 409、响应 `code=409`、本地化冲突提示 | `persistenceConflictsReturn409WithoutLeakingDatabaseDetails` |
| `StorageAccessException` | HTTP 503；日志带 requestId 和认证状态，不输出稳定 userId | `handleStorageAccess_shouldLogAuthenticationWithoutStableUserId` |
| MVC 缺参、不可读请求体、类型不匹配、原生方法入参约束 | HTTP 400、ApiResponse `code=400`；不记作未处理内部故障 | `handleMvcBadRequest_shouldReturn400WithoutUnhandledErrorLog`、`handleMethodValidationDistinguishesInputFromReturnValueErrors` |
| 不支持的 HTTP 方法 | HTTP 405、ApiResponse `code=405`，保留 Allow 响应头 | `handleMethodNotAllowed_shouldReturn405AndAllowHeader` |

反例：用 `exception.getMessage().contains(...)` 为新业务规则分配状态码；只断言状态而遗漏协议头；
把原始存储异常文本直接发给客户端。正确做法是沿用有类型的异常、现有消息键与响应工厂，
在对应测试中同时断言状态、响应字段及敏感信息边界。

验收时执行 [后端测试入口](../testing/backend-testing.md) 对应测试；修改过滤器链或真实 HTTP 协议时追加 Controller/安全测试。
上表是当前单测覆盖范围，不证明事务回滚、真实存储可用性或所有入口均经过该 handler。
