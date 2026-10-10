import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('My Skills Navigation (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens seeded skill detail from dashboard list and returns back', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug)

      await page.goto('/dashboard/skills')
      await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()

      await page.getByText(`@${skill.namespace}`).first().click()
      await expect(page).toHaveURL(new RegExp(`/space/${skill.namespace}/${skill.slug}\\?returnTo=%2Fdashboard%2Fskills$`))

      await page.getByRole('button', { name: 'Back' }).click()
      await expect(page).toHaveURL('/dashboard/skills')
    } finally {
      await builder.cleanup()
    }
  })
})
