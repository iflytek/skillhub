# 注册时自动创建个人命名空间

## 背景

自建部署里常见的诉求：每个新账号都应该有一块属于自己的地盘，可以直接发布技能，
而不必先向管理员申请命名空间、也不必把半成品塞进 `global`。

## 一、自动创建个人命名空间

### 「私有」在当前模型里的含义

命名空间没有可见性字段——只有 `GLOBAL` 和 `TEAM` 两种类型，
技能的可见性是技能自己的属性。因此这里的「私有命名空间」= **一个只有本人为成员的 TEAM 命名空间**。
本人拿到的是 `OWNER` 角色（比 `ADMIN` 更强：可以改设置、管成员、删除）。

如果要做到「别人搜不到这个命名空间」，那是独立的 namespace visibility 特性，不在本次范围内。

### 触发时机

在账号**第一次变得可用**时触发，共三处，均发布 `UserActivatedEvent`：

| 入口 | 位置 |
|------|------|
| 本地注册 | `LocalAuthService.register` |
| 外部身份首次登录 | `IdentityBindingService.bindOrCreate`（仅 `initialStatus == ACTIVE`） |
| 管理员审批 / 解封 | `AdminUserAppService.updateUserStatus`（仅从非 ACTIVE 转为 ACTIVE） |

第三处不可省略：开启了准入审批的部署里，用户在 OAuth 首次尝试时就以 `PENDING` 建号，
真正可用是在管理员审批那一刻。

### 为什么走事件 + AFTER_COMMIT

`PersonalNamespaceProvisioningListener` 用 `@TransactionalEventListener`
（默认 AFTER_COMMIT）并在自己的事务里建命名空间。原因是数据库约束：

```
namespace.created_by      REFERENCES user_account(id)
namespace_member.user_id  REFERENCES user_account(id)
```

- 如果**加入注册事务**：命名空间创建失败（例如 slug 竞态撞唯一约束）会把注册一起回滚，
  用户会因为「命名空间没建成」而登不上来。
- 如果在注册事务中**用 `REQUIRES_NEW` 挂起**：新事务看不到尚未提交的 `user_account` 行，
  外键检查会阻塞在外层事务的行锁上，形成互等。

放到提交之后就同时避开了这两点：账号已经落库，建命名空间失败只损失一个命名空间，
监听器捕获异常并记 WARN。

监听器**不加 `@Async`**：命名空间要在用户下一个请求到达前就绪。

### 命名模板

两个模板，占位符语法 `${...}`：

| 占位符 | 取值 |
|--------|------|
| `${username}` | 认证路径提供的用户名；缺失时依次回落到邮箱前缀、用户 ID |
| `${email_prefix}` | 邮箱 `@` 之前的部分 |
| `${user_id}` | 平台内部用户 ID |

未知占位符原样保留，让拼错的名字暴露出来，而不是静默消失。

slug 模板渲染后按 `SlugValidator` 的规则归一化：转小写、
字母数字以外的字符变连字符、去掉首尾与重复连字符。
**注意下划线不合法**——`${username}_space` 会得到 `alice-space`。
冲突处理：候选 slug 若非法（保留字如 `admin`、长度不足）或已被占用，
依次尝试 `-2`、`-3`……最多 64 次；全部失败则跳过并记 WARN。
`admin` 这类保留字因此自然落到 `admin-2`。

幂等：用户若已经拥有任意非 GLOBAL 命名空间，直接跳过。
解封会再次发布 `UserActivatedEvent`，靠这条保证不会重复发一个命名空间。

## 二、配置

| 位置 | 项 | 默认 |
|------|-----|------|
| `application.yml` | `skillhub.namespace.personal-provisioning.enabled` | `false` |
| 配置文件/环境变量 | 启用开关 | `true` |

默认只对新激活账号生效，不回填已有账号；如需关闭可设置环境变量。

模板刻意**不放在 `application.yml`**：它们含 `${...}`，
Spring 会当成属性占位符去解析（Boot 3.2 / Framework 6.1 尚不支持转义 `\${`）。
模板默认值固定为 `personal-${random}` 和 `${username}-个人空间`，
如需关闭可设置 `SKILLHUB_NAMESPACE_PERSONAL_PROVISIONING_ENABLED=false`。
