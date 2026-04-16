import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { createNamespaceReviewData } from './helpers/review-seed'

test.describe('Namespace Review Detail Access (Real API)', () => {
  test.describe.configure({ timeout: 120_000 })

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('opens namespace review detail from the namespace review list', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)

      await page.goto(`/dashboard/namespaces/${seeded.namespace.slug}/reviews`)

      await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
      await expect(page.getByText(`${seeded.namespace.slug}/${seeded.skill.slug}`)).toBeVisible()

      await page.getByRole('link', { name: 'Open review' }).first().click()

      await expect(page).toHaveURL(new RegExp(`/dashboard/namespaces/${seeded.namespace.slug}/reviews/\\d+$`))
      await expect(page.getByRole('heading', { name: 'Review Detail' })).toBeVisible()
      await expect(page.getByText(`${seeded.namespace.slug}/${seeded.skill.slug}`).first()).toBeVisible()
    } finally {
      await seeded?.cleanup()
    }
  })

  test('redirects /dashboard/reviews to a namespace review page for namespace operators', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)

      await page.goto('/dashboard/reviews')

      await expect(page).toHaveURL(/\/dashboard\/namespaces\/.+\/reviews$/)
      await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
    } finally {
      await seeded?.cleanup()
    }
  })

  test('redirects namespace review detail opened through the global detail route', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)

      await page.goto(`/dashboard/reviews/${seeded.reviewTaskId}`)

      await expect(page).toHaveURL(new RegExp(`/dashboard/namespaces/${seeded.namespace.slug}/reviews/${seeded.reviewTaskId}$`))
      await expect(page.getByRole('heading', { name: 'Review Detail' })).toBeVisible()
    } finally {
      await seeded?.cleanup()
    }
  })
})
