import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockProfilePage } from './helpers/route-mocks'

test.describe('Profile Settings', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates display name input and submits review-required changes', async ({ page }) => {
    await mockProfilePage(page)

    await page.goto('/settings/profile')

    await expect(page.getByText('Profile Settings')).toBeVisible()
    await page.getByRole('button', { name: 'Edit' }).click()

    await page.locator('#field-displayName').fill('x')
    await page.getByRole('button', { name: 'Save' }).click()
    await expect(page.getByText('Display name must be 2-32 characters.')).toBeVisible()

    await page.locator('#field-displayName').fill('Ops Reviewer')
    await page.getByRole('button', { name: 'Save' }).click()

    await expect(page.getByText('Your display name change to "Ops Reviewer" is pending review.')).toBeVisible()
  })

  test('renders rejected review feedback when pending profile changes were rejected', async ({ page }) => {
    await mockProfilePage(page, {
      profile: {
        displayName: 'Local User',
        avatarUrl: null,
        email: 'local-user@example.com',
        pendingChanges: {
          status: 'REJECTED',
          changes: { displayName: 'Rejected Name' },
          reviewComment: 'Need a more specific display name',
          createdAt: '2026-03-30T00:00:00Z',
        },
        fieldPolicies: {
          displayName: { editable: true, requiresReview: true },
          email: { editable: false, requiresReview: false },
        },
      },
    })

    await page.goto('/settings/profile')

    await expect(page.getByText('Your display name change was rejected.')).toBeVisible()
    await expect(page.getByText('Reason: Need a more specific display name')).toBeVisible()
  })
})
