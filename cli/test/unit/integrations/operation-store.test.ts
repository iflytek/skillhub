import { expect, test } from 'bun:test'
import { randomUUID } from 'node:crypto'
import { mkdir, mkdtemp, readdir, readFile, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OperationStore, operationRequestDigest } from '../../../src/integrations/operation-store'
import type { AcceptedOperationInput, OperationRequest } from '../../../src/integrations/operation-store'
const accepted: AcceptedOperationInput = { kind: 'install', namespace: 'team', slug: 'writer', version: '1.0.0', targetLabel: '当前项目',
  context: { hostLabel: '测试 Host', sourceIdentity: 'https://registry.example', sourceLabel: 'SkillHub', sourceRevision: randomUUID(), initiatedFrom: 'conversation' } }
function request(store: OperationStore): OperationRequest {
  return { hostInstanceId: store.hostInstanceId, ownerId: 'session-1', requestKey: randomUUID(), mode: 'new',
    requestDigest: operationRequestDigest({ detailToken: 'original-private-reference', scope: 'project' }) }
}
async function fixture(run: (store: OperationStore, root: string) => Promise<void>) {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-operations-'))
  try { await run(await OperationStore.open(root), root) } finally { await rm(root, { recursive: true, force: true }) }
}
test('restarts preserve host identity and accepted requests before any stale-token validation', () => fixture(async (store, root) => {
  const key = request(store)
  const first = (await store.accept(key, async () => accepted))!
  const reopened = await OperationStore.open(root)
  expect(reopened.hostInstanceId).toBe(store.hostInstanceId)
  const restored = await reopened.accept({ ...key, mode: 'retry' }, async () => { throw new Error('expired token must not be validated') })
  expect(restored).toEqual(first)
  expect(await readFile(join(root, `${first.operationId}.json`), 'utf8')).not.toContain('original-private-reference')
  expect(await reopened.get(first.operationId, 'other-session')).toBeUndefined()
  expect((await readdir(root)).sort()).toEqual([`${first.operationId}.json`, 'host.json'].sort())
}))
test('conflicting request arguments are rejected and missing retries never validate or create', () => fixture(async (store) => {
  const key = request(store)
  await store.accept(key, async () => accepted)
  await expect(store.accept({ ...key, requestDigest: operationRequestDigest({ scope: 'user' }) }, async () => accepted))
    .rejects.toMatchObject({ code: 'REQUEST_KEY_CONFLICT' })
  expect(await store.accept({ ...key, requestKey: randomUUID(), mode: 'retry' }, async () => { throw new Error('must not run') })).toBeUndefined()
  await expect(store.accept({ ...key, hostInstanceId: randomUUID() }, async () => accepted)).rejects.toMatchObject({ code: 'HOST_MISMATCH' })
}))
test('independent store instances deduplicate simultaneous accepts using file locks', () => fixture(async (store, root) => {
  const key = request(store)
  const stores = await Promise.all(Array.from({ length: 5 }, () => OperationStore.open(root)))
  const records = await Promise.all(stores.map(instance => instance.accept(key, async () => accepted)))
  expect(new Set(records.map(value => value!.operationId)).size).toBe(1)
  expect((await readdir(root)).filter(name => name.endsWith('.json'))).toHaveLength(2)
}))
test('status updates require the current revision, preserve context, and cannot replay a terminal operation', () => fixture(async store => {
  const key = request(store)
  const initial = (await store.accept(key, async () => accepted))!
  const staged = await store.update(initial.operationId, key.ownerId, 0, { ...initial.operation.status, phase: 'staging' })
  await expect(store.update(initial.operationId, key.ownerId, 0, staged.operation.status)).rejects.toMatchObject({ code: 'REVISION_CONFLICT' })
  await expect(store.update(initial.operationId, key.ownerId, 1, { ...staged.operation.status, phase: 'completed', files: 'installed' }))
    .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
  const checking = await store.update(initial.operationId, key.ownerId, 1, { ...staged.operation.status, phase: 'revalidating' })
  const committing = await store.update(initial.operationId, key.ownerId, 2, { ...checking.operation.status, phase: 'committing' })
  const done = await store.update(initial.operationId, key.ownerId, 3, { ...committing.operation.status, phase: 'completed', files: 'installed' })
  expect(done.operation.context.createdAt).toBe(initial.operation.context.createdAt)
  expect(done.operation.context.sourceRevision).toBe(accepted.context.sourceRevision)
  await expect(store.update(initial.operationId, key.ownerId, 4, { ...done.operation.status, phase: 'staging' })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
  expect((await store.update(initial.operationId, key.ownerId, 4, { ...done.operation.status, discovery: 'confirmed' })).operation.status.discovery).toBe('confirmed')
}))
test('invalid durable records block new acceptance rather than losing duplicate protection', () => fixture(async (store, root) => {
  const key = request(store)
  const first = (await store.accept(key, async () => accepted))!
  await writeFile(join(root, `${first.operationId}.json`), '{}')
  await expect(store.accept(request(store), async () => accepted)).rejects.toMatchObject({ code: 'CORRUPT_RECORD' })
}))
test('request digests ignore key ordering but preserve explicit parameters', () => {
  expect(operationRequestDigest({ a: 'one', b: 2 })).toBe(operationRequestDigest({ b: 2, a: 'one' }))
  expect(operationRequestDigest({ a: 'one', b: 2 })).not.toBe(operationRequestDigest({ a: 'one', b: 3 }))
  expect(() => operationRequestDigest({ a: Number.NaN })).toThrow()
})

test('startup scan blocks unresolved target intents, permits unrelated paths and clears an explicitly restored status', () => fixture(async (store, root) => {
  const target = join(root, 'skills/writer')
  const pending = (await store.accept(request(store), async () => ({ ...accepted, targetPaths: [target] })))!
  await store.update(pending.operationId, pending.ownerId, 0, { ...pending.operation.status, phase: 'staging' })
  await store.recordTransaction(pending.operationId, pending.ownerId, 1, { type: 'beforeMove', move: {
    phase: 'install', index: 0, agent: 'dsh', canonicalSkillDir: target,
    from: join(root, '.stage'), to: target, stagingDir: join(root, '.stage'), backupDir: null
  } })
  const reopened = await OperationStore.open(root)
  expect((await reopened.listForRecovery()).map(value => value.operationId)).toContain(pending.operationId)
  const next = (await reopened.accept(request(reopened), async () => ({ ...accepted, targetPaths: [target] })))!
  await reopened.update(next.operationId, next.ownerId, 0, { ...next.operation.status, phase: 'staging' })
  const current = { operationId: next.operationId, ownerId: next.ownerId }
  await expect(reopened.assertTargetsAvailable([target], current)).rejects.toMatchObject({ code: 'TARGET_RECOVERY_REQUIRED' })
  await expect(reopened.assertTargetsAvailable([join(root, 'forged')], current)).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
  const otherTarget = join(root, 'other/writer')
  const other = (await reopened.accept(request(reopened), async () => ({ ...accepted, targetPaths: [otherTarget] })))!
  await reopened.update(other.operationId, other.ownerId, 0, { ...other.operation.status, phase: 'staging' })
  await reopened.assertTargetsAvailable([otherTarget], { operationId: other.operationId, ownerId: other.ownerId })
  const old = (await reopened.get(pending.operationId, pending.ownerId))!
  await reopened.update(old.operationId, old.ownerId, old.revision, { ...old.operation.status, phase: 'failed', files: 'failed', recovery: 'restored' })
  await reopened.assertTargetsAvailable([target], current)
}))


test('pending journals block parent and child targets including aliases with missing descendants', () => fixture(async (store, root) => {
  const target = join(root, 'skills/writer')
  const pending = (await store.accept(request(store), async () => ({ ...accepted, targetPaths: [target] })))!
  await store.update(pending.operationId, pending.ownerId, 0, { ...pending.operation.status, phase: 'unknown', recovery: 'unknown' })
  await mkdir(join(root, 'skills'))
  const candidates = [join(root, 'skills'), join(target, 'nested/child')]
  if (process.platform !== 'win32') {
    await symlink(join(root, 'skills'), join(root, 'alias'))
    candidates.push(join(root, 'alias/writer/nested/child'))
  }
  for (const path of candidates) {
    const current = (await store.accept(request(store), async () => ({ ...accepted, targetPaths: [path] })))!
    await store.update(current.operationId, current.ownerId, 0, { ...current.operation.status, phase: 'staging' })
    await expect(store.assertTargetsAvailable([path], current)).rejects.toMatchObject({ code: 'TARGET_RECOVERY_REQUIRED' })
  }
  const sibling = join(root, 'skills/writer-other')
  const current = (await store.accept(request(store), async () => ({ ...accepted, targetPaths: [sibling] })))!
  await store.update(current.operationId, current.ownerId, 0, { ...current.operation.status, phase: 'staging' })
  await store.assertTargetsAvailable([sibling], current)
  expect((await store.get(pending.operationId, pending.ownerId))!.operation.status.phase).toBe('unknown')
}))
