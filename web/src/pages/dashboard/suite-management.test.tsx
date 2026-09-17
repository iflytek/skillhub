/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuite } from '@/api/types'
import { SuiteManagementPage } from './suite-management'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  search: { version: '1.0.0', tab: undefined as 'members' | 'versions' | 'publishing' | undefined },
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useParams: () => ({ namespace: 'global', slug: 'demo-yanked' }),
  useSearch: () => mocks.search,
  Link: ({ children, params, search }: {
    children: ReactNode
    params?: { namespace?: string; slug?: string; operationId?: string }
    search?: { version?: string }
  }) => <a href={`/space/${params?.namespace}/${params?.slug}?version=${search?.version ?? ''}`}>{children}</a>,
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'zh-CN' } }),
}))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ hasRole: () => false }) }))
vi.mock('@/features/skill/markdown-renderer', () => ({ MarkdownRenderer: ({ content }: { content: string }) => <div>{content}</div> }))
vi.mock('@/features/skill/skill-label-panel', () => ({ SuiteLabelPanel: () => <div>label-panel</div> }))
vi.mock('@/features/suite/suite-management-actions', () => ({ SuiteManagementActions: () => <div>management-actions</div> }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useSuiteLabels: () => ({ data: [] }) }))

const suite: SkillSuite = {
  id: 7,
  versionId: 70,
  namespace: 'global',
  slug: 'demo-yanked',
  displayName: 'Yanked demo',
  createdBy: 'owner-1',
  createdAt: '2026-09-15T10:00:00Z',
  summary: 'Yanked summary',
  overview: 'Yanked overview',
  version: '1.0.0',
  status: 'YANKED',
  visibility: 'PUBLIC',
  suiteStatus: 'ACTIVE',
  hidden: false,
  allowedActions: ['CREATE_VERSION'],
  available: false,
  members: [{
    skillId: 9,
    skillVersionId: 90,
    namespace: 'global',
    slug: 'member-one',
    version: '1.0.0',
    fingerprint: 'sha256:member',
    position: 0,
    entry: true,
    browsable: true,
  }],
}

vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteDetail: () => ({ data: suite, isLoading: false, error: null }),
  useSuiteVersions: () => ({ data: [{
    id: 70,
    version: '1.0.0',
    status: 'YANKED',
    visibility: 'PUBLIC',
    createdBy: 'owner-1',
    createdByName: 'Suite Owner',
    createdAt: '2026-09-15T10:00:00Z',
    yankedAt: '2026-09-15T11:00:00Z',
    changelog: 'Initial release',
  }] }),
  useMySuiteBundleOperations: () => ({
    data: { items: [], total: 0, page: 0, size: 50, hasChangingOperations: false },
  }),
  useSubmitSuite: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

describe('SuiteManagementPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.search = { version: '1.0.0', tab: undefined }
    suite.status = 'YANKED'
    suite.allowedActions = ['CREATE_VERSION']
  })

  it('keeps a yanked Suite in dashboard context with four management tabs', () => {
    render(<SuiteManagementPage />)

    expect(screen.getByText('Yanked demo')).not.toBeNull()
    expect(screen.getByText('@global/demo-yanked')).not.toBeNull()
    expect(screen.getByText(/suite\.statusLabel\.YANKED/)).not.toBeNull()
    expect(screen.getAllByRole('tab')).toHaveLength(4)
    expect(screen.queryByText('suite.management.publishChecklist')).toBeNull()
    expect(screen.getByText('suite.management.membersTitle')).not.toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'suite.createVersion' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/demo-yanked/new-version',
      search: { sourceVersion: '1.0.0' },
    })
  })

  it('shows the publishing checklist only while the current version can be submitted', () => {
    suite.status = 'DRAFT'
    suite.allowedActions = ['EDIT', 'SUBMIT']

    render(<SuiteManagementPage />)

    expect(screen.getByText('suite.management.publishChecklist')).not.toBeNull()
    expect(screen.getByRole('button', { name: 'suite.submitReview' })).not.toBeNull()
  })

  it('shows the member snapshot separately with an exact-version skill link', () => {
    mocks.search = { version: '1.0.0', tab: 'members' }
    render(<SuiteManagementPage />)

    expect(screen.getAllByText('@global/member-one').length).toBeGreaterThan(0)
    expect(screen.getByText('suite.entrySkill')).not.toBeNull()
    expect(screen.getAllByRole('link').some(link => link.getAttribute('href')?.includes('version=1.0.0'))).toBe(true)
    expect(screen.queryByText('suite.management.publishChecklist')).toBeNull()
  })

  it('shows version release metadata in the dedicated version tab', () => {
    mocks.search = { version: '1.0.0', tab: 'versions' }
    render(<SuiteManagementPage />)

    expect(screen.getAllByText('Initial release')).toHaveLength(1)
    expect(screen.getAllByText('Suite Owner')).toHaveLength(1)
    expect(screen.queryByText('suite.versionDetails')).toBeNull()
    expect(screen.getByText('suite.memberInformation')).not.toBeNull()
  })
})
