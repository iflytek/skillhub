import { expect, test, type BrowserContext } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('My Namespaces super admin actions (Real API)', () => {
  test.describe.configure({ timeout: 150_000 })

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-admin',
    })
  })

  test('opens and downloads a published namespace-only skill in an archived non-member namespace', async ({ page, browser }, testInfo) => {
    let adminBuilder: E2eTestDataBuilder | undefined
    let ownerContext: BrowserContext | undefined
    let ownerBuilder: E2eTestDataBuilder | undefined
    let namespaceSlug: string | undefined
    let namespaceArchived = false

    try {
      adminBuilder = new E2eTestDataBuilder(page, testInfo)
      await adminBuilder.init()
      const activeNamespace = await adminBuilder.createNamespace('e2e-super-admin-publish-active')
      const namespace = await adminBuilder.createNamespace('e2e-super-admin-read')
      namespaceSlug = namespace.slug

      ownerContext = await browser.newContext({
        extraHTTPHeaders: {
          'X-Mock-User-Id': 'local-user',
        },
      })
      const ownerPage = await ownerContext.newPage()
      ownerBuilder = new E2eTestDataBuilder(ownerPage, testInfo)
      await ownerBuilder.init()

      await adminBuilder.addNamespaceMember(namespace.slug, 'local-user')
      const transferResponse = await page.context().request.post(
        `/api/web/namespaces/${encodeURIComponent(namespace.slug)}/transfer-ownership`,
        {
          data: { newOwnerId: 'local-user' },
          headers: await csrfHeaders(page),
        },
      )
      expect(transferResponse.ok()).toBe(true)

      const removeAdminResponse = await ownerPage.context().request.delete(
        `/api/web/namespaces/${encodeURIComponent(namespace.slug)}/members/local-admin`,
        { headers: await csrfHeaders(ownerPage) },
      )
      expect(removeAdminResponse.ok()).toBe(true)

      const skillName = `super-admin-read-${Date.now().toString(36)}`
      const skill = await ownerBuilder.publishSkill(namespace.slug, {
        name: skillName,
        description: 'Published namespace-only skill for the SUPER_ADMIN read-chain regression',
        visibility: 'NAMESPACE_ONLY',
        readmeHeading: skillName,
      })

      const reviewTaskId = await adminBuilder.waitForPendingReview(namespace.slug, skill.slug, skill.version)
      await adminBuilder.approveReview(reviewTaskId)

      const archiveResponse = await ownerPage.context().request.post(
        `/api/web/namespaces/${encodeURIComponent(namespace.slug)}/archive`,
        {
          data: { reason: 'Validate SUPER_ADMIN archived namespace reads' },
          headers: await csrfHeaders(ownerPage),
        },
      )
      expect(archiveResponse.ok()).toBe(true)
      namespaceArchived = true

      await page.goto('/dashboard/publish')
      const namespaceTrigger = page.locator('#namespace')
      await namespaceTrigger.click()
      await page.getByRole('searchbox', { name: 'Search namespaces' }).fill(activeNamespace.slug)
      const activeOption = page.getByRole('button', {
        name: `${activeNamespace.displayName} (@${activeNamespace.slug})`,
      })
      await expect(activeOption).toBeVisible()
      await activeOption.click()

      await expect(namespaceTrigger).toContainText(`@${activeNamespace.slug}`)
      await namespaceTrigger.click()
      await page.getByRole('searchbox', { name: 'Search namespaces' }).fill(namespace.slug)
      await expect(page.getByText('No namespaces found')).toBeVisible()
      await expect(page.getByRole('button', {
        name: `${namespace.displayName} (@${namespace.slug})`,
      })).toHaveCount(0)

      await page.goto(`/dashboard/publish?namespace=${encodeURIComponent(namespace.slug)}&visibility=PUBLIC`)
      await expect(namespaceTrigger).toContainText(`@${namespace.slug}`)
      await expect(page.getByText('The selected namespace is not active or is no longer available.')).toBeVisible()

      await page.goto('/dashboard/namespaces')

      const namespaceCard = page.getByTestId(`namespace-card-${namespace.slug}`)
      await expect(namespaceCard.getByText(`@${namespace.slug}`)).toBeVisible()
      await expect(namespaceCard.getByText('Current role: Unknown')).toBeVisible()
      await expect(namespaceCard.getByRole('button', { name: 'Manage Members' })).toHaveCount(0)
      await expect(namespaceCard.getByRole('button', { name: 'Review Tasks' })).toHaveCount(0)

      await namespaceCard.click()
      await expect(page).toHaveURL(new RegExp(`/space/${namespace.slug}$`))
      await expect(page.getByRole('heading', { name: namespace.displayName })).toBeVisible()

      const skillHeading = page.getByRole('heading', { name: skillName, exact: true })
      await expect(skillHeading).toBeVisible()
      await skillHeading.click()

      await expect(page).toHaveURL(new RegExp(`/space/${namespace.slug}/${skill.slug}$`))
      await expect(page.getByRole('heading', { name: skillName, exact: true }).first()).toBeVisible()

      const downloadPromise = page.waitForEvent('download')
      await page.getByRole('button', { name: 'Download', exact: true }).click()
      const download = await downloadPromise
      expect(await download.failure()).toBeNull()
      expect(download.suggestedFilename()).toContain(skill.version)
    } finally {
      if (namespaceArchived && namespaceSlug && ownerContext) {
        const ownerPage = ownerContext.pages()[0]
        await ownerPage.context().request.post(
          `/api/web/namespaces/${encodeURIComponent(namespaceSlug)}/restore`,
          { headers: await csrfHeaders(ownerPage) },
        ).catch(() => undefined)
      }
      await ownerBuilder?.cleanup()
      if (namespaceSlug && ownerContext) {
        const ownerPage = ownerContext.pages()[0]
        await ownerPage.context().request.post(
          `/api/web/namespaces/${encodeURIComponent(namespaceSlug)}/archive`,
          {
            data: { reason: 'E2E cleanup' },
            headers: await csrfHeaders(ownerPage),
          },
        ).catch(() => undefined)
      }
      await adminBuilder?.cleanup()
      await ownerContext?.close()
    }
  })
})
