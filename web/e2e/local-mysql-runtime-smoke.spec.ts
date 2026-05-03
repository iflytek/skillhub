import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { loginWithCredentials } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

const seededSearchKeyword = 'mysql-runtime-fixture'

interface AuthMeEnvelope {
  code: number
  data: {
    userId: string
    displayName: string
  }
}

test.setTimeout(300_000)

test.describe('Local MySQL Runtime Smoke (Real API)', () => {
  test('auth, profile, namespace, publish, and search flow work end-to-end', async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await loginWithCredentials(page, {
      username: process.env.E2E_ADMIN_USERNAME ?? process.env.BOOTSTRAP_ADMIN_USERNAME ?? 'admin',
      password: process.env.E2E_ADMIN_PASSWORD ?? process.env.BOOTSTRAP_ADMIN_PASSWORD ?? 'ChangeMe!2026',
    }, testInfo)
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      await page.goto('/login')
      await expect(page).not.toHaveURL(/\/login$/)

      await page.goto('/settings/profile')
      await expect(page.getByRole('heading', { name: 'Profile Settings' })).toBeVisible()
      const authMe = (await (await page.request.get('/api/v1/auth/me')).json()) as AuthMeEnvelope
      expect(authMe.code).toBe(0)
      await expect(page.getByRole('main')).toContainText(authMe.data.displayName)
      await expect(page.getByRole('main')).toContainText(authMe.data.userId)

      const namespace = await builder.ensureWritableNamespace()
      await page.goto('/dashboard/namespaces')
      await expect(page.getByRole('heading', { name: 'My Namespaces' })).toBeVisible()
      await expect(page.getByText(`@${namespace.slug}`)).toBeVisible()

      const keyword = `mysql-smoke-${Date.now().toString(36)}`
      const skill = await builder.publishSkill(namespace.slug, {
        name: `mysql-smoke-${Date.now().toString(36)}`,
        description: `Searchable ${keyword} skill for local MySQL smoke coverage.`,
      })

      if (skill.status === 'PENDING_REVIEW') {
        const reviewTaskId = await builder.waitForPendingReview(namespace.slug, skill.slug, skill.version)
        await builder.approveReview(reviewTaskId)
      }

      await page.goto(`/dashboard/skills`)
      await expect(page.getByRole('main')).toContainText(skill.slug)

      await page.goto(`/search?q=${encodeURIComponent(seededSearchKeyword)}&sort=relevance&page=0&starredOnly=false`)
      await expect(page.getByRole('textbox')).toHaveValue(seededSearchKeyword)
      await expect(page.getByRole('main')).toContainText('Local MySQL Search Fixture')
      await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
    } finally {
      await builder.cleanup()
    }
  })
})
