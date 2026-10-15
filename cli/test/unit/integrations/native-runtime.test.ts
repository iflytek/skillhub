import { expect, test } from 'bun:test'
import { randomUUID } from 'node:crypto'
import { realpath, mkdir, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import type { NativeInstallAcceptance, NativeOperationRequest } from '../../../src/integrations/native-contract'
import { InventoryStore } from '../../../src/stores/inventory-store'

test('native acceptance survives restart, resolves retries before stale references, and scopes status to owner', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-accept-')))
  try {
    const options = { stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') }
    const first = await openNativeRuntime(options)
    // JavaScript consumers must not bypass the mandatory native policy callback.
    await expect(Reflect.apply(first.recover, first, [randomUUID(), 'session', []])).rejects.toThrow('requires Host authorization')
    const request: NativeOperationRequest = { hostInstanceId: first.hostInstanceId, ownerId: 'session', requestKey: randomUUID(), mode: 'new',
      parameters: { detailToken: 'old-detail', targetToken: 'old-target', initiatedFrom: 'plaza' } }
    const input: NativeInstallAcceptance = { namespace: 'team', slug: 'writer', version: '1.0.0', targetLabel: 'Current project',
      targetPaths: [join(root, 'skills/writer')], context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry',
        sourceRevision: 'original', hostLabel: 'Host', initiatedFrom: 'plaza' } }
    const accepted = (await first.acceptInstall(request, async () => input))!
    expect(accepted.phase).toBe('accepted')
    expect(JSON.stringify(accepted)).not.toContain(root)
    for (const privateField of ['targetPaths', 'transaction', 'ownerId', 'requestDigest', 'requestKey']) {
      expect(accepted).not.toHaveProperty(privateField)
    }
    const next = await openNativeRuntime(options)
    let validated = false
    const expired = async (): Promise<NativeInstallAcceptance> => { validated = true; throw new Error('expired detail') }
    expect(await next.acceptInstall({ ...request, mode: 'retry' }, expired)).toEqual(accepted)
    expect(await next.acceptInstall(request, expired)).toEqual(accepted)
    expect(validated).toBe(false)
    expect(await next.getOperation(accepted.operationId, 'foreign')).toBeUndefined()
    expect(await next.getOperation(accepted.operationId, 'session')).toEqual(accepted)
    await expect(next.acceptInstall({ ...request, parameters: { ...request.parameters, targetToken: 'different' } }, expired))
      .rejects.toMatchObject({ code: 'REQUEST_KEY_CONFLICT' })
    expect(await next.acceptInstall({ ...request, requestKey: randomUUID(), mode: 'retry' }, expired)).toBeUndefined()
    expect(validated).toBe(false)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('source changes after restart preserve original acceptance but apply to a new request', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-source-replay-')))
  try {
    const options = { stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') }
    const first = await openNativeRuntime(options)
    const request: NativeOperationRequest = { hostInstanceId: first.hostInstanceId, ownerId: 'session',
      requestKey: randomUUID(), mode: 'new', parameters: { namespace: 'team', slug: 'writer', scope: 'user' } }
    const input: NativeInstallAcceptance = { namespace: 'team', slug: 'writer', version: '1.0.0',
      targetLabel: 'User', targetPaths: [join(root, 'skills/writer')],
      context: { sourceIdentity: 'https://original.example', sourceLabel: 'Original', sourceRevision: 'original',
        hostLabel: 'Host', initiatedFrom: 'conversation' } }
    const accepted = (await first.acceptInstall(request, async () => input))!
    const next = await openNativeRuntime(options)
    let preparations = 0
    const changedSource = async (): Promise<NativeInstallAcceptance> => {
      preparations++
      return { ...input, version: '2.0.0', context: { ...input.context,
        sourceIdentity: 'https://changed.example', sourceLabel: 'Changed', sourceRevision: 'changed' } }
    }
    for (const mode of ['new', 'retry'] as const) {
      expect(await next.acceptInstall({ ...request, mode }, changedSource)).toEqual(accepted)
    }
    expect(preparations).toBe(0)
    const fresh = (await next.acceptInstall({ ...request, requestKey: randomUUID() }, changedSource))!
    expect(fresh.operationId).not.toBe(accepted.operationId)
    expect(fresh.version).toBe('2.0.0')
    expect(fresh.context.sourceIdentity).toBe('https://changed.example')
    expect(preparations).toBe(1)
    expect(await next.getOperation(accepted.operationId, request.ownerId)).toEqual(accepted)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('failed new-request validation does not leave an accepted operation', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-accept-reject-')))
  try {
    const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
    await expect(runtime.acceptInstall({ hostInstanceId: runtime.hostInstanceId, ownerId: 'session', requestKey: randomUUID(),
      mode: 'new', parameters: { detailToken: 'expired' } }, async () => { throw new Error('expired') })).rejects.toThrow('expired')
    expect(await runtime.listPending()).toEqual([])
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('native removal replay survives restart and dispatches recovery without installation', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-removal-runtime-')))
  try {
    const options = { stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') }
    const target = { scope: 'user' as const, root: join(root, 'skills') }
    await mkdir(target.root)
    const item = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0', fingerprint: 'fp',
      targets: [{ agent: 'dsh', rootDir: target.root, installDir: join(target.root, 'writer'), installedAt: '2026-10-14' }] }
    const inventory = new InventoryStore(options.inventoryHome)
    await inventory.writeAtomic({ items: [item] })
    const first = await openNativeRuntime(options)
    const baseline = await first.inspectRemoval(item, target)
    if (baseline.status !== 'ready') throw new Error('Expected stale record')
    const request: NativeOperationRequest = { hostInstanceId: first.hostInstanceId, ownerId: 'session', requestKey: randomUUID(),
      mode: 'new', parameters: { planId: 'confirmed-plan' } }
    const accepted = (await first.acceptRemoval(request, async () => ({ kind: 'remove-stale-record',
      namespace: item.namespace, slug: item.slug, version: item.version, targetLabel: 'User', targetPaths: [baseline.target.path],
      context: { sourceIdentity: item.registry, sourceLabel: 'Registry', sourceRevision: 'revision', hostLabel: 'Host', initiatedFrom: 'plaza' } })))!
    const next = await openNativeRuntime(options)
    expect(await next.acceptRemoval(request, async () => { throw new Error('Expired plan must not run') })).toEqual(accepted)
    expect((await next.executeRemoval({ operationId: accepted.operationId, ownerId: 'session', baseline, authorize: async () => {} })).outcome).toBe('committed')
    expect(await inventory.read()).toEqual({ items: [] })
    expect((await next.recover(accepted.operationId, 'session', [baseline.target.path], async () => {})).outcome).toBe('committed')
    expect((await next.getOperation(accepted.operationId, 'session'))?.files).toBe('stale-record-removed')
    expect(await next.listPending()).toEqual([])
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each(['upgrade', 'uninstall', 'remove-stale-record'] as const)('plan lookup recovers %s without reading a pending plan', async kind => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-plan-lookup-')))
  try {
    const options = { stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') }
    const first = await openNativeRuntime(options)
    const request: NativeOperationRequest = { hostInstanceId: first.hostInstanceId, ownerId: 'owner', requestKey: randomUUID(),
      mode: 'new', parameters: { planId: randomUUID() } }
    expect(await first.findPlanOperation(request)).toBeUndefined()
    expect(await first.listPending()).toEqual([])
    const input: NativeInstallAcceptance = { namespace: 'demo', slug: 'writer', version: '1', targetLabel: 'User',
      targetPaths: [join(root, 'skills/writer')], context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry',
        sourceRevision: 'original', hostLabel: 'Host', initiatedFrom: 'plaza' } }
    const accepted = kind === 'upgrade' ? await first.acceptUpgrade(request, async () => input)
      : await first.acceptRemoval(request, async () => ({ ...input, kind }))
    const next = await openNativeRuntime(options)
    expect(await next.findPlanOperation(request)).toEqual(accepted)
    expect(await next.findPlanOperation({ ...request, ownerId: 'foreign' })).toBeUndefined()
    await expect(next.findPlanOperation({ ...request, parameters: { planId: 'changed' } })).rejects.toMatchObject({ code: 'REQUEST_KEY_CONFLICT' })
    await expect(next.findPlanOperation({ ...request, hostInstanceId: randomUUID() })).rejects.toMatchObject({ code: 'HOST_MISMATCH' })
    expect(await next.findPlanOperation({ ...request, requestKey: randomUUID() })).toBeUndefined()
    expect(await next.listPending()).toHaveLength(1)
  } finally { await rm(root, { recursive: true, force: true }) }
})
