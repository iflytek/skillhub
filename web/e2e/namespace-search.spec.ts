import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

function waitForSkillSearch(page: Page, options: { namespace?: string; q?: string; sort?: string }) {
  return page.waitForResponse((response) => {
    if (!response.ok() || !response.url().includes('/api/web/skills?')) {
      return false
    }

    const url = new URL(response.url())
    const namespace = url.searchParams.get('namespace') ?? ''
    const query = url.searchParams.get('q') ?? ''
    const sort = url.searchParams.get('sort') ?? ''

    return namespace === (options.namespace ?? '')
      && query === (options.q ?? '')
      && (!options.sort || sort === options.sort)
  })
}

test.describe('Namespace Search (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.context().setExtraHTTPHeaders({
      'X-Mock-User-Id': 'local-admin',
    })
  })

  test('submits @namespace keyword search and clears the namespace filter', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.createNamespace('e2e-pm-search')
      const otherNamespace = await builder.createNamespace('e2e-dev-search')
      const namespaceSkill = await builder.publishSkill(namespace.slug, {
        name: 'roadmap-discovery',
        description: 'Roadmap planning skill for namespace search regression.',
      })
      const otherSkill = await builder.publishSkill(otherNamespace.slug, {
        name: 'roadmap-backend',
        description: 'Roadmap planning skill outside the selected namespace.',
      })
      await builder.waitForSearchResults('roadmap', [namespaceSkill.slug, otherSkill.slug])

      await page.goto('/search')
      await page.getByPlaceholder('Search skills...').fill(`@${namespace.slug} roadmap`)

      const filteredSearch = waitForSkillSearch(page, { namespace: namespace.slug, q: 'roadmap' })
      await page.getByRole('button', { name: 'Search', exact: true }).click()
      await filteredSearch

      await expect(page).toHaveURL(new RegExp(`namespace=${namespace.slug}`))
      await expect(page).toHaveURL(/q=roadmap/)
      await expect(page.getByRole('button', { name: `@${namespace.slug}` })).toBeVisible()
      await expect(page.getByRole('heading', { name: namespaceSkill.slug })).toBeVisible()
      await expect(page.getByText(`@${otherNamespace.slug}`)).toHaveCount(0)

      await page.goto(`/search?q=roadmap&namespace=${namespace.slug}&sort=downloads&page=1&starredOnly=false`)
      await expect(page.getByRole('button', { name: `@${namespace.slug}` })).toBeVisible()

      const unfilteredSearch = waitForSkillSearch(page, { q: 'roadmap', sort: 'downloads' })
      await page.getByRole('button', { name: `@${namespace.slug}` }).click()
      await unfilteredSearch

      await expect(page).toHaveURL(/q=roadmap/)
      await expect(page).toHaveURL(/sort=downloads/)
      await expect(page).toHaveURL(/page=0/)
      await expect(page).not.toHaveURL(new RegExp(`namespace=${namespace.slug}`))
      await expect(page.getByRole('heading', { name: namespaceSkill.slug })).toBeVisible()
      await expect(page.getByRole('heading', { name: otherSkill.slug })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })

  test('supports a sixty-four character namespace slug in search input', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.createNamespace('e2e-namespace-64-slug-search-case-alphaab')
      expect(namespace.slug).toHaveLength(64)
      const skill = await builder.publishSkill(namespace.slug, {
        name: 'boundary-search-agent',
        description: 'Boundary namespace search regression skill.',
      })
      await builder.waitForSearchResult('boundary', skill.slug)

      await page.goto('/search')
      await page.getByPlaceholder('Search skills...').fill(`@${namespace.slug} boundary`)

      const filteredSearch = waitForSkillSearch(page, { namespace: namespace.slug, q: 'boundary' })
      await page.getByRole('button', { name: 'Search', exact: true }).click()
      await filteredSearch

      await expect(page).toHaveURL(new RegExp(`namespace=${namespace.slug}`))
      await expect(page).toHaveURL(/q=boundary/)
      await expect(page.getByRole('button', { name: `@${namespace.slug}` })).toBeVisible()
      await expect(page.getByRole('heading', { name: skill.slug })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
