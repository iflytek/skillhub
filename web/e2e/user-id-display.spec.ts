import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('User ID Display', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows the signed-in account identity in the dashboard sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    const accountSummary = page.locator('aside').locator('.mb-4').first()
    await expect(accountSummary).toBeVisible()
    await expect(accountSummary).not.toHaveText(/^\s*$/)
  })

  test('shows user ID on profile settings page', async ({ page }) => {
    await page.goto('/settings/profile')
    await expect(page.getByRole('heading', { name: 'Profile Settings' })).toBeVisible()

    const label = page.getByText('User ID', { exact: true })
    await expect(label).toBeVisible()

    // The value is rendered in a sibling <p> element right after the label.
    // Locate the value paragraph within the same container.
    const container = page.locator('.space-y-2', { has: label })
    const value = container.locator('p')
    await expect(value).toBeVisible()

    const text = await value.textContent()
    expect(text?.trim()).not.toBe('')
    expect(text?.trim()).not.toBe('-')
  })
})
