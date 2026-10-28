import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { realpath, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { installSkill } from '../../../src/services/install-service'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import type { NativeUpgradeExecution } from '../../../src/integrations/native-contract'

async function fixture(downloadFailure = false, entryCollision = false) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-upgrade-')))
  const versions = ['1.0.0', '2.0.0'].map((version, index) => {
    const content = `# Version ${version}`
    const examples = JSON.stringify({ schemaVersion: 1, defaultLocale: 'en', examples: { en: [{ id: 'one', title: version, prompt: 'Use {{skill}} ' + version }] } })
    const collision = entryCollision && index === 1
    const aggregate = createHash('sha256')
      .update('.skillhub/examples.json:' + createHash('sha256').update(examples).digest('hex') + '\n')
      .update('SKILL.md:' + createHash('sha256').update(content).digest('hex') + '\n')
    if (collision) aggregate.update('SKILL.md.disabled:' + createHash('sha256').update('authored alias').digest('hex') + '\n')
    const fingerprint = 'sha256:' + aggregate.digest('hex')
    return { version, versionId: index + 1, content, examples, fingerprint, collision }
  })
  let downloads = 0
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const url = new URL(request.url)
    const version = versions.find(item => item.version === url.searchParams.get('version')) ?? versions[1]!
    if (url.pathname.endsWith('/resolve')) return Response.json({ data: {
      namespace: 'demo', slug: 'writer', version: version.version, versionId: version.versionId,
      fingerprint: version.fingerprint, downloadUrl: `/download?version=${version.version}`
    } })
    downloads++
    if (downloadFailure && version.version === '2.0.0') return new Response('missing', { status: 404 })
    return new Response(new Uint8Array(zipSync({ 'SKILL.md': new TextEncoder().encode(version.content), '.skillhub/examples.json': new TextEncoder().encode(version.examples), ...(version.collision ? { 'SKILL.md.disabled': new TextEncoder().encode('authored alias') } : {}) })).buffer)
  } })
  const registry = server.url.origin
  const inventory = new InventoryStore(join(root, 'home'))
  const roots = [{ scope: 'user' as const, root: join(root, 'user') }, { scope: 'project' as const, root: join(root, 'project') }]
  await installSkill({ registry, namespace: 'demo', slug: 'writer', version: '1.0.0', force: false, inventoryStore: inventory,
    targets: roots.map(target => ({ agent: 'dsh', rootDir: target.root, scope: target.scope, source: 'explicit' })) })
  const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
  const baseline = await runtime.inspectUpgrade({ registry, namespace: 'demo', slug: 'writer', version: '1.0.0' }, roots)
  if (baseline.status !== 'ready') throw new Error(baseline.reason)
  const acceptance = { hostInstanceId: runtime.hostInstanceId, ownerId: 'session', requestKey: randomUUID(), mode: 'new' as const,
    parameters: { planId: randomUUID() } }
  const operation = (await runtime.acceptUpgrade(acceptance, async () => ({ namespace: 'demo', slug: 'writer', version: '2.0.0',
    targetLabel: 'User and project', targetPaths: baseline.targets.map(target => target.path),
    context: { sourceIdentity: registry, sourceLabel: 'Fixture', sourceRevision: 'source', hostLabel: 'Host', initiatedFrom: 'plaza' } })))!
  const request: NativeUpgradeExecution = { operationId: operation.operationId, ownerId: 'session', registry,
    namespace: 'demo', slug: 'writer', ...versions[1]!, baseline, authorize: async () => {}, validateStaged: async () => {} }
  return { root, roots, runtime, request, inventory, acceptance, downloads: () => downloads,
    close: async () => { await server.stop(true); await rm(root, { recursive: true, force: true }) } }
}

test('native upgrade replaces the entire confirmed target set and persists its outcome', async () => {
  const f = await fixture()
  try {
    expect(await f.runtime.executeUpgrade(f.request)).toMatchObject({ outcome: 'committed' })
    for (const target of f.roots) expect(await readFile(join(target.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 2.0.0')
    expect(await f.runtime.readSkillExamples(join(f.roots[0]!.root, 'writer'))).toMatchObject({ state: 'ready', items: { en: [{ title: '2.0.0' }] } })
    expect((await f.inventory.read()).items[0]!.targets[0]!.version).toBe('2.0.0')
    expect((await f.runtime.getOperation(f.request.operationId, 'session'))?.files).toBe('upgraded')
    expect(await f.runtime.listPending()).toEqual([])
    const replay = await f.runtime.acceptUpgrade(f.acceptance, async () => { throw new Error('expired plan must not be resolved') })
    expect(replay?.operationId).toBe(f.request.operationId)
    await expect(f.runtime.executeUpgrade(f.request)).rejects.toThrow('accepted')
    expect(f.downloads()).toBe(2)
  } finally { await f.close() }
})

test('native upgrade rejects edits made after confirmation without downloading', async () => {
  const f = await fixture()
  try {
    await writeFile(join(f.roots[0]!.root, 'writer/SKILL.md'), 'user edit')
    await expect(f.runtime.executeUpgrade(f.request)).rejects.toThrow('baseline changed')
    expect(f.downloads()).toBe(1)
    expect((await f.inventory.read()).items[0]!.targets[0]!.version).toBe('1.0.0')
    expect((await f.runtime.getOperation(f.request.operationId, 'session'))?.phase).toBe('failed')
  } finally { await f.close() }
})

test('native upgrade rechecks all targets after staging before replacing any target', async () => {
  const f = await fixture()
  try {
    await expect(f.runtime.executeUpgrade({ ...f.request, validateStaged: async () => {
      await writeFile(join(f.roots[1]!.root, 'writer/SKILL.md'), 'concurrent edit')
    } })).rejects.toThrow('baseline changed')
    expect(await readFile(join(f.roots[0]!.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    expect(await readFile(join(f.roots[1]!.root, 'writer/SKILL.md'), 'utf8')).toBe('concurrent edit')
    expect((await f.inventory.read()).items[0]!.targets[0]!.version).toBe('1.0.0')
  } finally { await f.close() }
})

test('failure after a target replacement restores both old targets and can be reconciled', async () => {
  const f = await fixture()
  let replacements = 0
  try {
    await expect(f.runtime.executeUpgrade({ ...f.request, authorize: async (_paths, phase) => {
      if (phase === 'replace' && ++replacements === 3) throw new Error('injected second-target denial')
    } })).rejects.toThrow('injected second-target denial')
    expect(replacements).toBe(3)
    for (const target of f.roots) expect(await readFile(join(target.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    expect((await f.inventory.read()).items[0]!.targets[0]!.version).toBe('1.0.0')
    const result = await f.runtime.recover(f.request.operationId, 'session',
      f.request.baseline.targets.map(target => target.path), async () => {})
    expect(result.outcome).toBe('restored')
    expect((await f.runtime.getOperation(f.request.operationId, 'session'))?.recovery).toBe('restored')
  } finally { await f.close() }
})

for (const stage of ['before-download', 'after-staging'] as const) {
  test.each(['target-added', 'target-removed', 'ownership', 'version', 'fingerprint', 'metadata'] as const)(
    `upgrade checks selected-target %s drift ${stage}`, async drift => {
      const f = await fixture()
      try {
        let changed = false
        let expectedInventory = await f.inventory.read()
        const mutate = async () => {
          if (changed) return
          changed = true
          const inventory = await f.inventory.read()
          const item = inventory.items[0]!
          if (drift === 'target-added') item.targets.push({ ...item.targets[0]!, rootDir: join(f.root, 'foreign'),
            installDir: join(f.root, 'foreign/writer') })
          if (drift === 'target-removed') item.targets.pop()
          if (drift === 'ownership') item.targets[0]!.installedBy = ['direct', 'other-owner']
          if (drift === 'version') item.targets[0]!.version = '1.0.1'
          if (drift === 'fingerprint') item.targets[0]!.fingerprint = 'sha256:' + 'f'.repeat(64)
          if (drift === 'metadata') {
            const path = join(f.roots[1]!.root, 'writer/.skillhub/metadata.json')
            const metadata = JSON.parse(await readFile(path, 'utf8'))
            metadata.version = '1.0.1'
            await writeFile(path, JSON.stringify(metadata))
          } else await f.inventory.writeAtomic(inventory)
          expectedInventory = await f.inventory.read()
        }
        if (stage === 'before-download') await mutate()
        const operation = f.runtime.executeUpgrade({ ...f.request,
          validateStaged: stage === 'after-staging' ? mutate : async () => {},
        })
        if (drift === 'target-added') {
          expect(await operation).toMatchObject({ outcome: 'committed' })
          const targets = (await f.inventory.read()).items[0]!.targets
          expect(targets.find(target => target.rootDir === join(f.root, 'foreign'))).toEqual(expectedInventory.items[0]!.targets.find(target => target.rootDir === join(f.root, 'foreign')))
          for (const target of f.roots) expect(await readFile(join(target.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 2.0.0')
          return
        }
        await expect(operation).rejects.toThrow('baseline changed')
        expect(changed).toBe(true)
        expect(f.downloads()).toBe(stage === 'before-download' ? 1 : 2)
        expect(await f.inventory.read()).toEqual(expectedInventory)
        for (const target of f.roots) {
          expect(await readFile(join(target.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
        }
        if (drift === 'metadata') {
          expect(JSON.parse(await readFile(join(f.roots[1]!.root, 'writer/.skillhub/metadata.json'), 'utf8')).version).toBe('1.0.1')
        }
        expect(await f.runtime.getOperation(f.request.operationId, 'session')).toMatchObject({ phase: 'failed', recovery: 'not-needed' })
        expect(await f.runtime.listPending()).toEqual([])
      } finally { await f.close() }
    },
  )
}


test('upgrade keeps recovery required when rejected staging could not be cleaned', async () => {
  const f = await fixture()
  try {
    await expect(f.runtime.executeUpgrade({ ...f.request,
      validateStaged: async () => { throw new Error('Reject staged package') },
      authorize: async (_paths, phase) => { if (phase === 'cleanup') throw new Error('Keep staging for recovery') },
    })).rejects.toThrow('Reject staged package')
    const operation = await f.runtime.getOperation(f.request.operationId, 'session')
    expect(operation).toMatchObject({ phase: 'recovery-required', recovery: 'unknown' })
    expect((await f.runtime.listPending()).map(item => item.operationId)).toContain(f.request.operationId)
    for (const target of f.roots) expect(await readFile(join(target.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
  } finally { await f.close() }
})

test('upgrade download failure before staging ends without a recovery requirement', async () => {
  const f = await fixture(true)
  try {
    const before = await f.inventory.read()
    await expect(f.runtime.executeUpgrade(f.request)).rejects.toThrow()
    const result = await f.runtime.getOperation(f.request.operationId, f.request.ownerId)
    expect(result).toMatchObject({ phase: 'failed', recovery: 'not-needed' })
    expect(await f.inventory.read()).toEqual(before)
    for (const target of f.request.baseline.targets) {
      expect(await readFile(join(target.path, 'SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
    }
    expect(await f.runtime.listPending()).toEqual([])
  } finally { await f.close() }
})

test.each(['success', 'replace-denied', 'entry-collision'] as const)('disabled upgrade retains its entry state through %s', async mode => {
  const f = await fixture(false, mode === 'entry-collision')
  try {
    const root = f.roots[0]!
    const selection = { registry: f.request.registry, namespace: 'demo', slug: 'writer', version: '1.0.0' }
    const toggle = await f.runtime.inspectToggle(selection, root, 'disabled')
    if (toggle.status !== 'ready') throw new Error(JSON.stringify(toggle))
    const context = { sourceIdentity: selection.registry, sourceLabel: 'Fixture', sourceRevision: 'source', hostLabel: 'Host', initiatedFrom: 'plaza' as const }
    const disable = await f.runtime.acceptToggle({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'session', requestKey: randomUUID(), mode: 'new', parameters: { disable: true } },
      async () => ({ kind: 'disable', namespace: 'demo', slug: 'writer', version: '1.0.0', targetLabel: 'User', targetPaths: [toggle.target.path], context }))
    await f.runtime.executeToggle({ operationId: disable!.operationId, ownerId: 'session', baseline: toggle, authorize: async () => {} })
    const baseline = await f.runtime.inspectUpgrade(selection, [root])
    if (baseline.status !== 'ready') throw new Error(JSON.stringify(baseline))
    const accepted = await f.runtime.acceptUpgrade({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'session', requestKey: randomUUID(), mode: 'new', parameters: { disabledUpdate: true } },
      async () => ({ namespace: 'demo', slug: 'writer', version: '2.0.0', targetLabel: 'User', targetPaths: [toggle.target.path], context }))
    let replacing = 0
    const run = () => f.runtime.executeUpgrade({ ...f.request, operationId: accepted!.operationId, baseline,
      authorize: async (paths, phase) => {
        if (phase === 'replace' && paths.includes(toggle.target.path) && ++replacing === 2 && mode === 'replace-denied') throw new Error('Injected disabled update failure')
      } })
    if (mode === 'success') expect((await run()).outcome).toBe('committed')
    else {
      await expect(run()).rejects.toThrow(mode === 'entry-collision' ? 'entry destination is occupied' : 'Injected disabled update failure')
      expect((await f.runtime.recover(accepted!.operationId, 'session', [toggle.target.path], async () => {})).outcome).toBe('restored')
    }
    const installed = (await f.inventory.read()).items[0]!.targets.find(target => target.rootDir === root.root)!
    expect(installed.installationState).toBe('disabled')
    expect(installed.version).toBe(mode === 'success' ? '2.0.0' : '1.0.0')
    expect(await readFile(join(root.root, 'writer/SKILL.md.disabled'), 'utf8')).toBe(`# Version ${installed.version}`)
    await expect(readFile(join(root.root, 'writer/SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
    expect(await readFile(join(f.roots[1]!.root, 'writer/SKILL.md'), 'utf8')).toBe('# Version 1.0.0')
  } finally { await f.close() }
})
