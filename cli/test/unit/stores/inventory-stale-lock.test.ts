import { expect, test } from 'bun:test'
import { access, mkdir, mkdtemp, readFile, readdir, rm, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { acquireAcquisitionGate } from '../../../src/services/skill-target-lock'

async function exists(path: string): Promise<boolean> {
  try { await access(path); return true }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error }
}
async function until(check: () => Promise<boolean>): Promise<void> {
  const deadline = Date.now() + 10000
  while (!await check()) {
    if (Date.now() >= deadline) throw new Error('Inventory contention checkpoint timed out')
    await Bun.sleep(5)
  }
}

test('a delayed stale-lock observer cannot delete a replacement inventory owner', async () => {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-inventory-stale-race-'))
  const lockPath = join(home, '.skillhub/inventory.json.lock')
  await mkdir(lockPath, { recursive: true })
  const stale = new Date(Date.now() - 60000)
  await utimes(lockPath, stale, stale)
  const worker = fileURLToPath(new URL('../../helpers/inventory-stale-lock-worker.ts', import.meta.url))
  const processes: ReturnType<typeof Bun.spawn>[] = []
  const start = (id: string) => processes.push(Bun.spawn({ cmd: [Bun.which('bun') ?? process.execPath, worker, home, id], stdout: 'pipe', stderr: 'pipe' }))
  let secondObservedFresh = false
  let secondEnteredEarly = false
  let checkpointError: unknown
  try {
    start('first')
    await until(() => exists(join(home, 'first-stale')))
    start('second')
    await until(async () => {
      if (await exists(join(home, 'second-stale'))) return true
      try { return (await readdir(`${lockPath}.acquire`)).filter(name => name.startsWith('ticket.')).length === 2 }
      catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error }
    })
    await writeFile(join(home, 'first-resume'), '')
    await until(() => exists(join(home, 'first-entered')))
    await writeFile(join(home, 'second-resume'), '')
    await until(async () => await exists(join(home, 'second-entered')) || await exists(join(home, 'second-fresh')))
    secondObservedFresh = await exists(join(home, 'second-fresh'))
    secondEnteredEarly = await exists(join(home, 'second-entered'))
  } catch (error) { checkpointError = error } finally {
    for (const id of ['first', 'second']) {
      await writeFile(join(home, `${id}-resume`), '')
      await writeFile(join(home, `${id}-commit`), '')
    }
    const finished = await Promise.race([Promise.all(processes.map(child => child.exited)).then(() => true), Bun.sleep(12000).then(() => false)])
    if (!finished) { for (const child of processes) child.kill(); await Promise.all(processes.map(child => child.exited)) }
  }
  try {
    if (checkpointError) throw checkpointError
    expect(await Promise.all(processes.map(child => child.exited))).toEqual([0, 0])
    expect(secondObservedFresh).toBe(true)
    expect(secondEnteredEarly).toBe(false)
    const inventory = JSON.parse(await readFile(join(home, '.skillhub/inventory.json'), 'utf8')) as { records: { slug: string }[] }
    expect(inventory.records.map(item => item.slug).sort()).toEqual(['first', 'second'])
    expect(await exists(lockPath)).toBe(false)
    expect(await readdir(`${lockPath}.acquire`)).toEqual([])
  } finally { await rm(home, { recursive: true, force: true }) }
}, 30000)

for (const kind of ['choosing', 'ticket'] as const) {
  test(`bounded acquisition preserves a live ${kind} owner and removes only its own contender`, async () => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-gate-timeout-'))
    const owner = kind === 'choosing' ? `choosing.${process.pid}-suspended` : `ticket.1.${process.pid}-suspended`
    await writeFile(join(root, owner), '')
    try {
      await expect(acquireAcquisitionGate(root, 50)).rejects.toMatchObject({ code: 'ELOCKED' })
      expect(await readdir(root)).toEqual([owner])
    } finally { await rm(root, { recursive: true, force: true }) }
  })
}

test('inventory acquisition stops waiting for a live suspended gate owner without mutating inventory', async () => {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-inventory-timeout-'))
  const store = new InventoryStore(home)
  const gate = `${store.path}.lock.acquire`
  await mkdir(gate, { recursive: true })
  const owner = `ticket.1.${process.pid}-suspended`
  await writeFile(join(gate, owner), '')
  let inspected = false
  try {
    await expect(store.withLockedSnapshot(async () => { inspected = true })).rejects.toMatchObject({ code: 'ELOCKED' })
    expect(inspected).toBe(false)
    expect(await exists(store.path)).toBe(false)
    expect(await readdir(gate)).toEqual([owner])
    await rm(join(gate, owner))
    await store.withLockedSnapshot(async () => { inspected = true })
    expect(inspected).toBe(true)
    expect(await readdir(gate)).toEqual([])
  } finally { await rm(home, { recursive: true, force: true }) }
}, 15000)
