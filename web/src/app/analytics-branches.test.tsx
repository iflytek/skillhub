/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ManagedNamespace, SkillSuite } from '@/api/types'
import { NamespaceSettingsPage } from '@/pages/dashboard/namespace-settings'
import { ReportsPage } from '@/pages/dashboard/reports'
import { SuiteManagementActions } from '@/features/suite/suite-management-actions'
import { GithubSyncPlatformControl } from '@/features/github-sync/github-sync-platform-control'
import { GithubCredentialConfig } from '@/features/github-sync/github-credential-config'

const state = vi.hoisted(() => ({
  namespace: null as ManagedNamespace | null,
  namespaceMutate: vi.fn(), resolve: vi.fn(), dismiss: vi.fn(),
  hidden: vi.fn(), archived: vi.fn(), remove: vi.fn(),
  paused: false, platform: vi.fn(),
  credential: { id: 'synthetic-private-id', label: 'synthetic-private-label', status: 'ACTIVE', keyAvailable: true },
  create: vi.fn(), replace: vi.fn(), enable: vi.fn(),
}))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
vi.mock('@tanstack/react-router', () => ({ useParams: () => ({ slug: 'synthetic-private-team' }), useNavigate: () => vi.fn() }))
vi.mock('@/features/namespace/namespace-header', () => ({ NamespaceHeader: () => null }))
vi.mock('@/features/namespace/namespace-management-nav', () => ({ NamespaceManagementBack: () => null, NamespaceManagementNav: () => null }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useNamespaceDetail: () => ({ data: state.namespace, isLoading: false }),
  useMyNamespaces: () => ({ data: state.namespace ? [state.namespace] : [] }),
  useFreezeNamespace: () => ({ mutateAsync: state.namespaceMutate }),
  useUnfreezeNamespace: () => ({ mutateAsync: state.namespaceMutate }),
  useArchiveNamespace: () => ({ mutateAsync: state.namespaceMutate }),
  useRestoreNamespace: () => ({ mutateAsync: state.namespaceMutate }),
  useDeleteNamespace: () => ({ mutateAsync: state.namespaceMutate }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useReopenSuiteVersion: () => ({ mutateAsync: vi.fn() }),
  useYankSuiteVersion: () => ({ mutateAsync: vi.fn() }),
  useSetSuiteHidden: () => ({ mutateAsync: state.hidden }),
  useSetSuiteArchived: () => ({ mutateAsync: state.archived }),
  useDeleteSuite: () => ({ mutateAsync: state.remove }),
}))
vi.mock('@/features/report/use-skill-reports', () => ({
  useSkillReports: (status: string) => ({ data: { items: status === 'PENDING' ? [{ id: 1, skillId: 2, namespace: 'private-team', skillSlug: 'private-skill', reason: 'private-reason', createdAt: '2026-01-01' }] : [], total: status === 'PENDING' ? 1 : 0, size: 10 }, isLoading: false }),
  useResolveSkillReport: () => ({ mutateAsync: state.resolve, isPending: false }),
  useDismissSkillReport: () => ({ mutateAsync: state.dismiss, isPending: false }),
}))
vi.mock('@/features/github-sync/use-github-sync-admin', () => ({
  useGithubPlatformControl: () => ({ data: { desiredPaused: state.paused, version: 1, state: state.paused ? 'PAUSED' : 'RUNNING' } }),
  useUpdateGithubPlatformControl: () => ({ mutateAsync: state.platform }),
  useGithubCredentials: () => ({ data: [state.credential] }),
  useGithubCredentialMutations: () => ({ create: { mutateAsync: state.create }, replace: { mutateAsync: state.replace }, setEnabled: { mutateAsync: state.enable } }),
}))
const id = (node: Element) => node.getAttribute('data-analytics-id')
function assertNoSensitiveAttributes() {
  for (const node of document.querySelectorAll('[data-analytics-id]')) expect(id(node)).not.toMatch(/synthetic|private|reason|token-value/)
}
beforeEach(() => {
  vi.clearAllMocks()
  state.namespace = { id: 2, slug: 'synthetic-private-team', displayName: 'Private name', type: 'TEAM', status: 'ACTIVE', createdAt: '', immutable: false, currentUserRole: 'OWNER', canFreeze: true, canUnfreeze: true, canArchive: true, canRestore: true, canDelete: true }
  state.paused = false
  state.credential.status = 'ACTIVE'
})
afterEach(cleanup)

describe('finite business branch markers on real confirmation DOM', () => {
  it.each(['freeze', 'unfreeze', 'archive', 'restore', 'delete'] as const)('namespace %s keeps request, cancel, X and confirm distinct', async action => {
    render(<NamespaceSettingsPage />)
    const request = screen.getByTestId(`${action}-namespace-synthetic-private-team`)
    expect(id(request)).toBe(`namespace.${action}.request`)
    fireEvent.click(request)
    const confirm = screen.getByTestId(`namespace-action-confirm-${action}`)
    expect(id(confirm)).toBe(`namespace.${action}.confirm`)
    expect(id(screen.getByRole('button', { name: 'Close' }))).toBe(`namespace.${action}.cancel`)
    fireEvent.click(screen.getByRole('button', { name: 'dialog.cancel' }))
    expect(state.namespaceMutate).not.toHaveBeenCalled()
    fireEvent.click(request)
    fireEvent.click(screen.getByTestId(`namespace-action-confirm-${action}`))
    await waitFor(() => expect(state.namespaceMutate).toHaveBeenCalledTimes(1))
    assertNoSensitiveAttributes()
  })

  it('does not expose namespace actions for global or missing data', () => {
    state.namespace!.type = 'GLOBAL'
    const view = render(<NamespaceSettingsPage />)
    expect(document.querySelector('[data-analytics-id]')).toBeNull()
    state.namespace = null
    view.rerender(<NamespaceSettingsPage />)
    expect(document.querySelector('[data-analytics-id]')).toBeNull()
  })

  it.each(['hide', 'restore', 'archive', 'unarchive', 'delete'] as const)('suite %s confirmation and cancel preserve the handler', async action => {
    const suite = { id: 7, versionId: 70, namespace: 'private-team', slug: 'private-suite', displayName: 'Private name', version: '1', status: 'PUBLISHED', suiteStatus: action === 'unarchive' ? 'ARCHIVED' : 'ACTIVE', hidden: action === 'restore', allowedActions: [action.toUpperCase()] } as SkillSuite
    render(<SuiteManagementActions suite={suite} />)
    const request = screen.getByRole('button', { name: `suite.${action}` })
    expect(id(request)).toBe(`suite.${action}.request`)
    fireEvent.click(request)
    expect(id(screen.getByRole('button', { name: 'dialog.cancel' }))).toBe(`suite.${action}.cancel`)
    expect(id(screen.getByRole('button', { name: 'Close' }))).toBe(`suite.${action}.cancel`)
    const confirm = screen.getAllByRole('button', { name: `suite.${action}` }).find(node => id(node) === `suite.${action}.confirm`)!
    expect(confirm).toBeDefined()
    fireEvent.click(confirm)
    if (action === 'hide' || action === 'restore') await waitFor(() => expect(state.hidden).toHaveBeenCalledWith({ suiteId: 7, hidden: action === 'hide' }))
    else if (action === 'archive' || action === 'unarchive') await waitFor(() => expect(state.archived).toHaveBeenCalledWith({ suiteId: 7, archived: action === 'archive' }))
    else await waitFor(() => expect(state.remove).toHaveBeenCalledWith({ suiteId: 7 }))
    assertNoSensitiveAttributes()
  })

  it.each([
    ['dismiss', 'dismiss', undefined], ['resolve', 'resolve', 'RESOLVE_ONLY'],
    ['resolveAndHide', 'hide', 'RESOLVE_AND_HIDE'], ['resolveAndArchive', 'archive', 'RESOLVE_AND_ARCHIVE'],
  ] as const)('report %s keeps its disposition and abandonment', async (label, action, disposition) => {
    render(<ReportsPage />)
    const request = screen.getByRole('button', { name: `reports.${label}` })
    expect(id(request)).toBe(`report.${action}.request`)
    fireEvent.click(request)
    expect(id(screen.getByRole('button', { name: 'dialog.cancel' }))).toBe(`report.${action}.cancel`)
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(state.resolve).not.toHaveBeenCalled()
    expect(state.dismiss).not.toHaveBeenCalled()
    fireEvent.click(request)
    const confirm = document.querySelector(`[data-analytics-id="report.${action}.confirm"]`)!
    fireEvent.click(confirm)
    if (disposition) await waitFor(() => expect(state.resolve).toHaveBeenCalledWith({ id: 1, disposition }))
    else await waitFor(() => expect(state.dismiss).toHaveBeenCalledWith({ id: 1 }))
    assertNoSensitiveAttributes()
  })

  it.each([false, true])('platform paused=%s uses finite pause/resume values', async paused => {
    state.paused = paused
    render(<GithubSyncPlatformControl visible />)
    const action = paused ? 'resume' : 'pause'
    fireEvent.click(document.querySelector(`[data-analytics-id="github.platform.${action}.request"]`)!)
    expect(id(screen.getByRole('button', { name: 'Close' }))).toBe(`github.platform.${action}.cancel`)
    fireEvent.click(document.querySelector(`[data-analytics-id="github.platform.${action}.confirm"]`)!)
    await waitFor(() => expect(state.platform).toHaveBeenCalledWith({ expectedVersion: 1, paused: !paused }))
  })

  it.each(['create', 'replace'] as const)('credential %s marks submit only, never the secret fields', action => {
    render(<GithubCredentialConfig />)
    fireEvent.click(document.querySelector(`[data-analytics-id="github.credential.${action}"]`)!)
    const form = document.querySelector('form')!
    expect(id(form)).toBe(`github.credential.${action}.submit`)
    expect(id(screen.getByRole('button', { name: 'Close' }))).toBe(`github.credential.${action}.cancel`)
    expect(form.querySelector('button[type="submit"]')?.hasAttribute('data-analytics-id')).toBe(false)
    for (const field of form.querySelectorAll('input')) expect(id(field)).toBeNull()
    expect(document.querySelector('[data-analytics-id="github.credential.disable"]')).not.toBeNull()
    assertNoSensitiveAttributes()
  })
})
