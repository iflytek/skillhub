import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import { skill } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockPublishPage } from './helpers/route-mocks'

const sampleSkillZip = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'fixtures/sample-skill.zip')

test.describe('Publish Flow', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('publishes a package and redirects to my skills', async ({ page }) => {
    await mockPublishPage(page, {
      mySkills: [
        skill(71, 'Release Bot', {
          namespace: 'team-alpha',
          slug: 'release-bot',
          status: 'ACTIVE',
          publishedVersion: { id: 710, version: '1.0.0', status: 'PUBLISHED' },
          headlineVersion: { id: 710, version: '1.0.0', status: 'PUBLISHED' },
        }),
      ],
      publishResult: {
        skillId: 71,
        namespace: 'team-alpha',
        slug: 'release-bot',
        version: '1.0.0',
        status: 'PENDING_REVIEW',
        fileCount: 4,
        totalSize: 4096,
      },
    })

    await page.goto('/dashboard/publish')

    await expect(page.getByRole('heading', { name: 'Publish Skill' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Confirm Publish' })).toBeDisabled()

    await page.locator('#namespace').click()
    await page.getByRole('option', { name: 'Team Alpha (@team-alpha)' }).click()
    await page.locator('input[type="file"]').setInputFiles(sampleSkillZip)

    await expect(page.getByText(/sample-skill\.zip/)).toBeVisible()
    await page.getByRole('button', { name: 'Confirm Publish' }).click()

    await expect(page).toHaveURL('/dashboard/skills')
    await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Release Bot' })).toBeVisible()
  })

  test('surfaces duplicate-version publish errors', async ({ page }) => {
    await mockPublishPage(page, {
      publishError: {
        status: 409,
        msg: 'Version already exists',
      },
    })

    await page.goto('/dashboard/publish')

    await page.locator('#namespace').click()
    await page.getByRole('option', { name: 'Team Alpha (@team-alpha)' }).click()
    await page.locator('input[type="file"]').setInputFiles(sampleSkillZip)
    await page.getByRole('button', { name: 'Confirm Publish' }).click()

    await expect(page.getByText('Version already exists')).toBeVisible()
    await expect(page.getByText('This skill version has already been published. Update the version in SKILL.md, rebuild the package, and upload it again.')).toBeVisible()
    await expect(page).toHaveURL('/dashboard/publish')
  })
})
