import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Settings Pages (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens profile settings page', async ({ page }) => {
    await page.goto('/settings/profile')
    await expect(page.getByRole('heading', { name: 'Profile Settings' })).toBeVisible()
  })

  test('shows validation when current password is missing', async ({ page }) => {
    await page.goto('/settings/security')
    await expect(page.getByRole('heading', { name: 'Security Settings' })).toBeVisible()

    await page.getByRole('button', { name: 'Update Password' }).click()
    await expect(page.getByText('Please enter your current password')).toBeVisible()
  })

  test('opens notification settings page', async ({ page }) => {
    await page.goto('/settings/notifications')
    await expect(page.getByRole('heading', { name: 'Notification Settings' })).toBeVisible()
  })
})
