---
name: frontend-conventions
description: Coding conventions, architecture patterns, and testing rules for the SkillHub React frontend. Ensures agents follow Feature-Sliced Design and use the generated OpenAPI types.
license: Apache-2.0
---

# Frontend Conventions Skill

## Trigger

Use this skill when:
- Adding or modifying React/TypeScript frontend code
- Creating new pages, features, entities, or shared components
- Changing API client calls or data fetching patterns

## Rules

### Feature-Sliced Design

Place code at the lowest appropriate layer:

| Layer | Path | Purpose |
|-------|------|---------|
| Pages | `web/src/pages/` | Route-level page components |
| Features | `web/src/features/` | Business features (search, upload, review, etc.) |
| Entities | `web/src/entities/` | Domain entity display logic (skill, user, namespace) |
| Shared | `web/src/shared/` | Reusable UI components, hooks, utilities |

Current features:
- `admin` — Admin panel (user management, labels, search)
- `auth` — Login, OAuth flows, device auth
- `governance` — Skill governance actions (hide, yank, archive)
- `namespace` — Namespace management (members, settings)
- `notification` — User notifications and inbox
- `promotion` — Skill promotion between scopes
- `publish` — Skill upload/publish UI
- `report` — Skill reporting
- `review` — Review workflow UI
- `search` — Skill search and filtering
- `security-audit` — Security audit viewer
- `skill` — Skill detail, listing, cards
- `social` — Stars, ratings, subscriptions
- `token` — API token management

### Data Fetching

- **Always use TanStack Query** (`@tanstack/react-query`) for server state.
- **Never use `useEffect`** for data fetching.
- Use `openapi-fetch` client with generated types from `web/src/api/generated/schema.d.ts`.
- Never use `any` types.

### State Management

- **TanStack Query** for server state (API data, caching, invalidation, optimistic updates)
- **Zustand** for local/UI state (theme, sidebar, modals, form state)

### Component Composition

- **Radix UI** primitives: `@radix-ui/react-dropdown-menu`, `@radix-ui/react-select`
- **class-variance-authority** (cva) for component variants
- **clsx** + **tailwind-merge** for class merging
- **`cn()` utility**: `web/src/shared/lib/utils.ts`
- **shadcn/ui is NOT used** as a library

### API Type Generation

When backend OpenAPI contracts change:

```bash
make generate-api
```

This runs `openapi-typescript http://localhost:8080/v3/api-docs -o src/api/generated/schema.d.ts`.

Commit the updated `web/src/api/generated/schema.d.ts` with the PR.

To verify the generated file is not stale:

```bash
./scripts/check-openapi-generated.sh
```

### Styling

- **Tailwind CSS** for all styling
- **`cn()` utility** for conditional class merging
- Follow existing component patterns in `web/src/shared/components/`

### Internationalization

- **i18next** + **react-i18next** for translations
- All user-facing text must be translatable
- Translation keys in `web/src/i18n/`

### Build & Development

```bash
make dev-web                # Start Vite dev server (HMR enabled)
make build-frontend         # Production build
make typecheck-web          # TypeScript type check (tsc --noEmit)
make lint-web               # ESLint check
make test-frontend          # Vitest unit tests
make test-e2e-frontend      # Playwright E2E tests
make test-e2e-smoke-frontend # Playwright smoke tests
```

Vite HMR is enabled by default — save a file and the browser updates instantly.

### Frontend Dependencies

Key dependencies (from `web/package.json`):
- `react` 19, `react-dom` 19
- `@tanstack/react-query` 5
- `@tanstack/react-router` 1
- `@radix-ui/react-dropdown-menu`, `@radix-ui/react-select`
- `class-variance-authority`, `clsx`, `tailwind-merge`
- `openapi-fetch` 0.13
- `i18next`, `react-i18next`
- `zustand` 5
- `react-markdown`, `rehype-highlight`, `rehype-sanitize`
- `lucide-react` (icons)
- `sonner` (toasts)

Build tools: Vite 6, TypeScript 5.7, Vitest 3.2, Playwright 1.58
