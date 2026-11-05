import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { createNamespaceReviewData } from './helpers/review-seed'

test.describe('Namespace Review Detail Access (Real API)', () => {
  test.describe.configure({ timeout: 120_000 })

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('opens namespace review detail from the namespace review list', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)

      await page.goto(`/dashboard/namespaces/${seeded.namespace.slug}/reviews`)

      await expect(page).toHaveURL((url) => url.pathname === '/dashboard/reviews' && url.searchParams.get('namespace') === seeded?.namespace.slug)
      await expect(page.getByRole('heading', { name: 'Review center', exact: true })).toBeVisible()
      await expect(page.getByRole('combobox', { name: 'Review scope' })).toHaveText(seeded.namespace.displayName)
      const row = page.getByRole('row').filter({ hasText: `@${seeded.namespace.slug}/${seeded.skill.slug}` })
      await expect(row).toBeVisible()
      await row.getByRole('button', { name: 'View', exact: true }).click()

      await expect(page).toHaveURL((url) => url.pathname === `/dashboard/reviews/${seeded?.reviewTaskId}` && url.searchParams.get('namespace') === seeded?.namespace.slug)
      await expect(page.getByRole('heading', { level: 1, name: seeded.skillDisplayName, exact: true })).toBeVisible()
      await expect(page.getByTestId('review-task-header').getByText(`@${seeded.namespace.slug}/${seeded.skill.slug}`, { exact: true })).toBeVisible()
      await page.getByRole('button', { name: 'Back to list', exact: true }).click()
      await expect(page).toHaveURL((url) => url.pathname === '/dashboard/reviews' && url.searchParams.get('namespace') === seeded?.namespace.slug)
    } finally {
      await seeded?.cleanup()
    }
  })

  test('keeps namespace operators in the unified review center', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)

      await page.goto('/dashboard/reviews')

      await expect(page).toHaveURL((url) => url.pathname === '/dashboard/reviews')
      await expect(page.getByRole('heading', { name: 'Review center', exact: true })).toBeVisible()
      await expect(page.getByRole('combobox', { name: 'Review scope' })).toHaveText('All authorized scopes')
      await expect(page.getByRole('table').getByText(`@${seeded.namespace.slug}/${seeded.skill.slug}`, { exact: true })).toBeVisible()
    } finally {
      await seeded?.cleanup()
    }
  })

  test('opens the same bound task through global and legacy namespace detail routes', async ({ browser, page }, testInfo) => {
    let seeded: Awaited<ReturnType<typeof createNamespaceReviewData>> | undefined
    try {
      seeded = await createNamespaceReviewData(browser, page, testInfo)

      await page.goto(`/dashboard/reviews/${seeded.reviewTaskId}`)

      await expect(page).toHaveURL((url) => url.pathname === `/dashboard/reviews/${seeded?.reviewTaskId}`)
      await expect(page.getByRole('heading', { level: 1, name: seeded.skillDisplayName, exact: true })).toBeVisible()
      await expect(page.getByTestId('review-task-header').getByText(`@${seeded.namespace.slug}/${seeded.skill.slug}`, { exact: true })).toBeVisible()
      await page.goto(`/dashboard/namespaces/${seeded.namespace.slug}/reviews/${seeded.reviewTaskId}`)
      await expect(page.getByRole('heading', { level: 1, name: seeded.skillDisplayName, exact: true })).toBeVisible()
      await expect(page.getByTestId('review-task-header').getByText(`@${seeded.namespace.slug}/${seeded.skill.slug}`, { exact: true })).toBeVisible()
    } finally {
      await seeded?.cleanup()
    }
  })
})
