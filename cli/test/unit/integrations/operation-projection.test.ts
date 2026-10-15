import { expect, test } from 'bun:test'
import { projectOperation } from '../../../src/integrations/operation-projection'
import type { OperationRecord } from '../../../src/integrations/operation-store'

function record(): OperationRecord {
  return { schemaVersion: 1, operationId: 'operation', ownerId: 'private-owner', requestKey: 'private-key',
    requestDigest: 'private-digest', revision: 3,
    operation: { kind: 'upgrade', namespace: 'team', slug: 'writer', version: '2.0.0', targetLabel: '用户、项目',
      targetPaths: ['/private/user/writer', '/private/project/writer'],
      context: { hostInstanceId: 'host', hostLabel: 'Host', sourceIdentity: 'https://registry.example', sourceLabel: 'Registry',
        sourceRevision: 'source', initiatedFrom: 'plaza', createdAt: '2026-10-15T00:00:00Z', updatedAt: '2026-10-15T00:00:01Z' },
      status: { phase: 'completed', files: 'upgraded', discovery: 'unconfirmed', recovery: 'not-needed',
        warnings: ['Backup cleanup requires verification.', 'private exception /secret/path'] } },
    transaction: { moves: [], cleanup: [], inventoryCommit: { committed: true,
      before: { items: [{ registry: 'https://other.example', namespace: 'team', slug: 'writer', version: '9.0.0', targets: [] },
        { registry: 'https://registry.example', namespace: 'team', slug: 'writer', version: '1.0.0', targets: ['/private/user/writer', '/private/project/writer'].map(installDir =>
          ({ agent: 'dsh', rootDir: installDir.slice(0, -7), installDir, installedAt: '2026-10-15T00:00:00Z' })) }] },
      after: { items: [] } } } }
}

test('original journal reports both upgraded targets and versions without exposing private data', () => {
  const projected = projectOperation(record())
  expect(projected.resultDetails).toEqual({ beforeVersion: '1.0.0', afterVersion: '2.0.0',
    targets: [{ label: '目标 1（确认计划顺序）', outcome: 'upgraded' }, { label: '目标 2（确认计划顺序）', outcome: 'upgraded' }],
    warnings: ['backup-cleanup', 'verification-required'] })
  expect(JSON.stringify(projected)).not.toContain('private')
  expect(JSON.stringify(projected)).not.toContain('/secret/path')
  expect(projectOperation(JSON.parse(JSON.stringify(record())))).toEqual(projected)
})

test('an interrupted transaction never presents target moves as a completed upgrade', () => {
  const input = record()
  input.operation.status = { ...input.operation.status, phase: 'recovery-required', files: 'unknown', recovery: 'unknown' }
  const result = projectOperation(input).resultDetails!
  expect(result.afterVersion).toBeNull()
  expect(result.targets.map(target => target.outcome)).toEqual(['unknown', 'unknown'])
  input.operation.status.phase = 'failed'
  input.operation.status.recovery = 'restored'
  expect(projectOperation(input).resultDetails!.targets.map(target => target.outcome)).toEqual(['restored', 'restored'])
})

test('older records without a before snapshot or target paths retain unknown details', () => {
  const input = record()
  delete input.transaction
  delete input.operation.targetPaths
  expect(projectOperation(input).resultDetails).toMatchObject({ beforeVersion: null, targets: [] })
})

test('a new target does not inherit the old version from an unaffected scope', () => {
  const input = record()
  input.operation.kind = 'install'
  input.operation.status.files = 'installed'
  input.operation.targetPaths = ['/private/new-project/writer']
  expect(projectOperation(input).resultDetails).toMatchObject({ beforeVersion: null, afterVersion: '2.0.0' })
})

test('a verified already-installed operation retains its original version without a mutation snapshot', () => {
  const input = record()
  input.operation.kind = 'install'
  input.operation.targetPaths = ['/private/user/writer']
  input.operation.status.files = 'already-installed'
  delete input.transaction
  expect(projectOperation(input).resultDetails).toMatchObject({ beforeVersion: '2.0.0', afterVersion: '2.0.0' })
})

test('removal reports only the selected target and redacts cleanup warning details', () => {
  const input = record()
  const inventoryItem = input.transaction!.inventoryCommit!.before.items[1]!
  input.operation.kind = 'uninstall'
  input.operation.version = '1.0.0'
  input.operation.targetLabel = '当前用户'
  input.operation.targetPaths = ['/private/user/writer']
  input.operation.status.files = 'removed'
  input.operation.status.warnings = ['Removal quarantine cleanup requires verification.']
  input.removal = { intent: { inventoryItem, target: { root: '/private/user', path: '/private/user/writer', scope: 'user' },
    quarantine: '/private/trash', files: { 'SKILL.md': 'private-fingerprint' } }, isolated: true }
  delete input.transaction
  const result = projectOperation(input).resultDetails!
  expect(result).toEqual({ beforeVersion: '1.0.0', afterVersion: null,
    targets: [{ label: '当前用户', outcome: 'removed' }], warnings: ['removal-cleanup'] })
  expect(JSON.stringify(result)).not.toContain('/private')
})

test('a nonterminal phase cannot advertise success even with a success-like files value', () => {
  const input = record()
  input.operation.status.phase = 'recovering'
  expect(projectOperation(input).resultDetails).toMatchObject({ afterVersion: null,
    targets: [{ label: '目标 1（确认计划顺序）', outcome: 'unknown' }, { label: '目标 2（确认计划顺序）', outcome: 'unknown' }] })
})
