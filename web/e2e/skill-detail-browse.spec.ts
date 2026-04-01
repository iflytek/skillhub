import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

test.describe('Skill Detail Browse (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows not found for unknown namespace', async ({ page }) => {
    await page.goto('/space/e2e-missing-namespace')
    await expect(page.getByRole('heading', { name: 'Namespace not found' })).toBeVisible()
  })

  test('shows not found for unknown skill detail route', async ({ page }) => {
    await page.goto('/space/e2e-missing-namespace/e2e-missing-skill')
    await expect(page.getByRole('heading', { name: 'Skill not found' })).toBeVisible()
  })
})
