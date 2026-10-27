/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ status: vi.fn(), delete: vi.fn(), submit: vi.fn(), retry: vi.fn(), appeal: vi.fn() }))
vi.mock('@/api/client', () => ({ skillAvatarApi: api, buildApiUrl: (url: string) => url }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string, options?: { time?: string }) => options?.time ? `${key}: ${options.time}` : key, i18n: { language: 'ko-KR', resolvedLanguage: 'ko' } }) }))
import { SkillAvatarManager } from './skill-avatar-manager'

describe('SkillAvatarManager appeal', () => {
  afterEach(() => { cleanup(); Object.values(api).forEach(fn => fn.mockReset()) })

  it.each([
    ['PENDING', 'BLOCKED', 'NOT_REQUIRED', true],
    ['PENDING', 'BLOCKED', 'APPEAL_PENDING', false],
    ['PENDING', 'PROCESSING', 'NOT_REQUIRED', false],
    ['REJECTED', 'BLOCKED', 'REJECTED', false],
    ['PENDING', 'ERROR', 'NOT_REQUIRED', false],
  ] as const)('shows appeal only for machine-only blocked candidate: %s/%s/%s', async (candidateStatus, machineStatus, reviewStatus, expected) => {
    api.status.mockResolvedValue({ avatarUrl: null, candidateId: 7, candidateStatus, machineStatus, reviewStatus, candidateUrl: null, reviewReason: null })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" /></QueryClientProvider>)
    expect(await screen.findByText(new RegExp(`skillAvatar.states.${candidateStatus}`))).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'skillAvatar.appeal' }) !== null).toBe(expected)
  })

  it('disables paid retry during cooldown and explains when automatic retries stop', async () => {
    const nextRetry = new Date(Date.now() + 3600000)
    api.status.mockResolvedValue({ avatarUrl: null, candidateId: 7, candidateStatus: 'PENDING',
      machineStatus: 'ERROR', reviewStatus: 'NOT_REQUIRED', candidateUrl: null, reviewReason: null,
      machineNextRetryAt: nextRetry.toISOString(), automaticRetryExhausted: true })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" /></QueryClientProvider>)

    expect(await screen.findByText(`skillAvatar.retryAfter: ${nextRetry.toLocaleString('ko')}`)).toBeTruthy()
    expect(screen.getByText('skillAvatar.automaticRetryStopped')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'skillAvatar.retry' }).hasAttribute('disabled')).toBe(true)
  })
  it('calls the existing retry API and preserves the recoverable state on failure', async () => {
    api.status.mockResolvedValue({ candidateId: 7, candidateStatus: 'PENDING', machineStatus: 'ERROR', reviewStatus: 'NOT_REQUIRED', machineNextRetryAt: null, automaticRetryExhausted: true })
    api.retry.mockRejectedValue(new Error('service unavailable'))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('button', { name: 'skillAvatar.retry' }))
    expect((await screen.findByRole('alert')).textContent).toBe('service unavailable')
    expect(api.retry).toHaveBeenCalledWith('team', 'foo', 7)
    expect(screen.getByRole('button', { name: 'skillAvatar.retry' }).hasAttribute('disabled')).toBe(false)
  })


  it('shows the submitted image separately from the unchanged public avatar while waiting', async () => {
    api.status.mockResolvedValue({ avatarUrl: '/old-public', candidateId: 8, candidateStatus: 'PENDING',
      machineStatus: 'SKIPPED', reviewStatus: 'PENDING', candidateUrl: '/private-candidate/8' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" /></QueryClientProvider>)
    expect((await screen.findByRole('img', { name: 'skillAvatar.submittedImage' })).getAttribute('src')).toBe('/private-candidate/8')
    expect(screen.getByText('skillAvatar.publicUnchanged')).toBeTruthy()
    expect(screen.queryByRole('img')?.getAttribute('src')).not.toBe('/old-public')
    expect(api.status).toHaveBeenCalledWith('team', 'foo')
  })

  it('keeps the My Skills preview read-only without adding another avatar action', async () => {
    api.status.mockResolvedValue({ candidateId: 8, candidateStatus: 'PENDING', machineStatus: 'BLOCKED',
      reviewStatus: 'NOT_REQUIRED', candidateUrl: '/private-candidate/8' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" previewOnly /></QueryClientProvider>)
    expect(await screen.findByRole('img', { name: 'skillAvatar.submittedImage' })).toBeTruthy()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('explains an unavailable candidate preview rather than presenting the public avatar as the submission', async () => {
    api.status.mockResolvedValue({ avatarUrl: '/old-public', candidateId: 8, candidateStatus: 'REJECTED',
      machineStatus: 'SKIPPED', reviewStatus: 'REJECTED', candidateUrl: '/private-candidate/8', reviewReason: 'Please choose a different image' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" /></QueryClientProvider>)
    fireEvent.error(await screen.findByRole('img', { name: 'skillAvatar.submittedImage' }))
    expect(screen.getByText('skillAvatar.candidateUnavailable')).toBeTruthy()
    expect(screen.getByText('Please choose a different image')).toBeTruthy()
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('removes the pending preview after the standard approval invalidates owner state', async () => {
    api.status.mockResolvedValueOnce({ candidateId: 8, candidateStatus: 'PENDING', machineStatus: 'SKIPPED',
      reviewStatus: 'PENDING', candidateUrl: '/private-candidate/8' })
      .mockResolvedValue({ avatarUrl: '/new-public', candidateId: 8, candidateStatus: 'ACTIVE',
        machineStatus: 'SKIPPED', reviewStatus: 'APPROVED', candidateUrl: '/private-candidate/8' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" previewOnly /></QueryClientProvider>)
    expect(await screen.findByRole('img', { name: 'skillAvatar.submittedImage' })).toBeTruthy()
    await client.invalidateQueries({ queryKey: ['skill-avatar', 'team', 'foo'] })
    await waitFor(() => expect(screen.queryByRole('img')).toBeNull())
    expect(screen.queryByText('skillAvatar.publicUnchanged')).toBeNull()
  })


  it('reports an initial owner-status load failure and allows a read-only reload', async () => {
    api.status.mockRejectedValueOnce(new Error('unavailable')).mockResolvedValue({ candidateId: 8,
      candidateStatus: 'PENDING', machineStatus: 'SKIPPED', reviewStatus: 'PENDING', candidateUrl: '/private-candidate/8' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" previewOnly /></QueryClientProvider>)
    expect(await screen.findByText('skillAvatar.statusLoadFailed')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'skillAvatar.reloadStatus' }))
    expect(await screen.findByRole('img', { name: 'skillAvatar.submittedImage' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(api.status).toHaveBeenCalledTimes(2)
  })

  it('keeps a cached submitted image visible while clearly reporting a failed status refresh', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(['skill-avatar', 'team', 'foo'], { candidateId: 8, candidateStatus: 'PENDING',
      machineStatus: 'SKIPPED', reviewStatus: 'PENDING', candidateUrl: '/private-candidate/8' })
    api.status.mockRejectedValue(new Error('unavailable'))
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" previewOnly /></QueryClientProvider>)
    expect(await screen.findByText('skillAvatar.statusRefreshFailed')).toBeTruthy()
    expect(screen.getByRole('img', { name: 'skillAvatar.submittedImage' }).getAttribute('src')).toBe('/private-candidate/8')
    expect(screen.getByRole('button', { name: 'skillAvatar.reloadStatus' })).toBeTruthy()
  })

  it('does not render approved reasons, duplicate image or editing controls', async () => {
    api.status.mockResolvedValue({ avatarUrl: '/active', candidateId: 7, candidateStatus: 'ACTIVE', machineStatus: 'SKIPPED', reviewStatus: 'APPROVED', reviewReason: 'approved reason' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SkillAvatarManager namespace="team" slug="foo" /></QueryClientProvider>)
    await waitFor(() => expect(api.status).toHaveBeenCalled())
    expect(screen.queryByText('approved reason')).toBeNull()
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })
})
