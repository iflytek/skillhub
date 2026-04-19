import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { test as base } from '@playwright/test'
import { registerSession, type TestCredentials } from './session'

interface WorkerAuthState {
  credentials: TestCredentials
  storageStatePath: string
  tempDir: string
}

type AuthFixtures = {
  workerAuthCredentials: TestCredentials
}

type AuthWorkerFixtures = {
  workerAuthState: WorkerAuthState
}

export const authedTest = base.extend<AuthFixtures, AuthWorkerFixtures>({
  workerAuthState: [async ({ browser }, use, workerInfo) => {
    const tempDir = mkdtempSync(path.join(tmpdir(), 'skillhub-e2e-auth-'))
    const storageStatePath = path.join(tempDir, `worker-${workerInfo.parallelIndex}.json`)
    const context = await browser.newContext()

    try {
      const page = await context.newPage()
      const credentials = await registerSession(page, { parallelIndex: workerInfo.parallelIndex })
      await context.storageState({ path: storageStatePath })
      await use({ credentials, storageStatePath, tempDir })
    } finally {
      await context.close().catch(() => undefined)
      rmSync(tempDir, { recursive: true, force: true })
    }
  }, { scope: 'worker' }],

  workerAuthCredentials: [async ({ workerAuthState }, use) => {
    await use(workerAuthState.credentials)
  }, { scope: 'worker' }],

  storageState: async ({ workerAuthState }, use) => {
    await use(workerAuthState.storageStatePath)
  },
})
