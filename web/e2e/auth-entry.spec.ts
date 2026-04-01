import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Auth Entry (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates required fields and preserves returnTo on register link', async ({ page }) => {
    await page.goto('/login?returnTo=%2Fdashboard%2Ftokens')

    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()

    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()

    await page.getByRole('link', { name: 'Sign up now' }).click()
    await expect(page).toHaveURL('/register?returnTo=%2Fdashboard%2Ftokens')
  })
})
