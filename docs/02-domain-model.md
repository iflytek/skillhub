# skillhub 领域模型与数据模型

## 0. 用户标识约束

- 用户身份主键全链路统一为 `string`。
- 本约束覆盖 `user_id`、`owner_id`、`created_by`、`updated_by`、`published_by`、`reviewed_by`、`actor_user_id` 及所有等价语义字段。
- 历史文档里写成 `bigint` / `BIGINT` 的用户关联字段均应按字符串重新解释；这些旧类型描述不再作为实现依据。
- 若未来数据库为了索引或存储效率引入内部 surrogate key，也只能作为内部实现细节，不能替代字符串 `userId` 成为认证、授权、审计和 API 契约的主键。

## 3.1 核心实体

### namespace

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| slug | varchar(64) | URL 友好标识 |
| display_name | varchar(128) | 展示名 |
| type | enum | `GLOBAL` / `TEAM` |
| description | text | 描述 |
| avatar_url | varchar(512) | 头像 |
| status | enum | `ACTIVE` / `FROZEN` / `ARCHIVED` |
| created_by | varchar(128) | 创建人 |
| created_at | datetime | |
| updated_at | datetime | |

- `GLOBAL` 类型全局唯一（只有一个 `@global`），由平台管理员管理
- `TEAM` 类型对应部门/团队，可创建多个
- 技能完整寻址：`@{namespace_slug}/{skill_slug}`
- slug 唯一约束：`slug`
- slug 格式校验：`[a-z0-9]([a-z0-9-]*[a-z0-9])?`，长度 2-64，且不得包含连续两个以上的连字符 `--`（为兼容层坐标映射保留）
- slug 保留词列表（用户创建 namespace 时不可使用）：`admin`, `api`, `dashboard`, `search`, `auth`, `me`, `global`, `system`, `static`, `assets`, `health`
- 系统内置 namespace（`@global`）在数据库初始化时由 Flyway 脚本预置，绕过 slug 校验规则。保留词校验仅作用于用户创建 namespace 的接口
- 状态语义：
  - `ACTIVE`：正常使用
  - `FROZEN`：冻结，只读不可发布新版本，已有技能仍可浏览/下载
  - `ARCHIVED`：归档，对外不可见

### namespace_member

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| namespace_id | bigint | |
| user_id | varchar(128) | |
| role | enum | `OWNER` / `ADMIN` / `MEMBER` |
| created_at | datetime | |
| updated_at | datetime | |

- `OWNER`：命名空间创建者，可转让
- `ADMIN`：可审核该空间内的技能发布、管理成员
- `MEMBER`：可在该空间内发布技能（提交审核）
- 唯一约束：`(namespace_id, user_id)`，一个用户在一个空间只有一个角色

### skill

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| namespace_id | bigint | 所属命名空间 |
| slug | varchar(128) | URL 友好标识 |
| display_name | varchar(256) | |
| summary | varchar(512) | |
| owner_id | varchar(128) | 主要维护人（可转让） |
| source_skill_id | bigint | 派生来源（团队技能提升到全局时记录原 skill ID），nullable |
| visibility | enum | `PUBLIC` / `NAMESPACE_ONLY` / `PRIVATE` |
| status | enum | `ACTIVE` / `ARCHIVED` |
| latest_version_id | bigint | latest published pointer，仅指向最新 `PUBLISHED` 版本；若不存在已发布版本则可为 `null` |
| download_count | bigint | |
| star_count | int | |
| rating_avg | decimal(3,2) | 平均评分 |
| rating_count | int | 评分人数 |
| created_by | varchar(128) | |
| created_at | datetime | |
| updated_by | varchar(128) | |
| updated_at | datetime | |

- 唯一约束：`(namespace_id, slug)`
- `status` 表示 skill 容器生命周期，不再承载“隐藏”语义。隐藏是独立的治理覆盖层，由 `hidden` / `hidden_at` / `hidden_by` 表达
- `owner_id` 语义为"主要维护人"，可转让。权限主轴是 namespace role，不是 owner：
  - namespace ADMIN 对空间内所有 skill 有完整管理权（归档、版本管理、提升到全局），不受 owner 限制
  - owner 作为 MEMBER 时可管理自己创建的 skill（提交审核、编辑草稿）
  - owner 离职/换组后，namespace ADMIN 仍能完整管理所有技能
- `rating_avg` / `rating_count` 冗余字段，避免每次查询聚合
- `slug`：面向用户的 URL 标识，来自 SKILL.md 的 `name` 字段，首次发布后不可变更。slug 格式校验规则与 namespace slug 相同：`[a-z0-9]([a-z0-9-]*[a-z0-9])?`，同样适用保留词限制，且不得包含连续两个以上的连字符 `--`（为兼容层坐标映射保留）。全局空间（`@global`）下的 skill slug 额外禁止包含 `--`，以避免与兼容层 canonical slug 产生歧义
- `source_skill_id`：仅在"团队技能提升到全局"场景下填充，记录原始团队空间的 skill ID，用于追溯来源
- 提升关系的唯一事实来源是 `promotion_request` 表，UI 查询"是否已提升"通过 `SELECT ... FROM promotion_request WHERE source_skill_id=? AND status='APPROVED'` 判定

### skill_version

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | |
| version | varchar(32) | semver |
| version_sort | bigint | 排序用数值 |
| changelog | text | |
| manifest_json | json | 文件清单 |
| parsed_metadata_json | json | SKILL.md frontmatter 解析结果 |
| status | enum | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `REJECTED` / `YANKED` |
| reject_reason | varchar(512) | 拒绝原因 |
| published_by | varchar(128) | |
| published_at | datetime | |
| created_at | datetime | |

- `status` 表示 version 发布生命周期，和 skill 容器状态、review task 状态分离
- 当前代码下的实际迁移约束：
  - 普通用户首次上传/重传新版本后，版本直接进入 `PENDING_REVIEW`
  - `SUPER_ADMIN` 直发时可直接进入 `PUBLISHED`
  - 审核通过：`PENDING_REVIEW → PUBLISHED`
  - 审核拒绝：`PENDING_REVIEW → REJECTED`
  - 撤回审核：`PENDING_REVIEW → DRAFT`
  - 已发布撤回：`PUBLISHED → YANKED`
- 唯一约束：`(skill_id, version)` 防止重复发布
- `YANKED` 状态：已发布后撤回

版本号不可变性规则：

| 版本状态 | 版本号处理 |
|---------|-----------|
| DRAFT | 可删除该版本记录，重新使用同版本号 |
| PENDING_REVIEW | 可撤回到 DRAFT |
| REJECTED | 可删除该版本记录，重新使用同版本号 |
| PUBLISHED | 版本号永久占用，不可复用 |
| YANKED | 版本号永久占用，不可复用，版本列表中显示但标记为不可下载 |

### skill_file

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_version_id | bigint | |
| file_path | varchar(512) | |
| content_type | varchar(128) | |
| size_bytes | bigint | |
| sha256 | varchar(64) | |
| object_key | varchar(512) | |
| is_entry_file | boolean | |
| created_at | datetime | |

### skill_tag

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | |
| tag_name | varchar(64) | |
| target_version_id | bigint | |
| created_by | varchar(128) | |
| created_at | datetime | |
| updated_by | varchar(128) | |
| updated_at | datetime | |

- `latest` 是系统保留标签，只读，自动跟随 `skill.latest_version_id`；其语义严格等价于“最新已发布版本”，不允许 API 手动移动
- 自定义标签（如 `beta`、`stable-2026q1`）允许人工创建和移动
- 唯一约束：`(skill_id, tag_name)`
- `target_version_id` 必须指向 `status = PUBLISHED` 的版本，应用层校验

### review_task

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_version_id | bigint | 关联的版本 |
| namespace_id | bigint | 所属空间（决定谁能审核） |
| status | enum | `PENDING` / `APPROVED` / `REJECTED` |
| version | int | 乐观锁版本号，默认 1 |
| submitted_by | varchar(128) | 提交人 |
| reviewed_by | varchar(128) | 审核人 |
| review_comment | text | 审核意见 |
| submitted_at | datetime | |
| reviewed_at | datetime | |

- 仅用于普通发布审核，"提升到全局"使用独立的 `promotion_request` 表
- `version` 字段用于乐观锁，防止多 Pod 并发审核
- 业务约束：同一 `skill_version_id` 在 `status=PENDING` 时只能存在一条记录，重复提交返回 409 Conflict。撤回时删除 `PENDING` review_task，并将 `skill_version` 回退到 `DRAFT`
- PostgreSQL 并发约束落地：通过唯一索引 `(skill_version_id)` + 软删除标记实现。`review_task` 表增加 `deleted` 字段（bigint, 默认 0），唯一索引改为 `(skill_version_id, deleted)`。撤回时将 `deleted` 设为 `id`（非零值），新提交时 `deleted=0`，利用唯一索引防止并发重复提交。或者采用更简单的方案：撤回时物理删除 review_task 记录，依赖 `INSERT` 的唯一约束 `(skill_version_id)` 防并发。PostgreSQL 还支持 partial unique index 方案：`CREATE UNIQUE INDEX ON review_task (skill_version_id) WHERE status = 'PENDING'`，更优雅地实现"PENDING 状态唯一"约束

### promotion_request

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| source_skill_id | bigint | 来源团队 skill |
| source_version_id | bigint | 申请提升的版本 |
| target_namespace_id | bigint | 目标全局 namespace |
| target_skill_id | bigint | 审批通过后生成的全局 skill ID，nullable |
| status | enum | `PENDING` / `APPROVED` / `REJECTED` |
| version | int | 乐观锁版本号，默认 1 |
| submitted_by | varchar(128) | 提交人 |
| reviewed_by | varchar(128) | 审核人 |
| review_comment | text | 审核意见 |
| submitted_at | datetime | |
| reviewed_at | datetime | |

- 完整表达"哪个团队 skill 的哪一版被申请提升到哪个全局空间"
- 审批通过后填充 `target_skill_id`，指向全局空间新创建的 skill
- `promotion_request` 是提升关系的唯一事实来源，skill 表不再冗余 `promoted_to_skill_id`
- 业务约束：同一 `source_version_id` 在 `status=PENDING` 时只能存在一条记录，重复提交返回 409 Conflict
- PostgreSQL 并发约束落地：与 `review_task` 类似，通过唯一索引防止并发重复提交。推荐使用 partial unique index：`CREATE UNIQUE INDEX ON promotion_request (source_version_id) WHERE status = 'PENDING'`，或增加 `deleted` 字段 + `(source_version_id, deleted)` 唯一约束，或采用物理删除 + `(source_version_id)` 唯一约束方案

### skill_star

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | |
| user_id | varchar(128) | |
| created_at | datetime | |

唯一约束：`(skill_id, user_id)`

### skill_rating

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | |
| user_id | varchar(128) | |
| score | tinyint | 1-5 |
| created_at | datetime | |
| updated_at | datetime | |

唯一约束：`(skill_id, user_id)`，每人每技能一条，可修改

### user_account

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| display_name | varchar(128) | |
| email | varchar(256) | |
| avatar_url | varchar(512) | |
| status | enum | `ACTIVE` / `PENDING` / `DISABLED` / `MERGED` |
| merged_to_user_id | varchar(128) | 合并目标用户 ID，仅 MERGED 状态有值 |
| created_at | datetime | |
| updated_at | datetime | |

- 状态语义：
  - `ACTIVE`：正常使用
  - `PENDING`：等待管理员审批（AccessPolicy 返回 PENDING_APPROVAL 时创建）
  - `DISABLED`：管理员封禁，登录后拒绝所有操作，返回 403
  - `MERGED`：已合并到其他账号，保留记录不物理删除，登录时自动跳转到合并目标账号
- 授权层在每次请求时检查用户状态，非 `ACTIVE` 用户拒绝所有写操作

### identity_binding

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| user_id | varchar(128) | |
| provider_code | varchar(64) | 如 `github` |
| subject | varchar(256) | OAuth Provider 返回的唯一用户标识 |
| login_name | varchar(128) | 如 GitHub login |
| extra_json | json | 原始扩展字段 |
| created_at | datetime | |
| updated_at | datetime | |

- 唯一约束：`(provider_code, subject)`
- 一期只接入 GitHub OAuth，但表结构支持后续扩展多个 OAuth Provider

### api_token

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| subject_type | varchar(32) | `USER`（一期）/ `SERVICE_ACCOUNT`（预留） |
| subject_id | varchar(128) | 关联主体 ID（一期等同于 user_id） |
| user_id | varchar(128) | 兼容字段，一期与 subject_id 相同 |
| name | varchar(128) | Token 名称（必填），如"CI/CD"、"本地开发" |
| token_prefix | varchar(16) | |
| token_hash | varchar(64) | |
| scope_json | json | |
| expires_at | datetime | |
| last_used_at | datetime | |
| revoked_at | datetime | |
| created_at | datetime | |

### audit_log

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| actor_user_id | varchar(128) | |
| action | varchar(64) | |
| target_type | varchar(64) | |
| target_id | bigint | |
| request_id | varchar(64) | |
| client_ip | varchar(64) | |
| user_agent | varchar(512) | |
| detail_json | json | |
| created_at | datetime | |

## 3.2 RBAC 实体

一期即上线完整 RBAC，平台角色按最小权限拆分，避免所有治理能力压在单一超管角色上。

平台角色（一期内置，Flyway 预置）：

| 角色 code | 说明 | 典型权限 |
|-----------|------|---------|
| `SUPER_ADMIN` | 平台超管，拥有所有权限 | 全部 |
| `SKILL_ADMIN` | 技能治理：全局空间审核、提升审核、隐藏/恢复、撤回已发布版本 | `review:approve`, `skill:manage`, `promotion:approve` |
| `USER_ADMIN` | 用户治理：准入审批、封禁/解封、角色分配（不可分配 SUPER_ADMIN） | `user:manage`, `user:approve` |
| `AUDITOR` | 审计只读：查看审计日志 | `audit:read` |

- 命名空间权限仍由 `namespace_member.role`（OWNER / ADMIN / MEMBER）决定，不走 RBAC 表
- 一个用户可持有多个平台角色（多条 `user_role_binding`）
- `SUPER_ADMIN` 隐含所有权限，代码中硬判定短路

### role

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| code | varchar(64) | `SUPER_ADMIN` / `SKILL_ADMIN` / `USER_ADMIN` / `AUDITOR` |
| name | varchar(128) | 展示名 |
| description | varchar(512) | |
| is_system | boolean | 系统内置角色不可删除 |
| created_at | datetime | |

### permission

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| code | varchar(128) | 如 `skill:publish`, `review:approve`, `user:manage` |
| name | varchar(128) | |
| group_code | varchar(64) | 权限分组 |

### role_permission

| 字段 | 类型 | 说明 |
|------|------|------|
| role_id | bigint | |
| permission_id | bigint | |

### user_role_binding

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| user_id | varchar(128) | |
| role_id | bigint | |
| created_at | datetime | |

## 3.3 搜索文档表

### skill_search_document

一个 skill 对应一条搜索文档，内容取“最新已发布版本”。实现上可由 `latest_version_id` 作为缓存指针承载，但其语义只能是 latest published pointer。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | |
| skill_id | bigint | 唯一，一 skill 一条 |
| namespace_id | bigint | 用于空间过滤 |
| owner_id | varchar(128) | 用于 PRIVATE 可见性判定 |
| title | varchar(256) | |
| summary | varchar(512) | |
| keywords | varchar(512) | |
| search_text | text | `displayName`、`slug`、`summary`，以及 frontmatter 中除 `name` / `description` / `version` 外的字段展开结果 |
| visibility | enum | 冗余，避免搜索时 join |
| status | enum | |
| updated_at | datetime | |

PostgreSQL Full-Text Index：在 `skill_search_document` 表增加 `search_vector tsvector` 列，通过触发器或 `GENERATED ALWAYS AS` 自动维护，建立 GIN 索引。

## 3.4 幂等记录表

### idempotency_record

| 字段 | 类型 | 说明 |
|------|------|------|
| request_id | varchar(64) | 主键，客户端传入的 UUID v4 |
| resource_type | varchar(64) | 如 `skill_version`, `api_token` |
| resource_id | bigint | 业务操作产生的资源 ID |
| status | enum | `PROCESSING` / `COMPLETED` / `FAILED` |
| response_status_code | int | 原始响应状态码 |
| created_at | datetime | |
| expires_at | datetime | 过期时间（默认 24h） |

- 流程：收到请求 → 插入 record（PROCESSING）→ 业务处理 → 更新为 COMPLETED + resource_id → 重复请求时查 record 返回已有结果
- Redis 做快速去重缓存（SETNX），PostgreSQL 做持久化兜底
- 定时任务清理过期记录

## 3.5 关键索引设计

| 表 | 索引 | 用途 |
|------|------|------|
| namespace | `(slug)` UNIQUE | 唯一约束 |
| skill | `(namespace_id, status)` | 命名空间内技能列表 |
| skill | `(namespace_id, slug)` UNIQUE | 唯一约束 |
| skill_version | `(skill_id, status)` | 版本列表 |
| skill_version | `(skill_id, version)` UNIQUE | 唯一约束 |
| skill_tag | `(skill_id, tag_name)` UNIQUE | 标签唯一约束 |
| review_task | `(namespace_id, status)` | 审核列表 |
| review_task | `(submitted_by, status)` | 我的提交 |
| promotion_request | `(source_skill_id)` | 按来源 skill 查询 |
| promotion_request | `(status)` | 待审核列表 |
| idempotency_record | `(expires_at)` | 过期清理 |
| audit_log | `(created_at)` | 审计查询 |
| audit_log | `(actor_user_id, created_at)` | 用户操作历史 |
| skill_star | `(user_id)` | 我的收藏 |
| skill_star | `(skill_id)` | 技能收藏数 |
| skill_rating | `(skill_id)` | 评分聚合 |
| namespace_member | `(namespace_id, user_id)` UNIQUE | 成员唯一约束 |
| namespace_member | `(user_id)` | 用户所属空间 |
| identity_binding | `(provider_code, subject)` UNIQUE | 身份查找 |
| api_token | `(token_hash)` | Token 校验 |
