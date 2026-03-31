import { expect, test } from '@playwright/test'
import { mockAppShell, mockLandingPage } from './helpers/route-mocks'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Public Legal Pages', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders privacy and terms documents directly', async ({ page }) => {
    await mockAppShell(page)

    await page.goto('/privacy')
    await expect(page.getByRole('heading', { name: 'Privacy Policy' })).toBeVisible()
    await expect(page.getByText('Last updated: March 14, 2026')).toBeVisible()

    await page.goto('/terms')
    await expect(page.getByRole('heading', { name: 'Terms of Service' })).toBeVisible()
    await expect(page.getByText('Last updated: March 14, 2026')).toBeVisible()
  })

  test('reaches legal pages from the landing footer', async ({ page }) => {
    await mockLandingPage(page)

    await page.goto('/')
    await page.getByRole('link', { name: 'Privacy Policy' }).last().click()
    await expect(page).toHaveURL('/privacy')

    await page.goto('/')
    await page.getByRole('link', { name: 'Terms of Service' }).last().click()
    await expect(page).toHaveURL('/terms')
  })
})
