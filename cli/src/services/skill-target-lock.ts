import { createHash, randomUUID } from 'node:crypto'
import { chmod, lstat, mkdir, readdir, rename, unlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { lock } from 'proper-lockfile'
import { canonicalizeExistingPath } from '../platform/paths'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

const ACQUISITION_GATE_WAIT_MS = 1_000
const ACQUISITION_GATE_MAX_POLL_MS = 100

/** Serializes every local lifecycle mutation for one Skill target directory. */
export async function acquireSkillTargetLock(rootDir: string, slug: string): Promise<() => Promise<void>> {
  const lockPath = await skillTargetLockPath(rootDir, slug)
  // proper-lockfile's stale deletion is not serialized. Gate only the acquisition attempt so two
  // recoverers cannot remove and replace the same target lock concurrently.
  const acquisitionGatePath = `${lockPath}.acquire`
  let releaseAcquisitionGate: () => Promise<void>
  try {
    releaseAcquisitionGate = await acquireAcquisitionGate(acquisitionGatePath)
  } catch (error) {
    if (hasErrorCode(error, 'EEXIST')) throw targetBusyError(rootDir, slug)
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
  } catch (gateCleanupError) {
    try {
      await releaseTarget()
    } catch (targetCleanupError) {
      throw new AggregateError([gateCleanupError, targetCleanupError], 'target and acquisition gate cleanup both failed')
    }
    throw gateCleanupError
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

async function acquireAcquisitionGate(gatePath: string): Promise<() => Promise<void>> {
  await ensureAcquisitionGateDirectory(gatePath)
  // A per-target Lamport bakery queue avoids deleting a shared stale gate. Every removable path
  // contains a nonce and is owned by one PID, so crash recovery cannot unlink a replacement owner.
  const contenderId = `${process.pid}-${randomUUID()}`
  const choosingPath = join(gatePath, `choosing.${contenderId}`)
  let ticketPath: string | null = null
  let ticket: number | null = null

  await writeFile(choosingPath, '', { flag: 'wx', mode: 0o600 })
  try {
    const { tickets } = await readGateState(gatePath, contenderId)
    ticket = Math.max(0, ...tickets.map(contender => contender.ticket)) + 1
    ticketPath = join(gatePath, `ticket.${ticket}.${contenderId}`)
    await rename(choosingPath, ticketPath)

    const contenders = await waitForChoosingContenders(gatePath, contenderId)
    if (contenders.some(contender => compareContenders(contender, { id: contenderId, ticket: ticket! }) < 0)) {
      throw Object.assign(new Error('Acquisition gate is already being held'), { code: 'EEXIST' })
    }
  } catch (operationError) {
    const cleanupErrors = await removeContenderFiles(choosingPath, ...(ticketPath === null ? [] : [ticketPath]))
    if (cleanupErrors.length > 0) {
      throw new AggregateError([operationError, ...cleanupErrors], 'acquisition gate attempt and cleanup both failed')
    }
    throw operationError
  }

  return async () => {
    try {
      await unlink(ticketPath!)
    } catch (error) {
      if (hasErrorCode(error, 'ENOENT')) throw compromisedGateError(ticketPath!)
      throw error
    }
  }
}

async function ensureAcquisitionGateDirectory(gatePath: string): Promise<void> {
  try {
    await mkdir(gatePath, { mode: 0o700 })
  } catch (error) {
    if (!hasErrorCode(error, 'EEXIST')) throw error
  }
  const details = await lstat(gatePath)
  if (!details.isDirectory() || details.isSymbolicLink()) {
    throw new Error(`unsafe SkillHub CLI acquisition gate directory: ${gatePath}`)
  }
}

interface AcquisitionContender {
  id: string
  ticket: number
}

interface AcquisitionGateState {
  tickets: AcquisitionContender[]
  hasLiveChoosing: boolean
}

async function readGateState(gatePath: string, contenderId: string): Promise<AcquisitionGateState> {
  const entries = await readdir(gatePath)
  const tickets: AcquisitionContender[] = []
  let hasLiveChoosing = false
  for (const entry of entries) {
    const path = join(gatePath, entry)
    const ticketMatch = /^ticket\.(\d+)\.(\d+)-([^.]+)$/.exec(entry)
    if (ticketMatch) {
      const ticket = Number(ticketMatch[1])
      const pid = Number(ticketMatch[2])
      if (!isProcessAlive(pid)) {
        await unlinkIfPresent(path)
        continue
      }
      if (!Number.isSafeInteger(ticket) || ticket < 1) throw compromisedGateError(path)
      tickets.push({ id: `${ticketMatch[2]}-${ticketMatch[3]}`, ticket })
      continue
    }
    const choosingMatch = /^choosing\.(\d+)-([^.]+)$/.exec(entry)
    if (choosingMatch && `${choosingMatch[1]}-${choosingMatch[2]}` !== contenderId) {
      if (!isProcessAlive(Number(choosingMatch[1]))) {
        await unlinkIfPresent(path)
      } else {
        hasLiveChoosing = true
      }
    }
  }
  return { tickets, hasLiveChoosing }
}

async function waitForChoosingContenders(
  gatePath: string,
  contenderId: string
): Promise<AcquisitionContender[]> {
  const deadline = Date.now() + ACQUISITION_GATE_WAIT_MS
  let delayMs = 5
  do {
    const state = await readGateState(gatePath, contenderId)
    if (!state.hasLiveChoosing) return state.tickets
    await new Promise(resolve => setTimeout(resolve, delayMs))
    delayMs = Math.min(delayMs * 2, ACQUISITION_GATE_MAX_POLL_MS)
  } while (Date.now() < deadline)
  throw Object.assign(new Error('Acquisition gate contender did not finish choosing'), { code: 'EEXIST' })
}

function compareContenders(left: AcquisitionContender, right: AcquisitionContender): number {
  if (left.ticket !== right.ticket) return left.ticket < right.ticket ? -1 : 1
  if (left.id === right.id) return 0
  return left.id < right.id ? -1 : 1
}

function isProcessAlive(pid: number): boolean {
  try {
    process.kill(pid, 0)
    return true
  } catch (error) {
    return !hasErrorCode(error, 'ESRCH')
  }
}

async function removeContenderFiles(...paths: string[]): Promise<Error[]> {
  const errors: Error[] = []
  for (const path of paths) {
    try {
      await unlink(path)
    } catch (error) {
      if (!hasErrorCode(error, 'ENOENT')) errors.push(error instanceof Error ? error : new Error(String(error)))
    }
  }
  return errors
}

async function unlinkIfPresent(path: string): Promise<void> {
  try {
    await unlink(path)
  } catch (error) {
    if (!hasErrorCode(error, 'ENOENT')) throw error
  }
}

function compromisedGateError(gatePath: string): Error {
  return Object.assign(new Error(`SkillHub CLI acquisition gate was replaced: ${gatePath}`), {
    code: 'ECOMPROMISED'
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
