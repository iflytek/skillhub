## Why

R1-B 已允许平台管理员创建 Organization，并允许成员读取自己的组织，但首位 `ORG_OWNER` 尚不能从产品入口添加成员或委派管理角色。现有授权矩阵甚至不允许 owner 执行 `MANAGE_MEMBERS`，导致新组织无法由其所有者继续完成配置。

## What Changes

- `ORG_OWNER` 可以在本组织内添加已有的可用 Platform Account，并显式授予或撤销组织角色；`MEMBER_ADMIN` 仍可管理成员，但不能委派角色。
- 成员和角色的写操作与列表读取通过租户范围 API 提供，并在 Web 组织管理入口完成最小可用交互。
- 自我授权与授权他人遵循同一组织范围、ACTIVE Membership、最后一位 owner、并发和审计规则。
- `ORG_OWNER` 身份本身不自动提供 Login Connection 或 Secret 管理权限；授予 `IDENTITY_ADMIN`、`LOGIN_SECRET_ADMIN` 必须是独立显式操作。

## Non-goals

- 不实现 OIDC 连接配置、匿名企业登录、邮件邀请、预供给 subject、SCIM、自动 Namespace 授权或账号合并。
- 不合并历史大型控制面 PR #894；只交付可独立验收的成员与角色切片。
- 不改变现有公共登录及登录页的路由语义；只做兼容回归。

## Impact

涉及 Organization 领域授权、租户管理 API、组织管理 Web 页面、OpenAPI 类型及对应测试；使用已有 V60–V65 表，不修改 Flyway schema。下一片域名和 Login Connection 控制面在本片合并验收后单独开展。
