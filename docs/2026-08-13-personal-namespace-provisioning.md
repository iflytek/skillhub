# 注册时自动创建个人命名空间

## 背景

自建部署里常见的诉求：每个新账号都应该有一块属于自己的地盘，可以直接发布技能，
而不必先向管理员申请命名空间、也不必把半成品塞进 `global`。

在此之前 SkillHub 没有任何「全局设置」机制——只有按用户维度的通知偏好，
凡是部署级开关都只能靠配置文件加环境变量，改一次要重启。
本次改动同时补上这两块：一个通用的设置存储，和第一个使用它的功能。

## 一、通用设置存储（`system_setting`）

```sql
CREATE TABLE system_setting (
    setting_key VARCHAR(128) PRIMARY KEY,
    setting_value JSONB NOT NULL,
    updated_by VARCHAR(128) REFERENCES user_account(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

一行存一组设置，值是 JSON 文档，因此一组设置增加字段不需要新的迁移。

`SystemSettingService` 的读取接口强制调用方传入默认值：

```java
<T> T get(String settingKey, Class<T> type, T defaults)
```

这带来两个性质：

- **管理员没动过的设置组不存在数据库行**，读取时回落到部署的配置文件默认值。
  纯配置化的部署可以完全不碰控制台，行为与本功能上线前一致。
- **存量文档解析失败时同样回落到默认值**，并打一条 WARN 日志。
  一行损坏的设置不应该让登录这种关键路径挂掉。

设置组用 `@JsonIgnoreProperties(ignoreUnknown = true)`，
滚动升级时旧节点读到新节点写入的文档不会报错。

## 二、自动创建个人命名空间

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
控制台有实时预览，就是为了让这条规则在保存前可见。

冲突处理：候选 slug 若非法（保留字如 `admin`、长度不足）或已被占用，
依次尝试 `-2`、`-3`……最多 64 次；全部失败则跳过并记 WARN。
`admin` 这类保留字因此自然落到 `admin-2`。

幂等：用户若已经拥有任意非 GLOBAL 命名空间，直接跳过。
解封会再次发布 `UserActivatedEvent`，靠这条保证不会重复发一个命名空间。

## 三、配置

| 位置 | 项 | 默认 |
|------|-----|------|
| `application.yml` | `skillhub.namespace.personal-provisioning.enabled` | `false` |
| 控制台 | 启用开关、slug 模板、显示名模板 | `${username}` |

**默认关闭**：升级不应该让现有部署突然开始建命名空间。

模板刻意**不放在 `application.yml`**：它们含 `${...}`，
Spring 会当成属性占位符去解析（Boot 3.2 / Framework 6.1 尚不支持转义 `\${`）。
模板的默认值写在 `PersonalNamespaceProvisioningProperties` 的 Java 字段里，
运行期改动走控制台。

## 四、审计

`PUT /api/v1/admin/settings/personal-namespace` 写一条审计日志，
action 为 `SYSTEM_SETTING_PERSONAL_NAMESPACE_UPDATE`，target type `SYSTEM_SETTING`，
detail 中包含改动前后的完整设置。

## 五、后续可以复用的地方

`system_setting` 是通用的。最直接的下一个使用者是
[#318](https://github.com/iflytek/skillhub/issues/318)（管理员开关本地注册）——
目前只能靠在网关层挡 `/api/v1/auth/local/register`。
