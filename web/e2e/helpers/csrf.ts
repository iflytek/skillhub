import type { Page } from '@playwright/test'

const csrfCookieName = 'XSRF-TOKEN'
const csrfHeaderName = 'X-XSRF-TOKEN'
const requestTimeoutMs = process.env.CI ? 12_000 : 8_000

async function readCsrfToken(page: Page): Promise<string | null> {
  const cookie = (await page.context().cookies()).find((item) => item.name === csrfCookieName)
  return cookie?.value?.trim() || null
}

export async function csrfHeaders(page: Page, headers?: Record<string, string>): Promise<Record<string, string>> {
  let token = await readCsrfToken(page)
  if (!token) {
    await page.context().request.get('/api/v1/auth/providers', { timeout: requestTimeoutMs })
    token = await readCsrfToken(page)
  }

  if (!token) {
    throw new Error('Missing XSRF-TOKEN cookie after auth provider warm-up')
  }

  return {
    ...headers,
    [csrfHeaderName]: token,
  }
}
