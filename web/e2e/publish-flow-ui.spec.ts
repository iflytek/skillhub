import { expect, test } from '@playwright/test'
import path from 'node:path'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

interface PublishEnvelope {
  code: number
  msg?: string
  data: {
    namespace: string
    slug: string
    version: string
  }
}

test.describe('Publish Flow UI (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('publishes a generated skill package from dashboard page', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skillName = `publish-ui-${Date.now().toString(36)}`
      const packagePath = builder.createSkillPackageFile({ name: skillName })

      await page.goto('/dashboard/publish')
      await expect(page.getByRole('heading', { name: 'Publish Skill' })).toBeVisible()

      const namespaceTrigger = page.locator('#namespace')
      await expect(namespaceTrigger).toBeVisible()
      await namespaceTrigger.click()
      const namespaceOption = page.getByRole('option', {
        name: new RegExp(`\\(@${namespace.slug}\\)`),
      }).first()
      await expect(namespaceOption).toBeVisible()
      await namespaceOption.evaluate((element: HTMLElement) => {
        element.scrollIntoView({ block: 'center' })
        element.click()
      })
      await expect(namespaceTrigger).toContainText(`@${namespace.slug}`)

      await page.locator('input[type="file"]').setInputFiles(packagePath)
      await expect(page.getByText(path.basename(packagePath))).toBeVisible()
      const confirmButton = page.getByRole('button', { name: 'Confirm Publish' })
      await expect(confirmButton).toBeEnabled()
      const publishResponsePromise = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST'
          && response.url().includes(`/api/web/skills/${encodeURIComponent(namespace.slug)}/publish`),
        { timeout: 90_000 },
      )
      await confirmButton.click()
      const publishResponse = await publishResponsePromise
      const publishBody = await publishResponse.json() as PublishEnvelope

      expect(publishResponse.status(), `publish failed: ${publishBody.msg ?? 'unknown error'}`).toBe(200)
      expect(publishBody.code).toBe(0)
      expect(publishBody.data.namespace).toBe(namespace.slug)

      await page.goto('/dashboard/skills')
      await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible({ timeout: 30_000 })
      await expect(page.getByRole('heading', { name: skillName, exact: true })).toBeVisible({ timeout: 30_000 })
      await expect(page.getByText(`@${publishBody.data.namespace}`).first()).toBeVisible()
      await expect(page.getByText(`v${publishBody.data.version}`).first()).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
