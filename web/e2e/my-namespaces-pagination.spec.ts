import { expect, test, type Page, type Route } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

function apiEnvelope(data: unknown) {
  return {
    code: 0,
    msg: 'success',
    data,
    timestamp: new Date().toISOString(),
    requestId: 'e2e-my-namespaces-pagination',
  }
}

function namespaceForPage(page: number) {
  return {
    id: page + 1,
    slug: `team-page-${page}`,
    displayName: `Team Page ${page}`,
    description: `Namespace page ${page}`,
    type: 'TEAM',
    status: 'ACTIVE',
    createdAt: '2026-07-29T00:00:00Z',
    updatedAt: '2026-07-29T00:00:00Z',
    currentUserRole: 'OWNER',
    immutable: false,
    canFreeze: false,
    canUnfreeze: false,
    canArchive: false,
    canRestore: false,
    canDelete: page === 2,
  }
}

async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(apiEnvelope(data)),
  })
}

test.describe('My Namespaces pagination', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-admin',
    })
  })

  test('backs up to the previous valid page after deleting the only item on the last page', async ({ page }) => {
    let totalNamespaces = 41
    const requestedPages: number[] = []

    await installDashboardMocks(page, requestedPages, () => totalNamespaces, () => {
      totalNamespaces = 40
    })

    await page.goto('/dashboard/namespaces')
    await expect(page.getByRole('heading', { name: 'My Namespaces' })).toBeVisible()
    await expect(page.getByText('@team-page-0')).toBeVisible()

    await page.getByRole('button', { name: 'Go to page 3' }).click()
    await expect.poll(() => requestedPages).toContain(2)
    await expect(page.getByText('@team-page-2')).toBeVisible()

    await page.getByTestId('delete-namespace-team-page-2').click()
    await expect(page.getByTestId('namespace-action-dialog-delete')).toBeVisible()
    await page.getByTestId('namespace-action-confirm-delete').click()

    await expect.poll(() => requestedPages.slice(-2)).toEqual([2, 1])
    await expect(page.getByText('@team-page-1')).toBeVisible()
    await expect(page.getByText('@team-page-2')).toHaveCount(0)
  })
})

async function installDashboardMocks(
  page: Page,
  requestedPages: number[],
  getTotalNamespaces: () => number,
  deleteLastNamespace: () => void,
) {
  await page.route('**/api/v1/auth/me', async (route) => {
    await fulfillJson(route, {
      userId: 'local-admin',
      displayName: 'Local Admin',
      email: 'local-admin@example.com',
      avatarUrl: '',
      oauthProvider: 'mock',
      platformRoles: ['SUPER_ADMIN'],
    })
  })

  await page.route('**/api/v1/auth/providers**', async (route) => {
    await fulfillJson(route, [])
  })

  await page.route('**/api/web/notifications/unread-count', async (route) => {
    await fulfillJson(route, { count: 0 })
  })

  await page.route('**/api/web/notifications/sse', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: '',
    })
  })

  await page.route('**/api/web/me/namespaces', async (route) => {
    await fulfillJson(route, [])
  })

  await page.route('**/api/web/me/namespaces/page?**', async (route) => {
    const url = new URL(route.request().url())
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const isPrimaryDashboardQuery = !url.searchParams.has('roles')
    if (isPrimaryDashboardQuery) {
      requestedPages.push(pageIndex)
    }

    const total = getTotalNamespaces()
    const isEmptiedLastPage = total === 40 && pageIndex === 2
    await fulfillJson(route, {
      items: isEmptiedLastPage ? [] : [namespaceForPage(pageIndex)],
      total,
      page: pageIndex,
      size,
    })
  })

  await page.route('**/api/web/namespaces/team-page-2', async (route) => {
    if (route.request().method() === 'DELETE') {
      deleteLastNamespace()
      await fulfillJson(route, { message: 'Namespace deleted successfully' })
      return
    }
    await route.fallback()
  })
}
