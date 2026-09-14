import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Landing Navigation (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('submits the hero search to the search page', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Turn team expertise into Agent-ready skills' })).toBeVisible()

    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill('agent ops')
    await searchInput.press('Enter')

    await expect(page).toHaveURL(/\/search\?q=agent(\+|%20)ops&sort=relevance&page=0&starredOnly=false$/)
  })

  test('redirects anonymous publish attempts to login', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'Publish Skill' }).click()
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard%2Fpublish$/)
  })

  test('keeps the landing page within a 390px viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Turn team expertise into Agent-ready skills' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})
