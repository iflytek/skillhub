import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('User ID Display', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows user ID in dashboard account card', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.getByText('Account Information')).toBeVisible()

    const userIdText = page.getByText('User ID', { exact: false })
    await expect(userIdText).toBeVisible()

    // The dashboard renders "User ID: <value>" in a single element.
    // Verify the value is not empty by checking the text content is longer than just the label.
    const content = await userIdText.textContent()
    const valueAfterLabel = content?.replace(/^.*User ID[:\s]*/i, '').trim() ?? ''
    expect(valueAfterLabel.length).toBeGreaterThan(0)
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
