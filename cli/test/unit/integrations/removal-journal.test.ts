import { expect, test } from 'bun:test'
import { randomUUID } from 'node:crypto'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OperationStore, operationRequestDigest } from '../../../src/integrations/operation-store'
import type { RemovalEvent } from '../../../src/integrations/removal-journal'

test.each(['uninstall', 'remove-stale-record'] as const)('durable %s journal enforces exact target and commit order', async kind => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-removal-journal-'))
  try {
    const store = await OperationStore.open(join(root, 'operations'))
    const path = join(root, 'skills/writer')
    let record = (await store.accept({ hostInstanceId: store.hostInstanceId, ownerId: 'session', requestKey: randomUUID(),
      requestDigest: operationRequestDigest({ kind }), mode: 'new' }, async () => ({ kind, namespace: 'demo', slug: 'writer',
      version: '1.0.0', targetLabel: 'Project', targetPaths: [path],
      context: { sourceIdentity: 'https://registry.example', sourceLabel: 'SkillHub', sourceRevision: 'revision',
        hostLabel: 'Host', initiatedFrom: 'plaza' } })))!
    record = await store.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status, phase: 'revalidating' })
    const item = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0', fingerprint: 'fp',
      targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), installDir: path, installedAt: '2026-10-14' }] }
    const intent = { inventoryItem: item, target: { root: join(root, 'skills'), path, scope: 'project' as const },
      quarantine: kind === 'uninstall' ? join(root, '.skillhub-trash', record.operationId) : null,
      files: kind === 'uninstall' ? { 'SKILL.md': 'hash' } : null }
    const append = async (event: RemovalEvent) => {
      record = await store.recordRemoval(record.operationId, record.ownerId, record.revision, event)
    }
    await expect(append({ type: 'committed' })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await expect(append({ type: 'intent', intent: { ...intent, target: { ...intent.target, path: join(root, 'foreign') } } }))
      .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    if (kind === 'uninstall') {
      await expect(append({ type: 'intent', intent: { ...intent, quarantine: join(root, 'skills/trash') } }))
        .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    }
    await append({ type: 'intent', intent })
    expect((await (await OperationStore.open(store.root)).get(record.operationId, 'session'))?.removal?.intent).toEqual(intent)
    if (kind === 'uninstall') {
      const contender = (await store.accept({ hostInstanceId: store.hostInstanceId, ownerId: 'other-session',
        requestKey: randomUUID(), requestDigest: operationRequestDigest({ kind: 'install' }), mode: 'new' }, async () => ({
        kind: 'install', namespace: 'demo', slug: 'writer', version: '1.0.0', targetLabel: 'Project', targetPaths: [path],
        context: { sourceIdentity: 'https://registry.example', sourceLabel: 'SkillHub', sourceRevision: 'revision',
          hostLabel: 'Host', initiatedFrom: 'plaza' } })))!
      await store.update(contender.operationId, contender.ownerId, contender.revision, { ...contender.operation.status, phase: 'staging' })
      await expect(store.assertTargetsAvailable([path], contender)).rejects.toMatchObject({ code: 'TARGET_RECOVERY_REQUIRED' })
    }
    const commit = { before: { items: [item] }, after: { items: [] } }
    if (kind === 'uninstall') {
      await expect(append({ type: 'beforeCommit', commit })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
      await append({ type: 'isolated' })
    } else await expect(append({ type: 'isolated' })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await expect(append({ type: 'beforeCommit', commit: { ...commit, after: commit.before } })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await append({ type: 'beforeCommit', commit })
    await expect(append({ type: 'cleanup', removed: true })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await expect(store.update(record.operationId, 'session', record.revision, { ...record.operation.status,
      phase: 'completed', files: kind === 'uninstall' ? 'removed' : 'stale-record-removed' }))
      .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await append({ type: 'committed' })
    record = await store.update(record.operationId, 'session', record.revision, { ...record.operation.status,
      phase: 'completed', files: kind === 'uninstall' ? 'removed' : 'stale-record-removed' })
    if (kind === 'uninstall') {
      expect((await store.listForRecovery()).some(value => value.operationId === record.operationId)).toBe(true)
      await append({ type: 'cleanup', removed: false })
      expect(record.removal?.cleanup).toEqual({ removed: false })
      expect((await store.listForRecovery()).some(value => value.operationId === record.operationId)).toBe(true)
      await append({ type: 'cleanup', removed: true })
      expect((await store.listForRecovery()).some(value => value.operationId === record.operationId)).toBe(false)
    }
    await expect(append({ type: 'restore', completed: false })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    const reopened = await OperationStore.open(store.root)
    expect((await reopened.get(record.operationId, 'session'))?.removal?.inventoryCommit).toEqual({ ...commit, committed: true })
  } finally { await rm(root, { recursive: true, force: true }) }
})
