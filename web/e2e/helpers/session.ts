import { expect, type Page, type TestInfo } from '@playwright/test'

const password = 'Passw0rd!123'
const cachedUserByWorker = new Map<number, string>()

function usernameForWorker(testInfo?: TestInfo): string {
  const worker = testInfo?.parallelIndex ?? 0
  return `e2e_worker_${worker}`
}

function uniqueUsernameForWorker(testInfo?: TestInfo): string {
  const worker = testInfo?.parallelIndex ?? 0
  return `e2e_worker_${worker}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
}

async function loginWithRetry(
  request: Page['request'],
  username: string,
  retries = 6,
): Promise<boolean> {
  for (let i = 0; i < retries; i += 1) {
    const login = await request.post('/api/v1/auth/local/login', {
      data: { username, password },
    })

    if (login.ok()) {
      return true
    }

    const status = login.status()
    if (status !== 429 && status < 500) {
      return false
    }

    await sleep(250 * (i + 1))
  }

  return false
}

export async function registerSession(page: Page, testInfo?: TestInfo) {
  const worker = testInfo?.parallelIndex ?? 0
  const cached = cachedUserByWorker.get(worker)
  const username = cached ?? usernameForWorker(testInfo)
  const request = page.context().request

  if (await loginWithRetry(request, username)) {
    cachedUserByWorker.set(worker, username)
    return { username, password }
  }

  // Registering creates session cookies for the current request context.
  // Prefer creating a new unique account to avoid password drift and login throttling.
  for (let i = 0; i < 8; i += 1) {
    const uniqueUsername = `${uniqueUsernameForWorker(testInfo)}_${i}`
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

    if (register.status() === 409) {
      continue
    }

    if (register.status() === 429 || register.status() >= 500) {
      await sleep(300 * (i + 1))
      continue
    }

    expect(register.ok()).toBeTruthy()
  }

  // Final fallback for environments where registration is temporarily unavailable.
  const fallbackLoggedIn = await loginWithRetry(request, username, 8)
  expect(fallbackLoggedIn).toBeTruthy()
  cachedUserByWorker.set(worker, username)
  return { username, password }
}
