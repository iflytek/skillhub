## Design: ownership, maintenance and handover (2026-11-17)

This section is the technical approach for the requirements in `skill-ownership-model`, `skill-exact-addressing`, `skill-handover` and `team-member-departure`. It follows the maintainer decisions of 2026-11-17. Field and table names are suggestions until the implementation PRs. The earlier design for #974 follows unchanged at the end.

### Data model

On `skill`:

- `ownership_type`: `USER` or `NAMESPACE`. Defaults to `USER` for existing rows.
- `owner_id`: unchanged for user-owned rows; null for namespace-owned rows.
- `maintainer_id`: nullable; used only for namespace-owned source rows.
- `ownership_revision`: an integer increased on every ownership or maintainer change. Handover requests, conversion items and pending applications record it, so stale work is detected by comparing one number.

All schema changes are additive. Existing rows keep their ID, slug and owner. The existing constraint `UNIQUE(namespace_id, slug, owner_id)` (V13) stays. PostgreSQL treats nulls as distinct, so namespace-owned rows with the same slug can coexist without changing it. Publication-coordinate conflict rules are unchanged.

New table `skill_handover_request`: `id, skill_id, namespace_id, kind, from_ownership_type, from_owner_id, from_maintainer_id, from_ownership_revision, to_user_id, initiated_by, reason, status, expires_at, decided_by, decided_at, decision_note, version, created_at, updated_at`. A partial unique index allows one `PENDING` row per Skill. State changes are conditional updates on `status` and `version`.

### Global copies

The copy's responsibility comes from its source through the existing `source_skill_id` link (REQ-OM-05, REQ-OM-09). Handover and conversion never write a responsibility field on the copy. One domain resolver returns the effective ownership and responsible user for any Skill, following `source_skill_id` for active global copies. The copy's stored `owner_id` is kept as provenance and is no longer read for authorization.

Today some code reads the copy's own `owner_id`. Examples: the "team and global owner match" check in `PromotionService`, `VisibilityChecker`, `SkillQueryService` projections and My Skills listings. These callers move to the resolver. The call-site inventory (below) lists them all, and handover stays off until they are migrated. Public reads of the copy and platform governance (hide, unhide, revoke) keep their current, separate checks.

### Authorization

Every "is this the responsible user" check uses the resolver, and requires current membership for TEAM namespaces (delivery 1). Namespace OWNER/ADMIN and SUPER_ADMIN paths stay as they are. Authorization always reads the Skill row, never the search document. Hidden Skills keep today's rule: readable by the responsible user and namespace OWNER/ADMIN (`VisibilityChecker.canAccess`, `SkillQueryService.canManageRestrictedSkill`). Unhiding stays `SUPER_ADMIN` only (`AdminSkillController`).

### Exact addressing (delivery 2a)

`SkillSlugResolutionService.resolve` is extended, not replaced:

- **Optional `skillId`:** load by ID, check the namespace and slug match the path, then authorize. A mismatch or a missing right gives the same response as an unknown ID.
- **Slug-only, legacy rule kept:** `CURRENT_USER` still prefers the caller's user-owned record, then the published visible record. `PUBLISHED` still prefers the published visible record, then the caller's own. A maintenance relationship is not part of the slug-only preference. A maintained record is picked only when the legacy rule finds nothing and it is the only record the caller may access.
- **Selection required:** returned only when the legacy rule cannot decide, and for the upload case in REQ-ADDR-05. The error lists only candidates the caller may access (ID, owner or maintainer, visibility, status). The HTTP status (proposed: 409) is fixed in the inventory.

The Web app keeps `skillId` in routes and query keys from the moment a record is chosen. Mutations send it. The CLI adds `--skill-id`, and uses the stored install record to send it on update when available.

**Delivered first, as reviewable files in this change:**

1. **Call-site inventory.** Every place in the code that calls `findByNamespaceIdAndSlug` or reads `getOwnerId()`, by module, marked "must change" or "retest only", with the reason. Known "must change" examples: `SkillAvatarAppService.findSkill` and `CompatSkillLookupService.findByLegacySlug`, which take the first match.
2. **Compatibility matrix.** Rows: historical data shapes (single record, user-owned same-slug pairs, published plus private drafts, global copies, departed owners). Columns: old CLI install, old CLI update and download, ClawHub-compatible routes, Web deep links, API with and without `skillId`. Each cell gives the expected result and how it is validated.

### Handover completion (delivery 2b)

Lock order follows the existing user-then-namespace order (`NamespaceMemberService`, `AdminUserAppService.updateUserStatus`):

1. user rows of the previous responsible user and the recipient, ordered by ID;
2. namespace row;
3. request row;
4. source Skill row.

Review decisions, promotion approval and auto-approval also lock the source Skill row before they read responsibility. Under the lock, each one compares the application's recorded `ownership_revision` with the current one. If they differ, automatic approval is skipped and the application stays for a human (REQ-DEP-03, REQ-DEP-10). A human decision then checks current conditions. Handover completion does not check for pending work (REQ-HO-05).

Under the locks, completion re-checks REQ-HO-04. It then updates the source Skill, increases `ownership_revision`, marks the request `ACCEPTED` and writes audit records in one transaction. Notifications and search reindexing (source and copy) run after commit, using the existing `@TransactionalEventListener` pattern.

### Expiry and invalidation

Every action checks `expires_at` first. A scheduled job marks overdue requests `EXPIRED` and notifies the initiator. Membership, account and ownership changes invalidate affected pending requests in their own transaction. Completion re-checks regardless.

### Removal conversion (delivery 3)

`NamespaceMemberService.removeMember` keeps its user-then-namespace locks. In one transaction it:

1. deletes the membership, so access ends at commit (REQ-DEP-01);
2. inserts a `skill_ownership_conversion` row: removal event, namespace, removed user, operator, time and status;
3. registers the affected Skills with one set-based insert (`INSERT ... SELECT`) into `skill_ownership_conversion_item`: one row per owned source Skill and per maintainer assignment, with the responsibility it revokes (`OWNER` or `MAINTAINER`) and the `ownership_revision` seen at removal;
4. invalidates the user's pending handover requests in that namespace.

The transaction does not load each Skill or run its conversion workflow. Actual conversion, notifications and search reindexing run after commit. Registration still grows with the number of affected Skills, so removal is not constant-time; its scale is validated below. If any step fails, the whole transaction rolls back and the member is not removed.

A worker processes items in bounded batches. For each item it locks the Skill and checks the revision still matches. It then converts the Skill, or clears the maintainer, increases the revision and marks the item `DONE`. If the revision no longer matches, the item is marked `SKIPPED_CHANGED` and the Skill is not touched. Items are idempotent by status, so retries are safe. Newly created or later-changed Skills are never touched. Progress is visible in namespace management. Notifications go out after each batch commits.

**Temporary restriction, bound to the removal event.** An item restricts only the responsibility it recorded, and only while two things hold: the item is `PENDING`, and the Skill's current `ownership_revision` equals the revision recorded at removal. While both hold, the resolver treats the recorded user as not holding that responsibility. Every valid handover, governance assignment and conversion increases the revision, so:

- rejoining alone does not change the revision, and does not restore the old rights;
- a later valid handover or governance assignment, even to the same user, ends the restriction; that user then has the rights of the new responsibility;
- a retry of the old item sees the changed revision, marks it `SKIPPED_CHANGED` and leaves the new responsibility alone;
- `DONE` and `SKIPPED_CHANGED` items never restrict anything.

Access to internal content always follows current membership (REQ-DEP-01), independent of conversion items.

**Scale validation.** Before enabling conversion, run representative large-data tests: a member who owns or maintains many Skills (for example thousands) in a populated namespace. Measure removal-transaction duration and lock waits, including against concurrent reads, uploads and reviews in the namespace. Inject a failure during registration and confirm full rollback: membership kept, no event or items written. A more complex capture or snapshot mechanism is introduced only if these results show it is needed.

### Download links

New protected requests always check current authorization. Already-issued presigned URLs stay valid until `SKILLHUB_STORAGE_S3_PRESIGN_EXPIRY` (default `PT10M`). No revocation mechanism is added.

### Notifications and audit

- New in-app notification category `OWNERSHIP`. Email follows the existing per-category preference.
- Audit actions: `SKILL_HANDOVER_REQUESTED`, `SKILL_HANDOVER_ACCEPTED`, `SKILL_HANDOVER_REJECTED`, `SKILL_HANDOVER_CANCELLED`, `SKILL_HANDOVER_EXPIRED`, `SKILL_HANDOVER_INVALIDATED`, `SKILL_OWNERSHIP_CONVERTED` (trigger: handover, member removal with event ID, or remediation) and `SKILL_MAINTAINER_CHANGED`. Each records the before and after state, the operator, the request or event, and the reason.

### Rollout and rollback boundary

1. **Schema.** Additive migration only. Rolling back to older binaries is safe.
2. **Code that understands the new model.** Handover and removal conversion are behind switches that default to off. Rolling back to older binaries is safe while `SELECT count(*) FROM skill WHERE ownership_type = 'NAMESPACE'` is 0.
3. **Enable.** Only after every backend instance and consumer runs step-2 code, and the compatibility matrix passes. Once a namespace-owned row exists, older binaries are not supported, because they assume `owner_id` is never null. The supported rollback is turning the switches off on current binaries. That stops new handovers and conversions and leaves existing records readable.
4. **Validation of the boundary.** On a populated database copy: upgrade, enable, create namespace-owned rows, turn the switches off, and confirm reads, downloads and the old CLI still work. Separately, confirm an older binary refuses to start, or is documented as unsupported, when namespace-owned rows exist.

### Validation

Real-database concurrency tests use the existing Testcontainers PostgreSQL setup, such as `GithubContentRevisionConcurrencyIntegrationTest`. Each pair runs in both orders:

- accept vs. accept, cancel, recipient removal and recipient disabling;
- accept vs. review decision, promotion approval and auto-approval;
- recovery accept vs. the original owner rejoining;
- removal conversion vs. a pending handover, a governance reassignment and the user rejoining.

Other tests:

- same-slug read, update, download and delete on the exact record, with and without `skillId`;
- the compatibility matrix, including old CLI install records;
- source and copy consistency after handover and conversion;
- responsibility change during review, including auto-approval;
- conversion retry after a partial failure;
- departed-user access denial on old and new records;
- hidden and archived Skills keep their state;
- expiry enforced at action time;
- upgrade on a populated database.

The maintainers offered to help with populated-database upgrade and legacy-client validation. Results are reported as passed, failed and not run, separately from the proposed behavior.

---

## Earlier design for #974 (unchanged)

## 范围与当前状态

本设计用于 Issue #974 的独立实现，当前处于开发检查与审查阶段，尚未完成正式运行验收。产品需求见能力规范，历史问答见 proposal；本文件不增加第二套验收规则。

## 选择的最小方案

沿用技能当前所有者、账号状态和既有空间成员关系，不增加 Maintainer、接管状态或成员覆盖开关。复用现有鉴权和角色分支，将普通所有者读取与写入条件分开：读取允许账号正常的当前所有者，维护还要求是当前空间成员；管理员继续按原有独立授权路径处理。

仅在客户端隐藏按钮不足以保证边界。实现时需逐项追踪已存在的技能写入口，特别是历史版本重新发布、确认发布、撤回审核、头像和提升。不能使用只检查 owner 的旧 helper，或以 bypassMembershipCheck 恢复普通所有者权限。

## 审核与状态恢复

成员移除不批量撤回已提交申请，也不阻止有权审核人继续按原有流程决策。异步审核、扫描及执行者的权限，与被移除者的新写请求分开判断。账号停用采用既有认证机制，不新增凭证生命周期；重新启用后是否可维护仍取决于成员关系和当前所有者。

## 兼容性与数据

不删除、重建或搬迁技能，不修改 owner_id 或历史作者，不引入业务 SQL CHECK。若前端需要额外权限投影，优先复用现有 management state／capabilities；只有确有字段缺口时才作 additive 扩展，并同步 OpenAPI。个人空间当前也是 TEAM，不能凭名称追加特殊绕过；GLOBAL 不可变成员规则保持现状。

## 实施前需核对的工程事实

- 冻结实现基线并逐项追踪 Session、Token、普通发布、兼容 CLI 和套件绑定发布路径；此前审查未完整覆盖全部兼容入口。
- 盘点管理权限投影、列表、详情与下载的现有可见性分支，避免仅放开列表却使详情或下载拒绝。
- 将管理员正常路径与普通 owner＋空成员角色的旧测试分开；旧测试通过不意味着满足新规则。
- 对待审核申请的批准路径复核操作者语义，保留现有独立安全及生命周期校验，避免批量改一个 helper 导致审批意外失效。

## 验证边界

本次仅完成需求与设计文档，不声称源码、镜像、API 或浏览器验收通过。正式实施后按项目风险流程设计最小相关用例：正常成员正例、移除后读取、全部受影响写入口拒绝及无副作用、停用认证、重新加入恢复、已有申请审核、跨空间隔离和管理员回归。UI 与鉴权改动须在固定提交的本地服务上验证；不为文档整理启动预览。
