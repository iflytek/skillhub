---
phase: 02-application-api
plan: "01"
subsystem: api
tags: [spring, rest, collections, visibility, authorization]
requires:
  - phase: 01-domain-persistence
    provides: collection domain services, authorization policy, persistence model
provides:
  - Collection DTO contract package under skillhub-app
  - Portal query/command app services for collection workflows
  - Authenticated and public collection HTTP controllers
affects: [02-02-security, 03-web-ui, api-contracts]
tech-stack:
  added: []
  patterns: [thin controllers -> app services -> domain services, visibility-safe member filtering]
key-files:
  created:
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/collection/SkillCollectionCreateRequest.java
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillCollectionPortalQueryAppService.java
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillCollectionController.java
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PublicSkillCollectionController.java
  modified:
    - server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/collection/SkillCollectionResponse.java
    - server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/collection/SkillCollectionRepository.java
    - server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/collection/SkillCollectionContributorService.java
    - server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillCollectionJpaRepository.java
key-decisions:
  - "Expanded SkillCollectionResponse with visibility-filtered member list to satisfy VIS-03 in public and actor reads."
  - "Kept collection controllers on /api/web only because portal routes in repo do not enforce universal /api/v1 parity."
patterns-established:
  - "Collection REST handlers resolve acting user via request attributes and principal role checks."
  - "Public collection reads pass through query app service and SkillReadableForActorPort member filtering."
requirements-completed: [COL-01, COL-02, COL-03, COL-04, COL-05, COL-06, COL-07, ROL-01, ROL-02, ROL-03, ROL-04, ROL-05, ROL-06, ROL-07, ROL-08, VIS-01, VIS-02, VIS-03, INT-01, INT-02]
duration: 35min
completed: 2026-04-15
---

# Phase 2 Plan 1: Application API Summary

**Spring application services and REST controllers now expose collection CRUD, membership, contributor, and public visibility-filtered read flows over Phase 1 domain services.**

## Performance

- **Duration:** 35 min
- **Started:** 2026-04-15T06:40:00Z
- **Completed:** 2026-04-15T07:15:00Z
- **Tasks:** 4
- **Files modified:** 13

## Accomplishments
- Added full DTO package for collection requests/responses with validation.
- Implemented command/query app services that delegate to domain services and preserve role semantics.
- Added authenticated `/api/web` collection endpoints plus public owner+slug read endpoint.
- Applied member filtering via `SkillReadableForActorPort` to prevent private skill leakage in public views.

## Task Commits

1. **Task 1: Request/response DTOs under dto/collection** - `434ae7d3` (feat)
2. **Task 2: SkillCollectionPortalQueryAppService + SkillCollectionPortalCommandAppService** - `86ce32cd` (feat)
3. **Task 3: SkillCollectionController + PublicSkillCollectionController** - `0bc9566e` (feat)
4. **Task 4: Compile + smoke test slice** - no code changes (verification-only task)

## Files Created/Modified
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/collection/*` - request/response DTOs for collection API contracts.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillCollectionPortalCommandAppService.java` - command orchestration over domain services.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillCollectionPortalQueryAppService.java` - actor/public read logic with member visibility filtering.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillCollectionController.java` - authenticated CRUD/member/contributor HTTP endpoints.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PublicSkillCollectionController.java` - anonymous-safe public collection read endpoint.
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/collection/SkillCollectionRepository.java` - query methods required by app services.
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/collection/SkillCollectionContributorService.java` - contributor listing with authorization enforcement.

## Decisions Made
- Included filtered member payload inside `SkillCollectionResponse` so all query methods can safely return consistent visibility-aware results.
- Implemented contributor list authorization in domain service instead of controller-level checks to keep authorization authoritative in domain layer.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added missing repository/query methods required for list and public-read contracts**
- **Found during:** Task 2
- **Issue:** Phase 1 repository contracts lacked owner+slug and owner-or-contributor query paths needed by this plan.
- **Fix:** Extended collection and contributor repository interfaces and JPA adapters with required methods.
- **Files modified:** `SkillCollectionRepository`, `SkillCollectionContributorRepository`, `SkillCollectionJpaRepository`, `SkillCollectionContributorJpaRepository`
- **Verification:** `./mvnw -q -pl skillhub-app -DskipTests compile`
- **Committed in:** `86ce32cd`

---

**Total deviations:** 1 auto-fixed (Rule 2)
**Impact on plan:** Deviation was required to make planned API behavior executable without bypassing domain/repository boundaries.

## Known Stubs
None.

## Issues Encountered
- None blocking.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Collection API layer is in place for security policy registration in plan 02-02 and web integration in phase 03.
- Public endpoint auth policy still depends on explicit route policy updates planned in 02-02.

## Self-Check: PASSED

- Found file: `.planning/phases/02-application-api/02-01-SUMMARY.md`
- Found commit: `434ae7d3`
- Found commit: `86ce32cd`
- Found commit: `0bc9566e`
