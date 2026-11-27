// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  list: vi.fn(),
  management: vi.fn(),
  get: vi.fn(),
  create: vi.fn(),
  writeSecret: vi.fn(),
  test: vi.fn(),
  activate: vi.fn(),
  resume: vi.fn(),
  changeStatus: vi.fn(),
}))

vi.mock('@/api/client', () => ({ organizationConnectionApi: api }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('react-i18next', async () => ({
  ...await vi.importActual<typeof import('react-i18next')>('react-i18next'),
  useTranslation: () => ({ t: (key: string) => key }),
}))

import { OrganizationLoginConnections } from './organization-login-connections'

const fixture = {
  id: 'connection-a',
  displayName: 'Company sign-in',
  status: 'DRAFT',
  latestRevision: { id: 'draft-a', revision: 1, issuer: 'https://idp.example.org', clientId: 'public-client-id' },
}

const activeRevision = {
  id: 'active-a',
  revision: 2,
  issuer: 'https://live-idp.example.org',
  clientId: 'live-client',
  secretConfigured: true,
}

let queryClient: QueryClient

function renderConnections(canManage: boolean, canWriteSecret: boolean) {
  render(
    <QueryClientProvider client={queryClient}>
      <OrganizationLoginConnections viewerId="viewer-a" organizationId="org-a"
        canManage={canManage} canWriteSecret={canWriteSecret} />
    </QueryClientProvider>,
  )
}

async function showDetails() {
  fireEvent.click(await screen.findByRole('button', { name: 'organizationConnections.details' }))
  await screen.findByText(fixture.latestRevision.issuer)
}

beforeEach(() => {
  vi.resetAllMocks()
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  api.management.mockResolvedValue({ enabled: true })
  api.list.mockResolvedValue({ items: [{ id: fixture.id, displayName: fixture.displayName, status: fixture.status }], total: 1 })
  api.get.mockResolvedValue(fixture)
})

afterEach(() => {
  cleanup()
  queryClient.clear()
})

describe('OrganizationLoginConnections', () => {
  it('keeps summaries available when connection management is disabled', async () => {
    api.management.mockResolvedValue({ enabled: false })
    renderConnections(true, true)

    expect(await screen.findByText(fixture.displayName)).toBeTruthy()
    await waitFor(() => expect(api.management).toHaveBeenCalledWith('org-a'))
    expect(screen.queryByRole('button', { name: 'organizationConnections.create' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'organizationConnections.details' })).toBeNull()
    expect(api.get).not.toHaveBeenCalled()
  })

  it('skips management and credential details for read-only users', async () => {
    renderConnections(false, false)

    expect(await screen.findByText(fixture.displayName)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'organizationConnections.create' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'organizationConnections.details' })).toBeNull()
    expect(api.management).not.toHaveBeenCalled()
    expect(api.get).not.toHaveBeenCalled()
  })

  it('pins credential input to the reviewed version and clears it when a write fails', async () => {
    api.writeSecret.mockRejectedValue(new Error('server accidentally echoes fake-secret'))
    renderConnections(false, true)
    await showDetails()

    expect(screen.queryByRole('button', { name: 'organizationConnections.edit' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'organizationConnections.test' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'organizationConnections.writeSecret' }))
    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(fixture.latestRevision.issuer)).toBeTruthy()
    const password = dialog.getByLabelText<HTMLInputElement>('organizationConnections.secret')
    fireEvent.change(password, { target: { value: 'fake-secret' } })
    fireEvent.click(dialog.getByRole('button', { name: 'organizationConnections.writeSecret' }))

    expect(api.writeSecret).toHaveBeenCalledExactlyOnceWith('org-a', fixture.id, 'draft-a', { clientSecret: 'fake-secret' })
    expect(password.value).toBe('')
    expect((await dialog.findByRole('alert')).textContent).toBe('organizationConnections.secretError')
    expect(queryClient.getMutationCache().getAll()).toEqual([])
  })

  it('blocks a credential write when a newer draft arrives during review', async () => {
    renderConnections(false, true)
    await showDetails()
    fireEvent.click(screen.getByRole('button', { name: 'organizationConnections.writeSecret' }))
    const dialog = within(await screen.findByRole('dialog'))

    act(() => {
      queryClient.setQueryData(
        ['organizations', 'viewer-a', 'detail', 'org-a', 'login-connections', 'connection', fixture.id],
        { ...fixture, latestRevision: { ...fixture.latestRevision, id: 'draft-b' } },
      )
    })

    expect((await dialog.findByRole('alert')).textContent).toBe('organizationConnections.staleDraft')
    const submit = dialog.getByRole<HTMLButtonElement>('button', { name: 'organizationConnections.writeSecret' })
    expect(submit.disabled).toBe(true)
    fireEvent.click(submit)
    expect(api.writeSecret).not.toHaveBeenCalled()
  })

  it('waits for a passing test of the same revision before offering activation', async () => {
    api.get.mockResolvedValue({ ...fixture, latestRevision: { ...fixture.latestRevision, secretConfigured: true } })
    api.test.mockImplementation(async () => {
      api.get.mockResolvedValue({
        ...fixture,
        lastTestedRevisionId: fixture.latestRevision.id,
        latestRevision: { ...fixture.latestRevision, secretConfigured: true },
      })
      return { success: true }
    })
    api.activate.mockResolvedValue({ ...fixture, status: 'ACTIVE' })
    renderConnections(true, false)
    await showDetails()

    expect(screen.queryByRole('button', { name: 'organizationConnections.writeSecret' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'organizationConnections.action.activate' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'organizationConnections.test' }))
    await waitFor(() => expect(api.test).toHaveBeenCalledExactlyOnceWith('org-a', fixture.id, fixture.latestRevision.id))

    fireEvent.click(await screen.findByRole('button', { name: 'organizationConnections.action.activate' }))
    const dialog = within(await screen.findByRole('dialog'))
    expect(api.activate).not.toHaveBeenCalled()
    fireEvent.click(dialog.getByRole('button', { name: 'organizationConnections.action.activate' }))
    await waitFor(() => expect(api.activate).toHaveBeenCalledExactlyOnceWith('org-a', fixture.id, fixture.latestRevision.id))
  })

  it('shows active and draft configurations separately and scopes probe errors to the draft', async () => {
    api.get.mockResolvedValue({
      ...fixture,
      status: 'ACTIVE',
      activeRevisionId: activeRevision.id,
      activeRevision,
      health: { status: 'FAILED', failureReason: 'UPSTREAM_UNAVAILABLE' },
    })
    renderConnections(true, true)
    await showDetails()

    const active = within(screen.getByRole('region', { name: 'organizationConnections.activeRevision' }))
    const draft = within(screen.getByRole('region', { name: 'organizationConnections.draftRevision' }))
    expect(active.getByText(activeRevision.issuer)).toBeTruthy()
    expect(active.getByText('organizationConnections.secretReady')).toBeTruthy()
    expect(active.getByRole('button', { name: 'organizationConnections.action.suspend' })).toBeTruthy()
    expect(active.queryByText('organizationConnections.probeFailed')).toBeNull()
    expect(draft.getByText(fixture.latestRevision.issuer)).toBeTruthy()
    expect(draft.getByText('organizationConnections.secretMissing')).toBeTruthy()
    expect(draft.getByText('organizationConnections.probeFailure.unavailable')).toBeTruthy()
    expect(draft.queryByRole('button', { name: 'organizationConnections.action.activate' })).toBeNull()
  })

  it('keeps a single active section with configuration controls when there is no newer draft', async () => {
    api.get.mockResolvedValue({
      ...fixture,
      status: 'ACTIVE',
      activeRevisionId: activeRevision.id,
      activeRevision,
      latestRevision: activeRevision,
    })
    renderConnections(true, true)
    fireEvent.click(await screen.findByRole('button', { name: 'organizationConnections.details' }))

    const active = within(await screen.findByRole('region', { name: 'organizationConnections.activeRevision' }))
    expect(active.getByRole('button', { name: 'organizationConnections.edit' })).toBeTruthy()
    expect(active.getByRole('button', { name: 'organizationConnections.writeSecret' })).toBeTruthy()
    expect(screen.queryByRole('region', { name: 'organizationConnections.draftRevision' })).toBeNull()
    expect(screen.getAllByText(activeRevision.issuer)).toHaveLength(1)
  })

  it('resumes the previous active revision through confirmation while the latest draft has failed', async () => {
    const paused = {
      ...fixture,
      status: 'SUSPENDED',
      activeRevisionId: activeRevision.id,
      activeRevision,
      health: { status: 'FAILED', failureReason: 'INVALID_METADATA' },
    }
    api.get.mockResolvedValue(paused)
    api.resume.mockImplementation(async () => {
      const resumed = { ...paused, status: 'ACTIVE' }
      api.get.mockResolvedValue(resumed)
      return resumed
    })
    renderConnections(true, true)
    await showDetails()

    const previous = within(screen.getByRole('region', { name: 'organizationConnections.previousRevision' }))
    fireEvent.click(previous.getByRole('button', { name: 'organizationConnections.action.resume' }))
    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('organizationConnections.resumeImpact')).toBeTruthy()
    expect(api.resume).not.toHaveBeenCalled()
    fireEvent.click(dialog.getByRole('button', { name: 'organizationConnections.action.resume' }))
    await waitFor(() => expect(api.resume).toHaveBeenCalledExactlyOnceWith('org-a', fixture.id, activeRevision.id))
    expect(api.activate).not.toHaveBeenCalled()
    expect(await screen.findByRole('region', { name: 'organizationConnections.activeRevision' })).toBeTruthy()
    const draft = within(screen.getByRole('region', { name: 'organizationConnections.draftRevision' }))
    expect(draft.getByText('organizationConnections.probeFailure.invalidMetadata')).toBeTruthy()
    expect(draft.queryByRole('button', { name: 'organizationConnections.action.activate' })).toBeNull()
  })

  it('shows the terminal effect before confirming a disable', async () => {
    api.changeStatus.mockResolvedValue({ ...fixture, status: 'DISABLED' })
    renderConnections(true, false)
    await showDetails()

    fireEvent.click(screen.getByRole('button', { name: 'organizationConnections.action.disable' }))
    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('organizationConnections.disableImpact')).toBeTruthy()
    expect(api.changeStatus).not.toHaveBeenCalled()
    fireEvent.click(dialog.getByRole('button', { name: 'organizationConnections.action.disable' }))
    await waitFor(() => expect(api.changeStatus).toHaveBeenCalledExactlyOnceWith('org-a', fixture.id, 'disable'))
  })
})
