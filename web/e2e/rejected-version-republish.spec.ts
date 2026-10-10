import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { createFreshSession, loginWithCredentials } from './helpers/session'
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

function withoutKnownMetaCspWarning(messages: string[]): string[] {
  return messages.filter((message) => (
    !message.includes("frame-ancestors' is ignored when delivered via a <meta> element")
  ))
}

test.describe('Rejected version replacement (Real API)', () => {
  test.describe.configure({ timeout: 150_000 })

  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await createFreshSession(page, testInfo)
  })

  test('re-publishes the same version after rejection', async ({ page, browser }, testInfo) => {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
    const adminConsoleErrors: string[] = []
    const adminPageErrors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    page.on('pageerror', (error) => pageErrors.push(error.message))

    const publisherBuilder = new E2eTestDataBuilder(page, testInfo)
    await publisherBuilder.init()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    adminPage.on('console', (message) => {
      if (message.type() === 'error') adminConsoleErrors.push(message.text())
    })
    adminPage.on('pageerror', (error) => adminPageErrors.push(error.message))
    const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)
    await loginWithCredentials(adminPage, adminCredentials(), testInfo)
    await adminBuilder.init()

    try {
      const namespace = await publisherBuilder.ensureWritableNamespace()
      const skillName = `replace-rejected-${Date.now().toString(36)}`
      const firstPublish = await publisherBuilder.publishSkill(namespace.slug, {
        name: skillName,
        version: '1.0.0',
      })
      const rejectedReviewId = await adminBuilder.waitForPendingReview(
        namespace.slug,
        firstPublish.slug,
        firstPublish.version,
      )
      await publisherBuilder.waitForVersionStatus(
        namespace.slug,
        firstPublish.slug,
        firstPublish.version,
        'PENDING_REVIEW',
      )
      await adminBuilder.rejectReview(rejectedReviewId)
      await publisherBuilder.waitForVersionStatus(
        namespace.slug,
        firstPublish.slug,
        firstPublish.version,
        'REJECTED',
      )

      await page.goto('/dashboard/review-progress')
      await expect(page.getByRole('heading', { name: 'My Review Progress' })).toBeVisible()
      const rejectedCard = page.locator('article').filter({ hasText: firstPublish.slug })
      await expect(rejectedCard).toContainText('Rejected')
      await expect(rejectedCard).toContainText('1 submission')
      await rejectedCard.getByRole('button', { name: 'Submission history' }).click()
      await expect(rejectedCard).toContainText('Rejected by Playwright E2E')
      await rejectedCard.getByRole('link', { name: 'Edit and resubmit' }).click()
      await expect(page).toHaveURL(/\/dashboard\/publish/)
      await expect(page.getByText(new RegExp(`Resubmit .* v${firstPublish.version}`))).toBeVisible()

      const replacement = await publisherBuilder.publishSkill(namespace.slug, {
        name: skillName,
        description: 'Replacement after review rejection',
        version: '1.0.0',
      })
      const replacementReviewId = await adminBuilder.waitForPendingReview(
        namespace.slug,
        replacement.slug,
        replacement.version,
      )
      await publisherBuilder.waitForVersionStatus(
        namespace.slug,
        replacement.slug,
        replacement.version,
        'PENDING_REVIEW',
      )

      expect(replacement.skillId).toBe(firstPublish.skillId)
      expect(replacement.version).toBe(firstPublish.version)
      expect(replacementReviewId).not.toBe(rejectedReviewId)

      const progressResponse = await page.request.get(
        `/api/web/reviews/my-progress?q=${encodeURIComponent(replacement.slug)}&page=0&size=20`,
      )
      expect(progressResponse.status()).toBe(200)
      const progressBody = await progressResponse.json() as {
        data: {
          items: Array<{ latestStatus: string; attemptCount: number }>
          total: number
          statusCounts: { pending: number; approved: number; rejected: number }
        }
      }
      expect(progressBody.data.total).toBe(1)
      expect(progressBody.data.items).toHaveLength(1)
      expect(progressBody.data.items[0]).toMatchObject({ latestStatus: 'PENDING', attemptCount: 2 })
      expect(progressBody.data.statusCounts).toEqual({ pending: 1, approved: 0, rejected: 0 })

      const rejectedFilterResponse = await page.request.get(
        `/api/web/reviews/my-progress?q=${encodeURIComponent(replacement.slug)}&status=REJECTED&page=0&size=20`,
      )
      expect(rejectedFilterResponse.status()).toBe(200)
      const rejectedFilterBody = await rejectedFilterResponse.json() as {
        data: { items: unknown[]; total: number; statusCounts: { pending: number } }
      }
      expect(rejectedFilterBody.data.items).toEqual([])
      expect(rejectedFilterBody.data.total).toBe(0)
      expect(rejectedFilterBody.data.statusCounts.pending).toBe(1)

      const missingSearchResponse = await page.request.get(
        '/api/web/reviews/my-progress?q=definitely-missing-review-progress&page=0&size=20',
      )
      expect(missingSearchResponse.status()).toBe(200)
      const missingSearchBody = await missingSearchResponse.json() as {
        data: { items: unknown[]; total: number; statusCounts: { pending: number; approved: number; rejected: number } }
      }
      expect(missingSearchBody.data.items).toEqual([])
      expect(missingSearchBody.data.total).toBe(0)
      expect(missingSearchBody.data.statusCounts).toEqual({ pending: 0, approved: 0, rejected: 0 })

      const outOfRangeResponse = await page.request.get(
        `/api/web/reviews/my-progress?q=${encodeURIComponent(replacement.slug)}&page=99&size=1`,
      )
      expect(outOfRangeResponse.status()).toBe(200)
      const outOfRangeBody = await outOfRangeResponse.json() as {
        data: { items: unknown[]; total: number; statusCounts: { pending: number } }
      }
      expect(outOfRangeBody.data.items).toEqual([])
      expect(outOfRangeBody.data.total).toBe(1)
      expect(outOfRangeBody.data.statusCounts.pending).toBe(1)

      const maximumPageResponse = await page.request.get(
        `/api/web/reviews/my-progress?q=${encodeURIComponent(replacement.slug)}&page=2147483647&size=100`,
      )
      expect(maximumPageResponse.status()).toBe(200)
      const maximumPageBody = await maximumPageResponse.json() as {
        data: { items: unknown[]; total: number; statusCounts: { pending: number } }
      }
      expect(maximumPageBody.data.items).toEqual([])
      expect(maximumPageBody.data.total).toBe(1)
      expect(maximumPageBody.data.statusCounts.pending).toBe(1)

      const attemptsResponse = await page.request.get(
        `/api/web/reviews/my-progress/${replacementReviewId}/attempts`,
      )
      expect(attemptsResponse.status()).toBe(200)
      const attemptsBody = await attemptsResponse.json() as {
        data: Array<{ id: number; status: string; skillVersionId: number | null }>
      }
      expect(attemptsBody.data.map((attempt) => attempt.id)).toEqual([
        replacementReviewId,
        rejectedReviewId,
      ])
      expect(attemptsBody.data.map((attempt) => attempt.status)).toEqual(['PENDING', 'REJECTED'])
      expect(attemptsBody.data[1]?.skillVersionId).toBeNull()

      const reviewerAttemptsResponse = await adminPage.request.get(
        `/api/web/reviews/${replacementReviewId}/attempts`,
      )
      expect(reviewerAttemptsResponse.status()).toBe(200)
      const reviewerAttemptsBody = await reviewerAttemptsResponse.json() as {
        data: Array<{ id: number; status: string }>
      }
      expect(reviewerAttemptsBody.data.map((attempt) => attempt.id)).toEqual([
        replacementReviewId,
        rejectedReviewId,
      ])

      const replacedReviewResponse = await adminPage.request.get(`/api/web/reviews/${rejectedReviewId}`)
      expect(replacedReviewResponse.status()).toBe(200)

      await page.goto('/dashboard/review-progress')
      await expect(page.getByRole('heading', { name: 'My Review Progress' })).toBeVisible()
      await expect(page.getByRole('button', { name: /In review/ })).toContainText('1')
      const progressCard = page.locator('article').filter({ hasText: replacement.slug })
      await expect(progressCard).toContainText('In review')
      await expect(progressCard).toContainText('2 submissions')
      await progressCard.getByRole('button', { name: 'Submission history' }).click()
      await expect(progressCard).toContainText('Attempt 2')
      await expect(progressCard).toContainText('Attempt 1')
      await expect(progressCard).toContainText('Rejected by Playwright E2E')
      await expect(progressCard).toContainText('Reviewed by')
      await expect(progressCard.getByRole('link', { name: 'Edit and resubmit' })).toHaveCount(0)
      await page.screenshot({ path: testInfo.outputPath('author-review-progress-desktop.png'), fullPage: true })

      await page.setViewportSize({ width: 390, height: 844 })
      await expect(progressCard).toBeVisible()
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      await page.screenshot({ path: testInfo.outputPath('author-review-progress-mobile.png'), fullPage: true })

      await adminPage.goto(`/dashboard/reviews/${replacementReviewId}`)
      await expect(adminPage.getByRole('heading', { name: 'Submission History' })).toBeVisible()
      await expect(adminPage.getByText('Attempt 2')).toBeVisible()
      await expect(adminPage.getByText('Attempt 1')).toBeVisible()
      expect(withoutKnownMetaCspWarning(consoleErrors)).toEqual([])
      expect(pageErrors).toEqual([])
      expect(withoutKnownMetaCspWarning(adminConsoleErrors)).toEqual([])
      expect(adminPageErrors).toEqual([])
    } finally {
      await adminBuilder.cleanup()
      await adminContext.close()
      await publisherBuilder.cleanup()
    }
  })
})
