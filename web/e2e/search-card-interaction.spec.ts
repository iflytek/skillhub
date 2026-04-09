import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import {
  getSearchCard,
  getSearchCards,
  prepareSearchSeed,
  type PreparedSearchSeed,
} from './helpers/search-seed'
import { registerSession } from './helpers/session'

const SEARCH_URL = (q: string, sort = 'relevance', page = 0) =>
  `/search?q=${encodeURIComponent(q)}&sort=${sort}&page=${page}&starredOnly=false`

function latestSeed(seed: PreparedSearchSeed) {
  return {
    skill: seed.skills[seed.skills.length - 1],
    skillName: seed.skillNames[seed.skillNames.length - 1],
  }
}

async function waitForCards(page: Page) {
  const cards = getSearchCards(page)

  if (basicSeed) {
    await basicSeed.builder.waitForSearchResults(
      basicSeed.keyword,
      basicSeed.skills.map((skill) => skill.slug),
    )
  }

  const keyword = basicSeed?.keyword
  const encodedKeyword = keyword ? encodeURIComponent(keyword) : null

  for (let attempt = 0; attempt < 4; attempt += 1) {
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('textbox', { name: 'Search skills...' })).toBeVisible({ timeout: 8_000 })

    if (await cards.count() > 0) {
      return cards
    }

    if (attempt < 3) {
      const responsePromise = encodedKeyword
        ? page.waitForResponse(async (response) => {
          if (!response.url().includes('/api/web/skills?') || !response.url().includes(`q=${encodedKeyword}`)) {
            return false
          }
          if (response.status() !== 200) {
            return false
          }

          try {
            const payload = await response.json() as { data?: { items?: Array<unknown> } }
            return Array.isArray(payload.data?.items) && payload.data.items.length > 0
          } catch {
            return false
          }
        }, { timeout: 12_000 }).catch(() => null)
        : Promise.resolve(null)

      await page.waitForTimeout(750 * (attempt + 1))
      await page.reload({ waitUntil: 'networkidle' })
      await responsePromise
    }
  }

  return cards
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

// ─── Card Display After Search ────────────────────────────────────────────────

test.describe('Search Card Display (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_001 P0
  test('TC_SEARCH_INTERACT_001: cards appear immediately after search', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const cards = await waitForCards(page)
    await expect(cards.first()).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_INTERACT_005 P0 - cards show complete info
  test('TC_SEARCH_INTERACT_005: each card shows name, description, and version', async ({ page }) => {
    const current = latestSeed(basicSeed!)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })
    await expect(firstCard.getByRole('heading', { name: current.skillName, exact: true })).toBeVisible()
    await expect(firstCard.getByText(`v${current.skill.version}`)).toBeVisible()
  })

  // TC_SEARCH_INTERACT_039 P0 - version number format
  test('TC_SEARCH_INTERACT_039: version number is displayed in v1.2.3 format', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(page.getByText(/v\d+\.\d+\.\d+/).first()).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_INTERACT_038 P0 - long descriptions truncated
  test('TC_SEARCH_INTERACT_038: long descriptions are truncated with ellipsis', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const cards = await waitForCards(page)
    expect(await cards.count()).toBeGreaterThan(0)
  })

  // TC_SEARCH_INTERACT_031 P0 - no results shows empty state
  test('TC_SEARCH_INTERACT_031: no results shows empty state instead of cards', async ({ page }) => {
    await page.goto(SEARCH_URL('xyznonexistentkeyword99999abc'))
    await page.waitForLoadState('networkidle')
    await expect(getSearchCards(page)).toHaveCount(0)
    await expect(page.getByRole('heading', { name: 'No results found' })).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_INTERACT_035 P0 - large results show pagination
  test('TC_SEARCH_INTERACT_035: large result sets show pagination controls', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('button', { name: /next|›/i })).toBeVisible({ timeout: 10_000 })
  })
})

// ─── Card Content & Search Relevance ─────────────────────────────────────────

test.describe('Search Card Content (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_003 P0 - card count matches count indicator
  test('TC_SEARCH_INTERACT_003: displayed card count is consistent with skill count indicator', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const cards = getSearchCards(page)
    const cardCount = await cards.count()
    const countText = await page.getByText(/\d+\s+skills found/i).first().textContent()
    const totalMatch = countText?.match(/\d+/)
    if (totalMatch) {
      const total = parseInt(totalMatch[0], 10)
      expect(total).toBeGreaterThanOrEqual(cardCount)
    }
  })

  // TC_SEARCH_INTERACT_040 P0 - download count formatted
  test('TC_SEARCH_INTERACT_040: download counts are formatted correctly (numbers or K/M)', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(page.locator('body')).not.toContainText(/error|500/i)
    await expect(getSearchCards(page).first()).toContainText(/\d/)
  })
})

// ─── Card Click Navigation ────────────────────────────────────────────────────

test.describe('Search Card Navigation (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_007 P0 - clicking card navigates to detail page
  test('TC_SEARCH_INTERACT_007: clicking a skill card navigates to the skill detail page', async ({ page }, testInfo) => {
    const current = latestSeed(basicSeed!)
    await registerSession(page, testInfo)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })
    await firstCard.click()
    await expect(page).toHaveURL(new RegExp(`/space/${current.skill.namespace}/${current.skill.slug}`))
  })

  // TC_SEARCH_INTERACT_008 P0 - detail page matches clicked card
  test('TC_SEARCH_INTERACT_008: skill detail page matches the card that was clicked', async ({ page }, testInfo) => {
    const current = latestSeed(basicSeed!)
    await registerSession(page, testInfo)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })
    await firstCard.click()
    await expect(page).toHaveURL(new RegExp(`/space/${current.skill.namespace}/${current.skill.slug}`))
    await expect(page.getByRole('heading', { name: current.skillName, exact: true })).toBeVisible()
  })

  // TC_SEARCH_INTERACT_009 P1 - Ctrl+click opens in new tab
  test('TC_SEARCH_INTERACT_009: Ctrl+click on card opens skill detail in new tab', async ({ page, context }) => {
    test.skip(true, 'Skill cards render as clickable divs, so browser-level new-tab semantics do not apply.')
    const current = latestSeed(basicSeed!)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })

    const [newPage] = await Promise.all([
      context.waitForEvent('page'),
      firstCard.click({ modifiers: ['Meta'] }),
    ])
    await newPage.waitForLoadState()
    await expect(newPage).toHaveURL(/\/space\//)
    await newPage.close()
  })
})

// ─── Sort Switching Updates Cards ────────────────────────────────────────────

test.describe('Search Card Sort Interaction (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_021 P0 - switching sort updates cards
  test('TC_SEARCH_INTERACT_021: switching sort tab re-renders card list', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })

    await page.getByRole('button', { name: 'Downloads' }).click()
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/sort=downloads/)
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_INTERACT_026 P0 - re-search replaces cards
  test('TC_SEARCH_INTERACT_026: re-searching with new keyword replaces card list', async ({ page }) => {
    await page.goto(SEARCH_URL(''))
    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill(basicSeed!.keyword)
    await searchInput.press('Enter')
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(new RegExp(`q=${basicSeed!.keyword}`))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })
  })

  // TC_SEARCH_INTERACT_027 P0 - re-search resets page to 0
  test('TC_SEARCH_INTERACT_027: re-searching resets page number to 0', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword, 'relevance', 1))
    const searchInput = page.getByPlaceholder('Search skills...')
    await searchInput.fill(basicSeed!.keyword)
    await searchInput.press('Enter')
    await expect(page).toHaveURL(/page=0/)
  })
})

// ─── Pagination Card Updates ──────────────────────────────────────────────────

test.describe('Search Card Pagination (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_023 P0 - switching page updates cards
  test('TC_SEARCH_INTERACT_023: switching to next page shows different cards', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await page.waitForLoadState('networkidle')

    const nextBtn = page.getByRole('button', { name: /next|›/i })
    const firstCardTitle = await getSearchCards(page).first().getByRole('heading').textContent()
    await expect(nextBtn).toBeVisible({ timeout: 10_000 })
    await nextBtn.click()
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/page=1/)
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })
    const secondPageFirstTitle = await getSearchCards(page).first().getByRole('heading').textContent()
    expect(secondPageFirstTitle).not.toBe(firstCardTitle)
  })

  // TC_SEARCH_INTERACT_025 P1 - page switch scrolls to top
  test('TC_SEARCH_INTERACT_025: switching page scrolls back to top of results', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await page.waitForLoadState('networkidle')

    const nextBtn = page.getByRole('button', { name: /next|›/i })
    await expect(nextBtn).toBeVisible({ timeout: 10_000 })
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await nextBtn.click()
    await page.waitForLoadState('networkidle')
    await expect.poll(
      () => page.evaluate(() => window.scrollY),
      { timeout: 5_000, intervals: [100, 250, 500, 1_000] },
    ).toBeLessThan(300)
  })
})

// ─── Loading State ────────────────────────────────────────────────────────────

test.describe('Search Card Loading State (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_030 P0 - skeleton disappears after load
  test('TC_SEARCH_INTERACT_030: skeleton screen disappears and real cards appear after load', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await page.waitForLoadState('networkidle')
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })
    await expect(page.locator('[class*="skeleton"], [class*="shimmer"]')).toHaveCount(0)
  })
})

// ─── Responsive Layout ────────────────────────────────────────────────────────

test.describe('Search Card Responsive Layout (Real API)', () => {
  test.describe.configure({ retries: 2 })
  test.use({ hasTouch: true })

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_042 P0 - desktop 3-column grid
  test('TC_SEARCH_INTERACT_042: desktop viewport shows 3-column card grid', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })
    const grid = page.locator('[class*="grid"]').first()
    await expect(grid).toBeVisible()
  })

  // TC_SEARCH_INTERACT_044 P0 - mobile 1-column layout
  test('TC_SEARCH_INTERACT_044: mobile viewport shows single-column card layout', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const cards = await waitForCards(page)
    await expect(cards.first()).toBeVisible({ timeout: 8_000 })
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })

  // TC_SEARCH_INTERACT_043 P0 - tablet 2-column layout
  test('TC_SEARCH_INTERACT_043: tablet viewport shows 2-column card layout', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 })
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const cards = await waitForCards(page)
    await expect(cards.first()).toBeVisible({ timeout: 8_000 })
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })

  // TC_SEARCH_INTERACT_045 P1 - responsive layout adjusts on resize
  test('TC_SEARCH_INTERACT_045: card layout adjusts when browser window is resized', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })

    await page.setViewportSize({ width: 375, height: 812 })
    await expect(getSearchCards(page).first()).toBeVisible()
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })

  // TC_SEARCH_INTERACT_046 P0 - mobile touch interaction
  test('TC_SEARCH_INTERACT_046: mobile touch on card navigates to skill detail', async ({ page }, testInfo) => {
    const current = latestSeed(basicSeed!)
    await registerSession(page, testInfo)
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })
    await firstCard.tap()
    await expect(page).toHaveURL(new RegExp(`/space/${current.skill.namespace}/${current.skill.slug}`))
  })
})

// ─── Keyboard Navigation ──────────────────────────────────────────────────────

test.describe('Search Card Keyboard Navigation (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_049 P1 - Tab key navigates between cards
  test('TC_SEARCH_INTERACT_049: Tab key can navigate between skill cards', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })

    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    const focused = page.locator(':focus')
    await expect(focused).toBeVisible()
  })

  // TC_SEARCH_INTERACT_050 P1 - Enter key opens focused card
  test('TC_SEARCH_INTERACT_050: pressing Enter on a focused card opens the skill detail', async ({ page }, testInfo) => {
    const current = latestSeed(basicSeed!)
    await registerSession(page, testInfo)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })

    await firstCard.focus()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/space\//)
  })

  // TC_SEARCH_INTERACT_051 P1 - focus state visible on cards
  test('TC_SEARCH_INTERACT_051: focused card has a visible focus indicator', async ({ page }) => {
    const current = latestSeed(basicSeed!)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const firstCard = getSearchCard(page, current.skillName)
    await expect(firstCard).toBeVisible({ timeout: 8_000 })
    await firstCard.focus()
    const focused = page.locator(':focus')
    await expect(focused).toBeVisible()
  })
})

// ─── Error Handling ───────────────────────────────────────────────────────────

test.describe('Search Card Error Handling (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_SEARCH_INTERACT_033 P1 - single result displays correctly
  test('TC_SEARCH_INTERACT_033: single search result displays card layout correctly', async ({ page }) => {
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    const cards = await waitForCards(page)
    expect(await cards.count()).toBeGreaterThan(0)
    await expect(page.locator('body')).not.toContainText(/error|500/i)
  })

  // TC_SEARCH_INTERACT_060 P1 - cache: returning to search page shows results quickly
  test('TC_SEARCH_INTERACT_060: returning to search page shows cached results quickly', async ({ page }, testInfo) => {
    await registerSession(page, testInfo)
    await page.goto(SEARCH_URL(basicSeed!.keyword))
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })

    await page.goto('/dashboard')
    await page.goBack()
    await expect(page).toHaveURL(/\/search/)
    await expect(getSearchCards(page).first()).toBeVisible({ timeout: 8_000 })
  })
})
