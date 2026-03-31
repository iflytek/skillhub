import { expect, test } from '@playwright/test'
import { managedNamespace, namespaceCandidateUser, namespaceMember, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockNamespaceMembersPage } from './helpers/route-mocks'

test.describe('Namespace Members Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('searches candidates, adds a member, updates a role, and removes a member', async ({ page }) => {
    await mockNamespaceMembersPage(page, {
      currentUser: user('owner-user', 'Owner User', 'USER', { platformRoles: ['USER'] }),
      namespaceData: managedNamespace('team-alpha', 'Team Alpha', {
        id: 101,
        currentUserRole: 'OWNER',
        status: 'ACTIVE',
        type: 'TEAM',
      }),
      myNamespaces: [
        managedNamespace('team-alpha', 'Team Alpha', {
          id: 101,
          currentUserRole: 'OWNER',
          status: 'ACTIVE',
          type: 'TEAM',
        }),
      ],
      members: [
        namespaceMember(1, 'owner-user', 'OWNER'),
        namespaceMember(2, 'member-user', 'MEMBER'),
      ],
      candidates: [
        namespaceCandidateUser('new-user', 'New User', { email: 'new-user@example.com' }),
      ],
    })

    await page.goto('/dashboard/namespaces/team-alpha/members')

    await expect(page.getByRole('heading', { name: 'Member Management' })).toBeVisible()
    await page.getByRole('button', { name: 'Add Member' }).first().click()

    await page.locator('#member-search').fill('new')
    await page.getByRole('button', { name: 'Search' }).click()
    await expect(page.getByText('New User')).toBeVisible()

    await page.getByRole('button', { name: 'Use this user' }).click()
    await expect(page.locator('#member-user-id')).toHaveValue('new-user')
    await page.getByRole('button', { name: 'Add Member' }).last().click()

    await expect(page.getByText('Member added')).toBeVisible()
    const newUserRow = page.getByRole('row', { name: /new-user/ })
    await expect(newUserRow).toBeVisible()

    const memberRow = page.getByRole('row', { name: /member-user/ })
    await memberRow.getByRole('combobox').click()
    await page.getByRole('option', { name: 'ADMIN' }).click()
    await memberRow.getByRole('button', { name: 'Save role' }).click()

    await expect(page.getByText('Member role updated')).toBeVisible()
    await expect(memberRow.getByText('ADMIN')).toBeVisible()

    await newUserRow.getByRole('button', { name: 'Remove' }).click()
    await page.getByRole('button', { name: 'Remove' }).last().click()

    await expect(page.getByText('Member removed')).toBeVisible()
    await expect(page.getByRole('row', { name: /new-user/ })).not.toBeVisible()
  })

  test('shows read-only messaging for archived namespaces', async ({ page }) => {
    await mockNamespaceMembersPage(page, {
      currentUser: user('member-user', 'Member User', 'USER', { platformRoles: ['USER'] }),
      namespaceData: managedNamespace('team-archive', 'Team Archive', {
        id: 102,
        currentUserRole: 'MEMBER',
        status: 'ARCHIVED',
        type: 'TEAM',
      }),
      myNamespaces: [
        managedNamespace('team-archive', 'Team Archive', {
          id: 102,
          currentUserRole: 'MEMBER',
          status: 'ARCHIVED',
          type: 'TEAM',
        }),
      ],
      members: [
        namespaceMember(1, 'member-user', 'MEMBER'),
      ],
    })

    await page.goto('/dashboard/namespaces/team-archive/members')

    await expect(page.getByText('This namespace is archived. You can still view members, but cannot change membership until it is restored.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Add Member' })).toBeDisabled()
  })
})
