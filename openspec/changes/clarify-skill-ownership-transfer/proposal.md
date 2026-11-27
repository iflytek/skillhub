## Status (2026-11-23)

This change covers TEAM Skill ownership, maintenance handover and member departure. The maintainers ruled on all eleven open questions on PR #1060 on 2026-11-17, confirmed REQ-ADDR-05 on 2026-11-18 and clarified two implementation rules on 2026-11-23. Their rulings are recorded under [Decisions](#decisions-2026-11-17) and in the specs. The specs set product rules. They do not say the work is built or that compatibility has been checked.

The parts already delivered for #974 (PR #975) stay in force, except where a requirement below says it replaces one of them.

## Why

A Skill has one personal owner. When that person leaves, or hands the work to someone else, there is no safe way to move the responsibility. Deleting and recreating loses the ID, versions, statistics and history. A direct owner swap breaks when the recipient already has a Skill with the same slug, and it mixes up ownership, maintenance and authorship.

## What changes

Three concepts are kept apart:

1. **Ownership:** a Skill is user-owned or namespace-owned. Namespace ownership is a real relationship, not a synthetic user.
2. **Maintenance:** a namespace-owned Skill has at most one maintainer, who must be an active member. It may have none for a while.
3. **Attribution:** original creators and version contributors stay recorded. Ownership or maintainer changes never rewrite them.

Changes:

- **Access rule (delivery 1):** a user who is no longer a member of a TEAM namespace loses internal access to its Skills, including Skills they own and older records. Public content stays public. This replaces the read-only exception of REQ-OWN-02 (confirmed by the maintainers).
- **Exact addressing and compatibility (delivery 2a):** an optional `skillId` selects an exact record on existing routes and in the CLI. Old slug-only requests keep resolving the way they do today, and a new maintenance relationship never changes which record they pick. A selection is asked for only when the target really is unclear.
- **Ownership model and handover (delivery 2b):** Skills can be namespace-owned with one maintainer. Every accepted handover gives the same result: the Skill becomes namespace-owned and the recipient becomes its maintainer. Scans and pending reviews do not block a handover.
- **Member removal and historical records (delivery 3):** access ends at once when a member is removed. Converting their Skills to namespace ownership runs afterwards. It is recorded, can be retried, may run in batches, and only touches the records listed at removal time. Existing data is not converted on upgrade; a separate, audited flow handles historical records.

## Capabilities

### New capabilities

- `skill-ownership-model`: user or namespace ownership, maintainer, attribution, global copies and authorization.
- `skill-exact-addressing`: exact-record selection, upload target selection and historical compatibility across Web, API and CLI.
- `skill-handover`: handover requests, their states and completion.
- `team-member-departure`: access after leaving, reviews after responsibility changes, conversion on removal, historical remediation and the download-link boundary.

### Modified capabilities

- `skill-owner-membership-access`: REQ-OWN-01, REQ-OWN-02, REQ-OWN-04, REQ-OWN-06 and REQ-OWN-07 are replaced in whole or in part. Each one has a note naming what replaces it.

## Scope

In scope: ordinary TEAM Skills (PRIVATE, NAMESPACE_ONLY and PUBLIC), including Skills in auto-provisioned TEAM namespaces, and their linked active global copies.

Out of scope: direct handover of GLOBAL Skills, system-owned and GitHub-sync-managed Skills, bulk or cross-namespace transfer, platform emergency takeover, multiple maintainers, Token-based handover, account deletion, namespace dissolution and a separate personal-asset lifecycle.

## Delivery

Linked PRs, in this order. Each PR builds on the previous one.

1. **TEAM access-rule fix.** No schema change.
2. **Ownership, maintenance and handover**, in two PRs:
   - **2a. Exact addressing and compatibility.** Starts with a list of every place in the code that looks up a Skill by slug or reads its owner, marked "must change" or "retest only". Also includes a matrix of historical data and old clients. Handover cases that depend on exact addressing stay off until this is validated.
   - **2b. Ownership model and handover workflow.**
3. **Conversion on new member removal and explicit historical remediation.** Reuses the transition services from delivery 2b.

## Impact

- **Database:** additive columns on `skill`, a handover request table, and conversion tables. The upgrade is non-destructive and leaves existing rows user-owned. Historical Skills are not renamed, recreated or renumbered.
- **Rollback boundary:** rolling back to older binaries is safe only while no namespace-owned Skill exists. After the first one exists, the supported rollback is turning the feature switches off on the current binaries. Rolling back to older binaries is not supported. `design.md` describes the checks.
- **API:** an optional `skillId` on existing routes, handover endpoints and new response fields. A selection-required error is returned only when the target is genuinely unclear. Generated OpenAPI types are updated.
- **CLI:** a `--skill-id` option. Old clients keep working where the target is clear. Existing installation records and later update and download calls are validated.
- **Behavior change:** departed users lose the internal access that #975 allowed. Download links already issued keep working until they expire (`SKILLHUB_STORAGE_S3_PRESIGN_EXPIRY`, default `PT10M`). Release notes and UI messages must say this accurately.

## Decisions (2026-11-17)

Maintainer rulings on the eleven open questions, as written into the specs.

1. **Uniform handover result.** Every accepted handover makes the Skill namespace-owned with the recipient as maintainer, whether or not there is a slug conflict. A later handover only changes the maintainer. The recipient's own same-slug Skill is not changed. (REQ-HO-06)
2. **Who starts a handover of a namespace-owned Skill.** The current eligible maintainer (voluntary), or a namespace OWNER or ADMIN (governance, reason required). The recipient accepts. Self-assignment needs one explicit confirmation and a reason. An OWNER or ADMIN cannot take a user-owned Skill from an active member. Handover is available in browser sessions only. (REQ-HO-01, REQ-HO-09)
3. **Scans and pending work.** They do not block handover. Scans continue against the exact version. Pending applications stay, and each later decision or publication re-checks current conditions. Member removal never waits. (REQ-HO-05, REQ-HO-11, REQ-DEP-02)
4. **Archived and hidden Skills.** Both can be handed over and keep their state. Handover does not unhide, restore, publish or promote anything, and it grants no right to do so. (REQ-HO-08, REQ-HO-12, REQ-HO-13)
5. **Global copies.** An active global copy follows its source's effective ownership and maintainer through the existing source link. It has no maintainer of its own. (REQ-OM-05, REQ-OM-09)
6. **Exact addressing.** Optional `skillId`, checked against the namespace and slug. Old requests keep their established resolution. A selection is required only when the target is genuinely unclear. Historical compatibility is a release prerequisite. (REQ-ADDR-02, -03, -04, -06, -07, -08)
7. **Upload target.** Creating a Skill and updating a particular Skill are separate. When the target is unclear, the Web UI offers a choice and the CLI requires a selector. The system never guesses. (REQ-ADDR-05)
8. **Reviews after a responsibility change.** The application keeps its submitter, material and history. It loses automatic-approval eligibility and goes to normal human review. No new "continue" step. (REQ-DEP-03, REQ-DEP-10)
9. **Conversion on removal.** Access ends at once. Conversion is observable, idempotent, retryable and may run in batches. Rejoining does not bypass a pending conversion. (REQ-DEP-02, REQ-DEP-08, REQ-DEP-09)
10. **Auto-provisioned namespaces.** No exclusion. They are TEAM namespaces and follow TEAM rules. (REQ-OM-06)
11. **Download links.** The configured presigned URL expiry (default 10 minutes) is the boundary. (REQ-DEP-07)

The maintainers also confirmed that REQ-DEP-01 replaces the departed-owner read-only exception from #975. They also said this does not authorize automatic conversion of historical ownership (REQ-DEP-05).

### Confirmed (2026-11-18)

- **Slug-only upload by a maintainer with no personal record.** User B has no user-owned Skill with slug `x` but maintains namespace-owned Skill 202 with slug `x`. A slug-only upload from B is refused with the selection-required error listing 202 as an eligible update target. It neither updates 202 nor creates a new Skill. An explicit update of 202 goes through the existing permission and publication checks. This case is part of the delivery 2a compatibility matrix. (REQ-ADDR-05)

### Clarifications (2026-11-23)

- **Old removal events do not override later assignments.** The temporary restriction during conversion is bound to the removal event and the responsibility and `ownership_revision` it recorded. Rejoining alone does not restore old rights. A later valid handover or governance assignment grants the rights of that new responsibility, and retrying the old item neither clears nor restricts it. (REQ-DEP-09, `design.md`)
- **Conversion approach kept, scale to be validated.** Membership deletion, the removal event and set-based registration of affected records commit in one transaction. Conversion, notifications and reindexing run after commit in bounded batches. Removal is not constant-time. Large-data validation of transaction duration, lock waits and rollback comes first; a snapshot mechanism is added only if the results require it. (REQ-DEP-02, REQ-DEP-09, `design.md`)

---

## Earlier record (superseded where noted)

> **Superseded parts (2026-11-17).** The text below is the #974 record and is kept unchanged. These parts no longer describe the product contract:
>
> - "交接功能仍暂缓" in 状态, the 范围复核 deferral and the handover items in 暂缓事项: handover is now in scope (`skill-handover`).
> - Q5 (recovery by namespace OWNER only, ADMIN excluded): still holds for user-owned Skills. For namespace-owned Skills, namespace OWNER or ADMIN may start governance reassignment (REQ-HO-01).
> - Q10 and the Q10/Q12 link (read-only access for a removed owner): replaced by REQ-DEP-01.
> - The first What Changes bullet (removed owner can view and download) and the state table after Q12: replaced by REQ-DEP-01 and REQ-DEP-02.
> - Q13 (no change to admin rights in this round): still holds for existing admin rights. Namespace governance of namespace-owned Skills is added by REQ-OM-04 and REQ-HO-01.


## 状态

用户已授权实施，由 Issue #974 跟踪，关联但不替代 PR #797 与 Issue #732 的完整协作需求。交接功能仍暂缓，永久注销与自助退出不在本期范围，不关闭原公开对象。目录名称沿用初始讨论，实际交付范围以本节及 `specs/skill-owner-membership-access/spec.md` 为准。

## What Changes

- 账号正常但已被移出 TEAM 空间的当前技能所有者，可以查看、下载自己的内容，不能凭所有者身份继续修改。
- 技能、地址、当前所有者、历史记录和已有申请不因移除成员自动删除、迁移、转让或撤回。
- 重新加入且仍是当前所有者时恢复正常维护权限；账号停用继续按既有认证规则拒绝，不使用所有者读取例外。
- 空间／平台管理员的既有治理权限、审核规则保持现状；本轮不重新定义管理员权限。
- Web 管理入口反映只读状态，服务端对所有相关写入口执行同一成员边界，覆盖有权限调用这些入口的 Session 与 Token／CLI。

## Capabilities

### New Capabilities

- `skill-owner-membership-access`: 技能所有者与当前空间成员关系共同决定的访问和维护边界。

### Modified Capabilities

现有审核、停用账号、成员移除、公开读取与管理员治理维持原有流程。本轮对所有者授权入口的行为收紧在新增能力中统一描述，不新增角色、开关或数据库业务约束。

## Impact

技能读取、文件下载、生命周期、发布、头像、可见范围、提升及相关应用层权限投影；现有客户端入口与服务端校验需一致。正常成员、管理员和公开读取路径需回归验证。不采用 PR #797 的全员覆盖代码，不引入技能转让 API。

## Why

现有跨所有者维护提案使用空间级开关授权所有成员更新他人技能。历史讨论反对该权限范围，也认为逐技能 Maintainer 增加过多配置。源码核查发现成员移除后的所有者读取与写入条件不一致，本期先统一该边界，技能交接暂缓。

## 历史讨论记录（不作为当前实施范围）

- Q1（2026-10-16）：本期一起解决“退出后只读＋明确转移技能所有者”，不能仅增加转让入口而保留退出后的写入路径。
- 退出不表示技能被删除或自动搬出原空间。
- Q2（2026-10-19）：正常转让由当前技能所有者发起；未及时交接时的补救流程另行明确，不隐含管理员可任意转让。
- Q3（2026-10-19）：接收人确认后转让生效；确认前技能所有者不变。
- Q4（2026-10-19）：技能所有者退出空间后完全只读，不能发起转让。主动交接须先重新加入空间，不为转让新增退出后写权限例外。
- Q5（2026-10-19）：当原所有者已退出空间或账号已停用时，由空间负责人（Namespace OWNER）受限发起接管，记录原因，由接收人确认后生效；普通空间 ADMIN 不因此取得接管权限。
- Q6（2026-10-19）：空间负责人可以将符合接管条件的技能转给自己，自行发起和确认，必须记录原因及审计。不额外强制引入第二位审批人。
- Q7（2026-10-19）：未完成接管前，原所有者重新成为有效空间成员且账号正常，接管条件不再满足，申请失效；确认时重新校验，不依据过期条件转移所有权。

## 范围复核

2026-10-19：用户确认“暂缓交接功能，先核查现有权限”。此前 Q1–Q7 作为历史讨论保留，不作为当前开发要求；不继续细化转让流程，不创建转让 API 或修改业务规则。

当前核查范围：成员退出后对自己技能的读写路径、普通成员与空间管理员实际权限、相关测试与文档的一致性。只读源码核查与运行验证分开报告；有具体问题再确定是否另行修复，不将先前建议自动视为安全契约。

用户后续澄清：需要先弄明白“个人注销、退出命名空间后，技能和权限要怎么处理”。将此作为当前澄清重点，不把拒绝 PR #797 的全员覆盖实现解释为不再解决原问题。待核实并区分退出登录、账号停用、永久注销、主动退出空间与被管理员移除；分别讨论内容保留、读取、维护、署名、凭证及恢复，不将这些操作合并成一个权限规则。

Q8：永久注销账号后，已发布且公开或团队可见的技能继续保留，不因注销自动转让或下架。此为未来注销场景的产品原则，不表示本期已经决定新增永久注销功能；私有内容、草稿及历史署名尚未定案。

### 账号及成员生命周期现状核查

main `2fe1a44059f51c1fa2e72ea3f1f3d59942e1e8be` 静态核查：

- 存在退出登录、管理员停用／启用账号，没有找到完整永久注销／删除账号入口。用户状态为 ACTIVE、PENDING、DISABLED、MERGED。
- 退出登录结束当前 Session，不删除账号或技能，也不等于撤销 API Token。
- 停用不删除技能、空间或成员关系；请求认证会检查账号状态，停用账号的 Session／Token 被拒绝。不是停用时批量销毁所有凭证；重新启用后，仍有效且未撤销的 Token 可能恢复可用。
- 存在管理员移除成员接口，未找到普通 MEMBER 自助退出接口。移除只删除成员关系，技能 owner 与空间归属不变。
- 空间 OWNER 不能直接移除；既有空间所有权转移只改变成员角色，不转让技能。
- 自动创建的个人空间在当前模型中也是 TEAM，不存在可直接依赖的独立 PERSONAL 删除生命周期。

主要证据：`SecurityConfig.java:170–179`、`UserStatus.java:3–5`、`AdminUserAppService.java:112–124`、`AuthContextFilter.java:68–118`、`ApiTokenAuthenticationFilter.java:79–90`、`NamespaceController.java:212–218`、`NamespaceMemberService.java:55–69,88–117`、`PersonalNamespaceProvisioningService.java:88–103`。本轮未运行测试，不能宣称永久注销功能已存在或已验证。

Q9：用户确认本期先理清既有停用账号／移除成员场景。不新增永久注销、自助退出、空间解散或所有权交接系统；Q8 保留为未来注销场景原则，不成为本期实施要求。

Q10：账号仍可正常使用、但已被移出空间的当前技能所有者，可以查看和下载自己的内容，包括私有内容、历史版本、草稿及被拒绝版本；禁止修改。停用账号不获得这项读取例外。未来转让后不再持有所有权的边界见 Q12，不将本条解释为永久访问授权。

Q11：停用或移除前已提交的待审核申请保留，由现有审核流程继续决定，不因停用／移除自动通过或自动撤回；受限用户不能继续修改申请。具体审核执行仍须遵守其他现有安全、状态和治理校验，不能将此原则解释为跳过这些校验。

Q12：用户确认未来技能正式转让后，前任所有者按普通访问权限判断；保留历史署名，但不因历史提交记录保留私有／草稿访问权。具体访问仍依赖当前成员身份、内容可见性及原有角色。此项仅定义边界，不扩大本期为所有权转让开发。

Q10 与 Q12 的衔接：没有转让时，原用户仍是当前技能所有者；账号正常但已被移出空间时保留自己技能的只读／下载权限，不得修改。账号停用则不能使用所有者只读例外。不能把成员移除等同于所有权转让，也不能把这项只读例外扩展为查看其他所有者的内部内容。

用户确认上述状态表：账号正常且仍是成员的所有者按正常权限维护；被移出但未转让时可读／下载自己的私有内容、草稿和历史版本，不能更新、删除、归档等；账号停用时不能以该账号访问，公开内容仍可匿名读取；重新加入且所有权未变时恢复正常维护权限。技能不因移除而删除、迁移或自动交给管理员；未交接期间不自动产生新维护人。现有管理员治理权限的本期处理边界尚待单独确认，不以本条暗中修改管理员授权。

Q13：用户确认本期只统一所有者权限，空间／平台管理员权限和既有审核规则保持现状。用户若另有平台管理员身份，其现有管理员授权仍适用；不凭技能所有者身份绕过成员边界，也不把本期变更升级为全角色权限重构。

Q13 已解决前述状态表中管理员边界的待确认项。后续实现和验证以本节及新增能力规范为准，历史建议中关于收紧管理员内容权限的内容不纳入本期。

## 暂缓事项

- 技能转让与异常接管 API、平台紧急恢复流程。
- 转让接收人的资格、拒绝、申请取消／失效及接管条件变化的实现。
- 转让时在途版本、头像、扫描、套件绑定及同名冲突的处理。
- 团队与全局双记录的转让关系。
- 永久注销、自助退出和空间解散。

当前所有者移除后的读取、写入、重新加入与已有申请规则已在能力规范明确；相应页面／API 与验收不是暂缓项，仅等待正式实施阶段。

## 当前事实与限制

基于 main `277f3be7fcb06b8d980246c67e6c2a355c510b38` 的只读源码核查，未作运行验证：

- 移除成员只删除成员关系，技能所有者和空间归属不变。
- 尚无完整的技能所有权转让业务入口；现有空间 OWNER 转让不是技能转让。
- PRIVATE／未发布技能的所有者读取路径与 NAMESPACE_ONLY 不一致，退出后并非统一可读。
- 普通上传校验成员身份，但部分生命周期与历史版本重新发布路径未统一限制退出后的所有者写入。
- 管理员现有生命周期和套件绑定发布权限不能直接等同于“只治理、不代传”的新方案。
- 同空间同 slug 的目标所有者草稿可能导致转让唯一键冲突。
- 再次提升的现有校验要求团队和全局技能 owner 相同，单边转让不能不处理该关联。

这些事实是后续设计输入，不表示所有现有行为都应保留，也不表示待确认项已经定案。

## 2026-10-19 权限核查与 PR #797 价值判断

核查 main `2fe1a44059f51c1fa2e72ea3f1f3d59942e1e8be`；PR #797 head `c8f1e79b41ab99a6e95c440f497193b9b6523bdb`。本轮只有源码、调用链、测试源码及公开 PR 信息核查，未运行测试或部署，不将潜在路径表述成已实测 HTTP 越权。

### 现有权限事实

| 操作 | 非空间成员、仍为技能所有者时的静态授权 |
| --- | --- |
| 普通 ZIP 发布新版 | 普通用户须仍为当前成员；SUPER_ADMIN 另有例外 |
| 已有版本重新发布 | 所有者授权后明确绕过成员检查 |
| 归档／恢复／删除版本／yank | 所有者授权不要求成员，但仍受状态及其他操作条件限制 |
| 提交审核／确认私有版本发布 | 所有者授权不要求成员，仍需满足版本状态 |
| 撤回审核 | 检查申请提交者，不额外检查成员身份 |
| 修改可见范围 | 所有者可进入管理路径，仍受待审、扫描和技能状态约束 |
| 部分头像所有者操作 | 按所有者定位技能，未额外检查成员身份 |
| 读取自己的技能 | PRIVATE／未发布内容按所有者放行；已发布 NAMESPACE_ONLY 要求当前成员，所有者无例外 |

主要证据（固定 main 中行号）：`SkillPublishService.java:379,395–405,460–465`、`SkillGovernanceService.java:353–361`、`SkillReviewSubmitService.java:179–188`、`ReviewService.java:360–370`、`SkillVisibilityService.java:81–86`、`SkillAvatarAppService.java:364–375,623–630,657–660`、`VisibilityChecker.java:25–32`。

生命周期控制器经 `GovernanceWorkflowAppService`、`SkillLifecycleAppService` 到领域服务的已核对调用链没有统一成员补检。Session 认证／CSRF 与 Token scope 仍有效，不能把成员边界不一致描述成未认证访问。现有部分单测以 owner＋空空间角色集允许生命周期操作，表明“退出后统一只读”是需要明确实施的规则调整，而非仅凭此次讨论就认定全部路径为漏洞。

普通兼容 CLI 发布未完成独立入口核查，不将 ZIP 路径结论泛化到全部客户端。个人空间在模型中也是 TEAM，不能凭名称推导特殊规则。

### 价值与方案比较

- 原问题有价值：单一所有者不可用会影响后续维护，但当前暂缓交接，尚无必须立即上线的新协作契约。
- PR 方案不采用：`canOverwriteOtherOwners()` 只要求空间开关开启且当前是成员，没有技能级授权或所有权交接。发布新版仍挂在原所有者记录上，作者、提交者与后续管理权继续分离；目标 PRIVATE 技能沿用私有路径，不创建普通公开／团队审核任务。不能仅因默认关闭或存在审核就认为授权范围足够窄。
- 最小方案是继续保留现有内容所有者隔离，不新增成员覆盖能力。它暂不解决交接，但符合本轮范围收敛决定，避免引入第二套维护模型。
- 所有权转移比全员覆盖更适合交接，但本轮明确暂缓；逐技能 Maintainer 更适合长期协作，投入和权限维护成本较高，也不纳入本期。
- PR 当前有合并冲突，这是次要工程状态，不是拒绝方案的核心理由；不应先花时间解冲突或补测再讨论模型。

建议：PR #797 按不采用当前方案收尾，Issue #732 保留为暂缓需求；现有退出权限不一致单独列出供后续确定是否修复，不在本轮自动改代码。公开关闭、评论或标签操作未在本轮执行。
