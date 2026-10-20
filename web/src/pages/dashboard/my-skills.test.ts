import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createInstance, type i18n } from 'i18next'
import zh from '@/i18n/locales/zh.json'
import en from '@/i18n/locales/en.json'

const navigateMock = vi.fn()
const buttonRecords: Array<{ label: string; onClick?: ((event?: { stopPropagation: () => void }) => void) | undefined }> = []
const useMySkillsMock = vi.fn()
const searchMock: { filter?: string } = {}
let isSuperAdmin = false
let renderI18n: i18n | undefined

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useLocation: () => ({ pathname: '/dashboard/skills' }),
  useSearch: () => searchMock,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => renderI18n ? String(renderI18n.t(key, options)) : key,
    }),
  }
})

vi.mock('@/features/skill/skill-visibility-dialog', () => ({ SkillVisibilityDialog: () => createElement('span', null, 'visibility-control') }))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ hasRole: (role: string) => role === 'SUPER_ADMIN' && isSuperAdmin }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({
    children,
    onClick,
  }: {
    children?: ReactNode
    onClick?: (event?: { stopPropagation: () => void }) => void
  }) => {
    const label = Array.isArray(children) ? children.join('') : String(children ?? '')
    buttonRecords.push({ label, onClick })
    return createElement('button', null, children)
  },
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => createElement('div', null, children),
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: () => createElement('div', null, 'empty-state'),
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ actions }: { actions?: ReactNode }) => createElement('div', null, actions),
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => null,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useArchiveSkill: () => ({ mutateAsync: vi.fn() }),
  useUnarchiveSkill: () => ({ mutateAsync: vi.fn() }),
  useWithdrawSkillReview: () => ({ mutateAsync: vi.fn() }),
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMySkills: () => useMySkillsMock(),
  useSubmitPromotion: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({ data: [] }),
}))

vi.mock('@/shared/hooks/use-debounce', () => ({
  useDebounce: (value: string) => value,
}))

vi.mock('@/features/admin/use-admin-skills', () => ({
  useRestoreHiddenSkill: () => ({ mutateAsync: vi.fn() }),
}))

vi.mock('@/shared/lib/skill-lifecycle', () => ({
  getHeadlineVersion: () => ({ id: 11, version: '1.0.0', status: 'PUBLISHED' }),
  getPublishedVersion: () => ({ id: 11, version: '1.0.0', status: 'PUBLISHED' }),
  getOwnerPreviewVersion: (skill: { ownerPreviewVersion?: unknown }) => skill.ownerPreviewVersion ?? null,
  hasPendingOwnerPreview: (skill: { ownerPreviewVersion?: { status: string } }) => skill.ownerPreviewVersion?.status === 'PENDING_REVIEW',
}))

vi.mock('@/shared/lib/number-format', () => ({
  formatCompactCount: (v: number) => String(v),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/client', () => ({
  buildApiUrl: (url: string) => url,
  ApiError: class ApiError extends Error {
    serverMessageKey?: string
  },
}))

vi.mock('@/features/skill/skill-avatar-manager', () => ({
  SkillAvatarManager: ({ namespace, slug, previewOnly }: { namespace: string; slug: string; previewOnly?: boolean }) =>
    createElement('div', { 'data-submitted-avatar': `${namespace}/${slug}`, 'data-read-only': previewOnly }),
}))

import { MySkillsPage } from './my-skills'

function findButton(label: string) {
  const record = buttonRecords.find((item) => item.label === label)
  if (!record) {
    throw new Error(`Missing button: ${label}`)
  }
  return record
}

describe('MySkillsPage', () => {
  beforeEach(() => {
    renderI18n = undefined
    navigateMock.mockReset()
    buttonRecords.length = 0
    delete searchMock.filter
    isSuperAdmin = false
    useMySkillsMock.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            displayName: 'Team Agent',
            summary: 'summary',
            namespace: 'team-ai',
            slug: 'team-agent',
            downloadCount: 42,
            status: 'PUBLISHED',
            visibility: 'PRIVATE',
            canSubmitPromotion: false,
          },
        ],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })
  })

  it.each(['ACTIVE', 'ARCHIVED'])('keeps a removed owner’s %s skill readable without write actions', (status) => {
    useMySkillsMock.mockReturnValue({ data: { items: [{
      id: 1, displayName: 'Owned skill', namespace: 'team-ai', slug: 'owned',
      status, visibility: 'PRIVATE', downloadCount: 0, canSubmitPromotion: false,
      managementState: { ownerReadOnly: true },
      publishedVersion: { id: 1, version: '1.0.0', status: 'PUBLISHED' },
      ownerPreviewVersion: { id: 2, version: '1.1.0', status: 'PENDING_REVIEW' },
    }], total: 1, page: 0, size: 10 }, isLoading: false })
    const markup = renderToStaticMarkup(createElement(MySkillsPage))
    expect(markup).toContain('Owned skill')
    expect(markup).toContain('mySkills.ownerReadOnly')
    if (status === 'ACTIVE') expect(markup).toContain('data-submitted-avatar')
    expect(markup).not.toContain('visibility-control')
    for (const label of ['update', 'withdrawReview', 'archive', 'unarchive', 'promoteToGlobal']) {
      expect(buttonRecords.some(button => button.label === `mySkills.${label}`)).toBe(false)
    }
  })

  it('renders a localized display name independently of the package slug', () => {
    useMySkillsMock.mockReturnValue({
      data: {
        items: [{
          id: 1,
          displayName: '示例技能',
          namespace: 'team-ai',
          slug: 'example-skill',
          downloadCount: 0,
          status: 'ACTIVE',
          visibility: 'PUBLIC',
        }],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })

    const markup = renderToStaticMarkup(createElement(MySkillsPage))
    expect(markup).toMatch(/<h3[^>]*>示例技能<\/h3>/)
  })

  it('navigates to publish page with namespace and visibility when update is clicked', () => {
    renderToStaticMarkup(createElement(MySkillsPage))

    const stopPropagation = vi.fn()
    findButton('mySkills.update').onClick?.({ stopPropagation })

    expect(stopPropagation).toHaveBeenCalledTimes(1)
    expect(navigateMock).toHaveBeenCalledWith({
      to: '/dashboard/publish',
      search: {
        namespace: 'team-ai',
        updateSkill: 'team-agent',
        updateName: 'Team Agent',
        visibility: 'PRIVATE',
      },
    })
  })

  it('displays the active avatar beside the skill name and exposes only the existing update entry', () => {
    useMySkillsMock.mockReturnValue({ data: { items: [{ id: 1, displayName: 'Team Agent', namespace: 'team-ai', slug: 'team-agent', avatarUrl: '/skill-avatar.png', status: 'PUBLISHED', downloadCount: 0 }], total: 1 }, isLoading: false })
    const html = renderToStaticMarkup(createElement(MySkillsPage))
    expect(html).toContain('src="/skill-avatar.png"')
    expect(buttonRecords.some(button => button.label === 'skillAvatar.manage')).toBe(false)
    expect(buttonRecords.some(button => button.label === 'mySkills.update')).toBe(true)
  })

  it('offers an owner-only submitted avatar preview for active skills without a second editing action', () => {
    useMySkillsMock.mockReturnValue({ data: { items: [{ id: 1, displayName: 'Team Agent', namespace: 'team-ai', slug: 'team-agent', avatarUrl: '/skill-avatar.png', status: 'ACTIVE', downloadCount: 0 }], total: 1 }, isLoading: false })
    const html = renderToStaticMarkup(createElement(MySkillsPage))
    expect(html).toContain('data-submitted-avatar="team-ai/team-agent"')
    expect(html).toContain('data-read-only="true"')
    expect(html).toContain('src="/skill-avatar.png"')
    expect(buttonRecords.some(button => button.label === 'mySkills.update')).toBe(true)
    expect(buttonRecords.some(button => button.label === 'skillAvatar.manage')).toBe(false)
  })

  it('does not render update action for archived skills', () => {
    useMySkillsMock.mockReturnValue({
      data: {
        items: [
          {
            id: 2,
            displayName: 'Archived Agent',
            summary: 'summary',
            namespace: 'team-ai',
            slug: 'archived-agent',
            downloadCount: 7,
            status: 'ARCHIVED',
            visibility: 'PUBLIC',
            canSubmitPromotion: false,
          },
        ],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })

    renderToStaticMarkup(createElement(MySkillsPage))

    expect(buttonRecords.some((button) => button.label === 'mySkills.update')).toBe(false)
  })

  it('falls back to public visibility when the skill card data has no visibility field', () => {
    useMySkillsMock.mockReturnValue({
      data: {
        items: [
          {
            id: 3,
            displayName: 'Default Visibility Agent',
            summary: 'summary',
            namespace: 'team-ai',
            slug: 'default-visibility-agent',
            downloadCount: 9,
            status: 'PUBLISHED',
            canSubmitPromotion: false,
          },
        ],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })

    renderToStaticMarkup(createElement(MySkillsPage))

    findButton('mySkills.update').onClick?.({ stopPropagation: vi.fn() })

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/dashboard/publish',
      search: {
        namespace: 'team-ai',
        updateSkill: 'default-visibility-agent',
        updateName: 'Default Visibility Agent',
        visibility: 'PUBLIC',
      },
    })
  })

  it('shows a direct restore action instead of owner actions in the hidden governance view', () => {
    searchMock.filter = 'HIDDEN'
    isSuperAdmin = true

    renderToStaticMarkup(createElement(MySkillsPage))

    expect(buttonRecords.some((button) => button.label === 'mySkills.restoreHidden')).toBe(true)
    expect(buttonRecords.some((button) => button.label === 'mySkills.update')).toBe(false)
    expect(buttonRecords.some((button) => button.label === 'mySkills.archive')).toBe(false)
  })

  it('does not render the restore action for a non-super-admin even when the hidden filter is in the URL', () => {
    searchMock.filter = 'HIDDEN'

    renderToStaticMarkup(createElement(MySkillsPage))

    expect(buttonRecords.some((button) => button.label === 'mySkills.restoreHidden')).toBe(false)
  })

  it('shows pending metadata with exactly one status instead of the published title', () => {
    useMySkillsMock.mockReturnValue({ data: { items: [{
      id: 1, namespace: 'team-ai', slug: 'stable-slug', displayName: '旧名称', summary: '旧摘要',
      status: 'ACTIVE', downloadCount: 0,
      ownerPreviewVersion: { id: 12, version: '2.0.0', status: 'PENDING_REVIEW', displayName: '待审新名称', summary: '待审新摘要' },
    }], total: 1, page: 0, size: 10 }, isLoading: false })
    const html = renderToStaticMarkup(createElement(MySkillsPage))
    expect(html).toContain('待审新名称')
    expect(html).toContain('待审新摘要')
    expect(html).not.toContain('旧名称')
    expect(html.match(/class="status-pill[^"]*"/g)).toHaveLength(1)
    expect(html).toContain('mySkills.statusUpdatePendingReview')
    expect(html).not.toContain('mySkills.statusPublished')
  })

  it.each(['zh', 'en'])('shows effective private access and the pending public request after refresh in %s', async language => {
    renderI18n = createInstance()
    await renderI18n.init({ lng: language, fallbackLng: false, resources: { zh: { translation: zh }, en: { translation: en } }, interpolation: { escapeValue: false } })
    const item = { id: 1, displayName: 'Owned skill', namespace: 'team-ai', slug: 'owned', status: 'ACTIVE',
      visibility: 'PRIVATE', downloadCount: 0, managementState: { requestedVisibility: 'PUBLIC', reviewTaskId: 42 } }
    useMySkillsMock.mockReturnValue({ data: { items: [item], total: 1, page: 0, size: 10 }, isLoading: false })
    const html = renderToStaticMarkup(createElement(MySkillsPage))
    expect(html).toContain(renderI18n.t('skillVisibility.current', { visibility: renderI18n.t('skillVisibility.PRIVATE') }))
    expect(html).toContain(renderI18n.t('skillVisibility.pendingList', { visibility: renderI18n.t('skillVisibility.PUBLIC') }))
    expect(html.match(/class="status-pill[^"]*"/g)).toHaveLength(1)
    item.managementState = { requestedVisibility: '', reviewTaskId: 0 }
    const afterReview = renderToStaticMarkup(createElement(MySkillsPage))
    expect(afterReview).not.toContain('role="status"')
  })

  it('distinguishes actual promotion links and update action without merging the two skills', () => {
    useMySkillsMock.mockReturnValue({ data: { items: [
      { id: 1, displayName: 'Team', namespace: 'team-ai', slug: 'one', status: 'ACTIVE', downloadCount: 0,
        canSubmitPromotion: true, managementState: { promotionRole: 'TEAM_SOURCE', relatedNamespace: 'global', relatedSlug: 'copy' } },
      { id: 2, displayName: 'Global', namespace: 'global', slug: 'copy', status: 'ACTIVE', downloadCount: 0,
        managementState: { promotionRole: 'GLOBAL_COPY' } },
    ], total: 2, page: 0, size: 10 }, isLoading: false })
    const html = renderToStaticMarkup(createElement(MySkillsPage))
    expect(html).toContain('mySkills.promotionRoles.TEAM_SOURCE')
    expect(html).toContain('mySkills.promotionRoles.GLOBAL_COPY')
    expect(html).toContain('mySkills.viewGlobalCopy')
    expect(html).not.toContain('mySkills.viewTeamSource')
    expect(buttonRecords.some(button => button.label === 'mySkills.updateGlobalVersion')).toBe(true)
    expect(buttonRecords.some(button => button.label === 'mySkills.promoteToGlobal')).toBe(false)
  })

  it('exports a named component function', () => {
    expect(typeof MySkillsPage).toBe('function')
  })
})
