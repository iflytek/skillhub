import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Route Guards (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('redirects anonymous users to login for protected routes', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard$/)

    await page.goto('/space/team-alpha')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fspace%2Fteam-alpha$/)
  })

  test('allows authenticated users to open dashboard', async ({ page }, testInfo) => {
    await registerSession(page, testInfo)

    await page.goto('/dashboard')
    await expect(page).toHaveURL('/dashboard')
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })
})
