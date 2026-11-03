import { access, chmod, lstat, mkdir, mkdtemp, readdir, rm, symlink, unlink, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'bun:test'
import {
  acquireSkillTargetLock,
  acquireTargetLock,
  assertPrivateLockDir,
  ensurePrivateLockDir,
  skillTargetLockPath,
  orderSkillTargetLocks
} from '../../../src/services/skill-target-lock'

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

async function waitForFile(path: string): Promise<void> {
  for (let attempt = 0; attempt < 500; attempt++) {
    if (await exists(path)) return
    await Bun.sleep(10)
  }
  throw new Error(`timed out waiting for ${path}`)
}

async function waitForExitCount(
  processes: Array<{ exited: Promise<number> }>,
  count: number
): Promise<number[]> {
  const exitCodes: number[] = []
  for (const process of processes) {
    void process.exited.then(exitCode => exitCodes.push(exitCode))
  }
  for (let attempt = 0; attempt < 500; attempt++) {
    if (exitCodes.length >= count) return exitCodes
    await Bun.sleep(10)
  }
  throw new Error(`timed out waiting for ${count} lock contenders to exit`)
}

describe('skill target lifecycle lock', () => {
  test('orders canonical targets identically through aliases and deduplicates the same lock', async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-lock-order-'))
    try {
      const first = join(root, 'first')
      const second = join(root, 'second')
      const alias = join(root, 'alias')
      await mkdir(first)
      await mkdir(second)
      await symlink(first, alias, 'dir')
      const original = await orderSkillTargetLocks([{ rootDir: second }, { rootDir: first }], 'writer')
      const reversedAliases = await orderSkillTargetLocks([{ rootDir: alias }, { rootDir: second }, { rootDir: first }], 'writer')
      expect(reversedAliases).toHaveLength(2)
      const keys = (targets: Array<{ rootDir: string }>) => Promise.all(targets.map(target => skillTargetLockPath(target.rootDir, 'writer')))
      expect(await keys(reversedAliases)).toEqual(await keys(original))
      expect(await keys(original)).toEqual((await keys(original)).sort())
    } finally {
      await rm(root, { recursive: true, force: true })
    }
  })

  test('creates or repairs a private lock root and rejects unsafe roots', async () => {
    const parent = await mkdtemp(join(tmpdir(), 'skillhub-lock-root-'))
    const privateRoot = join(parent, 'private')
    await ensurePrivateLockDir(privateRoot)
    if (process.platform !== 'win32') {
      expect((await lstat(privateRoot)).mode & 0o077).toBe(0)
      await chmod(privateRoot, 0o755)
      await ensurePrivateLockDir(privateRoot)
      expect((await lstat(privateRoot)).mode & 0o077).toBe(0)
    }

    const fileRoot = join(parent, 'file')
    await writeFile(fileRoot, 'keep')
    await expect(ensurePrivateLockDir(fileRoot)).rejects.toThrow('unsafe SkillHub CLI lock directory')
    expect(await Bun.file(fileRoot).text()).toBe('keep')

    const symlinkTarget = join(parent, 'symlink-target')
    const symlinkRoot = join(parent, 'symlink')
    await mkdir(symlinkTarget)
    await symlink(symlinkTarget, symlinkRoot, 'dir')
    await expect(ensurePrivateLockDir(symlinkRoot)).rejects.toThrow('unsafe SkillHub CLI lock directory')
    expect((await lstat(symlinkRoot)).isSymbolicLink()).toBe(true)

    expect(() => assertPrivateLockDir('/foreign', {
      isDirectory: () => true,
      isSymbolicLink: () => false,
      uid: 2000,
      mode: 0o40700
    }, 1000)).toThrow('owned by another user')
  })

  test('simultaneous stale recovery admits exactly one owner across processes', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-target-lock-root-'))
    const lockPath = await skillTargetLockPath(rootDir, 'demo')
    await mkdir(lockPath)
    const staleTime = new Date(Date.now() - 60_000)
    await utimes(lockPath, staleTime, staleTime)
    const acquisitionGatePath = `${lockPath}.acquire`
    const worker = fileURLToPath(new URL('../../helpers/target-lock-worker.ts', import.meta.url))
    const bunPath = (await Bun.which('bun')) ?? process.execPath
    const acquiredPath = join(rootDir, 'acquired')
    const releasePath = join(rootDir, 'release')
    const startPath = join(rootDir, 'start')
    const workerCount = 8
    const readyPaths = Array.from({ length: workerCount }, (_, index) => join(rootDir, `ready-${index}`))

    const processes = readyPaths.map(readyPath => Bun.spawn({
      cmd: [bunPath, worker, rootDir, 'demo', readyPath, startPath, acquiredPath, releasePath],
      stdout: 'pipe',
      stderr: 'pipe'
    }))
    try {
      await Promise.all(readyPaths.map(waitForFile))
      await writeFile(startPath, 'start')
      await waitForFile(acquiredPath)
      const loserExitCodes = await waitForExitCount(processes, workerCount - 1)
      expect(loserExitCodes).toEqual(Array(workerCount - 1).fill(4))
    } finally {
      try {
        await writeFile(releasePath, 'release')
      } finally {
        const exited = await Promise.race([
          Promise.all(processes.map(process => process.exited)).then(() => true),
          Bun.sleep(5_000).then(() => false)
        ])
        if (!exited) {
          for (const process of processes) process.kill()
          await Promise.all(processes.map(process => process.exited))
        }
      }
    }
    const results = await Promise.all(processes.map(async process => ({
      exitCode: await process.exited,
      stdout: (await new Response(process.stdout).text()).trim(),
      stderr: (await new Response(process.stderr).text()).trim()
    })))

    expect(results.map(result => result.exitCode).sort()).toEqual([0, ...Array(workerCount - 1).fill(4)])
    expect(results.filter(result => result.stdout === 'acquired')).toHaveLength(1)
    for (const loser of results.filter(result => result.exitCode === 4)) {
      expect(loser.stderr).toContain('install target is busy')
    }
    expect(await exists(lockPath)).toBe(false)
    expect(await readdir(acquisitionGatePath)).toEqual([])
    const releaseAfterContention = await acquireSkillTargetLock(rootDir, 'demo')
    await releaseAfterContention()
    if (process.platform !== 'win32') {
      expect((await lstat(dirname(lockPath))).mode & 0o077).toBe(0)
    }
  }, 15_000)

  test('does not recover a live acquisition contender solely because its file is old', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-target-live-gate-'))
    const lockPath = await skillTargetLockPath(rootDir, 'demo')
    const acquisitionGatePath = `${lockPath}.acquire`
    await mkdir(acquisitionGatePath)
    const liveContenderPath = join(acquisitionGatePath, `choosing.${process.pid}-suspended`)
    await writeFile(liveContenderPath, '')
    const staleTime = new Date(Date.now() - 60_000)
    await utimes(liveContenderPath, staleTime, staleTime)

    const acquisition = acquireSkillTargetLock(rootDir, 'demo')
    const state = await Promise.race([
      acquisition.then(() => 'acquired', () => 'rejected'),
      Bun.sleep(50).then(() => 'waiting')
    ])
    expect(state).toBe('waiting')
    expect(await exists(liveContenderPath)).toBe(true)
    await unlink(liveContenderPath)
    const release = await acquisition
    await release()
  })

  test('does not pass a live acquisition ticket', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-target-live-ticket-'))
    const lockPath = await skillTargetLockPath(rootDir, 'demo')
    const acquisitionGatePath = `${lockPath}.acquire`
    await mkdir(acquisitionGatePath)
    const liveTicketPath = join(acquisitionGatePath, `ticket.1.${process.pid}-suspended`)
    await writeFile(liveTicketPath, '')

    const acquisition = acquireSkillTargetLock(rootDir, 'demo')
    const state = await Promise.race([
      acquisition.then(() => 'acquired', () => 'rejected'),
      Bun.sleep(50).then(() => 'waiting')
    ])
    expect(state).toBe('waiting')
    expect(await exists(liveTicketPath)).toBe(true)
    await unlink(liveTicketPath)
    const release = await acquisition
    await release()
  })

  test('recovers acquisition contenders whose owner process exited', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-target-dead-gate-'))
    const lockPath = await skillTargetLockPath(rootDir, 'demo')
    await mkdir(lockPath)
    const staleTime = new Date(Date.now() - 60_000)
    await utimes(lockPath, staleTime, staleTime)
    const acquisitionGatePath = `${lockPath}.acquire`
    await mkdir(acquisitionGatePath)
    const bunPath = (await Bun.which('bun')) ?? process.execPath
    const exitedOwner = Bun.spawn({ cmd: [bunPath, '-e', ''], stdout: 'ignore', stderr: 'ignore' })
    const deadPid = exitedOwner.pid
    expect(await exitedOwner.exited).toBe(0)
    await writeFile(join(acquisitionGatePath, `choosing.${deadPid}-abandoned`), '')
    await writeFile(join(acquisitionGatePath, `ticket.1.${deadPid}-abandoned`), '')

    const release = await acquireSkillTargetLock(rootDir, 'demo')
    await release()
    expect(await exists(lockPath)).toBe(false)
    expect(await readdir(acquisitionGatePath)).toEqual([])
  })

  test('keeps one lock identity when a symlink target is removed', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-target-symlink-root-'))
    const linkedDir = await mkdtemp(join(tmpdir(), 'skillhub-target-symlink-value-'))
    const skillDir = join(rootDir, 'demo')
    await symlink(linkedDir, skillDir, process.platform === 'win32' ? 'junction' : 'dir')

    const lockPathBeforeRemoval = await skillTargetLockPath(rootDir, 'demo')
    const release = await acquireSkillTargetLock(rootDir, 'demo')
    try {
      await unlink(skillDir)
      expect(await skillTargetLockPath(rootDir, 'demo')).toBe(lockPathBeforeRemoval)
      await expect(acquireSkillTargetLock(rootDir, 'demo')).rejects.toThrow('install target is busy')
    } finally {
      await release()
    }
    expect(await exists(lockPathBeforeRemoval)).toBe(false)
  })
})


test.each(['EPERM', 'EBUSY'])('Windows target lock retries only a transient mkdir %s on the selected lock', async code => {
  const path = join(tmpdir(), 'skillhub-injected-lock'); let calls = 0
  const release = async () => {}
  expect(await acquireTargetLock(path, async (target, options) => {
    expect(target).toBe(path)
    expect(options).toEqual({ lockfilePath: path, realpath: false, stale: 10000, update: 3000, retries: 0 })
    if (++calls < 3) throw Object.assign(new Error('Sharing failure'), { code, syscall: 'mkdir', path })
    return release
  }, 'win32')).toBe(release)
  expect(calls).toBe(3)
})

test('Windows target lock exhaustion preserves the original mkdir error', async () => {
  const path = join(tmpdir(), 'skillhub-injected-lock')
  const error = Object.assign(new Error('Sharing failure'), { code: 'EPERM', syscall: 'mkdir', path }); let calls = 0
  await expect(acquireTargetLock(path, async () => { calls++; throw error }, 'win32')).rejects.toBe(error)
  expect(calls).toBe(6)
})

test.each([
  ['linux', 'EPERM', 'mkdir', true], ['darwin', 'EBUSY', 'mkdir', true],
  ['win32', 'ELOCKED', 'mkdir', true], ['win32', 'EACCES', 'mkdir', true],
  ['win32', 'EPERM', 'rmdir', true], ['win32', 'EPERM', 'mkdir', false],
] as const)('target lock rejects %s %s %s without broadening retry ownership', async (platform, code, syscall, selectedPath) => {
  const path = join(tmpdir(), 'skillhub-injected-lock')
  const error = Object.assign(new Error('No retry'), { code, syscall, path: selectedPath ? path : path + '-other' }); let calls = 0
  await expect(acquireTargetLock(path, async () => { calls++; throw error }, platform)).rejects.toBe(error)
  expect(calls).toBe(1)
})

test('Windows target lock retries never steal a live proper-lockfile owner', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-lock-live-owner-'))
  const path = join(root, 'target.lock'); const release = await acquireTargetLock(path)
  try {
    const original = await lstat(path)
    await expect(acquireTargetLock(path, undefined, 'win32')).rejects.toMatchObject({ code: 'ELOCKED' })
    expect((await lstat(path)).mtimeMs).toBe(original.mtimeMs)
  } finally { await release(); await rm(root, { recursive: true, force: true }) }
})


test('target acquisition gate excludes another contender throughout Windows retry backoff', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-lock-retry-gate-'))
  let secondEntered = false; let attempts = 0
  let second: Promise<PromiseSettledResult<() => Promise<void>>[]> | undefined
  let release: (() => Promise<void>) | undefined
  try {
    release = await acquireSkillTargetLock(root, 'writer', path => acquireTargetLock(path, async () => {
      if (++attempts === 1) {
        second = Promise.allSettled([acquireSkillTargetLock(root, 'writer', async otherPath => {
          secondEntered = true; return acquireTargetLock(otherPath)
        })])
        throw Object.assign(new Error('Sharing failure'), { code: 'EPERM', syscall: 'mkdir', path })
      }
      expect(secondEntered).toBe(false)
      return acquireTargetLock(path)
    }, 'win32'))
    const result = await second!
    expect(attempts).toBe(2)
    expect(secondEntered).toBe(true)
    expect(result[0]!.status).toBe('rejected')
  } finally {
    if (second) for (const result of await second) if (result.status === 'fulfilled') await result.value()
    await release?.(); await rm(root, { recursive: true, force: true })
  }
})
