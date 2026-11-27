## 1. Directory Connection

- [x] 1.1 定义独立 Directory Connection、SCIM 2.0 状态、版本和权限契约。
- [x] 1.2 增加加密 bearer secret、只写轮换、默认关闭、allowlist 和审计。
- [x] 1.3 增加 `MembershipSourceType.DIRECTORY` 与必要的向前迁移。

## 2. SCIM Users

- [x] 2.1 实现 ServiceProviderConfig、ResourceTypes、Schemas 和组织限定 Users endpoint。
- [x] 2.2 实现 list/filter/get/create/replace/patch/deactivate，并限制资源字段与请求大小。
- [x] 2.3 实现 external resource coordinate、幂等重放、乱序更新和跨组织隔离。
- [x] 2.4 保持 membership/user/account/namespace 来源边界，不自动授予权限。

## 3. 管理与文档

- [x] 3.1 在组织管理详情提供 Directory Connection 摘要、endpoint 复制和 token 轮换入口。
- [x] 3.2 更新 OpenAPI 类型、错误码、审计和部署文档。
- [ ] 3.3 明确 Casdoor 参考点与不采用的实现点。

## 4. Verification

- [x] 4.1 后端单元、集成、PostgreSQL 16/17、权限、迁移和 OpenAPI drift 检查。
- [x] 4.2 Web tests/typecheck/lint/build 和无权限/暂停/空状态验收。
- [x] 4.3 使用可重复 SCIM reference service 验证用户全生命周期和幂等重放。
- [x] 4.4 验证目录停用即时撤权、手工 membership/Namespace 保留、日志脱敏和资源清理。
