import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Skill Detail Relative Links (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('previews package files from overview relative links and reports missing files', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skillName = `relative-links-${Date.now().toString(36)}`
      const skill = await builder.publishSkill(namespace.slug, {
        name: skillName,
        readmeBody: [
          `# ${skillName}`,
          '',
          '[Usage](docs/usage.md)',
          '',
          '[Missing](docs/missing.md)',
        ].join('\n'),
        extraFiles: [
          {
            path: 'docs/usage.md',
            content: '# Usage\n\nThis is linked documentation.\n\n[Nested](nested.md)',
          },
          {
            path: 'docs/nested.md',
            content: '# Nested\n\nSecond-level linked documentation.',
          }
        ],
      })

      await page.goto(`/space/${encodeURIComponent(namespace.slug)}/${encodeURIComponent(skill.slug)}`)

      await expect(page).toHaveURL(new RegExp(`/space/${namespace.slug}/${skill.slug}$`))
      await expect(page.getByRole('link', { name: 'Usage' })).toBeVisible()
      await page.getByRole('link', { name: 'Usage' }).click()
      await expect(page.getByRole('dialog')).toContainText('usage.md')
      await expect(page.getByRole('dialog')).toContainText('This is linked documentation.')
      await expect(page.getByRole('dialog').getByRole('link', { name: 'Nested' })).toBeVisible()

      await page.getByRole('dialog').getByRole('link', { name: 'Nested' }).click()
      await expect(page.getByRole('dialog')).toContainText('nested.md')
      await expect(page.getByRole('dialog')).toContainText('Second-level linked documentation.')

      await page.getByRole('button', { name: 'Close' }).click()
      await expect(page.getByRole('dialog')).toBeHidden()

      await page.getByRole('link', { name: 'Missing' }).click()
      await expect(page).toHaveURL(new RegExp(`/space/${namespace.slug}/${skill.slug}$`))
      await expect(page.getByText('File not found')).toBeVisible()
      await expect(page.getByText('not included in the current skill version')).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
