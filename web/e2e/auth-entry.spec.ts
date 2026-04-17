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

  test('renders google oauth action and sanitizes malicious returnTo', async ({ page }) => {
    await page.route('**/api/v1/auth/methods**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: [
            {
              id: 'oauth-google',
              methodType: 'OAUTH_REDIRECT',
              provider: 'google',
              displayName: 'Google',
              actionUrl: '/oauth2/authorization/google?returnTo=%2Fdashboard',
            },
          ],
        }),
      })
    })

    await page.goto('/login?returnTo=https%3A%2F%2Fevil.example%2Fsteal')

    await expect(page.getByRole('button', { name: 'OAuth' })).toBeVisible()
    await page.getByRole('button', { name: 'OAuth' }).click()

    const googleButton = page.locator('[data-provider-id="oauth-google"]')
    await expect(googleButton).toBeVisible()

    const oauthRequest = page.waitForRequest((request) => request.url().includes('/oauth2/authorization/google'))
    await googleButton.click()
    const request = await oauthRequest

    expect(request.url()).toContain('/oauth2/authorization/google')
    expect(request.url()).toContain('returnTo=%2Fdashboard')
    expect(request.url()).not.toContain('evil.example')
  })
})
