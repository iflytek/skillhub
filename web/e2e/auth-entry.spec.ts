import { expect, test } from '@playwright/test'
import { mockAppShell } from './helpers/route-mocks'
import {
  mockLoginMutation,
  mockRegisterMutation,
  oauthOnlyMethods,
  setEnglishLocale,
} from './helpers/auth-fixtures'

test.describe('Auth Entry', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders login options, validates required fields, and preserves returnTo on register link', async ({ page }) => {
    await mockAppShell(page, {
      authenticated: false,
      authMethods: oauthOnlyMethods('/search?q=auth&sort=relevance&page=0&starredOnly=false'),
    })

    await page.goto('/login?returnTo=%2Fsearch%3Fq%3Dauth%26sort%3Drelevance%26page%3D0%26starredOnly%3Dfalse')

    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()

    await expect(page.getByRole('link', { name: 'Sign up now' })).toHaveAttribute(
      'href',
      '/register?returnTo=%2Fsearch%3Fq%3Dauth%26sort%3Drelevance%26page%3D0%26starredOnly%3Dfalse',
    )

    await page.getByRole('button', { name: 'OAuth' }).click()
    await expect(page.getByRole('button', { name: 'Login with GitHub' })).toBeVisible()
  })

  test('logs in and redirects to the requested public page', async ({ page }) => {
    await mockAppShell(page, {
      authenticated: false,
      authMethods: oauthOnlyMethods('/search?q=auth&sort=relevance&page=0&starredOnly=false'),
    })
    await mockLoginMutation(page)

    await page.route('**/api/web/skills?**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: { items: [], total: 0, page: 0, size: 12 },
          timestamp: '2026-03-31T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/login?returnTo=%2Fsearch%3Fq%3Dauth%26sort%3Drelevance%26page%3D0%26starredOnly%3Dfalse')

    await page.getByLabel('Username').fill('local-user')
    await page.locator('#password').fill('ChangeMe!2026')
    await page.getByRole('button', { name: 'Login' }).click()

    await expect(page).toHaveURL('/search?q=auth&sort=relevance&page=0&starredOnly=false')
  })

  test('registers and links back to login with the same returnTo', async ({ page }) => {
    await mockAppShell(page, {
      authenticated: false,
      authMethods: oauthOnlyMethods('/search?q=register&sort=relevance&page=0&starredOnly=false'),
    })
    await mockRegisterMutation(page)

    await page.route('**/api/web/skills?**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: { items: [], total: 0, page: 0, size: 12 },
          timestamp: '2026-03-31T00:00:00Z',
          requestId: 'playwright-e2e',
        }),
      })
    })

    await page.goto('/register?returnTo=%2Fsearch%3Fq%3Dregister%26sort%3Drelevance%26page%3D0%26starredOnly%3Dfalse')

    await expect(page.getByRole('heading', { name: 'Create Account' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Back to login' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2Fsearch%3Fq%3Dregister%26sort%3Drelevance%26page%3D0%26starredOnly%3Dfalse',
    )

    await page.getByLabel('Username').fill('local-user')
    await page.getByLabel('Email').fill('local-user@example.com')
    await page.getByLabel('Password').fill('ChangeMe!2026')
    await page.getByRole('button', { name: 'Register & Login' }).click()

    await expect(page).toHaveURL('/search?q=register&sort=relevance&page=0&starredOnly=false')
  })
})
