/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { reviewApi } from '@/api/client'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'
import { useReviewScopes } from './use-review-scopes'

afterEach(() => { cleanup(); vi.restoreAllMocks() })
function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>
}
describe('authorized review scopes', () => {
  it('returns server-authorized scopes without consulting namespace membership', async () => {
    const scopes = [{ id: 23, slug: 'not-joined', displayName: 'Unjoined namespace' }]
    const request = vi.spyOn(reviewApi, 'scopes').mockResolvedValue(scopes)
    const { result } = renderHook(() => useReviewScopes(), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(scopes)
    expect(request).toHaveBeenCalledTimes(1)
  })
  it('clears the previous account scopes before another account reads them', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(['reviews', 'scopes'], [{ id: 23, slug: 'account-a-private', displayName: 'Account A' }])
    clearSessionScopedQueries(client)
    vi.spyOn(reviewApi, 'scopes').mockResolvedValue([{ id: 42, slug: 'account-b', displayName: 'Account B' }])
    const { result } = renderHook(() => useReviewScopes(), { wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> })
    expect(result.current.data).toBeUndefined()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0].id).toBe(42)
    client.clear()
  })
  it('does not request skill scopes in a profile-only workflow', () => {
    const request = vi.spyOn(reviewApi, 'scopes')
    renderHook(() => useReviewScopes(false), { wrapper })
    expect(request).not.toHaveBeenCalled()
  })
  it('keeps request failures visible instead of substituting member namespaces', async () => {
    vi.spyOn(reviewApi, 'scopes').mockRejectedValue(new Error('scope failed'))
    const { result } = renderHook(() => useReviewScopes(), { wrapper })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })
})
