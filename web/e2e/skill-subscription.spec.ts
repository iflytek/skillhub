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

test.describe('Skill Subscription (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('subscribe and unsubscribe to a skill', async ({ page, browser }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)
    await loginWithCredentials(adminPage, adminCredentials(), testInfo)
    await adminBuilder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug)

      const reviewTaskId = await adminBuilder.waitForPendingReview(namespace.slug, skill.slug, skill.version)
      await adminBuilder.approveReview(reviewTaskId)

      await page.goto(`/space/${namespace.slug}/${skill.slug}`)

      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()

      const subscribeButton = page.getByRole('button', { name: /Subscribe/ })
      await expect(subscribeButton).toBeVisible()

      const initialCount = await subscribeButton.textContent()
      const initialCountMatch = initialCount?.match(/\((\d+)\)/)
      const initialCountValue = initialCountMatch ? Number.parseInt(initialCountMatch[1], 10) : 0

      await subscribeButton.click()

      await expect(page.getByRole('button', { name: /Subscribed/ })).toBeVisible()

      const subscribedButton = page.getByRole('button', { name: /Subscribed/ })
      const subscribedCount = await subscribedButton.textContent()
      const subscribedCountMatch = subscribedCount?.match(/\((\d+)\)/)
      const subscribedCountValue = subscribedCountMatch ? Number.parseInt(subscribedCountMatch[1], 10) : 0

      expect(subscribedCountValue).toBe(initialCountValue + 1)

      await subscribedButton.click()

      await expect(page.getByRole('button', { name: /Subscribe/ })).toBeVisible()

      const unsubscribedButton = page.getByRole('button', { name: /Subscribe/ })
      const unsubscribedCount = await unsubscribedButton.textContent()
      const unsubscribedCountMatch = unsubscribedCount?.match(/\((\d+)\)/)
      const unsubscribedCountValue = unsubscribedCountMatch ? Number.parseInt(unsubscribedCountMatch[1], 10) : 0

      expect(unsubscribedCountValue).toBe(initialCountValue)
    } finally {
      await adminBuilder.cleanup()
      await adminContext.close()
      await builder.cleanup()
    }
  })

  test('shows subscribed skill in My Subscriptions page', async ({ page, browser }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)
    await loginWithCredentials(adminPage, adminCredentials(), testInfo)
    await adminBuilder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug)

      const reviewTaskId = await adminBuilder.waitForPendingReview(namespace.slug, skill.slug, skill.version)
      await adminBuilder.approveReview(reviewTaskId)

      await page.goto(`/space/${namespace.slug}/${skill.slug}`)

      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()

      const subscribeButton = page.getByRole('button', { name: /Subscribe/ })
      await expect(subscribeButton).toBeVisible()
      await subscribeButton.click()

      await expect(page.getByRole('button', { name: /Subscribed/ })).toBeVisible()

      await page.goto('/dashboard/subscriptions')

      await expect(page.getByRole('heading', { name: 'My Subscriptions' })).toBeVisible()
      await expect(page.getByText(`@${skill.namespace}`).first()).toBeVisible()
    } finally {
      await adminBuilder.cleanup()
      await adminContext.close()
      await builder.cleanup()
    }
  })
})
