import { randomBytes } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Auth Entry', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates required fields and preserves returnTo on register link', async ({ page }) => {
    await page.route('**/runtime-config.js', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.__SKILLHUB_RUNTIME_CONFIG__ = { authDirectEnabled: "true", authDirectProvider: "local" }',
      })
    })
    await page.goto('/login?returnTo=%2Fdashboard%2Ftokens')

    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()
    await expect(page.getByText(/password compatibility layer/i)).toHaveCount(0)

    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()

    await page.getByRole('link', { name: 'Sign up now' }).click()
    await expect(page).toHaveURL('/register?returnTo=%2Fdashboard%2Ftokens')

    await page.getByLabel('Email').fill('test@example.com')
    await page.getByRole('button', { name: 'Register & Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()

    await page.getByLabel('Email').fill('')
    await page.getByLabel('Email').blur()
    await expect(page.getByText('Email is required')).toBeVisible()
  })

  test('shows configured OAuth methods without exposing unsupported organization discovery', async ({ page }) => {
    await page.route('**/api/v1/auth/methods*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: [
            {
              id: 'github',
              methodType: 'OAUTH_REDIRECT',
              provider: 'github',
              displayName: 'GitHub',
              actionUrl: '/oauth2/authorization/github',
            },
            {
              id: 'enterprise-discovery',
              methodType: 'ENTERPRISE_DISCOVERY',
              provider: 'enterprise',
              displayName: 'Organization login',
              actionUrl: '/api/v1/auth/login-discovery',
            },
          ],
        }),
      })
    })

    await page.goto('/login')

    await expect(page.getByRole('button', { name: 'GitHub' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Organization login' })).toHaveCount(0)
  })

  test('keeps registration usable without configured OAuth methods', async ({ page }) => {
    await page.route('**/api/v1/auth/methods*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, msg: 'ok', data: [] }),
      })
    })

    await page.goto('/register')

    await expect(page.getByRole('button', { name: 'Register & Login' })).toBeVisible()
    await expect(page.getByText('Sign in directly with your existing OAuth account')).toHaveCount(0)
  })

  test('keeps the login form available when the session status check fails', async ({ page }) => {
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":503,"msg":"Unavailable"}' })
    })

    await page.goto('/login')

    await expect(page.getByLabel('Username')).toBeVisible()
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Login' })).toBeVisible()

    await page.goto('/dashboard/tokens')
    await expect(page.getByRole('heading', { name: 'Something went wrong' })).toBeVisible()
    await expect(page.getByLabel('Password', { exact: true })).toHaveCount(0)
  })

  test('routes configured direct password login to the direct endpoint', async ({ page }) => {
    let directRequests = 0
    let localRequests = 0
    await page.route('**/runtime-config.js', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.__SKILLHUB_RUNTIME_CONFIG__ = { authDirectEnabled: "true", authDirectProvider: "local" }',
      })
    })
    await page.route('**/api/v1/auth/direct/login', async (route) => {
      directRequests += 1
      expect(route.request().method()).toBe('POST')
      expect(route.request().postDataJSON()).toEqual({ provider: 'local', username: 'direct-demo', password: 'Valid1!pass' })
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, msg: 'ok', data: { userId: 'direct-demo', username: 'direct-demo', displayName: 'Direct Demo', platformRoles: [] } }),
      })
    })
    await page.route('**/api/v1/auth/local/login', async (route) => {
      localRequests += 1
      await route.abort()
    })

    await page.goto('/login')
    await page.getByLabel('Username').fill('direct-demo')
    await page.getByLabel('Password', { exact: true }).fill('Valid1!pass')
    await page.getByRole('button', { name: 'Login' }).click()

    await expect(page).toHaveURL('/')
    expect(directRequests).toBe(1)
    expect(localRequests).toBe(0)
  })

  test('keeps the desktop layout stable across theme and login-mode changes', async ({ page }, testInfo) => {
    await page.route('**/runtime-config.js', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.__SKILLHUB_RUNTIME_CONFIG__ = { authSessionBootstrapEnabled: "true", authSessionBootstrapProvider: "proxy", authSessionBootstrapAuto: "false" }',
      })
    })

    for (const viewport of [{ width: 1280, height: 800 }, { width: 1920, height: 1080 }]) {
      await page.setViewportSize(viewport)
      await page.goto('/login')
      await page.addStyleTag({ content: '*, *::before, *::after { animation-duration: 0s !important; transition-duration: 0s !important; }' })
      const title = page.getByRole('heading', { name: 'Login to SkillHub' })
      const registerLink = page.getByRole('link', { name: 'Sign up now' })
      await expect(title).toBeVisible()
      await expect(registerLink).toBeVisible()

      const before = await title.boundingBox()
      await page.getByRole('button', { name: 'Organization login' }).click()
      await expect(page.getByRole('button', { name: 'Log in with work account' })).toBeVisible()
      const after = await title.boundingBox()
      expect(before && after && Math.abs(before.y - after.y)).toBeLessThan(2)
      await expect(registerLink).toBeVisible()
      await page.getByRole('button', { name: 'Personal login' }).click()
      await expect(page.getByLabel('Password', { exact: true })).toBeVisible()

      const themeSwitch = page.getByRole('switch', { name: 'Dark theme' })
      if (await themeSwitch.getAttribute('aria-checked') === 'true') await themeSwitch.click()
      const lightArtwork = page.locator('img[src$="/login-skill-art-light.png"]')
      const darkArtwork = page.locator('img[src$="/login-skill-art-dark.png"]')
      await expect(lightArtwork).toBeVisible()
      await expect.poll(() => lightArtwork.evaluate((image) => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
      await expect(page.locator('img[src$="/login-skill-art-dark.png"]')).toBeHidden()
      await page.screenshot({ path: testInfo.outputPath(`login-${viewport.width}-light.png`) })
      await themeSwitch.click()
      await expect(darkArtwork).toBeVisible()
      await expect.poll(() => darkArtwork.evaluate((image) => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
      await expect(page.locator('img[src$="/login-skill-art-light.png"]')).toBeHidden()
      await page.screenshot({ path: testInfo.outputPath(`login-${viewport.width}-dark.png`) })

      const dimensions = await page.evaluate(() => ({
        scrollHeight: document.documentElement.scrollHeight,
        scrollWidth: document.documentElement.scrollWidth,
        innerHeight: window.innerHeight,
        innerWidth: window.innerWidth,
      }))
      expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.innerWidth + 2)
      expect(dimensions.scrollHeight).toBeLessThanOrEqual(dimensions.innerHeight + 2)

      await page.goto('/register')
      await page.addStyleTag({ content: '*, *::before, *::after { animation-duration: 0s !important; transition-duration: 0s !important; }' })
      const registerButton = page.getByRole('button', { name: 'Register & Login' })
      await expect(registerButton).toBeVisible()
      const registerButtonBox = await registerButton.boundingBox()
      expect(registerButtonBox && registerButtonBox.y + registerButtonBox.height).toBeLessThanOrEqual(viewport.height + 2)
      const registerDimensions = await page.evaluate(() => ({
        scrollHeight: document.documentElement.scrollHeight,
        scrollWidth: document.documentElement.scrollWidth,
        innerHeight: window.innerHeight,
        innerWidth: window.innerWidth,
      }))
      expect(registerDimensions.scrollWidth).toBeLessThanOrEqual(registerDimensions.innerWidth + 2)
      expect(registerDimensions.scrollHeight).toBeLessThanOrEqual(registerDimensions.innerHeight + 2)
      await page.screenshot({ path: testInfo.outputPath(`register-${viewport.width}-dark.png`) })
    }

    await page.getByRole('button', { name: 'English' }).click()
    await page.getByRole('menuitem', { name: '中文' }).click()
    await expect(page.getByText(/让技能\s*连接团队/)).toBeVisible()
    await page.getByRole('button', { name: '中文' }).click()
    await page.getByRole('menuitem', { name: 'Русский' }).click()
    await expect(page.getByText(/Навыки\s*объединяют\s*команду/)).toBeVisible()
  })

  test('keeps configured session bootstrap available in the organization view', async ({ page }) => {
    await page.route('**/runtime-config.js', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.__SKILLHUB_RUNTIME_CONFIG__ = { authSessionBootstrapEnabled: "true", authSessionBootstrapProvider: "proxy", authSessionBootstrapAuto: "false" }',
      })
    })

    await page.goto('/login')
    await page.getByRole('button', { name: 'Organization login' }).click()

    await expect(page.getByRole('button', { name: 'Log in with work account' })).toBeVisible()
    await expect(page.getByLabel('Password', { exact: true })).toBeHidden()

    await page.getByRole('button', { name: 'Personal login' }).click()
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible()
  })

  test('logs in to an explicit local destination on the isolated runtime', async ({ browser, page }) => {
    test.skip(process.env.SKILLHUB_RUNTIME_E2E !== '1', 'Requires an isolated runtime with local registration enabled')
    test.setTimeout(90_000)

    const suffix = randomBytes(6).toString('hex')
    const username = `pr901_${suffix}`
    const password = `${randomBytes(18).toString('base64url')}Aa1!`

    await page.goto('/register')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Email').fill(`${username}@example.com`)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Register & Login' }).click()
    await expect(page).toHaveURL('/')

    await page.goto('/login')
    await expect(page).toHaveURL('/')
    await page.goto('/login?returnTo=%2Fdashboard%2Ftokens%3Ftab%3Dactive%23latest')
    await expect(page).toHaveURL('/dashboard/tokens?tab=active#latest')
    await page.goto('/login?returnTo=%2Flogin')
    await expect(page).toHaveURL('/')

    const loginPage = await browser.newPage()
    await setEnglishLocale(loginPage)
    await loginPage.goto('/login?returnTo=%2Fdashboard%2Ftokens')
    await expect.poll(() => loginPage.evaluate(async () => (await fetch('/api/v1/auth/me')).status)).toBe(401)

    await loginPage.getByLabel('Username').fill(username)
    await loginPage.getByLabel('Password', { exact: true }).fill(password)
    await loginPage.getByRole('button', { name: 'Login' }).click()
    await expect(loginPage).toHaveURL('/dashboard/tokens')
    await expect.poll(() => loginPage.evaluate(async () => (await fetch('/api/v1/auth/me')).status)).toBe(200)
    await loginPage.close()

    const defaultLoginPage = await browser.newPage()
    await setEnglishLocale(defaultLoginPage)
    await defaultLoginPage.goto('/login')
    await defaultLoginPage.getByLabel('Username').fill(username)
    await defaultLoginPage.getByLabel('Password', { exact: true }).fill(password)
    await defaultLoginPage.getByRole('button', { name: 'Login' }).click()
    await expect(defaultLoginPage).toHaveURL('/')
    await defaultLoginPage.close()
  })
})
