import { expect, test } from '@playwright/test'
import { mockStaticApis, setEnglishLocale, skill } from './helpers/api-mocks'

function buildSearchResponse(url: URL) {
  const q = url.searchParams.get('q') ?? ''
  const page = Number(url.searchParams.get('page') ?? '0')
  const size = Number(url.searchParams.get('size') ?? '12')

  if (q === 'skill') {
    return {
      items: [skill(2, 'Recovered Skill Search')],
      total: 1,
      page,
      size,
    }
  }

  return {
    items: [skill(1, 'Initial Search Result')],
    total: 1,
    page,
    size,
  }
}

test.describe('Network Error Handling', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await mockStaticApis(page, { authenticated: false })
  })

  test('shows an empty state when a search request fails', async ({ page }) => {
    let failSearchRequests = false

    await page.route('**/api/web/skills?**', async (route) => {
      if (failSearchRequests) {
        await route.abort('internetdisconnected')
        return
      }

      const url = new URL(route.request().url())
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: buildSearchResponse(url),
          timestamp: '2026-03-27T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/search?q=&sort=relevance&page=0&starredOnly=false')
    await expect(page.getByRole('heading', { name: /^Initial Search Result$/ })).toBeVisible()

    failSearchRequests = true

    const searchInput = page.getByRole('textbox')
    await searchInput.fill('test query')
    await searchInput.press('Enter')

    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible()
    await expect(searchInput).toHaveValue('test query')
  })

  test('renders the page shell even when the initial request fails', async ({ page }) => {
    await page.route('**/api/web/skills?**', async (route) => {
      await route.abort('internetdisconnected')
    })

    await page.goto('/search?q=&sort=relevance&page=0&starredOnly=false')

    await expect(page.getByRole('textbox')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible()
  })

  test('recovers when a later search request succeeds again', async ({ page }) => {
    let failSearchRequests = false

    await page.route('**/api/web/skills?**', async (route) => {
      if (failSearchRequests) {
        await route.abort('internetdisconnected')
        return
      }

      const url = new URL(route.request().url())
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: buildSearchResponse(url),
          timestamp: '2026-03-27T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/search?q=&sort=relevance&page=0&starredOnly=false')
    await expect(page.getByRole('heading', { name: /^Initial Search Result$/ })).toBeVisible()

    const searchInput = page.getByRole('textbox')

    failSearchRequests = true
    await searchInput.fill('offline query')
    await searchInput.press('Enter')
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible()

    failSearchRequests = false
    await searchInput.fill('skill')
    await searchInput.press('Enter')

    await expect(page.getByRole('heading', { name: /^Recovered Skill Search$/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'No results found' })).not.toBeVisible()
  })
})
