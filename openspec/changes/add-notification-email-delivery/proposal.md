## Why

[#978](https://github.com/iflytek/skillhub/issues/978) 希望用户离开网页后仍能收到发布、审核、提升和举报相关通知。现有 SMTP 已用于密码重置，但业务通知只有站内通道；直接逐条转成邮件会造成订阅更新刷屏。

用户已批准按本技术方案实施。按通知类别分别选择即时或摘要，默认关闭；先以本地 SMTP 捕获器验证，不发送真实外部邮件。实现、验证与剩余限制回写本变更；合并仍需单独授权。

## What Changes

- 用户对每个现有通知类别选择 `OFF / IMMEDIATE / DIGEST`，邮件默认关闭，与站内开关独立。
- 摘要按收件人和时间窗口合并，多个技能、多个摘要类别合为一封，类别分节；即时模式仍按通知实例投递，不承诺所有通知都聚合。
- 复用 SMTP，独立验证当前账号的通知收件邮箱，不修改注册、登录准入或账号绑定规则。
- 增加持久化邮件项与投递批次、多实例领取、明确的重试/不确定状态和治理记录；不引入消息中间件。
- 后台普通运行设置复用 `system_setting`，SMTP 凭证继续由部署侧管理；增加用户设置和运维文档。
- 如实保留现有“业务提交后异步产生通知”的入口丢失窗口，可靠性保证从邮件项成功入箱开始，不冒称业务事件端到端不丢失。

## Capabilities

### New Capabilities

- `notification-email-delivery`：类别投递模式、按人聚合、通道隔离、可靠投递边界和运维控制。
- `notification-email-recipient`：当前账号邮箱的独立验证、地址变更失效及发送资格检查。

### Modified Capabilities

无。本仓库尚未将现有站内通知归档为独立主规格；本变更新增邮件规格并明确保留既有站内接口，不借机改写注册/身份规格。

## Impact

- `skillhub-notification`：邮件偏好、收件验证与投递模型、仓储/发信端口和业务规则。
- `skillhub-infra`：新增持久化适配及 SMTP 实现；复用项目已使用的 Spring Mail，不指定新厂商 SDK。
- `skillhub-app`：通知渠道编排、设置与验证 API、调度、邮件目标解析和管理可观测性。
- `skillhub-domain` / `skillhub-auth`：当前邮箱修订号及必要内部事件身份，覆盖外部登录、合并、初始化等已有写入路径；不改变身份匹配、登录或注册规则。
- Web 通知设置、系统配置、OpenAPI/生成类型、数据库新增迁移、Compose/Helm 的配置说明及测试。
- 现有 `/api/v1/notification-preferences` 及 `/api/web/notification-preferences` 的站内布尔偏好保持兼容；新增邮件接口的候选契约见设计。
