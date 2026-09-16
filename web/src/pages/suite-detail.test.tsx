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
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: ({ content }: { content: string }) => <div data-testid="suite-overview">{content}</div>,
}))
vi.mock('@/features/suite/suite-management-actions', () => ({ SuiteManagementActions: () => null }))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteDetail: () => mocks.detail,
  useSuiteVersions: () => ({ data: [] }),
  useSubmitSuite: () => mocks.submit,
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
    window.__SKILLHUB_RUNTIME_CONFIG__ = originalRuntimeConfig
  })

  it('adds entry skill guidance to the overview and keeps the full member grid separate', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)

    expect(screen.getByTestId('suite-overview').textContent).toContain('Run the entry skill first.')
    expect(screen.getByText('suite.startWithEntry')).not.toBeNull()
    expect(screen.getByText('suite.startWithEntryDescription')).not.toBeNull()
    expect(screen.getByText('Medical Records')).not.toBeNull()
    expect(screen.getByText('@global/medical-records@1.0.0')).not.toBeNull()
    expect(screen.getByRole('link', { name: 'suite.viewEntrySkill' }).getAttribute('href'))
      .toContain('/space/global/medical-records')
    expect(screen.queryByText('@global/deleted-helper')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTab' }))

    expect(screen.getByText('Medical Records')).not.toBeNull()
    expect(screen.getByText('Structures medical records.')).not.toBeNull()
    expect(screen.getByRole('link', { name: 'suite.viewMember' }).getAttribute('href'))
      .toContain('/space/global/medical-records')
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

    expect(screen.getByText('@global/medical-records@1.0.0')).not.toBeNull()
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
