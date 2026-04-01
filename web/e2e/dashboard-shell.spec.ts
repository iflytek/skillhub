import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Dashboard Shell (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('renders account summary and quick links', async ({ page }) => {
    await page.goto('/dashboard')

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await expect(page.getByText('Account Information')).toBeVisible()
    await expect(page.getByRole('link', { name: 'View API Tokens' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'View My Skills' }).first()).toBeVisible()
  })
})
