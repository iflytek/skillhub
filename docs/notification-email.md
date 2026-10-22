# 邮件通知接入与使用

SkillHub 可以通过部署方的 SMTP 服务发送业务通知。邮件与站内通知独立：关闭站内通知不会关闭邮件，删除站内消息也不会删除邮件批次明细。升级后邮件通知及所有用户的邮件类别默认关闭，不会自动给旧账号订阅。

SMTP 连接、TLS 和企业邮箱接入方式见 [SMTP 配置指南](19-smtp-password-reset-email-setup.md)。该指南中的密码重置验证码配置仍属于密码重置；业务通知与通知邮箱验证码使用本页说明的独立发件人配置，不改变登录或注册流程。

## 用户如何开启

1. 登录后进入 **通知设置**（`/settings/notifications`）。
2. 在“邮件通知”区域确认当前账号的脱敏邮箱。系统不接受临时填写其他收件人地址；无邮箱的账号需要先通过现有账号资料维护途径补齐邮箱。
3. 发送通知邮箱验证码，再填写邮箱中收到的六位码。验证码 10 分钟有效、60 秒内不可重发；最多允许五次错误，重发后只有最新码有效。该码不能用于登录或重置密码，也不授予任何角色。
4. 按通知类别选择 **关闭、即时或摘要**。所有类别默认关闭，保存后只影响新事件，不补发以前的通知。

所有账号都需要完成本次通知收件验证；不会将历史注册邮箱或外部登录中的旧邮箱推定为已验证。更换账号邮箱后证明失效，即使换回原地址也需要重新验证。邮箱验证不等于账号合并。

### 不是每个技能一封邮件

- **关闭**：不接收该类别，还会取消该类别尚未开始发送的项。
- **即时**：一个有效通知事件可能发送一封邮件。同一技能发生多次事件，可能多封。
- **摘要**：同一账号、同一时间窗口内，多个技能、多个摘要类别合为一个逻辑邮件批次。默认窗口 60 分钟，部署管理员可以调整。

例如将窗口配置为 15 分钟：该窗口内 12 条技能更新和 3 条审核通知，两个类别都选摘要，则合成一封邮件，按类别分节，共 15 条。不会按技能分成 15 封。不同账号即使邮箱相同，也不会合并内容或抄送。

“发布”类别同时包含作者的发布结果和订阅技能更新，建议使用摘要；目前不能为这两类分别设置频率。“审核”也可能包含个人资料审核，并非只有技能审核。密码重置、通知邮箱验证码等安全邮件始终即时发送，不参与业务摘要。

每封摘要最多展开 50 条，超出的通知通过邮件中的 **批次详情** 入口分页查看；不会为分页再拆发多封。详情路径为 `/dashboard/notifications/email/{id}`，要求登录为对应收件账号，并逐项检查当前权限。退出空间、失去权限或资源删除后，相关项不再展示。保留期结束、批次不存在或不属于当前账号时，页面会提示不可用。此明细不依赖站内通知是否开启、是否已读或已删除。明细采用游标逐页读取原始记录，再过滤当前权限；本页可见项为空但仍有下一游标时，可以继续查看后页。“原始记录数”不是当前可查看条数，“不可访问”数量仅对应本页。已合并的旧批次会提供目标摘要入口。

## 管理员如何开启

1. 在部署侧配置 SMTP、独立通知发件人、有限网络超时和真实公开站点根地址。不要把凭证提交进 Git，也不要在后台填 SMTP 密钥。
2. 超级管理员打开 **系统配置 → 业务邮件通知**。
3. 确认 SMTP 配置已存在，填写可信站点根地址（子路径部署须包含子路径，例如 `https://skills.example.com/skillhub`），设置摘要窗口后启用。
4. 用测试账号完成验邮，分别验证即时和摘要，再让用户自主订阅。

后台的“SMTP 配置存在”不是连通性测试，也不证明邮件已进入真实邮箱。管理员无法代用户完成收件验证或自动订阅。

普通开关、摘要窗口及站点地址存于 `system_setting` 的 `notification.email` 组。部署初值仅初始化缺失的设置；数据库已存在时，重启或修改初值不会覆盖后台设置。摘要窗口范围 5–1440 分钟，调整只影响之后入箱的项；旧项沿原窗口发送，不会立即重新分组。

## 部署配置

### Compose / 直接运行后端

Compose 继续使用 `.env.release` 与 `compose.release.yml`。以下为本功能新增配置；SMTP 连接用户名、密码和 TLS 仍使用[既有 SMTP 参数](19-smtp-password-reset-email-setup.md#1-需要配置的环境变量)。

| 环境变量 | 默认值 / 用途 | 后续由谁管理 |
|---|---|---|
| `SKILLHUB_NOTIFICATION_EMAIL_ENABLED` | `false` | 仅初始化缺失的数据库设置，之后用后台 |
| `SKILLHUB_NOTIFICATION_EMAIL_DIGEST_WINDOW_MINUTES` | `60`，范围 5–1440 | 仅初始化缺失的数据库设置，之后用后台 |
| `SKILLHUB_NOTIFICATION_EMAIL_PUBLIC_BASE_URL` | 回退 `SKILLHUB_PUBLIC_BASE_URL` | 仅初始化缺失的数据库设置，之后用后台 |
| `SKILLHUB_NOTIFICATION_EMAIL_FROM_ADDRESS` | Compose / application 缺省时回退 `SPRING_MAIL_USERNAME` | 部署侧；建议显式设置获 SMTP 许可的发件人 |
| `SKILLHUB_NOTIFICATION_EMAIL_FROM_NAME` | `SkillHub` | 部署侧 |
| `SPRING_MAIL_SMTP_CONNECTION_TIMEOUT_MS` | `5000`，连接超时毫秒 | 部署侧，共享 SMTP，也影响密码重置 |
| `SPRING_MAIL_SMTP_READ_TIMEOUT_MS` | `10000`，读取超时毫秒 | 部署侧，共享 SMTP |
| `SPRING_MAIL_SMTP_WRITE_TIMEOUT_MS` | `10000`，写入超时毫秒 | 部署侧，共享 SMTP |
| `SKILLHUB_NOTIFICATION_EMAIL_DELIVERY_POLL_DELAY_MS` | `10000`，轮询毫秒；最小 100 | 部署侧 |
| `SKILLHUB_NOTIFICATION_EMAIL_DELIVERY_LEASE_SECONDS` | `120`，租约秒；最小 30 | 部署侧；须覆盖有界 SMTP 尝试及确认开销 |
| `SKILLHUB_NOTIFICATION_EMAIL_DELIVERY_RETRY_WINDOW_HOURS` | `24`，临时故障恢复重试窗口（小时） | 部署侧；自首次临时失败计时，不是送达时限保证 |
| `SKILLHUB_NOTIFICATION_EMAIL_DELIVERY_BATCH_SIZE` | `20`，每轮领取批次数；范围 1–100 | 部署侧；不同于邮件内最多展开 50 条 |
| `SKILLHUB_NOTIFICATION_EMAIL_DELIVERY_SENT_RETENTION_DAYS` | `30`，已发送 / 已抑制记录保留天数；最小 8 | 部署侧 |
| `SKILLHUB_NOTIFICATION_EMAIL_DELIVERY_FAILED_RETENTION_DAYS` | `90`，失败 / 未知记录保留天数；最小 8 | 部署侧 |

这些参数的入口是 `server/skillhub-app/src/main/resources/application.yml`；Compose 透传在 `compose.release.yml`。前三项不会因重新部署覆盖已存在的 `notification.email` 设置，不要通过反复修改环境变量尝试改变已经保存的运行策略。

非秘密配置示例（SMTP 凭证另用受控部署配置注入）：

```dotenv
SKILLHUB_NOTIFICATION_EMAIL_ENABLED=false
SKILLHUB_NOTIFICATION_EMAIL_DIGEST_WINDOW_MINUTES=60
SKILLHUB_NOTIFICATION_EMAIL_PUBLIC_BASE_URL=https://skills.example.com/skillhub
SKILLHUB_NOTIFICATION_EMAIL_FROM_ADDRESS=notifications@example.com
SKILLHUB_NOTIFICATION_EMAIL_FROM_NAME=SkillHub
SPRING_MAIL_SMTP_CONNECTION_TIMEOUT_MS=5000
SPRING_MAIL_SMTP_READ_TIMEOUT_MS=10000
SPRING_MAIL_SMTP_WRITE_TIMEOUT_MS=10000
```

保持关闭部署并验证连通后，由超级管理员在后台启用。SMTP 和发件人改变需要更新部署、重启后端；通知开关、周期和站点 URL 保存后由数据库生效。

### Helm

Chart 提供 `notificationEmail` 的五个非秘密字段；SMTP 使用既有 `server.extraEnv`。凭证引用同命名空间下由部署方预先管理的 Secret，不能将明文密码写进 values、ConfigMap 或命令行：

```yaml
notificationEmail:
  enabled: false
  digestWindowMinutes: 60
  publicBaseUrl: "https://skills.example.com/skillhub"
  fromAddress: "notifications@example.com"
  fromName: "SkillHub"
server:
  extraEnv:
    - name: SPRING_MAIL_HOST
      value: "smtp.example.com"
    - name: SPRING_MAIL_PORT
      value: "587"
    - name: SPRING_MAIL_SMTP_AUTH
      value: "true"
    - name: SPRING_MAIL_SMTP_STARTTLS_ENABLE
      value: "true"
    - name: SPRING_MAIL_USERNAME
      valueFrom:
        secretKeyRef:
          name: skillhub-smtp
          key: username
    - name: SPRING_MAIL_PASSWORD
      valueFrom:
        secretKeyRef:
          name: skillhub-smtp
          key: password
```

以上 Secret 名、键及域名是部署示例，Chart 不自动创建 `skillhub-smtp`。请显式填写 Helm 的 `fromAddress`；`publicBaseUrl` 留空时 Chart 回退顶层 `publicBaseUrl`。worker 和超时参数需调整时，也通过 `server.extraEnv` 增加上述对应环境变量，不需要为每项创建后台字段。

Chart 配置与安装方式见 [Helm 手册](../charts/skillhub/README.md)。渲染、Secret 注入、真实 SMTP 连通和企业邮箱收件是不同验证层次；通过 `helm template` 不能证明邮件实际发送成功。

## 投递状态与故障处理

在系统配置的“投递记录”中按状态筛选并分页查看。仅超级管理员可访问；记录不显示完整邮箱、验证码或邮件正文。

| 状态 | 含义与处理 |
|---|---|
| 待发送 | 等待即时任务或摘要窗口到期 |
| 正在整理摘要 | 正在按有界批次整理通知，尚未取得发送授权 |
| 已并入摘要 | 原批次已并入恢复摘要，用户可通过明细页打开目标批次 |
| 已领取 | worker 持有租约，尚未取得发送授权 |
| 正在发送 | 最终资格检查已提交发送授权；即使 SMTP 尚未开始，也不能保证撤回 |
| 邮件服务器已接受 | SMTP 接受了邮件，不代表进入收件箱、用户已读或链接可访问 |
| 等待重试 | 明确的临时失败，在首次临时失败起默认 24 小时恢复窗口内退避，并重新检查资格 |
| 发送失败 | 永久拒绝或临时故障恢复窗口耗尽；修复后可受限人工重试 |
| 结果未知 | 邮件可能已被服务器接受，系统不会盲目自动重发 |
| 已停止发送 | 平台关闭、用户关闭类别、邮箱变化或收件资格不满足等 |

临时故障恢复窗口从首次明确的临时失败开始，重启进程或追加到期通知不会延长这一窗口。重试时可将同一账号后来到期的摘要并入恢复摘要，避免恢复后按积压时间窗逐封发送；整理和详情读取均采用有界处理。永久拒绝直接失败，UNKNOWN 不自动重发。24 小时是重试策略，不代表服务承诺在 24 小时内送达。

人工重试保留原收件账号和记录，并重新核对收件资格、从本次首次临时故障起开启默认最长 24 小时的新恢复窗口、记录审计；不能任意改写收件人或注入通知内容。结果未知时必须确认“可能产生重复邮件”的风险。SMTP 不提供可靠的全链路幂等保证，固定 Message-ID 也不代表恰好一次。

关闭平台或用户类别会取消尚未开始发送的项；已经取得发送授权或被 SMTP 接受的尝试不能承诺撤回。重新开启不会补发关闭期间的历史事件。改为摘要或即时只影响新事件，不把之前积压的摘要转成即时群发。

### 排查顺序

1. 确认平台开关、用户类别和当前邮箱验证状态。
2. 确认摘要窗口是否到期；不要将等待窗口误认为发送失败。
3. 查看投递状态、尝试次数及安全结果码；检查 SMTP 服务端日志和捕获器，不打印验证码、完整邮件正文或凭证。
4. 测试 SMTP 连通/TLS/发件人许可；配置存在不等于认证正确。
5. 对 UNKNOWN 先核对 SMTP 服务端接受记录，再决定是否人工重试。
6. 本地 SMTP 捕获成功只能证明应用交付到捕获器；真实企业邮箱还受反垃圾策略、SPF/DKIM/DMARC、隔离区等影响，需部署方另验。

## 可靠性与保留边界

当前业务事件在事务提交后经已有异步监听入箱。业务成功后、通知项持久入箱前若进程崩溃，通知仍可能丢失；本功能没有把所有业务事件改造成事务 outbox。持久入箱后使用数据库租约、去重和有限重试，邮件故障不应让已提交的发布或审核回滚。

新入箱仅接受发生时间距今七天内的事件；旧事件重放不能在去重记录清理后重新发信。正常投递重试和人工重试复用原记录，不重新入箱。已发送及抑制记录默认保留 30 天，失败及未知默认 90 天；未完成队列不按终态记录清理，清理后不能恢复或重试该批次。

回退应用先关闭邮件、等待 worker 结束并保留表。旧应用不维护邮箱修订号，因此回退前须由受控维护操作失效全部通知验邮证明/挑战、抑制未发项；重新升级保持关闭，用户重新验邮和订阅。不得仅回退镜像后恢复旧验邮证明，也不要删除业务或站内通知数据来“回滚”。具体维护命令应由部署者针对已验证的版本与数据库备份执行。
