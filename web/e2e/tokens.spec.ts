import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { mockTokenPage } from './helpers/route-mocks'

function tokenItem(id: number, name: string, overrides: Partial<{
  tokenPrefix: string
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string | null
}> = {}) {
  return {
    id,
    name,
    tokenPrefix: `sk_token_${id}`,
    createdAt: '2026-03-20T00:00:00Z',
    lastUsedAt: null,
    expiresAt: null,
    ...overrides,
  }
}

test.describe('Tokens Page', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates duplicate token names and creates a new token', async ({ page }) => {
    await mockTokenPage(page, {
      tokens: [tokenItem(1, 'existing-cli')],
    })

    await page.goto('/dashboard/tokens')

    await expect(page.getByRole('heading', { name: 'API Tokens' })).toBeVisible()
    await page.getByRole('button', { name: 'Create Token' }).click()

    await page.locator('#token-name').fill('existing-cli')
    await expect(page.getByText('You already have a token with this name')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create', exact: true })).toBeDisabled()

    await page.locator('#token-name').fill('deploy-cli')
    await page.getByRole('button', { name: 'Create', exact: true }).click()

    await expect(page.getByText('Token Created')).toBeVisible()
    await expect(page.getByText('sk_live_created_2')).toBeVisible()
    await expect(page.getByText('deploy-cli', { exact: true }).first()).toBeVisible()
  })

  test('updates token expiration and deletes a token', async ({ page }) => {
    await mockTokenPage(page, {
      tokens: [tokenItem(1, 'existing-cli')],
    })

    await page.goto('/dashboard/tokens')

    const row = page.getByRole('row', { name: /existing-cli/ })
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: 'Edit Expiration' }).click()

    await page.locator('#token-expiration-mode').click()
    await page.getByRole('option', { name: 'Expires in 7 days' }).click()
    await page.getByRole('button', { name: 'Save Expiration' }).click()

    await expect(page.getByText('Token expiration updated')).toBeVisible()
    await expect(page.getByRole('row', { name: /existing-cli/ }).getByText('Never expires')).not.toBeVisible()

    await page.getByRole('row', { name: /existing-cli/ }).getByRole('button', { name: 'Delete' }).click()
    await page.getByRole('button', { name: 'Delete' }).last().click()

    await expect(page.getByText('Token deleted')).toBeVisible()
    await expect(page.getByRole('row', { name: /existing-cli/ })).not.toBeVisible()
  })
})
