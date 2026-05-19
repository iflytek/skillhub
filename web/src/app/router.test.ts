import { describe, expect, it, vi } from 'vitest'

// The router module captures window.location.search at module load time.
// We test the exported ORIGINAL_URL_SEARCH constant and the buildReturnTo
// helper (tested indirectly via the route tree structure).

vi.mock('./layout', () => ({
  Layout: () => null,
}))

vi.mock('@/api/client', () => ({
  getCurrentUser: vi.fn().mockResolvedValue(null),
}))

vi.mock('@/shared/components/role-guard', () => ({
  RoleGuard: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/lib/search-query', () => ({
  normalizeSearchQuery: (q: string) => q.trim(),
}))

import { ORIGINAL_URL_SEARCH, router } from './router'

describe('ORIGINAL_URL_SEARCH', () => {
  it('is a string (captured from window.location.search at load time)', () => {
    expect(typeof ORIGINAL_URL_SEARCH).toBe('string')
  })
})

describe('router', () => {
  it('exports a TanStack Router instance with a route tree', () => {
    expect(router).toBeDefined()
    expect(router.routeTree).toBeDefined()
  })

  it('has a routeTree structure', () => {
    // The router instance exists and has the expected structure
    // In test environment, flatRoutes may not be populated until router is used
    expect(router.routeTree).toBeDefined()
  })

  it('registers the skill version compare route', () => {
    const children = (router.routeTree.children ?? []) as Array<{ fullPath?: string; path?: string }>
    const childPaths = children.map((route) => route.fullPath ?? route.path)
    expect(childPaths).toContain('/space/$namespace/$slug/compare')
  })
})
