import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface ReviewPageData {
  total: number
  size: number
}

async function fetchReviewPageMeta(page: Page, status: ReviewStatus): Promise<ReviewPageData> {
  const response = await page.request.get(`/api/web/reviews?status=${status}&page=0&size=20&sortDirection=DESC`)
  const body = await response.json() as ApiEnvelope<ReviewPageData>
  if (!response.ok() || body.code !== 0) {
    throw new Error(`Failed to query reviews for ${status}: status=${response.status()} code=${body.code} msg=${body.msg}`)
  }
  return body.data
}

test.describe('Review Management Pagination (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-admin',
    })
  })

  test('matches pagination rendering with real review totals', async ({ page }) => {
    const statuses: ReviewStatus[] = ['PENDING', 'APPROVED', 'REJECTED']
    const metaByStatus = new Map<ReviewStatus, ReviewPageData>()

    for (const status of statuses) {
      metaByStatus.set(status, await fetchReviewPageMeta(page, status))
    }

    await page.goto('/dashboard/reviews')
    await expect(page.getByRole('heading', { name: 'Review Center' })).toBeVisible()

    const tabMeta: Record<ReviewStatus, { tabLabel: string; summaryPrefix: string }> = {
      PENDING: { tabLabel: 'Pending', summaryPrefix: 'Total' },
      APPROVED: { tabLabel: 'Approved', summaryPrefix: 'Total' },
      REJECTED: { tabLabel: 'Rejected', summaryPrefix: 'Total' },
    }

    for (const status of statuses) {
      await page.getByRole('button', { name: tabMeta[status].tabLabel }).click()

      const meta = metaByStatus.get(status)
      if (!meta) {
        throw new Error(`Missing metadata for ${status}`)
      }
      const totalPages = meta.size > 0 ? Math.ceil(meta.total / meta.size) : 0

      if (meta.total === 0) {
        await expect(page.getByText('No review tasks')).toBeVisible()
        await expect(page.getByRole('button', { name: 'Previous' })).toHaveCount(0)
        await expect(page.getByRole('button', { name: 'Next' })).toHaveCount(0)
        continue
      }

      const previousButton = page.getByRole('button', { name: 'Previous' }).first()
      const nextButton = page.getByRole('button', { name: 'Next' }).first()
      await expect(previousButton).toBeVisible()
      await expect(nextButton).toBeVisible()
      await expect(previousButton).toBeDisabled()

      if (totalPages > 1) {
        await expect(page.getByText(new RegExp(`${tabMeta[status].summaryPrefix} ${meta.total} records, page 1`))).toBeVisible()
        await expect(nextButton).toBeEnabled()
        await nextButton.click()
        await expect(page.getByText(new RegExp(`${tabMeta[status].summaryPrefix} ${meta.total} records, page 2`))).toBeVisible()
      } else {
        await expect(nextButton).toBeDisabled()
      }
    }
  })
})
