import { expect, type Page } from '@playwright/test'

export async function expectRedirectToLogin(page: Page, returnTo: string) {
  await expect(page).toHaveURL(/\/login\?returnTo=/)

  const currentUrl = new URL(page.url())
  expect(currentUrl.pathname).toBe('/login')
  expect(currentUrl.searchParams.get('returnTo')).toBe(returnTo)
}
