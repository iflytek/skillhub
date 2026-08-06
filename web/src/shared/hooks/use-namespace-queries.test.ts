import { beforeEach, describe, expect, it, vi } from 'vitest'

const useQueryMock = vi.hoisted(() => vi.fn())
const listMinePageMock = vi.hoisted(() => vi.fn())

vi.mock('@tanstack/react-query', () => ({
  useQuery: useQueryMock,
  useMutation: vi.fn(),
  useQueryClient: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  namespaceApi: {
    listMine: vi.fn(),
    listMinePage: listMinePageMock,
  },
}))

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
  beforeEach(() => {
    useQueryMock.mockClear()
    listMinePageMock.mockReset()
  })

  it('exports all expected hook functions', async () => {
    const mod = await import('./use-namespace-queries')
    expect(typeof mod.useMyNamespacesPage).toBe('function')
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

  it('passes bounded filters to a single paged my namespaces query', async () => {
    const mod = await import('./use-namespace-queries')

    mod.useMyNamespacesPage({
      page: 3,
      size: 15,
      status: 'ACTIVE',
      type: 'TEAM',
      q: 'team',
      slug: 'team-ai',
      sort: ['slug,desc'],
      roles: ['OWNER', 'ADMIN'],
    })

    expect(useQueryMock).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['namespaces', 'my', {
        page: 3,
        size: 15,
        status: 'ACTIVE',
        type: 'TEAM',
        q: 'team',
        slug: 'team-ai',
        sort: ['slug,desc'],
        roles: ['OWNER', 'ADMIN'],
      }],
    }))
    const queryOptions = useQueryMock.mock.calls[useQueryMock.mock.calls.length - 1]?.[0]
    listMinePageMock.mockResolvedValue({ items: [], total: 101, page: 3, size: 15 })

    await queryOptions.queryFn()

    expect(listMinePageMock).toHaveBeenCalledTimes(1)
    expect(listMinePageMock).toHaveBeenCalledWith({
      page: 3,
      size: 15,
      status: 'ACTIVE',
      type: 'TEAM',
      q: 'team',
      slug: 'team-ai',
      sort: ['slug,desc'],
      roles: ['OWNER', 'ADMIN'],
    })
  })

})
