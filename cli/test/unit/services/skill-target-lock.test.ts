import { access, mkdir, mkdtemp, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'bun:test'
import { skillTargetLockPath } from '../../../src/services/skill-target-lock'

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

describe('skill target lifecycle lock', () => {
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

    const processes = [0, 1].map(() => Bun.spawn({
      cmd: [bunPath, worker, rootDir, 'demo', acquiredPath, releasePath],
      stdout: 'pipe',
      stderr: 'pipe'
    }))
    await waitForFile(acquiredPath)
    const loserExitCode = await Promise.race([
      ...processes.map(process => process.exited),
      Bun.sleep(5_000).then(() => { throw new Error('timed out waiting for the lock loser') })
    ])
    expect(loserExitCode).toBe(4)
    await writeFile(releasePath, 'release')
    const results = await Promise.all(processes.map(async process => ({
      exitCode: await process.exited,
      stdout: (await new Response(process.stdout).text()).trim(),
      stderr: (await new Response(process.stderr).text()).trim()
    })))

    expect(results.map(result => result.exitCode).sort()).toEqual([0, 4])
    expect(results.filter(result => result.stdout === 'acquired')).toHaveLength(1)
    expect(await exists(lockPath)).toBe(false)
  })
})
