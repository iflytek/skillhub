import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Dashboard Shell (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('renders account navigation and overview links', async ({ page }) => {
    await page.goto('/dashboard')

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    const sidebar = page.getByRole('complementary')
    await expect(sidebar.getByRole('link', { name: 'Profile', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: 'My Skills', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: 'API Tokens', exact: true })).toBeVisible()
    await expect(page.getByText('View and manage all your published skills')).toBeVisible()
  })
})
