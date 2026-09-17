## Purpose

定义企业认证第一批上线所需的 Secret、审计、隐私、限流、可观测性、灰度和回滚约束，使管理员操作与匿名登录入口能够在生产环境安全运行。

## ADDED Requirements

### Requirement: Login secrets are encrypted and separately versioned

OIDC client secret SHALL 使用独立 envelope encryption 存储，并记录用途、key id、版本和状态；Login Connection revision 只保存 Secret version reference。API、UI、审计、异常和日志不得返回明文。

#### Scenario: Connection is created with a secret
- **WHEN** 有 Secret 轮换权限的管理员创建需要 client secret 的 OIDC connection
- **THEN** 系统加密保存 Secret、将其版本绑定到 revision，并只返回 configured/redacted 摘要

#### Scenario: Secret is replaced
- **WHEN** 获授权管理员为已有 connection 创建携带新 Secret 的 revision
- **THEN** 系统创建新的 Secret version，不覆盖历史密文，且新版本只有在对应 revision 激活后进入新登录数据面

#### Scenario: Request or exception is logged
- **WHEN** Secret 出现在 DTO、校验失败或上游异常上下文中
- **THEN** `toString`、错误响应和日志都不得包含 Secret 值或加密 envelope

### Requirement: Connection data-plane changes require explicit authority and confirmation

创建/修改 Login Connection SHALL 要求 `IDENTITY_ADMIN`；写入 Secret SHALL 额外要求 Secret 轮换权限；激活、暂停和禁用 SHALL 要求显式确认并产生审计。

#### Scenario: Reader attempts to rotate a secret
- **WHEN** 只有连接查看权限的成员提交 client secret
- **THEN** 系统拒绝请求且不创建 Secret version 或 revision

#### Scenario: Activation confirmation is absent
- **WHEN** 管理员调用激活、暂停或禁用接口但没有提交有效确认
- **THEN** 系统在状态变更前返回 usage/validation error

### Requirement: Authentication logs and errors are privacy preserving

系统 SHALL 对认证请求的全部 query values、Cookie、Authorization header、OIDC code/state/nonce/token、email、subject 和 Secret 进行省略或脱敏；对外错误 SHALL 可操作但不得支持账号、成员、组织或连接枚举。

#### Scenario: OIDC callback is logged
- **WHEN** callback URL 包含 code、state 或其他 query values
- **THEN** 日志只保留规范化 path 和统一 `[REDACTED]` query 标记，不保留参数名或值

#### Scenario: Upstream authentication fails
- **WHEN** provider 返回错误或内部校验抛出异常
- **THEN** 客户端收到标准化错误和 requestId，日志记录固定低基数类别而非 token、claims、email、subject 或响应正文

### Requirement: Anonymous authentication endpoints are rate limited

login discovery、enterprise start 和 callback SHALL 分别配置有界的匿名与已认证请求额度；额度耗尽时 SHALL 在调用身份服务前拒绝请求。

#### Scenario: Discovery quota is exhausted
- **WHEN** 匿名客户端超过 discovery 窗口额度
- **THEN** 系统返回 429 和现有结构化错误，不查询 Organization、Membership 或 Login Connection

#### Scenario: Trusted proxy is not configured
- **WHEN** 请求携带伪造 Forwarded/X-Forwarded-For 且直连地址不属于 operator 配置的可信代理
- **THEN** 限流身份使用直连地址，不信任转发头

### Requirement: Client IP resolution honors explicit proxy trust

只有 operator 明确配置的可信代理链 SHALL 影响客户端 IP 解析；无效、超长或混合可信度转发链 SHALL fail safely，不能让匿名调用者选择限流 key。

#### Scenario: Trusted proxy forwards a client address
- **WHEN** 直连 peer 属于可信代理且转发链符合配置
- **THEN** 系统按受控规则解析原始客户端地址

#### Scenario: Untrusted peer supplies forwarded headers
- **WHEN** 直连 peer 不可信但包含 Forwarded 或 X-Forwarded-For
- **THEN** 系统忽略这些 header 并使用直连 peer

### Requirement: Enterprise identity changes are auditable

Organization、Membership、Domain、Role Binding、Login Connection、revision、Secret、External Identity 冲突和 session 撤销 SHALL 产生结构化审计或固定类别安全事件，并携带 requestId 和租户上下文。

#### Scenario: Connection revision is activated
- **WHEN** 管理员激活经过测试的 revision
- **THEN** 审计包含 actor、organization、connection、revision、前后状态、时间和 requestId，不包含 typed config 中的敏感值或 Secret

#### Scenario: Login correlation is ambiguous
- **WHEN** 首次登录出现多个账号或 Membership 候选
- **THEN** 系统拒绝自动绑定并记录可聚合冲突类别，匿名响应不泄露候选对象

### Requirement: Rollout controls are independent and observable

身份核心模式、动态企业 OIDC 控制面开关、匿名登录数据面开关和 Organization allowlist SHALL 独立配置；默认 SHALL 为 legacy-compatible 且企业 OIDC 控制面与登录数据面关闭。系统 SHALL 暴露不含身份数据的启用状态、连接健康和固定类别指标。

#### Scenario: Candidate is deployed with defaults
- **WHEN** 新版本使用默认配置启动
- **THEN** 既有认证继续工作，动态 OIDC 不可发现且不执行

#### Scenario: Control plane is enabled without an explicit data-plane switch
- **WHEN** operator 开启动态 OIDC 控制面但没有显式开启匿名登录数据面
- **THEN** 连接可以配置和测试，但 discovery、start 和 callback 仍保持关闭

#### Scenario: One organization is allowlisted
- **WHEN** operator 先开启控制面完成连接配置与测试，再开启登录数据面并只加入一个测试 Organization
- **THEN** 只有该 Organization 的 ACTIVE connection 可进入 discovery/start/callback，其他组织行为不变

### Requirement: Rollback is non-destructive

operator SHALL 能依次移除 Organization allowlist、关闭 OIDC 登录数据面、将身份核心切回 LEGACY，而不删除新表、V2 binding、session origin 或审计记录；控制面开关可以独立保留用于诊断。

#### Scenario: Dynamic OIDC shows elevated failures
- **WHEN** callback failure、identity conflict 或连接不可用指标超过发布阈值
- **THEN** operator 可先关闭对应 Organization 或全局 OIDC 数据面，现有公共认证继续可用

#### Scenario: Unified core must be rolled back
- **WHEN** 公共 OAuth 的统一核心门禁导致无法接受的登录失败、冲突或异常
- **THEN** operator 切回 LEGACY 模式，应用不要求执行 Flyway down migration，legacy `identity_binding` 仍可继续服务现有登录

### Requirement: Release evidence is bound to the exact candidate SHA

可合并结论 SHALL 基于同一冻结 SHA 的源代码检查、本地镜像、数据库迁移、真实 OIDC 参考实现和浏览器验收；旧分支、旧镜像或 Mock 测试不得替代 exact-SHA 证据。

#### Scenario: Unit tests pass but runtime proof is missing
- **WHEN** L0/L1 检查通过但 candidate image 或真实 OIDC 浏览器流程未完成
- **THEN** 交付状态保持未就绪，不声称可合并或可发布

#### Scenario: Lab run completes
- **WHEN** exact-SHA OIDC lab 和浏览器矩阵完成
- **THEN** 报告记录 source SHA、image digest、迁移版本、命令、用例结果、资源使用和清理结果，且凭证扫描通过

### Requirement: Identity validation lab is isolated and resource bounded

测试实验室 SHALL 与生产部署文件和数据隔离，使用开源 OIDC 参考实现、测试凭证、受限 CPU/内存和可清理网络/volume；不得将实验室服务加入生产 compose 或镜像。

#### Scenario: Lab starts under WSL pressure
- **WHEN** 本地机器资源紧张时启动身份实验室
- **THEN** 编排只启动当前批次必需的 PostgreSQL、Redis、SkillHub 和 OIDC provider，并应用明确资源限制，不启动 SCIM、LDAP、SAML 或 CAS 服务

#### Scenario: Lab stops after validation
- **WHEN** 测试结束或失败
- **THEN** 清理脚本停止并移除本次创建的容器、网络、临时 volume、测试凭证和浏览器工件，不触碰其他项目资源
