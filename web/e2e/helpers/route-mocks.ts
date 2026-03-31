import type { Page, Route } from '@playwright/test'
import {
  adminUser,
  auditLogItem,
  fulfillJson,
  fulfillText,
  labelDefinition,
  managedNamespace,
  namespace,
  namespaceCandidateUser,
  namespaceMember,
  paged,
  promotionTask,
  reviewTask,
  reviewSkillDetail,
  skill,
  skillDetail,
  skillFile,
  skillReport,
  skillVersion,
  type TestAdminUser,
  type TestAuditLogItem,
  type TestLabelDefinition,
  type TestNamespaceCandidateUser,
  type TestNamespaceMember,
  type TestPromotionTask,
  type TestReviewSkillDetail,
  type TestReviewTask,
  type TestManagedNamespace,
  type TestNamespace,
  type TestSkillReport,
  type TestSkillDetail,
  type TestSkillFile,
  type TestSkillSummary,
  type TestSkillVersion,
  type TestUser,
} from './api-fixtures'
import { mockAuthState, type oauthOnlyMethods } from './auth-fixtures'

type SearchHandler =
  (url: URL) => ReturnType<typeof paged<TestSkillSummary>> | Promise<ReturnType<typeof paged<TestSkillSummary>>>

type MockAppShellOptions = {
  authenticated?: boolean
  currentUser?: TestUser
  userRole?: string
  authMethods?: ReturnType<typeof oauthOnlyMethods>
}

type MockSearchPageOptions = MockAppShellOptions & {
  searchHandler: SearchHandler
}

type MockLandingPageOptions = MockAppShellOptions & {
  popularSkills?: TestSkillSummary[]
  latestSkills?: TestSkillSummary[]
}

type MockNamespacePageOptions = MockAppShellOptions & {
  namespaceData?: TestNamespace | null
  skills?: TestSkillSummary[]
}

type MockSkillDetailPageOptions = MockAppShellOptions & {
  namespace: string
  slug: string
  detail?: TestSkillDetail
  versions?: TestSkillVersion[]
  files?: TestSkillFile[]
  fileContents?: Record<string, string>
  failReadme?: boolean
}

type WorkspaceToken = {
  id: number
  name: string
  tokenPrefix: string
  createdAt: string
  lastUsedAt?: string | null
  expiresAt?: string | null
}

type WorkspaceProfile = {
  displayName: string
  avatarUrl: string | null
  email: string | null
  pendingChanges: {
    status: string
    changes: Record<string, string>
    reviewComment: string | null
    createdAt: string
  } | null
  fieldPolicies: Record<string, { editable: boolean; requiresReview: boolean }>
}

type WorkspaceNotification = {
  id: number
  category: 'PUBLISH' | 'REVIEW' | 'PROMOTION' | 'REPORT'
  eventType: string
  title: string
  bodyJson?: string
  status: 'UNREAD' | 'READ'
  createdAt: string
  targetRoute?: string
  entityType?: string
  entityId?: number
}

type MockTokenPageOptions = MockAppShellOptions & {
  tokens?: WorkspaceToken[]
}

type MockDashboardPageOptions = MockTokenPageOptions & {
  mySkills?: TestSkillSummary[]
}

type MockProfilePageOptions = MockAppShellOptions & {
  profile?: WorkspaceProfile
  updateResult?: {
    status: string
    appliedFields?: Record<string, string>
    pendingFields?: Record<string, string>
  }
}

type MockSecurityPageOptions = MockAppShellOptions & {
  changePasswordSucceeds?: boolean
  changePasswordStatus?: number
  changePasswordMessage?: string
}

type MockNotificationsPageOptions = MockAppShellOptions & {
  notifications?: WorkspaceNotification[]
}

type MockPublishPageOptions = MockDashboardPageOptions & {
  namespaces?: TestManagedNamespace[]
  publishResult?: {
    skillId: number
    namespace: string
    slug: string
    version: string
    status: string
    fileCount: number
    totalSize: number
  }
  publishError?: {
    status: number
    msg: string
  }
}

type MockMySkillsPageOptions = MockDashboardPageOptions & {
  namespaces?: TestManagedNamespace[]
  promotionError?: {
    status: number
    msg: string
  }
}

type MockMyNamespacesPageOptions = MockAppShellOptions & {
  namespaces?: TestManagedNamespace[]
}

type MockStarsPageOptions = MockAppShellOptions & {
  stars?: TestSkillSummary[]
}

type MockNotificationSettingsPageOptions = MockAppShellOptions & {
  preferences?: Array<{
    category: string
    channel: string
    enabled: boolean
  }>
}

type MockNamespaceReviewsPageOptions = MockAppShellOptions & {
  namespaceData?: TestManagedNamespace | null
  reviews?: TestReviewTask[]
}

type MockNamespaceMembersPageOptions = MockAppShellOptions & {
  namespaceData?: TestManagedNamespace | null
  myNamespaces?: TestManagedNamespace[]
  members?: TestNamespaceMember[]
  candidates?: TestNamespaceCandidateUser[]
}

type MockReviewsPageOptions = MockAppShellOptions & {
  reviews?: TestReviewTask[]
}

type MockPromotionsPageOptions = MockAppShellOptions & {
  promotions?: TestPromotionTask[]
}

type MockReportsPageOptions = MockAppShellOptions & {
  reports?: TestSkillReport[]
}

type MockReviewDetailPageOptions = MockAppShellOptions & {
  review?: TestReviewTask | null
  reviewSkillDetail?: TestReviewSkillDetail
  fileContents?: Record<string, string>
}

type MockAdminUsersPageOptions = MockAppShellOptions & {
  users?: TestAdminUser[]
}

type MockAuditLogPageOptions = MockAppShellOptions & {
  logs?: TestAuditLogItem[]
}

type MockAdminLabelsPageOptions = MockAppShellOptions & {
  definitions?: TestLabelDefinition[]
}

type MockCliAuthPageOptions = MockAppShellOptions & {
  createTokenResult?: {
    id: number
    name: string
    token: string
    tokenPrefix: string
    createdAt: string
  }
  createTokenError?: {
    status: number
    msg: string
  }
}

async function fulfillNotFound(route: Route, msg: string = 'Not Found') {
  await fulfillJson(route, null, { status: 404, msg })
}

export async function mockAppShell(page: Page, options: MockAppShellOptions = {}) {
  await mockAuthState(page, options)

  await page.route('**/api/web/labels', async (route) => {
    await fulfillJson(route, [
      { slug: 'official', type: 'RECOMMENDED', displayName: 'Official' },
      { slug: 'featured', type: 'RECOMMENDED', displayName: 'Featured' },
    ])
  })

  await page.route('**/api/web/notifications/unread-count**', async (route) => {
    await fulfillJson(route, { count: 0 })
  })

  await page.route('**/api/web/notifications?**', async (route) => {
    await fulfillJson(route, paged([], { size: 5 }))
  })

  await page.route('**/api/web/skills/*/star', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, false)
      return
    }

    await fulfillJson(route, {})
  })

  await page.route('**/api/web/skills/*/rating', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, { score: 0, rated: false })
      return
    }

    await fulfillJson(route, {})
  })

  await page.route('**/api/v1/skills/*/versions/*/security-audit', async (route) => {
    await fulfillNotFound(route)
  })
}

export async function mockSearchPage(page: Page, options: MockSearchPageOptions) {
  await mockAppShell(page, options)

  await page.route('**/api/web/skills?**', async (route) => {
    try {
      const url = new URL(route.request().url())
      await fulfillJson(route, await options.searchHandler(url))
    } catch (error) {
      const errorCode = error instanceof Error && error.message === 'internetdisconnected'
        ? 'internetdisconnected'
        : 'failed'
      await route.abort(errorCode)
    }
  })
}

export async function mockLandingPage(page: Page, options: MockLandingPageOptions = {}) {
  const popularSkills = options.popularSkills ?? [skill(1, 'Popular Agent'), skill(2, 'Team Assistant')]
  const latestSkills = options.latestSkills ?? [skill(3, 'Latest Release'), skill(4, 'Fresh Workflow')]

  await mockSearchPage(page, {
    ...options,
    searchHandler: (url) => {
      const sort = url.searchParams.get('sort') ?? 'newest'
      const size = Number(url.searchParams.get('size') ?? '6')

      if (sort === 'downloads') {
        return paged(popularSkills, { size })
      }

      if (sort === 'newest') {
        return paged(latestSkills, { size })
      }

      return paged(latestSkills, { size: Number(url.searchParams.get('size') ?? '12') })
    },
  })
}

export async function mockNamespacePage(page: Page, options: MockNamespacePageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const namespaceData = options.namespaceData === undefined
    ? namespace('team-alpha', 'Team Alpha')
    : options.namespaceData
  const skills = options.skills ?? [
    skill(10, 'Namespace Skill One', { namespace: namespaceData?.slug ?? 'team-alpha' }),
    skill(11, 'Namespace Skill Two', { namespace: namespaceData?.slug ?? 'team-alpha' }),
  ]

  await page.route('**/api/web/namespaces/*', async (route) => {
    if (namespaceData === null) {
      await fulfillNotFound(route)
      return
    }

    await fulfillJson(route, namespaceData)
  })

  await page.route('**/api/web/skills?**', async (route) => {
    const url = new URL(route.request().url())
    const requestedNamespace = url.searchParams.get('namespace')
    const size = Number(url.searchParams.get('size') ?? '20')

    if (namespaceData && requestedNamespace === namespaceData.slug) {
      await fulfillJson(route, paged(skills, { size }))
      return
    }

    await fulfillJson(route, paged([], { size }))
  })
}

export async function mockSkillDetailPage(page: Page, options: MockSkillDetailPageOptions) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const detail = options.detail ?? skillDetail(21, 'Test Skill', {
    namespace: options.namespace,
    slug: options.slug,
    publishedVersion: { id: 210, version: '1.0.0', status: 'PUBLISHED' },
    headlineVersion: { id: 210, version: '1.0.0', status: 'PUBLISHED' },
  })
  const versions = options.versions ?? [
    skillVersion(210, '1.0.0'),
    skillVersion(211, '0.9.0', { publishedAt: '2026-03-18T00:00:00Z' }),
  ]
  const files = options.files ?? [
    skillFile('README.md', { fileSize: 256 }),
    skillFile('guide.txt', { fileSize: 128 }),
  ]
  const fileContents = options.fileContents ?? {
    'README.md': '# Test Skill\n\nThis is the package overview.',
    'guide.txt': 'CLI setup instructions go here.',
  }

  await page.route(`**/api/web/skills/${options.namespace}/${options.slug}`, async (route) => {
    await fulfillJson(route, detail)
  })

  await page.route(`**/api/web/skills/${options.namespace}/${options.slug}/versions`, async (route) => {
    await fulfillJson(route, paged(versions, { size: 20 }))
  })

  await page.route(`**/api/web/skills/${options.namespace}/${options.slug}/versions/*/files`, async (route) => {
    await fulfillJson(route, files)
  })

  await page.route(`**/api/web/skills/${options.namespace}/${options.slug}/versions/*/file?*`, async (route) => {
    const url = new URL(route.request().url())
    const path = url.searchParams.get('path') ?? ''

    if (options.failReadme && path === 'README.md') {
      await route.fulfill({
        status: 500,
        headers: {
          'access-control-allow-origin': '*',
          'content-type': 'text/plain; charset=utf-8',
        },
        body: 'Documentation fetch failed',
      })
      return
    }

    const content = fileContents[path]
    if (content === undefined) {
      await route.fulfill({
        status: 404,
        headers: {
          'access-control-allow-origin': '*',
          'content-type': 'text/plain; charset=utf-8',
        },
        body: 'Not Found',
      })
      return
    }

    await fulfillText(route, content)
  })

  await page.route('**/api/web/skills/*/report', async (route) => {
    await fulfillJson(route, {})
  })
}

export async function mockTokenPage(page: Page, options: MockTokenPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let tokens = [...(options.tokens ?? [])]

  await page.route('**/api/v1/tokens?**', async (route) => {
    const url = new URL(route.request().url())
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '10')
    const start = pageIndex * size
    await fulfillJson(route, {
      items: tokens.slice(start, start + size),
      total: tokens.length,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/v1/tokens', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }

    const body = JSON.parse(route.request().postData() ?? '{}') as { name?: string; expiresAt?: string }
    const nextToken: WorkspaceToken & { token: string } = {
      id: tokens.length + 1_000,
      name: body.name ?? 'New Token',
      token: `sk_live_created_${tokens.length + 1}`,
      tokenPrefix: `sk_live_${tokens.length + 1}`,
      createdAt: '2026-03-31T00:00:00Z',
      expiresAt: body.expiresAt || null,
      lastUsedAt: null,
    }
    tokens = [{ ...nextToken }, ...tokens]
    await fulfillJson(route, nextToken)
  })

  await page.route('**/api/v1/tokens/*/expiration', async (route) => {
    const tokenId = Number(route.request().url().split('/').slice(-2)[0])
    const body = JSON.parse(route.request().postData() ?? '{}') as { expiresAt?: string }
    tokens = tokens.map((token) => (
      token.id === tokenId
        ? { ...token, expiresAt: body.expiresAt || null }
        : token
    ))
    const updated = tokens.find((token) => token.id === tokenId)
    await fulfillJson(route, updated ?? null)
  })

  await page.route('**/api/v1/tokens/*', async (route) => {
    if (route.request().method() !== 'DELETE') {
      await route.continue()
      return
    }

    const tokenId = Number(route.request().url().split('/').pop())
    tokens = tokens.filter((token) => token.id !== tokenId)
    await route.fulfill({
      status: 204,
      headers: { 'access-control-allow-origin': '*' },
      body: '',
    })
  })
}

export async function mockDashboardPage(page: Page, options: MockDashboardPageOptions = {}) {
  await mockTokenPage(page, options)

  const mySkills = options.mySkills ?? [
    skill(31, 'Draft Workflow', { namespace: 'team-alpha' }),
    skill(32, 'Ops Assistant', { namespace: 'team-alpha' }),
  ]

  await page.route('**/api/web/me/skills?**', async (route) => {
    const url = new URL(route.request().url())
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '10')
    const start = pageIndex * size
    await fulfillJson(route, {
      items: mySkills.slice(start, start + size),
      total: mySkills.length,
      page: pageIndex,
      size,
    })
  })
}

export async function mockPublishPage(page: Page, options: MockPublishPageOptions = {}) {
  await mockDashboardPage(page, options)

  const namespaces = options.namespaces ?? [
    managedNamespace('team-alpha', 'Team Alpha', { id: 101, type: 'TEAM' }),
    managedNamespace('global', 'Global', {
      id: 1,
      type: 'GLOBAL',
      immutable: true,
      canFreeze: false,
      canUnfreeze: false,
      canArchive: false,
      canRestore: false,
    }),
  ]

  await page.route('**/api/web/me/namespaces', async (route) => {
    await fulfillJson(route, namespaces)
  })

  await page.route('**/api/web/skills/*/publish', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }

    if (options.publishError) {
      await fulfillJson(route, null, options.publishError)
      return
    }

    const namespaceSlug = route.request().url().split('/').slice(-2)[0]
    await fulfillJson(route, options.publishResult ?? {
      skillId: 501,
      namespace: namespaceSlug,
      slug: 'release-bot',
      version: '1.0.0',
      status: 'PENDING_REVIEW',
      fileCount: 4,
      totalSize: 4096,
    })
  })
}

export async function mockMySkillsPage(page: Page, options: MockMySkillsPageOptions = {}) {
  await mockDashboardPage(page, options)

  let skills = [...(options.mySkills ?? [
    skill(61, 'Pending Workflow', {
      namespace: 'team-alpha',
      status: 'ACTIVE',
      ownerPreviewVersion: { id: 611, version: '1.1.0', status: 'PENDING_REVIEW' },
      publishedVersion: { id: 610, version: '1.0.0', status: 'PUBLISHED' },
      headlineVersion: { id: 611, version: '1.1.0', status: 'PENDING_REVIEW' },
    }),
    skill(62, 'Published Helper', {
      namespace: 'team-alpha',
      status: 'ACTIVE',
      canSubmitPromotion: true,
      publishedVersion: { id: 620, version: '2.0.0', status: 'PUBLISHED' },
      headlineVersion: { id: 620, version: '2.0.0', status: 'PUBLISHED' },
    }),
    skill(63, 'Archived Assistant', {
      namespace: 'team-alpha',
      status: 'ARCHIVED',
      publishedVersion: { id: 630, version: '1.0.0', status: 'PUBLISHED' },
      headlineVersion: { id: 630, version: '1.0.0', status: 'PUBLISHED' },
    }),
  ])]

  const namespaces = options.namespaces ?? [
    managedNamespace('team-alpha', 'Team Alpha', { id: 101, type: 'TEAM' }),
    managedNamespace('global', 'Global', {
      id: 1,
      type: 'GLOBAL',
      immutable: true,
      canFreeze: false,
      canUnfreeze: false,
      canArchive: false,
      canRestore: false,
    }),
  ]

  const matchesFilter = (item: TestSkillSummary, filter?: string | null) => {
    if (!filter || filter === 'ALL') {
      return true
    }

    switch (filter) {
      case 'PENDING_REVIEW':
        return item.ownerPreviewVersion?.status === 'PENDING_REVIEW'
      case 'PUBLISHED':
        return item.publishedVersion?.status === 'PUBLISHED' && item.status !== 'ARCHIVED' && item.status !== 'HIDDEN'
      case 'REJECTED':
        return item.ownerPreviewVersion?.status === 'REJECTED' || item.status === 'REJECTED'
      case 'ARCHIVED':
        return item.status === 'ARCHIVED'
      case 'HIDDEN':
        return item.status === 'HIDDEN'
      default:
        return true
    }
  }

  await page.route('**/api/web/me/namespaces', async (route) => {
    await fulfillJson(route, namespaces)
  })

  await page.route('**/api/web/namespaces/global', async (route) => {
    const globalNamespace = namespaces.find((item) => item.slug === 'global') ?? managedNamespace('global', 'Global', { id: 1, type: 'GLOBAL' })
    await fulfillJson(route, globalNamespace)
  })

  await page.route('**/api/web/me/skills?**', async (route) => {
    const url = new URL(route.request().url())
    const filter = url.searchParams.get('filter')
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '10')
    const filtered = skills.filter((item) => matchesFilter(item, filter))
    const start = pageIndex * size

    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/web/skills/*/*/archive', async (route) => {
    const parts = route.request().url().split('/')
    const slug = decodeURIComponent(parts.at(-2) ?? '')
    skills = skills.map((item) => (
      item.slug === slug ? { ...item, status: 'ARCHIVED' } : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/web/skills/*/*/unarchive', async (route) => {
    const parts = route.request().url().split('/')
    const slug = decodeURIComponent(parts.at(-2) ?? '')
    skills = skills.map((item) => (
      item.slug === slug ? { ...item, status: 'ACTIVE' } : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/web/skills/*/*/versions/*/withdraw-review', async (route) => {
    const parts = route.request().url().split('/')
    const version = decodeURIComponent(parts.at(-2) ?? '')
    skills = skills.map((item) => (
      item.ownerPreviewVersion?.version === version
        ? {
            ...item,
            headlineVersion: item.publishedVersion,
            ownerPreviewVersion: undefined,
          }
        : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/web/promotions', async (route) => {
    if (options.promotionError) {
      await fulfillJson(route, null, options.promotionError)
      return
    }

    await fulfillJson(route, {})
  })
}

export async function mockMyNamespacesPage(page: Page, options: MockMyNamespacesPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let namespaces = [...(options.namespaces ?? [
    managedNamespace('team-alpha', 'Team Alpha', {
      id: 101,
      type: 'TEAM',
      description: 'Primary team workspace',
      status: 'ACTIVE',
      canFreeze: true,
      canUnfreeze: false,
      canArchive: true,
      canRestore: false,
      currentUserRole: 'OWNER',
    }),
    managedNamespace('global', 'Global', {
      id: 1,
      type: 'GLOBAL',
      description: 'Built-in platform namespace',
      immutable: true,
      status: 'ACTIVE',
      canFreeze: false,
      canUnfreeze: false,
      canArchive: false,
      canRestore: false,
      currentUserRole: 'REVIEWER',
    }),
  ])]

  await page.route('**/api/web/me/namespaces', async (route) => {
    await fulfillJson(route, namespaces)
  })

  await page.route('**/api/web/namespaces/*', async (route) => {
    const slug = decodeURIComponent(route.request().url().split('/').pop() ?? '')
    const namespaceItem = namespaces.find((item) => item.slug === slug)
    if (!namespaceItem) {
      await fulfillNotFound(route)
      return
    }

    await fulfillJson(route, namespaceItem)
  })

  await page.route('**/api/v1/namespaces', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }

    const body = JSON.parse(route.request().postData() ?? '{}') as {
      slug?: string
      displayName?: string
      description?: string
    }

    const created = managedNamespace(body.slug ?? 'new-space', body.displayName ?? 'New Space', {
      id: namespaces.length + 500,
      description: body.description,
      status: 'ACTIVE',
      currentUserRole: 'OWNER',
      canFreeze: true,
      canUnfreeze: false,
      canArchive: true,
      canRestore: false,
    })
    namespaces = [...namespaces, created]
    await fulfillJson(route, created)
  })

  await page.route('**/api/web/namespaces/*/freeze', async (route) => {
    const slug = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    namespaces = namespaces.map((item) => (
      item.slug === slug
        ? { ...item, status: 'FROZEN', canFreeze: false, canUnfreeze: true }
        : item
    ))
    await fulfillJson(route, namespaces.find((item) => item.slug === slug) ?? null)
  })

  await page.route('**/api/web/namespaces/*/unfreeze', async (route) => {
    const slug = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    namespaces = namespaces.map((item) => (
      item.slug === slug
        ? { ...item, status: 'ACTIVE', canFreeze: true, canUnfreeze: false }
        : item
    ))
    await fulfillJson(route, namespaces.find((item) => item.slug === slug) ?? null)
  })

  await page.route('**/api/web/namespaces/*/archive', async (route) => {
    const slug = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    namespaces = namespaces.map((item) => (
      item.slug === slug
        ? { ...item, status: 'ARCHIVED', canArchive: false, canRestore: true }
        : item
    ))
    await fulfillJson(route, namespaces.find((item) => item.slug === slug) ?? null)
  })

  await page.route('**/api/web/namespaces/*/restore', async (route) => {
    const slug = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    namespaces = namespaces.map((item) => (
      item.slug === slug
        ? { ...item, status: 'ACTIVE', canArchive: true, canRestore: false }
        : item
    ))
    await fulfillJson(route, namespaces.find((item) => item.slug === slug) ?? null)
  })
}

export async function mockStarsPage(page: Page, options: MockStarsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const stars = options.stars ?? [
    skill(91, 'Pinned Assistant', {
      namespace: 'team-alpha',
      headlineVersion: { id: 910, version: '1.0.0', status: 'PUBLISHED' },
      publishedVersion: { id: 910, version: '1.0.0', status: 'PUBLISHED' },
    }),
    skill(92, 'Ops Copilot', {
      namespace: 'global',
      headlineVersion: { id: 920, version: '2.4.0', status: 'PUBLISHED' },
      publishedVersion: { id: 920, version: '2.4.0', status: 'PUBLISHED' },
    }),
  ]

  await page.route('**/api/web/me/stars?**', async (route) => {
    const url = new URL(route.request().url())
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '12')
    const start = pageIndex * size
    await fulfillJson(route, {
      items: stars.slice(start, start + size),
      total: stars.length,
      page: pageIndex,
      size,
    })
  })
}

export async function mockNotificationSettingsPage(page: Page, options: MockNotificationSettingsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let preferences = [...(options.preferences ?? [
    { category: 'PUBLISH', channel: 'IN_APP', enabled: true },
    { category: 'REVIEW', channel: 'IN_APP', enabled: true },
    { category: 'PROMOTION', channel: 'IN_APP', enabled: true },
    { category: 'REPORT', channel: 'IN_APP', enabled: false },
  ])]

  await page.route('**/api/web/notification-preferences', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, preferences)
      return
    }

    const body = JSON.parse(route.request().postData() ?? '{}') as {
      preferences?: Array<{ category: string; channel: string; enabled: boolean }>
    }
    preferences = [...(body.preferences ?? preferences)]
    await fulfillJson(route, {})
  })
}

export async function mockNamespaceReviewsPage(page: Page, options: MockNamespaceReviewsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const namespaceData = options.namespaceData === undefined
    ? managedNamespace('team-alpha', 'Team Alpha', {
        id: 101,
        type: 'TEAM',
        status: 'ACTIVE',
        canFreeze: true,
        canUnfreeze: false,
        canArchive: true,
        canRestore: false,
      })
    : options.namespaceData

  const reviews = options.reviews ?? [
    reviewTask(301, 'PENDING', { namespace: namespaceData?.slug ?? 'team-alpha', skillSlug: 'pending-review', version: '1.1.0' }),
    reviewTask(302, 'APPROVED', { namespace: namespaceData?.slug ?? 'team-alpha', skillSlug: 'approved-review', version: '1.0.0', reviewComment: 'Looks good' }),
    reviewTask(303, 'REJECTED', { namespace: namespaceData?.slug ?? 'team-alpha', skillSlug: 'rejected-review', version: '2.0.0', reviewComment: 'Needs more detail' }),
  ]

  await page.route('**/api/web/namespaces/*', async (route) => {
    if (namespaceData === null) {
      await fulfillNotFound(route)
      return
    }

    await fulfillJson(route, namespaceData)
  })

  await page.route('**/api/web/reviews?**', async (route) => {
    const url = new URL(route.request().url())
    const status = url.searchParams.get('status')
    const namespaceId = Number(url.searchParams.get('namespaceId') ?? '0')
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '10')
    const sortDirection = url.searchParams.get('sortDirection') ?? 'DESC'

    let filtered = reviews.filter((item) => item.status === status)
    if (namespaceData && namespaceId > 0) {
      filtered = filtered.filter(() => namespaceId === namespaceData.id)
    }

    filtered = [...filtered].sort((left, right) => {
      const leftTime = new Date((status === 'PENDING' ? left.submittedAt : left.reviewedAt ?? left.submittedAt)).getTime()
      const rightTime = new Date((status === 'PENDING' ? right.submittedAt : right.reviewedAt ?? right.submittedAt)).getTime()
      return sortDirection === 'ASC' ? leftTime - rightTime : rightTime - leftTime
    })

    const start = pageIndex * size
    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })
}

export async function mockNamespaceMembersPage(page: Page, options: MockNamespaceMembersPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const namespaceData = options.namespaceData === undefined
    ? managedNamespace('team-alpha', 'Team Alpha', {
        id: 101,
        type: 'TEAM',
        status: 'ACTIVE',
        currentUserRole: 'OWNER',
      })
    : options.namespaceData
  const myNamespaces = options.myNamespaces ?? (namespaceData ? [namespaceData] : [])
  let members = [...(options.members ?? [
    namespaceMember(1, 'owner-user', 'OWNER'),
    namespaceMember(2, 'admin-user', 'ADMIN'),
    namespaceMember(3, 'member-user', 'MEMBER'),
  ])]
  const candidates = options.candidates ?? [
    namespaceCandidateUser('new-user', 'New User'),
    namespaceCandidateUser('ops-user', 'Ops User'),
  ]

  await page.route('**/api/web/me/namespaces', async (route) => {
    await fulfillJson(route, myNamespaces)
  })

  await page.route('**/api/web/namespaces/*', async (route) => {
    if (namespaceData === null) {
      await fulfillNotFound(route)
      return
    }

    await fulfillJson(route, namespaceData)
  })

  await page.route('**/api/web/namespaces/*/members', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, { items: members })
      return
    }

    const body = JSON.parse(route.request().postData() ?? '{}') as { userId?: string; role?: string }
    const added = namespaceMember(members.length + 10, body.userId ?? 'new-user', body.role ?? 'MEMBER')
    const existingIndex = members.findIndex((item) => item.userId === added.userId)
    if (existingIndex >= 0) {
      members = members.map((item, index) => (index === existingIndex ? added : item))
    } else {
      members = [...members, added]
    }
    await fulfillJson(route, added)
  })

  await page.route('**/api/web/namespaces/*/member-candidates?**', async (route) => {
    const url = new URL(route.request().url())
    const search = (url.searchParams.get('search') ?? '').toLowerCase()
    const filtered = candidates.filter((item) => (
      item.userId.toLowerCase().includes(search)
        || item.displayName.toLowerCase().includes(search)
        || (item.email ?? '').toLowerCase().includes(search)
    ))
    await fulfillJson(route, filtered)
  })

  await page.route('**/api/web/namespaces/*/members/*/role', async (route) => {
    const parts = route.request().url().split('/')
    const userId = decodeURIComponent(parts.at(-2) ?? '')
    const body = JSON.parse(route.request().postData() ?? '{}') as { role?: string }
    members = members.map((item) => (
      item.userId === userId ? { ...item, role: body.role ?? item.role } : item
    ))
    await fulfillJson(route, members.find((item) => item.userId === userId) ?? null)
  })

  await page.route('**/api/web/namespaces/*/members/*', async (route) => {
    if (route.request().method() !== 'DELETE') {
      await route.continue()
      return
    }

    const userId = decodeURIComponent(route.request().url().split('/').pop() ?? '')
    members = members.filter((item) => item.userId !== userId)
    await fulfillJson(route, {})
  })
}

export async function mockReviewsPage(page: Page, options: MockReviewsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const reviews = options.reviews ?? [
    reviewTask(501, 'PENDING', { namespace: 'team-alpha', skillSlug: 'pending-review', version: '1.1.0', submittedAt: '2026-03-30T00:00:00Z' }),
    reviewTask(502, 'APPROVED', { namespace: 'global', skillSlug: 'approved-review', version: '1.0.0', reviewedAt: '2026-03-29T00:00:00Z', reviewedByName: 'Reviewer A' }),
    reviewTask(503, 'REJECTED', { namespace: 'team-beta', skillSlug: 'rejected-review', version: '2.0.0', reviewedAt: '2026-03-28T00:00:00Z', reviewedByName: 'Reviewer B' }),
  ]

  await page.route('**/api/web/reviews?**', async (route) => {
    const url = new URL(route.request().url())
    const status = url.searchParams.get('status')
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const sortDirection = url.searchParams.get('sortDirection') ?? 'DESC'

    let filtered = reviews.filter((item) => item.status === status)
    filtered = [...filtered].sort((left, right) => {
      const leftTime = new Date((status === 'PENDING' ? left.submittedAt : left.reviewedAt ?? left.submittedAt)).getTime()
      const rightTime = new Date((status === 'PENDING' ? right.submittedAt : right.reviewedAt ?? right.submittedAt)).getTime()
      return sortDirection === 'ASC' ? leftTime - rightTime : rightTime - leftTime
    })

    const start = pageIndex * size
    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })
}

export async function mockPromotionsPage(page: Page, options: MockPromotionsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let promotions = [...(options.promotions ?? [
    promotionTask(701, 'PENDING', {
      sourceNamespace: 'team-alpha',
      sourceSkillSlug: 'pending-promotion',
      sourceVersion: '1.1.0',
      submittedAt: '2026-03-30T00:00:00Z',
    }),
    promotionTask(702, 'APPROVED', {
      sourceNamespace: 'team-beta',
      sourceSkillSlug: 'approved-promotion',
      sourceVersion: '1.0.0',
      reviewComment: 'Approved for global release',
      reviewedAt: '2026-03-29T00:00:00Z',
      reviewedByName: 'Reviewer A',
    }),
    promotionTask(703, 'REJECTED', {
      sourceNamespace: 'team-gamma',
      sourceSkillSlug: 'rejected-promotion',
      sourceVersion: '2.0.0',
      reviewComment: 'Needs more polish',
      reviewedAt: '2026-03-28T00:00:00Z',
      reviewedByName: 'Reviewer B',
    }),
  ])]

  await page.route('**/api/web/promotions?**', async (route) => {
    const url = new URL(route.request().url())
    const status = url.searchParams.get('status') ?? 'PENDING'
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const filtered = promotions.filter((item) => item.status === status)
    const start = pageIndex * size

    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/web/promotions/*/approve', async (route) => {
    const id = Number(route.request().url().split('/').slice(-2)[0])
    const body = JSON.parse(route.request().postData() ?? '{}') as { comment?: string }
    promotions = promotions.map((item) => (
      item.id === id
        ? {
            ...item,
            status: 'APPROVED',
            reviewComment: body.comment || item.reviewComment,
            reviewedAt: '2026-03-31T00:00:00Z',
            reviewedByName: options.currentUser?.displayName ?? 'Skill Admin',
          }
        : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/web/promotions/*/reject', async (route) => {
    const id = Number(route.request().url().split('/').slice(-2)[0])
    const body = JSON.parse(route.request().postData() ?? '{}') as { comment?: string }
    promotions = promotions.map((item) => (
      item.id === id
        ? {
            ...item,
            status: 'REJECTED',
            reviewComment: body.comment || item.reviewComment,
            reviewedAt: '2026-03-31T00:00:00Z',
            reviewedByName: options.currentUser?.displayName ?? 'Skill Admin',
          }
        : item
    ))
    await fulfillJson(route, {})
  })
}

export async function mockReportsPage(page: Page, options: MockReportsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let reports = [...(options.reports ?? [
    skillReport(801, 'PENDING', {
      namespace: 'team-alpha',
      skillSlug: 'pending-report',
      skillDisplayName: 'Pending Report Skill',
      reporterId: 'alice',
      createdAt: '2026-03-30T00:00:00Z',
    }),
    skillReport(802, 'RESOLVED', {
      namespace: 'team-beta',
      skillSlug: 'resolved-report',
      skillDisplayName: 'Resolved Report Skill',
      handledBy: 'reviewer-a',
      handledAt: '2026-03-29T00:00:00Z',
    }),
    skillReport(803, 'DISMISSED', {
      namespace: 'team-gamma',
      skillSlug: 'dismissed-report',
      skillDisplayName: 'Dismissed Report Skill',
      handledBy: 'reviewer-b',
      handledAt: '2026-03-28T00:00:00Z',
    }),
  ])]

  await page.route('**/api/v1/admin/skill-reports?**', async (route) => {
    const url = new URL(route.request().url())
    const status = url.searchParams.get('status') ?? 'PENDING'
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const filtered = reports.filter((item) => item.status === status)
    const start = pageIndex * size

    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/v1/admin/skill-reports/*/resolve', async (route) => {
    const id = Number(route.request().url().split('/').slice(-2)[0])
    reports = reports.map((item) => (
      item.id === id
        ? {
            ...item,
            status: 'RESOLVED',
            handledBy: options.currentUser?.userId ?? 'skill-admin',
            handledAt: '2026-03-31T00:00:00Z',
          }
        : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/v1/admin/skill-reports/*/dismiss', async (route) => {
    const id = Number(route.request().url().split('/').slice(-2)[0])
    reports = reports.map((item) => (
      item.id === id
        ? {
            ...item,
            status: 'DISMISSED',
            handledBy: options.currentUser?.userId ?? 'skill-admin',
            handledAt: '2026-03-31T00:00:00Z',
          }
        : item
    ))
    await fulfillJson(route, {})
  })
}

export async function mockReviewDetailPage(page: Page, reviewId: number, options: MockReviewDetailPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let review = options.review === undefined
    ? reviewTask(reviewId, 'PENDING', {
        namespace: 'team-alpha',
        skillSlug: 'pending-review',
        version: '1.2.0',
        submittedByName: 'Alice',
      })
    : options.review

  const reviewDetailData = options.reviewSkillDetail ?? reviewSkillDetail(401, 'Pending Review Skill', {
    skill: {
      id: 401,
      slug: 'pending-review',
      displayName: 'Pending Review Skill',
      visibility: 'PUBLIC',
      status: 'ACTIVE',
      downloadCount: 10,
      starCount: 3,
      ratingCount: 1,
      hidden: false,
      namespace: review?.namespace ?? 'team-alpha',
      canManageLifecycle: false,
      canSubmitPromotion: false,
      canInteract: false,
      canReport: false,
      resolutionMode: 'REVIEW_TASK',
    },
    versions: [
      skillVersion(411, review?.version ?? '1.2.0', {
        status: 'PENDING_REVIEW',
        changelog: 'Pending moderation update',
      }),
      skillVersion(410, '1.1.0', {
        status: 'PUBLISHED',
        changelog: 'Previous approved release',
      }),
    ],
    activeVersion: review?.version ?? '1.2.0',
  })

  const fileContents = options.fileContents ?? {
    'README.md': '# Pending Review Skill\n\nPending review package overview.',
    'guide.txt': 'Step 1: Install dependencies',
  }

  await page.route(`**/api/web/reviews/${reviewId}`, async (route) => {
    if (review === null) {
      await fulfillNotFound(route, 'Review task not found')
      return
    }

    await fulfillJson(route, review)
  })

  await page.route(`**/api/web/reviews/${reviewId}/skill-detail`, async (route) => {
    await fulfillJson(route, reviewDetailData)
  })

  await page.route(`**/api/web/reviews/${reviewId}/file?*`, async (route) => {
    const url = new URL(route.request().url())
    const path = url.searchParams.get('path') ?? ''
    const content = fileContents[path]

    if (content === undefined) {
      await route.fulfill({
        status: 404,
        headers: {
          'access-control-allow-origin': '*',
          'content-type': 'text/plain; charset=utf-8',
        },
        body: 'Not Found',
      })
      return
    }

    await fulfillText(route, content)
  })

  await page.route(`**/api/web/reviews/${reviewId}/approve`, async (route) => {
    const body = JSON.parse(route.request().postData() ?? '{}') as { comment?: string }
    if (review) {
      review = {
        ...review,
        status: 'APPROVED',
        reviewComment: body.comment || review.reviewComment,
        reviewedAt: '2026-03-31T00:00:00Z',
        reviewedBy: options.currentUser?.userId ?? 'skill-admin',
        reviewedByName: options.currentUser?.displayName ?? 'Skill Admin',
      }
    }
    await fulfillJson(route, {})
  })

  await page.route(`**/api/web/reviews/${reviewId}/reject`, async (route) => {
    const body = JSON.parse(route.request().postData() ?? '{}') as { comment?: string }
    if (review) {
      review = {
        ...review,
        status: 'REJECTED',
        reviewComment: body.comment || review.reviewComment,
        reviewedAt: '2026-03-31T00:00:00Z',
        reviewedBy: options.currentUser?.userId ?? 'skill-admin',
        reviewedByName: options.currentUser?.displayName ?? 'Skill Admin',
      }
    }
    await fulfillJson(route, {})
  })

  await page.route('**/api/web/reviews?**', async (route) => {
    const url = new URL(route.request().url())
    const status = url.searchParams.get('status')
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const items = review && (!status || review.status === status) ? [review] : []
    const start = pageIndex * size

    await fulfillJson(route, {
      items: items.slice(start, start + size),
      total: items.length,
      page: pageIndex,
      size,
    })
  })
}

export async function mockAdminUsersPage(page: Page, options: MockAdminUsersPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let users = [...(options.users ?? [
    adminUser('pending-user', 'pending-user', 'PENDING', { platformRoles: ['USER'] }),
    adminUser('active-user', 'active-user', 'ACTIVE', { platformRoles: ['USER'] }),
    adminUser('disabled-user', 'disabled-user', 'DISABLED', { platformRoles: ['USER_ADMIN'] }),
  ])]

  await page.route('**/api/v1/admin/users?**', async (route) => {
    const url = new URL(route.request().url())
    const search = (url.searchParams.get('search') ?? '').toLowerCase()
    const status = url.searchParams.get('status') ?? ''
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    const filtered = users.filter((item) => {
      const matchesSearch = !search
        || item.userId.toLowerCase().includes(search)
        || item.username.toLowerCase().includes(search)
        || (item.email ?? '').toLowerCase().includes(search)
      const matchesStatus = !status || item.status === status
      return matchesSearch && matchesStatus
    })
    const start = pageIndex * size

    await fulfillJson(route, {
      items: filtered.slice(start, start + size).map((item) => ({
        id: item.userId,
        username: item.username,
        email: item.email,
        platformRoles: item.platformRoles,
        status: item.status,
        createdAt: item.createdAt,
      })),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/v1/admin/users/*/role', async (route) => {
    const userId = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    const body = JSON.parse(route.request().postData() ?? '{}') as { role?: string }
    users = users.map((item) => (
      item.userId === userId
        ? { ...item, platformRoles: body.role ? [body.role] : item.platformRoles }
        : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/v1/admin/users/*/approve', async (route) => {
    const userId = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    users = users.map((item) => (
      item.userId === userId ? { ...item, status: 'ACTIVE' } : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/v1/admin/users/*/disable', async (route) => {
    const userId = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    users = users.map((item) => (
      item.userId === userId ? { ...item, status: 'DISABLED' } : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/v1/admin/users/*/enable', async (route) => {
    const userId = decodeURIComponent(route.request().url().split('/').slice(-2)[0] ?? '')
    users = users.map((item) => (
      item.userId === userId ? { ...item, status: 'ACTIVE' } : item
    ))
    await fulfillJson(route, {})
  })
}

export async function mockAuditLogPage(page: Page, options: MockAuditLogPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  const logs = options.logs ?? [
    auditLogItem(1, 'REBUILD_SEARCH_INDEX', { resourceType: 'SEARCH_INDEX', details: 'Search index rebuild completed' }),
    auditLogItem(2, 'PROMOTION_APPROVE', { userId: 'skill-admin', username: 'Skill Admin', resourceType: 'PROMOTION', resourceId: '202' }),
    auditLogItem(3, 'REVIEW_SUBMIT', { userId: 'alice', username: 'Alice', requestId: 'req-alice', ipAddress: '10.0.0.3' }),
  ]

  await page.route('**/api/v1/admin/audit-logs?**', async (route) => {
    const url = new URL(route.request().url())
    const action = url.searchParams.get('action') ?? ''
    const userId = (url.searchParams.get('userId') ?? '').toLowerCase()
    const requestId = (url.searchParams.get('requestId') ?? '').toLowerCase()
    const ipAddress = (url.searchParams.get('ipAddress') ?? '').toLowerCase()
    const resourceType = (url.searchParams.get('resourceType') ?? '').toLowerCase()
    const resourceId = (url.searchParams.get('resourceId') ?? '').toLowerCase()
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    const filtered = logs.filter((item) => {
      if (action && item.action !== action) return false
      if (userId && !(item.userId ?? '').toLowerCase().includes(userId)) return false
      if (requestId && !(item.requestId ?? '').toLowerCase().includes(requestId)) return false
      if (ipAddress && !(item.ipAddress ?? '').toLowerCase().includes(ipAddress)) return false
      if (resourceType && !(item.resourceType ?? '').toLowerCase().includes(resourceType)) return false
      if (resourceId && !(item.resourceId ?? '').toLowerCase().includes(resourceId)) return false
      return true
    })
    const start = pageIndex * size

    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })
}

export async function mockAdminLabelsPage(page: Page, options: MockAdminLabelsPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let definitions = [...(options.definitions ?? [
    labelDefinition('official', 'Official', { sortOrder: 0, visibleInFilter: true }),
    labelDefinition('internal', 'Internal', {
      sortOrder: 1,
      visibleInFilter: false,
      type: 'PRIVILEGED',
    }),
  ])]

  const sortedDefinitions = () => [...definitions].sort((left, right) => {
    if (left.sortOrder !== right.sortOrder) {
      return left.sortOrder - right.sortOrder
    }
    return left.slug.localeCompare(right.slug)
  })

  await page.route('**/api/v1/admin/labels', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, sortedDefinitions())
      return
    }

    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }

    const body = JSON.parse(route.request().postData() ?? '{}') as TestLabelDefinition
    const created = labelDefinition(body.slug, body.translations?.[0]?.displayName ?? body.slug, {
      type: body.type,
      visibleInFilter: body.visibleInFilter,
      sortOrder: body.sortOrder,
      translations: body.translations,
    })
    definitions = [...definitions, created]
    await fulfillJson(route, created)
  })

  await page.route('**/api/v1/admin/labels/sort-order', async (route) => {
    const body = JSON.parse(route.request().postData() ?? '{}') as {
      items?: Array<{ slug: string; sortOrder: number }>
    }
    definitions = definitions.map((item) => {
      const updated = body.items?.find((candidate) => candidate.slug === item.slug)
      return updated ? { ...item, sortOrder: updated.sortOrder } : item
    })
    await fulfillJson(route, sortedDefinitions())
  })

  await page.route(/\/api\/v1\/admin\/labels\/[^/?]+(?:\?.*)?$/, async (route) => {
    const slug = decodeURIComponent(route.request().url().split('/').pop() ?? '')

    if (route.request().method() === 'PUT') {
      const body = JSON.parse(route.request().postData() ?? '{}') as Omit<TestLabelDefinition, 'slug'>
      definitions = definitions.map((item) => (
        item.slug === slug
          ? {
              ...item,
              type: body.type,
              visibleInFilter: body.visibleInFilter,
              sortOrder: body.sortOrder,
              translations: body.translations,
            }
          : item
      ))
      await fulfillJson(route, definitions.find((item) => item.slug === slug) ?? null)
      return
    }

    if (route.request().method() === 'DELETE') {
      definitions = definitions.filter((item) => item.slug !== slug)
      await route.fulfill({
        status: 204,
        headers: { 'access-control-allow-origin': '*' },
        body: '',
      })
      return
    }

    await route.continue()
  })
}

export async function mockCliAuthPage(page: Page, options: MockCliAuthPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  await page.route('**/api/v1/tokens', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }

    if (options.createTokenError) {
      await fulfillJson(route, null, options.createTokenError)
      return
    }

    await fulfillJson(route, options.createTokenResult ?? {
      id: 9901,
      name: 'CLI token',
      token: 'sk_cli_123',
      tokenPrefix: 'sk_cli',
      createdAt: '2026-03-31T00:00:00Z',
    })
  })
}

export async function mockProfilePage(page: Page, options: MockProfilePageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  let profile = options.profile ?? {
    displayName: 'Local User',
    avatarUrl: null,
    email: 'local-user@example.com',
    pendingChanges: null,
    fieldPolicies: {
      displayName: { editable: true, requiresReview: true },
      email: { editable: false, requiresReview: false },
    },
  }

  await page.route('**/api/v1/user/profile', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, profile)
      return
    }

    const body = JSON.parse(route.request().postData() ?? '{}') as Record<string, string>
    const result = options.updateResult ?? { status: 'PENDING_REVIEW', pendingFields: body }
    if (result.status === 'UPDATED' && body.displayName) {
      profile = { ...profile, displayName: body.displayName }
    }
    if (result.status === 'PENDING_REVIEW') {
      profile = {
        ...profile,
        pendingChanges: {
          status: 'PENDING',
          changes: body,
          reviewComment: null,
          createdAt: '2026-03-31T00:00:00Z',
        },
      }
    }
    await fulfillJson(route, result)
  })
}

export async function mockSecurityPage(page: Page, options: MockSecurityPageOptions = {}) {
  await mockAppShell(page, {
    authenticated: options.authenticated ?? true,
    currentUser: options.currentUser,
    userRole: options.userRole,
    authMethods: options.authMethods,
  })

  await page.route('**/api/v1/auth/local/change-password', async (route) => {
    if (options.changePasswordSucceeds === false) {
      await fulfillJson(route, null, {
        status: options.changePasswordStatus ?? 401,
        msg: options.changePasswordMessage ?? 'Current password is incorrect',
      })
      return
    }

    await fulfillJson(route, {})
  })

  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.fulfill({
      status: 204,
      headers: { 'access-control-allow-origin': '*' },
      body: '',
    })
  })
}

export async function mockNotificationsPage(page: Page, options: MockNotificationsPageOptions = {}) {
  await mockDashboardPage(page, options)

  let notifications = [...(options.notifications ?? [])]

  const filteredNotifications = (category?: string) => (
    category ? notifications.filter((item) => item.category === category) : notifications
  )

  await page.route('**/api/web/notifications?**', async (route) => {
    const url = new URL(route.request().url())
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const category = url.searchParams.get('category') ?? undefined
    const filtered = filteredNotifications(category)
    const start = pageIndex * size
    await fulfillJson(route, {
      items: filtered.slice(start, start + size),
      total: filtered.length,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/web/notifications/unread-count**', async (route) => {
    await fulfillJson(route, {
      count: notifications.filter((item) => item.status === 'UNREAD').length,
    })
  })

  await page.route('**/api/web/notifications/read-all', async (route) => {
    notifications = notifications.map((item) => ({ ...item, status: 'READ' as const }))
    await fulfillJson(route, { count: notifications.length })
  })

  await page.route('**/api/web/notifications/*/read', async (route) => {
    const id = Number(route.request().url().split('/').slice(-2)[0])
    notifications = notifications.map((item) => (
      item.id === id ? { ...item, status: 'READ' as const } : item
    ))
    await fulfillJson(route, {})
  })

  await page.route('**/api/web/notifications/*', async (route) => {
    if (route.request().method() !== 'DELETE') {
      await route.continue()
      return
    }

    const id = Number(route.request().url().split('/').pop())
    notifications = notifications.filter((item) => item.id !== id)
    await fulfillJson(route, {})
  })
}
