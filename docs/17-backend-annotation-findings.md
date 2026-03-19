# Backend Structure Findings During Annotation Pass

This document records architecture and structure issues that became consistently visible while enriching backend comments. The goal is to preserve concrete observations discovered during code reading, not to propose a full redesign.

## Status Update (2026-03-19)

This document was re-checked after the refactor branch work for findings 1, 2, and 4.

- Finding 1 is now handled in code.
- Finding 2 is partially handled in code.
- Finding 4 is now handled in code.

Validation completed on the standard regression path:

- `make test`
- backend Maven tests: `208` passed
- frontend Vitest tests: `61` passed

Double-check notes:

- The admin-user refactor removed an overlapping, unused application service rather than changing the controller-facing workflow owner.
- The namespace and skill-lifecycle refactors moved orchestration out of controllers, but preserved the same downstream domain-service calls, request parameters, audit fields, response message keys, and mutation response shapes.
- The security refactor centralized route metadata into one registry, but preserved the same route authorization rules, API-token scope behavior, and CSRF-ignore behavior.
- `AuthContextFilter` is now scoped to API paths when projecting request attributes. This narrows unnecessary work on non-API requests, but it does not change existing business behavior because `userId` and `userNsRoles` consumers are API-side controllers and interceptors.

## 1. Admin user management is split across overlapping application services

Status: handled on branch `docs/backend-annotation-findings-discussion`

Observed files:

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AdminUserAppService.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AdminUserManagementService.java`

Why this stands out:

- Both services sit in the application layer and are named as if they own the same capability.
- The naming does not make the responsibility boundary obvious to a reader.
- This increases the chance that new admin-user use cases get placed inconsistently.

Suggested direction:

- Either consolidate them into one application service, or split them with an explicit boundary such as query vs. command, or account governance vs. account operations.

Current state:

- `AdminUserManagementService` has been removed.
- `UserManagementController` continues to use `AdminUserAppService` as the single application-service entry point.
- Behavior review found no business-logic drift here because the deleted service had no active controller call path.

## 2. Several controllers still perform orchestration that belongs in application services

Status: partially handled on branch `docs/backend-annotation-findings-discussion`

Observed files:

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ReviewController.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillLifecycleController.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/compat/ClawHubCompatController.java`

Why this stands out:

- Some controllers coordinate multiple repositories, domain services, request-derived identities, and response assembly in one place.
- The controller layer is therefore carrying request translation and business workflow orchestration at the same time.
- This makes endpoint behavior harder to reuse, test, and document consistently.

Suggested direction:

- Move multi-step orchestration into dedicated application services and keep controllers focused on transport concerns.

Current state:

- `NamespaceController` has been slimmed down by moving orchestration into `NamespacePortalQueryAppService` and `NamespacePortalCommandAppService`.
- `SkillLifecycleController` has been slimmed down by moving orchestration into `SkillLifecycleAppService`.
- This branch preserved the original domain-service calls and response contracts for the refactored endpoints.
- `ReviewController`, `PromotionController`, and `ClawHubCompatController` still exhibit the same structural issue and remain future work.

## 3. Compatibility endpoints are tightly coupled to canonical domain and repository internals

Observed files:

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/compat/ClawHubCompatController.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/compat/ClawHubRegistryFacade.java`

Why this stands out:

- The compatibility layer pulls from repositories, domain services, and DTO-mapping concerns at the same time.
- The layer is useful, but it is not isolated enough to act as a clean anti-corruption boundary.
- Changes in canonical read models or publish flows are more likely to leak into compatibility code.

Suggested direction:

- Treat compatibility support as a dedicated adapter layer with narrower upstream contracts and fewer direct repository dependencies.

## 4. Security route policy is spread across configuration and implementation classes

Status: handled on branch `docs/backend-annotation-findings-discussion`

Observed files:

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/SecurityConfig.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/token/ApiTokenScopeService.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/filter/AuthContextFilter.java`

Why this stands out:

- Route access rules, token-scope rules, and request-context projection are all related to request authorization, but they are not expressed from one central policy model.
- A reader has to jump across modules to reconstruct how one API route is actually protected.

Suggested direction:

- Centralize route policy metadata or at least define one authoritative mapping between path patterns, authentication modes, and scope requirements.

Current state:

- Route metadata is now centralized in `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java`.
- `SecurityConfig`, `ApiTokenScopeService`, and `AuthContextFilter` now depend on that shared registry instead of maintaining separate route lists.
- Double-check review confirmed that the refactor preserved the previous access model while removing duplication.

## 5. Governance behavior is distributed across multiple services without one clear workflow owner

Observed files:

- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceGovernanceService.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillGovernanceService.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java`

Why this stands out:

- Governance rules are present in the right domain areas, but the end-to-end moderation and publishing workflow is distributed.
- Readers need to reconstruct lifecycle rules by navigating several services and controllers.

Suggested direction:

- Keep the domain split, but introduce a clearer workflow owner or workflow-facing facade for governance use cases.

## 6. Search-related read paths are split in a way that is hard to follow at first glance

Observed files:

- `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/postgres/PostgresFullTextQueryService.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java`

Why this stands out:

- The codebase has a sensible separation between search, application assembly, and canonical detail reads, but the naming alone does not make their responsibilities obvious.
- New contributors may need several passes to understand which service is the authoritative entry point for each read scenario.

Suggested direction:

- Clarify the boundary in naming or package-level docs, especially around "search result assembly" vs. "authoritative skill detail query."

## 7. Event-driven counter maintenance is useful but not yet modeled as a distinct projection concern

Observed files:

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/SkillStarEventListener.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/SkillRatingEventListener.java`

Why this stands out:

- Listeners are maintaining derived counters, which is a legitimate pattern.
- The projection/update responsibility is implicit rather than explicitly named as a read-model maintenance concern.

Suggested direction:

- Consider naming this area more explicitly as projection maintenance or read-model synchronization if the pattern continues to grow.

## 8. Exception modeling is duplicated across application, domain, and auth layers

Observed files:

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/exception/LocalizedException.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/shared/exception/LocalizedDomainException.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/exception/AuthFlowException.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/exception/GlobalExceptionHandler.java`

Why this stands out:

- The codebase uses localized error codes consistently, which is good, but several layers define parallel exception abstractions with overlapping semantics.
- The global exception handler then has to understand each branch separately.
- This makes it harder to tell whether a new business error belongs to the app layer, the auth layer, or the shared domain exception model.

Suggested direction:

- Keep layer-specific exception types only where they represent a real boundary, and consider converging on a smaller shared contract for localized API-facing errors.

## 9. Repository and read-model access patterns are mixed across layers

Observed files:

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/AdminUserSearchRepository.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/*/*Repository.java`
- `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/*`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/compat/ClawHubCompatController.java`

Why this stands out:

- Some flows use domain repository ports, some use infra JPA repositories, and some app-layer read logic uses `EntityManager` directly.
- This is not wrong in itself, but the conventions are not explicit, so contributors have to infer when bypassing the domain port layer is acceptable.
- The mixed style increases the chance that query behavior and write behavior evolve under different architectural rules.

Suggested direction:

- Define explicit rules for when a use case should depend on domain repository ports, dedicated query repositories, or direct persistence adapters.

## 10. OAuth login behavior is decomposed into many small classes without one visible flow owner

Observed files:

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/SkillHubOAuth2AuthorizationRequestResolver.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/CustomOAuth2UserService.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/GitHubClaimsExtractor.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuth2LoginSuccessHandler.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuth2LoginFailureHandler.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/AccessPolicyFactory.java`

Why this stands out:

- The current decomposition is modular, but understanding one OAuth login request still requires following state across request resolution, provider-specific claim extraction, access-policy evaluation, account provisioning, and redirect handling.
- The extension points are good, yet the absence of one flow-oriented facade or documented orchestration path increases onboarding cost.

Suggested direction:

- Keep the provider-specific strategy types, but consider a clearer flow owner or a compact architecture note that names the stages of the OAuth pipeline.

## 11. Some domain repository ports leak Spring Data pagination types

Observed files:

- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillRepository.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskRepository.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/audit/AuditLogQueryService.java`

Why this stands out:

- Several domain-facing repository contracts use `Page` and `Pageable` directly.
- This makes the domain boundary more dependent on Spring Data semantics than on a framework-neutral query model.
- It is workable, but it weakens the separation between domain contracts and persistence tooling.

Suggested direction:

- Either accept Spring Data as an intentional part of the domain boundary and document that choice, or introduce domain-oriented page/query abstractions where long-term isolation matters.

## 12. The auth module follows a more direct JPA style than the business-domain modules

Observed files:

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/repository/ApiTokenRepository.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/repository/IdentityBindingRepository.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/repository/RoleRepository.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillRepository.java`
- `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/JpaSkillRepositoryAdapter.java`

Why this stands out:

- The auth area usually talks directly to Spring Data JPA repositories over auth entities.
- The business-domain area more often exposes domain repository ports and implements them through infra adapters.
- Both styles are valid, but using them side by side without an explicit rationale makes the overall architecture feel uneven.

Suggested direction:

- Decide whether auth is intentionally allowed to stay as a more direct persistence-oriented module, and document that distinction so contributors know which style to apply in new code.

## 13. Many domain objects double as persistence entities instead of being isolated from JPA concerns

Observed files:

- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/Skill.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/Namespace.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTask.java`
- `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillJpaRepository.java`
- `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceMemberJpaRepository.java`

Why this stands out:

- The codebase often uses the same classes as both domain models and JPA persistence entities.
- This keeps implementation compact, but it also means persistence annotations, lazy-loading behavior, and storage-driven shape decisions can leak into domain modeling concerns.
- Combined with the repository-style differences already noted above, the codebase can feel partly domain-driven and partly persistence-driven depending on the module.

Suggested direction:

- If this is an intentional tradeoff, document it clearly as the project's default. Otherwise, consider introducing stronger separation only in areas where persistence concerns are starting to distort domain logic.

## Priority Recommendation

If only a small amount of structural cleanup is feasible, the highest-value items are:

1. Reduce controller orchestration by introducing a few focused application services.
2. Clarify the admin-user service boundary.
3. Centralize security route policy so access behavior is easier to reason about.
