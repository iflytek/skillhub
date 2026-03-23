import { describe, expect, it } from 'vitest'

/**
 * use-label-queries.ts exports React hooks that wrap @tanstack/react-query
 * useQuery/useMutation calls. Testing the hooks requires a React rendering
 * environment with QueryClientProvider, which is not available in this project.
 *
 * The pure logic (query key construction) is covered by query-keys.test.ts.
 * Here we verify that all expected hooks are exported.
 */
describe('use-label-queries exports', () => {
  it('exports all expected hook functions', async () => {
    const mod = await import('./use-label-queries')
    expect(typeof mod.useVisibleLabels).toBe('function')
    expect(typeof mod.useSkillLabels).toBe('function')
    expect(typeof mod.useAdminLabelDefinitions).toBe('function')
    expect(typeof mod.useAttachSkillLabel).toBe('function')
    expect(typeof mod.useDetachSkillLabel).toBe('function')
  })
})
