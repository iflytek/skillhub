## 1. 能力模型与路由

- [x] 1.1 `[REQ-ATC-01, REQ-ATC-06]` 在 `ApiTokenScopeService` 增加能力常量、别名展开
  （`skill:publish` → submit + publish-private + manage + `suite:legacy-write`）与已知 scope 校验；
  单元测试覆盖展开、去重、未知值与空列表。
- [x] 1.2 `[REQ-ATC-02]` 按 design D2 改写 `RouteSecurityPolicyRegistry` 的 Token 策略，订阅路由排在删除通配之前；
  更新 `RouteSecurityPolicyRegistryTest` 现有用例的预期 scope。
- [x] 1.3 `[REQ-ATC-02]` 新增目录完整性测试：枚举所有 Token 策略，技能写入路由必须属于 D2 的某一能力，
  v1／web 成对路由能力相同；改动前后的放行集合差异只允许 D2 列出的变化。
- [x] 1.4 `[REQ-ATC-07]` 新增 `error.apiToken.scope.escalation`、`error.apiToken.scope.unknown` 多语言文案，保持现有两类错误码与参数格式。

## 2. 组合操作上限

- [x] 2.1 `[REQ-ATC-03]` 认证过滤器把 Token 有效能力写入 `PlatformPrincipal`（会话为空表示不受限），AppService 层传入领域服务。
- [x] 2.2 `[REQ-ATC-03]` `SkillPublishService`：Token 调用且已有技能可见性变化时要求 `skill:manage`，
  在创建版本与修改技能之前拒绝；Token 调用时 SUPER_ADMIN 不触发 `autoPublish`。
- [x] 2.3 `[REQ-ATC-03]` `SkillReviewSubmitService#confirmPublish` 在 Token 调用时要求 `skill:publish-private`；
  `rerelease` 验证 PRIVATE 落在 `UPLOADED`。
- [x] 2.4 `[REQ-ATC-03]` 先盘点迁移前 Bundle 操作可按 operation ID 关联的发起方式及旧授权证据；
  操作记录持久化发起方式与 Token 能力快照（Flyway 新增字段）。新 Token 操作缺快照在落库前拒绝；
  恢复／失败重试只沿用原授权，可证历史记录按原授权处理，来源不明的空快照保持 `BLOCKED_RETRYABLE`，
  不按创建时间、当前 actor 角色或重试者权限推断旧版全能力。
- [x] 2.5 `[REQ-ATC-03]` 集成测试覆盖 spec 中全部场景及无副作用拒绝，尤其是 Bundle 新操作缺快照、
  历史空快照来源不明、可证历史恢复、提权重试；分别覆盖普通存量 Token、SUPER_ADMIN 存量 Token、
  SUPER_ADMIN 会话和新 read + submit Token 的 PRIVATE 上传／显式确认行为。

## 3. Token 签发与查询

- [ ] 3.1 `[REQ-ATC-04]` `TokenController`／`ApiTokenService`：未知 scope 400；Token 调用时创建、同名轮换、续期执行子集与到期时间检查，失败不吊销同名旧 Token。
- [ ] 3.2 `[REQ-ATC-04]` 设备码兑换只签发 D7 固定能力。
- [ ] 3.3 `[REQ-ATC-05]` `whoami` 增加 `capabilities`、`legacyCompat`、`expiresAt`；`/me/namespaces` 对 `skill:read` 开放；运行 `make generate-api` 与 OpenAPI 漂移检查。
- [ ] 3.4 `[REQ-ATC-04, REQ-ATC-05]` 集成测试：`token:manage` 签出 `skill:delete` 被拒、省略 scopes 不绕过、空空间角色、旧版 Token 的 whoami 展开。

## 4. Web 与 CLI

- [ ] 4.1 `[REQ-ATC-06]` Token 创建对话框提供只读下载、候选提交、私有发布、自定义预设，删除与 Token 管理仅在自定义中勾选；列表展示能力与旧版兼容标记。
- [ ] 4.2 `[REQ-ATC-06]` `cli-auth` 与设备码签发 `skill:read + skill:submit`。
- [ ] 4.3 `[REQ-ATC-06]` CLI `whoami` 展示能力；`remove` 识别 `scope.missing`（`skill:delete`）并提示显式授权，不自动换发。
- [ ] 4.4 `[REQ-ATC-06]` 兼容测试：旧版 CLI 使用新登录 Token 的 publish、validate、送审、remove；
  普通存量 Token 的原路由授权不变，并对 SUPER_ADMIN Token 直发例外与会话行为分别断言。

## 5. 文档与发布

- [ ] 5.1 更新 `docs/03-authentication-design.md` 作用域与默认权限、`docs/06-api-design.md` 中各接口的 Token 要求、OpenClaw 集成说明。
- [ ] 5.2 发布说明写明能力拆分、存量 Token 的路由兼容及 SUPER_ADMIN Token 上传直发例外、
  Bundle 无法证明来源的空快照需重新授权、CLI 重新登录后的删除授权，以及省略 scopes 默认值将在下一个次版本收紧。
