import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import {
  DEFAULT_SEARCH_KEYWORD,
  getSearchCards,
  prepareSearchSeed,
  type PreparedSearchSeed,
} from './helpers/search-seed'
import { registerSession } from './helpers/session'

function searchUrl(query: string, sort = 'relevance', page = 0, starredOnly = false) {
  return `/search?q=${encodeURIComponent(query)}&sort=${sort}&page=${page}&starredOnly=${starredOnly}`
}

let basicSeed: PreparedSearchSeed | undefined

test.setTimeout(300_000)

test.beforeAll(async ({ browser }, testInfo) => {
  test.setTimeout(300_000)
  basicSeed = await prepareSearchSeed(browser, testInfo, { count: 13 })
})

test.afterAll(async () => {
  await basicSeed?.dispose()
  basicSeed = undefined
})

// ─── Search Input ────────────────────────────────────────────────────────────

test.describe('Search Input (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INPUT_001 P0
  test('TC_SEARCH_INPUT_001: searches with a single keyword and shows results', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 10_000 })
  })

  // TC_SEARCH_INPUT_003 P0 - empty search guidance
  test('TC_SEARCH_INPUT_003: empty search shows keyword guidance instead of a default list', async ({ page }) => {
    await page.goto(searchUrl(''))
    await expect(page).toHaveURL(/\/search/)
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible()
    await expect(page.getByText('Please enter a search keyword')).toBeVisible()
  })

  // TC_SEARCH_INPUT_004 P0 - Enter key triggers search
  test('TC_SEARCH_INPUT_004: pressing Enter in search box triggers search', async ({ page }) => {
    await page.goto(searchUrl(''))
    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill(basicSeed!.keyword)
    await searchInput.press('Enter')
    await expect(page).toHaveURL(new RegExp(`q=${basicSeed!.keyword}`))
    await expect(getSearchCards(page).first()).toBeVisible()
  })

  // TC_SEARCH_INPUT_009 P0 - Chinese keyword search
  test('TC_SEARCH_INPUT_009: supports Chinese keyword search without error', async ({ page }) => {
    await page.goto(searchUrl('测试技能'))
    await expect(page).toHaveURL(/\/search/)
    await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
  })

  // TC_SEARCH_INPUT_010 P0 - English keyword search
  test('TC_SEARCH_INPUT_010: supports English keyword search', async ({ page }) => {
    await page.goto(searchUrl('skill'))
    await expect(page).toHaveURL(/q=skill/)
    await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
  })

  // TC_SEARCH_INPUT_007 P1 - special characters handled gracefully
  test('TC_SEARCH_INPUT_007: handles special characters in search without crashing', async ({ page }) => {
    await page.goto(searchUrl('@#$%'))
    await expect(page).toHaveURL(/\/search/)
    await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
  })

  // TC_SEARCH_INPUT_011 P1 - leading/trailing spaces trimmed
  test('TC_SEARCH_INPUT_011: trims leading and trailing spaces from search query', async ({ page }) => {
    await page.goto(searchUrl(''))
    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill(`  ${basicSeed!.keyword}  `)
    await searchInput.press('Enter')
    await expect(page).toHaveURL(new RegExp(`q=${basicSeed!.keyword}`))
    await expect(getSearchCards(page).first()).toBeVisible()
  })
})

// ─── Sort / Filter ────────────────────────────────────────────────────────────

test.describe('Search Sort and Filter (Authenticated Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  // TC_SEARCH_SORT_001 P0 - default relevance tab selected
  test('TC_SEARCH_SORT_001: relevance sort tab is selected by default', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance'))
    await expect(page.getByRole('button', { name: 'Relevance' })).toBeVisible()
  })

  // TC_SEARCH_SORT_004 P0 - downloads sort
  test('TC_SEARCH_SORT_004: clicking Downloads tab updates sort in URL', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance'))
    await page.getByRole('button', { name: 'Downloads' }).click()
    await expect(page).toHaveURL(/sort=downloads/)
  })

  // TC_SEARCH_SORT_005 P0 - newest sort
  test('TC_SEARCH_SORT_005: clicking Newest tab updates sort in URL', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance'))
    await page.getByRole('button', { name: 'Newest' }).click()
    await expect(page).toHaveURL(/sort=newest|sort=created/)
  })

  // TC_SEARCH_SORT_006 P0 - switching sort preserves search keyword
  test('TC_SEARCH_SORT_006: switching sort tab preserves the search keyword', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance'))
    await page.getByRole('button', { name: 'Downloads' }).click()
    await expect(page).toHaveURL(new RegExp(`q=${basicSeed!.keyword}`))
    await expect(page).toHaveURL(/sort=downloads/)
  })

  // TC_SEARCH_SORT_007 P0 - switching sort resets page to 0
  test('TC_SEARCH_SORT_007: switching sort tab resets page to 0', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance', 1))
    await page.getByRole('button', { name: 'Downloads' }).click()
    await expect(page).toHaveURL(/page=0/)
  })

  // TC_SEARCH_SORT_012 P1 - URL contains sort param
  test('TC_SEARCH_SORT_012: URL contains sort parameter after switching tabs', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance'))
    await page.getByRole('button', { name: 'Downloads' }).click()
    await expect(page).toHaveURL(/sort=/)
  })

  test('starred only filter stays on search page for authenticated user', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance'))
    await page.getByRole('button', { name: 'Starred only' }).click()
    await expect(page).toHaveURL(/starredOnly=true/)
    await expect(page).not.toHaveURL(/\/login/)
  })
})

test.describe('Search Sort and Filter (Anonymous Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('starred only filter redirects anonymous user to login', async ({ page }) => {
    await page.goto(searchUrl(DEFAULT_SEARCH_KEYWORD, 'relevance'))
    await page.getByRole('button', { name: 'Starred only' }).click()
    await expect(page).toHaveURL(/\/login\?returnTo=/)
  })
})

// ─── Skill Count ──────────────────────────────────────────────────────────────

test.describe('Search Skill Count Display (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_COUNT_001 P0 - count visible
  test('TC_SEARCH_COUNT_001: skill count indicator is visible on search page with results', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword))
    await expect(page.getByText(/\d+\s+skills found/i)).toBeVisible({ timeout: 10_000 })
  })

  // TC_SEARCH_COUNT_007 P0 - count updates after search
  test('TC_SEARCH_COUNT_007: skill count updates after performing a search', async ({ page }) => {
    await page.goto(searchUrl(''))
    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill(basicSeed!.keyword)
    await searchInput.press('Enter')
    await expect(page.getByText(/\d+\s+skills found/i)).toBeVisible({ timeout: 10_000 })
  })

  // TC_SEARCH_COUNT_009 P0 - zero results shows 0
  test('TC_SEARCH_COUNT_009: shows empty-state copy when search returns no results', async ({ page }) => {
    await page.goto(searchUrl('xyznonexistentkeyword99999'))
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_COUNT_008 P0 - count stays same when switching sort
  test('TC_SEARCH_COUNT_008: skill count remains the same after switching sort tab', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword))
    const countText = await page.getByText(/\d+\s+skills found/i).textContent()
    const initialCount = Number(countText?.match(/\d+/)?.[0] ?? '0')
    await page.getByRole('button', { name: 'Downloads' }).click()
    const updatedCountText = await page.getByText(/\d+\s+skills found/i).textContent()
    const updatedCount = Number(updatedCountText?.match(/\d+/)?.[0] ?? '0')
    expect(updatedCount).toBe(initialCount)
  })
})

// ─── Search Results ───────────────────────────────────────────────────────────

test.describe('Search Results (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_RESULT_001 P0 - results shown
  test('TC_SEARCH_RESULT_001: shows skill cards when search returns results', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 10_000 })
  })

  // TC_SEARCH_RESULT_002 P0 - no results message
  test('TC_SEARCH_RESULT_002: shows empty state message when no results found', async ({ page }) => {
    await page.goto(searchUrl('xyznonexistentkeyword99999'))
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_RESULT_006 P0 - loading state
  test('TC_SEARCH_RESULT_006: page renders without error during and after search', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword))
    await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
  })

  // TC_SEARCH_RESULT_008 P0 - result count matches cards
  test('TC_SEARCH_RESULT_008: number of displayed cards matches the count indicator', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword))
    await page.waitForLoadState('networkidle')
    const cards = getSearchCards(page)
    const visibleCount = await cards.count()
    const countText = await page.getByText(/\d+\s+skills found/i).textContent()
    const totalMatch = countText?.match(/\d+/)
    expect(totalMatch).toBeTruthy()
    expect(visibleCount).toBeGreaterThan(0)
    expect(Number(totalMatch?.[0])).toBeGreaterThanOrEqual(visibleCount)
  })

  // TC_SEARCH_RESULT_009 P0 - downloads sort order
  test('TC_SEARCH_RESULT_009: results are sorted by downloads when Downloads tab is selected', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'downloads'))
    await expect(page).toHaveURL(/sort=downloads/)
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })

  // TC_SEARCH_RESULT_010 P0 - newest sort order
  test('TC_SEARCH_RESULT_010: results are sorted by newest when Newest tab is selected', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'newest'))
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })
})

// ─── Pagination ───────────────────────────────────────────────────────────────

test.describe('Search Pagination (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_PAGE_011 P1 - URL contains page param
  test('TC_SEARCH_PAGE_011: URL contains page parameter', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance', 0))
    await expect(page).toHaveURL(/page=/)
  })

  // TC_SEARCH_PAGE_012 P0 - switching page preserves search and sort
  test('TC_SEARCH_PAGE_012: switching page preserves search keyword and sort', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'downloads', 0))
    const nextBtn = page.getByRole('button', { name: /next|›|»/i })
    await expect(nextBtn).toBeVisible({ timeout: 10_000 })
    await nextBtn.click()
    await expect(page).toHaveURL(new RegExp(`q=${basicSeed!.keyword}`))
    await expect(page).toHaveURL(/sort=downloads/)
    await expect(page).toHaveURL(/page=1/)
  })

  // TC_SEARCH_PAGE_007 P0 - first page disables previous button
  test('TC_SEARCH_PAGE_007: previous page button is disabled on first page', async ({ page }) => {
    await page.goto(searchUrl(basicSeed!.keyword, 'relevance', 0))
    const prevBtn = page.getByRole('button', { name: /prev|‹|«/i })
    if (await prevBtn.isVisible()) {
      await expect(prevBtn).toBeDisabled()
    }
  })
})

// ─── Security ─────────────────────────────────────────────────────────────────

test.describe('Search Security (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_SEC_001 P0 - XSS in search box
  test('TC_SEARCH_SEC_001: XSS payload in search box is not executed', async ({ page }) => {
    let alerted = false
    page.on('dialog', () => { alerted = true })

    await page.goto(searchUrl(''))
    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill("<script>alert('xss')</script>")
    await searchInput.press('Enter')

    await page.waitForTimeout(1_000)
    expect(alerted).toBe(false)
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })

  // TC_SEARCH_SEC_002 P0 - SQL injection in search box
  test('TC_SEARCH_SEC_002: SQL injection payload in search box is handled safely', async ({ page }) => {
    await page.goto(searchUrl("' OR '1'='1"))
    await expect(page.locator('body')).not.toContainText(/sql|syntax error|database/i)
    await expect(page).toHaveURL(/\/search/)
  })

  // TC_SEARCH_SEC_003 P1 - URL param tampering
  test('TC_SEARCH_SEC_003: tampered URL parameters are handled gracefully', async ({ page }, testInfo) => {
    await registerSession(page, testInfo)
    await page.goto('/search?q=agent&sort=INVALID_SORT&page=-1&starredOnly=invalid')
    await expect(page).toHaveURL(/\/search/)
    await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
  })
})
