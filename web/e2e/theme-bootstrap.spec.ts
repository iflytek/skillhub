import { expect, test } from '@playwright/test'

const THEME_STORAGE_KEY = 'skillhub-theme'
const SYSTEM_PREFERS_DARK_KEY = 'skillhub-theme-system-prefers-dark'

test.describe('Theme bootstrap smoke', () => {
  test('no FOUC: applies persisted dark mode and bootstrap markers before interactive frame', async ({ context, page }) => {
    await context.addInitScript((storageKey) => {
      window.localStorage.setItem(storageKey, 'dark')
    }, THEME_STORAGE_KEY)

    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('html')).toHaveAttribute('data-theme-bootstrap', 'done')
    const mode = await page.locator('html').getAttribute('data-theme-mode')
    expect(mode).toBe('dark')
    await expect(page.locator('html')).toHaveClass(/(^|\s)dark(\s|$)/)
  })

  test('released-toggle contract: exposes tri-state icon switch on /login and syncs persisted mode', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const toggle = page.getByRole('button', { name: /Theme mode: system/i })
    await expect(toggle).toBeVisible()

    await toggle.click()
    await expect(page.getByRole('button', { name: /Theme mode: light/i })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-theme-mode', 'light')
    await expect.poll(async () => page.evaluate((storageKey) => window.localStorage.getItem(storageKey), THEME_STORAGE_KEY))
      .toBe('light')

    await page.getByRole('button', { name: /Theme mode: light/i }).click()
    await expect(page.getByRole('button', { name: /Theme mode: dark/i })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-theme-mode', 'dark')
    await expect.poll(async () => page.evaluate((storageKey) => window.localStorage.getItem(storageKey), THEME_STORAGE_KEY))
      .toBe('dark')
  })

  test('persists preference and follows mocked system mode changes', async ({ context, page }) => {
    await context.addInitScript(({ storageKey, systemPrefersDarkKey }) => {
      window.matchMedia = ((query: string) => ({
        media: query,
        matches:
          query === '(prefers-color-scheme: dark)'
            ? window.localStorage.getItem(systemPrefersDarkKey) === '1'
            : false,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      })) as typeof window.matchMedia

      if (window.localStorage.getItem(storageKey) === null) {
        window.localStorage.setItem(storageKey, 'system')
      }

      if (window.localStorage.getItem(systemPrefersDarkKey) === null) {
        window.localStorage.setItem(systemPrefersDarkKey, '0')
      }
    }, {
      storageKey: THEME_STORAGE_KEY,
      systemPrefersDarkKey: SYSTEM_PREFERS_DARK_KEY,
    })

    await page.goto('/login', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('html')).toHaveAttribute('data-theme-mode', 'light')

    await page.evaluate((systemPrefersDarkKey) => {
      window.localStorage.setItem(systemPrefersDarkKey, '1')
    }, SYSTEM_PREFERS_DARK_KEY)
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.locator('html')).toHaveAttribute('data-theme-mode', 'dark')
    await expect(page.locator('html')).toHaveClass(/(^|\s)dark(\s|$)/)

    await page.evaluate((storageKey) => {
      window.localStorage.setItem(storageKey, 'light')
    }, THEME_STORAGE_KEY)
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.locator('html')).toHaveAttribute('data-theme-mode', 'light')
    await expect(page.locator('html')).toHaveClass(/(^|\s)light(\s|$)/)
  })
})
