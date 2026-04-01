import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('My Namespaces Data (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows namespace created by request helper', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      await page.goto('/dashboard/namespaces')

      await expect(page.getByRole('heading', { name: 'My Namespaces' })).toBeVisible()
      await expect(page.getByText(`@${namespace.slug}`)).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
