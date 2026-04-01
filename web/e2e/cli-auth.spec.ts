import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('CLI Auth (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('shows error for missing redirect params', async ({ page }) => {
    await page.goto('/cli/auth')
    await expect(page.getByRole('heading', { name: 'Authorization failed' })).toBeVisible()
  })
})
