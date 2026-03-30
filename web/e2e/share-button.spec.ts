import { expect, test } from '@playwright/test'
import { mockStaticApis, setEnglishLocale, skill } from './helpers/api-mocks'

test.describe('Skill Share Button', () => {
  test.beforeEach(async ({ page, context }) => {
    await setEnglishLocale(page)

    // Grant clipboard permissions
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])

    await mockStaticApis(page, { authenticated: true })

    // Mock skill sub-resource APIs (versions, files, etc.) to prevent server errors
    await page.route('**/api/web/skills/*/versions', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0, msg: 'ok',
          data: { items: [{ id: 10, version: '1.0.0', status: 'PUBLISHED', createdAt: '2026-03-20T00:00:00Z' }], total: 1, page: 0, size: 20 },
          timestamp: '2026-03-28T00:00:00Z', requestId: 'playwright-e2e',
        }),
      })
    })

    await page.route('**/api/web/skills/*/versions/*/files', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, msg: 'ok', data: [], timestamp: '2026-03-28T00:00:00Z', requestId: 'playwright-e2e' }),
      })
    })

    await page.route('**/api/web/skills/*/versions/*', async (route) => {
      // Let the versions list route handle its own path
      if (route.request().url().endsWith('/versions')) return route.continue()
      if (route.request().url().includes('/files')) return route.continue()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0, msg: 'ok',
          data: { id: 10, version: '1.0.0', status: 'PUBLISHED', createdAt: '2026-03-20T00:00:00Z' },
          timestamp: '2026-03-28T00:00:00Z', requestId: 'playwright-e2e',
        }),
      })
    })
  })

  test('copies share text to clipboard when share button is clicked', async ({ page }) => {
    // Mock skill detail API
    await page.route('**/api/web/skills/global/test-skill', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: skill(1, 'Test Skill', {
            summary: 'A useful test skill for sharing',
            namespace: 'global',
            slug: 'test-skill',
          }),
          timestamp: '2026-03-28T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/space/global/test-skill')

    // Wait for skill detail page to load
    await expect(page.getByRole('heading', { name: /^Test Skill$/ })).toBeVisible()

    // Find and click the share button
    const shareButton = page.getByRole('button', { name: /Share/i })
    await expect(shareButton).toBeVisible()
    await shareButton.click()

    // Verify button shows "Copied" state
    await expect(page.getByRole('button', { name: /Copied/i })).toBeVisible()

    // Verify clipboard content
    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    expect(clipboardText).toContain('test-skill')
    expect(clipboardText).toContain('http://localhost:3000/space/global/test-skill')
    expect(clipboardText.split('\n')).toHaveLength(2)
  })

  test('share text includes skill description when available', async ({ page }) => {
    await page.route('**/api/web/skills/global/test-skill', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: skill(1, 'Test Skill', {
            summary: 'A useful test skill for sharing',
            namespace: 'global',
            slug: 'test-skill',
          }),
          timestamp: '2026-03-28T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/space/global/test-skill')

    await expect(page.getByRole('heading', { name: /^Test Skill$/ })).toBeVisible()

    const shareButton = page.getByRole('button', { name: /Share/i })
    await shareButton.click()

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    // Description is truncated to fit within 30 char limit (displayName + " - " + desc)
    expect(clipboardText).toContain('A useful test sk')
  })

  test('share button resets to normal state after 2 seconds', async ({ page }) => {
    await page.route('**/api/web/skills/global/test-skill', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: skill(1, 'Test Skill', {
            summary: 'A useful test skill',
            namespace: 'global',
            slug: 'test-skill',
          }),
          timestamp: '2026-03-28T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/space/global/test-skill')

    await expect(page.getByRole('heading', { name: /^Test Skill$/ })).toBeVisible()

    const shareButton = page.getByRole('button', { name: /Share/i })
    await shareButton.click()

    // Should show "Copied" immediately
    await expect(page.getByRole('button', { name: /Copied/i })).toBeVisible()

    // Should reset to "Share" after 2 seconds
    await page.waitForTimeout(2100)
    await expect(page.getByRole('button', { name: /^Share$/i })).toBeVisible()
  })

  test('formats namespaced skill correctly in share text', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])

    await page.route('**/api/web/skills/team-alpha/namespaced-skill', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: skill(2, 'Namespaced Skill', {
            summary: 'Team skill',
            namespace: 'team-alpha',
            slug: 'namespaced-skill',
          }),
          timestamp: '2026-03-28T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/space/team-alpha/namespaced-skill')

    await expect(page.getByRole('heading', { name: /^Namespaced Skill$/ })).toBeVisible()

    const shareButton = page.getByRole('button', { name: /Share/i })
    await shareButton.click()

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    expect(clipboardText).toContain('team-alpha/namespaced-skill')
    expect(clipboardText).toContain('http://localhost:3000/space/team-alpha/namespaced-skill')
  })
})
