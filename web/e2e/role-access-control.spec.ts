import { expect, test } from '@playwright/test'
import { user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import {
  mockAdminLabelsPage,
  mockAdminUsersPage,
  mockAuditLogPage,
  mockDashboardPage,
  mockReportsPage,
} from './helpers/route-mocks'

test.describe('Role Access Control', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('returns basic users to the dashboard when they open the review center', async ({ page }) => {
    await mockDashboardPage(page, {
      currentUser: user('basic-user', 'Basic User', 'USER', { platformRoles: ['USER'] }),
    })

    await page.goto('/dashboard')
    await expect(page.getByText('Account Information')).toBeVisible()

    await page.goto('/dashboard/reports')

    await expect(page).toHaveURL('/dashboard')
    await expect(page.getByText('Account Information')).toBeVisible()
  })

  test('allows user admins to access the user management route', async ({ page }) => {
    await mockAdminUsersPage(page, {
      currentUser: user('user-admin', 'User Admin', 'USER_ADMIN', { platformRoles: ['USER_ADMIN'] }),
    })

    await page.goto('/admin/users')

    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible()
  })

  test('returns user admins to the previous admin route when they open the super-admin labels page', async ({ page }) => {
    await mockAdminUsersPage(page, {
      currentUser: user('user-admin', 'User Admin', 'USER_ADMIN', { platformRoles: ['USER_ADMIN'] }),
    })

    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible()

    await page.goto('/admin/labels')

    await expect(page).toHaveURL('/admin/users')
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible()
  })

  test('allows auditors to access the audit log route', async ({ page }) => {
    await mockAuditLogPage(page, {
      currentUser: user('auditor-user', 'Auditor User', 'AUDITOR', { platformRoles: ['AUDITOR'] }),
    })

    await page.goto('/admin/audit-log')

    await expect(page.getByRole('heading', { name: 'Audit Log' })).toBeVisible()
  })

  test('allows skill admins to access the reports route', async ({ page }) => {
    await mockReportsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
    })

    await page.goto('/dashboard/reports')

    await expect(page.getByRole('heading', { name: 'Skill Reports' })).toBeVisible()
  })

  test('allows super admins to access the labels route', async ({ page }) => {
    await mockAdminLabelsPage(page, {
      currentUser: user('super-admin', 'Super Admin', 'SUPER_ADMIN', { platformRoles: ['SUPER_ADMIN'] }),
    })

    await page.goto('/admin/labels')

    await expect(page.getByRole('heading', { name: 'Label Management' })).toBeVisible()
  })
})
