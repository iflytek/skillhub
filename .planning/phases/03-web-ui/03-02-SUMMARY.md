---
phase: 03-web-ui
plan: "02"
subsystem: backend-contract
tags: [spring, collections, visibility, reorder, security-tests]
requires:
  - phase: 03-web-ui
    plan: "01"
    provides: web route/forms contracts consuming collection detail/list APIs
provides:
  - D-09 aggregate hidden-members count for contributor authenticated reads
  - D-11 contributor-safe partial reorder merge in domain service
  - Regression tests for contributor/owner/public response shape and reorder behavior
affects: [03-03-web-detail, collection-api-contract]
tech-stack:
  added: []
  patterns:
    - contributor-only aggregate field without exposing hidden ids
    - stable merge of visible-subset reorder into full persisted membership order
key-files:
  modified:
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/collection/SkillCollectionResponse.java
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillCollectionPortalQueryAppService.java
    - server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/collection/SkillCollectionMembershipService.java
    - server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/SkillCollectionSecurityIT.java
    - server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/collection/SkillCollectionMembershipServiceTest.java
requirements-completed: [WEB-03, ROL-06, VIS-03]
completed: 2026-04-16
---

# Phase 3 Plan 2: Backend Contract Support For Collections UI

Реализованы backend-изменения для UX из фазы 3: non-leaking aggregate hidden count для contributor и partial reorder merge без передачи hidden skill id с клиента.

## Task commit

1. `6fc64170` — `feat(03-02): support contributor-safe reorder and hidden member aggregate`

## GitNexus impact (pre-edit)

- `SkillCollectionPortalQueryAppService` — risk `LOW`, d=1 callers: `SkillCollectionController`, `PublicSkillCollectionController`.
- `SkillCollectionMembershipService` — risk `LOW`, d=1 callers: `SkillCollectionReconciliationScheduler`, `SkillCollectionPortalCommandAppService`.
- `SkillCollectionResponse.java` (file) — risk `LOW`, d=1 importers: command/query services + portal/public controllers.
- `SkillCollectionSecurityIT`, `SkillCollectionMembershipServiceTest` — risk `LOW`.

`gitnexus detect_changes` после реализации показал `risk_level: high` из-за широкого охвата `listMine`/DTO flow, но все затронутые пути покрыты регрессионными тестами и full module test pass.

## Verification

- `server`: `mvn -q -pl skillhub-app,skillhub-domain test` (через локальный Maven binary + `maven.repo.local=C:\Users\ivkon\.m2\repository`) — passed.

## What changed

- В `SkillCollectionResponse` добавлено поле `additionalMembersHiddenFromActorCount`.
- В `SkillCollectionPortalQueryAppService` добавлен контролируемый расчёт aggregate:
  - для contributor на authenticated read возвращается скрытый count (total - visible),
  - для owner/admin/stranger/public — `0`.
- В `SkillCollectionMembershipService.reorderSkills` добавлен contributor path:
  - принимает только видимый subset,
  - проверяет соответствие видимому множеству,
  - сливает reordered visible элементы в full membership order со стабильным порядком hidden элементов.
- Расширены тесты:
  - `SkillCollectionSecurityIT` (contributor/owner/public count behavior),
  - `SkillCollectionMembershipServiceTest` (positive + reject case для contributor partial reorder).

## Self-check

- SUMMARY создан: `.planning/phases/03-web-ui/03-02-SUMMARY.md`
- План 03-02 закрыт одним task commit.
