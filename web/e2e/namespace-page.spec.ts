import { expect, test } from '@playwright/test'
import { namespace, skill } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockNamespacePage } from './helpers/route-mocks'

test.describe('Namespace Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders namespace metadata and skills', async ({ page }) => {
    await mockNamespacePage(page, {
      namespaceData: namespace('team-alpha', 'Team Alpha', {
        description: 'Namespace for shared automation skills',
      }),
      skills: [
        skill(1, 'Workspace Assistant', { namespace: 'team-alpha' }),
        skill(2, 'Release Notifier', { namespace: 'team-alpha' }),
      ],
    })

    await page.goto('/space/team-alpha')

    await expect(page.getByRole('heading', { name: 'Team Alpha' })).toBeVisible()
    await expect(page.getByText('Namespace for shared automation skills')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Workspace Assistant' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Release Notifier' })).toBeVisible()
  })

  test('renders an empty state when the namespace has no skills', async ({ page }) => {
    await mockNamespacePage(page, {
      namespaceData: namespace('team-alpha', 'Team Alpha'),
      skills: [],
    })

    await page.goto('/space/team-alpha')

    await expect(page.getByRole('heading', { name: 'No skills' })).toBeVisible()
  })

  test('renders a not found state when the namespace does not exist', async ({ page }) => {
    await mockNamespacePage(page, {
      namespaceData: null,
    })

    await page.goto('/space/team-alpha')

    await expect(page.getByText('Namespace not found')).toBeVisible()
  })
})
