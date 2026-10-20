/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ publish: vi.fn(), navigate: vi.fn(), submit: vi.fn(), remove: vi.fn(), refresh: vi.fn(), ownerReadOnly: false, search: { namespace: 'team', visibility: 'PUBLIC', updateSkill: 'foo', resubmitSkill: '' } }))
vi.mock('@tanstack/react-query', () => ({ useQueryClient: () => ({ invalidateQueries: mocks.refresh }), useQuery: ({ queryKey, enabled }: { queryKey: unknown[]; enabled?: boolean }) => queryKey[0] === 'skill-package-preview'
  ? { data: enabled ? { name: 'foo', displayName: 'Foo', version: '1.1' } : undefined, isSuccess: !!enabled, isPending: false, isError: false }
  : { data: { avatarUrl: '/owner-active' }, isLoading: false, isError: false } }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate, useSearch: () => mocks.search }))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({
  usePublishSkill: () => ({ mutateAsync: mocks.publish, isPending: false }),
  useSkillDetail: () => ({ data: { displayName: 'Foo', avatarUrl: '/active', ownerReadOnly: mocks.ownerReadOnly }, isLoading: false, isError: false }),
}))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({ useMyNamespaces: () => ({ data: [{ slug: 'team', displayName: 'Team' }], isLoading: false }) }))
vi.mock('@/features/publish/upload-zone', () => ({ UploadZone: ({ onFileSelect }: { onFileSelect: (file: File) => void }) => <button onClick={() => onFileSelect(new File(['zip'], 'skill.zip'))}>choose package</button> }))
vi.mock('@/features/skill/skill-avatar-editor', () => ({
  KEEP_AVATAR: { action: 'KEEP', ready: true },
  SkillAvatarEditor: ({ onChange }: { onChange: (value: { action: 'KEEP' | 'REPLACE' | 'REMOVE'; ready: boolean; uploadId?: string }) => void }) => <>
    <button onClick={() => onChange({ action: 'REPLACE', ready: false })}>image pending</button>
    <button onClick={() => onChange({ action: 'REPLACE', ready: true, uploadId: 'private-reference' })}>image uploaded</button>
    <button onClick={() => onChange({ action: 'REMOVE', ready: true })}>remove draft</button>
  </>,
}))
vi.mock('@/shared/lib/navigate-after-overlays', () => ({ navigateAfterOverlays: (fn: () => void) => fn() }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/features/skill/skill-avatar-manager', () => ({ SkillAvatarManager: () => null }))
vi.mock('@/api/client', async () => ({ ...await vi.importActual<typeof import('@/api/client')>('@/api/client'), skillAvatarApi: { submit: mocks.submit, delete: mocks.remove } }))
import { PublishPage } from './publish'
import { ApiError } from '@/api/client'
import { toast } from '@/shared/lib/toast'

describe('Publish avatar submission', () => {
  beforeEach(() => { vi.mocked(toast.success).mockClear(); vi.mocked(toast.error).mockClear(); mocks.ownerReadOnly = false; mocks.publish.mockReset(); mocks.navigate.mockReset(); mocks.search.updateSkill = 'foo'; mocks.search.resubmitSkill = ''; mocks.submit.mockReset(); mocks.remove.mockReset(); mocks.refresh.mockReset(); mocks.refresh.mockResolvedValue(undefined) })
  afterEach(cleanup)

  it('blocks both package and avatar-only updates when the server says the owner is read-only', () => {
    mocks.ownerReadOnly = true
    render(<PublishPage />)
    expect(screen.getByText('mySkills.ownerReadOnly')).toBeTruthy()
    fireEvent.click(screen.getByText('image uploaded'))
    expect(screen.getByRole('button', { name: 'publish.saveAvatar' }).hasAttribute('disabled')).toBe(true)
    fireEvent.click(screen.getByText('choose package'))
    expect(screen.getByRole('button', { name: 'publish.confirm' }).hasAttribute('disabled')).toBe(true)
    expect(mocks.submit).not.toHaveBeenCalled()
    expect(mocks.publish).not.toHaveBeenCalled()
  })

  it('refreshes permissions after an avatar API rejects a stale page, without a success notification', async () => {
    mocks.submit.mockRejectedValue(new ApiError('Forbidden', 403))
    render(<PublishPage />)
    fireEvent.click(screen.getByText('image uploaded'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    await waitFor(() => expect(toast.error).toHaveBeenCalled())
    expect(mocks.refresh).toHaveBeenCalledWith({ queryKey: ['skills'] })
    expect(mocks.refresh).toHaveBeenCalledWith({ queryKey: ['skill-avatar'] })
    expect(mocks.navigate).not.toHaveBeenCalled()
    expect(toast.success).not.toHaveBeenCalled()
    expect(mocks.submit).toHaveBeenCalledTimes(1)
  })

  it('blocks publishing a selected image until staged upload completes', async () => {
    mocks.publish.mockResolvedValue({ namespace: 'team', slug: 'foo', version: '1.1', status: 'PENDING_REVIEW' })
    render(<PublishPage />)
    fireEvent.click(screen.getByText('choose package'))
    fireEvent.click(screen.getByText('image pending'))
    expect(screen.getByRole('button', { name: 'publish.confirm' }).hasAttribute('disabled')).toBe(true)
    expect(mocks.publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText('image uploaded'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.confirm' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledWith(expect.objectContaining({ avatarAction: 'REPLACE', avatarUploadId: 'private-reference', expectedSlug: 'foo' })))
  })

  it('preserves the uploaded reference after a failed skill submission so retry does not reupload', async () => {
    mocks.publish.mockRejectedValueOnce(new Error('package invalid')).mockResolvedValueOnce({ namespace: 'team', slug: 'foo', version: '1.1', status: 'PUBLISHED' })
    render(<PublishPage />)
    fireEvent.click(screen.getByText('choose package'))
    fireEvent.click(screen.getByText('image uploaded'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.confirm' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledTimes(1))
    expect(mocks.navigate).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.confirm' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledTimes(2))
    expect(mocks.publish.mock.calls[1][0]).toEqual(mocks.publish.mock.calls[0][0])
  })

  it('sends removal only with the successful update request, not while editing', async () => {
    mocks.publish.mockResolvedValue({ namespace: 'team', slug: 'foo', version: '1.1', status: 'PENDING_REVIEW' })
    render(<PublishPage />)
    fireEvent.click(screen.getByText('remove draft'))
    expect(mocks.publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText('choose package'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.confirm' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledWith(expect.objectContaining({ avatarAction: 'REMOVE', expectedSlug: 'foo' })))
  })

  it('keeps the existing avatar by default when updating only skill content', async () => {
    mocks.publish.mockResolvedValue({ namespace: 'team', slug: 'foo', version: '1.1', status: 'PENDING_REVIEW' })
    render(<PublishPage />)
    fireEvent.click(screen.getByText('choose package'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.confirm' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledWith(expect.objectContaining({ avatarAction: 'KEEP', avatarUploadId: undefined, expectedSlug: 'foo' })))
  })
  it('clears the avatar draft when navigating to another skill in the same namespace', async () => {
    mocks.publish.mockResolvedValue({ namespace: 'team', slug: 'bar', version: '1.1', status: 'PENDING_REVIEW' })
    const view = render(<PublishPage />)
    fireEvent.click(screen.getByText('choose package'))
    fireEvent.click(screen.getByText('remove draft'))
    mocks.search.updateSkill = 'bar'
    view.rerender(<PublishPage />)
    fireEvent.click(screen.getByRole('button', { name: 'publish.confirm' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledWith(expect.objectContaining({ avatarAction: 'KEEP', avatarUploadId: undefined, expectedSlug: 'bar' })))
  })

  it('saves only an avatar through the existing API, without publishing a version, then refreshes caches', async () => {
    mocks.submit.mockResolvedValue({ candidateStatus: 'PENDING' })
    render(<PublishPage />)
    expect(screen.getByRole('button', { name: 'publish.saveAvatar' }).hasAttribute('disabled')).toBe(true)
    fireEvent.click(screen.getByText('image uploaded'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalled())
    expect(mocks.submit).toHaveBeenCalledWith('team', 'foo', 'private-reference')
    expect(mocks.publish).not.toHaveBeenCalled()
    expect(mocks.refresh.mock.calls.map(([arg]) => arg.queryKey)).toEqual([
      ['skills'], ['skill-avatar', 'team', 'foo'], ['reviews'],
    ])
  })

  it('waits for cache refresh and ignores a second save while the request is pending', async () => {
    let finish!: () => void
    mocks.submit.mockReturnValue(new Promise<void>(resolve => { finish = resolve }))
    let finishRefresh!: () => void
    mocks.refresh.mockReturnValue(new Promise<void>(resolve => { finishRefresh = resolve }))
    render(<PublishPage />)
    fireEvent.click(screen.getByText('image uploaded'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    fireEvent.click(screen.getByRole('button', { name: 'publish.publishing' }))
    expect(mocks.submit).toHaveBeenCalledTimes(1)
    finish()
    await waitFor(() => expect(mocks.refresh).toHaveBeenCalled())
    expect(mocks.navigate).not.toHaveBeenCalled()
    finishRefresh()
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalled())
  })

  it('keeps an avatar-only upload reference after failure so it can be retried', async () => {
    mocks.submit.mockRejectedValueOnce(new Error('503')).mockResolvedValueOnce({ candidateStatus: 'PENDING' })
    render(<PublishPage />)
    fireEvent.click(screen.getByText('image uploaded'))
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'publish.publishing' })).toBeNull())
    expect(mocks.navigate).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    await waitFor(() => expect(mocks.submit).toHaveBeenCalledTimes(2))
    expect(mocks.submit.mock.calls[1]).toEqual(mocks.submit.mock.calls[0])
  })

  it('removes only after save and does not create a skill version', async () => {
    mocks.remove.mockResolvedValue(undefined)
    render(<PublishPage />)
    fireEvent.click(screen.getByText('remove draft'))
    expect(mocks.remove).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.saveAvatar' }))
    await waitFor(() => expect(mocks.remove).toHaveBeenCalledWith('team', 'foo'))
    expect(mocks.publish).not.toHaveBeenCalled()
  })

  it.each(['create', 'resubmit'])('still requires a package for %s', mode => {
    mocks.search.updateSkill = ''
    if (mode === 'resubmit') mocks.search.resubmitSkill = 'foo'
    render(<PublishPage />)
    fireEvent.click(screen.getByText('image uploaded'))
    expect(screen.getByRole('button', { name: 'publish.confirm' }).hasAttribute('disabled')).toBe(true)
    expect(mocks.submit).not.toHaveBeenCalled()
  })

})
