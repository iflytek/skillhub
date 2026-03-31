import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { expectRedirectToLogin } from './helpers/assertions'
import { mockNamespacePage, mockSkillDetailPage } from './helpers/route-mocks'

test.describe('Route Guards', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('redirects anonymous users to login for protected namespace, detail, and publish routes', async ({ page }) => {
    await mockNamespacePage(page, { authenticated: false })
    await page.goto('/space/team-alpha')
    await expectRedirectToLogin(page, '/space/team-alpha')

    await mockSkillDetailPage(page, {
      authenticated: false,
      namespace: 'team-alpha',
      slug: 'guarded-skill',
    })
    await page.goto('/space/team-alpha/guarded-skill')
    await expectRedirectToLogin(page, '/space/team-alpha/guarded-skill')

    await page.goto('/dashboard/publish')
    await expectRedirectToLogin(page, '/dashboard/publish')
  })

  test('allows authenticated users to open namespace and skill detail routes', async ({ page }) => {
    await mockNamespacePage(page, { authenticated: true })
    await page.goto('/space/team-alpha')
    await expect(page.getByRole('heading', { name: 'Team Alpha' })).toBeVisible()

    await mockSkillDetailPage(page, {
      authenticated: true,
      namespace: 'team-alpha',
      slug: 'guarded-skill',
    })
    await page.goto('/space/team-alpha/guarded-skill')
    await expect(page.getByRole('heading', { name: 'Test Skill' }).first()).toBeVisible()
  })
})
