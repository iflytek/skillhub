/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ detail: vi.fn(), publish: vi.fn(), navigate: vi.fn(), search: {} as { namespace?: string; updateSkill?: string; resubmitSkill?: string; resubmitVersion?: string } }))
vi.mock('@/api/client', async () => ({
  ...await vi.importActual<typeof import('@/api/client')>('@/api/client'),
  fetchJson: api.detail,
  skillAvatarApi: { status: vi.fn().mockResolvedValue({ avatarUrl: '/active' }) },
}))
vi.mock('@/shared/hooks/use-skill-queries', async () => ({
  ...await vi.importActual<typeof import('@/shared/hooks/use-skill-queries')>('@/shared/hooks/use-skill-queries'),
  previewSkillPackage: vi.fn().mockResolvedValue({ name: 'foo', displayName: 'Foo', version: '1.1' }),
  usePublishSkill: () => ({ mutateAsync: api.publish, isPending: false }),
}))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => api.navigate, useSearch: () => api.search }))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useMyNamespaces: () => ({ data: [{ slug: 'team', displayName: 'Team' }], isLoading: false }) }))
vi.mock('@/features/publish/upload-zone', () => ({ UploadZone: ({ onFileSelect }: { onFileSelect: (file: File) => void }) => <button onClick={() => onFileSelect(new File(['zip'], 'skill.zip'))}>choose package</button> }))
vi.mock('@/features/skill/skill-avatar-manager', () => ({ SkillAvatarManager: () => null }))
vi.mock('@/features/skill/skill-avatar-editor', () => ({ KEEP_AVATAR: { action: 'KEEP', ready: true }, SkillAvatarEditor: () => null }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
import { PublishPage } from './publish'
import { ApiError } from '@/api/client'
import { toast } from '@/shared/lib/toast'

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe.each(['update', 'resubmit'])('%s package rejection refreshes current permissions', mode => {
  it.each([true, false])('uses the server ownerReadOnly=%s response after a rejected package', async ownerReadOnly => {
    api.search = mode === 'update' ? { namespace: 'team', updateSkill: 'foo' } : { namespace: 'team', resubmitSkill: 'foo', resubmitVersion: '1.0' }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
    api.detail.mockResolvedValueOnce({ ownerReadOnly: false }).mockResolvedValue({ ownerReadOnly })
    api.publish.mockRejectedValue(new ApiError('Localized package rejection', 400))
    render(<QueryClientProvider client={client}><PublishPage /></QueryClientProvider>)
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByText('choose package'))
    const submit = screen.getByRole('button', { name: 'publish.confirm' })
    await waitFor(() => expect(submit.hasAttribute('disabled')).toBe(false))
    fireEvent.click(submit)
    await waitFor(() => expect(toast.error).toHaveBeenCalled())
    expect(api.detail).toHaveBeenCalledTimes(2)
    expect(api.publish).toHaveBeenCalledTimes(1)
    expect(submit.hasAttribute('disabled')).toBe(ownerReadOnly)
    expect(Boolean(screen.queryByText('mySkills.ownerReadOnly'))).toBe(ownerReadOnly)
    expect(toast.success).not.toHaveBeenCalled()
    expect(api.navigate).not.toHaveBeenCalled()
    cleanup()
    client.clear()
  })
})
