## 1. Persistence and domain model

- [x] 1.1 Add Flyway migrations for `skill_suite`, `skill_suite_version`, and `skill_suite_version_member`, including per-type slug uniqueness, version uniqueness, version-level visibility, ordering, snapshot fields, indexes, and `ON DELETE SET NULL` member references.
- [x] 1.2 Implement Suite aggregate entities, statuses, repositories, package boundaries, and domain invariants for 100-member limits, exact versions, duplicate detection, and Entry Skill membership.
- [x] 1.3 Add focused repository and domain tests for same-slug Skill/Suite coexistence, immutable published versions, hard-deleted member snapshots, and latest-version recalculation.
- [x] 1.4 Implement computed Suite availability and blocking reasons without adding degraded to the persisted SuiteVersion lifecycle enum.

## 2. Lifecycle, authorization, and review

- [x] 2.1 Implement Suite draft, submit, approve, reject, direct-private-publish, yank, hide, restore, archive, and delete workflows without Member lifecycle side effects.
- [x] 2.2 Generalize review tasks to typed subjects, backfill existing rows as `SKILL_VERSION`, and preserve all existing Skill review behavior and queries.
- [x] 2.3 Reuse Namespace/platform authorization rules and add Suite-specific audit events for every material lifecycle action.
- [x] 2.4 Add tests covering roles, self-review rules, member eligibility revalidation at submit/approve/publish, visibility compatibility, namespace freeze/archive, and non-cascading governance.
- [x] 2.5 Add a rollout compatibility gate so Suite review writes are enabled only after all active application versions support typed review subjects.
- [x] 2.6 Implement rejected-to-draft resubmission with immutable review rounds, and prevent published/yanked version edits.

## 3. Server API and search

- [x] 3.1 Add transport-only Suite controllers and application services for management, version history, review actions, detail, and typed resolution of install plans.
- [x] 3.2 Add a typed resource discovery projection with `resourceType` and Suite metadata while keeping the existing Skill search endpoint Skill-only.
- [x] 3.3 Return ordered Member snapshots, mandatory Entry Skill, availability, and degraded reasons without N+1 member resolution.
- [x] 3.4 Regenerate `web/src/api/generated/schema.d.ts` with `make generate-api` and run the OpenAPI drift check.
- [x] 3.5 Record idempotent Suite-plan statistics with a client retry key and server operation ID; keep Member counters on actual existing download requests.
- [x] 3.6 Add a server-filtered Member candidate query scoped by caller access, Suite Namespace, target visibility, current installability, and exact versions.

## 4. Web experience

- [x] 4.1 Add typed Skill/Suite search cards and independent Suite list/detail/version routes.
- [x] 4.2 Add Suite creation and draft editing with the server-filtered Member picker, exact published versions, ordering, visibility, and mandatory Entry Skill.
- [x] 4.3 Extend the review center with typed Suite review details and ensure existing Skill review actions remain unchanged.
- [x] 4.4 Add install instructions using `skillhub suite install`, degraded-member explanations, and responsive/error/loading/empty states.
- [x] 4.5 Default Member selection to the current installable version, display the pinned exact version, and provide an explicit version-diff update action for drafts.
- [x] 4.6 Complete Web management for new versions, rejected-version reopen, yank, hide/restore, archive/unarchive, and guarded deletion using Server-derived capabilities.
- [x] 4.7 Add a versioned Markdown overview and browsable Member Skill cards with display metadata, pinned versions, Entry markers, and tombstone handling.
- [x] 4.8 Show privacy-filtered current Suite references on an Entry Skill detail page while preserving standalone Skill installation.

## 5. CLI and local lifecycle

- [x] 5.1 Add `skillhub suite install/check/upgrade/remove` and a typed Suite resolver without changing `skillhub install` resolution.
- [x] 5.2 Extend the existing staged installer to preflight, download, fingerprint-check, lock, commit, and roll back all Members and selected Agent targets as one operation.
- [x] 5.3 Extend inventory with backward-compatible Suite snapshots and multi-source `installedBy` provenance.
- [x] 5.4 Implement safe Suite removal that preserves direct-installed, shared, unknown-source, or locally modified Member directories.
- [x] 5.5 Add CLI tests for same-slug Skill/Suite, missing permissions, unavailable members, checksum failure, disk/rename failure, incomplete rollback reporting, shared members, legacy inventory, and multi-Agent targets.
- [ ] 5.6 Add Server capability detection and old-Server/new-CLI plus new-Server/old-CLI compatibility tests.

## 6. Documentation and validation

- [x] 6.1 Document the reserved `suite.yaml` interchange format, Suite/Skill terminology, typed coordinates, lifecycle boundaries, CLI commands, compatibility, and operator limits.
- [x] 6.2 Run targeted backend tests, `make test-backend-app`, frontend unit/type/lint checks, CLI tests/build, and OpenAPI drift validation.
- [x] 6.3 Build exact-SHA local Server/Web images and run authenticated Compose smoke tests for ordinary Skill and Suite flows.
- [x] 6.4 Execute the Server/Web OpenSpec scenario matrix, including normal flow, same-slug compatibility, lifecycle independence, degraded members, authorization, review-history retention, transactional rollback, rolling review migration, and non-cascading deletion.
- [ ] 6.5 Execute the deferred CLI scenario matrix, including atomic failure/recovery, upgrade, removal, legacy inventory, and old/new Server/CLI combinations.
- [ ] 6.6 Complete independent implementation review, manual Web retest instructions, privacy/readiness checks, and the Chinese merge-readiness report before requesting merge authorization. Additional CLI compatibility work remains deferred under 5.6 and 6.5.
