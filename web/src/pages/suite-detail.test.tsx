/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuite } from '@/api/types'
import { SuiteDetailPage } from './suite-detail'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  detail: { data: undefined as SkillSuite | undefined, isLoading: false, error: null as Error | null },
  submit: { mutateAsync: vi.fn(), isPending: false },
  suiteLabels: [] as Array<{ slug: string; type: string; displayName: string }>,
  entryGuide: { data: undefined as string | undefined, isLoading: false, error: null as Error | null },
  entryGuideCalls: vi.fn(),
}))
const originalRuntimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useParams: () => ({ namespace: 'global', slug: 'care-workflow' }),
  useSearch: () => ({ version: '1.0.0' }),
  Link: ({ children, params, search, ...props }: {
    children: ReactNode
    params: { namespace: string; slug: string }
    search?: { returnTo?: string }
    className?: string
    'aria-label'?: string
  }) => (
    <a
      href={`/space/${params.namespace}/${params.slug}?returnTo=${encodeURIComponent(search?.returnTo ?? '')}`}
      className={props.className}
      aria-label={props['aria-label']}
    >
      {children}
    </a>
  ),
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en', resolvedLanguage: 'en' } }),
}))
vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: ({ content }: { content: string }) => (
    <div data-testid={content.includes('Pinned guide') ? 'entry-guide' : 'suite-overview'}>{content}</div>
  ),
}))
vi.mock('@/features/suite/suite-management-actions', () => ({ SuiteManagementActions: () => null }))
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ user: null, hasRole: () => false }),
}))
vi.mock('@/shared/hooks/use-label-queries', () => ({
  useSuiteLabels: () => ({ data: mocks.suiteLabels }),
  useSkillLabels: () => ({ data: [] }),
  useVisibleLabels: () => ({ data: [], isLoading: false }),
  useAdminLabelDefinitions: () => ({ data: [], isLoading: false }),
  useAttachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useAttachSuiteLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSuiteLabel: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteDetail: () => mocks.detail,
  useSuiteVersions: () => ({ data: [] }),
  useSubmitSuite: () => mocks.submit,
}))
vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSkillFile: (...args: unknown[]) => {
    mocks.entryGuideCalls(...args)
    return mocks.entryGuide
  },
}))

function suite(): SkillSuite {
  return {
    id: 1,
    versionId: 10,
    namespace: 'global',
    slug: 'care-workflow',
    displayName: 'Care Workflow',
    summary: 'A short description for discovery.',
    overview: '## Workflow\n\nRun the entry skill first.',
    version: '1.0.0',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    suiteStatus: 'ACTIVE',
    hidden: false,
    allowedActions: [],
    available: false,
    members: [
      {
        skillId: 11,
        skillVersionId: 110,
        namespace: 'global',
        slug: 'medical-records',
        displayName: 'Medical Records',
        summary: 'Structures medical records.',
        version: '1.0.0',
        fingerprint: 'sha256:available',
        position: 0,
        entry: true,
        browsable: true,
      },
      {
        namespace: 'global',
        slug: 'deleted-helper',
        version: '2.0.0',
        fingerprint: 'sha256:deleted',
        position: 1,
        entry: false,
        browsable: false,
        blockingReason: 'DELETED',
      },
    ],
  }
}

describe('SuiteDetailPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.suiteLabels = []
    mocks.entryGuide = { data: undefined, isLoading: false, error: null }
    window.__SKILLHUB_RUNTIME_CONFIG__ = originalRuntimeConfig
  })

  it('shows labels directly associated with the Suite', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }
    mocks.suiteLabels = [{ slug: 'healthcare', type: 'RECOMMENDED', displayName: '医疗健康' }]

    render(<SuiteDetailPage />)

    expect(screen.getByText('医疗健康')).not.toBeNull()
  })

  it('adds entry skill guidance to the overview and keeps the full member grid separate', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)

    expect(screen.getByTestId('suite-overview').textContent).toContain('Run the entry skill first.')
    expect(screen.getByText('suite.startWithEntry')).not.toBeNull()
    expect(screen.getByText('suite.startWithEntryDescription')).not.toBeNull()
    expect(screen.getByText('Medical Records')).not.toBeNull()
    expect(screen.getAllByText('@global/medical-records@1.0.0')).toHaveLength(2)
    expect(screen.getByRole('link', { name: 'suite.viewEntrySkill' }).getAttribute('href'))
      .toContain('/space/global/medical-records')
    expect(screen.queryByText('@global/deleted-helper')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTab' }))

    expect(screen.getByText('Medical Records')).not.toBeNull()
    expect(screen.getByText('Structures medical records.')).not.toBeNull()
    expect(screen.getByRole('link', { name: 'suite.viewMember' }).getAttribute('href'))
      .toContain('/space/global/medical-records')
  })

  it('loads the pinned Entry Skill SKILL.md only after the user expands it', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }
    mocks.entryGuide = { data: '## Pinned guide\n\nExact version content.', isLoading: false, error: null }

    render(<SuiteDetailPage />)

    expect(screen.queryByTestId('entry-guide')).toBeNull()
    expect(mocks.entryGuideCalls).toHaveBeenLastCalledWith(
      'global', 'medical-records', '1.0.0', 'SKILL.md', false,
    )

    fireEvent.click(screen.getByRole('button', { name: /suite.entryGuideTitle/ }))

    expect(mocks.entryGuideCalls).toHaveBeenLastCalledWith(
      'global', 'medical-records', '1.0.0', 'SKILL.md', true,
    )
    expect(screen.getByTestId('entry-guide').textContent).toContain('Exact version content.')
    expect(screen.getByTestId('suite-overview')).not.toBeNull()
  })

  it('keeps the Suite overview visible when pinned Entry Skill instructions fail', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }
    mocks.entryGuide = { data: undefined, isLoading: false, error: new Error('forbidden') }

    render(<SuiteDetailPage />)
    fireEvent.click(screen.getByRole('button', { name: /suite.entryGuideTitle/ }))

    expect(screen.getByRole('alert').textContent).toBe('suite.entryGuideLoadFailed')
    expect(screen.getByTestId('suite-overview')).not.toBeNull()
  })

  it('keeps a deleted member as a non-navigable historical card', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTab' }))

    const deletedLabels = screen.getAllByText('@global/deleted-helper')
    expect(deletedLabels.every((label) => label.closest('a') === null)).toBe(true)
    expect(screen.getAllByRole('link')).toHaveLength(1)
  })

  it('keeps an unavailable entry skill visible but non-navigable in the overview', () => {
    const blockedEntrySuite = suite()
    blockedEntrySuite.members[0] = {
      ...blockedEntrySuite.members[0],
      browsable: false,
      blockingReason: 'SKILL_HIDDEN',
    }
    mocks.detail = { data: blockedEntrySuite, isLoading: false, error: null }

    render(<SuiteDetailPage />)

    expect(screen.getAllByText('@global/medical-records@1.0.0')).toHaveLength(2)
    expect(screen.getByText('suite.blockingReasons.SKILL_HIDDEN')).not.toBeNull()
    expect(screen.queryByRole('link', { name: 'suite.viewEntrySkill' })).toBeNull()
  })

  it.each([
    'DELETED',
    'NAMESPACE_ARCHIVED',
    'NAMESPACE_FROZEN',
    'SKILL_HIDDEN',
    'SKILL_ARCHIVED',
    'VERSION_UNAVAILABLE',
    'VISIBILITY_INCOMPATIBLE',
  ] as const)('keeps a member blocked by %s non-navigable', (blockingReason) => {
    const blockedSuite = suite()
    blockedSuite.members = [{
      namespace: 'global',
      slug: `blocked-${blockingReason.toLowerCase()}`,
      version: '1.0.0',
      fingerprint: 'sha256:blocked',
      position: 0,
      entry: false,
      browsable: false,
      blockingReason,
    }]
    mocks.detail = { data: blockedSuite, isLoading: false, error: null }

    render(<SuiteDetailPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTab' }))

    expect(screen.getByText(`suite.blockingReasons.${blockingReason}`)).not.toBeNull()
    expect(screen.queryByRole('link', { name: 'suite.viewMember' })).toBeNull()
  })

  it('places Suite metadata and installation in the detail sidebar', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      appBaseUrl: 'https://registry.internal.example/skillhub',
    }
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)

    const sidebar = screen.getByRole('complementary', { name: 'suite.detailsSidebar' })
    expect(within(sidebar).getByText('v1.0.0')).not.toBeNull()
    expect(within(sidebar).getByText('suite.installCommand')).not.toBeNull()
    expect(within(sidebar).getByText(
      'skillhub suite install @global/care-workflow --version 1.0.0 --registry https://registry.internal.example/skillhub',
    )).not.toBeNull()
    expect(within(sidebar).getByLabelText('suite.copyInstallCommand')).not.toBeNull()
  })

  it('does not expose a copyable shell command for an unsafe legacy version', () => {
    const unsafeSuite = suite()
    unsafeSuite.version = '1.0.0; touch pwned'
    mocks.detail = { data: unsafeSuite, isLoading: false, error: null }

    render(<SuiteDetailPage />)

    const sidebar = screen.getByRole('complementary', { name: 'suite.detailsSidebar' })
    expect(within(sidebar).getByRole('alert').textContent)
      .toBe('skillDetail.installCommandUnsafeVersion')
    expect(within(sidebar).queryByLabelText('suite.copyInstallCommand')).toBeNull()
  })
})
