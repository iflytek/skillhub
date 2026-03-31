import { expect, test } from '@playwright/test'
import { adminUser, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockAdminUsersPage } from './helpers/route-mocks'

test.describe('Admin Users Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('filters users by search and status', async ({ page }) => {
    await mockAdminUsersPage(page, {
      currentUser: user('super-admin', 'Super Admin', 'SUPER_ADMIN', { platformRoles: ['SUPER_ADMIN'] }),
      users: [
        adminUser('pending-user', 'pending-user', 'PENDING', { platformRoles: ['USER'] }),
        adminUser('active-user', 'active-user', 'ACTIVE', { platformRoles: ['USER_ADMIN'] }),
        adminUser('disabled-user', 'disabled-user', 'DISABLED', { platformRoles: ['AUDITOR'] }),
      ],
    })

    await page.goto('/admin/users')

    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible()
    await page.locator('#admin-user-search').fill('pending')
    await page.getByRole('button', { name: 'Search' }).click()

    await expect(page.getByRole('row', { name: /pending-user/ })).toBeVisible()
    await expect(page.getByRole('row', { name: /active-user/ })).not.toBeVisible()

    await page.getByRole('button', { name: 'Clear' }).click()
    await page.getByRole('combobox').click()
    await page.getByRole('option', { name: 'Disabled' }).click()

    await expect(page.getByRole('row', { name: /disabled-user/ })).toBeVisible()
    await expect(page.getByRole('row', { name: /pending-user/ })).not.toBeVisible()
  })

  test('approves pending users, changes role, and toggles activation status', async ({ page }) => {
    await mockAdminUsersPage(page, {
      currentUser: user('super-admin', 'Super Admin', 'SUPER_ADMIN', { platformRoles: ['SUPER_ADMIN'] }),
      users: [
        adminUser('pending-user', 'pending-user', 'PENDING', { platformRoles: ['USER'] }),
        adminUser('active-user', 'active-user', 'ACTIVE', { platformRoles: ['USER'] }),
      ],
    })

    await page.goto('/admin/users')

    const pendingRow = page.getByRole('row', { name: /pending-user/ })
    await pendingRow.getByRole('button', { name: 'Approve' }).click()
    await expect(pendingRow).toContainText('Active')

    const activeRow = page.getByRole('row', { name: /active-user/ })
    await activeRow.getByRole('button', { name: 'Change Role' }).click()
    await expect(page.getByText('Change User Role')).toBeVisible()
    await page.locator('#role').click()
    await page.getByRole('option', { name: 'Super Admin' }).click()
    await page.getByRole('button', { name: 'Confirm' }).click()
    await expect(activeRow).toContainText('SUPER_ADMIN')

    await activeRow.getByRole('button', { name: 'Disable' }).click()
    await expect(page.getByText('Confirm Action')).toBeVisible()
    await page.getByRole('button', { name: 'Confirm' }).last().click()
    await expect(activeRow).toContainText('Disabled')

    await activeRow.getByRole('button', { name: 'Enable' }).click()
    await page.getByRole('button', { name: 'Confirm' }).last().click()
    await expect(activeRow).toContainText('Active')
  })
})
