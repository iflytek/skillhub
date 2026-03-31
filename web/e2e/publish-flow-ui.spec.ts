import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Publish Flow UI (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('publishes a generated skill package from dashboard page', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const packagePath = builder.createSkillPackageFile()

      await page.goto('/dashboard/publish')
      await expect(page.getByRole('heading', { name: 'Publish Skill' })).toBeVisible()

      await page.locator('#namespace').click()
      await page.getByText(new RegExp(`\\(@${namespace.slug}\\)`)).first().click()

      await page.locator('input[type="file"]').setInputFiles(packagePath)
      await page.getByRole('button', { name: 'Confirm Publish' }).click()

      await expect(page).toHaveURL('/dashboard/skills')
      await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
