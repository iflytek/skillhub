import { createHash } from 'node:crypto'
import { chmod, lstat, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { lock } from 'proper-lockfile'
import { canonicalizeExistingPath } from '../platform/paths'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

const NO_AUTOMATIC_STALE_RECOVERY_MS = Number.MAX_SAFE_INTEGER

/** Serializes every local lifecycle mutation for one Skill target directory. */
export async function acquireSkillTargetLock(rootDir: string, slug: string): Promise<() => Promise<void>> {
  const lockPath = await skillTargetLockPath(rootDir, slug)
  // proper-lockfile's stale deletion is not serialized. Gate only the acquisition attempt so two
  // recoverers cannot remove and replace the same target lock concurrently.
  const acquisitionGatePath = `${lockPath}.acquire`
  let releaseAcquisitionGate: () => Promise<void>
  try {
    releaseAcquisitionGate = await acquireLockWithoutStaleRecovery(acquisitionGatePath)
  } catch (error) {
    if (hasErrorCode(error, 'ELOCKED')) throw targetBusyError(rootDir, slug)
    throw error
  }

  let releaseTarget: () => Promise<void>
  try {
    releaseTarget = await acquireTargetLock(lockPath)
  } catch (operationError) {
    try {
      await releaseAcquisitionGate()
    } catch (cleanupError) {
      throw new AggregateError([operationError, cleanupError], 'target lock acquisition and gate cleanup both failed')
    }
    if (hasErrorCode(operationError, 'ELOCKED')) throw targetBusyError(rootDir, slug)
    throw operationError
  }

  try {
    await releaseAcquisitionGate()
  } catch (error) {
    await releaseTarget().catch(() => {})
    throw error
  }

  return releaseTarget
}

function acquireTargetLock(lockPath: string): Promise<() => Promise<void>> {
  return lock(lockPath, {
    lockfilePath: lockPath,
    realpath: false,
    stale: 10_000,
    update: 3_000,
    retries: 0
  })
}

function acquireLockWithoutStaleRecovery(lockPath: string): Promise<() => Promise<void>> {
  return lock(lockPath, {
    lockfilePath: lockPath,
    realpath: false,
    stale: NO_AUTOMATIC_STALE_RECOVERY_MS,
    update: 3_000,
    retries: 0
  })
}

function hasErrorCode(error: unknown, code: string): boolean {
  return error instanceof Error && 'code' in error && error.code === code
}

export async function skillTargetLockPath(rootDir: string, slug: string): Promise<string> {
  const canonicalRoot = await canonicalizeExistingPath(resolve(rootDir))
  const target = resolve(canonicalRoot, slug)
  const digest = createHash('sha256').update(target).digest('hex')
  const uid = typeof process.getuid === 'function' ? process.getuid() : 'user'
  const lockDir = join(tmpdir(), `skillhub-cli-target-locks-${uid}`)
  await ensurePrivateLockDir(lockDir)
  return join(lockDir, `${digest}.lock`)
}

interface LockDirectoryDetails {
  isDirectory(): boolean
  isSymbolicLink(): boolean
  uid: number
  mode: number
}

export function assertPrivateLockDir(
  lockDir: string,
  details: LockDirectoryDetails,
  currentUid: number | null
): void {
  if (!details.isDirectory() || details.isSymbolicLink()) {
    throw new Error(`unsafe SkillHub CLI lock directory: ${lockDir}`)
  }
  if (currentUid !== null && details.uid !== currentUid) {
    throw new Error(`SkillHub CLI lock directory is owned by another user: ${lockDir}`)
  }
}

export async function ensurePrivateLockDir(lockDir: string): Promise<void> {
  try {
    await mkdir(lockDir, { mode: 0o700 })
  } catch (error) {
    if (!(error instanceof Error && 'code' in error && error.code === 'EEXIST')) throw error
  }

  const details = await lstat(lockDir)
  const currentUid = typeof process.getuid === 'function' ? process.getuid() : null
  assertPrivateLockDir(lockDir, details, currentUid)
  if (process.platform !== 'win32' && (details.mode & 0o077) !== 0) {
    await chmod(lockDir, 0o700)
  }
}

function targetBusyError(rootDir: string, slug: string): CliError {
  return new CliError(`install target is busy: ${join(rootDir, slug)}`, EXIT.filesystem, {
    path: join(rootDir, slug),
    next: 'wait for the other SkillHub CLI process to finish and retry'
  })
}
