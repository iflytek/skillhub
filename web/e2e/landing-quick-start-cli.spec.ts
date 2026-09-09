import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Landing access methods (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders three access methods and exposes current CLI commands', async ({ page }) => {
    await page.goto('/')

    const agentMode = page.getByRole('button', { name: /Agent integration/ })
    const cliMode = page.getByRole('button', { name: /\bCLI\b/ })
    const webMode = page.getByRole('button', { name: /Web interface/ })

    await expect(agentMode).toBeVisible({ timeout: 15_000 })
    await expect(cliMode).toBeVisible()
    await expect(webMode).toBeVisible()
    await expect(agentMode).toHaveAttribute('aria-pressed', 'true')

    await cliMode.click()
    await expect(cliMode).toHaveAttribute('aria-pressed', 'true')
    await expect(agentMode).toHaveAttribute('aria-pressed', 'false')
    await expect(webMode).toHaveAttribute('aria-pressed', 'false')

    await expect(page.getByText('npx -y @astron-team/skillhub@0.1.12 --version', { exact: true })).toBeVisible()
    await expect(page.getByText(/npx -y @astron-team\/skillhub@0\.1\.12 search weather/)).toBeVisible()
    await expect(page.getByText(/npx -y @astron-team\/skillhub@0\.1\.12 install @global\/weather/)).toBeVisible()
    await expect(page.getByRole('link', { name: 'CLI docs' })).toHaveAttribute(
      'href',
      'https://github.com/iflytek/skillhub/tree/main/cli',
    )
  })

  test('agent views expose Registry configuration and implicit discovery', async ({ page }) => {
    await page.goto('/')

    const registryTab = page.getByRole('tab', { name: 'Registry setup' })
    const discoveryTab = page.getByRole('tab', { name: 'Implicit discovery' })

    await expect(registryTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText(/registry\/skill\.md/).first()).toBeVisible()

    await discoveryTab.click()
    await expect(discoveryTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText('Search SkillHub Registry')).toBeVisible()
    await expect(page.getByText('Match @global/weather · v1.3.0')).toBeVisible()

    const guideResponse = await page.request.get('/registry/skill.md')
    expect(guideResponse.status()).toBe(200)
    const guide = await guideResponse.text()
    expect(guide).toContain('name: skillhub-cli')
    expect(guide).toContain('removing the trailing `/registry/skill.md`')
    expect(guideResponse.headers()['cache-control']).toContain('no-cache')
    const hostileHostResponse = await page.request.get('/registry/skill.md', {
      headers: { Host: 'attacker.example' },
    })
    expect(hostileHostResponse.status()).toBe(403)
    const extensionHostResponse = await page.request.get('/registry/skill.md', {
      headers: { Host: 'chrome-extension:evil;echo_injected' },
    })
    expect(extensionHostResponse.status()).toBe(400)
  })
})
