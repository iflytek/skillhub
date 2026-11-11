/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, renderHook, waitFor } from '@testing-library/react'
import { reviewApi } from '@/api/client'
import type { ReviewTask } from '@/api/types'
import { useReviewList } from './use-review-list'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

// useReviewList is a useQuery wrapper. The internal getReviewList function computes
// totalElements and totalPages from the API response. While getReviewList itself is
// not exported, its behaviour can be validated indirectly through the reviewApi
// integration surface, similar to the pattern used in profile-review.test.ts.

describe('use-review-list exports', () => {
  it('exports useReviewList', async () => {
    const mod = await import('./use-review-list')
    expect(mod.useReviewList).toBeDefined()
    expect(typeof mod.useReviewList).toBe('function')
  })
})

describe('reviewApi.list response mapping', () => {
  it('reviewApi exports the list method', async () => {
    const { reviewApi } = await import('@/api/client')
    expect(reviewApi.list).toBeDefined()
    expect(typeof reviewApi.list).toBe('function')
  })

  it('returns paginated skill review list from the API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 0,
        msg: 'response.success',
        data: {
          items: [],
          total: 15,
          page: 0,
          size: 20,
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))
    vi.stubGlobal('document', { cookie: '' })

    const { reviewApi } = await import('@/api/client')
    const response = await reviewApi.list({ status: 'PENDING', page: 0, size: 20 })

    expect(response.total).toBe(15)
    expect(response.items).toEqual([])
  })
})


function reviewTask(id: number): ReviewTask {
  return { id, skillVersionId: id, namespace: 'team', version: '1.0', status: 'PENDING', submittedBy: 'owner', submittedAt: '2026-10-13T00:00:00Z' }
}

function queryWrapper(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
}

describe('review list cache behavior', () => {
  it('fetches externally submitted tasks on remount despite the application 30-second freshness default', async () => {
    const list = vi.spyOn(reviewApi, 'list').mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 20 })
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
    const wrapper = queryWrapper(client)
    const first = renderHook(() => useReviewList('PENDING', 2), { wrapper })
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true))
    expect(first.result.current.data?.items).toEqual([])
    first.unmount()

    list.mockResolvedValueOnce({ items: [reviewTask(79)], total: 1, page: 0, size: 20 })
    const second = renderHook(() => useReviewList('PENDING', 2), { wrapper })
    await waitFor(() => expect(second.result.current.data?.items.map((task) => task.id)).toEqual([79]))
    expect(list).toHaveBeenCalledTimes(2)
    expect(second.result.current.data?.totalElements).toBe(1)
    client.clear()
  })

  it('keeps namespace, status, page and sorting caches separate during navigation', async () => {
    const list = vi.spyOn(reviewApi, 'list')
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
    const scopes = [
      { status: 'PENDING', namespaceId: 2, page: 0, sortDirection: 'DESC' as const },
      { status: 'PENDING', namespaceId: 3, page: 0, sortDirection: 'DESC' as const },
      { status: 'APPROVED', namespaceId: 3, page: 0, sortDirection: 'DESC' as const },
      { status: 'APPROVED', namespaceId: 3, page: 1, sortDirection: 'DESC' as const },
      { status: 'APPROVED', namespaceId: 3, page: 1, sortDirection: 'ASC' as const },
    ]
    list.mockResolvedValueOnce({ items: [reviewTask(1)], total: 45, page: 0, size: 20 })
    const hook = renderHook((scope) => useReviewList(scope.status, scope.namespaceId, scope.page, 20, scope.sortDirection), { initialProps: scopes[0], wrapper: queryWrapper(client) })
    await waitFor(() => expect(hook.result.current.data?.items[0]?.id).toBe(1))
    for (let index = 1; index < scopes.length; index++) {
      list.mockResolvedValueOnce({ items: [reviewTask(index + 1)], total: 45, page: scopes[index].page, size: 20 })
      hook.rerender(scopes[index])
      await waitFor(() => expect(hook.result.current.data?.items[0]?.id).toBe(index + 1))
      expect(list).toHaveBeenLastCalledWith({ ...scopes[index], size: 20 })
    }
    for (const [index, scope] of scopes.entries()) {
      const cached = client.getQueryData<{ items: ReviewTask[] }>(['reviews', scope.status, scope.namespaceId, scope.page, 20, scope.sortDirection])
      expect(cached?.items[0]?.id).toBe(index + 1)
    }
    expect(hook.result.current.data?.totalPages).toBe(3)
    hook.unmount()
    client.clear()
  })
})

describe('unified review filters', () => {
  it('sends keyword, request type and source to the server and isolates their cache entries', async () => {
    const list = vi.spyOn(reviewApi, 'list').mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const initial = { q: 'notes', reviewKind: 'VERSION_UPDATE', originType: 'USER' }
    const hook = renderHook((filters) => useReviewList('ALL', undefined, 0, 20, 'DESC', true, filters), { initialProps: initial, wrapper: queryWrapper(client) })
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
    expect(list).toHaveBeenLastCalledWith({ status: 'ALL', namespaceId: undefined, page: 0, size: 20, sortDirection: 'DESC', ...initial })
    hook.rerender({ ...initial, originType: 'GITHUB_SYNC' })
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
    expect(client.getQueryCache().getAll()).toHaveLength(2)
    hook.unmount()
    client.clear()
  })
})

it('encodes server-side filters without losing spaces, Unicode or namespace scope', async () => {
  const request = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: 'ok', data: { items: [], total: 0, page: 0, size: 20 } }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', request)
  await reviewApi.list({ status: 'ALL', namespaceId: 4, q: '  中文 / A&B  ', reviewKind: 'VISIBILITY_CHANGE', originType: 'GITHUB_SYNC' })
  const url = new URL(String(request.mock.calls[0][0]), 'http://localhost')
  expect(url.searchParams.get('q')).toBe('中文 / A&B')
  expect(url.searchParams.get('status')).toBe('ALL')
  expect(url.searchParams.get('namespaceId')).toBe('4')
  expect(url.searchParams.get('reviewKind')).toBe('VISIBILITY_CHANGE')
  expect(url.searchParams.get('originType')).toBe('GITHUB_SYNC')
})
