import { MutationCache, QueryClient } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/lib/api-error'
import { refreshSkillPermissionsAfterForbidden } from './refresh-skill-permissions'

describe('permission refresh after a rejected skill write', () => {
  it('invalidates only skill caches, keeps the write rejected and never reports success', async () => {
    const client: QueryClient = new QueryClient({ mutationCache: new MutationCache({
      onError: error => refreshSkillPermissionsAfterForbidden(client, error),
    }) })
    client.setQueryData(['skills', 'team', 'mine'], { canManageLifecycle: true })
    client.setQueryData(['skills', 'my'], { items: [] })
    client.setQueryData(['skill-avatar', 'team', 'mine'], { candidateStatus: 'PENDING' })
    client.setQueryData(['auth', 'me'], { userId: 'owner' })
    const write = vi.fn().mockRejectedValue(new ApiError('不允许修改', 403))
    const success = vi.fn()
    const mutation = client.getMutationCache().build(client, { mutationFn: write, onSuccess: success, retry: false })
    await expect(mutation.execute(undefined)).rejects.toMatchObject({ status: 403 })
    expect(write).toHaveBeenCalledTimes(1)
    expect(success).not.toHaveBeenCalled()
    expect(client.getQueryState(['skills', 'team', 'mine'])?.isInvalidated).toBe(true)
    expect(client.getQueryState(['skills', 'my'])?.isInvalidated).toBe(true)
    expect(client.getQueryState(['skill-avatar', 'team', 'mine'])?.isInvalidated).toBe(true)
    expect(client.getQueryState(['auth', 'me'])?.isInvalidated).toBe(false)
    client.clear()
  })

  it('refetches an active detail observer with the server read-only permissions', async () => {
    const { QueryObserver } = await import('@tanstack/react-query')
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const loader = vi.fn().mockResolvedValueOnce({ ownerReadOnly: false }).mockResolvedValue({ ownerReadOnly: true })
    const observer = new QueryObserver(client, { queryKey: ['skills', 'team', 'mine'], queryFn: loader })
    const unsubscribe = observer.subscribe(() => {})
    await observer.refetch()
    await refreshSkillPermissionsAfterForbidden(client, new ApiError('Forbidden', 403))
    expect(observer.getCurrentResult().data).toEqual({ ownerReadOnly: true })
    expect(loader).toHaveBeenCalledTimes(2)
    unsubscribe()
    client.clear()
  })

  it.each([new ApiError('Network error', 0), new ApiError('Conflict', 409), new Error('error')])('does not invalidate on other failures', async error => {
    const client = new QueryClient()
    client.setQueryData(['skills', 'my'], { items: [] })
    await refreshSkillPermissionsAfterForbidden(client, error)
    expect(client.getQueryState(['skills', 'my'])?.isInvalidated).toBe(false)
    client.clear()
  })
})
