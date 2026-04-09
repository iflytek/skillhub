import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { createFreshSession } from './helpers/session'

// TC_UN_* 用户名输入框 / TC_EM_* 邮箱输入框 / TC_PW_* 密码输入框
// TC_REG_* 注册/登录流程 / TC_UI_* UI/UX

let existingRegisteredUsername: string | null = null
const DUPLICATE_USERNAME_ERROR = /already.*exist|taken|username.*used/i
const REGISTER_RATE_LIMIT_ERROR = /too many|too frequent|rate limit|请求过于频繁/

test.describe('Register - Username Validation (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.goto('/register')
  })

  // TC_UN_008 P0
  test('TC_UN_008: shows required error when username is empty', async ({ page }) => {
    await page.getByRole('button', { name: 'Register' }).click()
    await expect(page.getByText(/username.*required|required.*username/i)).toBeVisible()
  })

  // TC_UN_001 P0 - valid minimum length
  test('TC_UN_001: accepts valid username with minimum 3 characters', async ({ page }) => {
    await page.getByLabel(/username/i).fill('abc')
    await page.getByLabel(/username/i).blur()
    await expect(page.getByText(/仅支持|only.*letter|username.*required/i)).not.toBeVisible()
  })

  // TC_UN_006 P1 - 2 chars below minimum
  test('TC_UN_006: shows length error for 2-character username', async ({ page }) => {
    await page.getByLabel(/username/i).fill('ab')
    await page.getByLabel(/username/i).blur()
    await expect(page.getByText(/3.{0,10}64|length|at least/i)).toBeVisible()
  })

  // TC_UN_009 P1 - special chars
  test('TC_UN_009: shows error for username with special characters like @', async ({ page }) => {
    await page.getByLabel(/username/i).fill('user@123')
    await page.getByLabel(/username/i).blur()
    await expect(page.getByText(/letter|number|underscore|alphanumeric/i)).toBeVisible()
  })

  // TC_UN_010 P1 - Chinese chars
  test('TC_UN_010: shows error for username containing Chinese characters', async ({ page }) => {
    await page.getByLabel(/username/i).fill('用户123')
    await page.getByLabel(/username/i).blur()
    await expect(page.getByText(/letter|number|underscore|alphanumeric/i)).toBeVisible()
  })
})

test.describe('Register - Email Validation (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.goto('/register')
  })

  // TC_EM_007 P0 - email is optional
  test('TC_EM_007: allows empty email (email is optional)', async ({ page }) => {
    const emailField = page.getByLabel(/email/i)
    if (await emailField.isVisible()) {
      await emailField.clear()
      await emailField.blur()
      await expect(page.getByText(/email.*required/i)).not.toBeVisible()
    }
  })

  // TC_EM_008 P1 - missing @
  test('TC_EM_008: shows error for email missing @ symbol', async ({ page }) => {
    const emailField = page.getByLabel(/email/i)
    if (await emailField.isVisible()) {
      await emailField.fill('userexample.com')
      await emailField.blur()
      await expect(page.getByText(/email.*invalid|invalid.*email|format/i)).toBeVisible()
    }
  })

  // TC_EM_009 P1 - missing domain
  test('TC_EM_009: shows error for email missing domain after @', async ({ page }) => {
    const emailField = page.getByLabel(/email/i)
    if (await emailField.isVisible()) {
      await emailField.fill('user@')
      await emailField.blur()
      await expect(page.getByText(/email.*invalid|invalid.*email|format/i)).toBeVisible()
    }
  })
})

test.describe('Register - Password Validation (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
    await page.goto('/register')
  })

  // TC_PW_013 P0 - empty password
  test('TC_PW_013: shows required error when password is empty', async ({ page }) => {
    await page.getByRole('button', { name: 'Register' }).click()
    await expect(page.getByText(/password.*required|required.*password/i)).toBeVisible()
  })

  // TC_PW_007 P1 - 7 chars (below minimum 8)
  test('TC_PW_007: shows length error for 7-character password', async ({ page }) => {
    await page.getByLabel(/^password/i).fill('Abc123!')
    await page.getByLabel(/^password/i).blur()
    await expect(page.getByText(/8|at least|minimum/i)).toBeVisible()
  })

  // TC_PW_008 P1 - only 2 types (uppercase + lowercase)
  test('TC_PW_008: shows complexity error for password with only 2 character types', async ({ page }) => {
    await page.getByLabel(/^password/i).fill('Abcdefgh')
    await page.getByLabel(/^password/i).blur()
    await expect(page.getByText(/three|3.*type|character type|complexity/i)).toBeVisible()
  })

  // TC_PW_001 P0 - valid password with 3+ types
  test('TC_PW_001: accepts valid password with 3 character types and minimum length', async ({ page }) => {
    await page.getByLabel(/^password/i).fill('Abc123!@')
    await page.getByLabel(/^password/i).blur()
    await expect(page.getByText(/three|3.*type|character type|complexity/i)).not.toBeVisible()
    await expect(page.getByText(/8|at least|minimum/i)).not.toBeVisible()
  })
})

test.describe('Register Flow (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_REG_001 P0 - successful registration with all fields
  test('TC_REG_001: registers successfully with valid username, email and password', async ({ page }) => {
    await page.goto('/register')
    const suffix = Date.now().toString(36)
    const username = `testuser_${suffix}`
    await page.getByLabel(/username/i).fill(username)
    const emailField = page.getByLabel(/email/i)
    if (await emailField.isVisible()) {
      await emailField.fill(`test_${suffix}@example.test`)
    }
    await page.getByLabel(/^password/i).fill('Test123!@')
    await page.getByRole('button', { name: 'Register' }).click()
    // Should redirect away from /register on success
    await expect(page).not.toHaveURL('/register')
    existingRegisteredUsername = username
  })

  // TC_REG_003 P0 - duplicate username
  test('TC_REG_003: shows error when registering with existing username', async ({ browser, page }, testInfo) => {
    let username = existingRegisteredUsername
    if (!username) {
      const seedContext = await browser.newContext()
      const seedPage = await seedContext.newPage()
      const seedCredentials = await createFreshSession(seedPage, testInfo)
      username = seedCredentials.username
      existingRegisteredUsername = username
      await seedContext.close()
    }

    // Now try to register with the same username again
    await page.goto('/register')
    await setEnglishLocale(page)
    await page.getByLabel(/username/i).fill(username)
    await page.getByLabel(/^password/i).fill('Test123!@')
    const main = page.getByRole('main')
    const duplicateUsernameError = main.getByText(DUPLICATE_USERNAME_ERROR).first()
    const registerRateLimitError = main.getByText(REGISTER_RATE_LIMIT_ERROR).first()

    for (let attempt = 0; attempt < 3; attempt += 1) {
      await page.getByRole('button', { name: 'Register' }).click()

      if (await duplicateUsernameError.isVisible().catch(() => false)) {
        return
      }

      if (attempt < 2 && await registerRateLimitError.isVisible().catch(() => false)) {
        await page.waitForTimeout(1_500 * (attempt + 1))
        continue
      }

      break
    }

    await expect(duplicateUsernameError).toBeVisible()
  })

  // TC_REG_002 P0 - registration without email
  test('TC_REG_002: registers successfully without email (email is optional)', async ({ page }) => {
    await page.goto('/register')
    const suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 5)
    await page.getByLabel(/username/i).fill(`noemail_${suffix}`)
    await page.getByLabel(/^password/i).fill('Test123!@')
    await page.getByRole('button', { name: 'Register' }).click()
    await expect(page).not.toHaveURL('/register')
  })

  // TC_REG_005 P0 - required fields empty on submit
  test('TC_REG_005: shows validation errors when submitting empty required fields', async ({ page }) => {
    await page.goto('/register')
    await page.getByRole('button', { name: 'Register' }).click()
    await expect(page.getByText(/username.*required|required.*username/i)).toBeVisible()
    await expect(page.getByText(/password.*required|required.*password/i)).toBeVisible()
  })
})

test.describe('Login Flow (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_REG_006 P0 - successful login (already tested in auth-entry.spec.ts partially; extend here)
  test('TC_REG_006: shows required field errors when submitting empty login form', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()
  })

  // TC_REG_007 P0 - wrong password
  test('TC_REG_007: shows error for wrong password on existing account', async ({ page }) => {
    // First register a user, then attempt login with wrong password
    const suffix = Date.now().toString(36)
    const username = `logintest_${suffix}`

    await page.goto('/register')
    await page.getByLabel(/username/i).fill(username)
    await page.getByLabel(/^password/i).fill('Test123!@')
    await page.getByRole('button', { name: 'Register' }).click()
    await expect(page).not.toHaveURL('/register')

    await page.goto('/login')
    await setEnglishLocale(page)
    await page.getByLabel(/username/i).fill(username)
    await page.getByLabel(/^password/i).fill('WrongPassword999!')
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText(/invalid|incorrect|wrong|username.*password/i)).toBeVisible()
  })

  // TC_REG_008 P0 - non-existent username
  test('TC_REG_008: shows error for non-existent username login attempt', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel(/username/i).fill('nonexistent_user_xyz99999')
    await page.getByLabel(/^password/i).fill('Test123!@')
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText(/invalid|incorrect|wrong|username.*password|not found/i)).toBeVisible()
  })

  // TC_REG_010 P1 - SQL injection safety
  test('TC_REG_010: safely handles SQL injection input in username field', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel(/username/i).fill("admin' OR '1'='1")
    await page.getByLabel(/^password/i).fill('anything')
    await page.getByRole('button', { name: 'Login' }).click()
    // Should not log in; should show error or validation message, NOT redirect to dashboard
    await expect(page).not.toHaveURL('/dashboard')
  })

  // TC_REG_011 P1 - XSS in input
  test('TC_REG_011: safely handles XSS payload in username field without executing script', async ({ page }) => {
    let alerted = false
    page.on('dialog', () => { alerted = true })

    await page.goto('/login')
    await page.getByLabel(/username/i).fill("<script>alert('xss')</script>")
    await page.getByLabel(/^password/i).fill('anything')
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page).not.toHaveURL('/dashboard')
    expect(alerted).toBe(false)
  })
})

test.describe('Register/Login UI (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  // TC_UI_003 P2 - password visibility toggle
  test('TC_UI_003: password visibility toggle switches between masked and plain text', async ({ page }) => {
    await page.goto('/register')
    const passwordInput = page.getByLabel(/^password/i)
    await expect(passwordInput).toHaveAttribute('type', 'password')

    const toggleBtn = page.getByRole('button', { name: /show|hide|toggle/i })
      .or(page.locator('[data-testid*="password-toggle"], [aria-label*="password"]'))
    if (await toggleBtn.isVisible()) {
      await toggleBtn.click()
      await expect(passwordInput).toHaveAttribute('type', 'text')
    }
  })

  // TC_UI_005 P2 - Enter key submits form
  test('TC_UI_005: pressing Enter in the last input field submits the login form', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel(/username/i).fill('someuser')
    await page.getByLabel(/^password/i).fill('SomePass123!')
    await page.getByLabel(/^password/i).press('Enter')
    // Form should attempt submission (either error msg or redirect)
    await expect(
      page.getByText(/invalid|incorrect|dashboard/i)
        .or(page.locator('[role="alert"]'))
    ).toBeVisible({ timeout: 5000 })
  })

  // returnTo param preservation (from auth-entry.spec.ts - extended)
  test('preserves returnTo param when navigating from register link on login page', async ({ page }) => {
    await page.goto('/login?returnTo=%2Fdashboard%2Ftokens')
    await page.getByRole('link', { name: /sign up|register/i }).click()
    await expect(page).toHaveURL('/register?returnTo=%2Fdashboard%2Ftokens')
  })
})
