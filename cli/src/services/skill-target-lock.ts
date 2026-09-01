import { createHash, randomUUID } from 'node:crypto'
import { open, readFile, rename, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { ensureDir } from '../platform/paths'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

/** Serializes every local lifecycle mutation for one Skill target directory. */
export async function acquireSkillTargetLock(rootDir: string, slug: string): Promise<() => Promise<void>> {
  const lockPath = await skillTargetLockPath(rootDir, slug)
  const ownershipToken = randomUUID()

  const createLock = async () => {
    const handle = await open(lockPath, 'wx')
    try {
      await handle.writeFile(JSON.stringify({
        pid: process.pid,
        ownershipToken,
        createdAt: new Date().toISOString()
      }))
      return handle
    } catch (error) {
      await handle.close().catch(() => {})
      await removeOwnedLock(lockPath, ownershipToken)
      throw error
    }
  }

  let handle: Awaited<ReturnType<typeof open>> | null = null
  for (let attempt = 0; attempt < 3 && !handle; attempt++) {
    try {
      handle = await createLock()
      break
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

      if (ownerIsRunning) throw targetBusyError(rootDir, slug)

      const quarantinedPath = `${lockPath}.stale-${ownershipToken}`
      try {
        await rename(lockPath, quarantinedPath)
      } catch (reclaimError) {
        if (reclaimError instanceof Error && 'code' in reclaimError && reclaimError.code === 'ENOENT') continue
        throw reclaimError
      }
      await rm(quarantinedPath, { force: true }).catch(() => {})
    }
  }
  if (!handle) throw targetBusyError(rootDir, slug)
  const ownedHandle = handle

  return async () => {
    await ownedHandle.close().catch(() => {})
    await removeOwnedLock(lockPath, ownershipToken)
  }
}

export async function skillTargetLockPath(rootDir: string, slug: string): Promise<string> {
  const target = resolve(rootDir, slug)
  const digest = createHash('sha256').update(target).digest('hex')
  const uid = typeof process.getuid === 'function' ? process.getuid() : 'user'
  const lockDir = join(tmpdir(), `skillhub-cli-target-locks-${uid}`)
  await ensureDir(lockDir)
  return join(lockDir, `${digest}.lock`)
}

function targetBusyError(rootDir: string, slug: string): CliError {
  return new CliError(`install target is busy: ${join(rootDir, slug)}`, EXIT.filesystem, {
    path: join(rootDir, slug),
    next: 'wait for the other SkillHub CLI process to finish and retry'
  })
}

async function removeOwnedLock(lockPath: string, ownershipToken: string): Promise<void> {
  try {
    const lock = JSON.parse(await readFile(lockPath, 'utf-8')) as { ownershipToken?: unknown }
    if (lock.ownershipToken !== ownershipToken) return
    await rm(lockPath, { force: true })
  } catch {
    // Missing or replaced locks are no longer owned by this caller.
  }
}
