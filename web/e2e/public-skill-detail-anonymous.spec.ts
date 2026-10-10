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

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

let seeded: PreparedSearchSeed | undefined

test.describe('Public Skill Detail Anonymous Access (Real API)', () => {
  test.describe.configure({ timeout: 150_000 })

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

    await expect(page).toHaveURL(new RegExp(`/space/${current.skill.namespace}/${current.skill.slug}(\\?|$)`))
    await expect(page).not.toHaveURL(/\/login\?returnTo=/)
    const skillNameHeadings = page.getByRole('heading', { name: current.skillName, exact: true })
    await expect(skillNameHeadings).toHaveCount(2)
    await expect(skillNameHeadings.first()).toBeVisible()
    await expect(page.getByText('Install', { exact: true })).toBeVisible()
    const clawhubTarget = current.skill.namespace === 'global'
      ? current.skill.slug
      : `${current.skill.namespace}--${current.skill.slug}`
    const skillhubCoordinate = `@${current.skill.namespace}/${current.skill.slug}`
    const registryUrl = new URL(page.url()).origin

    await expect(page.getByRole('tab', { name: 'SkillHub CLI' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText(
      `npx @astron-team/skillhub@latest install ${skillhubCoordinate} --version ${current.skill.version} --registry ${registryUrl}`,
      { exact: true },
    )).toBeVisible()
    await expect(page.getByRole('button', { name: 'Copy' }).first()).toBeVisible()

    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: registryUrl })
    await page.getByTestId('install-for-agent-button').click()
    const agentPrompt = await page.evaluate(() => navigator.clipboard.readText())
    expect(agentPrompt).toContain(`${registryUrl}/registry/skill.md`)
    expect(agentPrompt).toContain(skillhubCoordinate)
    expect(agentPrompt).toContain(current.skill.version)
    expect(agentPrompt).not.toContain('explain why and stop')
    expect(agentPrompt).not.toContain('do not use another source')
    expect(agentPrompt).not.toContain('fallback')

    await page.getByRole('tab', { name: 'ClawHub CLI' }).click()

    await expect(page.getByRole('tab', { name: 'ClawHub CLI' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText(new RegExp(`npx clawhub install ${escapeRegExp(clawhubTarget)} --registry`))).toBeVisible()
  })
})
