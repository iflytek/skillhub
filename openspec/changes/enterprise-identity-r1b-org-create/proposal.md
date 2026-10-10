## Why

R1-B 的只读 Organization API 已合入，但平台管理员还不能创建测试 Organization 并指定初始负责人。没有这一入口，后续成员管理与 Login Connection 控制面无法独立验收。

## What Changes

- 仅 `SUPER_ADMIN` 可创建 Organization，并指定一个已存在、ACTIVE、非系统、未合并的平台账号为初始 owner。
- 在一个事务中创建 Organization、ACTIVE Membership、`ORG_OWNER` role binding 和组织审计记录。
- 返回新组织的基本标识和状态；平台管理员不会自动取得该组织成员身份。
- Organization 创建与成员权限属于协议无关的租户基础能力，不受未来的企业 OIDC 连接管理或登录开关控制。

## Non-goals

- 不增加成员、角色、域名或 Login Connection 的管理接口或页面。
- 不开放企业登录、自动识别组织、SCIM、目录同步或 Namespace 授权。
- 不修改现有数据库 schema、默认登录模式或公开 Provider 行为。

## Impact

新增 `POST /api/v1/admin/organizations` 与相应 OpenAPI 类型；使用现有 Organization、Membership、Role Binding 和 audit_log 表。参见 [总体分批计划](../enterprise-identity-platform/rollout-plan.md)。
