import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Namespace Page Data (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows namespace page with request-seeded skill context', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      await builder.publishSkill(namespace.slug)

      await page.goto(`/space/${namespace.slug}`)

      await expect(page.getByText(`@${namespace.slug}`).first()).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Skills', exact: true })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
