import { planSkillUpgrades, executeSkillUpgradePlan } from '../../../src/services/upgrade-service'
import { removeLocalSkill } from '../../../src/services/remove-service'
import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { mkdtemp, mkdir, readFile, readdir, realpath, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { zipSync } from 'fflate'
import { InventoryStore, type Inventory } from '../../../src/stores/inventory-store'
import { installSkill } from '../../../src/services/install-service'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import { captureInstallInventory } from '../../../src/services/inventory-cas'

async function fixture() {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-v2-')))
  const store = new InventoryStore(join(root, 'home'))
  const roots = ['user', 'project-a', 'project-b'].map((label, i) => ({ root: join(root, label), scope: i ? 'project' as const : 'user' as const }))
  const releases = ['1.0.0', '2.0.0', '3.0.0'].map((version, i) => {
    const content = `# Version ${version}`
    const fingerprint = 'sha256:' + createHash('sha256').update(`SKILL.md:${createHash('sha256').update(content).digest('hex')}\n`).digest('hex')
    return { version, versionId: i + 1, content, fingerprint }
  })
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const url = new URL(request.url)
    const release = releases.find(item => item.version === url.searchParams.get('version')) ?? releases[2]!
    if (url.pathname.endsWith('/resolve')) return Response.json({ data: { namespace: 'team', slug: 'writer', ...release, downloadUrl: `/download?version=${release.version}` } })
    return new Response(new Uint8Array(zipSync({ 'SKILL.md': new TextEncoder().encode(release.content) })).buffer)
  } })
  const identity = { registry: server.url.origin, namespace: 'team', slug: 'writer' }
  const install = (index: number, version: string) => installSkill({ ...identity, version, force: true, inventoryStore: store,
    targets: [{ agent: 'dsh', rootDir: roots[index]!.root, scope: roots[index]!.scope, source: 'explicit' }] })
  const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
  const prepare = async (index: number) => {
    const baseline = await runtime.inspectUpgrade({ ...identity, version: '1.0.0' }, [roots[index]!])
    if (baseline.status !== 'ready') throw new Error(baseline.reason)
    const candidate = releases[2]!
    const operation = await runtime.acceptUpgrade({ hostInstanceId: runtime.hostInstanceId, ownerId: 'session', requestKey: randomUUID(), mode: 'new', parameters: { plan: randomUUID() } }, async () => ({ namespace: 'team', slug: 'writer', version: candidate.version, targetPaths: baseline.targets.map(target => target.path), targetLabel: roots[index]!.scope,
      context: { sourceIdentity: identity.registry, sourceLabel: 'Fixture', sourceRevision: 'source', hostLabel: 'Host', initiatedFrom: 'plaza' } }))
    return { ...identity, ...candidate, baseline, operationId: operation!.operationId, ownerId: 'session', authorize: async () => {}, validateStaged: async () => {} }
  }
  return { root, roots, store, install, runtime, prepare, identity, close: async () => { await server.stop(true); await rm(root, { recursive: true, force: true }) } }
}

test('v1 read preserves bytes and migration expands ownership without inventing fingerprints', async () => {
  const f = await fixture()
  try {
    const legacy = { items: [{ ...f.identity, version: '1.0.0', installedBy: ['suite:@team/set@1'], targets: f.roots.map(target => ({ agent: 'dsh', rootDir: target.root, installDir: join(target.root, 'writer'), installedAt: '2026-10-01', ...(target.scope === 'user' ? { installedBy: ['direct'] } : {}) })) }], suites: [] }
    const bytes = JSON.stringify(legacy, null, 3)
    await mkdir(dirname(f.store.path), { recursive: true }); await writeFile(f.store.path, bytes)
    const read = await f.store.read()
    expect(await readFile(f.store.path, 'utf8')).toBe(bytes)
    expect(read.items[0]).not.toHaveProperty('version')
    expect(read.items[0]!.targets.map(target => target.version)).toEqual(['1.0.0', '1.0.0', '1.0.0'])
    expect(read.items[0]!.targets[1]!.installedBy).toEqual(['suite:@team/set@1'])
    expect(read.items[0]!.targets[0]).not.toHaveProperty('fingerprint')
    await f.store.ensureWritable(); await f.store.ensureWritable()
    const backups = (await readdir(dirname(f.store.path))).filter(name => name.includes('.backup.json'))
    expect(backups).toHaveLength(1)
    expect(await readFile(join(dirname(f.store.path), backups[0]!), 'utf8')).toBe(bytes)
    const disk = JSON.parse(await readFile(f.store.path, 'utf8'))
    expect(disk.schemaVersion).toBe(2); expect(disk).not.toHaveProperty('items'); expect(disk.records[0]).not.toHaveProperty('version')
    expect(await f.store.read()).toEqual(read)
  } finally { await f.close() }
})

test.each(['before', 'after'] as const)('migration %s rename failure leaves one authority', async mode => {
  const f = await fixture()
  try {
    await mkdir(dirname(f.store.path), { recursive: true }); await writeFile(f.store.path, JSON.stringify({ items: [] }))
    class FailingStore extends InventoryStore { protected override async writeUnderLock(value: Inventory) {
      if (mode === 'after') await super.writeUnderLock(value)
      throw new Error('injected rename response')
    } }
    const store = new FailingStore(join(f.root, 'home'))
    if (mode === 'before') await expect(store.ensureWritable()).rejects.toThrow('injected')
    else await store.ensureWritable()
    expect(await store.diskVersion()).toBe(mode === 'before' ? 1 : 2)
    expect(await store.read()).toEqual({ items: [] })
    expect((await readdir(dirname(store.path))).filter(name => name.includes('.backup.json'))).toHaveLength(1)
  } finally { await f.close() }
})

test('future schema blocks install before target filesystem mutation', async () => {
  const f = await fixture()
  try {
    await mkdir(dirname(f.store.path), { recursive: true }); await writeFile(f.store.path, JSON.stringify({ schemaVersion: 99, records: [] }))
    await expect(f.install(0, '1.0.0')).rejects.toThrow()
    expect(await readdir(f.root)).not.toContain('user')
  } finally { await f.close() }
})

test('selected native upgrade preserves modified and suite-owned siblings', async () => {
  const f = await fixture()
  try {
    for (let i = 0; i < 3; i++) await f.install(i, '1.0.0')
    await writeFile(join(f.roots[0]!.root, 'writer/SKILL.md'), 'user custom edit')
    await f.store.mutateAtomic(value => { value.items[0]!.targets[2]!.installedBy = ['suite:@team/set@1'] })
    const before = await f.store.read()
    const originalMetadata = await readFile(join(f.roots[0]!.root, 'writer/.skillhub/metadata.json'), 'utf8')
    const request = await f.prepare(1)
    expect(request.baseline.inventoryItem.targets).toHaveLength(1)
    expect(await f.runtime.executeUpgrade(request)).toMatchObject({ outcome: 'committed' })
    const after = await f.store.read()
    for (const index of [0, 2]) expect(after.items[0]!.targets.find(target => target.rootDir === f.roots[index]!.root)).toEqual(before.items[0]!.targets[index])
    expect(await readFile(join(f.roots[0]!.root, 'writer/SKILL.md'), 'utf8')).toBe('user custom edit')
    expect(await readFile(join(f.roots[0]!.root, 'writer/.skillhub/metadata.json'), 'utf8')).toBe(originalMetadata)
    expect(after.items[0]!.targets.find(target => target.rootDir === f.roots[1]!.root)?.version).toBe('3.0.0')
  } finally { await f.close() }
})

test('independent prepared upgrades commit without losing siblings', async () => {
  const f = await fixture()
  try {
    for (let i = 0; i < 3; i++) await f.install(i, '1.0.0')
    const first = await f.prepare(0); const second = await f.prepare(1)
    expect((await Promise.all([f.runtime.executeUpgrade(first), f.runtime.executeUpgrade(second)])).map(result => result.outcome)).toEqual(['committed', 'committed'])
    const targets = (await f.store.read()).items[0]!.targets
    expect(f.roots.map(root => targets.find(target => target.rootDir === root.root)?.version)).toEqual(['3.0.0', '3.0.0', '1.0.0'])
  } finally { await f.close() }
})

test('selected CAS detects conflicting owners and ignores sibling release changes', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0'); await f.install(1, '1.0.0')
    const before = await f.store.read(); const paths = new Set([join(f.roots[0]!.root, 'writer')])
    const baseline = await captureInstallInventory(before, f.identity, paths)
    before.items[0]!.targets[1]!.version = '2.0.0'
    expect(await captureInstallInventory(before, f.identity, paths)).toBe(baseline)
    before.items.push({ ...f.identity, registry: 'https://other.example', targets: [before.items[0]!.targets[0]!] })
    expect(await captureInstallInventory(before, f.identity, paths)).not.toBe(baseline)
  } finally { await f.close() }
})

test('v2 recovery preserves a sibling advanced after the failed selected update', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0'); await f.install(1, '1.0.0')
    const request = await f.prepare(0)
    let replacements = 0
    await expect(f.runtime.executeUpgrade({ ...request, authorize: async (_paths, phase) => {
      if (phase === 'replace' && ++replacements === 2) throw new Error('injected replacement failure')
    } })).rejects.toThrow('injected replacement failure')
    await f.install(1, '2.0.0')
    const sibling = (await f.store.read()).items[0]!.targets.find(target => target.rootDir === f.roots[1]!.root)
    expect(await f.runtime.recover(request.operationId, 'session', request.baseline.targets.map(target => target.path), async () => {})).toMatchObject({ outcome: 'restored' })
    expect((await f.store.read()).items[0]!.targets.find(target => target.rootDir === f.roots[1]!.root)).toEqual(sibling)
    expect(await readFile(join(f.roots[1]!.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 2.0.0')
    expect(await readFile(join(f.roots[0]!.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
  } finally { await f.close() }
})

function legacySnapshot(inventory: Inventory) {
  return { ...inventory, items: inventory.items.map(item => ({ ...item, version: item.targets[0]!.version,
    ...(item.targets[0]!.fingerprint ? { fingerprint: item.targets[0]!.fingerprint } : {}),
    targets: item.targets.map(({ version, fingerprint, installationState, ...target }) => { void version; void fingerprint; void installationState; return target }) })) }
}

test('pending v1 recovery refuses v2 inventory; on v1 it preserves original journal snapshots', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0')
    const request = await f.prepare(0)
    await f.runtime.executeUpgrade(request)
    const operationPath = join(f.root, 'operations', `${request.operationId}.json`)
    const record = JSON.parse(await readFile(operationPath, 'utf8'))
    record.schemaVersion = 1
    record.operation.status.phase = 'committing'
    record.operation.status.files = 'unknown'
    record.transaction.preparation.before = legacySnapshot(record.transaction.preparation.before)
    record.transaction.inventoryCommit.before = legacySnapshot(record.transaction.inventoryCommit.before)
    record.transaction.inventoryCommit.after = legacySnapshot(record.transaction.inventoryCommit.after)
    record.transaction.inventoryCommit.committed = false
    await writeFile(operationPath, JSON.stringify(record))
    const args = [request.operationId, 'session', request.baseline.targets.map(target => target.path), async () => {}] as const
    expect(await f.runtime.recover(...args)).toMatchObject({ outcome: 'unknown', reason: 'legacy-journal-needs-v1-inventory' })
    const blockedRequest = await f.prepare(0).catch(() => null)
    expect(blockedRequest).toBeNull() // Current installed version is no longer the earlier 1.0.0.
    await writeFile(f.store.path, JSON.stringify(legacySnapshot(await f.store.read())))
    expect(await f.runtime.recover(...args)).toMatchObject({ outcome: 'committed' })
    const recovered = JSON.parse(await readFile(operationPath, 'utf8'))
    expect(recovered.schemaVersion).toBe(1)
    expect(recovered.transaction.preparation.before).toEqual(record.transaction.preparation.before)
    expect(recovered.transaction.inventoryCommit.before).toEqual(record.transaction.inventoryCommit.before)
    expect(recovered.transaction.inventoryCommit.after).toEqual(record.transaction.inventoryCommit.after)
    expect(await f.store.diskVersion()).toBe(1)
  } finally { await f.close() }
})

test('migration refuses ambiguous paths and conflicting backup without replacing the old authority', async () => {
  const f = await fixture()
  try {
    const target = { agent: 'dsh', rootDir: f.roots[0]!.root, installDir: join(f.roots[0]!.root, 'writer'), installedAt: 'now' }
    const raw = JSON.stringify({ items: [{ ...f.identity, version: '1.0.0', targets: [target, { ...target }] }] })
    await mkdir(dirname(f.store.path), { recursive: true }); await writeFile(f.store.path, raw)
    await expect(f.store.ensureWritable()).rejects.toThrow('Ambiguous')
    expect(await readFile(f.store.path, 'utf8')).toBe(raw)
    const valid = JSON.stringify({ items: [{ ...f.identity, version: '1.0.0', targets: [target] }] })
    await writeFile(f.store.path, valid)
    const digest = createHash('sha256').update(valid).digest('hex')
    await writeFile(join(dirname(f.store.path), `inventory.v1.${digest}.backup.json`), 'different bytes')
    await expect(f.store.ensureWritable()).rejects.toThrow('backup mismatch')
    expect(await readFile(f.store.path, 'utf8')).toBe(valid)
  } finally { await f.close() }
})

test('selected suite claim blocks native upgrade even if its ownership metadata is inconsistent', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0'); await f.install(1, '1.0.0')
    await f.store.mutateAtomic(value => { value.suites = [{ ...f.identity, slug: 'set', version: '1', fingerprint: 'suite', members: [{ namespace: 'team', slug: 'writer', version: '1.0.0', fingerprint: value.items[0]!.targets[0]!.fingerprint!, installDirs: [join(f.roots[0]!.root, 'writer')] }] }] })
    expect(await f.runtime.inspectUpgrade({ ...f.identity, version: '1.0.0' }, [f.roots[0]!])).toEqual({ status: 'blocked', reason: 'shared-target' })
    expect((await f.runtime.inspectUpgrade({ ...f.identity, version: '1.0.0' }, [f.roots[1]!])).status).toBe('ready')
  } finally { await f.close() }
})

test('installed projection revision binds target timestamp and ownership without contaminating sibling revisions', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0'); await f.install(1, '1.0.0')
    const before = await f.runtime.listInstalled(f.identity.registry, f.roots)
    await f.store.mutateAtomic(value => { value.items[0]!.targets[0]!.installedAt = 'changed timestamp' })
    const after = await f.runtime.listInstalled(f.identity.registry, f.roots)
    expect(before.items[0]!.recordRevision).toMatch(/^[a-f0-9]{64}$/)
    expect(after.items[0]!.recordRevision).not.toBe(before.items[0]!.recordRevision)
    expect(after.items[1]!.recordRevision).toBe(before.items[1]!.recordRevision)
  } finally { await f.close() }
})

test('ancestor and descendant claims block writes but prefix siblings remain independent', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0')
    const before = await f.store.read()
    const selected = before.items[0]!.targets[0]!
    const paths = new Set([selected.installDir])
    const baseline = await captureInstallInventory(before, f.identity, paths)
    for (const installDir of [join(selected.installDir, 'nested'), selected.rootDir]) {
      const overlapping = structuredClone(before)
      overlapping.items.push({ ...f.identity, slug: 'other', targets: [{ ...selected, rootDir: f.root, installDir }] })
      expect(await captureInstallInventory(overlapping, f.identity, paths)).not.toBe(baseline)
      await expect(f.store.writeAtomic(overlapping)).rejects.toThrow('Ambiguous inventory target ownership')
      expect(await f.store.read()).toEqual(before)
      // Simulate a pre-existing or externally corrupted claim; read-only native previews must fail closed too.
      await writeFile(f.store.path, JSON.stringify({ schemaVersion: 3, records: overlapping.items }))
      expect(await f.runtime.inspectRemoval({ ...f.identity, version: '1.0.0' }, f.roots[0]!)).toEqual({ status: 'blocked', reason: 'shared-target' })
      expect(await f.runtime.inspectUpgrade({ ...f.identity, version: '1.0.0' }, [f.roots[0]!])).toEqual({ status: 'blocked', reason: 'shared-target' })
      await writeFile(f.store.path, JSON.stringify({ schemaVersion: 3, records: before.items }))
      expect(await readFile(join(selected.installDir, 'SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    }
    const sibling = structuredClone(before)
    sibling.items.push({ ...f.identity, slug: 'other', targets: [{ ...selected, installDir: `${selected.installDir}-other` }] })
    expect(await captureInstallInventory(sibling, f.identity, paths)).toBe(baseline)
    await f.store.writeAtomic(sibling)
    expect((await f.store.read()).items).toHaveLength(2)
    await expect(installSkill({ ...f.identity, slug: 'child', inventoryStore: f.store, force: true,
      targets: [{ agent: 'dsh', rootDir: selected.installDir, scope: 'user', source: 'explicit' }] })).rejects.toThrow('overlaps another managed installation')
    expect(await readFile(join(selected.installDir, 'SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    expect((await f.store.read()).items).toHaveLength(2)
  } finally { await f.close() }
})


test.each(['suite-only', 'shared', 'suite-claim'] as const)('direct CLI mutation protects %s targets, including force, but allows an unrelated position', async mode => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0'); await f.install(1, '1.0.0')
    await f.store.mutateAtomic(inventory => {
      const target = inventory.items[0]!.targets[0]!
      if (mode === 'suite-only') target.installedBy = ['suite:@team/set@1']
      if (mode === 'shared') target.installedBy = ['direct', 'suite:@team/set@1']
      if (mode === 'suite-claim') inventory.suites = [{ ...f.identity, slug: 'set', version: '1.0.0', fingerprint: 'suite',
        members: [{ namespace: 'team', slug: 'writer', version: target.version, fingerprint: target.fingerprint!, installDirs: [target.installDir] }] }]
    })
    const before = await f.store.read()
    await expect(f.install(0, '2.0.0')).rejects.toThrow('shared or Suite-owned')
    const options = { coordinates: ['@team/writer'], registry: f.identity.registry, home: join(f.root, 'home'), force: true, tokenForRegistry: async () => undefined }
    const blocked = await planSkillUpgrades({ ...options, dir: f.roots[0]!.root })
    expect(blocked.blocked).toBe(1)
    expect(blocked.items[0]!.reason).toContain('shared or Suite-owned')
    await expect(removeLocalSkill({ ...f.identity, home: join(f.root, 'home') })).rejects.toThrow('shared or Suite-owned')
    expect(await f.store.read()).toEqual(before)
    for (const root of f.roots.slice(0, 2)) expect(await readFile(join(root.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    const allowed = await planSkillUpgrades({ ...options, dir: f.roots[1]!.root })
    expect(allowed.upgrades).toBe(1)
    expect((await executeSkillUpgradePlan(allowed, options)).upgraded).toBe(1)
    expect((await f.store.read()).items[0]!.targets.find(target => target.rootDir === f.roots[0]!.root)).toEqual(before.items[0]!.targets[0])
    expect(await readFile(join(f.roots[0]!.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
  } finally { await f.close() }
})

test('v2 read and writable checks preserve exact compatible bytes', async () => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0')
    const v3 = await f.store.read()
    const v2 = { schemaVersion: 2, records: v3.items.map(item => ({ ...item,
      targets: item.targets.map(({ installationState, ...target }) => { void installationState; return target }) })) }
    const bytes = JSON.stringify(v2, null, 4)
    await writeFile(f.store.path, bytes)
    expect((await f.store.read()).items[0]!.targets[0]!.installationState).toBe('enabled')
    expect(await readFile(f.store.path, 'utf8')).toBe(bytes)
    await f.store.ensureWritable()
    expect(await readFile(f.store.path, 'utf8')).toBe(bytes)
    const disk = JSON.parse(await readFile(f.store.path, 'utf8'))
    expect(disk.schemaVersion).toBe(2)
    expect(disk.records).toEqual(v2.records)
  } finally { await f.close() }
})

 test.each(['matching', 'conflict'] as const)('v3 conversion verifies files and preserves original bytes: %s', async mode => {
  const f = await fixture()
  try {
    await f.install(0, '1.0.0')
    const inventory = await f.store.read()
    if (mode === 'conflict') inventory.items[0]!.targets[0]!.installationState = 'disabled'
    const bytes = JSON.stringify({ schemaVersion: 3, records: inventory.items })
    await writeFile(f.store.path, bytes)
    if (mode === 'conflict') {
      await expect(f.store.ensureWritable()).rejects.toThrow('requires recovery')
      expect(await readFile(f.store.path, 'utf8')).toBe(bytes)
    } else {
      await f.store.ensureWritable()
      expect(await f.store.diskVersion()).toBe(2)
      expect(await readFile(join(dirname(f.store.path), `inventory.v3.${createHash('sha256').update(bytes).digest('hex')}.backup.json`), 'utf8')).toBe(bytes)
      expect(JSON.parse(await readFile(f.store.path, 'utf8')).records[0].targets[0]).not.toHaveProperty('installationState')
    }
  } finally { await f.close() }
})
