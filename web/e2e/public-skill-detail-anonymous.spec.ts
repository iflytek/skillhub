import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { getSearchCard, prepareSearchSeed, type PreparedSearchSeed } from './helpers/search-seed'

const SEARCH_URL = (q: string) => `/search?q=${encodeURIComponent(q)}&sort=relevance&page=0&starredOnly=false`

function latestSeed(seed: PreparedSearchSeed) {
  return {
    skill: seed.skills[seed.skills.length - 1],
    skillName: seed.skillNames[seed.skillNames.length - 1],
  }
}

let seeded: PreparedSearchSeed | undefined

test.describe('Public Skill Detail Anonymous Access (Real API)', () => {
  test.beforeAll(async ({ browser }, testInfo) => {
    seeded = await prepareSearchSeed(browser, testInfo, { count: 1 })
  })

  test.afterAll(async () => {
    await seeded?.dispose()
    seeded = undefined
  })

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('allows anonymous users to open a public skill detail and view install content', async ({ page }) => {
    const current = latestSeed(seeded!)

    await page.goto(SEARCH_URL(seeded!.keyword))
    const card = getSearchCard(page, current.skillName)
    await expect(card).toBeVisible({ timeout: 15_000 })

    await card.click()

    await expect(page).toHaveURL(new RegExp(`/space/${current.skill.namespace}/${current.skill.slug}$`))
    await expect(page).not.toHaveURL(/\/login\?returnTo=/)
    await expect(page.getByRole('heading', { name: current.skillName, exact: true })).toBeVisible()
    await expect(page.getByText('Install', { exact: true })).toBeVisible()
    await expect(page.getByText(new RegExp(`npx clawhub install ${current.skill.slug}`))).toBeVisible()
    await expect(page.getByRole('button', { name: 'Copy' }).first()).toBeVisible()
  })
})
