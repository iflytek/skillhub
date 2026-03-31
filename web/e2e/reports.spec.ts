import { expect, test } from '@playwright/test'
import { skillReport, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockReportsPage } from './helpers/route-mocks'

test.describe('Reports Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('resolves and archives a pending report from the moderation queue', async ({ page }) => {
    await mockReportsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      reports: [
        skillReport(1001, 'PENDING', {
          namespace: 'team-alpha',
          skillSlug: 'unsafe-skill',
          skillDisplayName: 'Unsafe Skill',
          reporterId: 'alice',
          reason: 'Unsafe instructions',
          details: 'Contains destructive commands',
        }),
      ],
    })

    await page.goto('/dashboard/reports')

    await expect(page.getByRole('heading', { name: 'Skill Reports' })).toBeVisible()
    await expect(page.getByText('team-alpha/unsafe-skill')).toBeVisible()

    await page.getByRole('button', { name: 'Resolve & archive' }).click()
    await expect(page.getByText('Resolve report')).toBeVisible()
    await page.getByRole('button', { name: 'Resolve & archive' }).last().click()

    await expect(page.getByText('Report resolved and skill archived')).toBeVisible()
    await expect(page.getByText('No reports')).toBeVisible()

    await page.getByRole('button', { name: 'Resolved', exact: true }).click({ force: true })
    await expect(page.getByText('team-alpha/unsafe-skill')).toBeVisible()
    await expect(page.getByText('Handled by: skill-admin')).toBeVisible()
  })

  test('dismisses a pending report and shows historical lanes', async ({ page }) => {
    await mockReportsPage(page, {
      currentUser: user('skill-admin', 'Skill Admin', 'SKILL_ADMIN', { platformRoles: ['SKILL_ADMIN'] }),
      reports: [
        skillReport(1002, 'PENDING', {
          namespace: 'team-beta',
          skillSlug: 'noisy-skill',
          skillDisplayName: 'Noisy Skill',
          reporterId: 'bob',
        }),
        skillReport(1003, 'RESOLVED', {
          namespace: 'team-gamma',
          skillSlug: 'resolved-skill',
          skillDisplayName: 'Resolved Skill',
          handledBy: 'reviewer-a',
        }),
      ],
    })

    await page.goto('/dashboard/reports')

    await page.getByRole('button', { name: 'Dismiss', exact: true }).click()
    await expect(page.getByText('Dismiss report')).toBeVisible()
    await page.getByRole('button', { name: 'Dismiss', exact: true }).last().click()

    await expect(page.getByText('Report dismissed')).toBeVisible()

    await page.getByRole('button', { name: 'Dismissed', exact: true }).click({ force: true })
    await expect(page.getByText('team-beta/noisy-skill')).toBeVisible()
    await expect(page.getByText('Handled by: skill-admin')).toBeVisible()

    await page.getByRole('button', { name: 'Resolved', exact: true }).click({ force: true })
    await expect(page.getByText('team-gamma/resolved-skill')).toBeVisible()
    await expect(page.getByText('Handled by: reviewer-a')).toBeVisible()
  })
})
