import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

async function expectRouteOpenOrRedirected(page: Page, route: string, heading: string) {
  await page.goto(route)

  if (page.url().includes(route)) {
    await expect(page.getByRole('heading', { name: heading }).first()).toBeVisible()
    return
  }

  await expect.poll(() => page.url()).not.toContain(route)
}

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
    await expectRouteOpenOrRedirected(page, '/dashboard/governance', 'Governance Center')

    await page.goto('/dashboard/namespaces/e2e-missing-namespace/members')
    await expect(page.getByRole('heading', { name: 'Namespace not found' })).toBeVisible()

    await page.goto('/dashboard/namespaces/e2e-missing-namespace/reviews')
    await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
  })
})
