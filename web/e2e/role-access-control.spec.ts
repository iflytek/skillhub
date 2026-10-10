import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

async function expectLeaveForbiddenRoute(page: Page, route: string) {
  await page.goto(route)
  await expect.poll(() => page.url()).not.toBe(`http://localhost:3000${route}`)
}

test.describe('Role Access Control (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('redirects regular user from review center', async ({ page }) => {
    await expectLeaveForbiddenRoute(page, '/dashboard/reviews')
  })

  test('redirects regular user from promotions and reports pages', async ({ page }) => {
    await expectLeaveForbiddenRoute(page, '/dashboard/promotions')
    await expectLeaveForbiddenRoute(page, '/dashboard/reports')
  })

  test('redirects regular user from admin pages', async ({ page }) => {
    await expectLeaveForbiddenRoute(page, '/admin/users')
    await expectLeaveForbiddenRoute(page, '/admin/audit-log')
    await expectLeaveForbiddenRoute(page, '/admin/labels')
  })
})
