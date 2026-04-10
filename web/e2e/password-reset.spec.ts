import { expect, test } from '@playwright/test'
import { setEnglishLocale, setUniqueClientIp } from './helpers/auth-fixtures'

test.describe('Password Reset (Real API)', () => {
  function uniqueResetEmail(seed: string) {
    return `nonexistent_${seed}_${Date.now()}@example.com`
  }

  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('sends verification code from reset-password page', async ({ page }) => {
    const email = uniqueResetEmail('request')
    await setUniqueClientIp(page, 'password-reset-request')

    await page.goto('/reset-password')

    await expect(page.getByRole('heading', { name: 'Reset Password' })).toBeVisible()
    await page.getByLabel('Email').fill(email)
    await page.getByRole('button', { name: 'Send Verification Code' }).click()

    await expect(page.getByText('If the account is eligible, a verification code has been sent.')).toBeVisible()
  })

  test('shows backend validation error for an invalid reset code', async ({ page }) => {
    const email = uniqueResetEmail('invalid-code')
    await setUniqueClientIp(page, 'password-reset-invalid-code')

    await page.goto('/reset-password')

    await expect(page.getByRole('heading', { name: 'Reset Password' })).toBeVisible()
    await page.getByLabel('Email').fill(email)
    await page.getByRole('button', { name: 'Send Verification Code' }).click()
    await expect(page.getByText('If the account is eligible, a verification code has been sent.')).toBeVisible()
    await page.getByLabel('Verification Code').fill('123456')
    await page.getByLabel('New Password').fill('Passw0rd!123')
    await page.getByLabel('Confirm Password').fill('Passw0rd!123')
    await page.getByRole('button', { name: 'Reset Password' }).click()

    await expect(
      page.getByText(/The verification code is invalid or has expired\.|验证码无效或已过期。/)
    ).toBeVisible()
  })
})
