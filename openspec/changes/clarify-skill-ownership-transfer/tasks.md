## 0. Contract (2026-11-16)

- [x] Record the ownership, maintenance and handover contract from the PR #1060 discussion.
- [ ] Settle the open questions in `proposal.md`.

## 1. Delivery 1: TEAM access-rule fix

- [ ] Require current membership for internal reads by the owner (REQ-DEP-01), for sessions and Tokens.
- [ ] Update the read-only UI message and add a release note on the behavior change.
- [ ] Tests: departed owner denied on old and new records; public content still readable; admin paths unchanged.

## 2. Delivery 2: ownership model, exact addressing and handover

- [ ] Migration: `ownership_type`, nullable `owner_id`, `maintainer_id`, `ownership_revision`, and the `skill_handover_request` table.
- [ ] Responsible-user authorization helper; move all owner checks to it (REQ-OM-04).
- [ ] Exact addressing in the resolver, all slug routes, the API selector and the CLI option (REQ-ADDR-02, REQ-ADDR-03).
- [ ] Handover service: start, accept, reject, cancel, expire, invalidate (REQ-HO-01 to REQ-HO-06).
- [ ] Lock the source Skill in promotion approval and when pending work is created.
- [ ] Notifications (`OWNERSHIP`) and audit actions (REQ-HO-07).
- [ ] UI: handover dialog with recipient search, My Skills requests tab, Skill detail panel.
- [ ] Enable same-slug handover only after exact addressing is complete (REQ-ADDR-04).
- [ ] Tests: unit, controller, real-database concurrency, E2E and populated-database upgrade.

## 3. Delivery 3: removal conversion and historical remediation

- [ ] Convert Skills and clear maintainer assignments on new member removal (REQ-DEP-02).
- [ ] Re-check submitter authority when reviews are decided; no automatic approval for removed submitters (REQ-DEP-03).
- [ ] Remediation list and actions for historical records (REQ-DEP-05).
- [ ] Remove-member dialog, namespace "no maintainer" list, and messages for old links (REQ-DEP-06).
- [ ] Tests: removal not blocked by pending work, no repeat conversion, audit operator, historical access denial.

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
