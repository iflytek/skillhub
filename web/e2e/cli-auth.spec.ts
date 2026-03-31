import { expect, test } from '@playwright/test'
import { user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockCliAuthPage } from './helpers/route-mocks'

test.describe('CLI Auth Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('redirects unauthenticated users to login and preserves CLI returnTo params', async ({ page }) => {
    await mockCliAuthPage(page, {
      authenticated: false,
    })

    await page.goto('/cli/auth?redirect_uri=http://localhost:3000/terms&state=state-123&label=My%20CLI')

    await expect(page.getByRole('heading', { name: 'Authorization failed' })).toBeVisible()
    await expect(page.getByText('You are not logged in')).toBeVisible()

    await page.getByRole('button', { name: 'Go to Login' }).click()

    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()

    const url = new URL(page.url())
    expect(url.pathname).toBe('/login')
    expect(url.searchParams.get('returnTo')).toBe('/cli/auth?redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fterms&state=state-123&label=My+CLI')
  })

  test('creates a CLI token and redirects to the loopback target with state and registry hash', async ({ page }) => {
    await mockCliAuthPage(page, {
      authenticated: true,
      currentUser: user('local-user', 'Local User', 'USER', { platformRoles: ['USER'] }),
      createTokenResult: {
        id: 9910,
        name: 'CLI token',
        token: 'sk_cli_success',
        tokenPrefix: 'sk_cli',
        createdAt: '2026-03-31T00:00:00Z',
      },
    })

    await page.goto('/cli/auth?redirect_uri=http://localhost:3000/terms&state=state-xyz&label_b64=Q0xJIFRvb2w')

    await expect(page).toHaveURL(/\/terms#/)
    await expect(page.getByRole('heading', { name: 'Terms of Service' })).toBeVisible()

    const redirected = new URL(page.url())
    const hashParams = new URLSearchParams(redirected.hash.slice(1))
    expect(hashParams.get('token')).toBe('sk_cli_success')
    expect(hashParams.get('state')).toBe('state-xyz')
    expect(hashParams.get('registry')).toBe('http://localhost:3000')
  })
})
