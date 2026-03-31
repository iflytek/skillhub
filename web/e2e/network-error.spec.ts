import { expect, test } from '@playwright/test'
import { skill } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockSearchPage } from './helpers/route-mocks'

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
  })

  test('shows an empty state when a search request fails', async ({ page }) => {
    let failSearchRequests = false

    await mockSearchPage(page, {
      authenticated: false,
      searchHandler: async (url) => {
        if (failSearchRequests) {
          throw new Error('internetdisconnected')
        }

        return buildSearchResponse(url)
      },
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
    await mockSearchPage(page, {
      authenticated: false,
      searchHandler: async () => {
        throw new Error('internetdisconnected')
      },
    })

    await page.goto('/search?q=&sort=relevance&page=0&starredOnly=false')

    await expect(page.getByRole('textbox')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible()
  })

  test('recovers when a later search request succeeds again', async ({ page }) => {
    let failSearchRequests = false

    await mockSearchPage(page, {
      authenticated: false,
      searchHandler: async (url) => {
        if (failSearchRequests) {
          throw new Error('internetdisconnected')
        }

        return buildSearchResponse(url)
      },
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
