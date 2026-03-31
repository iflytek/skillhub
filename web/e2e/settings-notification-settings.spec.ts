import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockNotificationSettingsPage } from './helpers/route-mocks'

test.describe('Notification Settings', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders current notification preferences and updates a toggle', async ({ page }) => {
    await mockNotificationSettingsPage(page, {
      preferences: [
        { category: 'PUBLISH', channel: 'IN_APP', enabled: true },
        { category: 'REVIEW', channel: 'IN_APP', enabled: true },
        { category: 'PROMOTION', channel: 'IN_APP', enabled: true },
        { category: 'REPORT', channel: 'IN_APP', enabled: false },
      ],
    })

    await page.goto('/settings/notifications')

    await expect(page.getByText('Notification Settings')).toBeVisible()

    const publishSwitch = page.getByRole('switch', { name: 'Publish Notifications' })
    const reportSwitch = page.getByRole('switch', { name: 'Report Notifications' })

    await expect(publishSwitch).toHaveAttribute('aria-checked', 'true')
    await expect(reportSwitch).toHaveAttribute('aria-checked', 'false')

    await reportSwitch.click()
    await expect(reportSwitch).toHaveAttribute('aria-checked', 'true')
  })

  test('defaults missing categories to enabled', async ({ page }) => {
    await mockNotificationSettingsPage(page, {
      preferences: [],
    })

    await page.goto('/settings/notifications')

    await expect(page.getByRole('switch', { name: 'Promotion Notifications' })).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByRole('switch', { name: 'Review Notifications' })).toHaveAttribute('aria-checked', 'true')
  })
})
