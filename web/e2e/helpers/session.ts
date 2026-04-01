import { expect, type Page, type TestInfo } from '@playwright/test'

const password = 'Passw0rd!123'
const cachedUserByWorker = new Map<number, string>()

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
  retries = process.env.CI ? 10 : 6,
): Promise<boolean> {
  for (let i = 0; i < retries; i += 1) {
    try {
      const login = await request.post('/api/v1/auth/local/login', {
        data: { username, password },
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

async function registerSessionOnce(page: Page, testInfo?: TestInfo) {
  const worker = testInfo?.parallelIndex ?? 0
  const cached = cachedUserByWorker.get(worker)
  const username = usernameForWorker(testInfo)
  const request = page.context().request

  // Prime auth provider endpoint to stabilize cookie/bootstrap behavior.
  try {
    await request.get('/api/v1/auth/providers')
  } catch {
    // Best effort warm-up.
  }

  // Prefer the known-good cached account to avoid repeated failed-logins on a fixed username.
  if (cached && await loginWithRetry(request, cached)) {
    return { username: cached, password }
  }

  // Support environments where a deterministic worker account already exists.
  if (!cached && await loginWithRetry(request, username, process.env.CI ? 4 : 3)) {
    cachedUserByWorker.set(worker, username)
    return { username, password }
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
      })

      if (register.ok()) {
        cachedUserByWorker.set(worker, uniqueUsername)
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
    if (await loginWithRetry(request, candidate, process.env.CI ? 12 : 8)) {
      cachedUserByWorker.set(worker, candidate)
      return { username: candidate, password }
    }
  }

  throw new Error(`Failed to establish e2e session for worker ${worker}`)
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
