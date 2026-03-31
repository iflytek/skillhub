import { expect, test } from '@playwright/test'
import { skill, user } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockDashboardPage } from './helpers/route-mocks'

const baseTokens = [
  {
    id: 1,
    name: 'cli-token',
    tokenPrefix: 'sk_cli_123',
    createdAt: '2026-03-20T00:00:00Z',
    lastUsedAt: '2026-03-21T00:00:00Z',
    expiresAt: null,
  },
]

test.describe('Dashboard Shell', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders account summary and preview cards for a regular user', async ({ page }) => {
    await mockDashboardPage(page, {
      currentUser: user('local-user', 'Local User', 'USER', {
        oauthProvider: 'github',
      }),
      mySkills: [
        skill(41, 'Release Bot', { namespace: 'team-alpha' }),
        skill(42, 'Support Assistant', { namespace: 'team-alpha' }),
      ],
      tokens: baseTokens,
    })

    await page.goto('/dashboard')

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await expect(page.getByText('Account Information')).toBeVisible()
    await expect(page.getByRole('main').getByText('Local User')).toBeVisible()
    await expect(page.getByText('Logged in via github')).toBeVisible()
    await expect(page.getByText('USER', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'View API Tokens' })).toBeVisible()
    await expect(page.getByText('Release Bot')).toBeVisible()
    await expect(page.getByText('Support Assistant')).toBeVisible()
    await expect(page.getByText('Open Governance Center')).not.toBeVisible()
  })

  test('shows governance entry points for reviewers and admins', async ({ page }) => {
    await mockDashboardPage(page, {
      currentUser: user('review-admin', 'Review Admin', 'SKILL_ADMIN', {
        platformRoles: ['SKILL_ADMIN'],
        oauthProvider: 'github',
      }),
      mySkills: [skill(43, 'Governed Workflow', { namespace: 'global' })],
      tokens: baseTokens,
    })

    await page.goto('/dashboard')

    await expect(page.getByRole('link', { name: 'Open Governance Center' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'View skill reports' })).toBeVisible()
  })
})
