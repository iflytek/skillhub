import { describe, expect, it } from 'vitest'

/**
 * use-user-queries.ts exports React hooks that wrap @tanstack/react-query
 * useQuery/useMutation calls. Testing the hooks requires a React rendering
 * environment with QueryClientProvider, which is not available in this project.
 *
 * Here we verify that all expected hooks are exported.
 */
describe('use-user-queries exports', () => {
  it('exports all expected hook functions', async () => {
    const mod = await import('./use-user-queries')
    expect(typeof mod.useMySkills).toBe('function')
    expect(typeof mod.useMyStars).toBe('function')
    expect(typeof mod.useMyStarsPage).toBe('function')
    expect(typeof mod.useSubmitPromotion).toBe('function')
  })
})
