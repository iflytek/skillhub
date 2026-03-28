import { expect, test } from '@playwright/test'
import { mockCommonApis, setEnglishLocale, skill } from './helpers/api-mocks'

function buildSearchResponse(url: URL) {
  const q = url.searchParams.get('q') ?? ''
  const sort = url.searchParams.get('sort') ?? 'newest'
  const label = url.searchParams.get('label') ?? ''
  const page = Number(url.searchParams.get('page') ?? '0')
  const size = Number(url.searchParams.get('size') ?? '12')

  if (q === 'agent' && sort === 'downloads' && label === 'official' && page === 1) {
    return {
      items: [skill(4, 'Official Agent Page Two')],
      total: 24,
      page,
      size,
    }
  }

  if (q === 'agent' && sort === 'downloads' && label === 'official') {
    return {
      items: [skill(3, 'Official Agent')],
      total: 24,
      page,
      size,
    }
  }

  if (q === 'agent' && sort === 'downloads') {
    return {
      items: [skill(2, 'Download Leader Agent')],
      total: 1,
      page,
      size,
    }
  }

  if (q === 'agent') {
    return {
      items: [skill(1, 'Agent Builder')],
      total: 1,
      page,
      size,
    }
  }

  return {
    items: [skill(10, 'Alpha Search'), skill(11, 'Beta Search')],
    total: 2,
    page,
    size,
  }
}

test.describe('Search Page Flows', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await mockCommonApis(page, {
      searchHandler: buildSearchResponse,
    })
  })

  test('updates URL state and results when searching, sorting, and filtering', async ({ page }) => {
    await page.goto('/search?q=&sort=relevance&page=0&starredOnly=false')

    await expect(page.getByRole('heading', { name: /^Alpha Search$/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Beta Search$/ })).toBeVisible()

    const searchInput = page.getByRole('textbox')
    await searchInput.fill('agent')
    await searchInput.press('Enter')

    await expect(page).toHaveURL(/\/search\?q=agent&sort=relevance&page=0&starredOnly=false$/)
    await expect(page.getByRole('heading', { name: /^Agent Builder$/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Alpha Search$/ })).not.toBeVisible()

    await page.getByRole('button', { name: 'Downloads' }).click()

    await expect(page).toHaveURL(/\/search\?q=agent&sort=downloads&page=0&starredOnly=false$/)
    await expect(page.getByRole('heading', { name: /^Download Leader Agent$/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Agent Builder$/ })).not.toBeVisible()

    await page.getByRole('button', { name: 'Official' }).click()

    await expect(page).toHaveURL(/\/search\?q=agent&label=official&sort=downloads&page=0&starredOnly=false$/)
    await expect(page.getByRole('heading', { name: /^Official Agent$/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Download Leader Agent$/ })).not.toBeVisible()
  })

  test('keeps the active label when paginating', async ({ page }) => {
    await page.goto('/search?q=agent&label=official&sort=downloads&page=0&starredOnly=false')

    await expect(page.getByRole('heading', { name: /^Official Agent$/ })).toBeVisible()

    await page.getByRole('button', { name: 'Next' }).click()

    await expect(page).toHaveURL(/\/search\?q=agent&label=official&sort=downloads&page=1&starredOnly=false$/)
    await expect(page.getByRole('heading', { name: /^Official Agent Page Two$/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Official Agent$/ })).not.toBeVisible()
  })

  test('redirects unauthenticated users to login when enabling starred-only', async ({ page }) => {
    await page.goto('/search?q=agent&sort=downloads&page=0&starredOnly=false')

    await page.getByRole('button', { name: 'Starred only' }).click()

    await expect(page).toHaveURL(/\/login\?returnTo=/)

    const currentUrl = new URL(page.url())
    expect(currentUrl.pathname).toBe('/login')
    expect(currentUrl.searchParams.get('returnTo')).toBe('/search?q=agent&sort=downloads&page=0&starredOnly=false')
    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()
  })
})
