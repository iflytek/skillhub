import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { mkdtemp, readFile, realpath, rename, rm, symlink, unlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { installSkill } from '../../../src/services/install-service'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import { boundedDiff } from '../../../src/integrations/native-changes'
import type { NativeInstallAcceptance } from '../../../src/integrations/native-contract'

async function fixture() {
  const home = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-changes-')))
  const versions = ['1.0.0', '2.0.0'].map((version, i) => {
    const files: Record<string, Uint8Array> = { 'SKILL.md': Buffer.from(`# Version ${version}`), 'resource.txt': Buffer.from(`resource${i}`), 'deleted.txt': Buffer.from('restore me') }
    const hash = createHash('sha256')
    for (const path of Object.keys(files).sort()) hash.update(`${path}:${createHash('sha256').update(files[path]!).digest('hex')}\n`)
    return { version, versionId: i + 1, fingerprint: `sha256:${hash.digest('hex')}`, files }
  })
  let badFingerprint = false
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const url = new URL(request.url), release = versions.find(v => v.version === url.searchParams.get('version')) ?? versions[0]!
    if (url.pathname.endsWith('/resolve')) return Response.json({ data: { namespace: 'demo', slug: 'writer', version: release.version, versionId: release.versionId,
      fingerprint: badFingerprint ? 'sha256:wrong' : release.fingerprint, downloadUrl: `/download?version=${release.version}` } })
    return new Response(new Uint8Array(zipSync(release.files)).buffer)
  } })
  const selected = { registry: server.url.origin, namespace: 'demo', slug: 'writer', version: '1.0.0' }
  const inventory = new InventoryStore(home), root = join(home, 'skills'), path = join(root, 'writer')
  await installSkill({ ...selected, force: false, inventoryStore: inventory, targets: [{ agent: 'dsh', rootDir: root, scope: 'user', source: 'explicit' }] })
  const state = join(home, 'operations'), runtime = await openNativeRuntime({ stateDirectory: state, inventoryHome: home }), target = { root, scope: 'user' as const }
  const ready = async () => { const s = await runtime.inspectChanges(selected, target); if (s.status !== 'ready') throw Error(s.reason); return s }
  const accept = async () => runtime.acceptFileReset({ hostInstanceId: runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { reset: randomUUID() } }, async (): Promise<NativeInstallAcceptance> => ({ ...selected,
    targetLabel: 'User', targetPaths: [path], context: { sourceIdentity: selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Local', initiatedFrom: 'plaza' } }))
  return { home, root, path, state, versions, selected, target, inventory, runtime, ready, accept, bad: () => { badFingerprint = true }, close: async () => { await server.stop(true); await rm(home, { recursive: true, force: true }) } }
}
test('local changes list and bounded body use exact release and include added/deleted/modified files', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'user edits'); await writeFile(join(f.path, 'extra.txt'), 'new'); await unlink(join(f.path, 'deleted.txt'))
    const snapshot = await f.ready()
    expect(snapshot.changes).toEqual([{ path: 'deleted.txt', kind: 'deleted' }, { path: 'extra.txt', kind: 'added' }, { path: 'resource.txt', kind: 'modified' }])
    expect((await f.runtime.readChangeDiff(snapshot, 'resource.txt')).lines).toEqual([{ kind: 'deleted', text: 'resource0' }, { kind: 'added', text: 'user edits' }])
    f.bad(); await expect(f.runtime.readChangeDiff(snapshot, 'resource.txt')).rejects.toThrow('identity')
  } finally { await f.close() }
})
test('version comparison is read-only and rejects downgrade', async () => {
  const f = await fixture()
  try {
    const before = await readFile(f.inventory.path)
    const diff = await f.runtime.prepareVersionChanges(f.selected, f.target, f.versions[1]!)
    expect(diff.changes.map(v => v.path)).toEqual(['resource.txt', 'SKILL.md'])
    expect((await f.runtime.readChangeDiff(diff, 'SKILL.md')).state).toBe('text')
    await expect(f.runtime.prepareVersionChanges(f.selected, f.target, f.versions[0]!)).rejects.toThrow('newer')
    expect(await readFile(f.inventory.path)).toEqual(before)
  } finally { await f.close() }
})
test.each([false, true])('reset preserves inventory metadata/version and disabled state=%s', async disabled => {
  const f = await fixture()
  try {
    if (disabled) await rename(join(f.path, 'SKILL.md'), join(f.path, 'SKILL.md.disabled'))
    await writeFile(join(f.path, 'resource.txt'), 'user edits'); await writeFile(join(f.path, 'extra.txt'), 'new'); await unlink(join(f.path, 'deleted.txt'))
    const metadata = await readFile(join(f.path, '.skillhub/metadata.json')), inventory = await readFile(f.inventory.path)
    const snapshot = await f.ready(), op = (await f.accept())!
    const result = await f.runtime.executeFileReset({ operationId: op.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async () => {} })
    expect(result.outcome).toBe('committed')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('resource0')
    expect(await readFile(join(f.path, 'extra.txt'), 'utf8')).toBe('new')
    const next = await f.ready(), op2 = (await f.accept())!
    expect((await f.runtime.executeFileReset({ operationId: op2.operationId, ownerId: 'owner', snapshot: next, paths: next.changes.map(v => v.path), authorize: async () => {} })).outcome).toBe('committed')
    expect((await f.ready()).changes).toEqual([])
    expect(await readFile(join(f.path, disabled ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    expect(await readFile(join(f.path, '.skillhub/metadata.json'))).toEqual(metadata)
    expect(await readFile(f.inventory.path)).toEqual(inventory)
    expect((await f.runtime.getOperation(op.operationId, 'owner'))?.files).toBe('files-reset')
  } finally { await f.close() }
})
test('changed snapshot or unlisted path never writes a file', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'first'); const snapshot = await f.ready(); const op = (await f.accept())!
    await writeFile(join(f.path, 'resource.txt'), 'second')
    expect((await f.runtime.executeFileReset({ operationId: op.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async () => {} })).outcome).toBe('inventory-before')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('second')
    const op2 = (await f.accept())!
    await expect(f.runtime.executeFileReset({ operationId: op2.operationId, ownerId: 'owner', snapshot: await f.ready(), paths: ['../outside'], authorize: async () => {} })).rejects.toThrow('parameters')
  } finally { await f.close() }
})
test('backup is byte-exact and receipt authorizes dirty upgrade without changing inventory format', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'team customization')
    const snapshot = await f.ready(), receipt = await f.runtime.backupChanges(snapshot, async () => {})
    expect(await readFile(join(f.home, 'backups', receipt.id, 'files/resource.txt'), 'utf8')).toBe('team customization')
    const version = f.versions[1]!, op = (await f.runtime.acceptUpgrade({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { upgrade: true } }, async () => ({ namespace: f.selected.namespace, slug: f.selected.slug, version: version.version, targetLabel: 'User', targetPaths: [f.path], context: { sourceIdentity: f.selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Local', initiatedFrom: 'plaza' } })))!
    expect((await f.runtime.executeUpgrade({ operationId: op.operationId, ownerId: 'owner', ...f.selected, ...version, baseline: snapshot.baseline,
      localChangesAuthorization: { snapshot, backupReceipt: receipt }, authorize: async () => {}, validateStaged: async () => {} })).outcome).toBe('committed')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('resource1')
    expect(JSON.parse(await readFile(f.inventory.path, 'utf8')).schemaVersion).toBe(2)
    expect(await readFile(join(f.home, 'backups', receipt.id, 'files/resource.txt'), 'utf8')).toBe('team customization')
  } finally { await f.close() }
})
test('backup failure or post-backup changes cannot authorize overwriting newer local files', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'before')
    const snapshot = await f.ready()
    await expect(f.runtime.backupChanges(snapshot, async () => { throw Error('Denied') })).rejects.toThrow('Denied')
    const receipt = await f.runtime.backupChanges(snapshot, async () => {})
    await writeFile(join(f.path, 'resource.txt'), 'later')
    const version = f.versions[1]!, op = (await f.runtime.acceptUpgrade({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { upgrade: true } }, async () => ({ namespace: f.selected.namespace, slug: f.selected.slug, version: version.version, targetLabel: 'User', targetPaths: [f.path], context: { sourceIdentity: f.selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Local', initiatedFrom: 'plaza' } })))!
    await expect(f.runtime.executeUpgrade({ operationId: op.operationId, ownerId: 'owner', ...f.selected, ...version, baseline: snapshot.baseline,
      localChangesAuthorization: { snapshot, backupReceipt: receipt }, authorize: async () => {}, validateStaged: async () => {} })).rejects.toThrow('stale')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('later')
  } finally { await f.close() }
})
test('reset failure after old directory move is recoverable and blocks other lifecycle writes', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'local')
    const snapshot = await f.ready(), op = (await f.accept())!
    // Edit after replacement authorization: the captured backup differs, so publish must stop.
    const result = await f.runtime.executeFileReset({ operationId: op.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async (_paths, phase) => {
      if (phase === 'replace') await writeFile(join(f.path, 'resource.txt'), 'race')
    } })
    expect(result.outcome).toBe('unknown')
    expect((await f.runtime.listPending()).some(item => item.operationId === op.operationId)).toBe(true)
    // Recovery refuses to overwrite a later edit rather than silently choosing either snapshot.
    await expect(f.runtime.recover(op.operationId, 'owner', [f.path], async () => {})).rejects.toThrow('backup changed')
  } finally { await f.close() }
})
test('unsafe symlink is never compared or reset', async () => {
  if (process.platform === 'win32') return
  const f = await fixture()
  try { await symlink(join(f.path, 'SKILL.md'), join(f.path, 'link')); expect((await f.runtime.inspectChanges(f.selected, f.target)).status).toBe('blocked') }
  finally { await f.close() }
})
test('bounded diff handles binary invalid UTF-8 and large text without quadratic work', () => {
  expect(boundedDiff('a', Buffer.from([0, 1]), Buffer.from('a')).state).toBe('binary')
  expect(boundedDiff('a', Buffer.from([0xff]), Buffer.from('a')).state).toBe('binary')
  expect(boundedDiff('a', Buffer.alloc(300000, 65), Buffer.from('a')).state).toBe('too-large')
  const value = boundedDiff('a', Buffer.from('a\n'.repeat(4000)), Buffer.from('b\n'.repeat(4000)))
  expect(value.lines).toHaveLength(2000); expect(value.truncated).toBe(true)
})

test('reset acceptance is idempotent after response loss and blocks new legacy acceptance', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'edit')
    const request = { hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new' as const, requestKey: randomUUID(), parameters: { reset: true } }
    const acceptance = { namespace: f.selected.namespace, slug: f.selected.slug, version: f.selected.version, targetLabel: 'User', targetPaths: [f.path], context: { sourceIdentity: f.selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Local', initiatedFrom: 'plaza' as const } }
    const first = (await f.runtime.acceptFileReset(request, async () => acceptance))!
    expect((await f.runtime.acceptFileReset(request, async () => { throw Error('must not reevaluate') }))?.operationId).toBe(first.operationId)
    await expect(f.runtime.acceptUpgrade({ ...request, requestKey: randomUUID() }, async () => acceptance)).rejects.toThrow('recovery required')
    expect((await f.runtime.executeFileReset({ operationId: first.operationId, ownerId: 'owner', snapshot: await f.ready(), paths: ['resource.txt'], authorize: async () => {} })).outcome).toBe('committed')
  } finally { await f.close() }
})

test.each([false, true])('dirty upgrade preserves disabled=%s and verifies retained dirty transaction backups', async disabled => {
  const f = await fixture()
  try {
    if (disabled) await rename(join(f.path, 'SKILL.md'), join(f.path, 'SKILL.md.disabled'))
    await writeFile(join(f.path, 'resource.txt'), 'customized')
    const snapshot = await f.ready(), backupReceipt = await f.runtime.backupChanges(snapshot, async () => {})
    const version = f.versions[1]!
    const operation = (await f.runtime.acceptUpgrade({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { upgrade: true } }, async () => ({ namespace: f.selected.namespace, slug: f.selected.slug, version: version.version, targetLabel: 'User', targetPaths: [f.path], context: { sourceIdentity: f.selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Local', initiatedFrom: 'plaza' } })))!
    const result = await f.runtime.executeUpgrade({ operationId: operation.operationId, ownerId: 'owner', ...f.selected, ...version, baseline: snapshot.baseline,
      localChangesAuthorization: { snapshot, backupReceipt }, authorize: async (_paths, phase) => { if (phase === 'cleanup') throw Error('cleanup denied') }, validateStaged: async () => {} })
    expect(['committed', 'unknown']).toContain(result.outcome)
    expect(await readFile(join(f.path, disabled ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('# Version 2.0.0')
    expect((await f.runtime.recover(operation.operationId, 'owner', [f.path], async () => {})).outcome).toBe('committed')
    expect(await readFile(join(f.home, 'backups', backupReceipt.id, 'files/resource.txt'), 'utf8')).toBe('customized')
  } finally { await f.close() }
})

test('backup file and manifest raw cannot be forged together to authorize a dirty overwrite', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'original local customization')
    const snapshot = await f.ready(), backupReceipt = await f.runtime.backupChanges(snapshot, async () => {})
    const backup = join(f.home, 'backups', backupReceipt.id)
    await writeFile(join(backup, 'files/resource.txt'), 'forged')
    const manifest = JSON.parse(await readFile(join(backup, 'manifest.json'), 'utf8'))
    manifest.raw.files['resource.txt'] = createHash('sha256').update('forged').digest('hex')
    await writeFile(join(backup, 'manifest.json'), JSON.stringify(manifest))
    const version = f.versions[1]!, operation = (await f.runtime.acceptUpgrade({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { upgrade: true } }, async () => ({ namespace: f.selected.namespace, slug: f.selected.slug, version: version.version, targetLabel: 'User', targetPaths: [f.path], context: { sourceIdentity: f.selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Local', initiatedFrom: 'plaza' } })))!
    await expect(f.runtime.executeUpgrade({ operationId: operation.operationId, ownerId: 'owner', ...f.selected, ...version, baseline: snapshot.baseline,
      localChangesAuthorization: { snapshot, backupReceipt }, authorize: async () => {}, validateStaged: async () => {} })).rejects.toThrow('Backup changed')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('original local customization')
  } finally { await f.close() }
})

test('committed reset with unfinished cleanup remains pending across restart and recovers only verified files', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'custom')
    const operation = (await f.accept())!, snapshot = await f.ready()
    expect((await f.runtime.executeFileReset({ operationId: operation.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async (_paths, phase) => {
      if (phase === 'cleanup') throw Error('stop after durable commit')
    } })).outcome).toBe('unknown')
    const restarted = await openNativeRuntime({ stateDirectory: f.state, inventoryHome: f.home })
    expect((await restarted.listPending()).some(record => record.operationId === operation.operationId)).toBe(true)
    expect((await restarted.recover(operation.operationId, 'owner', [f.path], async () => {})).outcome).toBe('committed')
    expect(await restarted.listPending()).toEqual([])
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('resource0')
  } finally { await f.close() }
})

test('recovery policy yield cannot erase a newly changed reset backup', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.path, 'resource.txt'), 'custom')
    const operation = (await f.accept())!, snapshot = await f.ready()
    await f.runtime.executeFileReset({ operationId: operation.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async (_paths, phase) => { if (phase === 'cleanup') throw Error('stop') } })
    const backup = `${f.path}.skillhub-backup-${operation.operationId}`
    await expect(f.runtime.recover(operation.operationId, 'owner', [f.path], async () => {
      await writeFile(join(backup, 'resource.txt'), 'new content during authorization')
    })).rejects.toThrow('changed after authorization')
    expect(await readFile(join(backup, 'resource.txt'), 'utf8')).toBe('new content during authorization')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('resource0')
  } finally { await f.close() }
})

test('backup quota includes its manifest and never deletes older backups', async () => {
  const f = await fixture()
  try {
    const { mkdir, open, readdir } = await import('node:fs/promises')
    const { treeBytes } = await import('../../../src/integrations/native-changes')
    await writeFile(join(f.path, 'resource.txt'), 'custom')
    const bytes = await treeBytes(f.path), directory = join(f.home, 'backups', 'retained')
    await mkdir(directory, { recursive: true })
    const sparse = await open(join(directory, 'large.bin'), 'w')
    try { await sparse.truncate(1024 * 1024 * 1024 - bytes - 64) } finally { await sparse.close() }
    await expect(f.runtime.backupChanges(await f.ready(), async () => {})).rejects.toThrow('quota')
    expect(await readdir(join(f.home, 'backups'))).toEqual(['retained'])
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('custom')
  } finally { await f.close() }
})

test('reset never adopts or removes an occupied staging directory', async () => {
  const f = await fixture()
  try {
    const { mkdir } = await import('node:fs/promises')
    await writeFile(join(f.path, 'resource.txt'), 'custom')
    const operation = (await f.accept())!, snapshot = await f.ready()
    const staging = `${f.path}.skillhub-reset-stage-${operation.operationId}`
    await mkdir(staging); await writeFile(join(staging, 'keep.txt'), 'competitor')
    expect((await f.runtime.executeFileReset({ operationId: operation.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async () => {} })).outcome).toBe('inventory-before')
    expect(await readFile(join(staging, 'keep.txt'), 'utf8')).toBe('competitor')
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('custom')
  } finally { await f.close() }
})

test('automatic change inspection blocks oversized local files before hashing their bodies', async () => {
  const f = await fixture()
  try {
    const { open } = await import('node:fs/promises')
    const { assertChangeTreeBudget } = await import('../../../src/integrations/change-tree-budget')
    const huge = await open(join(f.path, 'large.bin'), 'w')
    try { await huge.truncate(10 * 1024 * 1024 + 1) } finally { await huge.close() }
    await expect(assertChangeTreeBudget(f.path)).rejects.toThrow('package limit')
    expect(await f.runtime.inspectChanges(f.selected, f.target)).toEqual({ status: 'blocked', reason: 'target-unavailable' })
    expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('resource0')
  } finally { await f.close() }
})

test('durable tree flush keeps existing nested binary and text file bytes intact', async () => {
  const f = await fixture()
  try {
    const { mkdir } = await import('node:fs/promises')
    const { syncTree } = await import('../../../src/integrations/native-changes')
    const directory = join(f.home, 'flush-copy')
    await mkdir(join(directory, 'nested'), { recursive: true })
    await writeFile(join(directory, 'text.txt'), 'flush does not replace or truncate this file\n')
    await writeFile(join(directory, 'nested', 'binary.dat'), Buffer.from([0, 255, 1, 2, 3]))
    await syncTree(directory)
    expect(await readFile(join(directory, 'text.txt'), 'utf8')).toBe('flush does not replace or truncate this file\n')
    expect(await readFile(join(directory, 'nested', 'binary.dat'))).toEqual(Buffer.from([0, 255, 1, 2, 3]))
  } finally { await f.close() }
})
