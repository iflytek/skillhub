import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'
import { loginWithCredentials } from './helpers/session'

function getOptionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

function adminCredentials() {
  return {
    username: getOptionalEnv('E2E_ADMIN_USERNAME') ?? getOptionalEnv('BOOTSTRAP_ADMIN_USERNAME') ?? 'admin',
    password: getOptionalEnv('E2E_ADMIN_PASSWORD') ?? getOptionalEnv('BOOTSTRAP_ADMIN_PASSWORD') ?? 'ChangeMe!2026',
  }
}

async function currentDisplayName(page: Page, headers?: Record<string, string>): Promise<string> {
  const response = await page.context().request.get('/api/v1/auth/me', { headers })
  expect(response.ok()).toBeTruthy()
  const body = await response.json() as { data: { displayName: string } }
  return body.data.displayName
}

test.describe('Security Settings capability (Real API)', () => {
  test.use({ baseURL: 'http://127.0.0.1:3000' })

  test('shows the security menu entry and password form for local admin accounts', async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await loginWithCredentials(page, adminCredentials(), testInfo)
    const displayName = await currentDisplayName(page)

    await page.goto('/settings/security')
    await expect(page.getByRole('heading', { name: 'Security Settings' })).toBeVisible()
    await expect(page.getByLabel('Current Password')).toBeVisible()
    await expect(page.getByLabel('New Password')).toBeVisible()

    await page.getByRole('button', { name: displayName }).click()
    await expect(page.getByRole('link', { name: 'Security Settings' })).toBeVisible()
  })

  test('hides the security menu entry and rejects password changes without a local credential', async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-user',
    })
    const displayName = await currentDisplayName(page, { 'X-Mock-User-Id': 'local-user' })

    await page.goto('/settings/security')

    await expect(page.getByRole('heading', { name: 'Security Settings' })).toBeVisible()
    await expect(page.getByText('Password changes are unavailable for this account.')).toBeVisible()
    await expect(page.getByLabel('Current Password')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Update Password' })).toHaveCount(0)

    await page.getByRole('button', { name: displayName }).click()
    await expect(page.getByRole('link', { name: 'Security Settings' })).toHaveCount(0)

    const response = await page.context().request.post('/api/v1/auth/local/change-password', {
      data: {
        currentPassword: 'Passw0rd!123',
        newPassword: 'N3wPassw0rd!123',
      },
      headers: await csrfHeaders(page, { 'X-Mock-User-Id': 'local-user' }),
    })
    expect(response.status()).toBe(400)
  })
})
