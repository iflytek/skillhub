import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { loginWithCredentials, registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

function getOptionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

function adminCredentials() {
  return {
    username: getOptionalEnv('E2E_ADMIN_USERNAME') ?? getOptionalEnv('BOOTSTRAP_ADMIN_USERNAME') ?? 'admin',
    password: getOptionalEnv('E2E_ADMIN_PASSWORD') ?? getOptionalEnv('BOOTSTRAP_ADMIN_PASSWORD') ?? 'ChangeMe!2026',
  }
}

test.describe('Skill Version Compare (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('opens compare page for two published versions and renders unified diff data', async ({ page, browser }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)
    await loginWithCredentials(adminPage, adminCredentials(), testInfo)
    await adminBuilder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skillName = `compare-ui-${Date.now().toString(36)}`
      const v1 = await builder.publishSkill(namespace.slug, {
        name: skillName,
        version: '1.0.0',
        readmeHeading: `${skillName} v1`,
      })

      const firstReviewTaskId = await adminBuilder.waitForPendingReview(namespace.slug, v1.slug, v1.version)
      await adminBuilder.approveReview(firstReviewTaskId)

      const rereleaseResponse = await page.context().request.post(
        `/api/web/skills/${encodeURIComponent(namespace.slug)}/${encodeURIComponent(v1.slug)}/versions/${encodeURIComponent(v1.version)}/rerelease`,
        {
          data: {
            targetVersion: '1.1.0',
            confirmWarnings: true,
          },
        }
      )
      expect(rereleaseResponse.ok()).toBe(true)

      const secondReviewTaskId = await adminBuilder.waitForPendingReview(namespace.slug, v1.slug, '1.1.0')
      await adminBuilder.approveReview(secondReviewTaskId)

      await page.goto(`/space/${encodeURIComponent(namespace.slug)}/${encodeURIComponent(v1.slug)}/compare?from=1.0.0&to=1.1.0`)

      await expect(page.getByRole('heading', { name: 'Version Compare' })).toBeVisible()
      await expect(page.getByLabel('Base version')).toBeVisible()
      await expect(page.getByLabel('Head version')).toBeVisible()
      await expect(page.getByLabel('File list')).toContainText('SKILL.md')
      await expect(page.getByText('Modified').first()).toBeVisible()
      await expect(page.getByText('SKILL.md').first()).toBeVisible()
    } finally {
      await adminBuilder.cleanup()
      await adminContext.close()
      await builder.cleanup()
    }
  })
})
