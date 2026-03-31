import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Protected Routes (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('redirects anonymous users from dashboard and admin routes', async ({ page }) => {
    await page.goto('/dashboard/publish')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard%2Fpublish$/)

    await page.goto('/dashboard/governance')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard%2Fgovernance$/)

    await page.goto('/dashboard/reviews')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard%2Freviews$/)

    await page.goto('/admin/users')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fadmin%2Fusers$/)
  })
})
