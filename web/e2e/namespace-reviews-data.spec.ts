import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { createNamespaceReviewData } from './helpers/review-seed'

test.describe('Namespace Reviews Data (Real API)', () => {
  test.describe.configure({ timeout: 120_000 })

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('opens namespace reviews page with seeded review data context', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)
      await page.goto(`/dashboard/namespaces/${seeded.namespace.slug}/reviews`)

      await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
      await expect(page.getByText(`Review tasks for ${seeded.namespace.displayName}`)).toBeVisible()
    } finally {
      await seeded?.cleanup()
    }
  })
})
