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

export async function setEnglishLocale(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'en')
  })
}

export async function mockStaticApis(
  page: Page,
  options: {
    authenticated?: boolean
  },
) {
  const authenticated = options.authenticated ?? false

  await page.route('**/api/v1/auth/me', async (route) => {
    if (!authenticated) {
      await fulfillJson(route, null, { status: 401, msg: 'Unauthorized' })
      return
    }

    await fulfillJson(route, {
      userId: 'local-user',
      displayName: 'Local User',
      platformRoles: [],
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
