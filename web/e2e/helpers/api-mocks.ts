import type { Page, Route } from '@playwright/test'

type EnvelopeOptions = {
  status?: number
  msg?: string
}

type SkillSummary = {
  id: number
  slug: string
  displayName: string
  summary?: string
  downloadCount: number
  starCount: number
  ratingCount: number
  namespace: string
  updatedAt: string
  canSubmitPromotion: boolean
  headlineVersion?: {
    id: number
    version: string
    status: string
  }
  publishedVersion?: {
    id: number
    version: string
    status: string
  }
}

type SearchResponse = {
  items: SkillSummary[]
  total: number
  page: number
  size: number
}

type SearchHandler = (url: URL) => SearchResponse
type SkillDetailHandler = () => SkillSummary

type User = {
  userId: string
  displayName: string
  platformRoles: string[]
  email?: string
  status?: string
}

type Namespace = {
  slug: string
  name: string
  description?: string
  memberCount?: number
}

type Review = {
  id: number
  skillId: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  reviewedAt?: string
  comment?: string
}

type Token = {
  id: number
  name: string
  token?: string
  createdAt: string
  expiresAt?: string
}

type Notification = {
  id: number
  type: string
  title: string
  content: string
  read: boolean
  createdAt: string
}

const JSON_HEADERS = {
  'access-control-allow-origin': '*',
  'content-type': 'application/json',
}

function envelope<T>(data: T, options: EnvelopeOptions = {}) {
  return JSON.stringify({
    code: options.status && options.status >= 400 ? options.status : 0,
    msg: options.msg ?? 'ok',
    data,
    timestamp: '2026-03-27T00:00:00Z',
    requestId: 'playwright-e2e',
  })
}

async function fulfillJson<T>(route: Route, data: T, options?: EnvelopeOptions) {
  await route.fulfill({
    status: options?.status ?? 200,
    headers: JSON_HEADERS,
    body: envelope(data, options),
  })
}

export function skill(
  id: number,
  displayName: string,
  overrides: Partial<SkillSummary> = {},
): SkillSummary {
  return {
    id,
    slug: displayName.toLowerCase().replace(/\s+/g, '-'),
    displayName,
    summary: `${displayName} summary`,
    downloadCount: 100 + id,
    starCount: 10 + id,
    ratingCount: 0,
    namespace: 'global',
    updatedAt: '2026-03-20T00:00:00Z',
    canSubmitPromotion: false,
    headlineVersion: {
      id: id * 10,
      version: '1.0.0',
      status: 'PUBLISHED',
    },
    publishedVersion: {
      id: id * 10,
      version: '1.0.0',
      status: 'PUBLISHED',
    },
    ...overrides,
  }
}

export function user(
  userId: string,
  displayName: string,
  role: string = 'USER',
  overrides: Partial<User> = {},
): User {
  return {
    userId,
    displayName,
    platformRoles: [role],
    email: `${userId}@example.com`,
    status: 'ACTIVE',
    ...overrides,
  }
}

export function namespace(
  slug: string,
  name: string,
  overrides: Partial<Namespace> = {},
): Namespace {
  return {
    slug,
    name,
    description: `${name} namespace`,
    memberCount: 1,
    ...overrides,
  }
}

export function review(
  id: number,
  skillId: number,
  status: 'PENDING' | 'APPROVED' | 'REJECTED' = 'PENDING',
  overrides: Partial<Review> = {},
): Review {
  return {
    id,
    skillId,
    status,
    submittedAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function token(
  id: number,
  name: string,
  overrides: Partial<Token> = {},
): Token {
  return {
    id,
    name,
    createdAt: '2026-03-20T00:00:00Z',
    expiresAt: '2027-03-20T00:00:00Z',
    ...overrides,
  }
}

export function notification(
  id: number,
  type: string,
  read: boolean = false,
  overrides: Partial<Notification> = {},
): Notification {
  return {
    id,
    type,
    title: `Notification ${id}`,
    content: `Content for notification ${id}`,
    read,
    createdAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export async function setEnglishLocale(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'en')
  })
}

export async function mockStaticApis(
  page: Page,
  options: {
    authenticated?: boolean
    userRole?: string
  } = {},
) {
  const authenticated = options.authenticated ?? false
  const userRole = options.userRole ?? 'USER'

  await page.route('**/api/v1/auth/me**', async (route) => {
    if (!authenticated) {
      await fulfillJson(route, null, { status: 401, msg: 'Unauthorized' })
      return
    }

    await fulfillJson(route, {
      userId: 'local-user',
      displayName: 'Local User',
      platformRoles: [userRole],
    })
  })

  await page.route('**/api/v1/auth/methods**', async (route) => {
    await fulfillJson(route, [])
  })

  await page.route('**/api/v1/auth/providers**', async (route) => {
    await fulfillJson(route, [])
  })

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
    await fulfillJson(route, { items: [], total: 0, page: 0, size: 5 })
  })
}

export async function mockCommonApis(
  page: Page,
  options: {
    authenticated?: boolean
    searchHandler?: SearchHandler
    skillDetailHandler?: SkillDetailHandler
  },
) {
  await mockStaticApis(page, options)

  if (options.searchHandler) {
    await page.route('**/api/web/skills?**', async (route) => {
      const url = new URL(route.request().url())
      await fulfillJson(route, options.searchHandler!(url))
    })
  }

  if (options.skillDetailHandler) {
    await page.route('**/api/web/skills/**', async (route) => {
      // Skip search endpoint
      if (route.request().url().includes('?')) {
        return route.continue()
      }
      await fulfillJson(route, options.skillDetailHandler!())
    })
  }
}

// Auth mocking
export async function mockAuthLogin(page: Page, success: boolean = true) {
  await page.route('**/api/v1/auth/login', async (route) => {
    if (success) {
      await fulfillJson(route, { userId: 'local-user', displayName: 'Local User' })
    } else {
      await fulfillJson(route, null, { status: 401, msg: 'Invalid credentials' })
    }
  })
}

export async function mockAuthLogout(page: Page) {
  await page.route('**/api/v1/auth/logout', async (route) => {
    await fulfillJson(route, {})
  })
}

// Skill detail mocking
export async function mockSkillVersions(page: Page, versions: any[]) {
  await page.route('**/api/web/skills/*/versions', async (route) => {
    await fulfillJson(route, versions)
  })
}

export async function mockSkillFiles(page: Page, files: any[]) {
  await page.route('**/api/web/skills/*/versions/*/files', async (route) => {
    await fulfillJson(route, files)
  })
}

// Social mocking
export async function mockStarStatus(page: Page, starred: boolean) {
  await page.route('**/api/web/skills/*/star', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, { starred })
    } else {
      await route.continue()
    }
  })
}

export async function mockToggleStar(page: Page) {
  await page.route('**/api/web/skills/*/star', async (route) => {
    if (route.request().method() === 'POST' || route.request().method() === 'DELETE') {
      await fulfillJson(route, {})
    } else {
      await route.continue()
    }
  })
}

export async function mockRating(page: Page, rating: number | null) {
  await page.route('**/api/web/skills/*/rating', async (route) => {
    if (route.request().method() === 'GET') {
      await fulfillJson(route, { rating })
    } else {
      await route.continue()
    }
  })
}

export async function mockSubmitRating(page: Page) {
  await page.route('**/api/web/skills/*/rating', async (route) => {
    if (route.request().method() === 'POST') {
      await fulfillJson(route, {})
    } else {
      await route.continue()
    }
  })
}
