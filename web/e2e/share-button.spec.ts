import { expect, test } from '@playwright/test'
import { skillDetail } from './helpers/api-fixtures'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockSkillDetailPage } from './helpers/route-mocks'

test.describe('Skill Share Button', () => {
  test.beforeEach(async ({ page, context }) => {
    await setEnglishLocale(page)

    // Grant clipboard permissions
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  })

  test('copies share text to clipboard when share button is clicked', async ({ page }) => {
    await mockSkillDetailPage(page, {
      authenticated: true,
      namespace: 'global',
      slug: 'test-skill',
      detail: skillDetail(1, 'Test Skill', {
        summary: 'A useful test skill for sharing',
        namespace: 'global',
        slug: 'test-skill',
      }),
    })

    await page.goto('/space/global/test-skill')

    // Wait for skill detail page to load
    await expect(page.getByRole('heading', { name: /^Test Skill$/ }).first()).toBeVisible()

    // Find and click the share button
    const shareButton = page.getByTestId('share-skill-button')
    await expect(shareButton).toBeVisible()
    await shareButton.scrollIntoViewIfNeeded()
    await shareButton.click()

    // Verify button shows "Copied" state
    await expect(shareButton).toContainText(/Copied/i)

    // Verify clipboard content
    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    expect(clipboardText).toContain('test-skill')
    expect(clipboardText).toContain('http://localhost:3000/space/global/test-skill')
    expect(clipboardText.split('\n')).toHaveLength(3)
  })

  test('share text includes skill description when available', async ({ page }) => {
    await mockSkillDetailPage(page, {
      authenticated: true,
      namespace: 'global',
      slug: 'test-skill',
      detail: skillDetail(1, 'Test Skill', {
        summary: 'A useful test skill for sharing',
        namespace: 'global',
        slug: 'test-skill',
      }),
    })

    await page.goto('/space/global/test-skill')

    await expect(page.getByRole('heading', { name: /^Test Skill$/ }).first()).toBeVisible()

    const shareButton = page.getByTestId('share-skill-button')
    await expect(shareButton).toBeVisible()
    await shareButton.scrollIntoViewIfNeeded()
    await shareButton.click()

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    // Description is truncated to fit within 30 char limit (displayName + " - " + desc)
    expect(clipboardText).toContain('A useful test sk')
  })

  test('share button resets to normal state after 2 seconds', async ({ page }) => {
    await mockSkillDetailPage(page, {
      authenticated: true,
      namespace: 'global',
      slug: 'test-skill',
      detail: skillDetail(1, 'Test Skill', {
        summary: 'A useful test skill',
        namespace: 'global',
        slug: 'test-skill',
      }),
    })

    await page.goto('/space/global/test-skill')

    await expect(page.getByRole('heading', { name: /^Test Skill$/ }).first()).toBeVisible()

    const shareButton = page.getByTestId('share-skill-button')
    await expect(shareButton).toBeVisible()
    await shareButton.scrollIntoViewIfNeeded()
    await shareButton.click()

    // Should show "Copied" immediately
    await expect(shareButton).toContainText(/Copied/i)

    await expect.poll(async () => {
      return (await page.getByTestId('share-skill-button').textContent())?.trim()
    }, {
      timeout: 4000,
    }).toBe('Share')
  })

  test('formats namespaced skill correctly in share text', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])

    await mockSkillDetailPage(page, {
      authenticated: true,
      namespace: 'team-alpha',
      slug: 'namespaced-skill',
      detail: skillDetail(2, 'Namespaced Skill', {
        summary: 'Team skill',
        namespace: 'team-alpha',
        slug: 'namespaced-skill',
      }),
    })

    await page.goto('/space/team-alpha/namespaced-skill')

    await expect(page.getByRole('heading', { name: /^Namespaced Skill$/ }).first()).toBeVisible()

    const shareButton = page.getByTestId('share-skill-button')
    await expect(shareButton).toBeVisible()
    await shareButton.scrollIntoViewIfNeeded()
    await shareButton.click()

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
    expect(clipboardText).toContain('team-alpha/namespaced-skill')
    expect(clipboardText).toContain('http://localhost:3000/space/team-alpha/namespaced-skill')
  })
})
