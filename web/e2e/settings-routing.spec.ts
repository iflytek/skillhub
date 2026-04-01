import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Settings Routing (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('redirects accounts route to security settings', async ({ page }) => {
    await page.goto('/settings/accounts')
    await expect(page).toHaveURL('/settings/security')
    await expect(page.getByRole('heading', { name: 'Security Settings' })).toBeVisible()
  })
})
