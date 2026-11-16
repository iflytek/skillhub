## Design: ownership, maintenance and handover (2026-11-16)

This section is the technical approach for the requirements in `skill-ownership-model`, `skill-exact-addressing`, `skill-handover` and `team-member-departure`. Field and table names are suggestions until the implementation PRs. The earlier design for #974 follows unchanged at the end.

### Data model

On `skill`:

- `ownership_type`: `USER` or `NAMESPACE`. Defaults to `USER` for existing rows.
- `owner_id`: unchanged for user-owned rows; null for namespace-owned rows.
- `maintainer_id`: nullable; used only for namespace-owned rows.
- `ownership_revision`: an integer increased on every ownership or maintainer change. Handover requests record it, so a stale request is detected by comparing one number.

The existing constraint `UNIQUE(namespace_id, slug, owner_id)` (V13) stays. PostgreSQL treats nulls as distinct, so namespace-owned rows with the same slug can coexist without changing the constraint. The rule of at most one published Skill per namespace and slug is unchanged.

New table `skill_handover_request`: `id, skill_id, namespace_id, kind, from_ownership_type, from_owner_id, from_maintainer_id, from_ownership_revision, to_user_id, initiated_by, reason, status, expires_at, decided_by, decided_at, decision_note, version, created_at, updated_at`. A partial unique index allows one `PENDING` row per Skill. State changes are conditional updates on `status` and `version`.

### Authorization

One domain helper answers "who is responsible for this Skill": the owner for user-owned Skills, the maintainer for namespace-owned Skills. Today's `isOwner` checks, such as in `VisibilityChecker`, move to this helper and also require current membership for TEAM namespaces (delivery 1). Namespace OWNER/ADMIN and SUPER_ADMIN paths stay as they are. Authorization always reads the Skill row, never the search document.

### Exact addressing

`SkillSlugResolutionService.resolve` currently prefers the caller's own Skill, then the first published one. It becomes:

- **Reads:** the single published Skill, or the caller's one usable unpublished candidate. If there is more than one, return the ambiguity error.
- **Writes:** the one Skill the caller may maintain for that slug. If there is more than one, return the ambiguity error.
- **Explicit selector:** `skillId`. It is resolved first, then authorization is checked; the ID alone grants nothing.

Routes that call `findByNamespaceIdAndSlug(...)` directly and take the first match, for example `SkillAvatarAppService.findSkill` and `CompatSkillLookupService.findByLegacySlug`, are moved to the resolver. The CLI sends `skillId` when the user passes `--skill-id`. Older CLIs keep working when the slug is unambiguous.

### Handover completion

Lock order follows the existing user-then-namespace order (`NamespaceMemberService`, `AdminUserAppService.updateUserStatus`):

1. user rows of the previous responsible user and the recipient, ordered by ID;
2. namespace row;
3. request row;
4. source Skill row, then its active global copy.

Promotion approval (`PromotionService.approvePromotion`) and the creation of pending work (review submission, visibility change, avatar submission, new upload) lock the source Skill row before reading the owner or checking for pending work. Acceptance and these paths then run one after the other. Member removal and account status changes already lock the user row first.

Under the locks, completion re-checks REQ-HO-04 and REQ-HO-05. It then updates the Skill and its copy, increases `ownership_revision`, marks the request `ACCEPTED` and writes audit records in one transaction. Notifications and search reindexing run after commit, using the existing `@TransactionalEventListener` pattern.

### Expiry and invalidation

Every action checks `expires_at` first. A scheduled job marks overdue requests `EXPIRED` and notifies the initiator. Membership, account and ownership changes invalidate affected pending requests in their own transaction. Completion re-checks regardless.

### Removal conversion (delivery 3)

`NamespaceMemberService.removeMember` already locks user, then namespace. In the same transaction it converts the removed user's Skills in that namespace (sources first, then their copies, by ID order), clears their maintainer assignments, increases `ownership_revision` on each, and invalidates their pending requests. Running in one transaction means there is no partial state to retry (open question 9). Audit records name the operator, with the trigger `MEMBER_REMOVED`.

### Notifications and audit

- New in-app notification category `OWNERSHIP`. Email follows the existing per-category preference.
- Audit actions: `SKILL_HANDOVER_REQUESTED`, `SKILL_HANDOVER_ACCEPTED`, `SKILL_HANDOVER_REJECTED`, `SKILL_HANDOVER_CANCELLED`, `SKILL_HANDOVER_EXPIRED`, `SKILL_HANDOVER_INVALIDATED`, `SKILL_OWNERSHIP_CONVERTED` (with the trigger: handover, member removal or remediation) and `SKILL_MAINTAINER_CHANGED`. Each one records the before and after state, the operator, the request and the reason.

### Rollout

1. Deploy the schema and code that understand namespace-owned rows. No conversion happens yet.
2. Enable handover and removal conversion only after every backend and client in the deployment runs that version.
3. After the first conversion, rolling back to an older application is not supported, because older code assumes `owner_id` is never null. Operators keep a backup and verify the upgrade on a populated database.

### Validation

Real-database concurrency tests use the existing Testcontainers PostgreSQL setup, such as `GithubContentRevisionConcurrencyIntegrationTest`. Each pair runs in both orders:

- accept vs. accept, cancel, recipient removal and recipient disabling;
- accept vs. promotion approval, and vs. creating new pending work;
- recovery accept vs. the original user rejoining;
- removal conversion vs. a pending handover.

Other tests:

- same-slug handover with update, download and delete aimed at the exact record;
- ambiguity errors for older clients;
- departed-user access denial on old and new records;
- expiry enforced at action time;
- upgrade on a populated database.

Results are reported as passed, failed and not run, separately from the proposed behavior.

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
