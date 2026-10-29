import { expect, test } from 'bun:test'
import { randomUUID } from 'node:crypto'
import { cp, mkdtemp, mkdir, readFile, realpath, rename, rm, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import { OperationStore } from '../../../src/integrations/operation-store'
import { executeNativeToggle, inspectToggle } from '../../../src/integrations/native-toggle'

async function fixture() {
  const home = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-toggle-')))
  const root = join(home, 'skills')
  const path = join(root, 'writer')
  await mkdir(join(path, '.skillhub'), { recursive: true })
  await writeFile(join(path, 'SKILL.md'), '# Original\n')
  await writeFile(join(path, 'resource.txt'), 'keep bytes')
  const selected = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0' }
  const snapshot = await snapshotSkillDirectory(path)
  const target = { agent: 'dsh', rootDir: root, installDir: path, installedAt: '2026-10-27', version: selected.version, fingerprint: snapshot.fingerprint }
  const inventory = new InventoryStore(home)
  await inventory.writeAtomic({ items: [{ registry: selected.registry, namespace: selected.namespace, slug: selected.slug, targets: [target] }] })
  await writeFile(join(path, '.skillhub/metadata.json'), JSON.stringify({ ...selected, ...snapshot, agent: 'dsh', installedAt: target.installedAt }))
  const stateDirectory = join(home, 'operations')
  const runtime = await openNativeRuntime({ inventoryHome: home, stateDirectory })
  return { home, path, selected, inventory, runtime, stateDirectory, target: { root, scope: 'user' as const } }
}

test('offline toggles rename only entry, preserve bytes, reject stale plans, and uninstall disabled', async () => {
  const f = await fixture()
  try {
    const metadata = await readFile(join(f.path, '.skillhub/metadata.json'))
    for (const desired of ['disabled', 'enabled', 'disabled'] as const) {
      const baseline = await f.runtime.inspectToggle(f.selected, f.target, desired)
      if (baseline.status !== 'ready') throw new Error(JSON.stringify(baseline))
      const operation = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner',
        mode: 'new', requestKey: randomUUID(), parameters: { desired } }, async () => ({
        namespace: f.selected.namespace, slug: f.selected.slug, version: f.selected.version, kind: desired === 'disabled' ? 'disable' : 'enable', targetLabel: 'User', targetPaths: [f.path],
        context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
      }))
      const result = await f.runtime.executeToggle({ operationId: operation!.operationId, ownerId: 'owner', baseline, authorize: async () => {} })
      expect(result.outcome).toBe('committed')
      const disk = JSON.parse(await readFile(f.inventory.path, 'utf8'))
      expect(disk.schemaVersion).toBe(2)
      expect(disk.records[0].targets[0]).not.toHaveProperty('installationState')
      expect((await f.runtime.getOperation(operation!.operationId, 'owner'))?.files).toBe(desired)
      expect(await readFile(join(f.path, desired === 'disabled' ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('# Original\n')
      expect(await readFile(join(f.path, '.skillhub/metadata.json'))).toEqual(metadata)
      expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('keep bytes')
      expect((await f.runtime.listInstalled(f.selected.registry, [f.target])).items[0]?.installationState).toBe(desired)
      expect((await f.runtime.inspectToggle(f.selected, f.target, desired)).status).toBe('unchanged')
    }
    const removal = await f.runtime.inspectRemoval(f.selected, f.target)
    expect(removal.status).toBe('ready')
    if (removal.status !== 'ready') return
    const operation = await f.runtime.acceptRemoval({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { remove: true } }, async () => ({
      namespace: f.selected.namespace, slug: f.selected.slug, version: f.selected.version, kind: removal.kind, targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    expect((await f.runtime.executeRemoval({ operationId: operation!.operationId, ownerId: 'owner', baseline: removal, authorize: async () => {} })).outcome).toBe('committed')
    expect((await f.inventory.read()).items).toEqual([])
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test.each(['modified', 'alias-collision', 'shared'] as const)('toggle blocks %s before a write', async mode => {
  const f = await fixture()
  try {
    if (mode === 'modified') await writeFile(join(f.path, 'resource.txt'), 'changed')
    if (mode === 'alias-collision') await writeFile(join(f.path, 'SKILL.md.disabled'), 'authored')
    if (mode === 'shared') await f.inventory.mutateAtomic(value => { value.items[0]!.targets[0]!.installedBy = ['direct', 'suite:demo'] })
    expect((await f.runtime.inspectToggle(f.selected, f.target, 'disabled')).status).toBe('blocked')
    expect(await readFile(join(f.path, 'SKILL.md'), 'utf8')).toBe('# Original\n')
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test.each(['cas-before', 'cas-after', 'intent', 'committed-log'] as const)('toggle reconciles failure at %s', async mode => {
  const f = await fixture()
  try {
    const operations = await OperationStore.open(f.stateDirectory)
    const baseline = await inspectToggle(f.inventory, f.selected, f.target, 'disabled')
    if (baseline.status !== 'ready') throw new Error(JSON.stringify(baseline))
    const accepted = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { mode } }, async () => ({
      namespace: f.selected.namespace, slug: f.selected.slug, version: f.selected.version, kind: 'disable', targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    const append = operations.recordToggle.bind(operations)
    let once = true
    operations.recordToggle = async (...args) => {
      if (once && (mode === 'intent' && args[3].stage === 'intent' || mode !== 'intent' && args[3].stage === 'committed')) {
        once = false
        if (mode === 'cas-after' || mode === 'committed-log') await append(...args)
        throw new Error('Injected journal failure')
      }
      return append(...args)
    }
    const run = () => executeNativeToggle(operations, f.inventory, { operationId: accepted!.operationId, ownerId: 'owner', baseline, authorize: async () => {} })
    if (mode === 'cas-after' || mode === 'committed-log') expect((await run()).outcome).toBe('committed')
    else await expect(run()).rejects.toThrow('Injected')
    const state = (await f.inventory.read()).items[0]!.targets[0]!.installationState
    expect(state).toBe(mode === 'cas-after' || mode === 'committed-log' ? 'disabled' : 'enabled')
    expect(await readFile(join(f.path, state === 'disabled' ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('# Original\n')
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test.each([false, true])('restart recovers a renamed entry without overwriting a new occupant: conflict=%s', async conflict => {
  const f = await fixture()
  try {
    const baseline = await f.runtime.inspectToggle(f.selected, f.target, 'disabled')
    if (baseline.status !== 'ready') throw new Error(JSON.stringify(baseline))
    const request = { hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new' as const, requestKey: randomUUID(), parameters: { disable: true } }
    const accepted = await f.runtime.acceptToggle(request, async () => ({
      namespace: 'demo', slug: 'writer', version: '1.0.0', kind: 'disable', targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    expect((await f.runtime.acceptToggle(request, async () => { throw new Error('Must replay') }))?.operationId).toBe(accepted!.operationId)
    const operations = await OperationStore.open(f.stateDirectory)
    const append = operations.recordToggle.bind(operations)
    operations.recordToggle = async (...args) => {
      if (args[3].stage === 'moved') throw new Error('Crash window after rename')
      return append(...args)
    }
    await expect(executeNativeToggle(operations, f.inventory, { operationId: accepted!.operationId, ownerId: 'owner', baseline,
      authorize: async (_paths, phase) => { if (phase === 'rollback') throw new Error('Recovery unavailable') } })).rejects.toThrow('Crash window')
    expect((await f.inventory.read()).items[0]!.targets[0]!.installationState).toBe('disabled')
    expect(await readFile(join(f.path, 'SKILL.md.disabled'), 'utf8')).toBe('# Original\n')
    if (conflict) await writeFile(join(f.path, 'SKILL.md'), 'New occupant')
    const restarted = await openNativeRuntime({ inventoryHome: f.home, stateDirectory: f.stateDirectory })
    expect((await restarted.listPending()).map(record => record.operationId)).toContain(accepted!.operationId)
    const result = await restarted.recover(accepted!.operationId, 'owner', [f.path], async () => {})
    expect(result.outcome).toBe(conflict ? 'unknown' : 'restored')
    expect(await readFile(join(f.path, 'SKILL.md'), 'utf8')).toBe(conflict ? 'New occupant' : '# Original\n')
    if (conflict) expect(await readFile(join(f.path, 'SKILL.md.disabled'), 'utf8')).toBe('# Original\n')
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test('killed toggle writers recover intent, renamed and committed boundaries', async () => {
  const fixtures = await Promise.all(['intent', 'linked', 'moved', 'restore-linked', 'committed'].map(async boundary => {
    const f = await fixture()
    const accepted = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { boundary } }, async () => ({
      namespace: 'demo', slug: 'writer', version: '1.0.0', kind: 'disable', targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    return { ...f, boundary, operationId: accepted!.operationId }
  }))
  try {
    const crashes = await Promise.allSettled(fixtures.map(async f => {
      const readyFile = join(f.home, 'crash-ready')
      const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/toggle-crash-child.ts', import.meta.url)), f.home, f.operationId, f.boundary, readyFile],
        { stdout: 'pipe', stderr: 'pipe' })
      const waitForExit = async (exited: Promise<number>) => {
        let timer: ReturnType<typeof setTimeout> | undefined
        try {
          return await Promise.race([exited, new Promise<never>((_, reject) => {
            timer = setTimeout(() => reject(new Error('Owned crash process did not exit within 10 seconds')), 10000)
          })])
        } finally { clearTimeout(timer) }
      }
      const terminate = async () => {
        if (child.exitCode !== null) return
        if (process.platform === 'win32') {
          const killer = Bun.spawn(['taskkill.exe', '/PID', String(child.pid), '/T', '/F'], { stdout: 'ignore', stderr: 'pipe' })
          try { expect(await waitForExit(killer.exited)).toBe(0) } finally { if (killer.exitCode === null) killer.kill() }
        } else child.kill('SIGKILL')
      }
      try {
        const deadline = Date.now() + 20000
        let ready = false
        while (Date.now() < deadline && child.exitCode === null) {
          try { ready = (await readFile(readyFile, 'utf8')) === f.boundary } catch (error) {
            if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
          }
          if (ready) break
          await Bun.sleep(25)
        }
        expect(ready).toBe(true)
        await terminate()
        expect(await waitForExit(child.exited)).not.toBe(0)
        expect(await new Response(child.stderr).text()).toBe('')
      } finally {
        // Cleanup uses only this child handle even if taskkill failed; it is not a passing crash assertion.
        if (child.exitCode === null) child.kill()
        await waitForExit(child.exited)
      }
    }))
    const failures = crashes.filter((result): result is PromiseRejectedResult => result.status === 'rejected')
    if (failures.length) throw new AggregateError(failures.map(result => result.reason), 'Crash fixtures failed after all owned children settled')
    // Reverse-link recovery holds the inventory lease; its stale timeout is 30 seconds.
    await Bun.sleep(31_500)
    for (const f of fixtures) {
      const restarted = await openNativeRuntime({ inventoryHome: f.home, stateDirectory: f.stateDirectory })
      const result = await restarted.recover(f.operationId, 'owner', [f.path], async () => {})
      expect(result.outcome).toBe(f.boundary === 'committed' ? 'committed' : 'restored')
      const state = (await f.inventory.read()).items[0]!.targets[0]!.installationState
      expect(state).toBe(f.boundary === 'committed' ? 'disabled' : 'enabled')
      expect(await readFile(join(f.path, state === 'disabled' ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('# Original\n')
    }
  } finally { await Promise.all(fixtures.map(f => rm(f.home, { recursive: true, force: true }))) }
}, 90_000)

test.each(['forward', 'rollback'] as const)('exclusive entry creation preserves a competitor appearing after preflight: %s', async direction => {
  const f = await fixture()
  try {
    const baseline = await f.runtime.inspectToggle(f.selected, f.target, 'disabled')
    if (baseline.status !== 'ready') throw new Error(JSON.stringify(baseline))
    const accepted = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { direction } }, async () => ({
      namespace: 'demo', slug: 'writer', version: '1.0.0', kind: 'disable', targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    const operations = await OperationStore.open(f.stateDirectory)
    const append = operations.recordToggle.bind(operations)
    operations.recordToggle = async (...args) => {
      const saved = await append(...args)
      if (direction === 'forward' && args[3].stage === 'intent') await writeFile(args[3].to, 'Competing entry', { flag: 'wx' })
      if (direction === 'rollback' && args[3].stage === 'restoring') await writeFile(args[3].from, 'Competing entry', { flag: 'wx' })
      return saved
    }
    if (direction === 'rollback') {
      const write = operations.recordToggle.bind(operations)
      operations.recordToggle = async (...args) => { if (args[3].stage === 'committed') throw new Error('Commit denied'); return write(...args) }
    }
    await expect(executeNativeToggle(operations, f.inventory, { operationId: accepted!.operationId, ownerId: 'owner', baseline, authorize: async () => {} })).rejects.toThrow()
    expect(await readFile(join(f.path, direction === 'forward' ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('Competing entry')
    expect(await readFile(join(f.path, direction === 'forward' ? 'SKILL.md' : 'SKILL.md.disabled'), 'utf8')).toBe('# Original\n')
    expect((await f.inventory.read()).items[0]!.targets[0]!.installationState).toBeUndefined()
    expect((await f.runtime.getOperation(accepted!.operationId, 'owner'))?.phase).toBe('recovery-required')
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test('independent toggle transactions preserve both targets in shared inventory', async () => {
  const f = await fixture()
  try {
    const other = { scope: 'project' as const, root: join(f.home, 'project') }
    const otherPath = join(other.root, 'writer')
    await mkdir(other.root)
    await cp(f.path, otherPath, { recursive: true })
    await f.inventory.mutateAtomic(inventory => {
      const item = inventory.items[0]!
      item.targets.push({ ...item.targets[0]!, rootDir: other.root, installDir: otherPath })
    })
    const requests = await Promise.all([f.target, other].map(async target => {
      const baseline = await f.runtime.inspectToggle(f.selected, target, 'disabled')
      if (baseline.status !== 'ready') throw new Error(JSON.stringify(baseline))
      const accepted = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { scope: target.scope } }, async () => ({
        namespace: 'demo', slug: 'writer', version: '1.0.0', kind: 'disable', targetLabel: target.scope, targetPaths: [baseline.target.path],
        context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
      }))
      return { operationId: accepted!.operationId, ownerId: 'owner', baseline, authorize: async () => {} }
    }))
    expect((await Promise.all(requests.map(request => f.runtime.executeToggle(request)))).map(result => result.outcome)).toEqual(['committed', 'committed'])
    expect((await f.inventory.read()).items[0]!.targets.map(target => target.installationState)).toEqual(['disabled', 'disabled'])
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test.each(['enabled', 'disabled'] as const)('existing v3 recovery respects persisted %s commit evidence', async storedState => {
  const f = await fixture()
  try {
    const baseline = await f.runtime.inspectToggle(f.selected, f.target, 'disabled')
    if (baseline.status !== 'ready') throw new Error('Missing baseline')
    const accepted = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { storedState } }, async () => ({
      namespace: 'demo', slug: 'writer', version: '1.0.0', kind: 'disable', targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    const operations = await OperationStore.open(f.stateDirectory)
    const append = operations.recordToggle.bind(operations)
    operations.recordToggle = async (...args) => {
      const saved = await append(...args)
      if (args[3].stage === 'moved') throw new Error('Lost legacy response')
      return saved
    }
    await expect(executeNativeToggle(operations, f.inventory, { operationId: accepted!.operationId, ownerId: 'owner', baseline,
      authorize: async (_paths, phase) => { if (phase === 'rollback') throw new Error('Recovery unavailable') } })).rejects.toThrow()
    const inventory = await f.inventory.read()
    inventory.items[0]!.targets[0]!.installationState = storedState
    await writeFile(f.inventory.path, JSON.stringify({ schemaVersion: 3, records: inventory.items }))
    const restarted = await openNativeRuntime({ inventoryHome: f.home, stateDirectory: f.stateDirectory })
    expect((await restarted.recover(accepted!.operationId, 'owner', [f.path], async () => {})).outcome).toBe(storedState === 'disabled' ? 'committed' : 'restored')
    expect((await f.inventory.read()).items[0]!.targets[0]!.installationState).toBe(storedState)
    await f.inventory.ensureWritable()
    expect(await f.inventory.diskVersion()).toBe(2)
  } finally { await rm(f.home, { recursive: true, force: true }) }
})

test('recovery never overwrites a legitimate replacement after an interrupted enable', async () => {
  const f = await fixture()
  try {
    await rename(join(f.path, 'SKILL.md'), join(f.path, 'SKILL.md.disabled'))
    const baseline = await f.runtime.inspectToggle(f.selected, f.target, 'enabled')
    if (baseline.status !== 'ready') throw new Error('Missing disabled baseline')
    const accepted = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { enable: true } }, async () => ({
      namespace: 'demo', slug: 'writer', version: '1.0.0', kind: 'enable', targetLabel: 'User', targetPaths: [f.path],
      context: { sourceIdentity: f.selected.registry, sourceLabel: 'Registry', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
    }))
    const operations = await OperationStore.open(f.stateDirectory)
    const append = operations.recordToggle.bind(operations)
    operations.recordToggle = async (...args) => {
      const saved = await append(...args)
      if (args[3].stage === 'moved') throw new Error('Interrupted enable')
      return saved
    }
    await expect(executeNativeToggle(operations, f.inventory, { operationId: accepted!.operationId, ownerId: 'owner', baseline,
      authorize: async (_paths, phase) => { if (phase === 'rollback') throw new Error('Recovery unavailable') } })).rejects.toThrow()
    await writeFile(join(f.path, 'SKILL.md'), '# Legitimate replacement\n')
    const snapshot = await snapshotSkillDirectory(f.path)
    const target = { ...baseline.inventoryItem.targets[0]!, version: '2.0.0', installedAt: '2026-10-29', fingerprint: snapshot.fingerprint, installationState: 'enabled' as const }
    await writeFile(join(f.path, '.skillhub/metadata.json'), JSON.stringify({ ...f.selected, version: target.version, ...snapshot, agent: 'dsh', installedAt: target.installedAt }))
    await f.inventory.writeAtomic({ items: [{ ...baseline.inventoryItem, targets: [target] }] })
    const paths = [f.inventory.path, join(f.path, 'SKILL.md'), join(f.path, '.skillhub/metadata.json'), join(f.path, 'resource.txt')]
    const before = await Promise.all(paths.map(path => readFile(path)))
    const restarted = await openNativeRuntime({ inventoryHome: f.home, stateDirectory: f.stateDirectory })
    expect((await restarted.recover(accepted!.operationId, 'owner', [f.path], async () => {})).outcome).toBe('unknown')
    expect(await Promise.all(paths.map(path => readFile(path)))).toEqual(before)
  } finally { await rm(f.home, { recursive: true, force: true }) }
})
