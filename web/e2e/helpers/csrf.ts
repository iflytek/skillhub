import type { Page } from '@playwright/test'

const csrfCookieName = 'XSRF-TOKEN'
const csrfHeaderName = 'X-XSRF-TOKEN'
const requestTimeoutMs = process.env.CI ? 12_000 : 8_000

async function readCsrfToken(page: Page, requestUrl: string): Promise<string | null> {
  const cookie = (await page.context().cookies(requestUrl)).find((item) => item.name === csrfCookieName)
  return cookie?.value?.trim() || null
}

export async function csrfHeaders(page: Page, headers?: Record<string, string>): Promise<Record<string, string>> {
  // Resolve the API context's actual origin, including before page.goto(). Cached
  // sessions can contain both localhost and 127.0.0.1 cookies; only use cookies
  // that the browser would send to this server, and refresh after session changes.
  const response = await page.context().request.get('/api/v1/auth/providers', { timeout: requestTimeoutMs })
  if (!response.ok()) {
    throw new Error(`Failed to prime CSRF cookie: HTTP ${response.status()}`)
  }
  const token = await readCsrfToken(page, response.url())

  if (!token) {
    throw new Error('Missing XSRF-TOKEN cookie after auth provider warm-up')
  }

  return {
    ...headers,
    [csrfHeaderName]: token,
  }
}
