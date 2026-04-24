import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import path from 'node:path'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

interface PublishEnvelope {
  code: number
  msg?: string
  data: {
    namespace?: string
    slug?: string
    version?: string
    results?: Array<{
      namespace: string
      slug: string
      version: string
    }>
  }
}

async function chooseNamespace(page: Page, namespaceSlug: string) {
  const namespaceTrigger = page.locator('#namespace')
  await expect(namespaceTrigger).toBeVisible()
  await namespaceTrigger.click()
  const namespaceOption = page.getByRole('option', {
    name: new RegExp(`\\(@${namespaceSlug}\\)`),
  }).first()
  await expect(namespaceOption).toBeVisible()
  await namespaceOption.evaluate((element: HTMLElement) => {
    element.scrollIntoView({ block: 'center' })
    element.click()
  })
  await expect(namespaceTrigger).toContainText(`@${namespaceSlug}`)
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
      await chooseNamespace(page, namespace.slug)

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

  test('publishes a folder-packed skill collection and shows batch success toast', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const suffix = Date.now().toString(36)
      const firstSkillName = `publish-bulk-a-${suffix}`
      const secondSkillName = `publish-bulk-b-${suffix}`
      const packagePath = builder.createSkillCollectionPackageFile([
        { name: firstSkillName },
        { name: secondSkillName },
      ])

      await page.goto('/dashboard/publish')
      await chooseNamespace(page, namespace.slug)

      await page.locator('input[type="file"]').setInputFiles(packagePath)
      await expect(page.getByText(path.basename(packagePath))).toBeVisible()

      const confirmButton = page.getByRole('button', { name: 'Confirm Publish' })
      await expect(confirmButton).toBeEnabled()
      await confirmButton.click()

      await expect(page.getByText('Batch publish completed')).toBeVisible({ timeout: 90_000 })
      await expect(page.getByText('2 skill packages were published successfully.')).toBeVisible()
      await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible({ timeout: 30_000 })
      await expect(page.getByRole('heading', { name: firstSkillName, exact: true })).toBeVisible({ timeout: 30_000 })
      await expect(page.getByRole('heading', { name: secondSkillName, exact: true })).toBeVisible({ timeout: 30_000 })
    } finally {
      await builder.cleanup()
    }
  })

  test('keeps the publish page open when uploading an invalid package', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const invalidPackagePath = path.join(process.cwd(), 'e2e', 'fixtures', 'sample-skill.zip')

      await page.goto('/dashboard/publish')
      await chooseNamespace(page, namespace.slug)
      await page.locator('input[type="file"]').setInputFiles(invalidPackagePath)
      await expect(page.getByText(path.basename(invalidPackagePath))).toBeVisible()

      const publishResponsePromise = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST'
          && response.url().includes(`/api/web/skills/${encodeURIComponent(namespace.slug)}/publish`),
        { timeout: 90_000 },
      )
      await page.getByRole('button', { name: 'Confirm Publish' }).click()
      const publishResponse = await publishResponsePromise
      expect(publishResponse.ok()).toBe(false)

      await expect(page.getByText('Publish Failed')).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Publish Skill' })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
