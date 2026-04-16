import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

function buildUniqueUser() {
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`
  return {
    username: `e2e_reg_${suffix}`,
    email: `e2e_reg_${suffix}@example.test`,
    password: 'Passw0rd!123',
  }
}

test.describe('Register Email Required (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('registers successfully when email is provided', async ({ page }) => {
    const user = buildUniqueUser()
    await page.goto('/register')

    await expect(page.getByRole('heading', { name: 'Create Account' })).toBeVisible()
    await page.getByLabel('Username').fill(user.username)
    await page.getByLabel('Email').fill(user.email)
    await page.getByLabel('Password').fill(user.password)
    await page.getByRole('button', { name: 'Register & Login' }).click()

    await expect(page).toHaveURL('/dashboard')
  })

  test('shows required validation when email is missing', async ({ page }) => {
    await page.goto('/register')

    await page.getByLabel('Username').fill(`e2e_no_email_${Date.now().toString(36)}`)
    await page.getByLabel('Password').fill('Passw0rd!123')
    await page.getByRole('button', { name: 'Register & Login' }).click()

    const isEmailMissing = await page.getByLabel('Email').evaluate((element) => {
      const input = element as HTMLInputElement
      return input.validity.valueMissing
    })

    expect(isEmailMissing).toBeTruthy()
    await expect(page).toHaveURL(/\/register/)
  })
})
