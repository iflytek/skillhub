import { expect, test } from '@playwright/test'
import { mockLandingPage } from './helpers/route-mocks'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Landing Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await mockLandingPage(page)
  })

  test('submits the hero search to the search page', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Discover & Share AI Skills' })).toBeVisible()

    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill('agent ops')
    await searchInput.press('Enter')

    await expect(page).toHaveURL(/\/search\?q=agent(\+|%20)ops&sort=relevance&page=0&starredOnly=false$/)
    await expect(page.getByRole('heading', { name: /^Latest Release$/ })).toBeVisible()
  })

  test('navigates to search and redirects anonymous publish attempts to login', async ({ page }) => {
    await page.goto('/')

    await page.getByRole('link', { name: 'Explore Skills' }).click()
    await expect(page).toHaveURL('/search?q=&sort=relevance&page=0&starredOnly=false')

    await page.goto('/')
    await page.getByRole('link', { name: 'Publish Skill' }).click()

    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard%2Fpublish$/)
    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()
  })
})
