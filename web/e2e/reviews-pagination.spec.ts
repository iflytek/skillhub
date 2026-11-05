import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { loginWithCredentials } from './helpers/session'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface ReviewPageData {
  total: number
  size: number
  items: Array<{ namespace: string; subjectSlug?: string; skillSlug: string }>
}

async function fetchReviewPageMeta(page: Page, status: ReviewStatus, pageIndex = 0): Promise<ReviewPageData> {
  const response = await page.request.get(`/api/web/reviews?status=${status}&page=${pageIndex}&size=20&sortDirection=DESC`)
  const body = await response.json() as ApiEnvelope<ReviewPageData>
  if (!response.ok() || body.code !== 0) {
    throw new Error(`Failed to query reviews for ${status}: status=${response.status()} code=${body.code} msg=${body.msg}`)
  }
  return body.data
}

test.describe('Review Management Pagination (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await loginWithCredentials(page, {
      username: process.env.E2E_ADMIN_USERNAME ?? process.env.BOOTSTRAP_ADMIN_USERNAME ?? 'admin',
      password: process.env.E2E_ADMIN_PASSWORD ?? process.env.BOOTSTRAP_ADMIN_PASSWORD ?? 'ChangeMe!2026',
    }, testInfo)
  })

  test('matches applied status filters and pagination with real review totals', async ({ page }) => {
    const statuses: ReviewStatus[] = ['PENDING', 'APPROVED', 'REJECTED']
    const labels: Record<ReviewStatus, string> = { PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected' }
    await page.goto('/dashboard/reviews')
    await expect(page.getByRole('heading', { name: 'Review center', exact: true })).toBeVisible()
    const filters = page.getByRole('form', { name: 'Filter requests' })

    for (const status of statuses) {
      const meta = await fetchReviewPageMeta(page, status)
      await filters.getByRole('combobox', { name: 'Review status' }).click()
      await page.getByRole('option', { name: labels[status], exact: true }).click()
      const result = page.waitForResponse((response) => {
        const url = new URL(response.url())
        return url.pathname === '/api/web/reviews' && url.searchParams.get('status') === status && url.searchParams.get('page') === '0'
      })
      await filters.getByRole('button', { name: 'Search', exact: true }).click()
      expect((await result).ok()).toBe(true)
      await expect(page).toHaveURL((url) => url.searchParams.get('page') === null && (url.searchParams.get('status') ?? 'PENDING') === status)
      await expect(page.getByRole('heading', { name: `Results · ${meta.total} requests`, exact: true })).toBeVisible()
      const totalPages = meta.size > 0 ? Math.ceil(meta.total / meta.size) : 0

      if (meta.total === 0) {
        await expect(page.getByText('No matching requests', { exact: true })).toBeVisible()
        await expect(page.getByRole('button', { name: 'Previous' })).toHaveCount(0)
        await expect(page.getByRole('button', { name: 'Next' })).toHaveCount(0)
        continue
      }

      const rows = page.getByRole('table').getByRole('row').filter({ has: page.locator('td') })
      await expect(rows).toHaveCount(meta.items.length)
      for (const [index, item] of meta.items.entries()) {
        await expect(rows.nth(index).getByText(`@${item.namespace}/${item.subjectSlug || item.skillSlug}`, { exact: true })).toBeVisible()
      }
      const previousButton = page.getByRole('button', { name: 'Previous' }).first()
      const nextButton = page.getByRole('button', { name: 'Next' }).first()
      await expect(previousButton).toBeVisible()
      await expect(nextButton).toBeVisible()
      await expect(previousButton).toBeDisabled()
      await expect(page.getByText(`${meta.total} requests · Page 1`, { exact: true })).toBeVisible()

      if (totalPages > 1) {
        await expect(nextButton).toBeEnabled()
        const nextMeta = await fetchReviewPageMeta(page, status, 1)
        await nextButton.click()
        await expect(page).toHaveURL((url) => url.searchParams.get('page') === '1')
        await expect(page.getByText(`${meta.total} requests · Page 2`, { exact: true })).toBeVisible()
        await expect(rows).toHaveCount(nextMeta.items.length)
        for (const [index, item] of nextMeta.items.entries()) {
          await expect(rows.nth(index).getByText(`@${item.namespace}/${item.subjectSlug || item.skillSlug}`, { exact: true })).toBeVisible()
        }
      } else {
        await expect(nextButton).toBeDisabled()
      }
    }
  })

  test('opens the profile review queue from the review type search param', async ({ page }) => {
    await page.goto('/dashboard/reviews?type=profile')
    await expect(page).toHaveURL(/\/dashboard\/reviews\?type=profile$/)
    await expect(page.getByRole('heading', { name: 'Review center', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Profile Review Queue' })).toBeVisible()
  })
})
