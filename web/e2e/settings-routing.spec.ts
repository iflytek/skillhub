import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Settings Routing (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens account merge settings for an authenticated user', async ({ page }) => {
    await page.goto('/settings/accounts')
    await expect(page).toHaveURL('/settings/accounts')
    await expect(page.getByText('Initiate Account Merge')).toBeVisible()
    await expect(page.getByText('Approve from the second account')).toBeVisible()
  })
})
