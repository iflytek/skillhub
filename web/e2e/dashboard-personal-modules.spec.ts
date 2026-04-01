import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Dashboard Personal Modules (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens stars page', async ({ page }) => {
    await page.goto('/dashboard/stars')
    await expect(page.getByRole('heading', { name: 'My Stars' })).toBeVisible()
  })

  test('opens notifications page', async ({ page }) => {
    await page.goto('/dashboard/notifications')
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible()
  })
})
