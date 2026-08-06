import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

type PromotionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

function promotion(id: number, status: PromotionStatus, name: string, reviewedAt: string | null = null) {
  return {
    id,
    sourceSkillId: id + 100,
    sourceSkillDisplayName: name,
    sourceSkillSummary: `Summary for ${name}`,
    sourceNamespace: 'team-ai',
    sourceSkillSlug: name.toLowerCase().replaceAll(' ', '-'),
    sourceVersion: '1.3.0',
    sourceVersionFileCount: 23,
    sourceVersionTotalSize: 1_843_200,
    sourceSkillDownloadCount: 18,
    sourceSkillStarCount: 5,
    targetNamespace: 'global',
    targetSkillId: status === 'PENDING' ? undefined : id + 200,
    status,
    submittedBy: 'owner-1',
    submittedByName: 'Owner One',
    reviewedBy: status === 'PENDING' ? undefined : 'admin-1',
    reviewedByName: status === 'PENDING' ? undefined : 'Admin One',
    reviewComment: status === 'REJECTED' ? 'Needs clearer documentation before promotion.' : 'Looks good.',
    submittedAt: '2026-06-18T12:00:00Z',
    reviewedAt,
  }
}

test.describe('Promotion review dashboard', () => {
  let unexpectedPromotionRequests: string[]
  let expectedPromotionRequests: string[]

  test.beforeEach(async ({ page }) => {
    unexpectedPromotionRequests = []
    expectedPromotionRequests = []
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-admin',
    })

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            userId: 'local-admin',
            displayName: 'Local Admin',
            email: 'local-admin@example.com',
            avatarUrl: '',
            oauthProvider: 'mock',
            platformRoles: ['SUPER_ADMIN'],
          },
          timestamp: new Date().toISOString(),
          requestId: 'e2e-auth',
        }),
      })
    })
    await page.route('**/api/web/notifications/unread-count', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: { count: 0 },
          timestamp: new Date().toISOString(),
          requestId: 'e2e-notifications',
        }),
      })
    })
    await page.route('**/api/web/me/namespaces/page?**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            items: [],
            total: 0,
            page: 0,
            size: 20,
          },
          timestamp: new Date().toISOString(),
          requestId: 'e2e-namespaces',
        }),
      })
    })
    await page.route('**/api/web/notifications/sse', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: '',
      })
    })
  })

  async function installPromotionRouteMock(page: Page, expectedSignatures: string[]) {
    expectedPromotionRequests = [...expectedSignatures]

    await page.route('**/api/web/promotions**', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const allowedParams = new Set(['status', 'page', 'size', 'sortBy', 'sortDirection'])
      const extraParams = Array.from(url.searchParams.keys()).filter((key) => !allowedParams.has(key))
      if (request.method() !== 'GET' || url.pathname !== '/api/web/promotions' || extraParams.length > 0) {
        unexpectedPromotionRequests.push(url.toString())
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 400,
            msg: 'unexpected promotion request shape',
            data: null,
            timestamp: new Date().toISOString(),
            requestId: 'e2e-promotions-error',
          }),
        })
        return
      }

      const statusParam = url.searchParams.get('status')
      if (statusParam === null) {
        unexpectedPromotionRequests.push(url.toString())
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 400,
            msg: 'promotion request must include explicit status',
            data: null,
            timestamp: new Date().toISOString(),
            requestId: 'e2e-promotions-error',
          }),
        })
        return
      }

      const statusValues: PromotionStatus[] = ['PENDING', 'APPROVED', 'REJECTED']
      if (!statusValues.includes(statusParam as PromotionStatus)) {
        unexpectedPromotionRequests.push(url.toString())
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 400,
            msg: `unexpected status ${statusParam}`,
            data: null,
            timestamp: new Date().toISOString(),
            requestId: 'e2e-promotions-error',
          }),
        })
        return
      }

      const status = statusParam as PromotionStatus
      const sortBy = url.searchParams.get('sortBy')
      const sortDirectionParam = url.searchParams.get('sortDirection')
      const requestSignature = `${status}|${sortBy ?? 'none'}|${sortDirectionParam ?? 'none'}`
      const expectedSignature = expectedPromotionRequests.shift()
      if (requestSignature !== expectedSignature) {
        unexpectedPromotionRequests.push(`${url.toString()} expected ${expectedSignature ?? 'no more requests'}`)
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 400,
            msg: 'unexpected promotion request order',
            data: null,
            timestamp: new Date().toISOString(),
            requestId: 'e2e-promotions-error',
          }),
        })
        return
      }

      if (status === 'PENDING' && (sortBy !== null || sortDirectionParam !== null)) {
        unexpectedPromotionRequests.push(url.toString())
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 400,
            msg: 'pending request must not include history sort params',
            data: null,
            timestamp: new Date().toISOString(),
            requestId: 'e2e-promotions-error',
          }),
        })
        return
      }

      if (status !== 'PENDING' && (sortBy !== 'reviewedAt' || !['ASC', 'DESC'].includes(sortDirectionParam ?? ''))) {
        unexpectedPromotionRequests.push(url.toString())
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 400,
            msg: 'history request must include reviewedAt sort params',
            data: null,
            timestamp: new Date().toISOString(),
            requestId: 'e2e-promotions-error',
          }),
        })
        return
      }

      const dataByStatus: Record<PromotionStatus, ReturnType<typeof promotion>[]> = {
        PENDING: [promotion(1, 'PENDING', 'Knowledge Helper')],
        APPROVED: [
          promotion(2, 'APPROVED', 'Newest Approved', '2026-06-18T09:00:00Z'),
          promotion(3, 'APPROVED', 'Oldest Approved', '2026-06-17T09:00:00Z'),
        ],
        REJECTED: [
          promotion(4, 'REJECTED', 'Newest Rejected', '2026-06-18T08:00:00Z'),
          promotion(5, 'REJECTED', 'Oldest Rejected', '2026-06-16T08:00:00Z'),
        ],
      }

      const items = [...dataByStatus[status]]
      if (status !== 'PENDING' && sortDirectionParam === 'ASC') {
        items.reverse()
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: { items, total: items.length, page: 0, size: 20 },
          timestamp: new Date().toISOString(),
          requestId: 'e2e-promotions',
        }),
      })
    })
  }

  function expectPromotionRequestsSatisfied() {
    expect(unexpectedPromotionRequests).toEqual([])
    expect(expectedPromotionRequests).toEqual([])
  }

  test('shows enhanced pending cards and sorts approved/rejected history by reviewed time', async ({ page }) => {
    await installPromotionRouteMock(page, [
      'PENDING|none|none',
      'APPROVED|reviewedAt|DESC',
      'APPROVED|reviewedAt|ASC',
      'REJECTED|reviewedAt|DESC',
      'REJECTED|reviewedAt|ASC',
    ])

    const pendingRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'PENDING'
        && !url.searchParams.has('sortBy')
        && !url.searchParams.has('sortDirection')
    })

    await page.goto('/dashboard/promotions')
    await pendingRequest

    await expect(page.getByRole('heading', { name: 'Promotion Review' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Knowledge Helper' })).toBeVisible()
    await expect(page.getByText('@team-ai/knowledge-helper -> @global')).toBeVisible()
    await expect(page.getByText('Summary for Knowledge Helper')).toBeVisible()
    await expect(page.getByText(/Jun 18, 2026/)).toBeVisible()
    await expect(page.getByText('v1.3.0')).toBeVisible()
    await expect(page.getByText('Submitter Owner One')).toBeVisible()
    await expect(page.getByText('23 files')).toBeVisible()
    await expect(page.getByText('1.8 MB')).toBeVisible()
    await expect(page.getByText('18 downloads')).toBeVisible()
    await expect(page.getByText('5 stars')).toBeVisible()

    const approvedDescRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'APPROVED'
        && url.searchParams.get('sortBy') === 'reviewedAt'
        && url.searchParams.get('sortDirection') === 'DESC'
    })
    await page.getByRole('tab', { name: 'Approved' }).click()
    await approvedDescRequest
    const approvedTable = page.getByRole('table', { name: 'Promotion history' })
    await expect(approvedTable).toBeVisible()
    await expect(approvedTable.getByRole('row').nth(1)).toContainText('Newest Approved')
    await expect(approvedTable.getByRole('row').nth(2)).toContainText('Oldest Approved')

    const approvedAscRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'APPROVED'
        && url.searchParams.get('sortBy') === 'reviewedAt'
        && url.searchParams.get('sortDirection') === 'ASC'
    })
    await page.getByRole('button', { name: 'Sort by reviewed time ascending' }).click()
    await approvedAscRequest
    await expect(page.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeVisible()
    await expect(approvedTable.getByRole('row').nth(1)).toContainText('Oldest Approved')
    await expect(approvedTable.getByRole('row').nth(2)).toContainText('Newest Approved')

    const rejectedDescRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'REJECTED'
        && url.searchParams.get('sortBy') === 'reviewedAt'
        && url.searchParams.get('sortDirection') === 'DESC'
    })
    await page.getByRole('tab', { name: 'Rejected' }).click()
    await rejectedDescRequest
    await expect(page.getByRole('button', { name: 'Sort by reviewed time ascending' })).toBeVisible()

    const rejectedAscRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'REJECTED'
        && url.searchParams.get('sortBy') === 'reviewedAt'
        && url.searchParams.get('sortDirection') === 'ASC'
    })
    await page.getByRole('button', { name: 'Sort by reviewed time ascending' }).click()
    await rejectedAscRequest
    await expect(page.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeVisible()

    await page.getByRole('tab', { name: 'Approved' }).click()
    await expect(page.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeVisible()
    expectPromotionRequestsSatisfied()
  })

  test('sorter can be toggled from the keyboard', async ({ page }) => {
    await installPromotionRouteMock(page, [
      'PENDING|none|none',
      'APPROVED|reviewedAt|DESC',
      'APPROVED|reviewedAt|ASC',
    ])

    await page.goto('/dashboard/promotions')

    const approvedDescRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'APPROVED'
        && url.searchParams.get('sortBy') === 'reviewedAt'
        && url.searchParams.get('sortDirection') === 'DESC'
    })
    await page.getByRole('tab', { name: 'Approved' }).click()
    await approvedDescRequest

    const approvedAscRequest = page.waitForRequest((request) => {
      const url = new URL(request.url())
      return url.pathname === '/api/web/promotions'
        && url.searchParams.get('status') === 'APPROVED'
        && url.searchParams.get('sortBy') === 'reviewedAt'
        && url.searchParams.get('sortDirection') === 'ASC'
    })
    await page.getByRole('button', { name: 'Sort by reviewed time ascending' }).focus()
    await page.keyboard.press('Enter')
    await approvedAscRequest

    await expect(page.getByRole('button', { name: 'Sort by reviewed time descending' })).toBeVisible()
    expectPromotionRequestsSatisfied()
  })
})
