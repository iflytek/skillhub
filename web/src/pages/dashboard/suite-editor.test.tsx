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
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/lib/toast', () => ({ toast: mocks.toast }))
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
    fireEvent.click(screen.getByText('suite.updatePinnedVersion').closest('button')!)

    expect(screen.getByRole('dialog', { name: 'suite.confirmVersionUpdateTitle' })).not.toBeNull()
    expect(screen.getByText('@global/weather@1.0.0')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.confirmVersionUpdate' }))

    await waitFor(() => expect(screen.getByText('@global/weather@2.0.0')).not.toBeNull())
  })
})
