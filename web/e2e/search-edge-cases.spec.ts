import { expect, test, type Browser, type TestInfo } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { getSearchCards } from './helpers/search-seed'
import { createFreshSession, loginWithCredentials } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

function searchUrl(query: string, sort = 'relevance', page = 0, starredOnly = false) {
  return `/search?q=${encodeURIComponent(query)}&sort=${sort}&page=${page}&starredOnly=${starredOnly}`
}

function adminCredentials() {
  return {
    username: process.env.E2E_ADMIN_USERNAME?.trim() || process.env.BOOTSTRAP_ADMIN_USERNAME?.trim() || 'admin',
    password: process.env.E2E_ADMIN_PASSWORD?.trim() || process.env.BOOTSTRAP_ADMIN_PASSWORD?.trim() || 'ChangeMe!2026',
  }
}

interface PublishedSkill {
  skillId: number
  namespace: string
  slug: string
  version: string
}

async function publishApprovedSkills(browser: Browser, testInfo: TestInfo): Promise<{
  cleanup: () => Promise<void>
  skills: PublishedSkill[]
}> {
  const publisherContext = await browser.newContext()
  const publisherPage = await publisherContext.newPage()
  const publisherBuilder = new E2eTestDataBuilder(publisherPage, testInfo)

  const adminContext = await browser.newContext()
  const adminPage = await adminContext.newPage()
  const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)

  try {
    await createFreshSession(publisherPage, testInfo)
    await publisherBuilder.init()
    const namespace = await publisherBuilder.ensureWritableNamespace()

    const agentStudio = await publisherBuilder.publishSkill(namespace.slug, {
      name: 'Agent Studio',
      description: 'Visual builder workspace for multi-agent flows.',
    })
    const toolkit = await publisherBuilder.publishSkill(namespace.slug, {
      name: 'Toolkit',
      description: 'agent helper toolkit for internal automation teams.',
    })

    await loginWithCredentials(adminPage, adminCredentials(), testInfo)
    await adminBuilder.init()

    for (const skill of [agentStudio, toolkit]) {
      const reviewTaskId = await adminBuilder.waitForPendingReview(skill.namespace, skill.slug, skill.version)
      await adminBuilder.approveReview(reviewTaskId)
    }

    await publisherBuilder.waitForSearchResults('agent', [agentStudio.slug, toolkit.slug])

    return {
      skills: [agentStudio, toolkit],
      cleanup: async () => {
        await adminContext.close()
        await publisherBuilder.cleanup()
        await publisherContext.close()
      },
    }
  } catch (error) {
    await adminContext.close()
    await publisherBuilder.cleanup()
    await publisherContext.close()
    throw error
  }
}

test.describe('Search Edge Cases (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('normalizes invalid search sort and clamps oversized paging before calling the API', async ({ page }) => {
    const normalizedRequest = page.waitForResponse((response) => {
      if (!response.url().includes('/api/web/skills?')) {
        return false
      }

      const url = new URL(response.url())
      return response.status() === 200
        && url.searchParams.get('q') === 'agent'
        && url.searchParams.get('sort') === 'newest'
        && url.searchParams.get('page') === '10000'
        && url.searchParams.get('size') === '12'
    })

    await page.goto(searchUrl('agent', 'weird-sort', 999999999, false))
    await normalizedRequest

    await expect(page.getByRole('button', { name: 'Newest' })).toBeVisible()
    await expect(page.locator('body')).not.toContainText(/error|500|crash/i)
  })

  test('starred-only relevance prefers title matches over summary-only matches', async ({ page, browser }, testInfo) => {
    const published = await publishApprovedSkills(browser, testInfo)
    const viewerBuilder = new E2eTestDataBuilder(page, testInfo)

    try {
      await createFreshSession(page, testInfo)
      await viewerBuilder.init()
      await viewerBuilder.starSkill(published.skills[0].skillId)
      await viewerBuilder.starSkill(published.skills[1].skillId)

      await page.goto(searchUrl('agent', 'relevance', 0, true))

      const cards = getSearchCards(page)
      await expect(cards.first()).toBeVisible({ timeout: 10_000 })
      await expect(cards).toHaveCount(2)
      await expect(cards.first().getByRole('heading')).toHaveText('Agent Studio')
    } finally {
      await viewerBuilder.cleanup()
      await published.cleanup()
    }
  })
})
