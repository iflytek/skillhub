import { expect, test } from '@playwright/test'
import type { SkillSuite } from '../src/api/types'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'

// Real session and production page; controlled Suite data isolates the layout regression.
const suite: SkillSuite = {
  id: 7,
  versionId: 70,
  namespace: 'layout',
  slug: 'suite-layout',
  displayName: 'Suite layout acceptance',
  createdBy: 'layout-owner',
  createdAt: '2026-11-13T00:00:00Z',
  summary: 'Responsive header actions',
  overview: 'Layout regression fixture',
  version: '1.0.0',
  status: 'PUBLISHED',
  visibility: 'PUBLIC',
  suiteStatus: 'ACTIVE',
  hidden: false,
  allowedActions: ['CREATE_VERSION'],
  available: true,
  members: [],
}

for (const theme of ['light', 'dark']) {
  for (const width of [320, 390, 1280]) {
    test(`Suite header actions stay inside the viewport: ${theme}/${width}`, async ({ page }, testInfo) => {
      await setEnglishLocale(page)
      await registerSession(page, testInfo)
      await page.addInitScript(value => localStorage.setItem('skillhub-theme', value), theme)
      await page.setViewportSize({ width, height: 900 })
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await page.route('**/api/web/suites/layout/suite-layout*', route => route.fulfill({
        json: { code: 0, data: suite },
      }))
      await page.route('**/api/web/suites/layout/suite-layout/versions', route => route.fulfill({
        json: { code: 0, data: [] },
      }))
      await page.goto('/dashboard/suites/layout/suite-layout?version=1.0.0&tab=members')
      const create = page.getByRole('button', { name: 'Create new version', exact: true })
      const preview = page.getByRole('button', { name: 'Preview marketplace page', exact: true })
      await expect(create).toBeVisible()
      await expect(preview).toBeVisible()
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      for (const action of [create, preview]) {
        const bounds = await action.boundingBox()
        expect(bounds).not.toBeNull()
        expect(bounds!.x).toBeGreaterThanOrEqual(0)
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width)
      }
      await create.focus()
      await page.keyboard.press('Tab')
      await expect(preview).toBeFocused()
      await page.keyboard.press('Enter')
      await expect(page).toHaveURL(url => url.pathname === '/suite/layout/suite-layout'
        && url.searchParams.get('version') === '1.0.0')
    })
  }
}
