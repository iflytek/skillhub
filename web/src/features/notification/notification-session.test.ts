import { QueryClient, QueryObserver } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { clearSessionScopedQueries, getNotificationQueryKeyScope } from './notification-session'

describe('getNotificationQueryKeyScope', () => {
  it('partitions notification caches by authenticated user id', () => {
    expect(getNotificationQueryKeyScope('user-a')).toEqual(['notifications', 'user-a'])
    expect(getNotificationQueryKeyScope('user-b')).toEqual(['notifications', 'user-b'])
  })

  it('falls back to a guest scope when there is no authenticated user', () => {
    expect(getNotificationQueryKeyScope(undefined)).toEqual(['notifications', 'guest'])
    expect(getNotificationQueryKeyScope(null)).toEqual(['notifications', 'guest'])
  })
})

describe('clearSessionScopedQueries', () => {
  it('clears viewer-specific skill summaries while keeping unrelated public data', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['notifications', 'user-a', 'unread-count'], { count: 3 })
    queryClient.setQueryData(['labels', 'visible'], [{ slug: 'official' }])
    queryClient.setQueryData(['skills', 'my', { page: 0, size: 12 }], { items: [] })
    queryClient.setQueryData(['skills', 'search', { q: '', sort: 'relevance', page: 0, size: 12, starredOnly: false }], { items: [] })
    queryClient.setQueryData(['skills', 'subscriptions'], [{ hasVisibleSuites: true }])
    queryClient.setQueryData(['skills', 'subscriptions', 'page', { page: 0 }], { items: [{ hasVisibleSuites: true }] })
    queryClient.setQueryData(['catalog', 'metadata'], { title: 'SkillHub' })

    clearSessionScopedQueries(queryClient)

    expect(queryClient.getQueryData(['notifications', 'user-a', 'unread-count'])).toBeUndefined()
    expect(queryClient.getQueryData(['labels', 'visible'])).toBeUndefined()
    expect(queryClient.getQueryData(['skills', 'my', { page: 0, size: 12 }])).toBeUndefined()
    expect(queryClient.getQueryData(['skills', 'search', { q: '', sort: 'relevance', page: 0, size: 12, starredOnly: false }])).toBeUndefined()
    expect(queryClient.getQueryData(['skills', 'subscriptions'])).toBeUndefined()
    expect(queryClient.getQueryData(['skills', 'subscriptions', 'page', { page: 0 }])).toBeUndefined()
    expect(queryClient.getQueryData(['catalog', 'metadata'])).toEqual({ title: 'SkillHub' })
    queryClient.clear()
  })

  it.each([
    { from: 'guest', to: 'member', expected: true },
    { from: 'member', to: 'other-user', expected: false },
    { from: 'member', to: 'guest', expected: false },
  ])('refreshes mounted search and subscription observers when $from becomes $to', async ({ from, to, expected }) => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
    let viewer = from
    const searchLoader = vi.fn(async () => ({ items: [{ hasVisibleSuites: viewer === 'member' }] }))
    const subscriptionsLoader = vi.fn(async () => ({ items: [{ hasVisibleSuites: viewer === 'member' }] }))
    const search = new QueryObserver(client, {
      queryKey: ['skills', 'search', { q: 'workflow' }],
      queryFn: searchLoader,
    })
    const subscriptions = new QueryObserver(client, {
      queryKey: ['skills', 'subscriptions', 'page', { page: 0 }],
      queryFn: subscriptionsLoader,
    })
    const stopSearch = search.subscribe(() => {})
    const stopSubscriptions = subscriptions.subscribe(() => {})
    try {
      await Promise.all([search.refetch(), subscriptions.refetch()])
      expect(search.getCurrentResult().data?.items[0].hasVisibleSuites).toBe(from === 'member')
      expect(searchLoader).toHaveBeenCalledTimes(1)
      expect(subscriptionsLoader).toHaveBeenCalledTimes(1)

      viewer = to
      clearSessionScopedQueries(client)
      expect(search.getCurrentResult().data).toBeUndefined()
      expect(subscriptions.getCurrentResult().data).toBeUndefined()
      await vi.waitFor(() => {
        expect(search.getCurrentResult().data?.items[0].hasVisibleSuites).toBe(expected)
        expect(subscriptions.getCurrentResult().data?.items[0].hasVisibleSuites).toBe(expected)
      })
      expect(searchLoader).toHaveBeenCalledTimes(2)
      expect(subscriptionsLoader).toHaveBeenCalledTimes(2)
    } finally {
      stopSearch()
      stopSubscriptions()
      client.clear()
    }
  })

  it('ignores a late response started under the previous viewer', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
    let finishPreviousRequest: (value: { hasVisibleSuites: boolean }) => void = () => {}
    const previousRequest = new Promise<{ hasVisibleSuites: boolean }>((resolve) => {
      finishPreviousRequest = resolve
    })
    const loader = vi.fn()
      .mockReturnValueOnce(previousRequest)
      .mockResolvedValue({ hasVisibleSuites: false })
    const observer = new QueryObserver(client, { queryKey: ['skills', 'search', { q: 'workflow' }], queryFn: loader })
    const unsubscribe = observer.subscribe(() => {})
    try {
      expect(loader).toHaveBeenCalledTimes(1)
      clearSessionScopedQueries(client)
      await vi.waitFor(() => expect(observer.getCurrentResult().data).toEqual({ hasVisibleSuites: false }))
      finishPreviousRequest({ hasVisibleSuites: true })
      await previousRequest
      await Promise.resolve()
      expect(observer.getCurrentResult().data).toEqual({ hasVisibleSuites: false })
      expect(loader).toHaveBeenCalledTimes(2)
    } finally {
      unsubscribe()
      client.clear()
    }
  })

  it('does not restore the previous membership hint when the new viewer request fails', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
    const loader = vi.fn()
      .mockResolvedValueOnce({ hasVisibleSuites: true })
      .mockRejectedValue(new Error('Request rejected for the new viewer'))
    const observer = new QueryObserver(client, { queryKey: ['skills', 'search', { q: 'workflow' }], queryFn: loader })
    const unsubscribe = observer.subscribe(() => {})
    try {
      await observer.refetch()
      expect(observer.getCurrentResult().data).toEqual({ hasVisibleSuites: true })
      clearSessionScopedQueries(client)
      await vi.waitFor(() => expect(observer.getCurrentResult().isError).toBe(true))
      expect(observer.getCurrentResult().data).toBeUndefined()
      expect(loader).toHaveBeenCalledTimes(2)
    } finally {
      unsubscribe()
      client.clear()
    }
  })
})
