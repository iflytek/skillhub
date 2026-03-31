import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('My Skills Data (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows request-published skill in dashboard list', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug)

      await page.goto('/dashboard/skills')

      await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()
      await expect(page.getByText(`@${skill.namespace}`).first()).toBeVisible()
      await expect(page.getByText(`v${skill.version}`).first()).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
