import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Namespace Skill List Pagination (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows namespace page with skills and no pagination when under 20 skills', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      await builder.publishSkill(namespace.slug)

      await page.goto(`/space/${namespace.slug}`)

      await expect(page.getByText(`@${namespace.slug}`).first()).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Skills', exact: true })).toBeVisible()

      // Verify pagination controls do not appear when there are fewer than 20 skills
      await expect(page.getByRole('button', { name: 'Previous' })).toHaveCount(0)
      await expect(page.getByRole('button', { name: 'Next' })).toHaveCount(0)
    } finally {
      await builder.cleanup()
    }
  })

  test('shows pagination controls when there are more than 20 skills', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()

      // Build a fake skill list to simulate >20 skills without hitting rate limits.
      const fakeSkill = (i: number) => ({
        id: 90000 + i,
        namespace: namespace.slug,
        slug: `fake-skill-${i}`,
        displayName: `Fake Skill ${i}`,
        summary: `Pagination test skill ${i}`,
        downloadCount: 0,
        starCount: 0,
        ratingAvg: 0,
        ratingCount: 0,
        versions: [{ id: 90000 + i, version: '1.0.0', status: 'PUBLISHED' }],
      })

      // Intercept the search API with a regex (glob '?' is ambiguous for literal '?').
      // Return fully mocked responses to avoid needing real published skills.
      await page.route(/\/api\/web\/skills\?/, async (route) => {
        const url = new URL(route.request().url())
        const reqPage = Number(url.searchParams.get('page') ?? '0')
        const items = reqPage === 0
          ? Array.from({ length: 20 }, (_, i) => fakeSkill(i))
          : [fakeSkill(20)]

        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            msg: 'success',
            data: { items, total: 21, page: reqPage, size: 20 },
          }),
        })
      })

      await page.goto(`/space/${namespace.slug}`)

      await expect(page.getByText(`@${namespace.slug}`).first()).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Skills', exact: true })).toBeVisible()

      // Verify pagination controls appear
      const previousButton = page.getByRole('button', { name: 'Previous' }).first()
      const nextButton = page.getByRole('button', { name: 'Next' }).first()

      await expect(previousButton).toBeVisible()
      await expect(nextButton).toBeVisible()

      // First page: Previous should be disabled, Next should be enabled
      await expect(previousButton).toBeDisabled()
      await expect(nextButton).toBeEnabled()

      // Navigate to second page
      await nextButton.click()

      // Second page: Previous should be enabled
      await expect(previousButton).toBeEnabled()

      // Navigate back to first page
      await previousButton.click()
      await expect(previousButton).toBeDisabled()
      await expect(nextButton).toBeEnabled()
    } finally {
      await builder.cleanup()
    }
  })
})
