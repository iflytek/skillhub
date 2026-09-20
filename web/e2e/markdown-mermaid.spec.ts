import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Markdown Mermaid rendering (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('renders a valid Mermaid fence as an SVG diagram', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug, {
        name: `mermaid-valid-${Date.now().toString(36)}`,
        readmeBody: [
          '# Mermaid diagram',
          '',
          '```mermaid',
          'flowchart TD',
          '  A[Start] --> B[Done]',
          '```',
        ].join('\n'),
      })

      await page.goto(`/space/${encodeURIComponent(namespace.slug)}/${encodeURIComponent(skill.slug)}`)

      await expect(page.locator('[data-mermaid-diagram] svg')).toBeVisible({ timeout: 30_000 })
      await expect(page.locator('pre code.language-mermaid')).toHaveCount(0)
    } finally {
      await builder.cleanup()
    }
  })

  test('keeps invalid Mermaid source visible when rendering fails', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug, {
        name: `mermaid-invalid-${Date.now().toString(36)}`,
        readmeBody: [
          '# Invalid Mermaid diagram',
          '',
          '```mermaid',
          'this is not a Mermaid diagram',
          '```',
        ].join('\n'),
      })

      await page.goto(`/space/${encodeURIComponent(namespace.slug)}/${encodeURIComponent(skill.slug)}`)

      const source = page.locator('pre code.language-mermaid')
      await expect(source).toContainText('this is not a Mermaid diagram')
      await expect(page.locator('[data-mermaid-diagram]')).toHaveCount(0)
    } finally {
      await builder.cleanup()
    }
  })
})
