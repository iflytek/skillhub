# 登录开关与外部账号首次授权

超级管理员在“系统配置”中分别控制本地密码登录和本地账号自行注册，默认都开启。关闭密码登录后，本地注册也暂时不可用，因为当前注册流程会建立本地会话；注册开关的原值会保留。关闭密码登录还会阻止密码重置、密码修改和直接密码认证。已有会话按原有效期结束，不会立刻踢下线。

关闭密码登录前，页面会提示可能失去管理入口，但允许继续。部署方应先确认至少有一位超级管理员能通过外部身份登录。若锁定管理入口，使用受控数据库操作恢复 `system_setting` 中 `auth.local` 的 `passwordLoginEnabled`，并记录操作；仅修改启动参数不会覆盖已有数据库设置。

## 空库初始化

仅在数据库没有对应记录时，启动参数提供初始值：

| 环境变量 | Helm 值 | 默认值 |
| --- | --- | --- |
| `SKILLHUB_AUTH_INITIAL_PASSWORD_LOGIN_ENABLED` | `auth.initialSettings.passwordLoginEnabled` | `true` |
| `SKILLHUB_AUTH_INITIAL_SELF_REGISTRATION_ENABLED` | `auth.initialSettings.selfRegistrationEnabled` | `true` |
| `SKILLHUB_AUTH_INITIAL_ROLE_GRANTS_JSON` | `secrets.initialRoleGrantsJson` | `[]` |

首次授权规则的 JSON 示例：

```json
[{"provider":"feishu","email":"admin@example.com","role":"SUPER_ADMIN"}]
```

规则仅在第一次初始化且 `user_account` 为空时写入；初始化标记防止以后删除规则再重启时重复创建。正式环境请通过 Secret 提供 JSON，不要将实际邮箱写进公开的 values 文件。初始化以后，数据库和“系统配置”页面是权威来源。

## 授权边界

规则按身份来源代码和**已验证邮箱**匹配。只有外部身份通过现有准入策略、并且新账号首次创建为可用状态时，才给新账号授予选定平台角色；规则随后标为“已授权”，不会再次使用。拒绝或待审批的登录不会触发授权，规则不会绕过准入策略。已有账号请在“用户管理”中直接授权。相同邮箱的本地账号不会因此合并或获得角色。

后台可以新增规则、调整待匹配规则的角色、停用规则，并查看已使用规则的匹配身份和授权账号。只允许超级管理员操作；修改带版本号，避免两个管理员同时改动时后写覆盖前写。紧急恢复可通过受控数据库操作处理，并保留审计记录。
