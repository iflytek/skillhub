import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { csrfHeaders } from './helpers/csrf'
import { fixtureClientIp } from './helpers/auth-fixtures'
import { createFreshSession } from './helpers/session'

test.describe('E2E fixture session isolation (Real API)', () => {
  test('selects the CSRF cookie for the request origin, not another loopback host', async ({ page, baseURL }) => {
    const primary = new URL(baseURL!)
    test.skip(!['localhost', '127.0.0.1'].includes(primary.hostname), 'Requires the same local server on both loopback names')
    const alternate = new URL(primary)
    alternate.hostname = primary.hostname === 'localhost' ? '127.0.0.1' : 'localhost'
    const providers = '/api/v1/auth/providers'
    expect((await page.request.get(new URL(providers, alternate).href)).ok()).toBeTruthy()
    expect((await page.request.get(providers)).ok()).toBeTruthy()
    const cookies = await page.context().cookies()
    expect(new Set(cookies.filter(c => c.name === 'XSRF-TOKEN').map(c => c.domain)).size).toBe(2)

    const response = await page.request.post('/api/v1/auth/local/login', {
      data: { username: `missing_${randomUUID().slice(0, 8)}`, password: 'InvalidPassword!123' },
      headers: await csrfHeaders(page),
    })
    // A valid CSRF token reaches authentication and rejects the nonexistent account.
    // A foreign-origin token fails earlier with 403.
    expect(response.status()).toBe(401)

    const invalidCsrf = await page.request.post('/api/v1/auth/local/login', {
      data: { username: 'missing_account', password: 'InvalidPassword!123' },
      headers: { 'X-XSRF-TOKEN': 'deliberately-invalid-csrf' },
    })
    expect(invalidCsrf.status()).toBe(403)
  })

  test('fresh-account fixtures do not share one anonymous registration quota', async ({ browser, baseURL }, testInfo) => {
    test.setTimeout(30_000)
    const usernames = new Set<string>()
    // The server's default anonymous registration quota is five per client in five minutes.
    // Seven independent contexts must remain seven independent fixture clients.
    for (let index = 0; index < 7; index += 1) {
      const context = await browser.newContext({ baseURL })
      try {
        const page = await context.newPage()
        const account = await createFreshSession(page, testInfo)
        usernames.add(account.username)
        expect((await page.request.get('/api/v1/auth/me')).status()).toBe(200)
      } finally {
        await context.close()
      }
    }
    expect(usernames.size).toBe(7)
  })

  test('the server still throttles repeated registration attempts from one client', async ({ browser, baseURL }) => {
    let address: string | undefined
    for (let index = 0; index < 6; index += 1) {
      const context = await browser.newContext({ baseURL })
      try {
        const page = await context.newPage()
        address ??= fixtureClientIp(page)
        const response = await page.request.post('/api/v1/auth/local/register', {
          // Invalid input consumes the anonymous quota without creating accounts.
          data: { username: 'x', password: 'invalid', email: 'invalid' },
          headers: await csrfHeaders(page, { 'X-Forwarded-For': address }),
        })
        expect(response.status()).toBe(index < 5 ? 400 : 429)
      } finally {
        await context.close()
      }
    }
  })
})
