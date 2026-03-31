import { expect, test } from '@playwright/test'
import { skill, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockMySkillsPage } from './helpers/route-mocks'

test.describe('My Skills Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('filters pending-review skills and withdraws the pending upload', async ({ page }) => {
    await mockMySkillsPage(page, {
      mySkills: [
        skill(81, 'Pending Workflow', {
          namespace: 'team-alpha',
          status: 'ACTIVE',
          ownerPreviewVersion: { id: 811, version: '1.1.0', status: 'PENDING_REVIEW' },
          publishedVersion: { id: 810, version: '1.0.0', status: 'PUBLISHED' },
          headlineVersion: { id: 811, version: '1.1.0', status: 'PENDING_REVIEW' },
        }),
        skill(82, 'Published Helper', {
          namespace: 'team-alpha',
          status: 'ACTIVE',
          publishedVersion: { id: 820, version: '2.0.0', status: 'PUBLISHED' },
          headlineVersion: { id: 820, version: '2.0.0', status: 'PUBLISHED' },
        }),
      ],
    })

    await page.goto('/dashboard/skills')

    await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()
    await page.getByRole('button', { name: 'Pending Review', exact: true }).click({ force: true })

    await expect(page.getByRole('heading', { name: 'Pending Workflow' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Published Helper' })).not.toBeVisible()

    await page.getByRole('button', { name: 'Withdraw Review' }).click()
    await page.getByRole('button', { name: 'Withdraw Review' }).last().click()

    await expect(page.getByText('Upload withdrawn')).toBeVisible()
    await expect(page.getByText('There are no skills waiting for review right now.')).toBeVisible()
  })

  test('archives a published skill and restores it from the archived filter', async ({ page }) => {
    await mockMySkillsPage(page, {
      mySkills: [
        skill(83, 'Published Helper', {
          namespace: 'team-alpha',
          status: 'ACTIVE',
          publishedVersion: { id: 830, version: '2.0.0', status: 'PUBLISHED' },
          headlineVersion: { id: 830, version: '2.0.0', status: 'PUBLISHED' },
        }),
      ],
    })

    await page.goto('/dashboard/skills')

    await page.getByRole('button', { name: 'Archive', exact: true }).click()
    await page.getByRole('button', { name: 'Archive', exact: true }).last().click()

    await expect(page.getByText('Skill archived')).toBeVisible()

    await page.getByRole('button', { name: 'Archived', exact: true }).click({ force: true })
    await expect(page.getByRole('heading', { name: 'Published Helper' })).toBeVisible()

    await page.getByRole('button', { name: 'Restore' }).click()
    await page.getByRole('button', { name: 'Restore' }).last().click()

    await expect(page.getByText('Skill restored')).toBeVisible()
    await expect(page.getByText('There are no archived skills right now.')).toBeVisible()
  })

  test('shows a promotion conflict when the skill is already queued for global promotion', async ({ page }) => {
    await mockMySkillsPage(page, {
      currentUser: user('local-user', 'Local User', 'USER', { platformRoles: ['USER'] }),
      mySkills: [
        skill(84, 'Published Helper', {
          namespace: 'team-alpha',
          status: 'ACTIVE',
          canSubmitPromotion: true,
          publishedVersion: { id: 840, version: '2.0.0', status: 'PUBLISHED' },
          headlineVersion: { id: 840, version: '2.0.0', status: 'PUBLISHED' },
        }),
      ],
      promotionError: {
        status: 409,
        msg: 'promotion.duplicate_pending',
      },
    })

    await page.goto('/dashboard/skills')

    await page.getByRole('button', { name: 'Promote to Global' }).click()
    await page.getByRole('button', { name: 'Promote to Global' }).last().click()

    await expect(page.getByText('Promotion already pending')).toBeVisible()
    await expect(page.getByText('This version already has a pending promotion request.')).toBeVisible()
  })
})
