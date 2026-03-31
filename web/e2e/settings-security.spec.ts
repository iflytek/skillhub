import { expect, test } from '@playwright/test'
import { user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockSecurityPage } from './helpers/route-mocks'

test.describe('Security Settings', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('shows validation when the current password is missing', async ({ page }) => {
    await mockSecurityPage(page, {
      currentUser: user('local-user', 'Local User', 'USER', { oauthProvider: undefined }),
    })

    await page.goto('/settings/security')
    await page.getByRole('button', { name: 'Update Password' }).click()

    await expect(page.getByText('Please enter your current password')).toBeVisible()
  })

  test('redirects to login after a successful password change', async ({ page }) => {
    await mockSecurityPage(page, {
      currentUser: user('local-user', 'Local User', 'USER', { oauthProvider: undefined }),
    })

    await page.goto('/settings/security')
    await page.locator('#current-password').fill('old-password')
    await page.locator('#new-password').fill('new-password')
    await page.getByRole('button', { name: 'Update Password' }).click()

    await expect(page).toHaveURL('/login?returnTo=')
    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()
  })

  test('shows an error when the current password is invalid', async ({ page }) => {
    await mockSecurityPage(page, {
      currentUser: user('local-user', 'Local User', 'USER', { oauthProvider: undefined }),
      changePasswordSucceeds: false,
      changePasswordStatus: 401,
    })

    await page.goto('/settings/security')
    await page.locator('#current-password').fill('wrong-password')
    await page.locator('#new-password').fill('new-password')
    await page.getByRole('button', { name: 'Update Password' }).click()

    await expect(page.getByText('Current password is incorrect')).toBeVisible()
  })
})
