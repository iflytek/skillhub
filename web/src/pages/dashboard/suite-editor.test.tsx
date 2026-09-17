/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuite, SkillSuiteMemberCandidate } from '@/api/types'
import { SuiteEditor } from './suite-editor'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  detail: { data: undefined as SkillSuite | undefined, isLoading: false, error: null as Error | null },
  create: { mutateAsync: vi.fn(), isPending: false },
  createVersion: { mutateAsync: vi.fn(), isPending: false },
  update: { mutateAsync: vi.fn(), isPending: false },
  toast: { success: vi.fn(), error: vi.fn() },
  candidates: [] as SkillSuiteMemberCandidate[],
}))

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en', resolvedLanguage: 'en' } }),
}))
vi.mock('@/shared/lib/toast', () => ({ toast: mocks.toast }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ hasRole: () => false }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({
  useSuiteLabels: () => ({ data: [] }),
  useSkillLabels: () => ({ data: [] }),
  useVisibleLabels: () => ({ data: [], isLoading: false }),
  useAdminLabelDefinitions: () => ({ data: [], isLoading: false }),
  useAttachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useAttachSuiteLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSuiteLabel: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/features/suite/suite-bundle-import', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/suite/suite-bundle-import')>()
  return {
    ...actual,
    SuiteBundleImport: ({ expectedMode, expectedCoordinate, returnToSuite }: {
      expectedMode: string
      expectedCoordinate?: string
      returnToSuite?: { namespace: string; slug: string; version: string }
    }) => (
      <div data-return-to={returnToSuite ? `${returnToSuite.namespace}/${returnToSuite.slug}@${returnToSuite.version}` : ''}>
        {`bundle-import:${expectedMode}:${expectedCoordinate ?? ''}`}
      </div>
    ),
  }
})
vi.mock('@/shared/hooks/use-debounce', () => ({ useDebounce: (value: string) => value }))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({ data: [{ id: 1, slug: 'global' }] }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useCreateSuite: () => mocks.create,
  useCreateSuiteVersion: () => mocks.createVersion,
  useSuiteDetail: () => mocks.detail,
  useSuiteMemberCandidates: () => ({ data: mocks.candidates, isLoading: false }),
  useUpdateSuiteDraft: () => mocks.update,
}))
vi.mock('@/shared/ui/select', () => ({
  Select: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  SelectContent: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  SelectItem: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  SelectTrigger: ({ children, id }: { children?: ReactNode; id?: string }) => <div id={id}>{children}</div>,
  SelectValue: () => null,
}))

function sourceSuite(allowedActions: SkillSuite['allowedActions']): SkillSuite {
  return {
    id: 7,
    versionId: 70,
    namespace: 'global',
    slug: 'starter',
    displayName: 'Starter suite',
    createdBy: 'owner-1',
    createdAt: '2026-09-15T10:00:00Z',
    summary: 'Pinned tools',
    overview: '## Use this suite',
    version: '1.0.0',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    suiteStatus: 'ACTIVE',
    hidden: false,
    allowedActions,
    available: true,
    members: [{
      skillId: 9,
      skillVersionId: 90,
      namespace: 'global',
      slug: 'weather',
      version: '1.0.0',
      fingerprint: 'sha256:weather',
      position: 0,
      entry: true,
      browsable: true,
    }],
  }
}

describe('SuiteEditor', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    window.sessionStorage.clear()
    mocks.detail = { data: undefined, isLoading: false, error: null }
    mocks.candidates = []
  })

  it('shows a source error instead of submitting with a zero Suite id', () => {
    mocks.detail = { data: undefined, isLoading: false, error: new Error('not found') }

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)

    expect(screen.getByText('suite.sourceLoadFailed')).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.saveDraft' })).toBeNull()
  })

  it('blocks a directly opened edit route without the EDIT capability', () => {
    mocks.detail = { data: sourceSuite([]), isLoading: false, error: null }

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="edit" />)

    expect(screen.getByText('suite.editorAccessDenied')).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.saveDraft' })).toBeNull()
  })

  it('blocks a directly opened new-version route without CREATE_VERSION', () => {
    mocks.detail = { data: sourceSuite(['EDIT']), isLoading: false, error: null }

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)

    expect(screen.getByText('suite.editorAccessDenied')).not.toBeNull()
  })

  it('prefills the immutable source snapshot and creates a new exact version', async () => {
    mocks.detail = { data: sourceSuite(['CREATE_VERSION']), isLoading: false, error: null }
    mocks.createVersion.mutateAsync.mockResolvedValue(sourceSuite(['CREATE_VERSION']))
    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)

    await waitFor(() => expect((screen.getByLabelText('suite.name') as HTMLInputElement).value)
      .toBe('Starter suite'))
    expect((screen.getByLabelText('suite.overview') as HTMLTextAreaElement).value)
      .toBe('## Use this suite')
    expect(screen.getByText('suite.summaryComplete')).not.toBeNull()
    expect(screen.getByText('suite.overviewComplete')).not.toBeNull()
    expect(screen.getByText('suite.overviewPromptScenario')).not.toBeNull()
    expect(screen.getByText('suite.overviewPromptPreparation')).not.toBeNull()
    expect(screen.getByText('suite.overviewPromptSequence')).not.toBeNull()
    expect(screen.getByText('suite.overviewPromptInputsOutputs')).not.toBeNull()
    expect(screen.getByText('suite.overviewPromptBoundaries')).not.toBeNull()
    const version = screen.getByLabelText('suite.version') as HTMLInputElement
    expect(version.value).toBe('')
    fireEvent.change(version, { target: { value: '2.0.0' } })
    fireEvent.click(screen.getByRole('button', { name: 'suite.saveDraft' }))

    await waitFor(() => expect(mocks.createVersion.mutateAsync).toHaveBeenCalledWith({
      namespace: 'global',
      slug: 'starter',
      displayName: 'Starter suite',
      summary: 'Pinned tools',
      overview: '## Use this suite',
      version: '2.0.0',
      visibility: 'PUBLIC',
      changelog: undefined,
      entrySkill: { skillVersionId: 90, namespace: 'global', slug: 'weather', version: '1.0.0' },
      members: [{ skillVersionId: 90, namespace: 'global', slug: 'weather', version: '1.0.0' }],
    }))
  })

  it('reports a missing new version number without claiming the prefilled member is missing', async () => {
    mocks.detail = { data: sourceSuite(['CREATE_VERSION']), isLoading: false, error: null }

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)

    await waitFor(() => expect(screen.getByText('@global/weather@1.0.0')).not.toBeNull())
    fireEvent.click(screen.getByRole('button', { name: 'suite.saveDraft' }))

    expect(mocks.toast.error).toHaveBeenCalledWith('suite.versionRequired')
    expect(screen.getByRole('alert').textContent).toBe('suite.versionRequired')
    expect(mocks.toast.error).not.toHaveBeenCalledWith('suite.membersRequired')
    expect(mocks.createVersion.mutateAsync).not.toHaveBeenCalled()
  })

  it('does not claim selected skills are missing when only the suite name is empty', async () => {
    mocks.candidates = [{
      skillId: 21,
      skillVersionId: 210,
      namespace: 'global',
      slug: 'browser',
      displayName: 'Browser',
      version: '1.0.0',
      visibility: 'PUBLIC',
      recommended: true,
    }]

    render(<SuiteEditor />)

    await waitFor(() => expect((screen.getByLabelText('suite.slug') as HTMLInputElement).value).toBe(''))
    fireEvent.change(screen.getByLabelText('suite.slug'), { target: { value: 'browser-suite' } })
    fireEvent.click(screen.getByRole('button', { name: 'suite.search' }))
    fireEvent.click(screen.getByRole('button', { name: /Browser/ }))
    fireEvent.click(screen.getByRole('button', { name: 'suite.saveDraft' }))

    expect(mocks.toast.error).toHaveBeenCalledWith('suite.nameRequired')
    expect(mocks.toast.error).not.toHaveBeenCalledWith('suite.membersRequired')
    expect(mocks.create.mutateAsync).not.toHaveBeenCalled()
  })

  it('uses the first selected skill as the entry skill when creating a Suite', async () => {
    mocks.candidates = [{
      skillId: 21,
      skillVersionId: 210,
      namespace: 'global',
      slug: 'browser',
      displayName: 'Browser',
      version: '1.0.0',
      visibility: 'PUBLIC',
      recommended: true,
    }]
    mocks.create.mutateAsync.mockResolvedValue({
      ...sourceSuite(['EDIT']),
      slug: 'browser-suite',
      displayName: 'Browser suite',
      version: '1.0.0',
    })

    render(<SuiteEditor />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.search' }))
    await waitFor(() => expect(screen.getByRole('button', { name: /Browser/ })).not.toBeNull())
    fireEvent.change(screen.getByLabelText('suite.slug'), { target: { value: 'browser-suite' } })
    fireEvent.change(screen.getByLabelText('suite.name'), { target: { value: 'Browser suite' } })
    fireEvent.click(screen.getByRole('button', { name: /Browser/ }))
    fireEvent.click(screen.getByRole('button', { name: 'suite.saveDraft' }))

    await waitFor(() => expect(mocks.create.mutateAsync).toHaveBeenCalledWith({
      namespace: 'global',
      slug: 'browser-suite',
      displayName: 'Browser suite',
      summary: undefined,
      overview: undefined,
      version: '1.0.0',
      visibility: 'PUBLIC',
      changelog: undefined,
      entrySkill: { skillVersionId: 210, namespace: 'global', slug: 'browser', version: '1.0.0' },
      members: [{ skillVersionId: 210, namespace: 'global', slug: 'browser', version: '1.0.0' }],
    }))
  })

  it('requires one selected member to be the entry skill', async () => {
    const suite = sourceSuite(['EDIT'])
    suite.members[0].entry = false
    mocks.detail = { data: suite, isLoading: false, error: null }

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="edit" />)
    await waitFor(() => expect((screen.getByLabelText('suite.name') as HTMLInputElement).value)
      .toBe('Starter suite'))
    fireEvent.click(screen.getByRole('button', { name: 'suite.saveDraft' }))

    expect(mocks.toast.error).toHaveBeenCalledWith('suite.entryRequired')
    expect(mocks.update.mutateAsync).not.toHaveBeenCalled()
  })

  it('shows the pinned version change before replacing a selected member', async () => {
    mocks.detail = { data: sourceSuite(['EDIT']), isLoading: false, error: null }
    mocks.candidates = [{
      skillId: 9,
      skillVersionId: 91,
      namespace: 'global',
      slug: 'weather',
      displayName: 'Weather',
      version: '2.0.0',
      visibility: 'PUBLIC',
      recommended: true,
    }]

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="edit" />)
    await waitFor(() => expect(screen.getByText('@global/weather@1.0.0')).not.toBeNull())
    fireEvent.click(screen.getByRole('button', { name: 'suite.search' }))
    fireEvent.click(screen.getByText('suite.updatePinnedVersion').closest('button')!)

    expect(screen.getByRole('dialog', { name: 'suite.confirmVersionUpdateTitle' })).not.toBeNull()
    expect(screen.getByText('@global/weather@1.0.0')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.confirmVersionUpdate' }))

    await waitFor(() => expect(screen.getByText('@global/weather@2.0.0')).not.toBeNull())
  })

  it('offers local Bundle import for create and binds update imports to the current Suite', async () => {
    const { unmount } = render(<SuiteEditor />)
    fireEvent.click(screen.getByRole('tab', { name: 'suite.localImport' }))
    expect(screen.getByText('bundle-import:CREATE:')).not.toBeNull()
    unmount()

    mocks.detail = { data: sourceSuite(['CREATE_VERSION']), isLoading: false, error: null }
    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)
    await waitFor(() => expect(screen.getByRole('tab', { name: 'suite.localImport' })).not.toBeNull())
    fireEvent.click(screen.getByRole('tab', { name: 'suite.localImport' }))
    const updateImport = screen.getByText('bundle-import:UPDATE:@global/starter')
    expect(updateImport).not.toBeNull()
    expect(updateImport.getAttribute('data-return-to')).toBe('global/starter@1.0.0')
  })

  it('uses a deterministic Suite return target instead of browser history', async () => {
    mocks.detail = { data: sourceSuite(['CREATE_VERSION']), isLoading: false, error: null }
    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)

    await waitFor(() => expect(screen.getByRole('button', { name: 'suite.cancel' })).not.toBeNull())
    fireEvent.click(screen.getByRole('button', { name: 'suite.cancel' }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/starter',
      search: { version: '1.0.0' },
    })
  })

  it('reopens local import after reload when a CREATE operation is stored', () => {
    window.sessionStorage.setItem('skillhub:suite-bundle-operation:CREATE:new', 'operation-create')

    render(<SuiteEditor />)

    expect(screen.getByRole('tab', { name: 'suite.localImport' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByText('bundle-import:CREATE:')).not.toBeNull()
  })

  it('reopens the matching UPDATE import without using another Suite operation', async () => {
    window.sessionStorage.setItem(
      'skillhub:suite-bundle-operation:UPDATE:@global/starter',
      'operation-update',
    )
    window.sessionStorage.setItem(
      'skillhub:suite-bundle-operation:UPDATE:@global/another',
      'operation-other',
    )
    mocks.detail = { data: sourceSuite(['CREATE_VERSION']), isLoading: false, error: null }

    render(<SuiteEditor namespace="global" slug="starter" version="1.0.0" mode="new-version" />)

    await waitFor(() => expect(screen.getByText('bundle-import:UPDATE:@global/starter')).not.toBeNull())
    expect(screen.getByRole('tab', { name: 'suite.localImport' }).getAttribute('aria-selected')).toBe('true')
  })
})
