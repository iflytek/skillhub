import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Namespace Members Data (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens members management for writable namespace', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()

      await page.goto(`/dashboard/namespaces/${namespace.slug}/members`)

      await expect(page.getByRole('heading', { name: 'Member Management' })).toBeVisible()
      await expect(page.getByText(`@${namespace.slug}`).first()).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
