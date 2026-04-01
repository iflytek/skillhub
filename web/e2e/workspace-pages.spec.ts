import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Workspace Pages (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens my skills and my namespaces pages', async ({ page }) => {
    await page.goto('/dashboard/skills')
    await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()

    await page.goto('/dashboard/namespaces')
    await expect(page.getByRole('heading', { name: 'My Namespaces' })).toBeVisible()
  })
})
