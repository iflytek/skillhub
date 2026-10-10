# Namespace Governance Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the namespace governance lifecycle end-to-end: team namespace freeze/archive/restore, immutable `@global`, split management read models, state-aware backend policies, and dashboard interactions.

**Architecture:** Extend the existing namespace domain with a dedicated governance service and a shared access-policy helper instead of overloading `NamespaceService`. Keep public and management reads separate by adding `/me/namespaces`, then thread namespace status rules through publish/review/promotion/query/search and surface them in the React dashboard with role-aware controls.

**Tech Stack:** Spring Boot 3.x, Spring Data JPA, Spring Security, JUnit 5, Mockito, React 19, TypeScript, TanStack Query, TanStack Router, pnpm

---

**Spec:** `docs/superpowers/specs/2026-03-16-namespace-governance-design.md`

## File Structure Mapping

### Backend domain and portal

- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceGovernanceService.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceAccessPolicy.java`
- Create: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceGovernanceServiceTest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceLifecycleRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/MyNamespaceResponse.java`
- Create: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/NamespacePortalControllerTest.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/Namespace.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceRepository.java`
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceJpaRepository.java`
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java`
- Modify: `server/skillhub-app/src/main/resources/messages.properties`
- Modify: `server/skillhub-app/src/main/resources/messages_zh.properties`

### Cross-module state enforcement

- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java`
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java`
- Modify: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/ReviewPortalControllerTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/PromotionPortalControllerTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillControllerTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java`

### Frontend dashboard

- Modify: `web/src/api/types.ts`
- Modify: `web/src/api/client.ts`
- Modify: `web/src/shared/hooks/use-skill-queries.ts`
- Modify: `web/src/pages/dashboard/my-namespaces.tsx`
- Modify: `web/src/pages/dashboard/namespace-members.tsx`
- Modify: `web/src/pages/dashboard/namespace-reviews.tsx`
- Modify: `web/src/features/namespace/namespace-header.tsx`
- Modify: `web/src/i18n/locales/zh.json`
- Modify: `web/src/i18n/locales/en.json`

## Chunk 1: Namespace Governance Backend

### Task 1: Add lifecycle policy and immutable-global guard

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceGovernanceService.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceAccessPolicy.java`
- Create: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceGovernanceServiceTest.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/Namespace.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberService.java`
- Modify: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceServiceTest.java`
- Modify: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberServiceTest.java`

- [ ] **Step 1: Write the failing domain tests**

```java
@Test
void freezeNamespace_allowsAdminOnActiveTeamNamespace() {
    Namespace namespace = namespace("team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
    when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
    when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
            .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));

    Namespace updated = governanceService.freezeNamespace("team-a", "admin-1", null, null, null);

    assertEquals(NamespaceStatus.FROZEN, updated.getStatus());
}

@Test
void archiveNamespace_rejectsAdminAndAllowsOnlyOwner() { ... }

@Test
void updateNamespace_rejectsFrozenNamespace() { ... }

@Test
void addMember_rejectsArchivedNamespace() { ... }
```

- [ ] **Step 2: Run the domain tests to verify they fail**

Run: `cd server && ./mvnw -pl skillhub-domain -Dtest=NamespaceGovernanceServiceTest,NamespaceServiceTest,NamespaceMemberServiceTest test`

Expected: FAIL because `NamespaceGovernanceService`, namespace status setters, and read-only guards do not exist yet.

- [ ] **Step 3: Implement the lifecycle policy**

```java
public final class NamespaceAccessPolicy {

    public boolean isSystemImmutable(Namespace namespace) {
        return namespace.getType() == NamespaceType.GLOBAL;
    }

    public boolean canMutateSettings(Namespace namespace) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.ACTIVE;
    }

    public boolean canArchive(Namespace namespace, NamespaceRole role) {
        return namespace.getType() == NamespaceType.TEAM
                && role == NamespaceRole.OWNER
                && namespace.getStatus() != NamespaceStatus.ARCHIVED;
    }
}
```

```java
public Namespace freezeNamespace(String slug, String actorUserId, String requestId, String clientIp, String userAgent) {
    Namespace namespace = loadMutableNamespace(slug);
    NamespaceRole role = requireRole(namespace.getId(), actorUserId);
    if (role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
        throw new DomainForbiddenException("error.namespace.lifecycle.freeze.forbidden");
    }
    if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
        throw new DomainBadRequestException("error.namespace.state.transition.invalid");
    }
    namespace.setStatus(NamespaceStatus.FROZEN);
    return namespaceRepository.save(namespace);
}
```

- [ ] **Step 4: Re-run the domain tests and keep them green**

Run: `cd server && ./mvnw -pl skillhub-domain -Dtest=NamespaceGovernanceServiceTest,NamespaceServiceTest,NamespaceMemberServiceTest test`

Expected: PASS for lifecycle transitions, immutable `@global`, and read-only enforcement on settings/member operations.

- [ ] **Step 5: Commit the domain governance changes**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/namespace
git commit -m "feat: add namespace lifecycle governance"
git push origin feature/project-namespace
```

### Task 2: Expose management read model and lifecycle APIs

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceLifecycleRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/MyNamespaceResponse.java`
- Create: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/NamespacePortalControllerTest.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceRepository.java`
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceJpaRepository.java`
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java`
- Modify: `server/skillhub-app/src/main/resources/messages.properties`
- Modify: `server/skillhub-app/src/main/resources/messages_zh.properties`

- [ ] **Step 1: Write the failing portal tests**

```java
@Test
void listMyNamespaces_returnsFrozenAndArchivedNamespacesWithCurrentRole() throws Exception {
    mockMvc.perform(get("/api/v1/me/namespaces").with(auth("owner-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("ARCHIVED"))
            .andExpect(jsonPath("$.data[0].currentUserRole").value("OWNER"));
}

@Test
void archiveNamespace_returnsUpdatedNamespace() throws Exception {
    mockMvc.perform(post("/api/v1/namespaces/team-a/archive")
                    .with(csrf())
                    .with(auth("owner-1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"cleanup\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
}
```

- [ ] **Step 2: Run the portal tests to verify they fail**

Run: `cd server && ./mvnw -pl skillhub-app -Dtest=NamespacePortalControllerTest test`

Expected: FAIL because `/me/namespaces`, lifecycle endpoints, and management DTOs do not exist.

- [ ] **Step 3: Implement controller and DTO support**

```java
public record MyNamespaceResponse(
        Long id,
        String slug,
        String displayName,
        NamespaceStatus status,
        NamespaceType type,
        NamespaceRole currentUserRole,
        boolean immutable,
        boolean canFreeze,
        boolean canArchive,
        boolean canRestore
) {}
```

```java
@GetMapping("/me/namespaces")
public ApiResponse<List<MyNamespaceResponse>> listMyNamespaces(
        @RequestAttribute("userId") String userId,
        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
    return ok("response.success.read",
            namespaceService.listMyNamespaces(userId, userNsRoles != null ? userNsRoles : Map.of()));
}
```

- [ ] **Step 4: Re-run the portal tests**

Run: `cd server && ./mvnw -pl skillhub-app -Dtest=NamespacePortalControllerTest test`

Expected: PASS with `currentUserRole`, lifecycle booleans, and updated namespace payloads serialized correctly.

- [ ] **Step 5: Commit the portal API changes**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/dto \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/NamespacePortalControllerTest.java \
        server/skillhub-app/src/main/resources/messages.properties \
        server/skillhub-app/src/main/resources/messages_zh.properties \
        server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceJpaRepository.java \
        server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceRepository.java
git commit -m "feat: add namespace management endpoints"
git push origin feature/project-namespace
```

## Chunk 2: State Enforcement Across Publish, Review, Promotion, and Public Reads

### Task 3: Block write workflows when namespace is not ACTIVE

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java`
- Modify: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/ReviewPortalControllerTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/PromotionPortalControllerTest.java`

- [ ] **Step 1: Add failing tests for frozen and archived namespaces**

```java
@Test
void publishFromEntries_rejectsFrozenNamespace() { ... }

@Test
void submitReview_rejectsArchivedNamespace() throws Exception { ... }

@Test
void submitPromotion_rejectsFrozenNamespace() throws Exception { ... }
```

- [ ] **Step 2: Run the workflow tests to confirm the gap**

Run: `cd server && ./mvnw -pl skillhub-domain -Dtest=SkillPublishServiceTest test && ./mvnw -pl skillhub-app -Dtest=ReviewPortalControllerTest,PromotionPortalControllerTest test`

Expected: FAIL because the publish/review/promotion flows currently ignore namespace lifecycle state.

- [ ] **Step 3: Implement the shared ACTIVE-state guard**

```java
private void assertNamespaceActive(Namespace namespace, String messageKey) {
    if (namespace.getStatus() == NamespaceStatus.FROZEN) {
        throw new DomainBadRequestException("error.namespace.frozen", namespace.getSlug());
    }
    if (namespace.getStatus() == NamespaceStatus.ARCHIVED) {
        throw new DomainBadRequestException("error.namespace.archived", namespace.getSlug());
    }
}
```

Apply it before:
- publish package acceptance
- review submit/approve/reject/withdraw writes
- promotion submit writes

- [ ] **Step 4: Re-run the workflow tests**

Run: `cd server && ./mvnw -pl skillhub-domain -Dtest=SkillPublishServiceTest test && ./mvnw -pl skillhub-app -Dtest=ReviewPortalControllerTest,PromotionPortalControllerTest test`

Expected: PASS with stable error envelopes for frozen and archived namespaces.

- [ ] **Step 5: Commit the write-path enforcement**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java \
        server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java \
        server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/skill/service/SkillPublishServiceTest.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/ReviewPortalControllerTest.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/PromotionPortalControllerTest.java
git commit -m "feat: enforce namespace lifecycle on workflows"
git push origin feature/project-namespace
```

### Task 4: Hide archived namespaces from public skill reads and search

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java`
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillControllerTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java`

- [ ] **Step 1: Write the failing public-read tests**

```java
@Test
void getSkillDetail_returnsForbiddenOrNotFoundForArchivedNamespaceToAnonymousUser() throws Exception { ... }

@Test
void search_excludesSkillsFromArchivedNamespaces() throws Exception { ... }
```

- [ ] **Step 2: Run the public-read tests to verify failure**

Run: `cd server && ./mvnw -pl skillhub-app -Dtest=SkillControllerTest,SkillSearchControllerTest test`

Expected: FAIL because archived namespace state is not filtered in skill detail or search response assembly.

- [ ] **Step 3: Implement archived visibility filtering**

```java
private void assertNamespaceReadable(Namespace namespace, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
    boolean isMember = currentUserId != null && userNsRoles.containsKey(namespace.getId());
    if (namespace.getStatus() == NamespaceStatus.ARCHIVED && !isMember) {
        throw new DomainForbiddenException("error.namespace.archived");
    }
}
```

In search assembly, drop matched skills whose namespace is archived unless the current user is a member:

```java
.filter(skill -> namespaceVisible(skill.getNamespaceId(), userId, userNsRoles))
```

- [ ] **Step 4: Re-run the public-read tests**

Run: `cd server && ./mvnw -pl skillhub-app -Dtest=SkillControllerTest,SkillSearchControllerTest test`

Expected: PASS with archived namespaces hidden from public detail/search while frozen namespaces remain visible.

- [ ] **Step 5: Commit the public-read visibility changes**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSearchAppService.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillControllerTest.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillSearchControllerTest.java
git commit -m "feat: hide archived namespaces from public reads"
git push origin feature/project-namespace
```

## Chunk 3: Dashboard Integration

### Task 5: Add management DTOs and mutations to the web client

**Files:**
- Modify: `web/src/api/types.ts`
- Modify: `web/src/api/client.ts`
- Modify: `web/src/shared/hooks/use-skill-queries.ts`

- [ ] **Step 1: Add the failing frontend type and query integration**

Implement the client shape first so TypeScript fails until all consumers are updated:

```ts
export interface ManagedNamespace extends Namespace {
  currentUserRole: 'OWNER' | 'ADMIN' | 'MEMBER'
  immutable: boolean
  canFreeze: boolean
  canArchive: boolean
  canRestore: boolean
}
```

- [ ] **Step 2: Run frontend typecheck**

Run: `cd web && pnpm typecheck`

Expected: FAIL because `useMyNamespaces()` still returns the old `Namespace[]` shape and lifecycle mutations are missing.

- [ ] **Step 3: Implement API helpers and hooks**

```ts
async function getMyNamespaces(): Promise<ManagedNamespace[]> {
  return fetchJson<ManagedNamespace[]>(`${WEB_API_PREFIX}/me/namespaces`)
}

async function mutateNamespaceLifecycle(slug: string, action: 'freeze' | 'unfreeze' | 'archive' | 'restore', reason?: string) {
  return fetchJson<ManagedNamespace>(`${WEB_API_PREFIX}/namespaces/${slug}/${action}`, {
    method: 'POST',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(reason ? { reason } : {}),
  })
}
```

- [ ] **Step 4: Re-run frontend typecheck**

Run: `cd web && pnpm typecheck`

Expected: PASS for the API layer, even though UI pages still need updates in the next task.

- [ ] **Step 5: Commit the client-layer changes**

```bash
git add web/src/api/types.ts web/src/api/client.ts web/src/shared/hooks/use-skill-queries.ts
git commit -m "feat: add namespace governance client hooks"
git push origin feature/project-namespace
```

### Task 6: Enable namespace governance in dashboard pages

**Files:**
- Modify: `web/src/pages/dashboard/my-namespaces.tsx`
- Modify: `web/src/pages/dashboard/namespace-members.tsx`
- Modify: `web/src/pages/dashboard/namespace-reviews.tsx`
- Modify: `web/src/features/namespace/namespace-header.tsx`
- Modify: `web/src/i18n/locales/zh.json`
- Modify: `web/src/i18n/locales/en.json`

- [ ] **Step 1: Update the pages to fail fast on missing state fields**

Render status pills and lifecycle buttons from the new managed response shape so lint/typecheck catch any missing branch:

```tsx
{namespace.status === 'FROZEN' ? <Badge>{t('namespace.statusFrozen')}</Badge> : null}
{namespace.canArchive ? <Button onClick={() => archiveMutation.mutate({ slug: namespace.slug })}>...</Button> : null}
```

- [ ] **Step 2: Run frontend validation to capture incomplete UI wiring**

Run: `cd web && pnpm lint && pnpm typecheck`

Expected: FAIL until the pages, translations, and mutation invalidation logic are updated consistently.

- [ ] **Step 3: Implement the dashboard behavior**

Apply these rules:
- `my-namespaces`: show status badge, immutable `@global` hint, role-aware lifecycle buttons
- `namespace-members`: disable add/remove/role actions when status is `FROZEN` or `ARCHIVED`
- `namespace-reviews`: keep lists visible but disable review actions when namespace is not `ACTIVE`
- `namespace-header`: show namespace status and short governance hint

Suggested UI snippet:

```tsx
const readOnly = namespace.status !== 'ACTIVE' || namespace.immutable
<Button disabled={readOnly}>{t('members.addMember')}</Button>
{namespace.canFreeze ? <Button onClick={() => freezeMutation.mutate({ slug: namespace.slug })}>{t('namespace.freeze')}</Button> : null}
```

- [ ] **Step 4: Re-run frontend validation**

Run: `cd web && pnpm lint && pnpm typecheck`

Expected: PASS with no TypeScript or ESLint regressions.

- [ ] **Step 5: Commit the dashboard integration**

```bash
git add web/src/pages/dashboard/my-namespaces.tsx \
        web/src/pages/dashboard/namespace-members.tsx \
        web/src/pages/dashboard/namespace-reviews.tsx \
        web/src/features/namespace/namespace-header.tsx \
        web/src/i18n/locales/zh.json \
        web/src/i18n/locales/en.json
git commit -m "feat: add namespace governance dashboard"
git push origin feature/project-namespace
```

## Final Verification

- [ ] Run backend targeted verification:

```bash
cd server
./mvnw -pl skillhub-domain -Dtest=NamespaceGovernanceServiceTest,NamespaceServiceTest,NamespaceMemberServiceTest,SkillPublishServiceTest test
./mvnw -pl skillhub-app -Dtest=NamespacePortalControllerTest,ReviewPortalControllerTest,PromotionPortalControllerTest,SkillControllerTest,SkillSearchControllerTest test
```

- [ ] Run frontend verification:

```bash
cd web
pnpm lint
pnpm typecheck
```

- [ ] Run workspace status check:

```bash
git status --short
git log --oneline -n 5
```

- [ ] Push final branch state:

```bash
git push origin feature/project-namespace
```

Plan complete and saved to `docs/superpowers/plans/2026-03-16-namespace-governance.md`. Ready to execute?
