/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ remove: vi.fn(), status: vi.fn(), navigate: vi.fn() }))
vi.mock('@/api/client', async () => ({ ...await vi.importActual<typeof import('@/api/client')>('@/api/client'), skillAvatarApi: { delete: api.remove, status: api.status } }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => api.navigate, useSearch: () => ({ namespace: 'team', visibility: 'PUBLIC', updateSkill: 'foo' }) }))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useMyNamespaces: () => ({ data: [{ slug: 'team', displayName: 'Team' }], isLoading: false }) }))
vi.mock('@/features/publish/upload-zone', () => ({ UploadZone: () => null }))
vi.mock('@/features/skill/skill-avatar-manager', () => ({ SkillAvatarManager: () => null }))
vi.mock('@/features/skill/skill-avatar-editor', () => ({
  KEEP_AVATAR: { action: 'KEEP', ready: true },
  SkillAvatarEditor: ({ onChange }: { onChange: (value: { action: 'REMOVE'; ready: boolean }) => void }) => <button onClick={() => onChange({ action: 'REMOVE', ready: true })}>remove draft</button>,
}))
vi.mock('@/shared/lib/navigate-after-overlays', () => ({ navigateAfterOverlays: (callback: () => void) => callback() }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/shared/hooks/use-skill-queries', async () => ({ ...await vi.importActual<typeof import('@/shared/hooks/use-skill-queries')>('@/shared/hooks/use-skill-queries'), useSkillDetail: () => ({ data: undefined }) }))
import { PublishPage } from './publish'

describe('avatar-only removal refreshes inactive display caches', () => {
  afterEach(() => { cleanup(); Object.values(api).forEach(mock => mock.mockReset()) })

  it('refreshes inactive MySkills and detail data before navigating back after deletion', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
    const myKey = ['skills', 'my', { q: 'foo' }]
    const detailKey = ['skills', 'team', 'foo', 'en']
    let release!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    const myLoader = vi.fn().mockResolvedValueOnce({ items: [{ slug: 'foo', avatarUrl: '/old-avatar' }] }).mockImplementation(async () => { await gate; return { items: [{ slug: 'foo', avatarUrl: null }] } })
    const detailLoader = vi.fn().mockResolvedValueOnce({ slug: 'foo', avatarUrl: '/old-avatar' }).mockResolvedValue({ slug: 'foo', avatarUrl: null })
    await client.fetchQuery({ queryKey: myKey, queryFn: myLoader })
    await client.fetchQuery({ queryKey: detailKey, queryFn: detailLoader })
    expect(client.getQueryCache().find({ queryKey: myKey })?.getObserversCount()).toBe(0)
    expect(client.getQueryCache().find({ queryKey: detailKey })?.getObserversCount()).toBe(0)
    api.status.mockResolvedValue({ avatarUrl: null })
    api.remove.mockResolvedValue(undefined)
    api.navigate.mockImplementation(() => {
      expect(client.getQueryData(myKey)).toEqual({ items: [{ slug: 'foo', avatarUrl: null }] })
      expect(client.getQueryData(detailKey)).toEqual({ slug: 'foo', avatarUrl: null })
    })
    render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect(api.status).toHaveBeenCalled())
    fireEvent.click(screen.getByText('remove draft'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    await waitFor(() => expect(myLoader).toHaveBeenCalledTimes(2))
    expect(api.navigate).not.toHaveBeenCalled()
    release()
    await waitFor(() => expect(api.navigate).toHaveBeenCalledWith({ to: '/dashboard/skills' }))
    expect(api.remove).toHaveBeenCalledWith('team', 'foo')
    expect(detailLoader).toHaveBeenCalledTimes(2)
  })
})
