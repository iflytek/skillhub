import { expect, test } from 'bun:test'
import { mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { renameAtomicFile } from '../../../src/platform/atomic-rename'

test.each(['EPERM', 'EBUSY'])('Windows atomic rename retries transient %s without removing the destination', async code => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-atomic-rename-'))
  const from = join(root, 'temporary'); const to = join(root, 'inventory')
  try {
    await writeFile(from, 'next'); await writeFile(to, 'original')
    let calls = 0
    await renameAtomicFile(from, to, async (source, destination) => {
      expect(await readFile(to, 'utf8')).toBe('original')
      expect(await readFile(from, 'utf8')).toBe('next')
      if (++calls < 3) throw Object.assign(new Error('Sharing failure'), { code })
      await rename(source, destination)
    }, 'win32')
    expect(calls).toBe(3)
    expect(await readFile(to, 'utf8')).toBe('next')
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each([['linux', 'EPERM'], ['darwin', 'EBUSY'], ['win32', 'EACCES'], ['win32', 'ENOENT']] as const)(
  '%s does not retry %s outside the Windows sharing-failure boundary', async (platform, code) => {
    const error = Object.assign(new Error('Permanent failure'), { code }); let calls = 0
    await expect(renameAtomicFile('source', 'destination', async () => { calls++; throw error }, platform)).rejects.toBe(error)
    expect(calls).toBe(1)
  })
