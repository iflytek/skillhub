/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuite, SkillSuiteVersion } from '@/api/types'
import { SuiteDetailPage } from './suite-detail'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  detail: { data: undefined as SkillSuite | undefined, isLoading: false, error: null as Error | null },
  versions: [] as SkillSuiteVersion[],
  submit: { mutateAsync: vi.fn(), isPending: false },
  suiteLabels: [] as Array<{ slug: string; type: string; displayName: string }>,
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
    search?: { returnTo?: string; version?: string }
    className?: string
  }) => (
    <a
      href={`/space/${params.namespace}/${params.slug}?version=${encodeURIComponent(search?.version ?? '')}&returnTo=${encodeURIComponent(search?.returnTo ?? '')}`}
      className={props.className}
    >
      {children}
    </a>
  ),
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en', resolvedLanguage: 'en' } }),
}))
vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: ({ content }: { content: string }) => <div data-testid="suite-overview">{content}</div>,
}))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: null, hasRole: () => false }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({
  useSuiteLabels: () => ({ data: mocks.suiteLabels }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteDetail: () => mocks.detail,
  useSuiteVersions: () => ({ data: mocks.versions }),
  useSubmitSuite: () => mocks.submit,
}))
vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSkillFile: (...args: unknown[]) => mocks.entryGuideCalls(...args),
}))

function suite(): SkillSuite {
  return {
    id: 1,
    versionId: 10,
    namespace: 'global',
    slug: 'care-workflow',
    displayName: 'Care Workflow',
    createdBy: 'owner-1',
    createdAt: '2026-09-15T10:00:00Z',
    publishedAt: '2026-09-15T11:00:00Z',
    changelog: 'Initial workflow release',
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

function versions(): SkillSuiteVersion[] {
  return [{
    id: 10,
    version: '1.0.0',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    createdBy: 'owner-1',
    createdByName: 'Suite Owner',
    createdAt: '2026-09-15T10:00:00Z',
    publishedAt: '2026-09-15T11:00:00Z',
    changelog: 'Initial workflow release',
  }]
}

describe('SuiteDetailPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.suiteLabels = []
    mocks.versions = []
    window.__SKILLHUB_RUNTIME_CONFIG__ = originalRuntimeConfig
  })

  it('shows Suite labels and separates overview, members, and versions', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }
    mocks.versions = versions()
    mocks.suiteLabels = [{ slug: 'healthcare', type: 'RECOMMENDED', displayName: '医疗健康' }]

    render(<SuiteDetailPage />)

    expect(screen.getByText('医疗健康')).not.toBeNull()
    expect(screen.getAllByRole('tab')).toHaveLength(3)
    expect(screen.getByTestId('suite-overview').textContent).toContain('Run the entry skill first.')
    expect(screen.queryByText('Structures medical records.')).toBeNull()
  })

  it('does not fetch or embed the Entry Skill SKILL.md in the Suite overview', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)

    expect(mocks.entryGuideCalls).not.toHaveBeenCalled()
    expect(screen.getByText('suite.startWithEntry')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.viewPinnedVersion' }))
    expect(mocks.navigate).toHaveBeenCalledWith(expect.objectContaining({
      to: '/space/$namespace/$slug',
      search: expect.objectContaining({ version: '1.0.0' }),
    }))
  })

  it('links a browsable member to its exact pinned version and keeps a deleted member as text', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTabShort' }))

    const pinnedLinks = screen.getAllByRole('link').filter(link => link.getAttribute('href')?.includes('medical-records'))
    expect(pinnedLinks.length).toBeGreaterThan(0)
    expect(pinnedLinks.every(link => link.getAttribute('href')?.includes('version=1.0.0'))).toBe(true)
    expect(screen.getAllByText('@global/deleted-helper').every(label => label.closest('a') === null)).toBe(true)
    expect(screen.getByText('suite.blockingReasons.DELETED')).not.toBeNull()
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
    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTabShort' }))

    expect(screen.getByText(`suite.blockingReasons.${blockingReason}`)).not.toBeNull()
    expect(screen.queryByRole('link')).toBeNull()
  })

  it('shows release metadata and the member snapshot in version history', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }
    mocks.versions = versions()

    render(<SuiteDetailPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'suite.versionsTab' }))

    expect(screen.getAllByText('Initial workflow release')).toHaveLength(1)
    expect(screen.getAllByText('Suite Owner')).toHaveLength(1)
    expect(screen.queryByText('suite.versionDetails')).toBeNull()
    expect(screen.getByText('suite.memberInformation')).not.toBeNull()
    expect(screen.getAllByText('@global/medical-records').length).toBeGreaterThan(0)
  })

  it('shows installation actions only for a shell-safe version', () => {
    const safeSuite = suite()
    mocks.detail = { data: safeSuite, isLoading: false, error: null }
    const { rerender } = render(<SuiteDetailPage />)
    expect(screen.getByRole('button', { name: 'suite.installSuite' })).not.toBeNull()
    expect(screen.getByRole('button', { name: 'suite.copyInstallCommand' })).not.toBeNull()

    safeSuite.version = '1.0.0; touch pwned'
    mocks.detail = { data: safeSuite, isLoading: false, error: null }
    rerender(<SuiteDetailPage />)
    expect(screen.queryByRole('button', { name: 'suite.installSuite' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.copyInstallCommand' })).toBeNull()
  })
})
