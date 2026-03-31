import { expect, test } from '@playwright/test'
import { reviewTask, reviewSkillDetail, skillFile, skillVersion, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockReviewDetailPage } from './helpers/route-mocks'

test.describe('Review Detail Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('expands the pending skill detail and previews a review-bound file', async ({ page }) => {
    await mockReviewDetailPage(page, 710, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      review: reviewTask(710, 'PENDING', {
        namespace: 'team-alpha',
        skillSlug: 'review-target',
        version: '1.2.0',
      }),
      reviewSkillDetail: reviewSkillDetail(410, 'Review Target', {
        skill: {
          id: 410,
          slug: 'review-target',
          displayName: 'Review Target',
          visibility: 'PUBLIC',
          status: 'ACTIVE',
          downloadCount: 12,
          starCount: 4,
          ratingCount: 1,
          hidden: false,
          namespace: 'team-alpha',
          canManageLifecycle: false,
          canSubmitPromotion: false,
          canInteract: false,
          canReport: false,
          resolutionMode: 'REVIEW_TASK',
        },
        versions: [
          skillVersion(411, '1.2.0', { status: 'PENDING_REVIEW', changelog: 'Pending moderation update' }),
          skillVersion(412, '1.1.0', { status: 'PUBLISHED', changelog: 'Previous approved release' }),
        ],
        files: [
          skillFile('README.md', { fileSize: 256 }),
          skillFile('guide.txt', { fileSize: 128 }),
        ],
        documentationPath: 'README.md',
        documentationContent: '# Review Target\n\nPending review package overview.',
        activeVersion: '1.2.0',
        downloadUrl: '/api/web/reviews/710/download',
      }),
      fileContents: {
        'README.md': '# Review Target\n\nPending review package overview.',
        'guide.txt': 'Step 1: Install dependencies',
      },
    })

    await page.goto('/dashboard/reviews/710')

    await expect(page.getByRole('heading', { name: 'Review Detail' })).toBeVisible()
    await page.getByRole('button', { name: 'Expand full overview' }).click()

    await expect(page.getByText('Pending review package overview.')).toBeVisible()

    await page.getByRole('button', { name: 'Files', exact: true }).click({ force: true })
    await page.locator('[data-review-skill-detail-panel]').getByText('guide.txt', { exact: true }).first().click()

    await expect(page.getByText('Step 1: Install dependencies')).toBeVisible()
    await expect(page.getByText('guide.txt').last()).toBeVisible()
  })

  test('approves a pending review and returns to the review queue', async ({ page }) => {
    await mockReviewDetailPage(page, 711, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      review: reviewTask(711, 'PENDING', {
        namespace: 'team-alpha',
        skillSlug: 'pending-approval',
        version: '2.0.0',
      }),
    })

    await page.goto('/dashboard/reviews/711')

    await page.locator('#comment').fill('Approved after manual inspection')
    await page.getByRole('button', { name: 'Approve', exact: true }).click()
    await expect(page.getByText('Approve Review')).toBeVisible()
    await page.getByRole('button', { name: 'Approve', exact: true }).last().click()

    await expect(page).toHaveURL('/dashboard/reviews')
    await expect(page.getByRole('heading', { name: 'Review Center' })).toBeVisible()
  })
})
