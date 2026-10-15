import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { realpath, mkdtemp, readFile, rm, writeFile, symlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { zipSync } from 'fflate'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import { OperationStore, operationRequestDigest } from '../../../src/integrations/operation-store'
import type { NativeInstallExecution } from '../../../src/integrations/native-contract'

async function fixture(downloadFailure = false) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-execution-')))
  const content = '# Native fixture'
  const fingerprint = 'sha256:' + createHash('sha256').update('SKILL.md:' + createHash('sha256').update(content).digest('hex') + '\n').digest('hex')
  let downloads = 0
  let releaseChanged = false
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    if (new URL(request.url).pathname.endsWith('/resolve')) return Response.json({ data: {
      namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1,
      fingerprint: releaseChanged ? 'changed' : fingerprint, downloadUrl: '/download'
    } })
    downloads++
    if (downloadFailure) return new Response('missing', { status: 404 })
    return new Response(new Uint8Array(zipSync({ 'SKILL.md': new TextEncoder().encode(content) })).buffer)
  } })
  const registry = server.url.origin
  const stateDirectory = join(root, 'operations')
  const operations = await OperationStore.open(stateDirectory)
  const accept = () => operations.accept({ hostInstanceId: operations.hostInstanceId, ownerId: 'session',
    requestKey: randomUUID(), requestDigest: operationRequestDigest({ version: '1.0.0' }), mode: 'new' }, async () => ({
    kind: 'install', namespace: 'team', slug: 'writer', version: '1.0.0', targetLabel: 'Project', targetPaths: [join(root, 'skills/writer')],
    context: { sourceIdentity: registry, sourceLabel: 'Fixture', sourceRevision: 'source', hostLabel: 'Host', initiatedFrom: 'plaza' }
  }))
  const accepted = (await accept())!
  const runtime = await openNativeRuntime({ stateDirectory, inventoryHome: join(root, 'home') })
  const request: NativeInstallExecution = { operationId: accepted.operationId, ownerId: accepted.ownerId,
    registry, namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint,
    root: join(root, 'skills'), scope: 'project', authorize: async () => {}, validateStaged: async () => {} }
  return { root, content, runtime, request, operations, downloads: () => downloads,
    freshRequest: async () => ({ ...request, operationId: (await accept())!.operationId }),
    changeRelease: () => { releaseChanged = true },
    close: async () => { await server.stop(true); await rm(root, { recursive: true, force: true }) } }
}

test('a new request for the same complete installation returns already-installed without another download', async () => {
  const f = await fixture()
  try {
    await f.runtime.executeInstall(f.request)
    const second = await f.freshRequest()
    f.changeRelease() // This path must not require a network resolve of the already pinned installed version.
    expect(await f.runtime.executeInstall(second)).toMatchObject({ outcome: 'committed', reason: 'already-installed' })
    expect((await f.runtime.getOperation(second.operationId, second.ownerId))?.files).toBe('already-installed')
    expect(f.downloads()).toBe(1)
  } finally { await f.close() }
})

test.each(['content', 'metadata', 'symlink'] as const)('existing %s conflict never downloads or replaces installed files', async change => {
  const f = await fixture()
  try {
    await f.runtime.executeInstall(f.request)
    const path = join(f.root, 'skills/writer/SKILL.md')
    if (change === 'content') await writeFile(path, '# Local changes')
    if (change === 'metadata') await writeFile(join(f.root, 'skills/writer/.skillhub/metadata.json'), '{}')
    if (change === 'symlink') {
      await rm(path)
      await writeFile(join(f.root, 'outside.md'), '# Outside')
      await symlink(join(f.root, 'outside.md'), path)
    }
    const before = await readFile(path, 'utf8')
    const second = await f.freshRequest()
    await expect(f.runtime.executeInstall(second)).rejects.toThrow()
    expect(f.downloads()).toBe(1)
    expect(await readFile(path, 'utf8')).toBe(before)
    expect((await f.runtime.getOperation(second.operationId, second.ownerId))?.phase).toBe('failed')
  } finally { await f.close() }
})

test('native accepted operation installs actual files, commits journal and rejects duplicate execution', async () => {
  const f = await fixture()
  try {
    const phases: string[] = []
    const result = await f.runtime.executeInstall({ ...f.request, authorize: async (_paths, phase) => { phases.push(phase) } })
    expect(result.outcome).toBe('committed')
    expect(await readFile(join(f.root, 'skills/writer/SKILL.md'), 'utf8')).toBe(f.content)
    expect((await f.operations.get(f.request.operationId, 'session'))!.operation.status).toMatchObject({ phase: 'completed', files: 'installed' })
    expect(phases).toContain('replace')
    expect(await f.runtime.listPending()).toEqual([])
    await expect(f.runtime.executeInstall(f.request)).rejects.toThrow('accepted')
    expect(f.downloads()).toBe(1)
  } finally { await f.close() }
})

test('competing native runtime instances claim one operation only once', async () => {
  const f = await fixture()
  try {
    const other = await openNativeRuntime({ stateDirectory: join(f.root, 'operations'), inventoryHome: join(f.root, 'home') })
    const results = await Promise.allSettled([f.runtime.executeInstall(f.request), other.executeInstall(f.request)])
    expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1)
    expect(f.downloads()).toBe(1)
  } finally { await f.close() }
})

test('recovery cannot enter an active native installation before target locks are acquired', async () => {
  const f = await fixture()
  const entered = Promise.withResolvers<void>()
  const proceed = Promise.withResolvers<void>()
  let execution: Promise<unknown> | undefined
  const recoverFromChild = async () => {
    const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/native-recovery-child.ts', import.meta.url)),
      f.root, f.request.operationId, f.request.ownerId], { stdout: 'pipe', stderr: 'pipe' })
    expect(await child.exited).toBe(0)
    expect(await new Response(child.stderr).text()).toBe('')
    return JSON.parse(await new Response(child.stdout).text())
  }
  try {
    const other = await openNativeRuntime({ stateDirectory: join(f.root, 'operations'), inventoryHome: join(f.root, 'home') })
    execution = f.runtime.executeInstall({ ...f.request, validateStaged: async () => {
      entered.resolve()
      await proceed.promise
    } })
    await entered.promise
    const before = await f.runtime.getOperation(f.request.operationId, f.request.ownerId)
    let authorized = false
    await expect(other.recover(f.request.operationId, f.request.ownerId, [join(f.request.root, 'writer')],
      async () => { authorized = true })).rejects.toMatchObject({ details: { reason: 'TARGET_BUSY' } })
    expect(authorized).toBe(false)
    expect(await recoverFromChild()).toEqual({ outcome: 'busy' })
    expect(await f.runtime.getOperation(f.request.operationId, f.request.ownerId)).toEqual(before)
    proceed.resolve()
    await execution
    expect(await other.recover(f.request.operationId, f.request.ownerId, [join(f.request.root, 'writer')], async () => {}))
      .toMatchObject({ outcome: 'committed' })
    expect(f.downloads()).toBe(1)
    expect(await recoverFromChild()).toEqual({ outcome: 'committed' })
  } finally { proceed.resolve(); await execution?.catch(() => {}); await f.close() }
})

test('ownership and target mismatches do not claim; policy denial becomes a terminal failure without download', async () => {
  const f = await fixture()
  try {
    for (const request of [{ ...f.request, ownerId: 'foreign' }, { ...f.request, root: join(f.root, 'other') }]) {
      await expect(f.runtime.executeInstall(request)).rejects.toThrow()
    }
    expect(f.downloads()).toBe(0)
    expect((await f.operations.get(f.request.operationId, 'session'))!.operation.status.phase).toBe('accepted')
    await expect(f.runtime.executeInstall({ ...f.request, authorize: async () => { throw new Error('denied') } })).rejects.toThrow('denied')
    expect(f.downloads()).toBe(0)
    expect((await f.runtime.getOperation(f.request.operationId, 'session'))!).toMatchObject({ phase: 'failed', recovery: 'not-needed' })
  } finally { await f.close() }
})

test.skipIf(process.platform === 'win32')('a killed native executor leaves a lease that expires before recovery may enter', async () => {
  const f = await fixture()
  const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/native-execution-child.ts', import.meta.url)),
    f.root, JSON.stringify(f.request)], { stdout: 'pipe', stderr: 'pipe' })
  try {
    const reader = child.stdout.getReader()
    expect(new TextDecoder().decode((await reader.read()).value)).toBe('ready')
    reader.releaseLock()
    child.kill('SIGKILL')
    expect(await child.exited).not.toBe(0)
    expect(await new Response(child.stderr).text()).toBe('')
    const recover = () => f.runtime.recover(f.request.operationId, f.request.ownerId,
      [join(f.request.root, 'writer')], async () => {})
    await expect(recover()).rejects.toMatchObject({ details: { reason: 'TARGET_BUSY' } })
    await Bun.sleep(11_000)
    expect(await recover()).toMatchObject({ outcome: 'unknown', reason: 'target-root-unavailable' })
    expect(f.downloads()).toBe(0)
    expect((await f.runtime.listPending()).map(operation => operation.operationId)).toEqual([f.request.operationId])
  } finally { child.kill(); await child.exited; await f.close() }
}, 25_000)

test('release drift is stopped before download without claiming that files need recovery', async () => {
  const f = await fixture()
  try {
    f.changeRelease()
    await expect(f.runtime.executeInstall(f.request)).rejects.toThrow('release changed')
    expect(f.downloads()).toBe(0)
    expect((await f.operations.get(f.request.operationId, 'session'))!.operation.status).toMatchObject({ phase: 'failed', recovery: 'not-needed' })
    await expect(readFile(join(f.root, 'skills/writer/SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
  } finally { await f.close() }
})

test.each([false, true])('validator failure settles only after staging is absent (cleanup denied: %s)', async cleanupDenied => {
  const f = await fixture()
  try {
    let validated = false
    await expect(f.runtime.executeInstall({ ...f.request,
      authorize: async (_paths, phase) => { if (cleanupDenied && phase === 'cleanup') throw new Error('Retain staging') },
      validateStaged: async directory => {
      const pending = await f.operations.get(f.request.operationId, f.request.ownerId)
      expect(pending?.transaction?.staging).toEqual([{ rootDir: f.request.root, canonicalSkillDir: join(f.request.root, f.request.slug), stagingDir: directory }])
      expect(await readFile(join(directory, 'SKILL.md'), 'utf8')).toBe(f.content)
      validated = true
      throw new Error('Unsupported DSH format')
    } })).rejects.toThrow('Unsupported DSH format')
    expect(validated).toBe(true)
    expect(await f.runtime.getOperation(f.request.operationId, f.request.ownerId)).toMatchObject(cleanupDenied
      ? { phase: 'recovery-required', recovery: 'unknown' } : { phase: 'failed', recovery: 'not-needed' })
    await expect(readFile(join(f.root, 'skills/writer/SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
  } finally { await f.close() }
})

test('overlapping distinct requests settle the cleaned loser without disturbing the winner', async () => {
  const f = await fixture()
  const firstStaged = Promise.withResolvers<void>()
  const secondStaged = Promise.withResolvers<void>()
  const releaseFirst = Promise.withResolvers<void>()
  const releaseSecond = Promise.withResolvers<void>()
  let first: Promise<unknown> | undefined
  let second: Promise<unknown> | undefined
  try {
    first = f.runtime.executeInstall({ ...f.request, validateStaged: async () => { firstStaged.resolve(); await releaseFirst.promise } })
    await Promise.race([firstStaged.promise, first])
    const request = await f.freshRequest()
    second = f.runtime.executeInstall({ ...request, validateStaged: async () => { secondStaged.resolve(); await releaseSecond.promise } })
    const rejected = second.then(() => false, () => true)
    await Promise.race([secondStaged.promise, second])
    releaseFirst.resolve()
    await first
    const before = await readFile(join(f.root, 'home/.skillhub/inventory.json'), 'utf8')
    releaseSecond.resolve()
    expect(await rejected).toBe(true)
    expect(await f.runtime.getOperation(request.operationId, request.ownerId)).toMatchObject({ phase: 'failed', recovery: 'not-needed' })
    expect(await f.runtime.getOperation(f.request.operationId, f.request.ownerId)).toMatchObject({ phase: 'completed', files: 'installed' })
    expect(await readFile(join(f.root, 'skills/writer/SKILL.md'), 'utf8')).toBe(f.content)
    expect(await readFile(join(f.root, 'home/.skillhub/inventory.json'), 'utf8')).toBe(before)
    expect(await f.runtime.listPending()).toEqual([])
  } finally {
    releaseFirst.resolve(); releaseSecond.resolve()
    await first?.catch(() => {}); await second?.catch(() => {})
    await f.close()
  }
})


test('discovery is bound to an owned committed operation and rechecks content after observation', async () => {
  const f = await fixture()
  try {
    let calls = 0
    const observe = async (paths: readonly string[]) => {
      calls++
      expect(paths).toEqual([join(f.request.root, 'writer')])
      return true
    }
    const query = (owner = f.request.ownerId, roots = [f.request.root]) =>
      f.runtime.observeInstallation(f.request.operationId, owner, roots, observe)
    expect(await query()).toBe(false)
    expect(calls).toBe(0)
    await f.runtime.executeInstall(f.request)
    const before = await f.runtime.getOperation(f.request.operationId, f.request.ownerId)
    expect(await query('foreign')).toBe(false)
    expect(await query(f.request.ownerId, [join(f.root, 'other')])).toBe(false)
    expect(calls).toBe(0)
    expect(await query()).toBe(true)
    expect(calls).toBe(1)
    expect(await f.runtime.getOperation(f.request.operationId, f.request.ownerId)).toEqual(before)
    expect(await f.runtime.observeInstallation(f.request.operationId, f.request.ownerId, [f.request.root], async () => false)).toBe(false)
    expect(await f.runtime.observeInstallation(f.request.operationId, f.request.ownerId, [f.request.root], async () => {
      throw new Error('Host discovery unavailable')
    })).toBe(false)
    expect(await f.runtime.observeInstallation(f.request.operationId, f.request.ownerId, [f.request.root], async () => {
      await writeFile(join(f.request.root, 'writer/SKILL.md'), '# Changed during discovery')
      return true
    })).toBe(false)
    expect(await query()).toBe(false)
    expect(calls).toBe(1)
    expect(await f.runtime.getOperation(f.request.operationId, f.request.ownerId)).toEqual(before)
    expect(f.downloads()).toBe(1)
  } finally { await f.close() }
})

test('first installation download failure needs no recovery before staging exists', async () => {
  const f = await fixture(true)
  try {
    await expect(f.runtime.executeInstall(f.request)).rejects.toThrow()
    expect(await f.runtime.getOperation(f.request.operationId, f.request.ownerId))
      .toMatchObject({ phase: 'failed', recovery: 'not-needed' })
    expect((await f.operations.get(f.request.operationId, f.request.ownerId))?.transaction).toBeUndefined()
    await expect(readFile(join(f.root, 'skills/writer/SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
    expect(await f.runtime.listPending()).toEqual([])
  } finally { await f.close() }
})
