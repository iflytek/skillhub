import { dirname, isAbsolute, join, resolve } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { z } from 'zod'
import { inventoryItemSchema, inventorySnapshotSchema } from './transaction-journal'
import type { OperationRecord } from './operation-store'
import { sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { targetInstalledBy } from '../stores/inventory-store'

const path = z.string().min(1).max(2048).refine(value => isAbsolute(value) && resolve(value) === value)
const intent = z.object({ inventoryItem: inventoryItemSchema,
  target: z.object({ root: path, path, scope: z.enum(['user', 'project']) }).strict(),
  quarantine: path.nullable(), files: z.record(z.string(), z.string()).nullable() }).strict()
const commit = z.object({ before: inventorySnapshotSchema, after: inventorySnapshotSchema }).strict()
export const removalJournal = z.object({ intent, isolated: z.boolean(),
  inventoryCommit: commit.extend({ committed: z.boolean() }).optional(),
  cleanup: z.object({ removed: z.boolean() }).strict().optional(),
  restore: z.object({ completed: z.boolean() }).strict().optional() }).strict()
export const removalEvent = z.discriminatedUnion('type', [
  z.object({ type: z.literal('intent'), intent }).strict(),
  z.object({ type: z.literal('isolated') }).strict(),
  z.object({ type: z.literal('beforeCommit'), commit }).strict(),
  z.object({ type: z.literal('committed') }).strict(),
  z.object({ type: z.literal('cleanup'), removed: z.boolean() }).strict(),
  z.object({ type: z.literal('restore'), completed: z.boolean() }).strict()
])
export type RemovalEvent = z.infer<typeof removalEvent>

/** Validate persisted facts; this reducer never moves files or infers a successful commit. */
export function applyRemovalEvent(record: OperationRecord, event: RemovalEvent): z.infer<typeof removalJournal> {
  const fail = (): never => { throw new Error('Invalid removal journal transition') }
  const operation = record.operation
  if (!['uninstall', 'remove-stale-record'].includes(operation.kind)
    || (!['revalidating', 'committing', 'recovering'].includes(operation.status.phase)
      && !(operation.status.phase === 'completed' && event.type === 'cleanup'))) fail()
  if (event.type === 'intent') {
    const value = event.intent
    const item = value.inventoryItem
    const targets = item.targets.filter(target => target.installDir === value.target.path)
    if (record.removal || operation.status.phase !== 'revalidating'
      || !isDeepStrictEqual(operation.targetPaths, [value.target.path])
      || !sameInstalledSkillSource(item, { registry: operation.context.sourceIdentity, namespace: operation.namespace, slug: operation.slug })
      || targets[0]?.version !== operation.version || !targets[0]?.fingerprint || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(item.slug)
      || join(value.target.root, item.slug) !== value.target.path
      || targets.length !== 1 || targets[0]!.agent !== 'dsh' || targets[0]!.rootDir !== value.target.root
      || !isDeepStrictEqual(targetInstalledBy(item, targets[0]!), ['direct'])) fail()
    if (operation.kind === 'uninstall') {
      if (value.quarantine !== join(dirname(value.target.root), '.skillhub-trash', record.operationId)
        || value.files === null || !Object.hasOwn(value.files, 'SKILL.md')) fail()
    } else if (value.quarantine !== null || value.files !== null) fail()
    return { intent: value, isolated: false }
  }
  const state = record.removal && structuredClone(record.removal)
  if (!state) return fail()
  switch (event.type) {
    case 'isolated':
      if (operation.kind !== 'uninstall' || state.isolated || state.inventoryCommit || state.restore) fail()
      state.isolated = true
      break
    case 'beforeCommit': {
      if (state.inventoryCommit || state.restore || (operation.kind === 'uninstall' && !state.isolated)) fail()
      const before = event.commit.before
      const expected = structuredClone(before)
      const matches = expected.items.filter(item => sameInstalledSkillSource(item, state.intent.inventoryItem))
      if (!isDeepStrictEqual(matches.map(item => ({ ...item, targets: item.targets.filter(target => target.installDir === state.intent.target.path) })), [{ ...state.intent.inventoryItem, targets: state.intent.inventoryItem.targets.filter(target => target.installDir === state.intent.target.path) }])) fail()
      const item = matches[0]!
      item.targets = item.targets.filter(target => target.installDir !== state.intent.target.path)
      if (!item.targets.length) expected.items = expected.items.filter(entry => entry !== item)
      if (!isDeepStrictEqual(expected, event.commit.after)) fail()
      state.inventoryCommit = { ...event.commit, committed: false }
      break
    }
    case 'committed':
      if (!state.inventoryCommit || state.restore) return fail()
      state.inventoryCommit.committed = true
      break
    case 'cleanup':
      if (operation.kind !== 'uninstall' || !state.inventoryCommit?.committed || state.restore) fail()
      state.cleanup = { removed: event.removed }
      break
    case 'restore':
      if (operation.kind !== 'uninstall' || state.inventoryCommit?.committed
        || operation.status.phase !== 'recovering' || (event.completed && !state.restore)
        || state.restore?.completed) fail()
      state.restore = { completed: event.completed }
      break
  }
  return state
}
