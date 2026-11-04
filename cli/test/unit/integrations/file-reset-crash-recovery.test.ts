import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { link, mkdtemp, mkdir, open, readFile, readdir, realpath, rename, rm, symlink, unlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { zipSync } from 'fflate'
import { installSkill } from '../../../src/services/install-service'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'

const boundaries = ['prepared', 'old-moved', 'published', 'committed', 'cleanup'] as const
async function fixture(boundary: string, disabled: boolean, conflict = false, emptyConflict = false) {
  const home = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-reset-kill-'))), root = join(home, 'skills'), path = join(root, 'writer')
  const contents = { 'SKILL.md': '# Installed version\n', 'resource.txt': 'original resource', 'untouched.txt': 'original unselected' }
  const hash = createHash('sha256')
  for (const name of Object.keys(contents).sort() as (keyof typeof contents)[]) hash.update(`${name}:${createHash('sha256').update(contents[name]).digest('hex')}\n`)
  const fingerprint = `sha256:${hash.digest('hex')}`, zip = zipSync(Object.fromEntries(Object.entries(contents).map(([name, text]) => [name, Buffer.from(text)])))
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    if (new URL(request.url).pathname.endsWith('/resolve')) return Response.json({ data: { namespace: 'demo', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '/download' } })
    return new Response(new Uint8Array(zip).buffer)
  } })
  const selected = { registry: server.url.origin, namespace: 'demo', slug: 'writer', version: '1.0.0' }, inventory = new InventoryStore(home)
  await installSkill({ ...selected, force: false, inventoryStore: inventory, targets: [{ agent: 'dsh', rootDir: root, scope: 'user', source: 'explicit' }] })
  await mkdir(join(path, 'original-empty', 'nested'), { recursive: true })
  if (disabled) await rename(join(path, 'SKILL.md'), join(path, 'SKILL.md.disabled'))
  await writeFile(join(path, 'resource.txt'), 'local resource customization')
  await writeFile(join(path, 'untouched.txt'), 'local unselected customization')
  const runtime = await openNativeRuntime({ stateDirectory: join(home, 'operations'), inventoryHome: home })
  const snapshot = await runtime.inspectChanges(selected, { root, scope: 'user' })
  if (snapshot.status !== 'ready') throw Error(snapshot.reason)
  const parameters = { boundary, disabled, conflict, emptyConflict }
  const operation = (await runtime.acceptFileReset({ hostInstanceId: runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters }, async () => ({
    namespace: selected.namespace, slug: selected.slug, version: selected.version, targetPaths: [path], targetLabel: 'User',
    context: { sourceIdentity: selected.registry, sourceLabel: 'Fixture', sourceRevision: 'r1', hostLabel: 'Host', initiatedFrom: 'plaza' }
  })))!
  await writeFile(join(home, 'reset-input.json'), JSON.stringify({ operationId: operation.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'] }), { mode: 0o600 })
  return { home, root, path, disabled, conflict, emptyConflict, boundary, selected, runtime, operationId: operation.operationId,
    metadata: await readFile(join(path, '.skillhub', 'metadata.json')), inventory: await readFile(inventory.path), inventoryPath: inventory.path, server }
}
async function exited<T>(value: Promise<T>, milliseconds = 10000): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try { return await Promise.race([value, new Promise<never>((_, reject) => { timer = setTimeout(() => reject(Error('Owned child did not exit within deadline')), milliseconds) })]) }
  finally { clearTimeout(timer) }
}
async function killOwned(child: ReturnType<typeof Bun.spawn>) {
  if (child.exitCode !== null) throw Error('Writer exited before the forced kill')
  if (process.platform === 'win32') {
    const killer = Bun.spawn(['taskkill.exe', '/PID', String(child.pid), '/T', '/F'], { stdout: 'pipe', stderr: 'pipe' })
    try { expect(await exited(killer.exited)).toBe(0) } finally { if (killer.exitCode === null) killer.kill() }
  } else child.kill('SIGKILL')
  expect(await exited(child.exited)).not.toBe(0)
}
function childCommand(name: string, args: string[]): string[] {
  const node = process.env.SKILLHUB_TEST_RESET_NODE, bundles = process.env.SKILLHUB_TEST_RESET_BUNDLES
  if (node) {
    if (!bundles) throw Error('Node reset fixture bundle directory is required')
    return [node, join(bundles, name.replace(/\.ts$/, '.js')), ...args]
  }
  return [process.execPath, fileURLToPath(new URL(`../../fixtures/${name}`, import.meta.url)), ...args]
}
async function recoverInNewProcess(home: string, operationId: string) {
  const child = Bun.spawn(childCommand('file-reset-recover-child.ts', [home, operationId]), { stdout: 'pipe', stderr: 'pipe' })
  try {
    expect(await exited(child.exited)).toBe(0)
    expect(await new Response(child.stderr).text()).toBe('')
    return JSON.parse(await new Response(child.stdout).text()) as { result?: {outcome: string}; error?: string; pending: {operationId: string}[] }
  } finally { if (child.exitCode === null) { child.kill(); await exited(child.exited) } }
}
test('real killed file-reset writers recover five boundaries in enabled and disabled installations and preserve new occupants', async () => {
  const fixtures: Awaited<ReturnType<typeof fixture>>[] = []
  const evidence: Record<string, unknown>[] = []
  let passed = false
  try {
    fixtures.push(...await Promise.all([
      ...boundaries.flatMap(boundary => [false, true].map(disabled => ({ boundary, disabled, conflict: false }))),
      ...boundaries.map(boundary => ({ boundary, disabled: false, conflict: true })),
      { boundary: 'cleanup', disabled: false, conflict: true, emptyConflict: true }
    ].map(scenario => fixture(scenario.boundary, scenario.disabled, scenario.conflict, 'emptyConflict' in scenario && scenario.emptyConflict === true))))
    const kills = await Promise.allSettled(fixtures.map(async f => {
      const marker = join(f.home, 'killed-at')
      const child = Bun.spawn(childCommand('file-reset-crash-child.ts', [f.home, f.operationId, f.boundary, marker]), { stdout: 'pipe', stderr: 'pipe' })
      try {
        const deadline = Date.now() + 20000
        let ready = false
        while (Date.now() < deadline && child.exitCode === null) {
          try { ready = await readFile(marker, 'utf8') === f.boundary } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
          if (ready) break
          await Bun.sleep(25)
        }
        if (!ready && child.exitCode !== null) throw Error(`${f.boundary}: ${await new Response(child.stderr).text()}`)
        expect(ready, `${f.boundary}/${f.disabled}/${f.conflict}: ${child.exitCode}`).toBe(true)
        await killOwned(child)
        expect(await new Response(child.stderr).text()).toBe('')
        evidence.push({ boundary: f.boundary, disabled: f.disabled, conflict: f.conflict, emptyConflict: f.emptyConflict, signal: process.platform === 'win32' ? 'taskkill /T /F' : 'SIGKILL', exitCode: child.exitCode })
        expect((await f.runtime.listPending()).map(record => record.operationId)).toContain(f.operationId)
        await expect(f.runtime.acceptUpgrade({ hostInstanceId: f.runtime.hostInstanceId, ownerId: 'owner', mode: 'new', requestKey: randomUUID(), parameters: { blocked: true } }, async () => { throw Error('Must reject before validation') })).rejects.toThrow('File reset recovery required')
      } finally { if (child.exitCode === null) { child.kill(); await exited(child.exited) } }
    }))
    const failedKills = kills.filter((r): r is PromiseRejectedResult => r.status === 'rejected')
    if (failedKills.length) throw new AggregateError(failedKills.map(r => r.reason), 'Owned crash writers failed')
    // Real inventory locks held at the kill point expire after 30 seconds. Never remove locks by hand.
    await Bun.sleep(31500)
    const results = await Promise.allSettled(fixtures.map(async f => {
      const committed = ['committed', 'cleanup'].includes(f.boundary)
      if (f.conflict) {
        await mkdir(f.path, { recursive: true })
        if (f.emptyConflict) await mkdir(join(`${f.path}.skillhub-backup-${f.operationId}`, 'unexpected-empty'))
        else await writeFile(join(f.path, 'external.txt'), 'New file created after the process was killed')
      }
      const result = await recoverInNewProcess(f.home, f.operationId)
      if (f.conflict) {
        expect(result.error, `${f.boundary}: ${JSON.stringify(result)}`).toBeDefined()
        expect(result.pending.map(record => record.operationId)).toContain(f.operationId)
        if (f.emptyConflict) expect(await readdir(`${f.path}.skillhub-backup-${f.operationId}`)).toContain('unexpected-empty')
        else expect(await readFile(join(f.path, 'external.txt'), 'utf8')).toBe('New file created after the process was killed')
      } else {
        expect(result.error, `${f.boundary}: ${JSON.stringify(result)}`).toBeUndefined()
        expect(result.result?.outcome).toBe(committed ? 'committed' : 'restored')
        expect(result.pending).toEqual([])
        expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe(committed ? 'original resource' : 'local resource customization')
        expect(await readFile(join(f.path, 'untouched.txt'), 'utf8')).toBe('local unselected customization')
        expect(await readFile(join(f.path, f.disabled ? 'SKILL.md.disabled' : 'SKILL.md'), 'utf8')).toBe('# Installed version\n')
        expect(await readFile(join(f.path, '.skillhub', 'metadata.json'))).toEqual(f.metadata)
        expect(await readFile(f.inventoryPath)).toEqual(f.inventory)
        expect(await readdir(f.root)).toEqual(['writer'])
        expect(await readdir(join(f.path, 'original-empty'))).toEqual(['nested'])
        expect((await recoverInNewProcess(f.home, f.operationId)).result?.outcome).toBe(committed ? 'committed' : 'restored')
      }
      evidence.push({ boundary: f.boundary, disabled: f.disabled, conflict: f.conflict, emptyConflict: f.emptyConflict, recovery: result.error ? 'refused-new-occupant' : result.result?.outcome })
    }))
    const failures = results.filter((r): r is PromiseRejectedResult => r.status === 'rejected')
    if (failures.length) throw new AggregateError(failures.map(r => r.reason), 'Killed reset recovery matrix failed')
    passed = true
  } finally {
    if (process.env.SKILLHUB_TEST_EVIDENCE_DIR) {
      await mkdir(process.env.SKILLHUB_TEST_EVIDENCE_DIR, { recursive: true })
      await writeFile(join(process.env.SKILLHUB_TEST_EVIDENCE_DIR, 'reset-crash-matrix.json'), JSON.stringify({ passed, platform: process.platform, executable: process.execPath, childExecutable: process.env.SKILLHUB_TEST_RESET_NODE ?? process.execPath, evidence }, null, 2))
    }
    await Promise.all(fixtures.map(async f => { await f.server.stop(true); await rm(f.home, { recursive: true, force: true }) }))
  }
}, 90000)

for (const mode of ['new-file', 'metadata-change', 'hardlink', 'symlink', 'oversized', 'target-after-auth', 'backup-after-auth'] as const) {
  test.skipIf(mode === 'symlink' && process.platform === 'win32')(`partial cleanup refuses ${mode} without erasing new data`, async () => {
    const f = await fixture('cleanup-guard', false)
    try {
      const snapshot = await f.runtime.inspectChanges(f.selected, { root: f.root, scope: 'user' })
      if (snapshot.status !== 'ready') throw Error(snapshot.reason)
      await f.runtime.executeFileReset({ operationId: f.operationId, ownerId: 'owner', snapshot, paths: ['resource.txt'], authorize: async (_paths, phase) => {
        if (phase === 'cleanup') throw Error('Retain a committed backup for the recovery guard assertion')
      } })
      const backup = `${f.path}.skillhub-backup-${f.operationId}`
      await unlink(join(backup, 'resource.txt'))
      if (mode === 'new-file') await writeFile(join(backup, 'foreign.txt'), 'keep foreign bytes')
      if (mode === 'metadata-change') await writeFile(join(backup, '.skillhub', 'metadata.json'), '{}')
      if (mode === 'hardlink' || mode === 'symlink') {
        await unlink(join(backup, 'untouched.txt'))
        if (mode === 'hardlink') await link(join(f.path, 'untouched.txt'), join(backup, 'untouched.txt'))
        else await symlink(join(f.path, 'untouched.txt'), join(backup, 'untouched.txt'))
      }
      if (mode === 'oversized') {
        const file = await open(join(backup, 'untouched.txt'), 'w')
        try { await file.truncate(10 * 1024 * 1024 + 1) } finally { await file.close() }
      }
      const expected = mode === 'new-file' ? 'Unexpected reset cleanup file' : mode === 'metadata-change' ? 'Reset cleanup remainder changed'
        : mode === 'hardlink' ? 'Unsafe backup content' : mode === 'symlink' ? 'Unsafe change tree' : mode === 'oversized' ? 'Change file exceeds package limit' : 'changed after authorization'
      await expect(f.runtime.recover(f.operationId, 'owner', [f.path], async () => {
        if (mode === 'target-after-auth') await writeFile(join(f.path, 'external.txt'), 'do not delete current content')
        if (mode === 'backup-after-auth') await writeFile(join(backup, 'external.txt'), 'do not delete backup content')
      })).rejects.toThrow(expected)
      expect((await f.runtime.listPending()).map(record => record.operationId)).toContain(f.operationId)
      expect(await readFile(join(f.path, 'resource.txt'), 'utf8')).toBe('original resource')
      expect(await readFile(f.inventoryPath)).toEqual(f.inventory)
      if (mode === 'new-file') expect(await readFile(join(backup, 'foreign.txt'), 'utf8')).toBe('keep foreign bytes')
      if (mode === 'target-after-auth') expect(await readFile(join(f.path, 'external.txt'), 'utf8')).toBe('do not delete current content')
      if (mode === 'backup-after-auth') expect(await readFile(join(backup, 'external.txt'), 'utf8')).toBe('do not delete backup content')
    } finally { await f.server.stop(true); await rm(f.home, { recursive: true, force: true }) }
  })
}
