import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Tokens Page (Real API)', () => {
  test.describe.configure({ timeout: 90_000 })

  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('renders token management page and create action', async ({ page }) => {
    await page.goto('/dashboard/tokens')

    await expect(page.getByRole('heading', { name: 'Token Management' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create Token' })).toBeVisible()
  })
})
