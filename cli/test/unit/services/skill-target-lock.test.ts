import { access, writeFile } from 'node:fs/promises'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { acquireSkillTargetLock, skillTargetLockPath } from '../../../src/services/skill-target-lock'

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

describe('skill target lifecycle lock', () => {
  test('simultaneous stale recovery admits exactly one owner', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'skillhub-target-lock-root-'))
    const lockPath = await skillTargetLockPath(rootDir, 'demo')
    await writeFile(lockPath, JSON.stringify({ pid: 2_147_483_647, createdAt: '2026-09-01T00:00:00Z' }))

    const outcomes = await Promise.allSettled([
      acquireSkillTargetLock(rootDir, 'demo'),
      acquireSkillTargetLock(rootDir, 'demo')
    ])
    const winners = outcomes.filter(
      (outcome): outcome is PromiseFulfilledResult<() => Promise<void>> => outcome.status === 'fulfilled'
    )
    const losers = outcomes.filter(outcome => outcome.status === 'rejected')

    expect(winners).toHaveLength(1)
    expect(losers).toHaveLength(1)
    expect(await exists(lockPath)).toBe(true)
    await winners[0]!.value()
    expect(await exists(lockPath)).toBe(false)
  })
})
