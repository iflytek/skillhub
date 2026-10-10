## Boundary

平台管理 API 只处理创建。租户内读写继续由 Organization Membership 和组织角色授权，平台 `SUPER_ADMIN` 不在租户 API 获得隐式通行权。

Organization 及 Membership 是协议无关的租户基础能力。本批创建接口和已合入的成员范围只读接口不依赖企业 OIDC 开关；后续 OIDC 开关仅控制 OIDC Login Connection 的配置、测试和激活等操作，匿名登录另由数据面开关控制。现有成员范围的连接摘要读取仍按组织权限校验，不因关闭 OIDC 配置操作而失效。本批不实现这些开关。

控制器做请求校验和平台角色校验；应用服务编排现有聚合及仓储。保存 Organization、Membership、Role Binding 和审计均位于同一事务。slug 使用现有模型校验和数据库唯一约束；并发重名由现有 JPA adapter 映射为 409。

审计只持久化组织 ID、操作者、动作、成功结果和 requestId，不记录用户邮箱、密钥或请求体。初始 owner 的 Membership 来源为 MANUAL；创建成功后 Organization authority version 为 1。回滚无需删除数据：本批只有新 API，停用入口或回退应用版本即可，已创建组织保留。

本批只用现有 schema，不新增 Flyway migration。后续成员/角色管理和 Login Connection 控制面分别另行交付、验证与合并。
