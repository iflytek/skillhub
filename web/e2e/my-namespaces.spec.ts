import { expect, test } from '@playwright/test'
import { managedNamespace, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockMyNamespacesPage } from './helpers/route-mocks'

test.describe('My Namespaces Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates reserved slugs and creates a new namespace', async ({ page }) => {
    await mockMyNamespacesPage(page, {
      currentUser: user('admin-user', 'Admin User', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      namespaces: [],
    })

    await page.goto('/dashboard/namespaces')

    await expect(page.getByRole('heading', { name: 'My Namespaces' })).toBeVisible()
    await page.getByRole('button', { name: 'Create Namespace' }).first().click()

    await page.locator('#namespace-slug').fill('global')
    await page.locator('#namespace-display-name').fill('Platform Team')
    await page.getByRole('button', { name: 'Create Now' }).click()
    await expect(page.getByText('"global" is reserved. Please choose another slug.')).toBeVisible()

    await page.locator('#namespace-slug').fill('platform-team')
    await page.getByRole('button', { name: 'Create Now' }).click()

    await expect(page.getByText('Namespace created')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Platform Team' })).toBeVisible()
    await expect(page.getByText('@platform-team')).toBeVisible()
  })

  test('freezes, archives, and restores a team namespace', async ({ page }) => {
    await mockMyNamespacesPage(page, {
      currentUser: user('admin-user', 'Admin User', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      namespaces: [
        managedNamespace('team-alpha', 'Team Alpha', {
          id: 101,
          description: 'Primary team workspace',
          status: 'ACTIVE',
          canFreeze: true,
          canUnfreeze: false,
          canArchive: true,
          canRestore: false,
        }),
      ],
    })

    await page.goto('/dashboard/namespaces')

    await page.getByRole('button', { name: 'Freeze' }).click()
    await page.getByRole('button', { name: 'Freeze' }).last().click()
    await expect(page.getByText('Namespace frozen')).toBeVisible()
    await expect(page.getByText('Frozen', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Unfreeze' }).click()
    await page.getByRole('button', { name: 'Unfreeze' }).last().click()
    await expect(page.getByText('Namespace unfrozen')).toBeVisible()
    await expect(page.getByText('Active', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Archive', exact: true }).click()
    await page.getByRole('button', { name: 'Archive', exact: true }).last().click()
    await expect(page.getByText('Namespace archived')).toBeVisible()
    await expect(page.getByText('Archived', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Restore', exact: true }).click()
    await page.getByRole('button', { name: 'Restore', exact: true }).last().click()
    await expect(page.getByText('Namespace restored')).toBeVisible()
    await expect(page.getByText('Active', { exact: true })).toBeVisible()
  })
})
