import { open, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

/** Serializes every local lifecycle mutation for one Skill target directory. */
export async function acquireSkillTargetLock(rootDir: string, slug: string): Promise<() => Promise<void>> {
  const lockPath = join(rootDir, `.${slug}.skillhub-install.lock`)

  const createLock = async () => {
    const handle = await open(lockPath, 'wx')
    try {
      await handle.writeFile(JSON.stringify({ pid: process.pid, createdAt: new Date().toISOString() }))
      return handle
    } catch (error) {
      await handle.close().catch(() => {})
      await rm(lockPath, { force: true }).catch(() => {})
      throw error
    }
  }

  let handle: Awaited<ReturnType<typeof open>>
  try {
    handle = await createLock()
  } catch (error) {
    if (!(error instanceof Error && 'code' in error && error.code === 'EEXIST')) throw error

    let ownerIsRunning = true
    try {
      const lock = JSON.parse(await readFile(lockPath, 'utf-8')) as { pid?: unknown }
      if (typeof lock.pid !== 'number') throw new Error('invalid Skill target lock')
      process.kill(lock.pid, 0)
    } catch (lockError) {
      if (lockError instanceof Error && 'code' in lockError && lockError.code === 'ESRCH') {
        ownerIsRunning = false
      }
    }

    if (ownerIsRunning) {
      throw new CliError(`install target is busy: ${join(rootDir, slug)}`, EXIT.filesystem, {
        path: join(rootDir, slug),
        next: 'wait for the other SkillHub CLI process to finish and retry'
      })
    }
    await rm(lockPath, { force: true })
    handle = await createLock()
  }

  return async () => {
    await handle.close().catch(() => {})
    await rm(lockPath, { force: true }).catch(() => {})
  }
}
