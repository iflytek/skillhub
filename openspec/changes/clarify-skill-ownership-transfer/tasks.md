## 0. Contract

- [x] Record the ownership, maintenance and handover contract from the PR #1060 discussion.
- [x] Settle the open questions (maintainer decisions, 2026-11-17).
- [x] Maintainers confirm the slug-only upload case (REQ-ADDR-05, confirmed 2026-11-18).
- [x] DCO and CLA checks pass on the contract PR.
- [x] Apply the 2026-11-23 clarifications: event-bound conversion restriction, set-based registration and scale validation.

## 1. Delivery 1: TEAM access-rule fix

- [ ] Require current membership for internal reads by the owner (REQ-DEP-01), for sessions and Tokens, including auto-provisioned namespaces (REQ-OM-06).
- [ ] Replace the read-only UI message; release note on the behavior change and the download-link boundary (REQ-DEP-07).
- [ ] Tests: departed owner denied on old and new records; public content still readable; admin paths unchanged.

## 2a. Delivery 2 prerequisite: exact addressing and compatibility

- [ ] Call-site inventory: every slug lookup and owner read, marked "must change" or "retest only".
- [ ] Compatibility matrix for historical data and legacy clients, including the REQ-ADDR-05 case (maintainer of 202, no personal record, slug-only upload: selection-required error listing 202, nothing updated or created).
- [ ] Additive schema migration; no conversion.
- [ ] Responsibility resolver, including global copies through `source_skill_id` (REQ-OM-04, REQ-OM-05, REQ-OM-07 to REQ-OM-09); migrate every "must change" owner read.
- [ ] Extend `SkillSlugResolutionService` with optional `skillId` and the selection-required error (REQ-ADDR-02, REQ-ADDR-03, REQ-ADDR-07, REQ-ADDR-08).
- [ ] Upload target selection: create vs. update (REQ-ADDR-05).
- [ ] Web: carry `skillId` through routes, queries and mutations. CLI: `--skill-id` and install-record use.
- [ ] Regenerate OpenAPI types and check consumers.
- [ ] Validate the compatibility matrix (REQ-ADDR-06), with maintainer help for populated data and old clients.

## 2b. Delivery 2: ownership model and handover

- [ ] Handover service: start, accept, reject, cancel, expire, invalidate (REQ-HO-01 to REQ-HO-04, REQ-HO-06, REQ-HO-09, REQ-HO-10).
- [ ] No pending-work blocking; lock the source Skill and compare `ownership_revision` in review decisions, promotion approval and auto-approval (REQ-HO-05, REQ-HO-11, REQ-DEP-03, REQ-DEP-10).
- [ ] Archived and hidden Skills keep their state; no unhide path (REQ-HO-08, REQ-HO-12, REQ-HO-13).
- [ ] Notifications (`OWNERSHIP`) and audit actions (REQ-HO-07).
- [ ] UI: handover dialog with recipient search, My Skills requests tab, Skill detail panel.
- [ ] Keep same-slug handover cases off until 2a validation passes (REQ-ADDR-04).
- [ ] Feature switch, rollback-boundary check and its validation.
- [ ] Tests: unit, controller, real-database concurrency, E2E and populated-database upgrade.

## 3. Delivery 3: removal conversion and historical remediation

- [ ] Record the removal event and register affected items with a set-based insert in the removal transaction; end access at commit (REQ-DEP-01, REQ-DEP-02, REQ-DEP-08).
- [ ] Batched, idempotent, retryable conversion worker with progress shown in namespace management (REQ-DEP-09).
- [ ] Restriction bound to the removal event: a pending item restricts only its recorded responsibility while the recorded `ownership_revision` is current (REQ-DEP-09).
- [ ] Large-data validation: removal-transaction duration, lock waits and rollback on failure. Add a snapshot mechanism only if the results require it.
- [ ] Remediation list and explicit audited actions for historical records (REQ-DEP-05).
- [ ] Remove-member dialog, "no maintainer" list and messages for old links (REQ-DEP-06).
- [ ] Tests: removal not blocked by pending work, retry after partial failure, rejoin while incomplete, rejoin then valid reassignment then retry of the old item, source/copy consistency, audit operator, historical access denial.

---

## Earlier tasks for #974 (unchanged)

## 1. 实施前收敛

- [x] 刷新目标分支，确认当前需求与实施范围，用户已授权实施并创建 Issue #974。
- [ ] 基于冻结源码补齐各客户端与套件绑定入口的权限追踪，映射 REQ-OWN-01 至 REQ-OWN-07。

## 2. 服务端与界面

- [x] 统一当前所有者读取与成员维护权限，保留管理员独立授权。
- [x] 修正移除后写入口及旧旁路，补齐所有者列表、详情和受控下载读取。
- [x] 保留已有审核申请与现有账号停用／恢复行为。
- [x] 管理页面明确只读状态；如有契约变更，同步 OpenAPI 与生成类型。
- [x] 更新现有领域模型和用户文档中过时的成员移除／所有者权限说明，不新增转让或接管功能。

## 3. 验证和交付

- [ ] 完成关键阶段独立代码审查，并按正式流程冻结验证提交。
- [ ] 覆盖正常成员、移除后可读不可写、停用、重新加入、现有审核、管理员和跨空间回归。
- [ ] 对改动入口执行必要源码检查及固定提交运行／浏览器验收，不以单测替代运行证据。
- [ ] 汇总实际结果、人工复测、未验证项及 PR #797／Issue #732 的建议；未经授权不关闭或评论公开对象。
