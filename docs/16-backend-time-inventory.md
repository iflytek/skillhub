# skillhub 后端时间字段台账

## 1. 扫描范围

本台账基于 `server/skillhub-app`、`server/skillhub-auth`、`server/skillhub-domain`、`server/skillhub-infra`、`server/skillhub-storage` 的当前生产代码与 Flyway migration。

目标已经从“摸底问题分布”转为“记录当前真实进展与剩余尾项”。

## 2. 当前代码分布

### 2.1 生产代码中的 `LocalDateTime` 已基本清空

当前生产代码里只剩 1 处兼容解析保留 `LocalDateTime`：

- `ApiTokenService`
  - 用于兼容旧接口传入的裸时间字符串
  - 当前明确按 UTC 解释后转成 `Instant`

此前集中使用 `LocalDateTime` 的主链区域已完成迁移或收口：

- 认证与账号：
  - `api_token`
  - `account_merge_request`
  - `user_account`
  - `identity_binding`
  - `role`
  - `user_role_binding`
  - `local_credential`
- 核心领域：
  - `namespace`
  - `namespace_member`
  - `skill`
  - `skill_version`
  - `skill_file`
  - `skill_tag`
  - `skill_version_stats`
  - `skill_report`
  - `skill_star`
  - `skill_rating`
- 服务层：
  - `AccountMergeService`
  - `LocalAuthService`
  - `SkillPublishService`
  - `SkillGovernanceService`
  - `ReviewService`
  - `PromotionService`
  - `SkillReportService`
- DTO 与接口输出：
  - `NamespaceResponse`
  - `MemberResponse`
  - `SkillSummaryResponse`
  - `SkillVersionResponse`
  - `SkillVersionDetailResponse`
  - `TagResponse`
  - `AdminUserSummaryResponse`
  - `AdminSkillReportSummaryResponse`

结论：

- 主系统核心“事件发生时间”已经基本收口成 UTC 绝对时间
- 当前剩余工作主要是兼容策略、数据库尾项复核和防回归约束

### 2.2 `Instant` 已成为主流绝对时间类型

当前已稳定使用 `Instant` 的代表区域：

- 审计：
  - `AuditLog`
  - `AuditLogItemResponse`
- 通知：
  - `UserNotification`
- 审核流程：
  - `ReviewTask`
  - `PromotionRequest`
  - `ReviewTaskResponse`
  - `PromotionResponseDto`
- 幂等：
  - `IdempotencyRecord`
  - `IdempotencyInterceptor`
  - `IdempotencyCleanupTask`
- 技能主链：
  - `Skill`
  - `SkillVersion`
  - `SkillTag`
  - `SkillFile`
  - `SkillVersionStats`
- 认证主链：
  - `ApiToken`
  - `AccountMergeRequest`
  - `UserAccount`
  - `IdentityBinding`
  - `Role`
  - `UserRoleBinding`
  - `LocalCredential`

## 3. 数据库层分布

### 3.1 已完成的 `TIMESTAMPTZ` 迁移

- `V12__governance_notifications.sql`
  - `user_notification.created_at / read_at`
- `V13__api_token_timestamptz.sql`
  - `api_token.expires_at / last_used_at / revoked_at / created_at`
- `V14__account_merge_request_timestamptz.sql`
  - `account_merge_request.token_expires_at / completed_at / created_at`
- `V15__skill_version_timestamptz.sql`
  - `skill_version.published_at / created_at / yanked_at`
- `V16__skill_hidden_at_timestamptz.sql`
  - `skill.hidden_at`
- `V17__skill_created_updated_timestamptz.sql`
  - `skill.created_at / updated_at`
- `V18__namespace_timestamptz.sql`
  - `namespace.created_at / updated_at`
  - `namespace_member.created_at / updated_at`
- `V19__skill_secondary_timestamptz.sql`
  - `skill_tag.created_at / updated_at`
  - `skill_file.created_at`
  - `skill_version_stats.updated_at`
- `V20__social_and_skill_report_timestamptz.sql`
  - `skill_star.created_at`
  - `skill_rating.created_at / updated_at`
  - `skill_report.created_at / handled_at`
- `V21__user_account_timestamptz.sql`
  - `user_account.created_at / updated_at`
- `V22__auth_supporting_tables_timestamptz.sql`
  - `identity_binding.created_at / updated_at`
  - `role.created_at`
  - `user_role_binding.created_at`
  - `local_credential.locked_until / created_at / updated_at`
- `V23__review_and_idempotency_timestamptz.sql`
  - `review_task.submitted_at / reviewed_at`
  - `promotion_request.submitted_at / reviewed_at`
  - `idempotency_record.created_at / expires_at`

### 3.2 当前状态

- 主链核心事件时间列已基本完成 `TIMESTAMPTZ` 收口
- 初始建表 migration 中仍然能看到旧 `TIMESTAMP` 定义，但已由后续 Flyway 升级覆盖
- 后续重点不是“大批量迁移”，而是查漏补缺和约束新增

## 4. 已解决的高风险热点

### 4.1 兼容层时区解释冲突

此前：

- `ClawHubCompatController` 按 `ZoneOffset.UTC` 转 epoch
- `ClawHubRegistryFacade` 按系统默认时区解释

当前：

- 已统一按 UTC 解释绝对时间
- `ClawHubRegistryFacade` 的 `LocalDateTime` epoch 转换重载已移除

### 4.2 服务层散落的 `now()`

此前热点包括：

- `ApiTokenService`
- `AccountMergeService`
- `LocalAuthService`
- `SkillPublishService`
- `SkillGovernanceService`
- `ReviewService`
- `PromotionService`
- `SkillReportService`
- 多个实体 `@PrePersist` / `@PreUpdate`

当前：

- 服务层当前时间已基本统一为注入 `Clock`
- 实体回调已基本统一为显式 UTC

## 5. 分批迁移进展

### Batch 1：基础设施与治理链路

已完成：

- UTC `Clock` Bean
- Hibernate UTC 配置
- Jackson UTC 配置
- `ApiResponseFactory`
- `IdempotencyInterceptor`
- `IdempotencyCleanupTask`
- 审计、通知、审核、幂等链路

### Batch 2：认证与账号链路

已完成：

- `ApiToken` / `ApiTokenService`
- `AccountMergeRequest` / `AccountMergeService`
- `LocalCredential`
- `UserAccount`
- `IdentityBinding`
- `Role`
- `UserRoleBinding`
- `LocalAuthService`

### Batch 3：技能核心领域

已完成：

- `Skill`
- `SkillVersion`
- `SkillFile`
- `SkillTag`
- `SkillVersionStats`
- `Namespace`
- `NamespaceMember`
- `SkillPublishService`
- `SkillGovernanceService`
- `ReviewService`
- `PromotionService`
- `SkillReport`
- `SkillStar`
- `SkillRating`

### Batch 4：DTO 与 API 契约收口

已完成：

- `NamespaceResponse`
- `MemberResponse`
- `SkillSummaryResponse`
- `SkillVersionResponse`
- `SkillVersionDetailResponse`
- `TagResponse`
- `AdminUserSummaryResponse`
- `AdminSkillReportSummaryResponse`
- `TokenController` 的 UTC 输出收口

## 6. 当前剩余尾项

- `ApiTokenService` 仍保留对裸 `LocalDateTime` 字符串的兼容解析
- 需要补静态扫描或 ArchUnit 约束，防止新增 `LocalDateTime.now()`
- 需要做一轮跨时区回归，把 `UTC` / `Asia/Shanghai` 纳入关键测试
