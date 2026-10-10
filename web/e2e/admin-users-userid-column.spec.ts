import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

// This spec verifies the admin UI surfaces userId next to username and supports one-click copy.
// We rely on the `X-Mock-User-Id: local-admin` mock profile (the same approach used by other
// admin specs such as `my-namespaces-data.spec.ts`). The mock profile activates a deterministic
// admin session without exercising the full SSO path; treat results here as evidence of the
// admin UI behavior, not of the real-service auth path.
test.describe('Admin Users - UserId Column', () => {
  test.beforeEach(async ({ page, context }) => {
    await setEnglishLocale(page)
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])

    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-admin',
    })

    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/api/v1/admin/users') && resp.status() === 200,
      ),
      page.goto('/admin/users'),
    ])

    await expect(page.getByRole('columnheader', { name: 'User ID' })).toBeVisible()
  })

  test('userId column appears after username column', async ({ page }) => {
    const headers = page.getByRole('columnheader')
    const headerTexts = await headers.allTextContents()

    const usernameIndex = headerTexts.findIndex((text) => text.includes('Username'))
    const userIdIndex = headerTexts.findIndex((text) => text.includes('User ID'))

    expect(usernameIndex).toBeGreaterThanOrEqual(0)
    expect(userIdIndex).toBe(usernameIndex + 1)
  })

  test('userId values are displayed in table rows', async ({ page }) => {
    const firstRow = page.getByRole('row').nth(1)
    await expect(firstRow).toBeVisible()

    // Assert on the monospace span specifically, not the full cell (which includes button text).
    const userIdSpan = firstRow.getByRole('cell').nth(1).locator('span.font-mono')
    const userIdText = (await userIdSpan.textContent())?.trim() ?? ''
    expect(userIdText.length).toBeGreaterThan(0)
  })

  test('copy button exists for each userId row', async ({ page }) => {
    const rowCount = await page.getByRole('row').count()
    test.skip(rowCount <= 1, 'No data rows available to assert copy buttons')

    // Scope to the userId cell (2nd column) in each data row to avoid false positives
    // from other copy buttons that may appear elsewhere on the page.
    const dataRows = page.getByRole('row').filter({ hasNot: page.getByRole('columnheader') })
    const rows = await dataRows.count()
    for (let i = 0; i < rows; i++) {
      const userIdCell = dataRows.nth(i).getByRole('cell').nth(1)
      await expect(userIdCell.getByRole('button', { name: /copy/i })).toBeVisible()
    }
  })

  test('clicking copy button copies the row userId to clipboard', async ({ page }) => {
    const firstRow = page.getByRole('row').nth(1)
    await expect(firstRow).toBeVisible()

    // Extract the displayed userId from the monospace span inside the 2nd column cell.
    const userIdSpan = firstRow.getByRole('cell').nth(1).locator('span.font-mono')
    const expectedUserId = (await userIdSpan.textContent())?.trim() ?? ''
    expect(expectedUserId.length).toBeGreaterThan(0)

    const copyButton = firstRow.getByRole('cell').nth(1).getByRole('button').first()
    await copyButton.click()

    // Wait for the button text to flip to "Copied" as feedback.
    await expect(copyButton).toHaveText(/copied/i)

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    expect(clipboardText).toBe(expectedUserId)
  })

  test('copy button shows feedback after clicking', async ({ page }) => {
    const firstRow = page.getByRole('row').nth(1)
    await expect(firstRow).toBeVisible()

    const copyButton = firstRow.getByRole('cell').nth(1).getByRole('button').first()
    await copyButton.click()

    // Wait for the button text to flip to "Copied" as feedback.
    await expect(copyButton).toHaveText(/copied/i)
  })

  test('userId column persists after triggering a search', async ({ page }) => {
    const searchInput = page.getByLabel('Search users')
    await expect(searchInput).toBeVisible()

    // Trigger the actual search by clicking the Search button and wait for the API response,
    // rather than relying on debounced input.
    await searchInput.fill('admin')
    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/api/v1/admin/users') && resp.status() === 200,
      ),
      page.getByRole('button', { name: 'Search', exact: true }).click(),
    ])

    await expect(page.getByRole('columnheader', { name: 'User ID' })).toBeVisible()
  })

  test('userId column persists after applying a status filter', async ({ page }) => {
    // Open the status filter Select and pick "Active". This exercises the filter path that the
    // previous test version skipped entirely.
    await page.getByLabel('Status filter').click()
    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/api/v1/admin/users') && resp.status() === 200,
      ),
      page.getByRole('option', { name: 'Active' }).click(),
    ])

    await expect(page.getByRole('columnheader', { name: 'User ID' })).toBeVisible()
  })

  test('userId column persists across pagination', async ({ page }) => {
    const nextButton = page.getByRole('button', { name: /next/i })
    const isReachable = (await nextButton.isVisible()) && (await nextButton.isEnabled())
    test.skip(!isReachable, 'Pagination not reachable with current data set')

    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/api/v1/admin/users') && resp.status() === 200,
      ),
      nextButton.click(),
    ])

    await expect(page.getByRole('columnheader', { name: 'User ID' })).toBeVisible()
  })
})
