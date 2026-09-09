import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Landing Quick Start CLI Tab (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders three peer tabs and exposes the CLI install command', async ({ page }) => {
    await page.goto('/')

    const agentTab = page.getByRole('button', { name: 'I am Agent', exact: true })
    const humanTab = page.getByRole('button', { name: 'I am Human', exact: true })
    const cliTab = page.getByRole('button', { name: 'CLI', exact: true })

    await expect(agentTab).toBeVisible()
    await expect(humanTab).toBeVisible()
    await expect(cliTab).toBeVisible()

    await expect(agentTab).toHaveAttribute('aria-pressed', 'true')

    await cliTab.click()
    await expect(cliTab).toHaveAttribute('aria-pressed', 'true')
    await expect(agentTab).toHaveAttribute('aria-pressed', 'false')
    await expect(humanTab).toHaveAttribute('aria-pressed', 'false')

    await expect(
      page.getByText('Install the SkillHub CLI locally to run skillhub install for skills.'),
    ).toBeVisible()
    await expect(page.getByText('npm i -g @astron-team/skillhub', { exact: true })).toBeVisible()
  })

  test('agent and human tabs expose the current SkillHub guidance', async ({ page }) => {
    await page.goto('/')

    const agentTab = page.getByRole('button', { name: 'I am Agent', exact: true })
    const humanTab = page.getByRole('button', { name: 'I am Human', exact: true })

    await expect(
      page.getByText(
        'Connect SkillHub using http://127.0.0.1:3000/install/skillhub.md',
        { exact: true },
      ),
    ).toBeVisible()
    const guideResponse = await page.request.get('/install/skillhub.md')
    expect(guideResponse.status()).toBe(200)
    const guide = await guideResponse.text()
    expect(guide).toContain('http://127.0.0.1:3000')
    expect(guideResponse.headers()['cache-control']).toContain('no-cache')
    const legacyGuideResponse = await page.request.get('/registry/skill.md')
    expect(legacyGuideResponse.status()).toBe(200)
    expect(await legacyGuideResponse.text()).toBe(guide)
    const hostileHostResponse = await page.request.get('/install/skillhub.md', {
      headers: { Host: 'attacker.example' },
    })
    expect(hostileHostResponse.status()).toBe(403)
    const extensionHostResponse = await page.request.get('/install/skillhub.md', {
      headers: { Host: 'chrome-extension:evil;echo_injected' },
    })
    expect(extensionHostResponse.status()).toBe(400)

    await humanTab.click()
    await expect(humanTab).toHaveAttribute('aria-pressed', 'true')
    await expect(
      page.getByText(
        'npx @astron-team/skillhub@latest search <keyword> --registry http://127.0.0.1:3000',
        { exact: true },
      ),
    ).toBeVisible()

    await agentTab.click()
    await expect(agentTab).toHaveAttribute('aria-pressed', 'true')
    await expect(
      page.getByText(
        'Connect SkillHub using http://127.0.0.1:3000/install/skillhub.md',
        { exact: true },
      ),
    ).toBeVisible()
  })
})
