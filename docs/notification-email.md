# 邮件通知接入与使用

SkillHub 可以通过部署方的 SMTP 服务发送业务通知。邮件与站内通知独立：关闭站内通知不会关闭邮件，删除站内消息也不会删除邮件批次明细。升级后邮件通知及所有用户的邮件类别默认关闭，不会自动给旧账号订阅。

SMTP 连接、TLS 和企业邮箱接入方式见 [SMTP 配置指南](19-smtp-password-reset-email-setup.md)。该指南中的密码重置验证码配置仍属于密码重置；业务通知与通知邮箱验证码使用本页说明的独立发件人配置，不改变登录或注册流程。

## 一键部署：从安装到实际收件

本节适用于 `runtime.sh` 安装的单机实例。业务通知要求目标发行包含[业务邮件通知改动](https://github.com/iflytek/skillhub/pull/1004)；仅存在 SMTP 参数不能证明所用镜像已提供该功能。不需要启用邮件的实例保持默认配置，不必申请 SMTP 账号，也不必完成用户验邮。

### 保持同一个实例与版本来源

首次安装仍使用原一键入口，邮件不增加第二套 Compose 项目或独立服务。例如：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up \
  --home /opt/skillhub \
  --public-url https://skills.example.com
```

`/opt/skillhub` 和域名均为部署示例；执行账号必须能写入该目录并访问 Docker。成功后记录输出中的 **Runtime dir** 和 **Lifecycle commands**，后续优先复制该实例输出的生命周期命令。已有实例不得改用示例目录：`--home` 改变后可能操作另一套实例。未指定目录时，脚本默认使用 `${TMPDIR:-/tmp}/skillhub-runtime`，不是当前仓库目录或 `~/.skillhub`；正式部署必须使用可持久保存的明确目录。

| 操作 | 保持的参数与配置 | 说明 |
|---|---|---|
| 重跑安装命令 | 原 `--home`、文件来源及镜像选项 | 脚本更新 Compose 和示例模板，已有 `.env.release` 保留 |
| 固定发行 | 匹配的 `--ref` 与 `--version` | `--ref` 选择 GitHub 配置文件来源，`--version` 选择镜像标签，不能互相替代 |
| 使用阿里云入口 | 原 `--aliyun` 或 `--mirror-registry` 等选项 | `--aliyun` 的运行文件来自 OSS，不能仅凭 `--ref` 推定 OSS 文件已固定为某个 Git tag |
| 使用自定义项目或 override | 原项目名、环境文件和全部 override | 保留原部署命令；不要改用另一组基础 Compose 命令 |

默认 GitHub 文件来源是 `main`。需要固定发行时，必须从匹配的已发布 Git ref 获取脚本，并将同一 ref 传给 `--ref`、将匹配镜像标签传给 `--version`；目标取值查对应 Release，不使用未发布标签。只指定 `--version` 不会同时固定脚本及 Compose 文件。

### 编辑原运行目录中的私有配置

一键脚本只下载部署清单和环境模板，不下载 Makefile 或配置校验脚本。不要在 Runtime dir 运行 `make validate-release-config`，也不要重新复制模板覆盖已有配置。

先在 **系统配置 → 业务邮件通知** 关闭平台邮件；初次安装默认关闭。已有数据库中的平台开关不受环境初值覆盖，因此不能只把 `SKILLHUB_NOTIFICATION_EMAIL_ENABLED` 改为 `false` 就认为已停止发送。

进入实际 Runtime dir，编辑已有 `.env.release`。下列命令中的目录须替换为该实例记录的目录：

```bash
cd /opt/skillhub
chmod 600 .env.release
vi .env.release
```

仅更新相应行，不替换数据库密码、存储凭证、匿名下载密钥或其他已有配置。真实密码只写入该私有文件，不放在命令参数、聊天记录或 Git 中；密码含 `$`、`#` 等字符时，使用 Compose 支持的单引号字面值写法，不执行 `source .env.release`。

```dotenv
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=notifications@example.com
SPRING_MAIL_PASSWORD='replace-with-smtp-app-password'
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
SKILLHUB_NOTIFICATION_EMAIL_FROM_ADDRESS=notifications@example.com
SKILLHUB_NOTIFICATION_EMAIL_FROM_NAME=SkillHub
SKILLHUB_NOTIFICATION_EMAIL_ENABLED=false
SKILLHUB_NOTIFICATION_EMAIL_DIGEST_WINDOW_MINUTES=60
SKILLHUB_NOTIFICATION_EMAIL_PUBLIC_BASE_URL=https://skills.example.com
```

以上均为占位值，发件地址必须得到 SMTP 账号许可。`465 + SSL` 需要 `STARTTLS=false`、`SSL_ENABLE=true`；服务商要求 `587 + STARTTLS` 时改为端口 `587`、`STARTTLS=true`、`SSL_ENABLE=false`。不配置 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST`，默认使用 JVM 证书信任库；不能用空字符串或 `*` 代替正常证书校验。旧配置中该变量的空值由包含本次修复的脚本移除，明确主机例外保留；旧脚本实例需先更新脚本或手动删除空行。

| 配置层 | 修改入口 | 生效与边界 |
|---|---|---|
| SMTP 地址、密码、TLS、发件人 | 原 `.env.release` | 重跑同实例 `up` 后重建受影响容器；只 `restart` 不重新注入环境 |
| 平台开关、摘要窗口、站点地址的初值 | `SKILLHUB_NOTIFICATION_EMAIL_ENABLED` 等前三项 | 仅初始化缺失的数据库设置；不是覆盖后台的开关 |
| 平台邮件策略 | 超级管理员的系统配置 | 保存到数据库后生效，普通重启和升级不覆盖 |
| 用户邮箱证明与类别偏好 | 当前账号的通知设置 | 用户自行验邮并选择即时或摘要；管理员不能代订阅 |

### 仍使用原一键命令加载配置

保存文件后，重新执行安装时记录的 **up** 命令，保持同一 `--home` 和文件／镜像来源。例如上面的实例仍执行相同命令：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up \
  --home /opt/skillhub \
  --public-url https://skills.example.com
```

一键脚本没有单独的 SMTP 开关命令。`up` 会按原顺序准备部署文件、检查数据库连接并启动应用；邮件参数由已有 Compose 透传，环境变化时更新容器。`pull` 只下载镜像，不启动更新后的应用；单独执行 `docker compose restart` 也不加载新环境。原部署若使用固定 `--ref`、`--version`、镜像仓库或其他选项，必须继续保留，不能直接照抄省略这些选项的示例。

通过记录的 **ps** 和 **logs** 命令查看服务及启动日志。Runtime dir 中也可使用该实例原有的 Compose 项目参数查看后端日志；不要运行完整 `config`、`env` 或 `docker inspect` 后把秘密值贴到问题单。

### 管理员启用，用户完成收件验证

1. 超级管理员打开 **系统配置 → 业务邮件通知**，核对“SMTP 配置存在”，设置真实 Web 站点根地址和摘要窗口后启用。子路径必须包含在站点地址中，不能使用仅服务器自身可访问的 `localhost` 或 `127.0.0.1`；该地址不会自动配置 DNS、证书或反向代理。
2. 测试账号进入 **通知设置**，核对该账号的脱敏邮箱，获取并填写通知邮箱验证码。真实邮箱收到验证码且页面显示“已验证”后，才证明应用到收件邮箱的该条路径可用；后台显示“SMTP 配置存在”不能替代这个验证。
3. 将一个测试类别设为即时，产生一次新的发布或审核事件；核对真实收件、投递记录及邮件链接。再将该类别设为摘要，在同一窗口内产生两条事件并等窗口结束，核对合并内容。
4. 用收到邮件的同一 SkillHub 账号打开明细，核对跳转目标；换另一个账号应被拒绝。测试结束后恢复测试偏好，再由用户自主订阅。

### 升级、轮换与关闭

升级前备份 `.env.release`、数据库和对象存储，并记录原文件来源与镜像标签。在同一个 Runtime dir 更新到匹配的发行，重跑对应 **up**；脚本只为不存在的 `.env.release` 复制模板，不用新版模板覆盖已有 SMTP 参数。丢失私有文件必须恢复备份，不能把重新生成的默认文件当作原凭证。

轮换 SMTP 密码或发件人时，先在后台关闭业务邮件并处理在途状态，再编辑原 `.env.release`、重跑同实例 **up**。先核对后端健康和 SMTP 配置；这一步不证明实际收件。随后在协调好的验证窗口重新启用平台邮件：未验邮测试账号先获取验证码并验邮，已验邮测试账号直接触发新业务事件，分别验证即时和摘要。平台关闭时通知邮箱验证码及业务邮件测试均不可用，不能把实际收件测试放在重新启用之前。验证失败时在后台再次关闭，按投递状态排查，不盲目重试 UNKNOWN。

受控启用不是收件账号隔离开关；已经验邮并订阅的既有用户，在平台启用期间也可能收到其新事件。验证窗口须协调业务操作，测试完成后恢复测试账号偏好并按部署安排正常开放。SMTP 连接被密码重置邮件共用，修改会同时影响该通道；若启用密码重置，另显式配置获准的 `SKILLHUB_AUTH_PASSWORD_RESET_FROM_ADDRESS`。仅轮换 SMTP 基础设施不要求把已验证用户全部重新验邮，但用户账号邮箱发生变化后必须重新验证。

暂停业务邮件只需在后台关闭；不删除容器、数据库、队列或数据卷。关闭会取消尚未取得发送授权的项，已授权或 SMTP 已接受的邮件不能撤回，重新开启也不补发关闭期间事件。应用降级另按本页“可靠性与保留边界”处理，不能用退回镜像代替数据库及验邮证明的兼容检查。

| 现象 | 定位位置 | 检查与处理 |
|---|---|---|
| 不配置 SMTP 后服务无法启动 | 同实例后端启动日志 | 默认不启用邮件不应要求 SMTP 凭据；先排查数据库、存储等实际错误，不填虚构 SMTP 服务器绕过启动 |
| 配置已改，发送仍使用旧参数 | Runtime dir、原 up 命令、容器更新时间 | 确认编辑的是同一实例文件并重跑 up；不要仅 restart 或另建一个 home |
| SSL 报 `Server is not trusted` | 旧 `.env.release` 的信任覆盖项 | 移除空 SSL_TRUST，使用修复脚本重跑 up；不要关闭 TLS 或设置通配信任 |
| 验证码发送失败后提示频繁 | 通知设置的重发倒计时、后端安全错误码 | 等待服务端冷却结束后再试，先处理 SMTP/TLS/账号错误；不要连续点击或清空限流状态 |
| 验证码收到但没有业务邮件 | 平台开关、当前账号验证状态、对应邮件类别 | 开启必要条件后产生新事件；不要用历史事件或站内通知开关推断邮件已订阅 |
| 摘要尚未到达 | 管理员摘要窗口和投递记录 | 等该窗口到期，再按实际 SMTP 结果排查；不要将等待窗口当作失败反复重试 |
| 邮件能收到，链接打不开 | 后台站点地址、当前登录账号 | 核对真实域名／子路径并使用收件账号登录；不要放宽明细权限解决账号不匹配 |

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

套件审核结果通知发送给本次申请的提交者；批准和拒绝均产生独立结果通知，入口为“我的审核进度”。站内结果通知不要求邮箱验证，但仍受站内“审核”类别开关控制。业务邮件还必须同时满足平台已启用、该账号当前邮箱已验证、邮件“审核”类别设为即时或摘要；只配置 SMTP 不会自动订阅用户。

即时邮件主题直接说明事项，摘要按窗口汇总。正文仅包含类别、固定事件类型说明、时间及数量，不复制资源名称、简介、概述或审核意见。邮件明细绑定收件的 SkillHub 账号；用其他账号打开同一链接不会获得访问权限，页面可退出切换账号。

“发布”类别同时包含作者的发布结果和订阅技能更新，建议使用摘要；目前不能为这两类分别设置频率。“审核”包括技能、套件及个人资料审核，并非只有技能审核。套件待审邮件按具体审核任务核对当前权限，任务撤回、处理或审核权被撤销后，尚未授权的项不再投递；邮件不复制套件名称或概述。密码重置、通知邮箱验证码等安全邮件始终即时发送，不参与业务摘要。

每封摘要最多展开 50 条，超出的通知通过邮件中的 **批次详情** 入口分页查看；不会为分页再拆发多封。详情路径为 `/dashboard/notifications/email/{id}`，要求登录为对应收件账号，并逐项检查当前权限。退出空间、失去权限或资源删除后，相关项不再展示。保留期结束、批次不存在或不属于当前账号时，页面会提示不可用。此明细不依赖站内通知是否开启、是否已读或已删除。明细采用游标逐页读取原始记录，再过滤当前权限；本页可见项为空但仍有下一游标时，可以继续查看后页。“原始记录数”不是当前可查看条数，“不可访问”数量仅对应本页。已合并的旧批次会提供目标摘要入口。

详情接口的 `size` 范围为 1–100，管理员投递列表的 `page` 范围为 0–1000000；非法分页参数或非数字游标返回 HTTP 400，不调用查询服务，也不在错误响应中回显原始游标。服务端返回值校验失败仍按 HTTP 500 处理。

## 管理员如何开启

1. 在部署侧配置 SMTP、独立通知发件人、有限网络超时和真实公开站点根地址。不要把凭证提交进 Git，也不要在后台填 SMTP 密钥。
2. 超级管理员打开 **系统配置 → 业务邮件通知**。
3. 确认 SMTP 配置已存在，填写可信站点根地址（子路径部署须包含子路径，例如 `https://skills.example.com/skillhub`），设置摘要窗口后启用。
4. 用测试账号完成验邮，分别验证即时和摘要，再让用户自主订阅。

发送验证码的重发间隔仅限制同一账号申请验证码，不限制业务邮件批量处理。发送失败后仍保留防滥用冷却，页面会重新读取服务端截止时间并显示倒计时。

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

部署时保持关闭，先核对后端健康和 SMTP 配置，再由超级管理员在协调窗口受控启用，最后执行通知验邮及实际业务收件测试；平台关闭时不能执行这些测试。SMTP 和发件人改变需要更新部署并重建后端容器；直接运行的宿主机后端需重启进程。通知开关、周期和站点 URL 保存后由数据库生效。

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

到期历史按有限批次逐步清理。恢复组开始清理后即视为过期，用户明细和管理员投递列表不再显示，也不能人工重试；物理记录可能在后续多轮才全部删除。清理中断后继续原进度，不会因为重启而恢复部分已删除的邮件内容。分页限制约束单轮写入规模，不代表任意数据量下都有固定清理耗时。

回退应用先关闭邮件、等待 worker 结束并保留表。旧应用不维护邮箱修订号，因此回退前须由受控维护操作失效全部通知验邮证明/挑战、抑制未发项；重新升级先保持关闭；完成部署核对并在协调窗口受控启用后，用户重新验邮和订阅。不得仅回退镜像后恢复旧验邮证明，也不要删除业务或站内通知数据来“回滚”。具体维护命令应由部署者针对已验证的版本与数据库备份执行。
