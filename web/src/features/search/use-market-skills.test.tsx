// @vitest-environment jsdom
import type { ReactNode } from 'react'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchJson } from '@/api/client'
import type { PagedResponse, SkillSummary } from '@/api/types'
import { flattenMarketSkills, useMarketSkills } from './use-market-skills'

vi.mock('@/api/client', () => ({ fetchJson: vi.fn(), WEB_API_PREFIX: '/api/web' }))
const clients: QueryClient[] = []
afterEach(() => { cleanup(); clients.forEach(client => client.clear()); clients.length = 0; vi.resetAllMocks() })

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  clients.push(client)
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return { client, wrapper }
}
function page(ids: number[], index = 0, total = 4): PagedResponse<SkillSummary> {
  return { items: ids.map(id => ({ id, slug: `skill-${id}`, namespace: 'global', displayName: `Skill ${id}`, updatedAt: '', downloadCount: 0, starCount: 0, ratingCount: 0, canSubmitPromotion: false })), page: index, size: 2, total }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}
const params = { q: '', source: 'platform' as const, sort: 'newest', size: 2 }

describe('incremental market discovery', () => {
  it('starts at zero, coalesces concurrent append calls, deduplicates ids and stops at the end', async () => {
    const second = deferred<PagedResponse<SkillSummary>>()
    vi.mocked(fetchJson).mockResolvedValueOnce(page([1, 2])).mockReturnValueOnce(second.promise)
    const { wrapper } = setup()
    const hook = renderHook(() => useMarketSkills(params, 'viewer-a', 'zh', true), { wrapper })
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
    expect(hook.result.current.data?.pages).toHaveLength(1)
    expect(vi.mocked(fetchJson).mock.calls[0][0]).toContain('page=0')
    let pending!: Promise<unknown>
    act(() => { pending = Promise.all([hook.result.current.fetchNextPage({ cancelRefetch: false }), hook.result.current.fetchNextPage({ cancelRefetch: false })]) })
    await waitFor(() => expect(fetchJson).toHaveBeenCalledTimes(2))
    await act(async () => { second.resolve(page([2, 3], 1)); await pending })
    expect(vi.mocked(fetchJson).mock.calls.map(([url]) => String(url))).toEqual([expect.stringContaining('page=0'), expect.stringContaining('page=1')])
    expect(hook.result.current.error).toBeNull()
    await waitFor(() => expect(hook.result.current.data?.pageParams).toEqual([0, 1]))
    await waitFor(() => expect(hook.result.current.hasNextPage).toBe(false))
    expect(flattenMarketSkills(hook.result.current.data?.pages).map(skill => skill.id)).toEqual([1, 2, 3])
    expect(vi.mocked(fetchJson).mock.calls[1][0]).toContain('page=1')
  })

  it('preserves loaded results after an append failure and retries only the failed page', async () => {
    vi.mocked(fetchJson).mockResolvedValueOnce(page([1, 2])).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page([3, 4], 1))
    const { wrapper } = setup()
    const hook = renderHook(() => useMarketSkills(params, null, 'en', true), { wrapper })
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
    await act(async () => { await hook.result.current.fetchNextPage({ cancelRefetch: false }) })
    await waitFor(() => expect(hook.result.current.isFetchNextPageError).toBe(true))
    expect(flattenMarketSkills(hook.result.current.data?.pages).map(skill => skill.id)).toEqual([1, 2])
    await act(async () => { await hook.result.current.fetchNextPage({ cancelRefetch: false }) })
    await waitFor(() => expect(hook.result.current.hasNextPage).toBe(false))
    expect(flattenMarketSkills(hook.result.current.data?.pages)).toHaveLength(4)
    expect(vi.mocked(fetchJson).mock.calls.slice(1).every(([url]) => String(url).includes('page=1'))).toBe(true)
  })

  it('does not let a late old-filter response replace the current source or viewer', async () => {
    const old = deferred<PagedResponse<SkillSummary>>()
    vi.mocked(fetchJson).mockReturnValueOnce(old.promise).mockResolvedValueOnce(page([9], 0, 1))
    const { wrapper } = setup()
    const hook = renderHook(({ source, viewer }: { source: 'platform' | 'github'; viewer: string }) => useMarketSkills({ ...params, source }, viewer, 'zh', true), { wrapper, initialProps: { source: 'platform', viewer: 'a' } })
    hook.rerender({ source: 'github', viewer: 'b' })
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
    await act(async () => { old.resolve(page([1, 2])); await old.promise })
    expect(flattenMarketSkills(hook.result.current.data?.pages).map(skill => skill.id)).toEqual([9])
    expect(vi.mocked(fetchJson).mock.calls[1][0]).toContain('source=github')
  })

  it('retains all cached batches on detail-return remount without fetching them again', async () => {
    vi.mocked(fetchJson).mockResolvedValueOnce(page([1, 2])).mockResolvedValueOnce(page([3, 4], 1))
    const { wrapper } = setup()
    const first = renderHook(() => useMarketSkills(params, null, 'en', true), { wrapper })
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true))
    await act(async () => { await first.result.current.fetchNextPage({ cancelRefetch: false }) })
    first.unmount()
    const returned = renderHook(() => useMarketSkills(params, null, 'en', true), { wrapper })
    expect(flattenMarketSkills(returned.result.current.data?.pages)).toHaveLength(4)
    expect(fetchJson).toHaveBeenCalledTimes(2)
  })

  it('stops on an empty batch even when a stale total claims more results', async () => {
    vi.mocked(fetchJson).mockResolvedValueOnce(page([], 0, 100))
    const { wrapper } = setup()
    const hook = renderHook(() => useMarketSkills(params, null, 'en', true), { wrapper })
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
    expect(hook.result.current.hasNextPage).toBe(false)
    expect(fetchJson).toHaveBeenCalledOnce()
  })
})
