import { access, chmod, lstat, mkdir, mkdtemp, symlink, unlink, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'bun:test'
import {
  acquireSkillTargetLock,
  assertPrivateLockDir,
  ensurePrivateLockDir,
  skillTargetLockPath
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
    expect(await exists(lockPath)).toBe(false)
    const releaseAfterContention = await acquireSkillTargetLock(rootDir, 'demo')
    await releaseAfterContention()
    if (process.platform !== 'win32') {
      expect((await lstat(dirname(lockPath))).mode & 0o077).toBe(0)
    }
  }, 15_000)

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
