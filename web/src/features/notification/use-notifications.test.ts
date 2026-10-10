// @vitest-environment jsdom

import { createElement, type ReactNode } from 'react'
import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationItem, PagedResponse } from '@/api/types'

const { getUnreadCount } = vi.hoisted(() => ({
  getUnreadCount: vi.fn().mockResolvedValue({ count: 1 }),
}))

vi.mock('@/api/client', () => ({
  notificationApi: {
    getUnreadCount,
  },
}))

import {
  getNotificationListQueryOptions,
  getUnreadCountQueryOptions,
  markAllCachedNotificationsRead,
  markCachedNotificationRead,
  removeCachedNotification,
  useUnreadCount,
} from './use-notifications'

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children)
  }
}

describe('useUnreadCount polling lifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    getUnreadCount.mockClear()
  })

  afterEach(() => {
    focusManager.setFocused(undefined)
    vi.useRealTimers()
  })

  it('polls every ten seconds and stops after logout', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: Infinity } },
    })
    const { rerender, unmount } = renderHook(
      ({ userId }: { userId?: string }) => useUnreadCount(userId),
      { initialProps: { userId: 'user-a' as string | undefined }, wrapper: createWrapper(queryClient) },
    )

    await vi.waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(1))
    await act(() => vi.advanceTimersByTimeAsync(10_000))
    await vi.waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(2))

    rerender({ userId: undefined })
    await act(() => vi.advanceTimersByTimeAsync(20_000))
    expect(getUnreadCount).toHaveBeenCalledTimes(2)

    unmount()
    queryClient.clear()
  })

  it('uses a separate cache key after the authenticated user changes', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: Infinity } },
    })
    const { rerender, unmount } = renderHook(
      ({ userId }: { userId?: string }) => useUnreadCount(userId),
      { initialProps: { userId: 'user-a' as string | undefined }, wrapper: createWrapper(queryClient) },
    )

    await vi.waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(1))
    rerender({ userId: 'user-b' })
    await vi.waitFor(() => expect(
      queryClient.getQueryData(['notifications', 'user-b', 'unread-count']),
    ).toEqual({ count: 1 }))

    expect(queryClient.getQueryData(['notifications', 'user-a', 'unread-count'])).toEqual({ count: 1 })

    unmount()
    queryClient.clear()
  })

  it('refreshes on focus and clears its timer after unmount', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: Infinity } },
    })
    focusManager.setFocused(false)
    const { unmount } = renderHook(() => useUnreadCount('user-a'), {
      wrapper: createWrapper(queryClient),
    })

    await vi.waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(1))
    focusManager.setFocused(true)
    await vi.waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(2))

    unmount()
    await act(() => vi.advanceTimersByTimeAsync(20_000))
    expect(getUnreadCount).toHaveBeenCalledTimes(2)
    queryClient.clear()
  })
})

describe('notification mutation cache updates', () => {
  const userAKey = ['notifications', 'user-a', 'list', 0, 20] as const
  const userBKey = ['notifications', 'user-b', 'list', 0, 20] as const
  const page: PagedResponse<NotificationItem> = {
    items: [
      { id: 1, category: 'REVIEW' as const, eventType: 'A', title: 'A', status: 'UNREAD' as const, createdAt: '2026-09-03T00:00:00Z' },
      { id: 2, category: 'REVIEW' as const, eventType: 'B', title: 'B', status: 'UNREAD' as const, createdAt: '2026-09-03T00:00:01Z' },
    ],
    total: 2,
    page: 0,
    size: 20,
  }

  it('updates only the active user list immediately after mark-read operations', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(userAKey, page)
    queryClient.setQueryData(userBKey, page)

    markCachedNotificationRead(queryClient, 'user-a', 1, '2026-09-03T01:00:00Z')
    expect(queryClient.getQueryData<typeof page>(userAKey)?.items[0]).toMatchObject({
      status: 'READ',
      readAt: '2026-09-03T01:00:00Z',
    })
    expect(queryClient.getQueryData<typeof page>(userBKey)?.items[0].status).toBe('UNREAD')

    markAllCachedNotificationsRead(queryClient, 'user-a', '2026-09-03T01:01:00Z')
    expect(queryClient.getQueryData<typeof page>(userAKey)?.items.every((item) => item.status === 'READ')).toBe(true)
  })

  it('removes a deleted notification and adjusts the cached total', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(userAKey, page)

    removeCachedNotification(queryClient, 'user-a', 1)

    expect(queryClient.getQueryData<typeof page>(userAKey)).toMatchObject({
      items: [{ id: 2 }],
      total: 1,
    })
  })
})

describe('getUnreadCountQueryOptions', () => {
  it('polls the unread count over HTTP every ten seconds while the user is signed in', () => {
    const options = getUnreadCountQueryOptions('user-a')

    expect(options.queryKey).toEqual(['notifications', 'user-a', 'unread-count'])
    expect(options.enabled).toBe(true)
    expect(options.staleTime).toBe(0)
    expect(options.refetchInterval).toBe(10_000)
    expect(options.refetchOnWindowFocus).toBe(true)
  })

  it('does not poll before an authenticated user is available', () => {
    const options = getUnreadCountQueryOptions(undefined)

    expect(options.enabled).toBe(false)
  })
})

describe('getNotificationListQueryOptions', () => {
  it('polls an active notification list every ten seconds', () => {
    const options = getNotificationListQueryOptions('user-a', 0, 20, 'REVIEW')

    expect(options.queryKey).toEqual(['notifications', 'user-a', 'list', 0, 20, 'REVIEW'])
    expect(options.enabled).toBe(true)
    expect(options.staleTime).toBe(0)
    expect(options.refetchInterval).toBe(10_000)
    expect(options.refetchOnWindowFocus).toBe(true)
  })
})
