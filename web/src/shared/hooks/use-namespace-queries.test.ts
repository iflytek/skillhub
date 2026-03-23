import { describe, expect, it } from 'vitest'

/**
 * use-namespace-queries.ts exports React hooks that wrap @tanstack/react-query
 * useQuery/useMutation calls. Testing the hooks requires a React rendering
 * environment with QueryClientProvider, which is not available in this project.
 *
 * The pure logic (query key construction and shouldEnableNamespaceMemberCandidates)
 * is covered by query-keys.test.ts and skill-query-helpers.test.ts respectively.
 * Here we verify that all expected hooks are exported.
 */
describe('use-namespace-queries exports', () => {
  it('exports all expected hook functions', async () => {
    const mod = await import('./use-namespace-queries')
    expect(typeof mod.useMyNamespaces).toBe('function')
    expect(typeof mod.useCreateNamespace).toBe('function')
    expect(typeof mod.useNamespaceDetail).toBe('function')
    expect(typeof mod.useNamespaceMembers).toBe('function')
    expect(typeof mod.useNamespaceMemberCandidates).toBe('function')
    expect(typeof mod.useAddNamespaceMember).toBe('function')
    expect(typeof mod.useUpdateNamespaceMemberRole).toBe('function')
    expect(typeof mod.useRemoveNamespaceMember).toBe('function')
    expect(typeof mod.useFreezeNamespace).toBe('function')
    expect(typeof mod.useUnfreezeNamespace).toBe('function')
    expect(typeof mod.useArchiveNamespace).toBe('function')
    expect(typeof mod.useRestoreNamespace).toBe('function')
  })
})
