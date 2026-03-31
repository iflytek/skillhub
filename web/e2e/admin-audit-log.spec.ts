import { expect, test } from '@playwright/test'
import { auditLogItem, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockAuditLogPage } from './helpers/route-mocks'

test.describe('Admin Audit Log Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('applies the search index rebuild quick filter and clears filters', async ({ page }) => {
    await mockAuditLogPage(page, {
      currentUser: user('auditor-user', 'Auditor User', 'AUDITOR', { platformRoles: ['AUDITOR'] }),
      logs: [
        auditLogItem(1, 'REBUILD_SEARCH_INDEX', {
          resourceType: 'SEARCH_INDEX',
          details: 'Search index rebuild completed',
        }),
        auditLogItem(2, 'PROMOTION_APPROVE', {
          resourceType: 'PROMOTION',
          details: 'Promotion approved',
        }),
      ],
    })

    await page.goto('/admin/audit-log')

    await expect(page.getByRole('heading', { name: 'Audit Log' })).toBeVisible()
    await page.getByRole('button', { name: 'Search index rebuilds only' }).click()

    await expect(page.getByText('REBUILD_SEARCH_INDEX')).toBeVisible()
    await expect(page.getByText('PROMOTION_APPROVE')).not.toBeVisible()

    await page.getByRole('button', { name: 'Clear filters' }).click()
    await expect(page.getByText('PROMOTION_APPROVE')).toBeVisible()
  })

  test('filters by user id and paginates audit log results', async ({ page }) => {
    await mockAuditLogPage(page, {
      currentUser: user('auditor-user', 'Auditor User', 'AUDITOR', { platformRoles: ['AUDITOR'] }),
      logs: [
        ...Array.from({ length: 20 }, (_, index) => auditLogItem(index + 1, 'REVIEW_SUBMIT', {
          userId: 'alice',
          username: 'Alice',
          details: `Audit row ${index + 1}`,
        })),
        auditLogItem(21, 'REVIEW_SUBMIT', {
          userId: 'alice',
          username: 'Alice',
          details: 'Audit row 21',
        }),
        auditLogItem(22, 'PROMOTION_APPROVE', {
          userId: 'bob',
          username: 'Bob',
          details: 'Bob action',
        }),
      ],
    })

    await page.goto('/admin/audit-log')

    await page.getByPlaceholder('User ID...').fill('alice')
    await expect(page.getByText('Bob action')).not.toBeVisible()
    await expect(page.getByRole('cell', { name: 'Audit row 1', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Audit row 21', exact: true })).not.toBeVisible()

    await page.getByRole('button', { name: 'Next' }).click()
    await expect(page.getByRole('cell', { name: 'Audit row 21', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Audit row 1', exact: true })).not.toBeVisible()
  })
})
