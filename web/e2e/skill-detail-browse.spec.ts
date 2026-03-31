import { expect, test } from '@playwright/test'
import { skillDetail, skillFile, skillVersion } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockSkillDetailPage } from './helpers/route-mocks'

test.describe('Skill Detail Browse', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders overview, files, versions, and file preview for an authenticated user', async ({ page }) => {
    await mockSkillDetailPage(page, {
      namespace: 'team-alpha',
      slug: 'test-skill',
      detail: skillDetail(21, 'Test Skill', {
        namespace: 'team-alpha',
        slug: 'test-skill',
        summary: 'A skill for test coverage',
        ownerDisplayName: 'Platform Team',
      }),
      versions: [
        skillVersion(210, '1.0.0', { changelog: 'Initial GA release' }),
        skillVersion(211, '0.9.0', { changelog: 'Beta preview', publishedAt: '2026-03-18T00:00:00Z' }),
      ],
      files: [
        skillFile('README.md', { fileSize: 256 }),
        skillFile('guide.txt', { fileSize: 128 }),
      ],
      fileContents: {
        'README.md': '# Test Skill\n\nA stable overview for the package.',
        'guide.txt': 'Run `skillhub install team-alpha/test-skill`',
      },
    })

    await page.goto('/space/team-alpha/test-skill')

    await expect(page.getByRole('heading', { name: 'Test Skill' }).first()).toBeVisible()
    await expect(page.getByText('A skill for test coverage').first()).toBeVisible()
    await expect(page.getByText('By Platform Team').first()).toBeVisible()
    await expect(page.getByText('A stable overview for the package.').first()).toBeVisible()

    await page.getByRole('button', { name: 'Files' }).first().click()
    await expect(page.getByText('README.md').first()).toBeVisible()
    await page.getByText('guide.txt').first().click()

    await expect(page.getByRole('button', { name: 'Download guide.txt' })).toBeVisible()
    await expect(page.getByText('Run `skillhub install team-alpha/test-skill`')).toBeVisible()
    await page.getByRole('button', { name: 'Close' }).click()

    await page.getByRole('button', { name: 'Versions' }).first().click()
    await expect(page.getByText('v1.0.0').first()).toBeVisible()
    await expect(page.getByText('Initial GA release')).toBeVisible()
    await expect(page.getByText('v0.9.0').first()).toBeVisible()
  })

  test('renders documentation and file fallbacks when package assets are missing', async ({ page }) => {
    await mockSkillDetailPage(page, {
      namespace: 'team-alpha',
      slug: 'empty-skill',
      detail: skillDetail(22, 'Empty Skill', {
        namespace: 'team-alpha',
        slug: 'empty-skill',
        headlineVersion: { id: 220, version: '1.0.0', status: 'PUBLISHED' },
        publishedVersion: { id: 220, version: '1.0.0', status: 'PUBLISHED' },
      }),
      files: [],
      fileContents: {},
    })

    await page.goto('/space/team-alpha/empty-skill')

    await expect(page.getByText('No package documentation')).toBeVisible()
    await page.getByRole('button', { name: 'Files' }).first().click()
    await expect(page.getByText('No files')).toBeVisible()
  })

  test('renders the documentation unavailable state when the readme request fails', async ({ page }) => {
    await mockSkillDetailPage(page, {
      namespace: 'team-alpha',
      slug: 'broken-skill',
      failReadme: true,
    })

    await page.goto('/space/team-alpha/broken-skill')

    await expect(page.getByText('Documentation is unavailable')).toBeVisible()
  })
})
