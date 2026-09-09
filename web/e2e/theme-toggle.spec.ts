import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Light and dark theme', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({ 'X-Mock-User-Id': 'local-user' })
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            userId: 'theme-layout-user',
            displayName: 'Theme Layout User',
            avatarUrl: 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==',
            platformRoles: [],
            oauthProvider: 'local',
            canChangePassword: true,
          },
          timestamp: '2026-09-01T00:00:00Z',
          requestId: 'theme-auth-fixture',
        }),
      })
    })
    await page.route('**/api/web/me/namespaces', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: [],
          timestamp: '2026-09-01T00:00:00Z',
          requestId: 'theme-namespace-fixture',
        }),
      })
    })
    await page.route('**/api/web/notifications/unread-count', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: { count: 1 },
          timestamp: '2026-09-01T00:00:00Z',
          requestId: 'theme-unread-fixture',
        }),
      })
    })
    await page.route('**/api/web/me/stars?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: { items: [], total: 0, page: 0, size: 100 },
          timestamp: '2026-09-01T00:00:00Z',
          requestId: 'theme-stars-fixture',
        }),
      })
    })
    await page.addInitScript(() => {
      const observedWindow = window as Window & { __themeAtFirstReactContent?: boolean }
      const observer = new MutationObserver(() => {
        const root = document.querySelector('#root')
        if (root?.childElementCount) {
          observedWindow.__themeAtFirstReactContent = document.documentElement.classList.contains('dark')
          observer.disconnect()
        }
      })
      observer.observe(document, { childList: true, subtree: true })
      if (!window.sessionStorage.getItem('theme-test-initialized')) {
        window.localStorage.removeItem('skillhub-theme')
        window.sessionStorage.setItem('theme-test-initialized', 'true')
      }
    })
  })

  test('switches themes and restores only the browser-local selection', async ({ page }, testInfo) => {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    page.on('pageerror', (error) => pageErrors.push(error.stack ?? error.message))

    await page.route('**/api/web/notifications?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            items: [{
              id: 9001,
              category: 'REVIEW',
              eventType: 'REVIEW_SUBMITTED',
              title: 'Theme notification fixture',
              bodyJson: JSON.stringify({ skillName: 'Theme preview', version: '1.0.0' }),
              targetRoute: '/search',
              status: 'UNREAD',
              createdAt: '2026-09-01T00:00:00Z',
            }],
            total: 1,
            page: 0,
            size: 5,
          },
          timestamp: '2026-09-01T00:00:00Z',
          requestId: 'theme-notification-fixture',
        }),
      })
    })

    await page.goto('/')
    await expect(page.locator('html')).not.toHaveClass(/dark/)
    const header = page.locator('header')
    const lightHeaderBackground = await header.evaluate((element) => getComputedStyle(element).backgroundColor)

    const themeSwitch = page.getByRole('switch', { name: 'Dark theme' })
    await expect(themeSwitch).toHaveAttribute('aria-checked', 'false')
    await themeSwitch.click()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect(themeSwitch).toHaveAttribute('aria-checked', 'true')
    await expect.poll(() => header.evaluate((element) => getComputedStyle(element).backgroundColor))
      .not.toBe(lightHeaderBackground)
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem('skillhub-theme'))).toBe('dark')
    const destructiveContrast = await page.evaluate(() => {
      const probe = document.createElement('button')
      probe.className = 'bg-destructive text-destructive-foreground'
      probe.textContent = 'Destructive contrast probe'
      document.body.append(probe)
      const styles = getComputedStyle(probe)

      const luminance = (color: string) => {
        const channels = color.match(/[\d.]+/g)?.slice(0, 3).map(Number)
        if (!channels || channels.length !== 3) {
          throw new Error(`Unable to parse computed color: ${color}`)
        }
        const linear = channels.map((channel) => {
          const normalized = channel / 255
          return normalized <= 0.04045
            ? normalized / 12.92
            : ((normalized + 0.055) / 1.055) ** 2.4
        })
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
      }

      const background = luminance(styles.backgroundColor)
      const foreground = luminance(styles.color)
      probe.remove()
      return (Math.max(background, foreground) + 0.05) / (Math.min(background, foreground) + 0.05)
    })
    expect(destructiveContrast).toBeGreaterThanOrEqual(4.5)

    await page.reload()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect(page.getByRole('switch', { name: 'Dark theme' })).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByRole('heading', { name: 'SkillHub', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => (
      window as Window & { __themeAtFirstReactContent?: boolean }
    ).__themeAtFirstReactContent)).toBe(true)

    await page.getByRole('link', { name: 'Search', exact: true }).first().click()
    await expect(page).toHaveURL(/\/search(?:\?|$)/)
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect(page.getByPlaceholder('Search skills...')).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('dark-desktop.png'), fullPage: true })

    const notificationButton = page.getByRole('button', { name: 'Notifications' })
    await notificationButton.click()
    await expect(page.getByText('Notifications', { exact: true })).toBeVisible()
    const firstNotification = page.getByRole('link').filter({ hasText: 'Review submitted' })
    await expect(firstNotification).toBeVisible()
    const backgroundBeforeHover = await firstNotification.evaluate((element) => getComputedStyle(element).backgroundColor)
    await firstNotification.hover()
    await expect.poll(() => firstNotification.evaluate((element) => getComputedStyle(element).backgroundColor))
      .not.toBe(backgroundBeforeHover)
    await page.screenshot({ path: testInfo.outputPath('dark-notifications.png'), fullPage: true })
    await notificationButton.click()

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('switch', { name: 'Dark theme' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('dark-mobile.png'), fullPage: true })

    await page.setViewportSize({ width: 320, height: 568 })
    const headerControls = page.locator('header > div')
    const [headerBox, controlsBox] = await Promise.all([header.boundingBox(), headerControls.boundingBox()])
    expect(headerBox).not.toBeNull()
    expect(controlsBox).not.toBeNull()
    expect((controlsBox?.x ?? 0) + (controlsBox?.width ?? 0)).toBeLessThanOrEqual(
      (headerBox?.x ?? 0) + (headerBox?.width ?? 0),
    )
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('dark-mobile-320.png'), fullPage: true })

    const unexpectedConsoleErrors = consoleErrors.filter((message) => (
      !message.includes("frame-ancestors' is ignored when delivered via a <meta> element")
    ))
    expect(unexpectedConsoleErrors).toEqual([])
    expect(pageErrors).toEqual([])
  })
})
