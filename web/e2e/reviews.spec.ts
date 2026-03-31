import { expect, test } from '@playwright/test'
import { reviewTask, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockReviewsPage } from './helpers/route-mocks'

test.describe('Review Center', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('switches between review status tabs for skill reviews', async ({ page }) => {
    await mockReviewsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      reviews: [
        reviewTask(601, 'PENDING', {
          namespace: 'team-alpha',
          skillSlug: 'pending-review',
          version: '1.1.0',
          submittedByName: 'Alice',
          submittedAt: '2026-03-30T00:00:00Z',
        }),
        reviewTask(602, 'APPROVED', {
          namespace: 'global',
          skillSlug: 'approved-review',
          version: '1.0.0',
          submittedByName: 'Bob',
          reviewedByName: 'Reviewer A',
          reviewedAt: '2026-03-29T00:00:00Z',
        }),
        reviewTask(603, 'REJECTED', {
          namespace: 'team-beta',
          skillSlug: 'rejected-review',
          version: '2.0.0',
          submittedByName: 'Carol',
          reviewedByName: 'Reviewer B',
          reviewedAt: '2026-03-28T00:00:00Z',
        }),
      ],
    })

    await page.goto('/dashboard/reviews')

    await expect(page.getByRole('heading', { name: 'Review Center' })).toBeVisible()
    await expect(page.getByText('team-alpha/pending-review')).toBeVisible()

    await page.getByRole('button', { name: 'Approved', exact: true }).click({ force: true })
    await expect(page.getByText('global/approved-review')).toBeVisible()
    await expect(page.getByText('Reviewer A')).toBeVisible()

    await page.getByRole('button', { name: 'Rejected', exact: true }).click({ force: true })
    await expect(page.getByText('team-beta/rejected-review')).toBeVisible()
    await expect(page.getByText('Reviewer B')).toBeVisible()
  })

  test('changes time ordering for pending reviews', async ({ page }) => {
    await mockReviewsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      reviews: [
        reviewTask(604, 'PENDING', {
          namespace: 'team-alpha',
          skillSlug: 'older-review',
          version: '1.0.0',
          submittedAt: '2026-03-20T00:00:00Z',
        }),
        reviewTask(605, 'PENDING', {
          namespace: 'team-alpha',
          skillSlug: 'newer-review',
          version: '2.0.0',
          submittedAt: '2026-03-31T00:00:00Z',
        }),
      ],
    })

    await page.goto('/dashboard/reviews')

    const rows = page.getByRole('row')
    await expect(rows.nth(1)).toContainText('team-alpha/newer-review')

    await page.getByRole('combobox').click()
    await page.getByRole('option', { name: 'Oldest first' }).click()

    await expect(rows.nth(1)).toContainText('team-alpha/older-review')
  })
})
