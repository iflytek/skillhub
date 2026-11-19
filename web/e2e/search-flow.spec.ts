import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Search Flow (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders search controls and keeps query state in URL', async ({ page }) => {
    await page.goto('/search?q=agent&sort=downloads&page=0&starredOnly=false')

    await expect(page).toHaveURL('/search?q=agent&sort=downloads&page=0&starredOnly=false')
    const sort = page.getByRole('combobox', { name: 'Sort:', exact: true })
    await expect(sort).toHaveText('Downloads')
    await sort.click()
    for (const name of ['Relevance', 'Downloads', 'Newest']) {
      await expect(page.getByRole('option', { name, exact: true })).toBeVisible()
    }
    await page.keyboard.press('Escape')
    await expect(page.getByRole('button', { name: 'Saved only', exact: true })).toBeVisible()
  })

  test('redirects anonymous user to login when enabling starred filter', async ({ page }) => {
    await page.goto('/search?q=agent&sort=relevance&page=0&starredOnly=false')

    await page.getByRole('button', { name: 'Saved only', exact: true }).click()
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fsearch%3Fq%3Dagent%26sort%3Drelevance%26page%3D0%26starredOnly%3Dfalse$/)
  })
})
