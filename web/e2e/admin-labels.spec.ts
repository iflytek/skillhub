import { expect, test } from '@playwright/test'
import { labelDefinition, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockAdminLabelsPage } from './helpers/route-mocks'

test.describe('Admin Labels Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates label slug format and creates a new label definition', async ({ page }) => {
    await mockAdminLabelsPage(page, {
      currentUser: user('super-admin', 'Super Admin', 'SUPER_ADMIN', { platformRoles: ['SUPER_ADMIN'] }),
      definitions: [
        labelDefinition('official', 'Official', { sortOrder: 0 }),
        labelDefinition('featured', 'Featured', { sortOrder: 1 }),
      ],
    })

    await page.goto('/admin/labels')

    await expect(page.getByRole('heading', { name: 'Label Management' })).toBeVisible()
    await page.getByRole('button', { name: 'Create Label' }).click()

    await page.locator('#label-slug').fill('bad--slug')
    await page.locator('input[placeholder="Locale, e.g. en or zh-CN"]').fill('en')
    await page.locator('input[placeholder="Display name"]').fill('Security Audit')
    await page.getByRole('button', { name: 'Create Label' }).last().click()

    await expect(page.getByText('Use lowercase letters, numbers, and single hyphens only. The slug must start and end with a letter or number.')).toBeVisible()

    await page.locator('#label-slug').fill('security-audit')
    await page.getByRole('button', { name: 'Create Label' }).last().click()

    await expect(page.getByRole('row', { name: /security-audit/ })).toBeVisible()
    await expect(page.getByText('Security Audit')).toBeVisible()
  })

  test('edits visibility, reorders, and deletes label definitions', async ({ page }) => {
    await mockAdminLabelsPage(page, {
      currentUser: user('super-admin', 'Super Admin', 'SUPER_ADMIN', { platformRoles: ['SUPER_ADMIN'] }),
      definitions: [
        labelDefinition('official', 'Official', { sortOrder: 0, visibleInFilter: true }),
        labelDefinition('internal', 'Internal', {
          sortOrder: 1,
          visibleInFilter: false,
          type: 'PRIVILEGED',
        }),
      ],
    })

    await page.goto('/admin/labels')

    const officialRow = page.getByRole('row', { name: /official/ })
    await officialRow.getByRole('button', { name: 'Edit' }).click()
    await expect(page.getByText('Edit Label')).toBeVisible()
    await page.locator('#label-visibility').click()
    await page.getByRole('option', { name: 'Hidden from filters' }).click()
    await page.getByRole('button', { name: 'Save Changes' }).click()

    await expect(officialRow).toContainText('Hidden from filters')

    await officialRow.getByRole('button', { name: 'Move Down' }).click()
    await expect(page.getByText('Sort order updated')).toBeVisible()

    const internalRow = page.getByRole('row', { name: /internal/ })
    await internalRow.getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByRole('heading', { name: 'Delete Label' })).toBeVisible()
    await page.getByRole('button', { name: 'Delete' }).last().click()

    await page.reload()
    await expect(page.getByRole('heading', { name: 'Label Management' })).toBeVisible()
    await expect(page.getByRole('row', { name: /internal/ })).not.toBeVisible()
  })
})
