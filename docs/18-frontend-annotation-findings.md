# Frontend Structure Findings During Annotation Pass

This document records concrete structure and architecture issues noticed while enriching comments in the front-end codebase. The goal is to preserve observations that repeatedly affected code readability, not to prescribe a full rewrite.

## 1. The shared query layer has become a cross-feature kitchen sink

Observed files:

- `web/src/shared/hooks/use-skill-queries.ts`
- `web/src/features/skill/use-skill-detail.ts`
- `web/src/features/namespace/use-namespace-detail.ts`

Why this stands out:

- One shared hook file currently owns search, skill detail, version reads, namespace membership, publishing, and promotion-related mutations.
- Several feature modules then re-export pieces of that shared file, which hides the real dependency direction.
- This makes the boundary between `shared` and `features` feel inverted.

Suggested direction:

- Split the file by feature slice or by backend resource area, and keep feature-facing hooks owned by their feature directories.

## 2. The router is a large centralized registry with route policy mixed into route declarations

Observed files:

- `web/src/app/router.tsx`
- `web/src/shared/components/role-guard.tsx`

Why this stands out:

- Route creation, auth guards, role checks, search validation, and lazy-loading rules are all declared in one large module.
- This works, but it increases the cost of changing one route because all route concerns are concentrated in a single file.

Suggested direction:

- Keep one router entry point, but consider splitting route definitions by area such as public, dashboard, admin, and settings.

## 3. Some pages still do too much orchestration instead of delegating to feature-level containers

Observed files:

- `web/src/pages/landing.tsx`
- `web/src/pages/search.tsx`
- `web/src/pages/skill-detail.tsx`
- `web/src/pages/dashboard.tsx`

Why this stands out:

- Several pages coordinate multiple hooks, query invalidation, local UI state, navigation rules, and derived presentation decisions.
- The page layer is therefore acting as route entry point and business container at the same time.

Suggested direction:

- Move heavier orchestration into feature containers or page-specific hooks so the page files mainly compose them.

## 4. Feature boundaries are uneven across the codebase

Observed files:

- `web/src/features/*`
- `web/src/shared/hooks/use-skill-queries.ts`
- `web/src/shared/lib/*`

Why this stands out:

- Some concerns are organized cleanly by feature, while others remain in shared folders even though they are domain-specific.
- This makes it harder to predict where new logic should live.

Suggested direction:

- Tighten the rule for what qualifies as `shared`: generic UI, generic hooks, and framework glue should stay there; business-specific query logic should usually live under `features`.

## 5. Runtime configuration bootstrapping relies on a global window contract

Observed files:

- `web/src/bootstrap.ts`
- `web/src/api/client.ts`
- `web/public/runtime-config.js`

Why this stands out:

- The current approach is pragmatic for deploy-time configuration, but it couples startup and API behavior to a mutable global object on `window`.
- That contract is easy to miss because its definition is spread across bootstrap and API code.

Suggested direction:

- Keep the mechanism if deploy-time injection is required, but document the lifecycle clearly or wrap it behind a dedicated runtime-config module.

## 6. Some feature modules are only thin re-export layers over shared hooks

Observed files:

- `web/src/features/skill/use-skill-detail.ts`
- `web/src/features/namespace/use-namespace-detail.ts`
- `web/src/features/namespace/use-namespace-members.ts`
- `web/src/features/publish/use-publish-skill.ts`

Why this stands out:

- These files preserve a feature-oriented import path, but they do not own the actual logic.
- The real behavior still lives in shared modules, which weakens the meaning of the feature boundary.

Suggested direction:

- Either move the implementation into the feature modules or import the shared hooks directly; keeping both layers long term adds indirection without much value.

## 7. Some generic dashboard widgets still contain workflow-specific routing rules

Observed files:

- `web/src/features/governance/governance-inbox.tsx`
- `web/src/features/governance/governance-notifications.tsx`

Why this stands out:

- These components look presentation-oriented, but they still know how review, promotion, report, and skill routes map onto dashboard URLs.
- That means route policy is now split between the central router and a few feature widgets.

Suggested direction:

- Consider moving item-to-route resolution into a dedicated governance navigation helper or feature hook, so the visual components stay closer to pure rendering.
