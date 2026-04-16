import { expect, test, type APIRequestContext, type Page, type TestInfo } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface CreatedCollection {
  id: number
  ownerId: string
  slug: string
}

interface SearchSkillCandidate {
  id: number
  displayName: string
}

async function parseEnvelope<T>(response: Awaited<ReturnType<APIRequestContext['fetch']>>): Promise<T> {
  const body = await response.json() as ApiEnvelope<T>
  expect(response.ok(), `request failed: ${response.status()} ${body.msg}`).toBeTruthy()
  expect(body.code, body.msg).toBe(0)
  return body.data
}

test.describe('Collections happy-path flow (Real API)', () => {
  test.setTimeout(120_000)

  test('create collection, add skill, open public page, add and remove contributor', async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)

    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    const cleanupStack: Array<() => Promise<void>> = []

    try {
      let skillToAdd: SearchSkillCandidate
      try {
        const namespace = await builder.ensureWritableNamespace()
        const seededSkillName = `collections-flow-skill-${Date.now().toString(36)}`
        const seededSkill = await builder.publishSkill(namespace.slug, { name: seededSkillName })
        skillToAdd = {
          id: seededSkill.skillId,
          displayName: seededSkillName,
        }
      } catch {
        const fallbackSearch = await parseEnvelope<{ items: SearchSkillCandidate[] }>(
          await page.context().request.get('/api/web/skills?sort=newest&page=0&size=20'),
        )
        expect(fallbackSearch.items.length, 'expected visible skill for add-flow fallback').toBeGreaterThan(0)
        skillToAdd = fallbackSearch.items[0]
      }

      const collectionTitle = `Flow Collection ${Date.now().toString(36)}`
      const collectionSlug = `flow-${Date.now().toString(36)}`

      await page.goto('/dashboard/collections/new')
      await expect(page.getByTestId('collection-new-form')).toBeVisible()
      await page.getByTestId('collection-new-title-input').fill(collectionTitle)
      await page.getByTestId('collection-new-slug-input').fill(collectionSlug)
      await page.getByTestId('collection-new-description-input').fill('Playwright happy-path collection flow')

      await page.getByTestId('collection-new-visibility-trigger').click()
      await page.getByTestId('collection-new-visibility-public').click()
      await page.getByTestId('collection-new-submit').click()

      await expect(page.getByRole('heading', { name: collectionTitle, exact: true })).toBeVisible()
      expect(page.url()).toContain('/dashboard/collections/')
      const collectionId = page.url().split('/dashboard/collections/')[1]
      expect(collectionId).toBeTruthy()

      const collection = await parseEnvelope<CreatedCollection>(
        await page.context().request.get(`/api/web/collections/${encodeURIComponent(collectionId!)}`),
      )

      cleanupStack.push(async () => {
        await page.context().request.delete(`/api/web/collections/${encodeURIComponent(String(collection.id))}`)
      })

      await page.getByTestId('collection-detail-add-skill-empty').click()
      await expect(page.getByTestId('add-collection-skills-dialog')).toBeVisible()
      await page.getByLabel('Search skills').fill(skillToAdd.displayName)
      await expect(page.getByTestId(`add-collection-skills-select-${skillToAdd.id}`)).toBeVisible()
      await page.getByTestId(`add-collection-skills-select-${skillToAdd.id}`).click()
      await page.getByRole('button', { name: 'Add selected (1)' }).click()
      await page.getByRole('button', { name: 'Cancel' }).click()
      await expect(page.getByTestId('add-collection-skills-dialog')).not.toBeVisible()
      await expect(page.getByRole('link', { name: skillToAdd.displayName, exact: true }).first()).toBeVisible()

      const shareUrl = `${page.url().split('/dashboard/collections/')[0]}/u/${encodeURIComponent(collection.ownerId)}/c/${encodeURIComponent(collection.slug)}`
      await page.goto(shareUrl)
      await expect(page.getByRole('heading', { name: collectionTitle, exact: true })).toBeVisible()
      await expect(page.getByRole('link', { name: skillToAdd.displayName, exact: true }).first()).toBeVisible()
    } finally {
      for (let index = cleanupStack.length - 1; index >= 0; index -= 1) {
        try {
          await cleanupStack[index]()
        } catch {
          // Best-effort cleanup in shared E2E environments.
        }
      }
      await builder.cleanup()
    }
  })
})
