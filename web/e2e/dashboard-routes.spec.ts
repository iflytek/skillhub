import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Dashboard Routes (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens major dashboard pages', async ({ page }) => {
    await page.goto('/dashboard/skills')
    await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()

    await page.goto('/dashboard/publish')
    await expect(page.getByRole('heading', { name: 'Publish Skill' })).toBeVisible()

    await page.goto('/dashboard/namespaces')
    await expect(page.getByRole('heading', { name: 'My Namespaces' })).toBeVisible()

    await page.goto('/dashboard/stars')
    await expect(page.getByRole('heading', { name: 'My Stars' })).toBeVisible()

    await page.goto('/dashboard/notifications')
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible()
  })

  test('opens governance and namespace management pages', async ({ page }) => {
    await page.goto('/dashboard/governance')
    await expect(page.getByRole('heading', { name: 'Governance Center' })).toBeVisible()

    await page.goto('/dashboard/namespaces/e2e-missing-namespace/members')
    await expect(page.getByText('Namespace not found')).toBeVisible()

    await page.goto('/dashboard/namespaces/e2e-missing-namespace/reviews')
    await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
  })
})
