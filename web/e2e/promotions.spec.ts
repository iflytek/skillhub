import { expect, test } from '@playwright/test'
import { promotionTask, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockPromotionsPage } from './helpers/route-mocks'

test.describe('Promotions Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('switches between promotion status tabs', async ({ page }) => {
    await mockPromotionsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      promotions: [
        promotionTask(901, 'PENDING', {
          sourceNamespace: 'team-alpha',
          sourceSkillSlug: 'pending-promotion',
          sourceVersion: '1.2.0',
        }),
        promotionTask(902, 'APPROVED', {
          sourceNamespace: 'global-ready',
          sourceSkillSlug: 'approved-promotion',
          sourceVersion: '1.0.0',
          reviewComment: 'Approved for global release',
        }),
        promotionTask(903, 'REJECTED', {
          sourceNamespace: 'team-beta',
          sourceSkillSlug: 'rejected-promotion',
          sourceVersion: '2.0.0',
          reviewComment: 'Needs more polish',
        }),
      ],
    })

    await page.goto('/dashboard/promotions')

    await expect(page.getByRole('heading', { name: 'Promotion Review' })).toBeVisible()
    await expect(page.getByText('team-alpha/pending-promotion')).toBeVisible()

    await page.getByRole('button', { name: 'Approved', exact: true }).click({ force: true })
    await expect(page.getByText('global-ready/approved-promotion')).toBeVisible()
    await expect(page.getByText('Approved for global release')).toBeVisible()

    await page.getByRole('button', { name: 'Rejected', exact: true }).click({ force: true })
    await expect(page.getByText('team-beta/rejected-promotion')).toBeVisible()
    await expect(page.getByText('Needs more polish')).toBeVisible()
  })

  test('approves a pending promotion and moves it into the approved tab', async ({ page }) => {
    await mockPromotionsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      promotions: [
        promotionTask(904, 'PENDING', {
          sourceNamespace: 'team-alpha',
          sourceSkillSlug: 'release-bot',
          sourceVersion: '3.0.0',
        }),
      ],
    })

    await page.goto('/dashboard/promotions')

    await page.getByPlaceholder('Review comment (optional)').fill('Ship it')
    await page.getByRole('button', { name: 'Approve', exact: true }).click()

    await expect(page.getByText('No promotion requests')).toBeVisible()

    await page.getByRole('button', { name: 'Approved', exact: true }).click({ force: true })
    await expect(page.getByText('team-alpha/release-bot')).toBeVisible()
    await expect(page.getByText('Ship it')).toBeVisible()
  })
})
