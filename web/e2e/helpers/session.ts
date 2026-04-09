import { expect, type Page, type TestInfo } from '@playwright/test'

const password = 'Passw0rd!123'
const cachedUserByWorker = new Map<number, string>()
const cachedSessionByAccount = new Map<string, SessionSnapshot>()
const requestTimeoutMs = process.env.CI ? 12_000 : 8_000

export interface TestCredentials {
  password: string
  username: string
}

interface SessionSnapshot {
  username: string
  cookies: Array<{
    name: string
    value: string
    domain: string
    path: string
    expires: number
    httpOnly: boolean
    secure: boolean
    sameSite: 'Strict' | 'Lax' | 'None'
  }>
}

const cachedSessionByWorker = new Map<number, SessionSnapshot>()

function usernameForWorker(testInfo?: TestInfo): string {
  const worker = testInfo?.parallelIndex ?? 0
  return `e2e_worker_${worker}`
}

function uniqueUsernameForWorker(testInfo?: TestInfo): string {
  const worker = testInfo?.parallelIndex ?? 0
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`
  return `e2e_w${worker}_${suffix}`
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
}

function isRetryableStatus(status: number): boolean {
  return status === 429 || status >= 500
}

async function loginWithRetry(
  request: Page['request'],
  username: string,
  currentPassword = password,
  retries = process.env.CI ? 10 : 6,
): Promise<boolean> {
  for (let i = 0; i < retries; i += 1) {
    try {
      const login = await request.post('/api/v1/auth/local/login', {
        data: { username, password: currentPassword },
        timeout: requestTimeoutMs,
      })

      if (login.ok()) {
        return true
      }

      const status = login.status()
      if (!isRetryableStatus(status)) {
        return false
      }
    } catch {
      // Request context can be transiently unstable in CI startup windows.
    }

    await sleep(250 * (i + 1))
  }

  return false
}

async function hasActiveSession(page: Page): Promise<boolean> {
  try {
    const response = await page.context().request.get('/api/v1/auth/me', {
      timeout: requestTimeoutMs,
    })
    return response.ok()
  } catch {
    return false
  }
}

async function cacheSession(page: Page, worker: number, username: string) {
  const snapshot = {
    username,
    cookies: await page.context().cookies(),
  }
  cachedSessionByWorker.set(worker, snapshot)
  cachedSessionByAccount.set(username, snapshot)
}

async function cacheAccountSession(page: Page, username: string) {
  cachedSessionByAccount.set(username, {
    username,
    cookies: await page.context().cookies(),
  })
}

async function restoreCachedSession(page: Page, worker: number): Promise<SessionSnapshot | null> {
  const snapshot = cachedSessionByWorker.get(worker)
  if (!snapshot) {
    return null
  }

  await page.context().addCookies(snapshot.cookies)
  if (await hasActiveSession(page)) {
    return snapshot
  }

  cachedSessionByWorker.delete(worker)
  return null
}

async function restoreCachedSessionForAccount(page: Page, username: string): Promise<SessionSnapshot | null> {
  const snapshot = cachedSessionByAccount.get(username)
  if (!snapshot) {
    return null
  }

  await page.context().addCookies(snapshot.cookies)
  if (await hasActiveSession(page)) {
    return snapshot
  }

  cachedSessionByAccount.delete(username)
  return null
}

async function primeAuthProviders(page: Page) {
  try {
    await page.context().request.get('/api/v1/auth/providers', { timeout: requestTimeoutMs })
  } catch {
    // Best effort warm-up.
  }
}

async function tryBootstrapMockSession(page: Page, worker: number): Promise<{ username: string, password: string } | null> {
  try {
    await page.context().request.get('/api/v1/auth/providers', {
      headers: { 'X-Mock-User-Id': 'local-user' },
      timeout: requestTimeoutMs,
    })
  } catch {
    return null
  }

  if (!(await hasActiveSession(page))) {
    return null
  }

  await cacheSession(page, worker, 'local-user')
  cachedUserByWorker.set(worker, 'local-user')
  return { username: 'local-user', password }
}

async function registerSessionOnce(page: Page, testInfo?: TestInfo) {
  const worker = testInfo?.parallelIndex ?? 0
  const cached = cachedUserByWorker.get(worker)
  const username = usernameForWorker(testInfo)
  const request = page.context().request

  await primeAuthProviders(page)

  // Avoid hammering auth endpoints on every test run for the same worker.
  const restored = await restoreCachedSession(page, worker)
  if (restored) {
    cachedUserByWorker.set(worker, restored.username)
    return { username: restored.username, password }
  }

  const mockSession = await tryBootstrapMockSession(page, worker)
  if (mockSession) {
    return mockSession
  }

  // Prefer the known-good cached account to avoid repeated failed-logins on a fixed username.
  if (cached && await loginWithRetry(request, cached)) {
    await cacheSession(page, worker, cached)
    return { username: cached, password }
  }

  // Support environments where a deterministic worker account already exists.
  if (!cached && await loginWithRetry(request, username, password, process.env.CI ? 4 : 3)) {
    cachedUserByWorker.set(worker, username)
    await cacheSession(page, worker, username)
    return { username, password }
  }

  try {
    const register = await request.post('/api/v1/auth/local/register', {
      data: {
        username,
        password,
        email: `${username}@example.test`,
      },
      timeout: requestTimeoutMs,
    })

    if (register.ok()) {
      cachedUserByWorker.set(worker, username)
      await cacheSession(page, worker, username)
      return { username, password }
    }

    if (register.status() === 409 && await loginWithRetry(request, username, password, process.env.CI ? 8 : 6)) {
      cachedUserByWorker.set(worker, username)
      await cacheSession(page, worker, username)
      return { username, password }
    }
  } catch {
    // Fall through to the unique-account fallback below.
  }

  // Registering creates session cookies for the current request context.
  // Prefer creating a new unique account to avoid password drift and login throttling.
  for (let i = 0; i < 12; i += 1) {
    const uniqueUsername = `${uniqueUsernameForWorker(testInfo)}_${i}`

    try {
      const register = await request.post('/api/v1/auth/local/register', {
        data: {
          username: uniqueUsername,
          password,
          email: `${uniqueUsername}@example.test`,
        },
        timeout: requestTimeoutMs,
      })

      if (register.ok()) {
        cachedUserByWorker.set(worker, uniqueUsername)
        await cacheSession(page, worker, uniqueUsername)
        return { username: uniqueUsername, password }
      }

      const status = register.status()
      if (status === 409) {
        continue
      }

      if (isRetryableStatus(status)) {
        await sleep(300 * (i + 1))
        continue
      }

      // Username invalidation/conflicts can happen under concurrent CI retries.
      if (status === 400 || status === 409) {
        continue
      }

      expect(register.ok()).toBeTruthy()
    } catch {
      await sleep(300 * (i + 1))
    }
  }

  // Final fallback for environments where registration is temporarily unavailable.
  const fallbackCandidates = [cached, username].filter((candidate): candidate is string => Boolean(candidate))
  for (const candidate of fallbackCandidates) {
    if (await loginWithRetry(request, candidate, password, process.env.CI ? 12 : 8)) {
      cachedUserByWorker.set(worker, candidate)
      await cacheSession(page, worker, candidate)
      return { username: candidate, password }
    }
  }

  throw new Error(`Failed to establish e2e session for worker ${worker}`)
}

async function createFreshSessionOnce(page: Page, testInfo?: TestInfo) {
  const worker = testInfo?.parallelIndex ?? 0
  const request = page.context().request

  await primeAuthProviders(page)

  for (let i = 0; i < 12; i += 1) {
    const uniqueUsername = `${uniqueUsernameForWorker(testInfo)}_${i}`

    try {
      const register = await request.post('/api/v1/auth/local/register', {
        data: {
          username: uniqueUsername,
          password,
          email: `${uniqueUsername}@example.test`,
        },
        timeout: requestTimeoutMs,
      })

      if (register.ok()) {
        cachedUserByWorker.set(worker, uniqueUsername)
        await cacheSession(page, worker, uniqueUsername)
        return { username: uniqueUsername, password }
      }

      const status = register.status()
      if (status === 409 || status === 400) {
        continue
      }

      if (isRetryableStatus(status)) {
        await sleep(300 * (i + 1))
        continue
      }

      expect(register.ok()).toBeTruthy()
    } catch {
      await sleep(300 * (i + 1))
    }
  }

  throw new Error(`Failed to create fresh e2e session for worker ${worker}`)
}

export async function registerSession(page: Page, testInfo?: TestInfo) {
  let lastError: unknown

  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      return await registerSessionOnce(page, testInfo)
    } catch (error) {
      lastError = error
      if (attempt < 2) {
        await sleep(500 * (attempt + 1))
      }
    }
  }

  throw lastError
}

export async function createFreshSession(page: Page, testInfo?: TestInfo) {
  let lastError: unknown

  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      return await createFreshSessionOnce(page, testInfo)
    } catch (error) {
      lastError = error
      if (attempt < 2) {
        await sleep(500 * (attempt + 1))
      }
    }
  }

  throw lastError
}

export async function loginWithCredentials(page: Page, credentials: TestCredentials, _testInfo?: TestInfo) {
  const request = page.context().request

  await primeAuthProviders(page)

  const restored = await restoreCachedSessionForAccount(page, credentials.username)
  if (restored) {
    return credentials
  }

  const loggedIn = await loginWithRetry(
    request,
    credentials.username,
    credentials.password,
    process.env.CI ? 12 : 8,
  )
  expect(loggedIn).toBeTruthy()

  await cacheAccountSession(page, credentials.username)
  return credentials
}
