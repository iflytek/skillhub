import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'
import { loginWithCredentials, registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

async function currentUserId(page: Page): Promise<string> {
  const response = await page.context().request.get('/api/v1/auth/me')
  expect(response.ok()).toBeTruthy()
  return (await response.json()).data.userId
}

async function skillOwnerId(page: Page, namespace: string, slug: string): Promise<string> {
  const response = await page.context().request.get(`/api/web/skills/${namespace}/${slug}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()).data.ownerId
}

test.describe('Skill Owner Transfer (Real API)', () => {
  test.describe.configure({ timeout: 120_000 })

  test('namespace owner takes over a member skill; the member cannot transfer', async ({ browser, page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo, { allowMockSession: false })
    const memberId = await currentUserId(page)
    const memberBuilder = new E2eTestDataBuilder(page, testInfo)
    await memberBuilder.init()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    await setEnglishLocale(adminPage)
    await loginWithCredentials(adminPage, {
      username: process.env.E2E_ADMIN_USERNAME ?? process.env.BOOTSTRAP_ADMIN_USERNAME ?? 'admin',
      password: process.env.E2E_ADMIN_PASSWORD ?? process.env.BOOTSTRAP_ADMIN_PASSWORD ?? 'ChangeMe!2026',
    }, testInfo)
    const adminId = await currentUserId(adminPage)
    const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)
    await adminBuilder.init()

    let skillPath: string | undefined
    try {
      const namespace = await adminBuilder.createNamespace('e2e-owner')
      await adminBuilder.addNamespaceMember(namespace.slug, memberId, 'MEMBER')
      const skill = await memberBuilder.publishSkill(namespace.slug)
      await adminBuilder.approveReview(await adminBuilder.waitForPendingReview(namespace.slug, skill.slug, skill.version))
      skillPath = `${encodeURIComponent(skill.namespace)}/${encodeURIComponent(skill.slug)}`

      await page.goto(`/space/${skillPath}`)
      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
      await expect(page.getByRole('button', { name: 'Change owner' })).toHaveCount(0)

      await adminPage.goto(`/space/${skillPath}`)
      await adminPage.getByRole('button', { name: 'Change owner' }).click()
      const dialog = adminPage.getByRole('dialog', { name: 'Change owner' })
      await dialog.getByRole('combobox', { name: 'New owner' }).click()
      // The namespace has two members; the current owner (the member) is not offered.
      await expect(adminPage.getByRole('option')).toHaveCount(1)
      await adminPage.getByRole('option').click()
      await dialog.getByLabel('Reason').fill('E2E handover')
      await dialog.getByRole('button', { name: 'Transfer' }).click()

      await expect(dialog).toBeHidden()
      await expect.poll(() => skillOwnerId(adminPage, skill.namespace, skill.slug)).toBe(adminId)
    } finally {
      if (skillPath) {
        await adminPage.context().request.delete(`/api/web/skills/${skillPath}`, { headers: await csrfHeaders(adminPage) })
      }
      await memberBuilder.cleanup()
      await adminBuilder.cleanup()
      await adminContext.close()
    }
  })
})
