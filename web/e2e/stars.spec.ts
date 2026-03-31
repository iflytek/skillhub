import { expect, test } from '@playwright/test'
import { skill } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockStarsPage } from './helpers/route-mocks'

test.describe('My Stars Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders the empty state when no starred skills exist', async ({ page }) => {
    await mockStarsPage(page, {
      stars: [],
    })

    await page.goto('/dashboard/stars')

    await expect(page.getByRole('heading', { name: 'My Stars' })).toBeVisible()
    await expect(page.getByText('No starred skills yet')).toBeVisible()
  })

  test('paginates starred skills across pages', async ({ page }) => {
    await mockStarsPage(page, {
      stars: Array.from({ length: 13 }, (_, index) => skill(200 + index, `Star Skill ${index + 1}`, {
        namespace: index % 2 === 0 ? 'team-alpha' : 'global',
      })),
    })

    await page.goto('/dashboard/stars')

    await expect(page.getByRole('heading', { name: 'My Stars' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Star Skill 1', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Star Skill 13', exact: true })).not.toBeVisible()

    await page.getByRole('button', { name: 'Next' }).click()

    await expect(page.getByRole('heading', { name: 'Star Skill 13', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Star Skill 1', exact: true })).not.toBeVisible()
  })
})
