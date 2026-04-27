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

      // Publish 21 skills to trigger pagination (PAGE_SIZE = 20)
      for (let i = 0; i < 21; i += 1) {
        await builder.publishSkill(namespace.slug, {
          name: `e2e-skill-${i}`,
          description: `Test skill ${i} for pagination`,
        })
      }

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

      // Second page: both buttons should be enabled (or Previous enabled, Next disabled if only 2 pages)
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
