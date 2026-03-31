import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockNotificationsPage } from './helpers/route-mocks'

function notificationItem(id: number, overrides: Partial<{
  category: 'PUBLISH' | 'REVIEW' | 'PROMOTION' | 'REPORT'
  eventType: string
  title: string
  bodyJson: string
  status: 'UNREAD' | 'READ'
  createdAt: string
  targetRoute: string
  entityType: string
  entityId: number
}> = {}) {
  return {
    id,
    category: 'REVIEW' as const,
    eventType: 'REVIEW_SUBMITTED',
    title: 'Review submitted',
    bodyJson: JSON.stringify({ skillName: 'Review Skill', version: '1.0.0' }),
    status: 'UNREAD' as const,
    createdAt: '2026-03-31T00:00:00Z',
    targetRoute: '/dashboard',
    entityType: 'review',
    entityId: id,
    ...overrides,
  }
}

test.describe('Notifications Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('filters notifications by category', async ({ page }) => {
    await mockNotificationsPage(page, {
      notifications: [
        notificationItem(1, { category: 'REVIEW', eventType: 'REVIEW_SUBMITTED', title: 'Review submitted', status: 'UNREAD' }),
        notificationItem(2, { category: 'REPORT', eventType: 'REPORT_RESOLVED', title: 'Report resolved', status: 'READ', bodyJson: JSON.stringify({ skillName: 'Flagged Skill' }) }),
      ],
    })

    await page.goto('/dashboard/notifications')

    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible()
    await page.getByRole('button', { name: 'Review', exact: true }).click({ force: true })
    await expect(page.getByText('Review submitted')).toBeVisible()
    await expect(page.getByText('Report resolved')).not.toBeVisible()
  })

  test('opens the notification target route when clicking an unread item', async ({ page }) => {
    await mockNotificationsPage(page, {
      notifications: [
        notificationItem(3, { category: 'REVIEW', eventType: 'REVIEW_APPROVED', title: 'Review approved', status: 'UNREAD', targetRoute: '/terms' }),
      ],
    })

    await page.goto('/dashboard/notifications')
    await page.getByRole('button', { name: /Review approved/ }).click({ force: true })

    await expect(page).toHaveURL('/terms')
    await expect(page.getByRole('heading', { name: 'Terms of Service' })).toBeVisible()
  })
})
