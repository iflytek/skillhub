# 后端规范入口

正文读取与换会话接续遵循[有界读取规则](../workflow/context-reading.md)，按本页触发条件选资料，不批量加载整个目录。

适用 Java/Maven 后端及 Python Scanner 本体。先读本页，再按改动选择专题；工程规范正文在本目录，不依赖工程 Skill 自动触发。
具体业务需求、权限和状态机归正式 docs/OpenSpec；规范整理不授权改变业务行为。

## 日常约定

- Controller 负责传输边界，应用服务协调流程，领域服务维护业务不变量；复杂展示查询交给 app 查询仓储。
- 用户身份全链路使用 `String`；不要把技能或命名空间的数值主键类型套到用户身份。
- 复用现有仓储、响应工厂、本地化异常与请求追踪，不另造同义基础设施。
- 参数合法性、授权、领域状态、数据库完整性是不同边界，各自维护所负责的检查。
- 失败、并发、外部副作用与成功路径一起设计；不以加了事务注解证明跨服务原子性。

## 按需读取

| 触发条件 | 专题 |
|---|---|
| 新类、模块、依赖或读模型 | [模块边界](./directory-structure.md) |
| 仓储、查询、迁移与性能 | [数据库](./database-guidelines.md) |
| HTTP、鉴权上下文、API消费方、生成类型 | [接口契约](./api-contracts.md) |
| 状态变更、重复请求、竞争或外部副作用 | [事务与并发](./transactions-and-concurrency.md) |
| 异常与响应错误 | [错误处理](./error-handling.md) |
| 发布、下载、删除、授权行为变化，或日志、审计、追踪 | [日志](./logging-guidelines.md) |
| 扫描、队列、超时、重试或 Python Scanner 本体 | [扫描集成](./scanning-integration.md) |
| 实现与审查收尾 | [后端质量](./quality-guidelines.md)、[测试入口](../testing/index.md) |

生命周期修改读 [正式生命周期文档](../../../docs/14-skill-lifecycle.md) 及对应源码、测试；
API、坐标、权限修改读 [API设计](../../../docs/06-api-design.md)、[领域模型](../../../docs/02-domain-model.md)、[认证授权](../../../docs/03-authentication-design.md)。
发现文档与实现漂移时列出差异，不能按旧表机械改写代码；已知漂移见接口专题。
版本和模块清单以 [父 POM](../../../server/pom.xml) 为准，不将某次版本快照写成升级授权。

## 业务与基础设施路由

| 改动场景 | 修改前读取与核对 |
|---|---|
| 搜索条件、索引更新、搜索引擎替换 | [搜索设计](../../../docs/04-search-architecture.md)、[SearchQueryService](../../../server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchQueryService.java)、[SearchIndexService](../../../server/skillhub-search/src/main/java/com/iflytek/skillhub/search/SearchIndexService.java)；分别定位查询与索引端口，不将某个引擎的 SDK 泄漏到业务调用方 |
| 对象存储提供方、上传下载行为 | [架构设计](../../../docs/01-system-architecture.md)、[ObjectStorageService](../../../server/skillhub-storage/src/main/java/com/iflytek/skillhub/storage/ObjectStorageService.java)、[错误处理](./error-handling.md)；先核对端口方法、现有实现和调用方，再判断是配置变化还是接口变化 |
| 发布、审核、下载等跨服务流程 | [业务流程](../../../docs/05-business-flows.md)、[事务与并发](./transactions-and-concurrency.md)、[日志与审计](./logging-guidelines.md)；列出实际调用链的数据库写入、外部副作用与失败返回 |

上述是读取触发条件，不表示现有提供方已完成互换验证。涉及替换时按受影响行为验证，不凭“兼容协议”跳过验证。
