import { expect, test } from '@playwright/test'
import { managedNamespace, reviewTask } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockNamespaceReviewsPage } from './helpers/route-mocks'

test.describe('Namespace Reviews Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('switches between review status tabs for a namespace', async ({ page }) => {
    await mockNamespaceReviewsPage(page, {
      namespaceData: managedNamespace('team-alpha', 'Team Alpha', {
        id: 101,
        status: 'ACTIVE',
        type: 'TEAM',
      }),
      reviews: [
        reviewTask(401, 'PENDING', { namespace: 'team-alpha', skillSlug: 'pending-review', version: '1.1.0' }),
        reviewTask(402, 'APPROVED', { namespace: 'team-alpha', skillSlug: 'approved-review', version: '1.0.0', reviewComment: 'Looks good' }),
        reviewTask(403, 'REJECTED', { namespace: 'team-alpha', skillSlug: 'rejected-review', version: '2.0.0', reviewComment: 'Needs more detail' }),
      ],
    })

    await page.goto('/dashboard/namespaces/team-alpha/reviews')

    await expect(page.getByRole('heading', { name: 'Namespace Reviews' })).toBeVisible()
    await expect(page.getByText('team-alpha/pending-review')).toBeVisible()

    await page.getByRole('button', { name: 'Approved', exact: true }).click({ force: true })
    await expect(page.getByText('team-alpha/approved-review')).toBeVisible()
    await expect(page.getByText('Looks good')).toBeVisible()

    await page.getByRole('button', { name: 'Rejected', exact: true }).click({ force: true })
    await expect(page.getByText('team-alpha/rejected-review')).toBeVisible()
    await expect(page.getByText('Needs more detail')).toBeVisible()
  })

  test('shows the archived namespace read-only banner', async ({ page }) => {
    await mockNamespaceReviewsPage(page, {
      namespaceData: managedNamespace('team-archive', 'Team Archive', {
        id: 102,
        status: 'ARCHIVED',
        type: 'TEAM',
      }),
      reviews: [
        reviewTask(404, 'PENDING', { namespace: 'team-archive', skillSlug: 'archived-review', version: '1.0.0' }),
      ],
    })

    await page.goto('/dashboard/namespaces/team-archive/reviews')

    await expect(page.getByText('This namespace is archived. You can still view review history, but cannot continue processing review tasks until it is restored.')).toBeVisible()
    await expect(page.getByText('team-archive/archived-review')).toBeVisible()
  })
})
