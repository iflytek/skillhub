import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Namespace Review Detail Access (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens namespace review detail from the namespace review list', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const seeded = await builder.createReviewData()

      await page.goto(`/dashboard/namespaces/${seeded.namespace.slug}/reviews`)

      await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
      await expect(page.getByText(`${seeded.namespace.slug}/${seeded.skill.slug}`)).toBeVisible()

      await page.getByRole('link', { name: 'Open review' }).first().click()

      await expect(page).toHaveURL(new RegExp(`/dashboard/namespaces/${seeded.namespace.slug}/reviews/\\d+$`))
      await expect(page.getByRole('heading', { name: 'Review Detail' })).toBeVisible()
      await expect(page.getByText(`${seeded.namespace.slug}/${seeded.skill.slug}`).first()).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })

  test('redirects /dashboard/reviews to a namespace review page for namespace operators', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      await builder.createReviewData()

      await page.goto('/dashboard/reviews')

      await expect(page).toHaveURL(/\/dashboard\/namespaces\/.+\/reviews$/)
      await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })

  test('redirects namespace review detail opened through the global detail route', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const seeded = await builder.createReviewData()

      await page.goto(`/dashboard/namespaces/${seeded.namespace.slug}/reviews`)
      const reviewLink = page.getByRole('link', { name: 'Open review' }).first()
      const reviewPath = await reviewLink.getAttribute('href')
      const reviewId = reviewPath?.match(/\/reviews\/(\d+)$/)?.[1]
      if (!reviewId) {
        throw new Error(`Failed to resolve review id from href: ${reviewPath}`)
      }

      await page.goto(`/dashboard/reviews/${reviewId}`)

      await expect(page).toHaveURL(new RegExp(`/dashboard/namespaces/${seeded.namespace.slug}/reviews/${reviewId}$`))
      await expect(page.getByRole('heading', { name: 'Review Detail' })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
